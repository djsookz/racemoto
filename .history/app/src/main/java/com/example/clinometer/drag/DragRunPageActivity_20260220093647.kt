package com.example.clinometer.drag

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.location.Location
import android.os.*
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.example.clinometer.*
import com.example.clinometer.DragCalibration
import com.example.clinometer.settings.SoundManager
import com.example.clinometer.settings.UnitsManager

enum class MeasurementMode {
    ALL,
    ZERO_TO_100,
    ZERO_TO_200,
    HUNDRED_TO_200,
    QUARTER_MILE
}

class DragRunPageActivity : BaseActivity() {

    override fun getLayoutResourceId(): Int {
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            R.layout.activity_drag_run
        } else {
            R.layout.activity_drag_run
        }
    }
    override fun getNavigationItemId(): Int = R.id.navDrag

    private lateinit var btnStop: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvBigSpeed: TextView
    private var tvSpeedUnit: TextView? = null

    private lateinit var tvCard0to100: TextView
    private lateinit var tvCard0to200: TextView
    private lateinit var tvCard100to200: TextView
    private lateinit var tvCard0to402: TextView
    private lateinit var tvCard0to402Distance: TextView
    
    private var tvLabel0to100: TextView? = null
    private var tvLabel0to200: TextView? = null
    private var tvLabel100to200: TextView? = null
    private var tvLabel0to402: TextView? = null

    private lateinit var card0to100: CardView
    private lateinit var card0to200: CardView
    private lateinit var card100to200: CardView
    private lateinit var card0to402: CardView

    private var serviceBound = false
    private var foregroundService: ForegroundService? = null
    private val pollHandler = Handler(Looper.getMainLooper())
    private val POLL_INTERVAL_MS = 100L  // 100ms = 10 updates/sec, оптимално за плавност без натоварване

    private var currentSession: DragSession? = null
    private var currentAttempt: DragAttempt? = null
    private var profileId: Long = -1L
    private var temperature: Float? = null
    private var altitude: Float? = null

    private val START_SPEED_THRESHOLD = 4f  // Започваме измерване над 4 km/h за реални тестове
    private val CALIBRATION_SPEED = 10f     // Събираме данни до 10 km/h за изчисляване на ускорението
    private val MIN_ACCEL_MPS2 = 0.3        // Минимално допустимо ускорение (m/s^2)
    private val MAX_ACCEL_MPS2 = 10.0       // Максимално допустимо ускорение (m/s^2) за ограничение
    private val MAX_EXTRAPOLATION_SECONDS = 0.0 // БЕЗ екстраполация - започваме от 4 km/h
    private val MEDIAN_WINDOW_SAMPLES = 5   // Брой семпли за медианен филтър по време на калибрация
    private val KMH_TO_MPS = 1.0 / 3.6
    private var calibrationStartTime: Long = 0L
    private var extrapolatedStartTime: Long = 0L
    private var isCalibrating = false
    private var calibrationComplete = false
    private val calibrationSpeedMps: MutableList<Float> = mutableListOf()
    private val calibrationTimeNanos: MutableList<Long> = mutableListOf()

    private var serviceReady = false
    private var gpsReady = false
    private val readyCheckHandler = Handler(Looper.getMainLooper())

    private var measuring = false
    private var started = false
    private var startLocation: Location? = null
    private var startTimeNano: Long = 0L
    private var finishTimeNano: Long = -1L
    private val TARGET_METERS = 402.336f
    private val GPS_READY_ACCURACY_METERS = 30f
    private val FULL_STOP_REARM_SPEED_KMH = 40f
    private val DECELERATION_DELTA_KMH = -5f
    private val DECELERATION_MAX_SPEED_KMH = 80f
    private val ROLLING_START_MIN_KMH = 95f
    private val ROLLING_START_MAX_KMH = 99f
    private val ROLLING_FINISH_SPEED_KMH = 200f
    private var distanceCompleted = false
    private var measurementComplete = false
    private var accumulatedDistance = 0f
    private var lastLocationForDistance: Location? = null

    private var sessionBest0to100: Long = -1L
    private var sessionBest0to200: Long = -1L
    private var sessionBest100to200: Long = -1L
    private var sessionBest0to402: Long = -1L

    private var measurementMode: MeasurementMode = MeasurementMode.ALL
    private var rollingStartReady = false
    private var rolling100StartTime: Long = 0L

    private var measured0to100 = false
    private var measured0to200 = false
    private var measured100to200 = false
    private var measured0to402 = false
    private var attemptAlreadySaved = false
    
    // Sound effects
    private lateinit var soundManager: SoundManager
    private var sound100Played = false
    private var sound200Played = false
    private var sound402Played = false

    private var accelStartNano: Long = 0L
    private var attempt0to100Nanos: Long = -1L
    private var attempt0to200Nanos: Long = -1L
    private var attempt100to200Nanos: Long = -1L
    private var attempt0to402Nanos: Long = -1L
    private var timeAt100Nano: Long = -1L

    private var lastSpeed: Float = 0f
    private var decelerationDetected = false
    private var waitingForFullStop = false

    private lateinit var tvGCurrentBig: TextView
    private lateinit var gGaugeView: com.example.clinometer.GGaugeView
    private lateinit var gContainer: LinearLayout
    private var isShowingGForceInsteadOfSpeed = false
    private var lastDisplayedConvertedSpeed = 0f
    private var lastDisplayedG = 0f

    private var measurementStarted = false
    private val RESTART_COOLDOWN_MS = 5000L
    private var restartCooldownActive = false
    private var restartCooldownEndTime = 0L
    private val restartCooldownHandler = Handler(Looper.getMainLooper())
    private val restartCooldownRunnable = object : Runnable {
        override fun run() {
            if (!restartCooldownActive) return
            val remaining = restartCooldownEndTime - SystemClock.elapsedRealtime()
            if (remaining <= 0) {
                completeRestartCooldown()
            } else {
                updateRestartCooldownMessage()
                restartCooldownHandler.postDelayed(this, 200)
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as? ForegroundService.LocalBinder
            foregroundService = local?.getService()
            serviceBound = true
            serviceReady = true

            // Провери дали вече има GPS локация
            checkGPSReady()

            if (measuring) startPolling()
            updateReadyStatus()
            
            // Стартирай измерването когато service-ът се свърже
            if (!measurementStarted) {
                startMeasuring()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            serviceReady = false
            foregroundService = null
            stopPolling()
            updateReadyStatus()
        }
    }

    private fun checkGPSReady() {
        val location = foregroundService?.getLastLocation()
        if (location != null && location.accuracy < GPS_READY_ACCURACY_METERS) {
            gpsReady = true
            updateReadyStatus()
        } else {
            // Провери отново след 500ms
            readyCheckHandler.postDelayed({
                if (serviceBound) {
                    checkGPSReady()
                }
            }, 500)
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            try {
                val loc = foregroundService?.getLastLocation()
                if (loc != null) {
                    handleLocation(loc)
                }
                
                updateUIFromService()
            } finally {
                if (measuring) {
                    pollHandler.postDelayed(this, POLL_INTERVAL_MS)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        profileId = intent.getLongExtra("PROFILE_ID", -1L)
        
        // Зареждаме калибрацията за този профил
        DragCalibration.setProfile(profileId)
        Log.d("DragRunPage", "📍 Profile ID: $profileId, Calibrated: ${DragCalibration.isCalibrated}, Portrait: ${DragCalibration.isPortraitCalibrated}, Landscape: ${DragCalibration.isLandscapeCalibrated}")
        
        temperature = intent.getFloatExtra("TEMPERATURE", 0f).takeIf { it != 0f }
        altitude = intent.getFloatExtra("ALTITUDE", 0f).takeIf { it != 0f }
        
        // Get GPS coordinates if available
        val latitude = intent.getDoubleExtra("LATITUDE", 0.0).takeIf { it != 0.0 }
        val longitude = intent.getDoubleExtra("LONGITUDE", 0.0).takeIf { it != 0.0 }

        val modeString = intent.getStringExtra("MEASUREMENT_MODE") ?: "ALL"
        measurementMode = try {
            MeasurementMode.valueOf(modeString)
        } catch (e: Exception) {
            MeasurementMode.ALL
        }
        
        // Initialize sound manager
        soundManager = SoundManager(this)

        initializeViews()
        configureUIForMode()
        createNewSession()
        
        // If we have GPS coordinates from countdown, mark GPS as ready
        if (latitude != null && longitude != null) {
            gpsReady = true
        }
        
        updateReadyStatus()
        ensureServiceAndStart()
    }

    private fun updateReadyStatus() {
        runOnUiThread {
            when {
                !serviceReady -> {
                    tvStatus.text = getString(R.string.drag_status_initializing)
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                }
                !gpsReady -> {
                    tvStatus.text = getString(R.string.drag_status_waiting_gps)
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                }
                else -> {
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                    
                    // Проверяваме калибрацията на посоката
                    if (!DragCalibration.isCalibrated) {
                        // Не е калибрирано - насочваме към Settings
                        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                        tvStatus.text = "⚠️ Not calibrated! Go to Settings → Calibration"
                    } else {
                        // Вече калибрирано и готово
                        val accuracyMode = foregroundService?.getAccuracyMode() ?: "GPS_ONLY"
                        val linearAccelCal = foregroundService?.isLinearAccelCalibrated() ?: false
                        val accuracyIndicator = when (accuracyMode) {
                            "HIGH_ACCURACY" -> "🟢"
                            "GOOD_ACCURACY" -> "🟡"
                            else -> "🔴"
                        }
                        
                        val baseText = when (measurementMode) {
                            MeasurementMode.ALL -> getString(R.string.drag_status_ready_all)
                            MeasurementMode.ZERO_TO_100 -> getString(R.string.drag_status_ready_0to100)
                            MeasurementMode.ZERO_TO_200 -> getString(R.string.drag_status_ready_0to200)
                            MeasurementMode.HUNDRED_TO_200 -> getString(R.string.drag_status_ready_100to200)
                            MeasurementMode.QUARTER_MILE -> getString(R.string.drag_status_ready_quarter)
                        }
                        
                        tvStatus.text = "$accuracyIndicator $baseText"
                        Log.d("DragRunPage", "📊 Ready! DragCal: ${DragCalibration.isCalibrated}, LinearCal: $linearAccelCal, AccMode: $accuracyMode")
                    }
                }
            }
        }
    }

    private fun initializeViews() {
        btnStop = findViewById(R.id.btnStartMeasure)
        tvStatus = findViewById(R.id.tvMeasureStatus)
        tvBigSpeed = findViewById(R.id.tvBigSpeed)
        tvSpeedUnit = findViewById(R.id.tvSpeedUnit)

        tvGCurrentBig = findViewById(R.id.tvGCurrentBig)
        gGaugeView = findViewById(R.id.gGaugeView)
        gContainer = findViewById(R.id.g_container)

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) {
            val toggleListener = View.OnClickListener { togglePrimaryDisplay() }
            tvBigSpeed.setOnClickListener(toggleListener)
            tvGCurrentBig.setOnClickListener(toggleListener)
            gContainer.setOnClickListener(toggleListener)
            gGaugeView.setOnClickListener(toggleListener)
            tvBigSpeed.isClickable = true
            tvGCurrentBig.isClickable = true
            gContainer.isClickable = true
            gGaugeView.isClickable = true
        } else {
            tvBigSpeed.setOnClickListener(null)
            tvGCurrentBig.setOnClickListener(null)
            gContainer.setOnClickListener(null)
            gGaugeView.setOnClickListener(null)
            tvBigSpeed.isClickable = false
            tvGCurrentBig.isClickable = false
            gContainer.isClickable = false
            gGaugeView.isClickable = false
        }
        
        // Update speed unit label
        tvSpeedUnit?.text = UnitsManager.getSpeedUnit(this).symbol


        tvCard0to100 = findViewById(R.id.tvCard0to100Value)
        tvCard0to200 = findViewById(R.id.tvCard0to200Value)
        tvCard100to200 = findViewById(R.id.tvCard100to200Value)
        tvCard0to402 = findViewById(R.id.tvCard0to402Value)
        tvCard0to402Distance = findViewById(R.id.tvCard0to402Distance)
        
        tvLabel0to100 = findViewById(R.id.tvLabel0to100)
        tvLabel0to200 = findViewById(R.id.tvLabel0to200)
        tvLabel100to200 = findViewById(R.id.tvLabel100to200)
        tvLabel0to402 = findViewById(R.id.tvLabel0to402)
        
        // Update labels with current unit
        val speedUnit = UnitsManager.getSpeedUnit(this)
        val speed100 = UnitsManager.convertSpeed(100f, speedUnit).toInt()
        val speed200 = UnitsManager.convertSpeed(200f, speedUnit).toInt()
        tvLabel0to100?.text = "0-$speed100 ${speedUnit.symbol}"
        tvLabel0to200?.text = "0-$speed200 ${speedUnit.symbol}"
        tvLabel100to200?.text = "$speed100-$speed200 ${speedUnit.symbol}"
        tvLabel0to402?.text = "0-${UnitsManager.getQuarterMileDistance(this)}"

        card0to100 = findViewById(R.id.card0to100)
        card0to200 = findViewById(R.id.card0to200)
        card100to200 = findViewById(R.id.card100to200)
        card0to402 = findViewById(R.id.card0to402)

        btnStop.text = getString(R.string.stop_session)

        resetDisplayValues()
        tvBigSpeed.text = "0"
        applyPrimaryDisplayState()

        btnStop.setOnClickListener {
            if (measurementMode == MeasurementMode.ALL && measured0to100 && measured0to200 && measured100to200 && measured0to402) {
                finishSession()
            } else {
                showStopConfirmation()
            }
        }
    }

    private fun configureUIForMode() {
        card0to100.visibility = View.GONE
        card0to200.visibility = View.GONE
        card100to200.visibility = View.GONE
        card0to402.visibility = View.GONE

        when (measurementMode) {
            MeasurementMode.ALL -> {
                card0to100.visibility = View.VISIBLE
                card0to200.visibility = View.VISIBLE
                card100to200.visibility = View.VISIBLE
                card0to402.visibility = View.VISIBLE
                tvStatus.text = getString(R.string.drag_waiting_for_acceleration)
            }
            MeasurementMode.ZERO_TO_100 -> {
                card0to100.visibility = View.VISIBLE
                tvStatus.text = getString(R.string.drag_status_ready_0to100)
            }
            MeasurementMode.ZERO_TO_200 -> {
                card0to200.visibility = View.VISIBLE
                tvStatus.text = getString(R.string.drag_status_ready_0to200)
            }
            MeasurementMode.HUNDRED_TO_200 -> {
                card100to200.visibility = View.VISIBLE
                tvStatus.text = getString(R.string.drag_status_ready_100to200)
            }
            MeasurementMode.QUARTER_MILE -> {
                card0to402.visibility = View.VISIBLE
                tvStatus.text = getString(R.string.drag_status_ready_quarter)
            }
        }
    }

    private fun createNewSession() {
        val allSessions = DragStorage.loadDragSessions(this)
            .filter { it.profileId == profileId }

        val sessionNumber = allSessions.mapNotNull { session ->
            val match = Regex("Drag Session (\\d+)").find(session.name ?: "")
            match?.groupValues?.get(1)?.toIntOrNull()
        }.maxOrNull()?.plus(1) ?: 1

        currentSession = DragSession(
            id = System.currentTimeMillis(),
            profileId = profileId,
            name = "Drag Session $sessionNumber",
            temperature = temperature,
            altitude = altitude,
            measurementMode = measurementMode.name
        )
    }

    private fun createNewAttempt() {
        // Нулираме калибрационните флагове
        isCalibrating = false
        calibrationComplete = false
        calibrationStartTime = 0L
        extrapolatedStartTime = 0L
        calibrationSpeedMps.clear()
        calibrationTimeNanos.clear()

        // Останалата част от кода остава същата...
        attemptAlreadySaved = false
        decelerationDetected = false
        waitingForFullStop = false

        currentAttempt = DragAttempt(
            temperature = temperature,
            altitude = altitude
        )

        measuring = true
        started = false
        startLocation = null
        finishTimeNano = -1L
        startTimeNano = 0L
        distanceCompleted = false
        measurementComplete = false
        rollingStartReady = false
        rolling100StartTime = 0L
        accumulatedDistance = 0f
        lastLocationForDistance = null

        if (measurementMode != MeasurementMode.ALL) {
            measured0to100 = false
            measured0to200 = false
            measured100to200 = false
            measured0to402 = false
            
            // Reset sound flags
            sound100Played = false
            sound200Played = false
            sound402Played = false
        }

        accelStartNano = 0L
        attempt0to100Nanos = -1L
        attempt0to200Nanos = -1L
        attempt100to200Nanos = -1L
        attempt0to402Nanos = -1L
        timeAt100Nano = -1L

        if (measurementMode != MeasurementMode.ALL) {
            resetDisplayValues()
        }

        updateAttemptNumber()
    }

    private fun saveCurrentAttempt() {
        val gSamples = foregroundService?.getRecentGSamples() ?: emptyList()
        val gTimeStamps = foregroundService?.getRecentGTimeStamps() ?: emptyList()
        val gpsAccelSamples = foregroundService?.getRecentGpsAccelSamples() ?: emptyList()
        val gpsAccelTimeStamps = foregroundService?.getRecentGpsAccelTimeStamps() ?: emptyList()


        saveCurrentAttemptWithTimestamps(gSamples, gTimeStamps, gpsAccelSamples, gpsAccelTimeStamps)
    }

    private fun saveCurrentAttemptWithTimestamps(
        gSamples: List<Float>,
        gTimeStamps: List<Long>,
        gpsAccelSamples: List<Float>,
        gpsAccelTimeStamps: List<Long>
    ) {

        val speedSamplesRaw = foregroundService?.getRecentSpeedSamples() ?: emptyList()
        val speedTimeStampsRaw = foregroundService?.getRecentSpeedTimeStamps() ?: emptyList()
        currentAttempt?.let { attempt ->

            // Изчисли времената базирани на режима
            val attempt0to100Result = when (measurementMode) {
                MeasurementMode.ZERO_TO_100, MeasurementMode.ALL ->
                    if (attempt0to100Nanos > 0) attempt0to100Nanos else -1L
                else -> -1L
            }

            val attempt0to200Result = when (measurementMode) {
                MeasurementMode.ZERO_TO_200, MeasurementMode.ALL ->
                    if (attempt0to200Nanos > 0) attempt0to200Nanos else -1L
                else -> -1L
            }

            val attempt100to200Result = when (measurementMode) {
                MeasurementMode.HUNDRED_TO_200 -> {
                    val result = if (attempt100to200Nanos > 0) attempt100to200Nanos else -1L
                    result
                }
                MeasurementMode.ALL ->
                    if (attempt100to200Nanos > 0) attempt100to200Nanos else -1L
                else -> -1L
            }

            val attempt0to402Result = when (measurementMode) {
                MeasurementMode.QUARTER_MILE, MeasurementMode.ALL ->
                    if (measured0to402 && attempt0to402Nanos > 0) {
                        attempt0to402Nanos
                    } else -1L
                else -> -1L
            }

            val (windowStartNs, windowEndNs, speedCapKmh) = getMeasurementWindowAndSpeedCap(
                mode = measurementMode,
                attempt = attempt,
                attempt0to100Ns = attempt0to100Result,
                attempt0to200Ns = attempt0to200Result,
                attempt100to200Ns = attempt100to200Result
            )

            // RAW данни - без екстраполация, без синтетични точки
            val (alignedGSamples, alignedGTimes) = if (gSamples.isNotEmpty() && gTimeStamps.isNotEmpty()) {
                trimTimeSeriesToWindow(gSamples, gTimeStamps, windowStartNs, windowEndNs)
            } else {
                emptyList<Float>() to emptyList<Long>()
            }

            val (alignedGpsAccelSamples, alignedGpsAccelTimes) = if (gpsAccelSamples.isNotEmpty() && gpsAccelTimeStamps.isNotEmpty()) {
                trimTimeSeriesToWindow(gpsAccelSamples, gpsAccelTimeStamps, windowStartNs, windowEndNs)
            } else {
                emptyList<Float>() to emptyList<Long>()
            }

            val (trimmedSpeedSamplesRaw, trimmedSpeedTimes) = if (speedSamplesRaw.isNotEmpty() && speedTimeStampsRaw.isNotEmpty()) {
                trimTimeSeriesToWindow(speedSamplesRaw, speedTimeStampsRaw, windowStartNs, windowEndNs)
            } else {
                emptyList<Float>() to emptyList<Long>()
            }

            val adjustedSpeedSamples = if (speedCapKmh != null) {
                trimmedSpeedSamplesRaw.map { sample -> sample.coerceAtMost(speedCapKmh) }
            } else {
                trimmedSpeedSamplesRaw
            }

            val computedMaxSpeed = adjustedSpeedSamples.maxOrNull()
                ?: foregroundService?.getMaxSpeed()
                ?: 0f

            // Продължителност = максималното от измерените времена (нано)
            val measurementDuration = listOf(
                attempt0to100Result,
                attempt0to200Result,
                attempt100to200Result,
                attempt0to402Result
            ).filter { it > 0 }.maxOrNull() ?: 0L

            val updatedAttempt = attempt.copy(
                time0to100 = attempt0to100Result,
                time0to200 = attempt0to200Result,
                time100to200 = attempt100to200Result,
                time0to402 = attempt0to402Result,
                maxSpeed = computedMaxSpeed,
                gSamples = alignedGSamples,
                gpsAccelSamples = alignedGpsAccelSamples,
                startTime = attempt.startTime, // Запазваме rolling100StartTime за 100-200 режим
                timeStamps = alignedGTimes,
                gpsTimeStamps = alignedGpsAccelTimes,
                duration = measurementDuration,
                speedSamples = adjustedSpeedSamples,
                speedTimeStamps = trimmedSpeedTimes
            )

            val hasValidMeasurement = updatedAttempt.time0to100 > 0 ||
                    updatedAttempt.time0to200 > 0 ||
                    updatedAttempt.time100to200 > 0 ||
                    updatedAttempt.time0to402 > 0

            if (hasValidMeasurement) {
                currentSession?.attempts?.add(updatedAttempt)
                updateSessionBestTimes(updatedAttempt)

            } else {
            }
        }
    }


    private fun updateSessionBestTimes(attempt: DragAttempt) {
        if (attempt.time0to100 > 0 && (sessionBest0to100 < 0 || attempt.time0to100 < sessionBest0to100)) {
            sessionBest0to100 = attempt.time0to100
        }
        if (attempt.time0to200 > 0 && (sessionBest0to200 < 0 || attempt.time0to200 < sessionBest0to200)) {
            sessionBest0to200 = attempt.time0to200
        }
        if (attempt.time100to200 > 0 && (sessionBest100to200 < 0 || attempt.time100to200 < sessionBest100to200)) {
            sessionBest100to200 = attempt.time100to200
        }
        if (attempt.time0to402 > 0 && (sessionBest0to402 < 0 || attempt.time0to402 < sessionBest0to402)) {
            sessionBest0to402 = attempt.time0to402
        }
    }

    private fun displayTimeWithBest(current: String, best: Long): String {
        return if (measurementMode != MeasurementMode.ALL && best > 0) {
            val bestStr = formatNanos(best)
            "$current\nBest: $bestStr"
        } else {
            current
        }
    }

    private fun resetDisplayValues() {
        if (measurementMode != MeasurementMode.ALL) {
            // Показваме текущите резултати ако има такива, иначе session best
            tvCard0to100.text = if (attempt0to100Nanos > 0) {
                val timeStr = formatNanos(attempt0to100Nanos)
                if (sessionBest0to100 > 0) {
                    "$timeStr\nBest: ${formatNanos(sessionBest0to100)}"
                } else {
                    timeStr
                }
            } else if (sessionBest0to100 > 0) {
                "Best: ${formatNanos(sessionBest0to100)}"
            } else {
                "--"
            }
            
            tvCard0to200.text = if (attempt0to200Nanos > 0) {
                val timeStr = formatNanos(attempt0to200Nanos)
                if (sessionBest0to200 > 0) {
                    "$timeStr\nBest: ${formatNanos(sessionBest0to200)}"
                } else {
                    timeStr
                }
            } else if (sessionBest0to200 > 0) {
                "Best: ${formatNanos(sessionBest0to200)}"
            } else {
                "--"
            }
            
            tvCard100to200.text = if (attempt100to200Nanos > 0) {
                val timeStr = formatNanos(attempt100to200Nanos)
                if (sessionBest100to200 > 0) {
                    "$timeStr\nBest: ${formatNanos(sessionBest100to200)}"
                } else {
                    timeStr
                }
            } else if (sessionBest100to200 > 0) {
                "Best: ${formatNanos(sessionBest100to200)}"
            } else {
                "--"
            }
            
            tvCard0to402.text = if (attempt0to402Nanos > 0) {
                val timeStr = formatNanos(attempt0to402Nanos)
                if (sessionBest0to402 > 0) {
                    "$timeStr\nBest: ${formatNanos(sessionBest0to402)}"
                } else {
                    timeStr
                }
            } else if (sessionBest0to402 > 0) {
                "Best: ${formatNanos(sessionBest0to402)}"
            } else {
                "--"
            }
        } else {
            tvCard0to100.text = "--"
            tvCard0to200.text = "--"
            tvCard100to200.text = "--"
            tvCard0to402.text = "--"
        }
        val speedUnit = UnitsManager.getSpeedUnit(this)
        if (speedUnit == UnitsManager.SpeedUnit.MPH) {
            tvCard0to402Distance.text = "0.00 mi"
        } else {
            tvCard0to402Distance.text = "0 m"
        }
        tvCard0to402Distance.visibility = if (measurementMode == MeasurementMode.QUARTER_MILE || measurementMode == MeasurementMode.ALL) View.VISIBLE else View.GONE

        lastDisplayedConvertedSpeed = 0f
        lastDisplayedG = 0f
        isShowingGForceInsteadOfSpeed = false
        applyPrimaryDisplayState()
    }

    private fun togglePrimaryDisplay() {
        isShowingGForceInsteadOfSpeed = !isShowingGForceInsteadOfSpeed
        applyPrimaryDisplayState()
    }

    private fun applyPrimaryDisplayState() {
        val orientation = resources.configuration.orientation
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            if (isShowingGForceInsteadOfSpeed) {
                tvBigSpeed.visibility = View.GONE
                tvSpeedUnit?.visibility = View.GONE
                gContainer.visibility = View.VISIBLE
                tvGCurrentBig.visibility = View.VISIBLE
                tvGCurrentBig.text = String.format("%.2f g", lastDisplayedG)
            } else {
                tvBigSpeed.visibility = View.VISIBLE
                tvSpeedUnit?.visibility = View.VISIBLE
                tvGCurrentBig.visibility = View.GONE
                gContainer.visibility = View.GONE
                tvBigSpeed.text = lastDisplayedConvertedSpeed.toInt().toString()
                tvGCurrentBig.text = String.format("%.2f g", lastDisplayedG)
            }
        } else {
            // Portrait: always show both
            tvBigSpeed.visibility = View.VISIBLE
            tvSpeedUnit?.visibility = View.VISIBLE
            gContainer.visibility = View.VISIBLE
            tvGCurrentBig.visibility = View.VISIBLE
            tvBigSpeed.text = lastDisplayedConvertedSpeed.toInt().toString()
            tvGCurrentBig.text = String.format("%.2f g", lastDisplayedG)
        }
    }

    private fun updateAttemptNumber() {
        if (measurementMode == MeasurementMode.ALL) return

        val attemptNum = (currentSession?.attempts?.size ?: 0) + 1
        val prefix = getString(R.string.drag_attempt_number, attemptNum)
        tvStatus.text = when (measurementMode) {
            MeasurementMode.HUNDRED_TO_200 -> "$prefix - Accelerate to 95-99 km/h"
            else -> prefix
        }
    }

    private fun ensureServiceAndStart() {
        val intent = Intent(this, ForegroundService::class.java).apply {
            putExtra("ACTIVATE_NORMAL_MODE", true)
            putExtra("FORCE_GPS_HIGH_FREQUENCY", true)  // Форсираме високочестотен GPS за drag
        }
        ContextCompat.startForegroundService(this, intent)

        if (!serviceBound) {
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else {
            // Ако service-ът вече е свързан, стартирай веднага
            startMeasuring()
        }
    }

    private fun startMeasuring() {
        cancelRestartCooldown()
        if (measurementStarted) return
        measurementStarted = true
        
        createNewAttempt()
        
        // Start G-force measurement in service
        foregroundService?.startNewMeasurement(measurementMode.name)
        
        // Start polling to update UI (measuring is now true)
        startPolling()
    }

    private fun startPolling() {
        pollHandler.removeCallbacks(pollRunnable)
        pollHandler.post(pollRunnable)
    }

    private fun stopPolling() {
        pollHandler.removeCallbacks(pollRunnable)
    }

    private fun handleLocation(loc: Location) {
        if (loc.accuracy > GPS_READY_ACCURACY_METERS) {
            return
        }
        

        val speedKmh = loc.speed * 3.6f

        if (!gpsReady) {
            gpsReady = true
            updateReadyStatus()
        }

        // Деселерация детекция - само ако имаме реална скорост
        if (started && !measurementComplete && speedKmh > 5f) {
            val speedDiff = speedKmh - lastSpeed
            if (speedDiff < DECELERATION_DELTA_KMH && speedKmh < DECELERATION_MAX_SPEED_KMH && !decelerationDetected) {
                decelerationDetected = true
                handleDeceleration(speedKmh)
            }
        } else if (measurementMode == MeasurementMode.HUNDRED_TO_200 && started && !measurementComplete && speedKmh > 5f) {
            // За 100-200 режим - детектираме деселерация докато измерваме
            val speedDiff = speedKmh - lastSpeed
            if (speedDiff < DECELERATION_DELTA_KMH && speedKmh < DECELERATION_MAX_SPEED_KMH && !decelerationDetected) {
                decelerationDetected = true
                handleDeceleration(speedKmh)
            }
        }

        when (measurementMode) {
            MeasurementMode.ZERO_TO_100,
            MeasurementMode.ZERO_TO_200,
            MeasurementMode.QUARTER_MILE -> {

                if (waitingForFullStop) {
                    if (speedKmh < FULL_STOP_REARM_SPEED_KMH) {
                        prepareSingleModeNextAttemptAfterFullStop()
                    }
                } else if (!started && serviceReady && gpsReady && !decelerationDetected && !restartCooldownActive) {
                    // БЛОКИРАМЕ измерването ако няма калибрирана посока
                    if (!DragCalibration.isCalibrated) {
                        // Не е калибрирано - НЕ започваме измерване
                        Log.d("DragRunPage", "❌ DragCalibration NOT calibrated")
                        return
                    }
                    
                    // Хибридна старт детекция: Linear Acceleration + GPS
                    val linearAccelTriggered = foregroundService?.isLinearAccelTriggered() ?: false
                    
                    // ВАЖНО: GPS НЕ участва в старта! Само Linear Acceleration!
                    // GPS използваме САМО за измерване на скорости след старта
                    val shouldStart = linearAccelTriggered
                    
                    // Периодично логване - показва че чакаме за forward ускорение
                    if (!shouldStart && System.currentTimeMillis() % 3000 < 100) {
                        Log.d("DragRunPage", "⏳ Waiting for FORWARD acceleration... linearAccelTriggered=$linearAccelTriggered")
                    }
                    
                    if (shouldStart) {
                        val attemptNumber = getCurrentAttemptNumber()
                        val modeText = when (measurementMode) {
                            MeasurementMode.ZERO_TO_100 -> "0-100"
                            MeasurementMode.ZERO_TO_200 -> "0-200"
                            MeasurementMode.QUARTER_MILE -> "0-402m"
                            else -> ""
                        }
                        beginMeasurementFromLinearAcceleration(
                            loc = loc,
                            speedKmh = speedKmh,
                            linearAccelTriggered = linearAccelTriggered,
                            logSuffix = "",
                            statusText = getString(R.string.drag_status_measuring, modeText, attemptNumber)
                        )
                    }
                } else if (!started) {
                }

                if (isCalibrating && !calibrationComplete && speedKmh < CALIBRATION_SPEED) {
                    collectCalibrationSampleIfNeeded(speedKmh)
                }

                if (isCalibrating && !calibrationComplete && speedKmh >= CALIBRATION_SPEED) {
                    calibrationComplete = true
                    val calibrationEndTime = System.nanoTime()

                    val accelMps2 = computeCalibratedAccelerationMps2()

                    val vExtrapMps = START_SPEED_THRESHOLD * KMH_TO_MPS
                    val safeAccel = accelMps2.coerceIn(MIN_ACCEL_MPS2, MAX_ACCEL_MPS2)
                    val extrapolatedTime = (vExtrapMps / safeAccel).coerceIn(0.0, MAX_EXTRAPOLATION_SECONDS)

                    extrapolatedStartTime = (extrapolatedTime * 1_000_000_000L).toLong()
                    accelStartNano = calibrationStartTime - extrapolatedStartTime

                    tvStatus.text = getString(R.string.drag_measuring)
                }

                if (measurementMode == MeasurementMode.QUARTER_MILE && started) {
                    handleQuarterMile(loc, speedKmh)
                }
            }

            MeasurementMode.HUNDRED_TO_200 -> {
                // Проверяваме за завършване на измерването ПЪРВО
                if (started && !measurementComplete && speedKmh >= ROLLING_FINISH_SPEED_KMH) {
                    // Използваме rolling100StartTime като база за измерването
                    val currentTime = System.nanoTime()
                    attempt100to200Nanos = currentTime - rolling100StartTime
                    
                    // Обновяваме currentAttempt с ОТНОСИТЕЛНО startTime за графиката
                    val measurementStartTimeNano = foregroundService?.getMeasurementStartTimeNano() ?: 0L
                    val relativeStartTime = if (measurementStartTimeNano > 0L) {
                        rolling100StartTime - measurementStartTimeNano
                    } else {
                        0L
                    }
                    currentAttempt = currentAttempt?.copy(startTime = relativeStartTime)
                    
                    Log.d("DragRunPage", "✅ 100-200 measured: rolling100StartTime=${rolling100StartTime/1_000_000_000.0}s, currentTime=${currentTime/1_000_000_000.0}s, result=${attempt100to200Nanos/1_000_000_000.0}s")
                    Log.d("DragRunPage", "   measurementStartTimeNano=${measurementStartTimeNano/1_000_000_000.0}s, relativeStartTime=${relativeStartTime/1_000_000_000.0}s")
                
                // Validation: абсурдни времена (над 20 секунди или отрицателни)
                if (attempt100to200Nanos > 20_000_000_000L || attempt100to200Nanos < 0) {
                    Log.d("DragRunPage", "❌ Invalid 100-200 time: ${attempt100to200Nanos/1_000_000_000.0}s - discarding")
                    attempt100to200Nanos = -1L
                }
                
                // Показваме само ако времето е валидно
                if (attempt100to200Nanos > 0) {
                    val seconds = attempt100to200Nanos / 1_000_000_000.0
                    val resultText = String.format("%.3f s", seconds)

                    val display = if (sessionBest100to200 < 0 || attempt100to200Nanos < sessionBest100to200) {
                        "🏆 $resultText"
                    } else {
                        resultText
                    }
                    tvCard100to200.text = displayTimeWithBest(display, sessionBest100to200)

                    val speed100 = UnitsManager.convertSpeed(100f, UnitsManager.getSpeedUnit(this)).toInt()
                    val speed200 = UnitsManager.convertSpeed(200f, UnitsManager.getSpeedUnit(this)).toInt()
                    val speedUnit = UnitsManager.getSpeedUnit(this).symbol
                    tvStatus.text = "$speed100-$speed200 $speedUnit ${getString(R.string.drag_complete_return_95_99)}"
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                    measurementComplete = true
                    measured100to200 = true
                    waitingForFullStop = true
                    
                    // Нулираме rollingStartReady за нов опит
                    rollingStartReady = false

                    foregroundService?.stopMeasurement()
                    saveCurrentAttempt()
                    attemptAlreadySaved = true
                }
                }  // Затваряме if (started && !measurementComplete && speedKmh >= 200f)
                // След това проверяваме другите състояния САМО ако не сме завършили измерване
                if (waitingForFullStop && !measurementComplete) {
                    if (isInRollingStartRange(speedKmh)) {
                        prepareHundredToTwoHundredNextAttempt()
                    } else if (speedKmh < ROLLING_START_MIN_KMH) {
                        // Показваме съобщението само ако не е деселерация
                        if (!decelerationDetected) {
                            tvStatus.text = getString(R.string.drag_status_return_95_99)
                            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                        }
                    } else if (speedKmh > ROLLING_START_MAX_KMH) {
                        tvStatus.text = getString(R.string.drag_status_too_fast)
                        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                    }
                } else if (waitingForFullStop && measurementComplete) {
                    // След успешно измерване - показваме съобщението докато не се върне на 95-99
                    if (isInRollingStartRange(speedKmh)) {
                        prepareHundredToTwoHundredNextAttempt()
                    }
                    // Не променяме съобщението ако не сме в 95-99 диапазона
                } else if (!started && speedKmh >= 100f && rollingStartReady && serviceReady && gpsReady && !decelerationDetected) {
                    // Започваме измерване при 100+ km/h
                    started = true
                    rolling100StartTime = System.nanoTime()
                    val attemptNumber = getCurrentAttemptNumber()
                    tvStatus.text = getString(R.string.drag_status_measuring, "100-200", attemptNumber)
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
                    foregroundService?.startNewMeasurement(measurementMode.name)
                } else if (!started && isInRollingStartRange(speedKmh) && !rollingStartReady) {
                    // Готови сме за старт
                    rollingStartReady = true
                    tvStatus.text = getString(R.string.drag_status_pass_100)
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                } else if (!started && speedKmh < ROLLING_START_MIN_KMH) {
                    // Твърде бавно
                    rollingStartReady = false
                    tvStatus.text = getString(R.string.drag_status_speed_up)
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                } else if (!started && speedKmh > ROLLING_START_MAX_KMH && !rollingStartReady && !measurementComplete) {
                    // Твърде бързо - само ако не сме завършили измерване
                    tvStatus.text = getString(R.string.drag_status_too_fast)
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                }
            }

            MeasurementMode.ALL -> {
                // Проверка за пълна спирка след деселерация
                if (waitingForFullStop) {
                    if (speedKmh < FULL_STOP_REARM_SPEED_KMH) {
                        waitingForFullStop = false
                        decelerationDetected = false
                        isCalibrating = false
                        calibrationComplete = false
                        cancelRestartCooldown()
                        restartAllMeasurements()
                        foregroundService?.startNewMeasurement(measurementMode.name)
                    } else {
                        // Продължаваме да показваме съобщението
                        tvStatus.text = getString(R.string.drag_status_stop_to_restart)
                        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                    }
                } else if (!started && serviceReady && gpsReady && !decelerationDetected && !restartCooldownActive) {
                    // БЛОКИРАМЕ измерването ако няма калибрирана посока
                    if (!DragCalibration.isCalibrated) {
                        // Не е калибрирано - НЕ започваме измерване
                        Log.d("DragRunPage", "❌ DragCalibration NOT calibrated (ALL режим)")
                        return
                    }
                    
                    // Хибридна старт детекция: Linear Acceleration + GPS
                    val linearAccelTriggered = foregroundService?.isLinearAccelTriggered() ?: false
                    
                    // ВАЖНО: GPS НЕ участва в старта! Само Linear Acceleration!
                    // GPS използваме САМО за измерване на скорости след старта
                    val shouldStart = linearAccelTriggered
                    
                    // Периодично логване - показва че чакаме за forward ускорение
                    if (!shouldStart && System.currentTimeMillis() % 3000 < 100) {
                        Log.d("DragRunPage", "⏳ Waiting for FORWARD acceleration (ALL режим)... linearAccelTriggered=$linearAccelTriggered")
                    }
                    
                    if (shouldStart) {
                        beginMeasurementFromLinearAcceleration(
                            loc = loc,
                            speedKmh = speedKmh,
                            linearAccelTriggered = linearAccelTriggered,
                            logSuffix = " (ALL режим)",
                            statusText = getString(R.string.drag_measuring)
                        )
                    }
                } else if (!started) {
                }

                // В ALL режим винаги проверяваме 0-402m ако не е завършено
                if (started && (measurementMode == MeasurementMode.ALL || measurementMode == MeasurementMode.QUARTER_MILE)) {
                    handleQuarterMile(loc, speedKmh)
                }
            }
        }

        lastSpeed = speedKmh
    }

    // Събиране на калибрационни семпли със заглаждане (медиана)
    private fun collectCalibrationSampleIfNeeded(currentSpeedKmh: Float) {
        if (!isCalibrating || calibrationComplete) return
        val now = System.nanoTime()
        val currentMps = (currentSpeedKmh * KMH_TO_MPS).toFloat()
        val window = (calibrationSpeedMps.takeLast((MEDIAN_WINDOW_SAMPLES - 1).coerceAtLeast(0)) + currentMps)
        val smoothed = medianOf(window)
        calibrationSpeedMps.add(smoothed)
        calibrationTimeNanos.add(now)
    }

    private fun medianOf(window: List<Float>): Float {
        if (window.isEmpty()) return 0f
        val sorted = window.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else ((sorted[mid - 1] + sorted[mid]) / 2f)
    }

    private fun computeCalibratedAccelerationMps2(): Double {
        // Ако нямаме достатъчно данни, използваме безопасен минимум
        if (calibrationSpeedMps.size < 2 || calibrationTimeNanos.size < 2) return MIN_ACCEL_MPS2

        // Намери началния и крайния валиден семпъл в калибрацията (близки до 6→15 km/h)
        val startIndex = 0
        val endIndex = calibrationSpeedMps.size - 1

        val vStart = calibrationSpeedMps[startIndex].toDouble()
        val vEnd = calibrationSpeedMps[endIndex].toDouble()
        val tStart = calibrationTimeNanos[startIndex]
        val tEnd = calibrationTimeNanos[endIndex]
        val dt = (tEnd - tStart) / 1_000_000_000.0
        if (dt <= 0.0) return MIN_ACCEL_MPS2

        val accel = (vEnd - vStart) / dt
        return accel.coerceIn(MIN_ACCEL_MPS2, MAX_ACCEL_MPS2)
    }


    private fun handleDeceleration(currentSpeed: Float) {

        when (measurementMode) {
            MeasurementMode.ZERO_TO_100,
            MeasurementMode.ZERO_TO_200,
            MeasurementMode.QUARTER_MILE -> {
                // За индивидуални режими - НЕ изтриваме данните при деселерация
                // Само спираме измерването и чакаме пълно спиране
                measurementComplete = true
                waitingForFullStop = true
                started = false
                
                // НЕ нулираме измерванията - запазваме успешните резултати
                // Данните ще се нулират само при пълно спиране (под 3 km/h)

                foregroundService?.stopMeasurement()
                tvStatus.text = getString(R.string.drag_status_deceleration)
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            }

            MeasurementMode.HUNDRED_TO_200 -> {
                // За 100-200 - НЕ изтриваме данните при деселерация
                // Само спираме измерването и чакаме пълно спиране
                measurementComplete = true
                waitingForFullStop = true
                started = false
                rollingStartReady = false
                
                // НЕ нулираме измерванията - запазваме успешните резултати
                // Данните ще се нулират само при пълно спиране (под 3 km/h)

                foregroundService?.stopMeasurement()
                tvStatus.text = getString(R.string.drag_status_deceleration_100to200)
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            }

            MeasurementMode.ALL -> {
                // За ALL режим - НЕ изтриваме данните при деселерация
                // Само спираме измерването и чакаме пълно спиране
                measurementComplete = true
                waitingForFullStop = true
                started = false
                
                // НЕ нулираме измерванията - запазваме успешните резултати
                // Данните ще се нулират само при пълно спиране (под 3 km/h)

                foregroundService?.stopMeasurement()

                runOnUiThread {
                    tvStatus.text = getString(R.string.drag_status_stop_to_restart)
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                }
            }
        }
    }


    private fun handleQuarterMile(loc: Location, speedKmh: Float) {
        // В ALL режим или QUARTER_MILE режим, ако не сме завършили измерването
        if ((measurementMode == MeasurementMode.ALL || measurementMode == MeasurementMode.QUARTER_MILE) && !distanceCompleted) {
            val start = startLocation ?: return
            
            // Просто изчисляваме разстоянието от startLocation до текущата позиция - RAW данни
            if (lastLocationForDistance == null) {
                lastLocationForDistance = start
                accumulatedDistance = 0f
            } else {
                val distanceIncrement = lastLocationForDistance!!.distanceTo(loc)
                accumulatedDistance += distanceIncrement
                lastLocationForDistance = loc
            }


            if (accumulatedDistance < TARGET_METERS) {
                val speedUnit = UnitsManager.getSpeedUnit(this)
                // Конвертираме според скоростта
                if (speedUnit == UnitsManager.SpeedUnit.MPH) {
                    // При mph показваме в мили
                    val distInKm = accumulatedDistance / 1000.0
                    val distInMiles = UnitsManager.convertDistance(distInKm, UnitsManager.DistanceUnit.MILES)
                    tvCard0to402Distance.text = String.format("%.2f mi", distInMiles)
                } else {
                    // При km/h или m/s показваме в метри
                    tvCard0to402Distance.text = String.format("%.0f m", accumulatedDistance)
                }
            }

            if (accumulatedDistance >= TARGET_METERS) {
                // Използваме същото време като 0-100 и 0-200 - от service-а
                val measurementStartTime = foregroundService?.getMeasurementStartTimeNano() ?: 0L
                val currentTime = System.nanoTime()
                val elapsedNanos = currentTime - measurementStartTime
                finishTimeNano = currentTime

                // Запазваме времето за по-късно използване
                attempt0to402Nanos = elapsedNanos

                val resultText = formatNanos(elapsedNanos)
                val display = if (sessionBest0to402 < 0 || elapsedNanos < sessionBest0to402) {
                    "🏆 $resultText"
                } else {
                    resultText
                }
                tvCard0to402.text = displayTimeWithBest(display, sessionBest0to402)

                tvCard0to402Distance.visibility = View.GONE
                distanceCompleted = true
                measured0to402 = true
                
                // Play sound for reaching 402m
                if (!sound402Played) {
                    soundManager.playQuarterMileReached()
                    sound402Played = true
                }

                if (measurementMode == MeasurementMode.QUARTER_MILE) {
                    // Спираме събирането на данни само за индивидуални режими
                    foregroundService?.stopMeasurement()
                    saveCurrentAttempt()
                    attemptAlreadySaved = true
                    measurementComplete = true
                    started = false
                    waitingForFullStop = true
                    val quarterDistance = UnitsManager.getQuarterMileDistance(this)
                    tvStatus.text = "0-$quarterDistance ${getString(R.string.drag_complete_stop_for_new)}"
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                } else {
                    // В ALL режим - проверяваме дали всички измервания са завършени
                    tvStatus.text = getString(R.string.drag_status_quarter_complete_continue)
                    checkAllMeasurementsComplete()
                }
            }
        }
    }

    private fun startRestartCooldown() {
        if (restartCooldownActive) return
        restartCooldownActive = true
        restartCooldownEndTime = SystemClock.elapsedRealtime() + RESTART_COOLDOWN_MS
        updateRestartCooldownMessage()
        restartCooldownHandler.removeCallbacks(restartCooldownRunnable)
        restartCooldownHandler.postDelayed(restartCooldownRunnable, 200)
    }

    private fun updateRestartCooldownMessage() {
        if (!restartCooldownActive) return
        val remainingMs = (restartCooldownEndTime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val seconds = ((remainingMs + 999) / 1000).toInt().coerceAtLeast(0)
        tvStatus.text = getString(R.string.drag_status_restart_in, seconds)
        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
    }

    private fun completeRestartCooldown() {
        if (!restartCooldownActive) return
        restartCooldownActive = false
        restartCooldownHandler.removeCallbacks(restartCooldownRunnable)

        when (measurementMode) {
            MeasurementMode.ALL -> {
                restartAllMeasurements()
            }
            MeasurementMode.ZERO_TO_100 -> {
                createNewAttempt()
                tvStatus.text = getString(R.string.drag_status_ready_0to100)
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            }
            MeasurementMode.ZERO_TO_200 -> {
                createNewAttempt()
                tvStatus.text = getString(R.string.drag_status_ready_0to200)
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            }
            MeasurementMode.QUARTER_MILE -> {
                createNewAttempt()
                tvStatus.text = getString(R.string.drag_status_ready_quarter)
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            }
            else -> {
                createNewAttempt()
            }
        }
    }

    private fun cancelRestartCooldown() {
        if (!restartCooldownActive) {
            restartCooldownHandler.removeCallbacks(restartCooldownRunnable)
            return
        }
        restartCooldownActive = false
        restartCooldownHandler.removeCallbacks(restartCooldownRunnable)
    }

    private fun restartAllMeasurements() {
        // Спираме текущото измерване
        measurementComplete = false
        started = false
        decelerationDetected = false
        waitingForFullStop = false
        isCalibrating = false
        calibrationComplete = false
        calibrationSpeedMps.clear()
        calibrationTimeNanos.clear()

        // Нулираме измерванията
        measured0to100 = false
        measured0to200 = false
        measured100to200 = false
        measured0to402 = false
        
        // Reset sound flags
        sound100Played = false
        sound200Played = false
        sound402Played = false

        attempt0to100Nanos = -1L
        attempt0to200Nanos = -1L
        attempt100to200Nanos = -1L
        attempt0to402Nanos = -1L
        timeAt100Nano = -1L

        startTimeNano = 0L
        startLocation = null
        accelStartNano = 0L
        distanceCompleted = false
        calibrationStartTime = 0L
        extrapolatedStartTime = 0L
        accumulatedDistance = 0f
        lastLocationForDistance = null

        // Нулираме дисплея
        resetDisplayValues()

        // Спираме G-force измерването
        foregroundService?.stopMeasurement()

        runOnUiThread {
            tvStatus.text = "✅ READY - Start accelerating!"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        }

    }

    private fun hasValidMeasurements(): Boolean {
        return when (measurementMode) {
            MeasurementMode.ZERO_TO_100 -> attempt0to100Nanos > 0
            MeasurementMode.ZERO_TO_200 -> attempt0to200Nanos > 0
            MeasurementMode.HUNDRED_TO_200 -> attempt100to200Nanos > 0
            MeasurementMode.QUARTER_MILE -> measured0to402
            MeasurementMode.ALL -> {
                attempt0to100Nanos > 0 || attempt0to200Nanos > 0 ||
                        attempt100to200Nanos > 0 || measured0to402
            }
        }
    }

    private fun checkAllMeasurementsComplete() {
        
        if (measurementMode == MeasurementMode.ALL &&
            measured0to100 && measured0to200 && measured100to200 && measured0to402) {

            measurementComplete = true
            // Спираме събирането на данни само когато ВСИЧКИ измервания са завършени
            foregroundService?.stopMeasurement()
            tvStatus.text = getString(R.string.drag_status_all_complete)
            btnStop.text = getString(R.string.drag_finish_session)

        } else {
        }
    }
    
    private fun checkAllMeasurementsCompleteExcept402() {
        if (measurementMode == MeasurementMode.ALL &&
            measured0to100 && measured0to200 && measured100to200 && !measured0to402) {

        }
    }

    private fun getCurrentAttemptNumber(): Int {
        return (currentSession?.attempts?.size ?: 0) + 1
    }

    private var lastLoggedSpeed = -1f
    
    private fun updateUIFromService() {
        val svc = foregroundService ?: return

        val currentG = svc.getCurrentG()
        val peakG = svc.getPeakG()
        val speedFloat = svc.getCurrentSpeed()
        val speed = speedFloat.toInt()
        
        // Логваме само когато скоростта се промени
        if (kotlin.math.abs(speedFloat - lastLoggedSpeed) > 0.5f) {
            lastLoggedSpeed = speedFloat
        }

        runOnUiThread {
            // Скорост - конвертирана според избраната единица
        val convertedSpeed = UnitsManager.convertSpeed(speed.toFloat(), UnitsManager.getSpeedUnit(this))
        lastDisplayedConvertedSpeed = convertedSpeed
        lastDisplayedG = currentG
        applyPrimaryDisplayState()
        tvGCurrentBig.text = String.format("%.2f g", currentG)

            // Update GGaugeView with G-force data
            // Get G-force components from service
            val gForceX = foregroundService?.getCurrentGForceX() ?: 0f
            val gForceY = foregroundService?.getCurrentGForceY() ?: 0f
            if (::gGaugeView.isInitialized) {
                gGaugeView.gForceX = gForceX
                gGaugeView.gForceY = gForceY
                gGaugeView.peakGForce = peakG
            }
        }

        // Обработка на измерванията
        if (started && !measurementComplete) {
            val nowNano = System.nanoTime()

            // Използваме СЪЩАТА времева основа като RAW данните
            val measurementStartTimeNano = foregroundService?.getMeasurementStartTimeNano() ?: 0L

            // 0-100 измерване
            if ((measurementMode == MeasurementMode.ALL || measurementMode == MeasurementMode.ZERO_TO_100) && !measured0to100) {
                val svcT100 = foregroundService?.getTime0to100Nanos() ?: 0L
                if (svcT100 > 0 && measurementStartTimeNano > 0) {
                    // Ползваме точно семпълното време от Service
                    attempt0to100Nanos = svcT100
                    // svcT100 е вече относително време, не трябва да го добавяме към measurementStartTimeNano
                    timeAt100Nano = measurementStartTimeNano + svcT100
                    val resultNanos = attempt0to100Nanos
                    val timeStr = formatNanos(resultNanos)
                    
                    runOnUiThread {
                        val display = if (sessionBest0to100 < 0 || resultNanos < sessionBest0to100) {
                            "🏆 $timeStr"
                        } else {
                            timeStr
                        }
                        tvCard0to100.text = if (measurementMode != MeasurementMode.ALL && sessionBest0to100 > 0) {
                            "$display\nBest: ${formatNanos(sessionBest0to100)}"
                        } else {
                            display
                        }
                    }
                    measured0to100 = true
                    
                    // Play sound for reaching 100 km/h
                    if (!sound100Played) {
                        soundManager.playSpeedReached100()
                        sound100Played = true
                    }

                    if (measurementMode == MeasurementMode.ZERO_TO_100) {
                        // Спираме събирането на данни само за индивидуални режими
                        foregroundService?.stopMeasurement()
                        saveCurrentAttempt()
                        attemptAlreadySaved = true
                        measurementComplete = true
                        waitingForFullStop = true
                        runOnUiThread {
                            val speed100 = UnitsManager.convertSpeed(100f, UnitsManager.getSpeedUnit(this)).toInt()
                            val speedUnit = UnitsManager.getSpeedUnit(this).symbol
                            tvStatus.text = "0-$speed100 $speedUnit ${getString(R.string.drag_complete_stop_for_new)}"
                            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                        }
                    }
                } else if (measurementStartTimeNano > 0) {
                    // Показваме таймер докато измерваме
                    val elapsed = nowNano - measurementStartTimeNano
                    runOnUiThread {
                        val timerText = String.format("⏱️ %.2f s", elapsed / 1_000_000_000.0)
                        tvCard0to100.text = if (measurementMode != MeasurementMode.ALL && sessionBest0to100 > 0) {
                            "$timerText\nBest: ${formatNanos(sessionBest0to100)}"
                        } else {
                            timerText
                        }
                    }
                }
            }

            // 0-200 измерване
            if ((measurementMode == MeasurementMode.ALL || measurementMode == MeasurementMode.ZERO_TO_200) && !measured0to200) {
                val svcT200 = foregroundService?.getTime0to200Nanos() ?: 0L
                if (svcT200 > 0 && measurementStartTimeNano > 0) {
                    // Ползваме точно семпълното време от Service
                    attempt0to200Nanos = svcT200
                    if (timeAt100Nano <= 0) {
                        timeAt100Nano = measurementStartTimeNano + (foregroundService?.getTime0to100Nanos() ?: 0L)
                    }
                    if (timeAt100Nano > 0) {
                        // svcT200 и svcT100 са вече относителни времена, изчисляваме разликата
                        val svcT100 = foregroundService?.getTime0to100Nanos() ?: 0L
                        attempt100to200Nanos = svcT200 - svcT100
                    }
                    
                    val resultNanos = attempt0to200Nanos
                    val timeStr = formatNanos(resultNanos)

                    runOnUiThread {
                        val display = if (sessionBest0to200 < 0 || resultNanos < sessionBest0to200) {
                            "🏆 $timeStr"
                        } else {
                            timeStr
                        }
                        tvCard0to200.text = if (measurementMode != MeasurementMode.ALL && sessionBest0to200 > 0) {
                            "$display\nBest: ${formatNanos(sessionBest0to200)}"
                        } else {
                            display
                        }
                    }
                    measured0to200 = true
                    
                    // Play sound for reaching 200 km/h
                    if (!sound200Played) {
                        soundManager.playSpeedReached200()
                        sound200Played = true
                    }

                    if (measurementMode == MeasurementMode.ZERO_TO_200) {
                        // Спираме събирането на данни само за индивидуални режими
                        foregroundService?.stopMeasurement()
                        saveCurrentAttempt()
                        attemptAlreadySaved = true
                        measurementComplete = true
                        waitingForFullStop = true
                        runOnUiThread {
                            val speed200 = UnitsManager.convertSpeed(200f, UnitsManager.getSpeedUnit(this)).toInt()
                            val speedUnit = UnitsManager.getSpeedUnit(this).symbol
                            tvStatus.text = "0-$speed200 $speedUnit ${getString(R.string.drag_complete_stop_for_new)}"
                            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                        }
                    }
                } else if (measurementStartTimeNano > 0) {
                    // Показваме таймер
                    val elapsed = nowNano - measurementStartTimeNano
                    runOnUiThread {
                        val timerText = String.format("⏱️ %.2f s", elapsed / 1_000_000_000.0)
                        tvCard0to200.text = if (measurementMode != MeasurementMode.ALL && sessionBest0to200 > 0) {
                            "$timerText\nBest: ${formatNanos(sessionBest0to200)}"
                        } else {
                            timerText
                        }
                    }
                }
            }

            // 100-200 измерване (само за ALL режим)
            if (measurementMode == MeasurementMode.ALL && !measured100to200) {
                if (timeAt100Nano > 0 && speedFloat >= 200f) {
                    if (attempt100to200Nanos <= 0) {
                        attempt100to200Nanos = nowNano - timeAt100Nano
                    }
                    val resultNanos = attempt100to200Nanos
                    if (resultNanos > 0) {
                        val timeStr = formatNanos(resultNanos)
                        runOnUiThread {
                            tvCard100to200.text = if (sessionBest100to200 < 0 || resultNanos < sessionBest100to200) {
                                "🏆 $timeStr"
                            } else {
                                timeStr
                            }
                        }
                        measured100to200 = true
                        // В ALL режим - НЕ проверяваме тук дали всички измервания са завършени
                        // защото 0-402m може да завърши преди 100-200
                    }
                } else if (timeAt100Nano > 0) {
                    // Таймер за 100-200
                    val elapsed = nowNano - timeAt100Nano
                    runOnUiThread {
                        tvCard100to200.text = String.format("⏱️ %.2f s", elapsed / 1_000_000_000.0)
                    }
                }
            }

            // Quarter mile таймер
            if ((measurementMode == MeasurementMode.ALL || measurementMode == MeasurementMode.QUARTER_MILE) && started && !distanceCompleted) {
                // Използваме същото време като 0-100 и 0-200
                val measurementStartTime = foregroundService?.getMeasurementStartTimeNano() ?: 0L
                val elapsedNanos = System.nanoTime() - measurementStartTime
                val seconds = elapsedNanos / 1_000_000_000.0
                runOnUiThread {
                    if (!measured0to402) {
                        val timerText = String.format("⏱️ %.2f s", seconds)
                        tvCard0to402.text = if (measurementMode == MeasurementMode.QUARTER_MILE && sessionBest0to402 > 0) {
                            "$timerText\nBest: ${formatNanos(sessionBest0to402)}"
                        } else {
                            timerText
                        }
                    }
                }
            }

            // Проверка за деселерация - НЕ спираме измерването в ALL режим докато не завършим 0-402m
            if (started && !measurementComplete && !decelerationDetected) {
                val prevSpeed = lastSpeed
                val speedDiff = speedFloat - prevSpeed

                // Рязка деселерация = край на измерването, но НЕ в ALL режим докато не завършим 0-402m
                if (speedDiff < DECELERATION_DELTA_KMH && speedFloat < DECELERATION_MAX_SPEED_KMH) {
                    // В ALL режим, не спираме измерването докато не завършим 0-402m
                    if (measurementMode != MeasurementMode.ALL || measured0to402) {
                        decelerationDetected = true
                        handleDeceleration(speedFloat)
                    } else {
                    }
                }
            }

            // Статус обновяване за индивидуални режими
            if (measurementMode != MeasurementMode.ALL && started && !measurementComplete) {
                runOnUiThread {
                    val attemptNumber = getCurrentAttemptNumber()
                    val speedUnit = UnitsManager.getSpeedUnit(this)
                    val speed100 = UnitsManager.convertSpeed(100f, speedUnit).toInt()
                    val speed200 = UnitsManager.convertSpeed(200f, speedUnit).toInt()
                    val distLabel = UnitsManager.getQuarterMileDistance(this)
                    
                    val modeText = when (measurementMode) {
                        MeasurementMode.ZERO_TO_100 -> "0-$speed100"
                        MeasurementMode.ZERO_TO_200 -> "0-$speed200"
                        MeasurementMode.QUARTER_MILE -> "0-$distLabel"
                        MeasurementMode.HUNDRED_TO_200 -> "$speed100-$speed200"
                        else -> ""
                    }
                    tvStatus.text = "Measuring $modeText Attempt #$attemptNumber"
                }
            }
            // Статус обновяване за ALL режим
            else if (measurementMode == MeasurementMode.ALL && started && !measurementComplete) {
                val speedUnit = UnitsManager.getSpeedUnit(this)
                val speed100 = UnitsManager.convertSpeed(100f, speedUnit).toInt()
                val speed200 = UnitsManager.convertSpeed(200f, speedUnit).toInt()
                val distLabel = UnitsManager.getQuarterMileDistance(this)
                
                val completed = mutableListOf<String>()
                if (measured0to100) completed.add("0-$speed100✓")
                if (measured0to200) completed.add("0-$speed200✓")
                if (measured100to200) completed.add("$speed100-$speed200✓")
                if (measured0to402) completed.add("$distLabel✓")

                runOnUiThread {
                    if (completed.isNotEmpty()) {
                        tvStatus.text = getString(R.string.drag_completed_format, completed.joinToString(" "))
                    } else if (isCalibrating && !calibrationComplete) {
                        tvStatus.text = getString(R.string.drag_status_calibrating)
                    } else {
                        tvStatus.text = getString(R.string.drag_measuring)
                    }
                }

                // Проверяваме дали всички измервания са завършени, но не спираме данните
                checkAllMeasurementsCompleteExcept402()
            }
        }

        // 100-200 rolling start таймер (за HUNDRED_TO_200 режим)
        if (measurementMode == MeasurementMode.HUNDRED_TO_200 && started && !measurementComplete) {
            val elapsed = (System.nanoTime() - rolling100StartTime) / 1_000_000_000.0
            runOnUiThread {
                val timerText = String.format("⏱️ %.2f s", elapsed)
                tvCard100to200.text = if (sessionBest100to200 > 0) {
                    "$timerText\nBest: ${formatNanos(sessionBest100to200)}"
                } else {
                    timerText
                }
            }
        }

        lastSpeed = speedFloat
    }

    private fun isInRollingStartRange(speedKmh: Float): Boolean {
        return speedKmh in ROLLING_START_MIN_KMH..ROLLING_START_MAX_KMH
    }

    private fun getMeasurementWindowAndSpeedCap(
        mode: MeasurementMode,
        attempt: DragAttempt,
        attempt0to100Ns: Long,
        attempt0to200Ns: Long,
        attempt100to200Ns: Long
    ): Triple<Long, Long, Float?> {
        return when (mode) {
            MeasurementMode.ZERO_TO_100 -> Triple(0L, attempt0to100Ns, 100f)
            MeasurementMode.ZERO_TO_200 -> Triple(0L, attempt0to200Ns, 200f)
            MeasurementMode.HUNDRED_TO_200 -> {
                val startNs = attempt.startTime.coerceAtLeast(0L)
                val endNs = if (attempt100to200Ns > 0L) startNs + attempt100to200Ns else -1L
                Triple(startNs, endNs, 200f)
            }
            else -> Triple(0L, -1L, null)
        }
    }

    private fun <T> trimTimeSeriesToWindow(
        values: List<T>,
        timestamps: List<Long>,
        startNs: Long,
        endNs: Long
    ): Pair<List<T>, List<Long>> {
        val limit = minOf(values.size, timestamps.size)
        if (limit <= 0) return emptyList<T>() to emptyList()

        val alignedValues = values.take(limit)
        val alignedTimes = timestamps.take(limit)

        if (endNs <= 0L) {
            return alignedValues to alignedTimes
        }

        val filteredValues = mutableListOf<T>()
        val filteredTimes = mutableListOf<Long>()
        for (i in 0 until limit) {
            val ts = alignedTimes[i]
            if (ts in startNs..endNs) {
                filteredValues.add(alignedValues[i])
                filteredTimes.add(ts)
            }
        }

        return if (filteredValues.isNotEmpty()) {
            filteredValues to filteredTimes
        } else {
            alignedValues to alignedTimes
        }
    }

    private fun beginMeasurementFromLinearAcceleration(
        loc: Location,
        speedKmh: Float,
        linearAccelTriggered: Boolean,
        logSuffix: String,
        statusText: String
    ) {
        started = true
        Log.d("DragRunPage", "🚀 START измерване! speedKmh=$speedKmh, linearAccelTriggered=$linearAccelTriggered$logSuffix")

        startTimeNano = if (linearAccelTriggered) {
            foregroundService?.getLinearAccelTriggerTime() ?: System.nanoTime()
        } else {
            System.nanoTime()
        }

        foregroundService?.setMeasurementStartTimeNano(startTimeNano)
        startLocation = loc
        tvStatus.text = statusText
        foregroundService?.startNewMeasurement(measurementMode.name)
    }

    private fun prepareSingleModeNextAttemptAfterFullStop() {
        waitingForFullStop = false
        decelerationDetected = false
        isCalibrating = false
        calibrationComplete = false
        measurementComplete = false
        started = false
        cancelRestartCooldown()

        createNewAttempt()
        foregroundService?.startNewMeasurement(measurementMode.name)

        when (measurementMode) {
            MeasurementMode.ZERO_TO_100 -> {
                tvStatus.text = getString(R.string.drag_status_ready_0to100)
            }
            MeasurementMode.ZERO_TO_200 -> {
                tvStatus.text = getString(R.string.drag_status_ready_0to200)
            }
            MeasurementMode.QUARTER_MILE -> {
                tvStatus.text = getString(R.string.drag_status_ready_quarter)
            }
            else -> {
                tvStatus.text = getString(R.string.drag_waiting_for_acceleration)
            }
        }
        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
    }

    private fun prepareHundredToTwoHundredNextAttempt() {
        waitingForFullStop = false
        decelerationDetected = false
        isCalibrating = false
        calibrationComplete = false
        rollingStartReady = true
        started = false
        measurementComplete = false
        createNewAttempt()
        tvStatus.text = getString(R.string.drag_status_pass_100)
        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
    }

    private fun formatNanos(nanos: Long): String {
        return if (nanos > 0L) {
            val sec = nanos / 1_000_000_000.0
            String.format("%.3f s", sec)
        } else {
            "--"
        }
    }

    private fun showStopConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.drag_stop_session_title))
            .setMessage(getString(R.string.drag_stop_session_message))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                finishSession()
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    private fun finishSession() {

        val gSamples = foregroundService?.getRecentGSamples() ?: emptyList()
        val gTimeStamps = foregroundService?.getRecentGTimeStamps() ?: emptyList()
        val gpsAccelSamples = foregroundService?.getRecentGpsAccelSamples() ?: emptyList()
        val gpsAccelTimeStamps = foregroundService?.getRecentGpsAccelTimeStamps() ?: emptyList()

        if (measurementMode == MeasurementMode.ALL) {
            currentAttempt?.let { attempt ->
                val speedSamplesRaw = foregroundService?.getRecentSpeedSamples() ?: emptyList()
                val speedTimeStampsRaw = foregroundService?.getRecentSpeedTimeStamps() ?: emptyList()

                // RAW данни: без синтетични точки, без милисекундни офсети; всичко е в наносекунди
                val adjustedGSamples = if (gSamples.isNotEmpty() && gTimeStamps.isNotEmpty()) gSamples else emptyList()
                val adjustedGTimes = if (gSamples.isNotEmpty() && gTimeStamps.isNotEmpty()) gTimeStamps else emptyList()

                val adjustedGpsAccelSamples = if (gpsAccelSamples.isNotEmpty() && gpsAccelTimeStamps.isNotEmpty()) gpsAccelSamples else emptyList()
                val adjustedGpsAccelTimes = if (gpsAccelSamples.isNotEmpty() && gpsAccelTimeStamps.isNotEmpty()) gpsAccelTimeStamps else emptyList()

                val adjustedSpeedSamples = if (speedSamplesRaw.isNotEmpty() && speedTimeStampsRaw.isNotEmpty()) speedSamplesRaw else emptyList()
                val adjustedSpeedTimes = if (speedSamplesRaw.isNotEmpty() && speedTimeStampsRaw.isNotEmpty()) speedTimeStampsRaw else emptyList()

                // Продължителност = най-дългото от измерванията (наносекунди)
                val durationNs = listOf(
                    if (attempt0to100Nanos > 0) attempt0to100Nanos else -1L,
                    if (attempt0to200Nanos > 0) attempt0to200Nanos else -1L,
                    if (attempt100to200Nanos > 0) attempt100to200Nanos else -1L,
                    if (measured0to402 && finishTimeNano > 0 && startTimeNano > 0) finishTimeNano - startTimeNano else -1L
                ).filter { it > 0 }.maxOrNull() ?: 0L

                val updatedAttempt = attempt.copy(
                    time0to100 = if (attempt0to100Nanos > 0) attempt0to100Nanos else -1L,
                    time0to200 = if (attempt0to200Nanos > 0) attempt0to200Nanos else -1L,
                    time100to200 = if (attempt100to200Nanos > 0) attempt100to200Nanos else -1L,
                    time0to402 = if (measured0to402 && attempt0to402Nanos > 0) {
                        attempt0to402Nanos
                    } else -1L,
                    maxSpeed = foregroundService?.getMaxSpeed() ?: 0f,
                    gSamples = adjustedGSamples,
                    gpsAccelSamples = adjustedGpsAccelSamples,
                    startTime = 0L,
                    timeStamps = adjustedGTimes,
                    gpsTimeStamps = adjustedGpsAccelTimes,
                    speedSamples = adjustedSpeedSamples,
                    speedTimeStamps = adjustedSpeedTimes,
                    duration = durationNs
                )

                val hasValidMeasurement = updatedAttempt.time0to100 > 0 ||
                        updatedAttempt.time0to200 > 0 ||
                        updatedAttempt.time100to200 > 0 ||
                        updatedAttempt.time0to402 > 0

                if (hasValidMeasurement) {
                    currentSession?.attempts?.add(updatedAttempt)
                    updateSessionBestTimes(updatedAttempt)
                }
            }
        } else {
            if (!attemptAlreadySaved) {
                saveCurrentAttempt()
            }
        }

        measuring = false
        stopPolling()

        // Спри G-force измерването
        foregroundService?.stopMeasurement()

        currentSession?.let { session ->
            session.updateBestTimes()

            val hasValidAttempts = if (measurementMode == MeasurementMode.ALL) {
                session.attempts.any { attempt ->
                    attempt.time0to100 > 0 || attempt.time0to200 > 0 ||
                            attempt.time100to200 > 0 || attempt.time0to402 > 0
                }
            } else {
                session.attempts.isNotEmpty()
            }

            if (hasValidAttempts) {
                DragStorage.addDragSession(this@DragRunPageActivity, session)
                sendBroadcast(Intent("SESSION_UPDATED").apply {
                    putExtra("SESSION_ID", session.id)
                })
                setResult(Activity.RESULT_OK)
            } else {
                runOnUiThread {
                    Toast.makeText(this@DragRunPageActivity,
                        "No valid measurements to save",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Изчисти всички натрупани данни, за да не се пренасят към други режими
        foregroundService?.resetData()

        cleanup()
        
        // Отваряме детайлите на сесията вместо да завършваме активността
        currentSession?.let { session ->
            val intent = Intent(this, DragSessionDetailsActivity::class.java)
            intent.putExtra("SESSION_ID", session.id)
            startActivity(intent)
        }
        
        finish()
    }

    private fun cleanup() {
        stopPolling()
        cancelRestartCooldown()
        foregroundService?.stopMeasurement()
        if (serviceBound) {
            try {
                unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.w("DragRunPageActivity", "Service already unbound", e)
            }
            serviceBound = false
        }

        try {
            stopService(Intent(this, ForegroundService::class.java))
        } catch (e: Exception) {
            Log.w("DragRunPageActivity", "Unable to stop ForegroundService", e)
        }
        foregroundService = null
    }

    override fun onBackPressed() {
        showStopConfirmation()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        
        // Ръчно презареждане на layout-а при смяна на ориентацията
        val layoutId = if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            R.layout.activity_drag_run
        } else {
            R.layout.activity_drag_run
        }
        
        setContentView(layoutId)
        
        // Реинициализираме всички view-та
        initializeViews()
        configureUIForMode()
        
        // Възстановяваме състоянието на UI-а
        updateReadyStatus()
        updateUIFromService()
        
        // Възстановяваме навигацията
        setupBottomNavigation()
        
    }

    override fun onResume() {
        super.onResume()
        // Презареждаме калибрацията при връщане в activity-то
        DragCalibration.setProfile(profileId)
        Log.d("DragRunPage", "🔄 onResume - Profile ID: $profileId, Calibrated: ${DragCalibration.isCalibrated}, Portrait: ${DragCalibration.isPortraitCalibrated}, Landscape: ${DragCalibration.isLandscapeCalibrated}")
        updateReadyStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        readyCheckHandler.removeCallbacksAndMessages(null)
        soundManager.release()
        cleanup()
    }

}