package com.example.clinometer.data

import android.content.Context
import android.location.Location
import com.example.clinometer.DragAttempt
import com.example.clinometer.DragSession
import com.example.clinometer.LapData
import com.example.clinometer.Race
import com.example.clinometer.RoutePoint
import com.example.clinometer.RouteStorage
import com.google.gson.Gson

data class ProfileSessionSummary(
    val routeSessionCount: Int = 0,
    val routeDistanceKm: Double = 0.0,
    val routeTimeMs: Long = 0L,
    val trackSessionCount: Int = 0,
    val trackDistanceKm: Double = 0.0,
    val trackTimeMs: Long = 0L,
    val dragSessionCount: Int = 0,
    val dragDistanceKm: Double = 0.0,
    val dragTimeMs: Long = 0L,
    val updatedAtMs: Long = 0L
) {
    val totalSessions: Int
        get() = routeSessionCount + trackSessionCount + dragSessionCount

    val totalDistanceKm: Double
        get() = routeDistanceKm + trackDistanceKm + dragDistanceKm

    val totalTimeMs: Long
        get() = routeTimeMs + trackTimeMs + dragTimeMs
}

object ProfileSessionSummaryStore {
    private const val PREFS_NAME = "garage_profile_session_summary"
    private const val TRACK_OUTINGS_PREFS = "track_outings"
    private const val KEY_CACHE_VERSION = "cache_version"
    private const val KEY_LAST_FULL_REBUILD_AT = "last_full_rebuild_at"
    private const val CACHE_VERSION = 1

    fun isInitialized(context: Context): Boolean {
        return prefs(context).getInt(KEY_CACHE_VERSION, 0) >= CACHE_VERSION
    }

    fun ensureInitialized(context: Context) {
        if (!isInitialized(context)) {
            rebuildAllSummaries(context)
        }
    }

    fun loadSummary(context: Context, profileId: Long): ProfileSessionSummary {
        val prefs = prefs(context)
        return ProfileSessionSummary(
            routeSessionCount = prefs.getInt(key(profileId, "route_session_count"), 0),
            routeDistanceKm = prefs.getString(key(profileId, "route_distance_km"), null)?.toDoubleOrNull() ?: 0.0,
            routeTimeMs = prefs.getLong(key(profileId, "route_time_ms"), 0L),
            trackSessionCount = prefs.getInt(key(profileId, "track_session_count"), 0),
            trackDistanceKm = prefs.getString(key(profileId, "track_distance_km"), null)?.toDoubleOrNull() ?: 0.0,
            trackTimeMs = prefs.getLong(key(profileId, "track_time_ms"), 0L),
            dragSessionCount = prefs.getInt(key(profileId, "drag_session_count"), 0),
            dragDistanceKm = prefs.getString(key(profileId, "drag_distance_km"), null)?.toDoubleOrNull() ?: 0.0,
            dragTimeMs = prefs.getLong(key(profileId, "drag_time_ms"), 0L),
            updatedAtMs = prefs.getLong(key(profileId, "updated_at_ms"), 0L)
        )
    }

    fun clearProfile(context: Context, profileId: Long) {
        prefs(context).edit()
            .remove(key(profileId, "route_session_count"))
            .remove(key(profileId, "route_distance_km"))
            .remove(key(profileId, "route_time_ms"))
            .remove(key(profileId, "track_session_count"))
            .remove(key(profileId, "track_distance_km"))
            .remove(key(profileId, "track_time_ms"))
            .remove(key(profileId, "drag_session_count"))
            .remove(key(profileId, "drag_distance_km"))
            .remove(key(profileId, "drag_time_ms"))
            .remove(key(profileId, "updated_at_ms"))
            .apply()
    }

    fun updateRouteSummaries(context: Context, races: List<Race>) {
        val profileIds = linkedSetOf<Long>().apply {
            addAll(ProfileStorage.loadProfiles(context).map { it.id })
            addAll(races.map { it.profileId })
        }
        val routeStatsByProfile = buildRouteStatsByProfile(
            context = context,
            races = races,
            includeRoutePointFallback = false
        )

        val editor = prefs(context).edit()
        profileIds.forEach { profileId ->
            writeRouteStats(editor, profileId, routeStatsByProfile[profileId] ?: RouteStats())
            touchUpdatedAt(editor, profileId)
        }
        editor.putInt(KEY_CACHE_VERSION, CACHE_VERSION)
        editor.apply()
    }

