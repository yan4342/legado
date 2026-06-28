package io.legado.app.ui.main

import android.app.Activity
import android.content.Intent
import androidx.navigation3.runtime.NavKey

object MainNavigator {

    private var backNavigationInProgress = false

    fun navigateToRoute(backStack: MutableList<NavKey>, route: NavKey) {
        val currentRoute = backStack.lastOrNull()
        if (currentRoute == route) return

        when (route) {
            MainRouteHome -> {
                backStack.clear()
                backStack.add(MainRouteHome)
            }

            MainRouteSettings -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(MainRouteSettings)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(MainRouteSettings)
                }
            }

            MainRouteSettingsOther,
            MainRouteSettingsRead,
            MainRouteSettingsCover,
            MainRouteSettingsTheme,
            MainRouteSettingsBackup,
            MainRouteSettingsCustomTheme,
            MainRouteSettingsThemeManage,
            MainRouteSettingsDownloadCache,
            MainRouteSettingsTranslation -> {
                backStack.clear()
                backStack.add(MainRouteHome)
                backStack.add(MainRouteSettings)
                backStack.add(route)
            }

            MainRouteImportLocal,
            MainRouteImportRemote,
            is MainRouteCache,
            MainRouteBookCacheManage,
            is MainRouteReadBook -> {
                if (
                    currentRoute == MainRouteHome ||
                    currentRoute is MainRouteBookInfo
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteSearchContent -> {
                backStack.add(route)
            }

            is MainRouteSearch -> {
                if (
                    currentRoute == MainRouteHome ||
                    currentRoute is MainRouteBookInfo ||
                    currentRoute is MainRouteExploreShow ||
                    currentRoute is MainRouteReadRecord ||
                    currentRoute is MainRouteReadRecordOverview ||
                    currentRoute is MainRouteSearch
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteBookInfo -> {
                if (
                    currentRoute == MainRouteHome ||
                    currentRoute is MainRouteSearch ||
                    currentRoute is MainRouteExploreShow ||
                    currentRoute is MainRouteBookInfo
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteExploreShow -> {
                if (
                    currentRoute == MainRouteHome ||
                    currentRoute is MainRouteBookInfo ||
                    currentRoute is MainRouteSearch ||
                    currentRoute is MainRouteExploreShow
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            MainRouteRssFavorites,
            MainRouteRuleSub -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            MainRouteReadRecord -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            MainRouteAbout -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            MainRouteReadRecordOverview -> {
                if (currentRoute == MainRouteHome || currentRoute == MainRouteReadRecord) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            MainRouteAiDictRule,
            MainRouteOtherConfig,
            MainRouteBackupConfig,
            MainRouteThemeConfig -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            MainRouteWelcomeConfig,
            MainRouteCoverConfig -> {
                if (currentRoute == MainRouteHome || currentRoute == MainRouteThemeConfig) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            // RSS Sort/Read routes not yet ported from MD3 — skip for now

            else -> {
                // Default: clear and push route on top of home
                if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }
        }
    }

    fun navigateBack(activity: Activity, backStack: MutableList<NavKey>) {
        if (backNavigationInProgress) {
            return
        }
        backNavigationInProgress = true
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        } else {
            activity.finish()
        }
    }

    fun onBackStackChanged() {
        backNavigationInProgress = false
    }

    fun resolveStartRoute(intent: Intent?): NavKey {
        val route = intent?.getStringExtra(MainIntent.EXTRA_START_ROUTE)
        return resolveStartRoute(route, intent)
    }

    private fun resolveStartRoute(route: String?, intent: Intent?): MainRoute {
        return when (route) {
            MainRouteConst.ROUTE_MAIN -> MainRouteHome
            MainRouteConst.ROUTE_SETTINGS -> MainRouteSettings
            MainRouteConst.ROUTE_SETTINGS_OTHER -> MainRouteSettingsOther
            MainRouteConst.ROUTE_SETTINGS_READ -> MainRouteSettingsRead
            MainRouteConst.ROUTE_SETTINGS_COVER -> MainRouteSettingsCover
            MainRouteConst.ROUTE_SETTINGS_THEME -> MainRouteSettingsTheme
            MainRouteConst.ROUTE_SETTINGS_BACKUP -> MainRouteSettingsBackup
            MainRouteConst.ROUTE_SETTINGS_CUSTOM_THEME -> MainRouteSettingsCustomTheme
            MainRouteConst.ROUTE_SETTINGS_DOWNLOAD_CACHE -> MainRouteSettingsDownloadCache
            MainRouteConst.ROUTE_SETTINGS_TRANSLATION -> MainRouteSettingsTranslation
            MainRouteConst.ROUTE_IMPORT_LOCAL -> MainRouteImportLocal
            MainRouteConst.ROUTE_IMPORT_REMOTE -> MainRouteImportRemote
            MainRouteConst.ROUTE_CACHE -> MainRouteCache(
                intent?.getLongExtra(
                    MainIntent.EXTRA_CACHE_GROUP_ID,
                    -1L
                ) ?: -1L
            )

            MainRouteConst.ROUTE_BOOK_CACHE_MANAGE -> MainRouteBookCacheManage
            MainRouteConst.ROUTE_READ_BOOK -> MainRouteReadBook(
                bookUrl = intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL),
                readAloud = intent?.getBooleanExtra(MainIntent.EXTRA_READ_ALOUD, false) == true,
                inBookshelf = intent?.getBooleanExtra(MainIntent.EXTRA_IN_BOOKSHELF, true) != false,
                chapterChanged = intent?.getBooleanExtra(
                    MainIntent.EXTRA_CHAPTER_CHANGED,
                    false
                ) == true,
            )
            MainRouteConst.ROUTE_SEARCH -> MainRouteSearch(
                key = intent?.getStringExtra(MainIntent.EXTRA_SEARCH_KEY),
                scopeRaw = intent?.getStringExtra(MainIntent.EXTRA_SEARCH_SCOPE)
            )

            MainRouteConst.ROUTE_BOOK_INFO -> intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { bookUrl ->
                    MainRouteBookInfo(
                        name = intent.getStringExtra(MainIntent.EXTRA_BOOK_NAME),
                        author = intent.getStringExtra(MainIntent.EXTRA_BOOK_AUTHOR),
                        bookUrl = bookUrl,
                        origin = intent.getStringExtra(MainIntent.EXTRA_BOOK_ORIGIN),
                        coverPath = intent.getStringExtra(MainIntent.EXTRA_BOOK_COVER)
                    )
                } ?: MainRouteHome

            MainRouteConst.ROUTE_EXPLORE_SHOW -> intent?.getStringExtra(MainIntent.EXTRA_SOURCE_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { sourceUrl ->
                    MainRouteExploreShow(
                        title = intent.getStringExtra(MainIntent.EXTRA_EXPLORE_NAME),
                        sourceUrl = sourceUrl,
                        exploreUrl = intent.getStringExtra(MainIntent.EXTRA_EXPLORE_URL),
                    )
                } ?: MainRouteHome

            MainRouteConst.ROUTE_ABOUT -> MainRouteAbout

            else -> MainRouteHome
        }
    }
}
