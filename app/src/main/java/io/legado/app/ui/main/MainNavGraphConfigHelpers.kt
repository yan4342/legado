package io.legado.app.ui.main
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.jaredrummler.android.colorpicker.ColorShape
import io.legado.app.R
import io.legado.app.base.LocalStatusBarColor
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.storage.BackupConfig
import io.legado.app.help.storage.ImportOldData
import io.legado.app.help.storage.Restore
import io.legado.app.lib.dialogs.alert
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
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerRouteScreen
import io.legado.app.ui.book.search.SearchIntent
import io.legado.app.ui.book.search.SearchScreen
import io.legado.app.ui.book.search.SearchViewModel
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.common.compose.LegadoWaitDialog
import io.legado.app.ui.common.compose.LegadoWaitState
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
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import splitties.init.appCtx

// MainNavGraph 的配置域辅助函数（Phase 5 导航拆分：自 MainNavGraph.kt 迁出，行为不变）

internal fun setImageFromUri(context: android.content.Context, uri: Uri, prefKey: String, onSuccess: (() -> Unit)? = null) {
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
            AppConfigStore.putString(prefKey, file.absolutePath)
            onSuccess?.invoke()
        }.onFailure {
            appCtx.toastOnUi(it.localizedMessage)
        }
    }
}

internal fun setCoverFromUri(context: android.content.Context, uri: Uri, prefKey: String) {
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
            AppConfigStore.putString(prefKey, file.absolutePath)
            io.legado.app.model.BookCover.upDefaultCover()
        }.onFailure {
            appCtx.toastOnUi(it.localizedMessage)
        }
    }
}

internal fun upTheme(context: android.content.Context, isNightTheme: Boolean) {
    if (AppConfig.isNightTheme == isNightTheme) {
        ThemeConfig.applyTheme(context)
        io.legado.app.utils.postEvent(io.legado.app.constant.EventBus.RECREATE, "")
    }
}

internal fun selectBgAction(
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

internal fun backupIgnore(context: android.content.Context) {
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

internal fun webDavRestore(
    context: android.content.Context,
    backupWaitDialog: LegadoWaitState,
    scope: kotlinx.coroutines.CoroutineScope,
    appCompatActivity: AppCompatActivity?,
) {
    backupWaitDialog.onCancel = { /* handled in launched coroutine */ }
    backupWaitDialog.show()
    scope.launch {
        val job = currentCoroutineContext()[Job]
        backupWaitDialog.onCancel = { job?.cancel() }
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
    backupWaitDialog: LegadoWaitState,
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

internal fun restoreWebDav(
    context: android.content.Context,
    name: String,
    backupWaitDialog: LegadoWaitState,
) {
    backupWaitDialog.show("恢复中…")
    val task = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        AppWebDav.restoreWebDav(name)
    }
    task.invokeOnCompletion {
        appCtx.mainLooper.run { backupWaitDialog.dismiss() }
    }
    backupWaitDialog.onCancel = {
        task.cancel()
    }
}


internal fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

