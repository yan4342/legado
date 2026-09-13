package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_workspaces",
    indices = [Index(value = ["conversationId"], unique = true)],
)
data class AiWorkspace(
    @PrimaryKey
    val id: String,
    val name: String = "",
    val conversationId: String,
    @ColumnInfo(defaultValue = "''")
    val characterCardIds: String = "",
    @ColumnInfo(defaultValue = "''")
    val worldBookIds: String = "",
    @ColumnInfo(defaultValue = "''")
    val writingPromptIds: String = "",
    /** Optional source book binding (same shape as AiWorldBook). 采纳桥依赖此绑定。 */
    @ColumnInfo(defaultValue = "''")
    val bookUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
