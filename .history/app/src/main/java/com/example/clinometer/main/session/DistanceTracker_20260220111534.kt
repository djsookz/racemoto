package com.example.clinometer.main.session

import com.example.clinometer.GeoPoint

class DistanceTracker {
    var totalDistanceKm: Double = 0.0
        private set

    var lastDistancePoint: GeoPoint? = null
        private set

    private val _distancePoints = mutableListOf<GeoPoint>()
    val distancePoints: MutableList<GeoPoint>
        get() = _distancePoints

    fun reset() {
        totalDistanceKm = 0.0
        lastDistancePoint = null
        _distancePoints.clear()
    }

    fun seedIfNeeded(point: GeoPoint) {
        if (lastDistancePoint == null) {
            lastDistancePoint = point
            _distancePoints.add(point)
        }
    }

    fun addPointAndAccumulate(point: GeoPoint, minDistanceMeters: Double = 1.0): Boolean {
        val previous = lastDistancePoint
        if (previous != null) {
            val distanceMeters = previous.distanceToAsDouble(point)
            if (distanceMeters > minDistanceMeters) {
                totalDistanceKm += distanceMeters / 1000.0
                lastDistancePoint = point
                _distancePoints.add(point)
                return true
            }
            return false
        }

        lastDistancePoint = point
        _distancePoints.add(point)
        return false
    }

    fun replaceWith(points: List<GeoPoint>) {
        _distancePoints.clear()
        _distancePoints.addAll(points)
        recalculateTotalDistance()
        lastDistancePoint = _distancePoints.lastOrNull()
    }

    fun recalculateTotalDistance() {
        totalDistanceKm = 0.0
        if (_distancePoints.size >= 2) {
            for (i in 1 until _distancePoints.size) {
                totalDistanceKm += _distancePoints[i - 1].distanceToAsDouble(_distancePoints[i]) / 1000.0
            }
        }
    }

    fun restoreTotalDistance(totalDistanceKm: Double) {
        this.totalDistanceKm = totalDistanceKm
    }
}
