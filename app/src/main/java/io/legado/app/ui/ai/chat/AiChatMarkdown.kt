package io.legado.app.ui.ai.chat

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.utils.GSON
import io.legado.app.utils.sendToClip
import java.io.File

// ---- Markdown data model ----

internal sealed interface MarkdownBlock {
    data class TextBlock(val annotatedString: AnnotatedString) : MarkdownBlock
    data class CodeBlock(val text: String) : MarkdownBlock
    data class SvgBlock(val svgSource: String) : MarkdownBlock
    data class TableBlock(
        val headers: List<String>,
        val alignments: List<Char>,  // 'L', 'C', 'R'
        val rows: List<List<String>>
    ) : MarkdownBlock
    data class DetailsBlock(val summary: String, val content: String) : MarkdownBlock
}

// ---- Markdown text composable ----

@Composable
fun MarkdownText(
    markdown: String,
    color: Color,
    modifier: Modifier = Modifier,
    dialogueColor: Color? = null,
    /** Source URLs (e.g. web_search results) so [N] citation badges open their link. */
    sources: List<String>? = null,
) {
    val blocks = remember(markdown, dialogueColor, color, sources) { parseMarkdownBlocks(markdown, color, dialogueColor, sources) }
    SelectionContainer {
        Column(modifier = modifier) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.TextBlock -> Text(
                        text = block.annotatedString,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is MarkdownBlock.CodeBlock -> Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = color.copy(alpha = 0.08f),
                    ) {
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                            color = color.copy(alpha = 0.85f),
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    is MarkdownBlock.SvgBlock -> SvgBlockCard(block, color)
                    is MarkdownBlock.TableBlock -> MarkdownTable(block, color)
                    is MarkdownBlock.DetailsBlock -> DetailsSection(block, color)
                }
            }
        }
    }
}

// ---- HTML preprocessor ----

private fun preprocessHtml(text: String): String {
    var result = decodeHtmlEntities(text)
    result = preprocessDetails(result)
    result = preprocessPreBlocks(result)
    result = preprocessBlockHtml(result)
    result = preprocessInlineHtml(result)
    return result
}

private fun decodeHtmlEntities(text: String): String = text
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&apos;", "'")
    .replace("&#x27;", "'")
    .replace("&nbsp;", " ")

