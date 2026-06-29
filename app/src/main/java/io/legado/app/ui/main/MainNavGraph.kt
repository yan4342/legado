package io.legado.app.ui.main

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.jaredrummler.android.colorpicker.ColorShape
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.storage.BackupConfig
import io.legado.app.help.storage.ImportOldData
import io.legado.app.help.storage.Restore
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.prefs.ColorPreference
import io.legado.app.ui.book.info.compose.BookInfoRouteScreen
import io.legado.app.ui.book.search.SearchIntent
import io.legado.app.ui.book.search.SearchScreen
import io.legado.app.ui.book.search.SearchViewModel
import io.legado.app.ui.book.explore.ExploreShowIntent
import io.legado.app.ui.book.explore.ExploreShowScreen
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.config.CheckSourceConfig
import io.legado.app.ui.config.CoverRuleConfigDialog
import io.legado.app.ui.config.DirectLinkUploadConfig
import io.legado.app.ui.config.ThemeListDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.dialog.WaitDialog
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
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.inputStream
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.openUrl
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.readUri
import io.legado.app.utils.removePref
import io.legado.app.utils.share
import io.legado.app.utils.showCrashLogSheet
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showLogSheet
import io.legado.app.utils.showMarkdownSheet
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- Config route helpers (migrated from deleted MyFragment) ---

private fun setImageFromUri(context: android.content.Context, uri: Uri, prefKey: String, onSuccess: (() -> Unit)? = null) {
    val act = context as? AppCompatActivity ?: return
    act.readUri(uri) { fileDoc, inputStream ->
        kotlin.runCatching {
            var file = context.externalFiles
            val suffix = fileDoc.name.substringAfterLast(".")
            val fileName = uri.inputStream(context).getOrThrow().use {
                MD5Utils.md5Encode(it) + ".$suffix"
            }
            file = FileUtils.createFileIfNotExist(file, prefKey, fileName)
            FileOutputStream(file).use { inputStream.copyTo(it) }
            context.putPrefString(prefKey, file.absolutePath)
            onSuccess?.invoke()
        }.onFailure {
            appCtx.toastOnUi(it.localizedMessage)
        }
    }
}

private fun setCoverFromUri(context: android.content.Context, uri: Uri, prefKey: String) {
    val act = context as? AppCompatActivity ?: return
    act.readUri(uri) { fileDoc, inputStream ->
        kotlin.runCatching {
            var file = context.externalFiles
            val suffix = fileDoc.name.substringAfterLast(".")
            val fileName = uri.inputStream(context).getOrThrow().use {
                MD5Utils.md5Encode(it) + ".$suffix"
            }
            file = FileUtils.createFileIfNotExist(file, "covers", fileName)
            FileOutputStream(file).use { inputStream.copyTo(it) }
            context.putPrefString(prefKey, file.absolutePath)
            io.legado.app.model.BookCover.upDefaultCover()
        }.onFailure {
            appCtx.toastOnUi(it.localizedMessage)
        }
    }
}

private fun upTheme(context: android.content.Context, isNightTheme: Boolean) {
    if (AppConfig.isNightTheme == isNightTheme) {
        ThemeConfig.applyTheme(context)
        io.legado.app.utils.postEvent(io.legado.app.constant.EventBus.RECREATE, "")
    }
}

private fun selectBgAction(
    context: android.content.Context,
    isNight: Boolean,
    selectBgImage: androidx.activity.result.ActivityResultLauncher<(HandleFileContract.HandleFileParam.() -> Unit)?>,
) {
    val bgKey = if (isNight) PreferKey.bgImageN else PreferKey.bgImage
    val blurringKey = if (isNight) PreferKey.bgImageNBlurring else PreferKey.bgImageBlurring
    val actions = arrayListOf(
        context.getString(R.string.background_image_blurring),
        context.getString(R.string.select_image),
    )
    if (!context.getPrefString(bgKey).isNullOrEmpty()) {
        actions.add(context.getString(R.string.delete))
    }
    context.selector(items = actions) { _, i ->
        when (i) {
            0 -> {
                context.alert(R.string.background_image_blurring) {
                    val alertBinding = io.legado.app.databinding.DialogImageBlurringBinding.inflate(
                        android.view.LayoutInflater.from(context)
                    ).apply {
                        context.getPrefInt(blurringKey, 0).let {
                            seekBar.progress = it
                            textViewValue.text = it.toString()
                        }
                        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(seekBar: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                                textViewValue.text = progress.toString()
                            }
                            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
                        })
                    }
                    customView { alertBinding.root }
                    okButton {
                        alertBinding.seekBar.progress.let {
                            context.putPrefInt(blurringKey, it)
                            upTheme(context, isNight)
                        }
                    }
                    cancelButton()
                }
            }
            1 -> {
                selectBgImage.launch {
                    this.requestCode = if (isNight) 122 else 121
                    this.mode = HandleFileContract.IMAGE
                }
            }
            2 -> {
                context.removePref(bgKey)
                upTheme(context, isNight)
            }
        }
    }
}

