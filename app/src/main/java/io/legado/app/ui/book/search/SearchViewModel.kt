package io.legado.app.ui.book.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.PreferKey
import io.legado.app.constant.BookType
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.domain.model.BookShelfState
import io.legado.app.domain.model.MatchMode
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.book.removeType
import io.legado.app.help.config.AppConfig
import io.legado.app.model.webBook.SearchModel
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import splitties.init.appCtx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    val searchLayoutMode = MutableStateFlow(0)

    fun toggleSearchLayout() {
        searchLayoutMode.value = if (searchLayoutMode.value == 0) 1 else 0
    }

    val searchScope = SearchScope(AppConfig.searchScope)

    private val _uiState = MutableStateFlow(
        SearchUiState(
            scopeDisplay = searchScope.display,
            scopeDisplayNames = searchScope.displayNames,
            isAllScope = searchScope.isAll(),
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SearchEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private val queryFlow = MutableStateFlow("")

    private val searchModel: SearchModel
    private var bookshelfSet: Set<String> = emptySet()

    private var persistedSearchScopeRaw = AppConfig.searchScope
    private var hasTemporaryScope = false

    private var searchJob: Job? = null
    private var searchId = 0L
    private var currentSearchPage = 1
    private var wasSearching = false

    init {
        searchModel = SearchModel(viewModelScope, object : SearchModel.CallBack {
            override fun getSearchScope(): SearchScope = searchScope
            override fun onSearchStart() {
                _uiState.update { it.copy(isSearching = true) }
            }
            override fun onSearchSuccess(searchBooks: List<SearchBook>) {
                _uiState.update { state ->
                    val updated = mergeResults(state.results, searchBooks)
                    state.copy(results = updated)
                }
            }
            override fun onSearchFinish(isEmpty: Boolean, hasMore: Boolean) {
                _uiState.update { state ->
                    val emptyAction = if (state.results.isEmpty() && isEmpty && !searchScope.isAll()) {
                        SearchEmptyScopeAction(searchScope.display, state.matchMode)
                    } else null
                    state.copy(
                        isSearching = false,
                        hasMore = hasMore,
                        emptyScopeAction = emptyAction,
                    )
                }
            }
            override fun onSearchCancel(exception: Throwable?) {
                _uiState.update { it.copy(isSearching = false) }
                exception?.localizedMessage?.takeIf { it.isNotBlank() }?.let {
                    _effects.tryEmit(SearchEffect.ShowMessage(it))
                }
            }
        })

        observeBookshelf()
        observeQueryFlows()
    }

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.Initialize -> initialize(intent.key, intent.scopeRaw)
            is SearchIntent.UpdateQuery -> updateQuery(intent.query, intent.showSuggestions)
            SearchIntent.SubmitSearch -> submitSearch()
            SearchIntent.LoadMore -> loadMore()
            SearchIntent.StopSearch -> stopSearch()
            SearchIntent.PauseEngine -> { wasSearching = searchJob?.isActive == true; searchModel.pause() }
            SearchIntent.ResumeEngine -> {
                searchModel.resume()
                if (wasSearching) {
                    val state = _uiState.value
                    if (state.committedQuery.isNotBlank() && searchJob?.isActive != true) {
                        startSearch(state.committedQuery)
                    }
                    wasSearching = false
                }
            }
            is SearchIntent.UseHistoryKeyword -> {
                updateQuery(intent.keyword, showSuggestions = false)
                submitSearch(intent.keyword)
            }
            is SearchIntent.OpenSearchBook -> {
                _effects.tryEmit(
                    SearchEffect.OpenBookInfo(
                        name = intent.book.name, author = intent.book.author,
                        bookUrl = intent.book.bookUrl, origin = intent.book.origin,
                        coverPath = intent.book.coverUrl, sharedCoverKey = intent.sharedCoverKey,
                    )
                )
            }
            is SearchIntent.OpenBookshelfBook -> {
                _effects.tryEmit(
                    SearchEffect.OpenBookInfo(
                        name = intent.book.name, author = intent.book.author,
                        bookUrl = intent.book.bookUrl, origin = intent.book.origin,
                        coverPath = intent.book.displayCover, sharedCoverKey = null,
                    )
                )
            }
            is SearchIntent.DeleteHistory -> viewModelScope.launch { appDb.searchKeywordDao.delete(intent.item) }
            is SearchIntent.SetClearHistoryDialogVisible -> _uiState.update { it.copy(showClearHistoryDialog = intent.visible) }
            SearchIntent.ConfirmClearHistory -> {
                _uiState.update { it.copy(showClearHistoryDialog = false) }
                viewModelScope.launch { appDb.searchKeywordDao.deleteAll() }
            }
            is SearchIntent.SetScopeSheetVisible -> _uiState.update { it.copy(showScopeSheet = intent.visible) }
            is SearchIntent.SetSettingsSheetVisible -> _uiState.update { it.copy(showSettingsSheet = intent.visible) }
            is SearchIntent.ToggleSourceType -> {
                _uiState.update { state ->
                    val current = state.selectedSourceTypes
                    state.copy(
                        selectedSourceTypes = if (current.contains(intent.type)) current - intent.type else current + intent.type
                    )
                }
                restartCommittedSearchIfNeeded()
            }
            is SearchIntent.SetMatchMode -> {
                _uiState.update { it.copy(matchMode = intent.mode) }
                appCtx.putPrefBoolean(PreferKey.precisionSearch, intent.mode == MatchMode.EXACT)
                restartCommittedSearchIfNeeded()
            }
            SearchIntent.ConfirmEmptyScopeAction -> handleEmptyScopeActionConfirmed()
            SearchIntent.DismissEmptyScopeAction -> _uiState.update { it.copy(emptyScopeAction = null) }
            SearchIntent.OpenSourceManage -> _effects.tryEmit(SearchEffect.OpenSourceManage)
            is SearchIntent.ApplyScopeUpdate -> {
                searchScope.update(intent.scopeRaw)
                syncScopeState(restartSearch = true)
            }
            is SearchIntent.ToggleScopeItem -> {
                if (searchScope.displayNames.contains(intent.itemName)) {
                    searchScope.remove(intent.itemName)
                } else {
                    searchScope.update(intent.itemName)
                }
                syncScopeState(restartSearch = true)
            }
            SearchIntent.SelectAllScope -> {
                searchScope.update("")
                syncScopeState(restartSearch = true)
            }
            is SearchIntent.ExpandSource -> expandSource(intent.sourceUrl, intent.sourceName)
            SearchIntent.DismissExpandedSource -> _uiState.update { it.copy(showExpandedSource = false) }
            SearchIntent.LoadMoreExpandedSource -> {
                val s = _uiState.value
                val url = s.expandedSourceUrl ?: return
                if (s.expandedSourceLoading || s.expandedSourceEnd) return
                _uiState.update { it.copy(expandedSourceLoading = true, expandedSourceError = null) }
                loadExpandedSourcePage(url, s.expandedSourcePage)
            }
            is SearchIntent.OpenExpandedSourceBook -> {
                _effects.tryEmit(
                    SearchEffect.OpenBookInfo(
                        name = intent.book.name, author = intent.book.author,
                        bookUrl = intent.book.bookUrl, origin = intent.book.origin,
                        coverPath = intent.book.coverUrl, sharedCoverKey = intent.sharedCoverKey,
                    )
                )
            }
            is SearchIntent.SaveScrollState -> _uiState.update { it.copy(savedScrollIndex = intent.index, savedScrollOffset = intent.offset) }
            is SearchIntent.SaveExpandedSourceScrollState -> _uiState.update { it.copy(expandedSourceSavedScrollIndex = intent.index, expandedSourceSavedScrollOffset = intent.offset) }
        }
    }

    fun onAddToShelf(book: SearchBook) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val b = book.toBook()
                b.removeType(BookType.notShelf)
                if (b.order == 0) {
                    b.order = appDb.bookDao.minOrder - 1
                }
                appDb.bookDao.insert(b)
            }
            _effects.tryEmit(SearchEffect.ShowMessage(appCtx.getString(R.string.add_book_success)))
        }
    }

    override fun onCleared() {
        searchModel.close()
        super.onCleared()
    }

    private fun initialize(key: String?, scopeRaw: String?) {
        val temporaryScope = scopeRaw?.takeIf { it.isNotBlank() }
        if (temporaryScope != null) {
            hasTemporaryScope = true
            searchScope.update(temporaryScope, postValue = false)
            syncScopeState()
        }

        val initKey = key?.trim().orEmpty()
        if (initKey.isNotEmpty()) {
            stopSearch()
            updateQuery(initKey, showSuggestions = false)
            submitSearch(initKey)
        } else if (key != null) {
            val hasActiveSearch = _uiState.value.committedQuery.isNotEmpty()
            if (!hasActiveSearch) {
                updateQuery(initKey, showSuggestions = true)
            }
        }
    }

    private fun observeBookshelf() {
        viewModelScope.launch {
            appDb.bookDao.flowAll().map { books ->
                val keys = hashSetOf<String>()
                books.filterNot { it.isNotShelf }.forEach { b ->
                    keys.add("${b.name}-${b.author}")
                    keys.add(b.bookUrl)
                }
                keys
            }.catch { emit(hashSetOf<String>()) }.collect { keys ->
                bookshelfSet = keys
            }
        }
    }

    private fun observeQueryFlows() {
        viewModelScope.launch {
            queryFlow.map { it.trim() }.distinctUntilChanged().flatMapLatest { q ->
                if (q.isBlank()) appDb.searchKeywordDao.flowByTime()
                else appDb.searchKeywordDao.flowSearch(q)
            }.catch { emit(emptyList()) }.collect { history ->
                _uiState.update { it.copy(history = history) }
            }
        }
        viewModelScope.launch {
            queryFlow.map { it.trim() }.distinctUntilChanged().flatMapLatest { q ->
                appDb.bookDao.flowSearch(q)
            }.catch { emit(emptyList()) }.collect { books ->
                val hints = books.map { b ->
                    BookShelfItemUi(name = b.name, author = b.author, bookUrl = b.bookUrl, origin = b.origin, coverUrl = b.coverUrl)
                }
                _uiState.update { it.copy(bookshelfHints = hints) }
            }
        }
    }

    private fun updateQuery(query: String, showSuggestions: Boolean) {
        val state = _uiState.value
        if (state.query == query && state.showSuggestions == showSuggestions) return
        if (showSuggestions && state.isSearching && state.query != query) {
            stopSearch()
        }
        queryFlow.value = query
        _uiState.update { it.copy(query = query, showSuggestions = showSuggestions, emptyScopeAction = null) }
    }

    private fun submitSearch(keyOverride: String? = null) {
        val keyword = keyOverride?.trim() ?: queryFlow.value.trim()
        if (keyword.isBlank()) return
        updateQuery(keyword, showSuggestions = false)
        searchModel.cancelSearch()
        searchId = System.currentTimeMillis()
        currentSearchPage = 1
        _uiState.update {
            it.copy(
                committedQuery = keyword,
                results = emptyList(),
                isManualStop = false,
                hasMore = true,
                processedSources = 0,
                totalSources = 0,
                emptyScopeAction = null,
            )
        }
        viewModelScope.launch { appDb.searchKeywordDao.get(keyword)?.let { it.usage += 1; it.lastUseTime = System.currentTimeMillis(); appDb.searchKeywordDao.update(it) } ?: appDb.searchKeywordDao.insert(SearchKeyword(keyword, 1)) }
        startSearch(keyword)
    }

    private fun loadMore() {
        val state = _uiState.value
        if (state.isSearching || state.committedQuery.isBlank() || !state.hasMore) return
        currentSearchPage += 1
        _uiState.update { it.copy(isManualStop = false, showSuggestions = false) }
        startSearch(state.committedQuery)
    }

    private fun startSearch(keyword: String) {
        searchJob?.cancel()
        searchModel.resume()
        wasSearching = true
        searchJob = viewModelScope.launch { searchModel.search(searchId, keyword) }
    }

    private fun stopSearch() {
        searchModel.cancelSearch()
        wasSearching = false
        _uiState.update { it.copy(isSearching = false, isManualStop = true) }
    }

    private fun syncScopeState(restartSearch: Boolean = false) {
        persistSearchScope()
        _uiState.update {
            it.copy(
                scopeDisplay = searchScope.display,
                scopeDisplayNames = searchScope.displayNames,
                isAllScope = searchScope.isAll(),
            )
        }
        if (restartSearch) restartCommittedSearchIfNeeded()
    }

    private fun persistSearchScope() {
        hasTemporaryScope = false
        val raw = searchScope.toString()
        persistedSearchScopeRaw = raw
        AppConfig.searchScope = raw
    }

    private fun handleEmptyScopeActionConfirmed() {
        val action = _uiState.value.emptyScopeAction ?: return
        _uiState.update { it.copy(emptyScopeAction = null) }
        if (action.wasMatchMode == MatchMode.EXACT) {
            _uiState.update { it.copy(matchMode = MatchMode.DEFAULT) }
            appCtx.putPrefBoolean(PreferKey.precisionSearch, false)
        } else {
            searchScope.update("")
            syncScopeState()
        }
        restartCommittedSearchIfNeeded()
    }

    private fun restartCommittedSearchIfNeeded() {
        val state = _uiState.value
        if (state.committedQuery.isNotBlank() && state.query.trim() == state.committedQuery && !state.showSuggestions && !state.isManualStop) {
            submitSearch(state.committedQuery)
        }
    }

    private fun mergeResults(existing: List<SearchResultItemUi>, newBooks: List<SearchBook>): List<SearchResultItemUi> {
        val map = LinkedHashMap<String, SearchBook>()
        existing.forEach { map[it.book.bookUrl] = it.book }
        newBooks.forEach { newBook ->
            val existingBook = map[newBook.bookUrl]
            if (existingBook != null) {
                newBook.origins.forEach { existingBook.addOrigin(it) }
            } else {
                map[newBook.bookUrl] = newBook
            }
        }
        val keyword = _uiState.value.committedQuery
        val matchMode = _uiState.value.matchMode
        val sorted = map.values.sortedWithSearchPriority(keyword, matchMode)
        return sorted.map { book ->
            SearchResultItemUi(book = book, shelfState = resolveShelfState(book))
        }
    }

    private fun resolveShelfState(book: SearchBook): BookShelfState {
        val key = if (book.author.isNotBlank()) "${book.name}-${book.author}" else book.name
        if (bookshelfSet.contains(book.bookUrl)) return BookShelfState.IN_SHELF
        if (bookshelfSet.contains(key)) return BookShelfState.SAME_NAME_AUTHOR
        return BookShelfState.NOT_IN_SHELF
    }

    private fun Collection<SearchBook>.sortedWithSearchPriority(keyword: String, matchMode: MatchMode): List<SearchBook> {
        val equalBooks = arrayListOf<SearchBook>()
        val tagsBooks = arrayListOf<SearchBook>()
        val containsBooks = arrayListOf<SearchBook>()
        val otherBooks = arrayListOf<SearchBook>()
        forEach { book ->
            when {
                book.name.equals(keyword, ignoreCase = true) || book.author.equals(keyword, ignoreCase = true) -> equalBooks.add(book)
                book.kind?.contains(keyword, ignoreCase = true) == true -> tagsBooks.add(book)
                book.name.contains(keyword, ignoreCase = true) || book.author.contains(keyword, ignoreCase = true) -> containsBooks.add(book)
                matchMode == MatchMode.DEFAULT -> otherBooks.add(book)
            }
        }
        return buildList {
            addAll(equalBooks.sortedByDescending { it.origins.size })
            addAll(tagsBooks.sortedByDescending { it.origins.size })
            addAll(containsBooks.sortedByDescending { it.origins.size })
            addAll(otherBooks)
        }
    }

    private fun expandSource(sourceUrl: String, sourceName: String) {
        val state = _uiState.value
        if (state.expandedSourceUrl == sourceUrl) {
            _uiState.update { it.copy(showExpandedSource = true) }
            return
        }
        _uiState.update {
            it.copy(
                expandedSourceUrl = sourceUrl, expandedSourceName = sourceName,
                expandedSourceBooks = emptyList(), expandedSourceLoading = true,
                expandedSourceEnd = false, expandedSourceError = null,
                expandedSourcePage = 1, showExpandedSource = true,
                expandedSourceSavedScrollIndex = 0, expandedSourceSavedScrollOffset = 0,
            )
        }
        loadExpandedSourcePage(sourceUrl, page = 1)
    }

    private fun loadExpandedSourcePage(sourceUrl: String, page: Int) {
        viewModelScope.launch {
            val keyword = _uiState.value.committedQuery
            try {
                val source = withContext(Dispatchers.IO) { appDb.bookSourceDao.getBookSource(sourceUrl) }
                    ?: throw Exception("书源不存在")
                val books = withTimeout(30000L) {
                    withContext(Dispatchers.IO) {
                        WebBook.exploreBookAwait(source, source.searchUrl ?: source.exploreUrl ?: throw Exception("无可探索URL"), page)
                    }
                }
                _uiState.update {
                    it.copy(
                        expandedSourceBooks = it.expandedSourceBooks + books,
                        expandedSourceLoading = false,
                        expandedSourceEnd = books.isEmpty(),
                        expandedSourcePage = page + 1,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(expandedSourceLoading = false, expandedSourceError = e.localizedMessage ?: "Unknown error")
                }
            }
        }
    }
}
