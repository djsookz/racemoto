package com.example.clinometer.main

import com.example.clinometer.*
import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.clinometer.settings.LanguageManager
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.*
import android.util.Log

import android.view.View
import android.content.pm.ActivityInfo
import android.widget.ImageButton
import android.widget.Button
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.clinometer.main.map.NavigationDestinationLayerBinder
import com.example.clinometer.main.map.MapInitialCameraApplier
import com.example.clinometer.main.map.NavigationRouteFallbackRenderer
import com.example.clinometer.main.map.NavigationRouteRenderComponentsFactory
import com.example.clinometer.main.map.NavigationMarkerImageFactory
import com.example.clinometer.main.map.SdkStyleSetup
import com.example.clinometer.main.map.MapStyleReadyHandler
import com.example.clinometer.main.map.MapViewContainerBinder
import com.example.clinometer.main.map.SaveSessionActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.ViewGroup
import com.mapbox.geojson.Point as MapboxPoint
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView as MapboxMapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.maps.extension.style.expressions.dsl.generated.literal
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addLayerBelow
import com.mapbox.maps.extension.style.layers.addLayerAbove
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection

import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.formatter.UnitType
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener

import com.example.clinometer.navigation.DirectionsStep
import androidx.cardview.widget.CardView
import kotlin.math.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.res.Resources
import android.widget.FrameLayout
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.main.location.KalmanLocationFilter
import com.example.clinometer.main.location.MapCameraCenterCalculator
import com.example.clinometer.main.location.MapMotionSmoother
import com.example.clinometer.main.location.MapOrientationSmoother
import com.example.clinometer.main.location.MapZoomSmoother
import com.example.clinometer.main.location.MotionPredictor
import com.example.clinometer.main.location.LocationUpdateCoordinator
import com.example.clinometer.main.location.SensorMath
import com.example.clinometer.main.navigation.ManeuverDisplayPresenter
import com.example.clinometer.main.navigation.GeoBearing
import com.example.clinometer.main.navigation.NavigationStepSelector
import com.example.clinometer.main.navigation.RouteMath
import com.example.clinometer.main.navigation.TripProgressFormatter
import com.example.clinometer.main.session.DistanceTracker
import com.example.clinometer.main.session.MainServiceCoordinator
import com.example.clinometer.main.session.NavigationServiceLauncher
import com.example.clinometer.main.session.SessionRecorder
import com.example.clinometer.main.startup.NavigationLaunchDataParser
import com.example.clinometer.main.startup.ProfileLaunchParser
import com.example.clinometer.main.startup.SensorInitializer
import com.example.clinometer.main.ui.MainCameraModeBinder
import com.example.clinometer.main.ui.MainControlsBinder
import com.example.clinometer.main.ui.MainWindowInsetsApplier
import com.example.clinometer.main.ui.ManeuverUiBinder
import com.example.clinometer.main.ui.ProfileUiCoordinator
import com.example.clinometer.main.ui.ScreenKeepOnController
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.maps.plugin.animation.easeTo