    fun updateDragSummaries(context: Context, sessions: List<DragSession>) {
        val profileIds = linkedSetOf<Long>().apply {
            addAll(ProfileStorage.loadProfiles(context).map { it.id })
            addAll(sessions.map { it.profileId })
        }
        val dragStatsByProfile = sessions
            .groupBy { it.profileId }
            .mapValues { (_, profileSessions) ->
                DragStats(
                    sessionCount = profileSessions.size,
                    distanceKm = profileSessions.sumOf { session ->
                        session.attempts.sumOf { attempt -> calculateDragAttemptDistanceKm(attempt) }
                    },
                    timeMs = profileSessions.sumOf { session ->
                        session.attempts.sumOf { attempt -> calculateDragAttemptDurationMs(attempt) }
                    }
                )
            }

        val editor = prefs(context).edit()
        profileIds.forEach { profileId ->
            writeDragStats(editor, profileId, dragStatsByProfile[profileId] ?: DragStats())
            touchUpdatedAt(editor, profileId)
        }
        editor.putInt(KEY_CACHE_VERSION, CACHE_VERSION)
        editor.apply()
    }

    fun refreshTrackSummary(context: Context, profileId: Long) {
        val trackStats = buildTrackStatsForProfile(context, profileId)
        prefs(context).edit()
            .also { editor ->
                writeTrackStats(editor, profileId, trackStats)
                touchUpdatedAt(editor, profileId)
                editor.putInt(KEY_CACHE_VERSION, CACHE_VERSION)
            }
            .apply()
    }

    fun rebuildAllSummaries(context: Context) {
        val races = RouteStorage.loadRaces(context)
        val dragSessions = com.example.clinometer.DragStorage.loadDragSessions(context)
        val trackProfileIds = collectTrackProfileIds(context)
        val profileIds = linkedSetOf<Long>().apply {
            addAll(ProfileStorage.loadProfiles(context).map { it.id })
            addAll(races.map { it.profileId })
            addAll(dragSessions.map { it.profileId })
            addAll(trackProfileIds)
        }

        val routeStatsByProfile = buildRouteStatsByProfile(
            context = context,
            races = races,
            includeRoutePointFallback = true
        )
        val dragStatsByProfile = dragSessions
            .groupBy { it.profileId }
            .mapValues { (_, profileSessions) ->
                DragStats(
                    sessionCount = profileSessions.size,
                    distanceKm = profileSessions.sumOf { session ->
                        session.attempts.sumOf { attempt -> calculateDragAttemptDistanceKm(attempt) }
                    },
                    timeMs = profileSessions.sumOf { session ->
                        session.attempts.sumOf { attempt -> calculateDragAttemptDurationMs(attempt) }
                    }
                )
            }

        val editor = prefs(context).edit()
        editor.clear()
        profileIds.forEach { profileId ->
            writeRouteStats(editor, profileId, routeStatsByProfile[profileId] ?: RouteStats())
            writeTrackStats(editor, profileId, buildTrackStatsForProfile(context, profileId))
            writeDragStats(editor, profileId, dragStatsByProfile[profileId] ?: DragStats())
            touchUpdatedAt(editor, profileId)
        }
        editor.putInt(KEY_CACHE_VERSION, CACHE_VERSION)
        editor.putLong(KEY_LAST_FULL_REBUILD_AT, System.currentTimeMillis())
        editor.apply()
    }

    private fun buildRouteStatsByProfile(
        context: Context,
        races: List<Race>,
        includeRoutePointFallback: Boolean
    ): Map<Long, RouteStats> {
        return races.groupBy { it.profileId }.mapValues { (_, profileRaces) ->
            RouteStats(
                sessionCount = profileRaces.size,
                distanceKm = profileRaces.sumOf { race ->
                    calculateRaceDistanceKm(
                        context = context,
                        race = race,
                        includeRoutePointFallback = includeRoutePointFallback
                    )
                },
                timeMs = profileRaces.sumOf { race ->
                    calculateRaceDurationMs(
                        context = context,
                        race = race,
                        includeRoutePointFallback = includeRoutePointFallback
                    )
                }
            )
        }
    }

