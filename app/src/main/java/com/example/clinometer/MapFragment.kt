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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.gms.location.*
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.maps.plugin.attribution.attribution
import com.example.clinometer.settings.UnitsManager
import com.example.clinometer.network.OpenMeteoService
import com.example.clinometer.network.WeatherApiService
import com.example.clinometer.utils.WeatherIconMapper
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Fragment за картата - конвертиран от MainMapActivity
 * MapView се запазва в паметта между lifecycle промени за instant navigation
 */
class MapFragment : Fragment() {
    
    // Map views
    private lateinit var mapView: MapView // OSMDroid MapView
    private var mapboxMapView: MapboxMapView? = null // Mapbox MapView (nullable)
    private var isMapboxMode = false
    
    // UI Elements
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
    
    // Mapbox annotations
    private var mapboxPointAnnotationManager: PointAnnotationManager? = null
    private var mapboxLocationAnnotation: PointAnnotation? = null
    private var mapboxCircleAnnotationManager: CircleAnnotationManager? = null
    private var mapboxPulsingCircleAnnotation: CircleAnnotation? = null
    private var pulsingAnimator: ValueAnimator? = null
    
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
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    
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
        isMapboxMode = mapProvider == MapProviderManager.MapProvider.MAPBOX
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
        
        if (isMapboxMode) {
            // Скриваме OSMDroid mapView ако съществува и премахваме overlays
            val osmdroidMapView = view.findViewById<MapView>(R.id.mapView)
            osmdroidMapView?.let {
                // КРИТИЧНО: Премахваме всички overlays за да не се показват location markers
                it.overlays.clear()
                it.visibility = View.GONE
            }
            
            setupMapboxMap(view)
        } else {
            // Скриваме Mapbox mapView ако съществува
            val mapContainer = view.findViewById<FrameLayout>(R.id.mapContainer)
            mapContainer?.let { container ->
                // Премахваме всички MapboxMapView от контейнера
                for (i in 0 until container.childCount) {
                    val child = container.getChildAt(i)
                    if (child is com.mapbox.maps.MapView || child::class.simpleName == "MapboxMapView") {
                        container.removeViewAt(i)
                        break
                    }
                }
            }
            
            Configuration.getInstance().load(
                requireContext().applicationContext,
                PreferenceManager.getDefaultSharedPreferences(requireContext().applicationContext)
            )
            Configuration.getInstance().userAgentValue = requireContext().packageName
            
            mapView = view.findViewById(R.id.mapView)
            mapView?.visibility = View.VISIBLE
            setupOsmdroidMap()
        }
        
        // Initialize UI elements
        btnStartSession = view.findViewById(R.id.btnStartNavigationNoDestination)
        btnSessions = view.findViewById(R.id.btnSessions)
        
        val destinationSearchContainer = view.findViewById<LinearLayout>(R.id.destinationSearchContainer)
        destinationSearchContainer.setOnClickListener {
            startActivity(Intent(requireContext(), DestinationSearchActivity::class.java))
        }
        
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
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        
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
                    // Запазваме локацията в ViewModel за instant display при resume
                    mapStateViewModel.saveLastLocation(location)
                    
                    if (!mapStateViewModel.hasInitializedCamera) {
                        mapStateViewModel.hasInitializedCamera = true
                        
                        val savedState = mapStateViewModel.lastMapState
                        if (savedState != null) {
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
                            if (isMapboxMode) {
                                mapboxMapView?.mapboxMap?.setCamera(
                                    CameraOptions.Builder()
                                        .center(MapboxPoint.fromLngLat(location.longitude, location.latitude))
                                        .zoom(17.0)
                                        .build()
                                )
                                mapStateViewModel.saveMapState(location.latitude, location.longitude, 17.0, 45.0)
                            } else {
                                val geoPoint = GeoPoint(location.latitude, location.longitude)
                                mapView.controller.animateTo(geoPoint, MY_LOCATION_ZOOM, 400L)
                                mapStateViewModel.saveMapState(location.latitude, location.longitude, MY_LOCATION_ZOOM)
                            }
                        }
                        
                        if (isMapboxMode) {
                            updateMapboxLocationMarker(location)
                        } else {
                            myLocationOverlay.onLocationChanged(location, null)
                            if (::pulsingOverlay.isInitialized) {
                                pulsingOverlay.updateLocation(location)
                            }
                        }
                    } else {
                        if (isMapboxMode) {
                            mapboxMapView?.let { mv ->
                                updateMapboxLocationMarker(location)
                                mv.mapboxMap.cameraState.center.let { center ->
                                    mapStateViewModel.saveMapState(
                                        center.latitude(),
                                        center.longitude(),
                                        mv.mapboxMap.cameraState.zoom,
                                        mv.mapboxMap.cameraState.pitch
                                    )
                                }
                            }
                        } else {
                            myLocationOverlay.onLocationChanged(location, null)
                            if (::pulsingOverlay.isInitialized) {
                                pulsingOverlay.updateLocation(location)
                            }
                            val center = mapView.mapCenter
                            mapStateViewModel.saveMapState(center.latitude, center.longitude, mapView.zoomLevelDouble)
                        }
                    }
                    
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
    
