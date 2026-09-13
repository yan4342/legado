package io.legado.app.domain.usecase.structured.graph

/**
 * Pure graph mutations with writable-domain and awaiting gates.
 */
object OutlineGraphEngine {

    /**
     * @param blockWhileAwaiting When true (AI/maintain), reject plot-advancing mutations
     * while a branch choice is pending. Sheet UI should pass false so users can still edit.
     * @param enforceWritableDomain When true, only the active timeline/path is writable
     * (AI/maintain). Sheet manual editing passes false so any node can be edited.
     */
    fun apply(
        graph: OutlineGraph,
        op: OutlineGraphOp,
        allowSelect: Boolean = true,
        blockWhileAwaiting: Boolean = true,
        enforceWritableDomain: Boolean = true,
    ): OutlineGraphResult {
        return when (op) {
            is OutlineGraphOp.AddNode -> addNode(graph, op, blockWhileAwaiting, enforceWritableDomain)
            is OutlineGraphOp.UpdateNode -> updateNode(graph, op, blockWhileAwaiting, enforceWritableDomain)
            is OutlineGraphOp.DeleteNode -> deleteNode(graph, op, blockWhileAwaiting, enforceWritableDomain)
            is OutlineGraphOp.MoveNode -> moveNode(graph, op, blockWhileAwaiting, enforceWritableDomain)
            is OutlineGraphOp.SelectOption -> {
                if (!allowSelect) return OutlineGraphResult.Err("select_not_allowed")
                selectOption(graph, op.optionId)
            }
            is OutlineGraphOp.SetCurrentNode -> setCurrentNode(graph, op.nodeId)
            is OutlineGraphOp.SetAnchors -> setAnchors(graph, op)
        }
    }

    fun applyAll(
        graph: OutlineGraph,
        ops: List<OutlineGraphOp>,
        allowSelect: Boolean = false,
        blockWhileAwaiting: Boolean = true,
        enforceWritableDomain: Boolean = true,
    ): OutlineGraphResult {
        var current = graph
        var lastAddedId: String? = null
        for (op in ops) {
            val resolved = resolveParentRefs(op, lastAddedId, current)
            val beforeIds = current.nodes.keys
            when (
                val r = apply(
                    current,
                    resolved,
                    allowSelect = allowSelect,
                    blockWhileAwaiting = blockWhileAwaiting,
                    enforceWritableDomain = enforceWritableDomain,
                )
            ) {
                is OutlineGraphResult.Ok -> {
                    current = r.graph
                    if (resolved is OutlineGraphOp.AddNode) {
                        lastAddedId = current.nodes.keys.firstOrNull { it !in beforeIds } ?: lastAddedId
                    }
                }
                is OutlineGraphResult.Err -> return r
            }
        }
        return OutlineGraphResult.Ok(normalizeDerivedState(current))
    }

    /**
     * Resolve parent="@last" to the previous add_node id.
     * For OPTION, if @last is another OPTION, attach to that option's BRANCH parent.
     */
    private fun resolveParentRefs(
        op: OutlineGraphOp,
        lastAddedId: String?,
        graph: OutlineGraph,
    ): OutlineGraphOp {
        if (op !is OutlineGraphOp.AddNode) return op
        val parent = op.parentId.trim()
        if (!parent.equals("@last", ignoreCase = true) && parent != "\$last") return op
        var id = lastAddedId ?: return op
        if (op.node.type == OutlineNodeType.OPTION) {
            val last = graph.nodes[id]
            if (last?.type == OutlineNodeType.OPTION) {
                id = findParent(graph, id) ?: id
            }
        }
        return op.copy(parentId = id)
    }

    /**
     * Program-owned derived fields: outline_kind from BRANCH presence,
     * awaiting_choice from pending unselected options on the active spine.
     */
    fun normalizeDerivedState(graph: OutlineGraph): OutlineGraph {
        val hasBranch = graph.nodes.values.any { it.type == OutlineNodeType.BRANCH }
        val kind = when {
            hasBranch -> OutlineGraph.KIND_BRANCHING
            graph.outlineKind == OutlineGraph.KIND_BRANCHING -> OutlineGraph.KIND_LINEAR
            else -> graph.outlineKind.ifBlank { OutlineGraph.KIND_LINEAR }
        }
        return normalizeAwaiting(graph.copy(outlineKind = kind))
    }

