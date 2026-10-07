package com.example.clinometer.main.map

import android.view.View
import com.mapbox.geojson.LineString
import com.mapbox.maps.MapView
import com.mapbox.maps.Style

object MapStyleReadyHandler {

    fun handle(
        style: Style,
        mapView: MapView?,
        isNavigationActive: Boolean,
        navigationRouteGeometry: LineString?,
        setupSdkNavigationOnStyle: (Style) -> Unit,
        updateUiForProfile: () -> Unit,
        setupNavigationRouteFallback: (Style) -> Unit,
        requestInitialSdkRouteIfPossible: () -> Unit
    ) {
        setupSdkNavigationOnStyle(style)
        mapView?.visibility = View.VISIBLE

        if (!isNavigationActive) {
            return
        }

        updateUiForProfile()

        if (navigationRouteGeometry != null) {
            try {
                setupNavigationRouteFallback(style)
            } catch (_: Throwable) {
            }
        }

        requestInitialSdkRouteIfPossible()
    }
}
