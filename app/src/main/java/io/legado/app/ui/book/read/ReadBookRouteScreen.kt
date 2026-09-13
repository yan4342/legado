package io.legado.app.ui.book.read

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadStyleRefreshBus
import io.legado.app.help.config.AppConfigStore
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.prefs.ColorPreference
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.info.compose.BookInfoComposeActivity
import io.legado.app.ui.book.read.config.ReadAloudConfigDialog
import io.legado.app.ui.book.read.config.ReadAloudDialog
import io.legado.app.ui.book.read.config.ReadConfigIds.BG_COLOR
import io.legado.app.ui.book.read.config.ReadConfigIds.TEXT_COLOR
import io.legado.app.ui.book.read.config.compose.MoreConfigSheet
import io.legado.app.ui.book.read.config.compose.ReadStyleSheet
import io.legado.app.ui.book.readaloud.casting.BookVoiceCastingRouteScreen
import io.legado.app.ui.book.readaloud.cloudtts.CloudTtsRouteScreen
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerEffect
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerIntent
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerScreen
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerViewModel
import io.legado.app.ui.book.searchContent.SearchContentActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.common.compose.M3NumberPickerDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.font.FontSelectDialog
import io.legado.app.ui.main.MainRouteReadBook
import io.legado.app.utils.ACache
import io.legado.app.constant.AppConst
import io.legado.app.utils.FileDoc
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.find
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.showDialogFragment
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

/**
 * 阶段 4 阅读页路由化：主栈阅读页 Compose 壳。
 * AndroidView 承载 View 渲染层（ReadBookViewLayer）+ Compose 侧配置 Sheet/胶囊，
 * 生命周期语义经 DisposableEffect/LifecycleObserver 映射到 ReadBookController。
 */
