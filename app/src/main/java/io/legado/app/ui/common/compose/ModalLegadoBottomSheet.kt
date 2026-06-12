package io.legado.app.ui.common.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Material 3 底部抽屉封装，使用 legado 主题颜色。
 *
 * 核心：在内容 lambda 内嵌套 [MaterialExpressiveTheme]，
 * 确保弹窗内动画使用 expressive spring 参数。
 * 颜色使用 `rememberLegadoColorScheme()` + `legadoCardBackgroundColor()`。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModalLegadoBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!show) return

    val colorScheme = rememberLegadoColorScheme()
    val containerColor = legadoCardBackgroundColor()
    val contentColor = legadoPopupPrimaryTextColor()
    val dragHandleColor = legadoPopupPrimaryTextColor().copy(alpha = 0.4f)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes()
    ) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = containerColor,
            contentColor = contentColor,
            dragHandle = { BottomSheetDefaults.DragHandle(color = dragHandleColor) },
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                content()
            }
        }
    }
}
