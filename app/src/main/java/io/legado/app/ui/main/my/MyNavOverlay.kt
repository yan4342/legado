package io.legado.app.ui.main.my

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.Fragment
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.data.appDb
import io.legado.app.data.repository.AiUsageRepository
import io.legado.app.ui.book.readRecord.AiUsageOverviewScreen
import io.legado.app.ui.book.readRecord.AiUsageOverviewState
import io.legado.app.ui.book.readRecord.ReadPeriod
import io.legado.app.ui.book.readRecord.ReadRecordOverviewScreen
import io.legado.app.ui.book.readRecord.ReadRecordOverviewState
import io.legado.app.ui.book.readRecord.ReadRecordScreen
import io.legado.app.ui.book.readRecord.ReadRecordViewModel
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.dict.rule.ai.AiDictRuleEditActivity
import io.legado.app.ui.dict.rule.ai.AiDictRuleListScreen
import io.legado.app.ui.dict.rule.ai.AiDictRuleViewModel
import io.legado.app.ui.widget.ReadBarChartView
import io.legado.app.ui.widget.ReadVerticalBarChartView
import io.legado.app.utils.startActivity
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

// MyNavOverlay() and MyRootScreen() removed — MyFragment is deleted and
// the “My” tab is now rendered by BottomNavScreen (MyScreen composable directly).
// Only the reusable route composables below remain, used by MainNavGraph.

/** 阅读记录路由内容。零参 ReadRecordViewModel，沿用 remember 方式。 */
@Composable
internal fun ReadRecordRoute(
    onBack: () -> Unit,
    onOverview: () -> Unit,
    onAiOverview: () -> Unit,
    onNavigateToBook: (String, String) -> Unit,
    onNavigateToAiChat: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ReadRecordViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReadRecordScreen(
        state = state,
        onIntent = viewModel::onIntent,
        effects = viewModel.effects,
        onBack = onBack,
        onNavigateToBook = onNavigateToBook,
        onNavigateToSearch = { bookName ->
            (context as? MainActivity)?.navigateToSearch(key = bookName)
        },
        onOverviewClick = onOverview,
        onAiOverviewClick = onAiOverview,
        onNavigateToAiChat = onNavigateToAiChat,
    )
}

/** AI 词典路由内容。AiDictRuleViewModel(application) 借 Fragment 的 ViewModelStore。 */
@Composable
internal fun AiDictRuleRoute(
    fragment: Fragment? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    val viewModel: AiDictRuleViewModel = if (fragment != null) {
        viewModel(viewModelStoreOwner = fragment)
    } else {
        viewModel(
            factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )
    }
    val rules by viewModel.rulesFlow.collectAsState()
    AiDictRuleListScreen(
        rules = rules,
        onAdd = { context.startActivity<AiDictRuleEditActivity>() },
        onEdit = { name ->
            context.startActivity<AiDictRuleEditActivity> {
                putExtra("name", name)
            }
        },
        onToggleEnabled = viewModel::toggleEnabled,
        onDelete = viewModel::delete,
        onBack = onBack,
    )
}

/**
 * 阅读记录总览路由内容。无 ViewModel，本地 state + 协程加载，逻辑迁自 ReadRecordOverviewActivity。
 */
