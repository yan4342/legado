package io.legado.app.ui.book.manga

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.constant.AppConst
import io.legado.app.receiver.NetworkChangedListener
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.info.compose.BookInfoComposeActivity
import io.legado.app.ui.book.read.ReadBookActivity.Companion.RESULT_DELETED
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.browser.WebViewActivity
import io.legado.app.help.book.isImage
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.openUrl
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.toggleSystemBar
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlinx.coroutines.flow.collectLatest
import java.io.File

/**
 * Android compatibility host for the Compose manga reader.
 *
 * The reader surface and interaction state live in [MangaReaderScreen] and
 * [MangaReaderViewModel]. This activity only owns Android activity results,
 * window state and external activity navigation.
 */
class ReadMangaActivity : BaseComposeActivity(imageBg = false) {

    private val readerViewModel by viewModel<MangaReaderViewModel>()
    private val networkChangedListener by lazy { NetworkChangedListener(this) }

    private var isRestoredFromSavedState = false
    private var justInitialized = false

    /**
     * 换源弹窗请求标记：防止 Activity 重建（锁屏/进程回收/旋转）时 LaunchedEffect 重跑
     * 把同一个 ChangeSource 请求再弹一次窗（FragmentManager 会恢复旧 DialogFragment，
     * 再 show 同 tag 会抛 "Fragment already added" 或出现双弹窗）。
     */
    private var changeSourceDialogShown = false

    private val tocActivity = registerForActivityResult(TocActivityResult()) { result ->
        result?.let { (index, chapterPos, _) ->
            readerViewModel.onIntent(MangaReaderIntent.OpenChapter(index, chapterPos))
        }
    }

    private val bookInfoActivity =
        registerForActivityResult(StartActivityContract(BookInfoComposeActivity::class.java)) {
            if (it.resultCode == RESULT_OK) {
                setResult(RESULT_DELETED)
                finish()
            } else {
                readerViewModel.onIntent(MangaReaderIntent.ReloadContent)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        isRestoredFromSavedState = savedInstanceState != null
        super.onCreate(savedInstanceState)
        toggleSystemBar(false)
        justInitialized = true
        initializeReader(intent)
    }

    @Composable
    override fun Content() {
        val state by readerViewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) {
            readerViewModel.effects.collectLatest(::handleEffect)
        }
        MangaReaderScreen(state = state, onIntent = readerViewModel::onIntent)

        // 目录：改用主仓库 TocActivity（原 fork 的 ReaderBookSheetRoute 已弃用）
        LaunchedEffect(state.activeSheet, state.bookUrl) {
            if (state.activeSheet == MangaReaderSheet.Catalog && state.bookUrl.isNotEmpty()) {
                readerViewModel.onIntent(MangaReaderIntent.DismissSheet)
                tocActivity.launch(state.bookUrl)
            }
        }

        // 换源：改用主仓库 View 版 ChangeBookSourceDialog 桥接
        LaunchedEffect(state.activeSheet, state.changeSourceBook) {
            if (state.activeSheet == MangaReaderSheet.ChangeSource) {
                val oldBook = state.changeSourceBook?.toBook()
                if (oldBook != null && !changeSourceDialogShown &&
                    supportFragmentManager.findFragmentByTag(TAG_CHANGE_SOURCE) == null
                ) {
                    changeSourceDialogShown = true
                    val dialog = ChangeBookSourceDialog(oldBook.name, oldBook.author)
                    dialog.changeSourceCallback = object : ChangeBookSourceDialog.CallBack {
                        override val oldBook: io.legado.app.data.entities.Book? = oldBook
                        override fun changeTo(
                            source: io.legado.app.data.entities.BookSource,
                            book: io.legado.app.data.entities.Book,
                            toc: List<io.legado.app.data.entities.BookChapter>,
                        ) {
                            readerViewModel.onIntent(MangaReaderIntent.DismissSheet)
                            if (!book.isImage) {
                                toastOnUi(getString(R.string.manga_reader_source_not_manga))
                                return
                            }
                            readerViewModel.onIntent(MangaReaderIntent.ChangeSourceBook(book, toc))
                        }
                    }
                    dialog.show(supportFragmentManager, TAG_CHANGE_SOURCE)
                    // 取消/关闭对话框时同步清掉 sheet 状态，避免残留 activeSheet
                    // 导致下一次返回键被"静默吞掉"（需连按两次才能退出）
                    supportFragmentManager.executePendingTransactions()
                    dialog.dialog?.setOnDismissListener {
                        changeSourceDialogShown = false
                        readerViewModel.onIntent(MangaReaderIntent.DismissSheet)
                    }
                }
            } else {
                // sheet 已关闭：复位请求标记（下次再开换源允许重新弹窗）
                changeSourceDialogShown = false
            }
        }
    }

    companion object {
        private const val TAG_CHANGE_SOURCE = "changeSource"
    }

