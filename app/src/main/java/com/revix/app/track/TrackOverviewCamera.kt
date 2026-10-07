package com.revix.app.track

import com.mapbox.geojson.Point as MapboxPoint
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapboxMap

object TrackOverviewCamera {
    private const val CIRCUIT_ZOOM_BOOST = 0.65
    private const val POINT_TO_POINT_ZOOM_ADJUST = 0.28

    fun apply(
        mapboxMap: MapboxMap,
        points: List<MapboxPoint>,
        density: Float,
        isPointToPoint: Boolean,
        fallbackCenter: MapboxPoint,
        fallbackZoom: Double
    ): Pair<MapboxPoint, Double> {
        val padding = EdgeInsets(
            14.0 * density,
            14.0 * density,
            56.0 * density,
            14.0 * density
        )
        val zoomAdjust = if (isPointToPoint) {
            POINT_TO_POINT_ZOOM_ADJUST
        } else {
            CIRCUIT_ZOOM_BOOST
        }
        val fitted = try {
            if (points.size >= 2) {
                mapboxMap.cameraForCoordinates(
                    points,
                    CameraOptions.Builder().build(),
                    padding,
                    null,
                    null
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
        val center = fitted?.center ?: fallbackCenter
        val zoom = (fitted?.zoom ?: fallbackZoom) + zoomAdjust
        mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(center)
                .zoom(zoom)
                .pitch(0.0)
                .bearing(fitted?.bearing ?: 0.0)
                .build()
        )
        return center to zoom
    }
}
