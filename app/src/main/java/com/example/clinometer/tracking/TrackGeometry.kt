package com.example.clinometer.tracking

import android.content.Context
import org.osmdroid.util.GeoPoint
import kotlin.math.*

/**
 * Track geometry data for map matching
 * Contains real GPS coordinates for racing tracks
 */
object TrackGeometry {
    
    data class TrackSegment(
        val startPoint: GeoPoint,
        val endPoint: GeoPoint,
        val waypoints: List<GeoPoint>
    )
    
    data class TrackData(
        val name: String,
        val segments: List<TrackSegment>,
        val width: Double = 12.0 // Track width in meters
    )
    
    // Serres Circuit track data (REAL coordinates from TrackSessionActivity)
    // Start/Finish: 41.073128, 23.517839
    // Sector 2: 41.070481, 23.519244
    // Sector 3: 41.072907, 23.516091
    // Sector 4: 41.071511, 23.513143
    // Create centerline by interpolating between sectors
    private val SERRES_CIRCUIT_WAYPOINTS = listOf(
        // Start/Finish
        GeoPoint(41.073128, 23.517839),
        // Interpolate to Sector 2
        GeoPoint(41.072804, 23.518041),
        GeoPoint(41.072480, 23.518243),
        GeoPoint(41.072156, 23.518445),
        GeoPoint(41.071832, 23.518647),
        GeoPoint(41.071508, 23.518849),
        // Sector 2
        GeoPoint(41.070481, 23.519244),
        // Interpolate to Sector 3
        GeoPoint(41.070691, 23.518667),
        GeoPoint(41.070901, 23.518090),
        GeoPoint(41.071111, 23.517513),
        GeoPoint(41.071321, 23.516936),
        GeoPoint(41.071531, 23.516359),
        GeoPoint(41.071741, 23.515782),
        GeoPoint(41.071951, 23.515205),
        GeoPoint(41.072161, 23.514628),
        GeoPoint(41.072371, 23.514051),
        GeoPoint(41.072581, 23.513474),
        GeoPoint(41.072791, 23.512897),
        // Sector 3
        GeoPoint(41.072907, 23.516091),
        // Interpolate to Sector 4
        GeoPoint(41.072709, 23.515597),
        GeoPoint(41.072511, 23.515103),
        GeoPoint(41.072313, 23.514609),
        GeoPoint(41.072115, 23.514115),
        GeoPoint(41.071917, 23.513621),
        GeoPoint(41.071719, 23.513127),
        // Sector 4
        GeoPoint(41.071511, 23.513143),
        // Interpolate back to Start/Finish
        GeoPoint(41.071714, 23.513491),
        GeoPoint(41.071917, 23.513839),
        GeoPoint(41.072120, 23.514187),
        GeoPoint(41.072323, 23.514535),
        GeoPoint(41.072526, 23.514883),
        GeoPoint(41.072729, 23.515231),
        GeoPoint(41.072932, 23.515579),
        GeoPoint(41.073135, 23.515927),
        GeoPoint(41.073338, 23.516275),
        GeoPoint(41.073541, 23.516623),
        GeoPoint(41.073744, 23.516971),
        GeoPoint(41.073947, 23.517319),
        // Back to Start/Finish
        GeoPoint(41.073128, 23.517839)
    )
    
    // Sofia Ring track data (approximate real coordinates)
    private val SOFIA_RING_WAYPOINTS = listOf(
        // Main straight
        GeoPoint(42.6978, 23.3215),
        GeoPoint(42.6976, 23.3217),
        GeoPoint(42.6974, 23.3219),
        GeoPoint(42.6972, 23.3221),
        GeoPoint(42.6970, 23.3223),
        
        // Turn 1
        GeoPoint(42.6968, 23.3225),
        GeoPoint(42.6966, 23.3227),
        GeoPoint(42.6964, 23.3229),
        GeoPoint(42.6962, 23.3231),
        
        // Back straight
        GeoPoint(42.6960, 23.3233),
        GeoPoint(42.6958, 23.3235),
        GeoPoint(42.6956, 23.3237),
        GeoPoint(42.6954, 23.3239),
        GeoPoint(42.6952, 23.3241),
        
        // Turn 2
        GeoPoint(42.6950, 23.3243),
        GeoPoint(42.6948, 23.3245),
        GeoPoint(42.6946, 23.3247),
        GeoPoint(42.6944, 23.3249),
        
        // Turn 3
        GeoPoint(42.6942, 23.3251),
        GeoPoint(42.6940, 23.3253),
        GeoPoint(42.6938, 23.3255),
        GeoPoint(42.6936, 23.3257),
        
        // Back to start
        GeoPoint(42.6934, 23.3259),
        GeoPoint(42.6932, 23.3257),
        GeoPoint(42.6934, 23.3255),
        GeoPoint(42.6936, 23.3253),
        GeoPoint(42.6938, 23.3251),
        GeoPoint(42.6940, 23.3249),
        GeoPoint(42.6942, 23.3247),
        GeoPoint(42.6944, 23.3245),
        GeoPoint(42.6946, 23.3243),
        GeoPoint(42.6948, 23.3241),
        GeoPoint(42.6950, 23.3239),
        GeoPoint(42.6952, 23.3237),
        GeoPoint(42.6954, 23.3235),
        GeoPoint(42.6956, 23.3233),
        GeoPoint(42.6958, 23.3231),
        GeoPoint(42.6960, 23.3229),
        GeoPoint(42.6962, 23.3227),
        GeoPoint(42.6964, 23.3225),
        GeoPoint(42.6966, 23.3223),
        GeoPoint(42.6968, 23.3221),
        GeoPoint(42.6970, 23.3219),
        GeoPoint(42.6972, 23.3217),
        GeoPoint(42.6974, 23.3215),
        GeoPoint(42.6976, 23.3213),
        GeoPoint(42.6978, 23.3211),
        
        // Complete the circuit
        GeoPoint(42.6980, 23.3209),
        GeoPoint(42.6982, 23.3211),
        GeoPoint(42.6984, 23.3213),
        GeoPoint(42.6982, 23.3215),
        GeoPoint(42.6980, 23.3217),
        GeoPoint(42.6978, 23.3215)
    )
    
