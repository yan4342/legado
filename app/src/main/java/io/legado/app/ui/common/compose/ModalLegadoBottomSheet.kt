package io.legado.app.ui.common.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Material 3 ModalBottomSheet 统一封装，Markdown 风格装饰 header：
 * - 标题栏区域 primaryColor 背景，标题从左对齐动画居中，DragHandle 淡出
 * - 正文区域卡片色背景，与标题栏视觉分离
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModalLegadoBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    sheetState: SheetState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    if (!show) return

    val colorScheme = rememberLegadoColorScheme()
    val containerColor = legadoCardBackgroundColor()
    val dragHandleColor = legadoPopupPrimaryTextColor().copy(alpha = 0.4f)
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val titleStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)

    val resolvedSheetState = sheetState
        ?: rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val isExpanding by remember {
        derivedStateOf { resolvedSheetState.targetValue == SheetValue.Expanded }
    }
    val expansionProgress by animateFloatAsState(
        targetValue = if (isExpanding) 1f else 0f,
        label = "expansionProgress"
    )

    // 只测一次标题宽度，内容不变量
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
                    .padding(bottom = 16.dp)
            ) {
                if (title != null) {
                    // 标题栏：DragHandle 占位（alpha 淡出） + 标题 graphicsLayer 居中平移
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        val containerWidthPx = with(density) { maxWidth.toPx() }
                        val titleWidthPx = with(density) { titleWidthDp.toPx() }
                        // 标题过长时不推过头，clamp 到 0 保持左对齐即可
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

                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            actions()
                        }
                    }

                    // 正文区域：卡片色背景，顶部留白衔接 sheet 圆角
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(containerColor)
                    ) {
                        Spacer(Modifier.height(16.dp))
                        content()
                    }
                } else {
                    // 无标题：仅 DragHandle
                    BottomSheetDefaults.DragHandle(
                        color = dragHandleColor,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                    )
                    content()
                }
            }
        }
    }
}
