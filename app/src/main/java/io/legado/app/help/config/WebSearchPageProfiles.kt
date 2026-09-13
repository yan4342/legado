package io.legado.app.help.config

import io.legado.app.constant.PreferKey
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import splitties.init.appCtx
import java.util.UUID

/**
 * Switchable public search-page parse profiles.
 * Global [AppConfig.aiWebSearchMode] stays separate.
 */
object WebSearchPageProfiles {

    const val MAX_PROFILES = 10
    const val DEFAULT_PROFILE_NAME = "DuckDuckGo"

    data class PageProfile(
        val id: String,
        val name: String,
        val pageUrlTemplate: String = AppConfig.DEFAULT_PAGE_URL_TEMPLATE,
        val delayMs: Int = AppConfig.DEFAULT_PAGE_DELAY_MS,
        val resultSelector: String = AppConfig.DEFAULT_PAGE_RESULT_SELECTOR,
        val titleSelector: String = AppConfig.DEFAULT_PAGE_TITLE_SELECTOR,
        val snippetSelector: String = AppConfig.DEFAULT_PAGE_SNIPPET_SELECTOR,
        val redirectParam: String = AppConfig.DEFAULT_PAGE_REDIRECT_PARAM,
        val excludeHosts: String = AppConfig.DEFAULT_PAGE_EXCLUDE_HOSTS,
    ) {
        fun normalized(): PageProfile = copy(
            name = name.trim().ifBlank { DEFAULT_PROFILE_NAME },
            pageUrlTemplate = pageUrlTemplate.trim()
                .ifBlank { AppConfig.DEFAULT_PAGE_URL_TEMPLATE },
            delayMs = delayMs.coerceIn(0, 10_000),
            resultSelector = resultSelector.trim()
                .ifBlank { AppConfig.DEFAULT_PAGE_RESULT_SELECTOR },
            titleSelector = titleSelector.trim()
                .ifBlank { AppConfig.DEFAULT_PAGE_TITLE_SELECTOR },
            snippetSelector = snippetSelector.trim()
                .ifBlank { AppConfig.DEFAULT_PAGE_SNIPPET_SELECTOR },
            redirectParam = redirectParam.trim(),
            excludeHosts = excludeHosts.trim()
                .ifBlank { AppConfig.DEFAULT_PAGE_EXCLUDE_HOSTS },
        )
    }

    fun list(): List<PageProfile> {
        ensureMigrated()
        return loadRaw()
    }

    fun get(id: String): PageProfile? =
        list().firstOrNull { it.id == id }

    fun findByName(name: String): PageProfile? {
        val key = name.trim()
        if (key.isBlank()) return null
        return list().firstOrNull { it.name.equals(key, ignoreCase = true) }
    }

    fun activeId(): String {
        ensureMigrated()
        val profiles = loadRaw()
        val stored = appCtx.getPrefString(PreferKey.aiWebSearchPageActiveProfileId).orEmpty()
        if (profiles.any { it.id == stored }) return stored
        val first = profiles.first().id
        appCtx.putPrefString(PreferKey.aiWebSearchPageActiveProfileId, first)
        return first
    }

    fun getActive(): PageProfile {
        val id = activeId()
        return get(id) ?: list().first()
    }

    fun setActive(id: String): Result<PageProfile> {
        ensureMigrated()
        val profile = get(id) ?: return Result.failure(IllegalArgumentException("profile not found: $id"))
        appCtx.putPrefString(PreferKey.aiWebSearchPageActiveProfileId, id)
        return Result.success(profile)
    }

    fun update(profile: PageProfile): Result<PageProfile> {
        ensureMigrated()
        val normalized = profile.normalized()
        val profiles = loadRaw().toMutableList()
        val idx = profiles.indexOfFirst { it.id == normalized.id }
        if (idx < 0) return Result.failure(IllegalArgumentException("profile not found: ${normalized.id}"))
        profiles[idx] = normalized
        save(profiles)
        return Result.success(normalized)
    }

