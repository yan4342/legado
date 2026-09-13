package io.legado.app.domain.usecase.structured.graph

import io.legado.app.domain.usecase.structured.OutlineParser

/**
 * Encodes/decodes [OutlineGraph] to YAML front matter + Markdown body (outline_format: 2).
 *
 * Headings: `## id:vol_1 Title`
 * Options: `- [ ] id:opt_a Label` with nested children indented under the option.
 */
object OutlineGraphCodec {

    data class DecodeResult(
        val graph: OutlineGraph?,
        val isFormatV2: Boolean,
        val error: String? = null,
    )

    fun isFormatV2(content: String): Boolean {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return true // empty is ok to start fresh
        val (fm, _, _) = OutlineParser.splitFrontMatter(trimmed)
        if (fm == null) return false
        val yaml = parseSimpleYaml(fm)
        return yaml["outline_format"]?.toIntOrNull() == OutlineGraph.FORMAT_VERSION
    }

    fun decode(content: String): DecodeResult {
        val trimmed = content.trim()
        if (trimmed.isBlank()) {
            return DecodeResult(OutlineGraph.empty(), isFormatV2 = true)
        }
        val (fm, body, _) = OutlineParser.splitFrontMatter(trimmed)
        if (fm == null) {
            return DecodeResult(null, isFormatV2 = false, error = "missing_front_matter")
        }
        val yaml = parseSimpleYaml(fm)
        val format = yaml["outline_format"]?.toIntOrNull()
        if (format != OutlineGraph.FORMAT_VERSION) {
            return DecodeResult(null, isFormatV2 = false, error = "unsupported_format")
        }
        return try {
            val graph = parseBody(
                body = body,
                premise = yaml["premise"].orEmpty().trim(),
                currentProgress = yaml["current"].orEmpty().trim(),
                nextGoal = yaml["next"].orEmpty().trim(),
                inProgress = yaml["in_progress"]?.toBooleanStrictOrNull() ?: false,
                outlineKind = yaml["outline_kind"]?.trim()?.lowercase().orEmpty().let {
                    if (it == OutlineGraph.KIND_BRANCHING) OutlineGraph.KIND_BRANCHING
                    else OutlineGraph.KIND_LINEAR
                },
                awaitingChoice = yaml["awaiting_choice"]?.toBooleanStrictOrNull() ?: false,
                activePath = parseActivePath(yaml["active_path"]),
                currentNodeId = yaml["current_node"]?.trim()?.ifBlank { null },
            )
            DecodeResult(graph, isFormatV2 = true)
        } catch (e: Exception) {
            DecodeResult(null, isFormatV2 = true, error = e.message ?: "parse_error")
        }
    }

    fun decodeOrEmpty(content: String): OutlineGraph =
        decode(content).graph ?: OutlineGraph.empty()

    /**
     * Light repair of common model mistakes before [decode].
     * Does not invent structure — only fixes wire-format glitches.
     */
    fun repairAiMarkdown(raw: String): String {
        var text = raw.trim()
            .replace(Regex("""^```(?:markdown|md)?\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*```$"""), "")
            .trim()
        if (text.isBlank()) return text
        // outline_kind aliases
        text = text.replace(Regex("""(?m)^(outline_kind:\s*)branch\s*$"""), "$1branching")
        text = text.replace(Regex("""(?m)^(outline_kind:\s*)branches\s*$"""), "$1branching")
        // Double list markers under options: "- - [ ]" → "- [ ]"
        text = text.replace(Regex("""(?m)^(\s*)-\s+-\s+\["""), "$1- [")
        // Missing closing front-matter fence: insert before first heading
        if (text.startsWith("---")) {
            val second = text.indexOf("\n---", 3)
            if (second < 0) {
                val heading = Regex("""(?m)^#{1,6}\s+""").find(text)
                if (heading != null) {
                    val at = heading.range.first
                    text = text.substring(0, at).trimEnd() + "\n---\n\n" + text.substring(at)
                }
            }
        }
        // Ensure outline_format: 2 present in FM
        if (text.startsWith("---")) {
            val end = text.indexOf("\n---", 3)
            if (end > 0) {
                val fm = text.substring(3, end)
                if (!fm.contains("outline_format:")) {
                    text = "---\noutline_format: 2\n" + fm.trimStart() + text.substring(end)
                }
            }
        }
        return text
    }

