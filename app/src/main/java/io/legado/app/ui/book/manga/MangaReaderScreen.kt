package io.legado.app.ui.book.manga

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.toBitmap
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.transformations
import io.legado.app.R
import io.legado.app.help.coil.LegadoFetcher
import io.legado.app.ui.book.manga.config.MangaScrollMode
import io.legado.app.ui.book.manga.config.MangaDoublePageMode
import io.legado.app.ui.book.manga.config.MangaPageScaleType
import io.legado.app.ui.book.manga.config.MangaWidePageMode
import io.legado.app.ui.common.compose.LegadoAlertDialog
import androidx.compose.material3.Button
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.EnabledZoomGestures
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import org.koin.compose.koinInject
import kotlin.math.ceil
import kotlin.math.roundToInt

private val LocalReaderViewportSize = staticCompositionLocalOf { IntSize.Zero }
private val LocalReaderViewportOrigin = staticCompositionLocalOf { Offset.Zero }
private val LocalMangaAspectRatios = staticCompositionLocalOf<MutableMap<String, Float>> {
    mutableMapOf()
}
internal data class MangaPageEdgeColors(
    val top: Color,
    val bottom: Color,
)

private val LocalMangaBackgroundColors = staticCompositionLocalOf<MutableMap<String, MangaPageEdgeColors>> {
    mutableMapOf()
}

private const val MIN_WEBTOON_ZOOM = 0.5f
private const val MAX_WEBTOON_ZOOM = 3f
private const val WEBTOON_DOUBLE_TAP_ZOOM = 2.5f
private const val MIN_PAGE_ZOOM = 1f
private const val MAX_PAGE_ZOOM = 3f

/**
 * 放大后的平移边界：横向限制在单页宽度溢出的范围内，纵向限制在放大后的内容高度内。
 */
internal fun clampZoomPan(
    target: Offset,
    zoom: Float,
    itemWidth: Float,
    contentHeight: Float,
    viewport: IntSize,
): Offset {
    if (contentHeight <= 0f) return Offset.Zero
    val width = viewport.width.coerceAtLeast(1).toFloat()
    val height = viewport.height.coerceAtLeast(1).toFloat()
    val maxX = ((itemWidth * zoom - width) / 2f).coerceAtLeast(0f)
    val maxY = (contentHeight * zoom - height).coerceAtLeast(0f)
    return Offset(
        x = target.x.coerceIn(-maxX, maxX),
        y = target.y.coerceIn(-maxY, 0f),
    )
}

@Composable
fun MangaReaderScreen(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader = koinInject(),
) {
    BackHandler { onIntent(MangaReaderIntent.BackPressed) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var viewportOrigin by remember { mutableStateOf(Offset.Zero) }
    val aspectRatios = remember { mutableStateMapOf<String, Float>() }
    val automaticBackgrounds = remember { mutableStateMapOf<String, MangaPageEdgeColors>() }
    val readerBackground = state.settings.backgroundColor.copy(alpha = 1f)

    LaunchedEffect(
        state.autoReadEnabled,
        state.settings.autoReadSpeed,
        state.menuVisible,
        state.activeSheet,
        state.settingsCategory,
    ) {
        val isWebtoon = state.settings.scrollMode == MangaScrollMode.WEBTOON ||
                state.settings.scrollMode == MangaScrollMode.WEBTOON_WITH_GAP
        if (!state.autoReadEnabled || state.menuVisible || state.activeSheet != null ||
            state.settingsCategory != null || isWebtoon
        ) {
            return@LaunchedEffect
        }
        while (true) {
            delay(state.settings.autoReadSpeed.coerceAtLeast(1) * 1_000L)
            onIntent(MangaReaderIntent.PageStep(1))
        }
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pendingMessage = state.pendingMessages.firstOrNull()
    LaunchedEffect(pendingMessage?.id, context, lifecycleOwner) {
        val message = pendingMessage ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            Toast.makeText(
                context,
                message.content.resolve(context),
                Toast.LENGTH_SHORT,
            ).show()
            onIntent(MangaReaderIntent.MessageShown(message.id))
        }
    }
    // 只有 key/retryRevision 变化才重启预取（loadState 更新仅改列表内容，列表相等，
    // 不触发重启）；已就绪的页不再重复入队，避免 onStart 把已显示的图打回加载态。
    val pagePrefetchRevision = state.pages.filterIsInstance<MangaReaderItemUi.Page>()
        .map { it.key to it.retryRevision }
    DisposableEffect(
        pagePrefetchRevision,
        state.currentItemIndex,
        state.settings.preDownloadCount,
        state.settings.sourceOrigin,
        state.settings.enableEInk,
        state.settings.enableGray,
    ) {
        val ahead = state.settings.preDownloadCount.coerceIn(0, 10)
        val current = state.currentItemIndex
        // 当前页优先，其后向前预取，再回扫 2 页：滑块跳转时先命中目标页
        val prioritizedIndices = buildList {
            add(current)
            addAll((current + 1..current + ahead).filter { it in state.pages.indices })
            addAll((current - 1 downTo current - 2).filter { it in state.pages.indices })
        }.distinct()
        val requests = if (ahead == 0) emptyList() else prioritizedIndices
            .mapNotNull(state.pages::getOrNull)
            .filterIsInstance<MangaReaderItemUi.Page>()
            .filterNot { it.loadState == MangaPageLoadState.Ready }
            .map { page ->
                imageLoader.enqueue(
                    page.imageRequest(
                        settings = state.settings,
                        context = context,
                        onAspectRatio = { aspectRatios[page.key] = it },
                        onStart = { onIntent(MangaReaderIntent.PageLoadStarted(page.key)) },
                        onSuccess = { onIntent(MangaReaderIntent.PageLoadSucceeded(page.key)) },
                        onError = { message ->
                            onIntent(MangaReaderIntent.PageLoadFailed(page.key, message))
                        },
                    ).newBuilder()
                        // Keep prefetched pages available to the reader request. Disabling this
                        // caused a slider jump to decode the same image again from scratch.
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build()
                )
            }
        onDispose { requests.forEach { it.dispose() } }
    }
    CompositionLocalProvider(
        LocalReaderViewportSize provides viewportSize,
        LocalReaderViewportOrigin provides viewportOrigin,
        LocalMangaAspectRatios provides aspectRatios,
        LocalMangaBackgroundColors provides automaticBackgrounds,
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
                .onGloballyPositioned { viewportOrigin = it.positionInRoot() }
                .background(readerBackground),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(readerBackground)
            ) {
                when (state.settings.scrollMode) {
                    MangaScrollMode.PAGE_LEFT_TO_RIGHT,
                    MangaScrollMode.PAGE_RIGHT_TO_LEFT -> HorizontalMangaPager(state, onIntent, imageLoader)
                    MangaScrollMode.PAGE_TOP_TO_BOTTOM -> VerticalMangaPager(state, onIntent, imageLoader)
                    else -> WebtoonMangaList(state, onIntent, imageLoader)
                }
            }

            MangaFooter(state)
            MangaReaderMenu(state, onIntent)
            MangaSettingsSheet(state, onIntent)
            MangaReaderSheetsHost(state, onIntent)
            ReaderStatusOverlay(state, onIntent)
            if (state.activeDialog == MangaReaderDialog.ClickAreaConfig) {
                MangaClickAreaConfigOverlay(state, onIntent)
            }
        }
    }
    LegadoAlertDialog(
        show = state.activeDialog is MangaReaderDialog.AddToShelf,
        onDismissRequest = { onIntent(MangaReaderIntent.DismissDialog) },
        dialogTitle = stringResource(R.string.add_to_bookshelf),
        text = stringResource(R.string.check_add_bookshelf, state.bookName),
        confirmText = stringResource(R.string.ok),
        onConfirm = { onIntent(MangaReaderIntent.AddCurrentBookToShelf) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(MangaReaderIntent.DiscardCurrentBookAndExit) },
    )
    val progressDialog = state.activeDialog as? MangaReaderDialog.ConfirmProgress
    LegadoAlertDialog(
        show = progressDialog != null,
        onDismissRequest = { onIntent(MangaReaderIntent.DismissDialog) },
        dialogTitle = stringResource(R.string.get_book_progress),
        text = stringResource(R.string.cloud_progress_exceeds_current),
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            progressDialog?.progress?.let { onIntent(MangaReaderIntent.ApplyReadingProgress(it)) }
            onIntent(MangaReaderIntent.DismissDialog)
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(MangaReaderIntent.DismissDialog) },
    )
}

