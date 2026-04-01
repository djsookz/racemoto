package com.example.clinometer.main.location

import android.location.Location
import android.os.SystemClock
import com.example.clinometer.GeoPoint
import kotlin.math.sqrt

class KalmanLocationFilter(private val qMetersPerSecond: Float = 3f) {
    private var timestamp = 0L
    private var lat = 0.0
    private var lng = 0.0
    private var variance = -1.0

    fun process(location: Location): Location {
        val accuracy = location.accuracy.toDouble()

        if (variance < 0) {
            timestamp = location.time
            lat = location.latitude
            lng = location.longitude
            variance = accuracy * accuracy
        } else {
            val dt = (location.time - timestamp) / 1000.0
            if (dt > 0) {
                variance += dt * qMetersPerSecond * qMetersPerSecond
                timestamp = location.time
                val k = variance / (variance + accuracy * accuracy)
                lat += k * (location.latitude - lat)
                lng += k * (location.longitude - lng)
                variance = (1 - k) * variance
            }
        }

        return Location(location).apply {
            latitude = lat
            longitude = lng
            time = timestamp
            this.accuracy = sqrt(variance).toFloat()
        }
    }
}

class MotionPredictor {
    private data class MotionState(
        val position: GeoPoint,
        val velocity: DoubleArray,
        val timestamp: Long,
        val bearing: Float,
        val speed: Float
    )

    private val history = mutableListOf<MotionState>()
    private val maxHistory = 5

    fun addSample(position: GeoPoint, bearing: Float, speed: Float) {
        val now = SystemClock.elapsedRealtime()

        val velocity = if (history.isNotEmpty()) {
            val last = history.last()
            val dt = (now - last.timestamp) / 1000.0
            if (dt > 0) {
                doubleArrayOf(
                    (position.latitude - last.position.latitude) / dt,
                    (position.longitude - last.position.longitude) / dt
                )
            } else {
                doubleArrayOf(0.0, 0.0)
            }
        } else {
            doubleArrayOf(0.0, 0.0)
        }

        history.add(MotionState(position, velocity, now, bearing, speed))
        if (history.size > maxHistory) {
            history.removeAt(0)
        }
    }
}
