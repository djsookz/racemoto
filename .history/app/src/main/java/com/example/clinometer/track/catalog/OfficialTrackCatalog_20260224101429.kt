package com.example.clinometer.track.catalog

import com.example.clinometer.GeoPoint
import com.example.clinometer.R

object OfficialTrackCatalog {
    private val serresStart = GeoPoint(41.073128, 23.517839)
    private val serresSector2 = GeoPoint(41.070481, 23.519244)
    private val serresSector3 = GeoPoint(41.072907, 23.516091)
    private val serresSector4 = GeoPoint(41.071511, 23.513143)

    val tracks: List<TrackDefinition> = listOf(
        TrackDefinition(
            id = "serres_circuit",
            name = "Serres Circuit",
            description = "Професионална писта за мотоциклети и автомобили с 4 timing точки за прецизни измервания.",
            country = "Гърция",
            lengthKm = 3.2,
            turns = 12,
            mode = TrackMode.CIRCUIT,
            gpxResourceId = R.raw.serres_circuit,
            startFinishGate = TrackGate(start = serresStart, end = serresStart),
            lapSequence = listOf(
                serresStart,
                serresSector2,
                serresSector3,
                serresSector4,
                serresStart
            )
        )
    )

    fun getAll(): List<TrackDefinition> = tracks

    fun getById(trackId: String): TrackDefinition? = tracks.firstOrNull { it.id == trackId }
}
