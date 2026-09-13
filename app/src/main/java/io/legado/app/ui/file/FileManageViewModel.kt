package io.legado.app.ui.file

import android.app.Application
import androidx.core.content.FileProvider
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.utils.toastOnUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

class FileManageViewModel(application: Application) : BaseViewModel(application) {

    val rootDoc: File? = context.getExternalFilesDir(null)?.parentFile
    private val subDocs = mutableListOf<File>()

    private val _uiState = MutableStateFlow(FileManageUiState())
    val uiState: StateFlow<FileManageUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<FileManageEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        upFiles(rootDoc)
    }

    fun onIntent(intent: FileManageIntent) {
        when (intent) {
            is FileManageIntent.NavigateTo -> navigateTo(intent.file)
            FileManageIntent.UpOneLevel -> upOneLevel()
            is FileManageIntent.NavigateBreadcrumb -> navigateBreadcrumb(intent.index)
            is FileManageIntent.DeleteFile -> delFile(intent.file)
            is FileManageIntent.Search -> _uiState.update { it.copy(searchQuery = intent.query) }
        }
    }

    private fun navigateTo(file: File) {
        if (!file.isDirectory) {
            openFile(file)
        } else if (file == subDocs.lastOrNull()) {
            upOneLevel()
        } else {
            subDocs.add(file)
            upFiles(file)
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

    private fun openFile(file: File) {
        runCatching {
            FileProvider.getUriForFile(context, AppConst.authority, file)
        }.onSuccess { uri ->
            _effects.tryEmit(FileManageEffect.OpenFile(uri))
        }.onFailure {
            _effects.tryEmit(FileManageEffect.ShowToast(it.localizedMessage ?: "打开失败"))
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
                    searchQuery = "",
                )
            }
        }.onError {
            _effects.tryEmit(FileManageEffect.ShowToast(it.localizedMessage ?: "加载失败"))
        }
    }

    private fun delFile(file: File) {
        execute {
            file.delete()
        }.onSuccess {
            upFiles(subDocs.lastOrNull() ?: rootDoc)
        }.onError {
            _effects.tryEmit(FileManageEffect.ShowToast(it.localizedMessage ?: "删除失败"))
        }
    }
}
