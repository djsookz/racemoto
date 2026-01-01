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
import com.example.clinometer.DialogHelper
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
import com.example.clinometer.network.WeatherApiService
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
    private lateinit var weatherApiService: WeatherApiService

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

        // Зареждаме кешираните данни веднага за моментално показване
        loadCachedWeatherData()
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
            .baseUrl("https://api.weatherapi.com/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        weatherApiService = retrofitWeather.create(WeatherApiService::class.java)
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
            UnitsManager.formatTemperature(currentTemperature!!, this, decimals = 0)
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
    
    /**
     * Зарежда кешираните данни за температура и височина от SharedPreferences
     */
    private fun loadCachedWeatherData() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val cachedTemp = prefs.getFloat("cached_temperature", Float.NaN)
        val cachedAlt = prefs.getFloat("cached_altitude", Float.NaN)
        val cachedLat = prefs.getFloat("cached_location_lat", Float.NaN)
        val cachedLon = prefs.getFloat("cached_location_lon", Float.NaN)
        
        // Зареждаме само ако нямаме данни от сензорите
        if (currentTemperature == null && !cachedTemp.isNaN() && !cachedLat.isNaN() && !cachedLon.isNaN()) {
            currentTemperature = cachedTemp
            Log.d("DragPage", "✅ Loaded cached temperature: $currentTemperature°C")
        }
        
        if (currentAltitude == null && !cachedAlt.isNaN() && !cachedLat.isNaN() && !cachedLon.isNaN()) {
            currentAltitude = cachedAlt
            Log.d("DragPage", "✅ Loaded cached altitude: $currentAltitude m")
        }
    }
    
    /**
     * Кешира данните за температура и височина в SharedPreferences
     */
    private fun cacheWeatherData(location: Location) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val editor = prefs.edit()
        
        currentTemperature?.let {
            editor.putFloat("cached_temperature", it)
        }
        currentAltitude?.let {
            editor.putFloat("cached_altitude", it)
        }
        editor.putFloat("cached_location_lat", location.latitude.toFloat())
        editor.putFloat("cached_location_lon", location.longitude.toFloat())
        editor.apply()
        
        Log.d("DragPage", "💾 Cached weather data: temp=$currentTemperature, alt=$currentAltitude")
    }
    
    /**
     * Проверява дали трябва да направим заявка за данни
     */
    private fun shouldFetchWeatherData(location: Location): Boolean {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val cachedLat = prefs.getFloat("cached_location_lat", Float.NaN)
        val cachedLon = prefs.getFloat("cached_location_lon", Float.NaN)
        
        // Ако нямаме кеширани данни, правим заявка
        if (cachedLat.isNaN() || cachedLon.isNaN()) {
            Log.d("DragPage", "🔄 No cached data, fetching...")
            return true
        }
        
        // Проверяваме дали локацията е се променила значително
        val cachedLocation = Location("cached").apply {
            latitude = cachedLat.toDouble()
            longitude = cachedLon.toDouble()
        }
        val distanceKm = location.distanceTo(cachedLocation) / 1000.0
        
        if (distanceKm > CACHE_LOCATION_THRESHOLD_KM) {
            Log.d("DragPage", "🔄 Location changed significantly (${String.format("%.1f", distanceKm)}km), fetching...")
            return true
        }
        
        // Ако имаме кеширани данни и локацията е близо, не правим заявка
        Log.d("DragPage", "✅ Using cached data (location change: ${String.format("%.1f", distanceKm)}km)")
        return false
    }
    
    private fun fetchWeatherAndElevation() {
        if (!checkLocationPermission()) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                lifecycleScope.launch {
                    try {
                        // Fetch weather from WeatherAPI.com
                        val weatherResponse = weatherApiService.getCurrentWeather(
                            apiKey = "547cc84c36a447ab8fe131642251808",
                            location = "${location.latitude},${location.longitude}",
                            lang = "bg"
                        )
                        if (weatherResponse.isSuccessful && weatherResponse.body() != null) {
                            val weather = weatherResponse.body()!!
                            // WeatherAPI.com връща температурата в Celsius (temp_c)
                            // API е приоритетен - винаги презаписваме температурата от API
                            currentTemperature = weather.current.temp_c.toFloat()
                            updateEnvironmentDisplay()
                        }

                        // Fetch elevation - API е най-точен, затова винаги го използваме първо
                        val elevationResponse = openMeteoService.getElevation(
                            location.latitude,
                            location.longitude
                        )
                        if (elevationResponse.isSuccessful && elevationResponse.body() != null) {
                            val elevation = elevationResponse.body()!!.elevation.firstOrNull()
                            // API е приоритетен - винаги презаписваме височината от API
                            elevation?.let {
                                currentAltitude = it.toFloat()
                                updateEnvironmentDisplay()
                            }
                        }

                        // Кешираме новите данни
                        cacheWeatherData(location)

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

        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
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
        
        // Pre-start service and begin calibration IMMEDIATELY
        preStartDragService(mode)

        // Start countdown timer
        countdownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt() + 1
                val progress = ((5000 - millisUntilFinished) * 100 / 5000).toInt()
                
                tvCountdown.text = seconds.toString()
                progressBar.progress = progress
                
                // Play voice countdown
                soundManager.speakCountdown(seconds)
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

        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
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
        val deleteDialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(getString(R.string.delete_session_title))
            .setMessage(getString(R.string.delete_session_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                DragStorage.deleteDragSession(this, session.id)
                sessions.remove(session)
                dragAdapter.notifyDataSetChanged()
                updateNoDataVisibility()
            }
            .setNegativeButton(getString(R.string.dialog_cancel_button), null)
            .create()
        DialogHelper.styleDialogButtons(deleteDialog)
        deleteDialog.show()
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
        
        // Презареждаме калибрацията за текущия профил
        currentProfile?.let { profile ->
            DragCalibration.setProfile(profile.id)
            Log.d("DragPage", "🔄 onResume - Calibration reloaded for profile: ${profile.name} (ID: ${profile.id}), Calibrated: ${DragCalibration.isCalibrated}")
        }
        
        loadSessions()  // This will now filter by the current profile
        dragAdapter.notifyDataSetChanged()
        updateNoDataVisibility()

        // Зареждаме кешираните данни веднага за моментално показване
        loadCachedWeatherData()
        updateEnvironmentDisplay()

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
                // Проверяваме дали трябва да направим заявка
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null && shouldFetchWeatherData(location)) {
                        fetchWeatherAndElevation()
                    }
                }
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
        // НЕ unregister-ваме сензорите - те са регистрирани от ForegroundService!
        // sensorManager.unregisterListener(this) - ПРЕМАХНАТО защото вреди на ForegroundService
        locationManager.removeUpdates(this)
    }


    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                // Използваме sensor само като fallback (ако нямаме API данни)
                // API е по-точен за външната температура, затова не презаписваме ако вече имаме API данни
                if (currentTemperature == null) {
                    currentTemperature = event.values[0]
                    updateEnvironmentDisplay()
                }
            }
            Sensor.TYPE_PRESSURE -> {
                // Използваме pressure sensor само като последен fallback (ако нямаме API данни)
                // Не презаписваме височината ако вече имаме данни от API
                if (currentAltitude == null) {
                    val pressure = event.values[0]
                    currentAltitude = SensorManager.getAltitude(
                        SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                        pressure
                    )
                    updateEnvironmentDisplay()
                }
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
        
        // Използваме GPS altitude само като fallback (ако нямаме API данни)
        // API е по-точен, затова не презаписваме ако вече имаме данни от API
        if (location.hasAltitude() && currentAltitude == null) {
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
        private const val CACHE_LOCATION_THRESHOLD_KM = 5.0  // Кешът е валиден ако локацията е в радиус от 5км
    }
}

enum class MeasurementMode {
    ALL,
    ZERO_TO_100,
    ZERO_TO_200,
    HUNDRED_TO_200,
    QUARTER_MILE
}