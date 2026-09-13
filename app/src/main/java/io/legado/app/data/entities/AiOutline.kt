package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_outlines")
data class AiOutline(
    @PrimaryKey val conversationId: String,
    val content: String,
    /** Optional source book binding (same shape as AiWorldBook). */
    @ColumnInfo(defaultValue = "")
    val bookUrl: String = "",
    @ColumnInfo(defaultValue = "")
    val bookName: String = "",
    @ColumnInfo(defaultValue = "")
    val bookAuthor: String = "",
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
