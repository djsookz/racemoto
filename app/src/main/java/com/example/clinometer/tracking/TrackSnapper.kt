package com.example.clinometer.tracking

import android.content.Context
import org.osmdroid.util.GeoPoint
import kotlin.math.*

/**
 * Track Snapper - SIMPLE AND AGGRESSIVE
 * Always snap to closest point on centerline, preserve offset only if very small
 */
object TrackSnapper {
    
    data class SnappedPoint(
        val geoPoint: GeoPoint,
        val lateralOffset: Double, // meters: negative = left, positive = right, 0 = center
        val isSnapped: Boolean
    )
    
    private const val MAX_SNAPPING_DISTANCE = 200.0 // meters
    
    /**
     * Snap GPS points to track centerline
     */
    fun snapPoints(
        gpsPoints: List<GeoPoint>,
        trackId: String,
        context: Context?,
        centerlinePoints: List<GeoPoint>? = null
    ): List<SnappedPoint> {
        if (gpsPoints.isEmpty()) return emptyList()
        
        // Get track centerline
        val rawCenterline = if (centerlinePoints != null && centerlinePoints.isNotEmpty()) {
            centerlinePoints
        } else {
            getTrackCenterline(trackId, context)
        }
        
        if (rawCenterline.isEmpty()) {
            android.util.Log.w("TrackSnapper", "⚠️ No centerline for $trackId")
            return gpsPoints.map { SnappedPoint(it, 0.0, false) }
        }
        
        // Interpolate to create dense centerline
        val centerline = if (rawCenterline.size <= 5) {
            interpolateCenterline(rawCenterline, pointsPerSegment = 50) // More points for better snapping
        } else {
            rawCenterline
        }
        
        val trackWidth = getTrackWidth(trackId)
        val maxOffset = trackWidth / 2.0
        
        android.util.Log.d("TrackSnapper", "🔧 Snapping ${gpsPoints.size} points to ${centerline.size} centerline points")
        android.util.Log.d("TrackSnapper", "📏 Track width: ${trackWidth}m, max offset: ${maxOffset}m")
        
        val snappedPoints = mutableListOf<SnappedPoint>()
        var snappedCount = 0
        var centerlineSnapped = 0 // Points snapped directly to centerline
        var offsetPreserved = 0 // Points with preserved offset
        var notSnapped = 0 // Points that couldn't be snapped
        
        for (gpsPoint in gpsPoints) {
            val snapped = snapPoint(gpsPoint, centerline, maxOffset, snappedPoints.size)
            snappedPoints.add(snapped)
            if (snapped.isSnapped) {
                snappedCount++
                if (abs(snapped.lateralOffset) < 0.1) {
                    centerlineSnapped++
                } else {
                    offsetPreserved++
                }
            } else {
                notSnapped++
            }
        }
        
        android.util.Log.d("TrackSnapper", "✅ Snapped $snappedCount/${gpsPoints.size} points")
        android.util.Log.d("TrackSnapper", "   → $centerlineSnapped snapped to centerline, $offsetPreserved with offset preserved, $notSnapped not snapped")
        
        return snappedPoints
    }
    
