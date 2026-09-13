package io.legado.app.help.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.DisplayMetrics
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Theme
import io.legado.app.help.DefaultData
import io.legado.app.lib.theme.M3ColorHelper
import io.legado.app.lib.theme.ThemeManager
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.model.BookCover
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getFile
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.hexString
import io.legado.app.utils.postEvent
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.stackBlur
import splitties.init.appCtx
import java.io.File

@Keep
object ThemeConfig {
    private const val THEME_CONFIG_VERSION = 1

    const val configFileName = "themeConfig.json"
    val configFilePath = FileUtils.getPath(appCtx.filesDir, configFileName)

    val configList: ArrayList<Config> by lazy {
        val cList = getConfigs() ?: DefaultData.themeConfigs
        ArrayList(cList)
    }

    fun getTheme() = when {
        AppConfig.isEInkMode -> Theme.EInk
        AppConfig.isNightTheme -> Theme.Dark
        else -> Theme.Light
    }

    fun isDarkTheme(): Boolean {
        return getTheme() == Theme.Dark
    }

    fun applyDayNight(context: Context) {
        applyTheme(context)
        ThemeManager.refresh(context)
        initNightMode()
        BookCover.upDefaultCover()
        postEvent(EventBus.RECREATE, "")
    }

    fun applyDayNightInit(context: Context) {
        migrateIfNeeded(context)
        applyTheme(context)
        ThemeManager.refresh(context)
        initNightMode()
    }

    fun migrateIfNeeded(context: Context) {
        ThemeStore.isConfigured(context, THEME_CONFIG_VERSION)
    }

    private fun initNightMode() {
        val targetMode =
            if (AppConfig.isNightTheme) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        AppCompatDelegate.setDefaultNightMode(targetMode)
    }

    fun getBgImage(context: Context, metrics: DisplayMetrics): Bitmap? {
        val bgCfg = when (getTheme()) {
            Theme.Light -> Pair(
                AppConfigStore.getString(PreferKey.bgImage),
                AppConfigStore.getInt(PreferKey.bgImageBlurring) ?: 0
            )

            Theme.Dark -> Pair(
                AppConfigStore.getString(PreferKey.bgImageN),
                AppConfigStore.getInt(PreferKey.bgImageNBlurring) ?: 0
            )

            else -> null
        } ?: return null
        if (bgCfg.first.isNullOrBlank()) return null
        val bgImage = BitmapUtils
            .decodeBitmap(bgCfg.first!!, metrics.widthPixels, metrics.heightPixels)
        if (bgCfg.second == 0) {
            return bgImage
        }
        return bgImage?.stackBlur(bgCfg.second)
    }

    fun upConfig() {
        getConfigs()?.forEach { config ->
            addConfig(config)
        }
    }

    fun save() {
        val json = GSON.toJson(configList)
        FileUtils.delete(configFilePath)
        FileUtils.createFileIfNotExist(configFilePath).writeText(json)
    }

    fun delConfig(index: Int) {
        configList.removeAt(index)
        save()
    }

    fun addConfig(json: String): Boolean {
        GSON.fromJsonObject<Config>(json.trim { it < ' ' }).getOrNull()
            ?.let {
                if (validateConfig(it)) {
                    addConfig(it)
                    return true
                }
            }
        return false
    }

    fun addConfig(newConfig: Config) {
        if (!validateConfig(newConfig)) {
            return
        }
        configList.forEachIndexed { index, config ->
            if (newConfig.themeName == config.themeName) {
                configList[index] = newConfig
                return
            }
        }
        configList.add(newConfig)
        save()
    }

