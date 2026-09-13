package io.legado.app.ui.book.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.manga.config.MangaDoublePageMode
import io.legado.app.ui.book.manga.config.MangaPageScaleType
import io.legado.app.ui.book.manga.config.MangaScrollMode
import io.legado.app.ui.book.manga.config.MangaWidePageMode
import io.legado.app.ui.common.compose.CategorySection
import io.legado.app.ui.common.compose.ModalLegadoBottomSheet
import io.legado.app.ui.common.compose.SimpleColorPickerDialog
import io.legado.app.ui.widget.components.settingItem.TinyColorSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import kotlin.math.roundToInt

private val MangaReaderSettingsCategory.sheetTitleRes: Int
    get() = when (this) {
        MangaReaderSettingsCategory.SETTINGS -> R.string.setting
        MangaReaderSettingsCategory.INTERFACE -> R.string.interface_setting
        MangaReaderSettingsCategory.AUTO_READ -> R.string.manga_reader_auto_read
    }

private enum class MangaColorPickerTarget { BACKGROUND, FILTER }

/**
 * 漫画阅读设置：底部入口各自打开对应 sheet（设置 / 界面 / 自动阅读，长按「自动」进入），
 * 每个 sheet 内部与文本阅读器 MoreConfigSheet 一致——用 [CategorySection] 卡片分段，
 * 滚动查看，不做切页：
 * - 设置：阅读方式 / 缩放 / 交互
 * - 界面：页脚 / 显示 / 滤镜 / 亮度
 * - 自动阅读：启用与速度
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MangaSettingsSheet(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    val show = state.settingsCategory != null
    // 记住最近一次非空分类：收起期间 settingsCategory 会被置 null，
    // 若直接回退到 SETTINGS 会在收起前闪成「设置」内容（返回闪变 bug）
    var current by remember {
        mutableStateOf(state.settingsCategory ?: MangaReaderSettingsCategory.SETTINGS)
    }
    LaunchedEffect(state.settingsCategory) {
        state.settingsCategory?.let { current = it }
    }
    var colorPickerTarget by remember { mutableStateOf<MangaColorPickerTarget?>(null) }

    ModalLegadoBottomSheet(
        show = show,
        onDismissRequest = { onIntent(MangaReaderIntent.CloseSettings) },
        title = stringResource(current.sheetTitleRes),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (current) {
                MangaReaderSettingsCategory.SETTINGS -> {
                    CategorySection(stringResource(R.string.read_type)) {
                        ReaderBasicSettingsContent(state.settings, onIntent)
                    }
                    CategorySection(stringResource(R.string.manga_reader_zoom)) {
                        ReaderZoomSettingsContent(state.settings, onIntent)
                    }
                    CategorySection(stringResource(R.string.interaction)) {
                        ReaderInteractionSettingsContent(state.settings, onIntent)
                    }
                }

                MangaReaderSettingsCategory.INTERFACE -> {
                    CategorySection(stringResource(R.string.manga_reader_footer)) {
                        FooterSettingsContent(state.settings, onIntent)
                    }
                    CategorySection(stringResource(R.string.show)) {
                        PageDisplaySettingsContent(state.settings, onIntent)
                    }
                    CategorySection(stringResource(R.string.manga_reader_filter_short)) {
                        ColorFilterSettingsContent(
                            settings = state.settings,
                            onPickBackground = {
                                colorPickerTarget = MangaColorPickerTarget.BACKGROUND
                            },
                            onPickFilter = { colorPickerTarget = MangaColorPickerTarget.FILTER },
                            onIntent = onIntent,
                        )
                    }
                    CategorySection(stringResource(R.string.brightness)) {
                        BrightnessSettingsContent(state.settings, onIntent)
                    }
                }

                MangaReaderSettingsCategory.AUTO_READ -> {
                    CategorySection(stringResource(R.string.manga_reader_auto_read)) {
                        AutoReadSettingsContent(state, onIntent)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    colorPickerTarget?.let { target ->
        SimpleColorPickerDialog(
            title = when (target) {
                MangaColorPickerTarget.BACKGROUND -> stringResource(R.string.background_color)
                MangaColorPickerTarget.FILTER -> stringResource(R.string.manga_color_filter)
            },
            currentColor = when (target) {
                MangaColorPickerTarget.BACKGROUND -> state.settings.backgroundColor
                MangaColorPickerTarget.FILTER -> Color(
                    red = 255 - state.settings.filterRed,
                    green = 255 - state.settings.filterGreen,
                    blue = 255 - state.settings.filterBlue,
                )
            },
            onDismiss = { colorPickerTarget = null },
            onConfirm = { picked ->
                val red = (picked.red * 255).roundToInt()
                val green = (picked.green * 255).roundToInt()
                val blue = (picked.blue * 255).roundToInt()
                when (target) {
                    MangaColorPickerTarget.BACKGROUND -> {
                        onIntent(
                            MangaReaderIntent.UpdateSetting(
                                MangaReaderSettingKey.BACKGROUND_RED,
                                red
                            )
                        )
                        onIntent(
                            MangaReaderIntent.UpdateSetting(
                                MangaReaderSettingKey.BACKGROUND_GREEN,
                                green
                            )
                        )
                        onIntent(
                            MangaReaderIntent.UpdateSetting(
                                MangaReaderSettingKey.BACKGROUND_BLUE,
                                blue
                            )
                        )
                    }

                    MangaColorPickerTarget.FILTER -> {
                        // 存储的是「移除量」：所选颜色即要保留的通道，取反映射
                        onIntent(
                            MangaReaderIntent.UpdateSetting(
                                MangaReaderSettingKey.FILTER_RED,
                                255 - red
                            )
                        )
                        onIntent(
                            MangaReaderIntent.UpdateSetting(
                                MangaReaderSettingKey.FILTER_GREEN,
                                255 - green
                            )
                        )
                        onIntent(
                            MangaReaderIntent.UpdateSetting(
                                MangaReaderSettingKey.FILTER_BLUE,
                                255 - blue
                            )
                        )
                    }
                }
                colorPickerTarget = null
            },
        )
    }
}

/** 设置 → 阅读方式：滚动方式 / 预加载页数 */
@Composable
private fun ReaderBasicSettingsContent(
    settings: MangaReaderSettings,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    TinyDropdownSettingItem(
        title = stringResource(R.string.read_type),
        selectedValue = settings.scrollMode.toString(),
        displayEntries = arrayOf(
            stringResource(R.string.webtoon),
            stringResource(R.string.manga_reader_webtoon_gap),
            stringResource(R.string.manga_reader_left_to_right),
            stringResource(R.string.manga_reader_right_to_left),
            stringResource(R.string.manga_reader_top_to_bottom),
        ),
        entryValues = arrayOf(
            MangaScrollMode.WEBTOON,
            MangaScrollMode.WEBTOON_WITH_GAP,
            MangaScrollMode.PAGE_LEFT_TO_RIGHT,
            MangaScrollMode.PAGE_RIGHT_TO_LEFT,
            MangaScrollMode.PAGE_TOP_TO_BOTTOM,
        ).map(Int::toString).toTypedArray(),
        onValueChange = {
            onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.SCROLL_MODE, it.toInt()))
        },
    )
    SettingSlider(
        stringResource(R.string.manga_reader_preload_pages),
        settings.preDownloadCount,
        0..30
    ) {
        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.PRE_DOWNLOAD, it))
    }
    // 自动离线缓存（MD3 46f25b873）：开启后才显示「自动缓存章节数」滑条
    SettingSwitch(
        stringResource(R.string.manga_reader_auto_offline_cache),
        settings.autoOfflineCache
    ) {
        onIntent(
            MangaReaderIntent.UpdateSetting(
                MangaReaderSettingKey.AUTO_OFFLINE_CACHE,
                it.intValue
            )
        )
    }
    if (settings.autoOfflineCache) {
        SettingSlider(
            stringResource(R.string.manga_reader_auto_cache_following_chapters),
            settings.chapterPrefetchCount,
            0..3
        ) {
            onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.CHAPTER_PREFETCH, it))
        }
    }
}

