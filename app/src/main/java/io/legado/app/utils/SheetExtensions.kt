package io.legado.app.utils

import androidx.compose.runtime.Composable
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import io.legado.app.ui.common.compose.M3EditInputDialog
import io.legado.app.ui.widget.dialog.LegadoCrashLogContent
import io.legado.app.ui.widget.dialog.LegadoLogListContent
import io.legado.app.ui.widget.dialog.LegadoMarkdownContent
import io.legado.app.ui.widget.dialog.LegadoSheetDialog
import io.legado.app.ui.widget.dialog.LegadoTextContent
import io.legado.app.ui.widget.dialog.TextMode

// region Generic

fun FragmentActivity.showLegadoSheet(
    content: @Composable (requestDismiss: () -> Unit) -> Unit
) {
    LegadoSheetDialog.create(content).show(supportFragmentManager, LegadoSheetDialog.TAG)
}

fun Fragment.showLegadoSheet(
    content: @Composable (requestDismiss: () -> Unit) -> Unit
) {
    LegadoSheetDialog.create(content).show(childFragmentManager, LegadoSheetDialog.TAG)
}

// endregion

// region Markdown

fun FragmentActivity.showMarkdownSheet(title: String, content: String) {
    showLegadoSheet { requestDismiss ->
        LegadoMarkdownContent(
            title = title,
            markdownContent = content,
            onDismiss = requestDismiss,
        )
    }
}

fun Fragment.showMarkdownSheet(title: String, content: String) {
    showLegadoSheet { requestDismiss ->
        LegadoMarkdownContent(
            title = title,
            markdownContent = content,
            onDismiss = requestDismiss,
        )
    }
}

// endregion

// region Text

fun FragmentActivity.showTextSheet(
    title: String,
    content: String?,
    mode: TextMode = TextMode.TEXT,
    autoCloseMs: Long = 0,
) {
    showLegadoSheet { requestDismiss ->
        LegadoTextContent(
            title = title,
            content = content ?: "",
            mode = mode,
            autoCloseMs = autoCloseMs,
            onDismiss = requestDismiss,
        )
    }
}

fun Fragment.showTextSheet(
    title: String,
    content: String?,
    mode: TextMode = TextMode.TEXT,
    autoCloseMs: Long = 0,
) {
    showLegadoSheet { requestDismiss ->
        LegadoTextContent(
            title = title,
            content = content ?: "",
            mode = mode,
            autoCloseMs = autoCloseMs,
            onDismiss = requestDismiss,
        )
    }
}

// endregion

// region Log

fun FragmentActivity.showLogSheet() {
    showLegadoSheet { requestDismiss ->
        LegadoLogListContent(onDismiss = requestDismiss)
    }
}

fun Fragment.showLogSheet() {
    showLegadoSheet { requestDismiss ->
        LegadoLogListContent(onDismiss = requestDismiss)
    }
}

// endregion

// region Crash Log

fun FragmentActivity.showCrashLogSheet() {
    showLegadoSheet { requestDismiss ->
        LegadoCrashLogContent(onDismiss = requestDismiss)
    }
}

fun Fragment.showCrashLogSheet() {
    showLegadoSheet { requestDismiss ->
        LegadoCrashLogContent(onDismiss = requestDismiss)
    }
}

// endregion

// region M3 Edit Input Dialog

/**
 * 显示 MD3 风格输入弹窗。替代旧的 [io.legado.app.databinding.DialogEditTextBinding] 模式。
 */
fun FragmentActivity.showM3EditDialog(
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
) {
    M3EditInputDialog.create(
        title = title,
        initialValue = initialValue,
        hint = hint,
        isPassword = isPassword,
        singleLine = singleLine,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        neutralButtonText = neutralButtonText,
        onNeutralClick = onNeutralClick,
        suggestions = suggestions,
    ).show(supportFragmentManager, "M3EditInputDialog")
}

fun FragmentActivity.showM3EditDialog(
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
) {
    M3EditInputDialog.create(
        titleRes = titleRes,
        initialValue = initialValue,
        hintRes = hintRes,
        isPassword = isPassword,
        singleLine = singleLine,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        neutralButtonText = neutralButtonText,
        onNeutralClick = onNeutralClick,
        suggestions = suggestions,
    ).show(supportFragmentManager, "M3EditInputDialog")
}

fun Fragment.showM3EditDialog(
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
) {
    M3EditInputDialog.create(
        title = title,
        initialValue = initialValue,
        hint = hint,
        isPassword = isPassword,
        singleLine = singleLine,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        neutralButtonText = neutralButtonText,
        onNeutralClick = onNeutralClick,
        suggestions = suggestions,
    ).show(childFragmentManager, "M3EditInputDialog")
}

fun Fragment.showM3EditDialog(
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
) {
    M3EditInputDialog.create(
        titleRes = titleRes,
        initialValue = initialValue,
        hintRes = hintRes,
        isPassword = isPassword,
        singleLine = singleLine,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        neutralButtonText = neutralButtonText,
        onNeutralClick = onNeutralClick,
        suggestions = suggestions,
    ).show(childFragmentManager, "M3EditInputDialog")
}

// endregion
