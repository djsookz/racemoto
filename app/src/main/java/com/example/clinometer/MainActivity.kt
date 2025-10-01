package com.example.clinometer

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import com.example.clinometer.settings.LanguageManager
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.*
import android.util.Log
import android.view.View
import android.content.pm.ActivityInfo
import android.widget.ImageButton
import android.view.Surface
import android.view.WindowManager
import android.widget.Button
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import kotlin.math.*

class KalmanLocationFilter(private val qMetersPerSecond: Float = 3f) {
    private var timestamp = 0L
    private var lat = 0.0
    private var lng = 0.0
    private var variance = -1.0

    fun process(location: Location): Location {
        val accuracy = location.accuracy.toDouble()

        if (variance < 0) {
            timestamp = location.time
            lat = location.latitude
            lng = location.longitude
            variance = accuracy * accuracy
        } else {
            val dt = (location.time - timestamp) / 1000.0
            if (dt > 0) {
                variance += dt * qMetersPerSecond * qMetersPerSecond
                timestamp = location.time
                val k = variance / (variance + accuracy * accuracy)
                lat += k * (location.latitude - lat)
                lng += k * (location.longitude - lng)
                variance = (1 - k) * variance
            }
        }

        return Location(location).apply {
            latitude = lat
            longitude = lng
            time = timestamp
            this.accuracy = sqrt(variance).toFloat()
        }
    }
}

class MotionPredictor {
    private data class MotionState(
        val position: GeoPoint,
        val velocity: DoubleArray,
        val timestamp: Long,
        val bearing: Float,
        val speed: Float
    )

    private val history = mutableListOf<MotionState>()
    private val maxHistory = 5

    fun addSample(position: GeoPoint, bearing: Float, speed: Float) {
        val now = SystemClock.elapsedRealtime()

        val velocity = if (history.isNotEmpty()) {
            val last = history.last()
            val dt = (now - last.timestamp) / 1000.0
            if (dt > 0) {
                doubleArrayOf(
                    (position.latitude - last.position.latitude) / dt,
                    (position.longitude - last.position.longitude) / dt
                )
            } else {
                doubleArrayOf(0.0, 0.0)
            }
        } else {
            doubleArrayOf(0.0, 0.0)
        }

        history.add(MotionState(position, velocity, now, bearing, speed))
        if (history.size > maxHistory) {
            history.removeAt(0)
        }
    }

}

