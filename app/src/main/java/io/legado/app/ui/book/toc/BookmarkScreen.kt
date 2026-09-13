package io.legado.app.ui.book.toc

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.data.appDb
import io.legado.app.data.entities.Bookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkPage(
    bookUrl: String,
    searchQuery: String,
    onBookmarkClick: (index: Int, pos: Int) -> Unit,
    onBookmarkLongClick: (Bookmark) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val searchHint = searchQuery.ifBlank { null }

    var bookmarkList by remember { mutableStateOf<List<Bookmark>>(emptyList()) }
    var durChapterIndex by remember { mutableIntStateOf(0) }

    // Load book for durChapterIndex
    LaunchedEffect(bookUrl) {
        val book = withContext(Dispatchers.IO) { appDb.bookDao.getBook(bookUrl) }
        if (book != null) durChapterIndex = book.durChapterIndex
    }

    // Load bookmarks
    LaunchedEffect(bookUrl, searchHint) {
        val book = withContext(Dispatchers.IO) { appDb.bookDao.getBook(bookUrl) } ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                when (searchHint) {
                    null -> appDb.bookmarkDao.flowByBook(book.name, book.author)
                    else -> appDb.bookmarkDao.flowSearch(book.name, book.author, searchHint)
                }.flowOn(Dispatchers.IO).collect { list ->
                    bookmarkList = list
                }
            } catch (e: Exception) {
                io.legado.app.constant.AppLog.put("目录界面获取书签数据失败\n${e.localizedMessage}", e)
                bookmarkList = emptyList()
            }
        }
    }

    // Scroll to current chapter
    LaunchedEffect(bookmarkList) {
        if (bookmarkList.isEmpty()) return@LaunchedEffect
        val target = bookmarkList.indexOfLast { it.chapterIndex <= durChapterIndex }
        if (target >= 0) listState.scrollToItem(target.coerceAtMost(bookmarkList.size - 1))
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(bookmarkList, key = { it.time }) { bookmark ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onBookmarkClick(bookmark.chapterIndex, bookmark.chapterPos) },
                        onLongClick = { onBookmarkLongClick(bookmark) },
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(bookmark.chapterName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (bookmark.bookText.isNotEmpty()) {
                    Text(
                        bookmark.bookText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (bookmark.content.isNotEmpty()) {
                    Text(
                        bookmark.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp)
        }
    }
}