private fun MangaReaderText.resolve(context: android.content.Context): String = when (this) {
    is MangaReaderText.Dynamic -> value
    is MangaReaderText.Resource -> context.getString(resId, *args.toTypedArray())
}

@Composable
private fun WebtoonMangaList(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
    imageLoader: ImageLoader,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentItemIndex)
    val coroutineScope = rememberCoroutineScope()
    var pendingWebtoonTap by remember { mutableStateOf<Job?>(null) }
    var lastWebtoonTapAt by remember { mutableStateOf(0L) }
    var lastWebtoonTapPosition by remember { mutableStateOf(Offset.Unspecified) }
    val viewportSize = LocalReaderViewportSize.current
    val aspectRatios = LocalMangaAspectRatios.current
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val fraction = 1f - state.settings.sidePaddingPercent.coerceIn(0, 45) * 2f / 100f

    // 估算自然内容高度（放大后用于限制平移范围）；未加载的图片用整屏高度兜底
    val density = LocalDensity.current
    val fallbackPageHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val edgeHeightPx = with(density) { 96.dp.toPx() }
    val gapPx = with(density) { 8.dp.toPx() }
    val itemWidthPx = viewportSize.width * fraction
    val hasGap = state.settings.scrollMode == MangaScrollMode.WEBTOON_WITH_GAP
    val naturalContentHeight = state.pages.fold(0f) { acc, item ->
        val itemHeight = when (item) {
            is MangaReaderItemUi.Page -> {
                val ratio = aspectRatios[item.key]
                if (ratio != null && ratio > 0f) itemWidthPx / ratio else fallbackPageHeightPx
            }

            is MangaReaderItemUi.ChapterEdge,
            is MangaReaderItemUi.ChapterTransition -> edgeHeightPx
        }
        acc + itemHeight + if (hasGap) gapPx else 0f
    }
    // transformable 回调是 remember 住的，包一层 always-current 的值
    val latestNaturalContentHeight by rememberUpdatedState(naturalContentHeight)
    val latestItemWidthPx by rememberUpdatedState(itemWidthPx)
    val latestViewportSize by rememberUpdatedState(viewportSize)

    // 双击缩放：变换式（graphicsLayer），条目布局高度恒定，用滚动位移保持点击处内容不动。
    // 屏幕 y = zoom*(contentY - scroll)，令缩放前后点击处的 contentY 不变 =>
    // Δscroll = tap.y*(1/z0 - 1/z1)。缩小不再强制吸回页顶（旧实现依赖重排坐标，易触发
    // 自动切章/视觉跳动）；tap 落在分隔条/过渡条上也能锚定（不再依赖图片页尺寸）。
    fun toggleWebtoonZoom(tap: Offset) {
        val from = zoom
        val to = if (from > 1f) 1f else WEBTOON_DOUBLE_TAP_ZOOM
        val ratio = if (from > 0f) to / from else 1f
        zoom = to
        pan = clampZoomPan(
            target = Offset(
                x = (tap.x - latestViewportSize.width / 2f) * (1f - ratio),
                y = 0f,
            ),
            zoom = to,
            itemWidth = latestItemWidthPx,
            contentHeight = latestNaturalContentHeight,
            viewport = latestViewportSize,
        )
        // 同帧原子调整滚动，避免 requestScrollToItem 在重排中的竞态
        listState.dispatchRawDelta(tap.y * (1f / from - 1f / to))
    }

    // 列表使用真实的缩放后宽度测量，避免 LazyColumn 先按未缩放宽度裁掉图片两侧。
    // 外层 viewport 保持固定，负责九宫格命中和横向平移。
    // 手势：多点触控一律吞掉（双指可朝任意方向平移 + 捏合缩放），单指交给 LazyColumn 滚动。
    // 捏合缩放关闭时双指仍可平移（双击放大后移动视野），只是 zoomChange 恒为 1。
    val gestureModifier = if (
        state.settings.disableScale && state.settings.disableDoubleTapZoom
    ) {
        Modifier
    } else {
        Modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var handled = false
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            val centroid = event.calculateCentroid()
                            val zoomChange = if (state.settings.disableScale) 1f else event.calculateZoom()
                            val panChange = event.calculatePan()
                            val currentZoom = zoom
                            val newZoom =
                                (currentZoom * zoomChange).coerceIn(MIN_WEBTOON_ZOOM, MAX_WEBTOON_ZOOM)
                            val ratio = if (currentZoom > 0f) newZoom / currentZoom else 1f
                            if (ratio != 1f) {
                                handled = true
                                val width = latestViewportSize.width.coerceAtLeast(1).toFloat()
                                val height = latestViewportSize.height.coerceAtLeast(1).toFloat()
                                val center = Offset(width / 2f, height / 2f)
                                val effectiveCentroid = centroid.takeIf { it.isSpecified } ?: center
                                pan = pan * ratio + (effectiveCentroid - center) * (1f - ratio)
                            }
                            if (newZoom <= 1f) {
                                pan = Offset.Zero
                            } else {
                                if (panChange != Offset.Zero) handled = true
                                pan = clampZoomPan(
                                    target = Offset(pan.x + panChange.x, 0f),
                                    zoom = newZoom,
                                    itemWidth = latestItemWidthPx,
                                    contentHeight = latestNaturalContentHeight,
                                    viewport = latestViewportSize,
                                )
                                if (panChange.y != 0f) listState.dispatchRawDelta(-panChange.y)
                            }
                            if (handled) pressed.forEach { it.consume() }
                            zoom = newZoom
                        } else if (pressed.size == 1) {
                            if (handled) break
                            // 放大状态下单指横向平移：只取横向分量更新 pan.x（不消费事件，
                            // 纵向分量仍由 LazyColumn 滚动，二者可同时响应斜向拖动）。
                            // 未放大时不做任何事，避免与 pager/列表滚动冲突。
                            if (zoom > 1f) {
                                val delta = pressed.first().positionChange()
                                if (delta.x != 0f) {
                                    pan = clampZoomPan(
                                        target = Offset(pan.x + delta.x, 0f),
                                        zoom = zoom,
                                        itemWidth = latestItemWidthPx,
                                        contentHeight = latestNaturalContentHeight,
                                        viewport = latestViewportSize,
                                    )
                                }
                            }
                        }
                    } while (pressed.isNotEmpty())
                }
            }
    }

    LaunchedEffect(listState, state.pages, state.navigationId) {
        snapshotFlow {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val visibleItemIndices = visibleItems.map { it.index }
            val currentChapterVisible = visibleItems.any { item ->
                (state.pages.getOrNull(item.index) as? MangaReaderItemUi.Page)?.chapterIndex == state.chapterIndex
            }
            val firstItemIndex = visibleItems.firstOrNull()?.index
            // 聚焦页=视口底部页；跨章边界处按阅读方向取邻章贴边页，避免直接把会话
            // 提升到邻章最后一个可见页（进度跳章）。
            val focusedItemIndex = mangaWebtoonFocusedPageIndex(
                items = state.pages,
                visibleItemIndices = visibleItemIndices,
                currentChapterIndex = state.chapterIndex,
            )
            if (firstItemIndex == null || focusedItemIndex == null) null
            else Triple(focusedItemIndex, firstItemIndex, currentChapterVisible)
        }
            .distinctUntilChanged()
            .collect { entry ->
                entry?.let { (focusedIndex, firstIndex, stillVisible) ->
                    when (val item = state.pages.getOrNull(focusedIndex)) {
                        is MangaReaderItemUi.Page -> onIntent(MangaReaderIntent.VisibleItemChanged(
                            itemIndex = focusedIndex,
                            firstItemIndex = firstIndex,
                            lastItemIndex = focusedIndex,
                            currentChapterVisible = stillVisible,
                            navigationId = state.navigationId,
                        ))
                        is MangaReaderItemUi.ChapterTransition -> Unit
                        is MangaReaderItemUi.ChapterEdge, null -> Unit
                    }
                }
            }
    }
    LaunchedEffect(state.scrollRequest?.id) {
        state.scrollRequest?.let {
            if (it.animated) listState.animateScrollToItem(it.itemIndex)
            else listState.scrollToItem(it.itemIndex)
        }
    }
    LaunchedEffect(
        state.autoReadEnabled,
        state.settings.autoReadSpeed,
        state.menuVisible,
        state.activeSheet,
        state.settingsCategory,
    ) {
        if (!state.autoReadEnabled || state.menuVisible || state.activeSheet != null ||
            state.settingsCategory != null
        ) return@LaunchedEffect
        val distance = state.settings.autoReadSpeed.coerceAtLeast(1)
        val duration = ceil(16f / distance * 10_000f).toInt()
        while (true) {
            val consumed = listState.animateScrollBy(
                value = 10_000f,
                animationSpec = tween(durationMillis = duration, easing = LinearEasing),
            )
            if (consumed < 1f) {
                onIntent(MangaReaderIntent.NextChapter)
                delay(500L)
            }
        }
    }
    // 变换式缩放：列表布局宽度恒定（不随 zoom 放大），zoom 由 graphicsLayer 纯变换应用，
    // 避免条目按 width/aspectRatio 重新测量导致的整段重排乱跳。
    val listWidth = with(density) {
        viewportSize.width.coerceAtLeast(1).toDp()
    }

    fun performWebtoonTap(tap: Offset) {
        val action = mangaClickActionAt(
            clickActions = state.settings.clickActions,
            x = tap.x,
            y = tap.y,
            width = viewportSize.width,
            height = viewportSize.height,
        )
        when (action) {
            1, 2 -> if (!state.settings.disableClickScroll) {
                val direction = if (action == 1) 1 else -1
                coroutineScope.launch {
                    val distance = viewportSize.height * direction.toFloat()
                    val consumed = if (state.settings.disableScrollAnimation) {
                        listState.scrollBy(distance)
                    } else {
                        listState.animateScrollBy(distance)
                    }
                    if (kotlin.math.abs(consumed) < 1f) {
                        onIntent(
                            if (direction > 0) MangaReaderIntent.NextChapter
                            else MangaReaderIntent.PreviousChapter
                        )
                    }
                }
            }

            else -> performMangaClickAction(action, onIntent)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Navigation belongs to the viewport, not to an individual transformed strip item.
            // This keeps the nine-grid stable while the list scrolls, zooms, or pans.
            .pointerInput(state.settings, viewportSize, state.pages) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    var latest = start
                    var pointerCount = 1
                    var upAt = down.uptimeMillis
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        pointerCount = maxOf(pointerCount, event.changes.count { it.pressed })
                        event.changes.firstOrNull()?.let {
                            latest = it.position
                            upAt = it.uptimeMillis
                        }
                    } while (event.changes.any { it.pressed })

                    val moved = (latest - start).getDistance() > viewConfiguration.touchSlop
                    if (pointerCount > 1 || moved) return@awaitEachGesture
                    val heldFor = upAt - down.uptimeMillis
                    if (heldFor >= viewConfiguration.longPressTimeoutMillis &&
                        state.settings.longPressEnabled
                    ) {
                        pendingWebtoonTap?.cancel()
                        val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                            latest.y >= info.offset && latest.y < info.offset + info.size
                        }
                        val page = visibleItem?.let { state.pages.getOrNull(it.index) }
                            as? MangaReaderItemUi.Page
                        page?.let { onIntent(MangaReaderIntent.LongPressPage(it.key)) }
                        return@awaitEachGesture
                    }

                    // 双击判定不参与 disableScale：双击时恒取消待执行的单击动作（否则禁用缩放时
                    // 双击会连翻两页）；是否实际缩放由「双击缩放」设置决定。
                    // 双击缩放关闭时单击立即执行（双击 = 两次滚动动作），开启时延迟单击等双击判定。
                    val isDoubleTap =
                        upAt - lastWebtoonTapAt <= viewConfiguration.doubleTapTimeoutMillis &&
                        lastWebtoonTapPosition.isSpecified &&
                        (latest - lastWebtoonTapPosition).getDistance() <= viewConfiguration.touchSlop * 2
                    if (isDoubleTap) {
                        pendingWebtoonTap?.cancel()
                        pendingWebtoonTap = null
                        lastWebtoonTapAt = 0L
                        lastWebtoonTapPosition = Offset.Unspecified
                        if (!state.settings.disableDoubleTapZoom) toggleWebtoonZoom(latest)
                    } else {
                        lastWebtoonTapAt = upAt
                        lastWebtoonTapPosition = latest
                        pendingWebtoonTap?.cancel()
                        // 放大状态下单击不触发导航动作（翻页/章节/菜单），避免与双击缩放互相干扰：
                        // 放大时只保留双击缩小、捏合与平移，缩回 1x 后单击恢复正常。
                        if (zoom > 1f) {
                            // zoomed: single tap is a no-op
                        } else if (state.settings.disableDoubleTapZoom) {
                            performWebtoonTap(latest)
                        } else {
                            pendingWebtoonTap = coroutineScope.launch {
                                delay(viewConfiguration.doubleTapTimeoutMillis)
                                // 延迟期间可能已捏合放大：放大状态下单击不导航
                                if (zoom <= 1f) performWebtoonTap(latest)
                                pendingWebtoonTap = null
                            }
                        }
                    }
                }
            }
            .then(gestureModifier),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .requiredWidth(listWidth)
                .fillMaxHeight()
                .clipToBounds()
                .graphicsLayer {
                    // 变换式缩放：从顶部中心放大，横向平移由 pan.x 补偿；
                    // 纵向滚动仍由 LazyColumn 在布局空间处理，条目高度不随 zoom 变化。
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (state.settings.scrollMode == MangaScrollMode.WEBTOON_WITH_GAP) {
                Arrangement.spacedBy(8.dp)
            } else Arrangement.Top,
        ) {
            items(
                count = state.pages.size,
                key = { state.pages[it].key },
                contentType = { state.pages[it]::class },
            ) { index ->
                MangaReaderItem(
                    item = state.pages[index],
                    settings = state.settings,
                    onIntent = onIntent,
                    imageLoader = imageLoader,
                    modifier = Modifier.fillMaxWidth(fraction),
                    paged = false,
                )
            }
        }
    }
}

