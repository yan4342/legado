package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.core.view.get
import androidx.core.view.size
import androidx.lifecycle.lifecycleScope
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Status
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.IntentData
import io.legado.app.help.TTS
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isEpub
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.book.isMobi
import io.legado.app.help.book.removeType
import io.legado.app.help.book.update
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadStyleRefreshBus
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.domain.usecase.ExecuteResult
import io.legado.app.domain.usecase.ReChapterUseCase
import io.legado.app.help.source.getSourceType
import io.legado.app.help.storage.Backup
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setChapter
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.localBook.EpubFile
import io.legado.app.model.localBook.MobiFile
import io.legado.app.receiver.NetworkChangedListener
import io.legado.app.receiver.TimeBatteryReceiver
import io.legado.app.service.BaseReadAloudService
import splitties.systemservices.keyguardManager
import splitties.systemservices.powerManager
import io.legado.app.utils.showLogSheet
import io.legado.app.ui.book.bookmark.BookmarkDialog
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.changesource.ChangeChapterSourceDialog
import io.legado.app.ui.book.info.compose.BookInfoComposeActivity
import io.legado.app.ui.book.read.config.AutoReadDialog
import io.legado.app.ui.book.read.config.ReadConfigIds.BG_COLOR
import io.legado.app.ui.book.read.config.ReadConfigIds.TEXT_COLOR
import io.legado.app.ui.book.read.config.ReadConfigIds.TIP_COLOR
import io.legado.app.ui.book.read.config.ReadConfigIds.TIP_DIVIDER_COLOR
import io.legado.app.lib.prefs.ColorPreference
import io.legado.app.ui.book.read.config.compose.MoreConfigSheet
import io.legado.app.ui.book.read.config.compose.ReadStyleSheet
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import io.legado.app.ui.book.read.config.ReadAloudConfigDialog
import io.legado.app.ui.book.read.config.ReadAloudDialog
import io.legado.app.ui.book.readaloud.casting.BookVoiceCastingRouteScreen
import io.legado.app.ui.book.readaloud.cloudtts.CloudTtsRouteScreen
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerEffect
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerIntent
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerScreen
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerViewModel
import io.legado.app.ui.common.compose.LegadoTheme
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.LayoutProgressListener
import io.legado.app.ui.book.searchContent.SearchContentActivity
import io.legado.app.ui.book.searchContent.SearchResult
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.browser.WebViewActivity
import io.legado.app.ui.dict.DictSearchContext
import io.legado.app.ui.dict.createDictSheetDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.replace.ReplaceEditRoute
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.ui.widget.PopupAction
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.ui.common.compose.M3NumberPickerDialog
import io.legado.app.utils.ACache
import io.legado.app.utils.Debounce
import io.legado.app.utils.LogUtils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.StartActivityContract
import io.legado.app.ui.common.compose.RoundDropdownMenuItem
import io.legado.app.ui.common.compose.showComposeDropdownMenu
import io.legado.app.ui.font.FontSelectDialog
import io.legado.app.utils.visible
import io.legado.app.utils.buildMainHandler
import io.legado.app.utils.dismissDialogFragment
import io.legado.app.utils.hexString
import io.legado.app.utils.iconItemOnLongClick
import io.legado.app.utils.invisible
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.isTrue
import io.legado.app.utils.launch
import io.legado.app.utils.navigationBarGravity
import io.legado.app.utils.observeEvent
import io.legado.app.utils.observeEventSticky
import io.legado.app.utils.postEvent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.sysScreenOffTime
import io.legado.app.utils.throttle
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 阅读界面
 */
