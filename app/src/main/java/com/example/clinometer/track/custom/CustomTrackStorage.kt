package com.example.clinometer.tracking

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object CustomTrackStorage {
    private const val PREFS_NAME = "custom_tracks"
    private const val KEY_TRACKS = "tracks"
    private const val KEY_TRACKS_V2 = "tracks_v2"
    private const val KEY_SCHEMA_VERSION = "schema_version"
    private const val CURRENT_SCHEMA_VERSION = 2
    private val gson = Gson()

    private fun ensureMigrated(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentVersion = prefs.getInt(KEY_SCHEMA_VERSION, 1)
        val v2Json = prefs.getString(KEY_TRACKS_V2, null)

        if (currentVersion >= CURRENT_SCHEMA_VERSION && v2Json != null) {
            return
        }

        val legacyTracks = loadLegacyTracks(context)
        val migrated = legacyTracks.map { CustomTrackMigration.toV2(it) }
        saveV2Tracks(context, migrated)

        prefs.edit().putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION).apply()
        Log.d("CustomTrackStorage", "Migrated ${migrated.size} custom tracks to schema v$CURRENT_SCHEMA_VERSION")
    }

    private fun loadLegacyTracks(context: Context): List<CustomTrack> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_TRACKS, null) ?: return emptyList()

        return try {
            val type = object : TypeToken<List<CustomTrack>>() {}.type
            gson.fromJson<List<CustomTrack>>(json, type)
        } catch (e: Exception) {
            Log.e("CustomTrackStorage", "Error loading legacy tracks: ${e.message}")
            emptyList()
        }
    }

    private fun loadV2Tracks(context: Context): MutableList<CustomTrackDefinitionV2> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_TRACKS_V2, null) ?: return mutableListOf()

        return try {
            val type = object : TypeToken<List<CustomTrackDefinitionV2>>() {}.type
            gson.fromJson<List<CustomTrackDefinitionV2>>(json, type).toMutableList()
        } catch (e: Exception) {
            Log.e("CustomTrackStorage", "Error loading v2 tracks: ${e.message}")
            mutableListOf()
        }
    }

    private fun saveV2Tracks(context: Context, tracks: List<CustomTrackDefinitionV2>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(tracks)
        prefs.edit().putString(KEY_TRACKS_V2, json).apply()
    }
    
    /**
     * Save a custom track
     */
    fun saveCustomTrack(context: Context, track: CustomTrack) {
        ensureMigrated(context)
        val allTracks = loadV2Tracks(context)
        val trackV2 = CustomTrackMigration.toV2(track)
        
        // Remove existing track with same ID
        allTracks.removeAll { it.id == track.id }
        
        // Add new track
        allTracks.add(trackV2)
        
        // Save to preferences
        saveV2Tracks(context, allTracks)
        
        Log.d("CustomTrackStorage", "Saved custom track: ${track.name} (${track.type})")
    }
    
    /**
     * Load all custom tracks (shared across all profiles)
     */
    fun loadCustomTracks(context: Context, profileId: String? = null): List<CustomTrack> {
        ensureMigrated(context)
        return loadV2Tracks(context).map { CustomTrackMigration.toLegacy(it) }
    }
    
    /**
     * Load a specific custom track by ID
     */
    fun loadCustomTrack(context: Context, trackId: String): CustomTrack? {
        return loadCustomTracks(context).find { it.id == trackId }
    }

    fun loadCustomTracksV2(context: Context): List<CustomTrackDefinitionV2> {
        ensureMigrated(context)
        return loadV2Tracks(context)
    }

    fun loadCustomTrackV2(context: Context, trackId: String): CustomTrackDefinitionV2? {
        ensureMigrated(context)
        return loadV2Tracks(context).firstOrNull { it.id == trackId }
    }

    fun saveCustomTrackV2(context: Context, track: CustomTrackDefinitionV2) {
        ensureMigrated(context)
        val allTracks = loadV2Tracks(context)
        allTracks.removeAll { it.id == track.id }
        allTracks.add(track)
        saveV2Tracks(context, allTracks)

        Log.d("CustomTrackStorage", "Saved custom track V2: ${track.name} (${track.mode})")
    }
    
    /**
     * Delete a custom track
     */
    fun deleteCustomTrack(context: Context, trackId: String) {
        ensureMigrated(context)
        val tracks = loadV2Tracks(context)
        tracks.removeAll { it.id == trackId }
        saveV2Tracks(context, tracks)
        
        Log.d("CustomTrackStorage", "Deleted custom track: $trackId")
    }
    
    /**
     * Generate unique track ID
     */
    fun generateTrackId(): String {
        return "custom_${System.currentTimeMillis()}"
    }
}
