package io.legado.app.ui.dict.rule

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.base.BaseRuleEvent
import io.legado.app.data.entities.DictRule
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.ModalLegadoBottomSheet
import io.legado.app.ui.common.compose.RoundDropdownMenu
import io.legado.app.ui.common.compose.RoundDropdownMenuItem
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.utils.showHelp
import io.legado.app.utils.toastOnUi
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictRuleRouteScreen(
    viewModel: DictRuleViewModel = koinViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    DictRuleScreen(state = uiState, importState = importState, events = viewModel.events, onIntent = viewModel::onIntent, onPasteRule = viewModel::pasteRule, onBackClick = onBackClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictRuleScreen(
    state: DictRuleUiState,
    importState: BaseImportUiState<DictRule>,
    events: kotlinx.coroutines.flow.Flow<BaseRuleEvent>,
    onIntent: (DictRuleIntent) -> Unit,
    onPasteRule: () -> DictRule?,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val rules = state.items
    val selectedIds = state.selectedIds
    val inSelectionMode = selectedIds.isNotEmpty()

    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showEditSheet by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<DictRule?>(null) }
    var showDeleteDialog by remember { mutableStateOf<DictRule?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showUrlInput by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                onIntent(DictRuleIntent.ImportSource(stream.reader().readText()))
            }
        }
    }

    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { onIntent(DictRuleIntent.ExportSelection(it)) }
    }

    val qrLauncher = rememberLauncherForActivityResult(QrCodeResult()) { result ->
        result?.let { onIntent(DictRuleIntent.ImportSource(it)) }
    }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        onIntent(DictRuleIntent.MoveItem(from.index, to.index))
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    val onSurfaceColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onPrimary
    val containerColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) { events.collect { event -> when (event) { is BaseRuleEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message) } } }
    LaunchedEffect(reorderableState.isAnyItemDragging) { if (!reorderableState.isAnyItemDragging) onIntent(DictRuleIntent.SaveSortOrder) }

    // URL input dialog
    if (showUrlInput) {
        AlertDialog(
            onDismissRequest = { showUrlInput = false },
            title = { Text(stringResource(R.string.import_on_line)) },
            text = { OutlinedTextField(value = urlInput, onValueChange = { urlInput = it }, label = { Text("URL") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { showUrlInput = false; if (urlInput.isNotBlank()) { onIntent(DictRuleIntent.ImportSource(urlInput)); urlInput = "" } }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { showUrlInput = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dict_rule), color = onSurfaceColor) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = onSurfaceColor) } },
                actions = {
                    if (inSelectionMode) {
                        IconButton(onClick = { onIntent(DictRuleIntent.ClearSelection) }) { Icon(Icons.Default.Close, contentDescription = "Clear", tint = onSurfaceColor) }
                        IconButton(onClick = { onIntent(DictRuleIntent.SelectAll) }) { Icon(Icons.Default.Check, contentDescription = "Select all", tint = onSurfaceColor) }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = onSurfaceColor) }
                        RoundDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) { dismiss ->
                            RoundDropdownMenuItem(text = stringResource(R.string.import_local), onClick = { dismiss(); filePicker.launch(arrayOf("application/json", "text/*")) })
                            RoundDropdownMenuItem(text = stringResource(R.string.import_on_line), onClick = { dismiss(); showUrlInput = true })
                            RoundDropdownMenuItem(text = stringResource(R.string.import_by_qr_code), onClick = { dismiss(); qrLauncher.launch(null) })
                            RoundDropdownMenuItem(text = stringResource(R.string.import_default_rule), onClick = { dismiss(); onIntent(DictRuleIntent.ImportDefault) })
                            RoundDropdownMenuItem(text = stringResource(R.string.help), onClick = { dismiss(); (context as? AppCompatActivity)?.showHelp("dictRuleHelp") })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor, titleContentColor = onSurfaceColor, navigationIconContentColor = onSurfaceColor, actionIconContentColor = onSurfaceColor)
            )
        },
        floatingActionButton = { if (!inSelectionMode) { FloatingActionButton(onClick = { editingRule = null; showEditSheet = true }) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add)) } } },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (inSelectionMode) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onIntent(DictRuleIntent.EnableSelection); onIntent(DictRuleIntent.ClearSelection) }) { Text(stringResource(R.string.enable_selection)) }
                    TextButton(onClick = { onIntent(DictRuleIntent.DisableSelection); onIntent(DictRuleIntent.ClearSelection) }) { Text(stringResource(R.string.disable_selection)) }
                    TextButton(onClick = { exportFile.launch("exportDictRule.json") }) { Text(stringResource(R.string.export_selection)) }
                    TextButton(onClick = { onIntent(DictRuleIntent.SetSelection(selectedIds)); onIntent(DictRuleIntent.DeleteSelection) }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(rules, key = { it.id }) { item ->
                    ReorderableItem(reorderableState, key = item.id) { _ ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = if (selectedIds.contains(item.id)) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                            onClick = { if (inSelectionMode) onIntent(DictRuleIntent.ToggleSelection(item.id)) }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (inSelectionMode) Checkbox(checked = selectedIds.contains(item.id), onCheckedChange = { onIntent(DictRuleIntent.ToggleSelection(item.id)) })
                                Column(modifier = Modifier.weight(1f)) { Text(text = item.id, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                Switch(checked = item.isEnabled, onCheckedChange = { enabled -> onIntent(DictRuleIntent.SetRuleEnabled(item.rule, enabled)) })
                                IconButton(onClick = { editingRule = item.rule; showEditSheet = true }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                                IconButton(onClick = { showDeleteDialog = item.rule }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                            }
                        }
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { rule ->
        AlertDialog(onDismissRequest = { showDeleteDialog = null }, title = { Text(stringResource(R.string.delete)) }, text = { Text(stringResource(R.string.sure_del)) },
            confirmButton = { TextButton(onClick = { onIntent(DictRuleIntent.DeleteRule(rule)); showDeleteDialog = null }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text(stringResource(R.string.cancel)) } })
    }

    ModalLegadoBottomSheet(show = showEditSheet, onDismissRequest = { showEditSheet = false; editingRule = null }, title = stringResource(R.string.dict_rule)) {
        var name by remember(editingRule) { mutableStateOf(editingRule?.name ?: "") }
        var urlRule by remember(editingRule) { mutableStateOf(editingRule?.urlRule ?: "") }
        var showRule by remember(editingRule) { mutableStateOf(editingRule?.showRule ?: "") }
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = urlRule, onValueChange = { urlRule = it }, label = { Text(stringResource(R.string.url_rule)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = showRule, onValueChange = { showRule = it }, label = { Text(stringResource(R.string.show_rule)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (editingRule != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onIntent(DictRuleIntent.CopyRule(editingRule!!)) }) { Text(stringResource(R.string.copy_rule)) }
                    TextButton(onClick = {
                        onPasteRule()?.let { pasted ->
                            name = pasted.name
                            urlRule = pasted.urlRule
                            showRule = pasted.showRule ?: ""
                        }
                    }) { Text(stringResource(R.string.paste_rule)) }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showEditSheet = false; editingRule = null }) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = {
                    onIntent(DictRuleIntent.SaveRule(rule = (editingRule ?: DictRule(name = name)).copy(name = name, urlRule = urlRule, showRule = showRule), isNew = editingRule == null, originalName = editingRule?.name))
                    showEditSheet = false; editingRule = null
                }) { Text(stringResource(R.string.ok)) }
            }
        }
    }
}
