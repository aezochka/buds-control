package dev.aezochka.budscontrol.data

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Расчёты по записанной прогулке. Всё считается из реальных точек GPS
 * и показаний шагомера — никаких придуманных значений.
 */
object HistoryMath {

    /** Расстояние между двумя точками по формуле гаверсинуса, в метрах. */
    fun distanceMeters(a: TrackPoint, b: TrackPoint): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    fun totalDistance(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        var sum = 0.0
        for (i in 0 until points.lastIndex) sum += distanceMeters(points[i], points[i + 1])
        return sum
    }

    fun durationMillis(session: WalkSession, nowMillis: Long = System.currentTimeMillis()): Long {
        // Активная прогулка тикает до «сейчас», иначе время замирало на месте.
        val end = session.endedAtMillis
            ?: maxOf(session.lastSeenMillis ?: 0L, session.points.lastOrNull()?.timeMillis ?: 0L, nowMillis)
        return (end - session.startedAtMillis).coerceAtLeast(0)
    }

    /** Границы маршрута для подбора зума карты. */
    fun bounds(points: List<TrackPoint>): DoubleArray? {
        if (points.isEmpty()) return null
        return doubleArrayOf(
            points.minOf { it.latitude }, points.maxOf { it.latitude },
            points.minOf { it.longitude }, points.maxOf { it.longitude },
        )
    }

    fun steps(session: WalkSession): Long? {
        val start = session.startSteps ?: return null
        val end = session.endSteps ?: session.points.lastOrNull()?.steps?.plus(start) ?: return null
        return (end - start).coerceAtLeast(0)
    }

    /**
     * Нормализует точки в координаты 0..1 для рисования на Canvas.
     * Сохраняет пропорции, чтобы маршрут не растягивался.
     */
    fun normalize(points: List<TrackPoint>): List<Pair<Float, Float>> {
        if (points.isEmpty()) return emptyList()
        val lats = points.map { it.latitude }
        val lons = points.map { it.longitude }
        val minLat = lats.min(); val maxLat = lats.max()
        val minLon = lons.min(); val maxLon = lons.max()
        // Поправка на широту: 1° долготы короче 1° широты.
        val midLat = Math.toRadians((minLat + maxLat) / 2)
        val spanLat = (maxLat - minLat).coerceAtLeast(1e-6)
        val spanLon = ((maxLon - minLon) * cos(midLat)).coerceAtLeast(1e-6)
        val span = maxOf(spanLat, spanLon)
        return points.map { p ->
            val x = ((p.longitude - minLon) * cos(midLat) / span).toFloat()
            val y = (1.0 - (p.latitude - minLat) / span).toFloat()
            x to y
        }
    }

    fun formatDistance(meters: Double): String = when {
        meters >= 1000 -> String.format("%.2f", meters / 1000)
        else -> meters.toInt().toString()
    }

    fun distanceUnit(meters: Double): String = if (meters >= 1000) "км" else "м"

    fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "$hours ч $minutes мин" else "$minutes мин"
    }
}
