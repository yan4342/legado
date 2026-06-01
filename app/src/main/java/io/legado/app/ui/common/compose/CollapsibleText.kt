package io.legado.app.ui.common.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * 可折叠文本组件，默认显示 [collapsedMaxLines] 行，点击展开/折叠。
 * 展开按钮独立一行，不与文字重叠。E-Ink 模式下通过 LocalAnimationsEnabled 禁用动画。
 */
@Composable
fun CollapsibleText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 3,
) {
    var expanded by remember { mutableStateOf(false) }
    var isOverflow by remember { mutableStateOf(false) }
    val animationsEnabled = LocalAnimationsEnabled.current

    Column(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (animationsEnabled) Modifier.animateContentSize() else Modifier),
            onTextLayout = { result ->
                if (!expanded) {
                    isOverflow = result.hasVisualOverflow
                }
            },
        )
        if (isOverflow || expanded) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    text = if (expanded) "折叠" else "展开",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
