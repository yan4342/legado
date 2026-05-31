package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.EmptyStateView

/**
 * 书架页主 Composable。
 *
 * @param books 当前分组的书籍列表
 * @param groups 所有分组
 * @param selectedGroupId 当前选中的分组 ID
 * @param isGrid 是否为网格模式
 * @param onGroupSelected 分组切换回调
 * @param onToggleMode 切换列表/网格模式
 * @param onBookClick 书籍点击回调
 * @param onBookLongClick 书籍长按回调
 * @param onSearchClick 搜索按钮回调
 * @param onSort 排序回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    books: List<Book>,
    groups: List<BookGroup>,
    selectedGroupId: Long,
    isGrid: Boolean,
    onGroupSelected: (Long) -> Unit,
    onToggleMode: () -> Unit,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
    onBookDelete: (Book) -> Unit,
    onSearchClick: () -> Unit,
    onSort: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bookshelf)) },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = stringResource(R.string.action_search),
                        )
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = null,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.bookshelf_layout)) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(
                                        if (isGrid) R.drawable.ic_arrange else R.drawable.ic_view_quilt
                                    ),
                                    contentDescription = null,
                                )
                            },
                            onClick = { menuExpanded = false; onToggleMode() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort)) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_sort),
                                    contentDescription = null,
                                )
                            },
                            onClick = { menuExpanded = false; onSort() },
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        val selectedIndex = groups.indexOfFirst { it.groupId == selectedGroupId }
            .coerceAtLeast(0)

        Column(modifier = Modifier.padding(paddingValues)) {
            // 分组 Tab 行
            if (groups.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    edgePadding = 16.dp,
                ) {
                    groups.forEachIndexed { index, group ->
                        Tab(
                            selected = index == selectedIndex,
                            onClick = { onGroupSelected(group.groupId) },
                            text = {
                                Text(
                                    text = group.groupName.ifEmpty {
                                        group.getManageName(context)
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                        )
                    }
                }
            }

            // 书籍列表/网格/空状态
            if (books.isEmpty()) {
                EmptyStateView()
            } else if (isGrid) {
                BookGridContent(
                    books = books,
                    onBookClick = onBookClick,
                    onBookLongClick = onBookLongClick,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                BookListContent(
                    books = books,
                    onBookClick = onBookClick,
                    onBookLongClick = onBookLongClick,
                    onBookDelete = onBookDelete,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun BookListContent(
    books: List<Book>,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
    onBookDelete: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEInk = AppConfig.isEInkMode
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
    ) {
        items(
            items = books,
            key = { it.bookUrl },
        ) { book ->
            if (isEInk) {
                // E-Ink 模式：不使用滑动，避免动画
                BookListItem(
                    book = book,
                    onClick = { onBookClick(book) },
                    onLongClick = { onBookLongClick(book) },
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                // 左滑露出删除按钮，点击按钮才删除（不自动删除）
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { false } // 永远不自动确认，停留在露出状态
                )
                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        val color by animateColorAsState(
                            when (dismissState.targetValue) {
                                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                                else -> Color.Transparent
                            },
                            label = "swipe_bg",
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(color, MaterialTheme.shapes.large)
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            IconButton(
                                onClick = { onBookDelete(book) },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    },
                    enableDismissFromStartToEnd = false,
                ) {
                    BookListItem(
                        book = book,
                        onClick = { onBookClick(book) },
                        onLongClick = { onBookLongClick(book) },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BookGridContent(
    books: List<Book>,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = books.chunked(2)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = rows,
            key = { row -> row.joinToString { it.bookUrl } },
        ) { rowItems ->
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { book ->
                    BookGridItem(
                        book = book,
                        onClick = { onBookClick(book) },
                        onLongClick = { onBookLongClick(book) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
