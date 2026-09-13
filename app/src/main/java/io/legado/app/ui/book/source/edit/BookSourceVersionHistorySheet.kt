package io.legado.app.ui.book.source.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.legado.app.ui.common.compose.rememberLegadoBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.usecase.BookSourceVersionService
import io.legado.app.domain.usecase.structured.StructuredDataDiff
import io.legado.app.ui.common.compose.legadoSheetInsets
import io.legado.app.ui.widget.dialog.LegadoSheetDialog
import java.text.DateFormat
import java.util.Date

fun createBookSourceVersionHistoryDialog(
    versions: List<BookSourceVersionService.VersionSummary>,
    onRestore: (versionId: String) -> Unit,
    loadDiff: suspend (versionId: String) -> BookSourceVersionService.DiffResult?,
): LegadoSheetDialog = LegadoSheetDialog.create { requestDismiss ->
    BookSourceVersionHistorySheet(
        versions = versions,
        onDismiss = requestDismiss,
        onRestore = { id ->
            onRestore(id)
            requestDismiss()
        },
        loadDiff = loadDiff,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSourceVersionHistorySheet(
    versions: List<BookSourceVersionService.VersionSummary>,
    onDismiss: () -> Unit,
    onRestore: (versionId: String) -> Unit,
    loadDiff: suspend (versionId: String) -> BookSourceVersionService.DiffResult?,
) {
    val sheetState = rememberLegadoBottomSheetState()
    var pendingRestore by remember { mutableStateOf<BookSourceVersionService.VersionSummary?>(null) }
    var diffPreview by remember { mutableStateOf<BookSourceVersionService.DiffResult?>(null) }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .legadoSheetInsets()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.book_source_ai_version_history),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.book_source_ai_version_history_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (versions.isEmpty()) {
                Text(
                    stringResource(R.string.book_source_ai_no_versions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(versions, key = { it.id }) { version ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pendingRestore = version }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(
                                dateFormat.format(Date(version.createdAt)),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                listOfNotNull(version.source, version.diffSummary)
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    pendingRestore?.let { version ->
        LaunchedEffect(version.id) {
            diffPreview = loadDiff(version.id)
        }
        AlertDialog(
            onDismissRequest = {
                pendingRestore = null
                diffPreview = null
            },
            title = { Text(stringResource(R.string.book_source_ai_restore_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        listOfNotNull(version.source, version.diffSummary)
                            .joinToString(" · ")
                            .ifBlank { version.id },
                    )
                    val changes = diffPreview?.changes.orEmpty()
                    if (changes.isNotEmpty()) {
                        Text(
                            StructuredDataDiff.formatDiffForPreview(changes),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        diffPreview?.summary?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRestore(version.id)
                        pendingRestore = null
                        diffPreview = null
                    },
                ) { Text(stringResource(R.string.book_source_ai_restore_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingRestore = null
                        diffPreview = null
                    },
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
