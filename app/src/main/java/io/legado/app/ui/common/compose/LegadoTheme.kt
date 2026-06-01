package io.legado.app.ui.common.compose

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.M3ColorHelper
import io.legado.app.lib.theme.cardBackgroundColor
import io.legado.app.lib.theme.popupBackgroundColor
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.ColorUtils

/**
 * 从 ThemeStore 读取当前主题颜色，构建 M3 ColorScheme。
 * Activity recreate 后 Compose 自动拿到新颜色。
 */
@Composable
fun rememberLegadoColorScheme(): ColorScheme {
    val context = LocalContext.current
    val isDark = AppConfig.isNightTheme

    return if (AppConfig.isEInkMode) {
        val tokens = M3ColorHelper.computeEInkTokens()
        lightColorScheme(
            primary = Color(ThemeStore.primaryColor(context)),
            onPrimary = Color(tokens.onPrimary),
            primaryContainer = Color(tokens.primaryContainer),
            onPrimaryContainer = Color(tokens.onPrimaryContainer),
            secondary = Color(ThemeStore.accentColor(context)),
            secondaryContainer = Color(tokens.secondaryContainer),
            onSecondaryContainer = Color(tokens.onSecondaryContainer),
            surface = Color(tokens.surface),
            onSurface = Color(tokens.onSurface),
            surfaceVariant = Color(tokens.surfaceVariant),
            onSurfaceVariant = Color(tokens.onSurfaceVariant),
            background = Color(tokens.surface),
            surfaceContainerLow = Color(tokens.surfaceContainer),
        )
    } else if (isDark) {
        darkColorScheme(
            primary = Color(ThemeStore.primaryColor(context)),
            onPrimary = Color(ThemeStore.colorOnPrimary(context)),
            primaryContainer = Color(ThemeStore.colorPrimaryContainer(context)),
            onPrimaryContainer = Color(ThemeStore.colorOnPrimaryContainer(context)),
            secondary = Color(ThemeStore.accentColor(context)),
            secondaryContainer = Color(ThemeStore.colorSecondaryContainer(context)),
            onSecondaryContainer = Color(ThemeStore.colorOnSecondaryContainer(context)),
            surface = Color(ThemeStore.colorSurface(context)),
            onSurface = Color(ThemeStore.colorOnSurface(context)),
            surfaceVariant = Color(ThemeStore.colorSurfaceVariant(context)),
            onSurfaceVariant = Color(ThemeStore.colorOnSurfaceVariant(context)),
            background = Color(ThemeStore.backgroundColor(context)),
            surfaceContainerLow = Color(ThemeStore.colorSurfaceContainer(context)),
        )
    } else {
        lightColorScheme(
            primary = Color(ThemeStore.primaryColor(context)),
            onPrimary = Color(ThemeStore.colorOnPrimary(context)),
            primaryContainer = Color(ThemeStore.colorPrimaryContainer(context)),
            onPrimaryContainer = Color(ThemeStore.colorOnPrimaryContainer(context)),
            secondary = Color(ThemeStore.accentColor(context)),
            secondaryContainer = Color(ThemeStore.colorSecondaryContainer(context)),
            onSecondaryContainer = Color(ThemeStore.colorOnSecondaryContainer(context)),
            surface = Color(ThemeStore.colorSurface(context)),
            onSurface = Color(ThemeStore.colorOnSurface(context)),
            surfaceVariant = Color(ThemeStore.colorSurfaceVariant(context)),
            onSurfaceVariant = Color(ThemeStore.colorOnSurfaceVariant(context)),
            background = Color(ThemeStore.backgroundColor(context)),
            surfaceContainerLow = Color(ThemeStore.colorSurfaceContainer(context)),
        )
    }
}

/**
 * 用户自定义卡片背景色，优先读 PreferKey.cCardBg/cNCardBg，回退 surfaceContainerLow
 */
@Composable
fun legadoCardBackgroundColor(): Color {
    val context = LocalContext.current
    return Color(context.cardBackgroundColor)
}

/**
 * 用户自定义弹窗背景色，优先读 PreferKey.cPopupBg/cNPopupBg，回退 cardBackground
 */
@Composable
fun legadoPopupBackgroundColor(): Color {
    val context = LocalContext.current
    return Color(context.popupBackgroundColor)
}

/**
 * 浮窗文字主色，根据 popupBackgroundColor 明暗自动选择。
 * 浅色背景 → 深色文字，深色背景 → 白色文字。
 */
@Composable
fun legadoPopupPrimaryTextColor(): Color {
    val context = LocalContext.current
    val isLight = ColorUtils.isColorLight(context.popupBackgroundColor)
    return if (isLight) Color(0xDE000000) else Color(0xFFFFFFFF)
}

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraSmall = RoundedCornerShape(12.dp),  // DropdownMenu 圆角 12dp
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun LegadoTheme(content: @Composable () -> Unit) {
    val colorScheme = rememberLegadoColorScheme()
    CompositionLocalProvider(
        LocalAnimationsEnabled provides !AppConfig.isEInkMode
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
