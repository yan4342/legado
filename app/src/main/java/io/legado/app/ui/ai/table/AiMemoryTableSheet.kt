package io.legado.app.ui.ai.table

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.legado.app.ui.common.compose.rememberLegadoBottomSheetState
import io.legado.app.ui.common.compose.TooltipIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.AiChatConversation
import io.legado.app.data.entities.AiMemoryTable
import io.legado.app.data.entities.AiMemoryTableRow
import io.legado.app.ui.ai.chat.FieldChangeDiffList
import io.legado.app.ui.ai.chat.SourceBookBindingRow
import io.legado.app.ui.ai.chat.SourceBookPickerSheet
import io.legado.app.ui.ai.chat.FieldChangeUi
import io.legado.app.ui.ai.chat.GitStyleCellPreview
import io.legado.app.ui.ai.chat.SheetPreviewBanner
import io.legado.app.ui.ai.chat.canInlineGitHighlightMemoryTable
import io.legado.app.ui.ai.chat.fieldChangesByLeaf
import io.legado.app.ui.ai.chat.shouldShowSheetPreviewBanner
import io.legado.app.ui.common.compose.AiGenerationProgressPanel
import io.legado.app.ui.common.compose.legadoSheetInsets
import io.legado.app.utils.GSON
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject

private val MemoryTableColumnWidth = 96.dp
private val MemoryTableActionWidth = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiMemoryTableSheet(
    onDismiss: () -> Unit,
    conversationId: String = "",
    highlightRowId: String? = null,
    highlightTableId: String? = null,
    highlightMatch: String? = null,
    previewChanges: List<FieldChangeUi> = emptyList(),
    readOnly: Boolean = false,
) {
    val viewModel: AiMemoryTableViewModel = koinInject()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sheetState = rememberLegadoBottomSheetState()
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is AiMemoryTableEffect.ShowMessage ->
                    android.widget.Toast.makeText(context, effect.message, android.widget.Toast.LENGTH_SHORT).show()
                is AiMemoryTableEffect.CopyToClipboard ->
                    context.sendToClip(effect.text)
                is AiMemoryTableEffect.ShareText ->
                    context.share(effect.text, effect.title)
            }
        }
    }
    LaunchedEffect(conversationId) {
        viewModel.onIntent(AiMemoryTableIntent.SetConversationId(conversationId))
    }
    LaunchedEffect(highlightRowId) {
        viewModel.onIntent(AiMemoryTableIntent.SetHighlightRow(highlightRowId))
    }
    LaunchedEffect(highlightTableId) {
        if (!highlightTableId.isNullOrBlank()) {
            viewModel.onIntent(AiMemoryTableIntent.SelectTable(highlightTableId))
        }
    }
    LaunchedEffect(highlightMatch, state.selectedTableId) {
        if (!highlightMatch.isNullOrBlank() && highlightRowId.isNullOrBlank()) {
            viewModel.onIntent(AiMemoryTableIntent.ResolveHighlightMatch(highlightMatch))
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!readOnly && state.isEditing) viewModel.onIntent(AiMemoryTableIntent.SaveTable)
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .legadoSheetInsets(),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.selectedTableId != null || state.isEditing) {
                    IconButton(onClick = {
                        if (state.isEditing) viewModel.onIntent(AiMemoryTableIntent.CancelEdit)
                        else viewModel.onIntent(AiMemoryTableIntent.BackToList)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
                Text(
                    if (state.isEditing) "Edit Table"
                    else if (state.selectedTableId != null) state.tables.find { it.id == state.selectedTableId }?.name ?: "Table"
                    else "Memory Tables",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.selectedTableId == null && !state.isEditing) {
                    if (!readOnly) {
                        IconButton(onClick = { viewModel.onIntent(AiMemoryTableIntent.RestoreLatestSnapshot) }) {
                            Icon(Icons.Default.Undo, contentDescription = stringResource(R.string.ai_memory_undo_cd))
                        }
                        Switch(
                            checked = state.allEnabled,
                            onCheckedChange = { viewModel.onIntent(AiMemoryTableIntent.ToggleAllEnabled) },
                            modifier = Modifier.height(24.dp),
                        )
                        Spacer(Modifier.width(2.dp))

                        Box {
                            IconButton(onClick = { viewModel.onIntent(AiMemoryTableIntent.ShowTransferMenu) }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More", modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(
                                expanded = state.showTransferMenu,
                                onDismissRequest = { viewModel.onIntent(AiMemoryTableIntent.DismissTransferMenu) },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("AI Generate") },
                                    onClick = {
                                        viewModel.onIntent(AiMemoryTableIntent.DismissTransferMenu)
                                        viewModel.onIntent(AiMemoryTableIntent.GenerateTable)
                                    },
                                    enabled = !state.isGenerating && state.conversationId.isNotBlank(),
                                )
                                DropdownMenuItem(
                                    text = { Text("Import JSON") },
                                    onClick = {
                                        viewModel.onIntent(AiMemoryTableIntent.DismissTransferMenu)
                                        viewModel.onIntent(AiMemoryTableIntent.ShowImportDialog)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Import from conversation") },
                                    onClick = { viewModel.onIntent(AiMemoryTableIntent.ShowImportFromConversation) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Export to conversation") },
                                    onClick = { viewModel.onIntent(AiMemoryTableIntent.ShowExportToConversation) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Export JSON") },
                                    onClick = { viewModel.onIntent(AiMemoryTableIntent.ShowExportDialog) },
                                )
                                DropdownMenuItem(
                                    text = { Text("??????") },
                                    onClick = { viewModel.onIntent(AiMemoryTableIntent.CleanupOrphans) },
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.onIntent(AiMemoryTableIntent.StartEditTable(null)) }) {
                            Icon(Icons.Default.Add, contentDescription = "New Table")
                        }
                    }
                }
            }
            HorizontalDivider()

            val isAiWorking = state.isGenerating || state.isRegenerating
            AiGenerationProgressPanel(
                visible = isAiWorking,
                previewText = state.generationPreview,
                reasoningText = state.generationReasoning,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                maxHeightDp = 180,
            )

            val effectiveRowId = state.highlightRowId ?: highlightRowId
            val useInlineGit = canInlineGitHighlightMemoryTable(effectiveRowId, previewChanges)
            if (readOnly) {
                Text(
                    stringResource(R.string.ai_sheet_tool_preview_readonly),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            if (shouldShowSheetPreviewBanner(previewChanges, useInlineGit)) {
                SheetPreviewBanner(
                    changes = previewChanges,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isEditing && !readOnly -> EditTableView(state, viewModel, onDismiss)
                    state.selectedTableId != null -> TableDetailView(
                        state = state,
                        viewModel = viewModel,
                        previewChanges = previewChanges,
                        effectiveRowId = effectiveRowId,
                        useInlineGit = useInlineGit,
                        readOnly = readOnly,
                    )
                    else -> TableListView(state, viewModel, readOnly = readOnly)
                }
            }
        }
    }

    // Row edit dialog
    if (state.showRowEditDialog && !readOnly) {
        RowEditDialog(
            rowData = state.editingRowData,
            isNew = state.editingRowId == null,
            selectedTableId = state.selectedTableId,
            tables = state.tables,
            onUpdate = { viewModel.onIntent(AiMemoryTableIntent.UpdateRowField(it)) },
            onSave = { viewModel.onIntent(AiMemoryTableIntent.SaveRow) },
            onDismiss = { viewModel.onIntent(AiMemoryTableIntent.CancelEditRow) },
        )
    }

    // Export dialog
    if (state.showExportDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(AiMemoryTableIntent.DismissExportDialog) },
            title = { Text("Export Memory Tables") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Copy the JSON below or share it. Import this into another session to transfer the tables.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.exportJson,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 280.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TooltipIconButton(
                    onClick = { viewModel.onIntent(AiMemoryTableIntent.CopyExportToClipboard) },
                    label = "Copy",
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TooltipIconButton(
                        onClick = { viewModel.onIntent(AiMemoryTableIntent.ShareExport) },
                        label = "Share",
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                    }
                    TextButton(onClick = { viewModel.onIntent(AiMemoryTableIntent.DismissExportDialog) }) {
                        Text("Close")
                    }
                }
            },
        )
    }

    // Import dialog
    if (state.showImportDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(AiMemoryTableIntent.DismissImportDialog) },
            title = { Text("Import Memory Tables") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste the exported JSON below. Tables will be imported into the current session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.importJson,
                        onValueChange = { viewModel.onIntent(AiMemoryTableIntent.UpdateImportJson(it)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 280.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        placeholder = { Text("Paste JSON here...") },
                    )
                }
            },
            confirmButton = {
                TooltipIconButton(
                    onClick = { viewModel.onIntent(AiMemoryTableIntent.ConfirmImport) },
                    label = "Import",
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(AiMemoryTableIntent.DismissImportDialog) }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Conversation picker dialog for transfer
    if (state.showConversationPicker) {
        val isImport = state.conversationPickerMode == "import"
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(AiMemoryTableIntent.DismissConversationPicker) },
            title = { Text(if (isImport) "Import from conversation" else "Export to conversation") },
            text = {
                // Fixed height so LazyColumn gets bounded constraints (do not nest in verticalScroll).
                Column(modifier = Modifier.height(360.dp)) {
                    if (state.availableConversations.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No other conversations available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(
                            if (isImport) "Select a conversation to import tables from:"
                            else "Select a conversation to export tables to:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(state.availableConversations, key = { it.id }) { conv ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    onClick = {
                                        viewModel.onIntent(AiMemoryTableIntent.SelectConversationForTransfer(conv.id))
                                    },
                                ) {
                                    Text(
                                        conv.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(AiMemoryTableIntent.DismissConversationPicker) }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (state.showBookPicker && !readOnly) {
        SourceBookPickerSheet(
            books = state.bookshelfBooks,
            onSelect = { book -> viewModel.onIntent(AiMemoryTableIntent.SelectBook(book)) },
            onDismissRequest = { viewModel.onIntent(AiMemoryTableIntent.DismissBookPicker) },
        )
    }

    state.pendingUndo?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(AiMemoryTableIntent.DismissUndoSnapshot) },
            title = { Text(stringResource(R.string.ai_memory_undo_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pending.summary.isNotBlank()) {
                        Text(
                            pending.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (pending.changes.isNotEmpty()) {
                        Text(
                            stringResource(R.string.ai_memory_undo_preview_hint),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        FieldChangeDiffList(
                            changes = pending.changes,
                            maxHeightDp = 280,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(AiMemoryTableIntent.ConfirmUndoSnapshot) }) {
                    Text(stringResource(R.string.ai_memory_undo_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(AiMemoryTableIntent.DismissUndoSnapshot) }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun TableListView(
    state: AiMemoryTableUiState,
    viewModel: AiMemoryTableViewModel,
    readOnly: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }

    if (state.tables.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("No memory tables yet", color = colorScheme.onSurfaceVariant)
                if (!readOnly) {
                    if (state.conversationId.isNotBlank()) {
                        Button(
                            onClick = { viewModel.onIntent(AiMemoryTableIntent.GenerateTable) },
                            enabled = !state.isGenerating,
                        ) {
                            if (state.isGenerating) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("AI Generate")
                        }
                    }
                    OutlinedButton(onClick = { viewModel.onIntent(AiMemoryTableIntent.StartEditTable(null)) }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Create Manually")
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.tables, key = { it.id }) { table ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.surfaceVariant,
                    onClick = { viewModel.onIntent(AiMemoryTableIntent.SelectTable(table.id)) },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(table.name, style = MaterialTheme.typography.titleMedium)
                                val columns = runCatching {
                                    GSON.fromJson(table.columns, Array<String>::class.java).joinToString(", ")
                                }.getOrNull() ?: table.columns
                                Text(columns, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                            if (!readOnly) {
                                OutlinedButton(
                                    onClick = { viewModel.onIntent(AiMemoryTableIntent.RegenerateTable(table.id)) },
                                    enabled = !state.isRegenerating,
                                    modifier = Modifier.height(32.dp),
                                ) {
                                    if (state.isRegenerating) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Repair", style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { viewModel.onIntent(AiMemoryTableIntent.StartEditTable(table)) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { deleteConfirmId = table.id }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteConfirmId?.let { id ->
        val table = state.tables.find { it.id == id }
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text("Delete Table") },
            text = { Text("Delete \"${table?.name}\" and all its rows?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onIntent(AiMemoryTableIntent.DeleteTable(id))
                    deleteConfirmId = null
                }) { Text("Delete", color = colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmId = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TableDetailView(
    state: AiMemoryTableUiState,
    viewModel: AiMemoryTableViewModel,
    previewChanges: List<FieldChangeUi> = emptyList(),
    effectiveRowId: String? = null,
    useInlineGit: Boolean = false,
    readOnly: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val table = state.tables.find { it.id == state.selectedTableId } ?: return
    val columns = runCatching { GSON.fromJson(table.columns, Array<String>::class.java).toList() }.getOrNull() ?: emptyList()
    val columnChanges = fieldChangesByLeaf(previewChanges, table.id)
    val hScrollState = rememberScrollState()
    val listState = rememberLazyListState()
    var deleteConfirmRowId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(effectiveRowId, state.selectedTableRows) {
        val rowId = effectiveRowId ?: return@LaunchedEffect
        val index = state.selectedTableRows.indexOfFirst { it.id == rowId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(hScrollState)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            columns.forEach { col ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colorScheme.primaryContainer,
                    modifier = Modifier.width(MemoryTableColumnWidth),
                ) {
                    Text(
                        col,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(MemoryTableActionWidth))
        }
        HorizontalDivider()

        if (state.selectedTableRows.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No rows ??tap + to add", color = colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.selectedTableRows, key = { it.id }) { row ->
                    MemoryTableRowItem(
                        row = row,
                        columns = columns,
                        columnChanges = columnChanges,
                        isHighlighted = effectiveRowId == row.id,
                        showGitCells = useInlineGit && effectiveRowId == row.id,
                        hScrollState = hScrollState,
                        onPatchCell = if (!readOnly) {
                            { col, value -> viewModel.onIntent(AiMemoryTableIntent.PatchCell(row.id, col, value)) }
                        } else {
                            { _, _ -> }
                        },
                        onEditRow = if (!readOnly) {
                            { viewModel.onIntent(AiMemoryTableIntent.StartEditRow(row)) }
                        } else {
                            {}
                        },
                        onDeleteRow = if (!readOnly) {
                            { deleteConfirmRowId = row.id }
                        } else {
                            {}
                        },
                        readOnly = readOnly,
                    )
                }
            }
        }

        if (!readOnly) {
            Button(
                onClick = { viewModel.onIntent(AiMemoryTableIntent.StartEditRow(null)) },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add Row")
            }
        }
    }

    deleteConfirmRowId?.let { rowId ->
        AlertDialog(
            onDismissRequest = { deleteConfirmRowId = null },
            title = { Text(stringResource(R.string.ai_memory_table_delete_row_title)) },
            text = { Text(stringResource(R.string.ai_memory_table_delete_row_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onIntent(AiMemoryTableIntent.DeleteRow(rowId))
                    deleteConfirmRowId = null
                }) { Text(stringResource(R.string.delete), color = colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmRowId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun MemoryTableRowItem(
    row: AiMemoryTableRow,
    columns: List<String>,
    columnChanges: Map<String, FieldChangeUi>,
    isHighlighted: Boolean,
    showGitCells: Boolean,
    hScrollState: ScrollState,
    onPatchCell: (column: String, value: String) -> Unit,
    onEditRow: () -> Unit,
    onDeleteRow: () -> Unit,
    readOnly: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val rowData = remember(row.id, row.rowData) {
        io.legado.app.utils.parseJsonStringMap(row.rowData)
    }
    var editingColumn by remember(row.id) { mutableStateOf<String?>(null) }
    var editValue by remember(row.id) { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isHighlighted) colorScheme.primaryContainer.copy(alpha = 0.35f) else colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .horizontalScroll(hScrollState),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            columns.forEach { col ->
                val cellValue = rowData[col]?.toString() ?: ""
                val cellChange = if (showGitCells) columnChanges[col] else null
                MemoryTableCell(
                    rowId = row.id,
                    column = col,
                    cellValue = cellValue,
                    cellChange = cellChange,
                    showGitCell = showGitCells && cellChange != null,
                    isEditing = !readOnly && editingColumn == col,
                    editValue = editValue,
                    columnWidth = MemoryTableColumnWidth,
                    readOnly = readOnly,
                    onStartEdit = { initial ->
                        editingColumn = col
                        editValue = initial
                    },
                    onEditValueChange = { editValue = it },
                    onCommitEdit = {
                        if (editValue != cellValue) {
                            onPatchCell(col, editValue)
                        }
                        editingColumn = null
                    },
                )
            }
            if (!readOnly) {
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onEditRow, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onDeleteRow, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun MemoryTableCell(
    rowId: String,
    column: String,
    cellValue: String,
    cellChange: FieldChangeUi?,
    showGitCell: Boolean,
    isEditing: Boolean,
    editValue: String,
    columnWidth: Dp,
    readOnly: Boolean = false,
    onStartEdit: (initial: String) -> Unit,
    onEditValueChange: (String) -> Unit,
    onCommitEdit: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    if (isEditing) {
        var hadFocus by remember(rowId, column) { mutableStateOf(false) }
        OutlinedTextField(
            value = editValue,
            onValueChange = onEditValueChange,
            modifier = Modifier
                .width(columnWidth)
                .onFocusChanged { focus ->
                    if (!focus.isFocused && hadFocus) {
                        onCommitEdit()
                    }
                    hadFocus = focus.isFocused
                },
            textStyle = MaterialTheme.typography.bodySmall,
            singleLine = true,
        )
    } else if (showGitCell && cellChange != null) {
        GitStyleCellPreview(
            change = cellChange,
            currentValue = cellValue,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .width(columnWidth)
                .then(
                    if (!readOnly) {
                        Modifier.clickable {
                            onStartEdit(cellChange.newValue.ifBlank { cellValue })
                        }
                    } else {
                        Modifier
                    }
                ),
        )
    } else {
        Text(
            text = cellValue.ifBlank { "-" },
            style = MaterialTheme.typography.bodySmall,
            color = if (cellValue.isBlank()) {
                colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            } else {
                colorScheme.onSurface
            },
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .width(columnWidth)
                .then(
                    if (!readOnly) {
                        Modifier.clickable { onStartEdit(cellValue) }
                    } else {
                        Modifier
                    }
                ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EditTableView(
    state: AiMemoryTableUiState,
    viewModel: AiMemoryTableViewModel,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.editingTableName,
            onValueChange = { viewModel.onIntent(AiMemoryTableIntent.UpdateEditField("name", it)) },
            label = { Text("Table name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = state.editingTableColumns,
            onValueChange = { viewModel.onIntent(AiMemoryTableIntent.UpdateEditField("columns", it)) },
            label = { Text("Columns (JSON array)") },
            supportingText = { Text("e.g. [\"???\",\"??\",\"??\"]") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 6,
        )
        SourceBookBindingRow(
            bookName = state.editingBookName,
            bookAuthor = state.editingBookAuthor,
            readOnly = false,
            onSelectBook = { viewModel.onIntent(AiMemoryTableIntent.ShowBookPicker) },
            onClearBook = { viewModel.onIntent(AiMemoryTableIntent.ClearBookSource) },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { viewModel.onIntent(AiMemoryTableIntent.CancelEdit) }, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(onClick = { viewModel.onIntent(AiMemoryTableIntent.SaveTable) }, modifier = Modifier.weight(1f), enabled = state.editingTableName.isNotBlank()) {
                Text("Save")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RowEditDialog(
    rowData: String,
    isNew: Boolean,
    selectedTableId: String?,
    tables: List<AiMemoryTable>,
    onUpdate: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val table = tables.find { it.id == selectedTableId }
    val columns = runCatching { table?.columns?.let { GSON.fromJson(it, Array<String>::class.java).toList() } }.getOrNull() ?: emptyList()
    val colorScheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onSave,
        title = { Text(if (isNew) "Add Row" else "Edit Row") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Dynamic fields based on columns
                val dataMap = io.legado.app.utils.parseJsonStringMap(rowData)
                columns.forEach { col ->
                    OutlinedTextField(
                        value = dataMap[col]?.toString() ?: "",
                        onValueChange = { newVal ->
                            val updated = dataMap.toMutableMap()
                            updated[col] = newVal
                            onUpdate(GSON.toJson(updated))
                        },
                        label = { Text(col) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 4,
                    )
                }
                // Raw JSON fallback
                if (columns.isEmpty()) {
                    OutlinedTextField(
                        value = rowData,
                        onValueChange = onUpdate,
                        label = { Text("Row data (JSON)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 8,
                    )
                }
            }
        },
        confirmButton = {
            TooltipIconButton(
                onClick = onSave,
                label = "Save",
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
