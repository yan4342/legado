package io.legado.app.ui.book.info.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.info.compose.BookDetailCharacterEditOverlay
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.runtime.LaunchedEffect

@Composable
fun BookCharacterNetworkRouteScreen(
    bookUrl: String,
    focusCharacterId: String? = null,
    onBack: () -> Unit,
    onNavigateToCharacterList: (String) -> Unit = {},
    viewModel: BookCharacterNetworkViewModel = koinViewModel {
        parametersOf(bookUrl, focusCharacterId)
    },
) {
    LegadoTheme {
        BookCharacterNetworkScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = onBack,
            onNavigateToCharacterList = onNavigateToCharacterList,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookCharacterNetworkScreen(
    state: BookCharacterNetworkUiState,
    onIntent: (BookCharacterNetworkIntent) -> Unit,
    effects: Flow<BookCharacterNetworkEffect>,
    onBack: () -> Unit,
    onNavigateToCharacterList: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var editingCharacterId by remember { mutableStateOf<String?>(null) }
    var fitTrigger by remember { mutableIntStateOf(0) }
    var centerHubTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is BookCharacterNetworkEffect.ShowToast -> context.toastOnUi(effect.message)
                is BookCharacterNetworkEffect.OpenCharacterEdit -> {
                    editingCharacterId = effect.characterCardId
                }
                is BookCharacterNetworkEffect.NavigateToCharacterList -> {
                    onNavigateToCharacterList(effect.bookUrl)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.character_network)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { onIntent(BookCharacterNetworkIntent.Refresh) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading && state.nodes.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.nodes.isEmpty() -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.emptyHint.ifBlank {
                            stringResource(R.string.character_network_empty)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.character_network_empty_hint_format),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    FilledTonalButton(
                        onClick = { onIntent(BookCharacterNetworkIntent.OpenIdentifyCharacters) },
                        modifier = Modifier.padding(top = 20.dp),
                    ) {
                        Text(stringResource(R.string.character_network_identify_characters))
                    }
                }
            }

            else -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        SegmentedButton(
                            selected = state.viewMode == NetworkViewMode.Graph,
                            onClick = {
                                onIntent(BookCharacterNetworkIntent.SetViewMode(NetworkViewMode.Graph))
                            },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) {
                            Text(stringResource(R.string.character_network_view_graph))
                        }
                        SegmentedButton(
                            selected = state.viewMode == NetworkViewMode.List,
                            onClick = {
                                onIntent(BookCharacterNetworkIntent.SetViewMode(NetworkViewMode.List))
                            },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) {
                            Text(stringResource(R.string.character_network_view_list))
                        }
                    }

                    when (state.viewMode) {
                        NetworkViewMode.Graph -> {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            ) {
                                ForceDirectedGraph(
                                    nodes = state.nodes,
                                    edges = state.edges,
                                    hubNodeId = state.hubNodeId,
                                    selectedNodeId = state.selectedNodeId,
                                    simulationEnabled = state.simulationEnabled,
                                    onSimulationSettled = {
                                        onIntent(BookCharacterNetworkIntent.SetSimulationEnabled(false))
                                    },
                                    onSelectNode = { id ->
                                        onIntent(BookCharacterNetworkIntent.SelectNode(id))
                                    },
                                    onNodeDragStart = {
                                        if (!state.simulationEnabled) {
                                            onIntent(BookCharacterNetworkIntent.SetSimulationEnabled(true))
                                        }
                                    },
                                    fitTrigger = fitTrigger,
                                    centerHubTrigger = centerHubTrigger,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                                )
                                GraphControlBar(
                                    simulationEnabled = state.simulationEnabled,
                                    showSimulationToggle = !AppConfig.isEInkMode,
                                    onFit = { fitTrigger += 1 },
                                    onToggleSimulation = {
                                        onIntent(BookCharacterNetworkIntent.ToggleSimulation)
                                    },
                                    onCenterHub = { centerHubTrigger += 1 },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp),
                                )
                            }
                        }
                        NetworkViewMode.List -> {
                            GroupedRelationList(
                                nodes = state.nodes,
                                edges = state.edges,
                                selectedNodeId = state.selectedNodeId,
                                onSelectNode = { id ->
                                    onIntent(BookCharacterNetworkIntent.SelectNode(id))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    BookDetailCharacterEditOverlay(
        characterId = editingCharacterId,
        bookUrl = state.bookUrl,
        bookName = "",
        bookAuthor = "",
        onDismiss = { editingCharacterId = null },
    )
}
