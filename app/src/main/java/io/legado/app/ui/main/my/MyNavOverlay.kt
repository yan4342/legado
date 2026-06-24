package io.legado.app.ui.main.my

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.book.readRecord.ReadPeriod
import io.legado.app.ui.book.readRecord.ReadRecordOverviewScreen
import io.legado.app.ui.book.readRecord.ReadRecordOverviewState
import io.legado.app.ui.book.readRecord.ReadRecordScreen
import io.legado.app.ui.book.readRecord.ReadRecordViewModel
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.dict.rule.ai.AiDictRuleEditActivity
import io.legado.app.ui.dict.rule.ai.AiDictRuleListScreen
import io.legado.app.ui.dict.rule.ai.AiDictRuleViewModel
import io.legado.app.ui.main.MainRouteAbout
import io.legado.app.ui.main.MainRouteAiDictRule
import io.legado.app.ui.main.MainRouteBackupConfig
import io.legado.app.ui.main.MainRouteCoverConfig
import io.legado.app.ui.main.MainRouteMy
import io.legado.app.ui.main.MainRouteOtherConfig
import io.legado.app.ui.main.MainRouteReadRecord
import io.legado.app.ui.main.MainRouteReadRecordOverview
import io.legado.app.ui.main.MainRouteThemeConfig
import io.legado.app.ui.main.MainRouteWelcomeConfig
import io.legado.app.ui.widget.ReadBarChartView
import io.legado.app.ui.widget.ReadVerticalBarChartView
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefString
import io.legado.app.utils.startActivity
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * “我的”页的 Navigation 3 覆盖层。
 *
 * 把第一批 Compose 子页（阅读记录 / 阅读记录总览 / AI 词典）做成 NavDisplay 路由，
 * 返回时由 [NavDisplay] 的 predictivePopTransitionSpec 驱动 Compose pop 动画
 * （scale 0.8 + 从左滑入 + 淡入/淡出），对齐 MD3 的效果，MainActivity 整体不动。
 *
 * C 类纯 View 子页（书源 / 替换 / TxtToc / 字典 / 书签 / 文件管理 / 关于 / 设置）
 * 仍由 [MyScreen] 内部 startActivity 启动独立 Activity，与本覆盖层无关。
 */
