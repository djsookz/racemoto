package com.example.clinometer.tracking

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object CustomTrackStorage {
    private const val PREFS_NAME = "custom_tracks"
    private const val KEY_TRACKS = "tracks"
    private val gson = Gson()
    
    /**
     * Save a custom track
     */
    fun saveCustomTrack(context: Context, track: CustomTrack) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Load all tracks (not filtered by profile)
        val json = prefs.getString(KEY_TRACKS, null)
        val allTracks = if (json != null) {
            try {
                val type = object : TypeToken<List<CustomTrack>>() {}.type
                gson.fromJson<List<CustomTrack>>(json, type).toMutableList()
            } catch (e: Exception) {
                Log.e("CustomTrackStorage", "Error loading tracks: ${e.message}")
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
        
        // Remove existing track with same ID
        allTracks.removeAll { it.id == track.id }
        
        // Add new track
        allTracks.add(track)
        
        // Save to preferences
        val newJson = gson.toJson(allTracks)
        prefs.edit().putString(KEY_TRACKS, newJson).apply()
        
        Log.d("CustomTrackStorage", "Saved custom track: ${track.name} (${track.type})")
    }
    
    /**
     * Load all custom tracks (shared across all profiles)
     */
    fun loadCustomTracks(context: Context, profileId: String? = null): List<CustomTrack> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_TRACKS, null) ?: return emptyList()
        
        try {
            val type = object : TypeToken<List<CustomTrack>>() {}.type
            val allTracks: List<CustomTrack> = gson.fromJson(json, type)
            
            // Custom tracks are shared across all profiles - no filtering by profileId
            return allTracks
        } catch (e: Exception) {
            Log.e("CustomTrackStorage", "Error loading custom tracks: ${e.message}")
            return emptyList()
        }
    }
    
    /**
     * Load a specific custom track by ID
     */
    fun loadCustomTrack(context: Context, trackId: String): CustomTrack? {
        return loadCustomTracks(context).find { it.id == trackId }
    }
    
    /**
     * Delete a custom track
     */
    fun deleteCustomTrack(context: Context, trackId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val tracks = loadCustomTracks(context).toMutableList()
        
        tracks.removeAll { it.id == trackId }
        
        val json = gson.toJson(tracks)
        prefs.edit().putString(KEY_TRACKS, json).apply()
        
        Log.d("CustomTrackStorage", "Deleted custom track: $trackId")
    }
    
    /**
     * Generate unique track ID
     */
    fun generateTrackId(): String {
        return "custom_${System.currentTimeMillis()}"
    }
}
