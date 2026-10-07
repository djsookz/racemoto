package com.example.clinometer.main.map

import android.content.Context
import android.graphics.Color
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.formatter.UnitType
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources

data class NavigationVisualComponents(
    val maneuverApi: MapboxManeuverApi,
    val routeLineApi: MapboxRouteLineApi,
    val routeLineView: MapboxRouteLineView,
    val routeArrowView: MapboxRouteArrowView
)

object SdkStyleSetup {

    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    fun createNavigationVisualComponents(
        context: Context,
        style: Style,
        createRouteArrowView: (Boolean) -> MapboxRouteArrowView
    ): NavigationVisualComponents {
        val distanceFormatterOptions = DistanceFormatterOptions.Builder(context)
            .unitType(UnitType.METRIC)
            .build()
        val distanceFormatter = MapboxDistanceFormatter(distanceFormatterOptions)
        val maneuverApi = MapboxManeuverApi(distanceFormatter)

        val orangeColor = Color.parseColor("#FF6020")
        val darkerOrange = Color.parseColor("#CC4D1A")
        val routeLineColorResources = RouteLineColorResources.Builder()
            .routeDefaultColor(orangeColor)
            .routeCasingColor(darkerOrange)
            .routeUnknownCongestionColor(orangeColor)
            .routeLowCongestionColor(orangeColor)
            .routeModerateCongestionColor(orangeColor)
            .routeHeavyCongestionColor(orangeColor)
            .routeSevereCongestionColor(orangeColor)
            .build()

        val routeLineViewOptions = MapboxRouteLineViewOptions.Builder(context)
            .routeLineBelowLayerId("road-label")
            .routeLineColorResources(routeLineColorResources)
            .displaySoftGradientForTraffic(false)
            .build()
        val routeLineView = MapboxRouteLineView(routeLineViewOptions)
        val routeLineApi = MapboxRouteLineApi(
            MapboxRouteLineApiOptions.Builder()
                .vanishingRouteLineEnabled(true)
                .isRouteCalloutsEnabled(false)
                .build()
        )
        val routeArrowView = createRouteArrowView(false)

        try {
            routeLineView.initializeLayers(style)
        } catch (_: Throwable) {
        }

        return NavigationVisualComponents(
            maneuverApi = maneuverApi,
            routeLineApi = routeLineApi,
            routeLineView = routeLineView,
            routeArrowView = routeArrowView
        )
    }

    fun configureLocationPuck(
        mapView: MapView,
        navigationLocationProvider: NavigationLocationProvider,
        density: Float
    ) {
        val orangeColor = Color.parseColor("#FF6020")
        mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            updateSettings {
                enabled = true
                pulsingEnabled = true
                pulsingColor = orangeColor
                puckBearingEnabled = true
                locationPuck = LocationPuck2D(
                    topImage = ImageHolder.from(NavigationMarkerImageFactory.createOrangeTopImage(density)),
                    bearingImage = ImageHolder.from(NavigationMarkerImageFactory.createOrangeBearingImage(density)),
                    shadowImage = ImageHolder.from(NavigationMarkerImageFactory.createOrangeShadowImage(density))
                )
            }
        }
    }

    fun installVanishingRouteListener(
        mapView: MapView,
        existingListener: OnIndicatorPositionChangedListener?,
        routeLineApi: MapboxRouteLineApi?,
        routeLineView: MapboxRouteLineView?,
        hasActiveRoutes: () -> Boolean
    ): OnIndicatorPositionChangedListener? {
        existingListener?.let { listener ->
            try {
                mapView.location.removeOnIndicatorPositionChangedListener(listener)
            } catch (_: Throwable) {
            }
        }

        val rla = routeLineApi ?: return null
        val rlv = routeLineView ?: return null

        val listener = OnIndicatorPositionChangedListener { point ->
            val style = mapView.mapboxMap.style ?: return@OnIndicatorPositionChangedListener
            if (!hasActiveRoutes()) return@OnIndicatorPositionChangedListener

            val update = rla.updateTraveledRouteLine(point)
            rlv.renderRouteLineUpdate(style, update)
        }

        mapView.location.addOnIndicatorPositionChangedListener(listener)
        return listener
    }
}
