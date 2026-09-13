package io.legado.app.ui.replace

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.base.BaseRuleEvent
import io.legado.app.data.entities.ReplaceRule
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
fun ReplaceRuleRouteScreen(
    viewModel: ReplaceRuleViewModel = koinViewModel(),
    onBackClick: () -> Unit,
    onNavigateToEdit: (ReplaceEditRoute) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val groups by viewModel.allGroups.collectAsStateWithLifecycle()
    ReplaceRuleScreen(state = uiState, importState = importState, events = viewModel.events, groups = groups, onIntent = viewModel::onIntent, onPasteRule = viewModel::pasteRule, onBackClick = onBackClick, onNavigateToEdit = onNavigateToEdit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplaceRuleScreen(
    state: ReplaceRuleUiState,
    importState: BaseImportUiState<ReplaceRule>,
    events: kotlinx.coroutines.flow.Flow<BaseRuleEvent>,
    groups: List<String>,
    onIntent: (ReplaceRuleIntent) -> Unit,
    onPasteRule: () -> ReplaceRule?,
    onBackClick: () -> Unit,
    onNavigateToEdit: (ReplaceEditRoute) -> Unit,
) {
    val context = LocalContext.current
    val rules = state.items
    val selectedIds = state.selectedIds
    val inSelectionMode = selectedIds.isNotEmpty()

    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showDeleteDialog by remember { mutableStateOf<ReplaceRule?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var showUrlInput by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var showGroupSheet by remember { mutableStateOf(false) }
    var itemMenuRule by remember { mutableStateOf<ReplaceRule?>(null) }

    val tabItems = remember(groups) { listOf("全部") + groups }
    val selectedTabIndex = state.selectedGroup?.let(tabItems::indexOf)?.takeIf { it >= 0 } ?: 0

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { context.contentResolver.openInputStream(it)?.use { s -> onIntent(ReplaceRuleIntent.ImportSource(s.reader().readText())) } }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { onIntent(ReplaceRuleIntent.ExportSelection(it)) }
    }

    val qrLauncher = rememberLauncherForActivityResult(QrCodeResult()) { result ->
        result?.let { onIntent(ReplaceRuleIntent.ImportSource(it)) }
    }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        onIntent(ReplaceRuleIntent.MoveItem(from.index, to.index))
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    val onSurfaceColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onPrimary
    val containerColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) { events.collect { event -> when (event) { is BaseRuleEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message) } } }
    LaunchedEffect(reorderableState.isAnyItemDragging) { if (!reorderableState.isAnyItemDragging) onIntent(ReplaceRuleIntent.SaveSortOrder) }
    LaunchedEffect(searchText) { onIntent(ReplaceRuleIntent.UpdateSearchQuery(searchText)) }

    // URL input dialog
    if (showUrlInput) {
        AlertDialog(
            onDismissRequest = { showUrlInput = false }, title = { Text(stringResource(R.string.import_on_line)) },
            text = { OutlinedTextField(value = urlInput, onValueChange = { urlInput = it }, label = { Text("URL") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { showUrlInput = false; if (urlInput.isNotBlank()) { onIntent(ReplaceRuleIntent.ImportSource(urlInput)); urlInput = "" } }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { showUrlInput = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (showSearch) {
                            Box(modifier = Modifier.fillMaxWidth().height(36.dp).background(onSurfaceColor.copy(alpha = 0.08f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.CenterStart) {
                                BasicTextField(
                                    value = searchText, onValueChange = { searchText = it }, singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = onSurfaceColor), cursorBrush = SolidColor(onSurfaceColor),
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { innerTextField ->
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                                            Icon(Icons.Filled.Search, contentDescription = null, tint = onSurfaceColor.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Box(Modifier.weight(1f)) {
                                                if (searchText.isBlank()) Text(stringResource(R.string.replace_purify_search), style = MaterialTheme.typography.bodyMedium, color = onSurfaceColor.copy(alpha = 0.5f))
                                                innerTextField()
                                            }
                                        }
                                    }
                                )
                            }
                        } else { Text(stringResource(R.string.replace_purify), color = onSurfaceColor) }
                    },
                    navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = onSurfaceColor) } },
                    actions = {
                        if (!inSelectionMode && !showSearch) {
                            IconButton(onClick = { showSearch = true; onIntent(ReplaceRuleIntent.SetSearchMode(true)) }) { Icon(Icons.Filled.Search, contentDescription = "Search", tint = onSurfaceColor) }
                            Box {
                                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = onSurfaceColor) }
                                RoundDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) { dismiss ->
                                    RoundDropdownMenuItem(text = stringResource(R.string.menu_action_group), onClick = { dismiss(); showGroupSheet = true })
                                    RoundDropdownMenuItem(text = stringResource(R.string.import_local), onClick = { dismiss(); filePicker.launch(arrayOf("application/json", "text/*")) })
                                    RoundDropdownMenuItem(text = stringResource(R.string.import_on_line), onClick = { dismiss(); showUrlInput = true })
                                    RoundDropdownMenuItem(text = stringResource(R.string.import_by_qr_code), onClick = { dismiss(); qrLauncher.launch(null) })
                                    RoundDropdownMenuItem(text = stringResource(R.string.help), onClick = { dismiss(); (context as? AppCompatActivity)?.showHelp("replaceRuleHelp") })
                                }
                            }
                        }
                        if (inSelectionMode) {
                            IconButton(onClick = { onIntent(ReplaceRuleIntent.ClearSelection) }) { Icon(Icons.Default.Close, contentDescription = "Clear", tint = onSurfaceColor) }
                            IconButton(onClick = { onIntent(ReplaceRuleIntent.SelectAll) }) { Icon(Icons.Default.Check, contentDescription = "Select all", tint = onSurfaceColor) }
                        }
                        if (showSearch) {
                            IconButton(onClick = { showSearch = false; searchText = ""; onIntent(ReplaceRuleIntent.SetSearchMode(false)) }) { Icon(Icons.Default.Close, contentDescription = "Close", tint = onSurfaceColor) }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor, titleContentColor = onSurfaceColor, navigationIconContentColor = onSurfaceColor, actionIconContentColor = onSurfaceColor)
                )
                if (tabItems.size > 1 && !showSearch) {
                    LazyRow(modifier = Modifier.fillMaxWidth().background(containerColor).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(tabItems.size) { index -> FilterChip(selected = index == selectedTabIndex, onClick = { onIntent(ReplaceRuleIntent.SetGroup(tabItems[index])) }, label = { Text(tabItems[index]) }) }
                    }
                }
            }
        },
        floatingActionButton = { if (!inSelectionMode) { FloatingActionButton(onClick = { onNavigateToEdit(ReplaceEditRoute(id = -1)) }) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add)) } } },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Selection actions bar
            if (inSelectionMode) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onIntent(ReplaceRuleIntent.EnableSelection); onIntent(ReplaceRuleIntent.ClearSelection) }) { Text(stringResource(R.string.enable_selection)) }
                    TextButton(onClick = { onIntent(ReplaceRuleIntent.DisableSelection); onIntent(ReplaceRuleIntent.ClearSelection) }) { Text(stringResource(R.string.disable_selection)) }
                    TextButton(onClick = { onIntent(ReplaceRuleIntent.TopSelectByIds(selectedIds)) }) { Text(stringResource(R.string.selection_to_top)) }
                    TextButton(onClick = { onIntent(ReplaceRuleIntent.BottomSelectByIds(selectedIds)) }) { Text(stringResource(R.string.selection_to_bottom)) }
                    TextButton(onClick = { exportFile.launch("exportReplaceRule.json") }) { Text(stringResource(R.string.export_selection)) }
                    TextButton(onClick = { onIntent(ReplaceRuleIntent.SetSelection(selectedIds)); onIntent(ReplaceRuleIntent.DeleteSelection) }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(rules, key = { it.id }) { item ->
                    ReorderableItem(reorderableState, key = item.id) { _ ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = if (selectedIds.contains(item.id)) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                            onClick = { if (inSelectionMode) onIntent(ReplaceRuleIntent.ToggleSelection(item.id)) }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (inSelectionMode) Checkbox(checked = selectedIds.contains(item.id), onCheckedChange = { onIntent(ReplaceRuleIntent.ToggleSelection(item.id)) })
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (!item.group.isNullOrBlank()) Text(text = item.group, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Switch(checked = item.isEnabled, onCheckedChange = { enabled -> onIntent(ReplaceRuleIntent.SetRuleEnabled(item.id, enabled)) })
                                Box {
                                    IconButton(onClick = { itemMenuRule = item.toEntity() }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                                    itemMenuRule?.let { menuRule ->
                                        RoundDropdownMenu(expanded = menuRule.id == item.id, onDismissRequest = { itemMenuRule = null }) { dismiss ->
                                            RoundDropdownMenuItem(text = stringResource(R.string.edit), onClick = { dismiss(); itemMenuRule = null; onNavigateToEdit(ReplaceEditRoute(id = item.id)) })
                                            RoundDropdownMenuItem(text = stringResource(R.string.to_top), onClick = { dismiss(); itemMenuRule = null; onIntent(ReplaceRuleIntent.ToTop(item.toEntity())) })
                                            RoundDropdownMenuItem(text = stringResource(R.string.to_bottom), onClick = { dismiss(); itemMenuRule = null; onIntent(ReplaceRuleIntent.ToBottom(item.toEntity())) })
                                            RoundDropdownMenuItem(text = stringResource(R.string.delete), onClick = { dismiss(); itemMenuRule = null; showDeleteDialog = item.toEntity() })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation
    showDeleteDialog?.let { rule ->
        AlertDialog(onDismissRequest = { showDeleteDialog = null }, title = { Text(stringResource(R.string.delete)) }, text = { Text(stringResource(R.string.sure_del)) },
            confirmButton = { TextButton(onClick = { onIntent(ReplaceRuleIntent.DeleteRule(rule)); showDeleteDialog = null }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text(stringResource(R.string.cancel)) } })
    }

    // Group management sheet
    ModalLegadoBottomSheet(show = showGroupSheet, onDismissRequest = { showGroupSheet = false }, title = stringResource(R.string.group_manage)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { group ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(group, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onIntent(ReplaceRuleIntent.DeleteGroup(group)) }) { Icon(Icons.Default.Delete, contentDescription = "Delete group") }
                }
            }
        }
    }
}
