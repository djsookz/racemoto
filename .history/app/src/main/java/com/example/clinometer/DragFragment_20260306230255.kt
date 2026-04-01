package com.example.clinometer

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.clinometer.data.ProfileStorage
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.drag.DragRunPageActivity
import com.example.clinometer.drag.MeasurementMode
import com.example.clinometer.settings.SoundManager
import com.example.clinometer.settings.UnitsManager
import com.example.clinometer.network.WeatherApiService
import com.example.clinometer.network.OpenMeteoService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Fragment за Drag страницата - конвертиран от DragPageActivity с ПЪЛНА функционалност
 */
class DragFragment : Fragment(), SensorEventListener, LocationListener {
    
    private lateinit var tvTemperature: TextView
    private lateinit var tvAltitude: TextView
    private lateinit var tvNoData: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnStartSession: Button
    private lateinit var tvHeaderModelName: TextView
    private lateinit var ivHeaderProfileImage: android.widget.ImageView
    
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
    private val weatherRefreshHandler = Handler(Looper.getMainLooper())
    private var weatherRefreshRunnable: Runnable? = null
    private var sessions: MutableList<DragSession> = mutableListOf()
    private lateinit var dragAdapter: DragSessionAdapter
    
    private var currentProfile: Profile? = null
    
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    
    private var countdownDialog: AlertDialog? = null
    private var countdownTimer: CountDownTimer? = null
    private lateinit var soundManager: SoundManager
    
