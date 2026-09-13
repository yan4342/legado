package io.legado.app.domain.usecase.structured

import io.legado.app.domain.usecase.structured.graph.OutlineGraph
import io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec
import io.legado.app.domain.usecase.structured.graph.OutlineGraphEngine
import io.legado.app.domain.usecase.structured.graph.OutlineNodeType
import io.legado.app.utils.GSON

/**
 * Path-aware outline snapshot for auto-maintain prompts (JSON-shaped text).
 */
object OutlineMaintainSnapshotBuilder {
    const val BODY_EXCERPT_MAX = 800

    fun build(content: String, bodyExcerptMax: Int = BODY_EXCERPT_MAX): String {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return "(empty)"
        val decoded = OutlineGraphCodec.decode(trimmed)
        val graph = decoded.graph
        if (graph == null) {
            return "(unsupported_outline_format — regenerate required)"
        }
        return buildFromGraph(graph)
    }

    fun buildFromGraph(graph: OutlineGraph): String {
        val pending = OutlineGraphEngine.findPendingBranch(graph)
        val writable = OutlineGraphEngine.writableIds(graph)
        val activePathNodes = graph.activePath.mapNotNull { id ->
            graph.nodes[id]?.let { mapOf("id" to it.id, "title" to it.title) }
        }
        val frontierId = graph.activePath.lastOrNull()
            ?: graph.rootChildren().lastOrNull()?.id
        val frontier = frontierId?.let { graph.nodes[it] }
        val nearby = pending?.second?.map {
            mapOf("id" to it.id, "title" to it.title, "summary" to "pending")
        }.orEmpty().ifEmpty {
            // Sibling unread options under last selected branch
            siblingUnreadOptions(graph)
        }
        val writableToc = buildWritableToc(graph, writable)
        val payload = linkedMapOf(
            "anchors" to mapOf(
                "premise" to graph.premise,
                "current" to graph.currentProgress,
                "next" to graph.nextGoal,
                "in_progress" to graph.inProgress,
                "outline_kind" to graph.outlineKind,
                "awaiting_choice" to graph.awaitingChoice,
            ),
            "active_path" to activePathNodes,
            "current_frontier" to frontier?.let {
                mapOf(
                    "id" to it.id,
                    "title" to it.title,
                    "type" to it.type.name,
                    "bullets" to it.bullets,
                )
            },
            "nearby_unread_options" to nearby,
            "writable_toc" to writableToc,
            "pending_branch" to pending?.let { (branch, options) ->
                mapOf(
                    "id" to branch.id,
                    "title" to branch.title,
                    "options" to options.map { mapOf("id" to it.id, "title" to it.title) },
                )
            },
            "debug_tree" to graph.toDebugTree().lines().take(40).joinToString("\n"),
        )
        return GSON.toJson(payload)
    }

    private fun siblingUnreadOptions(graph: OutlineGraph): List<Map<String, String>> {
        val lastOpt = graph.activePath.lastOrNull() ?: return emptyList()
        val parentId = OutlineGraphEngine.findParent(graph, lastOpt) ?: return emptyList()
        val parent = graph.nodes[parentId] ?: return emptyList()
        if (parent.type != OutlineNodeType.BRANCH) return emptyList()
        return parent.children.mapNotNull { graph.nodes[it] }
            .filter { it.type == OutlineNodeType.OPTION && it.selected != true }
            .map { mapOf("id" to it.id, "title" to it.title, "summary" to "readonly") }
    }

    private fun buildWritableToc(graph: OutlineGraph, writable: Set<String>): List<String> {
        val lines = mutableListOf<String>()
        fun walk(id: String, depth: Int) {
            if (id !in writable && id != graph.rootId) return
            val n = graph.nodes[id] ?: return
            if (n.type != OutlineNodeType.ROOT) {
                lines.add("${"  ".repeat(depth)}${n.type.name} ${n.id} ${n.title}")
            }
            n.children.forEach { childId ->
                if (childId in writable || graph.nodes[childId]?.type == OutlineNodeType.BRANCH) {
                    walk(childId, if (n.type == OutlineNodeType.ROOT) 0 else depth + 1)
                }
            }
        }
        walk(graph.rootId, 0)
        return lines
    }
}
