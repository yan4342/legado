package io.legado.app.domain.usecase.structured

import io.legado.app.domain.model.OutlinePatchMode
import io.legado.app.domain.usecase.structured.graph.OutlineGraph
import io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec
import io.legado.app.domain.usecase.structured.graph.OutlineGraphEngine
import io.legado.app.domain.usecase.structured.graph.OutlineGraphOp
import io.legado.app.domain.usecase.structured.graph.OutlineGraphResult

/**
 * Roleplay outline awaiting gates for graph-ops maintain.
 */
object OutlineAwaitingChoicePolicy {

    fun isAwaiting(content: String): Boolean {
        if (content.isBlank()) return false
        val decoded = OutlineGraphCodec.decode(content)
        val graph = decoded.graph ?: return false
        // Only the explicit gate blocks maintain — pre-planted future branches do not.
        return graph.awaitingChoice
    }

    fun isAllowedWhileAwaiting(mode: OutlinePatchMode, current: String): Boolean {
        return when (mode) {
            is OutlinePatchMode.GraphOps -> {
                val graph = OutlineGraphCodec.decode(current).graph ?: return false
                OutlineGraphEngine.isAllowedWhileAwaiting(mode.ops) &&
                    mode.ops.none { it is OutlineGraphOp.SelectOption } &&
                    dryRunAllowed(graph, mode.ops)
            }
            is OutlinePatchMode.Generate -> false
            // Legacy string modes blocked for roleplay maintain
            is OutlinePatchMode.Append,
            is OutlinePatchMode.SearchReplace,
            is OutlinePatchMode.PatchSection,
            is OutlinePatchMode.Replace -> false
        }
    }

    private fun dryRunAllowed(graph: OutlineGraph, ops: List<OutlineGraphOp>): Boolean {
        var g = graph
        for (op in ops) {
            when (val r = OutlineGraphEngine.apply(g, op, allowSelect = false)) {
                is OutlineGraphResult.Ok -> g = r.graph
                is OutlineGraphResult.Err -> return false
            }
        }
        return true
    }

    fun normalizeAwaitingFrontMatter(content: String): String {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return trimmed
        val decoded = OutlineGraphCodec.decode(trimmed)
        val graph = decoded.graph ?: return trimmed
        val normalized = OutlineGraphEngine.normalizeAwaiting(graph)
        return OutlineGraphCodec.encode(normalized)
    }
}
