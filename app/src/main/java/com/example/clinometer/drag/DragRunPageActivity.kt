package com.example.clinometer.drag

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
import com.example.clinometer.*
import com.example.clinometer.DragCalibration
import com.example.clinometer.settings.SoundManager
import com.example.clinometer.settings.UnitsManager
import android.view.animation.AccelerateDecelerateInterpolator
import java.util.Locale

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

    private var currentSession: DragSession? = null
    private var currentAttempt: DragAttempt? = null
    private val pendingAllModePartialAttempts = mutableListOf<DragAttempt>()
    private var profileId: Long = -1L
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
    private val FULL_STOP_REARM_SPEED_KMH = 80f // Re-arm only after near full stop
    private val DECELERATION_DELTA_KMH = -5f
    private val DECELERATION_MAX_SPEED_KMH = 80f
    private val ROLLING_START_MIN_KMH = 95f
    private val ROLLING_START_MAX_KMH = 99f
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

    private var lastSpeed: Float = 0f
    private var decelerationDetected = false
    private var waitingForFullStop = false

    private lateinit var tvGCurrentBig: TextView
    private lateinit var gGaugeView: com.example.clinometer.GGaugeView
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
            if (measurementMode == MeasurementMode.ALL && measured0to100 && measured0to200 && measured100to200 && measured0to402 && !waitingForFullStop) {
                finishSession()
            } else {
                showStopConfirmation()
            }
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
                tvSingleMetricLabel.text = "0-$speed100 $speedSymbol"
                tvStatus.text = getString(R.string.drag_status_ready_0to100)
            }
            MeasurementMode.ZERO_TO_200 -> {
                singleModeContainer.visibility = View.VISIBLE
                llZeroTo200Splits.visibility = View.VISIBLE
                tvSingleMetricLabel.text = "0-$speed200 $speedSymbol"
                tvStatus.text = getString(R.string.drag_status_ready_0to200)
            }
            MeasurementMode.HUNDRED_TO_200 -> {
                singleModeContainer.visibility = View.VISIBLE
                llZeroTo200Splits.visibility = View.VISIBLE
                tvSingleMetricLabel.text = "$speed100-$speed200 $speedSymbol"
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
            useCompact -> 6
            else -> 10
        }
        val currentValueSp = when {
            useCompact -> 28f
            else -> 36f
        }
        val labelSp = when {
            useCompact -> 10f
            else -> 11f
        }
        val tickSp = when {
            useCompact -> 9f
            else -> 10f
        }
        val peakSp = when {
            useCompact -> 9f
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
                    useCompact -> 20
                    else -> 26
                }
            )
        }
        pbAccelForce.layoutParams = pbAccelForce.layoutParams.apply {
            height = dpToPx(
                when {
                    useCompact -> 10
                    else -> 14
                }
            )
        }
        vAccelMarker.layoutParams = vAccelMarker.layoutParams.apply {
            height = dpToPx(
                when {
                    useCompact -> 11
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

    private fun createNewSession() {
        val allSessions = DragStorage.loadDragSessions(this)
            .filter { it.profileId == profileId }

        pendingAllModePartialAttempts.clear()

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
            altitude = altitude,
            humidity = humidity,
            windKph = windKph,
            weatherIcon = weatherIcon
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

        measured0to100 = false
        measured0to200 = false
        measured100to200 = false
        measured0to402 = false
        
        // Reset sound flags
        sound100Played = false
        sound200Played = false
        sound402Played = false
        resetAccelerationForcePanel()

        accelStartNano = 0L
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

        val updatedAttempt = buildCurrentAttemptSnapshotWithTimestamps(
            gSamples = gSamples,
            gTimeStamps = gTimeStamps,
            gpsAccelSamples = gpsAccelSamples,
            gpsAccelTimeStamps = gpsAccelTimeStamps
        ) ?: return

        upsertAttemptInCurrentSession(updatedAttempt)
        updateSessionBestTimes(updatedAttempt)
        persistCurrentSessionSnapshot()
    }

    private fun buildCurrentAttemptSnapshotWithTimestamps(
        gSamples: List<Float>,
        gTimeStamps: List<Long>,
        gpsAccelSamples: List<Float>,
        gpsAccelTimeStamps: List<Long>
    ): DragAttempt? {

        val speedSamplesRaw = foregroundService?.getRecentSpeedSamples() ?: emptyList()
        val speedTimeStampsRaw = foregroundService?.getRecentSpeedTimeStamps() ?: emptyList()
        val attempt = currentAttempt ?: return null

        // Изчисли времената базирани на режима
        val attempt0to100Result = when (measurementMode) {
            MeasurementMode.ZERO_TO_100, MeasurementMode.ZERO_TO_200, MeasurementMode.ALL ->
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
            MeasurementMode.ZERO_TO_200 ->
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

        val cappedSpeedSamples = if (speedCapKmh != null) {
            trimmedSpeedSamplesRaw.map { sample -> sample.coerceAtMost(speedCapKmh) }
        } else {
            trimmedSpeedSamplesRaw
        }

        val (adjustedSpeedSamples, adjustedSpeedTimes) = ensureSpeedSeriesCoversMeasurementEnd(
            speedSamples = cappedSpeedSamples,
            speedTimes = trimmedSpeedTimes,
            windowEndNs = windowEndNs,
            targetSpeedKmh = speedCapKmh
        )

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
            startTime = attempt.startTime,
            timeStamps = alignedGTimes,
            gpsTimeStamps = alignedGpsAccelTimes,
            duration = measurementDuration,
            speedSamples = adjustedSpeedSamples,
            speedTimeStamps = adjustedSpeedTimes,
            distance50mTimeNs = sector50TimeNanos.takeIf { it > 0L } ?: -1L,
            distance100mTimeNs = sector100TimeNanos.takeIf { it > 0L } ?: -1L,
            distance200mTimeNs = sector200TimeNanos.takeIf { it > 0L } ?: -1L,
            distance300mTimeNs = sector300TimeNanos.takeIf { it > 0L } ?: -1L,
            distance402mTimeNs = when {
                attempt0to402Result > 0L -> attempt0to402Result
                sector402TimeNanos > 0L -> sector402TimeNanos
                else -> -1L
            },
            distance50mSpeedKmh = if (sector50SpeedKmh >= 0f) sector50SpeedKmh else -1f,
            distance100mSpeedKmh = if (sector100SpeedKmh >= 0f) sector100SpeedKmh else -1f,
            distance200mSpeedKmh = if (sector200SpeedKmh >= 0f) sector200SpeedKmh else -1f,
            distance300mSpeedKmh = if (sector300SpeedKmh >= 0f) sector300SpeedKmh else -1f,
            distance402mSpeedKmh = if (sector402SpeedKmh >= 0f) sector402SpeedKmh else -1f
        )

        val hasValidMeasurement = updatedAttempt.time0to100 > 0 ||
            updatedAttempt.time0to200 > 0 ||
            updatedAttempt.time100to200 > 0 ||
            updatedAttempt.time0to402 > 0

        return updatedAttempt.takeIf { hasValidMeasurement }
    }

    private fun stashCurrentAllModePartialAttemptIfNeeded() {
        if (measurementMode != MeasurementMode.ALL || attemptAlreadySaved) return

        val pendingAttempt = buildCurrentAttemptSnapshotWithTimestamps(
            gSamples = foregroundService?.getRecentGSamples() ?: emptyList(),
            gTimeStamps = foregroundService?.getRecentGTimeStamps() ?: emptyList(),
            gpsAccelSamples = foregroundService?.getRecentGpsAccelSamples() ?: emptyList(),
            gpsAccelTimeStamps = foregroundService?.getRecentGpsAccelTimeStamps() ?: emptyList()
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
        val attempts = currentSession?.attempts ?: return
        val existingIndex = attempts.indexOfFirst { it.id == updatedAttempt.id }
        if (existingIndex >= 0) {
            attempts[existingIndex] = updatedAttempt
        } else {
            attempts.add(updatedAttempt)
        }
    }

    private fun hasPersistableAttempts(session: DragSession): Boolean {
        return if (measurementMode == MeasurementMode.ALL) {
            session.attempts.any { attempt ->
                attempt.time0to100 > 0 || attempt.time0to200 > 0 ||
                    attempt.time100to200 > 0 || attempt.time0to402 > 0
            }
        } else {
            session.attempts.any { attempt ->
                when (measurementMode) {
                    MeasurementMode.ZERO_TO_100 -> attempt.time0to100 > 0
                    MeasurementMode.ZERO_TO_200 -> attempt.time0to200 > 0
                    MeasurementMode.HUNDRED_TO_200 -> attempt.time100to200 > 0
                    MeasurementMode.QUARTER_MILE -> attempt.time0to402 > 0
                    MeasurementMode.ALL -> false
                }
            }
        }
    }

    private fun persistCurrentSessionSnapshot(notify: Boolean = false): Boolean {
        val session = currentSession ?: return false
        session.updateBestTimes()

        if (!hasPersistableAttempts(session)) {
            return false
        }

        val existingSession = DragStorage.getDragSession(this, session.id)
        if (existingSession != null) {
            DragStorage.updateDragSession(this, session.id, session)
        } else {
            DragStorage.addDragSession(this, session)
        }

        if (notify) {
            sendBroadcast(Intent("SESSION_UPDATED").apply {
                putExtra("SESSION_ID", session.id)
            })
            setResult(Activity.RESULT_OK)
        }

        return true
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
            tvCard0to100.text = "--.---"
            tvCard0to200.text = "--.---"
            tvCard100to200.text = "--.---"
            tvCard0to402.text = "--.---"
        }
        val speedUnit = UnitsManager.getSpeedUnit(this)
        if (speedUnit == UnitsManager.SpeedUnit.MPH) {
            tvCard0to402Distance.text = "0.00 mi"
        } else {
            tvCard0to402Distance.text = "0 m"
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
        tvQuarterMetricLabel.text = "0-$quarterLabel"
        tvQuarterProgressMin.text = "0"
        tvQuarterProgressMax.text = quarterLabel

        val nowNano = System.nanoTime()
        val measurementStartTimeNano = foregroundService?.getMeasurementStartTimeNano() ?: 0L
        val valueText = when {
            attempt0to402Nanos > 0L -> formatSecondsValue(attempt0to402Nanos)
            started && measurementStartTimeNano > 0L -> formatSecondsValue(nowNano - measurementStartTimeNano)
            else -> "--.---"
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

        displayedAccelForceG += (target - displayedAccelForceG) * 0.24f
        if (kotlin.math.abs(target - displayedAccelForceG) < 0.002f) {
            displayedAccelForceG = target
        }
        if (displayedAccelForceG > peakAccelForceG) {
            peakAccelForceG = displayedAccelForceG
        }

        updateAccelerationForcePanel(displayedAccelForceG, peakAccelForceG)
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
            tvAccelPeakSummary.text = String.format(Locale.US, "PEAK: %.2fg @ ACCEL", peakClamped)
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
        if (measurementMode == MeasurementMode.ALL) return

        val attemptNum = getCurrentAttemptNumber()
        val prefix = getString(R.string.drag_attempt_number, attemptNum)
        tvStatus.text = when (measurementMode) {
            MeasurementMode.HUNDRED_TO_200 -> "$prefix - Accelerate to 95-99 km/h"
            else -> prefix
        }
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
        if (nanos <= 0L) return "--.---"
        return String.format("%.3f", nanos / 1_000_000_000.0)
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
                    attempt0to100Nanos > 0L -> formatSecondsValue(attempt0to100Nanos)
                    started && measurementStartTimeNano > 0L -> formatSecondsValue(nowNano - measurementStartTimeNano)
                    else -> "--.---"
                }
            }
            MeasurementMode.ZERO_TO_200 -> {
                when {
                    attempt0to200Nanos > 0L -> formatSecondsValue(attempt0to200Nanos)
                    started && measurementStartTimeNano > 0L -> formatSecondsValue(nowNano - measurementStartTimeNano)
                    else -> "--.---"
                }
            }
            MeasurementMode.HUNDRED_TO_200 -> {
                when {
                    attempt100to200Nanos > 0L -> formatSecondsValue(attempt100to200Nanos)
                    started && rolling100StartTime > 0L -> formatSecondsValue(nowNano - rolling100StartTime)
                    else -> "--.---"
                }
            }
            else -> "--.---"
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

        stage0to100View.text = "0-100:"
        stage100to200View.text = "100-200:"
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
            stage0to100View.text = "0-100: ${formatSecondsValue(attempt0to100Nanos)}s"
            stage0to100View.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            stage0to100View.setBackgroundResource(R.drawable.bg_drag_split_chip_0to100_active)
        } else if (is0to100Running) {
            stage0to100View.text = "0-100: running..."
            stage0to100View.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            stage0to100View.setBackgroundResource(R.drawable.bg_drag_split_chip_inactive)
        } else {
            stage0to100View.text = "0-100:"
            stage0to100View.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            stage0to100View.setBackgroundResource(R.drawable.bg_drag_split_chip_inactive)
        }

        if (has100to200) {
            stage100to200View.text = "100-200: ${formatSecondsValue(attempt100to200Nanos)}s"
            stage100to200View.setTextColor(ContextCompat.getColor(this, R.color.accent_purple))
            stage100to200View.setBackgroundResource(R.drawable.bg_drag_split_chip_100to200_active)
        } else if (is100to200Running) {
            stage100to200View.text = "100-200: running..."
            stage100to200View.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            stage100to200View.setBackgroundResource(R.drawable.bg_drag_split_chip_inactive)
        } else {
            stage100to200View.text = "100-200:"
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
        tvSector50Time.text = "--.---"
        tvSector100Time.text = "--.---"
        tvSector200Time.text = "--.---"
        tvSector300Time.text = "--.---"
        tvSector402Time.text = "--.---"
        tvSector50Speed.text = "--"
        tvSector100Speed.text = "--"
        tvSector200Speed.text = "--"
        tvSector300Speed.text = "--"
        tvSector402Speed.text = "--"
        tvAllModeSector50Time?.text = "--.---"
        tvAllModeSector100Time?.text = "--.---"
        tvAllModeSector200Time?.text = "--.---"
        tvAllModeSector300Time?.text = "--.---"
        tvAllModeSector402Time?.text = "--.---"
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

    private fun updateQuarterSectorDisplay() {
        if (!::tvSector50Time.isInitialized) return
        tvSector50Time.text = formatSecondsValue(sector50TimeNanos)
        tvSector100Time.text = formatSecondsValue(sector100TimeNanos)
        tvSector200Time.text = formatSecondsValue(sector200TimeNanos)
        tvSector300Time.text = formatSecondsValue(sector300TimeNanos)
        tvSector402Time.text = formatSecondsValue(sector402TimeNanos)
        tvSector50Speed.text = formatSpeedForDisplay(sector50SpeedKmh)
        tvSector100Speed.text = formatSpeedForDisplay(sector100SpeedKmh)
        tvSector200Speed.text = formatSpeedForDisplay(sector200SpeedKmh)
        tvSector300Speed.text = formatSpeedForDisplay(sector300SpeedKmh)
        tvSector402Speed.text = formatSpeedForDisplay(sector402SpeedKmh)
        tvAllModeSector50Time?.text = formatSecondsValue(sector50TimeNanos)
        tvAllModeSector100Time?.text = formatSecondsValue(sector100TimeNanos)
        tvAllModeSector200Time?.text = formatSecondsValue(sector200TimeNanos)
        tvAllModeSector300Time?.text = formatSecondsValue(sector300TimeNanos)
        tvAllModeSector402Time?.text = formatSecondsValue(sector402TimeNanos)
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
            putExtra("ACTIVATE_NORMAL_MODE", true)
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
                    if (!hasUsableDragCalibration()) {
                        // Не е калибрирано - НЕ започваме измерване
                        Log.d("DragRunPage", "❌ DragCalibration NOT calibrated")
                        return
                    }
                    
                    // Хибридна старт детекция: Linear Acceleration + GPS
                    val linearAccelTriggered = foregroundService?.isLinearAccelTriggered() ?: false
                    
                    // ВАЖНО: GPS НЕ участва в старта! Само Linear Acceleration!
                    // GPS използваме САМО за измерване на скорости след старта
                    val shouldStart = linearAccelTriggered && speedKmh <= FULL_STOP_REARM_SPEED_KMH
                    
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
                        if (attemptAlreadySaved && measured0to100 && measured0to200 && measured100to200 && measured0to402) {
                            prepareAllModeNextAttemptAfterFullStop()
                        } else {
                            // Прекъснат/неуспешен ALL опит: рестартираме само текущия опит.
                            stashCurrentAllModePartialAttemptIfNeeded()
                            waitingForFullStop = false
                            decelerationDetected = false
                            isCalibrating = false
                            calibrationComplete = false
                            cancelRestartCooldown()
                            createNewAttempt()
                            foregroundService?.startNewMeasurement(measurementMode.name)
                            tvStatus.text = getString(R.string.drag_status_ready_all)
                            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                        }
                    } else {
                        // Продължаваме да показваме съобщението
                        tvStatus.text = if (attemptAlreadySaved && measured0to100 && measured0to200 && measured100to200 && measured0to402) {
                            getString(R.string.drag_complete_stop_for_new)
                        } else {
                            getString(R.string.drag_status_stop_to_restart)
                        }
                        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                    }
                } else if (!started && serviceReady && gpsReady && !decelerationDetected && !restartCooldownActive) {
                    // БЛОКИРАМЕ измерването ако няма калибрирана посока
                    if (!hasUsableDragCalibration()) {
                        // Не е калибрирано - НЕ започваме измерване
                        Log.d("DragRunPage", "❌ DragCalibration NOT calibrated (ALL режим)")
                        return
                    }
                    
                    // Хибридна старт детекция: Linear Acceleration + GPS
                    val linearAccelTriggered = foregroundService?.isLinearAccelTriggered() ?: false
                    
                    // ВАЖНО: GPS НЕ участва в старта! Само Linear Acceleration!
                    // GPS използваме САМО за измерване на скорости след старта
                    val shouldStart = linearAccelTriggered && speedKmh <= FULL_STOP_REARM_SPEED_KMH
                    
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
            
            // Просто изчисляваме разстоянието от startLocation до текущата позиция - RAW данни
            if (lastLocationForDistance == null) {
                lastLocationForDistance = start
                accumulatedDistance = 0f
                lastQuarterDistanceElapsedNanos = 0L
                lastQuarterSpeedKmh = speedKmh
            } else {
                val prevDistance = accumulatedDistance
                val distanceIncrement = lastLocationForDistance!!.distanceTo(loc).coerceAtLeast(0f)
                accumulatedDistance += distanceIncrement
                updateQuarterSectorMilestones(
                    prevDistanceMeters = prevDistance,
                    currentDistanceMeters = accumulatedDistance,
                    segmentStartElapsedNanos = lastQuarterDistanceElapsedNanos.coerceAtLeast(0L),
                    segmentEndElapsedNanos = currentElapsedNanos,
                    segmentStartSpeedKmh = lastQuarterSpeedKmh.takeIf { it >= 0f } ?: speedKmh,
                    segmentEndSpeedKmh = speedKmh
                )
                lastQuarterDistanceElapsedNanos = currentElapsedNanos
                lastQuarterSpeedKmh = speedKmh
                lastLocationForDistance = loc
            }
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
                val currentTime = System.nanoTime()
                val elapsedNanos = currentElapsedNanos
                val canonical402Nanos = if (sector402TimeNanos > 0L) sector402TimeNanos else elapsedNanos
                finishTimeNano = currentTime

                // Запазваме времето за по-късно използване
                attempt0to402Nanos = canonical402Nanos
                if (sector402TimeNanos <= 0L) {
                    sector402TimeNanos = canonical402Nanos
                    sector402SpeedKmh = speedKmh
                }
                updateQuarterSectorDisplay()

                val resultText = formatNanos(canonical402Nanos)
                val display = if (measurementMode == MeasurementMode.ALL) {
                    resultText
                } else if (sessionBest0to402 < 0 || canonical402Nanos < sessionBest0to402) {
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
        accelStartNano = 0L
        distanceCompleted = false
        calibrationStartTime = 0L
        extrapolatedStartTime = 0L
        accumulatedDistance = 0f
        lastLocationForDistance = null

        // Нулираме дисплея
        resetDisplayValues()
        updateAllModeProgress(0f)
        updateSingleModeMetricDisplay()
        updateQuarterModeMetricDisplay()

        // Спираме G-force измерването
        foregroundService?.stopMeasurement()

        runOnUiThread {
            tvStatus.text = "✅ READY - Start accelerating!"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        }

    }

    private fun checkAllMeasurementsComplete() {
        
        if (measurementMode == MeasurementMode.ALL &&
            measured0to100 && measured0to200 && measured100to200 && measured0to402) {

            // Успешен ALL опит: запазваме го и чакаме пълно спиране за нов опит.
            if (!attemptAlreadySaved) {
                saveCurrentAttempt()
                attemptAlreadySaved = true
            }

            measurementComplete = true
            started = false
            waitingForFullStop = true
            foregroundService?.stopMeasurement()
            tvStatus.text = getString(R.string.drag_complete_stop_for_new)
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnStop.text = getString(R.string.stop_session)

        } else {
        }
    }
    
    private fun checkAllMeasurementsCompleteExcept402() {
        if (measurementMode == MeasurementMode.ALL &&
            measured0to100 && measured0to200 && measured100to200 && !measured0to402) {

        }
    }

    private fun getCurrentAttemptNumber(): Int {
        return (currentSession?.attempts?.size ?: 0) + pendingAllModePartialAttempts.size + 1
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
                    // Ползваме точно семпълното време от Service
                    attempt0to100Nanos = svcT100
                    // svcT100 е вече относително време, не трябва да го добавяме към measurementStartTimeNano
                    timeAt100Nano = measurementStartTimeNano + svcT100
                    val resultNanos = attempt0to100Nanos
                    val timeStr = formatNanos(resultNanos)
                    
                    runOnUiThread {
                        val display = if (measurementMode == MeasurementMode.ALL) {
                            timeStr
                        } else if (sessionBest0to100 < 0 || resultNanos < sessionBest0to100) {
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
                        val timerText = String.format("%.3f s", elapsed / 1_000_000_000.0)
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
                        val display = if (measurementMode == MeasurementMode.ALL) {
                            timeStr
                        } else if (sessionBest0to200 < 0 || resultNanos < sessionBest0to200) {
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
                        val timerText = String.format("%.3f s", elapsed / 1_000_000_000.0)
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
                        tvCard100to200.text = String.format("%.3f s", elapsed / 1_000_000_000.0)
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
                        val timerText = String.format("%.3f s", seconds)
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
                val timerText = String.format("%.3f s", elapsed)
                tvCard100to200.text = if (sessionBest100to200 > 0) {
                    "$timerText\nBest: ${formatNanos(sessionBest100to200)}"
                } else {
                    timerText
                }
            }
        }

        runOnUiThread {
            if (measurementMode == MeasurementMode.ALL) {
                updateAllModeProgress(speedFloat)
            }
            updateSingleModeMetricDisplay()
            updateQuarterModeMetricDisplay()
            updateZeroTo200SplitDisplay(speedFloat)
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

    private fun prepareAllModeNextAttemptAfterFullStop() {
        waitingForFullStop = false
        decelerationDetected = false
        isCalibrating = false
        calibrationComplete = false
        measurementComplete = false
        started = false
        cancelRestartCooldown()

        createNewAttempt()
        foregroundService?.startNewMeasurement(measurementMode.name)
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
            mergePendingAllModePartialAttemptsIntoCurrentSession()
            buildCurrentAttemptSnapshotWithTimestamps(
                gSamples = gSamples,
                gTimeStamps = gTimeStamps,
                gpsAccelSamples = gpsAccelSamples,
                gpsAccelTimeStamps = gpsAccelTimeStamps
            )?.let { updatedAttempt ->
                upsertAttemptInCurrentSession(updatedAttempt)
                updateSessionBestTimes(updatedAttempt)
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

        val sessionSaved = persistCurrentSessionSnapshot(notify = true)
        if (!sessionSaved) {
            runOnUiThread {
                Toast.makeText(this@DragRunPageActivity,
                    "No valid measurements to save",
                    Toast.LENGTH_SHORT).show()
            }
        }

        // Изчисти всички натрупани данни, за да не се пренасят към други режими
        foregroundService?.resetData()

        cleanup()
        
        // Отваряме детайлите на сесията вместо да завършваме активността
        if (sessionSaved) {
            currentSession?.let { session ->
                val intent = Intent(this, DragSessionDetailsActivity::class.java)
                intent.putExtra("SESSION_ID", session.id)
                startActivity(intent)
            }
        }
        
        finish()
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
        setStatusPulseActive(false)
        stopAccelerationPanelLoop()
        readyCheckHandler.removeCallbacksAndMessages(null)
        soundManager.release()
        cleanup()
    }

}