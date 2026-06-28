package io.legado.app.ui.book.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.domain.model.BookShelfState
import io.legado.app.domain.model.MatchMode
import io.legado.app.ui.common.compose.LegadoAlertDialog
import io.legado.app.ui.common.compose.ModalLegadoBottomSheet
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class, FlowPreview::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onOpenBookInfo: (name: String, author: String, bookUrl: String, origin: String?, coverPath: String?, sharedCoverKey: String?) -> Unit,
    onOpenSourceManage: () -> Unit,
    onShowLog: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val groupedListState = rememberLazyListState()
    var queryInput by rememberSaveable { mutableStateOf(state.query) }
    var ignoreNextDebouncedQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var keepResultsPinnedToTop by rememberSaveable { mutableStateOf(true) }
    var previewBook by remember { mutableStateOf<SearchBook?>(null) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    val showSuggestions = state.showSuggestions
    val isSourceGroupedMode by viewModel.searchLayoutMode.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(state.query) { if (state.query != queryInput) queryInput = state.query }

    LaunchedEffect(Unit) {
        snapshotFlow { queryInput }
            .distinctUntilChanged()
            .debounce(200)
            .collect { newQuery ->
                if (ignoreNextDebouncedQuery == newQuery) { ignoreNextDebouncedQuery = null; return@collect }
                if (newQuery != state.query) viewModel.onIntent(SearchIntent.UpdateQuery(newQuery))
            }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val activeState = if (isSourceGroupedMode == 1) groupedListState else listState
            val total = activeState.layoutInfo.totalItemsCount
            val last = activeState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            total > 0 && last >= total - 3
        }
    }

    LaunchedEffect(shouldLoadMore, state.isSearching, state.hasMore, state.isManualStop, state.showSuggestions) {
        if (!state.isSearching && state.hasMore && !state.isManualStop && !state.showSuggestions && shouldLoadMore) {
            viewModel.onIntent(SearchIntent.LoadMore)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SearchEffect.OpenBookInfo -> onOpenBookInfo(effect.name, effect.author, effect.bookUrl, effect.origin, effect.coverPath, effect.sharedCoverKey)
                SearchEffect.OpenSourceManage -> onOpenSourceManage()
                is SearchEffect.ShowMessage -> context.toastOnUi(effect.message)
            }
        }
    }

    DisposableEffect(viewModel) {
        onDispose { viewModel.onIntent(SearchIntent.PauseEngine) }
    }

    LaunchedEffect(viewModel) { viewModel.onIntent(SearchIntent.ResumeEngine) }

    DisposableEffect(viewModel) {
        onDispose {
            val first = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            if (first > 0 || offset > 0) viewModel.onIntent(SearchIntent.SaveScrollState(first, offset))
        }
    }

    LaunchedEffect(state.savedScrollIndex, state.savedScrollOffset) {
        val idx = state.savedScrollIndex; val off = state.savedScrollOffset
        if (idx > 0 || off > 0) { listState.scrollToItem(idx, off); viewModel.onIntent(SearchIntent.SaveScrollState(0, 0)) }
    }

    LaunchedEffect(state.committedQuery) { keepResultsPinnedToTop = true }

    LaunchedEffect(isSourceGroupedMode, listState, groupedListState) {
        snapshotFlow {
            val activeState = if (isSourceGroupedMode == 1) groupedListState else listState
            Triple(activeState.firstVisibleItemIndex, activeState.firstVisibleItemScrollOffset, activeState.isScrollInProgress)
        }.collect { triple ->
            val index = triple.first
            val offset = triple.second
            val isScrolling = triple.third
            if (index == 0 && offset == 0) keepResultsPinnedToTop = true
            else if (isScrolling) keepResultsPinnedToTop = false
        }
    }

    val firstResultKey = state.results.firstOrNull()?.let { "${it.book.origin}:${it.book.bookUrl}" }
    LaunchedEffect(firstResultKey, state.results.size, state.isSearching) {
        if (state.isSearching && keepResultsPinnedToTop && state.results.isNotEmpty()) {
            listState.scrollToItem(0); groupedListState.scrollToItem(0)
        }
    }

    val submitSearch: (String) -> Unit = { rawQuery ->
        val normalized = rawQuery.trim()
        if (normalized.isNotBlank()) {
            ignoreNextDebouncedQuery = normalized; queryInput = normalized
            if (normalized != state.query) viewModel.onIntent(SearchIntent.UpdateQuery(normalized))
            viewModel.onIntent(SearchIntent.SubmitSearch)
            focusManager.clearFocus()
        }
    }

    BackHandler { onBack() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    if (!showSuggestions) {
                        IconButton(onClick = viewModel::toggleSearchLayout) {
                            Icon(
                                if (isSourceGroupedMode == 1) Icons.AutoMirrored.Filled.List else Icons.AutoMirrored.Filled.List,
                                contentDescription = stringResource(R.string.switchLayout)
                            )
                        }
                        IconButton(onClick = { viewModel.onIntent(SearchIntent.SetSettingsSheetVisible(true)) }) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.setting))
                        }
                    }
                    Box {
                        IconButton(onClick = { overflowMenuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
                        }
                        DropdownMenu(
                            expanded = overflowMenuExpanded,
                            onDismissRequest = { overflowMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.precision_search)) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    val newMode = if (state.matchMode == MatchMode.EXACT) MatchMode.DEFAULT else MatchMode.EXACT
                                    viewModel.onIntent(SearchIntent.SetMatchMode(newMode))
                                },
                                trailingIcon = {
                                    if (state.matchMode == MatchMode.EXACT) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.groups_or_source)) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    viewModel.onIntent(SearchIntent.SetScopeSheetVisible(true))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.book_source_manage)) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onOpenSourceManage()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.log)) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onShowLog()
                                },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            val showFab = state.isSearching || (state.committedQuery.isNotBlank() && state.hasMore)
            AnimatedVisibility(visible = showFab, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()) {
                FloatingActionButton(
                    onClick = {
                        if (state.isSearching) viewModel.onIntent(SearchIntent.StopSearch)
                        else viewModel.onIntent(SearchIntent.LoadMore)
                    },
                ) {
                    Icon(
                        if (state.isSearching) Icons.Filled.Close else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isSearching) stringResource(R.string.stop) else stringResource(R.string.start)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Search bar
            OutlinedTextField(
                value = queryInput,
                onValueChange = { queryInput = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_book_key)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (queryInput.isNotEmpty()) {
                        IconButton(onClick = { queryInput = ""; viewModel.onIntent(SearchIntent.UpdateQuery("")) }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch(queryInput) }),
                shape = RoundedCornerShape(28.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Floating summary
            val showResultCount = state.committedQuery.isNotBlank() || state.isSearching
            if (showResultCount && !showSuggestions) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            stringResource(R.string.search_result_count, state.results.size),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (state.totalSources > 0) {
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                stringResource(R.string.search_source_progress, state.processedSources, state.totalSources),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            AnimatedContent(targetState = showSuggestions, label = "SearchBody") { isSuggestionVisible ->
                if (isSuggestionVisible) {
                    SearchSuggestionPanel(
                        state = state,
                        onUseHistory = { keyword -> queryInput = keyword; viewModel.onIntent(SearchIntent.UseHistoryKeyword(keyword)) },
                        onDeleteHistory = { viewModel.onIntent(SearchIntent.DeleteHistory(it)) },
                        onOpenBook = { viewModel.onIntent(SearchIntent.OpenBookshelfBook(it)) },
                        onClearHistory = { viewModel.onIntent(SearchIntent.SetClearHistoryDialogVisible(true)) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    if (state.results.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            SearchResultFooter(isSearching = state.isSearching, hasMore = state.hasMore, hasResult = false, committedQuery = state.committedQuery)
                        }
                    } else {
                        val sourceGroupedResults = remember(state.results) {
                            state.results.groupBy { it.book.origin }.map { (origin, books) ->
                                SourceGroup(origin = origin, sourceName = books.firstOrNull()?.book?.originName?.takeIf { it.isNotBlank() } ?: origin, items = books)
                            }
                        }

                        if (isSourceGroupedMode == 1) {
                            LazyColumn(
                                state = groupedListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                sourceGroupedResults.forEach { group ->
                                    item(key = "header_${group.origin}") {
                                        SearchSourceSection(
                                            sourceName = group.sourceName, items = group.items,
                                            onClickBook = { book ->
                                                val key = "cover_${book.bookUrl}"
                                                viewModel.onIntent(SearchIntent.OpenSearchBook(book, key))
                                            },
                                            onLongClickBook = { previewBook = it },
                                            onViewAll = { viewModel.onIntent(SearchIntent.ExpandSource(group.origin, group.sourceName)) },
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                        )
                                    }
                                }
                                item { SearchResultFooter(isSearching = state.isSearching, hasMore = state.hasMore, hasResult = true, committedQuery = state.committedQuery) }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                itemsIndexed(
                                    items = state.results,
                                    key = { _, item -> "${item.book.origin}:${item.book.bookUrl}" }
                                ) { _, item ->
                                    val coverKey = "cover_${item.book.bookUrl}"
                                    SearchBookListItem(
                                        book = item.book, shelfState = item.shelfState,
                                        onClick = { viewModel.onIntent(SearchIntent.OpenSearchBook(item.book, coverKey)) },
                                        onLongClick = { previewBook = it },
                                        sourceCount = item.book.origins.size,
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        sharedCoverKey = coverKey,
                                    )
                                }
                                item { SearchResultFooter(isSearching = state.isSearching, hasMore = state.hasMore, hasResult = true, committedQuery = state.committedQuery) }
                            }
                        }
                    }
                }
            }
        }
    }

    // Clear history dialog
    LegadoAlertDialog(
        show = state.showClearHistoryDialog,
        onDismissRequest = { viewModel.onIntent(SearchIntent.SetClearHistoryDialogVisible(false)) },
        dialogTitle = stringResource(R.string.draw),
        text = stringResource(R.string.sure_clear_search_history),
        confirmText = stringResource(R.string.ok),
        onConfirm = { viewModel.onIntent(SearchIntent.ConfirmClearHistory) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { viewModel.onIntent(SearchIntent.SetClearHistoryDialogVisible(false)) },
    )

    // Empty scope dialog
    LegadoAlertDialog(
        show = state.emptyScopeAction != null,
        onDismissRequest = { viewModel.onIntent(SearchIntent.DismissEmptyScopeAction) },
        dialogTitle = stringResource(R.string.draw),
        text = state.emptyScopeAction?.let { action ->
            if (action.wasMatchMode == MatchMode.EXACT) "${action.scopeDisplay}分组搜索结果为空，是否关闭精准搜索？"
            else "${action.scopeDisplay}分组搜索结果为空，是否切换到全部分组？"
        } ?: "",
        confirmText = stringResource(R.string.ok),
        onConfirm = { viewModel.onIntent(SearchIntent.ConfirmEmptyScopeAction) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { viewModel.onIntent(SearchIntent.DismissEmptyScopeAction) },
    )

    // Preview sheet
    SearchBookPreviewSheet(
        book = previewBook,
        shelfState = previewBook?.let { book -> state.results.find { it.book.bookUrl == book.bookUrl }?.shelfState } ?: io.legado.app.domain.model.BookShelfState.NOT_IN_SHELF,
        onDismissRequest = { previewBook = null },
        onOpenDetail = { book -> previewBook = null; viewModel.onIntent(SearchIntent.OpenSearchBook(book, null)) },
        onAddToShelf = { book -> previewBook = null; viewModel.onAddToShelf(book) },
        onExpandToDetail = null,
    )

    // Settings sheet
    SettingsSheet(
        show = state.showSettingsSheet,
        onDismissRequest = { viewModel.onIntent(SearchIntent.SetSettingsSheetVisible(false)) },
        isSourceGroupedMode = isSourceGroupedMode == 1,
        onToggleLayoutMode = { viewModel.toggleSearchLayout() },
        selectedSourceTypes = state.selectedSourceTypes,
        onToggleSourceType = { viewModel.onIntent(SearchIntent.ToggleSourceType(it)) },
    )

    // Scope sheet
    ScopeSheet(
        show = state.showScopeSheet,
        onDismissRequest = { viewModel.onIntent(SearchIntent.SetScopeSheetVisible(false)) },
        currentDisplayNames = state.scopeDisplayNames,
        isAllScope = state.isAllScope,
        onApplyScope = { scopeRaw -> viewModel.onIntent(SearchIntent.ApplyScopeUpdate(scopeRaw)) },
    )

    // Expanded source sheet
    ExpandedSourceSheet(
        show = state.showExpandedSource,
        sourceName = state.expandedSourceName ?: "",
        books = state.expandedSourceBooks,
        isLoading = state.expandedSourceLoading,
        isEnd = state.expandedSourceEnd,
        errorMsg = state.expandedSourceError,
        savedScrollIndex = state.expandedSourceSavedScrollIndex,
        savedScrollOffset = state.expandedSourceSavedScrollOffset,
        onDismiss = { viewModel.onIntent(SearchIntent.DismissExpandedSource) },
        onLoadMore = { viewModel.onIntent(SearchIntent.LoadMoreExpandedSource) },
        onSaveScrollState = { index, offset -> viewModel.onIntent(SearchIntent.SaveExpandedSourceScrollState(index, offset)) },
        onBookClick = { book -> viewModel.onIntent(SearchIntent.OpenExpandedSourceBook(book, null)) },
        onBookLongClick = { previewBook = it },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchSuggestionPanel(
    state: SearchUiState,
    onUseHistory: (String) -> Unit,
    onDeleteHistory: (SearchKeyword) -> Unit,
    onOpenBook: (BookShelfItemUi) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.query.isNotBlank() && state.bookshelfHints.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.bookshelf),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    state.bookshelfHints.forEach { book ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .combinedClickable(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onOpenBook(book)
                                    },
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(book.name, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.searchHistory), style = MaterialTheme.typography.titleSmall)
                }
                if (state.history.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.clear))
                    }
                }
            }
        }

        if (state.history.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        } else {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.history.forEach { history ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .combinedClickable(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onUseHistory(history.word)
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDeleteHistory(history)
                                    },
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(history.word, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultFooter(
    isSearching: Boolean, hasMore: Boolean, hasResult: Boolean, committedQuery: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        when {
            isSearching -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.is_loading))
            }
            !hasResult && committedQuery.isNotBlank() -> Text(stringResource(R.string.search_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            hasMore -> Text(stringResource(R.string.search_has_more), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Text(stringResource(R.string.search_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class SourceGroup(val origin: String, val sourceName: String, val items: List<SearchResultItemUi>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    isSourceGroupedMode: Boolean,
    onToggleLayoutMode: () -> Unit,
    selectedSourceTypes: Set<Int>,
    onToggleSourceType: (Int) -> Unit,
) {
    ModalLegadoBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.setting),
        skipPartiallyExpanded = true,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.layout_mode), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // 选项1：列表
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { if (isSourceGroupedMode) onToggleLayoutMode() }
                    .padding(vertical = 4.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = !isSourceGroupedMode, onClick = null) // onClick 交给外层
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.search_list_mode), style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(modifier = Modifier.width(48.dp))
            // 选项2：按书源分组
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { if (!isSourceGroupedMode) onToggleLayoutMode() }
                    .padding(vertical = 4.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = isSourceGroupedMode, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.search_group_by_source), style = MaterialTheme.typography.bodyLarge)
            }
        }

            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.search_type), style = MaterialTheme.typography.titleSmall)

            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { selectedSourceTypes.forEach { onToggleSourceType(it) } }.padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = selectedSourceTypes.isEmpty(), onCheckedChange = { selectedSourceTypes.forEach { onToggleSourceType(it) } })
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.all), style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onToggleSourceType(0) }.padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = selectedSourceTypes.contains(0), onCheckedChange = { onToggleSourceType(0) })
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.noval), style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onToggleSourceType(2) }.padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = selectedSourceTypes.contains(2), onCheckedChange = { onToggleSourceType(2) })
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.manga), style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onToggleSourceType(1) }.padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = selectedSourceTypes.contains(1), onCheckedChange = { onToggleSourceType(1) })
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.audio), style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ExpandedSourceSheet(
    show: Boolean, sourceName: String, books: List<SearchBook>,
    isLoading: Boolean, isEnd: Boolean, errorMsg: String?,
    savedScrollIndex: Int, savedScrollOffset: Int,
    onDismiss: () -> Unit, onLoadMore: () -> Unit,
    onSaveScrollState: (Int, Int) -> Unit,
    onBookClick: (SearchBook) -> Unit,
    onBookLongClick: ((SearchBook) -> Unit)?,
) {
    ModalLegadoBottomSheet(show = show, onDismissRequest = onDismiss, title = sourceName) {
        val listState = rememberLazyListState()
        val showLoadMore = isLoading || errorMsg != null || isEnd

        val shouldLoadMore by remember {
            derivedStateOf {
                val total = listState.layoutInfo.totalItemsCount
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                total > 0 && last >= total - 3
            }
        }

        LaunchedEffect(shouldLoadMore, isLoading, isEnd) {
            if (shouldLoadMore && !isLoading && !isEnd) onLoadMore()
        }

        LaunchedEffect(savedScrollIndex, savedScrollOffset) {
            if (savedScrollIndex > 0 || savedScrollOffset > 0) { listState.scrollToItem(savedScrollIndex, savedScrollOffset); onSaveScrollState(0, 0) }
        }

        DisposableEffect(listState) {
            onDispose {
                val first = listState.firstVisibleItemIndex; val offset = listState.firstVisibleItemScrollOffset
                if (first > 0 || offset > 0) onSaveScrollState(first, offset)
            }
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(books, key = { it.bookUrl }) { book ->
                SearchBookListItem(
                    book = book, shelfState = io.legado.app.domain.model.BookShelfState.NOT_IN_SHELF,
                    onClick = { onBookClick(book) }, onLongClick = onBookLongClick,
                )
            }
            if (showLoadMore) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        when {
                            isLoading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            errorMsg != null -> Text(errorMsg, color = MaterialTheme.colorScheme.error)
                            isEnd -> Text(stringResource(R.string.search_loaded_all), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    currentDisplayNames: List<String>,
    isAllScope: Boolean,
    onApplyScope: (String) -> Unit,
) {
    var isSourceMode by remember { mutableStateOf(false) }
    var selectedGroups by remember { mutableStateOf(currentDisplayNames.toSet()) }
    var selectedSourceUrl by remember { mutableStateOf<String?>(null) }
    var allGroups by remember { mutableStateOf<List<String>>(emptyList()) }
    var allSources by remember { mutableStateOf<List<io.legado.app.data.entities.BookSourcePart>>(emptyList()) }
    var searchKey by remember { mutableStateOf("") }

    LaunchedEffect(show) {
        if (show) {
            selectedGroups = currentDisplayNames.toSet()
            isSourceMode = false
            allGroups = withContext(Dispatchers.IO) { appDb.bookSourceDao.allEnabledGroups() }
            allSources = withContext(Dispatchers.IO) { appDb.bookSourceDao.allEnabledPart }
        }
    }

    val filteredSources = remember(searchKey, allSources) {
        if (searchKey.isBlank()) allSources
        else allSources.filter { it.bookSourceName.contains(searchKey, ignoreCase = true) || it.bookSourceUrl.contains(searchKey, ignoreCase = true) }
    }

    ModalLegadoBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.groups_or_source),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isSourceMode,
                    onClick = { isSourceMode = false },
                    label = { Text("分组") },
                    shape = RoundedCornerShape(20.dp),
                )
                FilterChip(
                    selected = isSourceMode,
                    onClick = { isSourceMode = true },
                    label = { Text("书源") },
                    shape = RoundedCornerShape(20.dp),
                )
            }

            if (isSourceMode) {
                OutlinedTextField(
                    value = searchKey,
                    onValueChange = { searchKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search)) },
                    trailingIcon = {
                        if (searchKey.isNotEmpty()) {
                            IconButton(onClick = { searchKey = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                )
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (isSourceMode) {
                    items(filteredSources, key = { it.bookSourceUrl }) { source ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                selectedSourceUrl = if (selectedSourceUrl == source.bookSourceUrl) null else source.bookSourceUrl
                            }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedSourceUrl == source.bookSourceUrl,
                                onClick = { selectedSourceUrl = if (selectedSourceUrl == source.bookSourceUrl) null else source.bookSourceUrl },
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(source.bookSourceName, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                } else {
                    items(allGroups, key = { it }) { group ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                selectedGroups = if (selectedGroups.contains(group)) selectedGroups - group else selectedGroups + group
                            }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selectedGroups.contains(group),
                                onCheckedChange = { selectedGroups = if (selectedGroups.contains(group)) selectedGroups - group else selectedGroups + group },
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(group, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = {
                    selectedGroups = allGroups.toSet()
                    selectedSourceUrl = null
                }) {
                    Text(stringResource(R.string.all))
                }
                TextButton(onClick = {
                    val scope = if (isSourceMode) {
                        allSources.find { it.bookSourceUrl == selectedSourceUrl }?.let {
                            "${it.bookSourceName}::${it.bookSourceUrl}"
                        } ?: ""
                    } else {
                        selectedGroups.joinToString(",")
                    }
                    onApplyScope(scope)
                    onDismissRequest()
                }) {
                    Text(stringResource(R.string.ok))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}