    private fun handleEffect(effect: MangaReaderEffect) {
        when (effect) {
            is MangaReaderEffect.Finish -> {
                if (effect.bookshelfChanged) setResult(RESULT_OK)
                finishReader()
            }
            MangaReaderEffect.OpenBookInfo -> openBookInfoActivity()
            is MangaReaderEffect.OpenChapterUrl -> openCurrentChapterUrl(effect.externalBrowser)
            is MangaReaderEffect.SetWindowBrightness -> {
                if (effect.auto) resetWindowToSystemBrightness()
                else updateWindowBrightness(effect.brightness)
            }
            is MangaReaderEffect.SetSystemBarsVisible -> toggleSystemBar(effect.visible)
            is MangaReaderEffect.ShareImage -> shareImage(effect.filePath)
            is MangaReaderEffect.CopyImage -> copyImage(effect.filePath)
        }
    }

    /** 分享单页/双页合成图（JPEG 临时文件，经 FileProvider 授予读权限） */
    private fun shareImage(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            toastOnUi(getString(R.string.manga_reader_action_failed))
            return
        }
        val uri = FileProvider.getUriForFile(this, AppConst.authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    /** 复制图片到剪贴板（ClipData URI，系统相册/聊天窗口可直接粘贴） */
    private fun copyImage(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            toastOnUi(getString(R.string.manga_reader_action_failed))
            return
        }
        val uri = FileProvider.getUriForFile(this, AppConst.authority, file)
        val clip = ClipData.newUri(contentResolver, "manga", uri)
        getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(clip)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initializeReader(intent)
    }

    private fun initializeReader(androidIntent: Intent) {
        readerViewModel.onIntent(
            MangaReaderIntent.Initialize(
                bookUrl = androidIntent.getStringExtra("bookUrl"),
                inBookshelf = androidIntent.getBooleanExtra("inBookshelf", true),
                chapterChanged = androidIntent.getBooleanExtra("chapterChanged", false),
            )
        )
    }

    override fun onResume() {
        super.onResume()
        networkChangedListener.register()
        networkChangedListener.onNetworkChanged = {
            if (NetworkUtils.isAvailable() && !justInitialized) {
                readerViewModel.onIntent(MangaReaderIntent.NetworkAvailable)
            }
        }
        justInitialized = false
        readerViewModel.onIntent(MangaReaderIntent.ResumeSession)
    }

    override fun onPause() {
        readerViewModel.onIntent(MangaReaderIntent.PauseSession)
        networkChangedListener.unRegister()
        // 锁屏/退后台：换源弹窗若并未真实显示（请求状态残留、半途取消未清），
        // 直接清掉 ChangeSource 状态，避免恢复前台后 LaunchedEffect 误弹窗
        if (supportFragmentManager.findFragmentByTag(TAG_CHANGE_SOURCE) == null &&
            readerViewModel.uiState.value.activeSheet == MangaReaderSheet.ChangeSource
        ) {
            readerViewModel.onIntent(MangaReaderIntent.DismissSheet)
        }
        super.onPause()
    }

    private fun finishReader() {
        if (readerViewModel.uiState.value.inBookshelf && !isRestoredFromSavedState) supportFinishAfterTransition()
        else finish()
    }

    private fun openBookInfoActivity() {
        readerViewModel.uiState.value.let {
            if (it.bookUrl.isEmpty()) return
            bookInfoActivity.launch {
                putExtra("name", it.bookName)
                putExtra("author", it.bookAuthor)
                putExtra("bookUrl", it.bookUrl)
            }
        }
    }

    private fun openCurrentChapterUrl(externalBrowser: Boolean) {
        val state = readerViewModel.uiState.value
        val chapterUrl = state.chapterUrl ?: return
        if (externalBrowser) {
            openUrl(chapterUrl)
            return
        }
        startActivity<WebViewActivity> {
            putExtra("title", state.chapterName)
            putExtra("url", chapterUrl)
            putExtra("sourceOrigin", state.sourceUrl)
            putExtra("sourceName", state.sourceName)
            putExtra("sourceType", state.sourceType)
        }
    }

    private fun resetWindowToSystemBrightness() {
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private fun updateWindowBrightness(brightness: Int) {
        window.attributes = window.attributes.apply {
            screenBrightness = (brightness / 255f).coerceIn(0f, 1f)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val settings = readerViewModel.uiState.value.settings
        if (!settings.volumeKeyPage) return super.onKeyDown(keyCode, event)
        val reverse = settings.reverseVolumeKeyPage
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> if (reverse) 1 else -1
            KeyEvent.KEYCODE_VOLUME_DOWN -> if (reverse) -1 else 1
            else -> return super.onKeyDown(keyCode, event)
        }
        readerViewModel.onIntent(MangaReaderIntent.PageStep(direction))
        return true
    }
}
