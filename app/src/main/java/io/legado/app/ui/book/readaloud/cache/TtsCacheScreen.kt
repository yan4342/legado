package io.legado.app.ui.book.readaloud.cache

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun TtsCacheRouteScreen(
    onBackClick: () -> Unit,
    viewModel: TtsCacheViewModel = koinViewModel(),
) {
    LegadoTheme {
        TtsCacheScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBackClick = onBackClick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsCacheScreen(
    state: TtsCacheUiState,
    onIntent: (TtsCacheIntent) -> Unit,
    effects: kotlinx.coroutines.flow.Flow<TtsCacheEffect>,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is TtsCacheEffect.ShowToast -> context.toastOnUi(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tts_cache_manage)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.tts_cache_total),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = buildString {
                    append(TtsCacheViewModel.formatSize(state.totalSizeBytes))
                    append(" · ")
                    append(context.getString(R.string.tts_cache_file_count, state.files.size))
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (state.files.isEmpty() && !state.loading) {
                Text(
                    text = stringResource(R.string.tts_cache_empty_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { onIntent(TtsCacheIntent.ShowClearAllDialog) },
                enabled = state.files.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                Text(
                    text = stringResource(R.string.clear_all_tts_cache),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }

    if (state.activeDialog == TtsCacheDialog.ClearAll) {
        AlertDialog(
            onDismissRequest = { onIntent(TtsCacheIntent.DismissDialog) },
            title = { Text(stringResource(R.string.clear_all_tts_cache)) },
            text = { Text(stringResource(R.string.sure_del)) },
            confirmButton = {
                TextButton(onClick = { onIntent(TtsCacheIntent.ClearAll) }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(TtsCacheIntent.DismissDialog) }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
