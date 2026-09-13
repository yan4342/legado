package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.net.toUri
import androidx.core.view.get
import androidx.core.view.size
import androidx.lifecycle.lifecycleScope
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
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
import io.legado.app.lib.prefs.ColorPreference
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.model.CacheBook
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
import io.legado.app.ui.book.bookmark.BookmarkDialog
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.changesource.ChangeChapterSourceDialog
import io.legado.app.ui.book.read.config.AutoReadDialog
import io.legado.app.ui.book.read.config.ClickActionConfigDialog
import io.legado.app.ui.book.read.config.PageKeyDialog
import io.legado.app.ui.book.read.config.ReadAloudConfigDialog
import io.legado.app.ui.book.read.config.ReadAloudDialog
import io.legado.app.ui.book.read.config.ReadConfigIds.BG_COLOR
import io.legado.app.ui.book.read.config.ReadConfigIds.TEXT_COLOR
import io.legado.app.ui.book.read.config.ReadConfigIds.TIP_COLOR
import io.legado.app.ui.book.read.config.ReadConfigIds.TIP_DIVIDER_COLOR
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.LayoutProgressListener
import io.legado.app.ui.browser.WebViewActivity
import io.legado.app.ui.dict.DictSearchContext
import io.legado.app.ui.dict.createDictSheetDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.font.FontSelectDialog
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.main.MainRouteReadBook
import io.legado.app.ui.replace.ReplaceEditRoute
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.ui.book.searchContent.SearchResult
import io.legado.app.ui.widget.PopupAction
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.ACache
import io.legado.app.utils.Debounce
import io.legado.app.utils.FileDoc
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.buildMainHandler
import io.legado.app.utils.dismissDialogFragment
import io.legado.app.utils.find
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.hexString
import io.legado.app.utils.invisible
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isTv
import io.legado.app.utils.isTrue
import io.legado.app.utils.launch
import io.legado.app.utils.navigationBarGravity
import io.legado.app.utils.openUrl
import io.legado.app.utils.observeEvent
import io.legado.app.utils.observeEventSticky
import io.legado.app.utils.postEvent
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLightStatusBar
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.setNavigationBarColorAuto
import io.legado.app.utils.share
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showLogSheet
import io.legado.app.utils.showHelp
import io.legado.app.utils.showM3EditDialog
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
import splitties.systemservices.keyguardManager
import splitties.systemservices.powerManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 阶段 4 阅读页路由化：阅读页控制器。
 * 承接原 ReadBookActivity/BaseReadBookActivity 的全部界面编排逻辑（逐字搬迁），
 * 渲染层（ReadView/PageView/ContentTextView/ReadMenu/SearchMenu）经 [refs] 以引用操作，
 * Activity 专属能力（窗口/系统栏/FragmentManager/ActivityResult）经 [activity]/[launchers] 桥接。
 * 生命周期语义由 ReadBookRouteScreen 的组合副作用驱动：onCreatedSetup/onResumed/onPaused/onDestroyCleanup。
 */
