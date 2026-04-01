 package com.example.clinometer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import android.content.res.Configuration
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.main.MainActivity
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import java.lang.Math

enum class AccelerationState {
    IDLE,
    ACCELERATING,
    COMPLETED
}

class ForegroundService : Service(), SensorEventListener {
    
    companion object {
        const val GPS_HZ_BROADCAST = "com.example.clinometer.GPS_HZ_UPDATE"
        const val EXTRA_GPS_HZ = "gps_hz"
        
        private const val NORMAL_SAMPLING_MS = 250L 
        private const val DRAG_SAMPLING_MS = 100L
        private const val RAD_TO_DEG = 57.29578f
        private const val MIN_ACCEL_CORRECTION = 0.03f
        private const val MAX_ACCEL_CORRECTION = 0.22f
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
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    var sessionStartTime: Long = 0
    var accelerationTracking = AccelerationData()

    // Lean angle fusion state (gyro + accel reference)
    private var latestRollRateDegPerSec = 0f
    private var gyroIntegratedLeanDeg = 0f
    private var hasGyroIntegratedLean = false
    private var leanGyroIntegrationTimestampNs = 0L
    private var lastGyroMagnitude = 0f
    private var runtimeLeanOffsetDeg = 0f
    private var profileLeanOffsetDeg = 0f
    private var lastLeanOrientationLandscape: Boolean? = null
    private var leanCalibrationSnapshot: LeanCalibrationSnapshot = LeanCalibrationSnapshot()
    private var selectedProfileIdForLeanCalibration: Long = -1L

    private val gravity = FloatArray(3)
    private val linearAcceleration = FloatArray(3)
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
    private var linearAccelTriggerTime = 0L
    private val REQUIRED_ACCEL_SAMPLES = 4
    private var consecutiveAccelSamples = 0
    private var triggerForwardFiltered = 0f
    private var triggerLateralFiltered = 0f
    private val triggerFilterAlpha = 0.35f
    private val triggerMinThreshold = 0.9f
    private val triggerDirectionalRatio = 2.5f
    private var currentMeasurementMode = "ALL"
    @Volatile private var activeRunOrientationLandscape: Boolean? = null

    private val SAMPLES_CAPACITY = 1000
    private val gSamplesBuffer: ArrayDeque<Float> = ArrayDeque(SAMPLES_CAPACITY)
    private val gpsAccelBuffer: ArrayDeque<Float> = ArrayDeque(SAMPLES_CAPACITY)
    private val gTimeStamps = ArrayDeque<Long>(SAMPLES_CAPACITY)
    private val gpsAccelTimeStamps = ArrayDeque<Long>(SAMPLES_CAPACITY)

    private var gMeasurementStartTime: Long = 0L
    private var measurementStartTimeNano: Long = 0L
    private var lastGSampleTime = 0L
    private var lastGPSAccelSampleTime = 0L
    private val speedSamplesBuffer: ArrayDeque<Float> = ArrayDeque(SAMPLES_CAPACITY)
    private val speedTimeStamps = ArrayDeque<Long>(SAMPLES_CAPACITY)

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

    fun getCurrentG(): Float = currentG
    fun getPeakG(): Float = peakG
    fun getCurrentGForceX(): Float = currentGForceX
    fun getCurrentGForceY(): Float = currentGForceY
    fun isLinearAccelTriggered(): Boolean = linearAccelTriggered
    fun getLinearAccelTriggerTime(): Long = linearAccelTriggerTime
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
    fun getAccelerationData(): AccelerationData = accelerationTracking
    fun getRecentGSamples(): List<Float> = synchronized(gSamplesBuffer) { gSamplesBuffer.toList() }
    fun getRecentGTimeStamps(): List<Long> = synchronized(gTimeStamps) { gTimeStamps.toList() }
    fun getRecentGpsAccelSamples(): List<Float> = synchronized(gpsAccelBuffer) { gpsAccelBuffer.toList() }
    fun getRecentGpsAccelTimeStamps(): List<Long> = synchronized(gpsAccelTimeStamps) { gpsAccelTimeStamps.toList() }
    fun getRecentSpeedSamples(): List<Float> = synchronized(speedSamplesBuffer) { speedSamplesBuffer.toList() }
    fun getRecentSpeedTimeStamps(): List<Long> = synchronized(speedTimeStamps) { speedTimeStamps.toList() }
    fun getMeasurementStartTimeNano(): Long = measurementStartTimeNano
    fun setMeasurementStartTimeNano(timeNanos: Long) { measurementStartTimeNano = timeNanos }
    fun getTime0to100Nanos(): Long = time0to100Nanos
    fun getTime0to200Nanos(): Long = time0to200Nanos
    fun getServiceDuration(): Long = if (actualStartTime == 0L) 0L else SystemClock.elapsedRealtime() - actualStartTime

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
        measurementStartTimeNano = System.nanoTime()
        lastGSampleTime = 0L; lastGPSAccelSampleTime = 0L
        isMeasurementActive = true; linearAccelTriggered = false; linearAccelTriggerTime = 0L; consecutiveAccelSamples = 0
        triggerForwardFiltered = 0f
        triggerLateralFiltered = 0f
        time0to100Nanos = 0L; time0to200Nanos = 0L
        currentG = 0f; peakG = 0f
        synchronized(gSamplesBuffer) { gSamplesBuffer.clear(); gTimeStamps.clear() }
        synchronized(gpsAccelBuffer) { gpsAccelBuffer.clear(); gpsAccelTimeStamps.clear() }
        synchronized(speedSamplesBuffer) { speedSamplesBuffer.clear(); speedTimeStamps.clear() }
        recordingHandler.removeCallbacks(recordingRunnable)
        recordingHandler.post(recordingRunnable)
    }

