package io.legado.app.ui.book.readRecord

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.domain.model.AiCallSource
import io.legado.app.help.config.AppConfig

@Composable
fun aiSourceIcon(source: String): ImageVector = when (source) {
    AiCallSource.CHAT -> Icons.AutoMirrored.Filled.Chat
    AiCallSource.TITLE -> Icons.Default.Title
    AiCallSource.COMPRESS -> Icons.Default.Compress
    AiCallSource.SUGGESTION -> Icons.Default.Psychology
    AiCallSource.GALGAME -> Icons.Default.SmartToy
    AiCallSource.POST_EDIT -> Icons.Default.Edit
    AiCallSource.HELP_REPLY -> Icons.Default.AutoAwesome
    AiCallSource.TOOL_SUBMODEL -> Icons.Default.Extension
    AiCallSource.OUTLINE -> Icons.Default.MenuBook
    AiCallSource.MEMORY -> Icons.Default.Storage
    AiCallSource.CHARACTER -> Icons.Default.Person
    AiCallSource.WORLDBOOK -> Icons.Default.MenuBook
    else -> Icons.Default.AutoAwesome
}

@Composable
fun aiSourceTint(source: String): Color {
    val scheme = MaterialTheme.colorScheme
    if (AppConfig.isEInkMode) return scheme.onSurface
    return when (source) {
        AiCallSource.CHAT -> scheme.primary
        AiCallSource.TITLE -> scheme.secondary
        AiCallSource.COMPRESS -> scheme.tertiary
        AiCallSource.SUGGESTION -> scheme.primary
        AiCallSource.GALGAME -> scheme.secondary
        AiCallSource.POST_EDIT -> scheme.tertiary
        AiCallSource.HELP_REPLY -> scheme.primary
        AiCallSource.TOOL_SUBMODEL -> scheme.secondary
        AiCallSource.OUTLINE -> scheme.tertiary
        AiCallSource.MEMORY -> scheme.primary
        AiCallSource.CHARACTER -> scheme.secondary
        AiCallSource.WORLDBOOK -> scheme.tertiary
        else -> scheme.onSurfaceVariant
    }
}

@Composable
fun aiModelTint(index: Int): Color {
    val scheme = MaterialTheme.colorScheme
    if (AppConfig.isEInkMode) return scheme.onSurface
    val colors = listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primaryContainer)
    return colors[index % colors.size]
}

/** 按 token 降序；「其他」固定排在最后，与饼图绘制顺序一致。 */
fun sortSourceSlices(slices: List<AiUsageSourceSliceUi>): List<AiUsageSourceSliceUi> {
    val (others, named) = slices.partition { it.source.isBlank() }
    return named.sortedByDescending { it.tokens } + others.sortedByDescending { it.tokens }
}
