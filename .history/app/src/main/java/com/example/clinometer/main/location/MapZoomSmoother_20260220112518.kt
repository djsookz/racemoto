package com.example.clinometer.main.location

import kotlin.math.abs

object MapZoomSmoother {
    fun compute(currentZoom: Float, targetZoom: Float): Float {
        val zoomDiff = targetZoom - currentZoom
        return if (abs(zoomDiff) > 0.01f) {
            currentZoom + zoomDiff * 0.08f
        } else {
            currentZoom
        }
    }
}
