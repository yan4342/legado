package io.legado.app.ui.book.info.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.common.compose.ModalLegadoBottomSheet
import io.legado.app.ui.common.compose.rememberLegadoColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn

/**
 * Compose 分组选择底部抽屉，替代 [io.legado.app.ui.book.group.GroupSelectDialog]。
 * 支持多选（groupId 为 bitmask），取消/确定按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSelectBottomSheet(
    show: Boolean,
    initialGroupId: Long,
    activity: androidx.fragment.app.FragmentActivity,
    onDismissRequest: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    if (!show) return

    var groups by remember { mutableStateOf<List<BookGroup>>(emptyList()) }
    var selectedGroupId by remember(initialGroupId, show) { mutableLongStateOf(initialGroupId) }
    val colorScheme = rememberLegadoColorScheme()

    LaunchedEffect(Unit) {
        appDb.bookGroupDao.flowSelect()
            .catch { /* ignore */ }
            .flowOn(Dispatchers.IO)
            .conflate()
            .collect { groups = it }
    }

    ModalLegadoBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.group_select),
    ) {
        if (groups.isEmpty()) {
            Text(
                text = stringResource(R.string.empty),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(groups, key = { it.groupId }) { group ->
                    val isSelected = (selectedGroupId and group.groupId) != 0L
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                selectedGroupId = if (checked) {
                                    selectedGroupId + group.groupId
                                } else {
                                    selectedGroupId - group.groupId
                                }
                            },
                        )
                        Text(
                            text = group.groupName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                        )
                    }
                }
            }

            // 操作按钮栏 — 左取消 / 右确定
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(
                    onClick = {
                        onConfirm(selectedGroupId)
                        onDismissRequest()
                    },
                ) {
                    Text(
                        stringResource(R.string.ok),
                        color = colorScheme.primary,
                    )
                }
            }
        }
    }
}
