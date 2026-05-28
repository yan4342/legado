package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.PopupWindow
import androidx.appcompat.widget.AppCompatSpinner
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.applyTint

class RoundedSpinner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.spinnerStyle
) : AppCompatSpinner(context, attrs, defStyleAttr) {

    init {
        if (!isInEditMode) {
            applyTint(context.accentColor)
        }
    }

    override fun performClick(): Boolean {
        val result = super.performClick()
        post { applyRoundedCornersToPopup() }
        return result
    }

    private fun applyRoundedCornersToPopup() {
        findPopupWindow()?.let { clipPopupToRoundedCorners(it) }
    }

    private fun findPopupWindow(): PopupWindow? {
        return try {
            val spinnerPopup = AppCompatSpinner::class.java
                .getDeclaredField("mPopup").apply { isAccessible = true }
                .get(this)
            val listPopupWindow = spinnerPopup.javaClass
                .getDeclaredField("mPopup").apply { isAccessible = true }
                .get(spinnerPopup)
            listPopupWindow.javaClass
                .getDeclaredField("mPopup").apply { isAccessible = true }
                .get(listPopupWindow) as? PopupWindow
        } catch (_: Exception) {
            null
        }
    }

    private fun clipPopupToRoundedCorners(popupWindow: PopupWindow) {
        val decorView = popupWindow.contentView?.parent as? View ?: return
        val radius = resources.displayMetrics.density * 12f
        decorView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        decorView.clipToOutline = true
    }
}
