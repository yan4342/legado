package io.legado.app.ui.book.read

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import io.legado.app.R
import io.legado.app.ui.common.compose.LocalAnimationsEnabled
import io.legado.app.ui.common.compose.legadoPopupBackgroundColor
import io.legado.app.ui.common.compose.legadoPopupPrimaryTextColor
import io.legado.app.ui.common.compose.rememberLegadoColorScheme
import kotlin.math.roundToInt

/**
 * Compose 文字选择菜单,替代旧的 ThemedPopupWindow 实现。
 * 支持三种形态:
 * - 快速菜单(QuickMenu):LazyRow 一级菜单 + "更多"按钮
 * - 展开菜单(MultiLineMenu):expandTextMenu 开启时,FlowRow 展示全部项
 * - 折叠菜单(MoreMenu):点击"更多"后展示剩余项
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TextActionSelectionMenu(
    menuState: TextMenuState?,
    expandTextMenu: Boolean,
    onDismiss: () -> Unit,
    onItemClick: (ActionMenuItem) -> Unit,
    onItemLongClick: (ActionMenuItem) -> Unit,
    onOpenManage: () -> Unit,
) {
    val colorScheme = rememberLegadoColorScheme()
    val animationsEnabled = LocalAnimationsEnabled.current

    var retainedMenuState by remember { mutableStateOf(menuState) }
    SideEffect {
        if (menuState != null) retainedMenuState = menuState
    }
    val visibilityState = remember { MutableTransitionState(false) }
    visibilityState.targetState = menuState != null
    val displayedMenuState = menuState ?: retainedMenuState ?: return
    if (!visibilityState.currentState && !visibilityState.targetState) return

    val localDensity = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val maxMenuWidth = with(localDensity) { containerSize.width.toDp() } - 32.dp
    val menuShadowPadding = 12.dp
    val menuCardMaxWidth = maxMenuWidth - menuShadowPadding * 2
    var showMoreMenu by remember { mutableStateOf(false) }
    LaunchedEffect(menuState) {
        showMoreMenu = false
    }
    val draftItems = displayedMenuState.items

    val primaryItems = remember(draftItems) { draftItems.filter { it.showState == 0 } }
    val collapsedItems = remember(draftItems) { draftItems.filter { it.showState == 1 } }
    val activeMultiItems = remember(draftItems) { draftItems.filter { it.showState == 0 || it.showState == 1 } }

    val density = localDensity.density
    val shadowPaddingPx = with(localDensity) { menuShadowPadding.roundToPx() }
    val positionProvider = remember(displayedMenuState, density, shadowPaddingPx) {
        TextMenuPositionProvider(
            density = density,
            startX = displayedMenuState.startX,
            startTopY = displayedMenuState.startTopY,
            startBottomY = displayedMenuState.startBottomY,
            endX = displayedMenuState.endX,
            endBottomY = displayedMenuState.endBottomY,
            shadowPadding = shadowPaddingPx,
        )
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = {
            showMoreMenu = false
            onDismiss()
        },
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            // 外部点击不在此拦截,交给 ReadView 的选择取消路径关闭(pressOnTextSelected 抑制翻页),
            // 与旧的 TextActionMenu 行为一致:点页面只关菜单不翻页
            dismissOnClickOutside = false,
        )
    ) {
        MaterialExpressiveTheme(colorScheme = colorScheme) {
            AnimatedVisibility(
                visibleState = visibilityState,
                // 与应用其他弹窗(RoundDropdownMenu)一致的 expressive spring 语言;E-Ink 模式禁用动画
                enter = if (animationsEnabled) {
                    fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                        scaleIn(
                            initialScale = 0.85f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                        )
                } else {
                    EnterTransition.None
                },
                exit = if (animationsEnabled) {
                    fadeOut() + scaleOut(targetScale = 0.85f)
                } else {
                    ExitTransition.None
                },
            ) {
                if (expandTextMenu) {
                    Box(modifier = Modifier.padding(menuShadowPadding)) {
                        MenuCard(
                            modifier = Modifier.widthIn(max = menuCardMaxWidth),
                            elevation = 12.dp,
                        ) {
                            MultiLineMenuView(
                                items = activeMultiItems,
                                onItemClick = onItemClick,
                                onItemLongClick = onItemLongClick,
                                onManageClick = {
                                    onDismiss()
                                    onOpenManage()
                                }
                            )
                        }
                    }
                } else if (showMoreMenu) {
                    // "更多"/"返回"在同一 Popup 内瞬时切换。
                    // 不用 AnimatedContent/Crossfade:内容尺寸变化会触发 Popup 重定位,造成抖动。
                    Box(modifier = Modifier.padding(menuShadowPadding)) {
                        MenuCard(
                            modifier = Modifier.widthIn(max = menuCardMaxWidth),
                            elevation = 6.dp,
                        ) {
                            MoreMenuView(
                                items = collapsedItems,
                                onItemClick = onItemClick,
                                onItemLongClick = onItemLongClick,
                                onBack = { showMoreMenu = false },
                                onManageClick = {
                                    onDismiss()
                                    onOpenManage()
                                }
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.padding(menuShadowPadding)) {
                        MenuCard(
                            modifier = Modifier.widthIn(max = menuCardMaxWidth),
                            elevation = 6.dp,
                        ) {
                            QuickMenuView(
                                items = primaryItems,
                                hasMore = collapsedItems.isNotEmpty(),
                                onItemClick = onItemClick,
                                onItemLongClick = onItemLongClick,
                                onMoreClick = { showMoreMenu = true },
                                onSettingsClick = {
                                    onDismiss()
                                    onOpenManage()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 文字选择菜单卡片(弹窗背景色 + 圆角 + 阴影) */