// --- Backup helpers (migrated from deleted MyFragment) ---

private fun backupIgnore(context: android.content.Context) {
    val checkedItems = BooleanArray(BackupConfig.ignoreKeys.size) {
        BackupConfig.ignoreConfig[BackupConfig.ignoreKeys[it]] ?: false
    }
    context.alert(R.string.restore_ignore) {
        multiChoiceItems(BackupConfig.ignoreTitle, checkedItems) { _, which, isChecked ->
            BackupConfig.ignoreConfig[BackupConfig.ignoreKeys[which]] = isChecked
        }
        onDismiss {
            BackupConfig.saveIgnoreConfig()
        }
    }
}

private fun webDavRestore(
    context: android.content.Context,
    backupWaitDialog: WaitDialog,
    scope: kotlinx.coroutines.CoroutineScope,
    appCompatActivity: AppCompatActivity?,
) {
    backupWaitDialog.setText(R.string.loading)
    backupWaitDialog.setOnCancelListener { /* handled in launched coroutine */ }
    backupWaitDialog.show()
    scope.launch {
        val job = currentCoroutineContext()[Job]
        backupWaitDialog.setOnCancelListener { job?.cancel() }
        try {
            showRestoreDialog(context, appCompatActivity, backupWaitDialog)
        } catch (e: Exception) {
            AppLog.put("WebDav恢复出错\n${e.localizedMessage}", e)
            appCtx.toastOnUi("WebDav恢复出错\n${e.localizedMessage}")
        } finally {
            backupWaitDialog.dismiss()
        }
    }
}

private suspend fun showRestoreDialog(
    context: android.content.Context,
    appCompatActivity: AppCompatActivity?,
    backupWaitDialog: WaitDialog,
) {
    val names = withContext(Dispatchers.IO) { AppWebDav.getBackupNames() }
    if (AppWebDav.isJianGuoYun && names.size > 700) {
        context.toastOnUi("由于坚果云限制列出文件数量，部分备份可能未显示，请及时清理旧备份")
    }
    if (names.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        withContext(Main) {
            context.selector(
                title = context.getString(R.string.select_restore_file),
                items = names
            ) { _, index ->
                if (index in 0 until names.size) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        restoreWebDav(context, names[index], backupWaitDialog)
                    }
                }
            }
        }
    } else {
        throw NoStackTraceException("Web dav no back up file")
    }
}

private fun restoreWebDav(
    context: android.content.Context,
    name: String,
    backupWaitDialog: WaitDialog,
) {
    backupWaitDialog.setText("恢复中…")
    backupWaitDialog.show()
    val task = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        AppWebDav.restoreWebDav(name)
    }
    task.invokeOnCompletion {
        appCtx.mainLooper.run { backupWaitDialog.dismiss() }
    }
    backupWaitDialog.setOnCancelListener {
        task.cancel()
    }
}

