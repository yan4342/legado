package io.legado.app.ui.file

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.utils.openFileUri
import io.legado.app.utils.toastOnUi
import org.koin.androidx.compose.koinViewModel

@Composable
fun FileManageRoute(onBack: () -> Unit) {
    val viewModel = koinViewModel<FileManageViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FileManageEffect.OpenFile -> context.openFileUri(effect.uri)
                is FileManageEffect.ShowToast -> context.toastOnUi(effect.message)
            }
        }
    }

    val isNotAtRoot = state.parentFile != null && state.parentFile != viewModel.rootDoc

    BackHandler(enabled = isNotAtRoot) { viewModel.onIntent(FileManageIntent.UpOneLevel) }

    FileManageScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = {
            if (isNotAtRoot) viewModel.onIntent(FileManageIntent.UpOneLevel) else onBack()
        },
    )
}
