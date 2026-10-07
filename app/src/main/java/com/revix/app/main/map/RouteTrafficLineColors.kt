package com.revix.app.main.map

import android.graphics.Color
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources

/**
 * Route line colors with traffic congestion.
 * Free/unknown stays brand orange; busier segments shift yellow → red → dark red.
 */
object RouteTrafficLineColors {
    private val free = Color.parseColor("#FF6020")
    private val casing = Color.parseColor("#CC4D1A")
    private val moderate = Color.parseColor("#FFCC33")
    private val heavy = Color.parseColor("#FF3B30")
    private val severe = Color.parseColor("#9B1B1B")

    // Slightly muted for alternatives so primary route stays dominant.
    private val altFree = Color.parseColor("#CC7A4D")
    private val altModerate = Color.parseColor("#D4B04A")
    private val altHeavy = Color.parseColor("#CC4A42")
    private val altSevere = Color.parseColor("#7A2828")

    fun colorResources(): RouteLineColorResources {
        return RouteLineColorResources.Builder()
            .routeDefaultColor(free)
            .routeCasingColor(casing)
            .routeUnknownCongestionColor(free)
            .routeLowCongestionColor(free)
            .routeModerateCongestionColor(moderate)
            .routeHeavyCongestionColor(heavy)
            .routeSevereCongestionColor(severe)
            .alternativeRouteDefaultColor(altFree)
            .alternativeRouteCasingColor(casing)
            .alternativeRouteUnknownCongestionColor(altFree)
            .alternativeRouteLowCongestionColor(altFree)
            .alternativeRouteModerateCongestionColor(altModerate)
            .alternativeRouteHeavyCongestionColor(altHeavy)
            .alternativeRouteSevereCongestionColor(altSevere)
            .build()
    }
}
