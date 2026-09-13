package io.legado.app.ui.ai.outline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.usecase.structured.graph.OutlineGraph
import io.legado.app.domain.usecase.structured.graph.OutlineGraphDocumentBridge
import io.legado.app.domain.usecase.structured.graph.OutlineGraphEngine
import io.legado.app.domain.usecase.structured.graph.OutlineGraphNode
import io.legado.app.domain.usecase.structured.graph.OutlineGraphOp
import io.legado.app.domain.usecase.structured.graph.OutlineGraphResult
import io.legado.app.domain.usecase.structured.graph.OutlineNodeType

/**
 * Nested outline tree editor backed by [OutlineGraph].
 * Non-active branch subtrees are view-only when [enforceWritableDomain] is true.
 */
@Composable
fun OutlineGraphTreeEditor(
    graph: OutlineGraph,
    onGraphChange: (OutlineGraph) -> Unit,
    readOnly: Boolean,
    allowBranches: Boolean = false,
    enforceWritableDomain: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val writable = if (enforceWritableDomain) {
        OutlineGraphEngine.writableIds(graph)
    } else {
        graph.nodes.keys
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.ai_outline_structure),
                style = MaterialTheme.typography.titleSmall,
            )
            if (!readOnly) {
                TextButton(
                    onClick = {
                        val id = OutlineGraphDocumentBridge.newId("vol")
                        applyOp(
                            graph,
                            OutlineGraphOp.AddNode(
                                parentId = graph.rootId,
                                node = OutlineGraphNode(
                                    id = id,
                                    type = OutlineNodeType.VOLUME,
                                    title = "新卷",
                                ),
                            ),
                            onGraphChange,
                        )
                    },
                ) {
                    Text(stringResource(R.string.ai_outline_add_volume_short))
                }
            }
        }
        graph.rootChildren().forEach { child ->
            GraphNodeEditor(
                graph = graph,
                node = child,
                depth = 0,
                writable = writable,
                readOnly = readOnly,
                allowBranches = allowBranches,
                onGraphChange = onGraphChange,
            )
        }
    }
}

