package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.EmptyStateView
import io.legado.app.ui.common.compose.LocalAnimationsEnabled
import io.legado.app.ui.common.compose.legadoPopupBackgroundColor
import io.legado.app.ui.common.compose.legadoPopupPrimaryTextColor
import kotlin.math.roundToInt

/**
 * 书架页主 Composable。
 *
 * @param books 当前分组的书籍列表
 * @param groups 所有分组
 * @param selectedGroupId 当前选中的分组 ID
 * @param isGrid 是否为网格模式
 * @param onGroupSelected 分组切换回调
 * @param onConfigBookshelf 打开书架布局设置对话框
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
    gridColumns: Int, // 0 = list, 3-6 = grid columns
    onGroupSelected: (Long) -> Unit,
    onConfigBookshelf: () -> Unit,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
    onBookDelete: (Book) -> Unit,
    onSearchClick: () -> Unit,
    onSort: () -> Unit,
    onUpdateToc: () -> Unit,
    onAddLocal: () -> Unit,
    onAddRemote: () -> Unit,
    onAddUrl: () -> Unit,
    onManageBookshelf: () -> Unit,
    onDownload: () -> Unit,
    onManageGroup: () -> Unit,
    onExportBookshelf: () -> Unit,
    onImportBookshelf: () -> Unit,
    onWebService: () -> Unit,
    onLog: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.bookshelf),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = stringResource(R.string.action_search),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.widthIn(min = 200.dp, max = 300.dp),
                        containerColor = legadoPopupBackgroundColor(),
                    ) {
                        val c = legadoPopupPrimaryTextColor()
                        // 排序
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort), color = c) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_sort), null, tint = c) },
                            onClick = { menuExpanded = false; onSort() },
                        )
                        // 书架布局设置
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.bookshelf_layout), color = c) },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.ic_view_quilt), null, tint = c)
                            },
                            onClick = { menuExpanded = false; onConfigBookshelf() },
                        )
                        // 更新目录
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.update_toc), color = c) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_refresh_black_24dp), null, tint = c) },
                            onClick = { menuExpanded = false; onUpdateToc() },
                        )
                        // 本地书籍
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.book_local), color = c) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_add), null, tint = c) },
                            onClick = { menuExpanded = false; onAddLocal() },
                        )
                        // 添加远程书籍
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_remote_book), color = c) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_add), null, tint = c) },
                            onClick = { menuExpanded = false; onAddRemote() },
                        )
                        // 添加URL
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_url), color = c) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_add_online), null, tint = c) },
                            onClick = { menuExpanded = false; onAddUrl() },
                        )
                        // 书架管理
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.bookshelf_management), color = c) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_arrange), null, tint = c) },
                            onClick = { menuExpanded = false; onManageBookshelf() },
                        )
                        // 缓存导出
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.cache_export), color = c) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_download_line), null, tint = c) },
                            onClick = { menuExpanded = false; onDownload() },
                        )
                        // 分组管理
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.group_manage), color = c) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_groups), null, tint = c) },
                            onClick = { menuExpanded = false; onManageGroup() },
                        )
                        // 导出书架
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_bookshelf), color = c) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_export), null, tint = c) },
                            onClick = { menuExpanded = false; onExportBookshelf() },
                        )
                        // 导入书架
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_bookshelf), color = c) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_import), null, tint = c) },
                            onClick = { menuExpanded = false; onImportBookshelf() },
                        )
                        // Web 服务
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.web_service), color = c) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_web_outline), null, tint = c) },
                            onClick = { menuExpanded = false; onWebService() },
                        )
                        // 日志
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.log), color = c) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_cfg_about), null, tint = c) },
                            onClick = { menuExpanded = false; onLog() },
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
                    edgePadding = 8.dp,
                    indicator = { tabPositions ->
                        if (selectedIndex in tabPositions.indices) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                                color = MaterialTheme.colorScheme.primary,
                                height = 2.dp,
                            )
                        }
                    },
                ) {
                    groups.forEachIndexed { index, group ->
                        Tab(
                            selected = index == selectedIndex,
                            onClick = { onGroupSelected(group.groupId) },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = {
                                Text(
                                    text = group.groupName.ifEmpty {
                                        group.getManageName(context)
                                    },
                                    style = if (index == selectedIndex) {
                                        MaterialTheme.typography.labelLarge
                                    } else {
                                        MaterialTheme.typography.labelMedium
                                    },
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
            }

            // 书籍列表/网格/空状态
            if (books.isEmpty()) {
                EmptyStateView()
            } else if (gridColumns > 0) {
                BookGridContent(
                    books = books,
                    columns = gridColumns,
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
    val animationsEnabled = LocalAnimationsEnabled.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
    ) {
        items(
            items = books,
            key = { it.bookUrl },
        ) { book ->
            if (!animationsEnabled) {
                // E-Ink 模式：直接显示，无滑动删除
                BookListItem(
                    book = book,
                    onClick = { onBookClick(book) },
                    onLongClick = { onBookLongClick(book) },
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                SwipeToDeleteItem(
                    onDelete = { onBookDelete(book) },
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    BookListItem(
                        book = book,
                        onClick = { onBookClick(book) },
                        onLongClick = { onBookLongClick(book) },
                    )
                }
            }
        }
    }
}

/**
 * 左滑露出删除按钮，滑到固定距离后停住，点击按钮才删除。
 * 使用动画实现平滑滑动和回弹。
 */
@Composable
private fun SwipeToDeleteItem(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val revealDp = 120.dp
    val revealPx = with(density) { revealDp.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animationsEnabled = LocalAnimationsEnabled.current

    Box(modifier = modifier.clipToBounds()) {
        // 底层：删除按钮（固定在右侧）
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    MaterialTheme.colorScheme.error,
                    MaterialTheme.shapes.large,
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            IconButton(onClick = {
                // 点击删除后先弹回再执行删除
                onDelete()
            }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        // 上层：书籍卡片，可左滑偏移
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        // 只允许左滑（delta < 0）
                        val newOffset = (offsetX + delta).coerceIn(-revealPx, 0f)
                        offsetX = newOffset
                    },
                    onDragStopped = {
                        // 松手后：滑过 50% 则保持露出，否则弹回
                        val target = if (offsetX < -revealPx * 0.5f) -revealPx else 0f
                        if (animationsEnabled) {
                            animate(
                                initialValue = offsetX,
                                targetValue = target,
                                animationSpec = androidx.compose.animation.core.tween(
                                    durationMillis = 200,
                                ),
                            ) { value, _ -> offsetX = value }
                        } else {
                            // E-Ink 模式：瞬间跳转，无动画
                            offsetX = target
                        }
                    },
                ),
        ) {
            content()
        }
    }
}

@Composable
private fun BookGridContent(
    books: List<Book>,
    columns: Int,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = books.chunked(columns)
    val spacing = 4.dp
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing, vertical = spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        items(
            items = rows,
            key = { row -> row.joinToString { it.bookUrl } },
        ) { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                rowItems.forEach { book ->
                    BookGridItem(
                        book = book,
                        onClick = { onBookClick(book) },
                        onLongClick = { onBookLongClick(book) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // 补齐不满一行的空位
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
