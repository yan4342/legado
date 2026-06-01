package io.legado.app.ui.common.compose

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.legado.app.lib.theme.accentColor

/**
 * 通用信息 Chip，支持 outlined/filled 两种风格。
 * filled 样式直接使用用户设置的 accentColor，不走 M3 派生。
 */
@Composable
fun InfoChip(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    if (filled) {
        val accent = Color(LocalContext.current.accentColor)
        SuggestionChip(
            onClick = {},
            label = {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                )
            },
            modifier = modifier.height(32.dp),
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = accent,
                labelColor = Color.White,
            ),
            border = null,
            shape = RoundedCornerShape(16.dp),// 圆角
        )
    } else {
        SuggestionChip(
            onClick = {},
            label = {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                )
            },
            modifier = modifier.height(32.dp),
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            border = null,
        )
    }
}
