package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.popupBackgroundColor

/**
 * 主题化的 PopupWindow 基类。
 * 自动应用 [popupBackgroundColor] 主题浮窗色并设置圆角裁剪，
 * 兼容 E-Ink 模式降级。
 *
 * 关键设计：PopupWindow 外壳保持透明，主题色设置在 contentView 上，
 * 避免外层背景先显示、内层内容再播放弹出动画。
 *
 * 使用方式：
 * ```
 * class MyPopup(context: Context) : ThemedPopupWindow(context) {
 *     private val binding = MyLayoutBinding.inflate(LayoutInflater.from(context))
 *     init { setThemedContentView(binding.root) }
 * }
 * ```
 */
open class ThemedPopupWindow(
    private val context: Context,
    private val cornerRadiusDp: Float = 16f
) : PopupWindow(
    ViewGroup.LayoutParams.WRAP_CONTENT,
    ViewGroup.LayoutParams.WRAP_CONTENT
) {

    private val radiusPx: Float = context.resources.displayMetrics.density * cornerRadiusDp

    init {
        isTouchable = true
        isOutsideTouchable = true
        isFocusable = true
        // Keep the PopupWindow shell transparent to avoid a static background before animation.
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    /**
     * 展示后标记 PopupBackgroundView 和 PopupDecorView，
     * 防止 BaseActivity.applyPopupBackgroundColor() 二次覆盖导致闪烁。
     * PopupWindow 结构：PopupDecorView → PopupBackgroundView → contentView
     */
    override fun showAtLocation(parent: View?, gravity: Int, x: Int, y: Int) {
        super.showAtLocation(parent, gravity, x, y)
        contentView?.post {
            (contentView?.parent as? View)?.tag = THEMED_TAG             // PopupBackgroundView
            (contentView?.parent?.parent as? View)?.tag = THEMED_TAG     // PopupDecorView
        }
    }

    override fun showAsDropDown(anchor: View?, xoff: Int, yoff: Int, gravity: Int) {
        super.showAsDropDown(anchor, xoff, yoff, gravity)
        contentView?.post {
            (contentView?.parent as? View)?.tag = THEMED_TAG
            (contentView?.parent?.parent as? View)?.tag = THEMED_TAG
        }
    }

    /**
     * 设置内容视图并应用圆角裁剪。
     * 替代直接调用 [setContentView]。
     */
    fun setThemedContentView(view: View) {
        view.background = createThemedBackground()
        super.setContentView(view)
        applyRoundedClip(view)
    }

    /** 创建 PopupWindow 自身背景（带主题色 + 圆角） */
    private fun createThemedBackground(): Drawable {
        if (AppConfig.isEInkMode) {
            return ContextCompat.getDrawable(context, R.drawable.bg_eink_border_dialog)!!
        }
        return GradientDrawable().apply {
            setColor(context.popupBackgroundColor)
            cornerRadius = radiusPx
        }
    }

    /** 对 contentView 设置 clipToOutline，确保内容不溢出圆角区域 */
    private fun applyRoundedClip(view: View) {
        view.clipToOutline = true
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
        // 标记为已主题化，防止 BaseActivity.applyPopupBackgroundColor() 二次覆盖
        view.tag = THEMED_TAG
    }

    companion object {
        /** 与 [io.legado.app.base.BaseActivity] 中 POPUP_THEME_TAG 一致，用于跳过已主题化的浮窗 */
        internal const val THEMED_TAG = "popup_theme_applied"
    }
}
