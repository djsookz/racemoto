package com.revix.app.track

import kotlin.math.abs

object TrackGateLineCrossing {
    /**
     * Returns the interpolation ratio [0, 1] along the movement segment (previous -> current)
     * where the segment intersects the gate line, or null when no intersection exists.
     */
    fun interpolateSegmentRatio(
        previousLat: Double,
        previousLon: Double,
        currentLat: Double,
        currentLon: Double,
        lineStartLat: Double,
        lineStartLon: Double,
        lineEndLat: Double,
        lineEndLon: Double
    ): Float? {
        val x1 = previousLon
        val y1 = previousLat
        val x2 = currentLon
        val y2 = currentLat
        val x3 = lineStartLon
        val y3 = lineStartLat
        val x4 = lineEndLon
        val y4 = lineEndLat

        val denominator = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if (abs(denominator) < 1e-12) return null

        val t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denominator
        val u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / denominator
        if (t < 0.0 || t > 1.0 || u < 0.0 || u > 1.0) return null
        return t.toFloat()
    }
}