@Composable
private fun HorizontalMangaPager(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
    imageLoader: ImageLoader,
) {
    val viewport = LocalReaderViewportSize.current
    val aspectRatios = LocalMangaAspectRatios.current
    val useDoublePage = isDoublePageActive(state.settings.doublePageMode, viewport)
    val aspectRatioSnapshot = aspectRatios.toMap()
    val spreads = remember(state.pages, useDoublePage, aspectRatioSnapshot, state.settings) {
        buildMangaSpreads(
            state.pages,
            useDoublePage,
            aspectRatioSnapshot,
            coverSingle = state.settings.doublePageCoverSingle,
            shiftPairing = state.settings.doublePageShift,
            splitWidePages = state.settings.widePageMode == MangaWidePageMode.SPLIT,
            splitRightToLeft =
                (state.settings.scrollMode == MangaScrollMode.PAGE_RIGHT_TO_LEFT) xor
                        state.settings.doublePageInvert,
        )
    }
    val initialSpread = spreads.indexOfFirst { state.currentItemIndex in it }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialSpread,
        pageCount = { spreads.size.coerceAtLeast(1) },
    )
    var spreadLayoutReady by remember { mutableStateOf(false) }
    LaunchedEffect(spreads, state.currentItemIndex) {
        spreadLayoutReady = false
        val target = spreads.indexOfFirst { state.currentItemIndex in it }
        if (target >= 0 && target != pagerState.currentPage) pagerState.scrollToPage(target)
        spreadLayoutReady = true
    }
    LaunchedEffect(pagerState, spreads, state.pages, state.navigationId) {
        snapshotFlow {
            val spread = spreads.getOrNull(pagerState.currentPage)
            val indices = spread?.itemIndices.orEmpty()
            val itemIndex = state.scrollRequest?.itemIndex?.takeIf { it in indices }
                ?: indices.firstOrNull()
            if (!spreadLayoutReady) null
            // 滚动中不切章：随 fling 越过章节边界时延迟到落定再上报，避免窗口重建期误切。
            // 把 isScrollInProgress 并入快照，滚动结束会产生新值从而重发聚焦页。
            else Triple(pagerState.isScrollInProgress, indices, itemIndex)
        }.distinctUntilChanged()
            .collect { entry ->
                val (isScrolling, indices, itemIndex) = entry ?: return@collect
                if (isScrolling) return@collect
                itemIndex?.let { index ->
                    when (val item = state.pages.getOrNull(index)) {
                        is MangaReaderItemUi.Page -> onIntent(MangaReaderIntent.VisibleItemChanged(
                            itemIndex = index,
                            firstItemIndex = indices.first(),
                            lastItemIndex = indices.last(),
                            currentChapterVisible = indices.any { visibleIndex ->
                                (state.pages.getOrNull(visibleIndex) as? MangaReaderItemUi.Page)
                                    ?.chapterIndex == state.chapterIndex
                            },
                            navigationId = state.navigationId,
                        ))
                        is MangaReaderItemUi.ChapterTransition -> Unit
                        is MangaReaderItemUi.ChapterEdge, null -> Unit
                    }
                }
            }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { onIntent(MangaReaderIntent.PagerScrollChanged(it)) }
    }
    LaunchedEffect(state.scrollRequest?.id, spreads) {
        state.scrollRequest?.let {
            val spreadIndex = spreads.indexOfFirst { spread -> it.itemIndex in spread }
            if (spreadIndex >= 0) {
                if (it.animated) pagerState.animateScrollToPage(spreadIndex)
                else pagerState.scrollToPage(spreadIndex)
            }
        }
    }
    // 单击/双击不再由 pager 层识别：点击区域动作统一交给页面内 telephoto 的 onClick 仲裁
    // （onTap 会等双击窗口过期才触发，与 onDoubleClick 天然互斥，不会出现"缩放时翻页"）。
    HorizontalPager(
        state = pagerState,
        key = { spreads.getOrNull(it)?.key ?: "empty:$it" },
        reverseLayout = state.settings.scrollMode == MangaScrollMode.PAGE_RIGHT_TO_LEFT,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        val slots = spreads.getOrNull(page)?.slots.orEmpty()
        val reverseSpread =
            (state.settings.scrollMode == MangaScrollMode.PAGE_RIGHT_TO_LEFT) xor
                    state.settings.doublePageInvert
        val displaySlots = if (slots.size == 2 && reverseSpread) slots.reversed() else slots
        MangaHorizontalSpread(
            slots = displaySlots,
            state = state,
            onIntent = onIntent,
            imageLoader = imageLoader,
        )
    }
}