class UltraSmoothLocationOverlay(
    private val mapView: MapView,
    private val locationIcon: Bitmap
) : Overlay() {

    private var currentPos = GeoPoint(0.0, 0.0)
    private var targetPos = GeoPoint(0.0, 0.0)
    private var currentBearing = 0f
    private var targetBearing = 0f
    private var lastUpdateTime = SystemClock.elapsedRealtime()
    private var isInitialized = false
    
    private var velocityLat = 0.0
    private var velocityLon = 0.0
    private var lastTargetPos = GeoPoint(0.0, 0.0)
    private var lastTargetTime = SystemClock.elapsedRealtime()
    
    private var smoothedVelocityLat = 0.0
    private var smoothedVelocityLon = 0.0
    private val velocitySmoothingFactor = 0.7

    private val paint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        isDither = true
    }

    private val interpolator = android.view.animation.AccelerateDecelerateInterpolator()

    fun updateTarget(position: GeoPoint, bearing: Float, immediate: Boolean = false) {
        val currentTime = SystemClock.elapsedRealtime()
        if (isInitialized && !immediate) {
            val timeDiff = (currentTime - lastTargetTime) / 1000.0
            if (timeDiff > 0.01) {
                val newVelocityLat = (position.latitude - lastTargetPos.latitude) / timeDiff
                val newVelocityLon = (position.longitude - lastTargetPos.longitude) / timeDiff
                
                velocityLat = newVelocityLat
                velocityLon = newVelocityLon
                smoothedVelocityLat = smoothedVelocityLat * velocitySmoothingFactor + velocityLat * (1 - velocitySmoothingFactor)
                smoothedVelocityLon = smoothedVelocityLon * velocitySmoothingFactor + velocityLon * (1 - velocitySmoothingFactor)
            }
        }
        
        targetPos = position
        targetBearing = bearing
        lastUpdateTime = currentTime
        lastTargetPos = position
        lastTargetTime = currentTime
        
        if (!isInitialized || immediate) {
            currentPos = position
            currentBearing = bearing
            velocityLat = 0.0
            velocityLon = 0.0
            smoothedVelocityLat = 0.0
            smoothedVelocityLon = 0.0
            isInitialized = true
        }
    }

    fun getCurrentPosition(): GeoPoint = currentPos
    fun getCurrentBearing(): Float = currentBearing

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !isInitialized) return
        
        val now = SystemClock.elapsedRealtime()
        val timeSinceLastUpdate = (now - lastUpdateTime) / 1000.0
        
        val predictedLat = lastTargetPos.latitude + smoothedVelocityLat * timeSinceLastUpdate
        val predictedLon = lastTargetPos.longitude + smoothedVelocityLon * timeSinceLastUpdate
        
        val elapsed = (now - lastUpdateTime).coerceAtMost(100)
        val progress = interpolator.getInterpolation(elapsed / 100f)
        
        currentPos = GeoPoint(
            currentPos.latitude + (predictedLat - currentPos.latitude) * progress * 0.3,
            currentPos.longitude + (predictedLon - currentPos.longitude) * progress * 0.3
        )
        
        var bearingDiff = targetBearing - currentBearing
        while (bearingDiff > 180) bearingDiff -= 360
        while (bearingDiff < -180) bearingDiff += 360
        val bearingSmoothing = when {
            abs(bearingDiff) > 90 -> 0.1f
            abs(bearingDiff) > 45 -> 0.15f
            else -> 0.25f
        }
        
        currentBearing += bearingDiff * bearingSmoothing
        while (currentBearing < 0) currentBearing += 360
        while (currentBearing > 360) currentBearing -= 360
        
        val point = Point()
        mapView.projection.toPixels(currentPos, point)

        canvas.save()
        canvas.rotate(currentBearing, point.x.toFloat(), point.y.toFloat())
        canvas.drawBitmap(
            locationIcon,
            point.x - locationIcon.width / 2f,
            point.y - locationIcon.height / 2f,
            paint
        )
        canvas.restore()
        mapView.postInvalidateDelayed(16)
    }
}

class MainActivity : AppCompatActivity(), SensorEventListener {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private var serviceBound = false
    private var foregroundService: ForegroundService? = null

    private val renderHandler = Handler(Looper.getMainLooper())

    private val kalmanFilter = KalmanLocationFilter()
    private val motionPredictor = MotionPredictor()
    private var lastProcessedLocation: Location? = null
    private var isFirstLocation = true

    private var totalDistance = 0.0
    private var lastDistancePoint: GeoPoint? = null
    private val distancePoints = mutableListOf<GeoPoint>()

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null
    private var sensorBearing = 0f


    private var targetAngle = 0f
    private var currentAngle = 0f
    private var needsZeroAfterRotation = true
    private var currentMapOrientation = 0f
    private var targetMapOrientation = 0f
    
    private var targetZoom = 17.5
    private var currentZoom = 17.5

    private lateinit var currentProfile: Profile
    private lateinit var mapView: MapView
    private lateinit var routeOverlay: Polyline
    private lateinit var smoothLocationOverlay: UltraSmoothLocationOverlay

    private lateinit var speedometerBackground: ImageView
    private lateinit var gaugeView: GaugeView
    private lateinit var currentAngleText: TextView
    private lateinit var speedText: TextView
    private lateinit var speedTextCar: TextView
    private lateinit var chronometerCar: Chronometer
    private lateinit var distanceText: TextView
    private lateinit var distanceTextCar: TextView
    private lateinit var distanceContainer: LinearLayout
    private lateinit var carModeContainer: LinearLayout
    private lateinit var resetButton: Button
    private lateinit var stopButton: Button
    private lateinit var chronometer: Chronometer
    private lateinit var zeroButton: Button
    private var orientationToggle: ImageButton? = null
    private var isOrientationLocked: Boolean = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ForegroundService.LocalBinder
            foregroundService = binder.getService()
            serviceBound = true
            startChronometer()
            startRenderLoop()
            updateAccelerationDisplay(foregroundService?.getAccelerationData() ?: ForegroundService.AccelerationData())

