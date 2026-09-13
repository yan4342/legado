package io.legado.app.data.repository

import io.legado.app.data.appDb
import io.legado.app.data.dao.AiUsageDailySummary
import io.legado.app.data.dao.AiUsageHourlySummary
import io.legado.app.data.dao.AiUsageModelSummary
import io.legado.app.data.dao.AiUsageSourceSummary
import io.legado.app.data.dao.AiUsageTokenSummary
import io.legado.app.data.entities.AiUsageRecord
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiUsage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class AiUsageRepository {

    private val dao get() = appDb.aiUsageRecordDao

    fun record(
        request: AiGenerateRequest,
        usage: AiUsage?,
        generatedChars: Int,
        durationMs: Long,
        success: Boolean,
        estimated: Boolean,
    ) {
        val meta = request.callMeta
        val prompt = usage?.promptTokens ?: 0
        val completion = usage?.completionTokens ?: 0
        val total = when {
            usage?.totalTokens != null && usage.totalTokens > 0 -> usage.totalTokens
            prompt + completion > 0 -> prompt + completion
            else -> 0
        }
        dao.insert(
            AiUsageRecord(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                providerId = request.model.provider.id,
                modelId = request.model.modelId,
                modelName = request.model.displayName,
                source = meta?.source ?: "",
                conversationId = meta?.conversationId,
                promptTokens = prompt,
                completionTokens = completion,
                totalTokens = total,
                cacheHitTokens = usage?.cacheHitTokens ?: 0,
                generatedChars = generatedChars,
                durationMs = durationMs,
                success = success,
                estimated = estimated,
            )
        )
    }

    fun sumByDateRange(startDate: String, endDate: String): AiUsageTokenSummary =
        dao.sumByDateRange(startDate, endDate)

    fun sumDailyByDateRange(startDate: String, endDate: String): List<AiUsageDailySummary> =
        dao.sumDailyByDateRange(startDate, endDate)

    fun sumByModel(): List<AiUsageModelSummary> = dao.sumByModel()

    fun sumByModelInRange(startDate: String, endDate: String): List<AiUsageModelSummary> =
        dao.sumByModelInRange(startDate, endDate)

    fun sumBySource(): List<AiUsageSourceSummary> = dao.sumBySource()

    fun recentRecords(limit: Int): List<AiUsageRecord> = dao.recentRecords(limit)

    fun recentByConversation(conversationId: String, limit: Int = 80): List<AiUsageRecord> =
        dao.recentByConversation(conversationId, limit)

    fun recentSideEffectsByConversation(conversationId: String, limit: Int = 50): List<AiUsageRecord> =
        dao.recentSideEffectsByConversation(conversationId, limit)

    fun consecutiveUsageDays(): Int {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        var count = 0
        while (true) {
            val date = fmt.format(cal.time)
            if (dao.sumTokensOnDate(date) > 0) {
                count++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return count
    }

    fun todaySummary(): AiUsageTokenSummary {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
        return dao.sumByDateRange(today, today)
    }

    fun allTimeSummary(): AiUsageTokenSummary {
        return dao.sumByDateRange("1970-01-01", "2099-12-31")
    }

    fun sumHourlyByDate(date: String): List<AiUsageHourlySummary> = dao.sumHourlyByDate(date)

    fun hasEstimatedRecords(): Boolean = dao.countEstimatedAll() > 0

    fun hasEstimatedInRange(startDate: String, endDate: String): Boolean =
        dao.countEstimatedInRange(startDate, endDate) > 0

    fun avgDailyTokensLastDays(days: Int): Long {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val end = Calendar.getInstance()
        val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(days - 1)) }
        val records = dao.sumDailyByDateRange(fmt.format(start.time), fmt.format(end.time))
        if (records.isEmpty()) return 0L
        return records.sumOf { it.totalTokens } / records.size
    }

    fun clearAll() {
        dao.deleteAll()
    }
}
