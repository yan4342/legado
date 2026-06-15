package io.legado.app.ui.book.readRecord

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.data.entities.ReadRecordShow
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.legadoCardBackgroundColor
import io.legado.app.ui.widget.ReadBarChartView
import io.legado.app.ui.widget.ReadHeatmapView
import io.legado.app.ui.widget.ReadVerticalBarChartView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException

enum class ReadPeriod(val label: String) { DAY("日"), WEEK("周"), MONTH("月"), YEAR("年"), ALL("总") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadRecordOverviewScreen(
    state: ReadRecordOverviewState,
    onPeriodChange: (ReadPeriod) -> Unit,
    onPrevDate: () -> Unit,
    onNextDate: () -> Unit,
    onBack: () -> Unit,
    onBookClick: (String, String) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(ReadPeriod.entries.indexOf(state.period)) }

    // 预测性返回手势：跟手滑动，系统处理跨 Activity 返回动画
    var backProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler { progress ->
        try {
            progress.collect { event -> backProgress = event.progress }
            onBack()
        } catch (_: CancellationException) {
            // 手势取消
        } finally {
            backProgress = 0f
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("阅读总览",
                    color = if (AppConfig.isEInkMode) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                )},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (AppConfig.isEInkMode) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回", tint = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary) } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            // Period tabs — fixed width for short labels
            item(key = "tabs") {
                TabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                    ReadPeriod.entries.forEachIndexed { i, p ->
                        Tab(selected = selectedTab == i, onClick = { selectedTab = i; onPeriodChange(p) }, text = { Text(p.label) })
                    }
                }
            }

            // DateNavigator — MD3 style prev/next with animated date text
            if (state.period != ReadPeriod.ALL) {
                item(key = "date_nav") {
                    DateNavigator(period = state.period, referenceDate = state.referenceDate, onPrev = onPrevDate, onNext = onNextDate)
                }
            }

            // Daily vertical bar chart — 竖柱状图
            if (state.dailyBarItems.isNotEmpty()) {
                item(key = "bar_chart") {
                    ChartCard("阅读时长分布") {
                        AndroidView(
                            factory = { ctx -> ReadVerticalBarChartView(ctx).apply { setData(state.dailyBarItems) } },
                            update = { it.setData(state.dailyBarItems) },
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                        )
                    }
                }
            }

            // Stats grid
            item(key = "stats") {
                StatsGridCard(state)
            }

            // Heatmap
            if (state.heatmapData.isNotEmpty()) {
                item(key = "heatmap") {
                    ChartCard("阅读热力图") {
                        AndroidView(
                            factory = { ctx -> ReadHeatmapView(ctx).apply { setData(state.heatmapData) } },
                            update = { it.setData(state.heatmapData) },
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                        )
                    }
                }
            }

            // Top books horizontal bar chart — 横柱状图
            if (state.topBookBarItems.isNotEmpty()) {
                item(key = "top_chart") {
                    ChartCard("阅读时长榜 Top ${state.topBookBarItems.size}") {
                        AndroidView(
                            factory = { ctx -> ReadBarChartView(ctx).apply { setData(state.topBookBarItems); onItemClick = { name -> onBookClick(name, "") } } },
                            update = { it.setData(state.topBookBarItems); it.onItemClick = { name -> onBookClick(name, "") } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

// ── DateNavigator — MD3 style ──

@Composable
private fun DateNavigator(period: ReadPeriod, referenceDate: LocalDate, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrev) {
            Icon(painterResource(R.drawable.ic_arrow_right), contentDescription = "前", modifier = Modifier.size(24.dp).graphicsLayer { scaleX = -1f } )
        }
        AnimatedContent(
            targetState = referenceDate,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "date",
        ) { date ->
            val text = when (period) {
                ReadPeriod.DAY -> date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
                ReadPeriod.WEEK -> {
                    val start = date.with(java.time.DayOfWeek.MONDAY)
                    val end = date.with(java.time.DayOfWeek.SUNDAY)
                    "${start.format(DateTimeFormatter.ofPattern("M.d"))} - ${end.format(DateTimeFormatter.ofPattern("M.d"))}"
                }
                ReadPeriod.MONTH -> date.format(DateTimeFormatter.ofPattern("yyyy年M月"))
                ReadPeriod.YEAR -> date.format(DateTimeFormatter.ofPattern("yyyy年"))
                else -> ""
            }
            Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        }
        TextButton(onClick = onNext) {
            Icon(painterResource(R.drawable.ic_arrow_right), contentDescription = "后", modifier = Modifier.size(24.dp))
        }
    }
}

// ── StatsGridCard ──

@Composable
private fun StatsGridCard(state: ReadRecordOverviewState) {
    val cardBg = legadoCardBackgroundColor()
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(16.dp), color = cardBg, shadowElevation = 0.dp) {
        Column(Modifier.padding(16.dp)) {
            Text("阅读数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            val stats = listOf(
                "阅读时间" to ReadRecordFormatter.formatDuring(state.totalTime),
                "阅读天数" to "${state.readingDays}天",
                "累计读过" to "${state.totalBooks}本",
                "今日阅读" to ReadRecordFormatter.formatDuring(state.todayTime),
            )
            for (i in stats.indices step 2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCell(stats[i].first, stats[i].second, Modifier.weight(1f))
                    if (i + 1 < stats.size) StatCell(stats[i + 1].first, stats[i + 1].second, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                }
                if (i + 2 < stats.size) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun ChartCard(title: String, content: @Composable () -> Unit) {
    val cardBg = legadoCardBackgroundColor()
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(16.dp), color = cardBg, shadowElevation = 0.dp) {
        Column(Modifier.padding(12.dp)) { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp)); content() }
    }
}

// ── State ──

data class ReadRecordOverviewState(
    val period: ReadPeriod = ReadPeriod.DAY,
    val referenceDate: LocalDate = LocalDate.now(),
    val totalTime: Long = 0L,
    val readingDays: Int = 0,
    val totalBooks: Int = 0,
    val todayTime: Long = 0L,
    val topBooks: List<ReadRecordShow> = emptyList(),
    val dailyBarItems: List<ReadVerticalBarChartView.BarItem> = emptyList(),
    val topBookBarItems: List<ReadBarChartView.BarItem> = emptyList(),
    val heatmapData: Map<String, Long> = emptyMap(),
)
