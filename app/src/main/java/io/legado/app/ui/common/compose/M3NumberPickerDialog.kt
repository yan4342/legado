package io.legado.app.ui.common.compose

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment

/**
 * MD3 风格数字选择对话框，替代旧的 View 版
 * [io.legado.app.ui.widget.number.NumberPickerDialog]（AlertDialog + NumberPicker）。
 *
 * 结构与 [M3EditInputDialog] 一致：透明全屏 DialogFragment 宿 +
 * ComposeView 居中 Surface，内部复用滚轮选择器 [WheelNumberPicker]。
 *
 * 用法：
 * ```
 * showDialogFragment(
 *     M3NumberPickerDialog.create(
 *         title = getString(R.string.xxx),
 *         value = current,
 *         minValue = 0,
 *         maxValue = 9999,
 *     ) { value -> ... }
 * )
 * ```
 */
class M3NumberPickerDialog private constructor(
    private val title: String,
    private val initialValue: Int,
    private val minValue: Int,
    private val maxValue: Int,
    private val defaultButtonText: String,
) : DialogFragment() {

    companion object {
        private var confirmCallback: ((Int) -> Unit)? = null
        private var dismissCallback: (() -> Unit)? = null
        private var defaultCallback: (() -> Unit)? = null

        fun create(
            title: String,
            value: Int,
            minValue: Int = 0,
            maxValue: Int = 9999,
            defaultButtonText: String = "",
            onDefaultClick: (() -> Unit)? = null,
            onConfirm: (Int) -> Unit,
            onDismiss: (() -> Unit)? = null,
        ): M3NumberPickerDialog {
            confirmCallback = onConfirm
            dismissCallback = onDismiss
            defaultCallback = onDefaultClick
            return M3NumberPickerDialog(title, value, minValue, maxValue, defaultButtonText)
        }

        fun create(
            titleRes: Int,
            value: Int,
            minValue: Int = 0,
            maxValue: Int = 9999,
            onConfirm: (Int) -> Unit,
            onDismiss: (() -> Unit)? = null,
        ): M3NumberPickerDialog {
            confirmCallback = onConfirm
            dismissCallback = onDismiss
            defaultCallback = null
            return M3NumberPickerDialog("", value, minValue, maxValue, "").also {
                it.titleRes = titleRes
            }
        }
    }

    private var titleRes: Int = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = object : Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen) {}
        dialog.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    val resolvedTitle = if (titleRes != 0) stringResource(titleRes) else title
                    M3NumberPickerDialogContent(
                        title = resolvedTitle,
                        initialValue = initialValue,
                        minValue = minValue,
                        maxValue = maxValue,
                        defaultButtonText = defaultButtonText,
                        onDismiss = { dismiss() },
                        onDefaultClick = { defaultCallback?.invoke() },
                        onConfirm = { value ->
                            confirmCallback?.invoke(value)
                            confirmCallback = null
                            dismissCallback = null
                            defaultCallback = null
                            dismiss()
                        },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissCallback?.invoke()
        confirmCallback = null
        dismissCallback = null
        defaultCallback = null
    }
}

@Composable
private fun M3NumberPickerDialogContent(
    title: String,
    initialValue: Int,
    minValue: Int,
    maxValue: Int,
    defaultButtonText: String,
    onDismiss: () -> Unit,
    onDefaultClick: (() -> Unit)?,
    onConfirm: (Int) -> Unit,
) {
    val colorScheme = rememberLegadoColorScheme()
    val popupBg = legadoPopupBackgroundColor()
    val popupTextColor = legadoPopupPrimaryTextColor()

    var selectedValue by remember { mutableIntStateOf(initialValue) }

    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = popupBg,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = popupTextColor,
                )
                Spacer(Modifier.height(16.dp))
                WheelNumberPicker(
                    value = selectedValue,
                    onValueChange = { selectedValue = it },
                    minValue = minValue,
                    maxValue = maxValue,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (onDefaultClick != null && defaultButtonText.isNotEmpty()) {
                        TextButton(onClick = onDefaultClick) {
                            Text(defaultButtonText)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onConfirm(selectedValue) }) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
        }
    }
}
