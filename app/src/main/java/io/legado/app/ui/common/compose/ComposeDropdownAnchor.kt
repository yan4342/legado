package io.legado.app.ui.common.compose

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.activity

// ── Unified factory: ComposeView { ⋮ or label + RoundDropdownMenu } ──

/**
 * Creates an icon-style ⋮ [ComposeView] that opens [RoundDropdownMenu] with spring bounce.
 *
 * For Toolbar setActionView(): `menuItem.setActionView(view)`
 * For Adapter item: replace ivMenuMore ImageView with this ComposeView.
 */
fun Context.createComposeDropdownIcon(
    iconTint: Color = Color.Unspecified,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
): View {
    return ComposeView(this).apply {
        setContent {
            LegadoTheme {
                val tint = if (iconTint == Color.Unspecified || AppConfig.isEInkMode)
                    MaterialTheme.colorScheme.onPrimary else iconTint
                var expanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = null,
                            tint = tint,
                        )
                    }
                    RoundDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        content = menuContent,
                    )
                }
            }
        }
    }
}

/**
 * Creates a text-label ComposeView that opens [RoundDropdownMenu] on click.
 * Used e.g. in ReadMenu for the source name button.
 */
fun Context.createComposeDropdownText(
    getLabel: () -> String,
    isVisible: () -> Boolean = { true },
    textColor: Color = Color.Unspecified,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
): View {
    return ComposeView(this).apply {
        setContent {
            LegadoTheme {
                if (!isVisible()) return@LegadoTheme
                var expanded by remember { mutableStateOf(false) }
                val label = getLabel()
                val color = if (textColor == Color.Unspecified)
                    MaterialTheme.colorScheme.onSurface else textColor
                Box {
                    Text(
                        text = label,
                        color = color,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { expanded = true },
                    )
                    RoundDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        content = menuContent,
                    )
                }
            }
        }
    }
}

/**
 * Inline ⋮ + [RoundDropdownMenu] for Compose-native screens.
 */
@Composable
fun OverflowMenuAnchor(
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = null,
                tint = iconTint,
            )
        }
        RoundDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            content = content,
        )
    }
}

// ── Scenario C: View RecyclerView item (drop-in PopupMenu replacement) ──

/**
 * Shows a Compose [RoundDropdownMenu] anchored below [anchor], replacing
 * [androidx.appcompat.widget.PopupMenu] in View-based RecyclerView adapters.
 *
 * The ComposeView is attached directly to the Activity's decorView so that
 * ViewTreeLifecycleOwner is naturally available — no manual injection needed.
 * This avoids the double-PopupWindow crash that occurs when nesting a
 * DropdownMenu inside a PopupWindow.
 */
fun showComposeDropdownMenu(
    context: Context,
    anchor: View,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    val decorView = anchor.activity?.window?.decorView as? ViewGroup ?: return
    val loc = intArrayOf(0, 0)
    anchor.getLocationInWindow(loc)
    val anchorX = loc[0]
    val anchorY = loc[1] + anchor.height

    val cv = ComposeView(context)
    cv.setContent {
        LegadoTheme {
            var expanded by remember { mutableStateOf(true) }

            fun dismiss() {
                expanded = false
                decorView.removeView(cv)
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.offset { IntOffset(anchorX, anchorY) }) {
                    RoundDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = ::dismiss,
                        content = { menuContent(::dismiss) },
                    )
                }
            }
        }
    }

    decorView.addView(
        cv,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    )
}
