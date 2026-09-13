package io.legado.app.ui.ai.outline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.legado.app.ui.common.compose.rememberLegadoBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.usecase.structured.MutationSnapshotService
import io.legado.app.ui.common.compose.legadoSheetInsets
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineVersionHistorySheet(
    versions: List<MutationSnapshotService.OutlineSnapshotSummary>,
    currentContent: String,
    onDismiss: () -> Unit,
    onRestore: (snapshotId: String) -> Unit,
    loadSnapshotContent: suspend (snapshotId: String) -> String?,
) {
    val sheetState = rememberLegadoBottomSheetState()
    var pendingRestore by remember { mutableStateOf<MutationSnapshotService.OutlineSnapshotSummary?>(null) }
    var previewBefore by remember { mutableStateOf<String?>(null) }
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
                stringResource(R.string.ai_outline_version_history),
                style = MaterialTheme.typography.titleMedium,
            )
            if (versions.isEmpty()) {
                Text(
                    stringResource(R.string.ai_outline_no_versions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(versions, key = { it.id }) { version ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pendingRestore = version
                                }
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
        androidx.compose.runtime.LaunchedEffect(version.id) {
            previewBefore = loadSnapshotContent(version.id)
        }
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.ai_outline_restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(version.diffSummary.orEmpty())
                    previewBefore?.let { before ->
                        Text(
                            before.take(400),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRestore(version.id)
                        pendingRestore = null
                    },
                ) { Text(stringResource(R.string.ai_outline_restore_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
