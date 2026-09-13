package io.legado.app.ui.rss.source.edit

import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.data.repository.RssSourceRepository
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.rss.source.debug.RssSourceDebugActivity
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.utils.getClipText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showLogSheet
import io.legado.app.utils.showMarkdownSheet
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

class RssSourceEditActivity : BaseComposeActivity(), VariableDialog.Callback {

    private val sourceRepository by inject<RssSourceRepository>()

    @Composable
    override fun Content() {
        val viewModel = koinViewModel<RssSourceEditViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        var menuExpanded by remember { mutableStateOf(false) }
        var showDiscardConfirm by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            viewModel.onIntent(RssSourceEditIntent.Load(intent.getStringExtra("sourceUrl")))
            viewModel.effects.collectLatest { effect ->
                when (effect) {
                    is RssSourceEditEffect.Finish -> {
                        if (effect.sourceUrl.isNotEmpty()) {
                            setResult(RESULT_OK, Intent().putExtra("origin", effect.sourceUrl))
                        }
                        finish()
                    }

                    is RssSourceEditEffect.OpenDebug -> startActivity<RssSourceDebugActivity> {
                        putExtra("key", effect.sourceUrl)
                    }

                    is RssSourceEditEffect.OpenLogin -> startActivity<SourceLoginActivity> {
                        putExtra("type", "rssSource")
                        putExtra("key", effect.sourceUrl)
                    }

                    is RssSourceEditEffect.CopyText -> sendToClip(effect.text)
                    is RssSourceEditEffect.ShareText -> share(effect.text)

                    RssSourceEditEffect.ReadClipboard -> {
                        val text = getClipText()
                        if (text.isNullOrBlank()) {
                            toastOnUi("剪贴板为空")
                        } else {
                            viewModel.onIntent(RssSourceEditIntent.ImportText(text))
                        }
                    }

                    RssSourceEditEffect.ConfirmDiscard -> showDiscardConfirm = true

                    is RssSourceEditEffect.OpenVariable -> openVariable(effect.sourceUrl)
                    is RssSourceEditEffect.ShowLog -> showLogSheet()
                    is RssSourceEditEffect.ShowHelp -> showMarkdownSheet(effect.title, effect.content)
                    is RssSourceEditEffect.ShowMessage -> toastOnUi(effect.message)
                }
            }
        }

        RssSourceEditScreen(
            state = state,
            menuExpanded = menuExpanded,
            onMenuExpandedChange = { menuExpanded = it },
            onIntent = viewModel::onIntent,
        )

        if (showDiscardConfirm) {
            AlertDialog(
                onDismissRequest = { showDiscardConfirm = false },
                title = { Text(stringResource(R.string.exit)) },
                text = { Text(stringResource(R.string.exit_no_save)) },
                confirmButton = {
                    TextButton(onClick = { showDiscardConfirm = false }) {
                        Text(stringResource(R.string.yes))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDiscardConfirm = false
                        viewModel.onIntent(RssSourceEditIntent.DiscardChanges)
                    }) {
                        Text(stringResource(R.string.no))
                    }
                },
            )
        }
    }

    private fun openVariable(sourceUrl: String) {
        lifecycleScope.launch {
            val source = sourceRepository.getByKey(sourceUrl) ?: return@launch
            val comment = source.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")
            val variable = withContext(Dispatchers.IO) { source.getVariable() }
            showDialogFragment(
                VariableDialog(
                    getString(R.string.set_source_variable),
                    source.getKey(),
                    variable,
                    comment
                )
            )
        }
    }

    override fun setVariable(key: String, variable: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            sourceRepository.getByKey(key)?.setVariable(variable)
        }
    }
}