    private fun setupOsmdroidMap() {
        // КРИТИЧНО: Гарантираме че няма стари overlays преди да добавим нови
        mapView.overlays.clear()
        
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(false)
        mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        
        // ПРОФЕСИОНАЛНО: Използваме запазеното състояние или lastKnownLocation
        val savedState = mapStateViewModel.lastMapState
        if (savedState != null) {
            mapView.controller.setZoom(savedState.zoom)
            val geoPoint = GeoPoint(savedState.centerLat, savedState.centerLon)
            mapView.controller.setCenter(geoPoint)
        } else {
            // Ако няма savedState, използваме lastKnownLocation за центриране
            val lastLocation = mapStateViewModel.lastKnownLocation
            if (lastLocation != null) {
                val geoPoint = GeoPoint(lastLocation.latitude, lastLocation.longitude)
                mapView.controller.setZoom(17.0)
                mapView.controller.setCenter(geoPoint)
            } else {
                // Последен fallback: default zoom без центриране (OSMDroid ще покаже по default)
                mapView.controller.setZoom(17.0)
            }
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
    
    private fun setupMapboxMap(view: View) {
        val mapContainer = view.findViewById<FrameLayout>(R.id.mapContainer)
        val osmdroidMapView = view.findViewById<MapView>(R.id.mapView)
        
        // Скриваме OSMDroid mapView напълно ако съществува и премахваме всички overlays
        if (osmdroidMapView != null) {
            // КРИТИЧНО: Премахваме всички overlays (включително location overlays) преди да скрием mapView
            osmdroidMapView.overlays.clear()
            osmdroidMapView.visibility = View.GONE
            if (osmdroidMapView.parent != null) {
                (osmdroidMapView.parent as? ViewGroup)?.removeView(osmdroidMapView)
            }
        }
        
        mapboxMapView = MapboxMapView(requireContext())
        mapboxMapView?.setBackgroundColor(Color.parseColor("#000000"))
        mapboxMapView?.alpha = 0f
        
        mapContainer.addView(mapboxMapView)
        
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
            
            // ВАЖНО: Винаги създаваме annotation managers, дори ако няма начална локация
            // Маркерът ще се създаде при първото location update
            mapboxMapView?.let {
                val annotationApi = it.annotations
                mapboxCircleAnnotationManager = annotationApi.createCircleAnnotationManager()
                mapboxPointAnnotationManager = annotationApi.createPointAnnotationManager()
            }
            
            if (initialLocation != null) {
                setupMapboxLocationMarker(style, initialLocation)
            } else {
                // НЕ създаваме маркер ако няма локация - ще се създаде при първото location update
                // Но annotation managers вече са създадени горе
                Log.d("MapFragment", "No initial location - marker will be created on first location update")
            }
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
    
    /**
     * ПРОФЕСИОНАЛНО: Определя началната локация по следния приоритет:
     * 1. lastKnownLocation от ViewModel (най-надеждно)
     * 2. lastMapState.center от ViewModel (запазено състояние на камерата)
     * 3. getLastLocation() от FusedLocationProvider (fallback)
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
        
        // 3. Трети приоритет: getLastLocation() (ако имаме permission)
        // Забележка: getLastLocation() е асинхронен и не може да се използва блокиращо тук
        // Затова разчитаме на lastKnownLocation от ViewModel който се обновява при location updates
        // Ако няма данни в ViewModel, getLastLocation() ще се извика в displayLastKnownLocationInstantly()
        
        // ВАЖНО: НЕ показваме София - връщаме null и ще изчакаме реална локация
        return null
    }
    
    private fun setupMapboxLocationMarker(style: Style, initialLocation: Pair<Double, Double>) {
        val locationIconBitmap = createLocationDotIconForMapbox()
        style.addImage("location-dot-icon", locationIconBitmap)
        
        // Annotation managers вече са създадени в setupMapboxMap()
        val initialPoint = MapboxPoint.fromLngLat(initialLocation.second, initialLocation.first)
        setupMapboxPulsingCircle(initialPoint)
        
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(initialPoint)
            .withIconImage("location-dot-icon")
            .withIconSize(1.0)
        
        mapboxLocationAnnotation = mapboxPointAnnotationManager?.create(pointAnnotationOptions)
    }
    
    private fun setupMapboxPulsingCircle(point: MapboxPoint) {
        if (mapboxCircleAnnotationManager == null) return
        
        val haloStartRadiusMeters = 10.0
        val haloEndRadiusMeters = 25.0
        
        val circleOptions = CircleAnnotationOptions()
            .withPoint(point)
            .withCircleRadius(haloStartRadiusMeters)
            .withCircleColor("#FF7A18")
            .withCircleOpacity(0.0)
            .withCircleStrokeColor("#00000000")
            .withCircleStrokeWidth(0.0)
        
        mapboxPulsingCircleAnnotation = mapboxCircleAnnotationManager?.create(circleOptions)
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
                val easedProgress = (0.5f - 0.5f * kotlin.math.cos(kotlin.math.PI * progress.toDouble())).toFloat()
                val currentRadius = minRadius + (maxRadius - minRadius) * easedProgress
                val envelope = if (progress < 0.5f) progress * 2f else (1f - progress) * 2f
                val maxAlpha = 64
                val currentAlpha = (maxAlpha * envelope).toInt().coerceIn(0, maxAlpha)
                val opacity = currentAlpha / 255.0
                
                mapboxPulsingCircleAnnotation?.let { circle ->
                    val currentPoint = circle.point
                    mapboxCircleAnnotationManager?.delete(circle)
                    
                    val circleOptions = CircleAnnotationOptions()
                        .withPoint(currentPoint)
                        .withCircleRadius(currentRadius)
                        .withCircleColor("#FF7A18")
                        .withCircleOpacity(opacity)
                        .withCircleStrokeColor("#00000000")
                        .withCircleStrokeWidth(0.0)
                    
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
        
        val baseRadiusPx = 11f * density
        val centerX = size / 2f
        val centerY = size / 2f
        
        val strokePaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
        }
        canvas.drawCircle(centerX, centerY, baseRadiusPx, strokePaint)
        
        val fillPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#FF7A18")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, baseRadiusPx, fillPaint)
        
        return bitmap
    }
    
    private fun createLocationDotIcon(): Bitmap {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        return bitmap
    }
    
    private fun updateMapboxLocationMarker(location: Location) {
        val point = MapboxPoint.fromLngLat(location.longitude, location.latitude)
        
        mapboxLocationAnnotation?.let {
            // Annotation вече съществува - просто го обновяваме
            it.point = point
            mapboxPointAnnotationManager?.update(it)
        } ?: run {
            // Annotation не съществува - създаваме нов САМО ако style е зареден
            mapboxMapView?.mapboxMap?.getStyle { style ->
                // ДВОЙНА ПРОВЕРКА: Проверяваме отново дали annotation не е създадено междувременно
                if (mapboxLocationAnnotation == null) {
                    val locationIconBitmap = createLocationDotIconForMapbox()
                    style.addImage("location-dot-icon", locationIconBitmap)
                    
                    if (mapboxPulsingCircleAnnotation == null) {
                        setupMapboxPulsingCircle(point)
                    }
                    
                    val pointAnnotationOptions = PointAnnotationOptions()
                        .withPoint(point)
                        .withIconImage("location-dot-icon")
                        .withIconSize(1.0)
                    
                    mapboxLocationAnnotation = mapboxPointAnnotationManager?.create(pointAnnotationOptions)
                } else {
                    // Annotation е създадено междувременно - просто го обновяваме
                    mapboxLocationAnnotation?.let { annotation ->
                        annotation.point = point
                        mapboxPointAnnotationManager?.update(annotation)
                    }
                }
            }
        }
        
        mapboxPulsingCircleAnnotation?.let { circle ->
            val currentRadius = circle.circleRadius ?: 0.5
            mapboxCircleAnnotationManager?.delete(circle)
            
            val circleOptions = CircleAnnotationOptions()
                .withPoint(point)
                .withCircleRadius(currentRadius)
                .withCircleColor("#FF7A18")
                .withCircleOpacity(0.0)
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
            startActivity(Intent(requireContext(), RacesActivity::class.java))
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
        super.onResume()
        
        loadCachedWeatherData()
        updateEnvironmentDisplay()
        
        // ПРОФЕСИОНАЛНО РЕШЕНИЕ: Показваме last known location ВЕДНАГА за instant display
        displayLastKnownLocationInstantly()
        
        if (isMapboxMode) {
            mapboxMapView?.onResume()
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
    
    /**
     * Показва последната известна локация веднага за instant display (като Google Maps)
     * Това предотвратява "скокането" на картата докато GPS данните не пристигнат
     */
    private fun displayLastKnownLocationInstantly() {
        if (!checkLocationPermission()) return
        
        // Първо проверяваме ViewModel за запазена локация
        val lastLocation = mapStateViewModel.lastKnownLocation
        
        if (lastLocation != null) {
            // Използваме запазената локация от ViewModel
            currentLocation = lastLocation
            updateLocationMarkerOnly(lastLocation)
        } else {
            // Ако няма в ViewModel, опитваме getLastLocation() за instant display
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val locationAge = System.currentTimeMillis() - it.time
                    // Използваме локацията ако е свежа (не по-стара от 60 секунди)
                    if (locationAge < 60000) {
                        currentLocation = it
                        mapStateViewModel.saveLastLocation(it)
                        updateLocationMarkerOnly(it)
                    }
                }
            }
        }
    }
    
    /**
     * Обновява само location marker без да мести камерата (използва се за instant display)
     */
    private fun updateLocationMarkerOnly(location: Location) {
        if (isMapboxMode) {
            updateMapboxLocationMarker(location)
        } else {
            if (::myLocationOverlay.isInitialized) {
                myLocationOverlay.onLocationChanged(location, null)
            }
            if (::pulsingOverlay.isInitialized) {
                pulsingOverlay.updateLocation(location)
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        
        // ПРОФЕСИОНАЛНО: Запазваме последната локация и състоянието на камерата
        currentLocation?.let { location ->
            mapStateViewModel.saveLastLocation(location)
        }
        
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
            mapStateViewModel.saveMapState(center.latitude, center.longitude, mapView.zoomLevelDouble)
        }
        
        stopLocationUpdates()
        
        if (!isMapboxMode) {
            mapView.onPause()
            myLocationOverlay.disableMyLocation()
            if (::pulsingOverlay.isInitialized) {
                pulsingOverlay.stop()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // КРИТИЧНО: НЕ унищожаваме MapView тук - той се запазва в паметта за instant navigation
        // MapView ще се унищожи само когато Fragment се унищожи напълно
    }
    
    override fun onDestroy() {
        super.onDestroy()
        pulsingAnimator?.cancel()
        if (isMapboxMode) {
            mapboxMapView?.onDestroy()
        }
        if (::pulsingOverlay.isInitialized) {
            pulsingOverlay.onDestroy()
        }
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
        if (isMapboxMode) {
            currentLocation?.let { location ->
                mapboxMapView?.mapboxMap?.setCamera(
                    CameraOptions.Builder()
                        .center(MapboxPoint.fromLngLat(location.longitude, location.latitude))
                        .zoom(17.0)
                        .build()
                )
            } ?: retrieveCurrentLocation()
        } else {
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
            // Започваме location updates за реално време
            startLocationUpdates()
            
            // ПРОФЕСИОНАЛНО: Използваме getLastLocation() за instant display докато чакаме новите данни
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val locationAge = System.currentTimeMillis() - it.time
                    // Използваме локацията ако е свежа (не по-стара от 10 секунди)
                    if (locationAge < 10000) {
                        currentLocation = it
                        mapStateViewModel.saveLastLocation(it)
                        
                        val savedState = mapStateViewModel.lastMapState
                        if (savedState != null) {
                            // Възстановяваме запазеното състояние на камерата (zoom, center, pitch)
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
                            
                            // Обновяваме location marker веднага (без да местим камерата)
                            updateLocationMarkerOnly(it)
                        } else {
                            // Няма запазено състояние - центрираме на локацията
                            if (isMapboxMode) {
                                mapboxMapView?.mapboxMap?.setCamera(
                                    CameraOptions.Builder()
                                        .center(MapboxPoint.fromLngLat(it.longitude, it.latitude))
                                        .zoom(17.0)
                                        .build()
                                )
                                mapStateViewModel.saveMapState(it.latitude, it.longitude, 17.0, 45.0)
                            } else {
                                val geoPoint = GeoPoint(it.latitude, it.longitude)
                                mapView.controller.animateTo(geoPoint, MY_LOCATION_ZOOM, 400L)
                                mapStateViewModel.saveMapState(it.latitude, it.longitude, MY_LOCATION_ZOOM)
                            }
                            mapStateViewModel.hasInitializedCamera = true
                            
                            // Обновяваме location marker
                            if (isMapboxMode) {
                                updateMapboxLocationMarker(it)
                            } else {
                                if (::myLocationOverlay.isInitialized) {
                                    myLocationOverlay.onLocationChanged(it, null)
                                }
                                if (::pulsingOverlay.isInitialized) {
                                    pulsingOverlay.updateLocation(it)
                                }
                            }
                        }
                    }
                }
            }
        }
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
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
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
        
        if (!cachedTemp.isNaN() && !cachedLat.isNaN() && !cachedLon.isNaN()) {
            currentTemperature = cachedTemp
            if (cachedIcon != -1) {
                currentWeatherIcon = cachedIcon
            }
        }
        
        if (!cachedAlt.isNaN() && !cachedLat.isNaN() && !cachedLon.isNaN()) {
            currentAltitude = cachedAlt
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
        editor.apply()
    }
    
    private fun shouldFetchWeatherData(location: Location): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
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
}
