@file:OptIn(ExperimentalSharedTransitionApi::class)

package io.legado.app.ui.common.compose

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.model.BookCover

private const val SharedCoverRadiusCacheMaxSize = 256
private val sharedCoverRadiusCache = mutableStateMapOf<String, Dp>()

/**
 * Compose 原生书籍封面组件，替代基于 AndroidView 的 BookCoverImage。
 * 支持 SharedTransitionScope 共享元素过渡动画。
 * 在线封面未加载时，显示默认封面背景图 + 书名/作者文字覆盖层。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BookCoverCompose(
    coverUrl: String?,
    name: String = "",
    author: String = "",
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    compact: Boolean = false,
    radius: Dp = if (compact) 4.dp else 8.dp,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
    showShadow: Boolean = false,
) {
    val context = LocalContext.current
    val useDefaultCover = coverUrl.isNullOrBlank()
        || coverUrl == "use_default_cover"
        || AppConfig.useDefaultCover
    val finalPath = if (useDefaultCover) null else coverUrl

    // 参与共享元素过渡时，乐观假定在线封面已缓存命中，避免过渡动画中
    // 目标侧短暂显示默认封面再切换为在线封面造成的闪烁。
    var isOnlineCoverLoaded by remember(finalPath) {
        mutableStateOf(sharedCoverKey != null && finalPath != null)
    }
    var hasEverLoadedSuccessfully by remember(finalPath) { mutableStateOf(false) }

    LaunchedEffect(finalPath) {
        if (finalPath == null) {
            isOnlineCoverLoaded = false
        }
    }

    val isInSharedTransition = sharedCoverKey != null && animatedVisibilityScope != null

    val transitionRadius = if (isInSharedTransition) {
        rememberSharedCoverTransitionRadius(
            sharedCoverKey = sharedCoverKey,
            radius = radius,
            animatedVisibilityScope = animatedVisibilityScope
        )
    } else {
        radius
    }
    val shape = remember(transitionRadius) { RoundedCornerShape(transitionRadius) }

    val sharedElementModifier = with(sharedTransitionScope) {
        if (this != null && animatedVisibilityScope != null && sharedCoverKey != null) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(sharedCoverKey),
                animatedVisibilityScope = animatedVisibilityScope,
                clipInOverlayDuringTransition = OverlayClip(shape)
            )
        } else Modifier
    }

    val showDefaultCover = !isOnlineCoverLoaded

    Box(
        modifier = modifier
            .then(sharedElementModifier)
            .then(if (showShadow) Modifier.shadow(4.dp, shape) else Modifier)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape,
            )
            .clip(shape)
    ) {
        // Online cover image — no placeholder, keeps transparent to let default cover show through
        if (finalPath != null) {
            val requestNoPlaceholder = remember(context, finalPath) {
                ImageRequest.Builder(context)
                    .data(finalPath)
                    .apply {
                        extras[io.legado.app.help.coil.LegadoFetcher.loadOnlyWifiKey] = false
                    }
                    .build()
            }
            AsyncImage(
                model = requestNoPlaceholder,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onSuccess = {
                    isOnlineCoverLoaded = true
                    hasEverLoadedSuccessfully = true
                },
                onError = {
                    if (sharedCoverKey == null || !hasEverLoadedSuccessfully) {
                        isOnlineCoverLoaded = false
                    }
                },
            )
        }

        // Default cover: BookCover.defaultDrawable background + text overlay
        if (showDefaultCover) {
            AsyncImage(
                model = BookCover.defaultDrawable,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )

            CoverTextOverlay(name = name, author = author)
        }
    }
}

/**
 * 共享封面过渡动画中的圆角半径变化。
 * 从缓存的前一个半径值平滑过渡到当前半径。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun rememberSharedCoverTransitionRadius(
    sharedCoverKey: String,
    radius: Dp,
    animatedVisibilityScope: AnimatedVisibilityScope,
): Dp {
    val transition = animatedVisibilityScope.transition
    val startRadius = sharedCoverRadiusCache[sharedCoverKey] ?: radius
    val animatedRadiusValue by transition.animateFloat(
        label = "book-cover-corner-radius"
    ) { state ->
        if (state == EnterExitState.Visible) radius.value else startRadius.value
    }

    LaunchedEffect(
        sharedCoverKey, radius,
        transition.currentState, transition.targetState
    ) {
        if (transition.currentState == EnterExitState.Visible
            && transition.targetState == EnterExitState.Visible
        ) {
            sharedCoverRadiusCache[sharedCoverKey] = radius
            if (sharedCoverRadiusCache.size > SharedCoverRadiusCacheMaxSize) {
                sharedCoverRadiusCache.keys
                    .firstOrNull { it != sharedCoverKey }
                    ?.let(sharedCoverRadiusCache::remove)
            }
        }
    }

    return animatedRadiusValue.dp
}

/**
 * 在默认封面上绘制书名/作者文字。
 * 竖排布局：书名左侧，作者右侧，白色描边 + 主题色填充。
 * 复刻 CoverImageView 的绘制行为。
 */
