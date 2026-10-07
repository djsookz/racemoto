package com.revix.app.main.map

import com.revix.app.garage.GarageReminderInbox

import com.revix.app.*
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
import com.revix.app.navigation.NavigationVoiceGuidance
import com.revix.app.averagespeed.AverageSpeedController
import com.revix.app.averagespeed.AverageSpeedProgressView
import com.revix.app.averagespeed.AverageSpeedSignView
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
import com.mapbox.geojson.Point as MapboxPoint
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.MapView as MapboxMapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
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
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.OnScaleListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.android.gestures.StandardScaleGestureDetector
import com.mapbox.api.directions.v5.DirectionsCriteria
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
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraStateChangedObserver
import com.mapbox.navigation.ui.maps.camera.transition.NavigationCameraTransitionOptions
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.revix.app.navigation.MapboxGeocodingService
import com.revix.app.navigation.GeocodingFeature
import com.revix.app.navigation.CategoryFeature
import com.revix.app.navigation.CategoryResponse
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.revix.app.data.ProfileStorage
import com.revix.app.data.SessionFuelStorage
import com.revix.app.garage.GarageFuelEntryActivity
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.revix.app.settings.LanguageManager
import com.revix.app.settings.UnitsManager
import com.revix.app.settings.VoiceAlertsSettings
import com.revix.app.network.OpenMeteoService
import com.revix.app.network.WeatherApiService
import com.revix.app.network.WeatherApiHour
import com.revix.app.preview.RouteWeatherPreviewOverlay
import com.revix.app.utils.WeatherIconMapper
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.revix.app.racebox.RaceBoxDebugGate
import com.revix.app.racebox.RaceBoxManager
import com.revix.app.racebox.RaceBoxProtocol
import com.mapbox.common.location.Location as MapboxLocation
import com.revix.app.main.MainContainerActivity
import com.revix.app.main.location.LocationPermissionHelper
import com.revix.app.main.ui.ManeuverUiBinder
import com.revix.app.main.ui.ManeuverViewConfigurator
import kotlin.math.abs
import com.revix.app.RouteStorage
import com.revix.app.RouteSnapshotGenerator
import com.revix.app.Race
import com.revix.app.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import com.revix.app.reports.ReportAlertsCoordinator
import com.revix.app.reports.ReportsIntegration
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
class MapFragment : Fragment() {

    private data class SavedDestination(
        val name: String,
        val latitude: Double,
        val longitude: Double
    )

    private enum class QuickListMode {
        RECENT,
        FAVORITES
    }
    
    // Map views
    private var mapboxMapView: MapboxMapView? = null // Mapbox MapView (nullable)
    
    // Reports System
    private var reportsIntegration: ReportsIntegration? = null
    private var averageSpeedController: AverageSpeedController? = null
    private lateinit var fabReport: View
    private lateinit var fabFuel: View
    private var lastReportsQueryLocation: Location? = null // За throttling на Firebase queries
    private val MIN_DISTANCE_FOR_REPORTS_UPDATE = 5000f // 5 км в метри
    private var currentRawLocation: Location? = null
    private val reportMergeDistanceUrbanMeters = 60.0
    private val reportMergeDistanceRuralMeters = 120.0
    private val reportSettlementRadiusMeters = 2000.0
    private val reportMergeDistanceFallbackMeters = 100.0
    private var mapRootLayoutChangeListener: View.OnLayoutChangeListener? = null
    private var mapRootObservedView: View? = null
    private var lastKnownLayoutOrientation: Int = android.content.res.Configuration.ORIENTATION_UNDEFINED
    private val fabRepositionRunnable = Runnable {
        if (!isAdded || view == null) return@Runnable
        repositionFabReport()
    }
    
    // UI Elements
    private lateinit var btnStartSession: MaterialButton
    private lateinit var btnSessions: MaterialButton
    private lateinit var destinationSearchContainer: LinearLayout
    private lateinit var searchContainer: LinearLayout
    private lateinit var searchInputContainer: LinearLayout
    private var quickPanelContainer: View? = null
    private var quickCategoriesContainer: View? = null
    private var quickDividerTop: View? = null
    private var quickDividerBottom: View? = null
    private lateinit var etSearch: TextInputEditText
    private var quickHomeButton: LinearLayout? = null
    private var quickWorkButton: LinearLayout? = null
    // Category buttons
    private var btnCategoryFavorites: View? = null
    private var btnCategoryGas: View? = null
    private var btnCategoryParking: View? = null
    private var btnCategoryFood: View? = null
    private var btnCategoryCoffee: View? = null
    private var tvQuickHomeSubtitle: TextView? = null
    private var tvQuickWorkSubtitle: TextView? = null
    private var tvQuickListHeader: TextView? = null
    private lateinit var rvSearchResults: RecyclerView
    private var poiBottomSheetContainer: LinearLayout? = null
    private var tvPoiBottomSheetTitle: TextView? = null
    private var rvPoiBottomSheetResults: RecyclerView? = null
    private lateinit var searchResultsAdapter: SearchResultsAdapter
    private var poiResultsAdapter: SearchResultsAdapter? = null
    private lateinit var geocodingService: MapboxGeocodingService
    private var mapboxAccessToken: String = ""
    private var quickListMode: QuickListMode = QuickListMode.RECENT
    private var isCategorySearchOverlayActive: Boolean = false
    private var isPoiCategoryModeActive: Boolean = false
    private var activePOICategoryId: String? = null
    private var activePOISearchFeatures: List<GeocodingFeature> = emptyList()
    private var poiMapCameraPadding: EdgeInsets? = null
    private var pendingMapPickCategory: QuickDestinationCategory? = null
    private var pendingSearchAssignmentCategory: QuickDestinationCategory? = null
    private var isShortcutMapPickInFlight: Boolean = false
    private var inlineSearchRequestId: Long = 0L
    private val inlineSearchDebounceHandler = Handler(Looper.getMainLooper())
    private var inlineSearchDebounceRunnable: Runnable? = null
    private var suppressInlineSearchFocusRestore: Boolean = false
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
    private var isTripCameraInOverview: Boolean = false
    private val navigationCameraStateObserver = NavigationCameraStateChangedObserver { state ->
        isTripCameraInOverview = state == NavigationCameraState.OVERVIEW ||
            state == NavigationCameraState.TRANSITION_TO_OVERVIEW
        if (isAdded) {
            updateTripCameraToggleUi()
        }
    }
    private var mapControlsContainer: LinearLayout? = null
    private var btnOrientationToggle: ImageButton? = null
    private var btnCameraNorthMode: ImageButton? = null
    private var btnVoiceGuidance: ImageButton? = null
    private var isOrientationLocked: Boolean = false
    private var isNorthUpMode: Boolean = false
    private var enforcePitchZero: Boolean = true
    private var isApplyingPitchZero: Boolean = false
    private var allowMotorways: Boolean = false
    private var garageReminderBannerKey: String? = null
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
    private lateinit var idleMapSpeedContainer: FrameLayout
    private lateinit var idleMapLeftChrome: LinearLayout
    private var idleAverageSpeedSignSlot: View? = null
    private var averageSpeedHudCluster: View? = null
    private lateinit var tvIdleMapSpeed: TextView
    private lateinit var tvHeaderModelName: TextView
    private lateinit var ivHeaderProfileImage: ImageView
    private lateinit var llActiveProfileHeader: LinearLayout
    
    // Inline route preview (draw routes on the same map, like TestNavigationActivity)
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView
    private var currentMapboxStyle: Style? = null
    private var currentDestination: Point? = null
    private var currentDestinationName: String? = null
    private fun navigationOverviewPadding(): EdgeInsets {
        val top = resources.getDimension(R.dimen.mapbox_overview_padding_top).toDouble()
        val left = resources.getDimension(R.dimen.mapbox_overview_padding_left).toDouble()
        val bottom = resources.getDimension(R.dimen.mapbox_overview_padding_bottom).toDouble()
        val right = resources.getDimension(R.dimen.mapbox_overview_padding_right).toDouble()
        return EdgeInsets(top, left, bottom, right)
    }

    private fun navigationFollowingPadding(): EdgeInsets {
        val top = resources.getDimension(R.dimen.mapbox_following_padding_top).toDouble()
        val left = resources.getDimension(R.dimen.mapbox_following_padding_left).toDouble()
        val bottom = resources.getDimension(R.dimen.mapbox_following_padding_bottom).toDouble()
        val right = resources.getDimension(R.dimen.mapbox_following_padding_right).toDouble()
        return EdgeInsets(top, left, bottom, right)
    }

    private fun refreshNavigationViewportPadding() {
        if (!this::viewportDataSource.isInitialized) return
        viewportDataSource.overviewPadding = navigationOverviewPadding()
        viewportDataSource.followingPadding = navigationFollowingPadding()
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
    /** Route line + free-ride camera/session; no Mapbox trip session (no Navigation MAU). */
    private var isFollowLineActive: Boolean = false
    private var hasReachedDestination: Boolean = false
    private lateinit var maneuverApi: MapboxManeuverApi
    private var maneuverContainer: View? = null
    private var maneuverView: MapboxManeuverView? = null
    private var btnCloseManeuver: View? = null
    private var tripProgressContainer: LinearLayout? = null
    private var tvTripEta: TextView? = null
    private var tvTripRemainingTime: TextView? = null
    private var tvTripRemainingDistance: TextView? = null
    private val routeArrowApi: MapboxRouteArrowApi by lazy { MapboxRouteArrowApi() }
    private var routeArrowView: MapboxRouteArrowView? = null
    private var onIndicatorPositionChangedListener: OnIndicatorPositionChangedListener? = null
    private val liveFollowPuckCameraListener = OnIndicatorPositionChangedListener { point ->
        puckIndicatorPosition = GeoPoint(point.latitude(), point.longitude())
    }
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
    private var distanceUnitCar: TextView? = null
    private var chronometerCar: Chronometer? = null
    private var buttonContainer: ViewGroup? = null
    private var buttonActionsRow: LinearLayout? = null
    private var arrivalActionContainer: LinearLayout? = null
    private var btnReset: View? = null
    private var btnZero: View? = null
    private var btnStop: View? = null
    private var btnArrivalSaveFinish: View? = null
    private var btnArrivalContinue: View? = null
    private var btnArrivalDelete: View? = null
    private var isArrivalActionVisible: Boolean = false
    private var angleContainerMoto: LinearLayout? = null
    private var angleTextMoto: TextView? = null
    private var linearGaugeView: LinearGaugeView? = null
    private var navSessionActive: Boolean = false
    private var activeSessionKey: Long = 0L
    private var navSessionStartTime: Long = 0L
    private var navSessionDistanceMeters: Double = 0.0
    private var navSessionLastLocation: Location? = null
    private var mapboxTargetPosition: GeoPoint? = null
    private var mapboxSmoothedTargetPosition: GeoPoint? = null
    private var puckIndicatorPosition: GeoPoint? = null
    private var mapboxTargetBearing: Float = 0f
    private var mapboxCurrentPosition: GeoPoint? = null
    private var mapboxCurrentCameraCenter: GeoPoint? = null
    private var mapboxCurrentBearing: Float = 0f
    private var mapboxLastUpdateTime: Long = 0L
    private var suppressMapCameraUpdatesUntil: Long = 0L
    private var startupCameraHandoffUntil: Long = 0L
    private var startupFollowStabilizeUntil: Long = 0L
    private var startupIntroUntil: Long = 0L
    private var mapboxRenderRunnable: Runnable? = null
    private var targetMapOrientation: Float = 0f
    private var currentMapOrientation: Float = 0f
    private var lastCalculatedBearing: Float = 0f
    private var lastAlertLocation: Location? = null
    private var lastProcessedLocation: Location? = null
    private var isFirstLocation: Boolean = true
    private var lastSpeedKmh: Float = 0f
    private var targetZoom: Double = 17.5
    private var currentZoom: Double = 17.5
    private var targetPitch: Double = 55.0
    private var currentPitch: Double = 55.0
    private var isMapboxLocationComponentEnabled: Boolean = false
    private var isUsingRawPuckLocationProvider: Boolean = false
    private var isIdleMapFollowEnabled: Boolean = true
    private var idleFollowProgrammaticCameraMove: Boolean = false
    private var idleMapFollowMoveListener: OnMoveListener? = null
    private var idleMapFollowScaleListener: OnScaleListener? = null
    private var idleFollowTargetPosition: GeoPoint? = null
    private var idleFollowSmoothPosition: GeoPoint? = null
    private var idleFollowLastFrameTime: Long = 0L
    private var idleMapRenderRunnable: Runnable? = null
    private val navigationLocationProvider = NavigationLocationProvider()
    private val rawPuckLocationProvider = NavigationLocationProvider()
    /** Phone GPS for idle / free-ride — avoids Mapbox Free Drive trip session (Navigation MAU). */
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var nonNavLocationCallback: LocationCallback? = null
    private var nonNavLocationUpdatesActive: Boolean = false
    private val raceBoxMapSampleListener: (RaceBoxProtocol.Sample) -> Unit = listener@{ sample ->
        if (!isAdded || context == null) return@listener
        if (!RaceBoxDebugGate.shouldOverridePhoneGps(requireContext())) return@listener
        if (!sample.fixOk) return@listener
        // Free-drive / idle: prefer RaceBox over phone GPS (no Mapbox trip session).
        if (!isNavigationActive) {
            ingestNonNavigationLocation(sample.location)
        }
    }
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
                if (isNavigationActive) {
                    stopNonNavigationLocationUpdates()
                    context?.let { NavigationVoiceGuidance.start(it, mapboxNavigation) }
                } else {
                    // Previously: startTripSession() here → Free Drive billing + Navigation MAU.
                    // Backup of that behavior: .backup/pre-free-ride-no-mau/
                    try {
                        mapboxNavigation.stopTripSession()
                    } catch (_: Exception) {
                    }
                    startNonNavigationLocationUpdates()
                }
            }

