package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_usage_records")
data class AiUsageRecord(
    @PrimaryKey
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val providerId: String = "",
    val modelId: String = "",
    val modelName: String = "",
    val source: String = "",
    val conversationId: String? = null,
    @ColumnInfo(defaultValue = "0")
    val promptTokens: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val completionTokens: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val totalTokens: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val cacheHitTokens: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val generatedChars: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val durationMs: Long = 0,
    @ColumnInfo(defaultValue = "1")
    val success: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val estimated: Boolean = false,
)