    private fun buildTrackStatsForProfile(context: Context, profileId: Long): TrackStats {
        val prefs = context.getSharedPreferences(TRACK_OUTINGS_PREFS, Context.MODE_PRIVATE)
        val sessionCountKeys = prefs.all.keys.filter {
            it.endsWith("_outing_count") && it.startsWith("${profileId}_")
        }

        var totalSessions = 0
        var totalDistanceKm = 0.0
        var totalTimeMs = 0L

        sessionCountKeys.forEach { key ->
            val sessionId = key.removeSuffix("_outing_count")
            val outingCount = prefs.getInt(key, 0)
            for (outingNumber in 1..outingCount) {
                totalSessions += 1
                totalDistanceKm += calculateTrackOutingDistanceKm(prefs, sessionId, outingNumber)
                val storedDuration = prefs.getString("${sessionId}_outing_${outingNumber}_duration", "").orEmpty()
                totalTimeMs += parseTrackDurationMs(storedDuration)
                    ?: calculateTrackOutingDurationMs(prefs, sessionId, outingNumber)
            }
        }

        return TrackStats(
            sessionCount = totalSessions,
            distanceKm = totalDistanceKm,
            timeMs = totalTimeMs
        )
    }

    private fun collectTrackProfileIds(context: Context): Set<Long> {
        return context.getSharedPreferences(TRACK_OUTINGS_PREFS, Context.MODE_PRIVATE)
            .all
            .keys
            .filter { it.endsWith("_outing_count") }
            .mapNotNull { key ->
                key.substringBefore('_').toLongOrNull()
            }
            .toSet()
    }

    private fun calculateRaceDistanceKm(
        context: Context,
        race: Race,
        includeRoutePointFallback: Boolean
    ): Double {
        if (race.distance > 0.0) {
            return race.distance
        }
        if (race.routePoints.size > 1) {
            return calculateRoutePointsDistanceKm(race.routePoints)
        }
        if (!includeRoutePointFallback) {
            return 0.0
        }
        return calculateRoutePointsDistanceKm(RouteStorage.loadRoutePoints(context, race.id))
    }

    private fun calculateRaceDurationMs(
        context: Context,
        race: Race,
        includeRoutePointFallback: Boolean
    ): Long {
        if (race.duration > 0L) {
            return race.duration
        }

        val points = when {
            race.routePoints.isNotEmpty() -> race.routePoints
            includeRoutePointFallback -> RouteStorage.loadRoutePoints(context, race.id)
            else -> emptyList()
        }
        if (points.isEmpty()) {
            return 0L
        }

        val firstPoint = points.first()
        val lastPoint = points.last()
        if (firstPoint.absoluteTime > 0L && lastPoint.absoluteTime > firstPoint.absoluteTime) {
            return lastPoint.absoluteTime - firstPoint.absoluteTime
        }
        return (lastPoint.timestamp - firstPoint.timestamp).coerceAtLeast(0L)
    }

    private fun calculateTrackOutingDistanceKm(
        prefs: android.content.SharedPreferences,
        sessionId: String,
        outingNumber: Int
    ): Double {
        var totalDistanceKm = 0.0
        forEachTrackLapData(prefs, sessionId, outingNumber) { lapData ->
            if (lapData.routePoints.size > 1) {
                totalDistanceKm += calculateRoutePointsDistanceKm(lapData.routePoints)
            }
        }
        return totalDistanceKm
    }

    private fun calculateTrackOutingDurationMs(
        prefs: android.content.SharedPreferences,
        sessionId: String,
        outingNumber: Int
    ): Long {
        var totalDurationMs = 0L
        forEachTrackLapData(prefs, sessionId, outingNumber) { lapData ->
            totalDurationMs += calculateLapDataDurationMs(lapData)
        }
        return totalDurationMs
    }

    private inline fun forEachTrackLapData(
        prefs: android.content.SharedPreferences,
        sessionId: String,
        outingNumber: Int,
        action: (LapData) -> Unit
    ) {
        val gson = Gson()
        val lapDataCount = prefs.getInt("${sessionId}_outing_${outingNumber}_lap_data_count", 0)
        for (lapIndex in 1..lapDataCount) {
            val lapJson = prefs.getString("${sessionId}_outing_${outingNumber}_lap_data_${lapIndex}", null)
                ?: continue
            val lapData = try {
                gson.fromJson(lapJson, LapData::class.java)
            } catch (_: Exception) {
                null
            } ?: continue
            action(lapData)
        }
    }

    private fun calculateRoutePointsDistanceKm(points: List<RoutePoint>): Double {
        if (points.size < 2) return 0.0

        var meters = 0.0
        for (index in 1 until points.size) {
            val previous = points[index - 1].geoPoint
            val current = points[index].geoPoint
            val results = FloatArray(1)
            Location.distanceBetween(
                previous.latitude,
                previous.longitude,
                current.latitude,
                current.longitude,
                results
            )
            meters += results[0].toDouble()
        }
        return meters / 1000.0
    }

