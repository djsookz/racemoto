package com.example.clinometer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import com.google.android.gms.location.*
import kotlin.math.atan2
import kotlin.math.sqrt
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import android.graphics.Color
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import android.os.Parcel
import android.os.Parcelable
import android.os.SystemClock
import android.widget.Chronometer
import com.example.clinometer.RoutePoint



class MainActivity : AppCompatActivity(), SensorEventListener {

    // Map & route

    private val routePoints = mutableListOf<RoutePoint>()
    private var currentCalibratedAngle = 0f
    private lateinit var locationRequest: LocationRequest


    // Sensors
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var filteredAngle = 0f
    private val filterAlpha = 0.015f
    private var offsetAngle = 0f
    private var maxLeftAngle = 0f
    private var maxRightAngle = 0f

    // GPS
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locCallback: LocationCallback
    private var maxSpeed = 0f

    // UI
    private lateinit var gaugeView: GaugeView
    private lateinit var currentAngleText: TextView
    private lateinit var maxLeftText: TextView
    private lateinit var maxRightText: TextView
    private lateinit var speedText: TextView
    private lateinit var maxSpeedText: TextView
    private lateinit var resetButton: Button
    private lateinit var stopButton: Button
    private lateinit var chronometer: Chronometer
    private var startTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.flags?.and(Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0) {
            routePoints.clear()
            maxLeftAngle = 0f
            maxRightAngle = 0f
            maxSpeed = 0f
        }
        // osmdroid config before view inflation
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        setContentView(R.layout.activity_main)

        chronometer = findViewById(R.id.chronometer)

        if (savedInstanceState == null) {
            startChronometer()
        }
        // --- UI find ---
        gaugeView = findViewById(R.id.gaugeView)
        currentAngleText = findViewById(R.id.currentAngleText)
        maxLeftText = findViewById(R.id.maxLeftText)
        maxRightText = findViewById(R.id.maxRightText)
        speedText = findViewById(R.id.speedText)
        maxSpeedText = findViewById(R.id.maxSpeedText)
        resetButton = findViewById(R.id.btnReset)
        stopButton = findViewById(R.id.btnStop)

        // --- Sensors setup ---
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // --- GPS setup ---
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(500)
            .setMinUpdateIntervalMillis(200)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .build()
        locCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { onNewLocation(it) }
            }
        }

        // Restore state if any
        savedInstanceState?.let {
            offsetAngle = it.getFloat("OFFSET_ANGLE", 0f)
            maxLeftAngle = it.getFloat("MAX_LEFT_ANGLE", 0f)
            maxRightAngle = it.getFloat("MAX_RIGHT_ANGLE", 0f)
            maxSpeed = it.getFloat("MAX_SPEED", 0f)
        }

        // Initial UI
        currentAngleText.text = getString(R.string.current_angle, 0)
        maxLeftText.text = getString(R.string.max_left_angle, 0)
        maxRightText.text = getString(R.string.max_right_angle, 0)
        speedText.text = getString(R.string.current_speed, 0)
        maxSpeedText.text = getString(R.string.max_speed, 0)

        // Reset logic
        resetButton.setOnClickListener {
            startTime = SystemClock.elapsedRealtime()
            offsetAngle = filteredAngle
            routePoints.clear()
            maxLeftAngle = 0f
            maxRightAngle = 0f
            maxSpeed = 0f
            maxLeftText.text = getString(R.string.max_left_angle, 0)
            maxRightText.text = getString(R.string.max_right_angle, 0)
            maxSpeedText.text = getString(R.string.max_speed, 0)
            chronometer.stop()
            chronometer.base = startTime // връща хронометъра на 00:00
            chronometer.start()
        }

        // Stop logic: go to MapActivity to display full route
        stopButton.setOnClickListener {
            val realDuration = routePoints.maxOfOrNull { it.timestamp } ?: 0L
            RaceRepository.addRace(routePoints, realDuration)

            // 1. Запис в репозиторито (ако го ползваш за RAM)
            RaceRepository.addRace(routePoints, realDuration)

            // 2. Запис във файл – за да се запази и след рестарт
            val previousRoutes = RouteStorage.loadRoutes(this).toMutableList()
            previousRoutes.add(routePoints)
            RouteStorage.saveRoutes(this, previousRoutes)

            // 3. Стартиране на списъка с маршрути
            startActivity(Intent(this, RacesActivity::class.java))
            finish()
        }

    }
    private fun startChronometer() {
        startTime = SystemClock.elapsedRealtime()
        chronometer.base = startTime
        chronometer.start()
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1000
            )
        } else {
            fusedClient.requestLocationUpdates(locationRequest, locCallback, Looper.getMainLooper())

        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        fusedClient.removeLocationUpdates(locCallback)
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putFloat("OFFSET_ANGLE", offsetAngle)
        out.putFloat("MAX_LEFT_ANGLE", maxLeftAngle)
        out.putFloat("MAX_RIGHT_ANGLE", maxRightAngle)
        out.putFloat("MAX_SPEED", maxSpeed)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val raw = Math.toDegrees(atan2(x.toDouble(), sqrt(y * y + z * z).toDouble())).toFloat()
            filteredAngle = filterAlpha * raw + (1 - filterAlpha) * filteredAngle
            val calibrated = offsetAngle - filteredAngle
            currentCalibratedAngle = calibrated
            gaugeView.angle = calibrated
            gaugeView.invalidate()
            currentAngleText.text = getString(R.string.current_angle, calibrated.toInt())

            if (calibrated < maxLeftAngle) {
                maxLeftAngle = calibrated
                maxLeftText.text = getString(R.string.max_left_angle, calibrated.toInt())
            }
            if (calibrated > maxRightAngle) {
                maxRightAngle = calibrated
                maxRightText.text = getString(R.string.max_right_angle, calibrated.toInt())
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, acc: Int) {}


    private fun onNewLocation(loc: Location) {
        val speedKmhF = loc.speed * 3.6f

        val pt = RoutePoint(
            geoPoint = GeoPoint(loc.latitude, loc.longitude),
            speed = speedKmhF,
            angle = currentCalibratedAngle,
            timestamp = SystemClock.elapsedRealtime() - startTime // Време от старта
        )
            routePoints.add(pt)

            val speedKmh = speedKmhF.toInt()
            speedText.text = getString(R.string.current_speed, speedKmh)
            if (speedKmh > maxSpeed) {
                maxSpeed = speedKmh.toFloat()
                maxSpeedText.text = getString(R.string.max_speed, speedKmh)
            }

        }


    }

