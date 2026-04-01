package com.example.clinometer.main.map

import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.layers.addLayer

object NavigationRouteFallbackRenderer {

    fun render(style: Style, navigationRouteGeometry: LineString) {
        val feature = Feature.fromGeometry(navigationRouteGeometry)
        val featureCollection = FeatureCollection.fromFeatures(listOf(feature))

        if (style.styleLayerExists("navigation-route-layer")) {
            style.removeStyleLayer("navigation-route-layer")
        }
        if (style.styleLayerExists("navigation-route-casing-layer")) {
            style.removeStyleLayer("navigation-route-casing-layer")
        }

        if (style.styleSourceExists("navigation-route-source")) {
            style.removeStyleSource("navigation-route-source")
        }

        style.addSource(
            geoJsonSource("navigation-route-source") {
                featureCollection(featureCollection)
            }
        )

        style.addLayer(
            lineLayer("navigation-route-casing-layer", "navigation-route-source") {
                lineColor("#CC4D1A")
                lineWidth(12.0)
                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)
                slot("middle")
            }
        )

        style.addLayer(
            lineLayer("navigation-route-layer", "navigation-route-source") {
                lineColor("#FF6020")
                lineWidth(8.0)
                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)
                slot("middle")
            }
        )
    }
}
