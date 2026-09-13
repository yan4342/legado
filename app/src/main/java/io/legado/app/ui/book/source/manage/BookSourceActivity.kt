package io.legado.app.ui.book.source.manage

import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.model.CheckSource
import io.legado.app.model.Debug
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.ui.book.source.debug.BookSourceDebugActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.share
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

/** 书源管理界面（Compose） */
class BookSourceActivity : BaseComposeActivity() {

    private val repository by inject<io.legado.app.data.repository.BookSourceRepository>()

    @Composable
    override fun Content() {
        val viewModel = koinViewModel<BookSourceViewModel>()
        val scope = rememberCoroutineScope()
        var pendingExportJson by remember { mutableStateOf<String?>(null) }

        val importDocument =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let { showDialogFragment(ImportBookSourceDialog(it.toString())) }
            }
        val exportFile =
            rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                val json = pendingExportJson
                pendingExportJson = null
                if (uri != null && json != null) {
                    scope.launch {
                        runCatching {
                            contentResolver.openOutputStream(uri)?.use { outputStream ->
                                outputStream.bufferedWriter().use { writer ->
                                    writer.write(json)
                                    writer.flush()
                                }
                            }
                        }.onSuccess {
                            toastOnUi(R.string.export_success)
                        }.onFailure {
                            toastOnUi(it.localizedMessage)
                        }
                    }
                }
            }

        BookSourceRouteScreen(
            viewModel = viewModel,
            onBackClick = ::finish,
            onAddSource = { startActivity<BookSourceEditActivity>() },
            onEditSource = { sourceUrl ->
                startActivity<BookSourceEditActivity> {
                    putExtra("sourceUrl", sourceUrl)
                }
            },
            onLoginSource = { sourceUrl ->
                startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", sourceUrl)
                }
            },
            onSearchSource = { sourceUrl ->
                scope.launch {
                    repository.getBookSourcePart(sourceUrl)?.let { source ->
                        startActivity<SearchActivity> {
                            putExtra("searchScope", SearchScope(source).toString())
                        }
                    }
                }
            },
            onDebugSource = { sourceUrl ->
                startActivity<BookSourceDebugActivity> {
                    putExtra("key", sourceUrl)
                }
            },
            onImportLocal = {
                importDocument.launch(
                    arrayOf(
                        "application/json",
                        "text/plain",
                        "text/*"
                    )
                )
            },
            onImportOnline = { text -> showDialogFragment(ImportBookSourceDialog(text)) },
            onExportSelected = { json ->
                pendingExportJson = json
                exportFile.launch("bookSource_${System.currentTimeMillis()}.json")
            },
            onShareSelected = { json -> share(json) },
            onCheckSelected = { parts ->
                keepScreenOn(true)
                CheckSource.start(this@BookSourceActivity, parts)
            },
            onShowHelp = { showHelp("SourceMBookHelp") },
        )
    }

    private fun keepScreenOn(on: Boolean) {
        val isScreenOn =
            (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        if (on == isScreenOn) return
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!Debug.isChecking) {
            Debug.debugMessageMap.clear()
        }
    }
}
