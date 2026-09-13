package io.legado.app.ui.widget.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.card.TextCard

/**
 * 数字加减步进器：加减按钮与数字胶囊对齐——同高（28dp）、同填充
 * （surfaceContainerHigh）、同圆角（8dp），与 TinyDropdownSettingItem 的
 * 值胶囊（如「阅读方式」项）视觉一致。
 */
@Composable
fun ValueStepper(
    value: Float,
    displayValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stepSize: Float = 1f,
    showDecimal: Boolean = false,
    valueFormat: ((Float) -> String)? = null,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ValueStepperButton(
            icon = Icons.Default.Remove,
            description = stringResource(R.string.a11y_decrease),
            enabled = enabled,
            onClick = {
                onValueChange((value - stepSize).coerceIn(valueRange))
            },
        )
        if (content != null) {
            content()
        } else {
            val displayText = valueFormat?.invoke(displayValue) ?: if (showDecimal) {
                displayValue.toString()
            } else {
                displayValue.toInt().toString()
            }
            TextCard(
                cornerRadius = 8.dp,
                horizontalPadding = 8.dp,
                verticalPadding = 4.dp,
                text = displayText,
                backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        }
        ValueStepperButton(
            icon = Icons.Default.Add,
            description = stringResource(R.string.a11y_increase),
            enabled = enabled,
            onClick = {
                onValueChange((value + stepSize).coerceIn(valueRange))
            },
        )
    }
}

/** 与数字胶囊对齐的加减按钮：28dp、8dp 圆角、surfaceContainerHigh 填充。 */
@Composable
private fun ValueStepperButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.5f
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            modifier = Modifier.size(16.dp),
        )
    }
}
