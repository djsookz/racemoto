package com.example.clinometer.main.map
import com.example.clinometer.*
import com.example.clinometer.main.MainContainerActivity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.example.clinometer.settings.LanguageManager
import com.example.clinometer.settings.UnitsManager
import com.example.clinometer.track.TrackLapMapLogic
import com.example.clinometer.track.TrackLapNavState
import com.example.clinometer.track.TrackMapNavigator
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
import android.widget.FrameLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.ScaleGestureDetector
import androidx.compose.ui.graphics.Paint
import com.example.clinometer.data.ProfileStorage
import com.github.mikephil.charting.components.YAxis
import kotlin.math.abs

class MapActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_INLINE_RACE = "inline_race"
        const val EXTRA_INLINE_ROUTE_POINTS = "inline_route_points"
        const val EXTRA_RETURN_TO_PREVIOUS = "return_to_previous"
    }
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }
    
    private lateinit var routePoints: List<RoutePoint>
    private var mapboxMapView: MapboxMapView? = null
    private var raceId: Long = -1L
    private var race: Race? = null
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
    private var mapboxRouteAnnotations = mutableListOf<com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation>()
    private var trackLapPartialAnnotation: com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation? = null
    private var lastDrawnIndex = -1
    private var mapboxRouteSourceId = "route-source-dynamic"
    private var mapboxRouteLayerId = "route-layer-dynamic"
    private var mapboxStyle: Style? = null
    private var lastMapboxUpdateTime = 0L
    private var pendingMapboxUpdate: Runnable? = null
    private val mapboxUpdateHandler = Handler(Looper.getMainLooper())
    private var zoomButtonsHideRunnable: Runnable? = null
    private val zoomButtonsHandler = Handler(Looper.getMainLooper())
    
    private var markerAnimator: android.animation.ValueAnimator? = null
    private var currentMarkerLat = 0.0
    private var currentMarkerLon = 0.0
    private var isMarkerInitialized = false
    private var currentTrackLapNumber = -1
    private var currentTrackOutingNumber = -1
    private var currentTrackTotalLaps = -1
    private var isPointToPointLapContext = false
    private var isTrackLapContext = false

    private enum class Mode {
        SPEED, ANGLE
    }
    
    private fun loadMapboxStyleFromJson(onStyleLoaded: (Style) -> Unit) {
        val styleUri = "mapbox://styles/djsookz/cmiyer8iu000101s84wqsav5l"
        mapboxMapView?.mapboxMap?.loadStyleUri(styleUri) { style ->
            onStyleLoaded(style)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_map)
        
        setupScreenKeepOn()
        

        val inlineRace = intent.getParcelableExtra<Race>(EXTRA_INLINE_RACE)
        val inlineRoutePoints = intent.getParcelableArrayListExtra<RoutePoint>(EXTRA_INLINE_ROUTE_POINTS)

        val raceToEdit = if (inlineRace != null) {
            raceId = inlineRace.id
            inlineRace
        } else {
            raceId = intent.getLongExtra("RACE_ID", -1)
            if (raceId == -1L) {
                Toast.makeText(this, R.string.error_missing_session_id, Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            val races = RouteStorage.loadRaces(this)
            val loadedRace = races.find { it.id == raceId }
            if (loadedRace == null) {
                Toast.makeText(this, R.string.error_session_not_found, Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            loadedRace
        }
        
        race = raceToEdit
        
        val profiles = ProfileStorage.loadProfiles(this)
        val profile = profiles.find { it.id == raceToEdit.profileId }
        val isMotorcycle = profile?.vehicleType == Profile.VehicleType.MOTORCYCLE

        val trackContext = TrackMapNavigator.parseIntent(intent, isMotorcycle)
        isTrackLapContext = trackContext.isTrackContext
        currentTrackLapNumber = trackContext.lapNumber
        currentTrackOutingNumber = trackContext.outingNumber
        isPointToPointLapContext = trackContext.isPointToPoint

        routePoints = if (inlineRace != null) {
            inlineRoutePoints?.toList()?.takeIf { it.isNotEmpty() } ?: inlineRace.routePoints
        } else {
            RouteStorage.loadRoutePoints(this, raceId)
        }
        
        if (routePoints.isEmpty()) {
        }

        // maxLeftLayout can be LinearLayout (portrait) or CardView (landscape), so use View
        val maxLeftLayout = findViewById<View>(R.id.maxLeftLayout)
        val maxRightLayout = findViewById<View>(R.id.maxRightLayout)

        if (isMotorcycle) {
            val maxLeftAngle = routePoints.filter { it.angle < 0 }.minByOrNull { it.angle }?.angle?.let { kotlin.math.abs(it) } ?: 0f
            val maxRightAngle = routePoints.filter { it.angle > 0 }.maxByOrNull { it.angle }?.angle ?: 0f

            findViewById<TextView>(R.id.tvMaxLeftInfo).text = "%.0f°".format(maxLeftAngle)
            findViewById<TextView>(R.id.tvMaxRightInfo).text = "%.0f°".format(maxRightAngle)
            findViewById<TextView>(R.id.tvDistanceMoto).apply {
                visibility = View.VISIBLE
                val convertedDist = UnitsManager.formatDistance(raceToEdit.distance, this@MapActivity, 2)

                text = convertedDist
            }
            findViewById<TextView>(R.id.tvDistanceCar).visibility = View.GONE
            maxLeftLayout?.visibility = View.VISIBLE
            maxRightLayout?.visibility = View.VISIBLE
        } else {
            maxLeftLayout?.visibility = View.GONE
            maxRightLayout?.visibility = View.GONE
            findViewById<TextView>(R.id.tvDistanceCar).apply {
                visibility = View.VISIBLE
                val convertedDist = UnitsManager.formatDistance(raceToEdit.distance, this@MapActivity, 2)
                text = convertedDist
            }
            findViewById<TextView>(R.id.tvDistanceMoto).visibility = View.GONE
        }

        val convertedMaxSpeed = UnitsManager.formatSpeed(raceToEdit.maxSpeed, this, 0)
        findViewById<TextView>(R.id.tvMaxSpeedInfo).text = "$convertedMaxSpeed"

        val btnNewRoute = findViewById<Button>(R.id.btnStart)
        btnNewRoute.setText(if (trackContext.isTrackContext) R.string.session_new_button else R.string.new_session_button)
        btnNewRoute.setOnClickListener {
            TrackMapNavigator.openSessionOrNewRoute(this, trackContext) {
                startActivity(Intent(this, MainContainerActivity::class.java).apply {
                    putExtra(MainContainerActivity.EXTRA_NAV_ITEM_ID, R.id.navMap)
                })
            }
        }

        val avgSpeed = if (raceToEdit.duration > 0) {
            (raceToEdit.distance * 3600000) / raceToEdit.duration
        } else {
            0.0
        }
        val convertedAvgSpeed = UnitsManager.formatSpeed(avgSpeed.toFloat(), this, 0)
        findViewById<TextView>(R.id.tvAvgSpeedInfo).text = convertedAvgSpeed

        if (routePoints.isEmpty()) {
            Toast.makeText(this, R.string.error_no_route_data, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val sessionTimestamp = routePoints.firstOrNull()?.absoluteTime ?: System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(sessionTimestamp))

        val sessionDateTime = findViewById<TextView>(R.id.sessionDateTime)
        sessionDateTime.text = getString(R.string.created_date_format, formattedDate)

        val tvSessionTitle = findViewById<TextView>(R.id.tvSessionTitle)
        if (!raceToEdit.name.isNullOrEmpty()) {
            tvSessionTitle.text = raceToEdit.name
        } else {
            tvSessionTitle.text = formattedDate
        }

        val navState = TrackMapNavigator.setupTrackLapNavigation(
            activity = this,
            state = TrackLapNavState(
                lapNumber = currentTrackLapNumber,
                outingNumber = currentTrackOutingNumber,
                totalLaps = currentTrackTotalLaps,
                isPointToPoint = isPointToPointLapContext
            ),
            context = trackContext,
            raceId = raceId,
            race = race
        )
        currentTrackLapNumber = navState.lapNumber
        currentTrackOutingNumber = navState.outingNumber
        currentTrackTotalLaps = navState.totalLaps
        isPointToPointLapContext = navState.isPointToPoint

        findViewById<View>(R.id.btnBack).setOnClickListener { onBackPressed() }

        chart = findViewById(R.id.chart)
        tabLayout = findViewById(R.id.tabs)
        TrackMapNavigator.applyTrackLapLayoutAdjustments(this, chart, trackContext.isTrackContext)
        
        setupMapboxMap()
        setupZoomButtons()
        showFullRoute()

        findViewById<TextView>(R.id.tvTotalTime).text = formatTime(raceToEdit.duration)

        // descriptionContainer and photosContainer may not exist in landscape layout
        val descriptionContainer = findViewById<androidx.cardview.widget.CardView>(R.id.descriptionContainer)
        val tvDescription = findViewById<TextView>(R.id.tvDescription)

        descriptionContainer?.let {
            if (!raceToEdit.description.isNullOrEmpty()) {
                it.visibility = View.VISIBLE
                tvDescription?.text = raceToEdit.description
            } else {
                it.visibility = View.GONE
            }
        }

        val photosContainer = findViewById<androidx.cardview.widget.CardView>(R.id.photosContainer)
        val rvPhotos = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvPhotos)

        photosContainer?.let {
            if (raceToEdit.photoPaths != null && raceToEdit.photoPaths.isNotEmpty()) {
                it.visibility = View.VISIBLE
                rvPhotos?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
                val adapter = PhotosAdapter(raceToEdit.photoPaths)
                rvPhotos?.adapter = adapter
            } else {
                it.visibility = View.GONE
            }
        }

        try {
            setupChart(isMotorcycle)
            
            setupTabs(isMotorcycle)
            
            updateChartData(currentMode, isMotorcycle)
        } catch (e: Exception) {
            Toast.makeText(this, "Chart error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupMapboxMap() {
        val mapContainer = findViewById<FrameLayout>(R.id.mapContainer)
        val osmdroidMapView = mapContainer.findViewById<View>(R.id.mapRoute)
        
        if (mapContainer == null) {
            return
        }
        
        if (osmdroidMapView != null) {
            mapContainer.removeView(osmdroidMapView)
        }
        
        mapboxMapView = MapboxMapView(this)
        mapContainer.addView(mapboxMapView)
        mapboxPolylineAnnotationManager = mapboxMapView?.annotations?.createPolylineAnnotationManager()
        
        val bounds = calculateBounds(routePoints.map { it.geoPoint })
        
        mapboxMapView?.mapboxMap?.setCamera(
            CameraOptions.Builder()
                .center(MapboxPoint.fromLngLat(
                    (bounds.minLon + bounds.maxLon) / 2.0,
                    (bounds.minLat + bounds.maxLat) / 2.0
                ))
                .zoom(calculateZoomLevel(bounds))
                .build()
        )
        
        mapboxMapView?.scalebar?.enabled = false
        mapboxMapView?.compass?.enabled = false
        mapboxMapView?.attribution?.enabled = false
        
        setupMapTouchListener()
        
        loadMapboxStyleFromJson { style ->
            setupMapboxRoute(style)
        }
    }
    
    private fun setupMapboxRoute(style: Style) {
        mapboxStyle = style
        
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
        
        
        if (!routeSourceExists) {
            val emptyFeatureCollection = FeatureCollection.fromFeatures(emptyList())
            style.addSource(
                geoJsonSource(mapboxRouteSourceId) {
                    featureCollection(emptyFeatureCollection)
                }
            )
        }
        
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
            val markerFeature = Feature.fromGeometry(com.mapbox.geojson.Point.fromLngLat(markerPoint.longitude(), markerPoint.latitude()))
            val source = style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>("marker-source")
            source?.featureCollection(FeatureCollection.fromFeatures(listOf(markerFeature)))
        }
        
        try {
            style.getStyleImage("blue-dot")
        } catch (e: Exception) {
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
        }
        
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
            style.addImage("blue-dot", blueDotBitmap)
        } catch (e: Exception) {
        }
        
        if (!routeLayerExists) {
            val labelLayerNames = listOf(
                "transit-labels", "waterway-labels", "poi-labels", 
                "road-labels", "place-labels",
                "place-city-lg-n", "place-city-md-n", "place-city-sm", 
                "place-town", "place-village"
            )
            
            var lastFoundLabelLayer: String? = null
            
            for (labelLayerName in labelLayerNames.reversed()) {
                try {
                    if (style.styleLayerExists(labelLayerName)) {
                        lastFoundLabelLayer = labelLayerName
                        break
                    }
                } catch (e: Exception) {
                }
            }
            
            if (lastFoundLabelLayer != null) {
                style.addLayerAbove(
                    lineLayer(mapboxRouteLayerId, mapboxRouteSourceId) {
                        lineColor("#FF7805")
                        lineWidth(6.0)
                    },
                    lastFoundLabelLayer
                )
            } else {
                style.addLayer(
                    lineLayer(mapboxRouteLayerId, mapboxRouteSourceId) {
                        lineColor("#FF7805")
                        lineWidth(6.0)
                    }
                )
            }
            
            mapboxMapView?.postDelayed({
                try {
                    if (!style.styleLayerExists(mapboxRouteLayerId)) return@postDelayed
                    
                    var foundLabel: String? = null
                    for (labelName in labelLayerNames.reversed()) {
                        try {
                            if (style.styleLayerExists(labelName)) {
                                foundLabel = labelName
                                break
                            }
                        } catch (e: Exception) {
                        }
                    }
                    
                    if (foundLabel != null) {
                        try {
                            style.removeStyleLayer(mapboxRouteLayerId)
                            style.addLayerAbove(
                                lineLayer(mapboxRouteLayerId, mapboxRouteSourceId) {
                                    lineColor("#FF7805")
                                    lineWidth(6.0)
                                },
                                foundLabel
                            )
                            
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
                        }
                    }
                } catch (e: Exception) {
                }
            }, 2000)
        }
        
        if (!markerLayerExists) {
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
        
        showFullRoute()
        
        mapboxMapView?.post {
            setupMapZoom()
        }
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

    private fun formatTimeForReader(timeValue: Float): String {
        val totalSeconds = timeValue.toLong().coerceAtLeast(0)
        val min = totalSeconds / 60
        val sec = totalSeconds % 60
        return String.format("%02d:%02d", min, sec)
    }
    private fun setupChart(isMotorcycle: Boolean) {
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false

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

        if (routePoints.isNotEmpty()) {
            val startTime = routePoints.first().timestamp / 1000f
            val firstTime = 0f
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
            }
        }

        val centerLine = com.github.mikephil.charting.components.LimitLine(0f).apply {
            lineColor = android.graphics.Color.RED
            lineWidth = 2f
            enableDashedLine(10f, 10f, 0f)
        }

        chart.setExtraOffsets(0f, 0f, 0f, 0f)

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

        var isZooming = false
        var zoomCenterX = 0f

        val startTimeRef = if (routePoints.isNotEmpty()) routePoints.first().timestamp / 1000f else 0f
        val dataStartTime = 0f
        val dataEndTime = if (routePoints.isNotEmpty()) (routePoints.last().timestamp / 1000f) - startTimeRef else 0f

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isZooming = true
                zoomCenterX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val deltaX = abs(detector.currentSpanX - detector.previousSpanX)
                val deltaY = abs(detector.currentSpanY - detector.previousSpanY)

                val scaleFactorX = if (deltaX > deltaY * 1.5) detector.scaleFactor else 1f
                val scaleFactorY = if (deltaY > detector.currentSpanX * 1.5) detector.scaleFactor else 1f

                if (deltaX <= deltaY * 1.5 && deltaY <= detector.currentSpanX * 1.5) {
                    chart.zoom(detector.scaleFactor, detector.scaleFactor,
                        chart.width / 2f, chart.height / 2f,
                        com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT)
                } else {
                    chart.zoom(scaleFactorX, scaleFactorY,
                        chart.width / 2f, chart.height / 2f,
                        com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT)
                }

                var targetX = zoomCenterX - chart.visibleXRange / 2f
                val visibleRange = chart.visibleXRange

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
                val centerX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                updateReaderPosition(centerX)
            }
        })

        chart.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)

            if (event.action == MotionEvent.ACTION_DOWN) {
                hasUserInteracted = true
            }

            if (!isZooming) {
                val beforeCenter = (chart.lowestVisibleX + chart.highestVisibleX) / 2f

                chart.onTouchEvent(event)

                val currentCenter = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                val visibleRange = chart.visibleXRange

                if (currentCenter < dataStartTime) {
                    chart.moveViewToX(dataStartTime - visibleRange / 2f)
                    chart.isDragEnabled = false
                    chart.postDelayed({ chart.isDragEnabled = true }, 1)
                } else if (currentCenter > dataEndTime) {
                    chart.moveViewToX(dataEndTime - visibleRange / 2f)
                    chart.isDragEnabled = false
                    chart.postDelayed({ chart.isDragEnabled = true }, 1)
                }
            }

            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                val centerX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                updateReaderPosition(centerX)
                chart.invalidate()
            }

            true
        }

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
            }
            override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {}

            override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
                hasUserInteracted = true
                
                if (!isZooming) {
                    val currentCenterX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                    val visibleRange = chart.visibleXRange

                    if (currentCenterX < dataStartTime) {
                        chart.moveViewToX(dataStartTime - visibleRange / 2f)
                        chart.isDragEnabled = false
                        chart.postDelayed({ chart.isDragEnabled = true }, 1)
                    } else if (currentCenterX > dataEndTime) {
                        chart.moveViewToX(dataEndTime - visibleRange / 2f)
                        chart.isDragEnabled = false
                        chart.postDelayed({ chart.isDragEnabled = true }, 1)
                    } else {
                        updateReaderPosition(currentCenterX)
                        chart.invalidate()
                    }
                }
            }
        })

        chart.xAxis.textColor = android.graphics.Color.WHITE
        chart.axisLeft.textColor = android.graphics.Color.WHITE
        chart.legend.textColor = android.graphics.Color.WHITE

        chart.invalidate()
    }

    private fun setupMapZoom() {
        if (routePoints.isEmpty()) return
        
        val allGeoPoints = routePoints.map { it.geoPoint }
        
        val profiles = ProfileStorage.loadProfiles(this)
        val race = RouteStorage.loadRaces(this).find { it.id == intent.getLongExtra("RACE_ID", -1) }
        val profile = race?.let { profiles.find { p -> p.id == it.profileId } }
        val isMotorcycle = profile?.vehicleType == Profile.VehicleType.MOTORCYCLE
        
        if (allGeoPoints.size >= 2) {
                val boundingBox = BoundingBox.fromGeoPointsSafe(allGeoPoints)
                
                mapboxMapView?.post {
                    val mapWidth = mapboxMapView?.width ?: 0
                    val mapHeight = mapboxMapView?.height ?: 0
                    
                    if (mapWidth > 0 && mapHeight > 0) {
                        val density = resources.displayMetrics.density
                        val paddingPx = 20.0 * density
                        
                        val paddingWidthRatio = (paddingPx * 2) / mapWidth
                        val paddingHeightRatio = (paddingPx * 2) / mapHeight
                        
                        val latDiff = boundingBox.latNorth - boundingBox.latSouth
                        val lonDiff = boundingBox.lonEast - boundingBox.lonWest
                        
                        val latPadding = latDiff * paddingHeightRatio / (1.0 - paddingHeightRatio)
                        val lonPadding = lonDiff * paddingWidthRatio / (1.0 - paddingWidthRatio)
                        
                        val padding = maxOf(latPadding, lonPadding)
                        
                        val adjustedBox = BoundingBox(
                            latNorth = boundingBox.latNorth + padding,
                            lonEast = boundingBox.lonEast + padding,
                            latSouth = boundingBox.latSouth - padding,
                            lonWest = boundingBox.lonWest - padding
                        )
                        
                        val centerLat = (adjustedBox.latSouth + adjustedBox.latNorth) / 2.0
                        val centerLon = (adjustedBox.lonWest + adjustedBox.lonEast) / 2.0
                        
                        val adjustedLatDiff = adjustedBox.latNorth - adjustedBox.latSouth
                        val adjustedLonDiff = adjustedBox.lonEast - adjustedBox.lonWest
                        
                        val aspectRatio = mapWidth.toDouble() / mapHeight.toDouble()
                        val routeAspectRatio = adjustedLonDiff / adjustedLatDiff
                        
                        val zoom = if (routeAspectRatio > aspectRatio) {
                            kotlin.math.log2(360.0 / adjustedLonDiff) - kotlin.math.log2(aspectRatio) + 0.5 - 0.5
                        } else {
                            kotlin.math.log2(360.0 / adjustedLatDiff) - 1.5 - 0.8
                        }.coerceIn(3.0, 19.0)
                        
                        mapboxMapView?.mapboxMap?.setCamera(
                            CameraOptions.Builder()
                                .center(MapboxPoint.fromLngLat(centerLon, centerLat))
                                .zoom(zoom)
                                .build()
                        )
                    } else {
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
                val point = allGeoPoints[0]
                mapboxMapView?.mapboxMap?.setCamera(
                    CameraOptions.Builder()
                        .center(MapboxPoint.fromLngLat(point.longitude, point.latitude))
                        .zoom(15.0)
                        .build()
                )
            }
    }
    
    private fun setupMapTouchListener() {
        mapboxMapView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    showZoomButtons()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
    }

    private fun showZoomButtons() {
        val zoomButtonsContainer = findViewById<View>(R.id.zoomButtonsContainer)

        zoomButtonsHideRunnable?.let { zoomButtonsHandler.removeCallbacks(it) }

        zoomButtonsContainer?.let { container ->
            container.visibility = View.VISIBLE
            container.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        }

        zoomButtonsHideRunnable = Runnable {
            zoomButtonsContainer?.let { container ->
                container.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        container.visibility = View.GONE
                    }
                    .start()
            }
        }
        zoomButtonsHandler.postDelayed(zoomButtonsHideRunnable!!, 5000)
    }
    
    private fun setupZoomButtons() {
        val btnZoomIn = findViewById<android.widget.Button>(R.id.btnZoomIn)
        val btnZoomOut = findViewById<android.widget.Button>(R.id.btnZoomOut)
        
        btnZoomIn?.setOnClickListener {
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
        }
        
        btnZoomOut?.setOnClickListener {
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
        }
    }

    private fun createBlueDotMarker(): android.graphics.drawable.Drawable {
        val size = 48
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        val outerPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            isAntiAlias = true
            setShadowLayer(6f, 0f, 3f, android.graphics.Color.argb(150, 0, 0, 0))
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3, outerPaint)
        
        val innerPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#1976D2")
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 8, innerPaint)
        
        val shadowPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(80, 0, 0, 0)
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f + 2, size / 2f + 2, (size / 2f) - 8, shadowPaint)
        
        return android.graphics.drawable.BitmapDrawable(resources, bitmap)
    }


    private fun showFullRoute() {
        val style = mapboxStyle ?: return

        if (isTrackLapContext && routePoints.size > 1) {
            drawTrackLapSegmentsWithInputColors()
            try {
                val source = style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>(mapboxRouteSourceId)
                source?.featureCollection(FeatureCollection.fromFeatures(emptyList()))
            } catch (_: Exception) {
            }
            return
        }

        mapboxPolylineAnnotationManager?.deleteAll()

        try {
            val coordinates = routePoints.map { point ->
                MapboxPoint.fromLngLat(point.geoPoint.longitude, point.geoPoint.latitude)
            }

            val lineString = LineString.fromLngLats(coordinates)
            val feature = Feature.fromGeometry(lineString)

            val source = style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>(mapboxRouteSourceId)
            source?.featureCollection(FeatureCollection.fromFeatures(listOf(feature)))
        } catch (e: Exception) {
        }
    }

    private fun drawTrackLapSegmentsWithInputColors() {
        val polyManager = mapboxPolylineAnnotationManager ?: return
        ensureTrackLapSegmentAnnotations()

        if (mapboxRouteAnnotations.isNotEmpty()) {
            mapboxRouteAnnotations.forEach { it.lineWidth = 6.5 }
            polyManager.update(mapboxRouteAnnotations)
        }

        trackLapPartialAnnotation?.let {
            it.lineWidth = 0.0
            polyManager.update(it)
        }

        currentDrawingIndex = mapboxRouteAnnotations.size
    }

    private fun ensureTrackLapSegmentAnnotations() {
        if (!isTrackLapContext) return

        val polyManager = mapboxPolylineAnnotationManager ?: return
        if (routePoints.size < 2) {
            mapboxRouteAnnotations.clear()
            trackLapPartialAnnotation = null
            currentDrawingIndex = 0
            return
        }

        val expectedCount = routePoints.size - 1
        val ready = mapboxRouteAnnotations.size == expectedCount && trackLapPartialAnnotation != null
        if (ready) return

        polyManager.deleteAll()
        mapboxRouteAnnotations.clear()
        trackLapPartialAnnotation = null

        val segmentOptions = TrackLapMapLogic.buildFullSegmentOptions(routePoints)
        if (segmentOptions.isNotEmpty()) {
            val created = polyManager.create(segmentOptions)
            mapboxRouteAnnotations = created.toMutableList()
            mapboxRouteAnnotations.forEach { it.lineWidth = 0.0 }
            polyManager.update(mapboxRouteAnnotations)
        }

        val firstPoint = routePoints.first().geoPoint
        val partial = polyManager.create(
            PolylineAnnotationOptions()
                .withPoints(
                    listOf(
                        MapboxPoint.fromLngLat(firstPoint.longitude, firstPoint.latitude),
                        MapboxPoint.fromLngLat(firstPoint.longitude, firstPoint.latitude)
                    )
                )
                .withLineColor("#00C850")
                .withLineWidth(0.0)
        )
        trackLapPartialAnnotation = partial
        currentDrawingIndex = 0
    }

    private fun drawTrackLapSegmentsUpToIndex(index: Int, interpolatedPoint: GeoPoint) {
        ensureTrackLapSegmentAnnotations()

        val polyManager = mapboxPolylineAnnotationManager ?: return
        val segmentCount = mapboxRouteAnnotations.size
        if (segmentCount == 0) return

        val targetFullCount = index.coerceIn(0, segmentCount)
        if (targetFullCount != currentDrawingIndex) {
            val changed = mutableListOf<com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation>()

            if (targetFullCount > currentDrawingIndex) {
                for (i in currentDrawingIndex until targetFullCount) {
                    mapboxRouteAnnotations.getOrNull(i)?.let {
                        it.lineWidth = 6.5
                        changed.add(it)
                    }
                }
            } else {
                for (i in targetFullCount until currentDrawingIndex) {
                    mapboxRouteAnnotations.getOrNull(i)?.let {
                        it.lineWidth = 0.0
                        changed.add(it)
                    }
                }
            }

            if (changed.isNotEmpty()) {
                polyManager.update(changed)
            }
            currentDrawingIndex = targetFullCount
        }

        trackLapPartialAnnotation?.let { partial ->
            if (targetFullCount >= segmentCount) {
                if ((partial.lineWidth ?: 0.0) != 0.0) {
                    partial.lineWidth = 0.0
                    polyManager.update(partial)
                }
            } else {
                val start = routePoints[targetFullCount].geoPoint
                val segmentColor = TrackLapMapLogic.getSegmentColorHex(routePoints, targetFullCount)

                partial.points = listOf(
                    MapboxPoint.fromLngLat(start.longitude, start.latitude),
                    MapboxPoint.fromLngLat(interpolatedPoint.longitude, interpolatedPoint.latitude)
                )
                if (!segmentColor.isNullOrBlank()) {
                    partial.lineColorInt = Color.parseColor(segmentColor)
                }
                partial.lineWidth = 6.5
                polyManager.update(partial)
            }
        }
    }


    private fun startRouteDrawingTimer() {
        routeDrawingRunnable?.let { routeDrawingTimer?.removeCallbacks(it) }
        
        routeDrawingRunnable = Runnable {
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
        
        val p1 = routePoints[beforeIndex]
        val p2 = routePoints[afterIndex]
        
        val t1 = (p1.timestamp / 1000f) - startTimeRef
        val t2 = (p2.timestamp / 1000f) - startTimeRef
        
        val factor = if (t2 > t1) {
            ((targetTimeSeconds - t1) / (t2 - t1)).coerceIn(0f, 1f)
        } else {
            0f
        }
        
        val interpolatedSpeed = p1.speed + (p2.speed - p1.speed) * factor
        val interpolatedAngle = p1.angle + (p2.angle - p1.angle) * factor
        
        return Pair(interpolatedSpeed, interpolatedAngle)
    }
    
    private fun updateReaderPosition(timeInSeconds: Float) {
        val (index, interpolatedPoint) = findInterpolatedPosition(timeInSeconds)
        if (index in routePoints.indices) {
            val startTime = if (routePoints.isNotEmpty()) routePoints.first().timestamp / 1000f else 0f
            val (interpolatedSpeed, interpolatedAngle) = interpolateSpeedAndAngle(timeInSeconds, startTime)

            if (isTrackLapContext) {
                if (hasUserInteracted) {
                    drawMapboxRouteUpToIndex(index, interpolatedPoint)
                    isDrawingRoute = true
                    startRouteDrawingTimer()
                } else {
                    updateMapboxMarkerPosition(interpolatedPoint, moveCamera = false)
                }
            } else if (hasUserInteracted) {
                drawMapboxRouteUpToIndex(index, interpolatedPoint)
                isDrawingRoute = true
                startRouteDrawingTimer()
            } else {
                updateMapboxMarkerPosition(interpolatedPoint)
            }

            val currentProfileId = ProfileStorage.getSelectedProfileId(this)
            val profiles = ProfileStorage.loadProfiles(this)
            val profile = profiles.find { it.id == currentProfileId }

            findViewById<TextView>(R.id.tvReaderSpeed).text =
                "${"%.0f".format(interpolatedSpeed)} ${getString(R.string.speed_unit)}"

            val angleContainer = findViewById<LinearLayout>(R.id.readerAngleContainer)

            if (profile?.vehicleType == Profile.VehicleType.MOTORCYCLE) {
                angleContainer?.visibility = View.VISIBLE
                findViewById<TextView>(R.id.tvReaderAngle)?.text = "${"%.0f".format(interpolatedAngle)}°"
            } else {
                angleContainer?.visibility = View.GONE
            }
        }
    }
    
    private fun updateMapboxMarkerPosition(geoPoint: GeoPoint, moveCamera: Boolean = true) {
        setMarkerPositionDirect(geoPoint.latitude, geoPoint.longitude, moveCamera)
    }
    
    private fun setMarkerPositionDirect(lat: Double, lon: Double, moveCamera: Boolean = true) {
        val point = MapboxPoint.fromLngLat(lon, lat)
        val style = mapboxStyle ?: return

        if (moveCamera) {
            val currentCamera = mapboxMapView?.mapboxMap?.cameraState
            mapboxMapView?.mapboxMap?.setCamera(
                CameraOptions.Builder()
                    .center(point)
                    .zoom(currentCamera?.zoom ?: 17.0)
                    .pitch(currentCamera?.pitch ?: 45.0)
                    .bearing(currentCamera?.bearing ?: 0.0)
                    .build()
            )
        }
        
        try {
            val markerFeature = Feature.fromGeometry(com.mapbox.geojson.Point.fromLngLat(lon, lat))
            val source = style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>("marker-source")
            source?.featureCollection(FeatureCollection.fromFeatures(listOf(markerFeature)))
        } catch (e: Exception) {
        }
    }
    
    private fun drawMapboxRouteUpToIndex(index: Int, interpolatedPoint: GeoPoint) {
        if (isTrackLapContext) {
            updateMapboxMarkerPosition(interpolatedPoint, moveCamera = false)
            drawTrackLapSegmentsUpToIndex(index, interpolatedPoint)
            lastDrawnIndex = index
            return
        }

        val style = mapboxStyle ?: return
        
        try {
            updateMapboxMarkerPosition(interpolatedPoint)
            
            val coordinates = mutableListOf<MapboxPoint>()
            
            for (i in 0..minOf(index, routePoints.size - 1)) {
                val point = routePoints[i]
                coordinates.add(MapboxPoint.fromLngLat(point.geoPoint.longitude, point.geoPoint.latitude))
            }
            
            coordinates.add(MapboxPoint.fromLngLat(interpolatedPoint.longitude, interpolatedPoint.latitude))
            
            val lineString = LineString.fromLngLats(coordinates)
            val feature = Feature.fromGeometry(lineString)
            
            val source = style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>(mapboxRouteSourceId)
            source?.featureCollection(FeatureCollection.fromFeatures(listOf(feature)))
            
            lastDrawnIndex = index
        } catch (e: Exception) {
        }
    }

    private fun setupTabs(isMotorcycle: Boolean) {
        if (!isMotorcycle) {
            tabLayout.visibility = View.GONE
            return
        }
        
        tabLayout.visibility = View.VISIBLE
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_speed))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_angle))

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
        
        val startTime = if (routePoints.isNotEmpty()) routePoints.first().timestamp / 1000f else 0f
        val speedEntries = routePoints.map { Entry((it.timestamp / 1000f) - startTime, it.speed) }
        val angleEntries = if (isMotorcycle) {
            routePoints.map { Entry((it.timestamp / 1000f) - startTime, it.angle) }
        } else {
            emptyList()
        }
        

        val activeColor = if (mode == Mode.SPEED) Color.rgb(252, 120, 5) else Color.rgb(5, 252, 227)
        val fadedColor = if (mode == Mode.SPEED) Color.argb(105,5, 252, 227) else Color.argb(105,252, 120, 5)

        val speedDataSet = LineDataSet(speedEntries, getString(R.string.chart_speed_legend)).apply {
            color = if (mode == Mode.SPEED) activeColor else fadedColor
            lineWidth = if (mode == Mode.SPEED) 2f else 1f
            setDrawValues(false)
            setDrawCircles(false)
            setMode(LineDataSet.Mode.LINEAR)
            if (mode != Mode.SPEED) enableDashedLine(10f, 5f, 0f)
        }

        val lineData = if (isMotorcycle) {
            val angleDataSet = LineDataSet(angleEntries, getString(R.string.chart_angle_legend)).apply {
                color = if (mode == Mode.ANGLE) activeColor else fadedColor
                lineWidth = if (mode == Mode.ANGLE) 2f else 1f
                setDrawValues(false)
                setDrawCircles(false)
                setMode(LineDataSet.Mode.LINEAR)
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

    
    private fun findInterpolatedPosition(targetTimeSeconds: Float): Pair<Int, GeoPoint> {
        if (routePoints.size < 2) {
            val point = routePoints.firstOrNull()?.geoPoint
            return Pair(0, if (point != null) {
                GeoPoint(point.latitude, point.longitude)
            } else {
                GeoPoint(0.0, 0.0)
            })
        }
        
        val startTime = routePoints.first().timestamp / 1000f
        
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
        
        val firstRelativeTime = 0f
        val lastRelativeTime = (routePoints.last().timestamp / 1000f) - startTime
        if (targetTimeSeconds <= firstRelativeTime) {
            val point = routePoints.first().geoPoint
            return Pair(0, GeoPoint(point.latitude, point.longitude))
        }
        if (targetTimeSeconds >= lastRelativeTime) {
            val point = routePoints.last().geoPoint
            return Pair(routePoints.size - 1, GeoPoint(point.latitude, point.longitude))
        }
        
        val p0Index = (beforeIndex - 1).coerceAtLeast(0)
        val p1Index = beforeIndex
        val p2Index = afterIndex
        val p3Index = (afterIndex + 1).coerceAtMost(routePoints.size - 1)
        
        val p0 = routePoints[p0Index].geoPoint
        val p1 = routePoints[p1Index].geoPoint
        val p2 = routePoints[p2Index].geoPoint
        val p3 = routePoints[p3Index].geoPoint
        
        val t1 = (routePoints[p1Index].timestamp / 1000f) - startTime
        val t2 = (routePoints[p2Index].timestamp / 1000f) - startTime
        val t = if (t2 > t1) {
            ((targetTimeSeconds - t1) / (t2 - t1)).coerceIn(0f, 1f)
        } else {
            0f
        }
        
        val interpolatedLat = catmullRomInterpolate(
            p0.latitude, p1.latitude, p2.latitude, p3.latitude, t
        )
        val interpolatedLon = catmullRomInterpolate(
            p0.longitude, p1.longitude, p2.longitude, p3.longitude, t
        )
        
        val interpolatedPoint = GeoPoint(interpolatedLat, interpolatedLon)
        
        return Pair(beforeIndex, interpolatedPoint)
    }
    
    private fun catmullRomInterpolate(p0: Double, p1: Double, p2: Double, p3: Double, t: Float): Double {
        val t2 = t * t
        val t3 = t2 * t
        
        return 0.5 * (
            2.0 * p1 +
            (-p0 + p2) * t +
            (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 +
            (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3
        )
    }
    

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = millis / (1000 * 60 * 60)
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

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
    }
    
    override fun onPause() {
        super.onPause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopRouteDrawingTimer()
        zoomButtonsHideRunnable?.let { zoomButtonsHandler.removeCallbacks(it) }
        mapboxMapView?.onDestroy()
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
        if (intent.getBooleanExtra(EXTRA_RETURN_TO_PREVIOUS, false)) {
            super.onBackPressed()
            return
        }

        val intent = Intent(this, MainContainerActivity::class.java).apply {
            putExtra(MainContainerActivity.EXTRA_INITIAL_PAGE, MainContainerActivity.PAGE_RACES)
        }
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private class PhotosAdapter(
        private val photoPaths: List<String>
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<PhotosAdapter.PhotoViewHolder>() {
        class PhotoViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            val imageView: android.widget.ImageView = itemView.findViewById(R.id.photoImageView)
            val removeButton: android.widget.ImageView = itemView.findViewById(R.id.removePhotoButton)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
            return PhotoViewHolder(view)
        }
        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            val photoPath = photoPaths[position]
            val bitmap = android.graphics.BitmapFactory.decodeFile(photoPath)
            holder.imageView.setImageBitmap(bitmap)
            holder.removeButton.visibility = View.GONE
            
            holder.imageView.setOnClickListener {
                val intent = Intent(holder.itemView.context, FullScreenImageActivity::class.java).apply {
                    putStringArrayListExtra("photo_paths", ArrayList(photoPaths))
                    putExtra("current_index", position)
                }
                holder.itemView.context.startActivity(intent)
            }
        }
        override fun getItemCount() = photoPaths.size
    }
}