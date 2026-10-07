package com.revix.app.utils

import android.location.Location
import com.revix.app.RoutePoint

object RoutePointDistance {
    fun distanceMeters(points: List<RoutePoint>): Double {
        if (points.size < 2) return 0.0

        var meters = 0.0
        val results = FloatArray(1)
        for (index in 1 until points.size) {
            val prev = points[index - 1].geoPoint
            val current = points[index].geoPoint
            Location.distanceBetween(
                prev.latitude,
                prev.longitude,
                current.latitude,
                current.longitude,
                results
            )
            meters += results[0].toDouble()
        }
        return meters
    }

    fun distanceKm(points: List<RoutePoint>): Double = distanceMeters(points) / 1000.0
}
