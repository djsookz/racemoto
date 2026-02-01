package com.example.clinometer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import java.text.DecimalFormat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.clinometer.settings.MapProviderManager
import com.mapbox.geojson.Point as MapboxPoint
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.MapView as MapboxMapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.addOnMapLongClickListener
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.example.clinometer.navigation.MapboxGeocodingService
import com.example.clinometer.navigation.GeocodingFeature
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.data.ProfileStorage
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.clinometer.settings.UnitsManager
import com.example.clinometer.network.OpenMeteoService
import com.example.clinometer.network.WeatherApiService
import com.example.clinometer.utils.WeatherIconMapper
import com.mapbox.geojson.LineString
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.common.location.Location as MapboxLocation
import com.example.clinometer.MainContainerActivity

@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
class MapFragment : Fragment() {
    
    // Map views
    private var mapboxMapView: MapboxMapView? = null // Mapbox MapView (nullable)
    
    // UI Elements
    private lateinit var btnStartSession: MaterialButton
    private lateinit var btnSessions: MaterialButton
    private lateinit var destinationSearchContainer: LinearLayout
    private lateinit var searchContainer: LinearLayout
    private lateinit var searchInputContainer: LinearLayout
    private lateinit var etSearch: TextInputEditText
    private lateinit var rvSearchResults: RecyclerView
    private lateinit var searchResultsAdapter: SearchResultsAdapter
    private lateinit var geocodingService: MapboxGeocodingService
    private var mapboxAccessToken: String = ""
    private lateinit var routeInfoContainer: LinearLayout
    private lateinit var tvDestinationName: TextView
    private lateinit var tvRouteDistance: TextView
    private lateinit var tvRouteDuration: TextView
    private lateinit var btnStartNavigation: com.google.android.material.button.MaterialButton // Keep for compatibility
    private lateinit var btnNavigateRoute: com.google.android.material.button.MaterialButton
    private lateinit var btnCancelRoute: com.google.android.material.button.MaterialButton
    private lateinit var routePreviewBottomContainer: LinearLayout
    private lateinit var btnSearchRoute: ImageButton
    private lateinit var btnMotorwayOptions: ImageButton
    private lateinit var motorwayOptionsContainer: LinearLayout
    private lateinit var btnWithMotorways: ImageButton
    private lateinit var btnWithoutMotorways: ImageButton
    private var btnOverview: ImageButton? = null
    private var btnRecenter: ImageButton? = null
    private var allowMotorways: Boolean = false
    private lateinit var llTemperature: LinearLayout
    private lateinit var llWeatherExpanded: TextView
    private lateinit var tvSeparator: TextView
    private lateinit var llAltitude: LinearLayout
    private lateinit var llAltitudeExpanded: TextView
    private lateinit var tvAltSeparator: TextView
    private lateinit var ivWeatherIcon: ImageView
    private lateinit var tvTemperature: TextView
    private lateinit var tvAltitude: TextView
    private lateinit var fabMyLocationContainer: FrameLayout
    private lateinit var tvHeaderModelName: TextView
    private lateinit var ivHeaderProfileImage: ImageView
    private lateinit var llActiveProfileHeader: LinearLayout
    
    // Inline route preview (draw routes on the same map, like TestNavigationActivity)
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView
    private var currentMapboxStyle: Style? = null
    private var currentDestination: Point? = null
    private var currentDestinationName: String? = null

    // Route cache: exactly 1 request for allowMotorways=true and 1 for allowMotorways=false per (fixedOrigin,destination)
    private var fixedOriginForRoute: Point? = null
    private var routeCacheKey: String? = null
    private var cachedRoutesAllowMotorways: List<NavigationRoute>? = null
    private var cachedRoutesNoMotorways: List<NavigationRoute>? = null
    private var routeRequestInFlightForAllowMotorways: Boolean? = null

    // Alternative route selection (like TestNavigationActivity)
    // Keep original order stable so route numbering (1..N) stays consistent even after we reorder for rendering.
    private var currentRoutesOriginal: List<NavigationRoute> = emptyList()
    private var selectedRouteIndex: Int = 0