@Composable
private fun CoverTextOverlay(
    name: String?,
    author: String?,
) {
    val showName = BookCover.drawBookName
    val showAuthor = BookCover.drawBookAuthor
    if (!showName && !showAuthor) return

    val context = LocalContext.current
    val accentColorInt = try {
        context.accentColor
    } catch (_: Exception) {
        Color(0xFF263238.toInt()).toArgb()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val viewWidth = size.width
        val viewHeight = size.height

        if (showName && !name.isNullOrBlank()) {
            val namePaint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                textSize = viewWidth / 6f
            }
            var startX = viewWidth * 0.16f
            var startY = viewHeight * 0.16f
            val fm = namePaint.fontMetrics
            val charHeight = fm.bottom - fm.top

            name.forEach { char ->
                // White stroke
                drawIntoCanvas { canvas ->
                    val strokePaint = Paint(namePaint).apply {
                        color = android.graphics.Color.WHITE
                        style = Paint.Style.STROKE
                        strokeWidth = namePaint.textSize / 5
                    }
                    canvas.nativeCanvas.drawText(char.toString(), startX, startY, strokePaint)
                }
                // Accent fill
                drawIntoCanvas { canvas ->
                    namePaint.color = accentColorInt
                    namePaint.style = Paint.Style.FILL
                    canvas.nativeCanvas.drawText(char.toString(), startX, startY, namePaint)
                }
                startY += charHeight
                if (startY > viewHeight * 0.8f) {
                    startX += namePaint.textSize * 1.2f
                    startY = viewHeight * 0.2f
                }
            }
        }

        if (showAuthor && !author.isNullOrBlank()) {
            val authorPaint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = viewWidth / 10f
            }
            val startX = viewWidth * 0.84f
            val fm = authorPaint.fontMetrics
            val charHeight = fm.bottom - fm.top
            var startY = viewHeight * 0.16f - (author.length * charHeight)
            startY = startY.coerceAtLeast(viewHeight * 0.2f)

            author.forEach { char ->
                // White stroke
                drawIntoCanvas { canvas ->
                    val strokePaint = Paint(authorPaint).apply {
                        color = android.graphics.Color.WHITE
                        style = Paint.Style.STROKE
                        strokeWidth = authorPaint.textSize / 5
                    }
                    canvas.nativeCanvas.drawText(char.toString(), startX, startY, strokePaint)
                }
                // Accent fill
                drawIntoCanvas { canvas ->
                    authorPaint.color = accentColorInt
                    authorPaint.style = Paint.Style.FILL
                    canvas.nativeCanvas.drawText(char.toString(), startX, startY, authorPaint)
                }
                startY += charHeight
            }
        }
    }
}
