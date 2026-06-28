package io.legado.app.ui.main

import android.content.Context
import android.content.Intent
import io.legado.app.ui.config.ConfigTag

object MainIntent {
    const val EXTRA_START_ROUTE = "startRoute"
    const val EXTRA_CACHE_GROUP_ID = "extra_cache_group_id"
    const val EXTRA_SEARCH_KEY = "extra_search_key"
    const val EXTRA_SEARCH_SCOPE = "extra_search_scope"
    const val EXTRA_BOOK_NAME = "name"
    const val EXTRA_BOOK_AUTHOR = "author"
    const val EXTRA_BOOK_URL = "bookUrl"
    const val EXTRA_BOOK_ORIGIN = "origin"
    const val EXTRA_BOOK_COVER = "coverPath"
    const val EXTRA_READ_ALOUD = "readAloud"
    const val EXTRA_IN_BOOKSHELF = "inBookshelf"
    const val EXTRA_CHAPTER_CHANGED = "chapterChanged"
    const val EXTRA_EXPLORE_NAME = "exploreName"
    const val EXTRA_SOURCE_URL = "sourceUrl"
    const val EXTRA_EXPLORE_URL = "exploreUrl"

    fun createLauncherIntent(context: Context): Intent {
        val launcherComponent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
        return if (launcherComponent != null) {
            Intent().setComponent(launcherComponent)
        } else {
            Intent(context, MainActivity::class.java)
        }
    }

    fun createHomeIntent(context: Context): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_MAIN)
        }
    }

    fun createIntent(context: Context, configTag: String? = null): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, routeForConfigTag(configTag))
        }
    }

    fun createBookshelfManageScreenIntent(
        context: Context,
        groupId: Long = -1L
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_CACHE)
            putExtra(EXTRA_CACHE_GROUP_ID, groupId)
        }
    }

    fun createCacheIntent(
        context: Context,
        groupId: Long = -1L
    ): Intent = createBookshelfManageScreenIntent(context, groupId)

    fun createBookCacheManageIntent(context: Context): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_BOOK_CACHE_MANAGE)
        }
    }

    fun createReadBookIntent(
        context: Context,
        bookUrl: String? = null,
        readAloud: Boolean = false,
        inBookshelf: Boolean = true,
        chapterChanged: Boolean = false,
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_READ_BOOK)
            bookUrl?.let { putExtra(EXTRA_BOOK_URL, it) }
            putExtra(EXTRA_READ_ALOUD, readAloud)
            putExtra(EXTRA_IN_BOOKSHELF, inBookshelf)
            putExtra(EXTRA_CHAPTER_CHANGED, chapterChanged)
        }
    }

    fun createSearchIntent(
        context: Context,
        key: String? = null,
        scopeRaw: String? = null
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_SEARCH)
            putExtra(EXTRA_SEARCH_KEY, key)
            scopeRaw?.takeIf { it.isNotBlank() }?.let {
                putExtra(EXTRA_SEARCH_SCOPE, it)
            }
        }
    }

    fun createBookInfoIntent(
        context: Context,
        name: String? = null,
        author: String? = null,
        bookUrl: String,
        origin: String? = null,
        coverPath: String? = null
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_BOOK_INFO)
            putExtra(EXTRA_BOOK_NAME, name)
            putExtra(EXTRA_BOOK_AUTHOR, author)
            putExtra(EXTRA_BOOK_URL, bookUrl)
            putExtra(EXTRA_BOOK_ORIGIN, origin)
            putExtra(EXTRA_BOOK_COVER, coverPath)
        }
    }

    fun createExploreShowIntent(
        context: Context,
        exploreName: String? = null,
        sourceUrl: String,
        exploreUrl: String? = null,
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_EXPLORE_SHOW)
            putExtra(EXTRA_EXPLORE_NAME, exploreName)
            putExtra(EXTRA_SOURCE_URL, sourceUrl)
            putExtra(EXTRA_EXPLORE_URL, exploreUrl)
        }
    }

    private fun routeForConfigTag(configTag: String?): String {
        return when (configTag) {
            ConfigTag.OTHER_CONFIG -> MainRouteConst.ROUTE_SETTINGS_OTHER
            ConfigTag.COVER_CONFIG -> MainRouteConst.ROUTE_SETTINGS_COVER
            ConfigTag.THEME_CONFIG -> MainRouteConst.ROUTE_SETTINGS_THEME
            ConfigTag.BACKUP_CONFIG -> MainRouteConst.ROUTE_SETTINGS_BACKUP
            else -> MainRouteConst.ROUTE_SETTINGS
        }
    }
}