private fun preprocessPreBlocks(text: String): String {
    val sb = StringBuilder()
    val regex = Regex(
        """<pre[^>]*>\s*(?:<code[^>]*>)?(.*?)(?:</code>\s*)?</pre>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    var lastEnd = 0
    for (m in regex.findAll(text)) {
        sb.append(text.substring(lastEnd, m.range.first))
        val code = m.groupValues[1]
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
        sb.append("\n```\n").append(code.trim()).append("\n```\n")
        lastEnd = m.range.last + 1
    }
    sb.append(text.substring(lastEnd))
    return sb.toString()
}

private fun preprocessDetails(text: String): String {
    val sb = StringBuilder()
    val regex = Regex(
        """<details[^>]*>(.*?)</details>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    var lastEnd = 0
    for (m in regex.findAll(text)) {
        sb.append(text.substring(lastEnd, m.range.first))
        val inner = m.groupValues[1]
        val summaryRegex = Regex(
            """<summary[^>]*>(.*?)</summary>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val summaryMatch = summaryRegex.find(inner)
        val summary = summaryMatch?.groupValues?.get(1)?.trim() ?: "Details"
        val content = if (summaryMatch != null) {
            (inner.substring(0, summaryMatch.range.first) +
                inner.substring(summaryMatch.range.last + 1)).trim()
        } else {
            inner.trim()
        }
        sb.append("\n:::details $summary\n$content\n:::\n")
        lastEnd = m.range.last + 1
    }
    sb.append(text.substring(lastEnd))
    return sb.toString()
}

private fun preprocessBlockHtml(text: String): String {
    var result = text

    result = Regex("""<br\s*/?>\s*""", RegexOption.IGNORE_CASE).replace(result, "\n")
    result = Regex("""<hr\s*/?>\s*""", RegexOption.IGNORE_CASE).replace(result, "\n---\n")

    result = Regex("""<h1[^>]*>(.*?)</h1>\s*""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).replace(result) { m ->
        "\n# ${m.groupValues[1].trim()}\n"
    }
    result = Regex("""<h2[^>]*>(.*?)</h2>\s*""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).replace(result) { m ->
        "\n## ${m.groupValues[1].trim()}\n"
    }
    result = Regex("""<h3[^>]*>(.*?)</h3>\s*""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).replace(result) { m ->
        "\n### ${m.groupValues[1].trim()}\n"
    }

    result = Regex("""<p[^>]*>(.*?)</p>\s*""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).replace(result) { m ->
        "\n${m.groupValues[1].trim()}\n"
    }

    result = Regex("""<blockquote[^>]*>(.*?)</blockquote>\s*""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).replace(result) { m ->
        "\n" + m.groupValues[1].trim().lines().joinToString("\n") { "> $it" } + "\n"
    }

    result = Regex("""<li[^>]*>(.*?)</li>\s*""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).replace(result) { m ->
        "- ${m.groupValues[1].trim()}\n"
    }

    return result
}

private fun preprocessInlineHtml(text: String): String {
    var result = text

    result = Regex("""<a\s+[^>]*href\s*=\s*"([^"]*)"[^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE).replace(result) { m ->
        "[${m.groupValues[2]}](${m.groupValues[1]})"
    }

    result = Regex("""<(b|strong)[^>]*>(.*?)</\1>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).replace(result) { m ->
        "**${m.groupValues[2]}**"
    }

    result = Regex("""<(i|em)[^>]*>(.*?)</\1>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).replace(result) { m ->
        "*${m.groupValues[2]}*"
    }

    result = Regex("""<code[^>]*>(.*?)</code>""", RegexOption.IGNORE_CASE).replace(result) { m ->
        "`${m.groupValues[1]}`"
    }

    result = Regex("""<(del|s|strike)[^>]*>(.*?)</\1>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).replace(result) { m ->
        "~~${m.groupValues[2]}~~"
    }

    return result
}

// ---- Block parser ----

private fun parseMarkdownBlocks(input: String, baseColor: Color, dialogueColor: Color? = null, sources: List<String>? = null): List<MarkdownBlock> {
    val preprocessed = preprocessHtml(input)
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = preprocessed.split("\n")
    var inCodeBlock = false
    var codeBlockBuf = StringBuilder()
    var textBuf = StringBuilder()
    var i = 0

    fun flushText() {
        if (textBuf.isNotBlank()) {
            val rawText = textBuf.toString().trimEnd()
            // Split raw text into SVG and non-SVG segments
            splitSvgSegments(rawText, baseColor, dialogueColor, blocks, sources)
            textBuf.clear()
        }
    }

    /** Parse a code-fence buffer; if it looks like SVG, produce an SvgBlock instead. */
    fun buildCodeOrSvgBlock(code: String): MarkdownBlock {
        val trimmed = code.trim()
        return if (trimmed.lines().any { it.trimStart().startsWith("<svg") } &&
            trimmed.contains("</svg>")) {
            MarkdownBlock.SvgBlock(trimmed)
        } else {
            MarkdownBlock.CodeBlock(trimmed)
        }
    }

    while (i < lines.size) {
        val rawLine = lines[i]
        val line = rawLine.trimEnd()
        val trimmed = line.trimStart()

        // Code-block fence
        if (trimmed.startsWith("```")) {
            flushText()
            if (inCodeBlock) {
                blocks.add(buildCodeOrSvgBlock(codeBlockBuf.toString().trimEnd()))
                codeBlockBuf = StringBuilder()
                inCodeBlock = false
            } else {
                inCodeBlock = true
            }
            i++
            continue
        }
        if (inCodeBlock) {
            if (codeBlockBuf.isNotEmpty()) codeBlockBuf.append("\n")
            codeBlockBuf.append(line)
            i++
            continue
        }

        // Blank line → paragraph break
        if (line.isBlank()) {
            flushText()
            i++
            continue
        }

        // Details block (:::details ... :::)
        if (trimmed.startsWith(":::details ")) {
            flushText()
            val summary = trimmed.removePrefix(":::details ").trim()
            val detailsContent = StringBuilder()
            i++
            while (i < lines.size) {
                val dl = lines[i].trimEnd()
                if (dl.trimStart() == ":::") {
                    i++
                    break
                }
                if (detailsContent.isNotEmpty()) detailsContent.append("\n")
                detailsContent.append(dl)
                i++
            }
            blocks.add(MarkdownBlock.DetailsBlock(summary, detailsContent.toString().trimEnd()))
            continue
        }

        // Table detection: current line has | and next line is a separator
        if (isTableRow(line) && i + 1 < lines.size && isTableSeparator(lines[i + 1].trimEnd())) {
            flushText()
            val headers = parseTableCells(line)
            val alignments = parseTableAlignments(lines[i + 1].trimEnd())
            val rows = mutableListOf<List<String>>()
            i += 2 // skip header + separator
            while (i < lines.size && isTableRow(lines[i].trimEnd())) {
                rows.add(parseTableCells(lines[i].trimEnd()))
                i++
            }
            blocks.add(MarkdownBlock.TableBlock(headers, alignments, rows))
            continue
        }

        // Accumulate text line
        if (textBuf.isNotEmpty()) textBuf.append("\n")
        textBuf.append(line)
        i++
    }

    // Dangling code block
    if (inCodeBlock && codeBlockBuf.isNotEmpty()) {
        blocks.add(buildCodeOrSvgBlock(codeBlockBuf.toString().trimEnd()))
    }
    flushText()
    return blocks
}

/** Detect raw <svg>…</svg> in text and split into alternating TextBlock / SvgBlock. */
private fun splitSvgSegments(
    rawText: String,
    baseColor: Color,
    dialogueColor: Color?,
    blocks: MutableList<MarkdownBlock>,
    sources: List<String>? = null,
) {
    val svgRegex = Regex("""<svg\b[\s\S]*?</svg>""", RegexOption.IGNORE_CASE)
    var last = 0
    for (m in svgRegex.findAll(rawText)) {
        if (m.range.first > last) {
            val before = rawText.substring(last, m.range.first).trimEnd()
            if (before.isNotBlank()) {
                blocks.add(MarkdownBlock.TextBlock(buildTextAnnotated(before, baseColor, dialogueColor, sources)))
            }
        }
        blocks.add(MarkdownBlock.SvgBlock(m.value))
        last = m.range.last + 1
    }
    if (last < rawText.length) {
        val after = rawText.substring(last).trimEnd()
        if (after.isNotBlank()) {
            blocks.add(MarkdownBlock.TextBlock(buildTextAnnotated(after, baseColor, dialogueColor, sources)))
        }
    }
    if (blocks.isEmpty() && last == 0) {
        blocks.add(MarkdownBlock.TextBlock(buildTextAnnotated(rawText, baseColor, dialogueColor, sources)))
    }
}

private fun isTableRow(line: String): Boolean {
    val t = line.trim()
    return t.startsWith("|") && t.endsWith("|") && t.length > 2
}

private fun isTableSeparator(line: String): Boolean {
    val t = line.trim()
    return t.startsWith("|") && t.endsWith("|") &&
        Regex("""^\|(\s*:?-{3,}:?\s*\|)+$""").matches(t)
}

private fun parseTableCells(line: String): List<String> =
    line.trim().removeSurrounding("|").split("|").map { it.trim() }

private fun parseTableAlignments(sep: String): List<Char> =
    sep.trim().removeSurrounding("|").split("|").map { c ->
        val s = c.trim()
        when {
            s.startsWith(":") && s.endsWith(":") -> 'C'
            s.endsWith(":") -> 'R'
            else -> 'L'
        }
    }

/** Build an [AnnotatedString] for a non-table text block. */
private fun buildTextAnnotated(text: String, baseColor: Color, dialogueColor: Color? = null, sources: List<String>? = null) = buildAnnotatedString {
    for ((lineIdx, rawLine) in text.split("\n").withIndex()) {
        if (lineIdx > 0) append("\n")
        val line = rawLine.trimEnd()
        val trimmed = line.trimStart()

        // Headings
        val h = Regex("""^(#{1,3})\s+(.+)$""").find(line)
        if (h != null) {
            val fs = when (h.groupValues[1].length) { 1 -> 20.sp; 2 -> 17.sp; else -> 15.sp }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = fs, color = baseColor)) {
                append(h.groupValues[2])
            }
            continue
        }
        // Horizontal rule
        if (Regex("""^(-{3,}|\*{3,})\s*$""").matches(trimmed)) {
            withStyle(SpanStyle(color = baseColor.copy(alpha = 0.3f))) { append("─────────") }
            continue
        }
        // Blockquote
        if (trimmed.startsWith("> ")) {
            val q = trimmed.removePrefix("> ")
            withStyle(SpanStyle(color = baseColor.copy(alpha = 0.7f), fontStyle = FontStyle.Italic)) {
                appendInline(q, baseColor.copy(alpha = 0.7f), dialogueColor, sources)
            }
            continue
        }
        // Unordered list
        val ul = Regex("""^(\s*)[-*]\s+(.+)$""").find(line)
        if (ul != null) {
            val indent = "  ".repeat(ul.groupValues[1].length / 2)
            append("$indent• ")
            appendInline(ul.groupValues[2], baseColor, dialogueColor, sources)
            continue
        }
        // Ordered list
        val ol = Regex("""^(\s*)(\d+)\.\s+(.+)$""").find(line)
        if (ol != null) {
            val indent = "  ".repeat(ol.groupValues[1].length / 2)
            append("$indent${ol.groupValues[2]}. ")
            appendInline(ol.groupValues[3], baseColor, dialogueColor, sources)
            continue
        }
        // Normal line
        appendInline(line, baseColor, dialogueColor, sources)
    }
}