    /** Nodes the AI/UI may mutate (active timeline + path nodes). */
    fun writableIds(graph: OutlineGraph): Set<String> {
        val result = linkedSetOf(graph.rootId)
        // Always include root's linear spine until first branch divergence
        fun collectWritableFrom(nodeId: String, onActiveSpine: Boolean) {
            val node = graph.nodes[nodeId] ?: return
            if (onActiveSpine) result.add(nodeId)
            when (node.type) {
                OutlineNodeType.BRANCH -> {
                    if (!onActiveSpine) return
                    result.add(nodeId)
                    val selected = node.children.mapNotNull { graph.nodes[it] }
                        .firstOrNull { it.selected == true }
                    if (selected != null) {
                        result.add(selected.id)
                        selected.children.forEach { collectWritableFrom(it, true) }
                    } else if (graph.awaitingChoice) {
                        // Labels of unselected options are writable while awaiting
                        node.children.forEach { optId ->
                            result.add(optId)
                        }
                    }
                }
                OutlineNodeType.OPTION -> {
                    if (onActiveSpine) {
                        node.children.forEach { collectWritableFrom(it, true) }
                    }
                }
                else -> {
                    if (onActiveSpine) {
                        node.children.forEach { collectWritableFrom(it, true) }
                    }
                }
            }
        }
        graph.rootChildren().forEach { collectWritableFrom(it.id, true) }
        // Also mark all ancestors of activePath options
        graph.activePath.forEach { optId ->
            result.add(optId)
            ancestors(graph, optId).forEach { result.add(it) }
            graph.nodes[optId]?.children?.forEach { collectWritableFrom(it, true) }
        }
        return result
    }

    /**
     * Chosen timeline for UI highlight: writable spine minus pending unselected options.
     * Includes volumes/chapters/branches/selected options along the active path.
     */
    fun activeSpineIds(graph: OutlineGraph): Set<String> {
        val pendingUnselected = findPendingBranch(graph)
            ?.second
            ?.asSequence()
            ?.filter { it.selected != true }
            ?.map { it.id }
            ?.toSet()
            .orEmpty()
        return writableIds(graph)
            .asSequence()
            .filter { it != graph.rootId && it !in pendingUnselected }
            .toSet()
    }

    fun findPendingBranch(graph: OutlineGraph): Pair<OutlineGraphNode, List<OutlineGraphNode>>? {
        if (!graph.awaitingChoice && graph.activePath.isNotEmpty()) {
            // Still search frontier for unset branch
        }
        // Prefer BRANCH on active spine with no selected option
        fun search(nodeId: String, onSpine: Boolean): Pair<OutlineGraphNode, List<OutlineGraphNode>>? {
            val node = graph.nodes[nodeId] ?: return null
            if (node.type == OutlineNodeType.BRANCH && onSpine) {
                val options = node.children.mapNotNull { graph.nodes[it] }
                    .filter { it.type == OutlineNodeType.OPTION }
                if (options.isNotEmpty() && options.none { it.selected == true }) {
                    return node to options
                }
                val selected = options.firstOrNull { it.selected == true }
                if (selected != null) {
                    selected.children.forEach { childId ->
                        search(childId, true)?.let { return it }
                    }
                }
                return null
            }
            if (!onSpine) return null
            if (node.type == OutlineNodeType.OPTION) {
                if (node.selected != true && graph.activePath.isNotEmpty() &&
                    node.id !in graph.activePath
                ) {
                    return null
                }
                node.children.forEach { search(it, true)?.let { r -> return r } }
                return null
            }
            node.children.forEach { childId ->
                val child = graph.nodes[childId] ?: return@forEach
                when (child.type) {
                    OutlineNodeType.BRANCH -> search(childId, true)?.let { return it }
                    OutlineNodeType.OPTION -> Unit
                    else -> search(childId, true)?.let { return it }
                }
            }
            return null
        }
        graph.rootChildren().forEach { search(it.id, true)?.let { return it } }
        return null
    }

    fun normalizeAwaiting(graph: OutlineGraph): OutlineGraph {
        val pending = findPendingBranch(graph)
        return when {
            pending == null -> graph.copy(awaitingChoice = false)
            // Keep an explicit gate; do NOT invent awaiting merely because a future
            // BRANCH exists in a freshly generated skeleton (that blocked maintain).
            graph.awaitingChoice -> graph.copy(
                outlineKind = OutlineGraph.KIND_BRANCHING,
                awaitingChoice = true,
                currentProgress = graph.currentProgress.ifBlank { "待选择：${pending.first.title}" },
                nextGoal = graph.nextGoal.ifBlank { "请选择分支" },
                inProgress = true,
            )
            else -> graph.copy(awaitingChoice = false)
        }
    }

