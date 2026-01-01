package com.example.clinometer

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
import android.view.Surface
import android.view.WindowManager
import android.widget.Button
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.ViewGroup
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import com.example.clinometer.settings.MapProviderManager
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
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
// Navigation SDK UI components imports
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.google.gson.Gson
// Route Arrow imports
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.ui.maps.route.arrow.model.ManeuverArrow
// Custom Maneuver View imports
import com.example.clinometer.navigation.DirectionsStep
import com.example.clinometer.navigation.StepManeuver
import androidx.cardview.widget.CardView
import kotlin.math.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.res.Resources
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class KalmanLocationFilter(private val qMetersPerSecond: Float = 3f) {
    private var timestamp = 0L
    private var lat = 0.0
    private var lng = 0.0
    private var variance = -1.0

    fun process(location: Location): Location {
        val accuracy = location.accuracy.toDouble()

        if (variance < 0) {
            timestamp = location.time
            lat = location.latitude
            lng = location.longitude
            variance = accuracy * accuracy
        } else {
            val dt = (location.time - timestamp) / 1000.0
            if (dt > 0) {
                variance += dt * qMetersPerSecond * qMetersPerSecond
                timestamp = location.time
                val k = variance / (variance + accuracy * accuracy)
                lat += k * (location.latitude - lat)
                lng += k * (location.longitude - lng)
                variance = (1 - k) * variance
            }
        }

        return Location(location).apply {
            latitude = lat
            longitude = lng
            time = timestamp
            this.accuracy = sqrt(variance).toFloat()
        }
    }
}

class MotionPredictor {
    private data class MotionState(
        val position: GeoPoint,
        val velocity: DoubleArray,
        val timestamp: Long,
        val bearing: Float,
        val speed: Float
    )

    private val history = mutableListOf<MotionState>()
    private val maxHistory = 5

    fun addSample(position: GeoPoint, bearing: Float, speed: Float) {
        val now = SystemClock.elapsedRealtime()

        val velocity = if (history.isNotEmpty()) {
            val last = history.last()
            val dt = (now - last.timestamp) / 1000.0
            if (dt > 0) {
                doubleArrayOf(
                    (position.latitude - last.position.latitude) / dt,
                    (position.longitude - last.position.longitude) / dt
                )
            } else {
                doubleArrayOf(0.0, 0.0)
            }
        } else {
            doubleArrayOf(0.0, 0.0)
        }

        history.add(MotionState(position, velocity, now, bearing, speed))
        if (history.size > maxHistory) {
            history.removeAt(0)
        }
    }

}

class UltraSmoothLocationOverlay(
    private val mapView: MapView,
    private val locationIcon: Bitmap
) : Overlay() {

    private var currentPos = GeoPoint(0.0, 0.0)
    private var targetPos = GeoPoint(0.0, 0.0)
    private var currentBearing = 0f
    private var targetBearing = 0f
    private var lastUpdateTime = SystemClock.elapsedRealtime()
    private var isInitialized = false
    
    private var velocityLat = 0.0
    private var velocityLon = 0.0
    private var lastTargetPos = GeoPoint(0.0, 0.0)
    private var lastTargetTime = SystemClock.elapsedRealtime()
    
    private var smoothedVelocityLat = 0.0
    private var smoothedVelocityLon = 0.0
    private val velocitySmoothingFactor = 0.7

    private val paint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        isDither = true
    }

    private val interpolator = android.view.animation.AccelerateDecelerateInterpolator()

    fun updateTarget(position: GeoPoint, bearing: Float, immediate: Boolean = false) {
        val currentTime = SystemClock.elapsedRealtime()
        if (isInitialized && !immediate) {
            val timeDiff = (currentTime - lastTargetTime) / 1000.0
            if (timeDiff > 0.01) {
                val newVelocityLat = (position.latitude - lastTargetPos.latitude) / timeDiff
                val newVelocityLon = (position.longitude - lastTargetPos.longitude) / timeDiff
                
                velocityLat = newVelocityLat
                velocityLon = newVelocityLon
                smoothedVelocityLat = smoothedVelocityLat * velocitySmoothingFactor + velocityLat * (1 - velocitySmoothingFactor)
                smoothedVelocityLon = smoothedVelocityLon * velocitySmoothingFactor + velocityLon * (1 - velocitySmoothingFactor)
            }
        }
        
        targetPos = position
        targetBearing = bearing
        lastUpdateTime = currentTime
        lastTargetPos = position
        lastTargetTime = currentTime
        
        if (!isInitialized || immediate) {
            currentPos = position
            currentBearing = bearing
            velocityLat = 0.0
            velocityLon = 0.0
            smoothedVelocityLat = 0.0
            smoothedVelocityLon = 0.0
            isInitialized = true
        }
    }

    fun getCurrentPosition(): GeoPoint = currentPos
    fun getCurrentBearing(): Float = currentBearing

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !isInitialized) return
        
        val now = SystemClock.elapsedRealtime()
        val timeSinceLastUpdate = (now - lastUpdateTime) / 1000.0
        
        val predictedLat = lastTargetPos.latitude + smoothedVelocityLat * timeSinceLastUpdate
        val predictedLon = lastTargetPos.longitude + smoothedVelocityLon * timeSinceLastUpdate
        
        val elapsed = (now - lastUpdateTime).coerceAtMost(100)
        val progress = interpolator.getInterpolation(elapsed / 100f)
        
        currentPos = GeoPoint(
            currentPos.latitude + (predictedLat - currentPos.latitude) * progress * 0.3,
            currentPos.longitude + (predictedLon - currentPos.longitude) * progress * 0.3
        )
        
        var bearingDiff = targetBearing - currentBearing
        while (bearingDiff > 180) bearingDiff -= 360
        while (bearingDiff < -180) bearingDiff += 360
        val bearingSmoothing = when {
            abs(bearingDiff) > 90 -> 0.1f
            abs(bearingDiff) > 45 -> 0.15f
            else -> 0.25f
        }
        
        currentBearing += bearingDiff * bearingSmoothing
        while (currentBearing < 0) currentBearing += 360
        while (currentBearing > 360) currentBearing -= 360
        
        val point = Point()
        mapView.projection.toPixels(currentPos, point)

        canvas.save()
        canvas.rotate(currentBearing, point.x.toFloat(), point.y.toFloat())
        canvas.drawBitmap(
            locationIcon,
            point.x - locationIcon.width / 2f,
            point.y - locationIcon.height / 2f,
            paint
        )
        canvas.restore()
        mapView.postInvalidateDelayed(16)
    }
}

class MainActivity : AppCompatActivity(), SensorEventListener {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private var serviceBound = false
    private var foregroundService: ForegroundService? = null
    private var shouldResetOnConnect = false  // Flag за изчакване на service connection при RESET

    private val renderHandler = Handler(Looper.getMainLooper())

    private val kalmanFilter = KalmanLocationFilter()
    private val motionPredictor = MotionPredictor()
    private var lastProcessedLocation: Location? = null
    private var isFirstLocation = true

    private var totalDistance = 0.0
    private var lastDistancePoint: GeoPoint? = null
    private val distancePoints = mutableListOf<GeoPoint>()

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
    private var needsZeroAfterRotation = true
    private var currentMapOrientation = 0f
    private var targetMapOrientation = 0f
    
    private var targetZoom = 17.5
    private var currentZoom = 17.5
    private var lastZoomChangeTime = 0L
    private val ZOOM_CHANGE_DELAY = 3000L // 3 seconds delay

    private lateinit var currentProfile: Profile
    private lateinit var mapView: MapView // OSMDroid MapView
    private var mapboxMapView: MapboxMapView? = null // Mapbox MapView (nullable)
    private var isMapboxMode = false
    private lateinit var routeOverlay: Polyline
    private lateinit var smoothLocationOverlay: UltraSmoothLocationOverlay
    
    // Navigation variables
    private var isNavigationActive = false
    private var hasReachedDestination = false
    private var navigationRouteGeometry: com.mapbox.geojson.LineString? = null
    private var navigationDestination: com.mapbox.geojson.Point? = null
    private var navigationDestinationName: String = ""
    private var navigationRoutePoints: List<com.mapbox.geojson.Point> = emptyList()
    private var navigationOriginLat: Double = 0.0
    private var navigationOriginLon: Double = 0.0
    private var navigationOriginBearing: Float = 0f
    private var currentRouteStepIndex = 0
    private var directionsResponseJson: String? = null
    
    // Rerouting support
    private var directionsService: com.example.clinometer.navigation.MapboxDirectionsService? = null
    private var mapboxAccessToken: String = ""
    private var isRerouting = false // Prevent multiple simultaneous rerouting requests
    private var lastRerouteTime = 0L // Throttle rerouting requests
    private val REROUTE_THRESHOLD_METERS = 50.0 // Base distance from route to trigger rerouting
    private val REROUTE_THROTTLE_MS = 15000L // Minimum time between rerouting requests (15 seconds)
    private val REROUTE_GRACE_PERIOD_MS = 10000L // Grace period after rerouting (10 seconds) - no off-route checks during this time
    private var rerouteGracePeriodEndTime = 0L // When grace period ends after rerouting
    private var lastRouteCheckTime = 0L // Last time we checked distance to route (throttle checks to once per second)
    private val ROUTE_CHECK_INTERVAL_MS = 1000L // Check distance to route once per second
    
    // Professional off-route detection: confirmation window (must be off-route for X seconds)
    private var offRouteStartTime: Long = 0L // When we first detected being off-route
    private val OFF_ROUTE_CONFIRMATION_MS = 3000L // Must be off-route for 3 seconds before rerouting
    private var lastOffRouteDistance: Double = Double.MAX_VALUE // Track if moving away or toward route
    
    // Mapbox Route Line components
    private var routeLineApi: MapboxRouteLineApi? = null
    private var routeLineView: MapboxRouteLineView? = null
    
    // Mapbox Route Arrow components
    private var routeArrowApi: MapboxRouteArrowApi? = null
    private var routeArrowView: MapboxRouteArrowView? = null
    private var routeArrowsInitialized: Boolean = false
    
    // Custom Maneuver components
    private var maneuverViewContainer: CardView? = null
    private var ivManeuverIcon: ImageView? = null
    private var tvManeuverDistance: TextView? = null
    private var tvManeuverPrimary: TextView? = null
    private var tvManeuverSecondary: TextView? = null

    // Custom Trip Progress views
    private var tripProgressContainer: LinearLayout? = null
    private var tvTripEta: TextView? = null
    private var tvTripRemainingTime: TextView? = null
    private var tvTripRemainingDistance: TextView? = null
    
    // Trip Progress views for landscape mode (in carStatsOverlay)
    private var tripProgressLandscapeContainer: LinearLayout? = null
    private var tvTripEtaLandscape: TextView? = null
    private var tvTripRemainingTimeLandscape: TextView? = null
    private var tvTripRemainingDistanceLandscape: TextView? = null
    private var tripProgressSeparator1: View? = null
    private val tripSpeedWindow = ArrayDeque<Double>()
    private var lastGoodSpeedMps: Double? = null
    private var smoothedRemainingMeters: Double? = null
    private var smoothedRemainingSec: Double? = null
    private var lastTripRemainingSecDisplayed: Long? = null
    private var lastTripEtaDisplayed: Long? = null
    private var lastTripUiUpdateMs: Long = 0L
    private var lastDistanceUpdateMs: Long = 0L
    private var lastMinutesBucket: Int? = null
    private var lastEtaMinutesBucket: Long? = null
    
    // Navigation steps for turn-by-turn instructions
    private var navigationSteps: List<DirectionsStep> = emptyList()
    private var currentStepIndex: Int = 0
    
    // Navigation overlay references for cleanup
    private var navRouteOverlay: org.osmdroid.views.overlay.Polyline? = null
    private var navDestinationMarker: org.osmdroid.views.overlay.Marker? = null
    
    // Mapbox smooth location interpolation (similar to smoothLocationOverlay)
    private var mapboxCurrentPosition: GeoPoint? = null
    private var mapboxCurrentBearing: Float = 0f
    private var mapboxTargetPosition: GeoPoint? = null
    private var mapboxTargetBearing: Float = 0f
    private var mapboxLastUpdateTime: Long = 0L
    
    private var mapboxPointAnnotationManager: PointAnnotationManager? = null
    private var mapboxLocationAnnotation: PointAnnotation? = null

    private lateinit var speedometerBackground: ImageView
    private lateinit var gaugeView: GaugeView
    private lateinit var smallGaugeView: GaugeView
    private lateinit var linearGaugeView: LinearGaugeView
    private lateinit var linearGaugeViewLandscape: LinearGaugeView
    private lateinit var angleTextMotoLandscape: TextView
    private lateinit var angleContainerMoto: LinearLayout
    private lateinit var currentAngleText: TextView
    private lateinit var speedText: TextView
    private lateinit var speedOverlayContainer: LinearLayout
    private lateinit var speedTextCar: TextView
    private lateinit var angleTextMoto: TextView
    private lateinit var chronometerCar: Chronometer
    private lateinit var distanceText: TextView
    private lateinit var distanceTextCar: TextView
    private lateinit var distanceContainer: LinearLayout
    private lateinit var carModeContainer: LinearLayout
    private lateinit var contentArea: ConstraintLayout
    private lateinit var dashboardContainer: ConstraintLayout
    private lateinit var mapControlsContainer: LinearLayout
    private lateinit var buttonContainer: LinearLayout
    private lateinit var carStatsOverlay: LinearLayout
    private var carActionButtonsOverlay: LinearLayout? = null
    private var resetButtonOverlay: ImageButton? = null
    private var zeroButtonOverlay: ImageButton? = null
    private var stopButtonOverlay: ImageButton? = null
    private lateinit var resetButton: Button
    private lateinit var stopButton: Button
    private lateinit var chronometer: Chronometer
    private lateinit var zeroButton: Button
    private var orientationToggle: ImageButton? = null
    private var cameraNorthModeButton: ImageButton? = null
    private var isOrientationLocked: Boolean = false
    private var isNorthUpMode: Boolean = false
    private var lastCalculatedBearing: Float = 0f
    private var baseMapControlsMarginTop = 0
    private var baseMapControlsMarginEnd = 0
    private var baseCarButtonsMarginBottom = 0
    private var baseCarButtonsMarginEnd = 0
    private var baseCarStatsMarginTop = 0
    private var baseCarStatsMarginStart = 0
    private var baseButtonContainerPaddingBottom = 0
    private var baseButtonContainerPaddingLeft = 0
    private var baseButtonContainerPaddingRight = 0
    private var baseSpeedOverlayMarginBottom = 0
    private var baseSpeedOverlayMarginStart = 0
    private lateinit var distanceTextCarLandscape: TextView
    private lateinit var chronometerCarLandscape: Chronometer

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ForegroundService.LocalBinder
            foregroundService = binder.getService()
            serviceBound = true
            
            // Ако има pending reset, го изпълняваме СЕГА че service-ът е свързан
            if (shouldResetOnConnect) {
                shouldResetOnConnect = false
                resetSessionData()
            }
            
            // КРИТИЧНО: При навигация обновяваме UI според профила, НО НЕ ресетваме сесията!
            // (Service-ът запазва състоянието си, затова не рестартираме сесията при повторно свързване)
            if (isNavigationActive) {
                updateUIForProfile() // Показва/скрива ъгли според типа превозно средство
            }
            
            startChronometer()
            startRenderLoop()
            updateAccelerationDisplay(foregroundService?.getAccelerationData() ?: ForegroundService.AccelerationData())

            foregroundService?.getLastLocation()?.let { location ->
                // Показваме картата ако е била скрита (за Mapbox в нормален режим)
                if (isMapboxMode && mapboxMapView != null && mapboxMapView?.visibility != android.view.View.VISIBLE) {
                    mapboxMapView?.visibility = android.view.View.VISIBLE
                    Log.d("MainActivity", "Showing map - location received from service: ${location.latitude}, ${location.longitude}")
                }
                
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                
                // Get current zoom - use stored value if Mapbox, otherwise get from mapView
                val currentZoomValue = if (isMapboxMode) {
                    currentZoom.toDouble()
                } else {
                    if (::mapView.isInitialized) {
                        mapView.zoomLevelDouble
                    } else {
                        currentZoom.toDouble()
                    }
                }
                
                val metersPerPixel = 156543.03392 * cos(Math.toRadians(location.latitude)) / Math.pow(2.0, currentZoomValue)
                val offsetMeters = 30 * resources.displayMetrics.density * metersPerPixel
                
                val bearingRad = Math.toRadians(location.bearing.toDouble())
                val offsetLat = (offsetMeters * cos(bearingRad)) / 111320.0
                val offsetLon = (offsetMeters * sin(bearingRad)) / (111320.0 * cos(Math.toRadians(location.latitude)))
                
                val centerLat = geoPoint.latitude + offsetLat
                val centerLon = geoPoint.longitude + offsetLon
                
                setMapCenter(centerLat, centerLon)
                if (!isMapboxMode && ::smoothLocationOverlay.isInitialized) {
                    smoothLocationOverlay.updateTarget(geoPoint, location.bearing, immediate = true)
                }
                motionPredictor.addSample(geoPoint, location.bearing, location.speed)

                if (lastDistancePoint == null) {
                    lastDistancePoint = geoPoint
                    distancePoints.add(geoPoint)
                }

                isFirstLocation = false
            }

