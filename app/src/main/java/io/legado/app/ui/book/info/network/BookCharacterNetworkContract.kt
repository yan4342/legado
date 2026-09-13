package io.legado.app.ui.book.info.network

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

enum class NetworkViewMode {
    Graph,
    List,
}

@Stable
data class BookCharacterNetworkUiState(
    val bookUrl: String,
    val isLoading: Boolean = true,
    val nodes: ImmutableList<NetworkNodeUi> = persistentListOf(),
    val edges: ImmutableList<NetworkEdgeUi> = persistentListOf(),
    val hubNodeId: String? = null,
    val selectedNodeId: String? = null,
    val viewMode: NetworkViewMode = NetworkViewMode.Graph,
    val simulationEnabled: Boolean = true,
    val focusCharacterId: String? = null,
    val emptyHint: String = "",
    val hasRelationTable: Boolean = false,
)

@Stable
data class NetworkNodeUi(
    val id: String,
    val name: String,
    val characterCardId: String? = null,
    val dramaticRole: String = "",
    val avatarPath: String = "",
)

@Stable
data class NetworkEdgeUi(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val label: String,
)

@Stable
data class RelationGroupUi(
    val nodeId: String,
    val name: String,
    val dramaticRole: String,
    val relations: ImmutableList<RelationLineUi>,
)

@Stable
data class RelationLineUi(
    val edgeId: String,
    val counterpartName: String,
    val counterpartNodeId: String,
    val label: String,
)

sealed interface BookCharacterNetworkIntent {
    data object Refresh : BookCharacterNetworkIntent
    data class SelectNode(val nodeId: String?) : BookCharacterNetworkIntent
    data class SetViewMode(val mode: NetworkViewMode) : BookCharacterNetworkIntent
    data object ToggleSimulation : BookCharacterNetworkIntent
    data class SetSimulationEnabled(val enabled: Boolean) : BookCharacterNetworkIntent
    data object OpenIdentifyCharacters : BookCharacterNetworkIntent
}

sealed interface BookCharacterNetworkEffect {
    data class ShowToast(val message: String) : BookCharacterNetworkEffect
    data class OpenCharacterEdit(val characterCardId: String) : BookCharacterNetworkEffect
    data class NavigateToCharacterList(val bookUrl: String) : BookCharacterNetworkEffect
}

/** Build grouped relation rows for the list view. */
fun buildRelationGroups(
    nodes: List<NetworkNodeUi>,
    edges: List<NetworkEdgeUi>,
    selectedNodeId: String?,
): List<RelationGroupUi> {
    val nodeById = nodes.associateBy { it.id }
    val filteredNodes = if (selectedNodeId != null) {
        nodes.filter { it.id == selectedNodeId }
    } else {
        nodes.sortedWith(
            compareBy<NetworkNodeUi> { io.legado.app.domain.model.DramaticRole.sortKey(it.dramaticRole) }
                .thenBy { it.name.lowercase() },
        )
    }
    return filteredNodes.mapNotNull { node ->
        val lines = edges.mapNotNull { edge ->
            val counterpartId = when (node.id) {
                edge.fromNodeId -> edge.toNodeId
                edge.toNodeId -> edge.fromNodeId
                else -> return@mapNotNull null
            }
            val counterpart = nodeById[counterpartId] ?: return@mapNotNull null
            RelationLineUi(
                edgeId = edge.id,
                counterpartName = counterpart.name,
                counterpartNodeId = counterpart.id,
                label = edge.label,
            )
        }.sortedBy { it.label.lowercase() }
        if (lines.isEmpty() && selectedNodeId == null) return@mapNotNull null
        RelationGroupUi(
            nodeId = node.id,
            name = node.name,
            dramaticRole = node.dramaticRole,
            relations = lines.toImmutableList(),
        )
    }
}
