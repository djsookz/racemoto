package com.example.clinometer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point as MapboxPoint
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView as MapboxMapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class SaveSessionActivity : AppCompatActivity() {
    
    private lateinit var mapPreview: MapboxMapView
    private lateinit var distanceText: TextView
    private lateinit var durationText: TextView
    private lateinit var maxSpeedText: TextView
    private lateinit var sessionNameInput: TextInputEditText
    private lateinit var descriptionInput: TextInputEditText
    private lateinit var photosRecyclerView: RecyclerView
    private lateinit var addPhotoButton: MaterialButton
    private lateinit var saveButton: MaterialButton
    private lateinit var mapLoadingProgress: ProgressBar
    
    private var raceId: Long = 0L
    private var race: Race? = null
    private var routePoints: List<RoutePoint> = emptyList()
    private val photoPaths = mutableListOf<String>()
    private lateinit var photoAdapter: PhotoAdapter
    
    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_IMAGE_PICK = 2
    private val REQUEST_CAMERA_PERMISSION = 100
    private var currentPhotoPath: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_save_session)
        
        // Hide status bar for fullscreen map view (after setContentView)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.hide(android.view.WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
        }
        
        // Get raceId from intent
        raceId = intent.getLongExtra("raceId", 0L)
        if (raceId == 0L) {
            Toast.makeText(this, "Грешка: Липсва идентификатор на сесията", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // Load race data
        val allRaces = RouteStorage.loadRaces(this)
        race = allRaces.find { it.id == raceId }
        
        if (race == null) {
            Toast.makeText(this, "Грешка: Сесията не е намерена", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // Load route points
        routePoints = RouteStorage.loadRoutePoints(this, raceId)
        if (routePoints.isEmpty()) {
            Toast.makeText(this, "Грешка: Няма данни за маршрута", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        initializeViews()
        setupMap()
        setupStatistics()
        setupPhotos() // Initialize photoAdapter first
        setupInputs()
        setupSaveButton()
    }
    
    private fun initializeViews() {
        mapPreview = findViewById(R.id.mapPreview)
        distanceText = findViewById(R.id.distanceText)
        durationText = findViewById(R.id.durationText)
        maxSpeedText = findViewById(R.id.maxSpeedText)
        sessionNameInput = findViewById(R.id.sessionNameInput)
        descriptionInput = findViewById(R.id.descriptionInput)
        photosRecyclerView = findViewById(R.id.photosRecyclerView)
        addPhotoButton = findViewById(R.id.addPhotoButton)
        saveButton = findViewById(R.id.saveButton)
        mapLoadingProgress = findViewById(R.id.mapLoadingProgress)
    }
    
    private fun setupMap() {
        // Disable scale bar and compass
        mapPreview.scalebar.enabled = false
        mapPreview.compass.enabled = false
        
        val styleUri = "mapbox://styles/djsookz/cmiyer8iu000101s84wqsav5l"
        mapPreview.mapboxMap.loadStyleUri(styleUri) { style ->
            mapLoadingProgress.visibility = View.GONE
            
            // Draw route
            val mapboxPoints = routePoints.map { 
                MapboxPoint.fromLngLat(it.geoPoint.longitude, it.geoPoint.latitude) 
            }
            val lineString = LineString.fromLngLats(mapboxPoints)
            val feature = Feature.fromGeometry(lineString)
            val featureCollection = FeatureCollection.fromFeatures(listOf(feature))
            
            style.addSource(geoJsonSource("route-source") {
                featureCollection(featureCollection)
            })
            
            style.addLayer(lineLayer("route-layer", "route-source") {
                lineColor("#FF6020") // Orange
                lineWidth(4.0)
            })
            
            // Calculate bounds and set camera
            val allGeoPoints = routePoints.map { it.geoPoint }
            val boundingBox = BoundingBox.fromGeoPointsSafe(allGeoPoints)
            
            val centerLat = (boundingBox.latSouth + boundingBox.latNorth) / 2.0
            val centerLon = (boundingBox.lonWest + boundingBox.lonEast) / 2.0
            
            val latDiff = boundingBox.latNorth - boundingBox.latSouth
            val lonDiff = boundingBox.lonEast - boundingBox.lonWest
            val maxDiff = maxOf(latDiff, lonDiff)
            
            val zoom = if (maxDiff > 0.0) {
                kotlin.math.log2(360.0 / maxDiff) - 1.5
            } else {
                15.0
            }.coerceIn(3.0, 19.0)
            
            mapPreview.mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(MapboxPoint.fromLngLat(centerLon, centerLat))
                    .zoom(zoom)
                    .pitch(0.0) // Top-down view (no tilt)
                    .build()
            )
        }
    }
    
    private fun setupStatistics() {
        race?.let { r ->
            // Distance
            val distanceKm = r.distance / 1000.0
            distanceText.text = String.format(Locale.getDefault(), "%.2f km", distanceKm)
            
            // Duration - форматираме правилно времето с секунди
            val totalSeconds = r.duration / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            durationText.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
            
            // Max Speed
            val maxSpeedKmh = r.maxSpeed
            maxSpeedText.text = String.format(Locale.getDefault(), "%.0f km/h", maxSpeedKmh)
        }
    }
    
    private fun setupInputs() {
        race?.let { r ->
            // Set default session name
            val defaultName = r.name ?: "Session ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(r.timestamp))}"
            sessionNameInput.setText(defaultName)
            
            // Set description if exists
            r.description?.let { descriptionInput.setText(it) }
            
            // Load existing photos
            photoPaths.clear()
            photoPaths.addAll(r.photoPaths)
            photoAdapter.notifyDataSetChanged()
        }
    }
    
    private fun setupPhotos() {
        // Initialize photo adapter with callback
        photoAdapter = PhotoAdapter(photoPaths) { position ->
            // Remove photo
            photoPaths.removeAt(position)
            photoAdapter.notifyDataSetChanged()
        }
        
        photosRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        photosRecyclerView.adapter = photoAdapter
        
        addPhotoButton.setOnClickListener {
            showPhotoOptions()
        }
    }
    
    private fun showPhotoOptions() {
        val options = arrayOf("Камера", "Галерия")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Добави снимка")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> takePhoto()
                    1 -> pickPhoto()
                }
            }
            .show()
    }
    
    private fun takePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            return
        }
        
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile = createImageFile()
        if (photoFile != null) {
            currentPhotoPath = photoFile.absolutePath
            val photoURI = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile
            )
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
        }
    }
    
    private fun pickPhoto() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_IMAGE_PICK)
    }
    
    private fun createImageFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "session_${raceId}_${timeStamp}.jpg"
            val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            if (storageDir != null && !storageDir.exists()) {
                storageDir.mkdirs()
            }
            File(storageDir, imageFileName)
        } catch (e: IOException) {
            Log.e("SaveSessionActivity", "Error creating image file", e)
            null
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    // Photo taken - file is already saved to currentPhotoPath
                    currentPhotoPath?.let {
                        photoPaths.add(it)
                        photoAdapter.notifyDataSetChanged()
                        currentPhotoPath = null
                    }
                }
                REQUEST_IMAGE_PICK -> {
                    // Photo picked from gallery
                    data?.data?.let { uri ->
                        val photoPath = savePhotoFromGallery(uri)
                        photoPath?.let {
                            photoPaths.add(it)
                            photoAdapter.notifyDataSetChanged()
                        }
                    }
                }
            }
        }
    }
    
    private fun savePhotoFromGallery(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val photoFile = File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "session_${raceId}_${timeStamp}.jpg")
            
            FileOutputStream(photoFile).use { output ->
                inputStream?.use { input ->
                    input.copyTo(output)
                }
            }
            
            photoFile.absolutePath
        } catch (e: IOException) {
            Log.e("SaveSessionActivity", "Error saving photo", e)
            null
        }
    }
    
    private fun setupSaveButton() {
        saveButton.setOnClickListener {
            saveSession()
        }
    }
    
    private fun saveSession() {
        race?.let { r ->
            val sessionName = sessionNameInput.text?.toString()?.trim() ?: r.name
            val description = descriptionInput.text?.toString()?.trim()
            
            // Update race with new data
            val updatedRace = r.copy(
                name = sessionName,
                description = description,
                photoPaths = photoPaths.toList()
            )
            
            // Save updated race
            val allRaces = RouteStorage.loadRaces(this).toMutableList()
            val raceIndex = allRaces.indexOfFirst { it.id == raceId }
            if (raceIndex != -1) {
                allRaces[raceIndex] = updatedRace
                RouteStorage.saveRaces(this, allRaces)
                
                // Navigate to ProcessingActivity
                val intent = Intent(this, ProcessingActivity::class.java).apply {
                    putExtra("raceId", raceId)
                }
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Грешка: Сесията не е намерена", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            takePhoto()
        }
    }
    
    override fun onStart() {
        super.onStart()
        mapPreview.onStart()
    }
    
    override fun onStop() {
        super.onStop()
        mapPreview.onStop()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mapPreview.onDestroy()
    }
}

// Simple Photo Adapter
class PhotoAdapter(
    private val photoPaths: List<String>,
    private val onRemoveClick: (Int) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {
    
    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: android.widget.ImageView = itemView.findViewById(R.id.photoImageView)
        val removeButton: MaterialButton = itemView.findViewById(R.id.removePhotoButton)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photoPath = photoPaths[position]
        val bitmap = BitmapFactory.decodeFile(photoPath)
        holder.imageView.setImageBitmap(bitmap)
        
        holder.removeButton.setOnClickListener {
            onRemoveClick(position)
        }
    }
    
    override fun getItemCount() = photoPaths.size
}

