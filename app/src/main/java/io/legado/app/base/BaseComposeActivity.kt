package io.legado.app.base

import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import io.legado.app.constant.EventBus
import io.legado.app.constant.Theme
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.disableAutoFill
import io.legado.app.utils.fullScreen
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setLightStatusBar
import io.legado.app.utils.windowSize
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 用于子级 Composable 设置状态栏是否透明（隐藏着色覆盖层）。
 * 默认为 false，即显示着色覆盖层。
 */
val LocalStatusBarTransparent = compositionLocalOf<MutableStateFlow<Boolean>?> { null }

/**
 * 用于子级 Composable 覆盖状态栏颜色。
 * 写入非空颜色将覆盖默认的 primary/surface 颜色；
 * 写入 null 恢复默认行为。
 */
val LocalStatusBarColor = compositionLocalOf<MutableStateFlow<Color?>?> { null }

abstract class BaseComposeActivity(
    val fullScreen: Boolean = true,
    private val toolBarTheme: Theme = Theme.Auto,
    private val transparent: Boolean = false,
    private val imageBg: Boolean = true
) : AppCompatActivity() {

    private val _statusBarTransparent = MutableStateFlow(false)
    private val _statusBarColor = MutableStateFlow<Color?>(null)

    @Composable
    protected abstract fun Content()

    override fun onCreate(savedInstanceState: Bundle?) {
        window.decorView.disableAutoFill()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setupSystemBar()
        setContent {
            LegadoTheme {
                val transparent by _statusBarTransparent.collectAsState()
                val statusBarColorOverride by _statusBarColor.collectAsState()

                CompositionLocalProvider(
                    LocalStatusBarTransparent provides _statusBarTransparent,
                    LocalStatusBarColor provides _statusBarColor,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Content()

                        if (!transparent) {
                            val defaultStatusBarColor = if (AppConfig.isEInkMode) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                            val finalColor = statusBarColorOverride ?: defaultStatusBarColor
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsTopHeight(WindowInsets.statusBars)
                                    .background(finalColor)
                            )
                        }
                    }
                }
            }
        }

        if (imageBg) {
            upBackgroundImage()
        }

        observeLiveBus()
    }

    open fun setupSystemBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (fullScreen && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            fullScreen()
        }

        // 状态栏颜色由 Compose 内容层处理（setContent 中的 statusBars 覆盖层）
        // 子页面可通过 LocalStatusBarTransparent 控制是否隐藏覆盖层（如详情页透出背景图）
        // 这里只设置状态栏图标明暗
        val primaryColor = ThemeStore.primaryColor(this)
        setLightStatusBar(ColorUtils.isColorLight(primaryColor))
    }

    open fun upBackgroundImage() {
        try {
            ThemeConfig.getBgImage(this, windowManager.windowSize)?.let {
                window.decorView.background = BitmapDrawable(resources, it)
            }
        } catch (_: Exception) {}
    }

    open fun observeLiveBus() {
        observeEvent<String>(EventBus.RECREATE) {
            recreate()
        }
        observeEvent<Boolean>(EventBus.NOTIFY_MAIN) {
            setupSystemBar()
        }
    }

}
