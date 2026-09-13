package io.legado.app.domain.usecase

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.utils.GSON

/**
 * Resolve a local book source the same way book-source management search does:
 * [io.legado.app.data.dao.BookSourceDao.search] on name / group / url / comment,
 * plus optional `group:` prefix via [io.legado.app.data.dao.BookSourceDao.groupSearch].
 */
object LocalBookSourceResolver {

    data class Candidate(
        val bookSourceUrl: String,
        val bookSourceName: String,
        val bookSourceGroup: String?,
        val enabled: Boolean,
    )

    sealed class Outcome {
        data class Unique(val source: BookSource) : Outcome()
        data class Ambiguous(val query: String, val candidates: List<Candidate>) : Outcome()
        data class NotFound(val query: String) : Outcome()
        data class MissingQuery(val message: String = "bookSourceUrl or query/bookSourceName is required") : Outcome()
    }

    fun resolveFromDb(
        bookSourceUrl: String? = null,
        query: String? = null,
        bookSourceName: String? = null,
        enabledOnly: Boolean = false,
        limit: Int = 20,
    ): Outcome = resolve(
        bookSourceUrl = bookSourceUrl,
        query = query,
        bookSourceName = bookSourceName,
        enabledOnly = enabledOnly,
        limit = limit,
        getByUrl = { appDb.bookSourceDao.getBookSource(it) },
        search = { key -> searchLocal(key) },
    )

    fun resolve(
        bookSourceUrl: String? = null,
        query: String? = null,
        bookSourceName: String? = null,
        enabledOnly: Boolean = false,
        limit: Int = 20,
        getByUrl: (String) -> BookSource?,
        search: (String) -> List<BookSource>,
    ): Outcome {
        val url = bookSourceUrl?.trim().orEmpty()
        if (url.isNotBlank()) {
            getByUrl(url)?.let { return Outcome.Unique(it) }
            // Explicit bookSourceUrl that misses must not fall through to fuzzy search
            // (a bad URL string could otherwise match unrelated sources).
            return Outcome.NotFound(url)
        }
        val key = sequenceOf(query, bookSourceName)
            .mapNotNull { it?.trim()?.takeIf { s -> s.isNotBlank() } }
            .firstOrNull()
            ?: return Outcome.MissingQuery()

        var hits = search(key)
        if (enabledOnly) hits = hits.filter { it.enabled }
        hits = hits.distinctBy { it.bookSourceUrl }.take(limit.coerceIn(1, 50))

        return when {
            hits.isEmpty() -> Outcome.NotFound(key)
            hits.size == 1 -> Outcome.Unique(hits.first())
            else -> pickPreferred(key, hits) ?: Outcome.Ambiguous(key, hits.map { it.toCandidate() })
        }
    }

    /** Prefer exact URL, then unique exact name (ignore case). */
    fun pickPreferred(key: String, hits: List<BookSource>): Outcome.Unique? {
        hits.firstOrNull { it.bookSourceUrl == key }?.let { return Outcome.Unique(it) }
        val byName = hits.filter { it.bookSourceName.equals(key, ignoreCase = true) }
        if (byName.size == 1) return Outcome.Unique(byName.first())
        return null
    }

    fun ambiguousJson(query: String, candidates: List<Candidate>): String = GSON.toJson(
        mapOf(
            "error" to "Multiple book sources matched; pick one bookSourceUrl (or ask the user).",
            "query" to query,
            "count" to candidates.size,
            "candidates" to candidates.map {
                mapOf(
                    "bookSourceUrl" to it.bookSourceUrl,
                    "bookSourceName" to it.bookSourceName,
                    "bookSourceGroup" to it.bookSourceGroup,
                    "enabled" to it.enabled,
                )
            },
            "hint" to "Call check_book_source again with bookSourceUrl, or use ask_user_questions with candidate urls as option ids.",
        ),
    )

    fun notFoundJson(query: String): String = GSON.toJson(
        mapOf(
            "error" to "Book source not found",
            "query" to query,
            "hint" to "Same local filter as book-source management (name / group / url / comment). Try a shorter name or group:分组名.",
        ),
    )

    private fun searchLocal(key: String): List<BookSource> {
        return if (key.startsWith("group:", ignoreCase = true)) {
            appDb.bookSourceDao.groupSearch(key.substringAfter(':').trim())
        } else {
            appDb.bookSourceDao.search(key)
        }
    }

    private fun BookSource.toCandidate() = Candidate(
        bookSourceUrl = bookSourceUrl,
        bookSourceName = bookSourceName,
        bookSourceGroup = bookSourceGroup,
        enabled = enabled,
    )
}
