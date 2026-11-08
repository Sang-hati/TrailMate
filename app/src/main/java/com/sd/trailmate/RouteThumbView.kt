package com.sd.trailmate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import org.osmdroid.util.GeoPoint
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

class RouteThumbView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var points: List<GeoPoint> = emptyList()
    private val d = resources.displayMetrics.density // dp -> pixel multiplier

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.trail_green)
        style = Paint.Style.STROKE
        strokeWidth = 4f * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.trail_green)
    }
    private val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.trail_earth)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.card_bg)
    }

    fun setRoute(route: List<GeoPoint>) {
        points = route
        invalidate() // ask Android to redraw this view
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2) return

        val pad = 24f * d
        val availW = width - 2 * pad
        val availH = height - 2 * pad
        if (availW <= 0 || availH <= 0) return

        // Bounding box of the route
        var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
        for (p in points) {
            minLat = min(minLat, p.latitude); maxLat = max(maxLat, p.latitude)
            minLon = min(minLon, p.longitude); maxLon = max(maxLon, p.longitude)
        }

        // Longitude degrees are shorter than latitude degrees away from the equator,
        // so shrink them by cos(latitude) to keep the real shape of the trail
        val lonScale = cos(Math.toRadians((minLat + maxLat) / 2))
        val spanX = max((maxLon - minLon) * lonScale, 1e-6)
        val spanY = max(maxLat - minLat, 1e-6)
        val scale = min(availW / spanX, availH / spanY) // same scale on both axes

        // Centre the route inside the view
        val offX = pad + (availW - spanX * scale) / 2
        val offY = pad + (availH - spanY * scale) / 2
        fun x(p: GeoPoint) = (offX + (p.longitude - minLon) * lonScale * scale).toFloat()
        fun y(p: GeoPoint) = (offY + (maxLat - p.latitude) * scale).toFloat()

        // Skip points on long routes: ~200 points look identical on a small card
        val step = max(1, points.size / 200)
        val path = Path()
        path.moveTo(x(points[0]), y(points[0]))
        for (i in 1 until points.size) {
            if (i % step == 0 || i == points.size - 1) path.lineTo(x(points[i]), y(points[i]))
        }
        canvas.drawPath(path, linePaint)

        val first = points.first()
        val last = points.last()
        canvas.drawCircle(x(first), y(first), 8f * d, ringPaint)
        canvas.drawCircle(x(first), y(first), 5.5f * d, startPaint)
        canvas.drawCircle(x(last), y(last), 8f * d, ringPaint)
        canvas.drawCircle(x(last), y(last), 5.5f * d, endPaint)
    }
}