            override fun onDetached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.unregisterLocationObserver(navigationLocationObserver)
                if (navigationObserversRegistered) {
                    mapboxNavigation.unregisterRoutesObserver(sdkRoutesObserver)
                    mapboxNavigation.unregisterRouteProgressObserver(sdkRouteProgressObserver)
                    mapboxNavigation.unregisterArrivalObserver(sdkArrivalObserver)
                    navigationObserversRegistered = false
                }
                stopNonNavigationLocationUpdates()
                if (!isNavigationActive) {
                    context?.let { NavigationVoiceGuidance.stop(it, mapboxNavigation) }
                    try {
                        mapboxNavigation.stopTripSession()
                    } catch (_: Exception) {
                    }
                }
            }
        },
        onInitialize = this::initNavigation
    )
    private val navigationLocationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: com.mapbox.common.location.Location) {
            rawPuckLocationProvider.changePosition(rawLocation, emptyList())

            currentRawLocation = Location("mapbox-raw").apply {
                latitude = rawLocation.latitude
                longitude = rawLocation.longitude
                time = System.currentTimeMillis()
                rawLocation.speed?.let { speed = it.toFloat() }
                rawLocation.bearing?.let { bearing = it.toFloat() }
                rawLocation.altitude?.let { altitude = it }
            }

            if (!isUsingRawPuckLocationProvider) {
                val mapView = mapboxMapView
                if (mapView != null) {
                    val locationPlugin = mapView.getPlugin(Plugin.MAPBOX_LOCATION_COMPONENT_PLUGIN_ID) as? LocationComponentPlugin
                    locationPlugin?.setLocationProvider(rawPuckLocationProvider)
                }
                isUsingRawPuckLocationProvider = true
                Log.d("MapFragment", "✅ Switched puck to raw GPS provider")
            }
        }

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhanced = locationMatcherResult.enhancedLocation

            // Keep matched location available for route/camera logic, but do not drive the visual puck with it.
            navigationLocationProvider.changePosition(enhanced, locationMatcherResult.keyPoints)

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

            // Update reports only when moved 5+ km (optimize Firebase queries).
            // Skip while along-route police/camera markers are active (preview or navigation).
            val lastQueryLoc = lastReportsQueryLocation
            if (
                reportsIntegration?.isRoutePreviewReportsActive() != true &&
                (lastQueryLoc == null || androidLoc.distanceTo(lastQueryLoc) > MIN_DISTANCE_FOR_REPORTS_UPDATE)
            ) {
                reportsIntegration?.startObservingReports(
                    centerLatitude = androidLoc.latitude,
                    centerLongitude = androidLoc.longitude,
                    radiusKm = 20.0
                )
                lastReportsQueryLocation = androidLoc
                Log.d("MapFragment", "📍 Updated reports query (moved ${lastQueryLoc?.distanceTo(androidLoc)?.div(1000)}+ km)")
            }
            // Realtime listener continues to push updates automatically
            
            if (ReportAlertsCoordinator.mapTabActive) {
                val bearing = resolveAlertMovementBearing(androidLoc)
                reportsIntegration?.checkForDrivingAlerts(
                    location = androidLoc,
                    bearing = bearing
                )
            }
            averageSpeedController?.onLocation(androidLoc)

            if (pendingFirstWeatherFetch && !isWeatherFirstOpenDone()) {
                fetchWeatherFromAPI(androidLoc)
                markWeatherFirstOpenDone()
                pendingFirstWeatherFetch = false
            }

            if (this@MapFragment::viewportDataSource.isInitialized) {
                viewportDataSource.onLocationChanged(enhanced)
                if (isNavigationActive) {
                    viewportDataSource.evaluate()
                }
            }

            if (isNavigationActive || navSessionActive) {
                updateNavSessionMetrics(androidLoc)
            }

            if (navSessionActive && !isNavigationActive) {
                // Free-drive camera: RaceBox when connected, else raw GPS (same as puck).
                if (!(RaceBoxDebugGate.shouldOverridePhoneGps(requireContext()) &&
                        RaceBoxManager.latestSample()?.fixOk == true)
                ) {
                    processNormalDrivingLocation(currentRawLocation ?: androidLoc)
                }
            }

            // Camera follow on the idle map (no active session / navigation).
            if (!navSessionActive && !isNavigationActive) {
                if (!(RaceBoxDebugGate.shouldOverridePhoneGps(requireContext()) &&
                        RaceBoxManager.latestSample()?.fixOk == true)
                ) {
                    if (!mapStateViewModel.hasInitializedCamera) {
                        mapboxMapView?.mapboxMap?.setCamera(
                            CameraOptions.Builder()
                                .center(MapboxPoint.fromLngLat(androidLoc.longitude, androidLoc.latitude))
                                .zoom(IDLE_MAP_FOLLOW_ZOOM)
                                .bearing(0.0)
                                .pitch(0.0)
                                .padding(idleMapFollowPadding())
                                .build()
                        )
                        mapStateViewModel.hasInitializedCamera = true
                        mapStateViewModel.saveLastLocation(androidLoc)
                        mapStateViewModel.saveMapState(
                            androidLoc.latitude,
                            androidLoc.longitude,
                            IDLE_MAP_FOLLOW_ZOOM,
                            0.0
                        )
                        revealMapWhenCameraReady()
                    }
                    feedIdleMapFollowTarget(androidLoc)
                }
            }

            // Weather fetch (only if needed)
            if (isAdded && context != null && shouldFetchWeatherData(androidLoc)) {
                fetchWeatherFromAPI(androidLoc)
            }
        }
    }

    /**
     * Idle map + free-ride location path that does not use Mapbox trip session
     * (so it does not count Navigation SDK MAU / Free Drive trips).
     */
    private fun ingestNonNavigationLocation(location: Location) {
        if (!isAdded || context == null || isNavigationActive) return

        currentRawLocation = location
        currentLocation = location
        mapStateViewModel.saveLastLocation(location)
        updateRawPuckFromAndroidLocation(location)

        val lastQueryLoc = lastReportsQueryLocation
        if (
            reportsIntegration?.isRoutePreviewReportsActive() != true &&
            (lastQueryLoc == null || location.distanceTo(lastQueryLoc) > MIN_DISTANCE_FOR_REPORTS_UPDATE)
        ) {
            reportsIntegration?.startObservingReports(
                centerLatitude = location.latitude,
                centerLongitude = location.longitude,
                radiusKm = 20.0
            )
            lastReportsQueryLocation = location
        }

        if (ReportAlertsCoordinator.mapTabActive) {
            val bearing = resolveAlertMovementBearing(location)
            reportsIntegration?.checkForDrivingAlerts(
                location = location,
                bearing = bearing
            )
        }
        averageSpeedController?.onLocation(location)

        if (pendingFirstWeatherFetch && !isWeatherFirstOpenDone()) {
            fetchWeatherFromAPI(location)
            markWeatherFirstOpenDone()
            pendingFirstWeatherFetch = false
        }

            if (navSessionActive) {
            updateNavSessionMetrics(location)
            processNormalDrivingLocation(location)
        } else {
            if (!mapStateViewModel.hasInitializedCamera) {
                applyIdleStartupCamera(force = true)
                // First fix: snap camera to GPS before idle follow starts (no globe → jump).
                mapboxMapView?.mapboxMap?.setCamera(
                    CameraOptions.Builder()
                        .center(MapboxPoint.fromLngLat(location.longitude, location.latitude))
                        .zoom(IDLE_MAP_FOLLOW_ZOOM)
                        .bearing(0.0)
                        .pitch(0.0)
                        .padding(idleMapFollowPadding())
                        .build()
                )
                mapStateViewModel.hasInitializedCamera = true
                mapStateViewModel.saveLastLocation(location)
                mapStateViewModel.saveMapState(
                    location.latitude,
                    location.longitude,
                    IDLE_MAP_FOLLOW_ZOOM,
                    0.0
                )
                revealMapWhenCameraReady()
            }
            feedIdleMapFollowTarget(location)
        }

        if (shouldFetchWeatherData(location)) {
            fetchWeatherFromAPI(location)
        }
    }

    private fun updateRawPuckFromAndroidLocation(location: Location) {
        val mapboxLoc = androidLocationToMapboxLocation(location)
        rawPuckLocationProvider.changePosition(mapboxLoc, emptyList())
        navigationLocationProvider.changePosition(mapboxLoc, emptyList())

        if (!isUsingRawPuckLocationProvider) {
            val mapView = mapboxMapView
            if (mapView != null) {
                val locationPlugin =
                    mapView.getPlugin(Plugin.MAPBOX_LOCATION_COMPONENT_PLUGIN_ID) as? LocationComponentPlugin
                locationPlugin?.setLocationProvider(rawPuckLocationProvider)
            }
            isUsingRawPuckLocationProvider = true
        }
    }

    private fun androidLocationToMapboxLocation(location: Location): MapboxLocation {
        val builder = MapboxLocation.Builder()
            .latitude(location.latitude)
            .longitude(location.longitude)
            .timestamp(if (location.time > 0L) location.time else System.currentTimeMillis())
        if (location.hasSpeed()) builder.speed(location.speed.toDouble())
        if (location.hasBearing()) builder.bearing(location.bearing.toDouble())
        if (location.hasAltitude()) builder.altitude(location.altitude)
        if (location.hasAccuracy()) builder.horizontalAccuracy(location.accuracy.toDouble())
        return builder.build()
    }

    private fun startNonNavigationLocationUpdates() {
        if (!isAdded || context == null || isNavigationActive || nonNavLocationUpdatesActive) return
        if (!checkLocationPermission()) return

        val client = fusedLocationClient
            ?: LocationServices.getFusedLocationProviderClient(requireContext()).also {
                fusedLocationClient = it
            }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(0f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!isAdded || isNavigationActive) return
                if (RaceBoxDebugGate.shouldOverridePhoneGps(requireContext()) &&
                    RaceBoxManager.latestSample()?.fixOk == true
                ) {
                    return
                }
                val loc = result.lastLocation ?: return
                ingestNonNavigationLocation(loc)
            }
        }
        nonNavLocationCallback = callback
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            nonNavLocationUpdatesActive = true
            Log.d("MapFragment", "Started non-navigation fused location (no Mapbox trip session)")
        } catch (e: SecurityException) {
            Log.w("MapFragment", "Unable to request fused location updates", e)
            nonNavLocationCallback = null
            nonNavLocationUpdatesActive = false
        }
    }

    private fun stopNonNavigationLocationUpdates() {
        val callback = nonNavLocationCallback ?: return
        try {
            fusedLocationClient?.removeLocationUpdates(callback)
        } catch (_: Exception) {
        }
        nonNavLocationCallback = null
        nonNavLocationUpdatesActive = false
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
            if (navSessionActive) {
                val serviceDistanceMeters = foregroundService?.getTotalDistanceMeters()?.coerceAtLeast(0.0) ?: 0.0
                // Keep the largest known value so lock/unlock/rebind cannot visually reset distance.
                navSessionDistanceMeters = maxOf(navSessionDistanceMeters, serviceDistanceMeters)
                updateDistanceCarDisplay()
            }
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

        try {
            val primaryRoute = primaryOnly.first()
            val geometryString = primaryRoute.directionsRoute.geometry()
            val routeGeometry = geometryString?.let { LineString.fromPolyline(it, 6) }
            reportsIntegration?.updateRouteGeometry(routeGeometry, isNavigationActive)
            syncAverageSpeedRoute()
            // Markers stay on live nearby observe during navigation; only route geometry
            // for alert matching is refreshed on reroute (along-route fetch is preview-only).
        } catch (e: Exception) {
            Log.e("MapFragment", "Failed to refresh alert route geometry", e)
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
                val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                ManeuverViewConfigurator.renderManeuvers(
                    maneuverApi,
                    maneuverView,
                    routeProgress,
                    isLandscape
                )
                val maneuverWasHidden = maneuverContainer?.visibility != View.VISIBLE
                maneuverContainer?.visibility = View.VISIBLE
                if (maneuverWasHidden) {
                    repositionMapControlsForActiveSession()
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

        val totalMinutes = (durationRemainingSeconds / 60).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val timeRemainingText = if (hours > 0) {
            getString(R.string.route_duration_hours_minutes, hours, minutes)
        } else {
            getString(R.string.map_trip_remaining_minutes, minutes.coerceAtLeast(0))
        }

        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.SECOND, durationRemainingSeconds.toInt())
        val etaText = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(calendar.time)

        tvTripEta?.text = etaText
        tvTripRemainingTime?.text = timeRemainingText
        tvTripRemainingDistance?.text = getString(R.string.map_trip_bar_distance, distanceKm)
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
    private var shouldRestoreFollowLineAfterRecreate: Boolean = false
    private var navBackPressedCallback: OnBackPressedCallback? = null
    private var pendingExitAfterSave: Boolean = false
    
    // Weather details
    private var currentWindKph: Double = 0.0
    private var currentWindDir: String = ""
    private var currentHumidity: Int = 0
    private var currentCloudCover: Int = 0
    private var currentIsDay: Boolean = true
    private var rainChance3h: Int = 0
    private var rainTimeText: String = ""
    private var rainTimePrefixKind: RainTimePrefixKind = RainTimePrefixKind.NONE
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
        outState.putBoolean(KEY_NAV_ACTIVE, isNavigationActive && !pendingExitAfterSave)
        outState.putBoolean(KEY_SESSION_ACTIVE, navSessionActive && !isNavigationActive && !pendingExitAfterSave)
        outState.putBoolean(KEY_FOLLOW_LINE_ACTIVE, isFollowLineActive && !pendingExitAfterSave)
        outState.putLong(KEY_ACTIVE_SESSION_KEY, activeSessionKey)
        outState.putDouble(KEY_SESSION_DISTANCE_METERS, navSessionDistanceMeters)
        val hasPreview = currentDestination != null &&
            !isNavigationActive &&
            !isFollowLineActive &&
            !navSessionActive
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
        
        // Activity-scoped so pager tab switches do not wipe last camera / location.
        mapStateViewModel = ViewModelProvider(requireActivity())[MapStateViewModel::class.java]
        mapStateViewModel.hydrate(requireContext())
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
        shouldRestoreFollowLineAfterRecreate = savedInstanceState?.getBoolean(KEY_FOLLOW_LINE_ACTIVE) == true
        navSessionDistanceMeters = savedInstanceState?.getDouble(KEY_SESSION_DISTANCE_METERS, 0.0) ?: 0.0
        activeSessionKey = savedInstanceState?.getLong(KEY_ACTIVE_SESSION_KEY, 0L) ?: 0L
        pendingRoutePreviewRestore = savedInstanceState?.getBoolean(KEY_PREVIEW_ACTIVE) == true &&
            !shouldRestoreFollowLineAfterRecreate
        if (savedInstanceState?.containsKey(KEY_PREVIEW_DEST_LAT) == true &&
            savedInstanceState.containsKey(KEY_PREVIEW_DEST_LON)
        ) {
            currentDestination = Point.fromLngLat(
                savedInstanceState.getDouble(KEY_PREVIEW_DEST_LON),
                savedInstanceState.getDouble(KEY_PREVIEW_DEST_LAT)
            )
            currentDestinationName = savedInstanceState.getString(KEY_PREVIEW_DEST_NAME)
        } else if (pendingRoutePreviewRestore) {
            pendingRoutePreviewRestore = false
        }

        navBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (isArrivalActionVisible) {
                    showArrivalDiscardSessionDialog()
                    return
                }
                if (navSessionActive) {
                    showExitNormalSessionDialog()
                    return
                }
                if (isNavigationActive) {
                    showExitNavigationDialog()
                    return
                }
                if (isPoiCategoryModeActive || isCategorySearchOverlayActive || poiBottomSheetContainer?.visibility == View.VISIBLE) {
                    exitPOICategoryModeToSearch()
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

        view.findViewById<AverageSpeedProgressView>(R.id.averageSpeedProgressView)?.let { progressView ->
            averageSpeedController = AverageSpeedController(
                requireContext(),
                progressView,
                view.findViewById(R.id.averageSpeedSignView),
                view.findViewById(R.id.idleAverageSpeedSignView)
            )
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
        quickPanelContainer = view.findViewById(R.id.quickPanelContainer)
        quickCategoriesContainer = view.findViewById(R.id.quickCategoriesContainer)
        quickDividerTop = view.findViewById(R.id.quickDividerTop)
        quickDividerBottom = view.findViewById(R.id.quickDividerBottom)
        etSearch = view.findViewById(R.id.etSearch)
        quickHomeButton = view.findViewById(R.id.btnQuickHome)
        quickWorkButton = view.findViewById(R.id.btnQuickWork)
        btnCategoryFavorites = view.findViewById(R.id.btnCategoryFavorites)
        btnCategoryGas = view.findViewById(R.id.btnCategoryGas)
        btnCategoryParking = view.findViewById(R.id.btnCategoryParking)
        btnCategoryFood = view.findViewById(R.id.btnCategoryFood)
        btnCategoryCoffee = view.findViewById(R.id.btnCategoryCoffee)
        tvQuickHomeSubtitle = view.findViewById(R.id.tvQuickHomeSubtitle)
        tvQuickWorkSubtitle = view.findViewById(R.id.tvQuickWorkSubtitle)
        tvQuickListHeader = view.findViewById(R.id.tvQuickListHeader)
        rvSearchResults = view.findViewById(R.id.rvSearchResults)
        poiBottomSheetContainer = view.findViewById(R.id.poiBottomSheetContainer)
        tvPoiBottomSheetTitle = view.findViewById(R.id.tvPoiBottomSheetTitle)
        rvPoiBottomSheetResults = view.findViewById(R.id.rvPoiBottomSheetResults)
        view.findViewById<View?>(R.id.btnPoiBottomSheetClose)?.setOnClickListener {
            exitPOICategoryModeToSearch()
        }
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
        
        // Reports System - NEW
        fabReport = view.findViewById(R.id.fabReport)
        fabFuel = view.findViewById(R.id.fabFuel)
        fabFuel.setOnClickListener { openSessionFuelEntry() }
         
        // Initialize map control buttons
        btnOverview = view.findViewById(R.id.btnOverview)
        btnRecenter = view.findViewById(R.id.btnRecenter)
        mapControlsContainer = view.findViewById(R.id.mapControlsContainer)
        btnOrientationToggle = view.findViewById(R.id.btnOrientationToggle)
        btnCameraNorthMode = view.findViewById(R.id.btnCameraNorthMode)
        btnVoiceGuidance = view.findViewById(R.id.btnVoiceGuidance)

        // Navigation UI (maneuvers + trip progress)
        maneuverContainer = view.findViewById(R.id.maneuverContainer)
        maneuverView = view.findViewById(R.id.maneuverView)
        btnCloseManeuver = view.findViewById(R.id.btnCloseManeuver)
        ManeuverViewConfigurator.setup(viewLifecycleOwner, maneuverView) {
            resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        }
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
        distanceUnitCar = view.findViewById(R.id.distanceUnitCar)
        chronometerCar = view.findViewById(R.id.chronometerCar)
        buttonContainer = view.findViewById(R.id.buttonContainer)
        buttonActionsRow = view.findViewById(R.id.buttonActionsRow)
        arrivalActionContainer = view.findViewById(R.id.arrivalActionContainer)
        btnReset = view.findViewById(R.id.btnReset)
        btnZero = view.findViewById(R.id.btnZero)
        btnStop = view.findViewById(R.id.btnStop)
        btnArrivalSaveFinish = view.findViewById(R.id.btnArrivalSaveFinish)
        btnArrivalContinue = view.findViewById(R.id.btnArrivalContinue)
        btnArrivalDelete = view.findViewById(R.id.btnArrivalDelete)
        angleContainerMoto = view.findViewById(R.id.angleContainerMoto)
        angleTextMoto = view.findViewById(R.id.angleTextMoto)
        linearGaugeView = view.findViewById(R.id.linearGaugeView)

        val distanceFormatterOptions = DistanceFormatterOptions.Builder(requireContext())
            .unitType(UnitType.METRIC)
            .build()
        maneuverApi = MapboxManeuverApi(MapboxDistanceFormatter(distanceFormatterOptions))

        btnCloseManeuver?.setOnClickListener {
            showEndNavigationSheet()
        }
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
            } else if (isFollowLineActive || (navSessionActive && !isNavigationActive)) {
                stopNormalSessionWithoutSave()
            } else {
                stopNavigationInline()
            }
        }
        btnArrivalSaveFinish?.setOnClickListener {
            hideArrivalActionPanel(animated = false)
            if (serviceBound) {
                saveAndFinishSession()
            } else {
                stopNavigationInline()
            }
        }
        btnArrivalContinue?.setOnClickListener {
            continueSessionAsNormalAfterArrival()
        }
        btnArrivalDelete?.setOnClickListener {
            showArrivalDiscardSessionDialog()
        }

        // Ensure camera buttons are wired after views are bound
        setupNavigationCameraButtons()
        setupOrientationToggle()
        setupCameraModeToggle()
        setupVoiceGuidanceToggle()

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
        idleMapSpeedContainer = view.findViewById(R.id.idleMapSpeedContainer)
        idleMapLeftChrome = view.findViewById(R.id.idleMapLeftChrome)
        idleAverageSpeedSignSlot = view.findViewById(R.id.idleAverageSpeedSignSlot)
        averageSpeedHudCluster = view.findViewById(R.id.averageSpeedHudCluster)
        tvIdleMapSpeed = view.findViewById(R.id.tvIdleMapSpeed)
        
        // Mapbox mode uses Mapbox Location Component
        
        btnStartSession.setOnClickListener { startNormalSession() }
        btnSessions.setOnClickListener { navigateToSessions() }
        fabMyLocationContainer.setOnClickListener { centerOnCurrentLocation() }
        updateIdleMapFollowUi()
        llTemperature.setOnClickListener { toggleWeatherExpansion() }
       llAltitude.setOnClickListener { toggleAltitudeExpansion() }
        
        // Reports System integration
        mapboxMapView?.let { mapView ->
            reportsIntegration = ReportsIntegration(requireActivity(), mapView)
            reportsIntegration?.initialize()
            if (ReportAlertsCoordinator.mapTabActive) {
                reportsIntegration?.setAlertsEnabled(true)
            }
            Log.d("MapFragment", "✅ Reports System initialized")
        }
        
        fabReport.setOnClickListener {
            val reportLocation = currentRawLocation ?: currentLocation ?: mapStateViewModel.lastKnownLocation
            val currentLat = reportLocation?.latitude
            val currentLon = reportLocation?.longitude
            
            if (currentLat != null && currentLon != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val mergeDistanceMeters = resolveReportMergeDistanceMeters(currentLat, currentLon)
                    reportsIntegration?.showCreateReportDialog(
                        latitude = currentLat,
                        longitude = currentLon,
                        mergeDistanceMeters = mergeDistanceMeters
                    )
                }
            } else {
                Toast.makeText(requireContext(), getString(R.string.map_no_gps_coordinates), Toast.LENGTH_SHORT).show()
            }
        }
        
        // Position FAB Report correctly on initial load
        repositionFabReport()
        lastKnownLayoutOrientation = resources.configuration.orientation
        mapRootObservedView = view
        mapRootLayoutChangeListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (!isAdded) return@OnLayoutChangeListener

            val widthChanged = (right - left) != (oldRight - oldLeft)
            val heightChanged = (bottom - top) != (oldBottom - oldTop)
            if (!widthChanged && !heightChanged) return@OnLayoutChangeListener

            val currentOrientation = resources.configuration.orientation
            val orientationChanged = currentOrientation != lastKnownLayoutOrientation
            if (orientationChanged) {
                lastKnownLayoutOrientation = currentOrientation
                snapGuidanceCameraAfterOrientationChange()
            }

            if (orientationChanged || widthChanged || heightChanged) {
                requestFabReportReposition()
            }
        }
        mapRootLayoutChangeListener?.let { view.addOnLayoutChangeListener(it) }
        requestFabReportReposition()
        
        val bottomContainer = view.findViewById<LinearLayout>(R.id.bottomContainer)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(bottomContainer) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom)
            insets
        }
        
        loadCachedWeatherData()
        updateEnvironmentDisplay()
        
        // Init route line rendering (Mapbox mode only) — orange when free, traffic colors when congested
        routeLineApi = MapboxRouteLineApi(
            MapboxRouteLineApiOptions.Builder()
                .vanishingRouteLineEnabled(true)
                .isRouteCalloutsEnabled(false)
                // Slightly more sensitive than defaults so moderate/heavy show sooner.
                .lowCongestionRange(0..29)
                .moderateCongestionRange(30..54)
                .heavyCongestionRange(55..74)
                .severeCongestionRange(75..100)
                .build()
        )

        routeLineView = MapboxRouteLineView(
            MapboxRouteLineViewOptions.Builder(requireContext())
                // Standard / Standard Satellite styles need a slot; classic below-layer is a fallback.
                .slotName("middle")
                .routeLineBelowLayerId("road-label")
                .routeLineColorResources(RouteTrafficLineColors.colorResources())
                .displaySoftGradientForTraffic(true)
                .softGradientTransition(30.0)
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
            Toast.makeText(requireContext(), getString(R.string.mapbox_token_missing), Toast.LENGTH_SHORT).show()
            mapboxAccessToken = ""
        }

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.mapbox.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        geocodingService = retrofit.create(MapboxGeocodingService::class.java)

        searchResultsAdapter = SearchResultsAdapter(
            onItemClick = { feature ->
                selectDestinationInline(feature)
            },
            onQuickDestinationClick = { quickDestination ->
                handleQuickDestinationSelection(quickDestination)
            },
            onQuickDestinationRemove = { quickDestination ->
                removeQuickDestinationItem(quickDestination)
            },
            onSearchResultLongClick = { feature ->
                showSearchResultSaveDialog(feature)
            },
            isSearchResultFavorite = { feature ->
                isFeatureInFavorites(feature)
            },
            onSearchResultFavoriteToggle = { feature, shouldBeFavorite ->
                toggleFavoriteForFeature(feature, shouldBeFavorite)
            }
        )
        rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        rvSearchResults.adapter = searchResultsAdapter

        poiResultsAdapter = SearchResultsAdapter(
            onItemClick = { feature ->
                selectDestinationInline(feature)
            },
            onQuickDestinationClick = null,
            onSearchResultLongClick = null,
            distanceTextProvider = { feature ->
                formatPoiDistanceFromCurrentLocation(feature)
            }
        )
        rvPoiBottomSheetResults?.layoutManager = LinearLayoutManager(requireContext())
        rvPoiBottomSheetResults?.adapter = poiResultsAdapter

        quickHomeButton?.setOnClickListener { onHomeShortcutClicked() }
        quickHomeButton?.setOnLongClickListener {
            showHomeWorkManageDialog(
                key = PREF_HOME_DESTINATION,
                category = QuickDestinationCategory.HOME,
                titleRes = R.string.search_manage_home_title
            )
            true
        }
        quickWorkButton?.setOnClickListener { onWorkShortcutClicked() }
        quickWorkButton?.setOnLongClickListener {
            showHomeWorkManageDialog(
                key = PREF_WORK_DESTINATION,
                category = QuickDestinationCategory.WORK,
                titleRes = R.string.search_manage_work_title
            )
            true
        }
        btnCategoryFavorites?.setOnClickListener { onFavoritesShortcutClicked() }
        btnCategoryFavorites?.setOnLongClickListener {
            showFavoriteSourceDialog()
            true
        }
        
        // POI Category Search Handlers
        btnCategoryGas?.setOnClickListener {
            searchPOICategory("gas_station", getString(R.string.search_category_gas_stations_plain))
        }
        btnCategoryParking?.setOnClickListener {
            searchPOICategory("parking", getString(R.string.search_category_parking))
        }
        btnCategoryFood?.setOnClickListener {
            searchPOICategory("restaurant", getString(R.string.search_category_food))
        }
        btnCategoryCoffee?.setOnClickListener {
            searchPOICategory("coffee", getString(R.string.search_category_coffee))
        }

        // Tap on collapsed pill -> expand inline search
        destinationSearchContainer.setOnClickListener { showInlineSearch() }
        searchInputContainer.setOnClickListener { showInlineSearch() }

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s?.toString()?.trim().orEmpty()
                if (q.length >= 2) {
                    if (isCategorySearchOverlayActive) {
                        clearPOISearchMarkers()
                        setPOIVisibility(true)
                    }
                    showSearchResultsMode()
                    scheduleInlineSearch(q)
                } else {
                    cancelInlineSearchDebounce()
                    inlineSearchRequestId++
                    if (isCategorySearchOverlayActive) {
                        clearPOISearchMarkers()
                        setPOIVisibility(true)
                    }
                    showQuickDestinationSuggestions()
                }
            }
        })

        etSearch.setOnEditorActionListener { _, actionId, event ->
            val isSearchAction =
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                    actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                    (event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)

            if (!isSearchAction) {
                return@setOnEditorActionListener false
            }

            submitInlineSearchFromIme()
            true
        }

        etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val q = etSearch.text?.toString()?.trim().orEmpty()
                if (q.length < 2) {
                    showQuickDestinationSuggestions()
                }
            } else if (currentDestination == null) {
                if (suppressInlineSearchFocusRestore || pendingMapPickCategory != null) {
                    suppressInlineSearchFocusRestore = false
                    return@setOnFocusChangeListener
                }
                restoreInitialMapUi()
            }
        }

        etSearch.setOnClickListener {
            val q = etSearch.text?.toString()?.trim().orEmpty()
            if (q.length < 2) {
                showQuickDestinationSuggestions()
            }
        }
    }

    private fun showInlineSearch(keepQuickListMode: Boolean = false) {
        hidePOIBottomSheet()
        if (isCategorySearchOverlayActive) {
            clearPOISearchMarkers()
            setPOIVisibility(true)
        }
        pendingMapPickCategory = null
        destinationSearchContainer.visibility = View.GONE
        searchContainer.visibility = View.VISIBLE
        etSearch.requestFocus()
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
        updateBottomNavigationVisibilityForInlineSearch()

        if (!keepQuickListMode && pendingSearchAssignmentCategory == null) {
            quickListMode = QuickListMode.RECENT
        }

        val query = etSearch.text?.toString()?.trim().orEmpty()
        if (query.length < 2) {
            showQuickDestinationSuggestions()
        } else {
            showSearchResultsMode()
            scheduleInlineSearch(query)
        }
        updateIdleMapFollowUi()
    }

    private fun hideInlineSearch() {
        cancelInlineSearchDebounce()
        inlineSearchRequestId++
        isPoiCategoryModeActive = false
        hidePOIBottomSheet()
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
        rvSearchResults.visibility = View.GONE
        searchContainer.visibility = View.GONE
        destinationSearchContainer.visibility =
            if (isNavigationActive || navSessionActive) View.GONE else View.VISIBLE
        suppressInlineSearchFocusRestore = true
        etSearch.clearFocus()
        clearPOISearchMarkers()
        
        // Restore default POI icons when closing search
        setPOIVisibility(true)
        updateBottomNavigationVisibilityForInlineSearch()
        updateIdleMapFollowUi()
    }

    private fun submitInlineSearchFromIme() {
        val q = etSearch.text?.toString()?.trim().orEmpty()
        if (q.length >= 2) {
            if (isCategorySearchOverlayActive) {
                clearPOISearchMarkers()
                setPOIVisibility(true)
            }
            cancelInlineSearchDebounce()
            showSearchResultsMode()
            scheduleInlineSearch(q)
        } else {
            showQuickDestinationSuggestions()
        }

        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)

        // Do not collapse inline search when IME removes focus after Search action.
        suppressInlineSearchFocusRestore = true
        etSearch.clearFocus()
    }

    private fun updateBottomNavigationVisibilityForInlineSearch() {
        if (resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE) return
        val bottomNav = activity?.findViewById<View>(R.id.bottomNavigationContainer) ?: return

        if (isNavigationActive || navSessionActive) {
            bottomNav.visibility = View.GONE
            return
        }

        val shouldHideForSearch = searchContainer.visibility == View.VISIBLE || isPoiCategoryModeActive
        bottomNav.visibility = if (shouldHideForSearch) View.GONE else View.VISIBLE
    }

    private fun scheduleInlineSearch(query: String) {
        cancelInlineSearchDebounce()
        inlineSearchDebounceRunnable = Runnable {
            performInlineSearch(query)
        }
        inlineSearchDebounceRunnable?.let {
            inlineSearchDebounceHandler.postDelayed(it, SEARCH_DEBOUNCE_MS)
        }
    }

    private fun cancelInlineSearchDebounce() {
        inlineSearchDebounceRunnable?.let { inlineSearchDebounceHandler.removeCallbacks(it) }
        inlineSearchDebounceRunnable = null
    }

    private fun setupDismissSearchOnOutsideTap(root: View) {
        val dismissListener = View.OnTouchListener { _, _ ->
            if (
                searchContainer.visibility == View.VISIBLE &&
                currentDestination == null &&
                pendingMapPickCategory == null &&
                !isCategorySearchOverlayActive
            ) {
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
        pendingMapPickCategory = null
        pendingSearchAssignmentCategory = null
        isShortcutMapPickInFlight = false
        isPoiCategoryModeActive = false
        hidePOIBottomSheet()
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
        attachIdleAverageSpeedSignToPreview(false)
        updateIdleMapFollowUi()
        fabReport?.visibility = View.VISIBLE
        repositionFabReport()
        view?.findViewById<LinearLayout>(R.id.bottomContainer)?.visibility = View.VISIBLE
        requireActivity().findViewById<View>(R.id.bottomNavigationContainer)?.visibility = View.VISIBLE
        destinationSearchContainer.visibility = View.VISIBLE
    }

    private fun performInlineSearch(query: String) {
        if (mapboxAccessToken.isBlank()) return
        if (query.length < 2) {
            showQuickDestinationSuggestions()
            return
        }
        val compactQuery = query.trim().replace(Regex("\\s+"), " ")
        val countryFilter = determineInlineSearchCountryFilter(compactQuery)
        val languagePreference = determineInlineSearchLanguages(compactQuery)
        val requestId = ++inlineSearchRequestId
        lifecycleScope.launch {
            try {
                val proximity = currentLocation?.let { "${it.longitude},${it.latitude}" }
                val features = withContext(Dispatchers.IO) {
                    val merged = LinkedHashMap<String, GeocodingFeature>()
                    val searchQueries = buildSearchQueries(query)

                    searchQueries.forEachIndexed { index, variant ->
                        val response = geocodingService.searchPlaces(
                            query = variant,
                            accessToken = mapboxAccessToken,
                            proximity = proximity,
                            limit = if (index == 0) 10 else 6,
                            language = languagePreference,
                            country = countryFilter,
                            autocomplete = true,
                            fuzzyMatch = true,
                            types = "address,poi,place,locality,neighborhood"
                        )
                        if (response.isSuccessful) {
                            response.body()?.features.orEmpty().forEach { feature ->
                                merged.putIfAbsent(feature.id, feature)
                            }
                        }
                    }

                    sortInlineFeatures(merged.values.toList(), compactQuery).take(10)
                }

                if (requestId != inlineSearchRequestId) return@launch

                searchResultsAdapter.updateResults(features)
                rvSearchResults.visibility = if (features.isNotEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                if (requestId != inlineSearchRequestId) return@launch
                searchResultsAdapter.updateResults(emptyList())
                rvSearchResults.visibility = View.GONE
            }
        }
    }

    private fun buildSearchQueries(rawQuery: String): List<String> {
        val compactQuery = rawQuery.trim().replace(Regex("\\s+"), " ")
        if (compactQuery.isBlank()) return emptyList()

        // Single-query strategy: users type ul./bul. explicitly when needed.
        return listOf(compactQuery)
    }

    private fun determineInlineSearchCountryFilter(compactQuery: String): String? {
        return if (shouldHardFilterToBulgaria(compactQuery)) "bg" else null
    }

    private fun determineInlineSearchLanguages(compactQuery: String): String {
        val hasCyrillic = compactQuery.any { it.code in 0x0400..0x04FF }
        return if (hasCyrillic) "bg,en,el" else "en,bg,el"
    }

    private fun shouldHardFilterToBulgaria(compactQuery: String): Boolean {
        if (compactQuery.isBlank()) return false

        val hasCyrillic = compactQuery.any { it.code in 0x0400..0x04FF }
        val streetPrefixRegex = Regex("^(ул\\.?|улица|бул\\.?|булевард)\\s+", RegexOption.IGNORE_CASE)
        val hasBulgarianStreetPrefix = streetPrefixRegex.containsMatchIn(compactQuery)
        val startsWithDigit = compactQuery.firstOrNull()?.isDigit() == true

        return hasBulgarianStreetPrefix || (hasCyrillic && startsWithDigit)
    }

    private fun sortInlineFeatures(features: List<GeocodingFeature>, compactQuery: String): List<GeocodingFeature> {
        if (features.isEmpty()) return features

        val isLikelyStreetOrAddress = shouldHardFilterToBulgaria(compactQuery) || compactQuery.any { it.isDigit() }
        if (!isLikelyStreetOrAddress) {
            // Keep Mapbox relevance order for city/place style queries (e.g. Thessaloniki/Солун).
            return features
        }

        return sortFeaturesByDistance(features)
    }

    private fun sortFeaturesByDistance(features: List<GeocodingFeature>): List<GeocodingFeature> {
        val anchor = currentLocation ?: mapStateViewModel.lastKnownLocation ?: return features
        return features.sortedBy { feature ->
            distanceToFeatureMeters(anchor, feature) ?: Double.MAX_VALUE
        }
    }

    private fun distanceToFeatureMeters(origin: Location, feature: GeocodingFeature): Double? {
        if (feature.center == null || feature.center.size < 2) return null
        val result = FloatArray(1)
        Location.distanceBetween(
            origin.latitude,
            origin.longitude,
            feature.center[1],
            feature.center[0],
            result
        )
        return result.firstOrNull()?.toDouble()
    }

    private fun formatPoiDistanceFromCurrentLocation(feature: GeocodingFeature): String? {
        val origin = currentLocation ?: mapStateViewModel.lastKnownLocation ?: return null
        val distanceMeters = distanceToFeatureMeters(origin, feature) ?: return null
        return if (distanceMeters < 1000.0) {
            getString(R.string.distance_meters_value, distanceMeters.roundToInt())
        } else {
            String.format(java.util.Locale.getDefault(), getString(R.string.distance_km_one_decimal_value), distanceMeters / 1000.0)
        }
    }

    private fun selectDestinationInline(feature: GeocodingFeature) {
        val assignmentCategory = pendingSearchAssignmentCategory
        if (assignmentCategory != null) {
            val destination = feature.toSavedDestination() ?: return
            saveShortcutDestination(assignmentCategory, destination)
            pendingSearchAssignmentCategory = null
            etSearch.text?.clear()
            if (assignmentCategory == QuickDestinationCategory.FAVORITE) {
                quickListMode = QuickListMode.FAVORITES
            }
            showQuickDestinationSuggestions()
            return
        }

        // Update the collapsed pill text to destination name
        val tv = destinationSearchContainer.findViewById<TextView>(R.id.tvDestinationPlaceholder)
        currentDestinationName = feature.placeName
        tv.text = currentDestinationName ?: getString(R.string.destination_placeholder)
        hideInlineSearch()

        val center = feature.center
        if (center != null && center.size >= 2) {
            val destination = Point.fromLngLat(center[0], center[1])
            setDestinationAndFindRoute(destination, currentDestinationName)
        }
    }

    private fun showQuickDestinationSuggestions() {
        hidePOIBottomSheet()
        showQuickPanelMode()
        updateQuickActionSubtitles()
        renderQuickList()
    }

    private fun showSearchResultsMode() {
        hidePOIBottomSheet()
        quickPanelContainer?.visibility = View.VISIBLE
        setQuickShortcutSectionsVisible(false)
        tvQuickListHeader?.visibility = View.GONE
    }

    private fun showQuickPanelMode() {
        hidePOIBottomSheet()
        quickPanelContainer?.visibility = View.VISIBLE
        setQuickShortcutSectionsVisible(true)
        tvQuickListHeader?.visibility = View.VISIBLE
    }

    private fun setQuickShortcutSectionsVisible(visible: Boolean) {
        val sectionVisibility = if (visible) View.VISIBLE else View.GONE
        quickCategoriesContainer?.visibility = sectionVisibility
        quickDividerTop?.visibility = sectionVisibility
        quickHomeButton?.visibility = sectionVisibility
        quickWorkButton?.visibility = sectionVisibility
        quickDividerBottom?.visibility = sectionVisibility
    }

    private fun showPOIBottomSheet(title: String, features: List<GeocodingFeature>) {
        tvPoiBottomSheetTitle?.text = title
        poiResultsAdapter?.updateResults(features)
        rvPoiBottomSheetResults?.visibility = if (features.isNotEmpty()) View.VISIBLE else View.GONE
        poiBottomSheetContainer?.visibility = if (features.isNotEmpty()) View.VISIBLE else View.GONE
        if (features.isNotEmpty()) {
            poiBottomSheetContainer?.post { refreshPoiBottomSheetCameraAnchor(features) }
        }
    }

    private fun hidePOIBottomSheet() {
        poiResultsAdapter?.updateResults(emptyList())
        rvPoiBottomSheetResults?.visibility = View.GONE
        poiBottomSheetContainer?.visibility = View.GONE
        clearPoiBottomSheetCameraPadding()
    }

    private fun buildFavoriteQuickItems(): List<QuickDestinationItem> {
        return readSavedDestinationList(PREF_FAVORITE_DESTINATIONS).mapIndexed { index, saved ->
            QuickDestinationItem(
                id = "favorite_$index",
                title = saved.name,
                subtitle = getString(R.string.quick_suggestion_favorites),
                category = QuickDestinationCategory.FAVORITE,
                latitude = saved.latitude,
                longitude = saved.longitude,
                destinationName = saved.name
            )
        }
    }

    private fun buildRecentQuickItems(): List<QuickDestinationItem> {
        return readSavedDestinationList(PREF_RECENT_DESTINATIONS)
            .take(MAX_RECENT_DESTINATIONS)
            .mapIndexed { index, saved ->
            QuickDestinationItem(
                id = "recent_$index",
                title = saved.name,
                subtitle = getString(R.string.search_recent_destination_subtitle),
                category = QuickDestinationCategory.RECENT,
                latitude = saved.latitude,
                longitude = saved.longitude,
                destinationName = saved.name
            )
        }
    }

    private fun renderQuickList() {
        val items = when (quickListMode) {
            QuickListMode.RECENT -> {
                tvQuickListHeader?.text = getString(R.string.search_quick_header_recent)
                buildRecentQuickItems()
            }
            QuickListMode.FAVORITES -> {
                tvQuickListHeader?.text = getString(R.string.search_quick_header_favorites)
                buildFavoriteQuickItems()
            }
        }

        searchResultsAdapter.showQuickItems(items)
        rvSearchResults.visibility = if (items.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateQuickActionSubtitles() {
        val home = readSavedDestination(PREF_HOME_DESTINATION)
        val work = readSavedDestination(PREF_WORK_DESTINATION)

        tvQuickHomeSubtitle?.text = home?.name ?: getString(R.string.search_set_location)
        tvQuickWorkSubtitle?.text = work?.name ?: getString(R.string.search_set_location)
    }

    private fun onHomeShortcutClicked() {
        handleHomeWorkShortcutClick(
            key = PREF_HOME_DESTINATION,
            category = QuickDestinationCategory.HOME,
            titleRes = R.string.search_select_home_source_title
        )
    }

    private suspend fun resolveReportMergeDistanceMeters(
        reportLatitude: Double,
        reportLongitude: Double
    ): Double {
        if (mapboxAccessToken.isBlank() || !::geocodingService.isInitialized) {
            Log.d(
                "MapFragment",
                "Report merge radius fallback=${reportMergeDistanceFallbackMeters.toInt()}m (geocoding unavailable) at $reportLatitude,$reportLongitude"
            )
            return reportMergeDistanceFallbackMeters
        }

        return runCatching {
            val response = geocodingService.reverseGeocode(
                longitude = reportLongitude,
                latitude = reportLatitude,
                accessToken = mapboxAccessToken,
                limit = 1,
                types = "place,locality,neighborhood"
            )
            if (!response.isSuccessful) {
                Log.d(
                    "MapFragment",
                    "Report merge radius fallback=${reportMergeDistanceFallbackMeters.toInt()}m (reverse geocode failed: ${response.code()}) at $reportLatitude,$reportLongitude"
                )
                return@runCatching reportMergeDistanceFallbackMeters
            }

            val feature = response.body()?.features?.firstOrNull()
            val center = feature?.center
            if (center == null || center.size < 2) {
                Log.d(
                    "MapFragment",
                    "Report merge radius rural=${reportMergeDistanceRuralMeters.toInt()}m (no settlement center) at $reportLatitude,$reportLongitude"
                )
                reportMergeDistanceRuralMeters
            } else {
                val settlementLocation = Location("report-settlement").apply {
                    latitude = center[1]
                    longitude = center[0]
                }
                val reportLocation = Location("report-source").apply {
                    latitude = reportLatitude
                    longitude = reportLongitude
                }
                val settlementDistanceMeters = reportLocation.distanceTo(settlementLocation)
                if (settlementDistanceMeters <= reportSettlementRadiusMeters) {
                    Log.d(
                        "MapFragment",
                        "Report merge radius urban=${reportMergeDistanceUrbanMeters.toInt()}m (nearest settlement='${feature.text}', distance=${settlementDistanceMeters.toInt()}m) at $reportLatitude,$reportLongitude"
                    )
                    reportMergeDistanceUrbanMeters
                } else {
                    Log.d(
                        "MapFragment",
                        "Report merge radius rural=${reportMergeDistanceRuralMeters.toInt()}m (nearest settlement='${feature.text}', distance=${settlementDistanceMeters.toInt()}m) at $reportLatitude,$reportLongitude"
                    )
                    reportMergeDistanceRuralMeters
                }
            }
        }.getOrElse { error ->
            Log.d(
                "MapFragment",
                "Report merge radius fallback=${reportMergeDistanceFallbackMeters.toInt()}m (exception=${error.javaClass.simpleName}) at $reportLatitude,$reportLongitude"
            )
            reportMergeDistanceFallbackMeters
        }
    }

    private fun resolveAlertMovementBearing(location: Location): Float {
        val previous = lastAlertLocation
        lastAlertLocation = Location(location)
        if (previous != null) {
            val movedMeters = previous.distanceTo(location)
            if (movedMeters >= 4f) {
                val results = FloatArray(2)
                Location.distanceBetween(
                    previous.latitude,
                    previous.longitude,
                    location.latitude,
                    location.longitude,
                    results
                )
                return results[1]
            }
        }
        return when {
            lastCalculatedBearing != 0f -> lastCalculatedBearing
            location.hasBearing() && location.speed >= 1f -> location.bearing
            else -> location.bearing
        }
    }

    private fun onWorkShortcutClicked() {
        handleHomeWorkShortcutClick(
            key = PREF_WORK_DESTINATION,
            category = QuickDestinationCategory.WORK,
            titleRes = R.string.search_select_work_source_title
        )
    }

    private fun handleHomeWorkShortcutClick(key: String, category: QuickDestinationCategory, titleRes: Int) {
        val saved = readSavedDestination(key)
        if (saved != null) {
            handleQuickDestinationSelection(
                QuickDestinationItem(
                    id = "${category.name.lowercase()}_saved",
                    title = saved.name,
                    subtitle = "",
                    category = category,
                    latitude = saved.latitude,
                    longitude = saved.longitude,
                    destinationName = saved.name
                )
            )
            return
        }

        showShortcutSourceDialog(category, titleRes)
    }

    private fun showShortcutSourceDialog(
        category: QuickDestinationCategory,
        titleRes: Int,
        includeClear: Boolean = false,
        clearKey: String? = null
    ) {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions += getString(R.string.search_pick_by_address) to {
            startAddressSearchForShortcut(category)
        }
        actions += getString(R.string.search_pick_current_location) to {
            saveShortcutFromCurrentLocation(category)
        }
        actions += getString(R.string.search_pick_on_map) to {
            startMapPickForShortcut(category)
        }

        if (includeClear && clearKey != null && readSavedDestination(clearKey) != null) {
            actions += getString(R.string.search_clear_shortcut) to {
                writeSavedDestination(clearKey, null)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.search_shortcut_cleared, shortcutCategoryLabel(category)),
                    Toast.LENGTH_SHORT
                ).show()
                refreshQuickSuggestionsIfVisible()
            }
        }

        val labels = actions.map { it.first }.toTypedArray()
        DialogHelper.show(
            DialogHelper.builder(requireContext())
                .setTitle(titleRes)
                .setItems(labels) { _, which ->
                    actions.getOrNull(which)?.second?.invoke()
                }
                .setNegativeButton(R.string.dialog_cancel_button, null)
        )
    }

    private fun showHomeWorkManageDialog(key: String, category: QuickDestinationCategory, titleRes: Int) {
        showShortcutSourceDialog(
            category = category,
            titleRes = titleRes,
            includeClear = true,
            clearKey = key
        )
    }

    private fun onFavoritesShortcutClicked() {
        val favorites = buildFavoriteQuickItems()
        if (favorites.isEmpty()) {
            showFavoriteSourceDialog()
            return
        }

        quickListMode = if (quickListMode == QuickListMode.FAVORITES) {
            QuickListMode.RECENT
        } else {
            QuickListMode.FAVORITES
        }
        showQuickDestinationSuggestions()
    }
    
    private fun searchPOICategory(categoryId: String, displayName: String) {
        val location = currentLocation ?: run {
            Toast.makeText(requireContext(), getString(R.string.search_wait_for_location_loading), Toast.LENGTH_SHORT).show()
            return
        }

        if (mapboxAccessToken.isBlank()) {
            Toast.makeText(requireContext(), getString(R.string.mapbox_token_missing), Toast.LENGTH_SHORT).show()
            return
        }

        collapseInlineSearchForCategoryMode()
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val proximity = "${location.longitude},${location.latitude}"

                // Use Category Search API with canonical category ID
                val response = withContext(Dispatchers.IO) {
                    geocodingService.searchCategory(
                        category = categoryId,
                        proximity = proximity,
                        accessToken = mapboxAccessToken,
                        limit = 25,
                        language = "en"
                    )
                }
                
                if (response.isSuccessful && response.body() != null) {
                    val categoryFeatures = response.body()!!.features
                    Log.d("MapFragment", "Category search response: ${categoryFeatures.size} results")
                    
                    if (categoryFeatures.isNotEmpty()) {
                        // Convert CategoryFeature to GeocodingFeature for adapter
                        val geocodingFeatures = categoryFeatures.map { categoryFeature ->
                            GeocodingFeature(
                                id = categoryFeature.properties.mapboxId,
                                placeName = categoryFeature.properties.fullAddress 
                                    ?: categoryFeature.properties.name,
                                center = categoryFeature.geometry.coordinates,  // [longitude, latitude]
                                text = categoryFeature.properties.name,
                                properties = com.revix.app.navigation.GeocodingProperties(
                                    address = categoryFeature.properties.fullAddress,
                                    category = categoryId
                                )
                            )
                        }
                        
                        // Sort by distance - closest first (Waze style)
                        val sortedFeatures = geocodingFeatures.sortedBy { feature ->
                            distanceToFeatureMeters(location, feature) ?: Double.MAX_VALUE
                        }
                        
                        showPOIBottomSheet(displayName, sortedFeatures)
                        
                        // Hide default POI icons and draw only category results
                        setPOIVisibility(false)
                        showPOISearchMarkers(sortedFeatures, categoryId)

                    } else {
                        exitPOICategoryModeToSearch()
                        Toast.makeText(requireContext(), getString(R.string.search_no_results_nearby, displayName), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("MapFragment", "Category search failed: ${response.code()} ${response.message()}")
                    exitPOICategoryModeToSearch()
                    Toast.makeText(requireContext(), getString(R.string.search_error_with_code, response.code()), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MapFragment", "Category search error", e)
                exitPOICategoryModeToSearch()
                Toast.makeText(requireContext(), getString(R.string.error_with_message, e.message ?: getString(R.string.error_unknown)), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun collapseInlineSearchForCategoryMode() {
        cancelInlineSearchDebounce()
        inlineSearchRequestId++
        isPoiCategoryModeActive = true
        hidePOIBottomSheet()

        suppressInlineSearchFocusRestore = true
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
        etSearch.clearFocus()

        searchContainer.visibility = View.GONE
        destinationSearchContainer.visibility = View.GONE
        updateBottomNavigationVisibilityForInlineSearch()
    }

    private fun exitPOICategoryModeToSearch() {
        if (!isPoiCategoryModeActive && !isCategorySearchOverlayActive && poiBottomSheetContainer?.visibility != View.VISIBLE) {
            return
        }

        isPoiCategoryModeActive = false
        clearPOISearchMarkers()
        setPOIVisibility(true)
        hidePOIBottomSheet()

        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
        etSearch.clearFocus()

        destinationSearchContainer.visibility = View.GONE
        searchContainer.visibility = View.VISIBLE
        updateBottomNavigationVisibilityForInlineSearch()

        val query = etSearch.text?.toString()?.trim().orEmpty()
        if (query.length < 2) {
            showQuickDestinationSuggestions()
        } else {
            showSearchResultsMode()
            scheduleInlineSearch(query)
        }
        updateIdleMapFollowUi()
    }
    
    private fun fitCameraToPOIResults(features: List<GeocodingFeature>) {
        refreshPoiBottomSheetCameraAnchor(features)
    }

    private fun refreshPoiBottomSheetCameraAnchor(features: List<GeocodingFeature>? = null) {
        val sheet = poiBottomSheetContainer ?: return
        if (sheet.visibility != View.VISIBLE) {
            poiMapCameraPadding = null
            return
        }

        val bottomContainer = view?.findViewById<View>(R.id.bottomContainer)
        val bottomInset = sheet.height + (bottomContainer?.height ?: 0)
        if (bottomInset <= 0) {
            sheet.post { refreshPoiBottomSheetCameraAnchor(features) }
            return
        }

        val location = currentLocation ?: return
        poiMapCameraPadding = EdgeInsets(0.0, 0.0, bottomInset.toDouble(), 0.0)
        val targetZoom = features?.let { resolvePoiCategoryZoom(it) }
            ?: (mapboxMapView?.mapboxMap?.cameraState?.zoom ?: IDLE_MAP_FOLLOW_ZOOM)

        mapboxMapView?.mapboxMap?.setCamera(
            CameraOptions.Builder()
                .center(MapboxPoint.fromLngLat(location.longitude, location.latitude))
                .zoom(targetZoom)
                .bearing(0.0)
                .pitch(0.0)
                .padding(poiMapCameraPadding!!)
                .build(),
        )
    }

    private fun resolvePoiCategoryZoom(features: List<GeocodingFeature>): Double {
        val location = currentLocation ?: return IDLE_MAP_FOLLOW_ZOOM
        var farthestMeters = 0.0
        features.take(20).forEach { feature ->
            val center = feature.center ?: return@forEach
            if (center.size < 2) return@forEach
            val result = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, center[1], center[0], result)
            farthestMeters = maxOf(farthestMeters, result.firstOrNull()?.toDouble() ?: 0.0)
        }
        return when {
            farthestMeters > 9000.0 -> 10.2
            farthestMeters > 7000.0 -> 10.8
            farthestMeters > 5000.0 -> 11.4
            farthestMeters > 3200.0 -> 12.0
            farthestMeters > 2200.0 -> 12.6
            farthestMeters > 1500.0 -> 13.1
            farthestMeters > 1000.0 -> 13.6
            farthestMeters > 600.0 -> 14.1
            farthestMeters > 300.0 -> 14.6
            else -> 15.0
        }
    }

    private fun clearPoiBottomSheetCameraPadding() {
        poiMapCameraPadding = null
        val mapView = mapboxMapView ?: return
        val cameraBuilder = CameraOptions.Builder()
            .padding(EdgeInsets(0.0, 0.0, 0.0, 0.0))
        val location = currentLocation
        if (location != null && isIdleMapFollowEnabled && canIdleMapFollow()) {
            cameraBuilder
                .center(MapboxPoint.fromLngLat(location.longitude, location.latitude))
                .zoom(IDLE_MAP_FOLLOW_ZOOM)
                .bearing(0.0)
                .pitch(0.0)
        }
        mapView.mapboxMap.setCamera(cameraBuilder.build())
    }

    private fun getPOIMarkerTapThresholdMeters(): Double {
        val zoom = mapboxMapView?.mapboxMap?.cameraState?.zoom ?: 14.0
        return when {
            zoom >= 17.0 -> 85.0
            zoom >= 16.0 -> 130.0
            zoom >= 15.0 -> 180.0
            zoom >= 14.0 -> 240.0
            else -> 320.0
        }
    }

    private fun resolvePOIIconRes(categoryId: String?): Int {
        return when (categoryId?.lowercase()) {
            "gas_station" -> R.drawable.gas_station
            "parking" -> R.drawable.parking
            "restaurant" -> R.drawable.cutlery
            "coffee" -> R.drawable.coffee_cup
            else -> R.drawable.gas_station
        }
    }

    private fun resolvePOIStyleImageId(categoryId: String?): String {
        return when (categoryId?.lowercase()) {
            "gas_station" -> POI_ICON_IMAGE_GAS
            "parking" -> POI_ICON_IMAGE_PARKING
            "restaurant" -> POI_ICON_IMAGE_FOOD
            "coffee" -> POI_ICON_IMAGE_COFFEE
            else -> POI_ICON_IMAGE_DEFAULT
        }
    }

    private fun buildPOIIconBitmap(iconRes: Int): Bitmap {
        val sizePx = (16f * resources.displayMetrics.density).toInt().coerceAtLeast(14)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val iconDrawable = ContextCompat.getDrawable(requireContext(), iconRes)
            ?: ContextCompat.getDrawable(requireContext(), R.drawable.gas_station)

        if (iconDrawable != null) {
            iconDrawable.mutate().setTint(Color.WHITE)
            iconDrawable.setBounds(0, 0, sizePx, sizePx)
            iconDrawable.draw(canvas)
        }

        return bitmap
    }

    private fun ensurePOIStyleImage(style: Style, categoryId: String?) {
        val imageId = resolvePOIStyleImageId(categoryId)
        val alreadyAdded = try {
            style.getStyleImage(imageId) != null
        } catch (_: Exception) {
            false
        }

        if (alreadyAdded) return

        try {
            style.addImage(imageId, buildPOIIconBitmap(resolvePOIIconRes(categoryId)))
        } catch (e: Exception) {
            Log.e("MapFragment", "Error adding POI style image", e)
        }
    }

    private fun handlePOISearchMarkerTap(clickPoint: Point): Boolean {
        if (!isCategorySearchOverlayActive || activePOICategoryId.isNullOrEmpty() || activePOISearchFeatures.isEmpty()) return false

        val tapLocation = Location("poi-marker-tap").apply {
            latitude = clickPoint.latitude()
            longitude = clickPoint.longitude()
        }

        val nearest = activePOISearchFeatures
            .mapNotNull { feature ->
                val distance = distanceToFeatureMeters(tapLocation, feature) ?: return@mapNotNull null
                feature to distance
            }
            .minByOrNull { it.second }
            ?: return false

        if (nearest.second > getPOIMarkerTapThresholdMeters()) {
            return false
        }

        selectDestinationInline(nearest.first)
        return true
    }
    
    private fun setPOIVisibility(show: Boolean) {
        try {
            mapboxMapView?.mapboxMap?.getStyle { style ->
                style.setStyleImportConfigProperty(
                    "basemap", // Mapbox Standard import ID
                    "showPointOfInterestLabels",
                    com.mapbox.bindgen.Value.valueOf(show)
                )
            }
        } catch (e: Exception) {
            Log.e("MapFragment", "Error setting POI visibility", e)
        }
    }

    private fun showPOISearchMarkers(features: List<GeocodingFeature>, categoryId: String?) {
        val markerFeatures = features.mapNotNull { feature ->
            val center = feature.center
            if (center != null && center.size >= 2) {
                Feature.fromGeometry(MapboxPoint.fromLngLat(center[0], center[1]))
            } else {
                null
            }
        }

        if (markerFeatures.isEmpty()) {
            clearPOISearchMarkers()
            return
        }

        val featureCollection = FeatureCollection.fromFeatures(markerFeatures)
        try {
            mapboxMapView?.mapboxMap?.getStyle { style ->
                try {
                    if (style.styleLayerExists(POI_SEARCH_ICON_LAYER_ID)) {
                        style.removeStyleLayer(POI_SEARCH_ICON_LAYER_ID)
                    }
                    if (style.styleLayerExists(POI_SEARCH_LAYER_ID)) {
                        style.removeStyleLayer(POI_SEARCH_LAYER_ID)
                    }
                    if (style.styleSourceExists(POI_SEARCH_SOURCE_ID)) {
                        style.removeStyleSource(POI_SEARCH_SOURCE_ID)
                    }

                    style.addSource(
                        geoJsonSource(POI_SEARCH_SOURCE_ID) {
                            featureCollection(featureCollection)
                        }
                    )

                    style.addLayer(
                        circleLayer(POI_SEARCH_LAYER_ID, POI_SEARCH_SOURCE_ID) {
                            circleColor("#FF7A00")
                            circleRadius(10.0)
                            circleStrokeColor("#FFFFFF")
                            circleStrokeWidth(1.8)
                            circleOpacity(0.95)
                        }
                    )

                    ensurePOIStyleImage(style, categoryId)
                    style.addLayer(
                        symbolLayer(POI_SEARCH_ICON_LAYER_ID, POI_SEARCH_SOURCE_ID) {
                            iconImage(resolvePOIStyleImageId(categoryId))
                            iconSize(0.9)
                            iconAnchor(IconAnchor.CENTER)
                            iconAllowOverlap(true)
                            iconIgnorePlacement(true)
                        }
                    )

                    isCategorySearchOverlayActive = true
                    activePOICategoryId = categoryId
                    activePOISearchFeatures = features
                } catch (e: Exception) {
                    Log.e("MapFragment", "Error rendering POI search markers", e)
                }
            }
        } catch (e: Exception) {
            Log.e("MapFragment", "Error preparing POI search markers", e)
        }
    }

    private fun clearPOISearchMarkers() {
        isCategorySearchOverlayActive = false
        activePOICategoryId = null
        activePOISearchFeatures = emptyList()

        try {
            mapboxMapView?.mapboxMap?.getStyle { style ->
                try {
                    if (style.styleLayerExists(POI_SEARCH_ICON_LAYER_ID)) {
                        style.removeStyleLayer(POI_SEARCH_ICON_LAYER_ID)
                    }
                    if (style.styleLayerExists(POI_SEARCH_LAYER_ID)) {
                        style.removeStyleLayer(POI_SEARCH_LAYER_ID)
                    }
                    if (style.styleSourceExists(POI_SEARCH_SOURCE_ID)) {
                        style.removeStyleSource(POI_SEARCH_SOURCE_ID)
                    }
                } catch (e: Exception) {
                    Log.e("MapFragment", "Error clearing POI search markers", e)
                }
            }
        } catch (e: Exception) {
            Log.e("MapFragment", "Error preparing POI marker cleanup", e)
        }
    }

    private fun showFavoriteSourceDialog() {
        showShortcutSourceDialog(
            category = QuickDestinationCategory.FAVORITE,
            titleRes = R.string.search_select_favorite_source_title
        )
    }

    private fun shortcutCategoryLabel(category: QuickDestinationCategory): String {
        return when (category) {
            QuickDestinationCategory.HOME -> getString(R.string.quick_suggestion_home)
            QuickDestinationCategory.WORK -> getString(R.string.quick_suggestion_work)
            QuickDestinationCategory.FAVORITE -> getString(R.string.quick_suggestion_favorites)
            QuickDestinationCategory.RECENT -> getString(R.string.search_quick_header_recent)
        }
    }

    private fun saveShortcutFromCurrentLocation(category: QuickDestinationCategory) {
        val location = currentLocation
        if (location == null) {
            Toast.makeText(requireContext(), getString(R.string.location_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        val point = Point.fromLngLat(location.longitude, location.latitude)
        lifecycleScope.launch {
            val fallback = getString(R.string.search_current_location_fallback)
            val name = reverseGeocodeName(point) ?: fallback
            val destination = SavedDestination(name, point.latitude(), point.longitude())
            saveShortcutDestination(category, destination)
        }
    }

    private fun startMapPickForShortcut(category: QuickDestinationCategory) {
        pendingSearchAssignmentCategory = null
        isShortcutMapPickInFlight = false
        pendingMapPickCategory = category
        hideInlineSearch()
        Toast.makeText(
            requireContext(),
            getString(R.string.search_pick_on_map_hint, shortcutCategoryLabel(category)),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun startAddressSearchForShortcut(category: QuickDestinationCategory) {
        pendingMapPickCategory = null
        pendingSearchAssignmentCategory = category
        showInlineSearch(keepQuickListMode = true)
        etSearch.text?.clear()
        Toast.makeText(
            requireContext(),
            getString(R.string.search_pick_by_address_hint, shortcutCategoryLabel(category)),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun completeShortcutMapPick(category: QuickDestinationCategory) {
        pendingMapPickCategory = null
        isShortcutMapPickInFlight = false
        if (category == QuickDestinationCategory.FAVORITE) {
            quickListMode = QuickListMode.FAVORITES
            showInlineSearch(keepQuickListMode = true)
        }
    }

    private fun saveShortcutDestination(category: QuickDestinationCategory, destination: SavedDestination) {
        when (category) {
            QuickDestinationCategory.HOME -> writeSavedDestination(PREF_HOME_DESTINATION, destination)
            QuickDestinationCategory.WORK -> writeSavedDestination(PREF_WORK_DESTINATION, destination)
            QuickDestinationCategory.FAVORITE -> addFavoriteDestination(destination)
            QuickDestinationCategory.RECENT -> addRecentDestination(destination)
        }

        Toast.makeText(
            requireContext(),
            getString(R.string.search_shortcut_saved, shortcutCategoryLabel(category)),
            Toast.LENGTH_SHORT
        ).show()

        if (searchContainer.visibility == View.VISIBLE) {
            if (category == QuickDestinationCategory.FAVORITE) {
                quickListMode = QuickListMode.FAVORITES
            }
            showQuickDestinationSuggestions()
        }
    }

    private fun handleQuickDestinationSelection(item: QuickDestinationItem) {
        val latitude = item.latitude
        val longitude = item.longitude
        if (latitude == null || longitude == null) {
            Toast.makeText(requireContext(), getString(R.string.search_quick_item_not_set), Toast.LENGTH_SHORT).show()
            return
        }

        hideInlineSearch()
        val point = Point.fromLngLat(longitude, latitude)
        val destinationName = item.destinationName ?: item.title
        setDestinationAndFindRoute(point, destinationName)
    }

    private fun showSearchResultSaveDialog(feature: GeocodingFeature) {
        val destination = feature.toSavedDestination() ?: return
        val options = arrayOf(
            getString(R.string.search_action_add_favorite),
            getString(R.string.search_action_set_home),
            getString(R.string.search_action_set_work)
        )

        DialogHelper.show(
            DialogHelper.builder(requireContext())
                .setTitle(destination.name)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            addFavoriteDestination(destination)
                            Toast.makeText(requireContext(), getString(R.string.search_saved_as_favorite), Toast.LENGTH_SHORT).show()
                        }
                        1 -> {
                            writeSavedDestination(PREF_HOME_DESTINATION, destination)
                            Toast.makeText(requireContext(), getString(R.string.search_saved_as_home), Toast.LENGTH_SHORT).show()
                        }
                        2 -> {
                            writeSavedDestination(PREF_WORK_DESTINATION, destination)
                            Toast.makeText(requireContext(), getString(R.string.search_saved_as_work), Toast.LENGTH_SHORT).show()
                        }
                    }
                    refreshQuickSuggestionsIfVisible()
                }
                .setNegativeButton(R.string.dialog_cancel_button, null)
        )
    }

    private fun isFeatureInFavorites(feature: GeocodingFeature): Boolean {
        val destination = feature.toSavedDestination() ?: return false
        return readSavedDestinationList(PREF_FAVORITE_DESTINATIONS)
            .any { it.isSameDestination(destination) }
    }

    private fun toggleFavoriteForFeature(feature: GeocodingFeature, shouldBeFavorite: Boolean) {
        val destination = feature.toSavedDestination() ?: return
        if (shouldBeFavorite) {
            addFavoriteDestination(destination)
            Toast.makeText(requireContext(), getString(R.string.search_saved_as_favorite), Toast.LENGTH_SHORT).show()
        } else {
            val removed = removeFavoriteDestination(destination)
            if (removed) {
                Toast.makeText(requireContext(), getString(R.string.search_removed_from_favorites), Toast.LENGTH_SHORT).show()
            }
        }
        refreshQuickSuggestionsIfVisible()
    }

    private fun refreshQuickSuggestionsIfVisible() {
        if (searchContainer.visibility != View.VISIBLE) return
        val query = etSearch.text?.toString()?.trim().orEmpty()
        if (query.length < 2) {
            showQuickDestinationSuggestions()
        }
    }

    private suspend fun reverseGeocodeName(point: Point): String? {
        if (mapboxAccessToken.isBlank()) return null
        return try {
            val response = withContext(Dispatchers.IO) {
                geocodingService.reverseGeocode(
                    point.longitude(),
                    point.latitude(),
                    mapboxAccessToken,
                    1
                )
            }
            val feature = response.body()?.features?.firstOrNull()
            feature?.placeName ?: feature?.text
        } catch (_: Exception) {
            null
        }
    }

    private fun GeocodingFeature.toSavedDestination(): SavedDestination? {
        if (center == null || center.size < 2) return null
        val destinationName = placeName.ifBlank { text }.ifBlank { getString(R.string.destination_placeholder) }
        return SavedDestination(
            name = destinationName,
            latitude = center[1],
            longitude = center[0]
        )
    }

    private fun getSearchPrefs() = PreferenceManager.getDefaultSharedPreferences(requireContext())

    private fun readSavedDestination(key: String): SavedDestination? {
        val raw = getSearchPrefs().getString(key, null) ?: return null
        return try {
            val obj = JSONObject(raw)
            SavedDestination(
                name = obj.optString("name"),
                latitude = obj.optDouble("lat"),
                longitude = obj.optDouble("lon")
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun writeSavedDestination(key: String, destination: SavedDestination?) {
        val editor = getSearchPrefs().edit()
        if (destination == null) {
            editor.remove(key).apply()
            return
        }

        val obj = JSONObject()
            .put("name", destination.name)
            .put("lat", destination.latitude)
            .put("lon", destination.longitude)
        editor.putString(key, obj.toString()).apply()
    }

    private fun readSavedDestinationList(key: String): MutableList<SavedDestination> {
        val raw = getSearchPrefs().getString(key, null) ?: return mutableListOf()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<SavedDestination>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list += SavedDestination(
                    name = obj.optString("name"),
                    latitude = obj.optDouble("lat"),
                    longitude = obj.optDouble("lon")
                )
            }
            if (key == PREF_RECENT_DESTINATIONS && list.size > MAX_RECENT_DESTINATIONS) {
                val trimmed = list.take(MAX_RECENT_DESTINATIONS).toMutableList()
                writeSavedDestinationList(key, trimmed)
                trimmed
            } else {
                list
            }
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun writeSavedDestinationList(key: String, destinations: List<SavedDestination>) {
        val array = JSONArray()
        destinations.forEach { destination ->
            array.put(
                JSONObject()
                    .put("name", destination.name)
                    .put("lat", destination.latitude)
                    .put("lon", destination.longitude)
            )
        }
        getSearchPrefs().edit().putString(key, array.toString()).apply()
    }

    private fun addFavoriteDestination(destination: SavedDestination) {
        val favorites = readSavedDestinationList(PREF_FAVORITE_DESTINATIONS)
        favorites.removeAll { it.isSameDestination(destination) }
        favorites.add(0, destination)
        if (favorites.size > MAX_FAVORITE_DESTINATIONS) {
            favorites.subList(MAX_FAVORITE_DESTINATIONS, favorites.size).clear()
        }
        writeSavedDestinationList(PREF_FAVORITE_DESTINATIONS, favorites)
    }

    private fun removeFavoriteDestination(destination: SavedDestination): Boolean {
        val favorites = readSavedDestinationList(PREF_FAVORITE_DESTINATIONS)
        val removed = favorites.removeAll { it.isSameDestination(destination) }
        if (removed) {
            writeSavedDestinationList(PREF_FAVORITE_DESTINATIONS, favorites)
        }
        return removed
    }

    private fun addRecentDestination(destination: SavedDestination) {
        val recent = readSavedDestinationList(PREF_RECENT_DESTINATIONS)
        recent.removeAll { it.isSameDestination(destination) }
        recent.add(0, destination)
        if (recent.size > MAX_RECENT_DESTINATIONS) {
            recent.subList(MAX_RECENT_DESTINATIONS, recent.size).clear()
        }
        writeSavedDestinationList(PREF_RECENT_DESTINATIONS, recent)
    }

    private fun removeQuickDestinationItem(item: QuickDestinationItem) {
        when (item.category) {
            QuickDestinationCategory.RECENT -> removeRecentDestination(item)
            QuickDestinationCategory.FAVORITE -> removeFavoriteQuickItem(item)
            else -> Unit
        }
    }

    private fun removeFavoriteQuickItem(item: QuickDestinationItem) {
        val latitude = item.latitude ?: return
        val longitude = item.longitude ?: return
        val target = SavedDestination(
            name = item.destinationName ?: item.title,
            latitude = latitude,
            longitude = longitude
        )

        if (!removeFavoriteDestination(target)) return

        Toast.makeText(
            requireContext(),
            getString(R.string.search_removed_from_favorites),
            Toast.LENGTH_SHORT
        ).show()
        if (quickListMode == QuickListMode.FAVORITES) {
            renderQuickList()
        }
    }

    private fun removeRecentDestination(item: QuickDestinationItem) {
        if (item.category != QuickDestinationCategory.RECENT) return

        val latitude = item.latitude ?: return
        val longitude = item.longitude ?: return
        val target = SavedDestination(
            name = item.destinationName ?: item.title,
            latitude = latitude,
            longitude = longitude
        )

        val recent = readSavedDestinationList(PREF_RECENT_DESTINATIONS)
        val removed = recent.removeAll { it.isSameDestination(target) }
        if (!removed) return

        writeSavedDestinationList(PREF_RECENT_DESTINATIONS, recent)
        if (quickListMode == QuickListMode.RECENT) {
            renderQuickList()
        }
    }

    private fun SavedDestination.isSameDestination(other: SavedDestination): Boolean {
        return abs(latitude - other.latitude) < 0.00001 && abs(longitude - other.longitude) < 0.00001
    }
    
    private fun setDestinationAndFindRoute(destination: Point, destinationName: String?) {
        currentDestination = destination
        currentDestinationName = destinationName
        bindGarageReminderBanner()
        tvDestinationName.text = destinationName ?: getString(R.string.destination_label)

        val nameForHistory = destinationName?.trim().orEmpty().ifBlank {
            String.format(
                java.util.Locale.getDefault(),
                "%.5f, %.5f",
                destination.latitude(),
                destination.longitude()
            )
        }
        addRecentDestination(
            SavedDestination(
                name = nameForHistory,
                latitude = destination.latitude(),
                longitude = destination.longitude()
            )
        )
        
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
        val pendingCategory = pendingMapPickCategory
        if (pendingCategory != null) {
            pendingMapPickCategory = null
            isShortcutMapPickInFlight = true
        }

        if (mapboxAccessToken.isBlank()) {
            isShortcutMapPickInFlight = false
            Toast.makeText(requireContext(), getString(R.string.mapbox_token_missing), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show loading indicator
        val loadingToast = Toast.makeText(requireContext(), getString(R.string.map_searching_location), Toast.LENGTH_SHORT)
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
                        val destinationName = feature.placeName ?: feature.text ?: getString(R.string.map_selected_location)
                        
                        // Set destination and find route
                        withContext(Dispatchers.Main) {
                            loadingToast.cancel()
                            if (pendingCategory != null) {
                                saveShortcutDestination(
                                    pendingCategory,
                                    SavedDestination(
                                        name = destinationName,
                                        latitude = point.latitude(),
                                        longitude = point.longitude()
                                    )
                                )
                                completeShortcutMapPick(pendingCategory)
                            } else {
                                setDestinationAndFindRoute(point, destinationName)
                                Toast.makeText(requireContext(), getString(R.string.map_destination_set_with_name, destinationName), Toast.LENGTH_SHORT).show()
                            }
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
                            if (pendingCategory != null) {
                                saveShortcutDestination(
                                    pendingCategory,
                                    SavedDestination(
                                        name = destinationName,
                                        latitude = point.latitude(),
                                        longitude = point.longitude()
                                    )
                                )
                                completeShortcutMapPick(pendingCategory)
                            } else {
                                setDestinationAndFindRoute(point, destinationName)
                                Toast.makeText(requireContext(), getString(R.string.map_destination_set), Toast.LENGTH_SHORT).show()
                            }
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
                        if (pendingCategory != null) {
                            saveShortcutDestination(
                                pendingCategory,
                                SavedDestination(
                                    name = destinationName,
                                    latitude = point.latitude(),
                                    longitude = point.longitude()
                                )
                            )
                            completeShortcutMapPick(pendingCategory)
                        } else {
                            setDestinationAndFindRoute(point, destinationName)
                            Toast.makeText(requireContext(), getString(R.string.map_destination_set), Toast.LENGTH_SHORT).show()
                        }
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
                    if (pendingCategory != null) {
                        saveShortcutDestination(
                            pendingCategory,
                            SavedDestination(
                                name = destinationName,
                                latitude = point.latitude(),
                                longitude = point.longitude()
                            )
                        )
                        completeShortcutMapPick(pendingCategory)
                    } else {
                        setDestinationAndFindRoute(point, destinationName)
                        Toast.makeText(requireContext(), getString(R.string.map_destination_set), Toast.LENGTH_SHORT).show()
                    }
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
        // Navigate button: choose turn-by-turn (Navigation MAU) or follow-line (no trip session)
        btnNavigateRoute.setOnClickListener {
            showStartNavigationModeDialog()
        }
        
        // Cancel button: clear route and return to initial state (like first time entering the page)
        btnCancelRoute.setOnClickListener {
            resumeDefaultReportsAfterPreview()
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
            mapboxNavigation.setNavigationRoutes(emptyList())
            
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
                                mapboxMap.setCamera(cameraOptions)
                            }
                        }
                    }
                }
            }
            
            // Show initial state UI (like first time entering)
            destinationSearchContainer.visibility = View.VISIBLE
            bindGarageReminderBanner()
            view?.findViewById<LinearLayout>(R.id.bottomContainer)?.visibility = View.VISIBLE
            llTemperature.visibility = View.VISIBLE
            llAltitude.visibility = View.VISIBLE
            attachIdleAverageSpeedSignToPreview(false)
            updateIdleMapFollowUi()
            fabReport?.visibility = View.VISIBLE
            repositionFabReport()
            
            // Show bottom navigation
            requireActivity().findViewById<View>(R.id.bottomNavigationContainer)?.visibility = View.VISIBLE
            showCountryTrafficLayer()
        }

        // Search button: return to search to find new destination
        btnSearchRoute.setOnClickListener {
            resumeDefaultReportsAfterPreview()
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
            showCountryTrafficLayer()
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
        resumeDefaultReportsAfterPreview()
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
        // Must clear Navigation SDK routes too — otherwise sync keeps country traffic hidden.
        mapboxNavigation.setNavigationRoutes(emptyList())

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
                            mapboxMap.setCamera(cameraOptions)
                        }
                    }
                }
            }
        }

        // Show initial state UI (like first time entering)
        restoreInitialMapUi()
        showCountryTrafficLayer()
    }

    companion object {
        private const val POI_SEARCH_SOURCE_ID = "poi-search-source"
        private const val POI_SEARCH_LAYER_ID = "poi-search-layer"
        private const val POI_SEARCH_ICON_LAYER_ID = "poi-search-icon-layer"
        private const val POI_ICON_IMAGE_GAS = "poi-icon-gas"
        private const val POI_ICON_IMAGE_PARKING = "poi-icon-parking"
        private const val POI_ICON_IMAGE_FOOD = "poi-icon-food"
        private const val POI_ICON_IMAGE_COFFEE = "poi-icon-coffee"
        private const val POI_ICON_IMAGE_DEFAULT = "poi-icon-default"
        private const val KEY_NAV_ACTIVE = "nav_active"
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val KEY_FOLLOW_LINE_ACTIVE = "follow_line_active"
        private const val KEY_ACTIVE_SESSION_KEY = "active_session_key"
        private const val KEY_SESSION_DISTANCE_METERS = "session_distance_meters"
        private const val KEY_PREVIEW_ACTIVE = "preview_active"
        private const val KEY_PREVIEW_DEST_LAT = "preview_dest_lat"
        private const val KEY_PREVIEW_DEST_LON = "preview_dest_lon"
        private const val KEY_PREVIEW_DEST_NAME = "preview_dest_name"
        private const val PREF_HOME_DESTINATION = "map_home_destination"
        private const val PREF_WORK_DESTINATION = "map_work_destination"
        private const val PREF_FAVORITE_DESTINATIONS = "map_favorite_destinations"
        private const val PREF_RECENT_DESTINATIONS = "map_recent_destinations"
        private const val WEATHER_API_KEY = "547cc84c36a447ab8fe131642251808"
        private const val WEATHER_REFRESH_INTERVAL_MS = 15 * 60 * 1000L
        private const val CACHE_WEATHER_MAX_AGE_MS = 15 * 60 * 1000L
        private const val CACHE_LOCATION_THRESHOLD_KM = 5.0
        private const val PREF_WEATHER_FIRST_OPEN_DONE = "weather_first_open_done"
        private const val CACHED_RAIN_PREFIX_KIND = "cached_rain_prefix_kind"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val IDLE_MAP_FOLLOW_ZOOM = 14.5
        /** Sport follow camera: slightly snappier enter than the old 2.4s ease. */
        private const val NORMAL_STARTUP_CAMERA_ANIMATION_MS = 1700L
        private const val NORMAL_CONTINUE_HANDOFF_ANIMATION_MS = 1600L
        private const val NORMAL_STARTUP_STABILIZE_MS = 400L
        private const val NORMAL_STARTUP_CAMERA_ZOOM = 19.6
        /** Overview → follow dive for Follow line (scales with zoom gap). */
        private const val FOLLOW_LINE_DIVE_MIN_MS = 1700L
        private const val FOLLOW_LINE_DIVE_MAX_MS = 2800L
        private const val FOLLOW_LINE_DIVE_STABILIZE_MS = 350L
        private const val NORMAL_BEARING_HOLD_SPEED_KMH = 4.5f
        /** Ignore tiny heading jitter on straight roads (degrees). */
        private const val NORMAL_BEARING_DEADBAND_DEG = 1.8f
        private const val MAX_FAVORITE_DESTINATIONS = 12
        private const val MAX_RECENT_DESTINATIONS = 10
        private const val SEARCH_DEBOUNCE_MS = 800L
        /**
         * Live follow (free-ride + follow-line) keeps the puck at a fixed fraction of the
         * MapView height from the bottom. Padding is used instead of a ground-meter offset so
         * the spot stays the same on every phone size, density and pitch.
         */
        private const val LIVE_FOLLOW_PUCK_FROM_BOTTOM_PORTRAIT = 0.24
        private const val LIVE_FOLLOW_PUCK_FROM_BOTTOM_LANDSCAPE = 0.22
        private const val NORMAL_ZOOM_AT_REST = 19.6
        private const val NORMAL_ZOOM_AT_SPEED = 15.8
        private const val NORMAL_PITCH_AT_REST = 55.0
        private const val NORMAL_PITCH_AT_SPEED = 72.0
        private const val NORMAL_PITCH_AT_REST_LANDSCAPE = 42.0
        private const val NORMAL_PITCH_AT_SPEED_LANDSCAPE = 58.0
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
            if (isNavCameraFollowingPuck()) {
                requestOverview()
            } else {
                requestFollowing()
            }
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

    private fun isNavCameraFollowingPuck(): Boolean {
        if (!this::navigationCamera.isInitialized) return !isTripCameraInOverview
        return when (navigationCamera.state) {
            NavigationCameraState.FOLLOWING,
            NavigationCameraState.TRANSITION_TO_FOLLOWING -> true
            else -> false
        }
    }

    private fun updateTripCameraToggleUi() {
        val button = btnOverview ?: return
        if (isNavCameraFollowingPuck()) {
            button.setImageResource(R.drawable.ic_route)
            button.contentDescription = getString(R.string.map_overview)
        } else {
            button.setImageResource(R.drawable.ic_navigation)
            button.contentDescription = getString(R.string.map_recenter)
        }
        button.setColorFilter(Color.WHITE)
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
            enforcePitchZero = isNorthUpMode
            snapFollowCameraToPuckInstant()
            updateCameraModeUi()
        }
        updateCameraModeUi()
    }

    private fun updateCameraModeUi() {
        btnCameraNorthMode?.setImageResource(R.drawable.ic_map_heading)
        val tintColor = if (isNorthUpMode) Color.WHITE else Color.parseColor("#B3FFFFFF")
        btnCameraNorthMode?.setColorFilter(tintColor)
    }

    private fun setupVoiceGuidanceToggle() {
        btnVoiceGuidance?.setOnClickListener {
            val enabled = !VoiceAlertsSettings.isEnabled(requireContext())
            VoiceAlertsSettings.setEnabled(requireContext(), enabled)
            updateVoiceGuidanceToggleUi()
        }
        updateVoiceGuidanceToggleUi()
    }

    private fun updateVoiceGuidanceToggleUi() {
        val muted = !VoiceAlertsSettings.isEnabled(requireContext())
        btnVoiceGuidance?.visibility = if (isNavigationActive) View.VISIBLE else View.GONE
        btnVoiceGuidance?.setImageResource(
            if (muted) android.R.drawable.ic_lock_silent_mode else android.R.drawable.ic_lock_silent_mode_off
        )
        val tintColor = if (muted) Color.WHITE else Color.parseColor("#B3FFFFFF")
        btnVoiceGuidance?.setColorFilter(tintColor)
        btnVoiceGuidance?.contentDescription = if (muted) {
            getString(R.string.map_voice_guidance_enable)
        } else {
            getString(R.string.map_voice_guidance_disable)
        }
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

    private fun showStartNavigationModeDialog() {
        if (currentDestination == null) {
            Toast.makeText(requireContext(), getString(R.string.map_no_selected_destination), Toast.LENGTH_SHORT).show()
            return
        }
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_start_navigation_mode, null)
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val hasTurnByTurnAccess = com.revix.app.billing.ProAccess.hasFullAccess(requireContext())
        dialogView.findViewById<View>(R.id.tvTurnByTurnProBadge).visibility =
            if (hasTurnByTurnAccess) View.GONE else View.VISIBLE

        dialogView.findViewById<View>(R.id.btnStartFollowLine).setOnClickListener {
            dialog.dismiss()
            startFollowLineInline()
        }
        dialogView.findViewById<View>(R.id.btnStartTurnByTurn).setOnClickListener {
            dialog.dismiss()
            startNavigationInline()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun startNavigationInline() {
        val dest = currentDestination
        if (dest == null) {
            Toast.makeText(requireContext(), getString(R.string.map_no_selected_destination), Toast.LENGTH_SHORT).show()
            return
        }

        // Turn-by-turn starts a Mapbox trip session (Navigation MAU) — Pro only.
        if (!com.revix.app.billing.ProGate.ensureTurnByTurnNavigation(requireContext())) {
            return
        }

        val selectedProfileId = ProfileStorage.getSelectedProfileId(requireContext())
        val profiles = ProfileStorage.loadProfiles(requireContext())
        val profile = if (selectedProfileId != -1L) {
            profiles.find { it.id == selectedProfileId }
        } else {
            profiles.firstOrNull()
        }
        if (profile == null || !com.revix.app.billing.ProGate.ensureWritable(
                requireContext(),
                profile.id,
                com.revix.app.billing.ProAccess.Feature.GENERAL
            )
        ) {
            return
        }

        val selectedRoute = currentRoutesOriginal.getOrNull(selectedRouteIndex)
            ?: mapboxNavigation.getNavigationRoutes().firstOrNull()
        if (selectedRoute == null) {
            Toast.makeText(requireContext(), getString(R.string.map_no_route_available), Toast.LENGTH_SHORT).show()
            return
        }

        isFollowLineActive = false
        detachFollowLineTraveledRouteListener()

        enforcePitchZero = false

        // Apply selected route to SDK and switch to following camera
        mapboxNavigation.setNavigationRoutes(listOf(selectedRoute))
        setNavigationActive(true)
        speakInitialNavigationAnnouncementIfAvailable(selectedRoute)
        if (this::viewportDataSource.isInitialized && this::navigationCamera.isInitialized) {
            mapboxMapView?.post {
                navigationCamera.requestNavigationCameraToFollowing()
            }
        }

        hideRoutePreviewChrome()
        mapControlsContainer?.visibility = View.VISIBLE

        // Hide bottom container (sessions/start) while navigating
        view?.findViewById<LinearLayout>(R.id.bottomContainer)?.visibility = View.GONE
    }

    /**
     * Follow the selected Directions route visually while recording a free-ride session.
     * Does not call startTripSession — no Navigation SDK MAU / Free Drive billing.
     */
    private fun startFollowLineInline() {
        if (currentDestination == null) {
            Toast.makeText(requireContext(), getString(R.string.map_no_selected_destination), Toast.LENGTH_SHORT).show()
            return
        }
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
        if (profile == null) {
            Toast.makeText(requireContext(), getString(R.string.map_select_profile_prompt), Toast.LENGTH_SHORT).show()
            val intent = Intent(requireContext(), MainContainerActivity::class.java).apply {
                putExtra(MainContainerActivity.EXTRA_INITIAL_PAGE, MainContainerActivity.PAGE_RACES)
            }
            startActivity(intent)
            return
        }
        if (!com.revix.app.billing.ProGate.ensureWritable(
                requireContext(),
                profile.id,
                com.revix.app.billing.ProAccess.Feature.GENERAL
            )
        ) {
            return
        }

        val selectedRoute = currentRoutesOriginal.getOrNull(selectedRouteIndex)
            ?: mapboxNavigation.getNavigationRoutes().firstOrNull()
        if (selectedRoute == null) {
            Toast.makeText(requireContext(), getString(R.string.map_no_route_available), Toast.LENGTH_SHORT).show()
            return
        }

        ProfileStorage.saveSelectedProfile(requireContext(), profile.id)

        applyFollowLineRouteVisuals(selectedRoute)
        mapStateViewModel.followLineRoute = selectedRoute
        resumeDefaultReportsAfterPreview()
        routeWeatherPreviewOverlay?.clear()

        isFollowLineActive = true
        attachFollowLineTraveledRouteListener()
        syncAverageSpeedRoute()
        hideRoutePreviewChrome()

        shouldResetOnConnect = true
        if (serviceBound && foregroundService != null) {
            shouldResetOnConnect = false
            resetSessionData()
        }
        startAndBindServiceIfNeeded()
        setNormalSessionUiActive(true)
        if (isNorthUpMode) {
            isNorthUpMode = false
            updateCameraModeUi()
        }
        enforcePitchZero = false
        mapStateViewModel.hasInitializedCamera = true
        resetNormalDrivingCameraState()
        startNavSessionTracking()
        // Dive from route-overview into puck follow (not free-ride startup — that assumes near-puck idle zoom).
        smoothDiveFromRoutePreviewToFollowLine()
        btnPreviewOverview?.visibility = View.GONE
        btnPreviewRecenter?.visibility = View.GONE
    }

    /**
     * Follow line only: cinematic [flyTo] from the route-preview overview toward the puck.
     * The puck stays on screen as the flight destination. Free-ride Start keeps its live intro.
     */
    private fun smoothDiveFromRoutePreviewToFollowLine() {
        val mapView = mapboxMapView ?: return
        val location = currentRawLocation
            ?: currentLocation
            ?: mapStateViewModel.lastKnownLocation
            ?: return

        if (this::navigationCamera.isInitialized) {
            navigationCamera.requestNavigationCameraToIdle()
        }
        if (this::viewportDataSource.isInitialized) {
            viewportDataSource.clearRouteData()
            viewportDataSource.evaluate()
        }

        val speedKmh = (location.speed * 3.6f).coerceAtLeast(0f)
        lastSpeedKmh = speedKmh

        val cameraState = mapView.mapboxMap.cameraState
        val bearing = when {
            isNorthUpMode -> 0f
            location.hasBearing() -> location.bearing
            else -> cameraState.bearing.toFloat()
        }

        val targetZoomValue = getNormalDrivingZoomForSpeed(speedKmh)
        val targetPitchValue = getNormalDrivingPitchForSpeed(speedKmh)
        val centerPoint = GeoPoint(location.latitude, location.longitude)

        mapboxTargetPosition = centerPoint
        mapboxSmoothedTargetPosition = centerPoint
        mapboxCurrentPosition = GeoPoint(cameraState.center.latitude(), cameraState.center.longitude())
        mapboxCurrentCameraCenter = GeoPoint(cameraState.center.latitude(), cameraState.center.longitude())
        mapboxTargetBearing = bearing
        mapboxCurrentBearing = cameraState.bearing.toFloat()
        lastCalculatedBearing = bearing
        lastProcessedLocation = location
        targetMapOrientation = if (isNorthUpMode) 0f else -bearing
        currentMapOrientation = -cameraState.bearing.toFloat()
        currentZoom = cameraState.zoom
        targetZoom = targetZoomValue
        currentPitch = cameraState.pitch
        targetPitch = if (isNorthUpMode) 0.0 else targetPitchValue
        mapboxLastUpdateTime = SystemClock.elapsedRealtime()
        isFirstLocation = false
        startupIntroUntil = 0L

        val zoomDelta = abs(cameraState.zoom - targetZoomValue)
        val durationMs = (1400L + (zoomDelta * 300.0).toLong())
            .coerceIn(FOLLOW_LINE_DIVE_MIN_MS, FOLLOW_LINE_DIVE_MAX_MS)

        val now = SystemClock.elapsedRealtime()
        suppressMapCameraUpdatesUntil = now + durationMs + 80L
        startupCameraHandoffUntil = suppressMapCameraUpdatesUntil
        startupFollowStabilizeUntil = suppressMapCameraUpdatesUntil + FOLLOW_LINE_DIVE_STABILIZE_MS

        mapView.camera.cancelAllAnimators()
        mapView.camera.flyTo(
            CameraOptions.Builder()
                .center(MapboxPoint.fromLngLat(centerPoint.longitude, centerPoint.latitude))
                .zoom(targetZoomValue)
                .bearing(if (isNorthUpMode) 0.0 else bearing.toDouble())
                .pitch(if (isNorthUpMode) 0.0 else targetPitchValue)
                .padding(getFreeRideFollowPadding())
                .build(),
            MapAnimationOptions.Builder()
                .duration(durationMs)
                .build()
        )

        view?.postDelayed({
            if (!isAdded || !isFollowLineActive || isNavigationActive) return@postDelayed
            val live = currentRawLocation ?: currentLocation ?: location
            val liveSpeed = (live.speed * 3.6f).coerceAtLeast(0f)
            val liveBearing = when {
                isNorthUpMode -> 0f
                live.hasBearing() -> live.bearing
                else -> bearing
            }
            val liveZoom = getNormalDrivingZoomForSpeed(liveSpeed)
            val livePitch = if (isNorthUpMode) 0.0 else getNormalDrivingPitchForSpeed(liveSpeed)
            val liveAnchor = GeoPoint(live.latitude, live.longitude)
            val liveCenter = liveAnchor
            currentZoom = liveZoom
            targetZoom = liveZoom
            currentPitch = livePitch
            targetPitch = livePitch
            mapboxCurrentCameraCenter = liveCenter
            mapboxCurrentPosition = liveAnchor
            mapboxSmoothedTargetPosition = liveAnchor
            mapboxTargetPosition = liveAnchor
            lastCalculatedBearing = liveBearing
            mapboxCurrentBearing = liveBearing
            mapboxTargetBearing = liveBearing
            targetMapOrientation = if (isNorthUpMode) 0f else -liveBearing
            currentMapOrientation = targetMapOrientation
        }, durationMs + 40L)
    }

    private fun hideRoutePreviewChrome() {
        attachIdleAverageSpeedSignToPreview(false)
        if (::routeInfoContainer.isInitialized) routeInfoContainer.visibility = View.GONE
        if (::routePreviewBottomContainer.isInitialized) routePreviewBottomContainer.visibility = View.GONE
        if (::btnSearchRoute.isInitialized) btnSearchRoute.visibility = View.GONE
        if (::btnMotorwayOptions.isInitialized) btnMotorwayOptions.visibility = View.GONE
        if (::motorwayOptionsContainer.isInitialized) motorwayOptionsContainer.visibility = View.GONE
        if (::destinationSearchContainer.isInitialized) destinationSearchContainer.visibility = View.GONE
        if (::searchContainer.isInitialized) searchContainer.visibility = View.GONE
        if (::llActiveProfileHeader.isInitialized) {
            llActiveProfileHeader.visibility = View.GONE
        }
        btnPreviewOverview?.visibility = View.GONE
        btnPreviewRecenter?.visibility = View.GONE
    }

    private fun applyFollowLineRouteVisuals(selectedRoute: NavigationRoute) {
        val routes = listOf(selectedRoute)
        mapboxNavigation.setNavigationRoutes(routes)
        val style = currentMapboxStyle ?: mapboxMapView?.mapboxMap?.style
        if (style == null || !::routeLineApi.isInitialized || !::routeLineView.isInitialized) {
            mapboxMapView?.post {
                if (isAdded && isFollowLineActive) {
                    applyFollowLineRouteVisuals(selectedRoute)
                }
            }
            return
        }
        val metadata = mapboxNavigation.getAlternativeMetadataFor(routes)
        routeLineApi.setNavigationRoutes(routes, metadata) { value ->
            routeLineView.renderRouteDrawData(style, value)
        }
        try {
            val geometryString = selectedRoute.directionsRoute.geometry()
            val routeGeometry = geometryString?.let { LineString.fromPolyline(it, 6) }
            reportsIntegration?.updateRouteGeometry(routeGeometry, true)
        } catch (e: Exception) {
            Log.e("MapFragment", "Failed to parse follow-line route geometry", e)
            reportsIntegration?.updateRouteGeometry(null, true)
        }
    }

    private fun restoreFollowLineAfterRecreate() {
        val selectedRoute = mapboxNavigation.getNavigationRoutes().firstOrNull()
            ?: currentRoutesOriginal.getOrNull(selectedRouteIndex)
            ?: mapStateViewModel.followLineRoute
        if (selectedRoute == null) {
            shouldRestoreFollowLineAfterRecreate = false
            return
        }
        shouldRestoreFollowLineAfterRecreate = false
        isFollowLineActive = true
        currentRoutesOriginal = listOf(selectedRoute)
        selectedRouteIndex = 0
        mapStateViewModel.followLineRoute = selectedRoute
        applyFollowLineRouteVisuals(selectedRoute)
        hideRoutePreviewChrome()
        attachFollowLineTraveledRouteListener()
        syncAverageSpeedRoute()
        applyLandscapeBottomHudAnchor()
        if (this::navigationCamera.isInitialized) {
            navigationCamera.requestNavigationCameraToIdle()
        }
        snapFollowCameraToPuckInstant()
    }

    private fun attachFollowLineTraveledRouteListener() {
        val mapView = mapboxMapView ?: return
        val locationPlugin = mapView.getPlugin(Plugin.MAPBOX_LOCATION_COMPONENT_PLUGIN_ID) as? LocationComponentPlugin
            ?: return
        detachFollowLineTraveledRouteListener()
        onIndicatorPositionChangedListener = OnIndicatorPositionChangedListener { point ->
            if (!isFollowLineActive) return@OnIndicatorPositionChangedListener
            if (!this::routeLineApi.isInitialized || !this::routeLineView.isInitialized) return@OnIndicatorPositionChangedListener
            val style = mapboxMapView?.mapboxMap?.style ?: return@OnIndicatorPositionChangedListener
            if (mapboxNavigation.getNavigationRoutes().isEmpty()) return@OnIndicatorPositionChangedListener
            val update = routeLineApi.updateTraveledRouteLine(point)
            routeLineView.renderRouteLineUpdate(style, update)
        }
        onIndicatorPositionChangedListener?.let { locationPlugin.addOnIndicatorPositionChangedListener(it) }
    }

    private fun detachFollowLineTraveledRouteListener() {
        val mapView = mapboxMapView ?: return
        val locationPlugin = mapView.getPlugin(Plugin.MAPBOX_LOCATION_COMPONENT_PLUGIN_ID) as? LocationComponentPlugin
            ?: return
        onIndicatorPositionChangedListener?.let { locationPlugin.removeOnIndicatorPositionChangedListener(it) }
        onIndicatorPositionChangedListener = null
    }

    private fun clearFollowLineSessionExtras() {
        if (!isFollowLineActive) return
        isFollowLineActive = false
        mapStateViewModel.followLineRoute = null
        detachFollowLineTraveledRouteListener()
        reportsIntegration?.updateRouteGeometry(null, false)
        syncAverageSpeedRoute()
        resumeDefaultReportsAfterPreview()
        mapboxNavigation.setNavigationRoutes(emptyList())
        if (::routeLineApi.isInitialized && ::routeLineView.isInitialized && currentMapboxStyle != null) {
            routeLineApi.clearRouteLine { value ->
                routeLineView.renderClearRouteLineValue(currentMapboxStyle!!, value)
            }
        }
        currentDestination = null
        currentDestinationName = null
        resetRouteCacheForNewSelection()
        destinationSearchContainer.findViewById<TextView>(R.id.tvDestinationPlaceholder)?.text =
            getString(R.string.destination_placeholder)
    }

    private fun syncAverageSpeedRoute() {
        val controller = averageSpeedController ?: return
        val guided = isNavigationActive || isFollowLineActive
        if (!guided) {
            controller.setGuidedRoute(null, false, voiceEnabled = false)
            return
        }
        val geometryString = mapboxNavigation.getNavigationRoutes().firstOrNull()?.directionsRoute?.geometry()
            ?: currentRoutesOriginal.getOrNull(selectedRouteIndex)?.directionsRoute?.geometry()
        val routeGeometry = try {
            geometryString?.let { LineString.fromPolyline(it, 6) }
        } catch (e: Exception) {
            Log.e("MapFragment", "Failed to parse average-speed route geometry", e)
            null
        }
        controller.setGuidedRoute(
            routeGeometry,
            routeGeometry != null,
            voiceEnabled = isNavigationActive
        )
    }

    private fun requestFabReportReposition() {
        val rootView = view ?: return
        rootView.removeCallbacks(fabRepositionRunnable)
        rootView.post(fabRepositionRunnable)
    }

    private fun resolveMapControlsMarginEndPx(defaultDp: Int = 12): Int {
        val density = resources.displayMetrics.density
        val defaultPx = (defaultDp * density).toInt()
        val controlsParams = mapControlsContainer?.layoutParams as? android.widget.RelativeLayout.LayoutParams
        return controlsParams?.marginEnd?.takeIf { it > 0 } ?: defaultPx
    }

    /** Route preview hides report FAB so it does not cover preview recenter controls. */
    private fun isRoutePreviewUiActive(): Boolean {
        if (isNavigationActive || navSessionActive) return false
        if (currentDestination != null) return true
        return ::routePreviewBottomContainer.isInitialized &&
            routePreviewBottomContainer.visibility == View.VISIBLE
    }

    private fun repositionReportAlert() {
        val container = view?.findViewById<View>(R.id.reportAlertContainer) ?: return
        val params = container.layoutParams as? android.widget.RelativeLayout.LayoutParams ?: return
        val density = resources.displayMetrics.density
        params.removeRule(android.widget.RelativeLayout.ABOVE)
        params.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP)
        params.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
        params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
        params.marginStart = (16 * density).toInt()
        params.bottomMargin = (12 * density).toInt()
        val sessionHudVisible = bottomHudRow?.visibility == View.VISIBLE
        if (sessionHudVisible) {
            params.addRule(android.widget.RelativeLayout.ABOVE, R.id.bottomHudRow)
        } else {
            params.addRule(android.widget.RelativeLayout.ABOVE, R.id.idleMapLeftChrome)
        }
        container.layoutParams = params
    }

    private fun repositionFabReport() {
        repositionReportAlert()
        val fabReportParams = fabReport?.layoutParams as? android.widget.RelativeLayout.LayoutParams ?: return

        // Returning from paywall/onResume must not force the report FAB visible over preview controls.
        if (isRoutePreviewUiActive()) {
            fabReport.visibility = View.GONE
            if (::fabFuel.isInitialized) fabFuel.visibility = View.GONE
            return
        }

        val density = resources.displayMetrics.density
        val isBottomHudShown = bottomHudRow?.isShown == true
        val isNavSessionShown = navSessionContainer?.isShown == true
        val isTripProgressShown = tripProgressContainer?.isShown == true
        val isStopShown = btnStop?.isShown == true
        
        if (resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            // Portrait mode: keep report FAB visible and anchor it above ride HUD when navigating.
            val shouldUseRideAnchorPortrait =
                isBottomHudShown || isNavSessionShown || isTripProgressShown || isNavigationActive || navSessionActive

            fabReportParams.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
            fabReportParams.removeRule(android.widget.RelativeLayout.ABOVE)
            fabReportParams.removeRule(android.widget.RelativeLayout.ALIGN_TOP)
            fabReportParams.removeRule(android.widget.RelativeLayout.ALIGN_BOTTOM)
            fabReportParams.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
            fabReportParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)

            if (shouldUseRideAnchorPortrait) {
                when {
                    isNavigationActive && isTripProgressShown -> {
                        fabReportParams.addRule(android.widget.RelativeLayout.ABOVE, R.id.tripProgressContainer)
                        fabReportParams.bottomMargin = (8 * density).toInt()
                    }
                    isNavSessionShown -> {
                        fabReportParams.addRule(android.widget.RelativeLayout.ABOVE, R.id.navSessionContainer)
                        fabReportParams.bottomMargin = (8 * density).toInt()
                    }
                    isBottomHudShown -> {
                        fabReportParams.addRule(android.widget.RelativeLayout.ABOVE, R.id.bottomHudRow)
                        fabReportParams.bottomMargin = (8 * density).toInt()
                    }
                    else -> {
                        fabReportParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
                        fabReportParams.bottomMargin = (140 * density).toInt()
                    }
                }
                fabReportParams.marginEnd = (16 * density).toInt()
                fabReportParams.marginStart = 0
                fabReport.visibility = View.VISIBLE
            } else {
                fabReportParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
                fabReportParams.marginEnd = (16 * density).toInt()
                fabReportParams.bottomMargin = (140 * density).toInt()
                fabReportParams.marginStart = 0
                fabReport.visibility = View.VISIBLE
            }

            fabReport.layoutParams = fabReportParams
            fabReport.bringToFront()
            repositionFabFuel(fabReportParams)
            return
        }

        val shouldUseRideAnchor =
            isBottomHudShown || isNavSessionShown || isStopShown || isNavigationActive || navSessionActive
        val navBottomMargin = resources.getDimensionPixelSize(R.dimen.hud_report_nav_bottom_margin_landscape)
        val fallbackBottomMargin = resources.getDimensionPixelSize(R.dimen.hud_report_fallback_bottom_margin_landscape)
        
        when {
            shouldUseRideAnchor -> {
                // Navigation/session mode: anchor above ride HUD and center on STOP when available.
                fabReportParams.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
                fabReportParams.removeRule(android.widget.RelativeLayout.ABOVE)
                fabReportParams.removeRule(android.widget.RelativeLayout.ALIGN_BOTTOM)
                fabReportParams.removeRule(android.widget.RelativeLayout.ALIGN_TOP)
                fabReportParams.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
                fabReportParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                if (isNavigationActive && isTripProgressShown) {
                    fabReportParams.addRule(android.widget.RelativeLayout.ABOVE, R.id.tripProgressContainer)
                    fabReportParams.bottomMargin = (8 * density).toInt()
                } else if (isBottomHudShown) {
                    fabReportParams.addRule(android.widget.RelativeLayout.ABOVE, R.id.bottomHudRow)
                    fabReportParams.bottomMargin = navBottomMargin
                } else if (isNavSessionShown) {
                    fabReportParams.addRule(android.widget.RelativeLayout.ABOVE, R.id.navSessionContainer)
                    fabReportParams.bottomMargin = navBottomMargin
                } else {
                    fabReportParams.addRule(android.widget.RelativeLayout.ABOVE, R.id.llTemperature)
                    fabReportParams.bottomMargin = fallbackBottomMargin
                }
                fabReportParams.marginEnd = (10 * density).toInt()
                fabReportParams.marginStart = 0
                fabReport?.visibility = View.VISIBLE
                if (isStopShown) {
                    centerFabReportHorizontallyOnStopButton()
                }
            }
            else -> {
                // Main page / preview mode: right side, above the temperature pill
                fabReportParams.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
                fabReportParams.removeRule(android.widget.RelativeLayout.ABOVE)
                fabReportParams.removeRule(android.widget.RelativeLayout.ALIGN_BOTTOM)
                fabReportParams.removeRule(android.widget.RelativeLayout.ALIGN_TOP)
                fabReportParams.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
                fabReportParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                fabReportParams.addRule(android.widget.RelativeLayout.ABOVE, R.id.llTemperature)
                fabReportParams.marginEnd = (16 * density).toInt()
                fabReportParams.bottomMargin = (8 * density).toInt()
                fabReportParams.marginStart = 0
                fabReport?.visibility = View.VISIBLE
            }
        }
        
        fabReport?.layoutParams = fabReportParams
        fabReport?.bringToFront()
        repositionFabFuel(fabReportParams)
        repositionLandscapeAverageSpeedCluster()
    }

    private fun repositionFabFuel(reportParams: android.widget.RelativeLayout.LayoutParams) {
        if (!this::fabFuel.isInitialized) return
        val fuelParams = fabFuel.layoutParams as? android.widget.RelativeLayout.LayoutParams ?: return
        val density = resources.displayMetrics.density
        val showFuel = isNavigationActive || navSessionActive

        if (!showFuel) {
            fabFuel.visibility = View.GONE
            repositionMapControlsForActiveSession()
            return
        }

        fabFuel.visibility = View.VISIBLE
        fuelParams.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
        fuelParams.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
        fuelParams.removeRule(android.widget.RelativeLayout.ALIGN_TOP)
        fuelParams.removeRule(android.widget.RelativeLayout.ALIGN_BOTTOM)
        fuelParams.removeRule(android.widget.RelativeLayout.ABOVE)
        fuelParams.addRule(android.widget.RelativeLayout.ABOVE, R.id.fabReport)
        applyFabFuelHorizontalAlignment(fuelParams)
        fuelParams.bottomMargin = (8 * density).toInt()
        fabFuel.layoutParams = fuelParams
        fabFuel.bringToFront()
        fabReport.bringToFront()
        repositionMapControlsForActiveSession()
    }

    private fun applyFabFuelHorizontalAlignment(fuelParams: android.widget.RelativeLayout.LayoutParams) {
        fuelParams.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
        fuelParams.removeRule(android.widget.RelativeLayout.ALIGN_END)
        fuelParams.addRule(android.widget.RelativeLayout.ALIGN_END, R.id.fabReport)
        fuelParams.marginEnd = 0
        fuelParams.marginStart = 0
    }

    private fun syncFabFuelAlignmentToReport() {
        if (!this::fabFuel.isInitialized || fabFuel.visibility != View.VISIBLE) return
        val fuelParams = fabFuel.layoutParams as? android.widget.RelativeLayout.LayoutParams ?: return
        applyFabFuelHorizontalAlignment(fuelParams)
        fabFuel.layoutParams = fuelParams
        repositionMapControlsForActiveSession()
    }

    private fun repositionMapControlsForActiveSession() {
        val controls = mapControlsContainer ?: return
        if (controls.visibility != View.VISIBLE) return
        val params = controls.layoutParams as? android.widget.RelativeLayout.LayoutParams ?: return
        val isPortrait =
            resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val inActiveSession = isNavigationActive || navSessionActive

        params.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP)
        params.removeRule(android.widget.RelativeLayout.ABOVE)
        params.removeRule(android.widget.RelativeLayout.BELOW)
        params.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)

        if (isNavigationActive && isPortrait) {
            params.addRule(android.widget.RelativeLayout.BELOW, R.id.maneuverContainer)
            params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
            params.topMargin = (8 * resources.displayMetrics.density).toInt()
            params.bottomMargin = 0
            params.marginEnd = (12 * resources.displayMetrics.density).toInt()
        } else if (isNavigationActive) {
            params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP)
            params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
            params.topMargin = resources.getDimensionPixelSize(R.dimen.hud_maneuver_margin_top_landscape)
            params.bottomMargin = 0
            params.marginEnd = (12 * resources.displayMetrics.density).toInt()
        } else if (isPortrait && inActiveSession) {
            params.addRule(android.widget.RelativeLayout.ABOVE, R.id.fabFuel)
            params.topMargin = 0
            params.bottomMargin = resources.getDimensionPixelSize(
                R.dimen.map_controls_margin_above_fuel_portrait
            )
            params.marginEnd = resolveMapControlsMarginEndPx()
        } else if (!isPortrait && inActiveSession) {
            params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP)
            params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
            params.topMargin = resources.getDimensionPixelSize(R.dimen.hud_maneuver_margin_top_landscape)
            params.bottomMargin = 0
            params.marginEnd = (12 * resources.displayMetrics.density).toInt()
        } else if (isPortrait) {
            params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP)
            params.topMargin = resources.getDimensionPixelSize(R.dimen.map_controls_margin_top_portrait)
            params.bottomMargin = 0
            params.marginEnd = (12 * resources.displayMetrics.density).toInt()
        } else {
            params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP)
            params.topMargin = resources.getDimensionPixelSize(R.dimen.map_controls_margin_top_landscape)
            params.bottomMargin = 0
        }

        controls.layoutParams = params
    }

    private fun centerFabReportHorizontallyOnTripDistanceValue() {
        if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) return

        val fabView = fabReport ?: return
        val distanceView = tvTripRemainingDistance ?: return
        if (!distanceView.isShown) return

        fabView.post {
            if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) return@post
            if (!isAdded || view == null) return@post

            val currentFab = fabReport ?: return@post
            val currentDistanceView = tvTripRemainingDistance ?: return@post
            if (!currentDistanceView.isShown) return@post

            val parentView = currentFab.parent as? ViewGroup ?: return@post
            if (currentFab.width <= 0 || currentDistanceView.width <= 0 || parentView.width <= 0) return@post

            val distanceLocation = IntArray(2)
            val parentLocation = IntArray(2)
            currentDistanceView.getLocationOnScreen(distanceLocation)
            parentView.getLocationOnScreen(parentLocation)

            val distanceCenterX = (distanceLocation[0] - parentLocation[0]).toFloat() + (currentDistanceView.width / 2f)
            val maxLeft = (parentView.width - currentFab.width).coerceAtLeast(0)
            val desiredLeft = (distanceCenterX - (currentFab.width / 2f)).roundToInt().coerceIn(0, maxLeft)
            val desiredMarginEnd = (parentView.width - desiredLeft - currentFab.width).coerceAtLeast(0)

            val currentParams = currentFab.layoutParams as? android.widget.RelativeLayout.LayoutParams ?: return@post
            currentParams.marginEnd = desiredMarginEnd
            currentParams.marginStart = 0
            currentFab.layoutParams = currentParams
            currentFab.bringToFront()
            syncFabFuelAlignmentToReport()
        }
    }

    private fun centerFabReportHorizontallyOnStopButton() {
        if (resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE) return

        val fabView = fabReport ?: return
        val stopView = btnStop ?: return
        if (!stopView.isShown) return

        fabView.post {
            if (resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE) return@post
            if (!isAdded || view == null) return@post

            val currentFab = fabReport ?: return@post
            if (!stopView.isShown) return@post

            val parentView = currentFab.parent as? ViewGroup ?: return@post
            if (currentFab.width <= 0 || stopView.width <= 0 || parentView.width <= 0) return@post

            val stopLocation = IntArray(2)
            val parentLocation = IntArray(2)
            stopView.getLocationOnScreen(stopLocation)
            parentView.getLocationOnScreen(parentLocation)

            val stopCenterX = (stopLocation[0] - parentLocation[0]).toFloat() + (stopView.width / 2f)
            val maxLeft = (parentView.width - currentFab.width).coerceAtLeast(0)
            val desiredLeft = (stopCenterX - (currentFab.width / 2f)).roundToInt().coerceIn(0, maxLeft)
            val desiredMarginEnd = (parentView.width - desiredLeft - currentFab.width).coerceAtLeast(0)

            val currentParams = currentFab.layoutParams as? android.widget.RelativeLayout.LayoutParams ?: return@post
            currentParams.marginEnd = desiredMarginEnd
            currentParams.marginStart = 0
            currentFab.layoutParams = currentParams
            syncFabFuelAlignmentToReport()
        }
    }

    private fun setNavigationActive(active: Boolean, preserveSessionState: Boolean = false) {
        if (active == isNavigationActive) return
        isNavigationActive = active
        hasReachedDestination = false
        bindGarageReminderBanner()

        if (active) {
            resetIdleMapFollowState()
            try {
                val geometryString = mapboxNavigation.getNavigationRoutes().firstOrNull()?.directionsRoute?.geometry()
                val routeGeometry = geometryString?.let { LineString.fromPolyline(it, 6) }
                reportsIntegration?.updateRouteGeometry(routeGeometry, true)
            } catch (e: Exception) {
                Log.e("MapFragment", "Failed to parse route geometry", e)
                reportsIntegration?.updateRouteGeometry(null, true)
            }
        } else {
            reportsIntegration?.updateRouteGeometry(null, false)
        }
        syncAverageSpeedRoute()
        
        if (!active) {
            hideArrivalActionPanel(animated = false)
        }
        navBackPressedCallback?.isEnabled = active

        if (active) {
            // Preview used along-route police/camera; navigation returns to live 20 km observe.
            resumeDefaultReportsAfterPreview()
            routeWeatherPreviewOverlay?.clear()
            stopNonNavigationLocationUpdates()
            NavigationVoiceGuidance.start(requireContext(), mapboxNavigation)
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
            applyTurnByTurnSessionChrome()
            applyLandscapeBottomHudAnchor()
            mapControlsContainer?.visibility = View.VISIBLE
            btnCameraNorthMode?.visibility = View.GONE
            btnOverview?.visibility = View.VISIBLE
            isTripCameraInOverview = false
            updateTripCameraToggleUi()
            btnPreviewOverview?.visibility = View.GONE
            btnPreviewRecenter?.visibility = View.GONE
            bottomHudRow?.bringToFront()
            if (preserveSessionState) {
                // Orientation restore path: keep existing session timing instead of restarting it.
                shouldResetOnConnect = false
                navSessionActive = true
                ensureActiveSessionKey()
                startAndBindServiceIfNeeded()

                val restoredStartTime = foregroundService?.getStartTime()
                    ?: navSessionStartTime.takeIf { it > 0L }
                    ?: SystemClock.elapsedRealtime()
                navSessionStartTime = restoredStartTime
                chronometerCar?.base = restoredStartTime
                chronometerCar?.start()

                updateLeanAngleVisibility()
                updateZeroButtonVisibility()
                startLeanAngleUpdates()
                startMapboxRenderLoop()
                repositionFabReport()
            } else {
                shouldResetOnConnect = true
                if (serviceBound && foregroundService != null) {
                    shouldResetOnConnect = false
                    resetSessionData()
                }
                startNavSessionTracking()
                startAndBindServiceIfNeeded()
            }
            llTemperature.visibility = View.GONE
            llAltitude.visibility = View.GONE
            hideIdleMapFollowChrome()
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
            resumeDefaultReportsAfterPreview()
            if (navigationObserversRegistered) {
                mapboxNavigation.unregisterRoutesObserver(sdkRoutesObserver)
                mapboxNavigation.unregisterRouteProgressObserver(sdkRouteProgressObserver)
                mapboxNavigation.unregisterArrivalObserver(sdkArrivalObserver)
                navigationObserversRegistered = false
            }
            NavigationVoiceGuidance.stop(requireContext(), mapboxNavigation)
            startNonNavigationLocationUpdates()
            mapStateViewModel.lastSpokenVoiceAnnouncement = null
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
            isTripCameraInOverview = false
            btnPreviewOverview?.visibility = View.GONE
            btnPreviewRecenter?.visibility = View.GONE
            stopNavSessionTracking()
            cleanupForegroundService()
            llTemperature.visibility = View.VISIBLE
            llAltitude.visibility = View.VISIBLE
            attachIdleAverageSpeedSignToPreview(false)
            updateIdleMapFollowUi()
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

        updateVoiceGuidanceToggleUi()
        averageSpeedController?.setSessionHudActive(active || navSessionActive)
        
        // Reposition FAB Report button based on mode
        repositionFabReport()
        repositionLandscapeAverageSpeedCluster()
        if (active) {
            hideCountryTrafficLayer()
        } else {
            showCountryTrafficLayer()
        }
    }

    /**
     * Turn-by-turn hides session km/time and Reset/End. Follow-line and free ride keep them.
     * Arrival actions still use the portrait session strip.
     */
    private fun applyTurnByTurnSessionChrome() {
        if (!isNavigationActive) return
        carModeContainer?.visibility = View.GONE
        if (isArrivalActionVisible) {
            navSessionContainer?.visibility = View.VISIBLE
            buttonContainer?.visibility = View.VISIBLE
            buttonActionsRow?.visibility = View.GONE
        } else {
            // Keep the portrait session strip in the tree so bottomHudRow can sit above it;
            // with children gone it collapses to 0 height.
            navSessionContainer?.visibility = View.VISIBLE
            buttonContainer?.visibility = View.GONE
            buttonActionsRow?.visibility = View.GONE
        }
        requestFabReportReposition()
        repositionLandscapeAverageSpeedCluster()
    }

    private fun showEndNavigationSheet() {
        if (!isAdded || !isNavigationActive) return
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle(getString(R.string.map_end_navigation_title))
            .setMessage(getString(R.string.map_end_navigation_message))
            .setPositiveButton(getString(R.string.map_save_and_finish)) { _, _ ->
                finishNavigationAndSaveSession()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        DialogHelper.styleDialogButtons(dialog)
        dialog.show()
    }

    private fun finishNavigationAndSaveSession() {
        if (serviceBound) {
            saveAndFinishSession()
        } else {
            stopNavigationInline()
        }
    }

    private fun showExitNavigationDialog() {
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle(getString(R.string.map_exit_navigation_title))
            .setMessage(getString(R.string.map_exit_session_not_saved_message))
            .setPositiveButton(getString(R.string.map_exit_yes)) { _, _ ->
                stopNavigationInline()
            }
            .setNegativeButton(getString(R.string.map_exit_no), null)
            .create()
        DialogHelper.styleDialogButtons(dialog)
        dialog.show()
    }

    private fun showExitNormalSessionDialog() {
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle(getString(R.string.map_exit_session_title))
            .setMessage(getString(R.string.map_exit_session_not_saved_message))
            .setPositiveButton(getString(R.string.map_exit_yes)) { _, _ ->
                stopNormalSessionWithoutSave()
            }
            .setNegativeButton(getString(R.string.map_exit_no), null)
            .create()
        DialogHelper.styleDialogButtons(dialog)
        dialog.show()
    }

    private fun showArrivalActionPanel() {
        val panel = arrivalActionContainer ?: return
        if (isArrivalActionVisible) return
        isArrivalActionVisible = true
        setButtonRowEnabled(false)
        applyTurnByTurnSessionChrome()
        // Hide the regular action row completely so its white/red backgrounds
        // do not bleed behind the arrival buttons.
        buttonActionsRow?.visibility = View.GONE

        panel.clearAnimation()
        panel.visibility = View.VISIBLE
        panel.bringToFront()
        panel.post {
            if (!isAdded || !isArrivalActionVisible) return@post
            val hiddenOffset = panel.height.toFloat().takeIf { it > 0f }
                ?: (72f * resources.displayMetrics.density)
            panel.translationY = hiddenOffset
            panel.alpha = 0f
            panel.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(230L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun hideArrivalActionPanel(animated: Boolean) {
        val panel = arrivalActionContainer ?: return
        if (!isArrivalActionVisible && panel.visibility != View.VISIBLE) return
        isArrivalActionVisible = false
        setButtonRowEnabled(true)
        buttonActionsRow?.visibility = View.VISIBLE

        panel.clearAnimation()
        if (!animated || panel.height == 0) {
            panel.alpha = 0f
            panel.visibility = View.GONE
            return
        }

        panel.animate()
            .translationY(panel.height.toFloat())
            .alpha(0f)
            .setDuration(180L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                panel.visibility = View.GONE
                panel.translationY = 0f
            }
            .start()
    }

    private fun setButtonRowEnabled(enabled: Boolean) {
        buttonActionsRow?.isEnabled = enabled
        btnReset?.isEnabled = enabled
        btnZero?.isEnabled = enabled
        btnStop?.isEnabled = enabled
        buttonActionsRow?.let { row ->
            for (i in 0 until row.childCount) {
                row.getChildAt(i).isEnabled = enabled
            }
        }
    }

    private fun showArrivalDiscardSessionDialog() {
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle(getString(R.string.map_discard_session_title))
            .setMessage(getString(R.string.map_discard_session_message))
            .setPositiveButton(getString(R.string.map_exit_yes)) { _, _ ->
                hideArrivalActionPanel(animated = false)
                stopNormalSessionWithoutSave()
            }
            .setNegativeButton(getString(R.string.map_exit_no), null)
            .create()
        DialogHelper.styleDialogButtons(dialog)
        dialog.show()
    }

    private fun continueSessionAsNormalAfterArrival() {
        hideArrivalActionPanel(animated = true)

        mapboxNavigation.setNavigationRoutes(emptyList())
        if (navigationObserversRegistered) {
            mapboxNavigation.unregisterRoutesObserver(sdkRoutesObserver)
            mapboxNavigation.unregisterRouteProgressObserver(sdkRouteProgressObserver)
            mapboxNavigation.unregisterArrivalObserver(sdkArrivalObserver)
            navigationObserversRegistered = false
        }
        NavigationVoiceGuidance.stop(requireContext(), mapboxNavigation)

        val mapView = mapboxMapView
        if (mapView != null) {
            val locationPlugin = mapView.getPlugin(Plugin.MAPBOX_LOCATION_COMPONENT_PLUGIN_ID) as? LocationComponentPlugin
            if (locationPlugin != null) {
                onIndicatorPositionChangedListener?.let { locationPlugin.removeOnIndicatorPositionChangedListener(it) }
            }
        }

        isNavigationActive = false
        hasReachedDestination = false
        updateVoiceGuidanceToggleUi()
        resetNavigationUiAfterStop()
        syncAverageSpeedRoute()
        setNormalSessionUiActive(true)
        navBackPressedCallback?.isEnabled = true

        enforcePitchZero = false
        resetNormalDrivingCameraState()
        // Voice stop ends the Mapbox trip session; free-ride uses fused GPS, not Navigation MAU.
        startNonNavigationLocationUpdates()
        smoothHandoffToNormalDrivingCamera()
        startMapboxRenderLoop()
        (currentRawLocation ?: currentLocation)?.let { ingestNonNavigationLocation(it) }
        // Arrival panel is hidden with animation; request two passes so FAB settles
        // to the same position as direct free-driving mode.
        requestFabReportReposition()
        view?.postDelayed({ requestFabReportReposition() }, 260L)
        showCountryTrafficLayer()
    }

    private fun smoothHandoffToNormalDrivingCamera() {
        val mapView = mapboxMapView ?: return
        val location = currentRawLocation
            ?: currentLocation
            ?: mapStateViewModel.lastKnownLocation
            ?: return

        val speedKmh = (location.speed * 3.6f).coerceAtLeast(0f)
        lastSpeedKmh = speedKmh

        val bearing = when {
            isNorthUpMode -> 0f
            location.hasBearing() -> location.bearing
            else -> mapView.mapboxMap.cameraState.bearing.toFloat()
        }

        val targetZoomValue = getNormalDrivingZoomForSpeed(speedKmh)
        val targetPitchValue = getNormalDrivingPitchForSpeed(speedKmh)

        val anchorPoint = GeoPoint(location.latitude, location.longitude)
        val centerPoint = anchorPoint

        mapboxTargetPosition = anchorPoint
        mapboxSmoothedTargetPosition = anchorPoint
        mapboxCurrentPosition = anchorPoint
        mapboxCurrentCameraCenter = centerPoint
        mapboxTargetBearing = bearing
        mapboxCurrentBearing = bearing
        targetMapOrientation = if (isNorthUpMode) 0f else -bearing
        currentMapOrientation = targetMapOrientation
        targetZoom = targetZoomValue
        currentZoom = targetZoomValue
        targetPitch = if (isNorthUpMode) 0.0 else targetPitchValue
        currentPitch = targetPitch
        mapboxLastUpdateTime = SystemClock.elapsedRealtime()
        isFirstLocation = false

        beginLiveSessionCameraIntro(NORMAL_CONTINUE_HANDOFF_ANIMATION_MS)
    }

    private fun stopNormalSessionWithoutSave() {
        hideArrivalActionPanel(animated = false)
        if (isNavigationActive) {
            mapboxNavigation.setNavigationRoutes(emptyList())
            setNavigationActive(false)
            resetNavigationUiAfterStop()
        } else {
            clearFollowLineSessionExtras()
        }
        stopNavSessionTracking()
        cleanupForegroundService()
        setNormalSessionUiActive(false)
        resetNormalDrivingCameraState()
        resetNavSessionMetrics(resetTime = true)
        resetActiveSessionKey(clearPendingFuel = true)
        enforcePitchZero = true
    }

    private fun startNavSessionTracking() {
        resetIdleMapFollowState()
        navSessionActive = true
        ensureActiveSessionKey()
        navBackPressedCallback?.isEnabled = true
        navSessionStartTime = SystemClock.elapsedRealtime()
        navSessionDistanceMeters = 0.0
        navSessionLastLocation = null
        chronometerCar?.apply {
            base = navSessionStartTime
            start()
        }
        updateDistanceCarDisplay()
        speedTextCar?.text = "0"
        updateLeanAngleVisibility()
        updateZeroButtonVisibility()
        startLeanAngleUpdates()
        startMapboxRenderLoop()
        
        // Reposition FAB Report button for session mode
        repositionFabReport()
    }

    private fun ensureActiveSessionKey() {
        if (activeSessionKey == 0L) {
            activeSessionKey = System.currentTimeMillis()
        }
    }

    private fun openSessionFuelEntry() {
        val profile = getActiveProfile() ?: run {
            Toast.makeText(requireContext(), getString(R.string.map_select_profile_prompt), Toast.LENGTH_SHORT).show()
            return
        }
        ensureActiveSessionKey()
        val location = currentRawLocation ?: currentLocation ?: mapStateViewModel.lastKnownLocation
        val intent = Intent(requireContext(), GarageFuelEntryActivity::class.java).apply {
            putExtra(GarageFuelEntryActivity.EXTRA_PROFILE_ID, profile.id)
            putExtra(GarageFuelEntryActivity.EXTRA_SESSION_KEY, activeSessionKey)
            location?.let {
                putExtra(GarageFuelEntryActivity.EXTRA_SESSION_LATITUDE, it.latitude)
                putExtra(GarageFuelEntryActivity.EXTRA_SESSION_LONGITUDE, it.longitude)
            }
        }
        startActivity(intent)
    }

    private fun resetActiveSessionKey(clearPendingFuel: Boolean) {
        if (activeSessionKey > 0L && clearPendingFuel) {
            SessionFuelStorage.clearPending(requireContext(), activeSessionKey)
        }
        activeSessionKey = 0L
    }

    private fun stopNavSessionTracking() {
        navSessionActive = false
        navBackPressedCallback?.isEnabled = false
        chronometerCar?.stop()
        stopLeanAngleUpdates()
        stopMapboxRenderLoop()
        
        // Reposition FAB Report button back to free driving mode
        repositionFabReport()
    }

    private fun resetNavSessionMetrics(resetTime: Boolean) {
        navSessionDistanceMeters = 0.0
        navSessionLastLocation = null
        updateDistanceCarDisplay()
        if (resetTime) {
            navSessionStartTime = SystemClock.elapsedRealtime()
            chronometerCar?.base = navSessionStartTime
        }
        resetAngleUi()
    }

    // Renders the session distance using the unit that follows the speed setting (km / mi).
    private fun updateDistanceCarDisplay() {
        val ctx = context ?: return
        distanceTextCar?.text = UnitsManager.formatTripDistanceValue(navSessionDistanceMeters / 1000.0, ctx)
        distanceUnitCar?.text = UnitsManager.getTripDistanceUnitSymbol(ctx)
    }

    private fun updateNavSessionMetrics(location: Location) {
        if (!navSessionActive) return

        val speedKmh = (location.speed * 3.6f).coerceAtLeast(0f)
        speedTextCar?.text = UnitsManager.formatSpeedValueWhole(speedKmh, requireContext())

        // The foreground service accumulates distance reliably in the background (even with the
        // screen locked), so it is the source of truth. The map-based accumulation only freezes
        // while locked and drops long gaps, which is exactly what caused the distance to "reset"
        // to the pre-lock value. Fall back to it only until the service is bound.
        val serviceDistanceMeters = foregroundService?.getTotalDistanceMeters()?.coerceAtLeast(0.0)
        if (serviceDistanceMeters != null) {
            navSessionDistanceMeters = maxOf(navSessionDistanceMeters, serviceDistanceMeters)
        } else {
            navSessionLastLocation?.let { prev ->
                if (location.accuracy <= 25f && prev.accuracy <= 25f) {
                    val distMeters = prev.distanceTo(location).toDouble()
                    val dtSeconds = (location.time - prev.time) / 1000.0

                    if (distMeters >= 0.3 && dtSeconds in 0.2..10.0) {
                        val segmentSpeedMps = distMeters / dtSeconds
                        val reportedSpeedMps = maxOf(prev.speed.toDouble(), location.speed.toDouble(), 0.0)
                        val adaptiveMaxSpeedMps = maxOf(70.0, reportedSpeedMps * 2.0 + 12.0)

                        if (segmentSpeedMps <= adaptiveMaxSpeedMps) {
                            navSessionDistanceMeters += distMeters
                        }
                    }
                }
            }
        }
        navSessionLastLocation = location

        updateDistanceCarDisplay()

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
        mapboxMapView?.mapboxMap?.setCamera(
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
        showCountryTrafficLayer()
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
        requireContext().startService(serviceIntent)

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
                    putExtra(MainContainerActivity.EXTRA_INITIAL_PAGE, MainContainerActivity.PAGE_MAP)
                })
                return
            }

            val race = createRaceFromSession()
            RouteStorage.saveRoutePoints(requireContext(), race.id, rawRoutePoints)
            val allRaces = RouteStorage.loadRaces(requireContext()).toMutableList()
            allRaces.add(race) // race.routePoints already empty — trail is in points file
            RouteStorage.saveRaces(requireContext(), allRaces)
            SessionFuelStorage.linkSessionToRace(requireContext(), activeSessionKey, race.id)
            resetActiveSessionKey(clearPendingFuel = false)

            // Snapshot generation is intentionally deferred to ProcessingActivity.
            // This avoids race conditions where an early raw snapshot overwrites
            // the processed/final snapshot for the same raceId.

            pendingExitAfterSave = true
            finalizeNormalSessionStateAfterSave()
            cleanupForegroundService()

            val intent = Intent(requireContext(), SaveSessionActivity::class.java).apply {
                putExtra("raceId", race.id)
                putExtra("isNewSession", true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            showError("Error saving the race: ${e.message}")
        }
    }

    private fun finalizeNormalSessionStateAfterSave() {
        if (isNavigationActive) {
            mapboxNavigation.setNavigationRoutes(emptyList())
            setNavigationActive(false)
            resetNavigationUiAfterStop()
        } else {
            clearFollowLineSessionExtras()
        }
        stopNavSessionTracking()
        setNormalSessionUiActive(false)
        resetNormalDrivingCameraState()
        resetNavSessionMetrics(resetTime = true)
        enforcePitchZero = true
        shouldRestoreNavigationAfterRecreate = false
        shouldRestoreNormalSessionAfterRecreate = false
        shouldRestoreFollowLineAfterRecreate = false
        navBackPressedCallback?.isEnabled = false
    }

    private fun createRaceFromSession(): Race {
        val activeProfile = getActiveProfile()
        val sessionNumber = getNextSessionNumber(activeProfile?.id ?: -1L)

        val isMotorcycle = activeProfile?.vehicleType == Profile.VehicleType.MOTORCYCLE
        val maxLeftAngle = if (isMotorcycle) (foregroundService?.getMaxLeftAngle() ?: 0f) else 0f
        val maxRightAngle = if (isMotorcycle) (foregroundService?.getMaxRightAngle() ?: 0f) else 0f
        val raceId = System.currentTimeMillis()

        return Race(
            profileId = activeProfile?.id ?: -1L,
            id = raceId,
            routePoints = emptyList(),
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
            time100to200 = 0L,
            recordedWithRaceBox = RaceBoxDebugGate.shouldOverridePhoneGps(requireContext())
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
            putExtra(MainContainerActivity.EXTRA_NAV_ITEM_ID, R.id.navMap)
        })
    }

    private fun showError(message: String) {
        val errorDialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle(getString(R.string.dialog_error_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.dialog_ok), null)
            .create()
        DialogHelper.styleDialogButtons(errorDialog)
        errorDialog.show()
    }

    private fun onDestinationReached() {
        if (hasReachedDestination) return
        hasReachedDestination = true
        showArrivalActionPanel()
    }

    private fun setCompactRouteMode(enabled: Boolean) {
        if (enabled && (isFollowLineActive || isNavigationActive || navSessionActive)) return
        // Hide/show map screen UI to match TestNavigationActivity behaviour after destination selection
        if (!enabled) {
            attachIdleAverageSpeedSignToPreview(false)
        }
        llTemperature.visibility = if (enabled) View.GONE else View.VISIBLE
        llAltitude.visibility = if (enabled) View.GONE else View.VISIBLE
        if (enabled) {
            hideIdleMapFollowChrome()
        } else {
            updateIdleMapFollowUi()
        }
        fabReport?.visibility = if (enabled) View.GONE else View.VISIBLE

        // If exiting preview mode, reposition FAB Report to ensure correct visibility/position
        if (!enabled) {
            repositionFabReport()
        }

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
            attachIdleAverageSpeedSignToPreview(true)
        }
    }

    private fun findRouteInline(destination: Point) {
        // Always Mapbox mode
        if (!this::routeLineApi.isInitialized || !this::routeLineView.isInitialized) {
            Toast.makeText(requireContext(), getString(R.string.map_loading_in_progress), Toast.LENGTH_SHORT).show()
            return
        }

        val originPoint = fixedOriginForRoute ?: run {
            val originLoc = currentLocation ?: mapStateViewModel.lastKnownLocation
            if (originLoc != null) {
                Point.fromLngLat(originLoc.longitude, originLoc.latitude)
            } else null
        }

        if (originPoint == null) {
            Toast.makeText(requireContext(), getString(R.string.map_no_current_location), Toast.LENGTH_SHORT).show()
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
            // Re-assert after other builders so traffic coloring always has the data it needs.
            .profile(DirectionsCriteria.PROFILE_DRIVING_TRAFFIC)
            .overview(DirectionsCriteria.OVERVIEW_FULL)
            .annotationsList(
                listOf(
                    DirectionsCriteria.ANNOTATION_CONGESTION_NUMERIC,
                    DirectionsCriteria.ANNOTATION_DISTANCE,
                    DirectionsCriteria.ANNOTATION_DURATION
                )
            )

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
                    logRouteCongestionSample(routes.first())

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
                    Toast.makeText(requireContext(), getString(R.string.route_find_error), Toast.LENGTH_SHORT).show()
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
        tvRouteDistance.text = getString(R.string.distance_km_value, df.format(distanceKm))
        val minutes = (durationSeconds / 60).toInt()
        val h = minutes / 60
        val m = minutes % 60
        tvRouteDuration.text = if (h > 0) {
            getString(R.string.route_duration_hours_minutes, h, m)
        } else {
            getString(R.string.route_duration_minutes, m)
        }

        setCompactRouteMode(true)
        routeWeatherPreviewOverlay?.showForRoute(primary)
        showRoutePreviewReports(primary)
        hideCountryTrafficLayer()
    }

    private var lastAlongRouteReportsGeometry: String? = null

    private fun showRoutePreviewReports(route: NavigationRoute) {
        // Along-route corridor markers are for route preview only.
        if (isNavigationActive || isFollowLineActive) return
        val geometry = route.directionsRoute.geometry().orEmpty()
        if (geometry.isBlank()) return
        if (
            geometry == lastAlongRouteReportsGeometry &&
            reportsIntegration?.isRoutePreviewReportsActive() == true
        ) {
            return
        }
        val lineString = try {
            LineString.fromPolyline(geometry, 6)
        } catch (_: Exception) {
            try {
                LineString.fromPolyline(geometry, 5)
            } catch (_: Exception) {
                null
            }
        } ?: return
        lastAlongRouteReportsGeometry = geometry
        // Full-size markers in preview and navigation (weather pills sit beside the route).
        reportsIntegration?.showReportsAlongRoute(
            route = lineString,
            previewCompactSizing = false
        )
    }

    private fun resumeDefaultReportsAfterPreview() {
        lastAlongRouteReportsGeometry = null
        val location = currentLocation
        if (location != null) {
            reportsIntegration?.resumeNearbyReportsObservation(location.latitude, location.longitude)
            lastReportsQueryLocation = location
        } else {
            reportsIntegration?.exitRoutePreviewReports()
        }
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
            Toast.makeText(requireContext(), getString(R.string.route_selected_format, selectedRouteIndex + 1), Toast.LENGTH_SHORT).show()
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
                overviewPadding = navigationOverviewPadding()
                followingPadding = navigationFollowingPadding()
            }
            navigationCamera = NavigationCamera(mv.mapboxMap, mv.camera, viewportDataSource)
            navigationCamera.registerNavigationCameraStateChangeObserver(navigationCameraStateObserver)
            mv.camera.addCameraAnimationsLifecycleListener(NavigationBasicGesturesHandler(navigationCamera))

            mv.mapboxMap.addOnCameraChangeListener {
                enforcePitchZeroIfNeeded()
            }

            setupIdleMapFollowGestures(mv)
            
            // Setup camera control buttons
            setupNavigationCameraButtons()

            // Enable alternative route selection by tapping on a route (same UX as TestNavigationActivity)
            mv.mapboxMap.addOnMapClickListener { clickPoint ->
                when {
                    pendingMapPickCategory != null -> {
                        handleLongPressDestination(clickPoint)
                        true
                    }
                    isShortcutMapPickInFlight -> true
                    handlePOISearchMarkerTap(clickPoint) -> true
                    routeInfoContainer.visibility == View.VISIBLE && currentRoutesOriginal.size > 1 -> {
                        handleRouteClick(clickPoint)
                        true
                    }
                    else -> false
                }
            }
            
            // Enable long press to set destination (like Google Maps)
            mv.mapboxMap.addOnMapLongClickListener { longPressPoint ->
                when {
                    pendingMapPickCategory != null -> {
                        handleLongPressDestination(longPressPoint)
                        true
                    }
                    isShortcutMapPickInFlight -> true
                    routeInfoContainer.visibility == View.GONE -> {
                        handleLongPressDestination(longPressPoint)
                        true
                    }
                    else -> false
                }
            }
        }
        
        val styleUri = getString(R.string.mapbox_style_uri)
        // Seed camera before style finishes so the first painted frame is never the globe.
        if (shouldRestoreNavigationAfterRecreate || shouldRestoreFollowLineAfterRecreate) {
            seedGuidanceCameraFromLastKnownLocation()
        } else {
            applyIdleStartupCamera(force = true)
        }
        mapboxMapView?.mapboxMap?.loadStyleUri(styleUri)
        
        mapboxMapView?.mapboxMap?.getStyle { style ->
            currentMapboxStyle = style
            averageSpeedController?.attachStyle(style)

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

            val followLine = isFollowLineActive || shouldRestoreFollowLineAfterRecreate
            val routesToRender = mapboxNavigation.getNavigationRoutes().ifEmpty {
                listOfNotNull(mapStateViewModel.followLineRoute)
            }
            if (routesToRender.isNotEmpty() && this::routeLineApi.isInitialized && this::routeLineView.isInitialized) {
                if (!followLine && this::viewportDataSource.isInitialized) {
                    viewportDataSource.onRouteChanged(routesToRender.first())
                    viewportDataSource.evaluate()
                }
                val routesForLine = if (followLine) listOf(routesToRender.first()) else routesToRender
                val metadata = mapboxNavigation.getAlternativeMetadataFor(routesForLine)
                routeLineApi.setNavigationRoutes(routesForLine, metadata) { value ->
                    routeLineView.renderRouteDrawData(style, value)
                }
            }
            if (followLine) {
                hideRoutePreviewChrome()
                attachFollowLineTraveledRouteListener()
            }

            // Style load can reset camera to world — re-apply before revealing the map.
            if (shouldRestoreNavigationAfterRecreate || shouldRestoreFollowLineAfterRecreate) {
                seedGuidanceCameraFromLastKnownLocation()
            } else if (!isNavigationActive && !navSessionActive && currentDestination == null) {
                applyIdleStartupCamera(force = true)
            }

            revealMapWhenCameraReady()

            // Enable Mapbox Location Component (SDK puck) when style is ready
            tryEnableMapboxLocationComponent()
            syncCountryTrafficLayerVisibility()

            if (isNavigationActive || shouldRestoreNavigationAfterRecreate) {
                mapboxMapView?.post { snapNavigationCameraToFollowingInstant() }
            } else if (isFollowLineActive || shouldRestoreFollowLineAfterRecreate) {
                mapboxMapView?.post { snapFollowCameraToPuckInstant() }
            }
        }
        
        configureMapboxPlugins()
        mapboxMapView?.postDelayed({ configureMapboxPlugins() }, 1000)
    }

    /**
     * Places the idle map on the last known camera/location instead of Mapbox's default globe.
     * Safe to call on every MapView/style create; skipped during nav / session / route preview.
     */
    private fun applyIdleStartupCamera(force: Boolean = false) {
        if (isNavigationActive || navSessionActive || currentDestination != null) return
        val map = mapboxMapView?.mapboxMap ?: return

        if (!force && mapStateViewModel.hasInitializedCamera && map.cameraState.zoom >= 10.0) {
            return
        }

        val saved = mapStateViewModel.lastMapState
        val loc = mapStateViewModel.lastKnownLocation
        when {
            saved != null -> {
                map.setCamera(
                    CameraOptions.Builder()
                        .center(MapboxPoint.fromLngLat(saved.centerLon, saved.centerLat))
                        .zoom(saved.zoom.coerceIn(11.0, 19.5))
                        .bearing(0.0)
                        .pitch(0.0)
                        .padding(idleMapFollowPadding())
                        .build()
                )
                mapStateViewModel.hasInitializedCamera = true
            }
            loc != null -> {
                map.setCamera(
                    CameraOptions.Builder()
                        .center(MapboxPoint.fromLngLat(loc.longitude, loc.latitude))
                        .zoom(IDLE_MAP_FOLLOW_ZOOM)
                        .bearing(0.0)
                        .pitch(0.0)
                        .padding(idleMapFollowPadding())
                        .build()
                )
                mapStateViewModel.hasInitializedCamera = true
            }
            else -> {
                Log.d("MapFragment", "No saved camera/location yet — waiting for first GPS fix")
            }
        }
    }

    /** Avoid flashing the globe: keep map hidden until we have a local camera seed (or timeout). */
    private fun revealMapWhenCameraReady() {
        val mapView = mapboxMapView ?: return
        fun reveal() {
            mapView.setBackgroundColor(Color.TRANSPARENT)
            mapView.alpha = 1f
        }
        if (mapStateViewModel.hasInitializedCamera ||
            (mapView.mapboxMap.cameraState.zoom >= 10.0)
        ) {
            mapView.post { reveal() }
            return
        }
        // First install / no cache: wait briefly for GPS, then show anyway.
        mapView.postDelayed({
            if (!isAdded) return@postDelayed
            if (!mapStateViewModel.hasInitializedCamera) {
                applyIdleStartupCamera(force = true)
            }
            reveal()
        }, 2500L)
    }

    /**
     * Country-wide Studio traffic overlay is permanently off — Mapbox live traffic is inaccurate
     * for our use. Route-line congestion colors (Navigation SDK) are unchanged.
     */
    private fun syncCountryTrafficLayerVisibility() {
        hideCountryTrafficLayer()
    }

    private fun hideCountryTrafficLayer() {
        applyCountryTrafficLayerVisibility(visible = false)
    }

    private fun showCountryTrafficLayer() {
        // Kept for call-site compatibility; overlay stays hidden.
        hideCountryTrafficLayer()
    }

    private fun applyCountryTrafficLayerVisibility(visible: Boolean) {
        val style = currentMapboxStyle ?: mapboxMapView?.mapboxMap?.style ?: return
        MapCountryTrafficLayer.setVisible(style, visible = false)
    }

    private fun logRouteCongestionSample(route: NavigationRoute) {
        try {
            val values = route.directionsRoute.legs()
                ?.asSequence()
                ?.mapNotNull { it.annotation()?.congestionNumeric() }
                ?.flatten()
                ?.filterNotNull()
                ?.toList()
                .orEmpty()
            if (values.isEmpty()) {
                Log.w("MapFragment", "Route has no congestion_numeric annotations — traffic colors won't appear")
            } else {
                Log.d(
                    "MapFragment",
                    "Route congestion_numeric: count=${values.size}, max=${values.maxOrNull()}, " +
                        "avg=${values.average().toInt()}"
                )
            }
        } catch (e: Exception) {
            Log.w("MapFragment", "Failed to read route congestion annotations", e)
        }
    }
    
    private fun setupIdleMapFollowGestures(mapView: MapboxMapView) {
        idleMapFollowMoveListener?.let { mapView.gestures.removeOnMoveListener(it) }
        idleMapFollowScaleListener?.let { mapView.gestures.removeOnScaleListener(it) }

        val moveListener = object : OnMoveListener {
            override fun onMoveBegin(detector: MoveGestureDetector) {
                onIdleMapUserGesture()
            }

            override fun onMove(detector: MoveGestureDetector): Boolean = false

            override fun onMoveEnd(detector: MoveGestureDetector) = Unit
        }
        val scaleListener = object : OnScaleListener {
            override fun onScaleBegin(detector: StandardScaleGestureDetector) {
                onIdleMapUserGesture()
            }

            override fun onScale(detector: StandardScaleGestureDetector) = Unit

            override fun onScaleEnd(detector: StandardScaleGestureDetector) = Unit
        }

        idleMapFollowMoveListener = moveListener
        idleMapFollowScaleListener = scaleListener
        mapView.gestures.addOnMoveListener(moveListener)
        mapView.gestures.addOnScaleListener(scaleListener)
    }

    private fun onIdleMapUserGesture() {
        if (idleFollowProgrammaticCameraMove) return
        if (isNavigationActive || navSessionActive) return
        isIdleMapFollowEnabled = false
        stopIdleMapRenderLoop()
        updateIdleMapFollowUi()
    }

    private fun hideIdleMapFollowChrome() {
        if (!::fabMyLocationContainer.isInitialized || !::idleMapSpeedContainer.isInitialized) return
        if (::idleMapLeftChrome.isInitialized) {
            idleMapLeftChrome.visibility = View.GONE
        }
        fabMyLocationContainer.visibility = View.GONE
        idleMapSpeedContainer.visibility = View.GONE
        if (!isIdleMapChromeVisible()) {
            idleAverageSpeedSignSlot?.visibility = View.GONE
        }
    }

    private fun attachIdleAverageSpeedSignToPreview(inPreview: Boolean) {
        val slot = idleAverageSpeedSignSlot ?: return
        val params = slot.layoutParams as? android.widget.RelativeLayout.LayoutParams ?: return
        params.removeRule(android.widget.RelativeLayout.ALIGN_BOTTOM)
        params.removeRule(android.widget.RelativeLayout.ALIGN_TOP)
        params.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
        params.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL)
        if (inPreview) {
            params.addRule(android.widget.RelativeLayout.ALIGN_TOP, R.id.btnMotorwayOptions)
            params.addRule(android.widget.RelativeLayout.ALIGN_BOTTOM, R.id.btnMotorwayOptions)
            slot.visibility = View.VISIBLE
            slot.bringToFront()
        } else {
            params.addRule(android.widget.RelativeLayout.ALIGN_BOTTOM, R.id.llAltitude)
            if (!isIdleMapChromeVisible()) {
                slot.visibility = View.GONE
            }
        }
        slot.layoutParams = params
    }

    private fun isIdleMapChromeVisible(): Boolean {
        if (isNavigationActive || navSessionActive || isFollowLineActive) return false
        if (::routeInfoContainer.isInitialized && routeInfoContainer.visibility == View.VISIBLE) return false
        return true
    }

    private fun isInlineSearchVisible(): Boolean {
        return ::searchContainer.isInitialized && searchContainer.visibility == View.VISIBLE
    }

    private fun updateIdleMapFollowUi(speedKmh: Float? = null) {
        if (!::fabMyLocationContainer.isInitialized || !::idleMapSpeedContainer.isInitialized) return
        if (!isIdleMapChromeVisible()) {
            hideIdleMapFollowChrome()
            return
        }

        if (::idleMapLeftChrome.isInitialized) {
            idleMapLeftChrome.visibility = View.VISIBLE
        }
        if (isInlineSearchVisible()) {
            idleAverageSpeedSignSlot?.visibility = View.GONE
        } else {
            idleAverageSpeedSignSlot?.visibility = View.VISIBLE
            idleAverageSpeedSignSlot?.bringToFront()
            idleAverageSpeedSignSlot?.translationZ = 28f * resources.displayMetrics.density
        }
        if (isIdleMapFollowEnabled && canIdleMapFollow()) {
            fabMyLocationContainer.visibility = View.GONE
            idleMapSpeedContainer.visibility = View.VISIBLE
            if (speedKmh != null && isAdded && context != null) {
                tvIdleMapSpeed.text = UnitsManager.formatSpeedValueWhole(speedKmh, requireContext())
            }
        } else {
            fabMyLocationContainer.visibility = View.VISIBLE
            idleMapSpeedContainer.visibility = View.GONE
        }
    }

    private fun canIdleMapFollow(): Boolean {
        if (isNavigationActive || navSessionActive || !isIdleMapFollowEnabled) return false
        if (!::routeInfoContainer.isInitialized) return true
        return routeInfoContainer.visibility != View.VISIBLE
    }

    private fun feedIdleMapFollowTarget(location: Location) {
        if (!canIdleMapFollow()) {
            stopIdleMapRenderLoop()
            return
        }

        val target = GeoPoint(location.latitude, location.longitude)
        idleFollowTargetPosition = target
        if (idleFollowSmoothPosition == null) {
            idleFollowSmoothPosition = target
            idleFollowLastFrameTime = 0L
            applyIdleMapFollowCamera(target)
        }
        updateIdleMapFollowUi((location.speed * 3.6f).coerceAtLeast(0f))
        startIdleMapRenderLoop()
    }

    private fun startIdleMapRenderLoop() {
        if (idleMapRenderRunnable != null) return
        val runnable = object : Runnable {
            override fun run() {
                if (!canIdleMapFollow() || !isAdded) {
                    stopIdleMapRenderLoop()
                    return
                }
                updateIdleMapFollowAnimation()
                handler.postDelayed(this, 33L)
            }
        }
        idleMapRenderRunnable = runnable
        handler.post(runnable)
    }

    private fun stopIdleMapRenderLoop() {
        idleMapRenderRunnable?.let { handler.removeCallbacks(it) }
        idleMapRenderRunnable = null
        idleFollowLastFrameTime = 0L
    }

    private fun resetIdleMapFollowState() {
        stopIdleMapRenderLoop()
        idleFollowTargetPosition = null
        idleFollowSmoothPosition = null
    }

    private fun updateIdleMapFollowAnimation() {
        val mapView = mapboxMapView ?: return
        if (mapView.width < 50 || mapView.height < 50) return

        val target = puckIndicatorPosition ?: idleFollowTargetPosition ?: return
        idleFollowSmoothPosition = target
        idleFollowLastFrameTime = SystemClock.elapsedRealtime()
        applyIdleMapFollowCamera(target)
    }

    private fun idleMapFollowPadding(): EdgeInsets {
        return poiMapCameraPadding ?: EdgeInsets(0.0, 0.0, 0.0, 0.0)
    }

    private fun applyIdleMapFollowCamera(center: GeoPoint) {
        val mapView = mapboxMapView ?: return
        idleFollowProgrammaticCameraMove = true
        mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(MapboxPoint.fromLngLat(center.longitude, center.latitude))
                .zoom(IDLE_MAP_FOLLOW_ZOOM)
                .bearing(0.0)
                .pitch(0.0)
                .padding(idleMapFollowPadding())
                .build()
        )
        mapView.post { idleFollowProgrammaticCameraMove = false }
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

        // Keep the visual puck on raw GPS; live follow camera tracks this same puck.
        isUsingRawPuckLocationProvider = false

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
        locationPlugin?.addOnIndicatorPositionChangedListener(liveFollowPuckCameraListener)
    
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
        locationPlugin?.removeOnIndicatorPositionChangedListener(liveFollowPuckCameraListener)
        locationPlugin?.updateSettings { enabled = false }
        puckIndicatorPosition = null
        isMapboxLocationComponentEnabled = false
        isUsingRawPuckLocationProvider = false
    }

    private fun initNavigation() {
        // Defensive: MyApplication already calls MapboxNavigationApp.setup, but keep this aligned with TestNavigationActivity.
        if (!MapboxNavigationApp.isSetup()) {
            MapboxNavigationApp.setup(NavigationOptions.Builder(requireContext()).build())
        }
    }

    private fun speakInitialNavigationAnnouncementIfAvailable(route: NavigationRoute) {
        if (!isNavigationActive || !VoiceAlertsSettings.isEnabled(requireContext())) return

        val firstStep = route.directionsRoute
            ?.legs()?.firstOrNull()
            ?.steps()?.firstOrNull()

        val initialAnnouncement = (
            firstStep?.voiceInstructions()?.firstOrNull()?.announcement()
                ?: firstStep?.maneuver()?.instruction()
        ).orEmpty()

        if (initialAnnouncement.isBlank()) return
        mapStateViewModel.lastSpokenVoiceAnnouncement = initialAnnouncement
        NavigationVoiceGuidance.speakManual(requireContext(), initialAnnouncement)
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

        if (profile == null) {
            Toast.makeText(requireContext(), getString(R.string.map_select_profile_prompt), Toast.LENGTH_SHORT).show()
            val intent = Intent(requireContext(), MainContainerActivity::class.java).apply {
                putExtra(MainContainerActivity.EXTRA_INITIAL_PAGE, MainContainerActivity.PAGE_RACES)
            }
            startActivity(intent)
            return
        }
        if (!com.revix.app.billing.ProGate.ensureWritable(
                requireContext(),
                profile.id,
                com.revix.app.billing.ProAccess.Feature.GENERAL
            )
        ) {
            return
        }

        ProfileStorage.saveSelectedProfile(requireContext(), profile.id)
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
        if (isNorthUpMode) {
            isNorthUpMode = false
            updateCameraModeUi()
        }
        enforcePitchZero = false
        mapStateViewModel.hasInitializedCamera = true
        resetNormalDrivingCameraState()
        startNavSessionTracking()
    }

    private fun setNormalSessionUiActive(active: Boolean) {
        hideArrivalActionPanel(animated = false)
        attachIdleAverageSpeedSignToPreview(false)
        navSessionContainer?.visibility = if (active) View.VISIBLE else View.GONE
        carModeContainer?.visibility = if (active) View.VISIBLE else View.GONE
        bottomHudRow?.visibility = if (active) View.VISIBLE else View.GONE
        buttonContainer?.visibility = if (active) View.VISIBLE else View.GONE
        if (active) {
            buttonActionsRow?.visibility = View.VISIBLE
        }
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
        if (active) {
            hideIdleMapFollowChrome()
            navSessionContainer?.bringToFront()
            bottomHudRow?.bringToFront()
            buttonContainer?.bringToFront()
        } else {
            updateIdleMapFollowUi()
        }

        if (::llActiveProfileHeader.isInitialized) {
            llActiveProfileHeader.visibility = if (active) View.GONE else View.VISIBLE
        }

        updateVoiceGuidanceToggleUi()
        applyLandscapeBottomHudAnchor()
        repositionReportAlert()
        averageSpeedController?.setSessionHudActive(active)
        if (active) {
            repositionMapControlsForActiveSession()
        }
        repositionLandscapeAverageSpeedCluster()
    }

    /** Landscape HUD sits on the screen bottom in free ride; above the trip bar only when that bar is visible. */
    private fun applyLandscapeBottomHudAnchor() {
        val hud = bottomHudRow ?: return
        val params = hud.layoutParams as? android.widget.RelativeLayout.LayoutParams ?: return
        val isLandscape =
            resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (!isLandscape) return
        params.removeRule(android.widget.RelativeLayout.ABOVE)
        params.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
        if (tripProgressContainer?.visibility == View.VISIBLE) {
            params.addRule(android.widget.RelativeLayout.ABOVE, R.id.tripProgressContainer)
        } else {
            params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
        }
        hud.layoutParams = params
        repositionLandscapeAverageSpeedCluster()
    }

    private fun repositionLandscapeAverageSpeedCluster() {
        if (!isAdded || view == null) return
        val cluster = averageSpeedHudCluster ?: return
        if (resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            return
        }
        val params = cluster.layoutParams as? android.widget.RelativeLayout.LayoutParams ?: return
        val density = resources.displayMetrics.density
        params.removeRule(android.widget.RelativeLayout.START_OF)
        params.removeRule(android.widget.RelativeLayout.ALIGN_BOTTOM)
        params.removeRule(android.widget.RelativeLayout.ALIGN_END)
        params.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
        params.removeRule(android.widget.RelativeLayout.ABOVE)
        if (isNavigationActive) {
            params.addRule(android.widget.RelativeLayout.START_OF, R.id.fabReport)
            params.addRule(android.widget.RelativeLayout.ALIGN_BOTTOM, R.id.fabReport)
            params.marginEnd = (16 * density).toInt()
            params.bottomMargin = 0
        } else {
            params.addRule(android.widget.RelativeLayout.ALIGN_END, R.id.bottomHudRow)
            params.addRule(android.widget.RelativeLayout.ALIGN_BOTTOM, R.id.bottomHudRow)
            val buttons = buttonContainer
            val hudPad = resources.getDimensionPixelSize(R.dimen.hud_bottom_row_padding_end_landscape)
            val gap = (16 * density).toInt()
            val buttonsWidth = if (buttons?.visibility == View.VISIBLE) buttons.width else 0
            params.marginEnd = if (buttonsWidth > 0) buttonsWidth + hudPad + gap else gap
            params.bottomMargin = (8 * density).toInt()
            if (buttons?.visibility == View.VISIBLE && buttonsWidth == 0) {
                buttons.post {
                    if (isAdded) repositionLandscapeAverageSpeedCluster()
                }
            }
        }
        cluster.layoutParams = params
    }

    private fun resetNormalDrivingCameraState() {
        mapboxTargetPosition = null
        mapboxSmoothedTargetPosition = null
        puckIndicatorPosition = null
        mapboxCurrentPosition = null
        mapboxCurrentCameraCenter = null
        mapboxCurrentBearing = 0f
        mapboxLastUpdateTime = 0L
        targetMapOrientation = 0f
        currentMapOrientation = 0f
        lastCalculatedBearing = 0f
        lastProcessedLocation = null
        isFirstLocation = true
        val currentZoomValue = mapboxMapView?.mapboxMap?.cameraState?.zoom ?: 17.5
        val currentPitchValue = mapboxMapView?.mapboxMap?.cameraState?.pitch ?: getNormalDrivingPitch()
        targetZoom = currentZoomValue
        currentZoom = currentZoomValue
        targetPitch = if (isNorthUpMode) 0.0 else currentPitchValue
        currentPitch = targetPitch
        suppressMapCameraUpdatesUntil = 0L
        startupCameraHandoffUntil = 0L
        startupFollowStabilizeUntil = 0L
        startupIntroUntil = 0L
    }

    private fun beginLiveSessionCameraIntro(durationMs: Long) {
        mapboxMapView?.camera?.cancelAllAnimators()
        suppressMapCameraUpdatesUntil = 0L
        startupCameraHandoffUntil = 0L
        startupFollowStabilizeUntil = 0L
        startupIntroUntil = SystemClock.elapsedRealtime() + durationMs
    }

    private fun isLandscapeFreeRideCamera(): Boolean {
        return navSessionActive &&
            !isNavigationActive &&
            resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    }

    private fun getNormalDrivingPitch(): Double {
        if (isNorthUpMode) return 0.0
        return if (isLandscapeFreeRideCamera()) NORMAL_PITCH_AT_REST_LANDSCAPE else NORMAL_PITCH_AT_REST
    }

    private fun cameraSmoothstep(t: Double): Double {
        val x = t.coerceIn(0.0, 1.0)
        return x * x * (3.0 - 2.0 * x)
    }

    private fun cameraLerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t.coerceIn(0.0, 1.0)

    private fun speedNorm01(speedKmh: Float, minSpeed: Float, maxSpeed: Float): Double {
        if (maxSpeed <= minSpeed) return 0.0
        return ((speedKmh - minSpeed) / (maxSpeed - minSpeed)).toDouble().coerceIn(0.0, 1.0)
    }

    private fun shortestBearingDelta(from: Float, to: Float): Float {
        var d = to - from
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        return d
    }

    private fun blendBearing(from: Float, to: Float, towardTo: Float): Float {
        val t = towardTo.coerceIn(0f, 1f)
        var result = from + shortestBearingDelta(from, to) * t
        if (result < 0f) result += 360f
        if (result >= 360f) result -= 360f
        return result
    }

    /** Continuous zoom curve: close at rest, wider at highway speed. */
    private fun getNormalDrivingZoomForSpeed(speed: Float): Double {
        val t = cameraSmoothstep(speedNorm01(speed, 0f, 160f))
        return cameraLerp(NORMAL_ZOOM_AT_REST, NORMAL_ZOOM_AT_SPEED, t)
    }

    /** Continuous pitch curve: sport framing without extreme cinematic tilt. */
    private fun getNormalDrivingPitchForSpeed(speed: Float): Double {
        if (isNorthUpMode) return 0.0
        val rest = if (isLandscapeFreeRideCamera()) NORMAL_PITCH_AT_REST_LANDSCAPE else NORMAL_PITCH_AT_REST
        val atSpeed = if (isLandscapeFreeRideCamera()) NORMAL_PITCH_AT_SPEED_LANDSCAPE else NORMAL_PITCH_AT_SPEED
        val t = cameraSmoothstep(speedNorm01(speed, 15f, 140f))
        return cameraLerp(rest, atSpeed, t)
    }

    /**
     * Puts the camera focal point (the puck) at a fixed screen fraction from the bottom.
     * Top padding only: leftover area is centered horizontally, so every display matches.
     */
    private fun getFreeRideFollowPadding(): EdgeInsets {
        if (isNorthUpMode) return EdgeInsets(0.0, 0.0, 0.0, 0.0)
        val mapHeight = mapboxMapView?.height?.takeIf { it > 50 }?.toDouble()
            ?: resources.displayMetrics.heightPixels.toDouble()
        val fromBottom = if (resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        ) {
            LIVE_FOLLOW_PUCK_FROM_BOTTOM_LANDSCAPE
        } else {
            LIVE_FOLLOW_PUCK_FROM_BOTTOM_PORTRAIT
        }
        val top = (mapHeight * (1.0 - 2.0 * fromBottom)).coerceAtLeast(0.0)
        return EdgeInsets(top, 0.0, 0.0, 0.0)
    }

    private fun processNormalDrivingLocation(location: Location) {
        val geoPoint = GeoPoint(location.latitude, location.longitude)

        val speedKmh = location.speed * 3.6f
        lastSpeedKmh = speedKmh
        val inStartupHandoff = SystemClock.elapsedRealtime() < startupCameraHandoffUntil

        mapboxSmoothedTargetPosition = geoPoint

        if (isFirstLocation) {
            initializeFirstNormalLocation(location)
            return
        }

        // Keep travel heading even in north-up so switching back can face the road.
        if (!inStartupHandoff && speedKmh > NORMAL_BEARING_HOLD_SPEED_KMH) {
            var calculatedBearing = if (location.hasBearing()) location.bearing else lastCalculatedBearing

            if (lastProcessedLocation != null) {
                val lastGeoPoint = GeoPoint(lastProcessedLocation!!.latitude, lastProcessedLocation!!.longitude)
                val distance = geoPoint.distanceToAsDouble(lastGeoPoint)

                if (distance > 0.35) {
                    val lat1 = Math.toRadians(lastGeoPoint.latitude)
                    val lat2 = Math.toRadians(geoPoint.latitude)
                    val deltaLon = Math.toRadians(geoPoint.longitude - lastGeoPoint.longitude)

                    val x = sin(deltaLon) * cos(lat2)
                    val y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)

                    var movementBearing = Math.toDegrees(atan2(x, y)).toFloat()
                    if (movementBearing < 0) movementBearing += 360f

                    val gpsBearing = if (location.hasBearing()) location.bearing else movementBearing
                    // Prefer GPS heading at speed; lean on movement vector when crawling.
                    val movementWeight = when {
                        speedKmh > 50f -> 0.22f
                        speedKmh > 20f -> 0.40f
                        else -> 0.55f
                    }
                    calculatedBearing = blendBearing(gpsBearing, movementBearing, movementWeight)
                }
            }

            val deltaFromLast = abs(shortestBearingDelta(lastCalculatedBearing, calculatedBearing))
            val applyDeadband = speedKmh < 90f && deltaFromLast < NORMAL_BEARING_DEADBAND_DEG
            if (!applyDeadband) {
                mapboxTargetBearing = calculatedBearing
                lastCalculatedBearing = calculatedBearing
                if (!isNorthUpMode) {
                    targetMapOrientation = -calculatedBearing
                }
            }
        }
        if (isNorthUpMode) {
            targetMapOrientation = 0f
        }

        lastProcessedLocation = location
        mapboxTargetPosition = mapboxSmoothedTargetPosition

        if (!inStartupHandoff) {
            updateZoomBasedOnSpeed(speedKmh)
            updatePitchBasedOnSpeed(speedKmh)
        } else {
            val cameraState = mapboxMapView?.mapboxMap?.cameraState
            if (cameraState != null) {
                currentZoom = cameraState.zoom
                targetZoom = cameraState.zoom
                currentPitch = cameraState.pitch
                targetPitch = cameraState.pitch
                if (!isNorthUpMode) {
                    val camBearing = cameraState.bearing.toFloat()
                    currentMapOrientation = -camBearing
                    targetMapOrientation = currentMapOrientation
                    mapboxTargetBearing = camBearing
                }
            }
        }
    }

    private fun initializeFirstNormalLocation(location: Location) {
        if (!isFirstLocation) return
        if (isFollowLineActive) {
            snapFollowCameraToPuckInstant()
            return
        }
        val mapView = mapboxMapView ?: return
        val geoPoint = GeoPoint(location.latitude, location.longitude)
        val cameraState = mapView.mapboxMap.cameraState

        val startupZoom = NORMAL_STARTUP_CAMERA_ZOOM
        val startupPitch = getNormalDrivingPitchForSpeed(lastSpeedKmh)
        val bearing = if (location.hasBearing()) location.bearing else cameraState.bearing.toFloat()

        val zoomDelta = abs(cameraState.zoom - startupZoom)
        val durationMs = (1500L + (zoomDelta * 200.0).toLong()).coerceIn(1600L, 2200L)

        mapboxCurrentPosition = geoPoint
        mapboxCurrentCameraCenter = GeoPoint(cameraState.center.latitude(), cameraState.center.longitude())
        mapboxTargetPosition = geoPoint
        mapboxSmoothedTargetPosition = geoPoint
        mapboxCurrentBearing = cameraState.bearing.toFloat()
        mapboxTargetBearing = bearing
        lastCalculatedBearing = bearing
        lastProcessedLocation = location
        currentZoom = cameraState.zoom
        targetZoom = startupZoom
        currentPitch = cameraState.pitch
        targetPitch = startupPitch
        targetMapOrientation = -bearing
        currentMapOrientation = -cameraState.bearing.toFloat()
        mapboxLastUpdateTime = SystemClock.elapsedRealtime()
        isFirstLocation = false
        beginLiveSessionCameraIntro(durationMs)
    }

    private fun updateZoomBasedOnSpeed(speed: Float) {
        targetZoom = getNormalDrivingZoomForSpeed(speed)
    }

    private fun updatePitchBasedOnSpeed(speed: Float) {
        targetPitch = getNormalDrivingPitchForSpeed(speed)
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
        val targetPos = puckIndicatorPosition ?: mapboxTargetPosition ?: return

        val nowRealtime = SystemClock.elapsedRealtime()
        if (nowRealtime < suppressMapCameraUpdatesUntil) {
            mapboxCurrentPosition = targetPos
            mapboxCurrentBearing = mapboxTargetBearing
            mapboxLastUpdateTime = nowRealtime
            val cameraZoom = mapView.mapboxMap.cameraState.zoom
            currentZoom = cameraZoom
            targetZoom = cameraZoom
            currentMapOrientation = targetMapOrientation
            return
        }

        if (nowRealtime < startupFollowStabilizeUntil) {
            // Keep the easeTo frame; only sync internal follow state so live loop has no jump.
            val cameraState = mapView.mapboxMap.cameraState
            val center = cameraState.center
            mapboxCurrentPosition = targetPos
            mapboxCurrentCameraCenter = GeoPoint(center.latitude(), center.longitude())
            mapboxCurrentBearing = mapboxTargetBearing
            mapboxLastUpdateTime = nowRealtime
            currentZoom = cameraState.zoom
            targetZoom = cameraState.zoom
            currentPitch = cameraState.pitch
            targetPitch = cameraState.pitch
            if (!isNorthUpMode) {
                currentMapOrientation = -cameraState.bearing.toFloat()
                targetMapOrientation = currentMapOrientation
            }
            return
        }

        val previousUpdateTime = mapboxLastUpdateTime
        mapboxCurrentBearing = mapboxTargetBearing

        if (mapboxCurrentCameraCenter == null) {
            mapboxCurrentCameraCenter = targetPos
        }

        val deltaMs = if (previousUpdateTime > 0L) {
            (nowRealtime - previousUpdateTime).coerceIn(16L, 120L)
        } else {
            33L
        }
        val deltaSec = deltaMs / 1000f
        val speed = lastSpeedKmh

        val inIntro = nowRealtime < startupIntroUntil
        val bearingTau = when {
            inIntro -> 0.40f
            speed > 50f -> 0.14f
            speed > 20f -> 0.22f
            else -> 0.32f
        }
        val zoomTau = if (inIntro) 0.95f else 1.10f
        val pitchTau = if (inIntro) 1.05f else 1.20f

        val bearingAlpha = 1f - kotlin.math.exp(-deltaSec / bearingTau)
        val zoomAlpha = 1f - kotlin.math.exp(-deltaSec / zoomTau)
        val pitchAlpha = 1f - kotlin.math.exp(-deltaSec / pitchTau)

        val smoothAnchor = targetPos
        mapboxCurrentPosition = smoothAnchor

        if (isNorthUpMode) {
            currentMapOrientation = 0f
        } else {
            val orientationDiff = shortestBearingDelta(currentMapOrientation, targetMapOrientation)
            currentMapOrientation += orientationDiff * bearingAlpha
        }

        currentZoom += (targetZoom - currentZoom) * zoomAlpha
        currentPitch += (targetPitch - currentPitch) * pitchAlpha

        val desiredCenter = smoothAnchor
        mapboxCurrentCameraCenter = desiredCenter
        mapboxLastUpdateTime = nowRealtime

        mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(MapboxPoint.fromLngLat(desiredCenter.longitude, desiredCenter.latitude))
                .bearing(if (isNorthUpMode) 0.0 else (-currentMapOrientation).toDouble())
                .zoom(currentZoom)
                .pitch(if (isNorthUpMode) 0.0 else currentPitch)
                .padding(getFreeRideFollowPadding())
                .build()
        )
    }
    
    private fun navigateToSessions() {
        // Навигираме към RACES страницата в MainContainerActivity
        val activity = requireActivity()
        if (activity is MainContainerActivity) {
            activity.navigateToPage(MainContainerActivity.PAGE_RACES)
        } else {
            // Fallback ако не сме в MainContainerActivity
            val intent = Intent(requireContext(), MainContainerActivity::class.java).apply {
                putExtra(MainContainerActivity.EXTRA_INITIAL_PAGE, MainContainerActivity.PAGE_RACES)
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
        if (!isNavigationActive) {
            stopNonNavigationLocationUpdates()
        }
    }

    override fun onResume() {
        super.onResume()
        if (com.revix.app.BuildConfig.DEBUG) {
            RaceBoxManager.ensureInitialized(requireContext())
            RaceBoxManager.addSampleListener(raceBoxMapSampleListener)
        }
        if (!isNavigationActive) {
            startNonNavigationLocationUpdates()
        }
        updateVoiceGuidanceToggleUi()
        reportsIntegration?.showPendingConfirmations()
        loadCachedWeatherData()
        updateEnvironmentDisplay()
        loadProfileInfo()
        bindGarageReminderBanner()
        updateZeroButtonVisibility()

        // Returning to the app (e.g. after unlocking): immediately reflect the distance the
        // background service accumulated while we were away, so it never appears to reset.
        if (navSessionActive) {
            val serviceDistanceMeters = foregroundService?.getTotalDistanceMeters()?.coerceAtLeast(0.0)
            if (serviceDistanceMeters != null) {
                navSessionDistanceMeters = maxOf(navSessionDistanceMeters, serviceDistanceMeters)
            }
            updateDistanceCarDisplay()
        }
        
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
            updateDistanceCarDisplay()
            startMapboxRenderLoop()
            updateZeroButtonVisibility()
            updateLeanAngleVisibility()
            startLeanAngleUpdates()
            if (shouldRestoreFollowLineAfterRecreate) {
                restoreFollowLineAfterRecreate()
            }
        } else if (shouldRestoreNavigationAfterRecreate) {
            shouldRestoreNavigationAfterRecreate = false
            if (!com.revix.app.billing.ProAccess.hasFullAccess(requireContext())) {
                // Lost Pro / not entitled — do not resume trip-session navigation.
                mapboxNavigation.setNavigationRoutes(emptyList())
                if (isNavigationActive) {
                    setNavigationActive(false)
                    resetNavigationUiAfterStop()
                }
            } else if (mapboxNavigation.getNavigationRoutes().isNotEmpty()) {
                setNavigationActive(true, preserveSessionState = true)
                // setNavigationActive already restores live nearby reports.
                if (this::navigationCamera.isInitialized) {
                    mapboxMapView?.post {
                        snapNavigationCameraToFollowingInstant()
                    }
                }
            }
        } else if (isNavigationActive && mapboxNavigation.getNavigationRoutes().isEmpty()) {
            setNavigationActive(false)
            resetNavigationUiAfterStop()
        }

        if (!isNavigationActive &&
            currentDestination == null &&
            !isFollowLineActive &&
            !shouldRestoreFollowLineAfterRecreate
        ) {
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
        requestFabReportReposition()

        // If the screen was locked while a normal session was active, onStop() has already stopped
        // the render loop. Force a one-shot camera resync and restart follow rendering.
        if (navSessionActive && !isNavigationActive) {
            suppressMapCameraUpdatesUntil = 0L
            startupCameraHandoffUntil = 0L
            startupFollowStabilizeUntil = 0L
            mapboxLastUpdateTime = 0L

            val resumeLocation = currentRawLocation
                ?: currentLocation
                ?: mapStateViewModel.lastKnownLocation
            if (resumeLocation != null) {
                processNormalDrivingLocation(resumeLocation)
                updateMapboxMapAnimation()
            } else {
                displayLastKnownLocationInstantly()
            }

            startMapboxRenderLoop()
        }

        triggerFirstOpenWeatherFetchIfNeeded()
        startWeatherRefreshTimer()

        if (pendingRoutePreviewRestore) {
            restoreRoutePreviewIfNeeded()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        lastKnownLayoutOrientation = newConfig.orientation
        requestFabReportReposition()
        applyLandscapeBottomHudAnchor()
        if (isFollowLineActive) {
            hideRoutePreviewChrome()
        }
        snapGuidanceCameraAfterOrientationChange()
    }

    /**
     * Portrait ↔ landscape while already following the puck: jump, do not fly.
     * Start-nav / start-follow-line / recenter keep their normal animations.
     */
    private fun snapGuidanceCameraAfterOrientationChange() {
        if (!isAdded) return
        val mapView = mapboxMapView ?: return
        mapView.post {
            if (!isAdded) return@post
            refreshNavigationViewportPadding()
            when {
                isNavigationActive -> snapNavigationCameraToFollowingInstant()
                isFollowLineActive -> snapFollowCameraToPuckInstant()
                navSessionActive -> snapFreeRideCameraToCurrentTarget()
            }
        }
    }

    private fun snapNavigationCameraToFollowingInstant() {
        if (!isAdded) return
        if (!this::navigationCamera.isInitialized || !this::viewportDataSource.isInitialized) return
        refreshNavigationViewportPadding()
        viewportDataSource.evaluate()
        mapboxMapView?.camera?.cancelAllAnimators()
        val instant = NavigationCameraTransitionOptions.Builder().maxDuration(0L).build()
        navigationCamera.requestNavigationCameraToFollowing(
            stateTransitionOptions = instant
        )
    }

    private fun seedGuidanceCameraFromLastKnownLocation() {
        val map = mapboxMapView?.mapboxMap ?: return
        val loc = currentRawLocation
            ?: currentLocation
            ?: mapStateViewModel.lastKnownLocation
            ?: return
        val speedKmh = (loc.speed * 3.6f).coerceAtLeast(0f)
        val bearing = when {
            loc.hasBearing() -> loc.bearing.toDouble()
            else -> map.cameraState.bearing
        }
        map.setCamera(
            CameraOptions.Builder()
                .center(MapboxPoint.fromLngLat(loc.longitude, loc.latitude))
                .zoom(getNormalDrivingZoomForSpeed(speedKmh))
                .bearing(bearing)
                .pitch(if (isNorthUpMode) 0.0 else getNormalDrivingPitchForSpeed(speedKmh))
                .build()
        )
        mapStateViewModel.hasInitializedCamera = true
    }

    private fun resolveLiveFollowBearing(location: Location?): Float {
        val loc = location
            ?: currentRawLocation
            ?: currentLocation
            ?: mapStateViewModel.lastKnownLocation
        if (loc != null && loc.hasBearing() && loc.speed >= 1f) {
            return loc.bearing
        }
        if (lastProcessedLocation != null) {
            return lastCalculatedBearing
        }
        if (loc != null && loc.hasBearing()) {
            return loc.bearing
        }
        return mapboxMapView?.mapboxMap?.cameraState?.bearing?.toFloat() ?: 0f
    }

    private fun snapFollowCameraToPuckInstant() {
        val mapView = mapboxMapView ?: return
        val location = currentRawLocation
            ?: currentLocation
            ?: mapStateViewModel.lastKnownLocation
            ?: return
        val speedKmh = (location.speed * 3.6f).coerceAtLeast(0f)
        lastSpeedKmh = speedKmh
        val travelBearing = resolveLiveFollowBearing(location)
        val bearing = if (isNorthUpMode) 0f else travelBearing
        val zoom = getNormalDrivingZoomForSpeed(speedKmh)
        val pitch = getNormalDrivingPitchForSpeed(speedKmh)
        val anchor = GeoPoint(location.latitude, location.longitude)
        val center = anchor
        mapboxTargetPosition = anchor
        mapboxSmoothedTargetPosition = anchor
        mapboxCurrentPosition = anchor
        mapboxCurrentCameraCenter = center
        mapboxTargetBearing = travelBearing
        mapboxCurrentBearing = bearing
        lastCalculatedBearing = travelBearing
        lastProcessedLocation = location
        currentZoom = zoom
        targetZoom = zoom
        currentPitch = pitch
        targetPitch = pitch
        targetMapOrientation = if (isNorthUpMode) 0f else -bearing
        currentMapOrientation = targetMapOrientation
        mapboxLastUpdateTime = SystemClock.elapsedRealtime()
        isFirstLocation = false
        startupIntroUntil = 0L
        suppressMapCameraUpdatesUntil = 0L
        startupCameraHandoffUntil = 0L
        startupFollowStabilizeUntil = 0L
        mapView.camera.cancelAllAnimators()
        mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(MapboxPoint.fromLngLat(center.longitude, center.latitude))
                .bearing(if (isNorthUpMode) 0.0 else bearing.toDouble())
                .zoom(zoom)
                .pitch(if (isNorthUpMode) 0.0 else pitch)
                .padding(getFreeRideFollowPadding())
                .build()
        )
    }

    private fun snapFreeRideCameraToCurrentTarget() {
        updatePitchBasedOnSpeed(lastSpeedKmh)
        updateZoomBasedOnSpeed(lastSpeedKmh)
        currentPitch = targetPitch
        currentZoom = targetZoom
        mapboxCurrentCameraCenter = null
        updateMapboxMapAnimation()
    }
    
    /**
     * Restores idle camera if we are still on the default globe / missing location seed.
     * Does not yank zoom when the camera is already at a sensible local level.
     */
    private fun displayLastKnownLocationInstantly() {
        if (isNavigationActive || navSessionActive || currentDestination != null) return
        val zoom = mapboxMapView?.mapboxMap?.cameraState?.zoom ?: 0.0
        if (mapStateViewModel.hasInitializedCamera && zoom >= 10.0) return
        applyIdleStartupCamera(force = true)
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
        
        // Persist last location + camera so next cold start / map recreate skips the globe.
        currentLocation?.let { location ->
            mapStateViewModel.saveLastLocation(location)
        }
        currentRawLocation?.let { location ->
            mapStateViewModel.saveLastLocation(location)
        }
        
        if (mapboxMapView != null && !isNavigationActive) {
            mapboxMapView?.mapboxMap?.cameraState?.let { cameraState ->
                // Avoid persisting accidental globe / ultra-zoomed-out frames.
                if (cameraState.zoom >= 10.0) {
                    mapStateViewModel.saveMapState(
                        cameraState.center.latitude(),
                        cameraState.center.longitude(),
                        cameraState.zoom,
                        cameraState.pitch
                    )
                }
            }
        }
        mapStateViewModel.persist(requireContext())
        
        disableMapboxLocationComponent()
        
        // Mapbox cleanup
    }
    
    override fun onDestroyView() {
        mapRootObservedView?.let { observedView ->
            mapRootLayoutChangeListener?.let { listener ->
                observedView.removeOnLayoutChangeListener(listener)
            }
            observedView.removeCallbacks(fabRepositionRunnable)
        }
        mapRootLayoutChangeListener = null
        mapRootObservedView = null
        idleMapFollowMoveListener?.let { listener ->
            mapboxMapView?.gestures?.removeOnMoveListener(listener)
        }
        idleMapFollowScaleListener?.let { listener ->
            mapboxMapView?.gestures?.removeOnScaleListener(listener)
        }
        idleMapFollowMoveListener = null
        idleMapFollowScaleListener = null
        if (this::navigationCamera.isInitialized) {
            navigationCamera.unregisterNavigationCameraStateChangeObserver(navigationCameraStateObserver)
        }
        resetIdleMapFollowState()
        if (com.revix.app.BuildConfig.DEBUG) {
            RaceBoxManager.removeSampleListener(raceBoxMapSampleListener)
        }
        super.onDestroyView()
        // КРИТИЧНО: НЕ унищожаваме MapView тук - той се запазва в паметта за instant navigation
        // MapView ще се унищожи само когато Fragment се унищожи напълно
        cancelInlineSearchDebounce()
        stopLeanAngleUpdates()
        stopMapboxRenderLoop()
        stopWeatherRefreshTimer()
        routeWeatherPreviewOverlay?.clear()
        reportsIntegration?.cleanup()
        reportsIntegration = null
        averageSpeedController?.release()
        averageSpeedController = null
        lastReportsQueryLocation = null // Reset для следващ път
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (!NavigationVoiceGuidance.isRunning()) {
            NavigationVoiceGuidance.shutdown()
        }
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
            idleAverageSpeedSignSlot?.bringToFront()
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
        idleAverageSpeedSignSlot?.bringToFront()
        updateAltitudeExpandedText()
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
        val rainTimePrefix = resolveRainTimePrefix()
        val summaryText = if (rainTimeText.isNotEmpty() && rainTimePrefix.isNotEmpty()) {
            "${icon}${rainChance3h}% $rainTimePrefix $rainTimeText"
        } else {
            "${icon}${rainChance3h}%"
        }
        val windText = UnitsManager.formatStoredSpeed(currentWindKph.toString(), requireContext())
        val expandedText = "💨$windText 💧${currentHumidity}% | $summaryText"
        llWeatherExpanded.text = expandedText
    }

    private fun resolveRainTimePrefix(): String {
        return when (rainTimePrefixKind) {
            RainTimePrefixKind.UNTIL -> getString(R.string.weather_until)
            RainTimePrefixKind.FROM -> getString(R.string.weather_from)
            RainTimePrefixKind.NONE -> ""
        }
    }

    private fun loadRainTimePrefixKind(
        prefs: android.content.SharedPreferences,
        cachedSummaryIsRain: Boolean
    ): RainTimePrefixKind {
        if (prefs.contains(CACHED_RAIN_PREFIX_KIND)) {
            val ordinal = prefs.getInt(CACHED_RAIN_PREFIX_KIND, RainTimePrefixKind.NONE.ordinal)
            return RainTimePrefixKind.entries.getOrElse(ordinal) { RainTimePrefixKind.NONE }
        }

        val legacyPrefix = prefs.getString("cached_rain_prefix", "") ?: ""
        return when {
            legacyPrefix.isBlank() -> RainTimePrefixKind.NONE
            cachedSummaryIsRain -> RainTimePrefixKind.UNTIL
            else -> RainTimePrefixKind.FROM
        }
    }

    private enum class RainTimePrefixKind {
        NONE,
        UNTIL,
        FROM
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
        llAltitudeExpanded.text = if (currentPressure > 0.0) {
            getString(R.string.altitude_pressure_value, currentPressure.toInt())
        } else {
            getString(R.string.altitude_pressure_placeholder)
        }
    }

    private fun resolveDisplayWeatherIcon(): Int {
        val tempC = currentTemperature ?: return currentWeatherIcon
        return WeatherIconMapper.adjustIconForTemperature(
            iconRes = currentWeatherIcon,
            tempC = tempC,
            cloudCover = currentCloudCover,
            isDay = currentIsDay
        )
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
        if (isFollowLineActive || shouldRestoreFollowLineAfterRecreate || navSessionActive) {
            pendingRoutePreviewRestore = false
            hideRoutePreviewChrome()
            return
        }
        val dest = currentDestination ?: return
        if (!this::routeLineApi.isInitialized || !this::routeLineView.isInitialized) {
            mapboxMapView?.post { restoreRoutePreviewIfNeeded() }
            return
        }
        pendingRoutePreviewRestore = false
        setDestinationAndFindRoute(dest, currentDestinationName)
    }

    fun handleBackPressedFromActivity(): Boolean {
        if (isArrivalActionVisible) {
            showArrivalDiscardSessionDialog()
            return true
        }
        if (navSessionActive) {
            showExitNormalSessionDialog()
            return true
        }
        if (isNavigationActive) {
            showExitNavigationDialog()
            return true
        }
        if (isPoiCategoryModeActive || isCategorySearchOverlayActive || poiBottomSheetContainer?.visibility == View.VISIBLE) {
            exitPOICategoryModeToSearch()
            return true
        }
        if (searchContainer.visibility == View.VISIBLE) {
            restoreInitialMapUi()
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
        isIdleMapFollowEnabled = true
        currentLocation?.let { location ->
            mapStateViewModel.hasInitializedCamera = true
            idleFollowSmoothPosition = null
            feedIdleMapFollowTarget(location)
        } ?: run {
            updateIdleMapFollowUi()
            retrieveCurrentLocation()
        }
    }
    
    private fun retrieveCurrentLocation() {
        // Mapbox Location Component handles location updates
        tryEnableMapboxLocationComponent()
    }
    
    private fun checkLocationPermission(): Boolean {
        return LocationPermissionHelper.hasAll(requireContext())
    }

    private fun requestLocationPermission() {
        LocationPermissionHelper.ensure(requireActivity(), LOCATION_PERMISSION_REQUEST_CODE)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (LocationPermissionHelper.hasAll(requireContext())) {
                retrieveCurrentLocation()
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.map_location_permission_required),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
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
        ivWeatherIcon.setImageResource(resolveDisplayWeatherIcon())
        updateWeatherExpandedText()
        updateAltitudeExpandedText()

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
        val cachedSummaryIsRain = prefs.getBoolean("cached_summary_is_rain", true)
        
        if (!cachedTemp.isNaN() && !cachedLat.isNaN() && !cachedLon.isNaN()) {
            currentTemperature = cachedTemp
            if (cachedIcon != -1 && isValidWeatherIcon(cachedIcon)) {
                currentWeatherIcon = cachedIcon
            }
        }
        
        currentIsDay = prefs.getBoolean("cached_is_day", isCurrentlyDay())
        if (currentTemperature != null) {
            currentWeatherIcon = WeatherIconMapper.adjustIconForTemperature(
                iconRes = currentWeatherIcon,
                tempC = currentTemperature!!,
                cloudCover = currentCloudCover,
                isDay = currentIsDay
            )
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
        rainTimePrefixKind = loadRainTimePrefixKind(prefs, cachedSummaryIsRain)
        summaryIsRain = cachedSummaryIsRain
        updateWeatherExpandedText()
        updateAltitudeExpandedText()
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
        editor.putBoolean("cached_is_day", currentIsDay)
        editor.putFloat("cached_wind_kph", currentWindKph.toFloat())
        editor.putInt("cached_humidity", currentHumidity)
        editor.putFloat("cached_pressure", currentPressure.toFloat())
        editor.putInt("cached_rain_chance", rainChance3h)
        editor.putString("cached_rain_time", rainTimeText)
        editor.putInt(CACHED_RAIN_PREFIX_KIND, rainTimePrefixKind.ordinal)
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
        val cachedTemp = prefs.getFloat("cached_temperature", Float.NaN)
        val cachedPressure = prefs.getFloat("cached_pressure", Float.NaN)
        val now = System.currentTimeMillis()
        if (cachedTime == 0L || now - cachedTime > CACHE_WEATHER_MAX_AGE_MS) {
            return true
        }

        if (cachedPressure.isNaN() || cachedPressure <= 0f) {
            return true
        }

        if (!cachedTemp.isNaN() &&
            cachedIcon == R.drawable.ic_weather_snowy &&
            cachedTemp > 2f
        ) {
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

    private fun isCurrentlyDay(): Boolean {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return hour in 6..19
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
                    currentIsDay = isDay
                    currentWeatherIcon = WeatherIconMapper.adjustIconForTemperature(
                        iconRes = WeatherIconMapper.getWeatherApiIcon(condition.code, cloudCover, isDay),
                        tempC = weather.current.temp_c.toFloat(),
                        cloudCover = cloudCover,
                        isDay = isDay
                    )
                    
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
                        rainTimePrefixKind = RainTimePrefixKind.NONE
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
                                    rainTimePrefixKind = RainTimePrefixKind.UNTIL
                                    summaryIsRain = true
                                }
                            } else {
                                val nextRainEntry = hourEntries.firstOrNull {
                                    it.timeMillis >= nowMillis && isRainHour(it.hour, rainThreshold)
                                }
                                if (nextRainEntry != null) {
                                    rainChance3h = nextRainEntry.hour.chance_of_rain
                                    rainTimeText = formatHourMillis(nextRainEntry.timeMillis)
                                    rainTimePrefixKind = RainTimePrefixKind.FROM
                                    summaryIsRain = true
                                } else {
                                    val futureEntries = hourEntries.filter { it.timeMillis >= nowMillis }
                                    val maxRainChance = futureEntries.maxOfOrNull { it.hour.chance_of_rain } ?: 0
                                    val minRainEntry = futureEntries.minByOrNull { it.hour.chance_of_rain }
                                    val sunChance = (100 - maxRainChance).coerceIn(0, 100)
                                    val timeMillis = minRainEntry?.timeMillis ?: nowMillis
                                    rainChance3h = sunChance
                                    rainTimeText = formatHourMillis(timeMillis)
                                    rainTimePrefixKind = RainTimePrefixKind.FROM
                                    summaryIsRain = false
                                }
                            }
                        }
                    } ?: run {
                        rainChance3h = 0
                        rainTimeText = ""
                        rainTimePrefixKind = RainTimePrefixKind.NONE
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
                    if (!isNavigationActive && !navSessionActive) {
                        val params = controls.layoutParams as? android.widget.RelativeLayout.LayoutParams
                        params?.topMargin = resources.getDimensionPixelSize(R.dimen.map_controls_margin_top_landscape)
                        controls.layoutParams = params
                    }
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
                        
                        if (::idleMapLeftChrome.isInitialized) {
                            val chromeParams = idleMapLeftChrome.layoutParams as? android.widget.RelativeLayout.LayoutParams
                            val temperaturePillHeight = it.height
                            val fabMarginBottom = pillsMarginBottom + temperaturePillHeight + (10 * resources.displayMetrics.density).toInt()
                            chromeParams?.marginStart = equalPadding + pillsExtraMargin
                            chromeParams?.bottomMargin = fabMarginBottom
                            idleMapLeftChrome.layoutParams = chromeParams
                        }
                    }

                }
            }
            
            insets
        }
    }

    private fun bindGarageReminderBanner() {
        val banner = view?.findViewById<View>(R.id.garageReminderBanner) ?: return
        val titleView = view?.findViewById<TextView>(R.id.tvGarageReminderBannerTitle) ?: return
        val moreView = view?.findViewById<TextView>(R.id.tvGarageReminderBannerMore) ?: return
        if (isNavigationActive || isFollowLineActive || currentDestination != null) {
            hideGarageReminderBanner(banner)
            return
        }
        val context = context ?: return
        GarageReminderInbox.refreshAll(context)
        val items = GarageReminderInbox.visibleHomeItems(context)
        if (items.isEmpty()) {
            hideGarageReminderBanner(banner)
            return
        }
        val top = items.first()
        val phaseLabel = getString(
            when (top.phase) {
                com.revix.app.garage.GarageReminderSchedule.HomePhase.OVERDUE ->
                    R.string.garage_home_reminder_chip_overdue
                com.revix.app.garage.GarageReminderSchedule.HomePhase.DUE_TODAY ->
                    R.string.garage_home_reminder_chip_today
                else -> R.string.garage_home_reminder_chip_approaching
            }
        )
        view?.findViewById<ImageView>(R.id.ivGarageReminderBannerIcon)?.apply {
            setImageResource(top.iconRes)
            setColorFilter(android.graphics.Color.parseColor("#FF6020"))
        }
        titleView.text = top.title
        titleView.setTextColor(
            when (top.phase) {
                com.revix.app.garage.GarageReminderSchedule.HomePhase.OVERDUE ->
                    android.graphics.Color.parseColor("#FF8A80")
                com.revix.app.garage.GarageReminderSchedule.HomePhase.DUE_TODAY ->
                    android.graphics.Color.parseColor("#FFD0C2")
                else -> android.graphics.Color.WHITE
            }
        )
        moreView.visibility = View.VISIBLE
        moreView.text = if (items.size > 1) {
            "$phaseLabel · ${getString(R.string.garage_home_reminder_more, items.size - 1)}"
        } else {
            phaseLabel
        }
        banner.setOnClickListener {
            startActivity(top.openIntent(context))
        }
        view?.findViewById<View>(R.id.btnGarageReminderBannerClose)?.setOnClickListener { closeButton ->
            closeButton.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            GarageReminderInbox.dismissHomeItem(context, top)
            hideGarageReminderBanner(banner)
        }
        val key = "${top.entryId}:${top.phase}:${items.size}:${top.title}"
        showGarageReminderBanner(banner, key)
    }

    private fun showGarageReminderBanner(banner: View, key: String) {
        if (banner.visibility == View.VISIBLE && garageReminderBannerKey == key) {
            return
        }
        garageReminderBannerKey = key
        banner.animate().cancel()
        banner.bringToFront()
        if (banner.visibility == View.VISIBLE) {
            return
        }
        val slide = -16f * resources.displayMetrics.density
        banner.visibility = View.VISIBLE
        banner.alpha = 0f
        banner.translationY = slide
        banner.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(260)
            .start()
    }

    private fun hideGarageReminderBanner(banner: View) {
        garageReminderBannerKey = null
        banner.animate().cancel()
        if (banner.visibility != View.VISIBLE) {
            banner.alpha = 1f
            banner.translationY = 0f
            return
        }
        val slide = -12f * resources.displayMetrics.density
        banner.animate()
            .alpha(0f)
            .translationY(slide)
            .setDuration(160)
            .withEndAction {
                banner.visibility = View.GONE
                banner.alpha = 1f
                banner.translationY = 0f
            }
            .start()
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
            // Cast: Gson may leave name null despite Kotlin non-null type.
            val fullName = (activeProfile.name as String?).orEmpty().trim()
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
                val imagePath = activeProfile.imagePath.orEmpty()
                val imageFile = java.io.File(requireContext().getExternalFilesDir(null), imagePath)
                if (imageFile.exists()) {
                    val expectedProfileId = activeProfile.id
                    val expectedImagePath = imagePath
                    viewLifecycleOwner.lifecycleScope.launch {
                        val bitmap = withContext(Dispatchers.IO) {
                            android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
                        }
                        if (!isAdded || view == null) return@launch

                        val selectedProfileId = ProfileStorage.getSelectedProfileId(requireContext())
                        val selectedProfile = ProfileStorage.loadProfiles(requireContext())
                            .find { it.id == selectedProfileId }
                        if (selectedProfile?.id != expectedProfileId || selectedProfile.imagePath != expectedImagePath) {
                            return@launch
                        }

                        if (bitmap != null) {
                            ivHeaderProfileImage.setImageBitmap(bitmap)
                            ivHeaderProfileImage.scaleType = ImageView.ScaleType.CENTER_CROP
                            ivHeaderProfileImage.setPadding(0, 0, 0, 0)
                        } else {
                            showDefaultIcon(activeProfile.vehicleType)
                        }
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
