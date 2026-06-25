@file:Suppress("DEPRECATION")

package io.legado.app.ui.main.bookshelf.compose

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.widget.SearchView
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.core.view.indices
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.entryProvider
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.service.WebService
import io.legado.app.ui.book.cache.CacheActivity
import io.legado.app.ui.book.group.GroupManageDialog
import io.legado.app.ui.book.info.compose.BookInfoRouteScreen
import io.legado.app.ui.book.import.local.ImportBookActivity
import io.legado.app.ui.book.import.remote.RemoteBookActivity
import io.legado.app.ui.book.manage.BookshelfManageActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.main.MainRoute
import io.legado.app.ui.main.MainRouteBookshelf
import io.legado.app.ui.main.MainRouteBookInfo
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.utils.checkByIndex
import io.legado.app.utils.getCheckedIndex
import io.legado.app.utils.observeEvent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showLogSheet
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 书架界面 — Compose 版。
 *
 * 使用 ComposeView 直接渲染 BookshelfScreen，
 * 完全替代 XML + RecyclerView + Adapter 方案。
 */
class BookshelfComposeFragment() : BaseBookshelfFragment(0),
    SearchView.OnQueryTextListener {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    private var selectedGroupId = BookGroup.IdAll
    // 布局模式：0=列表，1=网格3列，2=网格4列，3=网格5列，4=网格6列
    // 通过 configBookshelfCompose() 对话框修改后，gridVersionFlow 通知 Compose 重组
    private val sortVersionFlow = MutableStateFlow(0)
    private val refreshVersionFlow = MutableStateFlow(0)
    private val gridVersionFlow = MutableStateFlow(0)
    private val groupStyleVersionFlow = MutableStateFlow(0)
    private val lastUpdateVersionFlow = MutableStateFlow(0)
    private val isRefreshingFlow = MutableStateFlow(false)
    private val readBookResult = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Book read, refresh bookshelf data on next resume
        }
    }
    override val groupId: Long get() = selectedGroupId

    override val books: List<Book>
        get() = emptyList() // Compose 内部直接读取 Flow

    private fun sortBooks(books: List<Book>): List<Book> {
        return when (AppConfig.bookshelfSort) {
            0 -> books.sortedByDescending { it.durChapterTime }   // 最近阅读
            1 -> books.sortedByDescending { it.latestChapterTime } // 更新时间
            2 -> books.sortedBy { it.name }                        // 书名
            3 -> books.sortedBy { it.order }                       // 手动排序
            else -> books
        }
    }

    private fun gridColumns(): Int = when (AppConfig.bookshelfLayout) {
        1 -> 3
        2 -> 4
        3 -> 5
        4 -> 6
        else -> 0
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    SharedTransitionLayout {
                        val backStack = rememberNavBackStack(MainRouteBookshelf as MainRoute)

                        // Hide bottom nav when book info overlay is active
                        LaunchedEffect(backStack.size) {
                            val nav = (activity as? io.legado.app.ui.main.MainActivity)
                                ?.findViewById<android.view.View>(R.id.bottom_navigation_view)
                            nav?.visibility = if (backStack.size > 1)
                                android.view.View.GONE else android.view.View.VISIBLE
                        }

                        NavDisplay(
                            backStack = backStack,
                            sceneStrategies = listOf(SinglePaneSceneStrategy()),
                            transitionSpec = {
                                fadeIn(animationSpec = tween(300)) togetherWith
                                        fadeOut(animationSpec = tween(300))
                            },
                            popTransitionSpec = {
                                fadeIn(animationSpec = tween(300)) togetherWith
                                        fadeOut(animationSpec = tween(250))
                            },
                            predictivePopTransitionSpec = { _ ->
                                fadeIn(animationSpec = tween(300)) togetherWith
                                        fadeOut(animationSpec = tween(250))
                            },
                            onBack = { backStack.removeLastOrNull() },
                            entryProvider = entryProvider {
                                entry<MainRouteBookshelf> {
                                    BookshelfFragmentContent(
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                        onShowBookInfo = { name, author, bookUrl, origin, coverPath ->
                                            backStack.add(
                                                MainRouteBookInfo(
                                                    name = name,
                                                    author = author,
                                                    bookUrl = bookUrl,
                                                    origin = origin,
                                                    coverPath = coverPath,
                                                    sharedCoverKey = bookCoverSharedElementKey(bookUrl),
                                                )
                                            )
                                        },
                                    )
                                }

                                entry<MainRouteBookInfo> { route ->
                                    BookInfoRouteScreen(
                                        bookUrl = route.bookUrl,
                                        name = route.name,
                                        author = route.author,
                                        coverPath = route.coverPath,
                                        onBack = { backStack.removeLastOrNull() },
                                        onReadBook = { url, inShelf, _ ->
                                            backStack.removeLastOrNull()
                                            val intent = android.content.Intent(
                                                requireContext(),
                                                ReadBookActivity::class.java
                                            ).apply {
                                                putExtra("bookUrl", url)
                                                putExtra("inBookshelf", inShelf)
                                            }
                                            readBookResult.launch(intent)
                                        },
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                        sharedCoverKey = route.sharedCoverKey
                                            ?: bookCoverSharedElementKey(route.bookUrl),
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    /**
     * Inner composable for bookshelf data loading + [BookshelfScreen].
     */
    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    private fun BookshelfFragmentContent(
        sharedTransitionScope: SharedTransitionScope?,
        animatedVisibilityScope: AnimatedVisibilityScope?,
        onShowBookInfo: (String, String, String, String?, String?) -> Unit,
    ) {
        var groups by remember { mutableStateOf(emptyList<BookGroup>()) }
        var currentGroupId by remember { mutableStateOf(selectedGroupId) }
        val sortVersion by sortVersionFlow.collectAsState()
        val refreshVersion by refreshVersionFlow.collectAsState()
        val gridVersion by gridVersionFlow.collectAsState()
        val groupStyleVersion by groupStyleVersionFlow.collectAsState()
        val isRefreshing by isRefreshingFlow.collectAsState()
        val lastUpdateVersion by lastUpdateVersionFlow.collectAsState()
        val gridColumns = remember(gridVersion) { gridColumns() }
        val showUnread = remember(refreshVersion) { AppConfig.showUnread }
        val showLastUpdateTime = remember(refreshVersion) { AppConfig.showLastUpdateTime }
        val showFastScroller = remember(refreshVersion) { AppConfig.showBookshelfFastScroller }
        val bookGroupStyle = remember(groupStyleVersion) { AppConfig.bookGroupStyle }

        var books by remember(refreshVersion) {
            mutableStateOf(emptyList<Book>(), neverEqualPolicy())
        }
        LaunchedEffect(refreshVersion) {
            appDb.bookDao.flowByGroup(BookGroup.IdAll).collect { latestBooks ->
                books = latestBooks
            }
        }
        val sortedBooks = remember(books, sortVersion) { sortBooks(books) }

        DisposableEffect(Unit) {
            val groupsObserver = Observer<List<BookGroup>> { groups = it }
            appDb.bookGroupDao.show.observeForever(groupsObserver)
            onDispose {
                appDb.bookGroupDao.show.removeObserver(groupsObserver)
            }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        // On resume, only bump lastUpdateVersion for time display — no full recomposition
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                lastUpdateVersionFlow.value++
            }
        }

        val currentGroupBooks = remember(sortedBooks, currentGroupId, groups, bookGroupStyle) {
            if (bookGroupStyle == 0) {
                filterBooksForGroup(sortedBooks, currentGroupId, groups)
            } else {
                sortedBooks
            }
        }
        val enableRefresh = remember(currentGroupId, groups, currentGroupBooks.isNotEmpty()) {
            when {
                !currentGroupBooks.isNotEmpty() -> false
                currentGroupId == BookGroup.IdAll || currentGroupId == BookGroup.IdRoot -> true
                else -> groups.firstOrNull { it.groupId == currentGroupId }?.enableRefresh != false
            }
        }

        key(refreshVersion) {
        BookshelfScreen(
            books = sortedBooks,
            groups = groups,
            selectedGroupId = currentGroupId,
            gridColumns = gridColumns,
            bookGroupStyle = bookGroupStyle,
            onGroupSelected = {
                currentGroupId = it
                selectedGroupId = it
            },
            onConfigBookshelf = {
                configBookshelfCompose()
            },
            onBookClick = { book ->
                // Click → go directly to reading (existing behavior)
                val intent = android.content.Intent(
                    requireContext(),
                    ReadBookActivity::class.java
                ).apply {
                    putExtra("bookUrl", book.bookUrl)
                }
                startActivity(intent)
            },
            onBookLongClick = { book ->
                // Long click → open book info with shared element animation
                onShowBookInfo(
                    book.name,
                    book.author,
                    book.bookUrl,
                    book.origin,
                    book.getDisplayCover(),
                )
            },
            onBookDelete = { book ->
                appDb.bookDao.delete(book)
            },
            onSearchClick = {
                (activity as? MainActivity)?.navigateToSearch()
            },
            onSort = {
                val newSort = (AppConfig.bookshelfSort + 1) % 4
                AppConfig.bookshelfSort = newSort
                sortVersionFlow.value++
            },
            onUpdateToc = {
                val booksToUpdate = if (bookGroupStyle == 0) {
                    filterBooksForGroup(sortedBooks, selectedGroupId, groups)
                } else {
                    sortedBooks
                }
                activityViewModel.upToc(booksToUpdate)
            },
            onAddLocal = {
                startActivity<ImportBookActivity>()
            },
            onAddRemote = {
                startActivity<RemoteBookActivity>()
            },
            onAddUrl = {
                showAddBookByUrlAlert()
            },
            onManageBookshelf = {
                startActivity<BookshelfManageActivity> {
                    putExtra("groupId", currentGroupId)
                }
            },
            onDownload = {
                startActivity<CacheActivity> {
                    putExtra("groupId", currentGroupId)
                }
            },
            onManageGroup = {
                showDialogFragment<GroupManageDialog>()
            },
            onExportBookshelf = {
                val booksToExport = if (bookGroupStyle == 0) {
                    filterBooksForGroup(sortedBooks, selectedGroupId, groups)
                } else {
                    sortedBooks
                }
                viewModel.exportBookshelf(booksToExport) { file ->
                    exportResult.launch {
                        mode = HandleFileContract.EXPORT
                        fileData = HandleFileContract.FileData(
                            "bookshelf.json", file, "application/json"
                        )
                    }
                }
            },
            onImportBookshelf = {
                importBookshelfAlert(currentGroupId)
            },
            onWebService = {
                if (WebService.isRun) {
                    WebService.stop(requireContext())
                } else {
                    WebService.start(requireContext())
                }
            },
            onLog = {
                showLogSheet()
            },
            enableRefresh = enableRefresh,
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshingFlow.value = true
                val booksToUpdate = if (bookGroupStyle == 0) {
                    filterBooksForGroup(sortedBooks, selectedGroupId, groups)
                } else {
                    sortedBooks
                }
                activityViewModel.upToc(booksToUpdate)
                isRefreshingFlow.value = false
            },
            isUpdating = activityViewModel::isUpdate,
            lastUpdateVersion = lastUpdateVersion,
            showUnread = showUnread,
            showLastUpdateTime = showLastUpdateTime,
            showFastScroller = showFastScroller,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
        )
        } // key(refreshVersion)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // 不需要 setup XML toolbar，Compose 自带 TopAppBar
        // 每 30 秒递增版本号，触发 Compose 重组刷新「上次更新时间」
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                while (isActive) {
                    delay(30_000)
                    lastUpdateVersionFlow.value++
                }
            }
        }
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        // 监听书架刷新事件（configBookshelf 对话框的开关选项触发）
        observeEvent<String>(EventBus.BOOKSHELF_REFRESH) {
            refreshVersionFlow.value++
        }
    }

    /**
     * Compose 版书架布局设置对话框。
     * 与 BaseBookshelfFragment.configBookshelf() 相同的 UI，
     * 但布局/分组样式变更时通过 Flow 通知 Compose 重组，不触发 Activity recreate。
     */
    @SuppressLint("InflateParams")
    private fun configBookshelfCompose() {
        alert(titleResource = R.string.bookshelf_layout) {
            var bookshelfLayout = AppConfig.bookshelfLayout
            var bookshelfSort = AppConfig.bookshelfSort
            val alertBinding =
                io.legado.app.databinding.DialogBookshelfConfigBinding.inflate(layoutInflater)
                    .apply {
                        if (AppConfig.bookGroupStyle !in 0..<spGroupStyle.count) {
                            AppConfig.bookGroupStyle = 0
                        }
                        if (bookshelfLayout !in rgLayout.indices) {
                            bookshelfLayout = 0
                            AppConfig.bookshelfLayout = 0
                        }
                        if (bookshelfSort !in rgSort.indices) {
                            bookshelfSort = 0
                            AppConfig.bookshelfSort = 0
                        }
                        spGroupStyle.setSelection(AppConfig.bookGroupStyle)
                        swShowUnread.isChecked = AppConfig.showUnread
                        swShowLastUpdateTime.isChecked = AppConfig.showLastUpdateTime
                        swShowWaitUpBooks.isChecked = AppConfig.showWaitUpCount
                        swShowBookshelfFastScroller.isChecked = AppConfig.showBookshelfFastScroller
                        rgLayout.checkByIndex(bookshelfLayout)
                        rgSort.checkByIndex(bookshelfSort)
                    }
            customView { alertBinding.root }
            okButton {
                alertBinding.apply {
                    if (AppConfig.bookGroupStyle != spGroupStyle.selectedItemPosition) {
                        AppConfig.bookGroupStyle = spGroupStyle.selectedItemPosition
                        groupStyleVersionFlow.value++
                    }
                    if (AppConfig.showUnread != swShowUnread.isChecked) {
                        AppConfig.showUnread = swShowUnread.isChecked
                        refreshVersionFlow.value++
                    }
                    if (AppConfig.showLastUpdateTime != swShowLastUpdateTime.isChecked) {
                        AppConfig.showLastUpdateTime = swShowLastUpdateTime.isChecked
                        refreshVersionFlow.value++
                    }
                    if (AppConfig.showWaitUpCount != swShowWaitUpBooks.isChecked) {
                        AppConfig.showWaitUpCount = swShowWaitUpBooks.isChecked
                        activityViewModel.postUpBooksLiveData(true)
                    }
                    if (AppConfig.showBookshelfFastScroller != swShowBookshelfFastScroller.isChecked) {
                        AppConfig.showBookshelfFastScroller = swShowBookshelfFastScroller.isChecked
                        refreshVersionFlow.value++
                    }
                    if (bookshelfSort != rgSort.getCheckedIndex()) {
                        AppConfig.bookshelfSort = rgSort.getCheckedIndex()
                        sortVersionFlow.value++
                    }
                    if (bookshelfLayout != rgLayout.getCheckedIndex()) {
                        AppConfig.bookshelfLayout = rgLayout.getCheckedIndex()
                        gridVersionFlow.value++
                    }
                }
            }
            cancelButton()
        }
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        (activity as? MainActivity)?.navigateToSearch(key = query)
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean = false

    override fun upGroup(data: List<BookGroup>) {
        // Compose 通过 observeAsState 自动响应，无需手动更新
    }

    override fun upSort() {
        // 由 configBookshelfCompose() 直接调用 sortVersionFlow.value++，
        // 此方法仅保留兼容性（BaseBookshelfFragment 可能调用）
        sortVersionFlow.value++
    }

    override fun gotoTop() {
        // TODO: 滚动到顶部需要将 LazyListState 暴露出来
    }
}
