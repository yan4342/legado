package io.legado.app.domain.usecase.structured

/**
 * Encodes/decodes legacy [OutlineDocument] helpers.
 * Runtime source of truth is [io.legado.app.domain.usecase.structured.graph.OutlineGraph]
 * via [io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec].
 */
object OutlineMarkdownCodec {

    /** Extracts editable body from stored outline content (after YAML front matter). */
    fun bodyFromStored(content: String): String {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return ""
        val graph = io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec.decode(trimmed).graph
        if (graph != null) {
            return io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec.encodeBody(graph)
        }
        val (_, body, _) = OutlineParser.splitFrontMatter(trimmed)
        return body.ifBlank { trimmed }
    }

    fun encode(doc: OutlineDocument, bodyOverride: String? = null): String {
        val graph = io.legado.app.domain.usecase.structured.graph.OutlineGraphDocumentBridge
            .applyAnchors(
                io.legado.app.domain.usecase.structured.graph.OutlineGraph.empty(doc.outlineKind),
                doc,
            )
        if (bodyOverride.isNullOrBlank() && doc.volumes.isEmpty()) {
            return io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec.encode(graph)
        }
        return encodeLegacyDocument(doc, bodyOverride)
    }

    /** Markdown body only. Anchors live in YAML. */
    fun encodeBody(doc: OutlineDocument): String = buildBody(doc)

    fun withBodyMarkdown(doc: OutlineDocument, bodyMarkdown: String): OutlineDocument {
        val wrapped = encode(doc, bodyMarkdown)
        return decode(wrapped)
    }

    fun decode(content: String): OutlineDocument {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return OutlineDocument()
        val decoded = io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec.decode(trimmed)
        if (decoded.graph != null) {
            return io.legado.app.domain.usecase.structured.graph.OutlineGraphDocumentBridge
                .toDocumentAnchors(decoded.graph)
        }
        return OutlineDocument()
    }

    fun emptyTemplate(depth: Int): OutlineDocument {
        val g = io.legado.app.domain.usecase.structured.graph.OutlineGraph.emptyTemplate()
        return io.legado.app.domain.usecase.structured.graph.OutlineGraphDocumentBridge
            .toDocumentAnchors(g)
    }

    private fun encodeLegacyDocument(doc: OutlineDocument, bodyOverride: String?): String {
        val fm = buildString {
            append("---\n")
            append("outline_format: 2\n")
            appendYamlLine("premise", doc.premise)
            appendYamlLine("current", doc.currentProgress)
            appendYamlLine("next", doc.nextGoal)
            append("in_progress: ${doc.inProgress}\n")
            append("outline_kind: ${doc.outlineKind.ifBlank { OutlineDocument.KIND_LINEAR }}\n")
            append("awaiting_choice: ${doc.awaitingChoice}\n")
            appendYamlLine("active_path", doc.activePath)
            append("---\n")
        }
        val body = bodyOverride?.trim() ?: buildBody(doc)
        return if (body.isBlank()) fm.trimEnd() else fm + "\n" + body
    }

    private fun buildBody(doc: OutlineDocument): String = buildString {
        doc.volumes.forEachIndexed { vi, volume ->
            val volId = "vol_${vi + 1}"
            append("## id:$volId ${volume.title.trim()}\n")
            volume.children.forEachIndexed { ci, child ->
                appendNode(child, headingLevel = 3, idHint = "n_${vi}_${ci}")
            }
            append("\n")
        }
    }.trimEnd()

    private fun StringBuilder.appendNode(node: OutlineNode, headingLevel: Int, idHint: String) {
        append("${"#".repeat(headingLevel)} id:$idHint ${node.title.trim()}\n")
        if (node.branchOptions.isNotEmpty()) {
            node.branchOptions.forEach { opt ->
                val mark = if (opt.selected) "x" else " "
                append("- [$mark] id:${opt.id} ${opt.label.trim()}\n")
            }
        }
        node.bullets.filter { it.isNotBlank() }.forEach { bullet ->
            append("- ${bullet.trim()}\n")
        }
        node.sections.forEachIndexed { i, section ->
            appendNode(section, headingLevel = headingLevel + 1, idHint = "${idHint}_s$i")
        }
    }

    private fun StringBuilder.appendYamlLine(key: String, value: String) {
        val escaped = value.replace("\n", " ").trim()
        if (escaped.any { it == ':' || it == '"' }) {
            append("$key: \"${escaped.replace("\"", "\\\"")}\"\n")
        } else {
            append("$key: $escaped\n")
        }
    }
}
