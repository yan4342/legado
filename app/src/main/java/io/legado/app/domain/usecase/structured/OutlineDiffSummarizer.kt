package io.legado.app.domain.usecase.structured

/**
 * Produces a short human-readable diff summary (≤120 chars) for outline version history.
 */
object OutlineDiffSummarizer {

    private const val MAX_LEN = 120

    fun summarize(before: String, after: String): String {
        if (before == after) return "无变化"
        val beforeParsed = OutlineParser.parse(before)
        val afterParsed = OutlineParser.parse(after)
        val parts = mutableListOf<String>()
        if (beforeParsed.premise != afterParsed.premise) {
            parts.add("走向变更")
        }
        if (beforeParsed.currentProgress != afterParsed.currentProgress) {
            parts.add("进度: ${truncate(afterParsed.currentProgress)}")
        }
        if (beforeParsed.nextGoal != afterParsed.nextGoal) {
            parts.add("目标: ${truncate(afterParsed.nextGoal)}")
        }
        if (beforeParsed.inProgress != afterParsed.inProgress) {
            parts.add(if (afterParsed.inProgress) "标记进行中" else "取消进行中")
        }
        val lineDiff = StructuredDataDiff.diffTextLines(beforeParsed.body, afterParsed.body)
        if (lineDiff.isNotEmpty()) {
            parts.add("正文 ${lineDiff.size} 处变更")
        }
        val summary = parts.joinToString("；")
        return if (summary.length <= MAX_LEN) summary else summary.take(MAX_LEN - 1) + "…"
    }

    private fun truncate(text: String): String {
        val t = text.replace("\n", " ").trim()
        return if (t.length <= 24) t else t.take(21) + "…"
    }
}
