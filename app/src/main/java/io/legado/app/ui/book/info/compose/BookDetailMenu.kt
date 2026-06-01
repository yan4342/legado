package io.legado.app.ui.book.info.compose

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.common.compose.legadoPopupBackgroundColor
import io.legado.app.ui.common.compose.legadoPopupPrimaryTextColor

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
        modifier = Modifier.widthIn(min = 200.dp, max = 300.dp),
        containerColor = legadoPopupBackgroundColor(),
    ) {
        val textColor = legadoPopupPrimaryTextColor()
        // 分享
        DropdownMenuItem(
            text = { Text(stringResource(R.string.share), color = textColor) },
            onClick = { onDismiss(); onAction(MENU_SHARE) },
        )
        // 刷新
        DropdownMenuItem(
            text = { Text(stringResource(R.string.refresh), color = textColor) },
            onClick = { onDismiss(); onAction(MENU_REFRESH) },
        )
        // 换源
        DropdownMenuItem(
            text = { Text(stringResource(R.string.change_origin), color = textColor) },
            onClick = { onDismiss(); onAction(MENU_CHANGE_SOURCE) },
        )
        // 置顶
        DropdownMenuItem(
            text = { Text(stringResource(R.string.to_top), color = textColor) },
            onClick = { onDismiss(); onAction(MENU_TOP) },
        )
        // 登录（条件显示）
        if (isLoginVisible) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.login), color = textColor) },
                onClick = { onDismiss(); onAction(MENU_LOGIN) },
            )
        }
        // 源变量（条件显示）
        if (isSourceVariableVisible) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.set_source_variable), color = textColor) },
                onClick = { onDismiss(); onAction(MENU_SET_SOURCE_VARIABLE) },
            )
        }
        // 书籍变量（条件显示）
        if (isBookVariableVisible) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.set_book_variable), color = textColor) },
                onClick = { onDismiss(); onAction(MENU_SET_BOOK_VARIABLE) },
            )
        }
        // 复制书源 URL
        DropdownMenuItem(
            text = { Text(stringResource(R.string.copy_book_url), color = textColor) },
            onClick = { onDismiss(); onAction(MENU_COPY_BOOK_URL) },
        )
        // 复制目录 URL
        DropdownMenuItem(
            text = { Text(stringResource(R.string.copy_toc_url), color = textColor) },
            onClick = { onDismiss(); onAction(MENU_COPY_TOC_URL) },
        )
        // 允许更新
        DropdownMenuItem(
            text = { Text(stringResource(R.string.allow_update), color = textColor) },
            trailingIcon = {
                if (canUpdate) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = textColor,
                    )
                }
            },
            onClick = { onDismiss(); onAction(MENU_CAN_UPDATE) },
        )
        // 拆分长章节（仅 TXT 本地书）
        if (isLocalTxt) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.split_long_chapter), color = textColor) },
                trailingIcon = {
                    if (splitLongChapter) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = textColor,
                        )
                    }
                },
                onClick = { onDismiss(); onAction(MENU_SPLIT_LONG_CHAPTER) },
            )
        }
        // 上传到远程（仅本地书）
        if (isLocal) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.upload_to_remote), color = textColor) },
                onClick = { onDismiss(); onAction(MENU_UPLOAD) },
            )
        }
        // 清除缓存
        DropdownMenuItem(
            text = { Text(stringResource(R.string.clear_cache), color = textColor) },
            onClick = { onDismiss(); onAction(MENU_CLEAR_CACHE) },
        )
        // 日志
        DropdownMenuItem(
            text = { Text(stringResource(R.string.log), color = textColor) },
            onClick = { onDismiss(); onAction(MENU_LOG) },
        )
        // 删除
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete), color = textColor) },
            onClick = { onDismiss(); onAction(MENU_DELETE) },
        )
    }
}

const val MENU_EDIT = 0
const val MENU_SHARE = 1
const val MENU_REFRESH = 2
const val MENU_CHANGE_SOURCE = 3
const val MENU_TOP = 4
const val MENU_LOGIN = 5
const val MENU_SET_SOURCE_VARIABLE = 6
const val MENU_SET_BOOK_VARIABLE = 7
const val MENU_COPY_BOOK_URL = 8
const val MENU_COPY_TOC_URL = 9
const val MENU_CAN_UPDATE = 10
const val MENU_SPLIT_LONG_CHAPTER = 11
const val MENU_UPLOAD = 12
const val MENU_CLEAR_CACHE = 13
const val MENU_LOG = 14
const val MENU_DELETE = 15
