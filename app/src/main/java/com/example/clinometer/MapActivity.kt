package com.example.clinometer
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.example.clinometer.settings.LanguageManager
import com.example.clinometer.settings.UnitsManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.android.material.tabs.TabLayout
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import com.example.clinometer.settings.MapProviderManager
import com.mapbox.geojson.Point as MapboxPoint
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView as MapboxMapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addLayerAbove
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.ScaleGestureDetector
import androidx.compose.ui.graphics.Paint
import com.github.mikephil.charting.components.YAxis
import kotlin.math.abs

class MapActivity : AppCompatActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }
    
    private lateinit var routePoints: List<RoutePoint>
    private lateinit var map: MapView // OSMDroid MapView
    private var mapboxMapView: MapboxMapView? = null // Mapbox MapView (nullable)
    private var isMapboxMode = false
    private lateinit var marker: Marker
    private var mapboxPolylineAnnotationManager: PolylineAnnotationManager? = null
    private var mapboxPointAnnotationManager: PointAnnotationManager? = null
    private var mapboxCircleAnnotationManager: CircleAnnotationManager? = null
    private var mapboxPulsingCircleAnnotation: com.mapbox.maps.plugin.annotation.generated.CircleAnnotation? = null
    private var pulsingAnimator: ValueAnimator? = null
    private val pulsingHandler = Handler(Looper.getMainLooper())
    private lateinit var chart: LineChart
    private lateinit var tabLayout: TabLayout
    private var currentMode: Mode = Mode.SPEED
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var routeDrawingTimer: android.os.Handler? = null
    private var routeDrawingRunnable: Runnable? = null
    private var isDrawingRoute = false
    private var currentDrawingIndex = 0
    private var hasUserInteracted = false
    private val originalRouteOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()
    private var mapboxRouteAnnotations = mutableListOf<com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation>()
    private var lastDrawnIndex = -1
    private var mapboxRouteSourceId = "route-source-dynamic"
    private var mapboxRouteLayerId = "route-layer-dynamic"
    private var mapboxStyle: Style? = null
    private var lastMapboxUpdateTime = 0L
    private var pendingMapboxUpdate: Runnable? = null
    private val mapboxUpdateHandler = Handler(Looper.getMainLooper())
    private var zoomButtonsHideRunnable: Runnable? = null
    private val zoomButtonsHandler = Handler(Looper.getMainLooper())
    
    // Smooth marker animation
    private var markerAnimator: android.animation.ValueAnimator? = null
    private var currentMarkerLat = 0.0
    private var currentMarkerLon = 0.0
    private var isMarkerInitialized = false

    private enum class Mode {
        SPEED, ANGLE
    }
    
    /**
     * Зарежда Mapbox стил от JSON файл (res/raw/mapbox_style.json)
     * Това заобикаля проблемите с кеширане на стилове
     */
    private fun loadMapboxStyleFromJson(onStyleLoaded: (Style) -> Unit) {
        // Използваме директно URL с timestamp за да форсираме презареждане всеки път
        // Това гарантира че винаги се зарежда най-новия стил от Mapbox Studio
        val styleUri = "mapbox://styles/djsookz/cmiyer8iu000101s84wqsav5l"
        Log.d("MapActivity", "🔄 Зареждаме стил от URL: $styleUri")
        mapboxMapView?.mapboxMap?.loadStyleUri(styleUri) { style ->
            Log.d("MapActivity", "✅ Стилът е зареден успешно от URL!")
            onStyleLoaded(style)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check which map provider is selected
        val mapProvider = com.example.clinometer.settings.MapProviderManager.getMapProvider(this)
        isMapboxMode = mapProvider == com.example.clinometer.settings.MapProviderManager.MapProvider.MAPBOX
        
        if (!isMapboxMode) {
            Configuration.getInstance().load(
                applicationContext,
                PreferenceManager.getDefaultSharedPreferences(applicationContext)
            )
        }
        
        setContentView(R.layout.activity_map)
        
        setupScreenKeepOn()
        

        // Взимаме ID на сесията
        val raceId = intent.getLongExtra("RACE_ID", -1)
        if (raceId == -1L) {
            Toast.makeText(this, R.string.error_missing_session_id, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Зареждаме метаданните
        val races = RouteStorage.loadRaces(this)
        val race = races.find { it.id == raceId }
        if (race == null) {
            Toast.makeText(this, R.string.error_session_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // КРИТИЧНО: Използваме profileId от race, НЕ текущия избран профил!
        // Това гарантира че показваме правилни данни според профила с който е направена сесията
        val profiles = ProfileStorage.loadProfiles(this)
        val profile = profiles.find { it.id == race.profileId }
        val isMotorcycle = profile?.vehicleType == Profile.VehicleType.MOTORCYCLE
        android.util.Log.d("MapActivity", "🏍️ Profile check: race.profileId=${race.profileId}, isMotorcycle=$isMotorcycle, profile=${profile?.name}")

        // Зареждаме точките от хранилището
        routePoints = RouteStorage.loadRoutePoints(this, raceId)
        android.util.Log.d("MapActivity", "📂 LOADED routePoints: ${routePoints.size} for raceId=$raceId")
        
        if (routePoints.isEmpty()) {
            android.util.Log.e("MapActivity", "❌ CRITICAL: routePoints is EMPTY! raceId=$raceId")
            android.util.Log.e("MapActivity", "   This will cause the activity to finish!")
        }

        // Показване/скриване на елементите за ъгли според типа превозно средство
        val maxLeftLayout = findViewById<androidx.cardview.widget.CardView>(R.id.maxLeftLayout)
        val maxRightLayout = findViewById<LinearLayout>(R.id.maxRightLayout)

        if (isMotorcycle) {
            // Показваме данни за ъгли
            val maxLeftAngle = routePoints.filter { it.angle < 0 }.minByOrNull { it.angle }?.angle?.let { kotlin.math.abs(it) } ?: 0f
            val maxRightAngle = routePoints.filter { it.angle > 0 }.maxByOrNull { it.angle }?.angle ?: 0f

            // Показваме данни за ъгли
            findViewById<TextView>(R.id.tvMaxLeftInfo).text = getString(R.string.max_left_angle) + " " + "%.0f°".format(maxLeftAngle)
            findViewById<TextView>(R.id.tvMaxRightInfo).text = getString(R.string.max_right_angle) + " " + "%.0f°".format(maxRightAngle)
            findViewById<TextView>(R.id.tvDistanceMoto).apply {
                visibility = View.VISIBLE
                val convertedDist = UnitsManager.formatDistance(race.distance, this@MapActivity, 2)
                text = getString(R.string.distance_format) + " " + convertedDist
            }
            findViewById<TextView>(R.id.tvDistanceCar).visibility = View.GONE
            // 👇 ДОБАВЕТЕ този ред за да покажете картата с ъгли
            findViewById<androidx.cardview.widget.CardView>(R.id.maxLeftLayout).visibility = View.VISIBLE
        } else {
            // Скриваме целите редове за ъгли
            maxLeftLayout.visibility = View.GONE
            findViewById<TextView>(R.id.tvDistanceCar).apply {
                visibility = View.VISIBLE
                val convertedDist = UnitsManager.formatDistance(race.distance, this@MapActivity, 2)
                text = getString(R.string.distance_format) + " " + convertedDist
            }
            findViewById<TextView>(R.id.tvDistanceMoto).visibility = View.GONE
        }

        // Винаги показваме скоростта
        val convertedMaxSpeed = UnitsManager.formatSpeed(race.maxSpeed, this, 0)
        findViewById<TextView>(R.id.tvMaxSpeedInfo).text = "Max Speed: $convertedMaxSpeed"

        val btnNewRoute = findViewById<Button>(R.id.btnStart)
        btnNewRoute.setText(R.string.new_session_button)
        btnNewRoute.setOnClickListener {
            startActivity(Intent(this, MainContainerActivity::class.java).apply {
                putExtra("NAV_ITEM_ID", R.id.navMap)
            })
            finish()
        }

        val avgSpeed = if (race.duration > 0) {
            (race.distance * 3600000) / race.duration // km/h = (km * ms_in_hour) / ms
        } else {
            0.0
        }
        val convertedAvgSpeed = UnitsManager.formatSpeed(avgSpeed.toFloat(), this, 0)
        findViewById<TextView>(R.id.tvAvgSpeedInfo).text = getString(R.string.avg_speed) + " " + convertedAvgSpeed


        // Проверка дали има данни за маршрут
        if (routePoints.isEmpty()) {
            Toast.makeText(this, R.string.error_no_route_data, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val sessionTimestamp = routePoints.firstOrNull()?.absoluteTime ?: System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(sessionTimestamp))

        // Покажете датата
        val sessionDateTime = findViewById<TextView>(R.id.sessionDateTime)
        sessionDateTime.text = getString(R.string.created_date_format, formattedDate)


        val tvSessionTitle = findViewById<TextView>(R.id.tvSessionTitle)
// Използваме името на сесията, ако има такова, иначе показваме датата
        if (!race.name.isNullOrEmpty()) {
            tvSessionTitle.text = race.name
        } else {
            tvSessionTitle.text = formattedDate
        }

        Log.d("MapActivity", "Race name: ${race.name ?: "NULL"}")

        findViewById<View>(R.id.btnBack).setOnClickListener { onBackPressed() }

        chart = findViewById(R.id.chart)
        tabLayout = findViewById(R.id.tabs)
        
        if (isMapboxMode) {
            setupMapboxMap()
        } else {
            setupOsmdroidMap()
            // За OSMDroid зумваме веднага
            setupMapZoom()
        }
        
        // Setup zoom buttons
        setupZoomButtons()

        // Инициализираме маркера първо - синя точка
        if (!isMapboxMode) {
            marker = Marker(map).apply {
                position = routePoints.first().geoPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = getString(R.string.location_title)
                
                // Създаваме синя точка като икона
                val blueDot = createBlueDotMarker()
                setIcon(blueDot)
            }
        } else {
            setupMapboxMarker()
        }

        // Запазваме оригиналните overlays
        if (!isMapboxMode) {
            saveOriginalRoute()
        }
        
        // Показваме целия маршрут първоначално
        showFullRoute()

        // Пресмятаме точните секунди
        findViewById<TextView>(R.id.tvTotalTime).text = getString(R.string.time_format, formatTime(race.duration))

        try {
            android.util.Log.d("MapActivity", "🎨 Setting up chart...")
            setupChart(isMotorcycle)
            android.util.Log.d("MapActivity", "✅ setupChart complete")
            
            setupTabs(isMotorcycle)
            android.util.Log.d("MapActivity", "✅ setupTabs complete")
            
            updateChartData(currentMode, isMotorcycle)
            android.util.Log.d("MapActivity", "✅ updateChartData complete")
        } catch (e: Exception) {
            android.util.Log.e("MapActivity", "❌ ERROR setting up chart: ${e.message}", e)
            Toast.makeText(this, "Chart error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupOsmdroidMap() {
        val mapContainer = findViewById<FrameLayout>(R.id.mapContainer)
        val osmdroidMapView = findViewById<MapView>(R.id.mapRoute)
        
        map = osmdroidMapView
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        
        // Setup touch listener for showing zoom buttons
        setupMapTouchListener()
    }
    
    private fun setupMapboxMap() {
        val mapContainer = findViewById<FrameLayout>(R.id.mapContainer)
        val osmdroidMapView = findViewById<MapView>(R.id.mapRoute)
        
        if (mapContainer == null) {
            android.util.Log.e("MapActivity", "❌ mapContainer is null!")
            return
        }
        
        if (osmdroidMapView == null) {
            android.util.Log.e("MapActivity", "❌ osmdroidMapView is null!")
            return
        }
        
        // Remove OSMDroid MapView
        mapContainer.removeView(osmdroidMapView)
        
        // Create Mapbox MapView
        mapboxMapView = MapboxMapView(this)
        mapContainer.addView(mapboxMapView)
        
        // Calculate bounds from route points
        val bounds = calculateBounds(routePoints.map { it.geoPoint })
        
        // Set initial camera
        mapboxMapView?.mapboxMap?.setCamera(
            CameraOptions.Builder()
                .center(MapboxPoint.fromLngLat(
                    (bounds.minLon + bounds.maxLon) / 2.0,
                    (bounds.minLat + bounds.maxLat) / 2.0
                ))
                .zoom(calculateZoomLevel(bounds))
                .build()
        )
        
        // Disable scale bar and compass
        mapboxMapView?.scalebar?.enabled = false
        mapboxMapView?.compass?.enabled = false
        mapboxMapView?.attribution?.enabled = false
        
        // Setup touch listener for showing zoom buttons
        setupMapTouchListener()
        
        // Load custom map style from JSON (no caching issues!)
        loadMapboxStyleFromJson { style ->
            setupMapboxRoute(style)
        }
    }
    
    private fun setupMapboxRoute(style: Style) {
        mapboxStyle = style
        
        // Check if sources and layers already exist before adding them
        // Използваме try-catch за да проверим дали съществуват, но не хвърляме грешка ако не съществуват
        val routeSourceExists = try {
            val source = style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>(mapboxRouteSourceId)
            source != null
        } catch (e: Exception) {
            false
        }
        
        val routeLayerExists = try {
            style.styleLayerExists(mapboxRouteLayerId)
        } catch (e: Exception) {
            false
        }
        
        val markerSourceExists = try {
            val source = style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>("marker-source")
            source != null
        } catch (e: Exception) {
            false
        }
        
        val markerLayerExists = try {
            style.styleLayerExists("marker-layer")
        } catch (e: Exception) {
            false
        }
        
        Log.d("MapActivity", "🔍 Checking sources/layers: routeSource=$routeSourceExists, routeLayer=$routeLayerExists, markerSource=$markerSourceExists, markerLayer=$markerLayerExists")
        
        // Create GeoJSON source for dynamic route updates (only if doesn't exist)
        if (!routeSourceExists) {
            val emptyFeatureCollection = FeatureCollection.fromFeatures(emptyList())
            style.addSource(
                geoJsonSource(mapboxRouteSourceId) {
                    featureCollection(emptyFeatureCollection)
                }
            )
        }
        
        // Create GeoJSON source for marker (only if doesn't exist)
        // Use last point (end of route) for marker, or first if no points
        val markerPoint = if (routePoints.isNotEmpty()) {
            val lastPoint = routePoints.last().geoPoint
            MapboxPoint.fromLngLat(lastPoint.longitude, lastPoint.latitude)
        } else {
            val firstPoint = routePoints.firstOrNull()?.geoPoint
            if (firstPoint != null) {
                MapboxPoint.fromLngLat(firstPoint.longitude, firstPoint.latitude)
            } else {
                MapboxPoint.fromLngLat(0.0, 0.0)
            }
        }
        
        if (!markerSourceExists) {
            val markerFeature = Feature.fromGeometry(com.mapbox.geojson.Point.fromLngLat(markerPoint.longitude(), markerPoint.latitude()))
            style.addSource(
                geoJsonSource("marker-source") {
                    featureCollection(FeatureCollection.fromFeatures(listOf(markerFeature)))
                }
            )
        } else {
            // Marker source already exists, but update it to show the last point
            val markerFeature = Feature.fromGeometry(com.mapbox.geojson.Point.fromLngLat(markerPoint.longitude(), markerPoint.latitude()))
            val source = style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>("marker-source")
            source?.featureCollection(FeatureCollection.fromFeatures(listOf(markerFeature)))
        }
        
        // Add blue dot bitmap to style (ALWAYS add it, even if exists, to ensure it's there)
        try {
            // Check if image exists
            style.getStyleImage("blue-dot")
            Log.d("MapActivity", "✅ blue-dot image already exists")
        } catch (e: Exception) {
            // Image doesn't exist, add it
            Log.d("MapActivity", "⚠️ blue-dot image doesn't exist, creating it...")
            val blueDotDrawable = createBlueDotMarker()
            val blueDotBitmap = if (blueDotDrawable is android.graphics.drawable.BitmapDrawable) {
                blueDotDrawable.bitmap
            } else {
                val size = 48
                val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                blueDotDrawable.setBounds(0, 0, size, size)
                blueDotDrawable.draw(canvas)
                bitmap
            }
            style.addImage("blue-dot", blueDotBitmap)
            Log.d("MapActivity", "✅ blue-dot image added to style (${blueDotBitmap.width}x${blueDotBitmap.height})")
        }
        
        // ВАЖНО: Добавяме иконата отново за да се уверя че е там (понякога се губи при презареждане)
        try {
            val blueDotDrawable = createBlueDotMarker()
            val blueDotBitmap = if (blueDotDrawable is android.graphics.drawable.BitmapDrawable) {
                blueDotDrawable.bitmap
            } else {
                val size = 48
                val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                blueDotDrawable.setBounds(0, 0, size, size)
                blueDotDrawable.draw(canvas)
                bitmap
            }
            // Винаги добавяме иконата (дори ако вече съществува, за да се уверя че е там)
            style.addImage("blue-dot", blueDotBitmap)
            Log.d("MapActivity", "✅ blue-dot image ensured in style")
        } catch (e: Exception) {
            Log.e("MapActivity", "❌ Error adding blue-dot image", e)
        }
        
        // Create line layer for the route (ABOVE all labels) (only if doesn't exist)
        // ВАРИАНТ 1 + 2 + 3: Комбиниран подход - опитваме всички методи
        if (!routeLayerExists) {
            // ВАРИАНТ 1: Find the last label layer by name
            val labelLayerNames = listOf(
                "transit-labels", "waterway-labels", "poi-labels", 
                "road-labels", "place-labels",
                "place-city-lg-n", "place-city-md-n", "place-city-sm", 
                "place-town", "place-village"
            )
            
            var lastFoundLabelLayer: String? = null
            
            // Find the last existing label layer (check in reverse order)
            for (labelLayerName in labelLayerNames.reversed()) {
                try {
                    if (style.styleLayerExists(labelLayerName)) {
                        lastFoundLabelLayer = labelLayerName
                        break // Found the last one, stop searching
                    }
                } catch (e: Exception) {
                    // Continue searching
                }
            }
            
            if (lastFoundLabelLayer != null) {
                // ВАРИАНТ 1: Add route layer ABOVE the last found label layer
                style.addLayerAbove(
                    lineLayer(mapboxRouteLayerId, mapboxRouteSourceId) {
                        lineColor("#FF7805") // Orange color (matching the chart)
                        lineWidth(6.0)
                    },
                    lastFoundLabelLayer
                )
            } else {
                // ВАРИАНТ 2: No label layers found - add at the end (fallback)
                style.addLayer(
                    lineLayer(mapboxRouteLayerId, mapboxRouteSourceId) {
                        lineColor("#FF7805")
                        lineWidth(6.0)
                    }
                )
            }
            
            // ВАРИАНТ 3: Backup delayed check - even if we added above a layer,
            // double-check after a delay and move if needed (layers might load later)
            mapboxMapView?.postDelayed({
                try {
                    if (!style.styleLayerExists(mapboxRouteLayerId)) return@postDelayed
                    
                    // Try to find the last label layer again (might be loaded now)
                    var foundLabel: String? = null
                    for (labelName in labelLayerNames.reversed()) {
                        try {
                            if (style.styleLayerExists(labelName)) {
                                foundLabel = labelName
                                break
                            }
                        } catch (e: Exception) {
                            // Continue
                        }
                    }
                    
                    // If we found a label layer, ensure route is above it
                    if (foundLabel != null) {
                        // Remove route and re-add above label layer to ensure correct order
                        try {
                            style.removeStyleLayer(mapboxRouteLayerId)
                            style.addLayerAbove(
                                lineLayer(mapboxRouteLayerId, mapboxRouteSourceId) {
                                    lineColor("#FF7805")
                                    lineWidth(6.0)
                                },
                                foundLabel
                            )
                            
                            // Re-add marker above route
                            if (style.styleLayerExists("marker-layer")) {
                                style.removeStyleLayer("marker-layer")
                            }
                            style.addLayerAbove(
                                symbolLayer("marker-layer", "marker-source") {
                                    iconImage("blue-dot")
                                    iconSize(1.5)
                                    iconAllowOverlap(true)
                                    iconIgnorePlacement(true)
                                },
                                mapboxRouteLayerId
                            )
                        } catch (e: Exception) {
                            // Ignore errors
                        }
                    }
                } catch (e: Exception) {
                    // Ignore errors in delayed callback
                }
            }, 2000) // Wait 2 seconds for all layers to fully load
        }
        
        // Create symbol layer for marker (ABOVE route line - added after route) (only if doesn't exist)
        if (!markerLayerExists) {
            // Add marker layer ABOVE route layer to ensure it's on top of the route line
            style.addLayerAbove(
                symbolLayer("marker-layer", "marker-source") {
                    iconImage("blue-dot")
                    iconSize(1.5)
                    iconAllowOverlap(true)
                    iconIgnorePlacement(true)
                },
                mapboxRouteLayerId
            )
        }
        
        // Показваме целия маршрут първоначално
        showFullRoute()
        
        // Зумваме СЛЕД като стилът е зареден и картата е готова
        // Изчакваме малко за да се зареди картата преди да зумваме
        mapboxMapView?.post {
            setupMapZoom()
        }
    }
    
    private fun setupMapboxMarker() {
        // Not used anymore - marker is now a SymbolLayer created in setupMapboxRoute()
    }
    
    private fun drawMapboxRoute() {
        // Not used anymore - we use GeoJSON source for dynamic updates
        // Full route is drawn by showFullRoute()
    }
    
    private fun colorToHexString(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
    }
    
    private data class Bounds(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    )
    
    private fun calculateBounds(points: List<GeoPoint>): Bounds {
        if (points.isEmpty()) {
            return Bounds(0.0, 0.0, 0.0, 0.0)
        }
        
        var minLat = points[0].latitude
        var maxLat = points[0].latitude
        var minLon = points[0].longitude
        var maxLon = points[0].longitude
        
        for (point in points) {
            minLat = minOf(minLat, point.latitude)
            maxLat = maxOf(maxLat, point.latitude)
            minLon = minOf(minLon, point.longitude)
            maxLon = maxOf(maxLon, point.longitude)
        }
        
        return Bounds(minLat, maxLat, minLon, maxLon)
    }
    
    private fun calculateZoomLevel(bounds: Bounds): Double {
        val latDiff = bounds.maxLat - bounds.minLat
        val lonDiff = bounds.maxLon - bounds.minLon
        val maxDiff = maxOf(latDiff, lonDiff)
        
        return when {
            maxDiff > 0.1 -> 10.0.coerceAtMost(19.0).coerceAtLeast(8.0)
            maxDiff > 0.05 -> 12.0
            maxDiff > 0.01 -> 14.0
            maxDiff > 0.005 -> 16.0
            else -> 18.0
        }
    }

    // Добавете този помощен метод за форматиране на времето:
    private fun formatTimeForReader(timeValue: Float): String {
        // timeValue е вече в секунди (timestamp / 1000f)
        val totalSeconds = timeValue.toLong().coerceAtLeast(0)
        val min = totalSeconds / 60
        val sec = totalSeconds % 60
        return String.format("%02d:%02d", min, sec)
    }
    private fun setupChart(isMotorcycle: Boolean) {
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(false) // Изключваме вграденото scaling
        chart.setPinchZoom(false)
        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false

        // ИЗКЛЮЧВАМЕ НАПЪЛНО ИНЕРЦИЯТА/ЕЛАСТИЧНИЯ ЕФЕКТ
        chart.isDragDecelerationEnabled = false
        chart.dragDecelerationFrictionCoef = 0f

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(x: Float): String {
                    val totalSeconds = x.toLong()
                    val min = (totalSeconds / 60)
                    val sec = totalSeconds % 60
                    return String.format("%02d:%02d", min, sec)
                }
            }
        }

        // Настройваме границите с extra space (използваме относителен timestamp)
        if (routePoints.isNotEmpty()) {
            val startTime = routePoints.first().timestamp / 1000f
            val firstTime = 0f // Започваме от 0
            val lastTime = (routePoints.last().timestamp / 1000f) - startTime
            val duration = lastTime - firstTime

            chart.xAxis.axisMinimum = firstTime - duration
            chart.xAxis.axisMaximum = lastTime + duration

            chart.moveViewToX(firstTime - duration * 0.1f)

            chart.setVisibleXRangeMaximum(duration)

            val initialCenterX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
            try {
                updateReaderPosition(initialCenterX)
            } catch (e: Exception) {
                android.util.Log.e("MapActivity", "❌ Error in updateReaderPosition: ${e.message}", e)
            }
        }

        // Добавяме червена линия като LimitLine на X оста
        val centerLine = com.github.mikephil.charting.components.LimitLine(0f).apply {
            lineColor = android.graphics.Color.RED
            lineWidth = 2f
            enableDashedLine(10f, 10f, 0f)
        }

        // Използваме ViewPortHandler за рисуване на линията
        chart.setExtraOffsets(0f, 0f, 0f, 0f)

        // Override на renderer за рисуване на линията
        val originalRenderer = chart.renderer
        chart.renderer = object : com.github.mikephil.charting.renderer.LineChartRenderer(
            chart, chart.animator, chart.viewPortHandler
        ) {
            init {
                mChart = chart
                mAnimator = chart.animator
                mViewPortHandler = chart.viewPortHandler
            }

            override fun drawData(c: Canvas) {
                super.drawData(c)

                // Рисуваме червената линия в центъра
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.RED
                    strokeWidth = 3f
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
                }

                val centerX = mViewPortHandler.contentCenter.x
                c.drawLine(
                    centerX,
                    mViewPortHandler.contentTop(),
                    centerX,
                    mViewPortHandler.contentBottom(),
                    paint
                )

                // Добавяме текст за времето
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.RED
                    textSize = 35f
                    isAntiAlias = true
                    isFakeBoldText = true
                }

                val centerValue = (mChart.lowestVisibleX + mChart.highestVisibleX) / 2f
                val timeText = formatTimeForReader(centerValue)
                c.drawText(timeText, centerX + 10, mViewPortHandler.contentTop() + 40, textPaint)
            }
        }

        // Променливи за контрол на zoom/pan
        var isZooming = false
        var zoomCenterX = 0f

        // Запазваме границите на данните (относителен timestamp)
        val startTimeRef = if (routePoints.isNotEmpty()) routePoints.first().timestamp / 1000f else 0f
        val dataStartTime = 0f
        val dataEndTime = if (routePoints.isNotEmpty()) (routePoints.last().timestamp / 1000f) - startTimeRef else 0f

        // ScaleGestureDetector за zoom БЕЗ движение
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isZooming = true
                // Запазваме центъра преди zoom
                zoomCenterX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val deltaX = abs(detector.currentSpanX - detector.previousSpanX)
                val deltaY = abs(detector.currentSpanY - detector.previousSpanY)

                val scaleFactorX = if (deltaX > deltaY * 1.5) detector.scaleFactor else 1f
                val scaleFactorY = if (deltaY > detector.currentSpanX * 1.5) detector.scaleFactor else 1f

                if (deltaX <= deltaY * 1.5 && deltaY <= detector.currentSpanX * 1.5) {
                    // Зумваме и по двете оси
                    chart.zoom(detector.scaleFactor, detector.scaleFactor,
                        chart.width / 2f, chart.height / 2f,
                        com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT)
                } else {
                    // Зумваме само по една ос
                    chart.zoom(scaleFactorX, scaleFactorY,
                        chart.width / 2f, chart.height / 2f,
                        com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT)
                }

                // Връщаме се на същата позиция след zoom с проверка на границите
                var targetX = zoomCenterX - chart.visibleXRange / 2f
                val visibleRange = chart.visibleXRange

                // Проверяваме дали червената линия (центъра) не излиза извън данните
                val centerAfterMove = targetX + visibleRange / 2f
                if (centerAfterMove < dataStartTime) {
                    targetX = dataStartTime - visibleRange / 2f
                } else if (centerAfterMove > dataEndTime) {
                    targetX = dataEndTime - visibleRange / 2f
                }

                chart.moveViewToX(targetX)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isZooming = false
                // Обновваме позицията
                val centerX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                updateReaderPosition(centerX)
            }
        })

        // Touch listener - САМО ТУК ДОБАВЯМ ПРОВЕРКА
        chart.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)

            // Маркираме че потребителят е взаимодействал при първо докосване
            if (event.action == MotionEvent.ACTION_DOWN) {
                hasUserInteracted = true
            }

            // Позволяваме движение само ако НЕ зумваме
            if (!isZooming) {
                // Запазваме позицията преди движението
                val beforeCenter = (chart.lowestVisibleX + chart.highestVisibleX) / 2f

                // Изпълняваме движението
                chart.onTouchEvent(event)

                // ПРОВЕРКА: След движението проверяваме дали червената линия е в границите
                val currentCenter = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                val visibleRange = chart.visibleXRange

                // Ако червената линия излиза извън данните, връщаме я на границата
                if (currentCenter < dataStartTime) {
                    chart.moveViewToX(dataStartTime - visibleRange / 2f)
                    // Спираме всякакво инерционно движение
                    chart.isDragEnabled = false
                    chart.postDelayed({ chart.isDragEnabled = true }, 1)
                } else if (currentCenter > dataEndTime) {
                    chart.moveViewToX(dataEndTime - visibleRange / 2f)
                    // Спираме всякакво инерционно движение
                    chart.isDragEnabled = false
                    chart.postDelayed({ chart.isDragEnabled = true }, 1)
                }
            }

            // Обновяваме при край на докосване
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                val centerX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                updateReaderPosition(centerX)
                chart.invalidate() // Force redraw
            }

            true
        }

        // Gesture listener
        chart.setOnChartGestureListener(object : OnChartGestureListener {
            override fun onChartGestureStart(me: MotionEvent?, lastGesture: ChartTouchListener.ChartGesture?) {}
            override fun onChartGestureEnd(me: MotionEvent?, lastGesture: ChartTouchListener.ChartGesture?) {}
            override fun onChartLongPressed(me: MotionEvent?) {}
            override fun onChartDoubleTapped(me: MotionEvent?) {
                chart.fitScreen()
                val centerX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                updateReaderPosition(centerX)
            }
            override fun onChartSingleTapped(me: MotionEvent?) {}
            override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {
                // НАПЪЛНО БЛОКИРАМЕ FLING - без инерция, без еластичен ефект
                // Не правим нищо тук
            }
            override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {}

            override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
                // Маркираме че потребителят е взаимодействал при движение
                hasUserInteracted = true
                
                if (!isZooming) {
                    val currentCenterX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                    val visibleRange = chart.visibleXRange

                    // ПРОВЕРКА: Не позволяваме червената линия да излиза извън данните
                    if (currentCenterX < dataStartTime) {
                        chart.moveViewToX(dataStartTime - visibleRange / 2f)
                        // Спираме инерцията
                        chart.isDragEnabled = false
                        chart.postDelayed({ chart.isDragEnabled = true }, 1)
                    } else if (currentCenterX > dataEndTime) {
                        chart.moveViewToX(dataEndTime - visibleRange / 2f)
                        // Спираме инерцията
                        chart.isDragEnabled = false
                        chart.postDelayed({ chart.isDragEnabled = true }, 1)
                    } else {
                        updateReaderPosition(currentCenterX)
                        chart.invalidate()
                    }
                }
            }
        })

        // Цветове
        chart.xAxis.textColor = android.graphics.Color.WHITE
        chart.axisLeft.textColor = android.graphics.Color.WHITE
        chart.legend.textColor = android.graphics.Color.WHITE

        // Force initial draw
        chart.invalidate()
    }

    private fun setupMapZoom() {
        if (routePoints.isEmpty()) return
        
        val allGeoPoints = routePoints.map { it.geoPoint }
        
        if (isMapboxMode) {
            // Mapbox zoom setup - използваме същата логика като в RecyclerView
            if (allGeoPoints.size >= 2) {
                val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPointsSafe(allGeoPoints)
                
                // Изчакваме картата да се зареди преди да зумваме
                mapboxMapView?.post {
                    // Get map view dimensions
                    val mapWidth = mapboxMapView?.width ?: 0
                    val mapHeight = mapboxMapView?.height ?: 0
                    
                    if (mapWidth > 0 && mapHeight > 0) {
                        // Използваме 20dp padding за по-близък зум
                        val density = resources.displayMetrics.density
                        val paddingPx = 20.0 * density
                        
                        // Calculate padding as percentage of map size
                        val paddingWidthRatio = (paddingPx * 2) / mapWidth  // Left + right padding
                        val paddingHeightRatio = (paddingPx * 2) / mapHeight  // Top + bottom padding
                        
                        // Calculate bounding box dimensions in degrees
                        val latDiff = boundingBox.latNorth - boundingBox.latSouth
                        val lonDiff = boundingBox.lonEast - boundingBox.lonWest
                        
                        // Calculate required padding in degrees to achieve 20dp padding on screen
                        val latPadding = latDiff * paddingHeightRatio / (1.0 - paddingHeightRatio)
                        val lonPadding = lonDiff * paddingWidthRatio / (1.0 - paddingWidthRatio)
                        
                        // Use the larger padding to ensure padding on all sides
                        val padding = maxOf(latPadding, lonPadding)
                        
                        // Create adjusted bounding box with calculated padding
                        val adjustedBox = org.osmdroid.util.BoundingBox(
                            boundingBox.latNorth + padding,
                            boundingBox.lonEast + padding,
                            boundingBox.latSouth - padding,
                            boundingBox.lonWest - padding
                        )
                        
                        // Calculate center
                        val centerLat = (adjustedBox.latSouth + adjustedBox.latNorth) / 2.0
                        val centerLon = (adjustedBox.lonWest + adjustedBox.lonEast) / 2.0
                        
                        // Calculate zoom to fit the adjusted bounding box in the map view
                        val adjustedLatDiff = adjustedBox.latNorth - adjustedBox.latSouth
                        val adjustedLonDiff = adjustedBox.lonEast - adjustedBox.lonWest
                        
                        // Calculate zoom based on which dimension is larger (width or height)
                        val aspectRatio = mapWidth.toDouble() / mapHeight.toDouble()
                        val routeAspectRatio = adjustedLonDiff / adjustedLatDiff
                        
                        // Determine which dimension constrains the zoom
                        // За height (север-юг) използваме много по-малък коефициент за много по-отдалечен зум
                        val zoom = if (routeAspectRatio > aspectRatio) {
                            // Route is wider - constrained by width (изток-запад) - оставяме както е
                            kotlin.math.log2(360.0 / adjustedLonDiff) - kotlin.math.log2(aspectRatio) + 0.5
                        } else {
                            // Route is taller - constrained by height (север-юг) - намаляваме зума много повече за много по-отдалечено
                            kotlin.math.log2(360.0 / adjustedLatDiff) - 1.5
                        }.coerceIn(3.0, 19.0)
                        
                        mapboxMapView?.mapboxMap?.setCamera(
                            CameraOptions.Builder()
                                .center(MapboxPoint.fromLngLat(centerLon, centerLat))
                                .zoom(zoom)
                                .build()
                        )
                    } else {
                        // Fallback if map dimensions not available yet
                        val bounds = calculateBounds(allGeoPoints)
                        val zoomLevel = calculateZoomLevel(bounds)
                        
                        mapboxMapView?.mapboxMap?.setCamera(
                            CameraOptions.Builder()
                                .center(MapboxPoint.fromLngLat(
                                    (bounds.minLon + bounds.maxLon) / 2.0,
                                    (bounds.minLat + bounds.maxLat) / 2.0
                                ))
                                .zoom(zoomLevel)
                                .build()
                        )
                    }
                }
            } else {
                // Ако има само една точка, центрираме върху нея
                val point = allGeoPoints[0]
                mapboxMapView?.mapboxMap?.setCamera(
                    CameraOptions.Builder()
                        .center(MapboxPoint.fromLngLat(point.longitude, point.latitude))
                        .zoom(15.0)
                        .build()
                )
            }
        } else {
            // OSMDroid zoom setup
            if (allGeoPoints.size >= 2) {
                // Използваме същата логика като в RecyclerView
                val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPointsSafe(allGeoPoints)
                
                val latDiff = boundingBox.latNorth - boundingBox.latSouth
                val lonDiff = boundingBox.lonEast - boundingBox.lonWest
                val padding = kotlin.math.max(latDiff, lonDiff) * 0.15
                
                val adjustedBox = org.osmdroid.util.BoundingBox(
                    boundingBox.latNorth + padding,
                    boundingBox.lonEast + padding,
                    boundingBox.latSouth - padding,
                    boundingBox.lonWest - padding
                )
                
                map.post {
                    map.zoomToBoundingBox(adjustedBox, false)
                    map.invalidate()
                }
            } else {
                // Ако има само една точка, центрираме върху нея
                val point = allGeoPoints[0]
                map.controller.setCenter(point)
                map.controller.setZoom(15.0)
            }
        }
    }
    
    private fun setupMapTouchListener() {
        if (isMapboxMode) {
            mapboxMapView?.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    showZoomButtons()
                }
                false // Не пречим на нормалната работа на картата
            }
        } else {
            map.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    showZoomButtons()
                }
                false // Не пречим на нормалната работа на картата
            }
        }
    }
    
    private fun showZoomButtons() {
        val zoomButtonsContainer = findViewById<View>(R.id.zoomButtonsContainer) ?: return
        val routeButton = findViewById<View>(R.id.btnShowRoute) ?: return
        
        // Отменяме предишния hide runnable ако има такъв
        zoomButtonsHideRunnable?.let { zoomButtonsHandler.removeCallbacks(it) }
        
        // Показваме бутоните с fade-in анимация
        zoomButtonsContainer.visibility = View.VISIBLE
        zoomButtonsContainer.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
        
        routeButton.visibility = View.VISIBLE
        routeButton.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
        
        // След 5 секунди ги скриваме с fade-out
        zoomButtonsHideRunnable = Runnable {
            zoomButtonsContainer.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    zoomButtonsContainer.visibility = View.GONE
                }
                .start()
            
            routeButton.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    routeButton.visibility = View.GONE
                }
                .start()
        }
        zoomButtonsHandler.postDelayed(zoomButtonsHideRunnable!!, 5000)
    }
    
    private fun setupZoomButtons() {
        val btnZoomIn = findViewById<android.widget.Button>(R.id.btnZoomIn)
        val btnZoomOut = findViewById<android.widget.Button>(R.id.btnZoomOut)
        val btnShowRoute = findViewById<android.widget.ImageButton>(R.id.btnShowRoute)
        
        // Setup route button
        btnShowRoute?.setOnClickListener {
            // Показваме целия маршрут
            showFullRoute()
            
            // Зумваме отново за да се вижда целия маршрут
            // Изчакваме малко за да се обнови маршрутът преди да зумваме
            mapboxMapView?.postDelayed({
                setupMapZoom()
            }, 200)
        }
        
        btnZoomIn?.setOnClickListener {
            if (isMapboxMode) {
                // Mapbox zoom in
                val cameraState = mapboxMapView?.mapboxMap?.cameraState
                val currentZoom = cameraState?.zoom ?: 10.0
                val currentCenter = cameraState?.center
                val newZoom = (currentZoom + 1.0).coerceAtMost(20.0)
                
                if (currentCenter != null) {
                    mapboxMapView?.mapboxMap?.setCamera(
                        CameraOptions.Builder()
                            .center(currentCenter)
                            .zoom(newZoom)
                            .build()
                    )
                } else {
                    mapboxMapView?.mapboxMap?.setCamera(
                        CameraOptions.Builder()
                            .zoom(newZoom)
                            .build()
                    )
                }
            } else {
                // OSMDroid zoom in
                map.controller.zoomIn()
            }
        }
        
        btnZoomOut?.setOnClickListener {
            if (isMapboxMode) {
                // Mapbox zoom out
                val cameraState = mapboxMapView?.mapboxMap?.cameraState
                val currentZoom = cameraState?.zoom ?: 10.0
                val currentCenter = cameraState?.center
                val newZoom = (currentZoom - 1.0).coerceAtLeast(0.0)
                
                if (currentCenter != null) {
                    mapboxMapView?.mapboxMap?.setCamera(
                        CameraOptions.Builder()
                            .center(currentCenter)
                            .zoom(newZoom)
                            .build()
                    )
                } else {
                    mapboxMapView?.mapboxMap?.setCamera(
                        CameraOptions.Builder()
                            .zoom(newZoom)
                            .build()
                    )
                }
            } else {
                // OSMDroid zoom out
                map.controller.zoomOut()
            }
        }
    }

    private fun createBlueDotMarker(): android.graphics.drawable.Drawable {
        val size = 48 // Още по-голям размер - 2.5x по-голяма от дебелината на маршрута
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Външен кръг (бял) с по-силна сянка
        val outerPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            isAntiAlias = true
            setShadowLayer(6f, 0f, 3f, android.graphics.Color.argb(150, 0, 0, 0))
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3, outerPaint)
        
        // Вътрешен кръг (син) - като глава на змия
        val innerPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#1976D2") // По-тъмен син за по-добър контраст
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 8, innerPaint)
        
        // Добавяме сянка за 3D ефект
        val shadowPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(80, 0, 0, 0)
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f + 2, size / 2f + 2, (size / 2f) - 8, shadowPaint)
        
        return android.graphics.drawable.BitmapDrawable(resources, bitmap)
    }

    private fun saveOriginalRoute() {
        originalRouteOverlays.clear()
        if (routePoints.size > 1) {
            for (i in 0 until routePoints.size - 1) {
                val startPoint = routePoints[i]
                val endPoint = routePoints[i + 1]

                // Изчисляваме ускорението (промяна в скоростта)
                val acceleration = endPoint.speed - startPoint.speed

                // Определяме максимално ускорение за нормализация (примерно ±30 km/h промяна)
                val maxAcceleration = 30f

                // Нормализираме от -1 до +1
                val accelRatio = (acceleration / maxAcceleration).coerceIn(-1f, 1f)

                // Определяме цвета според ускорението
                val color = when {
                    startPoint.speed < 2f -> {
                        // Ако сме почти спрели - тъмно червено
                        Color.rgb(139, 0, 0)
                    }
                    accelRatio > 0.66f -> {
                        // Силно ускорение - тъмно зелено
                        Color.rgb(0, 128, 0)
                    }
                    accelRatio > 0.33f -> {
                        // Средно ускорение - зелено
                        Color.rgb(0, 200, 0)
                    }
                    accelRatio > 0 -> {
                        // Леко ускорение - жълто-зелено
                        Color.rgb(154, 205, 50)
                    }
                    accelRatio == 0f -> {
                        // Постоянна скорост - жълто
                        Color.rgb(255, 255, 0)
                    }
                    accelRatio > -0.5f -> {
                        // Леко забавяне - оранжево
                        Color.rgb(255, 165, 0)
                    }
                    else -> {
                        // Силно забавяне - червено-оранжево
                        val ratio = (-accelRatio - 0.5f) * 2
                        val green = (165 * (1 - ratio)).toInt()
                        Color.rgb(255, green, 0)
                    }
                }

                // Създаваме сегмент
                val segmentPolyline = Polyline().apply {
                    setPoints(listOf(startPoint.geoPoint, endPoint.geoPoint))
                    this.color = color
                    outlinePaint.strokeWidth = 18f
                }
                originalRouteOverlays.add(segmentPolyline)
            }
        }
    }

    private fun showFullRoute() {
        if (isMapboxMode) {
            // Mapbox: draw full route using GeoJSON
            val style = mapboxStyle ?: return
            
            try {
                // Build a single continuous LineString for the entire route
                val coordinates = routePoints.map { point ->
                    MapboxPoint.fromLngLat(point.geoPoint.longitude, point.geoPoint.latitude)
                }
                
                // НЕ обновяваме позицията на маркера тук - оставяме го там където е
                // Маркерът ще остане на текущата позиция дори когато показваме целия маршрут
                
                val lineString = LineString.fromLngLats(coordinates)
                val feature = Feature.fromGeometry(lineString)
                
                // Update the GeoJSON source with the full route
                val source = style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>(mapboxRouteSourceId)
                source?.featureCollection(FeatureCollection.fromFeatures(listOf(feature)))
            } catch (e: Exception) {
                Log.e("MapActivity", "Error drawing full Mapbox route", e)
            }
        } else {
            // OSMDroid: изчистваме текущите overlays
            map.overlays.clear()
            
            // Добавяме целия маршрут ПЪРВО (под точката)
            map.overlays.addAll(originalRouteOverlays)
            
            // Добавяме маркера НАКРАЯ (върху маршрута)
            map.overlays.add(marker)
            
            map.invalidate()
        }
    }

    private fun drawRouteUpToIndex(index: Int) {
        // Изчищаме текущите overlays
        map.overlays.clear()
        
        // Добавяме маршрута до дадения индекс ПЪРВО (под точката)
        for (i in 0 until minOf(index, originalRouteOverlays.size)) {
            map.overlays.add(originalRouteOverlays[i])
        }
        
        // Добавяме маркера НАКРАЯ (върху маршрута)
        map.overlays.add(marker)
        
        map.invalidate()
    }
    
    private fun drawRouteUpToInterpolatedPosition(beforeIndex: Int, interpolatedPoint: GeoPoint) {
        // Изчищаме текущите overlays
        map.overlays.clear()
        
        // Draw colored segments up to (but NOT including) beforeIndex
        // This keeps the original color coding (green/orange/etc)
        for (i in 0 until minOf(beforeIndex, originalRouteOverlays.size)) {
            map.overlays.add(originalRouteOverlays[i])
        }
        
        // Create final colored segment from beforeIndex to interpolated position
        // Calculate color based on acceleration (same logic as saveOriginalRoute)
        if (beforeIndex in routePoints.indices && beforeIndex < routePoints.size - 1) {
            val startPoint = routePoints[beforeIndex]
            val nextPoint = routePoints[beforeIndex + 1]
            
            // Calculate acceleration for color
            val acceleration = nextPoint.speed - startPoint.speed
            val maxAcceleration = 30f
            val accelRatio = (acceleration / maxAcceleration).coerceIn(-1f, 1f)
            
            // Determine color based on acceleration (same logic as original)
            val color = when {
                startPoint.speed < 2f -> Color.rgb(139, 0, 0) // Dark red - stopped
                accelRatio > 0.66f -> Color.rgb(0, 128, 0)   // Dark green - strong acceleration
                accelRatio > 0.33f -> Color.rgb(0, 200, 0)   // Green - medium acceleration
                accelRatio > 0 -> Color.rgb(154, 205, 50)    // Yellow-green - light acceleration
                accelRatio == 0f -> Color.rgb(255, 255, 0)   // Yellow - constant speed
                accelRatio > -0.5f -> Color.rgb(255, 165, 0) // Orange - light braking
                else -> {
                    val ratio = (-accelRatio - 0.5f) * 2
                    val green = (165 * (1 - ratio)).toInt()
                    Color.rgb(255, green, 0) // Red-orange - strong braking
                }
            }
            
            // Create the final segment with proper color
            val finalSegment = Polyline(map)
            finalSegment.setPoints(listOf(startPoint.geoPoint, interpolatedPoint))
            finalSegment.color = color
            finalSegment.outlinePaint.strokeWidth = 18f
            map.overlays.add(finalSegment)
        }
        
        // Добавяме маркера НАКРАЯ (върху маршрута) - главата на змията
        map.overlays.add(marker)
        
        map.invalidate()
    }

    private fun startRouteDrawingTimer() {
        // Отменяме предишния таймер ако има такъв
        routeDrawingRunnable?.let { routeDrawingTimer?.removeCallbacks(it) }
        
        routeDrawingRunnable = Runnable {
            // След 3 секунди показваме целия маршрут
            showFullRoute()
            isDrawingRoute = false
        }
        
        routeDrawingTimer = android.os.Handler(android.os.Looper.getMainLooper())
        routeDrawingRunnable?.let { routeDrawingTimer?.postDelayed(it, 3000) }
    }

    private fun stopRouteDrawingTimer() {
        routeDrawingRunnable?.let { routeDrawingTimer?.removeCallbacks(it) }
    }

    private fun interpolateSpeedAndAngle(targetTimeSeconds: Float, startTimeRef: Float): Pair<Float, Float> {
        if (routePoints.size < 2) {
            val point = routePoints.firstOrNull()
            return Pair(point?.speed ?: 0f, point?.angle ?: 0f)
        }
        
        // Намираме двете точки между които се намираме
        var beforeIndex = 0
        var afterIndex = 0
        
        for (i in 0 until routePoints.size - 1) {
            val currentTime = (routePoints[i].timestamp / 1000f) - startTimeRef
            val nextTime = (routePoints[i + 1].timestamp / 1000f) - startTimeRef
            
            if (targetTimeSeconds >= currentTime && targetTimeSeconds <= nextTime) {
                beforeIndex = i
                afterIndex = i + 1
                break
            }
        }
        
        // Ако сме извън диапазона, връщаме граничните стойности
        val firstTime = 0f
        val lastTime = (routePoints.last().timestamp / 1000f) - startTimeRef
        
        if (targetTimeSeconds <= firstTime) {
            val point = routePoints.first()
            return Pair(point.speed, point.angle)
        }
        if (targetTimeSeconds >= lastTime) {
            val point = routePoints.last()
            return Pair(point.speed, point.angle)
        }
        
        // Линейна интерполация между beforeIndex и afterIndex
        val p1 = routePoints[beforeIndex]
        val p2 = routePoints[afterIndex]
        
        val t1 = (p1.timestamp / 1000f) - startTimeRef
        val t2 = (p2.timestamp / 1000f) - startTimeRef
        
        // Изчисляваме интерполационния фактор (0 до 1)
        val factor = if (t2 > t1) {
            ((targetTimeSeconds - t1) / (t2 - t1)).coerceIn(0f, 1f)
        } else {
            0f
        }
        
        // Интерполираме speed и angle
        val interpolatedSpeed = p1.speed + (p2.speed - p1.speed) * factor
        val interpolatedAngle = p1.angle + (p2.angle - p1.angle) * factor
        
        return Pair(interpolatedSpeed, interpolatedAngle)
    }
    
    private fun updateReaderPosition(timeInSeconds: Float) {
        // Find surrounding points for smooth interpolation
        val (index, interpolatedPoint) = findInterpolatedPosition(timeInSeconds)
        if (index in routePoints.indices) {
            // ИНТЕРПОЛАЦИЯ НА SPEED И ANGLE между точките!
            val startTime = if (routePoints.isNotEmpty()) routePoints.first().timestamp / 1000f else 0f
            val (interpolatedSpeed, interpolatedAngle) = interpolateSpeedAndAngle(timeInSeconds, startTime)

            if (isMapboxMode) {
                // Mapbox: обновяваме И маркера И линията заедно в drawMapboxRouteUpToIndex
                // за синхронизация (глава и тяло на змията заедно)
                if (hasUserInteracted) {
                    drawMapboxRouteUpToIndex(index, interpolatedPoint)
                    isDrawingRoute = true
                    startRouteDrawingTimer()
                } else {
                    // Ако потребителят не е взаимодействал, обновяваме само маркера
                    updateMapboxMarkerPosition(interpolatedPoint)
                }
            } else {
                // OSMDroid: Use interpolated position for smooth movement
                marker.position = interpolatedPoint
                map.controller.setCenter(interpolatedPoint)
                
                // Рисуваме маршрута до интерполираната позиция само ако потребителят е взаимодействал
                if (hasUserInteracted) {
                    drawRouteUpToInterpolatedPosition(index, interpolatedPoint)
                    isDrawingRoute = true
                    // Стартираме таймера за показване на целия маршрут
                    startRouteDrawingTimer()
                }
                
                map.invalidate()
            }

            val currentProfileId = ProfileStorage.getSelectedProfileId(this)
            val profiles = ProfileStorage.loadProfiles(this)
            val profile = profiles.find { it.id == currentProfileId }

            findViewById<TextView>(R.id.tvReaderSpeed).text =
                "${getString(R.string.speed_label)} ${"%.0f".format(interpolatedSpeed)} ${getString(R.string.speed_unit)}"

            if (profile?.vehicleType == Profile.VehicleType.MOTORCYCLE) {
                findViewById<TextView>(R.id.tvReaderAngle).apply {
                    visibility = View.VISIBLE
                    text = "${getString(R.string.angle_label)} ${"%.0f".format(interpolatedAngle)}${getString(R.string.angle_unit)}"
                }
            } else {
                findViewById<TextView>(R.id.tvReaderAngle).visibility = View.GONE
            }
        }
    }
    
    private fun updateMapboxMarkerPosition(geoPoint: GeoPoint) {
        // Използваме директно интерполираната позиция, точно както в OSMDroid режима
        // findInterpolatedPosition вече прави интерполацията, така че не трябва допълнителна
        setMarkerPositionDirect(geoPoint.latitude, geoPoint.longitude)
    }
    
    private fun setMarkerPositionDirect(lat: Double, lon: Double) {
        val point = MapboxPoint.fromLngLat(lon, lat)
        val style = mapboxStyle ?: return
        
        // Обновяваме камерата БЕЗ анимация - запазваме текущия zoom, pitch, bearing
        // Това е еквивалент на map.controller.setCenter() в OSMDroid
        val currentCamera = mapboxMapView?.mapboxMap?.cameraState
        mapboxMapView?.mapboxMap?.setCamera(
            CameraOptions.Builder()
                .center(point)
                .zoom(currentCamera?.zoom ?: 17.0) // Запазваме текущия zoom
                .pitch(currentCamera?.pitch ?: 45.0) // Запазваме текущия pitch
                .bearing(currentCamera?.bearing ?: 0.0) // Запазваме текущия bearing
                .build()
        )
        
        // Обновяваме маркера
        try {
            val markerFeature = Feature.fromGeometry(com.mapbox.geojson.Point.fromLngLat(lon, lat))
            val source = style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>("marker-source")
            source?.featureCollection(FeatureCollection.fromFeatures(listOf(markerFeature)))
        } catch (e: Exception) {
            Log.e("MapActivity", "❌ Error updating marker position", e)
        }
    }
    
    private fun drawMapboxRouteUpToIndex(index: Int, interpolatedPoint: GeoPoint) {
        val style = mapboxStyle ?: return
        
        try {
            // 1. Обновяваме маркера ПЪРВО (глава на змията) - ДИРЕКТНО без Runnable
            updateMapboxMarkerPosition(interpolatedPoint)
            
            // 2. След това обновяваме линията (тялото на змията) - ДИРЕКТНО без Runnable
            // Build a single continuous LineString from start to interpolatedPoint
            val coordinates = mutableListOf<MapboxPoint>()
            
            // Add all points up to index
            for (i in 0..minOf(index, routePoints.size - 1)) {
                val point = routePoints[i]
                coordinates.add(MapboxPoint.fromLngLat(point.geoPoint.longitude, point.geoPoint.latitude))
            }
            
            // Add interpolated point as the final point
            coordinates.add(MapboxPoint.fromLngLat(interpolatedPoint.longitude, interpolatedPoint.latitude))
            
            // Create a single LineString feature
            val lineString = LineString.fromLngLats(coordinates)
            val feature = Feature.fromGeometry(lineString)
            
            // Update the GeoJSON source with the new line - ДИРЕКТНО
            val source = style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>(mapboxRouteSourceId)
            source?.featureCollection(FeatureCollection.fromFeatures(listOf(feature)))
            
            lastDrawnIndex = index
        } catch (e: Exception) {
            Log.e("MapActivity", "Error updating Mapbox route", e)
        }
    }

    private fun setupTabs(isMotorcycle: Boolean) {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_speed))

        // Добавяме таб за ъгъл само за мотоциклети
        if (isMotorcycle) {
            tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_angle))
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentMode = if (tab?.position == 0) Mode.SPEED else Mode.ANGLE
                updateChartData(currentMode, isMotorcycle)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateChartData(mode: Mode, isMotorcycle: Boolean) {
        android.util.Log.d("MapActivity", "📊 updateChartData - routePoints: ${routePoints.size}, isMotorcycle: $isMotorcycle, mode: $mode")
        
        // Използваме относителен timestamp (спрямо началото на сесията)
        val startTime = if (routePoints.isNotEmpty()) routePoints.first().timestamp / 1000f else 0f
        val speedEntries = routePoints.map { Entry((it.timestamp / 1000f) - startTime, it.speed) }
        val angleEntries = if (isMotorcycle) {
            routePoints.map { Entry((it.timestamp / 1000f) - startTime, it.angle) }
        } else {
            emptyList()
        }
        
        android.util.Log.d("MapActivity", "   speedEntries: ${speedEntries.size}, angleEntries: ${angleEntries.size}")
        if (speedEntries.isNotEmpty()) {
            android.util.Log.d("MapActivity", "   First speed entry: x=${speedEntries.first().x}, y=${speedEntries.first().y}")
            android.util.Log.d("MapActivity", "   Last speed entry: x=${speedEntries.last().x}, y=${speedEntries.last().y}")
        }
        if (angleEntries.isNotEmpty()) {
            android.util.Log.d("MapActivity", "   First angle entry: x=${angleEntries.first().x}, y=${angleEntries.first().y}")
            android.util.Log.d("MapActivity", "   Last angle entry: x=${angleEntries.last().x}, y=${angleEntries.last().y}")
        }

        val activeColor = if (mode == Mode.SPEED) Color.rgb(252, 120, 5) else Color.rgb(5, 252, 227)
        val fadedColor = if (mode == Mode.SPEED) Color.argb(105,5, 252, 227) else Color.argb(105,252, 120, 5)

        val speedDataSet = LineDataSet(speedEntries, getString(R.string.chart_speed_legend)).apply {
            color = if (mode == Mode.SPEED) activeColor else fadedColor
            lineWidth = if (mode == Mode.SPEED) 2f else 1f
            setDrawValues(false)
            setDrawCircles(false)
            setMode(LineDataSet.Mode.LINEAR)  // БЕЗ interpolation - показва точните данни!
            if (mode != Mode.SPEED) enableDashedLine(10f, 5f, 0f)
        }

        val lineData = if (isMotorcycle) {
            val angleDataSet = LineDataSet(angleEntries, getString(R.string.chart_angle_legend)).apply {
                color = if (mode == Mode.ANGLE) activeColor else fadedColor
                lineWidth = if (mode == Mode.ANGLE) 2f else 1f
                setDrawValues(false)
                setDrawCircles(false)
                setMode(LineDataSet.Mode.LINEAR)  // БЕЗ interpolation - показва точните angle данни!
                if (mode != Mode.ANGLE) enableDashedLine(10f, 5f, 0f)
            }
            LineData(speedDataSet, angleDataSet)
        } else {
            LineData(speedDataSet)
        }

        chart.data = lineData

        val yAxis = chart.axisLeft
        when (mode) {
            Mode.SPEED -> {
                val maxSpeed = routePoints.maxOfOrNull { it.speed } ?: 200f
                yAxis.axisMinimum = 0f
                yAxis.axisMaximum = if (maxSpeed > 200) maxSpeed * 1.1f else 200f
                yAxis.setDrawZeroLine(true)
                yAxis.zeroLineColor = Color.GRAY
                yAxis.zeroLineWidth = 1f
            }
            Mode.ANGLE -> {
                yAxis.axisMinimum = -90f
                yAxis.axisMaximum = 90f
                yAxis.setDrawZeroLine(true)
                yAxis.zeroLineColor = Color.GRAY
                yAxis.zeroLineWidth = 1f
            }
        }

        chart.invalidate()
    }

    private fun findClosestIndexToTime(targetTimeSeconds: Float): Int {
        if (routePoints.isEmpty()) return 0
        
        val startTime = routePoints.first().timestamp / 1000f
        var closestIndex = 0
        var minDiff = Float.MAX_VALUE
        routePoints.forEachIndexed { index, routePoint ->
            val pointTimeSeconds = (routePoint.timestamp / 1000f) - startTime
            val diff = Math.abs(pointTimeSeconds - targetTimeSeconds)
            if (diff < minDiff) {
                minDiff = diff
                closestIndex = index
            }
        }
        return closestIndex
    }
    
    /**
     * Find interpolated position using Catmull-Rom spline for smooth movement
     * Returns pair of (closest index, interpolated GeoPoint)
     */
    private fun findInterpolatedPosition(targetTimeSeconds: Float): Pair<Int, GeoPoint> {
        if (routePoints.size < 2) {
            return Pair(0, routePoints.firstOrNull()?.geoPoint ?: GeoPoint(0.0, 0.0))
        }
        
        val startTime = routePoints.first().timestamp / 1000f
        
        // Find the two points that surround the target time
        var beforeIndex = 0
        var afterIndex = 0
        
        for (i in 0 until routePoints.size - 1) {
            val currentTime = (routePoints[i].timestamp / 1000f) - startTime
            val nextTime = (routePoints[i + 1].timestamp / 1000f) - startTime
            
            if (targetTimeSeconds >= currentTime && targetTimeSeconds <= nextTime) {
                beforeIndex = i
                afterIndex = i + 1
                break
            }
        }
        
        // Handle edge cases
        val firstRelativeTime = 0f
        val lastRelativeTime = (routePoints.last().timestamp / 1000f) - startTime
        if (targetTimeSeconds <= firstRelativeTime) {
            return Pair(0, routePoints.first().geoPoint)
        }
        if (targetTimeSeconds >= lastRelativeTime) {
            return Pair(routePoints.size - 1, routePoints.last().geoPoint)
        }
        
        // Get the 4 points needed for Catmull-Rom: P0, P1, P2, P3
        val p0Index = (beforeIndex - 1).coerceAtLeast(0)
        val p1Index = beforeIndex
        val p2Index = afterIndex
        val p3Index = (afterIndex + 1).coerceAtMost(routePoints.size - 1)
        
        val p0 = routePoints[p0Index].geoPoint
        val p1 = routePoints[p1Index].geoPoint
        val p2 = routePoints[p2Index].geoPoint
        val p3 = routePoints[p3Index].geoPoint
        
        // Calculate interpolation factor (0 to 1) between p1 and p2
        val t1 = (routePoints[p1Index].timestamp / 1000f) - startTime
        val t2 = (routePoints[p2Index].timestamp / 1000f) - startTime
        val t = if (t2 > t1) {
            ((targetTimeSeconds - t1) / (t2 - t1)).coerceIn(0f, 1f)
        } else {
            0f
        }
        
        // Catmull-Rom spline interpolation
        val interpolatedLat = catmullRomInterpolate(
            p0.latitude, p1.latitude, p2.latitude, p3.latitude, t
        )
        val interpolatedLon = catmullRomInterpolate(
            p0.longitude, p1.longitude, p2.longitude, p3.longitude, t
        )
        
        val interpolatedPoint = GeoPoint(interpolatedLat, interpolatedLon)
        
        // CRITICAL: Always return beforeIndex to ensure line never goes past the marker
        // The line should draw up to beforeIndex, then a final segment to interpolatedPoint
        return Pair(beforeIndex, interpolatedPoint)
    }
    
    /**
     * Catmull-Rom spline interpolation
     * Creates smooth curves that pass through control points
     */
    private fun catmullRomInterpolate(p0: Double, p1: Double, p2: Double, p3: Double, t: Float): Double {
        val t2 = t * t
        val t3 = t2 * t
        
        // Catmull-Rom formula
        return 0.5 * (
            2.0 * p1 +
            (-p0 + p2) * t +
            (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 +
            (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3
        )
    }
    
    /**
     * Calculate simple distance between two GeoPoints (Euclidean approximation)
     */
    private fun calculateSimpleDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val latDiff = point1.latitude - point2.latitude
        val lonDiff = point1.longitude - point2.longitude
        return kotlin.math.sqrt(latDiff * latDiff + lonDiff * lonDiff)
    }

    private fun updateInfoDisplay(point: RoutePoint) {
        val formattedTime = formatTime(point.timestamp)
        val tv = findViewById<TextView>(R.id.tvInfo)

        val currentProfileId = ProfileStorage.getSelectedProfileId(this)
        val profiles = ProfileStorage.loadProfiles(this)
        val profile = profiles.find { it.id == currentProfileId }

        val infoText = if (profile?.vehicleType == Profile.VehicleType.MOTORCYCLE) {
            """
            ${getString(R.string.speed_label)} ${"%.0f".format(point.speed)} ${getString(R.string.speed_unit)}
            ${getString(R.string.angle_label)} ${"%.0f".format(point.angle)}${getString(R.string.angle_unit)}
            ${getString(R.string.time_label)} $formattedTime
            """.trimIndent()
        } else {
            """
            ${getString(R.string.speed_label)} ${"%.0f".format(point.speed)} ${getString(R.string.speed_unit)}
            ${getString(R.string.time_label)} $formattedTime
            """.trimIndent()
        }

        tv.text = infoText
        tv.textSize = 14f
    }

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = millis / (1000 * 60 * 60)
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onStart() {
        super.onStart()
        if (isMapboxMode) {
            mapboxMapView?.onStart()
        } else {
            if (::map.isInitialized) {
                map.onResume()
            }
        }
    }
    
    override fun onStop() {
        super.onStop()
        if (isMapboxMode) {
            mapboxMapView?.onStop()
        } else {
            if (::map.isInitialized) {
                map.onPause()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (!isMapboxMode && ::map.isInitialized) {
            map.onResume()
        }
    }
    
    override fun onPause() {
        super.onPause()
        if (!isMapboxMode && ::map.isInitialized) {
            map.onPause()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopRouteDrawingTimer()
        // Отменяме hide runnable при унищожаване
        zoomButtonsHideRunnable?.let { zoomButtonsHandler.removeCallbacks(it) }
        if (isMapboxMode) {
            mapboxMapView?.onDestroy()
        } else {
            if (::map.isInitialized) {
                map.onDetach()
            }
        }
    }

    private fun setupScreenKeepOn() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
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

    override fun onBackPressed() {
        val intent = Intent(this, RacesActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}