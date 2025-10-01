package com.example.clinometer

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.cos
import android.content.res.Configuration
import com.example.clinometer.settings.SoundManager
import com.example.clinometer.settings.UnitsManager
import android.widget.LinearLayout

// Data class for storing lap data
data class LapData(
    val lapNumber: Int = 0,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val speedData: MutableList<Float> = mutableListOf(),
    val accelerationData: MutableList<Float> = mutableListOf(),
    val leanAngleData: MutableList<Float> = mutableListOf(),
    val gyroscopeData: MutableList<Float> = mutableListOf(),
    val routePoints: MutableList<RoutePoint> = mutableListOf(),
    val timestamps: MutableList<Long> = mutableListOf()
)

class TrackSessionActivity : BaseActivity(), SensorEventListener, LocationListener {
    override fun getLayoutResourceId(): Int = R.layout.activity_track_session
    override fun getNavigationItemId(): Int = R.id.navTrack
    private lateinit var tvTrackName: TextView
    private lateinit var tvCurrentLap: TextView
    private lateinit var speedGauge: SpeedGaugeView
    private lateinit var tvLapTime: TextView
    private lateinit var llLapsContainer: LinearLayout
    private lateinit var tvNoLaps: TextView
    private lateinit var btnStartStop: MaterialButton
    private lateinit var btnLap: MaterialButton
    private var trackId: String = ""
    private var trackName: String = ""
    private var isMotorcycle: Boolean = true
    private var isRecording: Boolean = false
    private var currentLap: Int = 0
    private var lapStartTime: Long = 0
    private var sectorStartTime: Long = 0
    private var currentSector: Int = 0
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var rotationVector: Sensor? = null
    private var linearAccelSensor: Sensor? = null
    private lateinit var locationManager: LocationManager
    private val gyroscopeData = mutableListOf<Float>()
    private val speedData = mutableListOf<Float>()
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateDisplay()
            handler.postDelayed(this, 100)
        }
    }
    private var countdownTimer: Long = 0L
    private var isCountdownActive = false
    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (isCountdownActive && countdownTimer > 0) {
                tvLapTime.text = "Започва след: ${countdownTimer / 1000}"
                countdownTimer -= 1000
                handler.postDelayed(this, 1000)
            } else if (isCountdownActive && countdownTimer <= 0) {
                isCountdownActive = false
                startSession()
            }
        }
    }
    private val trackPoints = mutableListOf<TrackPoint>()
    private var currentTrackPointIndex = 0
    private var lastLocation: Location? = null
    private val accelerationData = mutableListOf<Float>()
    private val leanAngleData = mutableListOf<Float>()
    
    // Sound manager for track events
    private lateinit var soundManager: SoundManager
    
    // Lap data storage
    private val lapData = mutableListOf<LapData>()
    private var currentLapData = LapData()
    private var sessionStartTime: Long = 0L
    private var sessionEndTime: Long = 0L
    private val lapTimes = mutableListOf<Long>()
    private var totalLaps = 0
    private var bestLapTime: Long = Long.MAX_VALUE
    private var currentLapTime: Long = 0
    private val sectorTimes = mutableListOf<Long>() // Current lap sector times
    private val bestSectorTimes = mutableListOf<Long>() // Sector times from best LAP (not theoretical)
    private var sectorDistanceAccum: Float = 0f // meters traveled in current sector
    private var lapDistanceAccum: Float = 0f // meters traveled in current lap
    private val sectorDistances = mutableListOf<Float>() // Current lap sector distances (meters)
    private val bestSectorDistances = mutableListOf<Float>() // Sector distances from best lap (meters)
    private var bestLapDistance: Float = 0f // meters (sum of best lap sector distances)
    private var lastSectorChangeAtMs: Long = 0L
    private var lastPredictedLapSeconds: Float = Float.NaN
    private var displayedPredictedLapSeconds: Float = Float.NaN
    private val predictionMaxDeltaPerUpdate: Float = 0.8f // seconds
    private val sectorCrossFreezeMs: Long = 400
    private val speedWindowMs: Long = 2000
    private val speedSamplesMs = ArrayDeque<Pair<Long, Float>>() // (timestamp, speed m/s)
    private var awaitingStart: Boolean = false
    private var awaitingStartDialog: androidx.appcompat.app.AlertDialog? = null
    private var lastLocationTimeMs: Long = 0L
    private val predictionSmoothingAlpha: Float = 0.12f
    private var lastPredictionDisplayUpdateMs: Long = 0L
    private val predictionDisplayIntervalMs: Long = 1000L
    private val startProximityMeters: Float = 120f
    private val sectorProximityMeters: Float = 50f
    private var distanceToNextSector = 0f // Distance to next sector in meters
    private var maxSpeed: Float = 0f
    private var maxAcceleration: Float = 0f
    private var maxBraking: Float = 0f
    private var maxCorneringG: Float = 0f
    private var maxLeanAngle: Float = 0f
    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
        private const val MIN_DISTANCE_FOR_UPDATE = 1f
        private const val MIN_TIME_FOR_UPDATE = 100L
    }
    private val gravity = FloatArray(3) { 0f }
    private val linearAccel = FloatArray(3) { 0f }
    private val alphaGravity = 0.8f
    private val rotationMatrix = FloatArray(9) { 0f }
    private val worldAccel = FloatArray(3) { 0f }
    private var displayLX = 0f
    private var displayLY = 0f
    private val displaySmoothAlpha = 0.5f
    private val maxDisplayG = 3.0f
    // Heading smoothing for projecting world accel into vehicle frame
    private var hasSmoothedBearing = false
    private var smoothedBearingRad = 0f
    private val bearingAlpha = 0.2f
    // Stationary bias removal and deadband
    private var forwardBiasG = 0f
    private var lateralBiasG = 0f
    private val biasAlpha = 0.02f
    private val deadbandG = 0.10f
    // Prefer hardware linear acceleration if available
    private var preferLinearAccel = false
    // Stationary detection
    private var stationaryCounter = 0
    private var isStationary = false
    private val stationaryAccThreshold = 0.15f // m/s^2
    private val stationaryCountToLock = 8
    // Signal smoothing
    private var forwardGSmooth = 0f
    private var lateralGSmooth = 0f
    private val gSmoothAlpha = 0.3f
    // Lean angle (motorcycle) using ForegroundService logic
    private var filteredAngle: Float = 0f
    private var offsetAngle: Float = 0f
    private var currentCalibratedLean: Float = 0f
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize sound manager
        soundManager = SoundManager(this)
        
        trackId = intent.getStringExtra("track_id") ?: ""
        trackName = intent.getStringExtra("track_name") ?: "Track"
        isMotorcycle = intent.getBooleanExtra("is_motorcycle", true)
        val isResumeSession = intent.getBooleanExtra("resume_session", false)
        val sessionId = intent.getStringExtra("session_id") ?: ""
        initializeViews()
        setupClickListeners()
        setupSensors()
        setupLocation()
        loadTrackData()
        if (isResumeSession && sessionId.isNotEmpty()) {
            // For resume sessions, we don't need to clear active session
            // The session will continue with the existing sessionId
            // Do NOT auto-start. Wait for user to press Start (same as New Session)
        } else {
            android.util.Log.d("TrackSessionActivity", "🆕 NEW SESSION: clearing active session")
            // For new sessions, clear any existing active session
            clearActiveSession()
        }
    }
    private fun initializeViews() {
        tvTrackName = findViewById(R.id.tvTrackName)
        tvCurrentLap = findViewById(R.id.tvCurrentLap)
        speedGauge = findViewById(R.id.speedGauge)
        tvLapTime = findViewById(R.id.tvLapTime)
        llLapsContainer = findViewById(R.id.llLapsContainer)
        tvNoLaps = findViewById(R.id.tvNoLaps)
        btnStartStop = findViewById(R.id.btnStartStop)
        btnLap = findViewById(R.id.btnLap)
        tvTrackName.text = trackName
        speedGauge.setMotorcycleMode(isMotorcycle)
    }
    private fun setupClickListeners() {
        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            onBackPressed()
        }
        btnStartStop.setOnClickListener {
            toggleRecording()
        }
        btnLap.setOnClickListener {
            recordLap()
        }
    }
    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        preferLinearAccel = linearAccelSensor != null
        if (linearAccelSensor == null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (accelerometer == null && linearAccelSensor == null) {
            Log.w("TrackSession", "No accelerometer / linear acceleration sensor available")
        }
        if (rotationVector == null) {
            Log.w("TrackSession", "Rotation vector not available")
        }
    }
    
    private fun setupLocation() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST)
        } else {
            startLocationUpdates()
        }
    }
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_FOR_UPDATE, MIN_DISTANCE_FOR_UPDATE, this)
        }
    }
    private fun loadTrackData() {
        val trackManager = TrackManager(this)
        val trackData = trackManager.loadTrackData(trackId)
        
        // Always load track points regardless of trackData
        trackPoints.clear()
        // Override with explicit sector markers (including start/finish duplicated at the end)
        // Start/Finish
        val s = TrackPoint(41.073128, 23.517839)
        // Sector 2
        val s2 = TrackPoint(41.070481, 23.519244)
        // Sector 3
        val s3 = TrackPoint(41.072907, 23.516091)
        // Sector 4
        val s4 = TrackPoint(41.071511, 23.513143)
        // Order: start -> s2 -> s3 -> s4 -> start (lap ends when last is crossed)
        trackPoints.addAll(listOf(s, s2, s3, s4, s))
    }
    private fun toggleRecording() {
        isRecording = !isRecording
        if (isRecording) {
            startRecording()
        } else {
            stopRecording()
        }
    }
    private fun startRecording() {
        startCountdown()
    }
    private fun startCountdown() {
        isCountdownActive = true
        countdownTimer = 5000L
        btnStartStop.text = getString(R.string.track_button_cancel)
        btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.red))
        startLocationUpdates()
        handler.post(countdownRunnable)
    }
    private fun startSession() {
        isRecording = true
        sessionStartTime = System.currentTimeMillis()
        btnStartStop.text = getString(R.string.track_button_stop)
        btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.red))
        linearAccelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        rotationVector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        handler.post(updateRunnable)
        currentLap = 0
        lapStartTime = System.currentTimeMillis()
        sectorStartTime = lapStartTime
        currentSector = 0
        lapTimes.clear()
        totalLaps = 0
        bestLapTime = Long.MAX_VALUE
        currentLapTime = 0
        sectorTimes.clear()
        sectorDistances.clear()
        bestSectorTimes.clear()
        bestSectorDistances.clear()
        sectorDistanceAccum = 0f
        lapDistanceAccum = 0f
        bestLapDistance = 0f
        lastPredictedLapSeconds = Float.NaN
        displayedPredictedLapSeconds = Float.NaN
        speedGauge.unlockPredictiveColor()
        awaitingStart = true
        showAwaitingStartDialog()
        maxSpeed = 0f
        maxAcceleration = 0f
        maxBraking = 0f
        maxCorneringG = 0f
        maxLeanAngle = 0f
        
        // Initialize first lap data
        currentLapData = LapData(
            lapNumber = 1,
            startTime = lapStartTime
        )
        val sharedPrefs = getSharedPreferences("track_sessions", MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("has_active_session", true).putString("active_track_id", trackId).putString("active_track_name", trackName).apply()
    }
    private fun stopRecording() {
        isRecording = false
        isCountdownActive = false
        sessionEndTime = System.currentTimeMillis()
        
        // Set end time for current lap data
        currentLapData = currentLapData.copy(endTime = sessionEndTime)
        
        btnStartStop.text = getString(R.string.track_button_start)
        btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.green))
        handler.removeCallbacks(countdownRunnable)
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(updateRunnable)
        locationManager.removeUpdates(this)
        createOuting()
    }
    private fun stopRecordingWithoutSaving() {
        isRecording = false
        isCountdownActive = false
        sessionEndTime = System.currentTimeMillis()
        
        // Set end time for current lap data
        currentLapData = currentLapData.copy(endTime = sessionEndTime)
        
        btnStartStop.text = getString(R.string.track_button_start)
        btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.green))
        handler.removeCallbacks(countdownRunnable)
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(updateRunnable)
        locationManager.removeUpdates(this)
        clearActiveSession()
        showToast(getString(R.string.track_data_lost))
    }
    private fun recordLap() {
        if (isRecording) {
            currentLap++
            totalLaps++
            tvCurrentLap.text = currentLap.toString()
            val lapTime = System.currentTimeMillis() - lapStartTime
            val lapTimeFormatted = formatTime(lapTime)
            lapTimes.add(lapTime)
            
            // Play lap completion sound
            soundManager.playLapComplete()
            
            // Save current lap data
            currentLapData = currentLapData.copy(
                lapNumber = currentLap,
                endTime = System.currentTimeMillis()
            )
            lapData.add(currentLapData)
            android.util.Log.d("TrackSessionActivity", "Saved lap $currentLap data: ${currentLapData.routePoints.size} route points, ${currentLapData.speedData.size} speed samples, ${currentLapData.accelerationData.size} accel samples")
            
            val isNewBest = lapTime < bestLapTime
            if (isNewBest) {
                // Play personal best sound
                soundManager.playPersonalBest()
                bestLapTime = lapTime
                // Snapshot sector times and distances of this best lap
                bestSectorTimes.clear()
                bestSectorTimes.addAll(sectorTimes)
                bestSectorDistances.clear()
                bestSectorDistances.addAll(sectorDistances)
                bestLapDistance = sectorDistances.sum()
            }
            addLapToUI(currentLap, lapTimeFormatted)
            lapStartTime = System.currentTimeMillis()
            sectorStartTime = lapStartTime
            currentSector = 0
            sectorTimes.clear() // Clear sector times for new lap
            sectorDistances.clear()
            lapDistanceAccum = 0f
            sectorDistanceAccum = 0f
            
            // Start new lap data collection
            currentLapData = LapData(
                lapNumber = currentLap + 1,
                startTime = lapStartTime
            )
            
            showToast(getString(R.string.track_lap_saved, currentLap, lapTimeFormatted))
        }
    }
    private fun updateDisplay() {
        if (isRecording && !awaitingStart) {
            val currentTime = System.currentTimeMillis()
            val lapTime = currentTime - lapStartTime
            tvLapTime.text = formatTime(lapTime)
            updateStatistics()
            updateGauge()
        }
    }
    private fun updateStatistics() {
        lastLocation?.let { location ->
            val currentSpeed = location.speed * 3.6f
            maxSpeed = max(maxSpeed, currentSpeed)
        }
        // Max G updates use processed, smoothed signals from sensor pipeline
        maxAcceleration = max(maxAcceleration, max(0f, forwardGSmooth))
        maxBraking = max(maxBraking, max(0f, -forwardGSmooth))
        maxCorneringG = max(maxCorneringG, abs(lateralGSmooth))
        // Lean angle UI is updated in the sensor pipeline; maintain only max here
        if (isMotorcycle) {
            maxLeanAngle = max(maxLeanAngle, kotlin.math.abs(currentCalibratedLean))
        }
    }
    private fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val milliseconds = (timeMs % 1000) / 10
        return String.format("%02d:%02d.%02d", minutes, seconds, milliseconds)
    }
    override fun onSensorChanged(event: SensorEvent?) {
        if (!isRecording || awaitingStart) return
        event?.let { ev ->
            when (ev.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, ev.values)
                }
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    if (preferLinearAccel) {
                        linearAccel[0] = ev.values[0]
                        linearAccel[1] = ev.values[1]
                        linearAccel[2] = ev.values[2]
                        processLinearAccelerationAndUpdate(linearAccel)
                    }
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    // Fallback path when no dedicated linear acceleration
                    if (!preferLinearAccel) {
                    gravity[0] = alphaGravity * gravity[0] + (1 - alphaGravity) * ev.values[0]
                    gravity[1] = alphaGravity * gravity[1] + (1 - alphaGravity) * ev.values[1]
                    gravity[2] = alphaGravity * gravity[2] + (1 - alphaGravity) * ev.values[2]
                    linearAccel[0] = ev.values[0] - gravity[0]
                    linearAccel[1] = ev.values[1] - gravity[1]
                    linearAccel[2] = ev.values[2] - gravity[2]
                    processLinearAccelerationAndUpdate(linearAccel)
                    }
                }
                Sensor.TYPE_GYROSCOPE -> {
                    gyroscopeData.addAll(ev.values.sliceArray(0..2).toList())
                    if (gyroscopeData.size > 1000) {
                        gyroscopeData.removeAt(0)
                    }
                    // Add to current lap data
                    if (isRecording && !awaitingStart) {
                        currentLapData.gyroscopeData.addAll(ev.values.sliceArray(0..2).toList())
                        android.util.Log.d("TrackSessionActivity", "Added gyro data to lap: ${ev.values.size} values")
                    }
                }
            }
        }
        
        // Update gauge with current data including predictive gap
        updateGauge()
    }
    private fun processLinearAccelerationAndUpdate(deviceAccel: FloatArray) {
        // Transform device linear acceleration into world ENU frame
        if (!rotationMatrix.all { it == 0f }) {
            worldAccel[0] = rotationMatrix[0] * deviceAccel[0] + rotationMatrix[1] * deviceAccel[1] + rotationMatrix[2] * deviceAccel[2] // East
            worldAccel[1] = rotationMatrix[3] * deviceAccel[0] + rotationMatrix[4] * deviceAccel[1] + rotationMatrix[5] * deviceAccel[2] // North
            worldAccel[2] = rotationMatrix[6] * deviceAccel[0] + rotationMatrix[7] * deviceAccel[1] + rotationMatrix[8] * deviceAccel[2] // Up
        } else {
            worldAccel[0] = deviceAccel[0]
            worldAccel[1] = deviceAccel[1]
            worldAccel[2] = deviceAccel[2]
        }

        // Project ENU acceleration into vehicle frame using smoothed bearing
        // Bearing from GPS if available; otherwise derive yaw from rotation matrix (device yaw to world)
        var bearingRad = if (hasSmoothedBearing) smoothedBearingRad else 0f
        if (!hasSmoothedBearing && !rotationMatrix.all { it == 0f }) {
            // Extract yaw (azimuth) from rotation matrix
            val yaw = atan2(rotationMatrix[1].toDouble(), rotationMatrix[4].toDouble()).toFloat()
            bearingRad = yaw
        }
        val east = worldAccel[0]
        val north = worldAccel[1]

        // Stationary detection on world accel magnitude (exclude gravity – using linear accel already)
        val worldMag = kotlin.math.sqrt((east * east + north * north + worldAccel[2] * worldAccel[2]).toDouble()).toFloat()
        if (worldMag < stationaryAccThreshold) {
            stationaryCounter = (stationaryCounter + 1).coerceAtMost(1000)
        } else {
            stationaryCounter = (stationaryCounter - 2).coerceAtLeast(0)
        }
        isStationary = stationaryCounter >= stationaryCountToLock
        // Forward along heading; Right is positive lateral (so Left is negative)
        val forwardAcc = (east * sin(bearingRad)) + (north * cos(bearingRad))
        val lateralRightAcc = (east * cos(bearingRad)) - (north * sin(bearingRad))

        var forwardG = forwardAcc / 9.81f
        var lateralG = lateralRightAcc / 9.81f

        // Adaptive bias: slowly pull towards zero when near-zero motion
        val currentBiasAlpha = if (isStationary) biasAlpha * 5f else biasAlpha
        forwardBiasG = forwardBiasG + currentBiasAlpha * (forwardG - forwardBiasG)
        lateralBiasG = lateralBiasG + currentBiasAlpha * (lateralG - lateralBiasG)
        forwardG -= forwardBiasG
        lateralG -= lateralBiasG

        // Deadband to avoid jumps around zero
        if (kotlin.math.abs(forwardG) < deadbandG) forwardG = 0f
        if (kotlin.math.abs(lateralG) < deadbandG) lateralG = 0f

        // If stationary, force display easing to center
        if (isStationary) {
            displayLX *= 0.85f
            displayLY *= 0.85f
        }

        // Smooth G signals first
        forwardGSmooth = gSmoothAlpha * forwardGSmooth + (1 - gSmoothAlpha) * forwardG
        lateralGSmooth = gSmoothAlpha * lateralGSmooth + (1 - gSmoothAlpha) * lateralG
        // Then clamp for display
        val desiredDisplayY = clamp(forwardGSmooth, -maxDisplayG, maxDisplayG)
        val desiredDisplayX = clamp(lateralGSmooth, -maxDisplayG, maxDisplayG)
        displayLX = displaySmoothAlpha * displayLX + (1 - displaySmoothAlpha) * desiredDisplayX
        displayLY = displaySmoothAlpha * displayLY + (1 - displaySmoothAlpha) * desiredDisplayY
        val normX = (displayLX / maxDisplayG).coerceIn(-1f, 1f)
        val normY = (displayLY / maxDisplayG).coerceIn(-1f, 1f)

        // Positive forward is acceleration, negative forward is braking
        val accelerationG = max(0f, forwardGSmooth)
        val brakingG = max(0f, -forwardGSmooth)
        val corneringG = lateralGSmooth // keep sign; right +, left -

        // Update maxima
        maxAcceleration = max(maxAcceleration, accelerationG)
        maxBraking = max(maxBraking, brakingG)
        maxCorneringG = max(maxCorneringG, abs(corneringG))

        // Lean angle calculation (ForegroundService logic)
        if (isMotorcycle) {
            // Use raw accelerometer for tilt (gravity-based)
            val x = gravity[0] + deviceAccel[0]
            val y = gravity[1] + deviceAccel[1]
            val z = gravity[2] + deviceAccel[2]
            val totalGravity = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val rawTilt = if (totalGravity > 0f) {
                if (isLandscape) {
                    // Landscape: negative for tilt up, positive for tilt down (as in service)
                    (-Math.toDegrees(Math.asin((y / totalGravity).toDouble().coerceIn(-1.0, 1.0)))).toFloat()
                } else {
                    // Portrait: LEFT should be NEGATIVE, RIGHT positive
                    (-Math.toDegrees(Math.asin((x / totalGravity).toDouble().coerceIn(-1.0, 1.0)))).toFloat()
                }
            } else 0f
            val delta = kotlin.math.abs(rawTilt - filteredAngle)
            val adaptiveAlpha = (0.01f + (delta / 45f)).coerceIn(0.05f, 0.3f)
            filteredAngle += adaptiveAlpha * (rawTilt - filteredAngle)
            currentCalibratedLean = (offsetAngle - filteredAngle).coerceIn(-90f, 90f)
        }

        runOnUiThread {
            speedGauge.setGForces(accelerationG, brakingG, corneringG)
            speedGauge.setDotByNormalizedG(normX, normY)
            if (isMotorcycle) {
                speedGauge.setLeanAngle(currentCalibratedLean)
            }
        }

        // Keep small history if needed elsewhere
        accelerationData.add(deviceAccel[0])
        accelerationData.add(deviceAccel[1])
        accelerationData.add(deviceAccel[2])
        if (accelerationData.size > 1500) {
            repeat(3) { accelerationData.removeAt(0) }
        }
        
        // Add to current lap data
        if (isRecording && !awaitingStart) {
            currentLapData.accelerationData.addAll(deviceAccel.toList())
            currentLapData.leanAngleData.add(currentCalibratedLean)
            currentLapData.timestamps.add(System.currentTimeMillis())
            android.util.Log.d("TrackSessionActivity", "Added sensor data to lap: accel=${deviceAccel.size}, lean=${currentCalibratedLean}, timestamp=${System.currentTimeMillis()}")
        } else {
            android.util.Log.d("TrackSessionActivity", "Not adding sensor data: isRecording=$isRecording, awaitingStart=$awaitingStart")
        }
    }
    private fun clamp(v: Float, min: Float, max: Float) = when {
        v < min -> min
        v > max -> max
        else -> v
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onLocationChanged(location: Location) {
        // Accumulate distance traveled in current sector and lap
        if (!awaitingStart) {
            val nowT = location.time
            if (lastLocationTimeMs != 0L) {
                val dtSec = ((nowT - lastLocationTimeMs) / 1000f).coerceIn(0.05f, 1.5f)
                val inc = location.speed * dtSec // speed is m/s → distance meters
                sectorDistanceAccum += inc
                lapDistanceAccum += inc
            }
            lastLocationTimeMs = nowT
        }
        lastLocation = location
        val speedKmh = location.speed * 3.6f
        if (!awaitingStart) {
            speedData.add(speedKmh)
            // Add to current lap data
            currentLapData.speedData.add(speedKmh)
            currentLapData.routePoints.add(RoutePoint(
                geoPoint = org.osmdroid.util.GeoPoint(location.latitude, location.longitude),
                speed = speedKmh,
                angle = currentCalibratedLean,
                timestamp = System.currentTimeMillis(),
                absoluteTime = location.time
            ))
            android.util.Log.d("TrackSessionActivity", "Added GPS data to lap: speed=$speedKmh, lat=${location.latitude}, lng=${location.longitude}")
        } else {
            android.util.Log.d("TrackSessionActivity", "Not adding GPS data: awaitingStart=$awaitingStart")
        }
        // Keep rolling window of speed samples (m/s)
        val now = System.currentTimeMillis()
        speedSamplesMs.addLast(now to location.speed)
        while (speedSamplesMs.isNotEmpty() && now - speedSamplesMs.first().first > speedWindowMs) {
            speedSamplesMs.removeFirst()
        }
        // Smooth GPS bearing when available and speed is reasonable
        if (location.hasBearing() && location.speed > 1.5f) { // > ~5.4 km/h
            val bRad = Math.toRadians(location.bearing.toDouble()).toFloat()
            smoothedBearingRad = if (!hasSmoothedBearing) {
                hasSmoothedBearing = true
                bRad
            } else {
                // Smooth angle with wrap-around awareness
                val delta = atan2(sin(bRad - smoothedBearingRad), cos(bRad - smoothedBearingRad))
                smoothedBearingRad + bearingAlpha * delta
            }
        }
        checkTrackPointProximity(location)
    }
    private fun checkTrackPointProximity(location: Location) {
        if (trackPoints.isNotEmpty()) {
            // While awaiting start, only consider the Start/Finish point (index 0)
            val targetIndex = if (awaitingStart) 0 else currentTrackPointIndex
            if (targetIndex >= trackPoints.size) return
            val trackPoint = trackPoints[targetIndex]
            val trackLocation = Location("track").apply {
                latitude = trackPoint.latitude
                longitude = trackPoint.longitude
            }
            val distance = location.distanceTo(trackLocation)
            val threshold = if (awaitingStart) startProximityMeters else sectorProximityMeters
            if (distance < threshold) {
                // If waiting for start, initialize timing at the first crossing and do not record a sector
                if (awaitingStart) {
                    awaitingStart = false
                    lapStartTime = System.currentTimeMillis()
                    sectorStartTime = lapStartTime
                    currentSector = 0
                    // After crossing start, next target is Sector 2 (index 1)
                    currentTrackPointIndex = 1
                    // Ensure sensors and location updates are active
                    linearAccelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                    accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                    rotationVector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                    gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
                    handler.post(updateRunnable)
                    sectorDistanceAccum = 0f
                    lapDistanceAccum = 0f
                    lastLocationTimeMs = location.time
                    lastPredictedLapSeconds = Float.NaN
                    displayedPredictedLapSeconds = Float.NaN
                    // Do not lock color yet; no best lap → keep neutral until we have a target
                    speedGauge.unlockPredictiveColor()
                    dismissAwaitingStartDialog()
                    return
                }
                // Record sector time
                val sectorTime = System.currentTimeMillis() - sectorStartTime
                sectorTimes.add(sectorTime)
                // Record sector distance
                sectorDistances.add(sectorDistanceAccum)
                lastSectorChangeAtMs = System.currentTimeMillis()
                // Lock background color strictly by checkpoint delta (best vs current at THIS point)
                if (bestLapTime != Long.MAX_VALUE && bestSectorTimes.isNotEmpty()) {
                    val justCompletedSectors = sectorTimes.size // we just added the sector time
                    val currentElapsedMs = sectorTimes.sum()
                    val isStartFinishCross = (targetIndex == 0) // lap boundary
                    val isSlower: Boolean = if (isStartFinishCross) {
                        // Compare full lap time vs best lap time BEFORE possibly updating best
                        currentElapsedMs >= bestLapTime
                    } else {
                        // Compare cumulative elapsed to best cumulative at same checkpoint
                        val bestElapsedMs = bestSectorTimes.take(justCompletedSectors).sum()
                        currentElapsedMs >= bestElapsedMs
                    }
                    speedGauge.lockPredictiveColor(isSlower)
                }
                
                // Update best sector time if this is better
                if (currentSector < bestSectorTimes.size) {
                    if (sectorTime < bestSectorTimes[currentSector]) {
                        bestSectorTimes[currentSector] = sectorTime
                    }
                } else {
                    bestSectorTimes.add(sectorTime)
                }
                
                currentSector++
                sectorStartTime = System.currentTimeMillis()
                sectorDistanceAccum = 0f
                if (currentTrackPointIndex < trackPoints.size - 1) {
                    currentTrackPointIndex++
                } else {
                    recordLap()
                    currentTrackPointIndex = 0
                }
            }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            }
        }
    }
    override fun onResume() {
        super.onResume()
        if (isRecording) {
            linearAccelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            rotationVector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        handler.removeCallbacks(countdownRunnable)
        locationManager.removeUpdates(this)
        soundManager.release()
    }
    private fun addLapToUI(lapNumber: Int, lapTime: String) {
        tvNoLaps.visibility = android.view.View.GONE
        val inflater = layoutInflater
        val lapView = inflater.inflate(R.layout.lap_item_session_template, llLapsContainer, false)
        val tvLapNumber = lapView.findViewById<TextView>(R.id.tvLapNumber)
        val tvLapTime = lapView.findViewById<TextView>(R.id.tvLapTime)
        tvLapNumber.text = getString(R.string.track_lap_label, lapNumber)
        tvLapTime.text = lapTime
        llLapsContainer.addView(lapView, 0)
    }
    private fun updateGauge() {
        if (!awaitingStart) {
            lastLocation?.let { location ->
                val currentSpeed = location.speed * 3.6f
                speedGauge.setSpeed(currentSpeed)
            }
        } else {
            speedGauge.setSpeed(0f)
        }
        
        // Lap-level predictive gap: predictedLap = elapsed + (bestDistance - traveled) / currentSpeed
        if (isRecording && !awaitingStart && lapStartTime > 0 && bestLapTime != Long.MAX_VALUE && bestLapDistance > 0f) {
            val nowMs = System.currentTimeMillis()
            val elapsedLapSeconds = (nowMs - lapStartTime) / 1000f

            // Avoid spike right after sector crossing
            if (nowMs - lastSectorChangeAtMs < sectorCrossFreezeMs) {
                if (!lastPredictedLapSeconds.isNaN()) {
                    speedGauge.setPredictiveGap(lastPredictedLapSeconds, bestLapTime / 1000f)
                }
                return
            }

            val rollingSpeedMs = getRollingSpeedMs()
            val avgLapSpeedMs = if (elapsedLapSeconds > 0f && lapDistanceAccum > 0f) lapDistanceAccum / elapsedLapSeconds else 0f
            val effectiveSpeedMs = max(rollingSpeedMs, avgLapSpeedMs)
            val remainingDistance = (bestLapDistance - lapDistanceAccum).coerceAtLeast(0f)
            var predictedLapSeconds = if (effectiveSpeedMs > 0f) {
                elapsedLapSeconds + (remainingDistance / effectiveSpeedMs)
            } else {
                Float.POSITIVE_INFINITY
            }

            // Stronger rate-limit for readability
            if (!lastPredictedLapSeconds.isNaN()) {
                val delta = (predictedLapSeconds - lastPredictedLapSeconds)
                val clamped = delta.coerceIn(-0.5f, 0.5f)
                predictedLapSeconds = lastPredictedLapSeconds + clamped
            }
            lastPredictedLapSeconds = predictedLapSeconds
            // Exponential smoothing for display to slow number movement
            val alpha = 0.08f
            displayedPredictedLapSeconds = if (displayedPredictedLapSeconds.isNaN()) predictedLapSeconds
                else (alpha * predictedLapSeconds + (1 - alpha) * displayedPredictedLapSeconds)
            // Throttle UI updates to every 2 seconds for readability
            val nowUi = System.currentTimeMillis()
            if (nowUi - lastPredictionDisplayUpdateMs >= predictionDisplayIntervalMs) {
                lastPredictionDisplayUpdateMs = nowUi
                speedGauge.setPredictiveGap(displayedPredictedLapSeconds, bestLapTime / 1000f)
            }
        }
        
        // G-forces are now set inside processLinearAccelerationAndUpdate with proper projection
        if (isMotorcycle && leanAngleData.isNotEmpty()) {
            val leanAngle = abs(leanAngleData.last())
            speedGauge.setLeanAngle(leanAngle)
        }
    }

    private fun getRollingSpeedMs(): Float {
        if (speedSamplesMs.isEmpty()) return 0f
        var sum = 0f
        speedSamplesMs.forEach { sum += it.second }
        return sum / speedSamplesMs.size
    }
    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
    override fun onBackPressed() {
        if (isRecording) {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Потвърждение").setMessage("Сигурни ли сте? Ще се изгубят всички данни от измерването!").setPositiveButton("ДА, излез") { _, _ ->
                stopRecordingWithoutSaving()
                super.onBackPressed()
                overridePendingTransition(0, 0)
            }.setNegativeButton("Отказ", null).show()
        } else {
            super.onBackPressed()
            overridePendingTransition(0, 0)
        }
    }

    private fun showAwaitingStartDialog() {
        dismissAwaitingStartDialog()
        awaitingStartDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Премини старта")
            .setMessage("За да започне измерването, премини през старт/финал линията.")
            .setCancelable(false)
            .setNegativeButton("Отмени") { _, _ -> stopRecording() }
            .show()
    }

    private fun dismissAwaitingStartDialog() {
        awaitingStartDialog?.dismiss()
        awaitingStartDialog = null
    }
    private fun createOuting() {
        Thread {
            try {
                val sessionDuration = sessionEndTime - sessionStartTime
                val sessionDurationFormatted = formatTime(sessionDuration)
                val bestLapFormatted = if (bestLapTime == Long.MAX_VALUE) "--:--.---" else formatTime(bestLapTime)
                val outingData = mapOf(
                    "trackName" to trackName,
                    "date" to java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(sessionStartTime)),
                    "time" to java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(sessionStartTime)),
                    "duration" to sessionDurationFormatted,
                    "totalLaps" to totalLaps.toString(),
                    "bestLapTime" to bestLapFormatted,
                    "maxSpeed" to String.format("%.1f km/h", maxSpeed),
                    "maxAcceleration" to String.format("%.2f G", maxAcceleration),
                    "maxBraking" to String.format("%.2f G", maxBraking),
                    "maxCorneringG" to String.format("%.2f G", maxCorneringG),
                    "maxLeanAngle" to String.format("%.1f°", maxLeanAngle)
                )
                val isResumeSession = intent.getBooleanExtra("resume_session", false)
                val sessionIdRaw = if (isResumeSession) {
                    // For resume, sessionId already contains profileId prefix, so we need to extract the raw part
                    val fullSessionId = intent.getStringExtra("session_id") ?: trackId
                    if (fullSessionId.matches(Regex("\\d+_.*"))) {
                        // Remove profileId prefix: "123_serres_circuit_..." -> "serres_circuit_..."
                        fullSessionId.substringAfter("_")
                    } else {
                        fullSessionId
                    }
                } else {
                    val date = outingData["date"] ?: ""
                    val time = outingData["time"] ?: ""
                    val timestamp = System.currentTimeMillis()
                    "${trackId}_${date}_${time.replace(":", "")}_${timestamp}"
                }
                saveOutingDataWithSessionId(outingData, sessionIdRaw)
                val sharedPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
                val currentProfileId = ProfileStorage.getSelectedProfileId(this)
                val sessionIdWithProfile = "${currentProfileId}_${sessionIdRaw}"
                val outingNumber = sharedPrefs.getInt("${sessionIdWithProfile}_outing_count", 1)
                runOnUiThread {
                    showToast(getString(R.string.track_session_saved, totalLaps, bestLapFormatted))
                    val intent = Intent(this@TrackSessionActivity, TrackSessionDetailActivity::class.java)
                    intent.putExtra("trackName", trackName)
                    intent.putExtra("trackId", sessionIdWithProfile) // This contains profileId prefix
                    intent.putExtra("outingNumber", outingNumber)
                    intent.putExtra("date", outingData["date"])
                    intent.putExtra("time", outingData["time"])
                    intent.putExtra("duration", outingData["duration"])
                    intent.putExtra("totalLaps", outingData["totalLaps"])
                    intent.putExtra("bestLapTime", outingData["bestLapTime"])
                    intent.putExtra("maxSpeed", outingData["maxSpeed"])
                    intent.putExtra("maxAcceleration", outingData["maxAcceleration"])
                    intent.putExtra("maxBraking", outingData["maxBraking"])
                    intent.putExtra("maxCorneringG", outingData["maxCorneringG"])
                    intent.putExtra("maxLeanAngle", outingData["maxLeanAngle"])
                    startActivity(intent)
                    finish()
                    overridePendingTransition(0, 0)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showToast(getString(R.string.track_save_error, e.message ?: "Unknown"))
                    val intent = Intent(this@TrackSessionActivity, TrackActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        }.start()
    }
    private fun saveOutingDataWithSessionId(outingData: Map<String, String>, sessionIdRaw: String) {
        val sharedPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val currentProfileId = ProfileStorage.getSelectedProfileId(this)
        val sessionId = "${currentProfileId}_${sessionIdRaw}"
        val outingNumber = sharedPrefs.getInt("${sessionId}_outing_count", 0) + 1
        val editor = sharedPrefs.edit()
        editor.putString("${sessionId}_outing_${outingNumber}_date", outingData["date"])
        editor.putString("${sessionId}_outing_${outingNumber}_time", outingData["time"])
        editor.putString("${sessionId}_outing_${outingNumber}_duration", outingData["duration"])
        editor.putString("${sessionId}_outing_${outingNumber}_laps", outingData["totalLaps"])
        editor.putString("${sessionId}_outing_${outingNumber}_best_lap", outingData["bestLapTime"])
        editor.putString("${sessionId}_outing_${outingNumber}_max_speed", outingData["maxSpeed"])
        editor.putString("${sessionId}_outing_${outingNumber}_max_acceleration", outingData["maxAcceleration"])
        editor.putString("${sessionId}_outing_${outingNumber}_max_braking", outingData["maxBraking"])
        editor.putString("${sessionId}_outing_${outingNumber}_max_cornering", outingData["maxCorneringG"])
        editor.putString("${sessionId}_outing_${outingNumber}_max_lean_angle", outingData["maxLeanAngle"])
        editor.putInt("${sessionId}_outing_count", outingNumber)
        for (i in lapTimes.indices) {
            editor.putString("${sessionId}_outing_${outingNumber}_lap_${i + 1}", formatTime(lapTimes[i]))
        }
        
        // Save lap data
        saveLapData(editor, sessionId, outingNumber)
        
        editor.apply()
    }
    
    private fun saveLapData(editor: android.content.SharedPreferences.Editor, sessionId: String, outingNumber: Int) {
        val gson = com.google.gson.Gson()
        android.util.Log.d("TrackSessionActivity", "Saving ${lapData.size} lap data entries")
        for (i in lapData.indices) {
            val lap = lapData[i]
            val lapKey = "${sessionId}_outing_${outingNumber}_lap_data_${i + 1}"
            val json = gson.toJson(lap)
            editor.putString(lapKey, json)
            android.util.Log.d("TrackSessionActivity", "Saved lap ${i + 1}: ${lap.routePoints.size} route points, ${lap.speedData.size} speed samples, ${lap.accelerationData.size} accel samples")
        }
        editor.putInt("${sessionId}_outing_${outingNumber}_lap_data_count", lapData.size)
        android.util.Log.d("TrackSessionActivity", "Set lap data count to ${lapData.size}")
    }
    private fun setActiveSession(trackId: String, trackName: String) {
        val sharedPrefs = getSharedPreferences("track_sessions", MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("has_active_session", true).putString("active_track_id", trackId).putString("active_track_name", trackName).apply()
    }
    private fun clearActiveSession() {
        val sharedPrefs = getSharedPreferences("track_sessions", MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("has_active_session", false).remove("active_track_id").remove("active_track_name").apply()
    }
}