/** 设置 → 缩放：缩放模式 / 缩放起点 / 捏合缩放 / 双击缩放 */
@Composable
private fun ReaderZoomSettingsContent(
    settings: MangaReaderSettings,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    TinyDropdownSettingItem(
        title = stringResource(R.string.manga_reader_scale_type),
        selectedValue = settings.pageScaleType.toString(),
        displayEntries = arrayOf(
            stringResource(R.string.manga_reader_scale_fit_screen),
            stringResource(R.string.manga_reader_scale_stretch),
            stringResource(R.string.manga_reader_scale_fit_width),
            stringResource(R.string.manga_reader_scale_fit_height),
            stringResource(R.string.manga_reader_scale_original),
            stringResource(R.string.manga_reader_scale_smart),
        ),
        entryValues = arrayOf(
            MangaPageScaleType.FIT_SCREEN,
            MangaPageScaleType.STRETCH,
            MangaPageScaleType.FIT_WIDTH,
            MangaPageScaleType.FIT_HEIGHT,
            MangaPageScaleType.ORIGINAL,
            MangaPageScaleType.SMART_FIT,
        ).map(Int::toString).toTypedArray(),
        onValueChange = {
            onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.PAGE_SCALE_TYPE, it.toInt()))
        },
    )
    SettingSwitch(stringResource(R.string.manga_reader_pinch_zoom), !settings.disableScale) {
        onIntent(
            MangaReaderIntent.UpdateSetting(
                MangaReaderSettingKey.DISABLE_SCALE,
                (!it).intValue
            )
        )
    }
    SettingSwitch(
        stringResource(R.string.manga_reader_double_tap_zoom),
        !settings.disableDoubleTapZoom
    ) {
        onIntent(
            MangaReaderIntent.UpdateSetting(
                MangaReaderSettingKey.DISABLE_DOUBLE_TAP_ZOOM,
                (!it).intValue
            )
        )
    }
}

