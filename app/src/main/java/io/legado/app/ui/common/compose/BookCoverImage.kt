package io.legado.app.ui.common.compose

import android.view.ViewGroup
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.ui.widget.image.CoverImageView

/**
 * 书籍封面图片组件，5:7 比例，圆角。
 * 使用 CoverImageView（原版自定义 View），支持在默认封面上绘制书名/作者。
 * @param compact 是否紧凑模式（网格用），不强制 aspectRatio，由调用方控制尺寸
 */
@Composable
fun BookCoverImage(
    coverUrl: String?,
    name: String = "",
    author: String = "",
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    compact: Boolean = false,
) {
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
            coverView.load(coverUrl, name, author, false, null)
        },
        modifier = if (compact) {
            modifier.clip(RoundedCornerShape(4.dp))
        } else {
            modifier
                .aspectRatio(5f / 7f)
                .clip(RoundedCornerShape(8.dp))
        },
    )
}
