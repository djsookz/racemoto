package com.revix.app.track

import android.content.Context
import com.revix.app.GeoPoint
import com.revix.app.tracking.CustomTrackStorage

object TrackCompareSectorLoader {
    fun loadSectorDistancesMeters(
        context: Context,
        trackId: String,
        referenceRoute: TrackReferenceRoute?
    ): List<Float> {
        if (referenceRoute == null) return emptyList()
        val normalizedTrackId = trackId.trim()
        if (normalizedTrackId.isBlank() || !normalizedTrackId.startsWith("custom_")) {
            return emptyList()
        }

        val customTrack = CustomTrackStorage.loadCustomTrackV2(context, normalizedTrackId) ?: return emptyList()
        if (customTrack.sectorGates.isEmpty()) return emptyList()

        return customTrack.sectorGates.mapNotNull { gate ->
            val anchor = GeoPoint(
                latitude = (gate.start.latitude + gate.end.latitude) / 2.0,
                longitude = (gate.start.longitude + gate.end.longitude) / 2.0
            )
            referenceRoute.distanceFromStartFinish(anchor.latitude, anchor.longitude)
                .takeIf { it.isFinite() && it > 25f }
        }
            .distinct()
            .sorted()
    }
}
