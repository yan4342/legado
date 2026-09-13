package io.legado.app.ui.config

import android.app.Application
import android.content.Context
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.storage.Backup
import io.legado.app.utils.FileUtils
import io.legado.app.utils.restart
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.delay
import splitties.init.appCtx

class ConfigViewModel(application: Application) : BaseViewModel(application) {

    fun upWebDavConfig() {
        execute {
            AppWebDav.upConfig()
        }
    }

    fun testWebDav() {
        execute {
            AppWebDav.testConnection()
                .onSuccess { context.toastOnUi(R.string.web_dav_test_success) }
                .onFailure { context.toastOnUi("WebDav连接失败: ${it.localizedMessage}") }
        }
    }

    fun backupWebDav() {
        execute {
            if (!AppWebDav.isOk) {
                context.toastOnUi(R.string.web_dav_not_configured)
                return@execute
            }
            kotlin.runCatching {
                Backup.backupWebDavLocked(context)
            }.onSuccess {
                context.toastOnUi(R.string.backup_success)
            }.onFailure {
                context.toastOnUi("WebDav备份失败: ${it.localizedMessage}")
                AppLog.put("WebDav备份失败: ${it.localizedMessage}", it)
            }
        }
    }

    fun restoreWebDav() {
        execute {
            context.toastOnUi(R.string.restore_summary)
        }
    }

    fun backupLocal() {
        execute {
            val backupPath = AppConfig.backupPath
            if (backupPath.isNullOrBlank()) {
                context.toastOnUi(R.string.select_backup_path)
                return@execute
            }
            kotlin.runCatching {
                Backup.backupLocalLocked(context, backupPath)
            }.onSuccess {
                context.toastOnUi(R.string.backup_success)
            }.onFailure {
                context.toastOnUi("本地备份失败: ${it.localizedMessage}")
                AppLog.put("本地备份失败: ${it.localizedMessage}", it)
            }
        }
    }

    fun restoreLocal() {
        execute {
            context.toastOnUi(R.string.restore_summary)
        }
    }

    fun clearCache() {
        execute {
            BookHelp.clearCache()
            FileUtils.delete(context.cacheDir.absolutePath)
        }.onSuccess {
            context.toastOnUi(R.string.clear_cache_success)
        }
    }

    fun clearWebViewData() {
        execute {
            FileUtils.delete(context.getDir("webview", Context.MODE_PRIVATE))
            FileUtils.delete(context.getDir("hws_webview", Context.MODE_PRIVATE), true)
            context.toastOnUi(R.string.clear_webview_data_success)
            delay(3000)
            appCtx.restart()
        }
    }

    fun shrinkDatabase() {
        execute {
            appDb.openHelper.writableDatabase.execSQL("VACUUM")
        }.onSuccess {
            context.toastOnUi(R.string.success)
        }
    }

}
