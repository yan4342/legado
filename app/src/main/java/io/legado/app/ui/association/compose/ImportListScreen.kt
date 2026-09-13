package io.legado.app.ui.association.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.LegadoWaitDialog
import io.legado.app.ui.common.compose.LegadoWaitState
import io.legado.app.ui.common.compose.RoundDropdownMenu
import io.legado.app.ui.common.compose.RoundDropdownMenuItem

/**
 * 导入列表条目（对应 item_source_import.xml 一行）。
 *
 * @param title     条目名称（原 cb_source_name 文案）
 * @param stateText 状态文案（新增/更新/已有），由调用方按业务判定后传入
 * @param selected  选中态
 */
data class ImportListItem(
    val title: String,
    val stateText: String? = null,
    val selected: Boolean = true,
)

/**
 * 顶栏菜单动作（对应原 TitleBar 的一个 menu item）。
 *
 * @param title      文案，调用方用 stringResource(原 menu xml 的 title 资源)
 * @param iconRes    原 menu xml 的 icon 资源（多数导入菜单无图标，传 null 即可）
 * @param showInBar  true = 直接显示为顶栏图标按钮（对应 showAsAction="always"）；
 *                   false = 收进 MoreVert 下拉（对应 showAsAction="never"，默认）
 * @param isSelected checkable 菜单项当前勾选态，下拉项尾部显示 ✓
 * @param onClick    点击回调（业务留在各 Dialog 类内）
 */
data class ImportMenuAction(
    val title: String,
    val iconRes: Int? = null,
    val showInBar: Boolean = false,
    val isSelected: Boolean = false,
    val onClick: () -> Unit = {},
)

