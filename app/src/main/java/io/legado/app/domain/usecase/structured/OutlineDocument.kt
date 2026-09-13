package io.legado.app.domain.usecase.structured

data class OutlineDocument(
    val premise: String = "",
    val currentProgress: String = "",
    val nextGoal: String = "",
    val inProgress: Boolean = false,
    val hierarchyDepth: Int = 2,
    val volumes: List<OutlineVolume> = emptyList(),
    /** linear | branching */
    val outlineKind: String = KIND_LINEAR,
    val awaitingChoice: Boolean = false,
    val activePath: String = "",
) {
    val normalizedDepth: Int get() = if (hierarchyDepth == 3) 3 else 2

    companion object {
        const val KIND_LINEAR = "linear"
        const val KIND_BRANCHING = "branching"
    }
}

data class OutlineVolume(
    val title: String,
    val children: List<OutlineNode> = emptyList(),
)

/**
 * @param sections Used when [hierarchyDepth] is 3: each child is a 篇, sections are 节.
 * @param branchOptions When non-empty, this node is a roleplay branch choice point.
 */
data class OutlineNode(
    val title: String,
    val bullets: List<String> = emptyList(),
    val sections: List<OutlineNode> = emptyList(),
    val branchOptions: List<OutlineBranchOption> = emptyList(),
) {
    val isBranch: Boolean get() = branchOptions.isNotEmpty() || OutlineBranchParser.isBranchHeading(title)
}

data class OutlineBranchOption(
    val id: String,
    val label: String,
    val selected: Boolean = false,
)
