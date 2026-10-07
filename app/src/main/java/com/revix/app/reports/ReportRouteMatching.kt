package com.revix.app.reports

import android.location.Location
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import kotlin.math.abs

internal object ReportRouteMatching {
    data class RouteProjection(
        val distanceFromStartMeters: Double,
        val crossTrackMeters: Double
    )

    fun distanceAlongRouteAheadMeters(
        userLat: Double,
        userLon: Double,
        reportLat: Double,
        reportLon: Double,
        route: LineString,
        routeToleranceMeters: Double
    ): Float? {
        val userProjection = projectOntoRoute(userLat, userLon, route) ?: return null
        val reportProjection = projectOntoRoute(reportLat, reportLon, route) ?: return null

        if (reportProjection.crossTrackMeters > routeToleranceMeters) {
            return null
        }

        val aheadMeters = reportProjection.distanceFromStartMeters - userProjection.distanceFromStartMeters
        if (aheadMeters < -30.0) {
            return null
        }

        return aheadMeters.coerceAtLeast(0.0).toFloat()
    }

    fun crossTrackDistanceMeters(lat: Double, lon: Double, route: LineString): Double {
        return projectOntoRoute(lat, lon, route)?.crossTrackMeters ?: Double.MAX_VALUE
    }

    /**
     * Signed distance from the report to the user along the route polyline.
     * Positive = user has not reached the report yet; negative = user is past it.
     */
    fun metersUserPastReportOnRoute(
        userLat: Double,
        userLon: Double,
        reportLat: Double,
        reportLon: Double,
        route: LineString,
        routeToleranceMeters: Double
    ): Double? {
        val userProjection = projectOntoRoute(userLat, userLon, route) ?: return null
        val reportProjection = projectOntoRoute(reportLat, reportLon, route) ?: return null
        if (reportProjection.crossTrackMeters > routeToleranceMeters) {
            return null
        }
        return userProjection.distanceFromStartMeters - reportProjection.distanceFromStartMeters
    }

    private fun projectOntoRoute(lat: Double, lon: Double, route: LineString): RouteProjection? {
        val coordinates = route.coordinates()
        if (coordinates.size < 2) {
            return null
        }

        var traversedMeters = 0.0
        var bestCrossTrack = Double.MAX_VALUE
        var bestDistanceFromStart = 0.0

        for (index in 0 until coordinates.size - 1) {
            val start = coordinates[index]
            val end = coordinates[index + 1]
            val segmentMeters = segmentLengthMeters(
                start.latitude(),
                start.longitude(),
                end.latitude(),
                end.longitude()
            )

            val projection = projectPointOntoSegment(
                pointLat = lat,
                pointLon = lon,
                startLat = start.latitude(),
                startLon = start.longitude(),
                endLat = end.latitude(),
                endLon = end.longitude(),
                segmentMeters = segmentMeters
            )

            if (projection.crossTrackMeters < bestCrossTrack) {
                bestCrossTrack = projection.crossTrackMeters
                bestDistanceFromStart = traversedMeters + projection.alongSegmentMeters
            }

            traversedMeters += segmentMeters
        }

        if (bestCrossTrack == Double.MAX_VALUE) {
            return null
        }

        return RouteProjection(
            distanceFromStartMeters = bestDistanceFromStart,
            crossTrackMeters = bestCrossTrack
        )
    }

    private data class SegmentProjection(
        val alongSegmentMeters: Double,
        val crossTrackMeters: Double
    )

    private fun projectPointOntoSegment(
        pointLat: Double,
        pointLon: Double,
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        segmentMeters: Double
    ): SegmentProjection {
        if (segmentMeters <= 0.0) {
            val crossTrack = distanceMeters(pointLat, pointLon, startLat, startLon).toDouble()
            return SegmentProjection(alongSegmentMeters = 0.0, crossTrackMeters = crossTrack)
        }

        val startToPoint = distanceMeters(startLat, startLon, pointLat, pointLon).toDouble()
        val startToEnd = segmentMeters
        val endToPoint = distanceMeters(endLat, endLon, pointLat, pointLon).toDouble()

        val clampedAlong = when {
            startToPoint == 0.0 -> 0.0
            endToPoint == 0.0 -> startToEnd
            else -> {
                val rawFraction = ((startToPoint * startToPoint) - (endToPoint * endToPoint) + (startToEnd * startToEnd)) /
                    (2.0 * startToEnd * startToEnd)
                rawFraction.coerceIn(0.0, 1.0) * startToEnd
            }
        }

        val projectedLat = startLat + (endLat - startLat) * (clampedAlong / startToEnd)
        val projectedLon = startLon + (endLon - startLon) * (clampedAlong / startToEnd)
        val crossTrack = distanceMeters(pointLat, pointLon, projectedLat, projectedLon).toDouble()

        return SegmentProjection(
            alongSegmentMeters = clampedAlong,
            crossTrackMeters = crossTrack
        )
    }

