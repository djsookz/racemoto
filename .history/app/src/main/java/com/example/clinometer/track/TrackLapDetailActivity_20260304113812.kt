package com.example.clinometer

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.settings.LanguageManager
import com.example.clinometer.settings.UnitsManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.google.android.material.tabs.TabLayout
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.abs

class TrackLapDetailActivity : AppCompatActivity() {

    private data class ComparableOuting(
        val sessionId: String,
        val outingNumber: Int,
        val bestLapNumber: Int,
        val label: String
    )
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }
    
    private var routePoints: List<RoutePoint> = emptyList()
    private lateinit var map: MapView
    private lateinit var marker: Marker
    private lateinit var chart: LineChart
    private lateinit var tabLayout: TabLayout
    private var currentMode: Mode = Mode.SPEED
    private val originalRouteOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()
    private var routeDrawingTimer: android.os.Handler? = null
    private var routeDrawingRunnable: Runnable? = null
    private var currentDrawingIndex = -1
    private var currentReaderIndex = -1
    private var routeOverlaysAttached = false
    
    // UI elements
    private lateinit var tvLapTitle: TextView
    private lateinit var tvReaderSpeed: TextView
    private lateinit var tvReaderAngle: TextView
    private lateinit var btnPreviousLap: TextView
    private lateinit var btnNextLap: TextView
    private lateinit var btnCompareLap: ImageButton
    
    // Lap navigation
    private var currentLapNumber = 1
    private var totalLaps = 1
    
    // Real data storage
    private var realSpeedEntries = listOf<Entry>()
    private var realGForceEntries = listOf<Entry>()
    private var realAngleEntries = listOf<Entry>()
    private var hasRealData = false
    
    // Chart interaction
    private var hasUserInteracted = false
    private var isZooming = false
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private enum class Mode {
        SPEED, G_FORCE, ANGLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        setContentView(R.layout.activity_track_lap_detail)
        applySystemBarsPaddingToRoot()
        
        setupScreenKeepOn()

        // Get lap data from intent
        currentLapNumber = intent.getIntExtra("lap_number", 1)
        val lapTime = intent.getStringExtra("lap_time") ?: "1:23.456"
        val maxSpeed = intent.getStringExtra("max_speed") ?: "285 km/h"
        val maxGForce = intent.getStringExtra("max_g_force") ?: "2.8g"
        val maxCornering = intent.getStringExtra("max_cornering") ?: "45.2°"
        val isMotorcycle = intent.getBooleanExtra("is_motorcycle", true)

        initializeViews()
        setupClickListeners()
        updateNavigationButtons()
        setupMap()
        setupTabs(isMotorcycle)
        loadLapData(currentLapNumber, lapTime, maxSpeed, maxGForce, maxCornering, isMotorcycle)
        setupChart(isMotorcycle)
        // Update navigation buttons after loading data
        updateNavigationButtons()
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

    private fun initializeViews() {
        map = findViewById(R.id.mapRoute)
        chart = findViewById(R.id.chart)
        tabLayout = findViewById(R.id.tabs)
        tvLapTitle = findViewById(R.id.tvLapTitle)
        tvReaderSpeed = findViewById(R.id.tvReaderSpeed)
        tvReaderAngle = findViewById(R.id.tvReaderAngle)
        btnPreviousLap = findViewById(R.id.btnPreviousLap)
        btnNextLap = findViewById(R.id.btnNextLap)
        btnCompareLap = findViewById(R.id.btnCompareLap)
    }

    private fun setupClickListeners() {
        findViewById<TextView>(R.id.btnBack).setOnClickListener { onBackPressed() }
        btnCompareLap.setOnClickListener { showComparePicker() }
        
        btnPreviousLap.setOnClickListener {
            if (currentLapNumber > 1) {
                currentLapNumber--
                loadLapData(currentLapNumber, "", "", "", "", true)
                updateNavigationButtons()
            }
        }
        
        btnNextLap.setOnClickListener {
            if (currentLapNumber < totalLaps) {
                currentLapNumber++
                loadLapData(currentLapNumber, "", "", "", "", true)
                updateNavigationButtons()
            }
        }
    }

    private fun showComparePicker() {
        val candidates = loadComparableOutings()
        if (candidates.isEmpty()) {
            Toast.makeText(this, "Няма друга сесия на тази писта за сравнение", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = candidates.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Сравни с")
            .setItems(labels) { _, which ->
                val target = candidates[which]
                openLapCompare(target)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun loadComparableOutings(): List<ComparableOuting> {
        val sharedPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val currentSessionId = resolveCurrentSessionIdForCompare(sharedPrefs)
        if (currentSessionId.isBlank()) return emptyList()

        val currentTrackOnlyId = extractTrackIdFromSessionId(currentSessionId)
        if (currentTrackOnlyId.isBlank()) return emptyList()

        val allKeys = sharedPrefs.all.keys
        val sessionIds = allKeys
            .filter { it.endsWith("_outing_count") }
            .map { it.removeSuffix("_outing_count") }
            .distinct()

        val currentOuting = intent.getIntExtra("outing_number", 1)
        val result = mutableListOf<ComparableOuting>()
        sessionIds.forEach { sessionId ->
            val sessionTrackOnlyId = extractTrackIdFromSessionId(sessionId)
            if (sessionTrackOnlyId != currentTrackOnlyId) return@forEach

            val outingCount = sharedPrefs.getInt("${sessionId}_outing_count", 0)
            for (candidateOuting in 1..outingCount) {
                if (sessionId == currentSessionId && candidateOuting == currentOuting) continue

                val lapDataCount = sharedPrefs.getInt("${sessionId}_outing_${candidateOuting}_lap_data_count", 0)
                if (lapDataCount <= 0) continue

                val bestLapNumber = resolveBestLapNumber(sharedPrefs, sessionId, candidateOuting, lapDataCount)
                val bestLapTime = sharedPrefs.getString("${sessionId}_outing_${candidateOuting}_best_lap", "--:--.---") ?: "--:--.---"
                val date = sharedPrefs.getString("${sessionId}_outing_${candidateOuting}_date", "") ?: ""
                val time = sharedPrefs.getString("${sessionId}_outing_${candidateOuting}_time", "") ?: ""
                val label = "Session #$candidateOuting • $date $time • Best $bestLapTime"

                result.add(
                    ComparableOuting(
                        sessionId = sessionId,
                        outingNumber = candidateOuting,
                        bestLapNumber = bestLapNumber,
                        label = label
                    )
                )
            }
        }

        return result
    }

    private fun openLapCompare(target: ComparableOuting) {
        val sharedPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val currentSessionId = resolveCurrentSessionIdForCompare(sharedPrefs)
        if (currentSessionId.isBlank()) {
            Toast.makeText(this, "Липсва текуща сесия за сравнение", Toast.LENGTH_SHORT).show()
            return
        }

        val currentOuting = intent.getIntExtra("outing_number", 1)
        val currentLapDataCount = sharedPrefs.getInt("${currentSessionId}_outing_${currentOuting}_lap_data_count", 0)
        if (currentLapDataCount <= 0) {
            Toast.makeText(this, "Текущата сесия няма lap данни за сравнение", Toast.LENGTH_SHORT).show()
            return
        }

        val currentLap = currentLapNumber.coerceIn(1, currentLapDataCount)
        val isMotorcycle = intent.getBooleanExtra("is_motorcycle", true)

        startActivity(Intent(this, TrackLapCompareActivity::class.java).apply {
            putExtra("current_session_id", currentSessionId)
            putExtra("current_outing_number", currentOuting)
            putExtra("current_lap_number", currentLap)
            putExtra("compare_session_id", target.sessionId)
            putExtra("compare_outing_number", target.outingNumber)
            putExtra("compare_lap_number", target.bestLapNumber)
            putExtra("track_id", extractTrackIdFromSessionId(currentSessionId))
            putExtra("track_name", intent.getStringExtra("track_name") ?: extractTrackIdFromSessionId(currentSessionId))
            putExtra("is_motorcycle", isMotorcycle)
        })
    }

    private fun resolveCurrentSessionIdForCompare(sharedPrefs: android.content.SharedPreferences): String {
        val fullSessionId = intent.getStringExtra("full_session_id") ?: ""
        val currentOuting = intent.getIntExtra("outing_number", 1)
        if (fullSessionId.isNotBlank()) {
            val fullCount = sharedPrefs.getInt("${fullSessionId}_outing_${currentOuting}_lap_data_count", 0)
            if (fullCount > 0) return fullSessionId
        }

        val trackOnlyId = intent.getStringExtra("track_id") ?: ""
        if (trackOnlyId.isBlank()) return ""

        val currentProfileId = ProfileStorage.getSelectedProfileId(this)
        val sessionId = "${currentProfileId}_${trackOnlyId}"
        val count = sharedPrefs.getInt("${sessionId}_outing_${currentOuting}_lap_data_count", 0)
        return if (count > 0) sessionId else ""
    }

    private fun resolveBestLapNumber(
        prefs: android.content.SharedPreferences,
        sessionId: String,
        outing: Int,
        lapCount: Int
    ): Int {
        var bestLapNumber = 1
        var bestLapMs = Long.MAX_VALUE

        for (lap in 1..lapCount) {
            val lapTimeText = prefs.getString("${sessionId}_outing_${outing}_lap_${lap}", "--:--.---") ?: "--:--.---"
            val lapMs = parseLapTime(lapTimeText)
            if (lapMs < bestLapMs) {
                bestLapMs = lapMs
                bestLapNumber = lap
            }
        }

        return bestLapNumber
    }

    private fun parseLapTime(lapTimeText: String): Long {
        val parts = lapTimeText.split(":", ".")
        if (parts.size != 3) return Long.MAX_VALUE

        val minutes = parts[0].toLongOrNull() ?: return Long.MAX_VALUE
        val seconds = parts[1].toLongOrNull() ?: return Long.MAX_VALUE
        val millis = parts[2].toLongOrNull() ?: return Long.MAX_VALUE

        return minutes * 60_000L + seconds * 1_000L + millis
    }

    private fun extractTrackIdFromSessionId(sessionId: String): String {
        return TrackSessionIdUtils.extractTrackIdFromSessionId(this, sessionId)
    }
    
    private fun updateNavigationButtons() {
        // Update previous button
        btnPreviousLap.alpha = if (currentLapNumber > 1) 1.0f else 0.3f
        btnPreviousLap.isEnabled = currentLapNumber > 1
        
        // Update next button
        btnNextLap.alpha = if (currentLapNumber < totalLaps) 1.0f else 0.3f
        btnNextLap.isEnabled = currentLapNumber < totalLaps
    }

    private fun loadLapData(lapNumber: Int, lapTime: String, maxSpeed: String, 
                           maxGForce: String, maxCornering: String, isMotorcycle: Boolean) {
        tvLapTitle.text = getString(R.string.track_lap_label, lapNumber)
        
        // Load real lap data from storage
        loadRealLapData(lapNumber)
    }
    
    private fun loadRealLapData(lapNumber: Int) {
        // Get session data from intent
        val trackId = intent.getStringExtra("track_id") ?: ""
        val outingNumber = intent.getIntExtra("outing_number", 1)
        
        
        if (trackId.isEmpty()) {
            return
        }
        
        val sharedPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val currentProfileId = ProfileStorage.getSelectedProfileId(this)
        val sessionId = "${currentProfileId}_${trackId}"
        
        // Load lap data - try both formats
        var actualSessionId = sessionId
        var lapDataCount = sharedPrefs.getInt("${sessionId}_outing_${outingNumber}_lap_data_count", 0)
        // Update totalLaps with real data
        if (lapDataCount > 0) {
            totalLaps = lapDataCount
            updateNavigationButtons()
        }
        
        // If not found, try with full sessionId from intent
        if (lapDataCount == 0) {
            val fullSessionId = intent.getStringExtra("full_session_id") ?: ""
            if (fullSessionId.isNotEmpty()) {
                lapDataCount = sharedPrefs.getInt("${fullSessionId}_outing_${outingNumber}_lap_data_count", 0)
                if (lapDataCount > 0) {
                    // Update sessionId to use full one
                    actualSessionId = fullSessionId
                    totalLaps = lapDataCount
                    updateNavigationButtons()
                }
            }
        }
        
        if (lapDataCount > 0 && lapNumber <= lapDataCount) {
            val lapDataJson = sharedPrefs.getString("${actualSessionId}_outing_${outingNumber}_lap_data_${lapNumber}", null)
            if (lapDataJson != null) {
                try {
                    val gson = com.google.gson.Gson()
                    val lapData = gson.fromJson(lapDataJson, LapData::class.java)
                    processLapData(lapData)
                    return
                } catch (e: Exception) {
                }
            }
        }
    }
    
    private fun processLapData(lapData: LapData) {
        
        // Convert lap data to route points and NORMALIZE timestamps като в MapActivity
        if (lapData.routePoints.isNotEmpty()) {
            val baseTimestamp = lapData.routePoints.first().timestamp
            var tempRoutePoints = lapData.routePoints.map { point ->
                point.copy(timestamp = point.timestamp - baseTimestamp)
            }
            
            // SDK handles map matching - use raw route points
            val trackId = intent.getStringExtra("track_id") ?: ""
            if (trackId.isNotEmpty()) {
                // SDK handles snapping - use raw points
                routePoints = tempRoutePoints
            } else {
                routePoints = tempRoutePoints
            }
        } else {
            routePoints = emptyList()
        }
        
        // Mark that we have real data
        hasRealData = true
        
        // Update map with real route
        if (routePoints.isNotEmpty()) {
            setupMapWithRealData()
        } else {
        }
        
        // Update chart with real data
        updateChartWithRealData(lapData)
        
        // Update chart display with current mode
        val isMotorcycle = intent.getBooleanExtra("is_motorcycle", true)
        updateChartData(currentMode, isMotorcycle)
    }
    
    private fun setupMapWithRealData() {
        if (routePoints.isEmpty()) {
            return
        }
        
        
        // Съхраняваме цветните сегменти като отделни overlays
        saveOriginalRoute()
        currentReaderIndex = -1
        routeOverlaysAttached = false
        // Показваме целия маршрут ПЪРВО, след това добавяме маркера
        showFullRoute()

        // Setup map zoom
        setupMapZoom()
        map.invalidate()
    }

    private fun saveOriginalRoute() {
        originalRouteOverlays.clear()
        if (routePoints.size > 1) {
            for (i in 0 until routePoints.size - 1) {
                val startPoint = routePoints[i]
                val endPoint = routePoints[i + 1]

                val color = getSegmentAccelerationColor(startPoint, endPoint)

                val segmentPolyline = Polyline().apply {
                    setPoints(listOf(
                        org.osmdroid.util.GeoPoint(startPoint.geoPoint.latitude, startPoint.geoPoint.longitude),
                        org.osmdroid.util.GeoPoint(endPoint.geoPoint.latitude, endPoint.geoPoint.longitude)
                    ))
                    this.color = color
                    outlinePaint.color = color
                    outlinePaint.strokeWidth = 16f
                }
                originalRouteOverlays.add(segmentPolyline)
            }
        }
    }

    private fun getSegmentAccelerationColor(startPoint: RoutePoint, endPoint: RoutePoint): Int {
        val dtSec = ((endPoint.timestamp - startPoint.timestamp).coerceAtLeast(1L)) / 1000f
        if (dtSec <= 0f) return Color.rgb(120, 120, 120)

        val startSpeedMs = startPoint.speed / 3.6f
        val endSpeedMs = endPoint.speed / 3.6f
        val longitudinalMs2 = (endSpeedMs - startSpeedMs) / dtSec

        val accelThreshold = 0.10f
        val brakeThreshold = -0.10f

        return when {
            longitudinalMs2 >= accelThreshold -> Color.rgb(0, 220, 90)
            longitudinalMs2 <= brakeThreshold -> Color.rgb(255, 45, 45)
            else -> Color.rgb(120, 120, 120)
        }
    }

    private fun showFullRoute() {
        attachRouteOverlaysIfNeeded()
        if (originalRouteOverlays.isNotEmpty()) {
            originalRouteOverlays.forEach { it.isEnabled = true }
        }
        currentDrawingIndex = originalRouteOverlays.size
        map.postInvalidateOnAnimation()
    }

    private fun attachRouteOverlaysIfNeeded() {
        if (routeOverlaysAttached) return

        map.overlays.clear()
        if (originalRouteOverlays.isNotEmpty()) {
            map.overlays.addAll(originalRouteOverlays)
        }
        map.overlays.add(marker)
        routeOverlaysAttached = true
    }

    private fun drawRouteUpToIndex(index: Int): Boolean {
        attachRouteOverlaysIfNeeded()

        val targetIndex = minOf(index, originalRouteOverlays.size)
        if (targetIndex == currentDrawingIndex) {
            return false
        }

        if (currentDrawingIndex < 0) {
            for (i in originalRouteOverlays.indices) {
                originalRouteOverlays[i].isEnabled = i < targetIndex
            }
        } else if (targetIndex > currentDrawingIndex) {
            for (i in currentDrawingIndex until targetIndex) {
                if (i in originalRouteOverlays.indices) {
                    originalRouteOverlays[i].isEnabled = true
                }
            }
        } else {
            for (i in targetIndex until currentDrawingIndex) {
                if (i in originalRouteOverlays.indices) {
                    originalRouteOverlays[i].isEnabled = false
                }
            }
        }

        currentDrawingIndex = targetIndex
        return true
    }

    private fun startRouteDrawingTimer() {
        routeDrawingRunnable?.let { routeDrawingTimer?.removeCallbacks(it) }
        routeDrawingRunnable = Runnable {
            showFullRoute()
        }
        routeDrawingTimer = android.os.Handler(android.os.Looper.getMainLooper())
        routeDrawingRunnable?.let { routeDrawingTimer?.postDelayed(it, 3000) }
    }

    private fun updateChartWithRealData(lapData: LapData) {
        
        if (routePoints.isEmpty()) {
            return
        }

        // ТОЧНО като в MapActivity - използваме абсолютни времена
        // Конвертираме скоростта според избраната единица
        val speedUnit = UnitsManager.getSpeedUnit(this)
        val speedEntries = routePoints.map { 
            Entry(it.timestamp / 1000f, UnitsManager.convertSpeed(it.speed, speedUnit))
        }
        val angleEntries = routePoints.map { Entry(it.timestamp / 1000f, it.angle) }
        
        // G-force – добавяме към същите времена
        val firstTsSec = routePoints.first().timestamp / 1000f
        val lastTsSec = routePoints.last().timestamp / 1000f
        val durationSec = (lastTsSec - firstTsSec).coerceAtLeast(0f)
        
        val accelTriplets = lapData.accelerationData.chunked(3)
        val gForceEntries = if (accelTriplets.isNotEmpty()) {
            val count = accelTriplets.size
            accelTriplets.mapIndexed { index, accel ->
                val gForce = kotlin.math.sqrt(accel[0] * accel[0] + accel[1] * accel[1] + accel[2] * accel[2]) / 9.81f
                val t = if (count > 1) firstTsSec + (index.toFloat() / (count - 1)) * durationSec else firstTsSec
                Entry(t, gForce)
            }
        } else emptyList()
        
        realSpeedEntries = speedEntries
        realAngleEntries = angleEntries
        realGForceEntries = gForceEntries

        
        updateChartDataWithRealEntries(realSpeedEntries, realGForceEntries, realAngleEntries)
    }
    
    private fun updateChartDataWithRealEntries(speedEntries: List<Entry>, gForceEntries: List<Entry>, angleEntries: List<Entry>) {
        val isMotorcycle = intent.getBooleanExtra("is_motorcycle", true)
        
        val activeColor = when (currentMode) {
            Mode.SPEED -> Color.rgb(252, 120, 5)
            Mode.G_FORCE -> Color.rgb(255, 0, 0)
            Mode.ANGLE -> Color.rgb(5, 252, 227)
        }
        
        val fadedColor = when (currentMode) {
            Mode.SPEED -> Color.argb(105, 5, 252, 227)
            Mode.G_FORCE -> Color.argb(105, 255, 0, 0)
            Mode.ANGLE -> Color.argb(105, 252, 120, 5)
        }

        val speedUnitSymbol = UnitsManager.getSpeedUnit(this).symbol
        val speedDataSet = LineDataSet(speedEntries, "${getString(R.string.track_tab_speed)} ($speedUnitSymbol)").apply {
            color = if (currentMode == Mode.SPEED) activeColor else fadedColor
            lineWidth = if (currentMode == Mode.SPEED) 2f else 1f
            setDrawValues(false)
            setDrawCircles(false)
            if (currentMode != Mode.SPEED) enableDashedLine(10f, 5f, 0f)
        }

        val gForceDataSet = LineDataSet(gForceEntries, getString(R.string.track_tab_g_force)).apply {
            color = if (currentMode == Mode.G_FORCE) activeColor else fadedColor
            lineWidth = if (currentMode == Mode.G_FORCE) 2f else 1f
            setDrawValues(false)
            setDrawCircles(false)
            if (currentMode != Mode.G_FORCE) enableDashedLine(10f, 5f, 0f)
        }

        val lineData = if (isMotorcycle && angleEntries.isNotEmpty()) {
            val angleDataSet = LineDataSet(angleEntries, getString(R.string.track_tab_angle)).apply {
                color = if (currentMode == Mode.ANGLE) activeColor else fadedColor
                lineWidth = if (currentMode == Mode.ANGLE) 2f else 1f
                setDrawValues(false)
                setDrawCircles(false)
                if (currentMode != Mode.ANGLE) enableDashedLine(10f, 5f, 0f)
            }
            LineData(speedDataSet, gForceDataSet, angleDataSet)
        } else {
            LineData(speedDataSet, gForceDataSet)
        }

        chart.data = lineData
        chart.invalidate()
    }

    private fun setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        
        // ТОЧНО като в MapActivity - същите настройки
        map.isFocusable = true
        map.isClickable = true
        map.setUseDataConnection(true)
        
        // Initialize marker first - синя точка като в MapActivity
        marker = Marker(map).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "Позиция"
            
            // Създаваме синя точка като икона
            val blueDot = createBlueDotMarker()
            setIcon(blueDot)
        }
        // Данните се зареждат от loadRealLapData/processLapData
        // Не извикваме setupMapZoom тук, защото routePoints още не е наличен
    }


    private fun setupMapZoom() {
        if (routePoints.isEmpty()) return
        
        val allGeoPoints = routePoints.map { 
            org.osmdroid.util.GeoPoint(it.geoPoint.latitude, it.geoPoint.longitude)
        }
        
        if (allGeoPoints.size >= 2) {
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
            // Default center if no points
            map.controller.setZoom(15.0)
        }
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

        // ТОЧНО като в MapActivity - настройваме границите с extra space
        if (routePoints.isNotEmpty()) {
            val firstTime = routePoints.first().timestamp / 1000f
            val lastTime = routePoints.last().timestamp / 1000f
            val duration = lastTime - firstTime

            chart.xAxis.axisMinimum = firstTime - duration
            chart.xAxis.axisMaximum = lastTime + duration

            chart.moveViewToX(firstTime - duration * 0.1f)

            chart.setVisibleXRangeMaximum(duration)

            val initialCenterX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
            updateReaderPosition(initialCenterX)
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

        // ТОЧНО като в MapActivity - запазваме границите на данните
        val dataStartTime = if (routePoints.isNotEmpty()) routePoints.first().timestamp / 1000f else 0f
        val dataEndTime = if (routePoints.isNotEmpty()) routePoints.last().timestamp / 1000f else 0f

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
                val scaleFactorY = if (deltaY > deltaX * 1.5) detector.scaleFactor else 1f

                if (deltaX <= deltaY * 1.5 && deltaY <= deltaX * 1.5) {
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
                // Обновяваме позицията
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



    // Добавете този помощен метод за форматиране на времето:
    private fun formatTimeForReader(seconds: Float): String {
        val totalSeconds = seconds.toLong().coerceAtLeast(0)
        val min = totalSeconds / 60
        val sec = totalSeconds % 60
        return String.format("%02d:%02d", min, sec)
    }

    private fun setupTabs(isMotorcycle: Boolean) {
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.track_tab_speed)))
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.track_tab_g_force)))

        if (isMotorcycle) {
            tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.track_tab_angle)))
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentMode = when (tab?.position) {
                    0 -> Mode.SPEED
                    1 -> Mode.G_FORCE
                    2 -> if (isMotorcycle) Mode.ANGLE else Mode.SPEED
                    else -> Mode.SPEED
                }
                updateChartData(currentMode, isMotorcycle)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateChartData(mode: Mode, isMotorcycle: Boolean) {
        
        // Използваме само реални данни - без samples
        val speedEntries = if (routePoints.isNotEmpty()) {
            routePoints.map { Entry(it.timestamp / 1000f, it.speed) }
        } else {
            emptyList()
        }
        
        val angleEntries = if (isMotorcycle && routePoints.isNotEmpty()) {
            routePoints.map { Entry(it.timestamp / 1000f, it.angle) }
        } else {
            emptyList()
        }
        
        // G-force - само реални данни
        val gForceEntries = if (hasRealData && realGForceEntries.isNotEmpty()) realGForceEntries else emptyList()

        val activeColor = when (mode) {
            Mode.SPEED -> Color.rgb(252, 120, 5)
            Mode.G_FORCE -> Color.rgb(255, 0, 0)
            Mode.ANGLE -> Color.rgb(5, 252, 227)
        }
        
        val fadedColor = when (mode) {
            Mode.SPEED -> Color.argb(105, 5, 252, 227)
            Mode.G_FORCE -> Color.argb(105, 255, 0, 0)
            Mode.ANGLE -> Color.argb(105, 252, 120, 5)
        }

        val speedUnitSymbol = UnitsManager.getSpeedUnit(this).symbol
        val speedDataSet = LineDataSet(speedEntries, "${getString(R.string.track_tab_speed)} ($speedUnitSymbol)").apply {
            color = if (mode == Mode.SPEED) activeColor else fadedColor
            lineWidth = if (mode == Mode.SPEED) 2f else 1f
            setDrawValues(false)
            setDrawCircles(false)
            if (mode != Mode.SPEED) enableDashedLine(10f, 5f, 0f)
        }

        val gForceDataSet = LineDataSet(gForceEntries, getString(R.string.track_tab_g_force)).apply {
            color = if (mode == Mode.G_FORCE) activeColor else fadedColor
            lineWidth = if (mode == Mode.G_FORCE) 2f else 1f
            setDrawValues(false)
            setDrawCircles(false)
            if (mode != Mode.G_FORCE) enableDashedLine(10f, 5f, 0f)
        }

        val lineData = if (isMotorcycle && angleEntries.isNotEmpty()) {
            val angleDataSet = LineDataSet(angleEntries, getString(R.string.track_tab_angle)).apply {
                color = if (mode == Mode.ANGLE) activeColor else fadedColor
                lineWidth = if (mode == Mode.ANGLE) 2f else 1f
                setDrawValues(false)
                setDrawCircles(false)
                if (mode != Mode.ANGLE) enableDashedLine(10f, 5f, 0f)
            }
            LineData(speedDataSet, gForceDataSet, angleDataSet)
        } else {
            LineData(speedDataSet, gForceDataSet)
        }

        chart.data = lineData

        // ТОЧНО като в MapActivity - автоматично мащабиране на Y оста
        val yAxis = chart.axisLeft
        when (mode) {
            Mode.SPEED -> {
                val maxSpeed = if (routePoints.isNotEmpty()) {
                    routePoints.maxOfOrNull { it.speed } ?: 200f
                } else {
                    200f
                }
                yAxis.axisMinimum = 0f
                yAxis.axisMaximum = if (maxSpeed > 200) maxSpeed * 1.1f else 200f
                yAxis.setDrawZeroLine(true)
                yAxis.zeroLineColor = Color.GRAY
                yAxis.zeroLineWidth = 1f
            }
            Mode.G_FORCE -> {
                yAxis.axisMinimum = 0f
                yAxis.axisMaximum = 4f  // Обичайни G-force стойности
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


    private fun updateReaderPosition(timeInSeconds: Float) {
        val index = findClosestIndexToTime(timeInSeconds)
        if (index in routePoints.indices) {
            if (index == currentReaderIndex) return
            currentReaderIndex = index

            val point = routePoints[index]
            val osmdroidPoint = org.osmdroid.util.GeoPoint(point.geoPoint.latitude, point.geoPoint.longitude)
            marker.position = osmdroidPoint

            // Рисуваме маршрута до текущия индекс само ако потребителят е взаимодействал
            if (hasUserInteracted) {
                val routeChanged = drawRouteUpToIndex(index)
                if (routeChanged) {
                    startRouteDrawingTimer()
                }
            }

            // Update speed display
            val convertedSpeed = UnitsManager.formatSpeed(point.speed, this, 0)
            tvReaderSpeed.text = "${getString(R.string.track_speed_label)} $convertedSpeed"

            // Update angle display for motorcycles
            val isMotorcycle = intent.getBooleanExtra("is_motorcycle", true)
            if (isMotorcycle) {
                tvReaderAngle.visibility = View.VISIBLE
                tvReaderAngle.text = "${getString(R.string.track_tab_angle)}: ${"%.1f".format(point.angle)}°"
            } else {
                tvReaderAngle.visibility = View.GONE
            }

            map.postInvalidateOnAnimation()
        }
    }
    
    private fun findClosestIndexToTime(targetTimeSeconds: Float): Int {
        var closestIndex = 0
        var minDiff = Float.MAX_VALUE
        routePoints.forEachIndexed { index, routePoint ->
            val pointTimeSeconds = (routePoint.timestamp - routePoints.first().timestamp) / 1000f
            val diff = kotlin.math.abs(pointTimeSeconds - targetTimeSeconds)
            if (diff < minDiff) {
                minDiff = diff
                closestIndex = index
            }
        }
        return closestIndex
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

    override fun onBackPressed() {
        val intent = Intent(this, TrackSessionDetailActivity::class.java)
        // Предаваме обратно всички данни за да се върне към правилното излизане
        intent.putExtra("trackId", this.intent.getStringExtra("full_session_id") ?: this.intent.getStringExtra("track_id"))
        intent.putExtra("outingNumber", this.intent.getIntExtra("outing_number", 1))
        intent.putExtra("trackName", getString(R.string.track_name_serres)) // TODO: Може да се зареди динамично
        intent.putExtra("date", "")
        intent.putExtra("time", "")
        intent.putExtra("duration", "")
        intent.putExtra("totalLaps", totalLaps.toString())
        intent.putExtra("bestLapTime", "")
        intent.putExtra("maxSpeed", "")
        intent.putExtra("maxAcceleration", "")
        intent.putExtra("maxBraking", "")
        intent.putExtra("maxCorneringG", "")
        intent.putExtra("maxLeanAngle", "")
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }
}
