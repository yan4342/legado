package io.legado.app.ui.main.bookshelf.compose

import android.content.Intent
import android.view.LayoutInflater
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.indices
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import org.koin.androidx.compose.koinViewModel
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.utils.eventObservable
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.service.WebService
import io.legado.app.ui.book.cache.CacheActivity
import io.legado.app.ui.book.group.GroupManageDialog
import io.legado.app.ui.book.import.local.ImportBookActivity
import io.legado.app.ui.book.import.remote.RemoteBookActivity
import io.legado.app.ui.book.manage.BookshelfManageActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.main.MainRoute
import io.legado.app.ui.main.MainRouteBookInfo
import io.legado.app.ui.main.MainRouteSearch
import io.legado.app.ui.main.MainViewModel
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.main.bookshelf.BookshelfViewModel
import io.legado.app.utils.postEvent
import io.legado.app.utils.readText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showLogSheet
import io.legado.app.utils.showM3EditDialog
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BookshelfTab(
    onNavigateToRoute: (MainRoute) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? AppCompatActivity
    val mainViewModel: MainViewModel = koinViewModel()
    val bookshelfViewModel: BookshelfViewModel = koinViewModel()

    // ---- 版本号 Flow，驱动 Compose 重组 ----
    val sortVersionFlow = remember { MutableStateFlow(0) }
    val refreshVersionFlow = remember { MutableStateFlow(0) }
    val gridVersionFlow = remember { MutableStateFlow(0) }
    val groupStyleVersionFlow = remember { MutableStateFlow(0) }
    val lastUpdateVersionFlow = remember { MutableStateFlow(0) }
    val isRefreshing = remember { mutableStateOf(false) }

    val sortVersion by sortVersionFlow.collectAsState()
    val refreshVersion by refreshVersionFlow.collectAsState()
    val gridVersion by gridVersionFlow.collectAsState()
    val groupStyleVersion by groupStyleVersionFlow.collectAsState()
    val lastUpdateVersion by lastUpdateVersionFlow.collectAsState()

    var selectedGroupId by remember { mutableStateOf(-1L) }

    // ---- 从 AppConfig 派生配置（通过 version 触发 recompute） ----
    val gridColumns = remember(gridVersion) {
        when (AppConfig.bookshelfLayout) {
            1 -> 3; 2 -> 4; 3 -> 5; 4 -> 6; else -> 0
        }
    }
    val bookGroupStyle = remember(groupStyleVersion) { AppConfig.bookGroupStyle }
    val showUnread = remember(refreshVersion) { AppConfig.showUnread }
    val showLastUpdateTime = remember(refreshVersion) { AppConfig.showLastUpdateTime }
    val showFastScroller = remember(refreshVersion) { AppConfig.showBookshelfFastScroller }

    // ---- 数据源：从 Activity 级 BookshelfViewModel 订阅，entry 重建时数据不丢失 ----
    // data class BookshelfData 包了 version 字段，绕开 Book.equals 只比 bookUrl 导致 MutableStateFlow 拦截更新的问题
    val shelfData by bookshelfViewModel.dataFlow.collectAsState()
    val books: List<Book> = shelfData.books
    val groups: List<BookGroup> = shelfData.groups

    // ---- 排序 ----
    val sortedBooks = remember(books, sortVersion) {
        when (AppConfig.bookshelfSort) {
            0 -> books.sortedByDescending { it.durChapterTime }
            1 -> books.sortedByDescending { it.latestChapterTime }
            2 -> books.sortedBy { it.name }
            3 -> books.sortedBy { it.order }
            else -> books
        }
    }

    // ---- EventBus: 响应外部刷新事件 ----
    // Room flow 自动推送到 dataFlow，EventBus 只需 bump refreshVersion 驱动 key 重建
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = Observer<String> { refreshVersionFlow.value++ }
        eventObservable<String>(EventBus.BOOKSHELF_REFRESH).observe(lifecycleOwner, observer)
        onDispose { eventObservable<String>(EventBus.BOOKSHELF_REFRESH).removeObserver(observer) }
    }

    // ---- 每30秒 + resume 时刷新 lastUpdateVersion ----
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            lastUpdateVersionFlow.value++
            while (isActive) {
                delay(30_000)
                lastUpdateVersionFlow.value++
            }
        }
    }

    // ---- 辅助函数 ----
    fun currentGroupBooks(): List<Book> = if (bookGroupStyle == 0) {
        filterBooksForGroup(sortedBooks, selectedGroupId, groups)
    } else {
        sortedBooks
    }

    fun enableRefresh(): Boolean = when {
        currentGroupBooks().isEmpty() -> false
        selectedGroupId == BookGroup.IdAll || selectedGroupId == BookGroup.IdRoot -> true
        else -> groups.firstOrNull { it.groupId == selectedGroupId }?.enableRefresh != false
    }

    // ---- 文件导出 launcher ----
    val exportLauncher = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            context.sendToClip(uri.toString())
            activity?.showM3EditDialog(
                titleRes = R.string.export_success,
                initialValue = uri.toString(),
                hintRes = R.string.path,
                onConfirm = { context.sendToClip(uri.toString()) },
            )
        }
    }

    // ---- 文件导入 launcher ----
    val importLauncher = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        kotlin.runCatching {
            result.uri?.let { uri ->
                val text = uri.readText(context)
                bookshelfViewModel.importBookshelf(text, selectedGroupId)
            }
        }.onFailure {
            context.toastOnUi(it.localizedMessage ?: "ERROR")
        }
    }

    // ---- 阅读器返回结果 launcher ----
    val readBookLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { }

    key(refreshVersion) {
    BookshelfScreen(
        books = sortedBooks,
        groups = groups,
        selectedGroupId = selectedGroupId,
        gridColumns = gridColumns,
        bookGroupStyle = bookGroupStyle,
        onGroupSelected = { selectedGroupId = it },
        onConfigBookshelf = {
            val layoutInflater = LayoutInflater.from(context)
            context.alert(titleResource = R.string.bookshelf_layout) {
                var bookshelfLayout = AppConfig.bookshelfLayout
                var bookshelfSort = AppConfig.bookshelfSort
                val b = io.legado.app.databinding.DialogBookshelfConfigBinding.inflate(layoutInflater)
                if (AppConfig.bookGroupStyle !in 0 until b.spGroupStyle.count) {
                    AppConfig.bookGroupStyle = 0
                }
                if (bookshelfLayout !in b.rgLayout.indices) {
                    bookshelfLayout = 0
                    AppConfig.bookshelfLayout = 0
                }
                if (bookshelfSort !in b.rgSort.indices) {
                    bookshelfSort = 0
                    AppConfig.bookshelfSort = 0
                }
                b.spGroupStyle.setSelection(AppConfig.bookGroupStyle)
                b.swShowUnread.isChecked = AppConfig.showUnread
                b.swShowLastUpdateTime.isChecked = AppConfig.showLastUpdateTime
                b.swShowWaitUpBooks.isChecked = AppConfig.showWaitUpCount
                b.swShowBookshelfFastScroller.isChecked = AppConfig.showBookshelfFastScroller
                if (bookshelfLayout in b.rgLayout.indices) {
                    b.rgLayout.check(b.rgLayout.getChildAt(bookshelfLayout).id)
                }
                if (bookshelfSort in b.rgSort.indices) {
                    b.rgSort.check(b.rgSort.getChildAt(bookshelfSort).id)
                }
                customView { b.root }
                okButton {
                    val ab = b
                    fun getSortIndex(): Int {
                        for (i in 0 until ab.rgSort.childCount) {
                            if (ab.rgSort.checkedRadioButtonId == ab.rgSort.getChildAt(i).id) return i
                        }
                        return -1
                    }
                    fun getLayoutIndex(): Int {
                        for (i in 0 until ab.rgLayout.childCount) {
                            if (ab.rgLayout.checkedRadioButtonId == ab.rgLayout.getChildAt(i).id) return i
                        }
                        return -1
                    }
                    val newSortIndex = getSortIndex()
                    val newLayoutIndex = getLayoutIndex()
                    val newGroupPos = ab.spGroupStyle.selectedItemPosition
                    val newShowUnread = ab.swShowUnread.isChecked
                    val newShowLastUpdateTime = ab.swShowLastUpdateTime.isChecked
                    val newShowWaitUpBooks = ab.swShowWaitUpBooks.isChecked
                    val newShowFastScroller = ab.swShowBookshelfFastScroller.isChecked
                    if (AppConfig.bookGroupStyle != newGroupPos) {
                        AppConfig.bookGroupStyle = newGroupPos
                        groupStyleVersionFlow.value++
                    }
                    if (AppConfig.showUnread != newShowUnread) {
                        AppConfig.showUnread = newShowUnread
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                    if (AppConfig.showLastUpdateTime != newShowLastUpdateTime) {
                        AppConfig.showLastUpdateTime = newShowLastUpdateTime
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                    if (AppConfig.showWaitUpCount != newShowWaitUpBooks) {
                        AppConfig.showWaitUpCount = newShowWaitUpBooks
                        mainViewModel.postUpBooksLiveData(true)
                    }
                    if (AppConfig.showBookshelfFastScroller != newShowFastScroller) {
                        AppConfig.showBookshelfFastScroller = newShowFastScroller
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                    if (bookshelfSort != newSortIndex) {
                        AppConfig.bookshelfSort = newSortIndex
                        sortVersionFlow.value++
                    }
                    if (bookshelfLayout != newLayoutIndex) {
                        AppConfig.bookshelfLayout = newLayoutIndex
                        gridVersionFlow.value++
                    }
                }
                cancelButton()
            }
        },
        onBookClick = { book ->
            val intent = Intent(context, ReadBookActivity::class.java).apply {
                putExtra("bookUrl", book.bookUrl)
            }
            readBookLauncher.launch(intent)
        },
        onBookLongClick = { book ->
            onNavigateToRoute(MainRouteBookInfo(
                name = book.name,
                author = book.author,
                bookUrl = book.bookUrl,
                origin = book.origin,
                coverPath = book.getDisplayCover(),
                sharedCoverKey = bookCoverSharedElementKey(book.bookUrl),
            ))
        },
        onBookDelete = { book ->
            appDb.bookDao.delete(book)
        },
        onSearchClick = { onNavigateToRoute(MainRouteSearch(key = null)) },
        onSort = {
            val newSort = (AppConfig.bookshelfSort + 1) % 4
            AppConfig.bookshelfSort = newSort
            sortVersionFlow.value++
        },
        onUpdateToc = {
            mainViewModel.upToc(currentGroupBooks())
        },
        onAddLocal = {
            context.startActivity<ImportBookActivity>()
        },
        onAddRemote = {
            context.startActivity<RemoteBookActivity>()
        },
        onAddUrl = {
            activity?.showM3EditDialog(
                title = context.getString(R.string.add_book_url),
                hint = "url",
                onConfirm = { value ->
                    bookshelfViewModel.addBookByUrl(value)
                },
            )
        },
        onManageBookshelf = {
            context.startActivity<BookshelfManageActivity> {
                putExtra("groupId", selectedGroupId)
            }
        },
        onDownload = {
            context.startActivity<CacheActivity> {
                putExtra("groupId", selectedGroupId)
            }
        },
        onManageGroup = {
            activity?.showDialogFragment<GroupManageDialog>()
        },
        onExportBookshelf = {
            bookshelfViewModel.exportBookshelf(currentGroupBooks()) { file ->
                exportLauncher.launch {
                    mode = HandleFileContract.EXPORT
                    fileData = HandleFileContract.FileData(
                        "bookshelf.json", file, "application/json"
                    )
                }
            }
        },
        onImportBookshelf = {
            activity?.showM3EditDialog(
                title = context.getString(R.string.import_bookshelf),
                hint = "url/json",
                onConfirm = { value ->
                    bookshelfViewModel.importBookshelf(value, selectedGroupId)
                },
                neutralButtonText = context.getString(R.string.select_file),
                onNeutralClick = {
                    importLauncher.launch {
                        mode = HandleFileContract.FILE
                        allowExtensions = arrayOf("txt", "json")
                    }
                },
            )
        },
        onWebService = {
            if (WebService.isRun) {
                WebService.stop(context)
            } else {
                WebService.start(context)
            }
        },
        onLog = {
            activity?.showLogSheet()
        },
        enableRefresh = enableRefresh(),
        isRefreshing = isRefreshing.value,
        onRefresh = {
            isRefreshing.value = true
            mainViewModel.upToc(currentGroupBooks())
            isRefreshing.value = false
        },
        isUpdating = mainViewModel::isUpdate,
        lastUpdateVersion = lastUpdateVersion,
        showUnread = showUnread,
        showLastUpdateTime = showLastUpdateTime,
        showFastScroller = showFastScroller,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )
    } // key(refreshVersion)
}