    fun create(
        name: String,
        pageUrlTemplate: String? = null,
        delayMs: Int? = null,
        resultSelector: String? = null,
        titleSelector: String? = null,
        snippetSelector: String? = null,
        redirectParam: String? = null,
        excludeHosts: String? = null,
        activate: Boolean = false,
    ): Result<PageProfile> {
        ensureMigrated()
        val profiles = loadRaw()
        if (profiles.size >= MAX_PROFILES) {
            return Result.failure(IllegalStateException("Max $MAX_PROFILES profiles reached"))
        }
        val profile = PageProfile(
            id = newId(),
            name = name,
            pageUrlTemplate = pageUrlTemplate ?: AppConfig.DEFAULT_PAGE_URL_TEMPLATE,
            delayMs = delayMs ?: AppConfig.DEFAULT_PAGE_DELAY_MS,
            resultSelector = resultSelector ?: AppConfig.DEFAULT_PAGE_RESULT_SELECTOR,
            titleSelector = titleSelector ?: AppConfig.DEFAULT_PAGE_TITLE_SELECTOR,
            snippetSelector = snippetSelector ?: AppConfig.DEFAULT_PAGE_SNIPPET_SELECTOR,
            redirectParam = redirectParam ?: AppConfig.DEFAULT_PAGE_REDIRECT_PARAM,
            excludeHosts = excludeHosts ?: AppConfig.DEFAULT_PAGE_EXCLUDE_HOSTS,
        ).normalized()
        save(profiles + profile)
        if (activate) {
            appCtx.putPrefString(PreferKey.aiWebSearchPageActiveProfileId, profile.id)
        }
        return Result.success(profile)
    }

    fun delete(id: String): Result<PageProfile> {
        ensureMigrated()
        val profiles = loadRaw()
        if (profiles.size <= 1) {
            return Result.failure(IllegalStateException("Cannot delete the last profile"))
        }
        val removed = profiles.firstOrNull { it.id == id }
            ?: return Result.failure(IllegalArgumentException("profile not found: $id"))
        val next = profiles.filter { it.id != id }
        save(next)
        val storedActive = appCtx.getPrefString(PreferKey.aiWebSearchPageActiveProfileId)
        if (storedActive == id || next.none { it.id == storedActive }) {
            appCtx.putPrefString(PreferKey.aiWebSearchPageActiveProfileId, next.first().id)
        }
        return Result.success(removed)
    }

    fun updateActive(transform: (PageProfile) -> PageProfile): PageProfile {
        val current = getActive()
        val updated = transform(current).normalized().copy(id = current.id)
        update(updated)
        return get(updated.id) ?: updated
    }

    private fun loadRaw(): List<PageProfile> {
        val json = appCtx.getPrefString(PreferKey.aiWebSearchPageProfiles).orEmpty()
        if (json.isBlank()) return emptyList()
        return GSON.fromJsonArray<PageProfile>(json).getOrNull()
            ?.map { it.normalized() }
            ?.takeIf { it.isNotEmpty() }
            ?: emptyList()
    }

    private fun save(profiles: List<PageProfile>) {
        appCtx.putPrefString(PreferKey.aiWebSearchPageProfiles, GSON.toJson(profiles))
    }

    private fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    @Synchronized
    private fun ensureMigrated() {
        val existing = loadRaw()
        if (existing.isNotEmpty()) {
            val active = appCtx.getPrefString(PreferKey.aiWebSearchPageActiveProfileId).orEmpty()
            if (existing.none { it.id == active }) {
                appCtx.putPrefString(PreferKey.aiWebSearchPageActiveProfileId, existing.first().id)
            }
            return
        }
        val migrated = PageProfile(
            id = newId(),
            name = DEFAULT_PROFILE_NAME,
            pageUrlTemplate = legacyString(PreferKey.aiWebSearchPageUrlTemplate)
                ?: AppConfig.DEFAULT_PAGE_URL_TEMPLATE,
            delayMs = legacyInt(PreferKey.aiWebSearchPageDelayMs, AppConfig.DEFAULT_PAGE_DELAY_MS),
            resultSelector = legacyString(PreferKey.aiWebSearchPageResultSelector)
                ?: AppConfig.DEFAULT_PAGE_RESULT_SELECTOR,
            titleSelector = legacyString(PreferKey.aiWebSearchPageTitleSelector)
                ?: AppConfig.DEFAULT_PAGE_TITLE_SELECTOR,
            snippetSelector = legacyString(PreferKey.aiWebSearchPageSnippetSelector)
                ?: AppConfig.DEFAULT_PAGE_SNIPPET_SELECTOR,
            redirectParam = appCtx.getPrefString(PreferKey.aiWebSearchPageRedirectParam)
                ?: AppConfig.DEFAULT_PAGE_REDIRECT_PARAM,
            excludeHosts = legacyString(PreferKey.aiWebSearchPageExcludeHosts)
                ?: AppConfig.DEFAULT_PAGE_EXCLUDE_HOSTS,
        ).normalized()
        save(listOf(migrated))
        appCtx.putPrefString(PreferKey.aiWebSearchPageActiveProfileId, migrated.id)
    }

    private fun legacyString(key: String): String? =
        appCtx.getPrefString(key)?.takeIf { it.isNotBlank() }

    private fun legacyInt(key: String, default: Int): Int =
        appCtx.getPrefInt(key, default).coerceIn(0, 10_000)
}
