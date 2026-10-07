package com.revix.app.track.catalog

import com.revix.app.GeoPoint
import com.revix.app.R

object OfficialTrackCatalog {
    private val serresStartA = GeoPoint(41.073075, 23.517775)
    private val serresStartB = GeoPoint(41.073175, 23.517891666666667)
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
    private val nurburgringNordschleifeStartA = GeoPoint(50.34352777777778, 6.960155555555556)
    private val nurburgringNordschleifeStartB = GeoPoint(50.34365, 6.960030555555556)
    private val nurburgringNordschleifeFinishA = GeoPoint(50.35170833333333, 6.980819444444445)
    private val nurburgringNordschleifeFinishB = GeoPoint(50.35183055555556, 6.980702777777777)
    private val sachsenringStartA = GeoPoint(50.79171666666667, 12.688113888888888)
    private val sachsenringStartB = GeoPoint(50.79181666666666, 12.688005555555555)
    private val assenStartA = GeoPoint(52.96237222222222, 6.524047222222223)
    private val assenStartB = GeoPoint(52.962275, 6.524172222222222)
    private val aragonStartA = GeoPoint(41.07823333333333, -0.19787222222222222)
    private val aragonStartB = GeoPoint(41.07833333333333, -0.19776111111111112)
    private val jerezStartA = GeoPoint(36.70966388888889, -6.032563888888889)
    private val jerezStartB = GeoPoint(36.709705555555556, -6.032447222222222)
    private val portimaoStartA = GeoPoint(37.23209722222222, -8.630919444444444)
    private val portimaoStartB = GeoPoint(37.232141666666665, -8.630727777777778)
    private val spaStartA = GeoPoint(50.44403055555556, 5.965080555555555)
    private val spaStartB = GeoPoint(50.44409444444444, 5.965258333333334)
    private val hungaroringStartA = GeoPoint(47.57882777777778, 19.24836111111111)
    private val hungaroringStartB = GeoPoint(47.578925, 19.248475)

    private val megaraStartA = GeoPoint(37.98696664095969, 23.362928848936896)
    private val megaraStartB = GeoPoint(37.98705912774901, 23.362896662427453)
    private val drakonKaloyanovoStartA = GeoPoint(42.34099976493223, 24.737018670583495)
    private val drakonKaloyanovoStartB = GeoPoint(42.341092944849755, 24.73707834973642)
    private val laraA1MotoParkStartA = GeoPoint(42.31440495213859, 23.539085111327907)
    private val laraA1MotoParkStartB = GeoPoint(42.314391068398336, 23.539262137129864)
    private val lautaKartingStartA = GeoPoint(42.13610093705698, 24.768944609598083)
    private val lautaKartingStartB = GeoPoint(42.13613972256131, 24.76905256851518)
    private val krasnaPolyanaStartA = GeoPoint(42.6911714116843, 23.289239614924146)
    private val krasnaPolyanaStartB = GeoPoint(42.69124537025713, 23.289183266961405)
    private val vratsaStartA = GeoPoint(43.19915869333897, 23.571424492280254)
    private val vratsaStartB = GeoPoint(43.19926427799753, 23.57145801989426)
    private val haskovoKartStartA = GeoPoint(41.96390237398987, 25.551649903794644)
    private val haskovoKartStartB = GeoPoint(41.963921327318964, 25.55176527854574)
    private val varnaKartStartA = GeoPoint(43.23385583472042, 27.964840066577004)
    private val varnaKartStartB = GeoPoint(43.23388368150691, 27.964978870898985)
    private val todorSlavovKartStartA = GeoPoint(42.69296151210892, 25.89868213519028)
    private val todorSlavovKartStartB = GeoPoint(42.69287674107923, 25.89871700390885)
    private val pautaliaKartStartA = GeoPoint(42.266812642827354, 22.67321587138144)
    private val pautaliaKartStartB = GeoPoint(42.26688856484018, 22.6731193118531)
    private val thessalonikiDriveParkStartA = GeoPoint(40.51436974775059, 22.993026288810103)
    private val thessalonikiDriveParkStartB = GeoPoint(40.514286653121026, 22.99300751334626)
    private val afidnesKartStartA = GeoPoint(38.17783562224858, 23.85629690689459)
    private val afidnesKartStartB = GeoPoint(38.1779320848656, 23.85630696517879)
    private val sfiKartingStartA = GeoPoint(37.94933543941032, 23.918341763017136)
    private val sfiKartingStartB = GeoPoint(37.94938408597875, 23.918245203488798)
    private val speedForceStartA = GeoPoint(37.856514222211935, 23.830697980392443)
    private val speedForceStartB = GeoPoint(37.856554988663056, 23.830753636231695)
    private val spartaRacingStartA = GeoPoint(37.04206988747829, 22.454773161141965)
    private val spartaRacingStartB = GeoPoint(37.042111100206185, 22.45490458938887)
    private val sanNikolasKalamataStartA = GeoPoint(37.02464387744247, 22.02876211800629)
    private val sanNikolasKalamataStartB = GeoPoint(37.02457909967665, 22.028788269545217)

