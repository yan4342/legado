package io.legado.app.ui.common.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.common.compose.rememberLegadoColorScheme
import io.legado.app.ui.common.compose.legadoPopupBackgroundColor

/**
 * Material 3 对话框封装，使用 legado 主题颜色。
 *
 * 颜色使用 `rememberLegadoColorScheme()` + `legadoPopupBackgroundColor()`。
 * 内部嵌套 [MaterialExpressiveTheme] 确保弹窗动画效果一致。
 *
 * @param show 是否显示
 * @param onDismissRequest 点击外部或按返回键
 * @param dialogTitle 标题（可选）
 * @param text 正文文本（可选，可选中复制）
 * @param content 自定义内容（可选，替代或补充 text）
 * @param confirmText 确认按钮文字，默认"确定"
 * @param onConfirm 确认回调（为 null 时按钮不显示）
 * @param confirmIcon 确认按钮图标（可选）
 * @param dismissText 取消按钮文字，默认"取消"
 * @param onDismiss 取消回调（为 null 时按钮不显示）
 * @param dismissIcon 取消按钮图标（可选）
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LegadoAlertDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    onDismissRequest: () -> Unit,
    dialogTitle: String? = null,
    text: String? = null,
    content: (@Composable () -> Unit)? = null,
    confirmText: String = "确定",
    onConfirm: (() -> Unit)? = null,
    confirmIcon: Painter? = null,
    dismissText: String = "取消",
    onDismiss: (() -> Unit)? = null,
    dismissIcon: Painter? = null,
) {
    if (!show) return

    val colorScheme = rememberLegadoColorScheme()
    val containerColor = legadoPopupBackgroundColor()
    val popupTextColor = legadoPopupPrimaryTextColor()
    val popupTextSecondary = legadoPopupPrimaryTextColor().copy(alpha = 0.7f)

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes()
    ) {
        val confirmAction = onConfirm
        val dismissAction = onDismiss
        val hasText = text != null || content != null

        AlertDialog(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            containerColor = containerColor,
            iconContentColor = colorScheme.primary,
            titleContentColor = popupTextColor,
            textContentColor = popupTextSecondary,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            title = if (dialogTitle != null) { { Text(text = dialogTitle) } } else null,
            text = if (hasText) {
                {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        if (text != null) {
                            SelectionContainer {
                                Text(
                                    text = text,
                                    modifier = Modifier.padding(bottom = if (content != null) 16.dp else 0.dp)
                                )
                            }
                        }
                        if (content != null) {
                            content()
                        }
                    }
                }
            } else {
                null
            },
            confirmButton = if (confirmAction != null) {
                {
                    TextButton(
                        onClick = {
                            confirmAction()
                            onDismissRequest()
                        }
                    ) {
                        if (confirmIcon != null) {
                            Icon(confirmIcon, contentDescription = confirmText, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(text = confirmText, color = colorScheme.primary)
                    }
                }
            } else {
                {}
            },
            dismissButton = if (dismissAction != null) {
                {
                    TextButton(onClick = { dismissAction(); onDismissRequest() }) {
                        if (dismissIcon != null) {
                            Icon(dismissIcon, contentDescription = dismissText, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(text = dismissText)
                    }
                }
            } else {
                {}
            },
        )
    }
}
