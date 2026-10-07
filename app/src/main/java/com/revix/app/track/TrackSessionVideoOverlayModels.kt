package com.revix.app.track

import android.content.Context
import android.content.SharedPreferences
import com.revix.app.GeoPoint
import com.revix.app.HudVehicleIdentity
import com.revix.app.LapData
import com.revix.app.Profile
import com.revix.app.R
import com.revix.app.TrackManager
import com.revix.app.TrackMiniMapShapeResolver
import com.revix.app.TrackSessionIdUtils
import com.revix.app.TrackSessionVideoOverlayExporter
import com.revix.app.data.ProfileStorage
import com.revix.app.settings.UnitsManager
import com.revix.app.tracking.CustomTrackStorage
import com.revix.app.utils.LapTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

object TrackSessionVideoOverlayModels {
    fun build(
        context: Context,
        sessionId: String,
        outingNumber: Int,
        videoStartSessionElapsedMs: Long
    ): TrackSessionVideoOverlayExporter.OverlayModel? {
        val prefs = context.getSharedPreferences("track_outings", Context.MODE_PRIVATE)
        val totalLaps = prefs.getString("${sessionId}_outing_${outingNumber}_laps", null)
            ?.toIntOrNull()
            ?: 0
        val isPointToPoint = prefs.getString("${sessionId}_outing_${outingNumber}_mode", "circuit") ==
            "point_to_point"
        val isMotorcycle = resolveIsMotorcycle(context, sessionId)
        return build(
            context = context,
            prefs = prefs,
            sessionId = sessionId,
            outingNumber = outingNumber,
            totalLaps = totalLaps,
            isMotorcycle = isMotorcycle,
            isPointToPoint = isPointToPoint,
            videoStartSessionElapsedMs = videoStartSessionElapsedMs
        )
    }

