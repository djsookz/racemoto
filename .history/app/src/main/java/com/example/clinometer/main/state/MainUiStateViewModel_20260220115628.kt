package com.example.clinometer.main.state

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MainNavigationUiState(
    val currentPage: Int = 0
)

data class TripProgressUiState(
    val remainingDistanceMeters: Double = 0.0,
    val remainingTimeSeconds: Long = 0L,
    val etaText: String = ""
)

data class RoutePreviewUiState(
    val hasRoute: Boolean = false,
    val destinationName: String? = null,
    val routeDistanceText: String = "",
    val routeDurationText: String = ""
)

data class MainUiState(
    val navigation: MainNavigationUiState = MainNavigationUiState(),
    val tripProgress: TripProgressUiState = TripProgressUiState(),
    val routePreview: RoutePreviewUiState = RoutePreviewUiState()
)

class MainUiStateViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun setCurrentPage(page: Int) {
        _uiState.value = _uiState.value.copy(
            navigation = _uiState.value.navigation.copy(currentPage = page)
        )
    }

    fun updateTripProgress(remainingDistanceMeters: Double, remainingTimeSeconds: Long, etaText: String) {
        _uiState.value = _uiState.value.copy(
            tripProgress = TripProgressUiState(
                remainingDistanceMeters = remainingDistanceMeters,
                remainingTimeSeconds = remainingTimeSeconds,
                etaText = etaText
            )
        )
    }

    fun setRoutePreview(hasRoute: Boolean, destinationName: String?, routeDistanceText: String, routeDurationText: String) {
        _uiState.value = _uiState.value.copy(
            routePreview = RoutePreviewUiState(
                hasRoute = hasRoute,
                destinationName = destinationName,
                routeDistanceText = routeDistanceText,
                routeDurationText = routeDurationText
            )
        )
    }
}