    // NavigationCamera overview animation (same behaviour as TestNavigationActivity)
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private val pixelDensity = Resources.getSystem().displayMetrics.density
    // Adjust padding based on orientation - smaller padding for landscape to prevent zooming too far
    private val overviewPadding: EdgeInsets by lazy {
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) {
            EdgeInsets(80.0 * pixelDensity, 40.0 * pixelDensity, 80.0 * pixelDensity, 40.0 * pixelDensity)
        } else {
            EdgeInsets(140.0 * pixelDensity, 40.0 * pixelDensity, 120.0 * pixelDensity, 40.0 * pixelDensity)
        }
    }
    private val followingPadding = EdgeInsets(180.0 * pixelDensity, 40.0 * pixelDensity, 150.0 * pixelDensity, 40.0 * pixelDensity)
    
    // Mapbox location component (SDK)
    private var isMapboxLocationComponentEnabled: Boolean = false
    private var isUsingNavigationLocationProvider: Boolean = false

    // Mapbox Navigation SDK (used for map-matched "enhancedLocation" snapping, like TestNavigationActivity)
    private val navigationLocationProvider = NavigationLocationProvider()
    // (Turn-by-turn UI is handled in MainActivity; MapFragment stays as search + route preview)
    private val mapboxNavigation: MapboxNavigation by requireMapboxNavigation(
        onResumedObserver = object : MapboxNavigationObserver {
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.registerLocationObserver(navigationLocationObserver)
                mapboxNavigation.startTripSession()
            }

            override fun onDetached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.unregisterLocationObserver(navigationLocationObserver)
                mapboxNavigation.stopTripSession()
            }
        },
        onInitialize = this::initNavigation
    )
    private val navigationLocationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: com.mapbox.common.location.Location) {}

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhanced = locationMatcherResult.enhancedLocation

            // This is the "snapped" location (map matched) - feed it to the map puck
            navigationLocationProvider.changePosition(enhanced, locationMatcherResult.keyPoints)

            // Switch the map's location provider to the navigation provider on first enhanced update.
            // Until then, we keep the default provider so the user still sees a raw GPS puck quickly.
            if (!isUsingNavigationLocationProvider) {
                mapboxMapView?.location?.setLocationProvider(navigationLocationProvider)
                isUsingNavigationLocationProvider = true
                Log.d("MapFragment", "✅ Switched puck to NavigationLocationProvider (snapped)")
            }

            // Also maintain android.location.Location for the rest of this screen (weather, caching, camera)
            val androidLoc = Location("mapbox").apply {
                latitude = enhanced.latitude
                longitude = enhanced.longitude
                time = System.currentTimeMillis()
                // best-effort extras
                enhanced.speed?.let { speed = it.toFloat() }
                enhanced.bearing?.let { bearing = it.toFloat() }
                enhanced.altitude?.let { altitude = it }
            }

            currentLocation = androidLoc
            mapStateViewModel.saveLastLocation(androidLoc)

            // (Turn-by-turn UI is handled in MainActivity)

            // Camera init/restore
            if (!mapStateViewModel.hasInitializedCamera) {
                mapStateViewModel.hasInitializedCamera = true

                val savedState = mapStateViewModel.lastMapState
                if (savedState != null) {
                    mapboxMapView?.mapboxMap?.setCamera(
                        CameraOptions.Builder()
                            .center(MapboxPoint.fromLngLat(savedState.centerLon, savedState.centerLat))
                            .zoom(savedState.zoom)
                            .pitch(savedState.pitch ?: 45.0)
                            .build()
                    )
                } else {
                    mapboxMapView?.mapboxMap?.setCamera(
                        CameraOptions.Builder()
                            .center(MapboxPoint.fromLngLat(androidLoc.longitude, androidLoc.latitude))
                            .zoom(17.0)
                            .build()
                    )
                    mapStateViewModel.saveMapState(androidLoc.latitude, androidLoc.longitude, 17.0, 45.0)
                }
            }

            // Weather fetch (only if needed)
            if (isAdded && context != null && shouldFetchWeatherData(androidLoc)) {
                fetchWeatherFromAPI(androidLoc)
            }
        }
    }
    
    // State
    private var isWeatherExpanded = false
    private var isAltitudeExpanded = false
    private var altitudeCollapsedWidth = -1
    private var currentLocation: Location? = null
    private var currentTemperature: Float? = null
    private var currentAltitude: Float? = null
    private lateinit var mapStateViewModel: MapStateViewModel
    private var currentWeatherIcon: Int = R.drawable.ic_thermometer
    
    // Weather details
    private var currentWindKph: Double = 0.0
    private var currentWindDir: String = ""
    private var currentHumidity: Int = 0
    private var currentCloudCover: Int = 0
    private var rainChance3h: Int = 0
    private var rainTimeText: String = ""
    private var currentPressure: Double = 0.0
    
    private val handler = Handler(Looper.getMainLooper())
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val LOCATION_UPDATE_INTERVAL = 100L
        private const val LOCATION_FASTEST_UPDATE_INTERVAL = 100L
        private const val MY_LOCATION_ZOOM = 17.0
        private const val CACHE_LOCATION_THRESHOLD_KM = 5.0
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (ProfileStorage.loadProfiles(requireContext()).isEmpty()) {
            startActivity(Intent(requireContext(), WelcomeActivity::class.java))
            requireActivity().finish()
            return
        }
        
        mapStateViewModel = ViewModelProvider(this)[MapStateViewModel::class.java]
        
        val mapProvider = MapProviderManager.getMapProvider(requireContext())
        // Always use Mapbox
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_main_map, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Hide any OSMDroid mapView if it exists (legacy support)
        val osmdroidMapView = view.findViewById<android.view.View>(R.id.mapView)
        osmdroidMapView?.let {
            it.visibility = View.GONE
            if (it.parent != null) {
                (it.parent as? ViewGroup)?.removeView(it)
            }
        }
        
        setupMapboxMap(view)
        
        // Initialize UI elements
        btnStartSession = view.findViewById(R.id.btnStartNavigationNoDestination)
        btnSessions = view.findViewById(R.id.btnSessions)
        
        destinationSearchContainer = view.findViewById(R.id.destinationSearchContainer)
        searchContainer = view.findViewById(R.id.searchContainer)
        searchInputContainer = view.findViewById(R.id.searchInputContainer)
        etSearch = view.findViewById(R.id.etSearch)
        rvSearchResults = view.findViewById(R.id.rvSearchResults)
        tvHeaderModelName = view.findViewById(R.id.tvHeaderModelName)
        ivHeaderProfileImage = view.findViewById(R.id.ivHeaderProfileImage)
        llActiveProfileHeader = view.findViewById(R.id.llActiveProfileHeader)

        routeInfoContainer = view.findViewById(R.id.routeInfoContainer)
        tvDestinationName = view.findViewById(R.id.tvDestinationName)
        tvRouteDistance = view.findViewById(R.id.tvRouteDistance)
        tvRouteDuration = view.findViewById(R.id.tvRouteDuration)
        btnStartNavigation = view.findViewById(R.id.btnStartNavigation) // Keep for compatibility
        btnNavigateRoute = view.findViewById(R.id.btnNavigateRoute)
        btnCancelRoute = view.findViewById(R.id.btnCancelRoute)
        routePreviewBottomContainer = view.findViewById(R.id.routePreviewBottomContainer)
        btnSearchRoute = view.findViewById(R.id.btnSearchRoute)
        btnMotorwayOptions = view.findViewById(R.id.btnMotorwayOptions)
        motorwayOptionsContainer = view.findViewById(R.id.motorwayOptionsContainer)
        btnWithMotorways = view.findViewById(R.id.btnWithMotorways)
        btnWithoutMotorways = view.findViewById(R.id.btnWithoutMotorways)
        
        // Initialize camera control buttons
        btnOverview = view.findViewById(R.id.btnOverview)
        btnRecenter = view.findViewById(R.id.btnRecenter)

        // Load motorway preference
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        allowMotorways = prefs.getBoolean("allow_motorways", false)
        updateMotorwayButtonIcon()

        setupInlineSearchUI()
        setupRoutePreviewUi()
        
        llTemperature = view.findViewById(R.id.llTemperature)
        llWeatherExpanded = view.findViewById(R.id.llWeatherExpanded)
        tvSeparator = view.findViewById(R.id.tvSeparator)
        llAltitude = view.findViewById(R.id.llAltitude)
        llAltitudeExpanded = view.findViewById(R.id.llAltitudeExpanded)
        tvAltSeparator = view.findViewById(R.id.tvAltSeparator)
        ivWeatherIcon = view.findViewById(R.id.ivWeatherIcon)
        tvTemperature = view.findViewById(R.id.tvTemperature)
        tvAltitude = view.findViewById(R.id.tvAltitude)
        
        adjustMarginsForLandscapeNavigation(view)
        fabMyLocationContainer = view.findViewById(R.id.fabMyLocationContainer)
        
        // Mapbox mode uses Mapbox Location Component
        
        btnStartSession.setOnClickListener { startNormalSession() }
        btnSessions.setOnClickListener { navigateToSessions() }
        fabMyLocationContainer.setOnClickListener { centerOnCurrentLocation() }
        llTemperature.setOnClickListener { toggleWeatherExpansion() }
        llAltitude.setOnClickListener { toggleAltitudeExpansion() }
        
        val bottomContainer = view.findViewById<LinearLayout>(R.id.bottomContainer)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(bottomContainer) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom)
            insets
        }
        
        loadCachedWeatherData()
        updateEnvironmentDisplay()
        
        // Init route line rendering (Mapbox mode only)
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

        routeLineApi = MapboxRouteLineApi(
            MapboxRouteLineApiOptions.Builder()
                .vanishingRouteLineEnabled(false)
                .isRouteCalloutsEnabled(false)
                .build()
        )

        routeLineView = MapboxRouteLineView(
            MapboxRouteLineViewOptions.Builder(requireContext())
                .routeLineBelowLayerId("road-label")
                .routeLineColorResources(routeLineColorResources)
                .displaySoftGradientForTraffic(false)
                .build()
        )

        // Location updates: Mapbox Location Component drives updates (mapView.location)
        
        if (!checkLocationPermission()) {
            requestLocationPermission()
        } else {
            retrieveCurrentLocation()
        }
        displayLastKnownLocationInstantly()
    }

    private fun setupInlineSearchUI() {
        // Token + Retrofit (same approach as TestNavigationActivity)
        try {
            val resourceId = resources.getIdentifier("mapbox_access_token", "string", requireContext().packageName)
            mapboxAccessToken = resources.getString(resourceId)
        } catch (_: Resources.NotFoundException) {
            Toast.makeText(requireContext(), "Mapbox token не е намерен", Toast.LENGTH_SHORT).show()
            mapboxAccessToken = ""
        }

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.mapbox.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        geocodingService = retrofit.create(MapboxGeocodingService::class.java)

        searchResultsAdapter = SearchResultsAdapter { feature ->
            selectDestinationInline(feature)
        }
        rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        rvSearchResults.adapter = searchResultsAdapter

        // Tap on collapsed pill -> expand inline search
        destinationSearchContainer.setOnClickListener { showInlineSearch() }
        searchInputContainer.setOnClickListener { showInlineSearch() }

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s?.toString()?.trim().orEmpty()
                if (q.length >= 2) {
                    performInlineSearch(q)
                } else {
                    searchResultsAdapter.updateResults(emptyList())
                    rvSearchResults.visibility = View.GONE
                }
            }
        })
    }

    private fun showInlineSearch() {
        destinationSearchContainer.visibility = View.GONE
        searchContainer.visibility = View.VISIBLE
        etSearch.requestFocus()
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideInlineSearch() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
        rvSearchResults.visibility = View.GONE
        searchContainer.visibility = View.GONE
        destinationSearchContainer.visibility = View.VISIBLE
    }

    private fun performInlineSearch(query: String) {
        if (mapboxAccessToken.isBlank()) return
        lifecycleScope.launch {
            try {
                val proximity = currentLocation?.let { "${it.longitude},${it.latitude}" }
                val response = withContext(Dispatchers.IO) {
                    geocodingService.searchPlaces(query, mapboxAccessToken, proximity, 10)
                }
                if (response.isSuccessful && response.body() != null) {
                    val features = response.body()!!.features
                    searchResultsAdapter.updateResults(features)
                    rvSearchResults.visibility = if (features.isNotEmpty()) View.VISIBLE else View.GONE
                } else {
                    searchResultsAdapter.updateResults(emptyList())
                    rvSearchResults.visibility = View.GONE
                }
            } catch (e: Exception) {
                searchResultsAdapter.updateResults(emptyList())
                rvSearchResults.visibility = View.GONE
            }
        }
    }

    private fun selectDestinationInline(feature: GeocodingFeature) {
        // Update the collapsed pill text to destination name
        val tv = destinationSearchContainer.findViewById<TextView>(R.id.tvDestinationPlaceholder)
        currentDestinationName = feature.placeName
        tv.text = currentDestinationName ?: getString(R.string.destination_placeholder)
        hideInlineSearch()

        val center = feature.center
        if (center.size >= 2) {
            val destination = Point.fromLngLat(center[0], center[1])
            setDestinationAndFindRoute(destination, currentDestinationName)
        }
    }
    
    private fun setDestinationAndFindRoute(destination: Point, destinationName: String?) {
        currentDestination = destination
        currentDestinationName = destinationName
        tvDestinationName.text = destinationName ?: "Дестинация"
        
        // Update collapsed pill text
        val tv = destinationSearchContainer.findViewById<TextView>(R.id.tvDestinationPlaceholder)
        tv.text = destinationName ?: getString(R.string.destination_placeholder)

        // Fix origin for this destination selection (so caches remain valid when GPS updates).
        // If currentLocation is not available yet, we'll fall back to currentLocation inside findRouteInline().
        fixedOriginForRoute = currentLocation?.let { loc ->
            Point.fromLngLat(loc.longitude, loc.latitude)
        }
        resetRouteCacheForNewSelection()
        findRouteInline(destination)
    }
    
    private fun handleLongPressDestination(point: com.mapbox.geojson.Point) {
        if (mapboxAccessToken.isBlank()) {
            Toast.makeText(requireContext(), "Mapbox token not available", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show loading indicator
        val loadingToast = Toast.makeText(requireContext(), "Търсене на локация...", Toast.LENGTH_SHORT)
        loadingToast.show()
        
        // Perform reverse geocoding to get place name
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    geocodingService.reverseGeocode(
                        point.longitude(),
                        point.latitude(),
                        mapboxAccessToken,
                        1
                    )
                }
                
                if (response.isSuccessful && response.body() != null) {
                    val features = response.body()!!.features
                    if (features.isNotEmpty()) {
                        val feature = features.first()
                        val destinationName = feature.placeName ?: feature.text ?: "Избрана локация"
                        
                        // Set destination and find route
                        withContext(Dispatchers.Main) {
                            loadingToast.cancel()
                            setDestinationAndFindRoute(point, destinationName)
                            Toast.makeText(requireContext(), "Дестинация зададена: $destinationName", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            loadingToast.cancel()
                            // If no place name found, use coordinates
                            val destinationName = String.format(
                                java.util.Locale.getDefault(),
                                "%.5f, %.5f",
                                point.latitude(),
                                point.longitude()
                            )
                            setDestinationAndFindRoute(point, destinationName)
                            Toast.makeText(requireContext(), "Дестинация зададена", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        loadingToast.cancel()
                        // Fallback: use coordinates as name
                        val destinationName = String.format(
                            java.util.Locale.getDefault(),
                            "%.5f, %.5f",
                            point.latitude(),
                            point.longitude()
                        )
                        setDestinationAndFindRoute(point, destinationName)
                        Toast.makeText(requireContext(), "Дестинация зададена", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MapFragment", "Error reverse geocoding", e)
                withContext(Dispatchers.Main) {
                    loadingToast.cancel()
                    // Fallback: use coordinates as name
                    val destinationName = String.format(
                        java.util.Locale.getDefault(),
                        "%.5f, %.5f",
                        point.latitude(),
                        point.longitude()
                    )
                    setDestinationAndFindRoute(point, destinationName)
                    Toast.makeText(requireContext(), "Дестинация зададена", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun resetRouteCacheForNewSelection() {
        routeCacheKey = null
        cachedRoutesAllowMotorways = null
        cachedRoutesNoMotorways = null
        routeRequestInFlightForAllowMotorways = null
    }

    private fun buildRouteCacheKey(origin: Point, destination: Point): String {
        // Round to reduce cache invalidation due to tiny GPS jitter
        fun Double.r5() = String.format(java.util.Locale.US, "%.5f", this)
        return "${origin.longitude().r5()},${origin.latitude().r5()}|${destination.longitude().r5()},${destination.latitude().r5()}"
    }

    private fun setupRoutePreviewUi() {
        // Navigate button: start turn-by-turn navigation
        btnNavigateRoute.setOnClickListener { 
            startNavigationInMainActivityWithTransfer() 
        }
        
        // Cancel button: clear route and return to initial state (like first time entering the page)
        btnCancelRoute.setOnClickListener {
            // Clear search input and hide search container
            etSearch.text?.clear()
            hideInlineSearch()
            
            // Reset destination placeholder text
            val tv = destinationSearchContainer.findViewById<TextView>(R.id.tvDestinationPlaceholder)
            tv.text = getString(R.string.destination_placeholder)
            
            // Show profile info again
            if (::llActiveProfileHeader.isInitialized) {
                llActiveProfileHeader.visibility = View.VISIBLE
            }
            
            // Clear current route data
            currentDestination = null
            currentDestinationName = null
            resetRouteCacheForNewSelection()
            
            // Hide route preview UI
            routeInfoContainer.visibility = View.GONE
            routePreviewBottomContainer.visibility = View.GONE
            btnMotorwayOptions.visibility = View.GONE
            btnSearchRoute.visibility = View.GONE
            motorwayOptionsContainer.visibility = View.GONE
            
            // Clear route line from map
            if (::routeLineApi.isInitialized && ::routeLineView.isInitialized && currentMapboxStyle != null) {
                routeLineApi.clearRouteLine { value ->
                    routeLineView.renderClearRouteLineValue(currentMapboxStyle!!, value)
                }
            }
            
            // Animate camera back to current location using NavigationCamera (like SDK does)
            currentLocation?.let { androidLocation ->
                if (this::viewportDataSource.isInitialized && this::navigationCamera.isInitialized) {
                    // Clear route data from viewport data source
                    viewportDataSource.clearRouteData()
                    // Animate camera to current location with pitch 0 using easeTo (like renderRoutesInline does)
                    currentLocation?.let { location ->
                        val currentCameraState = mapboxMapView?.mapboxMap?.cameraState
                        currentCameraState?.let { state ->
                            val cameraOptions = CameraOptions.Builder()
                                .center(MapboxPoint.fromLngLat(location.longitude, location.latitude))
                                .zoom(if (state.zoom in 15.0..18.0) state.zoom else 17.0) // Reasonable zoom range
                                .bearing(state.bearing)
                                .pitch(0.0) // Always pitch 0 when canceling route
                                .build()
                            mapboxMapView?.mapboxMap?.let { mapboxMap ->
                                mapboxMap.easeTo(cameraOptions)
                            }
                        }
                    }
                }
            }
            
            // Show initial state UI (like first time entering)
            destinationSearchContainer.visibility = View.VISIBLE
            view?.findViewById<LinearLayout>(R.id.bottomContainer)?.visibility = View.VISIBLE
            llTemperature.visibility = View.VISIBLE
            llAltitude.visibility = View.VISIBLE
            fabMyLocationContainer.visibility = View.VISIBLE
            
            // Show bottom navigation
            requireActivity().findViewById<View>(R.id.bottomNavigationContainer)?.visibility = View.VISIBLE
        }

        // Search button: return to search to find new destination
        btnSearchRoute.setOnClickListener {
            // Clear current route and show search interface
            currentDestination = null
            currentDestinationName = null
            routeInfoContainer.visibility = View.GONE
            routePreviewBottomContainer.visibility = View.GONE
            btnMotorwayOptions.visibility = View.GONE
            btnSearchRoute.visibility = View.GONE
            motorwayOptionsContainer.visibility = View.GONE
            // Clear route line
            if (::routeLineApi.isInitialized && ::routeLineView.isInitialized && currentMapboxStyle != null) {
                routeLineApi.clearRouteLine { value ->
                    routeLineView.renderClearRouteLineValue(currentMapboxStyle!!, value)
                }
            }
            // Reset route cache
            resetRouteCacheForNewSelection()
            // Show search interface
            showInlineSearch()
            // Show initial bottom container
            view?.findViewById<LinearLayout>(R.id.bottomContainer)?.visibility = View.VISIBLE
            // Show bottom navigation
            requireActivity().findViewById<View>(R.id.bottomNavigationContainer)?.visibility = View.VISIBLE
        }

        btnMotorwayOptions.setOnClickListener {
            val isVisible = motorwayOptionsContainer.visibility == View.VISIBLE
            motorwayOptionsContainer.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        btnWithMotorways.setOnClickListener {
            if (!allowMotorways) {
                allowMotorways = true
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit().putBoolean("allow_motorways", true).apply()
                updateMotorwayButtonIcon()
                motorwayOptionsContainer.visibility = View.GONE
                currentDestination?.let { findRouteInline(it) }
            }
        }

        btnWithoutMotorways.setOnClickListener {
            if (allowMotorways) {
                allowMotorways = false
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit().putBoolean("allow_motorways", false).apply()
                updateMotorwayButtonIcon()
                motorwayOptionsContainer.visibility = View.GONE
                currentDestination?.let { findRouteInline(it) }
            }
        }
    }

    private fun updateMotorwayButtonIcon() {
        val iconRes = if (allowMotorways) R.drawable.ic_motorway else R.drawable.ic_road
        btnMotorwayOptions.setImageResource(iconRes)
    }
    
    private fun setupNavigationCameraButtons() {
        // Show camera control buttons when route is displayed
        btnOverview?.setOnClickListener {
            if (this::viewportDataSource.isInitialized && this::navigationCamera.isInitialized) {
                // Request overview state to show entire route
                mapboxMapView?.post {
                    navigationCamera.requestNavigationCameraToOverview()
                }
            }
        }
        
        btnRecenter?.setOnClickListener {
            // Use easeTo to animate camera to current location with pitch 0
            currentLocation?.let { location ->
                val currentCameraState = mapboxMapView?.mapboxMap?.cameraState
                currentCameraState?.let { state ->
                    val cameraOptions = CameraOptions.Builder()
                        .center(MapboxPoint.fromLngLat(location.longitude, location.latitude))
                        .zoom(if (state.zoom in 15.0..18.0) state.zoom else 17.0)
                        .bearing(state.bearing)
                        .pitch(0.0)
                        .build()
                    mapboxMapView?.mapboxMap?.easeTo(cameraOptions)
                }
            }
        }
        
        // Initially hide buttons - they will be shown when route is displayed
        btnOverview?.visibility = View.GONE
        btnRecenter?.visibility = View.GONE
    }

    private fun startNavigationInMainActivityWithTransfer() {
        // Always Mapbox mode

        val dest = currentDestination
        if (dest == null) {
            Toast.makeText(requireContext(), "Няма избрана дестинация", Toast.LENGTH_SHORT).show()
            return
        }

        val origin = fixedOriginForRoute ?: currentLocation?.let { loc ->
            Point.fromLngLat(loc.longitude, loc.latitude)
        }
        if (origin == null) {
            Toast.makeText(requireContext(), "Няма текуща локация", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Animate camera to current location before starting navigation (like SDK does)
        // Use easeTo for smooth animation to current location
        currentLocation?.let { location ->
            val currentCameraState = mapboxMapView?.mapboxMap?.cameraState
            currentCameraState?.let { state ->
                val cameraOptions = CameraOptions.Builder()
                    .center(MapboxPoint.fromLngLat(location.longitude, location.latitude))
                    .zoom(if (state.zoom in 15.0..18.0) state.zoom else 17.0)
                    .bearing(state.bearing)
                    .pitch(0.0)
                    .build()
                mapboxMapView?.mapboxMap?.easeTo(cameraOptions)
            }
        }

        // Use the route the user selected (from the original list), not always the current SDK primary.
        // MainActivity re-requests routes, so we also pass a "preferred" route signature (polyline) for re-selection.
        val selectedRoute = currentRoutesOriginal.getOrNull(selectedRouteIndex)
            ?: mapboxNavigation.getNavigationRoutes().firstOrNull()
        val routeGeometryJson: String? = try {
            val geometry = selectedRoute?.directionsRoute?.geometry().orEmpty()
            if (geometry.isBlank()) null else {
                // polyline6 is default; fallback to 5
                try {
                    com.mapbox.geojson.LineString.fromPolyline(geometry, 6).toJson()
                } catch (_: Throwable) {
                    com.mapbox.geojson.LineString.fromPolyline(geometry, 5).toJson()
                }
            }
        } catch (_: Throwable) {
            null
        }
        val preferredRoutePolyline: String? = selectedRoute?.directionsRoute?.geometry()

        val cameraState = mapboxMapView?.mapboxMap?.cameraState

        // Pass selected profile so MainActivity preserves CAR/MOTO UI logic and saves the session under the correct profile.
        val selectedProfileId = ProfileStorage.getSelectedProfileId(requireContext())
        val selectedProfile = ProfileStorage.loadProfiles(requireContext())
            .firstOrNull { it.id == selectedProfileId }
            ?: ProfileStorage.loadProfiles(requireContext()).firstOrNull()

        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            selectedProfile?.let { putExtra("SELECTED_PROFILE", it) }
            putExtra("navigation_active", true)
            putExtra("destination_latitude", dest.latitude())
            putExtra("destination_longitude", dest.longitude())
            putExtra("destination_name", currentDestinationName ?: tvDestinationName.text?.toString().orEmpty())
            putExtra("origin_latitude", origin.latitude())
            putExtra("origin_longitude", origin.longitude())
            putExtra("origin_bearing", currentLocation?.bearing ?: 0f)
            putExtra("allow_motorways", allowMotorways)

            // Transfer current preview camera so MainActivity doesn't do a jumpy zoom-out.
            cameraState?.let {
                putExtra("nav_camera_center_lat", it.center.latitude())
                putExtra("nav_camera_center_lon", it.center.longitude())
                putExtra("nav_camera_zoom", it.zoom)
                putExtra("nav_camera_bearing", it.bearing)
                putExtra("nav_camera_pitch", it.pitch)
            }

            // ВАЖНО: Запази големите данни във файлове вместо в Intent за да избегнем TransactionTooLargeException
            val geometryInCache = if (routeGeometryJson != null && routeGeometryJson.length > 50_000) {
                // Голям маршрут - запази във файл
                try {
                    NavigationDataCache.saveRouteGeometry(requireContext(), routeGeometryJson)
                    true
                } catch (e: Exception) {
                    android.util.Log.e("MapFragment", "Failed to cache route geometry", e)
                    false
                }
        } else {
                // Малък маршрут - може да се предаде директно
                false
            }
            
            // Предай route geometry само ако е малък, иначе използвай флаг за файл
            if (geometryInCache) {
                putExtra("route_geometry_in_cache", true)
            } else {
                routeGeometryJson?.let { putExtra("route_geometry", it) }
            }
            
            // Also transfer the selected route's polyline so MainActivity can re-select that alternative after re-requesting routes.
            preferredRoutePolyline?.let { putExtra("preferred_route_polyline", it) }

            // Tell MainActivity it is coming from the preview screen (skip initial overview).
            putExtra("nav_start_from_preview", true)
        }
        startActivity(intent)
        // Remove transition animation for seamless page change
        requireActivity().overridePendingTransition(0, 0)
    }

    private fun setCompactRouteMode(enabled: Boolean) {
        // Hide/show map screen UI to match TestNavigationActivity behaviour after destination selection
        llTemperature.visibility = if (enabled) View.GONE else View.VISIBLE
        llAltitude.visibility = if (enabled) View.GONE else View.VISIBLE
        fabMyLocationContainer.visibility = if (enabled) View.GONE else View.VISIBLE

        // Hide bottom container (sessions/start)
        view?.findViewById<LinearLayout>(R.id.bottomContainer)?.visibility = if (enabled) View.GONE else View.VISIBLE

        // Bottom navigation should always be visible (handled by MainContainerActivity)
        // We don't hide it when route preview is shown

        destinationSearchContainer.visibility = if (enabled) View.GONE else View.VISIBLE
        searchContainer.visibility = View.GONE
        if (::llActiveProfileHeader.isInitialized) {
            llActiveProfileHeader.visibility = if (enabled) View.GONE else View.VISIBLE
        }

        routeInfoContainer.visibility = if (enabled) View.VISIBLE else View.GONE
        routePreviewBottomContainer.visibility = if (enabled) View.VISIBLE else View.GONE
        btnSearchRoute.visibility = if (enabled) View.VISIBLE else View.GONE
        btnMotorwayOptions.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled) {
            motorwayOptionsContainer.visibility = View.GONE
        }

        // Ensure overlays are above the mapContainer (MapView is declared later in XML)
        if (enabled) {
            routeInfoContainer.bringToFront()
            routePreviewBottomContainer.bringToFront()
            btnSearchRoute.bringToFront()
            btnMotorwayOptions.bringToFront()
            motorwayOptionsContainer.bringToFront()
        }
    }

    private fun findRouteInline(destination: Point) {
        // Always Mapbox mode
        if (!this::routeLineApi.isInitialized || !this::routeLineView.isInitialized) {
            Toast.makeText(requireContext(), "Картата още се зарежда…", Toast.LENGTH_SHORT).show()
            return
        }

        val originPoint = fixedOriginForRoute ?: run {
            val originLoc = currentLocation
            if (originLoc != null) {
                Point.fromLngLat(originLoc.longitude, originLoc.latitude)
            } else null
        }

        if (originPoint == null) {
            Toast.makeText(requireContext(), "Няма текуща локация", Toast.LENGTH_SHORT).show()
            return
        }

        val key = buildRouteCacheKey(originPoint, destination)
        if (routeCacheKey != key) {
            // new origin/destination pair -> clear caches
            routeCacheKey = key
            cachedRoutesAllowMotorways = null
            cachedRoutesNoMotorways = null
            routeRequestInFlightForAllowMotorways = null
        }

        val cached = if (allowMotorways) cachedRoutesAllowMotorways else cachedRoutesNoMotorways
        if (!cached.isNullOrEmpty()) {
            selectedRouteIndex = 0
            currentRoutesOriginal = cached
            renderRoutesInline(routesToRender = cached, originalRoutes = cached)
            return
        }

        // Avoid spamming requests if user toggles rapidly
        if (routeRequestInFlightForAllowMotorways == allowMotorways) return
        routeRequestInFlightForAllowMotorways = allowMotorways

        // Clear previous route line
        currentMapboxStyle?.let { style ->
            routeLineApi.clearRouteLine { value ->
                routeLineView.renderClearRouteLineValue(style, value)
            }
        }

        val routeOptionsBuilder = RouteOptions.builder()
            .applyDefaultNavigationOptions()
            .applyLanguageAndVoiceUnitOptions(requireContext())
            .coordinatesList(listOf(originPoint, destination))
            .alternatives(true)

        if (!allowMotorways) {
            routeOptionsBuilder.exclude("motorway")
        }

        val routeOptions = routeOptionsBuilder.build()

        mapboxNavigation.requestRoutes(
            routeOptions,
            object : NavigationRouterCallback {
                override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                    routeRequestInFlightForAllowMotorways = null
                    if (routes.isEmpty()) return

                    // Cache results for future toggles
                    if (allowMotorways) {
                        cachedRoutesAllowMotorways = routes
                    } else {
                        cachedRoutesNoMotorways = routes
                    }

                    selectedRouteIndex = 0
                    currentRoutesOriginal = routes
                    renderRoutesInline(routesToRender = routes, originalRoutes = routes)
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    routeRequestInFlightForAllowMotorways = null
                    Toast.makeText(requireContext(), "Грешка при намиране на маршрут", Toast.LENGTH_SHORT).show()
                }

                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                    routeRequestInFlightForAllowMotorways = null
                }
            }
        )
    }

    private fun renderRoutesInline(
        routesToRender: List<NavigationRoute>,
        originalRoutes: List<NavigationRoute> = routesToRender
    ) {
        if (routesToRender.isEmpty()) return
        currentRoutesOriginal = originalRoutes

        // Store routes in Navigation SDK (needed for alternative metadata)
        mapboxNavigation.setNavigationRoutes(routesToRender)

        val style = currentMapboxStyle ?: return
        val metadata = mapboxNavigation.getAlternativeMetadataFor(routesToRender)
        routeLineApi.setNavigationRoutes(routesToRender, metadata) { value ->
            routeLineView.renderRouteDrawData(style, value)

            // EXACT same "overview after render" behaviour as TestNavigationActivity
            // Use NavigationCamera for smooth SDK-like animation to show entire route
            if (this@MapFragment::viewportDataSource.isInitialized && this@MapFragment::navigationCamera.isInitialized) {
                val primary = routesToRender.first()
                
                // Provide route data to viewport data source
                viewportDataSource.onRouteChanged(primary)
                
                // Evaluate to generate camera targets
                viewportDataSource.evaluate()
                
                // Show camera control buttons when route is displayed
                btnOverview?.visibility = View.VISIBLE
                btnRecenter?.visibility = View.VISIBLE
                
                // Request overview state for smooth animation showing entire route
                mapboxMapView?.post {
                    navigationCamera.requestNavigationCameraToOverview()
                }
            }
        }

        // Update route info UI like TestNavigationActivity
        val primary = routesToRender.first()
        val distanceMeters = primary.directionsRoute.distance() ?: 0.0
        val durationSeconds = primary.directionsRoute.duration() ?: 0.0
        val distanceKm = distanceMeters / 1000.0
        val df = DecimalFormat("#.#")
        tvRouteDistance.text = "${df.format(distanceKm)} km"
        val minutes = (durationSeconds / 60).toInt()
        val h = minutes / 60
        val m = minutes % 60
        tvRouteDuration.text = if (h > 0) "${h}ч ${m}м" else "${m}м"

        setCompactRouteMode(true)
    }

    private fun handleRouteClick(clickPoint: com.mapbox.geojson.Point) {
        if (currentRoutesOriginal.size <= 1) return

        // Calculate distance from click to each route using decoded polyline geometry
        val routeDistances = mutableListOf<Pair<Int, Double>>()

        for (i in currentRoutesOriginal.indices) {
            val route = currentRoutesOriginal[i]
            var minDistanceForRoute = Double.MAX_VALUE

            val geometry = route.directionsRoute.geometry().orEmpty()
            if (geometry.isBlank()) continue

            val coordinates: List<com.mapbox.geojson.Point> = try {
                // Navigation SDK uses polyline6 by default
                LineString.fromPolyline(geometry, 6).coordinates()
            } catch (_: Throwable) {
                try {
                    LineString.fromPolyline(geometry, 5).coordinates()
                } catch (_: Throwable) {
                    emptyList()
                }
            }

            if (coordinates.isEmpty()) continue

            for (coord in coordinates) {
                val dx = clickPoint.longitude() - coord.longitude()
                val dy = clickPoint.latitude() - coord.latitude()
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                if (distance < minDistanceForRoute) {
                    minDistanceForRoute = distance
                }
            }

            if (minDistanceForRoute != Double.MAX_VALUE) {
                routeDistances.add(i to minDistanceForRoute)
            }
        }

        if (routeDistances.isEmpty()) return

        routeDistances.sortBy { it.second }
        val (closestIdx, closestDist) = routeDistances.first()

        // Same generous threshold as TestNavigationActivity (degrees)
        val selectionThreshold = 0.15
        if (closestIdx != selectedRouteIndex && closestDist < selectionThreshold) {
            selectedRouteIndex = closestIdx

            val reordered = mutableListOf<NavigationRoute>()
            reordered.add(currentRoutesOriginal[selectedRouteIndex])
            for (j in currentRoutesOriginal.indices) {
                if (j != selectedRouteIndex) reordered.add(currentRoutesOriginal[j])
            }

            // Re-render with selected route as primary (no network call), preserve original ordering for numbering
            renderRoutesInline(routesToRender = reordered, originalRoutes = currentRoutesOriginal)
            Toast.makeText(requireContext(), "Маршрут ${selectedRouteIndex + 1} избран", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMapboxMap(view: View) {
        val mapContainer = view.findViewById<FrameLayout>(R.id.mapContainer)
        // Hide any OSMDroid mapView if it exists (legacy support)
        val osmdroidMapView = view.findViewById<android.view.View>(R.id.mapView)
        osmdroidMapView?.let {
            it.visibility = View.GONE
            if (it.parent != null) {
                (it.parent as? ViewGroup)?.removeView(it)
            }
        }
        
        mapboxMapView = MapboxMapView(requireContext())
        mapboxMapView?.setBackgroundColor(Color.parseColor("#000000"))
        mapboxMapView?.alpha = 0f
        
        mapContainer.addView(mapboxMapView)

        // Setup NavigationCamera (same as TestNavigationActivity)
        mapboxMapView?.let { mv ->
            viewportDataSource = MapboxNavigationViewportDataSource(mv.mapboxMap).apply {
                overviewPadding = this@MapFragment.overviewPadding
                followingPadding = this@MapFragment.followingPadding
            }
            navigationCamera = NavigationCamera(mv.mapboxMap, mv.camera, viewportDataSource)
            mv.camera.addCameraAnimationsLifecycleListener(NavigationBasicGesturesHandler(navigationCamera))
            
            // Setup camera control buttons
            setupNavigationCameraButtons()

            // Enable alternative route selection by tapping on a route (same UX as TestNavigationActivity)
            mv.mapboxMap.addOnMapClickListener { clickPoint ->
                if (routeInfoContainer.visibility == View.VISIBLE && currentRoutesOriginal.size > 1) {
                    handleRouteClick(clickPoint)
                    true
                } else {
                    false
                }
            }
            
            // Enable long press to set destination (like Google Maps)
            mv.mapboxMap.addOnMapLongClickListener { longPressPoint ->
                // Only allow long press when not in route preview mode
                if (routeInfoContainer.visibility == View.GONE) {
                    handleLongPressDestination(longPressPoint)
                    true // Consume the event
                } else {
                    false // Don't consume if route preview is shown
                }
            }
        }
        
        val styleUri = "mapbox://styles/djsookz/cmiyer8iu000101s84wqsav5l"
        mapboxMapView?.mapboxMap?.loadStyleUri(styleUri)
        
        // ПРОФЕСИОНАЛНО: Определяме началната локация на базата на запазените данни
        val initialLocation = determineInitialLocation()
        
        if (!mapStateViewModel.hasInitializedCamera && initialLocation != null) {
            mapboxMapView?.mapboxMap?.setCamera(
                CameraOptions.Builder()
                    .center(MapboxPoint.fromLngLat(initialLocation.second, initialLocation.first))
                    .zoom(17.0)
                    .build()
            )
        } else if (initialLocation == null) {
            // НЕ задаваме камера ако няма локация - ще се зададе при първото location update
            Log.d("MapFragment", "No initial location available - camera will be set on first location update")
        }
        
        mapboxMapView?.mapboxMap?.getStyle { style ->
            currentMapboxStyle = style

            // Initialize route line layers (if route line was initialized in onViewCreated)
            if (this::routeLineView.isInitialized) {
                try {
                    routeLineView.initializeLayers(style)
                } catch (_: Throwable) {
                    // ignore - custom styles may differ; route line can still render in many cases
                }
            }

            mapboxMapView?.post {
                mapboxMapView?.setBackgroundColor(Color.TRANSPARENT)
                mapboxMapView?.alpha = 1f
            }
            
            val savedState = mapStateViewModel.lastMapState
            if (!mapStateViewModel.hasInitializedCamera && savedState != null) {
                mapboxMapView?.mapboxMap?.setCamera(
                    CameraOptions.Builder()
                        .center(MapboxPoint.fromLngLat(savedState.centerLon, savedState.centerLat))
                        .zoom(savedState.zoom)
                        .pitch(savedState.pitch ?: 45.0)
                        .build()
                )
            }

            // Enable Mapbox Location Component (SDK puck) when style is ready
            tryEnableMapboxLocationComponent()
        }
        
        configureMapboxPlugins()
        mapboxMapView?.postDelayed({ configureMapboxPlugins() }, 1000)
    }
    
    private fun configureMapboxPlugins() {
        mapboxMapView?.let {
            it.compass.enabled = false
            it.scalebar.enabled = false
            it.attribution.enabled = false
        }
    }
    private fun determineInitialLocation(): Pair<Double, Double>? {
        mapStateViewModel.lastKnownLocation?.let { location ->
            return Pair(location.latitude, location.longitude)
        }
        mapStateViewModel.lastMapState?.let { state ->
            return Pair(state.centerLat, state.centerLon)
        }

        return null
    }
    
    private fun tryEnableMapboxLocationComponent() {
        val mapView = mapboxMapView ?: return
        if (!checkLocationPermission()) return
        if (isMapboxLocationComponentEnabled) return

        val orangeColor = Color.parseColor("#FF7A18")

        // Keep default provider initially (fast raw GPS puck). We'll switch to snapped provider when enhancedLocation arrives.
        isUsingNavigationLocationProvider = false

        mapView.location.updateSettings {
            enabled = true
            pulsingEnabled = true
            pulsingColor = orangeColor
            puckBearingEnabled = true
            locationPuck = LocationPuck2D(
                topImage = ImageHolder.from(createMapboxPuckTopImage()),
                bearingImage = ImageHolder.from(createMapboxPuckBearingImage()),
                shadowImage = ImageHolder.from(createMapboxPuckShadowImage())
            )
        }
    
        // Touch the navigation instance to ensure the lifecycle delegate is active.
        // (Location updates are pushed into navigationLocationProvider via navigationLocationObserver.)
        @Suppress("UNUSED_VARIABLE")
        val _nav = mapboxNavigation

        isMapboxLocationComponentEnabled = true
        Log.d("MapFragment", "✅ Mapbox Location Component enabled")
    }

    private fun disableMapboxLocationComponent() {
        val mapView = mapboxMapView ?: return
        if (!isMapboxLocationComponentEnabled) return

        mapView.location.updateSettings { enabled = false }
        isMapboxLocationComponentEnabled = false
        isUsingNavigationLocationProvider = false
    }

    private fun initNavigation() {
        // Defensive: MyApplication already calls MapboxNavigationApp.setup, but keep this aligned with TestNavigationActivity.
        if (!MapboxNavigationApp.isSetup()) {
            MapboxNavigationApp.setup(NavigationOptions.Builder(requireContext()).build())
        }
    }
    
    private fun createMapboxPuckTopImage(): Bitmap {
        val density = resources.displayMetrics.density
        val size = (32 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val centerX = size / 2f
        val centerY = size / 2f
        val radius = 11f * density
        
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF7A18")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, radius, fillPaint)
        
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
    }
        canvas.drawCircle(centerX, centerY, radius, strokePaint)
    
        return bitmap
    }
    
    private fun createMapboxPuckBearingImage(): Bitmap = createMapboxPuckTopImage()

    private fun createMapboxPuckShadowImage(): Bitmap {
        val density = resources.displayMetrics.density
        val size = (32 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val centerX = size / 2f
        val centerY = size / 2f
        val radiusX = 14f * density
        val radiusY = 6f * density

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 0, 0, 0)
            style = Paint.Style.FILL
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
    

    
    private fun createLocationDotIcon(): Bitmap {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        return bitmap
    }
    

    
    private fun startNormalSession() {
        if (!checkLocationPermission()) {
            requestLocationPermission()
            return
        }
        
        val selectedProfileId = ProfileStorage.getSelectedProfileId(requireContext())
        val profiles = ProfileStorage.loadProfiles(requireContext())
        val profile = if (selectedProfileId != -1L) {
            profiles.find { it.id == selectedProfileId }
        } else {
            profiles.firstOrNull()
        }
        
        profile?.let {
            ProfileStorage.saveSelectedProfile(requireContext(), it.id)
            val intent = Intent(requireContext(), CountdownActivity::class.java).apply {
                putExtra("SELECTED_PROFILE", it)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            requireActivity().finish()
        } ?: run {
            Toast.makeText(requireContext(), "Моля изберете профил", Toast.LENGTH_SHORT).show()
            val intent = Intent(requireContext(), MainContainerActivity::class.java).apply {
                putExtra("INITIAL_PAGE", MainContainerActivity.PAGE_RACES)
            }
            startActivity(intent)
        }
    }
    
    private fun navigateToSessions() {
        // Навигираме към RACES страницата в MainContainerActivity
        val activity = requireActivity()
        if (activity is MainContainerActivity) {
            activity.navigateToPage(MainContainerActivity.PAGE_RACES)
        } else {
            // Fallback ако не сме в MainContainerActivity
            val intent = Intent(requireContext(), MainContainerActivity::class.java).apply {
                putExtra("INITIAL_PAGE", MainContainerActivity.PAGE_RACES)
            }
            startActivity(intent)
        }
    }
    
    // ... (continued in next part due to length)
    
    override fun onStart() {
        super.onStart()
        mapboxMapView?.onStart()
    }
    
    override fun onStop() {
        super.onStop()
        mapboxMapView?.onStop()
    }
    
    override fun onResume() {
        super.onResume()
        
        loadCachedWeatherData()
        updateEnvironmentDisplay()
        loadProfileInfo()
        
        // ВАЖНО: Ако има currentDestination (route preview режим), НЕ приближаваме до локацията
        // Запазваме текущата camera позиция (zoom на маршрута)
        if (currentDestination == null) {
            // Само ако няма route preview, показваме last known location
            displayLastKnownLocationInstantly()
            displayLastKnownLocationInstantly() // ПРОФЕСИОНАЛНО РЕШЕНИЕ: Показваме last known location ВЕДНАГА за instant display
        }
        
        // ВАЖНО: Ако има currentDestination но сме в MainContainerActivity (не в MainActivity),
        // значи сме се върнали от навигацията - скрий temperature и altitude
        if (currentDestination != null) {
            val activity = requireActivity()
            if (activity.javaClass.simpleName == "MainContainerActivity") {
                // Сме се върнали от навигацията - скрий temperature и altitude
                llTemperature.visibility = View.GONE
                llAltitude.visibility = View.GONE
            }
        }
        
        mapboxMapView?.onResume()
        tryEnableMapboxLocationComponent()
    }
    
    /**
     * Показва последната известна локация веднага за instant display (като Google Maps)
     * Това предотвратява "скокането" на картата докато GPS данните не пристигнат
     */
    private fun displayLastKnownLocationInstantly() {
        if (!checkLocationPermission()) return

        val last = mapStateViewModel.lastKnownLocation ?: return
        mapboxMapView?.mapboxMap?.setCamera(
            CameraOptions.Builder()
                .center(MapboxPoint.fromLngLat(last.longitude, last.latitude))
                .zoom(17.0)
                .build()
        )
    }


    /**
     * Обновява само location marker без да мести камерата (използва се за instant display)
     */
    private fun updateLocationMarkerOnly(location: Location) {
        // Mapbox mode uses SDK puck; no manual marker update.
    }
    
    override fun onPause() {
        super.onPause()
        
        // Изчисти navigation data cache ако Fragment е паузиран
        try {
            NavigationDataCache.clear(requireContext())
        } catch (e: Exception) {
            Log.e("MapFragment", "Failed to clear navigation cache", e)
        }
        
        // ПРОФЕСИОНАЛНО: Запазваме последната локация и състоянието на камерата
        currentLocation?.let { location ->
            mapStateViewModel.saveLastLocation(location)
        }
        
        if (mapboxMapView != null) {
            mapboxMapView?.mapboxMap?.cameraState?.let { cameraState ->
                mapStateViewModel.saveMapState(
                    cameraState.center.latitude(),
                    cameraState.center.longitude(),
                    cameraState.zoom,
                    cameraState.pitch
                )
            }
        }
        
        disableMapboxLocationComponent()
        
        // Mapbox cleanup
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // КРИТИЧНО: НЕ унищожаваме MapView тук - той се запазва в паметта за instant navigation
        // MapView ще се унищожи само когато Fragment се унищожи напълно
    }
    
    override fun onDestroy() {
        super.onDestroy()
        disableMapboxLocationComponent()
        mapboxMapView?.onDestroy()
    }
    
    private fun toggleWeatherExpansion() {
        if (isAltitudeExpanded) {
            collapseAltitudeNow()
        }
        
        isWeatherExpanded = !isWeatherExpanded
        val collapsedWidth = (90 * resources.displayMetrics.density).toInt()
        
        if (isWeatherExpanded) {
            tvSeparator.visibility = TextView.VISIBLE
            llWeatherExpanded.visibility = TextView.VISIBLE
            tvSeparator.alpha = 0f
            llWeatherExpanded.alpha = 0f
            
            val params = llTemperature.layoutParams
            params.width = collapsedWidth
            llTemperature.layoutParams = params
            
            llTemperature.post {
                llTemperature.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
                    android.view.View.MeasureSpec.makeMeasureSpec(llTemperature.height, android.view.View.MeasureSpec.EXACTLY)
                )
                val targetWidth = llTemperature.measuredWidth
                
                val widthAnimator = ValueAnimator.ofInt(collapsedWidth, targetWidth)
                widthAnimator.addUpdateListener { animation ->
                    val animParams = llTemperature.layoutParams
                    animParams.width = animation.animatedValue as Int
                    llTemperature.layoutParams = animParams
                }
                widthAnimator.duration = 300
                widthAnimator.interpolator = DecelerateInterpolator()
                widthAnimator.start()
                
                tvSeparator.animate().alpha(1f).setDuration(250).setStartDelay(50).start()
                llWeatherExpanded.animate().alpha(1f).setDuration(300).setStartDelay(50).start()
            }
        } else {
            val currentWidth = llTemperature.width
            tvSeparator.animate().alpha(0f).setDuration(150).start()
            llWeatherExpanded.animate().alpha(0f).setDuration(150).start()
            
            val widthAnimator = ValueAnimator.ofInt(currentWidth, collapsedWidth)
            widthAnimator.addUpdateListener { animation ->
                val params = llTemperature.layoutParams
                params.width = animation.animatedValue as Int
                llTemperature.layoutParams = params
            }
            widthAnimator.startDelay = 100
            widthAnimator.duration = 250
            widthAnimator.interpolator = DecelerateInterpolator()
            widthAnimator.addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    tvSeparator.visibility = TextView.GONE
                    llWeatherExpanded.visibility = TextView.GONE
                }
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
            widthAnimator.start()
        }
    }
    
    private fun toggleAltitudeExpansion() {
        if (isWeatherExpanded) {
            collapseWeather()
        }
        
        if (isAltitudeExpanded) {
            collapseAltitudeNow()
        } else {
            expandAltitude()
        }
    }
    
    private fun expandAltitude() {
        isAltitudeExpanded = true
        val collapsedWidth = if (altitudeCollapsedWidth > 0) {
            altitudeCollapsedWidth
        } else {
            val measured = llAltitude.width
            if (measured > 0) measured else (90 * resources.displayMetrics.density).toInt()
        }
        altitudeCollapsedWidth = collapsedWidth
        
        tvAltSeparator.visibility = TextView.VISIBLE
        llAltitudeExpanded.visibility = TextView.VISIBLE
        tvAltSeparator.alpha = 0f
        llAltitudeExpanded.alpha = 0f
        
        val params = llAltitude.layoutParams
        params.width = collapsedWidth
        llAltitude.layoutParams = params
        
        llAltitude.post {
            llAltitude.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
                android.view.View.MeasureSpec.makeMeasureSpec(llAltitude.height, android.view.View.MeasureSpec.EXACTLY)
            )
            val targetWidth = llAltitude.measuredWidth
            
            val widthAnimator = ValueAnimator.ofInt(collapsedWidth, targetWidth)
            widthAnimator.addUpdateListener { animation ->
                val animParams = llAltitude.layoutParams
                animParams.width = animation.animatedValue as Int
                llAltitude.layoutParams = animParams
            }
            widthAnimator.duration = 300
            widthAnimator.interpolator = DecelerateInterpolator()
            widthAnimator.start()
            
            tvAltSeparator.animate().alpha(1f).setDuration(250).setStartDelay(50).start()
            llAltitudeExpanded.animate().alpha(1f).setDuration(300).setStartDelay(50).start()
        }
    }
    
    private fun collapseAltitudeNow() {
        isAltitudeExpanded = false
        val collapsedWidth = if (altitudeCollapsedWidth > 0) {
            altitudeCollapsedWidth
        } else {
            (90 * resources.displayMetrics.density).toInt()
        }
        val currentWidth = llAltitude.width
        
        tvAltSeparator.animate().alpha(0f).setDuration(150).start()
        llAltitudeExpanded.animate().alpha(0f).setDuration(150).start()
        
        val widthAnimator = ValueAnimator.ofInt(currentWidth, collapsedWidth)
        widthAnimator.addUpdateListener { animation ->
            val params = llAltitude.layoutParams
            params.width = animation.animatedValue as Int
            llAltitude.layoutParams = params
        }
        widthAnimator.startDelay = 100
        widthAnimator.duration = 250
        widthAnimator.interpolator = DecelerateInterpolator()
        widthAnimator.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                tvAltSeparator.visibility = TextView.GONE
                llAltitudeExpanded.visibility = TextView.GONE
                val params = llAltitude.layoutParams
                params.width = collapsedWidth
                llAltitude.layoutParams = params
            }
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationCancel(animation: android.animation.Animator) {}
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
        widthAnimator.start()
    }
    
    private fun collapseWeather() {
        if (!isWeatherExpanded) return
        
        isWeatherExpanded = false
        val collapsedWidth = (90 * resources.displayMetrics.density).toInt()
        val currentWidth = llTemperature.width
        
        tvSeparator.animate().alpha(0f).setDuration(150).start()
        llWeatherExpanded.animate().alpha(0f).setDuration(150).start()
        
        val widthAnimator = ValueAnimator.ofInt(currentWidth, collapsedWidth)
        widthAnimator.addUpdateListener { animation ->
            val params = llTemperature.layoutParams
            params.width = animation.animatedValue as Int
            llTemperature.layoutParams = params
        }
        widthAnimator.startDelay = 100
        widthAnimator.duration = 250
        widthAnimator.interpolator = DecelerateInterpolator()
        widthAnimator.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                tvSeparator.visibility = TextView.GONE
                llWeatherExpanded.visibility = TextView.GONE
            }
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationCancel(animation: android.animation.Animator) {}
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
        widthAnimator.start()
    }
    
    private fun updateWeatherExpandedText() {
        val untilText = getString(R.string.weather_until)
        val rainText = if (rainTimeText.isNotEmpty()) {
            "🌧️${rainChance3h}% $untilText $rainTimeText"
        } else {
            "🌧️${rainChance3h}%"
        }
        val expandedText = "💨${currentWindKph.toInt()}km/h 💧${currentHumidity}% | $rainText"
        llWeatherExpanded.text = expandedText
    }
    
    private fun updateAltitudeExpandedText() {
        val pressureText = "📊 ${currentPressure.toInt()} hPa"
        llAltitudeExpanded.text = pressureText
    }
    
    private fun centerOnCurrentLocation() {
        currentLocation?.let { location ->
            mapboxMapView?.mapboxMap?.setCamera(
                CameraOptions.Builder()
                    .center(MapboxPoint.fromLngLat(location.longitude, location.latitude))
                    .zoom(17.0)
                    .build()
            )
        } ?: retrieveCurrentLocation()
    }
    
    private fun retrieveCurrentLocation() {
        // Mapbox Location Component handles location updates
        tryEnableMapboxLocationComponent()
    }
    
    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestLocationPermission() {
        requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                retrieveCurrentLocation()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Разрешение за локация е необходимо за пълна функционалност",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun startLocationUpdates() {
        // Mapbox uses Location Component, no need for manual updates
    }
    
    private fun stopLocationUpdates() {
        // Mapbox uses Location Component, no need for manual updates
    }
    
    private fun updateEnvironmentDisplay() {
        val tempText = if (currentTemperature != null) {
            UnitsManager.formatTemperature(currentTemperature!!, requireContext(), decimals = 0)
        } else {
            val unit = UnitsManager.getTemperatureUnit(requireContext())
            "--${unit.symbol}"
        }
        
        val altText = if (currentAltitude != null) {
            String.format("%.0fm", currentAltitude)
        } else {
            "--m"
        }
        
        tvTemperature.text = tempText
        tvAltitude.text = altText
        ivWeatherIcon.setImageResource(currentWeatherIcon)
        
        if (currentTemperature != null) {
            llTemperature.visibility = LinearLayout.VISIBLE
        }
        if (currentAltitude != null) {
            llAltitude.visibility = LinearLayout.VISIBLE
        }
    }
    
    private fun loadCachedWeatherData() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val cachedTemp = prefs.getFloat("cached_temperature", Float.NaN)
        val cachedAlt = prefs.getFloat("cached_altitude", Float.NaN)
        val cachedLat = prefs.getFloat("cached_location_lat", Float.NaN)
        val cachedLon = prefs.getFloat("cached_location_lon", Float.NaN)
        val cachedIcon = prefs.getInt("cached_weather_icon", -1)
        val cachedWindKph = prefs.getFloat("cached_wind_kph", Float.NaN)
        val cachedHumidity = prefs.getInt("cached_humidity", -1)
        val cachedPressure = prefs.getFloat("cached_pressure", Float.NaN)
        
        if (!cachedTemp.isNaN() && !cachedLat.isNaN() && !cachedLon.isNaN()) {
            currentTemperature = cachedTemp
            if (cachedIcon != -1 && isValidWeatherIcon(cachedIcon)) {
                currentWeatherIcon = cachedIcon
            }
        }
        
        if (!cachedAlt.isNaN() && !cachedLat.isNaN() && !cachedLon.isNaN()) {
            currentAltitude = cachedAlt
        }
        
        if (!cachedWindKph.isNaN()) {
            currentWindKph = cachedWindKph.toDouble()
        }
        
        if (cachedHumidity != -1) {
            currentHumidity = cachedHumidity
        }
        
        if (!cachedPressure.isNaN()) {
            currentPressure = cachedPressure.toDouble()
        }
    }
    
    private fun cacheWeatherData(location: Location) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = prefs.edit()
        
        currentTemperature?.let { editor.putFloat("cached_temperature", it) }
        currentAltitude?.let { editor.putFloat("cached_altitude", it) }
        editor.putFloat("cached_location_lat", location.latitude.toFloat())
        editor.putFloat("cached_location_lon", location.longitude.toFloat())
        editor.putInt("cached_weather_icon", currentWeatherIcon)
        editor.putFloat("cached_wind_kph", currentWindKph.toFloat())
        editor.putInt("cached_humidity", currentHumidity)
        editor.putFloat("cached_pressure", currentPressure.toFloat())
        editor.apply()
    }
    
    private fun isValidWeatherIcon(iconRes: Int): Boolean {
        val validIcons = setOf(
            R.drawable.ic_thermometer,
            R.drawable.ic_weather_sunny,
            R.drawable.ic_weather_clear_night,
            R.drawable.ic_weather_partly_cloudy,
            R.drawable.ic_weather_partly_cloudy_night,
            R.drawable.ic_weather_cloudy,
            R.drawable.ic_weather_rainy,
            R.drawable.ic_weather_snowy
        )
        return validIcons.contains(iconRes)
    }
    
    private fun shouldFetchWeatherData(location: Location): Boolean {
        // Check if fragment is attached before accessing context
        val context = context ?: return false
        if (!isAdded) return false
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val cachedLat = prefs.getFloat("cached_location_lat", Float.NaN)
        val cachedLon = prefs.getFloat("cached_location_lon", Float.NaN)
        
        if (cachedLat.isNaN() || cachedLon.isNaN()) {
            return true
        }
        
        val cachedLocation = Location("cached").apply {
            latitude = cachedLat.toDouble()
            longitude = cachedLon.toDouble()
        }
        val distanceKm = location.distanceTo(cachedLocation) / 1000.0
        
        return distanceKm > CACHE_LOCATION_THRESHOLD_KM
    }
    
    private fun fetchWeatherFromAPI(location: Location) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val weatherRetrofit = Retrofit.Builder()
                    .baseUrl("https://api.weatherapi.com/v1/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                
                val elevationRetrofit = Retrofit.Builder()
                    .baseUrl("https://api.open-meteo.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                
                val weatherApiService = weatherRetrofit.create(WeatherApiService::class.java)
                val openMeteoService = elevationRetrofit.create(OpenMeteoService::class.java)
                
                val weatherResponse = weatherApiService.getCurrentWeather(
                    apiKey = "547cc84c36a447ab8fe131642251808",
                    location = "${location.latitude},${location.longitude}",
                    lang = "bg"
                )
                
                if (weatherResponse.isSuccessful && weatherResponse.body() != null) {
                    val weather = weatherResponse.body()!!
                    currentTemperature = weather.current.temp_c.toFloat()
                    
                    val condition = weather.current.condition
                    val cloudCover = weather.current.cloud
                    val isDay = weather.current.is_day == 1
                    currentWeatherIcon = WeatherIconMapper.getWeatherApiIcon(condition.code, cloudCover, isDay)
                    
                    currentWindKph = weather.current.wind_kph
                    currentWindDir = weather.current.wind_dir
                    currentHumidity = weather.current.humidity
                    currentCloudCover = weather.current.cloud
                    currentPressure = weather.current.pressure_mb
                    
                    weather.forecast?.forecastday?.firstOrNull()?.hour?.let { hours ->
                        val now = java.util.Calendar.getInstance()
                        val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
                        val currentMinute = now.get(java.util.Calendar.MINUTE)
                        
                        val next3Hours = hours.filter { hour ->
                            val hourTime = hour.time.split(" ")[1].split(":")[0].toInt()
                            hourTime > currentHour || (hourTime == currentHour && currentMinute < 30)
                        }.take(3)
                        
                        val maxRainHour = next3Hours.maxByOrNull { it.chance_of_rain }
                        rainChance3h = maxRainHour?.chance_of_rain ?: 0
                        
                        rainTimeText = if (maxRainHour != null && rainChance3h > 0) {
                            maxRainHour.time.split(" ")[1].substring(0, 5)
                        } else {
                            ""
                        }
                    } ?: run {
                        rainChance3h = 0
                        rainTimeText = ""
                    }
                    
                    withContext(Dispatchers.Main) {
                        updateWeatherExpandedText()
                        updateAltitudeExpandedText()
                    }
                }
                
                val elevationResponse = openMeteoService.getElevation(
                    location.latitude,
                    location.longitude
                )
                
                if (elevationResponse.isSuccessful && elevationResponse.body() != null) {
                    val elevation = elevationResponse.body()!!
                    currentAltitude = elevation.elevation.firstOrNull()?.toFloat() ?: 0f
                }
                
                cacheWeatherData(location)
                
                withContext(Dispatchers.Main) {
                    updateEnvironmentDisplay()
                }
            } catch (e: Exception) {
                android.util.Log.e("MapFragment", "Error fetching weather data", e)
            }
        }
    }
    
    private fun adjustMarginsForLandscapeNavigation(view: View) {
        val rootView = view.rootView
        
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            
            if (isLandscape) {
                val navBarWidth = maxOf(systemBars.left, systemBars.right)
                val basePadding = 16
                val equalPadding = basePadding + navBarWidth
                
                val bottomContainer = view.findViewById<LinearLayout>(R.id.bottomContainer)
                bottomContainer?.setPadding(
                    equalPadding,
                    bottomContainer.paddingTop,
                    equalPadding,
                    bottomContainer.paddingBottom
                )
                
                val pillsExtraMargin = 50
                
                bottomContainer?.post {
                    val bottomContainerHeight = bottomContainer.height
                    val pillsMarginBottom = bottomContainerHeight + (25 * resources.displayMetrics.density).toInt()
                    
                    llAltitude?.let {
                        val params = it.layoutParams as? android.widget.RelativeLayout.LayoutParams
                        params?.marginStart = equalPadding + pillsExtraMargin
                        params?.bottomMargin = pillsMarginBottom
                        it.layoutParams = params
                    }
                    
                    llTemperature?.let {
                        val params = it.layoutParams as? android.widget.RelativeLayout.LayoutParams
                        params?.marginEnd = equalPadding + pillsExtraMargin
                        params?.bottomMargin = pillsMarginBottom
                        it.layoutParams = params
                        
                        fabMyLocationContainer?.let { fab ->
                            val fabParams = fab.layoutParams as? android.widget.RelativeLayout.LayoutParams
                            val temperaturePillHeight = it.height
                            val fabMarginBottom = pillsMarginBottom + temperaturePillHeight + (10 * resources.displayMetrics.density).toInt()
                            fabParams?.marginEnd = equalPadding + pillsExtraMargin
                            fabParams?.bottomMargin = fabMarginBottom
                            fab.layoutParams = fabParams
                        }
                    }
                }
            }
            
            insets
        }
    }
    
    // ЕЛЕМЕНТАРНО: Зареждане на модела и снимката от активния профил
    private fun loadProfileInfo() {
        if (!isAdded || view == null) return
        
        // Проверка дали view-тата са инициализирани (важно при ротация)
        if (!::tvHeaderModelName.isInitialized || !::ivHeaderProfileImage.isInitialized) {
            return
        }
        
        val selectedId = ProfileStorage.getSelectedProfileId(requireContext())
        val profiles = ProfileStorage.loadProfiles(requireContext())
        val activeProfile = profiles.find { it.id == selectedId }

        if (activeProfile != null) {
            // 1. Зареждаме модела: "Audi A6" -> "A6"
            val fullName = activeProfile.name.trim()
            val modelName = if (fullName.contains(" ")) {
                fullName.substringAfterLast(" ")
            } else {
                fullName
            }
            tvHeaderModelName.text = modelName
            tvHeaderModelName.setTextColor(android.graphics.Color.WHITE)
            tvHeaderModelName.visibility = View.VISIBLE

            // 2. Зареждаме снимката или показваме иконка
            if (!activeProfile.imagePath.isNullOrEmpty()) {
                val imageFile = java.io.File(requireContext().getExternalFilesDir(null), activeProfile.imagePath)
                if (imageFile.exists()) {
                    // Image is already scaled on disk, just load it
                    val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
                    if (bitmap != null) {
                        ivHeaderProfileImage.setImageBitmap(bitmap)
                        ivHeaderProfileImage.scaleType = ImageView.ScaleType.CENTER_CROP
                        ivHeaderProfileImage.setPadding(0, 0, 0, 0)
                    } else {
                        showDefaultIcon(activeProfile.vehicleType)
                    }
                } else {
                    showDefaultIcon(activeProfile.vehicleType)
                }
            } else {
                showDefaultIcon(activeProfile.vehicleType)
            }
        } else {
            tvHeaderModelName.text = ""
            showDefaultIcon(Profile.VehicleType.CAR)
        }
    }
    
    private fun showDefaultIcon(type: Profile.VehicleType) {
        if (!::ivHeaderProfileImage.isInitialized) return
        
        val icon = if (type == Profile.VehicleType.CAR) R.drawable.ic_car else R.drawable.ic_motorcycle
        ivHeaderProfileImage.setImageResource(icon)
        ivHeaderProfileImage.scaleType = ImageView.ScaleType.CENTER_INSIDE
        val padding = (6 * resources.displayMetrics.density).toInt()
        ivHeaderProfileImage.setPadding(padding, padding, padding, padding)
        ivHeaderProfileImage.visibility = View.VISIBLE
    }
}
