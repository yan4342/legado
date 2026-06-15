package io.legado.app.ui.book.readRecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.appDb
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

class ReadRecordViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ReadRecordUiState())
    val uiState: StateFlow<ReadRecordUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ReadRecordEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    /** 缓存全量排序后的数据，分页时从这里切片 */
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
                    var c = 0; val cl = Calendar.getInstance(); val f = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    while (true) { if (appDb.dailyReadRecordDao.sumByDateRange(f.format(cl.time), f.format(cl.time)) > 0) { c++; cl.add(Calendar.DAY_OF_YEAR, -1) } else break }; c
                }

                val bookNames = filtered.map { it.bookName }.distinct()
                val booksMap = withContext(Dispatchers.IO) { bookNames.mapNotNull { n -> appDb.bookDao.findByName(n).firstOrNull()?.let { it.name to it } }.toMap() }
                allItems = filtered.map { r -> val b = booksMap[r.bookName]; BookReadRecordItem(r.bookName, b?.author ?: "", r.readTime, r.lastRead, b?.getDisplayCover(), b?.durChapterIndex ?: 0, b?.durChapterTitle) }

                // top 5 covers
                val top5 = showRecords.sortedByDescending { it.readTime }.take(5).map { it.bookName }
                val top5Map = withContext(Dispatchers.IO) { top5.mapNotNull { n -> appDb.bookDao.findByName(n).firstOrNull()?.let { it.name to it } }.toMap() }
                val covers = top5.mapNotNull { n -> val b = top5Map[n]; val r = showRecords.firstOrNull { it.bookName == n } ?: return@mapNotNull null; BookReadRecordItem(r.bookName, b?.author ?: "", r.readTime, r.lastRead, b?.getDisplayCover(), b?.durChapterIndex ?: 0, b?.durChapterTitle) }

                val page = allItems.take(pageSize)
                _uiState.update { it.copy(isLoading = false, books = page, totalReadTime = allTime, todayReadTime = todayTime, consecutiveDays = consecutiveDays, totalBooks = showRecords.size, summaryCovers = covers, enableRecord = AppConfig.enableReadRecord, hasMore = allItems.size > pageSize) }
            } catch (e: Exception) { _uiState.update { it.copy(isLoading = false) }; _effects.tryEmit(ReadRecordEffect.ShowToast("加载失败: ${e.message}")) }
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

    private fun search(key: String?) { _uiState.update { it.copy(searchKey = key?.trim()?.takeIf { it.isNotEmpty() }) }; loadData() }
    private fun setMode(mode: DisplayMode) { _uiState.update { it.copy(displayMode = mode) }; loadData() }
    private fun toggleEnableRecord() { AppConfig.enableReadRecord = !AppConfig.enableReadRecord; _uiState.update { it.copy(enableRecord = AppConfig.enableReadRecord) } }

    private fun deleteBook(bookName: String) {
        viewModelScope.launch { withContext(Dispatchers.IO) { appDb.readRecordDao.deleteByName(bookName) }; loadData(); _effects.tryEmit(ReadRecordEffect.ShowToast("已删除")) }
    }

    private fun onBookClick(bookName: String, author: String) {
        viewModelScope.launch { val b = withContext(Dispatchers.IO) { appDb.bookDao.findByName(bookName).firstOrNull() }; if (b != null) _effects.tryEmit(ReadRecordEffect.NavigateToBook(bookName, author)) else _effects.tryEmit(ReadRecordEffect.OpenSearch(bookName)) }
    }
}