/** 设置 → 交互：点击翻页 / 滚动动画 / 淡入淡出 / 长按保存 / 音量键 / 隐藏边缘提示 */
@Composable
private fun ReaderInteractionSettingsContent(
    settings: MangaReaderSettings,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    SettingSwitch(stringResource(R.string.manga_reader_tap_turn), !settings.disableClickScroll) {
        onIntent(
            MangaReaderIntent.UpdateSetting(
                MangaReaderSettingKey.DISABLE_CLICK_SCROLL,
                (!it).intValue
            )
        )
    }
    SettingSwitch(
        stringResource(R.string.manga_reader_scroll_animation),
        !settings.disableScrollAnimation
    ) {
        onIntent(
            MangaReaderIntent.UpdateSetting(
                MangaReaderSettingKey.DISABLE_SCROLL_ANIMATION,
                (!it).intValue
            )
        )
    }
    SettingSwitch(stringResource(R.string.manga_reader_image_fade), !settings.disableCrossFade) {
        onIntent(
            MangaReaderIntent.UpdateSetting(
                MangaReaderSettingKey.DISABLE_CROSS_FADE,
                (!it).intValue
            )
        )
    }
    SettingSwitch(
        stringResource(R.string.manga_reader_long_press_save),
        settings.longPressEnabled
    ) {
        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.LONG_PRESS, it.intValue))
    }
    SettingSwitch(stringResource(R.string.manga_reader_volume_page), settings.volumeKeyPage) {
        onIntent(
            MangaReaderIntent.UpdateSetting(
                MangaReaderSettingKey.VOLUME_KEY_PAGE,
                it.intValue
            )
        )
    }
    SettingSwitch(
        stringResource(R.string.manga_reader_reverse_volume),
        settings.reverseVolumeKeyPage
    ) {
        onIntent(
            MangaReaderIntent.UpdateSetting(
                MangaReaderSettingKey.REVERSE_VOLUME_KEY_PAGE,
                it.intValue
            )
        )
    }
    SettingSwitch(stringResource(R.string.manga_reader_hide_edge_prompt), settings.hideMangaTitle) {
        onIntent(
            MangaReaderIntent.UpdateSetting(
                MangaReaderSettingKey.HIDE_MANGA_TITLE,
                it.intValue
            )
        )
    }
}

