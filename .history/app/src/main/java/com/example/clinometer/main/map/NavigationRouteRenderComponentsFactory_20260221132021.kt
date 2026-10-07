package com.example.clinometer.main.map

import android.content.Context
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions

data class NavigationRouteRenderComponents(
    val routeLineApi: MapboxRouteLineApi,
    val routeLineView: MapboxRouteLineView,
    val routeArrowView: MapboxRouteArrowView
)

object NavigationRouteRenderComponentsFactory {

    fun ensure(
        context: Context,
        routeLineApi: MapboxRouteLineApi?,
        routeLineView: MapboxRouteLineView?,
        routeArrowView: MapboxRouteArrowView?,
        createRouteArrowView: (Boolean) -> MapboxRouteArrowView
    ): NavigationRouteRenderComponents {
        val ensuredRouteLineApi = routeLineApi ?: MapboxRouteLineApi(
            MapboxRouteLineApiOptions.Builder().build()
        )

        val ensuredRouteLineView = routeLineView ?: MapboxRouteLineView(
            MapboxRouteLineViewOptions.Builder(context)
                .routeLineBelowLayerId("road-label")
                .build()
        )

        val ensuredRouteArrowView = routeArrowView ?: createRouteArrowView(true)

        return NavigationRouteRenderComponents(
            routeLineApi = ensuredRouteLineApi,
            routeLineView = ensuredRouteLineView,
            routeArrowView = ensuredRouteArrowView
        )
    }
}
