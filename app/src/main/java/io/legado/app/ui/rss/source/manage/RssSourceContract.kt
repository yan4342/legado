package io.legado.app.ui.rss.source.manage

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.legado.app.ui.widget.components.list.InteractionState
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.list.SelectableItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Immutable
data class RssSourceItemUi(
    override val id: String,
    val name: String,
    val group: String?,
    val enabled: Boolean,
) : SelectableItem<String>

@Stable
data class RssSourceUiState(
    override val items: ImmutableList<RssSourceItemUi> = persistentListOf(),
    override val selectedIds: ImmutableSet<String> = persistentSetOf(),
    override val searchKey: String = "",
    val groupFilterName: String? = null,
    val activeFilter: String? = null,
    val groups: ImmutableList<String> = persistentListOf(),
    val interaction: InteractionState = InteractionState(isLoading = true),
) : ListUiState<RssSourceItemUi> {
    override val isSearch get() = interaction.isSearchMode
    override val isLoading get() = interaction.isLoading
}

sealed interface RssSourceIntent {
    data class SetSearchMode(val enabled: Boolean) : RssSourceIntent
    data class SetSearchQuery(val query: String) : RssSourceIntent
    data class SetSelection(val ids: Set<String>) : RssSourceIntent
    data class ToggleSelection(val id: String) : RssSourceIntent
    data class SetFilter(val filter: String?) : RssSourceIntent
    data class SetEnabled(val id: String, val enabled: Boolean) : RssSourceIntent
    data class SetEnabledForSelection(val ids: Set<String>, val enabled: Boolean) : RssSourceIntent
    data class Delete(val ids: Set<String>) : RssSourceIntent
    data class MoveToEdge(val ids: Set<String>, val toTop: Boolean) : RssSourceIntent
    data class MoveItem(val from: Int, val to: Int) : RssSourceIntent
    data object SaveSortOrder : RssSourceIntent
    data class AddToGroup(val ids: Set<String>, val group: String) : RssSourceIntent
    data class RemoveFromGroup(val ids: Set<String>, val group: String) : RssSourceIntent
    data class AddGroup(val group: String) : RssSourceIntent
    data class UpdateGroup(val old: String, val new: String) : RssSourceIntent
    data class DeleteGroup(val group: String) : RssSourceIntent
    data class CheckSelectedInterval(val ids: Set<String>) : RssSourceIntent
    data class ExportSelection(val ids: Set<String>) : RssSourceIntent
    data class ShareSelection(val ids: Set<String>) : RssSourceIntent
    data object ImportDefault : RssSourceIntent
}

sealed interface RssSourceEffect {
    data class Export(val json: String) : RssSourceEffect
    data class Share(val json: String) : RssSourceEffect
}
