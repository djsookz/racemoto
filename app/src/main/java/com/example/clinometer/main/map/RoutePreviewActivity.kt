package com.example.clinometer.main.map

import com.example.clinometer.*
import com.example.clinometer.main.MainActivity
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.clinometer.navigation.DirectionsResponse
import com.example.clinometer.navigation.DirectionsRoute
import com.example.clinometer.navigation.MapboxDirectionsService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import android.util.Log
import com.example.clinometer.data.ProfileStorage
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.DecimalFormat
import com.google.gson.Gson

class RoutePreviewActivity : AppCompatActivity() {
    
    private lateinit var mapView: MapView
    private lateinit var mapboxMap: MapboxMap
    private lateinit var directionsService: MapboxDirectionsService
    private var accessToken: String = ""
    
    private var originLat: Double = 0.0
    private var originLon: Double = 0.0
    private var destinationLat: Double = 0.0
    private var destinationLon: Double = 0.0
    private var destinationName: String = ""
    
    private var routeGeometry: LineString? = null
    private var routeDistance: Double = 0.0
    private var routeDuration: Double = 0.0
    
    // Multiple routes support
    private var allRoutes: List<DirectionsRoute> = emptyList()
    private var selectedRouteIndex: Int = 0
    private var currentStyle: Style? = null
    
    // Motorway options
    private var allowMotorways: Boolean = false
    private var btnMotorwayOptions: ImageButton? = null
    private var motorwayOptionsContainer: LinearLayout? = null
    private var btnWithMotorways: ImageButton? = null
    private var btnWithoutMotorways: ImageButton? = null
    
    // Route cache
    private var cachedAllRoutes: List<DirectionsRoute>? = null
    private var cachedNoMotorwaysRoutes: List<DirectionsRoute>? = null
    private var lastCacheCoordinates: String = ""
    private var routeCoordinatesFixed: Boolean = false
    private var fixedOriginLat: Double = 0.0
    private var fixedOriginLon: Double = 0.0
    