@Composable
private fun MangaHorizontalSpread(
    slots: List<MangaPageSlot>,
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
    imageLoader: ImageLoader,
) {
    val viewport = LocalReaderViewportSize.current
    if (slots.size < 2) {
        val slot = slots.firstOrNull()
        state.pages.getOrNull(slot?.itemIndex ?: -1)?.let {
            MangaReaderItem(
                it,
                state.settings,
                onIntent,
                imageLoader,
                Modifier
                    .fillMaxSize()
                    .pageSlice(slot?.slice ?: MangaPageSlice.FULL),
                paged = true,
            )
        }
        return
    }
    val zoomableState = rememberZoomableState(
        ZoomSpec(
            maxZoomFactor = MAX_PAGE_ZOOM,
            minZoomFactor = MIN_PAGE_ZOOM,
        )
    )
    val spreadSize = remember { mutableStateOf(IntSize.Zero) }
    val coroutineScope = rememberCoroutineScope()
    // 单击/双击仲裁统一交给 telephoto：onTap 延迟到双击窗口过期才触发，与 onDoubleClick 天然互斥。
    // 「捏合关+双击开」是 telephoto 公开预设无法表达的组合（PanOnly 不触发 quick zoom），
    // 由本层 detectTapGestures 兜底；此时 telephoto 必须收到全空回调进入被动模式
    // （不消费 down/up），否则子节点先消费事件会把本层检测器饿死（Main pass 子先于父）。
    val needsOwnDoubleTap = state.settings.usesOwnDoubleTapZoom()
    val doubleClickToZoom = when {
        state.settings.disableDoubleTapZoom || needsOwnDoubleTap -> null
        else -> DoubleClickToZoomListener.cycle(WEBTOON_DOUBLE_TAP_ZOOM)
    }
    // telephoto 的 onTap 对"按下→短滑动→松手"也会触发，用位移标记剔除滑动手势
    var gestureMoved by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxSize()
            .onSizeChanged { spreadSize.value = it }
            .markGestureCrossedSlop { gestureMoved = it }
            .then(
                if (needsOwnDoubleTap) {
                    Modifier.pointerInput(state.settings, viewport) {
                        detectTapGestures(
                            onTap = { tap ->
                                if (!gestureMoved) {
                                    clickAction(
                                        settings = state.settings,
                                        onIntent = onIntent,
                                        offset = tap,
                                        width = viewport.width,
                                        height = viewport.height,
                                    )
                                }
                            },
                            onLongPress = if (state.settings.longPressEnabled) { tap ->
                                val slot = if (tap.x < spreadSize.value.width / 2f) 0 else 1
                                val page = slots.getOrNull(slot)?.itemIndex
                                    ?.let(state.pages::getOrNull) as? MangaReaderItemUi.Page
                                page?.let {
                                    val companion = slots.getOrNull(if (slot == 0) 1 else 0)
                                        ?.itemIndex?.let(state.pages::getOrNull)
                                        as? MangaReaderItemUi.Page
                                    onIntent(
                                        MangaReaderIntent.LongPressPage(
                                            it.key,
                                            companion?.takeIf { other -> other.key != it.key }?.key,
                                            companionBeforePage = slot == 1,
                                        )
                                    )
                                }
                            } else null,
                            onDoubleTap = { tap ->
                                coroutineScope.launch {
                                    zoomableState.togglePagedDoubleTapZoom(tap)
                                }
                            },
                        )
                    }
                } else {
                    Modifier
                }
            )
            .then(
                if (needsOwnDoubleTap) {
                    // 放大后的单指平移：PanOnly 下 telephoto 的变换检测整体禁用（含平移），
                    // 这里程序化驱动 zoomableState.panBy；消费移动事件避免误判单击/ pager 抢滚动
                    Modifier.pointerInput(state.settings, viewport) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var handled = false
                            do {
                                val event = awaitPointerEvent()
                                val pressedCount = event.changes.count { it.pressed }
                                if (pressedCount == 1 && (zoomableState.zoomFraction ?: 0f) > 0f) {
                                    val delta = event.changes.firstOrNull { it.pressed }
                                        ?.positionChange() ?: Offset.Zero
                                    if (delta != Offset.Zero) {
                                        handled = true
                                        coroutineScope.launch {
                                            zoomableState.panBy(delta, SnapSpec())
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                } else if (pressedCount > 1) {
                                    handled = true
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                } else {
                    Modifier
                }
            )
            .zoomable(
                state = zoomableState,
                gestures = state.settings.zoomGestures(),
                // 跨页 Row 铺满视口，本地坐标即视口坐标；放大状态下单击仍按区域响应（与旧版一致）
                onClick = if (!needsOwnDoubleTap) { tap ->
                    if (!gestureMoved) {
                        clickAction(
                            settings = state.settings,
                            onIntent = onIntent,
                            offset = tap,
                            width = viewport.width,
                            height = viewport.height,
                        )
                    }
                } else null,
                onLongClick = if (state.settings.longPressEnabled && !needsOwnDoubleTap) { tap ->
                    val slot = if (tap.x < spreadSize.value.width / 2f) 0 else 1
                    val page = slots.getOrNull(slot)?.itemIndex?.let(state.pages::getOrNull)
                        as? MangaReaderItemUi.Page
                    page?.let {
                        val companion = slots.getOrNull(if (slot == 0) 1 else 0)
                            ?.itemIndex?.let(state.pages::getOrNull) as? MangaReaderItemUi.Page
                        onIntent(
                            MangaReaderIntent.LongPressPage(
                                it.key,
                                companion?.takeIf { other -> other.key != it.key }?.key,
                                companionBeforePage = slot == 1,
                            )
                        )
                    }
                } else null,
                onDoubleClick = doubleClickToZoom,
            ),
    ) {
            slots.forEach { slot ->
                state.pages.getOrNull(slot.itemIndex)?.let {
                    MangaReaderItem(
                        it,
                        state.settings,
                        onIntent,
                        imageLoader,
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pageSlice(slot.slice),
                        paged = true,
                        pageInteractions = false,
                    )
                }
            }
    }
}

/**
 * 宽页拆分为左右半页：测量时强制按 2 倍宽测量原图，再用 [clipToBounds] 只露出对应一半。
 */
private fun Modifier.pageSlice(slice: MangaPageSlice): Modifier = when (slice) {
    MangaPageSlice.FULL -> this
    MangaPageSlice.LEFT, MangaPageSlice.RIGHT -> clipToBounds().layout { measurable, constraints ->
        val width = constraints.maxWidth
        val placeable = measurable.measure(
            constraints.copy(minWidth = width * 2, maxWidth = width * 2),
        )
        layout(width, placeable.height) {
            placeable.placeRelative(if (slice == MangaPageSlice.LEFT) 0 else -width, 0)
        }
    }
}

private fun Modifier.rotateWidePage(enabled: Boolean): Modifier = if (!enabled) this else {
    layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth,
            ),
        )
        val width = constraints.constrainWidth(placeable.height)
        val height = constraints.constrainHeight(placeable.width)
        layout(width, height) {
            placeable.placeWithLayer(
                x = (width - placeable.width) / 2,
                y = (height - placeable.height) / 2,
            ) { rotationZ = 90f }
        }
    }
}

