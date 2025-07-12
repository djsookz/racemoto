package com.example.clinometer

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Chronometer
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private var serviceBound = false
    private var foregroundService: ForegroundService? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastUpdateTime = 0L
    private val UPDATE_INTERVAL = 16L

    private var targetAngle = 0f
    private var filteredTargetAngle = 0f
    private var currentAngle = 0f
    private var currentMapOrientation = 0f
    private var targetMapOrientation = 0f
    private var lastBearing = 0f
    private var isFirstLocationSet = false
    private var userPosition: GeoPoint? = null

    private lateinit var currentProfile: Profile
    private lateinit var mapView: MapView
    private lateinit var routeOverlay: Polyline
    private lateinit var myLocationOverlay: MyLocationNewOverlay

    private lateinit var gaugeView: GaugeView
    private lateinit var currentAngleText: TextView
    private lateinit var maxLeftText: TextView
    private lateinit var maxRightText: TextView
    private lateinit var speedText: TextView
    private lateinit var maxSpeedText: TextView
    private lateinit var resetButton: Button
    private lateinit var stopButton: Button
    private lateinit var chronometer: Chronometer
    private lateinit var tvZeroTo100: TextView
    private lateinit var tvZeroTo200: TextView
    private lateinit var tvHundredTo200: TextView

    private fun checkLocationPermission(): Boolean {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(
                this,
                requiredPermissions,
                1000
            )
            return false
        }
        return true
    }

    private val orientationUpdateRunnable = object : Runnable {
        override fun run() {
            updateMapOrientation()
            handler.postDelayed(this, 50)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ForegroundService.LocalBinder
            foregroundService = binder.getService()
            serviceBound = true
            startChronometer()
            startSmoothUpdates()

            updateAccelerationDisplay(foregroundService?.getAccelerationData() ?: ForegroundService.AccelerationData())
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            foregroundService = null
            stopSmoothUpdates()
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime >= UPDATE_INTERVAL) {
                updateUIFromService()
                updateGaugeAnimation()
                lastUpdateTime = currentTime
            }
            handler.post(this)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Получаваме избрания профил от Intent
        currentProfile = intent.getSerializableExtra("SELECTED_PROFILE") as? Profile
            ?: Profile(name = "My profile", vehicleType = Profile.VehicleType.MOTORCYCLE)

        // Инициализираме всички UI компоненти
        chronometer = findViewById(R.id.chronometer)
        gaugeView = findViewById(R.id.gaugeView)
        currentAngleText = findViewById(R.id.currentAngleText)
        maxLeftText = findViewById(R.id.maxLeftText)
        maxRightText = findViewById(R.id.maxRightText)
        speedText = findViewById(R.id.speedText)
        maxSpeedText = findViewById(R.id.maxSpeedText)
        resetButton = findViewById(R.id.btnReset)
        stopButton = findViewById(R.id.btnStop)
        tvZeroTo100 = findViewById(R.id.tvZeroTo100)
        tvZeroTo200 = findViewById(R.id.tvZeroTo200)
        tvHundredTo200 = findViewById(R.id.tvHundredTo200)

        // 1. grab prefs
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val keepOn = prefs.getBoolean("always_on_display", false)


// 2. conditionally add or clear the flag
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        prefs.registerOnSharedPreferenceChangeListener { shared, key ->
            if (key == "always_on_display") {
                val on = shared.getBoolean(key, false)
                if (on) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
        
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        setupMap()

        currentAngleText.text = getString(R.string.current_angle, 0)
        maxLeftText.text = getString(R.string.max_left_angle, 0)
        maxRightText.text = getString(R.string.max_right_angle, 0)
        speedText.text = getString(R.string.current_speed, 0)
        maxSpeedText.text = getString(R.string.max_speed, 0)

        updateAccelerationDisplay(ForegroundService.AccelerationData())
        setupButtons()

        // Актуализираме UI според типа на профила
        updateUIForProfile()

        if (isServiceRunning()) {
            val serviceIntent = Intent(this, ForegroundService::class.java)
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        handler.post(orientationUpdateRunnable)
    }


    private fun updateUIForProfile() {
        val angleElements = listOf(gaugeView, currentAngleText, maxLeftText, maxRightText)
        val isMotorcycle = currentProfile.vehicleType == Profile.VehicleType.MOTORCYCLE

        if (isMotorcycle) {
            // Показване на ъглови елементи с плавна анимация
            angleElements.forEach { view ->
                view.visibility = View.VISIBLE
                view.alpha = 0f
                view.animate().alpha(1f).setDuration(300).start()
            }
        } else {
            // Плавно скриване на ъглови елементи
            angleElements.forEach { view ->
                view.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction { view.visibility = View.GONE }
                    .start()
            }
        }

        // Допълнително: Ако имате други елементи, които зависят от типа превозно средство
        if (isMotorcycle) {
            // Настройки специфични за мотоциклети
        } else {
            // Настройки специфични за автомобили
            // Пример: Показване на допълнителни данни за автомобил
            tvZeroTo100.visibility = View.VISIBLE
            tvZeroTo200.visibility = View.VISIBLE
            tvHundredTo200.visibility = View.VISIBLE
        }
    }

    private fun setupMap() {
        mapView = findViewById(R.id.mapView)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(false)
        mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

        mapView.controller.setZoom(18.0)
        mapView.isTilesScaledToDpi = true
        mapView.isHorizontalMapRepetitionEnabled = false
        mapView.isVerticalMapRepetitionEnabled = false

        myLocationOverlay = MyLocationNewOverlay(mapView).apply {
            enableMyLocation()
            setDrawAccuracyEnabled(false)
        }
        mapView.overlays.add(myLocationOverlay)

        routeOverlay = Polyline().apply {
            outlinePaint.strokeWidth = 12f
            outlinePaint.color = Color.BLUE
            outlinePaint.alpha = 200
        }
        mapView.overlays.add(routeOverlay)
    }

    private fun setupButtons() {
        resetButton.setOnClickListener {
            if (checkLocationPermission()) {
                val serviceIntent = Intent(this, ForegroundService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
                bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

                foregroundService?.resetData()

                maxLeftText.text = getString(R.string.max_left_angle, 0)
                maxRightText.text = getString(R.string.max_right_angle, 0)
                maxSpeedText.text = getString(R.string.max_speed, 0)
                gaugeView.resetMaxima()

                updateAccelerationDisplay(ForegroundService.AccelerationData())

                chronometer.base = SystemClock.elapsedRealtime()
                chronometer.start()

                targetAngle = 0f
                filteredTargetAngle = 0f
                currentAngle = 0f

                routeOverlay.points.clear()
                mapView.invalidate()
                isFirstLocationSet = false
                userPosition = null
                currentMapOrientation = 0f
                targetMapOrientation = 0f
            }
        }

        stopButton.setOnClickListener {
            if (serviceBound) {
                try {
                    val distance = foregroundService?.getTotalDistanceKm() ?: 0.0
                    val accelData = foregroundService?.getAccelerationData()
                        ?: ForegroundService.AccelerationData()
                    val realDuration = foregroundService?.getServiceDuration() ?: 0
                    val time0to100 = accelData.best0to100()
                    val time0to200 = accelData.best0to200()
                    val time100to200 = if (time0to100 > 0 && time0to200 > 0 && time0to200 > time0to100) {
                        time0to200 - time0to100
                    } else -1

                    val race = Race(
                        profileId = currentProfile.id,
                        id = System.currentTimeMillis(),
                        routePoints = emptyList(),
                        timestamp = System.currentTimeMillis(),
                        duration = realDuration,
                        absoluteTimestamp = System.currentTimeMillis(),
                        maxLeftAngle = foregroundService?.getMaxLeftAngle() ?: 0f,
                        maxRightAngle = foregroundService?.getMaxRightAngle() ?: 0f,
                        maxSpeed = foregroundService?.getMaxSpeed() ?: 0f,
                        name = null,
                        distance = distance,
                        time0to100 = accelData.best0to100(),
                        time0to200 = accelData.best0to200(),
                        time100to200 = time100to200
                    )

                    val points = foregroundService?.getRoutePoints() ?: emptyList()
                    RouteStorage.saveRoutePoints(this, race.id, points)

                    val allRaces = RouteStorage.loadRaces(this).toMutableList()
                    allRaces.add(race)
                    RouteStorage.saveRaces(this, allRaces)

                    val mapIntent = Intent(this, MapActivity::class.java).apply {
                        putExtra("RACE_ID", race.id)
                    }

                    // СПИРАНЕ НА СЕРВИЗА И ЗАТВАРЯНЕ
                    try {
                        unbindService(serviceConnection)
                        stopService(Intent(this, ForegroundService::class.java))
                    } finally {
                        serviceBound = false
                    }

                    startActivity(mapIntent)
                    finish()

                } catch (e: Exception) {
                    Log.e("MainActivity", "Error saving race", e)
                    AlertDialog.Builder(this)
                        .setTitle("Error")
                        .setMessage("Error saving the race: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        myLocationOverlay.enableMyLocation()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        myLocationOverlay.disableMyLocation()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSmoothUpdates()
        handler.removeCallbacks(orientationUpdateRunnable)
        if (serviceBound) {
            unbindService(serviceConnection)
        }
    }

    private fun startSmoothUpdates() {
        lastUpdateTime = System.currentTimeMillis()
        handler.post(updateRunnable)
    }

    private fun stopSmoothUpdates() {
        handler.removeCallbacks(updateRunnable)
    }

    private fun startChronometer() {
        val baseTime = foregroundService?.getStartTime() ?: SystemClock.elapsedRealtime()
        chronometer.base = baseTime
        chronometer.start()
    }

    private fun updateAccelerationDisplay(accelData: ForegroundService.AccelerationData) {
        fun formatTime(timeNanos: Long): String {
            return if (timeNanos > 0) "%.3f".format(timeNanos / 1_000_000_000.0) else "--"
        }

        fun getDisplayText(prefix: String, bestTime: Long, isTracking: Boolean): SpannableString {
            val timeStr = formatTime(bestTime)
            val hasValidTime = bestTime > 0

            val fullText = when {
                isTracking && hasValidTime -> "$prefix: $timeStr⏱️"
                isTracking -> "$prefix: ⏱️"
                hasValidTime -> "$prefix: $timeStr"
                else -> "$prefix: -"
            }

            val spannable = SpannableString(fullText)

            if (hasValidTime) {
                val startIndex = fullText.indexOf(timeStr)
                if (startIndex != -1) {
                    val endIndex = startIndex + timeStr.length
                    spannable.setSpan(
                        ForegroundColorSpan(Color.GREEN),
                        startIndex,
                        endIndex,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            return spannable
        }

        val time100to200 = if (accelData.best0to200() > 0 && accelData.best0to100() > 0) {
            val total = accelData.best0to200()
            val firstPart = accelData.best0to100()
            if (total > firstPart) total - firstPart else -1
        } else -1

        tvZeroTo100.text = getDisplayText(
            "0-100",
            accelData.best0to100(),
            accelData.isTracking0to100
        )

        tvZeroTo200.text = getDisplayText(
            "0-200",
            accelData.best0to200(),
            accelData.isTracking0to200
        )

        tvHundredTo200.text = getDisplayText(
            "100-200",
            time100to200,
            isTracking = accelData.isTracking0to200
        )

        if (accelData.best0to100() > 0 && (currentProfile.best0to100 == 0L || accelData.best0to100() < currentProfile.best0to100)) {
            currentProfile.best0to100 = accelData.best0to100()
        }
        if (accelData.best0to200() > 0 && (currentProfile.best0to200 == 0L || accelData.best0to200() < currentProfile.best0to200)) {
            currentProfile.best0to200 = accelData.best0to200()
        }

        val profiles = ProfileStorage.loadProfiles(this)
        profiles.find { it.id == currentProfile.id }?.apply {
            best0to100 = currentProfile.best0to100
            best0to200 = currentProfile.best0to200
            maxSpeed = currentProfile.maxSpeed
        }
        ProfileStorage.saveProfiles(this, profiles)
    }

    private fun updateUIFromService() {
        foregroundService?.let { service ->
            val newTarget = service.getCurrentAngle()

            filteredTargetAngle += (newTarget - filteredTargetAngle) * 0.3f
            targetAngle = filteredTargetAngle

            if (abs(newTarget - currentAngle) > 0.2f) {
                currentAngleText.text = getString(R.string.current_angle, newTarget.toInt())
            }

            val currentSpeed = service.getCurrentSpeed()
            speedText.text = getString(R.string.current_speed, currentSpeed.toInt())
            maxLeftText.text = getString(R.string.max_left_angle, service.getMaxLeftAngle().toInt())
            maxRightText.text = getString(R.string.max_right_angle, service.getMaxRightAngle().toInt())
            maxSpeedText.text = getString(R.string.max_speed, service.getMaxSpeed().toInt())

            gaugeView.maxLeftAngle = service.getMaxLeftAngle()
            gaugeView.maxRightAngle = service.getMaxRightAngle()

            val lastLocation = service.getLastLocation()
            lastLocation?.let {
                val geoPoint = GeoPoint(it.latitude, it.longitude)
                updateMapWithLocation(geoPoint, it.bearing, service.getCurrentSpeed())
            }

            updateAccelerationDisplay(service.getAccelerationData())
        }
    }

    private fun updateMapWithLocation(geoPoint: GeoPoint, bearing: Float, currentSpeed: Float) {
        mapView.controller.setCenter(geoPoint)

        if (routeOverlay.points.isEmpty() ||
            geoPoint.distanceToAsDouble(routeOverlay.points.last()) > 2) {
            routeOverlay.points.add(geoPoint)
        }

        if (currentSpeed > 2) {
            val smoothedBearing = smoothBearing(bearing, lastBearing, 0.2f)
            targetMapOrientation = -smoothedBearing
            lastBearing = smoothedBearing
        }

        mapView.invalidate()
    }

    private fun smoothBearing(newBearing: Float, oldBearing: Float, factor: Float): Float {
        var diff = newBearing - oldBearing

        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f

        var result = oldBearing + diff * factor

        while (result > 360f) result -= 360f
        while (result < 0f) result += 360f

        return result
    }

    private fun updateMapOrientation() {
        var diff = targetMapOrientation - currentMapOrientation

        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f

        if (abs(diff) > 1f) {
            currentMapOrientation += diff * 0.1f

            while (currentMapOrientation > 360f) currentMapOrientation -= 360f
            while (currentMapOrientation < 0f) currentMapOrientation += 360f

            mapView.mapOrientation = currentMapOrientation
        }
    }

    private fun updateGaugeAnimation() {
        val diff = targetAngle - currentAngle
        currentAngle += diff * 0.8f

        if (abs(diff) < 0.05f) {
            currentAngle = targetAngle
        }

        gaugeView.angle = currentAngle
        gaugeView.invalidate()
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == ForegroundService::class.java.name }
    }

    override fun onBackPressed() {
        if (foregroundService?.getRoutePoints()?.isNotEmpty() == true) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.exit_race_header))
                .setMessage(getString(R.string.exit_race))
                .setPositiveButton(getString(R.string.exit_race_yes)) { _, _ ->
                    navigateToRacesActivity()
                }
                .setNegativeButton(getString(R.string.exit_race_no)) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } else {
            navigateToRacesActivity()
        }
    }

    private fun navigateToRacesActivity() {
        val intent = Intent(this, RacesActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putFloat("currentMapOrientation", currentMapOrientation)
        outState.putBoolean("isFirstLocationSet", isFirstLocationSet)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        currentMapOrientation = savedInstanceState.getFloat("currentMapOrientation", 0f)
    }
}