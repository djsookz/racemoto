package com.revix.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import com.revix.app.data.CalibrationReminderStore
import com.revix.app.data.ProfileStorage
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.revix.app.settings.LanguageManager
import com.revix.app.main.MainContainerActivity
import com.revix.app.main.tour.FeatureTourStore
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.max

class DragCalibrationActivity : AppCompatActivity(), SensorEventListener {
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }
    
    private lateinit var tvPortraitStatus: TextView
    private lateinit var tvPortraitDate: TextView
    private lateinit var btnCalibratePortrait: Button
    private lateinit var btnClearPortrait: Button
    
    private lateinit var tvLandscapeStatus: TextView
    private lateinit var tvLandscapeDate: TextView
    private lateinit var btnCalibrateLandscape: Button
    private lateinit var btnClearLandscape: Button

    private lateinit var cardPortraitCalibration: MaterialCardView
    private lateinit var cardLandscapeCalibration: MaterialCardView
    private var highlightStrokeWidthPx: Int = 0
    private var defaultStrokeWidthPx: Int = 0
    
    private lateinit var btnClearAll: Button
    private lateinit var btnCancel: Button
    private lateinit var btnCalibrateLater: Button
    private lateinit var btnContinue: Button

    private lateinit var tvCalibrationStep1: TextView
    private lateinit var tvCalibrationStep2: TextView
    private lateinit var tvCalibrationStep3: TextView
    private lateinit var tvCalibrationStep4: TextView
    private lateinit var tvCalibrationStep5: TextView
    private lateinit var tvCalibrationWarning: TextView
    
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private var gyroSumX = 0.0
    private var gyroSumY = 0.0
    private var gyroSumZ = 0.0
    private var gyroSampleCount = 0
    private val MIN_GYRO_BIAS_SAMPLES = 40
    
    private val rawAcceleration = FloatArray(3)  // RAW accelerometer данни (gravity + acceleration)
    
    // UNIVERSAL GRAVITY-BASED CALIBRATION
    private var isCalibrating = false
    private var calibrationStartTime = 0L

    // FORWARD събиране (20 samples = ~200ms за бърз и стабилен vector!)
    private val forwardSamplesX = mutableListOf<Float>()
    private val forwardSamplesY = mutableListOf<Float>()
    private val forwardSamplesZ = mutableListOf<Float>()
    private val FORWARD_SAMPLES_NEEDED = 20  // 20 samples @ 100Hz = 200ms (баланс между скорост и точност!)
    private val CAR_FORWARD_SAMPLES_NEEDED = 12  // ~0.25s pulse; idle cannot hold this continuously
    private var gravityVector = FloatArray(3)  // От phase 1
    
    // DEPRECATED старата логика (backward compatibility)
    private var calibratingOrientation: String = ""
    private var isLearningForward = false
    private var calibrationSamples = mutableListOf<Float>()
    private val noiseBaselineX = mutableListOf<Float>()
    private val noiseBaselineY = mutableListOf<Float>()
    private val noiseBaselineZ = mutableListOf<Float>()
    private var baselineCollected = false
    private val BASELINE_DURATION_MS = 5000L
    private var baselineVector = FloatArray(3)
    private var maxVibrX = 0f
    private var maxVibrY = 0f
    private var maxVibrZ = 0f
    private var baselineNoiseRms = 0f
    private var forwardLearningStartTime = 0L
    private var forwardReferenceUnit: FloatArray? = null
    private var forwardRejectedDirectionSamples = 0
    private var forwardDotAccumulator = 0f
    private var forwardDotCount = 0
    private var forwardMagnitudeAccumulator = 0f
    private var forwardMagnitudeSquaredAccumulator = 0f
    private val CAR_FORWARD_PHASE_TIMEOUT_MS = 15_000L
    private val FORWARD_DIRECTION_MIN_COS = 0.75f
    
    private var profileId: Long = -1L
    private var isFirstProfile: Boolean = false
    private var isNewProfile: Boolean = false
    private var isFirstLaunch: Boolean = false
    private var calibrationCompleted: Boolean = false  // Flag за успешна калибрация
    private var calibrationDeferred: Boolean = false

    // GPS-потвърдено каране напред — САМО мотоциклет (автомобил = оригиналната бърза accel логика).
    private var isMotorcycleProfile: Boolean = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationManager: LocationManager? = null
    private var calibrationLocationCallback: LocationCallback? = null
    private var locationUpdatesActive: Boolean = false
    private var pendingCalibrationOrientation: String? = null
    private var gpsAnchorLocation: Location? = null
    private var gpsLastAcceptedLocation: Location? = null
    private var gpsAcceptedPoints = 0
    private var gpsAcceptedSegments = 0
    private var gpsTotalDistanceMeters = 0f
    private var gpsDirectionSumEast = 0.0
    private var gpsDirectionSumNorth = 0.0
    private var gpsForwardReady = false
    /** True while recent GPS updates show straight riding at calibration speed. */
    private var gpsMotionReliable = false
    private var gpsSpeedGraceStrikes = 0
    private var gpsDirectionGraceStrikes = 0
    // Tuned to match on-screen guidance (~20 m at 15+ km/h) without being hypersensitive to GPS noise.
    private val GPS_MIN_TOTAL_DISTANCE_M = 16f
    private val GPS_MIN_POINTS = 5
    private val GPS_MIN_SPEED_MPS = 4.0f // ~14.4 km/h
    private val GPS_MIN_SEGMENT_DISTANCE_M = 1.2f
    private val GPS_MAX_HORIZONTAL_ACCURACY_M = 15f
    private val GPS_DIRECTION_DOT_MIN = 0.90 // ~25°
    private val GPS_SPEED_GRACE_BEFORE_RESET = 3
    private val GPS_DIRECTION_GRACE_BEFORE_RESET = 2
    private val FORWARD_GPS_MAX_DURATION_MS = 45_000L
    private val MAX_FORWARD_SAMPLES = 800
    private val EARTH_RADIUS_M = 6_371_000.0
    private val LOCATION_PERMISSION_REQUEST_CODE = 1402
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drag_calibration)
        setSupportActionBar(findViewById(R.id.calibrationToolbar))
        applySystemBarsPaddingToRoot()

        window.statusBarColor = ContextCompat.getColor(this, R.color.background_primary)
        
        profileId = savedInstanceState?.getLong("PROFILE_ID")
            ?: intent.getLongExtra("PROFILE_ID", -1L)
        isFirstProfile = savedInstanceState?.getBoolean("IS_FIRST_PROFILE")
            ?: intent.getBooleanExtra("IS_FIRST_PROFILE", false)
        isNewProfile = savedInstanceState?.getBoolean("IS_NEW_PROFILE")
            ?: intent.getBooleanExtra("IS_NEW_PROFILE", false)
        isFirstLaunch = savedInstanceState?.getBoolean("IS_FIRST_LAUNCH")
            ?: intent.getBooleanExtra("IS_FIRST_LAUNCH", false)
        calibrationCompleted = savedInstanceState?.getBoolean("calibration_completed") ?: false
        calibrationDeferred = savedInstanceState?.getBoolean("calibration_deferred") ?: false
        
        DragCalibration.setProfile(profileId)
        if (DragCalibration.hasAnyCalibration()) {
            calibrationCompleted = true
        }

        isMotorcycleProfile = ProfileStorage.loadProfiles(this)
            .find { it.id == profileId }?.vehicleType == Profile.VehicleType.MOTORCYCLE
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.calibration_title)
        supportActionBar?.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, R.color.background_primary)))
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.calibrationToolbar).apply {
            setNavigationIcon(R.drawable.ic_arrow_back)
            navigationIcon?.mutate()?.setTint(ContextCompat.getColor(this@DragCalibrationActivity, android.R.color.white))
        }
        
        // Ако е първи профил или нов профил от Garage - скриваме Back бутона
        if (isFirstProfile || isNewProfile) {
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
            findViewById<androidx.appcompat.widget.Toolbar>(R.id.calibrationToolbar).navigationIcon = null
        }
        
        initializeViews()
        initializeSensors()
        initializeLocation()
        updateUI()
        applyOrientationGating()
    }

    override fun onResume() {
        super.onResume()
        if (!isCalibrating) {
            applyOrientationGating()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!isCalibrating) {
            applyOrientationGating()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("PROFILE_ID", profileId)
        outState.putBoolean("IS_FIRST_PROFILE", isFirstProfile)
        outState.putBoolean("IS_NEW_PROFILE", isNewProfile)
        outState.putBoolean("IS_FIRST_LAUNCH", isFirstLaunch)
        outState.putBoolean("calibration_completed", calibrationCompleted)
        outState.putBoolean("calibration_deferred", calibrationDeferred)
    }
    
    private fun initializeViews() {
        tvPortraitStatus = findViewById(R.id.tvPortraitStatus)
        tvPortraitDate = findViewById(R.id.tvPortraitDate)
        btnCalibratePortrait = findViewById(R.id.btnCalibratePortrait)
        btnClearPortrait = findViewById(R.id.btnClearPortrait)
        
        tvLandscapeStatus = findViewById(R.id.tvLandscapeStatus)
        tvLandscapeDate = findViewById(R.id.tvLandscapeDate)
        btnCalibrateLandscape = findViewById(R.id.btnCalibrateLandscape)
        btnClearLandscape = findViewById(R.id.btnClearLandscape)
        
        btnClearAll = findViewById(R.id.btnClearAll)
        btnCancel = findViewById(R.id.btnCancel)
        btnCalibrateLater = findViewById(R.id.btnCalibrateLater)
        btnContinue = findViewById(R.id.btnContinue)

        cardPortraitCalibration = findViewById(R.id.cardPortraitCalibration)
        cardLandscapeCalibration = findViewById(R.id.cardLandscapeCalibration)
        tvCalibrationStep1 = findViewById(R.id.tvCalibrationStep1)
        tvCalibrationStep2 = findViewById(R.id.tvCalibrationStep2)
        tvCalibrationStep3 = findViewById(R.id.tvCalibrationStep3)
        tvCalibrationStep4 = findViewById(R.id.tvCalibrationStep4)
        tvCalibrationStep5 = findViewById(R.id.tvCalibrationStep5)
        tvCalibrationWarning = findViewById(R.id.tvCalibrationWarning)
        val density = resources.displayMetrics.density
        defaultStrokeWidthPx = (1f * density).toInt().coerceAtLeast(1)
        highlightStrokeWidthPx = (3f * density).toInt().coerceAtLeast(3)
        
        // Показваме името на профила в title
        val profiles = ProfileStorage.loadProfiles(this)
        val profileName = profiles.find { it.id == profileId }?.name ?: getString(R.string.garage_no_vehicle)
        supportActionBar?.title = "${getString(R.string.calibration_title)} - $profileName"
        
        btnCalibratePortrait.setOnClickListener {
            startCalibration("portrait")
        }
        
        btnClearPortrait.setOnClickListener {
            DragCalibration.clearOrientation(false)
            MotionCalibrationStore.clearSnapshot(this, profileId, isLandscape = false)
            LeanCalibrationStore.clearOrientation(this, profileId, isLandscape = false)
            updateUI()
            // Принудително обновяване на Continue бутона след изтриване
            if (isFirstProfile || isNewProfile) {
                btnContinue.isEnabled = DragCalibration.hasAnyCalibration()
            }
        }
        
        btnCalibrateLandscape.setOnClickListener {
            startCalibration("landscape")
        }
        
        btnClearLandscape.setOnClickListener {
            DragCalibration.clearOrientation(true)
            MotionCalibrationStore.clearSnapshot(this, profileId, isLandscape = true)
            LeanCalibrationStore.clearOrientation(this, profileId, isLandscape = true)
            updateUI()
            // Принудително обновяване на Continue бутона след изтриване
            if (isFirstProfile || isNewProfile) {
                btnContinue.isEnabled = DragCalibration.hasAnyCalibration()
            }
        }
        
        // Cancel: keep the profile even without calibration (shown as not calibrated in Garage).
        btnCancel.setOnClickListener {
            if ((isFirstProfile || isNewProfile) && !DragCalibration.hasAnyCalibration()) {
                CalibrationReminderStore.markDragCalibrationDeferred(this, profileId)
                calibrationDeferred = true
                if (isFirstProfile || isFirstLaunch) {
                    FeatureTourStore.markPending(this)
                    startActivity(Intent(this, MainContainerActivity::class.java).apply {
                        putExtra(MainContainerActivity.EXTRA_NAV_ITEM_ID, R.id.navMap)
                    })
                }
            }
            finish()
        }

        btnCalibrateLater.setOnClickListener {
            deferCalibrationForLater()
        }
        
        // Continue бутон - продължава напред
        btnContinue.setOnClickListener {
            if (DragCalibration.hasAnyCalibration()) {
                finishCalibration()
            }
        }
        
        // Clear All бутон - само за настройки
        btnClearAll.setOnClickListener {
            DragCalibration.clearCalibration()
            MotionCalibrationStore.clearSnapshot(this, profileId, isLandscape = false)
            MotionCalibrationStore.clearSnapshot(this, profileId, isLandscape = true)
            MotionCalibrationStore.clearSnapshot(this, profileId)
            LeanCalibrationStore.clearAll(this, profileId)
            updateUI()
        }
    }
    
    private fun initializeSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    private fun initializeLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
        calibrationLocationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!isCalibrating) return
                result.locations.forEach { onCalibrationLocation(it) }
            }
        }
    }

    private fun useGpsForwardAssist(): Boolean = isMotorcycleProfile && gyroscope != null

    private fun isDeviceLandscape(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun applyOrientationGating() {
        if (isCalibrating) return

        val isLandscape = isDeviceLandscape()
        val highlightColor = ContextCompat.getColor(this, R.color.primary_color)
        val defaultColor = ContextCompat.getColor(this, R.color.stroke_dark)

        cardPortraitCalibration.strokeColor = if (isLandscape) defaultColor else highlightColor
        cardPortraitCalibration.strokeWidth = if (isLandscape) defaultStrokeWidthPx else highlightStrokeWidthPx
        cardLandscapeCalibration.strokeColor = if (isLandscape) highlightColor else defaultColor
        cardLandscapeCalibration.strokeWidth = if (isLandscape) highlightStrokeWidthPx else defaultStrokeWidthPx

        btnCalibratePortrait.isEnabled = !isLandscape
        btnCalibrateLandscape.isEnabled = isLandscape
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun isAnyLocationProviderEnabled(): Boolean = runCatching {
        val lm = locationManager ?: return false
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)

    private fun ensureGpsForwardReady(orientation: String): Boolean {
        if (!hasLocationPermission()) {
            pendingCalibrationOrientation = orientation
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return false
        }
        if (!isAnyLocationProviderEnabled()) {
            Toast.makeText(this, getString(R.string.calibration_error_location_disabled), Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) return
        val pending = pendingCalibrationOrientation
        pendingCalibrationOrientation = null
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED && pending != null) {
            startCalibration(pending)
        } else {
            Toast.makeText(this, getString(R.string.calibration_error_gps_permission), Toast.LENGTH_LONG).show()
        }
    }

    private fun startCalibrationLocationUpdates() {
        if (locationUpdatesActive || !hasLocationPermission()) return
        val callback = calibrationLocationCallback ?: return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100L)
            .setMinUpdateIntervalMillis(100L)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            locationUpdatesActive = true
        } catch (e: SecurityException) {
            Log.e("DragCalibration", "❌ Липсва GPS разрешение за калибрация", e)
        }
    }

    private fun stopCalibrationLocationUpdates() {
        val callback = calibrationLocationCallback ?: return
        if (!locationUpdatesActive) return
        fusedLocationClient.removeLocationUpdates(callback)
        locationUpdatesActive = false
    }

    private fun resetGpsForwardSampling(anchorLocation: Location? = null, clearAccelSamples: Boolean = true) {
        gpsAnchorLocation = anchorLocation?.let { Location(it) }
        gpsLastAcceptedLocation = anchorLocation?.let { Location(it) }
        gpsAcceptedPoints = if (anchorLocation != null) 1 else 0
        gpsAcceptedSegments = 0
        gpsTotalDistanceMeters = 0f
        gpsDirectionSumEast = 0.0
        gpsDirectionSumNorth = 0.0
        gpsForwardReady = false
        gpsMotionReliable = false
        gpsSpeedGraceStrikes = 0
        gpsDirectionGraceStrikes = 0
        // Drop accel samples collected before / during a bad GPS window so forward is GPS-gated.
        if (clearAccelSamples) {
            clearForwardAccelSamples()
        }
    }

    private fun clearForwardAccelSamples() {
        forwardSamplesX.clear()
        forwardSamplesY.clear()
        forwardSamplesZ.clear()
        forwardReferenceUnit = null
        forwardRejectedDirectionSamples = 0
        forwardDotAccumulator = 0f
        forwardDotCount = 0
        forwardMagnitudeAccumulator = 0f
        forwardMagnitudeSquaredAccumulator = 0f
    }

    private fun computeEastNorthDeltaMeters(from: Location, to: Location): DoubleArray {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val lon1 = Math.toRadians(from.longitude)
        val lon2 = Math.toRadians(to.longitude)
        val avgLat = (lat1 + lat2) * 0.5
        val east = (lon2 - lon1) * EARTH_RADIUS_M * cos(avgLat)
        val north = (lat2 - lat1) * EARTH_RADIUS_M
        return doubleArrayOf(east, north)
    }

    private fun onCalibrationLocation(location: Location) {
        if (!isCalibrating || !isLearningForward || !useGpsForwardAssist()) return
        if (location.hasAccuracy() && location.accuracy > GPS_MAX_HORIZONTAL_ACCURACY_M) {
            gpsMotionReliable = false
            return
        }

        val current = Location(location)
        val anchor = gpsAnchorLocation
        if (anchor == null) {
            resetGpsForwardSampling(current, clearAccelSamples = false)
            return
        }
        val lastAccepted = gpsLastAcceptedLocation ?: run {
            gpsLastAcceptedLocation = current
            return
        }

        val delta = computeEastNorthDeltaMeters(lastAccepted, current)
        val segmentDistance = sqrt(delta[0] * delta[0] + delta[1] * delta[1]).toFloat()
        if (segmentDistance < GPS_MIN_SEGMENT_DISTANCE_M) return

        val dtSec = ((current.elapsedRealtimeNanos - lastAccepted.elapsedRealtimeNanos) / 1_000_000_000.0)
            .coerceAtLeast(0.05)
        val derivedSpeed = (segmentDistance / dtSec).toFloat()
        val speedMps = if (current.hasSpeed() && current.speed > 0f) current.speed else derivedSpeed
        if (speedMps < GPS_MIN_SPEED_MPS) {
            gpsMotionReliable = false
            gpsSpeedGraceStrikes++
            // Brief slowdowns no longer wipe progress; only repeated slow GPS updates reset.
            if (gpsSpeedGraceStrikes >= GPS_SPEED_GRACE_BEFORE_RESET) {
                resetGpsForwardSampling(current, clearAccelSamples = true)
            } else {
                gpsLastAcceptedLocation = current
            }
            return
        }
        gpsSpeedGraceStrikes = 0

        val unitEast = delta[0] / segmentDistance
        val unitNorth = delta[1] / segmentDistance
        if (gpsAcceptedSegments > 0) {
            val directionMag = sqrt(gpsDirectionSumEast * gpsDirectionSumEast + gpsDirectionSumNorth * gpsDirectionSumNorth)
            if (directionMag > 0.0001) {
                val avgEast = gpsDirectionSumEast / directionMag
                val avgNorth = gpsDirectionSumNorth / directionMag
                val dot = avgEast * unitEast + avgNorth * unitNorth
                if (dot < GPS_DIRECTION_DOT_MIN) {
                    gpsMotionReliable = false
                    gpsDirectionGraceStrikes++
                    if (gpsDirectionGraceStrikes >= GPS_DIRECTION_GRACE_BEFORE_RESET) {
                        resetGpsForwardSampling(current, clearAccelSamples = true)
                    } else {
                        gpsLastAcceptedLocation = current
                    }
                    return
                }
            }
        }
        gpsDirectionGraceStrikes = 0

        gpsAcceptedSegments++
        gpsAcceptedPoints = gpsAcceptedSegments + 1
        gpsTotalDistanceMeters += segmentDistance
        gpsDirectionSumEast += unitEast * segmentDistance
        gpsDirectionSumNorth += unitNorth * segmentDistance
        gpsLastAcceptedLocation = current
        // GPS now actively gates forward learning: accel samples are accepted only while this is true.
        gpsMotionReliable = true

        val anchorDelta = computeEastNorthDeltaMeters(anchor, current)
        val anchorDistance = sqrt(anchorDelta[0] * anchorDelta[0] + anchorDelta[1] * anchorDelta[1]).toFloat()
        val pathDistance = gpsTotalDistanceMeters
        // Unlock when either chord distance or path length reaches the target with enough points.
        if (gpsAcceptedPoints >= GPS_MIN_POINTS &&
            (anchorDistance >= GPS_MIN_TOTAL_DISTANCE_M || pathDistance >= GPS_MIN_TOTAL_DISTANCE_M)
        ) {
            gpsForwardReady = true
        }
    }

    private fun onForwardGpsTimeout() {
        isCalibrating = false
        isLearningForward = false
        sensorManager.unregisterListener(this)
        stopCalibrationLocationUpdates()
        val statusView = if (calibratingOrientation == "portrait") tvPortraitStatus else tvLandscapeStatus
        statusView.text = "⚠️ ${getString(R.string.calibration_error_gps_forward)}"
        statusView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
        Toast.makeText(this, getString(R.string.calibration_error_gps_forward), Toast.LENGTH_LONG).show()
        updateUI()
        applyOrientationGating()
    }
    
    private fun updateUI() {
        if (DragCalibration.hasAnyCalibration()) {
            CalibrationReminderStore.clearDragCalibrationDeferred(this, profileId)
        }

        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        
        // Portrait UI
        if (DragCalibration.isPortraitCalibrated) {
            tvPortraitStatus.text = "✅ ${getString(R.string.calibration_status_calibrated)}"
            tvPortraitStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            tvPortraitDate.text = getString(R.string.calibration_date_format, dateFormat.format(DragCalibration.portraitCalibrationTime))
            btnCalibratePortrait.text = getString(R.string.calibration_btn_recalibrate)
            btnClearPortrait.isEnabled = true
        } else {
            tvPortraitStatus.text = "⚠️ ${getString(R.string.calibration_status_not_calibrated)}"
            tvPortraitStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            tvPortraitDate.text = getString(R.string.calibration_please_portrait)
            btnCalibratePortrait.text = getString(R.string.calibration_btn_calibrate)
            btnClearPortrait.isEnabled = false
        }
        
        // Landscape UI
        if (DragCalibration.isLandscapeCalibrated) {
            tvLandscapeStatus.text = "✅ ${getString(R.string.calibration_status_calibrated)}"
            tvLandscapeStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            tvLandscapeDate.text = getString(R.string.calibration_date_format, dateFormat.format(DragCalibration.landscapeCalibrationTime))
            btnCalibrateLandscape.text = getString(R.string.calibration_btn_recalibrate)
            btnClearLandscape.isEnabled = true
        } else {
            tvLandscapeStatus.text = "⚠️ ${getString(R.string.calibration_status_not_calibrated)}"
            tvLandscapeStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            tvLandscapeDate.text = getString(R.string.calibration_please_landscape)
            btnCalibrateLandscape.text = getString(R.string.calibration_btn_calibrate)
            btnClearLandscape.isEnabled = false
        }
        
        // Button visibility and state logic
        if (isFirstProfile || isNewProfile) {
            // Първи профил или нов профил от Garage - показваме Continue бутона
            // Cancel бутонът се показва САМО за нов профил от Garage, НЕ за първи профил
            btnCancel.visibility = if (isFirstProfile) android.view.View.GONE else android.view.View.VISIBLE
            btnCalibrateLater.visibility = if (isFirstProfile && !DragCalibration.hasAnyCalibration()) android.view.View.VISIBLE else android.view.View.GONE
            btnContinue.visibility = android.view.View.VISIBLE
            btnClearAll.visibility = android.view.View.GONE
            
            // Continue бутонът е активен само ако има поне една калибрация
            btnContinue.isEnabled = DragCalibration.hasAnyCalibration()
        } else {
            // От настройки - показваме само Clear All бутона
            btnCancel.visibility = android.view.View.GONE
            btnCalibrateLater.visibility = android.view.View.GONE
            btnContinue.visibility = android.view.View.GONE
            btnClearAll.visibility = android.view.View.VISIBLE
            btnClearAll.text = getString(R.string.calibration_btn_clear_all)
            btnClearAll.isEnabled = DragCalibration.hasAnyCalibration()
            btnClearAll.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
        }

        applyOrientationGating()
        applyCalibrationInstructions()
    }

    private fun applyCalibrationInstructions() {
        if (useGpsForwardAssist()) {
            tvCalibrationStep1.setText(R.string.calibration_moto_gyro_step1)
            tvCalibrationStep2.setText(R.string.calibration_moto_gyro_step2)
            tvCalibrationStep3.setText(R.string.calibration_moto_gyro_step3)
            tvCalibrationStep4.setText(R.string.calibration_moto_gyro_step4)
            tvCalibrationStep5.setText(R.string.calibration_moto_gyro_step5)
            tvCalibrationWarning.setText(R.string.calibration_moto_gyro_warning)
        } else {
            tvCalibrationStep1.setText(R.string.calibration_step1)
            tvCalibrationStep2.setText(R.string.calibration_step2)
            tvCalibrationStep3.setText(R.string.calibration_step3)
            tvCalibrationStep4.setText(R.string.calibration_step4)
            tvCalibrationStep5.setText(R.string.calibration_step5)
            tvCalibrationWarning.setText(R.string.calibration_warning)
        }
    }
    
    private fun startCalibration(orientation: String) {
        if (useGpsForwardAssist() && !ensureGpsForwardReady(orientation)) {
            return
        }
        calibratingOrientation = orientation
        isCalibrating = true
        calibrationSamples.clear()
        noiseBaselineX.clear()
        noiseBaselineY.clear()
        noiseBaselineZ.clear()
        
        // 🔥 ВАЖНО: Изчистваме forward samples!
        forwardSamplesX.clear()
        forwardSamplesY.clear()
        forwardSamplesZ.clear()
        forwardReferenceUnit = null
        forwardRejectedDirectionSamples = 0
        forwardDotAccumulator = 0f
        forwardDotCount = 0
        forwardMagnitudeAccumulator = 0f
        forwardMagnitudeSquaredAccumulator = 0f
        
        baselineCollected = false
        isLearningForward = false
        baselineVector = FloatArray(3)
        maxVibrX = 0f
        maxVibrY = 0f
        maxVibrZ = 0f
        baselineNoiseRms = 0f
        gyroSumX = 0.0
        gyroSumY = 0.0
        gyroSumZ = 0.0
        gyroSampleCount = 0
        calibrationStartTime = System.currentTimeMillis()
        
        val statusView = if (orientation == "portrait") tvPortraitStatus else tvLandscapeStatus
        statusView.text = if (useGpsForwardAssist()) {
            getString(R.string.calibration_hold_steady_moto_gyro)
        } else {
            "📱 ${getString(R.string.calibration_hold_steady)}"
        }
        statusView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
        
        // ВАЖНО: Регистрираме sensor ПЪРВО, преди timer-а
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        // Gyro само за мото (baseline bias) — при автомобил оставя accel на пълна честота.
        if (useGpsForwardAssist()) {
            gyroscope?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        
        // ИЗЧАКВАМЕ 200ms за sensor warm-up, СЛЕД ТОВА стартираме 5-секунден timer
        statusView.postDelayed({
            calibrationStartTime = System.currentTimeMillis() // Рестартираме времето СЛЕД sensor warm-up
            
            // След 5 секунди събиране на baseline
            statusView.postDelayed({
                if (isCalibrating && !baselineCollected) {
                    completeBaseline()
                    startLearningForward()
                }
            }, BASELINE_DURATION_MS)
        }, 200L) // 200ms sensor warm-up
    }
    
    private fun completeBaseline() {
        // ПРОВЕРКА: Минимум 50 samples за надеждна калибрация (при ~50Hz = 1 секунда данни)
        val MIN_BASELINE_SAMPLES = 50
        
        if (noiseBaselineX.size < MIN_BASELINE_SAMPLES) {
            // НЕДОСТАТЪЧНО ДАННИ - продължаваме да чакаме!
            Log.w("DragCalibration", "⚠️ Insufficient baseline samples: ${noiseBaselineX.size}/$MIN_BASELINE_SAMPLES - extending collection")
            
            // Удължаваме събирането с още 2 секунди
            val statusView = if (calibratingOrientation == "portrait") tvPortraitStatus else tvLandscapeStatus
            statusView.postDelayed({
                if (isCalibrating && !baselineCollected) {
                    completeBaseline() // Опитваме отново
                    if (baselineCollected) {
                        startLearningForward()
                    }
                }
            }, 2000L)
            return
        }
        
        // ДОСТАТЪЧНО ДАННИ - изчисляваме baseline
        // Изчисляваме СРЕДНИЯ ВЕКТОР от всички семпли
        baselineVector = floatArrayOf(
            noiseBaselineX.average().toFloat(),
            noiseBaselineY.average().toFloat(),
            noiseBaselineZ.average().toFloat()
        )
        
        // Намираме МАКСИМАЛНАТА вибрация ПО ВСЯКА ОС (абсолютна стойност на отклонението)
        maxVibrX = 0f
        maxVibrY = 0f
        maxVibrZ = 0f
        var baselineNoiseEnergy = 0f
        for (i in noiseBaselineX.indices) {
            val dx = abs(noiseBaselineX[i] - baselineVector[0])
            val dy = abs(noiseBaselineY[i] - baselineVector[1])
            val dz = abs(noiseBaselineZ[i] - baselineVector[2])
            baselineNoiseEnergy += dx * dx + dy * dy + dz * dz
            
            if (dx > maxVibrX) maxVibrX = dx
            if (dy > maxVibrY) maxVibrY = dy
            if (dz > maxVibrZ) maxVibrZ = dz
        }
        baselineNoiseRms = sqrt((baselineNoiseEnergy / noiseBaselineX.size.coerceAtLeast(1)).coerceAtLeast(0f))
        
        baselineCollected = true
        Log.d("DragCalibration", "✅ Baseline collected: ${noiseBaselineX.size} samples")
        Log.d("DragCalibration", "   Average baseline vector: [${baselineVector[0]}, ${baselineVector[1]}, ${baselineVector[2]}]")
        Log.d("DragCalibration", "   Max vibrations per axis: X=${"%.3f".format(maxVibrX)}, Y=${"%.3f".format(maxVibrY)}, Z=${"%.3f".format(maxVibrZ)} m/s²")
        Log.d("DragCalibration", "   Baseline RMS noise: ${"%.3f".format(baselineNoiseRms)} m/s²")
    }
    
    private fun collectNoiseBaseline() {
        // Записваме RAW векторите (gravity + шум когато сме неподвижни)
        noiseBaselineX.add(rawAcceleration[0])
        noiseBaselineY.add(rawAcceleration[1])
        noiseBaselineZ.add(rawAcceleration[2])
    }
    
    private fun startLearningForward() {
        isLearningForward = true
        forwardLearningStartTime = System.currentTimeMillis()
        forwardReferenceUnit = null
        forwardRejectedDirectionSamples = 0
        forwardDotAccumulator = 0f
        forwardDotCount = 0
        forwardMagnitudeAccumulator = 0f
        forwardMagnitudeSquaredAccumulator = 0f
        resetGpsForwardSampling()
        if (useGpsForwardAssist()) {
            startCalibrationLocationUpdates()
        }
        val statusView = if (calibratingOrientation == "portrait") tvPortraitStatus else tvLandscapeStatus
        statusView.text = if (useGpsForwardAssist()) {
            getString(R.string.calibration_drive_forward_gps)
        } else {
            "🚗 ${getString(R.string.calibration_drive_forward)}"
        }
        statusView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // Записваме RAW данни (gravity + acceleration + шум)
            rawAcceleration[0] = event.values[0]
            rawAcceleration[1] = event.values[1]
            rawAcceleration[2] = event.values[2]
            
            // Първи 5 секунди - събираме baseline (gravity + шум)
            if (isCalibrating && !baselineCollected) {
                collectNoiseBaseline()
            }
            // След baseline - детектираме forward ускорение
            else if (isLearningForward) {
                detectForwardAcceleration()
            }
        } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            if (isCalibrating && !baselineCollected) {
                gyroSumX += event.values[0]
                gyroSumY += event.values[1]
                gyroSumZ += event.values[2]
                gyroSampleCount++
            }
        }
    }
    
    private fun detectForwardAcceleration() {
        val elapsedForward = System.currentTimeMillis() - forwardLearningStartTime
        if (useGpsForwardAssist()) {
            if (elapsedForward > FORWARD_GPS_MAX_DURATION_MS && !gpsForwardReady) {
                onForwardGpsTimeout()
                return
            }
        } else if (elapsedForward > CAR_FORWARD_PHASE_TIMEOUT_MS) {
            forwardSamplesX.clear()
            forwardSamplesY.clear()
            forwardSamplesZ.clear()
            forwardReferenceUnit = null
            forwardRejectedDirectionSamples = 0
            forwardDotAccumulator = 0f
            forwardDotCount = 0
            forwardMagnitudeAccumulator = 0f
            forwardMagnitudeSquaredAccumulator = 0f
            forwardLearningStartTime = System.currentTimeMillis()
            Log.w("DragCalibration", "⏱️ Forward phase timeout - restarting sample collection")
            return
        }

        // ИЗВАЖДАМЕ BASELINE (gravity + шум) от RAW данните
        val cleanAccel = floatArrayOf(
            rawAcceleration[0] - baselineVector[0],
            rawAcceleration[1] - baselineVector[1],
            rawAcceleration[2] - baselineVector[2]
        )
        
        // Изчисляваме magnitude на ПЪЛНИЯ 3D вектор
        val cleanMagnitude = sqrt(
            cleanAccel[0] * cleanAccel[0] + 
            cleanAccel[1] * cleanAccel[1] + 
            cleanAccel[2] * cleanAccel[2]
        )
        
        val gpsAssist = useGpsForwardAssist()
        // GPS actively defines the forward learning window: ignore accel until GPS confirms riding.
        if (gpsAssist && !gpsMotionReliable && !gpsForwardReady) {
            return
        }

        val forwardThreshold = if (gpsAssist) {
            DragCalibration.getCalibrationWeightedThreshold(
                linearAccel = cleanAccel,
                maxVibrX = maxVibrX,
                maxVibrY = maxVibrY,
                maxVibrZ = maxVibrZ,
                // Slightly easier once GPS already confirms straight motion.
                minFloor = if (gpsMotionReliable || gpsForwardReady) 0.45f else 0.6f
            )
        } else {
            // Car: the 5s peak is zero. Anything above that peak is forward — no fixed g floor.
            val peakIdle = maxOf(maxVibrX, maxVibrY, maxVibrZ)
            (peakIdle * 1.08f).coerceAtLeast(0.05f)
        }

        if (!gpsAssist && cleanMagnitude <= forwardThreshold) {
            if (forwardSamplesX.isNotEmpty()) {
                clearForwardAccelSamples()
            }
            return
        }

        // Игнорираме много малки стойности (под шума)
        if (cleanMagnitude < 0.05f) return
        
        // 🔥 СЪБИРАМЕ FORWARD SAMPLES (не записваме веднага!)
        // Детектираме ускорение НАД вибрациите
        if (cleanMagnitude > forwardThreshold) {
            val unitX = cleanAccel[0] / cleanMagnitude
            val unitY = cleanAccel[1] / cleanMagnitude
            val unitZ = cleanAccel[2] / cleanMagnitude

            val reference = forwardReferenceUnit
            if (reference == null) {
                forwardReferenceUnit = floatArrayOf(unitX, unitY, unitZ)
                forwardDotAccumulator = 1f
                forwardDotCount = 1
            } else {
                val cosine = unitX * reference[0] + unitY * reference[1] + unitZ * reference[2]
                if (cosine < FORWARD_DIRECTION_MIN_COS) {
                    forwardRejectedDirectionSamples++
                    if (forwardRejectedDirectionSamples % 5 == 0) {
                        Log.d(
                            "DragCalibration",
                            "↩️ Rejected off-direction sample: cos=${"%.3f".format(cosine)} (< ${"%.2f".format(FORWARD_DIRECTION_MIN_COS)})"
                        )
                    }
                    return
                }
                forwardDotAccumulator += cosine
                forwardDotCount++
            }

            if (forwardSamplesX.size < MAX_FORWARD_SAMPLES) {
                forwardSamplesX.add(cleanAccel[0])
                forwardSamplesY.add(cleanAccel[1])
                forwardSamplesZ.add(cleanAccel[2])
                forwardMagnitudeAccumulator += cleanMagnitude
                forwardMagnitudeSquaredAccumulator += cleanMagnitude * cleanMagnitude
            }

            val samplesNeeded = if (gpsAssist) FORWARD_SAMPLES_NEEDED else CAR_FORWARD_SAMPLES_NEEDED
            if (forwardSamplesX.size % 5 == 0) {
                Log.d(
                    "DragCalibration",
                    "📊 Forward sample #${forwardSamplesX.size}/$samplesNeeded: magnitude=${"%.3f".format(cleanMagnitude)} (threshold=${"%.3f".format(forwardThreshold)})"
                )
            }

            val hasEnoughSamples = forwardSamplesX.size >= samplesNeeded
            if (!gpsAssist) {
                if (hasEnoughSamples) {
                    completeForwardPhase()
                }
                return
            }
            if (hasEnoughSamples && gpsForwardReady) {
                completeForwardPhase()
            }
            return
        }
        
        // 🔍 DEBUG: Ако не мина threshold
        if (forwardSamplesX.isEmpty() && cleanMagnitude > 0.1f) {
            Log.d("DragCalibration", "⚠️ Below threshold: magnitude=${"%.3f".format(cleanMagnitude)}, threshold=${"%.3f".format(forwardThreshold)}")
        }
    }
    
    private fun completeForwardPhase() {
        if (forwardSamplesX.isEmpty()) {
            Log.w("DragCalibration", "⚠️ Forward phase completed with zero samples - abort")
            return
        }

        // Изчисляваме средния forward vector от всички samples
        var avgForwardX = forwardSamplesX.average().toFloat()
        var avgForwardY = forwardSamplesY.average().toFloat()
        var avgForwardZ = forwardSamplesZ.average().toFloat()

        if (useGpsForwardAssist()) {
            // 🔑 КРИТИЧНО за lean: премахваме компонентата УСПОРЕДНА на гравитацията, за да е
            // forward оста ЧИСТО ХОРИЗОНТАЛНА. Без това RIGHT = cross(gravity, forward) се
            // изражда (почти 0), нормализира се до шум и наклонът засича само едната посока.
            val gMag = sqrt(
                baselineVector[0] * baselineVector[0] +
                    baselineVector[1] * baselineVector[1] +
                    baselineVector[2] * baselineVector[2]
            )
            if (gMag > 0.0001f) {
                val gux = baselineVector[0] / gMag
                val guy = baselineVector[1] / gMag
                val guz = baselineVector[2] / gMag
                val proj = avgForwardX * gux + avgForwardY * guy + avgForwardZ * guz
                avgForwardX -= gux * proj
                avgForwardY -= guy * proj
                avgForwardZ -= guz * proj
            }
        }

        val magnitude = sqrt(avgForwardX * avgForwardX + avgForwardY * avgForwardY + avgForwardZ * avgForwardZ)

        if (useGpsForwardAssist() && magnitude < 0.02f) {
            Log.w("DragCalibration", "⚠️ Forward axis degenerate after horizontal projection (mag=$magnitude) - abort")
            onForwardGpsTimeout()
            return
        }
        
        // Нормализираме
        val forwardAxis = floatArrayOf(
            avgForwardX / magnitude,
            avgForwardY / magnitude,
            avgForwardZ / magnitude
        )

        val acceptedSamples = forwardSamplesX.size
        val attemptedSamples = acceptedSamples + forwardRejectedDirectionSamples
        val acceptRatio = if (attemptedSamples > 0) acceptedSamples.toFloat() / attemptedSamples.toFloat() else 0f
        val meanDot = if (forwardDotCount > 0) forwardDotAccumulator / forwardDotCount else 0f
        val meanMag = if (acceptedSamples > 0) forwardMagnitudeAccumulator / acceptedSamples else 0f
        val varianceMag = if (acceptedSamples > 0) {
            (forwardMagnitudeSquaredAccumulator / acceptedSamples) - (meanMag * meanMag)
        } else {
            0f
        }
        val stdMag = sqrt(max(0f, varianceMag))

        // Прост confidence score за quality на calibration (0..100)
        val coherenceScore = ((meanDot + 1f) / 2f).coerceIn(0f, 1f)
        val noiseScore = (1f - (baselineNoiseRms / 1.6f)).coerceIn(0f, 1f)
        val stabilityScore = (1f - (stdMag / max(0.3f, meanMag))).coerceIn(0f, 1f)
        val confidence = ((0.45f * coherenceScore) + (0.25f * acceptRatio) + (0.15f * noiseScore) + (0.15f * stabilityScore)) * 100f
        
        Log.d("DragCalibration", "✅ Forward phase COMPLETE!")
        Log.d("DragCalibration", "📊 Collected ${forwardSamplesX.size} samples over ~${FORWARD_SAMPLES_NEEDED * 10}ms")
        Log.d("DragCalibration", "📊 Rejected off-direction samples: $forwardRejectedDirectionSamples")
        Log.d("DragCalibration", "📍 Average Forward Vector: [${forwardAxis[0]}, ${forwardAxis[1]}, ${forwardAxis[2]}]")
        Log.d("DragCalibration", "📍 Magnitude: ${"%.3f".format(magnitude)} m/s²")
        Log.d("DragCalibration", "📍 Coherence: ${"%.3f".format(meanDot)}, acceptRatio=${"%.3f".format(acceptRatio)}")
        Log.d("DragCalibration", "📍 Confidence: ${"%.1f".format(confidence.coerceIn(0f, 100f))}%")

        // Lateral axis не ни трябва
        val dummyLateralAxis = floatArrayOf(0f, 1f, 0f)
        
        // Lock axes and save based on orientation!
        if (calibratingOrientation == "portrait") {
            DragCalibration.lockPortraitAxes(
                forward = forwardAxis,
                lateral = dummyLateralAxis,
                baseline = baselineVector,
                maxVibrX = maxVibrX,
                maxVibrY = maxVibrY,
                maxVibrZ = maxVibrZ,
                confidence = confidence.coerceIn(0f, 100f),
                profileId = profileId
            )
        } else {
            DragCalibration.lockLandscapeAxes(
                forward = forwardAxis,
                lateral = dummyLateralAxis,
                baseline = baselineVector,
                maxVibrX = maxVibrX,
                maxVibrY = maxVibrY,
                maxVibrZ = maxVibrZ,
                confidence = confidence.coerceIn(0f, 100f),
                profileId = profileId
            )
        }
        
        val targetLandscape = calibratingOrientation == "landscape"
        persistMotionFusionData(targetLandscape, confidence.coerceIn(0f, 100f))

        isCalibrating = false
        isLearningForward = false
        sensorManager.unregisterListener(this)
        stopCalibrationLocationUpdates()
        
        val statusView = if (calibratingOrientation == "portrait") tvPortraitStatus else tvLandscapeStatus
        statusView.text = "✅ ${getString(R.string.calibration_success)}"
        statusView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        
        CalibrationReminderStore.clearDragCalibrationDeferred(this, profileId)
        updateUI()
        applyOrientationGating()
        
        // Маркираме че калибрацията е завършена успешно
        calibrationCompleted = true
        
        // Допълнително принудително обновяване на Continue бутона за първи профил или нов профил на главния thread
        // Използваме post() за да гарантираме че UI-то се обновява правилно
        statusView.post {
            if ((isFirstProfile || isNewProfile) && DragCalibration.hasAnyCalibration()) {
                btnContinue.isEnabled = true
                // Принудително обновяване на видимостта
                btnContinue.invalidate()
                btnContinue.requestLayout()
            }
        }
        
        val successMessage = if (calibratingOrientation == "portrait") {
            getString(R.string.calibration_portrait_success)
        } else {
            getString(R.string.calibration_landscape_success)
        }
        Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show()
        
        // ВАЖНО: След калибрация редиректваме според контекста
        // Ако е първи профил или нов профил от Garage - НЕ прехвърляме автоматично, показваме бутон "ПРОДЪЛЖИ"
        // Бутонът "ПРОДЪЛЖИ" ще се появи в updateUI()
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    private fun persistMotionFusionData(targetLandscape: Boolean, confidence0to100: Float) {
        val hasGyroBias = gyroscope != null && gyroSampleCount >= MIN_GYRO_BIAS_SAMPLES
        val gyroBiasRad = if (hasGyroBias) {
            floatArrayOf(
                (gyroSumX / gyroSampleCount).toFloat(),
                (gyroSumY / gyroSampleCount).toFloat(),
                (gyroSumZ / gyroSampleCount).toFloat()
            )
        } else {
            floatArrayOf(0f, 0f, 0f)
        }

        val noiseFloor = baselineNoiseRms.coerceAtLeast(0f)

        MotionCalibrationStore.saveSnapshot(
            context = this,
            profileId = profileId,
            gyroBiasRad = gyroBiasRad,
            hasGyroBias = hasGyroBias,
            qualityScore = (confidence0to100 / 100f).coerceIn(0f, 1f),
            stillSamples = noiseBaselineX.size,
            forwardSamples = forwardSamplesX.size,
            stillLinearAvg = noiseFloor,
            stillVibrationMag = noiseFloor,
            forwardNoiseFloor = noiseFloor,
            forwardExcessTrigger = noiseFloor,
            isLandscape = targetLandscape
        )

        val gMag = sqrt(
            baselineVector[0] * baselineVector[0] +
                baselineVector[1] * baselineVector[1] +
                baselineVector[2] * baselineVector[2]
        )
        if (gMag > 0.0001f) {
            // Same formula Track/Foreground use: lean offset from baseline on calibrated RIGHT axis.
            // (lock*Axes already set DragCalibration.rightVector before this runs.)
            val leanOffsetDeg = DragCalibration.computeLeanOffsetDegFromBaseline(baselineVector)
            LeanCalibrationStore.saveOrientation(this, profileId, targetLandscape, leanOffsetDeg)
        }
    }

    private fun deferCalibrationForLater() {
        CalibrationReminderStore.markDragCalibrationDeferred(this, profileId)
        calibrationDeferred = true
        if (isFirstProfile || isFirstLaunch) {
            FeatureTourStore.markPending(this)
        }

        startActivity(Intent(this, MainContainerActivity::class.java).apply {
            putExtra(MainContainerActivity.EXTRA_NAV_ITEM_ID, R.id.navMap)
        })
        finish()
    }
    
    private fun finishCalibration() {
        CalibrationReminderStore.clearDragCalibrationDeferred(this, profileId)
        when {
            isFirstProfile -> {
                FeatureTourStore.markPending(this)
                // Първи профил - отиваме в главното app
                startActivity(Intent(this, MainContainerActivity::class.java).apply {
                    putExtra(MainContainerActivity.EXTRA_NAV_ITEM_ID, R.id.navMap)
                })
                finish()
            }
            isNewProfile -> {
                // Нов профил от Garage - връщаме се в Garage
                finish() // Просто затваряме activity-то, Garage е зад него
            }
            else -> {
                // От Settings - връщаме се
                finish()
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        handleBackPress()
        return true
    }
    
    override fun onBackPressed() {
        handleBackPress()
    }
    
    private fun handleBackPress() {
        if (isFirstProfile || isNewProfile) {
            // Ако е първи профил или нов профил от Garage и НЯМА НИКАКВА калибрация - показваме само съобщението
            // НЕ връщаме назад, оставаме на същата страница
            if (!DragCalibration.hasAnyCalibration()) {
                Toast.makeText(this, getString(R.string.calibration_need_one), Toast.LENGTH_LONG).show()
                return  // НЕ затваряме activity-то
            }
        }
        // Ако има калибрация или не е първи/нов профил - нормално затваряне
        // НО ако е първо влизане - не затваряме, само показваме съобщението
        if (isFirstLaunch) {
            if (!DragCalibration.hasAnyCalibration()) {
                Toast.makeText(this, getString(R.string.calibration_need_one), Toast.LENGTH_LONG).show()
            }
            return  // НЕ затваряме activity-то при първо влизане
        }
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        stopCalibrationLocationUpdates()
        // Do not delete the profile if the user skips/cancels calibration.
    }
}

