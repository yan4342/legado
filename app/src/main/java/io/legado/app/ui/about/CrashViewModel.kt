package io.legado.app.ui.about

import android.app.Application
import android.net.Uri
import io.legado.app.base.BaseViewModel
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.delete
import io.legado.app.utils.find
import io.legado.app.utils.getFile
import io.legado.app.utils.list
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.FileFilter

class CrashViewModel(application: Application) : BaseViewModel(application) {

    private val _logList = MutableStateFlow<List<FileDoc>>(emptyList())
    val logList: StateFlow<List<FileDoc>> = _logList.asStateFlow()

    private val _fileContent = MutableStateFlow<String?>(null)
    val fileContent: StateFlow<String?> = _fileContent.asStateFlow()

    var currentFileName: String? = null
        private set

    fun initData() {
        execute {
            val list = arrayListOf<FileDoc>()
            context.externalCacheDir
                ?.getFile("crash")
                ?.listFiles(FileFilter { it.isFile })
                ?.forEach {
                    list.add(FileDoc.fromFile(it))
                }
            val backupPath = AppConfig.backupPath
            if (!backupPath.isNullOrEmpty()) {
                val uri = Uri.parse(backupPath)
                FileDoc.fromUri(uri, true)
                    .find("crash")
                    ?.list {
                        !it.isDir
                    }?.let {
                        list.addAll(it)
                    }
            }
            return@execute list.sortedByDescending { it.name }.distinctBy { it.name }
        }.onSuccess {
            _logList.value = it
        }
    }

    fun readFileContent(fileDoc: FileDoc) {
        currentFileName = fileDoc.name
        execute {
            String(fileDoc.readBytes())
        }.onSuccess {
            _fileContent.value = it
        }.onError {
            context.toastOnUi(it.localizedMessage)
        }
    }

    fun clearFileContent() {
        _fileContent.value = null
        currentFileName = null
    }

    fun clearCrashLog() {
        execute {
            context.externalCacheDir
                ?.getFile("crash")
                ?.let {
                    FileUtils.delete(it, false)
                }
            val backupPath = AppConfig.backupPath
            if (!backupPath.isNullOrEmpty()) {
                val uri = Uri.parse(backupPath)
                FileDoc.fromUri(uri, true)
                    .find("crash")
                    ?.delete()
            }
        }.onError {
            context.toastOnUi(it.localizedMessage)
        }.onFinally {
            initData()
        }
    }
}
