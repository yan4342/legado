package io.legado.app.ui.main

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.ui.dict.rule.DictRuleActivity
import io.legado.app.ui.book.bookmark.AllBookmarkActivity
import io.legado.app.ui.file.FileManageActivity
import io.legado.app.ui.main.bookshelf.compose.BookshelfTab
import io.legado.app.ui.main.explore.ExploreScreen
import io.legado.app.ui.main.my.MyScreen
import io.legado.app.ui.main.rss.RssScreen
import io.legado.app.service.WebService
import io.legado.app.constant.PreferKey
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivity
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.launch

private val MainDestination.iconRes: Int
    get() = when (this) {
        MainDestination.Bookshelf -> R.drawable.ic_bottom_books_e
        MainDestination.Explore -> R.drawable.ic_bottom_explore_e
        MainDestination.Rss -> R.drawable.ic_bottom_rss_feed_e
        MainDestination.My -> R.drawable.ic_bottom_person_e
    }

private val MainDestination.selectedIconRes: Int
    get() = when (this) {
        MainDestination.Bookshelf -> R.drawable.ic_bottom_books_s
        MainDestination.Explore -> R.drawable.ic_bottom_explore_s
        MainDestination.Rss -> R.drawable.ic_bottom_rss_feed_s
        MainDestination.My -> R.drawable.ic_bottom_person_s
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun BottomNavScreen(
    showDiscovery: Boolean,
    showRSS: Boolean,
    onNavigateToRoute: (MainRoute) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val tabs = buildList {
        add(MainDestination.Bookshelf)
        if (showDiscovery) add(MainDestination.Explore)
        if (showRSS) add(MainDestination.Rss)
        add(MainDestination.My)
    }

    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    var myThemeMode by remember { mutableStateOf("followSystem") }
    var webServiceRunning by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.height(72.dp),
                containerColor = Color(LocalContext.current.bottomBackground),
                tonalElevation = 0.dp,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                tabs.forEachIndexed { index, dest ->
                    val selected = pagerState.currentPage == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        icon = {
                            Icon(
                                painterResource(if (selected) dest.selectedIconRes else dest.iconRes),
                                contentDescription = null,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = tabs.size.coerceAtMost(3),
            modifier = Modifier.padding(padding),
        ) { page ->
            Box(Modifier.fillMaxSize()) {
                when (tabs[page]) {
                    MainDestination.Bookshelf -> BookshelfTab(
                        onNavigateToRoute = onNavigateToRoute,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                    MainDestination.Explore -> ExploreScreen(onNavigateToRoute = onNavigateToRoute)
                    MainDestination.Rss -> RssScreen(onNavigateToRoute = onNavigateToRoute)
                    MainDestination.My -> {
                        val context = LocalContext.current
                        val onPrimary = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onPrimary
                        val container = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.primary
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    modifier = Modifier.height(64.dp),
                                    windowInsets = WindowInsets(0.dp),
                                    title = { Text(stringResource(R.string.my), color = onPrimary) },
                                    colors = TopAppBarDefaults.topAppBarColors(containerColor = container),
                                    actions = {
                                        IconButton(onClick = {
                                            (context as? AppCompatActivity)?.showHelp("appHelp")
                                        }) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_help),
                                                contentDescription = stringResource(R.string.help),
                                                tint = onPrimary,
                                            )
                                        }
                                    },
                                )
                            },
                            contentWindowInsets = WindowInsets(0.dp),
                        ) { contentPadding ->
                            MyScreen(
                                modifier = Modifier.padding(contentPadding),
                                themeMode = myThemeMode,
                                webServiceRunning = webServiceRunning,
                                webServiceAddress = "",
                                onBookSourceManage = { context.startActivity<BookSourceActivity>() },
                                onTxtTocRuleManage = { context.startActivity<TxtTocRuleActivity>() },
                                onReplaceManage = { context.startActivity<ReplaceRuleActivity>() },
                                onDictRuleManage = { context.startActivity<DictRuleActivity>() },
                                onAiDictRuleManage = { onNavigateToRoute(MainRouteAiDictRule) },
                                onBookmark = { context.startActivity<AllBookmarkActivity>() },
                                onReadRecord = { onNavigateToRoute(MainRouteReadRecord) },
                                onBackupRestore = { onNavigateToRoute(MainRouteBackupConfig) },
                                onThemeSetting = { onNavigateToRoute(MainRouteThemeConfig) },
                                onThemeModeChange = { myThemeMode = it },
                                onOtherSetting = { onNavigateToRoute(MainRouteOtherConfig) },
                                onWebServiceChange = { checked ->
                                    webServiceRunning = checked
                                    if (checked) WebService.start(context) else WebService.stop(context)
                                    context.putPrefBoolean(PreferKey.webService, checked)
                                },
                                onWebServiceLongClick = {},
                                onFileManage = { context.startActivity<FileManageActivity>() },
                                onAbout = { onNavigateToRoute(MainRouteAbout) },
                                onExit = { (context as? android.app.Activity)?.finish() },
                            )
                        }
                    }
                }
            }
        }
        }

    }
}
