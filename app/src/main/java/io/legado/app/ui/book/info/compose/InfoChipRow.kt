package io.legado.app.ui.book.info.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.common.compose.InfoChip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Chip 信息行：字数 + 已读进度 + 更新时间。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoChipRow(
    wordCount: String?,
    readProgress: Int,
    lastUpdateTime: Long,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!wordCount.isNullOrBlank()) {
            InfoChip(text = wordCount)
        }

        InfoChip(
            text = "已读 $readProgress%",
            filled = true,
        )

        if (lastUpdateTime > 0) {
            val timeText = formatRelativeTime(lastUpdateTime)
            InfoChip(text = "更新 $timeText")
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000

    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes}分钟前"
        hours < 24 -> "${hours}小时前"
        days < 30 -> "${days}天前"
        else -> {
            val sdf = SimpleDateFormat("MM-dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
