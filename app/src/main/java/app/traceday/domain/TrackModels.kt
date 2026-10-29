package app.traceday.domain

import app.traceday.core.geo.GeoMath
import java.util.Locale

data class GeoPointData(val timestamp: Long, val lat: Double, val lon: Double)

data class TrackStats(
    val distanceMeters: Double,
    val durationMillis: Long,
    val pointCount: Int,
    val startAt: Long?,
    val endAt: Long?
)

object TrackStatsCalculator {

    private const val TELEPORT_JUMP_METERS = 1_000_000.0

    fun compute(points: List<GeoPointData>): TrackStats {
        if (points.isEmpty()) return TrackStats(0.0, 0, 0, null, null)
        val sorted = points.sortedBy { it.timestamp }
        var distance = 0.0
        var prev: GeoPointData? = null
        for (p in sorted) {
            val last = prev
            if (last != null) {
                val d = GeoMath.distanceMeters(last.lat, last.lon, p.lat, p.lon)
                if (d <= TELEPORT_JUMP_METERS) distance += d
            }
            prev = p
        }
        val first = sorted.first()
        val lastPoint = sorted.last()
        return TrackStats(
            distanceMeters = distance,
            durationMillis = (lastPoint.timestamp - first.timestamp).coerceAtLeast(0),
            pointCount = sorted.size,
            startAt = first.timestamp,
            endAt = lastPoint.timestamp
        )
    }
}

object DistanceFormatter {

    fun formatDistance(meters: Double): String = when {
        meters < 1000 -> "${meters.toInt()} м"
        meters < 100_000 -> String.format(Locale.getDefault(), "%.1f км", meters / 1000)
        else -> "${(meters / 1000).toInt()} км"
    }

    fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60000
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h <= 0 -> "$m мин"
            else -> "$h ч $m мин"
        }
    }
}
