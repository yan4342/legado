@file:Suppress("unused")

package io.legado.app.lib.theme

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefInt

/**
 * @author Karim Abou Zeid (kabouzeid)
 */
@ColorInt
fun Context.getPrimaryTextColor(dark: Boolean): Int {
    return if (dark) {
        ContextCompat.getColor(this, R.color.md_light_primary_text)
    } else {
        ContextCompat.getColor(this, R.color.md_dark_primary_text)
    }
}

@ColorInt
fun Context.getSecondaryTextColor(dark: Boolean): Int {
    return if (dark) {
        ContextCompat.getColor(this, R.color.md_light_secondary)
    } else {
        ContextCompat.getColor(this, R.color.md_dark_primary_text)
    }
}

@ColorInt
fun Context.getPrimaryDisabledTextColor(dark: Boolean): Int {
    return if (dark) {
        ContextCompat.getColor(this, R.color.md_light_disabled)
    } else {
        ContextCompat.getColor(this, R.color.md_dark_disabled)
    }
}

@ColorInt
fun Context.getSecondaryDisabledTextColor(dark: Boolean): Int {
    return if (dark) {
        ContextCompat.getColor(
            this,
            androidx.appcompat.R.color.secondary_text_disabled_material_light
        )
    } else {
        ContextCompat.getColor(
            this,
            androidx.appcompat.R.color.secondary_text_disabled_material_dark
        )
    }
}

val Context.primaryColor: Int
    get() = ThemeStore.primaryColor(this)

val Context.primaryColorDark: Int
    get() = ThemeStore.primaryColorDark(this)

val Context.accentColor: Int
    get() = ThemeStore.accentColor(this)

val Context.backgroundColor: Int
    get() = ThemeStore.backgroundColor(this)

val Context.bottomBackground: Int
    get() = ThemeStore.bottomBackground(this)

val Context.colorOnPrimary: Int
    get() = ThemeStore.colorOnPrimary(this)

val Context.colorPrimaryContainer: Int
    get() = ThemeStore.colorPrimaryContainer(this)

val Context.colorOnPrimaryContainer: Int
    get() = ThemeStore.colorOnPrimaryContainer(this)

val Context.colorSecondaryContainer: Int
    get() = ThemeStore.colorSecondaryContainer(this)

val Context.colorOnSecondaryContainer: Int
    get() = ThemeStore.colorOnSecondaryContainer(this)

val Context.colorSurface: Int
    get() = ThemeStore.colorSurface(this)

val Context.colorOnSurface: Int
    get() = ThemeStore.colorOnSurface(this)

val Context.colorSurfaceVariant: Int
    get() = ThemeStore.colorSurfaceVariant(this)

val Context.colorOnSurfaceVariant: Int
    get() = ThemeStore.colorOnSurfaceVariant(this)

val Context.colorSurfaceContainer: Int
    get() = ThemeStore.colorSurfaceContainer(this)

/**
 * 卡片背景色：优先读取用户自定义 cCardBg/cNCardBg，回退到 colorSurfaceContainer
 */
val Context.cardBackgroundColor: Int
    get() {
        val prefKey = if (isDarkTheme) PreferKey.cNCardBg else PreferKey.cCardBg
        val savedColor = getPrefInt(prefKey)
        return if (savedColor != 0) savedColor else colorSurfaceContainer
    }

/**
 * 菜单/弹出层背景色：优先读取用户自定义卡片色，回退到 backgroundColor
 */
val Context.menuBackgroundColor: Int
    get() {
        val prefKey = if (isDarkTheme) PreferKey.cNCardBg else PreferKey.cCardBg
        val savedColor = getPrefInt(prefKey)
        return if (savedColor != 0) savedColor else backgroundColor
    }

/**
 * 图标着色色：使用 colorOnSurface（与文字主色一致）
 */
/**
 * 浮窗/弹出菜单背景色：优先读取用户自定义 cPopupBg/cNPopupBg，回退到 cardBackgroundColor
 */
val Context.popupBackgroundColor: Int
    get() {
        val prefKey = if (isDarkTheme) PreferKey.cNPopupBg else PreferKey.cPopupBg
        val savedColor = getPrefInt(prefKey)
        return if (savedColor != 0) savedColor else cardBackgroundColor
    }

val Context.iconTintColor: Int
    get() = colorOnSurface

val Context.primaryTextColor: Int
    get() = getPrimaryTextColor(isDarkTheme)

val Context.secondaryTextColor: Int
    get() = getSecondaryTextColor(isDarkTheme)

