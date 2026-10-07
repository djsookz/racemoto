package com.example.clinometer.main.map

import android.content.Intent
import android.location.Location
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView

object MapInitialCameraApplier {

    fun apply(
        intent: Intent,
        mapView: MapView,
        isNavigationActive: Boolean,
        navigationOriginLat: Double,
        navigationOriginLon: Double,
        navigationOriginBearing: Float,
        navigationDestination: Point?,
        navigationRoutePoints: List<Point>,
        lastLocation: Location?,
        calculateBearingBetweenPoints: (Double, Double, Double, Double) -> Double,
        cameraPitchProvider: () -> Double
    ) {
        val previewCamLat = intent.getDoubleExtra("nav_camera_center_lat", Double.NaN)
        val previewCamLon = intent.getDoubleExtra("nav_camera_center_lon", Double.NaN)
        val previewCamZoom = intent.getDoubleExtra("nav_camera_zoom", Double.NaN)
        val previewCamBearing = intent.getDoubleExtra("nav_camera_bearing", Double.NaN)
        val previewCamPitch = intent.getDoubleExtra("nav_camera_pitch", Double.NaN)
        val hasPreviewCamera =
            !previewCamLat.isNaN() && !previewCamLon.isNaN() &&
                !previewCamZoom.isNaN() && !previewCamBearing.isNaN() && !previewCamPitch.isNaN()

        val initialCenter = when {
            isNavigationActive && navigationOriginLat != 0.0 && navigationOriginLon != 0.0 -> {
                Point.fromLngLat(navigationOriginLon, navigationOriginLat)
            }
            isNavigationActive && navigationRoutePoints.isNotEmpty() -> {
                val firstPoint = navigationRoutePoints.first()
                Point.fromLngLat(firstPoint.longitude(), firstPoint.latitude())
            }
            else -> {
                if (lastLocation != null) {
                    Point.fromLngLat(lastLocation.longitude, lastLocation.latitude)
                } else {
                    null
                }
            }
        }

        if (hasPreviewCamera) {
            mapView.mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(previewCamLon, previewCamLat))
                    .zoom(previewCamZoom)
                    .bearing(previewCamBearing)
                    .pitch(previewCamPitch)
                    .build()
            )
            return
        }

        if (initialCenter == null) return

        val bearing = if (isNavigationActive && navigationOriginBearing != 0f) {
            navigationOriginBearing.toDouble()
        } else if (isNavigationActive) {
            navigationDestination?.let { dest ->
                calculateBearingBetweenPoints(
                    navigationOriginLat,
                    navigationOriginLon,
                    dest.latitude(),
                    dest.longitude()
                )
            } ?: 0.0
        } else {
            0.0
        }

        mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(initialCenter)
                .zoom(if (isNavigationActive) 19.0 else 17.5)
                .pitch(cameraPitchProvider())
                .bearing(bearing)
                .build()
        )
    }
}
