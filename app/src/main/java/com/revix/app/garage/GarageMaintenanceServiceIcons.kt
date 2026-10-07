package com.revix.app.garage

import com.revix.app.R
import java.util.Locale

object GarageMaintenanceServiceIcons {
    fun resolveIconRes(serviceType: String?): Int? {
        return when (serviceType
            ?.trim()
            ?.lowercase(Locale.ROOT)
        ) {
            "oil change" -> R.drawable.car_oil
            "engine" -> R.drawable.piston
            "wheels", "wheel", "tyres", "tires" -> R.drawable.wheel
            "gearbox" -> R.drawable.gearbox
            "suspension" -> R.drawable.suspension
            "brakes", "brake" -> R.drawable.disc_brake
            "electrical" -> R.drawable.power
            else -> null
        }
    }
}