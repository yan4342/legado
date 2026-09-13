package io.legado.app.domain.usecase

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.legado.app.data.entities.AiTodo
import io.legado.app.domain.gateway.AiTodoGateway
import io.legado.app.utils.GSON

/**
 * 会话任务清单工具：AI 在多步任务中通过 update_todos 全量维护一份进度清单
 * （语义同 Claude Code TodoWrite——每次调用传完整新列表整体替换）。
 * 存储于 ai_todos 表（每会话一行，todosJson 为 JSON 数组），UI 用 Room Flow 实时刷新。
 * 与计划模式（ai_plans）不同：免审批、高频自更新、三模式（chat/writing/plan）通用。
 */
class TodoTools(
    private val todoGateway: AiTodoGateway,
) {

    /** 读取会话当前任务清单（无行为空列表）。 */
    suspend fun read(conversationId: String): List<AiTodoItem> {
        if (conversationId.isBlank()) return emptyList()
        val row = todoGateway.getByConversation(conversationId) ?: return emptyList()
        return parseTodosJson(row.todosJson)
    }

    /** 全量替换会话任务清单。返回最新清单 JSON（模型每轮的"确认点"）。 */
    suspend fun update(conversationId: String, args: JsonObject): String {
        if (conversationId.isBlank()) return errorJson("conversationId is required")
        val items = parseTodoArgs(args)
            ?: return errorJson(
                """Invalid or missing "todos". Pass the FULL new list as a JSON array, e.g. {"todos":[{"content":"...","status":"pending","activeForm":"..."}]}. Every call replaces the whole list.""",
            )
        val json = GSON.toJson(items.map { it.toJsonObject() })
        todoGateway.upsert(
            AiTodo(
                conversationId = conversationId,
                todosJson = json,
            ),
        )
        val done = items.count { it.status == STATUS_COMPLETED }
        val checklist = renderTodosForPrompt(items, maxItems = items.size.coerceAtLeast(1))
        return buildString {
            append("update_todos: 任务清单已整体更新，当前 $done/${items.size} 完成")
            if (items.isEmpty()) append("，清单已清空")
            append("。\n")
            append(checklist)
        }
    }

    // ---- args 解析 ----

    /** 解析 args.todos → 规范化后的列表；非法结构返回 null。 */
    private fun parseTodoArgs(args: JsonObject): List<AiTodoItem>? {
        val el = args.get("todos") ?: return null
        val array: JsonArray = when {
            el.isJsonArray -> el.asJsonArray
            el.isJsonPrimitive && el.asJsonPrimitive.isString -> {
                val raw = el.asString.trim()
                if (raw.isBlank()) return emptyList()
                runCatching { GSON.fromJson(raw, JsonArray::class.java) }.getOrNull()
                    ?: return null
            }
            else -> return null
        }
        if (array.size() > MAX_ITEMS) {
            return null // 由调用方报错
        }
        val items = mutableListOf<AiTodoItem>()
        for (e in array) {
            if (!e.isJsonObject) return null
            val obj = e.asJsonObject
            val content = obj.stringOrNull("content")?.trim().orEmpty()
            if (content.isBlank()) return null
            val status = obj.stringOrNull("status")?.trim().orEmpty()
                .ifBlank { STATUS_PENDING }
            val normalized = normalizeStatus(status) ?: return null
            val activeForm = obj.stringOrNull("activeForm")?.trim()
            items.add(
                AiTodoItem(
                    content = content.take(MAX_CONTENT_LEN),
                    status = normalized,
                    activeForm = activeForm?.take(MAX_CONTENT_LEN)?.ifBlank { null },
                ),
            )
        }
        return items
    }

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_COMPLETED = "completed"

        const val MAX_ITEMS = 50
        const val MAX_CONTENT_LEN = 200

        /** 宽容归一化：常见变体映射到三态；未知返回 null。 */
        fun normalizeStatus(raw: String): String? = when (raw.trim().lowercase()) {
            "pending", "todo", "backlog", "not_started", "" -> STATUS_PENDING
            "in_progress", "in-progress", "inprogress", "doing", "active", "working" ->
                STATUS_IN_PROGRESS
            "completed", "done", "complete", "finished" -> STATUS_COMPLETED
            else -> null
        }

        private fun errorJson(message: String): String =
            GSON.toJson(mapOf("success" to false, "error" to message))

        private fun JsonObject.stringOrNull(key: String): String? {
            val el = get(key) ?: return null
            if (el.isJsonNull) return null
            return runCatching { el.asString }.getOrNull()
        }
    }
}

/** 单条任务。status ∈ pending | in_progress | completed。 */
data class AiTodoItem(
    val content: String,
    val status: String = TodoTools.STATUS_PENDING,
    val activeForm: String? = null,
) {
    fun toJsonObject(): JsonObject = JsonObject().apply {
        addProperty("content", content)
        addProperty("status", status)
        activeForm?.let { addProperty("activeForm", it) }
    }
}

/** 解析 todosJson → 任务列表（容错：非法项丢弃，空/非法整体返回空列表）。 */
fun parseTodosJson(todosJson: String): List<AiTodoItem> {
    if (todosJson.isBlank()) return emptyList()
    fun str(obj: JsonObject, key: String): String? =
        obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString
    return runCatching {
        GSON.fromJson(todosJson, JsonArray::class.java).mapNotNull { e ->
            if (!e.isJsonObject) return@mapNotNull null
            val obj = e.asJsonObject
            val content = str(obj, "content")?.trim().orEmpty()
            if (content.isBlank()) return@mapNotNull null
            AiTodoItem(
                content = content,
                status = TodoTools.normalizeStatus(str(obj, "status").orEmpty())
                    ?: TodoTools.STATUS_PENDING,
                activeForm = str(obj, "activeForm")?.trim()?.ifBlank { null },
            )
        }
    }.getOrDefault(emptyList())
}

/** 渲染为上下文注入文本（completed=[x]、in_progress=[>]、pending=[ ]）。空列表返回空串。
 *  [maxItems] 限制注入条数（UI 卡片不受此限制），避免 50 项×200 字占用过多 token。 */
fun renderTodosForPrompt(todos: List<AiTodoItem>, maxItems: Int = 20): String {
    if (todos.isEmpty()) return ""
    val shown = todos.take(maxItems.coerceAtLeast(1))
    val done = shown.count { it.status == TodoTools.STATUS_COMPLETED }
    val truncated = shown.size < todos.size
    val lines = shown.joinToString("\n") { item ->
        val marker = when (item.status) {
            TodoTools.STATUS_COMPLETED -> "[x]"
            TodoTools.STATUS_IN_PROGRESS -> "[>]"
            else -> "[ ]"
        }
        val content = if (item.content.length > 160) item.content.take(160) + "…" else item.content
        "- $marker $content"
    }
    return buildString {
        append("📋 当前任务清单（$done/${todos.size} 完成）")
        if (truncated) append("，仅显示前 ${shown.size} 项")
        append("：\n")
        append(lines)
    }
}
