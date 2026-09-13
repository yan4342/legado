package io.legado.app.ui.book.info.compose

import android.view.ViewGroup
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import coil3.compose.AsyncImage
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.model.BookCover
import io.legado.app.ui.common.compose.BookCoverCompose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope

/**
 * 详情页 Hero Header：模糊封面背景 + 大封面 + 书名 + 作者。
 */
@Composable
fun HeroHeader(
    book: Book,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
    coverTransitionName: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
) {
    val context = LocalContext.current
    val coverUrl = book.getDisplayCover()
    // 判断是否使用默认封面：无封面、哨兵值 use_default_cover、或用户开启全局默认封面
    val useDefaultCover = coverUrl.isNullOrBlank() ||
        coverUrl == "use_default_cover" ||
        runCatching { AppConfig.useDefaultCover }.getOrDefault(false)
    // 墨水屏模式下不显示动画效果
    val isEInkMode = runCatching { AppConfig.isEInkMode }.getOrDefault(false)
    // 【书源来源】— 异步查询书源名称，显示在作者名下方；本地书无书源，显示"本地"
    val sourceName by produceState<String?>(null, book.origin, book.isLocal, book.originName) {
        if (!book.isLocal) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    appDb.bookSourceDao.getBookSource(book.origin)?.bookSourceName
                }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: book.originName.takeIf { it.isNotBlank() }
            }
        }
    }
    val showSource = book.isLocal || !sourceName.isNullOrBlank()
    // 状态栏高度，用于让模糊背景延伸到状态栏区域
    val statusBarHeightDp = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    // 【Hero 区域总高度】
    // 竖屏：260dp + 状态栏 — 大封面展示区域
    // 横屏：220dp + 状态栏 — 加大模糊背景，增强视觉层次
    val heroTotalHeight = if (isLandscape) 220.dp + statusBarHeightDp
        else 260.dp + statusBarHeightDp
    // 【模糊背景高度】— 封面模糊图片的纵向范围
    // 竖屏：240dp + 状态栏；横屏：200dp + 状态栏（加大模糊区域）
    val bgHeight = if (isLandscape) 200.dp + statusBarHeightDp
        else 240.dp + statusBarHeightDp
    // 【封面行上边距】— 避开 TopAppBar，将封面与书名下移
    val topPadding = if (isLandscape) 60.dp + statusBarHeightDp
        else 60.dp + statusBarHeightDp
    // 【封面尺寸】
    val coverWidth = if (isLandscape) 70.dp else 90.dp
    val coverHeight = if (isLandscape) 98.dp else 126.dp
    // 【书名样式】— 竖屏双行大字，横屏单行
    val titleStyle = if (isLandscape) MaterialTheme.typography.titleLarge
        else MaterialTheme.typography.headlineMedium
    val titleMaxLines = if (isLandscape) 1 else 2

    // 【Hero 根容器】— 高度由 heroTotalHeight 控制，包含模糊背景 + 封面信息
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroTotalHeight),
    ) {
        // 【背景区域】— 根据封面类型选择不同背景
        // key 确保切换封面类型时 Composable 完整重建
        key(useDefaultCover, isEInkMode, coverUrl) {
            if (useDefaultCover && !isEInkMode) {
                // 默认封面：bookdetailbg.webp + 呼吸同心圆
                DefaultCoverBackground(
                    coverCenterXDp = 20.dp + coverWidth / 2,
                    coverCenterYDp = topPadding + coverHeight / 2,
                    bgHeight = bgHeight,
                )
            } else {
                // 【模糊封面背景】— 使用 Coil 加载封面图片并应用 25dp 高斯模糊
                AsyncImage(
                    model = BookCover.loadBlurRequest(context, coverUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bgHeight)
                        .blur(25.dp),
                )
            }
        }

        // 【渐变遮罩】— 半透明黑色渐变覆盖背景，统一暗色基调
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(bgHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.1f),
                            Color.Black.copy(alpha = 0.15f),
                            Color.Black.copy(alpha = 0.2f),
                        ),
                    ),
                ),
        )

        // 【封面 + 书名/作者 横向布局】— 左封面右信息，topPadding 避开 TopAppBar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPadding, start = 20.dp, end = 20.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // 【封面组件】— Compose 原生封面，支持共享元素过渡动画
            BookCoverCompose(
                coverUrl = coverUrl,
                name = book.name,
                author = book.getRealAuthor(),
                modifier = Modifier
                    .size(width = coverWidth, height = coverHeight),
                radius = 10.dp,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                sharedCoverKey = sharedCoverKey,
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 【书名 + 作者】— 竖屏双行书名，横屏单行
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                Text(
                    text = book.name,
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = titleMaxLines,
                )
                if (book.author.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = book.getRealAuthor(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (showSource) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (book.isLocal) {
                            stringResource(R.string.local)
                        } else {
                            sourceName.orEmpty()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 默认封面背景：bookdetailbg.webp 底图 + 呼吸同心圆线框。
 * 圆心对齐书籍封面中心，同心圆统一淡入淡出。
 */
@Composable
private fun DefaultCoverBackground(
    coverCenterXDp: Dp,
    coverCenterYDp: Dp,
    bgHeight: Dp,
    modifier: Modifier = Modifier,
) {
    // 统一呼吸动画：所有同心圆一起淡入淡出
    val infiniteTransition = rememberInfiniteTransition(label = "breathTransition")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.03f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathAlpha",
    )

    // 同心圆半径（由内向外）
    val circleRadii = remember {
        listOf(60.dp, 90.dp, 130.dp, 180.dp, 240.dp, 310.dp)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(bgHeight),
    ) {
        // bookdetailbg.webp 底图
        Image(
            painter = painterResource(R.drawable.bookdetailbg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // 同心圆线框呼吸动画
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(
                x = coverCenterXDp.toPx(),
                y = coverCenterYDp.toPx(),
            )

            // 从大到小绘制外圈到底层
            circleRadii.reversed().forEach { radiusDp ->
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = radiusDp.toPx(),
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
private fun HeroHeaderPreview() {
    val sampleBook = Book(
        name = "凡人修仙传",
        author = "忘语",
        kind = "仙侠, 奇幻, 修仙",
    )
    MaterialTheme {
        HeroHeader(book = sampleBook)
    }
}
