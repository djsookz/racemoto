package com.example.clinometer.main.navigation

import android.content.Context
import com.example.clinometer.R
import com.example.clinometer.navigation.DirectionsStep

data class ManeuverDisplayModel(
    val distanceText: String,
    val primaryText: String,
    val secondaryText: String?,
    val iconRes: Int
)

object ManeuverDisplayPresenter {
    fun build(
        context: Context,
        step: DirectionsStep,
        distanceToManeuver: Double
    ): ManeuverDisplayModel {
        val distance = if (distanceToManeuver >= 0) distanceToManeuver else step.distance
        val distanceText = RouteMath.formatDistance(distance)

        val instruction = step.maneuver?.instruction
            ?: step.bannerInstructions?.firstOrNull()?.primary?.text
            ?: step.name
            ?: "Continue"

        val primaryText = ManeuverFormatter.formatManeuverInstruction(context, instruction)
        val secondaryText = step.bannerInstructions?.firstOrNull()?.secondary?.text

        val parsedFromInstruction = ManeuverFormatter.parseManeuverFromInstruction(instruction)
        val parsedType = parsedFromInstruction.first
        val parsedModifier = parsedFromInstruction.second

        val bannerPrimary = step.bannerInstructions?.firstOrNull()?.primary
        val maneuverType = parsedType ?: bannerPrimary?.type ?: step.maneuver?.type
        val maneuverModifier = parsedModifier ?: bannerPrimary?.modifier ?: step.maneuver?.modifier

        val iconRes = ManeuverFormatter.getManeuverIcon(maneuverType, maneuverModifier)

        return ManeuverDisplayModel(
            distanceText = distanceText,
            primaryText = primaryText,
            secondaryText = secondaryText,
            iconRes = iconRes
        )
    }
}
