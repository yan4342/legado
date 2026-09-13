package io.legado.app.domain.usecase

import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import splitties.init.appCtx
import java.util.Calendar

/**
 * Hard caps for AI web research (limits are user-configurable via AppConfig):
 * - web_search conversation (default 10)
 * - read_web_page conversation (default 20, separate)
 * - Week/month for API web_search + API config test (defaults 249 / 996);
 *   public search-page mode does not use week/month
 * - Test connection daily (default 5)
 * Counters are local prefs (not backed up). Limit prefs are backed up.
 */
class WebSearchRateLimiter {

    sealed interface CheckResult {
        data object Ok : CheckResult
        data class Blocked(val layer: String, val message: String) : CheckResult
    }

    data class UsageSnapshot(
        val weekUsed: Int,
        val weekLimit: Int,
        val monthUsed: Int,
        val monthLimit: Int,
        val conversationUsed: Int,
        val conversationLimit: Int,
        val readConversationUsed: Int = 0,
        val readConversationLimit: Int = DEFAULT_READ_CONVERSATION_LIMIT,
        val testDayUsed: Int = 0,
        val testDayLimit: Int = DEFAULT_TEST_DAY_LIMIT,
    )

    private fun searchConvLimit(): Int = AppConfig.aiWebSearchConvLimit
    private fun readConvLimit(): Int = AppConfig.aiWebReadConvLimit
    private fun weekLimit(): Int = AppConfig.aiWebSearchWeekLimit
    private fun monthLimit(): Int = AppConfig.aiWebSearchMonthLimit
    private fun testDayLimit(): Int = AppConfig.aiWebSearchTestDayLimit

    /** Conversation + week/month (for paid API search). */
    fun check(conversationId: String?): CheckResult {
        when (val conv = checkSearchConversation(conversationId)) {
            is CheckResult.Blocked -> return conv
            CheckResult.Ok -> Unit
        }
        return checkWeekMonth()
    }

    /** web_search per-conversation cap. */
    fun checkSearchConversation(conversationId: String?): CheckResult {
        rotatePeriodsIfNeeded()
        val limit = searchConvLimit()
        val convId = conversationId?.takeIf { it.isNotBlank() } ?: return CheckResult.Ok
        val convCount = searchConversationCounts()[convId] ?: 0
        if (convCount >= limit) {
            return CheckResult.Blocked(
                layer = "conversation",
                message = "Web search limit reached for this conversation ($limit)",
            )
        }
        return CheckResult.Ok
    }

    /** read_web_page per-conversation cap (separate from search). */
    fun checkReadConversation(conversationId: String?): CheckResult {
        rotatePeriodsIfNeeded()
        val limit = readConvLimit()
        val convId = conversationId?.takeIf { it.isNotBlank() } ?: return CheckResult.Ok
        val convCount = readConversationCounts()[convId] ?: 0
        if (convCount >= limit) {
            return CheckResult.Blocked(
                layer = "read_conversation",
                message = "Read web page limit reached for this conversation ($limit)",
            )
        }
        return CheckResult.Ok
    }

    /** Pre-check for config-page test connection (daily test cap + week/month). */
    fun checkTest(): CheckResult {
        when (val day = checkTestDay()) {
            is CheckResult.Blocked -> return day
            CheckResult.Ok -> Unit
        }
        return checkWeekMonth()
    }

    /** Page-mode test: daily test cap only (no week/month). */
    fun checkTestDay(): CheckResult {
        rotatePeriodsIfNeeded()
        val limit = testDayLimit()
        val testUsed = appCtx.getPrefInt(PreferKey.aiWebSearchTestDayCount, 0)
        if (testUsed >= limit) {
            return CheckResult.Blocked(
                layer = "test_day",
                message = "Daily test-connection limit reached ($limit)",
            )
        }
        return CheckResult.Ok
    }

    fun record(conversationId: String?) {
        rotatePeriodsIfNeeded()
        bumpWeekMonth()
        recordSearchConversation(conversationId)
    }

    fun recordSearchConversation(conversationId: String?) {
        bumpConversationMap(
            PreferKey.aiWebSearchConvCounts,
            conversationId,
            searchConversationCounts(),
        )
    }

    fun recordReadConversation(conversationId: String?) {
        bumpConversationMap(
            PreferKey.aiWebReadConvCounts,
            conversationId,
            readConversationCounts(),
        )
    }

    /** Record a successful API test: bumps daily test counter and week/month (not conversation). */
    fun recordTest() {
        rotatePeriodsIfNeeded()
        bumpWeekMonth()
        bumpTestDay()
    }

    /** Page-mode test: daily test counter only. */
    fun recordTestDay() {
        rotatePeriodsIfNeeded()
        bumpTestDay()
    }

    private fun bumpTestDay() {
        appCtx.putPrefInt(
            PreferKey.aiWebSearchTestDayCount,
            appCtx.getPrefInt(PreferKey.aiWebSearchTestDayCount, 0) + 1,
        )
    }

