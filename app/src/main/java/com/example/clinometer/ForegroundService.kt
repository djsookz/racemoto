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

    data class AccelerationData(
        var isTracking0to100: Boolean = false,
        var isTracking0to200: Boolean = false,
        var isTracking100to200: Boolean = false,
        var lastBest0to100: Long = -1L,
        var lastBest0to200: Long = -1L,
        var lastBest100to200: Long = -1L,

        var startTime0to100: Long = 0L,
        var startTime0to200: Long = 0L,
        var startTime100to200: Long = 0L,

        var times0to100: MutableList<Long> = mutableListOf(),
        var times0to200: MutableList<Long> = mutableListOf(),
        var times100to200: MutableList<Long> = mutableListOf(),

        var hasReached100: Boolean = false,
        var hasReached200: Boolean = false,

        var speedHistory: MutableList<SpeedPoint> = mutableListOf(),
        var accelerationStartSpeed: Float = 0f
    )
    {
        // Добавяме функции за връщане на най-доброто време
        fun best0to100() = times0to100.minOrNull() ?: lastBest0to100
        fun best0to200() = times0to200.minOrNull() ?: lastBest0to200
        fun best100to200() = times100to200.minOrNull() ?: lastBest100to200
    }

    data class SpeedPoint(
        val speed: Float,
        val timestamp: Long
    )

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
            val calibrated = (offsetAngle - filteredAngle).coerceIn(-70f, 70f)
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun setupLocationUpdates() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500)
            .setMinUpdateIntervalMillis(250)
            .setWaitForAccurateLocation(false)
            .setMinUpdateDistanceMeters(0.5f)
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

        currentSpeed = newSpeed

        // ПОПРАВКА: Правилен ред на параметрите
        trackAcceleration(oldSpeed, newSpeed)

        if (currentSpeed > maxSpeed) maxSpeed = currentSpeed

        val pt = RoutePoint(
            geoPoint = GeoPoint(loc.latitude, loc.longitude),
            speed = currentSpeed,
            angle = currentCalibratedAngle,
            timestamp = SystemClock.elapsedRealtime() - startTime,
            absoluteTime = loc.time
        )
        routePoints.add(pt)
    }

    // Подобрена имплементация на проследяването на ускорението
    // Заменете trackAcceleration функцията във ForegroundService с тази:

    // Заменете trackAcceleration функцията с тази МНОГО по-проста версия:

    private fun trackAcceleration(oldSpeed: Float, newSpeed: Float) {
        val currentTime = SystemClock.elapsedRealtime()

        accelerationTracking.speedHistory.add(SpeedPoint(newSpeed, currentTime))

        // Поддържаме история от последните 10 секунди
        val cutoff = currentTime - 10000
        accelerationTracking.speedHistory.removeAll { it.timestamp < cutoff }

        // Проверка за ускорение/забавяне
        val isAccelerating = newSpeed > oldSpeed + 1.5f
        val isDecelerating = newSpeed < oldSpeed - 1.0f

        // =================== ДОБАВЕН ПРЕКЪСВАТЕЛ ПРИ ЗАБАВЯНЕ ===================
        // Спираме САМО активните измервания при забавяне
        if (isDecelerating) {
            if (accelerationTracking.isTracking0to100) {
                Log.d("AccelTrack", "Canceled 0-100: deceleration detected")
                accelerationTracking.isTracking0to100 = false
                accelerationTracking.hasReached100 = false
            }

            if (accelerationTracking.isTracking0to200) {
                Log.d("AccelTrack", "Canceled 0-200: deceleration detected")
                accelerationTracking.isTracking0to200 = false
                accelerationTracking.hasReached200 = false
            }

            if (accelerationTracking.isTracking100to200) {
                Log.d("AccelTrack", "Canceled 100-200: deceleration detected")
                accelerationTracking.isTracking100to200 = false
            }
        }

        // =================== 0-100 км/ч ИЗМЕРВАНЕ ===================
        if (!accelerationTracking.isTracking0to100 &&
            newSpeed <= 4f &&
            isAccelerating) {

            accelerationTracking.isTracking0to100 = true
            accelerationTracking.startTime0to100 = currentTime
            accelerationTracking.hasReached100 = false
            Log.d("AccelTrack", "Started 0-100 tracking at ${newSpeed}km/h")
        }

        if (accelerationTracking.isTracking0to100) {
            // Достигнали сме 100 км/ч
            if (newSpeed >= 40f && !accelerationTracking.hasReached100) {
                val time = currentTime - accelerationTracking.startTime0to100
                if (time > 2000) {
                    accelerationTracking.times0to100.add(time)
                    Log.d("AccelTrack", "0-100: ${time/1000f}s")
                    accelerationTracking.hasReached100 = true
                    accelerationTracking.isTracking0to100 = false
                }
            }
        }

        // =================== 0-200 км/ч ИЗМЕРВАНЕ ===================
        if (!accelerationTracking.isTracking0to200 &&
            newSpeed <= 4f &&
            isAccelerating) {

            accelerationTracking.isTracking0to200 = true
            accelerationTracking.startTime0to200 = currentTime
            accelerationTracking.hasReached200 = false
            Log.d("AccelTrack", "Started 0-200 tracking at ${newSpeed}km/h")
        }

        if (accelerationTracking.isTracking0to200) {
            // Достигнали сме 200 км/ч
            if (newSpeed >= 60f && !accelerationTracking.hasReached200) {
                val time = currentTime - accelerationTracking.startTime0to200
                if (time > 5000) {
                    accelerationTracking.times0to200.add(time)
                    Log.d("AccelTrack", "0-200: ${time/1000f}s")
                    accelerationTracking.hasReached200 = true
                    accelerationTracking.isTracking0to200 = false
                }
            }
        }

        // =================== 100-200 км/ч ИЗМЕРВАНЕ ===================
        if (!accelerationTracking.isTracking100to200 &&
            newSpeed >= 38f && newSpeed <= 42f &&
            isAccelerating) {

            accelerationTracking.isTracking100to200 = true
            accelerationTracking.startTime100to200 = currentTime
            Log.d("AccelTrack", "Started 100-200 tracking at ${newSpeed}km/h")
        }

        if (accelerationTracking.isTracking100to200) {
            // Достигнали сме 200 км/ч
            if (newSpeed >= 60f) {
                val time = currentTime - accelerationTracking.startTime100to200
                accelerationTracking.times100to200.add(time)
                Log.d("AccelTrack", "100-200: ${time/1000f}s")
                accelerationTracking.isTracking100to200 = false
            }
            // Спираме при забавяне или значително спадане на скоростта
            else if (newSpeed < 95f) {
                Log.d("AccelTrack", "Reset 100-200: speed dropped below 95km/h")
                accelerationTracking.isTracking100to200 = false
            }
        }

        // =================== TIMEOUT ПРОВЕРКИ ===================
        if (accelerationTracking.isTracking0to100 &&
            currentTime - accelerationTracking.startTime0to100 > 20000L) {

            Log.d("AccelTrack", "Stopped 0-100 tracking - timeout")
            accelerationTracking.isTracking0to100 = false
            accelerationTracking.hasReached100 = false
        }

        if (accelerationTracking.isTracking0to200 &&
            currentTime - accelerationTracking.startTime0to200 > 60000L) {

            Log.d("AccelTrack", "Stopped 0-200 tracking - timeout")
            accelerationTracking.isTracking0to200 = false
            accelerationTracking.hasReached200 = false
        }

        if (accelerationTracking.isTracking100to200 &&
            currentTime - accelerationTracking.startTime100to200 > 30000L) {

            Log.d("AccelTrack", "Stopped 100-200 tracking - timeout")
            accelerationTracking.isTracking100to200 = false
        }
    }

    private fun resetAccelerationTrackingFlags() {
        accelerationTracking.isTracking0to100 = false
        accelerationTracking.isTracking0to200 = false
        accelerationTracking.isTracking100to200 = false
    }

// ПРЕМАХНЕТЕ ИЗЦЯЛО тези две функции - вече не се използват:
// - isAcceleratingIntensively
// - isStartingToAccelerate
// - checkConsistentAcceleration
// - isSpeedStable

    // Нова функция за проверка на начало на ускорение:


    // Подобрена функция за проверка на интензивно ускорение
    // Заменете isAcceleratingIntensively функцията с тази по-проста и надеждна версия:



// Премахнете checkConsistentAcceleration и isSpeedStable функциите - вече не се използват

    // Подобрена проверка за консистентно ускорение


    // Подобрена проверка за стабилна скорост

}