package io.legado.app.ui.book.source.edit

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
import io.legado.app.data.entities.BookSource
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.ui.book.source.debug.BookSourceDebugActivity
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
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

class BookSourceEditActivity : BaseComposeActivity(), VariableDialog.Callback {

    private val sourceRepository by inject<BookSourceRepository>()

    @Composable
    override fun Content() {
        val viewModel = koinViewModel<BookSourceEditViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        var menuExpanded by remember { mutableStateOf(false) }
        var showDiscardConfirm by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            viewModel.onIntent(BookSourceEditIntent.Load(intent.getStringExtra("sourceUrl")))
            viewModel.effects.collectLatest { effect ->
                when (effect) {
                    is BookSourceEditEffect.Finish -> {
                        if (effect.sourceUrl.isNotEmpty()) {
                            setResult(RESULT_OK, Intent().putExtra("origin", effect.sourceUrl))
                        }
                        finish()
                    }

                    is BookSourceEditEffect.OpenDebug -> startActivity<BookSourceDebugActivity> {
                        putExtra("key", effect.sourceUrl)
                    }

                    is BookSourceEditEffect.OpenLogin -> startActivity<SourceLoginActivity> {
                        putExtra("type", "bookSource")
                        putExtra("key", effect.sourceUrl)
                    }

                    is BookSourceEditEffect.OpenSearch ->
                        GSON.fromJsonObject<BookSource>(effect.sourceJson)
                            .getOrNull()?.let { source ->
                                startActivity<SearchActivity> {
                                    putExtra("searchScope", SearchScope(source).toString())
                                }
                            }

                    is BookSourceEditEffect.CopyText -> sendToClip(effect.text)
                    is BookSourceEditEffect.ShareText -> share(effect.text)

                    BookSourceEditEffect.ReadClipboard -> {
                        val text = getClipText()
                        if (text.isNullOrBlank()) {
                            toastOnUi("剪贴板为空")
                        } else {
                            viewModel.onIntent(BookSourceEditIntent.ImportText(text))
                        }
                    }

                    BookSourceEditEffect.ConfirmDiscard -> showDiscardConfirm = true

                    is BookSourceEditEffect.OpenVariable -> openVariable(effect.sourceUrl)
                    is BookSourceEditEffect.OpenVersionHistory -> openVersionHistory(viewModel, effect.sourceUrl)
                    is BookSourceEditEffect.ShowLog -> showLogSheet()
                    is BookSourceEditEffect.ShowHelp -> showMarkdownSheet(effect.title, effect.content)
                    is BookSourceEditEffect.ShowMessage -> toastOnUi(effect.message)
                }
            }
        }

        BookSourceEditScreen(
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
                        viewModel.onIntent(BookSourceEditIntent.DiscardChanges)
                    }) {
                        Text(stringResource(R.string.no))
                    }
                },
            )
        }
    }

    private fun openVariable(sourceUrl: String) {
        lifecycleScope.launch {
            val source = sourceRepository.getBookSource(sourceUrl) ?: return@launch
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

    private fun openVersionHistory(
        viewModel: BookSourceEditViewModel,
        sourceUrl: String,
    ) {
        lifecycleScope.launch {
            val versions = withContext(Dispatchers.IO) {
                viewModel.listAiVersions(sourceUrl)
            }
            showDialogFragment(
                createBookSourceVersionHistoryDialog(
                    versions = versions,
                    onRestore = { versionId ->
                        viewModel.restoreAiVersion(versionId) { /* state 已在 VM 内更新 */ }
                    },
                    loadDiff = { versionId -> viewModel.loadAiVersionDiff(versionId) },
                )
            )
        }
    }

    override fun setVariable(key: String, variable: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            sourceRepository.getBookSource(key)?.setVariable(variable)
        }
    }
}
