package io.legado.app.ui.book.source.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.base.BaseComposeActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

class BookSourceDebugActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        val viewModel = koinViewModel<BookSourceDebugViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            viewModel.onIntent(BookSourceDebugIntent.Load(intent.getStringExtra("key")))
            viewModel.effects.collectLatest { effect ->
                when (effect) {
                    is BookSourceDebugEffect.ShowMessage -> toastOnUi(effect.message)
                }
            }
        }

        BookSourceDebugScreen(
            state = state,
            onIntent = viewModel::onIntent,
            onBack = ::finish,
        )
    }
}
