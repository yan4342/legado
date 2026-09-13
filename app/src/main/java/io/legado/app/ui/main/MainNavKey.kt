package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface MainRoute : NavKey

@Serializable
data object MainRouteEmpty : MainRoute

@Serializable
data object MainRouteHome : MainRoute

@Serializable
data object MainRouteBookshelf : MainRoute

@Serializable
data object MainRouteSettings : MainRoute

@Serializable
data object MainRouteSettingsOther : MainRoute

@Serializable
data object MainRouteSettingsRead : MainRoute

@Serializable
data object MainRouteSettingsCover : MainRoute

@Serializable
data object MainRouteSettingsTheme : MainRoute

@Serializable
data object MainRouteSettingsBackup : MainRoute

@Serializable
data object MainRouteSettingsDownloadCache : MainRoute

@Serializable
data object MainRouteSettingsTranslation : MainRoute

@Serializable
data object MainRouteSettingsLabConfig : MainRoute

@Serializable
data object MainRouteSettingsCustomTheme : MainRoute

@Serializable
data object MainRouteSettingsThemeManage : MainRoute

@Serializable
data object MainRouteImportLocal : MainRoute

@Serializable
data object MainRouteImportRemote : MainRoute

@Serializable
data class MainRouteCache(val groupId: Long) : MainRoute

@Serializable
data object MainRouteBookCacheManage : MainRoute

@Serializable
data object MainRouteReadAloudPlayer : MainRoute

@Serializable
data class MainRouteBookVoiceCasting(val bookUrl: String) : MainRoute

@Serializable
data class MainRouteBookCharacterNetwork(
    val bookUrl: String,
    val focusCharacterId: String? = null,
) : MainRoute

@Serializable
data class MainRouteBookCharacterList(val bookUrl: String) : MainRoute

@Serializable
data class MainRouteCloudTtsEngines(val bookUrl: String? = null) : MainRoute

@Serializable
data object MainRouteTtsCache : MainRoute

@Serializable
data class MainRouteReadBook(
    val bookUrl: String? = null,
    val readAloud: Boolean = false,
    val inBookshelf: Boolean = true,
    val chapterChanged: Boolean = false,
) : MainRoute

@Serializable
data class MainRouteSearch(
    val key: String?,
    val scopeRaw: String? = null,
) : MainRoute

@Serializable
data class MainRouteSearchContent(
    val bookUrl: String,
    val searchWord: String? = null,
    val searchResultIndex: Int = 0,
) : MainRoute

@Serializable
data class MainRouteBookInfo(
    val name: String?,
    val author: String?,
    val bookUrl: String,
    val origin: String? = null,
    val coverPath: String? = null,
    val sharedCoverKey: String? = null,
) : MainRoute

@Serializable
data class MainRouteExploreShow(
    val title: String?,
    val sourceUrl: String,
    val exploreUrl: String?,
) : MainRoute

@Serializable
data object MainRouteRssFavorites : MainRoute

@Serializable
data object MainRouteRuleSub : MainRoute

// "My" page routes
@Serializable
data object MainRouteMy : MainRoute

@Serializable
data object MainRouteAllBookmark : MainRoute

@Serializable
data object MainRouteFileManage : MainRoute

@Serializable
data object MainRouteDictRule : MainRoute

@Serializable
data object MainRouteReadRecord : MainRoute

@Serializable
data object MainRouteReadRecordOverview : MainRoute

@Serializable
data object MainRouteAiUsageOverview : MainRoute

@Serializable
data object MainRouteAiDictRule : MainRoute

// AI routes
@Serializable
data object MainRouteAiChat : MainRoute

@Serializable
data object MainRouteSettingsAi : MainRoute

@Serializable
data class MainRouteSettingsAiProfileEdit(val providerId: String? = null) : MainRoute

@Serializable
data class MainRouteSettingsAiModelEdit(val providerId: String, val modelProfileId: String? = null) : MainRoute

@Serializable
data object MainRouteSettingsAiAbilityManagement : MainRoute

@Serializable
data object MainRouteSettingsAiSkills : MainRoute

/** skillId blank = create; otherwise open SKILL.md for edit. */
@Serializable
data class MainRouteSettingsAiSkillEdit(
    val skillId: String = "",
) : MainRoute

@Serializable
data object MainRouteSettingsAiWebSearch : MainRoute

@Serializable
data object MainRouteSettingsAiPromptTemplates : MainRoute

@Serializable
data object MainRouteSettingsAiPromptPipeline : MainRoute

@Serializable
data object MainRouteSettingsAiChatColors : MainRoute

@Serializable
data object MainRouteSettingsAiHtmlThemes : MainRoute

@Serializable
data object MainRouteAbout : MainRoute

@Serializable
data object MainRouteOtherConfig : MainRoute

@Serializable
data object MainRouteThemeConfig : MainRoute

@Serializable
data object MainRouteWelcomeConfig : MainRoute

@Serializable
data object MainRouteCoverConfig : MainRoute

@Serializable
data object MainRouteBackupConfig : MainRoute

object MainRouteConst {
    const val ROUTE_MAIN = "main"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_SETTINGS_OTHER = "settings/other"
    const val ROUTE_SETTINGS_READ = "settings/read"
    const val ROUTE_SETTINGS_COVER = "settings/cover"
    const val ROUTE_SETTINGS_THEME = "settings/theme"
    const val ROUTE_SETTINGS_BACKUP = "settings/backup"
    const val ROUTE_SETTINGS_CUSTOM_THEME = "settings/custom_theme"
    const val ROUTE_SETTINGS_LAB_CONFIG = "settings/lab_config"
    const val ROUTE_SETTINGS_DOWNLOAD_CACHE = "settings/download_cache"
    const val ROUTE_SETTINGS_TRANSLATION = "settings/translation"
    const val ROUTE_IMPORT_LOCAL = "import/local"
    const val ROUTE_IMPORT_REMOTE = "import/remote"
    const val ROUTE_CACHE = "cache"
    const val ROUTE_BOOK_CACHE_MANAGE = "book/cache/manage"
    const val ROUTE_BOOK_VOICE_CASTING = "book/voice_casting"
    const val ROUTE_BOOK_CHARACTER_NETWORK = "book/character_network"
    const val ROUTE_BOOK_CHARACTER_LIST = "book/character_list"
    const val ROUTE_CLOUD_TTS = "book/cloud_tts"
    const val ROUTE_READ_ALOUD_PLAYER = "book/read_aloud_player"
    const val ROUTE_TTS_CACHE = "book/tts_cache"
    const val ROUTE_READ_BOOK = "book/read"
    const val ROUTE_SEARCH = "search"
    const val ROUTE_SEARCH_CONTENT = "book/searchContent"
    const val ROUTE_BOOK_INFO = "book/info"
    const val ROUTE_EXPLORE_SHOW = "explore/show"
    const val ROUTE_RSS_FAVORITES = "rss/favorites"
    const val ROUTE_RULE_SUB = "rss/rule_sub"
    const val ROUTE_READ_RECORD = "read_record"
    const val ROUTE_READ_RECORD_OVERVIEW = "read_record_overview"
    const val ROUTE_ABOUT = "about"
    const val ROUTE_AI_CHAT = "ai/chat"
    const val ROUTE_SETTINGS_AI = "settings/ai"
}
