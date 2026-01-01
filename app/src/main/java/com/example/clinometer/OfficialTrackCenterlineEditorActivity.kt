package com.example.clinometer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.clinometer.tracking.OfficialTrackCenterlineStorage
import com.google.android.gms.location.*
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker
import androidx.preference.PreferenceManager
import kotlin.math.sqrt

class OfficialTrackCenterlineEditorActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var mapController: IMapController
    private lateinit var btnAddPoint: Button
    private lateinit var btnClear: Button
    private lateinit var btnSave: Button
    private lateinit var btnDelete: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvPointCount: TextView

    private var centerlinePoints = mutableListOf<GeoPoint>()
    private var centerlinePolyline: Polyline? = null
    private var pointMarkers = mutableListOf<Marker>()
    
    private var trackId: String = ""
    private var trackName: String = ""

    // GPS variables
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    
    // Touch handling for map clicks
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchMoved = false
    private val TOUCH_MOVE_THRESHOLD = 20f // pixels - if moved more than this, it's a drag, not a tap

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_official_track_centerline_editor)

        trackId = intent.getStringExtra("track_id") ?: ""
        trackName = intent.getStringExtra("track_name") ?: ""

        if (trackId.isEmpty()) {
            finish()
            return
        }

        initViews()
        setupMap()
        setupButtons()
        loadExistingPoints()
        initializeGPS()
    }

    private fun initViews() {
        mapView = findViewById(R.id.mapView)
        btnAddPoint = findViewById(R.id.btnAddPoint)
        btnClear = findViewById(R.id.btnClear)
        btnSave = findViewById(R.id.btnSave)
        btnDelete = findViewById(R.id.btnDelete)
        tvStatus = findViewById(R.id.tvStatus)
        tvPointCount = findViewById(R.id.tvPointCount)

        findViewById<TextView>(R.id.tvTrackName).text = trackName
        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            onBackPressed()
        }
    }

    private fun setupMap() {
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapController = mapView.controller

        // Set initial zoom and center (adjust based on track)
        if (trackId == "serres_circuit") {
            mapController.setZoom(16.0)
            mapController.setCenter(GeoPoint(41.073128, 23.517839))
        } else if (trackId == "sofia_ring") {
            mapController.setZoom(16.0)
            mapController.setCenter(GeoPoint(42.6978, 23.3215))
        }

        // Handle map clicks to add points - use touch listener to get exact click position
        mapView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    touchMoved = false
                    false // Let map handle the touch
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - touchStartX
                    val deltaY = event.y - touchStartY
                    val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
                    
                    if (distance > TOUCH_MOVE_THRESHOLD) {
                        touchMoved = true
                    }
                    false // Let map handle the movement
                }
                MotionEvent.ACTION_UP -> {
                    if (!touchMoved) {
                        // Only add point if it was a tap, not a drag/scroll
                        val projection = mapView.projection
                        val iGeoPoint = projection.fromPixels(
                            event.x.toInt(),
                            event.y.toInt()
                        )
                        
                        if (iGeoPoint != null) {
                            val geoPoint = GeoPoint(iGeoPoint.latitude, iGeoPoint.longitude)
                            addPointAtLocation(geoPoint)
                            return@setOnTouchListener true // Consume the event
                        }
                    }
                    false // Let map handle the release
                }
                else -> false
            }
        }
    }

    private fun setupButtons() {
        btnAddPoint.setOnClickListener {
            if (currentLocation != null) {
                addPointAtLocation(GeoPoint(currentLocation!!.latitude, currentLocation!!.longitude))
            } else {
                showToast("Изчакайте GPS локация...")
            }
        }

        btnClear.setOnClickListener {
            showClearConfirmation()
        }

        btnSave.setOnClickListener {
            savePoints()
        }

        btnDelete.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun addPointAtLocation(point: GeoPoint) {
        centerlinePoints.add(point)
        addMarker(point, centerlinePoints.size - 1)
        updatePolyline()
        updateStatus("Добавена точка #${centerlinePoints.size}")
        updatePointCount()
    }

    private fun addMarker(point: GeoPoint, index: Int) {
        val marker = Marker(mapView).apply {
            position = point
            title = "Точка ${index + 1}"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setOnMarkerClickListener { marker, mapView ->
                showPointOptions(index)
                true
            }
        }
        pointMarkers.add(marker)
        mapView.overlays.add(marker)
        mapView.invalidate()
    }

    private fun showPointOptions(index: Int) {
        val options = arrayOf("Премахни точка", "Отмени")
        AlertDialog.Builder(this)
            .setTitle("Точка ${index + 1}")
            .setItems(options) { _, which ->
                if (which == 0) {
                    removePoint(index)
                }
            }
            .show()
    }

    private fun removePoint(index: Int) {
        if (index in centerlinePoints.indices) {
            centerlinePoints.removeAt(index)
            pointMarkers[index].remove(mapView)
            pointMarkers.removeAt(index)
            
            // Recreate all markers with updated indices
            mapView.overlays.removeAll(pointMarkers)
            pointMarkers.clear()
            centerlinePoints.forEachIndexed { i, point ->
                addMarker(point, i)
            }
            
            updatePolyline()
            updateStatus("Премахната точка")
            updatePointCount()
        }
    }

    private fun updatePolyline() {
        if (centerlinePolyline != null) {
            mapView.overlays.remove(centerlinePolyline)
        }

        if (centerlinePoints.size >= 2) {
            centerlinePolyline = Polyline().apply {
                setPoints(centerlinePoints)
                color = Color.parseColor("#FFD700") // Gold color
                outlinePaint.strokeWidth = 8f
            }
            mapView.overlays.add(centerlinePolyline)
            mapView.invalidate()
        }
    }

    private fun updateStatus(message: String) {
        tvStatus.text = message
    }

    private fun updatePointCount() {
        tvPointCount.text = "Точки: ${centerlinePoints.size}"
    }

    private fun loadExistingPoints() {
        val points = OfficialTrackCenterlineStorage.loadCenterlinePoints(this, trackId)
        if (points.isNotEmpty()) {
            centerlinePoints.clear()
            centerlinePoints.addAll(points)
            
            points.forEachIndexed { index, point ->
                addMarker(point, index)
            }
            updatePolyline()
            updatePointCount()
            updateStatus("Заредени ${points.size} точки")
        } else {
            updateStatus("Няма запазени точки. Добавете точки за centerline на пистата.")
            updatePointCount()
        }
    }

    private fun savePoints() {
        if (centerlinePoints.size < 2) {
            showToast("Добавете поне 2 точки!")
            return
        }

        OfficialTrackCenterlineStorage.saveCenterlinePoints(this, trackId, centerlinePoints)
        showToast("Запазени ${centerlinePoints.size} точки!")
        updateStatus("Запазени ${centerlinePoints.size} точки")
    }

    private fun showClearConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Изчисти всички точки?")
            .setMessage("Това ще премахне всички точки. Сигурни ли сте?")
            .setPositiveButton("Да") { _, _ ->
                clearAllPoints()
            }
            .setNegativeButton("Не", null)
            .show()
    }

    private fun clearAllPoints() {
        centerlinePoints.clear()
        pointMarkers.forEach { it.remove(mapView) }
        pointMarkers.clear()
        if (centerlinePolyline != null) {
            mapView.overlays.remove(centerlinePolyline)
            centerlinePolyline = null
        }
        mapView.invalidate()
        updateStatus("Всички точки са изчистени")
        updatePointCount()
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Изтрий запазените точки?")
            .setMessage("Това ще изтрие всички запазени точки за тази писта. Сигурни ли сте?")
            .setPositiveButton("Да") { _, _ ->
                OfficialTrackCenterlineStorage.deleteCenterlinePoints(this, trackId)
                clearAllPoints()
                showToast("Точките са изтрити!")
            }
            .setNegativeButton("Не", null)
            .show()
    }

    private fun initializeGPS() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 1000
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                    currentLocation = locationResult.lastLocation
                }
            },
            mainLooper
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
                initializeGPS()
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::fusedLocationClient.isInitialized) {
            // Stop location updates
        }
    }
}

