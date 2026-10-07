package com.revix.app.track.session

import kotlin.math.cos
import kotlin.math.sqrt

class TrackGateCrossingEngine(
    val lineThresholdMeters: Double = 30.0
) {
    fun didCrossLine(
        previousLat: Double,
        previousLon: Double,
        currentLat: Double,
        currentLon: Double,
        lineStartLat: Double,
        lineStartLon: Double,
        lineEndLat: Double,
        lineEndLon: Double
    ): Boolean {
        val p1x = previousLat
        val p1y = previousLon
        val p2x = currentLat
        val p2y = currentLon
        val q1x = lineStartLat
        val q1y = lineStartLon
        val q2x = lineEndLat
        val q2y = lineEndLon

        val o1 = orientation(p1x, p1y, p2x, p2y, q1x, q1y)
        val o2 = orientation(p1x, p1y, p2x, p2y, q2x, q2y)
        val o3 = orientation(q1x, q1y, q2x, q2y, p1x, p1y)
        val o4 = orientation(q1x, q1y, q2x, q2y, p2x, p2y)

        if (o1 != o2 && o3 != o4) return true
        if (o1 == 0 && onSegment(p1x, p1y, q1x, q1y, p2x, p2y)) return true
        if (o2 == 0 && onSegment(p1x, p1y, q2x, q2y, p2x, p2y)) return true
        if (o3 == 0 && onSegment(q1x, q1y, p1x, p1y, q2x, q2y)) return true
        if (o4 == 0 && onSegment(q1x, q1y, p2x, p2y, q2x, q2y)) return true

        return false
    }

    private fun orientation(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double): Int {
        val value = (by - ay) * (cx - bx) - (bx - ax) * (cy - by)
        val epsilon = 1e-10
        return when {
            kotlin.math.abs(value) < epsilon -> 0
            value > 0 -> 1
            else -> 2
        }
    }

    private fun onSegment(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double): Boolean {
        return bx <= maxOf(ax, cx) && bx >= minOf(ax, cx) &&
            by <= maxOf(ay, cy) && by >= minOf(ay, cy)
    }

    fun isWithinStartFinishLine(
        pointLat: Double,
        pointLon: Double,
        lineStartLat: Double,
        lineStartLon: Double,
        lineEndLat: Double,
        lineEndLon: Double
    ): Boolean {
        return distanceToLineMeters(
            pointLat = pointLat,
            pointLon = pointLon,
            lineStartLat = lineStartLat,
            lineStartLon = lineStartLon,
            lineEndLat = lineEndLat,
            lineEndLon = lineEndLon
        ) <= lineThresholdMeters
    }

    fun distanceToLineMeters(
        pointLat: Double,
        pointLon: Double,
        lineStartLat: Double,
        lineStartLon: Double,
        lineEndLat: Double,
        lineEndLon: Double
    ): Double {
        val metersPerLat = 111320.0
        val metersPerLon = 111320.0 * cos(Math.toRadians(lineStartLat)).coerceAtLeast(0.0001)
        val bx = (lineEndLon - lineStartLon) * metersPerLon
        val by = (lineEndLat - lineStartLat) * metersPerLat
        val px = (pointLon - lineStartLon) * metersPerLon
        val py = (pointLat - lineStartLat) * metersPerLat
        val lenSqM = bx * bx + by * by
        if (lenSqM == 0.0) {
            return sqrt(px * px + py * py)
        }
        val t = ((px * bx + py * by) / lenSqM).coerceIn(0.0, 1.0)
        val dx = px - t * bx
        val dy = py - t * by
        return sqrt(dx * dx + dy * dy)
    }
}
