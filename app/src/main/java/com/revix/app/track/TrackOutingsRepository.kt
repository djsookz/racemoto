package com.revix.app.track

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import com.revix.app.R
import com.revix.app.TrackManager
import com.revix.app.TrackSessionIdUtils
import com.revix.app.settings.UnitsManager
import com.revix.app.tracking.CustomTrackStorage
import com.revix.app.utils.LapTimeFormatter
import java.util.Locale

object TrackOutingsRepository {

    data class TrackAggregateStats(
        val totalSessions: Int,
        val totalLaps: Int,
        val totalDurationMs: Long,
        val bestLapMs: Long?,
        val bestLapSessionId: String?,
        val bestLapOutingNumber: Int?
    )

    data class TrackOutingListItem(
        val sessionIdFull: String,
        val outingNumber: Int,
        val trackId: String,
        val sortTimestamp: Long,
        val isTrackPb: Boolean,
        val recordedWithRaceBox: Boolean = false
    )

    private data class SessionIdMetadata(
        val trackId: String,
        val sessionDate: String,
        val sessionTime: String,
        val timestamp: Long?
    )

    private data class MutableTrackAggregateStats(
        var totalSessions: Int = 0,
        var totalLaps: Int = 0,
        var totalDurationMs: Long = 0L,
        var bestLapMs: Long? = null,
        var bestLapSessionId: String? = null,
        var bestLapOutingNumber: Int? = null,
        var bestLapSessionTimestamp: Long = Long.MIN_VALUE
    )

    fun loadSessionIdsForProfile(context: Context, profileId: Long): Set<String> {
        val sharedPrefs = context.getSharedPreferences("track_outings", Context.MODE_PRIVATE)
        return sharedPrefs.all.keys
            .filter { it.endsWith("_outing_count") }
            .map { it.removeSuffix("_outing_count") }
            .filter { it.startsWith("${profileId}_") }
            .toSet()
    }

    fun extractTrackIdFromSessionId(context: Context, sessionIdFull: String): String {
        return TrackSessionIdUtils.extractTrackIdFromSessionId(context, sessionIdFull)
    }

    fun resolveSessionTimestamp(sessionIdFull: String): Long {
        val metadata = parseSessionIdMetadata(sessionIdFull)
        return metadata.timestamp ?: run {
            if (metadata.sessionDate.isNotEmpty() && metadata.sessionTime.isNotEmpty()) {
                try {
                    val normalizedTime = metadata.sessionTime.replace(":", "")
                    val dateTime = "${metadata.sessionDate} $normalizedTime"
                    val formatter = java.text.SimpleDateFormat("dd.MM.yyyy HHmm", Locale.getDefault())
                    formatter.parse(dateTime)?.time ?: 0L
                } catch (_: Exception) {
                    0L
                }
            } else {
                0L
            }
        }
    }

    fun getTrackName(context: Context, trackId: String): String {
        TrackManager(context).getTrackById(trackId)?.name?.let { return it }

        return when {
            trackId == "custom_track" -> context.getString(R.string.track_name_custom)
            trackId.startsWith("custom_") -> {
                CustomTrackStorage.loadCustomTrack(context, trackId)?.name
                    ?: context.getString(R.string.track_name_unknown)
            }
            else -> context.getString(R.string.track_name_unknown)
        }
    }

