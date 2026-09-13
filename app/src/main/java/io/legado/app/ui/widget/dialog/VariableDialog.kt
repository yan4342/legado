package io.legado.app.ui.widget.dialog

import android.app.Application
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.common.compose.legadoPopupBackgroundColor
import io.legado.app.ui.common.compose.legadoPopupPrimaryTextColor

class VariableDialog() : DialogFragment() {

    private val viewModel by viewModels<ViewModel>()

    constructor(title: String, key: String, variable: String?, comment: String) : this() {
        arguments = Bundle().apply {
            putString("title", title)
            putString("key", key)
            putString("variable", variable)
            putString("comment", comment)
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
            // 软键盘适配：窗口不自行平移/缩放，由 Compose imePadding 抬升卡片
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val arguments = arguments ?: run {
            dismissAllowingStateLoss()
            return View(requireContext())
        }
        viewModel.init(arguments) {}
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    VariableDialogContent(
                        title = arguments.getString("title").orEmpty(),
                        initialVariable = arguments.getString("variable").orEmpty(),
                        comment = arguments.getString("comment"),
                        onDismiss = { dismissAllowingStateLoss() },
                        onSave = { variable ->
                            callback?.setVariable(viewModel.key ?: "", variable)
                            dismissAllowingStateLoss()
                        },
                    )
                }
            }
        }
    }

    val callback get() = (parentFragment as? Callback) ?: (activity as? Callback)

    class ViewModel(application: Application) : BaseViewModel(application) {

        var key: String? = null
        var comment: String? = null
        var variable: String? = null

        fun init(arguments: Bundle, onFinally: () -> Unit) {
            if (key != null) return
            execute {
                key = arguments.getString("key")
                comment = arguments.getString("comment")
                variable = arguments.getString("variable")
            }.onFinally {
                onFinally.invoke()
            }
        }

    }

    interface Callback {

        fun setVariable(key: String, variable: String?)

    }

}

@Composable
private fun VariableDialogContent(
    title: String,
    initialVariable: String,
    comment: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val popupBg = legadoPopupBackgroundColor()
    val popupTextColor = legadoPopupPrimaryTextColor()
    val popupTextSecondary = popupTextColor.copy(alpha = 0.7f)

    var text by remember { mutableStateOf(initialVariable) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .pointerInput(Unit) {
                detectTapGestures {
                    onDismiss()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = popupBg,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = popupTextColor,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onSave(text) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_save),
                            contentDescription = stringResource(R.string.action_save),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    text = stringResource(R.string.variable_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.heightIn(min = 12.dp))
                Text(
                    text = stringResource(R.string.variable_comment),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                SelectionContainer {
                    Text(
                        text = comment.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = popupTextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
