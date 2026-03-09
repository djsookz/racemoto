package com.example.clinometer.main.location

import kotlin.math.abs

object MapOrientationSmoother {
    fun computeNextOrientation(
        currentOrientation: Float,
        targetOrientation: Float,
        speed: Float
    ): Float {
        var diff = targetOrientation - currentOrientation
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f

        val smoothingFactor = when {
            abs(diff) > 90f -> 0.15f
            abs(diff) > 45f -> 0.12f
            abs(diff) > 20f -> 0.08f
            speed > 50f -> 0.06f
            speed > 20f -> 0.05f
            else -> 0.04f
        }

        if (abs(diff) <= 0.5f) {
            return currentOrientation
        }

        var newOrientation = currentOrientation + diff * smoothingFactor
        while (newOrientation > 360f) newOrientation -= 360f
        while (newOrientation < 0f) newOrientation += 360f
        return newOrientation
    }
}
