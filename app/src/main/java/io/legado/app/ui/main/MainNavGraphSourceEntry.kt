package io.legado.app.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.legado.app.R
import io.legado.app.model.CheckSource
import io.legado.app.model.Debug
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.ui.book.source.debug.BookSourceDebugActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.manage.BookSourceRouteScreen
import io.legado.app.ui.book.source.manage.BookSourceViewModel
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.share
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 书源管理（阶段 2 收编）：主栈路由承载，BookSourceActivity 保留薄壳复用同一内容。
 * 全部回调经由 LocalContext（主栈下即 MainActivity，AppCompatActivity）。
 */
@Composable
internal fun BookSourceManageEntry(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val viewModel: BookSourceViewModel = koinViewModel()
    val scope = rememberCoroutineScope()
    val repository: BookSourceRepository = remember {
        org.koin.java.KoinJavaComponent.get(BookSourceRepository::class.java)
    }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }

    val importDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { (context as? AppCompatActivity)?.showDialogFragment(ImportBookSourceDialog(it.toString())) }
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
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.bufferedWriter().use { writer ->
                                writer.write(json)
                                writer.flush()
                            }
                        }
                    }.onSuccess {
                        context.toastOnUi(R.string.export_success)
                    }.onFailure {
                        context.toastOnUi(it.localizedMessage)
                    }
                }
            }
        }

    BookSourceRouteScreen(
        viewModel = viewModel,
        onBackClick = onBackClick,
        onAddSource = { context.startActivity<BookSourceEditActivity>() },
        onEditSource = { sourceUrl ->
            context.startActivity<BookSourceEditActivity> {
                putExtra("sourceUrl", sourceUrl)
            }
        },
        onLoginSource = { sourceUrl ->
            context.startActivity<SourceLoginActivity> {
                putExtra("type", "bookSource")
                putExtra("key", sourceUrl)
            }
        },
        onSearchSource = { sourceUrl ->
            scope.launch {
                repository.getBookSourcePart(sourceUrl)?.let { source ->
                    context.startActivity<SearchActivity> {
                        putExtra("searchScope", SearchScope(source).toString())
                    }
                }
            }
        },
        onDebugSource = { sourceUrl ->
            context.startActivity<BookSourceDebugActivity> {
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
        onImportOnline = { text ->
            (context as? AppCompatActivity)?.showDialogFragment(ImportBookSourceDialog(text))
        },
        onExportSelected = { json ->
            pendingExportJson = json
            exportFile.launch("bookSource_" + System.currentTimeMillis() + ".json")
        },
        onShareSelected = { json -> context.share(json) },
        onCheckSelected = { parts ->
            keepScreenOn(activity, true)
            CheckSource.start(context, parts)
        },
        onShowHelp = { (context as? AppCompatActivity)?.showHelp("SourceMBookHelp") },
    )
}

private fun keepScreenOn(activity: AppCompatActivity?, on: Boolean) {
    val window = activity?.window ?: return
    val isScreenOn =
        (window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
    if (on == isScreenOn) return
    if (on) {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
