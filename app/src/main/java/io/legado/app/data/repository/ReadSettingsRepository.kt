package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.model.settings.ReadSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * 精简版 ReadSettingsRepository：与 [ReadSettings]（3 字段）对应。
 * 仅覆盖漫画阅读器闭包用到的 readUrlInBrowser / autoChangeSource / chineseConverterType。
 */
class ReadSettingsRepository(
    private val preferencesFlow: StateFlow<Preferences> = AppConfigStore.preferencesFlow,
) : ReadSettingsGateway {

    override val currentSettings: ReadSettings
        get() = preferencesFlow.value.toReadSettings()

    override val settings: Flow<ReadSettings> = preferencesFlow
        .map { preferences ->
            preferences.toReadSettings()
        }

    override suspend fun update(transform: (ReadSettings) -> ReadSettings) {
        AppConfigStore.atomicUpdate(
            read = { it.toReadSettings() },
            toPrefMap = ReadSettings::toGatewayPrefMap,
            transform = transform,
        )
    }

    private object Keys {
        val ReadUrlInBrowser = booleanPreferencesKey(PreferKey.readUrlOpenInBrowser)
        val AutoChangeSource = booleanPreferencesKey(PreferKey.autoChangeSource)
        val ChineseConverterType = intPreferencesKey(PreferKey.chineseConverterType)
        val ReadingAnchorEnabled = booleanPreferencesKey(PreferKey.readingAnchorEnabled)
        val ReadAloudDetachReminderEnabled =
            booleanPreferencesKey(PreferKey.readAloudDetachReminderEnabled)
        val KeepLight = intPreferencesKey(PreferKey.keepLight)
        val PaddingDisplayCutouts = booleanPreferencesKey(PreferKey.paddingDisplayCutouts)
        val DoublePageHorizontal = intPreferencesKey(PreferKey.doublePageHorizontal)
        val ProgressBarBehavior = stringPreferencesKey(PreferKey.progressBarBehavior)
        val MouseWheelPage = booleanPreferencesKey(PreferKey.mouseWheelPage)
        val VolumeKeyPage = booleanPreferencesKey(PreferKey.volumeKeyPage)
        val VolumeKeyPageOnPlay = booleanPreferencesKey(PreferKey.volumeKeyPageOnPlay)
        val KeyPageOnLongPress = booleanPreferencesKey(PreferKey.keyPageOnLongPress)
        val TextSelectAble = booleanPreferencesKey(PreferKey.textSelectAble)
        val ShowBrightnessView = booleanPreferencesKey(PreferKey.showBrightnessView)
        val NoAnimScrollPage = booleanPreferencesKey(PreferKey.noAnimScrollPage)
        val PreviewImageByClick = booleanPreferencesKey(PreferKey.previewImageByClick)
        val OptimizeRender = booleanPreferencesKey(PreferKey.optimizeRender)
        val DisableReturnKey = booleanPreferencesKey(PreferKey.disableReturnKey)
        val ExpandTextMenu = booleanPreferencesKey(PreferKey.expandTextMenu)
        val ShowReadTitleAddition = booleanPreferencesKey(PreferKey.showReadTitleAddition)
        val ReadBarStyleFollowPage = booleanPreferencesKey(PreferKey.readBarStyleFollowPage)
        val HideStatusBar = booleanPreferencesKey(PreferKey.hideStatusBar)
        val HideNavigationBar = booleanPreferencesKey(PreferKey.hideNavigationBar)
        val ReadBodyToLh = booleanPreferencesKey(PreferKey.readBodyToLh)
        val UseZhLayout = booleanPreferencesKey(PreferKey.useZhLayout)
        val TextFullJustify = booleanPreferencesKey(PreferKey.textFullJustify)
        val TextBottomJustify = booleanPreferencesKey(PreferKey.textBottomJustify)
    }
}

