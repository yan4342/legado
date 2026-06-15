package io.legado.app.ui.book.readRecord

import androidx.compose.runtime.Stable

enum class DisplayMode(val label: String) { SUMMARY("汇总"), LATEST("最近阅读"), BY_TIME("阅读时长") }

@Stable
data class ReadRecordUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val books: List<BookReadRecordItem> = emptyList(),
    val totalReadTime: Long = 0L,
    val todayReadTime: Long = 0L,
    val consecutiveDays: Int = 0,
    val totalBooks: Int = 0,
    val searchKey: String? = null,
    val displayMode: DisplayMode = DisplayMode.BY_TIME,
    val enableRecord: Boolean = true,
    val summaryCovers: List<BookReadRecordItem> = emptyList(),
    val hasMore: Boolean = false,
)

@Stable
data class BookReadRecordItem(
    val bookName: String, val author: String = "",
    val readTime: Long = 0L, val lastRead: Long = 0L,
    val coverPath: String? = null,
    val durChapterIndex: Int = 0,
    val durChapterTitle: String? = null,
)

sealed interface ReadRecordIntent {
    data object Load : ReadRecordIntent
    data object Refresh : ReadRecordIntent
    data class Search(val key: String?) : ReadRecordIntent
    data class SetMode(val mode: DisplayMode) : ReadRecordIntent
    data class DeleteBook(val bookName: String) : ReadRecordIntent
    data class ClickBook(val bookName: String, val author: String) : ReadRecordIntent
    data object ToggleEnableRecord : ReadRecordIntent
    data object LoadMore : ReadRecordIntent
}

sealed interface ReadRecordEffect {
    data class ShowToast(val message: String) : ReadRecordEffect
    data class NavigateToBook(val bookName: String, val author: String) : ReadRecordEffect
    data class OpenSearch(val bookName: String) : ReadRecordEffect
}