@Composable
internal fun ReadRecordOverviewRoute(
    onBack: () -> Unit,
    onBookClick: (String, String) -> Unit,
) {
    var state by remember { mutableStateOf(ReadRecordOverviewState()) }
    val scope = rememberCoroutineScope()

    fun load(period: ReadPeriod, refDate: LocalDate) {
        scope.launch {
            state = state.copy(period = period, referenceDate = refDate)
            val cal = Calendar.getInstance()
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = fmt.format(cal.time)
            val refInstant = refDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
            cal.time = Date.from(refInstant)

            val startDate = when (period) {
                ReadPeriod.DAY -> { cal.add(Calendar.DAY_OF_YEAR, -30); fmt.format(cal.time) }
                ReadPeriod.WEEK -> { cal.add(Calendar.DAY_OF_YEAR, -7); fmt.format(cal.time) }
                ReadPeriod.MONTH -> { cal.add(Calendar.MONTH, -1); fmt.format(cal.time) }
                ReadPeriod.YEAR -> { cal.add(Calendar.YEAR, -1); fmt.format(cal.time) }
            }
            val endDate = fmt.format(Date.from(refInstant))

            val totalTime = withContext(IO) { appDb.readRecordDao.allTime }
            val showRecords = withContext(IO) { appDb.readRecordDao.allShow }
            val dailyRecords = withContext(IO) { appDb.dailyReadRecordDao.sumDailyByDateRange(startDate, endDate) }

            val todayTime = withContext(IO) { appDb.dailyReadRecordDao.sumByDateRange(today, today) }
            val readingDays = withContext(IO) {
                var count = 0
                val c = Calendar.getInstance()
                val f = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                while (true) {
                    val d = f.format(c.time)
                    if (appDb.dailyReadRecordDao.sumByDateRange(d, d) > 0) {
                        count++; c.add(Calendar.DAY_OF_YEAR, -1)
                    } else break
                }
                count
            }

            // Bar chart data per period
            val dailyBarItems = when (period) {
                ReadPeriod.DAY -> {
                    val dateStr = fmt.format(Date.from(refInstant))
                    val hourlyRecords = withContext(IO) { appDb.hourlyReadRecordDao.sumHourlyByDateHourRange("$dateStr 00", "$dateStr 23") }
                    hourlyRecords.filter { it.readTime > 0 }.map { ReadVerticalBarChartView.BarItem(it.dateHour.takeLast(2) + "时", it.readTime) }
                }
                ReadPeriod.WEEK -> {
                    val weekStart = refDate.with(java.time.DayOfWeek.MONDAY)
                    val weekEnd = refDate.with(java.time.DayOfWeek.SUNDAY)
                    val wsInst = weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant()
                    val weInst = weekEnd.atStartOfDay(ZoneId.systemDefault()).toInstant()
                    val weekRecords = withContext(IO) { appDb.dailyReadRecordDao.sumDailyByDateRange(fmt.format(Date.from(wsInst)), fmt.format(Date.from(weInst))) }
                    val weekDayLabels = arrayOf("一", "二", "三", "四", "五", "六", "日")
                    val weekMap = weekRecords.associateBy { LocalDate.parse(it.date).dayOfWeek.value }
                    (1..7).map { day -> ReadVerticalBarChartView.BarItem(weekDayLabels[day - 1], weekMap[day]?.readTime ?: 0) }
                }
                ReadPeriod.MONTH -> {
                    dailyRecords.filter { it.readTime > 0 }.map { ReadVerticalBarChartView.BarItem(it.date.takeLast(2) + "日", it.readTime) }
                }
                ReadPeriod.YEAR -> {
                    val yearStart = "${refDate.year}-01-01"
                    val yearEnd = "${refDate.year}-12-31"
                    val yearRecords = withContext(IO) { appDb.dailyReadRecordDao.sumDailyByDateRange(yearStart, yearEnd) }
                    val monthMap = yearRecords.groupBy { it.date.substring(5, 7) }.mapValues { (_, records) -> records.sumOf { it.readTime } }
                    (1..12).map { m -> ReadVerticalBarChartView.BarItem("${m}月", monthMap[String.format("%02d", m)] ?: 0) }
                }
            }

            val topBooks = showRecords.sortedByDescending { it.readTime }.take(20)
            val topBookBarItems = topBooks.map { ReadBarChartView.BarItem(it.bookName, it.readTime) }

            state = state.copy(
                totalTime = totalTime, readingDays = readingDays, totalBooks = showRecords.size,
                todayTime = todayTime, topBooks = topBooks,
                dailyBarItems = dailyBarItems, topBookBarItems = topBookBarItems,
                heatmapData = dailyRecords.associate { it.date to it.readTime },
            )
        }
    }

    fun prevDate() {
        val ref = state.referenceDate
        load(state.period, when (state.period) {
            ReadPeriod.DAY -> ref.minusDays(1)
            ReadPeriod.WEEK -> ref.minusWeeks(1)
            ReadPeriod.MONTH -> ref.minusMonths(1)
            ReadPeriod.YEAR -> ref.minusYears(1)
        })
    }

    fun nextDate() {
        val ref = state.referenceDate
        load(state.period, when (state.period) {
            ReadPeriod.DAY -> ref.plusDays(1)
            ReadPeriod.WEEK -> ref.plusWeeks(1)
            ReadPeriod.MONTH -> ref.plusMonths(1)
            ReadPeriod.YEAR -> ref.plusYears(1)
        })
    }

    // 首次加载
    LaunchedEffect(Unit) { load(state.period, state.referenceDate) }

    ReadRecordOverviewScreen(
        state = state,
        onPeriodChange = { load(it, state.referenceDate) },
        onPrevDate = { prevDate() },
        onNextDate = { nextDate() },
        onBack = onBack,
        onBookClick = onBookClick,
    )
}