    fun usage(conversationId: String? = null): UsageSnapshot {
        rotatePeriodsIfNeeded()
        val id = conversationId?.takeIf { it.isNotBlank() }
        return UsageSnapshot(
            weekUsed = appCtx.getPrefInt(PreferKey.aiWebSearchWeekCount, 0),
            weekLimit = weekLimit(),
            monthUsed = appCtx.getPrefInt(PreferKey.aiWebSearchMonthCount, 0),
            monthLimit = monthLimit(),
            conversationUsed = id?.let { searchConversationCounts()[it] ?: 0 } ?: 0,
            conversationLimit = searchConvLimit(),
            readConversationUsed = id?.let { readConversationCounts()[it] ?: 0 } ?: 0,
            readConversationLimit = readConvLimit(),
            testDayUsed = appCtx.getPrefInt(PreferKey.aiWebSearchTestDayCount, 0),
            testDayLimit = testDayLimit(),
        )
    }

    private fun bumpConversationMap(
        prefKey: String,
        conversationId: String?,
        current: Map<String, Int>,
    ) {
        rotatePeriodsIfNeeded()
        val convId = conversationId?.takeIf { it.isNotBlank() } ?: return
        val map = current.toMutableMap()
        map[convId] = (map[convId] ?: 0) + 1
        if (map.size > 200) {
            val trimmed = map.entries.sortedByDescending { it.value }.take(100)
                .associate { it.key to it.value }
            appCtx.putPrefString(prefKey, GSON.toJson(trimmed))
        } else {
            appCtx.putPrefString(prefKey, GSON.toJson(map))
        }
    }

    private fun checkWeekMonth(): CheckResult {
        val weekCap = weekLimit()
        val week = appCtx.getPrefInt(PreferKey.aiWebSearchWeekCount, 0)
        if (week >= weekCap) {
            return CheckResult.Blocked(
                layer = "week",
                message = "Weekly web search limit reached ($weekCap)",
            )
        }
        val monthCap = monthLimit()
        val month = appCtx.getPrefInt(PreferKey.aiWebSearchMonthCount, 0)
        if (month >= monthCap) {
            return CheckResult.Blocked(
                layer = "month",
                message = "Monthly web search limit reached ($monthCap)",
            )
        }
        return CheckResult.Ok
    }

    private fun bumpWeekMonth() {
        appCtx.putPrefInt(
            PreferKey.aiWebSearchWeekCount,
            appCtx.getPrefInt(PreferKey.aiWebSearchWeekCount, 0) + 1,
        )
        appCtx.putPrefInt(
            PreferKey.aiWebSearchMonthCount,
            appCtx.getPrefInt(PreferKey.aiWebSearchMonthCount, 0) + 1,
        )
    }

    private fun searchConversationCounts(): Map<String, Int> =
        loadConversationCounts(PreferKey.aiWebSearchConvCounts)

    private fun readConversationCounts(): Map<String, Int> =
        loadConversationCounts(PreferKey.aiWebReadConvCounts)

    private fun loadConversationCounts(prefKey: String): Map<String, Int> {
        val raw = appCtx.getPrefString(prefKey).orEmpty()
        if (raw.isBlank()) return emptyMap()
        return GSON.fromJsonObject<Map<String, Double>>(raw).getOrNull()
            ?.mapValues { it.value.toInt() }
            ?: emptyMap()
    }

    private fun rotatePeriodsIfNeeded() {
        val monthPeriod = currentMonthPeriod()
        if (appCtx.getPrefString(PreferKey.aiWebSearchMonthPeriod) != monthPeriod) {
            appCtx.putPrefString(PreferKey.aiWebSearchMonthPeriod, monthPeriod)
            appCtx.putPrefInt(PreferKey.aiWebSearchMonthCount, 0)
        }
        val weekPeriod = currentWeekPeriod()
        if (appCtx.getPrefString(PreferKey.aiWebSearchWeekPeriod) != weekPeriod) {
            appCtx.putPrefString(PreferKey.aiWebSearchWeekPeriod, weekPeriod)
            appCtx.putPrefInt(PreferKey.aiWebSearchWeekCount, 0)
        }
        val dayPeriod = currentDayPeriod()
        if (appCtx.getPrefString(PreferKey.aiWebSearchTestDayPeriod) != dayPeriod) {
            appCtx.putPrefString(PreferKey.aiWebSearchTestDayPeriod, dayPeriod)
            appCtx.putPrefInt(PreferKey.aiWebSearchTestDayCount, 0)
        }
    }

    private fun currentMonthPeriod(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    private fun currentWeekPeriod(): String {
        val c = Calendar.getInstance()
        c.firstDayOfWeek = Calendar.MONDAY
        return "%04d-W%02d".format(c.get(Calendar.YEAR), c.get(Calendar.WEEK_OF_YEAR))
    }

    private fun currentDayPeriod(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH),
        )
    }

    companion object {
        const val DEFAULT_CONVERSATION_LIMIT = 10
        const val DEFAULT_READ_CONVERSATION_LIMIT = 20
        const val DEFAULT_WEEK_LIMIT = 249
        const val DEFAULT_MONTH_LIMIT = 996
        const val DEFAULT_TEST_DAY_LIMIT = 5

        /** @deprecated Use DEFAULT_* / AppConfig; kept for any external references. */
        const val CONVERSATION_LIMIT = DEFAULT_CONVERSATION_LIMIT
        const val READ_CONVERSATION_LIMIT = DEFAULT_READ_CONVERSATION_LIMIT
        const val WEEK_LIMIT = DEFAULT_WEEK_LIMIT
        const val MONTH_LIMIT = DEFAULT_MONTH_LIMIT
        const val TEST_DAY_LIMIT = DEFAULT_TEST_DAY_LIMIT
    }
}
