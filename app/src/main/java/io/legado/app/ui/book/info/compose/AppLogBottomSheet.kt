package io.legado.app.ui.book.info.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.ui.common.compose.ModalLegadoBottomSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compose 日志底部抽屉，替代 [io.legado.app.ui.about.AppLogDialog]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLogBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
) {
    var logs by remember(show) { mutableStateOf(AppLog.logs.toList()) }
    var showDetail by remember { mutableStateOf<String?>(null) }

    ModalLegadoBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.log),
    ) {
        // Clear button — right-aligned tone button
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            FilledTonalButton(
                onClick = {
                    AppLog.clear()
                    logs = emptyList()
                },
            ) {
                Text(text = stringResource(R.string.clear))
            }
        }

        if (logs.isEmpty()) {
            Text(
                text = "暂无日志",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(logs) { item ->
                    val (time, msg, _) = item
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(time)),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }

    // Detail sheet (nested)
    ModalLegadoBottomSheet(
        show = showDetail != null,
        onDismissRequest = { showDetail = null },
        title = "Log",
    ) {
        Text(
            text = showDetail ?: "",
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
