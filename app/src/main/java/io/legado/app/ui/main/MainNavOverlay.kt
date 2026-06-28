package io.legado.app.ui.main

import android.view.View
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import org.koin.androidx.compose.koinViewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.ui.book.info.compose.BookInfoRouteScreen
import io.legado.app.ui.book.search.SearchIntent
import io.legado.app.ui.book.search.SearchScreen
import io.legado.app.ui.book.search.SearchViewModel
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.utils.postEvent
import io.legado.app.utils.showLogSheet
import io.legado.app.utils.startActivity
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * MainActivity 全局搜索覆盖层。参照 [io.legado.app.ui.main.my.MyNavOverlay] 的模式，
 * 用 NavDisplay 把搜索页作为全屏路由，返回时由 popTransitionSpec 驱动 Compose pop 动画
 * （scaleOut 0.8 + slideIn + fade），露出 ViewPager 层，无白屏。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MainNavOverlay(
    sharedTransitionScope: SharedTransitionScope,
    navOverlayRoute: MutableStateFlow<MainRoute?>,
) {
    val context = LocalContext.current
    val backStack = rememberNavBackStack(MainRouteEmpty)
    val searchViewModel = koinViewModel<SearchViewModel>()
    val pendingRoute by navOverlayRoute.collectAsStateWithLifecycle()

    // 响应 MainActivity.navigateToSearch() 的调用
    LaunchedEffect(pendingRoute) {
        val route = pendingRoute
        if (route is MainRouteSearch) {
            if (backStack.lastOrNull() is MainRouteSearch) {
                backStack[backStack.lastIndex] = route
            } else {
                backStack.add(route)
            }
        }
    }

    // 搜索激活时隐藏底栏 & 禁用 ViewPager 滑动
    LaunchedEffect(backStack.size) {
        val isActive = backStack.size > 1
        val nav = (context as? MainActivity)
            ?.findViewById<View>(R.id.bottom_navigation_view)
        nav?.visibility = if (isActive) View.GONE else View.VISIBLE
        postEvent(EventBus.DISABLE_VIEW_PAGER, isActive)
        if (!isActive) navOverlayRoute.value = null
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
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<MainRouteEmpty> {
                Box(Modifier.fillMaxSize())
            }
            entry<MainRouteSearch> { route ->
                var bookInfoOverlay by remember { mutableStateOf<BookInfoOverlayParams?>(null) }

                LaunchedEffect(route.key, route.scopeRaw) {
                    searchViewModel.onIntent(SearchIntent.Initialize(key = route.key, scopeRaw = route.scopeRaw))
                }

                SearchScreen(
                    viewModel = searchViewModel,
                    onBack = {
                        if (bookInfoOverlay != null) bookInfoOverlay = null
                        else {
                            searchViewModel.onIntent(SearchIntent.ClearSearchResults)
                            backStack.removeLastOrNull()
                        }
                    },
                    onOpenBookInfo = { name, author, bookUrl, origin, coverPath, sharedCoverKey ->
                        bookInfoOverlay = BookInfoOverlayParams(
                            name, author, bookUrl, origin, coverPath, sharedCoverKey
                        )
                    },
                    onOpenSourceManage = {
                        context.startActivity<BookSourceActivity>()
                    },
                    onShowLog = {
                        (context as? androidx.fragment.app.FragmentActivity)?.showLogSheet()
                    },
                    sharedTransitionScope = sharedTransitionScope,
                )

                AnimatedVisibility(
                    visible = bookInfoOverlay != null,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(250)),
                ) {
                    bookInfoOverlay?.let { params ->
                        BookInfoRouteScreen(
                            bookUrl = params.bookUrl,
                            name = params.name,
                            author = params.author,
                            coverPath = params.coverPath,
                            origin = params.origin,
                            onBack = { bookInfoOverlay = null },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = this@AnimatedVisibility,
                            sharedCoverKey = params.sharedCoverKey,
                        )
                    }
                }
            }
        },
    )
}

internal data class BookInfoOverlayParams(
    val name: String,
    val author: String,
    val bookUrl: String,
    val origin: String?,
    val coverPath: String?,
    val sharedCoverKey: String?,
)
