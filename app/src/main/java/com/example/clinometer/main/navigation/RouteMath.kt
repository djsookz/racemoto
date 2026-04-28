package com.example.clinometer.main.navigation

import com.example.clinometer.GeoPoint
import com.example.clinometer.navigation.DirectionsStep

object RouteMath {
    fun calculateDistanceToManeuver(currentLocation: GeoPoint, step: DirectionsStep): Double {
        step.maneuver?.location?.let { loc ->
            if (loc.size >= 2) {
                val maneuverPoint = GeoPoint(loc[1], loc[0])
                return currentLocation.distanceToAsDouble(maneuverPoint)
            }
        }
        return step.distance
    }

    fun formatDistance(meters: Double): String {
        return when {
            meters >= 1000 -> String.format("%.1f km", meters / 1000)
            meters >= 100 -> String.format("%.0f m", meters)
            else -> String.format("%.0f m", meters)
        }
    }
}