    fun buildTrackAggregateStats(
        context: Context,
        sessionIds: Set<String>
    ): Map<String, TrackAggregateStats> {
        val sharedPrefs = context.getSharedPreferences("track_outings", Context.MODE_PRIVATE)
        val statsByTrack = mutableMapOf<String, MutableTrackAggregateStats>()

        for (sessionId in sessionIds) {
            val trackId = extractTrackIdFromSessionId(context, sessionId)
            val trackStats = statsByTrack.getOrPut(trackId) { MutableTrackAggregateStats() }
            val sessionTimestamp = resolveSessionTimestamp(sessionId)
            val outingCount = sharedPrefs.getInt("${sessionId}_outing_count", 0)
            trackStats.totalSessions += outingCount.coerceAtLeast(1)

            for (outingNumber in 1..outingCount) {
                val lapsRaw = sharedPrefs.getString("${sessionId}_outing_${outingNumber}_laps", "0") ?: "0"
                trackStats.totalLaps += parseLapsCount(lapsRaw)

                val durationRaw = sharedPrefs.getString("${sessionId}_outing_${outingNumber}_duration", "") ?: ""
                parseFlexibleTimeToMs(durationRaw)?.let { durationMs ->
                    trackStats.totalDurationMs += durationMs
                }

                val bestLapRaw = sharedPrefs.getString("${sessionId}_outing_${outingNumber}_best_lap", "") ?: ""
                val bestLapMs = parseFlexibleTimeToMs(bestLapRaw)
                if (bestLapMs != null) {
                    val currentBestMs = trackStats.bestLapMs
                    val isNewBestTime = currentBestMs == null || bestLapMs < currentBestMs
                    val isEqualBestButNewer = currentBestMs != null && bestLapMs == currentBestMs && (
                        sessionTimestamp > trackStats.bestLapSessionTimestamp ||
                            (
                                sessionTimestamp == trackStats.bestLapSessionTimestamp &&
                                    outingNumber > (trackStats.bestLapOutingNumber ?: Int.MIN_VALUE)
                                )
                        )

                    if (isNewBestTime || isEqualBestButNewer) {
                        trackStats.bestLapMs = bestLapMs
                        trackStats.bestLapSessionId = sessionId
                        trackStats.bestLapOutingNumber = outingNumber
                        trackStats.bestLapSessionTimestamp = sessionTimestamp
                    }
                }
            }
        }

        return statsByTrack.mapValues { (_, value) ->
            TrackAggregateStats(
                totalSessions = value.totalSessions,
                totalLaps = value.totalLaps,
                totalDurationMs = value.totalDurationMs,
                bestLapMs = value.bestLapMs,
                bestLapSessionId = value.bestLapSessionId,
                bestLapOutingNumber = value.bestLapOutingNumber
            )
        }
    }

    fun buildSessionBucketStats(
        context: Context,
        sessionIdFull: String
    ): TrackAggregateStats {
        return buildTrackAggregateStats(context, setOf(sessionIdFull)).values.firstOrNull()
            ?: TrackAggregateStats(
                totalSessions = 0,
                totalLaps = 0,
                totalDurationMs = 0L,
                bestLapMs = null,
                bestLapSessionId = null,
                bestLapOutingNumber = null
            )
    }

    fun loadOutingsForSessionBucket(
        context: Context,
        sessionIdFull: String
    ): List<TrackOutingListItem> {
        val sharedPrefs = context.getSharedPreferences("track_outings", Context.MODE_PRIVATE)
        val trackId = extractTrackIdFromSessionId(context, sessionIdFull)
        val bucketStats = buildSessionBucketStats(context, sessionIdFull)
        val items = mutableListOf<TrackOutingListItem>()
        val outingCount = sharedPrefs.getInt("${sessionIdFull}_outing_count", 0)
        val sessionTimestamp = resolveSessionTimestamp(sessionIdFull)

        for (outingNumber in 1..outingCount) {
            val isTrackPb = bucketStats.bestLapSessionId == sessionIdFull &&
                bucketStats.bestLapOutingNumber == outingNumber
            items += TrackOutingListItem(
                sessionIdFull = sessionIdFull,
                outingNumber = outingNumber,
                trackId = trackId,
                sortTimestamp = sessionTimestamp + outingNumber,
                isTrackPb = isTrackPb,
                recordedWithRaceBox = sharedPrefs.getBoolean(
                    "${sessionIdFull}_outing_${outingNumber}_recorded_with_racebox",
                    false
                )
            )
        }

        return items.sortedByDescending { it.sortTimestamp }
    }

