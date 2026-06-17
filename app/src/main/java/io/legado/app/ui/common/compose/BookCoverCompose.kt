@file:OptIn(ExperimentalSharedTransitionApi::class)

package io.legado.app.ui.common.compose

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.image.CoverImageView

private const val SharedCoverRadiusCacheMaxSize = 256
private val sharedCoverRadiusCache = mutableStateMapOf<String, Dp>()

/**
 * Compose 原生书籍封面组件，替代基于 AndroidView 的 BookCoverImage。
 * 支持 SharedTransitionScope 共享元素过渡动画。
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
    showLoadingPlaceholder: Boolean = true,
    showShadow: Boolean = false,
) {
    val context = LocalContext.current
    val useDefaultCover = coverUrl.isNullOrBlank()
        || coverUrl == "use_default_cover"
        || AppConfig.useDefaultCover
    val finalPath = if (useDefaultCover) null else coverUrl

    // 参与共享元素过渡时，乐观假定在线封面已缓存命中，避免过渡动画中
    // 目标侧短暂显示默认封面再切换为在线封面造成的闪烁。
    // hasEverLoadedSuccessfully 用于区分：封面确实缓存命中（应保持乐观状态）
    // 与封面从未加载成功（应回退到默认封面）两种情况。
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

    // 共享过渡缩放效果：进入侧从 0.85 平滑过渡到 1.0，源侧始终 1.0。
    // 与共享元素的位置/大小 morphing 同步进行，无 snap、无回弹。
    val transitionScale = if (isInSharedTransition) {
        rememberSharedCoverTransitionScale(animatedVisibilityScope!!)
    } else {
        1f
    }

    Box(
        modifier = modifier
            .then(sharedElementModifier)
            .then(if (showShadow) Modifier.shadow(4.dp, shape) else Modifier)
            // 始终使用相同底色，避免 isOnlineCoverLoaded 状态切换时
            // 背景色跳变导致闪烁；在线封面图片加载完后完全覆盖此底色。
            .graphicsLayer {
                scaleX = transitionScale
                scaleY = transitionScale
            }
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape,
            )
            .clip(shape)
    ) {
        // Cover image — 无 placeholder，加载前保持透明让底层 CoverImageView 显现
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
                    // 共享过渡中，如果曾经成功加载过（缓存命中），忽略短暂 onError；
                    // 如果从未成功过（本地文件已丢失等真实失败），允许回退到默认封面
                    if (sharedCoverKey == null || !hasEverLoadedSuccessfully) {
                        isOnlineCoverLoaded = false
                    }
                },
            )
        }

        // Default cover: CoverImageView draws book name/author on default cover background.
        if (showDefaultCover) {
            AndroidView(
                factory = { ctx ->
                    io.legado.app.ui.widget.image.CoverImageView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { coverView ->
                    val path = if (useDefaultCover) null else coverUrl
                    // CoverImageView.load() 内部做了路径/文字去重，
                    // 路径和文字都没变时跳过重新加载，不会触发 defaultCover=true 闪烁
                    coverView.load(path, name, author)
                },
                modifier = Modifier.fillMaxSize(),
            )
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
 * 共享封面过渡中进入侧的缩放动画。
 * 过渡前 90% 保持 0.85，最后 10% 缩放至 1.0，消除到达终点才开始缩放的停顿感。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun rememberSharedCoverTransitionScale(
    animatedVisibilityScope: AnimatedVisibilityScope,
): Float {
    val transition = animatedVisibilityScope.transition
    val animatedScale by transition.animateFloat(
        transitionSpec = {
            when {
                initialState == EnterExitState.PreEnter && targetState == EnterExitState.Visible -> {
                    keyframes {
                        durationMillis = 300
                        0.85f at 0
                        0.85f at 270
                        1f at 300
                    }
                }
                else -> tween()
            }
        },
        label = "book-cover-scale"
    ) { state ->
        when (state) {
            EnterExitState.PreEnter -> 0.85f
            EnterExitState.Visible -> 1f
            EnterExitState.PostExit -> 1f
        }
    }
    return animatedScale
}