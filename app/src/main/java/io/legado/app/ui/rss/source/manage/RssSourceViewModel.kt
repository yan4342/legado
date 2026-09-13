package io.legado.app.ui.rss.source.manage

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.entities.RssSource
import io.legado.app.data.repository.RssSourceRepository
import io.legado.app.help.DefaultData
import io.legado.app.utils.GSON
import io.legado.app.ui.widget.components.list.InteractionState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RssSourceViewModel(
    application: Application,
    private val repository: RssSourceRepository,
) : ViewModel() {
    companion object {
        const val FILTER_ENABLED = "@enabled"
        const val FILTER_DISABLED = "@disabled"
        const val FILTER_LOGIN = "@login"
        const val FILTER_NO_GROUP = "@noGroup"
        const val PREFIX_GROUP = "group:"
    }

    private val searchKey = MutableStateFlow("")
    private val isSearchMode = MutableStateFlow(false)
    private val filter = MutableStateFlow<String?>(null)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val localOrder = MutableStateFlow<List<String>?>(null)
    private val enabledOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    val uiState = combine(
        repository.flowAll(),
        repository.flowGroups(),
        searchKey,
        isSearchMode,
        filter,
        selectedIds,
        localOrder,
        enabledOverrides,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val sources = (values[0] as List<RssSource>)
        val groups = values[1] as List<String>
        val query = values[2] as String
        val searchMode = values[3] as Boolean
        val activeFilter = values[4] as String?
        val selected = values[5] as Set<String>
        val local = values[6] as List<String>?
        val pendingEnabled = values[7] as Map<String, Boolean>
        val visible = if (local == null) {
            sources.filterFor(activeFilter, query)
        } else {
            val latestById = sources.associateBy { it.sourceUrl }
            local.mapNotNull { latestById[it] }
        }
        RssSourceUiState(
            items = visible.map { source ->
                RssSourceItemUi(
                    id = source.sourceUrl,
                    name = source.sourceName,
                    group = source.sourceGroup,
                    enabled = pendingEnabled[source.sourceUrl] ?: source.enabled,
                )
            }.toImmutableList(),
            selectedIds = selected.intersect(visible.map { it.sourceUrl }.toSet()).toImmutableSet(),
            searchKey = query,
            groupFilterName = activeFilter?.displayName(application),
            activeFilter = activeFilter,
            groups = groups.toImmutableList(),
            interaction = InteractionState(isSearchMode = searchMode),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RssSourceUiState())

    private val _effects = MutableSharedFlow<RssSourceEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    fun onIntent(intent: RssSourceIntent) {
        when (intent) {
            is RssSourceIntent.SetSearchMode -> {
                isSearchMode.value = intent.enabled
                if (!intent.enabled) searchKey.value = ""
            }

            is RssSourceIntent.SetSearchQuery -> {
                localOrder.value = null; searchKey.value = intent.query
            }

            is RssSourceIntent.SetSelection -> selectedIds.value = intent.ids
            is RssSourceIntent.ToggleSelection -> selectedIds.update { if (intent.id in it) it - intent.id else it + intent.id }
            is RssSourceIntent.SetFilter -> {
                localOrder.value = null; filter.value = intent.filter
            }

            is RssSourceIntent.SetEnabled -> setEnabled(intent.id, intent.enabled)
            is RssSourceIntent.SetEnabledForSelection -> launch {
                repository.setEnabled(intent.ids, intent.enabled)
            }

            is RssSourceIntent.Delete -> launch {
                repository.deleteByIds(intent.ids)
                selectedIds.update { it - intent.ids }
            }

            is RssSourceIntent.MoveToEdge -> moveToEdge(intent.ids, intent.toTop)
            is RssSourceIntent.MoveItem -> moveItem(intent.from, intent.to)
            RssSourceIntent.SaveSortOrder -> saveSortOrder()
            is RssSourceIntent.AddToGroup -> addToGroups(intent.ids, intent.group)
            is RssSourceIntent.RemoveFromGroup -> removeFromGroups(intent.ids, intent.group)
            is RssSourceIntent.AddGroup -> addGroup(intent.group)
            is RssSourceIntent.UpdateGroup -> renameGroup(intent.old, intent.new)
            is RssSourceIntent.DeleteGroup -> deleteGroup(intent.group)
            is RssSourceIntent.CheckSelectedInterval -> checkInterval(intent.ids)
            is RssSourceIntent.ExportSelection -> exportSelection(intent.ids)
            is RssSourceIntent.ShareSelection -> shareSelection(intent.ids)
            RssSourceIntent.ImportDefault -> launch { DefaultData.importDefaultRssSources() }
        }
    }

    private fun launch(block: suspend () -> Unit) =
        viewModelScope.launch(Dispatchers.IO) { block() }

    private fun setEnabled(id: String, enabled: Boolean) {
        enabledOverrides.update { it + (id to enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.setEnabled(setOf(id), enabled)
                repository.getAll().firstOrNull { it.sourceUrl == id }?.enabled == enabled
            }
            enabledOverrides.update { it - id }
        }
    }

    private fun moveToEdge(ids: Set<String>, toTop: Boolean) = launch {
        val sources = repository.getAll().filter { it.sourceUrl in ids }
        if (toTop) repository.topSources(sources) else repository.bottomSources(sources)
    }

    private fun moveItem(from: Int, to: Int) {
        val moved = (localOrder.value ?: uiState.value.items.map { it.id }).toMutableList()
        if (from !in moved.indices || to !in moved.indices) return
        moved.add(to, moved.removeAt(from)); localOrder.value = moved
    }

    private fun saveSortOrder() {
        val urls = localOrder.value ?: return
        launch {
            val byUrl = repository.getAll().associateBy { it.sourceUrl }
            val updated = urls.mapIndexedNotNull { index, url ->
                byUrl[url]?.copy(customOrder = index + 1)
            }
            repository.updateSources(*updated.toTypedArray())
            localOrder.value = null
        }
    }

    private fun addToGroups(ids: Set<String>, group: String) = launch {
        repository.addGroup(ids, group)
        selectedIds.value = emptySet()
    }

    private fun removeFromGroups(ids: Set<String>, group: String) = launch {
        repository.removeGroup(ids, group)
        selectedIds.value = emptySet()
    }

    private fun addGroup(group: String) = launch {
        repository.addGroupForNoGroup(group)
    }

    private fun renameGroup(old: String, new: String) = launch {
        if (new.isBlank()) repository.deleteGroup(old)
        else repository.renameGroup(old, new)
    }

    private fun deleteGroup(group: String) = launch {
        repository.deleteGroup(group)
    }

    private fun checkInterval(ids: Set<String>) {
        val items = uiState.value.items
        val positions = items.mapIndexedNotNull { index, item -> index.takeIf { item.id in ids } }
        if (positions.isNotEmpty()) selectedIds.value =
            items.subList(positions.min(), positions.max() + 1).map { it.id }.toSet()
    }

    private fun exportSelection(ids: Set<String>) = launch {
        val sources = sourcesForSelection(ids)
        _effects.emit(RssSourceEffect.Export(GSON.toJson(sources)))
    }

    private fun shareSelection(ids: Set<String>) = launch {
        val sources = sourcesForSelection(ids)
        _effects.emit(RssSourceEffect.Share(GSON.toJson(sources)))
    }

    private suspend fun sourcesForSelection(ids: Set<String>): List<RssSource> {
        val items = uiState.value.items
        val selectedRate = if (items.isEmpty()) 1f else ids.size.toFloat() / items.size.toFloat()
        return when {
            selectedRate == 1f -> getSources()
            selectedRate < 0.3f -> repository.getAll().filter { it.sourceUrl in ids }
            else -> getSources().filter { it.sourceUrl in ids }
        }
    }

    private suspend fun getSources(): List<RssSource> {
        val activeFilter = filter.value
        val query = searchKey.value
        return when {
            query.isNotBlank() -> repository.search(query)
            activeFilter == null -> repository.getAll()
            activeFilter == FILTER_ENABLED -> repository.getAllEnabled()
            activeFilter == FILTER_DISABLED -> repository.getAllDisabled()
            activeFilter == FILTER_LOGIN -> repository.getAllLogin()
            activeFilter == FILTER_NO_GROUP -> repository.getAllNoGroup()
            activeFilter.startsWith(PREFIX_GROUP) ->
                repository.getByGroup(activeFilter.removePrefix(PREFIX_GROUP))

            else -> repository.getAll()
        }
    }
}

private fun List<RssSource>.filterFor(filter: String?, query: String): List<RssSource> =
    filter { source ->
        val filterMatch = when (filter) {
            null -> true
            RssSourceViewModel.FILTER_ENABLED -> source.enabled
            RssSourceViewModel.FILTER_DISABLED -> !source.enabled
            RssSourceViewModel.FILTER_LOGIN -> !source.loginUrl.isNullOrBlank()
            RssSourceViewModel.FILTER_NO_GROUP -> source.sourceGroup.isNullOrBlank()
            else -> filter.startsWith(RssSourceViewModel.PREFIX_GROUP) &&
                source.sourceGroup?.split(",")
                    ?.contains(filter.removePrefix(RssSourceViewModel.PREFIX_GROUP)) == true
        }
        filterMatch && (query.isBlank() || listOf(
            source.sourceName,
            source.sourceUrl,
            source.sourceGroup,
            source.sourceComment,
        ).any { it?.contains(query, true) == true })
    }.sortedBy { it.customOrder }

private fun String.displayName(application: Application) = when (this) {
    RssSourceViewModel.FILTER_ENABLED -> application.getString(R.string.enabled)
    RssSourceViewModel.FILTER_DISABLED -> application.getString(R.string.disabled)
    RssSourceViewModel.FILTER_LOGIN -> application.getString(R.string.need_login)
    RssSourceViewModel.FILTER_NO_GROUP -> application.getString(R.string.no_group)
    else -> removePrefix(RssSourceViewModel.PREFIX_GROUP)
}
