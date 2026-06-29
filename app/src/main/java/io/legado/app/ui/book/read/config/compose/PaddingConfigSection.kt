package io.legado.app.ui.book.read.config.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import io.legado.app.utils.postEvent

@Composable
fun PaddingConfigSection() {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(
                text = stringResource(R.string.padding),
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
                // Body padding
                SectionLabel(stringResource(R.string.main_body))
                PaddingSlider(
                    label = stringResource(R.string.top),
                    value = ReadBookConfig.paddingTop,
                    onValueChange = {
                        ReadBookConfig.paddingTop = it
                        postEvent(EventBus.UP_CONFIG, listOf(10, 5))
                    },
                )
                PaddingSlider(
                    label = stringResource(R.string.bottom),
                    value = ReadBookConfig.paddingBottom,
                    onValueChange = {
                        ReadBookConfig.paddingBottom = it
                        postEvent(EventBus.UP_CONFIG, listOf(10, 5))
                    },
                )
                PaddingSlider(
                    label = stringResource(R.string.left),
                    value = ReadBookConfig.paddingLeft,
                    onValueChange = {
                        ReadBookConfig.paddingLeft = it
                        postEvent(EventBus.UP_CONFIG, listOf(10, 5))
                    },
                )
                PaddingSlider(
                    label = stringResource(R.string.right),
                    value = ReadBookConfig.paddingRight,
                    onValueChange = {
                        ReadBookConfig.paddingRight = it
                        postEvent(EventBus.UP_CONFIG, listOf(10, 5))
                    },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Header padding
                SectionLabel(stringResource(R.string.header))
                PaddingSlider(
                    label = stringResource(R.string.top),
                    value = ReadBookConfig.headerPaddingTop,
                    onValueChange = {
                        ReadBookConfig.headerPaddingTop = it
                        postEvent(EventBus.UP_CONFIG, listOf(2))
                    },
                )
                PaddingSlider(
                    label = stringResource(R.string.bottom),
                    value = ReadBookConfig.headerPaddingBottom,
                    onValueChange = {
                        ReadBookConfig.headerPaddingBottom = it
                        postEvent(EventBus.UP_CONFIG, listOf(2))
                    },
                )
                PaddingSlider(
                    label = stringResource(R.string.left),
                    value = ReadBookConfig.headerPaddingLeft,
                    onValueChange = {
                        ReadBookConfig.headerPaddingLeft = it
                        postEvent(EventBus.UP_CONFIG, listOf(2))
                    },
                )
                PaddingSlider(
                    label = stringResource(R.string.right),
                    value = ReadBookConfig.headerPaddingRight,
                    onValueChange = {
                        ReadBookConfig.headerPaddingRight = it
                        postEvent(EventBus.UP_CONFIG, listOf(2))
                    },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Footer padding
                SectionLabel(stringResource(R.string.footer))
                PaddingSlider(
                    label = stringResource(R.string.top),
                    value = ReadBookConfig.footerPaddingTop,
                    onValueChange = {
                        ReadBookConfig.footerPaddingTop = it
                        postEvent(EventBus.UP_CONFIG, listOf(2))
                    },
                )
                PaddingSlider(
                    label = stringResource(R.string.bottom),
                    value = ReadBookConfig.footerPaddingBottom,
                    onValueChange = {
                        ReadBookConfig.footerPaddingBottom = it
                        postEvent(EventBus.UP_CONFIG, listOf(2))
                    },
                )
                PaddingSlider(
                    label = stringResource(R.string.left),
                    value = ReadBookConfig.footerPaddingLeft,
                    onValueChange = {
                        ReadBookConfig.footerPaddingLeft = it
                        postEvent(EventBus.UP_CONFIG, listOf(2))
                    },
                )
                PaddingSlider(
                    label = stringResource(R.string.right),
                    value = ReadBookConfig.footerPaddingRight,
                    onValueChange = {
                        ReadBookConfig.footerPaddingRight = it
                        postEvent(EventBus.UP_CONFIG, listOf(2))
                    },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Lines toggles
                SwitchRow(
                    label = "Show header line",
                    checked = ReadBookConfig.showHeaderLine,
                    onCheckedChange = {
                        ReadBookConfig.showHeaderLine = it
                        postEvent(EventBus.UP_CONFIG, listOf(2))
                    },
                )
                SwitchRow(
                    label = "Show footer line",
                    checked = ReadBookConfig.showFooterLine,
                    onCheckedChange = {
                        ReadBookConfig.showFooterLine = it
                        postEvent(EventBus.UP_CONFIG, listOf(2))
                    },
                )
            }
        }
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
fun PaddingSlider(label: String, value: Int, onValueChange: (Int) -> Unit, valueRange: ClosedFloatingPointRange<Float> = 0f..100f) {
    var sliderValue by remember(value) { mutableIntStateOf(value) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(48.dp),
        )
        Slider(
            value = sliderValue.toFloat(),
            onValueChange = {
                sliderValue = it.toInt()
                onValueChange(it.toInt())
            },
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$sliderValue",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(32.dp),
        )
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
