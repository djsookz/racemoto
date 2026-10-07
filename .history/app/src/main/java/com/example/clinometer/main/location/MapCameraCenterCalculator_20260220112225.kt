package com.example.clinometer.main.location

import com.example.clinometer.GeoPoint
import kotlin.math.cos
import kotlin.math.sin

data class CameraCenterResult(
    val latitude: Double,
    val longitude: Double
)

object MapCameraCenterCalculator {
    fun compute(
        currentPosition: GeoPoint,
        currentBearing: Float,
        isNorthUpMode: Boolean,
        isLandscape: Boolean,
        density: Float,
        currentZoomValue: Double,
        currentCenterLatitude: Double?,
        currentCenterLongitude: Double?
    ): CameraCenterResult {
        val metersPerPixel = 156543.03392 * cos(Math.toRadians(currentPosition.latitude)) / Math.pow(2.0, currentZoomValue)

        var offsetMeters = 30 * density * metersPerPixel
        if (isNorthUpMode || isLandscape) {
            offsetMeters = 0.0
        }

        val bearingRad = Math.toRadians(currentBearing.toDouble())
        val offsetLat = (offsetMeters * cos(bearingRad)) / 111320.0
        val offsetLon = (offsetMeters * sin(bearingRad)) / (111320.0 * cos(Math.toRadians(currentPosition.latitude)))

        val newLat = currentPosition.latitude + offsetLat
        val newLon = currentPosition.longitude + offsetLon

        if (currentCenterLatitude == null || currentCenterLongitude == null) {
            return CameraCenterResult(newLat, newLon)
        }

        val latDiff = newLat - currentCenterLatitude
        val lonDiff = newLon - currentCenterLongitude

        return CameraCenterResult(
            latitude = currentCenterLatitude + latDiff * 0.12,
            longitude = currentCenterLongitude + lonDiff * 0.12
        )
    }
}
