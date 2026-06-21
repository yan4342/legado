package io.legado.app.ui.common.compose

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment

/**
 * MD3 风格输入对话框，统一替代旧的 [io.legado.app.databinding.DialogEditTextBinding] 模式。
 *
 * 通过 [showM3EditDialog] 扩展函数调用，支持标题、初始值、提示文字、密码模式、
 * 中间按钮、下拉建议等。
 */
class M3EditInputDialog private constructor(
    private val title: String,
    private val initialValue: String = "",
    private val hint: String = "",
    private val isPassword: Boolean = false,
    private val singleLine: Boolean = true,
    private val neutralButtonText: String = "",
    private val suggestions: List<String> = emptyList(),
) : DialogFragment() {

    companion object {
        private var confirmCallback: ((String) -> Unit)? = null
        private var dismissCallback: (() -> Unit)? = null
        private var neutralCallback: (() -> Unit)? = null

        fun create(
            title: String,
            initialValue: String = "",
            hint: String = "",
            isPassword: Boolean = false,
            singleLine: Boolean = true,
            onConfirm: (String) -> Unit,
            onDismiss: (() -> Unit)? = null,
            neutralButtonText: String = "",
            onNeutralClick: (() -> Unit)? = null,
            suggestions: List<String> = emptyList(),
        ): M3EditInputDialog {
            confirmCallback = onConfirm
            dismissCallback = onDismiss
            neutralCallback = onNeutralClick
            return M3EditInputDialog(title, initialValue, hint, isPassword, singleLine, neutralButtonText, suggestions)
        }

        fun create(
            titleRes: Int,
            initialValue: String = "",
            hintRes: Int = 0,
            isPassword: Boolean = false,
            singleLine: Boolean = true,
            onConfirm: (String) -> Unit,
            onDismiss: (() -> Unit)? = null,
            neutralButtonText: String = "",
            onNeutralClick: (() -> Unit)? = null,
            suggestions: List<String> = emptyList(),
        ): M3EditInputDialog {
            confirmCallback = onConfirm
            dismissCallback = onDismiss
            neutralCallback = onNeutralClick
            return M3EditInputDialog("", initialValue, "", isPassword, singleLine, neutralButtonText, suggestions).also {
                it.titleRes = titleRes
                it.hintRes = hintRes
            }
        }
    }

    private var titleRes: Int = 0
    private var hintRes: Int = 0

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
                    val resolvedHint = if (hintRes != 0) stringResource(hintRes) else hint
                    LegadoEditInputDialogContent(
                        title = resolvedTitle,
                        initialValue = initialValue,
                        hint = resolvedHint,
                        isPassword = isPassword,
                        singleLine = singleLine,
                        neutralButtonText = neutralButtonText,
                        suggestions = suggestions,
                        onDismiss = { dismiss() },
                        onNeutralClick = { neutralCallback?.invoke() },
                        onConfirm = { value ->
                            confirmCallback?.invoke(value)
                            confirmCallback = null
                            dismissCallback = null
                            neutralCallback = null
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
        neutralCallback = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegadoEditInputDialogContent(
    title: String,
    initialValue: String,
    hint: String,
    isPassword: Boolean,
    singleLine: Boolean,
    neutralButtonText: String,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onNeutralClick: (() -> Unit)?,
    onConfirm: (String) -> Unit,
) {
    val colorScheme = rememberLegadoColorScheme()
    val popupBg = legadoPopupBackgroundColor()
    val popupTextColor = legadoPopupPrimaryTextColor()

    var text by remember { mutableStateOf(initialValue) }
    val hasSuggestions = suggestions.isNotEmpty() && !isPassword

    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = popupBg,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = popupTextColor,
                )
                Spacer(Modifier.height(24.dp))

                if (hasSuggestions) {
                    var expanded by remember { mutableStateOf(false) }
                    val filtered = remember(text, suggestions) {
                        if (text.isEmpty()) suggestions
                        else suggestions.filter { it.contains(text, ignoreCase = true) }
                    }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it; expanded = true },
                            singleLine = singleLine,
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            placeholder = if (hint.isNotEmpty()) {
                                { Text(hint) }
                            } else null,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        )
                        ExposedDropdownMenu(
                            expanded = expanded && filtered.isNotEmpty(),
                            onDismissRequest = { expanded = false },
                        ) {
                            filtered.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        text = item
                                        expanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = singleLine,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = if (hint.isNotEmpty()) {
                            { Text(hint) }
                        } else null,
                        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    )
                }

                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (onNeutralClick != null && neutralButtonText.isNotEmpty()) {
                        TextButton(onClick = onNeutralClick) {
                            Text(neutralButtonText)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onConfirm(text) }) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
        }
    }
}
