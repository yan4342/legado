package io.legado.app.domain.usecase

/**
 * Sticky snapshot cache for [WritingWorkspaceIndexBuilder] prefetch text.
 * Rebuilds only when [markStale] was called for the conversation (e.g. after structured maintain).
 */
object WorkspacePrefetchCache {

    private const val MAX_CONVERSATIONS = 32

    private data class Entry(
        val text: String,
        val revision: Int,
    )

    private val snapshots = object : LinkedHashMap<String, Entry>(MAX_CONVERSATIONS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean {
            return size > MAX_CONVERSATIONS
        }
    }
    private val staleIds = mutableSetOf<String>()
    private val locks = mutableMapOf<String, Any>()

    fun markStale(conversationId: String) {
        if (conversationId.isBlank()) return
        synchronized(this) {
            staleIds.add(conversationId)
        }
    }

    fun clear(conversationId: String) {
        if (conversationId.isBlank()) return
        synchronized(this) {
            snapshots.remove(conversationId)
            staleIds.remove(conversationId)
            locks.remove(conversationId)
        }
    }

    suspend fun resolve(
        conversationId: String,
        buildFresh: suspend () -> String,
    ): String {
        if (conversationId.isBlank()) return buildFresh()
        val lock = lockFor(conversationId)
        val cachedHit = synchronized(lock) {
            val isStale = synchronized(this@WorkspacePrefetchCache) {
                conversationId in staleIds
            }
            val cached = snapshots[conversationId]
            if (cached != null && !isStale) cached.text else null
        }
        if (cachedHit != null) return cachedHit

        val fresh = buildFresh()

        synchronized(lock) {
            val revision = (snapshots[conversationId]?.revision ?: 0) + 1
            snapshots[conversationId] = Entry(fresh, revision)
            synchronized(this@WorkspacePrefetchCache) {
                staleIds.remove(conversationId)
            }
        }
        return fresh
    }

    /** Visible for tests. */
    internal fun isStale(conversationId: String): Boolean = synchronized(this) {
        conversationId in staleIds
    }

    /** Visible for tests. */
    internal fun peek(conversationId: String): String? = snapshots[conversationId]?.text

    internal fun clearAllForTests() {
        synchronized(this) {
            snapshots.clear()
            staleIds.clear()
            locks.clear()
        }
    }

    private fun lockFor(conversationId: String): Any = synchronized(this) {
        locks.getOrPut(conversationId) { Any() }
    }
}
