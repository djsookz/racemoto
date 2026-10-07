package com.example.clinometer.main.location

import kotlin.math.abs

object MapZoomSmoother {
    fun compute(currentZoom: Double, targetZoom: Double): Double {
        val zoomDiff = targetZoom - currentZoom
        return if (abs(zoomDiff) > 0.01) {
            currentZoom + zoomDiff * 0.08
        } else {
            currentZoom
        }
    }
}