    private fun calculateLapDataDurationMs(lapData: LapData): Long {
        val directDuration = lapData.endTime - lapData.startTime
        if (directDuration > 0L) {
            return directDuration
        }

        val points = lapData.routePoints
        if (points.size < 2) {
            return 0L
        }

        val firstPoint = points.first()
        val lastPoint = points.last()
        if (firstPoint.absoluteTime > 0L && lastPoint.absoluteTime > firstPoint.absoluteTime) {
            return lastPoint.absoluteTime - firstPoint.absoluteTime
        }
        return (lastPoint.timestamp - firstPoint.timestamp).coerceAtLeast(0L)
    }

    private fun parseTrackDurationMs(value: String): Long? {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.contains("--")) return null

        val match = Regex("^(\\d+):(\\d{1,2})\\.(\\d{1,3})$").find(trimmed) ?: return null
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        val fraction = match.groupValues[3]
        val millis = when (fraction.length) {
            1 -> "${fraction}00"
            2 -> "${fraction}0"
            else -> fraction.take(3)
        }.toLongOrNull() ?: return null

        return (minutes * 60_000L) + (seconds * 1_000L) + millis
    }

    private fun calculateDragAttemptDistanceKm(attempt: DragAttempt): Double {
        val sampleLimit = minOf(attempt.speedSamples.size, attempt.speedTimeStamps.size)
        if (sampleLimit < 2) {
            return if (attempt.time0to402 > 0L) 0.402 else 0.0
        }

        var totalMeters = 0.0
        for (index in 1 until sampleLimit) {
            val t0 = attempt.speedTimeStamps[index - 1]
            val t1 = attempt.speedTimeStamps[index]
            val deltaNs = t1 - t0
            if (deltaNs <= 0L) continue

            val deltaSec = deltaNs / 1_000_000_000.0
            val v0 = attempt.speedSamples[index - 1].coerceAtLeast(0f) / 3.6
            val v1 = attempt.speedSamples[index].coerceAtLeast(0f) / 3.6
            totalMeters += ((v0 + v1) * 0.5) * deltaSec
        }

        if (totalMeters > 0.0) {
            return totalMeters / 1000.0
        }
        return if (attempt.time0to402 > 0L) 0.402 else 0.0
    }

    private fun calculateDragAttemptDurationMs(attempt: DragAttempt): Long {
        if (attempt.duration > 0L) {
            return attempt.duration / 1_000_000L
        }

        val sampleLimit = minOf(attempt.speedSamples.size, attempt.speedTimeStamps.size)
        if (sampleLimit >= 2) {
            val firstTime = attempt.speedTimeStamps.first()
            val lastTime = attempt.speedTimeStamps[sampleLimit - 1]
            if (lastTime > firstTime) {
                return (lastTime - firstTime) / 1_000_000L
            }
        }
        return 0L
    }

    private fun writeRouteStats(
        editor: android.content.SharedPreferences.Editor,
        profileId: Long,
        stats: RouteStats
    ) {
        editor.putInt(key(profileId, "route_session_count"), stats.sessionCount)
        editor.putString(key(profileId, "route_distance_km"), stats.distanceKm.toString())
        editor.putLong(key(profileId, "route_time_ms"), stats.timeMs)
    }

    private fun writeTrackStats(
        editor: android.content.SharedPreferences.Editor,
        profileId: Long,
        stats: TrackStats
    ) {
        editor.putInt(key(profileId, "track_session_count"), stats.sessionCount)
        editor.putString(key(profileId, "track_distance_km"), stats.distanceKm.toString())
        editor.putLong(key(profileId, "track_time_ms"), stats.timeMs)
    }

    private fun writeDragStats(
        editor: android.content.SharedPreferences.Editor,
        profileId: Long,
        stats: DragStats
    ) {
        editor.putInt(key(profileId, "drag_session_count"), stats.sessionCount)
        editor.putString(key(profileId, "drag_distance_km"), stats.distanceKm.toString())
        editor.putLong(key(profileId, "drag_time_ms"), stats.timeMs)
    }

    private fun touchUpdatedAt(editor: android.content.SharedPreferences.Editor, profileId: Long) {
        editor.putLong(key(profileId, "updated_at_ms"), System.currentTimeMillis())
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(profileId: Long, suffix: String): String = "profile_${profileId}_$suffix"

    private data class RouteStats(
        val sessionCount: Int = 0,
        val distanceKm: Double = 0.0,
        val timeMs: Long = 0L
    )

    private data class TrackStats(
        val sessionCount: Int = 0,
        val distanceKm: Double = 0.0,
        val timeMs: Long = 0L
    )

    private data class DragStats(
        val sessionCount: Int = 0,
        val distanceKm: Double = 0.0,
        val timeMs: Long = 0L
    )
}