class ReadBookActivity : BaseReadBookActivity(),
    View.OnTouchListener,
    ReadView.CallBack,
    ContentTextView.CallBack,
    PopupMenu.OnMenuItemClickListener,
    ReadMenu.CallBack,
    SearchMenu.CallBack,
    ReadAloudDialog.CallBack,
    ChangeBookSourceDialog.CallBack,
    ChangeChapterSourceDialog.CallBack,
    ReadBook.CallBack,
    AutoReadDialog.CallBack,
    FontSelectDialog.CallBack,
    ColorPickerDialogListener,
    LayoutProgressListener {

    private val tocActivity =
        registerForActivityResult(TocActivityResult()) {
            it?.let {
                viewModel.openChapter(it.first, it.second)
            }
        }
    private val sourceEditActivity =
        registerForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
            if (it.resultCode == RESULT_OK) {
                viewModel.upBookSource {
                    upMenuView()
                }
            }
        }
    private val replaceActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                viewModel.replaceRuleChanged()
            }
        }
    private val searchContentActivity =
        registerForActivityResult(StartActivityContract(SearchContentActivity::class.java)) {
            val data = it.data ?: return@registerForActivityResult
            val key = data.getLongExtra("key", System.currentTimeMillis())
            val index = data.getIntExtra("index", 0)
            applySearchJumpFromIntentData(key, index)
        }
    private val bookInfoActivity =
        registerForActivityResult(StartActivityContract(BookInfoComposeActivity::class.java)) {
            if (it.resultCode == RESULT_OK) {
                setResult(RESULT_DELETED)
                super.finish()
            } else {
                ReadBook.loadOrUpContent()
            }
        }
    private val selectImageDir = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            ACache.get().put(AppConst.imagePathKey, uri.toString())
            viewModel.saveImage(it.value, uri)
        }
    }
    private var menu: Menu? = null
    private var backupJob: Job? = null
    private var tts: TTS? = null
    override val curFontPath: String
        get() = ReadBookConfig.textFont
    override fun selectFont(path: String) {
        if (path != ReadBookConfig.textFont || path.isEmpty()) {
            ReadBookConfig.textFont = path
            postEvent(EventBus.UP_CONFIG, arrayListOf(2, 5))
        }
    }
    private val popupAction: PopupAction by lazy {
        PopupAction(this)
    }
    override val isInitFinish: Boolean get() = viewModel.isInitFinish
    override val isScroll: Boolean get() = binding.readView.isScroll
    private val isAutoPage get() = binding.readView.isAutoPage
    override var isShowingSearchResult = false
    override var isSelectingSearchResult = false
        set(value) {
            field = value && isShowingSearchResult
        }
    private val timeBatteryReceiver = TimeBatteryReceiver()
    /**
     * 阅读时长统计：仅"界面前台 + 亮屏 + 未锁屏"时累计。
     * 熄屏瞬间经 SCREEN_OFF 广播结算，退后台在 onPause 结算；
     * 另有低频定时结算兜底，即使生命周期/广播被 ROM 吞掉，误差也不超过一个周期
     */
    private var isResumedState = false
    private val screenStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    ReadBook.upReadTime()
                    ReadBook.saveRead()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT ->
                    if (canCountReadTime()) ReadBook.startCounting()
            }
        }
    }
    private val screenStatusFilter = IntentFilter(Intent.ACTION_SCREEN_OFF).apply {
        addAction(Intent.ACTION_SCREEN_ON)
        addAction(Intent.ACTION_USER_PRESENT)
    }

    private fun canCountReadTime(): Boolean =
        isResumedState && powerManager.isInteractive && !keyguardManager.isKeyguardLocked

    private val readTimeTickRunnable = object : Runnable {
        override fun run() {
            if (!isResumedState) return
            ReadBook.upReadTime()
            if (canCountReadTime()) ReadBook.startCounting()
            handler.postDelayed(this, READ_TIME_TICK_MS)
        }
    }
    private var screenTimeOut: Long = 0
    private var loadStates: Boolean = false
    override val pageFactory get() = binding.readView.pageFactory
    override val pageDelegate get() = binding.readView.pageDelegate
    override val headerHeight: Int get() = binding.readView.curPage.headerHeight
    private val nextPageDebounce by lazy { Debounce { keyPage(PageDirection.NEXT) } }
    private val prevPageDebounce by lazy { Debounce { keyPage(PageDirection.PREV) } }
    private var bookChanged = false
    private var pageChanged = false
    private val handler by lazy { buildMainHandler() }
    private val screenOffRunnable by lazy { Runnable { keepScreenOn(false) } }
    private val executor = ReadBook.executor
    private val upSeekBarThrottle = throttle(200) {
        runOnUiThread {
            upSeekBarProgress()
            binding.readMenu.upSeekBar()
        }
    }

    //恢复跳转前进度对话框的交互结果
    private var confirmRestoreProcess: Boolean? = null
    /** AI / standalone SearchContent → ReadBook search jump pending until content is ready. */
    private var pendingSearchJumpKey: Long? = null
    private var pendingSearchJumpIndex: Int = 0
    private val networkChangedListener by lazy {
        NetworkChangedListener(this)
    }
    private var justInitData: Boolean = false
    private var syncDialog: AlertDialog? = null

    // Compose sheet state
    private var showReadStyleSheet by mutableStateOf(false)
    private var showMoreConfigSheet by mutableStateOf(false)
    private var showReadAloudPlayer by mutableStateOf(false)
    private var showVoiceCastingOverlay by mutableStateOf(false)
    private var showCloudTtsOverlay by mutableStateOf(false)
    private var readAloudRunning by mutableStateOf(false)
    private var readAloudPaused by mutableStateOf(true)
    private var textActionMenuState by mutableStateOf<TextMenuState?>(null)
    private var expandTextMenu by mutableStateOf(false)
    private var showTextMenuConfigSheet by mutableStateOf(false)
    private var textMenuConfigItems by mutableStateOf(emptyList<ActionMenuItem>())

    private fun setupComposeSheets() {
        composeSheetsView.setContent {
            LegadoTheme {
                ReadStyleSheet(
                    show = showReadStyleSheet,
                    onDismiss = { bottomDialog--; showReadStyleSheet = false; syncComposeOverlayChrome() },
                    onFontSelect = { showDialogFragment<FontSelectDialog>() },
                    onTextColorClick = { color ->
                        ColorPreference.ColorPickerDialogCompat.newBuilder()
                            .setColor(color)
                            .setShowAlphaSlider(false)
                            .setDialogType(com.jaredrummler.android.colorpicker.ColorPickerDialog.TYPE_CUSTOM)
                            .setDialogId(TEXT_COLOR)
                            .create().apply {
                                show(this@ReadBookActivity.supportFragmentManager, "textColorPicker")
                            }
                    },
                    onBgColorClick = { color ->
                        ColorPreference.ColorPickerDialogCompat.newBuilder()
                            .setColor(color)
                            .setShowAlphaSlider(false)
                            .setDialogType(com.jaredrummler.android.colorpicker.ColorPickerDialog.TYPE_CUSTOM)
                            .setDialogId(BG_COLOR)
                            .create().apply {
                                show(this@ReadBookActivity.supportFragmentManager, "bgColorPicker")
                            }
                    },
                )
                MoreConfigSheet(
                    show = showMoreConfigSheet,
                    onDismiss = { bottomDialog--; showMoreConfigSheet = false; syncComposeOverlayChrome() },
                    onOrientationChange = {
                        val items = resources.getStringArray(R.array.screen_direction_title).toList()
                        this@ReadBookActivity.selector(
                            getString(R.string.screen_direction),
                            items,
                        ) { _, i ->
                            AppConfig.screenOrientation = i.toString()
                            setOrientation()
                        }
                    },
                    onReadBodyToLh = { recreate() },
                    onClickRegionalConfig = { showClickRegionalConfig() },
                    onCustomPageKey = { showCustomPageKeyConfig() },
                    onPageTouchSlop = {
                        showDialogFragment(
                            M3NumberPickerDialog.create(
                                title = getString(R.string.page_touch_slop_dialog_title),
                                value = AppConfig.pageTouchSlop,
                                minValue = 0,
                                maxValue = 9999,
                                onConfirm = {
                                    AppConfig.pageTouchSlop = it
                                    postEvent(EventBus.UP_CONFIG, listOf(4))
                                }
                            )
                        )
                    },
                    onRecreate = { recreate() },
                )
                TextActionSelectionMenu(
                    menuState = textActionMenuState,
                    expandTextMenu = expandTextMenu,
                    onDismiss = {
                        textActionMenuState = null
                        binding.readView.cancelSelect()
                    },
                    onItemClick = ::onTextActionItemClick,
                    onItemLongClick = ::onTextActionItemLongClick,
                    onOpenManage = {
                        textActionMenuState = null
                        binding.readView.cancelSelect()
                        textMenuConfigItems = buildTextActionMenuItems(this, false)
                        showTextMenuConfigSheet = true
                    },
                )
                TextMenuConfigSheet(
                    show = showTextMenuConfigSheet,
                    items = textMenuConfigItems,
                    expandTextMenu = expandTextMenu,
                    onExpandTextMenuChange = { checked ->
                        expandTextMenu = checked
                        AppConfigStore.putBoolean(PreferKey.expandTextMenu, checked)
                        // SP 镜像：Backup 的 config.xml 来自 SP 全量，Phase 4 前必须保留
                        putPrefBoolean(PreferKey.expandTextMenu, checked)
                    },
                    onDismissRequest = { showTextMenuConfigSheet = false },
                    onSaved = { items -> saveTextMenuConfig(this, items) },
                )
                if (showReadAloudPlayer) {
                    val playerViewModel: ReadAloudPlayerViewModel = koinViewModel()
                    BackHandler(enabled = !showVoiceCastingOverlay && !showCloudTtsOverlay) {
                        hideReadAloudPlayerOverlay()
                    }
                    LaunchedEffect(Unit) {
                        playerViewModel.onIntent(ReadAloudPlayerIntent.Refresh)
                        playerViewModel.effects.collectLatest { effect ->
                            when (effect) {
                                ReadAloudPlayerEffect.OpenToc -> {
                                    openChapterList()
                                }
                                ReadAloudPlayerEffect.ReturnToClassic -> {
                                    hideReadAloudPlayerOverlay(openClassic = true)
                                }
                                ReadAloudPlayerEffect.ReturnToReaderSettings -> {
                                    showDialogFragment<ReadAloudConfigDialog>()
                                }
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        ReadAloudPlayerScreen(
                            state = playerViewModel.uiState.collectAsStateWithLifecycle().value,
                            onIntent = playerViewModel::onIntent,
                            onBack = { hideReadAloudPlayerOverlay() },
                            onNavigateToCasting = ReadBook.book?.bookUrl?.let { bookUrl ->
                                {
                                    showVoiceCastingOverlay = true
                                    syncComposeOverlayChrome()
                                }
                            },
                            onNavigateToCloudTts = {
                                showCloudTtsOverlay = true
                                syncComposeOverlayChrome()
                            },
                        )
                    }
                } else if (
                    AppConfig.showReadAloudCapsule &&
                    readAloudRunning
                ) {
                    ReadAloudCapsule(
                        isPaused = readAloudPaused,
                        bookName = ReadBook.book?.name.orEmpty(),
                        onTogglePause = { onClickReadAloud() },
                        onStop = {
                            ReadAloud.stop(this@ReadBookActivity)
                        },
                        onOpenPlayer = { showReadAloudPlayerOverlay() },
                    )
                }
                if (showVoiceCastingOverlay) {
                    val bookUrl = ReadBook.book?.bookUrl.orEmpty()
                    BackHandler(enabled = !showCloudTtsOverlay) {
                        showVoiceCastingOverlay = false
                        syncComposeOverlayChrome()
                    }
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        BookVoiceCastingRouteScreen(
                            bookUrl = bookUrl,
                            onBack = {
                                showVoiceCastingOverlay = false
                                syncComposeOverlayChrome()
                            },
                            onManageCloudTts = {
                                showCloudTtsOverlay = true
                                syncComposeOverlayChrome()
                            },
                        )
                    }
                }
                if (showCloudTtsOverlay) {
                    BackHandler {
                        showCloudTtsOverlay = false
                        syncComposeOverlayChrome()
                    }
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        CloudTtsRouteScreen(
                            onBack = {
                                showCloudTtsOverlay = false
                                syncComposeOverlayChrome()
                            },
                        )
                    }
                }
            }
        }
        syncComposeOverlayChrome()
    }

    private fun syncComposeOverlayChrome() {
        val lp = composeSheetsView.layoutParams as? FrameLayout.LayoutParams ?: return
        val fullOverlay = showReadAloudPlayer ||
            showReadStyleSheet ||
            showMoreConfigSheet ||
            showVoiceCastingOverlay ||
            showCloudTtsOverlay
        if (fullOverlay) {
            lp.width = FrameLayout.LayoutParams.MATCH_PARENT
            lp.height = FrameLayout.LayoutParams.MATCH_PARENT
            lp.gravity = Gravity.TOP
        } else {
            lp.width = FrameLayout.LayoutParams.MATCH_PARENT
            lp.height = FrameLayout.LayoutParams.WRAP_CONTENT
            lp.gravity = Gravity.BOTTOM
        }
        composeSheetsView.layoutParams = lp
        val intercept = showReadAloudPlayer || showVoiceCastingOverlay || showCloudTtsOverlay
        composeSheetsView.isClickable = intercept
        composeSheetsView.isFocusable = intercept
    }

    fun showReadAloudPlayerOverlay() {
        if (showReadAloudPlayer) return
        supportFragmentManager.fragments
            .filterIsInstance<ReadAloudDialog>()
            .forEach { it.dismissAllowingStateLoss() }
        showReadAloudPlayer = true
        bottomDialog++
        syncComposeOverlayChrome()
    }

    private fun hideReadAloudPlayerOverlay(openClassic: Boolean = false) {
        showCloudTtsOverlay = false
        showVoiceCastingOverlay = false
        if (showReadAloudPlayer) {
            showReadAloudPlayer = false
            bottomDialog = (bottomDialog - 1).coerceAtLeast(0)
        }
        syncComposeOverlayChrome()
        if (openClassic) {
            showDialogFragment<ReadAloudDialog>()
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        setupComposeSheets()
        viewModel.initReadBookConfig(intent)
        Looper.myQueue().addIdleHandler {
            viewModel.initData(intent)
            false
        }
        justInitData = true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        binding.cursorLeft.setColorFilter(accentColor)
        binding.cursorRight.setColorFilter(accentColor)
        binding.cursorLeft.setOnTouchListener(this)
        binding.cursorRight.setOnTouchListener(this)
        window.setBackgroundDrawable(null)
        upScreenTimeOut()
        ReadBook.register(this)
        attachReadingFloatingCapsules(this)
        capturePendingSearchJump(intent)
        onBackPressedDispatcher.addCallback(this) {
            when {
                showCloudTtsOverlay -> {
                    showCloudTtsOverlay = false
                    syncComposeOverlayChrome()
                    return@addCallback
                }
                showVoiceCastingOverlay -> {
                    showVoiceCastingOverlay = false
                    syncComposeOverlayChrome()
                    return@addCallback
                }
                showReadAloudPlayer -> {
                    hideReadAloudPlayerOverlay()
                    return@addCallback
                }
            }
            if (isShowingSearchResult) {
                exitSearchMenu()
                restoreLastBookProcess()
                return@addCallback
            }
            //拦截返回供恢复阅读进度
            if (ReadBook.lastBookProgress != null && confirmRestoreProcess != false) {
                restoreLastBookProcess()
                return@addCallback
            }
            if (BaseReadAloudService.isPlay()) {
                ReadAloud.pause(this@ReadBookActivity)
                toastOnUi(R.string.read_aloud_pause)
                return@addCallback
            }
            if (isAutoPage) {
                autoPageStop()
                return@addCallback
            }
            if ((AppConfigStore.getBoolean(PreferKey.disableReturnKey) ?: false)
                && !menuLayoutIsVisible
            ) {
                return@addCallback
            }
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        capturePendingSearchJump(intent)
        viewModel.initData(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        upSystemUiVisibility()
        if (hasFocus) {
            binding.readMenu.upBrightnessState()
        } else if (!menuLayoutIsVisible) {
            ReadBook.cancelPreDownloadTask()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        upSystemUiVisibility()
        binding.readView.upStatusBar()
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        if (!isTopResumedActivity) {
            ReadBook.cancelPreDownloadTask()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        isResumedState = true
        if (canCountReadTime()) {
            ReadBook.startCounting()
        }
        if (bookChanged) {
            bookChanged = false
            ReadBook.callBack = this
            viewModel.initData(intent)
            justInitData = true
        } else {
            //web端阅读时，app处于阅读界面，本地记录会覆盖web保存的进度，在此处恢复
            ReadBook.webBookProgress?.let {
                ReadBook.setProgress(it)
                ReadBook.webBookProgress = null
            }
        }
        upSystemUiVisibility()
        registerReceiver(screenStatusReceiver, screenStatusFilter)
        registerReceiver(timeBatteryReceiver, timeBatteryReceiver.filter)
        binding.readView.upTime()
        screenOffTimerStart()
        handler.removeCallbacks(readTimeTickRunnable)
        handler.postDelayed(readTimeTickRunnable, READ_TIME_TICK_MS)
        // 网络监听，当从无网切换到网络环境时同步进度（注意注册的同时就会收到监听，因此界面激活时无需重复执行同步操作）
        networkChangedListener.register()
        networkChangedListener.onNetworkChanged = {
            // 当网络是可用状态且无需初始化时同步进度（初始化中已有同步进度逻辑）
            if (AppConfig.syncBookProgressPlus && NetworkUtils.isAvailable() && !justInitData && ReadBook.inBookshelf) {
                ReadBook.syncProgress({ progress -> sureNewProgress(progress) })
            }
        }
    }

    override fun onPause() {
        super.onPause()
        autoPageStop()
        backupJob?.cancel()
        isResumedState = false
        handler.removeCallbacks(readTimeTickRunnable)
        ReadBook.upReadTime()
        ReadBook.saveRead()
        ReadBook.cancelPreDownloadTask()
        unregisterReceiver(screenStatusReceiver)
        unregisterReceiver(timeBatteryReceiver)
        upSystemUiVisibility()
        if (!BuildConfig.DEBUG && ReadBook.inBookshelf) {
            if (AppConfig.syncBookProgressPlus) {
                ReadBook.syncProgress()
            } else {
                ReadBook.uploadProgress()
            }
        }
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
        justInitData = false
        networkChangedListener.unRegister()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_read, menu)
        menu.iconItemOnLongClick(R.id.menu_change_source) { anchor ->
            showComposeDropdownMenu(this, anchor) { dismiss ->
                RoundDropdownMenuItem(
                    text = getString(R.string.chapter_change_source),
                    onClick = { dismiss(); lifecycleScope.launch {
                        val book = ReadBook.book ?: return@launch
                        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex) ?: return@launch
                        binding.readMenu.runMenuOut()
                        showDialogFragment(ChangeChapterSourceDialog(book.name, book.author, chapter.index, chapter.title))
                    } },
                )
                RoundDropdownMenuItem(
                    text = getString(R.string.book_change_source),
                    onClick = { dismiss(); binding.readMenu.runMenuOut(); ReadBook.book?.let { showDialogFragment(ChangeBookSourceDialog(it.name, it.author)) } },
                )
            }
        }
        menu.iconItemOnLongClick(R.id.menu_refresh) { anchor ->
            showComposeDropdownMenu(this, anchor) { dismiss ->
                RoundDropdownMenuItem(
                    text = getString(R.string.menu_refresh_dur),
                    onClick = { dismiss(); if (ReadBook.bookSource == null) upContent() else ReadBook.book?.let { ReadBook.curTextChapter = null; binding.readView.upContent(); viewModel.refreshContentDur(it) } },
                )
                RoundDropdownMenuItem(
                    text = getString(R.string.menu_refresh_after),
                    onClick = { dismiss(); if (ReadBook.bookSource == null) upContent() else ReadBook.book?.let { ReadBook.clearTextChapter(); binding.readView.upContent(); viewModel.refreshContentAfter(it) } },
                )
                RoundDropdownMenuItem(
                    text = getString(R.string.menu_refresh_all),
                    onClick = { dismiss(); if (ReadBook.bookSource == null) upContent() else ReadBook.book?.let { refreshContentAll(it) } },
                )
            }
        }
        binding.readMenu.refreshMenuColorFilter()
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        this.menu = menu
        upMenu()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_same_title_removed)?.isChecked =
            ReadBook.curTextChapter?.sameTitleRemoved == true
        return super.onMenuOpened(featureId, menu)
    }

    /**
     * 更新菜单
     */
    private fun upMenu() {
        val menu = menu ?: return
        val book = ReadBook.book ?: return
        val onLine = !book.isLocal
        for (i in 0 until menu.size) {
            val item = menu[i]
            when (item.groupId) {
                R.id.menu_group_on_line -> item.isVisible = onLine
                R.id.menu_group_local -> item.isVisible = !onLine
                R.id.menu_group_text -> item.isVisible = book.isLocalTxt
                R.id.menu_group_epub -> item.isVisible = book.isEpub
                else -> when (item.itemId) {
                    R.id.menu_enable_replace -> item.isChecked = book.getUseReplaceRule()
                    R.id.menu_re_segment -> item.isChecked = book.getReSegment()
//                    R.id.menu_enable_review -> {
//                        item.isVisible = BuildConfig.DEBUG
//                        item.isChecked = AppConfig.enableReview
//                    }

                    R.id.menu_reverse_content -> item.isVisible = onLine
                    R.id.menu_del_ruby_tag -> item.isChecked = book.getDelTag(Book.rubyTag)
                    R.id.menu_del_h_tag -> item.isChecked = book.getDelTag(Book.hTag)
                    R.id.menu_re_chapter -> {
                        item.isVisible = onLine && !ReadBook.bookSource
                            ?.getContentRule()?.content.isNullOrEmpty()
                        item.isChecked = book.reChapterEnabled
                    }
                }
            }
        }
        lifecycleScope.launch {
            val show = ReadBook.inBookshelf && withContext(IO) {
                AppWebDav.isOk
            }
            menu.findItem(R.id.menu_get_progress)?.isVisible = show
            menu.findItem(R.id.menu_cover_progress)?.isVisible = show
        }
    }

    /**
     * 菜单
     */
    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_change_source,
            R.id.menu_book_change_source -> {
                binding.readMenu.runMenuOut()
                ReadBook.book?.let {
                    showDialogFragment(ChangeBookSourceDialog(it.name, it.author))
                }
            }

            R.id.menu_chapter_change_source -> lifecycleScope.launch {
                val book = ReadBook.book ?: return@launch
                val chapter =
                    appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                        ?: return@launch
                binding.readMenu.runMenuOut()
                showDialogFragment(
                    ChangeChapterSourceDialog(book.name, book.author, chapter.index, chapter.title)
                )
            }

            R.id.menu_refresh,
            R.id.menu_refresh_dur -> {
                if (ReadBook.bookSource == null) {
                    upContent()
                } else {
                    ReadBook.book?.let {
                        ReadBook.curTextChapter = null
                        binding.readView.upContent()
                        viewModel.refreshContentDur(it)
                    }
                }
            }

            R.id.menu_refresh_after -> {
                if (ReadBook.bookSource == null) {
                    upContent()
                } else {
                    ReadBook.book?.let {
                        ReadBook.clearTextChapter()
                        binding.readView.upContent()
                        viewModel.refreshContentAfter(it)
                    }
                }
            }

            R.id.menu_refresh_all -> {
                if (ReadBook.bookSource == null) {
                    upContent()
                } else {
                    ReadBook.book?.let {
                        refreshContentAll(it)
                    }
                }
            }

            R.id.menu_download -> showDownloadDialog()
            R.id.menu_add_bookmark -> addBookmark()
            R.id.menu_simulated_reading -> showSimulatedReading()
            R.id.menu_edit_content -> showDialogFragment(ContentEditDialog())
            R.id.menu_update_toc -> ReadBook.book?.let {
                if (it.isEpub) {
                    BookHelp.clearCache(it)
                    EpubFile.clear()
                }
                if (it.isMobi) {
                    MobiFile.clear()
                }
                loadChapterList(it)
            }

            R.id.menu_re_chapter -> handleReChapter()

            R.id.menu_enable_replace -> changeReplaceRuleState()
            R.id.menu_re_segment -> ReadBook.book?.let {
                it.setReSegment(!it.getReSegment())
                item.isChecked = it.getReSegment()
                ReadBook.loadContent(false)
            }

//            R.id.menu_enable_review -> {
//                AppConfig.enableReview = !AppConfig.enableReview
//                item.isChecked = AppConfig.enableReview
//                ReadBook.loadContent(false)
//            }

            R.id.menu_del_ruby_tag -> ReadBook.book?.let {
                item.isChecked = !item.isChecked
                if (item.isChecked) {
                    it.addDelTag(Book.rubyTag)
                } else {
                    it.removeDelTag(Book.rubyTag)
                }
                refreshContentAll(it)
            }

            R.id.menu_del_h_tag -> ReadBook.book?.let {
                item.isChecked = !item.isChecked
                if (item.isChecked) {
                    it.addDelTag(Book.hTag)
                } else {
                    it.removeDelTag(Book.hTag)
                }
                refreshContentAll(it)
            }

            R.id.menu_page_anim -> showPageAnimConfig {
                binding.readView.upPageAnim()
                ReadBook.loadContent(false)
            }

            R.id.menu_log -> showLogSheet()
            R.id.menu_toc_regex -> {
                // TODO: use TxtTocRuleActivity with startActivityForResult
                val intent = android.content.Intent(this, io.legado.app.ui.book.toc.rule.TxtTocRuleActivity::class.java).apply {
                    putExtra("tocRegex", ReadBook.book?.tocUrl)
                }
                startActivityForResult(intent, 0)
            }

            R.id.menu_reverse_content -> ReadBook.book?.let {
                viewModel.reverseContent(it)
            }

            R.id.menu_set_charset -> showCharsetConfig()
            R.id.menu_image_style -> {
                val imgStyles =
                    arrayListOf(
                        Book.imgStyleDefault, Book.imgStyleFull, Book.imgStyleText,
                        Book.imgStyleSingle
                    )
                selector(
                    R.string.image_style,
                    imgStyles
                ) { _, index ->
                    val imageStyle = imgStyles[index]
                    ReadBook.book?.setImageStyle(imageStyle)
                    if (imageStyle == Book.imgStyleSingle) {
                        ReadBook.book?.setPageAnim(0)  // 切换图片样式single后，自动切换为覆盖
                        binding.readView.upPageAnim()
                    }
                    ReadBook.loadContent(false)
                }
            }

            R.id.menu_get_progress -> ReadBook.book?.let {
                viewModel.syncBookProgress(it) { progress ->
                    sureSyncProgress(progress)
                }
            }

            R.id.menu_cover_progress -> ReadBook.book?.let {
                ReadBook.uploadProgress(true) { toastOnUi(R.string.upload_book_success) }
            }

            R.id.menu_same_title_removed -> {
                ReadBook.book?.let {
                    val contentProcessor = ContentProcessor.get(it)
                    val textChapter = ReadBook.curTextChapter
                    if (textChapter != null
                        && !textChapter.sameTitleRemoved
                        && !contentProcessor.removeSameTitleCache.contains(
                            textChapter.chapter.getFileName("nr")
                        )
                    ) {
                        toastOnUi("未找到可移除的重复标题")
                    }
                }
                viewModel.reverseRemoveSameTitle()
            }

            R.id.menu_effective_replaces -> showDialogFragment<EffectiveReplacesDialog>()

            R.id.menu_help -> showHelp()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun refreshContentAll(book: Book) {
        ReadBook.clearTextChapter()
        binding.readView.upContent()
        viewModel.refreshContentAll(book)
    }

    /**
     * 重新分章入口:未启用时确认开启并触发首批;已启用时选择"重新分章一次"或"关闭"。
     */
    /**
     * 重新分章开关:直接展示操作选择,不再叠加确认对话框(选择即意图)。
     * 未启用时可选"仅附近/全量"并开启;已启用时可选"重新分章一次/全量/关闭"。
     */
    private fun handleReChapter() {
        val book = ReadBook.book ?: return
        val source = ReadBook.bookSource ?: return
        if (!book.reChapterEnabled) {
            book.reChapterEnabled = true
            if (ReadBook.inBookshelf) appDb.bookDao.update(book)
            menu?.findItem(R.id.menu_re_chapter)?.isChecked = true
            selector(
                R.string.re_chapter,
                listOf(
                    getString(R.string.re_chapter_nearby),
                    getString(R.string.re_chapter_full),
                )
            ) { _, which ->
                when (which) {
                    0 -> runReChapter(book, source, showFailure = false) {
                        ReChapterUseCase(book, source).execute(book.durChapterIndex)
                    }
                    1 -> runReChapter(book, source, showFailure = true) {
                        ReChapterUseCase(book, source).fullBookReChapter()
                    }
                }
            }
        } else {
            selector(
                R.string.re_chapter,
                listOf<CharSequence>(
                    getString(R.string.re_chapter_again),
                    getString(R.string.re_chapter_full),
                    getString(R.string.re_chapter_disable),
                )
            ) { _, index ->
                when (index) {
                    0 -> runReChapter(book, source, showFailure = true) {
                        ReChapterUseCase(book, source).resetAndReChapter()
                    }
                    1 -> runReChapter(book, source, showFailure = true) {
                        ReChapterUseCase(book, source).fullBookReChapter()
                    }
                    else -> lifecycleScope.launch {
                        if (ReChapterUseCase.isRunning(book.bookUrl)) {
                            toastOnUi(R.string.re_chapter_running)
                            return@launch
                        }
                        val restored = withContext(IO) {
                            ReChapterUseCase(book, source).disableAndRestore()
                        }
                        menu?.findItem(R.id.menu_re_chapter)?.isChecked = false
                        if (restored) {
                            toastOnUi(R.string.re_chapter_disabled_restored)
                        }
                    }
                }
            }
        }
    }

    /**
     * 执行重新分章批次。本地判定不足时返回 [ExecuteResult.NONE],由调用方提示失败。
     */
    private fun runReChapter(
        book: Book,
        source: BookSource,
        showFailure: Boolean,
        run: suspend () -> ExecuteResult,
    ) {
        lifecycleScope.launch {
            if (ReChapterUseCase.isRunning(book.bookUrl)) {
                toastOnUi(R.string.re_chapter_running)
                return@launch
            }
            val result = withContext(IO) { run() }
            when (result) {
                ExecuteResult.MERGED -> toastOnUi(R.string.re_chapter_done)
                ExecuteResult.NONE ->
                    if (showFailure) toastOnUi(R.string.re_chapter_failed)
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return onCompatOptionsItemSelected(item)
    }

    /**
     * 按键拦截,显示菜单
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action
        val isDown = action == 0

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (isDown && !binding.readMenu.canShowMenu) {
                binding.readMenu.runMenuIn()
                return true
            }
            if (!isDown && !binding.readMenu.canShowMenu) {
                binding.readMenu.canShowMenu = true
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * 鼠标滚轮事件
     */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (0 != (event.source and InputDevice.SOURCE_CLASS_POINTER)) {
            if (event.action == MotionEvent.ACTION_SCROLL) {
                val axisValue = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                LogUtils.d("onGenericMotionEvent", "axisValue = $axisValue")
                // 获得垂直坐标上的滚动方向
                if (axisValue < 0.0f) { // 滚轮向下滚
                    mouseWheelPage(PageDirection.NEXT)
                } else { // 滚轮向上滚
                    mouseWheelPage(PageDirection.PREV)
                }
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    /**
     * 按键事件
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (menuLayoutIsVisible) {
            return super.onKeyDown(keyCode, event)
        }
        val longPress = event.repeatCount > 0
        when {
            isPrevKey(keyCode) -> {
                handleKeyPage(PageDirection.PREV, longPress)
                return true
            }

            isNextKey(keyCode) -> {
                handleKeyPage(PageDirection.NEXT, longPress)
                return true
            }
        }
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> if (volumeKeyPage(PageDirection.PREV, longPress)) {
                return true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> if (volumeKeyPage(PageDirection.NEXT, longPress)) {
                return true
            }

            KeyEvent.KEYCODE_PAGE_UP -> {
                handleKeyPage(PageDirection.PREV, longPress)
                return true
            }

            KeyEvent.KEYCODE_PAGE_DOWN -> {
                handleKeyPage(PageDirection.NEXT, longPress)
                return true
            }

            KeyEvent.KEYCODE_SPACE -> {
                handleKeyPage(PageDirection.NEXT, longPress)
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    /**
     * 松开按键事件
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (volumeKeyPage(PageDirection.NONE, false)) {
                    return true
                }
            }

        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * view触摸,文字选择
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean = binding.run {
        if (!binding.readView.isTextSelected) {
            return false
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> textActionMenuState = null
            MotionEvent.ACTION_MOVE -> {
                when (v.id) {
                    R.id.cursor_left -> if (!readView.curPage.getReverseStartCursor()) {
                        readView.curPage.selectStartMove(
                            event.rawX + cursorLeft.width,
                            event.rawY - cursorLeft.height
                        )
                    } else {
                        readView.curPage.selectEndMove(
                            event.rawX - cursorRight.width,
                            event.rawY - cursorRight.height
                        )
                    }

                    R.id.cursor_right -> if (readView.curPage.getReverseEndCursor()) {
                        readView.curPage.selectStartMove(
                            event.rawX + cursorLeft.width,
                            event.rawY - cursorLeft.height
                        )
                    } else {
                        readView.curPage.selectEndMove(
                            event.rawX - cursorRight.width,
                            event.rawY - cursorRight.height
                        )
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                readView.curPage.resetReverseCursor()
                showTextActionMenu()
            }
        }
        return true
    }

    /**
     * 更新文字选择开始位置
     */
    override fun upSelectedStart(x: Float, y: Float, top: Float) = binding.run {
        cursorLeft.x = x - cursorLeft.width
        cursorLeft.y = y
        cursorLeft.visible(true)
        textMenuPosition.x = x
        textMenuPosition.y = top
    }

    /**
     * 更新文字选择结束位置
     */
    override fun upSelectedEnd(x: Float, y: Float) = binding.run {
        cursorRight.x = x
        cursorRight.y = y
        cursorRight.visible(true)
    }

    /**
     * 取消文字选择
     */
    override fun onCancelSelect() = binding.run {
        cursorLeft.invisible()
        cursorRight.invisible()
        textActionMenuState = null
    }

    override fun onLongScreenshotTouchEvent(event: MotionEvent): Boolean {
        return binding.readView.onTouchEvent(event)
    }

    /**
     * 显示文本操作菜单
     */
    override fun showTextActionMenu() {
        // 重新分章合并章才提供"从此处拆分章节"
        val showSplitChapter = ReadBook.curTextChapter?.chapter?.let {
            BookHelp.isReChaptered(it)
        } == true
        expandTextMenu = AppConfigStore.getBoolean(PreferKey.expandTextMenu) ?: false
        textActionMenuState = TextMenuState(
            selectedText = binding.readView.getSelectText(),
            startX = binding.textMenuPosition.x.toInt(),
            startTopY = binding.textMenuPosition.y.toInt(),
            startBottomY = binding.cursorLeft.y.toInt() + binding.cursorLeft.height,
            endX = binding.cursorRight.x.toInt(),
            endBottomY = binding.cursorRight.y.toInt() + binding.cursorRight.height,
            items = buildTextActionMenuItems(this, showSplitChapter),
        )
    }

    /**
     * 当前选择的文本
     */
    val selectedText: String get() = binding.readView.getSelectText()

    /**
     * 文本选择菜单操作
     */
    fun onMenuItemSelected(itemId: Int): Boolean {
        when (itemId) {
            R.id.menu_aloud -> when (AppConfig.contentSelectSpeakMod) {
                1 -> lifecycleScope.launch {
                    binding.readView.aloudStartSelect()
                }

                else -> speak(binding.readView.getSelectText())
            }

            R.id.menu_bookmark -> binding.readView.curPage.let {
                val bookmark = it.createBookmark()
                if (bookmark == null) {
                    toastOnUi(R.string.create_bookmark_error)
                } else {
                    showDialogFragment(BookmarkDialog(bookmark, showDelete = true))
                }
                return true
            }

            R.id.menu_replace -> {
                val scopes = arrayListOf<String>()
                ReadBook.book?.name?.let {
                    scopes.add(it)
                }
                ReadBook.bookSource?.bookSourceUrl?.let {
                    scopes.add(it)
                }
                val text = selectedText.lineSequence().joinToString("\n") { it.trim() }
                replaceActivity.launch(
                    ReplaceRuleActivity.startIntent(
                        this,
                        ReplaceEditRoute(
                            pattern = text,
                            scope = scopes.joinToString(";")
                        )
                    )
                )
                return true
            }

            R.id.menu_search_content -> {
                viewModel.searchContentQuery = selectedText
                openSearchActivity(selectedText)
                return true
            }

            R.id.menu_dict -> {
                val dictSearchContext = DictSearchContext(
                    bookUrl = ReadBook.book?.bookUrl,
                    bookName = ReadBook.book?.name,
                    chapterIndex = ReadBook.durChapterIndex,
                    chapterTitle = ReadBook.curTextChapter?.title,
                )
                showDialogFragment(createDictSheetDialog(selectedText, dictSearchContext))
                return true
            }

            R.id.menu_split_chapter -> {
                splitChapterHere(selectedText)
                return true
            }
        }
        return false
    }

    /**
     * 在重新分章合并章内,按选中文本所在行拆分章节(修复漏拆)。
     */
    private fun splitChapterHere(selected: String) {
        val book = ReadBook.book ?: return
        val source = ReadBook.bookSource ?: return
        val textChapter = ReadBook.curTextChapter ?: return
        val chapter = textChapter.chapter
        if (!BookHelp.isReChaptered(chapter)) {
            toastOnUi(R.string.split_chapter_failed)
            return
        }
        lifecycleScope.launch {
            if (ReChapterUseCase.isRunning(book.bookUrl)) {
                toastOnUi(R.string.re_chapter_running)
                return@launch
            }
            val result = withContext(IO) {
                ReChapterUseCase(book, source).splitChapter(chapter, selected)
            }
            if (result != null) {
                toastOnUi(R.string.split_chapter_done)
            } else {
                toastOnUi(R.string.split_chapter_failed)
            }
        }
    }

    /**
     * 文本选择菜单操作完成
     */
    private fun onMenuActionFinally() = binding.run {
        textActionMenuState = null
        readView.cancelSelect()
    }

    /**
     * Compose 文字选择菜单点击分发
     */
    private fun onTextActionItemClick(item: ActionMenuItem) {
        val text = textActionMenuState?.selectedText
        val handled = onMenuItemSelected(item.id)
        if (!handled) {
            when (item.id) {
                R.id.menu_copy -> text?.let { sendToClip(it) }
                R.id.menu_share_str -> text?.let { share(it) }
                R.id.menu_browser -> text?.let { openTextInBrowser(it) }
                else -> {
                    val intent = item.intent ?: return
                    kotlin.runCatching {
                        intent.putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                        startActivity(intent)
                    }.onFailure { e ->
                        AppLog.put("执行文本菜单操作出错\n$e", e, true)
                    }
                }
            }
        }
        onMenuActionFinally()
    }

    private fun openTextInBrowser(text: String) {
        kotlin.runCatching {
            val intent = if (text.isAbsUrl()) {
                Intent(Intent.ACTION_VIEW).apply { data = text.toUri() }
            } else {
                Intent(Intent.ACTION_WEB_SEARCH).apply { putExtra(SearchManager.QUERY, text) }
            }
            startActivity(intent)
        }.onFailure {
            it.printOnDebug()
            toastOnUi(it.localizedMessage ?: "ERROR")
        }
    }

    /**
     * 长按文字选择菜单项:切换朗读模式(朗读选中内容 <-> 从选中处连续朗读)
     */
    private fun onTextActionItemLongClick(item: ActionMenuItem) {
        if (AppConfig.contentSelectSpeakMod == 0) {
            AppConfig.contentSelectSpeakMod = 1
            toastOnUi("切换为从选择的地方开始一直朗读")
        } else {
            AppConfig.contentSelectSpeakMod = 0
            toastOnUi("切换为朗读选择内容")
        }
    }

    private fun speak(text: String) {
        if (tts == null) {
            tts = TTS()
        }
        tts?.speak(text)
    }

    /**
     * 鼠标滚轮翻页
     */
    private fun mouseWheelPage(direction: PageDirection) {
        if (menuLayoutIsVisible || !AppConfig.mouseWheelPage) {
            return
        }
        keyPageDebounce(direction, mouseWheel = true, longPress = false)
    }

    /**
     * 音量键翻页
     */
    private fun volumeKeyPage(direction: PageDirection, longPress: Boolean): Boolean {
        if (!AppConfig.volumeKeyPage) {
            return false
        }
        if (!AppConfig.volumeKeyPageOnPlay && BaseReadAloudService.isPlay()) {
            return false
        }
        handleKeyPage(direction, longPress)
        return true
    }

    private fun handleKeyPage(direction: PageDirection, longPress: Boolean) {
        if (AppConfig.keyPageOnLongPress || direction == PageDirection.NONE) {
            keyPage(direction)
        } else {
            keyPageDebounce(direction, longPress = longPress)
        }
    }

    private fun keyPageDebounce(
        direction: PageDirection,
        mouseWheel: Boolean = false,
        longPress: Boolean
    ) {
        if (longPress) {
            return
        }
        nextPageDebounce.apply {
            wait = if (mouseWheel) 200L else 600L
            leading = !mouseWheel
            trailing = mouseWheel
        }
        prevPageDebounce.apply {
            wait = if (mouseWheel) 200L else 600L
            leading = !mouseWheel
            trailing = mouseWheel
        }
        when (direction) {
            PageDirection.NEXT -> nextPageDebounce.invoke()
            PageDirection.PREV -> prevPageDebounce.invoke()
            else -> {}
        }
    }

    private fun keyPage(direction: PageDirection) {
        binding.readView.cancelSelect()
        binding.readView.pageDelegate?.isCancel = false
        binding.readView.pageDelegate?.keyTurnPage(direction)
    }

    override fun upMenuView() {
        handler.post {
            upMenu()
            binding.readMenu.upBookView()
        }
    }

    override fun loadChapterList(book: Book) {
        ReadBook.upMsg(getString(R.string.toc_updateing))
        viewModel.loadChapterList(book)
    }

    /**
     * 内容加载完成
     */
    override fun contentLoadFinish() {
        if (intent.getBooleanExtra("readAloud", false)) {
            intent.removeExtra("readAloud")
            ReadBook.readAloud()
        }
        loadStates = true
        maybeApplyPendingSearchJump()
    }

    /**
     * 更新内容
     */
    override fun upContent(
        relativePosition: Int,
        resetPageOffset: Boolean,
        success: (() -> Unit)?
    ) {
        lifecycleScope.launch {
            binding.readView.upContent(relativePosition, resetPageOffset)
            if (relativePosition == 0) {
                upSeekBarProgress()
                maybeApplyPendingSearchJump()
            }
            loadStates = false
            success?.invoke()
        }
    }

    override suspend fun upContentAwait(
        relativePosition: Int,
        resetPageOffset: Boolean,
        success: (() -> Unit)?
    ) = withContext(Main.immediate) {
        binding.readView.upContent(relativePosition, resetPageOffset)
        if (relativePosition == 0) {
            upSeekBarProgress()
        }
        loadStates = false
    }

    override fun upPageAnim(upRecorder: Boolean) {
        lifecycleScope.launch {
            binding.readView.upPageAnim(upRecorder)
        }
    }

    override fun notifyBookChanged() {
        bookChanged = true
        if (!ReadBook.inBookshelf) {
            viewModel.removeFromBookshelf { super.finish() }
        }
    }

    override fun cancelSelect() {
        runOnUiThread {
            binding.readView.cancelSelect()
        }
    }

    /**
     * 页面改变
     */
    override fun pageChanged() {
        pageChanged = true
        binding.readView.onPageChange()
        handler.post {
            upSeekBarProgress()
        }
        executor.execute {
            startBackupJob()
        }
    }

    /**
     * 更新进度条位置
     */
    private fun upSeekBarProgress() {
        val progress = when (AppConfig.progressBarBehavior) {
            "page" -> ReadBook.durPageIndex
            else /* chapter */ -> ReadBook.durChapterIndex
        }
        binding.readMenu.setSeekPage(progress)
    }

    /**
     * 显示菜单
     */
    override fun showMenuBar() {
        binding.readMenu.runMenuIn()
    }

    override val oldBook: Book?
        get() = ReadBook.book

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        if (!book.isAudio) {
            viewModel.changeTo(book, toc)
        } else {
            ReadAloud.stop(this)
            lifecycleScope.launch {
                withContext(IO) {
                    ReadBook.book?.migrateTo(book, toc)
                    book.removeType(BookType.updateError)
                    ReadBook.book?.delete()
                    appDb.bookDao.insert(book)
                }
                startActivityForBook(book)
                finish()
            }
        }
    }

    override fun replaceContent(content: String) {
        ReadBook.book?.let {
            viewModel.saveContent(it, content)
        }
    }

    override fun showActionMenu() {
        when {
            BaseReadAloudService.isRun -> showReadAloudDialog()
            isAutoPage -> showDialogFragment<AutoReadDialog>()
            isShowingSearchResult -> binding.searchMenu.runMenuIn()
            else -> binding.readMenu.runMenuIn()
        }
    }

    /**
     * 显示朗读菜单
     */
    override fun showReadAloudDialog() {
        if (AppConfig.readAloudDefaultInterface == "player") {
            showReadAloudPlayerOverlay()
        } else {
            showDialogFragment<ReadAloudDialog>()
        }
    }

    override fun showReadAloudPlayer() {
        showReadAloudPlayerOverlay()
    }

    /**
     * 自动翻页
     */
    override fun autoPage() {
        ReadAloud.stop(this)
        if (isAutoPage) {
            autoPageStop()
        } else {
            binding.readView.autoPager.start()
            binding.readMenu.setAutoPage(true)
            screenTimeOut = -1L
            screenOffTimerStart()
        }
    }

    override fun autoPageStop() {
        if (isAutoPage) {
            binding.readView.autoPager.stop()
            binding.readMenu.setAutoPage(false)
            dismissDialogFragment<AutoReadDialog>()
            upScreenTimeOut()
        }
    }

    override fun openSourceEditActivity() {
        ReadBook.bookSource?.let {
            sourceEditActivity.launch {
                putExtra("sourceUrl", it.bookSourceUrl)
            }
        }
    }

    override fun openBookInfoActivity() {
        ReadBook.book?.let {
            bookInfoActivity.launch {
                putExtra("name", it.name)
                putExtra("author", it.author)
            }
        }
    }

    /**
     * 替换
     */
    override fun openReplaceRule() {
        replaceActivity.launch(Intent(this, ReplaceRuleActivity::class.java))
    }

    /**
     * 打开目录
     */
    override fun openChapterList() {
        ReadBook.book?.let {
            tocActivity.launch(it.bookUrl)
        }
    }

    /**
     * 打开搜索界面
     */
    override fun openSearchActivity(searchWord: String?) {
        val book = ReadBook.book ?: return
        searchContentActivity.launch {
            putExtra("bookUrl", book.bookUrl)
            putExtra("searchWord", searchWord ?: viewModel.searchContentQuery)
            putExtra("searchResultIndex", viewModel.searchResultIndex)
            viewModel.searchResultList?.first()?.let {
                if (it.query == viewModel.searchContentQuery) {
                    IntentData.put("searchResultList", viewModel.searchResultList)
                }
            }
        }
    }

    /**
     * 禁用书源
     */
    override fun disableSource() {
        viewModel.disableSource()
    }

    /**
     * 显示阅读样式配置
     */
    override fun showReadStyle() {
        bottomDialog++
        showReadStyleSheet = true
        syncComposeOverlayChrome()
    }

    /**
     * 显示更多设置
     */
    override fun showMoreSetting() {
        bottomDialog++
        showMoreConfigSheet = true
        syncComposeOverlayChrome()
    }

    override fun showSearchSetting() {
        bottomDialog++
        showMoreConfigSheet = true
        syncComposeOverlayChrome()
    }

    /**
     * 更新状态栏,导航栏
     */
    override fun upSystemUiVisibility() {
        upSystemUiVisibility(isInMultiWindow, !menuLayoutIsVisible, bottomDialog > 0)
        upNavigationBarColor()
    }

    // 退出全文搜索
    override fun exitSearchMenu() {
        if (isShowingSearchResult) {
            isShowingSearchResult = false
            binding.searchMenu.invalidate()
            binding.searchMenu.invisible()
            ReadBook.clearSearchResult()
            binding.readView.cancelSelect(true)
        }
    }

    /* 恢复到 全文搜索/进度条跳转前的位置 */
    private fun restoreLastBookProcess() {
        if (confirmRestoreProcess == true) {
            ReadBook.restoreLastBookProgress()
        } else if (confirmRestoreProcess == null) {
            alert(R.string.draw) {
                setMessage(R.string.restore_last_book_process)
                yesButton {
                    confirmRestoreProcess = true
                    ReadBook.restoreLastBookProgress() //恢复启动全文搜索前的进度
                }
                noButton {
                    ReadBook.lastBookProgress = null
                    confirmRestoreProcess = false
                }
                onCancelled {
                    ReadBook.lastBookProgress = null
                    confirmRestoreProcess = false
                }
            }
        }
    }

    override fun showLogin() {
        ReadBook.bookSource?.let {
            startActivity<SourceLoginActivity> {
                putExtra("type", "bookSource")
                putExtra("key", it.bookSourceUrl)
            }
        }
    }

    override fun payAction() {
        val book = ReadBook.book ?: return
        if (book.isLocal) return
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
        if (chapter == null) {
            toastOnUi("no chapter")
            return
        }
        alert(R.string.chapter_pay) {
            setMessage(chapter.title)
            yesButton {
                Coroutine.async(lifecycleScope) {
                    val source =
                        ReadBook.bookSource ?: throw NoStackTraceException("no book source")
                    val payAction = source.getContentRule().payAction
                    if (payAction.isNullOrBlank()) {
                        throw NoStackTraceException("no pay action")
                    }
                    val analyzeRule = AnalyzeRule(book, source)
                    analyzeRule.setCoroutineContext(coroutineContext)
                    analyzeRule.setBaseUrl(chapter.url)
                    analyzeRule.setChapter(chapter)
                    analyzeRule.evalJS(payAction).toString()
                }.onSuccess(IO) {
                    if (it.isAbsUrl()) {
                        startActivity<WebViewActivity> {
                            val bookSource = ReadBook.bookSource
                            putExtra("title", getString(R.string.chapter_pay))
                            putExtra("url", it)
                            putExtra("sourceOrigin", bookSource?.bookSourceUrl)
                            putExtra("sourceName", bookSource?.bookSourceName)
                            putExtra("sourceType", bookSource?.getSourceType())
                        }
                    } else if (it.isTrue()) {
                        //购买成功后刷新目录
                        ReadBook.book?.let {
                            ReadBook.curTextChapter = null
                            BookHelp.delContent(book, chapter)
                            loadChapterList(book)
                        }
                    }
                }.onError {
                    AppLog.put("执行购买操作出错\n${it.localizedMessage}", it, true)
                }
            }
            noButton()
        }
    }

    /**
     * 朗读按钮
     */
    override fun onClickReadAloud() {
        autoPageStop()
        when {
            !BaseReadAloudService.isRun -> {
                ReadAloud.upReadAloudClass()
                val scrollPageAnim = ReadBook.pageAnim() == 3
                if (scrollPageAnim) {
                    val pos = binding.readView.getReadAloudPos()
                    if (pos != null) {
                        val (index, line) = pos
                        if (ReadBook.durChapterIndex != index) {
                            ReadBook.openChapter(index, line.chapterPosition, false) {
                                ReadBook.readAloud(startPos = line.pagePosition)
                            }
                        } else {
                            ReadBook.durChapterPos = line.chapterPosition
                            ReadBook.readAloud(startPos = line.pagePosition)
                        }
                    } else {
                        ReadBook.readAloud()
                    }
                } else {
                    ReadBook.readAloud()
                }
            }

            BaseReadAloudService.pause -> {
                val scrollPageAnim = ReadBook.pageAnim() == 3
                if (scrollPageAnim && pageChanged) {
                    pageChanged = false
                    val pos = binding.readView.getReadAloudPos()
                    if (pos != null) {
                        val (index, line) = pos
                        if (ReadBook.durChapterIndex != index) {
                            ReadBook.openChapter(index, line.chapterPosition, false) {
                                ReadBook.readAloud(startPos = line.pagePosition)
                            }
                        } else {
                            ReadBook.durChapterPos = line.chapterPosition
                            ReadBook.readAloud(startPos = line.pagePosition)
                        }
                    } else {
                        ReadBook.readAloud()
                    }
                } else {
                    ReadAloud.resume(this)
                }
            }

            else -> ReadAloud.pause(this)
        }
    }

    override fun showHelp() {
        showHelp("readMenuHelp")
    }

    /**
     * 长按图片
     */
    @SuppressLint("RtlHardcoded")
    override fun onImageLongPress(x: Float, y: Float, src: String) {
        popupAction.setItems(
            listOf(
                SelectItem(getString(R.string.show), "show"),
                SelectItem(getString(R.string.refresh), "refresh"),
                SelectItem(getString(R.string.action_save), "save"),
                SelectItem(getString(R.string.menu), "menu"),
                SelectItem(getString(R.string.select_folder), "selectFolder")
            )
        )
        popupAction.onActionClick = {
            when (it) {
                "show" -> showDialogFragment(PhotoDialog(src))
                "refresh" -> viewModel.refreshImage(src)
                "save" -> {
                    val path = ACache.get().getAsString(AppConst.imagePathKey)
                    if (path.isNullOrEmpty()) {
                        selectImageDir.launch {
                            value = src
                        }
                    } else {
                        viewModel.saveImage(src, path.toUri())
                    }
                }

                "menu" -> showActionMenu()
                "selectFolder" -> selectImageDir.launch()
            }
            popupAction.dismiss()
        }
        val navigationBarHeight =
            if (!ReadBookConfig.hideNavigationBar && navigationBarGravity == Gravity.BOTTOM)
                binding.navigationBar.height else 0
        popupAction.showAtLocation(
            binding.readView, Gravity.BOTTOM or Gravity.LEFT, x.toInt(),
            binding.root.height + navigationBarHeight - y.toInt()
        )
    }

    /**
     * colorSelectDialog
     */
    override fun onColorSelected(dialogId: Int, color: Int) = ReadBookConfig.durConfig.run {
        when (dialogId) {
            TEXT_COLOR -> {
                setCurTextColor(color)
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6, 9, 11))
                if (AppConfig.readBarStyleFollowPage) {
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }
            }

            BG_COLOR -> {
                setCurBg(0, "#${color.hexString}")
                postEvent(EventBus.UP_CONFIG, arrayListOf(1))
                if (AppConfig.readBarStyleFollowPage) {
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }
            }

            TIP_COLOR -> {
                ReadTipConfig.tipColor = color
                postEvent(EventBus.TIP_COLOR, "")
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }

            TIP_DIVIDER_COLOR -> {
                ReadTipConfig.tipDividerColor = color
                postEvent(EventBus.TIP_COLOR, "")
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }
        }
    }

    /**
     * colorSelectDialog
     */
    override fun onDialogDismissed(dialogId: Int) = Unit

    // TODO: rewire through onActivityResult from TxtTocRuleActivity
    fun onTocRegexDialogResult(tocRegex: String) {
        ReadBook.book?.let {
            it.tocUrl = tocRegex
            loadChapterList(it)
        }
    }

    private fun sureSyncProgress(progress: BookProgress) {
        alert(R.string.get_book_progress) {
            setMessage(R.string.current_progress_exceeds_cloud)
            okButton {
                ReadBook.setProgress(progress)
            }
            noButton()
        }
    }

    /* 进度条跳转到指定章节 */
    override fun skipToChapter(index: Int) {
        ReadBook.saveCurrentBookProgress() //退出章节跳转恢复此时进度
        viewModel.openChapter(index)
    }

    /* 全文搜索跳转 */
    override fun navigateToSearch(searchResult: SearchResult, index: Int) {
        viewModel.searchResultIndex = index
        skipToSearch(searchResult)
    }

    /** 阅读样式分组刷新（分组语义同 UP_CONFIG），旧事件与新 Flow 两个来源共用 */
    private fun upConfigGroups(groups: List<Int>) {
        groups.forEach { value ->
            when (value) {
                0 -> upSystemUiVisibility()
                1 -> binding.readView.upBg()
                2 -> binding.readView.upStyle()
                3 -> binding.readView.upBgAlpha()
                4 -> binding.readView.upPageSlopSquare()
                5 -> if (isInitFinish) ReadBook.loadContent(resetPageOffset = false)
                6 -> binding.readView.upContent(resetPageOffset = false)
                8 -> ChapterProvider.upStyle()
                9 -> binding.readView.invalidateTextPage()
                10 -> ChapterProvider.upLayout()
                11 -> binding.readView.submitRenderTask()
                12 -> upPageAnim()
            }
        }
    }

    override fun onMenuShow() {
        binding.readView.autoPager.pause()
    }

    override fun onMenuHide() {
        binding.readView.autoPager.resume()
    }

    override fun onLayoutPageCompleted(index: Int, page: TextPage) {
        upSeekBarThrottle.invoke()
        binding.readView.onLayoutPageCompleted(index, page)
    }

    /* 全文搜索跳转 */
    private fun skipToSearch(searchResult: SearchResult) {
        if (searchResult.chapterIndex != ReadBook.durChapterIndex) {
            viewModel.openChapter(searchResult.chapterIndex) {
                jumpToPosition(searchResult)
            }
        } else {
            jumpToPosition(searchResult)
        }
    }

    private fun capturePendingSearchJump(intent: Intent?) {
        intent ?: return
        val key = intent.getLongExtra("searchJumpKey", 0L)
        if (key <= 0L) return
        pendingSearchJumpKey = key
        pendingSearchJumpIndex = intent.getIntExtra("searchResultIndex", 0)
        intent.removeExtra("searchJumpKey")
    }

    private fun maybeApplyPendingSearchJump() {
        val key = pendingSearchJumpKey ?: return
        if (ReadBook.curTextChapter == null) return
        pendingSearchJumpKey = null
        applySearchJumpFromIntentData(key, pendingSearchJumpIndex)
    }

    private fun applySearchJumpFromIntentData(key: Long, index: Int) {
        val searchResult = IntentData.get<SearchResult>("searchResult$key") ?: return
        val searchResultList = IntentData.get<List<SearchResult>>("searchResultList$key") ?: return
        viewModel.searchContentQuery = searchResult.query
        binding.searchMenu.upSearchResultList(searchResultList)
        isShowingSearchResult = true
        viewModel.searchResultIndex = index
        binding.searchMenu.updateSearchResultIndex(index)
        binding.searchMenu.selectedSearchResult?.let { currentResult ->
            ReadBook.saveCurrentBookProgress() //退出全文搜索恢复此时进度
            skipToSearch(currentResult)
            showActionMenu()
        }
    }

    private fun jumpToPosition(searchResult: SearchResult) {
        val curTextChapter = ReadBook.curTextChapter ?: return
        binding.searchMenu.updateSearchInfo()
        val (pageIndex, lineIndex, charIndex, addLine, charIndex2) =
            viewModel.searchResultPositions(curTextChapter, searchResult)
        ReadBook.skipToPage(pageIndex) {
            isSelectingSearchResult = true
            binding.readView.curPage.selectStartMoveIndex(0, lineIndex, charIndex)
            when (addLine) {
                0 -> binding.readView.curPage.selectEndMoveIndex(
                    0,
                    lineIndex,
                    charIndex + viewModel.searchContentQuery.length - 1
                )

                1 -> binding.readView.curPage.selectEndMoveIndex(
                    0, lineIndex + 1, charIndex2
                )
                //consider change page, jump to scroll position
                -1 -> binding.readView.curPage.selectEndMoveIndex(1, 0, charIndex2)
            }
            binding.readView.isTextSelected = true
            isSelectingSearchResult = false
        }
    }

    override fun addBookmark() {
        val book = ReadBook.book
        val page = ReadBook.curTextChapter?.getPage(ReadBook.durPageIndex)
        if (book != null && page != null) {
            val bookmark = book.createBookMark().apply {
                chapterIndex = ReadBook.durChapterIndex
                chapterPos = ReadBook.durChapterPos
                chapterName = page.title
                bookText = page.text.trim()
            }
            showDialogFragment(BookmarkDialog(bookmark, showDelete = true))
        }
    }

    override fun changeReplaceRuleState() {
        ReadBook.book?.let {
            it.setUseReplaceRule(!it.getUseReplaceRule())
            ReadBook.saveRead()
            menu?.findItem(R.id.menu_enable_replace)?.isChecked = it.getUseReplaceRule()
            viewModel.replaceRuleChanged()
        }
    }

    private fun startBackupJob() {
        backupJob?.cancel()
        backupJob = lifecycleScope.launch(IO) {
            delay(300000)
            ReadBook.book?.let {
                AppWebDav.uploadBookProgress(it)
                ensureActive()
                it.update()
                Backup.autoBack(this@ReadBookActivity)
            }
        }
    }

    override fun sureNewProgress(progress: BookProgress) {
        syncDialog?.dismiss()
        syncDialog = alert(R.string.get_book_progress) {
            setMessage(R.string.cloud_progress_exceeds_current)
            okButton {
                ReadBook.setProgress(progress)
            }
            noButton()
        }
    }

    override fun finish() {
        val book = ReadBook.book ?: return super.finish()

        if (ReadBook.inBookshelf) {
            return super.finish()
        }

        if (!AppConfig.showAddToShelfAlert) {
            viewModel.removeFromBookshelf { super.finish() }
        } else {
            alert(title = getString(R.string.add_to_bookshelf)) {
                setMessage(getString(R.string.check_add_bookshelf, book.name))
                okButton {
                    ReadBook.book?.removeType(BookType.notShelf)
                    ReadBook.book?.save()
                    ReadBook.inBookshelf = true
                    setResult(RESULT_OK)
                }
                noButton { viewModel.removeFromBookshelf { super.finish() } }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.clearTts()
        textActionMenuState = null
        popupAction.dismiss()
        binding.readView.onDestroy()
        ReadBook.upReadTime()
        ReadBook.unregister(this)
        if (!ReadBook.inBookshelf && !isChangingConfigurations) {
            viewModel.removeFromBookshelf(null)
        }
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
    }

    override fun observeLiveBus() = binding.run {
        observeEvent<String>(EventBus.TIME_CHANGED) { readView.upTime() }
        observeEvent<Int>(EventBus.BATTERY_CHANGED) { readView.upBattery(it) }
        observeEvent<Boolean>(EventBus.MEDIA_BUTTON) {
            if (it) {
                onClickReadAloud()
            } else {
                ReadBook.readAloud(!BaseReadAloudService.pause)
            }
        }
        observeEvent<ArrayList<Int>>(EventBus.UP_CONFIG) {
            upConfigGroups(it)
        }
        // Phase 3：新样式刷新流与旧事件并存，消费方迁完后删 UP_CONFIG
        lifecycleScope.launch {
            ReadStyleRefreshBus.refreshFlow.collect { upConfigGroups(it) }
        }
        observeEvent<Int>(EventBus.ALOUD_STATE) {
            readAloudRunning = BaseReadAloudService.isRun
            readAloudPaused = BaseReadAloudService.pause || it == Status.PAUSE || it == Status.STOP
            if (it == Status.STOP) {
                readAloudRunning = false
                if (showReadAloudPlayer) {
                    hideReadAloudPlayerOverlay()
                } else {
                    syncComposeOverlayChrome()
                }
            } else {
                syncComposeOverlayChrome()
            }
            if (it == Status.STOP || it == Status.PAUSE) {
                ReadBook.curTextChapter?.let { textChapter ->
                    val page = textChapter.getPageByReadPos(ReadBook.durChapterPos)
                    if (page != null) {
                        page.removePageAloudSpan()
                        readView.upContent(resetPageOffset = false)
                    }
                }
            }
        }
        observeEventSticky<Int>(EventBus.TTS_PROGRESS) { chapterStart ->
            lifecycleScope.launch(IO) {
                if (BaseReadAloudService.isPlay()) {
                    ReadBook.curTextChapter?.let { textChapter ->
                        ReadBook.durChapterPos = chapterStart
                        val pageIndex = ReadBook.durPageIndex
                        val aloudSpanStart = chapterStart - textChapter.getReadLength(pageIndex)
                        textChapter.getPage(pageIndex)
                            ?.upPageAloudSpan(aloudSpanStart)
                        upContent()
                    }
                }
            }
        }
        observeEvent<Boolean>(PreferKey.keepLight) {
            upScreenTimeOut()
        }
        observeEvent<Boolean>(PreferKey.textSelectAble) {
            readView.curPage.upSelectAble(it)
        }
        observeEvent<String>(PreferKey.showBrightnessView) {
            readMenu.upBrightnessState()
        }
        observeEvent<List<SearchResult>>(EventBus.SEARCH_RESULT) {
            viewModel.searchResultList = it
        }
        observeEvent<Boolean>(EventBus.UPDATE_READ_ACTION_BAR) {
            readMenu.reset()
        }
        observeEvent<Boolean>(EventBus.UP_SEEK_BAR) {
            readMenu.upSeekBar()
        }
    }

    private fun upScreenTimeOut() {
        val keepLightPrefer =
            AppConfigStore.getString(PreferKey.keepLight)?.toIntOrNull() ?: 0
        screenTimeOut = keepLightPrefer * 1000L
        screenOffTimerStart()
    }

    /**
     * 重置黑屏时间
     */
    override fun screenOffTimerStart() {
        handler.post {
            if (screenTimeOut < 0) {
                keepScreenOn(true)
                return@post
            }
            val t = screenTimeOut - sysScreenOffTime
            if (t > 0) {
                keepScreenOn(true)
                handler.removeCallbacks(screenOffRunnable)
                handler.postDelayed(screenOffRunnable, screenTimeOut)
            } else {
                keepScreenOn(false)
            }
        }
    }

    companion object {
        const val RESULT_DELETED = 100

        /** 阅读时长定时结算周期：兜底落库，任何漏掉的事件误差上限即此值 */
        private const val READ_TIME_TICK_MS = 300_000L
    }

}
