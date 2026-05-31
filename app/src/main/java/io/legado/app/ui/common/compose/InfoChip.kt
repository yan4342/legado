package io.legado.app.ui.common.compose

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 通用信息 Chip，支持 outlined/filled 两种风格。
 */
@Composable
fun InfoChip(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    if (filled) {
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
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            border = null,
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
