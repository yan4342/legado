package io.legado.app.utils

import android.app.Dialog
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.forEach
import androidx.fragment.app.DialogFragment
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.filletBackground
import io.legado.app.lib.theme.popupBackgroundColor
import splitties.systemservices.windowManager

fun AlertDialog.applyTint(): AlertDialog {
    window?.setBackgroundDrawable(context.filletBackground)
    val accentColor = ThemeStore.accentColor(context)
    val colorStateList = Selector.colorBuild()
        .setDefaultColor(accentColor)
        .setPressedColor(ColorUtils.darkenColor(accentColor))
        .create()
    if (getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
        getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(colorStateList)
    }
    if (getButton(AlertDialog.BUTTON_POSITIVE) != null) {
        getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(colorStateList)
    }
    if (getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
        getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(colorStateList)
    }
    // 对话框文字颜色适配自定义浮窗背景色
    val dialogTextColor = if (ColorUtils.isColorLight(context.popupBackgroundColor)) {
        0xDE000000.toInt()  // 浅色背景 → 深色文字
    } else {
        0xFFFFFFFF.toInt()  // 深色背景 → 白色文字
    }
    window?.decorView?.post {
        // 列表项文字颜色
        listView?.apply {
            val padding = 8.dpToPx()
            setPadding(padding, padding / 2, padding, padding / 2)
            clipToPadding = false
            forEach {
                it.applyTint(accentColor)
            }
        }
        // 遍历所有 TextView 统一设置文字颜色
        applyTextColorToDialog(window?.decorView, dialogTextColor)
    }
    return this
}

/** 递归遍历 View 树，设置所有非按钮/输入框 TextView 的文字颜色 */
private fun applyTextColorToDialog(view: android.view.View?, textColor: Int) {
    if (view == null) return
    if (view is TextView && view !is Button && view !is EditText) {
        view.setTextColor(textColor)
    }
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            applyTextColorToDialog(view.getChildAt(i), textColor)
        }
    }
}

fun AlertDialog.requestInputMethod() {
    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
}

fun DialogFragment.setLayout(widthMix: Float, heightMix: Float) {
    dialog?.setLayout(widthMix, heightMix)
}

fun Dialog.setLayout(widthMix: Float, heightMix: Float) {
    val dm = context.windowManager.windowSize
    window?.setLayout(
        (dm.widthPixels * widthMix).toInt(),
        (dm.heightPixels * heightMix).toInt()
    )
}

fun DialogFragment.setLayout(width: Int, heightMix: Float) {
    dialog?.setLayout(width, heightMix)
}

fun Dialog.setLayout(width: Int, heightMix: Float) {
    val dm = context.windowManager.windowSize
    window?.setLayout(
        width,
        (dm.heightPixels * heightMix).toInt()
    )
}

fun DialogFragment.setLayout(widthMix: Float, height: Int) {
    dialog?.setLayout(widthMix, height)
}

fun Dialog.setLayout(widthMix: Float, height: Int) {
    val dm = context.windowManager.windowSize
    window?.setLayout(
        (dm.widthPixels * widthMix).toInt(),
        height
    )
}

fun DialogFragment.setLayout(width: Int, height: Int) {
    dialog?.setLayout(width, height)
}

fun Dialog.setLayout(width: Int, height: Int) {
    window?.setLayout(width, height)
}