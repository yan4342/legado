package io.legado.app.ui.common.compose

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.utils.activity
import java.lang.ref.WeakReference

// ── Unified factory: ComposeView { ⋮ or label + RoundDropdownMenu } ──

/**
 * Creates a plain [AppCompatImageButton] (⋮ icon) for use as a Toolbar actionView.
 * On click, shows [RoundDropdownMenu] via [showComposeDropdownMenu] anchored to the button.
 *
 * Using a regular View avoids AbstractComposeView.onMeasure being called before the view
 * is attached to a window (which happens when ActionMenuPresenter.flagActionItems runs).
 */
fun Context.createComposeDropdownIcon(
    iconTint: Color = Color.Unspecified,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
): View {
    return AppCompatImageButton(this).apply {
        setImageResource(R.drawable.ic_more_vert)
        background = null
        contentDescription = null
        setOnClickListener { showComposeDropdownMenu(context, this, menuContent) }
    }
}

/**
 * Creates a plain [View] showing a text label for use as a Toolbar actionView.
 * On click, shows [RoundDropdownMenu] via [showComposeDropdownMenu] anchored to the view.
 */
fun Context.createComposeDropdownText(
    getLabel: () -> String,
    isVisible: () -> Boolean = { true },
    textColor: Color = Color.Unspecified,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
): View {
    return ComposeView(this).apply {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
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
            override fun onViewDetachedFromWindow(v: View) = Unit
        })
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

/** Guards against double-invocation: only one ComposeDropdownMenu may be showing at a time. */
private var showingMenuView: WeakReference<ComposeView>? = null

/**
 * Shows a Compose-styled dropdown menu anchored below [anchor], replacing
 * [androidx.appcompat.widget.PopupMenu] in View-based RecyclerView adapters.
 *
 * The ComposeView is attached directly to the Activity's decorView so that
 * ViewTreeLifecycleOwner is naturally available.
 *
 * Does NOT use [DropdownMenu], [BackHandler], or [OnBackPressedCallback] —
 * all of which register with [OnBackInvokedDispatcher] and crash when
 * unregistered during a predictive-back animation frame. Instead the menu is a
 * plain [Surface] + [Column], and the back key is intercepted via Compose's
 * [onKeyEvent] modifier (hardware back only — swipe-back is not intercepted).
 *
 * Re-entrant safe: if a menu is already showing, it is dismissed first.
 */
fun showComposeDropdownMenu(
    context: Context,
    anchor: View,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    // Dismiss any currently showing menu — prevents double-ComposeView crash
    showingMenuView?.get()?.let { cv ->
        (cv.parent as? ViewGroup)?.removeView(cv)
    }
    showingMenuView = null

    val decorView = anchor.activity?.window?.decorView as? ViewGroup ?: return
    val loc = intArrayOf(0, 0)
    anchor.getLocationInWindow(loc)
    val anchorX = loc[0]
    val anchorY = loc[1] + anchor.height

    val cv = ComposeView(context)
    showingMenuView = WeakReference(cv)
    cv.setContent {
        LegadoTheme {
            var visible by remember { mutableStateOf(false) }
            val focusRequester = remember { FocusRequester() }

            // Animate in on next frame
            LaunchedEffect(Unit) {
                visible = true
                focusRequester.requestFocus()
            }

            // Cleanup after exit animation
            LaunchedEffect(visible) {
                if (!visible) {
                    kotlinx.coroutines.delay(300)
                    (cv.parent as? ViewGroup)?.removeView(cv)
                }
            }

            // Full-screen transparent backdrop
            // - tap outside → dismiss
            // - KEYCODE_BACK → dismiss (hardware back, no OnBackInvokedDispatcher involvement)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.key == Key.Back) {
                            visible = false
                            true
                        } else false
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { visible = false }
            ) {
                // Anchored menu panel with spring bounce animation
                AnimatedVisibility(
                    visible = visible,
                    modifier = Modifier.offset { IntOffset(anchorX, anchorY) },
                    enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                        scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                            initialScale = 0.85f,
                        ),
                    exit = fadeOut() + scaleOut(targetScale = 0.85f),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = legadoPopupBackgroundColor(),
                        shadowElevation = 4.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            menuContent { visible = false }
                        }
                    }
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
