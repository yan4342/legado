package io.legado.app.ui.file

import android.app.Application
import android.os.Bundle
import android.os.Environment
import io.legado.app.base.BaseViewModel
import io.legado.app.exception.NoStackTraceException
import io.legado.app.utils.FileUtils
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

class FilePickerViewModel(application: Application) : BaseViewModel(application) {

    var rootDoc: File? = Environment.getExternalStorageDirectory()
    private val subDocs = mutableListOf<File>()
    var mode: Int = HandleFileContract.FILE
    var isShowHideDir: Boolean = false
    var allowExtensions: Array<String>? = null

    val isSelectDir: Boolean get() = mode == HandleFileContract.DIR
    val isSelectFile: Boolean get() = mode == HandleFileContract.FILE
    val lastDir: File? get() = subDocs.lastOrNull() ?: rootDoc

    private val _uiState = MutableStateFlow(FilePickerUiState())
    val uiState: StateFlow<FilePickerUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<FilePickerEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    fun initData(arguments: Bundle?) {
        arguments?.let {
            mode = it.getInt("mode", HandleFileContract.FILE)
            isShowHideDir = it.getBoolean("isShowHideDir")
            allowExtensions = it.getStringArray("allowExtensions")
            _uiState.update { state ->
                state.copy(
                    isSelectDir = mode == HandleFileContract.DIR,
                    allowExtensions = (allowExtensions ?: emptyArray()).toImmutableList(),
                )
            }
            it.getString("initPath")?.let { path ->
                rootDoc = File(path)
            }
        }
        upFiles(rootDoc)
    }

    fun onIntent(intent: FilePickerIntent) {
        when (intent) {
            is FilePickerIntent.NavigateTo -> navigateTo(intent.file)
            FilePickerIntent.UpOneLevel -> upOneLevel()
            is FilePickerIntent.NavigateBreadcrumb -> navigateBreadcrumb(intent.index)
            is FilePickerIntent.SelectFile -> selectFile(intent.file)
            FilePickerIntent.Confirm -> confirm()
            is FilePickerIntent.CreateFolder -> createFolder(intent.name)
        }
    }

    private fun navigateTo(file: File) {
        when {
            file == subDocs.lastOrNull() -> upOneLevel()
            file.isDirectory -> {
                subDocs.add(file)
                upFiles(file)
            }

            isSelectFile && isAllowed(file) ->
                _uiState.update { it.copy(selectedFile = file) }
        }
    }

    private fun upOneLevel() {
        if (subDocs.isNotEmpty()) {
            subDocs.removeLastOrNull()
            upFiles(subDocs.lastOrNull() ?: rootDoc)
        }
    }

    private fun navigateBreadcrumb(index: Int) {
        if (index < subDocs.size) {
            subDocs.subList(index, subDocs.size).clear()
            upFiles(subDocs.lastOrNull() ?: rootDoc)
        }
    }

    private fun selectFile(file: File) {
        if (isSelectFile && isAllowed(file)) {
            _uiState.update { it.copy(selectedFile = file) }
        }
    }

    private fun isAllowed(file: File): Boolean {
        val extensions = allowExtensions
        return extensions.isNullOrEmpty() || extensions.contains(FileUtils.getExtension(file.path))
    }

    private fun confirm() {
        if (isSelectDir) {
            lastDir?.let { _effects.tryEmit(FilePickerEffect.Confirm(it.path)) }
        } else {
            val selected = _uiState.value.selectedFile
            if (selected != null) {
                _effects.tryEmit(FilePickerEffect.Confirm(selected.path))
            } else {
                _effects.tryEmit(FilePickerEffect.ShowToast("请选择文件"))
            }
        }
    }

    private fun upFiles(parentFile: File?) {
        execute {
            parentFile ?: return@execute emptyList<File>()
            if (parentFile == rootDoc) {
                parentFile.listFiles()?.sortedWith(
                    compareBy({ it.isFile }, { it.name })
                )
            } else {
                val list = arrayListOf<File>(parentFile)
                parentFile.listFiles()?.sortedWith(
                    compareBy({ it.isFile }, { it.name })
                )?.let {
                    list.addAll(it)
                }
                list
            }
        }.onStart {
            _uiState.update { it.copy(files = persistentListOf()) }
        }.onSuccess { list ->
            _uiState.update {
                it.copy(
                    pathDirs = subDocs.toImmutableList(),
                    parentFile = parentFile,
                    files = (list ?: emptyList()).toImmutableList(),
                    selectedFile = null,
                )
            }
        }.onError {
            _effects.tryEmit(FilePickerEffect.ShowToast(it.localizedMessage ?: "加载失败"))
        }
    }

    private fun createFolder(name: String) {
        execute {
            val dir = lastDir ?: throw NoStackTraceException("父文件夹不存在")
            val folder = File(dir, name)
            if (!folder.canonicalPath.contains(dir.canonicalPath)) {
                throw NoStackTraceException("非法文件名")
            }
            folder.mkdir()
        }.onSuccess {
            upFiles(lastDir)
        }.onError {
            _effects.tryEmit(FilePickerEffect.ShowToast(it.localizedMessage ?: "创建失败"))
        }
    }
}
