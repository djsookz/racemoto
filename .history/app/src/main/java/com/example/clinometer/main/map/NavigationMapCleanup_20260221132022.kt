package com.example.clinometer.main.map

import com.mapbox.maps.Style

object NavigationMapCleanup {

    fun clearNavigationLayers(style: Style) {
        if (style.styleLayerExists("navigation-route-layer")) {
            style.removeStyleLayer("navigation-route-layer")
        }
        if (style.styleLayerExists("navigation-route-casing-layer")) {
            style.removeStyleLayer("navigation-route-casing-layer")
        }

        if (style.styleSourceExists("navigation-route-source")) {
            style.removeStyleSource("navigation-route-source")
        }

        if (style.styleLayerExists("navigation-destination-layer")) {
            style.removeStyleLayer("navigation-destination-layer")
        }
        if (style.styleSourceExists("navigation-destination-source")) {
            style.removeStyleSource("navigation-destination-source")
        }
    }
}
