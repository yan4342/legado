package io.legado.app.ui.ai.outline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.legado.app.ui.common.compose.rememberSwipeToConfirmDismiss
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.utils.toastOnUi
import io.legado.app.domain.usecase.structured.OutlineMarkdownCodec
import io.legado.app.domain.usecase.structured.graph.OutlineGraph
import io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec
import io.legado.app.domain.usecase.structured.graph.OutlineGraphDocumentBridge
import io.legado.app.domain.usecase.structured.graph.OutlineGraphEngine
import io.legado.app.domain.usecase.structured.graph.OutlineGraphOp
import io.legado.app.domain.usecase.structured.graph.OutlineGraphResult
import io.legado.app.ui.ai.chat.ChatOutlineBranchPanel
import io.legado.app.ui.ai.chat.FieldChangeUi
import io.legado.app.ui.ai.chat.GitStyleOutlineLinePanel
import io.legado.app.ui.ai.chat.OutlineBranchChoiceUi
import io.legado.app.ui.ai.chat.SheetPreviewBanner
import io.legado.app.ui.ai.chat.SourceBookBindingRow
import io.legado.app.ui.ai.chat.canInlineGitHighlightOutline
import io.legado.app.ui.ai.chat.shouldShowSheetPreviewBanner
import io.legado.app.ui.common.compose.AiGenerationProgressPanel
import io.legado.app.ui.common.compose.legadoSheetInsets

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OutlineSheet(
    outlineContent: String,
    outlineEnabled: Boolean,
    bookName: String = "",
    bookAuthor: String = "",
    isLoading: Boolean = false,
    generationPreview: String = "",
    generationReasoning: String = "",
    highlightSection: String? = null,
    readOnly: Boolean = false,
    readOnlyMessage: String? = null,
    previewChanges: List<FieldChangeUi> = emptyList(),
    writingSubMode: String = "author",
    branchChoice: OutlineBranchChoiceUi? = null,
    onDismiss: () -> Unit,
    onSave: (content: String, enabled: Boolean) -> Unit,
    onToggleEnabled: () -> Unit,
    onGenerate: (outlineKind: String) -> Unit = { _ -> },
    onSupplement: (content: String, outlineKind: String) -> Unit = { _, _ -> },
    onDelete: () -> Unit = {},
    onCleanupOrphans: () -> Unit = {},
    onExportJson: () -> Unit = {},
    onImportJson: () -> Unit = {},
    onImportFromConversation: () -> Unit = {},
    onExportToConversation: () -> Unit = {},
    onShowVersionHistory: () -> Unit = {},
    onShowBookPicker: () -> Unit = {},
    onClearBookSource: () -> Unit = {},
    onSelectBranch: (optionId: String) -> Unit = {},
) {
    val isRoleplay = writingSubMode == "roleplay"
    val decodedInitial = remember(outlineContent) {
        OutlineGraphCodec.decode(outlineContent)
    }
    var formatSupported by remember(outlineContent) {
        mutableStateOf(decodedInitial.isFormatV2 || outlineContent.isBlank())
    }
    var graph by remember(outlineContent) {
        mutableStateOf(
            decodedInitial.graph
                ?: OutlineGraph.empty(
                    if (isRoleplay) OutlineGraph.KIND_BRANCHING else OutlineGraph.KIND_LINEAR,
                ),
        )
    }
    var document by remember {
        mutableStateOf(OutlineGraphDocumentBridge.toDocumentAnchors(graph))
    }
    var enabled by remember { mutableStateOf(outlineEnabled) }
    var showTransferMenu by remember { mutableStateOf(false) }
    var showTemplateMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMarkdownBody by remember { mutableStateOf(false) }
    var showGraphCanvas by remember { mutableStateOf(false) }
    var markdownBody by remember(outlineContent) {
        mutableStateOf(
            if (formatSupported) {
                OutlineGraphCodec.encodeBody(graph)
            } else {
                OutlineMarkdownCodec.bodyFromStored(outlineContent)
            },
        )
    }
    var markdownBodyEditing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val (sheetState, onDismissRequest) = rememberSwipeToConfirmDismiss(
        onNeedConfirm = { context.toastOnUi(R.string.ai_sheet_swipe_again_to_close) },
        onDismiss = onDismiss,
    )
    val colorScheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()

    LaunchedEffect(outlineContent) {
        val decoded = OutlineGraphCodec.decode(outlineContent)
        formatSupported = decoded.isFormatV2 || outlineContent.isBlank()
        if (decoded.graph != null) {
            graph = decoded.graph
            document = OutlineGraphDocumentBridge.toDocumentAnchors(decoded.graph)
            if (!markdownBodyEditing) {
                markdownBody = OutlineGraphCodec.encodeBody(decoded.graph)
            }
        }
    }
    LaunchedEffect(graph) {
        document = OutlineGraphDocumentBridge.toDocumentAnchors(graph)
        if (!markdownBodyEditing) {
            markdownBody = OutlineGraphCodec.encodeBody(graph)
        }
    }
    LaunchedEffect(outlineEnabled) { enabled = outlineEnabled }
    val encodedPreview = remember(graph) { OutlineGraphCodec.encode(graph) }
    // 扩写的工作内容：用户正在 Markdown 区编辑的草稿，或当前图（含锚点）。
    val workingOutlineContent = remember(markdownBody, graph) {
        buildWorkingOutlineContent(graph, markdownBody)
    }
    val hasExpandableContent = graph.rootChildren().isNotEmpty() || markdownBody.isNotBlank()
    val useInlineGit = canInlineGitHighlightOutline(encodedPreview, highlightSection, previewChanges)
    val showBanner = shouldShowSheetPreviewBanner(previewChanges, useInlineGit)
    LaunchedEffect(highlightSection, encodedPreview) {
        val section = highlightSection?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val markers = listOf("## $section", "# $section", section)
        val idx = markers.firstNotNullOfOrNull { marker ->
            encodedPreview.indexOf(marker, ignoreCase = true).takeIf { it >= 0 }
        } ?: encodedPreview.indexOf(section, ignoreCase = true).takeIf { it >= 0 }
        if (idx != null && idx >= 0) {
            val lineStart = encodedPreview.lastIndexOf('\n', idx).let { if (it < 0) 0 else it + 1 }
            scrollState.scrollTo((lineStart / 4).coerceAtMost(scrollState.maxValue))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(1f)
                .legadoSheetInsets(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.ai_outline_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        if (!readOnly) {
                            enabled = it
                            onToggleEnabled()
                        }
                    },
                    enabled = !readOnly,
                )
                if (!readOnly) {
                    Box {
                        IconButton(onClick = { showTransferMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(
                            expanded = showTransferMenu,
                            onDismissRequest = { showTransferMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ai_outline_export_json)) },
                                onClick = {
                                    showTransferMenu = false
                                    onExportJson()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ai_outline_import_json)) },
                                onClick = {
                                    showTransferMenu = false
                                    onImportJson()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ai_outline_import_from_conversation)) },
                                onClick = {
                                    showTransferMenu = false
                                    onImportFromConversation()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ai_outline_export_to_conversation)) },
                                onClick = {
                                    showTransferMenu = false
                                    onExportToConversation()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ai_outline_cleanup_orphans)) },
                                onClick = {
                                    showTransferMenu = false
                                    onCleanupOrphans()
                                },
                            )
                            val canDelete = !isLoading && (
                                outlineContent.isNotBlank() ||
                                    document.premise.isNotBlank() ||
                                    graph.rootChildren().isNotEmpty()
                                )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.ai_outline_delete),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    showTransferMenu = false
                                    showDeleteConfirm = true
                                },
                                enabled = canDelete,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (readOnly) {
                    Text(
                        readOnlyMessage ?: stringResource(R.string.ai_sheet_viewing_other_conversation),
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.tertiary,
                    )
                }
                if (showBanner) {
                    SheetPreviewBanner(changes = previewChanges, maxHeightDp = 140)
                }
                SourceBookBindingRow(
                    bookName = bookName,
                    bookAuthor = bookAuthor,
                    readOnly = readOnly,
                    onSelectBook = onShowBookPicker,
                    onClearBook = onClearBookSource,
                )
                AiGenerationProgressPanel(
                    visible = isLoading,
                    previewText = generationPreview,
                    reasoningText = generationReasoning,
                    useMonospace = false,
                    maxHeightDp = 180,
                )
                if (useInlineGit) {
                    GitStyleOutlineLinePanel(
                        content = encodedPreview,
                        changes = previewChanges,
                        highlightSection = highlightSection,
                    )
                } else if (!highlightSection.isNullOrBlank()) {
                    Text(
                        "Section: $highlightSection",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.primary,
                    )
                }
                OutlineAnchorFields(
                    document = document,
                    onDocumentChange = {
                        if (!readOnly) {
                            document = it
                            graph = OutlineGraphDocumentBridge.applyAnchors(graph, it)
                        }
                    },
                    readOnly = readOnly,
                    writingSubMode = writingSubMode,
                    activePathLabel = OutlineGraphDocumentBridge.formatActivePathLabel(graph),
                )
                if (!formatSupported && outlineContent.isNotBlank()) {
                    Text(
                        stringResource(R.string.ai_outline_format_unsupported),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.error,
                    )
                }
                // Honor local draft gate — Clear awaiting must hide options before Save.
                if (!readOnly && branchChoice != null && document.awaitingChoice) {
                    ChatOutlineBranchPanel(
                        choice = branchChoice,
                        onSelect = onSelectBranch,
                    )
                }
                Text(
                    stringResource(
                        if (isRoleplay) R.string.ai_outline_mode_hint_roleplay
                        else R.string.ai_outline_mode_hint_author,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
                if (!readOnly) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { onGenerate(graph.outlineKind) },
                            enabled = !isLoading,
                        ) {
                            Text(
                                if (isLoading) stringResource(R.string.ai_outline_generating)
                                else stringResource(R.string.ai_outline_generate),
                                maxLines = 1,
                            )
                        }
                        FilledTonalButton(
                            onClick = { onSupplement(workingOutlineContent, graph.outlineKind) },
                            enabled = !isLoading && hasExpandableContent,
                        ) {
                            Text(stringResource(R.string.ai_outline_supplement), maxLines = 1)
                        }
                        if (graph.rootChildren().isEmpty()) {
                            Box {
                                TextButton(onClick = { showTemplateMenu = true }) {
                                    Text(stringResource(R.string.ai_outline_template_menu))
                                }
                                DropdownMenu(
                                    expanded = showTemplateMenu,
                                    onDismissRequest = { showTemplateMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ai_outline_template_blank)) },
                                        onClick = {
                                            showTemplateMenu = false
                                            formatSupported = true
                                            graph = OutlineGraph.empty(
                                                if (isRoleplay) {
                                                    OutlineGraph.KIND_BRANCHING
                                                } else {
                                                    OutlineGraph.KIND_LINEAR
                                                },
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ai_outline_template_two)) },
                                        onClick = {
                                            showTemplateMenu = false
                                            formatSupported = true
                                            graph = OutlineGraph.emptyTemplate(
                                                if (isRoleplay) {
                                                    OutlineGraph.KIND_BRANCHING
                                                } else {
                                                    OutlineGraph.KIND_LINEAR
                                                },
                                            )
                                        },
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { showGraphCanvas = !showGraphCanvas }) {
                            Text(stringResource(R.string.ai_outline_graph_view))
                        }
                        TextButton(onClick = onShowVersionHistory) {
                            Text(stringResource(R.string.ai_outline_version_history))
                        }
                    }
                }
                if (showGraphCanvas && formatSupported) {
                    OutlineGraphCanvas(
                        graph = graph,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 360.dp),
                        onSelectOption = if (!readOnly && isRoleplay && graph.awaitingChoice) {
                            onSelectBranch
                        } else {
                            null
                        },
                        onSetCurrent = if (!readOnly) {
                            { nodeId ->
                                when (
                                    val r = OutlineGraphEngine.apply(
                                        graph,
                                        OutlineGraphOp.SetCurrentNode(nodeId),
                                        allowSelect = false,
                                        blockWhileAwaiting = false,
                                    )
                                ) {
                                    is OutlineGraphResult.Ok -> graph = r.graph
                                    is OutlineGraphResult.Err -> Unit
                                }
                            }
                        } else {
                            null
                        },
                        onRegressCurrent = if (!readOnly) {
                            {
                                when (val r = OutlineGraphEngine.regressCurrent(graph)) {
                                    is OutlineGraphResult.Ok -> graph = r.graph
                                    is OutlineGraphResult.Err -> Unit
                                }
                            }
                        } else {
                            null
                        },
                        canRegress = !readOnly && OutlineGraphEngine.canRegressCurrent(graph),
                    )
                }
                if (formatSupported) {
                    OutlineGraphTreeEditor(
                        graph = graph,
                        onGraphChange = { if (!readOnly) graph = it },
                        readOnly = readOnly,
                        allowBranches = !readOnly && graph.outlineKind == OutlineGraph.KIND_BRANCHING,
                        enforceWritableDomain = isRoleplay,
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showMarkdownBody = !showMarkdownBody }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (showMarkdownBody) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.ai_outline_view_markdown),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.ai_outline_advanced),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showMarkdownBody) {
                        Text(
                            stringResource(R.string.ai_outline_markdown_body_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = markdownBody,
                            onValueChange = { value ->
                                if (!readOnly) markdownBody = value
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 280.dp)
                                .onFocusChanged { focus ->
                                    if (readOnly) return@onFocusChanged
                                    if (focus.isFocused) {
                                        markdownBodyEditing = true
                                    } else {
                                        markdownBodyEditing = false
                                        val wrapped = buildWorkingOutlineContent(graph, markdownBody)
                                        val decoded = OutlineGraphCodec.decode(wrapped)
                                        if (decoded.graph != null) {
                                            graph = decoded.graph
                                            formatSupported = true
                                        }
                                    }
                                },
                            readOnly = readOnly,
                            textStyle = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            HorizontalDivider(color = colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(if (readOnly) stringResource(R.string.ok) else stringResource(R.string.cancel))
                }
                if (!readOnly) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(OutlineGraphCodec.encode(graph), enabled)
                        },
                        enabled = formatSupported || outlineContent.isBlank(),
                    ) { Text(stringResource(R.string.action_save)) }
                }
            }
        }
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.ai_outline_delete)) },
            text = { Text(stringResource(R.string.ai_outline_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) {
                    Text(
                        stringResource(R.string.ai_outline_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/**
 * 扩写/保存时使用的「工作大纲」：当前图已提交的正文，或用户在 Markdown 区正在编辑的草稿。
 * 草稿未提交到图时也一并带上，并保留图的锚点信息，让模型在完整语义下补全。
 */
private fun buildWorkingOutlineContent(graph: OutlineGraph, markdownBody: String): String {
    val committedBody = OutlineGraphCodec.encodeBody(graph)
    val body = if (markdownBody == committedBody) committedBody else markdownBody
    return buildString {
        append("---\noutline_format: 2\n")
        append("premise: ${graph.premise}\n")
        append("current: ${graph.currentProgress}\n")
        append("next: ${graph.nextGoal}\n")
        append("in_progress: ${graph.inProgress}\n")
        append("outline_kind: ${graph.outlineKind}\n")
        append("awaiting_choice: ${graph.awaitingChoice}\n")
        append("active_path: ${graph.activePath.joinToString("/")}\n")
        append("current_node: ${graph.currentNodeId.orEmpty()}\n")
        append("---\n")
        append(body)
    }.trimEnd()
}