    fun isWithinDrivingCorridor(
        userLat: Double,
        userLon: Double,
        userBearing: Float,
        reportLat: Double,
        reportLon: Double,
        recentTrail: List<Location>,
        corridorHalfWidthMeters: Float
    ): Boolean {
        val crossTrackToTrail = minCrossTrackToTrailMeters(recentTrail, reportLat, reportLon)
        if (crossTrackToTrail <= corridorHalfWidthMeters) {
            return true
        }

        val straightDistance = distanceMeters(userLat, userLon, reportLat, reportLon)
        val lateralMeters = estimateLateralOffsetMeters(
            userLat = userLat,
            userLon = userLon,
            userBearing = userBearing,
            targetLat = reportLat,
            targetLon = reportLon,
            straightDistance = straightDistance
        )
        val forwardMeters = estimateForwardOffsetMeters(
            userLat = userLat,
            userLon = userLon,
            userBearing = userBearing,
            targetLat = reportLat,
            targetLon = reportLon,
            straightDistance = straightDistance
        )

        return forwardMeters >= 20f && lateralMeters <= corridorHalfWidthMeters
    }

    fun isApproachingReport(
        recentTrail: List<Location>,
        currentLat: Double,
        currentLon: Double,
        reportLat: Double,
        reportLon: Double,
        currentDistance: Float
    ): Boolean {
        val samples = buildList {
            recentTrail.takeLast(5).forEach { add(it.latitude to it.longitude) }
            add(currentLat to currentLon)
        }
        if (samples.size < 3) {
            return currentDistance <= 120f
        }

        val distances = samples.map { (lat, lon) ->
            distanceMeters(lat, lon, reportLat, reportLon)
        }

        var decreasingSteps = 0
        for (index in 1 until distances.size) {
            if (distances[index] + 4f < distances[index - 1]) {
                decreasingSteps++
            }
        }

        val netClosing = distances.first() - distances.last()
        return decreasingSteps >= 2 || netClosing >= 18f
    }

    fun maxRecentLateralOffsetMeters(
        recentTrail: List<Location>,
        userBearing: Float,
        reportLat: Double,
        reportLon: Double
    ): Float {
        if (recentTrail.isEmpty()) {
            return Float.MAX_VALUE
        }

        return recentTrail.takeLast(6).maxOf { location ->
            val distance = distanceMeters(location.latitude, location.longitude, reportLat, reportLon)
            estimateLateralOffsetMeters(
                userLat = location.latitude,
                userLon = location.longitude,
                userBearing = userBearing,
                targetLat = reportLat,
                targetLon = reportLon,
                straightDistance = distance
            )
        }
    }

    fun minCrossTrackToTrailMeters(
        recentTrail: List<Location>,
        reportLat: Double,
        reportLon: Double
    ): Float {
        if (recentTrail.size < 2) {
            return Float.MAX_VALUE
        }

        var minCrossTrack = Float.MAX_VALUE
        for (index in 0 until recentTrail.size - 1) {
            val start = recentTrail[index]
            val end = recentTrail[index + 1]
            val crossTrack = pointToSegmentCrossTrackMeters(
                pointLat = reportLat,
                pointLon = reportLon,
                startLat = start.latitude,
                startLon = start.longitude,
                endLat = end.latitude,
                endLon = end.longitude
            )
            minCrossTrack = minOf(minCrossTrack, crossTrack)
        }
        return minCrossTrack
    }

    private fun pointToSegmentCrossTrackMeters(
        pointLat: Double,
        pointLon: Double,
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double
    ): Float {
        val segmentMeters = segmentLengthMeters(startLat, startLon, endLat, endLon)
        return projectPointOntoSegment(
            pointLat = pointLat,
            pointLon = pointLon,
            startLat = startLat,
            startLon = startLon,
            endLat = endLat,
            endLon = endLon,
            segmentMeters = segmentMeters
        ).crossTrackMeters.toFloat()
    }

    private fun estimateLateralOffsetMeters(
        userLat: Double,
        userLon: Double,
        userBearing: Float,
        targetLat: Double,
        targetLon: Double,
        straightDistance: Float
    ): Float {
        val bearingToTarget = bearingDegrees(userLat, userLon, targetLat, targetLon)
        val bearingDiff = abs(normalizeBearing(bearingToTarget - userBearing))
        return straightDistance * kotlin.math.sin(Math.toRadians(bearingDiff.toDouble())).toFloat()
    }

    private fun estimateForwardOffsetMeters(
        userLat: Double,
        userLon: Double,
        userBearing: Float,
        targetLat: Double,
        targetLon: Double,
        straightDistance: Float
    ): Float {
        val bearingToTarget = bearingDegrees(userLat, userLon, targetLat, targetLon)
        val bearingDiff = abs(normalizeBearing(bearingToTarget - userBearing))
        return straightDistance * kotlin.math.cos(Math.toRadians(bearingDiff.toDouble())).toFloat()
    }

    private fun segmentLengthMeters(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double
    ): Double {
        return distanceMeters(startLat, startLon, endLat, endLon).toDouble()
    }

    private fun distanceMeters(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(startLat, startLon, endLat, endLon, results)
        return results[0]
    }

    private fun bearingDegrees(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double
    ): Float {
        val results = FloatArray(2)
        Location.distanceBetween(startLat, startLon, endLat, endLon, results)
        return results[1]
    }

    private fun normalizeBearing(bearing: Float): Float {
        var normalized = bearing % 360f
        if (normalized > 180f) normalized -= 360f
        if (normalized < -180f) normalized += 360f
        return normalized
    }
}
