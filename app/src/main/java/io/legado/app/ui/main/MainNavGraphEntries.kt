package io.legado.app.ui.main

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorShape
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.prefs.ColorPreference
import io.legado.app.ui.ai.chat.AiChatScreen
import io.legado.app.ui.ai.chat.AiChatViewModel
import io.legado.app.ui.book.bookmark.AllBookmarkRoute
import io.legado.app.ui.book.explore.ExploreShowIntent
import io.legado.app.ui.book.explore.ExploreShowScreen
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.book.info.compose.BookInfoRouteScreen
import io.legado.app.ui.book.readaloud.cache.TtsCacheRouteScreen
import io.legado.app.ui.book.readaloud.casting.BookVoiceCastingRouteScreen
import io.legado.app.ui.book.readaloud.cloudtts.CloudTtsRouteScreen
import io.legado.app.ui.book.read.ReadBookRouteState
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerRouteScreen
import io.legado.app.ui.book.search.SearchIntent
import io.legado.app.ui.book.search.SearchScreen
import io.legado.app.ui.book.search.SearchViewModel
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.book.toc.TocRouteState
import io.legado.app.ui.book.toc.TocScreen
import io.legado.app.ui.book.toc.TocViewModel
import io.legado.app.ui.config.CheckSourceConfig
import io.legado.app.ui.config.CoverRuleConfigDialog
import io.legado.app.ui.config.DirectLinkUploadConfig
import io.legado.app.ui.config.ThemeListDialog
import io.legado.app.ui.config.ai.AiAbilityManagementScreen
import io.legado.app.ui.config.ai.AiAbilityManagementViewModel
import io.legado.app.ui.config.ai.AiConfigScreen
import io.legado.app.ui.config.ai.AiConfigViewModel
import io.legado.app.ui.config.ai.AiModelEditScreen
import io.legado.app.ui.config.ai.AiProfileEditScreen
import io.legado.app.ui.config.ai.AiSkillEditScreen
import io.legado.app.ui.config.ai.AiSkillEditViewModel
import io.legado.app.ui.config.ai.AiSkillsScreen
import io.legado.app.ui.config.ai.AiSkillsViewModel
import io.legado.app.ui.config.ai.AiWebSearchConfigScreen
import io.legado.app.ui.config.ai.PromptPipelineScreen
import io.legado.app.ui.config.ai.PromptPipelineViewModel
import io.legado.app.ui.config.ai.PromptTemplateScreen
import io.legado.app.ui.config.ai.PromptTemplateViewModel
import io.legado.app.ui.dict.rule.DictRuleRouteScreen
import io.legado.app.ui.file.FileManageRoute
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.replace.ReplaceEditRoute
import io.legado.app.ui.replace.ReplaceRuleRouteScreen
import io.legado.app.ui.replace.edit.ReplaceEditRouteScreen
import io.legado.app.ui.replace.edit.ReplaceEditViewModel
import io.legado.app.ui.main.my.AboutActions
import io.legado.app.ui.main.my.AiDictRuleRoute
import io.legado.app.ui.main.my.AiUsageOverviewRoute
import io.legado.app.ui.main.my.BackupConfigActions
import io.legado.app.ui.main.my.CoverConfigActions
import io.legado.app.ui.main.my.MyAboutRoute
import io.legado.app.ui.main.my.MyBackupConfigRoute
import io.legado.app.ui.main.my.MyCoverConfigRoute
import io.legado.app.ui.main.my.MyOtherConfigRoute
import io.legado.app.ui.main.my.MyThemeConfigRoute
import io.legado.app.ui.main.my.MyWelcomeConfigRoute
import io.legado.app.ui.main.my.OtherConfigActions
import io.legado.app.ui.main.my.ReadRecordOverviewRoute
import io.legado.app.ui.main.my.ReadRecordRoute
import io.legado.app.ui.main.my.ThemeConfigActions
import io.legado.app.ui.main.my.WelcomeConfigActions
import io.legado.app.utils.getPrefString
import io.legado.app.utils.openUrl
import io.legado.app.utils.removePref
import io.legado.app.utils.share
import io.legado.app.utils.showCrashLogSheet
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showLogSheet
import io.legado.app.utils.showMarkdownSheet
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

