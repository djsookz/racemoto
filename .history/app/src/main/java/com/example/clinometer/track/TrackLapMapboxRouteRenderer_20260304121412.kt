package com.example.clinometer.track

import android.graphics.Color
import com.example.clinometer.GeoPoint
import com.example.clinometer.RoutePoint
import com.mapbox.geojson.Point as MapboxPoint
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions

class TrackLapMapboxRouteRenderer {
    private var segmentAnnotations = mutableListOf<PolylineAnnotation>()
    private var partialAnnotation: PolylineAnnotation? = null
    private var currentDrawingIndex = 0

    fun clear() {
        segmentAnnotations.clear()
        partialAnnotation = null
        currentDrawingIndex = 0
    }

    fun showFullRoute(polyManager: PolylineAnnotationManager?, routePoints: List<RoutePoint>) {
        ensureInitialized(polyManager, routePoints)

        if (segmentAnnotations.isNotEmpty() && polyManager != null) {
            segmentAnnotations.forEach { it.lineWidth = 6.5 }
            polyManager.update(segmentAnnotations)
        }

        partialAnnotation?.let {
            it.lineWidth = 0.0
            polyManager?.update(it)
        }

        currentDrawingIndex = segmentAnnotations.size
    }

    fun drawUpToIndex(
        polyManager: PolylineAnnotationManager?,
        routePoints: List<RoutePoint>,
        index: Int,
        interpolatedPoint: GeoPoint
    ) {
        ensureInitialized(polyManager, routePoints)

        val manager = polyManager ?: return
        val segmentCount = segmentAnnotations.size
        if (segmentCount == 0) return

        val targetFullCount = index.coerceIn(0, segmentCount)
        if (targetFullCount != currentDrawingIndex) {
            val changed = mutableListOf<PolylineAnnotation>()

            if (targetFullCount > currentDrawingIndex) {
                for (i in currentDrawingIndex until targetFullCount) {
                    segmentAnnotations.getOrNull(i)?.let {
                        it.lineWidth = 6.5
                        changed.add(it)
                    }
                }
            } else {
                for (i in targetFullCount until currentDrawingIndex) {
                    segmentAnnotations.getOrNull(i)?.let {
                        it.lineWidth = 0.0
                        changed.add(it)
                    }
                }
            }

            if (changed.isNotEmpty()) {
                manager.update(changed)
            }
            currentDrawingIndex = targetFullCount
        }

        partialAnnotation?.let { partial ->
            if (targetFullCount >= segmentCount) {
                if ((partial.lineWidth ?: 0.0) != 0.0) {
                    partial.lineWidth = 0.0
                    manager.update(partial)
                }
            } else {
                val start = routePoints[targetFullCount].geoPoint
                val segmentColor = TrackLapMapLogic.getSegmentColorHex(routePoints, targetFullCount)

                partial.points = listOf(
                    MapboxPoint.fromLngLat(start.longitude, start.latitude),
                    MapboxPoint.fromLngLat(interpolatedPoint.longitude, interpolatedPoint.latitude)
                )
                if (!segmentColor.isNullOrBlank()) {
                    partial.lineColorInt = Color.parseColor(segmentColor)
                }
                partial.lineWidth = 6.5
                manager.update(partial)
            }
        }
    }

    private fun ensureInitialized(polyManager: PolylineAnnotationManager?, routePoints: List<RoutePoint>) {
        val manager = polyManager ?: return

        if (routePoints.size < 2) {
            clear()
            return
        }

        val expectedCount = routePoints.size - 1
        val ready = segmentAnnotations.size == expectedCount && partialAnnotation != null
        if (ready) return

        manager.deleteAll()
        segmentAnnotations.clear()
        partialAnnotation = null

        val segmentOptions = TrackLapMapLogic.buildFullSegmentOptions(routePoints)
        if (segmentOptions.isNotEmpty()) {
            val created = manager.create(segmentOptions)
            segmentAnnotations = created.toMutableList()
            segmentAnnotations.forEach { it.lineWidth = 0.0 }
            manager.update(segmentAnnotations)
        }

        val firstPoint = routePoints.first().geoPoint
        partialAnnotation = manager.create(
            PolylineAnnotationOptions()
                .withPoints(
                    listOf(
                        MapboxPoint.fromLngLat(firstPoint.longitude, firstPoint.latitude),
                        MapboxPoint.fromLngLat(firstPoint.longitude, firstPoint.latitude)
                    )
                )
                .withLineColor("#00C850")
                .withLineWidth(0.0)
        )

        currentDrawingIndex = 0
    }
}
