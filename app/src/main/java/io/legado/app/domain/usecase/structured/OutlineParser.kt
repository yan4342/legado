package io.legado.app.domain.usecase.structured

/**
 * Parses outline content for injection, search, and read tools.
 * Prefers YAML front matter; falls back to legacy ## section headers.
 */
object OutlineParser {

    data class OutlineSection(
        val title: String,
        val offset: Int,
        val level: Int,
    )

    data class ParsedOutline(
        val premise: String = "",
        val currentProgress: String = "",
        val nextGoal: String = "",
        val inProgress: Boolean = false,
        val hierarchyDepth: Int = 2,
        val sections: List<OutlineSection> = emptyList(),
        val contentHash: String = "",
        val body: String = "",
        val bodyStartOffset: Int = 0,
        val outlineKind: String = OutlineDocument.KIND_LINEAR,
        val awaitingChoice: Boolean = false,
        val activePath: String = "",
    )

    private val headingRegex = Regex("""(?m)^(#{1,6})\s+(.+?)\s*$""")
    private val bulletRegex = Regex("""^\s*[-*]\s+(.+)$""")

    fun parse(content: String): ParsedOutline {
        val trimmed = content.trim()
        if (trimmed.isBlank()) {
            return ParsedOutline(contentHash = "0")
        }
        val (frontMatter, body, bodyStart) = splitFrontMatter(trimmed)
        val yaml = frontMatter?.let { parseSimpleYaml(it) }.orEmpty()
        val bodySections = parseSections(body, bodyStart)
        val legacy = if (yaml.isEmpty() && bodySections.isNotEmpty()) {
            extractLegacyAnchors(body, bodySections)
        } else {
            null
        }
        val premise = yaml["premise"] ?: legacy?.premise.orEmpty()
        val current = yaml["current"] ?: legacy?.currentProgress.orEmpty()
        val next = yaml["next"] ?: legacy?.nextGoal.orEmpty()
        val inProgress = yaml["in_progress"]?.toBooleanStrictOrNull()
            ?: legacy?.inProgress
            ?: current.contains("🔄")
        val depth = yaml["hierarchy_depth"]?.toIntOrNull()?.let { if (it == 3) 3 else 2 }
            ?: 2
        val outlineKind = yaml["outline_kind"]?.trim()?.lowercase().orEmpty().let {
            if (it == OutlineDocument.KIND_BRANCHING) OutlineDocument.KIND_BRANCHING
            else OutlineDocument.KIND_LINEAR
        }
        val awaitingChoice = yaml["awaiting_choice"]?.toBooleanStrictOrNull() ?: false
        val activePath = yaml["active_path"]?.trim().orEmpty()
        val sectionTitles = bodySections
            .filter { it.title !in LEGACY_ANCHOR_SECTIONS }
            .take(MAX_INDEX_SECTIONS)
        return ParsedOutline(
            premise = premise.trim(),
            currentProgress = current.trim(),
            nextGoal = next.trim(),
            inProgress = inProgress,
            hierarchyDepth = depth,
            sections = sectionTitles,
            contentHash = trimmed.hashCode().toString(16),
            body = body,
            bodyStartOffset = bodyStart,
            outlineKind = outlineKind,
            awaitingChoice = awaitingChoice,
            activePath = activePath,
        )
    }

    fun bodyTail(content: String, maxLen: Int = OUTLINE_TAIL_MAX): String {
        val parsed = parse(content)
        val body = parsed.body.ifBlank { content }
        val compact = body.replace("\n", " ").trim()
        if (compact.length <= maxLen) return compact
        return "…" + compact.takeLast(maxLen)
    }

    fun splitFrontMatter(content: String): Triple<String?, String, Int> {
        if (!content.startsWith("---")) {
            return Triple(null, content, 0)
        }
        val end = content.indexOf("\n---", 3)
        if (end < 0) return Triple(null, content, 0)
        val fm = content.substring(3, end).trim()
        val body = content.substring(end + 4).trimStart('\n', '\r')
        val bodyStart = content.length - body.length
        return Triple(fm, body, bodyStart)
    }

    private fun parseSections(body: String, baseOffset: Int): List<OutlineSection> {
        if (body.isBlank()) return emptyList()
        return headingRegex.findAll(body).map { match ->
            OutlineSection(
                title = match.groupValues[2].trim(),
                offset = baseOffset + match.range.first,
                level = match.groupValues[1].length,
            )
        }.toList()
    }

    private data class LegacyAnchors(
        val premise: String,
        val currentProgress: String,
        val nextGoal: String,
        val inProgress: Boolean,
    )

