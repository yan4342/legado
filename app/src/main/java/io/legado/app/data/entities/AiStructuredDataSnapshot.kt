package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_structured_data_snapshots",
    indices = [Index("conversationId"), Index("createdAt")],
)
data class AiStructuredDataSnapshot(
    @PrimaryKey val id: String,
    val conversationId: String?,
    val resourceType: String,
    val resourceKey: String,
    val payloadJson: String,
    val toolCallId: String?,
    val batchId: String?,
    val toolName: String?,
    val createdAt: Long,
)
