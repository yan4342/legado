package io.legado.app.domain.usecase.ai

/**
 * Splits roleplay text into narration vs dialogue segments using [findDialogueRanges].
 *
 * A quote span counts as dialogue only when the character immediately before the closing
 * quote is a sentence terminator (`。？！`). Unclosed quotes at EOF are ignored (streaming-safe).
 */
object DialogueNarrationSplitter {

    /** Sentence enders required immediately before a closing quote. */
    private val SENTENCE_ENDERS = setOf('。', '？', '！','…')

    private val QUOTE_PAIRS = listOf(
        '\u201c' to '\u201d', // “ ”
        '「' to '」',
        '『' to '』',
        '"' to '"',
    )

    sealed interface Segment {
        val text: String

        data class Narration(override val text: String) : Segment
        data class Dialogue(override val text: String) : Segment
    }

    /**
     * Find paired quote spans that end with a sentence terminator before the close quote.
     * Unclosed quotes at EOF are ignored (streaming-safe).
     */
    fun findDialogueRanges(text: String): List<IntRange> {
        if (text.isEmpty()) return emptyList()
        val openToClose = QUOTE_PAIRS.toMap()
        val closeToOpen = QUOTE_PAIRS.associate { (open, close) -> close to open }
        val stack = ArrayDeque<Pair<Char, Int>>()
        val ranges = mutableListOf<IntRange>()
        for (i in text.indices) {
            val c = text[i]
            val symmetric = openToClose[c] == c
            when {
                // ASCII " toggles: close when top is ", otherwise open.
                symmetric && stack.isNotEmpty() && stack.last().first == c -> {
                    val start = stack.removeLast().second
                    if (isSentenceClosedDialogue(text, start, i)) {
                        ranges.add(start..i)
                    }
                }
                c in openToClose -> stack.addLast(c to i)
                c in closeToOpen -> {
                    val expectedOpen = closeToOpen.getValue(c)
                    if (stack.isNotEmpty() && stack.last().first == expectedOpen) {
                        val start = stack.removeLast().second
                        if (isSentenceClosedDialogue(text, start, i)) {
                            ranges.add(start..i)
                        }
                    }
                }
            }
        }
        return ranges
    }

    /** True when [closeIndex] is preceded by `。？！` (and the span is at least open+ender+close). */
    private fun isSentenceClosedDialogue(text: String, openIndex: Int, closeIndex: Int): Boolean {
        if (closeIndex <= openIndex + 1) return false
        return text[closeIndex - 1] in SENTENCE_ENDERS
    }

    fun split(text: String): List<Segment> {
        if (text.isEmpty()) return emptyList()
        val ranges = findDialogueRanges(text).sortedBy { it.first }
        if (ranges.isEmpty()) {
            return listOf(Segment.Narration(text))
        }
        val out = mutableListOf<Segment>()
        var pos = 0
        for (range in ranges) {
            if (range.first < pos) continue
            if (pos < range.first) {
                val narr = text.substring(pos, range.first)
                if (narr.isNotBlank()) out.add(Segment.Narration(narr))
            }
            val dlg = text.substring(range.first, range.last + 1)
            if (dlg.isNotEmpty()) out.add(Segment.Dialogue(dlg))
            pos = range.last + 1
        }
        if (pos < text.length) {
            val narr = text.substring(pos)
            if (narr.isNotBlank()) out.add(Segment.Narration(narr))
        }
        return out.ifEmpty { listOf(Segment.Narration(text)) }
    }
}
