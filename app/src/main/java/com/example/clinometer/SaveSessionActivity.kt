package com.example.clinometer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
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
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
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
    private lateinit var dateTimeText: TextView
    private lateinit var sessionNameInput: TextInputEditText
    private lateinit var descriptionInput: TextInputEditText
    private lateinit var photosRecyclerView: RecyclerView
    private lateinit var addPhotoButton: CardView
    private lateinit var saveButton: MaterialButton
    private lateinit var btnDelete: ImageButton
    private lateinit var mapLoadingProgress: ProgressBar
    
    private var raceId: Long = 0L
    private var race: com.example.clinometer.Race? = null
    private var routePoints: List<com.example.clinometer.RoutePoint> = emptyList()
    private val photoPaths = mutableListOf<String>()
    private lateinit var photoAdapter: PhotoAdapter
    
    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_IMAGE_PICK = 2
    private val REQUEST_CAMERA_PERMISSION = 100
    private var currentPhotoPath: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_save_session)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.hide(android.view.WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        }
        
        raceId = intent.getLongExtra("raceId", 0L)
        if (raceId == 0L) {
            finish()
            return
        }
        
        val allRaces = com.example.clinometer.RouteStorage.loadRaces(this)
        race = allRaces.find { it.id == raceId }
        
        if (race == null) {
            Toast.makeText(this, "Session not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        routePoints = com.example.clinometer.RouteStorage.loadRoutePoints(this, raceId)
        
        initializeViews()
        setupMap()
        setupStatistics()
        setupPhotos()
        setupInputs()
        setupButtons()
    }
    
    private fun initializeViews() {
        mapPreview = findViewById(R.id.mapPreview)
        distanceText = findViewById(R.id.distanceText)
        durationText = findViewById(R.id.durationText)
        maxSpeedText = findViewById(R.id.maxSpeedText)
        dateTimeText = findViewById(R.id.dateTimeText)
        sessionNameInput = findViewById(R.id.sessionNameInput)
        descriptionInput = findViewById(R.id.descriptionInput)
        photosRecyclerView = findViewById(R.id.photosRecyclerView)
        addPhotoButton = findViewById(R.id.addPhotoButton)
        saveButton = findViewById(R.id.saveButton)
        btnDelete = findViewById(R.id.btnDelete)
        mapLoadingProgress = findViewById(R.id.mapLoadingProgress)
    }
    
    private fun setupMap() {
        mapPreview.scalebar.enabled = false
        mapPreview.compass.enabled = false
        
        val styleUri = "mapbox://styles/djsookz/cmiyer8iu000101s84wqsav5l"
        mapPreview.mapboxMap.loadStyleUri(styleUri) { style ->
            mapLoadingProgress.visibility = View.GONE
            
            if (routePoints.isNotEmpty()) {
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
                    lineColor("#FF6020")
                    lineWidth(4.0)
                })
                
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
                        .pitch(0.0)
                        .build()
                )
            }
        }
    }
    
    private fun setupStatistics() {
        race?.let { r ->
            distanceText.text = String.format(Locale.getDefault(), "%.2f km", r.distance)
            
            val totalSeconds = r.duration / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            durationText.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
            
            maxSpeedText.text = String.format(Locale.getDefault(), "%.0f km/h", r.maxSpeed)
            
            val sdf = SimpleDateFormat("MMMM d, yyyy • HH:mm", Locale.US)
            dateTimeText.text = sdf.format(Date(r.timestamp)).uppercase()
        }
    }
    
    private fun setupInputs() {
        race?.let { r ->
            val defaultName = r.name ?: "Session ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(r.timestamp))}"
            sessionNameInput.setText(defaultName)
            r.description?.let { descriptionInput.setText(it) }
            photoPaths.clear()
            photoPaths.addAll(r.photoPaths)
            photoAdapter.notifyDataSetChanged()
        }
    }
    
    private fun setupPhotos() {
        photoAdapter = PhotoAdapter(photoPaths) { position ->
            photoPaths.removeAt(position)
            photoAdapter.notifyDataSetChanged()
        }
        photosRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        photosRecyclerView.adapter = photoAdapter
        addPhotoButton.setOnClickListener { showPhotoOptions() }
    }

    private fun setupButtons() {
        saveButton.setOnClickListener { saveSession() }
        btnDelete.setOnClickListener { showDeleteConfirmation() }
    }

    private fun showDeleteConfirmation() {
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Delete Session")
            .setMessage("Are you sure you want to delete this session? This action cannot be undone.")
            .setPositiveButton("DELETE") { _, _ -> deleteSession() }
            .setNegativeButton("CANCEL", null)
            .create()
        dialog.show()
        com.example.clinometer.DialogHelper.styleDialogButtons(dialog)
    }

    private fun deleteSession() {
        val allRaces = com.example.clinometer.RouteStorage.loadRaces(this).toMutableList()
        val raceIndex = allRaces.indexOfFirst { it.id == raceId }
        if (raceIndex != -1) {
            allRaces.removeAt(raceIndex)
            com.example.clinometer.RouteStorage.saveRaces(this, allRaces)
            val pointsFile = File(File(filesDir, "route_points"), "points_$raceId.json")
            if (pointsFile.exists()) pointsFile.delete()
            
            val intent = Intent(this, com.example.clinometer.MainContainerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("INITIAL_PAGE", com.example.clinometer.MainContainerActivity.PAGE_MAP)
            }
            startActivity(intent)
            finish()
        }
    }
    
    private fun showPhotoOptions() {
        val options = arrayOf("Camera", "Gallery")
        AlertDialog.Builder(this)
            .setTitle("Add Photo")
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
            val photoURI = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
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
            storageDir?.let { if (!it.exists()) it.mkdirs() }
            File(storageDir, imageFileName)
        } catch (e: IOException) {
            null
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    currentPhotoPath?.let {
                        photoPaths.add(it)
                        photoAdapter.notifyDataSetChanged()
                        currentPhotoPath = null
                    }
                }
                REQUEST_IMAGE_PICK -> {
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
            FileOutputStream(photoFile).use { output -> inputStream?.use { input -> input.copyTo(output) } }
            photoFile.absolutePath
        } catch (e: IOException) {
            null
        }
    }
    
    private fun saveSession() {
        race?.let { r ->
            val sessionName = sessionNameInput.text?.toString()?.trim() ?: r.name
            val description = descriptionInput.text?.toString()?.trim()
            val updatedRace = r.copy(name = sessionName, description = description, photoPaths = photoPaths.toList())
            val allRaces = com.example.clinometer.RouteStorage.loadRaces(this).toMutableList()
            val raceIndex = allRaces.indexOfFirst { it.id == raceId }
            if (raceIndex != -1) {
                allRaces[raceIndex] = updatedRace
                com.example.clinometer.RouteStorage.saveRaces(this, allRaces)
                
                val isNewSession = intent.getBooleanExtra("isNewSession", false)
                if (isNewSession) {
                    // Нова сесия от навигация/активна сесия - отиваме в ProcessingActivity
                    val intent = Intent(this, com.example.clinometer.ProcessingActivity::class.java).apply { putExtra("raceId", raceId) }
                    startActivity(intent)
                    finish()
                } else {
                    // Редактиране от списъка със сесии - връщаме се в списъка със сесии
                    val intent = Intent(this, com.example.clinometer.RacesActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                }
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            takePhoto()
        }
    }
}

class PhotoAdapter(
    private val photoPaths: List<String>,
    private val onRemoveClick: (Int) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {
    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: android.widget.ImageView = itemView.findViewById(R.id.photoImageView)
        val removeButton: android.widget.ImageView = itemView.findViewById(R.id.removePhotoButton)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }
    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photoPath = photoPaths[position]
        val bitmap = BitmapFactory.decodeFile(photoPath)
        holder.imageView.setImageBitmap(bitmap)
        
        // Reset remove button visibility when binding
        holder.removeButton.visibility = View.GONE
        
        // Long press to show X button
        holder.imageView.setOnLongClickListener {
            holder.removeButton.visibility = View.VISIBLE
            true
        }
        
        // Click on X button to remove photo
        holder.removeButton.setOnClickListener {
            onRemoveClick(position)
        }
        
        // Click on image to hide X button if visible
        holder.imageView.setOnClickListener {
            if (holder.removeButton.visibility == View.VISIBLE) {
                holder.removeButton.visibility = View.GONE
            }
        }
    }
    override fun getItemCount() = photoPaths.size
}
