package io.legado.app.data.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.settings.ReadSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ReadSettings 与 DataStore 偏好键映射的往返守护（settings-ds-migration-plan.md Phase 0）。
 * 新增收编字段时必须同步补本测试，防止读写映射漂移。
 */
class ReadSettingsMappingTest {

    @Test
    fun `empty preferences map to defaults`() {
        val settings = emptyPreferences().toReadSettings()
        assertEquals(ReadSettings(), settings)
        // 阅读排版面板收编字段的 SP 现用默认
        assertEquals(0, settings.keepLight)
        assertEquals(0, settings.doublePageHorizontal)
        assertEquals("page", settings.progressBarBehavior)
        assertEquals(true, settings.mouseWheelPage)
        assertEquals(true, settings.textSelectAble)
        assertEquals(true, settings.showBrightnessView)
        assertEquals(false, settings.disableReturnKey)
        assertEquals(true, settings.showReadTitleAddition)
        assertEquals(false, settings.hideStatusBar)
        assertEquals(true, settings.readBodyToLh)
        assertEquals(false, settings.useZhLayout)
        assertEquals(true, settings.textFullJustify)
        assertEquals(true, settings.textBottomJustify)
    }

    @Test
    fun `typed preferences map to settings`() {
        val prefs = preferencesOf(
            booleanPreferencesKey(PreferKey.readUrlOpenInBrowser) to true,
            booleanPreferencesKey(PreferKey.autoChangeSource) to true,
            intPreferencesKey(PreferKey.chineseConverterType) to 2,
            booleanPreferencesKey(PreferKey.readingAnchorEnabled) to false,
            booleanPreferencesKey(PreferKey.readAloudDetachReminderEnabled) to true,
            intPreferencesKey(PreferKey.keepLight) to 5,
            booleanPreferencesKey(PreferKey.paddingDisplayCutouts) to true,
            intPreferencesKey(PreferKey.doublePageHorizontal) to 3,
            stringPreferencesKey(PreferKey.progressBarBehavior) to "chapter",
            booleanPreferencesKey(PreferKey.mouseWheelPage) to false,
            booleanPreferencesKey(PreferKey.volumeKeyPage) to false,
            booleanPreferencesKey(PreferKey.volumeKeyPageOnPlay) to true,
            booleanPreferencesKey(PreferKey.keyPageOnLongPress) to true,
            booleanPreferencesKey(PreferKey.textSelectAble) to false,
            booleanPreferencesKey(PreferKey.showBrightnessView) to false,
            booleanPreferencesKey(PreferKey.noAnimScrollPage) to true,
            booleanPreferencesKey(PreferKey.previewImageByClick) to true,
            booleanPreferencesKey(PreferKey.optimizeRender) to true,
            booleanPreferencesKey(PreferKey.disableReturnKey) to true,
            booleanPreferencesKey(PreferKey.expandTextMenu) to true,
            booleanPreferencesKey(PreferKey.showReadTitleAddition) to false,
            booleanPreferencesKey(PreferKey.readBarStyleFollowPage) to true,
            booleanPreferencesKey(PreferKey.hideStatusBar) to true,
            booleanPreferencesKey(PreferKey.hideNavigationBar) to true,
            booleanPreferencesKey(PreferKey.readBodyToLh) to false,
            booleanPreferencesKey(PreferKey.useZhLayout) to true,
            booleanPreferencesKey(PreferKey.textFullJustify) to false,
            booleanPreferencesKey(PreferKey.textBottomJustify) to false,
        )
        val settings = prefs.toReadSettings()
        assertEquals(
            ReadSettings(
                readUrlInBrowser = true,
                autoChangeSource = true,
                chineseConverterType = 2,
                readingAnchorEnabled = false,
                readAloudDetachReminderEnabled = true,
                keepLight = 5,
                paddingDisplayCutouts = true,
                doublePageHorizontal = 3,
                progressBarBehavior = "chapter",
                mouseWheelPage = false,
                volumeKeyPage = false,
                volumeKeyPageOnPlay = true,
                keyPageOnLongPress = true,
                textSelectAble = false,
                showBrightnessView = false,
                noAnimScrollPage = true,
                previewImageByClick = true,
                optimizeRender = true,
                disableReturnKey = true,
                expandTextMenu = true,
                showReadTitleAddition = false,
                readBarStyleFollowPage = true,
                hideStatusBar = true,
                hideNavigationBar = true,
                readBodyToLh = false,
                useZhLayout = true,
                textFullJustify = false,
                textBottomJustify = false,
            ),
            settings,
        )
    }

