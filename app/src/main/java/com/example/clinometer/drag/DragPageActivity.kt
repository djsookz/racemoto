package com.example.clinometer

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.drag.DragRunPageActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import android.os.Build
import android.widget.ProgressBar
import android.widget.Toast
import android.content.res.Configuration
import android.util.Log
import com.example.clinometer.settings.SoundManager
import com.example.clinometer.settings.UnitsManager
import com.example.clinometer.network.WeatherService
import com.example.clinometer.network.OpenMeteoService


class DragPageActivity : BaseActivity(), SensorEventListener, LocationListener {

    override fun getLayoutResourceId(): Int {
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            R.layout.activity_drag_page
        } else {
            R.layout.activity_drag_page
        }
    }
    override fun getNavigationItemId(): Int = R.id.navDrag

    private lateinit var tvTemperature: TextView
    private lateinit var tvAltitude: TextView
    private lateinit var tvNoData: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnStartSession: Button

    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var openMeteoService: OpenMeteoService
    private lateinit var weatherService: WeatherService

    private var backPressedTime: Long = 0
    private lateinit var backToast: Toast

    private var temperatureSensor: Sensor? = null
    private var pressureSensor: Sensor? = null
    private var currentTemperature: Float? = null
    private var currentAltitude: Float? = null
    private var sessions: MutableList<DragSession> = mutableListOf()
    private lateinit var dragAdapter: DragSessionAdapter

    private var currentProfile: Profile? = null
    
    // GPS coordinates
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    
    // Countdown dialog state
    private var countdownDialog: AlertDialog? = null
    private var countdownTimer: CountDownTimer? = null
    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize sound manager
        soundManager = SoundManager(this)

        initializeViews()
        initializeLocationServices()
        initializeSensors()
        loadCurrentProfile()
        loadSessions()
        setupRecyclerView()
        checkLocationPermission()
        setupBottomNavigation()
    }

    private fun initializeViews() {
        tvTemperature = findViewById(R.id.tvTemperature)
        tvAltitude = findViewById(R.id.tvAltitude)
        tvNoData = findViewById(R.id.tvNoData)
        recyclerView = findViewById(R.id.rvDragSessions)
        btnStartSession = findViewById(R.id.btnStartDragSession)

        btnStartSession.setOnClickListener {
            if (checkLocationPermission()) {
                startCountdownAndSession()
            } else {
                requestLocationPermission()
            }
        }

        updateEnvironmentDisplay()
    }

    private fun initializeLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val retrofitOpenMeteo = Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        openMeteoService = retrofitOpenMeteo.create(OpenMeteoService::class.java)

        val retrofitWeather = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        weatherService = retrofitWeather.create(WeatherService::class.java)
    }

    private fun initializeSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    }

    private fun loadCurrentProfile() {
        val currentProfileId = getCurrentProfileId()
        val profiles = ProfileStorage.loadProfiles(this)

        currentProfile = if (currentProfileId != -1L) {
            profiles.find { it.id == currentProfileId }
        } else {
            profiles.firstOrNull()
        }

        if (currentProfile == null && profiles.isEmpty()) {
            currentProfile = Profile(
                name = "Default",
                vehicleType = Profile.VehicleType.CAR
            )
        }
    }

    private fun getCurrentProfileId(): Long {
        return ProfileStorage.getSelectedProfileId(this).also { profileId ->
            println("🔍 Current profile ID from ProfileStorage: $profileId")
        }
    }

    private fun loadSessions() {
        val currentProfileId = getCurrentProfileId()

        val loadedSessions = DragStorage.loadDragSessions(this)
            .filter { it.profileId == currentProfileId }
            .sortedByDescending { it.timestamp }

        sessions.clear()
        sessions.addAll(loadedSessions)

        updateNoDataVisibility()

        // Log for debugging
        println("Loaded ${sessions.size} sessions for profile $currentProfileId")
        sessions.forEach { session ->
            println("Session: ${session.name}, ID: ${session.id}, attempts: ${session.attempts.size}")
        }
    }

    private fun setupRecyclerView() {
        dragAdapter = DragSessionAdapter(
            sessions = sessions,
            onItemClick = { session ->
                val intent = Intent(this, DragSessionDetailsActivity::class.java)
                intent.putExtra("SESSION_ID", session.id)
                startActivity(intent)
            },
            onDeleteClick = { session ->
                showDeleteConfirmation(session)
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@DragPageActivity)
            adapter = dragAdapter
        }
    }

    private fun updateNoDataVisibility() {
        if (sessions.isEmpty()) {
            tvNoData.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvNoData.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
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
    }

    private fun fetchWeatherAndElevation() {
        if (!checkLocationPermission()) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                lifecycleScope.launch {
                    try {
                        // Fetch weather
                        val weatherResponse = weatherService.getCurrentWeather(
                            location.latitude,
                            location.longitude,
                            "metric",
                            "bg",
                            "3779e3fdd0b6656b070993ef70b1420f" // Replace with your actual API key
                        )
                        if (weatherResponse.isSuccessful && weatherResponse.body() != null) {
                            val weather = weatherResponse.body()!!
                            // Weather API с "metric" параметър връща в Celsius
                            currentTemperature = weather.main.temp.toFloat()
                        }

                        // Fetch elevation
                        val elevationResponse = openMeteoService.getElevation(
                            location.latitude,
                            location.longitude
                        )
                        if (elevationResponse.isSuccessful && elevationResponse.body() != null) {
                            val elevation = elevationResponse.body()!!.elevation.firstOrNull()
                            currentAltitude = elevation?.toFloat()
                        }

                        updateEnvironmentDisplay()
                    } catch (e: Exception) {
                        // Handle errors silently
                    }
                }
            }
        }
    }


    private fun startCountdown(mode: MeasurementMode) {
        showCountdownDialog(mode)
    }

    private fun showCountdownDialog(mode: MeasurementMode) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_countdown, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val tvCountdown = dialogView.findViewById<TextView>(R.id.tvCountdown)
        val tvModeInfo = dialogView.findViewById<TextView>(R.id.tvModeInfo)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelCountdown)

        // Set mode info text
        tvModeInfo.text = when(mode) {
            MeasurementMode.ALL -> getString(R.string.drag_measuring_all)
            MeasurementMode.ZERO_TO_100 -> getString(R.string.drag_measuring_0to100)
            MeasurementMode.ZERO_TO_200 -> getString(R.string.drag_measuring_0to200)
            MeasurementMode.HUNDRED_TO_200 -> getString(R.string.drag_measuring_100to200)
            MeasurementMode.QUARTER_MILE -> getString(R.string.drag_measuring_402m)
        }

        // Store dialog state
        countdownDialog = dialog

        // Start GPS location updates during countdown (like in normal sessions)
        startLocationUpdates()

        // Start countdown timer
        countdownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt() + 1
                val progress = ((5000 - millisUntilFinished) * 100 / 5000).toInt()
                
                tvCountdown.text = seconds.toString()
                progressBar.progress = progress
                
                // Play voice countdown
                soundManager.speakCountdown(seconds)
                
                // Pre-start the service at 2 seconds so it's ready when countdown finishes
                if (seconds == 2) {
                    preStartDragService(mode)
                }
            }

            override fun onFinish() {
                // Play "GO!" sound
                soundManager.speakCountdown(0)
                
                dialog.dismiss()
                clearCountdownState()
                startDragRun(mode)
            }
        }

        btnCancel.setOnClickListener {
            countdownTimer?.cancel()
            stopLocationUpdates()
            dialog.dismiss()
            clearCountdownState()
        }

        dialog.show()
        countdownTimer?.start()
    }

    private fun clearCountdownState() {
        countdownDialog = null
        countdownTimer = null
    }

    private fun startLocationUpdates() {
        if (checkLocationPermission()) {
            try {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    100, // More frequent updates during countdown
                    0f,
                    this
                )
            } catch (e: SecurityException) {
                // Handle permission error
            }
        }
    }

    private fun stopLocationUpdates() {
        try {
            locationManager?.removeUpdates(this)
        } catch (e: SecurityException) {
            // Handle permission error
        }
    }

    private fun startCountdownAndSession() {
        showMeasurementModeDialog()
    }

    private fun showMeasurementModeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_measurement_mode, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val btnMeasureAll = dialogView.findViewById<Button>(R.id.btnMeasureAll)
        val btn0to100 = dialogView.findViewById<Button>(R.id.btn0to100)
        val btn0to200 = dialogView.findViewById<Button>(R.id.btn0to200)
        val btn100to200 = dialogView.findViewById<Button>(R.id.btn100to200)
        val btn0to402 = dialogView.findViewById<Button>(R.id.btn0to402)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelMode)
        
        // Обновяваме button текстовете според избраната единица
        val speedUnit = UnitsManager.getSpeedUnit(this)
        val speed100 = UnitsManager.convertSpeed(100f, speedUnit).toInt()
        val speed200 = UnitsManager.convertSpeed(200f, speedUnit).toInt()
        btn0to100.text = "0-$speed100"
        btn0to200.text = "0-$speed200"
        btn100to200.text = "$speed100-$speed200"
        btn0to402.text = "0-${UnitsManager.getQuarterMileDistance(this)}"

        btnMeasureAll.setOnClickListener {
            dialog.dismiss()
            startCountdown(MeasurementMode.ALL)
        }

        btn0to100.setOnClickListener {
            dialog.dismiss()
            startCountdown(MeasurementMode.ZERO_TO_100)
        }

        btn0to200.setOnClickListener {
            dialog.dismiss()
            startCountdown(MeasurementMode.ZERO_TO_200)
        }

        btn100to200.setOnClickListener {
            dialog.dismiss()
            startCountdown(MeasurementMode.HUNDRED_TO_200)
        }

        btn0to402.setOnClickListener {
            dialog.dismiss()
            startCountdown(MeasurementMode.QUARTER_MILE)
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun preStartDragService(mode: MeasurementMode) {
        // Start the ForegroundService early so it's ready when activity opens
        val serviceIntent = Intent(this, ForegroundService::class.java).apply {
            putExtra("profileId", currentProfile?.id)
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
    
    private fun startDragRun(mode: MeasurementMode) {
        val intent = Intent(this, DragRunPageActivity::class.java)
        intent.putExtra("PROFILE_ID", currentProfile?.id)
        intent.putExtra("TEMPERATURE", currentTemperature)
        intent.putExtra("ALTITUDE", currentAltitude)
        intent.putExtra("MEASUREMENT_MODE", mode.name)
        
        // Pass GPS data if available
        if (currentLatitude != null && currentLongitude != null) {
            intent.putExtra("LATITUDE", currentLatitude)
            intent.putExtra("LONGITUDE", currentLongitude)
        }
        
        startActivityForResult(intent, REQUEST_DRAG_RUN)
    }

    private fun showDeleteConfirmation(session: DragSession) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_session_title))
            .setMessage(getString(R.string.delete_session_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                DragStorage.deleteDragSession(this, session.id)
                sessions.remove(session)
                dragAdapter.notifyDataSetChanged()
                updateNoDataVisibility()
            }
            .setNegativeButton(getString(R.string.dialog_cancel_button), null)
            .show()
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
            PERMISSION_REQUEST_LOCATION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCountdownAndSession()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_DRAG_RUN -> {
                if (resultCode == RESULT_OK) {
                    // Принудително презареждане на всички данни
                    loadCurrentProfile()
                    loadSessions()
                    dragAdapter.notifyDataSetChanged()
                    updateNoDataVisibility()

                    // Ако има нужда от допълнително презареждане
                    fetchWeatherAndElevation()
                }
            }
        }
    }
    private val sessionUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "SESSION_UPDATED") {
                println("📢 Received session update broadcast")
                loadSessions()
                dragAdapter.notifyDataSetChanged()
                updateNoDataVisibility()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                sessionUpdateReceiver,
                IntentFilter("SESSION_UPDATED"),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(sessionUpdateReceiver, IntentFilter("SESSION_UPDATED"))
        }
        loadCurrentProfile()
        loadSessions()  // This will now filter by the current profile
        dragAdapter.notifyDataSetChanged()
        updateNoDataVisibility()

        // Останалия код за сензори и локация
        temperatureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        pressureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        if (checkLocationPermission()) {
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    5000,
                    0f,
                    this
                )
                fetchWeatherAndElevation()
            } catch (e: SecurityException) {
                // Handle permission error
            }
        }
    }

    override fun onBackPressed() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            backToast.cancel()
            super.onBackPressed()
            return
        } else {
            backToast = Toast.makeText(baseContext, getString(R.string.back_press_exit), Toast.LENGTH_SHORT)
            backToast.show()
        }
        backPressedTime = System.currentTimeMillis()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(this)
    }


    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                currentTemperature = event.values[0]
                updateEnvironmentDisplay()
            }
            Sensor.TYPE_PRESSURE -> {
                val pressure = event.values[0]
                currentAltitude = SensorManager.getAltitude(
                    SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                    pressure
                )
                updateEnvironmentDisplay()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    override fun onLocationChanged(location: Location) {
        // Update GPS coordinates
        currentLatitude = location.latitude
        currentLongitude = location.longitude
        
        if (location.hasAltitude()) {
            currentAltitude = location.altitude.toFloat()
            updateEnvironmentDisplay()
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        
        // Ръчно презареждане на layout-а при смяна на ориентацията
        val layoutId = if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            R.layout.activity_drag_page
        } else {
            R.layout.activity_drag_page
        }
        
        setContentView(layoutId)
        
        // Реинициализираме всички view-та
        initializeViews()
        setupRecyclerView()
        loadSessions()
        
        // Възстановяваме навигацията
        setupBottomNavigation()
        
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager?.removeUpdates(this)
        sensorManager?.unregisterListener(this)
        countdownTimer?.cancel()
        countdownDialog?.dismiss()
        soundManager.release()
    }

    companion object {
    private const val REQUEST_DRAG_RUN = 1001
        private const val PERMISSION_REQUEST_LOCATION = 1003
    }
}

enum class MeasurementMode {
    ALL,
    ZERO_TO_100,
    ZERO_TO_200,
    HUNDRED_TO_200,
    QUARTER_MILE
}