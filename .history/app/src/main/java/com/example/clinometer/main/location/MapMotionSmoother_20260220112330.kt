package com.example.clinometer.main.location

import com.example.clinometer.GeoPoint
import kotlin.math.abs

data class MapMotionSmoothingResult(
    val position: GeoPoint,
    val bearing: Float
)

object MapMotionSmoother {
    fun compute(
        currentPosition: GeoPoint,
        currentBearing: Float,
        targetPosition: GeoPoint,
        targetBearing: Float,
        progress: Float,
        distanceToManeuver: Double?
    ): MapMotionSmoothingResult {
        val smoothingFactor = when {
            distanceToManeuver == null -> 0.3f
            distanceToManeuver < 50.0 -> 0.7f
            distanceToManeuver < 100.0 -> 0.5f
            else -> 0.4f
        }

        val smoothNewLat = currentPosition.latitude + (targetPosition.latitude - currentPosition.latitude) * progress * smoothingFactor
        val smoothNewLon = currentPosition.longitude + (targetPosition.longitude - currentPosition.longitude) * progress * smoothingFactor
        val smoothPosition = GeoPoint(smoothNewLat, smoothNewLon)

        var bearingDiff = targetBearing - currentBearing
        while (bearingDiff > 180f) bearingDiff -= 360f
        while (bearingDiff < -180f) bearingDiff += 360f

        val bearingSmoothing = when {
            abs(bearingDiff) > 90f -> 0.1f
            abs(bearingDiff) > 45f -> 0.15f
            else -> 0.25f
        }

        val smoothBearing = currentBearing + bearingDiff * bearingSmoothing
        val normalizedBearing = ((smoothBearing % 360f) + 360f) % 360f

        return MapMotionSmoothingResult(
            position = smoothPosition,
            bearing = normalizedBearing
        )
    }
}
