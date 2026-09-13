package io.legado.app.ui.book.readRecord

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object AiUsageFormatter {

    fun formatTokens(count: Long): String {
        if (count <= 0) return "0"
        val absCount = abs(count)
        return when {
            absCount >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", count / 1_000_000.0)
            absCount >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }

    fun formatChars(count: Long): String {
        if (count <= 0) return "0字"
        val absCount = abs(count)
        return when {
            absCount >= 10_000 -> String.format(Locale.getDefault(), "%.1f万字", count / 10_000.0)
            else -> "${count}字"
        }
    }

    fun formatTime(timestamp: Long): String {
        if (timestamp <= 0) return ""
        return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