    /**
     * AGGRESSIVE SNAPPING ALGORITHM for inaccurate GPS:
     * Always snap to centerline to correct GPS errors
     * Only preserve very small offsets (< 1m) that are realistic
     */
    private fun snapPoint(
        gpsPoint: GeoPoint,
        centerline: List<GeoPoint>,
        maxOffset: Double,
        pointIndex: Int = 0
    ): SnappedPoint {
        if (centerline.isEmpty()) {
            return SnappedPoint(gpsPoint, 0.0, false)
        }
        
        // Find closest segment and perpendicular point
        var bestSegmentIndex = -1
        var bestPerpPoint: GeoPoint? = null
        var minDistance = Double.MAX_VALUE
        var lateralOffset = 0.0
        
        // Search ALL segments to find the best one
        for (i in 0 until centerline.size - 1) {
            val segmentStart = centerline[i]
            val segmentEnd = centerline[i + 1]
            
            val perpResult = findPerpendicularOnSegment(gpsPoint, segmentStart, segmentEnd)
            if (perpResult != null) {
                val (perpPoint, distance, signedOffset) = perpResult
                if (distance < minDistance) {
                    minDistance = distance
                    bestSegmentIndex = i
                    bestPerpPoint = perpPoint
                    lateralOffset = signedOffset
                }
            }
        }
        
        // If too far from centerline, don't snap
        if (bestPerpPoint == null || minDistance > MAX_SNAPPING_DISTANCE) {
            android.util.Log.d("TrackSnapper", "⚠️ Point too far: ${minDistance.toInt()}m > ${MAX_SNAPPING_DISTANCE.toInt()}m")
            return SnappedPoint(gpsPoint, 0.0, false)
        }
        
        // MAXIMUM AGGRESSIVE SNAPPING for very inaccurate GPS:
        // Always snap directly to centerline to correct GPS errors
        // Only preserve tiny offsets (< 1m) that are definitely realistic
        
        val segmentStart = centerline[bestSegmentIndex]
        val segmentEnd = centerline[bestSegmentIndex + 1]
        
        // Clamp offset to track width
        val clampedOffset = lateralOffset.coerceIn(-maxOffset, maxOffset)
        
        // Decision: Only preserve offset if it's VERY small (< 1m)
        // Everything else is GPS error - snap to centerline
        val finalPoint: GeoPoint
        val finalOffset: Double
        
        if (abs(lateralOffset) < 1.0 && abs(lateralOffset) <= maxOffset) {
            // Very small offset (< 1m) - might be real, preserve it
            finalPoint = applyOffset(bestPerpPoint, segmentStart, segmentEnd, clampedOffset)
            finalOffset = clampedOffset
        } else {
            // Any offset >= 1m is likely GPS error - snap directly to centerline
            finalPoint = bestPerpPoint
            finalOffset = 0.0
        }
        
        // Log occasionally for debugging
        if (pointIndex % 100 == 0) {
            android.util.Log.d("TrackSnapper", "Point $pointIndex: GPS dist=${minDistance.toInt()}m, offset=${lateralOffset.toInt()}m, snapped to centerline=${abs(finalOffset) < 0.1}")
        }
        
        return SnappedPoint(finalPoint, finalOffset, true)
    }
    
    /**
     * Find perpendicular point on segment
     */
    private fun findPerpendicularOnSegment(
        point: GeoPoint,
        segmentStart: GeoPoint,
        segmentEnd: GeoPoint
    ): Triple<GeoPoint, Double, Double>? {
        // Use average latitude for accurate meter calculations
        val avgLat = (segmentStart.latitude + segmentEnd.latitude) / 2.0
        val metersPerDegreeLat = 111320.0
        val metersPerDegreeLon = 111320.0 * cos(Math.toRadians(avgLat))
        
        // Convert segment to meters
        val dx = (segmentEnd.longitude - segmentStart.longitude) * metersPerDegreeLon
        val dy = (segmentEnd.latitude - segmentStart.latitude) * metersPerDegreeLat
        
        val segmentLengthSquared = dx * dx + dy * dy
        if (segmentLengthSquared < 0.0001) {
            // Segment too short, use start point
            val distance = haversineDistance(point, segmentStart)
            return Triple(segmentStart, distance, 0.0)
        }
        
        // Vector from segmentStart to point
        val px = (point.longitude - segmentStart.longitude) * metersPerDegreeLon
        val py = (point.latitude - segmentStart.latitude) * metersPerDegreeLat
        
        // Project point onto segment (parameter t: 0.0 to 1.0)
        val t = ((px * dx + py * dy) / segmentLengthSquared).coerceIn(0.0, 1.0)
        
        // Calculate perpendicular point on segment
        val perpLat = segmentStart.latitude + (segmentEnd.latitude - segmentStart.latitude) * t
        val perpLon = segmentStart.longitude + (segmentEnd.longitude - segmentStart.longitude) * t
        val perpPoint = GeoPoint(perpLat, perpLon)
        
        // Distance from GPS point to perpendicular point
        val distance = haversineDistance(point, perpPoint)
        
        // Calculate signed lateral offset
        // Cross product: positive = left of segment, negative = right of segment
        // We want: negative = left, positive = right (from driver's perspective)
        val crossProduct = dx * py - dy * px
        val signedOffset = if (crossProduct >= 0) distance else -distance
        
        return Triple(perpPoint, distance, signedOffset)
    }
    
