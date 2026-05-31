package io.legado.app.ui.common.compose

import android.widget.ImageView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.model.BookCover

/**
 * 书籍封面图片组件，5:7 比例，圆角。
 * 使用 AndroidView + Glide 直接加载，确保兼容性。
 */
@Composable
fun BookCoverImage(
    coverUrl: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
        },
        update = { imageView ->
            BookCover.load(context, coverUrl, false, null)
                .placeholder(R.drawable.image_cover_default)
                .error(R.drawable.image_cover_default)
                .into(imageView)
        },
        modifier = modifier
            .aspectRatio(5f / 7f)
            .clip(RoundedCornerShape(8.dp)),
    )
}
