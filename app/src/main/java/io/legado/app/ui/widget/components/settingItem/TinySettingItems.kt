package io.legado.app.ui.widget.components.settingItem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.legado.app.R
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.widget.components.AccentColorButton
import io.legado.app.ui.widget.components.AppSlider
import io.legado.app.ui.widget.components.TinySwitch
import io.legado.app.ui.widget.components.ValueStepper
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.text.AppText

/**
 * 滑块拖拽状态：宿主（如阅读菜单）可在任意滑块拖动时据此降低自身透明度。
 */
@Stable
class SliderDragState {
    var isDragging by mutableStateOf(false)
        private set

    fun startDragging() {
        isDragging = true
    }

    fun stopDragging() {
        isDragging = false
    }
}

/**
 * 提供当前 [SliderDragState] 的 CompositionLocal；宿主不提供时为 null（不参与透明度联动）。
 */
val LocalSliderDragState = staticCompositionLocalOf<SliderDragState?> { null }

@Composable
fun TinySettingItem(
    title: String,
    description: String? = null,
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color? = MaterialTheme.colorScheme.surfaceContainerLow,
    trailingContent: (@Composable () -> Unit)? = null,
    expanded: Boolean = false,
    onExpandChange: ((Boolean) -> Unit)? = null,
    expandContent: (@Composable ColumnScope.() -> Unit)? = null,
    enabled: Boolean = true,
    semanticRole: Role? = null,
    semanticStateDescription: String? = null,
    semanticToggleState: Boolean? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val isExpandable = expandContent != null && onExpandChange != null
    val alpha = if (enabled) 1f else 0.5f

    NormalCard(
        onClick = if (enabled) {
            {
                when {
                    isExpandable -> onExpandChange.invoke(!expanded)
                    else -> onClick?.invoke()
                }
            }
        } else null,
        onLongClick = if (enabled) onLongClick else null,
        modifier = modifier
            .padding(bottom = 4.dp)
            .heightIn(min = 56.dp)
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                semanticRole?.let { role = it }
                semanticStateDescription?.let { stateDescription = it }
                semanticToggleState?.let {
                    toggleableState = if (it) ToggleableState.On else ToggleableState.Off
                }
                if (!enabled) disabled()
            },
        cornerRadius = 12.dp,
        containerColor = color?.copy(alpha = alpha),
        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                imageVector?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        modifier = Modifier.size(18.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    AppText(
                        text = title,
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    )
                    description?.let {
                        AppText(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(contentAlignment = Alignment.Center) {
                    when {
                        trailingContent != null -> trailingContent()
                        isExpandable -> {
                            val rotation by animateFloatAsState(
                                targetValue = if (expanded) 180f else 0f,
                                label = "tinySettingArrow",
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(rotation),
                            )
                        }
                    }
                }
            }

            if (isExpandable) {
                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    ) {
                        expandContent.invoke(this)
                    }
                }
            }
        }
    }
}

@Composable
fun TinyDropdownSettingItem(
    title: String,
    selectedValue: String,
    displayEntries: Array<String>,
    entryValues: Array<String>,
    description: String? = null,
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color? = MaterialTheme.colorScheme.surfaceContainerLow,
    onValueChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val currentEntry = displayEntries.getOrNull(entryValues.indexOf(selectedValue)) ?: selectedValue

    TinySettingItem(
        title = title,
        description = description,
        imageVector = imageVector,
        modifier = modifier,
        color = color,
        trailingContent = {
            // 与文本阅读器 MoreConfigSheet 的 ValueChip 一致：主色胶囊，背景清晰可见
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = currentEntry,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 13.sp,
                )
            }
        },
        onClick = {
            // 与文本阅读器一致：selector 对话框选值（DropdownMenu 在滚动/弹层容器内定位不稳）
            context.selector(items = displayEntries.toList()) { _, index ->
                onValueChange(entryValues[index])
            }
        },
    )
}

