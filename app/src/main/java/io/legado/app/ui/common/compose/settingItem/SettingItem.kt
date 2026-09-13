package io.legado.app.ui.common.compose.settingItem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.common.compose.RoundDropdownMenu
import io.legado.app.ui.common.compose.legadoCardBackgroundColor

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingItem(
    modifier: Modifier = Modifier,
    painter: Painter? = null,
    imageVector: ImageVector? = null,
    title: String,
    description: String? = null,
    option: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    dropdownMenu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    expanded: Boolean = false,
    onExpandChange: ((Boolean) -> Unit)? = null,
    expandContent: (@Composable ColumnScope.() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.small,
) {
    var showMenu by remember { mutableStateOf(false) }
    val isExpandable = expandContent != null && onExpandChange != null

    // 用 rememberUpdatedState 让闭包始终读到最新参数，modifier 本身只创建一次
    val currentDropdownMenu by rememberUpdatedState(dropdownMenu)
    val currentOnExpandChange by rememberUpdatedState(onExpandChange)
    val currentExpanded by rememberUpdatedState(expanded)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentIsExpandable by rememberUpdatedState(isExpandable)

    val itemModifier = remember {
        Modifier.combinedClickable(
            onClick = {
                when {
                    currentDropdownMenu != null -> showMenu = true
                    currentIsExpandable -> currentOnExpandChange?.invoke(!currentExpanded)
                    else -> currentOnClick?.invoke()
                }
            },
            onLongClick = {
                if (currentDropdownMenu != null) showMenu = true
                currentOnLongClick?.invoke()
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(legadoCardBackgroundColor())
            .then(itemModifier),
    ) {
        ListItem(
            leadingContent = if (painter != null || imageVector != null) {
                {
                    if (painter != null) {
                        Icon(
                            painter = painter,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else if (imageVector != null) {
                        Icon(
                            imageVector = imageVector,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else null,
            supportingContent = if (description != null || option != null) {
                {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        description?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        option?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            } else null,
            trailingContent = {
                Box(contentAlignment = Alignment.Center) {
                    if (isExpandable && trailingContent == null) {
                        val rotation by animateFloatAsState(
                            if (expanded) 180f else 0f,
                            label = "arrow"
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.rotate(rotation)
                        )
                    } else {
                        trailingContent?.invoke()
                    }

                    dropdownMenu?.let { menu ->
                        RoundDropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            menu { showMenu = false }
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (isExpandable) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(expandFrom = Alignment.Top),
                exit = shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 8.dp)
                ) {
                    expandContent.invoke(this)
                }
            }
        }
    }
}

@Composable
private fun animateFloatAsState(
    targetValue: Float,
    label: String,
): androidx.compose.runtime.State<Float> {
    return androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetValue,
        label = label,
    )
}