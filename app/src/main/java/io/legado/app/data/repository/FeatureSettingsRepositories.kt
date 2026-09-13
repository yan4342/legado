package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import io.legado.app.BuildConfig
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.model.settings.BackupSettings
import io.legado.app.domain.model.settings.DownloadCacheSettings
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsBoolean
import io.legado.app.help.config.compatDsInt
import io.legado.app.help.config.compatDsString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 精简版 FeatureSettingsRepositories：只保留漫画阅读器闭包用到的
 * DownloadCache / Backup / Other 三个设置仓库。
 * （fork 原文含 AppShell/Theme/Cover/Lab/Translation 共 8 个仓库，其余 5 个与漫画无关已裁剪。）
 */

class DownloadCacheSettingsRepository : DownloadCacheSettingsGateway {
    override val currentSettings: DownloadCacheSettings
        get() = AppConfigStore.preferences.toDownloadCacheSettings()

    override val settings: Flow<DownloadCacheSettings> = AppConfigStore.preferencesFlow
        .map { it.toDownloadCacheSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (DownloadCacheSettings) -> DownloadCacheSettings) {
        AppConfigStore.atomicUpdateAndAwait(
            read = Preferences::toDownloadCacheSettings,
            toPrefMap = DownloadCacheSettings::toPrefMap,
            transform = transform,
        )
    }
}

class BackupSettingsRepository : BackupSettingsGateway {
    override val currentSettings: BackupSettings
        get() = AppConfigStore.preferences.toBackupSettings()

    override val settings: Flow<BackupSettings> = AppConfigStore.preferencesFlow
        .map { it.toBackupSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (BackupSettings) -> BackupSettings) {
        AppConfigStore.atomicUpdate(
            read = Preferences::toBackupSettings,
            toPrefMap = BackupSettings::toPrefMap,
            transform = transform,
        )
    }
}

class OtherSettingsRepository : OtherSettingsGateway {
    override val currentSettings: OtherSettings
        get() = AppConfigStore.preferences.toOtherSettings()

    override val settings: Flow<OtherSettings> = AppConfigStore.preferencesFlow
        .map { it.toOtherSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (OtherSettings) -> OtherSettings) {
        AppConfigStore.atomicUpdateAndAwait(
            read = Preferences::toOtherSettings,
            toPrefMap = OtherSettings::toPrefMap,
            transform = transform,
        )
    }
}

internal fun Preferences.toDownloadCacheSettings(): DownloadCacheSettings =
    DownloadCacheSettings(
        bitmapCacheSize = compatDsInt(PreferKey.bitmapCacheSize) ?: 50,
        imageRetainNum = compatDsInt(PreferKey.imageRetainNum) ?: 0,
        preDownloadNum = compatDsInt(PreferKey.preDownloadNum) ?: 10,
        threadCount = compatDsInt(PreferKey.threadCount) ?: 16,
        cacheBookThreadCount = compatDsInt(PreferKey.cacheBookThreadCount) ?: 16,
        userAgent = compatDsString(PreferKey.userAgent).orEmpty().ifBlank {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/${BuildConfig.Cronet_Main_Version} Safari/537.36"
        },
        cronetEnabled = compatDsBoolean(PreferKey.cronet) ?: false,
    )

internal fun DownloadCacheSettings.toPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.bitmapCacheSize to bitmapCacheSize,
    PreferKey.imageRetainNum to imageRetainNum,
    PreferKey.preDownloadNum to preDownloadNum,
    PreferKey.threadCount to threadCount,
    PreferKey.cacheBookThreadCount to cacheBookThreadCount,
    PreferKey.userAgent to userAgent,
    PreferKey.cronet to cronetEnabled,
)

internal fun Preferences.toBackupSettings(): BackupSettings = BackupSettings(
    webDavUrl = compatDsString(PreferKey.webDavUrl).orEmpty(),
    webDavAccount = compatDsString(PreferKey.webDavAccount).orEmpty(),
    webDavPassword = compatDsString(PreferKey.webDavPassword).orEmpty(),
    webDavDir = compatDsString(PreferKey.webDavDir) ?: "legado",
    webDavDeviceName = compatDsString(PreferKey.webDavDeviceName).orEmpty(),
    syncBookProgress = compatDsBoolean(PreferKey.syncBookProgress) ?: true,
    syncBookProgressPlus = compatDsBoolean(PreferKey.syncBookProgressPlus) ?: false,
    autoCheckNewBackup = compatDsBoolean(PreferKey.autoCheckNewBackup) ?: true,
    onlyLatestBackup = compatDsBoolean(PreferKey.onlyLatestBackup) ?: true,
    backupSyncMode = compatDsString(PreferKey.backupSyncMode) ?: "both",
    backupPath = compatDsString(PreferKey.backupPath),
)