            foregroundService?.getLastLocation()?.let { location ->
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                
                val currentZoom = mapView.zoomLevelDouble
                val metersPerPixel = 156543.03392 * cos(Math.toRadians(location.latitude)) / Math.pow(2.0, currentZoom)
                val offsetMeters = 30 * resources.displayMetrics.density * metersPerPixel
                
                val bearingRad = Math.toRadians(location.bearing.toDouble())
                val offsetLat = (offsetMeters * cos(bearingRad)) / 111320.0
                val offsetLon = (offsetMeters * sin(bearingRad)) / (111320.0 * cos(Math.toRadians(location.latitude)))
                
                val centerLat = geoPoint.latitude + offsetLat
                val centerLon = geoPoint.longitude + offsetLon
                
                mapView.controller.setCenter(GeoPoint(centerLat, centerLon))
                smoothLocationOverlay.updateTarget(geoPoint, location.bearing, immediate = true)
                motionPredictor.addSample(geoPoint, location.bearing, location.speed)

                if (lastDistancePoint == null) {
                    lastDistancePoint = geoPoint
                    distancePoints.add(geoPoint)
                }

                isFirstLocation = false
            }

            val existingPoints = foregroundService?.getRoutePoints() ?: emptyList()
            if (existingPoints.isNotEmpty()) {
                totalDistance = 0.0
                distancePoints.clear()
                for (point in existingPoints) {
                    distancePoints.add(point.geoPoint)
                }
                recalculateTotalDistance()
                updateDistanceDisplay()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            foregroundService = null
            stopRenderLoop()
        }
    }

    private val renderRunnable = object : Runnable {
        override fun run() {
            updateUIFromService()
            updateGaugeAnimation()
            updateMapAnimation()
            renderHandler.postDelayed(this, 16)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        currentProfile = intent.getSerializableExtra("SELECTED_PROFILE") as? Profile
            ?: Profile(name = "My profile", vehicleType = Profile.VehicleType.MOTORCYCLE)

        initializeSensors()
        setupScreenKeepOn()
        setupMap()
        
        initializeViews()
        updateUIForProfile()
        
        setupButtons()
        setupOrientationToggle()
        needsZeroAfterRotation = true

        if (isServiceRunning()) {
            bindService(Intent(this, ForegroundService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        }

    }

    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (rotationSensor == null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        }
    }

    private fun initializeViews() {
        speedometerBackground = findViewById(R.id.speedometerBackground)
        chronometer = findViewById(R.id.chronometer)
        chronometerCar = findViewById(R.id.chronometerCar)
        gaugeView = findViewById(R.id.gaugeView)
        currentAngleText = findViewById(R.id.currentAngleText)
        speedText = findViewById(R.id.speedText)
        speedTextCar = findViewById(R.id.speedTextCar)
        distanceText = findViewById(R.id.distanceText)
        distanceTextCar = findViewById(R.id.distanceTextCar)
        distanceContainer = findViewById(R.id.distanceContainer)
        carModeContainer = findViewById(R.id.carModeContainer)
        resetButton = findViewById(R.id.btnReset)
        zeroButton = findViewById(R.id.btnZero)
        stopButton = findViewById(R.id.btnStop)
        orientationToggle = findViewById(R.id.btnOrientationToggle)

        gaugeView.visibility = View.GONE
        currentAngleText.visibility = View.GONE
        zeroButton.visibility = View.GONE

        currentAngleText.text = getString(R.string.current_angle, 0)
        speedText.text = getString(R.string.current_speed, 0)
        if (::speedTextCar.isInitialized) {
            speedTextCar.text = "0"
        }
        distanceText.text = "0.00 km"
        if (::distanceTextCar.isInitialized) {
            distanceTextCar.text = "0.00 км"
        }
        updateAccelerationDisplay(ForegroundService.AccelerationData())
    }

    private fun setupScreenKeepOn() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        updateScreenKeepOn(prefs.getBoolean("always_on_display", false))

        prefs.registerOnSharedPreferenceChangeListener { shared, key ->
            if (key == "always_on_display") {
                updateScreenKeepOn(shared.getBoolean(key, false))
            }
        }
    }

    private fun updateScreenKeepOn(keepOn: Boolean) {
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun updateUIForProfile() {
        val isMotorcycle = currentProfile.vehicleType == Profile.VehicleType.MOTORCYCLE
        val motorcycleViews = mutableListOf<View>()
        
        if (::speedometerBackground.isInitialized) {
            motorcycleViews.add(speedometerBackground)
        }
        motorcycleViews.add(gaugeView)
        motorcycleViews.add(currentAngleText)
        motorcycleViews.add(zeroButton)

        if (isMotorcycle) {
            // Show motorcycle UI
            motorcycleViews.forEach { view ->
                view.visibility = View.VISIBLE
                view.alpha = 1f
            }
            if (::distanceContainer.isInitialized) {
                distanceContainer.visibility = View.VISIBLE
            }
            if (::carModeContainer.isInitialized) {
                carModeContainer.visibility = View.GONE
            }
        } else {
            // Show car UI
            motorcycleViews.forEach { view ->
                view.visibility = View.GONE
                view.alpha = 0f
            }
            if (::distanceContainer.isInitialized) {
                distanceContainer.visibility = View.GONE
            }
            if (::carModeContainer.isInitialized) {
                carModeContainer.visibility = View.VISIBLE
            }
        }
    }

    private fun setupMap() {
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        mapView = findViewById(R.id.mapView)
        mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(17.5)
            isTilesScaledToDpi = true
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isFlingEnabled = false
        }

        val locationIcon = createHighQualityLocationIcon()
        smoothLocationOverlay = UltraSmoothLocationOverlay(mapView, locationIcon)

        routeOverlay = Polyline().apply {
            outlinePaint.apply {
                strokeWidth = 18f
                color = Color.parseColor("#FF5722")
                alpha = 200
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                pathEffect = CornerPathEffect(20f)
            }
        }

        mapView.overlays.add(routeOverlay)
        mapView.overlays.add(smoothLocationOverlay)
    }

    private fun createHighQualityLocationIcon(): Bitmap {
        val size = (48 * resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_navigation)
        if (drawable != null) {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        } else {
            val paint = Paint().apply {
                isAntiAlias = true
                isDither = true
            }

            val centerX = size / 2f
            val centerY = size / 2f
            paint.apply {
                color = Color.argb(50, 0, 0, 0)
                maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawCircle(centerX, centerY + 2, centerX - 6f, paint)
            paint.maskFilter = null
            paint.apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(centerX, centerY, centerX - 4f, paint)
            paint.apply {
                color = Color.argb(100, 0, 0, 0)
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawCircle(centerX, centerY, centerX - 4f, paint)
            val path = Path().apply {
                moveTo(centerX, centerY - 18f)
                lineTo(centerX - 10f, centerY + 18f)
                lineTo(centerX, centerY + 6f)
                lineTo(centerX + 10f, centerY + 18f)
                close()
            }

            paint.apply {
                color = ContextCompat.getColor(this@MainActivity, R.color.accent_blue)
                style = Paint.Style.FILL
                setShadowLayer(2f, 0f, 1f, Color.argb(100, 0, 0, 0))
            }
            canvas.drawPath(path, paint)
        }

        return bitmap
    }

    private fun setupButtons() {
        resetButton.setOnClickListener {
            if (checkLocationPermission()) {
                startAndBindService()
                resetSessionData()
            }
        }

        zeroButton.setOnClickListener {
            if (currentProfile.vehicleType == Profile.VehicleType.MOTORCYCLE) {
                foregroundService?.calibrateZero()
                resetAngleDisplay()
            }
        }

        stopButton.setOnClickListener {
            if (serviceBound) {
                saveAndFinishSession()
            }
        }
    }

    private fun setupOrientationToggle() {
        applyOrientationLock(false)
        orientationToggle?.setOnClickListener {
            isOrientationLocked = !isOrientationLocked
            applyOrientationLock(isOrientationLocked)
        }
    }

    private fun applyOrientationLock(locked: Boolean) {
        if (locked) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            orientationToggle?.setImageResource(R.drawable.ic_lock)
            orientationToggle?.imageAlpha = (255 * 0.95).toInt()
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            orientationToggle?.setImageResource(R.drawable.ic_unlock)
            orientationToggle?.imageAlpha = (255 * 0.5).toInt()
        }
    }

    private fun checkLocationPermission(): Boolean {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, requiredPermissions, 1000)
        }
        return allGranted
    }

    private fun startAndBindService() {
        val serviceIntent = Intent(this, ForegroundService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun resetSessionData() {
        foregroundService?.resetData()
        resetAngleDisplay()
        updateAccelerationDisplay(ForegroundService.AccelerationData())

        val startTime = SystemClock.elapsedRealtime()
        chronometer.base = startTime
        chronometer.start()
        if (::chronometerCar.isInitialized) {
            chronometerCar.base = startTime
            chronometerCar.start()
        }

        targetAngle = 0f
        currentAngle = 0f
        currentMapOrientation = 0f
        targetMapOrientation = 0f
        isFirstLocation = true

        totalDistance = 0.0
        lastDistancePoint = null
        distancePoints.clear()
        updateDistanceDisplay()

        routeOverlay.points.clear()
        mapView.invalidate()
        motionPredictor.addSample(GeoPoint(0.0, 0.0), 0f, 0f)
    }

    private fun resetAngleDisplay() {
        currentAngleText.text = getString(R.string.current_angle, 0)

        gaugeView.apply {
            angle = 0f
            maxLeftAngle = 0f
            maxRightAngle = 0f
            resetMaxima()
            invalidate()
        }
    }

    private fun initializeFirstLocation(location: Location) {
        if (isFirstLocation) {
            val geoPoint = GeoPoint(location.latitude, location.longitude)
            
            val currentZoom = mapView.zoomLevelDouble
            val metersPerPixel = 156543.03392 * cos(Math.toRadians(location.latitude)) / Math.pow(2.0, currentZoom)
            val offsetMeters = 30 * resources.displayMetrics.density * metersPerPixel
            
            val bearingRad = Math.toRadians(location.bearing.toDouble())
            val offsetLat = (offsetMeters * cos(bearingRad)) / 111320.0
            val offsetLon = (offsetMeters * sin(bearingRad)) / (111320.0 * cos(Math.toRadians(location.latitude)))
            
            val centerLat = geoPoint.latitude + offsetLat
            val centerLon = geoPoint.longitude + offsetLon
            
            mapView.controller.setCenter(GeoPoint(centerLat, centerLon))
            smoothLocationOverlay.updateTarget(geoPoint, location.bearing, immediate = true)
            motionPredictor.addSample(geoPoint, location.bearing, location.speed)

            lastDistancePoint = geoPoint
            distancePoints.add(geoPoint)

            isFirstLocation = false
        }
    }

    private fun updateDistance(newPoint: GeoPoint) {
        if (lastDistancePoint != null) {
            val distance = lastDistancePoint!!.distanceToAsDouble(newPoint)

            if (distance > 1.0) {
                totalDistance += distance / 1000.0
                lastDistancePoint = newPoint
                distancePoints.add(newPoint)
                updateDistanceDisplay()
            }
        } else {
            lastDistancePoint = newPoint
            distancePoints.add(newPoint)
        }
    }

    private fun recalculateTotalDistance() {
        totalDistance = 0.0
        if (distancePoints.size >= 2) {
            for (i in 1 until distancePoints.size) {
                totalDistance += distancePoints[i - 1].distanceToAsDouble(distancePoints[i]) / 1000.0
            }
        }
    }

    private fun updateDistanceDisplay() {
        distanceText.text = "%.2f km".format(totalDistance)
        if (::distanceTextCar.isInitialized) {
            distanceTextCar.text = "%.2f км".format(totalDistance)
        }
    }

    private fun saveAndFinishSession() {
        try {
            val race = createRaceFromSession()
            val routePoints = foregroundService?.getRoutePoints() ?: emptyList()

            RouteStorage.saveRoutePoints(this, race.id, routePoints)
            val allRaces = RouteStorage.loadRaces(this).toMutableList()
            allRaces.add(race)
            RouteStorage.saveRaces(this, allRaces)

            cleanupAndNavigate(race.id)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error saving race", e)
            showError("Error saving the race: ${e.message}")
        }
    }

    private fun createRaceFromSession(): Race {
        val routePoints = foregroundService?.getRoutePoints() ?: emptyList()
        val sessionNumber = getNextSessionNumber()

        return Race(
            profileId = currentProfile.id,
            id = System.currentTimeMillis(),
            routePoints = emptyList(),
            timestamp = System.currentTimeMillis(),
            duration = foregroundService?.getServiceDuration() ?: 0,
            absoluteTimestamp = System.currentTimeMillis(),
            maxLeftAngle = foregroundService?.getMaxLeftAngle() ?: 0f,
            maxRightAngle = foregroundService?.getMaxRightAngle() ?: 0f,
            maxSpeed = foregroundService?.getMaxSpeed() ?: 0f,
            name = "Session $sessionNumber",
            distance = totalDistance,
            time0to100 = 0L,
            time0to200 = 0L,
            time100to200 = 0L
        )
    }


    private fun getNextSessionNumber(): Int {
        val races = RouteStorage.loadRaces(this)
            .filter { it.profileId == currentProfile.id }

        return races.mapNotNull { race ->
            race.name?.let { name ->
                if (name.startsWith("Session ")) {
                    name.substringAfter("Session ").toIntOrNull()
                } else null
            }
        }.maxOrNull()?.plus(1) ?: 1
    }

    private fun cleanupAndNavigate(raceId: Long) {
        try {
            unbindService(serviceConnection)
            stopService(Intent(this, ForegroundService::class.java))
        } finally {
            serviceBound = false
        }

        startActivity(Intent(this, MapActivity::class.java).apply {
            putExtra("RACE_ID", raceId)
        })
        overridePendingTransition(0, 0)
        finish()
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } ?: run {
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            magnetometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRenderLoop()
        if (serviceBound) {
            unbindService(serviceConnection)
        }
    }

    private fun startRenderLoop() {
        renderHandler.post(renderRunnable)
    }

    private fun stopRenderLoop() {
        renderHandler.removeCallbacks(renderRunnable)
    }

    private fun startChronometer() {
        val startTime = foregroundService?.getStartTime() ?: SystemClock.elapsedRealtime()
        chronometer.base = startTime
        chronometer.start()
        if (::chronometerCar.isInitialized) {
            chronometerCar.base = startTime
            chronometerCar.start()
        }
    }

    private fun updateAccelerationDisplay(accelData: ForegroundService.AccelerationData) {
        // Performance metrics removed - no longer needed
        updateProfileBestTimes(accelData)
    }

    private fun updateProfileBestTimes(accelData: ForegroundService.AccelerationData) {
        // Only update max speed - performance metrics removed
        val currentSpeed = foregroundService?.getCurrentSpeed() ?: 0f
        if (currentSpeed > currentProfile.maxSpeed) {
            currentProfile.maxSpeed = currentSpeed
            val profiles = ProfileStorage.loadProfiles(this)
            profiles.find { it.id == currentProfile.id }?.apply {
                maxSpeed = currentProfile.maxSpeed
            }
            ProfileStorage.saveProfiles(this, profiles)
        }
    }

    private fun updateUIFromService() {
        foregroundService?.let { service ->
            val angle = service.getCurrentAngle()
            targetAngle = targetAngle * 0.7f + angle * 0.3f

            currentAngleText.text = getString(R.string.current_angle, angle.toInt())

            val speed = service.getCurrentSpeed()
            speedText.text = getString(R.string.current_speed, speed.toInt())
            if (::speedTextCar.isInitialized) {
                speedTextCar.text = speed.toInt().toString()
            }

            gaugeView.maxLeftAngle = service.getMaxLeftAngle()
            gaugeView.maxRightAngle = service.getMaxRightAngle()

            service.getLastLocation()?.let { location ->
                processLocationUpdate(location, speed)
            }

            updateAccelerationDisplay(service.getAccelerationData())
        }
    }

    private fun processLocationUpdate(location: Location, speed: Float) {
        val filtered = kalmanFilter.process(location)
        val geoPoint = GeoPoint(filtered.latitude, filtered.longitude)

        if (isFirstLocation) {
            initializeFirstLocation(filtered)
            return
        }

        updateDistance(geoPoint)

        var calculatedBearing = location.bearing

        if (lastProcessedLocation != null && speed > 1) {
            val lastGeoPoint = GeoPoint(
                lastProcessedLocation!!.latitude,
                lastProcessedLocation!!.longitude
            )

            val distance = geoPoint.distanceToAsDouble(lastGeoPoint)

            if (distance > 0.3) {
                val lat1 = Math.toRadians(lastGeoPoint.latitude)
                val lat2 = Math.toRadians(geoPoint.latitude)
                val deltaLon = Math.toRadians(geoPoint.longitude - lastGeoPoint.longitude)

                val x = sin(deltaLon) * cos(lat2)
                val y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)

                var movementBearing = Math.toDegrees(atan2(x, y)).toFloat()
                if (movementBearing < 0) movementBearing += 360f

                calculatedBearing = when {
                    speed > 20 -> movementBearing * 0.1f + location.bearing * 0.9f
                    speed > 5 -> movementBearing * 0.5f + location.bearing * 0.5f
                    else -> location.bearing
                }
            }
        }

        lastProcessedLocation = filtered

        smoothLocationOverlay.updateTarget(geoPoint, calculatedBearing, immediate = false)

        updateRoute(geoPoint, speed)

        if (speed > 2) {
            targetMapOrientation = -calculatedBearing
        }
        
        updateZoomBasedOnSpeed(speed)
    }

    private fun updateRoute(geoPoint: GeoPoint, speed: Float) {
        if (routeOverlay.points.isEmpty()) {
            routeOverlay.points.add(geoPoint)
        } else {
            val lastPoint = routeOverlay.points.last()
            val distance = geoPoint.distanceToAsDouble(lastPoint)

            val minDistance = when {
                speed > 50 -> 2.0
                speed > 20 -> 1.5
                speed > 2 -> 1.0
                else -> 5.0
            }

            if (distance > minDistance) {
                routeOverlay.points.add(geoPoint)
            }
        }
    }

    private fun updateMapAnimation() {
        val currentPosition = smoothLocationOverlay.getCurrentPosition()
        val currentBearing = smoothLocationOverlay.getCurrentBearing()
        
        val currentZoom = mapView.zoomLevelDouble
        val metersPerPixel = 156543.03392 * cos(Math.toRadians(currentPosition.latitude)) / Math.pow(2.0, currentZoom)
        val offsetMeters = 30 * resources.displayMetrics.density * metersPerPixel
        
        val bearingRad = Math.toRadians(currentBearing.toDouble())
        val offsetLat = (offsetMeters * cos(bearingRad)) / 111320.0
        val offsetLon = (offsetMeters * sin(bearingRad)) / (111320.0 * cos(Math.toRadians(currentPosition.latitude)))
        
        val newLat = currentPosition.latitude + offsetLat
        val newLon = currentPosition.longitude + offsetLon
        
        val currentCenter = mapView.mapCenter
        val latDiff = newLat - currentCenter.latitude
        val lonDiff = newLon - currentCenter.longitude
        
        val smoothNewLat = currentCenter.latitude + latDiff * 0.05f
        val smoothNewLon = currentCenter.longitude + lonDiff * 0.05f
        
        mapView.controller.setCenter(GeoPoint(smoothNewLat, smoothNewLon))
        updateMapOrientation()
    }

    private fun updateZoomBasedOnSpeed(speed: Float) {
        targetZoom = when {
            speed < 20 -> 19.5
            speed < 50 -> 18.5
            speed < 90 -> 17.5
            else -> 15.5
        }
    }
    
    private fun updateMapOrientation() {
        var diff = targetMapOrientation - currentMapOrientation
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f

        val speed = foregroundService?.getCurrentSpeed() ?: 0f

        val smoothingFactor = when {
            abs(diff) > 90 -> 0.15f
            abs(diff) > 45 -> 0.12f
            abs(diff) > 20 -> 0.08f
            speed > 50 -> 0.06f
            speed > 20 -> 0.05f
            else -> 0.04f
        }

        if (abs(diff) > 0.5f) {
            currentMapOrientation += diff * smoothingFactor
            while (currentMapOrientation > 360f) currentMapOrientation -= 360f
            while (currentMapOrientation < 0f) currentMapOrientation += 360f
            mapView.mapOrientation = currentMapOrientation
        }
        
        updateZoomSmoothly()
    }
    
    private fun updateZoomSmoothly() {
        val zoomDiff = targetZoom - currentZoom
        if (abs(zoomDiff) > 0.01) {
            currentZoom += zoomDiff * 0.08f
            mapView.controller.setZoom(currentZoom)
        }
    }

    private fun updateGaugeAnimation() {
        val diff = targetAngle - currentAngle
        val smoothing = if (abs(diff) > 10) 0.85f else 0.75f
        currentAngle += diff * (1 - smoothing)

        if (abs(diff) < 0.01f) {
            currentAngle = targetAngle
        }

        gaugeView.angle = currentAngle
        gaugeView.invalidate()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                if (azimuth < 0) azimuth += 360f
                var bearingDiff = azimuth - sensorBearing
                while (bearingDiff > 180) bearingDiff -= 360
                while (bearingDiff < -180) bearingDiff += 360
                sensorBearing += bearingDiff * 0.2f // Smooth factor
                while (sensorBearing < 0) sensorBearing += 360
                while (sensorBearing > 360) sensorBearing -= 360
            }

            Sensor.TYPE_ACCELEROMETER -> {
                gravity = lowPass(event.values.clone(), gravity)
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                geomagnetic = lowPass(event.values.clone(), geomagnetic)

                if (gravity != null && geomagnetic != null) {
                    if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)

                        var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                        if (azimuth < 0) azimuth += 360f

                        var bearingDiff = azimuth - sensorBearing
                        while (bearingDiff > 180) bearingDiff -= 360
                        while (bearingDiff < -180) bearingDiff += 360
                        sensorBearing += bearingDiff * 0.2f
                        while (sensorBearing < 0) sensorBearing += 360
                        while (sensorBearing > 360) sensorBearing -= 360
                    }
                }
            }
        }
    }




    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    private fun lowPass(input: FloatArray, output: FloatArray?): FloatArray {
        if (output == null) return input

        val alpha = 0.8f
        for (i in input.indices) {
            output[i] = output[i] + alpha * (input[i] - output[i])
        }
        return output
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == ForegroundService::class.java.name }
    }

    override fun onBackPressed() {
        if (foregroundService?.getRoutePoints()?.isNotEmpty() == true) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.exit_race_header))
                .setMessage(getString(R.string.exit_race))
                .setPositiveButton(getString(R.string.exit_race_yes)) { _, _ ->
                    navigateToRaces()
                }
                .setNegativeButton(getString(R.string.exit_race_no), null)
                .show()
        } else {
            navigateToRaces()
        }
    }

    private fun navigateToRaces() {
        startActivity(Intent(this, RacesActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putFloat("currentMapOrientation", currentMapOrientation)
        outState.putBoolean("isFirstLocation", isFirstLocation)
        outState.putDouble("totalDistance", totalDistance)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        currentMapOrientation = savedInstanceState.getFloat("currentMapOrientation", 0f)
        isFirstLocation = savedInstanceState.getBoolean("isFirstLocation", true)
        totalDistance = savedInstanceState.getDouble("totalDistance", 0.0)
        targetMapOrientation = currentMapOrientation
        updateDistanceDisplay()
        needsZeroAfterRotation = true
    }
}