package io.legado.app.ui.file

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.io.File

@Stable
data class FileManageUiState(
    /** 子目录栈（面包屑，不含 root） */
    val pathDirs: ImmutableList<File> = persistentListOf(),
    /** 当前目录下的文件/文件夹列表，非 root 时首项为".." */
    val files: ImmutableList<File> = persistentListOf(),
    val searchQuery: String = "",
    val parentFile: File? = null,
)

sealed interface FileManageIntent {
    data class NavigateTo(val file: File) : FileManageIntent
    data object UpOneLevel : FileManageIntent
    data class NavigateBreadcrumb(val index: Int) : FileManageIntent
    data class DeleteFile(val file: File) : FileManageIntent
    data class Search(val query: String) : FileManageIntent
}

sealed interface FileManageEffect {
    data class OpenFile(val uri: android.net.Uri) : FileManageEffect
    data class ShowToast(val message: String) : FileManageEffect
}
