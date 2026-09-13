package io.legado.app.model.webBook

import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.mapParallelSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import splitties.init.appCtx
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext
import kotlin.math.min

class SearchModel(private val scope: CoroutineScope, private val callBack: CallBack) {
    val threadCount = AppConfig.threadCount
    private var searchPool: ExecutorCoroutineDispatcher? = null
    private var mSearchId = 0L
    private var searchPage = 1
    private var searchKey: String = ""
    private var bookSourceParts = emptyList<BookSourcePart>()
    private var searchBooks = arrayListOf<SearchBook>()
    private var searchJob: Job? = null
    private var workingState = MutableStateFlow(true)


    private fun initSearchPool() {
        searchPool?.close()
        searchPool = Executors
            .newFixedThreadPool(min(threadCount, AppConst.MAX_THREAD)).asCoroutineDispatcher()
    }

    fun search(searchId: Long, key: String) {
        if (searchId != mSearchId) {
            if (key.isEmpty()) {
                return
            }
            searchKey = key
            if (mSearchId != 0L) {
                close()
            }
            searchBooks.clear()
            bookSourceParts = callBack.getSearchScope().getBookSourceParts(callBack.getSourceTypes())
            if (bookSourceParts.isEmpty()) {
                callBack.onSearchCancel(NoStackTraceException("启用书源为空"))
                return
            }
            mSearchId = searchId
            searchPage = 1
            initSearchPool()
        } else {
            searchPage++
        }
        startSearch()
    }

    private fun startSearch() {
        startSearch(bookSourceParts)
    }

    private fun startSearch(parts: List<BookSourcePart>) {
        val precision = appCtx.getPrefBoolean(PreferKey.precisionSearch)
        var hasMore = false
        searchJob = scope.launch(searchPool!!) {
            flow {
                for (bs in parts) {
                    bs.getBookSource()?.let {
                        emit(it)
                    }
                    workingState.first { it }
                }
            }.onStart {
                callBack.onSearchStart()
            }.mapParallelSafe(threadCount) {
                withTimeout(30000L) {
                    WebBook.searchBookAwait(
                        it, searchKey, searchPage,
                        filter = { name, author ->
                            !precision || name.contains(searchKey) ||
                                    author.contains(searchKey)
                        })
                }
            }.onEach { items ->
                for (book in items) {
                    book.releaseHtmlData()
                }
                hasMore = hasMore || items.isNotEmpty()
                appDb.searchBookDao.insert(*items.toTypedArray())
                mergeItems(items, precision)
                currentCoroutineContext().ensureActive()
                callBack.onSearchSuccess(searchBooks)
            }.onCompletion {
                if (it == null) callBack.onSearchFinish(searchBooks.isEmpty(), hasMore)
            }.catch {
                AppLog.put("书源搜索出错\n${it.localizedMessage}", it)
            }.collect()
        }
    }

    private suspend fun mergeItems(newDataS: List<SearchBook>, precision: Boolean) {
        if (newDataS.isNotEmpty()) {
            val copyData = ArrayList(searchBooks)
            val equalData = arrayListOf<SearchBook>()
            val containsData = arrayListOf<SearchBook>()
            val otherData = arrayListOf<SearchBook>()
            copyData.forEach {
                coroutineContext.ensureActive()
                if (it.name == searchKey || it.author == searchKey) {
                    equalData.add(it)
                } else if (it.name.contains(searchKey) || it.author.contains(searchKey)) {
                    containsData.add(it)
                } else {
                    otherData.add(it)
                }
            }
            newDataS.forEach { nBook ->
                coroutineContext.ensureActive()
                if (nBook.name == searchKey || nBook.author == searchKey) {
                    var hasSame = false
                    equalData.forEach { pBook ->
                        coroutineContext.ensureActive()
                        if (pBook.name == nBook.name && pBook.author == nBook.author) {
                            mergeSearchBookDetails(pBook, nBook)
                            hasSame = true
                        }
                    }
                    if (!hasSame) {
                        equalData.add(nBook)
                    }
                } else if (nBook.name.contains(searchKey) || nBook.author.contains(searchKey)) {
                    var hasSame = false
                    containsData.forEach { pBook ->
                        coroutineContext.ensureActive()
                        if (pBook.name == nBook.name && pBook.author == nBook.author) {
                            mergeSearchBookDetails(pBook, nBook)
                            hasSame = true
                        }
                    }
                    if (!hasSame) {
                        containsData.add(nBook)
                    }
                } else if (!precision) {
                    var hasSame = false
                    otherData.forEach { pBook ->
                        coroutineContext.ensureActive()
                        if (pBook.name == nBook.name && pBook.author == nBook.author) {
                            mergeSearchBookDetails(pBook, nBook)
                            hasSame = true
                        }
                    }
                    if (!hasSame) {
                        otherData.add(nBook)
                    }
                }
            }
            coroutineContext.ensureActive()
            equalData.sortByDescending { it.origins.size }
            equalData.addAll(containsData.sortedByDescending { it.origins.size })
            if (!precision) {
                equalData.addAll(otherData)
            }
            coroutineContext.ensureActive()
            searchBooks = equalData
        }
    }

