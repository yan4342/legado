package io.legado.app.ui.book.bookmark

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.toastOnUi
import org.koin.androidx.compose.koinViewModel

@Composable
fun AllBookmarkRoute(
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<AllBookmarkViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            when (result.requestCode) {
                1 -> viewModel.exportBookmark(uri)
                2 -> viewModel.exportBookmarkMd(uri)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AllBookmarkEffect.RequestExport -> exportLauncher.launch {
                    requestCode = if (effect.type == ExportType.JSON) 1 else 2
                }

                is AllBookmarkEffect.ShowToast -> context.toastOnUi(effect.message)
            }
        }
    }

    AllBookmarkScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onBack,
    )
}
