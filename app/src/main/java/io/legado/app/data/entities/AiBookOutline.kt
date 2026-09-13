package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 书级正典大纲。
 *
 * 与 [AiOutline]（PK=conversationId，会话作用域）不同，正典大纲以 bookUrl 为主键，
 * 每本书只有一份，存放于正典层，供书详情页只读、正典维护会话直写。
 * 衍生会话的大纲仍写在 [AiOutline]；只有通过"采纳桥"才回填到本表。
 */
@Entity(tableName = "ai_book_outlines")
data class AiBookOutline(
    @PrimaryKey val bookUrl: String,
    val content: String,
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
    /** 最近一次写入/采纳来源的会话 id（空表示来自回填或手动维护）。 */
    @ColumnInfo(defaultValue = "")
    val sourceConversationId: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
