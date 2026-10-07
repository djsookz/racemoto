package com.revix.app.main.startup

import android.content.Intent
import com.revix.app.Profile

object ProfileLaunchParser {

    fun parse(intent: Intent): Profile {
        return intent.getSerializableExtra("SELECTED_PROFILE") as? Profile
            ?: Profile(name = "My profile", vehicleType = Profile.VehicleType.MOTORCYCLE)
    }
}
