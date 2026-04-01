package com.example.clinometer.main.navigation

import com.example.clinometer.GeoPoint
import com.example.clinometer.navigation.DirectionsStep
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object RouteMath {
    fun formatDistanceCompact(meters: Double): String {
        return if (meters >= 1000) {
            String.format("%.2f", meters / 1000.0)
        } else {
            String.format("%.0f m", meters)
        }
    }

    fun formatDurationMinutesBucket(minutes: Int): String {
        val hrs = minutes / 60
        val mins = minutes % 60
        return if (hrs > 0) {
            String.format("%d:%02d", hrs, mins)
        } else {
            String.format("%02d min", mins)
        }
    }

    fun formatEta(etaMillis: Long): String {
        val df = SimpleDateFormat("HH:mm", Locale.getDefault())
        return df.format(Date(etaMillis))
    }

    fun calculateDistanceToManeuver(currentLocation: GeoPoint, step: DirectionsStep): Double {
        step.maneuver?.location?.let { loc ->
            if (loc.size >= 2) {
                val maneuverPoint = GeoPoint(loc[1], loc[0])
                return currentLocation.distanceToAsDouble(maneuverPoint)
            }
        }
        return step.distance
    }

    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMeters = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }

    fun formatDistance(meters: Double): String {
        return when {
            meters >= 1000 -> String.format("%.1f km", meters / 1000)
            meters >= 100 -> String.format("%.0f m", meters)
            else -> String.format("%.0f m", meters)
        }
    }
}
