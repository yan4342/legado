package io.legado.app.utils

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.AbsListView
import android.widget.CheckedTextView
import android.widget.TextView
import io.legado.app.lib.theme.popupBackgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.ColorUtils

internal object PopupThemeApplier {

    private const val THEMED_TAG = "popup_theme_applied"
    private const val MAX_PARENT_DEPTH = 6

    /**
     * @return true if at least one popup window was themed
     */
    fun apply(context: Context): Boolean {
        var themed = false
        try {
            val wmClass = Class.forName("android.view.WindowManagerGlobal")
            val instance = wmClass.getDeclaredMethod("getInstance").invoke(null)
            val viewsField = wmClass.getDeclaredField("mViews").apply { isAccessible = true }
            val views = viewsField.get(instance) as? List<*> ?: return false
            val cardColor = context.popupBackgroundColor
            val textColor = if (ColorUtils.isColorLight(cardColor)) {
                0xDE000000.toInt()  // 深色文字用于浅色背景
            } else {
                0xFFFFFFFF.toInt()  // 白色文字用于深色背景
            }
            val accentColor = context.primaryColor
            val radius = context.resources.displayMetrics.density * 12f

            for (view in views) {
                if (view !is ViewGroup) continue
                val lp = view.layoutParams as? android.view.WindowManager.LayoutParams ?: continue
                val type = lp.type
                if (type == android.view.WindowManager.LayoutParams.TYPE_APPLICATION
                    || type == android.view.WindowManager.LayoutParams.TYPE_BASE_APPLICATION
                ) continue
                if (isActionMode(view)) continue

                val bgView = view.getChildAt(0)
                val contentView = (bgView as? ViewGroup)?.getChildAt(0)
                if (contentView?.tag == THEMED_TAG) continue
                if (bgView?.tag == THEMED_TAG) continue
                if (view.tag == THEMED_TAG) continue

                view.tag = THEMED_TAG
                applyTransparentLayer(view)
                bgView?.let { applyTransparentLayer(it) }
                val animatedLayer = findPopupContentLayer(contentView ?: bgView ?: view)
                animatedLayer?.let {
                    applyRoundedLayer(it, cardColor, radius)
                    applyTextColorToPopup(it, textColor, accentColor)
                }
                themed = true
            }
        } catch (_: Exception) {
        }
        return themed
    }

    fun applyMenuItemParents(context: Context, parent: View?) {
        val cardColor = context.popupBackgroundColor
        val textColor = if (ColorUtils.isColorLight(cardColor)) {
            0xDE000000.toInt()
        } else {
            0xFFFFFFFF.toInt()
        }
        val accentColor = context.primaryColor
        val radius = context.resources.displayMetrics.density * 12f
        var view = parent
        var depth = 0
        var contentApplied = false
        while (view != null && depth < MAX_PARENT_DEPTH) {
            if (!contentApplied && isPopupContentLayer(view)) {
                applyRoundedLayer(view, cardColor, radius)
                applyTextColorToPopup(view, textColor, accentColor)
                contentApplied = true
            } else if (isPopupContainerLayer(view)) {
                applyTransparentLayer(view)
            }
            view = view.parent as? View
            depth++
        }
    }

    private fun applyRoundedLayer(view: View, color: Int, radius: Float) {
        if (view is AbsListView) {
            view.cacheColorHint = Color.TRANSPARENT
        }
        view.background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, radius)
            }
        }
        view.clipToOutline = true
    }

    private fun applyTransparentLayer(view: View) {
        view.background = ColorDrawable(Color.TRANSPARENT)
        view.clipToOutline = false
    }

    /**
     * 遍历弹出层中的所有 TextView，统一设置文字颜色以适配自定义浮窗背景。
     * 同时将 CheckedTextView 的勾选标记着色为 [accentColor]。
     * 延迟执行以等待系统填充列表项。
     */
    private fun applyTextColorToPopup(popupContentView: View, textColor: Int, accentColor: Int) {
        popupContentView.post {
            applyTextColorRecursive(popupContentView, textColor, accentColor)
        }
    }

    private fun applyTextColorRecursive(view: View, textColor: Int, accentColor: Int) {
        if (view is CheckedTextView) {
            view.checkMarkTintList = ColorStateList.valueOf(accentColor)
        }
        if (view is TextView) {
            view.setTextColor(textColor)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyTextColorRecursive(view.getChildAt(i), textColor, accentColor)
            }
        }
    }

    private fun findPopupContentLayer(view: View?): View? {
        if (view == null) return null
        if (isPopupContentLayer(view)) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findPopupContentLayer(view.getChildAt(i))?.let { return it }
            }
        }
        return view
    }

    private fun isPopupContentLayer(view: View): Boolean {
        val name = view.javaClass.name
        return view is AbsListView
                || name.contains("DropDownListView")
                || name.contains("ExpandedMenuView")
    }

    private fun isPopupContainerLayer(view: View): Boolean {
        val name = view.javaClass.name
        return name.contains("Popup")
                || name.contains("FrameLayout")
    }

    private fun isActionMode(view: View): Boolean {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val name = child.javaClass.name
                if (name.contains("ActionBarContextView")
                    || name.contains("ActionMode")
                    || name.contains("ActionMenuView")
                    || name.contains("FloatingToolbar")
                ) return true
                if (child is ViewGroup && isActionMode(child)) return true
            }
        }
        return false
    }
}