    private fun addNode(
        graph: OutlineGraph,
        op: OutlineGraphOp.AddNode,
        blockWhileAwaiting: Boolean,
        enforceWritableDomain: Boolean,
    ): OutlineGraphResult {
        val parent = graph.nodes[op.parentId]
            ?: return OutlineGraphResult.Err("parent_not_found")
        if (parent.type == OutlineNodeType.BRANCH && op.node.type != OutlineNodeType.OPTION) {
            return OutlineGraphResult.Err("branch_children_must_be_options")
        }
        if (op.node.type == OutlineNodeType.OPTION && parent.type != OutlineNodeType.BRANCH) {
            return OutlineGraphResult.Err("option_requires_branch_parent")
        }
        if (enforceWritableDomain) {
            val writable = writableIds(graph)
            if (op.parentId !in writable && op.parentId != graph.rootId) {
                return OutlineGraphResult.Err("parent_not_writable")
            }
        }
        if (blockWhileAwaiting && graph.awaitingChoice && op.node.type != OutlineNodeType.OPTION) {
            // AI: allow only option labels while awaiting — not plot advance via ADD
            val pending = findPendingBranch(graph)
            val allowedParent = pending?.first?.id == op.parentId && op.node.type == OutlineNodeType.OPTION
            if (!allowedParent) {
                return OutlineGraphResult.Err("blocked_while_awaiting")
            }
        }
        val node = ensureUniqueId(graph, op.node)
        val children = parent.children.toMutableList()
        val idx = op.index?.coerceIn(0, children.size) ?: children.size
        children.add(idx, node.id)
        val nodes = graph.nodes.toMutableMap()
        nodes[node.id] = node
        nodes[parent.id] = parent.copy(children = children)
        var next = graph.copy(nodes = nodes)
        if (node.type == OutlineNodeType.BRANCH || node.type == OutlineNodeType.OPTION) {
            next = next.copy(outlineKind = OutlineGraph.KIND_BRANCHING)
        }
        // Adding a BRANCH opens a live fork (maintain / sheet “添加分支”).
        // Adding OPTION only keeps an already-open gate — never invent awaiting for
        // pre-planted pending branches further on the spine.
        when (node.type) {
            OutlineNodeType.BRANCH -> {
                next = next.copy(
                    outlineKind = OutlineGraph.KIND_BRANCHING,
                    awaitingChoice = true,
                    currentProgress = next.currentProgress.ifBlank { "待选择：${node.title}" },
                    nextGoal = next.nextGoal.ifBlank { "请选择分支" },
                    inProgress = true,
                )
            }
            OutlineNodeType.OPTION -> {
                next = next.copy(outlineKind = OutlineGraph.KIND_BRANCHING)
                if (graph.awaitingChoice) {
                    val pending = findPendingBranch(next)
                    next = if (pending != null) {
                        next.copy(
                            awaitingChoice = true,
                            currentProgress = next.currentProgress.ifBlank {
                                "待选择：${pending.first.title}"
                            },
                            nextGoal = next.nextGoal.ifBlank { "请选择分支" },
                            inProgress = true,
                        )
                    } else {
                        next.copy(awaitingChoice = false)
                    }
                }
            }
            else -> Unit
        }
        return OutlineGraphResult.Ok(next)
    }

    /** Blank or colliding ids are replaced; AI need not invent stable ids. */
    fun ensureUniqueId(graph: OutlineGraph, node: OutlineGraphNode): OutlineGraphNode {
        val preferred = node.id.trim()
        if (preferred.isNotBlank() && !graph.nodes.containsKey(preferred)) {
            return node.copy(id = preferred)
        }
        var id = OutlineGraphDocumentBridge.newId(idPrefix(node.type))
        var n = 0
        while (graph.nodes.containsKey(id) || id == preferred) {
            n++
            id = OutlineGraphDocumentBridge.newId(idPrefix(node.type)) + "_$n"
        }
        return node.copy(id = id)
    }

