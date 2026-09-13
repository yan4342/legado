package io.legado.app.ui.rss.source.manage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.ModalLegadoBottomSheet
import io.legado.app.ui.common.compose.RoundDropdownMenu
import io.legado.app.ui.common.compose.RoundDropdownMenuItem
import io.legado.app.ui.qrcode.QrCodeResult
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssSourceRouteScreen(
    viewModel: RssSourceViewModel = koinViewModel(),
    onBackClick: () -> Unit,
    onAddSource: () -> Unit,
    onEditSource: (String) -> Unit,
    onDebugSource: (String) -> Unit,
    onImportLocal: () -> Unit,
    onImportOnline: (String) -> Unit,
    onExportSelected: (String) -> Unit,
    onShareSelected: (String) -> Unit,
    onShowHelp: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RssSourceScreen(
        state = state,
        onIntent = viewModel::onIntent,
        effects = viewModel.effects,
        onBackClick = onBackClick,
        onAddSource = onAddSource,
        onEditSource = onEditSource,
        onDebugSource = onDebugSource,
        onImportLocal = onImportLocal,
        onImportOnline = onImportOnline,
        onExportSelected = onExportSelected,
        onShareSelected = onShareSelected,
        onShowHelp = onShowHelp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssSourceScreen(
    state: RssSourceUiState,
    onIntent: (RssSourceIntent) -> Unit,
    effects: kotlinx.coroutines.flow.Flow<RssSourceEffect>,
    onBackClick: () -> Unit,
    onAddSource: () -> Unit,
    onEditSource: (String) -> Unit,
    onDebugSource: (String) -> Unit,
    onImportLocal: () -> Unit,
    onImportOnline: (String) -> Unit,
    onExportSelected: (String) -> Unit,
    onShareSelected: (String) -> Unit,
    onShowHelp: () -> Unit,
) {
    val rules = state.items
    val selectedIds = state.selectedIds
    val inSelectionMode = selectedIds.isNotEmpty()

    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var showUrlInput by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var showGroupSheet by remember { mutableStateOf(false) }
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var groupToEdit by remember { mutableStateOf<String?>(null) }
    var deleteIds by remember { mutableStateOf<Set<String>?>(null) }
    var addGroup by remember { mutableStateOf(false) }
    var removeGroup by remember { mutableStateOf(false) }
    var showGroupFilter by remember { mutableStateOf(false) }
    var selectionMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is RssSourceEffect.Export -> onExportSelected(effect.json)
                is RssSourceEffect.Share -> onShareSelected(effect.json)
            }
        }
    }

    val qrLauncher = rememberLauncherForActivityResult(QrCodeResult()) { result ->
        result?.let { onImportOnline(it) }
    }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        onIntent(RssSourceIntent.MoveItem(from.index, to.index))
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    val onSurfaceColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onPrimary
    val containerColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.primary

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) onIntent(RssSourceIntent.SaveSortOrder)
    }
    LaunchedEffect(searchText) { onIntent(RssSourceIntent.SetSearchQuery(searchText)) }

    if (showUrlInput) {
        AlertDialog(
            onDismissRequest = { showUrlInput = false },
            title = { Text(stringResource(R.string.import_on_line)) },
            text = {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showUrlInput = false
                    if (urlInput.isNotBlank()) {
                        onImportOnline(urlInput); urlInput = ""
                    }
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showUrlInput = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (showSearch) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                                    .background(onSurfaceColor.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                BasicTextField(
                                    value = searchText,
                                    onValueChange = { searchText = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = onSurfaceColor),
                                    cursorBrush = SolidColor(onSurfaceColor),
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { innerTextField ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Search,
                                                contentDescription = null,
                                                tint = onSurfaceColor.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Box(Modifier.weight(1f)) {
                                                if (searchText.isBlank()) {
                                                    Text(
                                                        stringResource(R.string.search_rss_source),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = onSurfaceColor.copy(alpha = 0.5f)
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        }
                                    }
                                )
                            }
                        } else {
                            Column {
                                Text(stringResource(R.string.rss_source), color = onSurfaceColor)
                                if (state.groupFilterName != null) {
                                    Text(
                                        text = state.groupFilterName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = onSurfaceColor.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = onSurfaceColor
                            )
                        }
                    },
                    actions = {
                        if (!inSelectionMode && !showSearch) {
                            IconButton(onClick = { showSearch = true; onIntent(RssSourceIntent.SetSearchMode(true)) }) {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search), tint = onSurfaceColor)
                            }
                            Box {
                                IconButton(onClick = { showGroupFilter = true }) {
                                    Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.menu_action_group), tint = onSurfaceColor)
                                }
                                GroupFilterMenu(
                                    expanded = showGroupFilter,
                                    state = state,
                                    onDismissRequest = { showGroupFilter = false },
                                    onSelect = { value -> showGroupFilter = false; onIntent(RssSourceIntent.SetFilter(value)) }
                                )
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu), tint = onSurfaceColor)
                                }
                                RoundDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) { dismiss ->
                                    RoundDropdownMenuItem(text = stringResource(R.string.group_manage), onClick = { dismiss(); showGroupSheet = true })
                                    RoundDropdownMenuItem(text = stringResource(R.string.add_rss_source), onClick = { dismiss(); onAddSource() })
                                    RoundDropdownMenuItem(text = stringResource(R.string.import_local), onClick = { dismiss(); onImportLocal() })
                                    RoundDropdownMenuItem(text = stringResource(R.string.import_on_line), onClick = { dismiss(); showUrlInput = true })
                                    RoundDropdownMenuItem(text = stringResource(R.string.import_by_qr_code), onClick = { dismiss(); qrLauncher.launch(null) })
                                    RoundDropdownMenuItem(text = stringResource(R.string.import_default_rule), onClick = { dismiss(); onIntent(RssSourceIntent.ImportDefault) })
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    RoundDropdownMenuItem(text = stringResource(R.string.help), onClick = { dismiss(); onShowHelp() })
                                }
                            }
                        }
                        if (inSelectionMode) {
                            IconButton(onClick = { onIntent(RssSourceIntent.SetSelection(emptySet())) }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), tint = onSurfaceColor)
                            }
                            IconButton(onClick = {
                                onIntent(RssSourceIntent.SetSelection(rules.map { it.id }.toSet()))
                            }) {
                                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.select_all), tint = onSurfaceColor)
                            }
                        }
                        if (showSearch) {
                            IconButton(onClick = { showSearch = false; searchText = ""; onIntent(RssSourceIntent.SetSearchMode(false)) }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), tint = onSurfaceColor)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = containerColor,
                        titleContentColor = onSurfaceColor,
                        navigationIconContentColor = onSurfaceColor,
                        actionIconContentColor = onSurfaceColor
                    )
                )
            }
        },
        floatingActionButton = {
            if (!inSelectionMode) {
                FloatingActionButton(onClick = onAddSource) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_rss_source))
                }
            }
        },
        bottomBar = {
            if (inSelectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Checkbox(
                                checked = rules.isNotEmpty() && selectedIds.size == rules.size,
                                onCheckedChange = { checked ->
                                    onIntent(
                                        RssSourceIntent.SetSelection(
                                            if (checked) rules.map { it.id }.toSet() else emptySet()
                                        )
                                    )
                                },
                            )
                            Text(
                                stringResource(R.string.select_all),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        TextButton(onClick = {
                            onIntent(
                                RssSourceIntent.SetSelection(
                                    rules.map { it.id }.toSet() - selectedIds
                                )
                            )
                        }) {
                            Text(stringResource(R.string.revert_selection))
                        }
                        Button(
                            onClick = { deleteIds = selectedIds },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                        Box {
                            IconButton(onClick = { selectionMenuExpanded = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more_menu),
                                )
                            }
                            RoundDropdownMenu(
                                expanded = selectionMenuExpanded,
                                onDismissRequest = { selectionMenuExpanded = false },
                            ) { dismiss ->
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.enable_selection),
                                    onClick = {
                                        dismiss()
                                        onIntent(RssSourceIntent.SetEnabledForSelection(selectedIds, true))
                                    },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.disable_selection),
                                    onClick = {
                                        dismiss()
                                        onIntent(RssSourceIntent.SetEnabledForSelection(selectedIds, false))
                                    },
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.add_group),
                                    onClick = { dismiss(); addGroup = true },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.remove_group),
                                    onClick = { dismiss(); removeGroup = true },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.selection_to_top),
                                    onClick = {
                                        dismiss()
                                        onIntent(RssSourceIntent.MoveToEdge(selectedIds, true))
                                    },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.selection_to_bottom),
                                    onClick = {
                                        dismiss()
                                        onIntent(RssSourceIntent.MoveToEdge(selectedIds, false))
                                    },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.check_selected_interval),
                                    onClick = {
                                        dismiss()
                                        onIntent(RssSourceIntent.CheckSelectedInterval(selectedIds))
                                    },
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.export_selection),
                                    onClick = {
                                        dismiss()
                                        onIntent(RssSourceIntent.ExportSelection(selectedIds))
                                    },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.share_selected_source),
                                    onClick = {
                                        dismiss()
                                        onIntent(RssSourceIntent.ShareSelection(selectedIds))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            rules.forEachIndexed { index, item ->
                item(key = item.id) {
                    ReorderableItem(reorderableState, key = item.id) { isDragging ->
                        val interactionSource = remember { MutableInteractionSource() }
                        RssSourceItem(
                            item = item,
                            selected = item.id in selectedIds,
                            inSelectionMode = inSelectionMode,
                            isDragging = isDragging,
                            interactionSource = interactionSource,
                            dragModifier = Modifier.longPressDraggableHandle(
                                onDragStarted = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                },
                                onDragStopped = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                },
                                interactionSource = interactionSource,
                            ),
                            onToggleSelection = { onIntent(RssSourceIntent.ToggleSelection(item.id)) },
                            onEnabledChange = { enabled -> onIntent(RssSourceIntent.SetEnabled(item.id, enabled)) },
                            onEdit = { onEditSource(item.id) },
                            onMoveToEdge = { toTop -> onIntent(RssSourceIntent.MoveToEdge(setOf(item.id), toTop)) },
                            onDebug = { onDebugSource(item.id) },
                            onDelete = { deleteIds = setOf(item.id) },
                        )
                    }
                }
            }
        }
    }

    deleteIds?.let { ids ->
        AlertDialog(
            onDismissRequest = { deleteIds = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.sure_del)) },
            confirmButton = {
                TextButton(onClick = { onIntent(RssSourceIntent.Delete(ids)); deleteIds = null }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteIds = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (addGroup) {
        GroupInputDialog(
            title = stringResource(R.string.add_group),
            suggestions = state.groups,
            onDismiss = { addGroup = false },
            onConfirm = { group -> addGroup = false; onIntent(RssSourceIntent.AddToGroup(selectedIds, group)) }
        )
    }
    if (removeGroup) {
        GroupInputDialog(
            title = stringResource(R.string.remove_group),
            suggestions = state.groups,
            onDismiss = { removeGroup = false },
            onConfirm = { group -> removeGroup = false; onIntent(RssSourceIntent.RemoveFromGroup(selectedIds, group)) }
        )
    }

    ModalLegadoBottomSheet(
        show = showGroupSheet,
        onDismissRequest = { showGroupSheet = false },
        title = stringResource(R.string.group_manage)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showAddGroupDialog = true }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_group), style = MaterialTheme.typography.bodyLarge)
            }
            state.groups.forEach { group ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(group, modifier = Modifier.weight(1f))
                    IconButton(onClick = { groupToEdit = group }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.group_edit))
                    }
                    IconButton(onClick = { onIntent(RssSourceIntent.DeleteGroup(group)) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            }
        }
    }

    if (showAddGroupDialog) {
        GroupInputDialog(
            title = stringResource(R.string.add_group),
            suggestions = state.groups,
            onDismiss = { showAddGroupDialog = false },
            onConfirm = { group ->
                showAddGroupDialog = false
                onIntent(RssSourceIntent.AddGroup(group))
            }
        )
    }

    groupToEdit?.let { old ->
        GroupInputDialog(
            title = stringResource(R.string.group_edit),
            suggestions = state.groups.filter { it != old },
            initialText = old,
            onDismiss = { groupToEdit = null },
            onConfirm = { new ->
                groupToEdit = null
                onIntent(RssSourceIntent.UpdateGroup(old, new))
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RssSourceItem(
    item: RssSourceItemUi,
    selected: Boolean,
    inSelectionMode: Boolean,
    isDragging: Boolean,
    interactionSource: MutableInteractionSource,
    dragModifier: Modifier,
    onToggleSelection: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onMoveToEdge: (Boolean) -> Unit,
    onDebug: () -> Unit,
    onDelete: () -> Unit,
) {
    var itemMenuExpanded by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(12.dp)
    Card(
        modifier = dragModifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(cardShape)
            .combinedClickable(
                interactionSource = interactionSource,
                onClick = { onToggleSelection() },
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 6.dp else 0.dp
        ),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (inSelectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelection() })
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!item.group.isNullOrBlank()) {
                    Text(text = item.group, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Switch(
                checked = item.enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.padding(end = 6.dp),
            )
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.size(20.dp))
            }
            Box {
                IconButton(
                    onClick = { itemMenuExpanded = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu), modifier = Modifier.size(20.dp))
                }
                RoundDropdownMenu(expanded = itemMenuExpanded, onDismissRequest = { itemMenuExpanded = false }) { dismiss ->
                    RoundDropdownMenuItem(text = stringResource(R.string.to_top), onClick = { dismiss(); itemMenuExpanded = false; onMoveToEdge(true) })
                    RoundDropdownMenuItem(text = stringResource(R.string.to_bottom), onClick = { dismiss(); itemMenuExpanded = false; onMoveToEdge(false) })
                    RoundDropdownMenuItem(text = stringResource(R.string.debug), onClick = { dismiss(); itemMenuExpanded = false; onDebug() })
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.delete),
                        onClick = { dismiss(); itemMenuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupFilterMenu(
    expanded: Boolean,
    state: RssSourceUiState,
    onDismissRequest: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    val defaultOptions = listOf(
        stringResource(R.string.all) to null,
        stringResource(R.string.enabled) to RssSourceViewModel.FILTER_ENABLED,
        stringResource(R.string.disabled) to RssSourceViewModel.FILTER_DISABLED,
        stringResource(R.string.need_login) to RssSourceViewModel.FILTER_LOGIN,
        stringResource(R.string.no_group) to RssSourceViewModel.FILTER_NO_GROUP,
    )
    RoundDropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) { dismiss ->
        defaultOptions.forEach { (label, value) ->
            RoundDropdownMenuItem(
                text = label,
                isSelected = state.activeFilter == value,
                onClick = { dismiss(); onSelect(value) }
            )
        }
        if (state.groups.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            state.groups.forEach { group ->
                val value = "${RssSourceViewModel.PREFIX_GROUP}$group"
                RoundDropdownMenuItem(
                    text = group,
                    isSelected = state.activeFilter == value,
                    onClick = { dismiss(); onSelect(value) }
                )
            }
        }
    }
}

@Composable
private fun GroupInputDialog(
    title: String,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initialText: String = "",
) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (suggestions.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(suggestions) { _, group ->
                            TextButton(onClick = { text = group }) { Text(group) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
