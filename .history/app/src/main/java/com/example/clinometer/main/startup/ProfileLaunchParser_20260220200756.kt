package com.example.clinometer.main.startup

import android.content.Intent
import com.example.clinometer.Profile

object ProfileLaunchParser {

    fun parse(intent: Intent): Profile {
        return intent.getSerializableExtra("SELECTED_PROFILE") as? Profile
            ?: Profile(name = "My profile", vehicleType = Profile.VehicleType.MOTORCYCLE)
    }
}
