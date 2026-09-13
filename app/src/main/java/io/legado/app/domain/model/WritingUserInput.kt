package io.legado.app.domain.model

enum class WritingInputMode {
    ACTION,
    DIALOGUE,
}

data class WritingInputModeSwitchState(
    val textBeforeSwitch: String = "",
    val textAfterSwitch: String = "",
    val modeBeforeSwitch: WritingInputMode? = null,
    val modeAfterSwitch: WritingInputMode? = null,
) {
    fun canRevert(
        currentText: String,
        currentMode: WritingInputMode,
        targetMode: WritingInputMode,
    ): Boolean {
        val before = modeBeforeSwitch ?: return false
        val after = modeAfterSwitch ?: return false
        return currentText == textAfterSwitch &&
            currentMode == after &&
            targetMode == before
    }
}

data class WritingInputModeSwitchResult(
    val text: String,
    val state: WritingInputModeSwitchState,
)

private enum class SegmentKind {
    ACTION,
    DIALOGUE,
}

object WritingUserInput {

    /** Model-facing user message when the user taps send with empty input in writing mode. */
    const val CONTINUE_MODEL_CONTENT = "(Continue the story)"

    fun isContinueModelContent(content: String): Boolean =
        content.trim() == CONTINUE_MODEL_CONTENT

    private const val ACTION_OPEN = '\uFF08'  // （
    private const val ACTION_CLOSE = '\uFF09' // ）
    private const val QUOTE_OPEN = '\u201C'   // "
    private const val QUOTE_CLOSE = '\u201D'  // "

    fun hasActionMarkup(text: String): Boolean {
        var depth = 0
        for (c in text) {
            when (c) {
                ACTION_OPEN -> depth++
                ACTION_CLOSE -> if (depth > 0) return true
            }
        }
        return false
    }

    /**
     * When cycling input mode, commit the segment just typed so action/dialogue can be mixed
     * in one message, e.g. ACTION「欠身」→ DIALOGUE「你好」→ display「（欠身）你好」.
     *
     * If the user switches back without editing since the last switch, the structural change
     * is reverted instead of applying another transform (avoids orphan parentheses).
     */
    fun onModeSwitch(
        input: String,
        from: WritingInputMode,
        to: WritingInputMode,
        switchState: WritingInputModeSwitchState = WritingInputModeSwitchState(),
    ): WritingInputModeSwitchResult {
        if (from == to) {
            return WritingInputModeSwitchResult(input, switchState)
        }
        if (switchState.canRevert(input, from, to)) {
            return WritingInputModeSwitchResult(
                text = switchState.textBeforeSwitch,
                state = WritingInputModeSwitchState(),
            )
        }
        val newText = when {
            from == WritingInputMode.ACTION && to == WritingInputMode.DIALOGUE -> commitActionDraft(input)
            from == WritingInputMode.DIALOGUE && to == WritingInputMode.ACTION -> openActionDraft(input)
            else -> input
        }
        return WritingInputModeSwitchResult(
            text = newText,
            state = WritingInputModeSwitchState(
                textBeforeSwitch = input,
                textAfterSwitch = newText,
                modeBeforeSwitch = from,
                modeAfterSwitch = to,
            ),
        )
    }

    /** Display text before save: close open action parens; wrap only when the whole line is a single bare segment. */
    fun normalizeDisplay(raw: String, mode: WritingInputMode): String {
        val trimmed = closeUnclosedAction(raw.trim())
        if (trimmed.isEmpty()) return trimmed
        if (hasActionMarkup(trimmed)) return trimmed
        return when (mode) {
            WritingInputMode.ACTION -> "$ACTION_OPEN$trimmed$ACTION_CLOSE"
            WritingInputMode.DIALOGUE -> trimmed
        }
    }

    /** API payload: actions as a:…, dialogue wrapped in curly quotes. */
    fun toModelContent(display: String): String {
        val trimmed = display.trim()
        if (trimmed.isEmpty()) return trimmed
        if (isAlreadyModelContent(trimmed)) return trimmed
        return parseSegments(trimmed)
            .joinToString(" ") { (kind, text) ->
                when (kind) {
                    SegmentKind.ACTION -> "a:$text"
                    SegmentKind.DIALOGUE -> wrapCurlyQuotes(text)
                }
            }
    }

    private fun parseSegments(display: String): List<Pair<SegmentKind, String>> {
        val segments = mutableListOf<Pair<SegmentKind, String>>()
        var pos = 0
        while (pos < display.length) {
            when {
                display[pos] == ACTION_OPEN -> {
                    val close = display.indexOf(ACTION_CLOSE, pos + 1)
                    if (close < 0) {
                        appendDialogueSegment(segments, display.substring(pos).trim())
                        return segments
                    }
                    val inner = display.substring(pos + 1, close).trim()
                    if (inner.isNotEmpty()) {
                        segments.add(SegmentKind.ACTION to inner)
                    }
                    pos = close + 1
                }
                else -> {
                    val nextAction = display.indexOf(ACTION_OPEN, pos)
                    val end = if (nextAction < 0) display.length else nextAction
                    val chunk = display.substring(pos, end)
                    appendDialogueSegment(segments, chunk)
                    pos = end
                }
            }
        }
        return segments
    }

    private fun appendDialogueSegment(
        segments: MutableList<Pair<SegmentKind, String>>,
        raw: String,
    ) {
        val text = stripOuterQuotes(raw.trim())
        if (text.isNotEmpty()) {
            segments.add(SegmentKind.DIALOGUE to text)
        }
    }

    private fun stripOuterQuotes(text: String): String {
        if (text.length >= 2) {
            val open = text.first()
            val close = text.last()
            if ((open == QUOTE_OPEN && close == QUOTE_CLOSE) ||
                (open == '"' && close == '"') ||
                (open == '\u300C' && close == '\u300D') ||
                (open == '\u300E' && close == '\u300F')
            ) {
                return text.substring(1, text.length - 1).trim()
            }
        }
        return text
    }

    fun wrapCurlyQuotes(text: String): String = "$QUOTE_OPEN$text$QUOTE_CLOSE"

    private fun commitActionDraft(input: String): String {
        val trimmed = input.trimEnd()
        if (trimmed.isEmpty()) return trimmed
        val lastOpen = trimmed.lastIndexOf(ACTION_OPEN)
        val lastClose = trimmed.lastIndexOf(ACTION_CLOSE)
        if (lastOpen > lastClose) {
            return trimmed + ACTION_CLOSE
        }
        if (!hasActionMarkup(trimmed)) {
            return "$ACTION_OPEN$trimmed$ACTION_CLOSE"
        }
        return trimmed
    }

    private fun openActionDraft(input: String): String {
        val trimmed = input.trimEnd()
        if (trimmed.isEmpty()) return ACTION_OPEN.toString()
        val lastOpen = trimmed.lastIndexOf(ACTION_OPEN)
        val lastClose = trimmed.lastIndexOf(ACTION_CLOSE)
        if (lastOpen > lastClose) return trimmed
        return trimmed + ACTION_OPEN
    }

    private fun closeUnclosedAction(text: String): String {
        val lastOpen = text.lastIndexOf(ACTION_OPEN)
        val lastClose = text.lastIndexOf(ACTION_CLOSE)
        return if (lastOpen > lastClose) text + ACTION_CLOSE else text
    }

    private fun isAlreadyModelContent(text: String): Boolean {
        return text.startsWith("a:") ||
            (text.startsWith(QUOTE_OPEN) && text.endsWith(QUOTE_CLOSE) && !hasActionMarkup(text))
    }
}
