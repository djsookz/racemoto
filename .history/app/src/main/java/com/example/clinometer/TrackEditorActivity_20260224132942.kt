package com.example.clinometer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sqrt
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker
import java.util.*

class TrackEditorActivity : AppCompatActivity() {
    
    private lateinit var mapView: MapView
    private lateinit var mapController: IMapController
    private lateinit var btnSelectTrack: Button
    private lateinit var btnStartDrawing: Button
    private lateinit var btnStopDrawing: Button
    private lateinit var btnClear: Button
    private lateinit var btnSave: Button
    private lateinit var btnLoad: Button
    private lateinit var tvStatus: TextView
    private lateinit var etTrackName: EditText
    
    private var isDrawing = false
    private var currentTrackPoints = mutableListOf<GeoPoint>()
    private var currentPolyline: Polyline? = null
    private var trackMarkers = mutableListOf<Marker>()
    
    // Touch handling variables
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchMoved = false
    private val TOUCH_MOVE_THRESHOLD = 20f // pixels
    
    private var selectedOfficialTrack: String? = null
    
    companion object {
        private const val TAG = "TrackEditorActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_editor)
        applySystemBarsPaddingToRoot()
        
        initViews()
        setupMap()
        setupButtons()
    }
    
    private fun initViews() {
        mapView = findViewById(R.id.mapView)
        btnSelectTrack = findViewById(R.id.btnSelectTrack)
        btnStartDrawing = findViewById(R.id.btnStartDrawing)
        btnStopDrawing = findViewById(R.id.btnStopDrawing)
        btnClear = findViewById(R.id.btnClear)
        btnSave = findViewById(R.id.btnSave)
        btnLoad = findViewById(R.id.btnLoad)
        tvStatus = findViewById(R.id.tvStatus)
        etTrackName = findViewById(R.id.etTrackName)
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
                    if (isDrawing) {
                        touchStartX = event.x
                        touchStartY = event.y
                        touchMoved = false
                    }
                    false // Let map handle the touch
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDrawing) {
                        val deltaX = event.x - touchStartX
                        val deltaY = event.y - touchStartY
                        val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
                        
                        if (distance > TOUCH_MOVE_THRESHOLD) {
                            touchMoved = true
                        }
                    }
                    false // Let map handle the movement
                }
                MotionEvent.ACTION_UP -> {
                    if (isDrawing && !touchMoved) {
                        // Only add point if it was a tap, not a drag
                        val geoPoint = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                        addTrackPoint(geoPoint)
                        return@setOnTouchListener true
                    }
                    false // Let map handle the release
                }
                else -> false
            }
        }
    }
    
    private fun setupButtons() {
        btnSelectTrack.setOnClickListener {
            showOfficialTrackSelector()
        }
        
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
            if (selectedOfficialTrack != null) {
                saveOfficialTrack()
            } else {
                saveCustomTrack()
            }
        }
        
        btnLoad.setOnClickListener {
            showTrackSelector()
        }
        
        updateButtonStates()
    }
    
    private fun startDrawing() {
        isDrawing = true
        currentTrackPoints.clear()
        clearTrackVisuals()
        updateButtonStates()
        tvStatus.text = "Начертаване на трасето... Натиснете точките на картата"
        Log.d(TAG, "Started drawing track")
    }
    
    private fun stopDrawing() {
        isDrawing = false
        updateButtonStates()
        tvStatus.text = "Начертаването спряно. Точки: ${currentTrackPoints.size}"
        Log.d(TAG, "Stopped drawing track. Points: ${currentTrackPoints.size}")
    }
    
    private fun addTrackPoint(point: GeoPoint) {
        currentTrackPoints.add(point)
        
        // Add marker for visual feedback
        val marker = Marker(mapView)
        marker.position = point
        marker.setIcon(getDrawable(R.drawable.ic_place_black_24dp))
        marker.title = "Point ${currentTrackPoints.size}"
        mapView.overlays.add(marker)
        trackMarkers.add(marker)
        
        // Update polyline
        updateTrackPolyline()
        updateButtonStates()
        
        // Refresh map
        mapView.invalidate()
        
        tvStatus.text = "Точки: ${currentTrackPoints.size} | Начертаване активно"
        Log.d(TAG, "Added track point: ${point.latitude}, ${point.longitude}")
    }
    
    private fun updateTrackPolyline() {
        if (currentTrackPoints.size < 2) return
        
        // Remove old polyline
        currentPolyline?.let { mapView.overlays.remove(it) }
        
        // Create new polyline
        val newPolyline = Polyline()
        newPolyline.setPoints(ArrayList(currentTrackPoints))
        newPolyline.color = Color.RED
        newPolyline.width = 8f
        currentPolyline = newPolyline
        
        mapView.overlays.add(currentPolyline)
        mapView.invalidate()
    }
    
    private fun clearTrack() {
        isDrawing = false
        currentTrackPoints.clear()
        clearTrackVisuals()
        updateButtonStates()
        tvStatus.text = "Трасето изчистено"
        Log.d(TAG, "Cleared track")
    }
    
    private fun clearTrackVisuals() {
        // Remove polyline
        currentPolyline?.let { mapView.overlays.remove(it) }
        currentPolyline = null
        
        // Remove markers
        trackMarkers.forEach { mapView.overlays.remove(it) }
        trackMarkers.clear()
        
        mapView.invalidate()
    }
    
    private fun saveTrack() {
        val trackName = etTrackName.text.toString().trim()
        
        if (trackName.isEmpty()) {
            Toast.makeText(this, "Въведете име на трасето", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (currentTrackPoints.size < 3) {
            Toast.makeText(this, "Трасето трябва да има поне 3 точки", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Save to SharedPreferences
        val prefs = getSharedPreferences("custom_tracks", MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Convert points to string format
        val pointsString = currentTrackPoints.joinToString("|") { point ->
            "${point.latitude},${point.longitude}"
        }
        
        editor.putString("track_${trackName.lowercase()}", pointsString)
        editor.putString("track_${trackName.lowercase()}_name", trackName)
        editor.apply()
        
        Toast.makeText(this, "Трасето '$trackName' е запазено!", Toast.LENGTH_SHORT).show()
        tvStatus.text = "Запазено: $trackName (${currentTrackPoints.size} точки)"
        
        Log.d(TAG, "Saved track: $trackName with ${currentTrackPoints.size} points")
    }
    
    private fun showTrackSelector() {
        val prefs = getSharedPreferences("custom_tracks", MODE_PRIVATE)
        val allKeys = prefs.all.keys.filter { it.startsWith("track_") && it.endsWith("_name") }
        
        if (allKeys.isEmpty()) {
            Toast.makeText(this, "Няма запазени трасета", Toast.LENGTH_SHORT).show()
            return
        }
        
        val trackNames = allKeys.map { prefs.getString(it, "") }.filter { !it.isNullOrEmpty() }
        
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Избери трасето")
        builder.setItems(trackNames.toTypedArray()) { _, which ->
            val selectedTrackName = trackNames[which]
            if (selectedTrackName != null) {
                loadTrack(selectedTrackName)
            }
        }
        builder.show()
    }
    
    private fun loadTrack(trackName: String) {
        val prefs = getSharedPreferences("custom_tracks", MODE_PRIVATE)
        val pointsString = prefs.getString("track_${trackName.lowercase()}", null)
        
        if (pointsString == null) {
            Toast.makeText(this, "Грешка при зареждане на трасето", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Parse points
        val points = pointsString.split("|").mapNotNull { pointStr ->
            val parts = pointStr.split(",")
            if (parts.size == 2) {
                try {
                    GeoPoint(parts[0].toDouble(), parts[1].toDouble())
                } catch (e: NumberFormatException) {
                    null
                }
            } else null
        }
        
        if (points.isEmpty()) {
            Toast.makeText(this, "Невалидни данни за трасето", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Load track
        currentTrackPoints.clear()
        currentTrackPoints.addAll(points)
        
        clearTrackVisuals()
        updateTrackPolyline()
        
        // Add markers for all points
        points.forEachIndexed { index, point ->
            val marker = Marker(mapView)
            marker.position = point
            marker.setIcon(getDrawable(R.drawable.ic_place_black_24dp))
            marker.title = "Point ${index + 1}"
            mapView.overlays.add(marker)
            trackMarkers.add(marker)
        }
        
        mapView.invalidate()
        
        etTrackName.setText(trackName)
        tvStatus.text = "Заредено: $trackName (${points.size} точки)"
        
        Log.d(TAG, "Loaded track: $trackName with ${points.size} points")
    }
    
    private fun showOfficialTrackSelector() {
        val officialTracks = listOf(
            "Serres Circuit" to "serres_circuit",
            "Sofia Ring" to "sofia_ring"
        )
        
        val trackNames = officialTracks.map { it.first }
        
        AlertDialog.Builder(this)
            .setTitle("Избери официална писта")
            .setItems(trackNames.toTypedArray()) { _, which ->
                val selectedTrack = officialTracks[which]
                selectedOfficialTrack = selectedTrack.second
                etTrackName.setText(selectedTrack.first)
                loadOfficialTrack(selectedTrack.second)
            }
            .show()
    }
    
    private fun loadOfficialTrack(trackId: String) {
        // Start with empty track - user will add points manually
        currentTrackPoints.clear()
        clearTrackVisuals()
        updateButtonStates()
        
        // Set center of map to approximate track location for easier editing
        when (trackId) {
            "serres_circuit" -> {
                val centerPoint = GeoPoint(41.0862, 23.5490) // Approximate center of Serres Circuit
                mapView.controller.animateTo(centerPoint)
                mapView.controller.setZoom(16.0)
            }
            "sofia_ring" -> {
                val centerPoint = GeoPoint(42.6977, 23.3219) // Approximate center of Sofia Ring
                mapView.controller.animateTo(centerPoint)
                mapView.controller.setZoom(16.0)
            }
        }
        
        tvStatus.text = "Писта избрана: ${etTrackName.text}. Започнете да добавяте точки с tap-ове на картата."
        
        Log.d(TAG, "Selected official track: $trackId - ready for manual point addition")
    }
    
    private fun saveOfficialTrack() {
        val trackName = etTrackName.text.toString().trim()
        val trackId = selectedOfficialTrack
        
        if (trackId == null) {
            Toast.makeText(this, "Изберете писта първо!", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (currentTrackPoints.size < 3) {
            Toast.makeText(this, "Трасето трябва да има поне 3 точки!", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Save to SharedPreferences with official track ID
            val prefs = getSharedPreferences("official_tracks", MODE_PRIVATE)
            val editor = prefs.edit()
            
            // Convert points to string format
            val pointsString = currentTrackPoints.joinToString("|") { point ->
                "${point.latitude},${point.longitude}"
            }
            
            editor.putString("official_track_$trackId", pointsString)
            editor.putString("official_track_${trackId}_name", trackName)
            editor.apply()
            
            Toast.makeText(this, "Официалната писта '$trackName' е запазена!", Toast.LENGTH_SHORT).show()
            tvStatus.text = "Запазена официална писта: $trackName (${currentTrackPoints.size} точки)"
            
            Log.d(TAG, "Saved official track: $trackId with ${currentTrackPoints.size} points")
            
        } catch (e: Exception) {
            tvStatus.text = "Грешка при запазване: ${e.message}"
            Log.e(TAG, "Error saving official track", e)
        }
    }
    
    private fun saveCustomTrack() {
        val trackName = etTrackName.text.toString().trim()
        
        if (trackName.isEmpty()) {
            Toast.makeText(this, "Въведете име на трасето!", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (currentTrackPoints.size < 3) {
            Toast.makeText(this, "Трасето трябва да има поне 3 точки!", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Save to SharedPreferences as custom track
            val prefs = getSharedPreferences("custom_tracks", MODE_PRIVATE)
            val editor = prefs.edit()
            
            // Convert points to string format
            val pointsString = currentTrackPoints.joinToString("|") { point ->
                "${point.latitude},${point.longitude}"
            }
            
            editor.putString("track_${trackName.lowercase()}", pointsString)
            editor.putString("track_${trackName.lowercase()}_name", trackName)
            editor.apply()
            
            Toast.makeText(this, "Custom трасето '$trackName' е запазено!", Toast.LENGTH_SHORT).show()
            tvStatus.text = "Запазено custom трасе: $trackName (${currentTrackPoints.size} точки)"
            
            Log.d(TAG, "Saved custom track: $trackName with ${currentTrackPoints.size} points")
            
        } catch (e: Exception) {
            tvStatus.text = "Грешка при запазване: ${e.message}"
            Log.e(TAG, "Error saving custom track", e)
        }
    }
    
    private fun updateButtonStates() {
        btnSelectTrack.isEnabled = !isDrawing
        btnStartDrawing.isEnabled = !isDrawing
        btnStopDrawing.isEnabled = isDrawing
        btnClear.isEnabled = !isDrawing || currentTrackPoints.isNotEmpty()
        btnSave.isEnabled = currentTrackPoints.size >= 3 // Always enabled if we have enough points
        Log.d(TAG, "Button states updated - Save enabled: ${btnSave.isEnabled}, Points: ${currentTrackPoints.size}")
    }
    
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mapView.onDetach()
    }
}
