package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Full [BookSource] JSON snapshot taken before AI patch/write/restore.
 * Global (not conversation-scoped); prune keeps the newest N per bookSourceUrl.
 */
@Entity(
    tableName = "book_source_versions",
    indices = [
        Index("bookSourceUrl"),
        Index("createdAt"),
    ],
)
data class BookSourceVersion(
    @PrimaryKey val id: String,
    val bookSourceUrl: String,
    /** Full BookSource JSON payload. */
    val payloadJson: String,
    /** Origin of the snapshot: patch / write / before_restore / check. */
    val source: String,
    val diffSummary: String? = null,
    val toolCallId: String? = null,
    val batchId: String? = null,
    val conversationId: String? = null,
    val createdAt: Long,
)
