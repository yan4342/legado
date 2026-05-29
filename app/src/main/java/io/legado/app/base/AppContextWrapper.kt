package io.legado.app.base

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.util.TypedValue
import androidx.annotation.ColorInt
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.lib.theme.popupBackgroundColor
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.sysConfiguration
import java.util.*


@Suppress("unused")
object AppContextWrapper {

    @SuppressLint("ObsoleteSdkInt")
    fun wrap(context: Context): Context {
        val resources: Resources = context.resources
        val configuration: Configuration = resources.configuration
        val targetLocale = getSetLocale(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(targetLocale)
            configuration.setLocales(LocaleList(targetLocale))
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = targetLocale
        }
        configuration.fontScale = getFontScale(context)
        val configContext = context.createConfigurationContext(configuration)
        return ThemeContextWrapper(configContext)
    }

    fun getFontScale(context: Context): Float {
        var fontScale = context.getPrefInt(PreferKey.fontScale) / 10f
        if (fontScale !in 0.8f..1.6f) {
            fontScale = sysConfiguration.fontScale
        }
        return fontScale
    }

    /**
     * 当前系统语言
     */
    @SuppressLint("ObsoleteSdkInt")
    private fun getSystemLocale(): Locale {
        val locale: Locale
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { //7.0有多语言设置获取顶部的语言
            locale = sysConfiguration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            locale = sysConfiguration.locale
        }
        return locale
    }

    /**
     * 当前App语言
     */
    @SuppressLint("ObsoleteSdkInt")
    private fun getAppLocale(context: Context): Locale {
        val locale: Locale
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locale = context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            locale = context.resources.configuration.locale
        }
        return locale

    }

    /**
     * 当前设置语言
     */
    private fun getSetLocale(context: Context): Locale {
        return when (context.getPrefString(PreferKey.language)) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "tw" -> Locale.TRADITIONAL_CHINESE
            "en" -> Locale.ENGLISH
            else -> getSystemLocale()
        }
    }

    /**
     * 判断App语言和设置语言是否相同
     */
    fun isSameWithSetting(context: Context): Boolean {
        val locale = getAppLocale(context)
        val language = locale.language
        val country = locale.country
        val pfLocale = getSetLocale(context)
        val pfLanguage = pfLocale.language
        val pfCountry = pfLocale.country
        return language == pfLanguage && country == pfCountry
    }

    /**
     * ContextWrapper that intercepts [R.color.background_menu] in XML drawable
     * resolution to use the theme's [popupBackgroundColor].
     *
     * Since [Context.getDrawable] is final, we instead wrap [Resources] and
     * override [Resources.getColor], which is the path used when inflating
     * XML drawables like [R.drawable.bg_popup_menu].
     */
    private class ThemeContextWrapper(base: Context) : ContextWrapper(base) {

        private val themedResources by lazy {
            ThemeResources(super.getResources(), baseContext)
        }

        override fun getResources(): Resources = themedResources
    }

    /**
     * Resources wrapper that replaces [R.color.background_menu] with the
     * theme's [popupBackgroundColor] at drawable-inflation time.
     */
    private class ThemeResources(
        private val delegate: Resources,
        private val context: Context
    ) : Resources(delegate.assets, delegate.displayMetrics, delegate.configuration) {

        @ColorInt
        override fun getColor(id: Int, theme: Theme?): Int {
            if (id == R.color.background_menu) {
                return context.popupBackgroundColor
            }
            return super.getColor(id, theme)
        }

        override fun getColor(id: Int): Int {
            if (id == R.color.background_menu) {
                return context.popupBackgroundColor
            }
            return super.getColor(id)
        }

        override fun getValue(id: Int, outValue: TypedValue, resolveRefs: Boolean) {
            // Intercept @color/background_menu for XML drawable that uses
            // it as a reference (e.g. <solid android:color="@color/background_menu"/>).
            // When resolveRefs=true, the caller wants the actual value, not the reference.
            if (id == R.color.background_menu && resolveRefs) {
                outValue.type = TypedValue.TYPE_INT_COLOR_ARGB8
                outValue.data = context.popupBackgroundColor
                return
            }
            super.getValue(id, outValue, resolveRefs)
        }
    }

}