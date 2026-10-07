package com.revix.app.track

import android.Manifest
import android.content.Context
import android.content.Intent
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
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.revix.app.GeoPoint
import com.revix.app.DialogHelper
import com.revix.app.R
import com.revix.app.TrackSelectionActivity
import com.revix.app.applySystemBarsPaddingToRoot
import com.revix.app.navigation.MapboxDirectionsService
import com.revix.app.settings.LanguageManager
import com.revix.app.tracking.CustomTrack
import com.revix.app.tracking.CustomTrackDefinitionV2
import com.revix.app.tracking.CustomTrackCreationMode
import com.revix.app.tracking.CustomTrackExchange
import com.revix.app.tracking.CustomTrackExchangePoint
import com.revix.app.tracking.CustomTrackMode
import com.revix.app.tracking.CustomTrackStorage
import com.revix.app.tracking.GateLine
import com.revix.app.track.session.TrackGateCrossingEngine
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.gson.Gson
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.common.MapboxOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.plugin.annotation.Annotation
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.OnPointAnnotationDragListener
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class CustomTrackBuilderActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }


    private lateinit var mapContainer: FrameLayout
    private lateinit var mapView: MapView

    private lateinit var btnStartDrawing: Button
    private lateinit var btnStopDrawing: Button
    private lateinit var btnClear: Button
    private lateinit var btnSave: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvInstructions: TextView
    private lateinit var tvTrackModeInfo: TextView
    private lateinit var etTrackName: EditText

    private lateinit var btnAddStartFinish: Button
    private lateinit var btnAddStart: Button
    private lateinit var btnAddFinish: Button
    private lateinit var btnAddSnapHelper: Button
    private lateinit var btnImportTrack: TextView
    private lateinit var btnExportTrack: TextView

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var pointManager: PointAnnotationManager
    private lateinit var liveLocationPointManager: PointAnnotationManager
    private lateinit var polylineManager: PolylineAnnotationManager

    private var trackType: CustomTrack.TrackType = CustomTrack.TrackType.CIRCUIT
    private var creationMode: CreationMode = CreationMode.PHONE
    private var activeTool: BuilderTool = BuilderTool.SET_CIRCUIT_GATE
    private var isEditMode: Boolean = false

    private val checkpointPoints = mutableListOf<Point>()
    private val circuitGatePoints = mutableListOf<Point>()
    private val startGatePoints = mutableListOf<Point>()
    private val finishGatePoints = mutableListOf<Point>()

    private var editingTrackId: String? = null
    private var editingCreatedAt: Long = 0L

    private val gateAnnotationIndexById = mutableMapOf<String, Int>()
    private val checkpointAnnotationIndexById = mutableMapOf<String, Int>()
    private val startGateAnnotationIndexById = mutableMapOf<String, Int>()
    private val finishGateAnnotationIndexById = mutableMapOf<String, Int>()

    private var selectedPoint: SelectedPoint? = null
    private val undoStack = ArrayDeque<EditorSnapshot>()
    private var dragSnapshotCaptured = false
    private val gson = Gson()
    private var directionsService: MapboxDirectionsService? = null
    private var mapboxAccessToken: String = ""
    private var defaultMapboxAccessToken: String? = null
    private var isDrivingCaptureActive = false
    private val drivingRoutePoints = mutableListOf<Point>()
    private val phoneRoutePreviewPoints = mutableListOf<Point>()
    private var lastDrivingRoutePoint: Point? = null
    private var lastDrivingSampleLocation: Location? = null
    private var drivingRecordedDistanceMeters: Float = 0f
    private var phoneRoutePreviewDistanceMeters: Float? = null
    private var drivingCaptureStartedAtMs: Long = 0L
    private var hasMovedAwayFromStartLine: Boolean = false
    private var followLocationCallback: LocationCallback? = null
    private val routePreviewHandler = Handler(Looper.getMainLooper())
    private var phoneRouteRefreshRunnable: Runnable? = null
    private var phoneRouteRequestId: Long = 0L
    private var currentLiveLocation: Location? = null
    private var liveLocationAnnotation: PointAnnotation? = null
    private var hasCenteredCameraOnInitialLocation = false
    private val trackGateCrossingEngine = TrackGateCrossingEngine(lineThresholdMeters = 18.0)

    private val importTrackLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Temporary grant from OpenDocument is enough for one-shot import.
        }
        importTrackFromUri(uri)
    }

    private val exportTrackLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        exportTrackToUri(uri)
    }

    companion object {
        private const val TAG = "CustomTrackBuilder"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val CIRCUIT_CHECKPOINT_COUNT = 4
        private const val POINT_TO_POINT_MIN_CHECKPOINTS = 2
        private const val MIN_ROUTE_POINT_DISTANCE_METERS = 8.0
        private const val DEFAULT_GATE_WIDTH_METERS = 12.0
        private const val AUTO_STOP_MIN_DISTANCE_METERS = 220f
        private const val AUTO_STOP_MIN_ELAPSED_MS = 15_000L
        private const val AUTO_STOP_MIN_AWAY_FROM_LINE_METERS = 45.0
    }

    private enum class CreationMode {
        PHONE,
        DRIVING
    }

    private enum class BuilderTool {
        SET_CIRCUIT_GATE,
        SET_START,
        SET_FINISH,
        SET_CHECKPOINT
    }

    private enum class SelectedPointKind {
        GATE,
        CHECKPOINT,
        START,
        FINISH
    }

    private data class SelectedPoint(
        val kind: SelectedPointKind,
        val index: Int = -1
    )

    private data class EditorSnapshot(
        val checkpointPoints: List<Point>,
        val circuitGatePoints: List<Point>,
        val startGatePoints: List<Point>,
        val finishGatePoints: List<Point>,
        val selectedPoint: SelectedPoint?,
        val activeTool: BuilderTool
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_track_builder)
        applySystemBarsPaddingToRoot()
        applyCustomTrackMapboxAccessToken()

        trackType = CustomTrack.TrackType.valueOf(intent.getStringExtra("track_type") ?: "CIRCUIT")
        creationMode = try {
            CreationMode.valueOf(intent.getStringExtra("creation_mode") ?: "PHONE")
        } catch (_: Exception) {
            CreationMode.PHONE
        }

        initViews()
        setupMap()
        setupButtons()
        initializeRoutePreviewRouting()
        updateUIForTrackType(resetTool = true)
        initializeGPS()

        editingTrackId = intent.getStringExtra("edit_track_id")
        isEditMode = !editingTrackId.isNullOrBlank()
        if (!editingTrackId.isNullOrBlank()) {
            loadTrackForEditing(editingTrackId!!)
        }

        etTrackName.addTextChangedListener { updateBuilderState() }
        updateBuilderState()
    }

    private fun initViews() {
        mapContainer = findViewById(R.id.mapView)
        btnStartDrawing = findViewById(R.id.btnStartDrawing)
        btnStopDrawing = findViewById(R.id.btnStopDrawing)
        btnClear = findViewById(R.id.btnClear)
        btnSave = findViewById(R.id.btnSave)
        tvStatus = findViewById(R.id.tvStatus)
        tvInstructions = findViewById(R.id.tvInstructions)
        tvTrackModeInfo = findViewById(R.id.tvTrackModeInfo)
        etTrackName = findViewById(R.id.etTrackName)

        btnAddStartFinish = findViewById(R.id.btnAddStartFinish)
        btnAddStart = findViewById(R.id.btnAddStart)
        btnAddFinish = findViewById(R.id.btnAddFinish)
        btnAddSnapHelper = findViewById(R.id.btnAddSnapHelper)
        btnImportTrack = findViewById(R.id.btnImportTrack)
        btnExportTrack = findViewById(R.id.btnExportTrack)

        findViewById<View>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupMap() {
        val styleUri = getString(R.string.mapbox_custom_track_style_uri)
        mapView = MapView(this)
        mapContainer.addView(mapView)

        mapView.mapboxMap.loadStyleUri(styleUri)
        mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(23.5497, 41.0858))
                .zoom(15.0)
                .build()
        )

        pointManager = mapView.annotations.createPointAnnotationManager()
        liveLocationPointManager = mapView.annotations.createPointAnnotationManager()
        polylineManager = mapView.annotations.createPolylineAnnotationManager()

        pointManager.addDragListener(object : OnPointAnnotationDragListener {
            override fun onAnnotationDragStarted(annotation: Annotation<*>) {
                val pointAnnotation = annotation as? PointAnnotation ?: return
                if (!dragSnapshotCaptured) {
                    pushUndoSnapshot()
                    dragSnapshotCaptured = true
                }
                handleAnnotationDragged(pointAnnotation)
            }

            override fun onAnnotationDrag(annotation: Annotation<*>) {
                val pointAnnotation = annotation as? PointAnnotation ?: return
                handleAnnotationDragged(pointAnnotation)
            }

            override fun onAnnotationDragFinished(annotation: Annotation<*>) {
                val pointAnnotation = annotation as? PointAnnotation ?: return
                handleAnnotationDragged(pointAnnotation)
                normalizeCircuitCheckpointOrderIfNeeded(preferredCheckpoint = pointAnnotation.point)
                dragSnapshotCaptured = false
                redrawAnnotations()
                updateBuilderState()
                schedulePhoneRoutePreviewRefresh(immediate = true)
            }
        })

        pointManager.addClickListener { annotation ->
            onPointAnnotationSelected(annotation)
            true
        }

        mapView.mapboxMap.addOnMapClickListener { point ->
            handleMapClick(point)
            true
        }
    }

    private fun onPointAnnotationSelected(annotation: PointAnnotation) {
        val selected = when {
            gateAnnotationIndexById.containsKey(annotation.id) -> SelectedPoint(
                kind = SelectedPointKind.GATE,
                index = gateAnnotationIndexById[annotation.id] ?: -1
            )
            checkpointAnnotationIndexById.containsKey(annotation.id) -> SelectedPoint(
                kind = SelectedPointKind.CHECKPOINT,
                index = checkpointAnnotationIndexById[annotation.id] ?: -1
            )
            startGateAnnotationIndexById.containsKey(annotation.id) -> SelectedPoint(
                kind = SelectedPointKind.START,
                index = startGateAnnotationIndexById[annotation.id] ?: -1
            )
            finishGateAnnotationIndexById.containsKey(annotation.id) -> SelectedPoint(
                kind = SelectedPointKind.FINISH,
                index = finishGateAnnotationIndexById[annotation.id] ?: -1
            )
            else -> null
        }

        if (selected != null) {
            selectedPoint = selected
            redrawAnnotations()
            updateBuilderState()
            val message = if (creationMode == CreationMode.DRIVING) {
                getString(R.string.custom_track_point_selected)
            } else {
                getString(R.string.custom_track_point_selected_drag_undo)
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleAnnotationDragged(annotation: PointAnnotation) {
        if (isDrivingCaptureActive) return

        if (trackType == CustomTrack.TrackType.CIRCUIT) {
            val gateIndex = gateAnnotationIndexById[annotation.id]
            if (gateIndex != null && gateIndex in circuitGatePoints.indices) {
                circuitGatePoints[gateIndex] = annotation.point
                selectedPoint = SelectedPoint(SelectedPointKind.GATE, gateIndex)
                updatePhoneRoutePreviewFallback()
                redrawPolylines()
                updateBuilderState()
                return
            }

            val checkpointIndex = checkpointAnnotationIndexById[annotation.id]
            if (checkpointIndex != null && checkpointIndex in checkpointPoints.indices) {
                checkpointPoints[checkpointIndex] = annotation.point
                selectedPoint = SelectedPoint(SelectedPointKind.CHECKPOINT, checkpointIndex)
                updatePhoneRoutePreviewFallback()
                redrawPolylines()
                updateBuilderState()
            }
            return
        }

        val startIndex = startGateAnnotationIndexById[annotation.id]
        if (startIndex != null && startIndex in startGatePoints.indices) {
            startGatePoints[startIndex] = annotation.point
            selectedPoint = SelectedPoint(SelectedPointKind.START, startIndex)
            updatePhoneRoutePreviewFallback()
            redrawPolylines()
            updateBuilderState()
            return
        }

        val finishIndex = finishGateAnnotationIndexById[annotation.id]
        if (finishIndex != null && finishIndex in finishGatePoints.indices) {
            finishGatePoints[finishIndex] = annotation.point
            selectedPoint = SelectedPoint(SelectedPointKind.FINISH, finishIndex)
            updatePhoneRoutePreviewFallback()
            redrawPolylines()
            updateBuilderState()
            return
        }

        val checkpointIndex = checkpointAnnotationIndexById[annotation.id]
        if (checkpointIndex != null && checkpointIndex in checkpointPoints.indices) {
            checkpointPoints[checkpointIndex] = annotation.point
            selectedPoint = SelectedPoint(SelectedPointKind.CHECKPOINT, checkpointIndex)
            updatePhoneRoutePreviewFallback()
            redrawPolylines()
            updateBuilderState()
        }
    }

    private fun setupButtons() {
        btnAddStartFinish.setOnClickListener {
            activeTool = BuilderTool.SET_CIRCUIT_GATE
            updateBuilderState()
            Toast.makeText(this, getString(R.string.custom_track_step_start_finish_line), Toast.LENGTH_SHORT).show()
        }

        btnAddStart.setOnClickListener {
            activeTool = BuilderTool.SET_START
            updateBuilderState()
            Toast.makeText(this, getString(R.string.custom_track_step_start_line), Toast.LENGTH_SHORT).show()
        }

        btnAddFinish.setOnClickListener {
            activeTool = BuilderTool.SET_FINISH
            updateBuilderState()
            Toast.makeText(this, getString(R.string.custom_track_step_finish_line), Toast.LENGTH_SHORT).show()
        }

        btnStartDrawing.setOnClickListener {
            toggleDrivingCapture()
        }

        btnAddSnapHelper.setOnClickListener {
            if (creationMode == CreationMode.DRIVING) {
                autoGenerateCheckpointsFromDrivingRoute()
            } else {
                activeTool = BuilderTool.SET_CHECKPOINT
                updateBuilderState()
                Toast.makeText(this, getString(R.string.custom_track_step_checkpoints), Toast.LENGTH_SHORT).show()
            }
        }

        btnStopDrawing.setOnClickListener {
            handleUndoOrDelete()
        }

        btnClear.setOnClickListener {
            clearTrack()
        }

        btnSave.setOnClickListener {
            saveTrack()
        }

        btnImportTrack.setOnClickListener {
            // Accept any MIME — shared files often become application/octet-stream.
            importTrackLauncher.launch(arrayOf("*/*"))
        }

        btnExportTrack.setOnClickListener {
            val suggestedName = buildSuggestedExportFileName()
            exportTrackLauncher.launch(suggestedName)
        }
    }

    private fun buildSuggestedExportFileName(): String {
        val rawName = etTrackName.text.toString().trim().ifEmpty { "custom_track" }
        val safeName = rawName
            .replace("\\\\|/|:|\"|<|>|\\?|\\*".toRegex(), "_")
            .replace("\\s+".toRegex(), "_")
        return "${safeName}.revix-track.json"
    }

    private fun initializeRoutePreviewRouting() {
        mapboxAccessToken = getCustomTrackMapboxAccessToken()
        if (mapboxAccessToken.isBlank()) {
            Log.w(TAG, "Custom track Mapbox token missing. Phone custom routes will use straight-line fallback.")
            return
        }

        directionsService = Retrofit.Builder()
            .baseUrl("https://api.mapbox.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MapboxDirectionsService::class.java)
    }

    private fun getCustomTrackMapboxAccessToken(): String {
        return try {
            getString(R.string.mapbox_custom_track_access_token)
        } catch (_: Resources.NotFoundException) {
            ""
        }
    }

    private fun applyCustomTrackMapboxAccessToken() {
        val customTrackToken = getCustomTrackMapboxAccessToken()
        if (customTrackToken.isBlank()) return

        defaultMapboxAccessToken = MapboxOptions.accessToken
        MapboxOptions.accessToken = customTrackToken
    }

    private fun restoreDefaultMapboxAccessToken() {
        defaultMapboxAccessToken?.let { MapboxOptions.accessToken = it }
        defaultMapboxAccessToken = null
    }

    private fun lineMidpoint(points: List<Point>): Point? {
        if (points.size < 2) return null
        return Point.fromLngLat(
            (points[0].longitude() + points[1].longitude()) / 2.0,
            (points[0].latitude() + points[1].latitude()) / 2.0
        )
    }

    private fun collapseNearbyPoints(points: List<Point>, minDistanceMeters: Double = 5.0): List<Point> {
        if (points.isEmpty()) return emptyList()

        val result = mutableListOf(points.first())
        for (index in 1 until points.size) {
            val point = points[index]
            if (distanceMeters(result.last(), point) >= minDistanceMeters) {
                result.add(point)
            }
        }
        return result
    }

    private fun buildPhoneRouteControlPoints(): List<Point> {
        val points = mutableListOf<Point>()
        val orderedCheckpoints = currentCheckpointPointsInTrackOrder()

        when (trackType) {
            CustomTrack.TrackType.CIRCUIT -> {
                if (circuitGatePoints.size != 2 || orderedCheckpoints.size != CIRCUIT_CHECKPOINT_COUNT) {
                    return emptyList()
                }
                val startMid = lineMidpoint(circuitGatePoints) ?: return emptyList()
                points.add(startMid)
                points.addAll(orderedCheckpoints)
                points.add(startMid)
            }
            CustomTrack.TrackType.POINT_TO_POINT -> {
                if (
                    startGatePoints.size != 2 ||
                    finishGatePoints.size != 2 ||
                    orderedCheckpoints.size < POINT_TO_POINT_MIN_CHECKPOINTS
                ) {
                    return emptyList()
                }
                val startMid = lineMidpoint(startGatePoints) ?: return emptyList()
                val finishMid = lineMidpoint(finishGatePoints) ?: return emptyList()
                points.add(startMid)
                points.addAll(orderedCheckpoints)
                points.add(finishMid)
            }
        }

        return collapseNearbyPoints(points)
    }

    private fun calculatePointPathDistanceMeters(points: List<Point>): Float {
        if (points.size < 2) return 0f
        var totalMeters = 0f
        for (index in 1 until points.size) {
            totalMeters += distanceMeters(points[index - 1], points[index]).toFloat()
        }
        return totalMeters
    }

    private fun setPhoneRoutePreview(points: List<Point>, distanceMeters: Float?) {
        phoneRoutePreviewPoints.clear()
        phoneRoutePreviewPoints.addAll(points)
        phoneRoutePreviewDistanceMeters = distanceMeters?.takeIf { it.isFinite() && it > 0f }
    }

    private fun clearPhoneRoutePreview() {
        phoneRouteRefreshRunnable?.let(routePreviewHandler::removeCallbacks)
        phoneRouteRefreshRunnable = null
        phoneRouteRequestId += 1
        phoneRoutePreviewPoints.clear()
        phoneRoutePreviewDistanceMeters = null
    }

    private fun updatePhoneRoutePreviewFallback() {
        if (creationMode != CreationMode.PHONE) return

        if (!canRequestOnlinePhoneRoutePreview()) {
            clearPhoneRoutePreview()
            return
        }

        val controlPoints = buildPhoneRouteControlPoints()
        if (controlPoints.size < 2) {
            clearPhoneRoutePreview()
            return
        }

        setPhoneRoutePreview(controlPoints, calculatePointPathDistanceMeters(controlPoints))
    }

    private fun canRequestOnlinePhoneRoutePreview(): Boolean {
        if (creationMode != CreationMode.PHONE) return false

        return when (trackType) {
            CustomTrack.TrackType.CIRCUIT -> {
                circuitGatePoints.size == 2 && checkpointPoints.size == CIRCUIT_CHECKPOINT_COUNT
            }
            CustomTrack.TrackType.POINT_TO_POINT -> {
                startGatePoints.size == 2 &&
                    finishGatePoints.size == 2 &&
                    checkpointPoints.size >= POINT_TO_POINT_MIN_CHECKPOINTS
            }
        }
    }

    private fun schedulePhoneRoutePreviewRefresh(immediate: Boolean = false) {
        if (creationMode != CreationMode.PHONE) return

        phoneRouteRefreshRunnable?.let(routePreviewHandler::removeCallbacks)
        if (!canRequestOnlinePhoneRoutePreview()) {
            clearPhoneRoutePreview()
            redrawPolylines()
            updateBuilderState()
            return
        }

        val runnable = Runnable { refreshPhoneRoutePreviewFromWaypoints() }
        phoneRouteRefreshRunnable = runnable

        if (immediate) {
            routePreviewHandler.post(runnable)
        } else {
            routePreviewHandler.postDelayed(runnable, 250L)
        }
    }

    private fun refreshPhoneRoutePreviewFromWaypoints() {
        phoneRouteRefreshRunnable = null
        if (creationMode != CreationMode.PHONE) return

        val controlPoints = buildPhoneRouteControlPoints()
        if (controlPoints.size < 2) {
            clearPhoneRoutePreview()
            redrawPolylines()
            updateBuilderState()
            return
        }

        updatePhoneRoutePreviewFallback()
        redrawPolylines()
        updateBuilderState()

        if (trackType == CustomTrack.TrackType.CIRCUIT) {
            phoneRouteRequestId += 1
            return
        }

        if (!canRequestOnlinePhoneRoutePreview()) {
            phoneRouteRequestId += 1
            return
        }

        val routeService = directionsService ?: return
        if (mapboxAccessToken.isBlank()) return

        val requestId = ++phoneRouteRequestId
        val coordinates = controlPoints.joinToString(separator = ";") { point ->
            "${point.longitude()},${point.latitude()}"
        }

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    routeService.getRoute(
                        coordinates = coordinates,
                        accessToken = mapboxAccessToken,
                        steps = false,
                        bannerInstructions = false,
                        alternatives = false
                    )
                }

                if (requestId != phoneRouteRequestId) return@launch
                if (!response.isSuccessful) {
                    Log.w(TAG, "Phone route preview request failed: ${response.code()}")
                    return@launch
                }

                val route = response.body()?.routes?.firstOrNull() ?: return@launch
                val routePoints = route.geometry.coordinates.mapNotNull { coordinate ->
                    if (coordinate.size < 2) {
                        null
                    } else {
                        Point.fromLngLat(coordinate[0], coordinate[1])
                    }
                }
                if (routePoints.size < 2) return@launch

                setPhoneRoutePreview(routePoints, route.distance.toFloat())
                redrawPolylines()
                updateBuilderState()
            } catch (error: Exception) {
                if (requestId == phoneRouteRequestId) {
                    Log.w(TAG, "Phone route preview fallback kept after routing error: ${error.message}")
                }
            }
        }
    }

    private fun currentReferencePathPoints(): List<Point> {
        return when (creationMode) {
            CreationMode.DRIVING -> drivingRoutePoints
            CreationMode.PHONE -> phoneRoutePreviewPoints
        }
    }

    private fun currentMeasuredDistanceMeters(): Float? {
        return when (creationMode) {
            CreationMode.DRIVING -> drivingRecordedDistanceMeters.takeIf { it > 50f }
            CreationMode.PHONE -> phoneRoutePreviewDistanceMeters?.takeIf { it > 50f }
        }
    }

    private fun formatDistanceLabel(distanceMeters: Float): String {
        return if (distanceMeters >= 1000f) {
            String.format(Locale.US, "%.2f km", distanceMeters / 1000f).replace('.', ',')
        } else {
            "${distanceMeters.toInt()} m"
        }
    }

    private fun importTrackFromUri(uri: android.net.Uri) {
        try {
            val content = contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?.trim()
                ?.removePrefix("\uFEFF")
            if (content.isNullOrBlank()) {
                Toast.makeText(this, getString(R.string.custom_track_toast_empty_file), Toast.LENGTH_SHORT).show()
                return
            }

            val imported = CustomTrackExchange.parse(content, gson)
            val importedOk = applyImportedTrack(imported)
            if (importedOk) {
                Toast.makeText(this, getString(R.string.custom_track_toast_import_success), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Custom track import failed", e)
            Toast.makeText(this, getString(R.string.custom_track_toast_import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun exportTrackToUri(uri: android.net.Uri) {
        try {
            val exchange = createExportPayload()
            val json = gson.toJson(exchange)
            contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write(json)
                writer.flush()
            }
            Toast.makeText(this, getString(R.string.custom_track_toast_export_success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.custom_track_toast_export_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun createExportPayload(): CustomTrackExchange {
        val orderedCheckpoints = currentCheckpointPointsInTrackOrder()
        val referencePath = buildReferencePathForSave(orderedCheckpoints)
        return CustomTrackExchange(
            version = 2,
            name = etTrackName.text.toString().trim().ifEmpty { getString(R.string.custom_track_default_name) },
            mode = trackType.name,
            checkpointPoints = orderedCheckpoints.map {
                CustomTrackExchangePoint(lat = it.latitude(), lon = it.longitude())
            },
            circuitGatePoints = circuitGatePoints.map {
                CustomTrackExchangePoint(lat = it.latitude(), lon = it.longitude())
            },
            startGatePoints = startGatePoints.map {
                CustomTrackExchangePoint(lat = it.latitude(), lon = it.longitude())
            },
            finishGatePoints = finishGatePoints.map {
                CustomTrackExchangePoint(lat = it.latitude(), lon = it.longitude())
            },
            referencePath = referencePath.map {
                CustomTrackExchangePoint(lat = it.latitude, lon = it.longitude)
            },
            measuredDistanceMeters = resolveMeasuredDistanceForSave()
        )
    }

    private fun applyImportedTrack(imported: CustomTrackExchange): Boolean {
        val importedType = try {
            CustomTrack.TrackType.valueOf(imported.mode.trim().uppercase(Locale.US))
        } catch (_: Exception) {
            when {
                imported.circuitGatePoints.isNotEmpty() -> CustomTrack.TrackType.CIRCUIT
                imported.startGatePoints.isNotEmpty() || imported.finishGatePoints.isNotEmpty() ->
                    CustomTrack.TrackType.POINT_TO_POINT
                else -> trackType
            }
        }

        pushUndoSnapshot()

        // Auto-switch builder mode so import works regardless of how the screen was opened.
        trackType = importedType

        etTrackName.setText(imported.name)
        editingTrackId = null
        editingCreatedAt = 0L
        selectedPoint = null
        dragSnapshotCaptured = false

        checkpointPoints.clear()
        circuitGatePoints.clear()
        startGatePoints.clear()
        finishGatePoints.clear()
        clearPhoneRoutePreview()
        drivingRoutePoints.clear()
        lastDrivingRoutePoint = null
        lastDrivingSampleLocation = null
        drivingRecordedDistanceMeters = 0f

        checkpointPoints.addAll(
            imported.checkpointPoints.map { Point.fromLngLat(it.lon, it.lat) }
        )

        if (trackType == CustomTrack.TrackType.CIRCUIT) {
            circuitGatePoints.addAll(
                imported.circuitGatePoints.take(2).map { Point.fromLngLat(it.lon, it.lat) }
            )
            activeTool = if (circuitGatePoints.size < 2) {
                BuilderTool.SET_CIRCUIT_GATE
            } else {
                BuilderTool.SET_CHECKPOINT
            }
        } else {
            startGatePoints.addAll(
                imported.startGatePoints.take(2).map { Point.fromLngLat(it.lon, it.lat) }
            )
            finishGatePoints.addAll(
                imported.finishGatePoints.take(2).map { Point.fromLngLat(it.lon, it.lat) }
            )
            activeTool = when {
                startGatePoints.size < 2 -> BuilderTool.SET_START
                finishGatePoints.size < 2 -> BuilderTool.SET_FINISH
                else -> BuilderTool.SET_CHECKPOINT
            }
        }

        val importedReferencePath = imported.referencePath.orEmpty().map {
            Point.fromLngLat(it.lon, it.lat)
        }
        if (creationMode == CreationMode.DRIVING) {
            drivingRoutePoints.addAll(importedReferencePath)
            lastDrivingRoutePoint = drivingRoutePoints.lastOrNull()
            drivingRecordedDistanceMeters = imported.measuredDistanceMeters
                ?: calculatePointPathDistanceMeters(drivingRoutePoints)
        } else if (importedReferencePath.size >= 2) {
            setPhoneRoutePreview(
                importedReferencePath,
                imported.measuredDistanceMeters
                    ?: calculatePointPathDistanceMeters(importedReferencePath)
            )
        }

        normalizeCircuitCheckpointOrderIfNeeded()
        updateUIForTrackType(resetTool = false)
        redrawAnnotations()
        fitCameraToTrackPoints()
        mapView.post { fitCameraToTrackPoints() }
        updateBuilderState()
        if (creationMode == CreationMode.PHONE && phoneRoutePreviewPoints.size < 2) {
            schedulePhoneRoutePreviewRefresh(immediate = true)
        }
        return true
    }

    private fun trackTypeLabel(type: CustomTrack.TrackType): String {
        return when (type) {
            CustomTrack.TrackType.CIRCUIT -> getString(R.string.custom_track_type_circuit)
            CustomTrack.TrackType.POINT_TO_POINT -> getString(R.string.custom_track_type_point_to_point)
        }
    }

    private fun updateUIForTrackType(resetTool: Boolean) {
        btnStartDrawing.text = getString(R.string.custom_track_button_checkpoint)
        btnStopDrawing.text = getString(R.string.custom_track_button_undo)
        tvTrackModeInfo.text = getString(
            R.string.custom_track_mode_info,
            trackTypeLabel(trackType).replaceFirstChar { it.titlecase() },
            creationModeLabel()
        )

        if (creationMode == CreationMode.DRIVING) {
            btnStartDrawing.text = if (isDrivingCaptureActive) {
                getString(R.string.custom_track_button_stop_gps)
            } else {
                getString(R.string.custom_track_button_start_gps)
            }
            btnAddSnapHelper.text = getString(R.string.custom_track_button_auto_cp)
            btnAddSnapHelper.visibility = android.view.View.VISIBLE
            btnAddStartFinish.visibility = android.view.View.GONE
            btnAddStart.visibility = android.view.View.GONE
            btnAddFinish.visibility = android.view.View.GONE
        } else {
            btnAddSnapHelper.text = getString(R.string.custom_track_button_checkpoint)
        }

        when (trackType) {
            CustomTrack.TrackType.CIRCUIT -> {
                if (resetTool) activeTool = BuilderTool.SET_CIRCUIT_GATE
                if (creationMode == CreationMode.PHONE) {
                    btnAddStartFinish.visibility = android.view.View.VISIBLE
                    btnAddStart.visibility = android.view.View.GONE
                    btnAddFinish.visibility = android.view.View.GONE
                }
                if (creationMode == CreationMode.PHONE) {
                    btnAddSnapHelper.visibility = android.view.View.GONE
                }

                tvInstructions.text = if (creationMode == CreationMode.DRIVING) {
                    getString(R.string.custom_track_instructions_circuit_drive)
                } else {
                    getString(R.string.custom_track_instructions_circuit_phone)
                }
            }

            CustomTrack.TrackType.POINT_TO_POINT -> {
                if (resetTool) activeTool = BuilderTool.SET_START
                if (creationMode == CreationMode.PHONE) {
                    btnAddStartFinish.visibility = android.view.View.GONE
                    btnAddStart.visibility = android.view.View.VISIBLE
                    btnAddFinish.visibility = android.view.View.VISIBLE
                }
                if (creationMode == CreationMode.PHONE) {
                    btnAddSnapHelper.visibility = android.view.View.GONE
                }

                tvInstructions.text = if (creationMode == CreationMode.DRIVING) {
                    getString(R.string.custom_track_instructions_p2p_drive)
                } else {
                    getString(R.string.custom_track_instructions_p2p_phone)
                }
            }
        }
    }

    private fun creationModeLabel(): String {
        return when (creationMode) {
            CreationMode.PHONE -> getString(R.string.custom_track_creation_mode_phone)
            CreationMode.DRIVING -> getString(R.string.custom_track_creation_mode_driving)
        }
    }

    private fun handleMapClick(point: Point) {
        if (selectedPoint != null) {
            selectedPoint = null
            redrawAnnotations()
            updateBuilderState()
            return
        }

        if (creationMode == CreationMode.DRIVING) {
            Toast.makeText(this, getString(R.string.custom_track_toast_drive_mode_auto), Toast.LENGTH_SHORT).show()
            return
        }

        when (trackType) {
            CustomTrack.TrackType.CIRCUIT -> handleCircuitMapClick(point)
            CustomTrack.TrackType.POINT_TO_POINT -> handlePointToPointMapClick(point)
        }

        normalizeCircuitCheckpointOrderIfNeeded()
        updatePhoneRoutePreviewFallback()
        redrawAnnotations()
        updateBuilderState()
        schedulePhoneRoutePreviewRefresh()
    }

    private fun handleCircuitMapClick(point: Point) {
        when (activeTool) {
            BuilderTool.SET_CIRCUIT_GATE -> {
                pushUndoSnapshot()
                if (circuitGatePoints.size < 2) {
                    circuitGatePoints.add(point)
                } else {
                    val replaceIndex = nearestGatePointIndex(point)
                    circuitGatePoints[replaceIndex] = point
                }

                if (circuitGatePoints.size == 2) {
                    activeTool = BuilderTool.SET_CHECKPOINT
                }
            }

            BuilderTool.SET_CHECKPOINT -> {
                if (checkpointPoints.size >= CIRCUIT_CHECKPOINT_COUNT) {
                    Toast.makeText(this, getString(R.string.custom_track_toast_exactly_four_checkpoints), Toast.LENGTH_SHORT).show()
                    return
                }
                pushUndoSnapshot()
                checkpointPoints.add(point)
            }

            else -> Unit
        }
    }

    private fun handlePointToPointMapClick(point: Point) {
        when (activeTool) {
            BuilderTool.SET_START -> {
                pushUndoSnapshot()
                if (startGatePoints.size < 2) {
                    startGatePoints.add(point)
                } else {
                    val replaceIndex = nearestPointIndex(startGatePoints, point)
                    startGatePoints[replaceIndex] = point
                }
                if (startGatePoints.size == 2 && finishGatePoints.size < 2) {
                    activeTool = BuilderTool.SET_FINISH
                }
            }
            BuilderTool.SET_FINISH -> {
                pushUndoSnapshot()
                if (finishGatePoints.size < 2) {
                    finishGatePoints.add(point)
                } else {
                    val replaceIndex = nearestPointIndex(finishGatePoints, point)
                    finishGatePoints[replaceIndex] = point
                }
                if (startGatePoints.size == 2 && finishGatePoints.size == 2) {
                    activeTool = BuilderTool.SET_CHECKPOINT
                }
            }
            BuilderTool.SET_CHECKPOINT -> {
                pushUndoSnapshot()
                checkpointPoints.add(point)
            }
            else -> Unit
        }
    }

    private fun nearestGatePointIndex(target: Point): Int {
        if (circuitGatePoints.size < 2) return 0
        val d0 = distanceSquared(circuitGatePoints[0], target)
        val d1 = distanceSquared(circuitGatePoints[1], target)
        return if (d0 <= d1) 0 else 1
    }

    private fun nearestPointIndex(points: List<Point>, target: Point): Int {
        if (points.isEmpty()) return 0
        var nearestIndex = 0
        var nearestDistance = distanceSquared(points[0], target)
        for (index in 1 until points.size) {
            val distance = distanceSquared(points[index], target)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestIndex = index
            }
        }
        return nearestIndex
    }

    private fun distanceSquared(a: Point, b: Point): Double {
        val dx = a.longitude() - b.longitude()
        val dy = a.latitude() - b.latitude()
        return dx * dx + dy * dy
    }

    private fun redrawAnnotations() {
        pointManager.deleteAll()
        gateAnnotationIndexById.clear()
        checkpointAnnotationIndexById.clear()
        startGateAnnotationIndexById.clear()
        finishGateAnnotationIndexById.clear()

        clearInvalidSelection()

        val allowGateDrag = !isDrivingCaptureActive
        val allowCheckpointDrag = creationMode == CreationMode.PHONE && !isDrivingCaptureActive

        if (trackType == CustomTrack.TrackType.CIRCUIT) {
            circuitGatePoints.forEachIndexed { index, gatePoint ->
                val annotation = pointManager.create(
                    PointAnnotationOptions()
                        .withPoint(gatePoint)
                        .withIconImage(
                            createMarkerBitmap(
                                if (index == 0) "S/F 1" else "S/F 2",
                                markerColor(Color.parseColor("#2563EB"), isSelected(SelectedPointKind.GATE, index))
                            )
                        )
                        .withIconAnchor(IconAnchor.BOTTOM)
                        .withDraggable(allowGateDrag)
                )
                gateAnnotationIndexById[annotation.id] = index
            }
        } else {
            startGatePoints.forEachIndexed { index, point ->
                val annotation = pointManager.create(
                    PointAnnotationOptions()
                        .withPoint(point)
                        .withIconImage(
                            createMarkerBitmap(
                                "ST ${index + 1}",
                                markerColor(Color.parseColor("#16A34A"), isSelected(SelectedPointKind.START, index))
                            )
                        )
                        .withIconAnchor(IconAnchor.BOTTOM)
                        .withDraggable(allowGateDrag)
                )
                startGateAnnotationIndexById[annotation.id] = index
            }

            finishGatePoints.forEachIndexed { index, point ->
                val annotation = pointManager.create(
                    PointAnnotationOptions()
                        .withPoint(point)
                        .withIconImage(
                            createMarkerBitmap(
                                "FN ${index + 1}",
                                markerColor(Color.parseColor("#DC2626"), isSelected(SelectedPointKind.FINISH, index))
                            )
                        )
                        .withIconAnchor(IconAnchor.BOTTOM)
                        .withDraggable(allowGateDrag)
                )
                finishGateAnnotationIndexById[annotation.id] = index
            }
        }

        checkpointPoints.forEachIndexed { index, checkpoint ->
            val annotation = pointManager.create(
                PointAnnotationOptions()
                    .withPoint(checkpoint)
                    .withIconImage(
                        createMarkerBitmap(
                            "CP ${index + 1}",
                            markerColor(Color.parseColor("#0EA5E9"), isSelected(SelectedPointKind.CHECKPOINT, index))
                        )
                    )
                    .withIconAnchor(IconAnchor.BOTTOM)
                    .withDraggable(allowCheckpointDrag)
            )
            checkpointAnnotationIndexById[annotation.id] = index
        }

        updateLiveLocationAnnotation(currentLiveLocation)
        redrawPolylines()
    }

    private fun updateLiveLocationAnnotation(location: Location?) {
        if (!::liveLocationPointManager.isInitialized) return

        if (location == null) {
            liveLocationAnnotation?.let(liveLocationPointManager::delete)
            liveLocationAnnotation = null
            return
        }

        val point = Point.fromLngLat(location.longitude, location.latitude)
        val existingAnnotation = liveLocationAnnotation
        if (existingAnnotation == null) {
            liveLocationAnnotation = liveLocationPointManager.create(
                PointAnnotationOptions()
                    .withPoint(point)
                    .withIconImage(createLiveLocationBitmap())
                    .withIconAnchor(IconAnchor.CENTER)
                    .withDraggable(false)
            )
            return
        }

        existingAnnotation.point = point
        liveLocationPointManager.update(existingAnnotation)
    }

    private fun createLiveLocationBitmap(): Bitmap {
        val size = 56
        val center = size / 2f
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#66F59E0B")
        }
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F59E0B")
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        canvas.drawCircle(center, center, 20f, haloPaint)
        canvas.drawCircle(center, center, 11f, corePaint)
        canvas.drawCircle(center, center, 11f, strokePaint)

        return bitmap
    }

    private fun currentCheckpointPointsInTrackOrder(): List<Point> {
        return when (trackType) {
            CustomTrack.TrackType.CIRCUIT -> buildOrderedCircuitCheckpointPoints(checkpointPoints)
            CustomTrack.TrackType.POINT_TO_POINT -> checkpointPoints.toList()
        }
    }

    private fun normalizeCircuitCheckpointOrderIfNeeded(preferredCheckpoint: Point? = null) {
        if (creationMode != CreationMode.PHONE) return
        if (trackType != CustomTrack.TrackType.CIRCUIT) return
        if (circuitGatePoints.size != 2 || checkpointPoints.size != CIRCUIT_CHECKPOINT_COUNT) return

        val referenceCheckpoint = preferredCheckpoint ?: selectedCheckpointPoint()
        val ordered = buildOrderedCircuitCheckpointPoints(checkpointPoints)
        if (ordered.size != checkpointPoints.size) return
        if (ordered.indices.all { index -> pointsAreNear(ordered[index], checkpointPoints[index]) }) {
            return
        }

        checkpointPoints.clear()
        checkpointPoints.addAll(ordered)

        if (referenceCheckpoint != null) {
            selectedPoint = SelectedPoint(
                kind = SelectedPointKind.CHECKPOINT,
                index = nearestPointIndex(checkpointPoints, referenceCheckpoint)
            )
        }
    }

    private fun selectedCheckpointPoint(): Point? {
        val selected = selectedPoint ?: return null
        if (selected.kind != SelectedPointKind.CHECKPOINT) return null
        if (selected.index !in checkpointPoints.indices) return null
        return checkpointPoints[selected.index]
    }

    private fun buildOrderedCircuitCheckpointPoints(points: List<Point>): List<Point> {
        if (trackType != CustomTrack.TrackType.CIRCUIT) return points.toList()
        if (circuitGatePoints.size != 2 || points.size != CIRCUIT_CHECKPOINT_COUNT) return points.toList()

        val startMid = lineMidpoint(circuitGatePoints) ?: return points.toList()
        var bestOrder = points.toList()
        var bestScore = Double.POSITIVE_INFINITY

        forEachPointPermutation(points) { candidate ->
            val closedLoop = buildList {
                add(startMid)
                addAll(candidate)
                add(startMid)
            }
            val intersectionPenalty = countPolylineSelfIntersections(closedLoop) * 1_000_000.0
            val routeLength = calculatePointPathDistanceMeters(closedLoop).toDouble()
            val entryBalancePenalty = abs(distanceMeters(startMid, candidate.first()) - distanceMeters(startMid, candidate.last()))
            val score = intersectionPenalty + routeLength + entryBalancePenalty
            if (score < bestScore) {
                bestScore = score
                bestOrder = candidate.toList()
            }
        }

        return bestOrder
    }

    private fun forEachPointPermutation(points: List<Point>, onPermutation: (List<Point>) -> Unit) {
        val working = points.toMutableList()

        fun permute(startIndex: Int) {
            if (startIndex >= working.lastIndex) {
                onPermutation(working.toList())
                return
            }

            for (index in startIndex until working.size) {
                val current = working[startIndex]
                working[startIndex] = working[index]
                working[index] = current
                permute(startIndex + 1)
                working[index] = working[startIndex]
                working[startIndex] = current
            }
        }

        permute(0)
    }

    private fun countPolylineSelfIntersections(points: List<Point>): Int {
        if (points.size < 4) return 0

        var intersections = 0
        val segmentCount = points.size - 1
        for (firstIndex in 0 until segmentCount) {
            val firstStart = points[firstIndex]
            val firstEnd = points[firstIndex + 1]
            for (secondIndex in firstIndex + 1 until segmentCount) {
                if (secondIndex <= firstIndex + 1) continue
                if (firstIndex == 0 && secondIndex == segmentCount - 1) continue

                val secondStart = points[secondIndex]
                val secondEnd = points[secondIndex + 1]
                if (segmentsIntersect(firstStart, firstEnd, secondStart, secondEnd)) {
                    intersections += 1
                }
            }
        }
        return intersections
    }

    private fun segmentsIntersect(aStart: Point, aEnd: Point, bStart: Point, bEnd: Point): Boolean {
        val eps = 1e-10
        val o1 = orientation(aStart, aEnd, bStart)
        val o2 = orientation(aStart, aEnd, bEnd)
        val o3 = orientation(bStart, bEnd, aStart)
        val o4 = orientation(bStart, bEnd, aEnd)

        if ((o1 > eps && o2 < -eps || o1 < -eps && o2 > eps) && (o3 > eps && o4 < -eps || o3 < -eps && o4 > eps)) {
            return true
        }

        if (abs(o1) <= eps && pointOnSegment(aStart, bStart, aEnd)) return true
        if (abs(o2) <= eps && pointOnSegment(aStart, bEnd, aEnd)) return true
        if (abs(o3) <= eps && pointOnSegment(bStart, aStart, bEnd)) return true
        if (abs(o4) <= eps && pointOnSegment(bStart, aEnd, bEnd)) return true

        return false
    }

    private fun orientation(start: Point, middle: Point, end: Point): Double {
        return (middle.longitude() - start.longitude()) * (end.latitude() - start.latitude()) -
            (middle.latitude() - start.latitude()) * (end.longitude() - start.longitude())
    }

    private fun pointOnSegment(start: Point, point: Point, end: Point): Boolean {
        return point.longitude() >= minOf(start.longitude(), end.longitude()) - 1e-10 &&
            point.longitude() <= maxOf(start.longitude(), end.longitude()) + 1e-10 &&
            point.latitude() >= minOf(start.latitude(), end.latitude()) - 1e-10 &&
            point.latitude() <= maxOf(start.latitude(), end.latitude()) + 1e-10
    }

    private fun pointsAreNear(first: Point, second: Point): Boolean {
        return distanceMeters(first, second) <= 0.5
    }

    private fun redrawPolylines() {
        polylineManager.deleteAll()

        // Phone/driving reference routes are still computed for save/export but not drawn on the map.
        // Mapbox road routing often misrepresents circuit layouts on private track surfaces.

        if (trackType == CustomTrack.TrackType.CIRCUIT && circuitGatePoints.size == 2) {
            polylineManager.create(
                PolylineAnnotationOptions()
                    .withPoints(circuitGatePoints)
                    .withLineColor("#2563EB")
                    .withLineWidth(6.0)
            )
            drawGateDirectionArrow(circuitGatePoints)
        } else if (trackType == CustomTrack.TrackType.POINT_TO_POINT) {
            if (startGatePoints.size == 2) {
                polylineManager.create(
                    PolylineAnnotationOptions()
                        .withPoints(startGatePoints)
                        .withLineColor("#16A34A")
                        .withLineWidth(6.0)
                )
                drawGateDirectionArrow(startGatePoints)
            }
            if (finishGatePoints.size == 2) {
                polylineManager.create(
                    PolylineAnnotationOptions()
                        .withPoints(finishGatePoints)
                        .withLineColor("#DC2626")
                        .withLineWidth(6.0)
                )
            }
        }
    }

    private fun drawGateDirectionArrow(points: List<Point>) {
        if (points.size < 2) return
        val start = points[0]
        val end = points[1]
        TrackGateDirection.arrowPolylines(
            startLatitude = start.latitude(),
            startLongitude = start.longitude(),
            endLatitude = end.latitude(),
            endLongitude = end.longitude()
        ).forEach { polyline ->
            polylineManager.create(
                PolylineAnnotationOptions()
                    .withPoints(polyline.map { Point.fromLngLat(it.longitude, it.latitude) })
                    .withLineColor("#FFFFFF")
                    .withLineWidth(3.0)
            )
        }
    }

    private fun createMarkerBitmap(label: String, color: Int): Bitmap {
        val width = 132
        val height = 82
        val bubbleHeight = 56f
        val anchorY = 72f
        val anchorRadius = 6f
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = 22f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val anchorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE }

        canvas.drawRoundRect(0f, 0f, width.toFloat(), bubbleHeight, 14f, 14f, bgPaint)
        canvas.drawRect(width / 2f - 3f, bubbleHeight - 1f, width / 2f + 3f, anchorY - anchorRadius, bgPaint)
        canvas.drawCircle(width / 2f, anchorY, anchorRadius, anchorPaint)
        canvas.drawText(label, width / 2f, bubbleHeight / 2f + 8f, textPaint)

        return bitmap
    }

    private fun clearInvalidSelection() {
        val selected = selectedPoint ?: return
        val isValid = when (selected.kind) {
            SelectedPointKind.GATE -> selected.index in circuitGatePoints.indices
            SelectedPointKind.CHECKPOINT -> selected.index in checkpointPoints.indices
            SelectedPointKind.START -> selected.index in startGatePoints.indices
            SelectedPointKind.FINISH -> selected.index in finishGatePoints.indices
        }
        if (!isValid) {
            selectedPoint = null
        }
    }

    private fun markerColor(baseColor: Int, isSelected: Boolean): Int {
        return if (isSelected) Color.parseColor("#F59E0B") else baseColor
    }

    private fun isSelected(kind: SelectedPointKind, index: Int = -1): Boolean {
        val selected = selectedPoint ?: return false
        if (selected.kind != kind) return false
        return if (index >= 0) selected.index == index else true
    }

    private fun captureSnapshot(): EditorSnapshot {
        return EditorSnapshot(
            checkpointPoints = checkpointPoints.toList(),
            circuitGatePoints = circuitGatePoints.toList(),
            startGatePoints = startGatePoints.toList(),
            finishGatePoints = finishGatePoints.toList(),
            selectedPoint = selectedPoint,
            activeTool = activeTool
        )
    }

    private fun restoreSnapshot(snapshot: EditorSnapshot) {
        checkpointPoints.clear()
        checkpointPoints.addAll(snapshot.checkpointPoints)

        circuitGatePoints.clear()
        circuitGatePoints.addAll(snapshot.circuitGatePoints)

        startGatePoints.clear()
        startGatePoints.addAll(snapshot.startGatePoints)

        finishGatePoints.clear()
        finishGatePoints.addAll(snapshot.finishGatePoints)

        selectedPoint = snapshot.selectedPoint
        activeTool = snapshot.activeTool
    }

    private fun pushUndoSnapshot() {
        undoStack.addLast(captureSnapshot())
    }

    private fun deleteSelectedPointIfAny(): Boolean {
        val selected = selectedPoint ?: return false
        pushUndoSnapshot()

        when (selected.kind) {
            SelectedPointKind.GATE -> {
                if (selected.index !in circuitGatePoints.indices) return false
                circuitGatePoints.removeAt(selected.index)
            }
            SelectedPointKind.CHECKPOINT -> {
                if (selected.index !in checkpointPoints.indices) return false
                checkpointPoints.removeAt(selected.index)
            }
            SelectedPointKind.START -> {
                if (selected.index !in startGatePoints.indices) return false
                startGatePoints.removeAt(selected.index)
            }
            SelectedPointKind.FINISH -> {
                if (selected.index !in finishGatePoints.indices) return false
                finishGatePoints.removeAt(selected.index)
            }
        }

        selectedPoint = null
        updatePhoneRoutePreviewFallback()
        redrawAnnotations()
        updateBuilderState()
        schedulePhoneRoutePreviewRefresh(immediate = true)
        Toast.makeText(this, getString(R.string.custom_track_toast_point_deleted), Toast.LENGTH_SHORT).show()
        return true
    }

    private fun handleUndoOrDelete() {
        if (selectedPoint != null) {
            deleteSelectedPointIfAny()
            return
        }

        if (undoStack.isEmpty()) {
            Toast.makeText(this, getString(R.string.custom_track_toast_nothing_to_undo), Toast.LENGTH_SHORT).show()
            return
        }

        val snapshot = undoStack.removeLast()
        restoreSnapshot(snapshot)
        updatePhoneRoutePreviewFallback()
        redrawAnnotations()
        updateBuilderState()
        schedulePhoneRoutePreviewRefresh(immediate = true)
    }

    private fun clearTrack() {
        DialogHelper.show(
            DialogHelper.builder(this)
                .setTitle(getString(R.string.custom_track_clear_title))
                .setMessage(getString(R.string.custom_track_clear_message))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                pushUndoSnapshot()
                checkpointPoints.clear()
                circuitGatePoints.clear()
                startGatePoints.clear()
                finishGatePoints.clear()
                drivingRoutePoints.clear()
                clearPhoneRoutePreview()
                lastDrivingRoutePoint = null
                lastDrivingSampleLocation = null
                drivingRecordedDistanceMeters = 0f
                hasMovedAwayFromStartLine = false
                drivingCaptureStartedAtMs = 0L
                selectedPoint = null
                activeTool = if (trackType == CustomTrack.TrackType.CIRCUIT) {
                    BuilderTool.SET_CIRCUIT_GATE
                } else {
                    BuilderTool.SET_START
                }
                redrawAnnotations()
                updateBuilderState()
            }
                .setNegativeButton(getString(R.string.dialog_cancel_button), null)
        )
    }

    private fun saveTrack() {
        val name = etTrackName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, getString(R.string.custom_track_toast_enter_track_name), Toast.LENGTH_SHORT).show()
            return
        }

        if (!validateTrack()) return

        val trackId = editingTrackId ?: CustomTrackStorage.generateTrackId()
        val createdAt = if (editingCreatedAt > 0L) editingCreatedAt else System.currentTimeMillis()
        val mode = if (trackType == CustomTrack.TrackType.CIRCUIT) {
            CustomTrackMode.CIRCUIT
        } else {
            CustomTrackMode.POINT_TO_POINT
        }

        val startGate = if (trackType == CustomTrack.TrackType.CIRCUIT) {
            lineFromPoints(circuitGatePoints)
        } else {
            lineFromPoints(startGatePoints)
        }
        val finishGate = if (trackType == CustomTrack.TrackType.POINT_TO_POINT) {
            lineFromPoints(finishGatePoints)
        } else {
            null
        }

        val orderedCheckpoints = currentCheckpointPointsInTrackOrder()
        val sectorGates = orderedCheckpoints.map { cp ->
            val geo = GeoPoint(cp.latitude(), cp.longitude())
            GateLine(start = geo, end = geo)
        }

        val referencePath = buildReferencePathForSave(orderedCheckpoints)
        val measuredDistance = resolveMeasuredDistanceForSave()

        val trackV2 = CustomTrackDefinitionV2(
            id = trackId,
            name = name,
            mode = mode,
            creationMode = when (creationMode) {
                CreationMode.PHONE -> CustomTrackCreationMode.PHONE
                CreationMode.DRIVING -> CustomTrackCreationMode.DRIVING
            },
            createdAt = createdAt,
            startGate = startGate,
            finishGate = finishGate,
            sectorGates = sectorGates,
            referencePath = referencePath,
            measuredDistanceMeters = measuredDistance
        )

        CustomTrackStorage.saveCustomTrackV2(this, trackV2)
        val toastMessage = if (editingTrackId != null) {
            getString(R.string.custom_track_updated, name)
        } else {
            getString(R.string.custom_track_saved, name)
        }
        Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show()

        startActivity(Intent(this, TrackSelectionActivity::class.java).apply {
            putExtra(TrackSelectionActivity.EXTRA_SELECT_CUSTOM_TAB, true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }

    private fun buildReferencePathForSave(orderedCheckpoints: List<Point>): List<GeoPoint> {
        val controlPoints = when (creationMode) {
            CreationMode.PHONE -> buildPhoneRouteControlPoints()
            CreationMode.DRIVING -> drivingRoutePoints
        }
        if (controlPoints.size >= 2) {
            return controlPoints.map { point -> GeoPoint(point.latitude(), point.longitude()) }
        }
        return orderedCheckpoints.map { cp -> GeoPoint(cp.latitude(), cp.longitude()) }
    }

    private fun resolveMeasuredDistanceForSave(): Float? {
        return when (creationMode) {
            CreationMode.DRIVING -> drivingRecordedDistanceMeters.takeIf { it > 50f }
            CreationMode.PHONE -> null
        }
    }

    private fun currentDisplayRouteDistanceMeters(): Float? {
        return when (creationMode) {
            CreationMode.DRIVING -> drivingRecordedDistanceMeters.takeIf { it > 50f }
            CreationMode.PHONE -> {
                val controlPoints = buildPhoneRouteControlPoints()
                if (controlPoints.size >= 2) {
                    calculatePointPathDistanceMeters(controlPoints)
                } else {
                    null
                }
            }
        }
    }

    private fun isEditingTrackCalibrated(): Boolean {
        if (editingTrackId == null) return false
        val trackV2 = CustomTrackStorage.loadCustomTrackV2(this, editingTrackId!!) ?: return false
        return (trackV2.measuredDistanceMeters ?: 0f) > 100f
    }

    private fun validateTrack(): Boolean {
        return when (trackType) {
            CustomTrack.TrackType.CIRCUIT -> {
                when {
                    circuitGatePoints.size != 2 -> {
                        Toast.makeText(this, getString(R.string.custom_track_toast_add_start_finish_points), Toast.LENGTH_SHORT).show()
                        false
                    }
                    checkpointPoints.size != CIRCUIT_CHECKPOINT_COUNT -> {
                        Toast.makeText(this, getString(R.string.custom_track_toast_add_four_checkpoints), Toast.LENGTH_SHORT).show()
                        false
                    }
                    else -> true
                }
            }
            CustomTrack.TrackType.POINT_TO_POINT -> {
                when {
                    startGatePoints.size != 2 -> {
                        Toast.makeText(this, getString(R.string.custom_track_toast_add_start_points), Toast.LENGTH_SHORT).show()
                        false
                    }
                    finishGatePoints.size != 2 -> {
                        Toast.makeText(this, getString(R.string.custom_track_toast_add_finish_points), Toast.LENGTH_SHORT).show()
                        false
                    }
                    checkpointPoints.size < POINT_TO_POINT_MIN_CHECKPOINTS -> {
                        Toast.makeText(this, getString(R.string.custom_track_toast_add_min_checkpoints), Toast.LENGTH_SHORT).show()
                        false
                    }
                    else -> true
                }
            }
        }
    }

    private fun updateBuilderState() {
        val hasName = etTrackName.text.toString().trim().isNotEmpty()
        val canSave = when (trackType) {
            CustomTrack.TrackType.CIRCUIT -> hasName && circuitGatePoints.size == 2 && checkpointPoints.size == CIRCUIT_CHECKPOINT_COUNT
            CustomTrack.TrackType.POINT_TO_POINT -> hasName && startGatePoints.size == 2 && finishGatePoints.size == 2 && checkpointPoints.size >= POINT_TO_POINT_MIN_CHECKPOINTS
        }
        btnSave.isEnabled = canSave

        val hasSelection = selectedPoint != null
        btnStopDrawing.text = if (hasSelection) {
            getString(R.string.custom_track_button_delete)
        } else {
            getString(R.string.custom_track_button_undo)
        }
        btnStopDrawing.isEnabled = if (hasSelection) true else undoStack.isNotEmpty()

        val baseStatus = when (trackType) {
            CustomTrack.TrackType.CIRCUIT -> {
                when {
                    selectedPoint != null -> getString(R.string.custom_track_status_selected_point)
                    !hasName -> getString(R.string.custom_track_status_step_name)
                    creationMode == CreationMode.DRIVING && circuitGatePoints.size < 2 -> getString(R.string.custom_track_status_step_gps_start_finish)
                    circuitGatePoints.size < 2 -> getString(R.string.custom_track_status_step_start_finish_line)
                    checkpointPoints.size < CIRCUIT_CHECKPOINT_COUNT -> getString(R.string.custom_track_status_step_checkpoints_count, checkpointPoints.size)
                    else -> getString(R.string.custom_track_status_ready_circuit)
                }
            }
            CustomTrack.TrackType.POINT_TO_POINT -> {
                when {
                    selectedPoint != null -> getString(R.string.custom_track_status_selected_point)
                    !hasName -> getString(R.string.custom_track_status_step_name_p2p)
                    creationMode == CreationMode.DRIVING && startGatePoints.size < 2 -> getString(R.string.custom_track_status_step_gps_start)
                    creationMode == CreationMode.DRIVING && finishGatePoints.size < 2 -> getString(R.string.custom_track_status_step_gps_finish)
                    startGatePoints.size < 2 -> getString(R.string.custom_track_status_step_start_line_count, startGatePoints.size)
                    finishGatePoints.size < 2 -> getString(R.string.custom_track_status_step_finish_line_count, finishGatePoints.size)
                    checkpointPoints.size < POINT_TO_POINT_MIN_CHECKPOINTS -> getString(R.string.custom_track_status_step_checkpoints_p2p)
                    else -> getString(R.string.custom_track_status_ready_p2p)
                }
            }
        }

        val routeDistanceStatus = currentDisplayRouteDistanceMeters()?.let { distance ->
            if (distance > 50f) {
                val distanceLabel = formatDistanceLabel(distance)
                if (creationMode == CreationMode.PHONE && !isEditingTrackCalibrated()) {
                    getString(R.string.custom_track_route_distance_estimated, distanceLabel)
                } else {
                    getString(R.string.custom_track_route_distance, distanceLabel)
                }
            } else {
                null
            }
        }.orEmpty()

        val calibrationStatus = when {
            creationMode == CreationMode.DRIVING || isEditingTrackCalibrated() ->
                getString(R.string.custom_track_calibration_status_calibrated)
            canSave ->
                getString(R.string.custom_track_calibration_status_estimated)
            else -> ""
        }
        tvStatus.text = baseStatus + routeDistanceStatus +
            if (calibrationStatus.isNotEmpty()) "\n$calibrationStatus" else ""

        btnAddStartFinish.alpha = if (activeTool == BuilderTool.SET_CIRCUIT_GATE) 1f else 0.75f
        btnAddStart.alpha = if (activeTool == BuilderTool.SET_START) 1f else 0.75f
        btnAddFinish.alpha = if (activeTool == BuilderTool.SET_FINISH) 1f else 0.75f
        val checkpointActive = activeTool == BuilderTool.SET_CHECKPOINT
        btnStartDrawing.alpha = if (creationMode == CreationMode.DRIVING || checkpointActive) 1f else 0.75f
        btnAddSnapHelper.alpha = if (creationMode == CreationMode.DRIVING || checkpointActive) 1f else 0.75f

        if (creationMode == CreationMode.DRIVING) {
            btnStartDrawing.text = if (isDrivingCaptureActive) {
                getString(R.string.custom_track_button_stop_gps)
            } else {
                getString(R.string.custom_track_button_start_gps)
            }
            btnAddSnapHelper.text = getString(R.string.custom_track_button_auto_cp)
        }
    }

    private fun toggleDrivingCapture() {
        if (creationMode != CreationMode.DRIVING) {
            activeTool = BuilderTool.SET_CHECKPOINT
            updateBuilderState()
            Toast.makeText(this, getString(R.string.custom_track_step_checkpoints), Toast.LENGTH_SHORT).show()
            return
        }

        if (!checkLocationPermission()) {
            requestLocationPermission()
            return
        }

        if (isDrivingCaptureActive) {
            stopDrivingCapture(showToast = true)
        } else {
            startDrivingCapture()
        }
    }

    private fun startDrivingCapture() {
        if (trackType == CustomTrack.TrackType.CIRCUIT) {
            circuitGatePoints.clear()
        } else {
            startGatePoints.clear()
            finishGatePoints.clear()
        }
        checkpointPoints.clear()
        drivingRoutePoints.clear()
        lastDrivingRoutePoint = null
        lastDrivingSampleLocation = null
        drivingRecordedDistanceMeters = 0f
        drivingCaptureStartedAtMs = System.currentTimeMillis()
        hasMovedAwayFromStartLine = false

        isDrivingCaptureActive = true

        currentLiveLocation?.let {
            ensureAutoStartGateFromLocation(it)
            appendDrivingRoutePoint(it)
        } ?: fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (!isDrivingCaptureActive || location == null) return@addOnSuccessListener
            ensureAutoStartGateFromLocation(location)
            appendDrivingRoutePoint(location)
        }

        Toast.makeText(this, getString(R.string.custom_track_toast_gps_started), Toast.LENGTH_SHORT).show()
        redrawAnnotations()
        updateBuilderState()
    }

    private fun stopDrivingCapture(showToast: Boolean) {
        isDrivingCaptureActive = false

        if (creationMode == CreationMode.DRIVING) {
            applyAutoFinishGateFromRecordedRoute()
            autoGenerateCheckpointsFromDrivingRoute(showToast = false)
            redrawAnnotations()
        }

        if (showToast) {
            Toast.makeText(this, getString(R.string.custom_track_toast_gps_stopped), Toast.LENGTH_SHORT).show()
        }
        updateBuilderState()
    }

    private fun appendDrivingRoutePoint(location: Location) {
        val previousSample = lastDrivingSampleLocation
        lastDrivingSampleLocation = Location(location)

        if (creationMode == CreationMode.DRIVING && isDrivingCaptureActive) {
            ensureAutoStartGateFromLocation(location)
            if (trackType == CustomTrack.TrackType.CIRCUIT && shouldAutoStopCircuitCapture(previousSample, location)) {
                stopDrivingCapture(showToast = false)
                Toast.makeText(this, getString(R.string.custom_track_toast_gps_auto_stop), Toast.LENGTH_LONG).show()
                return
            }
        }

        val point = Point.fromLngLat(location.longitude, location.latitude)
        val previous = lastDrivingRoutePoint
        if (previous != null) {
            val distance = distanceMeters(previous, point)
            if (distance < MIN_ROUTE_POINT_DISTANCE_METERS) return
            drivingRecordedDistanceMeters += distance.toFloat()
        }
        drivingRoutePoints.add(point)
        lastDrivingRoutePoint = point
        redrawPolylines()
    }

    private fun shouldAutoStopCircuitCapture(previous: Location?, current: Location): Boolean {
        if (previous == null || circuitGatePoints.size != 2) return false
        if (drivingCaptureStartedAtMs <= 0L) return false

        val gateStart = circuitGatePoints[0]
        val gateEnd = circuitGatePoints[1]

        val distanceToLine = trackGateCrossingEngine.distanceToLineMeters(
            pointLat = current.latitude,
            pointLon = current.longitude,
            lineStartLat = gateStart.latitude(),
            lineStartLon = gateStart.longitude(),
            lineEndLat = gateEnd.latitude(),
            lineEndLon = gateEnd.longitude()
        )
        if (distanceToLine >= AUTO_STOP_MIN_AWAY_FROM_LINE_METERS) {
            hasMovedAwayFromStartLine = true
        }

        if (!hasMovedAwayFromStartLine) return false
        if (drivingRecordedDistanceMeters < AUTO_STOP_MIN_DISTANCE_METERS) return false
        if (System.currentTimeMillis() - drivingCaptureStartedAtMs < AUTO_STOP_MIN_ELAPSED_MS) return false

        return trackGateCrossingEngine.didCrossLine(
            previousLat = previous.latitude,
            previousLon = previous.longitude,
            currentLat = current.latitude,
            currentLon = current.longitude,
            lineStartLat = gateStart.latitude(),
            lineStartLon = gateStart.longitude(),
            lineEndLat = gateEnd.latitude(),
            lineEndLon = gateEnd.longitude()
        )
    }

    private fun autoGenerateCheckpointsFromDrivingRoute(showToast: Boolean = true) {
        if (creationMode != CreationMode.DRIVING) return

        if (drivingRoutePoints.size < 5) {
            if (showToast) {
                Toast.makeText(this, getString(R.string.custom_track_toast_not_enough_gps), Toast.LENGTH_SHORT).show()
            }
            return
        }

        when (trackType) {
            CustomTrack.TrackType.CIRCUIT -> {
                if (circuitGatePoints.size != 2) {
                    if (showToast) {
                        Toast.makeText(this, getString(R.string.custom_track_toast_auto_start_finish_on_gps), Toast.LENGTH_SHORT).show()
                    }
                    return
                }
            }
            CustomTrack.TrackType.POINT_TO_POINT -> {
                if (startGatePoints.size != 2 || finishGatePoints.size != 2) {
                    if (showToast) {
                        Toast.makeText(this, getString(R.string.custom_track_toast_auto_lines_from_gps), Toast.LENGTH_SHORT).show()
                    }
                    return
                }
            }
        }

        val fractions = listOf(0.2, 0.4, 0.6, 0.8)
        val generated = samplePointsAlongRoute(drivingRoutePoints, fractions)
        if (generated.isEmpty()) {
            Toast.makeText(this, getString(R.string.custom_track_toast_checkpoint_gen_failed), Toast.LENGTH_SHORT).show()
            return
        }

        pushUndoSnapshot()
        checkpointPoints.clear()
        checkpointPoints.addAll(generated)
        redrawAnnotations()
        updateBuilderState()
        if (showToast) {
            Toast.makeText(this, getString(R.string.custom_track_toast_checkpoints_added, generated.size), Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureAutoStartGateFromLocation(location: Location) {
        val center = Point.fromLngLat(location.longitude, location.latitude)
        val heading = if (location.hasBearing()) location.bearing.toDouble() else null

        if (trackType == CustomTrack.TrackType.CIRCUIT) {
            if (circuitGatePoints.size != 2) {
                circuitGatePoints.clear()
                circuitGatePoints.addAll(buildGateAroundCenter(center, heading))
                redrawAnnotations()
            }
            return
        }

        if (startGatePoints.size != 2) {
            startGatePoints.clear()
            startGatePoints.addAll(buildGateAroundCenter(center, heading))
            redrawAnnotations()
        }
    }

    private fun applyAutoFinishGateFromRecordedRoute() {
        if (trackType != CustomTrack.TrackType.POINT_TO_POINT) return
        val center = drivingRoutePoints.lastOrNull() ?: return
        val heading = estimateHeadingFromRouteTail()
        finishGatePoints.clear()
        finishGatePoints.addAll(buildGateAroundCenter(center, heading))
    }

    private fun estimateHeadingFromRouteTail(): Double? {
        if (drivingRoutePoints.size < 2) return null
        val prev = drivingRoutePoints[drivingRoutePoints.lastIndex - 1]
        val curr = drivingRoutePoints.last()

        val prevLocation = Location("prev").apply {
            latitude = prev.latitude()
            longitude = prev.longitude()
        }
        val currLocation = Location("curr").apply {
            latitude = curr.latitude()
            longitude = curr.longitude()
        }
        return prevLocation.bearingTo(currLocation).toDouble()
    }

    private fun buildGateAroundCenter(center: Point, travelBearingDegrees: Double?): List<Point> {
        val lineBearing = ((travelBearingDegrees ?: 0.0) + 90.0) % 360.0
        val half = DEFAULT_GATE_WIDTH_METERS / 2.0
        val first = offsetPointByBearing(center, lineBearing, half)
        val second = offsetPointByBearing(center, (lineBearing + 180.0) % 360.0, half)
        return listOf(first, second)
    }

    private fun offsetPointByBearing(center: Point, bearingDegrees: Double, distanceMeters: Double): Point {
        val lat = center.latitude()
        val lon = center.longitude()
        val bearingRad = Math.toRadians(bearingDegrees)

        val dNorth = cos(bearingRad) * distanceMeters
        val dEast = sin(bearingRad) * distanceMeters

        val dLat = dNorth / 111_320.0
        val dLon = dEast / (111_320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.0001))

        return Point.fromLngLat(lon + dLon, lat + dLat)
    }

    private fun samplePointsAlongRoute(route: List<Point>, fractions: List<Double>): List<Point> {
        if (route.size < 2) return emptyList()

        val cumulative = DoubleArray(route.size)
        var total = 0.0
        for (i in 1 until route.size) {
            total += distanceMeters(route[i - 1], route[i])
            cumulative[i] = total
        }
        if (total <= 0.0) return emptyList()

        val result = mutableListOf<Point>()
        for (fraction in fractions) {
            val target = total * fraction.coerceIn(0.0, 1.0)
            var index = 1
            while (index < cumulative.size && cumulative[index] < target) {
                index++
            }

            if (index >= route.size) {
                result.add(route.last())
                continue
            }

            val prevDistance = cumulative[index - 1]
            val segmentDistance = (cumulative[index] - prevDistance).coerceAtLeast(0.000001)
            val t = ((target - prevDistance) / segmentDistance).coerceIn(0.0, 1.0)

            val a = route[index - 1]
            val b = route[index]
            val lon = a.longitude() + (b.longitude() - a.longitude()) * t
            val lat = a.latitude() + (b.latitude() - a.latitude()) * t
            result.add(Point.fromLngLat(lon, lat))
        }

        return result
    }

    private fun distanceMeters(a: Point, b: Point): Double {
        val locationA = Location("routeA").apply {
            latitude = a.latitude()
            longitude = a.longitude()
        }
        val locationB = Location("routeB").apply {
            latitude = b.latitude()
            longitude = b.longitude()
        }
        return locationA.distanceTo(locationB).toDouble()
    }

    private fun lineFromPoints(points: List<Point>): GateLine? {
        if (points.size < 2) return null
        return GateLine(
            start = GeoPoint(points[0].latitude(), points[0].longitude()),
            end = GeoPoint(points[1].latitude(), points[1].longitude())
        )
    }

    private fun loadTrackForEditing(trackId: String) {
        val trackV2 = CustomTrackStorage.loadCustomTrackV2(this, trackId)
        val track = CustomTrackStorage.loadCustomTrack(this, trackId)
        if (trackV2 == null && track == null) {
            Toast.makeText(this, getString(R.string.custom_track_toast_load_failed), Toast.LENGTH_SHORT).show()
            return
        }

        val resolvedId = trackV2?.id ?: track!!.id
        val resolvedName = trackV2?.name ?: track!!.name
        val resolvedCreatedAt = trackV2?.createdAt ?: track!!.createdAt

        editingTrackId = resolvedId
        editingCreatedAt = resolvedCreatedAt
        etTrackName.setText(resolvedName)

        if (trackV2 != null) {
            trackType = when (trackV2.mode as CustomTrackMode?) {
                CustomTrackMode.POINT_TO_POINT -> CustomTrack.TrackType.POINT_TO_POINT
                else -> CustomTrack.TrackType.CIRCUIT
            }
            creationMode = when (trackV2.creationMode as CustomTrackCreationMode?) {
                CustomTrackCreationMode.DRIVING -> CreationMode.DRIVING
                CustomTrackCreationMode.PHONE -> CreationMode.PHONE
                null -> if ((trackV2.measuredDistanceMeters ?: 0f) > 50f) CreationMode.DRIVING else CreationMode.PHONE
            }
        } else {
            trackType = (track?.type as CustomTrack.TrackType?) ?: CustomTrack.TrackType.CIRCUIT
        }

        circuitGatePoints.clear()
        checkpointPoints.clear()
        startGatePoints.clear()
        finishGatePoints.clear()
        drivingRoutePoints.clear()
        clearPhoneRoutePreview()
        lastDrivingRoutePoint = null
        lastDrivingSampleLocation = null
        drivingRecordedDistanceMeters = 0f
        drivingCaptureStartedAtMs = 0L
        hasMovedAwayFromStartLine = false
        selectedPoint = null
        undoStack.clear()
        dragSnapshotCaptured = false

        val gatePoints = if (trackV2?.startGate != null && trackType == CustomTrack.TrackType.CIRCUIT) {
            listOf(
                Point.fromLngLat(trackV2.startGate.start.longitude, trackV2.startGate.start.latitude),
                Point.fromLngLat(trackV2.startGate.end.longitude, trackV2.startGate.end.latitude)
            )
        } else {
            track?.points
                ?.filter { it.pointType == CustomTrack.TrackPoint.PointType.START_FINISH }
                ?.map { Point.fromLngLat(it.geoPoint.longitude, it.geoPoint.latitude) }
                ?: emptyList()
        }

        val checkpointList = if (trackV2 != null) {
            trackV2.sectorGates.map { gate ->
                Point.fromLngLat(gate.start.longitude, gate.start.latitude)
            }
        } else {
            track?.points
                ?.filter { it.pointType == CustomTrack.TrackPoint.PointType.SNAP_HELPER }
                ?.map { Point.fromLngLat(it.geoPoint.longitude, it.geoPoint.latitude) }
                ?: emptyList()
        }

        val routePathList = if (trackV2 != null) {
            trackV2.referencePath.map { Point.fromLngLat(it.longitude, it.latitude) }
        } else {
            emptyList()
        }

        val startPoints = if (trackV2?.startGate != null && trackType == CustomTrack.TrackType.POINT_TO_POINT) {
            listOf(
                Point.fromLngLat(trackV2.startGate.start.longitude, trackV2.startGate.start.latitude),
                Point.fromLngLat(trackV2.startGate.end.longitude, trackV2.startGate.end.latitude)
            )
        } else {
            track?.points
                ?.filter { it.pointType == CustomTrack.TrackPoint.PointType.START }
                ?.map { Point.fromLngLat(it.geoPoint.longitude, it.geoPoint.latitude) }
                ?: emptyList()
        }

        val finishPoints = if (trackV2?.finishGate != null && trackType == CustomTrack.TrackType.POINT_TO_POINT) {
            listOf(
                Point.fromLngLat(trackV2.finishGate.start.longitude, trackV2.finishGate.start.latitude),
                Point.fromLngLat(trackV2.finishGate.end.longitude, trackV2.finishGate.end.latitude)
            )
        } else {
            track?.points
                ?.filter { it.pointType == CustomTrack.TrackPoint.PointType.FINISH }
                ?.map { Point.fromLngLat(it.geoPoint.longitude, it.geoPoint.latitude) }
                ?: emptyList()
        }

        if (creationMode == CreationMode.DRIVING && trackV2 != null) {
            drivingRoutePoints.addAll(routePathList)
            lastDrivingRoutePoint = drivingRoutePoints.lastOrNull()
            drivingRecordedDistanceMeters = trackV2.measuredDistanceMeters ?: run {
                var total = 0f
                for (index in 1 until drivingRoutePoints.size) {
                    total += distanceMeters(drivingRoutePoints[index - 1], drivingRoutePoints[index]).toFloat()
                }
                total
            }
        } else if (creationMode == CreationMode.PHONE && routePathList.size >= 2) {
            setPhoneRoutePreview(
                routePathList,
                trackV2?.measuredDistanceMeters ?: calculatePointPathDistanceMeters(routePathList)
            )
        }

        when (trackType) {
            CustomTrack.TrackType.CIRCUIT -> {
                circuitGatePoints.addAll(gatePoints.take(2))
                checkpointPoints.addAll(checkpointList.take(CIRCUIT_CHECKPOINT_COUNT))
                activeTool = if (circuitGatePoints.size < 2) BuilderTool.SET_CIRCUIT_GATE else BuilderTool.SET_CHECKPOINT
            }
            CustomTrack.TrackType.POINT_TO_POINT -> {
                startGatePoints.addAll(startPoints.take(2))
                finishGatePoints.addAll(finishPoints.take(2))
                checkpointPoints.addAll(checkpointList)
                activeTool = when {
                    startGatePoints.size < 2 -> BuilderTool.SET_START
                    finishGatePoints.size < 2 -> BuilderTool.SET_FINISH
                    else -> BuilderTool.SET_CHECKPOINT
                }
            }
        }

        normalizeCircuitCheckpointOrderIfNeeded()
        updateUIForTrackType(resetTool = false)
        redrawAnnotations()
        fitCameraToTrackPoints()
        mapView.post {
            fitCameraToTrackPoints()
        }
        Handler(Looper.getMainLooper()).postDelayed({
            fitCameraToTrackPoints()
        }, 250L)
        updateBuilderState()
        if (creationMode == CreationMode.PHONE && phoneRoutePreviewPoints.size < 2) {
            schedulePhoneRoutePreviewRefresh(immediate = true)
        }
        Toast.makeText(this, getString(R.string.custom_track_toast_edit_mode, resolvedName), Toast.LENGTH_SHORT).show()
    }

    private fun fitCameraToTrackPoints() {
        val allPoints = mutableListOf<Point>()
        allPoints.addAll(circuitGatePoints)
        allPoints.addAll(checkpointPoints)
        allPoints.addAll(startGatePoints)
        allPoints.addAll(finishGatePoints)

        if (allPoints.isEmpty()) return

        if (allPoints.size == 1) {
            mapView.mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(allPoints.first())
                    .zoom(17.0)
                    .build()
            )
            return
        }

        val density = resources.displayMetrics.density
        val cameraOptions = try {
            mapView.mapboxMap.cameraForCoordinates(
                allPoints,
                CameraOptions.Builder().build(),
                EdgeInsets(80.0 * density, 80.0 * density, 80.0 * density, 80.0 * density),
                null,
                null
            )
        } catch (e: Exception) {
            Log.w(TAG, "cameraForCoordinates failed in edit mode: ${e.message}")
            CameraOptions.Builder()
                .center(allPoints.first())
                .zoom(15.0)
                .build()
        }

        mapView.mapboxMap.setCamera(cameraOptions)
    }

    private fun initializeGPS() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (checkLocationPermission()) {
            startLocationCameraFollow()
            getCurrentLocation()
        } else {
            requestLocationPermission()
        }
    }

    private fun startLocationCameraFollow() {
        if (followLocationCallback != null || !checkLocationPermission()) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateDistanceMeters(2f)
            .build()

        followLocationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    currentLiveLocation = location
                    updateLiveLocationAnnotation(location)
                    centerCameraOnLocationOnce(location)
                    if (isDrivingCaptureActive) {
                        appendDrivingRoutePoint(location)
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(request, followLocationCallback!!, Looper.getMainLooper())
    }

    private fun stopLocationCameraFollow() {
        followLocationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
        }
        followLocationCallback = null
    }

    private fun centerCameraOnLocationOnce(location: Location) {
        if (hasCenteredCameraOnInitialLocation || isEditMode || !::mapView.isInitialized) return

        hasCenteredCameraOnInitialLocation = true
        val currentZoom = mapView.mapboxMap.cameraState.zoom
        val targetZoom = if (currentZoom.isFinite() && currentZoom >= 15.5) currentZoom else 16.5
        mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(location.longitude, location.latitude))
                .zoom(targetZoom)
                .build()
        )
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
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
                    if (isEditMode) {
                        return@addOnSuccessListener
                    }
                    location?.let {
                        currentLiveLocation = it
                        updateLiveLocationAnnotation(it)
                        centerCameraOnLocationOnce(it)
                    }
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Location error: ${error.message}")
                }
        } catch (ex: SecurityException) {
            Log.e(TAG, "Location permission error: ${ex.message}")
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
                startLocationCameraFollow()
                getCurrentLocation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkLocationPermission()) {
            startLocationCameraFollow()
        }
    }

    override fun onPause() {
        stopLocationCameraFollow()
        super.onPause()
    }

    override fun onDestroy() {
        stopDrivingCapture(showToast = false)
        stopLocationCameraFollow()
        routePreviewHandler.removeCallbacksAndMessages(null)
        pointManager.deleteAll()
        liveLocationPointManager.deleteAll()
        polylineManager.deleteAll()
        mapContainer.removeAllViews()
        restoreDefaultMapboxAccessToken()
        super.onDestroy()
    }
}
