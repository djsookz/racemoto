package com.example.clinometer.track.custom

import com.example.clinometer.GeoPoint

enum class CustomTrackMode {
    CIRCUIT,
    POINT_TO_POINT
}

data class GateLine(
    val start: GeoPoint,
    val end: GeoPoint
)

data class CustomTrackDefinitionV2(
    val id: String,
    val name: String,
    val mode: CustomTrackMode,
    val createdAt: Long = System.currentTimeMillis(),
    val startGate: GateLine? = null,
    val finishGate: GateLine? = null,
    val sectorGates: List<GateLine> = emptyList(),
    val referencePath: List<GeoPoint> = emptyList()
)
