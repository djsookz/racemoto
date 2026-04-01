package com.example.clinometer.main.map

import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource

object NavigationDestinationLayerBinder {

    fun bindIfNeeded(style: Style, destination: Point) {
        if (style.styleSourceExists("navigation-destination-source")) {
            return
        }

        val destinationFeature = Feature.fromGeometry(destination)
        val destinationCollection = FeatureCollection.fromFeatures(listOf(destinationFeature))

        style.addSource(
            geoJsonSource("navigation-destination-source") {
                featureCollection(destinationCollection)
            }
        )

        style.addLayer(
            symbolLayer("navigation-destination-layer", "navigation-destination-source") {
                iconImage("marker-icon")
                iconSize(1.5)
                iconAnchor(IconAnchor.BOTTOM)
            }
        )
    }
}
