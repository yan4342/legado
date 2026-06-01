package io.legado.app.ui.book.info.compose

import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.model.BookCover
import io.legado.app.ui.widget.image.CoverImageView

/**
 * 详情页 Hero Header：模糊封面背景 + 大封面 + 书名 + 作者。
 */
@Composable
fun HeroHeader(
    book: Book,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coverUrl = book.getDisplayCover()
    // 状态栏高度，用于让模糊背景延伸到状态栏区域
    val statusBarHeightDp = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }

    // 260dp: 模糊背景高度；+statusBarHeightDp: 延伸到状态栏
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp + statusBarHeightDp),
    ) {
        // 模糊封面背景（260dp = 背景实际高度，20dp 为底部留白）
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
            },
            update = { imageView ->
                BookCover.loadBlur(context, coverUrl, false, null)
                    .into(imageView)
            },
            modifier = Modifier
                .fillMaxWidth()
                // 260dp: 模糊背景高度，与渐变遮罩一致；+statusBarHeightDp 覆盖状态栏
                .height(260.dp + statusBarHeightDp)
                .blur(25.dp),
        )

        // 渐变遮罩 — 覆盖模糊背景，统一黑色（260dp + statusBarHeightDp）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp + statusBarHeightDp)
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

        // 封面（左）+ 书名/作者（右）同行，下移避开 TopAppBar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 80.dp + statusBarHeightDp, start = 20.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 使用 CoverImageView，支持在默认封面上绘制书名/作者
            AndroidView(
                factory = { ctx ->
                    CoverImageView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    }
                },
                update = { coverView ->
                    coverView.load(coverUrl, book.name, book.getRealAuthor(), false, null)
                },
                modifier = Modifier
                    .size(width = 90.dp, height = 126.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = book.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
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
            }
        }
    }
}
