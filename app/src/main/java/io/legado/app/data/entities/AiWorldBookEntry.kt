package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_world_book_entries",
    foreignKeys = [
        ForeignKey(
            entity = AiWorldBook::class,
            parentColumns = ["id"],
            childColumns = ["worldBookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("worldBookId")],
)
data class AiWorldBookEntry(
    @PrimaryKey
    val id: String,
    val worldBookId: String,
    val name: String = "",
    val keys: String = "",
    val content: String = "",
    @ColumnInfo(defaultValue = "0")
    val constant: Boolean = false,
    @ColumnInfo(defaultValue = "100")
    val priority: Int = 100,
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
    /** ST-aligned: `prefix` (system block) or `in_chat` (@D). */
    @ColumnInfo(defaultValue = "prefix")
    val position: String = POSITION_PREFIX,
    /** Chat insertion depth from the end (0 = nearest). Used when [position] is [POSITION_IN_CHAT]. */
    @ColumnInfo(defaultValue = "0")
    val insertDepth: Int = 0,
    /** Message role for in-chat injection: system / user / assistant. */
    @ColumnInfo(defaultValue = "system")
    val role: String = ROLE_SYSTEM,
    /** Per-entry keyword scan depth; 0 = use assembly global default. */
    @ColumnInfo(defaultValue = "0")
    val scanDepth: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val POSITION_PREFIX = "prefix"
        const val POSITION_IN_CHAT = "in_chat"
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}
