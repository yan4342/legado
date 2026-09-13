package io.legado.app.ui.ai.chat

/** Strip markdown fences, scripts, and duplicate HUD roots from model output. */
internal fun cleanGalgameHudHtml(raw: String): String {
    val trimmed = raw.trim()
    var cleaned = trimmed
        .removeSurrounding("```html", "```")
        .removeSurrounding("```", "```")
        .trim()
    cleaned = dedupeGalgameHudHtml(cleaned)
    return cleaned
        .replace(Regex("<script[^>]*>.*?</script>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("<script[^>]*/>", RegexOption.IGNORE_CASE), "")
}

/** If the model appended multiple HUD roots, keep the last block (+ shared style). */
internal fun dedupeGalgameHudHtml(html: String): String {
    val hudMarker = Regex("""<div\s+class=["']hud["']""", RegexOption.IGNORE_CASE)
    val hits = hudMarker.findAll(html).toList()
    if (hits.size <= 1) return html

    val styleBlock = Regex(
        """<style[^>]*>.*?</style>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    ).find(html)?.value?.trim().orEmpty()

    val lastHud = html.substring(hits.last().range.first).trim()
    return buildString {
        if (styleBlock.isNotEmpty()) {
            append(styleBlock)
            append('\n')
        }
        append(lastHud)
    }.trim()
}