/** AI 使用总览路由内容。 */
@Composable
internal fun AiUsageOverviewRoute(onBack: () -> Unit) {
    var state by remember { mutableStateOf(AiUsageOverviewState()) }
    val scope = rememberCoroutineScope()
    val repository = remember { AiUsageRepository() }

    fun load(period: ReadPeriod, refDate: LocalDate) {
        scope.launch {
            state = state.copy(period = period, referenceDate = refDate)
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val refInstant = refDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endDate = fmt.format(Date.from(refInstant))

            val (statsStartDate, statsEndDate) = when (period) {
                ReadPeriod.DAY -> endDate to endDate
                ReadPeriod.WEEK -> {
                    val weekStart = refDate.with(java.time.DayOfWeek.MONDAY)
                    val weekEnd = refDate.with(java.time.DayOfWeek.SUNDAY)
                    fmt.format(Date.from(weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant())) to
                        fmt.format(Date.from(weekEnd.atStartOfDay(ZoneId.systemDefault()).toInstant()))
                }
                ReadPeriod.MONTH -> {
                    val monthStart = refDate.withDayOfMonth(1)
                    val monthEnd = refDate.withDayOfMonth(refDate.lengthOfMonth())
                    fmt.format(Date.from(monthStart.atStartOfDay(ZoneId.systemDefault()).toInstant())) to
                        fmt.format(Date.from(monthEnd.atStartOfDay(ZoneId.systemDefault()).toInstant()))
                }
                ReadPeriod.YEAR -> "${refDate.year}-01-01" to "${refDate.year}-12-31"
            }

            val (heatmapStartDate, heatmapEndDate) = when (period) {
                ReadPeriod.DAY -> {
                    val cal = Calendar.getInstance()
                    cal.time = Date.from(refInstant)
                    cal.add(Calendar.DAY_OF_YEAR, -30)
                    fmt.format(cal.time) to endDate
                }
                else -> statsStartDate to statsEndDate
            }

            val rangeSummary = withContext(IO) { repository.sumByDateRange(statsStartDate, statsEndDate) }
            val statsDailyRecords = withContext(IO) { repository.sumDailyByDateRange(statsStartDate, statsEndDate) }
            val heatmapRecords = withContext(IO) { repository.sumDailyByDateRange(heatmapStartDate, heatmapEndDate) }
            val usageDays = when (period) {
                ReadPeriod.DAY -> if (rangeSummary.totalTokens > 0) 1 else 0
                else -> statsDailyRecords.count { it.totalTokens > 0 }
            }
            val todayTokens = when (period) {
                ReadPeriod.DAY -> rangeSummary.totalTokens
                else -> withContext(IO) { repository.todaySummary().totalTokens }
            }

            val dailyBarItems = when (period) {
                ReadPeriod.DAY -> {
                    val hourly = withContext(IO) { repository.sumHourlyByDate(endDate) }
                    hourly.filter { it.totalTokens > 0 }.map { record ->
                        ReadVerticalBarChartView.BarItem(record.hour.takeLast(2) + "时", record.totalTokens)
                    }
                }
                ReadPeriod.WEEK -> {
                    val weekStart = refDate.with(java.time.DayOfWeek.MONDAY)
                    val weekEnd = refDate.with(java.time.DayOfWeek.SUNDAY)
                    val wsInst = weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant()
                    val weInst = weekEnd.atStartOfDay(ZoneId.systemDefault()).toInstant()
                    val weekRecords = withContext(IO) {
                        repository.sumDailyByDateRange(fmt.format(Date.from(wsInst)), fmt.format(Date.from(weInst)))
                    }
                    val weekDayLabels = arrayOf("一", "二", "三", "四", "五", "六", "日")
                    val weekMap = weekRecords.associateBy { LocalDate.parse(it.date).dayOfWeek.value }
                    (1..7).map { day -> ReadVerticalBarChartView.BarItem(weekDayLabels[day - 1], weekMap[day]?.totalTokens ?: 0) }
                }
                ReadPeriod.MONTH -> statsDailyRecords.filter { it.totalTokens > 0 }
                    .map { ReadVerticalBarChartView.BarItem(it.date.takeLast(2) + "日", it.totalTokens) }
                ReadPeriod.YEAR -> {
                    val yearRecords = withContext(IO) { repository.sumDailyByDateRange(statsStartDate, statsEndDate) }
                    val monthMap = yearRecords.groupBy { it.date.substring(5, 7) }.mapValues { (_, records) -> records.sumOf { it.totalTokens } }
                    (1..12).map { m -> ReadVerticalBarChartView.BarItem("${m}月", monthMap[String.format("%02d", m)] ?: 0) }
                }
            }

            val topModels = withContext(IO) { repository.sumByModelInRange(statsStartDate, statsEndDate).take(20) }
            val modelTotal = topModels.sumOf { it.totalTokens }.coerceAtLeast(1)
            val topModelBarItems = topModels.map { model ->
                val name = model.modelName.ifBlank { model.modelId }
                val pct = model.totalTokens * 100 / modelTotal
                ReadBarChartView.BarItem("$name ($pct%)", model.totalTokens)
            }

            state = state.copy(
                totalTokens = rangeSummary.totalTokens,
                promptTokens = rangeSummary.promptTokens,
                completionTokens = rangeSummary.completionTokens,
                cacheHitTokens = rangeSummary.cacheHitTokens,
                generatedChars = rangeSummary.generatedChars,
                callCount = rangeSummary.callCount,
                usageDays = usageDays,
                todayTokens = todayTokens,
                dailyBarItems = dailyBarItems,
                topModelBarItems = topModelBarItems,
                heatmapData = heatmapRecords.associate { it.date to it.totalTokens },
                hasEstimated = withContext(IO) { repository.hasEstimatedInRange(statsStartDate, statsEndDate) },
            )
        }
    }

    fun prevDate() {
        val ref = state.referenceDate
        load(state.period, when (state.period) {
            ReadPeriod.DAY -> ref.minusDays(1)
            ReadPeriod.WEEK -> ref.minusWeeks(1)
            ReadPeriod.MONTH -> ref.minusMonths(1)
            ReadPeriod.YEAR -> ref.minusYears(1)
        })
    }

    fun nextDate() {
        val ref = state.referenceDate
        load(state.period, when (state.period) {
            ReadPeriod.DAY -> ref.plusDays(1)
            ReadPeriod.WEEK -> ref.plusWeeks(1)
            ReadPeriod.MONTH -> ref.plusMonths(1)
            ReadPeriod.YEAR -> ref.plusYears(1)
        })
    }

    LaunchedEffect(Unit) { load(state.period, state.referenceDate) }

    AiUsageOverviewScreen(
        state = state,
        onPeriodChange = { load(it, state.referenceDate) },
        onPrevDate = { prevDate() },
        onNextDate = { nextDate() },
        onBack = onBack,
    )
}
