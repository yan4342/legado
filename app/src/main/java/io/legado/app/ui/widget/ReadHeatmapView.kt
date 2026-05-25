package io.legado.app.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.isDarkTheme
import io.legado.app.utils.dpToPx
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * GitHub-style heatmap view for daily reading records.
 * Displays the last [totalDays] days as colored squares,
 * where color intensity represents reading duration.
 */
class ReadHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Data: date string "yyyy-MM-dd" -> readTime in milliseconds
    private var data: Map<String, Long> = emptyMap()
    private var maxReadTime: Long = 0L

    private val totalDays = 365
    private val columns = 53 // ~52 weeks + 1
    private val rows = 7    // Mon-Sun

    private val cellSize = 12.dpToPx().toFloat()
    private val cellGap = 3.dpToPx().toFloat()
    private val cornerRadius = 2.dpToPx().toFloat()
    private val labelWidth = 30.dpToPx().toFloat()
    private val headerHeight = 20.dpToPx().toFloat()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("M月", Locale.getDefault())

    // Colors
    private var emptyColor: Int = 0xFFEBEDF0.toInt()
    private var levelColors: IntArray = intArrayOf(
        0xFF9BE9A8.toInt(),
        0xFF40C463.toInt(),
        0xFF30A14E.toInt(),
        0xFF216E39.toInt()
    )

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10.dpToPx().toFloat()
        textAlign = Paint.Align.CENTER
    }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()

    // Tooltip
    private var tooltipText: String? = null
    private var tooltipX: Float = 0f
    private var tooltipY: Float = 0f
    private val tooltipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12.dpToPx().toFloat()
    }
    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tooltipPadding = 8.dpToPx().toFloat()

    // Scroll
    private var scrollX = 0f
    private var maxScrollX = 0f
    private var lastTouchX = 0f
    private var isDragging = false

    // Day labels
    private val dayLabels = arrayOf("一", "二", "三", "四", "五", "六", "日")

    // Month labels data
    private data class MonthLabel(val label: String, val col: Int)
    private var monthLabels: List<MonthLabel> = emptyList()

    // Date grid: col/row -> date string
    private var dateGrid: Array<Array<String?>> = emptyArray()

    var onDayClick: ((date: String, readTime: Long) -> Unit)? = null

    init {
        try {
            val isDark = context.isDarkTheme
            emptyColor = if (isDark) {
                0xFF161B22.toInt()
            } else {
                0xFFEBEDF0.toInt()
            }
            levelColors = if (isDark) {
                intArrayOf(
                    0xFF0E4429.toInt(),
                    0xFF006D32.toInt(),
                    0xFF26A641.toInt(),
                    0xFF39D353.toInt()
                )
            } else {
                intArrayOf(
                    0xFF9BE9A8.toInt(),
                    0xFF40C463.toInt(),
                    0xFF30A14E.toInt(),
                    0xFF216E39.toInt()
                )
            }
            textPaint.color = if (isDark) 0xFF8B949E.toInt() else 0xFF656D76.toInt()
            tooltipBgPaint.color = if (isDark) 0xFF2D333B.toInt() else 0xFFFFFFFF.toInt()
            tooltipPaint.color = if (isDark) 0xFFFFFFFF.toInt() else 0xFF24292F.toInt()
        } catch (e: Exception) {
            emptyColor = 0xFFEBEDF0.toInt()
            levelColors = intArrayOf(
                0xFF9BE9A8.toInt(),
                0xFF40C463.toInt(),
                0xFF30A14E.toInt(),
                0xFF216E39.toInt()
            )
            textPaint.color = 0xFF656D76.toInt()
            tooltipBgPaint.color = 0xFFFFFFFF.toInt()
            tooltipPaint.color = 0xFF24292F.toInt()
        }
    }

    fun setData(dailyData: Map<String, Long>) {
        data = dailyData
        maxReadTime = dailyData.values.maxOrNull()?.takeIf { it > 0 } ?: 1L
        buildDateGrid()
        requestLayout()
        invalidate()
    }

    private fun buildDateGrid() {
        val cal = Calendar.getInstance()
        // Start from today, go back totalDays-1 days
        cal.add(Calendar.DAY_OF_YEAR, -(totalDays - 1))
        // Align to Monday of that week
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        dateGrid = Array(columns) { arrayOfNulls<String>(rows) }
        val tempMonthLabels = mutableListOf<MonthLabel>()
        var lastMonth = -1

        for (col in 0 until columns) {
            for (row in 0 until rows) {
                val dateStr = dateFormat.format(cal.time)
                dateGrid[col][row] = dateStr
                // Track month labels
                if (row == 0) {
                    val month = cal.get(Calendar.MONTH)
                    if (month != lastMonth) {
                        tempMonthLabels.add(MonthLabel(monthFormat.format(cal.time), col))
                        lastMonth = month
                    }
                }
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        monthLabels = tempMonthLabels
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (labelWidth + columns * (cellSize + cellGap) + paddingLeft + paddingRight).toInt()
        val desiredHeight = (headerHeight + rows * (cellSize + cellGap) + paddingBottom + paddingTop).toInt()
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val contentWidth = labelWidth + columns * (cellSize + cellGap)
        maxScrollX = (contentWidth - w + paddingLeft + paddingRight).coerceAtLeast(0f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dateGrid.isEmpty()) return

        val saveCount = canvas.save()
        canvas.translate(-scrollX, 0f)

        // Draw month labels
        for (ml in monthLabels) {
            val x = labelWidth + ml.col * (cellSize + cellGap) + cellSize / 2
            canvas.drawText(ml.label, x, headerHeight - 4.dpToPx(), textPaint)
        }

        // Draw day labels (Mon, Wed, Fri)
        val labelRows = intArrayOf(0, 2, 4) // Mon, Wed, Fri
        for (row in labelRows) {
            val y = headerHeight + row * (cellSize + cellGap) + cellSize * 0.75f
            canvas.drawText(dayLabels[row], labelWidth / 2, y, textPaint)
        }

        // Draw cells
        for (col in 0 until columns) {
            for (row in 0 until rows) {
                val dateStr = dateGrid[col][row] ?: continue
                val x = labelWidth + col * (cellSize + cellGap)
                val y = headerHeight + row * (cellSize + cellGap)
                val readTime = data[dateStr] ?: 0L
                cellPaint.color = getColorForTime(readTime)
                rectF.set(x, y, x + cellSize, y + cellSize)
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, cellPaint)
            }
        }

        canvas.restoreToCount(saveCount)

        // Draw tooltip
        tooltipText?.let { text ->
            val bgRect = RectF()
            val textWidth = tooltipPaint.measureText(text)
            bgRect.set(
                tooltipX - textWidth / 2 - tooltipPadding,
                tooltipY - tooltipPaint.textSize - tooltipPadding * 2,
                tooltipX + textWidth / 2 + tooltipPadding,
                tooltipY
            )
            // Clamp to view bounds
            if (bgRect.left < 0) bgRect.offset(-bgRect.left, 0f)
            if (bgRect.right > width) bgRect.offset(width - bgRect.right, 0f)
            if (bgRect.top < 0) bgRect.offset(0f, -bgRect.top)

            canvas.drawRoundRect(bgRect, 6.dpToPx().toFloat(), 6.dpToPx().toFloat(), tooltipBgPaint)
            canvas.drawText(
                text,
                bgRect.centerX(),
                bgRect.bottom - tooltipPadding,
                tooltipPaint.apply { textAlign = Paint.Align.CENTER }
            )
        }
    }

    private fun getColorForTime(readTime: Long): Int {
        if (readTime <= 0) return emptyColor
        val ratio = readTime.toFloat() / maxReadTime
        val index = when {
            ratio < 0.25f -> 0
            ratio < 0.5f -> 1
            ratio < 0.75f -> 2
            else -> 3
        }
        return levelColors[index]
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                isDragging = false
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = lastTouchX - event.x
                if (Math.abs(dx) > 5) isDragging = true
                if (isDragging) {
                    scrollX = (scrollX + dx).coerceIn(0f, maxScrollX)
                    lastTouchX = event.x
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                parent.requestDisallowInterceptTouchEvent(false)
                if (!isDragging) {
                    handleClick(event.x, event.y)
                }
                tooltipText = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                tooltipText = null
                invalidate()
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleClick(rawX: Float, rawY: Float) {
        val x = rawX + scrollX - labelWidth
        val y = rawY - headerHeight
        if (x < 0 || y < 0) return
        val col = (x / (cellSize + cellGap)).toInt()
        val row = (y / (cellSize + cellGap)).toInt()
        if (col < 0 || col >= columns || row < 0 || row >= rows) return
        val dateStr = dateGrid.getOrNull(col)?.getOrNull(row) ?: return
        val readTime = data[dateStr] ?: 0L

        // Show tooltip
        val cellX = labelWidth + col * (cellSize + cellGap) + cellSize / 2
        val cellY = headerHeight + row * (cellSize + cellGap)
        tooltipX = cellX - scrollX
        tooltipY = cellY
        val timeText = formatReadTime(readTime)
        tooltipText = "$dateStr  $timeText"
        invalidate()

        onDayClick?.invoke(dateStr, readTime)
    }

    private fun formatReadTime(ms: Long): String {
        if (ms <= 0) return "未阅读"
        val minutes = ms / (1000 * 60)
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 -> "${hours}小时${mins}分钟"
            mins > 0 -> "${mins}分钟"
            else -> "<1分钟"
        }
    }
}
