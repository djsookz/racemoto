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
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import com.example.clinometer.tracking.CustomTrack
import com.example.clinometer.tracking.CustomTrackStorage
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
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

class CustomTrackBuilderActivity : AppCompatActivity() {

    private lateinit var mapContainer: FrameLayout
    private lateinit var mapView: MapView

    private lateinit var btnStartDrawing: Button
    private lateinit var btnStopDrawing: Button
    private lateinit var btnClear: Button
    private lateinit var btnSave: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvInstructions: TextView
    private lateinit var etTrackName: EditText

    private lateinit var btnAddStartFinish: Button
    private lateinit var btnAddStart: Button
    private lateinit var btnAddFinish: Button
    private lateinit var btnAddSnapHelper: Button

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var pointManager: PointAnnotationManager
    private lateinit var polylineManager: PolylineAnnotationManager

    private var trackType: CustomTrack.TrackType = CustomTrack.TrackType.CIRCUIT
    private var activeTool: BuilderTool = BuilderTool.SET_CIRCUIT_GATE
    private var isEditMode: Boolean = false

    private val checkpointPoints = mutableListOf<Point>()
    private val circuitGatePoints = mutableListOf<Point>()
    private var startPoint: Point? = null
    private var finishPoint: Point? = null

    private var editingTrackId: String? = null
    private var editingCreatedAt: Long = 0L

    private val gateAnnotationIndexById = mutableMapOf<String, Int>()
    private val checkpointAnnotationIndexById = mutableMapOf<String, Int>()
    private var startAnnotationId: String? = null
    private var finishAnnotationId: String? = null

    private var selectedPoint: SelectedPoint? = null
    private val undoStack = ArrayDeque<EditorSnapshot>()
    private var dragSnapshotCaptured = false

    companion object {
        private const val TAG = "CustomTrackBuilder"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val STYLE_URI = "mapbox://styles/djsookz/cmiyer8iu000101s84wqsav5l"
        private const val CIRCUIT_CHECKPOINT_COUNT = 4
        private const val POINT_TO_POINT_MIN_CHECKPOINTS = 2
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
        val startPoint: Point?,
        val finishPoint: Point?,
        val selectedPoint: SelectedPoint?,
        val activeTool: BuilderTool
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_track_builder)

        trackType = CustomTrack.TrackType.valueOf(intent.getStringExtra("track_type") ?: "CIRCUIT")

        initViews()
        setupMap()
        setupButtons()
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
        etTrackName = findViewById(R.id.etTrackName)

