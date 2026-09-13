package io.legado.app.data.repository

import com.google.gson.JsonObject
import io.legado.app.domain.model.AiToolGroup
import io.legado.app.domain.model.AiToolGroupState
import io.legado.app.domain.usecase.HtmlAppTools
import io.legado.app.domain.usecase.HtmlChatThemeTools
import io.legado.app.domain.usecase.PlanFileTools
import io.legado.app.domain.usecase.SkillTools
import io.legado.app.domain.usecase.SkillPackageCodec
import io.legado.app.utils.GSON

/**
 * Unified file-tool router. Dispatches read_file / edit_file / write_file / delete_file
 * onto the three file-backed backends via a `scheme://id/path` URI:
 *
 * - `theme://{themeId}/{filePath}` → HtmlChatThemeTools
 * - `skill://{skillId}` or `skill://{skillId}/{docId}` → SkillTools
 * - `plan://` → PlanFileTools (current conversation's plan)
 * - `htmlapp://{appId}` or `htmlapp://{appId}/index.html` → HtmlAppTools
 *
 * The tool name is shared across all backends, so domain access is enforced here
 * at runtime (defense in depth) in addition to the tool-group visibility gate:
 * theme:// paths require the `html_theme` group, skill:// paths require `skills`,
 * htmlapp:// paths require the `html_app` group.
 */
