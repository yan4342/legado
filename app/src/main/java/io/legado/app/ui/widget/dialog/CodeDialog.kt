package io.legado.app.ui.widget.dialog

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import io.legado.app.R
import io.legado.app.help.IntentData
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.common.compose.legadoPopupBackgroundColor
import io.legado.app.ui.common.compose.legadoPopupPrimaryTextColor

/**
 * 代码/文本查看对话框（Compose 渲染）。
 *
 * @param disableEdit 为 true 时只读展示（标题 code view），否则可编辑后保存。
 */
class CodeDialog() : DialogFragment() {

    constructor(code: String, disableEdit: Boolean = true, requestId: String? = null) : this() {
        arguments = Bundle().apply {
            putBoolean("disableEdit", disableEdit)
            putString("code", IntentData.put(code))
            putString("requestId", requestId)
        }
    }

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
        val code = IntentData.get<String>(arguments?.getString("code")) ?: ""
        val editable = arguments?.getBoolean("disableEdit") != true
        val requestId = arguments?.getString("requestId")
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    CodeDialogContent(
                        title = stringResource(if (editable) R.string.code_edit_title else R.string.code_view_title),
                        initialCode = code,
                        editable = editable,
                        onSave = { saved ->
                            (parentFragment as? Callback)?.onCodeSave(saved, requestId)
                                ?: (activity as? Callback)?.onCodeSave(saved, requestId)
                            dismiss()
                        },
                    )
                }
            }
        }
    }

    interface Callback {

        fun onCodeSave(code: String, requestId: String?)

    }

}

@Composable
private fun CodeDialogContent(
    title: String,
    initialCode: String,
    editable: Boolean,
    onSave: (String) -> Unit,
) {
    val backgroundColor = legadoPopupBackgroundColor()
    val textColor = legadoPopupPrimaryTextColor()
    var text by remember { mutableStateOf(initialCode) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .systemBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = textColor,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (editable) {
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onSave(text) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_save),
                        contentDescription = stringResource(R.string.action_save),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (editable) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = textColor,
                ),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 16.dp),
            )
        } else {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    ),
                    color = textColor,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
