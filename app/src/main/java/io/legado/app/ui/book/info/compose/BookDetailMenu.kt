package io.legado.app.ui.book.info.compose

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.ui.common.compose.RoundDropdownMenu
import io.legado.app.ui.common.compose.RoundDropdownMenuItem

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
    RoundDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) { dismiss ->
        RoundDropdownMenuItem(
            text = stringResource(R.string.share),
            onClick = { dismiss(); onAction(MENU_SHARE) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.refresh),
            onClick = { dismiss(); onAction(MENU_REFRESH) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.view_source),
            onClick = { dismiss(); onAction(MENU_CHANGE_SOURCE) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.group),
            onClick = { dismiss(); onAction(MENU_GROUP) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.to_top),
            onClick = { dismiss(); onAction(MENU_TOP) },
        )
        if (isLoginVisible) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.login),
                onClick = { dismiss(); onAction(MENU_LOGIN) },
            )
        }
        if (isSourceVariableVisible) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.set_source_variable),
                onClick = { dismiss(); onAction(MENU_SET_SOURCE_VARIABLE) },
            )
        }
        if (isBookVariableVisible) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.set_book_variable),
                onClick = { dismiss(); onAction(MENU_SET_BOOK_VARIABLE) },
            )
        }
        RoundDropdownMenuItem(
            text = stringResource(R.string.copy_book_url),
            onClick = { dismiss(); onAction(MENU_COPY_BOOK_URL) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.copy_toc_url),
            onClick = { dismiss(); onAction(MENU_COPY_TOC_URL) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.allow_update),
            onClick = { dismiss(); onAction(MENU_CAN_UPDATE) },
            isSelected = canUpdate,
        )
        if (isLocalTxt) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.split_long_chapter),
                onClick = { dismiss(); onAction(MENU_SPLIT_LONG_CHAPTER) },
                isSelected = splitLongChapter,
            )
        }
        if (isLocal) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.upload_to_remote),
                onClick = { dismiss(); onAction(MENU_UPLOAD) },
            )
        }
        RoundDropdownMenuItem(
            text = stringResource(R.string.clear_cache),
            onClick = { dismiss(); onAction(MENU_CLEAR_CACHE) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.book_voice_casting),
            onClick = { dismiss(); onAction(MENU_VOICE_CASTING) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.cloud_tts),
            onClick = { dismiss(); onAction(MENU_CLOUD_TTS) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.log),
            onClick = { dismiss(); onAction(MENU_LOG) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.delete),
            onClick = { dismiss(); onAction(MENU_DELETE) },
        )
    }
}

const val MENU_EDIT = 0
const val MENU_SHARE = 1
const val MENU_REFRESH = 2
const val MENU_CHANGE_SOURCE = 3
const val MENU_GROUP = 4
const val MENU_TOP = 5
const val MENU_LOGIN = 6
const val MENU_SET_SOURCE_VARIABLE = 7
const val MENU_SET_BOOK_VARIABLE = 8
const val MENU_COPY_BOOK_URL = 9
const val MENU_COPY_TOC_URL = 10
const val MENU_CAN_UPDATE = 11
const val MENU_SPLIT_LONG_CHAPTER = 12
const val MENU_UPLOAD = 13
const val MENU_CLEAR_CACHE = 14
const val MENU_LOG = 15
const val MENU_DELETE = 16
const val MENU_VOICE_CASTING = 17
const val MENU_CLOUD_TTS = 18
