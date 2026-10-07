package com.revix.app.track

import kotlin.math.cos
import kotlin.math.hypot

/**
 * Start-line direction from point order (P0 → P1).
 *
 * The arrow is the clockwise 90° perpendicular of P0→P1 in local east/north.
 * That matches GPS-placed gates: P0 is to the right of travel, P1 to the left,
 * so the arrow points along the recorded drive. Swapping the two points reverses it.
 *
 * A valid start crossing is previous on the left (positive [lineSide]) and
 * current on the right (negative), i.e. through the line with the arrow.
 */
object TrackGateDirection {
    data class LatLon(
        val latitude: Double,
        val longitude: Double
    )

    fun arrowPolylines(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double
    ): List<List<LatLon>> {
        val midLat = (startLatitude + endLatitude) / 2.0
        val midLon = (startLongitude + endLongitude) / 2.0
        val metersPerLat = 111_320.0
        val metersPerLon = 111_320.0 * cos(Math.toRadians(midLat)).coerceAtLeast(0.0001)
        val east = (endLongitude - startLongitude) * metersPerLon
        val north = (endLatitude - startLatitude) * metersPerLat
        val lengthMeters = hypot(east, north)
        if (lengthMeters < 0.5) return emptyList()

        val unitEast = east / lengthMeters
        val unitNorth = north / lengthMeters
        val forwardEast = unitNorth
        val forwardNorth = -unitEast
        val rightEast = forwardNorth
        val rightNorth = -forwardEast

        val shaftMeters = (lengthMeters * 0.22).coerceIn(2.4, 5.5)
        val headLengthMeters = shaftMeters * 0.40
        val headHalfWidthMeters = shaftMeters * 0.24

        val mid = LatLon(midLat, midLon)
        val tip = offsetMeters(mid, forwardEast * shaftMeters, forwardNorth * shaftMeters)
        val headBaseEast = forwardEast * (shaftMeters - headLengthMeters)
        val headBaseNorth = forwardNorth * (shaftMeters - headLengthMeters)
        val left = offsetMeters(
            mid,
            headBaseEast + rightEast * headHalfWidthMeters,
            headBaseNorth + rightNorth * headHalfWidthMeters
        )
        val right = offsetMeters(
            mid,
            headBaseEast - rightEast * headHalfWidthMeters,
            headBaseNorth - rightNorth * headHalfWidthMeters
        )

        return listOf(
            listOf(mid, tip),
            listOf(left, tip, right)
        )
    }

    fun lineSide(
        lineStartLat: Double,
        lineStartLon: Double,
        lineEndLat: Double,
        lineEndLon: Double,
        pointLat: Double,
        pointLon: Double
    ): Double {
        val ax = lineStartLon
        val ay = lineStartLat
        val bx = lineEndLon
        val by = lineEndLat
        val px = pointLon
        val py = pointLat
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax)
    }

    fun isCrossingWithArrow(
        previousLat: Double,
        previousLon: Double,
        currentLat: Double,
        currentLon: Double,
        lineStartLat: Double,
        lineStartLon: Double,
        lineEndLat: Double,
        lineEndLon: Double
    ): Boolean {
        val previousSide = lineSide(
            lineStartLat = lineStartLat,
            lineStartLon = lineStartLon,
            lineEndLat = lineEndLat,
            lineEndLon = lineEndLon,
            pointLat = previousLat,
            pointLon = previousLon
        )
        val currentSide = lineSide(
            lineStartLat = lineStartLat,
            lineStartLon = lineStartLon,
            lineEndLat = lineEndLat,
            lineEndLon = lineEndLon,
            pointLat = currentLat,
            pointLon = currentLon
        )
        return previousSide > 0.0 && currentSide < 0.0
    }

    private fun offsetMeters(origin: LatLon, eastMeters: Double, northMeters: Double): LatLon {
        val metersPerLat = 111_320.0
        val metersPerLon = 111_320.0 * cos(Math.toRadians(origin.latitude)).coerceAtLeast(0.0001)
        return LatLon(
            latitude = origin.latitude + northMeters / metersPerLat,
            longitude = origin.longitude + eastMeters / metersPerLon
        )
    }
}
