package com.revix.app.averagespeed

/**
 * Process-wide average-speed visit. Survives map rotation, leaving the map page,
 * and starting / stopping navigation without treating it as a new section entry.
 */
internal object AverageSpeedRuntime {
    val tracker = AverageSpeedTracker()

    @Volatile
    var lastState: AverageSpeedUiState = AverageSpeedUiState()
        private set

    fun publish(state: AverageSpeedUiState) {
        lastState = state.copy(voice = null)
    }
}