@Composable
fun MyNavOverlay(
    fragment: Fragment,
    webServiceRunning: StateFlow<Boolean>,
    webServiceAddress: StateFlow<String>,
    onHelp: () -> Unit,
    onNavigateToBook: (String, String) -> Unit,
    // C 类纯 View 子页：仍启动独立 Activity（与 MD3 一致，无 Compose pop 动画）
    onBookSourceManage: () -> Unit,
    onTxtTocRuleManage: () -> Unit,
    onReplaceManage: () -> Unit,
    onDictRuleManage: () -> Unit,
    onBookmark: () -> Unit,
    onFileManage: () -> Unit,
    // Web 服务：就地处理，无导航
    onWebServiceChange: (Boolean) -> Unit,
    onWebServiceLongClick: () -> Unit,
    onExit: () -> Unit,
    aboutActions: AboutActions,
    otherConfigActions: OtherConfigActions,
    backupConfigActions: BackupConfigActions,
    themeConfigActions: ThemeConfigActions,
    welcomeConfigActions: WelcomeConfigActions,
    coverConfigActions: CoverConfigActions,
) {
    val context = LocalContext.current
    var themeMode by remember { mutableStateOf(context.getPrefString(PreferKey.themeMode, "0") ?: "0") }
    val backStack = rememberNavBackStack(MainRouteMy)
    val isRunning by webServiceRunning.collectAsStateWithLifecycle()
    val hostAddress by webServiceAddress.collectAsStateWithLifecycle()

    // 子页缩放透出「我的」页时，底栏作为「我的」页的一部分应保持可见。
    // 孙页（backStack.size > 1）时才隐藏底栏。
    // 子/孙页面禁用 ViewPager 滑动，防止左滑切到其它根页面。
    LaunchedEffect(backStack.size) {
        val nav = (fragment.activity as? io.legado.app.ui.main.MainActivity)
            ?.findViewById<android.view.View>(R.id.bottom_navigation_view)
        nav?.visibility = if (backStack.size > 1)
            android.view.View.GONE else android.view.View.VISIBLE
        io.legado.app.utils.postEvent(io.legado.app.constant.EventBus.DISABLE_VIEW_PAGER, backStack.size > 1)
    }

    // 消费系统导航栏 bottom inset：MainActivity 的底栏已占着屏幕底部并消费了该 inset，
    // 但老式 ViewPager 不向下传播 inset 消费，导致子页 Scaffold 又读到导航栏 inset、
    // 在底栏上方留出一道空白带。此处把底部导航栏 inset 消费掉，子页不再重复留白。
    Box(
        modifier = Modifier.consumeWindowInsets(WindowInsets.navigationBars),
    ) {
        NavDisplay(
        backStack = backStack,
        transitionSpec = {
            (slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(easing = FastOutSlowInEasing),
                initialOffset = { fullWidth -> fullWidth }
            ) + fadeIn(animationSpec = tween(easing = LinearOutSlowInEasing))) togetherWith
                (slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(easing = FastOutSlowInEasing),
                    targetOffset = { fullWidth -> fullWidth / 4 }
                ) + fadeOut(animationSpec = tween(easing = LinearOutSlowInEasing)))
        },
        popTransitionSpec = {
            (slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(easing = FastOutSlowInEasing),
                initialOffset = { fullWidth -> -fullWidth / 4 }
            ) + fadeIn(animationSpec = tween(easing = LinearOutSlowInEasing))) togetherWith
                (scaleOut(
                    targetScale = 0.8f,
                    animationSpec = tween(easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(easing = LinearOutSlowInEasing)))
        },
        predictivePopTransitionSpec = { _ ->
            if (context.getPrefBoolean(PreferKey.predictiveBack, true)) {
                (slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(easing = FastOutSlowInEasing),
                    initialOffset = { fullWidth -> -fullWidth / 4 }
                ) + fadeIn(animationSpec = tween(easing = LinearOutSlowInEasing))) togetherWith
                    (scaleOut(
                        targetScale = 0.8f,
                        animationSpec = tween(easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween()))
            } else {
                (slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(easing = FastOutSlowInEasing),
                    initialOffset = { fullWidth -> -fullWidth / 4 }
                ) + fadeIn(animationSpec = tween(easing = LinearOutSlowInEasing))) togetherWith
                    fadeOut(animationSpec = tween(easing = LinearOutSlowInEasing))
            }
        },
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<MainRouteMy> {
                MyRootScreen(
                    themeMode = themeMode,
                    isRunning = isRunning,
                    hostAddress = hostAddress,
                    onHelp = onHelp,
                    onBookSourceManage = onBookSourceManage,
                    onTxtTocRuleManage = onTxtTocRuleManage,
                    onReplaceManage = onReplaceManage,
                    onDictRuleManage = onDictRuleManage,
                    onAiDictRuleManage = { backStack.add(MainRouteAiDictRule) },
                    onBookmark = onBookmark,
                    onReadRecord = { backStack.add(MainRouteReadRecord) },
                    onBackupRestore = { backStack.add(MainRouteBackupConfig) },
                    onThemeSetting = { backStack.add(MainRouteThemeConfig) },
                    onThemeModeChange = { newValue ->
                        themeMode = newValue
                        context.putPrefString(PreferKey.themeMode, newValue)
                        ThemeConfig.applyDayNight(context)
                    },
                    onOtherSetting = { backStack.add(MainRouteOtherConfig) },
                    onWebServiceChange = onWebServiceChange,
                    onWebServiceLongClick = onWebServiceLongClick,
                    onFileManage = onFileManage,
                    onAbout = { backStack.add(MainRouteAbout) },
                    onExit = onExit,
                )
            }
            entry<MainRouteReadRecord> {
                ReadRecordRoute(
                    onBack = { backStack.removeLastOrNull() },
                    onOverview = { backStack.add(MainRouteReadRecordOverview) },
                    onNavigateToBook = onNavigateToBook,
                )
            }
            entry<MainRouteReadRecordOverview> {
                ReadRecordOverviewRoute(
                    onBack = { backStack.removeLastOrNull() },
                    onBookClick = { name, _ -> onNavigateToBook(name, "") },
                )
            }
            entry<MainRouteAiDictRule> {
                AiDictRuleRoute(
                    fragment = fragment,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<MainRouteAbout> {
                MyAboutRoute(
                    onBack = { backStack.removeLastOrNull() },
                    actions = aboutActions,
                )
            }
            entry<MainRouteOtherConfig> {
                MyOtherConfigRoute(
                    fragment = fragment,
                    onBack = { backStack.removeLastOrNull() },
                    actions = otherConfigActions,
                )
            }
            entry<MainRouteBackupConfig> {
                MyBackupConfigRoute(
                    fragment = fragment,
                    onBack = { backStack.removeLastOrNull() },
                    actions = backupConfigActions,
                )
            }
            entry<MainRouteThemeConfig> {
                MyThemeConfigRoute(
                    onBack = { backStack.removeLastOrNull() },
                    actions = themeConfigActions,
                    onWelcomeStyle = { backStack.add(MainRouteWelcomeConfig) },
                    onCoverConfig = { backStack.add(MainRouteCoverConfig) },
                )
            }
            entry<MainRouteWelcomeConfig> {
                MyWelcomeConfigRoute(
                    fragment = fragment,
                    onBack = { backStack.removeLastOrNull() },
                    actions = welcomeConfigActions,
                )
            }
            entry<MainRouteCoverConfig> {
                MyCoverConfigRoute(
                    fragment = fragment,
                    onBack = { backStack.removeLastOrNull() },
                    actions = coverConfigActions,
                )
            }
        },
        )
    }
}

