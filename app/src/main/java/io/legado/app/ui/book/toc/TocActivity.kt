@file:Suppress("DEPRECATION")

package io.legado.app.ui.book.toc

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.bookmark.BookmarkDialog
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.common.compose.LegadoSearchBar
import io.legado.app.ui.common.compose.RoundDropdownMenu
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showLogSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TocActivity : BaseComposeActivity() {

    val viewModel by viewModels<TocViewModel>()

    private val exportLauncher = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            when (it.requestCode) {
                1 -> viewModel.saveBookmark(uri)
                2 -> viewModel.saveBookmarkMd(uri)
            }
        }
    }

    private val txtTocRuleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val bookUrl = intent.getStringExtra("bookUrl")
        val initialPage = intent.getIntExtra("initialPage", 0)
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

        // 文本书 + 阅读器未打开(从详情页进入目录)时,直接启动阅读器避免闪烁。
        // 先落库 durChapterIndex,再启动,并标记 readerLaunched 让调用方跳过重复启动/删除书籍。
        fun openReader(index: Int, pos: Int) {
            val url = bookUrl ?: return
            this@TocActivity.lifecycleScope.launch {
                val b = withContext(Dispatchers.IO) { appDb.bookDao.getBook(url) }
                if (b != null) {
                    b.durChapterIndex = index
                    b.durChapterPos = pos
                    withContext(Dispatchers.IO) { appDb.bookDao.update(b) }
                }
                startActivity(Intent(this@TocActivity, ReadBookActivity::class.java).apply {
                    putExtra("bookUrl", url)
                })
            }
            setResult(RESULT_OK, Intent().apply {
                putExtra("index", index)
                putExtra("chapterPos", pos)
                putExtra("readerLaunched", true)
            })
            finish()
        }

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
                        IconButton(onClick = { finish() }) {
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
                                onShowLog = { this@TocActivity.showLogSheet() },
                                onTxtTocRule = {
                                    currentBook?.let {
                                        txtTocRuleLauncher.launch(
                                            Intent(this@TocActivity, TxtTocRuleActivity::class.java)
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
                                    setResult(RESULT_OK, Intent().apply {
                                        putExtra("index", index)
                                        putExtra("chapterPos", 0)
                                        putExtra("readerLaunched", false)
                                    })
                                    finish()
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
                                    setResult(RESULT_OK, Intent().apply {
                                        putExtra("index", index)
                                        putExtra("chapterPos", pos)
                                        putExtra("readerLaunched", false)
                                    })
                                    finish()
                                }
                            },
                            onBookmarkLongClick = { bookmark ->
                                this@TocActivity.showDialogFragment(BookmarkDialog(bookmark, showDelete = true))
                            },
                        )
                    }
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