    private fun validateConfig(config: Config): Boolean {
        try {
            config.primaryColor.toColorInt()
            config.accentColor.toColorInt()
            config.backgroundColor.toColorInt()
            config.bottomBackground.toColorInt()
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun getConfigs(): List<Config>? {
        val configFile = File(configFilePath)
        if (configFile.exists()) {
            kotlin.runCatching {
                val json = configFile.readText()
                return GSON.fromJsonArray<Config>(json).getOrThrow()
            }.onFailure {
                it.printOnDebug()
            }
        }
        return null
    }

    fun applyConfig(context: Context, config: Config) {
        try {
            val primary = Color.parseColor(config.primaryColor)
            val accent = Color.parseColor(config.accentColor)
            val background = Color.parseColor(config.backgroundColor)
            val bBackground = Color.parseColor(config.bottomBackground)
            if (config.isNightTheme) {
                context.putPrefInt(PreferKey.cNPrimary, primary)
                context.putPrefInt(PreferKey.cNAccent, accent)
                context.putPrefInt(PreferKey.cNBackground, background)
                context.putPrefInt(PreferKey.cNBBackground, bBackground)
                config.cardBg?.let { context.putPrefInt(PreferKey.cNCardBg, Color.parseColor(it)) }
                config.popupBg?.let { context.putPrefInt(PreferKey.cNPopupBg, Color.parseColor(it)) }
                config.textAccent?.let { context.putPrefInt(PreferKey.cNTextAccent, Color.parseColor(it)) }
            } else {
                context.putPrefInt(PreferKey.cPrimary, primary)
                context.putPrefInt(PreferKey.cAccent, accent)
                context.putPrefInt(PreferKey.cBackground, background)
                context.putPrefInt(PreferKey.cBBackground, bBackground)
                config.cardBg?.let { context.putPrefInt(PreferKey.cCardBg, Color.parseColor(it)) }
                config.popupBg?.let { context.putPrefInt(PreferKey.cPopupBg, Color.parseColor(it)) }
                config.textAccent?.let { context.putPrefInt(PreferKey.cTextAccent, Color.parseColor(it)) }
            }
            AppConfig.isNightTheme = config.isNightTheme
            applyDayNight(context)
        } catch (e: Exception) {
            AppLog.put("设置主题出错\n$e", e, true)
        }
    }

    fun saveDayTheme(context: Context, name: String) {
        val primary =
            context.getPrefInt(PreferKey.cPrimary, context.getCompatColor(R.color.md_brown_500))
        val accent =
            context.getPrefInt(PreferKey.cAccent, context.getCompatColor(R.color.md_red_600))
        val background =
            context.getPrefInt(PreferKey.cBackground, context.getCompatColor(R.color.md_grey_100))
        val bBackground =
            context.getPrefInt(PreferKey.cBBackground, context.getCompatColor(R.color.md_grey_200))
        val cardBg = context.getPrefInt(PreferKey.cCardBg, 0).takeIf { it != 0 }
        val popupBg = context.getPrefInt(PreferKey.cPopupBg, 0).takeIf { it != 0 }
        val textAccent = context.getPrefInt(PreferKey.cTextAccent, 0).takeIf { it != 0 }
        val config = Config(
            themeName = name,
            isNightTheme = false,
            primaryColor = "#${primary.hexString}",
            accentColor = "#${accent.hexString}",
            backgroundColor = "#${background.hexString}",
            bottomBackground = "#${bBackground.hexString}",
            cardBg = cardBg?.let { "#${it.hexString}" },
            popupBg = popupBg?.let { "#${it.hexString}" },
            textAccent = textAccent?.let { "#${it.hexString}" }
        )
        addConfig(config)
    }

    fun saveNightTheme(context: Context, name: String) {
        val primary =
            context.getPrefInt(
                PreferKey.cNPrimary,
                context.getCompatColor(R.color.md_blue_grey_600)
            )
        val accent =
            context.getPrefInt(
                PreferKey.cNAccent,
                context.getCompatColor(R.color.md_deep_orange_800)
            )
        val background =
            context.getPrefInt(PreferKey.cNBackground, context.getCompatColor(R.color.md_grey_900))
        val bBackground =
            context.getPrefInt(PreferKey.cNBBackground, context.getCompatColor(R.color.md_grey_850))
        val cardBg = context.getPrefInt(PreferKey.cNCardBg, 0).takeIf { it != 0 }
        val popupBg = context.getPrefInt(PreferKey.cNPopupBg, 0).takeIf { it != 0 }
        val textAccent = context.getPrefInt(PreferKey.cNTextAccent, 0).takeIf { it != 0 }
        val config = Config(
            themeName = name,
            isNightTheme = true,
            primaryColor = "#${primary.hexString}",
            accentColor = "#${accent.hexString}",
            backgroundColor = "#${background.hexString}",
            bottomBackground = "#${bBackground.hexString}",
            cardBg = cardBg?.let { "#${it.hexString}" },
            popupBg = popupBg?.let { "#${it.hexString}" },
            textAccent = textAccent?.let { "#${it.hexString}" }
        )
        addConfig(config)
    }

    /**
     * 更新主题
     */
    fun applyTheme(context: Context) = with(context) {
        when {
            AppConfig.isEInkMode -> {
                ThemeStore.editTheme(this)
                    .primaryColor(Color.WHITE)
                    .accentColor(Color.BLACK)
                    .backgroundColor(Color.WHITE)
                    .bottomBackground(Color.WHITE)
                    .apply()
                val eink = M3ColorHelper.computeEInkTokens()
                ThemeStore.editTheme(this)
                    .colorOnPrimary(eink.onPrimary)
                    .colorPrimaryContainer(eink.primaryContainer)
                    .colorOnPrimaryContainer(eink.onPrimaryContainer)
                    .colorSecondaryContainer(eink.secondaryContainer)
                    .colorOnSecondaryContainer(eink.onSecondaryContainer)
                    .colorSurface(eink.surface)
                    .colorOnSurface(eink.onSurface)
                    .colorSurfaceVariant(eink.surfaceVariant)
                    .colorOnSurfaceVariant(eink.onSurfaceVariant)
                    .colorSurfaceContainer(eink.surfaceContainer)
                    .apply()
            }

            AppConfig.isNightTheme -> {
                val primary =
                    getPrefInt(PreferKey.cNPrimary, getCompatColor(R.color.md_blue_grey_600))
                val accent =
                    getPrefInt(PreferKey.cNAccent, getCompatColor(R.color.md_deep_orange_800))
                var background =
                    getPrefInt(PreferKey.cNBackground, getCompatColor(R.color.md_grey_900))
                if (ColorUtils.isColorLight(background)) {
                    background = getCompatColor(R.color.md_grey_900)
                    putPrefInt(PreferKey.cNBackground, background)
                }
                val bBackground =
                    getPrefInt(PreferKey.cNBBackground, getCompatColor(R.color.md_grey_850))
                val pColor = ColorUtils.withAlpha(primary, 1f)
                val aColor = ColorUtils.withAlpha(accent, 1f)
                val bgColor = ColorUtils.withAlpha(background, 1f)
                ThemeStore.editTheme(this)
                    .primaryColor(pColor)
                    .accentColor(aColor)
                    .backgroundColor(bgColor)
                    .bottomBackground(ColorUtils.withAlpha(bBackground, 1f))
                    .apply()
                val m3 = M3ColorHelper.computeTokens(pColor, aColor, bgColor, true)
                ThemeStore.editTheme(this)
                    .colorOnPrimary(m3.onPrimary)
                    .colorPrimaryContainer(m3.primaryContainer)
                    .colorOnPrimaryContainer(m3.onPrimaryContainer)
                    .colorSecondaryContainer(m3.secondaryContainer)
                    .colorOnSecondaryContainer(m3.onSecondaryContainer)
                    .colorSurface(m3.surface)
                    .colorOnSurface(m3.onSurface)
                    .colorSurfaceVariant(m3.surfaceVariant)
                    .colorOnSurfaceVariant(m3.onSurfaceVariant)
                    .colorSurfaceContainer(m3.surfaceContainer)
                    .apply()
            }

            else -> {
                val primary =
                    getPrefInt(PreferKey.cPrimary, getCompatColor(R.color.md_brown_500))
                val accent =
                    getPrefInt(PreferKey.cAccent, getCompatColor(R.color.md_red_600))
                var background =
                    getPrefInt(PreferKey.cBackground, getCompatColor(R.color.md_grey_100))
                if (!ColorUtils.isColorLight(background)) {
                    background = getCompatColor(R.color.md_grey_100)
                    putPrefInt(PreferKey.cBackground, background)
                }
                val bBackground =
                    getPrefInt(PreferKey.cBBackground, getCompatColor(R.color.md_grey_200))
                val pColor = ColorUtils.withAlpha(primary, 1f)
                val aColor = ColorUtils.withAlpha(accent, 1f)
                val bgColor = ColorUtils.withAlpha(background, 1f)
                ThemeStore.editTheme(this)
                    .primaryColor(pColor)
                    .accentColor(aColor)
                    .backgroundColor(bgColor)
                    .bottomBackground(ColorUtils.withAlpha(bBackground, 1f))
                    .apply()
                val m3 = M3ColorHelper.computeTokens(pColor, aColor, bgColor, false)
                ThemeStore.editTheme(this)
                    .colorOnPrimary(m3.onPrimary)
                    .colorPrimaryContainer(m3.primaryContainer)
                    .colorOnPrimaryContainer(m3.onPrimaryContainer)
                    .colorSecondaryContainer(m3.secondaryContainer)
                    .colorOnSecondaryContainer(m3.onSecondaryContainer)
                    .colorSurface(m3.surface)
                    .colorOnSurface(m3.onSurface)
                    .colorSurfaceVariant(m3.surfaceVariant)
                    .colorOnSurfaceVariant(m3.onSurfaceVariant)
                    .colorSurfaceContainer(m3.surfaceContainer)
                    .apply()
            }
        }
    }

    fun clearBg() {
        val bgImagePath = AppConfigStore.getString(PreferKey.bgImage)
        appCtx.externalFiles.getFile(PreferKey.bgImage).listFiles()?.forEach {
            if (it.absolutePath != bgImagePath) {
                it.delete()
            }
        }
        val bgImageNPath = AppConfigStore.getString(PreferKey.bgImageN)
        appCtx.externalFiles.getFile(PreferKey.bgImageN).listFiles()?.forEach {
            if (it.absolutePath != bgImageNPath) {
                it.delete()
            }
        }
    }

    @Keep
    data class Config(
        var themeName: String,
        var isNightTheme: Boolean,
        var primaryColor: String,
        var accentColor: String,
        var backgroundColor: String,
        var bottomBackground: String,
        var cardBg: String? = null,
        var popupBg: String? = null,
        var textAccent: String? = null
    ) {

        override fun hashCode(): Int {
            return GSON.toJson(this).hashCode()
        }

        override fun equals(other: Any?): Boolean {
            other ?: return false
            if (other is Config) {
                return other.themeName == themeName
                        && other.isNightTheme == isNightTheme
                        && other.primaryColor == primaryColor
                        && other.accentColor == accentColor
                        && other.backgroundColor == backgroundColor
                        && other.bottomBackground == bottomBackground
                        && other.cardBg == cardBg
                        && other.popupBg == popupBg
                        && other.textAccent == textAccent
            }
            return false
        }

    }

}