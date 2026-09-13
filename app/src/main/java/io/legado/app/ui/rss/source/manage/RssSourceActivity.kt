package io.legado.app.ui.rss.source.manage

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
import io.legado.app.ui.association.ImportRssSourceDialog
import io.legado.app.ui.rss.source.debug.RssSourceDebugActivity
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.share
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** 订阅源管理界面（Compose） */
class RssSourceActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        val viewModel = koinViewModel<RssSourceViewModel>()
        val scope = rememberCoroutineScope()
        var pendingExportJson by remember { mutableStateOf<String?>(null) }

        val importDocument =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let { showDialogFragment(ImportRssSourceDialog(it.toString())) }
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

        RssSourceRouteScreen(
            viewModel = viewModel,
            onBackClick = ::finish,
            onAddSource = { startActivity<RssSourceEditActivity>() },
            onEditSource = { sourceUrl ->
                startActivity<RssSourceEditActivity> {
                    putExtra("sourceUrl", sourceUrl)
                }
            },
            onDebugSource = { sourceUrl ->
                startActivity<RssSourceDebugActivity> {
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
            onImportOnline = { text -> showDialogFragment(ImportRssSourceDialog(text)) },
            onExportSelected = { json ->
                pendingExportJson = json
                exportFile.launch("rssSource_${System.currentTimeMillis()}.json")
            },
            onShareSelected = { json -> share(json) },
            onShowHelp = { showHelp("SourceMRssHelp") },
        )
    }
}
