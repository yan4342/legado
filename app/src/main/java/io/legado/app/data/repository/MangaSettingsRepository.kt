package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.domain.model.settings.MangaSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsBoolean
import io.legado.app.help.config.compatDsInt
import io.legado.app.help.config.compatDsString
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import splitties.init.appCtx

class MangaSettingsRepository : MangaSettingsGateway {

    override val currentSettings: MangaSettings
        get() = AppConfigStore.preferences.toMangaSettings()

    override val settings: Flow<MangaSettings> = AppConfigStore.preferencesFlow
        .map { it.toMangaSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (MangaSettings) -> MangaSettings) {
        AppConfigStore.atomicUpdate(
            read = Preferences::toMangaSettings,
            toPrefMap = MangaSettings::toPrefMap,
            transform = transform,
        )
    }
}

internal fun MangaSettings.toPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.showMangaUi to showMangaUi,
    PreferKey.disableMangaScale to disableMangaScale,
    PreferKey.disableMangaDoubleTapZoom to disableMangaDoubleTapZoom,
    PreferKey.disableMangaScrollAnimation to disableMangaScrollAnimation,
    PreferKey.disableMangaCrossFade to disableMangaCrossFade,
    PreferKey.mangaScrollMode to scrollMode,
    PreferKey.mangaPreDownloadNum to preDownloadNum,
    PreferKey.mangaChapterPrefetchCount to chapterPrefetchCount,
    PreferKey.mangaAutoOfflineCache to autoOfflineCache,
    PreferKey.mangaAutoPageSpeed to autoPageSpeed,
    PreferKey.mangaFooterConfig to footerConfig,
    PreferKey.disableClickScroll to disableClickScroll,
    PreferKey.mangaLongClick to longClick,
    PreferKey.mangaBackground to background,
    PreferKey.mangaAutoBackground to autoBackground,
    PreferKey.mangaPageScaleType to pageScaleType,
    PreferKey.mangaWidePageMode to widePageMode,
    PreferKey.mangaDoublePageMode to doublePageMode,
    PreferKey.mangaDoublePageCoverSingle to doublePageCoverSingle,
    PreferKey.mangaDoublePageInvert to doublePageInvert,
    PreferKey.mangaDoublePageShift to doublePageShift,
    PreferKey.mangaColorFilter to colorFilter,
    PreferKey.hideMangaTitle to hideTitle,
    PreferKey.enableMangaEInk to enableEInk,
    PreferKey.mangaEInkThreshold to eInkThreshold,
    PreferKey.enableMangaGray to enableGray,
    PreferKey.webtoonSidePaddingDp to webtoonSidePaddingDp,
    PreferKey.mangaVolumeKeyPage to volumeKeyPage,
    PreferKey.reverseVolumeKeyPage to reverseVolumeKeyPage,
    PreferKey.mangaClickActionTL to clickActionTL,
    PreferKey.mangaClickActionTC to clickActionTC,
    PreferKey.mangaClickActionTR to clickActionTR,
    PreferKey.mangaClickActionML to clickActionML,
    PreferKey.mangaClickActionMC to clickActionMC,
    PreferKey.mangaClickActionMR to clickActionMR,
    PreferKey.mangaClickActionBL to clickActionBL,
    PreferKey.mangaClickActionBC to clickActionBC,
    PreferKey.mangaClickActionBR to clickActionBR,
)

/**
 * 旧版 ReadManga 把漫画设置写在默认 SharedPreferences（"packageName_preferences"）。
 * settings DataStore 的 SP→DataStore 播种迁移（SpToDsSeedMigration）只填充 DataStore
 * 缺失的 key 且不清空 SP；SP 之后新写入的值不会自动覆盖 DataStore（两侧以先写者为准）。
 * 这里在 DataStore 缺失时回退读同名 SP key，保证升级后旧设置不丢。
 */
private fun Preferences.legacyBoolean(key: String, default: Boolean): Boolean =
    compatDsBoolean(key) ?: appCtx.getPrefBoolean(key, default)

