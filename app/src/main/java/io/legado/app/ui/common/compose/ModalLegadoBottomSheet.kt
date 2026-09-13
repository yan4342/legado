package io.legado.app.ui.common.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 统一 BottomSheet 状态：对应旧 [rememberModalBottomSheetState] 的 skipPartiallyExpanded 语义。
 */
@Composable
@ExperimentalMaterial3Api
fun rememberLegadoBottomSheetState(
    skipPartiallyExpanded: Boolean = false,
    confirmValueChange: (SheetValue) -> Boolean = { true },
): SheetState = rememberBottomSheetState(
    initialValue = SheetValue.Hidden,
    enabledValues = if (skipPartiallyExpanded) {
        setOf(SheetValue.Hidden, SheetValue.Expanded)
    } else {
        setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded)
    },
    confirmValueChange = confirmValueChange,
)

/**
 * 关闭二次确认：用于没有半屏态的全展开 sheet（如大纲、执行记录）。
 *
 * 这类 sheet 一下滑就直接从全屏到关闭、没有任何半屏过渡，最容易误触。此封装：
 * - 通过 [SheetState.confirmValueChange] veto 对 Hidden 的收敛，**下滑时 sheet 不会
 *   滑离/收起**（松手即弹回原位），只弹一次 [onNeedConfirm] 提示；
 * - 提示后 [confirmDelayMillis] 内的再次下滑 / 点遮罩 / 返回才会真正调用 [onDismiss]；
 * - 显式的"关闭/保存"按钮直接走 [onDismiss]，不受此拦截。
 *
 * @return (sheetState, onDismissRequest)，直接透传给 ModalBottomSheet。
 */
@Composable
@ExperimentalMaterial3Api
fun rememberSwipeToConfirmDismiss(
    skipPartiallyExpanded: Boolean = true,
    confirmDelayMillis: Long = 3_000L,
    onNeedConfirm: (() -> Unit)? = null,
    onDismiss: () -> Unit,
): Pair<SheetState, () -> Unit> {
    val scope = rememberCoroutineScope()
    // justVetoed：本次下滑已被 confirmValueChange veto，onDismissRequest 若随后回落属同一手势。
    val justVetoed = remember { mutableStateOf(false) }
    // armed：已提示过，下一次下滑 / 点外 / 返回真正关闭。
    val armed = remember { mutableStateOf(false) }
    val currentOnNeedConfirm by rememberUpdatedState(onNeedConfirm)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    fun reset() {
        justVetoed.value = false
        armed.value = false
    }

    val sheetState = rememberLegadoBottomSheetState(
        skipPartiallyExpanded = skipPartiallyExpanded,
        confirmValueChange = { target ->
            when {
                target != SheetValue.Hidden -> true
                armed.value -> {
                    justVetoed.value = false
                    true
                }
                else -> {
                    armed.value = true
                    justVetoed.value = true
                    currentOnNeedConfirm?.invoke()
                    scope.launch {
                        delay(confirmDelayMillis)
                        reset()
                    }
                    false
                }
            }
        },
    )
    val onDismissRequest: () -> Unit = {
        when {
            justVetoed.value -> {
                // 本次下滑已被 veto 的回落回调：不关闭，armed 已置位，下一次触发即关闭。
                justVetoed.value = false
            }
            armed.value -> currentOnDismiss()
            else -> {
                // 点遮罩 / 返回（不经过 confirmValueChange）：第一次只提示。
                armed.value = true
                currentOnNeedConfirm?.invoke()
                scope.launch { sheetState.expand() }
                scope.launch {
                    delay(confirmDelayMillis)
                    reset()
                }
            }
        }
    }
    return sheetState to onDismissRequest
}

