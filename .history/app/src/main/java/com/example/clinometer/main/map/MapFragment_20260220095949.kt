package com.example.clinometer.main.map

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
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
import android.os.SystemClock
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
import androidx.appcompat.app.AlertDialog
import androidx.activity.OnBackPressedCallback
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
import com.mapbox.maps.plugin.Plugin
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
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
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.formatter.UnitType
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
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
import com.example.clinometer.network.WeatherApiHour
import com.example.clinometer.preview.RouteWeatherPreviewOverlay
import com.example.clinometer.utils.WeatherIconMapper
import com.mapbox.geojson.LineString
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.common.location.Location as MapboxLocation
import com.example.clinometer.main.MainContainerActivity
import kotlin.math.abs
import com.example.clinometer.RouteStorage
import com.example.clinometer.RouteSnapshotGenerator
import com.example.clinometer.Race
import org.osmdroid.util.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

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
    private var btnPreviewOverview: ImageButton? = null
    private var btnPreviewRecenter: ImageButton? = null
    private var btnOverview: ImageButton? = null
    private var btnRecenter: ImageButton? = null
    private var mapControlsContainer: LinearLayout? = null
    private var btnOrientationToggle: ImageButton? = null
    private var btnCameraNorthMode: ImageButton? = null
    private var isOrientationLocked: Boolean = false
    private var isNorthUpMode: Boolean = false
    private var enforcePitchZero: Boolean = true
    private var isApplyingPitchZero: Boolean = false
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
    private val overviewPadding: EdgeInsets by lazy {
        val top = resources.getDimension(R.dimen.mapbox_overview_padding_top).toDouble()
        val left = resources.getDimension(R.dimen.mapbox_overview_padding_left).toDouble()
        val bottom = resources.getDimension(R.dimen.mapbox_overview_padding_bottom).toDouble()
        val right = resources.getDimension(R.dimen.mapbox_overview_padding_right).toDouble()
        EdgeInsets(top, left, bottom, right)
    }
    private val followingPadding: EdgeInsets by lazy {
        val top = resources.getDimension(R.dimen.mapbox_following_padding_top).toDouble()
        val left = resources.getDimension(R.dimen.mapbox_following_padding_left).toDouble()
        val bottom = resources.getDimension(R.dimen.mapbox_following_padding_bottom).toDouble()
        val right = resources.getDimension(R.dimen.mapbox_following_padding_right).toDouble()
        EdgeInsets(top, left, bottom, right)
    }
    private var fixedOriginForRoute: Point? = null
    private var routeCacheKey: String? = null
    private var cachedRoutesAllowMotorways: List<NavigationRoute>? = null
    private var cachedRoutesNoMotorways: List<NavigationRoute>? = null
    private var routeRequestInFlightForAllowMotorways: Boolean? = null
    private var currentRoutesOriginal: List<NavigationRoute> = emptyList()
    private var selectedRouteIndex: Int = 0
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var navigationCamera: NavigationCamera

    // Navigation UI/state (inline navigation)
    private var isNavigationActive: Boolean = false
    private var hasReachedDestination: Boolean = false
    private lateinit var maneuverApi: MapboxManeuverApi
    private var maneuverContainer: View? = null
    private var maneuverView: MapboxManeuverView? = null
    private var tripProgressContainer: LinearLayout? = null
    private var tvTripEta: TextView? = null
    private var tvTripRemainingTime: TextView? = null
    private var tvTripRemainingDistance: TextView? = null
    private val routeArrowApi: MapboxRouteArrowApi by lazy { MapboxRouteArrowApi() }
    private var routeArrowView: MapboxRouteArrowView? = null
    private var onIndicatorPositionChangedListener: OnIndicatorPositionChangedListener? = null
    private val leanUpdateHandler = Handler(Looper.getMainLooper())
    private var leanUpdateRunnable: Runnable? = null
    private var leanUpdatesActive: Boolean = false
    private var smoothedLeanAngle: Float = 0f
    private val leanAngleAlpha: Float = 0.15f
    private val leanAngleDeadband: Float = 1.5f
    private var navigationObserversRegistered: Boolean = false
    private var bottomHudRow: ViewGroup? = null
    private var speedTextCar: TextView? = null
    private var navSessionContainer: LinearLayout? = null
    private var carModeContainer: LinearLayout? = null
    private var distanceTextCar: TextView? = null
    private var chronometerCar: Chronometer? = null
    private var buttonContainer: LinearLayout? = null
    private var btnReset: View? = null
    private var btnZero: View? = null
    private var btnStop: View? = null
    private var angleContainerMoto: LinearLayout? = null
    private var angleTextMoto: TextView? = null
    private var linearGaugeView: LinearGaugeView? = null
    private var navSessionActive: Boolean = false
    private var navSessionStartTime: Long = 0L
    private var navSessionDistanceMeters: Double = 0.0
    private var navSessionLastLocation: Location? = null
    private val kalmanFilter = KalmanLocationFilter()
    private var mapboxTargetPosition: GeoPoint? = null
    private var mapboxSmoothedTargetPosition: GeoPoint? = null
    private var mapboxTargetBearing: Float = 0f
    private var mapboxCurrentPosition: GeoPoint? = null
    private var mapboxCurrentBearing: Float = 0f
    private var mapboxLastUpdateTime: Long = 0L
    private var mapboxRenderRunnable: Runnable? = null
    private var targetMapOrientation: Float = 0f
    private var currentMapOrientation: Float = 0f
    private var lastCalculatedBearing: Float = 0f
    private var lastProcessedLocation: Location? = null
    private var isFirstLocation: Boolean = true
    private var lastSpeedKmh: Float = 0f
    private var targetZoom: Double = 17.5
    private var currentZoom: Double = 17.5
    private var lastZoomChangeTime: Long = 0L
    private var isMapboxLocationComponentEnabled: Boolean = false
    private var isUsingNavigationLocationProvider: Boolean = false
    private val navigationLocationProvider = NavigationLocationProvider()
    // (Turn-by-turn UI is handled in MainActivity; MapFragment stays as search + route preview)
    private val mapboxNavigation: MapboxNavigation by requireMapboxNavigation(
        onResumedObserver = object : MapboxNavigationObserver {
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.registerLocationObserver(navigationLocationObserver)
                if (isNavigationActive && !navigationObserversRegistered) {
                    mapboxNavigation.registerRoutesObserver(sdkRoutesObserver)
                    mapboxNavigation.registerRouteProgressObserver(sdkRouteProgressObserver)
                    mapboxNavigation.registerArrivalObserver(sdkArrivalObserver)
                    navigationObserversRegistered = true
                }
                mapboxNavigation.startTripSession()
            }

            override fun onDetached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.unregisterLocationObserver(navigationLocationObserver)
                if (navigationObserversRegistered) {
                    mapboxNavigation.unregisterRoutesObserver(sdkRoutesObserver)
                    mapboxNavigation.unregisterRouteProgressObserver(sdkRouteProgressObserver)
                    mapboxNavigation.unregisterArrivalObserver(sdkArrivalObserver)
                    navigationObserversRegistered = false
                }
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
                val mapView = mapboxMapView
                if (mapView != null) {
                    val locationPlugin = mapView.getPlugin(Plugin.MAPBOX_LOCATION_COMPONENT_PLUGIN_ID) as? LocationComponentPlugin
                    locationPlugin?.setLocationProvider(navigationLocationProvider)
                }
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

            if (pendingFirstWeatherFetch && !isWeatherFirstOpenDone()) {
                fetchWeatherFromAPI(androidLoc)
                markWeatherFirstOpenDone()
                pendingFirstWeatherFetch = false
            }

            if (this@MapFragment::viewportDataSource.isInitialized) {
                viewportDataSource.onLocationChanged(enhanced)
                viewportDataSource.evaluate()
            }

            if (isNavigationActive || navSessionActive) {
                updateNavSessionMetrics(androidLoc)
            }

            if (navSessionActive && !isNavigationActive) {
                processNormalDrivingLocation(androidLoc)
            }

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

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            val binder = service as ForegroundService.LocalBinder
            foregroundService = binder.getService()
            serviceBound = true

            if (shouldResetOnConnect) {
                shouldResetOnConnect = false
                resetSessionData()
            }

            val startTime = foregroundService?.getStartTime() ?: SystemClock.elapsedRealtime()
            chronometerCar?.base = startTime
            chronometerCar?.start()
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            serviceBound = false
            foregroundService = null
        }
    }

    private val sdkRoutesObserver = RoutesObserver { result ->
        if (!isNavigationActive) return@RoutesObserver
        if (!this::routeLineApi.isInitialized || !this::routeLineView.isInitialized) return@RoutesObserver

        val routes = result.navigationRoutes
        val style = mapboxMapView?.mapboxMap?.style ?: return@RoutesObserver
        val rla = routeLineApi
        val rlv = routeLineView

        if (routes.isEmpty()) {
            rla.clearRouteLine { value -> rlv.renderClearRouteLineValue(style, value) }
            routeArrowView?.render(style, routeArrowApi.clearArrows())
            maneuverContainer?.visibility = View.GONE
            tripProgressContainer?.visibility = View.GONE
            return@RoutesObserver
        }

        val primaryOnly = listOf(routes.first())
        rla.setNavigationRoutes(primaryOnly, emptyList()) { value ->
            rlv.renderRouteDrawData(style, value)
            if (this::viewportDataSource.isInitialized) {
                viewportDataSource.onRouteChanged(primaryOnly.first())
                viewportDataSource.evaluate()
            }
        }
    }

    private val sdkRouteProgressObserver = RouteProgressObserver { routeProgress: RouteProgress ->
        if (!isNavigationActive) return@RouteProgressObserver

        if (this::viewportDataSource.isInitialized) {
            viewportDataSource.onRouteProgressChanged(routeProgress)
            viewportDataSource.evaluate()
        }

        val style = mapboxMapView?.mapboxMap?.style
        if (style != null && this::routeLineApi.isInitialized && this::routeLineView.isInitialized) {
            routeLineApi.updateWithRouteProgress(routeProgress) { value ->
                routeLineView.renderRouteLineUpdate(style, value)
            }
            val arrowUpdate = routeArrowApi.addUpcomingManeuverArrow(routeProgress)
            routeArrowView?.renderManeuverUpdate(style, arrowUpdate)
        }

        if (isNavigationActive && this::maneuverApi.isInitialized) {
            try {
                val maneuversExpected = maneuverApi.getManeuvers(routeProgress)
                maneuverView?.renderManeuvers(maneuversExpected)
                maneuverContainer?.visibility = View.VISIBLE

                val orangeColor = Color.parseColor("#FF6020")
                val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                maneuverView?.post {
                    val mv = maneuverView ?: return@post
                    setManeuverViewColors(mv, orangeColor, Color.TRANSPARENT)
                    reduceManeuverTextSize(mv, isLandscape)
                    reduceManeuverIconSize(mv, isLandscape)
                    centerManeuverText(mv)
                    reduceManeuverSpacing(mv, isLandscape)
                }
            } catch (_: Throwable) {
                // ignore
            }
        } else {
            maneuverContainer?.visibility = View.GONE
        }

        val distanceRemaining = routeProgress.distanceRemaining ?: 0f
        val durationRemainingSeconds = (routeProgress.durationRemaining ?: 0.0).toLong()

        val distanceKm = (distanceRemaining / 1000f).toInt().coerceAtLeast(0)
        val distanceRemainingText = distanceKm.toString()

        val totalMinutes = (durationRemainingSeconds / 60).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val timeRemainingText = if (hours > 0) "${hours}ч ${minutes}м" else "${minutes}м"

        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.SECOND, durationRemainingSeconds.toInt())
        val etaText = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(calendar.time)

        tvTripEta?.text = etaText
        tvTripRemainingTime?.text = timeRemainingText
        tvTripRemainingDistance?.text = distanceRemainingText
    }

    private val sdkArrivalObserver = object : ArrivalObserver {
        override fun onWaypointArrival(routeProgress: RouteProgress) {}
        override fun onNextRouteLegStart(routeLegProgress: com.mapbox.navigation.base.trip.model.RouteLegProgress) {}

        override fun onFinalDestinationArrival(routeProgress: RouteProgress) {
            if (!isNavigationActive) return
            mapboxNavigation.setNavigationRoutes(emptyList())
            onDestinationReached()
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
    private var foregroundService: ForegroundService? = null
    private var serviceBound: Boolean = false
    private var shouldResetOnConnect: Boolean = false
    private var shouldRestoreNavigationAfterRecreate: Boolean = false
    private var shouldRestoreNormalSessionAfterRecreate: Boolean = false
    private var navBackPressedCallback: OnBackPressedCallback? = null
    private var pendingExitAfterSave: Boolean = false
    
    // Weather details
    private var currentWindKph: Double = 0.0
    private var currentWindDir: String = ""
    private var currentHumidity: Int = 0
    private var currentCloudCover: Int = 0
    private var rainChance3h: Int = 0
    private var rainTimeText: String = ""
    private var rainTimePrefix: String = ""
    private var summaryIsRain: Boolean = true
    private var currentPressure: Double = 0.0
    
    private val handler = Handler(Looper.getMainLooper())
    private val weatherRefreshHandler = Handler(Looper.getMainLooper())
    private var weatherRefreshRunnable: Runnable? = null
    private var pendingFirstWeatherFetch = false
    private var pendingRoutePreviewRestore = false
    private var routeWeatherPreviewOverlay: RouteWeatherPreviewOverlay? = null
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_NAV_ACTIVE, isNavigationActive)
        outState.putBoolean(KEY_SESSION_ACTIVE, navSessionActive && !isNavigationActive)
        val hasPreview = currentDestination != null && !isNavigationActive
        outState.putBoolean(KEY_PREVIEW_ACTIVE, hasPreview)
        currentDestination?.let { dest ->
            outState.putDouble(KEY_PREVIEW_DEST_LAT, dest.latitude())
            outState.putDouble(KEY_PREVIEW_DEST_LON, dest.longitude())
        }
        currentDestinationName?.let { outState.putString(KEY_PREVIEW_DEST_NAME, it) }
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
        shouldRestoreNavigationAfterRecreate = savedInstanceState?.getBoolean(KEY_NAV_ACTIVE) == true
        shouldRestoreNormalSessionAfterRecreate = savedInstanceState?.getBoolean(KEY_SESSION_ACTIVE) == true
        pendingRoutePreviewRestore = savedInstanceState?.getBoolean(KEY_PREVIEW_ACTIVE) == true
        if (pendingRoutePreviewRestore) {
            val lat = savedInstanceState?.getDouble(KEY_PREVIEW_DEST_LAT)
            val lon = savedInstanceState?.getDouble(KEY_PREVIEW_DEST_LON)
            if (lat != null && lon != null) {
                currentDestination = Point.fromLngLat(lon, lat)
                currentDestinationName = savedInstanceState?.getString(KEY_PREVIEW_DEST_NAME)
            } else {
                pendingRoutePreviewRestore = false
            }
        }

        navBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (navSessionActive) {
                    showExitNormalSessionDialog()
                    return
                }
                if (isNavigationActive) {
                    showExitNavigationDialog()
                    return
                }
                if (searchContainer.visibility == View.VISIBLE) {
                    restoreInitialMapUi()
                    return
                }
                if (currentDestination != null) {
                    cancelRoutePreview()
                    return
                }
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, navBackPressedCallback!!)
        
        // Hide any OSMDroid mapView if it exists (legacy support)
        val osmdroidMapView = view.findViewById<android.view.View>(R.id.mapView)
        osmdroidMapView?.let {
            it.visibility = View.GONE
            if (it.parent != null) {
                (it.parent as? ViewGroup)?.removeView(it)
            }
        }
        
        setupMapboxMap(view)

        mapboxMapView?.let { mapView ->
            routeWeatherPreviewOverlay = RouteWeatherPreviewOverlay(
                context = requireContext(),
                mapView = mapView,
                weatherApiKey = WEATHER_API_KEY,
                coroutineScope = viewLifecycleOwner.lifecycleScope
            )
        }
        
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

        // Route preview controls (left-side)
        btnPreviewOverview = view.findViewById(R.id.btnPreviewOverview)
        btnPreviewRecenter = view.findViewById(R.id.btnPreviewRecenter)
        
        // Initialize map control buttons
        btnOverview = view.findViewById(R.id.btnOverview)
        btnRecenter = view.findViewById(R.id.btnRecenter)
        mapControlsContainer = view.findViewById(R.id.mapControlsContainer)
        btnOrientationToggle = view.findViewById(R.id.btnOrientationToggle)
        btnCameraNorthMode = view.findViewById(R.id.btnCameraNorthMode)

        // Navigation UI (maneuvers + trip progress)
        maneuverContainer = view.findViewById(R.id.maneuverContainer)
        maneuverView = view.findViewById(R.id.maneuverView)
        tripProgressContainer = view.findViewById(R.id.tripProgressContainer)
        tvTripEta = view.findViewById(R.id.tvTripEta)
        tvTripRemainingTime = view.findViewById(R.id.tvTripRemainingTime)
        tvTripRemainingDistance = view.findViewById(R.id.tvTripRemainingDistance)

        // Navigation HUD + session controls
        bottomHudRow = view.findViewById(R.id.bottomHudRow)
        speedTextCar = view.findViewById(R.id.speedTextCar)
        navSessionContainer = view.findViewById(R.id.navSessionContainer)
        carModeContainer = view.findViewById(R.id.carModeContainer)
        distanceTextCar = view.findViewById(R.id.distanceTextCar)
        chronometerCar = view.findViewById(R.id.chronometerCar)
        buttonContainer = view.findViewById(R.id.buttonContainer)
        btnReset = view.findViewById(R.id.btnReset)
        btnZero = view.findViewById(R.id.btnZero)
        btnStop = view.findViewById(R.id.btnStop)
        angleContainerMoto = view.findViewById(R.id.angleContainerMoto)
        angleTextMoto = view.findViewById(R.id.angleTextMoto)
        linearGaugeView = view.findViewById(R.id.linearGaugeView)

        val distanceFormatterOptions = DistanceFormatterOptions.Builder(requireContext())
            .unitType(UnitType.METRIC)
            .build()
        maneuverApi = MapboxManeuverApi(MapboxDistanceFormatter(distanceFormatterOptions))

        btnReset?.setOnClickListener {
            if (checkLocationPermission()) {
                if (serviceBound && foregroundService != null) {
                    resetSessionData()
                } else {
                    shouldResetOnConnect = true
                    startAndBindServiceIfNeeded()
                }
            }
        }
        btnZero?.setOnClickListener {
            val profile = getActiveProfile()
            if (profile?.vehicleType == Profile.VehicleType.MOTORCYCLE) {
                foregroundService?.calibrateZero()
            }
        }
        btnStop?.setOnClickListener {
            if (serviceBound) {
                saveAndFinishSession()
            } else {
                stopNavigationInline()
            }
        }

        // Ensure camera buttons are wired after views are bound
        setupNavigationCameraButtons()
        setupOrientationToggle()
        setupCameraModeToggle()

        // Load motorway preference
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        allowMotorways = prefs.getBoolean("allow_motorways", false)
        updateMotorwayButtonIcon()

        setupInlineSearchUI()
        setupRoutePreviewUi()
        setupDismissSearchOnOutsideTap(view)
        
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
                .vanishingRouteLineEnabled(true)
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

        etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && currentDestination == null) {
                restoreInitialMapUi()
            }
        }
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
        etSearch.clearFocus()
    }

    private fun setupDismissSearchOnOutsideTap(root: View) {
        val dismissListener = View.OnTouchListener { _, _ ->
            if (searchContainer.visibility == View.VISIBLE && currentDestination == null) {
                restoreInitialMapUi()
                return@OnTouchListener true
            }
            false
        }
        mapboxMapView?.setOnTouchListener(dismissListener)
        routeInfoContainer.setOnTouchListener(dismissListener)
        routePreviewBottomContainer.setOnTouchListener(dismissListener)
        bottomHudRow?.setOnTouchListener(dismissListener)
        navSessionContainer?.setOnTouchListener(dismissListener)
        root.setOnTouchListener(dismissListener)
    }

    private fun restoreInitialMapUi() {
        hideInlineSearch()
        if (currentDestination == null) {
            val tv = destinationSearchContainer.findViewById<TextView>(R.id.tvDestinationPlaceholder)
            tv.text = getString(R.string.destination_placeholder)
        }
        routeInfoContainer.visibility = View.GONE
        routePreviewBottomContainer.visibility = View.GONE
        btnMotorwayOptions.visibility = View.GONE
        btnSearchRoute.visibility = View.GONE
        motorwayOptionsContainer.visibility = View.GONE
        mapControlsContainer?.visibility = View.GONE
        btnPreviewOverview?.visibility = View.GONE
        btnPreviewRecenter?.visibility = View.GONE
        if (::llActiveProfileHeader.isInitialized) {
            llActiveProfileHeader.visibility = View.VISIBLE
        }
        llTemperature.visibility = View.VISIBLE
        llAltitude.visibility = View.VISIBLE
        fabMyLocationContainer.visibility = View.VISIBLE
        view?.findViewById<LinearLayout>(R.id.bottomContainer)?.visibility = View.VISIBLE
        requireActivity().findViewById<View>(R.id.bottomNavigationContainer)?.visibility = View.VISIBLE
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
        val originLocation = currentLocation ?: mapStateViewModel.lastKnownLocation
        fixedOriginForRoute = originLocation?.let { loc ->
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
            startNavigationInline()
        }
        
        // Cancel button: clear route and return to initial state (like first time entering the page)
        btnCancelRoute.setOnClickListener {
            routeWeatherPreviewOverlay?.clear()
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
            mapControlsContainer?.visibility = View.GONE
            btnPreviewOverview?.visibility = View.GONE
            btnPreviewRecenter?.visibility = View.GONE
            
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
            routeWeatherPreviewOverlay?.clear()
            // Clear current route and show search interface
            currentDestination = null
            currentDestinationName = null
            if (this::viewportDataSource.isInitialized) {
                viewportDataSource.clearRouteData()
                viewportDataSource.evaluate()
            }
            if (this::navigationCamera.isInitialized) {
                navigationCamera.requestNavigationCameraToIdle()
            }
            mapboxNavigation.setNavigationRoutes(emptyList())
            routeInfoContainer.visibility = View.GONE
            routePreviewBottomContainer.visibility = View.GONE
            btnMotorwayOptions.visibility = View.GONE
            btnSearchRoute.visibility = View.GONE
            motorwayOptionsContainer.visibility = View.GONE
            mapControlsContainer?.visibility = View.GONE
            // Clear route line
            if (::routeLineApi.isInitialized && ::routeLineView.isInitialized && currentMapboxStyle != null) {
                routeLineApi.clearRouteLine { value ->
                    routeLineView.renderClearRouteLineValue(currentMapboxStyle!!, value)
                }
            }
            // Reset route cache
            resetRouteCacheForNewSelection()
            restoreInitialMapUi()
            showInlineSearch()
        }

        btnMotorwayOptions.setOnClickListener {
            allowMotorways = !allowMotorways
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit().putBoolean("allow_motorways", allowMotorways).apply()
            updateMotorwayButtonIcon()
            motorwayOptionsContainer.visibility = View.GONE
            currentDestination?.let { findRouteInline(it) }
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

    private fun cancelRoutePreview() {
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
        mapControlsContainer?.visibility = View.GONE
        btnPreviewOverview?.visibility = View.GONE
        btnPreviewRecenter?.visibility = View.GONE
        routeWeatherPreviewOverlay?.clear()

        // Clear route line from map
        if (::routeLineApi.isInitialized && ::routeLineView.isInitialized && currentMapboxStyle != null) {
            routeLineApi.clearRouteLine { value ->
                routeLineView.renderClearRouteLineValue(currentMapboxStyle!!, value)
            }
        }

        // Animate camera back to current location using NavigationCamera (like SDK does)
        currentLocation?.let {
            if (this::viewportDataSource.isInitialized && this::navigationCamera.isInitialized) {
                // Clear route data from viewport data source
                viewportDataSource.clearRouteData()
                currentLocation?.let { location ->
                    val currentCameraState = mapboxMapView?.mapboxMap?.cameraState
                    currentCameraState?.let { state ->
                        val cameraOptions = CameraOptions.Builder()
                            .center(MapboxPoint.fromLngLat(location.longitude, location.latitude))
                            .zoom(if (state.zoom in 15.0..18.0) state.zoom else 17.0)
                            .bearing(state.bearing)
                            .pitch(0.0)
                            .build()
                        mapboxMapView?.mapboxMap?.let { mapboxMap ->
                            mapboxMap.easeTo(cameraOptions)
                        }
                    }
                }
            }
        }

        // Show initial state UI (like first time entering)
        restoreInitialMapUi()
    }

    companion object {
        private const val KEY_NAV_ACTIVE = "nav_active"
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val KEY_PREVIEW_ACTIVE = "preview_active"
        private const val KEY_PREVIEW_DEST_LAT = "preview_dest_lat"
        private const val KEY_PREVIEW_DEST_LON = "preview_dest_lon"
        private const val KEY_PREVIEW_DEST_NAME = "preview_dest_name"
        private const val WEATHER_API_KEY = "547cc84c36a447ab8fe131642251808"
        private const val WEATHER_REFRESH_INTERVAL_MS = 15 * 60 * 1000L
        private const val CACHE_WEATHER_MAX_AGE_MS = 15 * 60 * 1000L
        private const val CACHE_LOCATION_THRESHOLD_KM = 5.0
        private const val PREF_WEATHER_FIRST_OPEN_DONE = "weather_first_open_done"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val ZOOM_CHANGE_DELAY = 3000L
    }

    private fun updateMotorwayButtonIcon() {
        val iconRes = if (allowMotorways) R.drawable.ic_motorway else R.drawable.ic_road
        btnMotorwayOptions.setImageResource(iconRes)
    }
    
    private fun setupNavigationCameraButtons() {
        fun requestOverview() {
            if (this::viewportDataSource.isInitialized && this::navigationCamera.isInitialized) {
                mapboxMapView?.post {
                    navigationCamera.requestNavigationCameraToOverview()
                }
            }
        }

        fun requestFollowing() {
            if (this::viewportDataSource.isInitialized && this::navigationCamera.isInitialized) {
                mapboxMapView?.post {
                    navigationCamera.requestNavigationCameraToFollowing()
                }
            }
        }

        // Show camera control buttons when route is displayed
        btnOverview?.setOnClickListener {
            requestOverview()
        }
        
        btnRecenter?.setOnClickListener {
            requestFollowing()
        }

        // Route preview left-side buttons
        btnPreviewOverview?.setOnClickListener { requestOverview() }
        btnPreviewRecenter?.setOnClickListener { requestFollowing() }
        
        // Initially hide buttons - they will be shown when route is displayed
        btnOverview?.visibility = View.GONE
        btnRecenter?.visibility = View.GONE
        mapControlsContainer?.visibility = View.GONE
        btnPreviewOverview?.visibility = View.GONE
        btnPreviewRecenter?.visibility = View.GONE
    }

    private fun setupOrientationToggle() {
        btnOrientationToggle?.setOnClickListener {
            isOrientationLocked = !isOrientationLocked
            val activity = requireActivity()
            activity.requestedOrientation = if (isOrientationLocked) {
                val orientation = resources.configuration.orientation
                if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            } else {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            updateOrientationToggleUi()
        }
        updateOrientationToggleUi()
    }

    private fun updateOrientationToggleUi() {
        val isLocked = isOrientationLocked
        btnOrientationToggle?.setImageResource(if (isLocked) R.drawable.ic_lock else R.drawable.ic_unlock)
        val tintColor = if (isLocked) Color.WHITE else Color.parseColor("#B3FFFFFF")
        btnOrientationToggle?.setColorFilter(tintColor)
    }

    private fun setupCameraModeToggle() {
        btnCameraNorthMode?.setOnClickListener {
            isNorthUpMode = !isNorthUpMode
            if (isNorthUpMode) {
                mapboxMapView?.mapboxMap?.setCamera(CameraOptions.Builder().bearing(0.0).build())
                enforcePitchZero = true
                applyPitchZero()
            } else {
                enforcePitchZero = false
                mapboxMapView?.mapboxMap?.setCamera(
                    CameraOptions.Builder()
                        .pitch(getNormalDrivingPitch())
                        .build()
                )
            }
            updateCameraModeUi()
        }
        updateCameraModeUi()
    }

    private fun updateCameraModeUi() {
        btnCameraNorthMode?.setImageResource(R.drawable.ic_map_heading)
        val tintColor = if (isNorthUpMode) Color.WHITE else Color.parseColor("#B3FFFFFF")
        btnCameraNorthMode?.setColorFilter(tintColor)
    }

    private fun applyPitchZero() {
        mapboxMapView?.mapboxMap?.setCamera(
            CameraOptions.Builder()
                .pitch(0.0)
                .build()
        )
    }

    private fun enforcePitchZeroIfNeeded() {
        if (!enforcePitchZero || isApplyingPitchZero) return
        val pitch = mapboxMapView?.mapboxMap?.cameraState?.pitch ?: return
        if (abs(pitch) > 0.5) {
            isApplyingPitchZero = true
            mapboxMapView?.mapboxMap?.setCamera(
                CameraOptions.Builder()
                    .pitch(0.0)
                    .build()
            )
            mapboxMapView?.post { isApplyingPitchZero = false }
        }
    }

    private fun setManeuverViewColors(view: View, textColor: Int, backgroundColor: Int) {
        if (view.background != null) {
            view.setBackgroundColor(backgroundColor)
        }
        if (view is TextView) {
            view.setTextColor(textColor)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setManeuverViewColors(view.getChildAt(i), textColor, backgroundColor)
            }
        }
    }

    private fun reduceManeuverTextSize(view: View, isLandscape: Boolean = false) {
        if (view is TextView) {
            val alreadyScaled = (view.getTag(R.id.tag_maneuver_scaled_text) as? Boolean) == true
            if (alreadyScaled) return
            val currentSize = view.textSize / view.resources.displayMetrics.scaledDensity
            val scaleFactor = if (isLandscape) 0.7f else 0.8f
            val newSize = currentSize * scaleFactor
            view.textSize = newSize
            if (isLandscape) {
                view.maxLines = 2
                view.ellipsize = android.text.TextUtils.TruncateAt.END
                val density = view.resources.displayMetrics.density
                view.maxWidth = (360 * density).toInt()
            }
            view.setTag(R.id.tag_maneuver_scaled_text, true)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                reduceManeuverTextSize(view.getChildAt(i), isLandscape)
            }
        }
    }

    private fun reduceManeuverIconSize(view: View, isLandscape: Boolean = false) {
        if (view is ImageView) {
            val alreadyScaled = (view.getTag(R.id.tag_maneuver_scaled_icon) as? Boolean) == true
            if (alreadyScaled) return
            val layoutParams = view.layoutParams
            if (layoutParams != null) {
                val reductionFactor = if (isLandscape) 0.8f else 0.7f
                val currentWidth = layoutParams.width
                val currentHeight = layoutParams.height

                if (currentWidth > 0 && currentHeight > 0) {
                    layoutParams.width = (currentWidth * reductionFactor).toInt()
                    layoutParams.height = (currentHeight * reductionFactor).toInt()
                    view.layoutParams = layoutParams
                } else {
                    val sizeInDp = if (isLandscape) 36 else 32
                    val sizeInPx = (sizeInDp * view.resources.displayMetrics.density).toInt()
                    layoutParams.width = sizeInPx
                    layoutParams.height = sizeInPx
                    view.layoutParams = layoutParams
                }
            }
            view.setTag(R.id.tag_maneuver_scaled_icon, true)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                reduceManeuverIconSize(view.getChildAt(i), isLandscape)
            }
        }
    }

    private fun centerManeuverText(view: View) {
        if (view is TextView) {
            view.textAlignment = View.TEXT_ALIGNMENT_CENTER
            view.gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                centerManeuverText(view.getChildAt(i))
            }
        }
    }

    private fun reduceManeuverSpacing(view: View, isLandscape: Boolean = false) {
        if (view is ViewGroup) {
            val alreadyAdjusted = (view.getTag(R.id.tag_maneuver_spacing_adjusted) as? Boolean) == true
            if (!alreadyAdjusted) {
                if (view is LinearLayout && view.orientation == LinearLayout.HORIZONTAL) {
                    val currentPaddingStart = view.paddingStart
                    val currentPaddingEnd = view.paddingEnd
                    val maxPadding = if (isLandscape) 2 else 8
                    val reductionFactor = if (isLandscape) 0.1f else 0.4f
                    if (currentPaddingStart > 4 || currentPaddingEnd > 4) {
                        view.setPaddingRelative(
                            (currentPaddingStart * reductionFactor).toInt().coerceAtMost(maxPadding),
                            view.paddingTop,
                            (currentPaddingEnd * reductionFactor).toInt().coerceAtMost(maxPadding),
                            view.paddingBottom
                        )
                    }
                }
                view.setTag(R.id.tag_maneuver_spacing_adjusted, true)
            }

            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val childAlreadyAdjusted = (child.getTag(R.id.tag_maneuver_spacing_adjusted) as? Boolean) == true
                if (!childAlreadyAdjusted && (child is ImageView || child is TextView)) {
                    val childParams = child.layoutParams as? ViewGroup.MarginLayoutParams
                    if (childParams != null) {
                        val density = child.resources.displayMetrics.density
                        val marginDp = if (isLandscape) 0 else 4
                        if (childParams.marginStart > (6 * density).toInt()) {
                            childParams.marginStart = (marginDp * density).toInt()
                        }
                        if (childParams.marginEnd > (6 * density).toInt()) {
                            childParams.marginEnd = (marginDp * density).toInt()
                        }
                        child.layoutParams = childParams
                        child.setTag(R.id.tag_maneuver_spacing_adjusted, true)
                    }
                }
            }

            for (i in 0 until view.childCount) {
                reduceManeuverSpacing(view.getChildAt(i), isLandscape)
            }
        }
    }

    private fun startNavigationInline() {
        val dest = currentDestination
        if (dest == null) {
            Toast.makeText(requireContext(), "Няма избрана дестинация", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedRoute = currentRoutesOriginal.getOrNull(selectedRouteIndex)
            ?: mapboxNavigation.getNavigationRoutes().firstOrNull()
        if (selectedRoute == null) {
            Toast.makeText(requireContext(), "Няма маршрут", Toast.LENGTH_SHORT).show()
            return
        }

        enforcePitchZero = false

        // Apply selected route to SDK and switch to following camera
        mapboxNavigation.setNavigationRoutes(listOf(selectedRoute))
        setNavigationActive(true)
        if (this::viewportDataSource.isInitialized && this::navigationCamera.isInitialized) {
            mapboxMapView?.post {
                navigationCamera.requestNavigationCameraToFollowing()
            }
        }

        // Hide preview UI
        routeInfoContainer.visibility = View.GONE
        routePreviewBottomContainer.visibility = View.GONE
        btnSearchRoute.visibility = View.GONE
        btnMotorwayOptions.visibility = View.GONE
        motorwayOptionsContainer.visibility = View.GONE
        destinationSearchContainer.visibility = View.GONE
        searchContainer.visibility = View.GONE
        if (::llActiveProfileHeader.isInitialized) {
            llActiveProfileHeader.visibility = View.GONE
        }
        mapControlsContainer?.visibility = View.VISIBLE
        btnPreviewOverview?.visibility = View.GONE
        btnPreviewRecenter?.visibility = View.GONE

        // Hide bottom container (sessions/start) while navigating
        view?.findViewById<LinearLayout>(R.id.bottomContainer)?.visibility = View.GONE
    }

    private fun setNavigationActive(active: Boolean) {
        if (active == isNavigationActive) return
        isNavigationActive = active
        hasReachedDestination = false
        navBackPressedCallback?.isEnabled = active

        if (active) {
            routeWeatherPreviewOverlay?.clear()
            if (!navigationObserversRegistered) {
                mapboxNavigation.registerRoutesObserver(sdkRoutesObserver)
                mapboxNavigation.registerRouteProgressObserver(sdkRouteProgressObserver)
                mapboxNavigation.registerArrivalObserver(sdkArrivalObserver)
                navigationObserversRegistered = true
            }
            destinationSearchContainer.visibility = View.GONE
            searchContainer.visibility = View.GONE
            if (::llActiveProfileHeader.isInitialized) {
                llActiveProfileHeader.visibility = View.GONE
            }
            tripProgressContainer?.visibility = View.VISIBLE
            bottomHudRow?.visibility = View.VISIBLE
            navSessionContainer?.visibility = View.VISIBLE
            carModeContainer?.visibility = View.VISIBLE
            buttonContainer?.visibility = View.VISIBLE
            mapControlsContainer?.visibility = View.VISIBLE
            btnCameraNorthMode?.visibility = View.GONE
            if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                mapControlsContainer?.let { controls ->
                    val params = controls.layoutParams as? android.widget.RelativeLayout.LayoutParams
                    params?.topMargin = resources.getDimensionPixelSize(R.dimen.map_controls_margin_top_landscape)
                    controls.layoutParams = params
                }
            }
            btnOverview?.visibility = View.VISIBLE
            btnRecenter?.visibility = View.VISIBLE
            btnPreviewOverview?.visibility = View.GONE
            btnPreviewRecenter?.visibility = View.GONE
            bottomHudRow?.bringToFront()
            shouldResetOnConnect = true
            if (serviceBound && foregroundService != null) {
                shouldResetOnConnect = false
                resetSessionData()
            }
            startNavSessionTracking()
            startAndBindServiceIfNeeded()
            llTemperature.visibility = View.GONE
            llAltitude.visibility = View.GONE
            fabMyLocationContainer.visibility = View.GONE
            view?.findViewById<LinearLayout>(R.id.bottomContainer)?.visibility = View.GONE
            requireActivity().findViewById<View>(R.id.bottomNavigationContainer)?.visibility = View.GONE

            val mapView = mapboxMapView
            if (mapView != null) {
                val locationPlugin = mapView.getPlugin(Plugin.MAPBOX_LOCATION_COMPONENT_PLUGIN_ID) as? LocationComponentPlugin
                if (locationPlugin != null) {
                    onIndicatorPositionChangedListener?.let { locationPlugin.removeOnIndicatorPositionChangedListener(it) }
                    onIndicatorPositionChangedListener = OnIndicatorPositionChangedListener { point ->
                        if (!this::routeLineApi.isInitialized || !this::routeLineView.isInitialized) return@OnIndicatorPositionChangedListener
                        val style = mapboxMapView?.mapboxMap?.style ?: return@OnIndicatorPositionChangedListener
                        if (mapboxNavigation.getNavigationRoutes().isEmpty()) return@OnIndicatorPositionChangedListener
                        val update = routeLineApi.updateTraveledRouteLine(point)
                        routeLineView.renderRouteLineUpdate(style, update)
                    }
                    onIndicatorPositionChangedListener?.let { locationPlugin.addOnIndicatorPositionChangedListener(it) }
                }
            }
        } else {
            if (navigationObserversRegistered) {
                mapboxNavigation.unregisterRoutesObserver(sdkRoutesObserver)
                mapboxNavigation.unregisterRouteProgressObserver(sdkRouteProgressObserver)
                mapboxNavigation.unregisterArrivalObserver(sdkArrivalObserver)
                navigationObserversRegistered = false
            }
            maneuverContainer?.visibility = View.GONE
            tripProgressContainer?.visibility = View.GONE
            bottomHudRow?.visibility = View.GONE
            navSessionContainer?.visibility = View.GONE
            carModeContainer?.visibility = View.GONE
            buttonContainer?.visibility = View.GONE
            mapControlsContainer?.visibility = View.GONE
            btnCameraNorthMode?.visibility = View.GONE
            btnOverview?.visibility = View.GONE
            btnRecenter?.visibility = View.GONE
            btnPreviewOverview?.visibility = View.GONE
            btnPreviewRecenter?.visibility = View.GONE
            stopNavSessionTracking()
            cleanupForegroundService()
            llTemperature.visibility = View.VISIBLE
            llAltitude.visibility = View.VISIBLE
            fabMyLocationContainer.visibility = View.VISIBLE
            view?.findViewById<LinearLayout>(R.id.bottomContainer)?.visibility = View.VISIBLE
            requireActivity().findViewById<View>(R.id.bottomNavigationContainer)?.visibility = View.VISIBLE

            val mapView = mapboxMapView
            if (mapView != null) {
                val locationPlugin = mapView.getPlugin(Plugin.MAPBOX_LOCATION_COMPONENT_PLUGIN_ID) as? LocationComponentPlugin
                if (locationPlugin != null) {
                    onIndicatorPositionChangedListener?.let { locationPlugin.removeOnIndicatorPositionChangedListener(it) }
                }
            }
        }
    }

    private fun showExitNavigationDialog() {
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle("Изход от навигация?")
            .setMessage("Ако излезете, сесията няма да бъде записана.")
            .setPositiveButton("Да") { _, _ ->
                stopNavigationInline()
            }
            .setNegativeButton("Не", null)
            .create()
        DialogHelper.styleDialogButtons(dialog)
        dialog.show()
    }

    private fun showExitNormalSessionDialog() {
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle("Изход от сесия?")
            .setMessage("Ако излезете, сесията няма да бъде записана.")
            .setPositiveButton("Да") { _, _ ->
                stopNormalSessionWithoutSave()
            }
            .setNegativeButton("Не", null)
            .create()
        DialogHelper.styleDialogButtons(dialog)
        dialog.show()
    }

    private fun stopNormalSessionWithoutSave() {
        if (isNavigationActive) {
            mapboxNavigation.setNavigationRoutes(emptyList())
            setNavigationActive(false)
            resetNavigationUiAfterStop()
        }
        stopNavSessionTracking()
        cleanupForegroundService()
        setNormalSessionUiActive(false)
        resetNormalDrivingCameraState()
        resetNavSessionMetrics(resetTime = true)
        enforcePitchZero = true
    }

    private fun startNavSessionTracking() {
        navSessionActive = true
        navBackPressedCallback?.isEnabled = true
        navSessionStartTime = SystemClock.elapsedRealtime()
        navSessionDistanceMeters = 0.0
        navSessionLastLocation = null
        chronometerCar?.apply {
            base = navSessionStartTime
            start()
        }
        distanceTextCar?.text = "0.00"
        speedTextCar?.text = "0"
        updateLeanAngleVisibility()
        updateZeroButtonVisibility()
        startLeanAngleUpdates()
        startMapboxRenderLoop()
        if (!isNavigationActive) {
            mapboxMapView?.mapboxMap?.setCamera(
                CameraOptions.Builder()
                    .pitch(getNormalDrivingPitch())
                    .build()
            )
        }
    }

    private fun stopNavSessionTracking() {
        navSessionActive = false
        navBackPressedCallback?.isEnabled = false
        chronometerCar?.stop()
        stopLeanAngleUpdates()
        stopMapboxRenderLoop()
    }

    private fun resetNavSessionMetrics(resetTime: Boolean) {
        navSessionDistanceMeters = 0.0
        navSessionLastLocation = null
        distanceTextCar?.text = "0.00"
        if (resetTime) {
            navSessionStartTime = SystemClock.elapsedRealtime()
            chronometerCar?.base = navSessionStartTime
        }
        resetAngleUi()
    }

    private fun updateNavSessionMetrics(location: Location) {
        if (!navSessionActive) return

        val speedKmh = (location.speed * 3.6f).coerceAtLeast(0f)
        speedTextCar?.text = speedKmh.toInt().toString()

        navSessionLastLocation?.let { prev ->
            if (location.accuracy <= 25f && prev.accuracy <= 25f) {
                val dist = prev.distanceTo(location)
                val dt = (location.time - prev.time) / 1000.0
                if (dist in 0.3f..150f && dt in 0.2..5.0 && (dist / dt) < 35f) {
                    navSessionDistanceMeters += dist
                }
            }
        }
        navSessionLastLocation = location

        val km = navSessionDistanceMeters / 1000.0
        distanceTextCar?.text = String.format(java.util.Locale.getDefault(), "%.2f", km)

        updateLeanAngleUi()
    }

    private fun updateLeanAngleVisibility() {
        val profile = getActiveProfile()
        val isMotorcycle = profile?.vehicleType == Profile.VehicleType.MOTORCYCLE
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        angleContainerMoto?.visibility = if (isMotorcycle) View.VISIBLE else View.GONE
        linearGaugeView?.visibility = if (isMotorcycle && !isLandscape) View.VISIBLE else View.GONE
        angleTextMoto?.visibility = if (isMotorcycle) View.VISIBLE else View.GONE
        if (!isMotorcycle) {
            stopLeanAngleUpdates()
        }
    }

    private fun updateZeroButtonVisibility() {
        val profile = getActiveProfile()
        val isMotorcycle = profile?.vehicleType == Profile.VehicleType.MOTORCYCLE
        btnZero?.visibility = if (isMotorcycle) View.VISIBLE else View.GONE
    }

    private fun startLeanAngleUpdates() {
        val profile = getActiveProfile()
        if (profile?.vehicleType != Profile.VehicleType.MOTORCYCLE) return
        if (leanUpdatesActive) return
        leanUpdatesActive = true
        val runnable = object : Runnable {
            override fun run() {
                if (!leanUpdatesActive) return
                updateLeanAngleUi()
                leanUpdateHandler.postDelayed(this, 50L)
            }
        }
        leanUpdateRunnable = runnable
        leanUpdateHandler.post(runnable)
    }

    private fun stopLeanAngleUpdates() {
        leanUpdatesActive = false
        leanUpdateRunnable?.let { leanUpdateHandler.removeCallbacks(it) }
        leanUpdateRunnable = null
    }

    private fun updateLeanAngleUi() {
        val profile = getActiveProfile()
        if (profile?.vehicleType != Profile.VehicleType.MOTORCYCLE) return
        val service = foregroundService ?: return

        var targetAngle = service.getCurrentAngle()
        if (kotlin.math.abs(targetAngle) < leanAngleDeadband) {
            targetAngle = 0f
        }
        smoothedLeanAngle += (targetAngle - smoothedLeanAngle) * leanAngleAlpha

        val displayAngle = smoothedLeanAngle
        val text = "${displayAngle.toInt()}°"
        if (angleTextMoto?.text != text) {
            angleTextMoto?.text = text
        }

        linearGaugeView?.apply {
            angle = displayAngle
            maxLeftAngle = service.getMaxLeftAngle()
            maxRightAngle = service.getMaxRightAngle()
            invalidate()
        }
    }

    private fun resetAngleUi() {
        angleTextMoto?.text = "0°"
        smoothedLeanAngle = 0f
        linearGaugeView?.apply {
            angle = 0f
            maxLeftAngle = 0f
            maxRightAngle = 0f
            resetMaxima()
            invalidate()
        }
    }

    private fun stopNavigationInline() {
        mapboxNavigation.setNavigationRoutes(emptyList())
        setNavigationActive(false)
        resetNavigationUiAfterStop()
    }

    private fun resetNavigationUiAfterStop() {
        enforcePitchZero = true
        maneuverContainer?.visibility = View.GONE
        tripProgressContainer?.visibility = View.GONE

        if (this::viewportDataSource.isInitialized) {
            viewportDataSource.clearRouteData()
            viewportDataSource.evaluate()
        }
        if (this::navigationCamera.isInitialized) {
            navigationCamera.requestNavigationCameraToIdle()
        }
        mapboxMapView?.mapboxMap?.easeTo(
            CameraOptions.Builder()
                .bearing(0.0)
                .pitch(0.0)
                .build()
        )

        if (this::routeLineApi.isInitialized && this::routeLineView.isInitialized && currentMapboxStyle != null) {
            routeLineApi.clearRouteLine { value ->
                routeLineView.renderClearRouteLineValue(currentMapboxStyle!!, value)
            }
        }
        currentMapboxStyle?.let { style ->
            routeArrowView?.render(style, routeArrowApi.clearArrows())
        }

        currentDestination = null
        currentDestinationName = null
        resetRouteCacheForNewSelection()

        val placeholderView = destinationSearchContainer.findViewById<TextView>(R.id.tvDestinationPlaceholder)
        placeholderView.text = getString(R.string.destination_placeholder)

        routeInfoContainer.visibility = View.GONE
        routePreviewBottomContainer.visibility = View.GONE
        btnSearchRoute.visibility = View.GONE
        btnMotorwayOptions.visibility = View.GONE
        motorwayOptionsContainer.visibility = View.GONE
        destinationSearchContainer.visibility = View.VISIBLE
        searchContainer.visibility = View.GONE

        if (::llActiveProfileHeader.isInitialized) {
            llActiveProfileHeader.visibility = View.VISIBLE
        }

        mapControlsContainer?.visibility = View.GONE
        view?.findViewById<LinearLayout>(R.id.bottomContainer)?.visibility = View.VISIBLE
        requireActivity().findViewById<View>(R.id.bottomNavigationContainer)?.visibility = View.VISIBLE
    }

    private fun startAndBindServiceIfNeeded() {
        if (isServiceRunning()) {
            requireActivity().bindService(
                Intent(requireContext(), ForegroundService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
            return
        }

        val serviceIntent = Intent(requireContext(), ForegroundService::class.java).apply {
            putExtra("PRE_WARMING_MODE", true)
        }
        ContextCompat.startForegroundService(requireContext(), serviceIntent)

        val activateIntent = Intent(requireContext(), ForegroundService::class.java).apply {
            putExtra("ACTIVATE_NORMAL_MODE", true)
        }
        requireContext().startService(activateIntent)

        requireActivity().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun isServiceRunning(): Boolean {
        val manager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        val services = manager.getRunningServices(Int.MAX_VALUE)
        return services.any { it.service.className == ForegroundService::class.java.name }
    }

    private fun cleanupForegroundService() {
        try {
            if (serviceBound) {
                try {
                    requireActivity().unbindService(serviceConnection)
                } catch (_: IllegalArgumentException) {
                }
            }
            try {
                requireContext().stopService(Intent(requireContext(), ForegroundService::class.java))
            } catch (_: Exception) {
            }
        } finally {
            serviceBound = false
        }
    }

    private fun resetSessionData() {
        foregroundService?.resetData()
        resetNavSessionMetrics(resetTime = true)
        val startTime = foregroundService?.getStartTime() ?: SystemClock.elapsedRealtime()
        chronometerCar?.base = startTime
        chronometerCar?.start()
    }

    private fun saveAndFinishSession() {
        try {
            val rawRoutePoints = foregroundService?.getFinalRoutePoints() ?: emptyList()
            if (rawRoutePoints.isEmpty()) {
                handleEmptySession()
                return
            }
            if (rawRoutePoints.size < 3) {
                cleanupForegroundService()
                Toast.makeText(requireContext(), getString(R.string.error_no_route_data), Toast.LENGTH_LONG).show()
                startActivity(Intent(requireContext(), MainContainerActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("INITIAL_PAGE", MainContainerActivity.PAGE_MAP)
                })
                return
            }

            val race = createRaceFromSession()
            RouteStorage.saveRoutePoints(requireContext(), race.id, rawRoutePoints)
            val allRaces = RouteStorage.loadRaces(requireContext()).toMutableList()
            allRaces.add(race)
            RouteStorage.saveRaces(requireContext(), allRaces)

            if (rawRoutePoints.isNotEmpty()) {
                java.util.concurrent.Executors.newSingleThreadExecutor().execute {
                    try {
                        RouteSnapshotGenerator.generateAndSaveSnapshot(
                            context = requireContext(),
                            raceId = race.id,
                            routePoints = rawRoutePoints
                        ) { success ->
                            android.util.Log.d("MapFragment", if (success) "✅ Snapshot generated for race ${race.id}" else "❌ Failed to generate snapshot for race ${race.id}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MapFragment", "Error generating snapshot", e)
                    }
                }
            }

            cleanupForegroundService()

            pendingExitAfterSave = true

            val intent = Intent(requireContext(), SaveSessionActivity::class.java).apply {
                putExtra("raceId", race.id)
                putExtra("isNewSession", true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            showError("Error saving the race: ${e.message}")
        }
    }

    private fun createRaceFromSession(): Race {
        val routePoints = foregroundService?.getFinalRoutePoints() ?: emptyList()
        val activeProfile = getActiveProfile()
        val sessionNumber = getNextSessionNumber(activeProfile?.id ?: -1L)

        val isMotorcycle = activeProfile?.vehicleType == Profile.VehicleType.MOTORCYCLE
        val maxLeftAngle = if (isMotorcycle) (foregroundService?.getMaxLeftAngle() ?: 0f) else 0f
        val maxRightAngle = if (isMotorcycle) (foregroundService?.getMaxRightAngle() ?: 0f) else 0f
        val raceId = System.currentTimeMillis()

        return Race(
            profileId = activeProfile?.id ?: -1L,
            id = raceId,
            routePoints = routePoints,
            timestamp = System.currentTimeMillis(),
            duration = foregroundService?.getServiceDuration() ?: 0,
            absoluteTimestamp = System.currentTimeMillis(),
            maxLeftAngle = maxLeftAngle,
            maxRightAngle = maxRightAngle,
            maxSpeed = foregroundService?.getMaxSpeed() ?: 0f,
            name = "Session $sessionNumber",
            distance = navSessionDistanceMeters / 1000.0,
            time0to100 = 0L,
            time0to200 = 0L,
            time100to200 = 0L
        )
    }

    private fun getNextSessionNumber(profileId: Long): Int {
        if (profileId <= 0L) return 1
        val allRaces = RouteStorage.loadRaces(requireContext())
        val profileRaces = allRaces.filter { it.profileId == profileId }
        val sessionNumbers = profileRaces.mapNotNull { race ->
            race.name?.let { name ->
                if (name.startsWith("Session ")) name.substringAfter("Session ").toIntOrNull() else null
            }
        }
        val maxNumber = sessionNumbers.maxOrNull()
        return maxNumber?.plus(1) ?: 1
    }

    private fun getActiveProfile(): Profile? {
        val ctx = context ?: return null
        val selectedId = ProfileStorage.getSelectedProfileId(ctx)
        val profiles = ProfileStorage.loadProfiles(ctx)
        return profiles.find { it.id == selectedId } ?: profiles.firstOrNull()
    }

    private fun handleEmptySession() {
        cleanupForegroundService()
        Toast.makeText(requireContext(), getString(R.string.error_no_route_data), Toast.LENGTH_LONG).show()
        startActivity(Intent(requireContext(), MainContainerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("NAV_ITEM_ID", R.id.navMap)
        })
    }

    private fun showError(message: String) {
        val errorDialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .create()
        DialogHelper.styleDialogButtons(errorDialog)
        errorDialog.show()
    }

    private fun onDestinationReached() {
        if (hasReachedDestination) return
        hasReachedDestination = true

        setNavigationActive(false)
        resetNavigationUiAfterStop()
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
        // In route preview we keep the left-side overview/recenter like before.
        // The right-side "pill" controls are shown only during navigation.
        mapControlsContainer?.visibility = View.GONE
        btnOverview?.visibility = View.GONE
        btnRecenter?.visibility = View.GONE
        btnPreviewOverview?.visibility = if (enabled) View.VISIBLE else View.GONE
        btnPreviewRecenter?.visibility = if (enabled) View.VISIBLE else View.GONE
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
            btnPreviewOverview?.bringToFront()
            btnPreviewRecenter?.bringToFront()
        }
    }

    private fun findRouteInline(destination: Point) {
        // Always Mapbox mode
        if (!this::routeLineApi.isInitialized || !this::routeLineView.isInitialized) {
            Toast.makeText(requireContext(), "Картата още се зарежда…", Toast.LENGTH_SHORT).show()
            return
        }

        val originPoint = fixedOriginForRoute ?: run {
            val originLoc = currentLocation ?: mapStateViewModel.lastKnownLocation
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
                btnPreviewOverview?.visibility = View.VISIBLE
                btnPreviewRecenter?.visibility = View.VISIBLE
                
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
        routeWeatherPreviewOverlay?.showForRoute(primary)
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

            mv.mapboxMap.addOnCameraChangeListener {
                enforcePitchZeroIfNeeded()
            }
            
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

            if (routeArrowView == null) {
                routeArrowView = MapboxRouteArrowView(RouteArrowOptions.Builder(requireContext()).build())
            }

            val routesToRender = mapboxNavigation.getNavigationRoutes()
            if (routesToRender.isNotEmpty() && this::routeLineApi.isInitialized && this::routeLineView.isInitialized) {
                if (this::viewportDataSource.isInitialized) {
                    viewportDataSource.onRouteChanged(routesToRender.first())
                    viewportDataSource.evaluate()
                }
                val metadata = mapboxNavigation.getAlternativeMetadataFor(routesToRender)
                routeLineApi.setNavigationRoutes(routesToRender, metadata) { value ->
                    routeLineView.renderRouteDrawData(style, value)
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

        val locationPlugin = mapView.getPlugin(Plugin.MAPBOX_LOCATION_COMPONENT_PLUGIN_ID) as? LocationComponentPlugin
        locationPlugin?.updateSettings {
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

        val locationPlugin = mapView.getPlugin(Plugin.MAPBOX_LOCATION_COMPONENT_PLUGIN_ID) as? LocationComponentPlugin
        locationPlugin?.updateSettings { enabled = false }
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
            if (currentDestination != null) {
                cancelRoutePreview()
            }
            shouldResetOnConnect = true
            if (serviceBound && foregroundService != null) {
                shouldResetOnConnect = false
                resetSessionData()
            }
            startAndBindServiceIfNeeded()
            setNormalSessionUiActive(true)
            enforcePitchZero = false
            resetNormalDrivingCameraState()
            startNavSessionTracking()
            animateToCurrentLocation()
        } ?: run {
            Toast.makeText(requireContext(), "Моля изберете профил", Toast.LENGTH_SHORT).show()
            val intent = Intent(requireContext(), MainContainerActivity::class.java).apply {
                putExtra("INITIAL_PAGE", MainContainerActivity.PAGE_RACES)
            }
            startActivity(intent)
        }
    }

    private fun setNormalSessionUiActive(active: Boolean) {
        navSessionContainer?.visibility = if (active) View.VISIBLE else View.GONE
        carModeContainer?.visibility = if (active) View.VISIBLE else View.GONE
        bottomHudRow?.visibility = if (active) View.VISIBLE else View.GONE
        buttonContainer?.visibility = if (active) View.VISIBLE else View.GONE
        mapControlsContainer?.visibility = if (active) View.VISIBLE else View.GONE
        btnOverview?.visibility = View.GONE
        btnRecenter?.visibility = View.GONE
        btnCameraNorthMode?.visibility = if (active) View.VISIBLE else View.GONE
        view?.findViewById<LinearLayout>(R.id.bottomContainer)?.visibility = if (active) View.GONE else View.VISIBLE
        requireActivity().findViewById<View>(R.id.bottomNavigationContainer)?.visibility = if (active) View.GONE else View.VISIBLE

        maneuverContainer?.visibility = View.GONE
        tripProgressContainer?.visibility = View.GONE

        destinationSearchContainer.visibility = if (active) View.GONE else View.VISIBLE
        searchContainer.visibility = View.GONE
        routeInfoContainer.visibility = View.GONE
        routePreviewBottomContainer.visibility = View.GONE
        btnSearchRoute.visibility = View.GONE
        btnMotorwayOptions.visibility = View.GONE
        motorwayOptionsContainer.visibility = View.GONE

        llTemperature.visibility = if (active) View.GONE else View.VISIBLE
        llAltitude.visibility = if (active) View.GONE else View.VISIBLE
        fabMyLocationContainer.visibility = if (active) View.GONE else View.VISIBLE

        if (::llActiveProfileHeader.isInitialized) {
            llActiveProfileHeader.visibility = if (active) View.GONE else View.VISIBLE
        }
    }

    private fun animateToCurrentLocation() {
        val loc = currentLocation ?: mapStateViewModel.lastKnownLocation ?: return
        mapboxMapView?.mapboxMap?.easeTo(
            CameraOptions.Builder()
                .center(MapboxPoint.fromLngLat(loc.longitude, loc.latitude))
                .zoom(17.0)
                .build()
        )
    }

    private fun resetNormalDrivingCameraState() {
        mapboxTargetPosition = null
        mapboxSmoothedTargetPosition = null
        mapboxCurrentPosition = null
        mapboxCurrentBearing = 0f
        mapboxLastUpdateTime = 0L
        targetMapOrientation = 0f
        currentMapOrientation = 0f
        lastCalculatedBearing = 0f
        lastProcessedLocation = null
        isFirstLocation = true
        val currentZoomValue = mapboxMapView?.mapboxMap?.cameraState?.zoom ?: 17.5
        targetZoom = currentZoomValue
        currentZoom = currentZoomValue
        lastZoomChangeTime = 0L
    }

    private fun getNormalDrivingPitch(): Double {
        return if (isNorthUpMode) 0.0 else 60.0
    }

    private fun processNormalDrivingLocation(location: Location) {
        val filtered = kalmanFilter.process(location)
        val geoPoint = GeoPoint(filtered.latitude, filtered.longitude)

        val speedKmh = location.speed * 3.6f
        lastSpeedKmh = speedKmh

        mapboxSmoothedTargetPosition = mapboxSmoothedTargetPosition?.let { prev ->
            val alpha = when {
                speedKmh < 5 -> 0.15
                speedKmh < 30 -> 0.25
                else -> 0.35
            }
            GeoPoint(
                prev.latitude + (geoPoint.latitude - prev.latitude) * alpha,
                prev.longitude + (geoPoint.longitude - prev.longitude) * alpha
            )
        } ?: geoPoint

        if (isFirstLocation) {
            initializeFirstNormalLocation(filtered)
            return
        }

        var calculatedBearing = location.bearing

        if (lastProcessedLocation != null && speedKmh > 1f) {
            val lastGeoPoint = GeoPoint(lastProcessedLocation!!.latitude, lastProcessedLocation!!.longitude)
            val distance = geoPoint.distanceToAsDouble(lastGeoPoint)

            if (distance > 0.3) {
                val lat1 = Math.toRadians(lastGeoPoint.latitude)
                val lat2 = Math.toRadians(geoPoint.latitude)
                val deltaLon = Math.toRadians(geoPoint.longitude - lastGeoPoint.longitude)

                val x = sin(deltaLon) * cos(lat2)
                val y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)

                var movementBearing = Math.toDegrees(atan2(x, y)).toFloat()
                if (movementBearing < 0) movementBearing += 360f

                calculatedBearing = when {
                    speedKmh > 20 -> movementBearing * 0.1f + location.bearing * 0.9f
                    speedKmh > 5 -> movementBearing * 0.5f + location.bearing * 0.5f
                    else -> location.bearing
                }
            }
        }

        lastProcessedLocation = filtered

        mapboxTargetPosition = mapboxSmoothedTargetPosition
        mapboxTargetBearing = calculatedBearing
        lastCalculatedBearing = calculatedBearing

        if (isNorthUpMode) {
            targetMapOrientation = 0f
        } else if (speedKmh > 2) {
            targetMapOrientation = -calculatedBearing
        }

        updateZoomBasedOnSpeed(speedKmh)
        updateMapboxMapAnimation()
    }

    private fun initializeFirstNormalLocation(location: Location) {
        if (!isFirstLocation) return
        val geoPoint = GeoPoint(location.latitude, location.longitude)

        val zoomLevel = currentZoom
        val metersPerPixel = 156543.03392 * cos(Math.toRadians(location.latitude)) / Math.pow(2.0, zoomLevel)
        val offsetMeters = 30 * resources.displayMetrics.density * metersPerPixel

        val bearingRad = Math.toRadians(location.bearing.toDouble())
        val offsetLat = (offsetMeters * cos(bearingRad)) / 111320.0
        val offsetLon = (offsetMeters * sin(bearingRad)) / (111320.0 * cos(Math.toRadians(location.latitude)))

        val centerLat = geoPoint.latitude + offsetLat
        val centerLon = geoPoint.longitude + offsetLon

        mapboxMapView?.mapboxMap?.setCamera(
            CameraOptions.Builder()
                .center(MapboxPoint.fromLngLat(centerLon, centerLat))
                .zoom(currentZoom)
                .bearing(location.bearing.toDouble())
                .pitch(getNormalDrivingPitch())
                .build()
        )

        mapboxCurrentPosition = geoPoint
        mapboxCurrentBearing = location.bearing
        mapboxLastUpdateTime = SystemClock.elapsedRealtime()
        isFirstLocation = false
    }

    private fun updateZoomBasedOnSpeed(speed: Float) {
        val newTargetZoom = when {
            speed < 18 -> 19.5
            speed < 46 -> 18.5
            speed < 84 -> 17.5
            else -> 15.5
        }

        val currentTime = System.currentTimeMillis()
        if (newTargetZoom != targetZoom && (currentTime - lastZoomChangeTime) >= ZOOM_CHANGE_DELAY) {
            targetZoom = newTargetZoom
            lastZoomChangeTime = currentTime
        }
    }

    private fun startMapboxRenderLoop() {
        if (mapboxRenderRunnable != null) return
        val runnable = object : Runnable {
            override fun run() {
                if (!navSessionActive || isNavigationActive || !isAdded) {
                    stopMapboxRenderLoop()
                    return
                }
                updateMapboxMapAnimation()
                handler.postDelayed(this, 33L)
            }
        }
        mapboxRenderRunnable = runnable
        handler.post(runnable)
    }

    private fun stopMapboxRenderLoop() {
        mapboxRenderRunnable?.let { handler.removeCallbacks(it) }
        mapboxRenderRunnable = null
    }

    private fun updateMapboxMapAnimation() {
        val mapView = mapboxMapView ?: return
        if (!isAdded) return
        if (mapView.width < 50 || mapView.height < 50) return
        val targetPos = mapboxTargetPosition ?: return

        if (mapboxCurrentPosition == null) {
            mapboxCurrentPosition = targetPos
            mapboxCurrentBearing = mapboxTargetBearing
            mapboxLastUpdateTime = SystemClock.elapsedRealtime()
        }

        val now = SystemClock.elapsedRealtime()
        val elapsed = (now - mapboxLastUpdateTime).coerceAtMost(100)
        val progress = (elapsed / 100f).coerceIn(0f, 1f)

        val isLandscape = mapView.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val currentPos = mapboxCurrentPosition!!
        val distanceMeters = currentPos.distanceToAsDouble(targetPos)
        val landscapeFactor = if (isLandscape) 0.9 else 1.0
        val speedBasedStep = (lastSpeedKmh * 0.07).coerceIn(1.2, 12.0)
        val maxStepMeters = speedBasedStep * landscapeFactor
        val fraction = if (distanceMeters > 0.0) {
            kotlin.math.min(1.0, maxStepMeters / distanceMeters)
        } else {
            1.0
        }
        val smoothNewLat = currentPos.latitude + (targetPos.latitude - currentPos.latitude) * fraction
        val smoothNewLon = currentPos.longitude + (targetPos.longitude - currentPos.longitude) * fraction
        val smoothPosition = GeoPoint(smoothNewLat, smoothNewLon)

        var bearingDiff = mapboxTargetBearing - mapboxCurrentBearing
        while (bearingDiff > 180f) bearingDiff -= 360f
        while (bearingDiff < -180f) bearingDiff += 360f
        val bearingSmoothing = when {
            kotlin.math.abs(bearingDiff) > 90f -> 0.1f
            kotlin.math.abs(bearingDiff) > 45f -> 0.15f
            else -> 0.25f
        }
        val smoothBearing = mapboxCurrentBearing + bearingDiff * bearingSmoothing
        val normalizedBearing = ((smoothBearing % 360f) + 360f) % 360f

        mapboxCurrentPosition = smoothPosition
        mapboxCurrentBearing = normalizedBearing
        mapboxLastUpdateTime = now

        val currentPosition = smoothPosition
        val currentBearing = normalizedBearing

        val currentZoomValue = mapView.mapboxMap.cameraState.zoom

        val metersPerPixel = 156543.03392 * cos(Math.toRadians(currentPosition.latitude)) / Math.pow(2.0, currentZoomValue)
        val offsetDp = if (isLandscape) 38.0 else 30.0
        var offsetMeters = offsetDp * mapView.resources.displayMetrics.density * metersPerPixel
        if (isNorthUpMode) {
            offsetMeters = 0.0
        }

        val bearingRad = Math.toRadians(currentBearing.toDouble())
        val offsetLat = (offsetMeters * cos(bearingRad)) / 111320.0
        val offsetLon = (offsetMeters * sin(bearingRad)) / (111320.0 * cos(Math.toRadians(currentPosition.latitude)))

        val newLat = currentPosition.latitude + offsetLat
        val newLon = currentPosition.longitude + offsetLon

        val currentCenter = mapView.mapboxMap.cameraState.center

        val smoothCameraLat: Double
        val smoothCameraLon: Double
        val currentCenterLat = currentCenter.latitude()
        val currentCenterLon = currentCenter.longitude()

        val latDiff = newLat - currentCenterLat
        val lonDiff = newLon - currentCenterLon
        smoothCameraLat = currentCenterLat + latDiff * 0.12
        smoothCameraLon = currentCenterLon + lonDiff * 0.12

        updateMapboxMapOrientation()

        val point = MapboxPoint.fromLngLat(smoothCameraLon, smoothCameraLat)
        val cameraBearing = if (isNorthUpMode) 0.0 else (-currentMapOrientation).toDouble()

        val zoomDiff = targetZoom - currentZoom
        val smoothZoom = if (kotlin.math.abs(zoomDiff) > 0.01) {
            currentZoom + zoomDiff * 0.08
        } else {
            currentZoom
        }

        if (kotlin.math.abs(zoomDiff) > 0.01) {
            currentZoom = smoothZoom
        }

        val pitch = getNormalDrivingPitch()

        mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(point)
                .bearing(cameraBearing)
                .zoom(smoothZoom)
                .pitch(pitch)
                .build()
        )
    }

    private fun updateMapboxMapOrientation() {
        var diff = targetMapOrientation - currentMapOrientation
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f

        val speed = foregroundService?.getCurrentSpeed() ?: 0f

        val smoothingFactor = when {
            kotlin.math.abs(diff) > 90f -> 0.15f
            kotlin.math.abs(diff) > 45f -> 0.12f
            kotlin.math.abs(diff) > 20f -> 0.08f
            speed > 50 -> 0.06f
            speed > 20 -> 0.05f
            else -> 0.04f
        }

        if (kotlin.math.abs(diff) > 0.5f) {
            currentMapOrientation += diff * smoothingFactor
            while (currentMapOrientation > 360f) currentMapOrientation -= 360f
            while (currentMapOrientation < 0f) currentMapOrientation += 360f
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
        stopLeanAngleUpdates()
        stopMapboxRenderLoop()
    }

    override fun onResume() {
        super.onResume()
        
        loadCachedWeatherData()
        updateEnvironmentDisplay()
        loadProfileInfo()
        updateZeroButtonVisibility()
        
        // ВАЖНО: Ако има currentDestination (route preview режим), НЕ приближаваме до локацията
        // Запазваме текущата camera позиция (zoom на маршрута)
        if (currentDestination == null) {
            // Само ако няма route preview, показваме last known location
            displayLastKnownLocationInstantly()
            displayLastKnownLocationInstantly() // ПРОФЕСИОНАЛНО РЕШЕНИЕ: Показваме last known location ВЕДНАГА за instant display
        }

        if (pendingExitAfterSave) {
            pendingExitAfterSave = false
            mapboxNavigation.setNavigationRoutes(emptyList())
            setNavigationActive(false)
            resetNavigationUiAfterStop()
            stopNavSessionTracking()
            setNormalSessionUiActive(false)
            resetNormalDrivingCameraState()
            enforcePitchZero = true
        } else if (shouldRestoreNormalSessionAfterRecreate) {
            shouldRestoreNormalSessionAfterRecreate = false
            enforcePitchZero = false
            startAndBindServiceIfNeeded()
            navSessionActive = true
            setNormalSessionUiActive(true)
            startMapboxRenderLoop()
            updateZeroButtonVisibility()
            updateLeanAngleVisibility()
            startLeanAngleUpdates()
        } else if (shouldRestoreNavigationAfterRecreate) {
            shouldRestoreNavigationAfterRecreate = false
            if (mapboxNavigation.getNavigationRoutes().isNotEmpty()) {
                setNavigationActive(true)
                if (this::navigationCamera.isInitialized) {
                    mapboxMapView?.post {
                        navigationCamera.requestNavigationCameraToFollowing()
                    }
                }
            }
        } else if (isNavigationActive && mapboxNavigation.getNavigationRoutes().isEmpty()) {
            setNavigationActive(false)
            resetNavigationUiAfterStop()
        }

        if (!isNavigationActive && currentDestination == null) {
            mapboxNavigation.setNavigationRoutes(emptyList())
            if (this::routeLineApi.isInitialized && this::routeLineView.isInitialized && currentMapboxStyle != null) {
                routeLineApi.clearRouteLine { value ->
                    routeLineView.renderClearRouteLineValue(currentMapboxStyle!!, value)
                }
            }
            currentMapboxStyle?.let { style ->
                routeArrowView?.render(style, routeArrowApi.clearArrows())
            }
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
        triggerFirstOpenWeatherFetchIfNeeded()
        startWeatherRefreshTimer()

        if (pendingRoutePreviewRestore) {
            restoreRoutePreviewIfNeeded()
        }
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
        stopWeatherRefreshTimer()
        
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
        stopLeanAngleUpdates()
        stopMapboxRenderLoop()
        stopWeatherRefreshTimer()
        routeWeatherPreviewOverlay?.clear()
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
        val icon = if (summaryIsRain) "🌧️" else "☀️"
        val summaryText = if (rainTimeText.isNotEmpty() && rainTimePrefix.isNotEmpty()) {
            "${icon}${rainChance3h}% $rainTimePrefix $rainTimeText"
        } else {
            "${icon}${rainChance3h}%"
        }
        val expandedText = "💨${currentWindKph.toInt()}km/h 💧${currentHumidity}% | $summaryText"
        llWeatherExpanded.text = expandedText
    }

    private data class HourEntry(val hour: WeatherApiHour, val timeMillis: Long)

    private fun parseWeatherHourMillis(timeText: String): Long? {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            sdf.isLenient = false
            sdf.parse(timeText)?.time
        } catch (_: Exception) {
            null
        }
    }

    private fun formatHourMillis(timeMillis: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timeMillis))
    }

    private fun isRainCondition(code: Int): Boolean {
        return when (code) {
            1063, 1087, 1150, 1153 -> true
            in 1180..1201 -> true
            in 1240..1246 -> true
            in 1273..1282 -> true
            else -> false
        }
    }

    private fun isRainHour(hour: WeatherApiHour, chanceThreshold: Int = 30): Boolean {
        return isRainCondition(hour.condition.code) || hour.will_it_rain == 1 || hour.chance_of_rain >= chanceThreshold
    }
    
    private fun updateAltitudeExpandedText() {
        val pressureText = "📊 ${currentPressure.toInt()} hPa"
        llAltitudeExpanded.text = pressureText
    }

    private fun isWeatherCacheStale(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val cachedTime = prefs.getLong("cached_weather_time", 0L)
        if (cachedTime == 0L) return true
        val now = System.currentTimeMillis()
        return now - cachedTime > WEATHER_REFRESH_INTERVAL_MS
    }

    private fun isWeatherFirstOpenDone(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        return prefs.getBoolean(PREF_WEATHER_FIRST_OPEN_DONE, false)
    }

    private fun markWeatherFirstOpenDone() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        prefs.edit().putBoolean(PREF_WEATHER_FIRST_OPEN_DONE, true).apply()
    }

    private fun triggerFirstOpenWeatherFetchIfNeeded() {
        if (isWeatherFirstOpenDone()) return
        val loc = currentLocation ?: mapStateViewModel.lastKnownLocation
        if (loc != null) {
            fetchWeatherFromAPI(loc)
            markWeatherFirstOpenDone()
            pendingFirstWeatherFetch = false
        } else {
            pendingFirstWeatherFetch = true
        }
    }

    private fun restoreRoutePreviewIfNeeded() {
        if (!pendingRoutePreviewRestore) return
        val dest = currentDestination ?: return
        if (!this::routeLineApi.isInitialized || !this::routeLineView.isInitialized) {
            mapboxMapView?.post { restoreRoutePreviewIfNeeded() }
            return
        }
        pendingRoutePreviewRestore = false
        setDestinationAndFindRoute(dest, currentDestinationName)
    }

    fun handleBackPressedFromActivity(): Boolean {
        if (navSessionActive) {
            showExitNormalSessionDialog()
            return true
        }
        if (isNavigationActive) {
            showExitNavigationDialog()
            return true
        }
        if (currentDestination != null) {
            cancelRoutePreview()
            return true
        }
        return false
    }

    private fun startWeatherRefreshTimer() {
        stopWeatherRefreshTimer()
        weatherRefreshRunnable = object : Runnable {
            override fun run() {
                if (!isAdded) return
                val loc = currentLocation
                if (loc != null && isWeatherCacheStale()) {
                    fetchWeatherFromAPI(loc)
                }
                weatherRefreshHandler.postDelayed(this, WEATHER_REFRESH_INTERVAL_MS)
            }
        }
        weatherRefreshHandler.post(weatherRefreshRunnable!!)
    }

    private fun stopWeatherRefreshTimer() {
        weatherRefreshRunnable?.let { weatherRefreshHandler.removeCallbacks(it) }
        weatherRefreshRunnable = null
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

        if (isNavigationActive || navSessionActive || currentDestination != null) {
            llTemperature.visibility = LinearLayout.GONE
            llAltitude.visibility = LinearLayout.GONE
            return
        }

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
        val cachedRainChance = prefs.getInt("cached_rain_chance", -1)
        val cachedRainTime = prefs.getString("cached_rain_time", "") ?: ""
        val cachedRainPrefix = prefs.getString("cached_rain_prefix", "") ?: ""
        val cachedSummaryIsRain = prefs.getBoolean("cached_summary_is_rain", true)
        
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

        if (cachedRainChance >= 0) {
            rainChance3h = cachedRainChance
        }
        rainTimeText = cachedRainTime
        rainTimePrefix = cachedRainPrefix
        summaryIsRain = cachedSummaryIsRain
        updateWeatherExpandedText()
    }
    
    private fun cacheWeatherData(location: Location) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = prefs.edit()
        
        currentTemperature?.let { editor.putFloat("cached_temperature", it) }
        currentAltitude?.let { editor.putFloat("cached_altitude", it) }
        editor.putLong("cached_weather_time", System.currentTimeMillis())
        editor.putFloat("cached_location_lat", location.latitude.toFloat())
        editor.putFloat("cached_location_lon", location.longitude.toFloat())
        editor.putInt("cached_weather_icon", currentWeatherIcon)
        editor.putFloat("cached_wind_kph", currentWindKph.toFloat())
        editor.putInt("cached_humidity", currentHumidity)
        editor.putFloat("cached_pressure", currentPressure.toFloat())
        editor.putInt("cached_rain_chance", rainChance3h)
        editor.putString("cached_rain_time", rainTimeText)
        editor.putString("cached_rain_prefix", rainTimePrefix)
        editor.putBoolean("cached_summary_is_rain", summaryIsRain)
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
        val cachedTime = prefs.getLong("cached_weather_time", 0L)
        val cachedIcon = prefs.getInt("cached_weather_icon", -1)
        val cachedRainTime = prefs.getString("cached_rain_time", "") ?: ""
        val now = System.currentTimeMillis()
        if (cachedTime == 0L || now - cachedTime > CACHE_WEATHER_MAX_AGE_MS) {
            return true
        }

        if (cachedRainTime.isBlank()) {
            return true
        }

        if (cachedIcon == R.drawable.ic_weather_rainy && cachedRainTime.isBlank()) {
            return true
        }
        
        return false
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
                            apiKey = WEATHER_API_KEY,
                    location = "${location.latitude},${location.longitude}",
                    lang = "bg"
                )
                
                if (weatherResponse.isSuccessful && weatherResponse.body() != null) {
                    val weather = weatherResponse.body()!!
                    currentTemperature = weather.current.temp_c.toFloat()
                    android.util.Log.d(
                        "MapFragment",
                        "WeatherAPI temp_c=${weather.current.temp_c}, pressure_mb=${weather.current.pressure_mb}, humidity=${weather.current.humidity}"
                    )
                    
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
                        val nowMillis = System.currentTimeMillis()
                        val hourEntries = hours.mapNotNull { hour ->
                            parseWeatherHourMillis(hour.time)?.let { HourEntry(hour, it) }
                        }.sortedBy { it.timeMillis }

                        val rainThreshold = 30
                        val currentHourEntry = hourEntries.lastOrNull { it.timeMillis <= nowMillis }
                            ?: hourEntries.firstOrNull()

                        val isRainingNow = (weather.current.precip_mm ?: 0.0) > 0.0 ||
                            isRainCondition(weather.current.condition.code) ||
                            currentWeatherIcon == R.drawable.ic_weather_rainy ||
                            (currentHourEntry?.let { isRainHour(it.hour, rainThreshold) } == true)

                        rainChance3h = 0
                        rainTimeText = ""
                        rainTimePrefix = ""
                        summaryIsRain = true

                        if (currentHourEntry != null) {
                            if (isRainingNow) {
                                val startIndex = hourEntries.indexOf(currentHourEntry)
                                if (startIndex >= 0) {
                                    var endIndex = startIndex
                                    while (endIndex + 1 < hourEntries.size && isRainHour(hourEntries[endIndex + 1].hour, rainThreshold)) {
                                        endIndex++
                                    }

                                    val endTimeMillis = if (endIndex + 1 < hourEntries.size) {
                                        hourEntries[endIndex + 1].timeMillis
                                    } else {
                                        hourEntries[endIndex].timeMillis + 60 * 60 * 1000L
                                    }

                                    rainChance3h = hourEntries.subList(startIndex, endIndex + 1)
                                        .maxOfOrNull { it.hour.chance_of_rain } ?: 0
                                    rainTimeText = formatHourMillis(endTimeMillis)
                                    rainTimePrefix = getString(R.string.weather_until)
                                    summaryIsRain = true
                                }
                            } else {
                                val nextRainEntry = hourEntries.firstOrNull {
                                    it.timeMillis >= nowMillis && isRainHour(it.hour, rainThreshold)
                                }
                                if (nextRainEntry != null) {
                                    rainChance3h = nextRainEntry.hour.chance_of_rain
                                    rainTimeText = formatHourMillis(nextRainEntry.timeMillis)
                                    rainTimePrefix = getString(R.string.weather_from)
                                    summaryIsRain = true
                                } else {
                                    val futureEntries = hourEntries.filter { it.timeMillis >= nowMillis }
                                    val maxRainChance = futureEntries.maxOfOrNull { it.hour.chance_of_rain } ?: 0
                                    val minRainEntry = futureEntries.minByOrNull { it.hour.chance_of_rain }
                                    val sunChance = (100 - maxRainChance).coerceIn(0, 100)
                                    val timeMillis = minRainEntry?.timeMillis ?: nowMillis
                                    rainChance3h = sunChance
                                    rainTimeText = formatHourMillis(timeMillis)
                                    rainTimePrefix = getString(R.string.weather_from)
                                    summaryIsRain = false
                                }
                            }
                        }
                    } ?: run {
                        rainChance3h = 0
                        rainTimeText = ""
                        rainTimePrefix = ""
                        summaryIsRain = true
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
                    android.util.Log.d("MapFragment", "OpenMeteo elevation=${currentAltitude}m")
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

                mapControlsContainer?.let { controls ->
                    val params = controls.layoutParams as? android.widget.RelativeLayout.LayoutParams
                    params?.topMargin = resources.getDimensionPixelSize(R.dimen.map_controls_margin_top_landscape)
                    controls.layoutParams = params
                }
                
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
