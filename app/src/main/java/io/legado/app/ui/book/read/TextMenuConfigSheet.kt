package io.legado.app.ui.book.read

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.common.compose.ModalLegadoBottomSheet
import io.legado.app.ui.common.compose.rememberLegadoBottomSheetState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 文字选择菜单项编辑器。
 * 分组管理:一级菜单 / 折叠菜单 / 已隐藏;组内可拖拽排序;隐藏项可恢复。
 * 保存后通过 [onSaved] 持久化(排序 + showState)。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TextMenuConfigSheet(
    show: Boolean,
    items: List<ActionMenuItem>,
    expandTextMenu: Boolean,
    onExpandTextMenuChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    onSaved: (List<ActionMenuItem>) -> Unit,
) {
    var draftItems by remember(show, items) { mutableStateOf(items) }
    var group1Expanded by remember(show) { mutableStateOf(true) }
    var group2Expanded by remember(show) { mutableStateOf(true) }
    var group3Expanded by remember(show) { mutableStateOf(false) }

    val group1Items = draftItems.filter { it.showState == 0 }
    val group2Items = draftItems.filter { it.showState == 1 }
    val group3Items = draftItems.filter { it.showState == 2 }

    fun moveTo(item: ActionMenuItem, newShowState: Int) {
        draftItems = draftItems.map {
            if (it.uniqueId == item.uniqueId) it.copy(showState = newShowState) else it
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = draftItems.indexOfFirst { it.uniqueId == from.key }
        val toIndex = draftItems.indexOfFirst { it.uniqueId == to.key }
        if (fromIndex != -1 && toIndex != -1 &&
            draftItems[fromIndex].showState == draftItems[toIndex].showState
        ) {
            draftItems = draftItems.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
        }
    }

    // 拖拽排序期间禁止下滑关闭,避免手势冲突
    val sheetState = rememberLegadoBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = {
            if (it == SheetValue.Hidden) !reorderableState.isAnyItemDragging else true
        },
    )

    ModalLegadoBottomSheet(
        show = show,
        sheetState = sheetState,
        skipPartiallyExpanded = true,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.edit_select_menu),
        actions = {
            IconButton(onClick = { onSaved(draftItems); onDismissRequest() }) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = stringResource(R.string.action_save),
                )
            }
        },
    ) {
        LazyColumn(
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(bottom = 16.dp),
        ) {
            item(key = "expand") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.expand_text_menu),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = expandTextMenu,
                        onCheckedChange = onExpandTextMenuChange,
                    )
                }
            }

            stickyHeader(key = "group1") {
                GroupHeader(
                    title = stringResource(R.string.primary_menu),
                    expanded = group1Expanded,
                    onToggle = { group1Expanded = !group1Expanded },
                )
            }
            if (group1Expanded) {
                itemsIndexed(group1Items, key = { _, item -> item.uniqueId }) { _, item ->
                    ReorderableItem(reorderableState, key = item.uniqueId) { isDragging ->
                        ConfigItemRow(
                            item = item,
                            isDragging = isDragging,
                            dragHandleModifier = Modifier.longPressDraggableHandle(),
                            groupIcon = Icons.Default.KeyboardArrowDown,
                            groupContentDescription = stringResource(R.string.collapsed_menu),
                            onGroupAction = { moveTo(item, 1) },
                            onHide = { moveTo(item, 2) },
                        )
                    }
                }
            }

            stickyHeader(key = "group2") {
                GroupHeader(
                    title = stringResource(R.string.collapsed_menu),
                    expanded = group2Expanded,
                    onToggle = { group2Expanded = !group2Expanded },
                )
            }
            if (group2Expanded) {
                itemsIndexed(group2Items, key = { _, item -> item.uniqueId }) { _, item ->
                    ReorderableItem(reorderableState, key = item.uniqueId) { isDragging ->
                        ConfigItemRow(
                            item = item,
                            isDragging = isDragging,
                            dragHandleModifier = Modifier.longPressDraggableHandle(),
                            groupIcon = Icons.Default.KeyboardArrowUp,
                            groupContentDescription = stringResource(R.string.primary_menu),
                            onGroupAction = { moveTo(item, 0) },
                            onHide = { moveTo(item, 2) },
                        )
                    }
                }
            }

            item(key = "group3") {
                Column {
                    GroupHeader(
                        title = stringResource(R.string.hidden_items),
                        expanded = group3Expanded,
                        onToggle = { group3Expanded = !group3Expanded },
                    )
                    if (group3Expanded && group3Items.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            group3Items.forEach { item ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.clickable { moveTo(item, 0) },
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    ) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 分组标题:点击展开/收起 */
@Composable
private fun LazyItemScope.GroupHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 可拖拽排序的菜单项行 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConfigItemRow(
    item: ActionMenuItem,
    isDragging: Boolean,
    dragHandleModifier: Modifier,
    groupIcon: ImageVector,
    groupContentDescription: String,
    onGroupAction: () -> Unit,
    onHide: () -> Unit,
) {
    val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = elevation,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(dragHandleModifier),
        ) {
            // 拖拽提示图标(整行长按拖拽排序)
            Box(
                modifier = Modifier.padding(start = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            IconButton(
                onClick = onGroupAction,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = groupIcon,
                    contentDescription = groupContentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = onHide,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.hidden_items),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