@Composable
fun TinySliderSettingItem(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    description: String? = null,
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color? = MaterialTheme.colorScheme.surfaceContainerLow,
    enabled: Boolean = true,
    stepSize: Float = 1f,
    showDecimal: Boolean = false,
    valueFormat: ((Float) -> String)? = null,
    onValueChange: (Float) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    var displayValue by remember(value) { mutableFloatStateOf(value) }
    val dragState = LocalSliderDragState.current

    TinySettingItem(
        title = title,
        description = description,
        imageVector = imageVector,
        modifier = modifier,
        color = color,
        expanded = expanded,
        onExpandChange = { expanded = it },
        enabled = enabled,
        trailingContent = {
            ValueStepper(
                value = value,
                displayValue = displayValue,
                valueRange = valueRange,
                onValueChange = onValueChange,
                enabled = enabled,
                stepSize = stepSize,
                showDecimal = showDecimal,
                valueFormat = valueFormat,
            )
        },
        expandContent = {
            AppSlider(
                value = sliderValue,
                onValueChange = {
                    sliderValue = it
                    displayValue = it
                    onValueChange(it)
                    dragState?.startDragging()
                },
                onValueChangeFinished = {
                    dragState?.stopDragging()
                },
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                accessibilityLabel = title,
                accessibilityValue = description ?: displayValue.toString(),
            )
        },
    )

    LaunchedEffect(value) {
        if (!expanded) {
            sliderValue = value
            displayValue = value
        }
    }
}

@Composable
fun TinySwitchSettingItem(
    title: String,
    checked: Boolean,
    description: String? = null,
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color? = MaterialTheme.colorScheme.surfaceContainerLow,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    TinySettingItem(
        title = title,
        description = description,
        imageVector = imageVector,
        modifier = modifier,
        color = color,
        enabled = enabled,
        semanticRole = Role.Switch,
        semanticToggleState = checked,
        trailingContent = {
            TinySwitch(
                modifier = Modifier.clearAndSetSemantics { },
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                includeStateSemantics = false,
            )
        },
        onClick = { onCheckedChange(!checked) },
    )
}