/**
 * “我的”根路由：Scaffold + 顶栏（标题“我的” + 帮助按钮），内容为 [MyScreen]。
 * 顶栏用 Material3 原生 TopAppBar，配色与子页（阅读记录等）一致（primary 容器，E-Ink 退回 surface）。
 */
@Composable
private fun MyRootScreen(
    themeMode: String,
    isRunning: Boolean,
    hostAddress: String,
    onHelp: () -> Unit,
    onBookSourceManage: () -> Unit,
    onTxtTocRuleManage: () -> Unit,
    onReplaceManage: () -> Unit,
    onDictRuleManage: () -> Unit,
    onAiDictRuleManage: () -> Unit,
    onBookmark: () -> Unit,
    onReadRecord: () -> Unit,
    onBackupRestore: () -> Unit,
    onThemeSetting: () -> Unit,
    onThemeModeChange: (String) -> Unit,
    onOtherSetting: () -> Unit,
    onWebServiceChange: (Boolean) -> Unit,
    onWebServiceLongClick: () -> Unit,
    onFileManage: () -> Unit,
    onAbout: () -> Unit,
    onExit: () -> Unit,
) {
    val onPrimary = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onPrimary
    val container = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.primary
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my), color = onPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = container),
                actions = {
                    IconButton(onClick = onHelp) {
                        Icon(
                            painterResource(R.drawable.ic_help),
                            contentDescription = stringResource(R.string.help),
                            tint = onPrimary,
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        MyScreen(
            themeMode = themeMode,
            webServiceRunning = isRunning,
            webServiceAddress = hostAddress,
            onBookSourceManage = onBookSourceManage,
            onTxtTocRuleManage = onTxtTocRuleManage,
            onReplaceManage = onReplaceManage,
            onDictRuleManage = onDictRuleManage,
            onAiDictRuleManage = onAiDictRuleManage,
            onBookmark = onBookmark,
            onReadRecord = onReadRecord,
            onBackupRestore = onBackupRestore,
            onThemeSetting = onThemeSetting,
            onThemeModeChange = onThemeModeChange,
            onOtherSetting = onOtherSetting,
            onWebServiceChange = onWebServiceChange,
            onWebServiceLongClick = onWebServiceLongClick,
            onFileManage = onFileManage,
            onAbout = onAbout,
            onExit = onExit,
            modifier = Modifier.fillMaxSize().padding(contentPadding),
        )
    }
}

/** 阅读记录路由内容。零参 ReadRecordViewModel，沿用 remember 方式。 */
@Composable
private fun ReadRecordRoute(
    onBack: () -> Unit,
    onOverview: () -> Unit,
    onNavigateToBook: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val viewModel = remember { ReadRecordViewModel() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReadRecordScreen(
        state = state,
        onIntent = viewModel::onIntent,
        effects = viewModel.effects,
        onBack = onBack,
        onNavigateToBook = onNavigateToBook,
        onNavigateToSearch = { bookName ->
            SearchActivity.start(context, bookName)
        },
        onOverviewClick = onOverview,
    )
}

/** AI 词典路由内容。AiDictRuleViewModel(application) 借 Fragment 的 ViewModelStore。 */
@Composable
private fun AiDictRuleRoute(
    fragment: Fragment,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: AiDictRuleViewModel = viewModel(viewModelStoreOwner = fragment)
    val rules by viewModel.rulesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
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
private fun ReadRecordOverviewRoute(
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
