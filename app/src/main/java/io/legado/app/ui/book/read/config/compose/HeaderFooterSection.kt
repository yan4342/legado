package io.legado.app.ui.book.read.config.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.utils.postEvent

@Composable
fun HeaderFooterSection() {
    var expanded by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(
                text = stringResource(R.string.header_footer),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = if (expanded) " ▾" else " ▸",
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Title section
                SectionLabel(stringResource(R.string.body_title))
                val titleModes = listOf(
                    context.getString(R.string.title_left),
                    context.getString(R.string.title_center),
                    context.getString(R.string.hide),
                )
                OptionSelector(
                    label = "Title mode",
                    options = titleModes,
                    selectedIndex = ReadBookConfig.titleMode.coerceIn(0, 2),
                    onSelect = {
                        ReadBookConfig.titleMode = it
                        postEvent(EventBus.UP_CONFIG, listOf(5))
                    },
                )

                var titleSize by remember { mutableIntStateOf(ReadBookConfig.titleSize) }
                LabeledSlider(
                    label = stringResource(R.string.text_size),
                    value = titleSize,
                    valueRange = 0f..40f,
                    onValueChange = {
                        titleSize = it
                        ReadBookConfig.titleSize = it
                        postEvent(EventBus.UP_CONFIG, listOf(8, 5))
                    },
                )

                var titleTop by remember { mutableIntStateOf(ReadBookConfig.titleTopSpacing) }
                LabeledSlider(
                    label = stringResource(R.string.title_margin_top),
                    value = titleTop,
                    valueRange = 0f..40f,
                    onValueChange = {
                        titleTop = it
                        ReadBookConfig.titleTopSpacing = it
                        postEvent(EventBus.UP_CONFIG, listOf(8, 5))
                    },
                )

                var titleBottom by remember { mutableIntStateOf(ReadBookConfig.titleBottomSpacing) }
                LabeledSlider(
                    label = stringResource(R.string.title_margin_bottom),
                    value = titleBottom,
                    valueRange = 0f..40f,
                    onValueChange = {
                        titleBottom = it
                        ReadBookConfig.titleBottomSpacing = it
                        postEvent(EventBus.UP_CONFIG, listOf(8, 5))
                    },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Header mode
                SectionLabel(stringResource(R.string.header))
                val headerModes = ReadTipConfig.getHeaderModes(context)
                OptionSelector(
                    label = "Display",
                    options = headerModes.values.toList(),
                    selectedIndex = headerModes.keys.indexOf(ReadTipConfig.headerMode).coerceAtLeast(0),
                    onSelect = { idx ->
                        ReadTipConfig.headerMode = headerModes.keys.toList()[idx]
                        postEvent(EventBus.UP_CONFIG, listOf(2))
                    },
                )

                TipContentRow("Left", ReadTipConfig.tipHeaderLeft) { tipValue ->
                    ReadTipConfig.tipHeaderLeft = tipValue
                    postEvent(EventBus.UP_CONFIG, listOf(2, 6))
                }
                TipContentRow("Middle", ReadTipConfig.tipHeaderMiddle) { tipValue ->
                    ReadTipConfig.tipHeaderMiddle = tipValue
                    postEvent(EventBus.UP_CONFIG, listOf(2, 6))
                }
                TipContentRow("Right", ReadTipConfig.tipHeaderRight) { tipValue ->
                    ReadTipConfig.tipHeaderRight = tipValue
                    postEvent(EventBus.UP_CONFIG, listOf(2, 6))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Footer mode
                SectionLabel(stringResource(R.string.footer))
                val footerModes = ReadTipConfig.getFooterModes(context)
                OptionSelector(
                    label = "Display",
                    options = footerModes.values.toList(),
                    selectedIndex = footerModes.keys.indexOf(ReadTipConfig.footerMode).coerceAtLeast(0),
                    onSelect = { idx ->
                        ReadTipConfig.footerMode = footerModes.keys.toList()[idx]
                        postEvent(EventBus.UP_CONFIG, listOf(2))
                    },
                )

                TipContentRow("Left", ReadTipConfig.tipFooterLeft) { tipValue ->
                    ReadTipConfig.tipFooterLeft = tipValue
                    postEvent(EventBus.UP_CONFIG, listOf(2, 6))
                }
                TipContentRow("Middle", ReadTipConfig.tipFooterMiddle) { tipValue ->
                    ReadTipConfig.tipFooterMiddle = tipValue
                    postEvent(EventBus.UP_CONFIG, listOf(2, 6))
                }
                TipContentRow("Right", ReadTipConfig.tipFooterRight) { tipValue ->
                    ReadTipConfig.tipFooterRight = tipValue
                    postEvent(EventBus.UP_CONFIG, listOf(2, 6))
                }
            }
        }
    }
}

@Composable
private fun TipContentRow(label: String, currentValue: Int, onSelect: (Int) -> Unit) {
    val tipNames = remember { ReadTipConfig.tipNames }
    val tipValues = remember { ReadTipConfig.tipValues }
    val noneIdx = remember { tipValues.indexOf(ReadTipConfig.none) }

    var showSelector by remember { mutableStateOf(false) }

    val displayName = remember(currentValue) {
        tipNames.getOrElse(tipValues.indexOf(currentValue)) { tipNames.getOrElse(noneIdx) { "None" } }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showSelector = true }
            .padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = displayName, style = MaterialTheme.typography.bodySmall)
    }

    if (showSelector) {
        AlertDialog(
            onDismissRequest = { showSelector = false },
            title = { Text("Select content") },
            text = {
                Column {
                    tipNames.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val tipValue = tipValues[index]
                                    clearRepeatValue(tipValue)
                                    onSelect(tipValue)
                                    showSelector = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = index == tipValues.indexOf(currentValue).coerceAtLeast(0),
                                onClick = {
                                    val tipValue = tipValues[index]
                                    clearRepeatValue(tipValue)
                                    onSelect(tipValue)
                                    showSelector = false
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = item)
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
fun LabeledSlider(
    label: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(44.dp),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$value",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(28.dp),
        )
    }
}

@Composable
fun OptionSelector(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(44.dp),
        )
        options.forEachIndexed { index, option ->
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                label = { Text(option, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

private fun clearRepeatValue(repeat: Int) {
    if (repeat == ReadTipConfig.none) return
    with(ReadTipConfig) {
        if (tipHeaderLeft == repeat) tipHeaderLeft = ReadTipConfig.none
        if (tipHeaderMiddle == repeat) tipHeaderMiddle = ReadTipConfig.none
        if (tipHeaderRight == repeat) tipHeaderRight = ReadTipConfig.none
        if (tipFooterLeft == repeat) tipFooterLeft = ReadTipConfig.none
        if (tipFooterMiddle == repeat) tipFooterMiddle = ReadTipConfig.none
        if (tipFooterRight == repeat) tipFooterRight = ReadTipConfig.none
    }
}