class FileToolRouter(
    private val htmlChatThemeTools: HtmlChatThemeTools,
    private val skillTools: SkillTools,
    private val planFileTools: PlanFileTools,
    private val htmlAppTools: HtmlAppTools,
) {

    suspend fun read(
        args: JsonObject,
        sourceConversationId: String?,
        conversationType: String?,
        toolGroupState: AiToolGroupState?,
    ): String {
        val path = args.string("path") ?: return errorJson("path is required")
        val backend = parsePath(path) ?: return errorJson(
            "Invalid path \"$path\". Use theme://id/file, skill://id[/doc], plan://, or htmlapp://appId.",
        )
        return when (backend) {
            is Backend.Theme -> {
                groupError("theme", conversationType, toolGroupState)?.let { return it }
                htmlChatThemeTools.read(
                    jsonArgs {
                        addProperty("themeId", backend.id)
                        backend.filePath?.let { addProperty("path", it) }
                        args.string("grep")?.let { addProperty("grep", it) }
                        args.int("offset")?.let { addProperty("offset", it) }
                        args.int("limit")?.let { addProperty("limit", it) }
                    },
                )
            }
            is Backend.Skill -> {
                groupError("skill", conversationType, toolGroupState)?.let { return it }
                val docId = backend.docId?.let { normalizeSkillDoc(it) }
                if (docId == null || docId == SkillPackageCodec.DOC_ID_SKILL) {
                    skillTools.read(jsonArgs { addProperty("skillId", backend.skillId) })
                } else {
                    skillTools.readResourceDoc(backend.skillId, docId)
                }
            }
            is Backend.Plan -> planFileTools.read(sourceConversationId.orEmpty())
            is Backend.HtmlApp -> {
                groupError("htmlapp", conversationType, toolGroupState)?.let { return it }
                htmlAppTools.read(
                    backend.appId,
                    jsonArgs {
                        args.int("offset")?.let { addProperty("offset", it) }
                        args.int("limit")?.let { addProperty("limit", it) }
                    },
                )
            }
        }
    }

    suspend fun edit(
        args: JsonObject,
        sourceConversationId: String?,
        conversationType: String?,
        toolGroupState: AiToolGroupState?,
    ): String {
        val path = args.string("path") ?: return errorJson("path is required")
        val backend = parsePath(path) ?: return errorJson(
            "Invalid path \"$path\". Use theme://id/file, skill://id[/doc], or plan://.",
        )
        return when (backend) {
            is Backend.Theme -> {
                groupError("theme", conversationType, toolGroupState)?.let { return it }
                htmlChatThemeTools.edit(
                    jsonArgs {
                        addProperty("themeId", backend.id)
                        backend.filePath?.let { addProperty("path", it) }
                        args.string("old_string")?.let { addProperty("old_string", it) }
                        args.string("new_string")?.let { addProperty("new_string", it) }
                        args.boolean("replace_all")?.let { addProperty("replace_all", it) }
                        args.boolean("skipShellValidation")?.let { addProperty("skipShellValidation", it) }
                    },
                )
            }
            is Backend.Skill -> {
                groupError("skill", conversationType, toolGroupState)?.let { return it }
                skillTools.edit(
                    jsonArgs {
                        addProperty("skillId", backend.skillId)
                        backend.docId?.let { addProperty("doc", normalizeSkillDoc(it)) }
                        args.string("old_string")?.let { addProperty("old_string", it) }
                        args.string("new_string")?.let { addProperty("new_string", it) }
                        args.boolean("replace_all")?.let { addProperty("replace_all", it) }
                    },
                )
            }
            is Backend.Plan -> planFileTools.edit(
                sourceConversationId.orEmpty(),
                jsonArgs {
                    args.string("old_string")?.let { addProperty("old_string", it) }
                    args.string("new_string")?.let { addProperty("new_string", it) }
                    args.boolean("replace_all")?.let { addProperty("replace_all", it) }
                },
            )
            is Backend.HtmlApp -> {
                groupError("htmlapp", conversationType, toolGroupState)?.let { return it }
                htmlAppTools.edit(
                    backend.appId,
                    jsonArgs {
                        args.string("old_string")?.let { addProperty("old_string", it) }
                        args.string("new_string")?.let { addProperty("new_string", it) }
                        args.boolean("replace_all")?.let { addProperty("replace_all", it) }
                    },
                )
            }
        }
    }

    suspend fun write(
        args: JsonObject,
        sourceConversationId: String?,
        conversationType: String?,
        toolGroupState: AiToolGroupState?,
    ): String {
        val path = args.string("path") ?: return errorJson("path is required")
        val backend = parsePath(path) ?: return errorJson(
            "Invalid path \"$path\". Use theme://id/file, skill://id[/doc], or plan://.",
        )
        return when (backend) {
            is Backend.Theme -> {
                groupError("theme", conversationType, toolGroupState)?.let { return it }
                htmlChatThemeTools.write(
                    jsonArgs {
                        addProperty("themeId", backend.id)
                        backend.filePath?.let { addProperty("path", it) }
                        args.string("content")?.let { addProperty("contents", it) }
                        args.string("seedFrom")?.let { addProperty("baseThemeId", it) }
                        args.boolean("skipShellValidation")?.let { addProperty("skipShellValidation", it) }
                    },
                )
            }
            is Backend.Skill -> {
                groupError("skill", conversationType, toolGroupState)?.let { return it }
                skillTools.save(
                    jsonArgs {
                        backend.skillId.let { addProperty("skillId", it) }
                        // (a) full markdown
                        args.string("content")?.let { addProperty("markdown", it) }
                        // (b) field-built
                        args.string("name")?.let { addProperty("name", it) }
                        args.string("description")?.let { addProperty("description", it) }
                        args.string("body")?.let { addProperty("body", it) }
                        args.string("mode")?.let { addProperty("mode", it) }
                        args.string("resources")?.let { addProperty("resources", it) }
                        // (c) enabled toggle
                        args.boolean("enabled")?.let { addProperty("enabled", it) }
                    },
                )
            }
            is Backend.Plan -> planFileTools.write(
                sourceConversationId.orEmpty(),
                jsonArgs {
                    args.string("content")?.let { addProperty("content", it) }
                },
            )
            is Backend.HtmlApp -> {
                groupError("htmlapp", conversationType, toolGroupState)?.let { return it }
                htmlAppTools.write(
                    backend.appId,
                    jsonArgs {
                        args.string("content")?.let { addProperty("content", it) }
                    },
                )
            }
        }
    }

    suspend fun delete(
        args: JsonObject,
        sourceConversationId: String?,
        conversationType: String?,
        toolGroupState: AiToolGroupState?,
    ): String {
        val path = args.string("path") ?: return errorJson("path is required")
        val backend = parsePath(path) ?: return errorJson(
            "Invalid path \"$path\". Use theme://id/file, skill://id[/doc], or plan://.",
        )
        return when (backend) {
            is Backend.Theme -> {
                groupError("theme", conversationType, toolGroupState)?.let { return it }
                htmlChatThemeTools.deleteFile(
                    jsonArgs {
                        addProperty("themeId", backend.id)
                        backend.filePath?.let { addProperty("path", it) }
                    },
                )
            }
            is Backend.Skill -> {
                groupError("skill", conversationType, toolGroupState)?.let { return it }
                skillTools.delete(jsonArgs { addProperty("skillId", backend.skillId) })
            }
            is Backend.Plan -> errorJson("plan:// files cannot be deleted.")
            is Backend.HtmlApp -> {
                groupError("htmlapp", conversationType, toolGroupState)?.let { return it }
                htmlAppTools.delete(backend.appId, sourceConversationId)
            }
        }
    }

    // ---- path parsing ----

    private sealed interface Backend {
        data class Theme(val id: String, val filePath: String?) : Backend
        data class Skill(val skillId: String, val docId: String?) : Backend
        data object Plan : Backend
        data class HtmlApp(val appId: String, val filePath: String?) : Backend
    }

    private fun parsePath(path: String): Backend? {
        val schemeEnd = path.indexOf("://")
        if (schemeEnd < 0) return null
        val scheme = path.substring(0, schemeEnd).lowercase()
        val rest = path.substring(schemeEnd + 3)
        val slash = rest.indexOf('/')
        return when (scheme) {
            "theme" -> {
                val id = if (slash >= 0) rest.substring(0, slash) else rest
                val filePath = if (slash >= 0) rest.substring(slash + 1).trim().ifBlank { null } else null
                if (id.isBlank()) null else Backend.Theme(id.trim(), filePath)
            }
            "skill" -> {
                val id = if (slash >= 0) rest.substring(0, slash) else rest
                val doc = if (slash >= 0) rest.substring(slash + 1).trim().ifBlank { null } else null
                if (id.isBlank()) null else Backend.Skill(id.trim(), doc)
            }
            "plan" -> Backend.Plan
            "htmlapp" -> {
                val id = if (slash >= 0) rest.substring(0, slash) else rest
                val filePath = if (slash >= 0) rest.substring(slash + 1).trim().ifBlank { null } else null
                if (id.isBlank()) null else Backend.HtmlApp(id.trim(), filePath)
            }
            else -> null
        }
    }

    /** Normalize a skill:// doc id: strip .md suffix so "SKILL.md" → "SKILL". */
    private fun normalizeSkillDoc(docId: String): String =
        docId.trim().removeSuffix(".md")

    /** Runtime domain-group gate (defense in depth beyond the visibility filter). */
    private fun groupError(
        scheme: String,
        conversationType: String?,
        toolGroupState: AiToolGroupState?,
    ): String? {
        if (conversationType != "chat") return null
        if (toolGroupState == null) return null
        return when (scheme) {
            "theme" -> if (toolGroupState.isActive(AiToolGroup.HTML_THEME.id)) {
                null
            } else {
                errorJson("HTML theme tools not active. Use set_tool_groups to enable html_theme group.")
            }
            "skill" -> if (toolGroupState.isActive(AiToolGroup.SKILLS.id)) {
                null
            } else {
                errorJson("Skill tools not active. Use set_tool_groups to enable skills group.")
            }
            "htmlapp" -> if (toolGroupState.isActive(AiToolGroup.HTML_APP.id)) {
                null
            } else {
                errorJson("HTML app tools not active. Use set_tool_groups to enable html_app group.")
            }
            else -> null
        }
    }

    private fun jsonArgs(block: JsonObject.() -> Unit): JsonObject = JsonObject().apply(block)

    private fun errorJson(message: String): String =
        GSON.toJson(mapOf("success" to false, "error" to message))

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull }?.asString

    private fun JsonObject.int(key: String): Int? =
        runCatching { get(key)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull()

    private fun JsonObject.boolean(key: String): Boolean? =
        get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.let { el ->
            when {
                el.asJsonPrimitive.isBoolean -> el.asBoolean
                el.asJsonPrimitive.isString -> el.asString.equals("true", ignoreCase = true)
                else -> null
            }
        }
}