@Composable
private fun VerticalMangaPager(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
    imageLoader: ImageLoader,
) {
    val pagerState = rememberPagerState(
        initialPage = state.currentItemIndex,
        pageCount = { state.pages.size.coerceAtLeast(1) },
    )
    LaunchedEffect(pagerState, state.pages, state.navigationId) {
        snapshotFlow {
            val item = state.pages.getOrNull(pagerState.currentPage)
            // 滚动中不切章：把 isScrollInProgress 并入快照，滚动结束产生新值从而重发聚焦页。
            Triple(pagerState.isScrollInProgress, pagerState.currentPage, item)
        }.distinctUntilChanged()
            .collect { (isScrolling, page, item) ->
                if (isScrolling) return@collect
                when (item) {
                    is MangaReaderItemUi.Page -> onIntent(MangaReaderIntent.VisibleItemChanged(
                        itemIndex = page,
                        currentChapterVisible = item.chapterIndex == state.chapterIndex,
                        navigationId = state.navigationId,
                    ))
                    is MangaReaderItemUi.ChapterTransition -> Unit
                    is MangaReaderItemUi.ChapterEdge, null -> Unit
                }
            }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { onIntent(MangaReaderIntent.PagerScrollChanged(it)) }
    }
    LaunchedEffect(state.scrollRequest?.id) {
        state.scrollRequest?.let {
            if (it.animated) pagerState.animateScrollToPage(it.itemIndex)
            else pagerState.scrollToPage(it.itemIndex)
        }
    }
    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        state.pages.getOrNull(page)?.let {
            MangaReaderItem(it, state.settings, onIntent, imageLoader, Modifier.fillMaxSize(), true)
        }
    }
}

