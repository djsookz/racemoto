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
import com.mapbox.maps.MapView
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.plugin.annotation.annotations
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
    private var activeTool: BuilderTool = BuilderTool.DRAW_PATH

    private val pathPoints = mutableListOf<Point>()
    private val circuitGatePoints = mutableListOf<Point>()
    private var startPoint: Point? = null
    private var finishPoint: Point? = null

    companion object {
        private const val TAG = "CustomTrackBuilder"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val STYLE_URI = "mapbox://styles/djsookz/cmiyer8iu000101s84wqsav5l"
    }

    private enum class BuilderTool {
        DRAW_PATH,
        SET_CIRCUIT_GATE,
        SET_START,
        SET_FINISH
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_track_builder)

        trackType = CustomTrack.TrackType.valueOf(intent.getStringExtra("track_type") ?: "CIRCUIT")

        initViews()
        setupMap()
        setupButtons()
        updateUIForTrackType()
        initializeGPS()

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

        mapView.mapboxMap.addOnMapClickListener { point ->
            handleMapClick(point)
            true
        }
    }

    private fun setupButtons() {
        btnStartDrawing.setOnClickListener {
            activeTool = BuilderTool.DRAW_PATH
            updateBuilderState()
            Toast.makeText(this, "Режим: рисуване на трасе", Toast.LENGTH_SHORT).show()
        }

        btnStopDrawing.setOnClickListener {
            undoLastPathPoint()
        }

        btnClear.setOnClickListener {
            clearTrack()
        }

        btnSave.setOnClickListener {
            saveTrack()
        }

        btnAddStartFinish.setOnClickListener {
            activeTool = BuilderTool.SET_CIRCUIT_GATE
            updateBuilderState()
            Toast.makeText(this, "Поставете 2 точки за старт/финиш линия", Toast.LENGTH_SHORT).show()
        }

        btnAddStart.setOnClickListener {
            activeTool = BuilderTool.SET_START
            updateBuilderState()
            Toast.makeText(this, "Поставете старт точка", Toast.LENGTH_SHORT).show()
        }

        btnAddFinish.setOnClickListener {
            activeTool = BuilderTool.SET_FINISH
            updateBuilderState()
            Toast.makeText(this, "Поставете финиш точка", Toast.LENGTH_SHORT).show()
        }

        btnAddSnapHelper.setOnClickListener {
            activeTool = BuilderTool.DRAW_PATH
            updateBuilderState()
            Toast.makeText(this, "Режим: добавяне на точки по трасе", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUIForTrackType() {
        when (trackType) {
            CustomTrack.TrackType.CIRCUIT -> {
                btnAddStartFinish.visibility = android.view.View.VISIBLE
                btnAddStart.visibility = android.view.View.GONE
                btnAddFinish.visibility = android.view.View.GONE
                btnAddSnapHelper.visibility = android.view.View.VISIBLE
                tvInstructions.text = "1) Въведете име\n2) Добавете 2 точки за старт/финиш линия\n3) Рисувайте трасе с кликове по картата\n4) Запазете"
            }
            CustomTrack.TrackType.POINT_TO_POINT -> {
                btnAddStartFinish.visibility = android.view.View.GONE
                btnAddStart.visibility = android.view.View.VISIBLE
                btnAddFinish.visibility = android.view.View.VISIBLE
                btnAddSnapHelper.visibility = android.view.View.VISIBLE
                tvInstructions.text = "1) Въведете име\n2) Поставете старт\n3) Поставете финиш\n4) Рисувайте маршрута\n5) Запазете"
            }
        }
    }

    private fun handleMapClick(point: Point) {
        when (activeTool) {
            BuilderTool.DRAW_PATH -> {
                pathPoints.add(point)
            }
            BuilderTool.SET_CIRCUIT_GATE -> {
                if (circuitGatePoints.size < 2) {
                    circuitGatePoints.add(point)
                } else {
                    val replaceIndex = nearestGatePointIndex(point)
                    circuitGatePoints[replaceIndex] = point
                }
            }
            BuilderTool.SET_START -> {
                startPoint = point
            }
            BuilderTool.SET_FINISH -> {
                finishPoint = point
            }
        }

        redrawAnnotations()
        updateBuilderState()
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
        polylineManager.deleteAll()

        if (pathPoints.size >= 2) {
            polylineManager.create(
                PolylineAnnotationOptions()
                    .withPoints(pathPoints)
                    .withLineColor("#FF3B30")
                    .withLineWidth(5.0)
            )
        }

        if (trackType == CustomTrack.TrackType.CIRCUIT && circuitGatePoints.size == 2) {
            polylineManager.create(
                PolylineAnnotationOptions()
                    .withPoints(circuitGatePoints)
                    .withLineColor("#3B82F6")
                    .withLineWidth(6.0)
            )
        }

        if (trackType == CustomTrack.TrackType.CIRCUIT) {
            circuitGatePoints.forEachIndexed { index, p ->
                pointManager.create(
                    PointAnnotationOptions()
                        .withPoint(p)
                        .withIconImage(createMarkerBitmap(if (index == 0) "S/F 1" else "S/F 2", Color.parseColor("#3B82F6")))
                        .withIconAnchor(IconAnchor.BOTTOM)
                )
            }
        } else {
            startPoint?.let {
                pointManager.create(
                    PointAnnotationOptions()
                        .withPoint(it)
                        .withIconImage(createMarkerBitmap("START", Color.parseColor("#22C55E")))
                        .withIconAnchor(IconAnchor.BOTTOM)
                )
            }
            finishPoint?.let {
                pointManager.create(
                    PointAnnotationOptions()
                        .withPoint(it)
                        .withIconImage(createMarkerBitmap("FIN", Color.parseColor("#EF4444")))
                        .withIconAnchor(IconAnchor.BOTTOM)
                )
            }
        }
    }

    private fun createMarkerBitmap(label: String, color: Int): Bitmap {
        val width = 120
        val height = 60
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = 24f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 16f, 16f, bgPaint)
        canvas.drawText(label, width / 2f, height / 2f + 8f, textPaint)

        return bitmap
    }

    private fun undoLastPathPoint() {
        if (pathPoints.isNotEmpty()) {
            pathPoints.removeLast()
            redrawAnnotations()
            updateBuilderState()
        } else {
            Toast.makeText(this, "Няма точки за връщане", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearTrack() {
        AlertDialog.Builder(this)
            .setTitle("Изчистване")
            .setMessage("Да изчистя ли текущата custom писта?")
            .setPositiveButton("Да") { _, _ ->
                pathPoints.clear()
                circuitGatePoints.clear()
                startPoint = null
                finishPoint = null
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

        val trackPoints = mutableListOf<CustomTrack.TrackPoint>()

        pathPoints.forEach { point ->
            trackPoints.add(
                CustomTrack.TrackPoint(
                    geoPoint = GeoPoint(point.latitude(), point.longitude()),
                    pointType = CustomTrack.TrackPoint.PointType.SNAP_HELPER
                )
            )
        }

        if (trackType == CustomTrack.TrackType.CIRCUIT) {
            circuitGatePoints.forEach { point ->
                trackPoints.add(
                    CustomTrack.TrackPoint(
                        geoPoint = GeoPoint(point.latitude(), point.longitude()),
                        pointType = CustomTrack.TrackPoint.PointType.START_FINISH
                    )
                )
            }
        } else {
            startPoint?.let {
                trackPoints.add(
                    CustomTrack.TrackPoint(
                        geoPoint = GeoPoint(it.latitude(), it.longitude()),
                        pointType = CustomTrack.TrackPoint.PointType.START
                    )
                )
            }
            finishPoint?.let {
                trackPoints.add(
                    CustomTrack.TrackPoint(
                        geoPoint = GeoPoint(it.latitude(), it.longitude()),
                        pointType = CustomTrack.TrackPoint.PointType.FINISH
                    )
                )
            }
        }

        val track = CustomTrack(
            id = CustomTrackStorage.generateTrackId(),
            name = name,
            type = trackType,
            points = trackPoints
        )

        CustomTrackStorage.saveCustomTrack(this, track)
        Toast.makeText(this, "Пистата '$name' е запазена", Toast.LENGTH_LONG).show()

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
                    pathPoints.size < 3 -> {
                        Toast.makeText(this, "Добавете поне 3 точки за трасето", Toast.LENGTH_SHORT).show()
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
                    pathPoints.size < 2 -> {
                        Toast.makeText(this, "Добавете поне 2 точки за маршрута", Toast.LENGTH_SHORT).show()
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
            CustomTrack.TrackType.CIRCUIT -> hasName && circuitGatePoints.size == 2 && pathPoints.size >= 3
            CustomTrack.TrackType.POINT_TO_POINT -> hasName && startPoint != null && finishPoint != null && pathPoints.size >= 2
        }
        btnSave.isEnabled = canSave

        val modeText = when (activeTool) {
            BuilderTool.DRAW_PATH -> "рисуване"
            BuilderTool.SET_CIRCUIT_GATE -> "старт/финиш линия"
            BuilderTool.SET_START -> "старт"
            BuilderTool.SET_FINISH -> "финиш"
        }

        tvStatus.text = when (trackType) {
            CustomTrack.TrackType.CIRCUIT -> {
                when {
                    !hasName -> "Стъпка 1: въведете име"
                    circuitGatePoints.size < 2 -> "Стъпка 2: добавете старт/финиш линия (режим: $modeText)"
                    pathPoints.size < 3 -> "Стъпка 3: нарисувайте трасе (режим: $modeText)"
                    else -> "Готово за запазване"
                }
            }
            CustomTrack.TrackType.POINT_TO_POINT -> {
                when {
                    !hasName -> "Стъпка 1: въведете име"
                    startPoint == null -> "Стъпка 2: добавете старт (режим: $modeText)"
                    finishPoint == null -> "Стъпка 3: добавете финиш (режим: $modeText)"
                    pathPoints.size < 2 -> "Стъпка 4: нарисувайте маршрут (режим: $modeText)"
                    else -> "Готово за запазване"
                }
            }
        }

        btnStartDrawing.alpha = if (activeTool == BuilderTool.DRAW_PATH) 1f else 0.7f
        btnAddSnapHelper.alpha = if (activeTool == BuilderTool.DRAW_PATH) 1f else 0.7f
        btnAddStartFinish.alpha = if (activeTool == BuilderTool.SET_CIRCUIT_GATE) 1f else 0.7f
        btnAddStart.alpha = if (activeTool == BuilderTool.SET_START) 1f else 0.7f
        btnAddFinish.alpha = if (activeTool == BuilderTool.SET_FINISH) 1f else 0.7f
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
                    location?.let {
                        mapView.mapboxMap.setCamera(
                            CameraOptions.Builder()
                                .center(Point.fromLngLat(it.longitude, it.latitude))
                                .zoom(16.5)
                                .build()
                        )
                        tvStatus.text = "Локацията е намерена. Започнете създаване на писта."
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
