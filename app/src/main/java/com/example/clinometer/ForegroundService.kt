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
import java.lang.Math

enum class AccelerationState {
    IDLE,
    ACCELERATING,
    COMPLETED
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
    private val geomagnetic = FloatArray(3)
    var sessionStartTime: Long = 0
    var accelerationTracking = AccelerationData()

    private var lastDataSaveTime = 0L
    private val DATA_SAVE_INTERVAL = 250L

    private val gravity = FloatArray(3)
    private val linearAcceleration = FloatArray(3)
    private val alpha = 0.8f
    @Volatile
    private var isRealAcceleration = false
    @Volatile
    private var currentG = 0f

    @Volatile
    private var peakG = 0f

    @Volatile
    private var currentGForceX = 0f

    @Volatile
    private var currentGForceY = 0f
    @Volatile
    private var isMeasurementActive = false

    fun getCurrentG(): Float = currentG
    fun getPeakG(): Float = peakG
    fun getCurrentGForceX(): Float = currentGForceX
    fun getCurrentGForceY(): Float = currentGForceY

    private val SAMPLES_CAPACITY = 1000

    private val gSamplesBuffer: ArrayDeque<Float> = ArrayDeque(SAMPLES_CAPACITY)
    private val gpsAccelBuffer: ArrayDeque<Float> = ArrayDeque(SAMPLES_CAPACITY)
    private val gTimeStamps = ArrayDeque<Long>(SAMPLES_CAPACITY)
    private val gpsAccelTimeStamps = ArrayDeque<Long>(SAMPLES_CAPACITY)

    private var gMeasurementStartTime: Long = 0L
    private var measurementStartTimeNano: Long = 0L // Добавяме nano време за консистентност
    private val G_SAMPLE_INTERVAL_MS = 100L
    private var lastGSampleTime = 0L

    private var measurementStartTimeMs: Long = 0L
    private var lastGPSAccelSampleTime = 0L
    private val GPS_ACCEL_SAMPLE_INTERVAL = 100L

    private var lastAccelerationTime = 0L
    private val accelerometerThreshold = 0.5f
    private val accelerationWindow = 500_000_000L

    private val speedSamplesBuffer: ArrayDeque<Float> = ArrayDeque(SAMPLES_CAPACITY)
    private val speedTimeStamps = ArrayDeque<Long>(SAMPLES_CAPACITY)
    private var lastSpeedSampleTime = 0L
    private val SPEED_SAMPLE_INTERVAL_MS = 100L

