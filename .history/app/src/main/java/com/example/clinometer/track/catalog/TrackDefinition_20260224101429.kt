package com.example.clinometer.track.catalog

import com.example.clinometer.GeoPoint

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
    val gpxResourceId: Int? = null,
    val startFinishGate: TrackGate? = null,
    val lapSequence: List<GeoPoint> = emptyList()
) {
    fun detailsText(): String = "${lengthKm}km • ${turns} завоя • $country"
}