    val tracks: List<TrackDefinition> = listOf(
        TrackDefinition(
            id = "serres_circuit",
            name = "Serres Circuit",
            description = "Професионална писта за мотоциклети и автомобили с 4 timing точки за прецизни измервания.",
            country = "Гърция",
            lengthKm = 3.186,
            turns = 16,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            gpxResourceId = R.raw.serres_circuit,
            startFinishGate = TrackGate(start = serresStartA, end = serresStartB),
            lapSequence = listOf(
                serresStartA,
                serresSector2,
                serresSector3,
                serresSector4,
                serresStartA
            )
        ),
        TrackDefinition(
            id = "monza_circuit",
            name = "Monza",
            description = "Официална писта (приближена старт/финиш точка).",
            country = "Италия",
            lengthKm = 5.793,
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
            lengthKm = 5.245,
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
            lengthKm = 4.226,
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
            lengthKm = 4.326,
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
            lengthKm = 5.403,
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
            lengthKm = 5.148,
            turns = 15,
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
            lengthKm = 20.832,
            turns = 73,
            mode = TrackMode.POINT_TO_POINT,
            isEnabled = true,
            startGate = TrackGate(start = nurburgringNordschleifeStartA, end = nurburgringNordschleifeStartB),
            finishGate = TrackGate(start = nurburgringNordschleifeFinishA, end = nurburgringNordschleifeFinishB),
            lapSequence = listOf(
                nurburgringNordschleifeStartA,
                nurburgringNordschleifeStartB,
                nurburgringNordschleifeFinishA,
                nurburgringNordschleifeFinishB
            )
        ),
        TrackDefinition(
            id = "sachsenring",
            name = "Sachsenring",
            description = "Официална писта (приближена старт/финиш точка).",
            country = "Германия",
            lengthKm = 3.671,
            turns = 13,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = sachsenringStartA, end = sachsenringStartB),
            lapSequence = listOf(sachsenringStartA, sachsenringStartB)
        ),
        TrackDefinition(
            id = "assen_tt",
            name = "Assen",
            description = "Официална писта (приближена старт/финиш точка).",
            country = "Нидерландия",
            lengthKm = 4.542,
            turns = 18,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = assenStartA, end = assenStartB),
            lapSequence = listOf(assenStartA, assenStartB)
        ),
        TrackDefinition(
            id = "aragon_motorland",
            name = "MotorLand Aragón",
            description = "Официална писта (приближена старт/финиш точка).",
            country = "Испания",
            lengthKm = 5.078,
            turns = 17,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = aragonStartA, end = aragonStartB),
            lapSequence = listOf(aragonStartA, aragonStartB)
        ),
        TrackDefinition(
            id = "jerez_circuit",
            name = "Jerez",
            description = "Официална писта.",
            country = "Испания",
            lengthKm = 4.423,
            turns = 13,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = jerezStartA, end = jerezStartB),
            lapSequence = listOf(jerezStartA, jerezStartB)
        ),
        TrackDefinition(
            id = "portimao_circuit",
            name = "Portimão",
            description = "Официална писта.",
            country = "Португалия",
            lengthKm = 4.592,
            turns = 15,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = portimaoStartA, end = portimaoStartB),
            lapSequence = listOf(portimaoStartA, portimaoStartB)
        ),
        TrackDefinition(
            id = "spa_francorchamps",
            name = "Spa-Francorchamps",
            description = "Официална писта.",
            country = "Белгия",
            lengthKm = 7.004,
            turns = 19,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = spaStartA, end = spaStartB),
            lapSequence = listOf(spaStartA, spaStartB)
        ),
        TrackDefinition(
            id = "hungaroring",
            name = "Hungaroring",
            description = "Официална писта.",
            country = "Унгария",
            lengthKm = 4.381,
            turns = 14,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = hungaroringStartA, end = hungaroringStartB),
            lapSequence = listOf(hungaroringStartA, hungaroringStartB)
        ),
        TrackDefinition(
            id = "megara_circuit",
            name = "Athens Circuit Megara",
            description = "Официална писта в Гърция.",
            country = "Гърция",
            lengthKm = 2.1,
            turns = 10,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = megaraStartA, end = megaraStartB),
            lapSequence = listOf(megaraStartA, megaraStartB)
        ),
        TrackDefinition(
            id = "drakon_kaloyanovo",
            name = "Pista Drakon Kaloyanovo",
            description = "Официална писта в България.",
            country = "България",
            lengthKm = 2.9,
            turns = 16,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = drakonKaloyanovoStartA, end = drakonKaloyanovoStartB),
            lapSequence = listOf(drakonKaloyanovoStartA, drakonKaloyanovoStartB)
        ),
        TrackDefinition(
            id = "lara_a1_moto_park",
            name = "Pista Lara / A1 Moto Park",
            description = "Официална писта в България.",
            country = "България",
            lengthKm = 3.91,
            turns = 15,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = laraA1MotoParkStartA, end = laraA1MotoParkStartB),
            lapSequence = listOf(laraA1MotoParkStartA, laraA1MotoParkStartB)
        ),
        TrackDefinition(
            id = "lauta_karting_track",
            name = "Lauta Karting Track",
            description = "Официална писта в България.",
            country = "България",
            lengthKm = 0.87,
            turns = 15,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = lautaKartingStartA, end = lautaKartingStartB),
            lapSequence = listOf(lautaKartingStartA, lautaKartingStartB)
        ),
        TrackDefinition(
            id = "krasna_polyana",
            name = "Krasna Polyana",
            description = "Официална писта в България.",
            country = "България",
            lengthKm = 1.0,
            turns = 8,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = krasnaPolyanaStartA, end = krasnaPolyanaStartB),
            lapSequence = listOf(krasnaPolyanaStartA, krasnaPolyanaStartB)
        ),
        TrackDefinition(
            id = "vratsa",
            name = "Vratsa",
            description = "Официална писта в България.",
            country = "България",
            lengthKm = 0.92,
            turns = 13,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = vratsaStartA, end = vratsaStartB),
            lapSequence = listOf(vratsaStartA, vratsaStartB)
        ),
        TrackDefinition(
            id = "go_kart_haskovo",
            name = "Go-Kart Haskovo",
            description = "Официална писта в България.",
            country = "България",
            lengthKm = 1.0,
            turns = 8,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = haskovoKartStartA, end = haskovoKartStartB),
            lapSequence = listOf(haskovoKartStartA, haskovoKartStartB)
        ),
        TrackDefinition(
            id = "go_kart_varna",
            name = "Go-Kart Varna",
            description = "Официална писта в България.",
            country = "България",
            lengthKm = 1.05,
            turns = 15,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = varnaKartStartA, end = varnaKartStartB),
            lapSequence = listOf(varnaKartStartA, varnaKartStartB)
        ),
        TrackDefinition(
            id = "carting_todor_slavov",
            name = "Carting Todor Slavov",
            description = "Официална писта в България.",
            country = "България",
            lengthKm = 1.0,
            turns = 8,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = todorSlavovKartStartA, end = todorSlavovKartStartB),
            lapSequence = listOf(todorSlavovKartStartA, todorSlavovKartStartB)
        ),
        TrackDefinition(
            id = "go_kart_pautalia",
            name = "Go-Kart Pautalia",
            description = "Официална писта в България.",
            country = "България",
            lengthKm = 1.05,
            turns = 8,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = pautaliaKartStartA, end = pautaliaKartStartB),
            lapSequence = listOf(pautaliaKartStartA, pautaliaKartStartB)
        ),
        TrackDefinition(
            id = "drive_park_thessaloniki",
            name = "Drive Park Thessaloniki",
            description = "Официална писта в Гърция.",
            country = "Гърция",
            lengthKm = 1.268,
            turns = 16,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = thessalonikiDriveParkStartA, end = thessalonikiDriveParkStartB),
            lapSequence = listOf(thessalonikiDriveParkStartA, thessalonikiDriveParkStartB)
        ),
        TrackDefinition(
            id = "kartodromo_afidnes",
            name = "Kartodromo Afidnes",
            description = "Официална писта в Гърция.",
            country = "Гърция",
            lengthKm = 1.122,
            turns = 8,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = afidnesKartStartA, end = afidnesKartStartB),
            lapSequence = listOf(afidnesKartStartA, afidnesKartStartB)
        ),
        TrackDefinition(
            id = "sfi_karting",
            name = "SFi Karting",
            description = "Официална писта в Гърция.",
            country = "Гърция",
            lengthKm = 1.0,
            turns = 8,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = sfiKartingStartA, end = sfiKartingStartB),
            lapSequence = listOf(sfiKartingStartA, sfiKartingStartB)
        ),
        TrackDefinition(
            id = "speed_force",
            name = "Speed Force",
            description = "Официална писта в Гърция.",
            country = "Гърция",
            lengthKm = 1.0,
            turns = 8,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = speedForceStartA, end = speedForceStartB),
            lapSequence = listOf(speedForceStartA, speedForceStartB)
        ),
        TrackDefinition(
            id = "sparta_racing_circuit_motul",
            name = "Sparta Racing Circuit MOTUL",
            description = "Официална писта в Гърция.",
            country = "Гърция",
            lengthKm = 1.0,
            turns = 8,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = spartaRacingStartA, end = spartaRacingStartB),
            lapSequence = listOf(spartaRacingStartA, spartaRacingStartB)
        ),
        TrackDefinition(
            id = "san_nikolas_kalamata",
            name = "San Nikolas Kalamata",
            description = "Официална писта в Гърция.",
            country = "Гърция",
            lengthKm = 1.0,
            turns = 8,
            mode = TrackMode.CIRCUIT,
            isEnabled = true,
            startFinishGate = TrackGate(start = sanNikolasKalamataStartA, end = sanNikolasKalamataStartB),
            lapSequence = listOf(sanNikolasKalamataStartA, sanNikolasKalamataStartB)
        )
    )

    fun getAll(): List<TrackDefinition> = tracks

    fun getById(trackId: String): TrackDefinition? = tracks.firstOrNull { it.id == trackId }
}
