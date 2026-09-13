package io.legado.app.domain.usecase.structured

import io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec
import io.legado.app.domain.usecase.structured.graph.OutlineGraphEngine
import io.legado.app.domain.usecase.structured.graph.OutlineGraphResult

/**
 * Compatibility facade over [OutlineGraphEngine] for branch discovery / selection.
 */
object OutlineBranchParser {

    data class BranchOption(
        val id: String,
        val label: String,
        val selected: Boolean,
        val lineIndex: Int = -1,
    )

    data class PendingBranch(
        val title: String,
        val options: List<BranchOption>,
    )

    fun isBranchHeading(title: String): Boolean =
        OutlineGraphCodec.isBranchTitle(title)

    fun pendingOptions(content: String): List<BranchOption> {
        val branch = findAwaitingBranch(content) ?: return emptyList()
        return branch.options.filter { !it.selected }
    }

    fun findAwaitingBranch(content: String): PendingBranch? {
        val graph = OutlineGraphCodec.decode(content).graph ?: return null
        if (!graph.awaitingChoice) return null
        val pending = OutlineGraphEngine.findPendingBranch(graph) ?: return null
        return PendingBranch(
            title = pending.first.title,
            options = pending.second.map {
                BranchOption(
                    id = it.id,
                    label = it.title,
                    selected = it.selected == true,
                )
            },
        )
    }

    fun selectOption(content: String, optionId: String): String? {
        val decoded = OutlineGraphCodec.decode(content)
        val graph = decoded.graph ?: return null
        return when (val r = OutlineGraphEngine.selectOption(graph, optionId)) {
            is OutlineGraphResult.Ok -> OutlineGraphCodec.encode(r.graph)
            is OutlineGraphResult.Err -> null
        }
    }
}
