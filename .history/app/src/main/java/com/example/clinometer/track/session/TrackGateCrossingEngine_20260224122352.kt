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
        val d1 = direction(lineStartLat, lineStartLon, lineEndLat, lineEndLon, previousLat, previousLon)
        val d2 = direction(lineStartLat, lineStartLon, lineEndLat, lineEndLon, currentLat, currentLon)
        val d3 = direction(previousLat, previousLon, currentLat, currentLon, lineStartLat, lineStartLon)
        val d4 = direction(previousLat, previousLon, currentLat, currentLon, lineEndLat, lineEndLon)

        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
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
