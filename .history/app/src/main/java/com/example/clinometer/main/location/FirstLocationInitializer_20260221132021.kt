package com.example.clinometer.main.location

import android.location.Location
import com.example.clinometer.GeoPoint
import kotlin.math.cos
import kotlin.math.sin

data class FirstLocationSetup(
    val geoPoint: GeoPoint,
    val centerLat: Double,
    val centerLon: Double
)

object FirstLocationInitializer {

    fun compute(location: Location, density: Float, zoomLevel: Double): FirstLocationSetup {
        val geoPoint = GeoPoint(location.latitude, location.longitude)

        val metersPerPixel = 156543.03392 * cos(Math.toRadians(location.latitude)) / Math.pow(2.0, zoomLevel)
        val offsetMeters = 30 * density * metersPerPixel

        val bearingRad = Math.toRadians(location.bearing.toDouble())
        val offsetLat = (offsetMeters * cos(bearingRad)) / 111320.0
        val offsetLon = (offsetMeters * sin(bearingRad)) / (111320.0 * cos(Math.toRadians(location.latitude)))

        return FirstLocationSetup(
            geoPoint = geoPoint,
            centerLat = geoPoint.latitude + offsetLat,
            centerLon = geoPoint.longitude + offsetLon
        )
    }
}
