package com.revix.app.track

import android.graphics.Color
import com.revix.app.GeoPoint
import com.revix.app.RoutePoint
import com.mapbox.geojson.Point as MapboxPoint
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions

class TrackLapMapboxRouteRenderer {
    private var segmentAnnotations = mutableListOf<PolylineAnnotation>()
    private var partialAnnotation: PolylineAnnotation? = null
    private var currentDrawingIndex = 0
    private var longitudinalGByRouteIndex: FloatArray? = null
    private var cachedSegmentColorHex: Array<String?>? = null

    fun clear() {
        segmentAnnotations.clear()
        partialAnnotation = null
        currentDrawingIndex = 0
        longitudinalGByRouteIndex = null
        cachedSegmentColorHex = null
    }

    fun showFullRoute(
        polyManager: PolylineAnnotationManager?,
        routePoints: List<RoutePoint>,
        longitudinalGByRouteIndex: FloatArray? = null
    ) {
        this.longitudinalGByRouteIndex = longitudinalGByRouteIndex
        // Force rebuild when G series identity changes between calls.
        if (segmentAnnotations.isNotEmpty()) {
            segmentAnnotations.clear()
            partialAnnotation = null
            cachedSegmentColorHex = null
        }
        ensureInitialized(polyManager, routePoints)

        if (segmentAnnotations.isNotEmpty() && polyManager != null) {
            segmentAnnotations.forEachIndexed { index, annotation ->
                annotation.lineWidth = 6.5
                cachedSegmentColorHex?.getOrNull(index)?.let { hex ->
                    annotation.lineColorInt = Color.parseColor(hex)
                }
            }
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
                val segmentColor = cachedSegmentColorHex?.getOrNull(targetFullCount)
                    ?: TrackLapMapLogic.getSegmentColorHex(
                        routePoints,
                        targetFullCount,
                        longitudinalGByRouteIndex
                    )

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
        cachedSegmentColorHex = null

        val segmentOptions = TrackLapMapLogic.buildFullSegmentOptions(routePoints, longitudinalGByRouteIndex)
        if (segmentOptions.isNotEmpty()) {
            val created = try {
                manager.create(segmentOptions)
            } catch (_: Exception) {
                emptyList()
            }
            segmentAnnotations = created.toMutableList()
            cachedSegmentColorHex = Array(expectedCount) { index ->
                TrackLapMapLogic.getSegmentColorHex(routePoints, index, longitudinalGByRouteIndex)
            }
            segmentAnnotations.forEachIndexed { index, annotation ->
                cachedSegmentColorHex?.getOrNull(index)?.let { hex ->
                    annotation.lineColorInt = Color.parseColor(hex)
                }
                annotation.lineWidth = 6.5
            }
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
                .withLineColor("#28AF5F")
                .withLineWidth(0.0)
        )

        currentDrawingIndex = 0
    }
}