        btnAddStartFinish = findViewById(R.id.btnAddStartFinish)
        btnAddStart = findViewById(R.id.btnAddStart)
        btnAddFinish = findViewById(R.id.btnAddFinish)
        btnAddSnapHelper = findViewById(R.id.btnAddSnapHelper)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupMap() {
        mapView = MapView(this)
        mapContainer.addView(mapView)

        mapView.mapboxMap.loadStyleUri(STYLE_URI)
        mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(23.5497, 41.0858))
                .zoom(15.0)
                .build()
        )

        pointManager = mapView.annotations.createPointAnnotationManager()
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
                dragSnapshotCaptured = false
                redrawAnnotations()
                updateBuilderState()
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
            annotation.id == startAnnotationId -> SelectedPoint(SelectedPointKind.START)
            annotation.id == finishAnnotationId -> SelectedPoint(SelectedPointKind.FINISH)
            else -> null
        }

        if (selected != null) {
            selectedPoint = selected
            redrawAnnotations()
            updateBuilderState()
            Toast.makeText(this, "Точка избрана: drag за местене, UNDO за изтриване", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleAnnotationDragged(annotation: PointAnnotation) {
        if (trackType == CustomTrack.TrackType.CIRCUIT) {
            val gateIndex = gateAnnotationIndexById[annotation.id]
            if (gateIndex != null && gateIndex in circuitGatePoints.indices) {
                circuitGatePoints[gateIndex] = annotation.point
                selectedPoint = SelectedPoint(SelectedPointKind.GATE, gateIndex)
                redrawPolylines()
                updateBuilderState()
                return
            }

            val checkpointIndex = checkpointAnnotationIndexById[annotation.id]
            if (checkpointIndex != null && checkpointIndex in checkpointPoints.indices) {
                checkpointPoints[checkpointIndex] = annotation.point
                selectedPoint = SelectedPoint(SelectedPointKind.CHECKPOINT, checkpointIndex)
                redrawPolylines()
                updateBuilderState()
            }
            return
        }

        if (annotation.id == startAnnotationId) {
            startPoint = annotation.point
            selectedPoint = SelectedPoint(SelectedPointKind.START)
            redrawPolylines()
            updateBuilderState()
            return
        }

        if (annotation.id == finishAnnotationId) {
            finishPoint = annotation.point
            selectedPoint = SelectedPoint(SelectedPointKind.FINISH)
            redrawPolylines()
            updateBuilderState()
            return
        }

        val checkpointIndex = checkpointAnnotationIndexById[annotation.id]
        if (checkpointIndex != null && checkpointIndex in checkpointPoints.indices) {
            checkpointPoints[checkpointIndex] = annotation.point
            selectedPoint = SelectedPoint(SelectedPointKind.CHECKPOINT, checkpointIndex)
            redrawPolylines()
            updateBuilderState()
        }
    }

    private fun setupButtons() {
        btnAddStartFinish.setOnClickListener {
            activeTool = BuilderTool.SET_CIRCUIT_GATE
            updateBuilderState()
            Toast.makeText(this, "Стъпка: поставете/коригирайте старт-финиш линията", Toast.LENGTH_SHORT).show()
        }

        btnAddStart.setOnClickListener {
            activeTool = BuilderTool.SET_START
            updateBuilderState()
            Toast.makeText(this, "Стъпка: поставете старт точка", Toast.LENGTH_SHORT).show()
        }

        btnAddFinish.setOnClickListener {
            activeTool = BuilderTool.SET_FINISH
            updateBuilderState()
            Toast.makeText(this, "Стъпка: поставете финиш точка", Toast.LENGTH_SHORT).show()
        }

        btnStartDrawing.setOnClickListener {
            activeTool = BuilderTool.SET_CHECKPOINT
            updateBuilderState()
            Toast.makeText(this, "Стъпка: добавяне на checkpoint точки", Toast.LENGTH_SHORT).show()
        }

        btnAddSnapHelper.setOnClickListener {
            activeTool = BuilderTool.SET_CHECKPOINT
            updateBuilderState()
            Toast.makeText(this, "Стъпка: добавяне на checkpoint точки", Toast.LENGTH_SHORT).show()
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
    }

    private fun updateUIForTrackType(resetTool: Boolean) {
        btnStartDrawing.text = "CHECKPOINT"
        btnStopDrawing.text = "UNDO"

        when (trackType) {
            CustomTrack.TrackType.CIRCUIT -> {
                if (resetTool) activeTool = BuilderTool.SET_CIRCUIT_GATE
                btnAddStartFinish.visibility = android.view.View.VISIBLE
                btnAddStart.visibility = android.view.View.GONE
                btnAddFinish.visibility = android.view.View.GONE
                btnAddSnapHelper.visibility = android.view.View.GONE

                tvInstructions.text =
                    "1) Въведете име на писта\n" +
                    "2) Поставете 2 точки за старт/финиш линия\n" +
                    "3) Местете линията с drag за прецизна позиция\n" +
                    "4) Добавете точно 4 checkpoint точки\n" +
                    "5) Tap на точка за избор, drag за местене, UNDO за изтриване\n" +
                    "6) Запазете"
            }

            CustomTrack.TrackType.POINT_TO_POINT -> {
                if (resetTool) activeTool = BuilderTool.SET_START
                btnAddStartFinish.visibility = android.view.View.GONE
                btnAddStart.visibility = android.view.View.VISIBLE
                btnAddFinish.visibility = android.view.View.VISIBLE
                btnAddSnapHelper.visibility = android.view.View.VISIBLE

                tvInstructions.text =
                    "1) Въведете име на трасе\n" +
                    "2) Поставете старт\n" +
                    "3) Поставете финиш\n" +
                    "4) Добавете checkpoint точки\n" +
                    "5) Tap на точка за избор, drag за местене, UNDO за изтриване\n" +
                    "6) Запазете"
            }
        }
    }

    private fun handleMapClick(point: Point) {
        selectedPoint = null

        when (trackType) {
            CustomTrack.TrackType.CIRCUIT -> handleCircuitMapClick(point)
            CustomTrack.TrackType.POINT_TO_POINT -> handlePointToPointMapClick(point)
        }

        redrawAnnotations()
        updateBuilderState()
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
                    Toast.makeText(this, "За обиколка са позволени точно 4 checkpoints", Toast.LENGTH_SHORT).show()
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
                startPoint = point
            }
            BuilderTool.SET_FINISH -> {
                pushUndoSnapshot()
                finishPoint = point
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

    private fun distanceSquared(a: Point, b: Point): Double {
        val dx = a.longitude() - b.longitude()
        val dy = a.latitude() - b.latitude()
        return dx * dx + dy * dy
    }

    private fun redrawAnnotations() {
        pointManager.deleteAll()
        gateAnnotationIndexById.clear()
        checkpointAnnotationIndexById.clear()
        startAnnotationId = null
        finishAnnotationId = null

        clearInvalidSelection()

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
                        .withDraggable(true)
                )
                gateAnnotationIndexById[annotation.id] = index
            }
        } else {
            startPoint?.let {
                val annotation = pointManager.create(
                    PointAnnotationOptions()
                        .withPoint(it)
                        .withIconImage(
                            createMarkerBitmap(
                                "START",
                                markerColor(Color.parseColor("#16A34A"), isSelected(SelectedPointKind.START))
                            )
                        )
                        .withIconAnchor(IconAnchor.BOTTOM)
                        .withDraggable(true)
                )
                startAnnotationId = annotation.id
            }

            finishPoint?.let {
                val annotation = pointManager.create(
                    PointAnnotationOptions()
                        .withPoint(it)
                        .withIconImage(
                            createMarkerBitmap(
                                "FIN",
                                markerColor(Color.parseColor("#DC2626"), isSelected(SelectedPointKind.FINISH))
                            )
                        )
                        .withIconAnchor(IconAnchor.BOTTOM)
                        .withDraggable(true)
                )
                finishAnnotationId = annotation.id
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
                    .withDraggable(true)
            )
            checkpointAnnotationIndexById[annotation.id] = index
        }

        redrawPolylines()
    }

    private fun redrawPolylines() {
        polylineManager.deleteAll()

        if (trackType == CustomTrack.TrackType.CIRCUIT && circuitGatePoints.size == 2) {
            polylineManager.create(
                PolylineAnnotationOptions()
                    .withPoints(circuitGatePoints)
                    .withLineColor("#2563EB")
                    .withLineWidth(6.0)
            )
        }
    }

    private fun createMarkerBitmap(label: String, color: Int): Bitmap {
        val width = 132
        val height = 62
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = 22f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 14f, 14f, bgPaint)
        canvas.drawText(label, width / 2f, height / 2f + 8f, textPaint)

        return bitmap
    }

    private fun clearInvalidSelection() {
        val selected = selectedPoint ?: return
        val isValid = when (selected.kind) {
            SelectedPointKind.GATE -> selected.index in circuitGatePoints.indices
            SelectedPointKind.CHECKPOINT -> selected.index in checkpointPoints.indices
            SelectedPointKind.START -> startPoint != null
            SelectedPointKind.FINISH -> finishPoint != null
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
                if (startPoint == null) return false
                startPoint = null
            }
            SelectedPointKind.FINISH -> {
                if (finishPoint == null) return false
                finishPoint = null
            }
        }

        selectedPoint = null
        redrawAnnotations()
        updateBuilderState()
        Toast.makeText(this, "Избраната точка е изтрита", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun undoLastPoint() {
        if (deleteSelectedPointIfAny()) return

        when (trackType) {
            CustomTrack.TrackType.CIRCUIT -> {
                when {
                    checkpointPoints.isNotEmpty() -> checkpointPoints.removeLast()
                    circuitGatePoints.isNotEmpty() -> circuitGatePoints.removeLast()
                    else -> {
                        Toast.makeText(this, "Няма точки за връщане", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
            }
            CustomTrack.TrackType.POINT_TO_POINT -> {
                when {
                    checkpointPoints.isNotEmpty() -> checkpointPoints.removeLast()
                    finishPoint != null -> finishPoint = null
                    startPoint != null -> startPoint = null
                    else -> {
                        Toast.makeText(this, "Няма точки за връщане", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
            }
        }

        selectedPoint = null
        redrawAnnotations()
        updateBuilderState()
    }

    private fun clearTrack() {
        AlertDialog.Builder(this)
            .setTitle("Изчистване")
            .setMessage("Да изчистя ли текущата custom писта?")
            .setPositiveButton("Да") { _, _ ->
                pushUndoSnapshot()
                checkpointPoints.clear()
                circuitGatePoints.clear()
                startPoint = null
                finishPoint = null
                selectedPoint = null
                activeTool = if (trackType == CustomTrack.TrackType.CIRCUIT) {
                    BuilderTool.SET_CIRCUIT_GATE
                } else {
                    BuilderTool.SET_START
                }
                redrawAnnotations()
                updateBuilderState()
            }
            .setNegativeButton("Отказ", null)
            .show()
    }

    private fun saveTrack() {
        val name = etTrackName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Въведете име на пистата", Toast.LENGTH_SHORT).show()
            return
        }

        if (!validateTrack()) return

        val points = mutableListOf<CustomTrack.TrackPoint>()

        checkpointPoints.forEach { checkpoint ->
            points.add(
                CustomTrack.TrackPoint(
                    geoPoint = GeoPoint(checkpoint.latitude(), checkpoint.longitude()),
                    pointType = CustomTrack.TrackPoint.PointType.SNAP_HELPER
                )
            )
        }

        if (trackType == CustomTrack.TrackType.CIRCUIT) {
            circuitGatePoints.forEach { gatePoint ->
                points.add(
                    CustomTrack.TrackPoint(
                        geoPoint = GeoPoint(gatePoint.latitude(), gatePoint.longitude()),
                        pointType = CustomTrack.TrackPoint.PointType.START_FINISH
                    )
                )
            }
        } else {
            startPoint?.let {
                points.add(
                    CustomTrack.TrackPoint(
                        geoPoint = GeoPoint(it.latitude(), it.longitude()),
                        pointType = CustomTrack.TrackPoint.PointType.START
                    )
                )
            }
            finishPoint?.let {
                points.add(
                    CustomTrack.TrackPoint(
                        geoPoint = GeoPoint(it.latitude(), it.longitude()),
                        pointType = CustomTrack.TrackPoint.PointType.FINISH
                    )
                )
            }
        }

        val track = CustomTrack(
            id = editingTrackId ?: CustomTrackStorage.generateTrackId(),
            name = name,
            type = trackType,
            points = points,
            createdAt = if (editingCreatedAt > 0L) editingCreatedAt else System.currentTimeMillis()
        )

        CustomTrackStorage.saveCustomTrack(this, track)
        val toastMessage = if (editingTrackId != null) {
            "Пистата '$name' е обновена"
        } else {
            "Пистата '$name' е запазена"
        }
        Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show()

        startActivity(Intent(this, TrackSelectionActivity::class.java))
        finish()
    }

    private fun validateTrack(): Boolean {
        return when (trackType) {
            CustomTrack.TrackType.CIRCUIT -> {
                when {
                    circuitGatePoints.size != 2 -> {
                        Toast.makeText(this, "Добавете 2 точки за старт/финиш линия", Toast.LENGTH_SHORT).show()
                        false
                    }
                    checkpointPoints.size != CIRCUIT_CHECKPOINT_COUNT -> {
                        Toast.makeText(this, "Добавете точно 4 checkpoint точки", Toast.LENGTH_SHORT).show()
                        false
                    }
                    else -> true
                }
            }
            CustomTrack.TrackType.POINT_TO_POINT -> {
                when {
                    startPoint == null -> {
                        Toast.makeText(this, "Добавете старт точка", Toast.LENGTH_SHORT).show()
                        false
                    }
                    finishPoint == null -> {
                        Toast.makeText(this, "Добавете финиш точка", Toast.LENGTH_SHORT).show()
                        false
                    }
                    checkpointPoints.size < POINT_TO_POINT_MIN_CHECKPOINTS -> {
                        Toast.makeText(this, "Добавете поне 2 checkpoint точки", Toast.LENGTH_SHORT).show()
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
            CustomTrack.TrackType.POINT_TO_POINT -> hasName && startPoint != null && finishPoint != null && checkpointPoints.size >= POINT_TO_POINT_MIN_CHECKPOINTS
        }
        btnSave.isEnabled = canSave

        tvStatus.text = when (trackType) {
            CustomTrack.TrackType.CIRCUIT -> {
                when {
                    selectedPoint != null -> "✅ Избрана точка: drag за местене, UNDO за изтриване"
                    !hasName -> "Стъпка 1/3: въведете име на писта"
                    circuitGatePoints.size < 2 -> "Стъпка 2/3: поставете 2 точки за старт/финиш линия"
                    checkpointPoints.size < CIRCUIT_CHECKPOINT_COUNT -> "Стъпка 3/3: добавете checkpoints (${checkpointPoints.size}/4)"
                    else -> "✅ Готово: старт/финиш + 4 checkpoints"
                }
            }
            CustomTrack.TrackType.POINT_TO_POINT -> {
                when {
                    selectedPoint != null -> "✅ Избрана точка: drag за местене, UNDO за изтриване"
                    !hasName -> "Стъпка 1/4: въведете име"
                    startPoint == null -> "Стъпка 2/4: поставете старт"
                    finishPoint == null -> "Стъпка 3/4: поставете финиш"
                    checkpointPoints.size < POINT_TO_POINT_MIN_CHECKPOINTS -> "Стъпка 4/4: добавете checkpoints"
                    else -> "✅ Готово за запазване"
                }
            }
        }

        btnAddStartFinish.alpha = if (activeTool == BuilderTool.SET_CIRCUIT_GATE) 1f else 0.75f
        btnAddStart.alpha = if (activeTool == BuilderTool.SET_START) 1f else 0.75f
        btnAddFinish.alpha = if (activeTool == BuilderTool.SET_FINISH) 1f else 0.75f
        val checkpointActive = activeTool == BuilderTool.SET_CHECKPOINT
        btnStartDrawing.alpha = if (checkpointActive) 1f else 0.75f
        btnAddSnapHelper.alpha = if (checkpointActive) 1f else 0.75f
    }

    private fun loadTrackForEditing(trackId: String) {
        val track = CustomTrackStorage.loadCustomTrack(this, trackId)
        if (track == null) {
            Toast.makeText(this, "Неуспешно зареждане на custom писта за редакция", Toast.LENGTH_SHORT).show()
            return
        }

        editingTrackId = track.id
        editingCreatedAt = track.createdAt
        trackType = track.type
        etTrackName.setText(track.name)

        circuitGatePoints.clear()
        checkpointPoints.clear()
        startPoint = null
        finishPoint = null
        selectedPoint = null

        val gatePoints = track.points
            .filter { it.pointType == CustomTrack.TrackPoint.PointType.START_FINISH }
            .map { Point.fromLngLat(it.geoPoint.longitude, it.geoPoint.latitude) }
        val checkpointList = track.points
            .filter { it.pointType == CustomTrack.TrackPoint.PointType.SNAP_HELPER }
            .map { Point.fromLngLat(it.geoPoint.longitude, it.geoPoint.latitude) }
        val start = track.points.firstOrNull { it.pointType == CustomTrack.TrackPoint.PointType.START }?.geoPoint
        val finish = track.points.firstOrNull { it.pointType == CustomTrack.TrackPoint.PointType.FINISH }?.geoPoint

        when (trackType) {
            CustomTrack.TrackType.CIRCUIT -> {
                circuitGatePoints.addAll(gatePoints.take(2))
                checkpointPoints.addAll(checkpointList.take(CIRCUIT_CHECKPOINT_COUNT))
                activeTool = if (circuitGatePoints.size < 2) BuilderTool.SET_CIRCUIT_GATE else BuilderTool.SET_CHECKPOINT
            }
            CustomTrack.TrackType.POINT_TO_POINT -> {
                startPoint = start?.let { Point.fromLngLat(it.longitude, it.latitude) }
                finishPoint = finish?.let { Point.fromLngLat(it.longitude, it.latitude) }
                checkpointPoints.addAll(checkpointList)
                activeTool = when {
                    startPoint == null -> BuilderTool.SET_START
                    finishPoint == null -> BuilderTool.SET_FINISH
                    else -> BuilderTool.SET_CHECKPOINT
                }
            }
        }

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
        Toast.makeText(this, "Режим: редакция на '${track.name}'", Toast.LENGTH_SHORT).show()
    }

    private fun fitCameraToTrackPoints() {
        val allPoints = mutableListOf<Point>()
        allPoints.addAll(circuitGatePoints)
        allPoints.addAll(checkpointPoints)
        startPoint?.let { allPoints.add(it) }
        finishPoint?.let { allPoints.add(it) }

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
            getCurrentLocation()
        } else {
            requestLocationPermission()
        }
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
                        mapView.mapboxMap.setCamera(
                            CameraOptions.Builder()
                                .center(Point.fromLngLat(it.longitude, it.latitude))
                                .zoom(16.5)
                                .build()
                        )
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
                getCurrentLocation()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pointManager.deleteAll()
        polylineManager.deleteAll()
        mapContainer.removeAllViews()
    }
}
