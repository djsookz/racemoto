package com.example.clinometer.main.map

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import com.mapbox.maps.MapView

object MapViewContainerBinder {

    fun createMapView(
        activity: Activity,
        mapContainerId: Int,
        legacyMapViewId: Int
    ): MapView {
        val mapContainer = activity.findViewById<FrameLayout>(mapContainerId)
        val legacyMapView = mapContainer.findViewById<View>(legacyMapViewId)

        if (legacyMapView != null) {
            mapContainer.removeView(legacyMapView)
        }

        val mapView = MapView(activity)
        mapContainer.addView(mapView)
        return mapView
    }
}