@Composable
private fun MenuCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 6.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = legadoPopupBackgroundColor(),
        shadowElevation = elevation,
        content = content,
    )
}

@Composable
private fun MultiLineMenuView(
    items: List<ActionMenuItem>,
    onItemClick: (ActionMenuItem) -> Unit,
    onItemLongClick: (ActionMenuItem) -> Unit,
    onManageClick: () -> Unit
) {
    FlowRow(
        modifier = Modifier
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items.forEach { item ->
            QuickMenuItem(
                item = item,
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) },
                verticalPadding = 12.dp,
            )
        }

        Row(
            modifier = Modifier
                .clickable(onClick = onManageClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.edit_menu_items),
                tint = legadoPopupPrimaryTextColor(),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.edit_menu_items),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun QuickMenuView(
    items: List<ActionMenuItem>,
    hasMore: Boolean,
    onItemClick: (ActionMenuItem) -> Unit,
    onItemLongClick: (ActionMenuItem) -> Unit,
    onMoreClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier.wrapContentWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> item.uniqueId },
            ) { index, item ->
                QuickMenuItem(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) },
                    startPadding = if (index == 0) 16.dp else 10.dp,
                    endPadding = 8.dp,
                )
            }
        }
        if (items.isNotEmpty()) {
            VerticalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .height(20.dp)
                    .width(1.dp)
            )
        }
        Box(
            modifier = Modifier
                .clickable(onClick = if (hasMore) onMoreClick else onSettingsClick)
                .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (hasMore) Icons.Default.MoreVert else Icons.Default.Settings,
                contentDescription = stringResource(if (hasMore) R.string.more_menu else R.string.setting),
                tint = legadoPopupPrimaryTextColor(),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickMenuItem(
    item: ActionMenuItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    startPadding: Dp = 16.dp,
    endPadding: Dp = 16.dp,
    verticalPadding: Dp = 12.dp,
) {
    Row(
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(
                start = startPadding,
                end = endPadding,
                top = verticalPadding,
                bottom = verticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.iconDrawable != null) {
            AsyncImage(
                model = item.iconDrawable,
                contentDescription = item.title,
                modifier = Modifier
                    .size(16.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = item.title,
            color = legadoPopupPrimaryTextColor(),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

@Composable
private fun MoreMenuView(
    items: List<ActionMenuItem>,
    onItemClick: (ActionMenuItem) -> Unit,
    onItemLongClick: (ActionMenuItem) -> Unit,
    onBack: () -> Unit,
    onManageClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = legadoPopupPrimaryTextColor(),
                modifier = Modifier.size(20.dp)
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            modifier = Modifier
                .height(1.dp)
                .fillMaxWidth(0.8f)
                .align(Alignment.CenterHorizontally)
        )

        Column(
            modifier = Modifier
                .heightIn(max = 240.dp)
                .verticalScroll(scrollState)
                .fillMaxWidth()
        ) {
            items.forEach { item ->
                MoreMenuItem(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) },
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            modifier = Modifier
                .height(1.dp)
                .fillMaxWidth(0.8f)
                .align(Alignment.CenterHorizontally)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onManageClick)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.edit_menu_items),
                tint = legadoPopupPrimaryTextColor(),
                modifier = Modifier
                    .size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.edit_menu_items),
                color = legadoPopupPrimaryTextColor(),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoreMenuItem(
    item: ActionMenuItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.iconDrawable != null) {
            AsyncImage(
                model = item.iconDrawable,
                contentDescription = item.title,
                modifier = Modifier
                    .size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = item.title,
            color = legadoPopupPrimaryTextColor(),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

private class TextMenuPositionProvider(
    private val density: Float,
    private val startX: Int,
    private val startTopY: Int,
    private val startBottomY: Int,
    private val endX: Int,
    private val endBottomY: Int,
    private val shadowPadding: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x: Int
        val y: Int

        val marginHorizontal = (16 * density).toInt()
        val marginVertical = (12 * density).toInt()
        val textMargin = (4 * density).toInt()

        val cardHeight = popupContentSize.height - shadowPadding * 2
        val isSpaceEnoughAtTop = startTopY > cardHeight + textMargin + marginVertical

        if (isSpaceEnoughAtTop) {
            x = startX - shadowPadding
            y = startTopY - popupContentSize.height + shadowPadding - textMargin
        } else if (windowSize.height - startBottomY > cardHeight + textMargin + marginVertical) {
            x = startX - shadowPadding
            y = startBottomY + textMargin - shadowPadding
        } else {
            x = endX - shadowPadding
            y = endBottomY + textMargin - shadowPadding
        }

        val minX = marginHorizontal - shadowPadding
        val minY = marginVertical - shadowPadding
        val finalX = x.coerceIn(
            minX,
            (windowSize.width - popupContentSize.width - marginHorizontal + shadowPadding)
                .coerceAtLeast(minX),
        )
        val finalY = y.coerceIn(
            minY,
            (windowSize.height - popupContentSize.height - marginVertical + shadowPadding)
                .coerceAtLeast(minY),
        )

        return IntOffset(finalX, finalY)
    }
}
