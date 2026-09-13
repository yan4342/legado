package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_memory_table_rows",
    indices = [Index(value = ["tableId"]), Index(value = ["sourceMessageId"])]
)
data class AiMemoryTableRow(
    @PrimaryKey
    val id: String,
    val tableId: String,
    val rowData: String,  // JSON object, e.g. {"时间点":"第一天","地点":"学校","事件":"..."}
    val sourceMessageId: String? = null,  // message that created this row
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
