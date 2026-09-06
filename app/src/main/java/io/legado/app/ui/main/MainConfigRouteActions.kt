package io.legado.app.ui.main

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.help.storage.ImportOldData
import io.legado.app.help.storage.Restore
import io.legado.app.ui.common.compose.LegadoWaitState
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.showMarkdownSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

/**
 * 配置域路由（主题 / 封面 / 欢迎 / 备份 / 关于等）的胶水状态与 Action，
 * 自已删除的 MyFragment 迁出后先内联在 MainNavHost，Phase 1 收拢至此。
 *
 * - launcher 由 [rememberMainConfigRouteActions] 用 rememberLauncherForActivityResult 创建；
 * - 可变状态用 mutableStateOf，回调 / Job 暴露为 var 属性；
 * - [showMdFile] 为成员函数，供 About 等条目复用。
 *
 * 回调逻辑与迁移前逐行一致，请勿在此改动行为。
 */
internal class MainConfigRouteActions(
    private val context: Context,
    val scope: CoroutineScope,
) {

    val appCompatActivity: AppCompatActivity? = context as? AppCompatActivity

    // --- Pending one-shot callbacks / flags (migrated from deleted MyFragment) ---
    var pendingColorCallback: ((Color) -> Unit)? by mutableStateOf<((Color) -> Unit)?>(null)
    var pendingBgIsNight: Boolean by mutableStateOf(false)
    var pendingBgChange: (() -> Unit)? by mutableStateOf<(() -> Unit)?>(null)
    var pendingWelcomeIsNight: Boolean by mutableStateOf(false)
    var pendingCoverIsNight: Boolean by mutableStateOf(false)
    var restoreJob: Job? by mutableStateOf<Job?>(null)
    var backupPath: String by mutableStateOf(AppConfig.backupPath ?: "")

    val backupWaitDialog = LegadoWaitState()

    val colorPickerListener = object : ColorPickerDialogListener {
        override fun onColorSelected(dialogId: Int, color: Int) {
            pendingColorCallback?.invoke(Color(color))
            pendingColorCallback = null
        }

        override fun onDialogDismissed(dialogId: Int) {
            pendingColorCallback = null
        }
    }

    lateinit var selectBgImage: ActivityResultLauncher<(HandleFileContract.HandleFileParam.() -> Unit)?>
    lateinit var selectWelcomeImage: ActivityResultLauncher<(HandleFileContract.HandleFileParam.() -> Unit)?>
    lateinit var selectCoverImage: ActivityResultLauncher<(HandleFileContract.HandleFileParam.() -> Unit)?>
    lateinit var selectBackupPath: ActivityResultLauncher<(HandleFileContract.HandleFileParam.() -> Unit)?>
    lateinit var restoreDoc: ActivityResultLauncher<(HandleFileContract.HandleFileParam.() -> Unit)?>
    lateinit var restoreOld: ActivityResultLauncher<(HandleFileContract.HandleFileParam.() -> Unit)?>

    // Helper: read markdown from assets and show sheet
    fun showMdFile(title: String, fileName: String) {
        scope.launch {
            val md = withContext(Dispatchers.IO) {
                runCatching { context.assets.open(fileName).bufferedReader().readText() }.getOrNull() ?: ""
            }
            (context as? FragmentActivity)?.showMarkdownSheet(title, md)
        }
    }
}

@Composable
internal fun rememberMainConfigRouteActions(): MainConfigRouteActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actions = remember { MainConfigRouteActions(context, scope) }

    actions.selectBgImage = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            val bgKey = if (actions.pendingBgIsNight) PreferKey.bgImageN else PreferKey.bgImage
            setImageFromUri(context, uri, bgKey) { actions.pendingBgChange?.invoke() }
        }
    }

    actions.selectWelcomeImage = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            val key = if (actions.pendingWelcomeIsNight) PreferKey.welcomeImageDark else PreferKey.welcomeImage
            setImageFromUri(context, uri, key)
        }
    }

    actions.selectCoverImage = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            val key = if (actions.pendingCoverIsNight) PreferKey.defaultCoverDark else PreferKey.defaultCover
            setCoverFromUri(context, uri, key)
        }
    }

    // --- Backup/restore launchers (migrated from deleted MyFragment) ---
    actions.selectBackupPath = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            val path = if (uri.isContentScheme()) uri.toString() else uri.path ?: return@rememberLauncherForActivityResult
            AppConfig.backupPath = path
            actions.backupPath = path
        }
    }

    actions.restoreDoc = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            actions.backupWaitDialog.show("恢复中…")
            val task = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                Restore.restore(appCtx, uri)
            }
            task.invokeOnCompletion {
                appCtx.mainLooper.run { actions.backupWaitDialog.dismiss() }
            }
            actions.backupWaitDialog.onCancel = {
                task.cancel()
            }
        }
    }

    actions.restoreOld = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            ImportOldData.importUri(appCtx, uri)
        }
    }

    return actions
}
