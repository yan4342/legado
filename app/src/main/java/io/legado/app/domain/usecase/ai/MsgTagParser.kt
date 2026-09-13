package io.legado.app.domain.usecase.ai

/**
 * Streaming-friendly parser for chat multi-bubble `<msg>` protocol.
 *
 * Tags: `<msg>` / `<msg mode="long">` … `</msg>`
 * Long-form fallback: no pairs, single long msg, table/code fence, or overlong body.
 */
object MsgTagParser {

    data class Bubble(
        val text: String,
        val longForm: Boolean = false,
    )

    private val openTagRegex = Regex(
        """<msg(?:\s+mode\s*=\s*["'](long)["'])?\s*>""",
        RegexOption.IGNORE_CASE,
    )
    private val closeTag = "</msg>"
    private val anyMsgTagRegex = Regex(
        """</?msg(?:\s+[^>]*)?\s*>""",
        RegexOption.IGNORE_CASE,
    )

    private const val LONG_TEXT_THRESHOLD = 800

    /** Parse a complete assistant body into display bubbles. */
    fun split(fullText: String): List<Bubble> {
        val trimmed = fullText.trim()
        if (trimmed.isEmpty()) return emptyList()

        val pairs = extractClosedBubbles(trimmed)
        if (pairs.isEmpty()) {
            return listOf(Bubble(stripAllMsgTags(trimmed), longForm = looksLongForm(trimmed)))
        }
        if (pairs.size == 1 && pairs[0].longForm) {
            return listOf(Bubble(pairs[0].text, longForm = true))
        }
        if (shouldForceSingle(trimmed, pairs)) {
            return listOf(Bubble(stripAllMsgTags(trimmed), longForm = true))
        }
        // Trailing text outside tags (model forgot a tag) — append as its own bubble if non-blank.
        val afterLast = trailingOutsideTags(trimmed)
        return if (afterLast.isNotBlank()) pairs + Bubble(afterLast.trim()) else pairs
    }

    /**
     * Incremental feed for streaming UI. Returns newly completed bubbles since [emittedCount],
     * plus optional [openPartial] text inside an unclosed `<msg>`.
     */
    fun feed(
        buffer: String,
        emittedCount: Int,
    ): FeedResult {
        val completed = mutableListOf<Bubble>()
        var searchFrom = 0
        var openStart = -1
        var openLong = false
        var idx = 0
        while (true) {
            if (openStart < 0) {
                val m = openTagRegex.find(buffer, searchFrom) ?: break
                openStart = m.range.last + 1
                openLong = m.groupValues.getOrNull(1)?.equals("long", ignoreCase = true) == true
                searchFrom = openStart
            } else {
                val closeIdx = buffer.indexOf(closeTag, startIndex = openStart, ignoreCase = true)
                if (closeIdx < 0) break
                val body = buffer.substring(openStart, closeIdx).trim()
                if (body.isNotEmpty() || openLong) {
                    completed.add(Bubble(body, longForm = openLong))
                }
                searchFrom = closeIdx + closeTag.length
                openStart = -1
                openLong = false
                idx++
            }
        }
        val newOnes = if (emittedCount < completed.size) {
            completed.subList(emittedCount, completed.size)
        } else {
            emptyList()
        }
        val partial = if (openStart >= 0 && openStart <= buffer.length) {
            buffer.substring(openStart)
        } else {
            null
        }
        return FeedResult(
            newlyCompleted = newOnes,
            totalCompleted = completed.size,
            openPartial = partial?.takeIf { it.isNotEmpty() },
            openIsLong = openLong,
        )
    }

    data class FeedResult(
        val newlyCompleted: List<Bubble>,
        val totalCompleted: Int,
        val openPartial: String?,
        val openIsLong: Boolean,
    )

    fun stripAllMsgTags(text: String): String =
        anyMsgTagRegex.replace(text, "").trim()

    fun looksLongForm(text: String): Boolean {
        if (text.length > LONG_TEXT_THRESHOLD) return true
        if (text.contains("```")) return true
        // Markdown table: header separator like |---|---|
        if (Regex("""\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?""").containsMatchIn(text)) return true
        // Multiple pipe rows
        val pipeLines = text.lineSequence().count { line ->
            val t = line.trim()
            t.startsWith("|") && t.count { it == '|' } >= 2
        }
        return pipeLines >= 2
    }

    private fun shouldForceSingle(full: String, pairs: List<Bubble>): Boolean {
        val stripped = stripAllMsgTags(full)
        // Tables / code fences always stay one bubble (even if model wrapped multiple tags).
        if (looksLongForm(stripped)) return true
        if (pairs.size <= 1 && full.length > LONG_TEXT_THRESHOLD) return true
        return false
    }

    private fun extractClosedBubbles(text: String): List<Bubble> {
        val out = mutableListOf<Bubble>()
        var searchFrom = 0
        while (true) {
            val m = openTagRegex.find(text, searchFrom) ?: break
            val bodyStart = m.range.last + 1
            val longForm = m.groupValues.getOrNull(1)?.equals("long", ignoreCase = true) == true
            val closeIdx = text.indexOf(closeTag, startIndex = bodyStart, ignoreCase = true)
            if (closeIdx < 0) break
            val body = text.substring(bodyStart, closeIdx).trim()
            if (body.isNotEmpty() || longForm) {
                out.add(Bubble(body, longForm = longForm))
            }
            searchFrom = closeIdx + closeTag.length
        }
        return out
    }

    private fun trailingOutsideTags(text: String): String {
        var searchFrom = 0
        var lastEnd = 0
        var found = false
        while (true) {
            val m = openTagRegex.find(text, searchFrom) ?: break
            found = true
            val bodyStart = m.range.last + 1
            val closeIdx = text.indexOf(closeTag, startIndex = bodyStart, ignoreCase = true)
            if (closeIdx < 0) {
                return ""
            }
            lastEnd = closeIdx + closeTag.length
            searchFrom = lastEnd
        }
        if (!found) return ""
        return text.substring(lastEnd)
    }
}