    // Measured times relative to measurementStartTimeNano (nanoseconds)
    @Volatile private var time0to100Nanos: Long = 0L
    @Volatile private var time0to200Nanos: Long = 0L

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
        val timestamp: Long
    )

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
            isTracking100to200 = false

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
    fun getCurrentAngle(): Float = currentCalibratedAngle
    fun getCurrentRawAngle(): Float = filteredAngle
    fun getCurrentSpeed(): Float = currentSpeed
    fun getLastLocation(): Location? = lastLocation
    fun getAccelerationData(): AccelerationData = accelerationTracking
    fun isRealAccelerationDetected(): Boolean = isRealAcceleration
    fun getRecentGSamples(): List<Float> {
        synchronized(gSamplesBuffer) { return gSamplesBuffer.toList() }
    }
    fun getRecentGTimeStamps(): List<Long> {
        synchronized(gTimeStamps) { return gTimeStamps.toList() }
    }

    fun getRecentGpsAccelTimeStamps(): List<Long> {
        synchronized(gpsAccelTimeStamps) { return gpsAccelTimeStamps.toList() }
    }


    fun getRecentGpsAccelSamples(): List<Float> {
        synchronized(gpsAccelBuffer) { return gpsAccelBuffer.toList() }
    }

    fun getRecentSpeedSamples(): List<Float> {
        synchronized(speedSamplesBuffer) { return speedSamplesBuffer.toList() }
    }

    fun getRecentSpeedTimeStamps(): List<Long> {
        synchronized(speedTimeStamps) { return speedTimeStamps.toList() }
    }
    
    fun getMeasurementStartTimeNano(): Long = measurementStartTimeNano
    fun setMeasurementStartTimeNano(timeNanos: Long) {
        measurementStartTimeNano = timeNanos
    }
    fun getTime0to100Nanos(): Long = time0to100Nanos
    fun getTime0to200Nanos(): Long = time0to200Nanos
    
    fun addSpeedSample(speed: Float, relativeTimeNanos: Long) {
        synchronized(speedSamplesBuffer) {
            if (speedSamplesBuffer.size >= SAMPLES_CAPACITY) {
                speedSamplesBuffer.removeFirst()
                speedTimeStamps.removeFirst()
            }
            speedSamplesBuffer.addLast(speed)
            speedTimeStamps.addLast(relativeTimeNanos)
        }
    }

    fun calibrateZero() {
        offsetAngle = filteredAngle
        maxLeftAngle = 0f
        maxRightAngle = 0f
        currentCalibratedAngle = 0f
    }
    
    fun calibrateZeroWithAngle(angle: Float) {
        offsetAngle = angle
        maxLeftAngle = 0f
        maxRightAngle = 0f
        currentCalibratedAngle = 0f
    }

    fun startNewMeasurement() {

        gMeasurementStartTime = System.currentTimeMillis()
        measurementStartTimeNano = System.nanoTime() // Запазваме и nano времето
        measurementStartTimeMs = gMeasurementStartTime
        lastGSampleTime = 0L
        lastGPSAccelSampleTime = 0L
        lastSpeedSampleTime = 0L
        isMeasurementActive = true

        // Reset measured times
        time0to100Nanos = 0L
        time0to200Nanos = 0L

        currentG = 0f
        peakG = 0f

        synchronized(gSamplesBuffer) {
            gSamplesBuffer.clear()
            gTimeStamps.clear()
        }
        synchronized(gpsAccelBuffer) {
            gpsAccelBuffer.clear()
            gpsAccelTimeStamps.clear()
        }
        synchronized(speedSamplesBuffer) {
            speedSamplesBuffer.clear()
            speedTimeStamps.clear()
        }

    }
    fun stopMeasurement() {
        isMeasurementActive = false
    }

    fun resetData() {
        if (!isPreWarmingMode) {
            // Изчисти всички данни за нова сесия
            routePoints.clear()
            filteredAngle = 0f
            offsetAngle = 0f
            currentCalibratedAngle = 0f
            maxLeftAngle = 0f
            maxRightAngle = 0f
            maxSpeed = 0f
            currentSpeed = 0f

            // Нулирай всички времена и задай ново начално време
            startTime = SystemClock.elapsedRealtime()
            actualStartTime = SystemClock.elapsedRealtime()
            lastDataSaveTime = 0L
            resetTime = SystemClock.elapsedRealtime()
            lastAccelerationTime = 0L

            // Нулирай acceleration данните
            accelerationTracking = AccelerationData()
            totalDistance = 0.0
            lastLocationForDistance = null

            // Нулирай G measurement данните
            gMeasurementStartTime = 0L
            lastGSampleTime = 0L
            peakG = 0f

            // Изчисти всички буфери
            synchronized(gSamplesBuffer) {
                gSamplesBuffer.clear()
                gTimeStamps.clear()
            }
            synchronized(gpsAccelBuffer) {
                gpsAccelBuffer.clear()
                gpsAccelTimeStamps.clear()
            }
            synchronized(speedSamplesBuffer) {
                speedSamplesBuffer.clear()
                speedTimeStamps.clear()
            }
        } else {
            // Ако сме в pre-warming режим, пак искаме да нулираме сесиите, но да НЕ задаваме actualStartTime
            routePoints.clear()
            filteredAngle = 0f
            offsetAngle = 0f
            currentCalibratedAngle = 0f
            maxLeftAngle = 0f
            maxRightAngle = 0f
            maxSpeed = 0f
            currentSpeed = 0f

            startTime = SystemClock.elapsedRealtime()
            actualStartTime = 0L
            lastDataSaveTime = 0L
            resetTime = SystemClock.elapsedRealtime()
            lastAccelerationTime = 0L

            // Нулирай acceleration данните
            accelerationTracking = AccelerationData()
            totalDistance = 0.0
            lastLocationForDistance = null

            // Нулирай G measurement данните
            gMeasurementStartTime = 0L
            lastGSampleTime = 0L
            peakG = 0f

            // Изчисти всички буфери
            synchronized(gSamplesBuffer) {
                gSamplesBuffer.clear()
                gTimeStamps.clear()
            }
            synchronized(gpsAccelBuffer) {
                gpsAccelBuffer.clear()
                gpsAccelTimeStamps.clear()
            }
            synchronized(speedSamplesBuffer) {
                speedSamplesBuffer.clear()
                speedTimeStamps.clear()
            }
        }
    }



    fun getStartTime(): Long {
        if (isPreWarmingMode) {
            // Ако все още сме в pre-warming — казваме на клиента да използва текущото време (хронометър ще започне от 0)
            return SystemClock.elapsedRealtime()
        }
        if (actualStartTime == 0L) {
            actualStartTime = SystemClock.elapsedRealtime()
        }
        return actualStartTime
    }



    override fun onCreate() {
        super.onCreate()
        resetData()

        serviceStartTime = SystemClock.elapsedRealtime()
        sessionStartTime = System.currentTimeMillis()

        actualStartTime = 0L

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

        startTime = SystemClock.elapsedRealtime()

        setupLocationUpdates()

        if (!isPreWarmingMode) {
            registerSensors()
        }
    }

    fun getServiceDuration(): Long {
        return if (isPreWarmingMode) {
            0L
        } else {
            SystemClock.elapsedRealtime() - actualStartTime
        }
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
        if (isPreWarmingMode) return


        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
                gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
                gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]

                linearAcceleration[0] = event.values[0] - gravity[0]
                linearAcceleration[1] = event.values[1] - gravity[1]
                linearAcceleration[2] = event.values[2] - gravity[2]

                val magnitude = sqrt(
                    linearAcceleration[0] * linearAcceleration[0] +
                            linearAcceleration[1] * linearAcceleration[1] +
                            linearAcceleration[2] * linearAcceleration[2]
                )

                val gForce = magnitude / 9.81f
                currentG = gForce
                if (gForce > peakG) peakG = gForce

                // Calculate G-force components for visualization
                currentGForceX = linearAcceleration[0] / 9.81f
                currentGForceY = linearAcceleration[1] / 9.81f

                // Проверка за реално ускорение
                if (magnitude > accelerometerThreshold && gForce > 0.05f) {
                    isRealAcceleration = true
                    lastAccelerationTime = System.nanoTime()

                    // Автоматично стартирай измерване ако няма активно
                    if (!isMeasurementActive && gMeasurementStartTime == 0L) {
                        startNewMeasurement()
                    }
                }

                if (System.nanoTime() - lastAccelerationTime > accelerationWindow) {
                    isRealAcceleration = false
                }

                // Запазвай G samples само когато има активно измерване
                val currentTime = System.currentTimeMillis()

                // Запазвай данни само ако има активно ускорение или вече сме започнали измерване
                // Обработка на ъглите - винаги работи в реално време
                val x = event.values[0]  // Наклон наляво/надясно
                val y = event.values[1]  // Наклон напред/назад  
                val z = event.values[2]  // Гравитация нагоре/надолу
                
                // Проверяваме ориентацията
                val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                
                // Изчисляваме общата гравитация
                val totalGravity = sqrt(x * x + y * y + z * z)
                val raw = if (totalGravity > 0) {
                    if (isLandscape) {
                        // В ландскейп: Y ос за нагоре/надолу, нагоре = -, надолу = +
                        -Math.toDegrees(Math.asin((y / totalGravity).toDouble().coerceIn(-1.0, 1.0))).toFloat()
                    } else {
                        // В портрет: X ос за наляво/надясно
                        Math.toDegrees(Math.asin((x / totalGravity).toDouble().coerceIn(-1.0, 1.0))).toFloat()
                    }
                } else 0f

                val delta = abs(raw - filteredAngle)
                val adaptiveAlpha = (0.01f + (delta / 45f)).coerceIn(0.05f, 0.3f)
                filteredAngle += adaptiveAlpha * (raw - filteredAngle)
                val calibrated = (offsetAngle - filteredAngle).coerceIn(-90f, 90f)
                currentCalibratedAngle = calibrated

                if (calibrated < maxLeftAngle) maxLeftAngle = calibrated
                if (calibrated > maxRightAngle) maxRightAngle = calibrated

                // Записваме G семпли винаги, когато има активно измерване
                if (isMeasurementActive && gMeasurementStartTime > 0L) {
                    // Записваме всички пикове над 1.0g незабавно, независимо от интервала
                    val shouldRecordPeak = gForce > 1.0f && (gSamplesBuffer.isEmpty() || gForce > (gSamplesBuffer.lastOrNull() ?: 0f))
                    val shouldRecordRegular = currentTime - lastGSampleTime >= G_SAMPLE_INTERVAL_MS
                    
                    if (shouldRecordPeak || shouldRecordRegular) {
                        synchronized(gSamplesBuffer) {
                            if (gSamplesBuffer.size >= SAMPLES_CAPACITY) {
                                gSamplesBuffer.removeFirst()
                                gTimeStamps.removeFirst()
                            }
                            gSamplesBuffer.addLast(gForce)
                            // Използваме nano време за по-висока точност - записваме в наносекунди за консистентност
                            val relativeTimeNano = System.nanoTime() - measurementStartTimeNano
                            gTimeStamps.addLast(relativeTimeNano)
                            lastGSampleTime = currentTime

                            if (gSamplesBuffer.size % 10 == 0) {
                            }
                        }
                    }

                    saveDataPointIfNeeded()
                }
                // Записваме пикове дори извън активния период, за да не пропуснем пикове
                else if (gForce > 0.05f) {
                    // За пикове извън активния период - записваме само ако е значителен пик
                    val shouldRecordPeak = gForce > 1.0f && (gSamplesBuffer.isEmpty() || gForce > (gSamplesBuffer.lastOrNull() ?: 0f))
                    val shouldRecordRegular = currentTime - lastGSampleTime >= G_SAMPLE_INTERVAL_MS
                    
                    if (shouldRecordPeak || shouldRecordRegular) {
                        // Записваме семпъла с timestamp, използвайки текущото време
                        synchronized(gSamplesBuffer) {
                            if (gSamplesBuffer.size >= SAMPLES_CAPACITY) {
                                gSamplesBuffer.removeFirst()
                                gTimeStamps.removeFirst()
                            }
                            gSamplesBuffer.addLast(gForce)
                            // Използваме текущото време като база, ако няма активно измерване
                            val currentTimeNano = System.nanoTime()
                            val relativeTimeNano = if (measurementStartTimeNano > 0L) {
                                currentTimeNano - measurementStartTimeNano
                            } else {
                                currentTimeNano - (gMeasurementStartTime * 1_000_000L) // Convert ms to ns
                            }
                            gTimeStamps.addLast(relativeTimeNano)
                            lastGSampleTime = currentTime
                        }
                    }
                }
            }

        }
    }

    private fun saveDataPointIfNeeded() {
        if (isPreWarmingMode) return

        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastDataSaveTime >= DATA_SAVE_INTERVAL) {
            lastDataSaveTime = currentTime

            lastLocation?.let { location ->
                val pt = RoutePoint(
                    geoPoint = GeoPoint(location.latitude, location.longitude),
                    speed = currentSpeed,
                    angle = currentCalibratedAngle,
                    timestamp = currentTime - actualStartTime,
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
        intent?.let {
            val preWarm = it.getBooleanExtra("PRE_WARMING_MODE", false)
            val activate = it.getBooleanExtra("ACTIVATE_NORMAL_MODE", false)

            if (preWarm) {
                // Влизаме в pre-warming: събираме само GPS буфер (не задаваме actualStartTime)
                isPreWarmingMode = true
            }

            if (activate) {
                // Преминаваме в нормален режим: задаваме actualStartTime тук и регистрираме сензорите
                if (isPreWarmingMode) {
                    isPreWarmingMode = false
                }
                actualStartTime = SystemClock.elapsedRealtime()
                resetTime = actualStartTime
                startTime = actualStartTime

                // Ако имаме предварително записани GPS стойности от pre-warm — използваме последната
                if (gpsWarmupLocations.isNotEmpty()) {
                    lastLocation = gpsWarmupLocations.last()
                }

                registerSensors()
            }
        }

        return START_STICKY
    }


    private fun setupLocationUpdates() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100)
            .setMinUpdateIntervalMillis(100)
            .setWaitForAccurateLocation(false)
            .setMinUpdateDistanceMeters(0.1f)
            .setMaxUpdateDelayMillis(100)
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
        // Използваме само ACCELEROMETER - работи на всички устройства
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
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


    private fun lowPass(input: FloatArray, output: FloatArray?): FloatArray {
        if (output == null) return input

        val alpha = 0.8f
        for (i in input.indices) {
            output[i] = output[i] + alpha * (input[i] - output[i])
        }
        return output
    }


    private fun onNewLocation(loc: Location) {
        // Изчисли GPS acceleration
        val gpsAccel = run {
            val prev = lastLocation
            if (prev != null) {
                val dtSec = (loc.time - prev.time).coerceAtLeast(1L) / 1000.0
                if (dtSec > 0 && dtSec < 5.0) { // Игнорирай много големи времеви разлики
                    val prevMs = prev.speed
                    val newMs = loc.speed
                    val accel = ((newMs - prevMs) / dtSec).toFloat()
                    if (accel.isFinite() && !accel.isNaN()) accel else 0f
                } else 0f
            } else 0f
        }

        lastLocation = loc
        val newSpeed = loc.speed * 3.6f
        val oldSpeed = currentSpeed

        if (isPreWarmingMode) {
            gpsWarmupLocations.add(loc)
            if (gpsWarmupLocations.size > 10) {
                gpsWarmupLocations.removeAt(0)
            }
            currentSpeed = newSpeed
            return
        }

        // Запазвай GPS acceleration samples само при активно измерване
        val currentTime = System.currentTimeMillis()

        if (isMeasurementActive && gMeasurementStartTime > 0L) {
            // ДОБАВИ GPS ACCELERATION СЕМПЛИТЕ
            if (currentTime - lastGPSAccelSampleTime >= GPS_ACCEL_SAMPLE_INTERVAL) {
                synchronized(gpsAccelBuffer) {
                    if (gpsAccelBuffer.size >= SAMPLES_CAPACITY) {
                        gpsAccelBuffer.removeFirst()
                        gpsAccelTimeStamps.removeFirst()
                    }
                    gpsAccelBuffer.addLast(gpsAccel)
                    // Използваме nano време за по-висока точност - записваме в наносекунди за консистентност
                    val relativeTimeNano = System.nanoTime() - measurementStartTimeNano
                    gpsAccelTimeStamps.addLast(relativeTimeNano)
                    lastGPSAccelSampleTime = currentTime

                }
            }
            // Точно преди if (currentTime - lastSpeedSampleTime >= SPEED_SAMPLE_INTERVAL_MS)

            // ДОБАВИ SPEED СЕМПЛИТЕ - при всеки GPS update за синхронизация
            synchronized(speedSamplesBuffer) {
                if (speedSamplesBuffer.size >= SAMPLES_CAPACITY) {
                    speedSamplesBuffer.removeFirst()
                    speedTimeStamps.removeFirst()
                }
                speedSamplesBuffer.addLast(newSpeed)
                // Използваме nano време за по-висока точност - записваме в наносекунди за консистентност
                val relativeTimeNano = System.nanoTime() - measurementStartTimeNano
                speedTimeStamps.addLast(relativeTimeNano)

                // Засичане на crossing моменти с ИНТЕРПОЛАЦИЯ
                if (time0to100Nanos == 0L && speedSamplesBuffer.size >= 2) {
                    val prevSpeed = speedSamplesBuffer.elementAt(speedSamplesBuffer.size - 2)
                    val prevTime = speedTimeStamps.elementAt(speedTimeStamps.size - 2)
                    val currSpeed = newSpeed
                    val currTime = relativeTimeNano
                    
                    if (prevSpeed < 100f && currSpeed >= 100f) {
                        // ЛИНЕЙНА ИНТЕРПОЛАЦИЯ за точното време
                        val ratio = (100f - prevSpeed) / (currSpeed - prevSpeed)
                        time0to100Nanos = prevTime + ((currTime - prevTime) * ratio).toLong()
                    }
                }
                
                if (time0to200Nanos == 0L && speedSamplesBuffer.size >= 2) {
                    val prevSpeed = speedSamplesBuffer.elementAt(speedSamplesBuffer.size - 2)
                    val prevTime = speedTimeStamps.elementAt(speedTimeStamps.size - 2)
                    val currSpeed = newSpeed
                    val currTime = relativeTimeNano
                    
                    if (prevSpeed < 200f && currSpeed >= 200f) {
                        // ЛИНЕЙНА ИНТЕРПОЛАЦИЯ за точното време
                        val ratio = (200f - prevSpeed) / (currSpeed - prevSpeed)
                        time0to200Nanos = prevTime + ((currTime - prevTime) * ratio).toLong()
                    }
                }
            }
        } else {
        }

        trackAcceleration(oldSpeed, newSpeed)
        currentSpeed = newSpeed

        if (currentSpeed > maxSpeed) maxSpeed = currentSpeed
        updateTotalDistance(loc)

        if (SystemClock.elapsedRealtime() % 30_000 == 0L) {
            optimizeRoutePoints()
        }

        val pt = RoutePoint(
            geoPoint = GeoPoint(loc.latitude, loc.longitude),
            speed = currentSpeed,
            angle = currentCalibratedAngle,
            timestamp = SystemClock.elapsedRealtime() - actualStartTime,
            absoluteTime = loc.time
        )
        routePoints.add(pt)
    }


    private fun updateTotalDistance(newLocation: Location) {
        if (lastLocationForDistance == null) {
            if (newLocation.accuracy <= 25f) {
                lastLocationForDistance = newLocation
            }
            return
        }

        lastLocationForDistance?.let { lastLoc ->
            if (newLocation.accuracy > 25f || lastLoc.accuracy > 25f) return@let

            val distanceInMeters = lastLoc.distanceTo(newLocation)
            val timeDiff = (newLocation.time - lastLoc.time) / 1000.0

            if (isValidDistanceMeasurement(distanceInMeters, timeDiff, lastLoc, newLocation)) {
                totalDistance += distanceInMeters
                lastLocationForDistance = newLocation
            }
        }
    }

    private fun isValidDistanceMeasurement(
        distance: Float,
        timeDiff: Double,
        lastLocation: Location,
        newLocation: Location
    ): Boolean {
        if (distance < 0.3f || distance > 150f) return false
        if (timeDiff < 0.2 || timeDiff > 5.0) return false

        val calculatedSpeed = distance / timeDiff

        val bearingDiff = if (lastLocation.hasBearing() && newLocation.hasBearing()) {
            kotlin.math.abs(lastLocation.bearing - newLocation.bearing)
        } else null

        return when {
            calculatedSpeed < 2 -> true
            bearingDiff != null && bearingDiff > 30 -> calculatedSpeed < 35f
            else -> calculatedSpeed < 35f
        }
    }

    fun getTotalDistanceKm(): Double {
        return totalDistance / 1000.0
    }

    private fun trackAcceleration(oldSpeed: Float, newSpeed: Float) {
        val currentTime = System.nanoTime()

        // Запазваме историята
        accelerationTracking.speedHistory.add(SpeedPoint(newSpeed, currentTime))
        val cutoff = currentTime - 10_000_000_000L
        accelerationTracking.speedHistory.removeAll { it.timestamp < cutoff }

        // Проста детекция на ускорение/деселерация
        val speedDiff = newSpeed - oldSpeed
        val isAccelerating = speedDiff > 0.5f  // По-чувствителен праг
        val isDecelerating = speedDiff < -1.0f

        // Детектираме пълна спирка
        if (newSpeed < 2f) {  // По-нисък праг за спиране
            accelerationTracking.hasFullyStopped = true
            accelerationTracking.state = AccelerationState.IDLE
            resetAllActiveRanges("Full stop detected")
            return
        }

        when (accelerationTracking.state) {
            AccelerationState.IDLE -> {
                // Започваме веднага щом има ускорение от ниска скорост
                if (newSpeed < 5f && isAccelerating) {
                    accelerationTracking.state = AccelerationState.ACCELERATING
                    startMeasurements(currentTime, newSpeed)
                    accelerationTracking.hasFullyStopped = false
                }
            }

            AccelerationState.ACCELERATING -> {
                // Спираме ако има рязка деселерация
                if (isDecelerating) {
                    cancelActiveRanges("Deceleration")
                    accelerationTracking.state = AccelerationState.IDLE
                } else {
                    // Продължаваме измерването
                    updateMeasurements(newSpeed, currentTime)
                }
            }

            AccelerationState.COMPLETED -> {
                // Чакаме пълна спирка
                if (newSpeed < 2f) {
                    accelerationTracking.state = AccelerationState.IDLE
                }
            }
        }

        accelerationTracking.syncFromRanges()
    }

    private fun startMeasurements(currentTime: Long, currentSpeed: Float) {
        accelerationTracking.ranges.forEach { range ->
            if (range.requiresFullStop) {
                range.isActive = true
                range.startTime = currentTime
            }
        }
    }

    private fun updateMeasurements(newSpeed: Float, currentTime: Long) {
        val completedMeasurements = mutableListOf<AccelerationRange>()

        accelerationTracking.ranges.forEach { range ->
            when {
                range.isActive && newSpeed >= range.endSpeed -> {
                    val duration = currentTime - range.startTime
                    range.results.add(duration)
                    range.isActive = false
                    completedMeasurements.add(range)

                    when (range.name) {
                        "0-100" -> accelerationTracking.hasReached100 = true
                        "0-200" -> accelerationTracking.hasReached200 = true
                    }
                }
            }
        }

        if (accelerationTracking.ranges.none { it.isActive }) {
            val hasCompletedAny = accelerationTracking.ranges.any { it.results.isNotEmpty() }
            if (hasCompletedAny) {
                accelerationTracking.state = AccelerationState.COMPLETED
            }
        }
    }

    private fun resetAllActiveRanges(reason: String) {
        val activeRanges = accelerationTracking.ranges.filter { it.isActive }
        if (activeRanges.isNotEmpty()) {
            activeRanges.forEach { it.isActive = false }
        }
    }

    private fun cancelActiveRanges(reason: String) {
        accelerationTracking.ranges.filter { it.isActive }.forEach { range ->
            range.isActive = false
        }
    }

    private fun checkTimeouts(currentTime: Long) {
        accelerationTracking.ranges.filter { it.isActive }.forEach { range ->
            if (currentTime - range.startTime > range.timeout) {
                range.isActive = false
            }
        }
    }
}