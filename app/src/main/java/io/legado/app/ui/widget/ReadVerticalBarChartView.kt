package io.legado.app.ui.widget

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
 * Vertical bar chart view for displaying daily reading time distribution.
 */
class ReadVerticalBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class BarItem(
        val label: String,
        val readTime: Long
    )

    private var items: List<BarItem> = emptyList()
    private var maxTime: Long = 1L
    private var selectedIndex: Int = -1

    // Cached bar horizontal positions for hit-testing (populated in onDraw)
    private data class BarRect(val left: Float, val right: Float)
    private val barRects = mutableListOf<BarRect>()

    private val barWidth = 20.dpToPx().toFloat()
    private val barMinWidth = 8.dpToPx().toFloat()
    private val barGap = 6.dpToPx().toFloat()
    private val barGapDense = 2.dpToPx().toFloat()
    private val barCornerRadius = 4.dpToPx().toFloat()
    private val bottomLabelHeight = 32.dpToPx().toFloat()
    private val topPadding = 16.dpToPx().toFloat()
    private val maxLabelHeight = 16.dpToPx().toFloat()

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10.dpToPx().toFloat()
        textAlign = Paint.Align.CENTER
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgBarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.dpToPx().toFloat()
        style = Paint.Style.STROKE
    }
    private val valueLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11.dpToPx().toFloat()
        textAlign = Paint.Align.CENTER
    }
    private val valueBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()

    init {
        try {
            val isDark = context.isDarkTheme
            val accent = context.accentColor
            val bg = context.backgroundColor
            barPaint.color = accent
            bgBarPaint.color = if (isDark) {
                blendColor(bg, 0xFFFFFFFF.toInt(), 0.1f)
            } else {
                blendColor(bg, 0xFF000000.toInt(), 0.05f)
            }
            labelPaint.color = ThemeUtils.resolveColor(
                context,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                if (isDark) 0xFF8B949E.toInt() else 0xFF656D76.toInt()
            )
            gridPaint.color = if (isDark) 0x1AFFFFFF else 0x1A000000
            valueLabelPaint.color = accent
            valueBgPaint.color = if (isDark) 0xFF30363D.toInt() else 0xFFE8E8E8.toInt()
        } catch (e: Exception) {
            barPaint.color = 0xFF1976D2.toInt()
            bgBarPaint.color = 0xFFF6F8FA.toInt()
            labelPaint.color = 0xFF656D76.toInt()
            gridPaint.color = 0x1A000000
            valueLabelPaint.color = 0xFF1976D2.toInt()
            valueBgPaint.color = 0xFFE8E8E8.toInt()
        }
    }

    fun setData(barItems: List<BarItem>) {
        items = barItems
        maxTime = barItems.maxOfOrNull { it.readTime }?.takeIf { it > 0 } ?: 1L
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = resolveSize(suggestedMinimumHeight, heightMeasureSpec)
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            height
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val hitIndex = barRects.indexOfFirst { x in it.left..it.right }
            selectedIndex = if (hitIndex == selectedIndex) -1 else hitIndex
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty()) return

        // Measure y-axis label width for layout
        val maxLabelText = formatReadTime(maxTime)
        val labelTextWidth = labelPaint.measureText(maxLabelText)

        val chartLeft = paddingLeft.toFloat() + labelTextWidth + 12.dpToPx()
        val chartRight = (width - paddingRight).toFloat() - 4.dpToPx()
        val chartTop = paddingTop.toFloat() + topPadding + maxLabelHeight
        val chartBottom = (height - paddingBottom).toFloat() - bottomLabelHeight
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        // Draw grid lines with y-axis labels
        val gridStep = chartHeight / 4f
        for (i in 0..4) {
            val y = chartTop + gridStep * i
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            val timeAtLine = maxTime * (4 - i) / 4
            if (timeAtLine > 0) {
                canvas.drawText(
                    formatReadTime(timeAtLine),
                    chartLeft - 6.dpToPx(),
                    y + 4.dpToPx(),
                    labelPaint.apply { textAlign = Paint.Align.RIGHT }
                )
            }
        }
        labelPaint.textAlign = Paint.Align.CENTER

        // Draw axis lines (y-axis left, x-axis bottom)
        val axisPaint = gridPaint
        canvas.drawLine(chartLeft, chartTop, chartLeft, chartBottom, axisPaint)
        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, axisPaint)

        // Draw bars
        val dynamicGap = if (items.size > 20) barGapDense else barGap
        val maxBarWidth = when { items.size <= 7 -> 40.dpToPx().toFloat(); items.size <= 12 -> 24.dpToPx().toFloat(); else -> barWidth }
        val totalGapWidth = dynamicGap * (items.size + 1)
        val availableWidth = chartWidth - totalGapWidth
        val calculatedBarWidth = if (items.isNotEmpty()) availableWidth / items.size else barWidth
        val actualBarWidth = calculatedBarWidth.coerceIn(barMinWidth, maxBarWidth)
        val labelInterval = when { items.size > 20 -> 4; items.size > 12 -> 3; items.size > 7 -> 2; else -> 1 }

        barRects.clear()

        for ((index, item) in items.withIndex()) {
            val barLeft = chartLeft + dynamicGap + index * (actualBarWidth + dynamicGap)
            val barHeight = if (maxTime > 0) (item.readTime.toFloat() / maxTime) * chartHeight else 0f
            val barTop = chartBottom - barHeight

            barRects.add(BarRect(barLeft, barLeft + actualBarWidth))

            // Draw bar — highlight selected bar
            if (barHeight > 0) {
                rectF.set(barLeft, barTop, barLeft + actualBarWidth, chartBottom)
                canvas.drawRoundRect(rectF, barCornerRadius, barCornerRadius, barPaint)
            } else if (index == selectedIndex) {
                // Draw a tiny bar for zero-value selected bar so the label has an anchor
                rectF.set(barLeft, chartBottom - 2.dpToPx(), barLeft + actualBarWidth, chartBottom)
                canvas.drawRoundRect(rectF, barCornerRadius, barCornerRadius, barPaint)
            }

            // Draw bottom label at interval to avoid overlap
            if (index % labelInterval == 0) {
                val displayLabel = truncateText(item.label, (actualBarWidth + dynamicGap) * labelInterval, labelPaint)
                canvas.drawText(
                    displayLabel,
                    barLeft + actualBarWidth / 2f,
                    chartBottom + 14.dpToPx(),
                    labelPaint
                )
            }
        }

        // Draw value label above selected bar
        if (selectedIndex in items.indices) {
            val item = items[selectedIndex]
            val barLeft = chartLeft + dynamicGap + selectedIndex * (actualBarWidth + dynamicGap)
            val barCenterX = barLeft + actualBarWidth / 2f
            val barHeight = if (maxTime > 0) (item.readTime.toFloat() / maxTime) * chartHeight else 0f
            val barTop = chartBottom - barHeight

            val text = formatReadTime(item.readTime)
            val textWidth = valueLabelPaint.measureText(text)
            val textHeight = valueLabelPaint.textSize
            val bgPaddingH = 6.dpToPx()
            val bgPaddingV = 3.dpToPx()
            val bgTop = barTop - textHeight - bgPaddingV * 2 - 4.dpToPx()
            val bgLeft = barCenterX - textWidth / 2f - bgPaddingH
            val bgRight = barCenterX + textWidth / 2f + bgPaddingH
            val bgBottom = barTop - 4.dpToPx()

            // Clamp label left/right within chart area
            val clampedBgLeft = bgLeft.coerceAtLeast(chartLeft)
            val clampedBgRight = bgRight.coerceAtMost(chartRight)

            if (clampedBgRight > clampedBgLeft) {
                rectF.set(clampedBgLeft, bgTop, clampedBgRight, bgBottom)
                canvas.drawRoundRect(rectF, 4.dpToPx().toFloat(), 4.dpToPx().toFloat(), valueBgPaint)
                canvas.drawText(text, barCenterX, bgTop + textHeight + bgPaddingV, valueLabelPaint)
            }
        }
    }

    private fun truncateText(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + paint.measureText("…") > maxWidth) {
            end--
        }
        return if (end > 0) text.substring(0, end) + "…" else "…"
    }

    private fun formatReadTime(ms: Long): String {
        val totalMinutes = ms / (1000 * 60)
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${totalMinutes}m"
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
