package io.legado.app.ui.book.read.config.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadStyleRefreshBus
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.common.compose.ModalLegadoBottomSheet
import io.legado.app.ui.common.compose.SectionCard
import io.legado.app.ui.common.compose.settingItem.ClickableSettingItem
import io.legado.app.ui.common.compose.settingItem.SwitchSettingItem
import io.legado.app.utils.canvasrecorder.CanvasRecorderFactory
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import io.legado.app.lib.dialogs.selector
import splitties.init.appCtx

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreConfigSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onOrientationChange: () -> Unit,
    onReadBodyToLh: () -> Unit,
    onClickRegionalConfig: () -> Unit,
    onCustomPageKey: () -> Unit,
    onPageTouchSlop: () -> Unit,
    onRecreate: () -> Unit,
    onExpandTextMenuChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current

    val screenOrientation = remember(show) { AppConfig.screenOrientation ?: "0" }

    var hideStatusBar by remember(show) { mutableStateOf(ReadBookConfig.hideStatusBar) }
    var hideNavigationBar by remember(show) { mutableStateOf(ReadBookConfig.hideNavigationBar) }
    var keepLight by remember(show) {
        mutableStateOf(AppConfigStore.getString(PreferKey.keepLight)?.toIntOrNull() ?: 0)
    }
    var readBodyToLh by remember(show) { mutableStateOf(ReadBookConfig.readBodyToLh) }
    var paddingDisplayCutouts by remember(show) {
        mutableStateOf(AppConfigStore.getBoolean(PreferKey.paddingDisplayCutouts) ?: false)
    }
    var doubleHorizontalPage by remember(show) {
        mutableStateOf(AppConfigStore.getString(PreferKey.doublePageHorizontal)?.toIntOrNull() ?: 0)
    }
    var progressBarBehavior by remember(show) {
        mutableStateOf(AppConfigStore.getString(PreferKey.progressBarBehavior) ?: "page")
    }
    var useZhLayout by remember(show) { mutableStateOf(ReadBookConfig.useZhLayout) }
    var textFullJustify by remember(show) { mutableStateOf(ReadBookConfig.textFullJustify) }
    var textBottomJustify by remember(show) { mutableStateOf(ReadBookConfig.textBottomJustify) }
    var mouseWheelPage by remember(show) {
        mutableStateOf(AppConfigStore.getBoolean(PreferKey.mouseWheelPage) ?: true)
    }
    var volumeKeyPage by remember(show) {
        mutableStateOf(AppConfigStore.getBoolean(PreferKey.volumeKeyPage) ?: true)
    }
    var volumeKeyPageOnPlay by remember(show) {
        mutableStateOf(AppConfigStore.getBoolean(PreferKey.volumeKeyPageOnPlay) ?: false)
    }
    var keyPageOnLongPress by remember(show) {
        mutableStateOf(AppConfigStore.getBoolean(PreferKey.keyPageOnLongPress) ?: false)
    }
    var autoChangeSource by remember(show) {
        mutableStateOf(AppConfigStore.getBoolean(PreferKey.autoChangeSource) ?: true)
    }
    var selectText by remember(show) {
        mutableStateOf(AppConfigStore.getBoolean(PreferKey.textSelectAble) ?: true)
    }
    var showBrightnessView by remember(show) {
        mutableStateOf(AppConfigStore.getBoolean(PreferKey.showBrightnessView) ?: true)
    }
    var noAnimScrollPage by remember(show) {
        mutableStateOf(AppConfigStore.getBoolean(PreferKey.noAnimScrollPage) ?: false)
    }
    var readingAnchorEnabled by remember(show) {
        mutableStateOf(ReadBookConfig.readingAnchorEnabled)
    }
    var readAloudDetachReminderEnabled by remember(show) {
        mutableStateOf(ReadBookConfig.readAloudDetachReminderEnabled)
    }
    var previewImageByClick by remember(show) {
        mutableStateOf(AppConfigStore.getBoolean(PreferKey.previewImageByClick) ?: false)
    }
    var optimizeRender by remember(show) {
        mutableStateOf(AppConfigStore.getBoolean(PreferKey.optimizeRender) ?: false)
    }
    var disableReturnKey by remember(show) {
        mutableStateOf(AppConfigStore.getBoolean(PreferKey.disableReturnKey) ?: false)
    }
    var expandTextMenu by remember(show) {
        mutableStateOf(AppConfigStore.getBoolean(PreferKey.expandTextMenu) ?: false)
    }
    var showReadTitleAddition by remember(show) {
        mutableStateOf(AppConfigStore.getBoolean(PreferKey.showReadTitleAddition) ?: true)
    }
    var readBarStyleFollowPage by remember(show) {
        mutableStateOf(AppConfigStore.getBoolean(PreferKey.readBarStyleFollowPage) ?: false)
    }

    val supportsOptimizeRender = remember { CanvasRecorderFactory.isSupport }

    val keepLightOptions = remember {
        context.resources.getStringArray(R.array.screen_time_out).toList()
    }
    val keepLightValues = remember { listOf("0", "1", "5", "10", "-1") }

    ModalLegadoBottomSheet(
        show = show,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.other_setting),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Screen Settings ─────────────────────────────────────────────
            CategorySection(stringResource(R.string.screen_category)) {
                ClickableSettingItem(
                    title = stringResource(R.string.screen_direction),
                    trailingContent = {
                        ValueChip(getOrientationLabel(context, screenOrientation))
                    },
                    onClick = onOrientationChange,
                )
                ClickableSettingItem(
                    title = stringResource(R.string.keep_light),
                    trailingContent = {
                        ValueChip(
                            keepLightOptions.getOrElse(keepLightValues.indexOf(keepLight.toString())) { "Default" }
                        )
                    },
                    onClick = {
                        context.selector(items = keepLightOptions) { _, i ->
                            keepLight = keepLightValues[i].toInt()
                            AppConfigStore.putString(PreferKey.keepLight, keepLight.toString())
                            // SP 镜像：Backup 的 config.xml 来自 SP 全量，Phase 4 前必须保留
                            appCtx.putPrefString(PreferKey.keepLight, keepLight.toString())
                            postEvent(PreferKey.keepLight, true)
                        }
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.pt_hide_status_bar),
                    checked = hideStatusBar,
                    onCheckedChange = {
                        hideStatusBar = it
                        ReadBookConfig.hideStatusBar = it
                        ReadStyleRefreshBus.refresh(0, 2)
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.pt_hide_navigation_bar),
                    checked = hideNavigationBar,
                    onCheckedChange = {
                        hideNavigationBar = it
                        ReadBookConfig.hideNavigationBar = it
                        ReadStyleRefreshBus.refresh(0, 2)
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.read_body_to_lh),
                    checked = readBodyToLh,
                    onCheckedChange = {
                        readBodyToLh = it
                        ReadBookConfig.readBodyToLh = it
                        onReadBodyToLh()
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.padding_display_cutouts),
                    checked = paddingDisplayCutouts,
                    onCheckedChange = {
                        paddingDisplayCutouts = it
                        AppConfigStore.putBoolean(PreferKey.paddingDisplayCutouts, it)
                        appCtx.putPrefBoolean(PreferKey.paddingDisplayCutouts, it)
                        ReadStyleRefreshBus.refresh(2)
                    },
                )
            }

            // ── Page Control ────────────────────────────────────────────────
            CategorySection(stringResource(R.string.page)) {
                ClickableSettingItem(
                    title = stringResource(R.string.double_page_horizontal),
                    trailingContent = {
                        ValueChip(getDoublePageLabel(context, doubleHorizontalPage))
                    },
                    onClick = {
                        val titles = context.resources.getStringArray(R.array.double_page_title).toList()
                        val values = listOf("0", "1", "2", "3")
                        context.selector(items = titles) { _, i ->
                            doubleHorizontalPage = values[i].toInt()
                            AppConfigStore.putString(PreferKey.doublePageHorizontal, doubleHorizontalPage.toString())
                            appCtx.putPrefString(PreferKey.doublePageHorizontal, doubleHorizontalPage.toString())
                            ChapterProvider.upLayout()
                            ReadBook.loadContent(false)
                        }
                    },
                )
                ClickableSettingItem(
                    title = stringResource(R.string.progress_bar_behavior),
                    trailingContent = {
                        ValueChip(
                            if (progressBarBehavior == "page") stringResource(R.string.adjust_chapter_page)
                            else stringResource(R.string.adjust_chapter_index)
                        )
                    },
                    onClick = {
                        val titles = context.resources.getStringArray(R.array.progress_bar_behavior_title).toList()
                        val values = listOf("page", "chapter")
                        context.selector(items = titles) { _, i ->
                            progressBarBehavior = values[i]
                            AppConfigStore.putString(PreferKey.progressBarBehavior, progressBarBehavior)
                            appCtx.putPrefString(PreferKey.progressBarBehavior, progressBarBehavior)
                            postEvent(EventBus.UP_SEEK_BAR, true)
                        }
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.no_anim_scroll_page),
                    checked = noAnimScrollPage,
                    onCheckedChange = {
                        noAnimScrollPage = it
                        AppConfigStore.putBoolean(PreferKey.noAnimScrollPage, it)
                        appCtx.putPrefBoolean(PreferKey.noAnimScrollPage, it)
                        ReadBook.callBack?.upPageAnim()
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.show_brightness_view),
                    checked = showBrightnessView,
                    onCheckedChange = {
                        showBrightnessView = it
                        // DS 沿用 ShowBrightnessViewMigration 规整的 "1"/"0" 字符串格式，
                        // 避免每次冷启动重复触发该迁移
                        AppConfigStore.putString(PreferKey.showBrightnessView, if (it) "1" else "0")
                        appCtx.putPrefBoolean(PreferKey.showBrightnessView, it)
                        postEvent(PreferKey.showBrightnessView, "")
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.reading_anchor),
                    checked = readingAnchorEnabled,
                    onCheckedChange = {
                        readingAnchorEnabled = it
                        ReadBookConfig.readingAnchorEnabled = it
                        if (!it) ReadBook.discardReadingAnchor()
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.read_aloud_detach_reminder),
                    description = stringResource(R.string.read_aloud_detach_reminder_summary),
                    checked = readAloudDetachReminderEnabled,
                    onCheckedChange = {
                        readAloudDetachReminderEnabled = it
                        ReadBookConfig.readAloudDetachReminderEnabled = it
                    },
                )
            }

            // ── Text Layout ─────────────────────────────────────────────────
            CategorySection(stringResource(R.string.text_layout)) {
                SwitchSettingItem(
                    title = stringResource(R.string.use_zh_layout),
                    checked = useZhLayout,
                    onCheckedChange = {
                        useZhLayout = it
                        ReadBookConfig.useZhLayout = it
                        ReadStyleRefreshBus.refresh(5)
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.text_full_justify),
                    checked = textFullJustify,
                    onCheckedChange = {
                        textFullJustify = it
                        AppConfigStore.putBoolean(PreferKey.textFullJustify, it)
                        appCtx.putPrefBoolean(PreferKey.textFullJustify, it)
                        ReadStyleRefreshBus.refresh(5)
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.text_bottom_justify),
                    checked = textBottomJustify,
                    onCheckedChange = {
                        textBottomJustify = it
                        AppConfigStore.putBoolean(PreferKey.textBottomJustify, it)
                        appCtx.putPrefBoolean(PreferKey.textBottomJustify, it)
                        ReadStyleRefreshBus.refresh(5)
                    },
                )
                if (supportsOptimizeRender) {
                    SwitchSettingItem(
                        title = stringResource(R.string.enable_optimize_render),
                        checked = optimizeRender,
                        onCheckedChange = {
                            optimizeRender = it
                            AppConfigStore.putBoolean(PreferKey.optimizeRender, it)
                            appCtx.putPrefBoolean(PreferKey.optimizeRender, it)
                            ChapterProvider.upStyle()
                            ReadBook.callBack?.upPageAnim(true)
                            ReadBook.loadContent(false)
                        },
                    )
                }
            }

            // ── Interaction ─────────────────────────────────────────────────
            CategorySection(stringResource(R.string.interaction)) {
                SwitchSettingItem(
                    title = stringResource(R.string.mouse_wheel_page),
                    checked = mouseWheelPage,
                    onCheckedChange = {
                        mouseWheelPage = it
                        AppConfigStore.putBoolean(PreferKey.mouseWheelPage, it)
                        appCtx.putPrefBoolean(PreferKey.mouseWheelPage, it)
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.volume_key_page),
                    checked = volumeKeyPage,
                    onCheckedChange = {
                        volumeKeyPage = it
                        AppConfigStore.putBoolean(PreferKey.volumeKeyPage, it)
                        appCtx.putPrefBoolean(PreferKey.volumeKeyPage, it)
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.volume_key_page_on_play),
                    checked = volumeKeyPageOnPlay,
                    onCheckedChange = {
                        volumeKeyPageOnPlay = it
                        AppConfigStore.putBoolean(PreferKey.volumeKeyPageOnPlay, it)
                        appCtx.putPrefBoolean(PreferKey.volumeKeyPageOnPlay, it)
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.key_page_on_long_press),
                    checked = keyPageOnLongPress,
                    onCheckedChange = {
                        keyPageOnLongPress = it
                        AppConfigStore.putBoolean(PreferKey.keyPageOnLongPress, it)
                        appCtx.putPrefBoolean(PreferKey.keyPageOnLongPress, it)
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.auto_change_source),
                    checked = autoChangeSource,
                    onCheckedChange = {
                        autoChangeSource = it
                        AppConfigStore.putBoolean(PreferKey.autoChangeSource, it)
                        appCtx.putPrefBoolean(PreferKey.autoChangeSource, it)
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.selectText),
                    checked = selectText,
                    onCheckedChange = {
                        selectText = it
                        AppConfigStore.putBoolean(PreferKey.textSelectAble, it)
                        appCtx.putPrefBoolean(PreferKey.textSelectAble, it)
                        postEvent(PreferKey.textSelectAble, it)
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.preview_image_by_click),
                    checked = previewImageByClick,
                    onCheckedChange = {
                        previewImageByClick = it
                        AppConfigStore.putBoolean(PreferKey.previewImageByClick, it)
                        appCtx.putPrefBoolean(PreferKey.previewImageByClick, it)
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.disable_return_key),
                    checked = disableReturnKey,
                    onCheckedChange = {
                        disableReturnKey = it
                        AppConfigStore.putBoolean(PreferKey.disableReturnKey, it)
                        appCtx.putPrefBoolean(PreferKey.disableReturnKey, it)
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.expand_text_menu),
                    checked = expandTextMenu,
                    onCheckedChange = {
                        expandTextMenu = it
                        AppConfigStore.putBoolean(PreferKey.expandTextMenu, it)
                        appCtx.putPrefBoolean(PreferKey.expandTextMenu, it)
                        onExpandTextMenuChange(it)
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.show_read_title_addition),
                    checked = showReadTitleAddition,
                    onCheckedChange = {
                        showReadTitleAddition = it
                        AppConfigStore.putBoolean(PreferKey.showReadTitleAddition, it)
                        appCtx.putPrefBoolean(PreferKey.showReadTitleAddition, it)
                        postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                    },
                )
                SwitchSettingItem(
                    title = stringResource(R.string.read_bar_style_follow_page),
                    checked = readBarStyleFollowPage,
                    onCheckedChange = {
                        readBarStyleFollowPage = it
                        AppConfigStore.putBoolean(PreferKey.readBarStyleFollowPage, it)
                        appCtx.putPrefBoolean(PreferKey.readBarStyleFollowPage, it)
                        postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                    },
                )
            }

            // ── Other ───────────────────────────────────────────────────────
            CategorySection(stringResource(R.string.other)) {
                ClickableSettingItem(
                    title = stringResource(R.string.click_regional_config),
                    onClick = onClickRegionalConfig,
                )
                ClickableSettingItem(
                    title = stringResource(R.string.custom_page_key),
                    onClick = onCustomPageKey,
                )
                ClickableSettingItem(
                    title = stringResource(R.string.page_touch_slop_title),
                    onClick = onPageTouchSlop,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
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
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        )
        SectionCard { content() }
    }
}

private fun getOrientationLabel(context: android.content.Context, value: String?): String {
    val titles = context.resources.getStringArray(R.array.screen_direction_title)
    val values = listOf("0", "1", "2", "3", "4")
    return titles.getOrElse(values.indexOf(value ?: "0")) { titles[0] }
}

private fun getDoublePageLabel(context: android.content.Context, value: Int): String {
    val titles = context.resources.getStringArray(R.array.double_page_title)
    return titles.getOrElse(value) { titles[0] }
}
