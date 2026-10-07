package com.example.clinometer.track

import android.graphics.Color
import com.example.clinometer.GeoPoint
import com.example.clinometer.RoutePoint
import com.mapbox.geojson.Point as MapboxPoint
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import java.util.Locale
import kotlin.math.abs

object TrackLapMapLogic {

    fun getSegmentColorHex(routePoints: List<RoutePoint>, segmentIndex: Int): String? {
        if (routePoints.size < 2) return null
        if (segmentIndex !in 0 until routePoints.size - 1) return null

        val startPoint = routePoints[segmentIndex]
        val endPoint = routePoints[segmentIndex + 1]
        return toHexColor(getSegmentInputColor(startPoint, endPoint))
    }

    fun buildFullSegmentOptions(routePoints: List<RoutePoint>): List<PolylineAnnotationOptions> {
        if (routePoints.size < 2) return emptyList()

        val segmentOptions = mutableListOf<PolylineAnnotationOptions>()
        for (i in 0 until routePoints.size - 1) {
            val startPoint = routePoints[i]
            val endPoint = routePoints[i + 1]
            val colorHex = toHexColor(getSegmentInputColor(startPoint, endPoint))

            segmentOptions.add(
                PolylineAnnotationOptions()
                    .withPoints(
                        listOf(
                            MapboxPoint.fromLngLat(startPoint.geoPoint.longitude, startPoint.geoPoint.latitude),
                            MapboxPoint.fromLngLat(endPoint.geoPoint.longitude, endPoint.geoPoint.latitude)
                        )
                    )
                    .withLineColor(colorHex)
                    .withLineWidth(6.5)
            )
        }

        return segmentOptions
    }

    fun buildPartialSegmentOptions(
        routePoints: List<RoutePoint>,
        index: Int,
        interpolatedPoint: GeoPoint
    ): List<PolylineAnnotationOptions> {
        if (routePoints.size < 2) return emptyList()

        val cappedIndex = index.coerceIn(0, routePoints.size - 2)
        val segmentOptions = mutableListOf<PolylineAnnotationOptions>()

        for (i in 0 until cappedIndex) {
            val startPoint = routePoints[i]
            val endPoint = routePoints[i + 1]
            val colorHex = toHexColor(getSegmentInputColor(startPoint, endPoint))

            segmentOptions.add(
                PolylineAnnotationOptions()
                    .withPoints(
                        listOf(
                            MapboxPoint.fromLngLat(startPoint.geoPoint.longitude, startPoint.geoPoint.latitude),
                            MapboxPoint.fromLngLat(endPoint.geoPoint.longitude, endPoint.geoPoint.latitude)
                        )
                    )
                    .withLineColor(colorHex)
                    .withLineWidth(6.5)
            )
        }

        val partialStart = routePoints[cappedIndex]
        val partialEnd = routePoints[cappedIndex + 1]
        val partialColorHex = toHexColor(getSegmentInputColor(partialStart, partialEnd))
        segmentOptions.add(
            PolylineAnnotationOptions()
                .withPoints(
                    listOf(
                        MapboxPoint.fromLngLat(partialStart.geoPoint.longitude, partialStart.geoPoint.latitude),
                        MapboxPoint.fromLngLat(interpolatedPoint.longitude, interpolatedPoint.latitude)
                    )
                )
                .withLineColor(partialColorHex)
                .withLineWidth(6.5)
        )

        return segmentOptions
    }

    private fun getSegmentInputColor(startPoint: RoutePoint, endPoint: RoutePoint): Int {
        val dtSec = ((endPoint.timestamp - startPoint.timestamp).coerceAtLeast(1L)) / 1000f
        if (dtSec <= 0f) return Color.rgb(0, 200, 80)

        val startSpeedMs = startPoint.speed / 3.6f
        val endSpeedMs = endPoint.speed / 3.6f
        val longitudinalMs2 = (endSpeedMs - startSpeedMs) / dtSec
        val intensity = (abs(longitudinalMs2) / 3.0f).coerceIn(0f, 1f)

        return if (longitudinalMs2 >= 0f) {
            val r = (50f * (1f - intensity)).toInt().coerceIn(0, 255)
            val g = (190f + 65f * intensity).toInt().coerceIn(0, 255)
            val b = (70f * (1f - intensity)).toInt().coerceIn(0, 255)
            Color.rgb(r, g, b)
        } else {
            val r = (200f + 55f * intensity).toInt().coerceIn(0, 255)
            val g = (70f * (1f - intensity)).toInt().coerceIn(0, 255)
            val b = (70f * (1f - intensity)).toInt().coerceIn(0, 255)
            Color.rgb(r, g, b)
        }
    }

    private fun toHexColor(colorInt: Int): String {
        return String.format(Locale.US, "#%06X", 0xFFFFFF and colorInt)
    }
}
