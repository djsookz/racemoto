package com.example.clinometer.track.session

import kotlin.math.sqrt

class TrackGateCrossingEngine(
    val lineThresholdMeters: Double = 30.0
) {
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