    /** Decode after [repairAiMarkdown]; returns error detail when still invalid. */
    fun decodeAiOutput(raw: String): DecodeResult {
        val repaired = repairAiMarkdown(raw)
        val result = decode(repaired)
        if (result.graph != null) return result
        // One more try: if still missing FM close, wrap whole as body with empty anchors
        if (!repaired.startsWith("---")) {
            val wrapped = buildString {
                append("---\noutline_format: 2\n")
                append("premise: \ncurrent: \nnext: \nin_progress: true\n")
                append("outline_kind: branching\nawaiting_choice: false\nactive_path: \n")
                append("current_node: \n")
                append("---\n\n")
                append(repaired)
            }
            return decode(wrapped)
        }
        return result
    }
    fun encode(graph: OutlineGraph): String {
        val fm = encodeFrontMatter(graph)
        val body = encodeBody(graph)
        return if (body.isBlank()) fm.trimEnd() else fm + "\n" + body
    }

    fun encodeBody(graph: OutlineGraph): String = buildString {
        graph.rootChildren().forEach { child ->
            appendNode(child, graph, headingLevel = 2, indent = 0)
            append("\n")
        }
    }.trimEnd()

    private fun encodeFrontMatter(graph: OutlineGraph): String = buildString {
        append("---\n")
        append("outline_format: ${OutlineGraph.FORMAT_VERSION}\n")
        appendYamlLine("premise", graph.premise)
        appendYamlLine("current", graph.currentProgress)
        appendYamlLine("next", graph.nextGoal)
        append("in_progress: ${graph.inProgress}\n")
        append("outline_kind: ${graph.outlineKind.ifBlank { OutlineGraph.KIND_LINEAR }}\n")
        append("awaiting_choice: ${graph.awaitingChoice}\n")
        appendYamlLine("active_path", graph.activePath.joinToString("/"))
        appendYamlLine("current_node", graph.currentNodeId.orEmpty())
        append("---\n")
    }

