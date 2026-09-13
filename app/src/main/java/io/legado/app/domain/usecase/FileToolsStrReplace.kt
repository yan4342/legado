package io.legado.app.domain.usecase

/**
 * Shared strReplace semantics for file-edit AI tools (edit_file / skill edits / plan edits /
 * HTML-chat-theme edits). Deduplicates the identical countOccurrences + unique-match logic
 * that previously lived independently in [SkillTools], [PlanFileTools], and
 * [io.legado.app.ui.ai.chat.html.AiChatHtmlThemeStore].
 *
 * Semantics mirror Claude Code's Edit tool: old_string must match exactly once unless
 * replace_all=true; on 0 / N>1 matches the caller renders its own diagnostic.
 */
object FileToolsStrReplace {

    data class EditApplyResult(
        val content: String,
        val replacements: Int,
    )

    /**
     * Core strReplace: count occurrences, enforce uniqueness unless replaceAll.
     * Failure messages: "old_string must not be empty", "0 matches", "<N> matches".
     */
    fun applyEdit(
        content: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean,
    ): Result<EditApplyResult> {
        if (oldString.isEmpty()) {
            return Result.failure(IllegalArgumentException("old_string must not be empty"))
        }
        val count = countOccurrences(content, oldString)
        when {
            count == 0 -> return Result.failure(IllegalArgumentException("0 matches"))
            count > 1 && !replaceAll -> return Result.failure(
                IllegalArgumentException("$count matches"),
            )
        }
        val next = if (replaceAll) {
            content.replace(oldString, newString)
        } else {
            content.replaceFirst(oldString, newString)
        }
        return Result.success(
            EditApplyResult(
                content = next,
                replacements = if (replaceAll) count else 1,
            ),
        )
    }

    fun countOccurrences(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var idx = 0
        while (true) {
            val found = haystack.indexOf(needle, idx)
            if (found < 0) break
            count++
            idx = found + needle.length
        }
        return count
    }
}