@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
class MainActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private var serviceBound = false
    private var foregroundService: ForegroundService? = null
    private var shouldResetOnConnect = false

    private val renderHandler = Handler(Looper.getMainLooper())

    private val kalmanFilter = KalmanLocationFilter()
    private val motionPredictor = MotionPredictor()
    private var lastProcessedLocation: Location? = null
    private var isFirstLocation = true

    private val distanceTracker = DistanceTracker()

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null
    private var sensorBearing = 0f


    private var targetAngle = 0f
    private var currentAngle = 0f
    private var currentMapOrientation = 0f
    private var targetMapOrientation = 0f
    
    private var targetZoom = 17.5
    private var currentZoom = 17.5
    private var lastZoomChangeTime = 0L
    private val ZOOM_CHANGE_DELAY = 3000L

    private lateinit var currentProfile: Profile
    private var mapboxMapView: MapboxMapView? = null
    

    private var isNavigationActive = false
    private var hasReachedDestination = false
    private var navigationRouteGeometry: com.mapbox.geojson.LineString? = null
    private var navigationDestination: com.mapbox.geojson.Point? = null
    private var navigationDestinationName: String = ""
    private var navigationRoutePoints: List<com.mapbox.geojson.Point> = emptyList()
    private var navigationOriginLat: Double = 0.0
    private var navigationOriginLon: Double = 0.0
    private var navigationOriginBearing: Float = 0f
    private var allowMotorways: Boolean = true
    private var preferredRoutePolyline: String? = null
    private var hasAppliedPreferredRoute: Boolean = false
    private var hasTrimmedAlternativesForNav: Boolean = false
    private var directionsResponseJson: String? = null
    

    private var directionsService: com.example.clinometer.navigation.MapboxDirectionsService? = null
    private var mapboxAccessToken: String = ""
    private var isRerouting = false
    

    // SDK-driven navigation rendering (Mapbox mode)
    private var routeLineApi: MapboxRouteLineApi? = null
    private var routeLineView: MapboxRouteLineView? = null
    private val routeArrowApi: MapboxRouteArrowApi by lazy { MapboxRouteArrowApi() }
    private var routeArrowView: MapboxRouteArrowView? = null
    private var isSdkNavigationReady: Boolean = false
    private var hasRequestedInitialSdkRoute: Boolean = false
    private var hasInitializedSdkCamera: Boolean = false // legacy name: means "we already requested following"
    private var hasDoneInitialOverview: Boolean = false   // prevent overview on reroute (match TestNavigationActivity)
    private var shouldAnimateToFollowing: Boolean = false // Flag to animate to following after first location update

    private var viewportDataSource: MapboxNavigationViewportDataSource? = null
    private var navigationCamera: NavigationCamera? = null
    private val navigationLocationProvider = NavigationLocationProvider()
    private lateinit var maneuverApi: MapboxManeuverApi
    private var maneuverContainer: View? = null
    private var maneuverView: MapboxManeuverView? = null
    private var onIndicatorPositionChangedListener: OnIndicatorPositionChangedListener? = null

    private val pixelDensity = Resources.getSystem().displayMetrics.density
    // Adjust padding based on orientation - smaller padding for landscape to prevent zooming too far
    private fun getOverviewPadding(): EdgeInsets {
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        return if (isLandscape) {
            EdgeInsets(80.0 * pixelDensity, 40.0 * pixelDensity, 80.0 * pixelDensity, 40.0 * pixelDensity)
        } else {
            EdgeInsets(140.0 * pixelDensity, 40.0 * pixelDensity, 120.0 * pixelDensity, 40.0 * pixelDensity)
        }
    }
    private val followingPadding = EdgeInsets(180.0 * pixelDensity, 40.0 * pixelDensity, 150.0 * pixelDensity, 40.0 * pixelDensity)

    private val mapboxNavigation: MapboxNavigation by requireMapboxNavigation(
        onResumedObserver = object : MapboxNavigationObserver {
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                // Use Mapbox Navigation SDK for snap-to-road in both navigation and normal driving
                mapboxNavigation.registerLocationObserver(sdkLocationObserver)
                // Only register route observers in navigation mode
                if (isNavigationActive) {
                    mapboxNavigation.registerRoutesObserver(sdkRoutesObserver)
                    mapboxNavigation.registerRouteProgressObserver(sdkRouteProgressObserver)
                    mapboxNavigation.registerArrivalObserver(sdkArrivalObserver)
                }
                mapboxNavigation.startTripSession()
            }

            override fun onDetached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.unregisterLocationObserver(sdkLocationObserver)
                if (isNavigationActive) {
                    mapboxNavigation.unregisterRoutesObserver(sdkRoutesObserver)
                    mapboxNavigation.unregisterRouteProgressObserver(sdkRouteProgressObserver)
                    mapboxNavigation.unregisterArrivalObserver(sdkArrivalObserver)
                }
            }
        },
        onInitialize = this::initNavigationSdk
    )
    

    private var maneuverViewContainer: CardView? = null
    private var ivManeuverIcon: ImageView? = null
    private var tvManeuverDistance: TextView? = null
    private var tvManeuverPrimary: TextView? = null
    private var tvManeuverSecondary: TextView? = null


    private var tripProgressContainer: LinearLayout? = null
    private var tvTripEta: TextView? = null
    private var tvTripRemainingTime: TextView? = null
    private var tvTripRemainingDistance: TextView? = null
    

    private var navigationSteps: List<DirectionsStep> = emptyList()
    private var currentStepIndex: Int = 0
    

    

    private var mapboxCurrentPosition: GeoPoint? = null
    private var mapboxCurrentBearing: Float = 0f
    private var mapboxTargetPosition: GeoPoint? = null
    private var mapboxTargetBearing: Float = 0f
    private var mapboxLastUpdateTime: Long = 0L

    // Portrait-only views (nullable - may not exist in landscape layout)
    private var speedometerBackground: ImageView? = null
    private var gaugeView: GaugeView? = null
    private var linearGaugeView: LinearGaugeView? = null
    private var angleContainerMoto: LinearLayout? = null
    private var currentAngleText: TextView? = null
    private var speedText: TextView? = null
    private var angleTextMoto: TextView? = null
    private var distanceText: TextView? = null
    private var distanceTextCar: TextView? = null
    private var distanceContainer: LinearLayout? = null
    private var carModeContainer: LinearLayout? = null
    private var contentArea: ConstraintLayout? = null
    private var dashboardContainer: ConstraintLayout? = null
    private var buttonContainer: LinearLayout? = null
    private var resetButton: Button? = null
    private var stopButton: Button? = null
    private var chronometer: Chronometer? = null
    private var zeroButton: Button? = null
    
    // Common views (exist in both portrait and landscape)
    private lateinit var speedOverlayContainer: LinearLayout
    private lateinit var speedTextCar: TextView
    private lateinit var chronometerCar: Chronometer
    private lateinit var mapControlsContainer: LinearLayout
    private lateinit var bottomHudRow: ViewGroup
    
    // Landscape-only views (nullable - may not exist in portrait layout)
    private var linearGaugeViewLandscape: LinearGaugeView? = null
    private var angleTextMotoLandscape: TextView? = null
    private var angleLandscapeContainer: LinearLayout? = null
    private var carStatsOverlay: LinearLayout? = null
    private var distanceTextCarLandscape: TextView? = null
    private var chronometerCarLandscape: Chronometer? = null
    private var carActionButtonsOverlay: LinearLayout? = null
    private var resetButtonOverlay: ImageButton? = null
    private var zeroButtonOverlay: ImageButton? = null
    private var stopButtonOverlay: ImageButton? = null
    private var orientationToggle: ImageButton? = null
    private var cameraNorthModeButton: ImageButton? = null
    private var btnOverview: ImageButton? = null
    private var btnRecenter: ImageButton? = null
    private var isOrientationLocked: Boolean = false
    private var isNorthUpMode: Boolean = false
    private var lastCalculatedBearing: Float = 0f
    // Base margins/paddings are now in XML - store only what's needed for system bars insets
    private var baseMapControlsMarginTop = 0
    private var baseMapControlsMarginEnd = 0
    private var baseButtonContainerPaddingLeft = 0
    private var baseButtonContainerPaddingRight = 0
    private var baseButtonContainerPaddingBottom = 0

    // Constraints are now handled in XML (separate layout files for portrait/landscape)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ForegroundService.LocalBinder
            foregroundService = binder.getService()
            serviceBound = true
            

            if (shouldResetOnConnect) {
                shouldResetOnConnect = false
                resetSessionData()
            }
            


            // Update UI for both portrait and landscape modes
            // Landscape mode also needs updateUIForProfile() to hide trip progress and maneuver in normal driving
            updateUIForProfile()
            
            startChronometer()
            startRenderLoop()
            updateProfileBestTimes()

            foregroundService?.getLastLocation()?.let { location ->

                if (mapboxMapView != null && mapboxMapView?.visibility != android.view.View.VISIBLE) {
                    mapboxMapView?.visibility = android.view.View.VISIBLE
                }
                
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                

                val currentZoomValue = currentZoom.toDouble()
                
                val metersPerPixel = 156543.03392 * cos(Math.toRadians(location.latitude)) / Math.pow(2.0, currentZoomValue)
                val offsetMeters = 30 * resources.displayMetrics.density * metersPerPixel
                
                val bearingRad = Math.toRadians(location.bearing.toDouble())
                val offsetLat = (offsetMeters * cos(bearingRad)) / 111320.0
                val offsetLon = (offsetMeters * sin(bearingRad)) / (111320.0 * cos(Math.toRadians(location.latitude)))
                
                val centerLat = geoPoint.latitude + offsetLat
                val centerLon = geoPoint.longitude + offsetLon
                
                setMapCenter(centerLat, centerLon)
                motionPredictor.addSample(geoPoint, location.bearing, location.speed)

                distanceTracker.seedIfNeeded(geoPoint)

                isFirstLocation = false
            }

            val existingPoints = foregroundService?.getRoutePoints() ?: emptyList()
            if (existingPoints.isNotEmpty()) {
                distanceTracker.replaceWith(
                    existingPoints.map { GeoPoint(it.geoPoint.latitude, it.geoPoint.longitude) }
                )
                updateDistanceDisplay()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            foregroundService = null
            stopRenderLoop()
        }
    }

    private fun initNavigationSdk() {
        if (!MapboxNavigationApp.isSetup()) {
            MapboxNavigationApp.setup(NavigationOptions.Builder(this).build())
        }
    }

    private val sdkLocationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: com.mapbox.common.location.Location) {}

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation
            navigationLocationProvider.changePosition(enhancedLocation, locationMatcherResult.keyPoints)

            // Update viewport data source with location (like TestNavigationActivity)
            viewportDataSource?.let {
                it.onLocationChanged(enhancedLocation)
                it.evaluate()
            }

            // Convert enhancedLocation (com.mapbox.common.location.Location) to android.location.Location
            // for use in processLocationUpdate
            val androidLocation = android.location.Location("mapbox-enhanced")
            androidLocation.latitude = enhancedLocation.latitude
            androidLocation.longitude = enhancedLocation.longitude
            androidLocation.bearing = enhancedLocation.bearing?.toFloat() ?: 0f
            androidLocation.speed = enhancedLocation.speed?.toFloat() ?: 0f
            androidLocation.accuracy = enhancedLocation.horizontalAccuracy?.toFloat() ?: 10f
            // com.mapbox.common.location.Location doesn't have a time property
            // Use current time as timestamp (most accurate for real-time location updates)
            androidLocation.time = System.currentTimeMillis()

            // Process location update with snapped-to-road location
            // (snap-to-road is done by LocationMatcher, we just use the enhanced location)
            val speed = androidLocation.speed * 3.6f // Convert m/s to km/h
            processLocationUpdate(androidLocation, speed)

            // Navigation-specific logic (only for route requests, not camera)
            if (isNavigationActive) {
                // Request initial route once we have a real location and destination.
                if (!hasRequestedInitialSdkRoute && navigationDestination != null) {
                    requestInitialSdkRouteIfPossible()
                }
                
                // If we should animate to following (coming from preview), do it after location is updated
                if (shouldAnimateToFollowing && navigationCamera != null && mapboxNavigation.getNavigationRoutes().isNotEmpty()) {
                    shouldAnimateToFollowing = false
                    mapboxMapView?.post {
                        navigationCamera?.requestNavigationCameraToFollowing()
                    }
                }
            }
        }
    }

    private val sdkRoutesObserver = RoutesObserver { result ->
        if (!isNavigationActive) return@RoutesObserver
        val routes = result.navigationRoutes
        val style = mapboxMapView?.mapboxMap?.style ?: return@RoutesObserver
        val rla = routeLineApi ?: return@RoutesObserver
        val rlv = routeLineView ?: return@RoutesObserver

        if (routes.isEmpty()) {
            rla.clearRouteLine { value -> rlv.renderClearRouteLineValue(style, value) }
            routeArrowView?.render(style, routeArrowApi.clearArrows())
            maneuverContainer?.visibility = View.GONE
            maneuverViewContainer?.visibility = View.GONE
            // Don't hide tripProgressContainer here - let updateUIForProfile() manage it
            // tripProgressContainer visibility is managed by updateUIForProfile() based on navigation state and active session
            return@RoutesObserver
        }

        Log.d(TAG, "Routes received: ${routes.size}, preferred: ${preferredRoutePolyline?.take(50)}...")
        routes.forEachIndexed { idx, route ->
            Log.d(TAG, "Route $idx geometry: ${route.directionsRoute.geometry()?.take(50)}...")
        }

        // ВАЖНО: След reroute, новият маршрут става preferred
        // Ако preferredRoutePolyline е null и имаме маршрути, задаваме първия като preferred
        if (preferredRoutePolyline.isNullOrBlank() && routes.isNotEmpty()) {
            val firstRoutePolyline = routes.first().directionsRoute.geometry()
            if (!firstRoutePolyline.isNullOrBlank()) {
                preferredRoutePolyline = firstRoutePolyline
                Log.d(TAG, "Setting first route as preferred after reroute")
            }
        }

        // FIX: Преподреждане на маршрутите винаги когато има preferred route
        // Това гарантира, че дори при reroute, избраният маршрут остава primary
        val reorderedRoutes = if (!preferredRoutePolyline.isNullOrBlank()) {
            val preferredIdx = routes.indexOfFirst { 
                it.directionsRoute.geometry() == preferredRoutePolyline 
            }
            
            if (preferredIdx > 0) {
                // Намерен е preferred route, но не е на първа позиция
                Log.d(TAG, "Reordering routes: preferred route found at index $preferredIdx")
                val reordered = mutableListOf<NavigationRoute>()
                reordered.add(routes[preferredIdx])
                routes.indices.forEach { i -> 
                    if (i != preferredIdx) reordered.add(routes[i]) 
                }
                
                // След успешно преподреждане, задаваме новите маршрути и излизаме
                // Observer-а ще бъде извикан отново с вече правилния ред
                mapboxNavigation.setNavigationRoutes(reordered)
                return@RoutesObserver
            } else if (preferredIdx == 0) {
                // Preferred route вече е на първа позиция - OK
                Log.d(TAG, "Preferred route is already primary")
                routes
            } else {
                // Preferred route не е намерен (може би reroute е създал нов маршрут)
                // Използваме текущия ред, но запазваме само primary
                Log.d(TAG, "Preferred route not found, using current primary route")
                routes
            }
        } else {
            routes
        }

        // Trimming на alternatives - запазваме само primary route за активна навигация
        if (!hasTrimmedAlternativesForNav && reorderedRoutes.size > 1) {
            hasTrimmedAlternativesForNav = true
            mapboxNavigation.setNavigationRoutes(listOf(reorderedRoutes.first()))
            return@RoutesObserver
        }

        // Премахване на preview fallback layers
        try {
            if (style.styleLayerExists("navigation-route-layer")) style.removeStyleLayer("navigation-route-layer")
            if (style.styleLayerExists("navigation-route-casing-layer")) style.removeStyleLayer("navigation-route-casing-layer")
            if (style.styleSourceExists("navigation-route-source")) style.removeStyleSource("navigation-route-source")
        } catch (_: Throwable) {}

        // Рендериране само на primary route
        val primaryOnly = listOf(reorderedRoutes.first())
        rla.setNavigationRoutes(primaryOnly, emptyList()) { value ->
            rlv.renderRouteDrawData(style, value)
            
            // Update viewport data source with route for camera calculations
            viewportDataSource?.let {
                it.onRouteChanged(primaryOnly.first())
                it.evaluate()
            }
            
            // Request camera animation AFTER route is rendered (like TestNavigationActivity)
            // This ensures smooth animation without lag
            if (navigationCamera != null) {
                // Use post to ensure rendering is complete
                mapboxMapView?.post {
                    if (!hasDoneInitialOverview) {
                        // First time seeing route - show overview
                        navigationCamera?.requestNavigationCameraToOverview()
                        hasDoneInitialOverview = true
                    } else {
                        // Coming from preview - set flag to animate to following after first location update
                        // This ensures we have a valid location before animating
                        shouldAnimateToFollowing = true
                    }
                }
            }
        }
    }

    private val sdkRouteProgressObserver = RouteProgressObserver { routeProgress: RouteProgress ->
        if (!isNavigationActive) return@RouteProgressObserver

        // Update viewport data source with route progress (like TestNavigationActivity)
        viewportDataSource?.let {
            it.onRouteProgressChanged(routeProgress)
            it.evaluate()
        }

        // Route line + arrows (SDK-driven)
        val style = mapboxMapView?.mapboxMap?.style
        val rla = routeLineApi
        val rlv = routeLineView
        if (style != null && rla != null && rlv != null) {
            rla.updateWithRouteProgress(routeProgress) { value ->
                rlv.renderRouteLineUpdate(style, value)
            }
            val arrowUpdate = routeArrowApi.addUpcomingManeuverArrow(routeProgress)
            routeArrowView?.renderManeuverUpdate(style, arrowUpdate)
        }

        // Maneuver UI: render official MapboxManeuverView (same as TestNavigationActivity)
        // Only show in navigation mode, not in normal driving
        if (isNavigationActive) {
            try {
                val maneuversExpected = maneuverApi.getManeuvers(routeProgress)
                maneuverView?.renderManeuvers(maneuversExpected)
                maneuverContainer?.visibility = View.VISIBLE
                // Ensure legacy custom container stays hidden in Mapbox navigation
                maneuverViewContainer?.visibility = View.GONE

                // Ensure Mapbox internal backgrounds don't override our transparent pill background.
                // (Do it on UI thread; Mapbox can recreate child views.)
                val orangeColor = Color.parseColor("#FF6020")
                val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                maneuverView?.post {
                    val mv = maneuverView ?: return@post
                    ManeuverUiBinder.setViewColors(mv, orangeColor, Color.TRANSPARENT)
                    ManeuverUiBinder.reduceManeuverTextSize(mv, isLandscape)
                    ManeuverUiBinder.reduceManeuverIconSize(mv, isLandscape)
                    ManeuverUiBinder.centerManeuverText(mv)
                    ManeuverUiBinder.reduceManeuverSpacing(mv, isLandscape) // Reduce spacing between icon and text
                }
            } catch (_: Throwable) {
                // Ignore UI failures, keep navigation running.
            }
        } else {
            // Normal driving: hide maneuver container completely
            maneuverContainer?.visibility = View.GONE
            maneuverViewContainer?.visibility = View.GONE
            maneuverContainer?.setBackgroundResource(0) // Remove background
        }

        val distanceRemaining = routeProgress.distanceRemaining ?: 0f
        val durationRemainingSeconds = (routeProgress.durationRemaining ?: 0.0).toLong()
        val tripProgress = TripProgressFormatter.format(distanceRemaining, durationRemainingSeconds)

        // Don't set visibility here - let updateUIForProfile() manage it
        // Only update text content
        tvTripEta?.text = tripProgress.etaText
        tvTripRemainingTime?.text = tripProgress.timeRemainingText
        tvTripRemainingDistance?.text = tripProgress.distanceRemainingText
    }

    private val sdkArrivalObserver = object : ArrivalObserver {
        override fun onWaypointArrival(routeProgress: RouteProgress) {}
        override fun onNextRouteLegStart(routeLegProgress: com.mapbox.navigation.base.trip.model.RouteLegProgress) {}

        override fun onFinalDestinationArrival(routeProgress: RouteProgress) {
            if (!isNavigationActive) return
            // Keep existing behaviour (notification + cleanup UI), but stop SDK routes.
            mapboxNavigation.setNavigationRoutes(emptyList())
            onDestinationReached()
        }
    }

    private fun requestInitialSdkRouteIfPossible() {
        val dest = navigationDestination ?: return
        val originPoint = when {
            navigationOriginLat != 0.0 && navigationOriginLon != 0.0 -> com.mapbox.geojson.Point.fromLngLat(navigationOriginLon, navigationOriginLat)
            else -> {
                val last = navigationLocationProvider.lastLocation
                if (last != null) com.mapbox.geojson.Point.fromLngLat(last.longitude, last.latitude) else null
            }
        } ?: return

        hasRequestedInitialSdkRoute = true
        val routeOptionsBuilder = RouteOptions.builder()
            .applyDefaultNavigationOptions()
            .applyLanguageAndVoiceUnitOptions(this)
            .coordinatesList(listOf(originPoint, dest))
            .alternatives(true)
        if (!allowMotorways) {
            routeOptionsBuilder.exclude("motorway")
        }
        val routeOptions = routeOptionsBuilder.build()

        mapboxNavigation.requestRoutes(
            routeOptions,
            object : com.mapbox.navigation.base.route.NavigationRouterCallback {
                override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                    mapboxNavigation.setNavigationRoutes(routes)
                }

                override fun onFailure(reasons: List<com.mapbox.navigation.base.route.RouterFailure>, routeOptions: RouteOptions) {
                    Toast.makeText(this@MainActivity, "Грешка при намиране на маршрут", Toast.LENGTH_SHORT).show()
                }

                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {}
            }
        )
    }

    private fun initializeNavigationStateFromIntent(intent: Intent) {
        val launchData = NavigationLaunchDataParser.parse(this, intent)

        hasDoneInitialOverview = launchData.startFromPreview
        hasInitializedSdkCamera = false
        hasRequestedInitialSdkRoute = false

        directionsResponseJson = launchData.directionsResponseJson
        val originLat = launchData.originLat
        val originLon = launchData.originLon
        allowMotorways = launchData.allowMotorways
        navigationDestinationName = launchData.destinationName
        preferredRoutePolyline = launchData.preferredRoutePolyline

        if (originLat != 0.0 && originLon != 0.0) {
            navigationOriginLat = originLat
            navigationOriginLon = originLon
            navigationOriginBearing = launchData.originBearing
            isFirstLocation = false
        }

        navigationRouteGeometry = launchData.parsedRouteGeometry
        navigationRoutePoints = launchData.routePoints

        hasAppliedPreferredRoute = false
        hasTrimmedAlternativesForNav = false

        navigationSteps = launchData.navigationSteps
        navigationDestination = launchData.destinationPoint
    }

    private fun initializeUiForCurrentConfiguration() {
        initializeViews()

        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (!isLandscape) {
            updateUIForProfile()
        }

        setupButtons()
        setupOrientationToggle()
        setupCameraModeToggle()
        setupWindowInsets()
    }

    private fun initializeCoreComponents() {
        initializeSensors()
        ScreenKeepOnController.setup(this)
        setupMap()
        if (isNavigationActive) {
            initializeDirectionsService()
        }
    }

    private val renderRunnable = object : Runnable {
        override fun run() {
            updateUIFromService()
            updateGaugeAnimation()
            updateMapAnimation()
            renderHandler.postDelayed(this, 16)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        currentProfile = ProfileLaunchParser.parse(intent)


        isNavigationActive = intent.getBooleanExtra("navigation_active", false)
        hasReachedDestination = false
        if (isNavigationActive) {
            initializeNavigationStateFromIntent(intent)
        }

        initializeCoreComponents()

        initializeUiForCurrentConfiguration()

        NavigationServiceLauncher.connect(
            context = this,
            isServiceRunning = isServiceRunning(),
            isNavigationActive = isNavigationActive,
            serviceConnection = serviceConnection
        )

    }

    private fun initializeSensors() {
        val (manager, setup) = SensorInitializer.initialize(this)
        sensorManager = manager
        rotationSensor = setup.rotationSensor
        accelerometer = setup.accelerometer
        magnetometer = setup.magnetometer
    }

    private fun initializeViews() {
        // Portrait-only views (may be null in landscape layout)
        speedometerBackground = findViewById(R.id.speedometerBackground)
        chronometer = findViewById(R.id.chronometer)
        gaugeView = findViewById(R.id.gaugeView)
        currentAngleText = findViewById(R.id.currentAngleText)
        speedText = findViewById(R.id.speedText)
        angleContainerMoto = findViewById(R.id.angleContainerMoto)
        angleTextMoto = findViewById(R.id.angleTextMoto)
        linearGaugeView = findViewById(R.id.linearGaugeView)
        distanceText = findViewById(R.id.distanceText)
        distanceContainer = findViewById(R.id.distanceContainer)
        carModeContainer = findViewById(R.id.carModeContainer)
        contentArea = findViewById(R.id.contentArea)
        dashboardContainer = findViewById(R.id.dashboardContainer)
        buttonContainer = findViewById(R.id.buttonContainer)
        resetButton = findViewById(R.id.btnReset)
        zeroButton = findViewById(R.id.btnZero)
        stopButton = findViewById(R.id.btnStop)
        
        // Common views (exist in both layouts)
        speedOverlayContainer = findViewById(R.id.speedOverlayContainer)
        bottomHudRow = findViewById(R.id.bottomHudRow)
        speedTextCar = findViewById(R.id.speedTextCar)
        chronometerCar = findViewById(R.id.chronometerCar)
        mapControlsContainer = findViewById(R.id.mapControlsContainer)
        
        // Landscape-only views (may be null in portrait layout)
        angleLandscapeContainer = findViewById(R.id.angleLandscapeContainer)
        angleTextMotoLandscape = findViewById(R.id.angleTextMotoLandscape)
        linearGaugeViewLandscape = findViewById(R.id.linearGaugeViewLandscape)
        carStatsOverlay = findViewById(R.id.carStatsOverlay)
        distanceTextCarLandscape = findViewById(R.id.distanceTextCarLandscape)
        chronometerCarLandscape = findViewById(R.id.chronometerCarLandscape)
        carActionButtonsOverlay = findViewById(R.id.carActionButtonsOverlay)
        resetButtonOverlay = findViewById(R.id.btnResetCarMap)
        zeroButtonOverlay = findViewById(R.id.btnZeroCarMap)
        stopButtonOverlay = findViewById(R.id.btnStopCarMap)
        
        maneuverContainer = findViewById(R.id.maneuverContainer)
        maneuverView = findViewById(R.id.maneuverView)

        // Maneuver styling 1:1 with TestNavigationActivity (colors + font sizes)
        // Only set background if in navigation mode
        val orangeColor = Color.parseColor("#FF6020")
        val darkBackground = Color.TRANSPARENT
        if (isNavigationActive) {
            maneuverContainer?.setBackgroundResource(R.drawable.bg_map_controls_pill)
        } else {
            maneuverContainer?.setBackgroundResource(0) // No background in normal driving
            maneuverContainer?.visibility = View.GONE // Hide in normal driving
        }
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        maneuverView?.post {
            val mv = maneuverView ?: return@post
            ManeuverUiBinder.setViewColors(mv, orangeColor, darkBackground)
            ManeuverUiBinder.reduceManeuverTextSize(mv, isLandscape)
            ManeuverUiBinder.reduceManeuverIconSize(mv, isLandscape)
            ManeuverUiBinder.centerManeuverText(mv)
            ManeuverUiBinder.reduceManeuverSpacing(mv, isLandscape) // Reduce spacing between icon and text
        }

        maneuverViewContainer = findViewById(R.id.maneuverViewContainer)
        ivManeuverIcon = findViewById(R.id.ivManeuverIcon)
        tvManeuverDistance = findViewById(R.id.tvManeuverDistance)
        tvManeuverPrimary = findViewById(R.id.tvManeuverPrimary)
        tvManeuverSecondary = findViewById(R.id.tvManeuverSecondary)

        tripProgressContainer = findViewById(R.id.tripProgressContainer)
        tvTripEta = findViewById(R.id.tvTripEta)
        tvTripRemainingTime = findViewById(R.id.tvTripRemainingTime)
        tvTripRemainingDistance = findViewById(R.id.tvTripRemainingDistance)
        
        distanceTextCar = findViewById(R.id.distanceTextCar)
        orientationToggle = findViewById(R.id.btnOrientationToggle)
        cameraNorthModeButton = findViewById(R.id.btnCameraNorthMode)
        btnOverview = findViewById(R.id.btnOverview)
        btnRecenter = findViewById(R.id.btnRecenter)


        // Initialize default text values (only if views exist in current layout)
        currentAngleText?.text = getString(R.string.current_angle, 0)
        speedText?.text = getString(R.string.current_speed, 0)
        speedTextCar.text = "0"
        angleTextMoto?.text = "0°"
        angleTextMotoLandscape?.text = "0°"
        distanceText?.text = "0.00 km"
        distanceTextCar?.text = "0.00"
        distanceTextCarLandscape?.text = "0.00"

        (mapControlsContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            baseMapControlsMarginTop = lp.topMargin
            baseMapControlsMarginEnd = lp.marginEnd
        }
        buttonContainer?.let {
            baseButtonContainerPaddingLeft = it.paddingLeft
            baseButtonContainerPaddingRight = it.paddingRight
            baseButtonContainerPaddingBottom = it.paddingBottom
        }
        updateProfileBestTimes()
    }

    private fun updateUIForProfile() {
        val isMotorcycle = currentProfile.vehicleType == Profile.VehicleType.MOTORCYCLE
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        ProfileUiCoordinator.apply(
            isMotorcycle = isMotorcycle,
            isLandscape = isLandscape,
            isNavigationActive = isNavigationActive,
            carModeContainer = carModeContainer,
            dashboardContainer = dashboardContainer,
            speedOverlayContainer = speedOverlayContainer,
            angleContainerMoto = angleContainerMoto,
            angleTextMoto = angleTextMoto,
            linearGaugeView = linearGaugeView,
            zeroButton = zeroButton,
            speedometerBackground = speedometerBackground,
            gaugeView = gaugeView,
            currentAngleText = currentAngleText,
            speedText = speedText,
            chronometer = chronometer,
            distanceContainer = distanceContainer,
            contentArea = contentArea,
            tripProgressContainer = tripProgressContainer,
            maneuverContainer = maneuverContainer,
            maneuverViewContainer = maneuverViewContainer,
            zeroButtonOverlay = zeroButtonOverlay,
            angleLandscapeContainer = angleLandscapeContainer
        )
    }

    private fun setupMap() {
        setupMapboxMap()
    }
    
    private fun loadMapboxStyleFromJson(onStyleLoaded: (Style) -> Unit) {
        val styleUri = "mapbox://styles/djsookz/cmiyer8iu000101s84wqsav5l"
        mapboxMapView?.mapboxMap?.loadStyleUri(styleUri) { style ->
            onStyleLoaded(style)
        }
    }
    
    private fun setupMapboxMap() {
        mapboxMapView = MapViewContainerBinder.createMapView(
            activity = this,
            mapContainerId = R.id.mapContainer,
            legacyMapViewId = R.id.mapView
        )

        mapboxMapView?.let { mapView ->
            MapInitialCameraApplier.apply(
                intent = intent,
                mapView = mapView,
                isNavigationActive = isNavigationActive,
                navigationOriginLat = navigationOriginLat,
                navigationOriginLon = navigationOriginLon,
                navigationOriginBearing = navigationOriginBearing,
                navigationDestination = navigationDestination,
                navigationRoutePoints = navigationRoutePoints,
                lastLocation = foregroundService?.getLastLocation(),
                calculateBearingBetweenPoints = GeoBearing::calculateBetweenPoints,
                cameraPitchProvider = ::getCameraPitch
            )
        }
        

        loadMapboxStyleFromJson { style ->
            MapStyleReadyHandler.handle(
                style = style,
                mapView = mapboxMapView,
                isNavigationActive = isNavigationActive,
                navigationRouteGeometry = navigationRouteGeometry,
                setupSdkNavigationOnStyle = ::setupSdkNavigationOnStyle,
                updateUiForProfile = ::updateUIForProfile,
                setupNavigationRouteFallback = ::setupNavigationRouteFallback,
                requestInitialSdkRouteIfPossible = ::requestInitialSdkRouteIfPossible
            )
        }
        

        mapboxMapView?.compass?.enabled = false
        mapboxMapView?.scalebar?.enabled = false
    }

    private fun setupSdkNavigationOnStyle(style: Style) {
        val mv = mapboxMapView ?: return

        // Initialize viewportDataSource for route line rendering and camera control (like TestNavigationActivity)
        viewportDataSource = MapboxNavigationViewportDataSource(mv.mapboxMap).apply {
            overviewPadding = this@MainActivity.getOverviewPadding()
            followingPadding = this@MainActivity.followingPadding
        }
        // Initialize NavigationCamera for navigation mode (use SDK camera control like TestNavigationActivity)
        if (isNavigationActive) {
            navigationCamera = NavigationCamera(mv.mapboxMap, mv.camera, viewportDataSource!!)
            mv.camera.addCameraAnimationsLifecycleListener(NavigationBasicGesturesHandler(navigationCamera!!))
            
            // If we already have routes and came from preview, set flag to animate after location update
            if (hasDoneInitialOverview && mapboxNavigation.getNavigationRoutes().isNotEmpty()) {
                shouldAnimateToFollowing = true
            }
        }

        // Navigation-specific components (only for navigation mode)
        if (isNavigationActive) {
            val visuals = SdkStyleSetup.createNavigationVisualComponents(
                context = this,
                style = style,
                createRouteArrowView = ::createRouteArrowView
            )
            maneuverApi = visuals.maneuverApi
            routeLineApi = visuals.routeLineApi
            routeLineView = visuals.routeLineView
            routeArrowView = visuals.routeArrowView
        }

        SdkStyleSetup.configureLocationPuck(
            mapView = mv,
            navigationLocationProvider = navigationLocationProvider,
            density = resources.displayMetrics.density
        )

        // Vanishing route line: update traveled line by puck position (only in navigation mode)
        if (isNavigationActive) {
            onIndicatorPositionChangedListener = SdkStyleSetup.installVanishingRouteListener(
                mapView = mv,
                existingListener = onIndicatorPositionChangedListener,
                routeLineApi = routeLineApi,
                routeLineView = routeLineView,
                hasActiveRoutes = { mapboxNavigation.getNavigationRoutes().isNotEmpty() }
            )
        }

        isSdkNavigationReady = true
        setupNavigationCameraButtons()
    }

    private fun createRouteArrowView(useMiddleSlot: Boolean): MapboxRouteArrowView {
        val routeArrowOptionsBuilder = RouteArrowOptions.Builder(this)
        if (useMiddleSlot) {
            routeArrowOptionsBuilder.withSlotName("middle")
        }
        return MapboxRouteArrowView(routeArrowOptionsBuilder.build())
    }

    private fun setupNavigationRoute(style: Style) {
        if (!isNavigationActive) {
            return
        }
        
        try {
            val components = NavigationRouteRenderComponentsFactory.ensure(
                context = this,
                routeLineApi = routeLineApi,
                routeLineView = routeLineView,
                routeArrowView = routeArrowView,
                createRouteArrowView = ::createRouteArrowView
            )
            routeLineApi = components.routeLineApi
            routeLineView = components.routeLineView
            routeArrowView = components.routeArrowView

            if (navigationRouteGeometry != null) {
                setupNavigationRouteFallback(style)
            }
            

            navigationDestination?.let { destination ->
                NavigationDestinationLayerBinder.bindIfNeeded(style, destination)
            }
        } catch (e: Exception) {
            e.printStackTrace()

            if (navigationRouteGeometry != null) {
                setupNavigationRouteFallback(style)
            }
        }
    }
    
    private fun setupNavigationRouteFallback(style: Style) {
        val routeGeometry = navigationRouteGeometry ?: return
        try {
            NavigationRouteFallbackRenderer.render(style, routeGeometry)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Removed setupMapboxLocationMarker - using SDK Location Component (location puck) instead

    private fun setupButtons() {
        MainControlsBinder.bindSessionControls(
            resetButton = resetButton,
            resetButtonOverlay = resetButtonOverlay,
            zeroButton = zeroButton,
            zeroButtonOverlay = zeroButtonOverlay,
            stopButton = stopButton,
            stopButtonOverlay = stopButtonOverlay,
            onReset = {
                if (checkLocationPermission()) {
                    if (serviceBound && foregroundService != null) {
                        resetSessionData()
                    } else {
                        shouldResetOnConnect = true
                        startAndBindService()
                    }
                }
            },
            onZero = {
                if (currentProfile.vehicleType == Profile.VehicleType.MOTORCYCLE) {
                    foregroundService?.calibrateZero()
                    resetAngleDisplay()
                }
            },
            onStop = {
                if (serviceBound) {
                    saveAndFinishSession()
                }
            }
        )
    }

    private fun setupOrientationToggle() {
        applyOrientationLock(false)
        orientationToggle?.setOnClickListener {
            isOrientationLocked = !isOrientationLocked
            applyOrientationLock(isOrientationLocked)
        }
    }

    private fun getCameraPitch(): Double {
        return if (isNorthUpMode) {
            0.0
        } else {
            if (isNavigationActive) 60.0 else 45.0
        }
    }
    
    private fun setupCameraModeToggle() {
        updateCameraModeIcon()
        MainCameraModeBinder.bindToggle(
            button = cameraNorthModeButton,
            isNavigationActive = isNavigationActive,
            hasNavigationCamera = navigationCamera != null,
            onToggle = {
                isNorthUpMode = !isNorthUpMode
                if (isNorthUpMode) {
                    targetMapOrientation = 0f
                    centerCurrentLocation()
                } else {
                    targetMapOrientation = -lastCalculatedBearing
                }
                updateCameraModeIcon()
                MainCameraModeBinder.applyCameraMode(
                    mapView = mapboxMapView,
                    isNorthUpMode = isNorthUpMode,
                    targetMapOrientation = targetMapOrientation,
                    pitch = getCameraPitch()
                )
            }
        )
    }

    private fun setupNavigationCameraButtons() {
        MainControlsBinder.bindNavigationCameraButtons(
            isNavigationActive = isNavigationActive,
            hasNavigationCamera = navigationCamera != null,
            btnOverview = btnOverview,
            btnRecenter = btnRecenter,
            cameraNorthModeButton = cameraNorthModeButton,
            onRecenter = { navigationCamera?.requestNavigationCameraToFollowing() },
            onOverview = { navigationCamera?.requestNavigationCameraToOverview() }
        )
    }

    private fun setupWindowInsets() {
        val rootContainer = findViewById<View>(R.id.rootContainer)
        MainWindowInsetsApplier.apply(
            rootContainer = rootContainer,
            resources = resources,
            mapControlsContainer = mapControlsContainer,
            bottomHudRow = bottomHudRow,
            carStatsOverlay = carStatsOverlay,
            maneuverContainer = maneuverContainer,
            carActionButtonsOverlay = carActionButtonsOverlay,
            buttonContainer = buttonContainer,
            baseMapControlsMarginTop = baseMapControlsMarginTop,
            baseMapControlsMarginEnd = baseMapControlsMarginEnd,
            baseButtonContainerPaddingLeft = baseButtonContainerPaddingLeft,
            baseButtonContainerPaddingRight = baseButtonContainerPaddingRight,
            baseButtonContainerPaddingBottom = baseButtonContainerPaddingBottom
        )
    }

    private fun updateCameraModeIcon() {
        MainCameraModeBinder.updateIcon(cameraNorthModeButton, isNorthUpMode)
    }

    private fun centerCurrentLocation() {
        // Mapbox handles centering automatically
    }

    private fun applyOrientationLock(locked: Boolean) {
        if (locked) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            orientationToggle?.setImageResource(R.drawable.ic_lock)
            orientationToggle?.imageAlpha = (255 * 0.95).toInt()
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            orientationToggle?.setImageResource(R.drawable.ic_unlock)
            orientationToggle?.imageAlpha = (255 * 0.5).toInt()
        }
    }


    private fun setMapCenter(lat: Double, lon: Double) {
        val pitch = getCameraPitch()
        mapboxMapView?.mapboxMap?.setCamera(
            CameraOptions.Builder()
                .center(MapboxPoint.fromLngLat(lon, lat))
                .zoom(currentZoom.toDouble())
                .pitch(pitch)
                .build()
        )
    }
    
    override fun onStart() {
        super.onStart()
        mapboxMapView?.onStart()
    }
    
    override fun onStop() {
        super.onStop()

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun checkLocationPermission(): Boolean {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, requiredPermissions, 1000)
        }
        return allGranted
    }

    private fun startAndBindService() {
        MainServiceCoordinator.startForegroundAndBind(this, serviceConnection)
    }

    private fun resetSessionData() {
        foregroundService?.resetData()
        resetAngleDisplay()
        updateProfileBestTimes()


        val startTime = MainServiceCoordinator.resolveStartTimeOrNow(foregroundService?.getStartTime())
        MainServiceCoordinator.applyChronometerStart(
            startTime = startTime,
            mainChronometer = chronometer,
            carChronometer = if (::chronometerCar.isInitialized) chronometerCar else null,
            carLandscapeChronometer = chronometerCarLandscape
        )

        targetAngle = 0f
        currentAngle = 0f
        // Don't reset camera orientation - let camera stay where it is
        // currentMapOrientation = 0f
        // targetMapOrientation = 0f
        // isFirstLocation = true

        distanceTracker.reset()
        updateDistanceDisplay()


        motionPredictor.addSample(GeoPoint(0.0, 0.0), 0f, 0f)
    }

    private fun resetAngleDisplay() {
        currentAngleText?.text = getString(R.string.current_angle, 0)
        angleTextMoto?.text = "0°"
        angleTextMotoLandscape?.text = "0°"

        gaugeView?.apply {
            angle = 0f
            maxLeftAngle = 0f
            maxRightAngle = 0f
            resetMaxima()
            invalidate()
        }
        
        if (linearGaugeView?.visibility == View.VISIBLE) {
            linearGaugeView?.apply {
                angle = 0f
                maxLeftAngle = 0f
                maxRightAngle = 0f
                resetMaxima()
                invalidate()
            }
        }
        
        if (linearGaugeViewLandscape?.visibility == View.VISIBLE) {
            linearGaugeViewLandscape?.apply {
                angle = 0f
                maxLeftAngle = 0f
                maxRightAngle = 0f
                resetMaxima()
                invalidate()
            }
        }
    }

    private fun initializeFirstLocation(location: Location) {
        if (isFirstLocation) {
            val geoPoint = GeoPoint(location.latitude, location.longitude)
            

            if (mapboxMapView != null) {
                mapboxMapView?.visibility = android.view.View.VISIBLE
            }
            

            val zoomLevel = currentZoom
            val metersPerPixel = 156543.03392 * cos(Math.toRadians(location.latitude)) / Math.pow(2.0, zoomLevel)
            val offsetMeters = 30 * resources.displayMetrics.density * metersPerPixel
            
            val bearingRad = Math.toRadians(location.bearing.toDouble())
            val offsetLat = (offsetMeters * cos(bearingRad)) / 111320.0
            val offsetLon = (offsetMeters * sin(bearingRad)) / (111320.0 * cos(Math.toRadians(location.latitude)))
            
            val centerLat = geoPoint.latitude + offsetLat
            val centerLon = geoPoint.longitude + offsetLon
            
            setMapCenter(centerLat, centerLon)
            motionPredictor.addSample(geoPoint, location.bearing, location.speed)

            distanceTracker.seedIfNeeded(geoPoint)

            isFirstLocation = false
        }
    }

    private fun updateDistance(newPoint: GeoPoint) {
        val changed = distanceTracker.addPointAndAccumulate(newPoint)
        if (changed) {
            updateDistanceDisplay()
        }
    }

    private fun updateDistanceDisplay() {
        val totalDistance = distanceTracker.totalDistanceKm
        distanceText?.text = "%.2f km".format(totalDistance)
        distanceTextCar?.text = "%.2f".format(totalDistance)
        distanceTextCarLandscape?.text = "%.2f".format(totalDistance)
        
        // Don't set visibility here - let updateUIForProfile() manage it
        // Visibility is managed by updateUIForProfile() based on landscape mode and active session
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
                Toast.makeText(
                    this, 
                    getString(R.string.error_no_route_data), 
                    Toast.LENGTH_LONG
                ).show()
                
                startActivity(MainServiceCoordinator.buildNavigateToMapPageIntent(this))
                overridePendingTransition(0, 0)
                finish()
                return
            }

            val race = createRaceFromSession()


            RouteStorage.saveRoutePoints(this, race.id, rawRoutePoints)
            val allRaces = RouteStorage.loadRaces(this).toMutableList()
            allRaces.add(race)
            RouteStorage.saveRaces(this, allRaces)

            // 🔥 Генерирай snapshot ВЕДНАГА след запис (background task)
            if (rawRoutePoints.isNotEmpty()) {
                java.util.concurrent.Executors.newSingleThreadExecutor().execute {
                    try {
                        RouteSnapshotGenerator.generateAndSaveSnapshot(
                            context = this@MainActivity,
                            raceId = race.id,
                            routePoints = rawRoutePoints
                        ) { success ->
                            android.util.Log.d("MainActivity", if (success) "✅ Snapshot generated for race ${race.id}" else "❌ Failed to generate snapshot for race ${race.id}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error generating snapshot", e)
                    }
                }
            }

            cleanupForegroundService()



            val intent = Intent(this, SaveSessionActivity::class.java).apply {
                putExtra("raceId", race.id)
                putExtra("isNewSession", true)
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            showError("Error saving the race: ${e.message}")
        }
    }

    private fun createRaceFromSession(): Race {
        val routePoints = foregroundService?.getFinalRoutePoints() ?: emptyList()
        val sessionNumber = SessionRecorder.nextSessionNumber(this, currentProfile.id)
        

        val isMotorcycle = currentProfile.vehicleType == Profile.VehicleType.MOTORCYCLE
        val maxLeftAngle = if (isMotorcycle) (foregroundService?.getMaxLeftAngle() ?: 0f) else 0f
        val maxRightAngle = if (isMotorcycle) (foregroundService?.getMaxRightAngle() ?: 0f) else 0f

        val raceId = System.currentTimeMillis()

        return SessionRecorder.buildRace(
            profile = currentProfile,
            raceId = raceId,
            routePoints = routePoints,
            duration = foregroundService?.getServiceDuration() ?: 0,
            maxSpeed = foregroundService?.getMaxSpeed() ?: 0f,
            totalDistance = distanceTracker.totalDistanceKm,
            maxLeftAngle = maxLeftAngle,
            maxRightAngle = maxRightAngle,
            sessionNumber = sessionNumber
        )
    }

    private fun cleanupForegroundService() {
        serviceBound = MainServiceCoordinator.safeCleanup(this, serviceBound, serviceConnection)
    }

    private fun handleEmptySession() {
        cleanupForegroundService()
        Toast.makeText(this, getString(R.string.error_no_route_data), Toast.LENGTH_LONG).show()

        startActivity(MainServiceCoordinator.buildNavigateToMapIntent(this))
        overridePendingTransition(0, 0)
        finish()
    }

    private fun showError(message: String) {
        val errorDialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .create()
        DialogHelper.styleDialogButtons(errorDialog)
        errorDialog.show()
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } ?: run {
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            magnetometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Avoid leaking listeners (Mapbox location)
        try {
            val mv = mapboxMapView
            val listener = onIndicatorPositionChangedListener
            if (mv != null && listener != null) {
                mv.location.removeOnIndicatorPositionChangedListener(listener)
            }
        } catch (_: Throwable) {
        }
        onIndicatorPositionChangedListener = null


        routeLineApi?.cancel()
        routeLineView?.cancel()
        routeArrowView = null
        
        mapboxMapView?.onDestroy()
        stopRenderLoop()
        if (serviceBound) {
            try {
                unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
            }
            serviceBound = false
        }
    }

    private fun startRenderLoop() {
        renderHandler.post(renderRunnable)
    }

    private fun stopRenderLoop() {
        renderHandler.removeCallbacks(renderRunnable)
    }

    private fun startChronometer() {
        val startTime = MainServiceCoordinator.resolveStartTimeOrNow(foregroundService?.getStartTime())
        MainServiceCoordinator.applyChronometerStart(
            startTime = startTime,
            mainChronometer = chronometer,
            carChronometer = if (::chronometerCar.isInitialized) chronometerCar else null,
            carLandscapeChronometer = chronometerCarLandscape
        )
    }

    private fun updateProfileBestTimes() {

        val currentSpeed = foregroundService?.getCurrentSpeed() ?: 0f
        if (currentSpeed > currentProfile.maxSpeed) {
            currentProfile.maxSpeed = currentSpeed
            val profiles = ProfileStorage.loadProfiles(this)
            profiles.find { it.id == currentProfile.id }?.apply {
                maxSpeed = currentProfile.maxSpeed
            }
            ProfileStorage.saveProfiles(this, profiles)
        }
    }

    private fun updateUIFromService() {
        foregroundService?.let { service ->
            val angle = service.getCurrentAngle()
            targetAngle = targetAngle * 0.7f + angle * 0.3f

            currentAngleText?.text = getString(R.string.current_angle, angle.toInt())

            val speed = service.getCurrentSpeed()
            speedText?.text = getString(R.string.current_speed, speed.toInt())
            speedTextCar.text = speed.toInt().toString()



            val roundedAngle = currentAngle.toInt()
            val currentDisplayText = "${roundedAngle}°"
            if (angleTextMoto?.text != currentDisplayText) {
                angleTextMoto?.text = currentDisplayText
            }
            if (angleTextMotoLandscape?.text != currentDisplayText) {
                angleTextMotoLandscape?.text = currentDisplayText
            }

            gaugeView?.maxLeftAngle = service.getMaxLeftAngle()
            gaugeView?.maxRightAngle = service.getMaxRightAngle()
            
            if (linearGaugeView?.visibility == View.VISIBLE) {
                linearGaugeView?.maxLeftAngle = service.getMaxLeftAngle()
                linearGaugeView?.maxRightAngle = service.getMaxRightAngle()
            }
            
            if (linearGaugeViewLandscape?.visibility == View.VISIBLE) {
                linearGaugeViewLandscape?.maxLeftAngle = service.getMaxLeftAngle()
                linearGaugeViewLandscape?.maxRightAngle = service.getMaxRightAngle()
            }

            service.getLastLocation()?.let { location ->
                processLocationUpdate(location, speed)
            } ?: run {

                if (isNavigationActive && navigationOriginLat != 0.0 && navigationOriginLon != 0.0) {

                    val syntheticLocation = android.location.Location("navigation")
                    syntheticLocation.latitude = navigationOriginLat
                    syntheticLocation.longitude = navigationOriginLon
                    syntheticLocation.bearing = navigationOriginBearing
                    syntheticLocation.speed = 0f
                    syntheticLocation.accuracy = 10f
                    syntheticLocation.time = System.currentTimeMillis()
                    processLocationUpdate(syntheticLocation, speed)
                }


            }

            updateProfileBestTimes()
        } ?: run {
        }
    }

    private fun processLocationUpdate(location: Location, speed: Float) {
        val computed = LocationUpdateCoordinator.compute(
            location = location,
            speed = speed,
            lastProcessedLocation = lastProcessedLocation,
            isNorthUpMode = isNorthUpMode,
            kalmanFilter = kalmanFilter
        )
        

        // Navigation progress is handled by Mapbox SDK RouteProgress observer

        if (isFirstLocation) {
            initializeFirstLocation(computed.filteredLocation)
            return
        }
        updateDistance(computed.geoPoint)

        lastProcessedLocation = computed.filteredLocation

        // Location is handled by SDK Location Component (location puck)
        // No need to update custom marker
        mapboxTargetPosition = computed.geoPoint
        mapboxTargetBearing = computed.calculatedBearing

        // Route drawing is handled by Mapbox SDK

        lastCalculatedBearing = computed.calculatedBearing
        targetMapOrientation = if (isNorthUpMode) {
            0f
        } else if (speed > 2f) {
            computed.targetMapOrientation
        } else {
            targetMapOrientation
        }
        

        
        updateZoomBasedOnSpeed(speed)
    }
    
    // Removed updateMapboxLocationMarker - using SDK Location Component (location puck) instead
    
    private fun updateMapboxMapAnimation() {
        if (mapboxMapView == null) return
        

        val targetPos = mapboxTargetPosition ?: return
        


        

        val targetBearing = mapboxTargetBearing
        

        if (mapboxCurrentPosition == null) {
            mapboxCurrentPosition = targetPos
            mapboxCurrentBearing = targetBearing
            mapboxLastUpdateTime = SystemClock.elapsedRealtime()
            // Location is handled by SDK Location Component (location puck)
        }
        
        val now = SystemClock.elapsedRealtime()
        val elapsed = (now - mapboxLastUpdateTime).coerceAtMost(100)
        val progress = (elapsed / 100f).coerceIn(0f, 1f)
        

        val distanceToManeuver = if (isNavigationActive && currentStepIndex < navigationSteps.size) {
            val currentStep = navigationSteps[currentStepIndex]
            currentStep.maneuver?.location?.let { loc ->
                if (loc.size >= 2) {
                    val maneuverPoint = GeoPoint(loc[1], loc[0])
                    targetPos.distanceToAsDouble(maneuverPoint)
                } else {
                    null
                }
            }
        } else {
            null
        }

        val motion = MapMotionSmoother.compute(
            currentPosition = mapboxCurrentPosition!!,
            currentBearing = mapboxCurrentBearing,
            targetPosition = targetPos,
            targetBearing = targetBearing,
            progress = progress,
            distanceToManeuver = distanceToManeuver
        )
        

        mapboxCurrentPosition = motion.position
        mapboxCurrentBearing = motion.bearing
        mapboxLastUpdateTime = now
        




        // Location is handled by SDK Location Component (location puck)
        // No need to update custom marker
        

        val currentPosition = motion.position
        val currentBearing = motion.bearing
        

        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val currentZoomValue = mapboxMapView?.mapboxMap?.cameraState?.zoom ?: 17.0
        val currentCameraState = mapboxMapView?.mapboxMap?.cameraState
        val currentCenter = currentCameraState?.center
        val cameraCenter = MapCameraCenterCalculator.compute(
            currentPosition = currentPosition,
            currentBearing = currentBearing,
            isNorthUpMode = isNorthUpMode,
            isLandscape = isLandscape,
            density = resources.displayMetrics.density,
            currentZoomValue = currentZoomValue,
            currentCenterLatitude = currentCenter?.latitude(),
            currentCenterLongitude = currentCenter?.longitude()
        )
        

        updateMapboxMapOrientation()
        

        val point = MapboxPoint.fromLngLat(cameraCenter.longitude, cameraCenter.latitude)
        val cameraBearing = if (isNorthUpMode) {
            0.0
        } else {
            (-currentMapOrientation).toDouble()
        }
        

        val smoothZoom = MapZoomSmoother.compute(currentZoom, targetZoom)
        currentZoom = smoothZoom
        

        val pitch = getCameraPitch()
        


        
        mapboxMapView?.mapboxMap?.setCamera(
            CameraOptions.Builder()
                .center(point)
                .bearing(cameraBearing)
                .zoom(smoothZoom.toDouble())
                .pitch(pitch)
                .build()
        )
    }
    
    private fun updateMapboxMapOrientation() {
        val speed = foregroundService?.getCurrentSpeed() ?: 0f

        currentMapOrientation = MapOrientationSmoother.computeNextOrientation(
            currentOrientation = currentMapOrientation,
            targetOrientation = targetMapOrientation,
            speed = speed
        )
    }

    private fun initializeDirectionsService() {
        try {

            val resourceId = resources.getIdentifier("mapbox_access_token", "string", packageName)
            mapboxAccessToken = resources.getString(resourceId)
            

            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.mapbox.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            
            directionsService = retrofit.create(com.example.clinometer.navigation.MapboxDirectionsService::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun recalculateRoute(currentLocation: GeoPoint) {
        if (navigationDestination == null || directionsService == null || mapboxAccessToken.isEmpty()) {
            return
        }
        
        if (isRerouting) {
            return
        }
        
        isRerouting = true
        
        val currentLat = currentLocation.latitude
        val currentLon = currentLocation.longitude
        val destLat = navigationDestination!!.latitude()
        val destLon = navigationDestination!!.longitude()
        

        val coordinates = "$currentLon,$currentLat;$destLon,$destLat"
        
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    directionsService!!.getRoute(
                        coordinates, 
                        mapboxAccessToken, 
                        alternatives = false, // ВАЖНО: При reroute не искаме alternatives
                        exclude = if (!allowMotorways) "motorway" else null
                    )
                }
                
                if (response.isSuccessful && response.body() != null) {
                    val directionsResponse = response.body()!!
                    val route = directionsResponse.routes.firstOrNull()
                    
                    if (route != null) {

                        val coordinatesList = route.geometry.coordinates.map { coord ->
                            com.mapbox.geojson.Point.fromLngLat(coord[0], coord[1])
                        }
                        navigationRouteGeometry = com.mapbox.geojson.LineString.fromLngLats(coordinatesList)
                        navigationRoutePoints = navigationRouteGeometry?.coordinates() ?: emptyList()
                        

                        navigationSteps = route.legs.flatMap { it.steps }
                        directionsResponseJson = com.google.gson.Gson().toJson(directionsResponse)
                        
                        // ВАЖНО: След reroute, новият маршрут ще стане preferred когато SDK върне NavigationRoute
                        // preferredRoutePolyline ще се зададе в sdkRoutesObserver след като получим polyline string от SDK
                        
                        currentStepIndex = 0
                        

                        routeArrowView = null
                        
                        

                        

                        // Заявка за нов маршрут от SDK
                        // След като SDK върне маршрутите, sdkRoutesObserver ще зададе preferredRoutePolyline
                        if (mapboxMapView != null) {
                            requestInitialSdkRouteIfPossible()
                        }
                    } else {
                    }
                } else {
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isRerouting = false
            }
        }
    }
    
    private fun onDestinationReached() {
        if (hasReachedDestination) return
        hasReachedDestination = true
        
        

        showDestinationReachedNotification()
        

        maneuverContainer?.visibility = View.GONE
        maneuverViewContainer?.visibility = View.GONE
        tripProgressContainer?.visibility = View.GONE
        

        if (mapboxMapView != null) {
            mapboxMapView?.mapboxMap?.getStyle { style ->
                try {

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
                } catch (e: Exception) {
                }
            }
        }
        

        navigationRoutePoints = emptyList()
        navigationRouteGeometry = null
        navigationDestination = null
        navigationDestinationName = ""
        navigationSteps = emptyList()
        currentStepIndex = 0
        directionsResponseJson = null
        navigationOriginLat = 0.0
        navigationOriginLon = 0.0
        navigationOriginBearing = 0f
        

        isNavigationActive = false
        

        if (mapboxMapView != null) {
            val currentCenter = mapboxMapView?.mapboxMap?.cameraState?.center
            if (currentCenter != null) {
                mapboxMapView?.mapboxMap?.setCamera(
                    CameraOptions.Builder()
                        .center(currentCenter)
                        .zoom(currentZoom.toDouble())
                        .pitch(getCameraPitch())
                        .build()
                )
            }
        }
        
    }
    
    private fun showDestinationReachedNotification() {
        val channelId = "destination_reached_channel"
        val notificationId = 1001
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Destination Reached",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Нотификации за достигната дестинация"
                enableVibration(true)
                enableLights(true)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Дестинацията е достигната!")
            .setContentText("Успешно пристигнахте на ${navigationDestinationName.ifEmpty { "дестинацията" }}")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(notificationId, notification)
    }
    
    private fun updateCurrentNavigationStep(currentLocation: GeoPoint) {
        if (navigationSteps.isEmpty() || navigationRoutePoints.isEmpty()) return
        

        if (currentStepIndex >= navigationSteps.size) {
            currentStepIndex = 0
        }
        



        val selection = NavigationStepSelector.select(
            currentLocation = currentLocation,
            navigationSteps = navigationSteps,
            currentStepIndex = currentStepIndex
        )
        val bestStepIndex = selection.stepIndex
        


        if (bestStepIndex != currentStepIndex) {

            if (bestStepIndex > currentStepIndex || (currentStepIndex - bestStepIndex) <= 2) {
                currentStepIndex = bestStepIndex
            }
        }
        

        if (currentStepIndex < navigationSteps.size) {
            val distanceToManeuver = if (currentStepIndex == bestStepIndex) {
                selection.distanceToManeuver
            } else {
                val currentStep = navigationSteps[currentStepIndex]
                RouteMath.calculateDistanceToManeuver(currentLocation, currentStep)
            }
            updateManeuverView(currentStepIndex, distanceToManeuver)
        }
    }

    private fun updateManeuverView(stepIndex: Int, distanceToManeuver: Double = -1.0) {
        if (stepIndex >= navigationSteps.size) {
            maneuverViewContainer?.visibility = View.GONE
            return
        }
        
        val step = navigationSteps[stepIndex]
        maneuverViewContainer?.visibility = View.VISIBLE

        val display = ManeuverDisplayPresenter.build(
            context = this,
            step = step,
            distanceToManeuver = distanceToManeuver
        )

        tvManeuverDistance?.text = display.distanceText
        tvManeuverPrimary?.text = display.primaryText

        if (display.secondaryText != null) {
            tvManeuverSecondary?.text = display.secondaryText
            tvManeuverSecondary?.visibility = View.VISIBLE
        } else {
            tvManeuverSecondary?.visibility = View.GONE
        }

        ivManeuverIcon?.setImageResource(display.iconRes)
    }
    
    private fun updateMapAnimation() {
        // In Mapbox navigation mode, SDK NavigationCamera drives the camera.
        if (isNavigationActive) return
        updateMapboxMapAnimation()
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
    
    private fun updateGaugeAnimation() {
        val diff = targetAngle - currentAngle
        val smoothing = if (abs(diff) > 10) 0.85f else 0.75f
        currentAngle += diff * (1 - smoothing)

        if (abs(diff) < 0.01f) {
            currentAngle = targetAngle
        }


        if (gaugeView?.visibility == View.VISIBLE) {
            gaugeView?.angle = currentAngle
            gaugeView?.invalidate()
        }
        
        if (linearGaugeView?.visibility == View.VISIBLE) {
            linearGaugeView?.angle = currentAngle
            linearGaugeView?.invalidate()
        }
        
        if (linearGaugeViewLandscape?.visibility == View.VISIBLE) {
            linearGaugeViewLandscape?.angle = currentAngle
            linearGaugeViewLandscape?.invalidate()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                if (azimuth < 0) azimuth += 360f
                sensorBearing = SensorMath.smoothBearing(sensorBearing, azimuth)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                gravity = SensorMath.lowPass(event.values.clone(), gravity)
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                geomagnetic = SensorMath.lowPass(event.values.clone(), geomagnetic)

                if (gravity != null && geomagnetic != null) {
                    if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)

                        var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                        if (azimuth < 0) azimuth += 360f
                        sensorBearing = SensorMath.smoothBearing(sensorBearing, azimuth)
                    }
                }
            }
        }
    }




    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == ForegroundService::class.java.name }
    }

    override fun onBackPressed() {
        val hasRecordedPoints = foregroundService?.getRoutePoints()?.isNotEmpty() == true
        val isSessionRunning = foregroundService?.isSessionActive() == true
        if (hasRecordedPoints || isSessionRunning) {
            val exitDialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
                .setTitle(getString(R.string.exit_race_header))
                .setMessage(getString(R.string.exit_race))
                .setPositiveButton(getString(R.string.exit_race_yes)) { _, _ ->

                    stopSessionAndExit()
                }
                .setNegativeButton(getString(R.string.exit_race_no), null)
                .create()
            DialogHelper.styleDialogButtons(exitDialog)
            exitDialog.show()
        } else {
            navigateToRaces()
        }
    }
    
    private fun stopSessionAndExit() {

        foregroundService?.stopMeasurement()

        foregroundService?.resetData()

        cleanupForegroundService()

        navigateToRaces()
    }

    private fun navigateToRaces() {

        val intent = Intent(this, MainContainerActivity::class.java).apply {
            putExtra(MainContainerActivity.EXTRA_INITIAL_PAGE, MainContainerActivity.PAGE_RACES)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        
        // Save chronometer state BEFORE layout reload
        // Always use service start time if service is active - this is the source of truth
        val hasActiveSession = foregroundService != null
        val serviceStartTime = foregroundService?.getStartTime()
        
        // CRITICAL: When configChanges includes orientation, layout is NOT automatically reloaded
        // We must manually reload the correct layout file (portrait or landscape)
        setContentView(R.layout.activity_main)
        
        // Save map state before re-initialization (if using Mapbox)
        var savedMapCenter: com.mapbox.geojson.Point? = null
        var savedMapZoom: Double? = null
        var savedMapBearing: Double? = null
        var savedMapPitch: Double? = null
        
        if (mapboxMapView != null) {
            savedMapCenter = mapboxMapView?.mapboxMap?.cameraState?.center
            savedMapZoom = mapboxMapView?.mapboxMap?.cameraState?.zoom
            savedMapBearing = mapboxMapView?.mapboxMap?.cameraState?.bearing
            savedMapPitch = mapboxMapView?.mapboxMap?.cameraState?.pitch
        }
        
        // Re-initialize ALL views after layout reload
        initializeViews()
        
        // Restore chronometer state after view initialization
        // Always use service start time if service is active - this ensures chronometer continues correctly
        val isLandscape = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (hasActiveSession && serviceStartTime != null) {
            // Service is active - use service start time to maintain continuity
            if (isLandscape) {
                chronometerCarLandscape?.base = serviceStartTime
                chronometerCarLandscape?.start()
            } else {
                if (::chronometerCar.isInitialized) {
                    chronometerCar.base = serviceStartTime
                    chronometerCar.start()
                }
            }
        } else if (hasActiveSession) {
            // Service is active but start time is null - use current time (shouldn't happen, but handle it)
            val startTime = SystemClock.elapsedRealtime()
            if (isLandscape) {
                chronometerCarLandscape?.base = startTime
                chronometerCarLandscape?.start()
            } else {
                if (::chronometerCar.isInitialized) {
                    chronometerCar.base = startTime
                    chronometerCar.start()
                }
            }
        }
        
        // Re-setup buttons and other UI components
        setupButtons()
        setupOrientationToggle()
        setupCameraModeToggle()
        setupNavigationCameraButtons() // CRITICAL: Update button visibility based on navigation state
        
        // Landscape visibility is controlled entirely by XML - only update portrait mode
        if (::currentProfile.isInitialized && !isLandscape) {
            updateUIForProfile()
        } else if (::currentProfile.isInitialized && isLandscape) {
            // In landscape mode, hide zero button and angle display for car mode
            val isMotorcycle = currentProfile.vehicleType == Profile.VehicleType.MOTORCYCLE
            if (!isMotorcycle) {
                // Car mode in landscape: hide zero button and angle display
                zeroButtonOverlay?.visibility = View.GONE
                angleLandscapeContainer?.visibility = View.GONE
            } else {
                // Motorcycle mode in landscape: show zero button and angle display
                zeroButtonOverlay?.visibility = View.VISIBLE
                angleLandscapeContainer?.visibility = View.VISIBLE
            }
        }
        
        setupWindowInsets()
        
        // Force layout pass to ensure all constraints are applied correctly
        // This is critical when returning from landscape to portrait
        // Also ensure tripProgressContainer and mapControlsContainer are properly visible
        findViewById<View>(android.R.id.content)?.post {
            findViewById<View>(android.R.id.content)?.requestLayout()
            // Explicitly ensure mapControlsContainer is visible (it should always be visible)
            mapControlsContainer?.visibility = View.VISIBLE
            // Ensure tripProgressContainer and maneuverContainer visibility is correct based on navigation state
            // Show only in navigation mode, not in normal driving (both portrait and landscape)
            if (::currentProfile.isInitialized) {
                tripProgressContainer?.visibility = if (isNavigationActive) View.VISIBLE else View.GONE
                maneuverContainer?.visibility = if (isNavigationActive) View.VISIBLE else View.GONE
                maneuverViewContainer?.visibility = if (isNavigationActive) View.VISIBLE else View.GONE
                // Also ensure background is removed in normal driving
                if (!isNavigationActive) {
                    maneuverContainer?.setBackgroundResource(0)
                } else {
                    maneuverContainer?.setBackgroundResource(R.drawable.bg_map_controls_pill)
                }
            }
            // CRITICAL: Force trip progress container and its children to remeasure after orientation change
            // This ensures text views are properly sized and don't get cut off with ellipsis
            tripProgressContainer?.let { container ->
                container.requestLayout()
                // Force remeasure of text views inside to ensure proper width calculation
                container.post {
                    tvTripEta?.requestLayout()
                    tvTripRemainingTime?.requestLayout()
                    tvTripRemainingDistance?.requestLayout()
                    // Force a second layout pass to ensure all measurements are correct
                    container.requestLayout()
                }
            }
        }
        
        // Re-attach map view to new container
        if (mapboxMapView != null) {
            val mapContainer = findViewById<FrameLayout>(R.id.mapContainer)
            val parent = mapboxMapView?.parent as? ViewGroup
            parent?.removeView(mapboxMapView)
            mapContainer?.addView(mapboxMapView)
            
            // Restore camera state
            if (savedMapCenter != null && savedMapZoom != null && savedMapBearing != null && savedMapPitch != null) {
                mapboxMapView?.post {
                    mapboxMapView?.mapboxMap?.setCamera(
                        CameraOptions.Builder()
                            .center(savedMapCenter)
                            .zoom(savedMapZoom)
                            .bearing(savedMapBearing)
                            .pitch(savedMapPitch)
                            .build()
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putFloat("currentMapOrientation", currentMapOrientation)
        outState.putBoolean("isFirstLocation", isFirstLocation)
        outState.putDouble("totalDistance", distanceTracker.totalDistanceKm)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        currentMapOrientation = savedInstanceState.getFloat("currentMapOrientation", 0f)
        isFirstLocation = savedInstanceState.getBoolean("isFirstLocation", true)
        distanceTracker.restoreTotalDistance(savedInstanceState.getDouble("totalDistance", 0.0))
        targetMapOrientation = currentMapOrientation
        updateDistanceDisplay()
    }
}