package com.revix.app.track.catalog

import android.content.Context
import com.revix.app.GeoPoint
import com.revix.app.R

data class TrackGate(
    val start: GeoPoint,
    val end: GeoPoint
)

enum class TrackMode {
    CIRCUIT,
    POINT_TO_POINT
}

data class TrackDefinition(
    val id: String,
    val name: String,
    val description: String,
    val country: String,
    val lengthKm: Double,
    val turns: Int,
    val mode: TrackMode = TrackMode.CIRCUIT,
    val isEnabled: Boolean = true,
    val gpxResourceId: Int? = null,
    val startFinishGate: TrackGate? = null,
    val startGate: TrackGate? = null,
    val finishGate: TrackGate? = null,
    val lapSequence: List<GeoPoint> = emptyList()
) {
    fun detailsText(context: Context): String =
        context.getString(R.string.track_details_meta, lengthKm.toString(), turns, country)

    fun isReadyForSession(): Boolean {
        val hasTrackData = gpxResourceId != null || lapSequence.isNotEmpty()
        return isEnabled && hasTrackData
    }
}