            val existingPoints = foregroundService?.getRoutePoints() ?: emptyList()
            if (existingPoints.isNotEmpty()) {
                totalDistance = 0.0
                distancePoints.clear()
                for (point in existingPoints) {
                    distancePoints.add(point.geoPoint)
                }
                recalculateTotalDistance()
                updateDistanceDisplay()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            foregroundService = null
            stopRenderLoop()
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

        currentProfile = intent.getSerializableExtra("SELECTED_PROFILE") as? Profile
            ?: Profile(name = "My profile", vehicleType = Profile.VehicleType.MOTORCYCLE)

        // Check if navigation is active
        isNavigationActive = intent.getBooleanExtra("navigation_active", false)
        hasReachedDestination = false // Reset when starting new navigation
        Log.d("MainActivity", "Navigation active: $isNavigationActive")
        if (isNavigationActive) {
            val routeGeometryJson = intent.getStringExtra("route_geometry")
            val destLat = intent.getDoubleExtra("destination_latitude", 0.0)
            val destLon = intent.getDoubleExtra("destination_longitude", 0.0)
            val originLat = intent.getDoubleExtra("origin_latitude", 0.0)
            val originLon = intent.getDoubleExtra("origin_longitude", 0.0)
            navigationDestinationName = intent.getStringExtra("destination_name") ?: ""
            directionsResponseJson = intent.getStringExtra("directions_response_json")
            
            // Store origin for instant map centering
            if (originLat != 0.0 && originLon != 0.0) {
                navigationOriginLat = originLat
                navigationOriginLon = originLon
                navigationOriginBearing = intent.getFloatExtra("origin_bearing", 0f)
                // Skip first location initialization - we already have the position
                isFirstLocation = false
                Log.d("MainActivity", "Origin for instant centering: $originLat, $originLon, bearing: $navigationOriginBearing")
            }
            
            Log.d("MainActivity", "Route geometry JSON: ${routeGeometryJson?.take(100)}...")
            Log.d("MainActivity", "Destination: $destLat, $destLon")
            
            if (routeGeometryJson != null) {
                try {
                    navigationRouteGeometry = com.mapbox.geojson.LineString.fromJson(routeGeometryJson)
                    navigationRoutePoints = navigationRouteGeometry?.coordinates() ?: emptyList()
                    Log.d("MainActivity", "Parsed ${navigationRoutePoints.size} route points")
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error parsing route geometry: ${e.message}")
                    e.printStackTrace()
                }
            } else {
                Log.w("MainActivity", "routeGeometryJson is NULL!")
            }
            
            // Parse navigation steps for turn-by-turn instructions
            directionsResponseJson?.let { json ->
                try {
                    val response = Gson().fromJson(json, com.example.clinometer.navigation.DirectionsResponse::class.java)
                    navigationSteps = response.routes.firstOrNull()?.legs?.flatMap { it.steps } ?: emptyList()
                    Log.d("MainActivity", "Parsed ${navigationSteps.size} navigation steps")
                    navigationSteps.forEachIndexed { index, step ->
                        Log.d("MainActivity", "Step $index: ${step.maneuver?.type} ${step.maneuver?.modifier} - ${step.maneuver?.instruction}")
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error parsing navigation steps: ${e.message}")
                }
            }
            
            if (destLat != 0.0 && destLon != 0.0) {
                navigationDestination = com.mapbox.geojson.Point.fromLngLat(destLon, destLat)
            }
        }

        initializeSensors()
        setupScreenKeepOn()
        setupMap()
        
        // Initialize Directions Service for rerouting (only if navigation is active)
        if (isNavigationActive) {
            initializeDirectionsService()
        }
        
        initializeViews()
        updateUIForProfile()
        
        setupButtons()
        setupOrientationToggle()
        setupCameraModeToggle()
        setupWindowInsets()
        needsZeroAfterRotation = true

        if (isServiceRunning()) {
            bindService(Intent(this, ForegroundService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        } else if (isNavigationActive) {
            // Start service for navigation mode
            val serviceIntent = Intent(this, ForegroundService::class.java).apply {
                putExtra("PRE_WARMING_MODE", true)
            }
            androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
            
            // Activate normal mode immediately
            val activateIntent = Intent(this, ForegroundService::class.java).apply {
                putExtra("ACTIVATE_NORMAL_MODE", true)
            }
            startService(activateIntent)
            
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

    }

    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (rotationSensor == null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        }
    }

    private fun initializeViews() {
        speedometerBackground = findViewById(R.id.speedometerBackground)
        chronometer = findViewById(R.id.chronometer)
        chronometerCar = findViewById(R.id.chronometerCar)
        gaugeView = findViewById(R.id.gaugeView)
        smallGaugeView = findViewById(R.id.smallGaugeView)
        currentAngleText = findViewById(R.id.currentAngleText)
        speedText = findViewById(R.id.speedText)
        speedOverlayContainer = findViewById(R.id.speedOverlayContainer)
        speedTextCar = findViewById(R.id.speedTextCar)
        angleContainerMoto = findViewById(R.id.angleContainerMoto)
        angleTextMoto = findViewById(R.id.angleTextMoto)
        linearGaugeView = findViewById(R.id.linearGaugeView)
        angleTextMotoLandscape = findViewById(R.id.angleTextMotoLandscape)
        linearGaugeViewLandscape = findViewById(R.id.linearGaugeViewLandscape)
        distanceText = findViewById(R.id.distanceText)
        
        // Initialize custom maneuver view
        maneuverViewContainer = findViewById(R.id.maneuverViewContainer)
        ivManeuverIcon = findViewById(R.id.ivManeuverIcon)
        tvManeuverDistance = findViewById(R.id.tvManeuverDistance)
        tvManeuverPrimary = findViewById(R.id.tvManeuverPrimary)
        tvManeuverSecondary = findViewById(R.id.tvManeuverSecondary)

        // Custom trip progress views
        tripProgressContainer = findViewById(R.id.tripProgressContainer)
        tvTripEta = findViewById(R.id.tvTripEta)
        tvTripRemainingTime = findViewById(R.id.tvTripRemainingTime)
        tvTripRemainingDistance = findViewById(R.id.tvTripRemainingDistance)
        
        // Trip Progress views for landscape mode
        tripProgressLandscapeContainer = findViewById(R.id.tripProgressLandscapeContainer)
        tvTripEtaLandscape = findViewById(R.id.tvTripEtaLandscape)
        tvTripRemainingTimeLandscape = findViewById(R.id.tvTripRemainingTimeLandscape)
        tvTripRemainingDistanceLandscape = findViewById(R.id.tvTripRemainingDistanceLandscape)
        tripProgressSeparator1 = findViewById(R.id.tripProgressSeparator1)
        
        tripProgressContainer?.visibility = if (isNavigationActive) View.VISIBLE else View.GONE
        
        // Show maneuver view if navigation is active
        if (isNavigationActive && navigationSteps.isNotEmpty()) {
            maneuverViewContainer?.visibility = View.VISIBLE
            updateManeuverView(0) // Show first step
        }
        distanceTextCar = findViewById(R.id.distanceTextCar)
        distanceContainer = findViewById(R.id.distanceContainer)
        carModeContainer = findViewById(R.id.carModeContainer)
        contentArea = findViewById(R.id.contentArea)
        dashboardContainer = findViewById(R.id.dashboardContainer)
        mapControlsContainer = findViewById(R.id.mapControlsContainer)
        buttonContainer = findViewById(R.id.buttonContainer)
        carStatsOverlay = findViewById(R.id.carStatsOverlay)
        carActionButtonsOverlay = findViewById(R.id.carActionButtonsOverlay)
        resetButtonOverlay = findViewById(R.id.btnResetCarMap)
        zeroButtonOverlay = findViewById(R.id.btnZeroCarMap)
        stopButtonOverlay = findViewById(R.id.btnStopCarMap)
        resetButton = findViewById(R.id.btnReset)
        zeroButton = findViewById(R.id.btnZero)
        stopButton = findViewById(R.id.btnStop)
        orientationToggle = findViewById(R.id.btnOrientationToggle)
        cameraNorthModeButton = findViewById(R.id.btnCameraNorthMode)
        distanceTextCarLandscape = findViewById(R.id.distanceTextCarLandscape)
        chronometerCarLandscape = findViewById(R.id.chronometerCarLandscape)

        gaugeView.visibility = View.GONE
        smallGaugeView.visibility = View.GONE
        linearGaugeView.visibility = View.GONE
        linearGaugeViewLandscape.visibility = View.GONE
        angleContainerMoto.visibility = View.GONE
        angleTextMotoLandscape.visibility = View.GONE
        currentAngleText.visibility = View.GONE
        zeroButton.visibility = View.GONE

        currentAngleText.text = getString(R.string.current_angle, 0)
        speedText.text = getString(R.string.current_speed, 0)
        speedOverlayContainer.visibility = View.GONE
        speedTextCar.text = "0"
        angleTextMoto.text = "0°"
        if (::angleTextMotoLandscape.isInitialized) {
            angleTextMotoLandscape.text = "0°"
        }
        distanceText.text = "0.00 km"
        if (::distanceTextCar.isInitialized) {
            distanceTextCar.text = "0.00"
        }
        distanceTextCarLandscape.text = "0.00"
        chronometerCarLandscape.base = SystemClock.elapsedRealtime()

        (mapControlsContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            baseMapControlsMarginTop = lp.topMargin
            baseMapControlsMarginEnd = lp.marginEnd
        }
        (carActionButtonsOverlay?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            baseCarButtonsMarginBottom = lp.bottomMargin
            baseCarButtonsMarginEnd = lp.marginEnd
        }
        (carStatsOverlay.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            baseCarStatsMarginTop = lp.topMargin
            baseCarStatsMarginStart = lp.marginStart
        }
        baseButtonContainerPaddingBottom = buttonContainer.paddingBottom
        baseButtonContainerPaddingLeft = buttonContainer.paddingLeft
        baseButtonContainerPaddingRight = buttonContainer.paddingRight
        (speedOverlayContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            baseSpeedOverlayMarginBottom = lp.bottomMargin
            baseSpeedOverlayMarginStart = lp.marginStart
        }
        updateAccelerationDisplay(ForegroundService.AccelerationData())
    }

    private fun setupScreenKeepOn() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        updateScreenKeepOn(prefs.getBoolean("always_on_display", false))

        prefs.registerOnSharedPreferenceChangeListener { shared, key ->
            if (key == "always_on_display") {
                updateScreenKeepOn(shared.getBoolean(key, false))
            }
        }
    }

    private fun updateScreenKeepOn(keepOn: Boolean) {
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun updateUIForProfile() {
        val isMotorcycle = currentProfile.vehicleType == Profile.VehicleType.MOTORCYCLE
        Log.d("MainActivity", "🎨 updateUIForProfile: isMotorcycle=$isMotorcycle, profile=${currentProfile.name}, vehicleType=${currentProfile.vehicleType}")

        if (isMotorcycle) {
            val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            if (isLandscape) {
                // Landscape motorcycle: align with car layout, add lean angle overlay
                speedometerBackground.visibility = View.GONE
                gaugeView.visibility = View.GONE
                smallGaugeView.visibility = View.GONE
                currentAngleText.visibility = View.GONE
                zeroButton.visibility = View.GONE
                speedText.visibility = View.GONE
                chronometer.visibility = View.GONE
                distanceContainer.visibility = View.GONE
                carModeContainer.visibility = View.GONE
                contentArea.visibility = View.GONE
                dashboardContainer.visibility = View.GONE

                speedOverlayContainer.visibility = View.VISIBLE
                angleTextMotoLandscape.visibility = View.VISIBLE
                linearGaugeViewLandscape.visibility = View.GONE  // Hide gauge in landscape mode
                angleContainerMoto.visibility = View.GONE  // Hide portrait angle container in landscape
                carActionButtonsOverlay?.visibility = View.VISIBLE
                carStatsOverlay.visibility = View.VISIBLE
                zeroButtonOverlay?.visibility = View.VISIBLE
                
                // Настройваме mapControlsContainer marginTop на 110dp в landscape режим (вместо 150dp)
                (mapControlsContainer.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { mlp ->
                    mlp.topMargin = dp(120)
                    mapControlsContainer.layoutParams = mlp
                }
                
                // КРИТИЧНО: При навигация в landscape, настройваме layout параметрите на trip progress и maneuver
                if (isNavigationActive) {
                    // Trip Progress Container: по-къс и с margin отляво
                    tripProgressContainer?.layoutParams?.let { lp ->
                        lp.width = dp(280) // По-къс контейнер
                        tripProgressContainer?.layoutParams = lp
                    }
                    (tripProgressContainer?.layoutParams as? android.widget.FrameLayout.LayoutParams)?.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
                    (tripProgressContainer?.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { mlp ->
                        mlp.marginStart = dp(180)
                        mlp.marginEnd = 0
                        mlp.bottomMargin = dp(30)
                        tripProgressContainer?.layoutParams = mlp
                    }
                    
                    // Maneuver Container: по-къс, центриран по X и вдигнат нагоре
                    maneuverViewContainer?.layoutParams?.let { lp ->
                        lp.width = dp(400) // По-къс контейнер
                        maneuverViewContainer?.layoutParams = lp
                    }
                    (maneuverViewContainer?.layoutParams as? android.widget.FrameLayout.LayoutParams)?.gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                    (maneuverViewContainer?.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { mlp ->
                        mlp.topMargin = dp(40) // 40dp margin top
                        mlp.marginStart = 0 // Центриране - премахваме margins
                        mlp.marginEnd = 0
                        maneuverViewContainer?.layoutParams = mlp
                    }
                }
            } else {
                // Portrait motorcycle: compact UI like car mode, but with small gauge overlay
                speedometerBackground.visibility = View.GONE
                gaugeView.visibility = View.GONE
                currentAngleText.visibility = View.GONE
                zeroButton.visibility = View.VISIBLE  // Show ZERO button in portrait mode
                speedText.visibility = View.GONE
                chronometer.visibility = View.GONE
                distanceContainer.visibility = View.GONE
                carModeContainer.visibility = View.VISIBLE  // Use same layout as car mode
                carModeContainer.setBackgroundResource(R.drawable.bg_car_mode_stats)
                contentArea.visibility = View.GONE
                dashboardContainer.visibility = View.VISIBLE

                speedOverlayContainer.visibility = View.VISIBLE
                angleContainerMoto.visibility = View.VISIBLE  // Show angle with linear gauge on right side
                angleTextMoto.visibility = View.VISIBLE  // Make sure angle text is visible
                linearGaugeView.visibility = View.VISIBLE  // Make sure linear gauge is visible
                carActionButtonsOverlay?.visibility = View.GONE
                carStatsOverlay.visibility = View.GONE
                zeroButtonOverlay?.visibility = View.GONE
                
                // КРИТИЧНО: При навигация в портретен режим, показваме само linear gauge (не полукръгъл)
                if (isNavigationActive) {
                    // Скриваме small gauge (полукръгъл) - искаме само linear gauge
                    smallGaugeView.visibility = View.GONE
                    
                    // Преместваме angleContainerMoto над tripProgressContainer
                    val params = angleContainerMoto.layoutParams as? android.view.ViewGroup.MarginLayoutParams
                    params?.bottomMargin = dp(70) // Позиция над tripProgressContainer
                    angleContainerMoto.layoutParams = params
                } else {
                    // Без навигация - скриваме gauge, запазваме нормалното позициониране на angle
                    smallGaugeView.visibility = View.GONE
                    val params = angleContainerMoto.layoutParams as? android.view.ViewGroup.MarginLayoutParams
                    params?.bottomMargin = dp(0)
                    angleContainerMoto.layoutParams = params
                }
                
                // Восстановяваме нормалните layout параметри за trip progress и maneuver в portrait
                if (isNavigationActive) {
                    tripProgressContainer?.layoutParams?.let { lp ->
                        lp.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                        tripProgressContainer?.layoutParams = lp
                    }
                    (tripProgressContainer?.layoutParams as? android.widget.FrameLayout.LayoutParams)?.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                    (tripProgressContainer?.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { mlp ->
                        mlp.marginStart = dp(12)
                        mlp.marginEnd = dp(12)
                        mlp.bottomMargin = dp(15)
                        tripProgressContainer?.layoutParams = mlp
                    }
                    
                    maneuverViewContainer?.layoutParams?.let { lp ->
                        lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        maneuverViewContainer?.layoutParams = lp
                    }
                    (maneuverViewContainer?.layoutParams as? android.widget.FrameLayout.LayoutParams)?.gravity = android.view.Gravity.TOP
                    (maneuverViewContainer?.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { mlp ->
                        mlp.topMargin = dp(40) // Връщаме към оригиналния margin от layout файла
                        mlp.marginStart = dp(8) // Връщаме оригиналните margins
                        mlp.marginEnd = dp(8)
                        maneuverViewContainer?.layoutParams = mlp
                    }
                }
            }
        } else {
            // Car: hide gauge and show compact stats row - SKRIVAME ВСИЧКИ angle елементи!
            speedometerBackground.visibility = View.GONE
            gaugeView.visibility = View.GONE
            smallGaugeView.visibility = View.GONE
            currentAngleText.visibility = View.GONE
            zeroButton.visibility = View.GONE
            speedText.visibility = View.GONE
            chronometer.visibility = View.GONE
            distanceContainer.visibility = View.GONE
            contentArea.visibility = View.GONE
            speedOverlayContainer.visibility = View.VISIBLE
            angleContainerMoto.visibility = View.GONE
            // Скриваме И portrait И landscape angle елементи за автомобил!
            if (::angleTextMoto.isInitialized) {
                angleTextMoto.visibility = View.GONE
            }
            if (::angleTextMotoLandscape.isInitialized) {
                angleTextMotoLandscape.visibility = View.GONE
            }
            if (::linearGaugeView.isInitialized) {
                linearGaugeView.visibility = View.GONE
            }
            if (::linearGaugeViewLandscape.isInitialized) {
                linearGaugeViewLandscape.visibility = View.GONE
            }

            val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            if (isLandscape) {
                carModeContainer.visibility = View.GONE
                dashboardContainer.visibility = View.GONE
                carActionButtonsOverlay?.visibility = View.VISIBLE
                carStatsOverlay.visibility = View.VISIBLE
                zeroButtonOverlay?.visibility = View.GONE
                
                // Настройваме mapControlsContainer marginTop на 110dp в landscape режим (вместо 150dp)
                (mapControlsContainer.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { mlp ->
                    mlp.topMargin = dp(120)
                    mapControlsContainer.layoutParams = mlp
                }
                
                // КРИТИЧНО: При навигация в landscape за автомобил, настройваме layout параметрите същото като за мотоциклет
                if (isNavigationActive) {
                    // Trip Progress Container: по-къс и с margin отляво
                    tripProgressContainer?.layoutParams?.let { lp ->
                        lp.width = dp(280) // По-къс контейнер
                        tripProgressContainer?.layoutParams = lp
                    }
                    (tripProgressContainer?.layoutParams as? android.widget.FrameLayout.LayoutParams)?.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
                    (tripProgressContainer?.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { mlp ->
                        mlp.marginStart = dp(180)
                        mlp.marginEnd = 0
                        mlp.bottomMargin = dp(30)
                        tripProgressContainer?.layoutParams = mlp
                    }
                    
                    // Maneuver Container: по-къс, центриран по X и вдигнат нагоре
                    maneuverViewContainer?.layoutParams?.let { lp ->
                        lp.width = dp(400) // По-къс контейнер
                        maneuverViewContainer?.layoutParams = lp
                    }
                    (maneuverViewContainer?.layoutParams as? android.widget.FrameLayout.LayoutParams)?.gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                    (maneuverViewContainer?.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { mlp ->
                        mlp.topMargin = dp(40) // 40dp margin top
                        mlp.marginStart = 0 // Центриране - премахваме margins
                        mlp.marginEnd = 0
                        maneuverViewContainer?.layoutParams = mlp
                    }
                }
            } else {
                // Portrait: възстановяваме нормалния marginTop за mapControlsContainer
                (mapControlsContainer.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { mlp ->
                    mlp.topMargin = baseMapControlsMarginTop
                    mapControlsContainer.layoutParams = mlp
                }
                
                carModeContainer.visibility = View.VISIBLE
                carModeContainer.setBackgroundResource(R.drawable.bg_car_mode_stats)
                dashboardContainer.visibility = View.VISIBLE
                carActionButtonsOverlay?.visibility = View.GONE
                carStatsOverlay.visibility = View.GONE
                zeroButtonOverlay?.visibility = View.GONE
            }
        }
    }

    private fun setupMap() {
        // Check which map provider is selected
        val mapProvider = MapProviderManager.getMapProvider(this)
        isMapboxMode = mapProvider == MapProviderManager.MapProvider.MAPBOX
        
        if (isMapboxMode) {
            // Initialize Mapbox
            setupMapboxMap()
        } else {
            // Initialize OSMDroid (default)
            Configuration.getInstance().load(
                applicationContext,
                PreferenceManager.getDefaultSharedPreferences(applicationContext)
            )

            mapView = findViewById(R.id.mapView)
            setupOsmdroidMap()
        }
    }
    
    private fun setupOsmdroidMap() {
        mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(17.5)
            isTilesScaledToDpi = true
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isFlingEnabled = false
        }

        val locationIcon = createHighQualityLocationIcon()
        smoothLocationOverlay = UltraSmoothLocationOverlay(mapView, locationIcon)

        routeOverlay = Polyline().apply {
            outlinePaint.apply {
                strokeWidth = 18f
                color = Color.parseColor("#FF5722")
                alpha = 200
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                pathEffect = CornerPathEffect(20f)
            }
        }

        mapView.overlays.add(routeOverlay)
        mapView.overlays.add(smoothLocationOverlay)
        
        // Add navigation route if active
        if (isNavigationActive && navigationRoutePoints.isNotEmpty()) {
            setupOsmdroidNavigationRoute()
        }
    }
    
    private fun setupOsmdroidNavigationRoute() {
        if (navigationRoutePoints.isEmpty()) return
        
        // Convert Mapbox points to OSMDroid GeoPoints
        val osmdroidPoints = navigationRoutePoints.map { point ->
            GeoPoint(point.latitude(), point.longitude())
        }
        
        // Create polyline for navigation route
        navRouteOverlay = org.osmdroid.views.overlay.Polyline().apply {
            setPoints(osmdroidPoints)
            outlinePaint.apply {
                strokeWidth = 20f
                color = Color.parseColor("#3b9ddd")
                alpha = 200
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        }
        
        mapView.overlays.add(0, navRouteOverlay) // Add at the beginning (below other overlays)
        
        // Add destination marker
        navigationDestination?.let { dest ->
            val destPoint = GeoPoint(dest.latitude(), dest.longitude())
            navDestinationMarker = org.osmdroid.views.overlay.Marker(mapView).apply {
                position = destPoint
                icon = createDestinationMarkerIcon()
                setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
            }
            mapView.overlays.add(navDestinationMarker)
        }
        
        mapView.invalidate()
    }
    
    private fun createDestinationMarkerIcon(): android.graphics.drawable.Drawable {
        val size = (48 * resources.displayMetrics.density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        val paint = android.graphics.Paint().apply {
            color = Color.parseColor("#FF5722")
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        
        val strokePaint = android.graphics.Paint().apply {
            color = Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        
        val centerX = size / 2f
        val centerY = size / 2f
        val radius = size / 3f
        
        canvas.drawCircle(centerX, centerY, radius, paint)
        canvas.drawCircle(centerX, centerY, radius, strokePaint)
        
        return android.graphics.drawable.BitmapDrawable(resources, bitmap)
    }
    
    /**
     * Зарежда Mapbox стил от JSON файл (res/raw/mapbox_style.json)
     * Това заобикаля проблемите с кеширане на стилове
     */
    private fun loadMapboxStyleFromJson(onStyleLoaded: (Style) -> Unit) {
        // Use cached style URL for faster loading (no timestamp = cached)
        val styleUri = "mapbox://styles/djsookz/cmiyer8iu000101s84wqsav5l"
        Log.d("NAV_DEBUG", "⏱️ loadStyleUri START - ${System.currentTimeMillis()}")
        mapboxMapView?.mapboxMap?.loadStyleUri(styleUri) { style ->
            Log.d("NAV_DEBUG", "⏱️ loadStyleUri COMPLETE (style loaded) - ${System.currentTimeMillis()}")
            onStyleLoaded(style)
        }
    }
    
    private fun setupMapboxMap() {
        Log.d("NAV_DEBUG", "⏱️ setupMapboxMap() START - ${System.currentTimeMillis()}")
        val mapContainer = findViewById<android.widget.FrameLayout>(R.id.mapContainer)
        val osmdroidMapView = findViewById<MapView>(R.id.mapView)
        
        // Remove OSMDroid MapView
        mapContainer.removeView(osmdroidMapView)
        
        // Create Mapbox MapView
        mapboxMapView = MapboxMapView(this)
        mapContainer.addView(mapboxMapView)
        Log.d("NAV_DEBUG", "⏱️ MapboxMapView created - ${System.currentTimeMillis()}")
        
        // Set initial camera - use origin (current location) if navigation is active
        val initialCenter = if (isNavigationActive && navigationOriginLat != 0.0 && navigationOriginLon != 0.0) {
            Log.d("MainActivity", "Setting initial camera to origin: $navigationOriginLat, $navigationOriginLon")
            MapboxPoint.fromLngLat(navigationOriginLon, navigationOriginLat)
        } else if (isNavigationActive && navigationRoutePoints.isNotEmpty()) {
            val firstPoint = navigationRoutePoints.first()
            Log.d("MainActivity", "Setting initial camera to route start: ${firstPoint.latitude()}, ${firstPoint.longitude()}")
            MapboxPoint.fromLngLat(firstPoint.longitude(), firstPoint.latitude())
        } else {
            // Първо пробваме service-а, после FusedLocationProviderClient синхронно (ако има permission)
            val lastLocation = foregroundService?.getLastLocation()
            if (lastLocation != null) {
                Log.d("MainActivity", "Setting initial camera to last known location from service: ${lastLocation.latitude}, ${lastLocation.longitude}")
                MapboxPoint.fromLngLat(lastLocation.longitude, lastLocation.latitude)
            } else {
                // Ако няма локация от service-а, не задаваме камера СЪВСЕМ
                // Ще се зададе при първото location update от service-а
                // НЕ показваме София или друга default локация - по-добре да изчакаме реална локация
                Log.d("MainActivity", "No location available yet - camera will be set on first location update")
                null
            }
        }
        
        // Set camera IMMEDIATELY to final position - no animations! (само ако имаме initialCenter)
        if (initialCenter != null) {
            val bearing = if (isNavigationActive && navigationOriginBearing != 0f) {
                navigationOriginBearing.toDouble()
            } else if (isNavigationActive) {
                navigationDestination?.let { dest ->
                    calculateBearingBetweenPoints(navigationOriginLat, navigationOriginLon, dest.latitude(), dest.longitude())
                } ?: 0.0
            } else {
                0.0
            }
            
            Log.d("NAV_DEBUG", "⏱️ Setting camera BEFORE style load - center: ${initialCenter.latitude()}, ${initialCenter.longitude()}, zoom: ${if (isNavigationActive) 19.0 else 17.5}, bearing: $bearing - ${System.currentTimeMillis()}")
            
            mapboxMapView?.mapboxMap?.setCamera(
                CameraOptions.Builder()
                    .center(initialCenter)
                    .zoom(if (isNavigationActive) 19.0 else 17.5)
                    .pitch(getCameraPitch())
                    .bearing(bearing)
                    .build()
            )
            
            Log.d("NAV_DEBUG", "⏱️ Camera set BEFORE style load - ${System.currentTimeMillis()}")
        }
        
        // Load custom map style from JSON (no caching issues!)
        Log.d("NAV_DEBUG", "⏱️ Calling loadMapboxStyleFromJson - ${System.currentTimeMillis()}")
        loadMapboxStyleFromJson { style ->
            Log.d("NAV_DEBUG", "⏱️ Style callback received - ${System.currentTimeMillis()}")
            
            Log.d("NAV_DEBUG", "⏱️ Calling setupMapboxLocationMarker - ${System.currentTimeMillis()}")
            setupMapboxLocationMarker(style)
            Log.d("NAV_DEBUG", "⏱️ setupMapboxLocationMarker done - ${System.currentTimeMillis()}")
            
            // След setupMapboxLocationMarker, проверяваме дали има локация
            // Ако има локация (маркер е създаден), показваме картата
            // Ако няма локация, скриваме картата докато не получим локация
            if (initialCenter == null && mapboxLocationAnnotation == null) {
                Log.d("MainActivity", "No initial location and no marker - hiding map until location is received")
                mapboxMapView?.visibility = android.view.View.INVISIBLE
            } else {
                Log.d("MainActivity", "Location available - showing map")
                mapboxMapView?.visibility = android.view.View.VISIBLE
            }
            if (isNavigationActive && isMapboxMode) {
                Log.d("NAV_DEBUG", "⏱️ Calling setupNavigationRoute - ${System.currentTimeMillis()}")
                setupNavigationRoute(style)
                Log.d("NAV_DEBUG", "⏱️ setupNavigationRoute done - ${System.currentTimeMillis()}")
                Log.d("RouteArrow", "Navigation steps count: ${navigationSteps.size}")
                
                // CRITICAL: Initialize mapbox animation variables BEFORE GPS arrives
                // This prevents the camera from jumping when first GPS data arrives
                val initialPos = GeoPoint(navigationOriginLat, navigationOriginLon)
                mapboxTargetPosition = initialPos
                mapboxCurrentPosition = initialPos
                mapboxTargetBearing = navigationOriginBearing
                mapboxCurrentBearing = navigationOriginBearing
                mapboxLastUpdateTime = android.os.SystemClock.elapsedRealtime()
                
                // Set correct zoom for navigation mode (19.5)
                currentZoom = 19.5
                targetZoom = 19.5
                
                Log.d("NAV_DEBUG", "⏱️ Initialized navigation animation variables - pos: $navigationOriginLat, $navigationOriginLon, bearing: $navigationOriginBearing, zoom: $currentZoom - ${System.currentTimeMillis()}")
                
                // Camera is already set - skip first location initialization
                isFirstLocation = false
                
                // CRITICAL: Update UI based on vehicle type (motorcycle vs car)
                // This ensures lean angle is shown/hidden correctly during navigation
                updateUIForProfile()
            }
        }
        
        // Disable compass and scale bar
        mapboxMapView?.let { mapView ->
            mapView.compass.enabled = false
            mapView.scalebar.enabled = false
        }
    }
    
    private fun calculateBearingBetweenPoints(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        
        val y = Math.sin(dLon) * Math.cos(lat2Rad)
        val x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLon)
        
        var bearing = Math.toDegrees(Math.atan2(y, x))
        if (bearing < 0) bearing += 360.0
        return bearing
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }
    
    private fun setupNavigationRoute(style: Style) {
        if (!isNavigationActive) {
            Log.d("MainActivity", "Navigation not active, skipping route setup")
            return
        }
        
        try {
            // Initialize route line components if not already done
            if (routeLineApi == null || routeLineView == null) {
                Log.d("MainActivity", "Initializing Mapbox Route Line components")
                val routeLineApiOptions = MapboxRouteLineApiOptions.Builder().build()
                routeLineApi = MapboxRouteLineApi(routeLineApiOptions)
                
                val routeLineViewOptions = MapboxRouteLineViewOptions.Builder(this)
                    .routeLineBelowLayerId("road-label") // Position route line below labels
                    .build()
                routeLineView = MapboxRouteLineView(routeLineViewOptions)
            }
            
            // Initialize route arrow components for maneuver arrows
            if (routeArrowApi == null || routeArrowView == null) {
                Log.d("MainActivity", "Initializing Mapbox Route Arrow components")
                routeArrowApi = MapboxRouteArrowApi()
                val routeArrowOptions = RouteArrowOptions.Builder(this)
                    .withSlotName("middle") // Place arrows in middle slot (above roads, below labels)
                    .build()
                routeArrowView = MapboxRouteArrowView(routeArrowOptions)
            }
            
            // Use fallback implementation with improved styling
            // Note: Full NavigationRoute API requires RouteOptions which we don't have from custom API
            Log.d("MainActivity", "Using route line fallback with geometry")
            if (navigationRouteGeometry != null) {
                setupNavigationRouteFallback(style)
            } else {
                Log.w("MainActivity", "No route geometry available")
            }
            
            // Add destination marker
            navigationDestination?.let { dest ->
                // Check if source/layer already exist
                if (!style.styleSourceExists("navigation-destination-source")) {
                    val destFeature = Feature.fromGeometry(dest)
                    val destFeatureCollection = FeatureCollection.fromFeatures(listOf(destFeature))
                    
                    style.addSource(
                        geoJsonSource("navigation-destination-source") {
                            featureCollection(destFeatureCollection)
                        }
                    )
                    
                    style.addLayer(
                        symbolLayer("navigation-destination-layer", "navigation-destination-source") {
                            iconImage("marker-icon")
                            iconSize(1.5)
                            iconAnchor(IconAnchor.BOTTOM)
                        }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up navigation route: ${e.message}")
            e.printStackTrace()
            // Fallback to manual implementation on error
            if (navigationRouteGeometry != null) {
                setupNavigationRouteFallback(style)
            }
        }
    }
    
    private fun setupNavigationRouteFallback(style: Style) {
        // Manual implementation as fallback
        Log.d("MainActivity", "setupNavigationRouteFallback called, geometry: ${navigationRouteGeometry != null}")
        if (navigationRouteGeometry == null) {
            Log.w("MainActivity", "navigationRouteGeometry is null!")
            return
        }
        
        Log.d("MainActivity", "Route has ${navigationRouteGeometry?.coordinates()?.size ?: 0} points")
        
        try {
            // Създаваме нов feature collection с новия маршрут
            val feature = Feature.fromGeometry(navigationRouteGeometry)
            val featureCollection = FeatureCollection.fromFeatures(listOf(feature))
            Log.d("MainActivity", "Created feature collection successfully")
            
            // ВАЖНО: Първо премахваме layers, после обновяваме source (обратен ред за да избегнем грешки)
            // Layers трябва да се премахнат ПРЕДИ да обновим source, иначе Mapbox ще хвърли грешка
            if (style.styleLayerExists("navigation-route-layer")) {
                Log.d("MainActivity", "Removing old route layer...")
                style.removeStyleLayer("navigation-route-layer")
            }
            if (style.styleLayerExists("navigation-route-casing-layer")) {
                Log.d("MainActivity", "Removing old casing layer...")
                style.removeStyleLayer("navigation-route-casing-layer")
            }
            
            // Винаги премахваме и добавяме source отново за да гарантираме че новият маршрут се показва
            // Това е по-надеждно от опитите за обновяване на съществуващия source
            if (style.styleSourceExists("navigation-route-source")) {
                Log.d("MainActivity", "Removing old route source for rerouting...")
                style.removeStyleSource("navigation-route-source")
            }
            
            // Добавяме новия source с новите данни
            Log.d("MainActivity", "Adding new route source with updated route...")
            style.addSource(
                geoJsonSource("navigation-route-source") {
                    featureCollection(featureCollection)
                }
            )
            Log.d("MainActivity", "✅ Route source added successfully")
            
            // Use slot-based approach for Mapbox v11+ with Standard style imports
            // Slot "middle" places layers above roads but below labels
            Log.d("MainActivity", "Adding route layers in slot 'middle'")
            
            // Добавяме route casing layer (border/outline) първо
            style.addLayer(
                lineLayer("navigation-route-casing-layer", "navigation-route-source") {
                    lineColor("#1d5fa3") // Darker blue for casing
                    lineWidth(12.0)
                    lineCap(com.mapbox.maps.extension.style.layers.properties.generated.LineCap.ROUND)
                    lineJoin(com.mapbox.maps.extension.style.layers.properties.generated.LineJoin.ROUND)
                    slot("middle") // Place in middle slot - above roads, below labels
                }
            )
            Log.d("MainActivity", "Added casing layer")
            
            // Добавяме main route layer върху casing-а
            style.addLayer(
                lineLayer("navigation-route-layer", "navigation-route-source") {
                    lineColor("#56A8FB") // Mapbox-style bright blue
                    lineWidth(8.0)
                    lineCap(com.mapbox.maps.extension.style.layers.properties.generated.LineCap.ROUND)
                    lineJoin(com.mapbox.maps.extension.style.layers.properties.generated.LineJoin.ROUND)
                    slot("middle") // Place in middle slot - above roads, below labels
                }
            )
            Log.d("MainActivity", "✅ Route layers added successfully!")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error adding route layer: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun setupMapboxLocationMarker(style: Style) {
        // Create location icon bitmap from drawable
        val locationIconBitmap = createHighQualityLocationIcon()
        
        // Add image to style
        style.addImage("location-icon", locationIconBitmap)
        
        // Create Point Annotation Manager using Annotation API (simpler than Style Layers)
        mapboxMapView?.let { mapView ->
            val annotationApi = mapView.annotations
            mapboxPointAnnotationManager = annotationApi.createPointAnnotationManager()
            
            // Use navigation origin if available, otherwise use last known location from service
            val initialPoint = if (isNavigationActive && navigationOriginLat != 0.0 && navigationOriginLon != 0.0) {
                MapboxPoint.fromLngLat(navigationOriginLon, navigationOriginLat)
            } else {
                // Използваме последната локация от service-а ако има
                val lastLocation = foregroundService?.getLastLocation()
                if (lastLocation != null) {
                    Log.d("MainActivity", "Using last known location for marker: ${lastLocation.latitude}, ${lastLocation.longitude}")
                    MapboxPoint.fromLngLat(lastLocation.longitude, lastLocation.latitude)
                } else {
                    // Ако няма локация, не показваме маркер - ще се покаже при първото location update
                    // НЕ използваме Sofia default
                    Log.d("MainActivity", "No location available yet, marker will be created on first location update")
                    return@let // Не създаваме маркер ако няма локация, но оставяме картата да се покаже ако има initialCenter
                }
            }
            
            // Ако имаме локация за маркера, показваме картата (ако е била скрита)
            if (mapboxMapView?.visibility != android.view.View.VISIBLE) {
                Log.d("MainActivity", "Location marker will be created - showing map")
                mapboxMapView?.visibility = android.view.View.VISIBLE
            }
            
            val initialBearing = if (isNavigationActive && navigationOriginBearing != 0f) {
                navigationOriginBearing.toDouble()
            } else {
                0.0
            }
            
            val pointAnnotationOptions = PointAnnotationOptions()
                .withPoint(initialPoint)
                .withIconImage(locationIconBitmap)
                .withIconSize(1.0)
                .withIconRotate(initialBearing)
            
            mapboxLocationAnnotation = mapboxPointAnnotationManager?.create(pointAnnotationOptions)
            Log.d("MainActivity", "📍 Location marker created at: ${initialPoint.latitude()}, ${initialPoint.longitude()}, bearing: $initialBearing")
        }
    }

    private fun createHighQualityLocationIcon(): Bitmap {
        val size = (48 * resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_navigation)
        if (drawable != null) {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        } else {
            val paint = Paint().apply {
                isAntiAlias = true
                isDither = true
            }

            val centerX = size / 2f
            val centerY = size / 2f
            paint.apply {
                color = Color.argb(50, 0, 0, 0)
                maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawCircle(centerX, centerY + 2, centerX - 6f, paint)
            paint.maskFilter = null
            paint.apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(centerX, centerY, centerX - 4f, paint)
            paint.apply {
                color = Color.argb(100, 0, 0, 0)
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawCircle(centerX, centerY, centerX - 4f, paint)
            val path = Path().apply {
                moveTo(centerX, centerY - 18f)
                lineTo(centerX - 10f, centerY + 18f)
                lineTo(centerX, centerY + 6f)
                lineTo(centerX + 10f, centerY + 18f)
                close()
            }

            paint.apply {
                color = ContextCompat.getColor(this@MainActivity, R.color.accent_blue)
                style = Paint.Style.FILL
                setShadowLayer(2f, 0f, 1f, Color.argb(100, 0, 0, 0))
            }
            canvas.drawPath(path, paint)
        }

        return bitmap
    }

    private fun setupButtons() {
        val resetClickListener = View.OnClickListener {
            if (checkLocationPermission()) {
                if (serviceBound && foregroundService != null) {
                    // Service вече е свързан - reset веднага
                    resetSessionData()
                } else {
                    // Service все още не е свързан - задай flag за reset при connect
                    shouldResetOnConnect = true
                    startAndBindService()
                }
            }
        }
        resetButton.setOnClickListener(resetClickListener)
        resetButtonOverlay?.setOnClickListener(resetClickListener)

        val zeroClickListener = View.OnClickListener {
            if (currentProfile.vehicleType == Profile.VehicleType.MOTORCYCLE) {
                foregroundService?.calibrateZero()
                resetAngleDisplay()
            }
        }
        zeroButton.setOnClickListener(zeroClickListener)
        zeroButtonOverlay?.setOnClickListener(zeroClickListener)

        val stopClickListener = View.OnClickListener {
            if (serviceBound) {
                saveAndFinishSession()
            }
        }
        stopButton.setOnClickListener(stopClickListener)
        stopButtonOverlay?.setOnClickListener(stopClickListener)
    }

    private fun setupOrientationToggle() {
        applyOrientationLock(false)
        orientationToggle?.setOnClickListener {
            isOrientationLocked = !isOrientationLocked
            applyOrientationLock(isOrientationLocked)
        }
    }

    /**
     * Изчислява правилния pitch на базата на режима на камерата
     * - North Up Mode: pitch = 0.0 (няма наклон, вижда само от горе)
     * - Heading Up Mode: pitch = 60.0 (navigation) или 45.0 (normal)
     */
    private fun getCameraPitch(): Double {
        return if (isNorthUpMode) {
            0.0 // North up mode - няма наклон, вижда само от горе
        } else {
            if (isNavigationActive) 60.0 else 45.0 // Heading up mode - нормален наклон
        }
    }
    
    private fun setupCameraModeToggle() {
        updateCameraModeIcon()
        cameraNorthModeButton?.setOnClickListener {
            isNorthUpMode = !isNorthUpMode
            if (isNorthUpMode) {
                targetMapOrientation = 0f
                centerCurrentLocation()
            } else {
                targetMapOrientation = -lastCalculatedBearing
            }
            updateCameraModeIcon()
            
            // Update Mapbox camera bearing and pitch immediately when mode changes
            if (isMapboxMode && mapboxMapView != null) {
                val currentCenter = mapboxMapView?.mapboxMap?.cameraState?.center
                val currentZoom = mapboxMapView?.mapboxMap?.cameraState?.zoom ?: currentZoom.toDouble()
                val bearing = if (isNorthUpMode) 0.0 else (-targetMapOrientation).toDouble()
                val pitch = getCameraPitch()
                
                mapboxMapView?.mapboxMap?.setCamera(
                    CameraOptions.Builder()
                        .center(currentCenter)
                        .zoom(currentZoom)
                        .bearing(bearing)
                        .pitch(pitch)
                        .build()
                )
            }
        }
    }

    private fun setupWindowInsets() {
        val rootContainer = findViewById<View>(R.id.rootContainer)
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            (mapControlsContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.topMargin = baseMapControlsMarginTop + systemBars.top
                lp.marginEnd = baseMapControlsMarginEnd + systemBars.right
                mapControlsContainer.layoutParams = lp
            }

            (speedOverlayContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                val orientation = resources.configuration.orientation
                if (orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
                    val desired = systemBars.bottom - dp(38)
                    lp.bottomMargin = max(0, desired)
                } else {
                    lp.bottomMargin = baseSpeedOverlayMarginBottom + systemBars.bottom
                }
                lp.marginStart = baseSpeedOverlayMarginStart + systemBars.left
                speedOverlayContainer.layoutParams = lp
            }

            carActionButtonsOverlay?.let { overlay ->
                (overlay.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                    lp.bottomMargin = baseCarButtonsMarginBottom + systemBars.bottom
                    lp.marginEnd = baseCarButtonsMarginEnd + systemBars.right
                    overlay.layoutParams = lp
                }
            }

            (carStatsOverlay.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.topMargin = baseCarStatsMarginTop + systemBars.top
                lp.marginStart = baseCarStatsMarginStart + systemBars.left
                carStatsOverlay.layoutParams = lp
            }

            buttonContainer.setPadding(
                baseButtonContainerPaddingLeft + systemBars.left,
                buttonContainer.paddingTop,
                baseButtonContainerPaddingRight + systemBars.right,
                baseButtonContainerPaddingBottom + systemBars.bottom
            )

            insets
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private fun updateCameraModeIcon() {
        if (isNorthUpMode) {
            cameraNorthModeButton?.setImageResource(R.drawable.ic_map_compass)
            cameraNorthModeButton?.imageAlpha = 255
        } else {
            cameraNorthModeButton?.setImageResource(R.drawable.ic_map_heading)
            cameraNorthModeButton?.imageAlpha = (255 * 0.85f).toInt()
        }
    }

    private fun centerCurrentLocation() {
        if (!isMapboxMode) {
            val position = if (!isMapboxMode && ::smoothLocationOverlay.isInitialized) {
                smoothLocationOverlay.getCurrentPosition()
            } else {
                GeoPoint(0.0, 0.0) // Fallback
            }
            if (!position.latitude.isNaN() && !position.longitude.isNaN()) {
                animateMapTo(position.latitude, position.longitude)
            }
        }
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

    // Helper functions for map operations (work with both OSMDroid and Mapbox)
    private fun setMapCenter(lat: Double, lon: Double) {
        Log.d("NAV_DEBUG", "⏱️ setMapCenter called - $lat, $lon - ${System.currentTimeMillis()}")
        if (isMapboxMode) {
            val pitch = getCameraPitch()
            mapboxMapView?.mapboxMap?.setCamera(
                CameraOptions.Builder()
                    .center(MapboxPoint.fromLngLat(lon, lat))
                    .zoom(currentZoom.toDouble())
                    .pitch(pitch)
                    .build()
            )
        } else {
            if (::mapView.isInitialized) {
                mapView.controller.setCenter(GeoPoint(lat, lon))
            }
        }
    }
    
    private fun animateMapTo(lat: Double, lon: Double) {
        Log.d("NAV_DEBUG", "⏱️ animateMapTo called - $lat, $lon - ${System.currentTimeMillis()}")
        if (isMapboxMode) {
            val pitch = getCameraPitch()
            mapboxMapView?.mapboxMap?.setCamera(
                CameraOptions.Builder()
                    .center(MapboxPoint.fromLngLat(lon, lat))
                    .zoom(currentZoom.toDouble())
                    .pitch(pitch)
                    .build()
            )
        } else {
            if (::mapView.isInitialized) {
                mapView.controller.animateTo(GeoPoint(lat, lon))
            }
        }
    }
    
    private fun setMapZoom(zoom: Double) {
        Log.d("NAV_DEBUG", "⏱️ setMapZoom called - $zoom - ${System.currentTimeMillis()}")
        currentZoom = zoom
        if (isMapboxMode) {
            val pitch = getCameraPitch()
            mapboxMapView?.mapboxMap?.setCamera(
                CameraOptions.Builder()
                    .zoom(zoom)
                    .pitch(pitch)
                    .build()
            )
        } else {
            if (::mapView.isInitialized) {
                mapView.controller.setZoom(zoom)
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        if (isMapboxMode) {
            mapboxMapView?.onStart()
        }
    }
    
    override fun onStop() {
        super.onStop()
        // Гарантирай, че останалите екрани не наследяват заключената ориентация
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
        val serviceIntent = Intent(this, ForegroundService::class.java).apply {
            putExtra("ACTIVATE_NORMAL_MODE", true)  // 🔥 ВАЖНО: Активирай NORMAL режим
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun resetSessionData() {
        foregroundService?.resetData()
        resetAngleDisplay()
        updateAccelerationDisplay(ForegroundService.AccelerationData())

        // ВАЖНО: Взимаме actualStartTime от сървиза СЛЕД resetData()
        val startTime = foregroundService?.getStartTime() ?: SystemClock.elapsedRealtime()
        chronometer.base = startTime
        chronometer.start()
        if (::chronometerCar.isInitialized) {
            chronometerCar.base = startTime
            chronometerCar.start()
        }
        chronometerCarLandscape.base = startTime
        chronometerCarLandscape.start()

        targetAngle = 0f
        currentAngle = 0f
        currentMapOrientation = 0f
        targetMapOrientation = 0f
        isFirstLocation = true

        totalDistance = 0.0
        lastDistancePoint = null
        distancePoints.clear()
        updateDistanceDisplay()

        // Only clear route overlay if we're in OSMDroid mode and it's initialized
        if (!isMapboxMode && ::routeOverlay.isInitialized && ::mapView.isInitialized) {
            routeOverlay.points.clear()
            mapView.invalidate()
        }
        motionPredictor.addSample(GeoPoint(0.0, 0.0), 0f, 0f)
    }

    private fun resetAngleDisplay() {
        currentAngleText.text = getString(R.string.current_angle, 0)
        angleTextMoto.text = "0°"
        if (::angleTextMotoLandscape.isInitialized) {
            angleTextMotoLandscape.text = "0°"
        }

        gaugeView.apply {
            angle = 0f
            maxLeftAngle = 0f
            maxRightAngle = 0f
            resetMaxima()
            invalidate()
        }
        
        // Reset small gauge if visible
        if (::smallGaugeView.isInitialized && smallGaugeView.visibility == View.VISIBLE) {
            smallGaugeView.apply {
                angle = 0f
                maxLeftAngle = 0f
                maxRightAngle = 0f
                resetMaxima()
                invalidate()
            }
        }
        
        // Reset linear gauge if visible
        if (::linearGaugeView.isInitialized && linearGaugeView.visibility == View.VISIBLE) {
            linearGaugeView.apply {
                angle = 0f
                maxLeftAngle = 0f
                maxRightAngle = 0f
                resetMaxima()
                invalidate()
            }
        }
        
        // Reset landscape linear gauge if visible
        if (::linearGaugeViewLandscape.isInitialized && linearGaugeViewLandscape.visibility == View.VISIBLE) {
            linearGaugeViewLandscape.apply {
                angle = 0f
                maxLeftAngle = 0f
                maxRightAngle = 0f
                resetMaxima()
                invalidate()
            }
        }
        
        // Reset landscape angle text
        if (::angleTextMotoLandscape.isInitialized) {
            angleTextMotoLandscape.text = "0°"
        }
    }

    private fun initializeFirstLocation(location: Location) {
        if (isFirstLocation) {
            val geoPoint = GeoPoint(location.latitude, location.longitude)
            
            // Показваме картата ако е била скрита (за Mapbox)
            if (isMapboxMode && mapboxMapView != null) {
                mapboxMapView?.visibility = android.view.View.VISIBLE
                Log.d("MainActivity", "Showing map - first location received: ${location.latitude}, ${location.longitude}")
            }
            
            // Use currentZoom variable instead of mapView.zoomLevelDouble (works for both OSMDroid and Mapbox)
            val zoomLevel = if (!isMapboxMode && ::mapView.isInitialized) {
                mapView.zoomLevelDouble
            } else {
                currentZoom
            }
            val metersPerPixel = 156543.03392 * cos(Math.toRadians(location.latitude)) / Math.pow(2.0, zoomLevel)
            val offsetMeters = 30 * resources.displayMetrics.density * metersPerPixel
            
            val bearingRad = Math.toRadians(location.bearing.toDouble())
            val offsetLat = (offsetMeters * cos(bearingRad)) / 111320.0
            val offsetLon = (offsetMeters * sin(bearingRad)) / (111320.0 * cos(Math.toRadians(location.latitude)))
            
            val centerLat = geoPoint.latitude + offsetLat
            val centerLon = geoPoint.longitude + offsetLon
            
            setMapCenter(centerLat, centerLon)
            if (!isMapboxMode && ::smoothLocationOverlay.isInitialized) {
                smoothLocationOverlay.updateTarget(geoPoint, location.bearing, immediate = true)
            }
            motionPredictor.addSample(geoPoint, location.bearing, location.speed)

            lastDistancePoint = geoPoint
            distancePoints.add(geoPoint)

            isFirstLocation = false
        }
    }

    private fun updateDistance(newPoint: GeoPoint) {
        if (lastDistancePoint != null) {
            val distance = lastDistancePoint!!.distanceToAsDouble(newPoint)

            if (distance > 1.0) {
                totalDistance += distance / 1000.0
                lastDistancePoint = newPoint
                distancePoints.add(newPoint)
                updateDistanceDisplay()
            }
        } else {
            lastDistancePoint = newPoint
            distancePoints.add(newPoint)
        }
    }

    private fun recalculateTotalDistance() {
        totalDistance = 0.0
        if (distancePoints.size >= 2) {
            for (i in 1 until distancePoints.size) {
                totalDistance += distancePoints[i - 1].distanceToAsDouble(distancePoints[i]) / 1000.0
            }
        }
    }

    private fun updateDistanceDisplay() {
        distanceText.text = "%.2f km".format(totalDistance)
        if (::distanceTextCar.isInitialized) {
            distanceTextCar.text = "%.2f".format(totalDistance)
        }
        distanceTextCarLandscape.text = "%.2f".format(totalDistance)
    }

    private fun saveAndFinishSession() {
        try {
            val rawRoutePoints = foregroundService?.getFinalRoutePoints() ?: emptyList()

            // Професионална проверка: Минимум 3 точки са необходими за валиден маршрут
            if (rawRoutePoints.isEmpty()) {
                handleEmptySession()
                return
            }
            
            if (rawRoutePoints.size < 3) {
                Log.w("MainActivity", "⚠️ Insufficient route points: ${rawRoutePoints.size} (minimum: 3)")
                cleanupForegroundService()
                Toast.makeText(
                    this, 
                    getString(R.string.error_no_route_data), 
                    Toast.LENGTH_LONG
                ).show()
                
                // Връщаме потребителя в началната страница
                startActivity(Intent(this, MainContainerActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("INITIAL_PAGE", MainContainerActivity.PAGE_MAP)
                })
                overridePendingTransition(0, 0)
                finish()
                return
            }

            val race = createRaceFromSession()

            // Save race data FIRST (with raw route points)
            RouteStorage.saveRoutePoints(this, race.id, rawRoutePoints)
            val allRaces = RouteStorage.loadRaces(this).toMutableList()
            allRaces.add(race)
            RouteStorage.saveRaces(this, allRaces)

            cleanupForegroundService()

            // Navigate to SaveSessionActivity first (like Strava and other professional apps)
            // User can add name, description, and photos before processing
            val intent = Intent(this, SaveSessionActivity::class.java).apply {
                putExtra("raceId", race.id)
            }
            startActivity(intent)
            finish() // Close MainActivity
        } catch (e: Exception) {
            Log.e("MainActivity", "Error saving race", e)
            showError("Error saving the race: ${e.message}")
        }
    }

    private fun createRaceFromSession(): Race {
        val routePoints = foregroundService?.getFinalRoutePoints() ?: emptyList()
        val sessionNumber = getNextSessionNumber()
        
        // Check vehicle type - only save angles for motorcycles
        val isMotorcycle = currentProfile.vehicleType == Profile.VehicleType.MOTORCYCLE
        val maxLeftAngle = if (isMotorcycle) (foregroundService?.getMaxLeftAngle() ?: 0f) else 0f
        val maxRightAngle = if (isMotorcycle) (foregroundService?.getMaxRightAngle() ?: 0f) else 0f

        val raceId = System.currentTimeMillis()
        Log.d("MainActivity", "💾 Creating race: id=$raceId, profileId=${currentProfile.id}, name=Session $sessionNumber, points=${routePoints.size}, isMotorcycle=$isMotorcycle, maxLeft=$maxLeftAngle, maxRight=$maxRightAngle")

        return Race(
            profileId = currentProfile.id,
            id = raceId,
            routePoints = routePoints,
            timestamp = System.currentTimeMillis(),
            duration = foregroundService?.getServiceDuration() ?: 0,
            absoluteTimestamp = System.currentTimeMillis(),
            maxLeftAngle = maxLeftAngle,
            maxRightAngle = maxRightAngle,
            maxSpeed = foregroundService?.getMaxSpeed() ?: 0f,
            name = "Session $sessionNumber",
            distance = totalDistance,
            time0to100 = 0L,
            time0to200 = 0L,
            time100to200 = 0L
        )
    }


    private fun getNextSessionNumber(): Int {
        val allRaces = RouteStorage.loadRaces(this)
        val profileRaces = allRaces.filter { it.profileId == currentProfile.id }
        
        Log.d("MainActivity", "📊 getNextSessionNumber: total races=${allRaces.size}, profile races=${profileRaces.size}, profileId=${currentProfile.id}")
        
        val sessionNumbers = profileRaces.mapNotNull { race ->
            race.name?.let { name ->
                if (name.startsWith("Session ")) {
                    val num = name.substringAfter("Session ").toIntOrNull()
                    Log.d("MainActivity", "   Found session: $name -> number=$num")
                    num
                } else {
                    Log.d("MainActivity", "   Skipping session (doesn't match pattern): $name")
                    null
                }
            }
        }
        
        val maxNumber = sessionNumbers.maxOrNull()
        val nextNumber = maxNumber?.plus(1) ?: 1
        Log.d("MainActivity", "📊 Next session number: $nextNumber (max found: $maxNumber)")
        return nextNumber
    }

    private fun cleanupForegroundService() {
        try {
            if (serviceBound) {
                try {
                    unbindService(serviceConnection)
                } catch (e: IllegalArgumentException) {
                    Log.w("MainActivity", "Service already unbound in cleanup", e)
                }
            }
            try {
                stopService(Intent(this, ForegroundService::class.java))
            } catch (e: Exception) {
                Log.w("MainActivity", "Error stopping service in cleanup", e)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error during service cleanup", e)
        } finally {
            serviceBound = false
        }
    }

    private fun cleanupAndNavigate(raceId: Long) {
        cleanupForegroundService()

        startActivity(Intent(this, MapActivity::class.java).apply {
            putExtra("RACE_ID", raceId)
        })
        overridePendingTransition(0, 0)
        finish()
    }

    private fun handleEmptySession() {
        cleanupForegroundService()
        Toast.makeText(this, getString(R.string.error_no_route_data), Toast.LENGTH_LONG).show()

        startActivity(Intent(this, MainContainerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("NAV_ITEM_ID", R.id.navMap)
        })
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
        if (!isMapboxMode) {
            mapView.onResume()
        }
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
        if (!isMapboxMode) {
            mapView.onPause()
        }
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Cancel route line components
        routeLineApi?.cancel()
        routeLineView?.cancel()
        routeArrowApi = null
        routeArrowView = null
        
        if (isMapboxMode) {
            mapboxMapView?.onDestroy()
        }
        stopRenderLoop()
        if (serviceBound) {
            try {
                unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
                Log.w("MainActivity", "Service already unbound in onDestroy", e)
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
        // Винаги използваме startTime от service-а (той запазва времето при orientation change)
        val startTime = foregroundService?.getStartTime() ?: SystemClock.elapsedRealtime()
        chronometer.base = startTime
        chronometer.start()
        if (::chronometerCar.isInitialized) {
            chronometerCar.base = startTime
            chronometerCar.start()
        }
        chronometerCarLandscape.base = startTime
        chronometerCarLandscape.start()
    }

    private fun updateAccelerationDisplay(accelData: ForegroundService.AccelerationData) {
        // Performance metrics removed - no longer needed
        updateProfileBestTimes(accelData)
    }

    private fun updateProfileBestTimes(accelData: ForegroundService.AccelerationData) {
        // Only update max speed - performance metrics removed
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

            currentAngleText.text = getString(R.string.current_angle, angle.toInt())

            val speed = service.getCurrentSpeed()
            speedText.text = getString(R.string.current_speed, speed.toInt())
            if (::speedTextCar.isInitialized) {
                speedTextCar.text = speed.toInt().toString()
            }
            // Use the same smoothed currentAngle that the gauge uses for consistent behavior
            // The gauge already smooths the angle in updateGaugeAnimation(), so we use currentAngle directly
            // Only update UI text if the rounded value has changed (reduces flickering)
            val roundedAngle = currentAngle.toInt()
            val currentDisplayText = "${roundedAngle}°"
            if (angleTextMoto.text != currentDisplayText) {
                angleTextMoto.text = currentDisplayText
            }
            if (::angleTextMotoLandscape.isInitialized && angleTextMotoLandscape.text != currentDisplayText) {
                angleTextMotoLandscape.text = currentDisplayText
            }

            gaugeView.maxLeftAngle = service.getMaxLeftAngle()
            gaugeView.maxRightAngle = service.getMaxRightAngle()
            
            // Update small gauge if visible
            if (::smallGaugeView.isInitialized && smallGaugeView.visibility == View.VISIBLE) {
                smallGaugeView.maxLeftAngle = service.getMaxLeftAngle()
                smallGaugeView.maxRightAngle = service.getMaxRightAngle()
            }
            
            // Update linear gauge if visible
            if (::linearGaugeView.isInitialized && linearGaugeView.visibility == View.VISIBLE) {
                linearGaugeView.maxLeftAngle = service.getMaxLeftAngle()
                linearGaugeView.maxRightAngle = service.getMaxRightAngle()
            }
            
            // Update landscape linear gauge if visible
            if (::linearGaugeViewLandscape.isInitialized && linearGaugeViewLandscape.visibility == View.VISIBLE) {
                linearGaugeViewLandscape.maxLeftAngle = service.getMaxLeftAngle()
                linearGaugeViewLandscape.maxRightAngle = service.getMaxRightAngle()
            }

            service.getLastLocation()?.let { location ->
                processLocationUpdate(location, speed)
            } ?: run {
                // If navigation is active, use the navigation origin as fallback location
                if (isNavigationActive && navigationOriginLat != 0.0 && navigationOriginLon != 0.0) {
                    // Create a synthetic location from navigation origin
                    val syntheticLocation = android.location.Location("navigation")
                    syntheticLocation.latitude = navigationOriginLat
                    syntheticLocation.longitude = navigationOriginLon
                    syntheticLocation.bearing = navigationOriginBearing
                    syntheticLocation.speed = 0f
                    syntheticLocation.accuracy = 10f
                    syntheticLocation.time = System.currentTimeMillis()
                    processLocationUpdate(syntheticLocation, speed)
                }
                // Don't spam logs
                // Log.w("MainActivity", "⚠️ UI Update - No location available!")
            }

            updateAccelerationDisplay(service.getAccelerationData())
        } ?: run {
            Log.w("MainActivity", "⚠️ UI Update - No service available!")
        }
    }

    private fun processLocationUpdate(location: Location, speed: Float) {
        // ПРОФЕСИОНАЛНО РЕШЕНИЕ: Използваме RAW GPS за navigation logic, филтрирана за визуализация
        val rawGeoPoint = GeoPoint(location.latitude, location.longitude) // RAW GPS - за navigation logic
        val filtered = kalmanFilter.process(location)
        val geoPoint = GeoPoint(filtered.latitude, filtered.longitude) // Filtered - за визуализация
        
        // Update navigation progress if active - ИЗПОЛЗВАМЕ RAW GPS за точност!
        if (isNavigationActive) {
            updateNavigationProgress(rawGeoPoint) // RAW GPS за точно разстояние до маневри
        }

        if (isFirstLocation) {
            initializeFirstLocation(filtered)
            return
        }

        updateDistance(geoPoint)

        var calculatedBearing = location.bearing

        if (lastProcessedLocation != null && speed > 1) {
            val lastGeoPoint = GeoPoint(
                lastProcessedLocation!!.latitude,
                lastProcessedLocation!!.longitude
            )

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
                    speed > 20 -> movementBearing * 0.1f + location.bearing * 0.9f
                    speed > 5 -> movementBearing * 0.5f + location.bearing * 0.5f
                    else -> location.bearing
                }
            }
        }

        lastProcessedLocation = filtered

        if (!isMapboxMode && ::smoothLocationOverlay.isInitialized) {
            smoothLocationOverlay.updateTarget(geoPoint, calculatedBearing, immediate = false)
        } else if (isMapboxMode) {
            // Update Mapbox target position and bearing (will be smoothly interpolated in updateMapboxMapAnimation)
            mapboxTargetPosition = geoPoint
            mapboxTargetBearing = calculatedBearing
        }

        if (!isMapboxMode) {
            updateRoute(geoPoint, speed)
        }

        lastCalculatedBearing = calculatedBearing

        if (isNorthUpMode) {
            targetMapOrientation = 0f
        } else if (speed > 2) {
            targetMapOrientation = -calculatedBearing
        }
        
        // Mapbox camera update is handled in updateMapAnimation() (called from render loop)
        
        updateZoomBasedOnSpeed(speed)
    }
    
    private fun updateMapboxLocationMarker(geoPoint: GeoPoint, bearing: Float) {
        val point = MapboxPoint.fromLngLat(geoPoint.longitude, geoPoint.latitude)
        
        mapboxLocationAnnotation?.let { annotation ->
            // Update annotation position and rotation
            annotation.point = point
            annotation.iconRotate = bearing.toDouble()
            mapboxPointAnnotationManager?.update(annotation)
        } ?: run {
            // Create annotation if it doesn't exist
            val pointAnnotationOptions = PointAnnotationOptions()
                .withPoint(point)
                .withIconImage("location-icon")
                .withIconSize(1.0)
                .withIconRotate(bearing.toDouble())
            
            mapboxLocationAnnotation = mapboxPointAnnotationManager?.create(pointAnnotationOptions)
        }
    }
    
    private fun updateMapboxCameraBearing() {
        if (!isMapboxMode) return
        
        val bearing = if (isNorthUpMode) {
            0.0
        } else {
            (-targetMapOrientation).toDouble()
        }
        
        val pitch = getCameraPitch()
        mapboxMapView?.mapboxMap?.setCamera(
            CameraOptions.Builder()
                .bearing(bearing)
                .pitch(pitch)
                .build()
        )
    }
    
    private fun updateMapboxMapAnimation() {
        if (!isMapboxMode || mapboxMapView == null) return
        
        // Don't update camera until we have a valid position
        val targetPos = mapboxTargetPosition ?: return
        
        // Only log every 500ms to reduce spam
        // Log.d("NAV_DEBUG", "⏱️ updateMapboxMapAnimation called - ${System.currentTimeMillis()}")
        
        // Smooth interpolation for location marker (same as smoothLocationOverlay)
        val targetBearing = mapboxTargetBearing
        
        // Initialize if first time
        if (mapboxCurrentPosition == null) {
            mapboxCurrentPosition = targetPos
            mapboxCurrentBearing = targetBearing
            mapboxLastUpdateTime = SystemClock.elapsedRealtime()
            updateMapboxLocationMarker(targetPos, targetBearing)
        }
        
        val now = SystemClock.elapsedRealtime()
        val elapsed = (now - mapboxLastUpdateTime).coerceAtMost(100)
        val progress = (elapsed / 100f).coerceIn(0f, 1f)
        
        // АДАПТИВНО СГЛАЖДАНЕ: При навигация намаляваме забавянето, особено близо до маневри
        val smoothingFactor = if (isNavigationActive && currentStepIndex < navigationSteps.size) {
            // Проверяваме дали сме близо до маневър
            val currentStep = navigationSteps[currentStepIndex]
            val distanceToManeuver = currentStep.maneuver?.location?.let { loc ->
                if (loc.size >= 2) {
                    val maneuverPoint = GeoPoint(loc[1], loc[0])
                    targetPos.distanceToAsDouble(maneuverPoint)
                } else {
                    null
                }
            } ?: Double.MAX_VALUE
            
            // По-малко сглаждане при навигация, още по-малко близо до маневри
            when {
                distanceToManeuver < 50.0 -> 0.7f  // Много близо до маневър - почти RAW GPS
                distanceToManeuver < 100.0 -> 0.5f  // Близо до маневър - умерено сглаждане
                else -> 0.4f  // Нормално каране - по-малко сглаждане от нормалния режим
            }
        } else {
            0.3f  // Нормален режим - пълно сглаждане за плавност
        }
        
        // Smooth position interpolation с адаптивен фактор
        val currentPos = mapboxCurrentPosition!!
        val smoothNewLat = currentPos.latitude + (targetPos.latitude - currentPos.latitude) * progress * smoothingFactor
        val smoothNewLon = currentPos.longitude + (targetPos.longitude - currentPos.longitude) * progress * smoothingFactor
        val smoothPosition = GeoPoint(smoothNewLat, smoothNewLon)
        
        // Smooth bearing interpolation (same as smoothLocationOverlay)
        var bearingDiff = targetBearing - mapboxCurrentBearing
        while (bearingDiff > 180f) bearingDiff -= 360f
        while (bearingDiff < -180f) bearingDiff += 360f
        val bearingSmoothing = when {
            abs(bearingDiff) > 90f -> 0.1f
            abs(bearingDiff) > 45f -> 0.15f
            else -> 0.25f
        }
        val smoothBearing = mapboxCurrentBearing + bearingDiff * bearingSmoothing
        val normalizedBearing = ((smoothBearing % 360f) + 360f) % 360f
        
        // Update current values
        mapboxCurrentPosition = smoothPosition
        mapboxCurrentBearing = normalizedBearing
        mapboxLastUpdateTime = now
        
        // Update location marker with smooth values
        // In Mapbox, iconRotate is relative to the map (not absolute north)
        // When the map rotates with bearing in CameraOptions, annotations rotate with it automatically
        // Formula: relativeBearing = (bearing - cameraBearing + 360) % 360
        val mapCameraBearing = if (isNorthUpMode) 0.0 else (-currentMapOrientation).toDouble()
        val relativeBearing = ((normalizedBearing - mapCameraBearing + 360.0) % 360.0).toFloat()
        updateMapboxLocationMarker(smoothPosition, relativeBearing)
        
        // EXACT SAME LOGIC AS OSMDROID - 1:1 copy
        val currentPosition = smoothPosition
        val currentBearing = normalizedBearing
        
        // Check if in landscape mode
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        
        // Get zoom level (same as OSMDroid)
        val currentZoomValue = if (isMapboxMode) {
            mapboxMapView?.mapboxMap?.cameraState?.zoom ?: 17.0
        } else {
            currentZoom
        }
        
        val metersPerPixel = 156543.03392 * cos(Math.toRadians(currentPosition.latitude)) / Math.pow(2.0, currentZoomValue)
        var offsetMeters = 30 * resources.displayMetrics.density * metersPerPixel
        if (isNorthUpMode || isLandscape) {
            // In landscape mode, always center (no offset) so triangle is visible
            offsetMeters = 0.0
        }
        
        val bearingRad = Math.toRadians(currentBearing.toDouble())
        val offsetLat = (offsetMeters * cos(bearingRad)) / 111320.0
        val offsetLon = (offsetMeters * sin(bearingRad)) / (111320.0 * cos(Math.toRadians(currentPosition.latitude)))
        
        val newLat = currentPosition.latitude + offsetLat
        val newLon = currentPosition.longitude + offsetLon
        
        // Get current camera center
        val currentCameraState = mapboxMapView?.mapboxMap?.cameraState
        val currentCenter = currentCameraState?.center
        
        val smoothCameraLat: Double
        val smoothCameraLon: Double
        
        if (currentCenter == null) {
            // First time - set directly
            smoothCameraLat = newLat
            smoothCameraLon = newLon
        } else {
            val currentCenterLat = currentCenter.latitude()
            val currentCenterLon = currentCenter.longitude()
            
            val latDiff = newLat - currentCenterLat
            val lonDiff = newLon - currentCenterLon
            
            // Smoother camera following (0.12 factor)
            smoothCameraLat = currentCenterLat + latDiff * 0.12
            smoothCameraLon = currentCenterLon + lonDiff * 0.12
        }
        
        // Update map orientation (same as OSMDroid)
        updateMapboxMapOrientation()
        
        // Set camera center with smooth interpolation
        val point = MapboxPoint.fromLngLat(smoothCameraLon, smoothCameraLat)
        val cameraBearing = if (isNorthUpMode) {
            0.0
        } else {
            (-currentMapOrientation).toDouble()
        }
        
        // Smooth zoom interpolation (same as OSMDroid)
        val zoomDiff = targetZoom - currentZoom
        val smoothZoom = if (abs(zoomDiff) > 0.01) {
            currentZoom + zoomDiff * 0.08 // Same smoothing factor as OSMDroid
        } else {
            currentZoom
        }
        
        // Update currentZoom if changed
        if (abs(zoomDiff) > 0.01) {
            currentZoom = smoothZoom
        }
        
        // Use correct pitch based on camera mode
        val pitch = getCameraPitch()
        
        // Log only significant zoom changes
        // Log.d("NAV_DEBUG", "⏱️ updateMapboxMapAnimation setCamera - center: ${point.latitude()}, ${point.longitude()}, zoom: $smoothZoom, pitch: $pitch - ${System.currentTimeMillis()}")
        
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
        if (!isMapboxMode) return
        
        // Same logic as OSMDroid updateMapOrientation()
        var diff = targetMapOrientation - currentMapOrientation
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        
        val speed = foregroundService?.getCurrentSpeed() ?: 0f
        
        val smoothingFactor = when {
            abs(diff) > 90f -> 0.15f
            abs(diff) > 45f -> 0.12f
            abs(diff) > 20f -> 0.08f
            speed > 50 -> 0.06f
            speed > 20 -> 0.05f
            else -> 0.04f
        }
        
        if (abs(diff) > 0.5f) {
            currentMapOrientation += diff * smoothingFactor
            while (currentMapOrientation > 360f) currentMapOrientation -= 360f
            while (currentMapOrientation < 0f) currentMapOrientation += 360f
        }
    }

    private fun updateRoute(geoPoint: GeoPoint, speed: Float) {
        // In navigation mode, we don't draw the traveled route, only show the navigation route
        if (isNavigationActive) {
            return
        }
        
        if (isMapboxMode || !::routeOverlay.isInitialized) {
            // Route overlay is only for OSMDroid
            return
        }
        if (routeOverlay.points.isEmpty()) {
            routeOverlay.points.add(geoPoint)
        } else {
            val lastPoint = routeOverlay.points.last()
            val distance = geoPoint.distanceToAsDouble(lastPoint)

            val minDistance = when {
                speed > 50 -> 2.0
                speed > 20 -> 1.5
                speed > 2 -> 1.0
                else -> 5.0
            }

            if (distance > minDistance) {
                routeOverlay.points.add(geoPoint)
            }
        }
    }
    
    /**
     * Calculate perpendicular distance from point to line segment
     * This is the REAL distance from route, not just distance to closest point
     */
    private fun distanceToLineSegment(point: GeoPoint, lineStart: GeoPoint, lineEnd: GeoPoint): Double {
        val x = point.longitude
        val y = point.latitude
        val x1 = lineStart.longitude
        val y1 = lineStart.latitude
        val x2 = lineEnd.longitude
        val y2 = lineEnd.latitude
        
        val A = x - x1
        val B = y - y1
        val C = x2 - x1
        val D = y2 - y1
        
        val dot = A * C + B * D
        val lenSq = C * C + D * D
        
        var param = -1.0
        if (lenSq != 0.0) {
            param = dot / lenSq
        }
        
        val xx: Double
        val yy: Double
        
        when {
            param < 0 -> {
                xx = x1
                yy = y1
            }
            param > 1 -> {
                xx = x2
                yy = y2
            }
            else -> {
                xx = x1 + param * C
                yy = y1 + param * D
            }
        }
        
        val dx = x - xx
        val dy = y - yy
        
        // Convert to meters
        val latMeters = dy * 111320.0
        val lonMeters = dx * 111320.0 * Math.cos(Math.toRadians(point.latitude))
        
        return Math.sqrt(latMeters * latMeters + lonMeters * lonMeters)
    }
    
    /**
     * Find minimum perpendicular distance to the ENTIRE route path
     * This checks if we're off the ROAD, not just far from a point
     */
    private fun getMinimumDistanceToRoute(currentLocation: GeoPoint): Pair<Double, Int> {
        if (navigationRoutePoints.size < 2) return Pair(Double.MAX_VALUE, 0)
        
        var minDistance = Double.MAX_VALUE
        var closestSegmentIndex = 0
        
        // Check distance to each segment of the route
        for (i in 0 until navigationRoutePoints.size - 1) {
            val p1 = navigationRoutePoints[i]
            val p2 = navigationRoutePoints[i + 1]
            
            val segmentStart = GeoPoint(p1.latitude(), p1.longitude())
            val segmentEnd = GeoPoint(p2.latitude(), p2.longitude())
            
            val distance = distanceToLineSegment(currentLocation, segmentStart, segmentEnd)
            
            if (distance < minDistance) {
                minDistance = distance
                closestSegmentIndex = i
            }
        }
        
        return Pair(minDistance, closestSegmentIndex)
    }
    
    private fun updateNavigationProgress(currentLocation: GeoPoint) {
        if (navigationRoutePoints.isEmpty()) return
        
        // КРИТИЧНО: Използваме перпендикулярно разстояние до ЛИНИЯТА на маршрута
        // Това е РЕАЛНОТО разстояние от пътя, не разстояние до точка
        val (minDistanceToRoute, closestSegmentIndex) = getMinimumDistanceToRoute(currentLocation)
        
        // Find closest POINT index for step progression
        var closestPointIndex = 0
        var minPointDistance = Double.MAX_VALUE
        navigationRoutePoints.forEachIndexed { index, routePoint ->
            val distance = currentLocation.distanceToAsDouble(
                GeoPoint(routePoint.latitude(), routePoint.longitude())
            )
            if (distance < minPointDistance) {
                minPointDistance = distance
                closestPointIndex = index
            }
        }
        
        val now = System.currentTimeMillis()
        
        // КРИТИЧНО: Grace period след рероутиране
        val inGracePeriod = now < rerouteGracePeriodEndTime
        
        // ВАЖНО: Проверяваме за off-route само ако НЕ сме в grace period
        if (!inGracePeriod) {
            // OPTIMIZATION: Check distance to route only once per second
            val timeSinceLastCheck = now - lastRouteCheckTime
            if (timeSinceLastCheck >= ROUTE_CHECK_INTERVAL_MS) {
                lastRouteCheckTime = now
                
                // Professional off-route detection using perpendicular distance
                val shouldCheckRerouting = !isRerouting && !hasReachedDestination
                val timeSinceLastReroute = now - lastRerouteTime
                
                if (shouldCheckRerouting && timeSinceLastReroute > REROUTE_THROTTLE_MS) {
                // Check distance to destination
                val distanceToDestination = navigationDestination?.let { dest ->
                    currentLocation.distanceToAsDouble(GeoPoint(dest.latitude(), dest.longitude()))
                } ?: Double.MAX_VALUE
                
                if (distanceToDestination > 200.0) {
                    // Simple fixed threshold - if we're more than 50m from the ROAD, we're off-route
                    // Не зависи от скорост - 50m е достатъчно за всякакви условия
                    val OFF_ROUTE_THRESHOLD = 50.0
                    
                    Log.d("MainActivity", "📍 Distance to route: ${minDistanceToRoute.toInt()}m (threshold: ${OFF_ROUTE_THRESHOLD.toInt()}m)")
                    
                    if (minDistanceToRoute > OFF_ROUTE_THRESHOLD) {
                        // Check if moving toward route
                        val isMovingTowardRoute = if (lastOffRouteDistance != Double.MAX_VALUE) {
                            (lastOffRouteDistance - minDistanceToRoute) > 5.0
                        } else {
                            false
                        }
                        
                        if (isMovingTowardRoute) {
                            offRouteStartTime = 0L
                            lastOffRouteDistance = minDistanceToRoute
                            Log.d("MainActivity", "✅ Moving toward route - resetting off-route timer")
                        } else {
                            if (offRouteStartTime == 0L) {
                                offRouteStartTime = now
                                lastOffRouteDistance = minDistanceToRoute
                                Log.d("MainActivity", "⚠️ Off-route detected - starting confirmation window (${minDistanceToRoute.toInt()}m from route)")
                            } else {
                                val timeOffRoute = now - offRouteStartTime
                                lastOffRouteDistance = minDistanceToRoute
                                
                                // Confirmation window: must be off-route for 3 seconds
                                if (timeOffRoute >= OFF_ROUTE_CONFIRMATION_MS) {
                                    Log.d("MainActivity", "🚨 Off-route CONFIRMED after ${timeOffRoute/1000}s: ${minDistanceToRoute.toInt()}m from route. Rerouting...")
                                    recalculateRoute(currentLocation)
                                } else {
                                    Log.d("MainActivity", "⏳ Off-route confirmation: ${timeOffRoute/1000}s / ${OFF_ROUTE_CONFIRMATION_MS/1000}s (${minDistanceToRoute.toInt()}m)")
                                }
                            }
                        }
                    } else {
                        // We're on the route - reset everything
                        if (offRouteStartTime != 0L) {
                            Log.d("MainActivity", "✅ Back on route - distance: ${minDistanceToRoute.toInt()}m")
                        }
                        offRouteStartTime = 0L
                        lastOffRouteDistance = Double.MAX_VALUE
                    }
                } else {
                    offRouteStartTime = 0L
                    lastOffRouteDistance = Double.MAX_VALUE
                }
                }
            }
        } else {
            // В grace period - само ресетваме off-route променливите, но НЕ спираме обновяването на маневъра
            offRouteStartTime = 0L
            lastOffRouteDistance = Double.MAX_VALUE
            val remainingSeconds = (rerouteGracePeriodEndTime - now) / 1000
            if (remainingSeconds % 5 == 0L || remainingSeconds <= 3) { // Log every 5s or last 3s
                Log.d("MainActivity", "⏰ Grace period: ${remainingSeconds}s remaining, distance to route: ${minDistanceToRoute.toInt()}m")
            }
        }
        
        // Update step progression (use closest point index)
        if (closestPointIndex > currentRouteStepIndex) {
            currentRouteStepIndex = closestPointIndex
        }
        
        // Find the current navigation step based on location
        updateCurrentNavigationStep(currentLocation)
        
        // Update route arrow on map (only when approaching a turn)
        updateRouteArrow(currentLocation)

        // Update custom trip progress bar
        updateCustomTripProgress(currentLocation)
        
        // Check if we've reached the destination
        if (!hasReachedDestination) {
            navigationDestination?.let { dest ->
                val distanceToDest = currentLocation.distanceToAsDouble(
                    GeoPoint(dest.latitude(), dest.longitude())
                )
                if (distanceToDest < 50.0) { // Within 50 meters
                    onDestinationReached()
                }
            }
        }
    }
    
    /**
     * Initialize Mapbox Directions Service for rerouting
     */
    private fun initializeDirectionsService() {
        try {
            // Get Mapbox access token
            val resourceId = resources.getIdentifier("mapbox_access_token", "string", packageName)
            mapboxAccessToken = resources.getString(resourceId)
            
            // Initialize Retrofit for Directions API
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.mapbox.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            
            directionsService = retrofit.create(com.example.clinometer.navigation.MapboxDirectionsService::class.java)
            Log.d("MainActivity", "✅ Directions Service initialized for rerouting")
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error initializing Directions Service: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Recalculate route from current location to destination
     */
    private fun recalculateRoute(currentLocation: GeoPoint) {
        if (navigationDestination == null || directionsService == null || mapboxAccessToken.isEmpty()) {
            Log.w("MainActivity", "Cannot reroute: missing destination, service, or token")
            return
        }
        
        if (isRerouting) {
            Log.d("MainActivity", "Rerouting already in progress, skipping...")
            return
        }
        
        isRerouting = true
        // НЕ обновяваме lastRerouteTime тук - ще го обновим СЛЕД успешен рероутинг
        // ВАЖНО: Ресетваме off-route променливите ПРЕДИ рероутинг за да не се задейства повторно
        offRouteStartTime = 0L
        lastOffRouteDistance = Double.MAX_VALUE
        
        val currentLat = currentLocation.latitude
        val currentLon = currentLocation.longitude
        val destLat = navigationDestination!!.latitude()
        val destLon = navigationDestination!!.longitude()
        
        // Build coordinates string for API: "lon1,lat1;lon2,lat2"
        val coordinates = "$currentLon,$currentLat;$destLon,$destLat"
        
        Log.d("MainActivity", "🔄 Recalculating route from ($currentLat, $currentLon) to ($destLat, $destLon)")
        
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    directionsService!!.getRoute(
                        coordinates, 
                        mapboxAccessToken, 
                        alternatives = false, // Only get primary route for rerouting
                        exclude = null // Use motorway preference from saved route if needed
                    )
                }
                
                if (response.isSuccessful && response.body() != null) {
                    val directionsResponse = response.body()!!
                    val route = directionsResponse.routes.firstOrNull()
                    
                    if (route != null) {
                        // Update route data
                        val coordinatesList = route.geometry.coordinates.map { coord ->
                            com.mapbox.geojson.Point.fromLngLat(coord[0], coord[1])
                        }
                        navigationRouteGeometry = com.mapbox.geojson.LineString.fromLngLats(coordinatesList)
                        navigationRoutePoints = navigationRouteGeometry?.coordinates() ?: emptyList()
                        
                        // Update navigation steps
                        navigationSteps = route.legs.flatMap { it.steps }
                        
                        // Update directions response JSON
                        directionsResponseJson = com.google.gson.Gson().toJson(directionsResponse)
                        
                        // ЕЛЕМЕНТАРНО: Ресетваме всичко като първоначално зареждане
                        currentRouteStepIndex = 0
                        currentStepIndex = 0
                        
                        // ВАЖНО: Премахваме старите стрелки и създаваме нови
                        routeArrowApi = null
                        routeArrowView = null
                        routeArrowsInitialized = false
                        
                        Log.d("MainActivity", "✅ Route recalculated: ${navigationRoutePoints.size} points, ${navigationSteps.size} steps")
                        
                        // ВАЖНО: Задай grace period
                        rerouteGracePeriodEndTime = System.currentTimeMillis() + REROUTE_GRACE_PERIOD_MS
                        lastRerouteTime = System.currentTimeMillis()
                        
                        // Нулирай off-route променливите
                        offRouteStartTime = 0L
                        lastOffRouteDistance = Double.MAX_VALUE
                        
                        Log.d("MainActivity", "🔄 Rerouting completed. Grace period: ${REROUTE_GRACE_PERIOD_MS/1000}s. New route: ${navigationRoutePoints.size} points")
                        
                        // Update route on map (ще инициализира отново всичко)
                        if (isMapboxMode && mapboxMapView != null) {
                            mapboxMapView?.mapboxMap?.getStyle { style ->
                                setupNavigationRoute(style)
                                
                                // След като route е начертан, преизчисляваме стрелките и маневрите
                                foregroundService?.getLastLocation()?.let { lastLoc ->
                                    val currentGeoPoint = GeoPoint(lastLoc.latitude, lastLoc.longitude)
                                    updateRouteArrow(currentGeoPoint)
                                    // ВАЖНО: Извикваме updateCurrentNavigationStep за да изчисли правилно разстоянието и маневъра
                                    updateCurrentNavigationStep(currentGeoPoint)
                                }
                            }
                        }
                    } else {
                        Log.w("MainActivity", "No route found in rerouting response")
                    }
                } else {
                    Log.e("MainActivity", "Rerouting failed: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error recalculating route: ${e.message}")
                e.printStackTrace()
            } finally {
                isRerouting = false
                // ВАЖНО: Ресетваме off-route променливите след рероутинг (успешен или неуспешен)
                // Това предотвратява повторни рероутинги веднага след като новият маршрут е зареден
                // Grace period-ът (REROUTE_THROTTLE_MS) гарантира че няма да има повторно рероутиране за 15 секунди
                offRouteStartTime = 0L
                lastOffRouteDistance = Double.MAX_VALUE
            }
        }
    }
    
    /**
     * Called when destination is reached.
     * Shows notification, clears navigation data, removes route overlays, and returns to normal session.
     */
    private fun onDestinationReached() {
        if (hasReachedDestination) return // Already handled
        hasReachedDestination = true
        
        Log.d("MainActivity", "Destination reached! Clearing navigation...")
        
        // Show notification
        showDestinationReachedNotification()
        
        // Hide navigation UI elements
        maneuverViewContainer?.visibility = View.GONE
        tripProgressContainer?.visibility = View.GONE
        
        // Clear navigation route overlays from OSMDroid
        if (!isMapboxMode && ::mapView.isInitialized) {
            navRouteOverlay?.let { mapView.overlays.remove(it) }
            navDestinationMarker?.let { mapView.overlays.remove(it) }
            navRouteOverlay = null
            navDestinationMarker = null
            mapView.invalidate()
        }
        
        // Clear navigation route from Mapbox
        if (isMapboxMode && mapboxMapView != null) {
            mapboxMapView?.mapboxMap?.getStyle { style ->
                try {
                    // Remove route layers
                    if (style.styleLayerExists("navigation-route-layer")) {
                        style.removeStyleLayer("navigation-route-layer")
                    }
                    if (style.styleLayerExists("navigation-route-casing-layer")) {
                        style.removeStyleLayer("navigation-route-casing-layer")
                    }
                    // Remove route source
                    if (style.styleSourceExists("navigation-route-source")) {
                        style.removeStyleSource("navigation-route-source")
                    }
                    // Remove destination marker
                    if (style.styleLayerExists("navigation-destination-layer")) {
                        style.removeStyleLayer("navigation-destination-layer")
                    }
                    if (style.styleSourceExists("navigation-destination-source")) {
                        style.removeStyleSource("navigation-destination-source")
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error removing navigation layers: ${e.message}")
                }
            }
        }
        
        // Clear navigation data
        navigationRoutePoints = emptyList()
        navigationRouteGeometry = null
        navigationDestination = null
        navigationDestinationName = ""
        navigationSteps = emptyList()
        currentStepIndex = 0
        currentRouteStepIndex = 0
        directionsResponseJson = null
        navigationOriginLat = 0.0
        navigationOriginLon = 0.0
        navigationOriginBearing = 0f
        
        // Reset navigation state
        isNavigationActive = false
        
        // Reset camera to normal mode (correct pitch based on camera mode, normal zoom)
        if (isMapboxMode && mapboxMapView != null) {
            val currentCenter = mapboxMapView?.mapboxMap?.cameraState?.center
            if (currentCenter != null) {
                mapboxMapView?.mapboxMap?.setCamera(
                    CameraOptions.Builder()
                        .center(currentCenter)
                        .zoom(currentZoom.toDouble())
                        .pitch(getCameraPitch()) // Use correct pitch based on camera mode
                        .build()
                )
            }
        } else if (!isMapboxMode && ::mapView.isInitialized) {
            // For OSMDroid, just ensure normal zoom
            mapView.controller.setZoom(currentZoom.toInt())
        }
        
        Log.d("MainActivity", "Navigation cleared. Returned to normal session mode.")
    }
    
    /**
     * Shows a notification when destination is reached.
     */
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
        
        // ВАЖНО: Ако currentStepIndex е извън границите (напр. след рероутинг), принудително го ресетваме
        if (currentStepIndex >= navigationSteps.size) {
            currentStepIndex = 0
        }
        
        // КЛЮЧОВА ЛОГИКА: Винаги намираме правилния step според текущата локация
        // Проверяваме ВСИЧКИ steps и намираме първия, до който все още не сме стигнали (>= 10m)
        // Започваме от currentStepIndex, но ако пропуснем няколко завоя, проверяваме всички
        var bestStepIndex = currentStepIndex
        var bestDistance = Double.MAX_VALUE
        
        // Стратегия: Започваме от текущия step и проверяваме напред
        // Ако не намерим подходящ (>= 10m), проверяваме всички от началото
        for (i in currentStepIndex until navigationSteps.size) {
            val step = navigationSteps[i]
            val distanceToManeuver = calculateDistanceToManeuver(currentLocation, step)
            
            // Ако сме минали този step (< 10m), продължаваме към следващия
            if (distanceToManeuver < 10.0) {
                continue
            }
            
            // Намерихме step който все още не сме минали - това е правилният
            bestStepIndex = i
            bestDistance = distanceToManeuver
            break
        }
        
        // Ако не намерихме подходящ step напред, проверяваме ВСИЧКИ от началото
        // Това се случва когато сме пропуснали няколко завоя или currentStepIndex е останал назад
        if (bestDistance == Double.MAX_VALUE) {
            for (i in navigationSteps.indices) {
                val step = navigationSteps[i]
                val distanceToManeuver = calculateDistanceToManeuver(currentLocation, step)
                
                // Ако сме минали този step (< 10m), продължаваме
                if (distanceToManeuver < 10.0) {
                    continue
                }
                
                // Намерихме step който все още не сме минали - това е правилният
                bestStepIndex = i
                bestDistance = distanceToManeuver
                break
            }
        }
        
        // Ако сме минали ВСИЧКИ steps (всички са < 10m), използваме последния
        if (bestDistance == Double.MAX_VALUE && navigationSteps.isNotEmpty()) {
            bestStepIndex = navigationSteps.size - 1
            bestDistance = calculateDistanceToManeuver(currentLocation, navigationSteps[bestStepIndex])
        }
        
        // Обновяваме currentStepIndex само ако е различен и новият е по-напред
        // Това предотвратява връщане назад (освен ако наистина не е нужно)
        if (bestStepIndex != currentStepIndex) {
            // Позволяваме само движение напред или ако новият step е много по-близо (пропуснат завой)
            if (bestStepIndex > currentStepIndex || (currentStepIndex - bestStepIndex) <= 2) {
                currentStepIndex = bestStepIndex
                Log.d("MainActivity", "📍 Updated step index to $currentStepIndex (distance: ${bestDistance.toInt()}m)")
            }
        }
        
        // Calculate distance to next maneuver and update UI
        if (currentStepIndex < navigationSteps.size) {
            val currentStep = navigationSteps[currentStepIndex]
            val distanceToManeuver = calculateDistanceToManeuver(currentLocation, currentStep)
            updateManeuverView(currentStepIndex, distanceToManeuver)
        }
    }

    /**
     * Custom Trip Progress: ETA, remaining time, remaining distance
     * Uses navigationRoutePoints geometry and current speed.
     */
    private fun updateCustomTripProgress(currentLocation: GeoPoint) {
        if (!isNavigationActive || navigationRoutePoints.isEmpty()) {
            tripProgressContainer?.visibility = View.GONE
            return
        }

        val now = System.currentTimeMillis()

        // Find closest point index on route
        var closestIndex = 0
        var minDistance = Double.MAX_VALUE
        navigationRoutePoints.forEachIndexed { index, routePoint ->
            val d = currentLocation.distanceToAsDouble(GeoPoint(routePoint.latitude(), routePoint.longitude()))
            if (d < minDistance) {
                minDistance = d
                closestIndex = index
            }
        }

        // Sum remaining distance from closest index to end
        var remainingMeters = 0.0
        for (i in closestIndex until navigationRoutePoints.size - 1) {
            val p1 = navigationRoutePoints[i]
            val p2 = navigationRoutePoints[i + 1]
            remainingMeters += haversineDistance(p1.latitude(), p1.longitude(), p2.latitude(), p2.longitude())
        }

        // Smooth remaining distance (EMA) to reduce sudden drops/jumps
        smoothedRemainingMeters = when {
            smoothedRemainingMeters == null -> remainingMeters
            else -> smoothedRemainingMeters!! * 0.9 + remainingMeters * 0.1
        }
        val stableRemainingMeters = smoothedRemainingMeters ?: remainingMeters

        // Current speed (km/h) and smoothed m/s
        // КРИТИЧНО: ВИНАГИ използваме реалната текуща скорост за точно изчисление на ETA
        val speedKmh = foregroundService?.getCurrentSpeed() ?: 0f
        val rawSpeedMps = (speedKmh / 3.6).toDouble()
        
        // Добавяме скоростта в прозореца за smoothing (включително и 0)
        tripSpeedWindow.addLast(rawSpeedMps)
        if (tripSpeedWindow.size > 20) tripSpeedWindow.removeFirst()
        
        // Изчисляваме smoothed speed от прозореца
        val smoothedSpeedMps = if (tripSpeedWindow.isNotEmpty()) {
            tripSpeedWindow.average()
        } else {
            rawSpeedMps
        }
        
        // Запазваме последната добра скорост (ако е по-голяма от 0.5 m/s)
        if (smoothedSpeedMps > 0.5) {
            lastGoodSpeedMps = smoothedSpeedMps
        }
        
        // Използваме реалната smoothed скорост (включително и 0)
        // В началото (когато няма скорост) използваме разумна начална скорост за да покажем ETA веднага
        val initialSpeedMps = 13.9 // ~50 km/h - разумна начална скорост за градско каране
        val effectiveSpeedMps = when {
            smoothedSpeedMps >= 0.1 -> smoothedSpeedMps // Използваме реалната скорост дори и ако е малка
            lastGoodSpeedMps != null && lastGoodSpeedMps!! > 0.5 -> lastGoodSpeedMps!! // Използваме последната добра скорост
            else -> initialSpeedMps // В началото използваме начална скорост за да покажем ETA веднага
        }

        // If almost at destination, force zero
        if (stableRemainingMeters < 50.0) {
            // Винаги показваме данните в tripProgressContainer
            tripProgressContainer?.visibility = View.VISIBLE
            
            tvTripRemainingDistance?.text = formatDistanceCompact(stableRemainingMeters)
            tvTripRemainingTime?.text = "00 min"
            tvTripEta?.text = formatEta(System.currentTimeMillis())
            lastTripRemainingSecDisplayed = 0
            lastTripEtaDisplayed = System.currentTimeMillis()
            lastTripUiUpdateMs = now
            lastMinutesBucket = 0
            lastEtaMinutesBucket = lastTripEtaDisplayed
            smoothedRemainingSec = 0.0
            return
        }

        // Time remaining (raw) - изчисляваме РЕАЛНО с текущата скорост
        // Винаги изчисляваме (дори и с начална скорост в началото)
        val timeRemainingRawSec = stableRemainingMeters / effectiveSpeedMps

        // Smooth remaining time with EMA to avoid jumpiness
        // Но ако скоростта се е променила значително, актуализираме по-бързо
        smoothedRemainingSec = when {
            timeRemainingRawSec == null -> smoothedRemainingSec // Ако скоростта е 0, запазваме старото
            smoothedRemainingSec == null -> timeRemainingRawSec
            else -> {
                // Ако разликата е голяма, използваме по-малък smoothing factor за по-бърза реакция
                val diff = Math.abs(timeRemainingRawSec - smoothedRemainingSec!!)
                val smoothingFactor = if (diff > 300) 0.5 else 0.9 // По-бърза реакция при големи промени
                smoothedRemainingSec!! * smoothingFactor + timeRemainingRawSec * (1.0 - smoothingFactor)
            }
        }

        val timeRemainingSec = smoothedRemainingSec?.toLong()
        // Clamp time remaining change to avoid jumps (>60s per update)
        val clampedTimeRemainingSec = if (timeRemainingSec != null && lastTripRemainingSecDisplayed != null) {
            val delta = timeRemainingSec - lastTripRemainingSecDisplayed!!
            when {
                delta < -60 -> lastTripRemainingSecDisplayed!! - 60
                delta > 60 -> lastTripRemainingSecDisplayed!! + 60
                else -> timeRemainingSec
            }
        } else timeRemainingSec

        // Compute minutes (CEIL) for display, but update only once per 60s
        val displayMinutes = clampedTimeRemainingSec?.let { ((it + 59) / 60).toInt() }
        val displayEtaMillis = displayMinutes?.let { now + it * 60_000L }

        // Update time/ETA: normally every 20s; in final minute update every 5s
        val isFinalMinute = displayMinutes != null && displayMinutes <= 1
        val timeIntervalMs = if (isFinalMinute) 5_000L else 20_000L
        val shouldUpdateTime = (lastTripUiUpdateMs == 0L) || (now - lastTripUiUpdateMs >= timeIntervalMs)

        // Distance can update more often
        val shouldUpdateDistance = now - lastDistanceUpdateMs > 1_000

        // Formatters
        val remainingDistanceText = formatDistanceCompact(remainingMeters)
        val remainingTimeText = displayMinutes?.let { formatDurationMinutesBucket(it) } ?: "--:--"
        val etaText = displayEtaMillis?.let { formatEta(it) } ?: "--:--"

        // ВАЖНО: НЕ променяме layout параметрите тук! Те се управляват от updateUIForProfile()
        // Само показваме данните в tripProgressContainer
        tripProgressContainer?.visibility = View.VISIBLE

        // Обновяваме данните в tripProgressContainer
        if (shouldUpdateDistance) {
            tvTripRemainingDistance?.text = remainingDistanceText
            lastDistanceUpdateMs = now
        }
        if (shouldUpdateTime) {
            tvTripRemainingTime?.text = remainingTimeText
            tvTripEta?.text = etaText
            lastTripRemainingSecDisplayed = clampedTimeRemainingSec
            lastTripEtaDisplayed = displayEtaMillis
            lastTripUiUpdateMs = now
            lastMinutesBucket = displayMinutes
            lastEtaMinutesBucket = displayEtaMillis
        }
    }

    private fun formatDistanceCompact(meters: Double): String {
        return if (meters >= 1000) {
            String.format("%.2f", meters / 1000.0)
        } else {
            String.format("%.0f m", meters)
        }
    }

    private fun formatDurationHm(seconds: Long): String {
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        return if (hrs > 0) {
            String.format("%d:%02d", hrs, mins)
        } else {
            String.format("%02d:%02d", mins, seconds % 60)
        }
    }

    // Format for minute bucket (rounded up)
    private fun formatDurationMinutesBucket(minutes: Int): String {
        val hrs = minutes / 60
        val mins = minutes % 60
        return if (hrs > 0) {
            String.format("%d:%02d", hrs, mins)
        } else {
            String.format("%02d min", mins)
        }
    }

    private fun formatEta(etaMillis: Long): String {
        val df = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return df.format(java.util.Date(etaMillis))
    }
    
    private fun calculateDistanceToManeuver(currentLocation: GeoPoint, step: DirectionsStep): Double {
        step.maneuver?.location?.let { loc ->
            if (loc.size >= 2) {
                val maneuverPoint = GeoPoint(loc[1], loc[0])
                return currentLocation.distanceToAsDouble(maneuverPoint)
            }
        }
        return step.distance
    }
    
    // Calculate distance between two points in meters using Haversine formula
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth's radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
    
    private fun updateManeuverView(stepIndex: Int, distanceToManeuver: Double = -1.0) {
        if (stepIndex >= navigationSteps.size) {
            maneuverViewContainer?.visibility = View.GONE
            return
        }
        
        val step = navigationSteps[stepIndex]
        maneuverViewContainer?.visibility = View.VISIBLE
        
        // Update distance
        val distance = if (distanceToManeuver >= 0) distanceToManeuver else step.distance
        tvManeuverDistance?.text = formatDistance(distance)
        
        // Update instruction text
        val instruction = step.maneuver?.instruction 
            ?: step.bannerInstructions?.firstOrNull()?.primary?.text
            ?: step.name
            ?: "Continue"
        
        // Форматираме текста с оранжев цвят за кръгови кръстовища (exit номер и име на път)
        val formattedInstruction = formatManeuverInstruction(instruction)
        tvManeuverPrimary?.text = formattedInstruction
        
        // Update secondary text if available
        val secondaryText = step.bannerInstructions?.firstOrNull()?.secondary?.text
        if (secondaryText != null) {
            tvManeuverSecondary?.text = secondaryText
            tvManeuverSecondary?.visibility = View.VISIBLE
        } else {
            tvManeuverSecondary?.visibility = View.GONE
        }
        
        // Update maneuver icon
        // КРИТИЧНО: ВИНАГИ парсираме инструкцията и я приоритизираме над API отговора
        // Това гарантира че иконата винаги съответства на текста който показваме
        val parsedFromInstruction = parseManeuverFromInstruction(instruction)
        val parsedType = parsedFromInstruction.first
        val parsedModifier = parsedFromInstruction.second
        
        // Използваме парсираните стойности ако ги има, иначе използваме API отговора
        val bannerPrimary = step.bannerInstructions?.firstOrNull()?.primary
        val maneuverType = parsedType ?: bannerPrimary?.type ?: step.maneuver?.type
        val maneuverModifier = parsedModifier ?: bannerPrimary?.modifier ?: step.maneuver?.modifier
        
        val iconRes = getManeuverIcon(maneuverType, maneuverModifier)
        ivManeuverIcon?.setImageResource(iconRes)
    }
    
    /**
     * Форматира инструкцията за маневър с оранжев цвят
     * Оцветява:
     * - Номера на изхода при кръгови кръстовища (1st, 2nd, 3rd и т.н.)
     * - Посоката (left/ляво, right/дясно, straight/направо)
     * - Името на пътя (след "onto", "на", "to", "към")
     */
    private fun formatManeuverInstruction(instruction: String): android.text.SpannableString {
        val spannable = android.text.SpannableString(instruction)
        val orangeColor = ContextCompat.getColor(this, R.color.accent_orange)
        val lowerInstruction = instruction.lowercase()
        
        // 1. Оцветяваме посоката: left, right, straight, ляво, дясно, направо
        val directionPatterns = listOf(
            Regex("""\b(left|right|straight)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(ляво|дясно|направо)\b""", RegexOption.IGNORE_CASE)
        )
        directionPatterns.forEach { pattern ->
            pattern.findAll(instruction).forEach { matchResult ->
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(orangeColor),
                    matchResult.range.first,
                    matchResult.range.last + 1,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        
        // 2. За кръгови кръстовища - оцветяваме номера на изхода
        val isRoundabout = lowerInstruction.contains("roundabout") || 
                          lowerInstruction.contains("кръгово") ||
                          (lowerInstruction.contains("take") && lowerInstruction.contains("exit"))
        
        if (isRoundabout) {
            // Патърн за номер на изход: 1st, 2nd, 3rd, 4th, и т.н.
            val exitPattern = Regex("""(\d+)(st|nd|rd|th)\s+exit""", RegexOption.IGNORE_CASE)
            exitPattern.find(instruction)?.let { matchResult ->
                val start = matchResult.range.first
                val end = matchResult.range.last + 1
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(orangeColor),
                    start,
                    end,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        
        // 3. Оцветяваме името на пътя след "onto", "на", "to", "към", "toward"
        // Например: "onto 8", "onto бул. Цар Шишман", "на бул. Княгиня Мария Луиза.", "toward Пловдив"
        val roadNamePatterns = listOf(
            Regex("""(onto|to|toward)\s+(.+?)(?:\s*\.\s*$|$)""", RegexOption.IGNORE_CASE),
            Regex("""(на|към)\s+(.+?)(?:\s*\.\s*$|$)""", RegexOption.IGNORE_CASE)
        )
        roadNamePatterns.forEach { pattern ->
            pattern.findAll(instruction).forEach { matchResult ->
                // Оцветяваме само името на пътя (без "onto", "на" и т.н.)
                val roadNameGroup = matchResult.groups[2]
                if (roadNameGroup != null) {
                    var roadNameStart = roadNameGroup.range.first
                    var roadNameEnd = roadNameGroup.range.last + 1
                    
                    // Trim whitespace в началото
                    while (roadNameStart < roadNameEnd && instruction[roadNameStart].isWhitespace()) {
                        roadNameStart++
                    }
                    
                    // Trim whitespace и последната точка (ако е завършваща точка на изречението, не част от "бул.")
                    while (roadNameEnd > roadNameStart) {
                        val lastChar = instruction[roadNameEnd - 1]
                        if (lastChar.isWhitespace()) {
                            roadNameEnd--
                        } else if (lastChar == '.' && roadNameEnd >= instruction.length) {
                            // Точка в края на текста - проверяваме дали е част от "бул.", "ул." и т.н.
                            val textBeforeDot = instruction.substring(roadNameStart, roadNameEnd - 1).trimEnd()
                            val lastWordBeforeDot = textBeforeDot.takeLastWhile { !it.isWhitespace() && it != '.' }
                            if (lastWordBeforeDot.lowercase() !in listOf("бул", "ул", "пл", "ул", "str", "blvd", "st", "ave")) {
                                roadNameEnd-- // Премахваме завършващата точка
                            }
                            break
                        } else {
                            break
                        }
                    }
                    
                    if (roadNameStart < roadNameEnd) {
                        spannable.setSpan(
                            android.text.style.ForegroundColorSpan(orangeColor),
                            roadNameStart,
                            roadNameEnd,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
        }
        
        return spannable
    }
    
    /**
     * Парсира type и modifier от инструкцията като fallback ако няма в API отговора
     * Например: "Turn right" -> ("turn", "right")
     */
    private fun parseManeuverFromInstruction(instruction: String): Pair<String?, String?> {
        val lowerInstruction = instruction.lowercase().trim()
        
        // Проверяваме за различни типове инструкции (по-специфичните първо)
        // Забележка: Проверяваме за "turn right" преди "turn" за да избегнем грешки
        return when {
            // Roundabout - специален случай
            lowerInstruction.contains("roundabout") || lowerInstruction.contains("кръгово") || 
            lowerInstruction.startsWith("take") && lowerInstruction.contains("exit") -> Pair("roundabout", null)
            // U-turn
            lowerInstruction.contains("u-turn") || lowerInstruction.contains("u turn") || 
            lowerInstruction.contains("обратна посока") -> Pair("turn", "uturn")
            // Sharp turns (преди normal turns)
            lowerInstruction.contains("sharp right") || lowerInstruction.contains("рязко надясно") -> Pair("turn", "sharp right")
            lowerInstruction.contains("sharp left") || lowerInstruction.contains("рязко наляво") -> Pair("turn", "sharp left")
            // Slight turns (преди normal turns)
            lowerInstruction.contains("slight right") || lowerInstruction.contains("леко надясно") -> Pair("turn", "slight right")
            lowerInstruction.contains("slight left") || lowerInstruction.contains("леко наляво") -> Pair("turn", "slight left")
            // Normal turns - проверяваме за точни съвпадения
            lowerInstruction.startsWith("turn right") || lowerInstruction.contains("turn right") || 
            lowerInstruction.contains("завий надясно") || lowerInstruction.contains("поемете надясно") ||
            (lowerInstruction.contains("right") && !lowerInstruction.contains("roundabout")) -> Pair("turn", "right")
            lowerInstruction.startsWith("turn left") || lowerInstruction.contains("turn left") || 
            lowerInstruction.contains("завий наляво") || lowerInstruction.contains("поемете наляво") ||
            (lowerInstruction.contains("left") && !lowerInstruction.contains("roundabout")) -> Pair("turn", "left")
            // Other maneuvers
            lowerInstruction.contains("merge") || lowerInstruction.contains("сливане") -> Pair("merge", null)
            lowerInstruction.contains("arrive") || lowerInstruction.contains("пристигнахте") -> Pair("arrive", null)
            lowerInstruction.contains("continue") || lowerInstruction.contains("продължете") -> Pair("continue", null)
            else -> Pair(null, null)
        }
    }
    
    private fun formatDistance(meters: Double): String {
        return when {
            meters >= 1000 -> String.format("%.1f km", meters / 1000)
            meters >= 100 -> String.format("%.0f m", meters)
            else -> String.format("%.0f m", meters)
        }
    }
    
    private fun getManeuverIcon(type: String?, modifier: String?): Int {
        // Нормализираме type и modifier (lowercase, trim)
        val normalizedType = type?.lowercase()?.trim()
        val normalizedModifier = modifier?.lowercase()?.trim()
        
        return when (normalizedType) {
            "turn" -> when (normalizedModifier) {
                "left" -> R.drawable.ic_turn_left
                "right" -> R.drawable.ic_turn_right
                "slight left" -> R.drawable.ic_turn_slight_left
                "slight right" -> R.drawable.ic_turn_slight_right
                "sharp left" -> R.drawable.ic_turn_sharp_left
                "sharp right" -> R.drawable.ic_turn_sharp_right
                "uturn" -> R.drawable.ic_uturn
                else -> R.drawable.ic_turn_straight
            }
            "merge" -> R.drawable.ic_merge
            "roundabout", "rotary", "roundabout turn" -> R.drawable.ic_roundabout
            "arrive" -> R.drawable.ic_arrive
            "fork" -> when (normalizedModifier) {
                "left", "slight left" -> R.drawable.ic_turn_slight_left
                "right", "slight right" -> R.drawable.ic_turn_slight_right
                else -> R.drawable.ic_turn_straight
            }
            "off ramp", "on ramp" -> when (normalizedModifier) {
                "left", "slight left" -> R.drawable.ic_turn_slight_left
                "right", "slight right" -> R.drawable.ic_turn_slight_right
                else -> R.drawable.ic_turn_straight
            }
            "depart", "continue", "new name" -> R.drawable.ic_turn_straight
            else -> R.drawable.ic_turn_straight
        }
    }
    
    private fun updateRouteArrow(currentLocation: GeoPoint) {
        // Only initialize arrows ONCE - they stay permanently on the map
        if (routeArrowsInitialized) return
        if (!isMapboxMode || navigationSteps.isEmpty()) return
        
        // Инициализираме arrow API и View ако не са инициализирани
        if (routeArrowApi == null || routeArrowView == null) {
            Log.d("RouteArrow", "Creating new RouteArrowApi and RouteArrowView")
            routeArrowApi = MapboxRouteArrowApi()
            val routeArrowOptions = RouteArrowOptions.Builder(this)
                .withSlotName("middle")
                .build()
            routeArrowView = MapboxRouteArrowView(routeArrowOptions)
        }
        
        val style = mapboxMapView?.mapboxMap?.style ?: return
        
        // ВАЖНО: Изчистваме всички стари стрелки преди да добавим новите (за рероутинг)
        try {
            val clearResult = routeArrowApi!!.clearArrows()
            routeArrowView!!.render(style, clearResult)
            Log.d("RouteArrow", "Cleared old arrows")
        } catch (e: Exception) {
            Log.w("RouteArrow", "Error clearing arrows: ${e.message}")
        }
        
        // Add arrows for ALL turns at once
        for (stepIndex in 0 until navigationSteps.size) {
            val turnStep = navigationSteps[stepIndex]
            val maneuverType = turnStep.maneuver?.type
            
            // Skip non-turn maneuvers
            if (maneuverType == null || maneuverType == "depart" || maneuverType == "arrive") {
                continue
            }
            
            try {
                // Get approach step geometry (the road BEFORE the turn)
                val approachStepIndex = if (stepIndex > 0) stepIndex - 1 else 0
                val approachStep = navigationSteps[approachStepIndex]
                val approachGeometry = approachStep.geometry.coordinates
                
                // Get turn step geometry (the road AFTER the turn)
                val turnGeometry = turnStep.geometry.coordinates
                
                if (approachGeometry.isEmpty() || turnGeometry.isEmpty()) continue
                
                val arrowPoints = mutableListOf<com.mapbox.geojson.Point>()
                val targetDistanceMeters = 12.0
                
                // Find point ~12m before turn on the REAL road (walking backwards from end)
                val turnPoint = approachGeometry.last()
                if (turnPoint.size < 2) continue
                val turnLat = turnPoint[1]
                val turnLon = turnPoint[0]
                
                var accumulatedDistance = 0.0
                var beforePoint: List<Double>? = null
                for (i in approachGeometry.size - 1 downTo 1) {
                    val p1 = approachGeometry[i]
                    val p2 = approachGeometry[i - 1]
                    if (p1.size < 2 || p2.size < 2) continue
                    
                    val segmentDist = haversineDistance(p1[1], p1[0], p2[1], p2[0])
                    accumulatedDistance += segmentDist
                    
                    if (accumulatedDistance >= targetDistanceMeters) {
                        // Interpolate to get exact point at target distance
                        val overshoot = accumulatedDistance - targetDistanceMeters
                        val ratio = overshoot / segmentDist
                        val interpLat = p2[1] + (p1[1] - p2[1]) * ratio
                        val interpLon = p2[0] + (p1[0] - p2[0]) * ratio
                        beforePoint = listOf(interpLon, interpLat)
                        break
                    }
                }
                // If path is shorter than target, use first point
                if (beforePoint == null && approachGeometry.size >= 2) {
                    beforePoint = approachGeometry.first()
                }
                
                // Find point ~12m after turn on the REAL road (walking forward from start)
                accumulatedDistance = 0.0
                var afterPoint: List<Double>? = null
                for (i in 0 until turnGeometry.size - 1) {
                    val p1 = turnGeometry[i]
                    val p2 = turnGeometry[i + 1]
                    if (p1.size < 2 || p2.size < 2) continue
                    
                    val segmentDist = haversineDistance(p1[1], p1[0], p2[1], p2[0])
                    accumulatedDistance += segmentDist
                    
                    if (accumulatedDistance >= targetDistanceMeters) {
                        // Interpolate to get exact point at target distance
                        val overshoot = accumulatedDistance - targetDistanceMeters
                        val ratio = 1.0 - (overshoot / segmentDist)
                        val interpLat = p1[1] + (p2[1] - p1[1]) * ratio
                        val interpLon = p1[0] + (p2[0] - p1[0]) * ratio
                        afterPoint = listOf(interpLon, interpLat)
                        break
                    }
                }
                // If path is shorter than target, use last point
                if (afterPoint == null && turnGeometry.size >= 2) {
                    afterPoint = turnGeometry.last()
                }
                
                // Build arrow with real road points
                if (beforePoint != null && beforePoint.size >= 2) {
                    arrowPoints.add(com.mapbox.geojson.Point.fromLngLat(beforePoint[0], beforePoint[1]))
                }
                arrowPoints.add(com.mapbox.geojson.Point.fromLngLat(turnLon, turnLat))
                if (afterPoint != null && afterPoint.size >= 2) {
                    arrowPoints.add(com.mapbox.geojson.Point.fromLngLat(afterPoint[0], afterPoint[1]))
                }
                
                if (arrowPoints.size >= 3) {
                    val arrow = ManeuverArrow(arrowPoints)
                    val addResult = routeArrowApi!!.addArrow(arrow)
                    routeArrowView?.render(style, addResult)
                }
            } catch (e: Exception) {
                Log.e("RouteArrow", "Error adding arrow for step $stepIndex: ${e.message}")
            }
        }
        
        routeArrowsInitialized = true
        Log.d("RouteArrow", "All route arrows initialized")
    }
    

    private fun updateMapAnimation() {
        if (isMapboxMode) {
            // Mapbox: implement same logic as OSMDroid
            updateMapboxMapAnimation()
            return
        }
        
        if (!::smoothLocationOverlay.isInitialized || !::mapView.isInitialized) {
            return
        }
        
        val currentPosition = smoothLocationOverlay.getCurrentPosition()
        val currentBearing = smoothLocationOverlay.getCurrentBearing()
        
        val currentZoom = mapView.zoomLevelDouble
        val metersPerPixel = 156543.03392 * cos(Math.toRadians(currentPosition.latitude)) / Math.pow(2.0, currentZoom)
        var offsetMeters = 30 * resources.displayMetrics.density * metersPerPixel
        if (isNorthUpMode) {
            offsetMeters = 0.0
        }
        
        val bearingRad = Math.toRadians(currentBearing.toDouble())
        val offsetLat = (offsetMeters * cos(bearingRad)) / 111320.0
        val offsetLon = (offsetMeters * sin(bearingRad)) / (111320.0 * cos(Math.toRadians(currentPosition.latitude)))
        
        val newLat = currentPosition.latitude + offsetLat
        val newLon = currentPosition.longitude + offsetLon
        
        val currentCenter = mapView.mapCenter
        val latDiff = newLat - currentCenter.latitude
        val lonDiff = newLon - currentCenter.longitude
        
        val smoothNewLat = currentCenter.latitude + latDiff * 0.05f
        val smoothNewLon = currentCenter.longitude + lonDiff * 0.05f
        
        setMapCenter(smoothNewLat.toDouble(), smoothNewLon.toDouble())
        updateMapOrientation()
    }

    private fun updateZoomBasedOnSpeed(speed: Float) {
        val newTargetZoom = when {
            speed < 18 -> 19.5
            speed < 46 -> 18.5
            speed < 84 -> 17.5
            else -> 15.5
        }
        
        // Only change zoom if it's different AND enough time has passed since last change
        val currentTime = System.currentTimeMillis()
        if (newTargetZoom != targetZoom && (currentTime - lastZoomChangeTime) >= ZOOM_CHANGE_DELAY) {
            targetZoom = newTargetZoom
            lastZoomChangeTime = currentTime
            android.util.Log.d("MainActivity", "🔄 Zoom changed to ${targetZoom} based on speed ${speed.toInt()} km/h")
        }
    }
    
    private fun updateMapOrientation() {
        var diff = targetMapOrientation - currentMapOrientation
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f

        val speed = foregroundService?.getCurrentSpeed() ?: 0f

        val smoothingFactor = when {
            abs(diff) > 90 -> 0.15f
            abs(diff) > 45 -> 0.12f
            abs(diff) > 20 -> 0.08f
            speed > 50 -> 0.06f
            speed > 20 -> 0.05f
            else -> 0.04f
        }

        if (abs(diff) > 0.5f) {
            currentMapOrientation += diff * smoothingFactor
            while (currentMapOrientation > 360f) currentMapOrientation -= 360f
            while (currentMapOrientation < 0f) currentMapOrientation += 360f
            mapView.mapOrientation = currentMapOrientation
        }
        
        updateZoomSmoothly()
    }
    
    private fun updateZoomSmoothly() {
        val zoomDiff = targetZoom - currentZoom
        if (abs(zoomDiff) > 0.01) {
            currentZoom += zoomDiff * 0.08f
            setMapZoom(currentZoom.toDouble())
        }
    }

    private fun updateGaugeAnimation() {
        val diff = targetAngle - currentAngle
        val smoothing = if (abs(diff) > 10) 0.85f else 0.75f
        currentAngle += diff * (1 - smoothing)

        if (abs(diff) < 0.01f) {
            currentAngle = targetAngle
        }

        // Update large gauge if visible
        if (gaugeView.visibility == View.VISIBLE) {
            gaugeView.angle = currentAngle
            gaugeView.invalidate()
        }
        
        // Update small gauge if visible
        if (::smallGaugeView.isInitialized && smallGaugeView.visibility == View.VISIBLE) {
            smallGaugeView.angle = currentAngle
            smallGaugeView.invalidate()
        }
        
        // Update linear gauge if visible
        if (::linearGaugeView.isInitialized && linearGaugeView.visibility == View.VISIBLE) {
            linearGaugeView.angle = currentAngle
            linearGaugeView.invalidate()
        }
        
        // Update landscape linear gauge if visible
        if (::linearGaugeViewLandscape.isInitialized && linearGaugeViewLandscape.visibility == View.VISIBLE) {
            linearGaugeViewLandscape.angle = currentAngle
            linearGaugeViewLandscape.invalidate()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                if (azimuth < 0) azimuth += 360f
                var bearingDiff = azimuth - sensorBearing
                while (bearingDiff > 180) bearingDiff -= 360
                while (bearingDiff < -180) bearingDiff += 360
                sensorBearing += bearingDiff * 0.2f // Smooth factor
                while (sensorBearing < 0) sensorBearing += 360
                while (sensorBearing > 360) sensorBearing -= 360
            }

            Sensor.TYPE_ACCELEROMETER -> {
                gravity = lowPass(event.values.clone(), gravity)
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                geomagnetic = lowPass(event.values.clone(), geomagnetic)

                if (gravity != null && geomagnetic != null) {
                    if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)

                        var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                        if (azimuth < 0) azimuth += 360f

                        var bearingDiff = azimuth - sensorBearing
                        while (bearingDiff > 180) bearingDiff -= 360
                        while (bearingDiff < -180) bearingDiff += 360
                        sensorBearing += bearingDiff * 0.2f
                        while (sensorBearing < 0) sensorBearing += 360
                        while (sensorBearing > 360) sensorBearing -= 360
                    }
                }
            }
        }
    }




    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    private fun lowPass(input: FloatArray, output: FloatArray?): FloatArray {
        if (output == null) return input

        val alpha = 0.8f
        for (i in input.indices) {
            output[i] = output[i] + alpha * (input[i] - output[i])
        }
        return output
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
                    // Спираме сесията и service-а преди да излезем
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
        // Спираме сесията в service-а (спира събирането на точки)
        foregroundService?.stopMeasurement()
        // Изчистваме данните
        foregroundService?.resetData()
        // Спираме и унищожаваме service-а
        cleanupForegroundService()
        // Навигираме към RacesActivity
        navigateToRaces()
    }

    private fun navigateToRaces() {
        // Навигираме към RACES страницата в MainContainerActivity
        val intent = Intent(this, MainContainerActivity::class.java).apply {
            putExtra("INITIAL_PAGE", MainContainerActivity.PAGE_RACES)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putFloat("currentMapOrientation", currentMapOrientation)
        outState.putBoolean("isFirstLocation", isFirstLocation)
        outState.putDouble("totalDistance", totalDistance)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        currentMapOrientation = savedInstanceState.getFloat("currentMapOrientation", 0f)
        isFirstLocation = savedInstanceState.getBoolean("isFirstLocation", true)
        totalDistance = savedInstanceState.getDouble("totalDistance", 0.0)
        targetMapOrientation = currentMapOrientation
        updateDistanceDisplay()
        needsZeroAfterRotation = true
    }
}