    private fun idPrefix(type: OutlineNodeType): String = when (type) {
        OutlineNodeType.VOLUME -> "vol"
        OutlineNodeType.CHAPTER -> "ch"
        OutlineNodeType.SECTION -> "sec"
        OutlineNodeType.BRANCH -> "branch"
        OutlineNodeType.OPTION -> "opt"
        OutlineNodeType.ROOT -> "root"
    }

    private fun updateNode(
        graph: OutlineGraph,
        op: OutlineGraphOp.UpdateNode,
        blockWhileAwaiting: Boolean,
        enforceWritableDomain: Boolean,
    ): OutlineGraphResult {
        val node = graph.nodes[op.id] ?: return OutlineGraphResult.Err("node_not_found")
        if (enforceWritableDomain && op.id !in writableIds(graph)) {
            return OutlineGraphResult.Err("node_not_writable")
        }
        if (blockWhileAwaiting && graph.awaitingChoice) {
            val pending = findPendingBranch(graph)
            val isPendingOptionLabel =
                node.type == OutlineNodeType.OPTION &&
                    pending?.second?.any { it.id == op.id } == true &&
                    op.selected == null
            val isPendingBranchTitle =
                node.type == OutlineNodeType.BRANCH && op.id == pending?.first?.id
            if (!isPendingOptionLabel && !isPendingBranchTitle) {
                return OutlineGraphResult.Err("blocked_while_awaiting")
            }
        }
        // Never clear selected via update (no re-select)
        if (node.type == OutlineNodeType.OPTION && node.selected == true && op.selected == false) {
            return OutlineGraphResult.Err("cannot_unselect")
        }
        val updated = node.copy(
            title = op.title ?: node.title,
            bullets = op.bullets ?: node.bullets,
            selected = op.selected ?: node.selected,
        )
        return OutlineGraphResult.Ok(graph.copy(nodes = graph.nodes + (op.id to updated)))
    }

    private fun deleteNode(
        graph: OutlineGraph,
        op: OutlineGraphOp.DeleteNode,
        blockWhileAwaiting: Boolean,
        enforceWritableDomain: Boolean,
    ): OutlineGraphResult {
        if (op.id == graph.rootId) return OutlineGraphResult.Err("cannot_delete_root")
        if (enforceWritableDomain && op.id in graph.activePath) {
            return OutlineGraphResult.Err("cannot_delete_active_path")
        }
        if (enforceWritableDomain && op.id !in writableIds(graph)) {
            return OutlineGraphResult.Err("node_not_writable")
        }
        if (blockWhileAwaiting && graph.awaitingChoice) {
            return OutlineGraphResult.Err("blocked_while_awaiting")
        }
        val parentId = findParent(graph, op.id) ?: return OutlineGraphResult.Err("orphan")
        val toRemove = mutableSetOf<String>()
        fun collect(id: String) {
            toRemove.add(id)
            graph.nodes[id]?.children?.forEach { collect(it) }
        }
        collect(op.id)
        val nodes = graph.nodes.toMutableMap()
        toRemove.forEach { nodes.remove(it) }
        val parent = nodes[parentId]!!
        nodes[parentId] = parent.copy(children = parent.children.filter { it !in toRemove })
        val currentNodeId = graph.currentNodeId
            ?.takeIf { it !in toRemove }
            ?: parentId
        return OutlineGraphResult.Ok(graph.copy(nodes = nodes, currentNodeId = currentNodeId))
    }

    private fun moveNode(
        graph: OutlineGraph,
        op: OutlineGraphOp.MoveNode,
        blockWhileAwaiting: Boolean,
        enforceWritableDomain: Boolean,
    ): OutlineGraphResult {
        if (op.id == graph.rootId) return OutlineGraphResult.Err("cannot_move_root")
        if (enforceWritableDomain && op.id in graph.activePath) {
            return OutlineGraphResult.Err("cannot_move_active_path")
        }
        if (enforceWritableDomain) {
            val writable = writableIds(graph)
            if (op.id !in writable || op.newParentId !in writable) {
                return OutlineGraphResult.Err("not_writable")
            }
        }
        if (blockWhileAwaiting && graph.awaitingChoice) {
            return OutlineGraphResult.Err("blocked_while_awaiting")
        }
        val node = graph.nodes[op.id] ?: return OutlineGraphResult.Err("node_not_found")
        val newParent = graph.nodes[op.newParentId] ?: return OutlineGraphResult.Err("parent_not_found")
        if (newParent.type == OutlineNodeType.BRANCH && node.type != OutlineNodeType.OPTION) {
            return OutlineGraphResult.Err("branch_children_must_be_options")
        }
        // Prevent cycles
        if (op.id == op.newParentId || op.newParentId in descendants(graph, op.id)) {
            return OutlineGraphResult.Err("cycle")
        }
        val oldParentId = findParent(graph, op.id) ?: return OutlineGraphResult.Err("orphan")
        val nodes = graph.nodes.toMutableMap()
        val oldParent = nodes[oldParentId]!!
        nodes[oldParentId] = oldParent.copy(children = oldParent.children.filter { it != op.id })
        val children = newParent.children.filter { it != op.id }.toMutableList()
        val idx = op.index?.coerceIn(0, children.size) ?: children.size
        children.add(idx, op.id)
        nodes[op.newParentId] = newParent.copy(children = children)
        return OutlineGraphResult.Ok(graph.copy(nodes = nodes))
    }

