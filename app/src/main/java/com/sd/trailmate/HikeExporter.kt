package com.sd.trailmate

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

// Two ways to take a hike out of the app:
//  1. shareGpx()  : a .gpx file (opens in Google Earth, Komoot, Gaia GPS, etc.)
//  2. shareCard() : a picture with the route and stats, ready for WhatsApp / Instagram
object HikeExporter {

    // "lat,lng;lat,lng;..." (how the route is stored) -> list of (lat, lng)
    private fun route(raw: String): List<Pair<Double, Double>> =
        raw.split(";").mapNotNull {
            val p = it.split(",")
            val lat = p.getOrNull(0)?.toDoubleOrNull()
            val lng = p.getOrNull(1)?.toDoubleOrNull()
            if (lat != null && lng != null) lat to lng else null
        }

    private fun xml(s: String) =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun safeName(s: String) = s.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifBlank { "hike" }

    private fun exportDir(ctx: Context) = File(ctx.cacheDir, "exports").apply { mkdirs() }

    private fun send(ctx: Context, file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)   // lets the receiving app read this one file
        }
        ctx.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---------- GPX ----------
    fun shareGpx(ctx: Context, hike: Hike) {
        val pts = route(hike.routePoints)
        if (pts.size < 2) {
            Toast.makeText(ctx, "This hike has no route to export", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val sb = StringBuilder()
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            sb.append("<gpx version=\"1.1\" creator=\"TrailMate\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
            sb.append("  <metadata><name>${xml(hike.name)}</name></metadata>\n")
            sb.append("  <trk>\n    <name>${xml(hike.name)}</name>\n")
            sb.append("    <desc>${xml(hike.date)} | ${String.format(Locale.US, "%.2f", hike.distanceKm)} km</desc>\n")
            sb.append("    <trkseg>\n")
            for ((lat, lng) in pts) {
                sb.append("      <trkpt lat=\"$lat\" lon=\"$lng\"/>\n")
            }
            sb.append("    </trkseg>\n  </trk>\n</gpx>\n")

            val file = File(exportDir(ctx), "${safeName(hike.name)}.gpx")
            file.writeText(sb.toString())
            send(ctx, file, "application/gpx+xml", "Export ${hike.name}")
        } catch (e: Exception) {
            Toast.makeText(ctx, "Could not export the hike", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- share card (PNG) ----------
    fun shareCard(ctx: Context, hike: Hike) {
        try {
            val bmp = drawCard(ctx, hike)
            val file = File(exportDir(ctx), "${safeName(hike.name)}_card.png")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            send(ctx, file, "image/png", "Share ${hike.name}")
        } catch (e: Exception) {
            Toast.makeText(ctx, "Could not create the share card", Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawCard(ctx: Context, hike: Hike): Bitmap {
        // Always use the dark palette (values-night/colors.xml), whatever mode the phone is in,
        // so the picture looks the same wherever it is shared. No hex values: they come from your colors.xml.
        val nightConfig = Configuration(ctx.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
        val dark = ctx.createConfigurationContext(nightConfig)
        fun color(id: Int) = ContextCompat.getColor(dark, id)
        val bg = color(R.color.surface_bg)
        val panel = color(R.color.card_bg)
        val primaryText = color(R.color.text_primary)
        val secondaryText = color(R.color.text_secondary)
        val green = color(R.color.trail_green)
        val earth = color(R.color.trail_earth)

        val w = 1080
        val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bg)

        val text = Paint(Paint.ANTI_ALIAS_FLAG)

        // Title + date
        text.color = primaryText
        text.textSize = 72f
        text.typeface = Typeface.DEFAULT_BOLD
        c.drawText(hike.name.take(22), 70f, 130f, text)
        text.color = secondaryText
        text.textSize = 38f
        text.typeface = Typeface.DEFAULT
        val sub = if (hike.startPlace.isNotBlank()) "${hike.date}  •  ${hike.startPlace}" else hike.date
        c.drawText(sub.take(40), 70f, 195f, text)

        // Route, scaled to fit the box. Longitude is shrunk by cos(latitude) so the shape isn't stretched.
        val box = RectF(70f, 250f, w - 70f, 930f)
        c.drawRoundRect(box, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = panel })   // route panel
        val pts = route(hike.routePoints)
        if (pts.size >= 2) {
            val midLat = (pts.minOf { it.first } + pts.maxOf { it.first }) / 2
            val k = cos(Math.toRadians(midLat))
            val xs = pts.map { it.second * k }
            val ys = pts.map { -it.first }              // minus: north is up on screen
            val minX = xs.min(); val maxX = xs.max()
            val minY = ys.min(); val maxY = ys.max()
            val spanX = max(maxX - minX, 1e-9)
            val spanY = max(maxY - minY, 1e-9)
            val pad = 40f
            val scale = min((box.width() - 2 * pad) / spanX, (box.height() - 2 * pad) / spanY)
            val offX = box.left + (box.width() - spanX * scale).toFloat() / 2
            val offY = box.top + (box.height() - spanY * scale).toFloat() / 2
            fun px(i: Int) = (offX + (xs[i] - minX) * scale).toFloat()
            fun py(i: Int) = (offY + (ys[i] - minY) * scale).toFloat()

            val path = Path().apply {
                moveTo(px(0), py(0))
                for (i in 1 until pts.size) lineTo(px(i), py(i))
            }
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 12f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = green
            }
            c.drawPath(path, line)
            val dot = Paint(Paint.ANTI_ALIAS_FLAG)
            dot.color = earth                       // start
            c.drawCircle(px(0), py(0), 20f, dot)
            dot.color = green                       // end
            c.drawCircle(px(pts.size - 1), py(pts.size - 1), 20f, dot)
        }

        // Stats row
        val stats = mutableListOf(
            String.format(Locale.US, "%.2f km", hike.distanceKm) to "Distance",
            Utils.formatDuration(hike.durationSec) to "Time"
        )
        if (hike.steps > 0) stats.add(String.format(Locale.US, "%,d", hike.steps) to "Steps")
        val colW = (w - 140f) / stats.size
        stats.forEachIndexed { i, (value, label) ->
            val cx = 70f + colW * i + colW / 2
            text.textAlign = Paint.Align.CENTER
            text.color = primaryText
            text.textSize = 64f
            text.typeface = Typeface.DEFAULT_BOLD
            c.drawText(value, cx, 1060f, text)
            text.color = secondaryText
            text.textSize = 34f
            text.typeface = Typeface.DEFAULT
            c.drawText(label, cx, 1110f, text)
        }

        // Footer
        text.textAlign = Paint.Align.CENTER
        text.color = green
        text.textSize = 40f
        text.typeface = Typeface.DEFAULT_BOLD
        c.drawText("TrailMate", w / 2f, 1270f, text)
        return bmp
    }
}