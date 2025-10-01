package com.example.clinometer.drag

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Location
import android.os.*
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.clinometer.*
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
    private lateinit var tvSpeedUnit: TextView

    private lateinit var tvCard0to100: TextView
    private lateinit var tvCard0to200: TextView
    private lateinit var tvCard100to200: TextView
    private lateinit var tvCard0to402: TextView
    private lateinit var tvCard0to402Distance: TextView
    
    private lateinit var tvLabel0to100: TextView
    private lateinit var tvLabel0to200: TextView
    private lateinit var tvLabel100to200: TextView
    private lateinit var tvLabel0to402: TextView

    private lateinit var card0to100: CardView
    private lateinit var card0to200: CardView
    private lateinit var card100to200: CardView
    private lateinit var card0to402: CardView

    private var serviceBound = false
    private var foregroundService: ForegroundService? = null
    private val pollHandler = Handler(Looper.getMainLooper())
    private val POLL_INTERVAL_MS = 25L

    private var currentSession: DragSession? = null
    private var currentAttempt: DragAttempt? = null
    private var profileId: Long = -1L
    private var temperature: Float? = null
    private var altitude: Float? = null

    private val START_SPEED_THRESHOLD = 4f  // Започваме измерване над 4 km/h
    private val CALIBRATION_SPEED = 10f     // Събираме данни до 10 km/h за изчисляване на ускорението
    private val MIN_ACCEL_MPS2 = 0.3        // Минимално допустимо ускорение (m/s^2)
    private val MAX_ACCEL_MPS2 = 10.0       // Максимално допустимо ускорение (m/s^2) за ограничение
    private val MAX_EXTRAPOLATION_SECONDS = 1.0 // Максимално време за екстраполация 0→4 km/h
    private val MEDIAN_WINDOW_SAMPLES = 5   // Брой семпли за медианен филтър по време на калибрация
    private val KMH_TO_MPS = 1.0 / 3.6
    private var calibrationStartTime: Long = 0L
    private var calibrationStartSpeed: Float = 0f
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
    private var distanceCompleted = false
    private var measurementComplete = false
    private var accumulatedDistance = 0f
    private var lastLocationForDistance: Location? = null

    private var lastHighSpeed: Float = 0f
    private var lowSpeedStartTime: Long = 0L
    private val LOW_SPEED_THRESHOLD = 3f
    private val LOW_SPEED_DURATION = 3000L
    private val MIN_START_SPEED = 3f
    private val ROLLING_START_MIN = 95f
    private val ROLLING_START_MAX = 99f

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
    private var decelerationDialog: AlertDialog? = null

    private lateinit var tvGCurrentBig: TextView
    private lateinit var tvGPeakSmall: TextView
    private lateinit var gGaugeView: com.example.clinometer.GGaugeView


    private var waitingForStop = false
    private var waitingForAcceleration = false
    private var measurementStarted = false

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
        if (location != null && location.accuracy < 30f) {
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
                if (loc != null) handleLocation(loc)
                updateUIFromService()
            } finally {
                if (measuring) pollHandler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        profileId = intent.getLongExtra("PROFILE_ID", -1L)
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
                    when (measurementMode) {
                        MeasurementMode.ALL -> {
                            tvStatus.text = getString(R.string.drag_status_ready_all)
                        }
                        MeasurementMode.ZERO_TO_100 -> {
                            tvStatus.text = getString(R.string.drag_status_ready_0to100)
                        }
                        MeasurementMode.ZERO_TO_200 -> {
                            tvStatus.text = getString(R.string.drag_status_ready_0to200)
                        }
                        MeasurementMode.HUNDRED_TO_200 -> {
                            tvStatus.text = getString(R.string.drag_status_ready_100to200)
                        }
                        MeasurementMode.QUARTER_MILE -> {
                            tvStatus.text = getString(R.string.drag_status_ready_quarter)
                        }
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
        tvGPeakSmall = findViewById(R.id.tvGPeakSmall)
        gGaugeView = findViewById(R.id.gGaugeView)
        
        // Update speed unit label
        tvSpeedUnit.text = UnitsManager.getSpeedUnit(this).symbol


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
        tvLabel0to100.text = "0-$speed100 ${speedUnit.symbol}"
        tvLabel0to200.text = "0-$speed200 ${speedUnit.symbol}"
        tvLabel100to200.text = "$speed100-$speed200 ${speedUnit.symbol}"
        tvLabel0to402.text = "0-${UnitsManager.getQuarterMileDistance(this)}"

        card0to100 = findViewById(R.id.card0to100)
        card0to200 = findViewById(R.id.card0to200)
        card100to200 = findViewById(R.id.card100to200)
        card0to402 = findViewById(R.id.card0to402)

        btnStop.text = getString(R.string.stop_session)

        resetDisplayValues()
        tvBigSpeed.text = "0"

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
        calibrationStartSpeed = 0f
        extrapolatedStartTime = 0L
        calibrationSpeedMps.clear()
        calibrationTimeNanos.clear()

        // Останалата част от кода остава същата...
        attemptAlreadySaved = false
        waitingForStop = false
        waitingForAcceleration = false
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
                MeasurementMode.HUNDRED_TO_200 ->
                    if (attempt100to200Nanos > 0) attempt100to200Nanos else -1L
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

            // RAW данни - без екстраполация, без синтетични точки
            val adjustedGSamples = if (gSamples.isNotEmpty() && gTimeStamps.isNotEmpty()) gSamples else emptyList()
            val adjustedGTimes = if (gSamples.isNotEmpty() && gTimeStamps.isNotEmpty()) gTimeStamps else emptyList()

            val adjustedGpsAccelSamples = if (gpsAccelSamples.isNotEmpty() && gpsAccelTimeStamps.isNotEmpty()) gpsAccelSamples else emptyList()
            val adjustedGpsAccelTimes = if (gpsAccelSamples.isNotEmpty() && gpsAccelTimeStamps.isNotEmpty()) gpsAccelTimeStamps else emptyList()

            val adjustedSpeedSamples = if (speedSamplesRaw.isNotEmpty() && speedTimeStampsRaw.isNotEmpty()) speedSamplesRaw else emptyList()
            val adjustedSpeedTimes = if (speedSamplesRaw.isNotEmpty() && speedTimeStampsRaw.isNotEmpty()) speedTimeStampsRaw else emptyList()

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
                maxSpeed = foregroundService?.getMaxSpeed() ?: 0f,
                gSamples = adjustedGSamples,
                gpsAccelSamples = adjustedGpsAccelSamples,
                startTime = 0L, // Timestamps са вече относителни
                timeStamps = adjustedGTimes,
                gpsTimeStamps = adjustedGpsAccelTimes,
                duration = measurementDuration,
                speedSamples = adjustedSpeedSamples,
                speedTimeStamps = adjustedSpeedTimes
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
            tvCard0to100.text = if (sessionBest0to100 > 0) "Best: ${formatNanos(sessionBest0to100)}" else "--"
            tvCard0to200.text = if (sessionBest0to200 > 0) "Best: ${formatNanos(sessionBest0to200)}" else "--"
            tvCard100to200.text = if (sessionBest100to200 > 0) "Best: ${formatNanos(sessionBest100to200)}" else "--"
            tvCard0to402.text = if (sessionBest0to402 > 0) "Best: ${formatNanos(sessionBest0to402)}" else "--"
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
        if (measurementStarted) return
        measurementStarted = true
        
        createNewAttempt()
        
        // Start G-force measurement in service
        foregroundService?.startNewMeasurement()
        
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
        if (loc.accuracy > 30f) return

        val speedKmh = loc.speed * 3.6f

        if (!gpsReady) {
            gpsReady = true
            updateReadyStatus()
        }

        // Деселерация детекция - само ако имаме реална скорост
        if (started && !measurementComplete && speedKmh > 5f) {
            val speedDiff = speedKmh - lastSpeed
            if (speedDiff < -5f && speedKmh < 80f && !decelerationDetected) {
                decelerationDetected = true
                handleDeceleration(speedKmh)
            }
        } else if (measurementMode == MeasurementMode.HUNDRED_TO_200 && started && !measurementComplete && speedKmh > 5f) {
            // За 100-200 режим - детектираме деселерация докато измерваме
            val speedDiff = speedKmh - lastSpeed
            if (speedDiff < -5f && speedKmh < 80f && !decelerationDetected) {
                decelerationDetected = true
                handleDeceleration(speedKmh)
            }
        }

        when (measurementMode) {
            MeasurementMode.ZERO_TO_100,
            MeasurementMode.ZERO_TO_200,
            MeasurementMode.QUARTER_MILE -> {

                if (waitingForFullStop) {
                    if (speedKmh < 3f) {
                        waitingForFullStop = false
                        decelerationDetected = false
                        isCalibrating = false
                        calibrationComplete = false
                        createNewAttempt()
                        tvStatus.text = getString(R.string.drag_status_ready_all)
                        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                    }
                } else if (!started && speedKmh >= START_SPEED_THRESHOLD && serviceReady && gpsReady && !decelerationDetected) {
                    // Започваме измерване при 4 km/h
                    if (!isCalibrating) {
                        isCalibrating = true
                        calibrationStartTime = System.nanoTime()
                        calibrationStartSpeed = speedKmh
                        calibrationSpeedMps.clear()
                        calibrationTimeNanos.clear()
                        // добавяме първи семпъл
                        val firstMps = (speedKmh * KMH_TO_MPS).toFloat()
                        calibrationSpeedMps.add(firstMps)
                        calibrationTimeNanos.add(calibrationStartTime)
                        started = true
                        startTimeNano = System.nanoTime() // Започваме от текущото време (4 km/h)
                        // Задаваме measurementStartTimeNano в service-а да е същото време
                        foregroundService?.setMeasurementStartTimeNano(startTimeNano)
                        startLocation = loc
                        val attemptNumber = getCurrentAttemptNumber()
                        val modeText = when (measurementMode) {
                            MeasurementMode.ZERO_TO_100 -> "0-100"
                            MeasurementMode.ZERO_TO_200 -> "0-200"
                            MeasurementMode.QUARTER_MILE -> "0-402m"
                            else -> ""
                        }
                        tvStatus.text = getString(R.string.drag_status_measuring, modeText, attemptNumber)
                        foregroundService?.startNewMeasurement()
                    }
                } else if (!started) {
                }

                // Събиране на калибрационни семпли докато не достигнем 15 km/h
                if (isCalibrating && !calibrationComplete && speedKmh < CALIBRATION_SPEED) {
                    collectCalibrationSampleIfNeeded(speedKmh)
                }

                // Калибрация на ускорението
                if (isCalibrating && !calibrationComplete && speedKmh >= CALIBRATION_SPEED) {
                    calibrationComplete = true
                    val calibrationEndTime = System.nanoTime()

                    // Изчисляваме ускорение по данните 6→15 km/h в m/s^2 с медианен филтър
                    val accelMps2 = computeCalibratedAccelerationMps2()

                    // Екстраполираме 0→6 km/h (1.666... m/s)
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
                if (started && !measurementComplete && speedKmh >= 200f) {
                    val nowNano = System.nanoTime()
                    attempt100to200Nanos = nowNano - rolling100StartTime

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
                // След това проверяваме другите състояния САМО ако не сме завършили измерване
                else if (waitingForFullStop && !measurementComplete) {
                    if (speedKmh in 95f..99f) {
                        waitingForFullStop = false
                        decelerationDetected = false
                        rollingStartReady = true
                        started = false
                        measurementComplete = false
                        tvStatus.text = getString(R.string.drag_status_pass_100)
                        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                    } else if (speedKmh < 95f) {
                        // Показваме съобщението само ако не е деселерация
                        if (!decelerationDetected) {
                            tvStatus.text = getString(R.string.drag_status_return_95_99)
                            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                        }
                    } else if (speedKmh > 99f) {
                        tvStatus.text = getString(R.string.drag_status_too_fast)
                        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                    }
                } else if (waitingForFullStop && measurementComplete) {
                    // След успешно измерване - показваме съобщението докато не се върне на 95-99
                    if (speedKmh in 95f..99f) {
                        waitingForFullStop = false
                        decelerationDetected = false
                        rollingStartReady = true
                        started = false
                        measurementComplete = false
                        tvStatus.text = getString(R.string.drag_status_pass_100)
                        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                    }
                    // Не променяме съобщението ако не сме в 95-99 диапазона
                } else if (!started && speedKmh >= 100f && rollingStartReady && serviceReady && gpsReady && !decelerationDetected) {
                    // Започваме измерване при 100+ km/h
                    started = true
                    rolling100StartTime = System.nanoTime()
                    val attemptNumber = getCurrentAttemptNumber()
                    tvStatus.text = getString(R.string.drag_status_measuring, "100-200", attemptNumber)
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
                    foregroundService?.startNewMeasurement()
                } else if (!started && speedKmh in 95f..99f && !rollingStartReady) {
                    // Готови сме за старт
                    rollingStartReady = true
                    tvStatus.text = getString(R.string.drag_status_pass_100)
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                } else if (!started && speedKmh < 95f) {
                    // Твърде бавно
                    rollingStartReady = false
                    tvStatus.text = getString(R.string.drag_status_speed_up)
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                } else if (!started && speedKmh > 99f && !rollingStartReady && !measurementComplete) {
                    // Твърде бързо - само ако не сме завършили измерване
                    tvStatus.text = getString(R.string.drag_status_too_fast)
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                }
            }

            MeasurementMode.ALL -> {
                // Проверка за пълна спирка след деселерация
                if (waitingForFullStop) {
                    if (speedKmh < 3f) {
                        waitingForFullStop = false
                        decelerationDetected = false
                        isCalibrating = false
                        calibrationComplete = false
                        restartAllMeasurements()
                        tvStatus.text = getString(R.string.drag_status_ready_all)
                        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                    } else {
                        // Продължаваме да показваме съобщението
                        tvStatus.text = getString(R.string.drag_status_stop_to_restart)
                        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                    }
                } else if (!started && speedKmh >= START_SPEED_THRESHOLD && serviceReady && gpsReady && !decelerationDetected) {
                    // Започваме измерване при 4 km/h
                    if (!isCalibrating) {
                        isCalibrating = true
                        calibrationStartTime = System.nanoTime()
                        calibrationStartSpeed = speedKmh
                        calibrationSpeedMps.clear()
                        calibrationTimeNanos.clear()
                        val firstMps = (speedKmh * KMH_TO_MPS).toFloat()
                        calibrationSpeedMps.add(firstMps)
                        calibrationTimeNanos.add(calibrationStartTime)
                        started = true
                        startTimeNano = System.nanoTime() // Започваме от текущото време (4 km/h)
                        // Задаваме measurementStartTimeNano в service-а да е същото време
                        foregroundService?.setMeasurementStartTimeNano(startTimeNano)
                        startLocation = loc
                        tvStatus.text = getString(R.string.drag_status_calibrating)
                        foregroundService?.startNewMeasurement()
                    }
                } else if (!started) {
                }

                // Събиране на калибрационни семпли докато не достигнем 15 km/h
                if (isCalibrating && !calibrationComplete && speedKmh < CALIBRATION_SPEED) {
                    collectCalibrationSampleIfNeeded(speedKmh)
                }

                // Калибрация логика
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
                // За индивидуални режими - изтриваме данните от текущия опит
                measurementComplete = true
                waitingForFullStop = true
                started = false
                
                // НЕ запазваме опита при деселерация - изтриваме данните
                attemptAlreadySaved = true // Маркираме като "запазен" за да не се запази
                
                // Нулираме измерванията за нов опит
                attempt0to100Nanos = -1L
                attempt0to200Nanos = -1L
                attempt100to200Nanos = -1L
                attempt0to402Nanos = -1L
                timeAt100Nano = -1L
                measured0to100 = false
                measured0to200 = false
                measured100to200 = false
                measured0to402 = false
                
                // Нулираме дисплея
                resetDisplayValues()

                foregroundService?.stopMeasurement()
                tvStatus.text = getString(R.string.drag_status_deceleration)
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            }

            MeasurementMode.HUNDRED_TO_200 -> {
                // За 100-200 - изтриваме данните и чакаме 95-99 km/h
                measurementComplete = true
                waitingForFullStop = true
                started = false
                rollingStartReady = false
                
                // НЕ запазваме опита при деселерация
                attemptAlreadySaved = true
                
                // Нулираме измерванията
                attempt100to200Nanos = -1L
                measured100to200 = false
                
                // Нулираме дисплея
                resetDisplayValues()

                foregroundService?.stopMeasurement()
                tvStatus.text = getString(R.string.drag_status_deceleration_100to200)
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            }

            MeasurementMode.ALL -> {
                // За ALL режим - изтриваме ВСИЧКИ данни и рестартираме сесията
                measurementComplete = true
                waitingForFullStop = true
                started = false
                
                // НЕ запазваме нищо при деселерация - изтриваме всички данни
                attemptAlreadySaved = true
                
                // Нулираме всички измервания
                attempt0to100Nanos = -1L
                attempt0to200Nanos = -1L
                attempt100to200Nanos = -1L
                attempt0to402Nanos = -1L
                timeAt100Nano = -1L
                measured0to100 = false
                measured0to200 = false
                measured100to200 = false
                measured0to402 = false
                
                // Нулираме калибрацията
                isCalibrating = false
                calibrationComplete = false
                calibrationSpeedMps.clear()
                calibrationTimeNanos.clear()
                calibrationStartTime = 0L
                calibrationStartSpeed = 0f
                extrapolatedStartTime = 0L
                accelStartNano = 0L
                
                // Нулираме дисплея
                resetDisplayValues()

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
                    waitingForFullStop = true
                    tvStatus.text = getString(R.string.drag_status_quarter_complete_stop)
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                } else {
                    // В ALL режим - проверяваме дали всички измервания са завършени
                    tvStatus.text = getString(R.string.drag_status_quarter_complete_continue)
                    checkAllMeasurementsComplete()
                }
            }
        }
    }

    private fun showDecelerationDialog() {
        if (decelerationDialog?.isShowing == true) return

        runOnUiThread {
            tvStatus.text = getString(R.string.drag_status_decel_detected)
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))

            decelerationDialog = AlertDialog.Builder(this)
                .setTitle("Deceleration Detected")
                .setMessage("The measurement was interrupted. Please come to a complete stop and the system will automatically prepare for a new attempt.")
                .setPositiveButton("Restart Now") { dialog, _ ->
                    restartAllMeasurements()
                    dialog.dismiss()
                }
                .setCancelable(false)
                .show()
        }
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
        calibrationStartSpeed = 0f
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

    private fun updateUIFromService() {
        val svc = foregroundService ?: return

        val currentG = svc.getCurrentG()
        val peakG = svc.getPeakG()
        val speedFloat = svc.getCurrentSpeed()
        val speed = speedFloat.toInt()


        runOnUiThread {
            // Скорост - конвертирана според избраната единица
            val convertedSpeed = UnitsManager.convertSpeed(speed.toFloat(), UnitsManager.getSpeedUnit(this))
            tvBigSpeed.text = convertedSpeed.toInt().toString()

            // G-force
            tvGCurrentBig.text = String.format("%.2f g", currentG)
            tvGPeakSmall.text = String.format("Peak: %.2f g", peakG)

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
        if (started && !measurementComplete && !waitingForStop) {
            val nowNano = System.nanoTime()

            // Използваме СЪЩАТА времева основа като RAW данните
            val measurementStartTimeNano = foregroundService?.getMeasurementStartTimeNano() ?: 0L

            // 0-100 измерване
            if ((measurementMode == MeasurementMode.ALL || measurementMode == MeasurementMode.ZERO_TO_100) && !measured0to100) {
                val svcT100 = foregroundService?.getTime0to100Nanos() ?: 0L
                if (svcT100 > 0 && measurementStartTimeNano > 0) {
                    // Ползваме точно семпълното време от Service
                    attempt0to100Nanos = svcT100
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
                        attempt100to200Nanos = (measurementStartTimeNano + svcT200) - timeAt100Nano
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
                if (speedDiff < -5f && speedFloat < 80f) {
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
        if (serviceBound) {
            try {
                unbindService(serviceConnection)
            } catch (e: Exception) {
            }
            serviceBound = false
        }
        stopService(Intent(this, ForegroundService::class.java))
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

    override fun onDestroy() {
        super.onDestroy()
        decelerationDialog?.dismiss()
        readyCheckHandler.removeCallbacksAndMessages(null)
        soundManager.release()
        cleanup()
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 2001)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        } else {
            tvStatus.text = getString(R.string.measure_no_permission)
        }
    }
}