    private fun extractLegacyAnchors(body: String, sections: List<OutlineSection>): LegacyAnchors {
        var premise = ""
        var current = ""
        var next = ""
        val bodyStartInContent = sections.firstOrNull()?.let { it.offset } ?: 0
        sections.forEachIndexed { index, section ->
            val relStart = (section.offset - bodyStartInContent).coerceIn(0, body.length)
            val relEnd = sections.getOrNull(index + 1)?.let { nextSec ->
                (nextSec.offset - bodyStartInContent).coerceIn(0, body.length)
            } ?: body.length
            val slice = body.substring(relStart, relEnd)
            val text = slice.lines().drop(1).joinToString("\n").trim()
            val firstLine = text.lineSequence().firstOrNull().orEmpty()
            when {
                section.title.contains("整体走向") -> premise = firstLine
                section.title.contains("当前进度") -> current = firstLine
                section.title.contains("下一目标") -> next = firstLine
            }
        }
        if (current.isBlank()) {
            current = body.lineSequence().firstOrNull { "🔄" in it }?.trim().orEmpty()
        }
        return LegacyAnchors(
            premise = premise,
            currentProgress = current,
            nextGoal = next,
            inProgress = current.contains("🔄"),
        )
    }

    private fun parseSimpleYaml(text: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val idx = trimmed.indexOf(':')
            if (idx <= 0) continue
            val key = trimmed.substring(0, idx).trim()
            var value = trimmed.substring(idx + 1).trim()
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length - 1)
            }
            map[key] = value
        }
        return map
    }

    fun parseBodyToVolumes(body: String, depth: Int): List<OutlineVolume> {
        if (body.isBlank()) return emptyList()
        val lines = body.lines()
        val volumes = mutableListOf<OutlineVolume>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val h2 = matchHeading(line, 2)
            if (h2 == null || h2 in LEGACY_ANCHOR_SECTIONS || LEGACY_ANCHOR_SECTIONS.any { h2.contains(it) }) {
                i++
                continue
            }
            i++
            val children = mutableListOf<OutlineNode>()
            while (i < lines.size && !isHeadingLevel(lines[i], 2)) {
                val h3 = matchHeading(lines[i], 3)
                if (h3 != null) {
                    val (node, nextIndex) = readNode(lines, i, depth)
                    children.add(node)
                    i = nextIndex
                } else {
                    i++
                }
            }
            volumes.add(OutlineVolume(title = h2, children = children))
        }
        return volumes
    }

    private fun readNode(lines: List<String>, start: Int, depth: Int): Pair<OutlineNode, Int> {
        val title = matchHeading(lines[start], 3).orEmpty()
        var i = start + 1
        val bullets = mutableListOf<String>()
        val sections = mutableListOf<OutlineNode>()
        while (i < lines.size) {
            when {
                isHeadingLevel(lines[i], 2) || isHeadingLevel(lines[i], 3) -> break
                depth == 3 && isHeadingLevel(lines[i], 4) -> {
                    val (section, next) = readSection(lines, i)
                    sections.add(section)
                    i = next
                }
                else -> {
                    bulletRegex.matchEntire(lines[i].trim())?.let { bullets.add(it.groupValues[1].trim()) }
                    i++
                }
            }
        }
        return OutlineNode(title = title, bullets = bullets, sections = sections) to i
    }

    private fun readSection(lines: List<String>, start: Int): Pair<OutlineNode, Int> {
        val title = matchHeading(lines[start], 4).orEmpty()
        var i = start + 1
        val bullets = mutableListOf<String>()
        while (i < lines.size) {
            when {
                isHeadingLevel(lines[i], 2) || isHeadingLevel(lines[i], 3) || isHeadingLevel(lines[i], 4) -> break
                else -> {
                    bulletRegex.matchEntire(lines[i].trim())?.let { bullets.add(it.groupValues[1].trim()) }
                    i++
                }
            }
        }
        return OutlineNode(title = title, bullets = bullets) to i
    }

    private fun matchHeading(line: String, level: Int): String? {
        val m = Regex("""^#{$level}\s+(.+?)\s*$""").matchEntire(line.trim()) ?: return null
        return m.groupValues[1].trim()
    }

    private fun isHeadingLevel(line: String, level: Int): Boolean =
        line.trim().startsWith("#".repeat(level) + " ") &&
            !line.trim().startsWith("#".repeat(level + 1))

    private val LEGACY_ANCHOR_SECTIONS = setOf("整体走向", "当前进度", "下一目标")

    const val OUTLINE_TAIL_MAX = 300
    private const val MAX_INDEX_SECTIONS = 12
}
