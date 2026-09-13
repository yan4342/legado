package io.legado.app.domain.usecase

import com.google.gson.JsonParser
import java.util.concurrent.ConcurrentHashMap

/**
 * Repeat-tool reminder guard (mirrors DSH `guard/repeat-tool-reminder`).
 *
 * Counts consecutive calls of the same tool with the same normalized arguments inside
 * one conversation. When the count crosses escalation thresholds, a guidance prefix is
 * prepended to the tool result the model sees — it enriches, never intercepts/caches.
 * Read-only tools with intentionally identical calls (read_file pagination) skip the
 * reminder via [isReminderExempt].
 */
object RepeatToolReminder {

    private data class Key(val conversationId: String, val tool: String, val args: String)

    private val counts = ConcurrentHashMap<Key, Int>()

    private val THRESHOLDS = intArrayOf(3, 5, 8)

    /** Reset when the tool changes or args change (consecutive-only semantics). */
    fun reset(conversationId: String, tool: String, args: String) {
        counts.remove(Key(conversationId, tool, normalized(args)))
    }

    /**
     * @return [content] possibly prefixed with an escalation reminder when the same
     * (tool, normalized args) has been called consecutively across thresholds.
     */
    fun maybeRemind(
        content: String,
        conversationId: String?,
        tool: String,
        args: String,
    ): String {
        if (conversationId.isNullOrBlank()) return content
        if (isReminderExempt(tool)) return content
        val key = Key(conversationId, tool, normalized(args))
        val count = counts.merge(key, 1, Int::plus) ?: 1
        val level = THRESHOLDS.indexOfFirst { count == it }
        if (level < 0) return content
        val guide = when (level) {
            0 -> "Heads-up: this exact tool call has now been made $count times in a row. " +
                "Verify the previous result before calling it again; if it keeps failing, " +
                "adjust parameters or change approach."
            1 -> "You are repeating the same tool call ($tool, identical arguments) $count times " +
                "consecutively. Stop and re-read the earlier result — repeating will not help. " +
                "Change the arguments or conclude."
            else -> "Repeated identical tool call ($tool) detected $count times. Do NOT call it again " +
                "with the same arguments; analyze the prior result and either proceed or ask the user."
        }
        return "$guide\n\n$content"
    }

    /** Reset per conversation when a new user turn starts (prevents cross-turn false positives). */
    fun resetConversation(conversationId: String) {
        counts.keys.removeIf { it.conversationId == conversationId }
    }

    /** Tools exempt because identical consecutive calls are legitimate (e.g. read pagination). */
    private fun isReminderExempt(tool: String): Boolean = tool in setOf(
        "read_file",
        "list_book_chapters",
        "get_book_detail",
        "web_search",
    )

    private fun normalized(args: String): String {
        if (args.isBlank()) return ""
        return runCatching {
            val obj = JsonParser.parseString(args).asJsonObject
            obj.entrySet().sortedBy { it.key }
                .joinToString("&") { "${it.key}=${it.value}" }
        }.getOrElse { args }
    }
}