    // Location tracking - keep GPS warm for instant navigation start
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var currentBearing: Float = 0f
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                currentLocation = location
                // Update origin coordinates ONLY if route hasn't been calculated yet
                // Once route is calculated, keep the coordinates fixed for this session
                if (!routeCoordinatesFixed) {
                    originLat = location.latitude
                    originLon = location.longitude
                }
                if (location.hasBearing()) {
                    currentBearing = location.bearing
                }
                Log.d("RoutePreview", "📍 GPS updated: $originLat, $originLon, bearing: $currentBearing (fixed: $routeCoordinatesFixed)")
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_preview)
        
        // Get destination from intent
        destinationName = intent.getStringExtra("destination_name") ?: ""
        destinationLat = intent.getDoubleExtra("destination_latitude", 0.0)
        destinationLon = intent.getDoubleExtra("destination_longitude", 0.0)
        originLat = intent.getDoubleExtra("origin_latitude", 0.0)
        originLon = intent.getDoubleExtra("origin_longitude", 0.0)
        
        // Reset route cache for new destination
        cachedAllRoutes = null
        cachedNoMotorwaysRoutes = null
        lastCacheCoordinates = ""
        routeCoordinatesFixed = false
        fixedOriginLat = 0.0
        fixedOriginLon = 0.0
        
        if (destinationLat == 0.0 || destinationLon == 0.0) {
            Toast.makeText(this, "Грешка: липсва дестинация", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Start GPS tracking immediately to have fresh location when navigation starts
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startLocationUpdates()
        
        // Get Mapbox access token
        try {
            val resources: Resources = resources
            val resourceId = resources.getIdentifier("mapbox_access_token", "string", packageName)
            accessToken = resources.getString(resourceId)
        } catch (e: Resources.NotFoundException) {
            Toast.makeText(this, "Mapbox token не е намерен", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Initialize Retrofit for Directions API
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.mapbox.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        directionsService = retrofit.create(MapboxDirectionsService::class.java)
        
        // Load motorway preference BEFORE calculating route
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        allowMotorways = prefs.getBoolean("allow_motorways", false)
        Log.d("RoutePreview", "Loaded motorway preference: allowMotorways = $allowMotorways")
        
        // Initialize MapView
        mapView = findViewById(R.id.mapView)
        mapboxMap = mapView.getMapboxMap()
        
        // Load map style (cached for faster loading)
        val styleUri = "mapbox://styles/djsookz/cmiyer8iu000101s84wqsav5l"
        mapboxMap.loadStyleUri(styleUri) { style ->
            // Calculate and display route
            calculateRoute(style)
        }
        
        // Setup click listener for route selection
        mapboxMap.addOnMapClickListener { clickPoint ->
            handleRouteClick(clickPoint)
            true
        }
        
        // Display destination name
        val tvDestinationName = findViewById<TextView>(R.id.tvDestinationName)
        tvDestinationName.text = destinationName
        
        // Setup motorway options UI
        setupMotorwayOptions()
    }
    
    private fun setupMotorwayOptions() {
        // Load current setting
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        allowMotorways = prefs.getBoolean("allow_motorways", false)
        
        btnMotorwayOptions = findViewById(R.id.btnMotorwayOptions)
        motorwayOptionsContainer = findViewById(R.id.motorwayOptionsContainer)
        btnWithMotorways = findViewById(R.id.btnWithMotorways)
        btnWithoutMotorways = findViewById(R.id.btnWithoutMotorways)
        
        // Update button icon based on current setting
        updateMotorwayButtonIcon()
        
        // Toggle options visibility on button click with animation
        btnMotorwayOptions?.setOnClickListener {
            val isVisible = motorwayOptionsContainer?.visibility == View.VISIBLE
            if (isVisible) {
                hideOptionsWithAnimation()
            } else {
                showOptionsWithAnimation()
            }
        }
        
        // With motorways option
        btnWithMotorways?.setOnClickListener {
            if (!allowMotorways) {
                allowMotorways = true
                prefs.edit().putBoolean("allow_motorways", true).apply()
                updateMotorwayButtonIcon()
                hideOptionsWithAnimation()
                // Recalculate route with motorways
                currentStyle?.let { calculateRoute(it) }
            }
        }
        
        // Without motorways option
        btnWithoutMotorways?.setOnClickListener {
            if (allowMotorways) {
                allowMotorways = false
                prefs.edit().putBoolean("allow_motorways", false).apply()
                updateMotorwayButtonIcon()
                hideOptionsWithAnimation()
                // Recalculate route without motorways
                currentStyle?.let { calculateRoute(it) }
            }
        }
        
        // Close options when clicking start button
        val btnStartNavigation = findViewById<Button>(R.id.btnStartNavigation)
        btnStartNavigation.setOnClickListener {
            hideOptionsWithAnimation()
            startNavigation()
        }
    }
    
    private fun updateMotorwayButtonIcon() {
        // Update icon based on selection: motorway icon for motorways, road icon for regular roads
        val iconRes = if (allowMotorways) R.drawable.ic_motorway else R.drawable.ic_road
        btnMotorwayOptions?.setImageResource(iconRes)
    }
    
    private fun showOptionsWithAnimation() {
        motorwayOptionsContainer?.let { container ->
            val btnStartNavigation = findViewById<Button>(R.id.btnStartNavigation)
            
            // Hide start button immediately
            btnStartNavigation.visibility = View.GONE
            
            // First make it visible but transparent to measure height
            container.visibility = View.VISIBLE
            container.alpha = 0f
            
            // Measure the container
            container.measure(
                View.MeasureSpec.makeMeasureSpec(container.parent?.let { 
                    (it as? View)?.width ?: View.MeasureSpec.UNSPECIFIED 
                } ?: View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val height = container.measuredHeight
            
            // Start from below (off screen)
            container.translationY = height.toFloat() + 200f // Extra offset to start from below
            
            // Animate up
            ObjectAnimator.ofFloat(container, "translationY", height.toFloat() + 200f, 0f).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                start()
            }
            
            // Fade in
            ObjectAnimator.ofFloat(container, "alpha", 0f, 1f).apply {
                duration = 300
                start()
            }
        }
    }
    
    private fun hideOptionsWithAnimation() {
        motorwayOptionsContainer?.let { container ->
            val btnStartNavigation = findViewById<Button>(R.id.btnStartNavigation)
            val height = container.height
            if (height == 0) {
                // If height is 0, just hide it
                container.visibility = View.GONE
                btnStartNavigation.visibility = View.VISIBLE
                return
            }
            
            // Animate down (off screen)
            ObjectAnimator.ofFloat(container, "translationY", 0f, height.toFloat() + 200f).apply {
                duration = 250
                interpolator = DecelerateInterpolator()
                start()
            }
            
            // Fade out
            ObjectAnimator.ofFloat(container, "alpha", 1f, 0f).apply {
                duration = 250
                start()
            }.addUpdateListener { animator ->
                if (animator.animatedValue as Float <= 0f) {
                    container.visibility = View.GONE
                    container.translationY = 0f // Reset for next time
                    // Show start button after animation
                    btnStartNavigation.visibility = View.VISIBLE
                }
            }
        }
    }
    
    private fun calculateRoute(style: Style) {
        if (originLat == 0.0 || originLon == 0.0) {
            Toast.makeText(this, "Грешка: липсва текуща локация", Toast.LENGTH_SHORT).show()
            return
        }
        
        currentStyle = style
        
        // Fix coordinates on first calculation - don't update them from GPS anymore
        if (!routeCoordinatesFixed) {
            fixedOriginLat = originLat
            fixedOriginLon = originLon
            routeCoordinatesFixed = true
            Log.d("RoutePreview", "🔒 Fixed route coordinates: $fixedOriginLat, $fixedOriginLon")
        }
        
        // Use fixed coordinates for route calculations
        val routeOriginLat = fixedOriginLat
        val routeOriginLon = fixedOriginLon
        
        // Build coordinates string for API: "lon1,lat1;lon2,lat2"
        val coordinates = "$routeOriginLon,$routeOriginLat;$destinationLon,$destinationLat"
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Check if coordinates changed - if so, clear cache (shouldn't happen after fixing)
                if (lastCacheCoordinates != coordinates && lastCacheCoordinates.isNotEmpty()) {
                    // Coordinates changed (shouldn't happen), clear cache
                    cachedAllRoutes = null
                    cachedNoMotorwaysRoutes = null
                }
                lastCacheCoordinates = coordinates
                
                val filteredRoutes = if (allowMotorways) {
                    // When motorways are allowed: fetch all routes if not cached
                    if (cachedAllRoutes == null) {
                        Log.d("RoutePreview", "API Request #1: Fetching all routes (with motorways)")
                        val allRoutesResponse = withContext(Dispatchers.IO) {
                            directionsService.getRoute(coordinates, accessToken, alternatives = true, exclude = null)
                        }
                        
                        if (allRoutesResponse.isSuccessful && allRoutesResponse.body() != null) {
                            cachedAllRoutes = allRoutesResponse.body()!!.routes
                            Log.d("RoutePreview", "Cached ${cachedAllRoutes!!.size} all routes")
                        } else {
                            Log.e("RoutePreview", "Failed to fetch all routes")
                        }
                    } else {
                        Log.d("RoutePreview", "Using cached all routes (${cachedAllRoutes!!.size} routes)")
                    }
                    
                    val allRoutes = cachedAllRoutes ?: emptyList()
                    
                    // If we have both caches, filter to show only routes WITH motorways
                    if (cachedNoMotorwaysRoutes != null && allRoutes.isNotEmpty()) {
                        // We have both cached, so filter to show only routes with motorways
                        val noMotorwaysRoutes = cachedNoMotorwaysRoutes!!
                        
                        // Create signatures for routes without motorways
                        val noMotorwaysSignatures = noMotorwaysRoutes.map { route ->
                            "${(route.distance / 50).toInt()}_${(route.duration / 30).toInt()}"
                        }.toSet()
                        
                        // Keep routes that are NOT in the no-motorways set
                        val routesWithMotorways = allRoutes.filter { route ->
                            val signature = "${(route.distance / 50).toInt()}_${(route.duration / 30).toInt()}"
                            !noMotorwaysSignatures.contains(signature)
                        }
                        
                        Log.d("RoutePreview", "Filtered: ${allRoutes.size} total, ${noMotorwaysRoutes.size} without, ${routesWithMotorways.size} WITH motorways")
                        routesWithMotorways
                    } else {
                        // No cached no-motorways routes - just show all routes
                        // This happens on initial load when allowMotorways=true
                        Log.d("RoutePreview", "Showing all routes (${allRoutes.size} routes)")
                        allRoutes
                    }
                } else {
                    // When motorways are NOT allowed - fetch only no-motorways routes if not cached
                    if (cachedNoMotorwaysRoutes == null) {
                        Log.d("RoutePreview", "API Request #1 or #2: Fetching routes without motorways")
                        val response = withContext(Dispatchers.IO) {
                            directionsService.getRoute(coordinates, accessToken, alternatives = true, exclude = "motorway")
                        }
                        
                        if (response.isSuccessful && response.body() != null) {
                            cachedNoMotorwaysRoutes = response.body()!!.routes
                            Log.d("RoutePreview", "Cached ${cachedNoMotorwaysRoutes!!.size} routes without motorways")
                            cachedNoMotorwaysRoutes!!
                        } else {
                            Log.e("RoutePreview", "Failed to fetch routes")
                            emptyList()
                        }
                    } else {
                        // Use cached no-motorways routes
                        Log.d("RoutePreview", "Using cached routes without motorways (${cachedNoMotorwaysRoutes!!.size} routes)")
                        cachedNoMotorwaysRoutes!!
                    }
                }
                
                if (filteredRoutes.isNotEmpty()) {
                    // Create new response with filtered routes
                    val filteredResponse = DirectionsResponse(
                        routes = filteredRoutes,
                        code = "Ok"
                    )
                    directionsResponseJson = Gson().toJson(filteredResponse)
                    
                    allRoutes = filteredRoutes
                    selectedRouteIndex = 0
                    Log.d("RoutePreview", "Final: ${allRoutes.size} routes (allowMotorways: $allowMotorways)")
                    
                    // Select first route as primary
                    selectRoute(0)
                    
                    // Display all routes on map
                    displayAllRoutesOnMap(style)
                    
                    // Fit camera to route
                    fitCameraToRoute()
                } else {
                    Toast.makeText(this@RoutePreviewActivity, 
                        if (allowMotorways) "Не е намерен маршрут с магистрали" else "Не е намерен маршрут", 
                        Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@RoutePreviewActivity, 
                    "Грешка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun selectRoute(index: Int) {
        if (index >= allRoutes.size) return
        
        selectedRouteIndex = index
        val route = allRoutes[index]
        routeDistance = route.distance
        routeDuration = route.duration
        
        // Extract geometry from selected route
        val coordinatesList = route.geometry.coordinates.map { coord ->
            Point.fromLngLat(coord[0], coord[1])
        }
        routeGeometry = LineString.fromLngLats(coordinatesList)
        
        // Update UI with selected route info
        updateRouteInfo()
    }
    
    private fun displayAllRoutesOnMap(style: Style) {
        try {
            // Clear ALL old route layers and callouts first
            clearOldCallouts(style)
            clearAllOldRoutes(style)
            
            // All routes use the same orange color - like Google Maps
            val routeColor = "#FF6020"       // App primary color
            val casingColor = "#CC4D1A"      // Darker casing
            
            // First pass: Draw ALL routes with same orange color (this makes overlapping parts solid orange)
            for (i in allRoutes.indices) {
                val route = allRoutes[i]
                val routeId = "route-$i"
                
                val coordinatesList = route.geometry.coordinates.map { coord ->
                    Point.fromLngLat(coord[0], coord[1])
                }
                val geometry = LineString.fromLngLats(coordinatesList)
                val feature = Feature.fromGeometry(geometry)
                feature.addNumberProperty("route_index", i)
                val featureCollection = FeatureCollection.fromFeatures(listOf(feature))
                
                style.addSource(
                    geoJsonSource("$routeId-source") {
                        featureCollection(featureCollection)
                    }
                )
            }
            
            // Second pass: Add layers (alternatives first with dashed line, then primary solid on top)
            for (i in allRoutes.indices.reversed()) {
                val isPrimary = i == selectedRouteIndex
                val routeId = "route-$i"
                
                if (!isPrimary) {
                    // Alternative routes: solid darker orange line
                    style.addLayer(
                        lineLayer("$routeId-casing-layer", "$routeId-source") {
                            lineColor("#5A2D16")
                            lineWidth(10.0)
                            lineCap(com.mapbox.maps.extension.style.layers.properties.generated.LineCap.ROUND)
                            lineJoin(com.mapbox.maps.extension.style.layers.properties.generated.LineJoin.ROUND)
                            slot("middle")
                        }
                    )
                    style.addLayer(
                        lineLayer("$routeId-layer", "$routeId-source") {
                            lineColor("#994015")  // Darker orange
                            lineWidth(6.0)
                            lineCap(com.mapbox.maps.extension.style.layers.properties.generated.LineCap.ROUND)
                            lineJoin(com.mapbox.maps.extension.style.layers.properties.generated.LineJoin.ROUND)
                            slot("middle")
                        }
                    )
                }
            }
            
            // Third pass: Draw primary route solid on top (covers the dashed parts where routes overlap)
            val primaryRouteId = "route-$selectedRouteIndex"
            style.addLayer(
                lineLayer("$primaryRouteId-casing-layer", "$primaryRouteId-source") {
                    lineColor(casingColor)
                    lineWidth(12.0)
                    lineCap(com.mapbox.maps.extension.style.layers.properties.generated.LineCap.ROUND)
                    lineJoin(com.mapbox.maps.extension.style.layers.properties.generated.LineJoin.ROUND)
                    slot("middle")
                }
            )
            style.addLayer(
                lineLayer("$primaryRouteId-layer", "$primaryRouteId-source") {
                    lineColor(routeColor)
                    lineWidth(8.0)
                    lineCap(com.mapbox.maps.extension.style.layers.properties.generated.LineCap.ROUND)
                    lineJoin(com.mapbox.maps.extension.style.layers.properties.generated.LineJoin.ROUND)
                    slot("middle")
                }
            )
            
            // Add route callouts (time labels)
            addRouteCallouts()
            
            Log.d("RoutePreview", "All ${allRoutes.size} route layers added successfully")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Грешка при показване на маршрутите: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun clearOldCallouts(style: Style) {
        // Remove all old callout layers and sources
        for (i in 0..20) { // Clear up to 20 old callouts
            val sourceId = "callout-source-$i"
            val layerId = "callout-layer-$i"
            try {
                if (style.styleLayerExists(layerId)) {
                    style.removeStyleLayer(layerId)
                }
                if (style.styleSourceExists(sourceId)) {
                    style.removeStyleSource(sourceId)
                }
            } catch (e: Exception) {
                // Ignore errors for non-existent layers
            }
        }
    }
    
    private fun clearAllOldRoutes(style: Style) {
        // Remove ALL old route layers and sources (up to 20 routes)
        for (i in 0..20) {
            val routeId = "route-$i"
            try {
                if (style.styleLayerExists("$routeId-casing-layer")) {
                    style.removeStyleLayer("$routeId-casing-layer")
                }
                if (style.styleLayerExists("$routeId-layer")) {
                    style.removeStyleLayer("$routeId-layer")
                }
                if (style.styleLayerExists("$routeId-dash-layer")) {
                    style.removeStyleLayer("$routeId-dash-layer")
                }
                if (style.styleSourceExists("$routeId-source")) {
                    style.removeStyleSource("$routeId-source")
                }
            } catch (e: Exception) {
                // Ignore errors for non-existent layers
            }
        }
    }
    
    private fun addRouteCallouts() {
        // Add callouts using PointAnnotation for each route
        for (i in allRoutes.indices) {
            val route = allRoutes[i]
            val coords = route.geometry.coordinates
            if (coords.isEmpty()) continue
            
            // Get midpoint of route
            val midIndex = coords.size / 2
            val midPoint = coords[midIndex]
            val lon = midPoint[0]
            val lat = midPoint[1]
            
            // Format duration
            val durationMinutes = (route.duration / 60).toInt()
            val hours = durationMinutes / 60
            val minutes = durationMinutes % 60
            val durationText = if (hours > 0) "${hours}ч ${minutes}м" else "${minutes} мин"
            
            Log.d("RoutePreview", "Route $i: $durationText at $lat, $lon")
            
            // Add symbol layer for this callout
            addCalloutSymbol(i, lon, lat, durationText, i == selectedRouteIndex)
        }
    }
    
    private fun addCalloutSymbol(routeIndex: Int, lon: Double, lat: Double, text: String, isSelected: Boolean) {
        val style = currentStyle ?: return
        val sourceId = "callout-source-$routeIndex"
        val layerId = "callout-layer-$routeIndex"
        
        try {
            // Remove if exists
            if (style.styleLayerExists(layerId)) {
                style.removeStyleLayer(layerId)
            }
            if (style.styleSourceExists(sourceId)) {
                style.removeStyleSource(sourceId)
            }
            
            // Create point feature with text property
            val point = Point.fromLngLat(lon, lat)
            val feature = Feature.fromGeometry(point)
            feature.addStringProperty("duration", text)
            feature.addNumberProperty("route_index", routeIndex)
            val featureCollection = FeatureCollection.fromFeatures(listOf(feature))
            
            style.addSource(
                geoJsonSource(sourceId) {
                    featureCollection(featureCollection)
                }
            )
            
            val bgColor = if (isSelected) "#FF6020" else "#804020"
            
            style.addLayer(
                com.mapbox.maps.extension.style.layers.generated.symbolLayer(layerId, sourceId) {
                    textField(text)
                    textSize(14.0)
                    textColor("#FFFFFF")
                    textHaloColor(bgColor)
                    textHaloWidth(8.0)
                    textAllowOverlap(true)
                    textIgnorePlacement(true)
                }
            )
        } catch (e: Exception) {
            Log.e("RoutePreview", "Error adding callout symbol: ${e.message}")
        }
    }
    
    private fun handleRouteClick(clickPoint: Point) {
        if (allRoutes.size <= 1) return
        
        Log.d("RoutePreview", "Click at: ${clickPoint.latitude()}, ${clickPoint.longitude()}")
        
        // Calculate distance from click to each route
        val routeDistances = mutableListOf<Pair<Int, Double>>()
        
        for (i in allRoutes.indices) {
            val route = allRoutes[i]
            val coords = route.geometry.coordinates
            
            var minDistanceForRoute = Double.MAX_VALUE
            
            // Check all points for accuracy
            for (coord in coords) {
                val dx = clickPoint.longitude() - coord[0]
                val dy = clickPoint.latitude() - coord[1]
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                
                if (distance < minDistanceForRoute) {
                    minDistanceForRoute = distance
                }
            }
            
            routeDistances.add(Pair(i, minDistanceForRoute))
            Log.d("RoutePreview", "Route $i distance: $minDistanceForRoute")
        }
        
        // Sort by distance and get closest
        routeDistances.sortBy { it.second }
        val closestRoute = routeDistances.firstOrNull() ?: return
        
        // Select the closest route if it's different from current and within threshold
        // 0.02 degrees ≈ ~2km - very easy selection
        if (closestRoute.first != selectedRouteIndex && closestRoute.second < 0.02) {
            Log.d("RoutePreview", "Selecting route ${closestRoute.first}")
            selectRoute(closestRoute.first)
            currentStyle?.let { style ->
                displayAllRoutesOnMap(style)
            }
            Toast.makeText(this, "Маршрут ${closestRoute.first + 1} избран", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateRouteInfo() {
        val tvDistance = findViewById<TextView>(R.id.tvRouteDistance)
        val tvDuration = findViewById<TextView>(R.id.tvRouteDuration)
        
        // Format distance (meters to km)
        val distanceKm = routeDistance / 1000.0
        val df = DecimalFormat("#.#")
        tvDistance.text = "${df.format(distanceKm)} km"
        
        // Format duration (seconds to hours:minutes)
        val hours = (routeDuration / 3600).toInt()
        val minutes = ((routeDuration % 3600) / 60).toInt()
        if (hours > 0) {
            tvDuration.text = "${hours}ч ${minutes}м"
        } else {
            tvDuration.text = "${minutes}м"
        }
    }
    
    private fun fitCameraToRoute() {
        val geometry = routeGeometry ?: return
        
        // Calculate bounds from coordinates
        val coordinates = geometry.coordinates()
        if (coordinates.isNotEmpty()) {
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE
            var maxLon = -Double.MAX_VALUE
            
            coordinates.forEach { point ->
                val lat = point.latitude()
                val lon = point.longitude()
                if (lat < minLat) minLat = lat
                if (lat > maxLat) maxLat = lat
                if (lon < minLon) minLon = lon
                if (lon > maxLon) maxLon = lon
            }
            
            // Calculate center and zoom manually
            val centerLat = (minLat + maxLat) / 2.0
            val centerLon = (minLon + maxLon) / 2.0
            val latDiff = maxLat - minLat
            val lonDiff = maxLon - minLon
            val maxDiff = kotlin.math.max(latDiff, lonDiff)
            
            // Calculate appropriate zoom level
            val zoom = if (maxDiff > 0.0) {
                kotlin.math.log2(360.0 / maxDiff) - 1.0
            } else {
                15.0
            }.coerceIn(3.0, 19.0)
            
            mapboxMap.setCamera(
                com.mapbox.maps.CameraOptions.Builder()
                    .center(Point.fromLngLat(centerLon, centerLat))
                    .zoom(zoom)
                    .build()
            )
        }
    }
    
    private var directionsResponseJson: String? = null
    
    private fun startNavigation() {
        val geometry = routeGeometry ?: run {
            Toast.makeText(this, "Маршрутът все още се зарежда", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Stop location updates before navigating
        stopLocationUpdates()
        
        // Calculate bearing to destination if GPS bearing not available
        val bearingToUse = if (currentBearing != 0f) {
            currentBearing
        } else {
            // Calculate bearing from origin to first route point
            calculateBearing(originLat, originLon, 
                routeGeometry?.coordinates()?.getOrNull(1)?.latitude() ?: destinationLat,
                routeGeometry?.coordinates()?.getOrNull(1)?.longitude() ?: destinationLon)
        }
        
        // Get current profile to pass to MainActivity
        val selectedProfileId = ProfileStorage.getSelectedProfileId(this)
        val profiles = ProfileStorage.loadProfiles(this)
        val currentProfile = profiles.find { it.id == selectedProfileId } 
            ?: profiles.firstOrNull() 
            ?: Profile(name = "My profile", vehicleType = Profile.VehicleType.MOTORCYCLE)
        
        // ВАЖНО: Запази големите данни във файлове вместо в Intent за да избегнем TransactionTooLargeException
        val geometryJson = geometry.toJson()
        val geometryInCache = if (geometryJson.length > 50_000) {
            // Голям маршрут - запази във файл
            try {
                NavigationDataCache.saveRouteGeometry(this, geometryJson)
                true
            } catch (e: Exception) {
                Log.e("RoutePreview", "Failed to cache route geometry", e)
                false
            }
        } else {
            // Малък маршрут - може да се предаде директно
            false
        }
        
        var directionsResponseInCache = false
        var directionsResponseJson: String? = null
        try {
            val selectedRoute = allRoutes.getOrNull(selectedRouteIndex)
            if (selectedRoute != null) {
                // Create a minimal response with just the selected route
                val minimalResponse = com.example.clinometer.navigation.DirectionsResponse(
                    routes = listOf(selectedRoute),
                    code = "Ok"
                )
                directionsResponseJson = Gson().toJson(minimalResponse)
                
                if (directionsResponseJson.length > 50_000) {
                    // Голям JSON - запази във файл
                    try {
                        NavigationDataCache.saveDirectionsResponse(this, directionsResponseJson)
                        directionsResponseInCache = true
                    } catch (e: Exception) {
                        Log.e("RoutePreview", "Failed to cache directions response", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RoutePreview", "Error serializing route: ${e.message}")
        }
        
        // Pass route data to MainActivity with fresh GPS coordinates
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigation_active", true)
            putExtra("SELECTED_PROFILE", currentProfile) // КРИТИЧНО: Предаваме правилния профил!
            putExtra("origin_latitude", originLat)
            putExtra("origin_longitude", originLon)
            putExtra("origin_bearing", bearingToUse)
            putExtra("destination_latitude", destinationLat)
            putExtra("destination_longitude", destinationLon)
            putExtra("destination_name", destinationName)
            putExtra("route_distance", routeDistance)
            putExtra("route_duration", routeDuration)
            
            // Предай route geometry само ако е малък, иначе използвай флаг за файл
            if (geometryInCache) {
                putExtra("route_geometry_in_cache", true)
            } else {
                putExtra("route_geometry", geometryJson)
            }
            
            // Предай directions response само ако не е в cache
            if (!directionsResponseInCache && directionsResponseJson != null) {
                putExtra("directions_response_json", directionsResponseJson)
            } else if (directionsResponseInCache) {
                putExtra("directions_response_in_cache", true)
            }
        }
        startActivity(intent)
        finish()
    }
    
    override fun onPause() {
        super.onPause()
        // Изчисти navigation data cache ако Activity е паузиран
        try {
            NavigationDataCache.clear(this)
        } catch (e: Exception) {
            Log.e("RoutePreview", "Failed to clear navigation cache", e)
        }
    }
    
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(500)
            .build()
        
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        Log.d("RoutePreview", "📍 Started GPS tracking for instant navigation")
    }
    
    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d("RoutePreview", "📍 Stopped GPS tracking")
        } catch (e: Exception) {
            Log.e("RoutePreview", "Error stopping location updates", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }
    
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        
        val y = Math.sin(dLon) * Math.cos(lat2Rad)
        val x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLon)
        
        var bearing = Math.toDegrees(Math.atan2(y, x)).toFloat()
        if (bearing < 0) bearing += 360f
        return bearing
    }
}
