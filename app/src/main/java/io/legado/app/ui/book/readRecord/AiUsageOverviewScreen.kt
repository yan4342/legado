package io.legado.app.ui.book.readRecord

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.legadoCardBackgroundColor
import io.legado.app.ui.widget.ChartValueFormat
import io.legado.app.ui.widget.ReadBarChartView
import io.legado.app.ui.widget.ReadHeatmapView
import io.legado.app.ui.widget.ReadVerticalBarChartView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class AiUsageOverviewState(
    val period: ReadPeriod = ReadPeriod.DAY,
    val referenceDate: LocalDate = LocalDate.now(),
    val totalTokens: Long = 0L,
    val promptTokens: Long = 0L,
    val completionTokens: Long = 0L,
    val cacheHitTokens: Long = 0L,
    val generatedChars: Long = 0L,
    val callCount: Long = 0L,
    val usageDays: Int = 0,
    val todayTokens: Long = 0L,
    val dailyBarItems: List<ReadVerticalBarChartView.BarItem> = emptyList(),
    val topModelBarItems: List<ReadBarChartView.BarItem> = emptyList(),
    val heatmapData: Map<String, Long> = emptyMap(),
    val hasEstimated: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiUsageOverviewScreen(
    state: AiUsageOverviewState,
    onPeriodChange: (ReadPeriod) -> Unit,
    onPrevDate: () -> Unit,
    onNextDate: () -> Unit,
    onBack: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(ReadPeriod.entries.indexOf(state.period)) }

    LaunchedEffect(state.period) {
        selectedTab = ReadPeriod.entries.indexOf(state.period)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.ai_usage_overview_title),
                        color = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                            tint = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "tabs") {
                PrimaryTabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                    ReadPeriod.entries.forEachIndexed { i, p ->
                        Tab(
                            selected = selectedTab == i,
                            onClick = { selectedTab = i; onPeriodChange(p) },
                            text = { Text(p.label) },
                        )
                    }
                }
            }
            item(key = "date_nav") {
                AiDateNavigator(period = state.period, referenceDate = state.referenceDate, onPrev = onPrevDate, onNext = onNextDate)
            }
            item(key = "bar_chart") {
                AiChartCard(stringResource(R.string.ai_usage_chart_token_distribution)) {
                    if (state.dailyBarItems.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.ai_usage_chart_no_data),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        val items = state.dailyBarItems
                        val barCount = items.size
                        val gapDp = if (barCount > 20) 2 else 6
                        val chartMinDp = (8 + gapDp) * barCount + gapDp + 32
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val viewportWidth = maxWidth
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                AndroidView(
                                    factory = { ctx ->
                                        ReadVerticalBarChartView(ctx).apply {
                                            setValueFormat(ChartValueFormat.COMPACT_NUMBER)
                                            setData(items)
                                        }
                                    },
                                    update = {
                                        it.setValueFormat(ChartValueFormat.COMPACT_NUMBER)
                                        it.setData(items)
                                    },
                                    modifier = Modifier.width(maxOf(chartMinDp.dp, viewportWidth)).height(200.dp),
                                )
                            }
                        }
                    }
                }
            }
            item(key = "stats") { AiStatsGridCard(state) }
            if (state.heatmapData.isNotEmpty()) {
                item(key = "heatmap") {
                    AiChartCard(stringResource(R.string.ai_usage_chart_heatmap)) {
                        AndroidView(
                            factory = { ctx -> ReadHeatmapView(ctx).apply { setData(state.heatmapData) } },
                            update = { it.setData(state.heatmapData) },
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                        )
                    }
                }
            }
            if (state.topModelBarItems.isNotEmpty()) {
                item(key = "top_chart") {
                    AiChartCard(stringResource(R.string.ai_usage_chart_model_top, state.topModelBarItems.size)) {
                        AndroidView(
                            factory = { ctx ->
                                ReadBarChartView(ctx).apply {
                                    setValueFormat(ChartValueFormat.COMPACT_NUMBER)
                                    setLabelWidth(160f)
                                    setTimeLabelWidth(48f)
                                    setData(state.topModelBarItems)
                                }
                            },
                            update = {
                                it.setValueFormat(ChartValueFormat.COMPACT_NUMBER)
                                it.setLabelWidth(160f)
                                it.setTimeLabelWidth(48f)
                                it.setData(state.topModelBarItems)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

//日期导航
@Composable
private fun AiDateNavigator(period: ReadPeriod, referenceDate: LocalDate, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrev) {
            Icon(painterResource(R.drawable.ic_arrow_right), contentDescription = null, modifier = Modifier.size(24.dp).graphicsLayer { scaleX = -1f })
        }
        AnimatedContent(targetState = referenceDate, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "date") { date ->
            val text = when (period) {
                ReadPeriod.DAY -> date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
                ReadPeriod.WEEK -> {
                    val start = date.with(java.time.DayOfWeek.MONDAY)
                    val end = date.with(java.time.DayOfWeek.SUNDAY)
                    "${start.format(DateTimeFormatter.ofPattern("M.d"))} - ${end.format(DateTimeFormatter.ofPattern("M.d"))}"
                }
                ReadPeriod.MONTH -> date.format(DateTimeFormatter.ofPattern("yyyy年M月"))
                ReadPeriod.YEAR -> date.format(DateTimeFormatter.ofPattern("yyyy年"))
            }
            Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        }
        TextButton(onClick = onNext) {
            Icon(painterResource(R.drawable.ic_arrow_right), contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

//统计项卡片
@Composable
private fun AiStatsGridCard(state: AiUsageOverviewState) {
    val cardBg = legadoCardBackgroundColor()
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp), color = cardBg, shadowElevation = 0.dp) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.ai_usage_stats_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (state.hasEstimated) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.ai_usage_estimated_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            val totalTokenValue = buildString {
                append(AiUsageFormatter.formatTokens(state.totalTokens))
                if (state.hasEstimated) append(" ~")
            }
            val todayTokenLabel = if (state.period == ReadPeriod.DAY) {
                stringResource(R.string.ai_usage_stat_day_tokens)
            } else {
                stringResource(R.string.ai_usage_stat_today_tokens)
            }
            val stats = listOf(
                stringResource(R.string.ai_usage_stat_total_tokens) to totalTokenValue,
                stringResource(R.string.ai_usage_stat_prompt) to AiUsageFormatter.formatTokens(state.promptTokens),
                stringResource(R.string.ai_usage_stat_completion) to AiUsageFormatter.formatTokens(state.completionTokens),
                stringResource(R.string.ai_usage_stat_cache_hit) to AiUsageFormatter.formatTokens(state.cacheHitTokens),
                stringResource(R.string.ai_usage_stat_generated_chars) to AiUsageFormatter.formatChars(state.generatedChars),
                todayTokenLabel to AiUsageFormatter.formatTokens(state.todayTokens),
                stringResource(R.string.ai_usage_stat_active_days) to state.usageDays.toString(),
                stringResource(R.string.ai_usage_stat_call_count) to state.callCount.toString(),
            )
            for (i in stats.indices step 2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AiStatCell(stats[i].first, stats[i].second, Modifier.weight(1f))
                    if (i + 1 < stats.size) AiStatCell(stats[i + 1].first, stats[i + 1].second, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                }
                if (i + 2 < stats.size) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

//统计项单元格
@Composable
private fun AiStatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

//图表卡片
@Composable
private fun AiChartCard(title: String, content: @Composable () -> Unit) {
    val cardBg = legadoCardBackgroundColor()
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp), color = cardBg, shadowElevation = 0.dp) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
