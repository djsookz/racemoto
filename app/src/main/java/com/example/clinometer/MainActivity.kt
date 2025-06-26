package com.example.clinometer

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Point
import android.location.Location
import android.os.*
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
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

            // НОВО: Инициализиране на ускорението при свързване
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

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        setContentView(R.layout.activity_main)
        setupMap()

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


        currentAngleText.text = getString(R.string.current_angle, 0)
        maxLeftText.text = getString(R.string.max_left_angle, 0)
        maxRightText.text = getString(R.string.max_right_angle, 0)
        speedText.text = getString(R.string.current_speed, 0)
        maxSpeedText.text = getString(R.string.max_speed, 0)

        // НОВО: Инициализиране на ускорението при стартиране
        updateAccelerationDisplay(ForegroundService.AccelerationData())

        setupButtons()

        if (isServiceRunning()) {
            val serviceIntent = Intent(this, ForegroundService::class.java)
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        handler.post(orientationUpdateRunnable)
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

                // НОВО: Нулиране на дисплея за ускорение
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

                    val accelData = foregroundService?.getAccelerationData()
                        ?: ForegroundService.AccelerationData()
                    val realDuration = foregroundService?.getServiceDuration() ?: 0
                    val time0to100 = accelData.best0to100()
                    val time0to200 = accelData.best0to200()
                    val time100to200 =
                        if (time0to100 > 0 && time0to200 > 0 && time0to200 > time0to100) {
                            time0to200 - time0to100
                        } else -1

                    // Създаваме Race обект с всички данни
                    val race = Race(
                        id = System.currentTimeMillis(),
                        routePoints = emptyList(), // Не изпращаме точките през Intent
                        timestamp = System.currentTimeMillis(),
                        duration = realDuration,
                        absoluteTimestamp = System.currentTimeMillis(),
                        maxLeftAngle = foregroundService?.getMaxLeftAngle() ?: 0f,
                        maxRightAngle = foregroundService?.getMaxRightAngle() ?: 0f,
                        maxSpeed = foregroundService?.getMaxSpeed() ?: 0f,
                        name = null,
                        time0to100 = accelData.best0to100(),
                        time0to200 = accelData.best0to200(),
                        time100to200 = time100to200
                    )

                    // Запазваме race
                    val points = foregroundService?.getRoutePoints() ?: emptyList()
                    RouteStorage.saveRoutePoints(this, race.id, points)

                    // Запазване на метаданните
                    val allRaces = RouteStorage.loadRaces(this).toMutableList()
                    allRaces.add(race)
                    RouteStorage.saveRaces(this, allRaces)

                    // Предаване само на ID
                    val intent = Intent(this, MapActivity::class.java).apply {
                        putExtra("RACE_ID", race.id)
                    }
                    startActivity(intent)

                } catch (e: Exception) {
                    Log.e("MainActivity", "Error saving race", e)
                    AlertDialog.Builder(this)
                        .setTitle("Грешка")
                        .setMessage("Грешка при запазване: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                } finally {
                    // СПИРАНЕ НА СЕРВИЗА ВЪВ ВСЯКЪВ СЛУЧАЙ
                    if (serviceBound) {
                        unbindService(serviceConnection)
                        stopService(Intent(this, ForegroundService::class.java))
                        serviceBound = false
                    }
                    finish()  // ЗАТВАРЯНЕ НА ТЕКУЩАТА АКТИВНОСТ
                }

                unbindService(serviceConnection)
                stopService(Intent(this, ForegroundService::class.java))
                serviceBound = false

                startActivity(intent)
                finish()
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


    // НОВА ИМПЛЕМЕНТАЦИЯ: Показване на ускорението със зелени резултати
    // В MainActivity.kt - само функцията updateAccelerationDisplay трябва да се замени:

    // В MainActivity
    private fun updateAccelerationDisplay(accelData: ForegroundService.AccelerationData) {
        fun formatTime(timeNanos: Long): String {
            return if (timeNanos > 0) "%.3f".format(timeNanos / 1_000_000_000.0) else "--"
        }

        fun getDisplayText(prefix: String, bestTime: Long, isTracking: Boolean): SpannableString {
            val timeStr = formatTime(bestTime)
            val hasValidTime = bestTime > 0

            val fullText = when {
                isTracking && hasValidTime -> "$prefix: $timeStr⏱️" // Показва време + икона при активно проследяване
                isTracking -> "$prefix: ⏱️"                         // Само икона при активно проследяване без време
                hasValidTime -> "$prefix: $timeStr"                 // Само време при завършено ускорение
                else -> "$prefix: -"                                // Няма резултат
            }

            val spannable = SpannableString(fullText)

            if (hasValidTime) {
                // Намираме позицията на времето в текста
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

        // ВИНАГИ показваме най-добрите резултати, дори по време на активно проследяване
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
            // Show timer only when 0-200 is being tracked
            isTracking = accelData.isTracking0to200
        )

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

            // НОВО: Актуализиране на показването на ускорението
            updateAccelerationDisplay(service.getAccelerationData())
        }
    }

    private fun updateMapWithLocation(geoPoint: GeoPoint, bearing: Float, currentSpeed: Float) {
        // ВИНАГИ центрираме картата върху текущата позиция
        mapView.controller.setCenter(geoPoint)

        // Добавяме точка към маршрута само ако е на достатъчно голямо разстояние
        if (routeOverlay.points.isEmpty() ||
            geoPoint.distanceToAsDouble(routeOverlay.points.last()) > 2) {
            routeOverlay.points.add(geoPoint)
        }

        // Обновяваме ориентацията само ако се движим с достатъчно голяма скорост
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
                .setTitle("Изход от сесия?")
                .setMessage("Сигурни ли сте, че искате да излезете? Данните ще бъдат изгубени.")
                .setPositiveButton("Да") { _, _ ->
                    navigateToRacesActivity()
                }
                .setNegativeButton("Не") { dialog, _ ->
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