@Suppress("DEPRECATION")
class ReadBookController(
    val activity: AppCompatActivity,
    val viewModel: ReadBookViewModel,
    private val route: MainRouteReadBook,
    val routeState: ReadBookRouteState,
    val launchers: ReadBookLaunchers,
    private val onExitReader: () -> Unit,
) : View.OnTouchListener,
    ReadView.CallBack,
    ContentTextView.CallBack,
    SearchMenu.CallBack,
    ReadBook.CallBack,
    ReadAloudDialog.CallBack,
    ChangeBookSourceDialog.CallBack,
    ChangeChapterSourceDialog.CallBack,
    AutoReadDialog.CallBack,
    FontSelectDialog.CallBack,
    ColorPickerDialogListener,
    LayoutProgressListener {

    lateinit var refs: ReadBookViewRefs

    /** 路由导航（Screen 注入；阅读器内跳详情/换源走主栈路由） */
    var onNavigateToRoute: ((io.legado.app.ui.main.MainRoute) -> Unit)? = null

    // ── 阅读菜单状态（M3：Compose 菜单面板驱动） ──
    var menuVisible by mutableStateOf(false)
    var menuUiState by mutableStateOf(ReadMenuUiState())

    val brightnessViewEnabled: Boolean
        get() = AppConfigStore.getBoolean(PreferKey.showBrightnessView) ?: true

    fun brightnessAuto(): Boolean =
        activity.getPrefBoolean("brightnessAuto", true) || !brightnessViewEnabled

    /** 原 ReadMenu.upBookView/upSeekBar/upBrightnessState 的状态化合并 */
    fun updateMenuState() {
        val chapter = ReadBook.curTextChapter
        menuUiState = menuUiState.copy(
            bookName = ReadBook.book?.name,
            chapterName = chapter?.title,
            chapterUrl = if (!ReadBook.isLocalBook) chapter?.chapter?.getAbsoluteURL() else null,
            isLocalBook = ReadBook.isLocalBook,
            preEnabled = ReadBook.durChapterIndex != 0,
            nextEnabled = ReadBook.durChapterIndex != ReadBook.simulatedChapterSize - 1,
            seekMax = when (AppConfig.progressBarBehavior) {
                "page" -> (chapter?.pageSize ?: 1) - 1
                else -> ReadBook.simulatedChapterSize - 1
            },
            seekProgress = when (AppConfig.progressBarBehavior) {
                "page" -> ReadBook.durPageIndex
                else -> ReadBook.durChapterIndex
            },
            brightnessAuto = brightnessAuto(),
        )
    }

    fun showMenuBarPanel() {
        if (menuVisible) return
        menuVisible = true
        onMenuShow()
        if (!io.legado.app.help.config.LocalConfig.readMenuHelpVersionIsLast) {
            showHelp()
        }
    }

    fun hideMenu(onHidden: (() -> Unit)? = null) {
        if (!menuVisible) return
        menuVisible = false
        onMenuHide()
        onHidden?.invoke()
    }

    /** 亮度设置（原 ReadMenu.setScreenBrightness） */
    fun setScreenBrightness(value: Float) {
        var brightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (!brightnessAuto() && value != android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
            brightness = value
            if (brightness < 1f) brightness = 1f
            brightness /= 255f
        }
        val params = activity.window.attributes
        params.screenBrightness = brightness
        activity.window.attributes = params
    }

    /** 章节名/URL 点击（原 chapterViewClickListener） */
    fun onChapterViewClick() {
        if (ReadBook.isLocalBook) return
        val url = menuUiState.chapterUrl ?: return
        val name = menuUiState.chapterName.orEmpty()
        if (AppConfig.readUrlInBrowser) {
            activity.openUrl(url.substringBefore(",{"))
        } else {
            Coroutine.async {
                activity.startActivity<WebViewActivity> {
                    val bookSource = ReadBook.bookSource
                    putExtra("title", name)
                    putExtra("url", url)
                    putExtra("sourceOrigin", bookSource?.bookSourceUrl)
                    putExtra("sourceName", bookSource?.bookSourceName)
                    putExtra("sourceType", bookSource?.getSourceType())
                }
            }
        }
    }

    // ── 顶栏溢出菜单动作（原 options 菜单 book_read 全量恢复） ──

    fun refreshContentAfter() {
        if (ReadBook.bookSource == null) {
            upContent()
        } else {
            ReadBook.book?.let {
                ReadBook.clearTextChapter()
                refs.readView.upContent()
                viewModel.refreshContentAfter(it)
            }
        }
    }

    fun refreshContentAll() {
        ReadBook.book?.let { refreshContentAll(it) }
    }

    /** 获取云端进度（menu_get_progress，WebDav 可用时可见） */
    fun getBookProgress() {
        ReadBook.book?.let {
            viewModel.syncBookProgress(it) { progress -> sureSyncProgress(progress) }
        }
    }

    /** 覆盖云端进度（menu_cover_progress） */
    fun coverBookProgress() {
        ReadBook.book?.let {
            ReadBook.uploadProgress(true) { toastOnUi(R.string.upload_book_success) }
        }
    }

    /** 内容反转（menu_reverse_content，在线书） */
    fun reverseContent() {
        ReadBook.book?.let { viewModel.reverseContent(it) }
    }

    /** 去重标题（menu_same_title_removed：先提示无可删项，再反转） */
    fun reverseRemoveSameTitle() {
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

    /** 重新分段开关（menu_re_segment） */
    fun toggleReSegment() {
        ReadBook.book?.let {
            it.setReSegment(!it.getReSegment())
            ReadBook.loadContent(false)
        }
    }

    /** EPUB 删注/去H 标签切换（menu_del_ruby_tag / menu_del_h_tag） */
    fun toggleDelTag(tag: Long) {
        ReadBook.book?.let {
            if (it.getDelTag(tag)) {
                it.removeDelTag(tag)
            } else {
                it.addDelTag(tag)
            }
            refreshContentAll(it)
        }
    }

    /** 图片样式选择（menu_image_style） */
    fun showImageStyleSelector() {
        val imgStyles = arrayListOf(
            Book.imgStyleDefault, Book.imgStyleFull, Book.imgStyleText,
            Book.imgStyleSingle
        )
        activity.selector(R.string.image_style, imgStyles) { _, index ->
            val imageStyle = imgStyles[index]
            ReadBook.book?.setImageStyle(imageStyle)
            if (imageStyle == Book.imgStyleSingle) {
                ReadBook.book?.setPageAnim(0)  // 切换图片样式single后，自动切换为覆盖
                upPageAnim()
            }
            ReadBook.loadContent(false)
        }
    }

    /** 编辑内容（menu_edit_content） */
    fun showEditContent() {
        activity.showDialogFragment(ContentEditDialog())
    }

    /** 更新目录（menu_update_toc：清 EPUB/MOBI 缓存后重建） */
    fun updateToc() {
        ReadBook.book?.let {
            if (it.isEpub) {
                BookHelp.clearCache(it)
                EpubFile.clear()
            }
            if (it.isMobi) {
                MobiFile.clear()
            }
            loadChapterList(it)
        }
    }

    /** 替换净化一览（menu_effective_replaces） */
    fun showEffectiveReplaces() {
        activity.showDialogFragment(EffectiveReplacesDialog())
    }

    fun showLog() {
        activity.showLogSheet()
    }

    /** 章节换源（原 title_bar 换源图标长按菜单项） */
    fun showChapterChangeSource() {
        activity.lifecycleScope.launch {
            val book = ReadBook.book ?: return@launch
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex) ?: return@launch
            hideMenu()
            activity.showDialogFragment(
                ChangeChapterSourceDialog(book.name, book.author, chapter.index, chapter.title)
            )
        }
    }

    /** 换源（原 options 菜单 menu_book_change_source，漫画菜单同款入口） */
    fun showBookChangeSource() {
        ReadBook.book?.let {
            activity.showDialogFragment(ChangeBookSourceDialog(it.name, it.author))
        }
    }

    /** 刷新当前章（原 options 菜单 menu_refresh_dur） */
    fun refreshCurrentChapter() {
        if (ReadBook.bookSource == null) {
            upContent()
        } else {
            ReadBook.book?.let {
                ReadBook.curTextChapter = null
                refs.readView.upContent()
                viewModel.refreshContentDur(it)
            }
        }
    }

    /** 进度条松手（原 seekReadPage.onStopTrackingTouch） */
    fun onSeekReleased(progress: Int) {
        when (AppConfig.progressBarBehavior) {
            "page" -> ReadBook.skipToPage(progress)
            "chapter" -> {
                if (confirmSkipToChapter) {
                    skipToChapter(progress)
                } else {
                    activity.alert("章节跳转确认", "确定要跳转章节吗？") {
                        yesButton {
                            confirmSkipToChapter = true
                            skipToChapter(progress)
                        }
                        noButton { updateMenuState() }
                        onCancelled { updateMenuState() }
                    }
                }
            }
        }
    }

    /** 弹层系统栏联动计数（原 BaseReadBookActivity.bottomDialog） */
    var bottomDialog = 0
        set(value) {
            if (field != value) {
                field = value
                when (value) {
                    0 -> onMenuHide()
                    1 -> onMenuShow()
                }
            }
        }

    private var confirmSkipToChapter: Boolean = false

    val menuLayoutIsVisible
        get() = bottomDialog > 0 || menuVisible || refs.searchMenu.bottomMenuVisible

    /** 路由参数适配：ReadBookViewModel 的 initData/initReadBookConfig 仍按 Intent 读取，零改动复用 */
    val routeIntent: Intent = Intent().apply {
        putExtra("bookUrl", route.bookUrl)
        putExtra("inBookshelf", route.inBookshelf)
        putExtra("chapterChanged", route.chapterChanged)
    }

    // ── 状态字段（原 ReadBookActivity :198-338 逐字搬迁） ──
    private var menu: Menu? = null
    private var backupJob: Job? = null
    private var tts: TTS? = null
    private var isResumedState = false
    private var screenTimeOut: Long = 0
    private var loadStates: Boolean = false
    private var bookChanged = false
    private var pageChanged = false
    private var justInitData: Boolean = false
    private var syncDialog: androidx.appcompat.app.AlertDialog? = null

    // 恢复跳转前进度对话框的交互结果
    private var confirmRestoreProcess: Boolean? = null

    /** AI / standalone SearchContent → ReadBook search jump pending until content is ready. */
    private var pendingSearchJumpKey: Long? = null
    private var pendingSearchJumpIndex: Int = 0

    val isAutoPage get() = refs.readView.isAutoPage
    private val timeBatteryReceiver = TimeBatteryReceiver()

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

    private val nextPageDebounce by lazy { Debounce { keyPage(PageDirection.NEXT) } }
    private val prevPageDebounce by lazy { Debounce { keyPage(PageDirection.PREV) } }
    private val handler by lazy { buildMainHandler() }
    private val screenOffRunnable by lazy { Runnable { keepScreenOn(false) } }
    private val executor = ReadBook.executor
    private val upSeekBarThrottle = throttle(200) {
        activity.runOnUiThread {
            upSeekBarProgress()
            updateMenuState()
        }
    }
    private val popupAction: PopupAction by lazy { PopupAction(activity) }
    private val networkChangedListener by lazy { NetworkChangedListener(activity) }

    // Compose sheet 状态（原 composeSheetsView overlay 状态平移，Screen 直接读取）
    var showReadStyleSheet by mutableStateOf(false)
    var showMoreConfigSheet by mutableStateOf(false)
    var showReadAloudPlayer by mutableStateOf(false)
    var showVoiceCastingOverlay by mutableStateOf(false)
    var showCloudTtsOverlay by mutableStateOf(false)
    var readAloudRunning by mutableStateOf(false)
    var readAloudPaused by mutableStateOf(true)
    var textActionMenuState by mutableStateOf<TextMenuState?>(null)
    var expandTextMenu by mutableStateOf(false)
    var showTextMenuConfigSheet by mutableStateOf(false)
    var textMenuConfigItems by mutableStateOf(emptyList<ActionMenuItem>())

    // ── 系统栏 / 窗口（原 BaseReadBookActivity :158-274，window→activity.window） ──

    private val isInMultiWindow: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            activity.isInMultiWindowMode

    @SuppressLint("SourceLockedOrientationActivity")
    fun setOrientation() {
        when (AppConfig.screenOrientation) {
            "0" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            "1" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "2" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "3" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            "4" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        }
    }

    fun upSystemUiVisibility(
        isInMultiWindow: Boolean = this.isInMultiWindow,
        toolBarHide: Boolean = true,
        useBgMeanColor: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.insetsController?.run {
                if (toolBarHide && ReadBookConfig.hideNavigationBar) {
                    hide(WindowInsets.Type.navigationBars())
                } else {
                    show(WindowInsets.Type.navigationBars())
                }
                if (toolBarHide && ReadBookConfig.hideStatusBar) {
                    hide(WindowInsets.Type.statusBars())
                } else {
                    show(WindowInsets.Type.statusBars())
                }
            }
        }
        upSystemUiVisibilityO(isInMultiWindow, toolBarHide)
        if (toolBarHide) {
            activity.setLightStatusBar(ReadBookConfig.durConfig.curStatusIconDark())
        } else {
            val statusBarColor =
                if (AppConfig.readBarStyleFollowPage
                    && ReadBookConfig.durConfig.curBgType() == 0
                    || useBgMeanColor
                ) {
                    ReadBookConfig.bgMeanColor
                } else {
                    io.legado.app.lib.theme.ThemeStore.statusBarColor(activity, AppConfig.isTransparentStatusBar)
                }
            activity.setLightStatusBar(io.legado.app.utils.ColorUtils.isColorLight(statusBarColor))
        }
    }

    /** ReadMenu.CallBack / SearchMenu.CallBack 的无参形式 */
    override fun upSystemUiVisibility() {
        upSystemUiVisibility(isInMultiWindow, !menuLayoutIsVisible, bottomDialog > 0)
        upNavigationBarColor()
    }

    /** ReadAloudDialog.CallBack */
    override fun finish() {
        exitReader()
    }

    private fun upSystemUiVisibilityO(
        isInMultiWindow: Boolean,
        toolBarHide: Boolean = true
    ) {
        var flag = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        if (!isInMultiWindow) {
            flag = flag or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
        if (ReadBookConfig.hideNavigationBar) {
            flag = flag or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            if (toolBarHide) {
                flag = flag or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            }
        }
        if (ReadBookConfig.hideStatusBar && toolBarHide) {
            flag = flag or View.SYSTEM_UI_FLAG_FULLSCREEN
        }
        activity.window.decorView.systemUiVisibility = flag
    }

    private fun upNavigationBarColor() {
        upNavigationBar()
        when {
            menuVisible -> activity.setNavigationBarColorAuto(activity.bottomBackground)
            refs.searchMenu.bottomMenuVisible -> activity.setNavigationBarColorAuto(activity.bottomBackground)
            bottomDialog > 0 -> activity.setNavigationBarColorAuto(activity.bottomBackground)
            !AppConfig.immNavigationBar -> activity.setNavigationBarColorAuto(activity.bottomBackground)
            else -> activity.setNavigationBarColorAuto(ReadBookConfig.bgMeanColor)
        }
    }

    private fun upNavigationBar() {
        refs.navigationBar.isVisible = menuLayoutIsVisible
    }

    fun keepScreenOn(on: Boolean) {
        val isScreenOn =
            (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        if (on == isScreenOn) return
        if (on) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun upLayoutInDisplayCutoutMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.window.attributes = activity.window.attributes.apply {
                layoutInDisplayCutoutMode = if (ReadBookConfig.readBodyToLh) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                }
            }
        }
    }

    // ── Base 的配置弹窗（showDownloadDialog 等，activity 化） ──

    fun showDownloadDialog() {
        ReadBook.book?.let { book ->
            activity.alert(titleResource = R.string.offline_cache) {
                val alertBinding = io.legado.app.databinding.DialogDownloadChoiceBinding.inflate(activity.layoutInflater).apply {
                    editStart.setText((book.durChapterIndex + 1).toString())
                    editEnd.setText(book.totalChapterNum.toString())
                }
                customView { alertBinding.root }
                okButton {
                    alertBinding.run {
                        val start = editStart.text!!.toString().let {
                            if (it.isEmpty()) 0 else it.toInt()
                        }
                        val end = editEnd.text!!.toString().let {
                            if (it.isEmpty()) book.totalChapterNum else it.toInt()
                        }
                        CacheBook.start(activity, book, start - 1, end - 1)
                    }
                }
                cancelButton()
            }
        }
    }

    fun showSimulatedReading() {
        val book = ReadBook.book ?: return
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val alertBinding = io.legado.app.databinding.DialogSimulatedReadingBinding.inflate(activity.layoutInflater).apply {
            srEnabled.isChecked = book.getReadSimulating()
            editStart.setText(book.getStartChapter().toString())
            editNum.setText(book.getDailyChapters().toString())
            startDate.setText(book.getStartDate()?.format(dateFormatter))
            startDate.isFocusable = false
            startDate.isCursorVisible = false
            startDate.setOnClickListener {
                val localStartDate = LocalDate.parse(startDate.text)
                val datePickerDialog = android.app.DatePickerDialog(
                    activity,
                    { _, yy, mm, dayOfMonth ->
                        val date = LocalDate.of(yy, mm + 1, dayOfMonth)
                        val formattedDate = date.format(dateFormatter)
                        startDate.setText(formattedDate)
                    }, localStartDate.year,
                    localStartDate.monthValue - 1,
                    localStartDate.dayOfMonth
                )
                datePickerDialog.show()
            }
        }
        activity.alert(titleResource = R.string.simulated_reading) {
            customView { alertBinding.root }
            okButton {
                alertBinding.run {
                    val start = editStart.text!!.toString().let {
                        if (it.isEmpty()) 0 else it.toInt()
                    }
                    val num = editNum.text!!.toString().let {
                        if (it.isEmpty()) book.totalChapterNum else it.toInt()
                    }
                    val enabled = srEnabled.isChecked
                    val date = startDate.text!!.toString().let {
                        if (it.isEmpty()) LocalDate.now()
                        else LocalDate.parse(it, dateFormatter)
                    }
                    book.setStartDate(date)
                    book.setDailyChapters(num)
                    book.setStartChapter(start)
                    book.setReadSimulating(enabled)
                    book.save()
                    ReadBook.clearTextChapter()
                    viewModel.initData(routeIntent)
                }
            }
            cancelButton()
        }
    }

    fun showCharsetConfig() {
        activity.showM3EditDialog(
            title = activity.getString(R.string.set_charset),
            initialValue = ReadBook.book?.charset ?: "",
            hint = "charset",
            onConfirm = { value ->
                ReadBook.setCharset(value)
            },
        )
    }

    fun showPageAnimConfig(success: () -> Unit) {
        val items = arrayListOf<String>()
        items.add(activity.getString(R.string.btn_default_s))
        items.add(activity.getString(R.string.page_anim_cover))
        items.add(activity.getString(R.string.page_anim_slide))
        items.add(activity.getString(R.string.page_anim_simulation))
        items.add(activity.getString(R.string.page_anim_scroll))
        items.add(activity.getString(R.string.page_anim_none))
        activity.selector(R.string.page_anim, items) { _, i ->
            ReadBook.book?.setPageAnim(i - 1)
            success()
        }
    }

    fun showClickRegionalConfig() {
        activity.showDialogFragment<ClickActionConfigDialog>()
    }

    fun showCustomPageKeyConfig() {
        activity.showDialogFragment(PageKeyDialog())
    }

    fun isPrevKey(keyCode: Int): Boolean {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            return false
        }
        val prevKeysStr = activity.getPrefString(PreferKey.prevKeys)
        return prevKeysStr?.split(",")?.contains(keyCode.toString()) ?: false
    }

    fun isNextKey(keyCode: Int): Boolean {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            return false
        }
        val nextKeysStr = activity.getPrefString(PreferKey.nextKeys)
        return nextKeysStr?.split(",")?.contains(keyCode.toString()) ?: false
    }

    // ── 生命周期语义（Screen 组合副作用驱动） ──

    /** 原 Base.onCreate + Base.onActivityCreated + ReadBookActivity.onActivityCreated(:586-640) */
    fun onCreatedSetup() {
        ReadBook.msg = null
        setOrientation()
        upLayoutInDisplayCutoutMode()
        refs.navigationBar.setBackgroundColor(activity.bottomBackground)
        refs.navigationBar.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams {
                height = insets.bottom
            }
            windowInsets
        }
        refs.cursorLeft.setColorFilter(activity.accentColor)
        refs.cursorRight.setColorFilter(activity.accentColor)

        refs.cursorLeft.setOnTouchListener(this)
        refs.cursorRight.setOnTouchListener(this)
        activity.window.setBackgroundDrawable(null)
        upScreenTimeOut()
        updateMenuState()
        ReadBook.register(this)
        viewModel.permissionDenialLiveData.observe(activity) {
            launchers.selectBookFolder?.invoke {
                mode = HandleFileContract.DIR_SYS
                title = "选择书籍所在文件夹"
            }
        }
        if (!io.legado.app.help.config.LocalConfig.readHelpVersionIsLast) {
            if (activity.isTv) {
                showCustomPageKeyConfig()
            } else {
                showClickRegionalConfig()
            }
        }
    }

    /** 原 ReadBookActivity.onNewIntent(:642-647)：路由重开/搜索跳转进入 */
    fun onNewRouteIntent(intent: Intent) {
        capturePendingSearchJump(intent)
        viewModel.initData(intent)
    }

    /** 原 onWindowFocusChanged(:649-657) */
    fun onWindowFocusChanged(hasFocus: Boolean) {
        upSystemUiVisibility()
        if (hasFocus) {
            updateMenuState()
        } else if (!menuLayoutIsVisible) {
            ReadBook.cancelPreDownloadTask()
        }
    }

    /** 原 onConfigurationChanged(:659-663) */
    fun onConfigurationChanged() {
        upSystemUiVisibility()
        refs.readView.upStatusBar()
    }

    /** 原 onResume(:672-705) */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun onResumed() {
        isResumedState = true
        if (canCountReadTime()) {
            ReadBook.startCounting()
        }
        if (bookChanged) {
            bookChanged = false
            ReadBook.callBack = this
            viewModel.initData(routeIntent)
            justInitData = true
        } else {
            ReadBook.webBookProgress?.let {
                ReadBook.setProgress(it)
                ReadBook.webBookProgress = null
            }
        }
        upSystemUiVisibility()
        activity.registerReceiver(screenStatusReceiver, screenStatusFilter)
        activity.registerReceiver(timeBatteryReceiver, timeBatteryReceiver.filter)
        refs.readView.upTime()
        screenOffTimerStart()
        handler.removeCallbacks(readTimeTickRunnable)
        handler.postDelayed(readTimeTickRunnable, READ_TIME_TICK_MS)
        networkChangedListener.register()
        networkChangedListener.onNetworkChanged = {
            if (AppConfig.syncBookProgressPlus && NetworkUtils.isAvailable() && !justInitData && ReadBook.inBookshelf) {
                ReadBook.syncProgress({ progress -> sureNewProgress(progress) })
            }
        }
    }

    /** 原 onPause(:707-731) */
    fun onPaused() {
        autoPageStop()
        backupJob?.cancel()
        isResumedState = false
        handler.removeCallbacks(readTimeTickRunnable)
        ReadBook.upReadTime()
        ReadBook.saveRead()
        ReadBook.cancelPreDownloadTask()
        runCatching { activity.unregisterReceiver(screenStatusReceiver) }
        runCatching { activity.unregisterReceiver(timeBatteryReceiver) }
        upSystemUiVisibility()
        if (!io.legado.app.BuildConfig.DEBUG && ReadBook.inBookshelf) {
            if (AppConfig.syncBookProgressPlus) {
                ReadBook.syncProgress()
            } else {
                ReadBook.uploadProgress()
            }
        }
        if (!io.legado.app.BuildConfig.DEBUG) {
            Backup.autoBack(activity)
        }
        justInitData = false
        networkChangedListener.unRegister()
    }

    /** 原 onDestroy(:2244-2258) */
    fun onDestroyCleanup() {
        tts?.clearTts()
        textActionMenuState = null
        popupAction.dismiss()
        refs.readView.onDestroy()
        ReadBook.upReadTime()
        ReadBook.unregister(this)
        if (!ReadBook.inBookshelf) {
            viewModel.removeFromBookshelf(null)
        }
        if (!io.legado.app.BuildConfig.DEBUG) {
            Backup.autoBack(activity)
        }
    }

    fun observeEvents() {
        activity.observeEvent<String>(EventBus.TIME_CHANGED) { refs.readView.upTime() }
        activity.observeEvent<Int>(EventBus.BATTERY_CHANGED) { refs.readView.upBattery(it) }
        activity.observeEvent<Boolean>(EventBus.MEDIA_BUTTON) {
            if (it) {
                onClickReadAloud()
            } else {
                ReadBook.readAloud(!BaseReadAloudService.pause)
            }
        }
        activity.lifecycleScope.launch {
            ReadStyleRefreshBus.refreshFlow.collect { upConfigGroups(it) }
        }
        activity.observeEvent<Int>(EventBus.ALOUD_STATE) {
            readAloudRunning = BaseReadAloudService.isRun
            readAloudPaused = BaseReadAloudService.pause || it == Status.PAUSE || it == Status.STOP
            if (it == Status.STOP) {
                readAloudRunning = false
                if (showReadAloudPlayer) {
                    hideReadAloudPlayerOverlay()
                }
            }
            if (it == Status.STOP || it == Status.PAUSE) {
                ReadBook.curTextChapter?.let { textChapter ->
                    val page = textChapter.getPageByReadPos(ReadBook.durChapterPos)
                    if (page != null) {
                        page.removePageAloudSpan()
                        refs.readView.upContent(resetPageOffset = false)
                    }
                }
            }
        }
        activity.observeEventSticky<Int>(EventBus.TTS_PROGRESS) { chapterStart ->
            activity.lifecycleScope.launch(IO) {
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
        activity.observeEvent<Boolean>(PreferKey.keepLight) {
            upScreenTimeOut()
        }
        activity.observeEvent<Boolean>(PreferKey.textSelectAble) {
            refs.readView.curPage.upSelectAble(it)
        }
        activity.observeEvent<String>(PreferKey.showBrightnessView) {
            updateMenuState()
        }
        activity.observeEvent<List<SearchResult>>(EventBus.SEARCH_RESULT) {
            viewModel.searchResultList = it
        }
        activity.observeEvent<Boolean>(EventBus.UPDATE_READ_ACTION_BAR) {
            updateMenuState()
        }
        activity.observeEvent<Boolean>(EventBus.UP_SEEK_BAR) {
            updateMenuState()
        }
    }

    // ── 菜单/界面编排（原 :551-572, :1289-1304, :1538-1798 等） ──

    fun showReadAloudPlayerOverlay() {
        if (showReadAloudPlayer) return
        activity.supportFragmentManager.fragments
            .filterIsInstance<ReadAloudDialog>()
            .forEach { it.dismissAllowingStateLoss() }
        showReadAloudPlayer = true
        bottomDialog++
    }

    fun hideReadAloudPlayerOverlay(openClassic: Boolean = false) {
        showCloudTtsOverlay = false
        showVoiceCastingOverlay = false
        if (showReadAloudPlayer) {
            showReadAloudPlayer = false
            bottomDialog = (bottomDialog - 1).coerceAtLeast(0)
        }
        if (openClassic) {
            activity.showDialogFragment<ReadAloudDialog>()
        }
    }

    /** 模拟原 recreate()：路由形态不能重建 MainActivity，改为内容重载 */
    fun recreateSubstitute() {
        ReadBook.loadContent(false)
    }

    override fun upMenuView() {
        handler.post {
            upMenu()
            updateMenuState()
        }
    }

    /**
     * 更新菜单（options 菜单在路由形态不存在，menu 恒为 null 即空实现；M3 Compose 菜单接管）
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
        activity.lifecycleScope.launch {
            val show = ReadBook.inBookshelf && withContext(IO) {
                AppWebDav.isOk
            }
            menu.findItem(R.id.menu_get_progress)?.isVisible = show
            menu.findItem(R.id.menu_cover_progress)?.isVisible = show
        }
    }

    override fun loadChapterList(book: Book) {
        ReadBook.upMsg(activity.getString(R.string.toc_updateing))
        viewModel.loadChapterList(book)
    }

    override fun contentLoadFinish() {
        if (route.readAloud && !loadStates) {
            ReadBook.readAloud()
        }
        loadStates = true
        maybeApplyPendingSearchJump()
    }

    override fun upContent(
        relativePosition: Int,
        resetPageOffset: Boolean,
        success: (() -> Unit)?
    ) {
        activity.lifecycleScope.launch {
            refs.readView.upContent(relativePosition, resetPageOffset)
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
        refs.readView.upContent(relativePosition, resetPageOffset)
        if (relativePosition == 0) {
            upSeekBarProgress()
        }
        loadStates = false
    }

    override fun upPageAnim(upRecorder: Boolean) {
        activity.lifecycleScope.launch {
            refs.readView.upPageAnim(upRecorder)
        }
    }

    override fun notifyBookChanged() {
        bookChanged = true
        if (!ReadBook.inBookshelf) {
            viewModel.removeFromBookshelf { exitReader() }
        }
    }

    override fun cancelSelect() {
        activity.runOnUiThread {
            refs.readView.cancelSelect()
        }
    }

    override fun pageChanged() {
        pageChanged = true
        refs.readView.onPageChange()
        handler.post {
            upSeekBarProgress()
        }
        executor.execute {
            startBackupJob()
        }
    }

    private fun upSeekBarProgress() {
        val progress = when (AppConfig.progressBarBehavior) {
            "page" -> ReadBook.durPageIndex
            else /* chapter */ -> ReadBook.durChapterIndex
        }
        menuUiState = menuUiState.copy(seekProgress = progress)
    }

    override fun showMenuBar() {
        showMenuBarPanel()
    }

    override val oldBook: Book?
        get() = ReadBook.book

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        if (!book.isAudio) {
            viewModel.changeTo(book, toc)
        } else {
            ReadAloud.stop(activity)
            activity.lifecycleScope.launch {
                withContext(IO) {
                    ReadBook.book?.migrateTo(book, toc)
                    book.removeType(BookType.updateError)
                    ReadBook.book?.delete()
                    appDb.bookDao.insert(book)
                }
                activity.startActivityForBook(book)
                exitReader()
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
            isAutoPage -> activity.showDialogFragment<AutoReadDialog>()
            isShowingSearchResult -> refs.searchMenu.runMenuIn()
            else -> showMenuBarPanel()
        }
    }

    fun showReadAloudDialog() {
        if (AppConfig.readAloudDefaultInterface == "player") {
            showReadAloudPlayerOverlay()
        } else {
            activity.showDialogFragment<ReadAloudDialog>()
        }
    }

    override fun showReadAloudPlayer() {
        showReadAloudPlayerOverlay()
    }

    fun autoPage() {
        ReadAloud.stop(activity)
        if (isAutoPage) {
            autoPageStop()
        } else {
            refs.readView.autoPager.start()
            menuUiState = menuUiState.copy(autoPageActive = true)
            screenTimeOut = -1L
            screenOffTimerStart()
        }
    }

    override fun autoPageStop() {
        if (isAutoPage) {
            refs.readView.autoPager.stop()
            menuUiState = menuUiState.copy(autoPageActive = false)
            activity.dismissDialogFragment<AutoReadDialog>()
            upScreenTimeOut()
        }
    }

    fun openSourceEditActivity() {
        ReadBook.bookSource?.let {
            launchers.sourceEdit?.invoke { putExtra("sourceUrl", it.bookSourceUrl) }
        }
    }

    fun openBookInfoActivity() {
        ReadBook.book?.let { b ->
            val nav = onNavigateToRoute
            if (nav != null) {
                nav(
                    io.legado.app.ui.main.MainRouteBookInfo(
                        name = b.name,
                        author = b.author,
                        bookUrl = b.bookUrl,
                        origin = b.origin,
                    )
                )
            } else {
                routeState.pendingBookInfoUrl = b.bookUrl
                launchers.bookInfoDone?.invoke()
            }
        }
    }

    fun openReplaceRule() {
        launchers.replace?.invoke(
            Intent(activity, ReplaceRuleActivity::class.java)
        )
    }

    override fun openChapterList() {
        ReadBook.book?.let {
            launchers.toc?.invoke(it.bookUrl)
        }
    }

    override fun openSearchActivity(searchWord: String?) {
        val book = ReadBook.book ?: return
        launchers.searchContent?.invoke {
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

    fun disableSource() {
        viewModel.disableSource()
    }

    fun showReadStyle() {
        bottomDialog++
        showReadStyleSheet = true
    }

    fun showMoreSetting() {
        bottomDialog++
        showMoreConfigSheet = true
    }

    override fun showSearchSetting() {
        bottomDialog++
        showMoreConfigSheet = true
    }

    override fun exitSearchMenu() {
        if (isShowingSearchResult) {
            isShowingSearchResult = false
            refs.searchMenu.invalidate()
            refs.searchMenu.invisible()
            ReadBook.clearSearchResult()
            refs.readView.cancelSelect(true)
        }
    }

    private fun restoreLastBookProcess() {
        if (confirmRestoreProcess == true) {
            ReadBook.restoreLastBookProgress()
        } else if (confirmRestoreProcess == null) {
            activity.alert(R.string.draw) {
                setMessage(R.string.restore_last_book_process)
                yesButton {
                    confirmRestoreProcess = true
                    ReadBook.restoreLastBookProgress()
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

    fun showLogin() {
        ReadBook.bookSource?.let {
            activity.startActivity<SourceLoginActivity> {
                putExtra("type", "bookSource")
                putExtra("key", it.bookSourceUrl)
            }
        }
    }

    fun payAction() {
        val book = ReadBook.book ?: return
        if (book.isLocal) return
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
        if (chapter == null) {
            toastOnUi("no chapter")
            return
        }
        activity.alert(R.string.chapter_pay) {
            setMessage(chapter.title)
            yesButton {
                Coroutine.async(activity.lifecycleScope) {
                    val source =
                        ReadBook.bookSource ?: throw NoStackTraceException("no book source")
                    val payAction = source.getContentRule().payAction
                    if (payAction.isNullOrBlank()) {
                        throw NoStackTraceException("no pay action")
                    }
                    val analyzeRule = AnalyzeRule(book, source)
                    analyzeRule.setCoroutineContext(kotlin.coroutines.coroutineContext)
                    analyzeRule.setBaseUrl(chapter.url)
                    analyzeRule.setChapter(chapter)
                    analyzeRule.evalJS(payAction).toString()
                }.onSuccess(IO) {
                    if (it.isAbsUrl()) {
                        activity.startActivity<WebViewActivity> {
                            val bookSource = ReadBook.bookSource
                            putExtra("title", activity.getString(R.string.chapter_pay))
                            putExtra("url", it)
                            putExtra("sourceOrigin", bookSource?.bookSourceUrl)
                            putExtra("sourceName", bookSource?.bookSourceName)
                            putExtra("sourceType", bookSource?.getSourceType())
                        }
                    } else if (it.isTrue()) {
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

    override fun onClickReadAloud() {
        autoPageStop()
        when {
            !BaseReadAloudService.isRun -> {
                ReadAloud.upReadAloudClass()
                val scrollPageAnim = ReadBook.pageAnim() == 3
                if (scrollPageAnim) {
                    val pos = refs.readView.getReadAloudPos()
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
                    val pos = refs.readView.getReadAloudPos()
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
                    ReadAloud.resume(activity)
                }
            }

            else -> ReadAloud.pause(activity)
        }
    }

    fun showHelp() {
        activity.showHelp("readMenuHelp")
    }

    @SuppressLint("RtlHardcoded")
    override fun onImageLongPress(x: Float, y: Float, src: String) {
        popupAction.setItems(
            listOf(
                SelectItem(activity.getString(R.string.show), "show"),
                SelectItem(activity.getString(R.string.refresh), "refresh"),
                SelectItem(activity.getString(R.string.action_save), "save"),
                SelectItem(activity.getString(R.string.menu), "menu"),
                SelectItem(activity.getString(R.string.select_folder), "selectFolder")
            )
        )
        popupAction.onActionClick = {
            when (it) {
                "show" -> activity.showDialogFragment(PhotoDialog(src))
                "refresh" -> viewModel.refreshImage(src)
                "save" -> {
                    val path = ACache.get().getAsString(AppConst.imagePathKey)
                    if (path.isNullOrEmpty()) {
                        launchers.selectImageDir?.invoke {
                            value = src
                        }
                    } else {
                        viewModel.saveImage(src, path.toUri())
                    }
                }

                "menu" -> showActionMenu()
                "selectFolder" -> launchers.selectImageDir?.invoke(null)
            }
            popupAction.dismiss()
        }
        val navigationBarHeight =
            if (!ReadBookConfig.hideNavigationBar && activity.navigationBarGravity == Gravity.BOTTOM)
                refs.navigationBar.height else 0
        popupAction.showAtLocation(
            refs.readView, Gravity.BOTTOM or Gravity.LEFT, x.toInt(),
            refs.root.height + navigationBarHeight - y.toInt()
        )
    }

    override fun onColorSelected(dialogId: Int, color: Int) = ReadBookConfig.durConfig.run {
        when (dialogId) {
            TEXT_COLOR -> {
                setCurTextColor(color)
                ReadStyleRefreshBus.refresh(2, 6, 9, 11)
                if (AppConfig.readBarStyleFollowPage) {
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }
            }

            BG_COLOR -> {
                setCurBg(0, "#${color.hexString}")
                ReadStyleRefreshBus.refresh(1)
                if (AppConfig.readBarStyleFollowPage) {
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }
            }

            TIP_COLOR -> {
                ReadTipConfig.tipColor = color
                postEvent(EventBus.TIP_COLOR, "")
                ReadStyleRefreshBus.refresh(2)
            }

            TIP_DIVIDER_COLOR -> {
                ReadTipConfig.tipDividerColor = color
                postEvent(EventBus.TIP_COLOR, "")
                ReadStyleRefreshBus.refresh(2)
            }
        }
    }

    override fun onDialogDismissed(dialogId: Int) = Unit

    fun onTocRegexDialogResult(tocRegex: String) {
        ReadBook.book?.let {
            it.tocUrl = tocRegex
            loadChapterList(it)
        }
    }

    private fun sureSyncProgress(progress: BookProgress) {
        activity.alert(R.string.get_book_progress) {
            setMessage(R.string.current_progress_exceeds_cloud)
            okButton {
                ReadBook.setProgress(progress)
            }
            noButton()
        }
    }

    fun skipToChapter(index: Int) {
        ReadBook.saveCurrentBookProgress()
        viewModel.openChapter(index)
    }

    override fun navigateToSearch(searchResult: SearchResult, index: Int) {
        viewModel.searchResultIndex = index
        skipToSearch(searchResult)
    }

    private fun upConfigGroups(groups: List<Int>) {
        groups.forEach { value ->
            when (value) {
                0 -> upSystemUiVisibility()
                1 -> refs.readView.upBg()
                2 -> refs.readView.upStyle()
                3 -> refs.readView.upBgAlpha()
                4 -> refs.readView.upPageSlopSquare()
                5 -> if (isInitFinish) ReadBook.loadContent(resetPageOffset = false)
                6 -> refs.readView.upContent(resetPageOffset = false)
                8 -> ChapterProvider.upStyle()
                9 -> refs.readView.invalidateTextPage()
                10 -> ChapterProvider.upLayout()
                11 -> refs.readView.submitRenderTask()
                12 -> upPageAnim()
            }
        }
    }

    override fun onMenuShow() {
        refs.readView.autoPager.pause()
    }

    override fun onMenuHide() {
        refs.readView.autoPager.resume()
    }

    override fun onLayoutPageCompleted(index: Int, page: TextPage) {
        upSeekBarThrottle.invoke()
        refs.readView.onLayoutPageCompleted(index, page)
    }

    private fun skipToSearch(searchResult: SearchResult) {
        if (searchResult.chapterIndex != ReadBook.durChapterIndex) {
            viewModel.openChapter(searchResult.chapterIndex) {
                jumpToPosition(searchResult)
            }
        } else {
            jumpToPosition(searchResult)
        }
    }

    /** 搜索内容结果跳转入口（searchContent launcher 回调经此进入） */
    fun applySearchJump(key: Long, index: Int) = applySearchJumpFromIntentData(key, index)

    fun capturePendingSearchJump(key: Long, index: Int) {
        if (key <= 0L) return
        pendingSearchJumpKey = key
        pendingSearchJumpIndex = index
    }

    fun capturePendingSearchJump(intent: Intent?) {
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
        refs.searchMenu.upSearchResultList(searchResultList)
        isShowingSearchResult = true
        viewModel.searchResultIndex = index
        refs.searchMenu.updateSearchResultIndex(index)
        refs.searchMenu.selectedSearchResult?.let { currentResult ->
            ReadBook.saveCurrentBookProgress()
            skipToSearch(currentResult)
            showActionMenu()
        }
    }

    private fun jumpToPosition(searchResult: SearchResult) {
        val curTextChapter = ReadBook.curTextChapter ?: return
        refs.searchMenu.updateSearchInfo()
        val (pageIndex, lineIndex, charIndex, addLine, charIndex2) =
            viewModel.searchResultPositions(curTextChapter, searchResult)
        ReadBook.skipToPage(pageIndex) {
            isSelectingSearchResult = true
            refs.readView.curPage.selectStartMoveIndex(0, lineIndex, charIndex)
            when (addLine) {
                0 -> refs.readView.curPage.selectEndMoveIndex(
                    0,
                    lineIndex,
                    charIndex + viewModel.searchContentQuery.length - 1
                )

                1 -> refs.readView.curPage.selectEndMoveIndex(
                    0, lineIndex + 1, charIndex2
                )
                -1 -> refs.readView.curPage.selectEndMoveIndex(1, 0, charIndex2)
            }
            refs.readView.isTextSelected = true
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
            activity.showDialogFragment(BookmarkDialog(bookmark, showDelete = true))
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
        backupJob = activity.lifecycleScope.launch(IO) {
            delay(300000)
            ReadBook.book?.let {
                AppWebDav.uploadBookProgress(it)
                ensureActive()
                it.update()
                Backup.autoBack(activity)
            }
        }
    }

    override fun sureNewProgress(progress: BookProgress) {
        syncDialog?.dismiss()
        syncDialog = activity.alert(R.string.get_book_progress) {
            setMessage(R.string.cloud_progress_exceeds_current)
            okButton {
                ReadBook.setProgress(progress)
            }
            noButton()
        }
    }

    /**
     * 返回键拦截链（原 onActivityCreated :597-639 的 dispatcher callback）。
     * @return true 表示已拦截消费
     */
    fun handleBack(): Boolean {
        when {
            showCloudTtsOverlay -> {
                showCloudTtsOverlay = false
                return true
            }
            showVoiceCastingOverlay -> {
                showVoiceCastingOverlay = false
                return true
            }
            showReadAloudPlayer -> {
                hideReadAloudPlayerOverlay()
                return true
            }
        }
        if (isShowingSearchResult) {
            exitSearchMenu()
            restoreLastBookProcess()
            return true
        }
        //拦截返回供恢复阅读进度
        if (ReadBook.lastBookProgress != null && confirmRestoreProcess != false) {
            restoreLastBookProcess()
            return true
        }
        if (BaseReadAloudService.isPlay()) {
            ReadAloud.pause(activity)
            activity.toastOnUi(R.string.read_aloud_pause)
            return true
        }
        if (isAutoPage) {
            autoPageStop()
            return true
        }
        if ((AppConfigStore.getBoolean(PreferKey.disableReturnKey) ?: false)
            && !menuLayoutIsVisible
        ) {
            return true
        }
        return false
    }

    /** 原 finish()(:2221-2242)：加书架确认后经 onExitReader 退出路由 */
    fun exitReader() {
        val book = ReadBook.book ?: return onExitReader()

        if (ReadBook.inBookshelf) {
            return onExitReader()
        }

        if (!AppConfig.showAddToShelfAlert) {
            viewModel.removeFromBookshelf { onExitReader() }
        } else {
            activity.alert(title = activity.getString(R.string.add_to_bookshelf)) {
                setMessage(activity.getString(R.string.check_add_bookshelf, book.name))
                okButton {
                    ReadBook.book?.removeType(BookType.notShelf)
                    ReadBook.book?.save()
                    ReadBook.inBookshelf = true
                    routeState.addedToShelf = true
                }
                noButton { viewModel.removeFromBookshelf { onExitReader() } }
            }
        }
    }

    private fun upScreenTimeOut() {
        val keepLightPrefer =
            AppConfigStore.getString(PreferKey.keepLight)?.toIntOrNull() ?: 0
        screenTimeOut = keepLightPrefer * 1000L
        screenOffTimerStart()
    }

    override fun screenOffTimerStart() {
        handler.post {
            if (screenTimeOut < 0) {
                keepScreenOn(true)
                return@post
            }
            val t = screenTimeOut - activity.sysScreenOffTime
            if (t > 0) {
                keepScreenOn(true)
                handler.removeCallbacks(screenOffRunnable)
                handler.postDelayed(screenOffRunnable, screenTimeOut)
            } else {
                keepScreenOn(false)
            }
        }
    }

    // ── 属性覆写（接口要求） ──

    override val curFontPath: String
        get() = ReadBookConfig.textFont

    override fun selectFont(path: String) {
        if (path != ReadBookConfig.textFont || path.isEmpty()) {
            ReadBookConfig.textFont = path
            ReadStyleRefreshBus.refresh(2, 5)
        }
    }

    override val isInitFinish: Boolean get() = viewModel.isInitFinish
    override val isScroll: Boolean get() = refs.readView.isScroll
    override var isShowingSearchResult = false
    override var isSelectingSearchResult = false
        set(value) {
            field = value && isShowingSearchResult
        }
    override val pageFactory get() = refs.readView.pageFactory
    override val pageDelegate get() = refs.readView.pageDelegate
    override val headerHeight: Int get() = refs.readView.curPage.headerHeight

    val selectedText: String get() = refs.readView.getSelectText()

    // ── 文字选择 / 光标（原 :1210-1304） ──

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (!refs.readView.isTextSelected) {
            return false
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> textActionMenuState = null
            MotionEvent.ACTION_MOVE -> {
                when (v.id) {
                    R.id.cursor_left -> if (!refs.readView.curPage.getReverseStartCursor()) {
                        refs.readView.curPage.selectStartMove(
                            event.rawX + refs.cursorLeft.width,
                            event.rawY - refs.cursorLeft.height
                        )
                    } else {
                        refs.readView.curPage.selectEndMove(
                            event.rawX - refs.cursorRight.width,
                            event.rawY - refs.cursorRight.height
                        )
                    }

                    R.id.cursor_right -> if (refs.readView.curPage.getReverseEndCursor()) {
                        refs.readView.curPage.selectStartMove(
                            event.rawX + refs.cursorLeft.width,
                            event.rawY - refs.cursorLeft.height
                        )
                    } else {
                        refs.readView.curPage.selectEndMove(
                            event.rawX - refs.cursorRight.width,
                            event.rawY - refs.cursorRight.height
                        )
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                refs.readView.curPage.resetReverseCursor()
                showTextActionMenu()
            }
        }
        return true
    }

    override fun upSelectedStart(x: Float, y: Float, top: Float) {
        refs.cursorLeft.x = x - refs.cursorLeft.width
        refs.cursorLeft.y = y
        refs.cursorLeft.visible(true)
        refs.textMenuPosition.x = x
        refs.textMenuPosition.y = top
    }

    override fun upSelectedEnd(x: Float, y: Float) {
        refs.cursorRight.x = x
        refs.cursorRight.y = y
        refs.cursorRight.visible(true)
    }

    override fun onCancelSelect() {
        refs.cursorLeft.invisible()
        refs.cursorRight.invisible()
        textActionMenuState = null
    }

    override fun onLongScreenshotTouchEvent(event: MotionEvent): Boolean {
        return refs.readView.onTouchEvent(event)
    }

    override fun showTextActionMenu() {
        val showSplitChapter = ReadBook.curTextChapter?.chapter?.let {
            BookHelp.isReChaptered(it)
        } == true
        expandTextMenu = AppConfigStore.getBoolean(PreferKey.expandTextMenu) ?: false
        textActionMenuState = TextMenuState(
            selectedText = refs.readView.getSelectText(),
            startX = refs.textMenuPosition.x.toInt(),
            startTopY = refs.textMenuPosition.y.toInt(),
            startBottomY = refs.cursorLeft.y.toInt() + refs.cursorLeft.height,
            endX = refs.cursorRight.x.toInt(),
            endBottomY = refs.cursorRight.y.toInt() + refs.cursorRight.height,
            items = buildTextActionMenuItems(activity, showSplitChapter),
        )
    }

    // ── 文本菜单操作（原 :1314-1466） ──

    fun onMenuItemSelected(itemId: Int): Boolean {
        when (itemId) {
            R.id.menu_aloud -> when (AppConfig.contentSelectSpeakMod) {
                1 -> activity.lifecycleScope.launch {
                    refs.readView.aloudStartSelect()
                }

                else -> speak(refs.readView.getSelectText())
            }

            R.id.menu_bookmark -> refs.readView.curPage.let {
                val bookmark = it.createBookmark()
                if (bookmark == null) {
                    toastOnUi(R.string.create_bookmark_error)
                } else {
                    activity.showDialogFragment(BookmarkDialog(bookmark, showDelete = true))
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
                launchers.replace?.invoke(
                    ReplaceRuleActivity.startIntent(
                        activity,
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
                activity.showDialogFragment(createDictSheetDialog(selectedText, dictSearchContext))
                return true
            }

            R.id.menu_split_chapter -> {
                splitChapterHere(selectedText)
                return true
            }
        }
        return false
    }

    private fun splitChapterHere(selected: String) {
        val book = ReadBook.book ?: return
        val source = ReadBook.bookSource ?: return
        val textChapter = ReadBook.curTextChapter ?: return
        val chapter = textChapter.chapter
        if (!BookHelp.isReChaptered(chapter)) {
            toastOnUi(R.string.split_chapter_failed)
            return
        }
        activity.lifecycleScope.launch {
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

    private fun onMenuActionFinally() {
        textActionMenuState = null
        refs.readView.cancelSelect()
    }

    fun onTextActionItemClick(item: ActionMenuItem) {
        val text = textActionMenuState?.selectedText
        val handled = onMenuItemSelected(item.id)
        if (!handled) {
            when (item.id) {
                R.id.menu_copy -> text?.let { activity.sendToClip(it) }
                R.id.menu_share_str -> text?.let { activity.share(it) }
                R.id.menu_browser -> text?.let { openTextInBrowser(it) }
                else -> {
                    val intent = item.intent ?: return
                    kotlin.runCatching {
                        intent.putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                        activity.startActivity(intent)
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
            activity.startActivity(intent)
        }.onFailure {
            it.printOnDebug()
            toastOnUi(it.localizedMessage ?: "ERROR")
        }
    }

    fun onTextActionItemLongClick(item: ActionMenuItem) {
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

    // ── 按键 / 滚轮翻页（原 :1107-1536；路由形态由 Screen/MainActivity 分发调用） ──

    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action
        val isDown = action == 0

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (isDown && !menuVisible) {
                showMenuBarPanel()
                return true
            }
            if (!isDown && !menuVisible) {
                menuVisible = true
                return true
            }
        }
        return false
    }

    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (0 != (event.source and InputDevice.SOURCE_CLASS_POINTER)) {
            if (event.action == MotionEvent.ACTION_SCROLL) {
                val axisValue = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                io.legado.app.utils.LogUtils.d("onGenericMotionEvent", "axisValue = $axisValue")
                if (axisValue < 0.0f) {
                    mouseWheelPage(PageDirection.NEXT)
                } else {
                    mouseWheelPage(PageDirection.PREV)
                }
                return true
            }
        }
        return false
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (menuLayoutIsVisible) {
            return false
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
        return false
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (volumeKeyPage(PageDirection.NONE, false)) {
                    return true
                }
            }
        }
        return false
    }

    private fun mouseWheelPage(direction: PageDirection) {
        if (menuLayoutIsVisible || !AppConfig.mouseWheelPage) {
            return
        }
        keyPageDebounce(direction, mouseWheel = true, longPress = false)
    }

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
        refs.readView.cancelSelect()
        refs.readView.pageDelegate?.isCancel = false
        refs.readView.pageDelegate?.keyTurnPage(direction)
    }

    // ── 重新分章（原 :1007-1098） ──

    fun refreshContentAll(book: Book) {
        ReadBook.clearTextChapter()
        refs.readView.upContent()
        viewModel.refreshContentAll(book)
    }

    fun handleReChapter() {
        val book = ReadBook.book ?: return
        val source = ReadBook.bookSource ?: return
        if (!book.reChapterEnabled) {
            book.reChapterEnabled = true
            if (ReadBook.inBookshelf) appDb.bookDao.update(book)
            activity.selector(
                R.string.re_chapter,
                listOf(
                    activity.getString(R.string.re_chapter_nearby),
                    activity.getString(R.string.re_chapter_full),
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
            activity.selector(
                R.string.re_chapter,
                listOf<CharSequence>(
                    activity.getString(R.string.re_chapter_again),
                    activity.getString(R.string.re_chapter_full),
                    activity.getString(R.string.re_chapter_disable),
                )
            ) { _, index ->
                when (index) {
                    0 -> runReChapter(book, source, showFailure = true) {
                        ReChapterUseCase(book, source).resetAndReChapter()
                    }
                    1 -> runReChapter(book, source, showFailure = true) {
                        ReChapterUseCase(book, source).fullBookReChapter()
                    }
                    else -> activity.lifecycleScope.launch {
                        if (ReChapterUseCase.isRunning(book.bookUrl)) {
                            toastOnUi(R.string.re_chapter_running)
                            return@launch
                        }
                        val restored = withContext(IO) {
                            ReChapterUseCase(book, source).disableAndRestore()
                        }
                        if (restored) {
                            toastOnUi(R.string.re_chapter_disabled_restored)
                        }
                    }
                }
            }
        }
    }

    private fun runReChapter(
        book: Book,
        source: BookSource,
        showFailure: Boolean,
        run: suspend () -> ExecuteResult,
    ) {
        activity.lifecycleScope.launch {
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

    private fun toastOnUi(resId: Int) = activity.toastOnUi(resId)
    private fun toastOnUi(text: String) = activity.toastOnUi(text)

    fun upContent() {
        upContent(0, true, null)
    }

    companion object {
        /** 阅读时长定时结算周期：兜底落库，任何漏掉的事件误差上限即此值 */
        private const val READ_TIME_TICK_MS = 300_000L
    }
}
