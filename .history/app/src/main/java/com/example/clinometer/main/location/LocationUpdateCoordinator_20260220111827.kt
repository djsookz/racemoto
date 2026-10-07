package com.example.clinometer.main.location

import android.location.Location
import com.example.clinometer.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class LocationUpdateComputation(
    val filteredLocation: Location,
    val geoPoint: GeoPoint,
    val calculatedBearing: Float,
    val targetMapOrientation: Float
)

object LocationUpdateCoordinator {
    fun compute(
        location: Location,
        speed: Float,
        lastProcessedLocation: Location?,
        isNorthUpMode: Boolean,
        kalmanFilter: KalmanLocationFilter
    ): LocationUpdateComputation {
        val filtered = kalmanFilter.process(location)
        val geoPoint = GeoPoint(filtered.latitude, filtered.longitude)

        var calculatedBearing = location.bearing

        if (lastProcessedLocation != null && speed > 1f) {
            val lastGeoPoint = GeoPoint(
                lastProcessedLocation.latitude,
                lastProcessedLocation.longitude
            )

            val distance = geoPoint.distanceToAsDouble(lastGeoPoint)

            if (distance > 0.3) {
                val lat1 = Math.toRadians(lastGeoPoint.latitude)
                val lat2 = Math.toRadians(geoPoint.latitude)
                val deltaLon = Math.toRadians(geoPoint.longitude - lastGeoPoint.longitude)

                val x = sin(deltaLon) * cos(lat2)
                val y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)

                var movementBearing = Math.toDegrees(atan2(x, y)).toFloat()
                if (movementBearing < 0) movementBearing += 360f

                calculatedBearing = when {
                    speed > 20f -> movementBearing * 0.1f + location.bearing * 0.9f
                    speed > 5f -> movementBearing * 0.5f + location.bearing * 0.5f
                    else -> location.bearing
                }
            }
        }

        val targetMapOrientation = when {
            isNorthUpMode -> 0f
            speed > 2f -> -calculatedBearing
            else -> 0f
        }

        return LocationUpdateComputation(
            filteredLocation = filtered,
            geoPoint = geoPoint,
            calculatedBearing = calculatedBearing,
            targetMapOrientation = targetMapOrientation
        )
    }
}
