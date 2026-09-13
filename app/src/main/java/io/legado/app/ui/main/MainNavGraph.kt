package io.legado.app.ui.main

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.search.SearchViewModel
import io.legado.app.ui.book.read.ReadBookEntry
import io.legado.app.ui.book.read.ReadBookRouteState
import io.legado.app.ui.book.toc.TocRouteState
import io.legado.app.ui.common.compose.LegadoWaitDialog
import kotlinx.coroutines.awaitCancellation

// 各 entry 的内容体已迁至 MainNavGraphEntries.kt（按域分组的顶层 @Composable），
// 配置域胶水状态已迁至 MainConfigRouteActions.kt，
// 配置域辅助函数已迁至 MainNavGraphConfigHelpers.kt。

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalComposeUiApi::class)
@Composable
fun MainNavHost(
    onNavigateToRouteSetter: ((MainRoute) -> Unit) -> Unit,
    onBackAtHome: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }
    val backStack = rememberNavBackStack(MainRouteHome)
    val scope = rememberCoroutineScope()

    var onNavigateToRoute: (MainRoute) -> Unit by remember { mutableStateOf({}) }

    // 配置域路由的胶水状态（颜色选择 / 背景封面欢迎图 / 备份恢复等）。
    val actions = rememberMainConfigRouteActions()
    LegadoWaitDialog(actions.backupWaitDialog)

    // 目录路由结果回传（TocEntry → BookInfoEntry）的 pending-holder。
    val tocRouteState = remember { TocRouteState() }

    // 阅读路由结果回传（ReadBookEntry → BookInfoEntry）的 pending-holder。
    val readBookRouteState = remember { ReadBookRouteState() }

    // 统一回退回调，NavDisplay.onBack 和各条目 onBack 共用。
    // 当栈只有首页时，委托给 MainActivity 的双击退出逻辑；
    // 否则从栈中移除当前条目，走 NavDisplay 的 pop 动画。
    val onNavigateBack: () -> Unit = {
        if (backStack.size > 1) {
            val a = activity
            if (a != null) {
                MainNavigator.navigateBack(a, backStack)
            } else {
                backStack.removeLastOrNull()
            }
        } else {
            onBackAtHome()
        }
    }

    onNavigateToRoute = { route ->
        MainNavigator.navigateToRoute(backStack, route)
    }

    // 原始入栈（绕过 MainNavigator 的防抖/白名单），仅用于迁移前就使用
    // backStack.add 的条目回调（如 ReadRecord 的概览/AI 用量跳转），保持行为不变。
    val pushRoute: (MainRoute) -> Unit = { backStack.add(it) }

    // 导出回调给 MainActivity（供 navigateToSearch / onNewIntent 等调用）
    SideEffect {
        onNavigateToRouteSetter(onNavigateToRoute)
    }

    // Cold start: honor MainIntent / deep-link startRoute extras.
    LaunchedEffect(Unit) {
        val start = MainNavigator.resolveStartRoute(activity?.intent)
        if (start is MainRoute && start !is MainRouteHome) {
            MainNavigator.navigateToRoute(backStack, start)
        }
    }

    // 栈变化后重置防抖守卫，使下一次按钮返回能再次触发。
    LaunchedEffect(backStack) {
        snapshotFlow { backStack.toList() }.collect {
            MainNavigator.onBackStackChanged()
        }
    }

    // 搜索页在返回栈中时：详情页继续搜索，进入阅读页（Activity 失焦）时暂停。
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val hasSearch = backStack.any { it is MainRouteSearch }
            if (hasSearch) {
                SearchViewModel.resumeActiveSearch()
            }
            try {
                awaitCancellation()
            } finally {
                if (hasSearch) {
                    SearchViewModel.pauseActiveSearch()
                }
            }
        }
    }

    NavDisplay(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        sceneStrategies = listOf(SinglePaneSceneStrategy()),
        transitionSpec = {
            (slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
                initialOffset = { fullWidth -> fullWidth }
            ) + fadeIn(animationSpec = tween(durationMillis = 360, easing = LinearOutSlowInEasing))) togetherWith
                (slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
                    targetOffset = { fullWidth -> fullWidth / 4 }
                ) + fadeOut(animationSpec = tween(durationMillis = 360, easing = LinearOutSlowInEasing)))
        },
        popTransitionSpec = {
            (slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
                initialOffset = { fullWidth -> -fullWidth / 4 }
            ) + fadeIn(animationSpec = tween(durationMillis = 360, easing = LinearOutSlowInEasing))) togetherWith
                (scaleOut(
                    targetScale = 0.8f,
                    animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 360)))
        },
        predictivePopTransitionSpec = { _ ->
            (slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(easing = FastOutSlowInEasing),
                initialOffset = { fullWidth -> -fullWidth / 4 }
            ) + fadeIn(animationSpec = tween(easing = LinearOutSlowInEasing))) togetherWith
                (scaleOut(
                    targetScale = 0.8f,
                    animationSpec = tween(easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween()))
        },
        onBack = onNavigateBack,
        entryProvider = entryProvider {
            entry<MainRouteHome> {
                BackHandler { onNavigateBack() }
                BottomNavScreen(
                    showDiscovery = AppConfig.showDiscovery,
                    showRSS = AppConfig.showRSS,
                    onNavigateToRoute = onNavigateToRoute,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                )
            }

            // --- 书城域 ---
            entry<MainRouteSearch> { route ->
                SearchEntry(route, onNavigateToRoute, onNavigateBack, sharedTransitionScope)
            }

            entry<MainRouteBookInfo>(
                metadata = NavDisplay.transitionSpec {
                    val from = initialState.key
                    val fromStr = from.toString()
                    if (from is MainRouteHome || from is MainRouteExploreShow || from is MainRouteSearch ||
                        fromStr.startsWith("MainRouteHome") || fromStr.startsWith("MainRouteExploreShow") || fromStr.startsWith("MainRouteSearch")
                    ) {
                        fadeIn(animationSpec = tween(300)) togetherWith
                                fadeOut(animationSpec = tween(300))
                    } else null
                } + NavDisplay.popTransitionSpec {
                    val to = targetState.key
                    val toStr = to.toString()
                    if (to is MainRouteHome || to is MainRouteExploreShow || to is MainRouteSearch ||
                        toStr.startsWith("MainRouteHome") || toStr.startsWith("MainRouteExploreShow") || toStr.startsWith("MainRouteSearch")
                    ) {
                        fadeIn(animationSpec = tween(300)) togetherWith
                                fadeOut(animationSpec = tween(300))
                    } else null
                } + NavDisplay.predictivePopTransitionSpec { _ ->
                    val to = targetState.key
                    val toStr = to.toString()
                    if (to is MainRouteHome || to is MainRouteExploreShow || to is MainRouteSearch ||
                        toStr.startsWith("MainRouteHome") || toStr.startsWith("MainRouteExploreShow") || toStr.startsWith("MainRouteSearch")
                    ) {
                        fadeIn(animationSpec = tween(300)) togetherWith
                                fadeOut(animationSpec = tween(300))
                    } else null
                }
            ) { route ->
                BookInfoEntry(route, onNavigateToRoute, onNavigateBack, sharedTransitionScope, tocRouteState, readBookRouteState)
            }

            // 阶段 3：目录路由化——选择结果经 TocRouteState 回传 BookInfoEntry，
            // 阅读器/漫画/听书形态仍走 TocActivity 薄壳（TocActivityResult 契约不变）。
            entry<MainRouteToc> { route ->
                TocEntry(route, onNavigateBack, scope, tocRouteState, onNavigateToRoute)
            }

            // 阶段 4：阅读页路由化。当前无调用方进入（外部仍走 ReadBookActivity，
            // M5 删壳后统一走本路由）；isPopped 供 dispose 区分"被覆盖"与"已弹栈"。
            entry<MainRouteReadBook> { route ->
                ReadBookEntry(
                    route,
                    onNavigateBack,
                    readBookRouteState,
                    isPopped = { backStack.none { it == route } },
                )
            }

            entry<MainRouteExploreShow> { route ->
                ExploreShowEntry(route, onNavigateToRoute, onNavigateBack)
            }

            entry<MainRouteReadRecord> {
                ReadRecordEntry(onNavigateBack, onNavigateToRoute, pushRoute)
            }

            entry<MainRouteReadRecordOverview> {
                ReadRecordOverviewEntry(onNavigateBack, onNavigateToRoute)
            }

            entry<MainRouteAllBookmark> {
                AllBookmarkEntry(onNavigateBack)
            }

            entry<MainRouteFileManage> {
                FileManageEntry(onNavigateBack)
            }

            entry<MainRouteDictRule> {
                DictRuleEntry(onNavigateBack)
            }

            // --- 替换规则域（阶段 2 收编） ---
            entry<MainRouteReplaceRule> {
                ReplaceRuleEntry(onNavigateToRoute, onNavigateBack)
            }

            entry<MainRouteReplaceEdit> { route ->
                ReplaceEditEntry(route, onNavigateBack)
            }

            // --- 书源管理域（阶段 2 收编） ---
            entry<MainRouteBookSourceManage> {
                BookSourceManageEntry(onNavigateBack)
            }

            // --- 朗读 / TTS 域 ---
            entry<MainRouteReadAloudPlayer> {
                ReadAloudPlayerEntry(onNavigateToRoute, onNavigateBack)
            }

            entry<MainRouteBookVoiceCasting> { route ->
                BookVoiceCastingEntry(route, onNavigateToRoute, onNavigateBack)
            }

            entry<MainRouteBookCharacterNetwork> { route ->
                BookCharacterNetworkEntry(route, onNavigateToRoute, onNavigateBack)
            }

            entry<MainRouteBookCharacterList> { route ->
                BookCharacterListEntry(route, onNavigateBack)
            }

            entry<MainRouteCloudTtsEngines> {
                CloudTtsEnginesEntry(onNavigateBack)
            }

            entry<MainRouteTtsCache> {
                TtsCacheEntry(onNavigateBack)
            }

            // --- 配置域 ---
            entry<MainRouteAbout> {
                AboutEntry(onNavigateBack, actions)
            }

            entry<MainRouteOtherConfig> {
                OtherConfigEntry(onNavigateBack)
            }

            entry<MainRouteBackupConfig> {
                BackupConfigEntry(onNavigateBack, actions)
            }

            entry<MainRouteThemeConfig> {
                ThemeConfigEntry(onNavigateToRoute, onNavigateBack, actions)
            }

            entry<MainRouteWelcomeConfig> {
                WelcomeConfigEntry(onNavigateBack, actions)
            }

            entry<MainRouteCoverConfig> {
                CoverConfigEntry(onNavigateBack, actions)
            }

            // --- AI 域 ---
            entry<MainRouteAiChat> {
                AiChatEntry(onNavigateToRoute, onNavigateBack)
            }

            entry<MainRouteSettingsAiChatColors> {
                AiChatColorsEntry(onNavigateBack)
            }

            entry<MainRouteSettingsAi> {
                AiConfigEntry(onNavigateToRoute, onNavigateBack)
            }

            entry<MainRouteSettingsAiHtmlThemes> {
                AiHtmlThemesEntry(onNavigateBack)
            }

            entry<MainRouteSettingsAiSkills> {
                AiSkillsEntry(onNavigateToRoute, onNavigateBack)
            }

            entry<MainRouteSettingsAiSkillEdit> { route ->
                AiSkillEditEntry(route, onNavigateBack)
            }

            entry<MainRouteSettingsAiWebSearch> {
                AiWebSearchEntry(onNavigateBack)
            }

            entry<MainRouteSettingsAiProfileEdit> { route ->
                AiProfileEditEntry(route, onNavigateToRoute, onNavigateBack)
            }

            entry<MainRouteSettingsAiModelEdit> { route ->
                AiModelEditEntry(route, onNavigateBack)
            }

            entry<MainRouteSettingsAiAbilityManagement> {
                AiAbilityManagementEntry(onNavigateBack)
            }

            entry<MainRouteSettingsAiPromptTemplates> {
                AiPromptTemplatesEntry(onNavigateToRoute, onNavigateBack)
            }

            entry<MainRouteSettingsAiPromptPipeline> {
                AiPromptPipelineEntry(onNavigateBack)
            }

            entry<MainRouteAiDictRule> {
                AiDictRuleEntry(onNavigateBack)
            }

            entry<MainRouteAiUsageOverview> {
                AiUsageOverviewEntry(onNavigateBack)
            }
        },
    )
}