    private fun StringBuilder.appendNode(
        node: OutlineGraphNode,
        graph: OutlineGraph,
        headingLevel: Int,
        indent: Int,
    ) {
        val pad = " ".repeat(indent)
        when (node.type) {
            OutlineNodeType.OPTION -> {
                val mark = if (node.selected == true) "x" else " "
                append("$pad- [$mark] id:${node.id} ${node.title.trim()}\n")
                node.bullets.filter { it.isNotBlank() }.forEach { b ->
                    append("$pad  - ${b.trim()}\n")
                }
                node.children.mapNotNull { graph.nodes[it] }.forEach { child ->
                    when (child.type) {
                        OutlineNodeType.OPTION ->
                            appendNode(child, graph, headingLevel, indent) // shouldn't nest options as peers
                        else ->
                            appendNode(child, graph, (headingLevel + 1).coerceAtMost(6), indent + 2)
                    }
                }
            }
            OutlineNodeType.ROOT -> Unit
            else -> {
                val level = headingLevel.coerceIn(1, 6)
                append("$pad${"#".repeat(level)} id:${node.id} ${node.title.trim()}\n")
                if (node.type == OutlineNodeType.BRANCH) {
                    node.children.mapNotNull { graph.nodes[it] }.forEach { child ->
                        appendNode(child, graph, level + 1, indent)
                    }
                } else {
                    node.bullets.filter { it.isNotBlank() }.forEach { b ->
                        append("$pad- ${b.trim()}\n")
                    }
                    node.children.mapNotNull { graph.nodes[it] }.forEach { child ->
                        appendNode(child, graph, level + 1, indent)
                    }
                }
            }
        }
    }

    private fun parseBody(
        body: String,
        premise: String,
        currentProgress: String,
        nextGoal: String,
        inProgress: Boolean,
        outlineKind: String,
        awaitingChoice: Boolean,
        activePath: List<String>,
        currentNodeId: String?,
    ): OutlineGraph {
        val nodes = linkedMapOf<String, OutlineGraphNode>()
        nodes[OutlineGraph.ROOT_ID] = OutlineGraphNode(
            id = OutlineGraph.ROOT_ID,
            type = OutlineNodeType.ROOT,
            title = "root",
        )
        if (body.isBlank()) {
            return OutlineGraph(
                premise = premise,
                currentProgress = currentProgress,
                nextGoal = nextGoal,
                inProgress = inProgress,
                outlineKind = outlineKind,
                awaitingChoice = awaitingChoice,
                activePath = activePath,
                currentNodeId = currentNodeId,
                nodes = nodes,
            )
        }

        data class Frame(
            val id: String,
            val headingLevel: Int,
            /** For OPTION frames: indent of the option line. */
            val optionIndent: Int = -1,
        )

        val stack = ArrayDeque<Frame>()
        stack.addLast(Frame(OutlineGraph.ROOT_ID, 0))

        val lines = body.lines()
        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val trimmed = raw.trimEnd()
            if (trimmed.isBlank()) {
                i++
                continue
            }
            val indent = leadingSpaces(raw)
            val heading = HEADING_REGEX.matchEntire(trimmed.trim())
            val option = OPTION_REGEX.matchEntire(trimmed.trim())
            when {
                heading != null -> {
                    val level = heading.groupValues[1].length
                    val (id, title) = parseIdTitle(heading.groupValues[2].trim())
                    val type = inferHeadingType(level, title)
                    // Pop until parent heading level < this level; also leave option frames
                    while (stack.size > 1) {
                        val top = stack.last()
                        val topNode = nodes[top.id]!!
                        when {
                            topNode.type == OutlineNodeType.OPTION -> {
                                // Nested heading under option: keep if indent > option indent
                                if (indent > top.optionIndent && level > 1) break
                                stack.removeLast()
                            }
                            top.headingLevel >= level -> stack.removeLast()
                            else -> break
                        }
                    }
                    val parentId = stack.last().id
                    require(id.isNotBlank()) { "heading_missing_id: $title" }
                    require(!nodes.containsKey(id)) { "duplicate_id: $id" }
                    nodes[id] = OutlineGraphNode(id = id, type = type, title = title)
                    attachChild(nodes, parentId, id)
                    stack.addLast(Frame(id, level))
                    i++
                }
                option != null -> {
                    val selected = option.groupValues[1].equals("x", ignoreCase = true)
                    val id = option.groupValues[2]
                    val title = option.groupValues[3].trim()
                    // Options attach to nearest BRANCH (or current parent)
                    while (stack.size > 1) {
                        val top = stack.last()
                        val topNode = nodes[top.id]!!
                        when {
                            topNode.type == OutlineNodeType.OPTION -> {
                                // Sibling option: pop previous option
                                if (indent <= top.optionIndent) stack.removeLast()
                                else break
                            }
                            topNode.type == OutlineNodeType.BRANCH -> break
                            top.headingLevel >= 3 && topNode.type != OutlineNodeType.BRANCH -> {
                                // Prefer attaching to BRANCH; if somehow under chapter, pop
                                stack.removeLast()
                            }
                            else -> break
                        }
                    }
                    var parentId = stack.last().id
                    var parent = nodes[parentId]!!
                    if (parent.type != OutlineNodeType.BRANCH) {
                        // Find BRANCH in stack
                        val branchFrame = stack.lastOrNull { nodes[it.id]?.type == OutlineNodeType.BRANCH }
                        if (branchFrame != null) {
                            while (stack.last().id != branchFrame.id) stack.removeLast()
                            parentId = branchFrame.id
                            parent = nodes[parentId]!!
                        }
                    }
                    require(parent.type == OutlineNodeType.BRANCH) {
                        "option_not_under_branch: $id"
                    }
                    require(!nodes.containsKey(id)) { "duplicate_id: $id" }
                    nodes[id] = OutlineGraphNode(
                        id = id,
                        type = OutlineNodeType.OPTION,
                        title = title,
                        selected = selected,
                    )
                    attachChild(nodes, parentId, id)
                    stack.addLast(Frame(id, headingLevel = Int.MAX_VALUE, optionIndent = indent))
                    i++
                }
                BULLET_REGEX.matchEntire(trimmed.trim()) != null -> {
                    val text = BULLET_REGEX.matchEntire(trimmed.trim())!!.groupValues[1].trim()
                    // Skip if it looks like an option we missed
                    if (OPTION_REGEX.matchEntire(trimmed.trim()) != null) {
                        i++
                        continue
                    }
                    while (stack.size > 1) {
                        val top = stack.last()
                        val topNode = nodes[top.id]!!
                        if (topNode.type == OutlineNodeType.OPTION && indent <= top.optionIndent) {
                            stack.removeLast()
                        } else break
                    }
                    val parentId = stack.last().id
                    val parent = nodes[parentId]!!
                    nodes[parentId] = parent.copy(bullets = parent.bullets + text)
                    i++
                }
                else -> i++
            }
        }

        return OutlineGraph(
            premise = premise,
            currentProgress = currentProgress,
            nextGoal = nextGoal,
            inProgress = inProgress,
            outlineKind = outlineKind,
            awaitingChoice = awaitingChoice,
            activePath = activePath,
            currentNodeId = currentNodeId,
            nodes = nodes,
        )
    }

