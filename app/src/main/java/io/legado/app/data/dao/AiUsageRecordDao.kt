package io.legado.app.data.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiUsageRecord

data class AiUsageTokenSummary(
    @ColumnInfo(name = "promptTokens") val promptTokens: Long = 0,
    @ColumnInfo(name = "completionTokens") val completionTokens: Long = 0,
    @ColumnInfo(name = "totalTokens") val totalTokens: Long = 0,
    @ColumnInfo(name = "cacheHitTokens") val cacheHitTokens: Long = 0,
    @ColumnInfo(name = "generatedChars") val generatedChars: Long = 0,
    @ColumnInfo(name = "callCount") val callCount: Long = 0,
)

data class AiUsageDailySummary(
    val date: String = "",
    @ColumnInfo(name = "totalTokens") val totalTokens: Long = 0,
    @ColumnInfo(name = "generatedChars") val generatedChars: Long = 0,
    @ColumnInfo(name = "callCount") val callCount: Long = 0,
)

data class AiUsageModelSummary(
    val modelId: String = "",
    val modelName: String = "",
    @ColumnInfo(name = "totalTokens") val totalTokens: Long = 0,
    @ColumnInfo(name = "generatedChars") val generatedChars: Long = 0,
    @ColumnInfo(name = "callCount") val callCount: Long = 0,
)

data class AiUsageSourceSummary(
    val source: String = "",
    @ColumnInfo(name = "totalTokens") val totalTokens: Long = 0,
    @ColumnInfo(name = "generatedChars") val generatedChars: Long = 0,
    @ColumnInfo(name = "callCount") val callCount: Long = 0,
)

data class AiUsageHourlySummary(
    val hour: String = "",
    @ColumnInfo(name = "totalTokens") val totalTokens: Long = 0,
)

@Dao
interface AiUsageRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(record: AiUsageRecord)

    @Query(
        """
        SELECT coalesce(sum(promptTokens), 0) as promptTokens,
               coalesce(sum(completionTokens), 0) as completionTokens,
               coalesce(sum(totalTokens), 0) as totalTokens,
               coalesce(sum(cacheHitTokens), 0) as cacheHitTokens,
               coalesce(sum(generatedChars), 0) as generatedChars,
               count(*) as callCount
        FROM ai_usage_records
        WHERE success = 1
          AND strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') BETWEEN :startDate AND :endDate
        """
    )
    fun sumByDateRange(startDate: String, endDate: String): AiUsageTokenSummary

    @Query(
        """
        SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') as date,
               coalesce(sum(totalTokens), 0) as totalTokens,
               coalesce(sum(generatedChars), 0) as generatedChars,
               count(*) as callCount
        FROM ai_usage_records
        WHERE success = 1
          AND strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') BETWEEN :startDate AND :endDate
        GROUP BY date
        ORDER BY date
        """
    )
    fun sumDailyByDateRange(startDate: String, endDate: String): List<AiUsageDailySummary>

    @Query(
        """
        SELECT modelId, modelName,
               coalesce(sum(totalTokens), 0) as totalTokens,
               coalesce(sum(generatedChars), 0) as generatedChars,
               count(*) as callCount
        FROM ai_usage_records
        WHERE success = 1
        GROUP BY modelId, modelName
        ORDER BY totalTokens DESC
        """
    )
    fun sumByModel(): List<AiUsageModelSummary>

    @Query(
        """
        SELECT modelId, modelName,
               coalesce(sum(totalTokens), 0) as totalTokens,
               coalesce(sum(generatedChars), 0) as generatedChars,
               count(*) as callCount
        FROM ai_usage_records
        WHERE success = 1
          AND strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') BETWEEN :startDate AND :endDate
        GROUP BY modelId, modelName
        ORDER BY totalTokens DESC
        """
    )
    fun sumByModelInRange(startDate: String, endDate: String): List<AiUsageModelSummary>

    @Query(
        """
        SELECT source,
               coalesce(sum(totalTokens), 0) as totalTokens,
               coalesce(sum(generatedChars), 0) as generatedChars,
               count(*) as callCount
        FROM ai_usage_records
        WHERE success = 1
        GROUP BY source
        ORDER BY totalTokens DESC
        """
    )
    fun sumBySource(): List<AiUsageSourceSummary>

    @Query(
        """
        SELECT * FROM ai_usage_records
        WHERE conversationId = :conversationId
          AND source IN (
            'post_edit', 'structured_maintain', 'suggestion', 'galgame',
            'help_reply', 'tool_submodel', 'outline', 'memory', 'character',
            'worldbook', 'compress', 'title'
          )
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    fun recentSideEffectsByConversation(conversationId: String, limit: Int): List<AiUsageRecord>

    @Query(
        """
        SELECT * FROM ai_usage_records
        WHERE conversationId = :conversationId
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    fun recentByConversation(conversationId: String, limit: Int): List<AiUsageRecord>

    @Query(
        """
        SELECT * FROM ai_usage_records
        WHERE success = 1
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    fun recentRecords(limit: Int): List<AiUsageRecord>

    @Query(
        """
        SELECT coalesce(sum(totalTokens), 0) FROM ai_usage_records
        WHERE success = 1
          AND strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') = :date
        """
    )
    fun sumTokensOnDate(date: String): Long

    @Query(
        """
        SELECT strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') as hour,
               coalesce(sum(totalTokens), 0) as totalTokens
        FROM ai_usage_records
        WHERE success = 1
          AND strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') = :date
        GROUP BY hour
        ORDER BY hour
        """
    )
    fun sumHourlyByDate(date: String): List<AiUsageHourlySummary>

    @Query(
        """
        SELECT count(*) FROM ai_usage_records
        WHERE success = 1 AND estimated = 1
        """
    )
    fun countEstimatedAll(): Int

    @Query(
        """
        SELECT count(*) FROM ai_usage_records
        WHERE success = 1 AND estimated = 1
          AND strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') BETWEEN :startDate AND :endDate
        """
    )
    fun countEstimatedInRange(startDate: String, endDate: String): Int

    @Query("DELETE FROM ai_usage_records")
    fun deleteAll()

    @get:Query("SELECT * FROM ai_usage_records ORDER BY timestamp ASC")
    val all: List<AiUsageRecord>
}
