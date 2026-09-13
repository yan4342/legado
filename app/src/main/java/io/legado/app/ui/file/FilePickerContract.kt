package io.legado.app.ui.file

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.io.File

@Stable
data class FilePickerUiState(
    /** 子目录栈（面包屑，不含 root） */
    val pathDirs: ImmutableList<File> = persistentListOf(),
    /** 当前目录下的文件/文件夹列表，非 root 时首项为".." */
    val files: ImmutableList<File> = persistentListOf(),
    val parentFile: File? = null,
    /** FILE 模式下高亮选中的文件 */
    val selectedFile: File? = null,
    /** true 为选文件夹模式，false 为选文件模式 */
    val isSelectDir: Boolean = false,
    /** 允许选择的文件扩展名（空则全部允许） */
    val allowExtensions: ImmutableList<String> = persistentListOf(),
)

sealed interface FilePickerIntent {
    data class NavigateTo(val file: File) : FilePickerIntent
    data object UpOneLevel : FilePickerIntent
    data class NavigateBreadcrumb(val index: Int) : FilePickerIntent
    data class SelectFile(val file: File) : FilePickerIntent
    data object Confirm : FilePickerIntent
    data class CreateFolder(val name: String) : FilePickerIntent
}

sealed interface FilePickerEffect {
    data class Confirm(val path: String) : FilePickerEffect
    data class ShowToast(val message: String) : FilePickerEffect
}
