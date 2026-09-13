package io.legado.app.ui.rss.source.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.source.edit.SourceEditField
import io.legado.app.ui.book.source.edit.SourceEditOptionCard
import io.legado.app.ui.common.compose.RoundDropdownMenu
import io.legado.app.ui.common.compose.RoundDropdownMenuItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssSourceEditScreen(
    state: RssSourceEditUiState,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onIntent: (RssSourceEditIntent) -> Unit,
) {
    BackHandler { onIntent(RssSourceEditIntent.RequestBack) }
    val tabs = RssSourceEditTab.entries
    val pagerState = rememberPagerState(initialPage = state.selectedTab.ordinal) { tabs.size }
    val pagerScope = rememberCoroutineScope()

    LaunchedEffect(state.selectedTab) {
        if (pagerState.currentPage != state.selectedTab.ordinal) {
            pagerState.animateScrollToPage(state.selectedTab.ordinal)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            tabs.getOrNull(page)?.let { onIntent(RssSourceEditIntent.SelectTab(it)) }
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
                    title = { Text(stringResource(R.string.rss_source_edit), color = onSurfaceColor) },
                    navigationIcon = {
                        IconButton(onClick = { onIntent(RssSourceEditIntent.RequestBack) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = onSurfaceColor,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { onIntent(RssSourceEditIntent.SaveAndDebug) }) {
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
                            RssEditMenu(
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
                PrimaryTabRow(
                    selectedTabIndex = state.selectedTab.ordinal,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = containerColor,
                    contentColor = onSurfaceColor,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = state.selectedTab == tab,
                            onClick = {
                                onIntent(RssSourceEditIntent.SelectTab(tab))
                                pagerScope.launch { pagerState.animateScrollToPage(index) }
                            },
                            text = { Text(stringResource(tab.titleRes)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onIntent(RssSourceEditIntent.Save) }) {
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
                if (tab == RssSourceEditTab.Base) {
                    item(key = "options", contentType = "options") {
                        RssSourceOptions(state, onIntent)
                    }
                }
                if (tab == RssSourceEditTab.WebView) {
                    item(key = "webviewOptions", contentType = "webviewOptions") {
                        RssWebViewOptions(state, onIntent)
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
                            onIntent(RssSourceEditIntent.UpdateField(path, value))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RssSourceOptions(
    state: RssSourceEditUiState,
    onIntent: (RssSourceEditIntent) -> Unit,
) {
    val layoutTypes = stringArrayResource(R.array.layout_type)
    var styleMenuExpanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceEditOptionCard(
                title = stringResource(R.string.is_enable),
                checked = state.enabled,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(RssSourceEditIntent.SetEnabled(!state.enabled)) },
            )
            SourceEditOptionCard(
                title = stringResource(R.string.single_url),
                checked = state.singleUrl,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(RssSourceEditIntent.SetSingleUrl(!state.singleUrl)) },
            )
            SourceEditOptionCard(
                title = stringResource(R.string.auto_save_cookie),
                checked = state.enabledCookieJar,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(RssSourceEditIntent.SetCookieJarEnabled(!state.enabledCookieJar)) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                SourceEditOptionCard(
                    title = stringResource(R.string.bookshelf_layout),
                    subtitle = layoutTypes.getOrNull(state.articleStyle),
                    onClick = { styleMenuExpanded = true },
                )
                RoundDropdownMenu(
                    expanded = styleMenuExpanded,
                    onDismissRequest = { styleMenuExpanded = false },
                ) {
                    layoutTypes.forEachIndexed { index, title ->
                        RoundDropdownMenuItem(
                            text = title,
                            isSelected = state.articleStyle == index,
                            onClick = {
                                styleMenuExpanded = false
                                onIntent(RssSourceEditIntent.SetArticleStyle(index))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RssWebViewOptions(
    state: RssSourceEditUiState,
    onIntent: (RssSourceEditIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceEditOptionCard(
                title = stringResource(R.string.enable_js),
                checked = state.enableJs,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(RssSourceEditIntent.SetEnableJs(!state.enableJs)) },
            )
            SourceEditOptionCard(
                title = stringResource(R.string.load_with_base_url),
                checked = state.loadWithBaseUrl,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(RssSourceEditIntent.SetLoadWithBaseUrl(!state.loadWithBaseUrl)) },
            )
        }
    }
}

@Composable
private fun RssEditMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    state: RssSourceEditUiState,
    onIntent: (RssSourceEditIntent) -> Unit,
) {
    fun click(intent: RssSourceEditIntent) {
        onExpandedChange(false); onIntent(intent)
    }
    RoundDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
        RoundDropdownMenuItem(
            text = stringResource(R.string.login),
            onClick = { click(RssSourceEditIntent.SaveAndLogin) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.cookie),
            onClick = { click(RssSourceEditIntent.ClearCookie) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.auto_complete),
            isSelected = state.autoComplete,
            onClick = { click(RssSourceEditIntent.ToggleAutoComplete) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.copy_source),
            onClick = { click(RssSourceEditIntent.Copy) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.paste_source),
            onClick = { click(RssSourceEditIntent.Paste) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.set_source_variable),
            onClick = { click(RssSourceEditIntent.SaveAndSetVariable) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.str_share),
            onClick = { click(RssSourceEditIntent.Share) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.log),
            onClick = { click(RssSourceEditIntent.ShowLog) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.help),
            onClick = { click(RssSourceEditIntent.ShowHelp) })
    }
}
