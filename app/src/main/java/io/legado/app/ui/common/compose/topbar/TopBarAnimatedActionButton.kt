package io.legado.app.ui.common.compose.topbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.common.compose.button.series.AnimatedActionButtonCore
import io.legado.app.ui.common.compose.button.series.AnimatedIcon

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TopBarAnimatedActionButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconChecked: ImageVector,
    iconUnchecked: ImageVector,
    activeText: String,
    inactiveText: String,
    modifier: Modifier = Modifier,
    contentColor: Color? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val activeContentColor = contentColor ?: colorScheme.primary
    val toggleColors = ToggleButtonDefaults.toggleButtonColors(
        containerColor = Color.Transparent,
        checkedContainerColor = Color.Transparent,
        contentColor = activeContentColor,
        checkedContentColor = activeContentColor,
    )

    AnimatedActionButtonCore(
        checked = checked,
        onCheckedChange = onCheckedChange,
        iconChecked = iconChecked,
        iconUnchecked = iconUnchecked,
        activeText = activeText,
        inactiveText = inactiveText,
        modifier = modifier
            .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp)
            .height(36.dp)
            .widthIn(min = 36.dp),
        iconSize = 20.dp,
        textStyle = MaterialTheme.typography.labelMedium,
        textStartPadding = 8.dp,
        contentColor = contentColor,
        button = { buttonModifier, onToggle, content ->
            ToggleButton(
                modifier = buttonModifier,
                contentPadding = PaddingValues(horizontal = 6.dp),
                checked = checked,
                onCheckedChange = onToggle,
                colors = toggleColors,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    content = content,
                )
            }
        },
        icon = { imageVector, iconModifier, _ ->
            AnimatedIcon(
                modifier = iconModifier,
                imageVector = imageVector,
                contentDescription = null,
                tint = activeContentColor,
            )
        },
        text = { label, textModifier, style, _ ->
            Text(
                text = label,
                style = style,
                color = activeContentColor,
                modifier = textModifier,
                maxLines = 1,
                softWrap = false,
            )
        },
    )
}
