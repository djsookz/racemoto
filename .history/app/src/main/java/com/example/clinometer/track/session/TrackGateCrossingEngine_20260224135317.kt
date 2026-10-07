package com.example.clinometer.track.session

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

    private fun direction(
        ax: Double,
        ay: Double,
        bx: Double,
        by: Double,
        cx: Double,
        cy: Double
    ): Double {
        return (cx - ax) * (by - ay) - (bx - ax) * (cy - ay)
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
        val a = pointLat - lineStartLat
        val b = pointLon - lineStartLon
        val c = lineEndLat - lineStartLat
        val d = lineEndLon - lineStartLon

        val dot = a * c + b * d
        val lenSq = c * c + d * d

        if (lenSq == 0.0) {
            val dLat = pointLat - lineStartLat
            val dLon = pointLon - lineStartLon
            return sqrt(dLat * dLat + dLon * dLon) * 111000.0
        }

        val param = dot / lenSq

        val nearestLat: Double
        val nearestLon: Double

        if (param < 0.0) {
            nearestLat = lineStartLat
            nearestLon = lineStartLon
        } else if (param > 1.0) {
            nearestLat = lineEndLat
            nearestLon = lineEndLon
        } else {
            nearestLat = lineStartLat + param * c
            nearestLon = lineStartLon + param * d
        }

        val dx = pointLat - nearestLat
        val dy = pointLon - nearestLon

        return sqrt(dx * dx + dy * dy) * 111000.0
    }
}
