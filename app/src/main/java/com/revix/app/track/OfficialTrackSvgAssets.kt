package com.revix.app.track

object OfficialTrackSvgAssets {
    private const val FOLDER = "tracks"

    private val fileByTrackId = mapOf(
        "serres_circuit" to "Serres Automotive.svg",
        "monza_circuit" to "Monza.svg",
        "mugello_circuit" to "Mugello Grand Prix.svg",
        "misano_circuit" to "Misano.svg",
        "red_bull_ring" to "Red Bull Ring (A1).svg",
        "brno_circuit" to "Brno.svg",
        "nurburgring_gp" to "Nurburgring Grand Prix Strecke.svg",
        "nurburgring_nordschleife" to "Nurburgring Nordschleife.svg",
        "sachsenring" to "Sachsenring.svg",
        "assen_tt" to "TT Circuit Assen.svg",
        "aragon_motorland" to "Motorland Aragon.svg",
        "jerez_circuit" to "Jerez.svg",
        "portimao_circuit" to "Portimao.svg",
        "spa_francorchamps" to "Spa-Francorchamps.svg",
        "hungaroring" to "Hungaroring.svg",
        "megara_circuit" to "Megara.svg",
        "drakon_kaloyanovo" to "Kaloyanovo New.svg",
        "lara_a1_moto_park" to "A1 Motor Park.svg",
        "lauta_karting_track" to "Lauta.svg",
        "krasna_polyana" to "Krasna polqna.svg",
        "vratsa" to "Vratsa.svg",
        "go_kart_haskovo" to "Haskovo.svg",
        "go_kart_varna" to "Varna.svg",
        "carting_todor_slavov" to "Karting Academy Tvarditsa 4 - CW.svg",
        "go_kart_pautalia" to "Kyustendil.svg",
        "kartodromo_afidnes" to "Kartodromo Afidnes.svg",
        "sfi_karting" to "SFi KARTING.svg",
        "speed_force" to "Speed Force.svg",
        "sparta_racing_circuit_motul" to "Sparta Racing Circuit MOTUL.svg",
        "san_nikolas_kalamata" to "San Nikolas Kalamata.svg"
    )

    fun assetPathFor(trackId: String): String? {
        val fileName = fileByTrackId[trackId] ?: return null
        return "$FOLDER/$fileName"
    }
}