/**
 * 导入类弹窗共享骨架（替代 R.layout.dialog_recycler_view 的 Compose 版）：
 * 标题栏（primary 底色 + 菜单）/ 多选列表 / 加载态 / 消息与空态 / 底部 全选-取消-确定。
 *
 * 业务逻辑（解析、校验、导入、CodeDialog 等）全部留在调用方 Dialog 类内，
 * 本组件只负责展示与回传交互。
 *
 * 示例（迁移 ImportBookSourceDialog 时）：
 * ```
 * ImportListScreen(
 *     title = stringResource(R.string.import_book_source),
 *     items = items,
 *     loading = loading,
 *     message = message,
 *     footerText = footerText,
 *     waitState = waitState,
 *     menuActions = listOf(
 *         ImportMenuAction(stringResource(R.string.diy_source_group), showInBar = true) { ... },
 *         ImportMenuAction(stringResource(R.string.select_new_source)) { ... },
 *         ImportMenuAction(stringResource(R.string.keep_original_name), isSelected = AppConfig.importKeepName) { ... },
 *     ),
 *     onItemClick = ::toggleSelect,
 *     ...
 * )
 * ```
 *
 * @param title       标题栏文字
 * @param items       条目列表（含选中态）
 * @param loading     加载中（对应原 rotateLoading）
 * @param message     居中提示文案（错误/wrong_format，对应原 tv_msg）；非 null 且不在加载时显示
 * @param emptyText   列表为空且无 [message]、不在加载时的居中占位文案；null 不显示
 * @param footerText  左下角全选/取消全选文案（对应 tv_footer_left）；null 隐藏
 * @param waitState   LegadoWaitState 加载桥（对应原 WaitDialog），null 不渲染等待弹窗
 * @param menuActions 顶栏菜单动作；空列表时不显示 MoreVert
 * @param onItemClick 点击条目或复选框，参数为条目下标（语义：切换选中态）
 * @param onItemOpen  点击条目右侧"打开"，参数为条目下标；null 隐藏"打开"
 * @param onFooterClick 点击左下角全选文案
 * @param onCancelClick 点击取消；null 隐藏取消
 * @param onOkClick   点击确定；null 隐藏确定
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportListScreen(
    title: String,
    items: List<ImportListItem>,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    message: String? = null,
    emptyText: String? = null,
    footerText: String? = null,
    waitState: LegadoWaitState? = null,
    menuActions: List<ImportMenuAction> = emptyList(),
    onItemClick: (index: Int) -> Unit = {},
    onItemOpen: ((index: Int) -> Unit)? = null,
    onFooterClick: () -> Unit = {},
    onCancelClick: (() -> Unit)? = null,
    onOkClick: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    // E-Ink 下标题栏用表面色（与 TxtRuleScreen 等已迁屏一致），否则保持原 TitleBar 的 primary 底色
    val eInkMode = AppConfig.isEInkMode
    val barContainerColor = if (eInkMode) colorScheme.surface else colorScheme.primary
    val barContentColor = if (eInkMode) colorScheme.onSurface else colorScheme.onPrimary
    val bodyTextColor = colorScheme.onSurface
    val secondaryTextColor = colorScheme.onSurfaceVariant
    val accentTextColor = colorScheme.secondary

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    color = barContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            actions = {
                menuActions.filter { it.showInBar }.forEach { action ->
                    IconButton(onClick = action.onClick) {
                        if (action.iconRes != null) {
                            Icon(
                                painter = painterResource(action.iconRes),
                                contentDescription = action.title,
                                tint = barContentColor,
                            )
                        } else {
                            Text(text = action.title, color = barContentColor)
                        }
                    }
                }
                val overflowActions = menuActions.filterNot { it.showInBar }
                if (overflowActions.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.menu),
                                tint = barContentColor,
                            )
                        }
                        RoundDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) { dismiss ->
                            overflowActions.forEach { action ->
                                RoundDropdownMenuItem(
                                    text = action.title,
                                    isSelected = action.isSelected,
                                    leadingIcon = action.iconRes?.let { res ->
                                        {
                                            Icon(
                                                painter = painterResource(res),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        dismiss()
                                        action.onClick()
                                    },
                                )
                            }
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = barContainerColor,
                titleContentColor = barContentColor,
                navigationIconContentColor = barContentColor,
                actionIconContentColor = barContentColor,
            ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(items) { index, item ->
                    ImportListItemRow(
                        item = item,
                        bodyTextColor = bodyTextColor,
                        secondaryTextColor = secondaryTextColor,
                        showOpen = onItemOpen != null,
                        onClick = { onItemClick(index) },
                        onOpenClick = { onItemOpen?.invoke(index) },
                    )
                }
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(36.dp)
                        .padding(6.dp)
                        .align(Alignment.Center),
                    strokeWidth = 2.dp,
                    color = colorScheme.primary,
                )
            } else if (message != null) {
                CenteredHintText(text = message, color = secondaryTextColor)
            } else if (items.isEmpty() && emptyText != null) {
                CenteredHintText(text = emptyText, color = secondaryTextColor)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (footerText != null) {
                Text(
                    text = footerText,
                    color = accentTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clickable(onClick = onFooterClick)
                        .padding(12.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (onCancelClick != null) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = secondaryTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clickable(onClick = onCancelClick)
                        .padding(12.dp),
                )
            }
            if (onOkClick != null) {
                Text(
                    text = stringResource(R.string.ok),
                    color = accentTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clickable(onClick = onOkClick)
                        .padding(12.dp),
                )
            }
        }
    }

    if (waitState != null) {
        LegadoWaitDialog(state = waitState)
    }
}

@Composable
private fun ImportListItemRow(
    item: ImportListItem,
    bodyTextColor: Color,
    secondaryTextColor: Color,
    showOpen: Boolean,
    onClick: () -> Unit,
    onOpenClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.selected,
            onCheckedChange = { onClick() },
        )
        Text(
            text = item.title,
            color = bodyTextColor,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!item.stateText.isNullOrEmpty()) {
            Text(
                text = item.stateText,
                color = secondaryTextColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (showOpen) {
            Text(
                text = stringResource(R.string.open),
                color = secondaryTextColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .clickable(onClick = onOpenClick)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun CenteredHintText(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}
