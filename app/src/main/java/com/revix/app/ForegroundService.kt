 package com.revix.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import android.hardware.display.DisplayManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import android.content.res.Configuration
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.revix.app.data.ProfileStorage
import com.revix.app.lean.MotorcycleLeanAccelGate
import com.revix.app.main.MainContainerActivity
import com.revix.app.racebox.RaceBoxDebugGate
import com.revix.app.racebox.RaceBoxManager
import com.revix.app.racebox.RaceBoxProtocol
import com.revix.app.settings.DragRolloutSettings
import com.revix.app.utils.GnssSpeedSanitizer
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt
import java.lang.Math

enum class AccelerationState {
    IDLE,
    ACCELERATING,
    COMPLETED
}

class ForegroundService : Service(), SensorEventListener {
    
    companion object {
        const val GPS_HZ_BROADCAST = "com.revix.app.GPS_HZ_UPDATE"
        const val EXTRA_GPS_HZ = "gps_hz"
        
        private const val NORMAL_SAMPLING_MS = 250L 
        private const val DRAG_SAMPLING_MS = 100L
        /** Soft-cap live free-ride trail (~19 min at 4 Hz) before thinning. */
        private const val LIVE_ROUTE_SOFT_CAP = 4500
        private const val LIVE_ROUTE_TARGET_SIZE = 3000
        private const val RAD_TO_DEG = 57.29578f
        private const val MIN_ACCEL_CORRECTION = 0.03f
        private const val MAX_ACCEL_CORRECTION = 0.22f
        private const val LONGITUDINAL_ACCEL_SAMPLE_MS = 20L
        private const val LONGITUDINAL_LOWPASS_CUTOFF_HZ = 5.0f
        private const val LONGITUDINAL_FILTER_MIN_DT_SEC = 0.004f
        private const val LONGITUDINAL_FILTER_MAX_DT_SEC = 0.08f
        private const val PITCH_COMP_SMOOTH_ALPHA = 0.22f
        private const val PITCH_COMP_MAX_ANGLE_DEG = 15f
        private const val PITCH_COMP_MAX_MPS2 = 4.0f
        /** Allow official t0 up to 2s in the past (phone / RaceBox 1ft crossing + UI poll). */
        private const val MAX_EXTERNAL_START_BACKDATE_NS = 2_000_000_000L
        /** Abort phone 1ft integration if 30 cm not reached in time (false latch). */
        private const val PHONE_ROLLOUT_MAX_INTEGRATION_NS = 2_000_000_000L
        private const val PHONE_ROLLOUT_MAX_DT_SEC = 0.05f

        private const val FG_NOTIFICATION_CHANNEL_ID = "revix_location_service"
        private const val FG_NOTIFICATION_ID = 4711
    }

    private val routePoints = mutableListOf<RoutePoint>()
    private var filteredAngle = 0f
    private var offsetAngle = 0f
    private var currentCalibratedAngle = 0f
    private var maxLeftAngle = 0f
    private var maxRightAngle = 0f
    private var maxSpeed = 0f
    private var currentSpeed = 0f
    private var startTime: Long = 0
    private var lastLocation: Location? = null
    private var serviceStartTime: Long = 0
    
    // Telemetry Recording Loop
    private val recordingHandler = Handler(Looper.getMainLooper())
    private val recordingRunnable = object : Runnable {
        override fun run() {
            if (isMeasurementActive && !isPreWarmingMode) {
                recordTelemetrySnapshot()
            }
            recordingHandler.postDelayed(this, getDataSaveInterval())
        }
    }

    private var lastGpsHzTimeNanos = 0L
    private var totalDistance = 0.0
    private var lastLocationForDistance: Location? = null
    private var resetTime = 0L
    private var isPreWarmingMode = false
    private var actualStartTime: Long = 0L
    private val gpsWarmupLocations = mutableListOf<Location>()

    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locCallback: LocationCallback
    private val raceBoxSampleListener: (RaceBoxProtocol.Sample) -> Unit = listener@{ sample ->
        if (!RaceBoxDebugGate.shouldOverridePhoneGps(this)) return@listener
        if (sample.fixOk) {
            onNewLocation(sample.location)
            val nowNanos = sample.location.elapsedRealtimeNanos
            if (lastGpsHzTimeNanos > 0L) {
                val deltaMs = (nowNanos - lastGpsHzTimeNanos) / 1_000_000.0
                if (deltaMs > 0) sendGpsHzBroadcast(1000.0 / deltaMs)
            }
            lastGpsHzTimeNanos = nowNanos
        }
        applyRaceBoxImu(sample)
    }
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    var sessionStartTime: Long = 0
    var accelerationTracking = AccelerationData()

    // Lean angle fusion state (gyro + accel reference) — same model as TrackSessionActivity
    private var latestRollRateDegPerSec = 0f
    private var latestYawRateDegPerSec = 0f
    private val leanAccelGate = MotorcycleLeanAccelGate()
    private var gyroIntegratedLeanDeg = 0f
    private var hasGyroIntegratedLean = false
    private var leanGyroIntegrationTimestampNs = 0L
    private var lastGyroMagnitude = 0f
    private var runtimeLeanOffsetDeg = 0f
    private var profileLeanOffsetDeg = 0f
    private var lastLeanOrientationLandscape: Boolean? = null
    private var selectedProfileIdForLeanCalibration: Long = -1L
    private val madgwick = MadgwickAHRS(beta = 0.033f)
    private val latestGyroForMadgwick = FloatArray(3)
    private var lastMadgwickUpdateNs: Long = 0L

    private val gravity = FloatArray(3)
    private val alpha = 0.8f
    @Volatile private var isRealAcceleration = false
    @Volatile private var currentG = 0f
    @Volatile private var peakG = 0f
    @Volatile private var currentGForceX = 0f
    @Volatile private var currentGForceY = 0f
    private var displayLX = 0f  // Filtered G-force X for display
    private var displayLY = 0f  // Filtered G-force Y for display
    @Volatile private var isMeasurementActive = false
    
    private var linearAccelTriggered = false
    /** Official start must be consumed by UI within this window after t0. */
    private val LINEAR_ACCEL_TRIGGER_MAX_AGE_NS = 1_200_000_000L
    private var linearAccelTriggerTime = 0L
    private var consecutiveAccelSamples = 0
    private var triggerForwardFiltered = 0f
    private var triggerLateralFiltered = 0f
    private val triggerFilterAlpha = 0.35f
    /** Phone standing-start: motion latched, integrating forward accel to 1 ft before official t0. */
    private var phoneRolloutIntegrating = false
    private var phoneRolloutLatchTimeNs = 0L
    private var phoneRolloutDistanceM = 0f
    private var phoneRolloutVelocityMps = 0f
    private var phoneRolloutLastSampleNs = 0L
    private var currentMeasurementMode = "ALL"
    @Volatile private var activeRunOrientationLandscape: Boolean? = null

    /** G / accel buffers — phone sensor rate; 1000 is enough for drag windows. */
    private val SAMPLES_CAPACITY = 1000
    /**
     * RaceBox GPS is ~25 Hz. Old shared cap of 1000 kept only ~40s and chopped the SPEED
     * chart start on longer runs. Keep speed history for several minutes.
     */
    private val SPEED_SAMPLES_CAPACITY = 20_000
    private val gSamplesBuffer: ArrayDeque<Float> = ArrayDeque(SAMPLES_CAPACITY)
    private val gpsAccelBuffer: ArrayDeque<Float> = ArrayDeque(SAMPLES_CAPACITY)
    private val longitudinalAccelBuffer: ArrayDeque<Float> = ArrayDeque(SAMPLES_CAPACITY)
    private val gTimeStamps = ArrayDeque<Long>(SAMPLES_CAPACITY)
    private val gpsAccelTimeStamps = ArrayDeque<Long>(SAMPLES_CAPACITY)
    private val longitudinalAccelTimeStamps = ArrayDeque<Long>(SAMPLES_CAPACITY)

