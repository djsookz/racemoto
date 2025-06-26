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
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
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
import org.osmdroid.util.GeoPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

enum class AccelerationState {
    IDLE,           // Не измерваме нищо
    ACCELERATING,   // Ускоряваме от спирка
    COMPLETED       // Завършили сме всички измервания
}

class ForegroundService : Service(), SensorEventListener {

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

    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locCallback: LocationCallback
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    var sessionStartTime: Long = 0
    var accelerationTracking = AccelerationData()

    private var lastDataSaveTime = 0L
    private val DATA_SAVE_INTERVAL = 250L

    inner class LocalBinder : Binder() {
        fun getService(): ForegroundService = this@ForegroundService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

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

    data class SpeedPoint(
        val speed: Float,
        val timestamp: Long  // Тук ще използваме nanoTime
    )

    data class GPSSpeedPoint(
        val speed: Float,
        val timestamp: Long,
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float
    )

    data class AccelerationData(
        // Запазваме старите променливи за съвместимост
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

        // Нови State Machine променливи
        var state: AccelerationState = AccelerationState.IDLE,
        var ranges: MutableList<AccelerationRange> = mutableListOf(
            AccelerationRange("0-100", 3f, 100f, 20_000_000_000L, requiresFullStop = true),
            AccelerationRange("0-200", 3f, 200f, 60_000_000_000L, requiresFullStop = true)
        ),
        var lastSpeed: Float = 0f
    ) {
        // Запазваме старите функции за съвместимост
        fun best0to100() = times0to100.minOrNull() ?: lastBest0to100
        fun best0to200() = times0to200.minOrNull() ?: lastBest0to200
        fun best100to200() = times100to200.minOrNull() ?: lastBest100to200

        // Нови функции за синхронизация със старите променливи
        fun syncFromRanges() {
            val range030 = ranges.find { it.name == "0-100" }
            val range060 = ranges.find { it.name == "0-200" }

            isTracking0to100 = range030?.isActive == true
            isTracking0to200 = range060?.isActive == true
            isTracking100to200 = false // Махаме 30-60

            startTime0to100 = range030?.startTime ?: 0L
            startTime0to200 = range060?.startTime ?: 0L
            startTime100to200 = 0L

            if (range030?.results?.isNotEmpty() == true) {
                times0to100.addAll(range030.results)
                range030.results.clear()
            }
            if (range060?.results?.isNotEmpty() == true) {
                times0to200.addAll(range060.results)
                range060.results.clear()
            }
        }
    }

    fun getRoutePoints(): List<RoutePoint> = routePoints
    fun getMaxLeftAngle(): Float = maxLeftAngle
    fun getMaxRightAngle(): Float = maxRightAngle
    fun getMaxSpeed(): Float = maxSpeed
    fun getStartTime(): Long = startTime
    fun getCurrentAngle(): Float = currentCalibratedAngle
    fun getCurrentSpeed(): Float = currentSpeed
    fun getLastLocation(): Location? = lastLocation
    fun getAccelerationData(): AccelerationData = accelerationTracking

    fun resetAccelerationData() {
        accelerationTracking = AccelerationData()
    }

    fun resetData() {
        routePoints.clear()
        filteredAngle = 0f
        offsetAngle = filteredAngle
        currentCalibratedAngle = 0f
        maxLeftAngle = 0f
        maxRightAngle = 0f
        maxSpeed = 0f
        currentSpeed = 0f
        startTime = SystemClock.elapsedRealtime()
        lastDataSaveTime = 0L
        resetAccelerationData()
    }

    override fun onCreate() {
        super.onCreate()

        serviceStartTime = SystemClock.elapsedRealtime()
        sessionStartTime = System.currentTimeMillis()

        if (!hasRequiredPermissions()) {
            stopSelf()
            return
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "${packageName}:wakeLock")
        wakeLock.acquire()

        startForeground(1, createNotification())

        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        startTime = SystemClock.elapsedRealtime()

        setupLocationUpdates()
        registerSensors()
    }
    fun getServiceDuration(): Long {
        return SystemClock.elapsedRealtime() - serviceStartTime
    }

    private fun hasRequiredPermissions(): Boolean {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val yzSq = y * y + z * z
            val raw = if (yzSq > 0) {
                Math.toDegrees(atan2(x.toDouble(), sqrt(yzSq.toDouble()))).toFloat()
            } else 0f

            val delta = abs(raw - filteredAngle)
            val adaptiveAlpha = (0.01f + (delta / 45f)).coerceIn(0.05f, 0.3f)
            filteredAngle += adaptiveAlpha * (raw - filteredAngle)
            val calibrated = (offsetAngle - filteredAngle).coerceIn(-90f, 900f)
            currentCalibratedAngle = calibrated

            if (calibrated < maxLeftAngle) maxLeftAngle = calibrated
            if (calibrated > maxRightAngle) maxRightAngle = calibrated

            saveDataPointIfNeeded()
        }
    }

