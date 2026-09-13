package io.legado.app.ui.book.bookmark

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.Bookmark
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AllBookmarkUiState(
    val bookmarks: ImmutableList<Bookmark> = persistentListOf(),
    val editingBookmark: Bookmark? = null,
)

sealed interface AllBookmarkIntent {
    data class BookmarkClick(val bookmark: Bookmark) : AllBookmarkIntent
    data object DismissDialog : AllBookmarkIntent
    data class SaveBookmark(val time: Long, val bookText: String, val content: String) : AllBookmarkIntent
    data class DeleteBookmark(val time: Long) : AllBookmarkIntent
    data object ExportJson : AllBookmarkIntent
    data object ExportMd : AllBookmarkIntent
}

enum class ExportType { JSON, MD }

sealed interface AllBookmarkEffect {
    data class RequestExport(val type: ExportType) : AllBookmarkEffect
    data class ShowToast(val message: String) : AllBookmarkEffect
}