// --- End config route helpers ---

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

    // --- Config route action state (migrated from deleted MyFragment) ---
    val appCompatActivity = remember(context) { context as? AppCompatActivity }
    var pendingColorCallback by remember { mutableStateOf<((Color) -> Unit)?>(null) }
    var pendingBgIsNight by remember { mutableStateOf(false) }
    var pendingBgChange by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingWelcomeIsNight by remember { mutableStateOf(false) }
    var pendingCoverIsNight by remember { mutableStateOf(false) }

    val colorPickerListener = remember {
        object : ColorPickerDialogListener {
            override fun onColorSelected(dialogId: Int, color: Int) {
                pendingColorCallback?.invoke(Color(color))
                pendingColorCallback = null
            }
            override fun onDialogDismissed(dialogId: Int) {
                pendingColorCallback = null
            }
        }
    }

    val selectBgImage = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            val bgKey = if (pendingBgIsNight) PreferKey.bgImageN else PreferKey.bgImage
            setImageFromUri(context, uri, bgKey) { pendingBgChange?.invoke() }
        }
    }

    val selectWelcomeImage = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            val key = if (pendingWelcomeIsNight) PreferKey.welcomeImageDark else PreferKey.welcomeImage
            setImageFromUri(context, uri, key)
        }
    }

    val selectCoverImage = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            val key = if (pendingCoverIsNight) PreferKey.defaultCoverDark else PreferKey.defaultCover
            setCoverFromUri(context, uri, key)
        }
    }
    // --- Backup/restore state (migrated from deleted MyFragment) ---
    val backupWaitDialog = remember { WaitDialog(context) }
    var restoreJob by remember { mutableStateOf<Job?>(null) }

    val selectBackupPath = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            val path = if (uri.isContentScheme()) uri.toString() else uri.path ?: return@rememberLauncherForActivityResult
            AppConfig.backupPath = path
        }
    }

    val restoreDoc = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            backupWaitDialog.setText("恢复中…")
            backupWaitDialog.show()
            val task = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                Restore.restore(appCtx, uri)
            }
            task.invokeOnCompletion {
                appCtx.mainLooper.run { backupWaitDialog.dismiss() }
            }
            backupWaitDialog.setOnCancelListener {
                task.cancel()
            }
        }
    }

    val restoreOld = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            ImportOldData.importUri(appCtx, uri)
        }
    }
    // --- End config route action state ---

    // Helper: read markdown from assets and show sheet
    fun showMdFile(title: String, fileName: String) {
        scope.launch {
            val md = withContext(Dispatchers.IO) {
                runCatching { context.assets.open(fileName).bufferedReader().readText() }.getOrNull() ?: ""
            }
            (context as? FragmentActivity)?.showMarkdownSheet(title, md)
        }
    }

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
                        onShare = { context.share(context.getString(R.string.app_share_description), context.getString(R.string.app_name)) },
                        onScoring = { context.openUrl("market://details?id=${context.packageName}") },
                        onContributors = { context.openUrl(context.getString(R.string.contributors_url)) },
                        onUpdateLog = { showMdFile(context.getString(R.string.update_log), "updateLog.md") },
                        onCheckUpdate = { context.toastOnUi("检查更新功能暂未迁移") },
                        onCrashLog = { (context as? FragmentActivity)?.showCrashLogSheet() },
                        onSaveLog = { context.toastOnUi("保存日志功能暂未迁移") },
                        onCreateHeapDump = { context.toastOnUi("创建堆转储功能暂未迁移") },
                        onPrivacyPolicy = { showMdFile(context.getString(R.string.privacy_policy), "privacyPolicy.md") },
                        onLicense = { showMdFile(context.getString(R.string.license), "LICENSE.md") },
                        onDisclaimer = { showMdFile(context.getString(R.string.disclaimer), "disclaimer.md") },
                    ),
                )
            }

            entry<MainRouteOtherConfig> {
                MyOtherConfigRoute(
                    fragment = null,
                    onBack = onNavigateBack,
                    actions = OtherConfigActions(
                        onCheckSource = { (context as? AppCompatActivity)?.showDialogFragment<CheckSourceConfig>() },
                        onUploadRule = { (context as? AppCompatActivity)?.showDialogFragment<DirectLinkUploadConfig>() },
                    ),
                )
            }

            entry<MainRouteBackupConfig> {
                MyBackupConfigRoute(
                    fragment = null,
                    onBack = onNavigateBack,
                    actions = BackupConfigActions(
                        onBackupPath = { selectBackupPath.launch {} },
                        onRestoreIgnore = { backupIgnore(context) },
                        onImportOld = { restoreOld.launch {} },
                        onLocalRestore = {
                            restoreDoc.launch {
                                this.title = context.getString(R.string.select_restore_file)
                                this.mode = HandleFileContract.FILE
                                this.allowExtensions = arrayOf("zip")
                            }
                        },
                        onWebDavRestore = {
                            webDavRestore(context, backupWaitDialog, scope, appCompatActivity)
                        },
                        onHelp = {
                            scope.launch {
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

            entry<MainRouteThemeConfig> {
                MyThemeConfigRoute(
                    onBack = onNavigateBack,
                    actions = ThemeConfigActions(
                        onRequestColorPicker = { title, currentColor, onChange ->
                            pendingColorCallback = onChange
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
                            dialog.setColorPickerDialogListener(colorPickerListener)
                            appCompatActivity?.supportFragmentManager
                                ?.beginTransaction()
                                ?.add(dialog, "color_$title")
                                ?.commitAllowingStateLoss()
                        },
                        onThemeList = {
                            appCompatActivity?.supportFragmentManager?.let { fm ->
                                ThemeListDialog().show(fm, "themeList")
                            }
                        },
                        onBgImage = { isNight ->
                            pendingBgIsNight = isNight
                            pendingBgChange = {
                                ThemeConfig.applyTheme(context)
                            }
                            selectBgAction(context, isNight, selectBgImage)
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

            entry<MainRouteWelcomeConfig> {
                MyWelcomeConfigRoute(
                    fragment = null,
                    onBack = onNavigateBack,
                    actions = WelcomeConfigActions(
                        onWelcomeImage = { isNight ->
                            pendingWelcomeIsNight = isNight
                            val key = if (isNight) PreferKey.welcomeImageDark else PreferKey.welcomeImage
                            if (context.getPrefString(key).isNullOrEmpty()) {
                                selectWelcomeImage.launch {
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
                                        selectWelcomeImage.launch {
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

            entry<MainRouteCoverConfig> {
                MyCoverConfigRoute(
                    fragment = null,
                    onBack = onNavigateBack,
                    actions = CoverConfigActions(
                        onCoverRule = {
                            (context as? AppCompatActivity)?.showDialogFragment<CoverRuleConfigDialog>()
                        },
                        onDefaultCover = { isNight ->
                            pendingCoverIsNight = isNight
                            val key = if (isNight) PreferKey.defaultCoverDark else PreferKey.defaultCover
                            if (context.getPrefString(key).isNullOrEmpty()) {
                                selectCoverImage.launch {
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
                                        selectCoverImage.launch {
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
