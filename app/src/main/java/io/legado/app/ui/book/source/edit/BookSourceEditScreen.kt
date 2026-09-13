package io.legado.app.ui.book.source.edit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.RoundDropdownMenu
import io.legado.app.ui.common.compose.RoundDropdownMenuItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSourceEditScreen(
    state: BookSourceEditUiState,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onIntent: (BookSourceEditIntent) -> Unit,
) {
    BackHandler { onIntent(BookSourceEditIntent.RequestBack) }
    val tabs = BookSourceEditTab.entries
    val pagerState = rememberPagerState(initialPage = state.selectedTab.ordinal) { tabs.size }
    val pagerScope = rememberCoroutineScope()

    LaunchedEffect(state.selectedTab) {
        if (pagerState.currentPage != state.selectedTab.ordinal) {
            pagerState.animateScrollToPage(state.selectedTab.ordinal)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            tabs.getOrNull(page)?.let { onIntent(BookSourceEditIntent.SelectTab(it)) }
        }
    }

    val onSurfaceColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onPrimary
    val containerColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.edit_book_source), color = onSurfaceColor) },
                    navigationIcon = {
                        IconButton(onClick = { onIntent(BookSourceEditIntent.RequestBack) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = onSurfaceColor,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { onIntent(BookSourceEditIntent.SaveAndDebug) }) {
                            Icon(
                                Icons.Default.BugReport,
                                contentDescription = stringResource(R.string.debug_source),
                                tint = onSurfaceColor,
                            )
                        }
                        Box {
                            IconButton(onClick = { onMenuExpandedChange(true) }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more_menu),
                                    tint = onSurfaceColor,
                                )
                            }
                            BookSourceEditMenu(
                                expanded = menuExpanded,
                                onExpandedChange = onMenuExpandedChange,
                                state = state,
                                onIntent = onIntent,
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
                PrimaryScrollableTabRow(
                    selectedTabIndex = state.selectedTab.ordinal,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = containerColor,
                    contentColor = onSurfaceColor,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = state.selectedTab == tab,
                            onClick = {
                                onIntent(BookSourceEditIntent.SelectTab(tab))
                                pagerScope.launch { pagerState.animateScrollToPage(index) }
                            },
                            text = { Text(stringResource(tab.titleRes)) },
                        )
                    }
                }
            }
        },
        // Floating action button for saving the book source
        floatingActionButton = {
            FloatingActionButton(onClick = { onIntent(BookSourceEditIntent.Save) }) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = stringResource(R.string.action_save),
                )
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val tab = tabs[page]
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tab == BookSourceEditTab.Base) {
                    item(key = "options", contentType = "options") {
                        BookSourceOptions(state, onIntent)
                    }
                }
                items(
                    items = state.fieldGroups[tab].orEmpty(),
                    key = { it.path },
                    contentType = { "field" },
                ) { field ->
                    SourceEditField(
                        field = field,
                        modifier = Modifier.fillMaxWidth(),
                        onValueChange = { path, value ->
                            onIntent(BookSourceEditIntent.UpdateField(path, value))
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SourceEditField(
    field: BookSourceEditFieldUi,
    modifier: Modifier = Modifier,
    onValueChange: (String, String) -> Unit,
) {
    val title = field.labelRes?.let { stringResource(it) } ?: field.label.orEmpty()
    var text by rememberSaveable(field.path) { mutableStateOf(field.value) }
    LaunchedEffect(field.value) {
        if (text != field.value) text = field.value
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { newValue ->
                text = newValue
                onValueChange(field.path, newValue)
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 1,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
            ),
        )
    }
}

@Composable
private fun BookSourceOptions(
    state: BookSourceEditUiState,
    onIntent: (BookSourceEditIntent) -> Unit,
) {
    val sourceTypes = stringArrayResource(R.array.book_type)
    var typeMenuExpanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                SourceEditOptionCard(
                    title = stringResource(R.string.book_type),
                    subtitle = sourceTypes.getOrNull(state.bookSourceType),
                    onClick = { typeMenuExpanded = true },
                )
                RoundDropdownMenu(
                    expanded = typeMenuExpanded,
                    onDismissRequest = { typeMenuExpanded = false },
                ) {
                    sourceTypes.forEachIndexed { index, title ->
                        RoundDropdownMenuItem(
                            text = title,
                            isSelected = state.bookSourceType == index,
                            onClick = {
                                typeMenuExpanded = false
                                onIntent(BookSourceEditIntent.SetSourceType(index))
                            },
                        )
                    }
                }
            }
            SourceEditOptionCard(
                title = stringResource(R.string.is_enable),
                checked = state.enabled,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(BookSourceEditIntent.SetEnabled(!state.enabled)) },
            )
            SourceEditOptionCard(
                title = stringResource(R.string.discovery),
                checked = state.enabledExplore,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(BookSourceEditIntent.SetExploreEnabled(!state.enabledExplore)) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceEditOptionCard(
                title = stringResource(R.string.auto_save_cookie),
                checked = state.enabledCookieJar,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(BookSourceEditIntent.SetCookieJarEnabled(!state.enabledCookieJar)) },
            )
        }
    }
}

@Composable
fun SourceEditOptionCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    checked: Boolean? = null,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            )
            AnimatedVisibility(visible = !subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            AnimatedVisibility(visible = checked == true) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun BookSourceEditMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    state: BookSourceEditUiState,
    onIntent: (BookSourceEditIntent) -> Unit,
) {
    fun click(intent: BookSourceEditIntent) {
        onExpandedChange(false); onIntent(intent)
    }
    RoundDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
        RoundDropdownMenuItem(
            text = stringResource(R.string.login),
            onClick = { click(BookSourceEditIntent.SaveAndLogin) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.search),
            onClick = { click(BookSourceEditIntent.SaveAndSearch) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.cookie),
            onClick = { click(BookSourceEditIntent.ClearCookie) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.auto_complete),
            isSelected = state.autoComplete,
            onClick = { click(BookSourceEditIntent.ToggleAutoComplete) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.copy_source),
            onClick = { click(BookSourceEditIntent.Copy) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.paste_source),
            onClick = { click(BookSourceEditIntent.Paste) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.set_source_variable),
            onClick = { click(BookSourceEditIntent.SaveAndSetVariable) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.str_share),
            onClick = { click(BookSourceEditIntent.Share) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.book_source_ai_version_history),
            onClick = { click(BookSourceEditIntent.ShowVersionHistory) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.log),
            onClick = { click(BookSourceEditIntent.ShowLog) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.help),
            onClick = { click(BookSourceEditIntent.ShowHelp) })
    }
}
