package com.revix.app.track

import android.content.Context
import com.revix.app.GeoPoint
import com.revix.app.TrackManager
import com.revix.app.track.catalog.TrackMode
import com.revix.app.tracking.CustomTrackMode
import com.revix.app.tracking.CustomTrackStorage

object TrackReferencePathLoader {
    /**
     * Phone-built circuits store only a handful of control points (long chords).
     * Projecting lap GPS onto that geometry causes mid-lap Compare jumps.
     * Require a dense-enough centerline; otherwise Compare falls back to GPS odometer
     * distance (same path as official tracks without a GPX).
     */
    private const val MIN_REFERENCE_POINTS_FOR_COMPARE = 25
    private const val MAX_AVERAGE_SEGMENT_METERS = 60f

    fun load(
        context: Context,
        trackId: String,
        isPointToPoint: Boolean = false
    ): TrackReferenceRoute? {
        val normalizedTrackId = trackId.trim()
        if (normalizedTrackId.isBlank()) return null

        val isCircuit = !isPointToPoint
        resolveOfficialTemplate(context, normalizedTrackId, isCircuit)?.let { return it }
        resolveCustomTemplate(context, normalizedTrackId, isCircuit)?.let { return it }
        return null
    }

    private fun resolveOfficialTemplate(
        context: Context,
        trackId: String,
        isCircuit: Boolean
    ): TrackReferenceRoute? {
        val trackData = TrackManager(context).loadTrackData(trackId) ?: return null
        val points = trackData.trackPoints.map { it.geoPoint }
        if (points.size < 2) return null
        return TrackReferenceRoute.fromPoints(
            points = points,
            closeLoop = isCircuit,
            startFinishAnchor = points.first()
        )?.takeIf { isSuitableForDistanceCompare(it) }
    }

    private fun resolveCustomTemplate(
        context: Context,
        trackId: String,
        isCircuit: Boolean
    ): TrackReferenceRoute? {
        val customTrack = CustomTrackStorage.loadCustomTrackV2(context, trackId) ?: return null
        if (customTrack.referencePath.size < 2) return null

        val closeLoop = isCircuit && customTrack.mode == CustomTrackMode.CIRCUIT
        val startFinishAnchor = customTrack.startGate?.let { gate ->
            GeoPoint(
                latitude = (gate.start.latitude + gate.end.latitude) / 2.0,
                longitude = (gate.start.longitude + gate.end.longitude) / 2.0
            )
        }

        return TrackReferenceRoute.fromPoints(
            points = customTrack.referencePath,
            closeLoop = closeLoop,
            startFinishAnchor = startFinishAnchor
        )?.takeIf { isSuitableForDistanceCompare(it) }
    }

    private fun isSuitableForDistanceCompare(route: TrackReferenceRoute): Boolean {
        val pointCount = route.points.size
        if (pointCount < MIN_REFERENCE_POINTS_FOR_COMPARE) return false
        val segmentCount = (pointCount - 1).coerceAtLeast(1)
        val averageSegmentMeters = route.totalLengthMeters / segmentCount.toFloat()
        if (averageSegmentMeters > MAX_AVERAGE_SEGMENT_METERS) return false
        return route.totalLengthMeters >= 100f
    }

    fun isOfficialCircuit(context: Context, trackId: String): Boolean {
        return com.revix.app.track.catalog.OfficialTrackCatalog.tracks.any {
            it.id == trackId && it.mode == TrackMode.CIRCUIT
        }
    }
}