@Composable
internal fun ReadBookEntry(
    route: MainRouteReadBook,
    onNavigateBack: () -> Unit,
    readBookRouteState: ReadBookRouteState,
    isPopped: () -> Boolean,
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    val viewModel: ReadBookViewModel = koinViewModel(key = "ReadBook:${route.bookUrl ?: "last-read"}")
    val launchers = remember { ReadBookLaunchers() }
    val controller = remember(viewModel) {
        ReadBookController(activity, viewModel, route, readBookRouteState, launchers) {
            readBookRouteState.pendingExit = true
            onNavigateBack()
        }
    }

    // 赶首帧：ReadView 首绘前书目必须就绪（MD3 同款 remember 而非 LaunchedEffect）
    remember(viewModel, route) {
        viewModel.initReadBookConfig(controller.routeIntent)
        if (!viewModel.isInitFinish) {
            viewModel.initData(controller.routeIntent)
        }
        true
    }

    // ── ActivityResult 桥（幂等赋值，模式同 MainConfigRouteActions） ──
    val tocLauncher = rememberLauncherForActivityResult(TocActivityResult()) { result ->
        result?.let { (i, p, _) ->
            viewModel.openChapter(i, p)
        }
    }
    launchers.toc = { tocLauncher.launch(it) }

    val sourceEditLauncher = rememberLauncherForActivityResult(
        StartActivityContract(BookSourceEditActivity::class.java)
    ) {
        if (it.resultCode == AppCompatActivity.RESULT_OK) {
            viewModel.upBookSource {
                controller.upMenuView()
            }
        }
    }
    launchers.sourceEdit = { sourceEditLauncher.launch(it) }

    val replaceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == AppCompatActivity.RESULT_OK) {
            viewModel.replaceRuleChanged()
        }
    }
    launchers.replace = { replaceLauncher.launch(it) }

    val searchContentLauncher = rememberLauncherForActivityResult(
        StartActivityContract(SearchContentActivity::class.java)
    ) {
        val data = it.data ?: return@rememberLauncherForActivityResult
        val key = data.getLongExtra("key", System.currentTimeMillis())
        val index = data.getIntExtra("index", 0)
        controller.applySearchJump(key, index)
    }
    launchers.searchContent = { searchContentLauncher.launch(it) }

    val bookInfoLauncher = rememberLauncherForActivityResult(
        StartActivityContract(BookInfoComposeActivity::class.java)
    ) {
        if (it.resultCode == AppCompatActivity.RESULT_OK) {
            readBookRouteState.bookDeleted = true
            readBookRouteState.pendingExit = true
            onNavigateBack()
        } else {
            ReadBook.loadOrUpContent()
        }
    }
    launchers.bookInfoDone = { bookInfoLauncher.launch {} }

    val selectImageDirLauncher = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        result?.uri?.let { uri ->
            ACache.get().put(AppConst.imagePathKey, uri.toString())
            viewModel.saveImage(result.value, uri)
        }
    }
    launchers.selectImageDir = { selectImageDirLauncher.launch(it) }

    val selectBookFolderLauncher = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        result?.uri?.let { uri ->
            ReadBook.book?.let { book ->
                FileDoc.fromUri(uri, true).find(book.originName)?.let { doc ->
                    book.bookUrl = doc.uri.toString()
                    book.save()
                    viewModel.loadChapterList(book)
                } ?: ReadBook.upMsg("找不到文件")
            }
        } ?: ReadBook.upMsg("没有权限访问")
    }
    launchers.selectBookFolder = { selectBookFolderLauncher.launch(it) }

    // 弹窗硬转点过渡桥（M3 弹窗 Compose 化后删除）
    SideEffect {
        ReadBookRouteState.controllerRef = controller
    }

    // 状态栏色带：BaseComposeActivity 在 Content 之上常驻绘制状态栏色块（默认 primary），
    // 阅读页需要沉浸——进入时置透明，退出恢复默认（书架/详情等页面自行覆写自己的色值）
    val statusBarColorFlow = io.legado.app.base.LocalStatusBarColor.current
    DisposableEffect(statusBarColorFlow) {
        statusBarColorFlow?.value = Color.Transparent
        onDispose {
            statusBarColorFlow?.value = null
        }
    }

    // ── 生命周期映射 ──
    DisposableEffect(controller) {
        controller.onResumed()
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> controller.onPaused()
                Lifecycle.Event.ON_RESUME -> controller.onResumed()
                else -> {}
            }
        }
        activity.lifecycle.addObserver(obs)
        onDispose {
            activity.lifecycle.removeObserver(obs)
            controller.onPaused()
            if (isPopped()) {
                controller.onDestroyCleanup()
            }
            if (ReadBookRouteState.controllerRef === controller) {
                ReadBookRouteState.controllerRef = null
            }
        }
    }

    SideEffect {
        controller.observeEvents()
    }

    // ── 返回键拦截链（原 onActivityCreated :597-639） ──
    BackHandler {
        if (!controller.handleBack()) {
            controller.exitReader()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ReadBookViewLayer(
            readViewCallBack = controller,
            contentTextCallBack = controller,
            searchCallBack = controller,
            onRefsReady = { refs ->
                controller.refs = refs
                controller.onCreatedSetup()
            },
        )

        // ── Compose 侧弹层（原 setupComposeSheets overlay 平移为原生组合） ──
        ReadStyleSheet(
            show = controller.showReadStyleSheet,
            onDismiss = {
                controller.bottomDialog--
                controller.showReadStyleSheet = false
            },
            onFontSelect = { activity.showDialogFragment<FontSelectDialog>() },
            onTextColorClick = { color ->
                ColorPreference.ColorPickerDialogCompat.newBuilder()
                    .setColor(color)
                    .setShowAlphaSlider(false)
                    .setDialogType(com.jaredrummler.android.colorpicker.ColorPickerDialog.TYPE_CUSTOM)
                    .setDialogId(TEXT_COLOR)
                    .create().apply {
                        show(activity.supportFragmentManager, "textColorPicker")
                    }
            },
            onBgColorClick = { color ->
                ColorPreference.ColorPickerDialogCompat.newBuilder()
                    .setColor(color)
                    .setShowAlphaSlider(false)
                    .setDialogType(com.jaredrummler.android.colorpicker.ColorPickerDialog.TYPE_CUSTOM)
                    .setDialogId(BG_COLOR)
                    .create().apply {
                        show(activity.supportFragmentManager, "bgColorPicker")
                    }
            },
        )
        MoreConfigSheet(
            show = controller.showMoreConfigSheet,
            onDismiss = {
                controller.bottomDialog--
                controller.showMoreConfigSheet = false
            },
            onOrientationChange = {
                val items = activity.resources.getStringArray(R.array.screen_direction_title).toList()
                activity.selector(
                    activity.getString(R.string.screen_direction),
                    items,
                ) { _, i ->
                    AppConfig.screenOrientation = i.toString()
                    controller.setOrientation()
                }
            },
            onReadBodyToLh = { controller.recreateSubstitute() },
            onClickRegionalConfig = { controller.showClickRegionalConfig() },
            onCustomPageKey = { controller.showCustomPageKeyConfig() },
            onPageTouchSlop = {
                activity.showDialogFragment(
                    M3NumberPickerDialog.create(
                        title = activity.getString(R.string.page_touch_slop_dialog_title),
                        value = AppConfig.pageTouchSlop,
                        minValue = 0,
                        maxValue = 9999,
                        onConfirm = {
                            AppConfig.pageTouchSlop = it
                            ReadStyleRefreshBus.refresh(4)
                        }
                    )
                )
            },
            onRecreate = { controller.recreateSubstitute() },
        )
        TextActionSelectionMenu(
            menuState = controller.textActionMenuState,
            expandTextMenu = controller.expandTextMenu,
            onDismiss = {
                controller.textActionMenuState = null
                controller.cancelSelect()
            },
            onItemClick = controller::onTextActionItemClick,
            onItemLongClick = controller::onTextActionItemLongClick,
            onOpenManage = {
                controller.textActionMenuState = null
                controller.cancelSelect()
                controller.textMenuConfigItems = buildTextActionMenuItems(activity, false)
                controller.showTextMenuConfigSheet = true
            },
        )
        TextMenuConfigSheet(
            show = controller.showTextMenuConfigSheet,
            items = controller.textMenuConfigItems,
            expandTextMenu = controller.expandTextMenu,
            onExpandTextMenuChange = { checked ->
                controller.expandTextMenu = checked
                AppConfigStore.putBoolean(PreferKey.expandTextMenu, checked)
                // SP 镜像：Backup 的 config.xml 来自 SP 全量，Phase 4 前必须保留
                activity.putPrefBoolean(PreferKey.expandTextMenu, checked)
            },
            onDismissRequest = { controller.showTextMenuConfigSheet = false },
            onSaved = { items -> saveTextMenuConfig(activity, items) },
        )
        if (controller.showReadAloudPlayer) {
            val playerViewModel: ReadAloudPlayerViewModel = koinViewModel()
            BackHandler(enabled = !controller.showVoiceCastingOverlay && !controller.showCloudTtsOverlay) {
                controller.hideReadAloudPlayerOverlay()
            }
            LaunchedEffect(Unit) {
                playerViewModel.onIntent(ReadAloudPlayerIntent.Refresh)
                playerViewModel.effects.collectLatest { effect ->
                    when (effect) {
                        ReadAloudPlayerEffect.OpenToc -> {
                            controller.openChapterList()
                        }
                        ReadAloudPlayerEffect.ReturnToClassic -> {
                            controller.hideReadAloudPlayerOverlay(openClassic = true)
                        }
                        ReadAloudPlayerEffect.ReturnToReaderSettings -> {
                            activity.showDialogFragment<ReadAloudConfigDialog>()
                        }
                    }
                }
            }
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                ReadAloudPlayerScreen(
                    state = playerViewModel.uiState.collectAsStateWithLifecycle().value,
                    onIntent = playerViewModel::onIntent,
                    onBack = { controller.hideReadAloudPlayerOverlay() },
                    onNavigateToCasting = ReadBook.book?.bookUrl?.let { _ ->
                        {
                            controller.showVoiceCastingOverlay = true
                        }
                    },
                    onNavigateToCloudTts = {
                        controller.showCloudTtsOverlay = true
                    },
                )
            }
        } else if (
            AppConfig.showReadAloudCapsule &&
            controller.readAloudRunning
        ) {
            ReadAloudCapsule(
                isPaused = controller.readAloudPaused,
                bookName = ReadBook.book?.name.orEmpty(),
                onTogglePause = { controller.onClickReadAloud() },
                onStop = { ReadAloud.stop(activity) },
                onOpenPlayer = { controller.showReadAloudPlayerOverlay() },
            )
        }
        if (controller.showVoiceCastingOverlay) {
            val bookUrl = ReadBook.book?.bookUrl.orEmpty()
            BackHandler(enabled = !controller.showCloudTtsOverlay) {
                controller.showVoiceCastingOverlay = false
            }
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                BookVoiceCastingRouteScreen(
                    bookUrl = bookUrl,
                    onBack = { controller.showVoiceCastingOverlay = false },
                    onManageCloudTts = { controller.showCloudTtsOverlay = true },
                )
            }
        }
        if (controller.showCloudTtsOverlay) {
            BackHandler {
                controller.showCloudTtsOverlay = false
            }
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                CloudTtsRouteScreen(
                    onBack = { controller.showCloudTtsOverlay = false },
                )
            }
        }

        // 阅读菜单（M3：Compose 化，替代 View 版 ReadMenu）
        ReadBookMenuPanel(controller)

        // 阅读锚点/朗读脱离胶囊（原 decorView ComposeView 附着改为原生组合）
        ReadingFloatingCapsulesContent()
    }
}
