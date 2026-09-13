package io.legado.app.ui.book.bookmark

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.common.compose.LegadoAlertDialog

/**
 * 书签编辑/查看对话框。被"所有书签"页（Compose）和阅读器（DialogFragment 内嵌）共用。
 *
 * @param onDelete 为 null 时不显示删除按钮（阅读器新增书签场景）。
 */
@Composable
fun BookmarkEditDialog(
    bookmark: Bookmark,
    onDismiss: () -> Unit,
    onSave: (bookText: String, content: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var bookText by remember(bookmark.time) { mutableStateOf(bookmark.bookText) }
    var noteContent by remember(bookmark.time) { mutableStateOf(bookmark.content) }

    LegadoAlertDialog(
        show = true,
        onDismissRequest = onDismiss,
        dialogTitle = bookmark.chapterName,
        confirmText = stringResource(R.string.ok),
        onConfirm = { onSave(bookText, noteContent) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismiss,
        content = {
            Column {
                OutlinedTextField(
                    value = bookText,
                    onValueChange = { bookText = it },
                    placeholder = { Text(stringResource(R.string.content)) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                OutlinedTextField(
                    value = noteContent,
                    onValueChange = { noteContent = it },
                    placeholder = { Text(stringResource(R.string.note_content)) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                if (onDelete != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
    )
}
