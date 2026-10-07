package com.example.clinometer.main.navigation

import com.example.clinometer.GeoPoint
import com.example.clinometer.navigation.DirectionsStep

data class NavigationStepSelection(
    val stepIndex: Int,
    val distanceToManeuver: Double
)

object NavigationStepSelector {
    fun select(
        currentLocation: GeoPoint,
        navigationSteps: List<DirectionsStep>,
        currentStepIndex: Int
    ): NavigationStepSelection {
        if (navigationSteps.isEmpty()) {
            return NavigationStepSelection(0, Double.MAX_VALUE)
        }

        val startIndex = currentStepIndex.coerceIn(0, navigationSteps.lastIndex)
        var bestStepIndex = startIndex
        var bestDistance = Double.MAX_VALUE

        for (index in startIndex until navigationSteps.size) {
            val step = navigationSteps[index]
            val distanceToManeuver = RouteMath.calculateDistanceToManeuver(currentLocation, step)
            if (distanceToManeuver < 10.0) continue

            bestStepIndex = index
            bestDistance = distanceToManeuver
            break
        }

        if (bestDistance == Double.MAX_VALUE) {
            for (index in navigationSteps.indices) {
                val step = navigationSteps[index]
                val distanceToManeuver = RouteMath.calculateDistanceToManeuver(currentLocation, step)
                if (distanceToManeuver < 10.0) continue

                bestStepIndex = index
                bestDistance = distanceToManeuver
                break
            }
        }

        if (bestDistance == Double.MAX_VALUE) {
            bestStepIndex = navigationSteps.lastIndex
            bestDistance = RouteMath.calculateDistanceToManeuver(currentLocation, navigationSteps[bestStepIndex])
        }

        return NavigationStepSelection(
            stepIndex = bestStepIndex,
            distanceToManeuver = bestDistance
        )
    }
}