val Context.primaryDisabledTextColor: Int
    get() = getPrimaryDisabledTextColor(isDarkTheme)

val Context.secondaryDisabledTextColor: Int
    get() = getSecondaryDisabledTextColor(isDarkTheme)

val Fragment.primaryColor: Int
    get() = ThemeStore.primaryColor(requireContext())

val Fragment.primaryColorDark: Int
    get() = ThemeStore.primaryColorDark(requireContext())

val Fragment.accentColor: Int
    get() = ThemeStore.accentColor(requireContext())

val Fragment.backgroundColor: Int
    get() = ThemeStore.backgroundColor(requireContext())

val Fragment.bottomBackground: Int
    get() = ThemeStore.bottomBackground(requireContext())

val Fragment.colorOnPrimary: Int
    get() = ThemeStore.colorOnPrimary(requireContext())

val Fragment.colorPrimaryContainer: Int
    get() = ThemeStore.colorPrimaryContainer(requireContext())

val Fragment.colorOnPrimaryContainer: Int
    get() = ThemeStore.colorOnPrimaryContainer(requireContext())

val Fragment.colorSecondaryContainer: Int
    get() = ThemeStore.colorSecondaryContainer(requireContext())

val Fragment.colorOnSecondaryContainer: Int
    get() = ThemeStore.colorOnSecondaryContainer(requireContext())

val Fragment.colorSurface: Int
    get() = ThemeStore.colorSurface(requireContext())

val Fragment.colorOnSurface: Int
    get() = ThemeStore.colorOnSurface(requireContext())

val Fragment.colorSurfaceVariant: Int
    get() = ThemeStore.colorSurfaceVariant(requireContext())

val Fragment.colorOnSurfaceVariant: Int
    get() = ThemeStore.colorOnSurfaceVariant(requireContext())

val Fragment.colorSurfaceContainer: Int
    get() = ThemeStore.colorSurfaceContainer(requireContext())

val Fragment.cardBackgroundColor: Int
    get() = requireContext().cardBackgroundColor

val Fragment.menuBackgroundColor: Int
    get() = requireContext().menuBackgroundColor

val Fragment.popupBackgroundColor: Int
    get() = requireContext().popupBackgroundColor

val Fragment.iconTintColor: Int
    get() = requireContext().iconTintColor

val Fragment.primaryTextColor: Int
    get() = requireContext().getPrimaryTextColor(isDarkTheme)

val Fragment.secondaryTextColor: Int
    get() = requireContext().getSecondaryTextColor(isDarkTheme)

val Fragment.primaryDisabledTextColor: Int
    get() = requireContext().getPrimaryDisabledTextColor(isDarkTheme)

val Fragment.secondaryDisabledTextColor: Int
    get() = requireContext().getSecondaryDisabledTextColor(isDarkTheme)

val Context.buttonDisabledColor: Int
    get() = if (isDarkTheme) {
        ContextCompat.getColor(this, R.color.md_dark_disabled)
    } else {
        ContextCompat.getColor(this, R.color.md_light_disabled)
    }

val Context.isDarkTheme: Boolean
    get() = ColorUtils.isColorLight(ThemeStore.primaryColor(this))

val Fragment.isDarkTheme: Boolean
    get() = requireContext().isDarkTheme

val Context.elevation: Float
    @SuppressLint("PrivateResource")
    get() {
        return if (AppConfig.elevation < 0) {
            ThemeUtils.resolveFloat(
                this,
                android.R.attr.elevation,
                resources.getDimension(com.google.android.material.R.dimen.design_appbar_elevation)
            )
        } else {
            AppConfig.elevation.toFloat().dpToPx()
        }
    }

val Context.filletBackground: GradientDrawable
    get() {
        val background = GradientDrawable()
        background.cornerRadius = resources.getDimension(R.dimen.dialog_corner_radius)
        background.setColor(popupBackgroundColor)
        return background
    }

val Context.bottomSheetBackground: android.graphics.drawable.Drawable
    get() {
        if (AppConfig.isEInkMode) {
            return ContextCompat.getDrawable(this, R.drawable.bg_eink_border_dialog)!!
        }
        val background = GradientDrawable()
        val cornerRadius = resources.getDimension(R.dimen.dialog_corner_radius)
        background.cornerRadii = floatArrayOf(
            cornerRadius, cornerRadius,
            cornerRadius, cornerRadius,
            0f, 0f,
            0f, 0f
        )
        background.setColor(bottomBackground)
        return background
    }