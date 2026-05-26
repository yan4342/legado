package io.legado.app.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import io.legado.app.lib.theme.ThemeUtils
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.isDarkTheme
import io.legado.app.utils.dpToPx

/**
 * Horizontal bar chart view for displaying book reading time rankings.
 */
class ReadBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class BarItem(
        val bookName: String,
        val readTime: Long
    )

    private var items: List<BarItem> = emptyList()
    private var maxTime: Long = 1L

    private val barHeight = 24.dpToPx().toFloat()
    private val barGap = 10.dpToPx().toFloat()
    private val barCornerRadius = 4.dpToPx().toFloat()
    private val labelWidth = 120.dpToPx().toFloat()
    private val timeLabelWidth = 80.dpToPx().toFloat()
    private val valueGap = 8.dpToPx().toFloat()

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13.dpToPx().toFloat()
        color = ThemeUtils.resolveColor(
            context,
            com.google.android.material.R.attr.colorOnSurface,
            0xFF000000.toInt()
        )
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12.dpToPx().toFloat()
        textAlign = Paint.Align.RIGHT
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgBarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()

    private var barColors: IntArray = intArrayOf(0xFF1976D2.toInt())

    var onItemClick: ((bookName: String) -> Unit)? = null

    init {
        try {
            val isDark = context.isDarkTheme
            val accent = context.accentColor
            val bg = context.backgroundColor
            bgBarPaint.color = if (isDark) {
                blendColor(bg, 0xFFFFFFFF.toInt(), 0.1f)
            } else {
                blendColor(bg, 0xFF000000.toInt(), 0.05f)
            }
            timePaint.color = ThemeUtils.resolveColor(
                context,
                com.google.android.material.R.attr.colorOnSurface,
                if (isDark) 0xFF8B949E.toInt() else 0xFF656D76.toInt()
            )
            val barBase = if (isDark) 0xFF30363D.toInt() else 0xFFFFFFFF.toInt()
            barColors = intArrayOf(
                accent,
                blendColor(accent, barBase, 0.2f),
                blendColor(accent, barBase, 0.4f),
                blendColor(accent, barBase, 0.6f),
                blendColor(accent, barBase, 0.75f),
            )
        } catch (e: Exception) {
            val fallback = 0xFF1976D2.toInt()
            bgBarPaint.color = 0xFFF6F8FA.toInt()
            timePaint.color = 0xFF656D76.toInt()
            barColors = intArrayOf(
                fallback,
                blendColor(fallback, 0xFFFFFFFF.toInt(), 0.2f),
                blendColor(fallback, 0xFFFFFFFF.toInt(), 0.4f),
                blendColor(fallback, 0xFFFFFFFF.toInt(), 0.6f),
                blendColor(fallback, 0xFFFFFFFF.toInt(), 0.75f),
            )
        }
    }

    fun setData(barItems: List<BarItem>) {
        items = barItems
        maxTime = barItems.maxOfOrNull { it.readTime }?.takeIf { it > 0 } ?: 1L
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (items.size * (barHeight + barGap) + paddingTop + paddingBottom).toInt()
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty()) return

        val barAreaWidth = width - paddingLeft - paddingRight - labelWidth - timeLabelWidth - valueGap * 2
        var y = paddingTop.toFloat()

        for ((index, item) in items.withIndex()) {
            // Truncate book name if too long
            val displayName = truncateText(item.bookName, labelWidth, labelPaint)

            // Draw book name (left)
            canvas.drawText(
                displayName,
                paddingLeft.toFloat(),
                y + barHeight * 0.75f,
                labelPaint
            )

            // Draw background bar
            val barLeft = paddingLeft + labelWidth
            val barRight = barLeft + barAreaWidth
            rectF.set(barLeft, y, barRight, y + barHeight)
            canvas.drawRoundRect(rectF, barCornerRadius, barCornerRadius, bgBarPaint)

            // Draw filled bar
            val fillWidth = (item.readTime.toFloat() / maxTime) * barAreaWidth
            if (fillWidth > 0) {
                rectF.set(barLeft, y, barLeft + fillWidth, y + barHeight)
                barPaint.color = barColors[index % barColors.size]
                canvas.drawRoundRect(rectF, barCornerRadius, barCornerRadius, barPaint)
            }

            // Draw time label (right)
            canvas.drawText(
                formatReadTime(item.readTime),
                width - paddingRight.toFloat(),
                y + barHeight * 0.75f,
                timePaint
            )

            y += barHeight + barGap
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && items.isNotEmpty()) {
            val y = event.y - paddingTop
            val index = (y / (barHeight + barGap)).toInt()
            if (index in items.indices) {
                onItemClick?.invoke(items[index].bookName)
            }
        }
        return super.onTouchEvent(event)
    }

    private fun truncateText(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + paint.measureText("...") > maxWidth) {
            end--
        }
        return if (end > 0) text.substring(0, end) + "..." else "..."
    }

    private fun formatReadTime(ms: Long): String {
        val minutes = ms / (1000 * 60)
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 -> "${hours}h${mins}m"
            mins > 0 -> "${mins}m"
            else -> "<1m"
        }
    }

    private fun blendColor(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = (android.graphics.Color.alpha(color1) * inverseRatio + android.graphics.Color.alpha(color2) * ratio).toInt()
        val r = (android.graphics.Color.red(color1) * inverseRatio + android.graphics.Color.red(color2) * ratio).toInt()
        val g = (android.graphics.Color.green(color1) * inverseRatio + android.graphics.Color.green(color2) * ratio).toInt()
        val b = (android.graphics.Color.blue(color1) * inverseRatio + android.graphics.Color.blue(color2) * ratio).toInt()
        return android.graphics.Color.argb(a, r, g, b)
    }
}
