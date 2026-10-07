package com.revix.app

import android.content.Context
import com.revix.app.data.ProfileStorage
import com.revix.app.garage.VehicleProfileSpecs

data class HudVehicleIdentity(
    val title: String = "",
    val specs: String = ""
) {
    val isEmpty: Boolean get() = title.isBlank() && specs.isBlank()

    companion object {
        fun from(profile: Profile?, context: Context): HudVehicleIdentity {
            val title = profile?.name.orEmpty().trim()
            val specs = VehicleProfileSpecs.displayLine(
                profile?.engineDisplacementCc,
                profile?.powerHp,
                context.getString(R.string.garage_spec_cc_unit),
                context.getString(R.string.garage_spec_hp_unit)
            )
            return HudVehicleIdentity(title = title, specs = specs)
        }

        fun resolve(context: Context, profileId: Long? = null): HudVehicleIdentity {
            val profiles = ProfileStorage.loadProfiles(context)
            val selectedId = profileId?.takeIf { it > 0L } ?: ProfileStorage.getSelectedProfileId(context)
            val profile = profiles.find { it.id == selectedId } ?: profiles.firstOrNull()
            return from(profile, context)
        }
    }
}