    private var gMeasurementStartTime: Long = 0L
    private var measurementStartTimeNano: Long = 0L
    private var measurementStartTimeGpsNano: Long = 0L
    private var pendingExternalMeasurementStartNano: Long? = null
    private var lastGSampleTime = 0L
    private var lastGPSAccelSampleTime = 0L
    private var lastLongitudinalAccelSampleTime = 0L
    private var longitudinalAccelLowPassMps2 = 0f
    private var longitudinalFilterInitialized = false
    private var longitudinalFilterTimestampNs = 0L
    private var smoothedPitchAngleRad = 0f
    private var pitchCompInitialized = false
    private val speedSamplesBuffer: ArrayDeque<Float> = ArrayDeque(SPEED_SAMPLES_CAPACITY)
    private val speedTimeStamps = ArrayDeque<Long>(SPEED_SAMPLES_CAPACITY)
    private val speedReceiveTimeStamps = ArrayDeque<Long>(SPEED_SAMPLES_CAPACITY)
    private val speedSampleAgeNanos = ArrayDeque<Long>(SPEED_SAMPLES_CAPACITY)

    @Volatile private var time0to100Nanos: Long = 0L
    @Volatile private var time0to200Nanos: Long = 0L

    inner class LocalBinder : Binder() {
        fun getService(): ForegroundService = this@ForegroundService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder? = binder

    data class AccelerationRange(
        val name: String,
        val startSpeed: Float,
        val endSpeed: Float,
        val timeout: Long,
        val requiresFullStop: Boolean = false,
        var startTime: Long = 0L,
        var isActive: Boolean = false,
        val results: MutableList<Long> = mutableListOf()
    )

    data class SpeedPoint(val speed: Float, val timestamp: Long)

    data class AccelerationData(
        var isTracking0to100: Boolean = false,
        var isTracking0to200: Boolean = false,
        var isTracking100to200: Boolean = false,
        var lastBest0to100: Long = -1L,
        var lastBest0to200: Long = -1L,
        var lastBest100to200: Long = -1L,
        var hasFullyStopped: Boolean = false,
        var startTime0to100: Long = 0L,
        var startTime0to200: Long = 0L,
        var startTime100to200: Long = 0L,
        var times0to100: MutableList<Long> = mutableListOf(),
        var times0to200: MutableList<Long> = mutableListOf(),
        var times100to200: MutableList<Long> = mutableListOf(),
        var hasReached100: Boolean = false,
        var hasReached200: Boolean = false,
        var speedHistory: MutableList<SpeedPoint> = mutableListOf(),
        var accelerationStartSpeed: Float = 0f,
        var state: AccelerationState = AccelerationState.IDLE,
        var ranges: MutableList<AccelerationRange> = mutableListOf(
            AccelerationRange("0-100", 3f, 100f, 20_000_000_000L, requiresFullStop = true),
            AccelerationRange("0-200", 3f, 200f, 60_000_000_000L, requiresFullStop = true)
        ),
        var lastSpeed: Float = 0f
    ) {
        fun best0to100() = times0to100.minOrNull() ?: lastBest0to100
        fun best0to200() = times0to200.minOrNull() ?: lastBest0to200
        fun best100to200() = times100to200.minOrNull() ?: lastBest100to200

        fun syncFromRanges() {
            val range030 = ranges.find { it.name == "0-100" }
            val range060 = ranges.find { it.name == "0-200" }
            isTracking0to100 = range030?.isActive == true
            isTracking0to200 = range060?.isActive == true
            startTime0to100 = range030?.startTime ?: 0L
            startTime0to200 = range060?.startTime ?: 0L
            if (range030?.results?.isNotEmpty() == true) {
                times0to100.addAll(range030.results); range030.results.clear()
            }
            if (range060?.results?.isNotEmpty() == true) {
                times0to200.addAll(range060.results); range060.results.clear()
            }
        }
    }

    fun getRoutePoints(): List<RoutePoint> = routePoints
    
    fun getFinalRoutePoints(): List<RoutePoint> {
        if (routePoints.isEmpty()) return routePoints
        val currentTime = SystemClock.elapsedRealtime()
        val finalTimestamp = currentTime - actualStartTime
        val lastPoint = routePoints.last()
        return routePoints + RoutePoint(lastPoint.geoPoint, currentSpeed, currentCalibratedAngle, finalTimestamp, System.currentTimeMillis())
    }
    fun getTotalDistanceMeters(): Double = totalDistance

    fun getCurrentG(): Float = currentG
    fun getPeakG(): Float = peakG
    fun getCurrentGForceX(): Float = currentGForceX
    fun getCurrentGForceY(): Float = currentGForceY
    fun isLinearAccelTriggered(): Boolean = linearAccelTriggered
    fun getLinearAccelTriggerTime(): Long = linearAccelTriggerTime
    /** True while phone is integrating the 1 ft (~30 cm) rollout after motion latch. */
    fun isPhoneLaunchRolloutPending(): Boolean = phoneRolloutIntegrating
    fun resetLinearAccelTrigger() {
        linearAccelTriggered = false
        linearAccelTriggerTime = 0L
        consecutiveAccelSamples = 0
        resetPhoneRolloutState()
    }
    private fun resetPhoneRolloutState() {
        phoneRolloutIntegrating = false
        phoneRolloutLatchTimeNs = 0L
        phoneRolloutDistanceM = 0f
        phoneRolloutVelocityMps = 0f
        phoneRolloutLastSampleNs = 0L
    }
    fun isLinearAccelCalibrated(): Boolean = DragCalibration.isUniversalCalibrated
    fun getAccuracyMode(): String = if (DragCalibration.isUniversalCalibrated) "HIGH_ACCURACY" else "GPS_ONLY"
    fun setActiveRunOrientation(isLandscape: Boolean) {
        activeRunOrientationLandscape = isLandscape
        Log.d("ForegroundService", "🧭 Active run orientation set to ${if (isLandscape) "LANDSCAPE" else "PORTRAIT"}")
    }
    fun clearActiveRunOrientation() {
        activeRunOrientationLandscape = null
    }
    fun isSessionActive(): Boolean = isMeasurementActive
    fun getCurrentAngle(): Float = currentCalibratedAngle
    fun getCurrentSpeed(): Float = currentSpeed
    fun getMaxLeftAngle(): Float = maxLeftAngle
    fun getMaxRightAngle(): Float = maxRightAngle
    fun getMaxSpeed(): Float = maxSpeed
    fun getLastLocation(): Location? = lastLocation
    fun getRecentGSamples(): List<Float> = synchronized(gSamplesBuffer) { gSamplesBuffer.toList() }
    fun getRecentGTimeStamps(): List<Long> = synchronized(gTimeStamps) { gTimeStamps.toList() }
    fun getRecentGpsAccelSamples(): List<Float> = synchronized(gpsAccelBuffer) { gpsAccelBuffer.toList() }
    fun getRecentGpsAccelTimeStamps(): List<Long> = synchronized(gpsAccelTimeStamps) { gpsAccelTimeStamps.toList() }
    fun getRecentLongitudinalAccelSamples(): List<Float> = synchronized(longitudinalAccelBuffer) { longitudinalAccelBuffer.toList() }
    fun getRecentLongitudinalAccelTimeStamps(): List<Long> = synchronized(longitudinalAccelTimeStamps) { longitudinalAccelTimeStamps.toList() }
    fun getRecentSpeedSamples(): List<Float> = synchronized(speedSamplesBuffer) { speedSamplesBuffer.toList() }
    fun getRecentSpeedTimeStamps(): List<Long> = synchronized(speedTimeStamps) { speedTimeStamps.toList() }
    fun getRecentSpeedReceiveTimeStamps(): List<Long> = synchronized(speedReceiveTimeStamps) { speedReceiveTimeStamps.toList() }
    fun getRecentSpeedSampleAgeNanos(): List<Long> = synchronized(speedSampleAgeNanos) { speedSampleAgeNanos.toList() }
    fun getMeasurementStartTimeNano(): Long = measurementStartTimeNano
    fun getMeasurementStartTimeGpsNano(): Long = measurementStartTimeGpsNano
    fun setMeasurementStartTimeNano(timeNanos: Long) {
        pendingExternalMeasurementStartNano = timeNanos
    }
    fun getTime0to100Nanos(): Long = time0to100Nanos
    fun getTime0to200Nanos(): Long = time0to200Nanos
    fun getServiceDuration(): Long = if (actualStartTime == 0L) 0L else SystemClock.elapsedRealtime() - actualStartTime

    private fun estimateGpsStartFromSystemStart(systemStartNanos: Long): Long {
        val systemNow = System.nanoTime()
        val gpsNow = SystemClock.elapsedRealtimeNanos()
        return systemStartNanos + (gpsNow - systemNow)
    }

    private fun resolveRelativeMeasurementTimeNanos(loc: Location): Long {
        // RaceBox Location.elapsedRealtimeNanos is BLE receive-time (iTOW is discarded in
        // RaceBoxProtocol). Prefer System.nanoTime so speed samples share the same domain as
        // IMU G / longitudinal buffers and chart X stays in the measurement window.
        if (loc.provider == "racebox") {
            return if (measurementStartTimeNano > 0L) {
                (System.nanoTime() - measurementStartTimeNano).coerceAtLeast(0L)
            } else {
                0L
            }
        }
        val locElapsedNanos = loc.elapsedRealtimeNanos
        if (measurementStartTimeGpsNano > 0L && locElapsedNanos > 0L && locElapsedNanos >= measurementStartTimeGpsNano) {
            return (locElapsedNanos - measurementStartTimeGpsNano).coerceAtLeast(0L)
        }
        return (System.nanoTime() - measurementStartTimeNano).coerceAtLeast(0L)
    }

    private fun resolveReceiveRelativeMeasurementTimeNanos(): Long {
        val receiveElapsedNanos = SystemClock.elapsedRealtimeNanos()
        if (measurementStartTimeGpsNano > 0L && receiveElapsedNanos >= measurementStartTimeGpsNano) {
            return (receiveElapsedNanos - measurementStartTimeGpsNano).coerceAtLeast(0L)
        }
        return (System.nanoTime() - measurementStartTimeNano).coerceAtLeast(0L)
    }

    private fun resetLongitudinalStabilityState() {
        longitudinalAccelLowPassMps2 = 0f
        longitudinalFilterInitialized = false
        longitudinalFilterTimestampNs = 0L
        smoothedPitchAngleRad = 0f
        pitchCompInitialized = false
    }

    private fun applyLongitudinalLowPassFilter(sampleMps2: Float, sensorTimestampNs: Long): Float {
        if (!longitudinalFilterInitialized || longitudinalFilterTimestampNs <= 0L) {
            longitudinalAccelLowPassMps2 = sampleMps2
            longitudinalFilterInitialized = true
            longitudinalFilterTimestampNs = sensorTimestampNs
            return longitudinalAccelLowPassMps2
        }

        val rawDtSec = ((sensorTimestampNs - longitudinalFilterTimestampNs).toDouble() / 1_000_000_000.0)
            .coerceAtLeast(0.0)
        longitudinalFilterTimestampNs = sensorTimestampNs

        val dtSec = rawDtSec
            .coerceIn(LONGITUDINAL_FILTER_MIN_DT_SEC.toDouble(), LONGITUDINAL_FILTER_MAX_DT_SEC.toDouble())
        val rc = 1.0 / (2.0 * Math.PI * LONGITUDINAL_LOWPASS_CUTOFF_HZ.toDouble())
        val alpha = (dtSec / (rc + dtSec)).toFloat().coerceIn(0.01f, 1f)

        longitudinalAccelLowPassMps2 += alpha * (sampleMps2 - longitudinalAccelLowPassMps2)
        return longitudinalAccelLowPassMps2
    }

    private fun estimatePitchCompensationMps2(liveGravity: FloatArray): Float {
        if (!DragCalibration.isUniversalCalibrated) return 0f

        val baselineGravity = DragCalibration.gravityVector
        val forwardVector = DragCalibration.forwardVector

        val liveNorm = sqrt(
            liveGravity[0] * liveGravity[0] +
                liveGravity[1] * liveGravity[1] +
                liveGravity[2] * liveGravity[2]
        ).coerceAtLeast(0.0001f)
        val baselineNorm = sqrt(
            baselineGravity[0] * baselineGravity[0] +
                baselineGravity[1] * baselineGravity[1] +
                baselineGravity[2] * baselineGravity[2]
        ).coerceAtLeast(0.0001f)
        val forwardNorm = sqrt(
            forwardVector[0] * forwardVector[0] +
                forwardVector[1] * forwardVector[1] +
                forwardVector[2] * forwardVector[2]
        ).coerceAtLeast(0.0001f)

        val baselineForward = (
            (baselineGravity[0] / baselineNorm) * (forwardVector[0] / forwardNorm) +
                (baselineGravity[1] / baselineNorm) * (forwardVector[1] / forwardNorm) +
                (baselineGravity[2] / baselineNorm) * (forwardVector[2] / forwardNorm)
            ).coerceIn(-1f, 1f)

        val liveForward = (
            (liveGravity[0] / liveNorm) * (forwardVector[0] / forwardNorm) +
                (liveGravity[1] / liveNorm) * (forwardVector[1] / forwardNorm) +
                (liveGravity[2] / liveNorm) * (forwardVector[2] / forwardNorm)
            ).coerceIn(-1f, 1f)

        val baselinePitchRad = asin(baselineForward)
        val livePitchRad = asin(liveForward)
        val maxPitchRad = Math.toRadians(PITCH_COMP_MAX_ANGLE_DEG.toDouble()).toFloat()
        val rawPitchDeltaRad = (livePitchRad - baselinePitchRad).coerceIn(-maxPitchRad, maxPitchRad)

        if (!pitchCompInitialized) {
            smoothedPitchAngleRad = rawPitchDeltaRad
            pitchCompInitialized = true
        } else {
            smoothedPitchAngleRad =
                (PITCH_COMP_SMOOTH_ALPHA * rawPitchDeltaRad) +
                    ((1f - PITCH_COMP_SMOOTH_ALPHA) * smoothedPitchAngleRad)
        }

        val gravityComp = SensorManager.GRAVITY_EARTH * sin(smoothedPitchAngleRad)
        return gravityComp.coerceIn(-PITCH_COMP_MAX_MPS2, PITCH_COMP_MAX_MPS2)
    }

    private fun getDataSaveInterval(): Long = if (currentMeasurementMode == "NORMAL") NORMAL_SAMPLING_MS else DRAG_SAMPLING_MS

    fun calibrateZero() {
        val isLandscape = resolveRunOrientationIsLandscape()
        if (lastLeanOrientationLandscape == null || lastLeanOrientationLandscape != isLandscape) {
            updateProfileLeanOffsetForOrientation(isLandscape)
            lastLeanOrientationLandscape = isLandscape
        }
        runtimeLeanOffsetDeg = filteredAngle - profileLeanOffsetDeg
        offsetAngle = profileLeanOffsetDeg + runtimeLeanOffsetDeg
        maxLeftAngle = 0f; maxRightAngle = 0f; currentCalibratedAngle = 0f
    }

    fun startNewMeasurement(measurementMode: String = "ALL") {
        currentMeasurementMode = measurementMode
        reloadLeanCalibrationForSelectedProfile(forceResetRuntime = false)
        gMeasurementStartTime = System.currentTimeMillis()
        val nowNanos = System.nanoTime()
        val startTimeNanos = pendingExternalMeasurementStartNano
            ?.takeIf { externalStart ->
                externalStart <= nowNanos &&
                    (nowNanos - externalStart) <= MAX_EXTERNAL_START_BACKDATE_NS
            }
            ?: nowNanos
        pendingExternalMeasurementStartNano = null
        measurementStartTimeNano = startTimeNanos
        measurementStartTimeGpsNano = estimateGpsStartFromSystemStart(startTimeNanos)
        lastGSampleTime = 0L; lastGPSAccelSampleTime = 0L; lastLongitudinalAccelSampleTime = 0L
        resetLongitudinalStabilityState()
        isMeasurementActive = true
        linearAccelTriggered = false
        linearAccelTriggerTime = 0L
        consecutiveAccelSamples = 0
        resetPhoneRolloutState()
        triggerForwardFiltered = 0f; triggerLateralFiltered = 0f
        time0to100Nanos = 0L; time0to200Nanos = 0L
        currentG = 0f; peakG = 0f
        synchronized(gSamplesBuffer) { gSamplesBuffer.clear(); gTimeStamps.clear() }
        synchronized(gpsAccelBuffer) { gpsAccelBuffer.clear(); gpsAccelTimeStamps.clear() }
        synchronized(longitudinalAccelBuffer) { longitudinalAccelBuffer.clear(); longitudinalAccelTimeStamps.clear() }
        synchronized(speedSamplesBuffer) {
            speedSamplesBuffer.clear()
            speedTimeStamps.clear()
            speedReceiveTimeStamps.clear()
            speedSampleAgeNanos.clear()
        }
        recordingHandler.removeCallbacks(recordingRunnable)
        recordingHandler.post(recordingRunnable)
    }

    fun stopMeasurement() {
        isMeasurementActive = false
        resetLinearAccelTrigger()
        triggerForwardFiltered = 0f
        triggerLateralFiltered = 0f
        recordingHandler.removeCallbacks(recordingRunnable)
    }

    fun resetData() {
        routePoints.clear()
        maxLeftAngle = 0f; maxRightAngle = 0f; maxSpeed = 0f; currentSpeed = 0f
        reloadLeanCalibrationForSelectedProfile(forceResetRuntime = true)
        resetLeanFusionState(forceResetRuntime = false)
        val now = SystemClock.elapsedRealtime()
        startTime = now; actualStartTime = if (isPreWarmingMode) 0L else now; resetTime = now
        accelerationTracking = AccelerationData(); totalDistance = 0.0; lastLocationForDistance = null
        gMeasurementStartTime = 0L; measurementStartTimeNano = 0L; measurementStartTimeGpsNano = 0L
        pendingExternalMeasurementStartNano = null
        lastGSampleTime = 0L; lastLongitudinalAccelSampleTime = 0L; peakG = 0f
        resetLongitudinalStabilityState()
        synchronized(gSamplesBuffer) { gSamplesBuffer.clear(); gTimeStamps.clear() }
        synchronized(gpsAccelBuffer) { gpsAccelBuffer.clear(); gpsAccelTimeStamps.clear() }
        synchronized(longitudinalAccelBuffer) { longitudinalAccelBuffer.clear(); longitudinalAccelTimeStamps.clear() }
        synchronized(speedSamplesBuffer) {
            speedSamplesBuffer.clear()
            speedTimeStamps.clear()
            speedReceiveTimeStamps.clear()
            speedSampleAgeNanos.clear()
        }
    }

    fun getStartTime(): Long = if (isPreWarmingMode) SystemClock.elapsedRealtime() else (if (actualStartTime == 0L) SystemClock.elapsedRealtime().also { actualStartTime = it } else actualStartTime)

    override fun onCreate() {
        super.onCreate()
        resetData(); serviceStartTime = SystemClock.elapsedRealtime(); sessionStartTime = System.currentTimeMillis()
        if (!hasRequiredPermissions()) { stopSelf(); return }
        // Промотираме сервиза до foreground service от тип location. Без това Android
        // прилага background location throttling при заключен екран и GPS спира да тече,
        // докато само PARTIAL_WAKE_LOCK държи CPU-то будно (затова таймерите продължават).
        promoteToForegroundService()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "${packageName}:wakeLock")
        wakeLock.acquire()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        setupLocationUpdates()
        if (!isPreWarmingMode) registerSensors()
    }

