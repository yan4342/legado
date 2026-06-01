package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.legado.app.lib.theme.accentColor

/**
 * 书架列表未读徽章。
 *
 * @param count       未读章节数，0 时隐藏
 * @param highlight   true = 有新章节（accent 色），false = 灰色
 * @param isUpdating  正在更新时显示加载动画替代数字
 */
@Composable
fun BookBadge(
    count: Int,
    highlight: Boolean,
    isUpdating: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isUpdating) {
        CircularProgressIndicator(
            modifier = modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    if (count <= 0) return

    val bgColor = if (highlight) {
        Color(LocalContext.current.accentColor)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val textColor = if (highlight) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
        )
    }
}