/** 界面 → 页脚：隐藏项 + 对齐方式 */
@Composable
private fun FooterSettingsContent(
    settings: MangaReaderSettings,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    SettingSwitch(
        stringResource(R.string.manga_reader_hide_footer),
        settings.hideFooter
    ) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_FOOTER, it) }
    SettingSwitch(
        stringResource(R.string.manga_reader_hide_chapter_name),
        settings.hideChapterName
    ) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_CHAPTER_NAME, it) }
    SettingSwitch(
        stringResource(R.string.manga_reader_hide_page_number),
        settings.hidePageNumber
    ) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_PAGE_NUMBER, it) }
    SettingSwitch(
        stringResource(R.string.manga_reader_hide_page_label),
        settings.hidePageNumberLabel
    ) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_PAGE_NUMBER_LABEL, it) }
    SettingSwitch(
        stringResource(R.string.manga_reader_hide_chapter_progress),
        settings.hideChapter
    ) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_CHAPTER, it) }
    SettingSwitch(
        stringResource(R.string.manga_reader_hide_chapter_label),
        settings.hideChapterLabel
    ) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_CHAPTER_LABEL, it) }
    SettingSwitch(
        stringResource(R.string.manga_reader_hide_total_progress),
        settings.hideProgress
    ) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_PROGRESS, it) }
    SettingSwitch(
        stringResource(R.string.manga_reader_hide_progress_label),
        settings.hideProgressLabel
    ) { updateBoolean(onIntent, MangaReaderSettingKey.HIDE_PROGRESS_LABEL, it) }
    TinyDropdownSettingItem(
        title = stringResource(R.string.manga_reader_footer_alignment),
        selectedValue = settings.footerAlignment.toString(),
        displayEntries = arrayOf(
            stringResource(R.string.manga_reader_left_align),
            stringResource(R.string.manga_reader_center_align),
        ),
        entryValues = arrayOf("0", "1"),
        onValueChange = {
            updateInt(onIntent, MangaReaderSettingKey.FOOTER_ALIGNMENT, it.toInt())
        },
    )
}

/** 界面 → 显示：侧边留白 / 自动背景 / 双页 / 宽页 / 灰度 / EInk */
@Composable
private fun PageDisplaySettingsContent(
    settings: MangaReaderSettings,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    SettingSlider(
        stringResource(R.string.manga_reader_side_padding),
        settings.sidePaddingPercent,
        0..45
    ) {
        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.SIDE_PADDING, it))
    }
    SettingSwitch(stringResource(R.string.manga_reader_auto_background), settings.autoBackground) {
        updateBoolean(onIntent, MangaReaderSettingKey.AUTO_BACKGROUND, it)
    }
    TinyDropdownSettingItem(
        title = stringResource(R.string.manga_reader_double_page),
        selectedValue = settings.doublePageMode.toString(),
        displayEntries = arrayOf(
            stringResource(R.string.manga_reader_mode_off),
            stringResource(R.string.manga_reader_mode_landscape),
            stringResource(R.string.manga_reader_mode_always),
        ),
        entryValues = arrayOf(
            MangaDoublePageMode.OFF,
            MangaDoublePageMode.LANDSCAPE,
            MangaDoublePageMode.ALWAYS,
        ).map(Int::toString).toTypedArray(),
        onValueChange = {
            onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.DOUBLE_PAGE_MODE, it.toInt()))
        },
    )
    TinyDropdownSettingItem(
        title = stringResource(R.string.manga_reader_wide_page),
        selectedValue = settings.widePageMode.toString(),
        displayEntries = arrayOf(
            stringResource(R.string.manga_reader_wide_normal),
            stringResource(R.string.manga_reader_wide_fit_width),
            stringResource(R.string.manga_reader_wide_rotate),
            stringResource(R.string.manga_reader_wide_split),
        ),
        entryValues = arrayOf(
            MangaWidePageMode.NORMAL,
            MangaWidePageMode.FIT_WIDTH,
            MangaWidePageMode.ROTATE_TO_FIT,
            MangaWidePageMode.SPLIT,
        ).map(Int::toString).toTypedArray(),
        onValueChange = {
            onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.WIDE_PAGE_MODE, it.toInt()))
        },
    )
    // 双页封面对齐（MD3 c4cd6afdf）：封面单页 / 顺序反转 / 配对错位
    SettingSwitch(
        stringResource(R.string.manga_reader_double_page_cover_single),
        settings.doublePageCoverSingle
    ) { updateBoolean(onIntent, MangaReaderSettingKey.DOUBLE_PAGE_COVER_SINGLE, it) }
    SettingSwitch(
        stringResource(R.string.manga_reader_double_page_invert),
        settings.doublePageInvert
    ) { updateBoolean(onIntent, MangaReaderSettingKey.DOUBLE_PAGE_INVERT, it) }
    SettingSwitch(
        stringResource(R.string.manga_reader_double_page_shift),
        settings.doublePageShift
    ) { updateBoolean(onIntent, MangaReaderSettingKey.DOUBLE_PAGE_SHIFT, it) }
    SettingSwitch(
        stringResource(R.string.manga_reader_grayscale),
        settings.enableGray
    ) { updateBoolean(onIntent, MangaReaderSettingKey.ENABLE_GRAY, it) }
    SettingSwitch(stringResource(R.string.manga_reader_eink), settings.enableEInk) {
        updateBoolean(
            onIntent,
            MangaReaderSettingKey.ENABLE_EINK,
            it
        )
    }
    SettingSlider(
        stringResource(R.string.manga_reader_eink_threshold),
        settings.eInkThreshold,
        0..255
    ) {
        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.EINK_THRESHOLD, it))
    }
}

