package io.legado.app.ui.book.info.compose

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.addType
import io.legado.app.help.book.getRemoteUrl
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.book.isWebFile
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.ui.book.changecover.ChangeCoverDialog
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.group.GroupSelectDialog
import io.legado.app.ui.book.info.BookInfoViewModel
import io.legado.app.ui.book.info.edit.BookInfoEditActivity
import io.legado.app.ui.book.manga.ReadMangaActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadBookActivity.Companion.RESULT_DELETED
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.GSON
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.dpToPx
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.openFileUri
import io.legado.app.utils.sendToClip
import io.legado.app.utils.shareWithQr
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 书籍详情页 — Compose 版。
 *
 * 使用 BookInfoViewModel（与原 BookInfoActivity 共享），
 * 通过 setContent { BookDetailScreen } 渲染 UI。
 * 保留所有原有的业务逻辑回调。
 */
class BookInfoComposeActivity :
    AppCompatActivity(),
    GroupSelectDialog.CallBack,
    ChangeBookSourceDialog.CallBack,
    ChangeCoverDialog.CallBack,
    VariableDialog.Callback {

    val viewModel by viewModels<BookInfoViewModel>()

    private val tocActivityResult = registerForActivityResult(TocActivityResult()) {
        it?.let {
            viewModel.getBook(false)?.let { book ->
                lifecycleScope.launch {
                    withContext(IO) {
                        book.durChapterIndex = it.first
                        book.durChapterPos = it.second
                        appDb.bookDao.update(book)
                    }
                    startReadActivity(book)
                }
            }
        } ?: let {
            if (!viewModel.inBookshelf) {
                viewModel.delBook()
            }
        }
    }
    private val localBookTreeSelect = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { treeUri ->
            AppConfig.defaultBookTreeUri = treeUri.toString()
        }
    }
    private val readBookResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.upBook(intent)
        when (it.resultCode) {
            RESULT_OK -> viewModel.inBookshelf = true
            RESULT_DELETED -> {
                setResult(RESULT_OK)
                finish()
            }
        }
    }
    private val infoEditResult = registerForActivityResult(
        StartActivityContract(BookInfoEditActivity::class.java)
    ) {
        if (it.resultCode == RESULT_OK) {
            viewModel.upEditBook()
        }
    }
    private val editSourceResult = registerForActivityResult(
        StartActivityContract(BookSourceEditActivity::class.java)
    ) {
        if (it.resultCode == RESULT_CANCELED) return@registerForActivityResult
        viewModel.getBook()?.let { book ->
            viewModel.bookSource = appDb.bookSourceDao.getBookSource(book.origin)
            viewModel.refreshBook(book)
        }
    }
    private val waitDialog by lazy { WaitDialog(this) }
    private val book get() = viewModel.getBook(false)

    @SuppressLint("PrivateResource")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 避免白屏：在 setContent 之前设置窗口背景色与主题一致
        window.decorView.setBackgroundColor(
            io.legado.app.lib.theme.ThemeStore.backgroundColor(this)
        )

        viewModel.initData(intent)
        viewModel.waitDialogData.observe(this) { upWaitDialogStatus(it) }

        setContent {
            LegadoTheme {
                var currentBook by remember { mutableStateOf(viewModel.bookData.value) }
                var chapterList by remember { mutableStateOf(viewModel.chapterListData.value) }

                DisposableEffect(Unit) {
                    val bookObserver = Observer<Book?> { currentBook = it }
                    val chapterObserver = Observer<List<BookChapter>?> { chapterList = it }
                    viewModel.bookData.observe(this@BookInfoComposeActivity, bookObserver)
                    viewModel.chapterListData.observe(this@BookInfoComposeActivity, chapterObserver)
                    onDispose {
                        viewModel.bookData.removeObserver(bookObserver)
                        viewModel.chapterListData.removeObserver(chapterObserver)
                    }
                }

                if (currentBook != null) {
                    val book = currentBook!!
                    var canUpdateState by remember(book) { mutableStateOf(book.canUpdate) }
                    var splitLongChapterState by remember(book) { mutableStateOf(book.getSplitLongChapter()) }
                    var inBookshelfState by remember { mutableStateOf(viewModel.inBookshelf) }
                    BookDetailScreen(
                        book = book,
                        latestChapterTitle = book.latestChapterTitle,
                        totalChapterNum = book.totalChapterNum,
                        onBack = { finish() },
                        onReadClick = {
                            if (book.isWebFile) {
                                showWebFileDownloadAlert { readBook(it) }
                            } else {
                                readBook(book)
                            }
                        },
                        onShelfClick = {
                            if (book.isWebFile) {
                                showWebFileDownloadAlert()
                            } else {
                                viewModel.addToBookshelf {
                                    inBookshelfState = true
                                }
                            }
                        },
                        inBookshelf = inBookshelfState,
                        onTocClick = {
                            if (chapterList.isNullOrEmpty()) {
                                toastOnUi(R.string.chapter_list_empty)
                            } else {
                                openChapterList()
                            }
                        },
                        onEditClick = {
                            infoEditResult.launch {
                                putExtra("bookUrl", book.bookUrl)
                            }
                        },
                        onMenuAction = { action ->
                            when (action) {
                                MENU_CAN_UPDATE -> {
                                    book.canUpdate = !book.canUpdate
                                    canUpdateState = book.canUpdate
                                }
                                MENU_SPLIT_LONG_CHAPTER -> {
                                    book.setSplitLongChapter(!book.getSplitLongChapter())
                                    splitLongChapterState = book.getSplitLongChapter()
                                }
                            }
                            handleMenuAction(action, book)
                        },
                        canUpdate = canUpdateState,
                        splitLongChapter = splitLongChapterState,
                        isLoginVisible = !viewModel.bookSource?.loginUrl.isNullOrBlank(),
                        isSourceVariableVisible = viewModel.bookSource != null,
                        isBookVariableVisible = viewModel.bookSource != null,
                    )
                }
            }
        }
    }

    private fun handleMenuAction(itemId: Int, book: Book) {
        when (itemId) {
            MENU_EDIT -> infoEditResult.launch {
                putExtra("bookUrl", book.bookUrl)
            }
            MENU_SHARE -> {
                val bookJson = GSON.toJson(book)
                val shareStr = "${book.bookUrl}#$bookJson"
                shareWithQr(shareStr, book.name)
            }
            MENU_REFRESH -> refreshBook()
            MENU_LOGIN -> viewModel.bookSource?.let {
                startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", it.bookSourceUrl)
                }
            }
            MENU_TOP -> viewModel.topBook()
            MENU_SET_SOURCE_VARIABLE -> setSourceVariable()
            MENU_SET_BOOK_VARIABLE -> setBookVariable()
            MENU_COPY_BOOK_URL -> sendToClip(book.bookUrl)
            MENU_COPY_TOC_URL -> book.tocUrl?.let { sendToClip(it) }
            MENU_CAN_UPDATE -> {
                // 状态已在 onMenuAction lambda 中切换，这里只保存
                if (viewModel.inBookshelf) {
                    viewModel.saveBook(book)
                }
            }
            MENU_SPLIT_LONG_CHAPTER -> {
                // 状态已在 onMenuAction lambda 中切换，这里只重新加载
                viewModel.loadBookInfo(book, false)
            }
            MENU_CLEAR_CACHE -> viewModel.clearCache()
            MENU_LOG -> showDialogFragment<AppLogDialog>()
            MENU_UPLOAD -> upLoadBook(book)
            MENU_DELETE -> deleteBook()
            MENU_CHANGE_SOURCE -> {
                showDialogFragment(
                    ChangeBookSourceDialog(book.name, book.author)
                )
            }
            MENU_GROUP -> {
                showDialogFragment(
                    GroupSelectDialog(book.group)
                )
            }
        }
    }

    private fun refreshBook() {
        viewModel.getBook()?.let {
            viewModel.refreshBook(it)
        }
    }

    private fun setSourceVariable() {
        lifecycleScope.launch {
            val source = viewModel.bookSource
            if (source == null) {
                toastOnUi("书源不存在")
                return@launch
            }
            val comment = source.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")
            val variable = withContext(IO) { source.getVariable() }
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

    private fun setBookVariable() {
        lifecycleScope.launch {
            val source = viewModel.bookSource
            if (source == null) {
                toastOnUi("书源不存在")
                return@launch
            }
            val book = viewModel.getBook() ?: return@launch
            val variable = withContext(IO) { book.getCustomVariable() }
            val comment = source.getDisplayVariableComment(
                """书籍变量可在js中通过book.getVariable("custom")获取"""
            )
            showDialogFragment(
                VariableDialog(
                    getString(R.string.set_book_variable),
                    book.bookUrl,
                    variable,
                    comment
                )
            )
        }
    }

    private fun upLoadBook(book: Book) {
        lifecycleScope.launch {
            waitDialog.setText("上传中.....")
            waitDialog.show()
            try {
                val bookWebDav = AppWebDav.defaultBookWebDav
                if (bookWebDav == null) {
                    toastOnUi("未配置webDav")
                    return@launch
                }
                bookWebDav.upload(book)
                book.lastCheckTime = System.currentTimeMillis()
                viewModel.saveBook(book)
            } catch (e: Exception) {
                toastOnUi(e.localizedMessage)
            } finally {
                waitDialog.dismiss()
            }
        }
    }

    private fun readBook(book: Book) {
        if (!viewModel.inBookshelf) {
            book.addType(BookType.notShelf)
            viewModel.saveBook(book) {
                viewModel.saveChapterList {
                    startReadActivity(book)
                }
            }
        } else {
            viewModel.saveBook(book) {
                startReadActivity(book)
            }
        }
    }

    private fun startReadActivity(book: Book) {
        when {
            book.isAudio -> readBookResult.launch(
                Intent(this, AudioPlayActivity::class.java)
                    .putExtra("bookUrl", book.bookUrl)
                    .putExtra("inBookshelf", viewModel.inBookshelf)
            )
            else -> readBookResult.launch(
                Intent(
                    this,
                    if (!book.isLocal && book.isImage && AppConfig.showMangaUi)
                        ReadMangaActivity::class.java
                    else ReadBookActivity::class.java
                ).putExtra("bookUrl", book.bookUrl)
                    .putExtra("inBookshelf", viewModel.inBookshelf)
            )
        }
    }

    private fun openChapterList() {
        viewModel.getBook()?.let {
            tocActivityResult.launch(it.bookUrl)
        }
    }

    @SuppressLint("InflateParams")
    private fun deleteBook() {
        viewModel.getBook()?.let {
            if (LocalConfig.bookInfoDeleteAlert) {
                alert(
                    titleResource = R.string.draw,
                    messageResource = R.string.sure_del
                ) {
                    var checkBox: CheckBox? = null
                    if (it.isLocal) {
                        checkBox = CheckBox(this@BookInfoComposeActivity).apply {
                            setText(R.string.delete_book_file)
                            isChecked = LocalConfig.deleteBookOriginal
                        }
                        val view = LinearLayout(this@BookInfoComposeActivity).apply {
                            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
                            addView(checkBox)
                        }
                        customView { view }
                    }
                    yesButton {
                        if (checkBox != null) {
                            LocalConfig.deleteBookOriginal = checkBox.isChecked
                        }
                        viewModel.delBook(LocalConfig.deleteBookOriginal) {
                            setResult(RESULT_OK)
                            finish()
                        }
                    }
                    noButton()
                }
            } else {
                viewModel.delBook(LocalConfig.deleteBookOriginal) {
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
    }

    private fun showWebFileDownloadAlert(
        onClick: ((Book) -> Unit)? = null,
    ) {
        val webFiles = viewModel.webFiles
        if (webFiles.isEmpty()) {
            toastOnUi("Unexpected webFileData")
            return
        }
        selector(R.string.download_and_import_file, webFiles) { _, webFile, _ ->
            if (webFile.isSupported) {
                viewModel.importOrDownloadWebFile<Book>(webFile) { onClick?.invoke(it) }
            } else if (webFile.isSupportDecompress) {
                viewModel.importOrDownloadWebFile<Uri>(webFile) { uri ->
                    viewModel.getArchiveFilesName(uri) { fileNames ->
                        if (fileNames.size == 1) {
                            viewModel.importArchiveBook(uri, fileNames[0]) {
                                onClick?.invoke(it)
                            }
                        } else {
                            selector(R.string.import_select_book, fileNames) { _, name, _ ->
                                viewModel.importArchiveBook(uri, name) { onClick?.invoke(it) }
                            }
                        }
                    }
                }
            } else {
                alert(
                    title = getString(R.string.draw),
                    message = getString(R.string.file_not_supported, webFile.name)
                ) {
                    neutralButton(R.string.open_fun) {
                        viewModel.importOrDownloadWebFile<Uri>(webFile) {
                            openFileUri(it, "*/*")
                        }
                    }
                    noButton()
                }
            }
        }
    }

    override val oldBook: Book?
        get() = viewModel.bookData.value

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        viewModel.changeTo(source, book, toc)
    }

    override fun coverChangeTo(coverUrl: String) {
        viewModel.bookData.value?.let { book ->
            book.customCoverUrl = coverUrl
            if (viewModel.inBookshelf) {
                viewModel.saveBook(book)
            }
        }
    }

    override fun upGroup(requestCode: Int, groupId: Long) {
        viewModel.getBook()?.let { book ->
            book.group = groupId
            if (viewModel.inBookshelf) {
                viewModel.saveBook(book)
            } else if (groupId > 0) {
                viewModel.addToBookshelf {}
            }
        }
    }

    override fun setVariable(key: String, variable: String?) {
        when (key) {
            viewModel.bookSource?.getKey() -> viewModel.bookSource?.setVariable(variable)
            viewModel.bookData.value?.bookUrl -> viewModel.bookData.value?.let {
                it.putCustomVariable(variable)
                if (viewModel.inBookshelf) {
                    viewModel.saveBook(it)
                }
            }
        }
    }

    private fun upWaitDialogStatus(isShow: Boolean) {
        if (isShow) {
            waitDialog.run {
                setText("Loading.....")
                show()
            }
        } else {
            waitDialog.dismiss()
        }
    }

}
