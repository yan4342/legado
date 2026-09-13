package io.legado.app.help.storage

import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import splitties.init.appCtx

/**
 * 备份配置
 */
@Suppress("ConstPropertyName")
object BackupConfig {

    private val ignoreConfigPath = FileUtils.getPath(appCtx.filesDir, "restoreIgnore.json")
    val ignoreConfig: HashMap<String, Boolean> by lazy {
        val file = FileUtils.createFileIfNotExist(ignoreConfigPath)
        val json = file.readText()
        GSON.fromJsonObject<HashMap<String, Boolean>>(json).getOrNull() ?: hashMapOf()
    }

    private const val readConfigKey = "readConfig"
    private const val themeConfigKey = "themeConfig"
    private const val coverConfigKey = "coverConfig"
    private const val localBookKey = "localBook"
    private const val aiConfigKey = "aiConfig"
    private const val aiDataKey = "aiData"

    //配置忽略key
    val ignoreKeys = arrayOf(
        readConfigKey,
        PreferKey.themeMode,
        themeConfigKey,
        coverConfigKey,
        PreferKey.bookshelfLayout,
        PreferKey.showRss,
        PreferKey.threadCount,
        localBookKey,
        aiConfigKey,
        aiDataKey,
    )

    //配置忽略标题
    val ignoreTitle = arrayOf(
        appCtx.getString(R.string.read_config),
        appCtx.getString(R.string.theme_mode),
        appCtx.getString(R.string.theme_config),
        appCtx.getString(R.string.cover_config),
        appCtx.getString(R.string.bookshelf_layout),
        appCtx.getString(R.string.show_rss),
        appCtx.getString(R.string.thread_count),
        appCtx.getString(R.string.local_book),
        appCtx.getString(R.string.ai_config),
        appCtx.getString(R.string.ai_data),
    )

    //自动忽略keys
    private val ignorePrefKeys = arrayOf(
        PreferKey.defaultCover,
        PreferKey.defaultCoverDark,
        PreferKey.backupPath,
        PreferKey.defaultBookTreeUri,
        PreferKey.webDavDeviceName,
        PreferKey.launcherIcon,
        PreferKey.bitmapCacheSize,
        PreferKey.webServiceWakeLock,
        PreferKey.readAloudWakeLock,
        PreferKey.audioPlayWakeLock
    )

    //阅读配置
    private val readPrefKeys = arrayOf(
        PreferKey.readStyleSelect,
        PreferKey.comicStyleSelect,
        PreferKey.shareLayout,
        PreferKey.hideStatusBar,
        PreferKey.hideNavigationBar,
        PreferKey.autoReadSpeed,
        PreferKey.clickActionTL,
        PreferKey.clickActionTC,
        PreferKey.clickActionTR,
        PreferKey.clickActionML,
        PreferKey.clickActionMC,
        PreferKey.clickActionMR,
        PreferKey.clickActionBL,
        PreferKey.clickActionBC,
        PreferKey.clickActionBR
    )

    private val themePrefKeys = arrayOf(
        PreferKey.cPrimary,
        PreferKey.cAccent,
        PreferKey.cBackground,
        PreferKey.cBBackground,
        PreferKey.cCardBg,
        PreferKey.cPopupBg,
        PreferKey.cTextAccent,
        PreferKey.bgImage,
        PreferKey.bgImageBlurring,
        PreferKey.cNPrimary,
        PreferKey.cNAccent,
        PreferKey.cNBackground,
        PreferKey.cNBBackground,
        PreferKey.cNCardBg,
        PreferKey.cNPopupBg,
        PreferKey.cNTextAccent,
        PreferKey.bgImageN,
        PreferKey.bgImageNBlurring
    )

    private val coverPrefKeys = arrayOf(
        PreferKey.useDefaultCover,
        PreferKey.loadCoverOnlyWifi,
        PreferKey.coverShowName,
        PreferKey.coverShowAuthor,
        PreferKey.coverShowNameN,
        PreferKey.coverShowAuthorN
    )