internal fun Preferences.toReadSettings(): ReadSettings {
    return ReadSettings(
        readUrlInBrowser = compatDsValue(
            booleanPreferencesKey(PreferKey.readUrlOpenInBrowser), false
        ),
        autoChangeSource = compatDsValue(
            booleanPreferencesKey(PreferKey.autoChangeSource), false
        ),
        chineseConverterType = compatDsValue(
            intPreferencesKey(PreferKey.chineseConverterType), 0
        ),
        readingAnchorEnabled = compatDsValue(
            booleanPreferencesKey(PreferKey.readingAnchorEnabled), true
        ),
        readAloudDetachReminderEnabled = compatDsValue(
            booleanPreferencesKey(PreferKey.readAloudDetachReminderEnabled), false
        ),
        // keepLight/doublePageHorizontal 在 SP/DS 历史数据中以字符串存储，compatDsValue 走
        // compatDsInt 兼容读取
        keepLight = compatDsValue(intPreferencesKey(PreferKey.keepLight), 0),
        paddingDisplayCutouts = compatDsValue(
            booleanPreferencesKey(PreferKey.paddingDisplayCutouts), false
        ),
        doublePageHorizontal = compatDsValue(
            intPreferencesKey(PreferKey.doublePageHorizontal), 0
        ),
        progressBarBehavior = compatDsValue(
            stringPreferencesKey(PreferKey.progressBarBehavior), "page"
        ),
        mouseWheelPage = compatDsValue(booleanPreferencesKey(PreferKey.mouseWheelPage), true),
        volumeKeyPage = compatDsValue(booleanPreferencesKey(PreferKey.volumeKeyPage), true),
        volumeKeyPageOnPlay = compatDsValue(
            booleanPreferencesKey(PreferKey.volumeKeyPageOnPlay), false
        ),
        keyPageOnLongPress = compatDsValue(
            booleanPreferencesKey(PreferKey.keyPageOnLongPress), false
        ),
        textSelectAble = compatDsValue(booleanPreferencesKey(PreferKey.textSelectAble), true),
        showBrightnessView = compatDsValue(
            booleanPreferencesKey(PreferKey.showBrightnessView), true
        ),
        noAnimScrollPage = compatDsValue(booleanPreferencesKey(PreferKey.noAnimScrollPage), false),
        previewImageByClick = compatDsValue(
            booleanPreferencesKey(PreferKey.previewImageByClick), false
        ),
        optimizeRender = compatDsValue(booleanPreferencesKey(PreferKey.optimizeRender), false),
        disableReturnKey = compatDsValue(booleanPreferencesKey(PreferKey.disableReturnKey), false),
        expandTextMenu = compatDsValue(booleanPreferencesKey(PreferKey.expandTextMenu), false),
        showReadTitleAddition = compatDsValue(
            booleanPreferencesKey(PreferKey.showReadTitleAddition), true
        ),
        readBarStyleFollowPage = compatDsValue(
            booleanPreferencesKey(PreferKey.readBarStyleFollowPage), false
        ),
        hideStatusBar = compatDsValue(booleanPreferencesKey(PreferKey.hideStatusBar), false),
        hideNavigationBar = compatDsValue(booleanPreferencesKey(PreferKey.hideNavigationBar), false),
        readBodyToLh = compatDsValue(booleanPreferencesKey(PreferKey.readBodyToLh), true),
        useZhLayout = compatDsValue(booleanPreferencesKey(PreferKey.useZhLayout), false),
        textFullJustify = compatDsValue(booleanPreferencesKey(PreferKey.textFullJustify), true),
        textBottomJustify = compatDsValue(booleanPreferencesKey(PreferKey.textBottomJustify), true),
    )
}

internal fun ReadSettings.toGatewayPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.readUrlOpenInBrowser to readUrlInBrowser,
    PreferKey.autoChangeSource to autoChangeSource,
    PreferKey.chineseConverterType to chineseConverterType,
    PreferKey.readingAnchorEnabled to readingAnchorEnabled,
    PreferKey.readAloudDetachReminderEnabled to readAloudDetachReminderEnabled,
    PreferKey.keepLight to keepLight,
    PreferKey.paddingDisplayCutouts to paddingDisplayCutouts,
    PreferKey.doublePageHorizontal to doublePageHorizontal,
    PreferKey.progressBarBehavior to progressBarBehavior,
    PreferKey.mouseWheelPage to mouseWheelPage,
    PreferKey.volumeKeyPage to volumeKeyPage,
    PreferKey.volumeKeyPageOnPlay to volumeKeyPageOnPlay,
    PreferKey.keyPageOnLongPress to keyPageOnLongPress,
    PreferKey.textSelectAble to textSelectAble,
    PreferKey.showBrightnessView to showBrightnessView,
    PreferKey.noAnimScrollPage to noAnimScrollPage,
    PreferKey.previewImageByClick to previewImageByClick,
    PreferKey.optimizeRender to optimizeRender,
    PreferKey.disableReturnKey to disableReturnKey,
    PreferKey.expandTextMenu to expandTextMenu,
    PreferKey.showReadTitleAddition to showReadTitleAddition,
    PreferKey.readBarStyleFollowPage to readBarStyleFollowPage,
    PreferKey.hideStatusBar to hideStatusBar,
    PreferKey.hideNavigationBar to hideNavigationBar,
    PreferKey.readBodyToLh to readBodyToLh,
    PreferKey.useZhLayout to useZhLayout,
    PreferKey.textFullJustify to textFullJustify,
    PreferKey.textBottomJustify to textBottomJustify,
)