@Composable
private fun GraphNodeEditor(
    graph: OutlineGraph,
    node: OutlineGraphNode,
    depth: Int,
    writable: Set<String>,
    readOnly: Boolean,
    allowBranches: Boolean,
    onGraphChange: (OutlineGraph) -> Unit,
) {
    val canEdit = !readOnly && node.id in writable
    val dimmed = node.id !in writable && node.type != OutlineNodeType.ROOT
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 12).dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val prefix = when (node.type) {
                OutlineNodeType.OPTION -> if (node.selected == true) "✓ " else "○ "
                OutlineNodeType.BRANCH -> "⑂ "
                else -> ""
            }
            OutlinedTextField(
                value = "$prefix${node.title}",
                onValueChange = { raw ->
                    if (!canEdit) return@OutlinedTextField
                    val title = raw.removePrefix("✓ ").removePrefix("○ ").removePrefix("⑂ ").trim()
                    applyOp(
                        graph,
                        OutlineGraphOp.UpdateNode(id = node.id, title = title),
                        onGraphChange,
                    )
                },
                modifier = Modifier.weight(1f),
                readOnly = !canEdit,
                enabled = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = if (dimmed) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ),
                label = {
                    Text("${node.type.name} · ${node.id}")
                },
                singleLine = true,
            )
            if (canEdit && node.id != graph.rootId && node.id !in graph.activePath) {
                IconButton(
                    onClick = {
                        applyOp(graph, OutlineGraphOp.DeleteNode(node.id), onGraphChange)
                    },
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
            }
        }
        node.bullets.forEachIndexed { index, bullet ->
            OutlinedTextField(
                value = bullet,
                onValueChange = { v ->
                    if (!canEdit) return@OutlinedTextField
                    val bullets = node.bullets.toMutableList()
                    bullets[index] = v
                    applyOp(
                        graph,
                        OutlineGraphOp.UpdateNode(id = node.id, bullets = bullets),
                        onGraphChange,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                readOnly = !canEdit,
                singleLine = false,
                label = { Text(stringResource(R.string.ai_outline_bullet_hint)) },
            )
        }
        if (canEdit && node.type != OutlineNodeType.BRANCH && node.type != OutlineNodeType.OPTION) {
            TextButton(
                onClick = {
                    val bullets = node.bullets + ""
                    applyOp(
                        graph,
                        OutlineGraphOp.UpdateNode(id = node.id, bullets = bullets),
                        onGraphChange,
                    )
                },
            ) {
                Text(stringResource(R.string.ai_outline_add_bullet_short))
            }
        }
        if (canEdit) {
            when (node.type) {
                OutlineNodeType.VOLUME -> {
                    TextButton(
                        onClick = {
                            val id = OutlineGraphDocumentBridge.newId("ch")
                            applyOp(
                                graph,
                                OutlineGraphOp.AddNode(
                                    parentId = node.id,
                                    node = OutlineGraphNode(
                                        id = id,
                                        type = OutlineNodeType.CHAPTER,
                                        title = "新章",
                                    ),
                                ),
                                onGraphChange,
                            )
                        },
                    ) { Text(stringResource(R.string.ai_outline_add_chapter)) }
                    if (allowBranches) {
                        TextButton(
                            onClick = {
                                val branchId = OutlineGraphDocumentBridge.newId("branch")
                                val optA = OutlineGraphDocumentBridge.newId("opt")
                                val optB = OutlineGraphDocumentBridge.newId("opt")
                                var g = graph
                                listOf(
                                    OutlineGraphOp.AddNode(
                                        parentId = node.id,
                                        node = OutlineGraphNode(
                                            id = branchId,
                                            type = OutlineNodeType.BRANCH,
                                            title = "分支：路口",
                                        ),
                                    ),
                                    OutlineGraphOp.AddNode(
                                        parentId = branchId,
                                        node = OutlineGraphNode(
                                            id = optA,
                                            type = OutlineNodeType.OPTION,
                                            title = "选项 A",
                                        ),
                                    ),
                                    OutlineGraphOp.AddNode(
                                        parentId = branchId,
                                        node = OutlineGraphNode(
                                            id = optB,
                                            type = OutlineNodeType.OPTION,
                                            title = "选项 B",
                                        ),
                                    ),
                                    OutlineGraphOp.SetAnchors(
                                        outlineKind = OutlineGraph.KIND_BRANCHING,
                                        awaitingChoice = true,
                                    ),
                                ).forEach { op ->
                                    when (
                                        val r = OutlineGraphEngine.apply(
                                            g,
                                            op,
                                            allowSelect = false,
                                            blockWhileAwaiting = false,
                                            enforceWritableDomain = false,
                                        )
                                    ) {
                                        is OutlineGraphResult.Ok -> g = r.graph
                                        is OutlineGraphResult.Err -> return@TextButton
                                    }
                                }
                                onGraphChange(OutlineGraphEngine.normalizeAwaiting(g))
                            },
                        ) { Text(stringResource(R.string.ai_outline_add_branch)) }
                    }
                }
                OutlineNodeType.CHAPTER, OutlineNodeType.SECTION -> {
                    if (allowBranches) {
                        TextButton(
                            onClick = {
                                val branchId = OutlineGraphDocumentBridge.newId("branch")
                                val optA = OutlineGraphDocumentBridge.newId("opt")
                                val optB = OutlineGraphDocumentBridge.newId("opt")
                                var g = graph
                                listOf(
                                    OutlineGraphOp.AddNode(
                                        parentId = node.id,
                                        node = OutlineGraphNode(
                                            id = branchId,
                                            type = OutlineNodeType.BRANCH,
                                            title = "分支：路口",
                                        ),
                                    ),
                                    OutlineGraphOp.AddNode(
                                        parentId = branchId,
                                        node = OutlineGraphNode(
                                            id = optA,
                                            type = OutlineNodeType.OPTION,
                                            title = "选项 A",
                                        ),
                                    ),
                                    OutlineGraphOp.AddNode(
                                        parentId = branchId,
                                        node = OutlineGraphNode(
                                            id = optB,
                                            type = OutlineNodeType.OPTION,
                                            title = "选项 B",
                                        ),
                                    ),
                                    OutlineGraphOp.SetAnchors(
                                        outlineKind = OutlineGraph.KIND_BRANCHING,
                                        awaitingChoice = true,
                                    ),
                                ).forEach { op ->
                                    when (
                                        val r = OutlineGraphEngine.apply(
                                            g,
                                            op,
                                            allowSelect = false,
                                            blockWhileAwaiting = false,
                                            enforceWritableDomain = false,
                                        )
                                    ) {
                                        is OutlineGraphResult.Ok -> g = r.graph
                                        is OutlineGraphResult.Err -> return@TextButton
                                    }
                                }
                                onGraphChange(OutlineGraphEngine.normalizeAwaiting(g))
                            },
                        ) { Text(stringResource(R.string.ai_outline_add_branch)) }
                    }
                }
                OutlineNodeType.BRANCH -> {
                    if (canEdit) {
                        TextButton(
                            onClick = {
                                val id = OutlineGraphDocumentBridge.newId("opt")
                                applyOp(
                                    graph,
                                    OutlineGraphOp.AddNode(
                                        parentId = node.id,
                                        node = OutlineGraphNode(
                                            id = id,
                                            type = OutlineNodeType.OPTION,
                                            title = "新选项",
                                        ),
                                    ),
                                    onGraphChange,
                                )
                            },
                        ) { Text(stringResource(R.string.ai_outline_add_branch_option)) }
                    }
                }
                OutlineNodeType.OPTION -> {
                    if (canEdit) {
                        TextButton(
                            onClick = {
                                val id = OutlineGraphDocumentBridge.newId("ch")
                                applyOp(
                                    graph,
                                    OutlineGraphOp.AddNode(
                                        parentId = node.id,
                                        node = OutlineGraphNode(
                                            id = id,
                                            type = OutlineNodeType.CHAPTER,
                                            title = "新章",
                                        ),
                                    ),
                                    onGraphChange,
                                )
                            },
                        ) { Text(stringResource(R.string.ai_outline_add_chapter)) }
                    }
                }
                else -> Unit
            }
        }
        graph.childrenOf(node.id).forEach { child ->
            GraphNodeEditor(
                graph = graph,
                node = child,
                depth = depth + 1,
                writable = writable,
                readOnly = readOnly,
                allowBranches = allowBranches,
                onGraphChange = onGraphChange,
            )
        }
    }
}

private fun applyOp(
    graph: OutlineGraph,
    op: OutlineGraphOp,
    onGraphChange: (OutlineGraph) -> Unit,
) {
    // Sheet edits must work even while awaiting_choice (AI maintain still uses the gate).
    when (
        val r = OutlineGraphEngine.apply(
            graph,
            op,
            allowSelect = false,
            blockWhileAwaiting = false,
            enforceWritableDomain = false,
        )
    ) {
        is OutlineGraphResult.Ok -> onGraphChange(r.graph)
        is OutlineGraphResult.Err -> Unit
    }
}
