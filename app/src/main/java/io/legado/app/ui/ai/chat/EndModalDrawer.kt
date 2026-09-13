package io.legado.app.ui.ai.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DrawerGestureThreshold = 48.dp

/**
 * Horizontal swipe detector that uses **accumulated** drag distance.
 *
 * [detectHorizontalDragGestures]'s `dragAmount` is a per-frame delta; treating it as
 * total travel made the end drawer nearly impossible to open with a normal swipe.
 */
private fun Modifier.horizontalSwipeGestures(
    thresholdPx: Float,
    enabled: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(thresholdPx) {
        var total = 0f
        var handled = false
        detectHorizontalDragGestures(
            onDragStart = {
                total = 0f
                handled = false
            },
            onDragEnd = {
                if (handled) return@detectHorizontalDragGestures
                when {
                    total <= -thresholdPx -> onSwipeLeft()
                    total >= thresholdPx -> onSwipeRight()
                }
            },
            onDragCancel = {
                total = 0f
                handled = false
            },
            onHorizontalDrag = { _, dragAmount ->
                if (handled) return@detectHorizontalDragGestures
                total += dragAmount
                when {
                    total <= -thresholdPx -> {
                        handled = true
                        onSwipeLeft()
                    }
                    total >= thresholdPx -> {
                        handled = true
                        onSwipeRight()
                    }
                }
            },
        )
    }
}

@Composable
fun EndModalDrawer(
    isOpen: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    gesturesEnabled: Boolean = true,
    isLeftDrawerOpen: Boolean = false,
    onOpenLeftDrawer: () -> Unit = {},
    onCloseLeftDrawer: () -> Unit = {},
    scrimColor: Color = Color.Black.copy(alpha = 0.32f),
    drawerContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    drawerWidth: Dp = 300.dp,
    drawerContent: @Composable ColumnScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val thresholdPx = with(LocalDensity.current) { DrawerGestureThreshold.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        // rememberUpdatedState: gesture lambdas always see latest callbacks without
        // restarting the pointerInput coroutine.
        val currentIsOpen by rememberUpdatedState(isOpen)
        val currentIsLeftDrawerOpen by rememberUpdatedState(isLeftDrawerOpen)
        val currentOnClose by rememberUpdatedState(onClose)
        val currentOnOpen by rememberUpdatedState(onOpen)
        val currentOnCloseLeftDrawer by rememberUpdatedState(onCloseLeftDrawer)
        val currentOnOpenLeftDrawer by rememberUpdatedState(onOpenLeftDrawer)

        val onSwipeLeft = {
            when {
                currentIsOpen -> currentOnClose()
                currentIsLeftDrawerOpen -> currentOnCloseLeftDrawer()
                else -> currentOnOpen()
            }
        }
        val onSwipeRight = {
            when {
                currentIsOpen -> currentOnClose()
                currentIsLeftDrawerOpen -> currentOnCloseLeftDrawer()
                else -> currentOnOpenLeftDrawer()
            }
        }

        // 1 - Main content with unified horizontal gesture for both drawers
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalSwipeGestures(
                    thresholdPx = thresholdPx,
                    enabled = gesturesEnabled,
                    onSwipeLeft = onSwipeLeft,
                    onSwipeRight = onSwipeRight,
                ),
        ) {
            content()
        }

        // 2 - Scrim with tap-to-close and swipe-to-close
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { currentOnClose() }
                    .horizontalSwipeGestures(
                        thresholdPx = thresholdPx,
                        enabled = true,
                        onSwipeLeft = { /* already open; left swipe ignored */ },
                        onSwipeRight = { currentOnClose() },
                    ),
            )
        }

        // 3 - Drawer sheet from right
        AnimatedVisibility(
            visible = isOpen,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
        ) {
            Surface(
                modifier = Modifier
                    .width(drawerWidth)
                    .fillMaxHeight()
                    .horizontalSwipeGestures(
                        thresholdPx = thresholdPx,
                        enabled = true,
                        onSwipeLeft = { /* already open */ },
                        onSwipeRight = { currentOnClose() },
                    ),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                color = drawerContainerColor,
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    drawerContent()
                }
            }
        }
    }
}