    private fun mergeSearchBookDetails(target: SearchBook, incoming: SearchBook) {
        target.addOrigin(incoming.origin)
        if (target.kind.isNullOrBlank() && !incoming.kind.isNullOrBlank()) {
            target.kind = incoming.kind
        }
        if (target.wordCount.isNullOrBlank() && !incoming.wordCount.isNullOrBlank()) {
            target.wordCount = incoming.wordCount
        }
        if (target.intro.isNullOrBlank() && !incoming.intro.isNullOrBlank()) {
            target.intro = incoming.intro
        }
        if (target.latestChapterTitle.isNullOrBlank() && !incoming.latestChapterTitle.isNullOrBlank()) {
            target.latestChapterTitle = incoming.latestChapterTitle
        }
        if (target.coverUrl.isNullOrBlank() && !incoming.coverUrl.isNullOrBlank()) {
            target.coverUrl = incoming.coverUrl
        }
    }

    fun pause() {
        workingState.value = false
    }

    fun resume() {
        workingState.value = true
    }

    /**
     * 增量扩展搜索：保留已有结果，只搜索 [addedParts] 里的新增书源。
     * 用于搜索范围纯新增（如勾选更多分组）时避免整场重启。
     * @return true 表示已启动增量搜索；false 表示当前没有可延续的搜索上下文，调用方应回退为整场重启。
     */
    fun extendSearch(key: String, fullScopeParts: List<BookSourcePart>, addedParts: List<BookSourcePart>): Boolean {
        if (addedParts.isEmpty() || key.isEmpty()) return false
        if (mSearchId == 0L || searchPool == null) return false
        searchKey = key
        searchPage = 1
        // bookSourceParts 更新为完整新范围，保证后续「加载更多」覆盖整个范围
        bookSourceParts = fullScopeParts
        // 本次只搜新增书源；searchBooks 不清空，与旧结果合并
        startSearch(addedParts)
        return true
    }

    /**
     * 范围缩小（移除书源/分组）时裁剪内存结果：剔除所有来源都不在新范围内的书。
     * 保留书源列表同步为 [newScopeParts]，保证后续「加载更多」不再覆盖已移除书源。
     */
    fun pruneResults(newScopeParts: List<BookSourcePart>) {
        val keepUrls = newScopeParts.map { it.bookSourceUrl }.toSet()
        val kept = searchBooks.filter { it.origins.isEmpty() || it.origins.any { origin -> origin in keepUrls } }
        searchBooks.clear()
        searchBooks.addAll(kept)
        bookSourceParts = newScopeParts
    }

    fun cancelSearch() {
        close()
        callBack.onSearchCancel()
    }

    fun close() {
        searchJob?.cancel()
        searchPool?.close()
        searchPool = null
        mSearchId = 0L
        searchKey = ""
    }

    interface CallBack {
        fun getSearchScope(): SearchScope
        fun getSourceTypes(): Set<Int> = emptySet()
        fun onSearchStart()
        fun onSearchSuccess(searchBooks: List<SearchBook>)
        fun onSearchFinish(isEmpty: Boolean, hasMore: Boolean)
        fun onSearchCancel(exception: Throwable? = null)
    }

}