    private fun attachChild(
        nodes: MutableMap<String, OutlineGraphNode>,
        parentId: String,
        childId: String,
    ) {
        val parent = nodes[parentId] ?: return
        nodes[parentId] = parent.copy(children = parent.children + childId)
    }

    private fun inferHeadingType(level: Int, title: String): OutlineNodeType {
        if (isBranchTitle(title)) return OutlineNodeType.BRANCH
        return when (level) {
            1, 2 -> OutlineNodeType.VOLUME
            3 -> OutlineNodeType.CHAPTER
            else -> OutlineNodeType.SECTION
        }
    }

    fun isBranchTitle(title: String): Boolean {
        val t = title.trim()
        return t.startsWith("分支:") || t.startsWith("分支：") ||
            t.contains("分支:") || t.contains("分支：")
    }

    private fun parseIdTitle(raw: String): Pair<String, String> {
        val m = ID_TITLE_REGEX.matchEntire(raw)
        return if (m != null) {
            m.groupValues[1] to m.groupValues[2].trim()
        } else {
            // Generate id from title for robustness; prefer requiring id
            val id = "n_" + raw.hashCode().toString(16).replace("-", "m")
            id to raw
        }
    }

    private fun parseActivePath(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split('/', ',', '|').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun leadingSpaces(line: String): Int {
        var n = 0
        for (c in line) {
            if (c == ' ') n++ else if (c == '\t') n += 2 else break
        }
        return n
    }

    private fun parseSimpleYaml(text: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        text.lineSequence().forEach { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return@forEach
            val colon = t.indexOf(':')
            if (colon <= 0) return@forEach
            val key = t.substring(0, colon).trim()
            var value = t.substring(colon + 1).trim()
            if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length - 1).replace("\\\"", "\"")
            }
            map[key] = value
        }
        return map
    }

    private fun StringBuilder.appendYamlLine(key: String, value: String) {
        val escaped = value.replace("\n", " ").trim()
        if (escaped.any { it == ':' || it == '"' }) {
            append("$key: \"${escaped.replace("\"", "\\\"")}\"\n")
        } else {
            append("$key: $escaped\n")
        }
    }

    private val HEADING_REGEX = Regex("""^(#{1,6})\s+(.+?)\s*$""")
    private val ID_TITLE_REGEX = Regex("""^id:([A-Za-z0-9_\-]+)\s+(.+)$""")
    private val OPTION_REGEX = Regex(
        """^[-*]\s+\[([ xX])\]\s+id:([A-Za-z0-9_\-]+)\s+(.+?)\s*$""",
    )
    private val BULLET_REGEX = Regex("""^[-*]\s+(.+)$""")
}
