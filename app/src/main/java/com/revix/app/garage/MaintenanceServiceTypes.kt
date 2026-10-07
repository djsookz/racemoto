package com.revix.app.garage

import android.content.Context
import com.revix.app.R
import java.util.Locale

object MaintenanceServiceTypes {
    val defaultTypes = listOf(
        "Oil Change",
        "Engine",
        "Wheels",
        "Gearbox",
        "Suspension",
        "Brakes",
        "Electrical"
    )

    fun localizedLabel(context: Context, serviceType: String): String {
        return when (serviceType.trim().lowercase(Locale.ROOT)) {
            "oil change" -> context.getString(R.string.maintenance_service_oil_change)
            "engine" -> context.getString(R.string.maintenance_service_engine)
            "wheels", "wheel", "tyres", "tires" -> context.getString(R.string.maintenance_service_wheels)
            "gearbox" -> context.getString(R.string.maintenance_service_gearbox)
            "suspension" -> context.getString(R.string.maintenance_service_suspension)
            "brakes", "brake" -> context.getString(R.string.maintenance_service_brakes)
            "electrical" -> context.getString(R.string.maintenance_service_electrical)
            else -> serviceType.trim()
        }
    }
}
