package io.legado.app.ui.common.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * 与 [ScrollState] 关联的纵向滚动条，适用于 Column + verticalScroll。
 */
@Composable
fun VerticalScrollStateScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    fadeWhenIdle: Boolean = true,
) {
    if (scrollState.maxValue <= 0) return

    val animationsEnabled = LocalAnimationsEnabled.current
    val density = LocalDensity.current
    val minHandlePx = with(density) { 24.dp.toPx() }

    var isDragging by remember { mutableStateOf(false) }
    var isActive by remember { mutableStateOf(!fadeWhenIdle) }

    LaunchedEffect(isDragging) {
        if (isDragging) {
            isActive = true
        } else if (fadeWhenIdle) {
            delay(1500)
            isActive = false
        }
    }

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            isActive = true
        } else if (!isDragging && fadeWhenIdle) {
            delay(1500)
            isActive = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isActive || !fadeWhenIdle) 1f else 0f,
        animationSpec = if (animationsEnabled) tween(300) else tween(0),
        label = "scrollStateScrollbarAlpha",
    )

    if (alpha <= 0f) return

    val trackWidthDp = 6.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(alpha),
        contentAlignment = Alignment.CenterEnd,
    ) {
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

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(trackWidthDp)
                .padding(end = 2.dp)
                .pointerInput(scrollState.maxValue) {
                    val scope = CoroutineScope(coroutineContext)
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                    ) { change, dragAmount ->
                        change.consume()
                        val trackHeightPx = size.height.toFloat()
                        if (trackHeightPx <= 0f || scrollState.maxValue <= 0) return@detectDragGestures
                        val viewportPx = trackHeightPx
                        val contentPx = scrollState.maxValue + viewportPx
                        val thumbHeightPx = (viewportPx * viewportPx / contentPx)
                            .coerceAtLeast(minHandlePx)
                            .coerceAtMost(trackHeightPx)
                        val scrollableTrackPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
                        val deltaFraction = dragAmount.y / scrollableTrackPx
                        val target = (scrollState.value + deltaFraction * scrollState.maxValue)
                            .roundToInt()
                            .coerceIn(0, scrollState.maxValue)
                        scope.launch { scrollState.scrollTo(target) }
                    }
                }
                .pointerInput(scrollState.maxValue) {
                    val scope = CoroutineScope(coroutineContext)
                    detectTapGestures { offset ->
                        val trackHeightPx = size.height.toFloat()
                        if (trackHeightPx <= 0f || scrollState.maxValue <= 0) return@detectTapGestures
                        val viewportPx = trackHeightPx
                        val contentPx = scrollState.maxValue + viewportPx
                        val thumbHeightPx = (viewportPx * viewportPx / contentPx)
                            .coerceAtLeast(minHandlePx)
                            .coerceAtMost(trackHeightPx)
                        val scrollableTrackPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
                        val tapFraction = ((offset.y - thumbHeightPx / 2f) / scrollableTrackPx)
                            .coerceIn(0f, 1f)
                        val target = (tapFraction * scrollState.maxValue).roundToInt()
                            .coerceIn(0, scrollState.maxValue)
                        scope.launch { scrollState.scrollTo(target) }
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val trackHeightPx = constraints.maxHeight.toFloat()
                        if (!constraints.hasBoundedHeight || trackHeightPx <= 0f) {
                            layout(placeable.width, 0) {
                                placeable.placeRelative(0, 0)
                            }
                        } else {
                        val viewportPx = trackHeightPx
                        val contentPx = scrollState.maxValue + viewportPx
                        val thumbHeightPx = (viewportPx * viewportPx / contentPx)
                            .coerceAtLeast(minHandlePx)
                            .coerceAtMost(trackHeightPx)
                        val scrollableTrackPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
                        val fraction = if (scrollState.maxValue > 0) {
                            scrollState.value.toFloat() / scrollState.maxValue
                        } else {
                            0f
                        }
                        val y = (scrollableTrackPx * fraction).roundToInt()
                        layout(placeable.width, thumbHeightPx.roundToInt()) {
                            placeable.placeRelative(0, y)
                        }
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
