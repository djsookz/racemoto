package com.example.clinometer.main.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiStateViewModelTest {

    @Test
    fun navigationState_updatesCurrentPage() {
        val viewModel = MainUiStateViewModel()

        viewModel.setCurrentPage(3)

        assertEquals(3, viewModel.uiState.value.navigation.currentPage)
    }

    @Test
    fun tripProgressState_updatesValues() {
        val viewModel = MainUiStateViewModel()

        viewModel.updateTripProgress(
            remainingDistanceMeters = 1540.0,
            remainingTimeSeconds = 320,
            etaText = "12:40"
        )

        val state = viewModel.uiState.value.tripProgress
        assertEquals(1540.0, state.remainingDistanceMeters, 0.0)
        assertEquals(320, state.remainingTimeSeconds)
        assertEquals("12:40", state.etaText)
    }

    @Test
    fun routePreviewState_updatesValues() {
        val viewModel = MainUiStateViewModel()

        viewModel.setRoutePreview(
            hasRoute = true,
            destinationName = "Center",
            routeDistanceText = "3.2 km",
            routeDurationText = "8 min"
        )

        val state = viewModel.uiState.value.routePreview
        assertTrue(state.hasRoute)
        assertEquals("Center", state.destinationName)
        assertEquals("3.2 km", state.routeDistanceText)
        assertEquals("8 min", state.routeDurationText)

        viewModel.setRoutePreview(false, null, "", "")
        assertFalse(viewModel.uiState.value.routePreview.hasRoute)
    }
}