    private fun promoteToForegroundService() {
        try {
            ensureForegroundNotificationChannel()
            val notification = buildForegroundNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    FG_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(FG_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w("ForegroundService", "Unable to start foreground service", e)
        }
    }

    private fun ensureForegroundNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(FG_NOTIFICATION_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            FG_NOTIFICATION_CHANNEL_ID,
            getString(R.string.location_service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.location_service_channel_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            MainContainerActivity.launchIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FG_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.location_service_notification_title))
            .setContentText(getString(R.string.location_service_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun recordTelemetrySnapshot() {
        if (!isMeasurementActive || actualStartTime == 0L) return
        val location = lastLocation ?: return
        val currentTime = SystemClock.elapsedRealtime()
        routePoints.add(RoutePoint(GeoPoint(location.latitude, location.longitude), currentSpeed, currentCalibratedAngle, currentTime - actualStartTime, System.currentTimeMillis()))
        // Soft-cap earlier for multi-hour free-ride / navigation — keeps RAM flat without changing UX.
        if (routePoints.size >= LIVE_ROUTE_SOFT_CAP) {
            optimizeRoutePoints(LIVE_ROUTE_TARGET_SIZE)
        }
    }

    private fun optimizeRoutePoints(targetMax: Int = LIVE_ROUTE_TARGET_SIZE) {
        if (routePoints.size <= targetMax) return
        val source = routePoints.toList()
        val important = ArrayList<RoutePoint>(targetMax)
        important.add(source.first())
        for (i in 1 until source.size - 1) {
            val prev = source[i - 1]
            val curr = source[i]
            val angleChanged = abs(curr.angle - prev.angle) > 1.0f
            val speedChanged = abs(curr.speed - prev.speed) > 2.0f
            if (angleChanged || speedChanged) {
                important.add(curr)
            }
        }
        important.add(source.last())
        val optimized = if (important.size <= targetMax) {
            important
        } else {
            sampleRoutePoints(source, targetMax)
        }
        routePoints.clear()
        routePoints.addAll(optimized)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Гарантира, че startForeground() се вика при всеки старт (вкл. повторни),
        // за да удовлетворим 5-секундното изискване на startForegroundService().
        promoteToForegroundService()
        intent?.let {
            if (it.getBooleanExtra("PRE_WARMING_MODE", false)) isPreWarmingMode = true
            if (it.getBooleanExtra("ACTIVATE_DRAG_MODE", false)) {
                isPreWarmingMode = false
                if (accelerometer == null) registerSensors()
            }
            if (it.getBooleanExtra("ACTIVATE_NORMAL_MODE", false)) {
                isPreWarmingMode = false; currentMeasurementMode = "NORMAL"; actualStartTime = SystemClock.elapsedRealtime()
                registerSensors(); startNewMeasurement("NORMAL")
            }
        }
        return START_STICKY
    }

    private fun setupLocationUpdates() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100).setMinUpdateIntervalMillis(100).setWaitForAccurateLocation(false).build()
        locCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // Debug-only: when RaceBox is connected as GPS source, ignore phone GPS.
                if (RaceBoxDebugGate.shouldOverridePhoneGps(this@ForegroundService)) return
                result.locations.forEach { onNewLocation(it) }
                result.lastLocation?.let { lastLoc ->
                    val nowNanos = lastLoc.elapsedRealtimeNanos
                    if (lastGpsHzTimeNanos > 0L) {
                        val deltaMs = (nowNanos - lastGpsHzTimeNanos) / 1_000_000.0
                        if (deltaMs > 0) sendGpsHzBroadcast(1000.0 / deltaMs)
                    }
                    lastGpsHzTimeNanos = nowNanos
                }
            }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) fusedClient.requestLocationUpdates(locationRequest, locCallback, Looper.getMainLooper())
        if (RaceBoxDebugGate.isAvailable()) {
            RaceBoxManager.ensureInitialized(this)
            RaceBoxManager.addSampleListener(raceBoxSampleListener)
        }
    }

    private fun registerSensors() {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMeasurement()
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
        fusedClient.removeLocationUpdates(locCallback); sensorManager.unregisterListener(this)
        if (RaceBoxDebugGate.isAvailable()) {
            RaceBoxManager.removeSampleListener(raceBoxSampleListener)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun applyRaceBoxImu(sample: RaceBoxProtocol.Sample) {
        if (isPreWarmingMode) return

        currentGForceX = sample.displayLateralG
        currentGForceY = sample.displayLongitudinalG
        displayLX = sample.displayLateralG
        displayLY = sample.displayLongitudinalG
        currentG = sample.dynamicG
        if (currentG > peakG) peakG = currentG

        filteredAngle = sample.leanDegApprox
        currentCalibratedAngle = (sample.leanDegApprox - offsetAngle).coerceIn(-90f, 89f)
        if (currentCalibratedAngle < maxLeftAngle) maxLeftAngle = currentCalibratedAngle
        if (currentCalibratedAngle > maxRightAngle) maxRightAngle = currentCalibratedAngle

        if (isMeasurementActive && gMeasurementStartTime > 0L) {
            val now = System.currentTimeMillis()
            if (now - lastGSampleTime >= 100) {
                synchronized(gSamplesBuffer) {
                    gSamplesBuffer.addLast(currentG)
                    gTimeStamps.addLast(System.nanoTime() - measurementStartTimeNano)
                    if (gSamplesBuffer.size > SAMPLES_CAPACITY) {
                        gSamplesBuffer.removeFirst()
                        gTimeStamps.removeFirst()
                    }
                }
                lastGSampleTime = now
            }
            val longMps2 = sample.gForceX * 9.81f
            if (now - lastLongitudinalAccelSampleTime >= LONGITUDINAL_ACCEL_SAMPLE_MS) {
                synchronized(longitudinalAccelBuffer) {
                    var longitudinalTimestamp = (System.nanoTime() - measurementStartTimeNano).coerceAtLeast(0L)
                    if (longitudinalAccelTimeStamps.isNotEmpty() && longitudinalTimestamp <= longitudinalAccelTimeStamps.last()) {
                        longitudinalTimestamp = longitudinalAccelTimeStamps.last() + 1L
                    }
                    longitudinalAccelBuffer.addLast(longMps2)
                    longitudinalAccelTimeStamps.addLast(longitudinalTimestamp)
                    if (longitudinalAccelBuffer.size > SAMPLES_CAPACITY) {
                        longitudinalAccelBuffer.removeFirst()
                        longitudinalAccelTimeStamps.removeFirst()
                    }
                }
                lastLongitudinalAccelSampleTime = now
            }
        }

        // Drag launch trigger stays phone-only. RaceBox standing starts are GPS-motion based in DragRunPageActivity.
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (isPreWarmingMode) return
        // When RaceBox is the active source, IMU/G/lean come from the hardware sample stream.
        // Do not arm phone linear-accel launch — that path must stay unchanged for phone-only use.
        if (RaceBoxDebugGate.shouldOverridePhoneGps(this) && RaceBoxManager.latestSample() != null) {
            return
        }
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                updateLeanFusionFromGyroscope(event)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val isLandscape = resolveRunOrientationIsLandscape()

                gravity[0] = alpha * gravity[0] + (1 - alpha) * x
                gravity[1] = alpha * gravity[1] + (1 - alpha) * y
                gravity[2] = alpha * gravity[2] + (1 - alpha) * z

                val linearX = x - gravity[0]
                val linearY = y - gravity[1]
                val linearZ = z - gravity[2]

                updateLeanFusionFromAccelerometer(
                    x = x,
                    y = y,
                    z = z,
                    linearX = linearX,
                    linearY = linearY,
                    linearZ = linearZ,
                    isLandscape = isLandscape
                )

                val magnitude = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)
                currentG = magnitude / 9.81f
                if (currentG > peakG) peakG = currentG

                var longitudinalAccelMps2 = 0f
                var hasReliableLongitudinalAxis = false
                val rawAccel = floatArrayOf(x, y, z)

                // Използваме калибрацията за определяне на посоките (ако е налична)
                if (DragCalibration.isUniversalCalibrated) {
                    hasReliableLongitudinalAxis = true
                    val calibratedGravity = DragCalibration.gravityVector

                    val forwardAccelRaw = DragCalibration.getSignedForwardAcceleration(rawAccel, calibratedGravity)
                    val lateralAccel = DragCalibration.getSignedLateralAcceleration(rawAccel, calibratedGravity)
                    longitudinalAccelMps2 = forwardAccelRaw

                    // Конвертираме в g-сили и показваме ИНЕРЦИОННАТА СИЛА
                    // Инерционна сила = обратна на ускорението:
                    // - Ускорение напред → сила назад (gForceY положителна = точка надолу)
                    // - Спиране → сила напред (gForceY отрицателна = точка нагоре)
                    // - Завой надясно → сила наляво (gForceX отрицателна = точка наляво)
                    // - Завой наляво → сила надясно (gForceX положителна = точка надясно)
                    val rawGForceX = -lateralAccel / 9.81f
                    val rawGForceY = -forwardAccelRaw / 9.81f

                    val deltaX = abs(rawGForceX - displayLX)
                    val deltaY = abs(rawGForceY - displayLY)
                    val alphaX = if (deltaX > 0.5f) 0.3f else 0.5f
                    val alphaY = if (deltaY > 0.5f) 0.3f else 0.5f

                    displayLX = alphaX * rawGForceX + (1f - alphaX) * displayLX
                    displayLY = alphaY * rawGForceY + (1f - alphaY) * displayLY

                    currentGForceX = displayLX
                    currentGForceY = displayLY
                } else {
                    val rawGForceX = linearX / 9.81f
                    // Keep sign convention consistent with calibrated branch:
                    // forward acceleration => negative longitudinal inertial G.
                    val rawGForceY = -linearY / 9.81f
                    longitudinalAccelMps2 = linearY

                    val deltaX = abs(rawGForceX - displayLX)
                    val deltaY = abs(rawGForceY - displayLY)
                    val alphaX = if (deltaX > 0.5f) 0.3f else 0.5f
                    val alphaY = if (deltaY > 0.5f) 0.3f else 0.5f

                    displayLX = alphaX * rawGForceX + (1f - alphaX) * displayLX
                    displayLY = alphaY * rawGForceY + (1f - alphaY) * displayLY

                    currentGForceX = displayLX
                    currentGForceY = displayLY
                }

                val stabilizedLongitudinalAccelMps2 = if (isMeasurementActive && gMeasurementStartTime > 0L) {
                    applyLongitudinalLowPassFilter(longitudinalAccelMps2, event.timestamp)
                } else {
                    longitudinalAccelMps2
                }

                if (isMeasurementActive && gMeasurementStartTime > 0L) {
                    val now = System.currentTimeMillis()

                    if (hasReliableLongitudinalAxis && now - lastLongitudinalAccelSampleTime >= LONGITUDINAL_ACCEL_SAMPLE_MS) {
                        synchronized(longitudinalAccelBuffer) {
                            var longitudinalTimestamp = (System.nanoTime() - measurementStartTimeNano).coerceAtLeast(0L)
                            if (longitudinalAccelTimeStamps.isNotEmpty() && longitudinalTimestamp <= longitudinalAccelTimeStamps.last()) {
                                longitudinalTimestamp = longitudinalAccelTimeStamps.last() + 1L
                            }
                            longitudinalAccelBuffer.addLast(stabilizedLongitudinalAccelMps2)
                            longitudinalAccelTimeStamps.addLast(longitudinalTimestamp)
                            if (longitudinalAccelBuffer.size > SAMPLES_CAPACITY) {
                                longitudinalAccelBuffer.removeFirst()
                                longitudinalAccelTimeStamps.removeFirst()
                            }
                        }
                        lastLongitudinalAccelSampleTime = now
                    }

                    if (now - lastGSampleTime >= 100) {
                        synchronized(gSamplesBuffer) {
                            gSamplesBuffer.addLast(currentG)
                            gTimeStamps.addLast(System.nanoTime() - measurementStartTimeNano)
                            if (gSamplesBuffer.size > SAMPLES_CAPACITY) {
                                gSamplesBuffer.removeFirst()
                                gTimeStamps.removeFirst()
                            }
                        }
                        lastGSampleTime = now
                    }
                }
                expireLinearAccelTriggerIfStale()
                if (currentMeasurementMode != "NORMAL") {
                    when {
                        phoneRolloutIntegrating -> updatePhoneRolloutIntegration(event.values, System.nanoTime())
                        !linearAccelTriggered -> checkLinearAccelStart(event.values)
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun onNewLocation(loc: Location) {
        val gpsAccel = lastLocation?.let { prev ->
            val dt = if (loc.elapsedRealtimeNanos > prev.elapsedRealtimeNanos && prev.elapsedRealtimeNanos > 0L) {
                (loc.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1_000_000_000.0
            } else {
                (loc.time - prev.time).coerceAtLeast(1L) / 1000.0
            }
            if (dt > 0 && dt < 5.0) ((loc.speed - prev.speed) / dt).toFloat() else 0f
        } ?: 0f
        val previousLocation = lastLocation
        lastLocation = loc
        val newSpeed = GnssSpeedSanitizer.sanitizeFromLocation(
            location = loc,
            previous = previousLocation
        )
        val oldSpeed = currentSpeed
        if (isPreWarmingMode) {
            gpsWarmupLocations.add(loc); if (gpsWarmupLocations.size > 10) gpsWarmupLocations.removeAt(0)
            currentSpeed = newSpeed; return
        }
        if (isMeasurementActive && gMeasurementStartTime > 0L) {
            val now = System.currentTimeMillis()
            val relativeTimeNanos = resolveRelativeMeasurementTimeNanos(loc)
            val relativeReceiveTimeNanos = resolveReceiveRelativeMeasurementTimeNanos()
            if (now - lastGPSAccelSampleTime >= 100) {
                synchronized(gpsAccelBuffer) {
                    var gpsAccelTimestamp = relativeTimeNanos
                    if (gpsAccelTimeStamps.isNotEmpty() && gpsAccelTimestamp <= gpsAccelTimeStamps.last()) {
                        gpsAccelTimestamp = gpsAccelTimeStamps.last() + 1L
                    }
                    gpsAccelBuffer.addLast(gpsAccel); gpsAccelTimeStamps.addLast(gpsAccelTimestamp)
                    if (gpsAccelBuffer.size > SAMPLES_CAPACITY) { gpsAccelBuffer.removeFirst(); gpsAccelTimeStamps.removeFirst() }
                }
                lastGPSAccelSampleTime = now
            }
            synchronized(speedSamplesBuffer) {
                var relTime = relativeTimeNanos
                if (speedTimeStamps.isNotEmpty() && relTime <= speedTimeStamps.last()) {
                    relTime = speedTimeStamps.last() + 1L
                }
                var receiveRelTime = relativeReceiveTimeNanos
                if (speedReceiveTimeStamps.isNotEmpty() && receiveRelTime <= speedReceiveTimeStamps.last()) {
                    receiveRelTime = speedReceiveTimeStamps.last() + 1L
                }
                val sampleAgeNs = (receiveRelTime - relTime).coerceAtLeast(0L)

                speedSamplesBuffer.addLast(newSpeed)
                speedTimeStamps.addLast(relTime)
                speedReceiveTimeStamps.addLast(receiveRelTime)
                speedSampleAgeNanos.addLast(sampleAgeNs)
                if (speedSamplesBuffer.size > SPEED_SAMPLES_CAPACITY) {
                    speedSamplesBuffer.removeFirst()
                    speedTimeStamps.removeFirst()
                    speedReceiveTimeStamps.removeFirst()
                    speedSampleAgeNanos.removeFirst()
                }
                if (time0to100Nanos == 0L && speedSamplesBuffer.size >= 2) {
                    val pV = speedSamplesBuffer.elementAt(speedSamplesBuffer.size - 2); val pT = speedTimeStamps.elementAt(speedTimeStamps.size - 2)
                    if (pV < 100f && newSpeed >= 100f) time0to100Nanos = pT + ((relTime - pT) * (100f - pV) / (newSpeed - pV)).toLong()
                }
                if (time0to200Nanos == 0L && speedSamplesBuffer.size >= 2) {
                    val pV = speedSamplesBuffer.elementAt(speedSamplesBuffer.size - 2); val pT = speedTimeStamps.elementAt(speedTimeStamps.size - 2)
                    if (pV < 200f && newSpeed >= 200f) time0to200Nanos = pT + ((relTime - pT) * (200f - pV) / (newSpeed - pV)).toLong()
                }
            }
        }
        trackAcceleration(oldSpeed, newSpeed)
        currentSpeed = newSpeed; if (currentSpeed > maxSpeed) maxSpeed = currentSpeed
        updateTotalDistance(loc)
    }

    private fun updateTotalDistance(loc: Location) {
        // Ignore low-quality fixes entirely.
        if (loc.accuracy > 25f) return

        val prev = lastLocationForDistance
        // CRITICAL: always advance the anchor to the latest usable fix. Previously the anchor
        // moved only when a segment was accepted, so a single gap > 5s (red light, roundabout,
        // tunnel, locked screen) left a stale anchor and every later segment kept failing the
        // dt check — freezing the distance permanently after a few seconds.
        lastLocationForDistance = loc
        if (prev == null) return

        val dt = (loc.time - prev.time) / 1000.0
        if (dt <= 0.0) return
        // Skip this one segment if too much time elapsed between usable fixes, but the anchor is
        // already refreshed above so accumulation resumes immediately on the next fix.
        if (dt > 5.0) return

        val dist = prev.distanceTo(loc)
        val speedMps = dist / dt
        // Accept plausible driving segments only: ignore standing jitter and impossible jumps
        // (70 m/s ≈ 252 km/h covers any real road speed while rejecting GPS teleports).
        if (dist in 0.3f..200f && speedMps < 70f) {
            totalDistance += dist
        }
    }

    private fun trackAcceleration(oldSpeed: Float, newSpeed: Float) {
        val now = System.nanoTime()
        accelerationTracking.speedHistory.add(SpeedPoint(newSpeed, now))
        accelerationTracking.speedHistory.removeAll { it.timestamp < now - 10_000_000_000L }
        if (newSpeed < 2f) {
            accelerationTracking.hasFullyStopped = true; accelerationTracking.state = AccelerationState.IDLE
            accelerationTracking.ranges.forEach { it.isActive = false }; return
        }
        when (accelerationTracking.state) {
            AccelerationState.IDLE -> if (newSpeed < 5f && newSpeed > oldSpeed + 0.5f) {
                accelerationTracking.state = AccelerationState.ACCELERATING; accelerationTracking.hasFullyStopped = false
                accelerationTracking.ranges.forEach { if (it.requiresFullStop) { it.isActive = true; it.startTime = now } }
            }
            AccelerationState.ACCELERATING -> {
                if (newSpeed < oldSpeed - 1.0f) { accelerationTracking.ranges.forEach { it.isActive = false }; accelerationTracking.state = AccelerationState.IDLE }
                else accelerationTracking.ranges.forEach { if (it.isActive && newSpeed >= it.endSpeed) { it.results.add(now - it.startTime); it.isActive = false } }
            }
            AccelerationState.COMPLETED -> if (newSpeed < 2f) accelerationTracking.state = AccelerationState.IDLE
        }
        accelerationTracking.syncFromRanges()
    }

    private fun sendGpsHzBroadcast(hz: Double) { sendBroadcast(Intent(GPS_HZ_BROADCAST).apply { putExtra(EXTRA_GPS_HZ, hz) }) }
    private fun hasRequiredPermissions(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun resolveRunOrientationIsLandscape(): Boolean {
        return activeRunOrientationLandscape
            ?: (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    private fun reloadLeanCalibrationForSelectedProfile(forceResetRuntime: Boolean = false) {
        val selectedProfileId = ProfileStorage.getSelectedProfileId(this)
        val profileChanged = selectedProfileId != selectedProfileIdForLeanCalibration
        if (profileChanged) {
            selectedProfileIdForLeanCalibration = selectedProfileId
        }
        DragCalibration.setProfile(selectedProfileId)
        if (profileChanged || forceResetRuntime) {
            runtimeLeanOffsetDeg = 0f
        }
        lastLeanOrientationLandscape = null
        updateProfileLeanOffsetForOrientation(resolveRunOrientationIsLandscape())
    }

    private fun updateProfileLeanOffsetForOrientation(isLandscape: Boolean) {
        val baseline = DragCalibration.getBaselineForOrientation(isLandscape)
        profileLeanOffsetDeg = if (baseline != null) {
            DragCalibration.activateOrientationRuntime(isLandscape)
            computeLeanOffsetDegFromBaseline(baseline, isLandscape)
        } else {
            // No fallback to legacy LeanCalibrationStore; Smart/Drag baseline is the only source.
            0f
        }
        offsetAngle = profileLeanOffsetDeg + runtimeLeanOffsetDeg
    }

    private fun computeLeanOffsetDegFromBaseline(baseline: FloatArray, isLandscape: Boolean): Float {
        // Same shared formula as Track / DragCalibrationActivity when universal axes exist.
        if (DragCalibration.isUniversalCalibrated) {
            return DragCalibration.computeLeanOffsetDegFromBaseline(baseline)
        }

        val mag = sqrt(
            baseline[0] * baseline[0] +
                baseline[1] * baseline[1] +
                baseline[2] * baseline[2]
        ).coerceAtLeast(0.0001f)

        val leanSign = resolveLeanDirectionSign(isLandscape)

        val normalizedComponent = if (isLandscape) {
            (leanSign * baseline[1]) / mag
        } else {
            baseline[0] / mag
        }

        return (-Math.toDegrees(asin(normalizedComponent.coerceIn(-1f, 1f).toDouble()))).toFloat()
            .coerceIn(-89f, 89f)
    }

    /** Same gate as Track: calibrated bike axes when universal calib + gyro exist. */
    private fun useCalibratedBikeLeanAxes(): Boolean {
        return gyroscope != null && DragCalibration.isUniversalCalibrated
    }

    private fun resolveLeanDirectionSign(isLandscape: Boolean): Float {
        if (!isLandscape) return 1f
        return when (resolveDisplayRotation()) {
            Surface.ROTATION_90 -> -1f
            Surface.ROTATION_270 -> 1f
            else -> 1f
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveDisplayRotation(): Int {
        val dm = getSystemService(DisplayManager::class.java)
        val rotationFromDisplayManager = runCatching {
            dm?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
        }.getOrNull()

        if (rotationFromDisplayManager != null) {
            return rotationFromDisplayManager
        }

        // Service context is not always display-associated on newer Android versions.
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        return wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
    }

    private fun resetLeanFusionState(forceResetRuntime: Boolean) {
        if (forceResetRuntime) {
            runtimeLeanOffsetDeg = 0f
        }
        latestRollRateDegPerSec = 0f
        latestYawRateDegPerSec = 0f
        leanAccelGate.reset()
        gyroIntegratedLeanDeg = 0f
        hasGyroIntegratedLean = false
        leanGyroIntegrationTimestampNs = 0L
        lastGyroMagnitude = 0f
        filteredAngle = 0f
        currentCalibratedAngle = 0f
        lastLeanOrientationLandscape = null
        madgwick.reset()
        lastMadgwickUpdateNs = 0L
        latestGyroForMadgwick[0] = 0f
        latestGyroForMadgwick[1] = 0f
        latestGyroForMadgwick[2] = 0f
        updateProfileLeanOffsetForOrientation(resolveRunOrientationIsLandscape())
    }

    private fun updateLeanFusionFromGyroscope(event: SensorEvent) {
        val isLandscape = resolveRunOrientationIsLandscape()
        val leanSign = resolveLeanDirectionSign(isLandscape)
        val gx = event.values[0]
        val gy = event.values[1]
        val gz = event.values[2]
        latestGyroForMadgwick[0] = gx
        latestGyroForMadgwick[1] = gy
        latestGyroForMadgwick[2] = gz

        val gyroMag = sqrt(gx * gx + gy * gy + gz * gz)
        lastGyroMagnitude = 0.2f * gyroMag + 0.8f * lastGyroMagnitude

        val useCalibratedRollAxis = useCalibratedBikeLeanAxes()
        val rawRollRateRad = if (useCalibratedRollAxis) {
            val fw = DragCalibration.forwardVector
            gx * fw[0] + gy * fw[1] + gz * fw[2]
        } else if (isLandscape) {
            gx * leanSign
        } else {
            gy
        }
        val rollRateDeg = if (useCalibratedRollAxis) {
            rawRollRateRad * RAD_TO_DEG
        } else {
            -rawRollRateRad * RAD_TO_DEG
        }
        val rollRateFilterAlpha = if (useCalibratedRollAxis) 0.16f else 0.25f
        latestRollRateDegPerSec =
            rollRateFilterAlpha * rollRateDeg + (1f - rollRateFilterAlpha) * latestRollRateDegPerSec
        val down = DragCalibration.gravityVector
        val rawYawDeg = if (useCalibratedRollAxis) {
            MotorcycleLeanAccelGate.yawRateDegPerSec(gx, gy, gz, down[0], down[1], down[2])
        } else {
            gz * RAD_TO_DEG
        }
        latestYawRateDegPerSec = 0.18f * rawYawDeg + 0.82f * latestYawRateDegPerSec

        if (hasGyroIntegratedLean && leanGyroIntegrationTimestampNs > 0L) {
            val dtSec = ((event.timestamp - leanGyroIntegrationTimestampNs) / 1_000_000_000f).coerceIn(0f, 0.06f)
            if (dtSec > 0f) {
                gyroIntegratedLeanDeg = (gyroIntegratedLeanDeg + latestRollRateDegPerSec * dtSec).coerceIn(-89f, 89f)
            }
        }
        leanGyroIntegrationTimestampNs = event.timestamp
    }

    private fun updateLeanFusionFromAccelerometer(
        x: Float,
        y: Float,
        z: Float,
        linearX: Float,
        linearY: Float,
        linearZ: Float,
        isLandscape: Boolean
    ) {
        if (lastLeanOrientationLandscape == null || lastLeanOrientationLandscape != isLandscape) {
            updateProfileLeanOffsetForOrientation(isLandscape)
            hasGyroIntegratedLean = false
            leanGyroIntegrationTimestampNs = 0L
            leanAccelGate.reset()
            lastLeanOrientationLandscape = isLandscape
        }

        val leanSign = resolveLeanDirectionSign(isLandscape)
        val useAdvancedLeanFusion = useCalibratedBikeLeanAxes()

        val leanGx: Float
        val leanGy: Float
        val leanGz: Float
        if (gyroscope != null) {
            if (!madgwick.isInitialized) {
                madgwick.seedFromAccelerometer(x, y, z)
            }
            val dtSec = if (lastMadgwickUpdateNs > 0L) {
                ((SystemClock.elapsedRealtimeNanos() - lastMadgwickUpdateNs) / 1_000_000_000.0)
                    .toFloat()
                    .coerceIn(0.001f, 0.05f)
            } else {
                0.01f
            }
            madgwick.samplePeriodSec = dtSec
            madgwick.update(
                latestGyroForMadgwick[0],
                latestGyroForMadgwick[1],
                latestGyroForMadgwick[2],
                x, y, z
            )
            lastMadgwickUpdateNs = SystemClock.elapsedRealtimeNanos()
            val mg = madgwick.getGravityVector()
            gravity[0] = mg[0]
            gravity[1] = mg[1]
            gravity[2] = mg[2]
            leanGx = mg[0]
            leanGy = mg[1]
            leanGz = mg[2]
        } else {
            leanGx = gravity[0]
            leanGy = gravity[1]
            leanGz = gravity[2]
        }

        val totalGravity = sqrt(leanGx * leanGx + leanGy * leanGy + leanGz * leanGz)
        val accelReferenceTilt = if (totalGravity > 0f) {
            if (useAdvancedLeanFusion) {
                val rv = DragCalibration.rightVector
                val rightComponent = ((leanGx * rv[0] + leanGy * rv[1] + leanGz * rv[2]) / totalGravity)
                    .toDouble()
                    .coerceIn(-1.0, 1.0)
                Math.toDegrees(asin(rightComponent)).toFloat()
            } else if (isLandscape) {
                (-Math.toDegrees(asin(((leanSign * leanGy) / totalGravity).toDouble().coerceIn(-1.0, 1.0)))).toFloat()
            } else {
                (-Math.toDegrees(asin((leanGx / totalGravity).toDouble().coerceIn(-1.0, 1.0)))).toFloat()
            }
        } else {
            0f
        }

        if (!hasGyroIntegratedLean) {
            gyroIntegratedLeanDeg = accelReferenceTilt
            hasGyroIntegratedLean = true
        }

        val dynamicLinearMag = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)
        val correctionGain = if (gyroscope == null) {
            val dynamicLoadG = (dynamicLinearMag / SensorManager.GRAVITY_EARTH).coerceAtLeast(0f)
            val accelMotionTrust = (1f - dynamicLoadG * 0.55f).coerceIn(0.18f, 1f)
            val gyroSpinPenalty = (lastGyroMagnitude / 4.0f).coerceIn(0f, 1f)
            val accelTrust = (accelMotionTrust * (1f - 0.25f * gyroSpinPenalty)).coerceIn(0.15f, 1f)
            if (useAdvancedLeanFusion) {
                (0.04f + (0.20f - 0.04f) * accelTrust).coerceIn(0.04f, 0.20f)
            } else {
                (MIN_ACCEL_CORRECTION + (MAX_ACCEL_CORRECTION - MIN_ACCEL_CORRECTION) * accelTrust)
                    .coerceIn(MIN_ACCEL_CORRECTION, MAX_ACCEL_CORRECTION)
            }
        } else {
            val speedMps = currentSpeed / 3.6f
            val kinematicLatG = MotorcycleLeanAccelGate.kinematicLateralG(speedMps, latestYawRateDegPerSec)
            leanAccelGate.correctionGain(
                speedMps = speedMps,
                yawRateDegPerSec = latestYawRateDegPerSec,
                kinematicLateralG = kinematicLatG,
                gyroLeanDeg = gyroIntegratedLeanDeg,
                accelLeanDeg = accelReferenceTilt,
                straightGain = MotorcycleLeanAccelGate.STRAIGHT_ACCEL_GAIN
            )
        }

        gyroIntegratedLeanDeg += correctionGain * (accelReferenceTilt - gyroIntegratedLeanDeg)
        filteredAngle = gyroIntegratedLeanDeg
        currentCalibratedAngle = (filteredAngle - offsetAngle).coerceIn(-90f, 90f)

        if (currentCalibratedAngle < maxLeftAngle) maxLeftAngle = currentCalibratedAngle
        if (currentCalibratedAngle > maxRightAngle) maxRightAngle = currentCalibratedAngle
    }

    private fun expireLinearAccelTriggerIfStale() {
        val now = System.nanoTime()
        if (phoneRolloutIntegrating) {
            if (phoneRolloutLatchTimeNs > 0L &&
                now - phoneRolloutLatchTimeNs > PHONE_ROLLOUT_MAX_INTEGRATION_NS
            ) {
                Log.d("ForegroundService", "⏳ Phone 1ft rollout timed out — reset latch")
                resetLinearAccelTrigger()
            }
            return
        }
        if (!linearAccelTriggered || linearAccelTriggerTime <= 0L) return
        if (now - linearAccelTriggerTime > LINEAR_ACCEL_TRIGGER_MAX_AGE_NS) {
            resetLinearAccelTrigger()
        }
    }

    /**
     * Updates filtered forward/lateral and returns whether launch threshold is met.
     */
    private fun updateLaunchTriggerFilters(rawAccel: FloatArray): Boolean {
        val isLandscape = resolveRunOrientationIsLandscape()
        val hasOrientationCalibration = DragCalibration.hasCalibrationFor(isLandscape) ||
            DragCalibration.isLandscapeCalibrated ||
            DragCalibration.isPortraitCalibrated
        val hasUniversalCalibration = DragCalibration.isUniversalCalibrated

        if (!hasOrientationCalibration && !hasUniversalCalibration) {
            triggerForwardFiltered = 0f
            triggerLateralFiltered = 0f
            return false
        }

        if (hasOrientationCalibration) {
            val calibrationOrientation = when {
                DragCalibration.hasCalibrationFor(isLandscape) -> isLandscape
                DragCalibration.isLandscapeCalibrated -> true
                else -> false
            }

            val forward = DragCalibration.getForwardAcceleration(rawAccel, calibrationOrientation)
            val lateral = DragCalibration.getLateralAcceleration(rawAccel, calibrationOrientation)

            triggerForwardFiltered =
                triggerFilterAlpha * forward + (1f - triggerFilterAlpha) * triggerForwardFiltered
            triggerLateralFiltered =
                triggerFilterAlpha * lateral + (1f - triggerFilterAlpha) * triggerLateralFiltered

            val maxVibrPerAxis = DragCalibration.getMaxVibrationsPerAxis(calibrationOrientation)
            val threshold = if (maxVibrPerAxis != null) {
                val maxVibr = kotlin.math.max(
                    maxVibrPerAxis[0],
                    kotlin.math.max(maxVibrPerAxis[1], maxVibrPerAxis[2])
                )
                if (maxVibr > 0.1f && maxVibr < 1.2f) {
                    maxVibr * 1.35f + 0.25f
                } else {
                    0.5f
                }
            } else {
                0.5f
            }

            return triggerForwardFiltered > threshold &&
                triggerForwardFiltered > triggerLateralFiltered * 2.0f
        }

        val liveGravity = floatArrayOf(gravity[0], gravity[1], gravity[2])
        val forwardRaw = DragCalibration.getSignedForwardAcceleration(rawAccel, liveGravity)
        val lateralRaw = abs(DragCalibration.getSignedLateralAcceleration(rawAccel, liveGravity))

        triggerForwardFiltered =
            triggerFilterAlpha * forwardRaw + (1f - triggerFilterAlpha) * triggerForwardFiltered
        triggerLateralFiltered =
            triggerFilterAlpha * lateralRaw + (1f - triggerFilterAlpha) * triggerLateralFiltered

        val maxVibr = kotlin.math.max(
            DragCalibration.maxVibrXUniversal,
            kotlin.math.max(DragCalibration.maxVibrYUniversal, DragCalibration.maxVibrZUniversal)
        )
        val threshold = if (maxVibr > 0.1f && maxVibr < 1.2f) {
            maxVibr * 1.35f + 0.25f
        } else {
            0.5f
        }

        return triggerForwardFiltered > threshold &&
            triggerForwardFiltered > triggerLateralFiltered * 2.0f
    }

    private fun checkLinearAccelStart(rawAccel: FloatArray) {
        val isLandscape = resolveRunOrientationIsLandscape()
        val hasOrientationCalibration = DragCalibration.hasCalibrationFor(isLandscape) ||
            DragCalibration.isLandscapeCalibrated ||
            DragCalibration.isPortraitCalibrated
        val hasUniversalCalibration = DragCalibration.isUniversalCalibrated

        if (!hasOrientationCalibration && !hasUniversalCalibration) {
            consecutiveAccelSamples = 0
            triggerForwardFiltered = 0f
            triggerLateralFiltered = 0f
            return
        }

        val triggered = updateLaunchTriggerFilters(rawAccel)

        if (triggered) {
            consecutiveAccelSamples += 1
            consecutiveAccelSamples = consecutiveAccelSamples.coerceAtMost(4)
            if (consecutiveAccelSamples >= 2) {
                if (!isPhoneNearStopForStandingLaunch()) {
                    consecutiveAccelSamples = 0
                    return
                }
                armPhoneOfficialStartOrRollout()
            }
        } else {
            consecutiveAccelSamples = (consecutiveAccelSamples - 1).coerceAtLeast(0)
        }
    }

    /** Match DragRunPage FULL_STOP_REARM: do not latch a standing start while already rolling. */
    private fun isPhoneNearStopForStandingLaunch(): Boolean {
        if (lastLocation == null) return false
        return currentSpeed <= 3f
    }

    /**
     * Motion latch: with 1ft setting ON, integrate forward accel to [DragRolloutSettings.ROLLOUT_METERS]
     * before exposing official t0. With setting OFF, official t0 = latch time (legacy).
     */
    private fun armPhoneOfficialStartOrRollout() {
        val now = System.nanoTime()
        if (DragRolloutSettings.is1ftRolloutEnabled(this)) {
            phoneRolloutIntegrating = true
            phoneRolloutLatchTimeNs = now
            phoneRolloutDistanceM = 0f
            phoneRolloutVelocityMps = 0f
            phoneRolloutLastSampleNs = now
            linearAccelTriggered = false
            linearAccelTriggerTime = 0L
            Log.d("ForegroundService", "🚀 Phone launch latched — integrating 1ft rollout")
        } else {
            resetPhoneRolloutState()
            linearAccelTriggered = true
            linearAccelTriggerTime = now
            Log.d("ForegroundService", "🚀 Phone launch official t0 (1ft OFF)")
        }
    }

    private fun updatePhoneRolloutIntegration(rawAccel: FloatArray, nowNs: Long) {
        if (!phoneRolloutIntegrating) return
        updateLaunchTriggerFilters(rawAccel)

        val prevNs = phoneRolloutLastSampleNs
        if (prevNs <= 0L) {
            phoneRolloutLastSampleNs = nowNs
            return
        }
        val dtSec = ((nowNs - prevNs).toDouble() / 1_000_000_000.0).toFloat()
        phoneRolloutLastSampleNs = nowNs
        if (dtSec <= 0f || dtSec > PHONE_ROLLOUT_MAX_DT_SEC) return

        // Only count forward thrust toward the strip (clamp negatives to avoid rewind).
        val a = triggerForwardFiltered.coerceAtLeast(0f)
        val v0 = phoneRolloutVelocityMps
        val v1 = v0 + a * dtSec
        val ds = (v0 + v1) * 0.5f * dtSec
        val s0 = phoneRolloutDistanceM
        val s1 = s0 + ds.coerceAtLeast(0f)
        phoneRolloutVelocityMps = v1.coerceAtLeast(0f)

        val target = DragRolloutSettings.ROLLOUT_METERS
        if (s1 < target) {
            phoneRolloutDistanceM = s1
            return
        }

        val ratio = if (ds > 1e-6f) {
            ((target - s0) / ds).coerceIn(0f, 1f)
        } else {
            1f
        }
        val crossingNs = prevNs + ((nowNs - prevNs) * ratio.toDouble()).toLong()
        phoneRolloutDistanceM = s1
        phoneRolloutIntegrating = false
        linearAccelTriggered = true
        linearAccelTriggerTime = crossingNs
        Log.d(
            "ForegroundService",
            "🚀 Phone official t0 after 1ft (${"%.3f".format(s1)} m), v=${"%.2f".format(phoneRolloutVelocityMps)} m/s"
        )
    }
}
