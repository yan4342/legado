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

    // Per-group position cache: cleared each bind cycle
    private var cachedParent: PreferenceGroup? = null
    private var cachedPositions: Map<androidx.preference.Preference, CardPosition> = emptyMap()

    // Drawable cache: keyed by position + cornerRadius
    private var cachedCornerRadius: Float = -1f
    private val drawableCache = HashMap<CardPosition, Drawable>(4)

    fun applyCardStyle(
        holder: PreferenceViewHolder,
        preference: androidx.preference.Preference
    ) {
        val position = getCardPosition(preference)
        if (position == CardPosition.NONE) return
        val ctx = holder.itemView.context
        val cornerRadius = if (cachedCornerRadius > 0) {
            cachedCornerRadius
        } else {
            ctx.resources.getDimension(R.dimen.prefs_card_corner_radius).also {
                cachedCornerRadius = it
            }
        }
        holder.itemView.background = getOrCreateDrawable(ctx, position, cornerRadius)
    }

    private fun getOrCreateDrawable(
        ctx: android.content.Context,
        position: CardPosition,
        cornerRadius: Float
    ): Drawable {
        drawableCache[position]?.let { return it }

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

        val cardDrawable = GradientDrawable().apply {
            setColor(cardColor)
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
        val drawable: Drawable = RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            cardDrawable,
            null
        )
        drawableCache[position] = drawable
        return drawable
    }

    private fun getCardPosition(preference: androidx.preference.Preference): CardPosition {
        val parent = preference.parent ?: return CardPosition.NONE
        if (parent !is PreferenceGroup) return CardPosition.NONE

        // Rebuild cache when parent group changes
        if (parent !== cachedParent) {
            cachedParent = parent
            val visibleChildren = (0 until parent.preferenceCount)
                .map { parent.getPreference(it) }
                .filter { it.isVisible && it !is PreferenceCategory }
            val map = HashMap<androidx.preference.Preference, CardPosition>(visibleChildren.size)
            for ((i, child) in visibleChildren.withIndex()) {
                map[child] = when {
                    visibleChildren.size == 1 -> CardPosition.SINGLE
                    i == 0 -> CardPosition.FIRST
                    i == visibleChildren.size - 1 -> CardPosition.LAST
                    else -> CardPosition.MIDDLE
                }
            }
            cachedPositions = map
        }

        return cachedPositions[preference] ?: CardPosition.NONE
    }

    internal fun clearCache() {
        cachedParent = null
        cachedPositions = emptyMap()
        drawableCache.clear()
        cachedCornerRadius = -1f
    }
}
