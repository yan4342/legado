package io.legado.app.ui.about

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.legado.app.data.appDb
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.book.readRecord.ReadPeriod
import io.legado.app.ui.book.readRecord.ReadRecordOverviewScreen
import io.legado.app.ui.book.readRecord.ReadRecordOverviewState
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.widget.ReadBarChartView
import io.legado.app.ui.widget.ReadVerticalBarChartView
import io.legado.app.utils.startActivityForBook
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

class ReadRecordOverviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        window.statusBarColor = ThemeStore.primaryColor(this)

        setContent {
            LegadoTheme {
                var state by remember { mutableStateOf(ReadRecordOverviewState()) }

                fun load(period: ReadPeriod, refDate: LocalDate) {
                    lifecycleScope.launch {
                        state = state.copy(period = period, referenceDate = refDate)
                        val cal = Calendar.getInstance()
                        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val today = fmt.format(cal.time)
                        val refInstant = refDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                        cal.time = java.util.Date.from(refInstant)

                        val startDate = when (period) {
                            ReadPeriod.DAY -> { cal.add(Calendar.DAY_OF_YEAR, -30); fmt.format(cal.time) }
                            ReadPeriod.WEEK -> { cal.add(Calendar.DAY_OF_YEAR, -7); fmt.format(cal.time) }
                            ReadPeriod.MONTH -> { cal.add(Calendar.MONTH, -1); fmt.format(cal.time) }
                            ReadPeriod.YEAR -> { cal.add(Calendar.YEAR, -1); fmt.format(cal.time) }
                        }
                        val endDate = fmt.format(java.util.Date.from(refInstant))

                        val totalTime = withContext(IO) { appDb.readRecordDao.allTime }
                        val showRecords = withContext(IO) { appDb.readRecordDao.allShow }
                        val dailyRecords = withContext(IO) { appDb.dailyReadRecordDao.sumDailyByDateRange(startDate, endDate) }

                        val todayTime = withContext(IO) { appDb.dailyReadRecordDao.sumByDateRange(today, today) }
                        val readingDays = withContext(IO) {
                            var count = 0; val c = Calendar.getInstance(); val f = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            while (true) { val d = f.format(c.time); if (appDb.dailyReadRecordDao.sumByDateRange(d, d) > 0) { count++; c.add(Calendar.DAY_OF_YEAR, -1) } else break }; count
                        }

                        // Bar chart data per period
                        val dailyBarItems = when (period) {
                            ReadPeriod.DAY -> {
                                // Hourly bars for the reference date
                                val dateStr = fmt.format(java.util.Date.from(refInstant))
                                val hourlyRecords = withContext(IO) { appDb.hourlyReadRecordDao.sumHourlyByDateHourRange("$dateStr 00", "$dateStr 23") }
                                hourlyRecords.filter { it.readTime > 0 }.map { ReadVerticalBarChartView.BarItem(it.dateHour.takeLast(2) + "时", it.readTime) }
                            }
                            ReadPeriod.WEEK -> {
                                val weekStart = refDate.with(java.time.DayOfWeek.MONDAY)
                                val weekEnd = refDate.with(java.time.DayOfWeek.SUNDAY)
                                val wsInst = weekStart.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                                val weInst = weekEnd.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                                val weekRecords = withContext(IO) { appDb.dailyReadRecordDao.sumDailyByDateRange(fmt.format(java.util.Date.from(wsInst)), fmt.format(java.util.Date.from(weInst))) }
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

                        state = state.copy(totalTime = totalTime, readingDays = readingDays, totalBooks = showRecords.size,
                            todayTime = todayTime, topBooks = topBooks, dailyBarItems = dailyBarItems, topBookBarItems = topBookBarItems, heatmapData = dailyRecords.associate { it.date to it.readTime })
                    }
                }

                fun prevDate() { val ref = state.referenceDate; load(state.period, when (state.period) { ReadPeriod.DAY -> ref.minusDays(1); ReadPeriod.WEEK -> ref.minusWeeks(1); ReadPeriod.MONTH -> ref.minusMonths(1); ReadPeriod.YEAR -> ref.minusYears(1) }) }
                fun nextDate() { val ref = state.referenceDate; load(state.period, when (state.period) { ReadPeriod.DAY -> ref.plusDays(1); ReadPeriod.WEEK -> ref.plusWeeks(1); ReadPeriod.MONTH -> ref.plusMonths(1); ReadPeriod.YEAR -> ref.plusYears(1) }) }

                load(state.period, state.referenceDate)

                ReadRecordOverviewScreen(
                    state = state, onPeriodChange = { load(it, state.referenceDate) },
                    onPrevDate = { prevDate() }, onNextDate = { nextDate() }, onBack = { finish() },
                    onBookClick = { name, _ ->
                        lifecycleScope.launch { val b = withContext(IO) { appDb.bookDao.findByName(name).firstOrNull() }; if (b != null) startActivityForBook(b) else SearchActivity.start(this@ReadRecordOverviewActivity, name) }
                    },
                )
            }
        }
    }
}
