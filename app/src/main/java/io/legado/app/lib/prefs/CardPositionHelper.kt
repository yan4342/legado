package io.legado.app.lib.prefs

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.getPrefInt

object CardPositionHelper {

    enum class CardPosition { FIRST, MIDDLE, LAST, SINGLE, NONE }

    // Cached dimension value
    private var cachedCornerRadius: Float = -1f

    fun applyCardStyle(
        holder: PreferenceViewHolder,
        preference: androidx.preference.Preference
    ) {
        val position = computeCardPosition(preference)
        if (position == CardPosition.NONE) return
        val view = holder.itemView
        val ctx = view.context
        val cornerRadius = if (cachedCornerRadius > 0) {
            cachedCornerRadius
        } else {
            ctx.resources.getDimension(R.dimen.prefs_card_corner_radius).also {
                cachedCornerRadius = it
            }
        }
        view.background = createDrawable(ctx, position, cornerRadius)
    }

    /**
     * Creates a fresh [RippleDrawable] with independent animation state for each view.
     * Drawable instances are NOT cached because [RippleDrawable] carries per-view animation
     * state (hotspot, ripple progress). Sharing a single instance across views causes the
     * ripple to appear on the wrong item. [ConstantState.newDrawable] cloning is also
     * avoided as it is unreliable across OEM ROMs (can lose content color on some devices).
     *
     * Preference rows can be rebound while pressed (for example when a SwitchPreference
     * toggles). Always assigning the background keeps recycled views from keeping the
     * platform default selectable background and losing the card color.
     */
    private fun createDrawable(
        ctx: android.content.Context,
        position: CardPosition,
        cornerRadius: Float
    ): Drawable {
        val bgColor = ctx.backgroundColor
        val prefKey = if (AppConfig.isNightTheme) PreferKey.cNCardBg else PreferKey.cCardBg
        val savedColor = ctx.getPrefInt(prefKey)
        val cardColor = if (savedColor != 0) {
            savedColor
        } else if (AppConfig.isNightTheme) {
            ColorUtils.shiftColor(bgColor, 1.05f)
        } else {
            ColorUtils.shiftColor(bgColor, 0.95f)
        }
        val rippleColor = if (AppConfig.isNightTheme) 0x20FFFFFF else 0x20000000

        val maskDrawable = GradientDrawable().apply {
            // Color is irrelevant for a mask — only the shape/outline matters
            setColor(0xFF000000.toInt())
            applyCornerShape(position, cornerRadius)
        }

        val cardDrawable = GradientDrawable().apply {
            setColor(cardColor)
            applyCornerShape(position, cornerRadius)
        }

        return RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            cardDrawable,
            maskDrawable
        )
    }

    private fun GradientDrawable.applyCornerShape(
        position: CardPosition,
        cornerRadius: Float
    ) {
        when (position) {
            CardPosition.FIRST -> {
                cornerRadii = floatArrayOf(
                    cornerRadius, cornerRadius,
                    cornerRadius, cornerRadius,
                    0f, 0f,
                    0f, 0f
                )
            }
            CardPosition.MIDDLE -> {
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
            }
            CardPosition.LAST -> {
                cornerRadii = floatArrayOf(
                    0f, 0f,
                    0f, 0f,
                    cornerRadius, cornerRadius,
                    cornerRadius, cornerRadius
                )
            }
            CardPosition.SINGLE -> {
                this.cornerRadius = cornerRadius
            }
            else -> {}
        }
    }

    private fun computeCardPosition(preference: androidx.preference.Preference): CardPosition {
        val parent = preference.parent ?: return CardPosition.NONE
        if (parent !is PreferenceGroup) return CardPosition.NONE
        val visibleChildren = (0 until parent.preferenceCount)
            .map { parent.getPreference(it) }
            .filter { it.isVisible && it !is PreferenceCategory }
        val index = visibleChildren.indexOf(preference)
        if (index < 0) return CardPosition.NONE
        return when {
            visibleChildren.size == 1 -> CardPosition.SINGLE
            index == 0 -> CardPosition.FIRST
            index == visibleChildren.size - 1 -> CardPosition.LAST
            else -> CardPosition.MIDDLE
        }
    }

    internal fun clearCache() {
        cachedCornerRadius = -1f
    }
}