/** @see io.legado.app.domain.usecase.ai.DialogueNarrationSplitter.findDialogueRanges */
internal fun findDialogueRanges(text: String): List<IntRange> =
    io.legado.app.domain.usecase.ai.DialogueNarrationSplitter.findDialogueRanges(text)

/** Tokenises [text] for inline markdown and optional dialogue quote highlighting. */
private fun AnnotatedString.Builder.appendInline(
    text: String,
    baseColor: Color,
    dialogueColor: Color? = null,
    sources: List<String>? = null,
) {
    val highlightColor = dialogueColor ?: run {
        appendInlineMarkdown(text, baseColor, sources)
        return
    }
    val dialogueRanges = findDialogueRanges(text)
    if (dialogueRanges.isEmpty()) {
        appendInlineMarkdown(text, baseColor, sources)
        return
    }

    var pos = 0
    for (range in dialogueRanges.sortedBy { it.first }) {
        if (range.first < pos) continue
        if (pos < range.first) {
            appendInlineMarkdown(text.substring(pos, range.first), baseColor, sources)
        }
        val quoted = text.substring(range.first, range.last + 1)
        withStyle(SpanStyle(color = highlightColor)) { append(quoted) }
        pos = range.last + 1
    }
    if (pos < text.length) {
        appendInlineMarkdown(text.substring(pos), baseColor, sources)
    }
}

