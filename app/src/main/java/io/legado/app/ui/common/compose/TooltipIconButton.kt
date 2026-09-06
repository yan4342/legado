package io.legado.app.ui.common.compose

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 长按显示名称气泡的 IconButton。
 * [label] 同时作为长按 tooltip 与无障碍描述的来源；需要自定义无障碍文案时用 [contentDescription]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = label,
    content: @Composable () -> Unit,
) {
    val tooltipState = rememberTooltipState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = tooltipState,
        modifier = modifier,
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            content()
        }
    }
}
