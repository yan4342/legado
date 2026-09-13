package io.legado.app.domain.usecase.structured.graph

import io.legado.app.domain.usecase.structured.OutlineDocument

/** Bridge anchors between [OutlineGraph] and legacy [OutlineDocument] UI fields. */
object OutlineGraphDocumentBridge {

    /** Human-readable active path for sheet UI, e.g. `附魔 → 触碰 → 献祭`. */
    fun formatActivePathLabel(graph: OutlineGraph): String {
        if (graph.activePath.isEmpty()) return ""
        return graph.activePath.joinToString(" → ") { id ->
            graph.nodes[id]?.title
                ?.removePrefix("分支：")
                ?.removePrefix("分支:")
                ?.trim()
                ?.ifBlank { id }
                ?: id
        }
    }

    fun toDocumentAnchors(graph: OutlineGraph): OutlineDocument =
        OutlineDocument(
            premise = graph.premise,
            currentProgress = graph.currentProgress,
            nextGoal = graph.nextGoal,
            inProgress = graph.inProgress,
            hierarchyDepth = 2,
            volumes = emptyList(),
            outlineKind = graph.outlineKind,
            awaitingChoice = graph.awaitingChoice,
            activePath = graph.activePath.joinToString("/"),
        )

    fun applyAnchors(graph: OutlineGraph, doc: OutlineDocument): OutlineGraph =
        graph.copy(
            premise = doc.premise,
            currentProgress = doc.currentProgress,
            nextGoal = doc.nextGoal,
            inProgress = doc.inProgress,
            outlineKind = doc.outlineKind.ifBlank { graph.outlineKind },
            awaitingChoice = doc.awaitingChoice,
            activePath = doc.activePath.split('/', ',', '|')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .ifEmpty { graph.activePath },
            currentNodeId = graph.currentNodeId,
        )

    fun newId(prefix: String): String =
        "${prefix}_${System.currentTimeMillis().toString(36).takeLast(6)}"
}
