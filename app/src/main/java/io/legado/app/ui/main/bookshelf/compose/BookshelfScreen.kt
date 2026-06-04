package io.legado.app.ui.main.bookshelf.compose

import android.view.View as AndroidView
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.EmptyStateView
import io.legado.app.ui.common.compose.LocalAnimationsEnabled
import io.legado.app.ui.common.compose.legadoPopupBackgroundColor
import io.legado.app.ui.common.compose.legadoPopupPrimaryTextColor
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 书架页主 Composable。
 *
 * Tab 分组模式下，使用 [HorizontalPager] 实现分组间跟手平移滑动；
 * 书籍列表项左滑删除优先于页面切换；在首尾分组继续越界拖动时放行给外层 ViewPager。
 *
 * @param books 全量书籍列表（Tab 模式），或当前分组的书籍列表（其他模式）
 * @param groups 所有可见分组
 * @param selectedGroupId 当前选中的分组 ID
 * @param gridColumns 0 = 列表, 3-6 = 网格列数
 * @param bookGroupStyle 0 = Tab 分组, 1 = 文件布局
 * @param onGroupSelected 分组切换回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    books: List<Book>,
    groups: List<BookGroup>,
    selectedGroupId: Long,
    gridColumns: Int, // 0 = list, 3-6 = grid columns
    bookGroupStyle: Int = 0, // 0 = Tab 分组, 1 = 文件布局
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
    enableRefresh: Boolean = true,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    isUpdating: (String) -> Boolean = { false },
    lastUpdateVersion: Int = 0,
    showUnread: Boolean = true,
    showLastUpdateTime: Boolean = false,
    showFastScroller: Boolean = false,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val animationsEnabled = LocalAnimationsEnabled.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val touchSlopPx = with(LocalDensity.current) { 8.dp.toPx() }

    // 是否为 Tab 分组模式且有多于一个分组可用
    val usePager = bookGroupStyle == 0 && groups.size > 1
    val selectedIndex = if (usePager) {
        groups.indexOfFirst { it.groupId == selectedGroupId }.coerceAtLeast(0)
    } else {
        0
    }
    val pagerState = if (usePager) {
        rememberPagerState(initialPage = selectedIndex) { groups.size }
    } else {
        null
    }

    // 同步：Tab 点击 → pager 滚动
    if (usePager && pagerState != null) {
        // 仅在 pager 真正停稳并回到整页位置时，才同步分组选中状态。
        LaunchedEffect(pagerState) {
            snapshotFlow {
                PagerScrollSnapshot(
                    page = pagerState.currentPage,
                    targetPage = pagerState.targetPage,
                    offset = pagerState.currentPageOffsetFraction,
                    scrolling = pagerState.isScrollInProgress,
                )
            }.collect { state ->
                val isStable = !state.scrolling && abs(state.offset) < 0.001f
                if (isStable && state.page in groups.indices) {
                    onGroupSelected(groups[state.page].groupId)
                }
            }
        }
        // pager 停止滚动时释放父级拦截
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.isScrollInProgress }
                .collect { isScrolling ->
                    if (!isScrolling) {
                        if (abs(pagerState.currentPageOffsetFraction) > 0.001f) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage)
                            }
                        }
                        view.requestParentDisallowIntercept(false)
                    }
                }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.bookshelf),
                        color = if (AppConfig.isEInkMode) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (AppConfig.isEInkMode) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
                actions = {
                    val iconTint = if (AppConfig.isEInkMode) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    }
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = stringResource(R.string.action_search),
                            tint = iconTint,
                        )
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = null,
                            tint = iconTint,
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
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        val content: @Composable () -> Unit = {
            Column(modifier = Modifier.fillMaxSize()) {
                // 分组 Tab 行（仅 Tab 分组模式）
                if (bookGroupStyle == 0 && groups.isNotEmpty()) {
                    val tabSelectedIndex = if (usePager && pagerState != null) {
                        groups.indexOfFirst { it.groupId == selectedGroupId }
                            .coerceAtLeast(0)
                    } else {
                        groups.indexOfFirst { it.groupId == selectedGroupId }
                            .coerceAtLeast(0)
                    }
                    @Suppress("DEPRECATION")
                    ScrollableTabRow(
                        selectedTabIndex = tabSelectedIndex,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        edgePadding = 8.dp,
                        indicator = { tabPositions ->
                            if (tabSelectedIndex in tabPositions.indices) {
                                @Suppress("DEPRECATION")
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[tabSelectedIndex]),
                                    color = if (AppConfig.isEInkMode) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    height = 2.dp,
                                )
                            }
                        },
                    ) {
                        groups.forEachIndexed { index, group ->
                            Tab(
                                selected = index == tabSelectedIndex,
                                onClick = {
                                    onGroupSelected(group.groupId)
                                    if (pagerState != null && index != pagerState.currentPage) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                                },
                                selectedContentColor = if (AppConfig.isEInkMode) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                text = {
                                    Text(
                                        text = group.groupName.ifEmpty {
                                            group.getManageName(context)
                                        },
                                        style = if (index == tabSelectedIndex) {
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

                // 内容区：HorizontalPager（多分组）或直接渲染
                if (usePager && pagerState != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val pageAtDown = pagerState.currentPage
                                    val atExactFirstPage =
                                        pageAtDown == 0 && abs(pagerState.currentPageOffsetFraction) < 0.001f
                                    val atExactLastPage =
                                        pageAtDown == groups.lastIndex && abs(pagerState.currentPageOffsetFraction) < 0.001f
                                    var totalDx = 0f
                                    var parentDecisionMade = false
                                    if (!atExactFirstPage && !atExactLastPage) {
                                        parentDecisionMade = true
                                        view.requestParentDisallowIntercept(true)
                                    }
                                    // 等到手指抬起（不释放，交由 pager 状态观察者处理）
                                    do {
                                        val event = awaitPointerEvent()
                                        totalDx += event.changes.firstOrNull()?.positionChange()?.x ?: 0f
                                        if (!parentDecisionMade && abs(totalDx) > touchSlopPx) {
                                            val handOffToParent =
                                                (atExactFirstPage && totalDx < -touchSlopPx) ||
                                                    (atExactLastPage && totalDx > touchSlopPx)
                                            parentDecisionMade = true
                                            view.requestParentDisallowIntercept(!handOffToParent)
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            },
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            val group = groups[page]
                            val groupBooks = remember(books, group) {
                                filterBooksForGroup(books, group.groupId, groups)
                            }
                            GroupPageContent(
                                books = groupBooks,
                                gridColumns = gridColumns,
                                onBookClick = onBookClick,
                                onBookLongClick = onBookLongClick,
                                onBookDelete = onBookDelete,
                                isUpdating = isUpdating,
                                lastUpdateVersion = lastUpdateVersion,
                                showUnread = showUnread,
                                showLastUpdateTime = showLastUpdateTime,
                                showFastScroller = showFastScroller,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                } else {
                    // 非 Pager 模式：直接渲染当前分组内容
                    if (books.isEmpty()) {
                        EmptyStateView()
                    } else if (bookGroupStyle == 1) {
                        BookGroupMixedContent(
                            books = books,
                            groups = groups,
                            gridColumns = gridColumns,
                            onBookClick = onBookClick,
                            onBookLongClick = onBookLongClick,
                            onBookDelete = onBookDelete,
                            onGroupClick = onGroupSelected,
                            showFastScroller = showFastScroller,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (gridColumns > 0) {
                        BookGridContent(
                            books = books,
                            columns = gridColumns,
                            onBookClick = onBookClick,
                            onBookLongClick = onBookLongClick,
                            showFastScroller = showFastScroller,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        BookListContent(
                            books = books,
                            onBookClick = onBookClick,
                            onBookLongClick = onBookLongClick,
                            onBookDelete = onBookDelete,
                            isUpdating = isUpdating,
                            lastUpdateVersion = lastUpdateVersion,
                            showUnread = showUnread,
                            showLastUpdateTime = showLastUpdateTime,
                            showFastScroller = showFastScroller,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        if (enableRefresh) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = contentModifier,
            ) { content() }
        } else {
            Box(modifier = contentModifier) { content() }
        }
    }
}

/**
 * 渲染单个分组页面的内容：列表或网格。
 */
@Composable
private fun GroupPageContent(
    books: List<Book>,
    gridColumns: Int,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
    onBookDelete: (Book) -> Unit,
    isUpdating: (String) -> Boolean = { false },
    lastUpdateVersion: Int = 0,
    showUnread: Boolean = true,
    showLastUpdateTime: Boolean = false,
    showFastScroller: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (books.isEmpty()) {
        EmptyStateView()
    } else if (gridColumns > 0) {
        BookGridContent(
            books = books,
            columns = gridColumns,
            onBookClick = onBookClick,
            onBookLongClick = onBookLongClick,
            showFastScroller = showFastScroller,
            modifier = modifier,
        )
    } else {
        BookListContent(
            books = books,
            onBookClick = onBookClick,
            onBookLongClick = onBookLongClick,
            onBookDelete = onBookDelete,
            isUpdating = isUpdating,
            lastUpdateVersion = lastUpdateVersion,
            showUnread = showUnread,
            showLastUpdateTime = showLastUpdateTime,
            showFastScroller = showFastScroller,
            modifier = modifier,
        )
    }
}

/**
 * 从全量书籍中过滤出指定分组的书籍。
 * 系统分组按类型位掩码筛选，用户分组按 [Book.group] 位掩码匹配。
 */
internal fun filterBooksForGroup(
    allBooks: List<Book>,
    groupId: Long,
    allGroups: List<BookGroup>,
): List<Book> {
    // 所有用户分组的位掩码之和
    val sumUserGroupIds = allGroups.fold(0L) { acc, g ->
        if (g.groupId > 0) acc or g.groupId else acc
    }
    return when (groupId) {
        BookGroup.IdAll -> allBooks
        BookGroup.IdRoot -> allBooks.filter { book ->
            (book.type and BookType.text) != 0 &&
                    (book.type and BookType.local) == 0 &&
                    (sumUserGroupIds and book.group) == 0L
        }
        BookGroup.IdLocal -> allBooks.filter { (it.type and BookType.local) != 0 }
        BookGroup.IdAudio -> allBooks.filter { (it.type and BookType.audio) != 0 }
        BookGroup.IdNetNone -> allBooks.filter { book ->
            (book.type and BookType.audio) == 0 &&
                    (book.type and BookType.local) == 0 &&
                    (sumUserGroupIds and book.group) == 0L
        }
        BookGroup.IdLocalNone -> allBooks.filter { book ->
            (book.type and BookType.local) != 0 &&
                    (sumUserGroupIds and book.group) == 0L
        }
        BookGroup.IdError -> allBooks.filter {
            (it.type and BookType.updateError) != 0
        }
        else -> allBooks.filter { (it.group and groupId) != 0L }
    }
}

@Composable
private fun BookListContent(
    books: List<Book>,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
    onBookDelete: (Book) -> Unit,
    isUpdating: (String) -> Boolean = { false },
    lastUpdateVersion: Int = 0,
    showUnread: Boolean = true,
    showLastUpdateTime: Boolean = false,
    showFastScroller: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val listState = rememberLazyListState()
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
        ) {
            items(
                items = books,
                key = { it.bookUrl },
            ) { book ->
                val isUpdatingBook = isUpdating(book.bookUrl)
                if (!animationsEnabled) {
                    // E-Ink 模式：直接显示，无滑动删除
                    BookListItem(
                        book = book,
                        onClick = { onBookClick(book) },
                        onLongClick = { onBookLongClick(book) },
                        modifier = Modifier.padding(vertical = 4.dp),
                        isUpdating = isUpdatingBook,
                        lastUpdateVersion = lastUpdateVersion,
                        showUnread = showUnread,
                        showLastUpdateTime = showLastUpdateTime,
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
                            isUpdating = isUpdatingBook,
                            lastUpdateVersion = lastUpdateVersion,
                            showUnread = showUnread,
                            showLastUpdateTime = showLastUpdateTime,
                        )
                    }
                }
            }
        }
        FastScrollbar(
            lazyListState = listState,
            itemCount = books.size,
            visible = showFastScroller,
            modifier = Modifier.matchParentSize(),
        )
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
    val directionSlopPx = with(density) { 8.dp.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animationsEnabled = LocalAnimationsEnabled.current
    val view = LocalView.current

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
                    painter = painterResource(R.drawable.ic_outline_delete),
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
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalDragX = 0f
                        var disallowingParent = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes
                                .firstOrNull { it.id == down.id }
                                ?: break
                            if (!change.pressed) break
                            totalDragX += change.positionChange().x
                            if (totalDragX < -directionSlopPx && !disallowingParent) {
                                view.requestParentDisallowIntercept(true)
                                disallowingParent = true
                            }
                        }
                        if (disallowingParent) {
                            view.requestParentDisallowIntercept(false)
                        }
                    }
                }
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
                                animationSpec = tween(durationMillis = 200),
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
    showFastScroller: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val rows = books.chunked(columns)
    val spacing = 4.dp
    val rowSpacing = 8.dp
    val listState = rememberLazyListState()
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = spacing, vertical = spacing),
            verticalArrangement = Arrangement.spacedBy(rowSpacing),
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
                            showTitle = columns < 5,
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
        FastScrollbar(
            lazyListState = listState,
            itemCount = rows.size,
            visible = showFastScroller,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/**
 * 文件布局模式：分组头 + 书籍混合列表。
 * 模拟 Style2 的行为，但使用 Compose 实现。
 */
@Composable
private fun BookGroupMixedContent(
    books: List<Book>,
    groups: List<BookGroup>,
    gridColumns: Int,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
    onBookDelete: (Book) -> Unit,
    onGroupClick: (Long) -> Unit,
    showFastScroller: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // 仅保留用户自定义分组（groupId > 0），过滤掉系统分组（IdAll/IdLocal/IdAudio 等）
    val userGroups = remember(groups) { groups.filter { it.groupId > 0 } }

    // 按分组分组书籍
    val groupedBooks = remember(books, userGroups) {
        // 预计算所有用户分组 ID 的并集，用于判断"未分组"
        val allGroupBits = userGroups.fold(0L) { acc, g -> acc or g.groupId }

        val groupMap = mutableMapOf<Long, MutableList<Book>>()
        val ungrouped = mutableListOf<Book>()
        books.forEach { book ->
            val groupIds = getGroupIds(book.group)
            // 只保留存在于 userGroups 中的分组 ID
            val validIds = groupIds.filter { gid -> gid > 0 && gid and allGroupBits != 0L }
            if (validIds.isEmpty()) {
                // 没有匹配的用户分组，归入"未分组"
                ungrouped.add(book)
            } else {
                validIds.forEach { gid ->
                    groupMap.getOrPut(gid) { mutableListOf() }.add(book)
                }
            }
        }

        val result = userGroups.map { group ->
            group to (groupMap[group.groupId] ?: emptyList())
        }.toMutableList()

        // 添加"未分组"组
        if (ungrouped.isNotEmpty()) {
            val noGroup = BookGroup(
                groupId = 0L,
                groupName = "",
            )
            result.add(noGroup to ungrouped)
        }
        result
    }

    val listState = rememberLazyListState()
    // 记录每个分组的折叠/展开状态（groupId -> collapsed）
    val collapsedGroups = remember { mutableStateOf(setOf<Long>()) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            groupedBooks.forEach { (group, groupBooks) ->
                val isCollapsed = group.groupId in collapsedGroups.value
                // 分组头（点击切换折叠/展开）
                item(key = "group_${group.groupId}") {
                    GroupHeaderItem(
                        group = group,
                        bookCount = groupBooks.size,
                        expanded = !isCollapsed,
                        onClick = {
                            collapsedGroups.value = if (isCollapsed) {
                                collapsedGroups.value - group.groupId
                            } else {
                                collapsedGroups.value + group.groupId
                            }
                        },
                    )
                }
                // 分组下的书籍（折叠时跳过）
                if (!isCollapsed) {
                    if (gridColumns > 0) {
                        val rows = groupBooks.chunked(gridColumns)
                        items(
                            items = rows,
                            key = { row -> row.joinToString { "grid_${it.bookUrl}" } },
                        ) { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                rowItems.forEach { book ->
                                    BookGridItem(
                                        book = book,
                                        onClick = { onBookClick(book) },
                                        onLongClick = { onBookLongClick(book) },
                                        showTitle = gridColumns < 5,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(gridColumns - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(
                            items = groupBooks,
                            key = { "list_${it.bookUrl}" },
                        ) { book ->
                            BookListItem(
                                book = book,
                                onClick = { onBookClick(book) },
                                onLongClick = { onBookLongClick(book) },
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
        FastScrollbar(
            lazyListState = listState,
            itemCount = books.size,
            visible = showFastScroller,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/**
 * 分组头组件
 */
@Composable
private fun GroupHeaderItem(
    group: BookGroup,
    bookCount: Int,
    expanded: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_groups),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = when {
                    group.groupId == 0L -> context.getString(R.string.no_group)
                    group.groupName.isNotEmpty() -> group.groupName
                    else -> group.getManageName(context)
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$bookCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(
                    if (expanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_right
                ),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 从 Book.group 位运算字段中提取所有分组 ID
 */
private fun getGroupIds(group: Long): List<Long> {
    if (group == 0L) return listOf(0L)
    val ids = mutableListOf<Long>()
    var bit = 1L
    while (bit <= group) {
        if (group and bit != 0L) {
            ids.add(bit)
        }
        bit = bit shl 1
    }
    return ids.ifEmpty { listOf(0L) }
}

private fun AndroidView.requestParentDisallowIntercept(disallow: Boolean) {
    var viewParent = parent
    while (viewParent != null) {
        viewParent.requestDisallowInterceptTouchEvent(disallow)
        viewParent = viewParent.parent
    }
}

private data class PagerScrollSnapshot(
    val page: Int,
    val targetPage: Int,
    val offset: Float,
    val scrolling: Boolean,
)