// MainNavGraph 各 entry 的内容体（Phase 1 导航拆分：自 MainNavHost 迁出，行为不变）。
// 共享的 onNavigateBack / onNavigateToRoute 闭包仍留在 MainNavHost，按需传入。

// ------------------------------------------------------------------
// 书城域：搜索 / 书籍详情 / 发现 / 阅读记录 / 书签 / 文件管理 / 词典规则
// ------------------------------------------------------------------

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun SearchEntry(
    route: MainRouteSearch,
    onNavigateToRoute: (MainRoute) -> Unit,
    onNavigateBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
) {
    val context = LocalContext.current
    val searchViewModel = koinViewModel<SearchViewModel>()

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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun BookInfoEntry(
    route: MainRouteBookInfo,
    onNavigateToRoute: (MainRoute) -> Unit,
    onNavigateBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    tocRouteState: TocRouteState,
    readBookRouteState: ReadBookRouteState,
) {
    BookInfoRouteScreen(
        bookUrl = route.bookUrl,
        name = route.name,
        author = route.author,
        coverPath = route.coverPath,
        origin = route.origin,
        onBack = onNavigateBack,
        onNavigateToVoiceCasting = { bookUrl ->
            onNavigateToRoute(MainRouteBookVoiceCasting(bookUrl))
        },
        onNavigateToCloudTts = { bookUrl ->
            onNavigateToRoute(MainRouteCloudTtsEngines(bookUrl))
        },
        onNavigateToCharacterNetwork = { bookUrl, focusCharacterId ->
            onNavigateToRoute(
                MainRouteBookCharacterNetwork(bookUrl, focusCharacterId),
            )
        },
        onNavigateToCharacterList = { bookUrl ->
            onNavigateToRoute(MainRouteBookCharacterList(bookUrl))
        },
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        sharedCoverKey = route.sharedCoverKey,
        tocRouteState = tocRouteState,
        onOpenTocRoute = { bookUrl -> onNavigateToRoute(MainRouteToc(bookUrl)) },
        readBookRouteState = readBookRouteState,
        onOpenReader = { bookUrl, inBookshelf ->
            onNavigateToRoute(MainRouteReadBook(bookUrl = bookUrl, inBookshelf = inBookshelf))
        },
    )
}

/**
 * 主栈目录 entry：内容与结果逻辑在 [TocScreen]，选择结果经 [TocRouteState]
 * 回传下方 BookInfoEntry（阅读器/漫画/听书形态仍走 TocActivity 薄壳）。
 */
@Composable
internal fun TocEntry(
    route: MainRouteToc,
    onNavigateBack: () -> Unit,
    launchScope: CoroutineScope,
    tocRouteState: TocRouteState,
    onNavigateToRoute: (MainRoute) -> Unit,
) {
    val viewModel: TocViewModel = koinViewModel()

    TocScreen(
        bookUrl = route.bookUrl,
        initialPage = 0,
        viewModel = viewModel,
        launchScope = launchScope,
        onExit = { result ->
            tocRouteState.pendingResult = result
            onNavigateBack()
        },
        onOpenReader = { bookUrl ->
            onNavigateToRoute(MainRouteReadBook(bookUrl = bookUrl))
        },
    )
}

@Composable
internal fun ExploreShowEntry(
    route: MainRouteExploreShow,
    onNavigateToRoute: (MainRoute) -> Unit,
    onNavigateBack: () -> Unit,
) {
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

@Composable
internal fun ReadRecordEntry(
    onNavigateBack: () -> Unit,
    onNavigateToRoute: (MainRoute) -> Unit,
    onPushRoute: (MainRoute) -> Unit,
) {
    ReadRecordRoute(
        onBack = onNavigateBack,
        onOverview = { onPushRoute(MainRouteReadRecordOverview) },
        onAiOverview = { onPushRoute(MainRouteAiUsageOverview) },
        onNavigateToBook = { name, key ->
            onNavigateToRoute(MainRouteSearch(key = name))
        },
        onNavigateToAiChat = { onPushRoute(MainRouteAiChat) },
    )
}

@Composable
internal fun ReadRecordOverviewEntry(
    onNavigateBack: () -> Unit,
    onNavigateToRoute: (MainRoute) -> Unit,
) {
    ReadRecordOverviewRoute(
        onBack = onNavigateBack,
        onBookClick = { name, _ ->
            onNavigateToRoute(MainRouteSearch(key = name))
        },
    )
}

@Composable
internal fun AllBookmarkEntry(onNavigateBack: () -> Unit) {
    AllBookmarkRoute(onBack = onNavigateBack)
}

@Composable
internal fun FileManageEntry(onNavigateBack: () -> Unit) {
    FileManageRoute(onBack = onNavigateBack)
}

@Composable
internal fun DictRuleEntry(onNavigateBack: () -> Unit) {
    DictRuleRouteScreen(onBackClick = onNavigateBack)
}

// ------------------------------------------------------------------
// 替换规则域（阶段 2 收编）：规则列表 / 规则编辑
// ------------------------------------------------------------------

@Composable
internal fun ReplaceRuleEntry(
    onNavigateToRoute: (MainRoute) -> Unit,
    onNavigateBack: () -> Unit,
) {
    ReplaceRuleRouteScreen(
        onBackClick = onNavigateBack,
        onNavigateToEdit = { editRoute ->
            onNavigateToRoute(
                MainRouteReplaceEdit(
                    id = editRoute.id,
                    pattern = editRoute.pattern,
                    isRegex = editRoute.isRegex,
                    scope = editRoute.scope,
                    isScopeTitle = editRoute.isScopeTitle,
                    isScopeContent = editRoute.isScopeContent,
                    sessionId = editRoute.sessionId,
                )
            )
        },
    )
}

@Composable
internal fun ReplaceEditEntry(
    route: MainRouteReplaceEdit,
    onNavigateBack: () -> Unit,
) {
    // 沿用 ReplaceRuleActivity 的会话隔离：以 sessionId 为 key 复用 Koin 中的
    // ReplaceEditViewModel 工厂（parametersOf(ReplaceEditRoute)）。
    val editRoute = remember(route) {
        ReplaceEditRoute(
            id = route.id,
            pattern = route.pattern,
            isRegex = route.isRegex,
            scope = route.scope,
            isScopeTitle = route.isScopeTitle,
            isScopeContent = route.isScopeContent,
            sessionId = route.sessionId,
        )
    }
    val viewModel: ReplaceEditViewModel = koinViewModel(
        key = "replace_edit_${route.sessionId}"
    ) { parametersOf(editRoute) }

    ReplaceEditRouteScreen(
        viewModel = viewModel,
        onBack = onNavigateBack,
        onSaveSuccess = onNavigateBack,
    )
}

// ------------------------------------------------------------------
// 朗读 / TTS 域：朗读播放 / 语音合成 / 人物网络 / 云端 TTS / TTS 缓存
// ------------------------------------------------------------------

@Composable
internal fun ReadAloudPlayerEntry(
    onNavigateToRoute: (MainRoute) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val bookUrl = io.legado.app.model.ReadBook.book?.bookUrl
    ReadAloudPlayerRouteScreen(
        onBack = onNavigateBack,
        onNavigateToCasting = bookUrl?.let { url ->
            { onNavigateToRoute(MainRouteBookVoiceCasting(url)) }
        },
        onNavigateToCloudTts = {
            onNavigateToRoute(MainRouteCloudTtsEngines(bookUrl))
        },
    )
}

@Composable
internal fun BookVoiceCastingEntry(
    route: MainRouteBookVoiceCasting,
    onNavigateToRoute: (MainRoute) -> Unit,
    onNavigateBack: () -> Unit,
) {
    BookVoiceCastingRouteScreen(
        bookUrl = route.bookUrl,
        onBack = onNavigateBack,
        onManageCloudTts = {
            onNavigateToRoute(MainRouteCloudTtsEngines(route.bookUrl))
        },
    )
}

@Composable
internal fun BookCharacterNetworkEntry(
    route: MainRouteBookCharacterNetwork,
    onNavigateToRoute: (MainRoute) -> Unit,
    onNavigateBack: () -> Unit,
) {
    io.legado.app.ui.book.info.network.BookCharacterNetworkRouteScreen(
        bookUrl = route.bookUrl,
        focusCharacterId = route.focusCharacterId,
        onBack = onNavigateBack,
        onNavigateToCharacterList = { bookUrl ->
            onNavigateToRoute(MainRouteBookCharacterList(bookUrl))
        },
    )
}

@Composable
internal fun BookCharacterListEntry(
    route: MainRouteBookCharacterList,
    onNavigateBack: () -> Unit,
) {
    io.legado.app.ui.book.info.characters.BookCharacterListRouteScreen(
        bookUrl = route.bookUrl,
        onBack = onNavigateBack,
    )
}

@Composable
internal fun CloudTtsEnginesEntry(onNavigateBack: () -> Unit) {
    CloudTtsRouteScreen(onBack = onNavigateBack)
}

@Composable
internal fun TtsCacheEntry(onNavigateBack: () -> Unit) {
    TtsCacheRouteScreen(onBackClick = onNavigateBack)
}

// ------------------------------------------------------------------
// 配置域：关于 / 其它配置 / 备份 / 主题 / 欢迎 / 封面
// ------------------------------------------------------------------

@Composable
internal fun AboutEntry(
    onNavigateBack: () -> Unit,
    actions: MainConfigRouteActions,
) {
    val context = LocalContext.current
    MyAboutRoute(
        onBack = onNavigateBack,
        actions = AboutActions(
            onShare = { context.share(context.getString(R.string.app_share_description), context.getString(R.string.app_name)) },
            onScoring = { context.openUrl("market://details?id=${context.packageName}") },
            onContributors = { context.openUrl(context.getString(R.string.contributors_url)) },
            onUpdateLog = { actions.showMdFile(context.getString(R.string.update_log), "updateLog.md") },
            onCheckUpdate = { context.toastOnUi("检查更新功能暂未迁移") },
            onCrashLog = { (context as? FragmentActivity)?.showCrashLogSheet() },
            onSaveLog = { context.toastOnUi("保存日志功能暂未迁移") },
            onCreateHeapDump = { context.toastOnUi("创建堆转储功能暂未迁移") },
            onPrivacyPolicy = { actions.showMdFile(context.getString(R.string.privacy_policy), "privacyPolicy.md") },
            onLicense = { actions.showMdFile(context.getString(R.string.license), "LICENSE.md") },
            onDisclaimer = { actions.showMdFile(context.getString(R.string.disclaimer), "disclaimer.md") },
        ),
    )
}

@Composable
internal fun OtherConfigEntry(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    MyOtherConfigRoute(
        fragment = null,
        onBack = onNavigateBack,
        actions = OtherConfigActions(
            onCheckSource = { (context as? AppCompatActivity)?.showDialogFragment<CheckSourceConfig>() },
            onUploadRule = { (context as? AppCompatActivity)?.showDialogFragment<DirectLinkUploadConfig>() },
        ),
    )
}

@Composable
internal fun BackupConfigEntry(
    onNavigateBack: () -> Unit,
    actions: MainConfigRouteActions,
) {
    val context = LocalContext.current
    MyBackupConfigRoute(
        fragment = null,
        onBack = onNavigateBack,
        backupPath = actions.backupPath,
        actions = BackupConfigActions(
            onBackupPath = { actions.selectBackupPath.launch {} },
            onRestoreIgnore = { backupIgnore(context) },
            onImportOld = { actions.restoreOld.launch {} },
            onLocalRestore = {
                actions.restoreDoc.launch {
                    this.title = context.getString(R.string.select_restore_file)
                    this.mode = HandleFileContract.FILE
                    this.allowExtensions = arrayOf("zip")
                }
            },
            onWebDavRestore = {
                webDavRestore(context, actions.backupWaitDialog, actions.scope, actions.appCompatActivity)
            },
            onHelp = {
                actions.scope.launch {
                    val mdText = withContext(Dispatchers.IO) {
                        runCatching {
                            context.assets.open("web/help/md/webDavHelp.md").bufferedReader().readText()
                        }.getOrNull() ?: ""
                    }
                    (context as? FragmentActivity)?.showMarkdownSheet(
                        context.getString(R.string.help), mdText
                    )
                }
            },
            onLog = { (context as? FragmentActivity)?.showLogSheet() },
        ),
    )
}

@Composable
internal fun ThemeConfigEntry(
    onNavigateToRoute: (MainRoute) -> Unit,
    onNavigateBack: () -> Unit,
    actions: MainConfigRouteActions,
) {
    val context = LocalContext.current
    MyThemeConfigRoute(
        onBack = onNavigateBack,
        actions = ThemeConfigActions(
            onRequestColorPicker = { title, currentColor, onChange ->
                actions.pendingColorCallback = onChange
                val colorInt = if (currentColor != Color.Unspecified) {
                    val r = (currentColor.red * 255).toInt()
                    val g = (currentColor.green * 255).toInt()
                    val b = (currentColor.blue * 255).toInt()
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                } else {
                    android.graphics.Color.GRAY
                }
                val dialog = ColorPreference.ColorPickerDialogCompat.newBuilder()
                    .setDialogType(ColorPickerDialog.TYPE_PRESETS)
                    .setDialogTitle(0)
                    .setColorShape(ColorShape.CIRCLE)
                    .setPresets(ColorPickerDialog.MATERIAL_COLORS)
                    .setAllowPresets(true)
                    .setAllowCustom(true)
                    .setShowAlphaSlider(false)
                    .setShowColorShades(true)
                    .setColor(colorInt)
                    .create()
                dialog.setColorPickerDialogListener(actions.colorPickerListener)
                actions.appCompatActivity?.supportFragmentManager
                    ?.beginTransaction()
                    ?.add(dialog, "color_$title")
                    ?.commitAllowingStateLoss()
            },
            onThemeList = {
                actions.appCompatActivity?.supportFragmentManager?.let { fm ->
                    ThemeListDialog().show(fm, "themeList")
                }
            },
            onBgImage = { isNight ->
                actions.pendingBgIsNight = isNight
                actions.pendingBgChange = {
                    ThemeConfig.applyTheme(context)
                }
                selectBgAction(context, isNight, actions.selectBgImage)
            },
            onThemeModeToggle = {
                AppConfig.isNightTheme = !AppConfig.isNightTheme
                ThemeConfig.applyDayNight(context)
            },
        ),
        onWelcomeStyle = { onNavigateToRoute(MainRouteWelcomeConfig) },
        onCoverConfig = { onNavigateToRoute(MainRouteCoverConfig) },
    )
}

@Composable
internal fun WelcomeConfigEntry(
    onNavigateBack: () -> Unit,
    actions: MainConfigRouteActions,
) {
    val context = LocalContext.current
    MyWelcomeConfigRoute(
        fragment = null,
        onBack = onNavigateBack,
        actions = WelcomeConfigActions(
            onWelcomeImage = { isNight ->
                actions.pendingWelcomeIsNight = isNight
                val key = if (isNight) PreferKey.welcomeImageDark else PreferKey.welcomeImage
                if (context.getPrefString(key).isNullOrEmpty()) {
                    actions.selectWelcomeImage.launch {
                        this.requestCode = if (isNight) 222 else 221
                        this.mode = HandleFileContract.IMAGE
                    }
                } else {
                    context.selector(
                        items = arrayListOf(
                            context.getString(R.string.delete),
                            context.getString(R.string.select_image),
                        )
                    ) { _, i ->
                        if (i == 0) {
                            context.removePref(key)
                            if (isNight) {
                                AppConfig.welcomeShowTextDark = true
                                AppConfig.welcomeShowIconDark = true
                            } else {
                                AppConfig.welcomeShowText = true
                                AppConfig.welcomeShowIcon = true
                            }
                            io.legado.app.model.BookCover.upDefaultCover()
                        } else {
                            actions.selectWelcomeImage.launch {
                                this.requestCode = if (isNight) 222 else 221
                                this.mode = HandleFileContract.IMAGE
                            }
                        }
                    }
                }
            },
        ),
    )
}

@Composable
internal fun CoverConfigEntry(
    onNavigateBack: () -> Unit,
    actions: MainConfigRouteActions,
) {
    val context = LocalContext.current
    MyCoverConfigRoute(
        fragment = null,
        onBack = onNavigateBack,
        actions = CoverConfigActions(
            onCoverRule = {
                (context as? AppCompatActivity)?.showDialogFragment<CoverRuleConfigDialog>()
            },
            onDefaultCover = { isNight ->
                actions.pendingCoverIsNight = isNight
                val key = if (isNight) PreferKey.defaultCoverDark else PreferKey.defaultCover
                if (context.getPrefString(key).isNullOrEmpty()) {
                    actions.selectCoverImage.launch {
                        this.requestCode = if (isNight) 112 else 111
                        this.mode = HandleFileContract.IMAGE
                    }
                } else {
                    context.selector(
                        items = arrayListOf(
                            context.getString(R.string.delete),
                            context.getString(R.string.select_image),
                        )
                    ) { _, i ->
                        if (i == 0) {
                            context.removePref(key)
                            io.legado.app.model.BookCover.upDefaultCover()
                        } else {
                            actions.selectCoverImage.launch {
                                this.requestCode = if (isNight) 112 else 111
                                this.mode = HandleFileContract.IMAGE
                            }
                        }
                    }
                }
            },
        ),
    )
}

// ------------------------------------------------------------------
// AI 域：AI 对话 / AI 设置 / 技能 / 模型 / 提示词 / Web 搜索 / AI 词典 / 用量
// ------------------------------------------------------------------

@Composable
internal fun AiChatEntry(
    onNavigateToRoute: (MainRoute) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    // Scope the chat ViewModel to the host Activity so leaving this route does not
    // clear it — in-flight streaming keeps running and state survives round-trips.
    val activityOwner = remember(context) { context.findActivity() }
    val aiChatViewModel = koinViewModel<AiChatViewModel>(
        viewModelStoreOwner = (activityOwner as? ViewModelStoreOwner)
            ?: LocalViewModelStoreOwner.current!!,
    )
    val lifecycleOwner = LocalLifecycleOwner.current
    var renderTrack by remember { mutableStateOf(AppConfig.aiChatRenderTrack) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                renderTrack = AppConfig.aiChatRenderTrack
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val useDynamic = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val chatColorScheme = if (useDynamic) {
        if (AppConfig.isNightTheme) {
            androidx.compose.material3.dynamicDarkColorScheme(context)
        } else {
            androidx.compose.material3.dynamicLightColorScheme(context)
        }
    } else {
        io.legado.app.ui.common.compose.rememberLegadoColorScheme()
    }
    androidx.compose.material3.MaterialTheme(colorScheme = chatColorScheme) {
        io.legado.app.lib.theme.ProvideAiChatSemanticColors {
            androidx.compose.runtime.key(renderTrack) {
                if (renderTrack == AppConfig.AI_CHAT_RENDER_HTML) {
                    io.legado.app.ui.ai.chat.html.AiChatHtmlScreen(
                        viewModel = aiChatViewModel,
                        onBack = onNavigateBack,
                        onNavigateToAiSettings = { onNavigateToRoute(MainRouteSettingsAi) },
                    )
                } else {
                    AiChatScreen(
                        viewModel = aiChatViewModel,
                        onBack = onNavigateBack,
                        onNavigateToAiSettings = { onNavigateToRoute(MainRouteSettingsAi) },
                        onNavigateToAiChatColors = { onNavigateToRoute(MainRouteSettingsAiChatColors) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun AiChatColorsEntry(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val useDynamic = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val chatColorScheme = if (useDynamic) {
        if (AppConfig.isNightTheme) {
            androidx.compose.material3.dynamicDarkColorScheme(context)
        } else {
            androidx.compose.material3.dynamicLightColorScheme(context)
        }
    } else {
        io.legado.app.ui.common.compose.rememberLegadoColorScheme()
    }
    androidx.compose.material3.MaterialTheme(colorScheme = chatColorScheme) {
        io.legado.app.lib.theme.ProvideAiChatSemanticColors {
            io.legado.app.ui.config.ai.AiChatColorConfigScreen(
                onBack = onNavigateBack,
            )
        }
    }
}

@Composable
internal fun AiConfigEntry(
    onNavigateToRoute: (MainRoute) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val aiConfigViewModel = koinViewModel<AiConfigViewModel>()
    val aiProfileGateway: AiProfileGateway = koinInject()
    AiConfigScreen(
        viewModel = aiConfigViewModel,
        aiProfileGateway = aiProfileGateway,
        onBack = onNavigateBack,
        onNavigateToProfileEdit = { providerId: String? ->
            onNavigateToRoute(MainRouteSettingsAiProfileEdit(providerId))
        },
        onNavigateToModelEdit = { providerId: String, modelProfileId: String? ->
            onNavigateToRoute(MainRouteSettingsAiModelEdit(providerId, modelProfileId))
        },
        onNavigateToAbilityManagement = {
            onNavigateToRoute(MainRouteSettingsAiAbilityManagement)
        },
        onNavigateToSkills = {
            onNavigateToRoute(MainRouteSettingsAiSkills)
        },
        onNavigateToWebSearch = {
            onNavigateToRoute(MainRouteSettingsAiWebSearch)
        },
        onNavigateToPromptTemplates = {
            onNavigateToRoute(MainRouteSettingsAiPromptTemplates)
        },
        onNavigateToHtmlThemes = {
            onNavigateToRoute(MainRouteSettingsAiHtmlThemes)
        },
    )
}

@Composable
internal fun AiHtmlThemesEntry(onNavigateBack: () -> Unit) {
    io.legado.app.ui.config.ai.AiHtmlThemeConfigScreen(
        onBack = onNavigateBack,
    )
}

@Composable
internal fun AiSkillsEntry(
    onNavigateToRoute: (MainRoute) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val skillsViewModel = koinViewModel<AiSkillsViewModel>()
    AiSkillsScreen(
        viewModel = skillsViewModel,
        onBack = onNavigateBack,
        onNavigateToCreate = {
            onNavigateToRoute(MainRouteSettingsAiSkillEdit())
        },
        onNavigateToEdit = { skillId ->
            onNavigateToRoute(MainRouteSettingsAiSkillEdit(skillId = skillId))
        },
    )
}

@Composable
internal fun AiSkillEditEntry(
    route: MainRouteSettingsAiSkillEdit,
    onNavigateBack: () -> Unit,
) {
    val editViewModel = koinViewModel<AiSkillEditViewModel>(
        key = route.skillId.ifBlank { "new" },
    ) { parametersOf(route.skillId) }
    AiSkillEditScreen(
        viewModel = editViewModel,
        onBack = onNavigateBack,
        onSaved = onNavigateBack,
    )
}

@Composable
internal fun AiWebSearchEntry(onNavigateBack: () -> Unit) {
    AiWebSearchConfigScreen(onBack = onNavigateBack)
}

@Composable
internal fun AiProfileEditEntry(
    route: MainRouteSettingsAiProfileEdit,
    onNavigateToRoute: (MainRoute) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val aiProfileGateway: AiProfileGateway = koinInject()
    AiProfileEditScreen(
        providerId = route.providerId,
        aiProfileGateway = aiProfileGateway,
        onBack = onNavigateBack,
        onSaved = { onNavigateBack() },
        onDeleted = { onNavigateBack() },
        onNavigateToModelEdit = { providerId: String, modelProfileId: String? ->
            onNavigateToRoute(MainRouteSettingsAiModelEdit(providerId, modelProfileId))
        },
    )
}

@Composable
internal fun AiModelEditEntry(
    route: MainRouteSettingsAiModelEdit,
    onNavigateBack: () -> Unit,
) {
    val aiProfileGateway: AiProfileGateway = koinInject()
    val aiTextGateway: AiTextGateway = koinInject()
    AiModelEditScreen(
        providerId = route.providerId,
        modelProfileId = route.modelProfileId,
        aiProfileGateway = aiProfileGateway,
        aiTextGateway = aiTextGateway,
        onBack = onNavigateBack,
        onSaved = { onNavigateBack() },
    )
}

@Composable
internal fun AiAbilityManagementEntry(onNavigateBack: () -> Unit) {
    val toolViewModel = koinViewModel<AiAbilityManagementViewModel>()
    AiAbilityManagementScreen(
        viewModel = toolViewModel,
        onBack = onNavigateBack,
    )
}

@Composable
internal fun AiPromptTemplatesEntry(
    onNavigateToRoute: (MainRoute) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val promptTemplateViewModel = koinViewModel<PromptTemplateViewModel>()
    PromptTemplateScreen(
        viewModel = promptTemplateViewModel,
        onBack = onNavigateBack,
        onNavigateToPipeline = {
            onNavigateToRoute(MainRouteSettingsAiPromptPipeline)
        },
    )
}

@Composable
internal fun AiPromptPipelineEntry(onNavigateBack: () -> Unit) {
    val pipelineViewModel = koinViewModel<PromptPipelineViewModel>()
    PromptPipelineScreen(
        viewModel = pipelineViewModel,
        onBack = onNavigateBack,
    )
}

@Composable
internal fun AiDictRuleEntry(onNavigateBack: () -> Unit) {
    AiDictRuleRoute(
        fragment = null,
        onBack = onNavigateBack,
    )
}

@Composable
internal fun AiUsageOverviewEntry(onNavigateBack: () -> Unit) {
    AiUsageOverviewRoute(onBack = onNavigateBack)
}