    // Професионално решение: lazy initialization на SharedPreferences
    private val profilePrefs by lazy { requireContext().getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE) }
    
    // Създаваме слушателя като променлива на класа (ВАЖНО, за да не бъде изтрит от Garbage Collector)
    private val profileChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "selected_profile_id") {
            loadProfileInfo()
        }
    }
    
    private val sessionUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "SESSION_UPDATED") {
                loadSessions()
                dragAdapter.notifyDataSetChanged()
                updateNoDataVisibility()
            }
        }
    }
    
    companion object {
        private const val REQUEST_DRAG_RUN = 1001
        private const val PERMISSION_REQUEST_LOCATION = 1003
        private const val CACHE_LOCATION_THRESHOLD_KM = 5.0
        private const val WEATHER_REFRESH_INTERVAL_MS = 15 * 60 * 1000L
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_drag_page, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        soundManager = SoundManager(requireContext())
        
        initializeViews(view)
        initializeLocationServices()
        initializeSensors()
        loadCurrentProfile()
        loadSessions()
        setupRecyclerView()
        checkLocationPermission()
        
        // Регистрираме слушателя за промени в профила
        profilePrefs.registerOnSharedPreferenceChangeListener(profileChangeListener)
        
        // Първоначално зареждане на профила
        view.post {
            loadProfileInfo()
        }
    }
    
    private fun initializeViews(view: View) {
        tvTemperature = view.findViewById(R.id.tvTemperature)
        tvAltitude = view.findViewById(R.id.tvAltitude)
        tvNoData = view.findViewById(R.id.tvNoData)
        recyclerView = view.findViewById(R.id.rvDragSessions)
        btnStartSession = view.findViewById(R.id.btnStartDragSession)
        tvHeaderModelName = view.findViewById(R.id.tvHeaderModelName)
        ivHeaderProfileImage = view.findViewById(R.id.ivHeaderProfileImage)
        
        btnStartSession.setOnClickListener {
            if (checkLocationPermission()) {
                verifyCalibrationAndStartSession()
            } else {
                requestLocationPermission()
            }
        }
        
        loadCachedWeatherData()
        updateEnvironmentDisplay()
    }
    
    private fun initializeLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        
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
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    }
    
    private fun loadCurrentProfile() {
        val currentProfileId = getCurrentProfileId()
        val profiles = ProfileStorage.loadProfiles(requireContext())
        
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
        return ProfileStorage.getSelectedProfileId(requireContext())
    }
    
    private fun loadSessions() {
        val currentProfileId = getCurrentProfileId()
        
        val loadedSessions = DragStorage.loadDragSessions(requireContext())
            .filter { it.profileId == currentProfileId }
            .sortedByDescending { it.timestamp }
        
        sessions.clear()
        sessions.addAll(loadedSessions)
        
        updateNoDataVisibility()
    }
    
    private fun setupRecyclerView() {
        dragAdapter = DragSessionAdapter(
            sessions = sessions,
            onItemClick = { session ->
                val intent = Intent(requireContext(), DragSessionDetailsActivity::class.java)
                intent.putExtra("SESSION_ID", session.id)
                startActivity(intent)
            },
            onDeleteClick = { session ->
                showDeleteConfirmation(session)
            }
        )
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
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
            UnitsManager.formatTemperature(currentTemperature!!, requireContext(), decimals = 0)
        } else {
            val unit = UnitsManager.getTemperatureUnit(requireContext())
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

    private fun isWeatherCacheStale(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val cachedTime = prefs.getLong("cached_weather_time", 0L)
        if (cachedTime == 0L) return true
        val now = System.currentTimeMillis()
        return now - cachedTime > WEATHER_REFRESH_INTERVAL_MS
    }

    private fun startWeatherRefreshTimer() {
        stopWeatherRefreshTimer()
        weatherRefreshRunnable = object : Runnable {
            override fun run() {
                if (!isAdded) return
                if (isWeatherCacheStale()) {
                    fetchWeatherAndElevation()
                }
                weatherRefreshHandler.postDelayed(this, WEATHER_REFRESH_INTERVAL_MS)
            }
        }
        weatherRefreshHandler.post(weatherRefreshRunnable!!)
    }

    private fun stopWeatherRefreshTimer() {
        weatherRefreshRunnable?.let { weatherRefreshHandler.removeCallbacks(it) }
        weatherRefreshRunnable = null
    }
    
    private fun loadCachedWeatherData() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val cachedTemp = prefs.getFloat("cached_temperature", Float.NaN)
        val cachedAlt = prefs.getFloat("cached_altitude", Float.NaN)
        val cachedLat = prefs.getFloat("cached_location_lat", Float.NaN)
        val cachedLon = prefs.getFloat("cached_location_lon", Float.NaN)
        
        if (currentTemperature == null && !cachedTemp.isNaN() && !cachedLat.isNaN() && !cachedLon.isNaN()) {
            currentTemperature = cachedTemp
        }
        
        if (currentAltitude == null && !cachedAlt.isNaN() && !cachedLat.isNaN() && !cachedLon.isNaN()) {
            currentAltitude = cachedAlt
        }
    }
    
    private fun cacheWeatherData(location: Location) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = prefs.edit()
        
        currentTemperature?.let { editor.putFloat("cached_temperature", it) }
        currentAltitude?.let { editor.putFloat("cached_altitude", it) }
        editor.putLong("cached_weather_time", System.currentTimeMillis())
        editor.putFloat("cached_location_lat", location.latitude.toFloat())
        editor.putFloat("cached_location_lon", location.longitude.toFloat())
        editor.apply()
    }
    
    private fun shouldFetchWeatherData(location: Location): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val cachedLat = prefs.getFloat("cached_location_lat", Float.NaN)
        val cachedLon = prefs.getFloat("cached_location_lon", Float.NaN)

        if (isWeatherCacheStale()) {
            return true
        }
        
        if (cachedLat.isNaN() || cachedLon.isNaN()) {
            return true
        }
        
        val cachedLocation = Location("cached").apply {
            latitude = cachedLat.toDouble()
            longitude = cachedLon.toDouble()
        }
        val distanceKm = location.distanceTo(cachedLocation) / 1000.0
        
        return distanceKm > CACHE_LOCATION_THRESHOLD_KM
    }
    
    private fun fetchWeatherAndElevation() {
        if (!checkLocationPermission()) return
        
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val weatherResponse = weatherApiService.getCurrentWeather(
                            apiKey = "547cc84c36a447ab8fe131642251808",
                            location = "${location.latitude},${location.longitude}",
                            lang = "bg"
                        )
                        if (weatherResponse.isSuccessful && weatherResponse.body() != null) {
                            val weather = weatherResponse.body()!!
                            currentTemperature = weather.current.temp_c.toFloat()
                            updateEnvironmentDisplay()
                        }
                        
                        val elevationResponse = openMeteoService.getElevation(
                            location.latitude,
                            location.longitude
                        )
                        if (elevationResponse.isSuccessful && elevationResponse.body() != null) {
                            val elevation = elevationResponse.body()!!.elevation.firstOrNull()
                            elevation?.let {
                                currentAltitude = it.toFloat()
                                updateEnvironmentDisplay()
                            }
                        }
                        
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
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_countdown, null)
        
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        val tvCountdown = dialogView.findViewById<TextView>(R.id.tvCountdown)
        val tvModeInfo = dialogView.findViewById<TextView>(R.id.tvModeInfo)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelCountdown)
        
        tvModeInfo.text = when(mode) {
            MeasurementMode.ALL -> getString(R.string.drag_measuring_all)
            MeasurementMode.ZERO_TO_100 -> getString(R.string.drag_measuring_0to100)
            MeasurementMode.ZERO_TO_200 -> getString(R.string.drag_measuring_0to200)
            MeasurementMode.HUNDRED_TO_200 -> getString(R.string.drag_measuring_100to200)
            MeasurementMode.QUARTER_MILE -> getString(R.string.drag_measuring_402m)
        }
        
        countdownDialog = dialog
        startLocationUpdates()
        preStartDragService(mode)
        
        countdownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt() + 1
                val progress = ((5000 - millisUntilFinished) * 100 / 5000).toInt()
                
                tvCountdown.text = seconds.toString()
                progressBar.progress = progress
                soundManager.speakCountdown(seconds)
            }
            
            override fun onFinish() {
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
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    100,
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
            locationManager.removeUpdates(this)
        } catch (e: SecurityException) {
            // Handle permission error
        }
    }
    
    private fun startCountdownAndSession() {
        showMeasurementModeDialog()
    }

    private fun verifyCalibrationAndStartSession() {
        val selectedProfileId = getCurrentProfileId()
        if (selectedProfileId != -1L) {
            DragCalibration.setProfile(selectedProfileId)
        }

        if (!DragCalibration.isCalibrated) {
            AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
                .setTitle("Calibration Required")
                .setMessage("Drag start needs calibration. Open calibration now?")
                .setPositiveButton("Open Calibration") { _, _ -> openCalibrationScreen() }
                .setNegativeButton(getString(R.string.dialog_cancel_button), null)
                .show()
        } else {
            startCountdownAndSession()
        }
    }

    private fun openCalibrationScreen() {
        val selectedProfileId = getCurrentProfileId()
        val calibrationIntent = Intent(requireContext(), DragCalibrationActivity::class.java).apply {
            putExtra("PROFILE_ID", selectedProfileId)
            putExtra("IS_FIRST_PROFILE", false)
            putExtra("IS_NEW_PROFILE", false)
            putExtra("IS_FIRST_LAUNCH", false)
        }
        startActivity(calibrationIntent)
    }
    
    private fun showMeasurementModeDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_measurement_mode, null)
        
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        val btnMeasureAll = dialogView.findViewById<Button>(R.id.btnMeasureAll)
        val btn0to100 = dialogView.findViewById<Button>(R.id.btn0to100)
        val btn0to200 = dialogView.findViewById<Button>(R.id.btn0to200)
        val btn100to200 = dialogView.findViewById<Button>(R.id.btn100to200)
        val btn0to402 = dialogView.findViewById<Button>(R.id.btn0to402)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelMode)
        
        val speedUnit = UnitsManager.getSpeedUnit(requireContext())
        val speed100 = UnitsManager.convertSpeed(100f, speedUnit).toInt()
        val speed200 = UnitsManager.convertSpeed(200f, speedUnit).toInt()
        btn0to100.text = "0-$speed100"
        btn0to200.text = "0-$speed200"
        btn100to200.text = "$speed100-$speed200"
        btn0to402.text = "0-${UnitsManager.getQuarterMileDistance(requireContext())}"
        
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
        val serviceIntent = Intent(requireContext(), ForegroundService::class.java).apply {
            putExtra("profileId", currentProfile?.id)
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            requireContext().startForegroundService(serviceIntent)
        } else {
            requireContext().startService(serviceIntent)
        }
    }
    
    private fun startDragRun(mode: MeasurementMode) {
        val intent = Intent(requireContext(), DragRunPageActivity::class.java)
        intent.putExtra("PROFILE_ID", currentProfile?.id)
        intent.putExtra("TEMPERATURE", currentTemperature)
        intent.putExtra("ALTITUDE", currentAltitude)
        intent.putExtra("MEASUREMENT_MODE", mode.name)
        
        if (currentLatitude != null && currentLongitude != null) {
            intent.putExtra("LATITUDE", currentLatitude)
            intent.putExtra("LONGITUDE", currentLongitude)
        }
        
        startActivityForResult(intent, REQUEST_DRAG_RUN)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_DRAG_RUN -> {
                if (resultCode == Activity.RESULT_OK) {
                    loadCurrentProfile()
                    loadSessions()
                    dragAdapter.notifyDataSetChanged()
                    updateNoDataVisibility()
                    fetchWeatherAndElevation()
                }
            }
        }
    }
    
    private fun showDeleteConfirmation(session: DragSession) {
        val deleteDialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle(getString(R.string.delete_session_title))
            .setMessage(getString(R.string.delete_session_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                DragStorage.deleteDragSession(requireContext(), session.id)
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
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestLocationPermission() {
        requestPermissions(
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
                verifyCalibrationAndStartSession()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(
                sessionUpdateReceiver,
                IntentFilter("SESSION_UPDATED"),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            requireContext().registerReceiver(sessionUpdateReceiver, IntentFilter("SESSION_UPDATED"))
        }
        
        loadCurrentProfile()
        
        currentProfile?.let { profile ->
            DragCalibration.setProfile(profile.id)
        }
        
        loadSessions()
        dragAdapter.notifyDataSetChanged()
        updateNoDataVisibility()
        
        loadCachedWeatherData()
        updateEnvironmentDisplay()
        
        // Обновяваме профила при връщане на екрана
        loadProfileInfo()
        
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
    
    override fun onPause() {
        super.onPause()
        locationManager.removeUpdates(this)
        try {
            requireContext().unregisterReceiver(sessionUpdateReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        locationManager.removeUpdates(this)
        sensorManager.unregisterListener(this)
        countdownTimer?.cancel()
        countdownDialog?.dismiss()
        soundManager.release()
        
        // Важно: отписваме се, за да няма memory leaks
        profilePrefs.unregisterOnSharedPreferenceChangeListener(profileChangeListener)
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                if (currentTemperature == null) {
                    currentTemperature = event.values[0]
                    updateEnvironmentDisplay()
                }
            }
            Sensor.TYPE_PRESSURE -> {
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
        currentLatitude = location.latitude
        currentLongitude = location.longitude
        
        if (location.hasAltitude() && currentAltitude == null) {
            currentAltitude = location.altitude.toFloat()
            updateEnvironmentDisplay()
        }
    }
    
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    
    // ЕЛЕМЕНТАРНО: Зареждане на модела и снимката от активния профил
    private fun loadProfileInfo() {
        if (!isAdded || view == null) return
        
        val selectedId = ProfileStorage.getSelectedProfileId(requireContext())
        val profiles = ProfileStorage.loadProfiles(requireContext())
        val activeProfile = profiles.find { it.id == selectedId }

        if (activeProfile != null) {
            // 1. Зареждаме модела: "Audi A6" -> "A6"
            val fullName = activeProfile.name.trim()
            val modelName = if (fullName.contains(" ")) {
                fullName.substringAfterLast(" ")
            } else {
                fullName
            }
            tvHeaderModelName.text = modelName
            tvHeaderModelName.setTextColor(android.graphics.Color.WHITE)
            tvHeaderModelName.visibility = View.VISIBLE

            // 2. Зареждаме снимката или показваме иконка
            if (!activeProfile.imagePath.isNullOrEmpty()) {
                val imageFile = java.io.File(requireContext().getExternalFilesDir(null), activeProfile.imagePath)
                if (imageFile.exists()) {
                    // Image is already scaled on disk, just load it
                    val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
                    if (bitmap != null) {
                        ivHeaderProfileImage.setImageBitmap(bitmap)
                        ivHeaderProfileImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                        ivHeaderProfileImage.setPadding(0, 0, 0, 0)
                    } else {
                        showDefaultIcon(activeProfile.vehicleType)
                    }
                } else {
                    showDefaultIcon(activeProfile.vehicleType)
                }
            } else {
                showDefaultIcon(activeProfile.vehicleType)
            }
        } else {
            tvHeaderModelName.text = ""
            showDefaultIcon(Profile.VehicleType.CAR)
        }
    }
    
    private fun showDefaultIcon(type: Profile.VehicleType) {
        val icon = if (type == Profile.VehicleType.CAR) R.drawable.ic_car else R.drawable.ic_motorcycle
        ivHeaderProfileImage.setImageResource(icon)
        ivHeaderProfileImage.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        val padding = (6 * resources.displayMetrics.density).toInt()
        ivHeaderProfileImage.setPadding(padding, padding, padding, padding)
        ivHeaderProfileImage.visibility = View.VISIBLE
    }
}