    fun selectOption(graph: OutlineGraph, optionId: String): OutlineGraphResult {
        if (!graph.awaitingChoice) {
            return OutlineGraphResult.Err("not_awaiting")
        }
        val option = graph.nodes[optionId]
            ?: return OutlineGraphResult.Err("option_not_found")
        if (option.type != OutlineNodeType.OPTION) {
            return OutlineGraphResult.Err("not_an_option")
        }
        if (option.selected == true) {
            return OutlineGraphResult.Err("already_selected")
        }
        val pending = findPendingBranch(graph)
            ?: return OutlineGraphResult.Err("no_pending_branch")
        val options = pending.second
        if (options.none { it.id == optionId }) {
            return OutlineGraphResult.Err("option_not_in_pending")
        }
        val nodes = graph.nodes.toMutableMap()
        options.forEach { opt ->
            nodes[opt.id] = opt.copy(selected = opt.id == optionId)
        }
        val newPath = graph.activePath + optionId
        // Always clear the gate after a pick. Deeper pre-planted BRANCHes stay on the spine
        // but must not pop the chooser until maintain / patch_outline opens awaiting again.
        return OutlineGraphResult.Ok(
            graph.copy(
                nodes = nodes,
                outlineKind = OutlineGraph.KIND_BRANCHING,
                awaitingChoice = false,
                activePath = newPath,
                currentProgress = option.title,
                nextGoal = "沿「${option.title}」推进",
                inProgress = true,
            ),
        )
    }

    /**
     * Move the story current-node pointer (graph UI "设为当前"). Registers the node title
     * as `current` so [WritingWorkspaceIndexBuilder.buildNextSceneGuide] advances from here.
     * Never touches activePath / awaitingChoice — only the pointer + narrative anchors.
     */
    private fun setCurrentNode(graph: OutlineGraph, nodeId: String?): OutlineGraphResult {
        if (nodeId.isNullOrBlank()) {
            return OutlineGraphResult.Ok(graph.copy(currentNodeId = null))
        }
        val node = graph.nodes[nodeId] ?: return OutlineGraphResult.Err("node_not_found")
        if (node.type == OutlineNodeType.ROOT) return OutlineGraphResult.Err("cannot_set_root")
        return OutlineGraphResult.Ok(
            graph.copy(
                currentNodeId = node.id,
                currentProgress = node.title,
                inProgress = true,
            ),
        )
    }

    /** Whether a [regressCurrent] step is available (current pointer exists and has a predecessor). */
    fun canRegressCurrent(graph: OutlineGraph): Boolean {
        val currentId = graph.currentNodeId ?: return false
        if (graph.nodes[currentId] == null) return false
        return narrativePredecessor(graph, currentId) != null
    }

    /**
     * Regress the current-node pointer one step back in narrative order (correct an
     * over-advanced node). Predecessor rules:
     * - previous sibling at the same level, descending into a container's last scene;
     * - first child of a volume → previous volume's last chapter, else the volume itself;
     * - chapter under an option → the option, then the option → its branch;
     * - nothing left → [OutlineGraphResult.Err].
     * Only the pointer + current/next anchors change; activePath / awaitingChoice / selections
     * are preserved so branch history is never lost.
     */
    fun regressCurrent(graph: OutlineGraph): OutlineGraphResult {
        val currentId = graph.currentNodeId ?: return OutlineGraphResult.Err("no_current_node")
        val current = graph.nodes[currentId]
            ?: return OutlineGraphResult.Err("current_node_missing")
        val prevId = narrativePredecessor(graph, currentId)
            ?: return OutlineGraphResult.Err("already_at_start")
        val prev = graph.nodes[prevId] ?: return OutlineGraphResult.Err("predecessor_missing")
        return OutlineGraphResult.Ok(
            graph.copy(
                currentNodeId = prev.id,
                currentProgress = prev.title,
                nextGoal = current.title,
                inProgress = true,
            ),
        )
    }

