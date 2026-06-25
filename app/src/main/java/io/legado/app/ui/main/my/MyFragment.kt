package io.legado.app.ui.main.my

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.jaredrummler.android.colorpicker.ColorShape
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.databinding.DialogImageBlurringBinding
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.CrashHandler
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.storage.BackupConfig
import io.legado.app.help.storage.ImportOldData
import io.legado.app.help.storage.Restore
import io.legado.app.help.update.AppUpdate
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.prefs.ColorPreference
import io.legado.app.model.BookCover
import io.legado.app.service.WebService
import io.legado.app.ui.about.UpdateDialog
import io.legado.app.ui.book.bookmark.AllBookmarkActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.config.CheckSourceConfig
import io.legado.app.ui.config.CoverRuleConfigDialog
import io.legado.app.ui.config.DirectLinkUploadConfig
import io.legado.app.ui.config.ThemeListDialog
import io.legado.app.ui.dict.rule.DictRuleActivity
import io.legado.app.ui.file.FileManageActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.createFolderIfNotExist
import io.legado.app.utils.delete
import io.legado.app.utils.externalCache
import io.legado.app.utils.externalFiles
import io.legado.app.utils.find
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.inputStream
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.list
import io.legado.app.utils.observeEventSticky
import io.legado.app.utils.openInputStream
import io.legado.app.utils.openOutputStream
import io.legado.app.utils.openUrl
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.readUri
import io.legado.app.utils.removePref
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.showCrashLogSheet
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.showLogSheet
import io.legado.app.utils.showMarkdownSheet
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream

/**
 * “我的”页 Fragment。直接返回全屏 [ComposeView]，内含 [MyNavOverlay]（Navigation 3 覆盖层）。
 *
 * 之所以不再用 fragment_my_config.xml 的 TitleBar + preFragment 结构：覆盖层需要占满
 * 全屏才能让子路由（阅读记录等）的 Compose pop 动画对齐 MD3（缩放透出下层、不露顶/底栏）。
 * 对齐 BookshelfComposeFragment 的全屏 ComposeView 模式。
 */
class MyFragment() : BaseFragment(0), MainFragmentInterface, ColorPickerDialogListener {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    private val _webServiceRunning = MutableStateFlow(WebService.isRun)
    private val _webServiceAddress = MutableStateFlow(WebService.hostAddress)

    // ---- 备份/恢复相关字段（迁自 BackupConfigComposeFragment） ----
    private val backupWaitDialog by lazy { WaitDialog(requireContext()) }
    private var restoreJob: Job? = null

