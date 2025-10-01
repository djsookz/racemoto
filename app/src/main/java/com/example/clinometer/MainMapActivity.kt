package com.example.clinometer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.gms.location.*
import com.google.android.material.button.MaterialButton
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.jvm.java
import com.example.clinometer.settings.UnitsManager
import com.example.clinometer.network.WeatherService
import com.example.clinometer.network.OpenMeteoService
import com.example.clinometer.network.ElevationResponse
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainMapActivity : BaseActivity() {
    override fun getLayoutResourceId(): Int = R.layout.activity_main_map
    override fun getNavigationItemId(): Int = R.id.navMap
    private lateinit var mapView: MapView
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var btnStartSession: MaterialButton
    private lateinit var btnSessions: MaterialButton
    private lateinit var llEnvironment: LinearLayout
    private lateinit var tvTemperature: TextView
    private lateinit var tvAltitude: TextView

    private var currentLocation: Location? = null
    private var currentTemperature: Float? = null
    private var currentAltitude: Float? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var backPressedTime: Long = 0
    private lateinit var backToast: Toast

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val LOCATION_UPDATE_INTERVAL = 2000L
        private const val LOCATION_FASTEST_UPDATE_INTERVAL = 1000L
        private const val MY_LOCATION_ZOOM = 17.0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ProfileStorage.loadProfiles(this).isEmpty()) {
            startActivity(Intent(this, FirstProfileActivity::class.java))
            finish()
            return
        }

        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        Configuration.getInstance().userAgentValue = packageName

        mapView = findViewById(R.id.mapView)
        btnStartSession = findViewById(R.id.btnStartNavigationNoDestination)
        btnSessions = findViewById(R.id.btnSessions)
        llEnvironment = findViewById(R.id.llEnvironment)
        tvTemperature = findViewById(R.id.tvTemperature)
        tvAltitude = findViewById(R.id.tvAltitude)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(false)
        mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        mapView.controller.setZoom(17.0)
        mapView.isTilesScaledToDpi = true

        val customLocationIcon = createLocationIconFromDrawable()
        myLocationOverlay = MyLocationNewOverlay(mapView).apply {
            enableMyLocation()
            setDrawAccuracyEnabled(false)
            setPersonIcon(customLocationIcon)
            setDirectionIcon(customLocationIcon)
            setEnableAutoStop(false)
        }
        mapView.overlays.add(myLocationOverlay)

        btnStartSession.setOnClickListener {
            startNormalSession()
        }

        btnSessions.setOnClickListener {
            navigateToSessions()
        }
        
        updateEnvironmentDisplay() // Показваме placeholder стойности веднага

        locationRequest = LocationRequest.create().apply {
            interval = LOCATION_UPDATE_INTERVAL
            fastestInterval = LOCATION_FASTEST_UPDATE_INTERVAL
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let { location ->
                    currentLocation = location
                    myLocationOverlay.onLocationChanged(location, null)
                    
                    // Fetch weather data when we get location
                    if (currentTemperature == null && currentAltitude == null) {
                        fetchWeatherFromAPI(location)
                    }
                }
            }
        }

        if (!checkLocationPermission()) {
            requestLocationPermission()
        } else {
            retrieveCurrentLocation()
            myLocationOverlay.runOnFirstFix {
                runOnUiThread {
                    myLocationOverlay.myLocation?.let {
                        mapView.controller.setCenter(it)
                    }
                }
            }
        }
    }

    private fun createLocationIconFromDrawable(): Bitmap {
        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_navigation)
        val size = (48 * resources.displayMetrics.density).toInt()
        return if (drawable != null) {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap
        } else {
            createFallbackLocationIcon()
        }
    }

    private fun createFallbackLocationIcon(): Bitmap {
        val size = (48 * resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val centerX = size / 2f
        val centerY = size / 2f

        val backgroundPaint = android.graphics.Paint().apply {
            color = Color.WHITE
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }

        val strokePaint = android.graphics.Paint().apply {
            color = Color.BLACK
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }

        canvas.drawCircle(centerX, centerY, centerX - 4f, backgroundPaint)
        canvas.drawCircle(centerX, centerY, centerX - 4f, strokePaint)

        val arrowPaint = android.graphics.Paint().apply {
            color = ContextCompat.getColor(this@MainMapActivity, R.color.accent_blue)
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }

        val arrowStroke = android.graphics.Paint().apply {
            color = Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        val path = android.graphics.Path().apply {
            moveTo(centerX, centerY - 18f)
            lineTo(centerX - 10f, centerY + 18f)
            lineTo(centerX - 4f, centerY + 12f)
            lineTo(centerX, centerY + 6f)
            lineTo(centerX + 4f, centerY + 12f)
            lineTo(centerX + 10f, centerY + 18f)
            close()
        }

        canvas.drawPath(path, arrowStroke)
        canvas.drawPath(path, arrowPaint)
        return bitmap
    }

    private fun startNormalSession() {
        if (!checkLocationPermission()) {
            requestLocationPermission()
            return
        }

        val selectedProfileId = ProfileStorage.getSelectedProfileId(this)
        val profiles = ProfileStorage.loadProfiles(this)
        val profile = if (selectedProfileId != -1L) {
            profiles.find { it.id == selectedProfileId }
        } else {
            profiles.firstOrNull()
        }

        profile?.let {
            ProfileStorage.saveSelectedProfile(this, it.id)
            val intent = Intent(this, CountdownActivity::class.java).apply {
                putExtra("SELECTED_PROFILE", it)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        } ?: run {
            Toast.makeText(this, "Моля изберете профил", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, RacesActivity::class.java))
        }
    }

    private fun centerOnCurrentLocation() {
        val loc = myLocationOverlay.myLocation
        if (loc != null) {
            mapView.controller.animateTo(
                GeoPoint(loc.latitude, loc.longitude),
                MY_LOCATION_ZOOM,
                400L
            )
        } else {
            retrieveCurrentLocation()
        }
    }

    private fun retrieveCurrentLocation() {
        if (checkLocationPermission()) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    currentLocation = it
                    val geoPoint = GeoPoint(it.latitude, it.longitude)
                    mapView.controller.animateTo(geoPoint, MY_LOCATION_ZOOM, 400L)
                } ?: run {
                    Toast.makeText(this, "Локацията не може да бъде получена", Toast.LENGTH_SHORT).show()
                }
            }
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

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onResume() {
        if (ProfileStorage.loadProfiles(this).isEmpty()) {
            startActivity(Intent(this, FirstProfileActivity::class.java))
            finish()
            return
        }
        super.onResume()
        mapView.onResume()
        myLocationOverlay.enableMyLocation()
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        myLocationOverlay.disableMyLocation()
        stopLocationUpdates()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                retrieveCurrentLocation()
            } else {
                Toast.makeText(
                    this,
                    "Разрешение за локация е необходимо за пълна функционалност",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onBackPressed() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            backToast.cancel()
            super.onBackPressed()
            return
        } else {
            backToast = Toast.makeText(baseContext, "Натиснете отново за изход", Toast.LENGTH_SHORT)
            backToast.show()
        }
        backPressedTime = System.currentTimeMillis()
    }
    
    private fun fetchWeatherData() {
        if (currentLocation != null) {
            fetchWeatherFromAPI(currentLocation!!)
        }
    }
    
    private fun updateEnvironmentDisplay() {
        val tempText = if (currentTemperature != null) {
            UnitsManager.formatTemperature(currentTemperature!!, this)
        } else {
            val unit = UnitsManager.getTemperatureUnit(this)
            "--${unit.symbol}"
        }
        
        val altText = if (currentAltitude != null) {
            String.format("%.0fm", currentAltitude)
        } else {
            "--m"
        }
        
        tvTemperature.text = tempText
        tvAltitude.text = altText
        
        // Show environment info if we have any data
        if (currentTemperature != null || currentAltitude != null) {
            llEnvironment.visibility = LinearLayout.VISIBLE
        }
    }
    
    private fun fetchWeatherFromAPI(location: Location) {
        android.util.Log.d("MainMapActivity", "Fetching weather for location: ${location.latitude}, ${location.longitude}")
        lifecycleScope.launch {
            try {
                val weatherRetrofit = Retrofit.Builder()
                    .baseUrl("https://api.openweathermap.org/data/2.5/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                
                val elevationRetrofit = Retrofit.Builder()
                    .baseUrl("https://api.open-meteo.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                
                val weatherService = weatherRetrofit.create(WeatherService::class.java)
                val openMeteoService = elevationRetrofit.create(OpenMeteoService::class.java)
                
                // Fetch weather
                val weatherResponse = weatherService.getCurrentWeather(
                    location.latitude,
                    location.longitude,
                    "metric",
                    "bg",
                    "3779e3fdd0b6656b070993ef70b1420f"
                )
                android.util.Log.d("MainMapActivity", "Weather response: ${weatherResponse.isSuccessful}")
                if (weatherResponse.isSuccessful && weatherResponse.body() != null) {
                    val weather = weatherResponse.body()!!
                    currentTemperature = weather.main.temp.toFloat()
                    android.util.Log.d("MainMapActivity", "Temperature: $currentTemperature")
                }
                
                // Fetch elevation
                val elevationResponse = openMeteoService.getElevation(
                    location.latitude,
                    location.longitude
                )
                android.util.Log.d("MainMapActivity", "Elevation response: ${elevationResponse.isSuccessful}")
                if (elevationResponse.isSuccessful && elevationResponse.body() != null) {
                    val elevation = elevationResponse.body()!!
                    currentAltitude = elevation.elevation.firstOrNull()?.toFloat() ?: 0f
                    android.util.Log.d("MainMapActivity", "Altitude: $currentAltitude")
                }
                
                withContext(Dispatchers.Main) {
                    updateEnvironmentDisplay()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainMapActivity", "Error fetching weather data", e)
            }
        }
    }
}