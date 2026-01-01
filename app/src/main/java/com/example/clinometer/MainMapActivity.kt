package com.example.clinometer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.gms.location.*
import com.google.android.material.button.MaterialButton
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import com.example.clinometer.settings.MapProviderManager
import com.mapbox.geojson.Point as MapboxPoint
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView as MapboxMapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.dsl.generated.literal
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.maps.plugin.attribution.attribution
import android.view.ViewGroup
import kotlin.jvm.java
import com.example.clinometer.settings.UnitsManager
import com.example.clinometer.network.WeatherService
import com.example.clinometer.network.OpenMeteoService
import com.example.clinometer.network.ElevationResponse
import com.example.clinometer.network.WeatherApiService
import com.example.clinometer.utils.WeatherIconMapper
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainMapActivity : BaseActivity() {
    override fun getLayoutResourceId(): Int = R.layout.activity_main_map
    override fun getNavigationItemId(): Int = R.id.navMap
    private lateinit var mapView: MapView // OSMDroid MapView
    private var mapboxMapView: MapboxMapView? = null // Mapbox MapView (nullable)
    private var isMapboxMode = false
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var btnStartSession: MaterialButton
    private lateinit var btnSessions: MaterialButton
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
    private lateinit var pulsingOverlay: PulsingLocationOverlay
    private var mapboxPointAnnotationManager: com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager? = null
    private var mapboxLocationAnnotation: com.mapbox.maps.plugin.annotation.generated.PointAnnotation? = null
    private var mapboxCircleAnnotationManager: com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager? = null
    private var mapboxPulsingCircleAnnotation: com.mapbox.maps.plugin.annotation.generated.CircleAnnotation? = null
    private var pulsingAnimator: ValueAnimator? = null
    
    private var isWeatherExpanded = false
    private var isAltitudeExpanded = false
    private var altitudeCollapsedWidth = -1

    private var currentLocation: Location? = null
    private var currentTemperature: Float? = null
    private var currentAltitude: Float? = null
    
    // ViewModel за запазване на състоянието на картата
    private lateinit var mapStateViewModel: MapStateViewModel
    private var currentWeatherIcon: Int = R.drawable.ic_thermometer  // Default icon
    
    // Weather details for expanded view
    private var currentWindKph: Double = 0.0
    private var currentWindDir: String = ""
    private var currentHumidity: Int = 0
    private var currentCloudCover: Int = 0
    private var rainChance3h: Int = 0
    private var rainTimeText: String = ""  // Time of max rain chance
    private var currentPressure: Double = 0.0  // Atmospheric pressure in hPa
    
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var backPressedTime: Long = 0
    private lateinit var backToast: Toast

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val LOCATION_UPDATE_INTERVAL = 100L  // 100ms = 10Hz (като ForegroundService)
        private const val LOCATION_FASTEST_UPDATE_INTERVAL = 100L  // 100ms = 10Hz
        private const val MY_LOCATION_ZOOM = 17.0
        private const val CACHE_LOCATION_THRESHOLD_KM = 5.0  // Кешът е валиден ако локацията е в радиус от 5км
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ProfileStorage.loadProfiles(this).isEmpty()) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }

        // Инициализираме ViewModel за запазване на състоянието на картата
        mapStateViewModel = ViewModelProvider(this)[MapStateViewModel::class.java]

        // Използваме нормален режим - Android ще добави padding автоматично
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)

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
            Configuration.getInstance().userAgentValue = packageName
            
            mapView = findViewById(R.id.mapView)
            setupOsmdroidMap()
        }
        btnStartSession = findViewById(R.id.btnStartNavigationNoDestination)
        btnSessions = findViewById(R.id.btnSessions)
        
        // Setup destination search field
        val destinationSearchContainer = findViewById<LinearLayout>(R.id.destinationSearchContainer)
        destinationSearchContainer.setOnClickListener {
            startActivity(Intent(this, DestinationSearchActivity::class.java))
        }
        llTemperature = findViewById(R.id.llTemperature)
        llWeatherExpanded = findViewById(R.id.llWeatherExpanded)
        tvSeparator = findViewById(R.id.tvSeparator)
        llAltitude = findViewById(R.id.llAltitude)
        llAltitudeExpanded = findViewById(R.id.llAltitudeExpanded)
        tvAltSeparator = findViewById(R.id.tvAltSeparator)
        ivWeatherIcon = findViewById(R.id.ivWeatherIcon)
        tvTemperature = findViewById(R.id.tvTemperature)
        tvAltitude = findViewById(R.id.tvAltitude)
        
        // 🔥 ДИНАМИЧНО ADJUST НА MARGINS ЗА LANDSCAPE СПОРЕД НАВИГАЦИЯТА!
        adjustMarginsForLandscapeNavigation()
        fabMyLocationContainer = findViewById(R.id.fabMyLocationContainer)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        btnStartSession.setOnClickListener {
            startNormalSession()
        }

        btnSessions.setOnClickListener {
            navigateToSessions()
        }
        
        fabMyLocationContainer.setOnClickListener {
            centerOnCurrentLocation()
        }
        
        llTemperature.setOnClickListener {
            toggleWeatherExpansion()
        }
        
        llAltitude.setOnClickListener {
            toggleAltitudeExpansion()
        }

        // Handle system bars insets (за navigation bar отдолу)
        val bottomContainer = findViewById<LinearLayout>(R.id.bottomContainer)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(bottomContainer) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                systemBars.bottom  // Добавяме padding отдолу за navigation bar
            )
            insets
        }
        
        // Зареждаме кешираните данни веднага за моментално показване
        loadCachedWeatherData()
        updateEnvironmentDisplay() // Показваме кешираните стойности веднага

        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        )
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_UPDATE_INTERVAL)
            .setWaitForAccurateLocation(false)
            .setMinUpdateDistanceMeters(0.1f)
            .setMaxUpdateDelayMillis(100)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let { location ->
                    currentLocation = location
                    
                    // КРИТИЧНО: При първа локация центрираме камерата за instant display
                    // Използваме ViewModel за да запазим състоянието
                    if (!mapStateViewModel.hasInitializedCamera) {
                        mapStateViewModel.hasInitializedCamera = true
                        
                        // Проверяваме дали има запазено състояние от преди
                        val savedState = mapStateViewModel.lastMapState
                        if (savedState != null) {
                            // Възстановяваме запазеното състояние
                            if (isMapboxMode) {
                                mapboxMapView?.mapboxMap?.setCamera(
                                    CameraOptions.Builder()
                                        .center(MapboxPoint.fromLngLat(savedState.centerLon, savedState.centerLat))
                                        .zoom(savedState.zoom)
                                        .pitch(savedState.pitch ?: 45.0)
                                        .build()
                                )
                            } else {
                                val geoPoint = GeoPoint(savedState.centerLat, savedState.centerLon)
                                mapView.controller.setZoom(savedState.zoom)
                                mapView.controller.setCenter(geoPoint)
                            }
                        } else {
                            // Няма запазено състояние - центрираме на текущата локация
                            if (isMapboxMode) {
                                mapboxMapView?.mapboxMap?.setCamera(
                                    CameraOptions.Builder()
                                        .center(MapboxPoint.fromLngLat(location.longitude, location.latitude))
                                        .zoom(17.0)
                                        .build()
                                )
                                // Запазваме състоянието
                                mapStateViewModel.saveMapState(
                                    location.latitude,
                                    location.longitude,
                                    17.0,
                                    45.0
                                )
                            } else {
                                val geoPoint = GeoPoint(location.latitude, location.longitude)
                                mapView.controller.animateTo(geoPoint, MY_LOCATION_ZOOM, 400L)
                                // Запазваме състоянието
                                mapStateViewModel.saveMapState(
                                    location.latitude,
                                    location.longitude,
                                    MY_LOCATION_ZOOM
                                )
                            }
                        }
                        
                        // Обновяваме маркера на локацията
                        if (isMapboxMode) {
                            updateMapboxLocationMarker(location)
                        } else {
                            myLocationOverlay.onLocationChanged(location, null)
                            if (::pulsingOverlay.isInitialized) {
                                pulsingOverlay.updateLocation(location)
                            }
                        }
                    } else {
                        // След първа локация, само обновяваме маркера (не центрираме камерата)
                        // Но запазваме текущото състояние на картата
                        if (isMapboxMode) {
                            mapboxMapView?.let { mapView ->
                                updateMapboxLocationMarker(location)
                                // Запазваме текущото състояние на картата
                                mapView.mapboxMap.cameraState.center.let { center ->
                                    mapStateViewModel.saveMapState(
                                        center.latitude(),
                                        center.longitude(),
                                        mapView.mapboxMap.cameraState.zoom,
                                        mapView.mapboxMap.cameraState.pitch
                                    )
                                }
                            }
                        } else {
                            myLocationOverlay.onLocationChanged(location, null)
                            if (::pulsingOverlay.isInitialized) {
                                pulsingOverlay.updateLocation(location)
                            }
                            // Запазваме текущото състояние на картата
                            val center = mapView.mapCenter
                            mapStateViewModel.saveMapState(
                                center.latitude,
                                center.longitude,
                                mapView.zoomLevelDouble
                            )
                        }
                    }
                    
                    // Fetch weather data when we get location (only if needed)
                    // Проверяваме дали имаме кеширани данни и дали локацията е се променила значително
                    val shouldFetch = shouldFetchWeatherData(location)
                    if (shouldFetch) {
                        fetchWeatherFromAPI(location)
                    }
                }
            }
        }

        if (!checkLocationPermission()) {
            requestLocationPermission()
        } else {
            retrieveCurrentLocation()
        }
    }
    
    /**
     * ПРОФЕСИОНАЛНО: Определя началната локация по следния приоритет:
     * 1. lastKnownLocation от ViewModel (най-надеждно)
     * 2. lastMapState.center от ViewModel (запазено състояние на камерата)
     * ВАЖНО: НЕ показваме София или друга default локация - по-добре да изчакаме реална локация
     */
    private fun determineInitialLocation(): Pair<Double, Double>? {
        // 1. Най-висок приоритет: lastKnownLocation от ViewModel
        mapStateViewModel.lastKnownLocation?.let { location ->
            return Pair(location.latitude, location.longitude)
        }
        
        // 2. Втори приоритет: lastMapState center (запазено състояние на камерата)
        mapStateViewModel.lastMapState?.let { state ->
            return Pair(state.centerLat, state.centerLon)
        }
        
        // ВАЖНО: НЕ показваме София - връщаме null и ще изчакаме реална локация
        return null
    }
    
    private fun setupOsmdroidMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(false)
        mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        
        // Проверяваме дали има запазено състояние от ViewModel
        val savedState = mapStateViewModel.lastMapState
        if (savedState != null) {
            // Възстановяваме запазеното състояние
            mapView.controller.setZoom(savedState.zoom)
            val geoPoint = GeoPoint(savedState.centerLat, savedState.centerLon)
            mapView.controller.setCenter(geoPoint)
        } else {
            // Няма запазено състояние - използваме default
            mapView.controller.setZoom(17.0)
        }
        
        mapView.isTilesScaledToDpi = true
        pulsingOverlay = PulsingLocationOverlay(mapView)

        val customLocationIcon = createLocationDotIcon()
        myLocationOverlay = MyLocationNewOverlay(mapView).apply {
            enableMyLocation()
            setDrawAccuracyEnabled(false)
            setPersonIcon(customLocationIcon)
            setDirectionIcon(customLocationIcon)
            setEnableAutoStop(false)
        }
        mapView.overlays.add(pulsingOverlay)
        mapView.overlays.add(myLocationOverlay)
    }
    
    /**
     * Зарежда Mapbox стил от JSON файл (res/raw/mapbox_style.json)
     * Това заобикаля проблемите с кеширане на стилове
     */
    private fun loadMapboxStyleFromJson(onStyleLoaded: (Style) -> Unit) {
        // Използваме директно URL с timestamp за да форсираме презареждане всеки път
        // Това гарантира че винаги се зарежда най-новия стил от Mapbox Studio
        val styleUri = "mapbox://styles/djsookz/cmiyer8iu000101s84wqsav5l"
        android.util.Log.d("MainMapActivity", "🔄 Зареждаме стил от URL: $styleUri")
        mapboxMapView?.mapboxMap?.loadStyleUri(styleUri) { style ->
            android.util.Log.d("MainMapActivity", "✅ Стилът е зареден успешно от URL!")
            onStyleLoaded(style)
        }
    }
    
    private fun setupMapboxMap() {
        val mapContainer = findViewById<android.widget.FrameLayout>(R.id.mapContainer)
        val osmdroidMapView = findViewById<MapView>(R.id.mapView)
        
        // Remove OSMDroid MapView само ако съществува
        if (osmdroidMapView.parent != null) {
            mapContainer.removeView(osmdroidMapView)
        }
        
        // Create Mapbox MapView
        mapboxMapView = MapboxMapView(this)
        
        // Задаваме тъмен background докато се зареди стилът (за предотвратяване на премигване)
        mapboxMapView?.setBackgroundColor(android.graphics.Color.parseColor("#000000"))
        mapboxMapView?.alpha = 0f
        
        mapContainer.addView(mapboxMapView)
        
        val styleUri = "mapbox://styles/djsookz/cmiyer8iu000101s84wqsav5l"
        
        // Зареждаме стила
        mapboxMapView?.mapboxMap?.loadStyleUri(styleUri)
        
        // ПРОФЕСИОНАЛЕН ПОДХОД: Не променяме камерата при onCreate ако вече имаме инициализирана
        // Използваме determineInitialLocation() за да използваме lastKnownLocation вместо Sofia default
        if (!mapStateViewModel.hasInitializedCamera) {
            val initialLocation = determineInitialLocation()
            if (initialLocation != null) {
                mapboxMapView?.mapboxMap?.setCamera(
                    CameraOptions.Builder()
                        .center(MapboxPoint.fromLngLat(initialLocation.second, initialLocation.first))
                        .zoom(17.0)
                        .build()
                )
            } else {
                // НЕ задаваме камера ако няма локация - ще се зададе при първото location update
                android.util.Log.d("MainMapActivity", "No initial location available - camera will be set on first location update")
            }
        }
        
        // ПРОФЕСИОНАЛЕН ПОДХОД: Чакаме стилът да се зареди и показваме MapView
        mapboxMapView?.mapboxMap?.getStyle { style ->
            android.util.Log.d("MainMapActivity", "✅ Стилът зареден успешно")
            
            // КРИТИЧНО: Показваме MapView СЛЕД като стилът е зареден
            mapboxMapView?.post {
                mapboxMapView?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                mapboxMapView?.alpha = 1f
            }
            
            // Ако имаме запазено състояние и камерата НЕ е инициализирана, възстановяваме го
            val savedState = mapStateViewModel.lastMapState
            if (!mapStateViewModel.hasInitializedCamera && savedState != null) {
                mapboxMapView?.mapboxMap?.setCamera(
                    CameraOptions.Builder()
                        .center(MapboxPoint.fromLngLat(savedState.centerLon, savedState.centerLat))
                        .zoom(savedState.zoom)
                        .pitch(savedState.pitch ?: 45.0)
                        .build()
                )
                android.util.Log.d("MainMapActivity", "✅ Възстановено запазено състояние на камерата")
            }
            
            setupMapboxLocationMarker(style)
        }
        
        // Disable Mapbox UI plugins (scale bar and compass)
        configureMapboxPlugins()
        
        // Also disable them after a delay to ensure they stay disabled
        mapboxMapView?.postDelayed({
            configureMapboxPlugins()
        }, 1000)
    }
    
    
    private fun configureMapboxPlugins() {
        mapboxMapView?.let { mapView ->
            // Disable Compass plugin
            val compassPlugin = mapView.compass
            compassPlugin.enabled = false
            
            // Disable Scale Bar plugin
            val scaleBarPlugin = mapView.scalebar
            scaleBarPlugin.enabled = false
            
            // Hide attribution button (if you want)
            val attributionPlugin = mapView.attribution
            attributionPlugin.enabled = false
        }
    }
    
    private fun setupMapboxLocationMarker(style: Style) {
        // Create location dot icon bitmap
        val locationIconBitmap = createLocationDotIconForMapbox()
        
        // Add image to style
        style.addImage("location-dot-icon", locationIconBitmap)
        
        // Create Point Annotation Manager using Annotation API (simpler than Style Layers)
        mapboxMapView?.let { mapView ->
            // Get annotation API and create annotation managers
            // IMPORTANT: Create CircleAnnotationManager FIRST, then PointAnnotationManager
            // This ensures circles render below points (lower z-index)
            val annotationApi = mapView.annotations
            mapboxCircleAnnotationManager = annotationApi.createCircleAnnotationManager()
            mapboxPointAnnotationManager = annotationApi.createPointAnnotationManager()
            
            // Create initial point - използваме determineInitialLocation() вместо Sofia default
            val initialLocation = determineInitialLocation()
            if (initialLocation != null) {
                val initialPoint = MapboxPoint.fromLngLat(initialLocation.second, initialLocation.first)
                
                // IMPORTANT: Create pulsing circle FIRST (so it appears below the point)
                setupMapboxPulsingCircle(initialPoint)
                
                // Create point annotation AFTER (so it appears on top)
                val pointAnnotationOptions = PointAnnotationOptions()
                    .withPoint(initialPoint)
                    .withIconImage("location-dot-icon") // Use the image name, not the bitmap
                    .withIconSize(1.0)
                
                mapboxLocationAnnotation = mapboxPointAnnotationManager?.create(pointAnnotationOptions)
            } else {
                // НЕ създаваме маркер ако няма локация - ще се създаде при първото location update
                android.util.Log.d("MainMapActivity", "No initial location - marker will be created on first location update")
            }
        }
    }
    
    private fun setupMapboxPulsingCircle(point: MapboxPoint) {
        if (mapboxCircleAnnotationManager == null) return
        
        // In PulsingLocationOverlay:
        // baseRadiusPx = 11f * density (pixels)
        // haloStartRadius = baseRadiusPx + haloGapPx = 11f * density + 4f * density = 15f * density (pixels)
        // haloEndRadius = max(accuracy, haloStartRadius + haloPulseExtraPx) = max(accuracy, 15f + 6f) = max(accuracy, 21f * density) (pixels)
        
        // For Mapbox, we need meters. At zoom 17, approximately:
        // 1 pixel ≈ 0.6 meters (at equator, zoom 17)
        // So: 15 pixels ≈ 9 meters, 21 pixels ≈ 12.6 meters
        
        // But let's use larger values to make it more visible
        // Start radius: ~10 meters (equivalent to ~15-20 pixels)
        // End radius: ~20 meters (equivalent to ~30-35 pixels)
        val haloStartRadiusMeters = 10.0 // meters - start of pulse
        val haloEndRadiusMeters = 25.0 // meters - end of pulse
        
        // Create pulsing circle annotation (orange halo - same as PulsingLocationOverlay)
        // Mapbox expects hex color format: #RRGGBB
        // #FF7A18 is the orange color (255, 122, 24)
        // Start with 0 opacity (fully transparent)
        val circleOptions = CircleAnnotationOptions()
            .withPoint(point)
            .withCircleRadius(haloStartRadiusMeters) // Start radius in meters
            .withCircleColor("#FF7A18") // Orange color
            .withCircleOpacity(0.0) // Start with 0 opacity (fully transparent)
            .withCircleStrokeColor("#00000000") // No stroke
            .withCircleStrokeWidth(0.0)
        
        mapboxPulsingCircleAnnotation = mapboxCircleAnnotationManager?.create(circleOptions)
        
        // Start pulsing animation
        startPulsingAnimation(haloStartRadiusMeters, haloEndRadiusMeters)
    }
    
    private fun startPulsingAnimation(minRadius: Double, maxRadius: Double) {
        pulsingAnimator?.cancel()
        
        pulsingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                // Ease in/out using cosine (same as PulsingLocationOverlay)
                val easedProgress = (0.5f - 0.5f * kotlin.math.cos(kotlin.math.PI * progress.toDouble())).toFloat()
                
                // Animate radius from minRadius to maxRadius (same as PulsingLocationOverlay)
                val currentRadius = minRadius + (maxRadius - minRadius) * easedProgress
                
                // Animate opacity: 0 -> 25% (at 50%) -> 0
                // envelope: 0 at start, 1 at 50%, 0 at end
                val envelope = if (progress < 0.5f) progress * 2f else (1f - progress) * 2f
                // Max opacity = 25% = 0.25, so alpha = 255 * 0.25 = 64
                val maxAlpha = 64
                val currentAlpha = (maxAlpha * envelope).toInt().coerceIn(0, maxAlpha)
                // Convert to opacity (0.0 to 1.0) for Mapbox
                val opacity = currentAlpha / 255.0
                
                mapboxPulsingCircleAnnotation?.let { circle ->
                    val currentPoint = circle.point
                    
                    // Delete old annotation
                    mapboxCircleAnnotationManager?.delete(circle)
                    
                    // Create new annotation with updated properties
                    // Use base orange color with animated opacity
                    val colorHex = "#FF7A18" // Base orange color
                    val circleOptions = CircleAnnotationOptions()
                        .withPoint(currentPoint)
                        .withCircleRadius(currentRadius)
                        .withCircleColor(colorHex)
                        .withCircleOpacity(opacity) // Animated opacity: 0 -> 0.25 -> 0
                        .withCircleStrokeColor("#00000000")
                        .withCircleStrokeWidth(0.0)
                    
                    // Create new annotation and update reference
                    mapboxPulsingCircleAnnotation = mapboxCircleAnnotationManager?.create(circleOptions)
                }
            }
        }
        
        pulsingAnimator?.start()
    }
    
    private fun createLocationDotIconForMapbox(): Bitmap {
        val density = resources.displayMetrics.density
        val size = (32 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Same as PulsingLocationOverlay: orange circle with white stroke
        val baseRadiusPx = 11f * density
        val centerX = size / 2f
        val centerY = size / 2f
        
        // Draw white stroke circle (outer)
        val strokePaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
        }
        canvas.drawCircle(centerX, centerY, baseRadiusPx, strokePaint)
        
        // Draw orange fill circle (inner) - same color as PulsingLocationOverlay
        val fillPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#FF7A18") // Same orange as PulsingLocationOverlay
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, baseRadiusPx, fillPaint)
        
        return bitmap
    }

    private fun createLocationDotIcon(): Bitmap {
        // Hide default marker (we draw our own overlay)
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        return bitmap
    }
    
    private fun updateMapboxLocationMarker(location: Location) {
        val point = MapboxPoint.fromLngLat(location.longitude, location.latitude)
        
        mapboxLocationAnnotation?.let { annotation ->
            // Update annotation position
            annotation.point = point
            mapboxPointAnnotationManager?.update(annotation)
        } ?: run {
            // Create annotation if it doesn't exist
            // Need to get the icon bitmap from style
            mapboxMapView?.mapboxMap?.getStyle { style ->
                val locationIconBitmap = createLocationDotIconForMapbox()
                style.addImage("location-dot-icon", locationIconBitmap)
                
                // Setup pulsing circle FIRST if not already created (so it appears below)
                if (mapboxPulsingCircleAnnotation == null) {
                    setupMapboxPulsingCircle(point)
                }
                
                // Create point annotation AFTER (so it appears on top)
                val pointAnnotationOptions = PointAnnotationOptions()
                    .withPoint(point)
                    .withIconImage("location-dot-icon") // Use the image name, not the bitmap
                    .withIconSize(1.0)
                
                mapboxLocationAnnotation = mapboxPointAnnotationManager?.create(pointAnnotationOptions)
            }
        }
        
        // Update pulsing circle position (delete and recreate to maintain z-order)
        mapboxPulsingCircleAnnotation?.let { circle ->
            val currentRadius = circle.circleRadius ?: 0.5 // Default to 0.5 meters if null
            mapboxCircleAnnotationManager?.delete(circle)
            
            // Recreate pulsing circle (will be below the point)
            // Start with 0 opacity
            val circleOptions = CircleAnnotationOptions()
                .withPoint(point)
                .withCircleRadius(currentRadius) // Keep current radius from animation
                .withCircleColor("#FF7A18") // Orange color
                .withCircleOpacity(0.0) // Start with 0 opacity
                .withCircleStrokeColor("#00000000")
                .withCircleStrokeWidth(0.0)
            mapboxPulsingCircleAnnotation = mapboxCircleAnnotationManager?.create(circleOptions)
        }
    }

    private fun startNormalSession() {
        if (!checkLocationPermission()) {
            requestLocationPermission()
            return
        }

        val selectedProfileId = ProfileStorage.getSelectedProfileId(this)
        val profiles = ProfileStorage.loadProfiles(this)
        val profile = if (selectedProfileId != -1L) {
            profiles.find { it.id == selectedProfileId }
        } else {
            profiles.firstOrNull()
        }

        profile?.let {
            ProfileStorage.saveSelectedProfile(this, it.id)
            val intent = Intent(this, CountdownActivity::class.java).apply {
                putExtra("SELECTED_PROFILE", it)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        } ?: run {
            Toast.makeText(this, "Моля изберете профил", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, RacesActivity::class.java))
        }
    }

    private fun toggleWeatherExpansion() {
        // Close altitude if open (mutual exclusion)
        if (isAltitudeExpanded) {
            collapseAltitudeNow()
        }
        
        isWeatherExpanded = !isWeatherExpanded
        
        val collapsedWidth = (90 * resources.displayMetrics.density).toInt()
        
        if (isWeatherExpanded) {
            // Show elements BEFORE animation to avoid glitches
            tvSeparator.visibility = TextView.VISIBLE
            llWeatherExpanded.visibility = TextView.VISIBLE
            tvSeparator.alpha = 0f
            llWeatherExpanded.alpha = 0f
            
            // Set to collapsed width first
            val params = llTemperature.layoutParams
            params.width = collapsedWidth
            llTemperature.layoutParams = params
            
            // Single post for measuring
            llTemperature.post {
                // Force measure with all content visible
                llTemperature.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
                    android.view.View.MeasureSpec.makeMeasureSpec(llTemperature.height, android.view.View.MeasureSpec.EXACTLY)
                )
                val targetWidth = llTemperature.measuredWidth
                
                android.util.Log.d("MainMapActivity", "🎬 EXPAND: start=$collapsedWidth, target=$targetWidth")
                
                // Animate width expansion IMMEDIATELY
                val widthAnimator = ValueAnimator.ofInt(collapsedWidth, targetWidth)
                widthAnimator.addUpdateListener { animation ->
                    val animParams = llTemperature.layoutParams
                    animParams.width = animation.animatedValue as Int
                    llTemperature.layoutParams = animParams
                }
                widthAnimator.duration = 300
                widthAnimator.interpolator = DecelerateInterpolator()
                widthAnimator.start()
                
                // Fade in text smoothly
                tvSeparator.animate().alpha(1f).setDuration(250).setStartDelay(50).start()
                llWeatherExpanded.animate().alpha(1f).setDuration(300).setStartDelay(50).start()
            }
            
        } else {
            // Collapse animation
            val currentWidth = llTemperature.width
            
            android.util.Log.d("MainMapActivity", "🎬 COLLAPSE: current=$currentWidth, target=$collapsedWidth")
            
            // Fade out text first
            tvSeparator.animate().alpha(0f).setDuration(150).start()
            llWeatherExpanded.animate().alpha(0f).setDuration(150).start()
            
            // Then animate width
            val widthAnimator = ValueAnimator.ofInt(currentWidth, collapsedWidth)
            widthAnimator.addUpdateListener { animation ->
                val params = llTemperature.layoutParams
                params.width = animation.animatedValue as Int
                llTemperature.layoutParams = params
            }
            widthAnimator.startDelay = 100  // Wait for fade out
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
        
        android.util.Log.d("MainMapActivity", "🔄 Weather pill expanded: $isWeatherExpanded")
    }
    
    private fun toggleAltitudeExpansion() {
        // Close weather if open (mutual exclusion)
        if (isWeatherExpanded) {
            collapseWeather()
        }
        
        if (isAltitudeExpanded) {
            // Already expanded, so collapse it
            collapseAltitudeNow()
        } else {
            // Collapsed, so expand it
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
        // Single line: Wind, Humidity | Rain forecast
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
        // Show atmospheric pressure
        val pressureText = "📊 ${currentPressure.toInt()} hPa"
        llAltitudeExpanded.text = pressureText
    }
    
    private fun centerOnCurrentLocation() {
        if (isMapboxMode) {
            // Center Mapbox map
            currentLocation?.let { location ->
                mapboxMapView?.mapboxMap?.setCamera(
                    CameraOptions.Builder()
                        .center(MapboxPoint.fromLngLat(location.longitude, location.latitude))
                        .zoom(17.0)
                        .build()
                )
            } ?: retrieveCurrentLocation()
        } else {
            // Center OSMDroid map
            val loc = myLocationOverlay.myLocation
            if (loc != null) {
                mapView.controller.animateTo(
                    GeoPoint(loc.latitude, loc.longitude),
                    MY_LOCATION_ZOOM,
                    400L
                )
            } else {
                retrieveCurrentLocation()
            }
        }
    }

    private fun retrieveCurrentLocation() {
        if (checkLocationPermission()) {
            // КРИТИЧНО: Започваме location updates ВЕДНАГА вместо да чакаме lastLocation
            // Това гарантира че ще получим локацията много по-бързо
            startLocationUpdates()
            
            // Също така опитваме да използваме lastLocation ако е наличен (за instant display)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    // Проверяваме дали локацията е свежа (не по-стара от 10 секунди)
                    val locationAge = System.currentTimeMillis() - location.time
                    if (locationAge < 10000) { // 10 секунди
                        currentLocation = it
                        // Проверяваме дали има запазено състояние
                        val savedState = mapStateViewModel.lastMapState
                        if (savedState != null) {
                            // Възстановяваме запазеното състояние
                            if (isMapboxMode) {
                                mapboxMapView?.mapboxMap?.setCamera(
                                    CameraOptions.Builder()
                                        .center(MapboxPoint.fromLngLat(savedState.centerLon, savedState.centerLat))
                                        .zoom(savedState.zoom)
                                        .pitch(savedState.pitch ?: 45.0)
                                        .build()
                                )
                            } else {
                                val geoPoint = GeoPoint(savedState.centerLat, savedState.centerLon)
                                mapView.controller.setZoom(savedState.zoom)
                                mapView.controller.setCenter(geoPoint)
                            }
                            mapStateViewModel.hasInitializedCamera = true
                        } else {
                            // Няма запазено състояние - центрираме на текущата локация
                            if (isMapboxMode) {
                                mapboxMapView?.mapboxMap?.setCamera(
                                    CameraOptions.Builder()
                                        .center(MapboxPoint.fromLngLat(it.longitude, it.latitude))
                                        .zoom(17.0)
                                        .build()
                                )
                                mapStateViewModel.saveMapState(
                                    it.latitude,
                                    it.longitude,
                                    17.0,
                                    45.0
                                )
                            } else {
                                val geoPoint = GeoPoint(it.latitude, it.longitude)
                                mapView.controller.animateTo(geoPoint, MY_LOCATION_ZOOM, 400L)
                                mapStateViewModel.saveMapState(
                                    it.latitude,
                                    it.longitude,
                                    MY_LOCATION_ZOOM
                                )
                            }
                            mapStateViewModel.hasInitializedCamera = true
                        }
                        
                        // Обновяваме маркера
                        if (isMapboxMode) {
                            updateMapboxLocationMarker(it)
                        } else {
                            myLocationOverlay.onLocationChanged(it, null)
                            if (::pulsingOverlay.isInitialized) {
                                pulsingOverlay.updateLocation(it)
                            }
                        }
                    }
                    // Ако локацията е остаряла, locationCallback ще получи нова скоро
                }
                // Ако няма lastLocation, locationCallback ще получи локация скоро
            }
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onStart() {
        super.onStart()
        if (isMapboxMode) {
            mapboxMapView?.onStart()
        }
    }
    
    override fun onStop() {
        super.onStop()
        if (isMapboxMode) {
            mapboxMapView?.onStop()
        }
    }
    
    override fun onResume() {
        if (ProfileStorage.loadProfiles(this).isEmpty()) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }
        super.onResume()
        
        // Зареждаме кешираните данни веднага за моментално показване
        loadCachedWeatherData()
        updateEnvironmentDisplay()
        
        if (isMapboxMode) {
            mapboxMapView?.onResume()
            // ПРОФЕСИОНАЛЕН ПОДХОД: НЕ променяме камерата в onResume!
            // Mapbox автоматично запазва състоянието между lifecycle промени
            // Промяната на камерата тук причинява премигане
            // Започваме location updates (може да са вече стартирани, но requestLocationUpdates е безопасен за multiple calls)
            startLocationUpdates()
        } else {
            mapView.onResume()
            myLocationOverlay.enableMyLocation()
            startLocationUpdates()
            if (::pulsingOverlay.isInitialized) {
                pulsingOverlay.start()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Запазваме текущото състояние на картата преди да спрем
        if (isMapboxMode && mapboxMapView != null) {
            mapboxMapView?.mapboxMap?.cameraState?.let { cameraState ->
                mapStateViewModel.saveMapState(
                    cameraState.center.latitude(),
                    cameraState.center.longitude(),
                    cameraState.zoom,
                    cameraState.pitch
                )
            }
        } else if (!isMapboxMode && ::mapView.isInitialized) {
            val center = mapView.mapCenter
            mapStateViewModel.saveMapState(
                center.latitude,
                center.longitude,
                mapView.zoomLevelDouble
            )
        }
        
        // НЕ нулираме hasInitializedCamera - запазваме го за да няма презареждане!
        if (isMapboxMode) {
            // Mapbox lifecycle is handled in onStart/onStop
            stopLocationUpdates()
        } else {
            mapView.onPause()
            myLocationOverlay.disableMyLocation()
            stopLocationUpdates()
            if (::pulsingOverlay.isInitialized) {
                pulsingOverlay.stop()
            }
        }
    }

    override fun onDestroy() {
        // Stop pulsing animation
        pulsingAnimator?.cancel()
        
        if (isMapboxMode) {
            mapboxMapView?.onDestroy()
        }
        super.onDestroy()
        if (::pulsingOverlay.isInitialized) {
            pulsingOverlay.onDestroy()
        }
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
                    this,
                    "Разрешение за локация е необходимо за пълна функционалност",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onBackPressed() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            backToast.cancel()
            super.onBackPressed()
            return
        } else {
            backToast = Toast.makeText(baseContext, "Натиснете отново за изход", Toast.LENGTH_SHORT)
            backToast.show()
        }
        backPressedTime = System.currentTimeMillis()
    }
    
    private fun fetchWeatherData() {
        if (currentLocation != null) {
            fetchWeatherFromAPI(currentLocation!!)
        }
    }
    
    private fun updateEnvironmentDisplay() {
        val tempText = if (currentTemperature != null) {
            UnitsManager.formatTemperature(currentTemperature!!, this, decimals = 0)
        } else {
            val unit = UnitsManager.getTemperatureUnit(this)
            "--${unit.symbol}"
        }
        
        val altText = if (currentAltitude != null) {
            String.format("%.0fm", currentAltitude)
        } else {
            "--m"
        }
        
        tvTemperature.text = tempText
        tvAltitude.text = altText
        
        // Update weather icon dynamically
        ivWeatherIcon.setImageResource(currentWeatherIcon)
        
        // Show environment info pills if we have data
        if (currentTemperature != null) {
            llTemperature.visibility = LinearLayout.VISIBLE
        }
        if (currentAltitude != null) {
            llAltitude.visibility = LinearLayout.VISIBLE
        }
    }
    
    /**
     * Зарежда кешираните данни за температура и височина от SharedPreferences
     * и ги показва моментално при връщане в activity-то
     */
    private fun loadCachedWeatherData() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val cachedTemp = prefs.getFloat("cached_temperature", Float.NaN)
        val cachedAlt = prefs.getFloat("cached_altitude", Float.NaN)
        val cachedLat = prefs.getFloat("cached_location_lat", Float.NaN)
        val cachedLon = prefs.getFloat("cached_location_lon", Float.NaN)
        val cachedIcon = prefs.getInt("cached_weather_icon", -1)
        
        if (!cachedTemp.isNaN() && !cachedLat.isNaN() && !cachedLon.isNaN()) {
            currentTemperature = cachedTemp
            if (cachedIcon != -1) {
                currentWeatherIcon = cachedIcon
            }
            android.util.Log.d("MainMapActivity", "✅ Loaded cached temperature: $currentTemperature°C")
        }
        
        if (!cachedAlt.isNaN() && !cachedLat.isNaN() && !cachedLon.isNaN()) {
            currentAltitude = cachedAlt
            android.util.Log.d("MainMapActivity", "✅ Loaded cached altitude: $currentAltitude m")
        }
    }
    
    /**
     * Кешира данните за температура и височина в SharedPreferences
     */
    private fun cacheWeatherData(location: Location) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = prefs.edit()
        
        currentTemperature?.let {
            editor.putFloat("cached_temperature", it)
        }
        currentAltitude?.let {
            editor.putFloat("cached_altitude", it)
        }
        editor.putFloat("cached_location_lat", location.latitude.toFloat())
        editor.putFloat("cached_location_lon", location.longitude.toFloat())
        editor.putInt("cached_weather_icon", currentWeatherIcon)
        editor.apply()
        
        android.util.Log.d("MainMapActivity", "💾 Cached weather data: temp=$currentTemperature, alt=$currentAltitude")
    }
    
    /**
     * Проверява дали трябва да направим заявка за данни
     * Връща true ако:
     * - Нямаме кеширани данни
     * - Локацията е се променила значително (повече от 5км)
     */
    private fun shouldFetchWeatherData(location: Location): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val cachedLat = prefs.getFloat("cached_location_lat", Float.NaN)
        val cachedLon = prefs.getFloat("cached_location_lon", Float.NaN)
        
        // Ако нямаме кеширани данни, правим заявка
        if (cachedLat.isNaN() || cachedLon.isNaN()) {
            android.util.Log.d("MainMapActivity", "🔄 No cached data, fetching...")
            return true
        }
        
        // Проверяваме дали локацията е се променила значително
        val cachedLocation = Location("cached").apply {
            latitude = cachedLat.toDouble()
            longitude = cachedLon.toDouble()
        }
        val distanceKm = location.distanceTo(cachedLocation) / 1000.0
        
        if (distanceKm > CACHE_LOCATION_THRESHOLD_KM) {
            android.util.Log.d("MainMapActivity", "🔄 Location changed significantly (${String.format("%.1f", distanceKm)}km), fetching...")
            return true
        }
        
        // Ако имаме кеширани данни и локацията е близо, не правим заявка
        android.util.Log.d("MainMapActivity", "✅ Using cached data (location change: ${String.format("%.1f", distanceKm)}km)")
        return false
    }
    
    private fun fetchWeatherFromAPI(location: Location) {
        android.util.Log.d("MainMapActivity", "🔄 FETCHING weather for location: ${location.latitude}, ${location.longitude}")
        lifecycleScope.launch {
            try {
                // WeatherAPI.com retrofit
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
                
                // Fetch weather from WeatherAPI.com
                val weatherResponse = weatherApiService.getCurrentWeather(
                    apiKey = "547cc84c36a447ab8fe131642251808",
                    location = "${location.latitude},${location.longitude}",
                    lang = "bg"
                )
                android.util.Log.d("MainMapActivity", "Weather response: ${weatherResponse.isSuccessful}")
                if (weatherResponse.isSuccessful && weatherResponse.body() != null) {
                    val weather = weatherResponse.body()!!
                    currentTemperature = weather.current.temp_c.toFloat()
                    
                    // Get weather icon based on condition, cloud cover %, and day/night
                    val condition = weather.current.condition
                    val cloudCover = weather.current.cloud
                    val isDay = weather.current.is_day == 1
                    currentWeatherIcon = WeatherIconMapper.getWeatherApiIcon(condition.code, cloudCover, isDay)
                    
                    android.util.Log.d("MainMapActivity", "🌤️ Weather: code=${condition.code}, cloud=${cloudCover}%, isDay=$isDay ${if(isDay) "☀️" else "🌙"}")
                    
                    // Store weather details for expanded view
                    currentWindKph = weather.current.wind_kph
                    currentWindDir = weather.current.wind_dir
                    currentHumidity = weather.current.humidity
                    currentCloudCover = weather.current.cloud
                    currentPressure = weather.current.pressure_mb
                    
                    // Calculate rain chance in next 3 hours from forecast (FUTURE hours only)
                    weather.forecast?.forecastday?.firstOrNull()?.hour?.let { hours ->
                        val now = java.util.Calendar.getInstance()
                        val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
                        val currentMinute = now.get(java.util.Calendar.MINUTE)
                        
                        // Filter FUTURE hours only (including current hour if minutes < 30)
                        val next3Hours = hours.filter { hour ->
                            val hourTime = hour.time.split(" ")[1].split(":")[0].toInt()
                            // Include hour if it's in the future OR current hour with minutes < 30
                            hourTime > currentHour || (hourTime == currentHour && currentMinute < 30)
                        }.take(3) // Take only next 3 hours
                        
                        // Find hour with max rain chance
                        val maxRainHour = next3Hours.maxByOrNull { it.chance_of_rain }
                        rainChance3h = maxRainHour?.chance_of_rain ?: 0
                        
                        // Get time of max rain (HH:MM format)
                        rainTimeText = if (maxRainHour != null && rainChance3h > 0) {
                            val hourTime = maxRainHour.time.split(" ")[1].substring(0, 5) // "HH:MM"
                            hourTime
                        } else {
                            ""
                        }
                    } ?: run {
                        rainChance3h = 0
                        rainTimeText = ""
                    }
                    
                    android.util.Log.d("MainMapActivity", "🌤️ WeatherAPI Code: ${condition.code}, Desc: ${condition.text}, Cloud: ${cloudCover}%")
                    android.util.Log.d("MainMapActivity", "🌡️ Temperature: $currentTemperature°C")
                    android.util.Log.d("MainMapActivity", "💨 Wind: ${currentWindKph}km/h ${currentWindDir}, Humidity: ${currentHumidity}%, Rain 3h: ${rainChance3h}%")
                    
                    // Update expanded view text
                    withContext(Dispatchers.Main) {
                        updateWeatherExpandedText()
                        updateAltitudeExpandedText()
                    }
                }
                
                // Fetch elevation
                val elevationResponse = openMeteoService.getElevation(
                    location.latitude,
                    location.longitude
                )
                android.util.Log.d("MainMapActivity", "Elevation response: ${elevationResponse.isSuccessful}")
                if (elevationResponse.isSuccessful && elevationResponse.body() != null) {
                    val elevation = elevationResponse.body()!!
                    currentAltitude = elevation.elevation.firstOrNull()?.toFloat() ?: 0f
                    android.util.Log.d("MainMapActivity", "Altitude: $currentAltitude")
                }
                
                // Кешираме новите данни
                cacheWeatherData(location)
                
                withContext(Dispatchers.Main) {
                    updateEnvironmentDisplay()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainMapActivity", "Error fetching weather data", e)
            }
        }
    }
    
    /**
     * Helper функция за конвертиране на dp в px
     */
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
    
    /**
     * 🔥 Динамично adjust на margins за landscape според навигацията!
     * ВАЖНО: Бутоните SESSIONS/START трябва да са ЦЕНТРИРАНИ с еднакъв padding от двете страни!
     */
    private fun adjustMarginsForLandscapeNavigation() {
        val rootView = findViewById<android.view.View>(android.R.id.content)
        
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            
            if (isLandscape) {
                android.util.Log.d("MainMapActivity", "🔄 LANDSCAPE detected!")
                android.util.Log.d("MainMapActivity", "   System bars - left: ${systemBars.left}, right: ${systemBars.right}")
                
                // 🎯 CRITICAL: За да са центрирани бутоните, трябва да имат ЕДНАКЪВ padding!
                // Вземаме по-голямата стойност (навигацията) и я прилагаме от ДВЕТЕ страни!
                val navBarWidth = maxOf(systemBars.left, systemBars.right)
                val basePadding = 16 // Base padding в dp
                val equalPadding = basePadding + navBarWidth
                
                // Bottom container (SESSIONS/START buttons) - ЕДНАКЪВ padding от двете страни!
                val bottomContainer = findViewById<android.widget.LinearLayout>(R.id.bottomContainer)
                bottomContainer?.setPadding(
                    equalPadding,  // ЕДНАКВО отляво
                    bottomContainer.paddingTop,
                    equalPadding,  // ЕДНАКВО отдясно
                    bottomContainer.paddingBottom
                )
                android.util.Log.d("MainMapActivity", "      🔘 Bottom container EQUAL padding: L=$equalPadding, R=$equalPadding")
                
                // 🎯 Pills трябва ДОПЪЛНИТЕЛЕН margin за да са подравнени с бутоните!
                val pillsExtraMargin = 50  // 👈 ПРОМЕНИ ТОЗИ БРОЙ (в dp) за да местиш pills!
                
                // Измерваме височината на bottomContainer за да позиционираме pills
                bottomContainer?.post {
                    val bottomContainerHeight = bottomContainer.height
                    val pillsMarginBottom = bottomContainerHeight + 25.dpToPx()  // 25dp над контейнера
                    
                    android.util.Log.d("MainMapActivity", "      📏 Bottom container height: $bottomContainerHeight px")
                    android.util.Log.d("MainMapActivity", "      📏 Pills marginBottom: $pillsMarginBottom px")
                    
                    // Altitude pill → align с ЛЕВИЯ ръб на SESSIONS + extra margin
                    llAltitude?.let {
                        val params = it.layoutParams as? android.widget.RelativeLayout.LayoutParams
                        params?.marginStart = equalPadding + pillsExtraMargin
                        params?.bottomMargin = pillsMarginBottom  // 25dp над контейнера
                        it.layoutParams = params
                        android.util.Log.d("MainMapActivity", "      ⛰️ Altitude pill marginStart: ${equalPadding + pillsExtraMargin}, marginBottom: $pillsMarginBottom")
                    }
                    
                    // Temperature pill → align с ДЕСНИЯ ръб на START + extra margin
                    llTemperature?.let {
                        val params = it.layoutParams as? android.widget.RelativeLayout.LayoutParams
                        params?.marginEnd = equalPadding + pillsExtraMargin
                        params?.bottomMargin = pillsMarginBottom  // 25dp над контейнера
                        it.layoutParams = params
                        android.util.Log.d("MainMapActivity", "      ☀️ Temperature pill marginEnd: ${equalPadding + pillsExtraMargin}, marginBottom: $pillsMarginBottom")
                        
                        // FAB button → 10dp НАД temperature pill
                        fabMyLocationContainer?.let { fab ->
                            val fabParams = fab.layoutParams as? android.widget.RelativeLayout.LayoutParams
                            val temperaturePillHeight = it.height
                            val fabMarginBottom = pillsMarginBottom + temperaturePillHeight + 10.dpToPx()  // Pills + pill height + 10dp spacing
                            fabParams?.marginEnd = equalPadding + pillsExtraMargin
                            fabParams?.bottomMargin = fabMarginBottom  // 10dp НАД temperature pill
                            fab.layoutParams = fabParams
                            android.util.Log.d("MainMapActivity", "      🎯 FAB marginEnd: ${equalPadding + pillsExtraMargin}, marginBottom: $fabMarginBottom")
                        }
                    }
                }
            } else {
                android.util.Log.d("MainMapActivity", "📱 PORTRAIT detected - no extra margins needed")
            }
            
            insets
        }
    }
}