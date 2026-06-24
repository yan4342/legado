package io.legado.app.ui.main.bookshelf.compose

import android.view.View as AndroidView
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.EmptyStateView
import io.legado.app.ui.common.compose.LocalAnimationsEnabled
import io.legado.app.ui.common.compose.RoundDropdownMenu
import io.legado.app.ui.common.compose.RoundDropdownMenuItem
import io.legado.app.ui.main.bookCoverSharedElementKey
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
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
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val animationsEnabled = LocalAnimationsEnabled.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val touchSlopPx = with(LocalDensity.current) { 8.dp.toPx() }

    // 只有当 top bar 处于完全展开状态（未滚动）时，才允许下拉刷新，
    // 避免与上滑回顶使 top bar 变色的效果冲突。
    // 用 contentOffset 而非 collapsedFraction，因为 pinned 行为下 heightOffset 始终为 0。
    // 直接读 State 让 Compose 自动追踪变化，避免 LaunchedEffect/snapshotFlow 的时序问题。
    val topBarAtRest = scrollBehavior.state.contentOffset < 0.5f

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

    // 返回键拦截：菜单展开时关闭菜单
    BackHandler(enabled = menuExpanded) {
        menuExpanded = false
    }

    Scaffold(
        modifier = Modifier,
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
                    RoundDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.widthIn(min = 160.dp, max = 300.dp),
                    ) { dismiss ->
                        // 排序
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.sort),
                            onClick = { dismiss(); onSort() },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_sort), null) },
                        )
                        // 书架布局设置
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.bookshelf_layout),
                            onClick = { dismiss(); onConfigBookshelf() },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_view_quilt), null) },
                        )
                        // 更新目录
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.update_toc),
                            onClick = { dismiss(); onUpdateToc() },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_refresh_black_24dp), null) },
                        )
                        // 本地书籍
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.book_local),
                            onClick = { dismiss(); onAddLocal() },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_add), null) },
                        )
                        // 添加远程书籍
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.add_remote_book),
                            onClick = { dismiss(); onAddRemote() },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_add), null) },
                        )
                        // 添加URL
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.add_url),
                            onClick = { dismiss(); onAddUrl() },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_add_online), null) },
                        )
                        // 书架管理
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.bookshelf_management),
                            onClick = { dismiss(); onManageBookshelf() },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_arrange), null) },
                        )
                        // 缓存导出
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.cache_export),
                            onClick = { dismiss(); onDownload() },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_download_line), null) },
                        )
                        // 分组管理
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.group_manage),
                            onClick = { dismiss(); onManageGroup() },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_groups), null) },
                        )
                        // 导出书架
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.export_bookshelf),
                            onClick = { dismiss(); onExportBookshelf() },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_export), null) },
                        )
                        // 导入书架
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.import_bookshelf),
                            onClick = { dismiss(); onImportBookshelf() },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_import), null) },
                        )
                        // Web 服务
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.web_service),
                            onClick = { dismiss(); onWebService() },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_web_outline), null) },
                        )
                        // 日志
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.log),
                            onClick = { dismiss(); onLog() },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_cfg_about), null) },
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
                // 分组 Tab 行（仅 Tab 分组模式）— 自定义 LazyRow 实现「底块透出」效果
                if (bookGroupStyle == 0 && groups.isNotEmpty()) {
                    val tabSelectedIndex = groups.indexOfFirst { it.groupId == selectedGroupId }
                        .coerceAtLeast(0)
                    val isEInk = AppConfig.isEInkMode
                    val accentColor = if (isEInk) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(
                            count = groups.size,
                            key = { groups[it].groupId },
                        ) { index ->
                            val group = groups[index]
                            val isSelected = index == tabSelectedIndex
                            Box(
                                modifier = Modifier
                                    .clickable {
                                        onGroupSelected(group.groupId)
                                        if (pagerState != null && index != pagerState.currentPage) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                // 底层：选中时在文字下方透出的颜色块（左→右渐变淡出）
                                if (isSelected && !isEInk) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .align(Alignment.Center)
                                            .offset(y = 6.dp)
                                            .height(3.dp)
                                            .background(
                                                brush = Brush.horizontalGradient(
                                                    colors = listOf(
                                                        accentColor.copy(alpha = 0.35f),
                                                        Color.Transparent,
                                                    ),
                                                ),
                                                shape = MaterialTheme.shapes.extraSmall,
                                            ),
                                    )
                                }
                                // 顶层：文字
                                Text(
                                    text = group.groupName.ifEmpty {
                                        group.getManageName(context)
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) {
                                        FontWeight.ExtraBold
                                    } else {
                                        FontWeight.Normal
                                    },
                                    color = if (isSelected) {
                                        accentColor
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                )
                            }
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
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
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
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    } else if (gridColumns > 0) {
                        BookGridContent(
                            books = books,
                            columns = gridColumns,
                            onBookClick = onBookClick,
                            onBookLongClick = onBookLongClick,
                            showFastScroller = showFastScroller,
                            modifier = Modifier.fillMaxSize(),
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
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
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                }
            }
        }
        // 将 scrollBehavior.nestedScrollConnection 放在 PTR 内部，
        // 使其在 post-scroll 链中位于 PTR 之前，从而 contentOffset 只反映
        // LazyColumn 的真实滚动量，避免 PTR 自身的滚动消耗形成反馈循环。
        val wrappedContent: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
            ) { content() }
        }
        if (enableRefresh) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = contentModifier,
                enabled = topBarAtRest,
            ) { wrappedContent() }
        } else {
            Box(modifier = contentModifier) { wrappedContent() }
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
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
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
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
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
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
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
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val listState = rememberLazyListState()
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "bookshelf_list" },
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
        ) {
            items(
                items = books,
                key = { it.bookUrl },
            ) { book ->
                val isUpdatingBook = isUpdating(book.bookUrl)
                val sharedKey = bookCoverSharedElementKey(book.bookUrl)
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
                        coverTransitionName = sharedKey,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        sharedCoverKey = sharedKey,
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
                            coverTransitionName = sharedKey,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            sharedCoverKey = sharedKey,
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
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val rows = books.chunked(columns)
    val spacing = 4.dp
    val rowSpacing = 8.dp
    val listState = rememberLazyListState()
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "bookshelf_list" },
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
                        val sharedKey = bookCoverSharedElementKey(book.bookUrl)
                        BookGridItem(
                            book = book,
                            onClick = { onBookClick(book) },
                            onLongClick = { onBookLongClick(book) },
                            showTitle = columns < 5,
                            modifier = Modifier.weight(1f),
                            coverTransitionName = sharedKey,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            sharedCoverKey = sharedKey,
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
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
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
                                    val sharedKey = bookCoverSharedElementKey(book.bookUrl)
                                    BookGridItem(
                                        book = book,
                                        onClick = { onBookClick(book) },
                                        onLongClick = { onBookLongClick(book) },
                                        showTitle = gridColumns < 5,
                                        modifier = Modifier.weight(1f),
                                        coverTransitionName = sharedKey,
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        sharedCoverKey = sharedKey,
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
                            val sharedKey = bookCoverSharedElementKey(book.bookUrl)
                            BookListItem(
                                book = book,
                                onClick = { onBookClick(book) },
                                onLongClick = { onBookLongClick(book) },
                                coverTransitionName = sharedKey,
                                modifier = Modifier.padding(vertical = 2.dp),
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                sharedCoverKey = sharedKey,
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
