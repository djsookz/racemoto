package com.revix.app

import android.content.Context
import android.util.Log
import com.revix.app.data.ProfileSessionSummaryStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream


object RouteStorage {
    private const val TAG = "RouteStorage"
    private const val RACES_FILE = "races.json"
    private const val POINTS_DIR = "route_points"

    /**
     * Persist session metadata only. Full GPS trails live in [saveRoutePoints].
     * Any embedded [Race.routePoints] are migrated to the side file, then stripped
     * so races.json stays small even after multi-hour free-ride sessions.
     */
    fun saveRaces(context: Context, races: List<Race>) {
        synchronized(this) {
            try {
                val lightweight = races.map { ensurePointsFileAndStrip(context, it) }
                writeRacesFile(context, lightweight)
                ProfileSessionSummaryStore.updateRouteSummaries(context, lightweight)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving races", e)
            }
        }
    }

    // Запазване на точките за конкретна сесия
    fun saveRoutePoints(context: Context, raceId: Long, points: List<RoutePoint>) {
        synchronized(this) {
            try {
                Log.d(TAG, "💾 Saving ${points.size} points for raceId=$raceId")

                val dir = File(context.filesDir, POINTS_DIR)
                if (!dir.exists()) {
                    dir.mkdirs()
                    Log.d(TAG, "📁 Created directory: ${dir.absolutePath}")
                }

                val file = File(dir, "points_$raceId.json")
                val gson = GsonBuilder().create()
                val json = gson.toJson(points)
                FileOutputStream(file).use {
                    it.write(json.toByteArray())
                }

                Log.d(TAG, "✅ Saved ${points.size} points to ${file.absolutePath} (${json.length} bytes)")

                if (points.isEmpty()) {
                    Log.e(TAG, "⚠️ WARNING: Saved EMPTY list for raceId=$raceId!")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error saving points for raceId=$raceId", e)
            }
        }
    }

    // Зареждане на метаданни
    fun loadRaces(context: Context): List<Race> {
        synchronized(this) {
            return try {
                val file = File(context.filesDir, RACES_FILE)
                if (!file.exists()) return emptyList()

                val json = file.readText()
                val type = object : TypeToken<List<Race>>() {}.type
                val races: List<Race> = Gson().fromJson(json, type) ?: emptyList()

                var needsRewrite = false
                val normalized = races.map { raw ->
                    val race = sanitizeRace(raw).also { if (it != raw) needsRewrite = true }
                    if ((race.routePoints as List<*>?).orEmpty().isNotEmpty()) {
                        needsRewrite = true
                        ensurePointsFileAndStrip(context, race)
                    } else {
                        race.copy(
                            routePoints = emptyList(),
                            photoPaths = (race.photoPaths as List<String?>?)?.filterNotNull().orEmpty()
                        )
                    }
                }
                if (needsRewrite) {
                    // One-time migration: drop embedded trails from races.json.
                    writeRacesFile(context, normalized)
                    ProfileSessionSummaryStore.updateRouteSummaries(context, normalized)
                    Log.d(TAG, "Migrated embedded routePoints out of races.json (${normalized.size} races)")
                }
                normalized
            } catch (e: Exception) {
                Log.e(TAG, "Error loading races", e)
                emptyList()
            }
        }
    }

    // Зареждане на точки за конкретна сесия
    fun loadRoutePoints(context: Context, raceId: Long): List<RoutePoint> {
        synchronized(this) {
            return try {
                val file = File(File(context.filesDir, POINTS_DIR), "points_$raceId.json")

                if (!file.exists()) {
                    Log.w(TAG, "⚠️ File not found for raceId=$raceId at ${file.absolutePath}")
                    return emptyList()
                }

                val json = file.readText()
                val type = object : TypeToken<List<RoutePoint>>() {}.type
                val points: List<RoutePoint> = sanitizeRoutePoints(
                    Gson().fromJson(json, type) ?: emptyList()
                )

                Log.d(TAG, "📂 Loaded ${points.size} points for raceId=$raceId from ${file.absolutePath} (${json.length} bytes)")

                if (points.isEmpty()) {
                    Log.w(TAG, "⚠️ Loaded EMPTY list for raceId=$raceId! File exists but contains no data!")
                }

                points
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading points for raceId=$raceId", e)
                emptyList()
            }
        }
    }

    private fun pointsFile(context: Context, raceId: Long): File =
        File(File(context.filesDir, POINTS_DIR), "points_$raceId.json")

    /**
     * If [race] still carries an embedded trail, persist it to points_<id>.json when missing,
     * then return a metadata-only copy.
     */
    private fun ensurePointsFileAndStrip(context: Context, race: Race): Race {
        val points = sanitizeRoutePoints(race.routePoints)
        if (points.isEmpty()) {
            return race.copy(
                routePoints = emptyList(),
                photoPaths = (race.photoPaths as List<String?>?)?.filterNotNull().orEmpty()
            )
        }
        val file = pointsFile(context, race.id)
        if (!file.exists() || file.length() < 3L) {
            saveRoutePoints(context, race.id, points)
        }
        return race.copy(
            routePoints = emptyList(),
            photoPaths = (race.photoPaths as List<String?>?)?.filterNotNull().orEmpty()
        )
    }

    private fun sanitizeRace(race: Race): Race {
        return race.copy(
            routePoints = sanitizeRoutePoints(race.routePoints),
            photoPaths = (race.photoPaths as List<String?>?)?.filterNotNull().orEmpty()
        )
    }

    private fun sanitizeRoutePoints(points: List<RoutePoint>?): List<RoutePoint> {
        return (points as List<RoutePoint?>?)
            ?.mapNotNull { point ->
                if (point == null) return@mapNotNull null
                val geo = point.geoPoint as GeoPoint? ?: return@mapNotNull null
                point.copy(geoPoint = geo)
            }
            .orEmpty()
    }

    private fun writeRacesFile(context: Context, races: List<Race>) {
        val gson = GsonBuilder().create()
        val json = gson.toJson(races)
        val file = File(context.filesDir, RACES_FILE)
        FileOutputStream(file).use {
            it.write(json.toByteArray())
            it.flush()
        }
    }
}