/** Tokenises [text] for inline markdown, strips syntax markers, applies styles. */
private fun AnnotatedString.Builder.appendInlineMarkdown(text: String, baseColor: Color, sources: List<String>? = null) {
    data class Tok(val start: Int, val end: Int, val content: String, val style: SpanStyle, val url: String? = null)

    val base = SpanStyle(color = baseColor)
    val toks = mutableListOf<Tok>()

    // **bold**
    Regex("""\*\*(.+?)\*\*""").findAll(text).forEach { m ->
        toks.add(Tok(m.range.first, m.range.last + 1, m.groupValues[1], base.copy(fontWeight = FontWeight.Bold)))
    }
    // *italic*  (not **)
    Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)""").findAll(text).forEach { m ->
        toks.add(Tok(m.range.first, m.range.last + 1, m.groupValues[1], base.copy(fontStyle = FontStyle.Italic)))
    }
    // `inline code`
    Regex("""`([^`]+)`""").findAll(text).forEach { m ->
        toks.add(Tok(m.range.first, m.range.last + 1, m.groupValues[1], base.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)))
    }
    // ~~strikethrough~~
    Regex("""~~(.+?)~~""").findAll(text).forEach { m ->
        toks.add(Tok(m.range.first, m.range.last + 1, m.groupValues[1], base.copy(textDecoration = TextDecoration.LineThrough)))
    }
    // [text](url) — clickable link
    Regex("""\[(.+?)\]\((.+?)\)""").findAll(text).forEach { m ->
        toks.add(Tok(m.range.first, m.range.last + 1, m.groupValues[1], base.copy(textDecoration = TextDecoration.Underline), url = m.groupValues[2]))
    }
    // [N] citation badge — superscript, links to sources[N-1] when available
    Regex("""\[(\d+)\]""").findAll(text).forEach { m ->
        val num = m.groupValues[1].toIntOrNull()
        val url = num?.let { sources?.getOrNull(it - 1) }
        toks.add(
            Tok(
                m.range.first, m.range.last + 1, m.value,
                base.copy(
                    fontSize = 11.sp,
                    baselineShift = BaselineShift.Superscript,
                    textDecoration = if (url != null) TextDecoration.Underline else null,
                ),
                url = url,
            )
        )
    }
    // <u>text</u>
    Regex("""<u[^>]*>(.+?)</u>""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
        toks.add(Tok(m.range.first, m.range.last + 1, m.groupValues[1], base.copy(textDecoration = TextDecoration.Underline)))
    }
    // <kbd>key</kbd>
    Regex("""<kbd[^>]*>(.+?)</kbd>""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
        toks.add(Tok(m.range.first, m.range.last + 1, m.groupValues[1],
            base.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, background = baseColor.copy(alpha = 0.12f))))
    }

    toks.sortBy { it.start }

    // Remove overlapping tokens (prefer first/longest)
    val kept = mutableListOf<Tok>()
    var cutoff = 0
    for (t in toks) {
        if (t.start >= cutoff) {
            kept.add(t)
            cutoff = t.end
        }
    }

    var pos = 0
    var builderPos = 0
    for (t in kept) {
        if (t.start > pos) {
            val gap = text.substring(pos, t.start)
            withStyle(base) { append(gap) }
            builderPos += gap.length
        }
        val contentStart = builderPos
        withStyle(t.style) { append(t.content) }
        builderPos += t.content.length
        pos = t.end
        if (t.url != null) {
            addLink(
                LinkAnnotation.Url(
                    t.url,
                    TextLinkStyles(style = t.style),
                ),
                contentStart,
                builderPos,
            )
        }
    }
    if (pos < text.length) {
        withStyle(base) { append(text.substring(pos)) }
    }
}

// ---- Markdown table composable ----

@Composable
private fun MarkdownTable(table: MarkdownBlock.TableBlock, color: Color) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.06f),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(1.dp)) {
            // Header
            Row(modifier = Modifier.fillMaxWidth().background(color.copy(alpha = 0.12f))) {
                table.headers.forEachIndexed { idx, h ->
                    Box(
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = when (table.alignments.getOrElse(idx) { 'L' }) {
                            'R' -> Alignment.CenterEnd
                            'C' -> Alignment.Center
                            else -> Alignment.CenterStart
                        }
                    ) {
                        Text(
                            text = h,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            HorizontalDivider(color = color.copy(alpha = 0.15f))
            // Data rows
            table.rows.forEachIndexed { rowIdx, row ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .then(if (rowIdx % 2 == 1) Modifier.background(color.copy(alpha = 0.05f)) else Modifier)
                ) {
                    row.forEachIndexed { colIdx, cell ->
                        Box(
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = when (table.alignments.getOrElse(colIdx) { 'L' }) {
                                'R' -> Alignment.CenterEnd
                                'C' -> Alignment.Center
                                else -> Alignment.CenterStart
                            }
                        ) {
                            MarkdownText(
                                markdown = cell,
                                color = color,
                            )
                        }
                    }
                    // Pad empty cells to match header count
                    repeat(table.headers.size - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                if (rowIdx < table.rows.size - 1) {
                    HorizontalDivider(color = color.copy(alpha = 0.08f))
                }
            }
        }
    }
}

// ---- Details (collapsible) composable ----

@Composable
private fun DetailsSection(details: MarkdownBlock.DetailsBlock, color: Color) {
    val expanded = remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded.value = !expanded.value }
                .background(color.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expanded.value) "▼" else "▶",
                style = MaterialTheme.typography.bodySmall,
                color = color.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = details.summary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = color,
            )
        }
        if (expanded.value) {
            MarkdownText(
                markdown = details.content,
                color = color,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp),
            )
        }
    }
}

// ---- SVG in-text rendering ----

@Composable
private fun SvgBlockCard(svgBlock: MarkdownBlock.SvgBlock, color: Color) {
    val context = LocalContext.current
    val sanitized = remember(svgBlock.svgSource) {
        svgBlock.svgSource
            .replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<foreignObject[\s\S]*?</foreignObject>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bon\w+\s*=\s*"[^"]*"|\bon\w+\s*=\s*'[^']*'""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bon\w+\s*=\s*\S+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""javascript\s*:""", RegexOption.IGNORE_CASE), "")
    }
    val bitmap = remember(sanitized) {
        runCatching {
            val svg = com.caverock.androidsvg.SVG.getFromString(sanitized) ?: return@runCatching null
            val density = context.resources.displayMetrics.density
            val limitPx = (320f * density).toInt()
            val docW = svg.documentWidth.takeIf { it > 0f } ?: limitPx.toFloat()
            val docH = svg.documentHeight.takeIf { it > 0f } ?: limitPx.toFloat()
            val scale = minOf(limitPx / docW, limitPx / docH)
            val w = (docW * scale).toInt().coerceAtLeast(1)
            val h = (docH * scale).toInt().coerceAtLeast(1)
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            canvas.scale(scale, scale)
            svg.renderToCanvas(canvas)
            bmp
        }.getOrNull()
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        if (bitmap != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.04f),
            ) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "SVG",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .padding(8.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = {
                runCatching { context.sendToClip(sanitized) }
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("复制代码", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = {
                android.util.Log.d("svg", "SvgBlockCard: save clicked svgLen=${sanitized.length}")
                runCatching {
                    // Render at higher resolution for saving
                    val svg = com.caverock.androidsvg.SVG.getFromString(sanitized)
                        ?: return@runCatching
                    val density = context.resources.displayMetrics.density
                    val limitPx = (480f * density).toInt()
                    val docW = svg.documentWidth.takeIf { it > 0f } ?: limitPx.toFloat()
                    val docH = svg.documentHeight.takeIf { it > 0f } ?: limitPx.toFloat()
                    val scale = minOf(limitPx / docW, limitPx / docH)
                    val w = (docW * scale).toInt().coerceAtLeast(1)
                    val h = (docH * scale).toInt().coerceAtLeast(1)
                    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.scale(scale, scale)
                    svg.renderToCanvas(canvas)
                    val dir = File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_PICTURES,
                        ),
                        "legado",
                    )
                    dir.mkdirs()
                    val dest = File(dir, "ai_image_${System.currentTimeMillis()}.png")
                    dest.outputStream().use { os -> bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, os) }
                    bmp.recycle()
                    android.util.Log.d("svg", "SvgBlockCard: saved to ${dest.absolutePath} size=${dest.length()}")
                    android.widget.Toast.makeText(
                        context, "已保存到 " + dest.absolutePath, android.widget.Toast.LENGTH_SHORT
                    ).show()
                }.onFailure { e ->
                    android.util.Log.e("svg", "SvgBlockCard: save failed", e)
                    android.widget.Toast.makeText(
                        context, "保存失败: " + e.message, android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }) {
                Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("保存图片", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
