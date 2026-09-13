package io.legado.app.ui.book.source.debug

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.model.Debug
import io.legado.app.ui.common.compose.ModalLegadoBottomSheet
import io.legado.app.utils.LogUtils
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSourceDebugScreen(
    state: BookSourceDebugUiState,
    onIntent: (BookSourceDebugIntent) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val listState = rememberLazyListState()
    val visibleEntries = state.entries.filter { entry ->
        when (state.filter) {
            BookSourceDebugFilter.All -> true
            BookSourceDebugFilter.Messages ->
                entry.kind == Debug.EventKind.Message || entry.kind == Debug.EventKind.Completed
            BookSourceDebugFilter.Sources -> entry.kind.isSourcePayload
            BookSourceDebugFilter.Errors -> entry.kind == Debug.EventKind.Error
        }
    }
    LaunchedEffect(visibleEntries.size, state.status) {
        if (state.status == BookSourceDebugStatus.Running && visibleEntries.isNotEmpty()) {
            listState.animateScrollToItem(visibleEntries.lastIndex)
        }
    }

    val onSurfaceColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onPrimary
    val containerColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_book_source), color = onSurfaceColor) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = onSurfaceColor,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onIntent(BookSourceDebugIntent.Clear) }) {
                        Icon(
                            Icons.Default.ClearAll,
                            contentDescription = stringResource(R.string.debug_clear_log),
                            tint = onSurfaceColor,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = containerColor,
                    titleContentColor = onSurfaceColor,
                    navigationIconContentColor = onSurfaceColor,
                    actionIconContentColor = onSurfaceColor,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    onIntent(
                        if (state.status == BookSourceDebugStatus.Running) BookSourceDebugIntent.Stop
                        else BookSourceDebugIntent.Start
                    )
                },
            ) {
                Icon(
                    imageVector = if (state.status == BookSourceDebugStatus.Running) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (state.status == BookSourceDebugStatus.Running) {
                        stringResource(R.string.debug_stop)
                    } else {
                        stringResource(R.string.debug_start)
                    },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "controls", contentType = "controls") {
                DebugControls(state, onIntent)
            }
            item(key = "filters", contentType = "filters") {
                DebugChipRow {
                    items(BookSourceDebugFilter.entries, key = { it.name }) { filter ->
                        FilterChip(
                            label = { Text(stringResource(filter.titleRes)) },
                            selected = state.filter == filter,
                            onClick = { onIntent(BookSourceDebugIntent.SelectFilter(filter)) },
                        )
                    }
                }
            }
            if (visibleEntries.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = if (state.status == BookSourceDebugStatus.Running) {
                            stringResource(R.string.debug_waiting_log)
                        } else {
                            stringResource(R.string.debug_input_to_start)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .padding(top = 100.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            items(visibleEntries, key = { it.id }, contentType = { it.kind }) { entry ->
                DebugEntryCard(
                    entry = entry,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onIntent(BookSourceDebugIntent.ShowEntry(entry.id)) },
                )
            }
        }
    }
    val selectedEntry = state.entries.firstOrNull { it.id == state.selectedEntryId }
    ModalLegadoBottomSheet(
        show = selectedEntry != null,
        onDismissRequest = { onIntent(BookSourceDebugIntent.DismissEntry) },
        title = selectedEntry?.kind?.let { stringResource(it.titleRes) },
    ) {
        if (selectedEntry != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = selectedEntry.message,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun DebugControls(
    state: BookSourceDebugUiState,
    onIntent: (BookSourceDebugIntent) -> Unit,
) {
    val visibleExamples = remember(state.examples, state.target) {
        state.examples.filter { it.target == state.target }.take(8)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.sourceName,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            DebugChipRow {
                items(BookSourceDebugTarget.entries, key = { it.name }) { target ->
                    FilterChip(
                        label = { Text(stringResource(target.titleRes)) },
                        selected = state.target == target,
                        onClick = { onIntent(BookSourceDebugIntent.SelectTarget(target)) },
                    )
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = { onIntent(BookSourceDebugIntent.SetQuery(it)) },
                label = { Text(stringResource(state.target.hintRes)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.status != BookSourceDebugStatus.Loading,
                maxLines = 4,
            )
            if (visibleExamples.isNotEmpty()) {
                DebugChipRow {
                    items(visibleExamples, key = { "${it.target}:${it.value}" }) { example ->
                        FilterChip(
                            label = { Text(example.title) },
                            selected = state.target == example.target && state.query == example.value,
                            onClick = { onIntent(BookSourceDebugIntent.UseExample(example)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DebugChipRow(content: LazyListScope.() -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun DebugEntryCard(
    entry: BookSourceDebugEntryUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val containerColor = when (entry.kind) {
        Debug.EventKind.Message -> MaterialTheme.colorScheme.surfaceContainerHigh
        Debug.EventKind.SearchSource,
        Debug.EventKind.InfoSource,
        Debug.EventKind.TocSource,
        Debug.EventKind.ContentSource -> MaterialTheme.colorScheme.primaryContainer
        Debug.EventKind.Error -> MaterialTheme.colorScheme.errorContainer
        Debug.EventKind.Completed -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val accentColor = when (entry.kind) {
        Debug.EventKind.Message -> MaterialTheme.colorScheme.onSurfaceVariant
        Debug.EventKind.SearchSource,
        Debug.EventKind.InfoSource,
        Debug.EventKind.TocSource,
        Debug.EventKind.ContentSource -> MaterialTheme.colorScheme.onPrimaryContainer
        Debug.EventKind.Error -> MaterialTheme.colorScheme.onErrorContainer
        Debug.EventKind.Completed -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = accentColor,
        ),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(entry.kind.titleRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                )
                Text(
                    text = "+%.3fs".format(entry.elapsedMillis / 1000.0),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = entry.message,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = LogUtils.logTimeFormat.format(Date(entry.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val Debug.EventKind.titleRes: Int
    get() = when (this) {
        Debug.EventKind.Message -> R.string.debug_kind_message
        Debug.EventKind.SearchSource -> R.string.debug_kind_search
        Debug.EventKind.InfoSource -> R.string.debug_kind_info
        Debug.EventKind.TocSource -> R.string.debug_kind_toc
        Debug.EventKind.ContentSource -> R.string.debug_kind_content
        Debug.EventKind.Error -> R.string.debug_kind_error
        Debug.EventKind.Completed -> R.string.debug_kind_completed
    }

private val BookSourceDebugTarget.titleRes: Int
    get() = when (this) {
        BookSourceDebugTarget.Search -> R.string.debug_target_search
        BookSourceDebugTarget.Explore -> R.string.debug_target_explore
        BookSourceDebugTarget.Info -> R.string.debug_target_info
        BookSourceDebugTarget.Toc -> R.string.debug_target_toc
        BookSourceDebugTarget.Content -> R.string.debug_target_content
    }

private val BookSourceDebugTarget.hintRes: Int
    get() = when (this) {
        BookSourceDebugTarget.Search -> R.string.debug_hint_search
        BookSourceDebugTarget.Explore -> R.string.debug_hint_explore
        BookSourceDebugTarget.Info -> R.string.debug_hint_info
        BookSourceDebugTarget.Toc -> R.string.debug_hint_toc
        BookSourceDebugTarget.Content -> R.string.debug_hint_content
    }

private val BookSourceDebugFilter.titleRes: Int
    get() = when (this) {
        BookSourceDebugFilter.All -> R.string.debug_filter_all
        BookSourceDebugFilter.Messages -> R.string.debug_filter_message
        BookSourceDebugFilter.Sources -> R.string.debug_filter_source
        BookSourceDebugFilter.Errors -> R.string.debug_filter_error
    }
