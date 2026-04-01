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
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.main.MainContainerActivity
import com.example.clinometer.track.catalog.TrackMode
import com.example.clinometer.track.session.TrackGateCrossingEngine
import com.example.clinometer.track.session.TrackLapTimingEngine

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
    val timestamps: MutableList<Long> = mutableListOf(),
    val sensorData: MutableList<Any> = mutableListOf() // Placeholder - SDK handles sensor data
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
    private val trackPointTypes = mutableListOf<com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType>() // Types of each point
    private val startFinishLineIndices = mutableListOf<Int>() // Indices of start/finish line points
    private val gateCrossingEngine = TrackGateCrossingEngine(lineThresholdMeters = 30.0)
    private val lapTimingEngine = TrackLapTimingEngine(minLapTimeMs = 10_000L)
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
    private val sectorCrossFreezeMs: Long = 400
    private val speedWindowMs: Long = 2000
    private val speedSamplesMs = ArrayDeque<Pair<Long, Float>>() // (timestamp, speed m/s)
    private var awaitingStart: Boolean = false
    private var awaitingStartDialog: androidx.appcompat.app.AlertDialog? = null
    private var awaitingStartMessageView: TextView? = null
    private var lastLocationTimeMs: Long = 0L
    private var lastPredictionDisplayUpdateMs: Long = 0L
    private val predictionDisplayIntervalMs: Long = 1000L
    private val startProximityMeters: Float = 20f  // Must pass very close to start/finish to begin
    private val sectorProximityMeters: Float = 50f
    private var currentTrackMode: TrackMode = TrackMode.CIRCUIT
    private var maxSpeed: Float = 0f
    private var maxAcceleration: Float = 0f
    private var maxBraking: Float = 0f
    private var maxCorneringLeftG: Float = 0f
    private var maxCorneringRightG: Float = 0f
    private var maxLeanAngle: Float = 0f
    private var previousLocationForCrossing: Location? = null
    private var lastStartFinishCrossAtMs: Long = 0L
    private val startFinishCrossDebounceMs: Long = 1500L
    private val pointToPointStartHintMeters: Double = 1500.0
    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
        private const val MIN_DISTANCE_FOR_UPDATE = 1f
        private const val MIN_TIME_FOR_UPDATE = 100L
    }
    private val gravity = FloatArray(3) { 0f }
    private val latestRawAccel = FloatArray(3) { 0f }
    private val linearAccel = FloatArray(3) { 0f }
    private val alphaGravity = 0.8f
    private val rotationMatrix = FloatArray(9) { 0f }
    private val worldAccel = FloatArray(3) { 0f }
    private var displayLX = 0f
    private var displayLY = 0f
    private val maxDisplayG = 3.0f
    // Heading smoothing for projecting world accel into vehicle frame
    private var hasSmoothedBearing = false
    private var smoothedBearingRad = 0f
    private val bearingAlpha = 0.2f
    // Stationary bias removal and deadband
    private var forwardBiasG = 0f
    private var lateralBiasG = 0f
    private val biasAlpha = 0.02f
    private val deadbandG = 0.05f
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
    private var statsFilteredLongG = 0f
    private var statsFilteredLatG = 0f
    private val statsFilterAlpha = 0.20f
    private val statsDeadbandG = 0.06f
    private val minConfidenceForStats = 0.35f
    private val confidenceLowPassAlpha = 0.15f
    private var smoothedConfidence = 1f
    private var lastGyroMagnitude = 0f
    private var accelTimestampNs: Long = 0L
    private var rotationTimestampNs: Long = 0L
    private var gyroTimestampNs: Long = 0L
    private var worldAccelTimestampNs: Long = 0L
    private val worldFusionAlpha = 0.35f
    private val fusedWorldAccel = FloatArray(3) { 0f }
    private var hasFusedWorldAccel = false
    private val peakEntryHysteresisG = 0.03f
    private val peakExitHysteresisG = 0.015f
    private val peakHoldMs = 120L
    private data class PeakDetector(
        var committed: Float = 0f,
        var candidate: Float = 0f,
        var candidateSinceMs: Long = 0L
    )
    private val accelerationPeakDetector = PeakDetector()
    private val brakingPeakDetector = PeakDetector()
    private val corneringLeftPeakDetector = PeakDetector()
    private val corneringRightPeakDetector = PeakDetector()
    // Lean angle (motorcycle) using ForegroundService logic
    private var filteredAngle: Float = 0f
    private var offsetAngle: Float = 0f
    private var currentCalibratedLean: Float = 0f
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBarsPaddingToRoot()
        
        // ✅ КРИТИЧНО: Инициализираме DragCalibration и задаваме профила
        // Това е необходимо за да работи калибрацията правилно
        DragCalibration.init(this)
        val currentProfileId = ProfileStorage.getSelectedProfileId(this)
        DragCalibration.setProfile(currentProfileId)
        android.util.Log.d("TrackSessionActivity", "🔧 DragCalibration initialized for profile $currentProfileId, isUniversalCalibrated=${DragCalibration.isUniversalCalibrated}")
        
        // Initialize sound manager
        soundManager = SoundManager(this)
        
        trackId = intent.getStringExtra("track_id") ?: ""
        trackName = intent.getStringExtra("track_name") ?: "Track"
        isMotorcycle = if (intent.hasExtra("is_motorcycle")) {
            intent.getBooleanExtra("is_motorcycle", true)
        } else {
            val selectedProfile = ProfileStorage.loadProfiles(this).find { it.id == currentProfileId }
            selectedProfile?.vehicleType == Profile.VehicleType.MOTORCYCLE
        }
        val isResumeSession = intent.getBooleanExtra("resume_session", false)
        val sessionId = intent.getStringExtra("session_id") ?: ""
        initializeViews()
        setupClickListeners()
        setupSensors()
        loadTrackData()  // ✅ CRITICAL: Load track data BEFORE starting location updates
        setupLocation()
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
            if (currentTrackMode == TrackMode.POINT_TO_POINT) {
                onBackPressed()
            } else {
                recordLap()
            }
        }
    }

    private fun updateSecondaryActionButton() {
        if (currentTrackMode == TrackMode.POINT_TO_POINT) {
            btnLap.text = "CANCEL"
        } else {
            btnLap.text = getString(R.string.track_button_lap)
        }
    }
    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        preferLinearAccel = linearAccelSensor != null
        // ВИНАГИ получаваме ACCELEROMETER сензора (за g-сили), дори когато има TYPE_LINEAR_ACCELERATION
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (accelerometer == null) {
            Log.w("TrackSession", "No accelerometer sensor available")
        }
        if (rotationVector == null) {
            Log.w("TrackSession", "Rotation vector not available")
        }
        
        // Регистрираме ACCELEROMETER сензора ВИНАГИ (не само когато записваме)
        // за да може g-силите да се обновяват винаги (както в drag сесиите)
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
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
        trackPoints.clear()
        trackPointTypes.clear()
        startFinishLineIndices.clear()
        var hasValidStartTrigger = false
        
        val isOfficial = if (intent.hasExtra("is_official")) {
            intent.getBooleanExtra("is_official", true)
        } else {
            !trackId.startsWith("custom_")
        }
        
        if (isOfficial) {
            // Load official track data
            val trackManager = TrackManager(this)
            val trackDefinition = trackManager.getTrackDefinition(trackId)
            val trackData = trackManager.loadTrackData(trackId)
            currentTrackMode = trackDefinition?.mode ?: TrackMode.CIRCUIT

            val startFinishGate = trackDefinition?.startFinishGate
            val startGate = trackDefinition?.startGate
            val finishGate = trackDefinition?.finishGate
            if (currentTrackMode == TrackMode.CIRCUIT && startFinishGate != null) {
                val gateStart = TrackPoint(startFinishGate.start.latitude, startFinishGate.start.longitude)
                val gateEnd = TrackPoint(startFinishGate.end.latitude, startFinishGate.end.longitude)
                trackPoints.addAll(listOf(gateStart, gateEnd, gateStart, gateEnd))
                startFinishLineIndices.addAll(listOf(0, 1, 2, 3))
                hasValidStartTrigger = true

                trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.START_FINISH)
                trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.START_FINISH)
                trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.START_FINISH)
                trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.START_FINISH)
            } else if (currentTrackMode == TrackMode.POINT_TO_POINT && startGate != null && finishGate != null) {
                val startA = TrackPoint(startGate.start.latitude, startGate.start.longitude)
                val startB = TrackPoint(startGate.end.latitude, startGate.end.longitude)
                val finishA = TrackPoint(finishGate.start.latitude, finishGate.start.longitude)
                val finishB = TrackPoint(finishGate.end.latitude, finishGate.end.longitude)

                trackPoints.add(startA)
                trackPoints.add(startB)
                trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.START)
                trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.START)

                trackPoints.add(finishA)
                trackPoints.add(finishB)
                trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.FINISH)
                trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.FINISH)

                startFinishLineIndices.addAll(listOf(0, 1, 2, 3))
                hasValidStartTrigger = true
            }

            if (trackPoints.isEmpty() && trackDefinition != null && trackDefinition.lapSequence.isNotEmpty()) {
                trackPoints.addAll(
                    trackDefinition.lapSequence.map { point ->
                        TrackPoint(point.latitude, point.longitude)
                    }
                )
            } else if (trackPoints.isEmpty() && trackData != null) {
                trackPoints.addAll(trackData.trackPoints)
            }

            if (trackPoints.isEmpty()) {
                val s = TrackPoint(41.073128, 23.517839)
                trackPoints.add(s)
                android.util.Log.e("TrackSessionActivity", "Official track has no usable points: $trackId. Applied safe fallback point.")
            }
            
            awaitingStart = hasValidStartTrigger
            if (awaitingStart) {
                android.util.Log.d("TrackSessionActivity", "⏰ awaitingStart set to TRUE for official track (valid start trigger)")
            } else {
                android.util.Log.w("TrackSessionActivity", "⚠️ Official track has no valid start trigger, session will start immediately after countdown")
            }
        } else {
            // Load custom track data from schema v2
            val customTrackV2 = com.example.clinometer.tracking.CustomTrackStorage.loadCustomTrackV2(this, trackId)
            if (customTrackV2 != null) {
                trackName = customTrackV2.name
                currentTrackMode = when (customTrackV2.mode) {
                    com.example.clinometer.tracking.CustomTrackMode.CIRCUIT -> TrackMode.CIRCUIT
                    com.example.clinometer.tracking.CustomTrackMode.POINT_TO_POINT -> TrackMode.POINT_TO_POINT
                }

                when (currentTrackMode) {
                    TrackMode.CIRCUIT -> {
                        val gate = customTrackV2.startGate
                        if (gate != null) {
                            val gateStart = TrackPoint(gate.start.latitude, gate.start.longitude)
                            val gateEnd = TrackPoint(gate.end.latitude, gate.end.longitude)
                            trackPoints.addAll(listOf(gateStart, gateEnd, gateStart, gateEnd))
                            startFinishLineIndices.addAll(listOf(0, 1, 2, 3))
                            hasValidStartTrigger = true

                            trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.START_FINISH)
                            trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.START_FINISH)
                            trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.START_FINISH)
                            trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.START_FINISH)
                        } else {
                            android.util.Log.w("TrackSessionActivity", "Custom circuit missing startGate: $trackId")
                        }
                    }
                    TrackMode.POINT_TO_POINT -> {
                        val startGate = customTrackV2.startGate
                        val finishGate = customTrackV2.finishGate

                        val hasStartLine = startGate != null &&
                            (startGate.start.latitude != startGate.end.latitude || startGate.start.longitude != startGate.end.longitude)
                        val hasFinishLine = finishGate != null &&
                            (finishGate.start.latitude != finishGate.end.latitude || finishGate.start.longitude != finishGate.end.longitude)

                        if (hasStartLine && hasFinishLine) {
                            val startA = TrackPoint(startGate!!.start.latitude, startGate.start.longitude)
                            val startB = TrackPoint(startGate.end.latitude, startGate.end.longitude)
                            trackPoints.add(startA)
                            trackPoints.add(startB)
                            trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.START)
                            trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.START)

                            trackPoints.addAll(
                                customTrackV2.referencePath.map { point ->
                                    TrackPoint(point.latitude, point.longitude)
                                }
                            )
                            repeat(customTrackV2.referencePath.size) {
                                trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.SNAP_HELPER)
                            }

                            val finishA = TrackPoint(finishGate!!.start.latitude, finishGate.start.longitude)
                            val finishB = TrackPoint(finishGate.end.latitude, finishGate.end.longitude)
                            trackPoints.add(finishA)
                            trackPoints.add(finishB)
                            trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.FINISH)
                            trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.FINISH)

                            startFinishLineIndices.addAll(listOf(0, 1, trackPoints.size - 2, trackPoints.size - 1))
                            hasValidStartTrigger = true
                        } else {
                            startGate?.let { gate ->
                                trackPoints.add(
                                    TrackPoint(
                                        latitude = (gate.start.latitude + gate.end.latitude) / 2.0,
                                        longitude = (gate.start.longitude + gate.end.longitude) / 2.0
                                    )
                                )
                                hasValidStartTrigger = true
                            }

                            trackPoints.addAll(
                                customTrackV2.referencePath.map { point ->
                                    TrackPoint(point.latitude, point.longitude)
                                }
                            )

                            finishGate?.let { gate ->
                                trackPoints.add(
                                    TrackPoint(
                                        latitude = (gate.start.latitude + gate.end.latitude) / 2.0,
                                        longitude = (gate.start.longitude + gate.end.longitude) / 2.0
                                    )
                                )
                            }
                        }
                    }
                }

                if (trackPoints.isEmpty()) {
                    val fallback = TrackPoint(41.073128, 23.517839)
                    trackPoints.add(fallback)
                    android.util.Log.e("TrackSessionActivity", "Custom track V2 has no usable points: $trackId. Applied safe fallback point.")
                }

                android.util.Log.d("TrackSessionActivity", "Loaded custom track via V2: ${customTrackV2.name} (${customTrackV2.mode}) with ${trackPoints.size} points")
                
                // ✅ CRITICAL: Await start only when a valid start trigger exists
                awaitingStart = hasValidStartTrigger
                if (awaitingStart) {
                    android.util.Log.d("TrackSessionActivity", "⏰ awaitingStart set to TRUE for custom track (valid start trigger)")
                } else {
                    android.util.Log.w("TrackSessionActivity", "⚠️ Custom track has no valid start trigger, session will start immediately after countdown")
                }
            } else {
                android.util.Log.e("TrackSessionActivity", "Custom track not found: $trackId")
                // Fallback to default track
                val s = TrackPoint(41.073128, 23.517839)
                trackPoints.addAll(listOf(s))
                awaitingStart = false
            }
        }

        updateSecondaryActionButton()
    }
    private fun toggleRecording() {
        // Don't toggle isRecording here! It will be set in startSession() after countdown
        if (!isRecording && !isCountdownActive) {
            // Start recording - countdown will begin
            startRecording()
        } else {
            // Stop recording
            isRecording = false
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
        // For both official and custom tracks: show dialog if awaitingStart is true
        if (awaitingStart) {
            showAwaitingStartDialog()
        }

        isRecording = true
        sessionStartTime = System.currentTimeMillis()
        btnStartStop.text = getString(R.string.track_button_stop)
        btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.red))
        linearAccelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        // ACCELEROMETER вече е регистриран в setupSensors() (винаги активен за g-сили)
        rotationVector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        handler.post(updateRunnable)
        currentLap = 0
        
        // ✅ Keep zero until start crossing only when awaitingStart is enabled
        lapStartTime = if (awaitingStart) 0L else System.currentTimeMillis()
        
        sectorStartTime = 0L
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
        maxSpeed = 0f
        maxAcceleration = 0f
        maxBraking = 0f
        maxCorneringLeftG = 0f
        maxCorneringRightG = 0f
        maxLeanAngle = 0f
        resetPeakDetectors()
        statsFilteredLongG = 0f
        statsFilteredLatG = 0f
        smoothedConfidence = 1f
        
        // Initialize first lap data
        // NOTE: For custom tracks, startTime will be set when crossing start/finish line
        currentLapData = LapData(
            lapNumber = 1,
            startTime = lapStartTime
        )
        previousLocationForCrossing = null
        lastStartFinishCrossAtMs = 0L
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
        // Не премахваме ACCELEROMETER сензора, защото се нуждаем от него за g-сили
        linearAccelSensor?.let { sensorManager.unregisterListener(this, it) }
        rotationVector?.let { sensorManager.unregisterListener(this, it) }
        gyroscope?.let { sensorManager.unregisterListener(this, it) }
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
        // Не премахваме ACCELEROMETER сензора, защото се нуждаем от него за g-сили
        linearAccelSensor?.let { sensorManager.unregisterListener(this, it) }
        rotationVector?.let { sensorManager.unregisterListener(this, it) }
        gyroscope?.let { sensorManager.unregisterListener(this, it) }
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
            addLapToUI(currentLap, lapTimeFormatted, isNewBest)
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
        // Lean angle UI is updated in the sensor pipeline; maintain only max here
        if (isMotorcycle) {
            maxLeanAngle = max(maxLeanAngle, kotlin.math.abs(currentCalibratedLean))
        }
    }

    private fun applyStatsDeadband(value: Float): Float {
        return if (abs(value) < statsDeadbandG) 0f else value
    }

    private fun resetPeakDetectors() {
        accelerationPeakDetector.committed = 0f
        accelerationPeakDetector.candidate = 0f
        accelerationPeakDetector.candidateSinceMs = 0L

        brakingPeakDetector.committed = 0f
        brakingPeakDetector.candidate = 0f
        brakingPeakDetector.candidateSinceMs = 0L

        corneringLeftPeakDetector.committed = 0f
        corneringLeftPeakDetector.candidate = 0f
        corneringLeftPeakDetector.candidateSinceMs = 0L

        corneringRightPeakDetector.committed = 0f
        corneringRightPeakDetector.candidate = 0f
        corneringRightPeakDetector.candidateSinceMs = 0L
    }

    private fun updatePeakDetector(detector: PeakDetector, sample: Float, nowMs: Long): Float {
        val currentMax = detector.committed

        if (sample > currentMax + peakEntryHysteresisG) {
            if (detector.candidateSinceMs == 0L) {
                detector.candidate = sample
                detector.candidateSinceMs = nowMs
            } else {
                detector.candidate = max(detector.candidate, sample)
            }

            if (nowMs - detector.candidateSinceMs >= peakHoldMs) {
                detector.committed = max(detector.committed, detector.candidate)
                detector.candidate = 0f
                detector.candidateSinceMs = 0L
            }
        } else if (sample < currentMax + peakExitHysteresisG) {
            detector.candidate = 0f
            detector.candidateSinceMs = 0L
        }

        return detector.committed
    }

    private fun computeSampleConfidence(nowNs: Long): Float {
        val gravityMagnitude = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2])
        val gravityScore = (1f - (abs(gravityMagnitude - SensorManager.GRAVITY_EARTH) / 2.0f)).coerceIn(0f, 1f)

        val accelAgeMs = if (accelTimestampNs == 0L) Float.MAX_VALUE else (nowNs - accelTimestampNs) / 1_000_000f
        val rotationAgeMs = if (rotationTimestampNs == 0L) Float.MAX_VALUE else (nowNs - rotationTimestampNs) / 1_000_000f
        val worldAgeMs = if (worldAccelTimestampNs == 0L) Float.MAX_VALUE else (nowNs - worldAccelTimestampNs) / 1_000_000f
        val gyroAgeMs = if (gyroTimestampNs == 0L) Float.MAX_VALUE else (nowNs - gyroTimestampNs) / 1_000_000f

        val accelFreshness = (1f - (accelAgeMs / 180f)).coerceIn(0f, 1f)
        val rotationFreshness = (1f - (rotationAgeMs / 280f)).coerceIn(0f, 1f)
        val worldFreshness = (1f - (worldAgeMs / 200f)).coerceIn(0f, 1f)
        val gyroFreshness = (1f - (gyroAgeMs / 300f)).coerceIn(0f, 1f)

        val gyroStability = (1f - ((lastGyroMagnitude - 2.5f) / 5.5f)).coerceIn(0f, 1f)
        val speedMs = lastLocation?.speed ?: 0f
        val speedScore = if (speedMs > 5f) 1f else if (speedMs > 1.5f) 0.85f else 0.7f

        val weighted = 0.28f * gravityScore +
            0.20f * accelFreshness +
            0.20f * rotationFreshness +
            0.15f * worldFreshness +
            0.10f * gyroFreshness +
            0.07f * gyroStability

        val rawConfidence = (weighted * speedScore).coerceIn(0f, 1f)
        smoothedConfidence = confidenceLowPassAlpha * rawConfidence + (1f - confidenceLowPassAlpha) * smoothedConfidence
        return smoothedConfidence.coerceIn(0f, 1f)
    }

    private fun updateSessionGForceStatistics(rawLatG: Float, rawLongG: Float, confidence: Float) {
        if (!isRecording || awaitingStart || lapStartTime <= 0L) return
        if (confidence < minConfidenceForStats) return

        statsFilteredLatG = statsFilterAlpha * rawLatG + (1f - statsFilterAlpha) * statsFilteredLatG
        statsFilteredLongG = statsFilterAlpha * rawLongG + (1f - statsFilterAlpha) * statsFilteredLongG

        val lateral = applyStatsDeadband(statsFilteredLatG)
        val longitudinal = applyStatsDeadband(statsFilteredLongG)

        val nowMs = SystemClock.elapsedRealtime()
        val accelerationSample = max(0f, -longitudinal)
        val brakingSample = max(0f, longitudinal)
        val corneringLeftSample = max(0f, lateral)
        val corneringRightSample = max(0f, -lateral)

        maxAcceleration = updatePeakDetector(accelerationPeakDetector, accelerationSample, nowMs)
        maxBraking = updatePeakDetector(brakingPeakDetector, brakingSample, nowMs)
        maxCorneringLeftG = updatePeakDetector(corneringLeftPeakDetector, corneringLeftSample, nowMs)
        maxCorneringRightG = updatePeakDetector(corneringRightPeakDetector, corneringRightSample, nowMs)
    }

    private fun updateInertialForcesFromLinearAcceleration(deviceLinearAccel: FloatArray) {
        val isCalibrated = DragCalibration.isUniversalCalibrated
        val nowNs = SystemClock.elapsedRealtimeNanos()

        val calibratedLatG: Float
        val calibratedLongG: Float

        if (isCalibrated) {
            val forwardAccel = DragCalibration.getSignedForwardAccelerationFromLinear(deviceLinearAccel)
            val lateralAccel = DragCalibration.getSignedLateralAccelerationFromLinear(deviceLinearAccel)

            calibratedLatG = -lateralAccel / 9.81f
            calibratedLongG = -forwardAccel / 9.81f
        } else {
            calibratedLatG = deviceLinearAccel[0] / 9.81f
            calibratedLongG = deviceLinearAccel[1] / 9.81f
        }

        var fusedLatG = calibratedLatG
        var fusedLongG = calibratedLongG

        if (hasFusedWorldAccel && hasSmoothedBearing) {
            val east = fusedWorldAccel[0]
            val north = fusedWorldAccel[1]
            val sinB = sin(smoothedBearingRad)
            val cosB = cos(smoothedBearingRad)

            val forwardAccelFromHeading = east * sinB + north * cosB
            val rightAccelFromHeading = east * cosB - north * sinB

            val headingLatG = -rightAccelFromHeading / 9.81f
            val headingLongG = -forwardAccelFromHeading / 9.81f

            val headingBlend = if (isCalibrated) 0.25f else 0.65f
            fusedLatG = (1f - headingBlend) * fusedLatG + headingBlend * headingLatG
            fusedLongG = (1f - headingBlend) * fusedLongG + headingBlend * headingLongG
        }

        val confidence = computeSampleConfidence(nowNs)

        val speedMs = lastLocation?.speed ?: 0f
        val canLearnBias = isStationary || !isRecording || awaitingStart || speedMs < 0.8f
        if (canLearnBias) {
            forwardBiasG = (1f - biasAlpha) * forwardBiasG + biasAlpha * fusedLongG
            lateralBiasG = (1f - biasAlpha) * lateralBiasG + biasAlpha * fusedLatG
        }

        var correctedLongG = fusedLongG - forwardBiasG
        var correctedLatG = fusedLatG - lateralBiasG

        val adaptiveDeadband = deadbandG + (1f - confidence) * 0.08f
        if (abs(correctedLongG) < adaptiveDeadband) correctedLongG = 0f
        if (abs(correctedLatG) < adaptiveDeadband) correctedLatG = 0f

        val confidenceScale = (0.65f + 0.35f * confidence).coerceIn(0.65f, 1f)
        correctedLongG *= confidenceScale
        correctedLatG *= confidenceScale

        val adaptiveDisplayAlpha = (0.25f + 0.35f * confidence).coerceIn(0.25f, 0.60f)
        displayLY = adaptiveDisplayAlpha * correctedLongG + (1f - adaptiveDisplayAlpha) * displayLY
        displayLX = adaptiveDisplayAlpha * correctedLatG + (1f - adaptiveDisplayAlpha) * displayLX

        forwardGSmooth = gSmoothAlpha * displayLY + (1f - gSmoothAlpha) * forwardGSmooth
        lateralGSmooth = gSmoothAlpha * displayLX + (1f - gSmoothAlpha) * lateralGSmooth

        val finalLongG = clamp(forwardGSmooth, -maxDisplayG, maxDisplayG)
        val finalLatG = clamp(lateralGSmooth, -maxDisplayG, maxDisplayG)

        speedGauge.gForceX = finalLatG
        speedGauge.gForceY = finalLongG
        updateSessionGForceStatistics(finalLatG, finalLongG, confidence)
    }

    private fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val milliseconds = (timeMs % 1000) / 10
        return String.format("%02d:%02d.%02d", minutes, seconds, milliseconds)
    }
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { ev ->
            // Използваме същата логика като в ForegroundService.kt за g-сили
            // Това трябва да работи винаги, не само когато записваме
            if (ev.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                // Update live gravity filter (за lean angle и др.)
                gravity[0] = alphaGravity * gravity[0] + (1 - alphaGravity) * ev.values[0]
                gravity[1] = alphaGravity * gravity[1] + (1 - alphaGravity) * ev.values[1]
                gravity[2] = alphaGravity * gravity[2] + (1 - alphaGravity) * ev.values[2]
                accelTimestampNs = ev.timestamp

                latestRawAccel[0] = ev.values[0]
                latestRawAccel[1] = ev.values[1]
                latestRawAccel[2] = ev.values[2]
                if (!preferLinearAccel) {
                    val fallbackLinear = floatArrayOf(
                        ev.values[0] - gravity[0],
                        ev.values[1] - gravity[1],
                        ev.values[2] - gravity[2]
                    )
                    updateInertialForcesFromLinearAcceleration(fallbackLinear)
                }
            }
            
            // Останалата логика работи само когато записваме
            // TODO: Върни тази проверка след тестване на g-силите!
            // if (!isRecording || awaitingStart || lapStartTime == 0L) return@let
            when (ev.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    rotationTimestampNs = ev.timestamp
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, ev.values)
                }
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    if (preferLinearAccel) {
                        linearAccel[0] = ev.values[0]
                        linearAccel[1] = ev.values[1]
                        linearAccel[2] = ev.values[2]
                        updateInertialForcesFromLinearAcceleration(linearAccel)
                        processLinearAccelerationAndUpdate(linearAccel, ev.timestamp)
                    }
                    
                    // Единен pipeline: G-силите се изчисляват в processLinearAccelerationAndUpdate
                    
                    // SDK handles sensor data - no need to collect
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    // Fallback path when no dedicated linear acceleration (за processLinearAccelerationAndUpdate)
                    if (!preferLinearAccel) {
                    linearAccel[0] = ev.values[0] - gravity[0]
                    linearAccel[1] = ev.values[1] - gravity[1]
                    linearAccel[2] = ev.values[2] - gravity[2]
                    updateInertialForcesFromLinearAcceleration(linearAccel)
                    processLinearAccelerationAndUpdate(linearAccel, ev.timestamp)
                    }
                    
                    // Единен pipeline: G-силите се изчисляват в processLinearAccelerationAndUpdate
                    
                    // SDK handles sensor data - no need to collect
                }
                Sensor.TYPE_GYROSCOPE -> {
                    gyroTimestampNs = ev.timestamp
                    val gyroMag = sqrt(ev.values[0] * ev.values[0] + ev.values[1] * ev.values[1] + ev.values[2] * ev.values[2])
                    lastGyroMagnitude = 0.2f * gyroMag + 0.8f * lastGyroMagnitude
                    gyroscopeData.addAll(ev.values.sliceArray(0..2).toList())
                    if (gyroscopeData.size > 1000) {
                        gyroscopeData.removeAt(0)
                    }
                    // Add to current lap data
                    if (isRecording && lapStartTime > 0L) {
                        currentLapData.gyroscopeData.addAll(ev.values.sliceArray(0..2).toList())
                        android.util.Log.d("TrackSessionActivity", "Added gyro data to lap: ${ev.values.size} values")
                        
                        // SDK handles sensor data - no need to collect
                    }
                }
            }
        }
        
        // Update gauge with current data including predictive gap
        updateGauge()
    }
    private fun processLinearAccelerationAndUpdate(deviceAccel: FloatArray, timestampNs: Long) {
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

        fusedWorldAccel[0] = worldFusionAlpha * worldAccel[0] + (1f - worldFusionAlpha) * fusedWorldAccel[0]
        fusedWorldAccel[1] = worldFusionAlpha * worldAccel[1] + (1f - worldFusionAlpha) * fusedWorldAccel[1]
        fusedWorldAccel[2] = worldFusionAlpha * worldAccel[2] + (1f - worldFusionAlpha) * fusedWorldAccel[2]
        hasFusedWorldAccel = true
        worldAccelTimestampNs = timestampNs

        val east = worldAccel[0]
        val north = worldAccel[1]

        // Stationary detection on linear acceleration magnitude
        val worldMag = kotlin.math.sqrt((east * east + north * north + worldAccel[2] * worldAccel[2]).toDouble()).toFloat()
        if (worldMag < stationaryAccThreshold) {
            stationaryCounter = (stationaryCounter + 1).coerceAtMost(1000)
        } else {
            stationaryCounter = (stationaryCounter - 2).coerceAtLeast(0)
        }
        isStationary = stationaryCounter >= stationaryCountToLock

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
                    (-Math.toDegrees(Math.asin((y / totalGravity).toDouble().coerceIn(-1.0, 1.0)))).toFloat()
                } else {
                    (-Math.toDegrees(Math.asin((x / totalGravity).toDouble().coerceIn(-1.0, 1.0)))).toFloat()
                }
            } else 0f
            val delta = kotlin.math.abs(rawTilt - filteredAngle)
            val adaptiveAlpha = (0.01f + (delta / 45f)).coerceIn(0.05f, 0.3f)
            filteredAngle += adaptiveAlpha * (rawTilt - filteredAngle)
            currentCalibratedLean = (offsetAngle - filteredAngle).coerceIn(-90f, 90f)
        }

        runOnUiThread {
            // НЕ извикваме setGForces тук защото те вече са зададени в onSensorChanged!
            // Само обновяваме lean angle ако е мотор
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
        if (isRecording && lapStartTime > 0L) {
            currentLapData.accelerationData.addAll(deviceAccel.toList())
            currentLapData.leanAngleData.add(currentCalibratedLean)
            currentLapData.timestamps.add(System.currentTimeMillis())
        } else {
            android.util.Log.d("TrackSessionActivity", "Not adding sensor data: isRecording=$isRecording, awaitingStart=$awaitingStart, lapStartTime=$lapStartTime")
        }
    }
    private fun clamp(v: Float, min: Float, max: Float) = when {
        v < min -> min
        v > max -> max
        else -> v
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onLocationChanged(location: Location) {
        previousLocationForCrossing = lastLocation
        lastLocation = location
        val speedKmh = location.speed * 3.6f
        
        // ✅ CRITICAL FIX: Only record GPS data if we're recording AND lapStartTime is set
        if (isRecording && lapStartTime > 0L) {
            // Accumulate distance traveled in current sector and lap
            val nowT = location.time
            if (lastLocationTimeMs != 0L) {
                val dtSec = ((nowT - lastLocationTimeMs) / 1000f).coerceIn(0.05f, 1.5f)
                val inc = location.speed * dtSec // speed is m/s → distance meters
                sectorDistanceAccum += inc
                lapDistanceAccum += inc
            }
            lastLocationTimeMs = nowT
            
            speedData.add(speedKmh)
            // Add to current lap data
            currentLapData.speedData.add(speedKmh)
            
            val currentTime = System.currentTimeMillis()
            val relativeTimestamp = currentTime - lapStartTime
            
            android.util.Log.d("TrackSessionActivity", "📍 GPS DATA ADDED:")
            android.util.Log.d("TrackSessionActivity", "   Current time: $currentTime")
            android.util.Log.d("TrackSessionActivity", "   Lap start time: $lapStartTime")
            android.util.Log.d("TrackSessionActivity", "   Relative timestamp: $relativeTimestamp")
            android.util.Log.d("TrackSessionActivity", "   Speed: ${speedKmh}km/h")
            android.util.Log.d("TrackSessionActivity", "   Total GPS points so far: ${currentLapData.routePoints.size + 1}")
            
            currentLapData.routePoints.add(RoutePoint(
                geoPoint = com.example.clinometer.GeoPoint(location.latitude, location.longitude),
                speed = speedKmh,
                angle = currentCalibratedLean,
                timestamp = relativeTimestamp, // Използваме относително време!
                absoluteTime = location.time
            ))
        } else {
            android.util.Log.d("TrackSessionActivity", "Not adding GPS data: isRecording=$isRecording, awaitingStart=$awaitingStart, lapStartTime=$lapStartTime")
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
        
        // Only check track point proximity if we're actually recording
        if (isRecording) {
            checkTrackPointProximity(location)
        }
    }
    
    private fun checkStartFinishLineCrossing(
        location: Location, 
        point1: TrackPoint, 
        point2: TrackPoint,
        ignoreDebounce: Boolean = false
    ): Boolean {
        val previous = previousLocationForCrossing ?: return false
        val now = System.currentTimeMillis()
        var crossed = gateCrossingEngine.didCrossLine(
            previousLat = previous.latitude,
            previousLon = previous.longitude,
            currentLat = location.latitude,
            currentLon = location.longitude,
            lineStartLat = point1.geoPoint.latitude,
            lineStartLon = point1.geoPoint.longitude,
            lineEndLat = point2.geoPoint.latitude,
            lineEndLon = point2.geoPoint.longitude
        )

        if (!crossed && ignoreDebounce) {
            val previousSide = lineSide(
                point1.geoPoint.latitude,
                point1.geoPoint.longitude,
                point2.geoPoint.latitude,
                point2.geoPoint.longitude,
                previous.latitude,
                previous.longitude
            )
            val currentSide = lineSide(
                point1.geoPoint.latitude,
                point1.geoPoint.longitude,
                point2.geoPoint.latitude,
                point2.geoPoint.longitude,
                location.latitude,
                location.longitude
            )
            val minDistanceToLine = minOf(
                gateCrossingEngine.distanceToLineMeters(
                    pointLat = previous.latitude,
                    pointLon = previous.longitude,
                    lineStartLat = point1.geoPoint.latitude,
                    lineStartLon = point1.geoPoint.longitude,
                    lineEndLat = point2.geoPoint.latitude,
                    lineEndLon = point2.geoPoint.longitude
                ),
                gateCrossingEngine.distanceToLineMeters(
                    pointLat = location.latitude,
                    pointLon = location.longitude,
                    lineStartLat = point1.geoPoint.latitude,
                    lineStartLon = point1.geoPoint.longitude,
                    lineEndLat = point2.geoPoint.latitude,
                    lineEndLon = point2.geoPoint.longitude
                )
            )

            val sideChanged = (previousSide > 0.0 && currentSide < 0.0) || (previousSide < 0.0 && currentSide > 0.0)
            val veryNearLine = minDistanceToLine <= 12.0
            if (sideChanged && veryNearLine) {
                crossed = true
                android.util.Log.d("TrackSessionActivity", "✅ POINT_TO_POINT tolerant line cross detected (near-line side change)")
            }
        }

        if (!crossed) {
            return false
        }

        if (!ignoreDebounce && now - lastStartFinishCrossAtMs < startFinishCrossDebounceMs) {
            android.util.Log.d("TrackSessionActivity", "⏸️ Start/finish debounce active (${now - lastStartFinishCrossAtMs}ms)")
            return false
        }

        android.util.Log.d("TrackSessionActivity", "✅ STRICT LINE CROSS DETECTED")
        lastStartFinishCrossAtMs = now
        return true
    }

    private fun lineSide(
        lineStartLat: Double,
        lineStartLon: Double,
        lineEndLat: Double,
        lineEndLon: Double,
        pointLat: Double,
        pointLon: Double
    ): Double {
        val ax = lineStartLon
        val ay = lineStartLat
        val bx = lineEndLon
        val by = lineEndLat
        val px = pointLon
        val py = pointLat
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax)
    }
    
    private fun checkTrackPointProximity(location: Location) {
        if (trackPoints.isEmpty()) return
        
        // For custom circuit tracks with start/finish LINE (4 points total: 2 + 2 duplicate):
        // Check if we're crossing the start/finish line
        if (startFinishLineIndices.isNotEmpty() && startFinishLineIndices.size >= 2) {
            // Check line crossing
            if (awaitingStart) {
                if (currentTrackMode == TrackMode.POINT_TO_POINT) {
                    handlePointToPointStagingAndStart(location, trackPoints[0], trackPoints[1])
                    return
                }

                val distanceToStartLine = gateCrossingEngine.distanceToLineMeters(
                    pointLat = location.latitude,
                    pointLon = location.longitude,
                    lineStartLat = trackPoints[0].geoPoint.latitude,
                    lineStartLon = trackPoints[0].geoPoint.longitude,
                    lineEndLat = trackPoints[1].geoPoint.latitude,
                    lineEndLon = trackPoints[1].geoPoint.longitude
                )
                updateAwaitingStartDialog(distanceToStartLine)
                val meters = distanceToStartLine.toInt().coerceAtLeast(0)
                tvLapTime.text = "До старт/финал: ${meters} m"

                // Check initial start/finish line (indices 0 and 1)
                val crossed = checkStartFinishLineCrossing(location, trackPoints[0], trackPoints[1])
                if (crossed) {
                    beginTimedSession(location)
                }
                return
            } else if (lapStartTime == 0L) {
                // This should not happen - awaitingStart should handle this case
                android.util.Log.w("TrackSessionActivity", "⚠️ UNEXPECTED: lapStartTime == 0L but not awaitingStart")
                return
            } else {
                // Check if we're at the lap completion line (last 2 points)
                val lapLineIndex1 = trackPoints.size - 2
                val lapLineIndex2 = trackPoints.size - 1
                
                val crossed = checkStartFinishLineCrossing(
                    location = location,
                    point1 = trackPoints[lapLineIndex1],
                    point2 = trackPoints[lapLineIndex2],
                    ignoreDebounce = currentTrackMode == TrackMode.POINT_TO_POINT
                )
                if (crossed) {
                    if (currentTrackMode == TrackMode.POINT_TO_POINT) {
                        finalizePointToPointRun()
                        android.util.Log.d("TrackSessionActivity", "✅ POINT_TO_POINT FINISH LINE CROSSED - stopping session")
                        stopRecording()
                        return
                    }

                    val lapElapsedTime = lapTimingEngine.lapElapsedMs(lapStartTime)
                    if (!lapTimingEngine.canCompleteLap(lapStartTime)) {
                        android.util.Log.d("TrackSessionActivity", "⏸️ Crossed line but too soon! Elapsed: ${lapElapsedTime / 1000}s (need ${lapTimingEngine.minLapTimeMs / 1000}s)")
                        return
                    }
                    
                    android.util.Log.d("TrackSessionActivity", "🎉 CROSSED START/FINISH LINE!")
                    android.util.Log.d("TrackSessionActivity", "🏁 LAP COMPLETED!")
                    
                    // 🔍 DIAGNOSTIC: Log finish timing information
                    android.util.Log.d("TrackSessionActivity", "⏰ FINISH TIMING DIAGNOSTIC:")
                    android.util.Log.d("TrackSessionActivity", "   Finish line detected at: ${System.currentTimeMillis()}")
                    android.util.Log.d("TrackSessionActivity", "   Lap duration: ${lapElapsedTime}ms")
                    android.util.Log.d("TrackSessionActivity", "   Total GPS points: ${currentLapData.routePoints.size}")
                    if (currentLapData.routePoints.isNotEmpty()) {
                        android.util.Log.d("TrackSessionActivity", "   Last GPS point timestamp: ${currentLapData.routePoints.last().timestamp}")
                    }
                    
                    // Record sector and lap
                    val sectorTime = System.currentTimeMillis() - sectorStartTime
                    sectorTimes.add(sectorTime)
                    sectorDistances.add(sectorDistanceAccum)
                    
                    recordLap()
                    
                    // Stay at lap completion line for next lap
                    currentTrackPointIndex = lapLineIndex1
                }
                return
            }
        }
        
        // Fallback for old single-point logic (shouldn't happen for new custom tracks)
        val targetIndex = if (awaitingStart) 0 else currentTrackPointIndex
        if (targetIndex >= trackPoints.size) return
        
        val trackPoint = trackPoints[targetIndex]
        val trackLocation = Location("track").apply {
            latitude = trackPoint.geoPoint.latitude
            longitude = trackPoint.geoPoint.longitude
        }
        val distance = location.distanceTo(trackLocation)
        val threshold = if (awaitingStart) startProximityMeters else sectorProximityMeters
        
        android.util.Log.d("TrackSessionActivity", "📍 Check point $targetIndex: distance=${distance.toInt()}m, threshold=${threshold.toInt()}m, awaitingStart=$awaitingStart")
        
        val shouldTrigger = distance < threshold
        
        if (shouldTrigger) {
            // Anti-bounce: For lap completion point, require minimum lap time
            if (!awaitingStart && targetIndex == trackPoints.size - 1 && currentTrackMode == TrackMode.CIRCUIT) {
                val lapElapsedTime = lapTimingEngine.lapElapsedMs(lapStartTime)
                
                if (!lapTimingEngine.canCompleteLap(lapStartTime)) {
                    android.util.Log.d("TrackSessionActivity", "⏸️ Too soon for lap! Elapsed: ${lapElapsedTime / 1000}s (need ${lapTimingEngine.minLapTimeMs / 1000}s)")
                    return
                }
            }
            
            android.util.Log.d("TrackSessionActivity", "🎉 TRIGGERED at index $targetIndex")
            
            // Handle awaiting start
            if (awaitingStart) {
                android.util.Log.d("TrackSessionActivity", "🚀 SESSION STARTED!")
                beginTimedSession(location)
                return
            }
            
            // Record sector
            val sectorTime = System.currentTimeMillis() - sectorStartTime
            sectorTimes.add(sectorTime)
            sectorDistances.add(sectorDistanceAccum)
            lastSectorChangeAtMs = System.currentTimeMillis()
            
            // Check if this is START_FINISH point (lap boundary)
            val isStartFinish = targetIndex < trackPointTypes.size && 
                               trackPointTypes[targetIndex] == com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.START_FINISH
            
            if (bestLapTime != Long.MAX_VALUE && bestSectorTimes.isNotEmpty()) {
                val justCompletedSectors = sectorTimes.size
                val currentElapsedMs = sectorTimes.sum()
                val isSlower = if (isStartFinish) {
                    currentElapsedMs >= bestLapTime
                } else {
                    val bestElapsedMs = bestSectorTimes.take(justCompletedSectors).sum()
                    currentElapsedMs >= bestElapsedMs
                }
                speedGauge.lockPredictiveColor(isSlower)
            }
            
            // Update best sector
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
            
            // Advance to next point
            currentTrackPointIndex++
            
            // Check if we completed a lap (reached end with START_FINISH point)
            if (currentTrackPointIndex >= trackPoints.size) {
                android.util.Log.d("TrackSessionActivity", "🏁 LAP COMPLETED!")
                if (currentTrackMode == TrackMode.POINT_TO_POINT) {
                    finalizePointToPointRun()
                    android.util.Log.d("TrackSessionActivity", "✅ POINT_TO_POINT FINISH REACHED - stopping session")
                    stopRecording()
                    return
                }
                recordLap()
                // For custom circuit tracks with 2 points (start/finish duplicated), 
                // go back to point 1 (the duplicate) to check for next lap completion
                // Point 0 is ONLY for initial start detection
                currentTrackPointIndex = if (trackPoints.size == 2) 1 else 0
            }
        }
    }

    private fun beginTimedSession(location: Location) {
        awaitingStart = false
        lapStartTime = System.currentTimeMillis()
        android.util.Log.d("TrackSessionActivity", "⏰ LAP START TIME SET: $lapStartTime")

        val currentLocation = lastLocation
        if (currentLocation != null) {
            val currentSpeedKmh = currentLocation.speed * 3.6f
            currentLapData.speedData.add(currentSpeedKmh)
            currentLapData.routePoints.add(
                RoutePoint(
                    geoPoint = com.example.clinometer.GeoPoint(currentLocation.latitude, currentLocation.longitude),
                    speed = currentSpeedKmh,
                    angle = currentCalibratedLean,
                    timestamp = 0L,
                    absoluteTime = currentLocation.time
                )
            )
        }

        val updatedRoutePoints = currentLapData.routePoints.map { routePoint ->
            routePoint.copy(timestamp = routePoint.absoluteTime - lapStartTime)
        }
        currentLapData = currentLapData.copy(
            startTime = lapStartTime,
            routePoints = updatedRoutePoints.toMutableList()
        )

        sectorStartTime = lapStartTime
        currentSector = 0
        currentTrackPointIndex = if (startFinishLineIndices.size >= 4) 2 else 1
        statsFilteredLongG = 0f
        statsFilteredLatG = 0f
        resetPeakDetectors()
        smoothedConfidence = 1f

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
        speedGauge.unlockPredictiveColor()
        dismissAwaitingStartDialog()
    }

    private fun handlePointToPointStagingAndStart(location: Location, startLineA: TrackPoint, startLineB: TrackPoint) {
        val distanceToStartLine = gateCrossingEngine.distanceToLineMeters(
            pointLat = location.latitude,
            pointLon = location.longitude,
            lineStartLat = startLineA.geoPoint.latitude,
            lineStartLon = startLineA.geoPoint.longitude,
            lineEndLat = startLineB.geoPoint.latitude,
            lineEndLon = startLineB.geoPoint.longitude
        )

        updateAwaitingStartDialog(distanceToStartLine)

        if (distanceToStartLine <= pointToPointStartHintMeters) {
            val meters = distanceToStartLine.toInt().coerceAtLeast(0)
            tvLapTime.text = "До старта: ${meters} m"
        } else {
            tvLapTime.text = "Приближи се до старта, за да започне състезанието"
        }

        val strictCrossed = checkStartFinishLineCrossing(
            location = location,
            point1 = startLineA,
            point2 = startLineB,
            ignoreDebounce = true
        )

        if (strictCrossed) {
            android.util.Log.d("TrackSessionActivity", "POINT_TO_POINT start line crossed - run started")
            beginTimedSession(location)
        }
    }

    private fun updateAwaitingStartDialog(distanceToStartLineMeters: Double) {
        val dialog = awaitingStartDialog ?: return
        val messageView = awaitingStartMessageView ?: return

        val meters = distanceToStartLineMeters.toInt().coerceAtLeast(0)
        val message = buildAwaitingStartMessage(
            metersLabel = "${meters} м",
            isNearStartLine = distanceToStartLineMeters <= pointToPointStartHintMeters
        )
        if (dialog.isShowing) {
            messageView.text = message
        }
    }

    private fun buildAwaitingStartMessage(metersLabel: String, isNearStartLine: Boolean): CharSequence {
        val prefix = "Разстояние от старт/финал: "
        val body = if (isNearStartLine) {
            "Когато пресечеш старт/финал линията, измерването започва."
        } else {
            "Приближи се до старт/финал линията, за да започне състезанието."
        }

        val fullMessage = "$prefix$metersLabel\n$body"
        val spannable = SpannableStringBuilder(fullMessage)
        val valueStart = prefix.length
        val valueEnd = valueStart + metersLabel.length
        spannable.setSpan(
            ForegroundColorSpan(getColor(R.color.primary_color)),
            valueStart,
            valueEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannable
    }

    private fun finalizePointToPointRun() {
        if (!isRecording || lapStartTime <= 0L) return

        val finishTimestamp = System.currentTimeMillis()
        val runElapsedMs = finishTimestamp - lapStartTime

        currentLap = 1
        totalLaps = 1
        tvCurrentLap.text = "1"

        lapTimes.clear()
        lapTimes.add(runElapsedMs)
        bestLapTime = runElapsedMs

        currentLapData = currentLapData.copy(
            lapNumber = 1,
            endTime = finishTimestamp
        )
        if (lapData.isEmpty()) {
            lapData.add(currentLapData)
        } else {
            lapData[lapData.lastIndex] = currentLapData
        }

        showToast("Финиш! Време: ${formatTime(runElapsedMs)}")
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
        // ACCELEROMETER вече е регистриран в setupSensors() (винаги активен за g-сили)
        if (isRecording) {
            linearAccelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            rotationVector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }
    override fun onPause() {
        super.onPause()
        // Не премахваме ACCELEROMETER сензора, защото се нуждаем от него за g-сили
        linearAccelSensor?.let { sensorManager.unregisterListener(this, it) }
        rotationVector?.let { sensorManager.unregisterListener(this, it) }
        gyroscope?.let { sensorManager.unregisterListener(this, it) }
    }
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        handler.removeCallbacks(countdownRunnable)
        locationManager.removeUpdates(this)
        soundManager.release()
    }
    private fun addLapToUI(lapNumber: Int, lapTime: String, isBestLap: Boolean) {
        tvNoLaps.visibility = android.view.View.GONE
        val inflater = layoutInflater
        val lapView = inflater.inflate(R.layout.lap_item_session_template, llLapsContainer, false)
        lapView.tag = lapNumber
        val tvLapNumber = lapView.findViewById<TextView>(R.id.tvLapNumber)
        val tvLapTime = lapView.findViewById<TextView>(R.id.tvLapTime)
        tvLapNumber.text = getString(R.string.track_lap_label, lapNumber)
        tvLapTime.text = lapTime
        llLapsContainer.addView(lapView, 0)
        if (isBestLap) {
            markBestLapInUi(lapNumber)
        }
    }

    private fun markBestLapInUi(bestLapNumber: Int) {
        for (index in 0 until llLapsContainer.childCount) {
            val lapView = llLapsContainer.getChildAt(index)
            val tvLapNumber = lapView.findViewById<TextView>(R.id.tvLapNumber)
            val tvBestLapMarker = lapView.findViewById<TextView>(R.id.tvBestLapMarker)
            val lapNumber = lapView.tag as? Int ?: continue
            val baseLabel = getString(R.string.track_lap_label, lapNumber)
            tvLapNumber.text = baseLabel
            tvBestLapMarker.visibility = if (lapNumber == bestLapNumber) android.view.View.VISIBLE else android.view.View.GONE
        }
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
        val message = buildAwaitingStartMessage(
            metersLabel = "-- м",
            isNearStartLine = false
        )
        dismissAwaitingStartDialog()

        val dialogView = layoutInflater.inflate(R.layout.dialog_track_awaiting_start, null)
        val messageView = dialogView.findViewById<TextView>(R.id.tvAwaitingStartMessage)
        val cancelButton = dialogView.findViewById<TextView>(R.id.btnAwaitingStartCancel)

        messageView.text = message
        cancelButton.setOnClickListener {
            stopRecording()
        }

        awaitingStartMessageView = messageView
        awaitingStartDialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        awaitingStartDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        awaitingStartDialog?.show()
    }

    private fun dismissAwaitingStartDialog() {
        awaitingStartDialog?.dismiss()
        awaitingStartDialog = null
        awaitingStartMessageView = null
    }
    private fun createOuting() {
        Thread {
            try {
                val sessionDuration = sessionEndTime - sessionStartTime
                val sessionDurationFormatted = formatTime(sessionDuration)
                val bestLapFormatted = if (bestLapTime == Long.MAX_VALUE) "--:--.---" else formatTime(bestLapTime)
                val outingData = mapOf(
                    "trackName" to trackName,
                    "mode" to if (currentTrackMode == TrackMode.POINT_TO_POINT) "point_to_point" else "circuit",
                    "date" to java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(sessionStartTime)),
                    "time" to java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(sessionStartTime)),
                    "duration" to sessionDurationFormatted,
                    "totalLaps" to totalLaps.toString(),
                    "bestLapTime" to bestLapFormatted,
                    "maxSpeed" to String.format("%.1f km/h", maxSpeed),
                    "maxAcceleration" to String.format("%.2f G", maxAcceleration),
                    "maxBraking" to String.format("%.2f G", maxBraking),
                    "maxCorneringLeftG" to String.format("%.2f G", maxCorneringLeftG),
                    "maxCorneringRightG" to String.format("%.2f G", maxCorneringRightG),
                    "maxCorneringG" to String.format("%.2f G", max(maxCorneringLeftG, maxCorneringRightG)),
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
                    intent.putExtra("maxCorneringLeft", outingData["maxCorneringLeftG"])
                    intent.putExtra("maxCorneringRight", outingData["maxCorneringRightG"])
                    intent.putExtra("maxCorneringG", outingData["maxCorneringG"])
                    intent.putExtra("maxLeanAngle", outingData["maxLeanAngle"])
                    startActivity(intent)
                    finish()
                    overridePendingTransition(0, 0)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showToast(getString(R.string.track_save_error, e.message ?: "Unknown"))
                    val intent = Intent(this@TrackSessionActivity, MainContainerActivity::class.java).apply {
                        putExtra(MainContainerActivity.EXTRA_INITIAL_PAGE, MainContainerActivity.PAGE_TRACK)
                    }
                    startActivity(intent)
                    overridePendingTransition(0, 0)
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
        editor.putString("${sessionId}_outing_${outingNumber}_mode", outingData["mode"])
        editor.putString("${sessionId}_outing_${outingNumber}_duration", outingData["duration"])
        editor.putString("${sessionId}_outing_${outingNumber}_laps", outingData["totalLaps"])
        editor.putString("${sessionId}_outing_${outingNumber}_best_lap", outingData["bestLapTime"])
        editor.putString("${sessionId}_outing_${outingNumber}_max_speed", outingData["maxSpeed"])
        editor.putString("${sessionId}_outing_${outingNumber}_max_acceleration", outingData["maxAcceleration"])
        editor.putString("${sessionId}_outing_${outingNumber}_max_braking", outingData["maxBraking"])
        editor.putString("${sessionId}_outing_${outingNumber}_max_cornering_left", outingData["maxCorneringLeftG"])
        editor.putString("${sessionId}_outing_${outingNumber}_max_cornering_right", outingData["maxCorneringRightG"])
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
        
        // Apply smart map matching to each lap
        val processedLapData = mutableListOf<LapData>()
        
        for (lap in lapData) {
            if (lap.routePoints.isNotEmpty() && lap.sensorData.isNotEmpty()) {
                processedLapData.add(lap)
            } else {
                processedLapData.add(lap)
            }
        }
        
        for (i in processedLapData.indices) {
            val lap = processedLapData[i]
            val lapKey = "${sessionId}_outing_${outingNumber}_lap_data_${i + 1}"
            val json = gson.toJson(lap)
            editor.putString(lapKey, json)
            android.util.Log.d("TrackSessionActivity", "Saved lap ${i + 1}: ${lap.routePoints.size} route points (smart map matched), ${lap.speedData.size} speed samples, ${lap.accelerationData.size} accel samples")
        }
        editor.putInt("${sessionId}_outing_${outingNumber}_lap_data_count", processedLapData.size)
        android.util.Log.d("TrackSessionActivity", "Set lap data count to ${processedLapData.size}")
    }
    private fun clearActiveSession() {
        val sharedPrefs = getSharedPreferences("track_sessions", MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("has_active_session", false).remove("active_track_id").remove("active_track_name").apply()
    }
}