/** 界面 → 滤镜：背景色 / 滤镜颜色 / 滤镜透明度 */
@Composable
private fun ColorFilterSettingsContent(
    settings: MangaReaderSettings,
    onPickBackground: () -> Unit,
    onPickFilter: () -> Unit,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    TinyColorSettingItem(
        title = stringResource(R.string.background_color),
        colorValue = settings.backgroundColor.toArgb(),
        onClick = onPickBackground,
    )
    TinyColorSettingItem(
        title = stringResource(R.string.manga_reader_display_filter),
        colorValue = Color(
            red = 255 - settings.filterRed,
            green = 255 - settings.filterGreen,
            blue = 255 - settings.filterBlue,
        ).toArgb(),
        onClick = onPickFilter,
    )
    SettingSlider(
        stringResource(R.string.manga_reader_filter_alpha),
        settings.filterAlpha,
        0..255
    ) { updateInt(onIntent, MangaReaderSettingKey.FILTER_ALPHA, it) }
}

/** 界面 → 亮度：跟随系统亮度 / 屏幕亮度 */
@Composable
private fun BrightnessSettingsContent(
    settings: MangaReaderSettings,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    SettingSwitch(
        stringResource(R.string.manga_reader_system_brightness),
        settings.autoBrightness
    ) { updateBoolean(onIntent, MangaReaderSettingKey.AUTO_BRIGHTNESS, it) }
    if (!settings.autoBrightness) {
        SettingSlider(
            stringResource(R.string.manga_reader_screen_brightness),
            settings.brightness,
            0..255
        ) {
            updateInt(onIntent, MangaReaderSettingKey.BRIGHTNESS, it)
        }
    }
}

/** 自动阅读：启用与速度 */
@Composable
private fun AutoReadSettingsContent(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    SettingSwitch(stringResource(R.string.manga_reader_enable_auto_read), state.autoReadEnabled) {
        onIntent(MangaReaderIntent.ToggleAutoRead)
    }
    SettingSlider(
        stringResource(R.string.manga_reader_auto_speed),
        state.settings.autoReadSpeed,
        1..15
    ) {
        onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.AUTO_READ_SPEED, it))
    }
}

private val Boolean.intValue: Int get() = if (this) 1 else 0

private fun updateBoolean(
    onIntent: (MangaReaderIntent) -> Unit,
    key: MangaReaderSettingKey,
    value: Boolean,
) = onIntent(MangaReaderIntent.UpdateSetting(key, value.intValue))

private fun updateInt(
    onIntent: (MangaReaderIntent) -> Unit,
    key: MangaReaderSettingKey,
    value: Int,
) = onIntent(MangaReaderIntent.UpdateSetting(key, value))

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    TinySwitchSettingItem(
        title = label,
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
private fun SettingSlider(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    TinySliderSettingItem(
        title = label,
        value = value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
        valueRange = range.first.toFloat()..range.last.toFloat(),
        onValueChange = { onValueChange(it.toInt()) },
    )
}
