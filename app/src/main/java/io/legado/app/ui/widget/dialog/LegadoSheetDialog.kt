package io.legado.app.ui.widget.dialog

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import io.legado.app.ui.common.compose.LegadoTheme
import kotlinx.coroutines.flow.first

/**
 * 通用 Compose M3 ModalBottomSheet 容器。
 *
 * 提供全屏透明 Dialog shell + ComposeView + LegadoTheme，
 * 内容通过 [content] lambda 注入，每种内容类型自行管理 ModalBottomSheet / SheetState。
 *
 * @param content @Composable (requestDismiss: () -> Unit) -> Unit
 *   requestDismiss 调用后会等 sheet 退场动画播完再 dismiss Dialog。
 */
class LegadoSheetDialog private constructor(
    private val content: @Composable (requestDismiss: () -> Unit) -> Unit
) : DialogFragment() {

    companion object {
        const val TAG = "LegadoSheetDialog"

        fun create(
            content: @Composable (requestDismiss: () -> Unit) -> Unit
        ): LegadoSheetDialog = LegadoSheetDialog(content)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = object : Dialog(
            requireContext(),
            android.R.style.Theme_Translucent_NoTitleBar_Fullscreen
        ) {}
        dialog.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            LegadoTheme {
                content { dismissAllowingStateLoss() }
            }
        }
    }
}

/**
 * 创建一个 requestDismiss 回调，它会在调用后等待 [sheetState] 到达 [SheetValue.Hidden]
 * 才执行 [onDismiss]（通常是 DialogFragment.dismiss()）。
 *
 * 用法：
 * ```
 * val sheetState = rememberModalBottomSheetState(...)
 * val requestDismiss = rememberDelayedDismiss(sheetState) { dismissAllowingStateLoss() }
 * ModalBottomSheet(onDismissRequest = requestDismiss, sheetState = sheetState, ...) { ... }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberDelayedDismiss(
    sheetState: SheetState,
    onDismiss: () -> Unit
): () -> Unit {
    var dismissRequested by remember { mutableStateOf(false) }

    LaunchedEffect(dismissRequested) {
        if (dismissRequested) {
            sheetState.hide()
            snapshotFlow { sheetState.currentValue }
                .first { it == SheetValue.Hidden }
            onDismiss()
        }
    }

    return { dismissRequested = true }
}
