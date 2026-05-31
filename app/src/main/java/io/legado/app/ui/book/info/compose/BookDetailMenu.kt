package io.legado.app.ui.book.info.compose

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.legado.app.R

/**
 * 详情页更多操作菜单 — 完整复用原 book_info.xml 中的所有菜单项。
 */
@Composable
fun BookDetailMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (Int) -> Unit,
    canUpdate: Boolean = true,
    splitLongChapter: Boolean = true,
    isLoginVisible: Boolean = false,
    isSourceVariableVisible: Boolean = false,
    isBookVariableVisible: Boolean = false,
    isLocalTxt: Boolean = false,
    isLocal: Boolean = false,
    deleteAlert: Boolean = true,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        // 编辑
        DropdownMenuItem(
            text = { Text(stringResource(R.string.edit)) },
            onClick = { onDismiss(); onAction(MENU_EDIT) },
        )
        // 分享
        DropdownMenuItem(
            text = { Text(stringResource(R.string.share)) },
            onClick = { onDismiss(); onAction(MENU_SHARE) },
        )
        // 换源
        DropdownMenuItem(
            text = { Text(stringResource(R.string.change_origin)) },
            onClick = { onDismiss(); onAction(MENU_CHANGE_SOURCE) },
        )
        // 刷新
        DropdownMenuItem(
            text = { Text(stringResource(R.string.refresh)) },
            onClick = { onDismiss(); onAction(MENU_REFRESH) },
        )
        // 置顶
        DropdownMenuItem(
            text = { Text(stringResource(R.string.to_top)) },
            onClick = { onDismiss(); onAction(MENU_TOP) },
        )
        // 登录（条件显示）
        if (isLoginVisible) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.login)) },
                onClick = { onDismiss(); onAction(MENU_LOGIN) },
            )
        }
        // 源变量（条件显示）
        if (isSourceVariableVisible) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.set_source_variable)) },
                onClick = { onDismiss(); onAction(MENU_SET_SOURCE_VARIABLE) },
            )
        }
        // 书籍变量（条件显示）
        if (isBookVariableVisible) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.set_book_variable)) },
                onClick = { onDismiss(); onAction(MENU_SET_BOOK_VARIABLE) },
            )
        }
        // 允许更新（条件显示）
        if (!isLocalTxt) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.allow_update)) },
                onClick = { onDismiss(); onAction(MENU_CAN_UPDATE) },
            )
        }
        // 拆分长章节（仅 TXT 本地书）
        if (isLocalTxt) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.split_long_chapter)) },
                onClick = { onDismiss(); onAction(MENU_SPLIT_LONG_CHAPTER) },
            )
        }
        // 上传到远程（仅本地书）
        if (isLocal) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.upload_to_remote)) },
                onClick = { onDismiss(); onAction(MENU_UPLOAD) },
            )
        }
        // 清除缓存
        DropdownMenuItem(
            text = { Text(stringResource(R.string.clear_cache)) },
            onClick = { onDismiss(); onAction(MENU_CLEAR_CACHE) },
        )
        // 删除
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete)) },
            onClick = { onDismiss(); onAction(MENU_DELETE) },
        )
    }
}

const val MENU_EDIT = 0
const val MENU_SHARE = 1
const val MENU_CHANGE_SOURCE = 2
const val MENU_REFRESH = 3
const val MENU_TOP = 4
const val MENU_LOGIN = 5
const val MENU_SET_SOURCE_VARIABLE = 6
const val MENU_SET_BOOK_VARIABLE = 7
const val MENU_CAN_UPDATE = 8
const val MENU_SPLIT_LONG_CHAPTER = 9
const val MENU_UPLOAD = 10
const val MENU_CLEAR_CACHE = 11
const val MENU_DELETE = 12