    private val selectBackupPath = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            val path = if (uri.isContentScheme()) uri.toString() else uri.path ?: return@registerForActivityResult
            AppConfig.backupPath = path
        }
    }

    private val restoreDoc = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            backupWaitDialog.setText("恢复中…")
            backupWaitDialog.show()
            val task = Coroutine.async {
                Restore.restore(appCtx, uri)
            }.onFinally {
                backupWaitDialog.dismiss()
            }
            backupWaitDialog.setOnCancelListener {
                task.cancel()
            }
        }
    }

    private val restoreOld = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            ImportOldData.importUri(appCtx, uri)
        }
    }

    // ---- 主题设置相关字段（迁自 ThemeConfigComposeFragment） ----
    private var pendingColorCallback: ((Color) -> Unit)? = null
    private var currentDialogTag: String? = null
    private var pendingBgIsNight: Boolean = false
    private var pendingBgChange: (() -> Unit)? = null

    private val selectBgImage = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            val bgKey = if (pendingBgIsNight) PreferKey.bgImageN else PreferKey.bgImage
            setBgFromUri(uri, bgKey) {
                pendingBgChange?.invoke()
            }
        }
    }

    // ---- 启动界面样式/封面配置字段（迁自 WelcomeConfigComposeFragment / CoverConfigComposeFragment） ----
    private var pendingWelcomeIsNight: Boolean = false
    private var pendingCoverIsNight: Boolean = false

    private val selectWelcomeImage = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            val key = if (pendingWelcomeIsNight) PreferKey.welcomeImageDark else PreferKey.welcomeImage
            setWelcomeImageFromUri(key, uri)
        }
    }

    private val selectCoverImage = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            val key = if (pendingCoverIsNight) PreferKey.defaultCoverDark else PreferKey.defaultCover
            setCoverImageFromUri(key, uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        observeEventSticky<String>(EventBus.WEB_SERVICE) {
            _webServiceRunning.value = WebService.isRun
            _webServiceAddress.value = WebService.hostAddress
        }
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    MyNavOverlay(
                        fragment = this@MyFragment,
                        webServiceRunning = _webServiceRunning,
                        webServiceAddress = _webServiceAddress,
                        onHelp = { showHelp("appHelp") },
                        onNavigateToBook = { bookName, _ -> launchBookOrSearch(bookName) },
                        onBookSourceManage = { startActivity<BookSourceActivity>() },
                        onTxtTocRuleManage = { startActivity<TxtTocRuleActivity>() },
                        onReplaceManage = { startActivity<ReplaceRuleActivity>() },
                        onDictRuleManage = { startActivity<DictRuleActivity>() },
                        onBookmark = { startActivity<AllBookmarkActivity>() },
                        onWebServiceChange = { checked ->
                            if (checked) {
                                WebService.start(requireContext())
                            } else {
                                WebService.stop(requireContext())
                            }
                            putPrefBoolean(PreferKey.webService, checked)
                        },
                        onWebServiceLongClick = {
                            if (WebService.isRun) {
                                requireContext().selector(arrayListOf("复制地址", "浏览器打开")) { _, i ->
                                    when (i) {
                                        0 -> requireContext().sendToClip(WebService.hostAddress)
                                        1 -> requireContext().openUrl(WebService.hostAddress)
                                    }
                                }
                            }
                        },
                        onFileManage = { startActivity<FileManageActivity>() },
                        onExit = { activity?.finish() },
                        aboutActions = aboutActions(),
                        otherConfigActions = otherConfigActions(),
                        backupConfigActions = backupConfigActions(),
                        themeConfigActions = themeConfigActions(),
                        welcomeConfigActions = welcomeConfigActions(),
                        coverConfigActions = coverConfigActions(),
                    )
                }
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // 全屏 ComposeView 在 onCreateView 内已设置；无 XML 布局需绑定，此处空实现
    }

    /** 阅读记录/总览点书：查库跳阅读，找不到跳搜索。 */
    private fun launchBookOrSearch(bookName: String) {
        lifecycleScope.launch {
            val book = withContext(IO) { appDb.bookDao.findByName(bookName).firstOrNull() }
            if (book != null) {
                startActivityForBook(book)
            } else {
                (activity as? MainActivity)?.navigateToSearch(key = bookName)
            }
        }
    }

    // ---- 关于页回调（实现迁自 AboutComposeFragment，宿主改为本 Fragment）----

    private val aboutWaitDialog by lazy { WaitDialog(requireContext()) }

    private fun aboutActions() = AboutActions(
        onShare = {
            activity?.let {
                it.share(
                    it.getString(R.string.app_share_description),
                    it.getString(R.string.app_name)
                )
            }
        },
        onScoring = {
            activity?.openUrl("market://details?id=${requireContext().packageName}")
        },
        onContributors = { activity?.openUrl(getString(R.string.contributors_url)) },
        onUpdateLog = { showMdFile(getString(R.string.update_log), "updateLog.md") },
        onCheckUpdate = { checkUpdate() },
        onCrashLog = { showCrashLogSheet() },
        onSaveLog = { saveLog() },
        onCreateHeapDump = { createHeapDump() },
        onPrivacyPolicy = { showMdFile(getString(R.string.privacy_policy), "privacyPolicy.md") },
        onLicense = { showMdFile(getString(R.string.license), "LICENSE.md") },
        onDisclaimer = { showMdFile(getString(R.string.disclaimer), "disclaimer.md") },
    )

    private fun showMdFile(title: String, fileName: String) {
        val mdText = String(requireContext().assets.open(fileName).readBytes())
        showMarkdownSheet(title, mdText)
    }

    private fun checkUpdate() {
        aboutWaitDialog.show()
        AppUpdate.gitHubUpdate?.run {
            check(lifecycleScope)
                .onSuccess {
                    showDialogFragment(UpdateDialog(it))
                }.onError {
                    appCtx.toastOnUi("${getString(R.string.check_update)}\n${it.localizedMessage}")
                }.onFinally {
                    aboutWaitDialog.dismiss()
                }
        }
    }

    private fun saveLog() {
        Coroutine.async {
            val backupPath = AppConfig.backupPath ?: let {
                appCtx.toastOnUi("未设置备份目录")
                return@async
            }
            if (!AppConfig.recordLog) {
                appCtx.toastOnUi("未开启日志记录，请去其他设置里打开记录日志")
                delay(3000)
            }
            val doc = FileDoc.fromUri(Uri.parse(backupPath), true)
            copyLogs(doc)
            copyHeapDump(doc)
            appCtx.toastOnUi("已保存至备份目录")
        }.onError {
            AppLog.put("保存日志出错\n${it.localizedMessage}", it, true)
        }
    }

    private fun createHeapDump() {
        Coroutine.async {
            val backupPath = AppConfig.backupPath ?: let {
                appCtx.toastOnUi("未设置备份目录")
                return@async
            }
            if (!AppConfig.recordHeapDump) {
                appCtx.toastOnUi("未开启堆转储记录，请去其他设置里打开记录堆转储")
                delay(3000)
            }
            appCtx.toastOnUi("开始创建堆转储")
            System.gc()
            CrashHandler.doHeapDump(true)
            val doc = FileDoc.fromUri(Uri.parse(backupPath), true)
            if (!copyHeapDump(doc)) {
                appCtx.toastOnUi("未找到堆转储文件")
            } else {
                appCtx.toastOnUi("已保存至备份目录")
            }
        }.onError {
            AppLog.put("保存堆转储失败\n${it.localizedMessage}", it)
        }
    }

    private fun copyLogs(doc: FileDoc) {
        val cacheDir = appCtx.externalCache
        val logFiles = File(cacheDir, "logs")
        val crashFiles = File(cacheDir, "crash")
        val logcatFile = File(cacheDir, "logcat.txt")
        dumpLogcat(logcatFile)
        val zipFile = File(cacheDir, "logs.zip")
        ZipUtils.zipFiles(arrayListOf(logFiles, crashFiles, logcatFile), zipFile)
        doc.find("logs.zip")?.delete()
        zipFile.inputStream().use { input ->
            doc.createFileIfNotExist("logs.zip").openOutputStream().getOrNull()
                ?.use { input.copyTo(it) }
        }
        zipFile.delete()
    }

    private fun copyHeapDump(doc: FileDoc): Boolean {
        val heapFile = FileDoc.fromFile(File(appCtx.externalCache, "heapDump")).list()
            ?.firstOrNull() ?: return false
        doc.find("heapDump")?.delete()
        val heapDumpDoc = doc.createFolderIfNotExist("heapDump")
        heapFile.openInputStream().getOrNull()?.use { input ->
            heapDumpDoc.createFileIfNotExist(heapFile.name).openOutputStream().getOrNull()
                ?.use { input.copyTo(it) }
        }
        return true
    }

    private fun dumpLogcat(file: File) {
        try {
            val process = Runtime.getRuntime().exec("logcat -d")
            file.outputStream().use {
                process.inputStream.copyTo(it)
            }
        } catch (e: Exception) {
            AppLog.put("保存Logcat失败\n$e", e)
        }
    }

    // ---- 其他设置回调（迁自 OtherConfigComposeFragment） ----

    private fun otherConfigActions() = OtherConfigActions(
        onCheckSource = { showDialogFragment<CheckSourceConfig>() },
        onUploadRule = { showDialogFragment<DirectLinkUploadConfig>() },
    )

    // ---- 备份回调（迁自 BackupConfigComposeFragment） ----

    private fun backupConfigActions() = BackupConfigActions(
        onBackupPath = { selectBackupPath.launch {} },
        onRestoreIgnore = { backupIgnore() },
        onImportOld = { restoreOld.launch {} },
        onLocalRestore = {
            restoreDoc.launch {
                title = getString(R.string.select_restore_file)
                mode = HandleFileContract.FILE
                allowExtensions = arrayOf("zip")
            }
        },
        onWebDavRestore = { webDavRestore() },
        onHelp = { showHelp("webDavHelp") },
        onLog = { showLogSheet() },
    )

    private fun backupIgnore() {
        val checkedItems = BooleanArray(BackupConfig.ignoreKeys.size) {
            BackupConfig.ignoreConfig[BackupConfig.ignoreKeys[it]] ?: false
        }
        alert(R.string.restore_ignore) {
            multiChoiceItems(BackupConfig.ignoreTitle, checkedItems) { _, which, isChecked ->
                BackupConfig.ignoreConfig[BackupConfig.ignoreKeys[which]] = isChecked
            }
            onDismiss {
                BackupConfig.saveIgnoreConfig()
            }
        }
    }

    private fun webDavRestore() {
        backupWaitDialog.setText(R.string.loading)
        backupWaitDialog.setOnCancelListener {
            restoreJob?.cancel()
        }
        backupWaitDialog.show()
        Coroutine.async {
            restoreJob = coroutineContext[Job]
            showRestoreDialog(requireContext())
        }.onError {
            AppLog.put("WebDav恢复出错\n${it.localizedMessage}", it)
            if (context == null) return@onError
            appCtx.toastOnUi("WebDav恢复出错\n${it.localizedMessage}")
        }.onFinally {
            backupWaitDialog.dismiss()
        }
    }

    private suspend fun showRestoreDialog(context: android.content.Context) {
        val names = withContext(IO) { AppWebDav.getBackupNames() }
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
                        view?.post { restoreWebDav(names[index]) }
                    }
                }
            }
        } else {
            throw NoStackTraceException("Web dav no back up file")
        }
    }

    private fun restoreWebDav(name: String) {
        backupWaitDialog.setText("恢复中…")
        backupWaitDialog.show()
        val task = Coroutine.async {
            AppWebDav.restoreWebDav(name)
        }.onError {
            AppLog.put("WebDav恢复出错\n${it.localizedMessage}", it)
            appCtx.toastOnUi("WebDav恢复出错\n${it.localizedMessage}")
        }.onFinally {
            backupWaitDialog.dismiss()
        }
        backupWaitDialog.setOnCancelListener {
            task.cancel()
        }
    }

    // ---- 主题设置回调（迁自 ThemeConfigComposeFragment） ----

    private fun themeConfigActions() = ThemeConfigActions(
        onRequestColorPicker = { title, currentColor, onChange ->
            pendingColorCallback = onChange
            currentDialogTag = "color_$title"
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
            dialog.setColorPickerDialogListener(this@MyFragment)
            childFragmentManager
                .beginTransaction()
                .add(dialog, currentDialogTag!!)
                .commitAllowingStateLoss()
        },
        onThemeList = {
            ThemeListDialog().show(childFragmentManager, "themeList")
        },
        onBgImage = { isNight ->
            pendingBgIsNight = isNight
            pendingBgChange = {
                ThemeConfig.applyTheme(requireContext())
            }
            selectBgAction(isNight)
        },
        onThemeModeToggle = {
            AppConfig.isNightTheme = !AppConfig.isNightTheme
            ThemeConfig.applyDayNight(requireContext())
        },
    )

    private fun selectBgAction(isNight: Boolean) {
        val bgKey = if (isNight) PreferKey.bgImageN else PreferKey.bgImage
        val blurringKey = if (isNight) PreferKey.bgImageNBlurring else PreferKey.bgImageBlurring
        val actions = arrayListOf(
            getString(R.string.background_image_blurring),
            getString(R.string.select_image),
        )
        if (!getPrefString(bgKey).isNullOrEmpty()) {
            actions.add(getString(R.string.delete))
        }
        context?.selector(items = actions) { _, i ->
            when (i) {
                0 -> alertImageBlurring(blurringKey) {
                    upTheme(isNight)
                }
                1 -> {
                    selectBgImage.launch {
                        this.requestCode = if (isNight) 122 else 121
                        this.mode = HandleFileContract.IMAGE
                    }
                }
                2 -> {
                    removePref(bgKey)
                    upTheme(isNight)
                }
            }
        }
    }

    private fun alertImageBlurring(preferKey: String, success: () -> Unit) {
        alert(R.string.background_image_blurring) {
            val alertBinding = DialogImageBlurringBinding.inflate(layoutInflater).apply {
                getPrefInt(preferKey, 0).let {
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
                    putPrefInt(preferKey, it)
                    success.invoke()
                }
            }
            cancelButton()
        }
    }

    private fun upTheme(isNightTheme: Boolean) {
        if (AppConfig.isNightTheme == isNightTheme) {
            ThemeConfig.applyDayNight(requireContext())
        }
    }

    private fun setBgFromUri(uri: android.net.Uri, preferenceKey: String, success: () -> Unit) {
        readUri(uri) { fileDoc, inputStream ->
            kotlin.runCatching {
                var file = requireContext().externalFiles
                val suffix = fileDoc.name.substringAfterLast(".")
                val fileName = uri.inputStream(requireContext()).getOrThrow().use {
                    MD5Utils.md5Encode(it) + ".$suffix"
                }
                file = FileUtils.createFileIfNotExist(file, preferenceKey, fileName)
                FileOutputStream(file).use {
                    inputStream.copyTo(it)
                }
                putPrefString(preferenceKey, file.absolutePath)
                success()
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }

    // ---- ColorPickerDialogListener ----

    override fun onResume() {
        super.onResume()
        currentDialogTag?.let { tag ->
            childFragmentManager.findFragmentByTag(tag)?.let { f ->
                if (f is ColorPickerDialog) {
                    f.setColorPickerDialogListener(this@MyFragment)
                    if (pendingColorCallback == null) {
                        f.dismissAllowingStateLoss()
                    }
                }
            }
        }
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        val callback = pendingColorCallback
        pendingColorCallback = null
        currentDialogTag?.let { tag ->
            childFragmentManager.findFragmentByTag(tag)?.let { f ->
                if (f is ColorPickerDialog) {
                    f.dismissAllowingStateLoss()
                }
            }
        }
        callback?.invoke(Color(color))
    }

    override fun onDialogDismissed(dialogId: Int) {
        pendingColorCallback = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        backupWaitDialog.dismiss()
    }

    // ---- 启动界面样式回调（迁自 WelcomeConfigComposeFragment） ----

    private fun welcomeConfigActions() = WelcomeConfigActions(
        onWelcomeImage = { isNight ->
            pendingWelcomeIsNight = isNight
            val key = if (isNight) PreferKey.welcomeImageDark else PreferKey.welcomeImage
            if (getPrefString(key).isNullOrEmpty()) {
                launchWelcomeImagePicker(isNight)
            } else {
                context?.selector(
                    items = arrayListOf(
                        getString(R.string.delete),
                        getString(R.string.select_image),
                    )
                ) { _, i ->
                    if (i == 0) {
                        removePref(key)
                        if (isNight) {
                            AppConfig.welcomeShowTextDark = true
                            AppConfig.welcomeShowIconDark = true
                        } else {
                            AppConfig.welcomeShowText = true
                            AppConfig.welcomeShowIcon = true
                        }
                        BookCover.upDefaultCover()
                    } else {
                        launchWelcomeImagePicker(isNight)
                    }
                }
            }
        },
    )

    private fun launchWelcomeImagePicker(isNight: Boolean) {
        pendingWelcomeIsNight = isNight
        selectWelcomeImage.launch {
            requestCode = if (isNight) 222 else 221
            mode = HandleFileContract.IMAGE
        }
    }

    private fun setWelcomeImageFromUri(preferenceKey: String, uri: Uri) {
        readUri(uri) { fileDoc, inputStream ->
            kotlin.runCatching {
                var file = requireContext().externalFiles
                val suffix = fileDoc.name.substringAfterLast(".")
                val fileName = uri.inputStream(requireContext()).getOrThrow().use {
                    MD5Utils.md5Encode(it) + ".$suffix"
                }
                file = FileUtils.createFileIfNotExist(file, "covers", fileName)
                FileOutputStream(file).use {
                    inputStream.copyTo(it)
                }
                putPrefString(preferenceKey, file.absolutePath)
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }

    // ---- 封面配置回调（迁自 CoverConfigComposeFragment） ----

    private fun coverConfigActions() = CoverConfigActions(
        onCoverRule = {
            showDialogFragment<io.legado.app.ui.config.CoverRuleConfigDialog>()
        },
        onDefaultCover = { isNight ->
            pendingCoverIsNight = isNight
            val key = if (isNight) PreferKey.defaultCoverDark else PreferKey.defaultCover
            if (getPrefString(key).isNullOrEmpty()) {
                launchCoverImagePicker(isNight)
            } else {
                context?.selector(
                    items = arrayListOf(
                        getString(R.string.delete),
                        getString(R.string.select_image),
                    )
                ) { _, i ->
                    if (i == 0) {
                        removePref(key)
                        BookCover.upDefaultCover()
                    } else {
                        launchCoverImagePicker(isNight)
                    }
                }
            }
        },
    )

    private fun launchCoverImagePicker(isNight: Boolean) {
        pendingCoverIsNight = isNight
        selectCoverImage.launch {
            requestCode = if (isNight) 112 else 111
            mode = HandleFileContract.IMAGE
        }
    }

    private fun setCoverImageFromUri(preferenceKey: String, uri: Uri) {
        readUri(uri) { fileDoc, inputStream ->
            kotlin.runCatching {
                var file = requireContext().externalFiles
                val suffix = fileDoc.name.substringAfterLast(".")
                val fileName = uri.inputStream(requireContext()).getOrThrow().use {
                    MD5Utils.md5Encode(it) + ".$suffix"
                }
                file = FileUtils.createFileIfNotExist(file, "covers", fileName)
                FileOutputStream(file).use {
                    inputStream.copyTo(it)
                }
                putPrefString(preferenceKey, file.absolutePath)
                BookCover.upDefaultCover()
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }
}