/**
 * Material 3 ModalBottomSheet 统一封装：
 * - 标题栏区域 primaryColor 背景，标题从左对齐动画居中，DragHandle 淡出
 * - 正文区域卡片色背景，与标题栏视觉分离
 * - 可选 [expandedContent]：展开后 crossfade 切换内容
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModalLegadoBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    sheetState: SheetState? = null,
    skipPartiallyExpanded: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    expandedContent: (@Composable ColumnScope.() -> Unit)? = null,
    onExpanded: (() -> Unit)? = null,
    transitionOverlay: (@Composable BoxScope.(expansionProgress: Float) -> Unit)? = null,
    contentContainerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!show) return

    val colorScheme = rememberLegadoColorScheme()
    val containerColor = contentContainerColor ?: legadoCardBackgroundColor()
    val dragHandleColor = legadoPopupPrimaryTextColor().copy(alpha = 0.4f)
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val titleStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)

    val resolvedSheetState = sheetState
        ?: rememberLegadoBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)

    val isExpanding by remember {
        derivedStateOf { resolvedSheetState.currentValue == SheetValue.Expanded }
    }
    val expansionProgress by animateFloatAsState(
        targetValue = if (isExpanding) 1f else 0f,
        label = "expansionProgress"
    )

    // onExpanded 回调：半展开模式下监听用户拖到全展开
    val currentOnExpanded by rememberUpdatedState(onExpanded)
    LaunchedEffect(resolvedSheetState, skipPartiallyExpanded) {
        if (skipPartiallyExpanded || currentOnExpanded == null) return@LaunchedEffect
        // 先等 sheet 稳定到半展开，再等用户拖到全展开
        snapshotFlow { resolvedSheetState.currentValue }
            .first { it == SheetValue.PartiallyExpanded }
        snapshotFlow { resolvedSheetState.currentValue }
            .first { it == SheetValue.Expanded }
        currentOnExpanded?.invoke()
    }

    // 展开状态下拽 DragHandle 时阻止直接关闭，先退回预览状态
    if (expandedContent != null) {
        val scope = rememberCoroutineScope()
        LaunchedEffect(resolvedSheetState) {
            snapshotFlow {
                Pair(resolvedSheetState.currentValue, resolvedSheetState.targetValue)
            }.collect { (current, target) ->
                if (current == SheetValue.Expanded && target == SheetValue.Hidden) {
                    scope.launch { resolvedSheetState.partialExpand() }
                }
            }
        }
    }

    val titleWidthDp = if (title != null) {
        with(density) {
            textMeasurer.measure(
                text = title,
                style = titleStyle,
                maxLines = 1,
            ).size.width.toDp()
        }
    } else 0.dp

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes()
    ) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = resolvedSheetState,
            containerColor = colorScheme.primary,
            dragHandle = null,
            shape = BottomSheetDefaults.ExpandedShape,
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .legadoSheetInsets()
                    .padding(bottom = 16.dp)
            ) {
                if (title != null) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(start = 20.dp, end = 4.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        val containerWidthPx = with(density) { maxWidth.toPx() }
                        val titleWidthPx = with(density) { titleWidthDp.toPx() }
                        val centerOffsetPx = ((containerWidthPx - titleWidthPx) / 2f).coerceAtLeast(0f)

                        BottomSheetDefaults.DragHandle(
                            color = dragHandleColor,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .alpha(1f - expansionProgress)
                        )

                        Text(
                            text = title,
                            style = titleStyle,
                            color = colorScheme.onPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer {
                                translationX = centerOffsetPx * expansionProgress
                            }
                        )

                        Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                            actions()
                        }
                    }

                    val contentTextColor = legadoPopupPrimaryTextColor()
                    CompositionLocalProvider(LocalContentColor provides contentTextColor) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(containerColor)
                        ) {
                            Spacer(Modifier.height(16.dp))
                            val columnScope = this
                            Box {
                                with(columnScope) {
                                    if (expandedContent != null) {
                                        AnimatedContent(
                                            targetState = isExpanding,
                                            transitionSpec = {
                                                (fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(200)))
                                                    .using(SizeTransform(clip = false))
                                            },
                                            label = "sheetContent"
                                        ) { expanded ->
                                            if (expanded) expandedContent() else content()
                                        }
                                    } else {
                                        content()
                                    }
                                }
                                if (transitionOverlay != null) {
                                    transitionOverlay(expansionProgress)
                                }
                            }
                        }
                    }
                } else {
                    BottomSheetDefaults.DragHandle(
                        color = dragHandleColor,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    val columnScope = this
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(containerColor)
                    ) {
                        with(columnScope) {
                            if (expandedContent != null) {
                                AnimatedContent(
                                    targetState = isExpanding,
                                    transitionSpec = {
                                        (fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(200)))
                                            .using(SizeTransform(clip = false))
                                    },
                                    label = "sheetContent"
                                ) { expanded ->
                                    if (expanded) expandedContent() else content()
                                }
                            } else {
                                content()
                            }
                        }
                        if (transitionOverlay != null) {
                            transitionOverlay(expansionProgress)
                        }
                    }
                }
            }
        }
    }
}
