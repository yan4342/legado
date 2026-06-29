package io.legado.app.ui.book.read.config.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.common.compose.ModalLegadoBottomSheet
import io.legado.app.utils.ChineseUtils
import com.github.liuyueyi.quick.transfer.constants.TransType
import io.legado.app.utils.postEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadStyleSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onFontSelect: () -> Unit,
    onTextColorClick: (Int) -> Unit,
    onBgColorClick: (Int) -> Unit,
    onPaddingConfig: () -> Unit,
    onTipConfig: () -> Unit,
) {
    val context = LocalContext.current

    var textSize by remember(show) { mutableIntStateOf(ReadBookConfig.textSize - 5) }
    var letterSpacing by remember(show) { mutableFloatStateOf(ReadBookConfig.letterSpacing) }
    var lineSpacing by remember(show) { mutableIntStateOf(ReadBookConfig.lineSpacingExtra) }
    var paragraphSpacing by remember(show) { mutableIntStateOf(ReadBookConfig.paragraphSpacing) }
    var pageAnim by remember(show) { mutableIntStateOf(ReadBook.pageAnim()) }
    var textBold by remember(show) { mutableIntStateOf(ReadBookConfig.textBold) }
    var shareLayout by remember(show) { mutableStateOf(ReadBookConfig.shareLayout) }

    var showBgTextConfig by remember { mutableStateOf(false) }
    var bgTextEditIndex by remember { mutableIntStateOf(0) }
    var selectedPreset by remember(show) { mutableIntStateOf(ReadBookConfig.styleSelect) }

    val fontWeightOptions = context.resources.getStringArray(R.array.text_font_weight).toList()

    val pageAnimOptions = listOf(
        context.getString(R.string.page_anim_cover),
        context.getString(R.string.page_anim_slide),
        context.getString(R.string.page_anim_simulation),
        context.getString(R.string.page_anim_scroll),
        context.getString(R.string.page_anim_none),
    )

    // ChineseConverter reads state
    var chineseMode by remember(show) {
        mutableIntStateOf(AppConfig.chineseConverterType)
    }

    ModalLegadoBottomSheet(
        show = show,
        onDismissRequest = {
            ReadBookConfig.save()
            onDismiss()
        },
        title = "",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // ── Row 1: chips (font weight | font | indent | converter | padding | tip) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Text font weight converter
                FilterChip(
                    selected = textBold > 0,
                    onClick = {
                        textBold = (textBold + 1) % 3
                        ReadBookConfig.textBold = textBold
                        postEvent(EventBus.UP_CONFIG, listOf(8, 9, 6))
                    },
                    label = {
                        Text(
                            text = fontWeightOptions.getOrElse(textBold.coerceIn(0, 2)) { "Normal" },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
                // Text font
                FilterChip(
                    selected = false,
                    onClick = onFontSelect,
                    label = {
                        Text(
                            text = stringResource(R.string.text_font),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
                // Text indent
                val indent = ReadBookConfig.paragraphIndent.length
                FilterChip(
                    selected = indent > 0,
                    onClick = {
                        val newIndent = (indent + 1) % 4
                        ReadBookConfig.paragraphIndent = "　".repeat(newIndent)
                        postEvent(EventBus.UP_CONFIG, listOf(8, 5))
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.text_indent),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
                // Chinese converter
                FilterChip(
                    selected = chineseMode > 0,
                    onClick = {
                        chineseMode = (chineseMode + 1) % 3
                        AppConfig.chineseConverterType = chineseMode
                        ChineseUtils.unLoad(*TransType.entries.toTypedArray())
                        when (chineseMode) {
                            1 -> ChineseUtils.preLoad(false, TransType.TRADITIONAL_TO_SIMPLE)
                            2 -> ChineseUtils.preLoad(false, TransType.SIMPLE_TO_TRADITIONAL)
                        }
                        postEvent(EventBus.UP_CONFIG, listOf(5))
                    },
                    label = {
                        Text(
                            text = when (chineseMode) {
                                1 -> "繁→简"
                                2 -> "简→繁"
                                else -> "繁简"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
                // Padding
                FilterChip(
                    selected = false,
                    onClick = onPaddingConfig,
                    label = {
                        Text(
                            text = stringResource(R.string.padding),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
                // Tip (header/footer)
                FilterChip(
                    selected = false,
                    onClick = onTipConfig,
                    label = {
                        Text(
                            text = stringResource(R.string.information),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }

            // ── SeekBars ──
            LabeledSlider(
                label = stringResource(R.string.text_size),
                value = textSize,
                valueRange = 0f..40f,
                displayValue = "${textSize + 5}",
                onValueChange = {
                    textSize = it
                    ReadBookConfig.textSize = it + 5
                    postEvent(EventBus.UP_CONFIG, listOf(8, 5))
                },
            )
            LabeledSlider(
                label = stringResource(R.string.text_letter_spacing),
                value = ((letterSpacing * 100).toInt() + 50).coerceIn(0, 100),
                valueRange = 0f..100f,
                displayValue = "%.2f".format(letterSpacing),
                onValueChange = {
                    val valFloat = (it - 50) / 100f
                    letterSpacing = valFloat
                    ReadBookConfig.letterSpacing = valFloat
                    postEvent(EventBus.UP_CONFIG, listOf(8, 5))
                },
            )
            LabeledSlider(
                label = stringResource(R.string.line_size),
                value = lineSpacing,
                valueRange = 0f..50f,
                displayValue = "%.1f".format(lineSpacing / 10f),
                onValueChange = {
                    lineSpacing = it
                    ReadBookConfig.lineSpacingExtra = it
                    postEvent(EventBus.UP_CONFIG, listOf(8, 5))
                },
            )
            LabeledSlider(
                label = stringResource(R.string.paragraph_size),
                value = paragraphSpacing,
                valueRange = 0f..50f,
                displayValue = "%.1f".format(paragraphSpacing / 10f),
                onValueChange = {
                    paragraphSpacing = it
                    ReadBookConfig.paragraphSpacing = it
                    postEvent(EventBus.UP_CONFIG, listOf(8, 5))
                },
            )


            // ── Page animation ──
            Text(
                text = stringResource(R.string.page_anim),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                pageAnimOptions.forEachIndexed { index, name ->
                    val pageAnimValue = index + 1
                    FilterChip(
                        selected = pageAnim == pageAnimValue,
                        onClick = {
                            pageAnim = pageAnimValue
                            ReadBook.book?.setPageAnim(-1)
                            ReadBookConfig.pageAnim = pageAnimValue
                            ReadBook.callBack?.upPageAnim()
                            ReadBook.loadContent(false)
                        },
                        label = {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }


            // ── "Text/Bg style" label + Share layout ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.text_bg_style),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.share_layout),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(end = 4.dp),
                )
                Switch(
                    checked = shareLayout,
                    onCheckedChange = {
                        shareLayout = it
                        ReadBookConfig.shareLayout = it
                        postEvent(EventBus.UP_CONFIG, listOf(1, 2, 5))
                    },
                )
            }

            // ── Style preset cards ──
            StylePresetRow(
                selectedIndex = selectedPreset,
                onSelect = { index ->
                    if (index != selectedPreset) {
                        selectedPreset = index
                        ReadBookConfig.styleSelect = index
                        postEvent(EventBus.UP_CONFIG, listOf(1, 2, 5))
                        if (AppConfig.readBarStyleFollowPage) {
                            postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                        }
                    }
                },
                onLongPress = { index ->
                    selectedPreset = index
                    ReadBookConfig.styleSelect = index
                    postEvent(EventBus.UP_CONFIG, listOf(1, 2, 5))
                    bgTextEditIndex = index
                    showBgTextConfig = true
                },
                onAdd = {
                    ReadBookConfig.configList.add(ReadBookConfig.Config())
                    bgTextEditIndex = ReadBookConfig.configList.lastIndex
                    showBgTextConfig = true
                },
            )

            Spacer(modifier = Modifier.padding(12.dp))
        }
    }

    BgTextConfigSheet(
        show = showBgTextConfig,
        onDismiss = {
            showBgTextConfig = false
            postEvent(EventBus.UP_CONFIG, listOf(1, 2, 5))
        },
        onTextColorClick = onTextColorClick,
        onBgColorClick = onBgColorClick,
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    displayValue: String = "$value",
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.width(48.dp),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange,
            modifier = Modifier
                .weight(1f)
                .height(20.dp),
        )
        Text(
            text = displayValue,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(36.dp),
        )
    }
}
