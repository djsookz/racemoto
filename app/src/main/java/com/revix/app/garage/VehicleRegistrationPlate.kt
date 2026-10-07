package com.revix.app.garage

import java.util.Locale

object VehicleRegistrationPlate {
    private val latinPlate = Regex("^([A-Z]{1,2})(\\d{4})([A-Z]{1,2})$")
    private val greekPlate = Regex("^([Α-Ω]{2,3})(\\d{4})$")

    fun sanitize(raw: String?): String {
        return raw.orEmpty()
            .uppercase(Locale.getDefault())
            .filter { it.isLetterOrDigit() }
    }

    fun display(raw: String?): String {
        val clean = sanitize(raw)
        if (clean.isEmpty()) return ""
        latinPlate.matchEntire(clean)?.let { match ->
            return "${match.groupValues[1]} ${match.groupValues[2]} ${match.groupValues[3]}"
        }
        greekPlate.matchEntire(clean)?.let { match ->
            return "${match.groupValues[1]} ${match.groupValues[2]}"
        }
        return clean
    }
}