    fun build(
        context: Context,
        prefs: SharedPreferences,
        sessionId: String,
        outingNumber: Int,
        totalLaps: Int,
        isMotorcycle: Boolean,
        isPointToPoint: Boolean,
        videoStartSessionElapsedMs: Long
    ): TrackSessionVideoOverlayExporter.OverlayModel? {
        val laps = mutableListOf<LapData>()
        forEachSessionLap(context, prefs, sessionId, outingNumber) { lapData ->
            laps += lapData
        }

        val extraLapMarker = prefs.getString(
            "${sessionId}_outing_${outingNumber}_video_export_lap_data",
            null
        )
        if (!extraLapMarker.isNullOrBlank()) {
            val extraLap = when {
                extraLapMarker.startsWith("file:") -> {
                    val lapIndex = extraLapMarker.removePrefix("file:").toIntOrNull()
                    if (lapIndex != null) {
                        TrackLapDataStore.loadLap(context, prefs, sessionId, outingNumber, lapIndex)
                    } else {
                        null
                    }
                }
                extraLapMarker == "file" -> null
                else -> runCatching { TrackLapDataStore.fromJson(extraLapMarker) }.getOrNull()
            }
            extraLap
                ?.takeIf { lap ->
                    val lastLap = laps.lastOrNull()
                    lastLap == null ||
                        lastLap.lapNumber != lap.lapNumber ||
                        lastLap.startTime != lap.startTime ||
                        lastLap.endTime != lap.endTime
                }
                ?.let { laps += it }
        }

        val orderedLaps = laps
            .filter { it.startTime > 0L }
            .sortedBy { it.startTime }
        if (orderedLaps.isEmpty()) return null

        val sessionStartWallTimeMs = orderedLaps.minOfOrNull { it.startTime } ?: return null
        val completedLapCount = totalLaps.takeIf { it > 0 } ?: orderedLaps.size
        val lapSegments = orderedLaps.mapIndexed { index, lap ->
            TrackSessionVideoOverlayExporter.LapSegment(
                lapNumber = if (lap.lapNumber > 0) lap.lapNumber else index + 1,
                startMs = lap.startTime - sessionStartWallTimeMs,
                durationMs = resolveStoredLapDurationMs(prefs, sessionId, outingNumber, index + 1, lap),
                isCompleted = index < completedLapCount
            )
        }

        val routeSamples = orderedLaps.flatMap { lap ->
            val lapOffsetMs = lap.startTime - sessionStartWallTimeMs
            lap.routePoints.map { point ->
                TrackSessionVideoOverlayExporter.RouteSample(
                    timeMs = lapOffsetMs + point.timestamp,
                    geoPoint = point.geoPoint,
                    speedKmh = point.speed
                )
            }
        }.sortedBy { it.timeMs }

        val gSamples = orderedLaps.flatMap { lap ->
            val sampleCount = minOf(lap.timestamps.size, lap.longitudinalGData.size, lap.lateralGData.size)
            val lapOffsetMs = lap.startTime - sessionStartWallTimeMs
            (0 until sampleCount).map { sampleIndex ->
                TrackSessionVideoOverlayExporter.GSample(
                    timeMs = lapOffsetMs + resolveLapTelemetryRelativeMs(lap, sampleIndex, sampleCount),
                    longitudinalG = lap.longitudinalGData[sampleIndex],
                    lateralG = lap.lateralGData[sampleIndex],
                    maxBraking = lap.maxBrakingData.getOrNull(sampleIndex)?.takeIf { it.isFinite() },
                    maxAccel = lap.maxAccelData.getOrNull(sampleIndex)?.takeIf { it.isFinite() },
                    maxLeft = lap.maxCorneringLeftData.getOrNull(sampleIndex)?.takeIf { it.isFinite() },
                    maxRight = lap.maxCorneringRightData.getOrNull(sampleIndex)?.takeIf { it.isFinite() },
                    maxResultG = lap.maxResultGData.getOrNull(sampleIndex)?.takeIf { it.isFinite() }
                )
            }
        }.sortedBy { it.timeMs }

        val leanSamples = orderedLaps.flatMap { lap ->
            val sampleCount = minOf(
                lap.timestamps.size,
                max(lap.displayLeanAngleData.size, lap.leanAngleData.size)
            )
            val lapOffsetMs = lap.startTime - sessionStartWallTimeMs
            (0 until sampleCount).mapNotNull { sampleIndex ->
                val angle = lap.displayLeanAngleData.getOrNull(sampleIndex)
                    ?: lap.leanAngleData.getOrNull(sampleIndex)?.let(::normalizeLegacyOverlayLeanAngle)
                    ?: return@mapNotNull null
                if (!angle.isFinite()) {
                    null
                } else {
                    TrackSessionVideoOverlayExporter.LeanSample(
                        timeMs = lapOffsetMs + resolveLapTelemetryRelativeMs(lap, sampleIndex, sampleCount),
                        angleDeg = angle
                    )
                }
            }
        }.sortedBy { it.timeMs }

        val trackId = TrackSessionIdUtils.extractTrackIdFromSessionId(context, sessionId)
        val miniMapPoints = TrackMiniMapShapeResolver(context).resolveMiniMapPoints(
            trackId = trackId,
            orderedLaps = orderedLaps,
            routeFallback = routeSamples.map { it.geoPoint },
            isCircuit = !isPointToPoint
        ).ifEmpty {
            routeSamples.map { it.geoPoint }.distinctBy { point ->
                String.format(Locale.US, "%.6f:%.6f", point.latitude, point.longitude)
            }
        }

        val identity = HudVehicleIdentity.from(resolveSessionProfile(context, sessionId), context)

        return TrackSessionVideoOverlayExporter.OverlayModel(
            isMotorcycle = isMotorcycle,
            videoStartSessionElapsedMs = videoStartSessionElapsedMs,
            lapSegments = lapSegments,
            routeSamples = routeSamples,
            gSamples = gSamples,
            leanSamples = leanSamples,
            miniMapPoints = miniMapPoints,
            speedUnitLabel = UnitsManager.getSpeedUnitSymbol(context).uppercase(Locale.getDefault()),
            speedFactor = UnitsManager.convertSpeed(1f, UnitsManager.getSpeedUnit(context)),
            trackName = resolveTrackName(context, sessionId),
            vehicleName = identity.title,
            vehicleSpecs = identity.specs,
            interpolatePhoneSpeed = !prefs.getBoolean(
                "${sessionId}_outing_${outingNumber}_recorded_with_racebox",
                false
            ),
            startMarker = resolveStartMarker(context, sessionId),
            followMiniMap = TrackSessionVideoSettings.map3dEnabled(context)
        )
    }

