package io.legado.app.ui.rss.source.debug

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.legado.app.ui.book.source.debug.BookSourceDebugEntryUi
import io.legado.app.ui.book.source.debug.BookSourceDebugFilter
import io.legado.app.ui.book.source.debug.BookSourceDebugStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class RssSourceDebugTarget { Sort, Content }

@Stable
data class RssSourceDebugUiState(
    val sourceName: String = "",
    val query: String = "",
    val target: RssSourceDebugTarget = RssSourceDebugTarget.Sort,
    val status: BookSourceDebugStatus = BookSourceDebugStatus.Loading,
    val filter: BookSourceDebugFilter = BookSourceDebugFilter.All,
    val entries: ImmutableList<BookSourceDebugEntryUi> = persistentListOf(),
    val examples: ImmutableList<RssSourceDebugExampleUi> = persistentListOf(),
    val selectedEntryId: Long? = null,
)

@Immutable
data class RssSourceDebugExampleUi(
    val title: String,
    val target: RssSourceDebugTarget,
    val value: String,
)

sealed interface RssSourceDebugIntent {
    data class Load(val sourceUrl: String?) : RssSourceDebugIntent
    data class SetQuery(val value: String) : RssSourceDebugIntent
    data class SelectTarget(val target: RssSourceDebugTarget) : RssSourceDebugIntent
    data class SelectFilter(val filter: BookSourceDebugFilter) : RssSourceDebugIntent
    data class UseExample(val example: RssSourceDebugExampleUi) : RssSourceDebugIntent
    data class ShowEntry(val id: Long) : RssSourceDebugIntent
    data object DismissEntry : RssSourceDebugIntent
    data object Start : RssSourceDebugIntent
    data object Stop : RssSourceDebugIntent
    data object Clear : RssSourceDebugIntent
}

sealed interface RssSourceDebugEffect {
    data class ShowMessage(val message: String) : RssSourceDebugEffect
}
