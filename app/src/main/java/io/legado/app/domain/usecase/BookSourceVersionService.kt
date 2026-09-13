package io.legado.app.domain.usecase

import io.legado.app.data.appDb
import io.legado.app.data.dao.BookSourceVersionDao
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourceVersion
import io.legado.app.domain.usecase.structured.FieldChange
import io.legado.app.domain.usecase.structured.StructuredDataDiff
import io.legado.app.help.source.SourceHelp
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import java.util.UUID

/**
 * Capture / list / diff / restore full [BookSource] snapshots for AI-only edits.
 */
class BookSourceVersionService(
    private val versionDao: BookSourceVersionDao,
) {

    data class VersionSummary(
        val id: String,
        val bookSourceUrl: String,
        val source: String,
        val diffSummary: String?,
        val createdAt: Long,
        val conversationId: String?,
    )

    data class DiffResult(
        val versionId: String,
        val bookSourceUrl: String,
        val source: String,
        val createdAt: Long,
        val changes: List<FieldChange>,
        val summary: String,
    )

    data class RestoreResult(
        val success: Boolean,
        val message: String,
        val snapshotId: String? = null,
        val bookSourceUrl: String? = null,
    )

    data class CaptureMeta(
        val toolCallId: String? = null,
        val batchId: String? = null,
        val conversationId: String? = null,
        val diffSummary: String? = null,
    )

    /**
     * Snapshot the current DB source before an AI change.
     * Returns null when the source does not exist yet (first write).
     */
    suspend fun captureBeforeChange(
        bookSourceUrl: String,
        source: String,
        meta: CaptureMeta? = null,
    ): String? {
        val url = bookSourceUrl.trim()
        if (url.isBlank()) return null
        val current = appDb.bookSourceDao.getBookSource(url) ?: return null
        val snapshotId = newId()
        versionDao.insert(
            BookSourceVersion(
                id = snapshotId,
                bookSourceUrl = url,
                payloadJson = GSON.toJson(current),
                source = source,
                diffSummary = meta?.diffSummary,
                toolCallId = meta?.toolCallId,
                batchId = meta?.batchId,
                conversationId = meta?.conversationId,
                createdAt = System.currentTimeMillis(),
            ),
        )
        prune(url)
        return snapshotId
    }

    suspend fun list(bookSourceUrl: String, limit: Int = VERSION_KEEP): List<VersionSummary> {
        val url = bookSourceUrl.trim()
        if (url.isBlank()) return emptyList()
        return versionDao.listByUrl(url, limit.coerceIn(1, 50)).map { v ->
            VersionSummary(
                id = v.id,
                bookSourceUrl = v.bookSourceUrl,
                source = v.source,
                diffSummary = v.diffSummary,
                createdAt = v.createdAt,
                conversationId = v.conversationId,
            )
        }
    }

    suspend fun diff(versionId: String): DiffResult? {
        val version = versionDao.getById(versionId) ?: return null
        val snap = GSON.fromJsonObject<BookSource>(version.payloadJson).getOrNull() ?: return null
        val current = appDb.bookSourceDao.getBookSource(version.bookSourceUrl)
        val changes = diffBookSources(current, snap)
        val summary = version.diffSummary?.takeIf { it.isNotBlank() }
            ?: if (changes.isEmpty()) "no field changes vs current"
            else "${changes.size} field(s) differ from current"
        return DiffResult(
            versionId = version.id,
            bookSourceUrl = version.bookSourceUrl,
            source = version.source,
            createdAt = version.createdAt,
            changes = changes.take(80),
            summary = summary,
        )
    }

    /**
     * Capture current as before_restore, then write the snapshot payload back to DAO.
     */
    suspend fun restore(versionId: String, meta: CaptureMeta? = null): RestoreResult {
        val version = versionDao.getById(versionId)
            ?: return RestoreResult(false, "Version not found: $versionId")
        val restored = GSON.fromJsonObject<BookSource>(version.payloadJson).getOrNull()
            ?: return RestoreResult(false, "Invalid snapshot payload")
        val beforeId = captureBeforeChange(
            bookSourceUrl = version.bookSourceUrl,
            source = SOURCE_BEFORE_RESTORE,
            meta = meta?.copy(diffSummary = "before restore ${version.id}"),
        )
        SourceHelp.insertBookSource(restored)
        return RestoreResult(
            success = true,
            message = "Restored book source ${restored.bookSourceName.ifBlank { restored.bookSourceUrl }}",
            snapshotId = beforeId,
            bookSourceUrl = restored.bookSourceUrl,
        )
    }

    suspend fun prune(bookSourceUrl: String, keep: Int = VERSION_KEEP) {
        versionDao.deleteOlderThan(bookSourceUrl, keep.coerceAtLeast(1))
    }

    /** Keep version history attached after primary-key rename. */
    suspend fun migrateUrl(oldUrl: String, newUrl: String) {
        val from = oldUrl.trim()
        val to = newUrl.trim()
        if (from.isBlank() || to.isBlank() || from == to) return
        versionDao.remapeUrl(from, to)
        prune(to)
    }

    suspend fun getById(versionId: String): BookSourceVersion? = versionDao.getById(versionId)

    companion object {
        const val VERSION_KEEP = 10
        const val SOURCE_PATCH = "patch"
        const val SOURCE_WRITE = "write"
        const val SOURCE_BEFORE_RESTORE = "before_restore"
        const val SOURCE_CHECK = "check"
        const val SOURCE_COMMIT = "commit"

        fun newId(): String = "bsv_${UUID.randomUUID().toString().replace("-", "")}"

        fun diffBookSources(current: BookSource?, snapshot: BookSource): List<FieldChange> {
            val oldMap = current?.toFieldMap().orEmpty()
            val newMap = snapshot.toFieldMap()
            return StructuredDataDiff.diffFields(oldMap, newMap)
        }

        fun BookSource.toFieldMap(): Map<String, String> = linkedMapOf(
            "bookSourceName" to bookSourceName,
            "bookSourceUrl" to bookSourceUrl,
            "bookSourceGroup" to bookSourceGroup.orEmpty(),
            "bookSourceType" to bookSourceType.toString(),
            "bookUrlPattern" to bookUrlPattern.orEmpty(),
            "customOrder" to customOrder.toString(),
            "enabled" to enabled.toString(),
            "enabledExplore" to enabledExplore.toString(),
            "jsLib" to jsLib.orEmpty(),
            "enabledCookieJar" to (enabledCookieJar?.toString().orEmpty()),
            "concurrentRate" to concurrentRate.orEmpty(),
            "header" to header.orEmpty(),
            "loginUrl" to loginUrl.orEmpty(),
            "loginUi" to loginUi.orEmpty(),
            "loginCheckJs" to loginCheckJs.orEmpty(),
            "coverDecodeJs" to coverDecodeJs.orEmpty(),
            "bookSourceComment" to bookSourceComment.orEmpty(),
            "variableComment" to variableComment.orEmpty(),
            "exploreUrl" to exploreUrl.orEmpty(),
            "exploreScreen" to exploreScreen.orEmpty(),
            "searchUrl" to searchUrl.orEmpty(),
            "ruleExplore" to GSON.toJson(ruleExplore),
            "ruleSearch" to GSON.toJson(ruleSearch),
            "ruleBookInfo" to GSON.toJson(ruleBookInfo),
            "ruleToc" to GSON.toJson(ruleToc),
            "ruleContent" to GSON.toJson(ruleContent),
        )
    }
}
