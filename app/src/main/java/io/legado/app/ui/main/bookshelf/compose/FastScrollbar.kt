package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.legado.app.ui.common.compose.LocalAnimationsEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * Compose 原生快速滚动条。
 *
 * @param lazyListState 关联的 LazyListState
 * @param itemCount     列表总条目数
 * @param visible       是否显示（受 AppConfig 控制）
 */
@Composable
fun FastScrollbar(
    lazyListState: LazyListState,
    itemCount: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible || itemCount <= 0) return

    val animationsEnabled = LocalAnimationsEnabled.current
    val density = LocalDensity.current

    // 是否正在拖拽
    var isDragging by remember { mutableStateOf(false) }
    // 闲置后自动隐藏
    var isActive by remember { mutableStateOf(false) }

    // 拖拽结束后 1.5 秒淡出
    LaunchedEffect(isDragging) {
        if (isDragging) {
            isActive = true
        } else {
            delay(1500)
            isActive = false
        }
    }

    // 滚动时短暂显示
    val isScrolling = lazyListState.isScrollInProgress
    LaunchedEffect(isScrolling) {
        if (isScrolling) {
            isActive = true
        } else if (!isDragging) {
            delay(1500)
            isActive = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = if (animationsEnabled) tween(300) else tween(0),
        label = "fastScrollbarAlpha",
    )

    if (alpha <= 0f) return

    // 计算把手位置比例
    val fraction = if (itemCount > 0) {
        val firstIndex = lazyListState.firstVisibleItemIndex
        val offset = lazyListState.firstVisibleItemScrollOffset
        // 简单估算：假设每个 item 高度相近
        (firstIndex.toFloat() / itemCount.coerceAtLeast(1)).coerceIn(0f, 1f)
    } else 0f

    val handleHeightDp = 36.dp
    val trackWidthDp = 6.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(alpha),
        contentAlignment = Alignment.CenterEnd,
    ) {
        // 半透明轨道背景
        Box(
            modifier = Modifier
                .width(trackWidthDp)
                .fillMaxHeight()
                .padding(end = 2.dp)
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    CircleShape,
                ),
        )

        // 可拖拽把手
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(trackWidthDp)
                .padding(end = 2.dp)
                .pointerInput(itemCount) {
                    val scope = kotlinx.coroutines.CoroutineScope(coroutineContext)
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                    ) { change, dragAmount ->
                        change.consume()
                        val trackHeightPx = size.height.toFloat()
                        if (trackHeightPx <= 0f) return@detectDragGestures
                        val delta = dragAmount.y / trackHeightPx
                        val currentIndex = lazyListState.firstVisibleItemIndex
                        val targetIndex = (currentIndex + (delta * itemCount).toInt())
                            .coerceIn(0, (itemCount - 1).coerceAtLeast(0))
                        scope.launch { lazyListState.scrollToItem(targetIndex) }
                    }
                }
                .pointerInput(itemCount) {
                    val scope = kotlinx.coroutines.CoroutineScope(coroutineContext)
                    detectTapGestures { offset ->
                        val trackHeightPx = size.height.toFloat()
                        if (trackHeightPx <= 0f) return@detectTapGestures
                        val tapFraction = (offset.y / trackHeightPx).coerceIn(0f, 1f)
                        val targetIndex = (tapFraction * (itemCount - 1)).roundToInt()
                            .coerceIn(0, (itemCount - 1).coerceAtLeast(0))
                        scope.launch { lazyListState.scrollToItem(targetIndex) }
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val trackHeightPx = constraints.maxHeight
                        val handlePx = with(density) { handleHeightDp.toPx() }
                        val y = ((trackHeightPx - handlePx) * fraction)
                            .coerceIn(0f, trackHeightPx - handlePx)
                            .roundToInt()
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(0, y)
                        }
                    }
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        CircleShape,
                    ),
            )
        }
    }
}
