package io.legado.app.domain.model.settings

/**
 * 精简版 ReadSettings：漫画阅读器闭包字段 + 按设置流收编进度逐域补进的
 * 阅读排版面板字段（MoreConfigSheet，见 settings-ds-migration-plan.md Phase 1）。
 *
 * fork 原文为 108 字段（覆盖整套 MD3 阅读器菜单/模糊/工具栏设置），
 * 移植时按漫画闭包裁剪，后续随收编进度逐域补字段。
 */
data class ReadSettings(
    val readUrlInBrowser: Boolean = false,
    val autoChangeSource: Boolean = false,
    val chineseConverterType: Int = 0,
    val readingAnchorEnabled: Boolean = true,
    val readAloudDetachReminderEnabled: Boolean = false,
    // ── 阅读排版面板（MoreConfigSheet）收编字段，默认值照 SP 现用默认 ──
    val keepLight: Int = 0,
    val paddingDisplayCutouts: Boolean = false,
    val doublePageHorizontal: Int = 0,
    val progressBarBehavior: String = "page",
    val mouseWheelPage: Boolean = true,
    val volumeKeyPage: Boolean = true,
    val volumeKeyPageOnPlay: Boolean = false,
    val keyPageOnLongPress: Boolean = false,
    val textSelectAble: Boolean = true,
    val showBrightnessView: Boolean = true,
    val noAnimScrollPage: Boolean = false,
    val previewImageByClick: Boolean = false,
    val optimizeRender: Boolean = false,
    val disableReturnKey: Boolean = false,
    val expandTextMenu: Boolean = false,
    val showReadTitleAddition: Boolean = true,
    val readBarStyleFollowPage: Boolean = false,
    val hideStatusBar: Boolean = false,
    val hideNavigationBar: Boolean = false,
    val readBodyToLh: Boolean = true,
    val useZhLayout: Boolean = false,
    val textFullJustify: Boolean = true,
    val textBottomJustify: Boolean = true,
)
