package com.example.clinometer.track.catalog

import com.example.clinometer.GeoPoint
import com.example.clinometer.R

object OfficialTrackCatalog {
    private val serresStart = GeoPoint(41.073128, 23.517839)
    private val serresSector2 = GeoPoint(41.070481, 23.519244)
    private val serresSector3 = GeoPoint(41.072907, 23.516091)
    private val serresSector4 = GeoPoint(41.071511, 23.513143)

    private val monzaStartA = GeoPoint(45.618966666666665, 9.281258333333334)
    private val monzaStartB = GeoPoint(45.618975, 9.28111111111111)
    private val mugelloStartA = GeoPoint(43.99757530017942, 11.37155573331234)
    private val mugelloStartB = GeoPoint(43.997623537622495, 11.371392789125483)
    private val misanoStartA = GeoPoint(43.96228888888889, 12.684191666666666)
    private val misanoStartB = GeoPoint(43.962244444444444, 12.684322222222223)
    private val redBullRingStartA = GeoPoint(47.22006944444445, 14.765194444444445)
    private val redBullRingStartB = GeoPoint(47.21996388888889, 14.76523611111111)
    private val brnoStartA = GeoPoint(49.20279722222222, 16.445408333333334)
    private val brnoStartB = GeoPoint(49.20291666666667, 16.44548888888889)
    private val nurburgringGpStartA = GeoPoint(50.33396388888889, 6.945369444444445)
    private val nurburgringGpStartB = GeoPoint(50.33406111111111, 6.945211111111111)
    private val nurburgringNordschleifeStartA = GeoPoint(50.335556, 6.947500)
    private val nurburgringNordschleifeStartB = GeoPoint(50.335636, 6.947362)
    private val sachsenringStart = GeoPoint(50.791667, 12.688889)
    private val assenStart = GeoPoint(52.961667, 6.523333)
    private val aragonStart = GeoPoint(41.078333, -0.207500)
    private val jerezStart = GeoPoint(36.708333, -6.034167)
    private val portimaoStart = GeoPoint(37.231944, -8.631944)
    private val spaStart = GeoPoint(50.437222, 5.971389)
    private val hungaroringStart = GeoPoint(47.582222, 19.251111)

    private val megaraStart = GeoPoint(37.986669, 23.362808)
    private val drakonKaloyanovoStart = GeoPoint(42.3412422, 24.7356018)
    private val laraA1MotoParkStart = GeoPoint(42.3146048, 23.5370067)
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
            startFinishGate = TrackGate(start = monzaStartA, end = monzaStartB),
            lapSequence = listOf(monzaStartA, monzaStartB)
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
            startFinishGate = TrackGate(start = mugelloStartA, end = mugelloStartB),
            lapSequence = listOf(mugelloStartA, mugelloStartB)
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
            startFinishGate = TrackGate(start = misanoStartA, end = misanoStartB),
            lapSequence = listOf(misanoStartA, misanoStartB)
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
            startFinishGate = TrackGate(start = redBullRingStartA, end = redBullRingStartB),
            lapSequence = listOf(redBullRingStartA, redBullRingStartB)
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
            startFinishGate = TrackGate(start = brnoStartA, end = brnoStartB),
            lapSequence = listOf(brnoStartA, brnoStartB)
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
            startFinishGate = TrackGate(start = nurburgringGpStartA, end = nurburgringGpStartB),
            lapSequence = listOf(nurburgringGpStartA, nurburgringGpStartB)
        ),
        TrackDefinition(
            id = "nurburgring_nordschleife",
            name = "Nürburgring Nordschleife",
            description = "Официална писта (приближена старт/финиш точка).",
            country = "Германия",
            lengthKm = 20.83,
            turns = 73,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = nurburgringNordschleifeStartA, end = nurburgringNordschleifeStartB),
            lapSequence = listOf(nurburgringNordschleifeStartA, nurburgringNordschleifeStartB)
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
            id = "drakon_kaloyanovo",
            name = "Pista Drakon Kaloyanovo",
            description = "Официална писта в България (приближена старт/финиш точка).",
            country = "България",
            lengthKm = 2.0,
            turns = 8,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = drakonKaloyanovoStart, end = drakonKaloyanovoStart),
            lapSequence = listOf(drakonKaloyanovoStart)
        ),
        TrackDefinition(
            id = "lara_a1_moto_park",
            name = "Pista Lara / A1 Moto Park",
            description = "Официална писта в България (приближена старт/финиш точка).",
            country = "България",
            lengthKm = 2.3,
            turns = 10,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = laraA1MotoParkStart, end = laraA1MotoParkStart),
            lapSequence = listOf(laraA1MotoParkStart)
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
