package com.example.clinometer

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.animation.ObjectAnimator
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.clinometer.navigation.GeocodingFeature
import com.example.clinometer.navigation.MapboxGeocodingService
import com.example.clinometer.navigation.MapboxDirectionsService
import com.example.clinometer.navigation.DirectionsResponse
import com.google.gson.GsonBuilder
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.api.directions.v5.models.DirectionsResponse as MapboxDirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute as MapboxDirectionsRoute
import com.mapbox.common.location.Location
import com.mapbox.geojson.Point
import com.mapbox.geojson.Point as MapboxPoint
import com.mapbox.geojson.LineString
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.scalebar.scalebar

import com.mapbox.maps.ImageHolder
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.TimeFormat
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.formatter.UnitType
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.base.trip.model.RouteLegProgress
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.tripdata.progress.api.MapboxTripProgressApi
import com.mapbox.navigation.tripdata.progress.model.*
import com.mapbox.navigation.tripdata.shield.model.RouteShieldCallback
import com.mapbox.navigation.ui.maps.NavigationStyles
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources
import com.example.clinometer.databinding.ActivityTestNavigationBinding
import com.mapbox.maps.plugin.LocationPuck2D

/**
 * Custom Navigation Activity.
 * Pure white text, dark backgrounds, and correct data formatting using stable RouteProgress fields.
 */
