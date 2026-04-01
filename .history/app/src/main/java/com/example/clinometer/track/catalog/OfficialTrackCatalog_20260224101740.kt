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
            isEnabled = true,
            gpxResourceId = R.raw.serres_circuit,
            startFinishGate = TrackGate(start = serresStart, end = serresStart),
            lapSequence = listOf(
                serresStart,
                serresSector2,
                serresSector3,
                serresSector4,
                serresStart
            )
        ),
        TrackDefinition(
            id = "monza_circuit",
            name = "Monza",
            description = "Official EU track (coming soon).",
            country = "Италия",
            lengthKm = 5.79,
            turns = 11,
            mode = TrackMode.CIRCUIT,
            isEnabled = false
        ),
        TrackDefinition(
            id = "mugello_circuit",
            name = "Mugello",
            description = "Official EU track (coming soon).",
            country = "Италия",
            lengthKm = 5.25,
            turns = 15,
            mode = TrackMode.CIRCUIT,
            isEnabled = false
        ),
        TrackDefinition(
            id = "misano_circuit",
            name = "Misano",
            description = "Official EU track (coming soon).",
            country = "Италия",
            lengthKm = 4.23,
            turns = 16,
            mode = TrackMode.CIRCUIT,
            isEnabled = false
        ),
        TrackDefinition(
            id = "red_bull_ring",
            name = "Red Bull Ring",
            description = "Official EU track (coming soon).",
            country = "Австрия",
            lengthKm = 4.32,
            turns = 10,
            mode = TrackMode.CIRCUIT,
            isEnabled = false
        ),
        TrackDefinition(
            id = "brno_circuit",
            name = "Brno",
            description = "Official EU track (coming soon).",
            country = "Чехия",
            lengthKm = 5.4,
            turns = 14,
            mode = TrackMode.CIRCUIT,
            isEnabled = false
        ),
        TrackDefinition(
            id = "nurburgring_gp",
            name = "Nürburgring GP",
            description = "Official EU track (coming soon).",
            country = "Германия",
            lengthKm = 5.15,
            turns = 16,
            mode = TrackMode.CIRCUIT,
            isEnabled = false
        ),
        TrackDefinition(
            id = "sachsenring",
            name = "Sachsenring",
            description = "Official EU track (coming soon).",
            country = "Германия",
            lengthKm = 3.67,
            turns = 13,
            mode = TrackMode.CIRCUIT,
            isEnabled = false
        ),
        TrackDefinition(
            id = "assen_tt",
            name = "Assen",
            description = "Official EU track (coming soon).",
            country = "Нидерландия",
            lengthKm = 4.54,
            turns = 18,
            mode = TrackMode.CIRCUIT,
            isEnabled = false
        ),
        TrackDefinition(
            id = "aragon_motorland",
            name = "MotorLand Aragón",
            description = "Official EU track (coming soon).",
            country = "Испания",
            lengthKm = 5.08,
            turns = 17,
            mode = TrackMode.CIRCUIT,
            isEnabled = false
        ),
        TrackDefinition(
            id = "jerez_circuit",
            name = "Jerez",
            description = "Official EU track (coming soon).",
            country = "Испания",
            lengthKm = 4.42,
            turns = 13,
            mode = TrackMode.CIRCUIT,
            isEnabled = false
        ),
        TrackDefinition(
            id = "portimao_circuit",
            name = "Portimão",
            description = "Official EU track (coming soon).",
            country = "Португалия",
            lengthKm = 4.59,
            turns = 15,
            mode = TrackMode.CIRCUIT,
            isEnabled = false
        ),
        TrackDefinition(
            id = "spa_francorchamps",
            name = "Spa-Francorchamps",
            description = "Official EU track (coming soon).",
            country = "Белгия",
            lengthKm = 7.0,
            turns = 19,
            mode = TrackMode.CIRCUIT,
            isEnabled = false
        ),
        TrackDefinition(
            id = "hungaroring",
            name = "Hungaroring",
            description = "Official EU track (coming soon).",
            country = "Унгария",
            lengthKm = 4.38,
            turns = 14,
            mode = TrackMode.CIRCUIT,
            isEnabled = false
        )
    )

    fun getAll(): List<TrackDefinition> = tracks

    fun getById(trackId: String): TrackDefinition? = tracks.firstOrNull { it.id == trackId }
}