    /**
     * Apply lateral offset to a point on a segment
     */
    private fun applyOffset(
        pointOnSegment: GeoPoint,
        segmentStart: GeoPoint,
        segmentEnd: GeoPoint,
        offsetMeters: Double
    ): GeoPoint {
        if (abs(offsetMeters) < 0.01) {
            return pointOnSegment
        }
        
        val avgLat = (segmentStart.latitude + segmentEnd.latitude) / 2.0
        val metersPerDegreeLat = 111320.0
        val metersPerDegreeLon = 111320.0 * cos(Math.toRadians(avgLat))
        
        // Segment direction vector
        val dx = (segmentEnd.longitude - segmentStart.longitude) * metersPerDegreeLon
        val dy = (segmentEnd.latitude - segmentStart.latitude) * metersPerDegreeLat
        
        val segmentLength = sqrt(dx * dx + dy * dy)
        if (segmentLength < 0.0001) {
            return pointOnSegment
        }
        
        // Normalize direction
        val dirX = dx / segmentLength
        val dirY = dy / segmentLength
        
        // Perpendicular direction (rotate 90° clockwise: (x,y) -> (-y,x))
        val perpDirX = -dirY
        val perpDirY = dirX
        
        // Apply offset in perpendicular direction
        val offsetLat = offsetMeters * perpDirY / metersPerDegreeLat
        val offsetLon = offsetMeters * perpDirX / (111320.0 * cos(Math.toRadians(pointOnSegment.latitude)))
        
        return GeoPoint(pointOnSegment.latitude + offsetLat, pointOnSegment.longitude + offsetLon)
    }
    
    /**
     * Interpolate centerline to create denser points
     */
    private fun interpolateCenterline(points: List<GeoPoint>, pointsPerSegment: Int = 20): List<GeoPoint> {
        if (points.size < 2) return points
        
        val interpolated = mutableListOf<GeoPoint>()
        
        for (i in 0 until points.size - 1) {
            val start = points[i]
            val end = points[i + 1]
            
            if (i == 0) {
                interpolated.add(start)
            }
            
            for (j in 1 until pointsPerSegment) {
                val t = j.toDouble() / pointsPerSegment
                val lat = start.latitude + (end.latitude - start.latitude) * t
                val lon = start.longitude + (end.longitude - start.longitude) * t
                interpolated.add(GeoPoint(lat, lon))
            }
            
            if (i == points.size - 2) {
                interpolated.add(end)
            }
        }
        
        return interpolated
    }
    
    /**
     * Get track centerline points
     */
    private fun getTrackCenterline(trackId: String, context: Context?): List<GeoPoint> {
        return when {
            trackId == "serres_circuit" || trackId == "sofia_ring" -> {
                if (context != null) {
                    val userCenterline = OfficialTrackCenterlineStorage.loadCenterlinePoints(context, trackId)
                    if (userCenterline.isNotEmpty()) {
                        android.util.Log.d("TrackSnapper", "✅ Using ${userCenterline.size} user centerline points")
                        return userCenterline
                    }
                }
                val fallback = TrackGeometry.getTrackWaypoints(trackId, context)
                android.util.Log.d("TrackSnapper", "📌 Using ${fallback.size} fallback waypoints")
                fallback
            }
            trackId.startsWith("custom_") -> {
                if (context != null) {
                    val customTrack = CustomTrackStorage.loadCustomTrack(context, trackId)
                    customTrack?.points?.map { it.geoPoint } ?: emptyList()
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }
    
    /**
     * Get track width in meters
     */
    private fun getTrackWidth(trackId: String): Double {
        return when (trackId) {
            "serres_circuit" -> 12.0
            "sofia_ring" -> 14.0
            else -> {
                val trackData = TrackGeometry.getTrackData(trackId)
                trackData?.width ?: 10.0
            }
        }
    }
    
    /**
     * Calculate distance using Haversine formula
     */
    private fun haversineDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val earthRadius = 6371000.0 // meters
        
        val lat1 = Math.toRadians(point1.latitude)
        val lat2 = Math.toRadians(point2.latitude)
        val deltaLat = Math.toRadians(point2.latitude - point1.latitude)
        val deltaLon = Math.toRadians(point2.longitude - point1.longitude)
        
        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(deltaLon / 2) * sin(deltaLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
}
