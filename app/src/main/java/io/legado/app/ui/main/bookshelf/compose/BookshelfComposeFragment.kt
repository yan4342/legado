@file:Suppress("DEPRECATION")

package io.legado.app.ui.main.bookshelf.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Observer
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.info.compose.BookInfoComposeActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private var isGrid = AppConfig.bookshelfLayout == 1
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    var groups by remember { mutableStateOf(emptyList<BookGroup>()) }
                    var books by remember { mutableStateOf(emptyList<Book>()) }
                    var currentGroupId by remember { mutableStateOf(selectedGroupId) }
                    var currentIsGrid by remember { mutableStateOf(isGrid) }

                    DisposableEffect(currentGroupId) {
                        val groupsObserver = Observer<List<BookGroup>> { groups = it }
                        val booksJob = CoroutineScope(Dispatchers.Main).launch {
                            appDb.bookDao.flowByGroup(currentGroupId).collect {
                                books = sortBooks(it)
                            }
                        }
                        appDb.bookGroupDao.show.observeForever(groupsObserver)
                        onDispose {
                            appDb.bookGroupDao.show.removeObserver(groupsObserver)
                            booksJob.cancel()
                        }
                    }

                    BookshelfScreen(
                        books = books,
                        groups = groups,
                        selectedGroupId = currentGroupId,
                        isGrid = currentIsGrid,
                        onGroupSelected = {
                            currentGroupId = it
                            selectedGroupId = it
                        },
                        onToggleMode = {
                            currentIsGrid = !currentIsGrid
                            isGrid = currentIsGrid
                            AppConfig.bookshelfLayout = if (currentIsGrid) 1 else 0
                        },
                        onBookClick = { book ->
                            // 点击进入阅读页
                            startActivity<ReadBookActivity> {
                                putExtra("bookUrl", book.bookUrl)
                            }
                        },
                        onBookLongClick = { book ->
                            // 长按进入详情页
                            startActivity<BookInfoComposeActivity> {
                                putExtra("bookUrl", book.bookUrl)
                            }
                        },
                        onBookDelete = { book ->
                            // 左滑删除
                            appDb.bookDao.delete(book)
                        },
                        onSearchClick = {
                            startActivity<SearchActivity>()
                        },
                        onSort = {
                            // 排序：循环切换排序方式
                            AppConfig.bookshelfSort = (AppConfig.bookshelfSort + 1) % 4
                            books = sortBooks(books)
                        },
                    )
                }
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // 不需要 setup XML toolbar，Compose 自带 TopAppBar
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        SearchActivity.start(requireContext(), query)
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean = false

    override fun upGroup(data: List<BookGroup>) {
        // Compose 通过 observeAsState 自动响应，无需手动更新
    }

    override fun upSort() {
        // Compose 通过 Flow 自动响应排序变化
    }

    override fun gotoTop() {
        // TODO: 滚动到顶部需要将 LazyListState 暴露出来
    }
}
