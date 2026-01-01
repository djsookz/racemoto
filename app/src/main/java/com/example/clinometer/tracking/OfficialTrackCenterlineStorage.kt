package com.example.clinometer.tracking

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.osmdroid.util.GeoPoint

/**
 * Storage for centerline points of official tracks
 * These points are used for snapping GPS data to track centerline
 */
object OfficialTrackCenterlineStorage {
    
    private const val PREFS_NAME = "official_track_centerlines"
    private const val KEY_PREFIX = "centerline_"
    
    /**
     * Save centerline points for an official track
     */
    fun saveCenterlinePoints(context: Context, trackId: String, points: List<GeoPoint>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = gson.toJson(points.map { CenterlinePoint(it.latitude, it.longitude) })
        prefs.edit().putString("${KEY_PREFIX}${trackId}", json).apply()
        android.util.Log.d("OfficialTrackCenterlineStorage", "✅ Saved ${points.size} centerline points for $trackId")
    }
    
    /**
     * Load centerline points for an official track
     */
    fun loadCenterlinePoints(context: Context, trackId: String): List<GeoPoint> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString("${KEY_PREFIX}${trackId}", null)
        
        if (json == null || json.isEmpty()) {
            return emptyList()
        }
        
        return try {
            val gson = Gson()
            val type = object : TypeToken<List<CenterlinePoint>>() {}.type
            val points = gson.fromJson<List<CenterlinePoint>>(json, type)
            points.map { GeoPoint(it.latitude, it.longitude) }
        } catch (e: Exception) {
            android.util.Log.e("OfficialTrackCenterlineStorage", "Error loading centerline points: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Delete centerline points for an official track
     */
    fun deleteCenterlinePoints(context: Context, trackId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("${KEY_PREFIX}${trackId}").apply()
        android.util.Log.d("OfficialTrackCenterlineStorage", "✅ Deleted centerline points for $trackId")
    }
    
    /**
     * Check if centerline points exist for a track
     */
    fun hasCenterlinePoints(context: Context, trackId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains("${KEY_PREFIX}${trackId}")
    }
    
    /**
     * Get count of centerline points for a track
     */
    fun getCenterlinePointCount(context: Context, trackId: String): Int {
        return loadCenterlinePoints(context, trackId).size
    }
    
    /**
     * Data class for serialization
     */
    private data class CenterlinePoint(
        val latitude: Double,
        val longitude: Double
    )
}

