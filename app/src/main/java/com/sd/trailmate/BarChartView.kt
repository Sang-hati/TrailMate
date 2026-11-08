package com.sd.trailmate

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class BarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    class Bar(val label: String, val value: Float)

    var onBarSelected: ((Int) -> Unit)? = null

    private var bars: List<Bar> = emptyList()
    private var goal = 0f
    private var selected = -1

    private val d = resources.displayMetrics.density   // dp -> pixels
    private val leftPad = 4f * d
    private val rightPad = 36f * d    // room for the y-axis labels
    private val topPad = 12f * d
    private val bottomPad = 26f * d   // room for the x-axis labels

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.trail_green)
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.trail_green_light)
    }
    private val gridPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.grid_line)
        strokeWidth = 1f * d
    }
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.trail_earth)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * d
        pathEffect = DashPathEffect(floatArrayOf(10f * d, 6f * d), 0f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = 11f * d
    }
    private val goalPath = Path()
    private val rect = RectF()

    fun setData(newBars: List<Bar>, goalSteps: Int, selectIndex: Int = -1) {
        bars = newBars
        goal = goalSteps.toFloat()
        selected = selectIndex
        invalidate() // ask Android to redraw
    }

    private fun niceMax(v: Float): Float {
        if (v <= 0f) return 1000f
        val step = when {
            v <= 2000f -> 500f
            v <= 6000f -> 1000f
            v <= 12000f -> 2000f
            v <= 30000f -> 5000f
            else -> 10000f
        }
        return ceil(v / step) * step
    }

    private fun axisText(v: Float): String =
        if (v >= 1000f) {
            val k = v / 1000f
            if (k % 1f == 0f) "${k.toInt()}k" else "${k}k"
        } else v.toInt().toString()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty()) return

        val left = leftPad
        val right = width - rightPad
        val top = topPad
        val bottom = height - bottomPad
        val chartH = bottom - top
        val maxValue = niceMax(max(bars.maxOf { it.value }, goal))
        fun yOf(v: Float) = bottom - (v / maxValue) * chartH

        // Three grid lines (0, half, max) with labels on the right
        for (i in 0..2) {
            val v = maxValue * i / 2f
            val y = yOf(v)
            canvas.drawLine(left, y, right, y, gridPaint)
            canvas.drawText(axisText(v), right + 6f * d, y + 4f * d, textPaint)
        }

        // Dashed goal line (drawn as a Path so dashes also work with hardware drawing)
        if (goal > 0f) {
            goalPath.reset()
            goalPath.moveTo(left, yOf(goal))
            goalPath.lineTo(right, yOf(goal))
            canvas.drawPath(goalPath, goalPaint)
        }

        val slot = (right - left) / bars.size    // width reserved for each bar
        val barW = slot * 0.62f
        val radius = min(barW / 2f, 6f * d)

        bars.forEachIndexed { i, bar ->
            val cx = left + slot * i + slot / 2f
            if (bar.value > 0f) {
                rect.set(cx - barW / 2f, yOf(bar.value), cx + barW / 2f, bottom)
                // The tapped bar is solid; the others fade a little
                barPaint.alpha = if (selected == -1 || selected == i) 255 else 110
                canvas.drawRoundRect(rect, radius, radius, barPaint)
            } else {
                // No data: a thin stub, so empty days are still visible
                rect.set(cx - barW / 2f, bottom - 3f * d, cx + barW / 2f, bottom)
                canvas.drawRoundRect(rect, 1.5f * d, 1.5f * d, emptyPaint)
            }
            if (bar.label.isNotEmpty()) {
                val w = textPaint.measureText(bar.label)
                // coerceIn keeps the first and last labels from being cut off at the edges
                val x = (cx - w / 2f).coerceIn(0f, width - w)
                canvas.drawText(bar.label, x, bottom + 17f * d, textPaint)
            }
        }
    }

    private fun select(x: Float) {
        val slot = (width - leftPad - rightPad) / bars.size
        val index = ((x - leftPad) / slot).toInt().coerceIn(0, bars.size - 1)
        if (index != selected) {
            selected = index
            invalidate()
            onBarSelected?.invoke(index)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (bars.isEmpty()) return false
        return when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true) // dragging across bars shouldn't scroll the page
                select(e.x)
                true
            }
            MotionEvent.ACTION_UP -> { performClick(); true }
            else -> super.onTouchEvent(e)
        }
    }

    override fun performClick(): Boolean = super.performClick()
}