    @Test
    fun `compat reads handle legacy string stored values`() {
        // 历史 SP 脏数据：boolean 存成 "1"/"0" 字符串
        val prefs = preferencesOf(
            stringPreferencesKey(PreferKey.readingAnchorEnabled) to "0",
            stringPreferencesKey(PreferKey.readAloudDetachReminderEnabled) to "1",
            stringPreferencesKey(PreferKey.keepLight) to "10",
            stringPreferencesKey(PreferKey.doublePageHorizontal) to "2",
            stringPreferencesKey(PreferKey.mouseWheelPage) to "0",
            stringPreferencesKey(PreferKey.showBrightnessView) to "1",
        )
        val settings = prefs.toReadSettings()
        assertEquals(false, settings.readingAnchorEnabled)
        assertEquals(true, settings.readAloudDetachReminderEnabled)
        assertEquals(10, settings.keepLight)
        assertEquals(2, settings.doublePageHorizontal)
        assertEquals(false, settings.mouseWheelPage)
        assertEquals(true, settings.showBrightnessView)
    }

    @Test
    fun `gateway pref map keys stay stable for backup parity`() {
        val settings = ReadSettings(
            readUrlInBrowser = true,
            autoChangeSource = false,
            chineseConverterType = 1,
            readingAnchorEnabled = false,
            readAloudDetachReminderEnabled = true,
            keepLight = 5,
            progressBarBehavior = "chapter",
            hideStatusBar = true,
            textFullJustify = false,
        )
        val map = settings.toGatewayPrefMap()
        assertEquals(
            setOf(
                PreferKey.readUrlOpenInBrowser,
                PreferKey.autoChangeSource,
                PreferKey.chineseConverterType,
                PreferKey.readingAnchorEnabled,
                PreferKey.readAloudDetachReminderEnabled,
                PreferKey.keepLight,
                PreferKey.paddingDisplayCutouts,
                PreferKey.doublePageHorizontal,
                PreferKey.progressBarBehavior,
                PreferKey.mouseWheelPage,
                PreferKey.volumeKeyPage,
                PreferKey.volumeKeyPageOnPlay,
                PreferKey.keyPageOnLongPress,
                PreferKey.textSelectAble,
                PreferKey.showBrightnessView,
                PreferKey.noAnimScrollPage,
                PreferKey.previewImageByClick,
                PreferKey.optimizeRender,
                PreferKey.disableReturnKey,
                PreferKey.expandTextMenu,
                PreferKey.showReadTitleAddition,
                PreferKey.readBarStyleFollowPage,
                PreferKey.hideStatusBar,
                PreferKey.hideNavigationBar,
                PreferKey.readBodyToLh,
                PreferKey.useZhLayout,
                PreferKey.textFullJustify,
                PreferKey.textBottomJustify,
            ),
            map.keys,
        )
        assertEquals(false, map[PreferKey.readingAnchorEnabled])
        assertEquals(true, map[PreferKey.readAloudDetachReminderEnabled])
        assertEquals(5, map[PreferKey.keepLight])
        assertEquals("chapter", map[PreferKey.progressBarBehavior])
        assertEquals(true, map[PreferKey.hideStatusBar])
        assertEquals(false, map[PreferKey.textFullJustify])
    }
}