@Composable
private fun MangaReaderItem(
    item: MangaReaderItemUi,
    settings: MangaReaderSettings,
    onIntent: (MangaReaderIntent) -> Unit,
    imageLoader: ImageLoader,
    modifier: Modifier,
    paged: Boolean,
    pageInteractions: Boolean = true,
) {
    // 非图片页（章节过渡/边缘）的滑动标记：detectTapGestures 对短滑动松手也会触发 onTap
    var gestureMoved by remember { mutableStateOf(false) }
    when (item) {
        is MangaReaderItemUi.Page -> MangaPageImage(
            page = item,
            settings = settings,
            onIntent = onIntent,
            imageLoader = imageLoader,
            modifier = modifier,
            paged = paged,
            interactionsEnabled = pageInteractions,
        )
        is MangaReaderItemUi.ChapterEdge -> Box(
            modifier = modifier
                .then(if (paged) Modifier.fillMaxHeight() else Modifier.height(96.dp))
                // 无 telephoto 的非图片页：仅分页模式在此兜底单击区域动作（旧版由 pager 层识别器
                // 处理）。条漫模式不在此处理——条漫由视口级识别器统一接管所有点击，子条目再处理
                // 会重复触发，且会用条目自身尺寸（非视口）映射九宫格导致区域错位。
                .then(
                    if (paged) {
                        Modifier
                            .markGestureCrossedSlop { gestureMoved = it }
                            .pointerInput(settings) {
                                detectTapGestures { tap ->
                                    if (!gestureMoved) {
                                        clickAction(
                                            settings = settings,
                                            onIntent = onIntent,
                                            offset = tap,
                                            width = size.width,
                                            height = size.height,
                                        )
                                    }
                                }
                            }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (item.loading) CircularProgressIndicator()
                Text(item.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.retryChapterIndex?.let { chapterIndex ->
                    Button(onClick = { onIntent(MangaReaderIntent.RetryChapter(chapterIndex)) }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
        is MangaReaderItemUi.ChapterTransition -> Box(
            modifier = modifier
                .then(if (paged) Modifier.fillMaxHeight() else Modifier.heightIn(min = 160.dp))
                .then(
                    if (paged) {
                        Modifier
                            .markGestureCrossedSlop { gestureMoved = it }
                            .pointerInput(settings) {
                                detectTapGestures { tap ->
                                    if (!gestureMoved) {
                                        clickAction(
                                            settings = settings,
                                            onIntent = onIntent,
                                            offset = tap,
                                            width = size.width,
                                            height = size.height,
                                        )
                                    }
                                }
                            }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val topLabel = if (item.direction == MangaChapterTransitionDirection.PREVIOUS) {
                    stringResource(R.string.manga_reader_transition_previous)
                } else {
                    stringResource(R.string.manga_reader_transition_current)
                }
                val bottomLabel = if (item.direction == MangaChapterTransitionDirection.PREVIOUS) {
                    stringResource(R.string.manga_reader_transition_current)
                } else {
                    stringResource(R.string.manga_reader_transition_next)
                }
                val topChapter = if (item.direction == MangaChapterTransitionDirection.PREVIOUS) {
                    item.targetChapterName
                } else {
                    item.currentChapterName
                }
                val bottomChapter = if (item.direction == MangaChapterTransitionDirection.PREVIOUS) {
                    item.currentChapterName
                } else {
                    item.targetChapterName
                }
                Text(
                    "$topLabel：",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    topChapter ?: stringResource(
                        if (item.direction == MangaChapterTransitionDirection.PREVIOUS) {
                            R.string.manga_reader_no_previous_chapter
                        } else {
                            R.string.manga_reader_no_next_chapter
                        }
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$bottomLabel：",
                    modifier = Modifier.padding(top = 18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    bottomChapter ?: stringResource(
                        if (item.direction == MangaChapterTransitionDirection.PREVIOUS) {
                            R.string.manga_reader_no_previous_chapter
                        } else {
                            R.string.manga_reader_no_next_chapter
                        }
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.targetStatus == MangaChapterTransitionStatus.LOADING) {
                    CircularProgressIndicator()
                }
                item.statusMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item.retryChapterIndex?.let { chapterIndex ->
                    Button(onClick = { onIntent(MangaReaderIntent.RetryChapter(chapterIndex)) }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaPageImage(
    page: MangaReaderItemUi.Page,
    settings: MangaReaderSettings,
    onIntent: (MangaReaderIntent) -> Unit,
    imageLoader: ImageLoader,
    modifier: Modifier,
    paged: Boolean,
    interactionsEnabled: Boolean,
) {
    var positionInRoot by remember(page.key) { mutableStateOf(Offset.Zero) }
    var imageViewportSize by remember(page.key) { mutableStateOf(IntSize.Zero) }
    val viewportSize = LocalReaderViewportSize.current
    val viewportOrigin = LocalReaderViewportOrigin.current
    val aspectRatios = LocalMangaAspectRatios.current
    val automaticBackgrounds = LocalMangaBackgroundColors.current
    val context = LocalContext.current
    val fallbackHeight = LocalConfiguration.current.screenHeightDp.dp
    val webtoonSizeModifier = if (paged) Modifier else {
        aspectRatios[page.key]?.takeIf { it > 0f }?.let { Modifier.aspectRatio(it) }
            ?: Modifier.height(fallbackHeight)
    }
    val imagePipelineKey = remember(
        page.key,
        settings.sourceOrigin,
        settings.enableEInk,
        settings.eInkThreshold,
        settings.enableGray,
        settings.disableCrossFade,
        page.retryRevision,
    ) {
        listOf(
            page.key,
            settings.sourceOrigin,
            settings.enableEInk,
            settings.eInkThreshold,
            settings.enableGray,
            settings.disableCrossFade,
            page.retryRevision,
        )
    }
    val request = remember(imagePipelineKey) {
        page.imageRequest(
            settings = settings,
            context = context,
            onAspectRatio = { ratio -> aspectRatios[page.key] = ratio },
            onStart = { onIntent(MangaReaderIntent.PageLoadStarted(page.key)) },
            onSuccess = { onIntent(MangaReaderIntent.PageLoadSucceeded(page.key)) },
            onError = { message ->
                onIntent(MangaReaderIntent.PageLoadFailed(page.key, message))
            },
        )
    }
    val imageRatio = aspectRatios[page.key]
    val isHorizontalPager = settings.scrollMode == MangaScrollMode.PAGE_LEFT_TO_RIGHT ||
        settings.scrollMode == MangaScrollMode.PAGE_RIGHT_TO_LEFT
    DisposableEffect(
        page.key,
        settings.autoBackground,
        settings.sourceOrigin,
        isHorizontalPager,
        imageRatio,
    ) {
        if (!isHorizontalPager || imageRatio == null ||
            !settings.autoBackground ||
            automaticBackgrounds.containsKey(page.key)
        ) {
            onDispose { }
        } else {
            val disposable = imageLoader.enqueue(
                page.backgroundColorRequest(
                    context = context,
                    sourceOrigin = settings.sourceOrigin,
                    fallbackColor = settings.backgroundColor,
                    aspectRatio = imageRatio,
                ) { colors ->
                    automaticBackgrounds[page.key] = colors
                },
            )
            onDispose(disposable::dispose)
        }
    }

    val isWidePage = imageRatio != null && imageRatio > 1f
    val contentScale = when {
        isWidePage && settings.widePageMode == MangaWidePageMode.FIT_WIDTH -> ContentScale.FillWidth
        settings.pageScaleType == MangaPageScaleType.STRETCH -> ContentScale.FillBounds
        settings.pageScaleType == MangaPageScaleType.FIT_WIDTH -> ContentScale.FillWidth
        settings.pageScaleType == MangaPageScaleType.FIT_HEIGHT -> ContentScale.FillHeight
        settings.pageScaleType == MangaPageScaleType.ORIGINAL -> ContentScale.None
        settings.pageScaleType == MangaPageScaleType.SMART_FIT && isWidePage -> ContentScale.FillWidth
        else -> ContentScale.Fit
    }
    val rotateWidePage = paged && isWidePage &&
        settings.widePageMode == MangaWidePageMode.ROTATE_TO_FIT
    val pageBackground = if (isHorizontalPager && settings.autoBackground) {
        automaticBackgrounds[page.key]?.let { colors ->
            Brush.verticalGradient(colors = listOf(colors.top, colors.bottom))
        }
    } else null

    val contentDescription = stringResource(
        R.string.manga_reader_page_description,
        page.chapterName,
        page.pageIndex + 1,
    )
    val imageModifier = modifier
        .then(webtoonSizeModifier)
        .rotateWidePage(rotateWidePage)
        .then(pageBackground?.let { Modifier.background(it) } ?: Modifier)
        .onSizeChanged { imageViewportSize = it }
        .onGloballyPositioned { positionInRoot = it.positionInRoot() }

    val zoomableImageState = rememberZoomableImageState(
        rememberZoomableState(
            ZoomSpec(
                maxZoomFactor = MAX_PAGE_ZOOM,
                minZoomFactor = MIN_PAGE_ZOOM,
            )
        ),
    )
    val coroutineScope = rememberCoroutineScope()
    // pointerInput 的 key 不能随滚动位置变化而重启，这里用 always-current 的引用
    val latestPositionInRoot by rememberUpdatedState(positionInRoot)
    val latestViewportOrigin by rememberUpdatedState(viewportOrigin)
    val latestViewportSize by rememberUpdatedState(viewportSize)
    if (paged && !interactionsEnabled) {
        // 跨页模式的半页：所有手势（单击/双击/长按/捏合）由跨页 Row 级 zoomable 统一处理，
        // 这里只负责渲染。不能用 ZoomableAsyncImage——它的 onDoubleClick 形参非空（默认
        // cycle()），内部点击节点会活跃并消费 down/up，把祖先 Row 级手势全部饿死。
        Box(imageModifier) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = contentScale,
                colorFilter = mangaColorFilter(settings),
                modifier = Modifier.fillMaxSize(),
            )
            MangaImageLoadOverlay(page, onIntent)
        }
        return
    }
    if (paged) {
        // 单击/双击仲裁统一交给 telephoto：onTap 延迟到双击窗口过期才触发，与 onDoubleClick
        // 天然互斥，不会出现"双击缩放时翻页"。gestureMoved 标记负责把"滑动后松手"从
        // 单击里剔除（telephoto 的 onTap 对未消费的短滑动也会在抬手时触发）。
        val needsOwnDoubleTap = settings.usesOwnDoubleTapZoom()
        var gestureMoved by remember { mutableStateOf(false) }
        val movementTracker = Modifier.markGestureCrossedSlop { gestureMoved = it }
        val pageTap: ((Offset) -> Unit) = { tap ->
            if (!gestureMoved) {
                clickAction(
                    settings = settings,
                    onIntent = onIntent,
                    offset = latestPositionInRoot + tap - latestViewportOrigin,
                    width = latestViewportSize.width,
                    height = latestViewportSize.height,
                )
            }
        }
        if (needsOwnDoubleTap) {
            // 「捏合关+双击开」：telephoto 公开 API 无法表达该组合——PanOnly 不含 QuickZoom
            // （双击永不触发）且会连平移一起禁用；ZoomAndPan 又会放开捏合；ZoomableAsyncImage
            // 的 onDoubleClick 形参非空、点击节点始终活跃消费事件，会饿死本层检测器。
            // 与条漫模式同一思路：zoom/pan 自管（graphicsLayer + clampZoomPan），手势全部
            // 由本层处理。链序注意：pan 处理器在 detectTapGestures 内侧（后声明），放大后
            // 消费移动事件以阻止误判单击并拦住 pager 抢滚动。
            var pageZoom by remember(page.key) { mutableFloatStateOf(1f) }
            var pagePan by remember(page.key) { mutableStateOf(Offset.Zero) }
            val latestPageZoom by rememberUpdatedState(pageZoom)
            val latestImageViewport by rememberUpdatedState(imageViewportSize)
            fun togglePageZoom(tap: Offset) {
                if (latestPageZoom > 1f) {
                    pageZoom = 1f
                    pagePan = Offset.Zero
                } else {
                    pageZoom = WEBTOON_DOUBLE_TAP_ZOOM
                    val width = latestImageViewport.width.coerceAtLeast(1).toFloat()
                    val height = latestImageViewport.height.coerceAtLeast(1).toFloat()
                    // 以点击点为锚：缩放围绕中心进行，再用平移把点击处内容拉回手指下
                    val ratio = WEBTOON_DOUBLE_TAP_ZOOM
                    pagePan = clampZoomPan(
                        target = Offset(
                            x = (width / 2f - tap.x) * (1f - ratio),
                            y = (height / 2f - tap.y) * (1f - ratio),
                        ),
                        zoom = WEBTOON_DOUBLE_TAP_ZOOM,
                        itemWidth = width,
                        contentHeight = height,
                        viewport = latestImageViewport,
                    )
                }
            }
            Box(
                imageModifier
                    .then(movementTracker)
                    .then(
                        Modifier.pointerInput(page.key) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var handled = false
                                do {
                                    val event = awaitPointerEvent()
                                    val pressedCount = event.changes.count { it.pressed }
                                    if (pressedCount == 1 && latestPageZoom > 1f) {
                                        val change = event.changes.firstOrNull { it.pressed }
                                        val delta = change?.positionChange() ?: Offset.Zero
                                        if (delta != Offset.Zero) {
                                            handled = true
                                            val width = latestImageViewport.width.coerceAtLeast(1).toFloat()
                                            val height = latestImageViewport.height.coerceAtLeast(1).toFloat()
                                            pagePan = clampZoomPan(
                                                target = Offset(pagePan.x + delta.x, pagePan.y + delta.y),
                                                zoom = latestPageZoom,
                                                itemWidth = width,
                                                contentHeight = height,
                                                viewport = latestImageViewport,
                                            )
                                            event.changes.forEach { it.consume() }
                                        }
                                    } else if (pressedCount > 1) {
                                        // 捏合缩放已被设置关闭，多指不做任何事
                                        handled = true
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                    )
                    .then(
                        Modifier.pointerInput(
                            settings.clickActions,
                            settings.disableClickScroll,
                            settings.longPressEnabled,
                        ) {
                            detectTapGestures(
                                onTap = pageTap,
                                onLongPress = if (settings.longPressEnabled) { _ ->
                                    onIntent(MangaReaderIntent.LongPressPage(page.key))
                                } else null,
                                onDoubleTap = ::togglePageZoom,
                            )
                        },
                    ),
            ) {
                AsyncImage(
                    model = request,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    colorFilter = mangaColorFilter(settings),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = pageZoom
                            scaleY = pageZoom
                            translationX = pagePan.x
                            translationY = pagePan.y
                        },
                )
                MangaImageLoadOverlay(page, onIntent)
            }
            return
        }
        // 双击缩放关闭时传 no-op 监听器：吞掉双击（否则双击会连翻两页），单击延迟一个
        // 双击窗口响应。ZoomableAsyncImage 的 onDoubleClick 形参非空，无法传 null 被动化。
        val doubleClickToZoom = remember(settings.disableDoubleTapZoom) {
            if (settings.disableDoubleTapZoom) NoOpDoubleClickToZoom
            else DoubleClickToZoomListener.cycle(WEBTOON_DOUBLE_TAP_ZOOM)
        }
        Box(imageModifier.then(movementTracker)) {
            ZoomableAsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = contentDescription,
                state = zoomableImageState,
                gestures = settings.zoomGestures(),
                contentScale = contentScale,
                colorFilter = mangaColorFilter(settings),
                onDoubleClick = doubleClickToZoom,
                onClick = pageTap,
                onLongClick = if (settings.longPressEnabled) { _ ->
                    onIntent(MangaReaderIntent.LongPressPage(page.key))
                } else null,
                modifier = Modifier.fillMaxSize(),
            )
            MangaImageLoadOverlay(page, onIntent)
        }
        return
    }

    Box(imageModifier) {
        ZoomableAsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            state = zoomableImageState,
            gestures = EnabledZoomGestures.None,
            // 条漫的缩放/双击由外层自研手势系统处理。这里的默认 cycle() 监听器会让 telephoto
            // 消费事件，但条漫识别器走 Final pass 且 requireUnconsumed=false，不受影响；
            // 形参非空也无法传 null，保持默认即可。
            contentScale = ContentScale.FillWidth,
            colorFilter = mangaColorFilter(settings),
            modifier = Modifier.fillMaxSize(),
        )
        MangaImageLoadOverlay(page, onIntent)
    }
}

/**
 * 页面加载遮罩：失败态保留深色底便于重试按钮可读，并提供「重试本章失败页」；
 * 加载/排队态不再整页压黑罩，只显示转圈，避免已解码淡入的图被一层「阴影」盖住。
 */
@Composable
private fun MangaImageLoadOverlay(
    page: MangaReaderItemUi.Page,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    val loadState = page.loadState
    if (loadState is MangaPageLoadState.Ready) return
    if (loadState is MangaPageLoadState.Failed) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = { onIntent(MangaReaderIntent.RetryPage(page.key)) }) {
                    Text(stringResource(R.string.retry))
                }
                Button(onClick = {
                    onIntent(MangaReaderIntent.RetryFailedPagesInChapter(page.chapterIndex))
                }) {
                    Text(stringResource(R.string.manga_reader_retry_failed_chapter))
                }
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

private fun MangaReaderItemUi.Page.imageRequest(
    settings: MangaReaderSettings,
    context: android.content.Context,
    onAspectRatio: (Float) -> Unit = {},
    onStart: () -> Unit = {},
    onSuccess: () -> Unit = {},
    onError: (String?) -> Unit = {},
): ImageRequest {
    val memoryCacheKey = "manga-page:$bookUrl:$imageUrl:${settings.sourceOrigin}:" +
        "${settings.enableEInk}:${settings.eInkThreshold}:${settings.enableGray}"
    return ImageRequest.Builder(context)
        .data(imageUrl)
        .allowHardware(true)
        // The preload request has no view-size resolver while the displayed request does. A
        // shared key lets the displayed request reuse it immediately, then crossfade only if a
        // better-sized decode is needed.
        .memoryCacheKey(memoryCacheKey)
        .placeholderMemoryCacheKey(memoryCacheKey)
        .apply {
            extras[LegadoFetcher.mangaKey] = true
            extras[LegadoFetcher.sourceOriginKey] = settings.sourceOrigin
            extras[LegadoFetcher.mangaBookUrlKey] = bookUrl
        }
        .apply {
            when {
                settings.enableEInk -> transformations(MangaEInkTransformation(settings.eInkThreshold))
                settings.enableGray -> transformations(MangaGrayscaleTransformation)
            }
            crossfade(!settings.disableCrossFade)
        }
        .listener(
            onStart = { _ -> onStart() },
            onError = { _, result -> onError(result.throwable.localizedMessage) },
            onSuccess = { _, result ->
                val image = result.image
                if (image.width > 0 && image.height > 0) {
                    onAspectRatio(image.width.toFloat() / image.height)
                }
                onSuccess()
            },
        )
        .build()
}

private fun MangaReaderItemUi.Page.backgroundColorRequest(
    context: android.content.Context,
    sourceOrigin: String?,
    fallbackColor: Color,
    aspectRatio: Float,
    onColors: (MangaPageEdgeColors) -> Unit,
): ImageRequest = ImageRequest.Builder(context)
    .data(imageUrl)
    .let { builder ->
        backgroundDecodeSize(aspectRatio).let { size ->
            builder.size(size.width, size.height)
        }
    }
    .allowHardware(false)
    .apply {
        extras[LegadoFetcher.mangaKey] = true
        extras[LegadoFetcher.sourceOriginKey] = sourceOrigin
        extras[LegadoFetcher.mangaBookUrlKey] = bookUrl
    }
    .listener(onSuccess = { _, result ->
        onColors(extractMangaEdgeColors(result.image.toBitmap(), fallbackColor))
    })
    .build()

private fun backgroundDecodeSize(aspectRatio: Float): IntSize {
    val shortEdge = 256
    val longEdge = 1024
    val ratio = aspectRatio.coerceIn(0.05f, 20f)
    return if (ratio <= 1f) {
        IntSize(shortEdge, (shortEdge / ratio).roundToInt().coerceAtMost(longEdge))
    } else {
        IntSize((shortEdge * ratio).roundToInt().coerceAtMost(longEdge), shortEdge)
    }
}

/**
 * Extracts the two page-edge colors from a small decode of the page.
 *
 * Sampling several inset rows avoids a one-pixel scan line or a page border deciding the result.
 * The most common quantized color bucket preserves a large color block while rejecting sparse
 * line art and text. Four vertical edge tracks are also scanned in 25 segments, following the
 * Komikku approach, to recognize a reliably white or dark page edge.
 */
internal fun extractMangaEdgeColors(
    bitmap: android.graphics.Bitmap,
    fallbackColor: Color = Color.Black,
): MangaPageEdgeColors {
    fun edgeColor(top: Boolean): Color {
        if (bitmap.width == 0 || bitmap.height == 0) return fallbackColor

        val horizontalInset = (bitmap.width * 0.0275f).toInt().coerceAtMost(bitmap.width / 3)
        val verticalInset = (bitmap.height * 0.0125f).toInt()
        val stripHeight = (bitmap.height * 0.06f).toInt().coerceIn(2, 16)
        val startY = if (top) {
            verticalInset
        } else {
            (bitmap.height - verticalInset - stripHeight).coerceAtLeast(0)
        }
        val endY = (startY + stripHeight).coerceAtMost(bitmap.height)
        val startX = horizontalInset
        val endX = (bitmap.width - horizontalInset).coerceAtLeast(startX + 1)
        val stepX = ((endX - startX) / 96).coerceAtLeast(1)
        val stepY = ((endY - startY) / 8).coerceAtLeast(1)
        val buckets = mutableMapOf<Int, MutableList<Int>>()

        for (y in startY until endY step stepY) {
            for (x in startX until endX step stepX) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.alpha(pixel) < 128) continue
                val bucket =
                    (android.graphics.Color.red(pixel) / 24 shl 8) or
                        (android.graphics.Color.green(pixel) / 24 shl 4) or
                        (android.graphics.Color.blue(pixel) / 24)
                buckets.getOrPut(bucket) { mutableListOf() }.add(pixel)
            }
        }

        val dominant = buckets.maxByOrNull { it.value.size }?.value ?: return fallbackColor
        val dominantColor = Color(
            red = dominant.sumOf(android.graphics.Color::red) / dominant.size,
            green = dominant.sumOf(android.graphics.Color::green) / dominant.size,
            blue = dominant.sumOf(android.graphics.Color::blue) / dominant.size,
        )
        val edgeRun = mangaEdgeRun(bitmap, top)
        return when {
            edgeRun.white >= 6 -> Color.White
            edgeRun.dark >= 6 -> dominantColor.takeIf { it.isMangaDark() } ?: Color.Black
            else -> dominantColor
        }
    }

    return MangaPageEdgeColors(
        top = edgeColor(top = true),
        bottom = edgeColor(top = false),
    )
}

private data class MangaEdgeRun(val white: Int, val dark: Int)

private fun mangaEdgeRun(bitmap: android.graphics.Bitmap, top: Boolean): MangaEdgeRun {
    val left = (bitmap.width * 0.0275f).toInt().coerceIn(0, bitmap.width - 1)
    val right = (bitmap.width - left - 1).coerceAtLeast(left)
    val offset = (bitmap.width * 0.01f).toInt().coerceAtLeast(1)
    val tracks = intArrayOf(
        left,
        right,
        (left + offset).coerceAtMost(bitmap.width - 1),
        (right - offset).coerceAtLeast(0),
    )
    val classes = (0 until 25).map { index ->
        val y = if (top) {
            index * (bitmap.height - 1) / 24
        } else {
            (24 - index) * (bitmap.height - 1) / 24
        }
        val pixels = tracks.map { x -> bitmap.getPixel(x, y) }
        when {
            pixels.count { it.isMangaWhite() } >= 3 -> 1
            pixels.count { it.isMangaDark() } >= 3 -> -1
            else -> 0
        }
    }
    return MangaEdgeRun(
        white = classes.takeWhile { it == 1 }.size,
        dark = classes.takeWhile { it == -1 }.size,
    )
}

private fun Int.isMangaDark(): Boolean =
    android.graphics.Color.red(this) < 40 &&
        android.graphics.Color.green(this) < 40 &&
        android.graphics.Color.blue(this) < 40 &&
        android.graphics.Color.alpha(this) > 200

private fun Int.isMangaWhite(): Boolean =
    android.graphics.Color.red(this) + android.graphics.Color.green(this) +
        android.graphics.Color.blue(this) > 740 && android.graphics.Color.alpha(this) > 200

private fun Color.isMangaDark(): Boolean =
    red * 255f < 40f && green * 255f < 40f && blue * 255f < 40f

private fun mangaColorFilter(settings: MangaReaderSettings): ColorFilter? {
    if (settings.filterRed == 0 && settings.filterGreen == 0 &&
        settings.filterBlue == 0 && settings.filterAlpha == 0
    ) return null
    return ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        (255 - settings.filterRed) / 255f, 0f, 0f, 0f, 0f,
        0f, (255 - settings.filterGreen) / 255f, 0f, 0f, 0f,
        0f, 0f, (255 - settings.filterBlue) / 255f, 0f, 0f,
        0f, 0f, 0f, (255 - settings.filterAlpha) / 255f, 0f,
    )))
}

private fun clickAction(
    settings: MangaReaderSettings,
    onIntent: (MangaReaderIntent) -> Unit,
    offset: Offset,
    width: Int,
    height: Int,
) {
    val action = mangaClickActionAt(settings.clickActions, offset.x, offset.y, width, height)
    if ((action == 1 || action == 2) && settings.disableClickScroll) return
    performMangaClickAction(action, onIntent)
}

private fun performMangaClickAction(
    action: Int,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    when (action) {
        -1 -> Unit
        0 -> onIntent(MangaReaderIntent.ToggleMenu)
        1 -> onIntent(MangaReaderIntent.PageStep(1))
        2 -> onIntent(MangaReaderIntent.PageStep(-1))
        3 -> onIntent(MangaReaderIntent.NextChapter)
        4 -> onIntent(MangaReaderIntent.PreviousChapter)
    }
}

internal fun isDoublePageActive(mode: Int, viewport: IntSize): Boolean =
    mode == MangaDoublePageMode.ALWAYS ||
        mode == MangaDoublePageMode.LANDSCAPE && viewport.width > viewport.height

/**
 * 缩放手势矩阵（「捏合缩放」与「双击缩放」各自独立）：
 * - 捏合开（无论双击）→ ZoomAndPan：捏合可用；双击是否缩放由 onDoubleClick 监听器控制
 *   （双击关时传 null，单击立即响应，双击 = 两次区域动作）。
 * - 捏合关 + 双击开 → PanOnly：捏合不可用；双击/单击/长按由本层 detectTapGestures 兜底
 *   （见 [MangaReaderSettings.usesOwnDoubleTapZoom]，telephoto 公开预设无法表达「仅双击」，
 *   且 telephoto 必须全空回调才会进入不消费事件的被动模式）。
 * - 都关 → None。
 */
internal fun MangaReaderSettings.zoomGestures(): EnabledZoomGestures = when {
    !disableScale -> EnabledZoomGestures.ZoomAndPan
    !disableDoubleTapZoom -> EnabledZoomGestures.PanOnly
    else -> EnabledZoomGestures.None
}

/** 是否由本层（页面/跨页）自行实现双击缩放：仅「捏合关 + 双击开」需要兜底。 */
internal fun MangaReaderSettings.usesOwnDoubleTapZoom(): Boolean =
    disableScale && !disableDoubleTapZoom

/** 双击缩放关闭时传给 telephoto 的 no-op 监听器：吞掉 quick zoom，双击不缩放。 */
private val NoOpDoubleClickToZoom = DoubleClickToZoomListener { _, _ -> }

/**
 * 观察当前手势是否已越过滑动阈值（或出现多指），结果实时写回 [crossed]。
 * 只观察不消费：telephoto 的 onTap 对"按下→短滑动→松手"也会在抬手时触发，
 * 单击回调需先检查该标记，把滑动手势从点击区域动作里剔除。
 */
private fun Modifier.markGestureCrossedSlop(crossed: (Boolean) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            crossed(false)
            var pointerCount = 1
            val start = awaitFirstDown(requireUnconsumed = false).position
            var latest = start
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                pointerCount = maxOf(pointerCount, event.changes.count { it.pressed })
                event.changes.firstOrNull()?.let { change ->
                    latest = change.position
                    if (pointerCount > 1 ||
                        (latest - start).getDistance() > viewConfiguration.touchSlop
                    ) {
                        crossed(true)
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }

/**
 * 分页模式的双击缩放切换，语义与 telephoto 官方 [DoubleClickToZoomListener.cycle] 对齐：
 * 当前倍率距目标倍率不足 0.05 时复位，否则以点击点为锚放大到 [WEBTOON_DOUBLE_TAP_ZOOM]。
 * （旧实现用 zoomFraction > 0.05 判定复位，捏合到 1.3x 后双击会错误地直接复位。）
 */
private suspend fun ZoomableState.togglePagedDoubleTapZoom(tap: Offset) {
    val fraction = zoomFraction ?: 0f
    val currentFactor = MIN_PAGE_ZOOM + fraction * (MAX_PAGE_ZOOM - MIN_PAGE_ZOOM)
    if (WEBTOON_DOUBLE_TAP_ZOOM - currentFactor < 0.05f) {
        resetZoom()
    } else {
        zoomTo(
            zoomFactor = WEBTOON_DOUBLE_TAP_ZOOM,
            centroid = tap,
        )
    }
}

