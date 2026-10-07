package com.revix.app.track

import com.revix.app.RoutePoint
import kotlin.math.roundToInt

/**
 * Shared map-route prep: normalize timestamps, then lightly downsample for display only.
 * Full-fidelity telemetry stays in TrackLapDataStore / binary; this only affects RouteStorage → map line.
 */
fun normalizeRoutePointsForMap(points: List<RoutePoint>): List<RoutePoint> {
    if (points.isEmpty()) return emptyList()

    val baseTimestamp = points.first().timestamp
    var normalized = points.map { point ->
        point.copy(
            timestamp = point.timestamp - baseTimestamp,
            absoluteTime = if (point.absoluteTime > 0L) point.absoluteTime else point.timestamp
        )
    }

    if (normalized.all { it.timestamp == normalized.first().timestamp } && normalized.size > 1) {
        normalized = normalized.mapIndexed { index, point ->
            point.copy(timestamp = index * 100L)
        }
    }

    val span = normalized.last().timestamp - normalized.first().timestamp
    if (span <= 500L && normalized.size > 1) {
        val step = 1000f / normalized.size.toFloat()
        normalized = normalized.mapIndexed { index, point ->
            point.copy(timestamp = (index * step).toLong())
        }
    }

    return downsampleRoutePointsForDisplay(normalized)
}

/** Keep first/last; uniformly thin the middle when point count is huge (RaceBox 25 Hz). */
fun downsampleRoutePointsForDisplay(
    points: List<RoutePoint>,
    maxPoints: Int = 2500
): List<RoutePoint> {
    if (points.size <= maxPoints || maxPoints < 3) return points

    val out = ArrayList<RoutePoint>(maxPoints)
    out.add(points.first())
    val lastIndex = points.lastIndex
    val step = lastIndex.toFloat() / (maxPoints - 1).toFloat()
    for (i in 1 until maxPoints - 1) {
        val index = (i * step).roundToInt().coerceIn(1, lastIndex - 1)
        out.add(points[index])
    }
    out.add(points.last())
    return out
}
