package io.legado.app.domain.usecase.structured.graph

/**
 * Runtime outline tree (source of truth). Persisted via [OutlineGraphCodec] as YAML+Markdown v2.
 */
enum class OutlineNodeType {
    ROOT,
    VOLUME,
    CHAPTER,
    SECTION,
    BRANCH,
    OPTION,
}

data class OutlineGraphNode(
    val id: String,
    val type: OutlineNodeType,
    val title: String,
    val bullets: List<String> = emptyList(),
    /** OPTION only: selected path marker. */
    val selected: Boolean? = null,
    val children: List<String> = emptyList(),
)

data class OutlineGraph(
    val version: Int = FORMAT_VERSION,
    val premise: String = "",
    val currentProgress: String = "",
    val nextGoal: String = "",
    val inProgress: Boolean = false,
    /** linear | branching */
    val outlineKind: String = KIND_LINEAR,
    val awaitingChoice: Boolean = false,
    /** Selected OPTION ids in order; never shortened except full regenerate. */
    val activePath: List<String> = emptyList(),
    /** Story node the writing model currently advances from (chapter/section/option). */
    val currentNodeId: String? = null,
    val rootId: String = ROOT_ID,
    val nodes: Map<String, OutlineGraphNode> = mapOf(
        ROOT_ID to OutlineGraphNode(id = ROOT_ID, type = OutlineNodeType.ROOT, title = "root"),
    ),
) {
    fun node(id: String): OutlineGraphNode? = nodes[id]

    fun rootChildren(): List<OutlineGraphNode> =
        nodes[rootId]?.children?.mapNotNull { nodes[it] }.orEmpty()

    fun childrenOf(id: String): List<OutlineGraphNode> =
        nodes[id]?.children?.mapNotNull { nodes[it] }.orEmpty()

    fun toDebugTree(): String = buildString {
        fun walk(nodeId: String, depth: Int) {
            val n = nodes[nodeId] ?: return
            if (n.type != OutlineNodeType.ROOT) {
                val mark = when {
                    n.type == OutlineNodeType.OPTION && n.selected == true -> " ✓"
                    else -> ""
                }
                append("${"  ".repeat(depth)}- [${n.type.name}] ${n.id} ${n.title}$mark\n")
                n.bullets.filter { it.isNotBlank() }.forEach { b ->
                    append("${"  ".repeat(depth + 1)}• $b\n")
                }
            }
            val nextDepth = if (n.type == OutlineNodeType.ROOT) 0 else depth + 1
            n.children.forEach { walk(it, nextDepth) }
        }
        walk(rootId, 0)
    }.trimEnd()

    companion object {
        const val FORMAT_VERSION = 2
        const val ROOT_ID = "root"
        const val KIND_LINEAR = "linear"
        const val KIND_BRANCHING = "branching"

        fun empty(kind: String = KIND_LINEAR): OutlineGraph = OutlineGraph(outlineKind = kind)

        fun emptyTemplate(kind: String = KIND_LINEAR): OutlineGraph {
            val volId = "vol_1"
            val chId = "ch_1"
            return OutlineGraph(
                outlineKind = kind,
                nodes = mapOf(
                    ROOT_ID to OutlineGraphNode(
                        id = ROOT_ID,
                        type = OutlineNodeType.ROOT,
                        title = "root",
                        children = listOf(volId),
                    ),
                    volId to OutlineGraphNode(
                        id = volId,
                        type = OutlineNodeType.VOLUME,
                        title = "第一卷",
                        children = listOf(chId),
                    ),
                    chId to OutlineGraphNode(
                        id = chId,
                        type = OutlineNodeType.CHAPTER,
                        title = "第一章",
                        bullets = listOf(""),
                    ),
                ),
            )
        }
    }
}

sealed interface OutlineGraphOp {
    data class AddNode(
        val parentId: String,
        val node: OutlineGraphNode,
        val index: Int? = null,
    ) : OutlineGraphOp

    data class UpdateNode(
        val id: String,
        val title: String? = null,
        val bullets: List<String>? = null,
        val selected: Boolean? = null,
    ) : OutlineGraphOp

    data class DeleteNode(val id: String) : OutlineGraphOp

    data class MoveNode(
        val id: String,
        val newParentId: String,
        val index: Int? = null,
    ) : OutlineGraphOp

    data class SelectOption(val optionId: String) : OutlineGraphOp

    /** Move the story "current node" pointer (regress/advance fixes). */
    data class SetCurrentNode(val nodeId: String?) : OutlineGraphOp

    data class SetAnchors(
        val premise: String? = null,
        val currentProgress: String? = null,
        val nextGoal: String? = null,
        val inProgress: Boolean? = null,
        val outlineKind: String? = null,
        val awaitingChoice: Boolean? = null,
    ) : OutlineGraphOp
}

sealed interface OutlineGraphResult {
    data class Ok(val graph: OutlineGraph) : OutlineGraphResult
    data class Err(val message: String) : OutlineGraphResult
}