    fun loadOutingsForTrack(
        context: Context,
        profileId: Long,
        trackId: String
    ): List<TrackOutingListItem> {
        val sharedPrefs = context.getSharedPreferences("track_outings", Context.MODE_PRIVATE)
        val sessionIds = loadSessionIdsForProfile(context, profileId)
            .filter { extractTrackIdFromSessionId(context, it) == trackId }
        val trackStats = buildTrackAggregateStats(context, sessionIds.toSet())[trackId]

        val items = mutableListOf<TrackOutingListItem>()
        for (sessionId in sessionIds) {
            val outingCount = sharedPrefs.getInt("${sessionId}_outing_count", 0)
            val sessionTimestamp = resolveSessionTimestamp(sessionId)
            for (outingNumber in 1..outingCount) {
                val isTrackPb = trackStats?.bestLapSessionId == sessionId &&
                    trackStats.bestLapOutingNumber == outingNumber
                items += TrackOutingListItem(
                    sessionIdFull = sessionId,
                    outingNumber = outingNumber,
                    trackId = trackId,
                    sortTimestamp = sessionTimestamp + outingNumber,
                    isTrackPb = isTrackPb,
                    recordedWithRaceBox = sharedPrefs.getBoolean(
                        "${sessionId}_outing_${outingNumber}_recorded_with_racebox",
                        false
                    )
                )
            }
        }

        return items.sortedByDescending { it.sortTimestamp }
    }

    fun findLatestSessionIdForTrack(
        context: Context,
        profileId: Long,
        trackId: String
    ): String? {
        return loadSessionIdsForProfile(context, profileId)
            .filter { extractTrackIdFromSessionId(context, it) == trackId }
            .maxByOrNull { resolveSessionTimestamp(it) }
    }

    fun formatTimeMs(totalMs: Long): String = LapTimeFormatter.formatMs(totalMs)

    fun formatSummaryDuration(context: Context, totalMs: Long): String {
        if (totalMs <= 0L) return "--"

        val hours = totalMs / 3_600_000L
        val minutes = (totalMs % 3_600_000L) / 60_000L

        return if (hours > 0L) {
            context.getString(R.string.track_summary_time_hours_minutes, hours.toInt(), minutes.toInt())
        } else {
            context.getString(R.string.track_summary_time_minutes, minutes.toInt())
        }
    }

    fun formatDurationDisplay(rawDuration: String): String = LapTimeFormatter.formatDurationDisplay(rawDuration)

    fun formatSpeedDisplayWhole(context: Context, rawSpeed: String): String {
        return UnitsManager.formatStoredSpeed(rawSpeed, context)
    }

    fun resolveWeatherIconStyle(context: Context, iconRes: Int, humidityPercent: Int?): Pair<Int, Int> {
        val baseIcon = when (iconRes) {
            R.drawable.ic_weather_sunny -> R.drawable.ic_weather_sunny
            R.drawable.ic_weather_clear_night -> R.drawable.ic_weather_clear_night
            R.drawable.ic_weather_partly_cloudy,
            R.drawable.ic_weather_partly_cloudy_night -> R.drawable.ic_weather_partly_cloudy
            R.drawable.ic_weather_cloudy -> R.drawable.ic_weather_cloudy
            R.drawable.ic_weather_rainy -> R.drawable.ic_weather_rainy
            R.drawable.ic_weather_snowy -> R.drawable.ic_weather_snowy
            else -> R.drawable.ic_weather_cloudy
        }

        val finalIcon = if (baseIcon == R.drawable.ic_weather_sunny && (humidityPercent ?: 0) >= 70) {
            R.drawable.ic_weather_cloudy
        } else {
            baseIcon
        }

        val tintRes = when (finalIcon) {
            R.drawable.ic_weather_sunny -> R.color.warning_color
            R.drawable.ic_weather_rainy -> R.color.accent_light
            R.drawable.ic_weather_snowy -> R.color.accent_light
            R.drawable.ic_weather_clear_night -> R.color.text_secondary_light
            R.drawable.ic_weather_partly_cloudy -> R.color.text_secondary_light
            R.drawable.ic_weather_cloudy -> R.color.text_secondary_light
            else -> R.color.text_secondary_light
        }

        return finalIcon to ContextCompat.getColor(context, tintRes)
    }

