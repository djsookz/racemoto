package com.example.clinometer.track.catalog

import com.example.clinometer.GeoPoint
import com.example.clinometer.R

object OfficialTrackCatalog {
    private val serresStart = GeoPoint(41.073128, 23.517839)
    private val serresSector2 = GeoPoint(41.070481, 23.519244)
    private val serresSector3 = GeoPoint(41.072907, 23.516091)
    private val serresSector4 = GeoPoint(41.071511, 23.513143)

    private val monzaStart = GeoPoint(45.620556, 9.289444)
    private val mugelloStart = GeoPoint(43.997500, 11.371944)
    private val misanoStart = GeoPoint(43.961389, 12.683333)
    private val redBullRingStart = GeoPoint(47.219722, 14.764722)
    private val brnoStart = GeoPoint(49.204722, 16.450556)
    private val nurburgringGpStart = GeoPoint(50.335556, 6.947500)
    private val sachsenringStart = GeoPoint(50.791667, 12.688889)
    private val assenStart = GeoPoint(52.961667, 6.523333)
    private val aragonStart = GeoPoint(41.078333, -0.207500)
    private val jerezStart = GeoPoint(36.708333, -6.034167)
    private val portimaoStart = GeoPoint(37.231944, -8.631944)
    private val spaStart = GeoPoint(50.437222, 5.971389)
    private val hungaroringStart = GeoPoint(47.582222, 19.251111)

    private val megaraStart = GeoPoint(37.986669, 23.362808)
    private val sofiaKrasnaPolyanaKartStart = GeoPoint(42.691701, 23.289639)
    private val slivenKartStart = GeoPoint(42.636831, 26.324128)

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
            description = "Официална писта (приближена старт/финиш точка).",
            country = "Италия",
            lengthKm = 5.79,
            turns = 11,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = monzaStart, end = monzaStart),
            lapSequence = listOf(monzaStart)
        ),
        TrackDefinition(
            id = "mugello_circuit",
            name = "Mugello",
            description = "Официална писта (приближена старт/финиш точка).",
            country = "Италия",
            lengthKm = 5.25,
            turns = 15,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = mugelloStart, end = mugelloStart),
            lapSequence = listOf(mugelloStart)
        ),
        TrackDefinition(
            id = "misano_circuit",
            name = "Misano",
            description = "Официална писта (приближена старт/финиш точка).",
            country = "Италия",
            lengthKm = 4.23,
            turns = 16,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = misanoStart, end = misanoStart),
            lapSequence = listOf(misanoStart)
        ),
        TrackDefinition(
            id = "red_bull_ring",
            name = "Red Bull Ring",
            description = "Официална писта (приближена старт/финиш точка).",
            country = "Австрия",
            lengthKm = 4.32,
            turns = 10,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = redBullRingStart, end = redBullRingStart),
            lapSequence = listOf(redBullRingStart)
        ),
        TrackDefinition(
            id = "brno_circuit",
            name = "Brno",
            description = "Официална писта (приближена старт/финиш точка).",
            country = "Чехия",
            lengthKm = 5.4,
            turns = 14,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = brnoStart, end = brnoStart),
            lapSequence = listOf(brnoStart)
        ),
        TrackDefinition(
            id = "nurburgring_gp",
            name = "Nürburgring GP",
            description = "Официална писта (приближена старт/финиш точка).",
            country = "Германия",
            lengthKm = 5.15,
            turns = 16,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = nurburgringGpStart, end = nurburgringGpStart),
            lapSequence = listOf(nurburgringGpStart)
        ),
        TrackDefinition(
            id = "sachsenring",
            name = "Sachsenring",
            description = "Официална писта (приближена старт/финиш точка).",
            country = "Германия",
            lengthKm = 3.67,
            turns = 13,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = sachsenringStart, end = sachsenringStart),
            lapSequence = listOf(sachsenringStart)
        ),
        TrackDefinition(
            id = "assen_tt",
            name = "Assen",
            description = "Официална писта (приближена старт/финиш точка).",
            country = "Нидерландия",
            lengthKm = 4.54,
            turns = 18,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = assenStart, end = assenStart),
            lapSequence = listOf(assenStart)
        ),
        TrackDefinition(
            id = "aragon_motorland",
            name = "MotorLand Aragón",
            description = "Официална писта (приближена старт/финиш точка).",
            country = "Испания",
            lengthKm = 5.08,
            turns = 17,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = aragonStart, end = aragonStart),
            lapSequence = listOf(aragonStart)
        ),
        TrackDefinition(
            id = "jerez_circuit",
            name = "Jerez",
            description = "Официална писта (приближена старт/финиш точка).",
            country = "Испания",
            lengthKm = 4.42,
            turns = 13,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = jerezStart, end = jerezStart),
            lapSequence = listOf(jerezStart)
        ),
        TrackDefinition(
            id = "portimao_circuit",
            name = "Portimão",
            description = "Официална писта (приближена старт/финиш точка).",
            country = "Португалия",
            lengthKm = 4.59,
            turns = 15,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = portimaoStart, end = portimaoStart),
            lapSequence = listOf(portimaoStart)
        ),
        TrackDefinition(
            id = "spa_francorchamps",
            name = "Spa-Francorchamps",
            description = "Официална писта (приближена старт/финиш точка).",
            country = "Белгия",
            lengthKm = 7.0,
            turns = 19,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = spaStart, end = spaStart),
            lapSequence = listOf(spaStart)
        ),
        TrackDefinition(
            id = "hungaroring",
            name = "Hungaroring",
            description = "Официална писта (приближена старт/финиш точка).",
            country = "Унгария",
            lengthKm = 4.38,
            turns = 14,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = hungaroringStart, end = hungaroringStart),
            lapSequence = listOf(hungaroringStart)
        ),
        TrackDefinition(
            id = "megara_circuit",
            name = "Athens Circuit Megara",
            description = "Официална писта в Гърция (приближена старт/финиш точка).",
            country = "Гърция",
            lengthKm = 2.1,
            turns = 11,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = megaraStart, end = megaraStart),
            lapSequence = listOf(megaraStart)
        ),
        TrackDefinition(
            id = "sofia_krasna_polyana_kart",
            name = "Sofia Krasna Polyana Kart",
            description = "Официална писта в България (приближена старт/финиш точка).",
            country = "България",
            lengthKm = 0.9,
            turns = 8,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = sofiaKrasnaPolyanaKartStart, end = sofiaKrasnaPolyanaKartStart),
            lapSequence = listOf(sofiaKrasnaPolyanaKartStart)
        ),
        TrackDefinition(
            id = "sliven_kart_track",
            name = "Sliven Kart Track",
            description = "Официална писта в България (приближена старт/финиш точка).",
            country = "България",
            lengthKm = 1.2,
            turns = 9,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = slivenKartStart, end = slivenKartStart),
            lapSequence = listOf(slivenKartStart)
        )
    )

    fun getAll(): List<TrackDefinition> = tracks

    fun getById(trackId: String): TrackDefinition? = tracks.firstOrNull { it.id == trackId }
}
