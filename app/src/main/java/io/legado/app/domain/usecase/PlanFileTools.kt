package io.legado.app.domain.usecase

import com.google.gson.JsonObject
import io.legado.app.data.entities.AiPlan
import io.legado.app.domain.gateway.AiPlanGateway
import io.legado.app.help.ai.PlanFileStore
import io.legado.app.utils.GSON
import splitties.init.appCtx

/**
 * 计划文件工具：AI 在计划模式/修订轮中用 read_file("plan://…") / edit_file("plan://…") 精准修改当前计划文件。
 * 每个计划一份 plan_<planId>.md；编辑成功即写回该文件并 upsert ai_plans 行（revision+1），
 * ViewModel 据此同步面板。
 */
class PlanFileTools(
    private val planGateway: AiPlanGateway,
) {

    /** 当前生效计划：pending 优先，否则取最近一条。 */
    private suspend fun resolvePlan(conversationId: String): AiPlan? =
        planGateway.getPendingByConversation(conversationId)
            ?: planGateway.getByConversation(conversationId).firstOrNull()

    /** 读取当前计划 markdown（文件为唯一来源；无文件时返回空）。 */
    suspend fun read(conversationId: String): String {
        val row = resolvePlan(conversationId) ?: return ""
        return PlanFileStore.read(appCtx, conversationId, row.id).orEmpty()
    }

    /** 计划模式首轮：AI 调用 write_file("plan://…") 将完整计划写入新文件，并建 ai_plans 行。 */
    suspend fun write(conversationId: String, args: JsonObject): String {
        val content = args.stringOrNull("content")?.trim() ?: ""
        if (content.isBlank()) return errorJson("content must not be empty — pass the full plan markdown")
        // 作废旧 pending + 写文件 + 建行（messageId 先空，ViewModel 在消息落库后回填）。
        planGateway.supersedePending(conversationId)
        val planId = "plan_${java.util.UUID.randomUUID().toString().replace("-", "")}"
        PlanFileStore.write(appCtx, conversationId, planId, content)
        planGateway.upsert(
            AiPlan(
                id = planId,
                conversationId = conversationId,
                messageId = "",
                status = AiPlan.STATUS_PENDING,
                revision = 1,
            ),
        )
        return GSON.toJson(
            mapOf(
                "success" to true,
                "action" to "write_file",
                "planId" to planId,
                "path" to "$planId.md",
                "note" to "Plan file written. The user will review and approve/reject in the plan panel.",
            ),
        )
    }

    suspend fun edit(conversationId: String, args: JsonObject): String {
        val oldString = args.stringOrNull("old_string")?.trimEnd()
            ?: args.stringOrNull("oldString")?.trimEnd()
        val newString = args.stringOrNull("new_string")
            ?: args.stringOrNull("newString")
            ?: ""
        if (oldString.isNullOrEmpty()) return errorJson("old_string must not be empty")
        val replaceAll = args.booleanOrNull("replace_all")
            ?: args.booleanOrNull("replaceAll")
            ?: false

        val current = read(conversationId)
        if (current.isBlank()) {
            return errorJson("当前会话没有计划文件。先在计划模式生成计划后再编辑。")
        }
        val count = FileToolsStrReplace.countOccurrences(current, oldString)
        when {
            count == 0 -> {
                val probe = oldString.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().take(64)
                val hit = if (probe.length >= 4) current.indexOf(probe) else -1
                val snippet = if (hit >= 0) {
                    val start = (hit - 20).coerceAtLeast(0)
                    val end = (hit + probe.length + 20).coerceAtMost(current.length)
                    current.substring(start, end)
                } else {
                    current.take(100)
                }
                return GSON.toJson(
                    mapOf(
                        "error" to "old_string not found (0 matches)",
                        "snippet" to snippet,
                        "hint" to if (hit >= 0) {
                            "old_string not found at exact position — similar text at offset ~$hit. Re-read and widen old_string context."
                        } else {
                            "old_string not found in plan. Re-read the plan file and try again."
                        },
                    ),
                )
            }
            count > 1 && !replaceAll -> {
                return GSON.toJson(
                    mapOf(
                        "error" to "$count matches found. Add more context to old_string to make it unique, or set replace_all=true.",
                        "matchCount" to count,
                        "hint" to "Re-read, widen old_string context, and retry.",
                    ),
                )
            }
        }
        val next = if (replaceAll) current.replace(oldString, newString) else current.replaceFirst(oldString, newString)
        val replacements = if (replaceAll) count else 1

        // 写回文件 + 同步行（revision+1，供修订轮判断"AI 改过文件"）。
        val row = planGateway.getPendingByConversation(conversationId)
            ?: planGateway.getByConversation(conversationId).firstOrNull()
        if (row == null) {
            return errorJson("当前会话没有计划记录。先在计划模式生成计划。")
        }
        PlanFileStore.write(appCtx, conversationId, row.id, next)
        planGateway.upsert(
            row.copy(
                revision = row.revision + 1,
            ),
        )
        return GSON.toJson(
            mapOf(
                "success" to true,
                "action" to "edit_file",
                "replacements" to replacements,
                "note" to if (replaceAll && replacements > 1) {
                    "$replacements replacements applied. Plan file updated."
                } else {
                    "1 replacement applied. Plan file updated."
                },
            ),
        )
    }

    companion object {
        private fun errorJson(message: String): String =
            GSON.toJson(mapOf("error" to message))

        private fun JsonObject.stringOrNull(key: String): String? {
            val el = get(key) ?: return null
            if (el.isJsonNull) return null
            return runCatching { el.asString }.getOrNull()
        }

        private fun JsonObject.booleanOrNull(key: String): Boolean? {
            val el = get(key) ?: return null
            if (el.isJsonNull) return null
            return when {
                el.isJsonPrimitive && el.asJsonPrimitive.isBoolean -> el.asBoolean
                else -> el.asString.toBooleanStrictOrNull()
            }
        }
    }
}