    private val TRACK_DATA = mapOf(
        "serres_circuit" to TrackData(
            name = "Serres Circuit",
            segments = createSegmentsFromWaypoints(SERRES_CIRCUIT_WAYPOINTS),
            width = 12.0
        ),
        "sofia_ring" to TrackData(
            name = "Sofia Ring", 
            segments = createSegmentsFromWaypoints(SOFIA_RING_WAYPOINTS),
            width = 14.0
        ),
        "custom_track" to TrackData(
            name = "Custom Track",
            segments = emptyList(),
            width = 10.0
        )
    )
    
    /**
     * Get track data for a specific track ID
     */
    fun getTrackData(trackId: String): TrackData? {
        return TRACK_DATA[trackId]
    }
    
    /**
     * Get all waypoints for a track (including custom tracks)
     */
    fun getTrackWaypoints(trackId: String, context: Context? = null): List<GeoPoint> {
        return when (trackId) {
            "serres_circuit" -> SERRES_CIRCUIT_WAYPOINTS
            "sofia_ring" -> SOFIA_RING_WAYPOINTS
            else -> {
                // Try to load custom track
                if (context != null && trackId.startsWith("custom_")) {
                    loadCustomTrack(trackId, context)
                } else {
                    emptyList()
                }
            }
        }
    }
    
    /**
     * Load custom track from SharedPreferences
     */
    private fun loadCustomTrack(trackId: String, context: Context): List<GeoPoint> {
        val prefs = context.getSharedPreferences("custom_tracks", Context.MODE_PRIVATE)
        val pointsString = prefs.getString("track_${trackId.substring(7)}", null) // Remove "custom_" prefix
        
        if (pointsString == null) return emptyList()
        
        return pointsString.split("|").mapNotNull { pointStr: String ->
            val parts = pointStr.split(",")
            if (parts.size == 2) {
                try {
                    GeoPoint(parts[0].toDouble(), parts[1].toDouble())
                } catch (e: NumberFormatException) {
                    null
                }
            } else null
        }
    }
    
    /**
     * Find the closest point on track to a given GPS point
     */
    fun findClosestPointOnTrack(gpsPoint: GeoPoint, trackId: String, context: Context? = null): GeoPoint? {
        val waypoints = getTrackWaypoints(trackId, context)
        if (waypoints.isEmpty()) return null
        
        var closestPoint = waypoints[0]
        var minDistance = Double.MAX_VALUE
        
        for (waypoint in waypoints) {
            val distance = gpsPoint.distanceToAsDouble(waypoint)
            if (distance < minDistance) {
                minDistance = distance
                closestPoint = waypoint
            }
        }
        
        return if (minDistance < 100.0) closestPoint else null // Only snap if within 100m
    }
    
    /**
     * Calculate distance from a point to the track
     */
    fun calculateDistanceToTrack(point: GeoPoint, trackId: String, context: Context? = null): Double {
        val closestPoint = findClosestPointOnTrack(point, trackId, context)
        return if (closestPoint != null) {
            point.distanceToAsDouble(closestPoint)
        } else {
            Double.MAX_VALUE
        }
    }
    
    /**
     * Check if a point is near the track (within snapping distance)
     */
    fun isNearTrack(point: GeoPoint, trackId: String, context: Context? = null, maxDistance: Double = 50.0): Boolean {
        return calculateDistanceToTrack(point, trackId, context) <= maxDistance
    }
    
    private fun createSegmentsFromWaypoints(waypoints: List<GeoPoint>): List<TrackSegment> {
        val segments = mutableListOf<TrackSegment>()
        
        for (i in 0 until waypoints.size - 1) {
            segments.add(
                TrackSegment(
                    startPoint = waypoints[i],
                    endPoint = waypoints[i + 1],
                    waypoints = listOf(waypoints[i], waypoints[i + 1])
                )
            )
        }
        
        return segments
    }
}
