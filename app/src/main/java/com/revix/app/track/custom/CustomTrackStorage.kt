package com.revix.app.tracking

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.revix.app.GeoPoint

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

        // V2 already has data but schema flag was never bumped — do not wipe it with an empty legacy migrate.
        if (!v2Json.isNullOrBlank() && v2Json != "null" && v2Json != "[]") {
            prefs.edit().putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION).commit()
            Log.d("CustomTrackStorage", "Bumped schema to v$CURRENT_SCHEMA_VERSION without wiping existing v2 tracks")
            return
        }

        val legacyTracks = loadLegacyTracks(context)
        val migrated = legacyTracks.map { CustomTrackMigration.toV2(it) }
        saveV2Tracks(context, migrated)

        prefs.edit().putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION).commit()
        Log.d("CustomTrackStorage", "Migrated ${migrated.size} custom tracks to schema v$CURRENT_SCHEMA_VERSION")
    }

    private fun loadLegacyTracks(context: Context): List<CustomTrack> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_TRACKS, null) ?: return emptyList()

        return try {
            val type = object : TypeToken<List<CustomTrack>>() {}.type
            val loaded = gson.fromJson<List<CustomTrack>>(json, type).orEmpty()
            loaded.mapNotNull { sanitizeLegacyTrack(it) }
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
            val loaded = gson.fromJson<List<CustomTrackDefinitionV2>>(json, type).orEmpty()
            val sanitized = loaded.mapNotNull { sanitizeV2Track(it) }.toMutableList()
            if (sanitized.size != loaded.size || sanitized != loaded) {
                saveV2Tracks(context, sanitized)
            }
            sanitized
        } catch (e: Exception) {
            Log.e("CustomTrackStorage", "Error loading v2 tracks: ${e.message}")
            mutableListOf()
        }
    }

    private fun sanitizeLegacyTrack(track: CustomTrack?): CustomTrack? {
        if (track == null) return null
        val id = (track.id as String?)?.takeIf { it.isNotBlank() } ?: return null
        val name = (track.name as String?) ?: ""
        val type = (track.type as CustomTrack.TrackType?) ?: CustomTrack.TrackType.CIRCUIT
        val points = (track.points as List<CustomTrack.TrackPoint?>?)
            ?.mapNotNull { point ->
                val geo = point?.geoPoint ?: return@mapNotNull null
                val pointType = (point.pointType as CustomTrack.TrackPoint.PointType?)
                    ?: CustomTrack.TrackPoint.PointType.SNAP_HELPER
                CustomTrack.TrackPoint(geoPoint = geo, pointType = pointType, name = point.name)
            }
            .orEmpty()
        return CustomTrack(
            id = id,
            name = name,
            type = type,
            points = points,
            createdAt = track.createdAt
        )
    }

    private fun sanitizeV2Track(track: CustomTrackDefinitionV2?): CustomTrackDefinitionV2? {
        if (track == null) return null
        val id = (track.id as String?)?.takeIf { it.isNotBlank() } ?: return null
        val name = (track.name as String?) ?: ""
        val mode = (track.mode as CustomTrackMode?) ?: CustomTrackMode.CIRCUIT
        val sectorGates = (track.sectorGates as List<GateLine?>?)
            ?.mapNotNull { sanitizeGate(it) }
            .orEmpty()
        val referencePath = (track.referencePath as List<GeoPoint?>?)?.filterNotNull().orEmpty()
        return track.copy(
            id = id,
            name = name,
            mode = mode,
            startGate = sanitizeGate(track.startGate),
            finishGate = sanitizeGate(track.finishGate),
            sectorGates = sectorGates,
            referencePath = referencePath
        )
    }

    private fun sanitizeGate(gate: GateLine?): GateLine? {
        if (gate == null) return null
        val start = gate.start as GeoPoint? ?: return null
        val end = gate.end as GeoPoint? ?: return null
        return GateLine(start = start, end = end)
    }

    private fun saveV2Tracks(context: Context, tracks: List<CustomTrackDefinitionV2>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(tracks)
        // commit() so the list is durable before the builder navigates away.
        prefs.edit()
            .putString(KEY_TRACKS_V2, json)
            .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            .commit()
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
