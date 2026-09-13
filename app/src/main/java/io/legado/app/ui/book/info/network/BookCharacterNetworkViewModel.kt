package io.legado.app.ui.book.info.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.dao.BookDao
import io.legado.app.domain.gateway.AiMemoryTableGateway
import io.legado.app.domain.gateway.ReadAloudCharacterGateway
import io.legado.app.domain.model.DramaticRole
import io.legado.app.domain.model.RelationGraphParser
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.DebugLog
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

class BookCharacterNetworkViewModel(
    private val bookUrl: String,
    private val bookDao: BookDao,
    private val memoryTableGateway: AiMemoryTableGateway,
    private val readAloudCharacterGateway: ReadAloudCharacterGateway,
    private val focusCharacterId: String? = null,
) : ViewModel() {

    private companion object {
        private const val TAG = "RelationGraph"
    }

    private val _uiState = MutableStateFlow(
        BookCharacterNetworkUiState(
            bookUrl = bookUrl,
            focusCharacterId = focusCharacterId,
            simulationEnabled = !AppConfig.isEInkMode,
        ),
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BookCharacterNetworkEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var loadJob: Job? = null
    private var initialSelectionApplied = false

    init {
        load()
    }

    fun onIntent(intent: BookCharacterNetworkIntent) {
        when (intent) {
            BookCharacterNetworkIntent.Refresh -> {
                initialSelectionApplied = false
                load()
            }
            is BookCharacterNetworkIntent.SelectNode -> selectNode(intent.nodeId)
            is BookCharacterNetworkIntent.SetViewMode -> {
                _uiState.update { it.copy(viewMode = intent.mode) }
            }
            BookCharacterNetworkIntent.ToggleSimulation -> {
                _uiState.update { it.copy(simulationEnabled = !it.simulationEnabled) }
            }
            is BookCharacterNetworkIntent.SetSimulationEnabled -> {
                _uiState.update { it.copy(simulationEnabled = intent.enabled) }
            }
            BookCharacterNetworkIntent.OpenIdentifyCharacters -> {
                _effects.tryEmit(BookCharacterNetworkEffect.NavigateToCharacterList(bookUrl))
            }
        }
    }

    private fun selectNode(nodeId: String?) {
        val state = _uiState.value
        if (nodeId != null && nodeId == state.selectedNodeId) {
            val cardId = state.nodes.firstOrNull { it.id == nodeId }?.characterCardId
            if (!cardId.isNullOrBlank()) {
                _effects.tryEmit(BookCharacterNetworkEffect.OpenCharacterEdit(cardId))
            } else {
                _effects.tryEmit(
                    BookCharacterNetworkEffect.ShowToast(
                        appCtx.getString(R.string.character_network_no_card),
                    ),
                )
            }
            return
        }
        _uiState.update { it.copy(selectedNodeId = nodeId) }
    }

    private fun logD(message: String) {
        runCatching { DebugLog.d(TAG, message) }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val book = bookDao.getBook(bookUrl)
                val bookName = book?.name.orEmpty()
                val bookAuthor = book?.author.orEmpty()
                combine(
                    memoryTableGateway.observeForBook(bookUrl, bookName, bookAuthor),
                    readAloudCharacterGateway.observeCharacterCards(bookUrl),
                    readAloudCharacterGateway.observeSpeakerCharacters(bookUrl),
                ) { tables, cards, speakers -> Triple(tables, cards, speakers) }
                    .collect { (tables, cards, speakers) ->
                        val rowsByTable = tables.associate { table ->
                            table.id to memoryTableGateway.getRows(table.id)
                        }
                        logD(
                            "load bookUrl=$bookUrl tables=${tables.size} cards=${cards.size} " +
                                "relationTables=${tables.count(RelationGraphParser::isRelationTable)}",
                        )
                        val graph = RelationGraphParser.build(tables, rowsByTable, cards)
                        val roleByCardId = speakers.associate { it.id to it.role }
                        val avatarByCardId = cards.associate { it.id to it.avatarPath }
                        val hubCardId = speakers.firstOrNull { it.role == DramaticRole.MALE_LEAD }?.id
                            ?: speakers.firstOrNull { it.role == DramaticRole.FEMALE_LEAD }?.id
                        val hubNodeId = hubCardId?.let { cardId ->
                            graph.nodes.firstOrNull { it.characterCardId == cardId }?.id
                        }
                        val nodes = graph.nodes.map { node ->
                            val cardId = node.characterCardId
                            NetworkNodeUi(
                                id = node.id,
                                name = node.name,
                                characterCardId = cardId,
                                dramaticRole = cardId?.let { roleByCardId[it] }.orEmpty(),
                                avatarPath = cardId?.let { avatarByCardId[it] }.orEmpty(),
                            )
                        }.toImmutableList()
                        val preferredFocus = focusCharacterId
                            ?.takeIf { id -> nodes.any { it.characterCardId == id || it.id == id } }
                            ?.let { id ->
                                nodes.firstOrNull { it.characterCardId == id || it.id == id }?.id
                            }
                        val nodeIds = nodes.map { it.id }.toSet()
                        val retainedSelection = _uiState.value.selectedNodeId
                            ?.takeIf { it in nodeIds }
                        val initialSelected = when {
                            initialSelectionApplied -> retainedSelection
                            preferredFocus != null -> preferredFocus
                            else -> hubNodeId
                        }
                        initialSelectionApplied = true
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                hubNodeId = hubNodeId,
                                selectedNodeId = initialSelected,
                                nodes = nodes,
                                edges = graph.edges.map { edge ->
                                    NetworkEdgeUi(
                                        id = edge.id,
                                        fromNodeId = edge.fromNodeId,
                                        toNodeId = edge.toNodeId,
                                        label = listOf(edge.relationType, edge.summary)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · "),
                                    )
                                }.toImmutableList(),
                                hasRelationTable = tables.any(RelationGraphParser::isRelationTable),
                                emptyHint = when {
                                    tables.none(RelationGraphParser::isRelationTable) ->
                                        appCtx.getString(R.string.character_network_no_relation_table)
                                    graph.nodes.isEmpty() ->
                                        appCtx.getString(R.string.character_network_empty)
                                    else -> ""
                                },
                            )
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false) }
                _effects.tryEmit(
                    BookCharacterNetworkEffect.ShowToast(
                        e.localizedMessage ?: appCtx.getString(R.string.load_failed)
                    )
                )
            }
        }
    }
}