    private val aiPrefKeys = arrayOf(
        PreferKey.confirmToolsBeforeExecute,
        PreferKey.aiMaxToolRounds,
        PreferKey.aiMaxToolOutputChars,
        PreferKey.aiWebSearchProvider,
        PreferKey.aiWebSearchApiKey,
        PreferKey.aiWebSearchMode,
        PreferKey.aiWebSearchPageUrlTemplate,
        PreferKey.aiWebSearchPageDelayMs,
        PreferKey.aiWebSearchPageResultSelector,
        PreferKey.aiWebSearchPageTitleSelector,
        PreferKey.aiWebSearchPageSnippetSelector,
        PreferKey.aiWebSearchPageRedirectParam,
        PreferKey.aiWebSearchPageExcludeHosts,
        PreferKey.aiWebSearchPageProfiles,
        PreferKey.aiWebSearchPageActiveProfileId,
        PreferKey.aiWebSearchBraveBaseUrl,
        PreferKey.aiWebSearchTavilyBaseUrl,
        PreferKey.aiWebSearchConvLimit,
        PreferKey.aiWebReadConvLimit,
        PreferKey.aiWebSearchWeekLimit,
        PreferKey.aiWebSearchMonthLimit,
        PreferKey.aiWebSearchTestDayLimit,
        PreferKey.aiNativeSearchMaxUses,
        PreferKey.aiNativeSearchContextSize,
        PreferKey.aiNativeSearchAllowedDomains,
        PreferKey.interCharacterChatEnabled,
        PreferKey.dialogueHighlightEnabled,
        PreferKey.lastConversationType,
        PreferKey.aiChatUserBubbleBg,
        PreferKey.aiChatUserBubbleText,
        PreferKey.aiChatUserDialogue,
        PreferKey.aiChatToolSearch,
        PreferKey.aiChatToolRead,
        PreferKey.aiChatToolExtract,
        PreferKey.aiChatToolMemory,
        PreferKey.aiChatToolMutation,
        PreferKey.aiChatToolDestructive,
        PreferKey.aiChatSpeakerColor0,
        PreferKey.aiChatSpeakerColor1,
        PreferKey.aiChatSpeakerColor2,
        PreferKey.aiChatSpeakerColor3,
        PreferKey.aiChatSpeakerColor4,
        PreferKey.aiChatSpeakerColor5,
        PreferKey.aiChatSpeakerColor6,
        PreferKey.aiChatSpeakerColor7,
    )

    val aiDataFileNames = arrayOf(
        "aiChatConversation.json",
        "aiChatMessage.json",
        "aiCharacterCard.json",
        "aiWritingPrompt.json",
        "aiProviderProfile.json",
        "aiModelProfile.json",
        "aiTaskPreset.json",
        "aiArtifact.json",
        "aiMemory.json",
        "aiWorldBook.json",
        "aiMemoryTable.json",
        "aiMemoryTableRow.json",
        "aiOutline.json",
        "aiPromptTemplate.json",
        "aiToolConfig.json",
        "aiSkill.json",
        "aiUsageRecord.json",
        "aiWorkspace.json",
        "aiStructuredDataSnapshot.json",
        "aiPromptPipelinePreset.json",
        "aiWorldBookEntry.json",
        "aiDictRule.json",
        "bookSourceVersion.json",
        "bookCharacterCast.json",
        "readAloudVoices.json",
        "bookVoiceBindings.json",
        "chapterSpeechAnalysis.json",
        "chapterSpeechSegments.json",
        "cloudTtsEngines.json",
    )

    fun keyIsNotIgnore(key: String): Boolean {
        return when {
            ignorePrefKeys.contains(key) -> false
            ignoreReadConfig && readPrefKeys.contains(key) -> false
            ignoreThemeConfig && themePrefKeys.contains(key) -> false
            ignoreCoverConfig && coverPrefKeys.contains(key) -> false
            ignoreAiConfig && aiPrefKeys.contains(key) -> false
            PreferKey.themeMode == key && ignoreThemeMode -> false
            PreferKey.bookshelfLayout == key && ignoreBookshelfLayout -> false
            PreferKey.showRss == key && ignoreShowRss -> false
            PreferKey.threadCount == key && ignoreThreadCount -> false
            else -> true
        }
    }

    fun aiDataFileShouldRestore(fileName: String): Boolean =
        !ignoreAiData || fileName !in aiDataFileNames

    val ignoreReadConfig: Boolean
        get() = ignoreConfig[readConfigKey] == true
    private val ignoreThemeMode: Boolean
        get() = ignoreConfig[PreferKey.themeMode] == true
    private val ignoreThemeConfig: Boolean
        get() = ignoreConfig[themeConfigKey] == true
    private val ignoreCoverConfig: Boolean
        get() = ignoreConfig[coverConfigKey] == true
    private val ignoreBookshelfLayout: Boolean
        get() = ignoreConfig[PreferKey.bookshelfLayout] == true
    private val ignoreShowRss: Boolean
        get() = ignoreConfig[PreferKey.showRss] == true
    private val ignoreThreadCount: Boolean
        get() = ignoreConfig[PreferKey.threadCount] == true
    val ignoreLocalBook: Boolean
        get() = ignoreConfig[localBookKey] == true
    val ignoreAiConfig: Boolean
        get() = ignoreConfig[aiConfigKey] == true
    val ignoreAiData: Boolean
        get() = ignoreConfig[aiDataKey] == true

    fun saveIgnoreConfig() {
        val json = GSON.toJson(ignoreConfig)
        FileUtils.createFileIfNotExist(ignoreConfigPath).writeText(json)
    }

}