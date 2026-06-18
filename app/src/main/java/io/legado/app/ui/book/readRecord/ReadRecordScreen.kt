package io.legado.app.ui.book.readRecord

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.BookCoverCompose
import io.legado.app.ui.common.compose.LocalAnimationsEnabled
import io.legado.app.ui.common.compose.RoundDropdownMenu
import io.legado.app.ui.common.compose.RoundDropdownMenuItem
import io.legado.app.ui.common.compose.legadoCardBackgroundColor
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import android.view.View as AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadRecordScreen(
    state: ReadRecordUiState,
    onIntent: (ReadRecordIntent) -> Unit,
    effects: Flow<ReadRecordEffect>? = null,
    onBack: () -> Unit,
    onNavigateToBook: (String, String) -> Unit,
    onNavigateToSearch: (String) -> Unit,
    onOverviewClick: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyListState()
    var showSearch by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var searchText by remember(state.searchKey) { mutableStateOf(state.searchKey ?: "") }

    LaunchedEffect(Unit) { onIntent(ReadRecordIntent.Load) }
    LaunchedEffect(effects) {
        effects?.collect {
            when (it) {
                is ReadRecordEffect.NavigateToBook -> onNavigateToBook(it.bookName, it.author)
                is ReadRecordEffect.OpenSearch -> onNavigateToSearch(it.bookName)
                is ReadRecordEffect.ShowToast -> {}
            }
        }
    }

    // 预测性返回手势：搜索栏/菜单打开时跟手滑动关闭，系统处理跨 Activity 返回动画
    var searchBackProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = showSearch) { progress ->
        try {
            progress.collect { event -> searchBackProgress = event.progress }
            showSearch = false
            searchText = ""
            onIntent(ReadRecordIntent.Search(null))
        } catch (_: CancellationException) {
            // 手势取消
        } finally {
            searchBackProgress = 0f
        }
    }
    var menuBackProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = showMenu) { progress ->
        try {
            progress.collect { event -> menuBackProgress = event.progress }
            showMenu = false
        } catch (_: CancellationException) {
            // 手势取消
        } finally {
            menuBackProgress = 0f
        }
    }

    // 跨 Activity 返回由系统处理预测返回动画，无需手动拦截

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(
                        "阅读记录", 
                        color = if (AppConfig.isEInkMode) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        },
                    ) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (AppConfig.isEInkMode) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    ),
                    navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回", tint = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary) } },
                    actions = {
                        IconButton(onClick = {
                            val modes = DisplayMode.entries
                            val next = modes[(modes.indexOf(state.displayMode) + 1) % modes.size]
                            onIntent(ReadRecordIntent.SetMode(next))
                        }) { Icon(painterResource(R.drawable.ic_baseline_sort_24), contentDescription = "切换视图", tint = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary) }
                        IconButton(onClick = { showSearch = !showSearch }) { Icon(painterResource(R.drawable.ic_search), contentDescription = "搜索", tint = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary) }
                        IconButton(onClick = { showMenu = true }) { Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "菜单", tint = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary) }
                        RoundDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) { dismiss ->
                            DisplayMode.entries.forEach { m ->
                                RoundDropdownMenuItem(
                                    text = m.label,
                                    onClick = { dismiss(); onIntent(ReadRecordIntent.SetMode(m)) },
                                )
                            }
                            RoundDropdownMenuItem(
                                text = if (state.enableRecord) "关闭阅读记录" else "开启阅读记录",
                                onClick = { dismiss(); onIntent(ReadRecordIntent.ToggleEnableRecord) },
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
                AnimatedVisibility(visible = showSearch) {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showSearch = false; searchText = ""; onIntent(ReadRecordIntent.Search(null)) }) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "关闭") }
                            OutlinedTextField(value = searchText, onValueChange = { v -> searchText = v; onIntent(ReadRecordIntent.Search(v.ifBlank { null })) },
                                modifier = Modifier.weight(1f), placeholder = { Text("搜索书名") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                        }
                    }
                }
            }
        },
    ) { padding ->
        val contentKey = when { state.isLoading -> "loading"; state.books.isEmpty() -> "empty"; else -> "content" }
        AnimatedContent(targetState = contentKey, label = "content", transitionSpec = { fadeIn() togetherWith fadeOut() }) { key ->
            when (key) {
                "loading" -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("加载中…") }
                "empty" -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("暂无阅读记录", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("开始阅读后这里将会显示你的阅读记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                "content" -> {
                    // 滚动到底部附近时自动加载更多
                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                            val totalItems = listState.layoutInfo.totalItemsCount
                            !state.isLoadingMore && state.hasMore && lastVisible >= totalItems - 4
                        }
                    }
                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore) onIntent(ReadRecordIntent.LoadMore)
                    }
                    LazyColumn(
                        state = listState, modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
                    ) {
                        // 1. Summary — 整个卡片可点击进入二级总览页
                        item(key = "summary") { ReadingSummaryCard(state, onOverviewClick) }
                        // 2. Section title
                        item(key = "list_header") { Text("${state.displayMode.label}（${state.totalBooks}）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
                        // 3. Book list
                        items(state.books, key = { "book_${it.bookName}" }) { item ->
                            var showChapterInfo by remember { mutableStateOf(false) }
                            if (showChapterInfo) {
                                AlertDialog(
                                    onDismissRequest = { showChapterInfo = false },
                                    title = { Text(item.bookName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    text = {
                                        val chapterTitle = item.durChapterTitle
                                        if (chapterTitle != null) {
                                            Text("最新读到：第${item.durChapterIndex + 1}章 $chapterTitle")
                                        } else {
                                            Text("暂无章节信息")
                                        }
                                    },
                                    confirmButton = { TextButton(onClick = { showChapterInfo = false }) { Text("确定") } },
                                )
                            }
                            val animationsEnabled = LocalAnimationsEnabled.current
                            val itemModifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                            if (!animationsEnabled) {
                                BookItem(
                                    item,
                                    onClick = { onIntent(ReadRecordIntent.ClickBook(item.bookName, item.author)) },
                                    onLongClick = { showChapterInfo = true },
                                    modifier = itemModifier,
                                )
                            } else {
                                SwipeToDeleteItem(
                                    onDelete = { onIntent(ReadRecordIntent.DeleteBook(item.bookName)) },
                                    modifier = itemModifier,
                                ) {
                                    BookItem(
                                        item,
                                        onClick = { onIntent(ReadRecordIntent.ClickBook(item.bookName, item.author)) },
                                        onLongClick = { showChapterInfo = true },
                                    )
                                }
                            }
                        }
                        // 4. Load more indicator
                        if (state.isLoadingMore) {
                            item(key = "loading_more") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

// ── ReadingSummaryCard — MD3 exact: GlassCard style, title primary labelLarge, "已读 N 本书" split styling ──

@Composable
private fun ReadingSummaryCard(state: ReadRecordUiState, onClick: () -> Unit) {
    val cardBg = legadoCardBackgroundColor()
    val totalMinutes = state.totalReadTime / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val timeStr = if (hours > 0) "${hours}小时${minutes}分钟" else "${minutes}分钟"

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        shape = RoundedCornerShape(16.dp), color = cardBg, shadowElevation = 0.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("累计阅读成就", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("已读 ", style = MaterialTheme.typography.titleMedium)
                    Text("${state.totalBooks}", style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(" 本书", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(4.dp))
                Text("共阅读 $timeStr", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.summaryCovers.isNotEmpty()) {
                BookStackView(state.summaryCovers.take(5))
            }
        }
    }
}

@Composable private fun StatChip(label: String, value: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(4.dp))
            Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

// ── BookStackView — 水平偏移堆叠 + 交替旋转角度 ──

@Composable
private fun BookStackView(covers: List<BookReadRecordItem>) {
    val step = 12.dp
    val stackWidth = 44.dp + (step * (covers.size - 1).coerceAtLeast(0))
    val rotations = listOf(5f, 3f, -3f, 0f,-8f)
    
    Box(
        modifier = Modifier.width(stackWidth).height(64.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        covers.forEachIndexed { i, item ->
            val rev = covers.size - 1 - i
            val rot = rotations[i % rotations.size]
            Surface(
                modifier = Modifier
                    .padding(start = step * rev)
                    .zIndex(rev.toFloat())
                    .graphicsLayer { rotationZ = rot },
                shadowElevation = 4.dp,
                shape = RoundedCornerShape(4.dp),
                color = Color.Transparent,
            ) {
                BookCoverCompose(coverUrl = item.coverPath, name = item.bookName, author = item.author,
                    modifier = Modifier.width(44.dp), compact = true)
            }
        }
    }
}

// ── Book Item ──

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookItem(
    item: BookReadRecordItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardBg = legadoCardBackgroundColor()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(12.dp), color = cardBg, shadowElevation = 0.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCoverCompose(coverUrl = item.coverPath, name = item.bookName, author = item.author,
                modifier = Modifier.size(48.dp, 64.dp), compact = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.bookName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.author.isNotBlank()) Text(item.author, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("最后阅读: ${ReadRecordFormatter.formatDateShort(item.lastRead)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            Text(ReadRecordFormatter.formatDuring(item.readTime), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
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

private fun AndroidView.requestParentDisallowIntercept(disallow: Boolean) {
    var viewParent = parent
    while (viewParent != null) {
        viewParent.requestDisallowInterceptTouchEvent(disallow)
        viewParent = viewParent.parent
    }
}