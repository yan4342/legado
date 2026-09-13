package io.legado.app.ui.common.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.legado.app.base.LocalStatusBarTransparentHandle
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.setLightStatusBar

/**
 * Hide the activity status-bar color overlay so a [surface]-colored TopAppBar can extend
 * edge-to-edge. Safe across nested / sequential screens via [io.legado.app.base.StatusBarTransparentHandle].
 *
 * Icon appearance uses [setLightStatusBar] (WindowInsetsController + legacy flags). Re-applies after
 * composition and on resume — OEM Android 13 often resets icons when [android.view.Window.setSoftInputMode]
 * or other window attribute writes run later in the same frame.
 */
@Composable
fun TransparentTopAppBarStatusBar(
    barColor: Color = MaterialTheme.colorScheme.surface,
) {
    val handle = LocalStatusBarTransparentHandle.current ?: return
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = view.context.findActivity() ?: return
    val isLightBar = ColorUtils.isColorLight(barColor.toArgb())

    fun applyIcons() {
        activity.setLightStatusBar(isLightBar)
    }

    DisposableEffect(handle) {
        handle.push()
        onDispose {
            handle.pop()
            if (!handle.hasRequests) {
                activity.setLightStatusBar(
                    ColorUtils.isColorLight(ThemeStore.primaryColor(activity)),
                )
            }
        }
    }

    // Win over later same-frame attribute writes (e.g. softInputMode) via SideEffect + post.
    SideEffect {
        applyIcons()
        view.post { applyIcons() }
    }

    DisposableEffect(isLightBar, lifecycleOwner) {
        applyIcons()
        view.post { applyIcons() }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                applyIcons()
                view.post { applyIcons() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
