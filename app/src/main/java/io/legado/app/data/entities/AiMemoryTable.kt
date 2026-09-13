package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_memory_tables")
data class AiMemoryTable(
    @PrimaryKey
    val id: String,
    val name: String,
    val columns: String,  // JSON array, e.g. ["时间点","地点","事件","情绪","备注"]
    val conversationId: String = "",  // required non-empty; bound to ai_chat_conversations.id
    /** Optional source book binding (same shape as AiWorldBook). */
    @ColumnInfo(defaultValue = "")
    val bookUrl: String = "",
    @ColumnInfo(defaultValue = "")
    val bookName: String = "",
    @ColumnInfo(defaultValue = "")
    val bookAuthor: String = "",
    /** 正典标记:canonical=1 的行属于书级正典层(对二创不可见),canonical=0 属会话衍生层。 */
    @ColumnInfo(defaultValue = "0")
    val canonical: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