    fun deleteSessionBucket(context: Context, sessionIdFull: String) {
        val sharedPrefs = context.getSharedPreferences("track_outings", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        sharedPrefs.all.keys
            .filter { it.startsWith("${sessionIdFull}_") }
            .forEach { key -> editor.remove(key) }
        editor.apply()
    }

    fun deleteOutingFromSessionBucket(
        context: Context,
        sessionIdFull: String,
        outingNumber: Int
    ): Boolean {
        val sharedPrefs = context.getSharedPreferences("track_outings", Context.MODE_PRIVATE)
        val outingCount = sharedPrefs.getInt("${sessionIdFull}_outing_count", 0)
        if (outingNumber !in 1..outingCount) return false

        val editor = sharedPrefs.edit()
        removeOutingKeys(editor, sharedPrefs, sessionIdFull, outingNumber)

        for (number in (outingNumber + 1)..outingCount) {
            moveOutingKeys(editor, sharedPrefs, sessionIdFull, fromNumber = number, toNumber = number - 1)
            removeOutingKeys(editor, sharedPrefs, sessionIdFull, number)
        }

        if (outingCount <= 1) {
            sharedPrefs.all.keys
                .filter { it.startsWith("${sessionIdFull}_") }
                .forEach { key -> editor.remove(key) }
        } else {
            editor.putInt("${sessionIdFull}_outing_count", outingCount - 1)
        }
        editor.apply()
        return true
    }

    private fun outingKeyPrefix(sessionIdFull: String, outingNumber: Int): String =
        "${sessionIdFull}_outing_${outingNumber}_"

    private fun removeOutingKeys(
        editor: SharedPreferences.Editor,
        sharedPrefs: SharedPreferences,
        sessionIdFull: String,
        outingNumber: Int
    ) {
        val prefix = outingKeyPrefix(sessionIdFull, outingNumber)
        sharedPrefs.all.keys
            .filter { it.startsWith(prefix) }
            .forEach { key -> editor.remove(key) }
    }

    private fun moveOutingKeys(
        editor: SharedPreferences.Editor,
        sharedPrefs: SharedPreferences,
        sessionIdFull: String,
        fromNumber: Int,
        toNumber: Int
    ) {
        val fromPrefix = outingKeyPrefix(sessionIdFull, fromNumber)
        sharedPrefs.all.keys
            .filter { it.startsWith(fromPrefix) }
            .forEach { key ->
                val suffix = key.removePrefix(fromPrefix)
                val targetKey = "${outingKeyPrefix(sessionIdFull, toNumber)}$suffix"
                when (val value = sharedPrefs.all[key]) {
                    is String -> editor.putString(targetKey, value)
                    is Int -> editor.putInt(targetKey, value)
                    is Long -> editor.putLong(targetKey, value)
                    is Float -> editor.putFloat(targetKey, value)
                    is Boolean -> editor.putBoolean(targetKey, value)
                }
            }
    }

    private fun parseSessionIdMetadata(sessionIdFull: String): SessionIdMetadata {
        val raw = extractRawSessionId(sessionIdFull)
        val parts = raw.split("_").toMutableList()

        var timestamp: Long? = null
        var sessionTime = ""
        var sessionDate = ""

        if (parts.isNotEmpty() && parts.last().matches(Regex("\\d{10,13}"))) {
            timestamp = parts.removeAt(parts.lastIndex).toLongOrNull()
        }
        if (parts.isNotEmpty() && (parts.last().matches(Regex("\\d{4}")) || parts.last().matches(Regex("\\d{2}:\\d{2}")))) {
            sessionTime = parts.removeAt(parts.lastIndex)
        }
        if (parts.isNotEmpty() && parts.last().matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}"))) {
            sessionDate = parts.removeAt(parts.lastIndex)
        }

        val trackId = if (parts.isEmpty()) raw else parts.joinToString("_")
        return SessionIdMetadata(
            trackId = trackId,
            sessionDate = sessionDate,
            sessionTime = sessionTime,
            timestamp = timestamp
        )
    }

    private fun extractRawSessionId(sessionIdFull: String): String {
        val firstUnderscore = sessionIdFull.indexOf('_')
        return if (firstUnderscore > 0) sessionIdFull.substring(firstUnderscore + 1) else sessionIdFull
    }

    private fun parseLapsCount(rawValue: String): Int {
        val direct = rawValue.trim().toIntOrNull()
        if (direct != null) return direct
        val digitsOnly = rawValue.filter { it.isDigit() }
        return digitsOnly.toIntOrNull() ?: 0
    }

    private fun parseFlexibleTimeToMs(value: String): Long? {
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
}
