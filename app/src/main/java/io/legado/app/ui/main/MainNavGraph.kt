package io.legado.app.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import org.koin.androidx.compose.koinViewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import android.app.Activity
import androidx.compose.runtime.snapshotFlow
import io.legado.app.ui.book.info.compose.BookInfoRouteScreen
import io.legado.app.ui.book.search.SearchIntent
import io.legado.app.ui.book.search.SearchScreen
import io.legado.app.ui.book.search.SearchViewModel
import io.legado.app.ui.book.explore.ExploreShowIntent
import io.legado.app.ui.book.explore.ExploreShowScreen
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.main.my.AboutActions
import io.legado.app.ui.main.my.AiDictRuleRoute
import io.legado.app.ui.main.my.BackupConfigActions
import io.legado.app.ui.main.my.CoverConfigActions
import io.legado.app.ui.main.my.MyAboutRoute
import io.legado.app.ui.main.my.MyBackupConfigRoute
import io.legado.app.ui.main.my.MyCoverConfigRoute
import io.legado.app.ui.main.my.MyOtherConfigRoute
import io.legado.app.ui.main.my.MyThemeConfigRoute
import io.legado.app.ui.main.my.MyWelcomeConfigRoute
import io.legado.app.ui.main.my.OtherConfigActions
import io.legado.app.ui.main.my.ReadRecordRoute
import io.legado.app.ui.main.my.ReadRecordOverviewRoute
import io.legado.app.ui.main.my.ThemeConfigActions
import io.legado.app.ui.main.my.WelcomeConfigActions
import io.legado.app.utils.showLogSheet
import io.legado.app.utils.startActivity

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
    val searchViewModel = koinViewModel<SearchViewModel>()

    var onNavigateToRoute: (MainRoute) -> Unit by remember { mutableStateOf({}) }

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

    // 导出回调给 MainActivity（供 navigateToSearch 等遗留代码调用）
    SideEffect {
        onNavigateToRouteSetter(onNavigateToRoute)
    }

    // 栈变化后重置防抖守卫，使下一次按钮返回能再次触发。
    LaunchedEffect(backStack) {
        snapshotFlow { backStack.toList() }.collect {
            MainNavigator.onBackStackChanged()
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
                    showDiscovery = io.legado.app.help.config.AppConfig.showDiscovery,
                    showRSS = io.legado.app.help.config.AppConfig.showRSS,
                    onNavigateToRoute = onNavigateToRoute,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                )
            }

            entry<MainRouteSearch> { route ->

                LaunchedEffect(route.key, route.scopeRaw) {
                    searchViewModel.onIntent(SearchIntent.Initialize(key = route.key, scopeRaw = route.scopeRaw))
                }

                SearchScreen(
                    viewModel = searchViewModel,
                    onBack = {
                        searchViewModel.onIntent(SearchIntent.ClearSearchResults)
                        onNavigateBack()
                    },
                    onOpenBookInfo = { name, author, bookUrl, origin, coverPath, sharedCoverKey ->
                        onNavigateToRoute(
                            MainRouteBookInfo(
                                name = name,
                                author = author,
                                bookUrl = bookUrl,
                                origin = origin,
                                coverPath = coverPath,
                                sharedCoverKey = sharedCoverKey,
                            )
                        )
                    },
                    onOpenSourceManage = { context.startActivity<BookSourceActivity>() },
                    onShowLog = { (context as? androidx.fragment.app.FragmentActivity)?.showLogSheet() },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                )
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
                BookInfoRouteScreen(
                    bookUrl = route.bookUrl,
                    name = route.name,
                    author = route.author,
                    coverPath = route.coverPath,
                    origin = route.origin,
                    onBack = onNavigateBack,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                    sharedCoverKey = route.sharedCoverKey,
                )
            }

            entry<MainRouteReadRecord> {
                ReadRecordRoute(
                    onBack = onNavigateBack,
                    onOverview = { backStack.add(MainRouteReadRecordOverview) },
                    onNavigateToBook = { name, key ->
                        onNavigateToRoute(MainRouteSearch(key = name))
                    },
                )
            }

            entry<MainRouteReadRecordOverview> {
                ReadRecordOverviewRoute(
                    onBack = onNavigateBack,
                    onBookClick = { name, _ ->
                        onNavigateToRoute(MainRouteSearch(key = name))
                    },
                )
            }

            entry<MainRouteAiDictRule> {
                AiDictRuleRoute(
                    fragment = null,
                    onBack = onNavigateBack,
                )
            }

            entry<MainRouteAbout> {
                MyAboutRoute(
                    onBack = onNavigateBack,
                    actions = AboutActions(
                        onShare = {},
                        onScoring = {},
                        onContributors = {},
                        onUpdateLog = {},
                        onCheckUpdate = {},
                        onCrashLog = {},
                        onSaveLog = {},
                        onCreateHeapDump = {},
                        onPrivacyPolicy = {},
                        onLicense = {},
                        onDisclaimer = {},
                    ),
                )
            }

            entry<MainRouteOtherConfig> {
                MyOtherConfigRoute(
                    fragment = null,
                    onBack = onNavigateBack,
                    actions = OtherConfigActions(
                        onCheckSource = {},
                        onUploadRule = {},
                    ),
                )
            }

            entry<MainRouteBackupConfig> {
                MyBackupConfigRoute(
                    fragment = null,
                    onBack = onNavigateBack,
                    actions = BackupConfigActions(
                        onBackupPath = {},
                        onRestoreIgnore = {},
                        onImportOld = {},
                        onLocalRestore = {},
                        onWebDavRestore = {},
                        onHelp = {},
                        onLog = {},
                    ),
                )
            }

            entry<MainRouteThemeConfig> {
                MyThemeConfigRoute(
                    onBack = onNavigateBack,
                    actions = ThemeConfigActions(
                        onRequestColorPicker = { _, _, _ -> },
                        onThemeList = {},
                        onBgImage = {},
                        onThemeModeToggle = {},
                    ),
                    onWelcomeStyle = { onNavigateToRoute(MainRouteWelcomeConfig) },
                    onCoverConfig = { onNavigateToRoute(MainRouteCoverConfig) },
                )
            }

            entry<MainRouteWelcomeConfig> {
                MyWelcomeConfigRoute(
                    fragment = null,
                    onBack = onNavigateBack,
                    actions = WelcomeConfigActions(onWelcomeImage = {}),
                )
            }

            entry<MainRouteCoverConfig> {
                MyCoverConfigRoute(
                    fragment = null,
                    onBack = onNavigateBack,
                    actions = CoverConfigActions(
                        onCoverRule = {},
                        onDefaultCover = {},
                    ),
                )
            }

            entry<MainRouteExploreShow> { route ->
                val exploreViewModel = koinViewModel<ExploreShowViewModel>()

                LaunchedEffect(route) {
                    exploreViewModel.onIntent(
                        ExploreShowIntent.Initialize(
                            sourceUrl = route.sourceUrl,
                            exploreUrl = route.exploreUrl ?: "",
                            title = route.title ?: "",
                        )
                    )
                }

                ExploreShowScreen(
                    viewModel = exploreViewModel,
                    onBack = onNavigateBack,
                    onOpenBookInfo = { name, author, bookUrl ->
                        onNavigateToRoute(MainRouteBookInfo(
                            name = name,
                            author = author,
                            bookUrl = bookUrl,
                        ))
                    },
                )
            }
        },
    )
}
