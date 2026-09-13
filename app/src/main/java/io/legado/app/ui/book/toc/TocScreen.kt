package io.legado.app.ui.book.toc

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.bookmark.BookmarkDialog
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.common.compose.LegadoSearchBar
import io.legado.app.ui.common.compose.RoundDropdownMenu
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showLogSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 目录选章结果。Selection 对应 TocActivityResult 回传的 (index, pos, readerLaunched) 三元组；
 * Cancelled 对应未选章节返回（Activity 形态的 null result，主栈形态下由消费端清理临时书）。
 */
sealed interface TocRouteResult {
    data class Selection(
        val bookUrl: String,
        val index: Int,
        val pos: Int,
        val readerLaunched: Boolean,
    ) : TocRouteResult

    data object Cancelled : TocRouteResult
}

/**
 * 主栈形态下目录结果回传 BookInfo 的 pending-holder（参照 MainConfigRouteActions 模式）：
 * TocEntry 写入后回退，BookInfoRouteScreen 消费并清空。
 */
class TocRouteState {
    var pendingResult: TocRouteResult? by mutableStateOf(null)
}

/**
 * 目录界面共享内容：TocActivity（阅读器/漫画/听书形态）与主栈 TocEntry（书籍详情形态）都委托此 Composable。
 * 章节点击路径经 [onExit] 参数化：
 * ① 文本书 + 阅读器未开（详情页进入）—— 先落库 durChapterIndex 再直接启动阅读器防闪烁，
 *    Selection(readerLaunched = true) 让调用方跳过重复启动；
 * ② 阅读器已开 / 漫画 / 听书 —— 仅回传 Selection(readerLaunched = false)，由调用方导航；
 * ③ 未选章节退出（顶栏返回 / 系统返回）—— Cancelled。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TocScreen(
    bookUrl: String?,
    initialPage: Int,
    viewModel: TocViewModel,
    launchScope: CoroutineScope,
    onExit: (TocRouteResult) -> Unit,
    // 阅读器打开回调：主栈 TocEntry 传路由导航；TocActivity 薄壳（漫画/听书）不传（其路径不走 openReader）
    onOpenReader: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val tabIndex by viewModel.tabIndex.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentBook by viewModel.bookFlow.collectAsState()
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, 1),
        pageCount = { 2 },
    )
    val topBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onPrimary,
        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
    )
    var menuExpanded by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            when (it.requestCode) {
                1 -> viewModel.saveBookmark(uri)
                2 -> viewModel.saveBookmarkMd(uri)
            }
        }
    }

    val txtTocRuleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("tocRegex")?.let { tocRegex ->
                viewModel.bookData.value?.let { book ->
                    book.tocUrl = tocRegex
                    viewModel.upBookTocRule(book) {
                        if (ReadBook.book == book) {
                            ReadBook.upMsg(null)
                        }
                    }
                }
            }
        }
    }

    // 文本书 + 阅读器未开(从详情页进入目录)时,直接启动阅读器避免闪烁。
    // 先落库 durChapterIndex,再启动,并标记 readerLaunched 让调用方跳过重复启动/删除书籍。
    // launchScope 由宿主提供(activity 传 lifecycleScope,主栈 entry 传 MainNavHost 的 scope):
    // 退出后协程仍需完成落库与启动,不能用本组合的 rememberCoroutineScope。
    fun openReader(index: Int, pos: Int) {
        val url = bookUrl ?: return
        val open = onOpenReader ?: return
        launchScope.launch {
            val b = withContext(Dispatchers.IO) { appDb.bookDao.getBook(url) }
            if (b != null) {
                b.durChapterIndex = index
                b.durChapterPos = pos
                withContext(Dispatchers.IO) { appDb.bookDao.update(b) }
            }
            open(url)
        }
        onExit(TocRouteResult.Selection(url, index, pos, readerLaunched = true))
    }

    fun exitWithSelection(index: Int, pos: Int) {
        val url = bookUrl ?: return
        onExit(TocRouteResult.Selection(url, index, pos, readerLaunched = false))
    }

    // 系统返回同样视为未选章节退出,保留调用方对临时书的清理语义。
    BackHandler { onExit(TocRouteResult.Cancelled) }

    // Sync pager ↔ tab
    LaunchedEffect(tabIndex) { pagerState.animateScrollToPage(tabIndex) }
    LaunchedEffect(pagerState.currentPage) { viewModel.selectTab(pagerState.currentPage) }

    // Load book data (for title bar and menu)
    LaunchedEffect(bookUrl) {
        bookUrl?.let { viewModel.initBook(it) }
    }

    Scaffold(
        modifier = Modifier,
        topBar = {
            TopAppBar(
                title = {
                    LegadoSearchBar(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        placeholder = stringResource(R.string.search),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onExit(TocRouteResult.Cancelled) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null)
                        }
                        TocMenu(
                            expanded = menuExpanded,
                            onDismiss = { menuExpanded = false },
                            book = currentBook,
                            viewModel = viewModel,
                            onExportBookmark = { exportLauncher.launch { requestCode = 1 } },
                            onExportMd = { exportLauncher.launch { requestCode = 2 } },
                            onShowLog = { activity?.showLogSheet() },
                            onTxtTocRule = {
                                currentBook?.let {
                                    txtTocRuleLauncher.launch(
                                        Intent(context, TxtTocRuleActivity::class.java)
                                            .apply { putExtra("tocRegex", it.tocUrl) }
                                    )
                                }
                            },
                        )
                    }
                },
                colors = topBarColors,
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text(stringResource(R.string.chapter_list)) },
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text(stringResource(R.string.bookmark)) },
                )
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> ChapterListPage(
                        bookUrl = bookUrl ?: "",
                        searchQuery = searchQuery,
                        refreshTrigger = viewModel.chapterRefreshTrigger,
                        onChapterClick = { index ->
                            val readerOpen = ReadBook.book?.bookUrl == bookUrl
                            val mangaAudio = currentBook?.isImage == true || currentBook?.isAudio == true
                            if (!readerOpen && !mangaAudio) {
                                // 文本书从详情页进入:直接启动阅读器(无闪烁)
                                openReader(index, 0)
                            } else {
                                // 阅读器已打开(原位切换)或漫画/听书(导航自己的阅读器):返回结果
                                exitWithSelection(index, 0)
                            }
                        },
                    )
                    1 -> BookmarkPage(
                        bookUrl = bookUrl ?: "",
                        searchQuery = searchQuery,
                        onBookmarkClick = { index, pos ->
                            val readerOpen = ReadBook.book?.bookUrl == bookUrl
                            val mangaAudio = currentBook?.isImage == true || currentBook?.isAudio == true
                            if (!readerOpen && !mangaAudio) {
                                openReader(index, pos)
                            } else {
                                exitWithSelection(index, pos)
                            }
                        },
                        onBookmarkLongClick = { bookmark ->
                            activity?.showDialogFragment(BookmarkDialog(bookmark, showDelete = true))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TocMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    book: Book?,
    viewModel: TocViewModel,
    onExportBookmark: () -> Unit,
    onExportMd: () -> Unit,
    onShowLog: () -> Unit,
    onTxtTocRule: () -> Unit,
) {
    val tabIndex by viewModel.tabIndex.collectAsState()
    val isBookmarkTab = tabIndex == 1

    RoundDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) { dismiss ->
        if (isBookmarkTab) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.export)) },
                onClick = { dismiss(); onExportBookmark() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.export_md)) },
                onClick = { dismiss(); onExportMd() },
            )
        } else {
            if (book?.isLocalTxt == true) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.txt_toc_rule)) },
                    onClick = { dismiss(); onTxtTocRule() },
                )
                val splitChecked = book.getSplitLongChapter()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.split_long_chapter)) },
                    leadingIcon = {
                        if (splitChecked) Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                    },
                    onClick = {
                        dismiss()
                        book.setSplitLongChapter(!splitChecked)
                        viewModel.upBookTocRule(book) { viewModel.triggerChapterRefresh() }
                    },
                )
                HorizontalDivider()
            }

            DropdownMenuItem(
                text = { Text(stringResource(R.string.reverse_toc)) },
                onClick = {
                    dismiss()
                    viewModel.reverseToc { viewModel.triggerChapterRefresh() }
                },
            )

            val useReplace = AppConfig.tocUiUseReplace
            DropdownMenuItem(
                text = { Text(stringResource(R.string.use_replace)) },
                leadingIcon = {
                    if (useReplace) Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                },
                onClick = {
                    dismiss()
                    AppConfig.tocUiUseReplace = !useReplace
                    viewModel.triggerChapterRefresh()
                },
            )

            val loadWordCount = AppConfig.tocCountWords
            DropdownMenuItem(
                text = { Text(stringResource(R.string.load_word_count)) },
                leadingIcon = {
                    if (loadWordCount) Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                },
                onClick = {
                    dismiss()
                    AppConfig.tocCountWords = !loadWordCount
                    viewModel.triggerChapterRefresh()
                },
            )
        }

        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.log)) },
            onClick = { dismiss(); onShowLog() },
        )
    }
}
