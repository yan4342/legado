package io.legado.app.lib.prefs

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.view.forEach
import androidx.preference.ListPreferenceDialogFragmentCompat
import androidx.preference.PreferenceDialogFragmentCompat
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.filletBackground
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.applyTint
import io.legado.app.utils.dpToPx

class ListPreferenceDialog : ListPreferenceDialogFragmentCompat() {

    companion object {

        fun newInstance(key: String?): ListPreferenceDialog {
            val fragment = ListPreferenceDialog()
            val b = Bundle(1)
            b.putString(PreferenceDialogFragmentCompat.ARG_KEY, key)
            fragment.arguments = b
            return fragment
        }

    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawable(requireContext().filletBackground)
        dialog.window?.decorView?.post {
            (dialog as AlertDialog).run {
                val colorStateList = Selector.colorBuild()
                    .setDefaultColor(accentColor)
                    .setPressedColor(ColorUtils.darkenColor(accentColor))
                    .create()
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(colorStateList)
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(colorStateList)
                getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(colorStateList)
                listView?.apply {
                    val padding = 8.dpToPx()
                    setPadding(padding, padding / 2, padding, padding / 2)
                    clipToPadding = false
                    forEach {
                        it.applyTint(accentColor)
                    }
                }
            }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        if (AppConfig.isEInkMode) {
            dialog?.window?.let {
                it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                val attr = it.attributes
                attr.dimAmount = 0.0f
                attr.windowAnimations = 0
                it.attributes = attr
                it.setBackgroundDrawableResource(R.color.transparent)
                when (attr.gravity) {
                    Gravity.TOP -> it.decorView.setBackgroundResource(R.drawable.bg_eink_border_bottom)
                    Gravity.BOTTOM -> it.decorView.setBackgroundResource(R.drawable.bg_eink_border_top)
                    else -> {
                        val padding = 2.dpToPx();
                        it.decorView.setPadding(padding, padding, padding, padding)
                        it.decorView.setBackgroundResource(R.drawable.bg_eink_border_dialog)
                    }
                }
            }
        }
    }

}