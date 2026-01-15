package com.example.clinometer.tracking

import com.example.clinometer.GeoPoint


/**
 * Data class representing a custom track created by the user
 */
data class CustomTrack(
    val id: String,
    val name: String,
    val type: TrackType,
    val points: List<TrackPoint>,
    val createdAt: Long = System.currentTimeMillis()
) {
    enum class TrackType {
        CIRCUIT,    // Closed loop track (start = finish)
        POINT_TO_POINT  // Start to finish track
    }
    
    data class TrackPoint(
        val geoPoint: GeoPoint,
        val pointType: PointType,
        val name: String? = null
    ) {
        enum class PointType {
            START_FINISH,   // For circuit tracks
            START,          // For point-to-point tracks
            FINISH,         // For point-to-point tracks
            SNAP_HELPER     // Helper points for snap-to-road
        }
    }
}