    private fun resolveStartMarker(context: Context, sessionId: String): GeoPoint? {
        val trackId = TrackSessionIdUtils.extractTrackIdFromSessionId(context, sessionId)
        if (trackId.isBlank()) return null
        val custom = CustomTrackStorage.loadCustomTrackV2(context, trackId)
        val gate = custom?.startGate ?: custom?.finishGate
        if (gate != null) {
            return GeoPoint(
                latitude = (gate.start.latitude + gate.end.latitude) / 2.0,
                longitude = (gate.start.longitude + gate.end.longitude) / 2.0
            )
        }
        return TrackManager(context).loadTrackData(trackId)?.trackPoints?.firstOrNull()?.geoPoint
    }

    private fun resolveIsMotorcycle(context: Context, sessionId: String): Boolean {
        return resolveSessionProfile(context, sessionId)?.vehicleType == Profile.VehicleType.MOTORCYCLE
    }

    private fun resolveTrackName(context: Context, sessionId: String): String {
        val trackId = TrackSessionIdUtils.extractTrackIdFromSessionId(context, sessionId)
        TrackManager(context).getTrackById(trackId)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        if (trackId.startsWith("custom_")) {
            CustomTrackStorage.loadCustomTrack(context, trackId)?.name?.takeIf { it.isNotBlank() }?.let { return it }
            CustomTrackStorage.loadCustomTrackV2(context, trackId)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return context.getString(R.string.track_name_unknown)
    }

    private fun resolveSessionProfile(context: Context, sessionId: String): Profile? {
        val profiles = ProfileStorage.loadProfiles(context)
        val sessionProfileId = sessionId.substringBefore("_", "").toLongOrNull()
        return profiles.find { it.id == sessionProfileId }
            ?: profiles.find { it.id == ProfileStorage.getSelectedProfileId(context) }
            ?: profiles.firstOrNull()
    }

    private fun forEachSessionLap(
        context: Context,
        prefs: SharedPreferences,
        sessionId: String,
        outingNumber: Int,
        onLapData: (LapData) -> Unit
    ) {
        val currentProfileId = ProfileStorage.getSelectedProfileId(context)
        val baseTrackId = TrackSessionIdUtils.extractTrackIdFromSessionId(context, sessionId)
        val sessionCandidates = linkedSetOf<String>()
        if (sessionId.isNotBlank()) sessionCandidates += sessionId
        if (baseTrackId.isNotBlank()) {
            sessionCandidates += "${currentProfileId}_$baseTrackId"
            sessionCandidates += baseTrackId
        }

        val resolvedSessionId = sessionCandidates.firstOrNull { candidate ->
            prefs.getInt("${candidate}_outing_${outingNumber}_lap_data_count", 0) > 0
        } ?: return
        val lapDataCount = prefs.getInt("${resolvedSessionId}_outing_${outingNumber}_lap_data_count", 0)
        for (lapIndex in 1..lapDataCount) {
            val lapData = TrackLapDataStore.loadLap(
                context = context,
                sharedPrefs = prefs,
                sessionId = resolvedSessionId,
                outingNumber = outingNumber,
                lapIndex = lapIndex
            ) ?: continue
            onLapData(lapData)
        }
    }

    private fun resolveStoredLapDurationMs(
        prefs: SharedPreferences,
        sessionId: String,
        outingNumber: Int,
        lapNumber: Int,
        lapData: LapData
    ): Long {
        val completedLapTime = prefs.getString("${sessionId}_outing_${outingNumber}_lap_$lapNumber", null)
        val completedDuration = completedLapTime?.let(::parseFlexibleTimeToMs)
        if (completedDuration != null) {
            return completedDuration.coerceAtLeast(0L)
        }
        if (lapData.endTime > lapData.startTime) {
            return (lapData.endTime - lapData.startTime).coerceAtLeast(0L)
        }
        val routeDuration = lapData.routePoints.maxOfOrNull { it.timestamp }
        if (routeDuration != null) return routeDuration.coerceAtLeast(0L)
        val sensorDuration = lapData.timestamps.maxOrNull()?.let { it - lapData.startTime }
        return sensorDuration?.coerceAtLeast(0L) ?: 0L
    }

    private fun parseFlexibleTimeToMs(value: String): Long? = LapTimeFormatter.parseToMs(value)

    private fun normalizeLegacyOverlayLeanAngle(angleDeg: Float): Float {
        return if (abs(angleDeg) < 1.8f) 0f else angleDeg
    }
}