    fun stopMeasurement() {
        isMeasurementActive = false
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
        gMeasurementStartTime = 0L; lastGSampleTime = 0L; peakG = 0f
        triggerForwardFiltered = 0f
        triggerLateralFiltered = 0f
        synchronized(gSamplesBuffer) { gSamplesBuffer.clear(); gTimeStamps.clear() }
        synchronized(gpsAccelBuffer) { gpsAccelBuffer.clear(); gpsAccelTimeStamps.clear() }
        synchronized(speedSamplesBuffer) { speedSamplesBuffer.clear(); speedTimeStamps.clear() }
    }

    fun getStartTime(): Long = if (isPreWarmingMode) SystemClock.elapsedRealtime() else (if (actualStartTime == 0L) SystemClock.elapsedRealtime().also { actualStartTime = it } else actualStartTime)

    override fun onCreate() {
        super.onCreate()
        resetData(); serviceStartTime = SystemClock.elapsedRealtime(); sessionStartTime = System.currentTimeMillis()
        if (!hasRequiredPermissions()) { stopSelf(); return }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "${packageName}:wakeLock")
        wakeLock.acquire()
        startForeground(1, createNotification())
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        setupLocationUpdates()
        if (!isPreWarmingMode) registerSensors()
    }

    private fun recordTelemetrySnapshot() {
        if (!isMeasurementActive || actualStartTime == 0L) return
        val location = lastLocation ?: return
        val currentTime = SystemClock.elapsedRealtime()
        routePoints.add(RoutePoint(GeoPoint(location.latitude, location.longitude), currentSpeed, currentCalibratedAngle, currentTime - actualStartTime, System.currentTimeMillis()))
        if (routePoints.size > 10000) optimizeRoutePoints()
    }

    private fun optimizeRoutePoints() {
        if (routePoints.size < 2000) return
        val optimized = mutableListOf<RoutePoint>()
        optimized.add(routePoints.first())
        for (i in 1 until routePoints.size - 1) {
            val prev = routePoints[i-1]; val curr = routePoints[i]; val next = routePoints[i+1]
            val angleChanged = abs(curr.angle - prev.angle) > 0.5f || abs(curr.angle - next.angle) > 0.5f
            val speedChanged = abs(curr.speed - prev.speed) > 1.0f
            if (angleChanged || speedChanged || i % 10 == 0) optimized.add(curr)
        }
        optimized.add(routePoints.last())
        routePoints.clear(); routePoints.addAll(optimized)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (it.getBooleanExtra("PRE_WARMING_MODE", false)) isPreWarmingMode = true
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
    }

    private fun registerSensors() {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    private fun createNotification(): Notification {
        val channelId = "tracking_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Tracking", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId).setContentTitle("RaceMoto Tracking").setContentText("Recording data...").setSmallIcon(R.drawable.ic_launcher_foreground).setContentIntent(pendingIntent).build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMeasurement()
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
        fusedClient.removeLocationUpdates(locCallback); sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (isPreWarmingMode) return
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

                // Използваме калибрацията за определяне на посоките (ако е налична)
                if (DragCalibration.isUniversalCalibrated) {
                    val rawAccel = floatArrayOf(x, y, z)
                    val calibratedGravity = DragCalibration.gravityVector

                    val forwardAccel = DragCalibration.getSignedForwardAcceleration(rawAccel, calibratedGravity)
                    val lateralAccel = DragCalibration.getSignedLateralAcceleration(rawAccel, calibratedGravity)

                    // Конвертираме в g-сили и показваме ИНЕРЦИОННАТА СИЛА
                    // Инерционна сила = обратна на ускорението:
                    // - Ускорение напред → сила назад (gForceY положителна = точка надолу)
                    // - Спиране → сила напред (gForceY отрицателна = точка нагоре)
                    // - Завой надясно → сила наляво (gForceX отрицателна = точка наляво)
                    // - Завой наляво → сила надясно (gForceX положителна = точка надясно)
                    val rawGForceX = -lateralAccel / 9.81f
                    val rawGForceY = -forwardAccel / 9.81f

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
                    val rawGForceY = linearY / 9.81f

                    val deltaX = abs(rawGForceX - displayLX)
                    val deltaY = abs(rawGForceY - displayLY)
                    val alphaX = if (deltaX > 0.5f) 0.3f else 0.5f
                    val alphaY = if (deltaY > 0.5f) 0.3f else 0.5f

                    displayLX = alphaX * rawGForceX + (1f - alphaX) * displayLX
                    displayLY = alphaY * rawGForceY + (1f - alphaY) * displayLY

                    currentGForceX = displayLX
                    currentGForceY = displayLY
                }

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
                }
                if (currentMeasurementMode != "NORMAL" && !linearAccelTriggered) checkLinearAccelStart(event.values)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun onNewLocation(loc: Location) {
        val gpsAccel = lastLocation?.let { prev ->
            val dt = (loc.time - prev.time).coerceAtLeast(1L) / 1000.0
            if (dt > 0 && dt < 5.0) ((loc.speed - prev.speed) / dt).toFloat() else 0f
        } ?: 0f
        lastLocation = loc; val newSpeed = loc.speed * 3.6f; val oldSpeed = currentSpeed
        if (isPreWarmingMode) {
            gpsWarmupLocations.add(loc); if (gpsWarmupLocations.size > 10) gpsWarmupLocations.removeAt(0)
            currentSpeed = newSpeed; return
        }
        if (isMeasurementActive && gMeasurementStartTime > 0L) {
            val now = System.currentTimeMillis()
            if (now - lastGPSAccelSampleTime >= 100) {
                synchronized(gpsAccelBuffer) {
                    gpsAccelBuffer.addLast(gpsAccel); gpsAccelTimeStamps.addLast(System.nanoTime() - measurementStartTimeNano)
                    if (gpsAccelBuffer.size > SAMPLES_CAPACITY) { gpsAccelBuffer.removeFirst(); gpsAccelTimeStamps.removeFirst() }
                }
                lastGPSAccelSampleTime = now
            }
            synchronized(speedSamplesBuffer) {
                speedSamplesBuffer.addLast(newSpeed); val relTime = System.nanoTime() - measurementStartTimeNano; speedTimeStamps.addLast(relTime)
                if (speedSamplesBuffer.size > SAMPLES_CAPACITY) { speedSamplesBuffer.removeFirst(); speedTimeStamps.removeFirst() }
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
        lastLocationForDistance?.let { prev ->
            if (loc.accuracy <= 25f && prev.accuracy <= 25f) {
                val dist = prev.distanceTo(loc); val dt = (loc.time - prev.time) / 1000.0
                if (dist in 0.3f..150f && dt in 0.2..5.0 && (dist / dt) < 35f) {
                    totalDistance += dist; lastLocationForDistance = loc
                }
            }
        } ?: run { if (loc.accuracy <= 25f) lastLocationForDistance = loc }
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
        if (profileChanged || forceResetRuntime) {
            runtimeLeanOffsetDeg = 0f
        }
        lastLeanOrientationLandscape = null
        updateProfileLeanOffsetForOrientation(resolveRunOrientationIsLandscape())
    }

    private fun updateProfileLeanOffsetForOrientation(isLandscape: Boolean) {
        // Reset-v2: runtime lean uses only live zeroing (calibrateZero), no separate lean store offsets.
        profileLeanOffsetDeg = 0f
        offsetAngle = runtimeLeanOffsetDeg
    }

    private fun resetLeanFusionState(forceResetRuntime: Boolean) {
        if (forceResetRuntime) {
            runtimeLeanOffsetDeg = 0f
        }
        latestRollRateDegPerSec = 0f
        gyroIntegratedLeanDeg = 0f
        hasGyroIntegratedLean = false
        leanGyroIntegrationTimestampNs = 0L
        lastGyroMagnitude = 0f
        filteredAngle = 0f
        currentCalibratedAngle = 0f
        lastLeanOrientationLandscape = null
        updateProfileLeanOffsetForOrientation(resolveRunOrientationIsLandscape())
    }

    private fun updateLeanFusionFromGyroscope(event: SensorEvent) {
        val isLandscape = resolveRunOrientationIsLandscape()
        val gyroMag = sqrt(
            event.values[0] * event.values[0] +
                event.values[1] * event.values[1] +
                event.values[2] * event.values[2]
        )
        lastGyroMagnitude = 0.2f * gyroMag + 0.8f * lastGyroMagnitude

        val rawRollRateRad = if (isLandscape) event.values[0] else event.values[1]
        val rollRateDeg = -rawRollRateRad * RAD_TO_DEG
        latestRollRateDegPerSec = 0.25f * rollRateDeg + 0.75f * latestRollRateDegPerSec

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
            lastLeanOrientationLandscape = isLandscape
        }

        val totalGravity = sqrt(x * x + y * y + z * z)
        val accelReferenceTilt = if (totalGravity > 0f) {
            if (isLandscape) {
                (-Math.toDegrees(Math.asin((y / totalGravity).toDouble().coerceIn(-1.0, 1.0)))).toFloat()
            } else {
                (-Math.toDegrees(Math.asin((x / totalGravity).toDouble().coerceIn(-1.0, 1.0)))).toFloat()
            }
        } else {
            0f
        }

        if (!hasGyroIntegratedLean) {
            gyroIntegratedLeanDeg = accelReferenceTilt
            hasGyroIntegratedLean = true
        }

        val dynamicLinearMag = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)
        val dynamicLoadG = (dynamicLinearMag / SensorManager.GRAVITY_EARTH).coerceAtLeast(0f)
        val accelMotionTrust = (1f - dynamicLoadG * 0.55f).coerceIn(0.18f, 1f)
        val gyroSpinPenalty = (lastGyroMagnitude / 4.0f).coerceIn(0f, 1f)
        val accelTrust = (accelMotionTrust * (1f - 0.25f * gyroSpinPenalty)).coerceIn(0.15f, 1f)
        val correctionGain = (MIN_ACCEL_CORRECTION + (MAX_ACCEL_CORRECTION - MIN_ACCEL_CORRECTION) * accelTrust)
            .coerceIn(MIN_ACCEL_CORRECTION, MAX_ACCEL_CORRECTION)

        gyroIntegratedLeanDeg += correctionGain * (accelReferenceTilt - gyroIntegratedLeanDeg)
        filteredAngle = gyroIntegratedLeanDeg
        currentCalibratedAngle = (filteredAngle - offsetAngle).coerceIn(-90f, 90f)

        if (currentCalibratedAngle < maxLeftAngle) maxLeftAngle = currentCalibratedAngle
        if (currentCalibratedAngle > maxRightAngle) maxRightAngle = currentCalibratedAngle
    }

    private fun checkLinearAccelStart(rawAccel: FloatArray) {
        if (!DragCalibration.isUniversalCalibrated) {
            consecutiveAccelSamples = 0
            return
        }

        val liveGravity = floatArrayOf(gravity[0], gravity[1], gravity[2])
        val forwardRaw = DragCalibration.getSignedForwardAcceleration(rawAccel, liveGravity)
        val lateralRaw = abs(DragCalibration.getSignedLateralAcceleration(rawAccel, liveGravity))

        triggerForwardFiltered = triggerFilterAlpha * forwardRaw + (1f - triggerFilterAlpha) * triggerForwardFiltered
        triggerLateralFiltered = triggerFilterAlpha * lateralRaw + (1f - triggerFilterAlpha) * triggerLateralFiltered

        val threshold = DragCalibration.getDynamicThreshold().coerceAtLeast(triggerMinThreshold)
        val isForwardLaunch =
            triggerForwardFiltered > threshold &&
                triggerForwardFiltered > triggerLateralFiltered * triggerDirectionalRatio

        if (isForwardLaunch) {
            consecutiveAccelSamples++
            if (consecutiveAccelSamples >= REQUIRED_ACCEL_SAMPLES) {
                linearAccelTriggered = true
                linearAccelTriggerTime = System.nanoTime()
            }
        } else {
            consecutiveAccelSamples = (consecutiveAccelSamples - 1).coerceAtLeast(0)
        }
    }
}
