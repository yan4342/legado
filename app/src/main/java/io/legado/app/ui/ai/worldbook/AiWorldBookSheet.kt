package io.legado.app.ui.ai.worldbook

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.AiWorldBook
import io.legado.app.data.entities.AiWorldBookEntry
import io.legado.app.data.entities.Book
import io.legado.app.ui.common.compose.AiGenerationProgressPanel
import io.legado.app.ui.common.compose.legadoSheetInsets
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiWorldBookSheet(
    onDismiss: () -> Unit,
    highlightWorldBookId: String? = null,
    readOnly: Boolean = false,
) {
    val viewModel: AiWorldBookViewModel = koinInject()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sheetState = rememberLegadoBottomSheetState()
    val colorScheme = MaterialTheme.colorScheme

    var showImportJsonDialog by remember { mutableStateOf(false) }
    var importJsonDraft by remember { mutableStateOf("") }
    var importIntoCurrent by remember { mutableStateOf(false) }

    fun openImportDialog(intoCurrent: Boolean) {
        importIntoCurrent = intoCurrent
        importJsonDraft = ""
        showImportJsonDialog = true
    }

    val worldInfoImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.onSuccess { bytes ->
            if (bytes == null || bytes.isEmpty()) {
                android.util.Log.w("StWorldImport", "picker empty bytes uri=$uri")
                return@onSuccess
            }
            val name = uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
                ?: "Imported Lorebook"
            val isPng = io.legado.app.domain.model.PngCharaDecoder.isPng(bytes)
            android.util.Log.i(
                "StWorldImport",
                "picker uri=$uri mime=${context.contentResolver.getType(uri)} bytes=${bytes.size} isPng=$isPng intoCurrent=$importIntoCurrent name=$name",
            )
            if (importIntoCurrent) {
                viewModel.onIntent(AiWorldBookIntent.ImportEntriesBytesIntoCurrent(bytes))
            } else {
                viewModel.onIntent(AiWorldBookIntent.ImportWorldInfoBytes(bytes, name))
            }
            importIntoCurrent = false
        }.onFailure {
            android.util.Log.e("StWorldImport", "picker read failed uri=$uri: ${it.message}", it)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is AiWorldBookEffect.ShowMessage -> {
                    android.widget.Toast.makeText(context, effect.message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    LaunchedEffect(highlightWorldBookId, state.worldBooks) {
        val id = highlightWorldBookId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        state.worldBooks.find { it.id == id }?.let { wb ->
            viewModel.onIntent(AiWorldBookIntent.StartEdit(wb))
        }
    }

    val isEditing = state.isEditing

    ModalBottomSheet(
        onDismissRequest = {
            if (!readOnly && isEditing) {
                viewModel.onIntent(AiWorldBookIntent.Save)
            }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isEditing) {
                    IconButton(onClick = { viewModel.onIntent(AiWorldBookIntent.CancelEdit) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
                Text(
                    if (isEditing) stringResource(R.string.ai_world_book_edit)
                    else stringResource(R.string.ai_world_book_list),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (!isEditing && !readOnly) {
                    IconButton(onClick = { openImportDialog(intoCurrent = false) }) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = stringResource(R.string.ai_world_book_import_json),
                        )
                    }
                    IconButton(onClick = { viewModel.onIntent(AiWorldBookIntent.StartEdit(null)) }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ai_world_book_new))
                    }
                }
                IconButton(onClick = {
                    if (isEditing) viewModel.onIntent(AiWorldBookIntent.CancelEdit)
                    onDismiss()
                }) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }
            HorizontalDivider()
            if (readOnly) {
                Text(
                    stringResource(R.string.ai_sheet_tool_preview_readonly),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isEditing) {
                    EditContent(
                        state = state,
                        viewModel = viewModel,
                        readOnly = readOnly,
                        onImportEntries = { openImportDialog(intoCurrent = true) },
                    )
                } else {
                    ListContent(
                        state = state,
                        viewModel = viewModel,
                        readOnly = readOnly,
                        onImportJson = { openImportDialog(intoCurrent = false) },
                    )
                }
            }
        }
    }

    if (showImportJsonDialog && !readOnly) {
        AlertDialog(
            onDismissRequest = { showImportJsonDialog = false },
            title = {
                Text(
                    if (importIntoCurrent) stringResource(R.string.ai_world_book_import_entries_into)
                    else stringResource(R.string.ai_world_book_import_dialog_title),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (importIntoCurrent) stringResource(R.string.ai_world_book_import_entries_hint)
                        else stringResource(R.string.ai_world_book_import_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = importJsonDraft,
                        onValueChange = { importJsonDraft = it },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        placeholder = { Text("JSON…") },
                    )
                    TextButton(
                        onClick = {
                            showImportJsonDialog = false
                            worldInfoImportLauncher.launch(
                                arrayOf("application/json", "image/png", "text/*", "*/*"),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.ai_world_book_import_from_file))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val json = importJsonDraft.trim()
                        if (json.isEmpty()) return@TextButton
                        android.util.Log.i(
                            "StWorldImport",
                            "paste jsonLen=${json.length} intoCurrent=$importIntoCurrent head=${json.take(60).replace('\n', ' ')}",
                        )
                        showImportJsonDialog = false
                        if (importIntoCurrent) {
                            viewModel.onIntent(AiWorldBookIntent.ImportEntriesIntoCurrent(json))
                        } else {
                            viewModel.onIntent(AiWorldBookIntent.ImportWorldInfoJson(json))
                        }
                        importJsonDraft = ""
                    },
                    enabled = importJsonDraft.isNotBlank(),
                ) {
                    Text(stringResource(R.string.ai_world_book_import_json))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportJsonDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    // Book picker nested sheet
    if (state.showBookPicker && !readOnly) {
        BookPickerSheet(
            books = state.bookshelfBooks,
            onSelect = { book -> viewModel.onIntent(AiWorldBookIntent.SelectBook(book)) },
            onDismissRequest = { viewModel.onIntent(AiWorldBookIntent.DismissBookPicker) },
        )
    }

    if (state.showEntryEditor && !readOnly) {
        val entry = state.editingEntry
        if (entry != null) {
            val config = LocalConfiguration.current
            val bodyMaxHeight = (config.screenHeightDp * 0.55f).dp
            AlertDialog(
                onDismissRequest = { viewModel.onIntent(AiWorldBookIntent.CancelEntryEdit) },
                properties = DialogProperties(usePlatformDefaultWidth = true),
                title = {
                    Text(
                        if (entry.name.isBlank()) "New Lore Entry" else entry.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                text = {
                    var priorityText by remember(entry.id) {
                        mutableStateOf(entry.priority.toString())
                    }
                    var insertDepthText by remember(entry.id) {
                        mutableStateOf(entry.insertDepth.toString())
                    }
                    var scanDepthText by remember(entry.id) {
                        mutableStateOf(entry.scanDepth.toString())
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = bodyMaxHeight)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = entry.name,
                            onValueChange = { viewModel.onIntent(AiWorldBookIntent.UpdateEntryField("name", it)) },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = entry.keys,
                            onValueChange = { viewModel.onIntent(AiWorldBookIntent.UpdateEntryField("keys", it)) },
                            label = { Text("Keywords (comma/newline)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 1,
                            maxLines = 4,
                        )
                        OutlinedTextField(
                            value = entry.content,
                            onValueChange = { viewModel.onIntent(AiWorldBookIntent.UpdateEntryField("content", it)) },
                            label = { Text("Content") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 220.dp),
                            minLines = 4,
                            maxLines = 12,
                        )
                        OutlinedTextField(
                            value = priorityText,
                            onValueChange = { raw ->
                                val filtered = raw.filter { it.isDigit() }
                                priorityText = filtered
                                filtered.toIntOrNull()?.let {
                                    viewModel.onIntent(
                                        AiWorldBookIntent.UpdateEntryField("priority", it.toString()),
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.ai_world_book_entry_order)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = {
                                Text(stringResource(R.string.ai_world_book_entry_order_hint))
                            },
                        )
                        Text(
                            stringResource(R.string.ai_world_book_entry_position),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            listOf(
                                AiWorldBookEntry.POSITION_PREFIX to R.string.ai_world_book_entry_position_prefix,
                                AiWorldBookEntry.POSITION_IN_CHAT to R.string.ai_world_book_entry_position_in_chat,
                            ).forEach { (value, labelRes) ->
                                FilterChip(
                                    selected = entry.position == value,
                                    onClick = {
                                        viewModel.onIntent(AiWorldBookIntent.UpdateEntryField("position", value))
                                    },
                                    label = { Text(stringResource(labelRes)) },
                                )
                            }
                        }
                        if (entry.position == AiWorldBookEntry.POSITION_IN_CHAT) {
                            OutlinedTextField(
                                value = insertDepthText,
                                onValueChange = { raw ->
                                    val filtered = raw.filter { it.isDigit() }
                                    insertDepthText = filtered
                                    filtered.toIntOrNull()?.let {
                                        viewModel.onIntent(
                                            AiWorldBookIntent.UpdateEntryField(
                                                "insertDepth",
                                                it.toString(),
                                            ),
                                        )
                                    }
                                },
                                label = { Text(stringResource(R.string.ai_world_book_entry_insert_depth)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                supportingText = {
                                    Text(stringResource(R.string.ai_world_book_entry_insert_depth_hint))
                                },
                            )
                            Text(
                                stringResource(R.string.ai_world_book_entry_role),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                listOf(
                                    AiWorldBookEntry.ROLE_SYSTEM to R.string.ai_world_book_entry_role_system,
                                    AiWorldBookEntry.ROLE_USER to R.string.ai_world_book_entry_role_user,
                                    AiWorldBookEntry.ROLE_ASSISTANT to R.string.ai_world_book_entry_role_assistant,
                                ).forEach { (value, labelRes) ->
                                    FilterChip(
                                        selected = entry.role == value,
                                        onClick = {
                                            viewModel.onIntent(AiWorldBookIntent.UpdateEntryField("role", value))
                                        },
                                        label = { Text(stringResource(labelRes)) },
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = scanDepthText,
                            onValueChange = { raw ->
                                val filtered = raw.filter { it.isDigit() }
                                scanDepthText = filtered
                                filtered.toIntOrNull()?.let {
                                    viewModel.onIntent(
                                        AiWorldBookIntent.UpdateEntryField("scanDepth", it.toString()),
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.ai_world_book_entry_scan_depth)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = {
                                Text(stringResource(R.string.ai_world_book_entry_scan_depth_hint))
                            },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stringResource(R.string.ai_world_book_entry_enabled),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Switch(
                                checked = entry.enabled,
                                onCheckedChange = {
                                    viewModel.onIntent(
                                        AiWorldBookIntent.UpdateEntryField(
                                            "enabled",
                                            if (it) "true" else "false",
                                        ),
                                    )
                                },
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stringResource(R.string.ai_world_book_entry_constant),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Switch(
                                checked = entry.constant,
                                onCheckedChange = {
                                    viewModel.onIntent(
                                        AiWorldBookIntent.UpdateEntryField(
                                            "constant",
                                            if (it) "true" else "false",
                                        ),
                                    )
                                },
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.onIntent(AiWorldBookIntent.SaveEntry) }) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onIntent(AiWorldBookIntent.CancelEntryEdit) }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun ListContent(
    state: AiWorldBookUiState,
    viewModel: AiWorldBookViewModel,
    readOnly: Boolean = false,
    onImportJson: () -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }

    if (state.worldBooks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    if (readOnly) "No world books" else "No world books yet — tap + to create one",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.worldBooks, key = { it.id }) { wb ->
                WorldBookCard(
                    worldBook = wb,
                    readOnly = readOnly,
                    onEdit = { viewModel.onIntent(AiWorldBookIntent.StartEdit(wb)) },
                    onDelete = { deleteConfirmId = wb.id },
                    onToggleEnabled = { viewModel.onIntent(AiWorldBookIntent.ToggleEnabled(wb.id)) },
                )
            }
        }
    }

    deleteConfirmId?.let { id ->
        val wb = state.worldBooks.find { it.id == id }
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text(stringResource(R.string.ai_world_book_delete_confirm)) },
            text = { Text(wb?.name.orEmpty()) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onIntent(AiWorldBookIntent.Delete(id))
                    deleteConfirmId = null
                }) { Text(stringResource(R.string.ok), color = colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmId = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun WorldBookCard(
    worldBook: AiWorldBook,
    readOnly: Boolean = false,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (worldBook.enabled) colorScheme.surfaceVariant else colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = onEdit,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            worldBook.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (worldBook.enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    if (worldBook.bookName.isNotBlank()) {
                        Text(
                            "${worldBook.bookName} · ${worldBook.bookAuthor}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (worldBook.enabled) colorScheme.onSurfaceVariant else colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!readOnly) {
                    IconButton(onClick = onToggleEnabled, modifier = Modifier.size(40.dp)) {
                        Icon(
                            if (worldBook.enabled) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (worldBook.enabled) "Enabled" else "Disabled",
                            tint = if (worldBook.enabled) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }
            if (worldBook.writingStyle.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    worldBook.writingStyle.take(120),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (worldBook.enabled) colorScheme.onSurfaceVariant else colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EditContent(
    state: AiWorldBookUiState,
    viewModel: AiWorldBookViewModel,
    readOnly: Boolean = false,
    onImportEntries: () -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    val fields = state.editFields
    // part1 = style archive fields; part2 = lore entries.
    // When part1 is empty, show lore first so ST-style lorebooks aren't buried under blank forms.
    val part1Empty = fields.writingStyle.isBlank() &&
        fields.grammar.isBlank() &&
        fields.plotSummary.isBlank() &&
        fields.representativeDialogues.isBlank() &&
        fields.representativeProse.isBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = fields.name,
            onValueChange = { if (!readOnly) viewModel.onIntent(AiWorldBookIntent.UpdateField("name", it)) },
            label = { Text(stringResource(R.string.ai_world_book_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = readOnly,
        )

        if (fields.bookName.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = colorScheme.surfaceVariant,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.ai_world_book_source), style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                    Text("${fields.bookName} · ${fields.bookAuthor}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (part1Empty) {
            EditLoreEntriesSection(
                state = state,
                viewModel = viewModel,
                readOnly = readOnly,
                onImportEntries = onImportEntries,
            )
            EditStyleFieldsSection(
                fields = fields,
                viewModel = viewModel,
                readOnly = readOnly,
            )
        } else {
            EditStyleFieldsSection(
                fields = fields,
                viewModel = viewModel,
                readOnly = readOnly,
            )
            EditLoreEntriesSection(
                state = state,
                viewModel = viewModel,
                readOnly = readOnly,
                onImportEntries = onImportEntries,
            )
        }

        if (!readOnly) {
            if (state.selectedBook != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = colorScheme.secondaryContainer,
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "${stringResource(R.string.ai_world_book_chapter_range)}: ${state.selectedBook!!.name}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        val bookKey = state.selectedBook!!.bookUrl
                        var chapterStartText by remember(bookKey) {
                            mutableStateOf(state.chapterStartIndex.toString())
                        }
                        var chapterEndText by remember(bookKey) {
                            mutableStateOf(state.chapterEndIndex.toString())
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedTextField(
                                value = chapterStartText,
                                onValueChange = { raw ->
                                    val filtered = raw.filter { it.isDigit() }
                                    chapterStartText = filtered
                                    filtered.toIntOrNull()?.let { v ->
                                        viewModel.onIntent(
                                            AiWorldBookIntent.SetChapterRange(v, state.chapterEndIndex),
                                        )
                                    }
                                },
                                label = { Text("Start") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = chapterEndText,
                                onValueChange = { raw ->
                                    val filtered = raw.filter { it.isDigit() }
                                    chapterEndText = filtered
                                    filtered.toIntOrNull()?.let { v ->
                                        viewModel.onIntent(
                                            AiWorldBookIntent.SetChapterRange(state.chapterStartIndex, v),
                                        )
                                    }
                                },
                                label = { Text("End") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        AiGenerationProgressPanel(
                            visible = state.extractionInProgress,
                            previewText = state.extractionPreview,
                            reasoningText = state.extractionReasoning,
                            maxHeightDp = 160,
                        )
                        if (state.extractionInProgress) {
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(
                            onClick = { viewModel.onIntent(AiWorldBookIntent.StartExtraction) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.extractionInProgress,
                        ) {
                            if (state.extractionInProgress) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.ai_world_book_extract))
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { viewModel.onIntent(AiWorldBookIntent.ShowBookPicker) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.ai_world_book_select_book))
                }
            }

            if (state.editingExistingId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.onIntent(AiWorldBookIntent.ExportStyleToPrompts(state.editingExistingId!!)) },
                        modifier = Modifier.weight(1f),
                        enabled = fields.writingStyle.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ai_world_book_export_style), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { viewModel.onIntent(AiWorldBookIntent.ExportGrammarToPrompts(state.editingExistingId!!)) },
                        modifier = Modifier.weight(1f),
                        enabled = fields.grammar.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ai_world_book_export_grammar), maxLines = 1)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.onIntent(AiWorldBookIntent.CancelEdit) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = { viewModel.onIntent(AiWorldBookIntent.Save) },
                    modifier = Modifier.weight(1f),
                    enabled = fields.name.isNotBlank(),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun EditStyleFieldsSection(
    fields: WorldBookEditFields,
    viewModel: AiWorldBookViewModel,
    readOnly: Boolean,
) {
    OutlinedTextField(
        value = fields.writingStyle,
        onValueChange = { if (!readOnly) viewModel.onIntent(AiWorldBookIntent.UpdateField("writingStyle", it)) },
        label = { Text(stringResource(R.string.ai_world_book_writing_style)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 10,
        readOnly = readOnly,
    )
    OutlinedTextField(
        value = fields.grammar,
        onValueChange = { if (!readOnly) viewModel.onIntent(AiWorldBookIntent.UpdateField("grammar", it)) },
        label = { Text(stringResource(R.string.ai_world_book_grammar)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 8,
        readOnly = readOnly,
    )
    OutlinedTextField(
        value = fields.plotSummary,
        onValueChange = { if (!readOnly) viewModel.onIntent(AiWorldBookIntent.UpdateField("plotSummary", it)) },
        label = { Text(stringResource(R.string.ai_world_book_plot)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 10,
        readOnly = readOnly,
    )
    OutlinedTextField(
        value = fields.representativeDialogues,
        onValueChange = { if (!readOnly) viewModel.onIntent(AiWorldBookIntent.UpdateField("representativeDialogues", it)) },
        label = { Text(stringResource(R.string.ai_world_book_dialogues)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 10,
        readOnly = readOnly,
    )
    OutlinedTextField(
        value = fields.representativeProse,
        onValueChange = { if (!readOnly) viewModel.onIntent(AiWorldBookIntent.UpdateField("representativeProse", it)) },
        label = { Text(stringResource(R.string.ai_world_book_prose)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 10,
        readOnly = readOnly,
    )
}

@Composable
private fun EditLoreEntriesSection(
    state: AiWorldBookUiState,
    viewModel: AiWorldBookViewModel,
    readOnly: Boolean,
    onImportEntries: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    var deleteEntryConfirm by remember { mutableStateOf<AiWorldBookEntry?>(null) }
    if (state.editingExistingId != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Lore Entries", style = MaterialTheme.typography.titleSmall)
            if (!readOnly) {
                Row {
                    TextButton(onClick = onImportEntries) {
                        Text(stringResource(R.string.ai_world_book_import_entries_into))
                    }
                    TooltipIconButton(
                        onClick = { viewModel.onIntent(AiWorldBookIntent.StartEntryEdit(null)) },
                        label = stringResource(R.string.add),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
        state.entries.forEach { entry ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = if (entry.enabled) colorScheme.surfaceVariant
                else colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.name.ifBlank { entry.keys.take(40) },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (entry.keys.isNotBlank()) {
                            Text(
                                entry.keys,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (entry.content.isNotBlank()) {
                            Text(
                                entry.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (entry.constant) {
                            Text(
                                stringResource(R.string.ai_world_book_entry_constant),
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.primary,
                            )
                        }
                        if (entry.position == AiWorldBookEntry.POSITION_IN_CHAT) {
                            Text(
                                stringResource(
                                    R.string.ai_world_book_entry_at_depth_badge,
                                    entry.insertDepth,
                                    entry.role,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.tertiary,
                            )
                        }
                    }
                    if (!readOnly) {
                        Switch(
                            checked = entry.enabled,
                            onCheckedChange = {
                                viewModel.onIntent(AiWorldBookIntent.ToggleEntryEnabled(entry.id))
                            },
                        )
                        IconButton(
                            onClick = { deleteEntryConfirm = entry },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.ai_world_book_entry_delete_confirm),
                                tint = colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        TooltipIconButton(
                            onClick = { viewModel.onIntent(AiWorldBookIntent.StartEntryEdit(entry)) },
                            label = stringResource(R.string.edit),
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
        }
    } else if (!readOnly) {
        Text(
            stringResource(R.string.ai_world_book_import_need_save_first),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
        )
    }

    if (state.pendingEntryDrafts.isNotEmpty()) {
        Text(
            stringResource(R.string.ai_world_book_import_entries, state.pendingEntryDrafts.size) +
                " (save to keep)",
            style = MaterialTheme.typography.titleSmall,
        )
        state.pendingEntryDrafts.forEach { draft ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = colorScheme.tertiaryContainer.copy(alpha = 0.5f),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(draft.name.ifBlank { draft.keys.take(40) }, style = MaterialTheme.typography.bodyMedium)
                    if (draft.keys.isNotBlank()) {
                        Text(draft.keys, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    deleteEntryConfirm?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteEntryConfirm = null },
            title = { Text(stringResource(R.string.ai_world_book_entry_delete_confirm)) },
            text = { Text(entry.name.ifBlank { entry.keys.take(40) }) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onIntent(AiWorldBookIntent.DeleteEntry(entry.id))
                    deleteEntryConfirm = null
                }) { Text(stringResource(R.string.ok), color = colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteEntryConfirm = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookPickerSheet(
    books: List<Book>,
    onSelect: (Book) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberLegadoBottomSheetState()
    val colorScheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).legadoSheetInsets()) {
            Text(
                stringResource(R.string.ai_world_book_select_book),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(400.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(books, key = { it.bookUrl }) { book ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.surface,
                        onClick = { onSelect(book) },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(book.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${book.author} · ${book.durChapterIndex + 1}/${book.totalChapterNum} chapters",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
