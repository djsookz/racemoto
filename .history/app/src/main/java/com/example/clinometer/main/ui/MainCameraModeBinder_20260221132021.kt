package com.example.clinometer.main.ui

import com.example.clinometer.R
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import android.widget.ImageButton

object MainCameraModeBinder {

    fun bindToggle(
        button: ImageButton?,
        isNavigationActive: Boolean,
        hasNavigationCamera: Boolean,
        onToggle: () -> Unit
    ) {
        button?.setOnClickListener {
            if (isNavigationActive && hasNavigationCamera) return@setOnClickListener
            onToggle()
        }
    }

    fun updateIcon(button: ImageButton?, isNorthUpMode: Boolean) {
        if (isNorthUpMode) {
            button?.setImageResource(R.drawable.ic_map_compass)
            button?.imageAlpha = 255
        } else {
            button?.setImageResource(R.drawable.ic_map_heading)
            button?.imageAlpha = (255 * 0.85f).toInt()
        }
    }

    fun applyCameraMode(mapView: MapView?, isNorthUpMode: Boolean, targetMapOrientation: Float, pitch: Double) {
        val currentCenter = mapView?.mapboxMap?.cameraState?.center
        val currentZoom = mapView?.mapboxMap?.cameraState?.zoom ?: return
        val bearing = if (isNorthUpMode) 0.0 else (-targetMapOrientation).toDouble()

        mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(currentCenter)
                .zoom(currentZoom)
                .bearing(bearing)
                .pitch(pitch)
                .build()
        )
    }
}