private fun Preferences.legacyInt(key: String, default: Int): Int =
    compatDsInt(key) ?: appCtx.getPrefInt(key, default)

private fun Preferences.legacyString(key: String, default: String): String =
    compatDsString(key) ?: appCtx.getPrefString(key, default).orEmpty()

internal fun Preferences.toMangaSettings(): MangaSettings = MangaSettings(
    showMangaUi = legacyBoolean(PreferKey.showMangaUi, true),
    disableMangaScale = legacyBoolean(PreferKey.disableMangaScale, true),
    disableMangaDoubleTapZoom = legacyBoolean(PreferKey.disableMangaDoubleTapZoom, true),
    disableMangaScrollAnimation = legacyBoolean(PreferKey.disableMangaScrollAnimation, false),
    disableMangaCrossFade = legacyBoolean(PreferKey.disableMangaCrossFade, false),
    scrollMode = legacyInt(PreferKey.mangaScrollMode, 4),
    preDownloadNum = legacyInt(PreferKey.mangaPreDownloadNum, 10),
    // Manga (MD3 port)：章节预取仅用于自动离线缓存，默认关闭
    chapterPrefetchCount = legacyInt(PreferKey.mangaChapterPrefetchCount, 0),
    autoOfflineCache = legacyBoolean(PreferKey.mangaAutoOfflineCache, false),
    autoPageSpeed = legacyInt(PreferKey.mangaAutoPageSpeed, 3),
    footerConfig = legacyString(PreferKey.mangaFooterConfig, ""),
    disableClickScroll = legacyBoolean(PreferKey.disableClickScroll, false),
    longClick = legacyBoolean(PreferKey.mangaLongClick, true),
    background = legacyInt(PreferKey.mangaBackground, 0xFF000000.toInt()),
    autoBackground = legacyBoolean(PreferKey.mangaAutoBackground, false),
    pageScaleType = legacyInt(PreferKey.mangaPageScaleType, 0),
    widePageMode = legacyInt(PreferKey.mangaWidePageMode, 0),
    doublePageMode = legacyInt(PreferKey.mangaDoublePageMode, 0),
    // Manga (MD3 port)：双页封面对齐
    doublePageCoverSingle = legacyBoolean(PreferKey.mangaDoublePageCoverSingle, true),
    doublePageInvert = legacyBoolean(PreferKey.mangaDoublePageInvert, false),
    doublePageShift = legacyBoolean(PreferKey.mangaDoublePageShift, false),
    colorFilter = legacyString(PreferKey.mangaColorFilter, ""),
    hideTitle = legacyBoolean(PreferKey.hideMangaTitle, false),
    enableEInk = legacyBoolean(PreferKey.enableMangaEInk, false),
    eInkThreshold = legacyInt(PreferKey.mangaEInkThreshold, 150),
    enableGray = legacyBoolean(PreferKey.enableMangaGray, false),
    webtoonSidePaddingDp = legacyInt(PreferKey.webtoonSidePaddingDp, 0),
    volumeKeyPage = legacyBoolean(PreferKey.mangaVolumeKeyPage, false),
    reverseVolumeKeyPage = legacyBoolean(PreferKey.reverseVolumeKeyPage, false),
    clickActionTL = legacyInt(PreferKey.mangaClickActionTL, -1),
    clickActionTC = legacyInt(PreferKey.mangaClickActionTC, -1),
    clickActionTR = legacyInt(PreferKey.mangaClickActionTR, 1),
    clickActionML = legacyInt(PreferKey.mangaClickActionML, 2),
    clickActionMC = legacyInt(PreferKey.mangaClickActionMC, 0),
    clickActionMR = legacyInt(PreferKey.mangaClickActionMR, 1),
    clickActionBL = legacyInt(PreferKey.mangaClickActionBL, 2),
    clickActionBC = legacyInt(PreferKey.mangaClickActionBC, 1),
    clickActionBR = legacyInt(PreferKey.mangaClickActionBR, 1),
)
