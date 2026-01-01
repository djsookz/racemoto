package com.example.clinometer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.pow
import com.example.clinometer.tracking.CustomTrack
import com.example.clinometer.tracking.CustomTrackStorage
import com.example.clinometer.tracking.TrackGeometry
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker
import java.util.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader

class CustomTrackBuilderActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var mapController: IMapController
    private lateinit var btnStartDrawing: Button
    private lateinit var btnStopDrawing: Button
    private lateinit var btnClear: Button
    private lateinit var btnSave: Button
    private lateinit var tvStatus: TextView
    private lateinit var etTrackName: EditText

    // Track type specific buttons
    private lateinit var btnAddStartFinish: Button
    private lateinit var btnAddStart: Button
    private lateinit var btnAddFinish: Button
    private lateinit var btnAddSnapHelper: Button

    private var isDrawing = false
    private var currentTrackPoints = mutableListOf<GeoPoint>()
    private var currentPolyline: Polyline? = null
    private var trackMarkers = mutableListOf<Marker>()
    private var trackType: CustomTrack.TrackType = CustomTrack.TrackType.CIRCUIT

    // Start/Finish line management
    private var startFinishLinePoints = mutableListOf<GeoPoint>()
    private var startFinishLine: Polyline? = null
    private var startFinishMarkers = mutableListOf<Marker>()
    private var isDraggingStartFinish = false
    private var draggedMarkerIndex = -1

    // Drag functionality
    private var isDraggingMarker = false
    private var draggedMarker: Marker? = null
    private var dragStartPoint: GeoPoint? = null

    // Double tap handling for undo
    private var lastTapTime = 0L
    private val DOUBLE_TAP_TIME_DELTA = 500L // milliseconds

    // Auto-routing functionality
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isRoutingInProgress = false

    // Circuit closing functionality
    private var firstPointMarker: Marker? = null

    // GPS variables
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null

    // Touch handling variables
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchMoved = false
    private val TOUCH_MOVE_THRESHOLD = 20f // pixels

    companion object {
        private const val TAG = "CustomTrackBuilder"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_track_builder)

        // Get track type from intent
        val trackTypeString = intent.getStringExtra("track_type") ?: "CIRCUIT"
        trackType = CustomTrack.TrackType.valueOf(trackTypeString)

        initViews()
        setupMap()
        setupButtons()
        updateUIForTrackType()
        initializeGPS()
    }

    private fun initViews() {
        mapView = findViewById(R.id.mapView)
        btnStartDrawing = findViewById(R.id.btnStartDrawing)
        btnStopDrawing = findViewById(R.id.btnStopDrawing)
        btnClear = findViewById(R.id.btnClear)
        btnSave = findViewById(R.id.btnSave)
        tvStatus = findViewById(R.id.tvStatus)
        etTrackName = findViewById(R.id.etTrackName)

        // Track type specific buttons
        btnAddStartFinish = findViewById(R.id.btnAddStartFinish)
        btnAddStart = findViewById(R.id.btnAddStart)
        btnAddFinish = findViewById(R.id.btnAddFinish)
        btnAddSnapHelper = findViewById(R.id.btnAddSnapHelper)

        // Back button
        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            onBackPressed()
        }
    }

    private fun updateUIForTrackType() {
        when (trackType) {
            CustomTrack.TrackType.CIRCUIT -> {
                // Show circuit buttons
                btnAddStartFinish.visibility = View.VISIBLE
                btnAddStart.visibility = View.GONE
                btnAddFinish.visibility = View.GONE
                btnAddSnapHelper.visibility = View.VISIBLE

                // Update instructions
                updateInstructionsForCircuit()
            }
            CustomTrack.TrackType.POINT_TO_POINT -> {
                // Show point-to-point buttons
                btnAddStartFinish.visibility = View.GONE
                btnAddStart.visibility = View.VISIBLE
                btnAddFinish.visibility = View.VISIBLE
                btnAddSnapHelper.visibility = View.VISIBLE

                // Update instructions
                updateInstructionsForPointToPoint()
            }
        }
    }

    private fun updateInstructionsForCircuit() {
        val instructions = """
            1. Въведете име на трасето
            2. Натиснете 'Добави старт/финиш' и кликнете на картата
            3. Натиснете 'Добави точки за снапване' и кликнете на картата
            4. Натиснете 'Започни' за да започнете да рисувате
            5. Натиснете точките на картата за да начертаете трасето
            6. Натиснете 'Спри' когато приключите
            7. Натиснете 'Запази' за да запазите трасето
        """.trimIndent()

        findViewById<TextView>(R.id.tvInstructions).text = instructions
    }

    private fun updateInstructionsForPointToPoint() {
        val instructions = """
            1. Въведете име на трасето
            2. Натиснете 'Добави старт точка' и кликнете на картата
            3. Натиснете 'Добави финиш точка' и кликнете на картата
            4. Натиснете 'Добави точки за снапване' и кликнете на картата
            5. Натиснете 'Започни' за да започнете да рисувате
            6. Натиснете точките на картата за да начертаете трасето
            7. Натиснете 'Спри' когато приключите
            8. Натиснете 'Запази' за да запазите трасето
        """.trimIndent()

        findViewById<TextView>(R.id.tvInstructions).text = instructions
    }

    private fun setupMap() {
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapController = mapView.controller
        mapController.setZoom(18.0)

        // Set initial location (Serres area)
        val serresCenter = GeoPoint(41.0858, 23.5497)
        mapController.setCenter(serresCenter)

        // Enable map interactions
        mapView.isClickable = true
        mapView.setMultiTouchControls(true)

        // Set up touch listener for drawing
        mapView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    touchMoved = false

                    // Check if we're touching a marker
                    val geoPoint = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                    val touchedMarker = findMarkerAtPoint(geoPoint)

                    if (touchedMarker != null) {
                        // We're touching a marker - let it handle the drag
                        isDraggingMarker = true
                        draggedMarker = touchedMarker
                        dragStartPoint = geoPoint
                        return@setOnTouchListener false // Let marker handle the touch
                    } else if (isDrawing) {
                        // We're drawing - track movement
                        touchMoved = false
                    }
                    false // Let map handle the touch
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDraggingMarker) {
                        // Let the marker handle dragging
                        return@setOnTouchListener false
                    } else if (isDrawing) {
                        val deltaX = abs(event.x - touchStartX)
                        val deltaY = abs(event.y - touchStartY)
                        if (deltaX > TOUCH_MOVE_THRESHOLD || deltaY > TOUCH_MOVE_THRESHOLD) {
                            touchMoved = true
                        }
                    }
                    false
                }
                MotionEvent.ACTION_UP -> {
                    if (isDraggingMarker) {
                        // Finish marker drag
                        isDraggingMarker = false
                        draggedMarker = null
                        dragStartPoint = null
                        return@setOnTouchListener false
                    } else if (isDrawing && !touchMoved) {
                        // Check for double tap (undo)
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapTime < DOUBLE_TAP_TIME_DELTA) {
                            // Double tap - undo last point
                            undoLastPoint()
                            lastTapTime = 0L // Reset to prevent triple tap
                            return@setOnTouchListener true
                        } else {
                            // Single tap - add point
                            lastTapTime = currentTime
                            val geoPoint = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                            addTrackPoint(geoPoint)
                            return@setOnTouchListener true
                        }
                    }
                    false // Let map handle the release
                }
                else -> false
            }
        }
    }

    private fun setupButtons() {
        btnStartDrawing.setOnClickListener {
            startDrawing()
        }

        btnStopDrawing.setOnClickListener {
            stopDrawing()
        }

        btnClear.setOnClickListener {
            clearTrack()
        }

        btnSave.setOnClickListener {
            saveTrack()
        }

        // Track type specific buttons
        btnAddStartFinish.setOnClickListener {
            addPointType(CustomTrack.TrackPoint.PointType.START_FINISH)
        }

        btnAddStart.setOnClickListener {
            addPointType(CustomTrack.TrackPoint.PointType.START)
        }

        btnAddFinish.setOnClickListener {
            addPointType(CustomTrack.TrackPoint.PointType.FINISH)
        }

        btnAddSnapHelper.setOnClickListener {
            addPointType(CustomTrack.TrackPoint.PointType.SNAP_HELPER)
        }
    }

    private fun addPointType(pointType: CustomTrack.TrackPoint.PointType) {
        tvStatus.text = when (pointType) {
            CustomTrack.TrackPoint.PointType.START_FINISH -> "Кликнете на картата за старт/финиш точка"
            CustomTrack.TrackPoint.PointType.START -> "Кликнете на картата за старт точка"
            CustomTrack.TrackPoint.PointType.FINISH -> "Кликнете на картата за финиш точка"
            CustomTrack.TrackPoint.PointType.SNAP_HELPER -> "Кликнете на картата за точка за снапване"
        }

        // Temporarily enable drawing for point placement
        isDrawing = true
        mapView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    touchMoved = false
                    false // Let map handle the touch
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = abs(event.x - touchStartX)
                    val deltaY = abs(event.y - touchStartY)
                    if (deltaX > TOUCH_MOVE_THRESHOLD || deltaY > TOUCH_MOVE_THRESHOLD) {
                        touchMoved = true
                    }
                    false // Let map handle the movement
                }
                MotionEvent.ACTION_UP -> {
                    if (!touchMoved) {
                        // Only add point if it was a tap, not a drag
                        val geoPoint = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                        addSpecialPoint(geoPoint, pointType)
                        isDrawing = false
                        tvStatus.text = "Готов за начертаване на трасето"
                        return@setOnTouchListener true
                    }
                    false // Let map handle the release
                }
                else -> false
            }
        }
    }

    private fun addSpecialPoint(geoPoint: GeoPoint, pointType: CustomTrack.TrackPoint.PointType) {
        when (pointType) {
            CustomTrack.TrackPoint.PointType.START_FINISH -> {
                addStartFinishLinePoint(geoPoint)
            }
            CustomTrack.TrackPoint.PointType.START -> {
                addSinglePoint(geoPoint, "Старт", R.drawable.ic_track_point_start)
            }
            CustomTrack.TrackPoint.PointType.FINISH -> {
                addSinglePoint(geoPoint, "Финиш", R.drawable.ic_track_point_finish)
            }
            CustomTrack.TrackPoint.PointType.SNAP_HELPER -> {
                addSinglePoint(geoPoint, "Снапване", R.drawable.ic_track_point_snap)
            }
        }
    }

    private fun addSinglePoint(geoPoint: GeoPoint, title: String, iconRes: Int) {
        val marker = Marker(mapView)
        marker.position = geoPoint
        marker.title = title

        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.setIcon(resources.getDrawable(iconRes, theme))

        mapView.overlays.add(marker)
        trackMarkers.add(marker)
        mapView.invalidate()

        Log.d(TAG, "Added $title point at ${geoPoint.latitude}, ${geoPoint.longitude}")
    }

    private fun addStartFinishLinePoint(geoPoint: GeoPoint) {
        // Allow 2 points for start/finish line
        if (startFinishLinePoints.size >= 2) {
            Toast.makeText(this, "Вече има 2 старт/финиш точки (линия). Изтрийте ги ако искате да добавите нови.", Toast.LENGTH_SHORT).show()
            return
        }

        startFinishLinePoints.add(geoPoint)

        // Create marker for this point
        val marker = Marker(mapView)
        marker.position = geoPoint
        marker.title = if (startFinishLinePoints.size == 1) "Старт/Финиш 1" else "Старт/Финиш 2"
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.setIcon(resources.getDrawable(R.drawable.ic_track_point_start_finish, theme))

        // Make marker draggable
        marker.isDraggable = true
        val markerIndex = startFinishLinePoints.size - 1
        marker.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: Marker) {
                isDraggingStartFinish = true
            }

            override fun onMarkerDrag(marker: Marker) {
                if (markerIndex < startFinishLinePoints.size) {
                    startFinishLinePoints[markerIndex] = marker.position as GeoPoint
                    updateStartFinishLine()
                }
            }

            override fun onMarkerDragEnd(marker: Marker) {
                isDraggingStartFinish = false
                if (markerIndex < startFinishLinePoints.size) {
                    startFinishLinePoints[markerIndex] = marker.position as GeoPoint
                    updateStartFinishLine()
                }
                Log.d(TAG, "Start/Finish point $markerIndex moved to ${marker.position.latitude}, ${marker.position.longitude}")
            }
        })

        mapView.overlays.add(marker)
        startFinishMarkers.add(marker)

        // Create or update line if we have 2 points
        if (startFinishLinePoints.size == 2) {
            createStartFinishLine()
            tvStatus.text = "✅ Старт/Финиш лиия създадена! Можете да преместите точките."
        } else {
            tvStatus.text = "✅ Първа старт/финиш точка създадена! Добавете втора за линия."
        }

        mapView.invalidate()
        Log.d(TAG, "Added start/finish point ${startFinishLinePoints.size} at ${geoPoint.latitude}, ${geoPoint.longitude}")
    }

    private fun createStartFinishLine() {
        if (startFinishLinePoints.size == 2) {
            // Remove existing line
            startFinishLine?.let { mapView.overlays.remove(it) }

            // Create new line
            val line = Polyline()
            line.setPoints(startFinishLinePoints)
            line.color = Color.RED
            line.width = 8f

            mapView.overlays.add(line)
            startFinishLine = line
            mapView.invalidate()

            Log.d(TAG, "Created start/finish line between ${startFinishLinePoints[0]} and ${startFinishLinePoints[1]}")
        }
    }

    private fun updateStartFinishLine() {
        if (startFinishLinePoints.size == 2) {
            startFinishLine?.setPoints(startFinishLinePoints)
            mapView.invalidate()
        }
    }

    private fun clearStartFinishLine() {
        // Remove markers
        startFinishMarkers.forEach { mapView.overlays.remove(it) }
        startFinishMarkers.clear()

        // Remove line
        startFinishLine?.let { mapView.overlays.remove(it) }
        startFinishLine = null

        // Clear points
        startFinishLinePoints.clear()

        mapView.invalidate()
    }

    private fun startDrawing() {
        if (etTrackName.text.toString().trim().isEmpty()) {
            Toast.makeText(this, "Моля въведете име на трасето", Toast.LENGTH_SHORT).show()
            return
        }

        isDrawing = true
        btnStartDrawing.isEnabled = false
        btnStopDrawing.isEnabled = true
        tvStatus.text = "Рисуване активно - кликнете на картата за да добавите точки. Двойно кликване за изтриване на последната точка."

        // Clear previous track line
        currentPolyline?.let { mapView.overlays.remove(it) }
        currentTrackPoints.clear()
    }

    private fun stopDrawing() {
        isDrawing = false
        btnStartDrawing.isEnabled = true
        btnStopDrawing.isEnabled = false
        tvStatus.text = "Рисуване спряно"

        // Hide first point marker
        hideFirstPointMarker()

        // Draw final track line
        drawTrackLine()
    }

    private fun addTrackPoint(geoPoint: GeoPoint) {
        if (currentTrackPoints.isEmpty()) {
            // First point - just add it
            currentTrackPoints.add(geoPoint)
            showFirstPointMarker(geoPoint)
            drawTrackLine()
            Log.d(TAG, "Added first track point: ${geoPoint.latitude}, ${geoPoint.longitude}")
        } else {
            // Check if we're clicking close to the first point (closing the circuit)
            val firstPoint = currentTrackPoints.first()
            val distanceToFirst = calculateDistance(firstPoint, geoPoint)
            val closeToFirstThreshold = 0.00005 // Approximately 5 meters - much smaller trigger area

            if (distanceToFirst <= closeToFirstThreshold && currentTrackPoints.size >= 3) {
                // Close the circuit - route from last point to first point
                val lastPoint = currentTrackPoints.last()
                        findRouteBetweenPoints(lastPoint, firstPoint) { routePoints ->
                            if (routePoints.isNotEmpty()) {
                                // Filter route points for smoother track
                                val filteredRoute = filterRoutePoints(routePoints)
                                // ✅ CRITICAL FIX: Add ALL route points including the first point to ensure line connects properly
                                // The last point of the route is exactly the first point, so line will connect seamlessly
                                currentTrackPoints.addAll(filteredRoute)
                                
                                
                                drawTrackLine()
                                Log.d(TAG, "🎯 CIRCUIT CLOSED! Added ${filteredRoute.size} route points (including first point for seamless connection) from ${lastPoint.latitude},${lastPoint.longitude} to first point ${firstPoint.latitude},${firstPoint.longitude}")

                                // Hide first point marker and show success message
                                hideFirstPointMarker()
                                tvStatus.text = "✅ Обиколката е затворена! Можете да запазите трака."
                            } else {
                                // ✅ CRITICAL FIX: Don't add first point again in fallback either!
                                // The circuit is already closed since we have the first point at the beginning
                                drawTrackLine()
                                Log.d(TAG, "🎯 CIRCUIT CLOSED (fallback)! No additional points needed - first point already exists")
                                hideFirstPointMarker()
                                tvStatus.text = "✅ Обиколката е затворена! Можете да запазите трака."
                            }
                        }
            } else {
                // Normal point - find route between last point and new point
                val lastPoint = currentTrackPoints.last()
                        findRouteBetweenPoints(lastPoint, geoPoint) { routePoints ->
                            if (routePoints.isNotEmpty()) {
                                // Filter route points for smoother track
                                val filteredRoute = filterRoutePoints(routePoints)
                                // Add all route points except the first one (we already have it)
                                currentTrackPoints.addAll(filteredRoute.drop(1))
                                
                                
                                drawTrackLine()
                                Log.d(TAG, "Added filtered route with ${filteredRoute.size} points from ${lastPoint.latitude},${lastPoint.longitude} to ${geoPoint.latitude},${geoPoint.longitude}")
                            } else {
                                // Fallback: add direct point if routing fails
                                currentTrackPoints.add(geoPoint)
                                drawTrackLine()
                                Log.d(TAG, "Routing failed, added direct point: ${geoPoint.latitude}, ${geoPoint.longitude}")
                            }
                        }
            }
        }
    }

    private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val latDiff = point1.latitude - point2.latitude
        val lonDiff = point1.longitude - point2.longitude
        return sqrt(latDiff * latDiff + lonDiff * lonDiff)
    }

    /**
     * Filter route points to reduce sharp turns and deviations
     * Removes points that are too close together or create sharp angles
     */
    private fun filterRoutePoints(routePoints: List<GeoPoint>): List<GeoPoint> {
        if (routePoints.size <= 2) return routePoints

        val filtered = mutableListOf<GeoPoint>()
        filtered.add(routePoints.first()) // Always keep first point

        var i = 1
        while (i < routePoints.size - 1) {
            val prev = filtered.last()
            val current = routePoints[i]

            // Calculate distance from previous point
            val distToPrev = calculateDistance(prev, current)

            // Only skip points that are extremely close (less than 2 meters)
            if (distToPrev < 0.00002) {
                i++
                continue
            }

            // Keep most points - only skip if they're too close
            filtered.add(current)

            i++
        }

        filtered.add(routePoints.last()) // Always keep last point

        Log.d(TAG, "🔧 Filtered route points: ${routePoints.size} -> ${filtered.size}")
        return filtered
    }


    /**
     * Calculate angle between three points in degrees
     */
    private fun calculateAngle(p1: GeoPoint, p2: GeoPoint, p3: GeoPoint): Double {
        // Vector from p2 to p1
        val v1x = p1.latitude - p2.latitude
        val v1y = p1.longitude - p2.longitude

        // Vector from p2 to p3
        val v2x = p3.latitude - p2.latitude
        val v2y = p3.longitude - p2.longitude

        // Calculate dot product
        val dot = v1x * v2x + v1y * v2y

        // Calculate magnitudes
        val mag1 = sqrt(v1x * v1x + v1y * v1y)
        val mag2 = sqrt(v2x * v2x + v2y * v2y)

        // Avoid division by zero
        if (mag1 == 0.0 || mag2 == 0.0) return 0.0

        // Calculate angle in radians, then convert to degrees
        val cosAngle = dot / (mag1 * mag2)
        val clampedCos = cosAngle.coerceIn(-1.0, 1.0) // Clamp to avoid numerical errors
        val angleRad = kotlin.math.acos(clampedCos)
        val angleDeg = Math.toDegrees(angleRad)

        return angleDeg
    }

    private fun showFirstPointMarker(geoPoint: GeoPoint) {
        // Remove existing marker if any
        firstPointMarker?.let { mapView.overlays.remove(it) }

        // Create new marker for first point
        val marker = Marker(mapView)
        marker.position = geoPoint
        marker.title = "Първа точка - кликнете тук за да затворите обиколката"
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.setIcon(resources.getDrawable(R.drawable.ic_track_point_start_finish, theme))

        // Make it slightly larger and different color to indicate it's special
        marker.icon?.setTint(Color.GREEN)

        mapView.overlays.add(marker)
        firstPointMarker = marker
        mapView.invalidate()
    }

    private fun hideFirstPointMarker() {
        firstPointMarker?.let {
            mapView.overlays.remove(it)
            firstPointMarker = null
            mapView.invalidate()
        }
    }

    private fun findRouteBetweenPoints(start: GeoPoint, end: GeoPoint, callback: (List<GeoPoint>) -> Unit) {
        if (isRoutingInProgress) {
            // If routing is already in progress, just add the direct point
            callback(listOf(end))
            return
        }

        isRoutingInProgress = true
        tvStatus.text = "Намиране на път..."

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val routePoints = getRouteFromOSRM(start, end)
                withContext(Dispatchers.Main) {
                    isRoutingInProgress = false
                    tvStatus.text = "Рисуване активно - кликнете на картата за да добавите точки"
                    callback(routePoints)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Routing failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    isRoutingInProgress = false
                    tvStatus.text = "Рисуване активно - кликнете на картата за да добавите точки"
                    callback(listOf(end)) // Fallback to direct point
                }
            }
        }
    }

    private suspend fun getRouteFromOSRM(start: GeoPoint, end: GeoPoint): List<GeoPoint> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson"

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val jsonResponse = JSONObject(response.toString())
                    val routes = jsonResponse.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val route = routes.getJSONObject(0)
                        val geometry = route.getJSONObject("geometry")
                        val coordinates = geometry.getJSONArray("coordinates")

                        val routePoints = mutableListOf<GeoPoint>()
                        for (i in 0 until coordinates.length()) {
                            val coord = coordinates.getJSONArray(i)
                            val lon = coord.getDouble(0)
                            val lat = coord.getDouble(1)
                            routePoints.add(GeoPoint(lat, lon))
                        }
                        routePoints
                    } else {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "OSRM API error: ${e.message}")
                emptyList()
            }
        }
    }

    private fun undoLastPoint() {
        if (currentTrackPoints.isNotEmpty()) {
            currentTrackPoints.removeLast()

            // If we're back to just the first point, show the marker again
            if (currentTrackPoints.size == 1) {
                showFirstPointMarker(currentTrackPoints.first())
            }

            drawTrackLine()
            Log.d(TAG, "Undid last track point. Remaining points: ${currentTrackPoints.size}")
        }
    }

    private fun findMarkerAtPoint(geoPoint: GeoPoint): Marker? {
        val allMarkers = startFinishMarkers + trackMarkers
        val tolerance = 0.0001 // Approximately 10 meters

        for (marker in allMarkers) {
            val markerPoint = marker.position as GeoPoint
            val latDiff = markerPoint.latitude - geoPoint.latitude
            val lonDiff = markerPoint.longitude - geoPoint.longitude
            val distance = sqrt(latDiff * latDiff + lonDiff * lonDiff)
            if (distance <= tolerance) {
                return marker
            }
        }
        return null
    }

    private fun drawTrackLine() {
        if (currentTrackPoints.size < 2) return

        // Remove previous polyline
        currentPolyline?.let { mapView.overlays.remove(it) }

        // Create new polyline
        val polyline = Polyline()
        polyline.setPoints(currentTrackPoints)
        polyline.color = Color.RED
        polyline.width = 8f

        mapView.overlays.add(polyline)
        currentPolyline = polyline
        mapView.invalidate()
    }


    private fun clearTrack() {
        AlertDialog.Builder(this)
            .setTitle("Изчистване на трасето")
            .setMessage("Сигурни ли сте, че искате да изчистите цялото трасе?")
            .setPositiveButton("Да") { _, _ ->
                // Clear all overlays except markers
                mapView.overlays.clear()
                mapView.overlays.addAll(trackMarkers)

                currentTrackPoints.clear()
                currentPolyline = null

                // Clear first point marker
                hideFirstPointMarker()

                // Clear start/finish line
                clearStartFinishLine()

                isDrawing = false
                btnStartDrawing.isEnabled = true
                btnStopDrawing.isEnabled = false
                tvStatus.text = "Готов за начертаване на трасето"

                mapView.invalidate()
            }
            .setNegativeButton("Отказ", null)
            .show()
    }

    private fun saveTrack() {
        val trackName = etTrackName.text.toString().trim()
        if (trackName.isEmpty()) {
            Toast.makeText(this, "Моля въведете име на трасето", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentTrackPoints.isEmpty() && trackMarkers.isEmpty()) {
            Toast.makeText(this, "Моля добавете точки на трасето първо", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate track based on type
        if (!validateTrack()) {
            return
        }

        // Create custom track
        val trackId = CustomTrackStorage.generateTrackId()

        // Convert all points to CustomTrack.TrackPoint
        val trackPoints = mutableListOf<CustomTrack.TrackPoint>()

        // Add drawing points as SNAP_HELPER
        currentTrackPoints.forEach { geoPoint ->
            trackPoints.add(CustomTrack.TrackPoint(geoPoint, CustomTrack.TrackPoint.PointType.SNAP_HELPER))
        }

        // Add start/finish line (2 points)
        startFinishLinePoints.forEach { point ->
            trackPoints.add(CustomTrack.TrackPoint(point, CustomTrack.TrackPoint.PointType.START_FINISH))
        }

        // Add other special points (start/finish/snap) from markers
        trackMarkers.forEach { marker ->
            val pointType = when (marker.title) {
                "Старт" -> CustomTrack.TrackPoint.PointType.START
                "Финиш" -> CustomTrack.TrackPoint.PointType.FINISH
                "Снапване" -> CustomTrack.TrackPoint.PointType.SNAP_HELPER
                else -> CustomTrack.TrackPoint.PointType.SNAP_HELPER
            }
            trackPoints.add(CustomTrack.TrackPoint(marker.position as GeoPoint, pointType))
        }

        val customTrack = CustomTrack(
            id = trackId,
            name = trackName,
            type = trackType,
            points = trackPoints
        )

        // Save track
        CustomTrackStorage.saveCustomTrack(this, customTrack)

        Toast.makeText(this, "Трасето '$trackName' е запазено успешно!", Toast.LENGTH_LONG).show()

        // Navigate back to track selection
        val intent = Intent(this, TrackSelectionActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun validateTrack(): Boolean {
        val totalPoints = currentTrackPoints.size + trackMarkers.size + startFinishLinePoints.size

        when (trackType) {
            CustomTrack.TrackType.CIRCUIT -> {
                // For circuit, we need at least 3 points to form a closed loop
                if (totalPoints < 3) {
                    Toast.makeText(this, "За обиколка са нужни поне 3 точки", Toast.LENGTH_SHORT).show()
                    return false
                }
            }
            CustomTrack.TrackType.POINT_TO_POINT -> {
                // For point-to-point, we need at least 2 points
                if (totalPoints < 2) {
                    Toast.makeText(this, "За точка-до-точка са нужни поне 2 точки", Toast.LENGTH_SHORT).show()
                    return false
                }
            }
        }

        // Validate start/finish line - must have exactly 2 points
        if (startFinishLinePoints.size != 2) {
            Toast.makeText(this, "Моля добавете 2 старт/финиш точки за линия", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    override fun onBackPressed() {
        if (isDrawing) {
            AlertDialog.Builder(this)
                .setTitle("Изход")
                .setMessage("Сигурни ли сте, че искате да излезете? Ще се загубят всички промени.")
                .setPositiveButton("Да") { _, _ ->
                    super.onBackPressed()
                }
                .setNegativeButton("Отказ", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }

    private fun initializeGPS() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (checkLocationPermission()) {
            getCurrentLocation()
        } else {
            requestLocationPermission()
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

    private fun getCurrentLocation() {
        if (!checkLocationPermission()) return

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        currentLocation = location
                        val geoPoint = GeoPoint(location.latitude, location.longitude)

                        // Center map on current location
                        mapController.setCenter(geoPoint)
                        mapController.setZoom(18.0)

                        Log.d("CustomTrackBuilder", "📍 Current location: ${location.latitude}, ${location.longitude}")
                        tvStatus.text = "Локацията е намерена. Готов за създаване на писта."
                    } else {
                        Log.w("CustomTrackBuilder", "Location is null")
                        tvStatus.text = "Локацията не е намерена. Използвайте картата за навигация."
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("CustomTrackBuilder", "Error getting location: ${e.message}")
                    tvStatus.text = "Грешка при получаване на локация. Използвайте картата за навигация."
                }
        } catch (e: SecurityException) {
            Log.e("CustomTrackBuilder", "Security exception: ${e.message}")
            tvStatus.text = "Няма разрешение за локация. Използвайте картата за навигация."
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
                getCurrentLocation()
            } else {
                tvStatus.text = "Няма разрешение за локация. Използвайте картата за навигация."
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}
