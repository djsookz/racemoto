package com.example.clinometer.tracking

import android.util.Log
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object OsrmRouteHelper {

    private const val TAG = "OsrmRouteHelper"
    private const val BASE_URL = "https://router.project-osrm.org/route/v1/driving/"
    private const val MAX_WAYPOINTS = 10
    private const val MAX_DISTANCE_FOR_SNAP_METERS = 25.0
    private const val MAX_ATTEMPTS = 6

    /**
     * Attempts to snap the provided GPS points to an OSRM-generated route.
     * @return list with the same size as originalPoints or null if OSRM failed.
     */
    fun snapToRoute(
        originalPoints: List<GeoPoint>,
        maxDeviationMeters: Double = MAX_DISTANCE_FOR_SNAP_METERS
    ): List<GeoPoint>? {
        if (originalPoints.size < 2) return null

        val url = buildOsrmUrl(originalPoints)
        val osrmPolyline = fetchOsrmPolyline(url) ?: return null
        val polylinePoints = decodePolyline(osrmPolyline)

        if (polylinePoints.isEmpty()) {
            Log.w(TAG, "OSRM returned empty polyline")
            return null
        }

        val snapped = mutableListOf<GeoPoint>()
        var snappedCount = 0

        for (i in originalPoints.indices) {
            val point = originalPoints[i]
            val (closest, distance) = findClosestPointOnPolyline(point, polylinePoints)
            if (distance <= maxDeviationMeters) {
                snapped.add(closest)
                snappedCount++
            } else {
                // Keep original point to avoid unrealistic jumps
                snapped.add(point)
            }
        }

        Log.d(TAG, "Snapped $snappedCount of ${originalPoints.size} points via OSRM")
        return snapped
    }

    /**
     * Generates OSRM-snapped processed points using adaptive re-routing.
     * Ensures the polyline stays on the road even if the initial OSRM route diverges.
     */
    fun generateNavigatedProcessedPoints(
        originalPoints: List<GeoPoint>,
        speedData: List<Float>,
        maxDeviationMeters: Double = MAX_DISTANCE_FOR_SNAP_METERS
    ): List<SmartMapMatcher.ProcessedPoint>? {
        if (originalPoints.size < 2) return null

        val snappedPoints = mutableListOf<GeoPoint>()
        var index = 0
        var attempts = 0

        while (index < originalPoints.size && attempts < MAX_ATTEMPTS) {
            val segment = originalPoints.subList(index, originalPoints.size)
            val result = snapSegmentWithDeviation(segment, maxDeviationMeters) ?: break

            if (result.snappedPoints.isEmpty()) {
                Log.w(TAG, "OSRM segment returned 0 snapped points at index=$index, aborting navigation fallback.")
                break
            }

            snappedPoints.addAll(result.snappedPoints)
            index += result.snappedPoints.size
            attempts++

            if (result.completed) {
                break
            }
        }

        if (snappedPoints.size != originalPoints.size) {
            Log.w(
                TAG,
                "Navigation fallback incomplete (snapped=${snappedPoints.size}/${originalPoints.size}), falling back to original chunk."
            )
            return null
        }

        Log.d(TAG, "Navigation fallback succeeded for ${originalPoints.size} points (attempts=$attempts)")
        return toProcessedPoints(snappedPoints, originalPoints, speedData)
    }

    /**
     * Converts snapped GeoPoints to ProcessedPoints preserving speed data.
     */
    fun toProcessedPoints(
        snappedPoints: List<GeoPoint>,
        originalPoints: List<GeoPoint>,
        speedData: List<Float>
    ): List<SmartMapMatcher.ProcessedPoint> {
        val processed = mutableListOf<SmartMapMatcher.ProcessedPoint>()
        for (i in snappedPoints.indices) {
            val snapped = snappedPoints[i]
            val speed = speedData.getOrNull(i) ?: 0f
            val next = snappedPoints.getOrNull(min(i + 1, snappedPoints.lastIndex))
            val bearing = calculateBearing(snapped, next ?: snapped)

            processed.add(
                SmartMapMatcher.ProcessedPoint(
                    geoPoint = snapped,
                    confidence = 0.65f,
                    speed = speed,
                    bearing = bearing,
                    acceleration = 0f,
                    isSnapped = true,
                    originalPoint = originalPoints.getOrNull(i) ?: snapped
                )
            )
        }
        return processed
    }

    private fun buildOsrmUrl(points: List<GeoPoint>): String {
        val sampled = sampleWaypoints(points)
        val coordinates = sampled.joinToString(";") { "${it.longitude},${it.latitude}" }
        return "$BASE_URL$coordinates?overview=full&geometries=polyline&steps=false&annotations=distance"
    }

    private fun sampleWaypoints(points: List<GeoPoint>): List<GeoPoint> {
        if (points.size <= MAX_WAYPOINTS + 2) return points

        val sampled = mutableListOf<GeoPoint>()
        sampled.add(points.first())

        val step = (points.size - 2) / (MAX_WAYPOINTS + 1).coerceAtLeast(1)
        var index = step
        while (index < points.size - 1 && sampled.size < MAX_WAYPOINTS + 1) {
            sampled.add(points[index])
            index += step
        }

        sampled.add(points.last())
        return sampled
    }

    private fun fetchOsrmPolyline(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 20000
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "OSRM request failed: HTTP $responseCode")
                connection.disconnect()
                return null
            }

            val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            connection.disconnect()

            val json = JSONObject(response)
            val routes = json.optJSONArray("routes") ?: return null
            if (routes.length() == 0) return null
            val firstRoute = routes.getJSONObject(0)
            val geometry = firstRoute.optString("geometry", "")
            if (geometry.isEmpty()) {
                Log.w(TAG, "OSRM response missing geometry")
                null
            } else {
                geometry
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching OSRM route: ${e.message}", e)
            null
        }
    }

    private data class SnapSegmentResult(
        val snappedPoints: List<GeoPoint>,
        val completed: Boolean
    )

    private fun snapSegmentWithDeviation(
        segmentPoints: List<GeoPoint>,
        maxDeviationMeters: Double
    ): SnapSegmentResult? {
        if (segmentPoints.size < 2) return null

        val url = buildOsrmUrl(segmentPoints)
        val polyline = fetchOsrmPolyline(url) ?: return null
        val polylinePoints = decodePolyline(polyline)

        if (polylinePoints.size < 2) {
            Log.w(TAG, "OSRM segment polyline too short (${polylinePoints.size})")
            return null
        }

        val snapped = mutableListOf<GeoPoint>()
        for (point in segmentPoints) {
            val (closest, distance) = findClosestPointOnPolyline(point, polylinePoints)
            if (distance <= maxDeviationMeters) {
                snapped.add(closest)
            } else {
                Log.d(TAG, "Deviation detected (${distance}m) – restarting OSRM from new waypoint")
                return SnapSegmentResult(snapped, completed = false)
            }
        }

        return SnapSegmentResult(snapped, completed = true)
    }

    private fun decodePolyline(polyline: String): List<GeoPoint> {
        val coordinates = mutableListOf<GeoPoint>()
        var index = 0
        val len = polyline.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = polyline[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLat = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            lat += dLat

            shift = 0
            result = 0
            do {
                b = polyline[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLng = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            lng += dLng

            val latitude = lat / 1E5
            val longitude = lng / 1E5
            coordinates.add(GeoPoint(latitude, longitude))
        }
        return coordinates
    }

    private fun findClosestPointOnPolyline(
        target: GeoPoint,
        polyline: List<GeoPoint>
    ): Pair<GeoPoint, Double> {
        if (polyline.size == 1) {
            val distance = distanceMeters(target, polyline[0])
            return polyline[0] to distance
        }

        var closestPoint = polyline.first()
        var minDistance = Double.MAX_VALUE

        for (i in 0 until polyline.size - 1) {
            val segmentStart = polyline[i]
            val segmentEnd = polyline[i + 1]
            val projection = projectToSegment(target, segmentStart, segmentEnd)
            val distance = distanceMeters(target, projection)
            if (distance < minDistance) {
                minDistance = distance
                closestPoint = projection
            }
        }

        return closestPoint to minDistance
    }

    private fun projectToSegment(point: GeoPoint, start: GeoPoint, end: GeoPoint): GeoPoint {
        val refLat = Math.toRadians(point.latitude)
        val r = 6371000.0

        val sx = Math.toRadians(start.longitude) * cos(refLat) * r
        val sy = Math.toRadians(start.latitude) * r
        val ex = Math.toRadians(end.longitude) * cos(refLat) * r
        val ey = Math.toRadians(end.latitude) * r
        val px = Math.toRadians(point.longitude) * cos(refLat) * r
        val py = Math.toRadians(point.latitude) * r

        val dx = ex - sx
        val dy = ey - sy
        val lengthSquared = dx * dx + dy * dy

        if (lengthSquared == 0.0) return start

        val t = ((px - sx) * dx + (py - sy) * dy) / lengthSquared
        val clampedT = max(0.0, min(1.0, t))

        val closestX = sx + clampedT * dx
        val closestY = sy + clampedT * dy

        val lat = Math.toDegrees(closestY / r)
        val lon = Math.toDegrees(closestX / (r * cos(refLat)))

        return GeoPoint(lat, lon)
    }

    private fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val earthRadius = 6371000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val deltaLat = Math.toRadians(b.latitude - a.latitude)
        val deltaLon = Math.toRadians(b.longitude - a.longitude)

        val sinLat = kotlin.math.sin(deltaLat / 2).pow(2.0)
        val sinLon = kotlin.math.sin(deltaLon / 2).pow(2.0)
        val a = sinLat + cos(lat1) * cos(lat2) * sinLon
        val c = 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    private fun calculateBearing(from: GeoPoint, to: GeoPoint): Float {
        if (abs(from.latitude - to.latitude) < 1e-6 && abs(from.longitude - to.longitude) < 1e-6) {
            return 0f
        }
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)

        val y = kotlin.math.sin(deltaLon) * kotlin.math.cos(lat2)
        val x = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) -
                kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(deltaLon)

        val bearing = Math.toDegrees(kotlin.math.atan2(y, x))
        return ((bearing + 360) % 360).toFloat()
    }
}

