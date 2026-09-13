package io.legado.app.ui.book.read.config.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import io.legado.app.ui.common.compose.rememberLegadoBottomSheetState
import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.model.ReadBook
import io.legado.app.ui.common.compose.ModalLegadoBottomSheet
import io.legado.app.ui.common.compose.SectionCard
import io.legado.app.ui.common.compose.settingItem.ClickableSettingItem
import io.legado.app.utils.ChineseUtils
import io.legado.app.utils.hexString
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
) {
    var textSize by remember(show) { mutableIntStateOf(ReadBookConfig.textSize - 5) }
    var letterSpacing by remember(show) { mutableFloatStateOf(ReadBookConfig.letterSpacing) }
    var lineSpacing by remember(show) { mutableIntStateOf(ReadBookConfig.lineSpacingExtra) }
    var paragraphSpacing by remember(show) { mutableIntStateOf(ReadBookConfig.paragraphSpacing) }
    var pageAnim by remember(show) { mutableIntStateOf(ReadBook.pageAnim()) }
    var textBold by remember(show) { mutableIntStateOf(ReadBookConfig.textBold) }
    var shareLayout by remember(show) { mutableStateOf(ReadBookConfig.shareLayout) }

    var showBgTextConfig by remember { mutableStateOf(false) }
    var selectedPreset by remember(show) { mutableIntStateOf(ReadBookConfig.styleSelect) }
    var showPaddingConfig by remember { mutableStateOf(false) }
    var showTipConfig by remember { mutableStateOf(false) }
    var presetVersion by remember { mutableIntStateOf(0) }

    val sheetState = rememberLegadoBottomSheetState(skipPartiallyExpanded = false)

    //LaunchedEffect(showPaddingConfig, showTipConfig) {
    //     if (showPaddingConfig || showTipConfig) {
    //         sheetState.expand()
    //     } else {
    //         sheetState.partialExpand()
    //     }
    // }

    val fontWeightOptions = stringArrayResource(R.array.text_font_weight).toList()

    val pageAnimOptions = listOf(
        stringResource(R.string.page_anim_cover),
        stringResource(R.string.page_anim_slide),
        stringResource(R.string.page_anim_simulation),
        stringResource(R.string.page_anim_scroll),
        stringResource(R.string.page_anim_none),
    )

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
        sheetState = sheetState,
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
                CompactChip(
                    modifier = Modifier.weight(1f),
                    selected = textBold > 0,
                    onClick = {
                        textBold = (textBold + 1) % 3
                        ReadBookConfig.textBold = textBold
                        postEvent(EventBus.UP_CONFIG, arrayListOf(8, 9, 6))
                    },
                    label = {
                        Text(
                            text = fontWeightOptions.getOrElse(textBold.coerceIn(0, 2)) { "Normal" },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
                // Text font
                CompactChip(
                    modifier = Modifier.weight(1f),
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
                CompactChip(
                    modifier = Modifier.weight(1f),
                    selected = indent > 0,
                    onClick = {
                        val newIndent = (indent + 1) % 4
                        ReadBookConfig.paragraphIndent = "　".repeat(newIndent)
                        postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.text_indent),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
                // Chinese converter
                CompactChip(
                    modifier = Modifier.weight(1.3f),
                    selected = chineseMode > 0,
                    onClick = {
                        chineseMode = (chineseMode + 1) % 3
                        AppConfig.chineseConverterType = chineseMode
                        ChineseUtils.unLoad(*TransType.entries.toTypedArray())
                        when (chineseMode) {
                            1 -> ChineseUtils.preLoad(false, TransType.TRADITIONAL_TO_SIMPLE)
                            2 -> ChineseUtils.preLoad(false, TransType.SIMPLE_TO_TRADITIONAL)
                        }
                        postEvent(EventBus.UP_CONFIG, arrayListOf(5))
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
                CompactChip(
                    modifier = Modifier.weight(1f),
                    selected = showPaddingConfig,
                    onClick = {
                        showTipConfig = false
                        showPaddingConfig = !showPaddingConfig
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.padding),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
                // Tip (header/footer)
                CompactChip(
                    modifier = Modifier.weight(1f),
                    selected = showTipConfig,
                    onClick = {
                        showPaddingConfig = false
                        showTipConfig = !showTipConfig
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.information),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }

            // ── Inline Padding Config ──
            if (showPaddingConfig) {
                InLinePaddingConfig()
            }

            // ── Inline Tip Config ──
            if (showTipConfig) {
                InlineTipConfig()
            }

            // ── SeekBars ──
            if (!showPaddingConfig && !showTipConfig) {
            LabeledSlider(
                label = stringResource(R.string.text_size),
                initialValue = textSize,
                valueRange = 0f..45f,
                displayFormat = { "${it + 5}" },
                onApply = {
                    ReadBookConfig.textSize = it + 5
                    postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
                },
                updateKey = selectedPreset,
            )
            LabeledSlider(
                label = stringResource(R.string.text_letter_spacing),
                initialValue = ((letterSpacing * 100).toInt() + 50).coerceIn(0, 100),
                valueRange = 0f..100f,
                displayFormat = { "%.2f".format((it - 50) / 100f) },
                onApply = {
                    ReadBookConfig.letterSpacing = (it - 50) / 100f
                    postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
                },
                updateKey = selectedPreset,
            )
            LabeledSlider(
                label = stringResource(R.string.line_size),
                initialValue = lineSpacing,
                valueRange = 0f..20f,
                displayFormat = { "%.1f".format(it / 10f) },
                onApply = {
                    ReadBookConfig.lineSpacingExtra = it
                    postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
                },
                updateKey = selectedPreset,
            )
            LabeledSlider(
                label = stringResource(R.string.paragraph_size),
                initialValue = paragraphSpacing,
                valueRange = 0f..20f,
                displayFormat = { "%.1f".format(it / 10f) },
                onApply = {
                    ReadBookConfig.paragraphSpacing = it
                    postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
                },
                updateKey = selectedPreset,
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
                    CompactChip(
                        selected = pageAnim == index,
                        onClick = {
                            pageAnim = index
                            ReadBook.book?.setPageAnim(-1)
                            ReadBookConfig.pageAnim = index
                            ReadBook.callBack?.upPageAnim()
                            ReadBook.loadContent(false)
                        },
                        label = {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
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
                        postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
                    },
                )
            }

            // ── Style preset cards ──
            key(presetVersion) {
            StylePresetRow(
                selectedIndex = selectedPreset,
                onSelect = { index ->
                    if (index != selectedPreset) {
                        selectedPreset = index
                        ReadBookConfig.styleSelect = index
                        textSize = ReadBookConfig.textSize - 5
                        letterSpacing = ReadBookConfig.letterSpacing
                        lineSpacing = ReadBookConfig.lineSpacingExtra
                        paragraphSpacing = ReadBookConfig.paragraphSpacing
                        pageAnim = ReadBook.pageAnim()
                        textBold = ReadBookConfig.textBold
                        shareLayout = ReadBookConfig.shareLayout
                        postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
                        if (AppConfig.readBarStyleFollowPage) {
                            postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                        }
                    }
                },
                onLongPress = { index ->
                    selectedPreset = index
                    ReadBookConfig.styleSelect = index
                    textSize = ReadBookConfig.textSize - 5
                    letterSpacing = ReadBookConfig.letterSpacing
                    lineSpacing = ReadBookConfig.lineSpacingExtra
                    paragraphSpacing = ReadBookConfig.paragraphSpacing
                    pageAnim = ReadBook.pageAnim()
                    textBold = ReadBookConfig.textBold
                    shareLayout = ReadBookConfig.shareLayout
                    postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
                    showBgTextConfig = true
                },
                onAdd = {
                    ReadBookConfig.configList.add(ReadBookConfig.Config())
                    val newIndex = ReadBookConfig.configList.lastIndex
                    selectedPreset = newIndex
                    ReadBookConfig.styleSelect = newIndex
                    textSize = ReadBookConfig.textSize - 5
                    letterSpacing = ReadBookConfig.letterSpacing
                    lineSpacing = ReadBookConfig.lineSpacingExtra
                    paragraphSpacing = ReadBookConfig.paragraphSpacing
                    pageAnim = ReadBook.pageAnim()
                    textBold = ReadBookConfig.textBold
                    shareLayout = ReadBookConfig.shareLayout
                    showBgTextConfig = true
                },
            )
            }

            Spacer(modifier = Modifier.padding(12.dp))
            }
        }
    }

    BgTextConfigSheet(
        show = showBgTextConfig,
        onDismiss = {
            presetVersion++
            selectedPreset = ReadBookConfig.styleSelect
            textSize = ReadBookConfig.textSize - 5
            letterSpacing = ReadBookConfig.letterSpacing
            lineSpacing = ReadBookConfig.lineSpacingExtra
            paragraphSpacing = ReadBookConfig.paragraphSpacing
            pageAnim = ReadBook.pageAnim()
            textBold = ReadBookConfig.textBold
            shareLayout = ReadBookConfig.shareLayout
            showBgTextConfig = false
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
        },
        onTextColorClick = onTextColorClick,
        onBgColorClick = onBgColorClick,
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    initialValue: Int,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    displayFormat: (Int) -> String = { it.toString() },
    onApply: (Int) -> Unit,
    updateKey: Any? = null,
) {
    var sliderValue by remember(initialValue, updateKey) { mutableIntStateOf(initialValue) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.width(36.dp),
        )
        Slider(
            value = sliderValue.toFloat(),
            onValueChange = { sliderValue = it.toInt() },
            onValueChangeFinished = { onApply(sliderValue) },
            valueRange = valueRange,
            modifier = Modifier
                .weight(1f)
                .height(20.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = displayFormat(sliderValue),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(32.dp),
        )
    }
}

@Composable
private fun CompactChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = shape,
            )
            .background(
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        label()
    }
}

@Composable
private fun InLinePaddingConfig() {
    var showHeaderLine by remember { mutableStateOf(ReadBookConfig.showHeaderLine) }
    var showFooterLine by remember { mutableStateOf(ReadBookConfig.showFooterLine) }

    Text(
        text = "正文边距",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
    )
    LabeledSlider("上", ReadBookConfig.paddingTop, valueRange = 0f..200f, onApply = {
        ReadBookConfig.paddingTop = it; postEvent(EventBus.UP_CONFIG, arrayListOf(10, 5))
    })
    LabeledSlider("下", ReadBookConfig.paddingBottom, valueRange = 0f..100f, onApply = {
        ReadBookConfig.paddingBottom = it; postEvent(EventBus.UP_CONFIG, arrayListOf(10, 5))
    })
    LabeledSlider("左", ReadBookConfig.paddingLeft, valueRange = 0f..100f, onApply = {
        ReadBookConfig.paddingLeft = it; postEvent(EventBus.UP_CONFIG, arrayListOf(10, 5))
    })
    LabeledSlider("右", ReadBookConfig.paddingRight, valueRange = 0f..100f, onApply = {
        ReadBookConfig.paddingRight = it; postEvent(EventBus.UP_CONFIG, arrayListOf(10, 5))
    })

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "页眉边距",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f),
        )
        Text(text = "显示页眉线", style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.width(6.dp))
        Switch(
            checked = showHeaderLine,
            onCheckedChange = {
                showHeaderLine = it
                ReadBookConfig.showHeaderLine = it
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            },
        )
    }
    LabeledSlider("上", ReadBookConfig.headerPaddingTop, valueRange = 0f..300f, onApply = {
        ReadBookConfig.headerPaddingTop = it; postEvent(EventBus.UP_CONFIG, arrayListOf(2))
    })
    LabeledSlider("下", ReadBookConfig.headerPaddingBottom, valueRange = 0f..300f, onApply = {
        ReadBookConfig.headerPaddingBottom = it; postEvent(EventBus.UP_CONFIG, arrayListOf(2))
    })
    LabeledSlider("左", ReadBookConfig.headerPaddingLeft, valueRange = 0f..300f, onApply = {
        ReadBookConfig.headerPaddingLeft = it; postEvent(EventBus.UP_CONFIG, arrayListOf(2))
    })
    LabeledSlider("右", ReadBookConfig.headerPaddingRight, valueRange = 0f..300f, onApply = {
        ReadBookConfig.headerPaddingRight = it; postEvent(EventBus.UP_CONFIG, arrayListOf(2))
    })

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "页脚边距",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f),
        )
        Text(text = "显示页脚线", style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.width(6.dp))
        Switch(
            checked = showFooterLine,
            onCheckedChange = {
                showFooterLine = it
                ReadBookConfig.showFooterLine = it
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            },
        )
    }
    LabeledSlider("上", ReadBookConfig.footerPaddingTop, valueRange = 0f..300f, onApply = {
        ReadBookConfig.footerPaddingTop = it; postEvent(EventBus.UP_CONFIG, arrayListOf(2))
    })
    LabeledSlider("下", ReadBookConfig.footerPaddingBottom, valueRange = 0f..300f, onApply = {
        ReadBookConfig.footerPaddingBottom = it; postEvent(EventBus.UP_CONFIG, arrayListOf(2))
    })
    LabeledSlider("左", ReadBookConfig.footerPaddingLeft, valueRange = 0f..300f, onApply = {
        ReadBookConfig.footerPaddingLeft = it; postEvent(EventBus.UP_CONFIG, arrayListOf(2))
    })
    LabeledSlider("右", ReadBookConfig.footerPaddingRight, valueRange = 0f..300f, onApply = {
        ReadBookConfig.footerPaddingRight = it; postEvent(EventBus.UP_CONFIG, arrayListOf(2))
    })

    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun InlineTipConfig() {
    val context = LocalContext.current
    val tipNames = remember { ReadTipConfig.tipNames }
    val tipColorNames = remember { ReadTipConfig.tipColorNames }
    val tipDividerColorNames = remember { ReadTipConfig.tipDividerColorNames }
    var titleMode by remember { mutableIntStateOf(ReadBookConfig.titleMode) }
    var headerMode by remember { mutableIntStateOf(ReadTipConfig.headerMode) }
    var tipHeaderLeft by remember { mutableIntStateOf(ReadTipConfig.tipHeaderLeft) }
    var tipHeaderMiddle by remember { mutableIntStateOf(ReadTipConfig.tipHeaderMiddle) }
    var tipHeaderRight by remember { mutableIntStateOf(ReadTipConfig.tipHeaderRight) }
    var footerMode by remember { mutableIntStateOf(ReadTipConfig.footerMode) }
    var tipFooterLeft by remember { mutableIntStateOf(ReadTipConfig.tipFooterLeft) }
    var tipFooterMiddle by remember { mutableIntStateOf(ReadTipConfig.tipFooterMiddle) }
    var tipFooterRight by remember { mutableIntStateOf(ReadTipConfig.tipFooterRight) }
    var tipColor by remember { mutableIntStateOf(ReadTipConfig.tipColor) }
    var tipDividerColor by remember { mutableIntStateOf(ReadTipConfig.tipDividerColor) }

    Text(
        text = "标题",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val titleModes = listOf(
            context.getString(R.string.title_left),
            context.getString(R.string.title_center),
            context.getString(R.string.title_hide),
        )
        titleModes.forEachIndexed { index, name ->
            CompactChip(
                selected = titleMode == index,
                onClick = {
                    titleMode = index
                    ReadBookConfig.titleMode = index
                    postEvent(EventBus.UP_CONFIG, arrayListOf(5))
                },
                label = {
                    Text(text = name, style = MaterialTheme.typography.labelSmall)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
    LabeledSlider("字号", ReadBookConfig.titleSize, valueRange = 0f..10f, onApply = {
        ReadBookConfig.titleSize = it; postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
    })
    LabeledSlider("上边距", ReadBookConfig.titleTopSpacing, valueRange = 0f..100f, onApply = {
        ReadBookConfig.titleTopSpacing = it; postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
    })
    LabeledSlider("下边距", ReadBookConfig.titleBottomSpacing, valueRange = 0f..100f, onApply = {
        ReadBookConfig.titleBottomSpacing = it; postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
    })

    CategorySection("页眉") {
        val headerModes = remember { ReadTipConfig.getHeaderModes(context) }
        val headerModeNames = headerModes.values.toList()
        val headerModeKeys = headerModes.keys.toList()
        ClickableSettingItem(
            title = "显示",
            trailingContent = {
                ValueChip(headerModeNames.getOrElse(headerModeKeys.indexOf(headerMode)) { "" })
            },
            onClick = {
                context.selector(items = headerModeNames) { _, i ->
                    headerMode = headerModeKeys[i]
                    ReadTipConfig.headerMode = headerMode
                    postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                }
            },
        )
        listOf(
            Triple("左", { tipHeaderLeft }, { v: Int -> tipHeaderLeft = v }),
            Triple("中", { tipHeaderMiddle }, { v: Int -> tipHeaderMiddle = v }),
            Triple("右", { tipHeaderRight }, { v: Int -> tipHeaderRight = v }),
        ).forEach { (label, getter, setter) ->
            ClickableSettingItem(
                title = label,
                trailingContent = {
                    ValueChip(tipNames.getOrElse(ReadTipConfig.tipValues.indexOf(getter())) { tipNames.first() })
                },
                onClick = {
                    context.selector(items = tipNames) { _, i ->
                        val v = ReadTipConfig.tipValues[i]
                        clearRepeat(v)
                        setter(v)
                        ReadTipConfig.tipHeaderLeft = tipHeaderLeft
                        ReadTipConfig.tipHeaderMiddle = tipHeaderMiddle
                        ReadTipConfig.tipHeaderRight = tipHeaderRight
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
                    }
                },
            )
        }
    }

    CategorySection("页脚") {
        val footerModes = remember { ReadTipConfig.getFooterModes(context) }
        val footerModeNames = footerModes.values.toList()
        val footerModeKeys = footerModes.keys.toList()
        ClickableSettingItem(
            title = "显示",
            trailingContent = {
                ValueChip(footerModeNames.getOrElse(footerModeKeys.indexOf(footerMode)) { "" })
            },
            onClick = {
                context.selector(items = footerModeNames) { _, i ->
                    footerMode = footerModeKeys[i]
                    ReadTipConfig.footerMode = footerMode
                    postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                }
            },
        )
        listOf(
            Triple("左", { tipFooterLeft }, { v: Int -> tipFooterLeft = v }),
            Triple("中", { tipFooterMiddle }, { v: Int -> tipFooterMiddle = v }),
            Triple("右", { tipFooterRight }, { v: Int -> tipFooterRight = v }),
        ).forEach { (label, getter, setter) ->
            ClickableSettingItem(
                title = label,
                trailingContent = {
                    ValueChip(tipNames.getOrElse(ReadTipConfig.tipValues.indexOf(getter())) { tipNames.first() })
                },
                onClick = {
                    context.selector(items = tipNames) { _, i ->
                        val v = ReadTipConfig.tipValues[i]
                        clearRepeat(v)
                        setter(v)
                        ReadTipConfig.tipFooterLeft = tipFooterLeft
                        ReadTipConfig.tipFooterMiddle = tipFooterMiddle
                        ReadTipConfig.tipFooterRight = tipFooterRight
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
                    }
                },
            )
        }
    }

    CategorySection("颜色") {
        ClickableSettingItem(
            title = "文字颜色",
            trailingContent = {
                val tipColorDisplay = if (tipColor == 0) tipColorNames.first()
                    else "#${tipColor.hexString}"
                ValueChip(tipColorDisplay)
            },
            onClick = {
                context.selector(items = tipColorNames) { _, i ->
                    if (i == 0) {
                        tipColor = 0
                        ReadTipConfig.tipColor = 0
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                    } else {
                        io.legado.app.lib.prefs.ColorPreference.ColorPickerDialogCompat.newBuilder()
                            .setDialogType(com.jaredrummler.android.colorpicker.ColorPickerDialog.TYPE_CUSTOM)
                            .setShowAlphaSlider(false)
                            .setColor(tipColor)
                            .setDialogId(io.legado.app.ui.book.read.config.ReadConfigIds.TIP_COLOR)
                            .create().apply {
                                show(findActivity(context).supportFragmentManager, "tipColorPicker")
                            }
                    }
                }
            },
        )
        ClickableSettingItem(
            title = "分隔线颜色",
            trailingContent = {
                val dividerColorDisplay = when (tipDividerColor) {
                    -1, 0 -> tipDividerColorNames[tipDividerColor + 1]
                    else -> "#${tipDividerColor.hexString}"
                }
                ValueChip(dividerColorDisplay)
            },
            onClick = {
                context.selector(items = tipDividerColorNames) { _, i ->
                    if (i <= 1) {
                        tipDividerColor = i - 1
                        ReadTipConfig.tipDividerColor = tipDividerColor
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                    } else {
                        io.legado.app.lib.prefs.ColorPreference.ColorPickerDialogCompat.newBuilder()
                            .setDialogType(com.jaredrummler.android.colorpicker.ColorPickerDialog.TYPE_CUSTOM)
                            .setShowAlphaSlider(false)
                            .setColor(tipDividerColor)
                            .setDialogId(io.legado.app.ui.book.read.config.ReadConfigIds.TIP_DIVIDER_COLOR)
                            .create().apply {
                                show(findActivity(context).supportFragmentManager, "tipDividerColorPicker")
                            }
                    }
                }
            },
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun CategorySection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        )
        SectionCard { content() }
    }
}

@Composable
private fun ValueChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 13.sp,
        )
    }
}

private fun clearRepeat(repeat: Int) {
    if (repeat != ReadTipConfig.none) {
        if (ReadTipConfig.tipHeaderLeft == repeat) ReadTipConfig.tipHeaderLeft = ReadTipConfig.none
        if (ReadTipConfig.tipHeaderMiddle == repeat) ReadTipConfig.tipHeaderMiddle = ReadTipConfig.none
        if (ReadTipConfig.tipHeaderRight == repeat) ReadTipConfig.tipHeaderRight = ReadTipConfig.none
        if (ReadTipConfig.tipFooterLeft == repeat) ReadTipConfig.tipFooterLeft = ReadTipConfig.none
        if (ReadTipConfig.tipFooterMiddle == repeat) ReadTipConfig.tipFooterMiddle = ReadTipConfig.none
        if (ReadTipConfig.tipFooterRight == repeat) ReadTipConfig.tipFooterRight = ReadTipConfig.none
    }
}

private fun findActivity(context: android.content.Context): androidx.fragment.app.FragmentActivity {
    var ctx = context
    while (ctx !is android.app.Activity && ctx is android.content.ContextWrapper) {
        ctx = ctx.baseContext
    }
    return ctx as androidx.fragment.app.FragmentActivity
}