@Composable
fun TinyClickableSettingItem(
    title: String,
    description: String? = null,
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color? = MaterialTheme.colorScheme.surfaceContainerLow,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    TinySettingItem(
        title = title,
        description = description,
        imageVector = imageVector,
        modifier = modifier,
        color = color,
        trailingContent = trailingContent ?: {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

@Composable
fun TinyColorSettingItem(
    title: String,
    colorValue: Int,
    description: String? = null,
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color? = MaterialTheme.colorScheme.surfaceContainerLow,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TinySettingItem(
        title = title,
        description = description,
        imageVector = imageVector,
        modifier = modifier,
        color = color,
        enabled = enabled,
        trailingContent = {
            AccentColorButton(
                color = colorValue,
                onClick = onClick,
                enabled = enabled,
            )
        },
        onClick = { onClick() },
    )
}

/**
 * A color setting item with an integrated light/dark mode pill toggle.
 *
 * @param title The title text.
 * @param dayColor The color value for light mode (ARGB int).
 * @param nightColor The color value for dark mode (ARGB int).
 * @param onClickColor Called when the color knob is clicked (passes current mode's selection).
 */
@Composable
fun TinyColorModeSettingItem(
    title: String,
    dayColor: Int,
    nightColor: Int,
    onClickColor: (isNight: Boolean) -> Unit,
    description: String? = null,
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color? = MaterialTheme.colorScheme.surfaceContainerLow,
    enabled: Boolean = true,
) {
    val currentDarkMode = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var isNightMode by remember(currentDarkMode) { mutableStateOf(currentDarkMode) }

    TinySettingItem(
        title = title,
        description = description,
        imageVector = imageVector,
        modifier = modifier,
        color = color,
        enabled = enabled,
        trailingContent = {
            ColorModePill(
                dayColor = dayColor,
                nightColor = nightColor,
                isNightMode = isNightMode,
                onToggleMode = { isNightMode = !isNightMode },
                onClickColor = { onClickColor(isNightMode) },
                enabled = enabled,
            )
        },
    )
}

@Composable
private fun ColorModePill(
    dayColor: Int,
    nightColor: Int,
    isNightMode: Boolean,
    onToggleMode: () -> Unit,
    onClickColor: () -> Unit,
    enabled: Boolean = true,
) {
    val pillWidth = 60.dp
    val pillHeight = 32.dp
    val knobSize = 24.dp
    val padding = 4.dp

    val knobOffset by animateDpAsState(
        targetValue = if (isNightMode) pillWidth - knobSize - padding else padding,
        animationSpec = tween(durationMillis = 200),
        label = "knobOffset",
    )

    val currentColor = if (isNightMode) nightColor else dayColor

    Box(
        modifier = Modifier
            .width(pillWidth)
            .height(pillHeight)
            .clip(RoundedCornerShape(pillHeight))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                enabled = enabled,
                onClick = onToggleMode,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Icons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.DarkMode,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (!isNightMode) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Icon(
                imageVector = Icons.Default.LightMode,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isNightMode) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        // Knob — color or + icon
        Box(
            modifier = Modifier
                .offset { IntOffset(x = knobOffset.roundToPx(), y = 0) }
                .size(knobSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .then(
                    if (currentColor != 0) Modifier.background(Color(currentColor))
                    else Modifier
                )
                .clickable(
                    enabled = enabled,
                    onClick = onClickColor,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (currentColor == 0) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A color mode setting item with a reset icon to the left of the pill.
 * Shows a reset button when the current mode has a custom color set.
 */
@Composable
fun TinyClearColorModeSettingItem(
    title: String,
    dayColor: Int,
    nightColor: Int,
    onClickColor: (isNight: Boolean) -> Unit,
    onClearColor: (isNight: Boolean) -> Unit,
    description: String? = null,
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color? = MaterialTheme.colorScheme.surfaceContainerLow,
    enabled: Boolean = true,
) {
    val currentDarkMode = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var isNightMode by remember(currentDarkMode) { mutableStateOf(currentDarkMode) }

    TinySettingItem(
        title = title,
        description = description,
        imageVector = imageVector,
        modifier = modifier,
        color = color,
        enabled = enabled,
        trailingContent = {
            ClearColorModePill(
                dayColor = dayColor,
                nightColor = nightColor,
                isNightMode = isNightMode,
                enabled = enabled,
                onToggleMode = { isNightMode = !isNightMode },
                onClickColor = { onClickColor(isNightMode) },
                onReset = { onClearColor(isNightMode) },
            )
        },
    )
}

@Composable
private fun ClearColorModePill(
    dayColor: Int,
    nightColor: Int,
    isNightMode: Boolean,
    enabled: Boolean,
    onToggleMode: () -> Unit,
    onClickColor: () -> Unit,
    onReset: () -> Unit,
) {
    val currentColor = if (isNightMode) nightColor else dayColor
    val hasCustomColor = currentColor != 0
    val knobSize = 32.dp

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasCustomColor) {
            Box(
                modifier = Modifier
                    .size(knobSize)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, onClick = onReset)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ColorModePill(
            dayColor = dayColor,
            nightColor = nightColor,
            isNightMode = isNightMode,
            onToggleMode = onToggleMode,
            onClickColor = onClickColor,
            enabled = enabled,
        )
    }
}

/**
 * A background image mode setting item with separate day/night cards.
 * Each card shows the selected image and a reset button when an image is set.
 */
@Composable
fun TinyBgImageModeSettingItem(
    title: String,
    dayBgImage: String?,
    nightBgImage: String?,
    onClickImage: (isNight: Boolean) -> Unit,
    onClearImage: (isNight: Boolean) -> Unit,
    description: String? = null,
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color? = MaterialTheme.colorScheme.surfaceContainerLow,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    TinySettingItem(
        title = title,
        description = description,
        imageVector = imageVector,
        modifier = modifier,
        color = color,
        enabled = enabled,
        expanded = expanded,
        onExpandChange = { expanded = it },
        expandContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                BgImageCard(
                    label = stringResource(R.string.day),
                    bgImage = dayBgImage,
                    enabled = enabled,
                    onClick = { onClickImage(false) },
                    onReset = { onClearImage(false) },
                    modifier = Modifier.weight(1f),
                )
                BgImageCard(
                    label = stringResource(R.string.night),
                    bgImage = nightBgImage,
                    enabled = enabled,
                    onClick = { onClickImage(true) },
                    onReset = { onClearImage(true) },
                    modifier = Modifier.weight(1f),
                )
            }
        },
    )
}

@Composable
private fun BgImageCard(
    label: String,
    bgImage: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasImage = !bgImage.isNullOrBlank()

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        if (hasImage) {
            AsyncImage(
                model = bgImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.6f,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val labelStyle = if (hasImage) {
                MaterialTheme.typography.labelSmall.copy(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.6f),
                        offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                        blurRadius = 3f,
                    )
                )
            } else {
                MaterialTheme.typography.labelSmall
            }
            AppText(
                text = label,
                style = labelStyle,
                color = if (hasImage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (hasImage) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                        .clickable(enabled = enabled, onClick = onReset),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