internal fun BackupSettings.toPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.webDavUrl to webDavUrl,
    PreferKey.webDavAccount to webDavAccount,
    PreferKey.webDavPassword to webDavPassword,
    PreferKey.webDavDir to webDavDir,
    PreferKey.webDavDeviceName to webDavDeviceName,
    PreferKey.syncBookProgress to syncBookProgress,
    PreferKey.syncBookProgressPlus to syncBookProgressPlus,
    PreferKey.autoCheckNewBackup to autoCheckNewBackup,
    PreferKey.onlyLatestBackup to onlyLatestBackup,
    PreferKey.backupSyncMode to backupSyncMode,
    PreferKey.backupPath to backupPath,
)

internal fun Preferences.toOtherSettings(): OtherSettings {
    val rawSourceEditMaxLine = compatDsInt(PreferKey.sourceEditMaxLine) ?: Int.MAX_VALUE
    return OtherSettings(
        updateToVariant = compatDsString(PreferKey.updateToVariant) ?: "official_version",
        autoCheckUpdateOnStart = compatDsBoolean(PreferKey.autoCheckUpdateOnStart) ?: false,
        webServiceAutoStart = compatDsBoolean(PreferKey.webServiceAutoStart) ?: false,
        autoRefresh = compatDsBoolean(PreferKey.autoRefresh) ?: false,
        defaultToRead = compatDsBoolean(PreferKey.defaultToRead) ?: false,
        notificationsPost = compatDsBoolean(PreferKey.notificationsPost) ?: true,
        ignoreBatteryPermission = compatDsBoolean(PreferKey.ignoreBatteryPermission) ?: true,
        firebaseEnable = compatDsBoolean(PreferKey.firebaseEnable) ?: true,
        defaultBookTreeUri = compatDsString(PreferKey.defaultBookTreeUri),
        antiAlias = compatDsBoolean(PreferKey.antiAlias) ?: false,
        replaceEnableDefault = compatDsBoolean(PreferKey.replaceEnableDefault) ?: true,
        autoClearExpired = compatDsBoolean(PreferKey.autoClearExpired) ?: true,
        showAddToShelfAlert = compatDsBoolean(PreferKey.showAddToShelfAlert) ?: true,
        showMangaUi = compatDsBoolean(PreferKey.showMangaUi) ?: true,
        webServiceWakeLock = compatDsBoolean(PreferKey.webServiceWakeLock) ?: false,
        sourceEditMaxLine = rawSourceEditMaxLine.takeIf { it >= 10 } ?: Int.MAX_VALUE,
        webPort = compatDsInt(PreferKey.webPort) ?: 1122,
        processText = compatDsBoolean(PreferKey.processText) ?: true,
        recordLog = compatDsBoolean(PreferKey.recordLog) ?: false,
        recordHeapDump = compatDsBoolean(PreferKey.recordHeapDump) ?: false,
        audioPlayUseWakeLock = compatDsBoolean(PreferKey.audioPlayWakeLock) ?: false,
        importKeepName = compatDsBoolean(PreferKey.importKeepName) ?: false,
        importKeepGroup = compatDsBoolean(PreferKey.importKeepGroup) ?: false,
        importKeepEnable = compatDsBoolean(PreferKey.importKeepEnable) ?: false,
        fontSort = compatDsInt(PreferKey.fontSort) ?: 0,
    )
}

internal fun OtherSettings.toPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.updateToVariant to updateToVariant,
    PreferKey.autoCheckUpdateOnStart to autoCheckUpdateOnStart,
    PreferKey.webServiceAutoStart to webServiceAutoStart,
    PreferKey.autoRefresh to autoRefresh,
    PreferKey.defaultToRead to defaultToRead,
    PreferKey.notificationsPost to notificationsPost,
    PreferKey.ignoreBatteryPermission to ignoreBatteryPermission,
    PreferKey.firebaseEnable to firebaseEnable,
    PreferKey.defaultBookTreeUri to defaultBookTreeUri,
    PreferKey.antiAlias to antiAlias,
    PreferKey.replaceEnableDefault to replaceEnableDefault,
    PreferKey.autoClearExpired to autoClearExpired,
    PreferKey.showAddToShelfAlert to showAddToShelfAlert,
    PreferKey.showMangaUi to showMangaUi,
    PreferKey.webServiceWakeLock to webServiceWakeLock,
    PreferKey.sourceEditMaxLine to sourceEditMaxLine,
    PreferKey.webPort to webPort,
    PreferKey.processText to processText,
    PreferKey.recordLog to recordLog,
    PreferKey.recordHeapDump to recordHeapDump,
    PreferKey.audioPlayWakeLock to audioPlayUseWakeLock,
    PreferKey.importKeepName to importKeepName,
    PreferKey.importKeepGroup to importKeepGroup,
    PreferKey.importKeepEnable to importKeepEnable,
    PreferKey.fontSort to fontSort,
)
