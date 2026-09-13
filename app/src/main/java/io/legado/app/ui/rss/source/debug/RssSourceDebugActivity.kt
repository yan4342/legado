package io.legado.app.ui.rss.source.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.base.BaseComposeActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

class RssSourceDebugActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        val viewModel = koinViewModel<RssSourceDebugViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            viewModel.onIntent(RssSourceDebugIntent.Load(intent.getStringExtra("key")))
            viewModel.effects.collectLatest { effect ->
                when (effect) {
                    is RssSourceDebugEffect.ShowMessage -> toastOnUi(effect.message)
                }
            }
        }

        RssSourceDebugScreen(
            state = state,
            onIntent = viewModel::onIntent,
            onBack = ::finish,
        )
    }
}