    /** Node one step earlier in narrative order, or null at the start. */
    private fun narrativePredecessor(graph: OutlineGraph, nodeId: String): String? {
        val parentId = findParent(graph, nodeId) ?: return null
        val parent = graph.nodes[parentId] ?: return null
        val siblings = parent.children.mapNotNull { graph.nodes[it] }
        val idx = siblings.indexOfFirst { it.id == nodeId }
        if (idx > 0) {
            val prev = siblings[idx - 1]
            return when (prev.type) {
                OutlineNodeType.VOLUME, OutlineNodeType.BRANCH, OutlineNodeType.OPTION ->
                    lastSceneId(graph, prev.id) ?: prev.id
                else -> prev.id
            }
        }
        if (parent.type == OutlineNodeType.ROOT) return null
        // First child: climb to the container's predecessor.
        if (parent.type == OutlineNodeType.VOLUME) {
            val grandParentId = findParent(graph, parentId) ?: return null
            val grandParent = graph.nodes[grandParentId] ?: return null
            val volIdx = grandParent.children.indexOf(parentId)
            if (volIdx > 0) {
                val prevVol = graph.nodes[grandParent.children[volIdx - 1]] ?: return null
                return lastSceneId(graph, prevVol.id) ?: prevVol.id
            }
            return parentId
        }
        return parentId
    }

    /** Deepest last child in narrative order (container → its last scene). */
    private fun lastSceneId(graph: OutlineGraph, nodeId: String): String? {
        var cursor = graph.nodes[nodeId] ?: return null
        while (cursor.children.isNotEmpty()) {
            val lastChildId = cursor.children.last()
            cursor = graph.nodes[lastChildId] ?: return cursor.id
        }
        return cursor.id
    }

    private fun setAnchors(graph: OutlineGraph, op: OutlineGraphOp.SetAnchors): OutlineGraphResult {
        // Narrative anchors; awaiting_choice may be set explicitly (e.g. open a pre-planted fork).
        // outline_kind still follows BRANCH presence via [normalizeDerivedState] in applyAll.
        return OutlineGraphResult.Ok(
            graph.copy(
                premise = op.premise ?: graph.premise,
                currentProgress = op.currentProgress ?: graph.currentProgress,
                nextGoal = op.nextGoal ?: graph.nextGoal,
                inProgress = op.inProgress ?: graph.inProgress,
                awaitingChoice = op.awaitingChoice ?: graph.awaitingChoice,
            ),
        )
    }

    fun findParent(graph: OutlineGraph, childId: String): String? {
        graph.nodes.forEach { (id, n) ->
            if (childId in n.children) return id
        }
        return null
    }

    private fun ancestors(graph: OutlineGraph, nodeId: String): List<String> {
        val out = mutableListOf<String>()
        var cur = findParent(graph, nodeId)
        while (cur != null) {
            out.add(cur)
            cur = findParent(graph, cur)
        }
        return out
    }

    private fun descendants(graph: OutlineGraph, nodeId: String): Set<String> {
        val out = mutableSetOf<String>()
        fun walk(id: String) {
            graph.nodes[id]?.children?.forEach {
                out.add(it)
                walk(it)
            }
        }
        walk(nodeId)
        return out
    }

    fun isAllowedWhileAwaiting(ops: List<OutlineGraphOp>): Boolean {
        // Only UpdateNode on pending options (labels) or SetAnchors for narrative progress text
        return ops.all { op ->
            when (op) {
                is OutlineGraphOp.UpdateNode -> true // engine enforces details
                is OutlineGraphOp.SetAnchors -> true // kind/awaiting ignored; narrative only
                is OutlineGraphOp.SetCurrentNode -> true // pointer only, never plot-advancing
                is OutlineGraphOp.AddNode -> op.node.type == OutlineNodeType.OPTION
                is OutlineGraphOp.SelectOption -> false
                is OutlineGraphOp.DeleteNode, is OutlineGraphOp.MoveNode -> false
            }
        }
    }
}
