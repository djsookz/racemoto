package com.revix.app.drag

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.ServiceConnection
import android.content.res.Configuration
import android.location.Location
import android.os.*
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.revix.app.*
import com.revix.app.DragCalibration
import com.revix.app.racebox.RaceBoxDebugGate
import com.revix.app.settings.DragRolloutSettings
import com.revix.app.settings.SoundManager
import com.revix.app.settings.UnitsManager
import android.view.animation.AccelerateDecelerateInterpolator
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    private lateinit var statusPulseDot: View
    private lateinit var tvBigSpeed: TextView
    private var tvSpeedUnit: TextView? = null
    private lateinit var tvAttemptValue: TextView
    private lateinit var ivWeatherCondition: ImageView
    private lateinit var tvWeatherTemp: TextView
    private lateinit var tvWeatherHumidity: TextView
    private lateinit var tvWeatherWind: TextView
    private lateinit var singleModeContainer: LinearLayout
    private var llTimeCards: LinearLayout? = null
    private var timeCardsFrame: FrameLayout? = null
    private var quarterHeaderContainer: LinearLayout? = null
    private lateinit var quarterSectorsContainer: LinearLayout
    private lateinit var allModeContainer: LinearLayout
    private lateinit var tvSingleMetricLabel: TextView
    private lateinit var tvSingleMetricValue: TextView
    private lateinit var llZeroTo200Splits: LinearLayout
    private lateinit var tvSingleModeMinSpeed: TextView
    private lateinit var pbZeroTo200Progress: ProgressBar
    private lateinit var tvZeroTo200MaxSpeed: TextView
    private lateinit var llZeroTo200StageRow: LinearLayout
    private lateinit var tvZeroTo200Stage0to100: TextView
    private lateinit var tvZeroTo200Stage100to200: TextView
    private var llZeroTo200StageRowInAccel: LinearLayout? = null
    private var tvZeroTo200Stage0to100InAccel: TextView? = null
    private var tvZeroTo200Stage100to200InAccel: TextView? = null
    private lateinit var tvQuarterMetricLabel: TextView
    private lateinit var tvQuarterMetricValue: TextView
    private lateinit var tvQuarterProgressMin: TextView
    private lateinit var tvQuarterProgressMax: TextView
    private lateinit var pbQuarterProgress: ProgressBar
    private lateinit var tvSector50Time: TextView
    private lateinit var tvSector100Time: TextView
    private lateinit var tvSector200Time: TextView
    private lateinit var tvSector300Time: TextView
    private lateinit var tvSector402Time: TextView
    private lateinit var tvSector50Speed: TextView
    private lateinit var tvSector100Speed: TextView
    private lateinit var tvSector200Speed: TextView
    private lateinit var tvSector300Speed: TextView
    private lateinit var tvSector402Speed: TextView
    private lateinit var pbAll0to100: ProgressBar
    private lateinit var pbAll100to200: ProgressBar
    private lateinit var pbAll0to200: ProgressBar
    private lateinit var pbAll0to402: ProgressBar
    private var allModeQuarterSectorsInAccel: LinearLayout? = null
    private var tvAllModeSector50Time: TextView? = null
    private var tvAllModeSector100Time: TextView? = null
    private var tvAllModeSector200Time: TextView? = null
    private var tvAllModeSector300Time: TextView? = null
    private var tvAllModeSector402Time: TextView? = null
    private var tvAllModeSector50Speed: TextView? = null
    private var tvAllModeSector100Speed: TextView? = null
    private var tvAllModeSector200Speed: TextView? = null
    private var tvAllModeSector300Speed: TextView? = null
    private var tvAllModeSector402Speed: TextView? = null
    private var accelForcePanel: LinearLayout? = null
    private var accelTrackContainer: View? = null
    private var tvAccelForceLabel: TextView? = null
    private var tvAccelTick05: TextView? = null
    private var tvAccelScale075: TextView? = null
    private var tvAccelScale10: TextView? = null
    private var tvAccelScale125: TextView? = null
    private var tvAccelTick15: TextView? = null

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
    private val LIVE_COMPENSATION_REFRESH_MS = 250L

    private var currentSession: DragSession? = null
    private val dragPersistExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "drag-persist").apply { isDaemon = true }
    }
    private var currentAttempt: DragAttempt? = null
    private val dragVideoAttachListener: (Long, Long, String, Long) -> Unit = { sessionId, attemptId, path, t0OffsetMs ->
        runOnUiThread { attachVideoToAttempt(sessionId, attemptId, path, t0OffsetMs) }
    }
    private val pendingAllModePartialAttempts = mutableListOf<DragAttempt>()
    private var profileId: Long = -1L
    private var dragIsMotorcycle = false
    private var temperature: Float? = null
    private var altitude: Float? = null
    private var humidity: Int? = null
    private var windKph: Float? = null
    private var weatherIcon: Int? = null
    private val QUARTER_MILE_SECTOR_50 = 50f
    private val QUARTER_MILE_SECTOR_100 = 100f
    private val QUARTER_MILE_SECTOR_200 = 200f
    private val QUARTER_MILE_SECTOR_300 = 300f
    private val QUARTER_MILE_SECTOR_402 = 402.336f

    private val KMH_TO_MPS = 1.0 / 3.6
    private val LATENCY_MAX_COMPENSATION_NS = 220_000_000L
    private val LATENCY_BASELINE_WINDOW_NS = 1_500_000_000L
    private val LATENCY_LOCAL_WINDOW_NS = 700_000_000L
    private val LATENCY_MAX_SAMPLE_AGE_NS = 1_500_000_000L
    private val LATENCY_MAX_START_EXCESS_NS = 120_000_000L
    private val LATENCY_MAX_DRIFT_NS = 180_000_000L
    private val LATENCY_MIN_SAMPLES = 4
    private val SMART_INTERPOLATION_GAP_NS = 350_000_000L
    private val SMART_INTERPOLATION_MAX_SHIFT_NS = 200_000_000L
    private val FUSION_TIMELINE_STEP_NS = 40_000_000L
    private val FUSION_ACCEL_BIAS_WINDOW_NS = 300_000_000L
    private val FUSION_ACCEL_CONTEXT_NS = 1_000_000_000L
    private val FUSION_ACCEL_BIAS_CLAMP_MPS2 = 1.5f
    private val FUSION_CORRECTION_CLAMP_MPS2 = 6.0
    private val FUSION_CORRECTION_SMOOTHING_PASSES = 2
    private val FUSION_ZERO_PHASE_ALPHA = 0.28
    /** Phone accel fusion may lead GPS slightly; never invent crossings far above GPS max. */
    private val PHONE_FUSION_SPEED_HEADROOM_KMH = 8f
    private val CHART_GPS_BLEND_WEIGHT = 0.88f
    private val CHART_FUSION_HEADROOM_KMH = 3f
    // Phone standing-start: constant Fused lag vs RaceBox. Chart + times from t0 share this.
    // 100-200 delta is not shifted (both ends move equally). RaceBox path stays 0.
    private val PHONE_GPS_LAG_ALIGN_NS = 200_000_000L

    private var serviceReady = false
    private var gpsReady = false
    private val readyCheckHandler = Handler(Looper.getMainLooper())

    private var measuring = false
    private var finishingSession = false
    private var started = false
    /** True once the current attempt has an official launch trigger (linear accel or 100-200 rolling start). */
    private var currentAttemptWasOfficiallyStarted = false
    private var startLocation: Location? = null
    private var startTimeNano: Long = 0L
    private val TARGET_METERS = 402.336f
    private val GPS_READY_ACCURACY_METERS = 30f
    private val FULL_STOP_REARM_SPEED_KMH = 3f // Re-arm only after near full stop
    private val REARM_UNIQUE_FIXES = 2
    private val REARM_MIN_DWELL_NS = 400_000_000L
    private val REARM_ON_GAS_G = 0.15f
    // Sure end-of-run for ALL / 100-200. Replaces -5 km/h below 80 and coast-below-45.
    /** If first RaceBox SPEED sample is later than this, prepend t=0 so chart starts at launch. */
    private val CHART_LAUNCH_GAP_FILL_NS = 150_000_000L
    // Official t0 must be consumed soon (phone 1ft + 100ms poll). Wider than pre-rollout latch.
    private val TRIGGER_FRESHNESS_NS = 1_200_000_000L
    private val LAUNCH_CONFIRM_SPEED_KMH = 5f
    private val FAIL_START_GRACE_NS = 2_000_000_000L
    private var launchSpeedConfirmed = false
    private val RACEBOX_STATIONARY_MAX_KMH = 3f
    private val RACEBOX_MOTION_START_KMH = 3.5f
    private val RACEBOX_ROLLOUT_METERS = DragRolloutSettings.ROLLOUT_METERS
    private var raceBoxStationaryReady: Boolean = false
    private var raceBoxStartAnchor: Location? = null
    private var raceBoxAwaitingRollout: Boolean = false
    private var raceBoxRolloutDistanceM: Float = 0f
    private var raceBoxRolloutLastElapsedNs: Long = -1L
    private var raceBoxRolloutLastSpeedKmh: Float = -1f
    private var raceBoxRolloutT0Ns: Long = 0L
    /** Phone standing start: must have been ≤ 3 km/h before gas can latch. */
    private var phoneStationaryReady: Boolean = false
    private var rearmStopFixCount = 0
    private var rearmLastFixNs = -1L
    private var rearmFirstFixNs = 0L
    private val END_BRAKE_G = -0.30f
    private val END_BRAKE_HOLD_NS = 1_200_000_000L
    private val END_ON_GAS_G = 0.10f
    private val END_COAST_DROP_KMH = 27f
    private val END_COAST_UNIQUE_FIXES = 3
    private val END_RACEBOX_STREAK_NS = 1_500_000_000L
    private val END_RECOVERY_KMH = 2f
    private val END_GRACE_AFTER_START_NS = 1_500_000_000L
    private val ROLLING_CROSS_SPEED_KMH = 100f
    private val ROLLING_REARM_SPEED_KMH = 95f
    private val ROLLING_FINISH_SPEED_KMH = 200f
    private var distanceCompleted = false
    private var measurementComplete = false
    private var accumulatedDistance = 0f
    private var lastLocationForDistance: Location? = null
    private var lastQuarterDistanceElapsedNanos: Long = -1L
    private var lastQuarterSpeedKmh: Float = -1f

    private var sessionBest0to100: Long = -1L
    private var sessionBest0to200: Long = -1L
    private var sessionBest100to200: Long = -1L
    private var sessionBest0to402: Long = -1L

    private var measurementMode: MeasurementMode = MeasurementMode.ALL
    private var rollingStartReady = false
    private var rolling100StartTime: Long = 0L
    private var rolling100StartElapsedNs: Long = 0L
    private var rollingLastFixNs: Long = -1L
    private var rollingLastSpeedKmh: Float = -1f
    private var rollingCanCross100 = false

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

    private var attempt0to100Nanos: Long = -1L
    private var attempt0to200Nanos: Long = -1L
    private var attempt100to200Nanos: Long = -1L
    private var attempt0to402Nanos: Long = -1L
    private var timeAt100Nano: Long = -1L
    private var sector50TimeNanos: Long = -1L
    private var sector100TimeNanos: Long = -1L
    private var sector200TimeNanos: Long = -1L
    private var sector300TimeNanos: Long = -1L
    private var sector402TimeNanos: Long = -1L
    private var sector50SpeedKmh: Float = -1f
    private var sector100SpeedKmh: Float = -1f
    private var sector200SpeedKmh: Float = -1f
    private var sector300SpeedKmh: Float = -1f
    private var sector402SpeedKmh: Float = -1f

    private data class DistanceCrossingPoint(
        val timeNs: Long,
        val speedKmh: Float
    )

    private data class SpeedAnchor(
        val timeNs: Long,
        val speedMps: Double
    )

    private data class LatencyCompensatedMilestones(
        val estimatedLatencyNs: Long = 0L,
        val time0to100Ns: Long = -1L,
        val time0to200Ns: Long = -1L,
        val time100to200Ns: Long = -1L,
        val time0to402Ns: Long = -1L,
        val distance50m: DistanceCrossingPoint? = null,
        val distance100m: DistanceCrossingPoint? = null,
        val distance200m: DistanceCrossingPoint? = null,
        val distance300m: DistanceCrossingPoint? = null,
        val distance402m: DistanceCrossingPoint? = null,
        val fusedSpeedSamplesKmh: List<Float> = emptyList(),
        val fusedSpeedTimeStampsNs: List<Long> = emptyList()
    )

    private var lastSpeed: Float = 0f
    private var lastLiveCompensationRefreshMs: Long = 0L
    private var latestFusedSpeedSamplesKmh: List<Float> = emptyList()
    private var latestFusedSpeedTimeStampsNs: List<Long> = emptyList()
    private var decelerationDetected = false
    private var waitingForFullStop = false
    private var runPeakSpeedKmh = 0f
    private var endBrakeHoldStartNs = 0L
    private var endCoastFallingFixes = 0
    private var endCoastStreakStartNs = 0L
    private var endLastFixElapsedNs = -1L
    private var endLastFixSpeedKmh = -1f

    private lateinit var tvGCurrentBig: TextView
    private lateinit var gGaugeView: com.revix.app.GGaugeView
    private lateinit var gContainer: LinearLayout
    private lateinit var tvAccelForceCurrent: TextView
    private lateinit var tvAccelPeakSummary: TextView
    private lateinit var pbAccelForce: ProgressBar
    private lateinit var vAccelMarker: View
    private var isShowingGForceInsteadOfSpeed = false
    private var lastDisplayedConvertedSpeed = 0f
    private var lastDisplayedG = 0f
    private var displayedAccelForceG = 0f
    private var peakAccelForceG = 0f
    private val LIVE_ACCEL_DISPLAY_CAPACITY = 5_000
    private val liveAccelDisplaySamples = mutableListOf<Float>()
    private val liveAccelDisplayTimeStamps = mutableListOf<Long>()
    private val ACCEL_FORCE_MAX_G = 1.5f
    private val ACCEL_PANEL_FRAME_MS = 16L
    private val accelPanelHandler = Handler(Looper.getMainLooper())
    private var accelPanelLoopActive = false
    private var lastAccelTextValue = Float.NaN
    private var lastAccelPeakTextValue = Float.NaN
    private var lastAccelProgressValue = -1
    private val accelPanelRunnable = object : Runnable {
        override fun run() {
            if (!accelPanelLoopActive) return
            sampleAndRenderAccelerationPanel()
            accelPanelHandler.postDelayed(this, ACCEL_PANEL_FRAME_MS)
        }
    }

    private var measurementStarted = false
    private var isStatusPulseActive = false
    private var statusPulseAnimator: AnimatorSet? = null
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
            syncServiceRunOrientation()
            ensureDragCalibrationRuntimeReady()
            
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
        dragIsMotorcycle = com.revix.app.data.ProfileStorage.loadProfiles(this)
            .firstOrNull { it.id == profileId }?.vehicleType == Profile.VehicleType.MOTORCYCLE
        
        // Зареждаме калибрацията за този профил
        DragCalibration.setProfile(profileId)
        ensureDragCalibrationRuntimeReady()
        Log.d("DragRunPage", "📍 Profile ID: $profileId, Calibrated: ${DragCalibration.isCalibrated}, Portrait: ${DragCalibration.isPortraitCalibrated}, Landscape: ${DragCalibration.isLandscapeCalibrated}")
        
        temperature = intent.getFloatExtra("TEMPERATURE", 0f).takeIf { it != 0f }
        altitude = intent.getFloatExtra("ALTITUDE", 0f).takeIf { it != 0f }
        humidity = intent.getIntExtra("HUMIDITY", -1).takeIf { it in 0..100 }
        windKph = intent.getFloatExtra("WIND_KPH", Float.NaN).takeIf { !it.isNaN() && it >= 0f }
        weatherIcon = intent.getIntExtra("WEATHER_ICON", -1).takeIf { it != -1 }
        
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
        pendingAllModePartialAttempts.clear()
        currentSession = null
        DragRunVideoBridge.addVideoListener(dragVideoAttachListener)
        publishDragVideoHud()
        
        // If we have GPS coordinates from countdown, mark GPS as ready
        if (latitude != null && longitude != null) {
            gpsReady = true
        }
        
        updateReadyStatus()
        ensureServiceAndStart()
    }

    private fun updateReadyStatus() {
        runOnUiThread {
            ensureDragCalibrationRuntimeReady()
            when {
                !serviceReady -> {
                    tvStatus.text = getString(R.string.drag_status_initializing)
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                    setStatusPulseActive(false)
                }
                !gpsReady -> {
                    tvStatus.text = getString(R.string.drag_status_waiting_gps)
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                    setStatusPulseActive(false)
                }
                else -> {
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                    setStatusPulseActive(false)
                    
                    // Проверяваме калибрацията на посоката
                    if (!hasUsableDragCalibration()) {
                        // Не е калибрирано - насочваме към Settings
                        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                        tvStatus.text = getString(R.string.drag_status_not_calibrated_settings)
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
        statusPulseDot = findViewById(R.id.viewStatusPulseDot)
        tvBigSpeed = findViewById(R.id.tvBigSpeed)
        tvSpeedUnit = findViewById(R.id.tvSpeedUnit)
        tvAttemptValue = findViewById(R.id.tvAttemptValue)
        ivWeatherCondition = findViewById(R.id.ivWeatherCondition)
        tvWeatherTemp = findViewById(R.id.tvWeatherTemp)
        tvWeatherHumidity = findViewById(R.id.tvWeatherHumidity)
        tvWeatherWind = findViewById(R.id.tvWeatherWind)
        singleModeContainer = findViewById(R.id.singleModeContainer)
        llTimeCards = findViewById(R.id.llTimeCards)
        timeCardsFrame = findViewById(R.id.timeCardsFrame)
        quarterHeaderContainer = findViewById(R.id.quarterHeaderContainer)
        quarterSectorsContainer = findViewById(R.id.quarterSectorsContainer)
        allModeContainer = findViewById(R.id.allModeContainer)
        tvSingleMetricLabel = findViewById(R.id.tvSingleMetricLabel)
        tvSingleMetricValue = findViewById(R.id.tvSingleMetricValue)
        llZeroTo200Splits = findViewById(R.id.llZeroTo200Splits)
        tvSingleModeMinSpeed = findViewById(R.id.tvSingleModeMinSpeed)
        pbZeroTo200Progress = findViewById(R.id.pbZeroTo200Progress)
        tvZeroTo200MaxSpeed = findViewById(R.id.tvZeroTo200MaxSpeed)
        llZeroTo200StageRow = findViewById(R.id.llZeroTo200StageRow)
        tvZeroTo200Stage0to100 = findViewById(R.id.tvZeroTo200Stage0to100)
        tvZeroTo200Stage100to200 = findViewById(R.id.tvZeroTo200Stage100to200)
        llZeroTo200StageRowInAccel = findViewById(R.id.llZeroTo200StageRowInAccel)
        tvZeroTo200Stage0to100InAccel = findViewById(R.id.tvZeroTo200Stage0to100InAccel)
        tvZeroTo200Stage100to200InAccel = findViewById(R.id.tvZeroTo200Stage100to200InAccel)
        tvQuarterMetricLabel = findViewById(R.id.tvQuarterMetricLabel)
        tvQuarterMetricValue = findViewById(R.id.tvQuarterMetricValue)
        tvQuarterProgressMin = findViewById(R.id.tvQuarterProgressMin)
        tvQuarterProgressMax = findViewById(R.id.tvQuarterProgressMax)
        pbQuarterProgress = findViewById(R.id.pbQuarterProgress)
        tvSector50Time = findViewById(R.id.tvSector50Time)
        tvSector100Time = findViewById(R.id.tvSector100Time)
        tvSector200Time = findViewById(R.id.tvSector200Time)
        tvSector300Time = findViewById(R.id.tvSector300Time)
        tvSector402Time = findViewById(R.id.tvSector402Time)
        tvSector50Speed = findViewById(R.id.tvSector50Speed)
        tvSector100Speed = findViewById(R.id.tvSector100Speed)
        tvSector200Speed = findViewById(R.id.tvSector200Speed)
        tvSector300Speed = findViewById(R.id.tvSector300Speed)
        tvSector402Speed = findViewById(R.id.tvSector402Speed)
        pbAll0to100 = findViewById(R.id.pbAll0to100)
        pbAll100to200 = findViewById(R.id.pbAll100to200)
        pbAll0to200 = findViewById(R.id.pbAll0to200)
        pbAll0to402 = findViewById(R.id.pbAll0to402)
        allModeQuarterSectorsInAccel = findViewById(R.id.allModeQuarterSectorsInAccel)
        tvAllModeSector50Time = findViewById(R.id.tvAllModeSector50Time)
        tvAllModeSector100Time = findViewById(R.id.tvAllModeSector100Time)
        tvAllModeSector200Time = findViewById(R.id.tvAllModeSector200Time)
        tvAllModeSector300Time = findViewById(R.id.tvAllModeSector300Time)
        tvAllModeSector402Time = findViewById(R.id.tvAllModeSector402Time)
        tvAllModeSector50Speed = findViewById(R.id.tvAllModeSector50Speed)
        tvAllModeSector100Speed = findViewById(R.id.tvAllModeSector100Speed)
        tvAllModeSector200Speed = findViewById(R.id.tvAllModeSector200Speed)
        tvAllModeSector300Speed = findViewById(R.id.tvAllModeSector300Speed)
        tvAllModeSector402Speed = findViewById(R.id.tvAllModeSector402Speed)

        tvGCurrentBig = findViewById(R.id.tvGCurrentBig)
        gGaugeView = findViewById(R.id.gGaugeView)
        gContainer = findViewById(R.id.g_container)
        tvAccelForceCurrent = findViewById(R.id.tvAccelForceCurrent)
        tvAccelPeakSummary = findViewById(R.id.tvAccelPeakSummary)
        pbAccelForce = findViewById(R.id.pbAccelForce)
        vAccelMarker = findViewById(R.id.vAccelMarker)
        accelForcePanel = findViewById(R.id.accelForcePanel)
        accelTrackContainer = findViewById(R.id.accelTrackContainer)
        tvAccelForceLabel = findViewById(R.id.tvAccelForceLabel)
        tvAccelTick05 = findViewById(R.id.tvAccelTick05)
        tvAccelScale075 = findViewById(R.id.tvAccelScale075)
        tvAccelScale10 = findViewById(R.id.tvAccelScale10)
        tvAccelScale125 = findViewById(R.id.tvAccelScale125)
        tvAccelTick15 = findViewById(R.id.tvAccelTick15)

        tvBigSpeed.setOnClickListener(null)
        tvGCurrentBig.setOnClickListener(null)
        gContainer.setOnClickListener(null)
        gGaugeView.setOnClickListener(null)
        tvBigSpeed.isClickable = false
        tvGCurrentBig.isClickable = false
        gContainer.isClickable = false
        gGaugeView.isClickable = false
        
        // Update speed unit label
        tvSpeedUnit?.text = UnitsManager.getSpeedUnit(this).symbol
        updateWeatherSummaryDisplay()
        updateAttemptIndicator()
        resetQuarterSectorDisplay()
        resetZeroTo200SplitDisplay()
        updateAllModeProgress(0f)
        resetAccelerationForcePanel()


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
        tvLabel0to100?.text = formatDragSpeedRangeWithUnit(0, 100)
        tvLabel0to200?.text = formatDragSpeedRangeWithUnit(0, 200)
        tvLabel100to200?.text = formatDragSpeedRangeWithUnit(100, 200)
        tvLabel0to402?.text = formatDragQuarterRangeLabel()

        card0to100 = findViewById(R.id.card0to100)
        card0to200 = findViewById(R.id.card0to200)
        card100to200 = findViewById(R.id.card100to200)
        card0to402 = findViewById(R.id.card0to402)

        btnStop.text = getString(R.string.stop_session)

        resetDisplayValues()
        tvBigSpeed.text = "0"
        applyPrimaryDisplayState()

        btnStop.setOnClickListener {
            if (measurementMode == MeasurementMode.ALL && hasAllModeSuccessfulMetrics() && !waitingForFullStop) {
                finishSession()
            } else {
                showStopConfirmation()
            }
        }

        findViewById<View>(R.id.btnDragCamera)?.setOnClickListener {
            ensureDragSessionExists()
            publishDragVideoHud()
            startActivity(Intent(this, DragRunVideoActivity::class.java))
        }
    }

    private fun configureUIForMode() {
        singleModeContainer.visibility = View.GONE
        quarterHeaderContainer?.visibility = View.GONE
        quarterSectorsContainer.visibility = View.GONE
        allModeQuarterSectorsInAccel?.visibility = View.GONE
        llZeroTo200StageRowInAccel?.visibility = View.GONE
        allModeContainer.visibility = View.GONE

        val speedUnit = UnitsManager.getSpeedUnit(this)
        val speed100 = UnitsManager.convertSpeed(100f, speedUnit).toInt()
        val speed200 = UnitsManager.convertSpeed(200f, speedUnit).toInt()
        val speedSymbol = speedUnit.symbol

        when (measurementMode) {
            MeasurementMode.ALL -> {
                allModeContainer.visibility = View.VISIBLE
                allModeQuarterSectorsInAccel?.visibility = View.VISIBLE
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    quarterSectorsContainer.visibility = View.VISIBLE
                }
                llZeroTo200Splits.visibility = View.GONE
                tvStatus.text = getString(R.string.drag_waiting_for_acceleration)
            }
            MeasurementMode.ZERO_TO_100 -> {
                singleModeContainer.visibility = View.VISIBLE
                llZeroTo200Splits.visibility = View.VISIBLE
                tvSingleMetricLabel.text = formatDragSpeedRangeWithUnit(0, 100)
                tvStatus.text = getString(R.string.drag_status_ready_0to100)
            }
            MeasurementMode.ZERO_TO_200 -> {
                singleModeContainer.visibility = View.VISIBLE
                llZeroTo200Splits.visibility = View.VISIBLE
                tvSingleMetricLabel.text = formatDragSpeedRangeWithUnit(0, 200)
                tvStatus.text = getString(R.string.drag_status_ready_0to200)
            }
            MeasurementMode.HUNDRED_TO_200 -> {
                singleModeContainer.visibility = View.VISIBLE
                llZeroTo200Splits.visibility = View.VISIBLE
                tvSingleMetricLabel.text = formatDragSpeedRangeWithUnit(100, 200)
                tvStatus.text = getString(R.string.drag_status_ready_100to200)
            }
            MeasurementMode.QUARTER_MILE -> {
                quarterHeaderContainer?.visibility = View.VISIBLE
                quarterSectorsContainer.visibility = View.VISIBLE
                llZeroTo200Splits.visibility = View.GONE
                tvStatus.text = getString(R.string.drag_status_ready_quarter)
            }
        }

        val useCompactAccelPanel =
            measurementMode == MeasurementMode.QUARTER_MILE || measurementMode == MeasurementMode.ALL
        applyPortraitTimeCardsBalanceForMode(
            useFlexibleCards = measurementMode == MeasurementMode.ALL || measurementMode == MeasurementMode.QUARTER_MILE
        )
        applyAccelForceSizingForMode(useCompactAccelPanel)

        updateSingleModeMetricDisplay()
        updateZeroTo200SplitDisplay(0f)
        updateQuarterModeMetricDisplay()
        updateQuarterSectorDisplay()
    }

    private fun applyPortraitTimeCardsBalanceForMode(useFlexibleCards: Boolean) {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) return

        llTimeCards?.layoutParams = (llTimeCards?.layoutParams as? LinearLayout.LayoutParams)?.apply {
            height = if (useFlexibleCards) 0 else LinearLayout.LayoutParams.WRAP_CONTENT
            weight = if (useFlexibleCards) 1f else 0f
        }

        timeCardsFrame?.layoutParams = (timeCardsFrame?.layoutParams as? LinearLayout.LayoutParams)?.apply {
            height = if (useFlexibleCards) 0 else LinearLayout.LayoutParams.WRAP_CONTENT
            weight = if (useFlexibleCards) 1f else 0f
        }

        llTimeCards?.requestLayout()
        timeCardsFrame?.requestLayout()
    }

    private fun shouldUseLandscapeAccelSplitPills(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            measurementMode == MeasurementMode.ZERO_TO_200 &&
            llZeroTo200StageRowInAccel != null &&
            tvZeroTo200Stage0to100InAccel != null &&
            tvZeroTo200Stage100to200InAccel != null
    }

    private fun applyAccelForceSizingForMode(isQuarterMode: Boolean) {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (!isLandscape) {
            // Portrait sizing is controlled by XML to keep rendering consistent across real devices.
            return
        }

        val useCompact = isQuarterMode

        val panelPaddingDp = when {
            useCompact -> 4
            else -> 10
        }
        val currentValueSp = when {
            useCompact -> 22f
            else -> 36f
        }
        val labelSp = when {
            useCompact -> 9f
            else -> 11f
        }
        val tickSp = when {
            useCompact -> 8f
            else -> 10f
        }
        val peakSp = when {
            useCompact -> 8f
            else -> 10f
        }

        accelForcePanel?.setPadding(
            dpToPx(panelPaddingDp),
            dpToPx(panelPaddingDp),
            dpToPx(panelPaddingDp),
            dpToPx(panelPaddingDp)
        )
        tvAccelForceCurrent.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentValueSp)
        tvAccelForceLabel?.setTextSize(TypedValue.COMPLEX_UNIT_SP, labelSp)
        tvAccelTick05?.setTextSize(TypedValue.COMPLEX_UNIT_SP, tickSp)
        tvAccelScale075?.setTextSize(TypedValue.COMPLEX_UNIT_SP, tickSp)
        tvAccelScale10?.setTextSize(TypedValue.COMPLEX_UNIT_SP, tickSp)
        tvAccelScale125?.setTextSize(TypedValue.COMPLEX_UNIT_SP, tickSp)
        tvAccelTick15?.setTextSize(TypedValue.COMPLEX_UNIT_SP, tickSp)
        tvAccelPeakSummary.setTextSize(TypedValue.COMPLEX_UNIT_SP, peakSp)

        accelTrackContainer?.layoutParams = accelTrackContainer?.layoutParams?.apply {
            height = dpToPx(
                when {
                    useCompact -> 16
                    else -> 26
                }
            )
        }
        pbAccelForce.layoutParams = pbAccelForce.layoutParams.apply {
            height = dpToPx(
                when {
                    useCompact -> 8
                    else -> 14
                }
            )
        }
        vAccelMarker.layoutParams = vAccelMarker.layoutParams.apply {
            height = dpToPx(
                when {
                    useCompact -> 9
                    else -> 16
                }
            )
        }

        accelTrackContainer?.requestLayout()
        pbAccelForce.requestLayout()
        vAccelMarker.requestLayout()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun ensureDragSessionExists() {
        if (currentSession != null) return

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
            measurementMode = measurementMode.name,
            recordedWithRaceBox = isRaceBoxGpsStartActive()
        )
    }

    private fun createNewAttempt() {
        attemptAlreadySaved = false
        decelerationDetected = false
        waitingForFullStop = false
        liveAccelDisplaySamples.clear()
        liveAccelDisplayTimeStamps.clear()

        currentAttempt = DragAttempt(
            temperature = temperature,
            altitude = altitude,
            humidity = humidity,
            windKph = windKph,
            weatherIcon = weatherIcon
        )

        measuring = true
        started = false
        currentAttemptWasOfficiallyStarted = false
        resetEndOfRunWatch()
        resetRearmStopWatch()
        startLocation = null
        startTimeNano = 0L
        distanceCompleted = false
        measurementComplete = false
        rollingStartReady = false
        rolling100StartTime = 0L
        rolling100StartElapsedNs = 0L
        rollingCanCross100 = false
        launchSpeedConfirmed = false
        resetRaceBoxGpsStartState()
        phoneStationaryReady = false
        accumulatedDistance = 0f
        lastLocationForDistance = null
        lastLiveCompensationRefreshMs = 0L
        latestFusedSpeedSamplesKmh = emptyList()
        latestFusedSpeedTimeStampsNs = emptyList()

        measured0to100 = false
        measured0to200 = false
        measured100to200 = false
        measured0to402 = false
        
        // Reset sound flags
        sound100Played = false
        sound200Played = false
        sound402Played = false
        resetAccelerationForcePanel()

        attempt0to100Nanos = -1L
        attempt0to200Nanos = -1L
        attempt100to200Nanos = -1L
        attempt0to402Nanos = -1L
        timeAt100Nano = -1L
        resetQuarterSectorState()
        resetQuarterSectorDisplay()
        resetZeroTo200SplitDisplay()
        updateAllModeProgress(0f)

        resetDisplayValues()
        updateSingleModeMetricDisplay()

        updateAttemptNumber()
        publishDragVideoHud()

        // If already stopped, arm standing-start now so the next attempt can launch on gas.
        val loc = foregroundService?.getLastLocation()
        armRaceBoxStartIfAlreadyStationary(loc, loc?.speed?.times(3.6f) ?: lastSpeed)
    }

    private fun saveCurrentAttempt() {
        val gSamples = foregroundService?.getRecentGSamples() ?: emptyList()
        val gTimeStamps = foregroundService?.getRecentGTimeStamps() ?: emptyList()
        val gpsAccelSamples = foregroundService?.getRecentGpsAccelSamples() ?: emptyList()
        val gpsAccelTimeStamps = foregroundService?.getRecentGpsAccelTimeStamps() ?: emptyList()
        val longitudinalAccelSamples = foregroundService?.getRecentLongitudinalAccelSamples() ?: emptyList()
        val longitudinalAccelTimeStamps = foregroundService?.getRecentLongitudinalAccelTimeStamps() ?: emptyList()


        saveCurrentAttemptWithTimestamps(
            gSamples,
            gTimeStamps,
            gpsAccelSamples,
            gpsAccelTimeStamps,
            longitudinalAccelSamples,
            longitudinalAccelTimeStamps
        )
    }

    private fun saveCurrentAttemptWithTimestamps(
        gSamples: List<Float>,
        gTimeStamps: List<Long>,
        gpsAccelSamples: List<Float>,
        gpsAccelTimeStamps: List<Long>,
        longitudinalAccelSamples: List<Float>,
        longitudinalAccelTimeStamps: List<Long>
    ) {

        val updatedAttempt = buildCurrentAttemptSnapshotWithTimestamps(
            gSamples = gSamples,
            gTimeStamps = gTimeStamps,
            gpsAccelSamples = gpsAccelSamples,
            gpsAccelTimeStamps = gpsAccelTimeStamps,
            longitudinalAccelSamples = longitudinalAccelSamples,
            longitudinalAccelTimeStamps = longitudinalAccelTimeStamps
        ) ?: return

        upsertAttemptInCurrentSession(updatedAttempt)
        updateSessionBestTimes(updatedAttempt)
        persistCurrentSessionSnapshot()
        attemptAlreadySaved = true
        publishDragVideoHud()
    }

    private fun buildCurrentAttemptSnapshotWithTimestamps(
        gSamples: List<Float>,
        gTimeStamps: List<Long>,
        gpsAccelSamples: List<Float>,
        gpsAccelTimeStamps: List<Long>,
        longitudinalAccelSamples: List<Float>,
        longitudinalAccelTimeStamps: List<Long>
    ): DragAttempt? {
        if (!currentAttemptWasOfficiallyStarted) {
            return null
        }

        // Refresh compensated milestones only for officially started attempts.
        refreshLiveCompensatedMilestones(force = true)

        val speedSamplesRaw = foregroundService?.getRecentSpeedSamples() ?: emptyList()
        val speedTimeStampsRaw = foregroundService?.getRecentSpeedTimeStamps() ?: emptyList()
        val attempt = currentAttempt ?: return null

        val baseAttempt0to100Result = when (measurementMode) {
            MeasurementMode.ZERO_TO_100, MeasurementMode.ZERO_TO_200, MeasurementMode.ALL ->
                attempt0to100Nanos.takeIf { it > 0L } ?: -1L
            else -> -1L
        }

        val baseAttempt0to200Result = when (measurementMode) {
            MeasurementMode.ZERO_TO_200, MeasurementMode.ALL ->
                attempt0to200Nanos.takeIf { it > 0L } ?: -1L
            else -> -1L
        }

        val baseAttempt100to200Result = when (measurementMode) {
            MeasurementMode.HUNDRED_TO_200, MeasurementMode.ZERO_TO_200, MeasurementMode.ALL ->
                attempt100to200Nanos.takeIf { it > 0L } ?: -1L
            else -> -1L
        }

        val baseAttempt0to402Result = when (measurementMode) {
            MeasurementMode.QUARTER_MILE, MeasurementMode.ALL ->
                attempt0to402Nanos.takeIf { it > 0L } ?: -1L
            else -> -1L
        }

        val (windowStartNs, windowEndNs, speedCapKmh) = getMeasurementWindowAndSpeedCap(
            mode = measurementMode,
            attempt = attempt,
            attempt0to100Ns = baseAttempt0to100Result,
            attempt0to200Ns = baseAttempt0to200Result,
            attempt100to200Ns = baseAttempt100to200Result
        )

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

        val (alignedLongitudinalAccelSamples, alignedLongitudinalAccelTimes) =
            if (longitudinalAccelSamples.isNotEmpty() && longitudinalAccelTimeStamps.isNotEmpty()) {
                trimTimeSeriesToWindow(longitudinalAccelSamples, longitudinalAccelTimeStamps, windowStartNs, windowEndNs)
            } else {
                emptyList<Float>() to emptyList<Long>()
            }

        val (alignedLiveAccelDisplaySamples, alignedLiveAccelDisplayTimes) =
            if (liveAccelDisplaySamples.isNotEmpty() && liveAccelDisplayTimeStamps.isNotEmpty()) {
                trimTimeSeriesToWindow(liveAccelDisplaySamples, liveAccelDisplayTimeStamps, windowStartNs, windowEndNs)
            } else {
                emptyList<Float>() to emptyList<Long>()
            }

        val (trimmedSpeedSamplesRaw, trimmedSpeedTimes) = if (speedSamplesRaw.isNotEmpty() && speedTimeStampsRaw.isNotEmpty()) {
            trimTimeSeriesToWindow(speedSamplesRaw, speedTimeStampsRaw, windowStartNs, windowEndNs)
        } else {
            emptyList<Float>() to emptyList<Long>()
        }

        val (adjustedSpeedSamples, adjustedSpeedTimes) = ensureSpeedSeriesCoversMeasurementEnd(
            speedSamples = trimmedSpeedSamplesRaw,
            speedTimes = trimmedSpeedTimes,
            windowEndNs = windowEndNs,
            targetSpeedKmh = speedCapKmh
        )

        // Phone chart: GPS-heavy blend (not full fusion). RaceBox: GPS-only, filled from t0.
        // Milestone times still come from full fusion elsewhere — chart series is display-only.
        val (speedSamplesForSaveRaw, speedTimesForSaveRaw) =
            if (!isRaceBoxGpsStartActive() &&
                latestFusedSpeedSamplesKmh.size >= 2 &&
                latestFusedSpeedTimeStampsNs.size >= 2
            ) {
                latestFusedSpeedSamplesKmh to latestFusedSpeedTimeStampsNs
            } else {
                shiftSpeedSeriesByPhoneGpsLag(adjustedSpeedSamples, adjustedSpeedTimes)
            }
        val (speedSamplesForSave, speedTimesForSave) = ensureSpeedChartStartsAtLaunch(
            speedSamplesForSaveRaw,
            speedTimesForSaveRaw
        )

        val attempt0to100Result = baseAttempt0to100Result
        val attempt0to200Result = baseAttempt0to200Result
        val attempt100to200Result = when (measurementMode) {
            MeasurementMode.HUNDRED_TO_200 -> baseAttempt100to200Result
            MeasurementMode.ZERO_TO_200,
            MeasurementMode.ALL -> {
                if (baseAttempt100to200Result > 0L) {
                    baseAttempt100to200Result
                } else if (baseAttempt0to100Result > 0L && baseAttempt0to200Result > baseAttempt0to100Result) {
                    baseAttempt0to200Result - baseAttempt0to100Result
                } else {
                    -1L
                }
            }
            else -> -1L
        }

        val attempt0to402Result = when (measurementMode) {
            MeasurementMode.QUARTER_MILE,
            MeasurementMode.ALL -> baseAttempt0to402Result.takeIf { it > 0L } ?: (sector402TimeNanos.takeIf { it > 0L } ?: -1L)
            else -> -1L
        }

        val distance50mTimeNs = sector50TimeNanos.takeIf { it > 0L } ?: -1L
        val distance100mTimeNs = sector100TimeNanos.takeIf { it > 0L } ?: -1L
        val distance200mTimeNs = sector200TimeNanos.takeIf { it > 0L } ?: -1L
        val distance300mTimeNs = sector300TimeNanos.takeIf { it > 0L } ?: -1L
        val distance402mTimeNs = when {
            attempt0to402Result > 0L -> attempt0to402Result
            sector402TimeNanos > 0L -> sector402TimeNanos
            else -> -1L
        }

        val finalAttempt0to100Result = adjusted0to100Ns(attempt0to100Result)
        val finalAttempt0to200Result = adjusted0to200Ns(attempt0to200Result)
        val finalAttempt100to200Result = attempt100to200Result
        val finalAttempt0to402Result = adjusted0to402Ns(attempt0to402Result)

        val finalDistance50mTimeNs = adjusted50mNs(distance50mTimeNs)
        val finalDistance100mTimeNs = adjusted100mNs(distance100mTimeNs)
        val finalDistance200mTimeNs = adjusted200mNs(distance200mTimeNs)
        val finalDistance300mTimeNs = adjusted300mNs(distance300mTimeNs)
        val finalDistance402mTimeNs = if (finalAttempt0to402Result > 0L) {
            finalAttempt0to402Result
        } else {
            adjusted402mNs(distance402mTimeNs)
        }

        val distance50mSpeedKmh = if (sector50SpeedKmh >= 0f) sector50SpeedKmh else -1f
        val distance100mSpeedKmh = if (sector100SpeedKmh >= 0f) sector100SpeedKmh else -1f
        val distance200mSpeedKmh = if (sector200SpeedKmh >= 0f) sector200SpeedKmh else -1f
        val distance300mSpeedKmh = if (sector300SpeedKmh >= 0f) sector300SpeedKmh else -1f
        val distance402mSpeedKmh = if (sector402SpeedKmh >= 0f) sector402SpeedKmh else -1f

        val computedMaxSpeed = speedSamplesForSave.maxOrNull()
            ?: foregroundService?.getMaxSpeed()
            ?: 0f

        val measurementDuration = listOf(
            finalAttempt0to100Result,
            finalAttempt0to200Result,
            finalAttempt100to200Result,
            finalAttempt0to402Result
        ).filter { it > 0 }.maxOrNull() ?: 0L

        val updatedAttempt = attempt.copy(
            time0to100 = finalAttempt0to100Result,
            time0to200 = finalAttempt0to200Result,
            time100to200 = finalAttempt100to200Result,
            time0to402 = finalAttempt0to402Result,
            maxSpeed = computedMaxSpeed,
            gSamples = alignedGSamples,
            gpsAccelSamples = alignedGpsAccelSamples,
            longitudinalAccelSamples = alignedLongitudinalAccelSamples,
            liveAccelDisplaySamples = alignedLiveAccelDisplaySamples,
            startTime = attempt.startTime,
            timeStamps = alignedGTimes,
            gpsTimeStamps = alignedGpsAccelTimes,
            longitudinalAccelTimeStamps = alignedLongitudinalAccelTimes,
            liveAccelDisplayTimeStamps = alignedLiveAccelDisplayTimes,
            duration = measurementDuration,
            speedSamples = speedSamplesForSave,
            speedTimeStamps = speedTimesForSave,
            distance50mTimeNs = finalDistance50mTimeNs,
            distance100mTimeNs = finalDistance100mTimeNs,
            distance200mTimeNs = finalDistance200mTimeNs,
            distance300mTimeNs = finalDistance300mTimeNs,
            distance402mTimeNs = finalDistance402mTimeNs,
            distance50mSpeedKmh = distance50mSpeedKmh,
            distance100mSpeedKmh = distance100mSpeedKmh,
            distance200mSpeedKmh = distance200mSpeedKmh,
            distance300mSpeedKmh = distance300mSpeedKmh,
            distance402mSpeedKmh = distance402mSpeedKmh,
            peakLongitudinalG = peakAccelForceG.takeIf { it > 0f } ?: 0f
        )

        val hasValidMeasurement = DragAttemptMetrics.hasCompletedMeasurement(updatedAttempt)

        return updatedAttempt.takeIf { hasValidMeasurement }
    }

    private fun stashCurrentAllModePartialAttemptIfNeeded() {
        if (measurementMode != MeasurementMode.ALL || attemptAlreadySaved || !currentAttemptWasOfficiallyStarted) return

        val pendingAttempt = buildCurrentAttemptSnapshotWithTimestamps(
            gSamples = foregroundService?.getRecentGSamples() ?: emptyList(),
            gTimeStamps = foregroundService?.getRecentGTimeStamps() ?: emptyList(),
            gpsAccelSamples = foregroundService?.getRecentGpsAccelSamples() ?: emptyList(),
            gpsAccelTimeStamps = foregroundService?.getRecentGpsAccelTimeStamps() ?: emptyList(),
            longitudinalAccelSamples = foregroundService?.getRecentLongitudinalAccelSamples() ?: emptyList(),
            longitudinalAccelTimeStamps = foregroundService?.getRecentLongitudinalAccelTimeStamps() ?: emptyList()
        ) ?: return

        val hasAllMeasurements = pendingAttempt.time0to100 > 0 &&
            pendingAttempt.time0to200 > 0 &&
            pendingAttempt.time100to200 > 0 &&
            pendingAttempt.time0to402 > 0
        if (hasAllMeasurements) return

        val existingIndex = pendingAllModePartialAttempts.indexOfFirst { it.id == pendingAttempt.id }
        if (existingIndex >= 0) {
            pendingAllModePartialAttempts[existingIndex] = pendingAttempt
        } else {
            pendingAllModePartialAttempts.add(pendingAttempt)
        }
    }

    private fun mergePendingAllModePartialAttemptsIntoCurrentSession() {
        if (pendingAllModePartialAttempts.isEmpty()) return

        pendingAllModePartialAttempts.forEach { attempt ->
            upsertAttemptInCurrentSession(attempt)
            updateSessionBestTimes(attempt)
        }
        currentSession?.attempts?.sortBy { it.timestamp }
        pendingAllModePartialAttempts.clear()
    }

    private fun upsertAttemptInCurrentSession(updatedAttempt: DragAttempt) {
        if (!DragAttemptMetrics.hasCompletedMeasurement(updatedAttempt)) return
        ensureDragSessionExists()
        val attempts = currentSession?.attempts ?: return
        val existingIndex = attempts.indexOfFirst { it.id == updatedAttempt.id }
        if (existingIndex >= 0) {
            attempts[existingIndex] = updatedAttempt
        } else {
            attempts.add(updatedAttempt)
        }
    }

    private fun hasPersistableAttempts(session: DragSession): Boolean {
        return session.attempts.any { DragAttemptMetrics.hasCompletedMeasurement(it) }
    }

    private fun publishDragVideoHud(
        sessionActive: Boolean = measuring || measurementStarted,
        speedKmh: Float = lastSpeed
    ) {
        val time100 = attempt0to100Nanos.takeIf { it > 0L }?.let { adjusted0to100Ns(it) } ?: -1L
        val time200 = attempt0to200Nanos.takeIf { it > 0L }?.let { adjusted0to200Ns(it) } ?: -1L
        val time100to200 = attempt100to200Nanos.takeIf { it > 0L } ?: -1L
        val time402 = attempt0to402Nanos.takeIf { it > 0L }?.let { adjusted0to402Ns(it) } ?: -1L
        val startNs = when {
            !currentAttemptWasOfficiallyStarted -> 0L
            measurementMode == MeasurementMode.HUNDRED_TO_200 && rolling100StartTime > 0L -> rolling100StartTime
            startTimeNano > 0L -> startTimeNano
            else -> foregroundService?.getMeasurementStartTimeNano() ?: 0L
        }
        val chronoNs = if (currentAttemptWasOfficiallyStarted && startNs > 0L) {
            (System.nanoTime() - startNs).coerceAtLeast(0L)
        } else {
            -1L
        }
        DragRunVideoBridge.publish(
            DragRunVideoHudState(
                sessionActive = sessionActive,
                sessionId = currentSession?.id ?: 0L,
                attemptId = currentAttempt?.id ?: 0L,
                measurementMode = measurementMode,
                speedKmh = speedKmh,
                time0to100Ns = time100,
                time0to200Ns = time200,
                time100to200Ns = time100to200,
                time0to402Ns = time402,
                officiallyStarted = currentAttemptWasOfficiallyStarted,
                attemptSaved = attemptAlreadySaved,
                statusText = if (::tvStatus.isInitialized) tvStatus.text?.toString().orEmpty() else "",
                accelG = displayedAccelForceG,
                peakAccelG = peakAccelForceG,
                chronoNs = chronoNs
            )
        )
    }

    private fun attachVideoToAttempt(sessionId: Long, attemptId: Long, path: String, t0OffsetMs: Long = 0L) {
        if (attemptId <= 0L || path.isBlank()) return
        val offset = t0OffsetMs.coerceAtLeast(0L)
        if (currentAttempt?.id == attemptId) {
            currentAttempt = currentAttempt?.copy(videoPath = path, videoT0OffsetMs = offset)
        }
        val session = currentSession
        if (session != null) {
            val index = session.attempts.indexOfFirst { it.id == attemptId }
            if (index >= 0) {
                session.attempts[index] = session.attempts[index].copy(videoPath = path, videoT0OffsetMs = offset)
            }
            persistCurrentSessionSnapshot()
        }
        val persistSessionId = session?.id?.takeIf { it > 0L } ?: sessionId
        if (persistSessionId > 0L) {
            dragPersistExecutor.execute {
                DragStorage.attachAttemptVideo(applicationContext, persistSessionId, attemptId, path, offset)
            }
        }
    }

    private fun persistCurrentSessionSnapshot(notify: Boolean = false): Boolean {
        val session = currentSession ?: return false
        session.updateBestTimes()

        if (!hasPersistableAttempts(session)) {
            return false
        }

        val appContext = applicationContext
        val snapshot = session.copy(
            attempts = session.attempts.map { it.copy() }.toMutableList()
        )

        val writer = Callable {
            try {
                DragStorage.addDragSession(appContext, snapshot)
                true
            } catch (t: Throwable) {
                Log.e("DragRunPageActivity", "Failed to persist drag session", t)
                false
            }
        }

        val saved = try {
            if (notify) {
                // Must finish writing before opening details / leaving the run screen.
                dragPersistExecutor.submit(writer).get(45, TimeUnit.SECONDS)
            } else {
                dragPersistExecutor.execute {
                    writer.call()
                }
                true
            }
        } catch (t: Throwable) {
            Log.e("DragRunPageActivity", "Persist executor failed — trying sync fallback", t)
            try {
                writer.call()
            } catch (t2: Throwable) {
                Log.e("DragRunPageActivity", "Sync persist fallback failed", t2)
                false
            }
        }

        if (saved && notify) {
            sendBroadcast(Intent("SESSION_UPDATED").apply {
                putExtra("SESSION_ID", snapshot.id)
            })
            setResult(Activity.RESULT_OK)
        }

        return saved
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
            getString(R.string.drag_run_best_multiline_format, current, formatNanos(best))
        } else {
            current
        }
    }

    private fun formatSessionBestDisplay(bestNs: Long): String {
        return getString(R.string.drag_run_best_prefix, formatNanos(bestNs))
    }

    private fun resetDisplayValues() {
        if (measurementMode != MeasurementMode.ALL) {
            // Показваме текущите резултати ако има такива, иначе session best
            tvCard0to100.text = if (attempt0to100Nanos > 0) {
                displayTimeWithBest(
                    formatNanos(adjusted0to100Ns(attempt0to100Nanos)),
                    sessionBest0to100
                )
            } else if (sessionBest0to100 > 0) {
                formatSessionBestDisplay(sessionBest0to100)
            } else {
                "--"
            }
            
            tvCard0to200.text = if (attempt0to200Nanos > 0) {
                displayTimeWithBest(
                    formatNanos(adjusted0to200Ns(attempt0to200Nanos)),
                    sessionBest0to200
                )
            } else if (sessionBest0to200 > 0) {
                formatSessionBestDisplay(sessionBest0to200)
            } else {
                "--"
            }
            
            tvCard100to200.text = if (attempt100to200Nanos > 0) {
                displayTimeWithBest(formatNanos(attempt100to200Nanos), sessionBest100to200)
            } else if (sessionBest100to200 > 0) {
                formatSessionBestDisplay(sessionBest100to200)
            } else {
                "--"
            }
            
            tvCard0to402.text = if (attempt0to402Nanos > 0) {
                displayTimeWithBest(
                    formatNanos(adjusted0to402Ns(attempt0to402Nanos)),
                    sessionBest0to402
                )
            } else if (sessionBest0to402 > 0) {
                formatSessionBestDisplay(sessionBest0to402)
            } else {
                "--"
            }
        } else {
            tvCard0to100.text = "--.--"
            tvCard0to200.text = "--.--"
            tvCard100to200.text = "--.--"
            tvCard0to402.text = "--.--"
        }
        val speedUnit = UnitsManager.getSpeedUnit(this)
        if (speedUnit == UnitsManager.SpeedUnit.MPH) {
            tvCard0to402Distance.text = getString(R.string.drag_distance_zero_mi)
        } else {
            tvCard0to402Distance.text = getString(R.string.drag_distance_zero_m)
        }
        tvCard0to402Distance.visibility = if (measurementMode == MeasurementMode.ALL) View.VISIBLE else View.GONE

        lastDisplayedConvertedSpeed = 0f
        lastDisplayedG = 0f
        isShowingGForceInsteadOfSpeed = false
        applyPrimaryDisplayState()
        updateSingleModeMetricDisplay()
        updateZeroTo200SplitDisplay(0f)
        updateQuarterModeMetricDisplay()
        updateQuarterSectorDisplay()
        updateAllModeProgress(0f)
    }

    private fun updateQuarterModeMetricDisplay() {
        if (!::tvQuarterMetricValue.isInitialized || measurementMode != MeasurementMode.QUARTER_MILE) {
            return
        }

        val quarterLabel = UnitsManager.getQuarterMileDistance(this)
        tvQuarterMetricLabel.text = formatDragQuarterRangeLabel()
        tvQuarterProgressMin.text = "0"
        tvQuarterProgressMax.text = quarterLabel

        val nowNano = System.nanoTime()
        val measurementStartTimeNano = foregroundService?.getMeasurementStartTimeNano() ?: 0L
        val valueText = when {
            attempt0to402Nanos > 0L -> formatSecondsValue(adjusted0to402Ns(attempt0to402Nanos))
            started && measurementStartTimeNano > 0L -> formatSecondsValue(nowNano - measurementStartTimeNano)
            else -> "--.--"
        }
        tvQuarterMetricValue.text = valueText

        val isActiveMeasurement = started && !measurementComplete && !waitingForFullStop
        val progress = when {
            measured0to402 -> 100
            !isActiveMeasurement -> 0
            else -> ((accumulatedDistance / TARGET_METERS) * 100f).toInt().coerceIn(0, 100)
        }
        pbQuarterProgress.progress = progress
    }

    private fun syncAllModeCanonicalCards() {
        if (measurementMode != MeasurementMode.ALL) return

        if (attempt0to100Nanos > 0L) {
            tvCard0to100.text = formatNanos(adjusted0to100Ns(attempt0to100Nanos))
        }
        if (attempt0to200Nanos > 0L) {
            tvCard0to200.text = formatNanos(adjusted0to200Ns(attempt0to200Nanos))
        }
        if (attempt100to200Nanos > 0L) {
            tvCard100to200.text = formatNanos(attempt100to200Nanos)
        }
        if (attempt0to402Nanos > 0L) {
            tvCard0to402.text = formatNanos(adjusted0to402Ns(attempt0to402Nanos))
        }
    }

    private fun applyPrimaryDisplayState() {
        tvBigSpeed.visibility = View.VISIBLE
        tvSpeedUnit?.visibility = View.VISIBLE
        gContainer.visibility = View.GONE
        tvGCurrentBig.visibility = View.GONE
        tvBigSpeed.text = lastDisplayedConvertedSpeed.toInt().toString()
        tvGCurrentBig.text = String.format("%.2f g", lastDisplayedG)
    }

    private fun resetAccelerationForcePanel() {
        displayedAccelForceG = 0f
        peakAccelForceG = 0f
        lastAccelTextValue = Float.NaN
        lastAccelPeakTextValue = Float.NaN
        lastAccelProgressValue = -1
        updateAccelerationForcePanel(0f, 0f)
    }

    private fun startAccelerationPanelLoop() {
        if (accelPanelLoopActive) return
        accelPanelLoopActive = true
        accelPanelHandler.removeCallbacks(accelPanelRunnable)
        accelPanelHandler.post(accelPanelRunnable)
    }

    private fun stopAccelerationPanelLoop() {
        accelPanelLoopActive = false
        accelPanelHandler.removeCallbacks(accelPanelRunnable)
    }

    private fun sampleAndRenderAccelerationPanel() {
        val svc = foregroundService ?: return
        // currentGForceY is inertial-directional on some devices; invert so panel tracks forward acceleration.
        val rawAccelerationOnlyG = (-svc.getCurrentGForceY()).coerceAtLeast(0f)
        val target = rawAccelerationOnlyG.coerceIn(0f, ACCEL_FORCE_MAX_G * 1.5f)

        val followAlpha = if (dragIsMotorcycle) 0.14f else 0.20f
        displayedAccelForceG += (target - displayedAccelForceG) * followAlpha
        if (kotlin.math.abs(target - displayedAccelForceG) < 0.002f) {
            displayedAccelForceG = target
        }
        if (target > peakAccelForceG) {
            peakAccelForceG = target
        }

        recordLiveAccelDisplaySample(displayedAccelForceG)

        updateAccelerationForcePanel(displayedAccelForceG, peakAccelForceG)
        publishDragVideoHud()
    }

    private fun recordLiveAccelDisplaySample(displayedAccelG: Float) {
        val svc = foregroundService ?: return
        // Persist ACCEL chart series only for the actual run window, not arming/wait time.
        if (!started) return
        if (!svc.isSessionActive()) return

        val measurementStartNs = svc.getMeasurementStartTimeNano()
        if (measurementStartNs <= 0L) return

        var relativeTimeNs = (System.nanoTime() - measurementStartNs).coerceAtLeast(0L)
        if (liveAccelDisplayTimeStamps.isNotEmpty() && relativeTimeNs <= liveAccelDisplayTimeStamps.last()) {
            relativeTimeNs = liveAccelDisplayTimeStamps.last() + 1L
        }

        liveAccelDisplaySamples.add(displayedAccelG.coerceAtLeast(0f))
        liveAccelDisplayTimeStamps.add(relativeTimeNs)

        if (liveAccelDisplaySamples.size > LIVE_ACCEL_DISPLAY_CAPACITY) {
            liveAccelDisplaySamples.removeAt(0)
            liveAccelDisplayTimeStamps.removeAt(0)
        }
    }

    private fun updateAccelerationForcePanel(currentAccelG: Float, peakAccelG: Float) {
        if (!::tvAccelForceCurrent.isInitialized) return

        val currentClamped = currentAccelG.coerceIn(0f, ACCEL_FORCE_MAX_G * 1.5f)
        val peakClamped = peakAccelG.coerceAtLeast(0f)

        if (lastAccelTextValue.isNaN() || kotlin.math.abs(currentClamped - lastAccelTextValue) >= 0.01f) {
            tvAccelForceCurrent.text = String.format(Locale.US, "%.2f", currentClamped)
            lastAccelTextValue = currentClamped
        }
        if (lastAccelPeakTextValue.isNaN() || kotlin.math.abs(peakClamped - lastAccelPeakTextValue) >= 0.01f) {
            tvAccelPeakSummary.text = getString(R.string.drag_accel_peak_summary_format, peakClamped)
            lastAccelPeakTextValue = peakClamped
        }

        val normalized = (currentClamped / ACCEL_FORCE_MAX_G).coerceIn(0f, 1f)
        val progressValue = (normalized * 1000f).toInt()
        if (progressValue != lastAccelProgressValue) {
            pbAccelForce.progress = progressValue
            lastAccelProgressValue = progressValue
        }

        val availableWidth = (pbAccelForce.width - vAccelMarker.width).coerceAtLeast(0)
        if (availableWidth > 0) {
            vAccelMarker.translationX = normalized * availableWidth
        }
    }

    private fun updateAttemptNumber() {
        updateAttemptIndicator()
        if (measurementMode == MeasurementMode.ALL ||
            measurementMode == MeasurementMode.HUNDRED_TO_200
        ) return
        tvStatus.text = getString(R.string.drag_attempt_number, getCurrentAttemptNumber())
    }

    private fun updateAttemptIndicator() {
        if (!::tvAttemptValue.isInitialized) return
        tvAttemptValue.text = getCurrentAttemptNumber().toString()
    }

    private fun updateWeatherSummaryDisplay() {
        if (!::ivWeatherCondition.isInitialized || !::tvWeatherTemp.isInitialized || !::tvWeatherHumidity.isInitialized || !::tvWeatherWind.isInitialized) {
            return
        }

        val (weatherIconRes, weatherTintRes) = resolveWeatherIconStyle(weatherIcon ?: -1, humidity)
        ivWeatherCondition.setImageResource(weatherIconRes)
        ivWeatherCondition.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, weatherTintRes))

        tvWeatherTemp.text = temperature?.let {
            UnitsManager.formatTemperature(it, this, decimals = 0)
        } ?: getString(R.string.drag_weather_temp_placeholder)

        tvWeatherHumidity.text = humidity?.let { "$it%" }
            ?: getString(R.string.drag_weather_humidity_placeholder)

        val speedUnit = UnitsManager.getSpeedUnit(this)
        tvWeatherWind.text = windKph?.let {
            val converted = UnitsManager.convertSpeed(it, speedUnit)
            "${converted.toInt()} ${speedUnit.symbol}"
        } ?: getString(R.string.drag_weather_wind_placeholder)
    }

    private fun resolveWeatherIconStyle(iconRes: Int, humidityPercent: Int?): Pair<Int, Int> {
        val baseIcon = when (iconRes) {
            R.drawable.ic_weather_sunny -> R.drawable.ic_weather_sunny
            R.drawable.ic_weather_clear_night -> R.drawable.ic_weather_clear_night
            R.drawable.ic_weather_partly_cloudy,
            R.drawable.ic_weather_partly_cloudy_night -> R.drawable.ic_weather_partly_cloudy
            R.drawable.ic_weather_cloudy -> R.drawable.ic_weather_cloudy
            R.drawable.ic_weather_rainy -> R.drawable.ic_weather_rainy
            R.drawable.ic_weather_snowy -> R.drawable.ic_weather_snowy
            else -> R.drawable.ic_weather_cloudy
        }

        val finalIcon = if (baseIcon == R.drawable.ic_weather_sunny && (humidityPercent ?: 0) >= 70) {
            R.drawable.ic_weather_cloudy
        } else {
            baseIcon
        }

        val tintRes = when (finalIcon) {
            R.drawable.ic_weather_sunny -> R.color.warning_color
            R.drawable.ic_weather_rainy,
            R.drawable.ic_weather_snowy -> R.color.accent_light
            R.drawable.ic_weather_clear_night,
            R.drawable.ic_weather_cloudy,
            R.drawable.ic_weather_partly_cloudy -> R.color.text_tertiary
            else -> R.color.text_tertiary
        }

        return finalIcon to tintRes
    }

    private fun formatSecondsValue(nanos: Long): String {
        if (nanos <= 0L) return "--.--"
        return String.format("%.2f", nanos / 1_000_000_000.0)
    }

    private fun formatDragSpeedRangeWithUnit(fromKmh: Int, toKmh: Int): String {
        val unit = UnitsManager.getSpeedUnit(this).symbol
        return "${UnitsManager.formatDragSpeedIntervalLabel(fromKmh, toKmh, this)} $unit"
    }

    private fun formatDragSpeedRangeWithColon(fromKmh: Int, toKmh: Int): String {
        return "${UnitsManager.formatDragSpeedIntervalLabel(fromKmh, toKmh, this)}:"
    }

    private fun formatDragQuarterRangeLabel(): String {
        return UnitsManager.formatDragZeroTo402IntervalLabel(this)
    }

    private fun updateSingleModeMetricDisplay() {
        if (!::tvSingleMetricValue.isInitialized || measurementMode == MeasurementMode.ALL || measurementMode == MeasurementMode.QUARTER_MILE) {
            return
        }

        val nowNano = System.nanoTime()
        val measurementStartTimeNano = foregroundService?.getMeasurementStartTimeNano() ?: 0L
        val valueText = when (measurementMode) {
            MeasurementMode.ZERO_TO_100 -> {
                when {
                    attempt0to100Nanos > 0L -> formatSecondsValue(adjusted0to100Ns(attempt0to100Nanos))
                    started && measurementStartTimeNano > 0L -> formatSecondsValue(nowNano - measurementStartTimeNano)
                    else -> "--.--"
                }
            }
            MeasurementMode.ZERO_TO_200 -> {
                when {
                    attempt0to200Nanos > 0L -> formatSecondsValue(adjusted0to200Ns(attempt0to200Nanos))
                    started && measurementStartTimeNano > 0L -> formatSecondsValue(nowNano - measurementStartTimeNano)
                    else -> "--.--"
                }
            }
            MeasurementMode.HUNDRED_TO_200 -> {
                when {
                    attempt100to200Nanos > 0L -> formatSecondsValue(attempt100to200Nanos)
                    started && rolling100StartTime > 0L -> formatSecondsValue(nowNano - rolling100StartTime)
                    else -> "--.--"
                }
            }
            else -> "--.--"
        }

        tvSingleMetricValue.text = valueText
    }

    private fun resetZeroTo200SplitDisplay() {
        if (!::pbZeroTo200Progress.isInitialized) return
        pbZeroTo200Progress.progress = 0

        val useLandscapeAccelPills = shouldUseLandscapeAccelSplitPills()
        llZeroTo200StageRow.visibility = if (measurementMode == MeasurementMode.ZERO_TO_200 && !useLandscapeAccelPills) View.VISIBLE else View.GONE
        llZeroTo200StageRowInAccel?.visibility = if (measurementMode == MeasurementMode.ZERO_TO_200 && useLandscapeAccelPills) View.VISIBLE else View.GONE

        val stage0to100View = if (useLandscapeAccelPills) {
            tvZeroTo200Stage0to100InAccel ?: tvZeroTo200Stage0to100
        } else {
            tvZeroTo200Stage0to100
        }
        val stage100to200View = if (useLandscapeAccelPills) {
            tvZeroTo200Stage100to200InAccel ?: tvZeroTo200Stage100to200
        } else {
            tvZeroTo200Stage100to200
        }

        val speedUnit = UnitsManager.getSpeedUnit(this)
        val speed100 = UnitsManager.convertSpeed(100f, speedUnit).toInt()
        val speed200 = UnitsManager.convertSpeed(200f, speedUnit).toInt()

        when (measurementMode) {
            MeasurementMode.ZERO_TO_100 -> {
                tvSingleModeMinSpeed.text = "0"
                tvZeroTo200MaxSpeed.text = speed100.toString()
            }
            MeasurementMode.HUNDRED_TO_200 -> {
                tvSingleModeMinSpeed.text = speed100.toString()
                tvZeroTo200MaxSpeed.text = speed200.toString()
            }
            else -> {
                tvSingleModeMinSpeed.text = "0"
                tvZeroTo200MaxSpeed.text = speed200.toString()
            }
        }

        stage0to100View.text = formatDragSpeedRangeWithColon(0, 100)
        stage100to200View.text = formatDragSpeedRangeWithColon(100, 200)
        stage0to100View.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
        stage100to200View.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
        stage0to100View.setBackgroundResource(R.drawable.bg_drag_split_chip_inactive)
        stage100to200View.setBackgroundResource(R.drawable.bg_drag_split_chip_inactive)
    }

    private fun updateZeroTo200SplitDisplay(currentSpeedKmh: Float) {
        if (!::llZeroTo200Splits.isInitialized) return
        if (measurementMode != MeasurementMode.ZERO_TO_100 && measurementMode != MeasurementMode.ZERO_TO_200 && measurementMode != MeasurementMode.HUNDRED_TO_200) return

        // Keep the progress row explicitly visible in all single speed modes.
        llZeroTo200Splits.visibility = View.VISIBLE

        val isActiveMeasurement = started && !measurementComplete && !waitingForFullStop
        val speedUnit = UnitsManager.getSpeedUnit(this)
        val speed100 = UnitsManager.convertSpeed(100f, speedUnit).toInt()
        val speed200 = UnitsManager.convertSpeed(200f, speedUnit).toInt()

        tvSingleModeMinSpeed.text = if (measurementMode == MeasurementMode.HUNDRED_TO_200) speed100.toString() else "0"
        tvZeroTo200MaxSpeed.text = when (measurementMode) {
            MeasurementMode.ZERO_TO_100 -> speed100.toString()
            else -> speed200.toString()
        }

        val progress = when (measurementMode) {
            MeasurementMode.ZERO_TO_100 -> {
                when {
                    measured0to100 -> 100
                    !isActiveMeasurement -> 0
                    else -> ((currentSpeedKmh / 100f) * 100f).toInt().coerceIn(0, 100)
                }
            }
            MeasurementMode.HUNDRED_TO_200 -> {
                when {
                    measured100to200 -> 100
                    !isActiveMeasurement -> 0
                    else -> (((currentSpeedKmh - 100f) / 100f) * 100f).toInt().coerceIn(0, 100)
                }
            }
            else -> {
                when {
                    measured0to200 -> 100
                    !isActiveMeasurement -> 0
                    else -> ((currentSpeedKmh / 200f) * 100f).toInt().coerceIn(0, 100)
                }
            }
        }
        pbZeroTo200Progress.progress = progress

        val useLandscapeAccelPills = shouldUseLandscapeAccelSplitPills()
        val stage0to100View = if (useLandscapeAccelPills) {
            tvZeroTo200Stage0to100InAccel ?: tvZeroTo200Stage0to100
        } else {
            tvZeroTo200Stage0to100
        }
        val stage100to200View = if (useLandscapeAccelPills) {
            tvZeroTo200Stage100to200InAccel ?: tvZeroTo200Stage100to200
        } else {
            tvZeroTo200Stage100to200
        }

        if (measurementMode != MeasurementMode.ZERO_TO_200) {
            llZeroTo200StageRow.visibility = View.GONE
            llZeroTo200StageRowInAccel?.visibility = View.GONE
            return
        }

        llZeroTo200StageRow.visibility = if (useLandscapeAccelPills) View.GONE else View.VISIBLE
        llZeroTo200StageRowInAccel?.visibility = if (useLandscapeAccelPills) View.VISIBLE else View.GONE

        val has0to100 = attempt0to100Nanos > 0L
        val has100to200 = attempt100to200Nanos > 0L
        val is0to100Running = isActiveMeasurement && !has0to100
        val is100to200Running = isActiveMeasurement && has0to100 && !has100to200

        if (has0to100) {
            stage0to100View.text = getString(
                R.string.drag_stage_time_format,
                formatDragSpeedRangeWithColon(0, 100),
                formatSecondsValue(adjusted0to100Ns(attempt0to100Nanos))
            )
            stage0to100View.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            stage0to100View.setBackgroundResource(R.drawable.bg_drag_split_chip_0to100_active)
        } else if (is0to100Running) {
            stage0to100View.text = getString(
                R.string.drag_stage_running_label_format,
                formatDragSpeedRangeWithColon(0, 100),
                getString(R.string.drag_stage_running)
            )
            stage0to100View.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            stage0to100View.setBackgroundResource(R.drawable.bg_drag_split_chip_inactive)
        } else {
            stage0to100View.text = formatDragSpeedRangeWithColon(0, 100)
            stage0to100View.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            stage0to100View.setBackgroundResource(R.drawable.bg_drag_split_chip_inactive)
        }

        if (has100to200) {
            stage100to200View.text = getString(
                R.string.drag_stage_time_format,
                formatDragSpeedRangeWithColon(100, 200),
                formatSecondsValue(attempt100to200Nanos)
            )
            stage100to200View.setTextColor(ContextCompat.getColor(this, R.color.accent_purple))
            stage100to200View.setBackgroundResource(R.drawable.bg_drag_split_chip_100to200_active)
        } else if (is100to200Running) {
            stage100to200View.text = getString(
                R.string.drag_stage_running_label_format,
                formatDragSpeedRangeWithColon(100, 200),
                getString(R.string.drag_stage_running)
            )
            stage100to200View.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            stage100to200View.setBackgroundResource(R.drawable.bg_drag_split_chip_inactive)
        } else {
            stage100to200View.text = formatDragSpeedRangeWithColon(100, 200)
            stage100to200View.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            stage100to200View.setBackgroundResource(R.drawable.bg_drag_split_chip_inactive)
        }
    }

    private fun resetQuarterSectorState() {
        sector50TimeNanos = -1L
        sector100TimeNanos = -1L
        sector200TimeNanos = -1L
        sector300TimeNanos = -1L
        sector402TimeNanos = -1L
        sector50SpeedKmh = -1f
        sector100SpeedKmh = -1f
        sector200SpeedKmh = -1f
        sector300SpeedKmh = -1f
        sector402SpeedKmh = -1f
        lastQuarterDistanceElapsedNanos = -1L
        lastQuarterSpeedKmh = -1f
    }

    private fun resetQuarterSectorDisplay() {
        if (!::tvSector50Time.isInitialized) return
        tvSector50Time.text = "--.--"
        tvSector100Time.text = "--.--"
        tvSector200Time.text = "--.--"
        tvSector300Time.text = "--.--"
        tvSector402Time.text = "--.--"
        tvSector50Speed.text = "--"
        tvSector100Speed.text = "--"
        tvSector200Speed.text = "--"
        tvSector300Speed.text = "--"
        tvSector402Speed.text = "--"
        tvAllModeSector50Time?.text = "--.--"
        tvAllModeSector100Time?.text = "--.--"
        tvAllModeSector200Time?.text = "--.--"
        tvAllModeSector300Time?.text = "--.--"
        tvAllModeSector402Time?.text = "--.--"
        tvAllModeSector50Speed?.text = "--"
        tvAllModeSector100Speed?.text = "--"
        tvAllModeSector200Speed?.text = "--"
        tvAllModeSector300Speed?.text = "--"
        tvAllModeSector402Speed?.text = "--"
    }

    private fun formatSpeedForDisplay(speedKmh: Float): String {
        if (speedKmh < 0f) return "--"
        val speedUnit = UnitsManager.getSpeedUnit(this)
        val converted = UnitsManager.convertSpeed(speedKmh, speedUnit).toInt()
        return "$converted ${speedUnit.symbol}"
    }

    private fun distanceIncrementFromSpeed(
        prevElapsedNs: Long,
        currentElapsedNs: Long,
        prevSpeedKmh: Float,
        currentSpeedKmh: Float
    ): Float {
        val dtNs = currentElapsedNs - prevElapsedNs
        if (dtNs <= 0L) return 0f
        val dtSec = dtNs / 1_000_000_000f
        val prevMps = prevSpeedKmh.coerceAtLeast(0f) * KMH_TO_MPS.toFloat()
        val currMps = currentSpeedKmh.coerceAtLeast(0f) * KMH_TO_MPS.toFloat()
        return ((prevMps + currMps) * 0.5f * dtSec).coerceAtLeast(0f)
    }

    private fun shouldApplyPhoneGpsLagAlign(): Boolean {
        return measurementMode != MeasurementMode.HUNDRED_TO_200 &&
            !isRaceBoxGpsStartActive()
    }

    private fun applyFinalTimeOffsetNs(rawTimeNs: Long, @Suppress("UNUSED_PARAMETER") offsetNs: Long): Long {
        if (rawTimeNs <= 0L || !shouldApplyPhoneGpsLagAlign()) return rawTimeNs
        return (rawTimeNs - PHONE_GPS_LAG_ALIGN_NS).coerceAtLeast(1L)
    }

    private fun shiftSpeedSeriesByPhoneGpsLag(
        speeds: List<Float>,
        times: List<Long>
    ): Pair<List<Float>, List<Long>> {
        if (!shouldApplyPhoneGpsLagAlign() || speeds.isEmpty() || times.isEmpty()) {
            return speeds to times
        }
        val shiftedTimes = times.map { timeNs ->
            if (timeNs <= 0L) timeNs else (timeNs - PHONE_GPS_LAG_ALIGN_NS).coerceAtLeast(1L)
        }
        return speeds to shiftedTimes
    }

    private fun adjusted0to100Ns(rawTimeNs: Long): Long = applyFinalTimeOffsetNs(rawTimeNs, 0L)
    private fun adjusted0to200Ns(rawTimeNs: Long): Long = applyFinalTimeOffsetNs(rawTimeNs, 0L)
    private fun adjusted0to402Ns(rawTimeNs: Long): Long = applyFinalTimeOffsetNs(rawTimeNs, 0L)
    private fun adjusted50mNs(rawTimeNs: Long): Long = applyFinalTimeOffsetNs(rawTimeNs, 0L)
    private fun adjusted100mNs(rawTimeNs: Long): Long = applyFinalTimeOffsetNs(rawTimeNs, 0L)
    private fun adjusted200mNs(rawTimeNs: Long): Long = applyFinalTimeOffsetNs(rawTimeNs, 0L)
    private fun adjusted300mNs(rawTimeNs: Long): Long = applyFinalTimeOffsetNs(rawTimeNs, 0L)
    private fun adjusted402mNs(rawTimeNs: Long): Long = applyFinalTimeOffsetNs(rawTimeNs, 0L)

    private fun updateQuarterSectorDisplay() {
        if (!::tvSector50Time.isInitialized) return
        tvSector50Time.text = formatSecondsValue(adjusted50mNs(sector50TimeNanos))
        tvSector100Time.text = formatSecondsValue(adjusted100mNs(sector100TimeNanos))
        tvSector200Time.text = formatSecondsValue(adjusted200mNs(sector200TimeNanos))
        tvSector300Time.text = formatSecondsValue(adjusted300mNs(sector300TimeNanos))
        tvSector402Time.text = formatSecondsValue(adjusted402mNs(sector402TimeNanos))
        tvSector50Speed.text = formatSpeedForDisplay(sector50SpeedKmh)
        tvSector100Speed.text = formatSpeedForDisplay(sector100SpeedKmh)
        tvSector200Speed.text = formatSpeedForDisplay(sector200SpeedKmh)
        tvSector300Speed.text = formatSpeedForDisplay(sector300SpeedKmh)
        tvSector402Speed.text = formatSpeedForDisplay(sector402SpeedKmh)
        tvAllModeSector50Time?.text = formatSecondsValue(adjusted50mNs(sector50TimeNanos))
        tvAllModeSector100Time?.text = formatSecondsValue(adjusted100mNs(sector100TimeNanos))
        tvAllModeSector200Time?.text = formatSecondsValue(adjusted200mNs(sector200TimeNanos))
        tvAllModeSector300Time?.text = formatSecondsValue(adjusted300mNs(sector300TimeNanos))
        tvAllModeSector402Time?.text = formatSecondsValue(adjusted402mNs(sector402TimeNanos))
        tvAllModeSector50Speed?.text = formatSpeedForDisplay(sector50SpeedKmh)
        tvAllModeSector100Speed?.text = formatSpeedForDisplay(sector100SpeedKmh)
        tvAllModeSector200Speed?.text = formatSpeedForDisplay(sector200SpeedKmh)
        tvAllModeSector300Speed?.text = formatSpeedForDisplay(sector300SpeedKmh)
        tvAllModeSector402Speed?.text = formatSpeedForDisplay(sector402SpeedKmh)
    }

    private fun updateQuarterSectorMilestones(
        prevDistanceMeters: Float,
        currentDistanceMeters: Float,
        segmentStartElapsedNanos: Long,
        segmentEndElapsedNanos: Long,
        segmentStartSpeedKmh: Float,
        segmentEndSpeedKmh: Float
    ) {
        if (currentDistanceMeters <= prevDistanceMeters) {
            updateQuarterSectorDisplay()
            return
        }

        fun crossed(targetMeters: Float): Boolean {
            return prevDistanceMeters < targetMeters && currentDistanceMeters >= targetMeters
        }

        fun crossingRatio(targetMeters: Float): Float {
            val denom = (currentDistanceMeters - prevDistanceMeters).coerceAtLeast(0.0001f)
            return ((targetMeters - prevDistanceMeters) / denom).coerceIn(0f, 1f)
        }

        fun crossingElapsedNanos(targetMeters: Float): Long {
            val ratio = crossingRatio(targetMeters)
            val span = (segmentEndElapsedNanos - segmentStartElapsedNanos).coerceAtLeast(0L)
            return segmentStartElapsedNanos + (span * ratio).toLong()
        }

        fun crossingSpeedKmh(targetMeters: Float): Float {
            val ratio = crossingRatio(targetMeters)
            return segmentStartSpeedKmh + (segmentEndSpeedKmh - segmentStartSpeedKmh) * ratio
        }

        if (sector50TimeNanos < 0L && crossed(QUARTER_MILE_SECTOR_50)) {
            sector50TimeNanos = crossingElapsedNanos(QUARTER_MILE_SECTOR_50)
            sector50SpeedKmh = crossingSpeedKmh(QUARTER_MILE_SECTOR_50)
        }
        if (sector100TimeNanos < 0L && crossed(QUARTER_MILE_SECTOR_100)) {
            sector100TimeNanos = crossingElapsedNanos(QUARTER_MILE_SECTOR_100)
            sector100SpeedKmh = crossingSpeedKmh(QUARTER_MILE_SECTOR_100)
        }
        if (sector200TimeNanos < 0L && crossed(QUARTER_MILE_SECTOR_200)) {
            sector200TimeNanos = crossingElapsedNanos(QUARTER_MILE_SECTOR_200)
            sector200SpeedKmh = crossingSpeedKmh(QUARTER_MILE_SECTOR_200)
        }
        if (sector300TimeNanos < 0L && crossed(QUARTER_MILE_SECTOR_300)) {
            sector300TimeNanos = crossingElapsedNanos(QUARTER_MILE_SECTOR_300)
            sector300SpeedKmh = crossingSpeedKmh(QUARTER_MILE_SECTOR_300)
        }
        if (sector402TimeNanos < 0L && crossed(QUARTER_MILE_SECTOR_402)) {
            sector402TimeNanos = crossingElapsedNanos(QUARTER_MILE_SECTOR_402)
            sector402SpeedKmh = crossingSpeedKmh(QUARTER_MILE_SECTOR_402)
        }

        updateQuarterSectorDisplay()
    }

    private fun updateAllModeProgress(currentSpeedKmh: Float) {
        if (!::pbAll0to100.isInitialized) return

        val progress0to100 = if (measured0to100) 100 else ((currentSpeedKmh / 100f) * 100f).toInt().coerceIn(0, 100)
        val progress100to200 = if (measured100to200) 100 else (((currentSpeedKmh - 100f) / 100f) * 100f).toInt().coerceIn(0, 100)
        val progress0to200 = if (measured0to200) 100 else ((currentSpeedKmh / 200f) * 100f).toInt().coerceIn(0, 100)
        val progress0to402 = if (measured0to402) 100 else ((accumulatedDistance / TARGET_METERS) * 100f).toInt().coerceIn(0, 100)

        pbAll0to100.progress = progress0to100
        pbAll100to200.progress = progress100to200
        pbAll0to200.progress = progress0to200
        pbAll0to402.progress = progress0to402
    }

    private fun ensureServiceAndStart() {
        val intent = Intent(this, ForegroundService::class.java).apply {
            // Do not use ACTIVATE_NORMAL_MODE here: on a warm service it can race with
            // startMeasuring() and leave drag in NORMAL mode (linear-accel start disabled).
            putExtra("ACTIVATE_DRAG_MODE", true)
            putExtra("FORCE_GPS_HIGH_FREQUENCY", true)  // Форсираме високочестотен GPS за drag
        }
        startService(intent)

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
        syncServiceRunOrientation()
        ensureDragCalibrationRuntimeReady()
        
        createNewAttempt()
        
        // Start G-force measurement in service
        foregroundService?.startNewMeasurement(measurementMode.name)
        
        // Start polling to update UI (measuring is now true)
        startPolling()
    }

    private fun startPolling() {
        pollHandler.removeCallbacks(pollRunnable)
        pollHandler.post(pollRunnable)
        startAccelerationPanelLoop()
    }

    private fun stopPolling() {
        pollHandler.removeCallbacks(pollRunnable)
        stopAccelerationPanelLoop()
    }

    private fun handleLocation(loc: Location) {
        // Phone GPS: ignore coarse fixes. RaceBox already gates fix quality — never drop its
        // samples here or waitingForFullStop / fail-start / re-arm never see a stop.
        val fromRaceBox = loc.provider == "racebox" || isRaceBoxGpsStartActive()
        if (!fromRaceBox && loc.accuracy > GPS_READY_ACCURACY_METERS) {
            return
        }
        if (fromRaceBox && loc.hasAccuracy() && loc.accuracy > 80f) {
            return
        }

        val rawSpeedKmh = loc.speed.coerceAtLeast(0f) * 3.6f
        // RaceBox residual Doppler can sit at 3–6 km/h while stopped; use the same sanitized
        // speed the HUD shows so stop / re-arm / banners match phone behavior.
        val speedKmh = if (fromRaceBox) {
            foregroundService?.getCurrentSpeed()?.takeIf { it >= 0f }
                ?: com.revix.app.utils.GnssSpeedSanitizer.sanitizeReportedKmh(rawSpeedKmh)
        } else {
            rawSpeedKmh
        }

        if (!gpsReady) {
            gpsReady = true
            updateReadyStatus()
        }

        // ALL / 100-200: sure lift or brake ends the run. Other modes use fail-start.
        maybeDetectSureEndOfRun(loc, speedKmh)

        if (measurementMode != MeasurementMode.HUNDRED_TO_200 && !waitingForFullStop) {
            if (started && !measurementComplete) {
                if (speedKmh >= LAUNCH_CONFIRM_SPEED_KMH) {
                    launchSpeedConfirmed = true
                }
                maybeAbortFailedLaunch(loc, speedKmh)
            } else if (!started) {
                updatePhoneStationaryArm(speedKmh)
                releaseInvalidLinearAccelTriggerIfNeeded(speedKmh)
            }
        }

        when (measurementMode) {
            MeasurementMode.ZERO_TO_100,
            MeasurementMode.ZERO_TO_200,
            MeasurementMode.QUARTER_MILE -> {

                if (waitingForFullStop) {
                    if (isConfirmedFullStop(loc, speedKmh)) {
                        prepareSingleModeNextAttemptAfterFullStop()
                    } else {
                        // Keep banner visible (same as ALL) — RaceBox was leaving stale status text.
                        val modeLabel = when (measurementMode) {
                            MeasurementMode.ZERO_TO_100 -> formatDragSpeedRangeWithUnit(0, 100)
                            MeasurementMode.ZERO_TO_200 -> formatDragSpeedRangeWithUnit(0, 200)
                            MeasurementMode.QUARTER_MILE -> formatDragQuarterRangeLabel()
                            else -> ""
                        }
                        tvStatus.text = if (modeLabel.isNotEmpty()) {
                            getString(R.string.drag_complete_stop_for_new_format, modeLabel)
                        } else {
                            getString(R.string.drag_complete_stop_for_new)
                        }
                        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                    }
                } else if (!started && serviceReady && gpsReady && !decelerationDetected && !restartCooldownActive) {
                    if (isRaceBoxGpsStartActive()) {
                        if (pollRaceBoxGpsStart(loc, speedKmh)) {
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
                                linearAccelTriggered = false,
                                logSuffix = " (RaceBox GPS)",
                                statusText = getString(R.string.drag_status_measuring, modeText, attemptNumber)
                            )
                            resetRaceBoxGpsStartState()
                        }
                    } else {
                        val attemptNumber = getCurrentAttemptNumber()
                        val modeText = when (measurementMode) {
                            MeasurementMode.ZERO_TO_100 -> "0-100"
                            MeasurementMode.ZERO_TO_200 -> "0-200"
                            MeasurementMode.QUARTER_MILE -> "0-402m"
                            else -> ""
                        }
                        tryBeginPhoneStandingStart(
                            loc = loc,
                            speedKmh = speedKmh,
                            logSuffix = "",
                            statusText = getString(R.string.drag_status_measuring, modeText, attemptNumber)
                        )
                    }
                }

                if (measurementMode == MeasurementMode.QUARTER_MILE && started) {
                    handleQuarterMile(loc, speedKmh)
                }
            }

            MeasurementMode.HUNDRED_TO_200 -> {
                val fixNs = uniqueGpsFixNanos(loc)
                val isNewFix = fixNs > 0L && fixNs != rollingLastFixNs
                val prevFixNs = rollingLastFixNs
                val prevFixSpeed = rollingLastSpeedKmh
                if (isNewFix) {
                    rollingLastFixNs = fixNs
                    rollingLastSpeedKmh = speedKmh
                }
                if (speedKmh <= ROLLING_REARM_SPEED_KMH) {
                    rollingCanCross100 = true
                }

                if (started && !measurementComplete && isNewFix &&
                    prevFixNs > 0L &&
                    prevFixSpeed < ROLLING_FINISH_SPEED_KMH &&
                    speedKmh >= ROLLING_FINISH_SPEED_KMH
                ) {
                    val t200 = interpolateSpeedCrossingNs(
                        prevFixNs, prevFixSpeed, fixNs, speedKmh, ROLLING_FINISH_SPEED_KMH
                    )
                    completeHundredToTwoHundred(t200)
                } else if (waitingForFullStop) {
                    if (speedKmh <= ROLLING_REARM_SPEED_KMH) {
                        prepareHundredToTwoHundredNextAttempt()
                    }
                } else if (!started &&
                    serviceReady &&
                    gpsReady &&
                    !decelerationDetected &&
                    rollingCanCross100 &&
                    isNewFix &&
                    prevFixNs > 0L &&
                    prevFixSpeed < ROLLING_CROSS_SPEED_KMH &&
                    speedKmh >= ROLLING_CROSS_SPEED_KMH
                ) {
                    val t100 = interpolateSpeedCrossingNs(
                        prevFixNs, prevFixSpeed, fixNs, speedKmh, ROLLING_CROSS_SPEED_KMH
                    )
                    beginHundredToTwoHundredFromCrossing(loc, speedKmh, t100)
                    if (speedKmh >= ROLLING_FINISH_SPEED_KMH) {
                        val t200 = interpolateSpeedCrossingNs(
                            prevFixNs, prevFixSpeed, fixNs, speedKmh, ROLLING_FINISH_SPEED_KMH
                        )
                        completeHundredToTwoHundred(t200)
                    }
                } else if (!started && !waitingForFullStop && !decelerationDetected) {
                    updateHundredToTwoHundredIdleStatus(speedKmh)
                }
            }

            MeasurementMode.ALL -> {
                // Проверка за пълна спирка след деселерация
                if (waitingForFullStop) {
                    if (isConfirmedFullStop(loc, speedKmh)) {
                        if (attemptAlreadySaved || hasAllModePersistableMetrics()) {
                            if (!attemptAlreadySaved) {
                                saveCurrentAttempt()
                                attemptAlreadySaved = true
                            }
                            prepareAllModeNextAttemptAfterFullStop()
                        } else {
                            // Прекъснат/неуспешен ALL опит: рестартираме само текущия опит.
                            stashCurrentAllModePartialAttemptIfNeeded()
                            waitingForFullStop = false
                            decelerationDetected = false
                            cancelRestartCooldown()
                            val rearmLoc = loc
                            val rearmSpeed = speedKmh
                            createNewAttempt()
                            foregroundService?.startNewMeasurement(measurementMode.name)
                            armRaceBoxStartIfAlreadyStationary(rearmLoc, rearmSpeed)
                            tvStatus.text = getString(R.string.drag_status_ready_all)
                            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                        }
                    } else {
                        // Продължаваме да показваме съобщението
                        tvStatus.text = if (attemptAlreadySaved || hasAllModePersistableMetrics()) {
                            getString(R.string.drag_complete_stop_for_new)
                        } else {
                            getString(R.string.drag_status_stop_to_restart)
                        }
                        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                    }
                } else if (!started && serviceReady && gpsReady && !decelerationDetected && !restartCooldownActive) {
                    if (isRaceBoxGpsStartActive()) {
                        if (pollRaceBoxGpsStart(loc, speedKmh)) {
                            beginMeasurementFromLinearAcceleration(
                                loc = loc,
                                speedKmh = speedKmh,
                                linearAccelTriggered = false,
                                logSuffix = " (ALL / RaceBox GPS)",
                                statusText = getString(R.string.drag_measuring)
                            )
                            resetRaceBoxGpsStartState()
                        }
                    } else {
                        tryBeginPhoneStandingStart(
                            loc = loc,
                            speedKmh = speedKmh,
                            logSuffix = " (ALL режим)",
                            statusText = getString(R.string.drag_measuring)
                        )
                    }
                }

                // В ALL режим винаги проверяваме 0-402m ако не е завършено
                if (started && (measurementMode == MeasurementMode.ALL || measurementMode == MeasurementMode.QUARTER_MILE)) {
                    handleQuarterMile(loc, speedKmh)
                }
            }
        }

        lastSpeed = speedKmh
    }

    private fun resetEndOfRunWatch() {
        runPeakSpeedKmh = 0f
        endBrakeHoldStartNs = 0L
        endCoastFallingFixes = 0
        endCoastStreakStartNs = 0L
        endLastFixElapsedNs = -1L
        endLastFixSpeedKmh = -1f
    }

    private fun uniqueGpsFixNanos(loc: Location): Long {
        return when {
            loc.elapsedRealtimeNanos > 0L -> loc.elapsedRealtimeNanos
            loc.time > 0L -> loc.time * 1_000_000L
            else -> -1L
        }
    }

    private fun resetRearmStopWatch() {
        rearmStopFixCount = 0
        rearmLastFixNs = -1L
        rearmFirstFixNs = 0L
    }

    private fun markWaitingForFullStop() {
        waitingForFullStop = true
        resetRearmStopWatch()
    }

    /**
     * READY / fail-start stop: two *new* GPS fixes under 3 km/h, ≥400 ms apart.
     * Same lastLocation polled at 100 ms does not count twice. Phone IMU on gas vetoes.
     */
    private fun isConfirmedFullStop(loc: Location, speedKmh: Float): Boolean {
        if (speedKmh >= FULL_STOP_REARM_SPEED_KMH) {
            resetRearmStopWatch()
            return false
        }
        val forwardG = currentForwardAccelG()
        if (forwardG != null && forwardG > REARM_ON_GAS_G) {
            resetRearmStopWatch()
            return false
        }
        val fixNs = uniqueGpsFixNanos(loc)
        if (fixNs <= 0L) return false
        if (fixNs != rearmLastFixNs) {
            rearmLastFixNs = fixNs
            if (rearmStopFixCount == 0) {
                rearmFirstFixNs = fixNs
            }
            rearmStopFixCount += 1
        }
        if (rearmStopFixCount < REARM_UNIQUE_FIXES) return false
        return (fixNs - rearmFirstFixNs) >= REARM_MIN_DWELL_NS
    }

    /** Forward accel in g. Positive = on gas, negative = braking. Null on RaceBox. */
    private fun currentForwardAccelG(): Float? {
        if (isRaceBoxGpsStartActive()) return null
        val svc = foregroundService ?: return null
        return -svc.getCurrentGForceY()
    }

    /**
     * End ALL / 100-200 only when we are sure the driver lifted or braked.
     * Phone: IMU brake hold, or 3 new GPS fixes down with ≥27 km/h off peak.
     * RaceBox: unique GPS streak ≥1.5 s and ≥27 km/h off peak, no recovery.
     */
    private fun maybeDetectSureEndOfRun(loc: Location, speedKmh: Float) {
        if (measurementMode != MeasurementMode.ALL &&
            measurementMode != MeasurementMode.HUNDRED_TO_200
        ) {
            return
        }
        if (!started || measurementComplete || waitingForFullStop || decelerationDetected) {
            return
        }
        if (speedKmh > runPeakSpeedKmh) {
            runPeakSpeedKmh = speedKmh
            endCoastFallingFixes = 0
            endCoastStreakStartNs = 0L
        }

        val t0 = if (measurementMode == MeasurementMode.HUNDRED_TO_200) {
            rolling100StartTime
        } else {
            startTimeNano
        }
        if (t0 <= 0L || System.nanoTime() - t0 < END_GRACE_AFTER_START_NS) {
            rememberEndOfRunFix(loc, speedKmh)
            return
        }

        val nowNs = System.nanoTime()
        val forwardG = currentForwardAccelG()
        if (forwardG != null) {
            if (forwardG > END_ON_GAS_G) {
                endBrakeHoldStartNs = 0L
                endCoastFallingFixes = 0
                endCoastStreakStartNs = 0L
            } else if (forwardG <= END_BRAKE_G) {
                if (endBrakeHoldStartNs <= 0L) {
                    endBrakeHoldStartNs = nowNs
                }
                if (nowNs - endBrakeHoldStartNs >= END_BRAKE_HOLD_NS) {
                    confirmSureEndOfRun(speedKmh, "phone brake ${"%.2f".format(forwardG)}g")
                    return
                }
            } else {
                endBrakeHoldStartNs = 0L
            }
        }

        val fixNs = uniqueGpsFixNanos(loc)
        val isNewFix = fixNs > 0L && fixNs != endLastFixElapsedNs
        if (isNewFix) {
            val prevFixSpeed = endLastFixSpeedKmh
            rememberEndOfRunFix(loc, speedKmh)

            if (speedKmh > runPeakSpeedKmh) {
                runPeakSpeedKmh = speedKmh
                endCoastFallingFixes = 0
                endCoastStreakStartNs = 0L
                return
            }
            val recovered = prevFixSpeed >= 0f && speedKmh > prevFixSpeed + END_RECOVERY_KMH
            if (recovered || (forwardG != null && forwardG > END_ON_GAS_G)) {
                endCoastFallingFixes = 0
                endCoastStreakStartNs = 0L
                return
            }

            val dropFromPeak = runPeakSpeedKmh - speedKmh
            if (isRaceBoxGpsStartActive()) {
                if (endCoastStreakStartNs <= 0L) {
                    endCoastStreakStartNs = nowNs
                }
                if (nowNs - endCoastStreakStartNs >= END_RACEBOX_STREAK_NS &&
                    dropFromPeak >= END_COAST_DROP_KMH
                ) {
                    confirmSureEndOfRun(speedKmh, "RaceBox coast drop=${"%.1f".format(dropFromPeak)}")
                }
            } else {
                val falling = prevFixSpeed >= 0f && speedKmh < prevFixSpeed
                if (falling) {
                    endCoastFallingFixes += 1
                }
                if (endCoastFallingFixes >= END_COAST_UNIQUE_FIXES &&
                    dropFromPeak >= END_COAST_DROP_KMH &&
                    (forwardG == null || forwardG <= END_ON_GAS_G)
                ) {
                    confirmSureEndOfRun(speedKmh, "phone coast drop=${"%.1f".format(dropFromPeak)}")
                }
            }
        }
    }

    private fun rememberEndOfRunFix(loc: Location, speedKmh: Float) {
        val fixNs = uniqueGpsFixNanos(loc)
        if (fixNs <= 0L) return
        endLastFixElapsedNs = fixNs
        endLastFixSpeedKmh = speedKmh
    }

    private fun confirmSureEndOfRun(speedKmh: Float, reason: String) {
        if (decelerationDetected || measurementComplete || waitingForFullStop) return
        Log.d("DragRunPage", "🛑 Sure end of run: $reason peak=${"%.1f".format(runPeakSpeedKmh)} speed=$speedKmh")
        decelerationDetected = true
        handleDeceleration(speedKmh)
    }

    private fun handleDeceleration(currentSpeed: Float) {

        when (measurementMode) {
            MeasurementMode.ZERO_TO_100,
            MeasurementMode.ZERO_TO_200,
            MeasurementMode.QUARTER_MILE -> {
                // За индивидуални режими - НЕ изтриваме данните при деселерация
                // Само спираме измерването и чакаме пълно спиране
                measurementComplete = true
                markWaitingForFullStop()
                started = false
                
                // НЕ нулираме измерванията - запазваме успешните резултати
                // Данните ще се нулират само при пълно спиране (под 3 km/h)

                foregroundService?.stopMeasurement()
                tvStatus.text = getString(R.string.drag_status_deceleration)
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            }

            MeasurementMode.HUNDRED_TO_200 -> {
                measurementComplete = true
                markWaitingForFullStop()
                started = false
                rollingStartReady = false
                rollingCanCross100 = false
                foregroundService?.stopMeasurement()
                tvStatus.text = getString(R.string.drag_status_deceleration_100to200)
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            }

            MeasurementMode.ALL -> {
                // За ALL режим - НЕ изтриваме данните при деселерация
                // Само спираме измерването и чакаме пълно спиране
                finalizeAllModeRunForNextAttempt()
            }
        }
    }


    private fun handleQuarterMile(loc: Location, speedKmh: Float) {
        // В ALL режим или QUARTER_MILE режим, ако не сме завършили измерването
        if ((measurementMode == MeasurementMode.ALL || measurementMode == MeasurementMode.QUARTER_MILE) && !distanceCompleted) {
            if (startLocation == null) return
            val measurementStartTime = foregroundService?.getMeasurementStartTimeNano() ?: 0L
            val measurementStartTimeGps = foregroundService?.getMeasurementStartTimeGpsNano() ?: 0L
            if (measurementStartTime <= 0L && measurementStartTimeGps <= 0L) return

            val currentElapsedNanos = when {
                measurementStartTimeGps > 0L &&
                    loc.elapsedRealtimeNanos > 0L &&
                    loc.elapsedRealtimeNanos >= measurementStartTimeGps -> {
                    (loc.elapsedRealtimeNanos - measurementStartTimeGps).coerceAtLeast(0L)
                }
                measurementStartTime > 0L -> {
                    (System.nanoTime() - measurementStartTime).coerceAtLeast(0L)
                }
                else -> return
            }
            
            // Distance from speed×time (same method as Dragy / RaceBox), not GPS point-to-point.
            val prevElapsedNs = lastQuarterDistanceElapsedNanos.takeIf { it >= 0L } ?: 0L
            val prevSpeedKmh = lastQuarterSpeedKmh.takeIf { it >= 0f } ?: 0f
            val prevDistance = accumulatedDistance
            accumulatedDistance += distanceIncrementFromSpeed(
                prevElapsedNs = prevElapsedNs,
                currentElapsedNs = currentElapsedNanos,
                prevSpeedKmh = prevSpeedKmh,
                currentSpeedKmh = speedKmh
            )
            updateQuarterSectorMilestones(
                prevDistanceMeters = prevDistance,
                currentDistanceMeters = accumulatedDistance,
                segmentStartElapsedNanos = prevElapsedNs,
                segmentEndElapsedNanos = currentElapsedNanos,
                segmentStartSpeedKmh = prevSpeedKmh,
                segmentEndSpeedKmh = speedKmh
            )
            lastQuarterDistanceElapsedNanos = currentElapsedNanos
            lastQuarterSpeedKmh = speedKmh
            lastLocationForDistance = loc
            if (measurementMode == MeasurementMode.ALL) {
                val speedUnit = UnitsManager.getSpeedUnit(this)
                if (speedUnit == UnitsManager.SpeedUnit.MPH) {
                    val distInKm = accumulatedDistance / 1000.0
                    val distInMiles = UnitsManager.convertDistance(distInKm, UnitsManager.DistanceUnit.MILES)
                    tvCard0to402Distance.text = String.format("%.2f mi", distInMiles)
                } else {
                    tvCard0to402Distance.text = String.format("%.0f m", accumulatedDistance)
                }
            }


            if (accumulatedDistance >= TARGET_METERS) {
                val elapsedNanos = currentElapsedNanos
                refreshLiveCompensatedMilestones(force = true)
                val canonical402Nanos = when {
                    sector402TimeNanos > 0L -> sector402TimeNanos
                    attempt0to402Nanos > 0L -> attempt0to402Nanos
                    else -> elapsedNanos
                }

                // Запазваме времето за по-късно използване
                attempt0to402Nanos = canonical402Nanos
                if (sector402TimeNanos <= 0L) {
                    sector402TimeNanos = canonical402Nanos
                    sector402SpeedKmh = speedKmh
                }
                updateQuarterSectorDisplay()

                val resultDisplayNanos = adjusted0to402Ns(canonical402Nanos)
                val resultText = formatNanos(resultDisplayNanos)
                val display = if (measurementMode == MeasurementMode.ALL) {
                    resultText
                } else if (sessionBest0to402 < 0 || resultDisplayNanos < sessionBest0to402) {
                    "🏆 $resultText"
                } else {
                    resultText
                }
                tvCard0to402.text = displayTimeWithBest(display, sessionBest0to402)

                tvCard0to402Distance.visibility = if (measurementMode == MeasurementMode.ALL) View.VISIBLE else View.GONE
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
                    markWaitingForFullStop()
                    tvStatus.text = getString(
                        R.string.drag_complete_stop_for_new_format,
                        formatDragQuarterRangeLabel()
                    )
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                } else {
                    // В ALL режим - проверяваме дали всички измервания са завършени
                    tvStatus.text = getString(R.string.drag_status_quarter_complete_continue)
                    checkAllMeasurementsComplete()
                }
            }
        }
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
                foregroundService?.startNewMeasurement(measurementMode.name)
                tvStatus.text = getString(R.string.drag_status_ready_0to100)
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            }
            MeasurementMode.ZERO_TO_200 -> {
                createNewAttempt()
                foregroundService?.startNewMeasurement(measurementMode.name)
                tvStatus.text = getString(R.string.drag_status_ready_0to200)
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            }
            MeasurementMode.QUARTER_MILE -> {
                createNewAttempt()
                foregroundService?.startNewMeasurement(measurementMode.name)
                tvStatus.text = getString(R.string.drag_status_ready_quarter)
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            }
            else -> {
                createNewAttempt()
                foregroundService?.startNewMeasurement(measurementMode.name)
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
        currentAttemptWasOfficiallyStarted = false
        decelerationDetected = false
        waitingForFullStop = false
        resetEndOfRunWatch()
        resetRearmStopWatch()
        // Нулираме измерванията
        measured0to100 = false
        measured0to200 = false
        measured100to200 = false
        measured0to402 = false
        
        // Reset sound flags
        sound100Played = false
        sound200Played = false
        sound402Played = false
        resetAccelerationForcePanel()

        attempt0to100Nanos = -1L
        attempt0to200Nanos = -1L
        attempt100to200Nanos = -1L
        attempt0to402Nanos = -1L
        timeAt100Nano = -1L
        resetQuarterSectorState()
        resetQuarterSectorDisplay()

        startTimeNano = 0L
        startLocation = null
        distanceCompleted = false
        accumulatedDistance = 0f
        lastLocationForDistance = null

        // Нулираме дисплея
        resetDisplayValues()
        updateAllModeProgress(0f)
        updateSingleModeMetricDisplay()
        updateQuarterModeMetricDisplay()

        // Спираме G-force измерването и re-arm linear-accel trigger detection
        foregroundService?.stopMeasurement()
        foregroundService?.startNewMeasurement(measurementMode.name)
        val loc = foregroundService?.getLastLocation()
        armRaceBoxStartIfAlreadyStationary(loc, loc?.speed?.times(3.6f) ?: lastSpeed)

        runOnUiThread {
            tvStatus.text = getString(R.string.drag_status_ready_all)
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        }

    }

    private fun hasAllModeSuccessfulMetrics(): Boolean {
        if (measurementMode != MeasurementMode.ALL) return false
        return attempt0to100Nanos > 0L &&
            attempt0to200Nanos > 0L &&
            attempt100to200Nanos > 0L &&
            attempt0to402Nanos > 0L
    }

    /** Any completed milestone — enough to treat the run as a real attempt (phone + IMU). */
    private fun hasAllModePersistableMetrics(): Boolean {
        if (measurementMode != MeasurementMode.ALL) return false
        return attempt0to100Nanos > 0L ||
            attempt0to200Nanos > 0L ||
            attempt100to200Nanos > 0L ||
            attempt0to402Nanos > 0L
    }

    /**
     * End an ALL run that has at least one real result (often 0-402 without 0-200),
     * save it, and enter the same stop-completely → next-attempt flow as phone.
     */
    private fun finalizeAllModeRunForNextAttempt() {
        if (measurementMode != MeasurementMode.ALL) return
        if (waitingForFullStop && measurementComplete) {
            // Already finalized — keep banner path in handleLocation.
            if (hasAllModePersistableMetrics() && !attemptAlreadySaved) {
                saveCurrentAttempt()
                attemptAlreadySaved = true
            }
            return
        }

        if (hasAllModePersistableMetrics() && !attemptAlreadySaved) {
            saveCurrentAttempt()
            attemptAlreadySaved = true
        }

        measurementComplete = true
        markWaitingForFullStop()
        started = false
        decelerationDetected = true
        foregroundService?.stopMeasurement()

        runOnUiThread {
            tvStatus.text = if (attemptAlreadySaved || hasAllModePersistableMetrics()) {
                getString(R.string.drag_complete_stop_for_new)
            } else {
                getString(R.string.drag_status_stop_to_restart)
            }
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            btnStop.text = getString(R.string.stop_session)
        }
    }

    private fun checkAllMeasurementsComplete() {
        if (measurementMode == MeasurementMode.ALL && hasAllModeSuccessfulMetrics()) {

            // Успешен ALL опит: запазваме го и чакаме пълно спиране за нов опит.
            if (!attemptAlreadySaved) {
                saveCurrentAttempt()
                attemptAlreadySaved = true
            }

            measurementComplete = true
            started = false
            markWaitingForFullStop()
            decelerationDetected = true
            foregroundService?.stopMeasurement()
            tvStatus.text = getString(R.string.drag_complete_stop_for_new)
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnStop.text = getString(R.string.stop_session)
        }
    }
    
    private fun checkAllMeasurementsCompleteExcept402() {
        if (measurementMode != MeasurementMode.ALL) return
        if (measurementComplete || attemptAlreadySaved) return

        // 402m may complete before 0-200/100-200 in some runs.
        // Finalize immediately once all four metrics are available.
        if (hasAllModeSuccessfulMetrics()) {
            checkAllMeasurementsComplete()
        }
    }

    private fun getCurrentAttemptNumber(): Int {
        return (currentSession?.attempts?.size ?: 0) + pendingAllModePartialAttempts.size + 1
    }

    private fun updateUIFromService() {
        val svc = foregroundService ?: return

        val currentG = svc.getCurrentG()
        val peakG = svc.getPeakG()
        val speedFloat = svc.getCurrentSpeed()
        val speed = speedFloat.toInt()

        if (started && !measurementComplete) {
            refreshLiveCompensatedMilestones()
        }

        runOnUiThread {
            val shouldPulse = started && !measurementComplete && !waitingForFullStop && !restartCooldownActive
            setStatusPulseActive(shouldPulse)

            // Скорост - конвертирана според избраната единица
            val convertedSpeed = UnitsManager.convertSpeed(speed.toFloat(), UnitsManager.getSpeedUnit(this))
            lastDisplayedConvertedSpeed = convertedSpeed
            lastDisplayedG = currentG
            applyPrimaryDisplayState()
            tvGCurrentBig.text = String.format("%.2f g", currentG)
            if (measurementMode == MeasurementMode.ALL) {
                updateAllModeProgress(speedFloat)
            }
            updateSingleModeMetricDisplay()
            updateQuarterModeMetricDisplay()

            // Update GGaugeView with G-force data
            // Get G-force components from service
            val gForceX = foregroundService?.getCurrentGForceX() ?: 0f
            val gForceY = foregroundService?.getCurrentGForceY() ?: 0f
            if (::gGaugeView.isInitialized && gContainer.visibility == View.VISIBLE) {
                gGaugeView.gForceX = gForceX
                gGaugeView.gForceY = gForceY
                gGaugeView.peakGForce = peakG
            }
            publishDragVideoHud(speedKmh = speedFloat)
        }

        // Обработка на измерванията
        if (started && !measurementComplete) {
            val nowNano = System.nanoTime()

            // Използваме СЪЩАТА времева основа като RAW данните
            val measurementStartTimeNano = foregroundService?.getMeasurementStartTimeNano() ?: 0L

            // 0-100 измерване
            if ((measurementMode == MeasurementMode.ALL || measurementMode == MeasurementMode.ZERO_TO_100 || measurementMode == MeasurementMode.ZERO_TO_200) && !measured0to100) {
                val svcT100 = foregroundService?.getTime0to100Nanos() ?: 0L
                if (svcT100 > 0 && measurementStartTimeNano > 0) {
                    refreshLiveCompensatedMilestones(force = true)
                    val canonical0to100Nanos = attempt0to100Nanos.takeIf { it > 0L }
                        ?: svcT100
                    attempt0to100Nanos = canonical0to100Nanos
                    timeAt100Nano = measurementStartTimeNano + canonical0to100Nanos
                    val resultDisplayNanos = adjusted0to100Ns(canonical0to100Nanos)
                    val timeStr = formatNanos(resultDisplayNanos)
                    
                    runOnUiThread {
                        val display = if (measurementMode == MeasurementMode.ALL) {
                            timeStr
                        } else if (sessionBest0to100 < 0 || resultDisplayNanos < sessionBest0to100) {
                            "🏆 $timeStr"
                        } else {
                            timeStr
                        }
                        tvCard0to100.text = displayTimeWithBest(display, sessionBest0to100)
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
                        started = false
                        markWaitingForFullStop()
                        runOnUiThread {
                            tvStatus.text = getString(
                                R.string.drag_complete_stop_for_new_format,
                                formatDragSpeedRangeWithUnit(0, 100)
                            )
                            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                        }
                    }
                } else if (measurementStartTimeNano > 0) {
                    // Показваме таймер докато измерваме
                    val elapsed = nowNano - measurementStartTimeNano
                    runOnUiThread {
                        val timerText = String.format("%.2f s", elapsed / 1_000_000_000.0)
                        tvCard0to100.text = displayTimeWithBest(timerText, sessionBest0to100)
                    }
                }
            }

            // 0-200 измерване
            if ((measurementMode == MeasurementMode.ALL || measurementMode == MeasurementMode.ZERO_TO_200) && !measured0to200) {
                val svcT200 = foregroundService?.getTime0to200Nanos() ?: 0L
                if (svcT200 > 0 && measurementStartTimeNano > 0) {
                    refreshLiveCompensatedMilestones(force = true)
                    val canonical0to200Nanos = attempt0to200Nanos.takeIf { it > 0L }
                        ?: svcT200
                    attempt0to200Nanos = canonical0to200Nanos
                    if (timeAt100Nano <= 0) {
                        val canonical0to100Nanos = attempt0to100Nanos
                            .takeIf { it > 0L }
                            ?: (foregroundService?.getTime0to100Nanos() ?: 0L)
                        if (canonical0to100Nanos > 0L) {
                            timeAt100Nano = measurementStartTimeNano + canonical0to100Nanos
                        }
                    }
                    if (timeAt100Nano > 0) {
                        val canonical0to100Nanos = attempt0to100Nanos
                            .takeIf { it > 0L }
                            ?: (foregroundService?.getTime0to100Nanos() ?: 0L)
                        if (canonical0to100Nanos > 0L && canonical0to200Nanos > canonical0to100Nanos) {
                            attempt100to200Nanos = canonical0to200Nanos - canonical0to100Nanos
                        }
                    }
                    
                    val resultDisplayNanos = adjusted0to200Ns(canonical0to200Nanos)
                    val timeStr = formatNanos(resultDisplayNanos)

                    runOnUiThread {
                        val display = if (measurementMode == MeasurementMode.ALL) {
                            timeStr
                        } else if (sessionBest0to200 < 0 || resultDisplayNanos < sessionBest0to200) {
                            "🏆 $timeStr"
                        } else {
                            timeStr
                        }
                        tvCard0to200.text = displayTimeWithBest(display, sessionBest0to200)
                    }
                    measured0to200 = true
                    if (attempt100to200Nanos > 0L) {
                        measured100to200 = true
                    }
                    
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
                        started = false
                        markWaitingForFullStop()
                        runOnUiThread {
                            tvStatus.text = getString(
                                R.string.drag_complete_stop_for_new_format,
                                formatDragSpeedRangeWithUnit(0, 200)
                            )
                            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                        }
                    }
                } else if (measurementStartTimeNano > 0) {
                    // Показваме таймер
                    val elapsed = nowNano - measurementStartTimeNano
                    runOnUiThread {
                        val timerText = String.format("%.2f s", elapsed / 1_000_000_000.0)
                        tvCard0to200.text = displayTimeWithBest(timerText, sessionBest0to200)
                    }
                }
            }

            // 100-200 измерване (само за ALL режим)
            if (measurementMode == MeasurementMode.ALL && !measured100to200) {
                if (timeAt100Nano > 0 && speedFloat >= 200f) {
                    refreshLiveCompensatedMilestones(force = true)
                    if (attempt100to200Nanos <= 0) {
                        // Изчисляваме само в GPS clock domain — не смесваме System.nanoTime() с GPS relative стойности
                        if (attempt0to200Nanos > 0L && attempt0to100Nanos > 0L && attempt0to200Nanos > attempt0to100Nanos) {
                            attempt100to200Nanos = attempt0to200Nanos - attempt0to100Nanos
                        }
                    }
                    val resultNanos = attempt100to200Nanos
                    if (resultNanos > 0) {
                        val timeStr = formatNanos(resultNanos)
                        runOnUiThread {
                            tvCard100to200.text = timeStr
                        }
                        measured100to200 = true
                        // В ALL режим - НЕ проверяваме тук дали всички измервания са завършени
                        // защото 0-402m може да завърши преди 100-200
                    }
                } else if (timeAt100Nano > 0) {
                    // Таймер за 100-200
                    val elapsed = nowNano - timeAt100Nano
                    runOnUiThread {
                        tvCard100to200.text = String.format("%.2f s", elapsed / 1_000_000_000.0)
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
                        val timerText = String.format("%.2f s", seconds)
                        tvCard0to402.text = displayTimeWithBest(timerText, sessionBest0to402)
                    }
                }
            }

            // Статус обновяване за индивидуални режими
            if (measurementMode != MeasurementMode.ALL &&
                measurementMode != MeasurementMode.HUNDRED_TO_200 &&
                started &&
                !measurementComplete
            ) {
                runOnUiThread {
                    val attemptNumber = getCurrentAttemptNumber()
                    val modeText = when (measurementMode) {
                        MeasurementMode.ZERO_TO_100 -> UnitsManager.formatDragSpeedIntervalLabel(0, 100, this)
                        MeasurementMode.ZERO_TO_200 -> UnitsManager.formatDragSpeedIntervalLabel(0, 200, this)
                        MeasurementMode.QUARTER_MILE -> formatDragQuarterRangeLabel()
                        MeasurementMode.HUNDRED_TO_200 -> UnitsManager.formatDragSpeedIntervalLabel(100, 200, this)
                        else -> ""
                    }
                    tvStatus.text = getString(R.string.drag_status_measuring, modeText, attemptNumber)
                }
            }
            // Статус обновяване за ALL режим (не пипай banner докато чакаме full stop)
            else if (measurementMode == MeasurementMode.ALL &&
                started &&
                !measurementComplete &&
                !waitingForFullStop
            ) {
                val distLabel = UnitsManager.getQuarterMileDistance(this)
                
                val completed = mutableListOf<String>()
                if (measured0to100) completed.add("${UnitsManager.formatDragSpeedIntervalLabel(0, 100, this)}✓")
                if (measured0to200) completed.add("${UnitsManager.formatDragSpeedIntervalLabel(0, 200, this)}✓")
                if (measured100to200) completed.add("${UnitsManager.formatDragSpeedIntervalLabel(100, 200, this)}✓")
                if (measured0to402) completed.add("$distLabel✓")

                runOnUiThread {
                    if (completed.isNotEmpty()) {
                        tvStatus.text = getString(R.string.drag_completed_format, completed.joinToString(" "))
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
                val timerText = String.format("%.2f s", elapsed)
                tvCard100to200.text = displayTimeWithBest(timerText, sessionBest100to200)
            }
        }

        runOnUiThread {
            if (measurementMode == MeasurementMode.ALL) {
                updateAllModeProgress(speedFloat)
                syncAllModeCanonicalCards()
            }
            updateSingleModeMetricDisplay()
            updateQuarterModeMetricDisplay()
            updateZeroTo200SplitDisplay(speedFloat)
        }

        lastSpeed = speedFloat
    }

    private fun interpolateSpeedCrossingNs(
        prevNs: Long,
        prevSpeedKmh: Float,
        currNs: Long,
        currSpeedKmh: Float,
        targetSpeedKmh: Float
    ): Long {
        val span = (currNs - prevNs).coerceAtLeast(0L)
        val denom = currSpeedKmh - prevSpeedKmh
        if (span <= 0L || denom <= 0.0001f) return currNs
        val ratio = ((targetSpeedKmh - prevSpeedKmh) / denom).coerceIn(0f, 1f)
        return prevNs + (span * ratio).toLong()
    }

    private fun beginHundredToTwoHundredFromCrossing(
        loc: Location,
        speedKmh: Float,
        t100ElapsedNs: Long
    ) {
        val nowSys = System.nanoTime()
        val nowGps = loc.elapsedRealtimeNanos.takeIf { it > 0L } ?: nowSys
        val ageNs = (nowGps - t100ElapsedNs).coerceAtLeast(0L)
        rolling100StartElapsedNs = t100ElapsedNs
        rolling100StartTime = nowSys - ageNs
        started = true
        currentAttemptWasOfficiallyStarted = true
        publishDragVideoHud()
        rollingCanCross100 = false
        resetEndOfRunWatch()
        runPeakSpeedKmh = speedKmh.coerceAtLeast(ROLLING_CROSS_SPEED_KMH)
        startTimeNano = rolling100StartTime
        tvStatus.text = getString(R.string.drag_measuring)
        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
        foregroundService?.setMeasurementStartTimeNano(rolling100StartTime)
        foregroundService?.startNewMeasurement(measurementMode.name)
        Log.d("DragRunPage", "🚀 100-200 start at interpolated 100 km/h, speed=$speedKmh")
    }

    private fun completeHundredToTwoHundred(t200ElapsedNs: Long) {
        if (measurementComplete) return
        attempt100to200Nanos = if (rolling100StartElapsedNs > 0L && t200ElapsedNs > rolling100StartElapsedNs) {
            t200ElapsedNs - rolling100StartElapsedNs
        } else {
            (System.nanoTime() - rolling100StartTime).coerceAtLeast(1L)
        }
        refreshLiveCompensatedMilestones(force = true)

        val measurementStartTimeNano = foregroundService?.getMeasurementStartTimeNano() ?: 0L
        val relativeStartTime = if (measurementStartTimeNano > 0L) {
            rolling100StartTime - measurementStartTimeNano
        } else {
            0L
        }
        currentAttempt = currentAttempt?.copy(startTime = relativeStartTime)

        if (attempt100to200Nanos > 20_000_000_000L || attempt100to200Nanos < 0) {
            Log.d("DragRunPage", "❌ Invalid 100-200 time: ${attempt100to200Nanos / 1_000_000_000.0}s - discarding")
            attempt100to200Nanos = -1L
            started = false
            measurementComplete = true
            markWaitingForFullStop()
            rollingCanCross100 = false
            foregroundService?.stopMeasurement()
            return
        }

        val resultText = formatNanos(attempt100to200Nanos)
        val display = if (sessionBest100to200 < 0 || attempt100to200Nanos < sessionBest100to200) {
            "🏆 $resultText"
        } else {
            resultText
        }
        tvCard100to200.text = displayTimeWithBest(display, sessionBest100to200)
        tvStatus.text = getString(
            R.string.drag_status_100to200_complete,
            formatDragSpeedRangeWithUnit(100, 200)
        )
        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        measurementComplete = true
        measured100to200 = true
        markWaitingForFullStop()
        rollingStartReady = false
        rollingCanCross100 = false
        foregroundService?.stopMeasurement()
        saveCurrentAttempt()
        attemptAlreadySaved = true
        Log.d("DragRunPage", "✅ 100-200 measured: ${attempt100to200Nanos / 1_000_000_000.0}s")
    }

    private fun updateHundredToTwoHundredIdleStatus(speedKmh: Float) {
        if (!serviceReady || !gpsReady) return
        if (rollingCanCross100 && speedKmh < ROLLING_CROSS_SPEED_KMH) {
            tvStatus.text = getString(R.string.drag_status_ready_100to200)
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else if (!rollingCanCross100 && speedKmh >= ROLLING_CROSS_SPEED_KMH) {
            tvStatus.text = getString(R.string.drag_status_drop_below_100)
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        }
    }

    private fun getMeasurementWindowAndSpeedCap(
        mode: MeasurementMode,
        attempt: DragAttempt,
        attempt0to100Ns: Long,
        attempt0to200Ns: Long,
        attempt100to200Ns: Long
    ): Triple<Long, Long, Float?> {
        return when (mode) {
            MeasurementMode.ZERO_TO_100 -> Triple(0L, attempt0to100Ns, null)
            MeasurementMode.ZERO_TO_200 -> Triple(0L, attempt0to200Ns, null)
            MeasurementMode.HUNDRED_TO_200 -> {
                val startNs = attempt.startTime.coerceAtLeast(0L)
                val endNs = if (attempt100to200Ns > 0L) startNs + attempt100to200Ns else -1L
                Triple(startNs, endNs, null)
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

    /**
     * Display-only: if the first SPEED sample is late (RaceBox buffer gap / late first fix),
     * prepend launch at t=0 so the chart shows the full run like phone.
     */
    private fun ensureSpeedChartStartsAtLaunch(
        speedSamples: List<Float>,
        speedTimes: List<Long>
    ): Pair<List<Float>, List<Long>> {
        val limit = minOf(speedSamples.size, speedTimes.size)
        if (limit <= 0) return speedSamples to speedTimes
        val firstTime = speedTimes.first()
        if (firstTime <= CHART_LAUNCH_GAP_FILL_NS) return speedSamples to speedTimes

        val filledSpeeds = ArrayList<Float>(limit + 1)
        val filledTimes = ArrayList<Long>(limit + 1)
        filledSpeeds.add(0f)
        filledTimes.add(0L)
        for (i in 0 until limit) {
            filledSpeeds.add(speedSamples[i].coerceAtLeast(0f))
            filledTimes.add(speedTimes[i])
        }
        return filledSpeeds to filledTimes
    }

    private fun ensureSpeedSeriesCoversMeasurementEnd(
        speedSamples: List<Float>,
        speedTimes: List<Long>,
        windowEndNs: Long,
        targetSpeedKmh: Float?
    ): Pair<List<Float>, List<Long>> {
        if (windowEndNs <= 0L || targetSpeedKmh == null) return speedSamples to speedTimes
        if (speedSamples.isEmpty() || speedTimes.isEmpty()) return speedSamples to speedTimes

        val limit = minOf(speedSamples.size, speedTimes.size)
        val alignedSamples = speedSamples.take(limit).toMutableList()
        val alignedTimes = speedTimes.take(limit).toMutableList()

        val hasEndOrAfterPoint = alignedTimes.any { it >= windowEndNs }
        val maxSpeed = alignedSamples.maxOrNull() ?: 0f

        if (!hasEndOrAfterPoint && maxSpeed < targetSpeedKmh) {
            alignedSamples.add(targetSpeedKmh)
            alignedTimes.add(windowEndNs)
        }

        return alignedSamples to alignedTimes
    }

    private fun refreshLiveCompensatedMilestones(force: Boolean = false) {
        val attempt = currentAttempt ?: return
        if (!started && !force) return

        val nowMs = SystemClock.elapsedRealtime()
        if (!force && (nowMs - lastLiveCompensationRefreshMs) < LIVE_COMPENSATION_REFRESH_MS) {
            return
        }
        lastLiveCompensationRefreshMs = nowMs

        val speedSamplesRaw = foregroundService?.getRecentSpeedSamples() ?: emptyList()
        val speedTimeStampsRaw = foregroundService?.getRecentSpeedTimeStamps() ?: emptyList()
        val speedReceiveTimeStampsRaw = foregroundService?.getRecentSpeedReceiveTimeStamps() ?: emptyList()
        val speedSampleAgesRaw = foregroundService?.getRecentSpeedSampleAgeNanos() ?: emptyList()
        val longitudinalAccelSamplesRaw = foregroundService?.getRecentLongitudinalAccelSamples() ?: emptyList()
        val longitudinalAccelTimeStampsRaw = foregroundService?.getRecentLongitudinalAccelTimeStamps() ?: emptyList()

        if (speedSamplesRaw.size < 2 || speedTimeStampsRaw.size < 2) return

        val baseAttempt0to100Result = when (measurementMode) {
            MeasurementMode.ZERO_TO_100,
            MeasurementMode.ZERO_TO_200,
            MeasurementMode.ALL -> attempt0to100Nanos.takeIf { it > 0L } ?: -1L
            else -> -1L
        }

        val baseAttempt0to200Result = when (measurementMode) {
            MeasurementMode.ZERO_TO_200,
            MeasurementMode.ALL -> attempt0to200Nanos.takeIf { it > 0L } ?: -1L
            else -> -1L
        }

        val baseAttempt100to200Result = when (measurementMode) {
            MeasurementMode.HUNDRED_TO_200,
            MeasurementMode.ZERO_TO_200,
            MeasurementMode.ALL -> attempt100to200Nanos.takeIf { it > 0L } ?: -1L
            else -> -1L
        }

        val (windowStartNs, windowEndNs, speedCapKmh) = getMeasurementWindowAndSpeedCap(
            mode = measurementMode,
            attempt = attempt,
            attempt0to100Ns = baseAttempt0to100Result,
            attempt0to200Ns = baseAttempt0to200Result,
            attempt100to200Ns = baseAttempt100to200Result
        )

        val (trimmedSpeedSamplesRaw, trimmedSpeedTimes) = trimTimeSeriesToWindow(
            speedSamplesRaw,
            speedTimeStampsRaw,
            windowStartNs,
            windowEndNs
        )

        if (trimmedSpeedSamplesRaw.size < 2 || trimmedSpeedTimes.size < 2) return

        val (trimmedSpeedReceiveTimes, _) =
            if (speedReceiveTimeStampsRaw.isNotEmpty() && speedTimeStampsRaw.isNotEmpty()) {
                trimTimeSeriesToWindow(
                    speedReceiveTimeStampsRaw,
                    speedTimeStampsRaw,
                    windowStartNs,
                    windowEndNs
                )
            } else {
                emptyList<Long>() to emptyList()
            }

        val (trimmedSpeedSampleAges, _) =
            if (speedSampleAgesRaw.isNotEmpty() && speedTimeStampsRaw.isNotEmpty()) {
                trimTimeSeriesToWindow(
                    speedSampleAgesRaw,
                    speedTimeStampsRaw,
                    windowStartNs,
                    windowEndNs
                )
            } else {
                emptyList<Long>() to emptyList()
            }

        val (alignedLongitudinalAccelSamples, alignedLongitudinalAccelTimes) =
            if (longitudinalAccelSamplesRaw.isNotEmpty() && longitudinalAccelTimeStampsRaw.isNotEmpty()) {
                trimTimeSeriesToWindow(
                    longitudinalAccelSamplesRaw,
                    longitudinalAccelTimeStampsRaw,
                    windowStartNs,
                    windowEndNs
                )
            } else {
                emptyList<Float>() to emptyList<Long>()
            }

        val (adjustedSpeedSamples, adjustedSpeedTimes) = ensureSpeedSeriesCoversMeasurementEnd(
            speedSamples = trimmedSpeedSamplesRaw,
            speedTimes = trimmedSpeedTimes,
            windowEndNs = windowEndNs,
            targetSpeedKmh = speedCapKmh
        )

        val zeroReferenceNs = if (measurementMode == MeasurementMode.HUNDRED_TO_200) {
            attempt.startTime.coerceAtLeast(0L)
        } else {
            0L
        }

        // RaceBox ~25 Hz GNSS speed is truth for timing/chart. Accel fusion invents false
        // 100/200 crossings and wild sector speeds — use GPS-only when IMU overrides phone GPS.
        val useRaceBoxGpsOnly = isRaceBoxGpsStartActive()

        val refinedMilestones = computeLatencyCompensatedMilestones(
            speedSamplesKmh = adjustedSpeedSamples,
            speedTimeStampsNs = adjustedSpeedTimes,
            speedReceiveTimeStampsNs = trimmedSpeedReceiveTimes,
            speedSampleAgeNs = trimmedSpeedSampleAges,
            longitudinalAccelSamplesMps2 = if (useRaceBoxGpsOnly) {
                emptyList()
            } else {
                alignedLongitudinalAccelSamples
            },
            longitudinalAccelTimeStampsNs = if (useRaceBoxGpsOnly) {
                emptyList()
            } else {
                alignedLongitudinalAccelTimes
            },
            zeroReferenceNs = zeroReferenceNs,
            allowAccelFusion = !useRaceBoxGpsOnly
        )

        if (measurementMode != MeasurementMode.HUNDRED_TO_200 &&
            !useRaceBoxGpsOnly &&
            refinedMilestones.fusedSpeedSamplesKmh.size >= 2 &&
            refinedMilestones.fusedSpeedTimeStampsNs.size >= 2
        ) {
            // Persist GPS-heavy series for SPEED chart only; milestone fusion above is unchanged.
            val chartSeries = buildGpsHeavyChartSpeedSeries(
                gpsSpeedSamplesKmh = adjustedSpeedSamples,
                gpsTimeStampsNs = adjustedSpeedTimes,
                fusedSpeedSamplesKmh = refinedMilestones.fusedSpeedSamplesKmh,
                fusedSpeedTimeStampsNs = refinedMilestones.fusedSpeedTimeStampsNs
            )
            val alignedChart = shiftSpeedSeriesByPhoneGpsLag(chartSeries.first, chartSeries.second)
            latestFusedSpeedSamplesKmh = alignedChart.first
            latestFusedSpeedTimeStampsNs = alignedChart.second
        } else if (measurementMode != MeasurementMode.HUNDRED_TO_200) {
            latestFusedSpeedSamplesKmh = emptyList()
            latestFusedSpeedTimeStampsNs = emptyList()
        }

        val refined0to100Ns = refinedMilestones.time0to100Ns
            .takeIf { it > 0L }
        val refined0to200Ns = refinedMilestones.time0to200Ns
            .takeIf { it > 0L }
        val refined0to402Ns = refinedMilestones.time0to402Ns
            .takeIf { it > 0L }

        val refined50mTimeNs = refinedMilestones.distance50m?.timeNs
            ?.takeIf { it > 0L }
        val refined100mTimeNs = refinedMilestones.distance100m?.timeNs
            ?.takeIf { it > 0L }
        val refined200mTimeNs = refinedMilestones.distance200m?.timeNs
            ?.takeIf { it > 0L }
        val refined300mTimeNs = refinedMilestones.distance300m?.timeNs
            ?.takeIf { it > 0L }
        val refined402mTimeNs = refinedMilestones.distance402m?.timeNs
            ?.takeIf { it > 0L }

        val attempt0to100Result = when (measurementMode) {
            MeasurementMode.ZERO_TO_100,
            MeasurementMode.ZERO_TO_200,
            MeasurementMode.ALL -> refined0to100Ns ?: baseAttempt0to100Result
            else -> -1L
        }

        val attempt0to200Result = when (measurementMode) {
            MeasurementMode.ZERO_TO_200,
            MeasurementMode.ALL -> refined0to200Ns ?: baseAttempt0to200Result
            else -> -1L
        }

        val attempt100to200Result = when (measurementMode) {
            MeasurementMode.HUNDRED_TO_200 -> {
                refinedMilestones.time100to200Ns.takeIf { it > 0L } ?: baseAttempt100to200Result
            }
            MeasurementMode.ZERO_TO_200,
            MeasurementMode.ALL -> {
                val delta = if (attempt0to100Result > 0L && attempt0to200Result > attempt0to100Result) {
                    attempt0to200Result - attempt0to100Result
                } else {
                    -1L
                }
                when {
                    delta > 0L -> delta
                    refinedMilestones.time100to200Ns > 0L -> refinedMilestones.time100to200Ns
                    else -> baseAttempt100to200Result
                }
            }
            else -> -1L
        }

        val attempt0to402Result = when (measurementMode) {
            MeasurementMode.QUARTER_MILE,
            MeasurementMode.ALL -> {
                refined0to402Ns ?: (attempt0to402Nanos.takeIf { it > 0L } ?: -1L)
            }
            else -> -1L
        }

        val distance50mTimeNs = refined50mTimeNs ?: sector50TimeNanos.takeIf { it > 0L } ?: -1L
        val distance100mTimeNs = refined100mTimeNs ?: sector100TimeNanos.takeIf { it > 0L } ?: -1L
        val distance200mTimeNs = refined200mTimeNs ?: sector200TimeNanos.takeIf { it > 0L } ?: -1L
        val distance300mTimeNs = refined300mTimeNs ?: sector300TimeNanos.takeIf { it > 0L } ?: -1L
        val distance402mTimeNs = when {
            refined402mTimeNs != null && refined402mTimeNs > 0L -> refined402mTimeNs
            attempt0to402Result > 0L -> attempt0to402Result
            sector402TimeNanos > 0L -> sector402TimeNanos
            else -> -1L
        }

        val distance50mSpeedKmh = refinedMilestones.distance50m?.speedKmh?.takeIf { it >= 0f }
            ?: if (sector50SpeedKmh >= 0f) sector50SpeedKmh else -1f
        val distance100mSpeedKmh = refinedMilestones.distance100m?.speedKmh?.takeIf { it >= 0f }
            ?: if (sector100SpeedKmh >= 0f) sector100SpeedKmh else -1f
        val distance200mSpeedKmh = refinedMilestones.distance200m?.speedKmh?.takeIf { it >= 0f }
            ?: if (sector200SpeedKmh >= 0f) sector200SpeedKmh else -1f
        val distance300mSpeedKmh = refinedMilestones.distance300m?.speedKmh?.takeIf { it >= 0f }
            ?: if (sector300SpeedKmh >= 0f) sector300SpeedKmh else -1f
        val distance402mSpeedKmh = refinedMilestones.distance402m?.speedKmh?.takeIf { it >= 0f }
            ?: if (sector402SpeedKmh >= 0f) sector402SpeedKmh else -1f

        if (!measured0to100 && attempt0to100Result > 0L) attempt0to100Nanos = attempt0to100Result
        if (!measured0to200 && attempt0to200Result > 0L) attempt0to200Nanos = attempt0to200Result
        if (!measured100to200 && attempt100to200Result > 0L) attempt100to200Nanos = attempt100to200Result
        if (!measured0to402 && attempt0to402Nanos <= 0L && attempt0to402Result > 0L) {
            attempt0to402Nanos = attempt0to402Result
        }

        // Lock each distance split on first crossing. Do not keep rewriting live HUD times.
        lockQuarterSectorIfNeeded(sectorTime = { sector50TimeNanos }, setTime = { sector50TimeNanos = it }, sectorSpeed = { sector50SpeedKmh }, setSpeed = { sector50SpeedKmh = it }, timeNs = distance50mTimeNs, speedKmh = distance50mSpeedKmh)
        lockQuarterSectorIfNeeded(sectorTime = { sector100TimeNanos }, setTime = { sector100TimeNanos = it }, sectorSpeed = { sector100SpeedKmh }, setSpeed = { sector100SpeedKmh = it }, timeNs = distance100mTimeNs, speedKmh = distance100mSpeedKmh)
        lockQuarterSectorIfNeeded(sectorTime = { sector200TimeNanos }, setTime = { sector200TimeNanos = it }, sectorSpeed = { sector200SpeedKmh }, setSpeed = { sector200SpeedKmh = it }, timeNs = distance200mTimeNs, speedKmh = distance200mSpeedKmh)
        lockQuarterSectorIfNeeded(sectorTime = { sector300TimeNanos }, setTime = { sector300TimeNanos = it }, sectorSpeed = { sector300SpeedKmh }, setSpeed = { sector300SpeedKmh = it }, timeNs = distance300mTimeNs, speedKmh = distance300mSpeedKmh)
        lockQuarterSectorIfNeeded(sectorTime = { sector402TimeNanos }, setTime = { sector402TimeNanos = it }, sectorSpeed = { sector402SpeedKmh }, setSpeed = { sector402SpeedKmh = it }, timeNs = distance402mTimeNs, speedKmh = distance402mSpeedKmh)
    }

    private fun lockQuarterSectorIfNeeded(
        sectorTime: () -> Long,
        setTime: (Long) -> Unit,
        sectorSpeed: () -> Float,
        setSpeed: (Float) -> Unit,
        timeNs: Long,
        speedKmh: Float
    ) {
        if (sectorTime() > 0L || timeNs <= 0L) return
        setTime(timeNs)
        if (speedKmh >= 0f) setSpeed(speedKmh)
    }

    private fun computeLatencyCompensatedMilestones(
        speedSamplesKmh: List<Float>,
        speedTimeStampsNs: List<Long>,
        speedReceiveTimeStampsNs: List<Long>,
        speedSampleAgeNs: List<Long>,
        longitudinalAccelSamplesMps2: List<Float>,
        longitudinalAccelTimeStampsNs: List<Long>,
        zeroReferenceNs: Long,
        allowAccelFusion: Boolean = true
    ): LatencyCompensatedMilestones {
        val speedLimit = minOf(speedSamplesKmh.size, speedTimeStampsNs.size)
        if (speedLimit < 2) return LatencyCompensatedMilestones()

        val alignedSpeeds = speedSamplesKmh.take(speedLimit)
        val alignedSpeedTimes = speedTimeStampsNs.take(speedLimit)
        val gpsMaxSpeedKmh = alignedSpeeds.maxOrNull() ?: 0f

        // RaceBox path: no latency guesswork — stamps are already relative to official t0.
        val estimatedLatencyNs = if (allowAccelFusion) {
            estimateLatencyCompensationNs(
                speedSamplesKmh = alignedSpeeds,
                speedTimeStampsNs = alignedSpeedTimes,
                speedReceiveTimeStampsNs = speedReceiveTimeStampsNs,
                speedSampleAgeNs = speedSampleAgeNs
            )
        } else {
            0L
        }

        val fusedSeries = if (allowAccelFusion) {
            buildFusedSpeedSeries(
                gpsSpeedSamplesKmh = alignedSpeeds,
                gpsTimeStampsNs = alignedSpeedTimes,
                longitudinalAccelSamplesMps2 = longitudinalAccelSamplesMps2,
                longitudinalAccelTimeStampsNs = longitudinalAccelTimeStampsNs,
                latencyNs = estimatedLatencyNs,
                zeroReferenceNs = zeroReferenceNs
            )
        } else {
            emptyList<Float>() to emptyList()
        }

        val hasFusedSeries = fusedSeries.first.size >= 2 && fusedSeries.second.size >= 2
        val (relativeSpeedsKmhRaw, relativeTimesNs) = if (hasFusedSeries) {
            fusedSeries
        } else {
            buildRelativeSpeedSeriesFromGps(
                gpsSpeedSamplesKmh = alignedSpeeds,
                gpsTimeStampsNs = alignedSpeedTimes,
                latencyNs = estimatedLatencyNs,
                zeroReferenceNs = zeroReferenceNs
            )
        }

        // Phone fusion can overshoot GPS; never invent crossings above what GPS has seen.
        val relativeSpeedsKmh = if (hasFusedSeries) {
            val capKmh = (gpsMaxSpeedKmh + PHONE_FUSION_SPEED_HEADROOM_KMH).coerceAtLeast(gpsMaxSpeedKmh)
            relativeSpeedsKmhRaw.map { it.coerceIn(0f, capKmh) }
        } else {
            relativeSpeedsKmhRaw
        }

        if (relativeSpeedsKmh.size < 2 || relativeTimesNs.size < 2) {
            val raw100Ns = findSpeedCrossingTimeNs(alignedSpeeds, alignedSpeedTimes, 100f)
            val raw200Ns = findSpeedCrossingTimeNs(alignedSpeeds, alignedSpeedTimes, 200f)

            val relative100Ns = toRelativeTimeNsAfterCompensation(raw100Ns, estimatedLatencyNs, zeroReferenceNs)
            val relative200Ns = toRelativeTimeNsAfterCompensation(raw200Ns, estimatedLatencyNs, zeroReferenceNs)

            val relative100to200Ns =
                if (relative100Ns > 0L && relative200Ns > relative100Ns) {
                    relative200Ns - relative100Ns
                } else {
                    -1L
                }

            val distanceCrossings = computeDistanceCrossingsFromSpeed(
                speedSamplesKmh = alignedSpeeds,
                speedTimeStampsNs = alignedSpeedTimes,
                latencyNs = estimatedLatencyNs,
                zeroReferenceNs = zeroReferenceNs,
                targetsMeters = listOf(
                    QUARTER_MILE_SECTOR_50,
                    QUARTER_MILE_SECTOR_100,
                    QUARTER_MILE_SECTOR_200,
                    QUARTER_MILE_SECTOR_300,
                    QUARTER_MILE_SECTOR_402
                )
            )

            return LatencyCompensatedMilestones(
                estimatedLatencyNs = estimatedLatencyNs,
                time0to100Ns = relative100Ns,
                time0to200Ns = relative200Ns,
                time100to200Ns = relative100to200Ns,
                time0to402Ns = distanceCrossings[QUARTER_MILE_SECTOR_402]?.timeNs ?: -1L,
                distance50m = distanceCrossings[QUARTER_MILE_SECTOR_50],
                distance100m = distanceCrossings[QUARTER_MILE_SECTOR_100],
                distance200m = distanceCrossings[QUARTER_MILE_SECTOR_200],
                distance300m = distanceCrossings[QUARTER_MILE_SECTOR_300],
                distance402m = distanceCrossings[QUARTER_MILE_SECTOR_402]
            )
        }

        val relative100Ns = findSpeedCrossingTimeNs(relativeSpeedsKmh, relativeTimesNs, 100f)
        val relative200Ns = findSpeedCrossingTimeNs(relativeSpeedsKmh, relativeTimesNs, 200f)

        val relative100to200Ns =
            if (relative100Ns > 0L && relative200Ns > relative100Ns) {
                relative200Ns - relative100Ns
            } else {
                -1L
            }

        val distanceCrossings = computeDistanceCrossingsFromSpeed(
            speedSamplesKmh = relativeSpeedsKmh,
            speedTimeStampsNs = relativeTimesNs,
            latencyNs = 0L,
            zeroReferenceNs = 0L,
            targetsMeters = listOf(
                QUARTER_MILE_SECTOR_50,
                QUARTER_MILE_SECTOR_100,
                QUARTER_MILE_SECTOR_200,
                QUARTER_MILE_SECTOR_300,
                QUARTER_MILE_SECTOR_402
            )
        )

        return LatencyCompensatedMilestones(
            estimatedLatencyNs = estimatedLatencyNs,
            time0to100Ns = relative100Ns,
            time0to200Ns = relative200Ns,
            time100to200Ns = relative100to200Ns,
            time0to402Ns = distanceCrossings[QUARTER_MILE_SECTOR_402]?.timeNs ?: -1L,
            distance50m = distanceCrossings[QUARTER_MILE_SECTOR_50],
            distance100m = distanceCrossings[QUARTER_MILE_SECTOR_100],
            distance200m = distanceCrossings[QUARTER_MILE_SECTOR_200],
            distance300m = distanceCrossings[QUARTER_MILE_SECTOR_300],
            distance402m = distanceCrossings[QUARTER_MILE_SECTOR_402],
            fusedSpeedSamplesKmh = if (hasFusedSeries) relativeSpeedsKmh else emptyList(),
            fusedSpeedTimeStampsNs = if (hasFusedSeries) relativeTimesNs else emptyList()
        )
    }

    private fun buildRelativeSpeedSeriesFromGps(
        gpsSpeedSamplesKmh: List<Float>,
        gpsTimeStampsNs: List<Long>,
        latencyNs: Long,
        zeroReferenceNs: Long
    ): Pair<List<Float>, List<Long>> {
        val limit = minOf(gpsSpeedSamplesKmh.size, gpsTimeStampsNs.size)
        if (limit < 2) return emptyList<Float>() to emptyList<Long>()

        val relativeSpeeds = mutableListOf<Float>()
        val relativeTimes = mutableListOf<Long>()

        for (i in 0 until limit) {
            val correctedTimeNs = gpsTimeStampsNs[i] - latencyNs - zeroReferenceNs
            if (correctedTimeNs < 0L) continue
            if (relativeTimes.isNotEmpty() && correctedTimeNs <= relativeTimes.last()) continue

            relativeTimes.add(correctedTimeNs)
            relativeSpeeds.add(gpsSpeedSamplesKmh[i].coerceAtLeast(0f))
        }

        return if (relativeSpeeds.size >= 2 && relativeTimes.size >= 2) {
            relativeSpeeds to relativeTimes
        } else {
            emptyList<Float>() to emptyList<Long>()
        }
    }

    /**
     * Display-only SPEED series: mostly GPS, small fusion fill between sparse phone fixes.
     * Does not feed 0–100 / 0–200 / sector timing.
     */
    private fun buildGpsHeavyChartSpeedSeries(
        gpsSpeedSamplesKmh: List<Float>,
        gpsTimeStampsNs: List<Long>,
        fusedSpeedSamplesKmh: List<Float>,
        fusedSpeedTimeStampsNs: List<Long>
    ): Pair<List<Float>, List<Long>> {
        val fusedLimit = minOf(fusedSpeedSamplesKmh.size, fusedSpeedTimeStampsNs.size)
        val gpsLimit = minOf(gpsSpeedSamplesKmh.size, gpsTimeStampsNs.size)
        if (fusedLimit < 2) {
            return gpsSpeedSamplesKmh.take(gpsLimit) to gpsTimeStampsNs.take(gpsLimit)
        }
        if (gpsLimit < 2) {
            return fusedSpeedSamplesKmh.take(fusedLimit) to fusedSpeedTimeStampsNs.take(fusedLimit)
        }

        val gpsTimes = gpsTimeStampsNs.take(gpsLimit)
        val gpsSpeeds = gpsSpeedSamplesKmh.take(gpsLimit)
        val gpsMaxKmh = gpsSpeeds.maxOrNull() ?: 0f
        val chartCapKmh = (gpsMaxKmh + CHART_FUSION_HEADROOM_KMH).coerceAtLeast(gpsMaxKmh)
        val fusionWeight = (1f - CHART_GPS_BLEND_WEIGHT).coerceIn(0f, 1f)

        val chartSpeeds = ArrayList<Float>(fusedLimit)
        val chartTimes = ArrayList<Long>(fusedLimit)
        for (i in 0 until fusedLimit) {
            val timeNs = fusedSpeedTimeStampsNs[i]
            val fusedKmh = fusedSpeedSamplesKmh[i].coerceAtLeast(0f)
            val gpsKmh = interpolateFloatAtTimeNs(
                timestampsNs = gpsTimes,
                values = gpsSpeeds,
                targetTimeNs = timeNs
            )?.coerceAtLeast(0f) ?: fusedKmh
            val blended = (CHART_GPS_BLEND_WEIGHT * gpsKmh + fusionWeight * fusedKmh)
                .coerceIn(0f, chartCapKmh)
            chartSpeeds.add(blended)
            chartTimes.add(timeNs)
        }
        return chartSpeeds to chartTimes
    }

    private fun buildFusedSpeedSeries(
        gpsSpeedSamplesKmh: List<Float>,
        gpsTimeStampsNs: List<Long>,
        longitudinalAccelSamplesMps2: List<Float>,
        longitudinalAccelTimeStampsNs: List<Long>,
        latencyNs: Long,
        zeroReferenceNs: Long
    ): Pair<List<Float>, List<Long>> {
        val anchors = buildSpeedAnchorsFromGps(
            gpsSpeedSamplesKmh = gpsSpeedSamplesKmh,
            gpsTimeStampsNs = gpsTimeStampsNs,
            latencyNs = latencyNs,
            zeroReferenceNs = zeroReferenceNs
        )
        if (anchors.size < 2) return emptyList<Float>() to emptyList<Long>()

        val startTimeNs = anchors.first().timeNs
        val endTimeNs = anchors.last().timeNs
        if (endTimeNs <= startTimeNs) return emptyList<Float>() to emptyList<Long>()

        val gridTimesNs = mutableListOf<Long>()
        var timeNs = startTimeNs
        while (timeNs < endTimeNs) {
            gridTimesNs.add(timeNs)
            timeNs += FUSION_TIMELINE_STEP_NS
        }
        if (gridTimesNs.isEmpty() || gridTimesNs.last() != endTimeNs) {
            gridTimesNs.add(endTimeNs)
        }

        val accelLimit = minOf(longitudinalAccelSamplesMps2.size, longitudinalAccelTimeStampsNs.size)
        if (accelLimit < 2) {
            return resampleAnchorSpeedSeriesOnGrid(anchors, gridTimesNs)
        }

        val accelPairs = mutableListOf<Pair<Long, Float>>()
        val accelMinTimeNs = startTimeNs - FUSION_ACCEL_CONTEXT_NS
        val accelMaxTimeNs = endTimeNs + FUSION_ACCEL_CONTEXT_NS

        for (i in 0 until accelLimit) {
            val relativeTimeNs = longitudinalAccelTimeStampsNs[i] - zeroReferenceNs
            if (relativeTimeNs !in accelMinTimeNs..accelMaxTimeNs) continue
            accelPairs.add(relativeTimeNs to longitudinalAccelSamplesMps2[i])
        }

        if (accelPairs.size < 2) {
            return resampleAnchorSpeedSeriesOnGrid(anchors, gridTimesNs)
        }

        val sortedAccelPairs = accelPairs.sortedBy { it.first }
        val accelTimesNs = sortedAccelPairs.map { it.first }
        val accelValuesMps2 = sortedAccelPairs.map { it.second }

        val biasWindowEndNs = (startTimeNs + FUSION_ACCEL_BIAS_WINDOW_NS).coerceAtMost(endTimeNs)
        val biasCandidates = sortedAccelPairs
            .filter { (ts, _) -> ts in startTimeNs..biasWindowEndNs }
            .map { it.second }
        val accelBiasMps2 = medianFloat(biasCandidates)
            .coerceIn(-FUSION_ACCEL_BIAS_CLAMP_MPS2, FUSION_ACCEL_BIAS_CLAMP_MPS2)

        val timelineNs = (gridTimesNs + anchors.map { it.timeNs })
            .distinct()
            .sorted()

        if (timelineNs.size < 2) {
            return resampleAnchorSpeedSeriesOnGrid(anchors, gridTimesNs)
        }

        val rawAccelMps2 = timelineNs.map { ts ->
            val interpolated = interpolateFloatAtTimeNs(
                timestampsNs = accelTimesNs,
                values = accelValuesMps2,
                targetTimeNs = ts
            ) ?: accelValuesMps2.last()

            (interpolated - accelBiasMps2).toDouble()
        }

        val intervalCorrections = MutableList(anchors.size - 1) { 0.0 }
        for (intervalIndex in 0 until anchors.lastIndex) {
            val startAnchor = anchors[intervalIndex]
            val endAnchor = anchors[intervalIndex + 1]
            val startIndex = timelineNs.binarySearch(startAnchor.timeNs)
            val endIndex = timelineNs.binarySearch(endAnchor.timeNs)
            if (startIndex < 0 || endIndex < 0 || endIndex <= startIndex) continue

            val durationNs = endAnchor.timeNs - startAnchor.timeNs
            if (durationNs <= 0L) continue

            val targetDeltaV = endAnchor.speedMps - startAnchor.speedMps
            val rawDeltaV = integrateAccelerationOverRange(
                accelerationMps2 = rawAccelMps2,
                timelineNs = timelineNs,
                startIndex = startIndex,
                endIndex = endIndex
            )
            val durationSec = durationNs / 1_000_000_000.0
            if (durationSec <= 0.0) continue

            intervalCorrections[intervalIndex] = ((targetDeltaV - rawDeltaV) / durationSec)
                .coerceIn(-FUSION_CORRECTION_CLAMP_MPS2, FUSION_CORRECTION_CLAMP_MPS2)
        }

        repeat(FUSION_CORRECTION_SMOOTHING_PASSES) {
            if (intervalCorrections.size <= 1) return@repeat
            val previous = intervalCorrections.toList()
            for (index in previous.indices) {
                val left = if (index > 0) previous[index - 1] else previous[index]
                val center = previous[index]
                val right = if (index < previous.lastIndex) previous[index + 1] else previous[index]
                intervalCorrections[index] = (left + center * 2.0 + right) * 0.25
            }
        }

        val fusedSpeedMps = MutableList(timelineNs.size) { 0.0 }
        fusedSpeedMps[0] = anchors.first().speedMps.coerceAtLeast(0.0)

        for (index in 1 until timelineNs.size) {
            val previousTimeNs = timelineNs[index - 1]
            val currentTimeNs = timelineNs[index]
            val dtSec = (currentTimeNs - previousTimeNs) / 1_000_000_000.0
            if (dtSec <= 0.0) {
                fusedSpeedMps[index] = fusedSpeedMps[index - 1]
                continue
            }

            val intervalIndex = findAnchorIntervalForTimeNs(anchors, previousTimeNs)
            val intervalCorrectionMps2 = if (intervalIndex in intervalCorrections.indices) {
                intervalCorrections[intervalIndex]
            } else {
                0.0
            }

            val a0 = rawAccelMps2[index - 1] + intervalCorrectionMps2
            val a1 = rawAccelMps2[index] + intervalCorrectionMps2

            fusedSpeedMps[index] = (fusedSpeedMps[index - 1] + ((a0 + a1) * 0.5 * dtSec))
                .coerceAtLeast(0.0)
        }

        reanchorSpeedSeries(
            speedMps = fusedSpeedMps,
            timelineNs = timelineNs,
            anchors = anchors
        )

        val smoothedSpeedMps = applyZeroPhaseSpeedSmoothing(
            speedMps = fusedSpeedMps,
            alpha = FUSION_ZERO_PHASE_ALPHA
        )

        reanchorSpeedSeries(
            speedMps = smoothedSpeedMps,
            timelineNs = timelineNs,
            anchors = anchors
        )

        val timelineSpeedsKmh = smoothedSpeedMps.map { (it * 3.6).toFloat().coerceAtLeast(0f) }
        val fusedGridSpeedsKmh = gridTimesNs.map { ts ->
            interpolateFloatAtTimeNs(
                timestampsNs = timelineNs,
                values = timelineSpeedsKmh,
                targetTimeNs = ts
            ) ?: timelineSpeedsKmh.last()
        }.map { it.coerceAtLeast(0f) }

        return fusedGridSpeedsKmh to gridTimesNs
    }

    private fun buildSpeedAnchorsFromGps(
        gpsSpeedSamplesKmh: List<Float>,
        gpsTimeStampsNs: List<Long>,
        latencyNs: Long,
        zeroReferenceNs: Long
    ): List<SpeedAnchor> {
        val limit = minOf(gpsSpeedSamplesKmh.size, gpsTimeStampsNs.size)
        if (limit < 2) return emptyList()

        val rawAnchors = mutableListOf<SpeedAnchor>()
        for (i in 0 until limit) {
            val correctedRelativeTimeNs = gpsTimeStampsNs[i] - latencyNs - zeroReferenceNs
            val speedMps = (gpsSpeedSamplesKmh[i].coerceAtLeast(0f) * KMH_TO_MPS.toFloat()).toDouble()
            rawAnchors.add(SpeedAnchor(correctedRelativeTimeNs, speedMps))
        }

        if (rawAnchors.size < 2) return emptyList()

        val deduplicatedAnchors = mutableListOf<SpeedAnchor>()
        rawAnchors.sortedBy { it.timeNs }.forEach { point ->
            if (deduplicatedAnchors.isNotEmpty() && point.timeNs == deduplicatedAnchors.last().timeNs) {
                deduplicatedAnchors[deduplicatedAnchors.lastIndex] = point
            } else if (deduplicatedAnchors.isEmpty() || point.timeNs > deduplicatedAnchors.last().timeNs) {
                deduplicatedAnchors.add(point)
            }
        }

        if (deduplicatedAnchors.size < 2) return emptyList()

        val nonNegativeAnchors = deduplicatedAnchors.filter { it.timeNs >= 0L }.toMutableList()
        if (nonNegativeAnchors.isEmpty()) return emptyList()

        val speedAtZeroMps = interpolateAnchorSpeedAtTimeNs(deduplicatedAnchors, 0L)
        when {
            nonNegativeAnchors.first().timeNs > 0L -> {
                nonNegativeAnchors.add(0, SpeedAnchor(0L, speedAtZeroMps))
            }
            nonNegativeAnchors.first().timeNs == 0L -> {
                nonNegativeAnchors[0] = SpeedAnchor(0L, speedAtZeroMps)
            }
        }

        return nonNegativeAnchors
    }

    private fun interpolateAnchorSpeedAtTimeNs(
        anchors: List<SpeedAnchor>,
        targetTimeNs: Long
    ): Double {
        if (anchors.isEmpty()) return 0.0
        if (anchors.size == 1) return anchors.first().speedMps

        val anchorTimes = anchors.map { it.timeNs }
        val anchorSpeeds = anchors.map { it.speedMps.toFloat() }

        return (interpolateFloatAtTimeNs(
            timestampsNs = anchorTimes,
            values = anchorSpeeds,
            targetTimeNs = targetTimeNs
        ) ?: anchorSpeeds.last()).toDouble()
    }

    private fun resampleAnchorSpeedSeriesOnGrid(
        anchors: List<SpeedAnchor>,
        gridTimesNs: List<Long>
    ): Pair<List<Float>, List<Long>> {
        if (anchors.size < 2 || gridTimesNs.size < 2) return emptyList<Float>() to emptyList<Long>()

        val anchorTimes = anchors.map { it.timeNs }
        val anchorSpeedsKmh = anchors.map { (it.speedMps * 3.6).toFloat().coerceAtLeast(0f) }

        val samplesKmh = gridTimesNs.map { timeNs ->
            interpolateFloatAtTimeNs(
                timestampsNs = anchorTimes,
                values = anchorSpeedsKmh,
                targetTimeNs = timeNs
            ) ?: anchorSpeedsKmh.last()
        }.map { it.coerceAtLeast(0f) }

        return samplesKmh to gridTimesNs
    }

    private fun integrateAccelerationOverRange(
        accelerationMps2: List<Double>,
        timelineNs: List<Long>,
        startIndex: Int,
        endIndex: Int
    ): Double {
        if (startIndex < 0 || endIndex <= startIndex) return 0.0
        if (endIndex >= accelerationMps2.size || endIndex >= timelineNs.size) return 0.0

        var deltaV = 0.0
        for (index in (startIndex + 1)..endIndex) {
            val dtSec = (timelineNs[index] - timelineNs[index - 1]) / 1_000_000_000.0
            if (dtSec <= 0.0) continue
            deltaV += (accelerationMps2[index - 1] + accelerationMps2[index]) * 0.5 * dtSec
        }
        return deltaV
    }

    private fun findAnchorIntervalForTimeNs(
        anchors: List<SpeedAnchor>,
        timeNs: Long
    ): Int {
        if (anchors.size < 2) return -1
        if (timeNs <= anchors.first().timeNs) return 0

        for (index in 0 until anchors.lastIndex) {
            if (timeNs < anchors[index + 1].timeNs) {
                return index
            }
        }

        return anchors.lastIndex - 1
    }

    private fun reanchorSpeedSeries(
        speedMps: MutableList<Double>,
        timelineNs: List<Long>,
        anchors: List<SpeedAnchor>
    ) {
        if (speedMps.size < 2 || timelineNs.size < 2 || anchors.size < 2) return

        for (anchorIndex in 0 until anchors.lastIndex) {
            val startAnchor = anchors[anchorIndex]
            val endAnchor = anchors[anchorIndex + 1]

            val startIndex = timelineNs.binarySearch(startAnchor.timeNs)
            val endIndex = timelineNs.binarySearch(endAnchor.timeNs)
            if (startIndex < 0 || endIndex < 0 || endIndex <= startIndex) continue

            speedMps[startIndex] = startAnchor.speedMps.coerceAtLeast(0.0)

            val targetEndSpeed = endAnchor.speedMps.coerceAtLeast(0.0)
            val currentEndSpeed = speedMps[endIndex]
            val correction = targetEndSpeed - currentEndSpeed

            val durationNs = (endAnchor.timeNs - startAnchor.timeNs).toDouble()
            if (durationNs <= 0.0) {
                speedMps[endIndex] = targetEndSpeed
                continue
            }

            for (index in startIndex..endIndex) {
                val ratio = ((timelineNs[index] - startAnchor.timeNs).toDouble() / durationNs)
                    .coerceIn(0.0, 1.0)
                speedMps[index] = (speedMps[index] + correction * ratio).coerceAtLeast(0.0)
            }

            speedMps[endIndex] = targetEndSpeed
        }
    }

    private fun applyZeroPhaseSpeedSmoothing(
        speedMps: List<Double>,
        alpha: Double
    ): MutableList<Double> {
        val size = speedMps.size
        if (size <= 2) return speedMps.toMutableList()

        val smoothingAlpha = alpha.coerceIn(0.01, 0.99)
        val forward = MutableList(size) { 0.0 }
        forward[0] = speedMps[0].coerceAtLeast(0.0)

        for (index in 1 until size) {
            forward[index] = (smoothingAlpha * speedMps[index]) +
                ((1.0 - smoothingAlpha) * forward[index - 1])
        }

        val backward = MutableList(size) { 0.0 }
        backward[size - 1] = forward[size - 1].coerceAtLeast(0.0)

        for (index in (size - 2) downTo 0) {
            backward[index] = (smoothingAlpha * forward[index]) +
                ((1.0 - smoothingAlpha) * backward[index + 1])
        }

        for (index in backward.indices) {
            backward[index] = backward[index].coerceAtLeast(0.0)
        }

        return backward
    }

    private fun medianFloat(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) * 0.5f
        }
    }

    private fun estimateLatencyCompensationNs(
        speedSamplesKmh: List<Float>,
        speedTimeStampsNs: List<Long>,
        speedReceiveTimeStampsNs: List<Long>,
        speedSampleAgeNs: List<Long>
    ): Long {
        val speedLimit = minOf(speedSamplesKmh.size, speedTimeStampsNs.size)
        if (speedLimit < LATENCY_MIN_SAMPLES) return 0L

        val ageByFixTimeNs = buildSpeedSampleAgesByFixTimeNs(
            speedTimeStampsNs = speedTimeStampsNs,
            speedReceiveTimeStampsNs = speedReceiveTimeStampsNs,
            speedSampleAgeNs = speedSampleAgeNs
        )
        if (ageByFixTimeNs.size < LATENCY_MIN_SAMPLES) return 0L

        val firstFixNs = speedTimeStampsNs.firstOrNull()?.coerceAtLeast(0L) ?: return 0L
        val baselineEndNs = firstFixNs + LATENCY_BASELINE_WINDOW_NS

        val baselineAges = ageByFixTimeNs
            .asSequence()
            .filter { it.first <= baselineEndNs }
            .map { it.second }
            .toList()

        val fallbackBaseline = ageByFixTimeNs
            .take(minOf(ageByFixTimeNs.size, LATENCY_MIN_SAMPLES + 1))
            .map { it.second }

        val baselineAgeNs = medianLong(
            if (baselineAges.size >= 2) baselineAges else fallbackBaseline
        )
        if (baselineAgeNs <= 0L) return 0L

        val crossingReferenceNs = listOf(
            findSpeedCrossingTimeNs(speedSamplesKmh, speedTimeStampsNs, 100f),
            findSpeedCrossingTimeNs(speedSamplesKmh, speedTimeStampsNs, 200f)
        ).firstOrNull { it > 0L } ?: speedTimeStampsNs.last()

        val localAgeNs = estimateLocalAgeAtTimeNs(
            ageByFixTimeNs = ageByFixTimeNs,
            targetTimeNs = crossingReferenceNs
        )

        val startExcessNs = (firstFixNs - baselineAgeNs).coerceIn(0L, LATENCY_MAX_START_EXCESS_NS)
        val ageDriftNs = (localAgeNs - baselineAgeNs).coerceIn(0L, LATENCY_MAX_DRIFT_NS)

        val blendedLatencyNs = (
            ageDriftNs.toDouble() * 0.7 +
                startExcessNs.toDouble() * 0.3
            ).toLong()

        return blendedLatencyNs.coerceIn(0L, LATENCY_MAX_COMPENSATION_NS)
    }

    private fun buildSpeedSampleAgesByFixTimeNs(
        speedTimeStampsNs: List<Long>,
        speedReceiveTimeStampsNs: List<Long>,
        speedSampleAgeNs: List<Long>
    ): List<Pair<Long, Long>> {
        val fromAges = mutableListOf<Pair<Long, Long>>()
        val ageLimit = minOf(speedTimeStampsNs.size, speedSampleAgeNs.size)
        for (i in 0 until ageLimit) {
            val fixNs = speedTimeStampsNs[i].coerceAtLeast(0L)
            val ageNs = speedSampleAgeNs[i].coerceIn(0L, LATENCY_MAX_SAMPLE_AGE_NS)
            fromAges.add(fixNs to ageNs)
        }

        if (fromAges.size >= LATENCY_MIN_SAMPLES) {
            return fromAges
        }

        val derivedAges = mutableListOf<Pair<Long, Long>>()
        val derivedLimit = minOf(speedTimeStampsNs.size, speedReceiveTimeStampsNs.size)
        for (i in 0 until derivedLimit) {
            val fixNs = speedTimeStampsNs[i].coerceAtLeast(0L)
            val receiveNs = speedReceiveTimeStampsNs[i].coerceAtLeast(0L)
            val ageNs = (receiveNs - fixNs).coerceIn(0L, LATENCY_MAX_SAMPLE_AGE_NS)
            derivedAges.add(fixNs to ageNs)
        }

        return derivedAges
    }

    private fun estimateLocalAgeAtTimeNs(
        ageByFixTimeNs: List<Pair<Long, Long>>,
        targetTimeNs: Long
    ): Long {
        if (ageByFixTimeNs.isEmpty()) return 0L

        val localWindowAges = ageByFixTimeNs
            .asSequence()
            .filter { kotlin.math.abs(it.first - targetTimeNs) <= LATENCY_LOCAL_WINDOW_NS }
            .map { it.second }
            .toList()

        if (localWindowAges.size >= 2) {
            return medianLong(localWindowAges)
        }

        return interpolateLongSeriesAtTimeNs(
            seriesByTimeNs = ageByFixTimeNs,
            targetTimeNs = targetTimeNs
        ) ?: medianLong(ageByFixTimeNs.takeLast(minOf(4, ageByFixTimeNs.size)).map { it.second })
    }

    private fun medianLong(values: List<Long>): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2L
        }
    }

    private fun interpolateLongSeriesAtTimeNs(
        seriesByTimeNs: List<Pair<Long, Long>>,
        targetTimeNs: Long
    ): Long? {
        if (seriesByTimeNs.isEmpty()) return null
        if (seriesByTimeNs.size == 1) return seriesByTimeNs.first().second

        if (targetTimeNs <= seriesByTimeNs.first().first) return seriesByTimeNs.first().second
        if (targetTimeNs >= seriesByTimeNs.last().first) return seriesByTimeNs.last().second

        var low = 0
        var high = seriesByTimeNs.lastIndex

        while (low <= high) {
            val mid = (low + high) ushr 1
            val midTime = seriesByTimeNs[mid].first
            when {
                midTime < targetTimeNs -> low = mid + 1
                midTime > targetTimeNs -> high = mid - 1
                else -> return seriesByTimeNs[mid].second
            }
        }

        val right = low.coerceIn(1, seriesByTimeNs.lastIndex)
        val left = right - 1

        val (leftTime, leftValue) = seriesByTimeNs[left]
        val (rightTime, rightValue) = seriesByTimeNs[right]
        if (rightTime <= leftTime) return rightValue

        val ratio = ((targetTimeNs - leftTime).toDouble() / (rightTime - leftTime).toDouble())
            .coerceIn(0.0, 1.0)

        return (leftValue + ((rightValue - leftValue) * ratio)).toLong()
    }

    private fun findSpeedCrossingTimeNs(
        speedSamplesKmh: List<Float>,
        speedTimeStampsNs: List<Long>,
        targetSpeedKmh: Float
    ): Long {
        val limit = minOf(speedSamplesKmh.size, speedTimeStampsNs.size)
        if (limit < 2) return -1L

        for (i in 1 until limit) {
            val v0 = speedSamplesKmh[i - 1]
            val v1 = speedSamplesKmh[i]
            val t0 = speedTimeStampsNs[i - 1]
            val t1 = speedTimeStampsNs[i]

            if (v0 < targetSpeedKmh && v1 >= targetSpeedKmh) {
                val linearCrossingNs = interpolateCrossingTimeLinearNs(
                    v0 = v0,
                    v1 = v1,
                    t0 = t0,
                    t1 = t1,
                    targetSpeedKmh = targetSpeedKmh
                )
                return linearCrossingNs
            }
        }

        return -1L
    }

    private fun interpolateCrossingTimeLinearNs(
        v0: Float,
        v1: Float,
        t0: Long,
        t1: Long,
        targetSpeedKmh: Float
    ): Long {
        if (t1 <= t0) return t0
        if (v1 == v0) return t1
        val ratio = ((targetSpeedKmh - v0) / (v1 - v0)).coerceIn(0f, 1f)
        return t0 + ((t1 - t0) * ratio).toLong()
    }

    private fun interpolateCrossingTimeHermiteNs(
        speedSamplesKmh: List<Float>,
        speedTimeStampsNs: List<Long>,
        leftIndex: Int,
        rightIndex: Int,
        targetSpeedKmh: Float,
        linearFallbackNs: Long
    ): Long {
        val limit = minOf(speedSamplesKmh.size, speedTimeStampsNs.size)
        if (limit < 3) return linearFallbackNs
        if (leftIndex !in 0 until limit || rightIndex !in 0 until limit || rightIndex <= leftIndex) {
            return linearFallbackNs
        }

        val t0 = speedTimeStampsNs[leftIndex]
        val t1 = speedTimeStampsNs[rightIndex]
        val dtNs = t1 - t0
        if (dtNs <= SMART_INTERPOLATION_GAP_NS) return linearFallbackNs

        val v0 = speedSamplesKmh[leftIndex].toDouble()
        val v1 = speedSamplesKmh[rightIndex].toDouble()
        val target = targetSpeedKmh.toDouble()
        if (target <= v0 || target > v1) return linearFallbackNs

        val slope0 = estimateSpeedSlopeKmhPerNs(
            speedSamplesKmh = speedSamplesKmh,
            speedTimeStampsNs = speedTimeStampsNs,
            index = leftIndex
        )
        val slope1 = estimateSpeedSlopeKmhPerNs(
            speedSamplesKmh = speedSamplesKmh,
            speedTimeStampsNs = speedTimeStampsNs,
            index = rightIndex
        )
        if (!slope0.isFinite() || !slope1.isFinite()) return linearFallbackNs

        val dt = dtNs.toDouble()
        val chordSlope = (v1 - v0) / dt
        if (!chordSlope.isFinite() || chordSlope <= 0.0) return linearFallbackNs

        val boundedSlope0 = slope0.coerceIn(0.0, chordSlope * 3.0)
        val boundedSlope1 = slope1.coerceIn(0.0, chordSlope * 3.0)

        val minTarget = minOf(v0, v1)
        val maxTarget = maxOf(v0, v1)
        if (target !in minTarget..maxTarget) return linearFallbackNs

        var low = 0.0
        var high = 1.0
        repeat(30) {
            val mid = (low + high) * 0.5
            val midSpeed = hermiteSpeedAt(
                normalizedTime = mid,
                startSpeed = v0,
                endSpeed = v1,
                startSlope = boundedSlope0,
                endSlope = boundedSlope1,
                dtNs = dt
            )
            if (midSpeed < target) {
                low = mid
            } else {
                high = mid
            }
        }

        val normalizedCrossing = (low + high) * 0.5
        return (t0 + (dtNs * normalizedCrossing).toLong()).coerceIn(t0, t1)
    }

    private fun estimateSpeedSlopeKmhPerNs(
        speedSamplesKmh: List<Float>,
        speedTimeStampsNs: List<Long>,
        index: Int
    ): Double {
        val limit = minOf(speedSamplesKmh.size, speedTimeStampsNs.size)
        if (limit < 2) return 0.0

        fun slopeBetween(left: Int, right: Int): Double {
            if (left !in 0 until limit || right !in 0 until limit || right <= left) return Double.NaN
            val dtNs = (speedTimeStampsNs[right] - speedTimeStampsNs[left]).toDouble()
            if (dtNs <= 0.0) return Double.NaN
            val dv = (speedSamplesKmh[right] - speedSamplesKmh[left]).toDouble()
            return dv / dtNs
        }

        if (index <= 0) return slopeBetween(0, 1)
        if (index >= limit - 1) return slopeBetween(limit - 2, limit - 1)

        val leftSlope = slopeBetween(index - 1, index)
        val rightSlope = slopeBetween(index, index + 1)

        if (!leftSlope.isFinite() && !rightSlope.isFinite()) return 0.0
        if (!leftSlope.isFinite()) return rightSlope
        if (!rightSlope.isFinite()) return leftSlope

        return if (leftSlope < 0.0 || rightSlope < 0.0) {
            maxOf(0.0, minOf(leftSlope, rightSlope))
        } else {
            (leftSlope + rightSlope) * 0.5
        }
    }

    private fun hermiteSpeedAt(
        normalizedTime: Double,
        startSpeed: Double,
        endSpeed: Double,
        startSlope: Double,
        endSlope: Double,
        dtNs: Double
    ): Double {
        val u = normalizedTime.coerceIn(0.0, 1.0)
        val u2 = u * u
        val u3 = u2 * u

        val h00 = 2.0 * u3 - 3.0 * u2 + 1.0
        val h10 = u3 - 2.0 * u2 + u
        val h01 = -2.0 * u3 + 3.0 * u2
        val h11 = u3 - u2

        return h00 * startSpeed +
            h10 * (startSlope * dtNs) +
            h01 * endSpeed +
            h11 * (endSlope * dtNs)
    }

    private fun interpolateFloatAtTimeNs(
        timestampsNs: List<Long>,
        values: List<Float>,
        targetTimeNs: Long
    ): Float? {
        val limit = minOf(timestampsNs.size, values.size)
        if (limit <= 0) return null
        if (limit == 1) return values.first()

        if (targetTimeNs <= timestampsNs.first()) return values.first()
        if (targetTimeNs >= timestampsNs[limit - 1]) return values[limit - 1]

        var low = 0
        var high = limit - 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val midTime = timestampsNs[mid]
            when {
                midTime < targetTimeNs -> low = mid + 1
                midTime > targetTimeNs -> high = mid - 1
                else -> return values[mid]
            }
        }

        val right = low.coerceIn(1, limit - 1)
        val left = right - 1
        val t0 = timestampsNs[left]
        val t1 = timestampsNs[right]
        if (t1 <= t0) return values[right]

        val ratio = ((targetTimeNs - t0).toDouble() / (t1 - t0).toDouble()).toFloat().coerceIn(0f, 1f)
        val v0 = values[left]
        val v1 = values[right]
        return v0 + (v1 - v0) * ratio
    }

    private fun computeDistanceCrossingsFromSpeed(
        speedSamplesKmh: List<Float>,
        speedTimeStampsNs: List<Long>,
        latencyNs: Long,
        zeroReferenceNs: Long,
        targetsMeters: List<Float>
    ): Map<Float, DistanceCrossingPoint> {
        val limit = minOf(speedSamplesKmh.size, speedTimeStampsNs.size)
        if (limit < 2) return emptyMap()

        val correctedPoints = mutableListOf<Pair<Long, Float>>()
        for (i in 0 until limit) {
            val correctedTimeNs = speedTimeStampsNs[i] - latencyNs
            if (correctedTimeNs >= zeroReferenceNs) {
                correctedPoints.add(correctedTimeNs to speedSamplesKmh[i])
            }
        }

        if (correctedPoints.isEmpty()) return emptyMap()
        correctedPoints.sortBy { it.first }

        val sortedTargets = targetsMeters.sorted()
        val results = mutableMapOf<Float, DistanceCrossingPoint>()
        var targetIndex = 0
        var accumulatedDistanceMeters = 0f

        var prevTimeNs = correctedPoints.first().first
        var prevSpeedKmh = correctedPoints.first().second.coerceAtLeast(0f)

        for (i in 1 until correctedPoints.size) {
            val currentTimeNs = correctedPoints[i].first
            val currentSpeedKmh = correctedPoints[i].second.coerceAtLeast(0f)
            val dtNs = currentTimeNs - prevTimeNs
            if (dtNs <= 0L) {
                prevTimeNs = currentTimeNs
                prevSpeedKmh = currentSpeedKmh
                continue
            }

            val dtSec = dtNs / 1_000_000_000f
            val prevSpeedMps = prevSpeedKmh * KMH_TO_MPS.toFloat()
            val currentSpeedMps = currentSpeedKmh * KMH_TO_MPS.toFloat()
            val segmentDistanceMeters = ((prevSpeedMps + currentSpeedMps) * 0.5f * dtSec).coerceAtLeast(0f)
            val nextAccumulatedDistance = accumulatedDistanceMeters + segmentDistanceMeters

            while (targetIndex < sortedTargets.size && nextAccumulatedDistance >= sortedTargets[targetIndex]) {
                val targetDistance = sortedTargets[targetIndex]
                val ratio = if (segmentDistanceMeters > 1e-6f) {
                    ((targetDistance - accumulatedDistanceMeters) / segmentDistanceMeters).coerceIn(0f, 1f)
                } else {
                    0f
                }

                val crossingTimeAbsNs = prevTimeNs + ((currentTimeNs - prevTimeNs) * ratio).toLong()
                val crossingSpeedKmh = prevSpeedKmh + (currentSpeedKmh - prevSpeedKmh) * ratio
                val crossingTimeRelativeNs = (crossingTimeAbsNs - zeroReferenceNs).coerceAtLeast(0L)

                results[targetDistance] = DistanceCrossingPoint(
                    timeNs = crossingTimeRelativeNs,
                    speedKmh = crossingSpeedKmh
                )
                targetIndex++
            }

            accumulatedDistanceMeters = nextAccumulatedDistance
            prevTimeNs = currentTimeNs
            prevSpeedKmh = currentSpeedKmh

            if (targetIndex >= sortedTargets.size) break
        }

        return results
    }

    private fun toRelativeTimeNsAfterCompensation(
        rawCrossingTimeNs: Long,
        latencyNs: Long,
        zeroReferenceNs: Long
    ): Long {
        if (rawCrossingTimeNs <= 0L) return -1L

        val correctedAbsoluteNs = rawCrossingTimeNs - latencyNs
        if (correctedAbsoluteNs <= zeroReferenceNs) return -1L

        return correctedAbsoluteNs - zeroReferenceNs
    }

    private fun isLinearAccelTriggerFresh(): Boolean {
        val triggerTimeNs = foregroundService?.getLinearAccelTriggerTime() ?: 0L
        if (triggerTimeNs <= 0L) return false
        val ageNs = System.nanoTime() - triggerTimeNs
        return ageNs in 0L..TRIGGER_FRESHNESS_NS
    }

    private fun hasCurrentAttemptProgress(): Boolean {
        return when (measurementMode) {
            MeasurementMode.ZERO_TO_100 -> attempt0to100Nanos > 0L
            MeasurementMode.ZERO_TO_200 -> attempt0to200Nanos > 0L
            MeasurementMode.QUARTER_MILE -> attempt0to402Nanos > 0L || accumulatedDistance >= TARGET_METERS
            MeasurementMode.ALL -> attempt0to100Nanos > 0L || attempt0to200Nanos > 0L ||
                attempt100to200Nanos > 0L || attempt0to402Nanos > 0L || accumulatedDistance >= TARGET_METERS
            else -> false
        }
    }

    private fun maybeAbortFailedLaunch(loc: Location, speedKmh: Float) {
        if (!started || measurementComplete || waitingForFullStop) return
        val measurementStartNs = foregroundService?.getMeasurementStartTimeNano() ?: 0L
        if (measurementStartNs <= 0L) return
        val elapsedSinceStartNs = System.nanoTime() - measurementStartNs
        if (elapsedSinceStartNs < FAIL_START_GRACE_NS) return
        if (hasCurrentAttemptProgress()) return

        val imuOnGas = currentForwardAccelG()?.let { it > REARM_ON_GAS_G } == true
        val abortForNoLaunch = !launchSpeedConfirmed && !imuOnGas
        val abortForSpeedDrop = isConfirmedFullStop(loc, speedKmh)
        if (abortForNoLaunch || abortForSpeedDrop) {
            handleSingleModeFailStartStop()
        }
    }

    private fun releaseInvalidLinearAccelTriggerIfNeeded(speedKmh: Float) {
        if (isRaceBoxGpsStartActive()) return
        // While integrating 1ft, official trigger is not set yet — do not touch.
        if (foregroundService?.isPhoneLaunchRolloutPending() == true) return
        val triggered = foregroundService?.isLinearAccelTriggered() ?: false
        if (!triggered) return
        if (!isLinearAccelTriggerFresh()) {
            foregroundService?.resetLinearAccelTrigger()
            return
        }
        // With 1ft ON, after ~30 cm speed is often > 3 km/h — clearing here would drop t0.
        // With 1ft OFF, reject a latched trigger while already rolling.
        if (!DragRolloutSettings.is1ftRolloutEnabled(this) && speedKmh > FULL_STOP_REARM_SPEED_KMH) {
            foregroundService?.resetLinearAccelTrigger()
        }
    }

    /**
     * Phone standing start: wait for official accel t0 (after optional 1ft integration).
     * GPS is not used for t0 — only to require a prior full stop, then for speed/distance.
     */
    private fun tryBeginPhoneStandingStart(
        loc: Location,
        speedKmh: Float,
        logSuffix: String,
        statusText: String
    ) {
        if (!hasUsableDragCalibration()) {
            Log.d("DragRunPage", "❌ DragCalibration NOT calibrated$logSuffix")
            return
        }

        if (!phoneStationaryReady) {
            if (foregroundService?.isLinearAccelTriggered() == true ||
                foregroundService?.isPhoneLaunchRolloutPending() == true
            ) {
                foregroundService?.resetLinearAccelTrigger()
            }
            return
        }

        if (foregroundService?.isPhoneLaunchRolloutPending() == true) {
            if (System.currentTimeMillis() % 2000 < 50) {
                Log.d("DragRunPage", "⏳ Phone 1ft rollout integrating...$logSuffix")
            }
            return
        }

        val linearAccelTriggered = foregroundService?.isLinearAccelTriggered() ?: false
        val triggerFresh = isLinearAccelTriggerFresh()
        // 1ft ON: allow start even if GPS already shows >3 km/h (expected after 30 cm).
        // 1ft OFF: still require near-stop so a stale rolling latch cannot arm.
        val speedOk = DragRolloutSettings.is1ftRolloutEnabled(this) ||
            speedKmh <= FULL_STOP_REARM_SPEED_KMH
        val shouldStart = linearAccelTriggered && triggerFresh && speedOk

        if (!shouldStart && System.currentTimeMillis() % 3000 < 100) {
            Log.d(
                "DragRunPage",
                "⏳ Waiting for FORWARD acceleration$logSuffix... " +
                    "triggered=$linearAccelTriggered, fresh=$triggerFresh, speed=$speedKmh"
            )
        }

        if (shouldStart) {
            beginMeasurementFromLinearAcceleration(
                loc = loc,
                speedKmh = speedKmh,
                linearAccelTriggered = true,
                logSuffix = logSuffix,
                statusText = statusText
            )
        }
    }

    private fun isRaceBoxGpsStartActive(): Boolean {
        return RaceBoxDebugGate.shouldOverridePhoneGps(this)
    }

    private fun resetRaceBoxGpsStartState() {
        raceBoxStationaryReady = false
        raceBoxStartAnchor = null
        raceBoxAwaitingRollout = false
        resetRaceBoxRolloutIntegral()
    }

    private fun resetRaceBoxRolloutIntegral(
        nowNs: Long = -1L,
        speedKmh: Float = -1f
    ) {
        raceBoxRolloutDistanceM = 0f
        raceBoxRolloutLastElapsedNs = nowNs
        raceBoxRolloutLastSpeedKmh = speedKmh
        raceBoxRolloutT0Ns = 0L
    }

    /**
     * After fail-start / full-stop re-arm, phone can launch immediately via accel.
     * RaceBox must be "stationary-ready" first — pre-arm when already near stop so the
     * next attempt flow matches phone (no stuck "ready" that never arms).
     */
    private fun armRaceBoxStartIfAlreadyStationary(loc: Location?, speedKmh: Float) {
        armPhoneStartIfAlreadyStationary(speedKmh)
        if (!isRaceBoxGpsStartActive()) return
        if (loc == null) return
        val gateSpeed = foregroundService?.getCurrentSpeed()?.takeIf { it >= 0f }
            ?: com.revix.app.utils.GnssSpeedSanitizer.sanitizeReportedKmh(speedKmh)
        if (gateSpeed > RACEBOX_STATIONARY_MAX_KMH) return
        raceBoxStationaryReady = true
        raceBoxStartAnchor = Location(loc)
        raceBoxAwaitingRollout = false
        resetRaceBoxRolloutIntegral(nowNs = System.nanoTime(), speedKmh = gateSpeed)
    }

    private fun armPhoneStartIfAlreadyStationary(speedKmh: Float) {
        if (isRaceBoxGpsStartActive()) return
        val gateSpeed = foregroundService?.getCurrentSpeed()?.takeIf { it >= 0f } ?: speedKmh
        if (gateSpeed > FULL_STOP_REARM_SPEED_KMH) return
        phoneStationaryReady = true
    }

    /**
     * Standing-start only: gas while already rolling must not start a run.
     * Keep ready through 1ft so GPS > 3 km/h after ~30 cm does not cancel a real launch.
     */
    private fun updatePhoneStationaryArm(speedKmh: Float) {
        if (isRaceBoxGpsStartActive()) return
        if (speedKmh <= FULL_STOP_REARM_SPEED_KMH) {
            phoneStationaryReady = true
            return
        }
        if (foregroundService?.isPhoneLaunchRolloutPending() == true) return
        phoneStationaryReady = false
        foregroundService?.resetLinearAccelTrigger()
    }

    /**
     * Official RaceBox-style standing start: arm while nearly stopped, then start on GPS motion.
     * With 1ft rollout: clock starts after 0.3048 m from ∫ v dt (same method as 50–402 m).
     */
    private fun pollRaceBoxGpsStart(loc: Location, speedKmh: Float): Boolean {
        val nowNs = System.nanoTime()
        if (speedKmh <= RACEBOX_STATIONARY_MAX_KMH) {
            raceBoxStationaryReady = true
            raceBoxStartAnchor = Location(loc)
            raceBoxAwaitingRollout = false
            resetRaceBoxRolloutIntegral(nowNs = nowNs, speedKmh = speedKmh)
            return false
        }

        if (!raceBoxStationaryReady) return false

        if (DragRolloutSettings.is1ftRolloutEnabled(this)) {
            val prevElapsedNs = raceBoxRolloutLastElapsedNs.takeIf { it >= 0L } ?: nowNs
            val prevSpeedKmh = raceBoxRolloutLastSpeedKmh.takeIf { it >= 0f } ?: 0f
            val prevDistance = raceBoxRolloutDistanceM
            raceBoxRolloutDistanceM += distanceIncrementFromSpeed(
                prevElapsedNs = prevElapsedNs,
                currentElapsedNs = nowNs,
                prevSpeedKmh = prevSpeedKmh,
                currentSpeedKmh = speedKmh
            )
            raceBoxRolloutLastElapsedNs = nowNs
            raceBoxRolloutLastSpeedKmh = speedKmh

            if (raceBoxRolloutDistanceM < RACEBOX_ROLLOUT_METERS) {
                raceBoxAwaitingRollout = true
                if (System.currentTimeMillis() % 2000 < 50) {
                    Log.d(
                        "DragRunPage",
                        "⏳ RaceBox rollout ${"%.3f".format(raceBoxRolloutDistanceM)} / ${RACEBOX_ROLLOUT_METERS} m"
                    )
                }
                return false
            }

            val denom = (raceBoxRolloutDistanceM - prevDistance).coerceAtLeast(0.0001f)
            val ratio = ((RACEBOX_ROLLOUT_METERS - prevDistance) / denom).coerceIn(0f, 1f)
            val span = (nowNs - prevElapsedNs).coerceAtLeast(0L)
            raceBoxRolloutT0Ns = prevElapsedNs + (span * ratio).toLong()
            raceBoxAwaitingRollout = false
            Log.d(
                "DragRunPage",
                "🚀 RaceBox GPS start after 1ft speed-integral (${"%.3f".format(raceBoxRolloutDistanceM)} m), speed=$speedKmh"
            )
            return true
        }

        if (speedKmh >= RACEBOX_MOTION_START_KMH) {
            Log.d("DragRunPage", "🚀 RaceBox GPS start on motion speed=$speedKmh")
            return true
        }
        return false
    }

    private fun beginMeasurementFromLinearAcceleration(
        loc: Location,
        speedKmh: Float,
        linearAccelTriggered: Boolean,
        logSuffix: String,
        statusText: String
    ) {
        started = true
        currentAttemptWasOfficiallyStarted = true
        publishDragVideoHud()
        phoneStationaryReady = false
        launchSpeedConfirmed = speedKmh >= LAUNCH_CONFIRM_SPEED_KMH
        resetEndOfRunWatch()
        runPeakSpeedKmh = speedKmh
        Log.d("DragRunPage", "🚀 START измерване! speedKmh=$speedKmh, linearAccelTriggered=$linearAccelTriggered$logSuffix")

        startTimeNano = if (linearAccelTriggered) {
            foregroundService?.getLinearAccelTriggerTime() ?: System.nanoTime()
        } else {
            raceBoxRolloutT0Ns.takeIf { it > 0L } ?: System.nanoTime()
        }

        // Ensure chart timeline starts from the real launch trigger, not pre-start arming data.
        liveAccelDisplaySamples.clear()
        liveAccelDisplayTimeStamps.clear()

        foregroundService?.setMeasurementStartTimeNano(startTimeNano)
        startLocation = loc
        tvStatus.text = statusText
        foregroundService?.startNewMeasurement(measurementMode.name)
    }

    private fun handleSingleModeFailStartStop() {
        // Фейл старт: скоростта падна под FULL_STOP_REARM_SPEED_KMH докато се измерва.
        // Не записваме нищо → run номерът остава същия.
        val rearmLoc = foregroundService?.getLastLocation()
        val rearmSpeed = rearmLoc?.speed?.times(3.6f) ?: lastSpeed
        foregroundService?.stopMeasurement()
        decelerationDetected = false
        cancelRestartCooldown()

        createNewAttempt()
        foregroundService?.startNewMeasurement(measurementMode.name)
        armRaceBoxStartIfAlreadyStationary(rearmLoc, rearmSpeed)

        when (measurementMode) {
            MeasurementMode.ZERO_TO_100 -> tvStatus.text = getString(R.string.drag_status_ready_0to100)
            MeasurementMode.ZERO_TO_200 -> tvStatus.text = getString(R.string.drag_status_ready_0to200)
            MeasurementMode.QUARTER_MILE -> tvStatus.text = getString(R.string.drag_status_ready_quarter)
            MeasurementMode.ALL -> tvStatus.text = getString(R.string.drag_status_ready_all)
            else -> tvStatus.text = getString(R.string.drag_waiting_for_acceleration)
        }
        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
    }

    private fun prepareSingleModeNextAttemptAfterFullStop() {
        waitingForFullStop = false
        decelerationDetected = false
        measurementComplete = false
        started = false
        cancelRestartCooldown()

        val rearmLoc = foregroundService?.getLastLocation()
        val rearmSpeed = rearmLoc?.speed?.times(3.6f) ?: lastSpeed

        createNewAttempt()
        foregroundService?.startNewMeasurement(measurementMode.name)
        armRaceBoxStartIfAlreadyStationary(rearmLoc, rearmSpeed)

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

    private fun prepareAllModeNextAttemptAfterFullStop() {
        waitingForFullStop = false
        decelerationDetected = false
        measurementComplete = false
        started = false
        cancelRestartCooldown()

        val rearmLoc = foregroundService?.getLastLocation()
        val rearmSpeed = rearmLoc?.speed?.times(3.6f) ?: lastSpeed

        createNewAttempt()
        foregroundService?.startNewMeasurement(measurementMode.name)
        armRaceBoxStartIfAlreadyStationary(rearmLoc, rearmSpeed)

        tvStatus.text = getString(R.string.drag_status_ready_all)
        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
    }

    private fun setStatusPulseActive(active: Boolean) {
        if (!::statusPulseDot.isInitialized || isStatusPulseActive == active) return

        isStatusPulseActive = active
        if (active) {
            statusPulseDot.visibility = View.VISIBLE
            if (statusPulseAnimator == null) {
                val scaleX = ObjectAnimator.ofFloat(statusPulseDot, View.SCALE_X, 1f, 1.35f, 1f)
                val scaleY = ObjectAnimator.ofFloat(statusPulseDot, View.SCALE_Y, 1f, 1.35f, 1f)
                val alpha = ObjectAnimator.ofFloat(statusPulseDot, View.ALPHA, 1f, 0.45f, 1f)

                statusPulseAnimator = AnimatorSet().apply {
                    playTogether(scaleX, scaleY, alpha)
                    duration = 900
                    interpolator = AccelerateDecelerateInterpolator()
                    startDelay = 0
                }

                scaleX.repeatCount = ObjectAnimator.INFINITE
                scaleY.repeatCount = ObjectAnimator.INFINITE
                alpha.repeatCount = ObjectAnimator.INFINITE
            }
            statusPulseAnimator?.start()
        } else {
            statusPulseAnimator?.cancel()
            statusPulseDot.alpha = 1f
            statusPulseDot.scaleX = 1f
            statusPulseDot.scaleY = 1f
            statusPulseDot.visibility = View.GONE
        }
    }

    private fun prepareHundredToTwoHundredNextAttempt() {
        waitingForFullStop = false
        decelerationDetected = false
        rollingStartReady = true
        started = false
        measurementComplete = false
        createNewAttempt()
        rollingCanCross100 = true
        tvStatus.text = getString(R.string.drag_status_ready_100to200)
        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
    }

    private fun formatNanos(nanos: Long): String {
        return if (nanos > 0L) {
            val sec = nanos / 1_000_000_000.0
            String.format("%.2f s", sec)
        } else {
            "--"
        }
    }

    private fun showStopConfirmation() {
        DialogHelper.show(
            DialogHelper.builder(this)
                .setTitle(getString(R.string.drag_stop_session_title))
                .setMessage(getString(R.string.drag_stop_session_message))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    finishSession()
                }
                .setNegativeButton(getString(R.string.no), null)
        )
    }

    private fun finishSession() {
        if (finishingSession) return
        finishingSession = true

        val gSamples = foregroundService?.getRecentGSamples() ?: emptyList()
        val gTimeStamps = foregroundService?.getRecentGTimeStamps() ?: emptyList()
        val gpsAccelSamples = foregroundService?.getRecentGpsAccelSamples() ?: emptyList()
        val gpsAccelTimeStamps = foregroundService?.getRecentGpsAccelTimeStamps() ?: emptyList()
        val longitudinalAccelSamples = foregroundService?.getRecentLongitudinalAccelSamples() ?: emptyList()
        val longitudinalAccelTimeStamps = foregroundService?.getRecentLongitudinalAccelTimeStamps() ?: emptyList()

        if (measurementMode == MeasurementMode.ALL) {
            mergePendingAllModePartialAttemptsIntoCurrentSession()
            if (currentAttemptWasOfficiallyStarted) {
                buildCurrentAttemptSnapshotWithTimestamps(
                    gSamples = gSamples,
                    gTimeStamps = gTimeStamps,
                    gpsAccelSamples = gpsAccelSamples,
                    gpsAccelTimeStamps = gpsAccelTimeStamps,
                    longitudinalAccelSamples = longitudinalAccelSamples,
                    longitudinalAccelTimeStamps = longitudinalAccelTimeStamps
                )?.let { updatedAttempt ->
                    upsertAttemptInCurrentSession(updatedAttempt)
                    updateSessionBestTimes(updatedAttempt)
                }
            }
        } else if (currentAttemptWasOfficiallyStarted && !attemptAlreadySaved) {
            saveCurrentAttempt()
        }

        measuring = false
        publishDragVideoHud(sessionActive = false)
        stopPolling()

        // Спри G-force измерването
        foregroundService?.stopMeasurement()
        foregroundService?.resetData()
        cleanup()

        val sessionId = currentSession?.id ?: 0L
        val mode = measurementMode
        val app = applicationContext
        Thread({
            DragRunVideoBridge.awaitRecordingIdle(4_000L)
            val sessionSaved = persistCurrentSessionSnapshot(notify = true)
            if (sessionSaved && sessionId > 0L) {
                DragVideoOverlayService.start(app, sessionId, mode)
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (!sessionSaved) {
                    Toast.makeText(
                        this@DragRunPageActivity,
                        getString(R.string.drag_no_valid_measurements),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    currentSession?.let { session ->
                        val intent = Intent(this, DragSessionDetailsActivity::class.java)
                        intent.putExtra("SESSION_ID", session.id)
                        startActivity(intent)
                    }
                }
                finish()
            }
        }, "drag-finish-session").start()
    }

    private fun cleanup() {
        stopPolling()
        stopAccelerationPanelLoop()
        cancelRestartCooldown()
        foregroundService?.clearActiveRunOrientation()
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

    private fun isRunOrientationLandscape(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun ensureDragCalibrationRuntimeReady() {
        val runLandscape = isRunOrientationLandscape()
        if (DragCalibration.activateOrientationRuntime(runLandscape)) {
            return
        }

        if (DragCalibration.isLandscapeCalibrated) {
            DragCalibration.activateOrientationRuntime(true)
        } else if (DragCalibration.isPortraitCalibrated) {
            DragCalibration.activateOrientationRuntime(false)
        }
    }

    private fun hasUsableDragCalibration(): Boolean {
        val runLandscape = isRunOrientationLandscape()
        return DragCalibration.hasCalibrationFor(runLandscape) ||
            DragCalibration.isCalibrated ||
            DragCalibration.isUniversalCalibrated ||
            DragCalibration.hasAnyCalibration()
    }

    private fun syncServiceRunOrientation() {
        foregroundService?.setActiveRunOrientation(isRunOrientationLandscape())
    }

    override fun onBackPressed() {
        showStopConfirmation()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Re-inflate the orientation-specific layout and rebind all view references
        // while preserving the active measurement state in memory/service.
        setContentView(getLayoutResourceId())
        applySystemBarsPaddingToRoot()
        setupBottomNavigation()
        initializeViews()
        configureUIForMode()
        updateWeatherSummaryDisplay()
        ensureDragCalibrationRuntimeReady()
        updateReadyStatus()
        updateUIFromService()
        syncServiceRunOrientation()

    }

    override fun onResume() {
        super.onResume()
        // Презареждаме калибрацията при връщане в activity-то
        DragCalibration.setProfile(profileId)
        ensureDragCalibrationRuntimeReady()
        syncServiceRunOrientation()
        Log.d("DragRunPage", "🔄 onResume - Profile ID: $profileId, Calibrated: ${DragCalibration.isCalibrated}, Portrait: ${DragCalibration.isPortraitCalibrated}, Landscape: ${DragCalibration.isLandscapeCalibrated}")
        tvSpeedUnit?.text = UnitsManager.getSpeedUnit(this).symbol
        updateWeatherSummaryDisplay()
        updateQuarterSectorDisplay()
        updateSingleModeMetricDisplay()
        updateQuarterModeMetricDisplay()
        updateAllModeProgress(lastSpeed)
        if (serviceBound) {
            startAccelerationPanelLoop()
        }
        updateReadyStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        DragRunVideoBridge.removeVideoListener(dragVideoAttachListener)
        publishDragVideoHud(sessionActive = false)
        setStatusPulseActive(false)
        stopAccelerationPanelLoop()
        readyCheckHandler.removeCallbacksAndMessages(null)
        soundManager.release()
        cleanup()
        dragPersistExecutor.shutdown()
        try {
            dragPersistExecutor.awaitTermination(3, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

}