package com.sd.trailmate

import org.osmdroid.util.GeoPoint
import java.util.Locale

object Utils {
    // 3725 seconds -> "01:02:05"
    fun formatDuration(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    // "22.5,88.3;22.6,88.4" -> list of map points. Bad entries are skipped, not crashed on.
    fun parseRoute(raw: String): List<GeoPoint> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { pair ->
            try {
                val p = pair.split(",")
                GeoPoint(p[0].toDouble(), p[1].toDouble())
            } catch (e: Exception) {
                null
            }
        }
    }
}