    private fun saveDataPointIfNeeded() {
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastDataSaveTime >= DATA_SAVE_INTERVAL) {
            lastDataSaveTime = currentTime

            lastLocation?.let { location ->
                val pt = RoutePoint(
                    geoPoint = GeoPoint(location.latitude, location.longitude),
                    speed = currentSpeed,
                    angle = currentCalibratedAngle,
                    timestamp = currentTime - startTime,
                    absoluteTime = location.time
                )
                routePoints.add(pt)
            }
        }
    }

    private fun optimizeRoutePoints() {
        if (routePoints.size > 5000) {
            val compressed = routePoints.filterIndexed { index, _ ->
                index % 2 == 0 || routePoints[index].speed > 5f
            }
            routePoints.clear()
            routePoints.addAll(compressed)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun setupLocationUpdates() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 150)
            .setMinUpdateIntervalMillis(50)
            .setWaitForAccurateLocation(false)
            .setMinUpdateDistanceMeters(0.2f)
            .setMaxUpdateDelayMillis(200)
            .build()

        locCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { onNewLocation(it) }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedClient.requestLocationUpdates(
                locationRequest,
                locCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun registerSensors() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun createNotification(): Notification {
        val channelId = "tracking_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "Tracking Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Канал за проследяване на сесия" }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(serviceChannel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Clinometer проследява")
            .setContentText("Активна сесия")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveDataPointIfNeeded()
        wakeLock.release()
        fusedClient.removeLocationUpdates(locCallback)
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun onNewLocation(loc: Location) {
        lastLocation = loc
        val newSpeed = loc.speed * 3.6f
        val oldSpeed = currentSpeed // Запазваме старата скорост

        trackAcceleration(oldSpeed, newSpeed)
        currentSpeed = newSpeed

        if (currentSpeed > maxSpeed) maxSpeed = currentSpeed
        if (SystemClock.elapsedRealtime() % 30_000 == 0L) {
            optimizeRoutePoints()
        }

        val pt = RoutePoint(
            geoPoint = GeoPoint(loc.latitude, loc.longitude),
            speed = currentSpeed,
            angle = currentCalibratedAngle,
            timestamp = SystemClock.elapsedRealtime() - startTime,
            absoluteTime = loc.time
        )
        routePoints.add(pt)
    }

    // Премахваме функцията hasStableSpeedAround30() защото вече не я използваме

    private fun trackAcceleration(oldSpeed: Float, newSpeed: Float) {
        val currentTime = System.nanoTime()

        // Запазваме историята (като преди)
        accelerationTracking.speedHistory.add(SpeedPoint(newSpeed, currentTime))
        val cutoff = currentTime - 10_000_000_000L
        accelerationTracking.speedHistory.removeAll { point -> point.timestamp < cutoff }

        // State Machine логика
        val isAccelerating = newSpeed > oldSpeed + 1.2f
        val isDecelerating = newSpeed < oldSpeed - 1.0f
        accelerationTracking.lastSpeed = oldSpeed

        // Детектираме пълна спирка
        if (newSpeed < 4f) {
            accelerationTracking.hasFullyStopped = true
            accelerationTracking.state = AccelerationState.IDLE
            resetAllActiveRanges("Full stop detected")
        }

        when (accelerationTracking.state) {
            AccelerationState.IDLE -> {
                if (accelerationTracking.hasFullyStopped && newSpeed > 3f && isAccelerating) {
                    accelerationTracking.state = AccelerationState.ACCELERATING
                    startMeasurements(currentTime, newSpeed)
                    accelerationTracking.hasFullyStopped = false
                }
            }

            AccelerationState.ACCELERATING -> {
                if (isDecelerating) {
                    cancelActiveRanges("Deceleration detected")
                    accelerationTracking.state = AccelerationState.IDLE
                    // Reset logic за compatibility
                    if (newSpeed < 5f) {
                        accelerationTracking.hasReached100 = false
                        accelerationTracking.hasReached200 = false
                    }
                } else {
                    updateMeasurements(newSpeed, currentTime)
                    checkTimeouts(currentTime)
                }
            }

            AccelerationState.COMPLETED -> {
                // Оставаме в този стат докато не спрем напълно
            }
        }

        // Синхронизираме със старите променливи за съвместимост
        accelerationTracking.syncFromRanges()
    }

    private fun startMeasurements(currentTime: Long, currentSpeed: Float) {
        accelerationTracking.ranges.forEach { range ->
            if (range.requiresFullStop ) {
                range.isActive = true
                range.startTime = currentTime
                Log.d("AccelTrack", "Started ${range.name} tracking at ${currentSpeed}km/h after full stop")
            }
        }
    }

    private fun updateMeasurements(newSpeed: Float, currentTime: Long) {
        val completedMeasurements = mutableListOf<AccelerationRange>()

        accelerationTracking.ranges.forEach { range ->
            when {
                // Завършваме активните ranges
                range.isActive && newSpeed >= range.endSpeed -> {
                    val duration = currentTime - range.startTime
                    range.results.add(duration)
                    range.isActive = false
                    completedMeasurements.add(range)
                    Log.d("AccelTrack", "${range.name}: ${"%.2f".format(duration / 1_000_000_000.0)}s")

                    // Compatibility logic
                    when (range.name) {
                        "0-100" -> accelerationTracking.hasReached100 = true
                        "0-200" -> accelerationTracking.hasReached200 = true
                    }
                }
            }
        }

        // Проверяваме дали всички ranges са завършени
        if (accelerationTracking.ranges.none { it.isActive }) {
            val hasCompletedAny = accelerationTracking.ranges.any { it.results.isNotEmpty() }
            if (hasCompletedAny) {
                accelerationTracking.state = AccelerationState.COMPLETED
                Log.d("AccelTrack", "All measurements completed!")
            }
        }
    }

    private fun resetAllActiveRanges(reason: String) {
        val activeRanges = accelerationTracking.ranges.filter { it.isActive }
        if (activeRanges.isNotEmpty()) {
            Log.d("AccelTrack", "Reset all active ranges: $reason")
            activeRanges.forEach { it.isActive = false }
        }
    }

    private fun cancelActiveRanges(reason: String) {
        accelerationTracking.ranges.filter { it.isActive }.forEach { range ->
            range.isActive = false
            Log.d("AccelTrack", "Canceled ${range.name}: $reason")
        }
    }

    private fun checkTimeouts(currentTime: Long) {
        accelerationTracking.ranges.filter { it.isActive }.forEach { range ->
            if (currentTime - range.startTime > range.timeout) {
                range.isActive = false
                Log.d("AccelTrack", "Stopped ${range.name} tracking - timeout")
            }
        }
    }

    private fun resetAccelerationTrackingFlags() {
        accelerationTracking.isTracking0to100 = false
        accelerationTracking.isTracking0to200 = false
        accelerationTracking.isTracking100to200 = false
    }
}