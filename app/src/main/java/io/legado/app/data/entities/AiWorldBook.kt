package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_world_books")
data class AiWorldBook(
    @PrimaryKey
    val id: String,
    val name: String,
    val bookUrl: String = "",
    val bookName: String = "",
    val bookAuthor: String = "",
    /** 正典标记:canonical=1 的条目属于书级正典层(对二创不可见),canonical=0 属会话衍生层。 */
    @ColumnInfo(defaultValue = "0")
    val canonical: Boolean = false,
    val writingStyle: String = "",
    val grammar: String = "",
    val plotSummary: String = "",
    val representativeDialogues: String = "",
    val representativeProse: String = "",
    val sourceChapterIndices: String = "",
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
