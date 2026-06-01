package io.legado.app.ui.book.info.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.common.compose.InfoChip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Chip 信息行：来源标签 + 字数 + 已读进度 + 更新时间。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoChipRow(
    kindList: List<String>,
    wordCount: String?,
    readProgress: Int,
    lastUpdateTime: Long,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 来源/分类标签（如"玄幻""完结"等）
        kindList.forEach { kind ->
            InfoChip(text = kind, filled = true)
        }

        // 字数：格式化显示（如 19600000 → 19.6M）
        val formattedWordCount = formatWordCount(wordCount, kindList)
        if (formattedWordCount != null) {
            InfoChip(text = formattedWordCount)
        }

        // 已读进度：始终显示
        InfoChip(text = "已读 $readProgress%", filled = true)

        // 更新时间
        val timeText = formatRelativeTime(lastUpdateTime)
        InfoChip(text = timeText, filled = true)
    }
}

/** 格式化字数，避免与 kindList 中的字数标签重复 */
private fun formatWordCount(raw: String?, kindList: List<String>): String? {
    if (raw.isNullOrBlank()) return null
    // 如果 kindList 已经包含了字数标签，则不重复显示
    if (kindList.any { it.contains("字") && (it.contains("万") || it.contains("M") || it.contains("K")) }) {
        return null
    }
    val count = raw.toLongOrNull() ?: return null
    if (count <= 0) return null
    return when {
        count >= 100_000_000L -> "${(count / 10_000_000L).toFloat() / 10f}亿字"
        count >= 10_000L -> "${(count / 1_000_000L).toFloat() / 10f}M字"
        count >= 1_000L -> "${(count / 100L).toFloat() / 10f}K字"
        else -> "${count}字"
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    if (timestamp <= 0) return "未更新"
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000

    return when {
        minutes < 1 -> "刚刚更新"
        minutes < 60 -> "${minutes}分钟前"
        hours < 24 -> "${hours}小时前"
        days < 30 -> "${days}天前"
        else -> {
            val sdf = SimpleDateFormat("MM-dd", Locale.getDefault())
            "更新于${sdf.format(Date(timestamp))}"
        }
    }
}
