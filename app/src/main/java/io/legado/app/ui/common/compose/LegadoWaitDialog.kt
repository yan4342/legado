package io.legado.app.ui.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R

/**
 * 加载等待弹窗（替代 View 版 [io.legado.app.ui.widget.dialog.WaitDialog]）。
 *
 * 视觉与旧版一致：横向排列的小型进度指示器 + 文案，圆角弹窗底色。
 * 不可点击外部取消（与旧版 setCanceledOnTouchOutside(false) 一致），返回键可关闭。
 *
 * 用法一（state 驱动）：
 * ```
 * var waitMessage by remember { mutableStateOf<String?>(null) }
 * LegadoWaitDialog(message = waitMessage)
 * ```
 *
 * 用法二（命令式桥接，便于迁移旧 show/dismiss 调用点）：
 * ```
 * val waitState = rememberLegadoWaitState()
 * LegadoWaitDialog(waitState)
 * waitState.show("恢复中…"); waitState.dismiss()
 * ```
 */
@Composable
fun LegadoWaitDialog(
    message: String?,
    onDismissRequest: () -> Unit = {},
) {
    if (message == null) return

    val colorScheme = rememberLegadoColorScheme()
    val containerColor = legadoPopupBackgroundColor()
    val popupTextColor = legadoPopupPrimaryTextColor()

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes()
    ) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
        ) {
            Row(
                modifier = Modifier
                    .background(containerColor, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 12.dp),
                    strokeWidth = 2.5.dp,
                    color = colorScheme.primary,
                )
                Text(
                    text = message.ifEmpty { stringResource(R.string.loading) },
                    color = popupTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * 命令式状态桥：让遗留的 `waitDialog.setText(x).show()/dismiss()` 调用点
 * 以最小改动切到 Compose 渲染。空字符串表示显示默认"加载中"文案。
 */
@Stable
class LegadoWaitState {
    internal var message by mutableStateOf<String?>(null)

    /** 用户按返回键取消时的回调（对应旧 setOnCancelListener）。 */
    var onCancel: (() -> Unit)? = null

    /** @param text 为 null 时显示默认加载文案。 */
    fun show(text: String? = null) {
        message = text ?: ""
    }

    fun dismiss() {
        message = null
    }

    /** 返回键触发：隐藏并回调 onCancel。 */
    fun cancel() {
        if (message != null) {
            val cb = onCancel
            message = null
            cb?.invoke()
        }
    }
}

@Composable
fun rememberLegadoWaitState(): LegadoWaitState = remember { LegadoWaitState() }

/** 命令式桥接入口：观察 [state]，由 [LegadoWaitDialog] 渲染。 */
@Composable
fun LegadoWaitDialog(state: LegadoWaitState) {
    LegadoWaitDialog(
        message = state.message,
        onDismissRequest = { state.cancel() },
    )
}