@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
class TestNavigationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestNavigationBinding
    private val CUSTOM_STYLE_URI = "mapbox://styles/djsookz/cmiyer8iu000101s84wqsav5l"

    private lateinit var maneuverApi: MapboxManeuverApi

    // Motorway options
    private var allowMotorways: Boolean = false

    private val mapboxNavigation: MapboxNavigation by requireMapboxNavigation(
        onResumedObserver = object : MapboxNavigationObserver {
            @SuppressLint("MissingPermission")
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.registerRoutesObserver(routesObserver)
                mapboxNavigation.registerLocationObserver(locationObserver)
                mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
                mapboxNavigation.registerArrivalObserver(arrivalObserver)
                mapboxNavigation.startTripSession()
            }

            override fun onDetached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.unregisterRoutesObserver(routesObserver)
                mapboxNavigation.unregisterLocationObserver(locationObserver)
                mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
                mapboxNavigation.unregisterArrivalObserver(arrivalObserver)
            }
        },
        onInitialize = this::initNavigation
    )

    private lateinit var customTripProgressView: CustomTripProgressView
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private val navigationLocationProvider = NavigationLocationProvider()

    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView
    private val routeArrowApi = MapboxRouteArrowApi()
    private lateinit var routeArrowView: MapboxRouteArrowView

    private lateinit var geocodingService: MapboxGeocodingService
    private lateinit var directionsService: MapboxDirectionsService
    private lateinit var searchResultsAdapter: SearchResultsAdapter
    private var accessToken: String = ""
    private var isNavigationStarted = false
    private var currentDestination: Point? = null // Store current destination for route recalculation
    private var currentRoutes: List<NavigationRoute> = emptyList() // Store current routes for selection (original order)
    private var selectedRouteIndex: Int = 0 // Track which route is currently selected (index in currentRoutes)
    private var isRecalculatingRoute: Boolean = false // Flag to prevent UI changes during route recalculation
    private var onIndicatorPositionChangedListener: com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener? = null

    private val pixelDensity = Resources.getSystem().displayMetrics.density
    private val overviewPadding = EdgeInsets(140.0 * pixelDensity, 40.0 * pixelDensity, 120.0 * pixelDensity, 40.0 * pixelDensity)
    private val followingPadding = EdgeInsets(180.0 * pixelDensity, 40.0 * pixelDensity, 150.0 * pixelDensity, 40.0 * pixelDensity)

    private var hasInitializedCamera = false

    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: Location) {}

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation
            navigationLocationProvider.changePosition(enhancedLocation, locationMatcherResult.keyPoints)
            viewportDataSource.onLocationChanged(enhancedLocation)
            viewportDataSource.evaluate()

            // Center camera on first location
            if (!hasInitializedCamera) {
                hasInitializedCamera = true
                binding.mapView.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(MapboxPoint.fromLngLat(enhancedLocation.longitude, enhancedLocation.latitude))
                        .zoom(17.0)
                        .build()
                )
            }

            // Speed circle update - only show if navigation is started
            if (isNavigationStarted) {
                val speedKmh = (enhancedLocation.speed ?: 0.0) * 3.6
                binding.tvSpeed.text = speedKmh.toInt().toString()
            }
        }
    }

    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        // Only update navigation UI if navigation has started
        if (!isNavigationStarted) return@RouteProgressObserver

        viewportDataSource.onRouteProgressChanged(routeProgress)
        viewportDataSource.evaluate()

        // Update route line smoothly during navigation (like Google Maps)
        // This updates the route line to follow the puck without redrawing the entire route
        routeLineApi.updateWithRouteProgress(routeProgress) { value ->
            binding.mapView.mapboxMap.style?.let { routeLineView.renderRouteLineUpdate(it, value) }
        }

        binding.mapView.mapboxMap.style?.let {
            val arrowResult = routeArrowApi.addUpcomingManeuverArrow(routeProgress)
            routeArrowView.renderManeuverUpdate(it, arrowResult)
        }

        // Update UI panels
        updateManeuverUI(routeProgress)
        updateProgressUI(routeProgress)
    }

    private val routesObserver = RoutesObserver { result ->
        if (result.navigationRoutes.isNotEmpty()) {
            val currentRoute = result.navigationRoutes.first()

                    if (!isNavigationStarted) {
                        // ПРЕДИ НАВИГАЦИЯ: Показваме всички маршрути
                        // Only update currentRoutes if it's a new route calculation (empty or different route IDs)
                        // Don't update if we just reordered routes (same routes, different order)
                        val isNewRouteCalculation = currentRoutes.isEmpty() ||
                            currentRoutes.size != result.navigationRoutes.size ||
                            currentRoutes.map { it.id }.toSet() != result.navigationRoutes.map { it.id }.toSet()

                        if (isNewRouteCalculation) {
                            currentRoutes = result.navigationRoutes
                            selectedRouteIndex = 0 // Reset to first route when new routes are calculated
                        }
                        // If it's just reordering, keep currentRoutes unchanged (preserve original order)

                        val alternativesMetadata = mapboxNavigation.getAlternativeMetadataFor(result.navigationRoutes)
                        routeLineApi.setNavigationRoutes(result.navigationRoutes, alternativesMetadata) { value ->
                            binding.mapView.mapboxMap.style?.let { routeLineView.renderRouteDrawData(it, value) }
                            
                            // Request camera overview AFTER route is rendered (inside callback)
                            // This ensures smooth animation without lag
                            if (!isRecalculatingRoute) {
                                // Use post to ensure rendering is complete
                                binding.mapView.post {
                                    navigationCamera.requestNavigationCameraToOverview()
                                }
                            }
                        }

                        // Update route info UI
                        updateRouteInfo(currentRoute)

                        // Only change visibility if NOT recalculating (to avoid flickering)
                        if (!isRecalculatingRoute) {
                            binding.routeInfoContainer.visibility = View.VISIBLE
                            binding.searchContainer.visibility = View.GONE
                            binding.btnStartNavigation.visibility = View.VISIBLE
                        }

                        // Reset flag after handling routes
                        isRecalculatingRoute = false
            } else {
                // ПО ВРЕМЕ НА НАВИГАЦИЯ: Винаги само основен маршрут
                val primaryRouteOnly = listOf(currentRoute)

                // Проверка за промяна на route ID (истински reroute)
                val routeIdChanged = currentRoute.id != currentRoutes.firstOrNull()?.id

                if (routeIdChanged) {
                    android.util.Log.d("TestNavigation", "Reroute detected (ID: ${currentRoute.id}) - redrawing route line")
                    routeLineApi.setNavigationRoutes(primaryRouteOnly, emptyList()) { value ->
                        binding.mapView.mapboxMap.style?.let { routeLineView.renderRouteDrawData(it, value) }
                    }
                    currentRoutes = primaryRouteOnly
                }
                // Не правим нищо при route refresh (без промяна на ID)
            }

            viewportDataSource.onRouteChanged(currentRoute)
            viewportDataSource.evaluate()

            if (isNavigationStarted) {
                binding.maneuverContainer.visibility = View.VISIBLE
                binding.tripProgressCard.visibility = View.VISIBLE
                binding.tvSpeed.visibility = View.VISIBLE
                binding.mapControlsContainer.visibility = View.VISIBLE
            }
        } else {
            // Маршрутът е изчистен
            binding.mapView.mapboxMap.style?.let {
                routeLineApi.clearRouteLine { value -> routeLineView.renderClearRouteLineValue(it, value) }
                routeArrowView.render(it, routeArrowApi.clearArrows())
                customTripProgressView.reset()
            }
            viewportDataSource.clearRouteData()
            viewportDataSource.evaluate()

            binding.maneuverContainer.visibility = View.GONE
            binding.tripProgressCard.visibility = View.GONE
            binding.btnStartNavigation.visibility = View.GONE
            binding.btnReset.visibility = View.GONE
            binding.btnStop.visibility = View.GONE
            binding.tvSpeed.visibility = View.GONE
            binding.mapControlsContainer.visibility = View.GONE
            binding.routeInfoContainer.visibility = View.GONE
            binding.searchContainer.visibility = View.VISIBLE
            isNavigationStarted = false
            currentRoutes = emptyList()
            selectedRouteIndex = 0
        }
    }
    
    private fun setupMapClickListener() {
        // Setup map click listener for route selection (outside of style callback)
        binding.mapView.mapboxMap.addOnMapClickListener { point ->
            android.util.Log.d("TestNavigation", "Map clicked at: ${point.latitude()}, ${point.longitude()}, isNavStarted: $isNavigationStarted, routes: ${currentRoutes.size}")
            
            if (!isNavigationStarted && currentRoutes.size > 1) {
                handleRouteClick(point)
                true // Return true to consume the event
            } else {
                false // Return false to allow other handlers
            }
        }
    }
    
    private fun handleRouteClick(clickPoint: Point) {
        if (currentRoutes.size <= 1) {
            android.util.Log.d("TestNavigation", "No alternative routes to select (${currentRoutes.size} routes)")
            return
        }
        
        android.util.Log.d("TestNavigation", "Handling route click at: ${clickPoint.latitude()}, ${clickPoint.longitude()}, routes: ${currentRoutes.size}")
        
        // Calculate distance from click to each route
        val routeDistances = mutableListOf<Pair<Int, Double>>()
        
        for (i in currentRoutes.indices) {
            val route = currentRoutes[i]
            
            // Calculate distance using coordinates from legs/steps/maneuvers
            var minDistanceForRoute = Double.MAX_VALUE
            try {
                val coordinates = mutableListOf<Point>()
                
                // Get coordinates from waypoints (origin and destination)
                val routeOptions = route.directionsRoute.routeOptions()
                val waypoints = routeOptions?.coordinatesList()
                if (waypoints != null) {
                    coordinates.addAll(waypoints)
                }
                
                // Try to decode polyline from geometry for full route coordinates
                try {
                    val geometry = route.directionsRoute.geometry()
                    if (geometry != null && geometry.isNotBlank()) {
                        try {
                            // Try to decode polyline (precision 5 is standard for Mapbox)
                            val lineString = LineString.fromPolyline(geometry, 5)
                            val polylineCoords = lineString.coordinates()
                            if (polylineCoords != null && polylineCoords.isNotEmpty()) {
                                // Add all coordinates from polyline (this gives us full route geometry)
                                coordinates.addAll(polylineCoords)
                                android.util.Log.d("TestNavigation", "Route $i: Decoded ${polylineCoords.size} coordinates from polyline")
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("TestNavigation", "Route $i: Failed to decode polyline: ${e.message}")
                            // Fall back to maneuvers if polyline decode fails
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("TestNavigation", "Route $i: Error getting geometry: ${e.message}")
                }
                
                // Also add coordinates from all maneuvers in legs/steps (as backup/supplement)
                val legs = route.directionsRoute.legs()
                if (legs != null) {
                    for (leg in legs) {
                        val steps = leg.steps()
                        if (steps != null) {
                            for (step in steps) {
                                val maneuver = step.maneuver()
                                if (maneuver != null) {
                                    val location = maneuver.location()
                                    if (location != null) {
                                        // Only add if not already present (avoid duplicates)
                                        if (!coordinates.any { it.latitude() == location.latitude() && it.longitude() == location.longitude() }) {
                                            coordinates.add(location)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (coordinates.isEmpty()) {
                    android.util.Log.w("TestNavigation", "Route $i has no coordinates")
                    continue
                }
                
                android.util.Log.d("TestNavigation", "Route $i has ${coordinates.size} coordinate points")
                
                // Calculate minimum distance to any point on the route
                for (coord in coordinates) {
                    val dx = clickPoint.longitude() - coord.longitude()
                    val dy = clickPoint.latitude() - coord.latitude()
                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                    
                    if (distance < minDistanceForRoute) {
                        minDistanceForRoute = distance
                    }
                }
                
                android.util.Log.d("TestNavigation", "Route $i min distance: $minDistanceForRoute degrees (${minDistanceForRoute * 111} km)")
            } catch (e: Exception) {
                android.util.Log.w("TestNavigation", "Failed to calculate route distance for route $i: ${e.message}", e)
                continue
            }
            
            if (minDistanceForRoute == Double.MAX_VALUE) {
                android.util.Log.w("TestNavigation", "Route $i distance calculation failed")
                continue
            }
            
            routeDistances.add(Pair(i, minDistanceForRoute))
        }
        
        if (routeDistances.isEmpty()) {
            android.util.Log.w("TestNavigation", "No route distances calculated")
            return
        }
        
        // Sort by distance and get closest
        routeDistances.sortBy { it.second }
        val closestRoute = routeDistances.firstOrNull() ?: return
        
        android.util.Log.d("TestNavigation", "Closest route: ${closestRoute.first}, distance: ${closestRoute.second} degrees (${closestRoute.second * 111} km)")
        
        // Select the closest route if it's different from current and within threshold
        val selectionThreshold = 0.15 // degrees (~16.5km)
        if (closestRoute.first != selectedRouteIndex && closestRoute.second < selectionThreshold) {
            android.util.Log.d("TestNavigation", "Selecting route ${closestRoute.first} (маршрут ${closestRoute.first + 1}, distance: ${closestRoute.second * 111} km)")
            
            selectedRouteIndex = closestRoute.first
            
            // Move selected route to first position (for navigation SDK)
            val reorderedRoutes = mutableListOf<NavigationRoute>()
            reorderedRoutes.add(currentRoutes[selectedRouteIndex]) // Add selected route first
            for (i in currentRoutes.indices) {
                if (i != selectedRouteIndex) {
                    reorderedRoutes.add(currentRoutes[i]) // Add other routes after
                }
            }
            
            // Update routes in navigation
            mapboxNavigation.setNavigationRoutes(reorderedRoutes)
            Toast.makeText(this, "Маршрут ${selectedRouteIndex + 1} избран", Toast.LENGTH_SHORT).show()
            
            // Update route info UI
            val selectedRoute = currentRoutes[selectedRouteIndex]
            updateRouteInfo(selectedRoute)
        }
    }
    
    private fun updateRouteInfo(route: NavigationRoute) {
        val routeDistance = route.directionsRoute.distance() ?: 0.0
        val routeDuration = route.directionsRoute.duration() ?: 0.0
        
        // Format distance (meters to km)
        val distanceKm = routeDistance / 1000.0
        val df = DecimalFormat("#.#")
        binding.tvRouteDistance.text = "${df.format(distanceKm)} km"
        
        // Format duration (seconds to hours:minutes)
        val hours = (routeDuration / 3600).toInt()
        val minutes = ((routeDuration % 3600) / 60).toInt()
        binding.tvRouteDuration.text = if (hours > 0) {
            "${hours}ч ${minutes}м"
        } else {
            "${minutes}м"
        }
    }

    private fun updateDestinationName(feature: GeocodingFeature?) {
        if (feature != null) {
            binding.tvDestinationName.text = feature.placeName ?: "Дестинация"
        }
    }

    private val arrivalObserver = object : ArrivalObserver {
        override fun onWaypointArrival(routeProgress: RouteProgress) {
            // User arrived at a waypoint (intermediate stop)
            Toast.makeText(this@TestNavigationActivity, "Пристигнахте на междинна точка", Toast.LENGTH_SHORT).show()
        }

        override fun onNextRouteLegStart(routeLegProgress: RouteLegProgress) {
            // User started navigating to next leg
            Toast.makeText(this@TestNavigationActivity, "Започвате следващата част от маршрута", Toast.LENGTH_SHORT).show()
        }

        override fun onFinalDestinationArrival(routeProgress: RouteProgress) {
            // User arrived at final destination
            Toast.makeText(this@TestNavigationActivity, "Пристигнахте на дестинацията!", Toast.LENGTH_LONG).show()
            
            // Stop navigation automatically
            mapboxNavigation.setNavigationRoutes(emptyList())
            isNavigationStarted = false
            
            // Hide navigation UI
            binding.btnStartNavigation.visibility = View.GONE
            binding.btnReset.visibility = View.GONE
            binding.btnStop.visibility = View.GONE
            binding.maneuverContainer.visibility = View.GONE
            binding.tripProgressCard.visibility = View.GONE
            binding.tvSpeed.visibility = View.GONE
            binding.mapControlsContainer.visibility = View.GONE
            
            // Reset camera to overview
            navigationCamera.requestNavigationCameraToOverview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestNavigationBinding.inflate(layoutInflater)
        customTripProgressView = binding.customTripProgressView
        setContentView(binding.root)

        setupNavigationApis()
        setupManeuverViewStyling()

        viewportDataSource = MapboxNavigationViewportDataSource(binding.mapView.mapboxMap)
        navigationCamera = NavigationCamera(binding.mapView.mapboxMap, binding.mapView.camera, viewportDataSource)
        binding.mapView.camera.addCameraAnimationsLifecycleListener(NavigationBasicGesturesHandler(navigationCamera))
        
        viewportDataSource.overviewPadding = overviewPadding
        viewportDataSource.followingPadding = followingPadding

        // Disable compass, scale bar, and attribution logo
        binding.mapView.compass.enabled = false
        binding.mapView.scalebar.enabled = false
        binding.mapView.attribution.enabled = false

        // Configure route line: single orange color (no traffic colors)
        val orangeColor = android.graphics.Color.parseColor("#FF6020") // Orange
        val darkerOrange = android.graphics.Color.parseColor("#CC4D1A") // Darker orange for casing
        
        val routeLineColorResources = RouteLineColorResources.Builder()
            .routeDefaultColor(orangeColor) // Main route line color - orange
            .routeCasingColor(darkerOrange) // Darker orange for casing
            .routeUnknownCongestionColor(orangeColor) // All traffic - orange (no traffic visualization)
            .routeLowCongestionColor(orangeColor) // All traffic - orange
            .routeModerateCongestionColor(orangeColor) // All traffic - orange
            .routeHeavyCongestionColor(orangeColor) // All traffic - orange
            .routeSevereCongestionColor(orangeColor) // All traffic - orange
            .build()
        
        val routeLineApiOptions = MapboxRouteLineApiOptions.Builder()
            .vanishingRouteLineEnabled(true) // Enable vanishing route line
            .isRouteCalloutsEnabled(false) // Disable route callouts (we show info in UI instead)
            .build()
        
        routeLineApi = MapboxRouteLineApi(routeLineApiOptions)
        
        val routeLineViewOptions = MapboxRouteLineViewOptions.Builder(this)
            .routeLineBelowLayerId("road-label")
            .routeLineColorResources(routeLineColorResources)
            .displaySoftGradientForTraffic(false) // Disable gradient - solid colors only
            .build()
        routeLineView = MapboxRouteLineView(routeLineViewOptions)
        routeArrowView = MapboxRouteArrowView(RouteArrowOptions.Builder(this).build())

        binding.mapView.mapboxMap.loadStyleUri(CUSTOM_STYLE_URI) { style ->
            routeLineView.initializeLayers(style)
            
            // Route callouts are disabled (they take too much space and make route selection difficult)
            
            // Configure location component with orange location puck
            val orangeColor = android.graphics.Color.parseColor("#FF6020") // Orange color matching route line
            binding.mapView.location.apply {
                setLocationProvider(navigationLocationProvider)
                updateSettings {
                    this.enabled = true
                    this.pulsingEnabled = true
                    this.pulsingColor = orangeColor
                    
                    // Create orange LocationPuck2D with custom images
                    this.locationPuck = LocationPuck2D(
                        topImage = ImageHolder.from(createOrangeTopImage()),
                        bearingImage = ImageHolder.from(createOrangeBearingImage()),
                        shadowImage = ImageHolder.from(createOrangeShadowImage())
                    )
                    this.puckBearingEnabled = true
                }
                
                // Setup listener for vanishing route line
                onIndicatorPositionChangedListener = com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener { point ->
                    if (isNavigationStarted) {
                        // Update vanishing route line with current puck position
                        val result = routeLineApi.updateTraveledRouteLine(point)
                        binding.mapView.mapboxMap.style?.let { style ->
                            routeLineView.renderRouteLineUpdate(style, result)
                        }
                    }
                }
                addOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener!!)
            }
        }

        setupGeocoding()
        setupSearch()
        setupButtons()
        setupMotorwayOptions()
        setupMapClickListener()
        
        // Hide navigation UI elements initially (they will show only when navigation starts)
        binding.btnReset.visibility = View.GONE
        binding.btnStop.visibility = View.GONE
        binding.tvSpeed.visibility = View.GONE
        binding.maneuverContainer.visibility = View.GONE
        binding.tripProgressCard.visibility = View.GONE
        binding.mapControlsContainer.visibility = View.GONE
        
        // Hide motorway options button initially (will show after destination selection)
        binding.root.findViewById<ImageButton>(R.id.btnMotorwayOptions)?.visibility = View.GONE
    }

    private fun setupManeuverViewStyling() {
        val orangeColor = android.graphics.Color.parseColor("#FF6020")
        val darkBackground = android.graphics.Color.parseColor("#202123")

        // Задаваме фон на контейнера
        binding.maneuverContainer.setBackgroundColor(darkBackground)

        // Обхождаме и променяме ManeuverView
        binding.maneuverView.post {
            setViewColors(binding.maneuverView, orangeColor, darkBackground)
            reduceManeuverTextSize(binding.maneuverView)
            reduceManeuverIconSize(binding.maneuverView)
        }
    }

    private fun setViewColors(view: View, textColor: Int, backgroundColor: Int) {
        // Променяме фона на всички View-та
        if (view.background != null) {
            view.setBackgroundColor(backgroundColor)
        }

        // Променяме текста на всички TextView-та
        if (view is TextView) {
            view.setTextColor(textColor)
        }

        // Рекурсивно обхождаме децата
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setViewColors(view.getChildAt(i), textColor, backgroundColor)
            }
        }
    }

    private fun setManeuverTextColors(view: View, color: Int) {
        if (view is TextView) {
            view.setTextColor(color)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setManeuverTextColors(view.getChildAt(i), color)
            }
        }
    }

    private fun reduceManeuverTextSize(view: View) {
        if (view is TextView) {
            val currentSize = view.textSize / resources.displayMetrics.scaledDensity
            // Reduce text size by 20% (multiply by 0.8)
            val newSize = currentSize * 0.8f
            view.textSize = newSize
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                reduceManeuverTextSize(view.getChildAt(i))
            }
        }
    }

    private fun reduceManeuverIconSize(view: View) {
        if (view is android.widget.ImageView) {
            val layoutParams = view.layoutParams
            if (layoutParams != null) {
                // Reduce icon size by 30% (multiply by 0.7)
                val currentWidth = layoutParams.width
                val currentHeight = layoutParams.height
                
                if (currentWidth > 0 && currentHeight > 0) {
                    layoutParams.width = (currentWidth * 0.7f).toInt()
                    layoutParams.height = (currentHeight * 0.7f).toInt()
                    view.layoutParams = layoutParams
                } else {
                    // If width/height are wrap_content or match_parent, set fixed smaller size
                    val sizeInDp = 32 // Reduced from typical 48dp
                    val sizeInPx = (sizeInDp * resources.displayMetrics.density).toInt()
                    layoutParams.width = sizeInPx
                    layoutParams.height = sizeInPx
                    view.layoutParams = layoutParams
                }
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                reduceManeuverIconSize(view.getChildAt(i))
            }
        }
    }

    private fun setupNavigationApis() {
        val distanceFormatterOptions = DistanceFormatterOptions.Builder(this)
            .unitType(UnitType.METRIC)
            .build()

        maneuverApi = MapboxManeuverApi(MapboxDistanceFormatter(distanceFormatterOptions))

    }

    private fun updateManeuverUI(routeProgress: RouteProgress) {
        val maneuversExpected = maneuverApi.getManeuvers(routeProgress)
        maneuversExpected.fold(
            { /* Error */ },
            { maneuvers ->
                if (maneuvers.isNotEmpty()) {
                    val maneuver = maneuvers.first()
                    // Show in official Mapbox view but keep your container visibility
                    binding.maneuverView.renderManeuvers(maneuversExpected)
                    binding.maneuverContainer.visibility = View.VISIBLE
                }
            }
        )
    }

    private fun updateProgressUI(routeProgress: RouteProgress) {
        customTripProgressView.update(routeProgress)
        binding.tripProgressCard.visibility = View.VISIBLE
    }

    private fun setupGeocoding() {
        try {
            val resourceId = resources.getIdentifier("mapbox_access_token", "string", packageName)
            accessToken = resources.getString(resourceId)
        } catch (e: Resources.NotFoundException) {
            Toast.makeText(this, "Mapbox token не е намерен", Toast.LENGTH_SHORT).show()
            return
        }
        
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.mapbox.com/")
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
        
        geocodingService = retrofit.create(MapboxGeocodingService::class.java)
        directionsService = retrofit.create(MapboxDirectionsService::class.java)
    }

    private fun setupSearch() {
        searchResultsAdapter = SearchResultsAdapter { feature ->
            selectDestination(feature)
        }
        
        binding.rvSearchResults.layoutManager = LinearLayoutManager(this)
        binding.rvSearchResults.adapter = searchResultsAdapter
        
        // Make the entire search input container clickable to focus the input field
        binding.root.findViewById<LinearLayout>(R.id.searchInputContainer)?.setOnClickListener {
            binding.etSearch.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
        }
        
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim()
                if (query.isNullOrEmpty()) {
                    binding.rvSearchResults.visibility = View.GONE
                } else if (query.length >= 2) {
                    performSearch(query)
                }
            }
        })
    }

    private fun performSearch(query: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val originLocation = navigationLocationProvider.lastLocation
                val proximity = originLocation?.let {
                    "${it.longitude},${it.latitude}"
                }
                
                val response = withContext(Dispatchers.IO) {
                    geocodingService.searchPlaces(query, accessToken, proximity, 10)
                }
                
                if (response.isSuccessful && response.body() != null) {
                    val features = response.body()!!.features
                    searchResultsAdapter.updateResults(features)
                    binding.rvSearchResults.visibility = if (features.isNotEmpty()) View.VISIBLE else View.GONE
                } else {
                    searchResultsAdapter.updateResults(emptyList())
                    binding.rvSearchResults.visibility = View.GONE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                searchResultsAdapter.updateResults(emptyList())
                binding.rvSearchResults.visibility = View.GONE
            }
        }
    }

    private fun selectDestination(feature: GeocodingFeature) {
        val center = feature.center
        if (center.size >= 2) {
            val destination = Point.fromLngLat(center[0], center[1])
            currentDestination = destination // Store destination for route recalculation
            
            // Update destination name
            updateDestinationName(feature)
            
            // Hide search container
            binding.searchContainer.visibility = View.GONE
            hideKeyboard()
            
            // Show motorway options button
            binding.root.findViewById<ImageButton>(R.id.btnMotorwayOptions)?.visibility = View.VISIBLE
            
            // Find route to destination
            findRoute(destination)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    private fun setupButtons() {
        binding.btnStartNavigation.setOnClickListener {
            val routes = mapboxNavigation.getNavigationRoutes()
            if (routes.isNotEmpty()) {
                val primaryRouteOnly = listOf(routes.first())
                
                // ВАЖНО: Първо обновяваме route line API, за да премахне alternatives от визуализацията
                routeLineApi.setNavigationRoutes(primaryRouteOnly, emptyList()) { value ->
                    binding.mapView.mapboxMap.style?.let { routeLineView.renderRouteDrawData(it, value) }
                }
                
                // След това задаваме само primary route в Navigation SDK
                mapboxNavigation.setNavigationRoutes(primaryRouteOnly)
                
                // Запазваме само primary route в currentRoutes
                currentRoutes = primaryRouteOnly
            }
            
            isNavigationStarted = true
            
            binding.btnStartNavigation.visibility = View.GONE
            binding.btnReset.visibility = View.VISIBLE
            binding.btnStop.visibility = View.VISIBLE
            binding.maneuverContainer.visibility = View.VISIBLE
            binding.tripProgressCard.visibility = View.VISIBLE
            binding.tvSpeed.visibility = View.VISIBLE
            binding.mapControlsContainer.visibility = View.VISIBLE
            
            // Hide route info container (destination, distance, time) and motorway options button
            binding.routeInfoContainer.visibility = View.GONE
            binding.root.findViewById<ImageButton>(R.id.btnMotorwayOptions)?.visibility = View.GONE
            
            navigationCamera.requestNavigationCameraToFollowing()
            
            // Hide route callouts when navigation starts
            try {
                routeLineView.clearCalloutAdapter()
            } catch (e: Exception) {
                android.util.Log.w("TestNavigation", "Failed to hide callouts: ${e.message}", e)
            }
        }
        
        binding.btnStop.setOnClickListener { 
            mapboxNavigation.setNavigationRoutes(emptyList())
            isNavigationStarted = false
        }
        
        binding.btnReset.setOnClickListener {
            navigationCamera.requestNavigationCameraToFollowing()
        }
        
        binding.btnRecenter.setOnClickListener {
            navigationCamera.requestNavigationCameraToFollowing()
        }
        
        binding.btnOverview.setOnClickListener {
            navigationCamera.requestNavigationCameraToOverview()
        }
    }

    private fun setupMotorwayOptions() {
        // Load current setting
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        allowMotorways = prefs.getBoolean("allow_motorways", false)
        
        val btnMotorwayOptions = binding.root.findViewById<ImageButton>(R.id.btnMotorwayOptions)
        val motorwayOptionsContainer = binding.root.findViewById<LinearLayout>(R.id.motorwayOptionsContainer)
        val btnWithMotorways = binding.root.findViewById<ImageButton>(R.id.btnWithMotorways)
        val btnWithoutMotorways = binding.root.findViewById<ImageButton>(R.id.btnWithoutMotorways)
        
        // Update button icon based on current setting
        updateMotorwayButtonIcon(btnMotorwayOptions)
        
        // Toggle options visibility on button click
        btnMotorwayOptions?.setOnClickListener {
            val isVisible = motorwayOptionsContainer?.visibility == View.VISIBLE
            if (isVisible) {
                hideMotorwayOptions(motorwayOptionsContainer)
            } else {
                showMotorwayOptions(motorwayOptionsContainer)
            }
        }
        
        // With motorways option
        btnWithMotorways?.setOnClickListener {
            if (!allowMotorways) {
                allowMotorways = true
                prefs.edit().putBoolean("allow_motorways", true).apply()
                updateMotorwayButtonIcon(btnMotorwayOptions)
                hideMotorwayOptions(motorwayOptionsContainer)
                // Recalculate route WITHOUT clearing UI (isRecalculation = true)
                currentDestination?.let { findRoute(it, isRecalculation = true) }
            }
        }
        
        // Without motorways option
        btnWithoutMotorways?.setOnClickListener {
            if (allowMotorways) {
                allowMotorways = false
                prefs.edit().putBoolean("allow_motorways", false).apply()
                updateMotorwayButtonIcon(btnMotorwayOptions)
                hideMotorwayOptions(motorwayOptionsContainer)
                // Recalculate route WITHOUT clearing UI (isRecalculation = true)
                currentDestination?.let { findRoute(it, isRecalculation = true) }
            }
        }
    }
    
    private fun updateMotorwayButtonIcon(btn: ImageButton?) {
        val iconRes = if (allowMotorways) R.drawable.ic_motorway else R.drawable.ic_road
        btn?.setImageResource(iconRes)
    }
    
    private fun showMotorwayOptions(container: LinearLayout?) {
        container?.let {
            val btnStartNavigation = binding.btnStartNavigation
            
            // Hide start button immediately
            btnStartNavigation.visibility = View.GONE
            
            // Make visible but transparent to measure height
            it.visibility = View.VISIBLE
            it.alpha = 0f
            
            // Measure container
            it.measure(
                View.MeasureSpec.makeMeasureSpec(it.parent?.let { (it as? View)?.width ?: View.MeasureSpec.UNSPECIFIED } ?: View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val height = it.measuredHeight
            
            // Start from below (off screen)
            it.translationY = height.toFloat() + 200f
            
            // Animate up
            ObjectAnimator.ofFloat(it, "translationY", height.toFloat() + 200f, 0f).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                start()
            }
            
            // Fade in
            ObjectAnimator.ofFloat(it, "alpha", 0f, 1f).apply {
                duration = 300
                start()
            }
        }
    }
    
    private fun hideMotorwayOptions(container: LinearLayout?) {
        container?.let {
            val btnStartNavigation = binding.btnStartNavigation
            val height = it.height
            if (height == 0) {
                it.visibility = View.GONE
                btnStartNavigation.visibility = View.VISIBLE
                return
            }
            
            // Animate down
            ObjectAnimator.ofFloat(it, "translationY", 0f, height.toFloat() + 200f).apply {
                duration = 250
                interpolator = DecelerateInterpolator()
                start()
            }
            
            // Fade out
            ObjectAnimator.ofFloat(it, "alpha", 1f, 0f).apply {
                duration = 250
                start()
            }.addUpdateListener { animator ->
                if (animator.animatedValue as Float <= 0f) {
                    it.visibility = View.GONE
                    it.translationY = 0f
                    btnStartNavigation.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun initNavigation() {
        if (!MapboxNavigationApp.isSetup()) {
            MapboxNavigationApp.setup(NavigationOptions.Builder(this).build())
        }
    }

    private fun findRoute(destination: Point, isRecalculation: Boolean = false) {
        val originLocation = navigationLocationProvider.lastLocation ?: return
        
        // Set flag if this is a recalculation (motorway option change)
        if (isRecalculation) {
            isRecalculatingRoute = true
        }
        
        val originPoint = Point.fromLngLat(originLocation.longitude, originLocation.latitude)
        
        // Only clear routes if this is NOT a recalculation (new destination selection)
        if (!isRecalculation) {
            mapboxNavigation.setNavigationRoutes(emptyList())
        }
        
        val routeOptionsBuilder = RouteOptions.builder()
            .applyDefaultNavigationOptions()
            .applyLanguageAndVoiceUnitOptions(this)
            .coordinatesList(listOf(originPoint, destination))
            .alternatives(true)
        
        // Добавяме изключване на магистрали
        if (!allowMotorways) {
            routeOptionsBuilder.exclude("motorway")
        }

        mapboxNavigation.requestRoutes(
            routeOptionsBuilder.build(),
            object : NavigationRouterCallback {
                override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                    mapboxNavigation.setNavigationRoutes(routes)
                    // RoutesObserver ще се погрижи за визуализацията автоматично
                }
                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    isRecalculatingRoute = false // Reset flag on failure
                    Toast.makeText(this@TestNavigationActivity, "Грешка при намиране на маршрут", Toast.LENGTH_SHORT).show()
                }
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                    isRecalculatingRoute = false // Reset flag on cancel
                }
            }
        )
    }

    private fun createOrangeTopImage(): android.graphics.Bitmap {
        val density = resources.displayMetrics.density
        val size = (32 * density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        val centerX = size / 2f
        val centerY = size / 2f
        val radius = 12f * density
        
        // Draw orange circle
        val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#FF6020")
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, radius, fillPaint)
        
        // Draw white stroke
        val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3f * density
        }
        canvas.drawCircle(centerX, centerY, radius, strokePaint)
        
        return bitmap
    }
    
    private fun createOrangeBearingImage(): android.graphics.Bitmap {
        val density = resources.displayMetrics.density
        val size = (32 * density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        val centerX = size / 2f
        val centerY = size / 2f
        val radius = 12f * density
        
        // Draw orange circle
        val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#FF6020")
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, radius, fillPaint)
        
        // Draw white stroke
        val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3f * density
        }
        canvas.drawCircle(centerX, centerY, radius, strokePaint)
        
        // Draw arrow pointing up (bearing indicator)
        val arrowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
        val path = android.graphics.Path()
        val arrowWidth = 4f * density
        val arrowHeight = 8f * density
        val arrowTopY = centerY - radius + 2f * density
        
        path.moveTo(centerX, arrowTopY)
        path.lineTo(centerX - arrowWidth, arrowTopY + arrowHeight)
        path.lineTo(centerX + arrowWidth, arrowTopY + arrowHeight)
        path.close()
        canvas.drawPath(path, arrowPaint)
        
        return bitmap
    }
    
    private fun createOrangeShadowImage(): android.graphics.Bitmap {
        val density = resources.displayMetrics.density
        val size = (32 * density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        val centerX = size / 2f
        val centerY = size / 2f
        val radiusX = 14f * density
        val radiusY = 6f * density
        
        // Draw elliptical shadow (oval shape for 3D effect)
        val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(100, 0, 0, 0)
            style = android.graphics.Paint.Style.FILL
        }
        
        val rect = android.graphics.RectF(
            centerX - radiusX,
            centerY - radiusY,
            centerX + radiusX,
            centerY + radiusY
        )
        canvas.drawOval(rect, shadowPaint)
        
        return bitmap
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove indicator position listener
        onIndicatorPositionChangedListener?.let {
            binding.mapView.location.removeOnIndicatorPositionChangedListener(it)
        }
        maneuverApi.cancel()
        routeLineApi.cancel()
        routeLineView.cancel()
    }
}
