package io.legado.app.help.config

import android.content.SharedPreferences
import android.os.Build
import io.legado.app.BuildConfig
import io.legado.app.constant.AppConst
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.utils.canvasrecorder.CanvasRecorderFactory
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isNightMode
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefFloat
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefLong
import io.legado.app.utils.putPrefString
import io.legado.app.utils.removePref
import io.legado.app.utils.sysConfiguration
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

@Suppress("MemberVisibilityCanBePrivate", "ConstPropertyName")
object AppConfig : SharedPreferences.OnSharedPreferenceChangeListener {
    const val AI_CHAT_RENDER_COMPOSE = "compose"
    const val AI_CHAT_RENDER_HTML = "html"

    /** 未设置 userAgent 时的计算默认值（原 getPrefUserAgent 逻辑，设置流收编 Phase 2 批 3） */
    private val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" +
                BuildConfig.Cronet_Main_Version + " Safari/537.36"

    var isCronet: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.cronet) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.cronet, value)
            appCtx.putPrefBoolean(PreferKey.cronet, value)
        }
    var useAntiAlias: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.antiAlias) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.antiAlias, value)
            appCtx.putPrefBoolean(PreferKey.antiAlias, value)
        }
    var userAgent: String
        get() = AppConfigStore.getString(PreferKey.userAgent)
            ?.takeUnless { it.isBlank() }
            ?: DEFAULT_USER_AGENT
        set(value) {
            AppConfigStore.putString(PreferKey.userAgent, value)
            appCtx.putPrefString(PreferKey.userAgent, value)
        }
    var themeMode: String
        get() = AppConfigStore.getString(PreferKey.themeMode) ?: "0"
        set(value) {
            AppConfigStore.putString(PreferKey.themeMode, value)
            appCtx.putPrefString(PreferKey.themeMode, value)
        }

    /** 由 themeMode 派生，随快照实时（设置流收编 Phase 2 批 1） */
    val isEInkMode: Boolean get() = themeMode == "3"
    var clickActionTL: Int
        get() = AppConfigStore.getInt(PreferKey.clickActionTL) ?: 2
        set(value) {
            AppConfigStore.putInt(PreferKey.clickActionTL, value)
            appCtx.putPrefInt(PreferKey.clickActionTL, value)
        }
    var clickActionTC: Int
        get() = AppConfigStore.getInt(PreferKey.clickActionTC) ?: 2
        set(value) {
            AppConfigStore.putInt(PreferKey.clickActionTC, value)
            appCtx.putPrefInt(PreferKey.clickActionTC, value)
        }
    var clickActionTR: Int
        get() = AppConfigStore.getInt(PreferKey.clickActionTR) ?: 1
        set(value) {
            AppConfigStore.putInt(PreferKey.clickActionTR, value)
            appCtx.putPrefInt(PreferKey.clickActionTR, value)
        }
    var clickActionML: Int
        get() = AppConfigStore.getInt(PreferKey.clickActionML) ?: 2
        set(value) {
            AppConfigStore.putInt(PreferKey.clickActionML, value)
            appCtx.putPrefInt(PreferKey.clickActionML, value)
        }
    var clickActionMC: Int
        get() = AppConfigStore.getInt(PreferKey.clickActionMC) ?: 0
        set(value) {
            AppConfigStore.putInt(PreferKey.clickActionMC, value)
            appCtx.putPrefInt(PreferKey.clickActionMC, value)
        }
    var clickActionMR: Int
        get() = AppConfigStore.getInt(PreferKey.clickActionMR) ?: 1
        set(value) {
            AppConfigStore.putInt(PreferKey.clickActionMR, value)
            appCtx.putPrefInt(PreferKey.clickActionMR, value)
        }
    var clickActionBL: Int
        get() = AppConfigStore.getInt(PreferKey.clickActionBL) ?: 2
        set(value) {
            AppConfigStore.putInt(PreferKey.clickActionBL, value)
            appCtx.putPrefInt(PreferKey.clickActionBL, value)
        }
    var clickActionBC: Int
        get() = AppConfigStore.getInt(PreferKey.clickActionBC) ?: 1
        set(value) {
            AppConfigStore.putInt(PreferKey.clickActionBC, value)
            appCtx.putPrefInt(PreferKey.clickActionBC, value)
        }
    var clickActionBR: Int
        get() = AppConfigStore.getInt(PreferKey.clickActionBR) ?: 1
        set(value) {
            AppConfigStore.putInt(PreferKey.clickActionBR, value)
            appCtx.putPrefInt(PreferKey.clickActionBR, value)
        }

    var useDefaultCover: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.useDefaultCover) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.useDefaultCover, value)
            appCtx.putPrefBoolean(PreferKey.useDefaultCover, value)
        }
    var optimizeRender = try {
        CanvasRecorderFactory.isSupport &&
                (AppConfigStore.getBoolean(PreferKey.optimizeRender) ?: false)
    } catch (_: Throwable) { false }
    var recordLog: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.recordLog) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.recordLog, value)
            appCtx.putPrefBoolean(PreferKey.recordLog, value)
        }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        // 设置流已收编 settings DataStore（Phase 2），Restore 也直写 DS（Phase 4），
        // 桥接分支已拆（Phase 5）；仅保留缓存变量的刷新分支
        when (key) {
            PreferKey.optimizeRender -> optimizeRender = CanvasRecorderFactory.isSupport
                    && (AppConfigStore.getBoolean(PreferKey.optimizeRender) ?: false)
        }
    }

    var isNightTheme: Boolean
        get() = when (themeMode) {
            "1" -> false
            "2" -> true
            "3" -> false
            else -> sysConfiguration.isNightMode
        }
        set(value) {
            if (isNightTheme != value) {
                if (value) {
                    themeMode = "2"
                } else {
                    themeMode = "1"
                }
            }
        }

    var showUnread: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.showUnread) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.showUnread, value)
            appCtx.putPrefBoolean(PreferKey.showUnread, value)
        }

    var showLastUpdateTime: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.showLastUpdateTime) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.showLastUpdateTime, value)
            appCtx.putPrefBoolean(PreferKey.showLastUpdateTime, value)
        }

    var showWaitUpCount: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.showWaitUpCount) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.showWaitUpCount, value)
            appCtx.putPrefBoolean(PreferKey.showWaitUpCount, value)
        }

    var readBrightness: Int
        get() = if (isNightTheme) {
            AppConfigStore.getInt(PreferKey.nightBrightness) ?: 100
        } else {
            AppConfigStore.getInt(PreferKey.brightness) ?: 100
        }
        set(value) {
            if (isNightTheme) {
                AppConfigStore.putInt(PreferKey.nightBrightness, value)
                appCtx.putPrefInt(PreferKey.nightBrightness, value)
            } else {
                AppConfigStore.putInt(PreferKey.brightness, value)
                appCtx.putPrefInt(PreferKey.brightness, value)
            }
        }

    val textSelectAble: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.textSelectAble) ?: true

    var isTransparentStatusBar: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.transparentStatusBar) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.transparentStatusBar, value)
            appCtx.putPrefBoolean(PreferKey.transparentStatusBar, value)
        }

    var immNavigationBar: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.immNavigationBar) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.immNavigationBar, value)
            appCtx.putPrefBoolean(PreferKey.immNavigationBar, value)
        }

    var screenOrientation: String?
        get() = AppConfigStore.getString(PreferKey.screenOrientation)
        set(value) {
            AppConfigStore.putString(PreferKey.screenOrientation, value)
            appCtx.putPrefString(PreferKey.screenOrientation, value)
        }

    var bookGroupStyle: Int
        get() = AppConfigStore.getInt(PreferKey.bookGroupStyle) ?: 0
        set(value) {
            AppConfigStore.putInt(PreferKey.bookGroupStyle, value)
            appCtx.putPrefInt(PreferKey.bookGroupStyle, value)
        }

    var bookshelfLayout: Int
        get() = AppConfigStore.getInt(PreferKey.bookshelfLayout) ?: 0
        set(value) {
            AppConfigStore.putInt(PreferKey.bookshelfLayout, value)
            appCtx.putPrefInt(PreferKey.bookshelfLayout, value)
        }

    var saveTabPosition: Int
        get() = AppConfigStore.getInt(PreferKey.saveTabPosition) ?: 0
        set(value) {
            AppConfigStore.putInt(PreferKey.saveTabPosition, value)
            appCtx.putPrefInt(PreferKey.saveTabPosition, value)
        }

    var bookExportFileName: String?
        get() = AppConfigStore.getString(PreferKey.bookExportFileName)
        set(value) {
            AppConfigStore.putString(PreferKey.bookExportFileName, value)
            appCtx.putPrefString(PreferKey.bookExportFileName, value)
        }

    // 保存 自定义导出章节模式 文件名js表达式
    var episodeExportFileName: String?
        get() = AppConfigStore.getString(PreferKey.episodeExportFileName) ?: ""
        set(value) {
            AppConfigStore.putString(PreferKey.episodeExportFileName, value)
            appCtx.putPrefString(PreferKey.episodeExportFileName, value)
        }

    var bookImportFileName: String?
        get() = AppConfigStore.getString(PreferKey.bookImportFileName)
        set(value) {
            AppConfigStore.putString(PreferKey.bookImportFileName, value)
            appCtx.putPrefString(PreferKey.bookImportFileName, value)
        }

    // ignorePrefKeys 成员：不进备份，纯 DS 读写（Phase 2 批 3）。
    // 清空时同步 removePref，防止 SpToDsSeedMigration 用 SP 里的陈旧值复活已清除的 key
    var backupPath: String?
        get() = AppConfigStore.getString(PreferKey.backupPath)
        set(value) {
            if (value.isNullOrEmpty()) {
                AppConfigStore.remove(PreferKey.backupPath)
                appCtx.removePref(PreferKey.backupPath)
            } else {
                AppConfigStore.putString(PreferKey.backupPath, value)
            }
        }

    // 书籍保存位置
    // ignorePrefKeys 成员：不进备份，纯 DS 读写，无 SP 镜像（Phase 2 批 3）
    var defaultBookTreeUri: String?
        get() = AppConfigStore.getString(PreferKey.defaultBookTreeUri)
        set(value) {
            if (value.isNullOrEmpty()) {
                AppConfigStore.remove(PreferKey.defaultBookTreeUri)
                appCtx.removePref(PreferKey.defaultBookTreeUri)
            } else {
                AppConfigStore.putString(PreferKey.defaultBookTreeUri, value)
            }
        }

    var showDiscovery: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.showDiscovery) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.showDiscovery, value)
            appCtx.putPrefBoolean(PreferKey.showDiscovery, value)
        }

    var showRSS: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.showRss) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.showRss, value)
            appCtx.putPrefBoolean(PreferKey.showRss, value)
        }

    var autoRefreshBook: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.autoRefresh) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.autoRefresh, value)
            appCtx.putPrefBoolean(PreferKey.autoRefresh, value)
        }

    var enableReview: Boolean
        get() = BuildConfig.DEBUG && (AppConfigStore.getBoolean(PreferKey.enableReview) ?: false)
        set(value) {
            AppConfigStore.putBoolean(PreferKey.enableReview, value)
            appCtx.putPrefBoolean(PreferKey.enableReview, value)
        }

    var threadCount: Int
        get() = AppConfigStore.getInt(PreferKey.threadCount) ?: 16
        set(value) {
            AppConfigStore.putInt(PreferKey.threadCount, value)
            appCtx.putPrefInt(PreferKey.threadCount, value)
        }

    var remoteServerId: Long
        get() = AppConfigStore.getLong(PreferKey.remoteServerId) ?: 0L
        set(value) {
            AppConfigStore.putLong(PreferKey.remoteServerId, value)
            appCtx.putPrefLong(PreferKey.remoteServerId, value)
        }

    // 添加本地选择的目录
    var importBookPath: String?
        get() = AppConfigStore.getString(PreferKey.importBookPath)
        set(value) {
            AppConfigStore.putString(PreferKey.importBookPath, value)
            // putString(null) 即从 SP 移除
            appCtx.putPrefString(PreferKey.importBookPath, value)
        }

    var ttsFlowSys: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.ttsFollowSys) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.ttsFollowSys, value)
            appCtx.putPrefBoolean(PreferKey.ttsFollowSys, value)
        }

    val noAnimScrollPage: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.noAnimScrollPage) ?: false

    const val defaultSpeechRate = 5

    var ttsSpeechRate: Int
        get() = AppConfigStore.getInt(PreferKey.ttsSpeechRate) ?: defaultSpeechRate
        set(value) {
            AppConfigStore.putInt(PreferKey.ttsSpeechRate, value)
            appCtx.putPrefInt(PreferKey.ttsSpeechRate, value)
        }

    var ttsTimer: Int
        get() = AppConfigStore.getInt(PreferKey.ttsTimer) ?: 0
        set(value) {
            AppConfigStore.putInt(PreferKey.ttsTimer, value)
            appCtx.putPrefInt(PreferKey.ttsTimer, value)
        }

    var ttsParagraphInterval: Int
        get() = AppConfigStore.getInt(PreferKey.ttsParagraphInterval) ?: 0
        set(value) {
            AppConfigStore.putInt(PreferKey.ttsParagraphInterval, value)
            appCtx.putPrefInt(PreferKey.ttsParagraphInterval, value)
        }

    /** "classic" | "player" — which UI opens from the reader aloud button */
    var readAloudDefaultInterface: String
        get() = AppConfigStore.getString(PreferKey.readAloudDefaultInterface) ?: "classic"
        set(value) {
            AppConfigStore.putString(PreferKey.readAloudDefaultInterface, value)
            appCtx.putPrefString(PreferKey.readAloudDefaultInterface, value)
        }

    var showReadAloudCapsule: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.showReadAloudCapsule) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.showReadAloudCapsule, value)
            appCtx.putPrefBoolean(PreferKey.showReadAloudCapsule, value)
        }

    // ── 朗读服务域收编（Phase 5，ReadAloudSettingsRepository 剩余 SP 直连 key）──
    // ignorePrefKeys 成员：不进备份，纯 DS 读写，无 SP 镜像
    var readAloudWakeLock: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.readAloudWakeLock) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.readAloudWakeLock, value)
        }

    var readAloudByPage: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.readAloudByPage) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.readAloudByPage, value)
            appCtx.putPrefBoolean(PreferKey.readAloudByPage, value)
        }

    var mediaButtonPerNext: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.mediaButtonPerNext) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.mediaButtonPerNext, value)
            appCtx.putPrefBoolean(PreferKey.mediaButtonPerNext, value)
        }

    var systemMediaControlCompatibilityChange: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.systemMediaControlCompatibilityChange) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.systemMediaControlCompatibilityChange, value)
            appCtx.putPrefBoolean(PreferKey.systemMediaControlCompatibilityChange, value)
        }

    var audioCacheCleanTime: Int
        get() = AppConfigStore.getInt(PreferKey.audioCacheCleanTime) ?: 10
        set(value) {
            AppConfigStore.putInt(PreferKey.audioCacheCleanTime, value)
            appCtx.putPrefInt(PreferKey.audioCacheCleanTime, value)
        }

    var capsuleAutoCollapse: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.capsuleAutoCollapse) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.capsuleAutoCollapse, value)
            appCtx.putPrefBoolean(PreferKey.capsuleAutoCollapse, value)
        }

    var capsuleOffsetX: Float
        get() = AppConfigStore.getFloat(PreferKey.capsuleOffsetX) ?: 0f
        set(value) {
            AppConfigStore.putFloat(PreferKey.capsuleOffsetX, value)
            appCtx.putPrefFloat(PreferKey.capsuleOffsetX, value)
        }

    var capsuleOffsetY: Float
        get() = AppConfigStore.getFloat(PreferKey.capsuleOffsetY) ?: 0f
        set(value) {
            AppConfigStore.putFloat(PreferKey.capsuleOffsetY, value)
            appCtx.putPrefFloat(PreferKey.capsuleOffsetY, value)
        }

    // 历史遗留 key：实际生效的是 contentSelectSpeakMod（contentReadAloudMod），本 key 暂无活跃消费方
    var contentSelectSpeakMode: Int
        get() = AppConfigStore.getInt(PreferKey.contentSelectSpeakMode) ?: 0
        set(value) {
            AppConfigStore.putInt(PreferKey.contentSelectSpeakMode, value)
            appCtx.putPrefInt(PreferKey.contentSelectSpeakMode, value)
        }

    var useMultiSpeaker: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.useMultiSpeaker) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.useMultiSpeaker, value)
            appCtx.putPrefBoolean(PreferKey.useMultiSpeaker, value)
        }

    var audioPreDownloadNum: Int
        get() = AppConfigStore.getInt(PreferKey.audioPreDownloadNum) ?: 10
        set(value) {
            AppConfigStore.putInt(PreferKey.audioPreDownloadNum, value)
            appCtx.putPrefInt(PreferKey.audioPreDownloadNum, value)
        }

    var ttsPreSynthesisConcurrency: Int
        get() = AppConfigStore.getInt(PreferKey.ttsPreSynthesisConcurrency) ?: 3
        set(value) {
            AppConfigStore.putInt(PreferKey.ttsPreSynthesisConcurrency, value)
            appCtx.putPrefInt(PreferKey.ttsPreSynthesisConcurrency, value)
        }

    var speechAnalysisMode: String
        get() = AppConfigStore.getString(PreferKey.speechAnalysisMode) ?: "rule"
        set(value) {
            AppConfigStore.putString(PreferKey.speechAnalysisMode, value)
            appCtx.putPrefString(PreferKey.speechAnalysisMode, value)
        }

    val speechRatePlay: Int get() = if (ttsFlowSys) defaultSpeechRate else ttsSpeechRate

    var chineseConverterType: Int
        get() = AppConfigStore.getInt(PreferKey.chineseConverterType) ?: 0
        set(value) {
            AppConfigStore.putInt(PreferKey.chineseConverterType, value)
            appCtx.putPrefInt(PreferKey.chineseConverterType, value)
        }

    var systemTypefaces: Int
        get() = AppConfigStore.getInt(PreferKey.systemTypefaces) ?: 0
        set(value) {
            AppConfigStore.putInt(PreferKey.systemTypefaces, value)
            appCtx.putPrefInt(PreferKey.systemTypefaces, value)
        }

    var elevation: Int
        get() = if (isEInkMode) 0 else AppConfigStore.getInt(PreferKey.barElevation)
            ?: AppConst.sysElevation
        set(value) {
            AppConfigStore.putInt(PreferKey.barElevation, value)
            appCtx.putPrefInt(PreferKey.barElevation, value)
        }

    var readUrlInBrowser: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.readUrlOpenInBrowser) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.readUrlOpenInBrowser, value)
            appCtx.putPrefBoolean(PreferKey.readUrlOpenInBrowser, value)
        }

    var exportCharset: String
        get() = AppConfigStore.getString(PreferKey.exportCharset)
            ?.takeUnless { it.isBlank() } ?: "UTF-8"
        set(value) {
            AppConfigStore.putString(PreferKey.exportCharset, value)
            appCtx.putPrefString(PreferKey.exportCharset, value)
        }

    var exportUseReplace: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.exportUseReplace) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.exportUseReplace, value)
            appCtx.putPrefBoolean(PreferKey.exportUseReplace, value)
        }

    var exportToWebDav: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.exportToWebDav) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.exportToWebDav, value)
            appCtx.putPrefBoolean(PreferKey.exportToWebDav, value)
        }
    var exportNoChapterName: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.exportNoChapterName) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.exportNoChapterName, value)
            appCtx.putPrefBoolean(PreferKey.exportNoChapterName, value)
        }

    // 是否启用自定义导出 default->false
    var enableCustomExport: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.enableCustomExport) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.enableCustomExport, value)
            appCtx.putPrefBoolean(PreferKey.enableCustomExport, value)
        }

    var exportType: Int
        get() = AppConfigStore.getInt(PreferKey.exportType) ?: 0
        set(value) {
            AppConfigStore.putInt(PreferKey.exportType, value)
            appCtx.putPrefInt(PreferKey.exportType, value)
        }
    var exportPictureFile: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.exportPictureFile) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.exportPictureFile, value)
            appCtx.putPrefBoolean(PreferKey.exportPictureFile, value)
        }

    var parallelExportBook: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.parallelExportBook) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.parallelExportBook, value)
            appCtx.putPrefBoolean(PreferKey.parallelExportBook, value)
        }

    var changeSourceCheckAuthor: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.changeSourceCheckAuthor) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.changeSourceCheckAuthor, value)
            appCtx.putPrefBoolean(PreferKey.changeSourceCheckAuthor, value)
        }

    var ttsEngine: String?
        get() = AppConfigStore.getString(PreferKey.ttsEngine)
        set(value) {
            AppConfigStore.putString(PreferKey.ttsEngine, value)
            appCtx.putPrefString(PreferKey.ttsEngine, value)
        }

    var webPort: Int
        get() = AppConfigStore.getInt(PreferKey.webPort) ?: 1122
        set(value) {
            AppConfigStore.putInt(PreferKey.webPort, value)
            appCtx.putPrefInt(PreferKey.webPort, value)
        }

    var tocUiUseReplace: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.tocUiUseReplace) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.tocUiUseReplace, value)
            appCtx.putPrefBoolean(PreferKey.tocUiUseReplace, value)
        }

    var tocCountWords: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.tocCountWords) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.tocCountWords, value)
            appCtx.putPrefBoolean(PreferKey.tocCountWords, value)
        }

    var enableReadRecord: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.enableReadRecord) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.enableReadRecord, value)
            appCtx.putPrefBoolean(PreferKey.enableReadRecord, value)
        }

    val autoChangeSource: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.autoChangeSource) ?: true

    var changeSourceLoadInfo: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.changeSourceLoadInfo) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.changeSourceLoadInfo, value)
            appCtx.putPrefBoolean(PreferKey.changeSourceLoadInfo, value)
        }

    var changeSourceLoadToc: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.changeSourceLoadToc) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.changeSourceLoadToc, value)
            appCtx.putPrefBoolean(PreferKey.changeSourceLoadToc, value)
        }

    var changeSourceLoadWordCount: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.changeSourceLoadWordCount) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.changeSourceLoadWordCount, value)
            appCtx.putPrefBoolean(PreferKey.changeSourceLoadWordCount, value)
        }

    var openBookInfoByClickTitle: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.openBookInfoByClickTitle) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.openBookInfoByClickTitle, value)
            appCtx.putPrefBoolean(PreferKey.openBookInfoByClickTitle, value)
        }

    var showBookshelfFastScroller: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.showBookshelfFastScroller) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.showBookshelfFastScroller, value)
            appCtx.putPrefBoolean(PreferKey.showBookshelfFastScroller, value)
        }

    var contentSelectSpeakMod: Int
        get() = AppConfigStore.getInt(PreferKey.contentSelectSpeakMod) ?: 0
        set(value) {
            AppConfigStore.putInt(PreferKey.contentSelectSpeakMod, value)
            appCtx.putPrefInt(PreferKey.contentSelectSpeakMod, value)
        }

    var batchChangeSourceDelay: Int
        get() = AppConfigStore.getInt(PreferKey.batchChangeSourceDelay) ?: 0
        set(value) {
            AppConfigStore.putInt(PreferKey.batchChangeSourceDelay, value)
            appCtx.putPrefInt(PreferKey.batchChangeSourceDelay, value)
        }

    var importKeepName: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.importKeepName) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.importKeepName, value)
            appCtx.putPrefBoolean(PreferKey.importKeepName, value)
        }
    var importKeepGroup: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.importKeepGroup) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.importKeepGroup, value)
            appCtx.putPrefBoolean(PreferKey.importKeepGroup, value)
        }
    var importKeepEnable: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.importKeepEnable) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.importKeepEnable, value)
            appCtx.putPrefBoolean(PreferKey.importKeepEnable, value)
        }

    var previewImageByClick: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.previewImageByClick) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.previewImageByClick, value)
            // SP 镜像：Backup 的 config.xml 来自 SP 全量，Phase 4 前必须保留
            appCtx.putPrefBoolean(PreferKey.previewImageByClick, value)
        }

    var preDownloadNum: Int
        get() = AppConfigStore.getInt(PreferKey.preDownloadNum) ?: 10
        set(value) {
            AppConfigStore.putInt(PreferKey.preDownloadNum, value)
            appCtx.putPrefInt(PreferKey.preDownloadNum, value)
        }

    var syncBookProgress: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.syncBookProgress) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.syncBookProgress, value)
            appCtx.putPrefBoolean(PreferKey.syncBookProgress, value)
        }

    var syncBookProgressPlus: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.syncBookProgressPlus) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.syncBookProgressPlus, value)
            appCtx.putPrefBoolean(PreferKey.syncBookProgressPlus, value)
        }

    var mediaButtonOnExit: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.mediaButtonOnExit) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.mediaButtonOnExit, value)
            appCtx.putPrefBoolean(PreferKey.mediaButtonOnExit, value)
        }

    var readAloudByMediaButton: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.readAloudByMediaButton) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.readAloudByMediaButton, value)
            appCtx.putPrefBoolean(PreferKey.readAloudByMediaButton, value)
        }

    var replaceEnableDefault: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.replaceEnableDefault) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.replaceEnableDefault, value)
            appCtx.putPrefBoolean(PreferKey.replaceEnableDefault, value)
        }

    var webDavDir: String?
        get() = AppConfigStore.getString(PreferKey.webDavDir) ?: "legado"
        set(value) {
            AppConfigStore.putString(PreferKey.webDavDir, value)
            appCtx.putPrefString(PreferKey.webDavDir, value)
        }

    // WebDav 三件套收编（Phase 5）：读走 DS 快照、写 DS + SP 镜像；
    // password 链保持不变——Restore 解密后经 putAll 双写、Backup 从 SP 读后加密导出，
    // SP 镜像保证 Backup 导出值不丢；置 null 即两侧同删（鉴权失败清除路径用）
    var webDavUrl: String?
        get() = AppConfigStore.getString(PreferKey.webDavUrl)
        set(value) {
            AppConfigStore.putString(PreferKey.webDavUrl, value)
            appCtx.putPrefString(PreferKey.webDavUrl, value)
        }

    var webDavAccount: String?
        get() = AppConfigStore.getString(PreferKey.webDavAccount)
        set(value) {
            AppConfigStore.putString(PreferKey.webDavAccount, value)
            appCtx.putPrefString(PreferKey.webDavAccount, value)
        }

    var webDavPassword: String?
        get() = AppConfigStore.getString(PreferKey.webDavPassword)
        set(value) {
            AppConfigStore.putString(PreferKey.webDavPassword, value)
            appCtx.putPrefString(PreferKey.webDavPassword, value)
        }

    // ignorePrefKeys 成员：不进备份，纯 DS 读写，无 SP 镜像（Phase 2 批 3）
    var webDavDeviceName: String?
        get() = AppConfigStore.getString(PreferKey.webDavDeviceName) ?: Build.MODEL
        set(value) {
            AppConfigStore.putString(PreferKey.webDavDeviceName, value)
        }

    var recordHeapDump: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.recordHeapDump) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.recordHeapDump, value)
            appCtx.putPrefBoolean(PreferKey.recordHeapDump, value)
        }

    var loadCoverOnlyWifi: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.loadCoverOnlyWifi) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.loadCoverOnlyWifi, value)
            appCtx.putPrefBoolean(PreferKey.loadCoverOnlyWifi, value)
        }

    var showAddToShelfAlert: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.showAddToShelfAlert) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.showAddToShelfAlert, value)
            appCtx.putPrefBoolean(PreferKey.showAddToShelfAlert, value)
        }

    // ── 其他设置域收编（Phase 5，OtherSettingsRepository 孤儿映射的活跃调用点切换）──
    var defaultToRead: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.defaultToRead) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.defaultToRead, value)
            appCtx.putPrefBoolean(PreferKey.defaultToRead, value)
        }

    var autoClearExpired: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.autoClearExpired) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.autoClearExpired, value)
            appCtx.putPrefBoolean(PreferKey.autoClearExpired, value)
        }

    var processText: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.processText) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.processText, value)
            appCtx.putPrefBoolean(PreferKey.processText, value)
        }

    // ignorePrefKeys 成员：不进备份，纯 DS 读写，无 SP 镜像（Phase 5）
    var webServiceWakeLock: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.webServiceWakeLock) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.webServiceWakeLock, value)
        }

    var ignoreAudioFocus: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.ignoreAudioFocus) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.ignoreAudioFocus, value)
            appCtx.putPrefBoolean(PreferKey.ignoreAudioFocus, value)
        }

    var pauseReadAloudWhilePhoneCalls: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.pauseReadAloudWhilePhoneCalls) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.pauseReadAloudWhilePhoneCalls, value)
            appCtx.putPrefBoolean(PreferKey.pauseReadAloudWhilePhoneCalls, value)
        }

    var onlyLatestBackup: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.onlyLatestBackup) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.onlyLatestBackup, value)
            appCtx.putPrefBoolean(PreferKey.onlyLatestBackup, value)
        }

    var autoCheckNewBackup: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.autoCheckNewBackup) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.autoCheckNewBackup, value)
            appCtx.putPrefBoolean(PreferKey.autoCheckNewBackup, value)
        }

    var defaultHomePage: String?
        get() = AppConfigStore.getString(PreferKey.defaultHomePage) ?: "bookshelf"
        set(value) {
            AppConfigStore.putString(PreferKey.defaultHomePage, value)
            appCtx.putPrefString(PreferKey.defaultHomePage, value)
        }

    var updateToVariant: String?
        get() = AppConfigStore.getString(PreferKey.updateToVariant) ?: "default_version"
        set(value) {
            AppConfigStore.putString(PreferKey.updateToVariant, value)
            appCtx.putPrefString(PreferKey.updateToVariant, value)
        }

    var streamReadAloudAudio: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.streamReadAloudAudio) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.streamReadAloudAudio, value)
            appCtx.putPrefBoolean(PreferKey.streamReadAloudAudio, value)
        }

    val doublePageHorizontal: String?
        get() = AppConfigStore.getString(PreferKey.doublePageHorizontal)

    val progressBarBehavior: String?
        get() = AppConfigStore.getString(PreferKey.progressBarBehavior) ?: "page"

    val keyPageOnLongPress
        get() = AppConfigStore.getBoolean(PreferKey.keyPageOnLongPress) ?: false

    val volumeKeyPage
        get() = AppConfigStore.getBoolean(PreferKey.volumeKeyPage) ?: true

    val volumeKeyPageOnPlay
        get() = AppConfigStore.getBoolean(PreferKey.volumeKeyPageOnPlay) ?: true

    val mouseWheelPage
        get() = AppConfigStore.getBoolean(PreferKey.mouseWheelPage) ?: true

    val paddingDisplayCutouts
        get() = AppConfigStore.getBoolean(PreferKey.paddingDisplayCutouts) ?: false

    var searchScope: String
        get() = AppConfigStore.getString(PreferKey.searchScope) ?: ""
        set(value) {
            AppConfigStore.putString(PreferKey.searchScope, value)
            appCtx.putPrefString(PreferKey.searchScope, value)
        }

    var searchGroup: String
        get() = AppConfigStore.getString(PreferKey.searchGroup) ?: ""
        set(value) {
            AppConfigStore.putString(PreferKey.searchGroup, value)
            appCtx.putPrefString(PreferKey.searchGroup, value)
        }

    var pageTouchSlop: Int
        get() = AppConfigStore.getInt(PreferKey.pageTouchSlop) ?: 0
        set(value) {
            AppConfigStore.putInt(PreferKey.pageTouchSlop, value)
            appCtx.putPrefInt(PreferKey.pageTouchSlop, value)
        }

    var bookshelfSort: Int
        get() = AppConfigStore.getInt(PreferKey.bookshelfSort) ?: 0
        set(value) {
            AppConfigStore.putInt(PreferKey.bookshelfSort, value)
            appCtx.putPrefInt(PreferKey.bookshelfSort, value)
        }

    fun getBookSortByGroupId(groupId: Long): Int {
        return appDb.bookGroupDao.getByID(groupId)?.getRealBookSort()
            ?: bookshelfSort
    }

    // ignorePrefKeys 成员：不进备份，纯 DS 读写，无 SP 镜像（Phase 2 批 3）
    var bitmapCacheSize: Int
        get() = AppConfigStore.getInt(PreferKey.bitmapCacheSize) ?: 50
        set(value) {
            AppConfigStore.putInt(PreferKey.bitmapCacheSize, value)
        }

    var imageRetainNum: Int
        get() = AppConfigStore.getInt(PreferKey.imageRetainNum) ?: 0
        set(value) {
            AppConfigStore.putInt(PreferKey.imageRetainNum, value)
            appCtx.putPrefInt(PreferKey.imageRetainNum, value)
        }

    var showReadTitleBarAddition: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.showReadTitleAddition) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.showReadTitleAddition, value)
            // SP 镜像：Backup 的 config.xml 来自 SP 全量，Phase 4 前必须保留
            appCtx.putPrefBoolean(PreferKey.showReadTitleAddition, value)
        }
    var readBarStyleFollowPage: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.readBarStyleFollowPage) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.readBarStyleFollowPage, value)
            // SP 镜像：Backup 的 config.xml 来自 SP 全量，Phase 4 前必须保留
            appCtx.putPrefBoolean(PreferKey.readBarStyleFollowPage, value)
        }

    var sourceEditMaxLine: Int
        get() {
            val maxLine = AppConfigStore.getInt(PreferKey.sourceEditMaxLine) ?: Int.MAX_VALUE
            if (maxLine < 10) {
                return Int.MAX_VALUE
            }
            return maxLine
        }
        set(value) {
            AppConfigStore.putInt(PreferKey.sourceEditMaxLine, value)
            appCtx.putPrefInt(PreferKey.sourceEditMaxLine, value)
        }

    // ignorePrefKeys 成员：不进备份，纯 DS 读写，无 SP 镜像（Phase 2 批 3）
    var audioPlayUseWakeLock: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.audioPlayWakeLock) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.audioPlayWakeLock, value)
        }

    var brightnessVwPos: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.brightnessVwPos) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.brightnessVwPos, value)
            appCtx.putPrefBoolean(PreferKey.brightnessVwPos, value)
        }

    fun detectClickArea() {
        if (clickActionTL * clickActionTC * clickActionTR
            * clickActionML * clickActionMC * clickActionMR
            * clickActionBL * clickActionBC * clickActionBR != 0
        ) {
            clickActionMC = 0
            appCtx.toastOnUi("当前没有配置菜单区域,自动恢复中间区域为菜单.")
        }
    }

    //跳转到漫画界面不使用富文本模式
    var showMangaUi: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.showMangaUi) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.showMangaUi, value)
            appCtx.putPrefBoolean(PreferKey.showMangaUi, value)
        }

    //禁用漫画内标题（漫画阅读器内容生成 ReadManga.getManageChapter 读取）
    var hideMangaTitle: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.hideMangaTitle) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.hideMangaTitle, value)
            appCtx.putPrefBoolean(PreferKey.hideMangaTitle, value)
        }

    var disableHorizontalPageSnap: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.disableHorizontalPageSnap) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.disableHorizontalPageSnap, value)
            appCtx.putPrefBoolean(PreferKey.disableHorizontalPageSnap, value)
        }

    var welcomeImage
        get() = AppConfigStore.getString(PreferKey.welcomeImage)
        set(value) {
            AppConfigStore.putString(PreferKey.welcomeImage, value)
            appCtx.putPrefString(PreferKey.welcomeImage, value)
        }

    var welcomeShowText: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.welcomeShowText) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.welcomeShowText, value)
            appCtx.putPrefBoolean(PreferKey.welcomeShowText, value)
        }

    var customWelcome: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.customWelcome) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.customWelcome, value)
            appCtx.putPrefBoolean(PreferKey.customWelcome, value)
        }

    var welcomeShowIcon: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.welcomeShowIcon) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.welcomeShowIcon, value)
            appCtx.putPrefBoolean(PreferKey.welcomeShowIcon, value)
        }

    var welcomeImageDark
        get() = AppConfigStore.getString(PreferKey.welcomeImageDark)
        set(value) {
            AppConfigStore.putString(PreferKey.welcomeImageDark, value)
            appCtx.putPrefString(PreferKey.welcomeImageDark, value)
        }

    var welcomeShowTextDark: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.welcomeShowTextDark) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.welcomeShowTextDark, value)
            appCtx.putPrefBoolean(PreferKey.welcomeShowTextDark, value)
        }

    var welcomeShowIconDark: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.welcomeShowIconDark) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.welcomeShowIconDark, value)
            appCtx.putPrefBoolean(PreferKey.welcomeShowIconDark, value)
        }

    /**
     * Writing mode: run background memory-table / outline maintain on interval.
     * Main-model tools (patch_history_memory / patch_outline) are unaffected.
     */
    var aiStructuredAutoMaintain: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.aiStructuredAutoMaintain) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.aiStructuredAutoMaintain, value)
            appCtx.putPrefBoolean(PreferKey.aiStructuredAutoMaintain, value)
        }

    /**
     * AI chat render track: [AI_CHAT_RENDER_COMPOSE] or [AI_CHAT_RENDER_HTML].
     * Same [io.legado.app.ui.ai.chat.AiChatViewModel]; UI shells diverge.
     */
    var aiChatRenderTrack: String
        get() = AppConfigStore.getString(PreferKey.aiChatRenderTrack)
            ?.takeIf { it == AI_CHAT_RENDER_HTML || it == AI_CHAT_RENDER_COMPOSE }
            ?: AI_CHAT_RENDER_COMPOSE
        set(value) {
            val normalized = when (value) {
                AI_CHAT_RENDER_HTML -> AI_CHAT_RENDER_HTML
                else -> AI_CHAT_RENDER_COMPOSE
            }
            AppConfigStore.putString(PreferKey.aiChatRenderTrack, normalized)
            appCtx.putPrefString(PreferKey.aiChatRenderTrack, normalized)
        }

    /** Active HTML chat theme pack id. Stored in prefs; packs live in assets/files. */
    var aiChatHtmlThemeId: String
        get() = AppConfigStore.getString(PreferKey.aiChatHtmlThemeId)
            ?.takeIf { it.isNotBlank() }
            ?: "default"
        set(value) {
            val normalized = value.trim().ifBlank { "default" }
            AppConfigStore.putString(PreferKey.aiChatHtmlThemeId, normalized)
            appCtx.putPrefString(PreferKey.aiChatHtmlThemeId, normalized)
        }

    /** AI 生成 publish_html_app 后是否自动打开播放器。 */
    var aiHtmlAppAutoLaunch: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.aiHtmlAppAutoLaunch) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.aiHtmlAppAutoLaunch, value)
            appCtx.putPrefBoolean(PreferKey.aiHtmlAppAutoLaunch, value)
        }

    var aiMaxToolRounds: Int
        get() = (AppConfigStore.getInt(PreferKey.aiMaxToolRounds) ?: 12).coerceIn(6, 24)
        set(value) {
            val normalized = value.coerceIn(6, 24)
            AppConfigStore.putInt(PreferKey.aiMaxToolRounds, normalized)
            appCtx.putPrefInt(PreferKey.aiMaxToolRounds, normalized)
        }

    var aiMaxToolOutputChars: Int
        get() = (AppConfigStore.getInt(PreferKey.aiMaxToolOutputChars) ?: 20_000)
            .coerceIn(4_000, 32_000)
        set(value) {
            val normalized = value.coerceIn(4_000, 32_000)
            AppConfigStore.putInt(PreferKey.aiMaxToolOutputChars, normalized)
            appCtx.putPrefInt(PreferKey.aiMaxToolOutputChars, normalized)
        }

    /**
     * Master switch for [io.legado.app.data.repository.CacheFirstAiTextGateway] —
     * caches idempotent prompt-invariant LLM responses (sub-model / compress / outline /
     * character) in the ai_artifacts table. Default OFF to match DSH's "no dialogue
     * response cache" restraint.
     */
    var aiResponseCacheEnabled: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.aiResponseCacheEnabled) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.aiResponseCacheEnabled, value)
            appCtx.putPrefBoolean(PreferKey.aiResponseCacheEnabled, value)
        }

    /**
     * Character threshold above which a tool result's full text is spilled to disk and
     * replaced in-context by a head/tail preview + locator (see ToolResultSpillStore).
     */
    var aiToolSpillThreshold: Int
        get() = (AppConfigStore.getInt(PreferKey.aiToolSpillThreshold) ?: 50_000)
            .coerceIn(20_000, 200_000)
        set(value) {
            val normalized = value.coerceIn(20_000, 200_000)
            AppConfigStore.putInt(PreferKey.aiToolSpillThreshold, normalized)
            appCtx.putPrefInt(PreferKey.aiToolSpillThreshold, normalized)
        }

    var aiAllowBookSourceFetch: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.aiAllowBookSourceFetch) ?: false
        set(value) {
            AppConfigStore.putBoolean(PreferKey.aiAllowBookSourceFetch, value)
            appCtx.putPrefBoolean(PreferKey.aiAllowBookSourceFetch, value)
        }

    /**
     * Optional proxy for Skill URL downloads (`install_skill_from_url` / import URL).
     * Format: `http://host:port`, `socks5://host:port`, optional auth `http://host:port@user@pass`.
     * Empty = direct connection.
     */
    var aiSkillDownloadProxy: String
        get() = AppConfigStore.getString(PreferKey.aiSkillDownloadProxy).orEmpty()
        set(value) {
            val normalized = value.trim()
            AppConfigStore.putString(PreferKey.aiSkillDownloadProxy, normalized)
            appCtx.putPrefString(PreferKey.aiSkillDownloadProxy, normalized)
        }

    /**
     * Optional proxy for all AI requests (chat / generate / models).
     * Format: `http://host:port`, `socks5://host:port`, optional auth `http://host:port@user@pass`.
     * Empty = direct connection.
     */
    var aiChatProxy: String
        get() = AppConfigStore.getString(PreferKey.aiChatProxy).orEmpty()
        set(value) {
            val normalized = value.trim()
            AppConfigStore.putString(PreferKey.aiChatProxy, normalized)
            appCtx.putPrefString(PreferKey.aiChatProxy, normalized)
        }

    /** Whether [aiChatProxy] is applied to AI requests. */
    var aiChatProxyEnabled: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.aiChatProxyEnabled) ?: true
        set(value) {
            AppConfigStore.putBoolean(PreferKey.aiChatProxyEnabled, value)
            appCtx.putPrefBoolean(PreferKey.aiChatProxyEnabled, value)
        }

    /** `"brave"` or `"tavily"` (default). Used when [aiWebSearchMode] is `api`. */
    var aiWebSearchProvider: String
        get() = AppConfigStore.getString(PreferKey.aiWebSearchProvider)
            ?.takeIf { it.isNotBlank() } ?: "tavily"
        set(value) {
            AppConfigStore.putString(PreferKey.aiWebSearchProvider, value)
            appCtx.putPrefString(PreferKey.aiWebSearchProvider, value)
        }

    var aiWebSearchApiKey: String
        get() = AppConfigStore.getString(PreferKey.aiWebSearchApiKey).orEmpty()
        set(value) {
            AppConfigStore.putString(PreferKey.aiWebSearchApiKey, value)
            appCtx.putPrefString(PreferKey.aiWebSearchApiKey, value)
        }

    /** `"api"` (Tavily/Brave) or `"page"` (BackstageWebView + URL template). Default `api`. */
    var aiWebSearchMode: String
        get() = AppConfigStore.getString(PreferKey.aiWebSearchMode)?.takeIf {
            it == "page" || it == "api"
        } ?: "api"
        set(value) {
            val normalized = if (value == "page") "page" else "api"
            AppConfigStore.putString(PreferKey.aiWebSearchMode, normalized)
            appCtx.putPrefString(PreferKey.aiWebSearchMode, normalized)
        }

    /**
     * Native (provider built-in) web_search tuning for Responses-protocol models.
     * All defaults keep the outgoing tool object at the bare `{"type":"web_search"}`.
     */
    var aiNativeSearchMaxUses: Int
        get() = AppConfigStore.getInt(PreferKey.aiNativeSearchMaxUses) ?: 0
        set(value) {
            AppConfigStore.putInt(PreferKey.aiNativeSearchMaxUses, value)
            appCtx.putPrefInt(PreferKey.aiNativeSearchMaxUses, value)
        }

    /** `low` / `medium` / `high`; blank = provider default (`search_context_size`). */
    var aiNativeSearchContextSize: String
        get() = AppConfigStore.getString(PreferKey.aiNativeSearchContextSize).orEmpty()
        set(value) {
            val normalized = value.trim()
            AppConfigStore.putString(PreferKey.aiNativeSearchContextSize, normalized)
            appCtx.putPrefString(PreferKey.aiNativeSearchContextSize, normalized)
        }

    /** Comma-separated domain allowlist sent as `filters.allowed_domains`; blank = no filter. */
    var aiNativeSearchAllowedDomains: String
        get() = AppConfigStore.getString(PreferKey.aiNativeSearchAllowedDomains).orEmpty()
        set(value) {
            val normalized = value.trim()
            AppConfigStore.putString(PreferKey.aiNativeSearchAllowedDomains, normalized)
            appCtx.putPrefString(PreferKey.aiNativeSearchAllowedDomains, normalized)
        }

    /** Public search-page URL template; must contain `{{key}}` or `{{query}}`. */
    var aiWebSearchPageUrlTemplate: String
        get() = WebSearchPageProfiles.getActive().pageUrlTemplate
        set(value) {
            WebSearchPageProfiles.updateActive {
                it.copy(pageUrlTemplate = value)
            }
        }

    /** BackstageWebView delay before reading HTML (ms). */
    var aiWebSearchPageDelayMs: Int
        get() = WebSearchPageProfiles.getActive().delayMs
        set(value) {
            WebSearchPageProfiles.updateActive {
                it.copy(delayMs = value)
            }
        }

    var aiWebSearchPageResultSelector: String
        get() = WebSearchPageProfiles.getActive().resultSelector
        set(value) {
            WebSearchPageProfiles.updateActive {
                it.copy(resultSelector = value)
            }
        }

    var aiWebSearchPageTitleSelector: String
        get() = WebSearchPageProfiles.getActive().titleSelector
        set(value) {
            WebSearchPageProfiles.updateActive {
                it.copy(titleSelector = value)
            }
        }

    var aiWebSearchPageSnippetSelector: String
        get() = WebSearchPageProfiles.getActive().snippetSelector
        set(value) {
            WebSearchPageProfiles.updateActive {
                it.copy(snippetSelector = value)
            }
        }

    /** Query param for search-engine redirect unwrap (e.g. `uddg`). Blank = no unwrap. */
    var aiWebSearchPageRedirectParam: String
        get() = WebSearchPageProfiles.getActive().redirectParam
        set(value) {
            WebSearchPageProfiles.updateActive {
                it.copy(redirectParam = value)
            }
        }

    /** Comma-separated host substrings excluded after redirect unwrap. */
    var aiWebSearchPageExcludeHosts: String
        get() = WebSearchPageProfiles.getActive().excludeHosts
        set(value) {
            WebSearchPageProfiles.updateActive {
                it.copy(excludeHosts = value)
            }
        }

    /** Brave API origin, default `https://api.search.brave.com`. */
    var aiWebSearchBraveBaseUrl: String
        get() = AppConfigStore.getString(PreferKey.aiWebSearchBraveBaseUrl)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_BRAVE_BASE_URL
        set(value) {
            val trimmed = value.trim().trimEnd('/').ifBlank { DEFAULT_BRAVE_BASE_URL }
            AppConfigStore.putString(PreferKey.aiWebSearchBraveBaseUrl, trimmed)
            appCtx.putPrefString(PreferKey.aiWebSearchBraveBaseUrl, trimmed)
        }

    /** Tavily API origin, default `https://api.tavily.com`. */
    var aiWebSearchTavilyBaseUrl: String
        get() = AppConfigStore.getString(PreferKey.aiWebSearchTavilyBaseUrl)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_TAVILY_BASE_URL
        set(value) {
            val trimmed = value.trim().trimEnd('/').ifBlank { DEFAULT_TAVILY_BASE_URL }
            AppConfigStore.putString(PreferKey.aiWebSearchTavilyBaseUrl, trimmed)
            appCtx.putPrefString(PreferKey.aiWebSearchTavilyBaseUrl, trimmed)
        }

    val aiWebSearchConfigured: Boolean
        get() = when (aiWebSearchMode) {
            "page" -> {
                val t = aiWebSearchPageUrlTemplate
                t.contains("{{key}}") || t.contains("{{query}}")
            }
            else -> aiWebSearchApiKey.isNotBlank()
        }

    /** web_search calls allowed per conversation (default 10). */
    var aiWebSearchConvLimit: Int
        get() = (AppConfigStore.getInt(PreferKey.aiWebSearchConvLimit) ?: 10).coerceIn(1, 100)
        set(value) {
            val normalized = value.coerceIn(1, 100)
            AppConfigStore.putInt(PreferKey.aiWebSearchConvLimit, normalized)
            appCtx.putPrefInt(PreferKey.aiWebSearchConvLimit, normalized)
        }

    /** read_web_page calls allowed per conversation (default 20). */
    var aiWebReadConvLimit: Int
        get() = (AppConfigStore.getInt(PreferKey.aiWebReadConvLimit) ?: 20).coerceIn(1, 100)
        set(value) {
            val normalized = value.coerceIn(1, 100)
            AppConfigStore.putInt(PreferKey.aiWebReadConvLimit, normalized)
            appCtx.putPrefInt(PreferKey.aiWebReadConvLimit, normalized)
        }

    var aiWebSearchWeekLimit: Int
        get() = (AppConfigStore.getInt(PreferKey.aiWebSearchWeekLimit) ?: 249)
            .coerceIn(1, 10_000)
        set(value) {
            val normalized = value.coerceIn(1, 10_000)
            AppConfigStore.putInt(PreferKey.aiWebSearchWeekLimit, normalized)
            appCtx.putPrefInt(PreferKey.aiWebSearchWeekLimit, normalized)
        }

    var aiWebSearchMonthLimit: Int
        get() = (AppConfigStore.getInt(PreferKey.aiWebSearchMonthLimit) ?: 996)
            .coerceIn(1, 50_000)
        set(value) {
            val normalized = value.coerceIn(1, 50_000)
            AppConfigStore.putInt(PreferKey.aiWebSearchMonthLimit, normalized)
            appCtx.putPrefInt(PreferKey.aiWebSearchMonthLimit, normalized)
        }

    var aiWebSearchTestDayLimit: Int
        get() = (AppConfigStore.getInt(PreferKey.aiWebSearchTestDayLimit) ?: 5).coerceIn(1, 50)
        set(value) {
            val normalized = value.coerceIn(1, 50)
            AppConfigStore.putInt(PreferKey.aiWebSearchTestDayLimit, normalized)
            appCtx.putPrefInt(PreferKey.aiWebSearchTestDayLimit, normalized)
        }

    const val DEFAULT_BRAVE_BASE_URL = "https://api.search.brave.com"
    const val DEFAULT_TAVILY_BASE_URL = "https://api.tavily.com"
    const val DEFAULT_PAGE_URL_TEMPLATE = "https://html.duckduckgo.com/html/?q={{key}}"
    const val DEFAULT_PAGE_DELAY_MS = 1000
    const val DEFAULT_PAGE_RESULT_SELECTOR = "#links .result, .results .result, .result"
    const val DEFAULT_PAGE_TITLE_SELECTOR =
        "a.result__a, a.result-link, a[href^=http], a[href^=//]"
    const val DEFAULT_PAGE_SNIPPET_SELECTOR = ".result__snippet, .result-snippet"
    const val DEFAULT_PAGE_REDIRECT_PARAM = "uddg"
    const val DEFAULT_PAGE_EXCLUDE_HOSTS = "duckduckgo.com,google.com"
}

