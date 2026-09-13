package io.legado.app.ui.book.readRecord

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.appDb
import io.legado.app.data.repository.AiUsageRepository
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.cnCompare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReadRecordViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val aiUsageRepository = AiUsageRepository()

    private val _uiState = MutableStateFlow(
        ReadRecordUiState(
            selectedTab = savedStateHandle.get<String>(KEY_SELECTED_TAB)
                ?.let { runCatching { RecordTab.valueOf(it) }.getOrNull() }
                ?: RecordTab.READING,
        )
    )
    val uiState: StateFlow<ReadRecordUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ReadRecordEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var allItems: List<BookReadRecordItem> = emptyList()
    private val pageSize = 30

    fun onIntent(intent: ReadRecordIntent) {
        when (intent) {
            is ReadRecordIntent.Load -> loadData()
            is ReadRecordIntent.Refresh -> loadData()
            is ReadRecordIntent.LoadMore -> loadMore()
            is ReadRecordIntent.Search -> search(intent.key)
            is ReadRecordIntent.SetMode -> setMode(intent.mode)
            is ReadRecordIntent.DeleteBook -> deleteBook(intent.bookName)
            is ReadRecordIntent.ClickBook -> onBookClick(intent.bookName, intent.author)
            is ReadRecordIntent.ToggleEnableRecord -> toggleEnableRecord()
            is ReadRecordIntent.SetTab -> {
                savedStateHandle[KEY_SELECTED_TAB] = intent.tab.name
                _uiState.update { it.copy(selectedTab = intent.tab) }
            }
            is ReadRecordIntent.SetAiDisplayMode -> {
                _uiState.update { it.copy(aiDisplayMode = intent.mode) }
                loadAiItems(intent.mode)
            }
            is ReadRecordIntent.RequestClearAiUsage -> _uiState.update { it.copy(showClearAiConfirm = true) }
            is ReadRecordIntent.ConfirmClearAiUsage -> clearAiUsage()
            is ReadRecordIntent.DismissClearAiConfirm -> _uiState.update { it.copy(showClearAiConfirm = false) }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val allTime = withContext(Dispatchers.IO) { appDb.readRecordDao.allTime }
                val showRecords = withContext(Dispatchers.IO) { appDb.readRecordDao.allShow }
                val mode = _uiState.value.displayMode

                val sorted = when (mode) {
                    DisplayMode.BY_TIME -> showRecords.sortedByDescending { it.readTime }
                    DisplayMode.LATEST -> showRecords.sortedByDescending { it.lastRead }
                    DisplayMode.SUMMARY -> showRecords.sortedWith { o1, o2 -> o1.bookName.cnCompare(o2.bookName) }
                }
                val searchKey = _uiState.value.searchKey
                val filtered = if (searchKey.isNullOrBlank()) sorted else sorted.filter { it.bookName.contains(searchKey, ignoreCase = true) }

                val cal = Calendar.getInstance()
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val today = fmt.format(cal.time)
                val todayTime = withContext(Dispatchers.IO) { appDb.dailyReadRecordDao.sumByDateRange(today, today) }
                val consecutiveDays = withContext(Dispatchers.IO) {
                    var c = 0
                    val cl = Calendar.getInstance()
                    val f = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    while (true) {
                        if (appDb.dailyReadRecordDao.sumByDateRange(f.format(cl.time), f.format(cl.time)) > 0) {
                            c++
                            cl.add(Calendar.DAY_OF_YEAR, -1)
                        } else break
                    }
                    c
                }

                val bookNames = filtered.map { it.bookName }.distinct()
                val booksMap = withContext(Dispatchers.IO) {
                    bookNames.mapNotNull { n -> appDb.bookDao.findByName(n).firstOrNull()?.let { it.name to it } }.toMap()
                }
                allItems = filtered.map { r ->
                    val b = booksMap[r.bookName]
                    BookReadRecordItem(r.bookName, b?.author ?: "", r.readTime, r.lastRead, b?.getDisplayCover(), b?.durChapterIndex ?: 0, b?.durChapterTitle)
                }

                val top5 = showRecords.sortedByDescending { it.readTime }.take(5).map { it.bookName }
                val top5Map = withContext(Dispatchers.IO) {
                    top5.mapNotNull { n -> appDb.bookDao.findByName(n).firstOrNull()?.let { it.name to it } }.toMap()
                }
                val covers = top5.mapNotNull { n ->
                    val b = top5Map[n]
                    val r = showRecords.firstOrNull { it.bookName == n } ?: return@mapNotNull null
                    BookReadRecordItem(r.bookName, b?.author ?: "", r.readTime, r.lastRead, b?.getDisplayCover(), b?.durChapterIndex ?: 0, b?.durChapterTitle)
                }

                val aiSummary = withContext(Dispatchers.IO) { loadAiSummary() }
                val aiItems = withContext(Dispatchers.IO) { loadAiItemsInternal(_uiState.value.aiDisplayMode) }

                val page = allItems.take(pageSize)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        books = page,
                        totalReadTime = allTime,
                        todayReadTime = todayTime,
                        consecutiveDays = consecutiveDays,
                        totalBooks = showRecords.size,
                        summaryCovers = covers,
                        enableRecord = AppConfig.enableReadRecord,
                        hasMore = allItems.size > pageSize,
                        aiSummary = aiSummary,
                        aiItems = aiItems,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _effects.tryEmit(ReadRecordEffect.ShowToast("加载失败: ${e.message}"))
            }
        }
    }

    private fun loadAiItems(mode: AiUsageDisplayMode) {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { loadAiItemsInternal(mode) }
            _uiState.update { it.copy(aiItems = items) }
        }
    }

    private fun loadAiSummary(): AiUsageSummaryUi {
        val today = aiUsageRepository.todaySummary()
        val all = aiUsageRepository.allTimeSummary()
        val sources = aiUsageRepository.sumBySource().filter { it.totalTokens > 0 }
        val top = sources.take(4)
        val otherTokens = sources.drop(4).sumOf { it.totalTokens }
        val sourceSlices = sortSourceSlices(buildList {
            addAll(top.map { AiUsageSourceSliceUi(it.source, it.totalTokens) })
            if (otherTokens > 0) add(AiUsageSourceSliceUi(source = "", tokens = otherTokens))
        })
        return AiUsageSummaryUi(
            todayTokens = today.totalTokens,
            totalTokens = all.totalTokens,
            todayChars = today.generatedChars,
            totalChars = all.generatedChars,
            consecutiveDays = aiUsageRepository.consecutiveUsageDays(),
            callCount = all.callCount,
            sourceSlices = sourceSlices,
            hasEstimated = aiUsageRepository.hasEstimatedRecords(),
        )
    }

    private fun loadAiItemsInternal(mode: AiUsageDisplayMode): List<AiUsageItemUi> = when (mode) {
        AiUsageDisplayMode.BY_MODEL -> aiUsageRepository.sumByModel().map {
            AiUsageItemUi(
                id = "model_${it.modelId}",
                title = it.modelName.ifBlank { it.modelId }.ifBlank { "未知模型" },
                subtitle = "${it.callCount} 次调用",
                totalTokens = it.totalTokens,
                generatedChars = it.generatedChars,
            )
        }
        AiUsageDisplayMode.BY_SOURCE -> aiUsageRepository.sumBySource().map {
            AiUsageItemUi(
                id = "source_${it.source}",
                title = aiSourceDisplayName(it.source),
                subtitle = "${it.callCount} 次调用",
                totalTokens = it.totalTokens,
                generatedChars = it.generatedChars,
                source = it.source,
            )
        }
        AiUsageDisplayMode.RECENT -> aiUsageRepository.recentRecords(50).map {
            AiUsageItemUi(
                id = it.id,
                title = it.modelName.ifBlank { it.modelId }.ifBlank { "未知模型" },
                subtitle = "${aiSourceDisplayName(it.source)} · ${AiUsageFormatter.formatTime(it.timestamp)}",
                totalTokens = it.totalTokens.toLong(),
                generatedChars = it.generatedChars.toLong(),
                timestamp = it.timestamp,
                source = it.source,
                estimated = it.estimated,
            )
        }
    }

    private fun clearAiUsage() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { aiUsageRepository.clearAll() }
            _uiState.update { it.copy(showClearAiConfirm = false) }
            loadData()
            _effects.tryEmit(ReadRecordEffect.ShowToast("已清空 AI 统计"))
        }
    }

    private fun loadMore() {
        val current = _uiState.value
        if (current.isLoadingMore || !current.hasMore) return
        _uiState.update { it.copy(isLoadingMore = true) }
        val currentSize = current.books.size
        val nextPage = allItems.drop(currentSize).take(pageSize)
        val newBooks = current.books + nextPage
        _uiState.update { it.copy(isLoadingMore = false, books = newBooks, hasMore = newBooks.size < allItems.size) }
    }

    private fun search(key: String?) {
        _uiState.update { it.copy(searchKey = key?.trim()?.takeIf { it.isNotEmpty() }) }
        loadData()
    }

    private fun setMode(mode: DisplayMode) {
        _uiState.update { it.copy(displayMode = mode) }
        loadData()
    }

    private fun toggleEnableRecord() {
        AppConfig.enableReadRecord = !AppConfig.enableReadRecord
        _uiState.update { it.copy(enableRecord = AppConfig.enableReadRecord) }
    }

    private fun deleteBook(bookName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { appDb.readRecordDao.deleteByName(bookName) }
            loadData()
            _effects.tryEmit(ReadRecordEffect.ShowToast("已删除"))
        }
    }

    private fun onBookClick(bookName: String, author: String) {
        viewModelScope.launch {
            val b = withContext(Dispatchers.IO) { appDb.bookDao.findByName(bookName).firstOrNull() }
            if (b != null) _effects.tryEmit(ReadRecordEffect.NavigateToBook(bookName, author))
            else _effects.tryEmit(ReadRecordEffect.OpenSearch(bookName))
        }
    }

    companion object {
        private const val KEY_SELECTED_TAB = "selectedTab"
    }
}
