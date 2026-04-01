package com.example.clinometer

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.settings.LanguageManager
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sqrt

class LeanCalibrationActivity : AppCompatActivity(), SensorEventListener {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private lateinit var tvProfileName: TextView
    private lateinit var tvPortraitStatus: TextView
    private lateinit var tvPortraitDate: TextView
    private lateinit var tvLandscapeStatus: TextView
    private lateinit var tvLandscapeDate: TextView
    private lateinit var tvLiveLean: TextView

    private lateinit var btnCalibratePortrait: Button
    private lateinit var btnClearPortrait: Button
    private lateinit var btnCalibrateLandscape: Button
    private lateinit var btnClearLandscape: Button
    private lateinit var btnClearAll: Button

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var profileId: Long = -1L
    private var isCalibrating = false
    private var calibratingLandscape = false
    private var filteredTiltDeg = 0f
    private var hasFilteredTilt = false
    private var stableCounter = 0
    private val stableSamples = mutableListOf<Float>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lean_calibration)
        applySystemBarsPaddingToRoot()

        window.statusBarColor = ContextCompat.getColor(this, R.color.dark_background)

        profileId = intent.getLongExtra("PROFILE_ID", ProfileStorage.getSelectedProfileId(this))

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, R.color.dark_background)))
        supportActionBar?.title = getString(R.string.lean_calibration_title)

        bindViews()
        initSensors()
        initButtons()
        updateUi()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private fun bindViews() {
        tvProfileName = findViewById(R.id.tvLeanProfileName)
        tvPortraitStatus = findViewById(R.id.tvLeanPortraitStatus)
        tvPortraitDate = findViewById(R.id.tvLeanPortraitDate)
        tvLandscapeStatus = findViewById(R.id.tvLeanLandscapeStatus)
        tvLandscapeDate = findViewById(R.id.tvLeanLandscapeDate)
        tvLiveLean = findViewById(R.id.tvLeanLiveValue)

        btnCalibratePortrait = findViewById(R.id.btnLeanCalibratePortrait)
        btnClearPortrait = findViewById(R.id.btnLeanClearPortrait)
        btnCalibrateLandscape = findViewById(R.id.btnLeanCalibrateLandscape)
        btnClearLandscape = findViewById(R.id.btnLeanClearLandscape)
        btnClearAll = findViewById(R.id.btnLeanClearAll)

        val profileName = ProfileStorage.loadProfiles(this).find { it.id == profileId }?.name ?: getString(R.string.track_compare_unknown_profile)
        tvProfileName.text = profileName
    }

    private fun initSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private fun initButtons() {
        btnCalibratePortrait.setOnClickListener { startCalibration(isLandscape = false) }
        btnCalibrateLandscape.setOnClickListener { startCalibration(isLandscape = true) }

        btnClearPortrait.setOnClickListener {
            LeanCalibrationStore.clearOrientation(this, profileId, isLandscape = false)
            updateUi()
        }

        btnClearLandscape.setOnClickListener {
            LeanCalibrationStore.clearOrientation(this, profileId, isLandscape = true)
            updateUi()
        }

        btnClearAll.setOnClickListener {
            LeanCalibrationStore.clearAll(this, profileId)
            updateUi()
        }
    }

    private fun startCalibration(isLandscape: Boolean) {
        if (accelerometer == null) {
            Toast.makeText(this, getString(R.string.lean_calibration_sensor_missing), Toast.LENGTH_LONG).show()
            return
        }

        calibratingLandscape = isLandscape
        isCalibrating = true
        filteredTiltDeg = 0f
        hasFilteredTilt = false
        stableCounter = 0
        stableSamples.clear()

        val orientationLabel = if (isLandscape) getString(R.string.lean_calibration_landscape) else getString(R.string.lean_calibration_portrait)
        tvLiveLean.text = getString(R.string.lean_calibration_hold_still, orientationLabel)
        sensorManager.unregisterListener(this)
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    private fun finishCalibration(offsetDeg: Float) {
        LeanCalibrationStore.saveOrientation(this, profileId, calibratingLandscape, offsetDeg)
        isCalibrating = false
        sensorManager.unregisterListener(this)
        val doneLabel = if (calibratingLandscape) getString(R.string.lean_calibration_landscape) else getString(R.string.lean_calibration_portrait)
        Toast.makeText(this, getString(R.string.lean_calibration_saved, doneLabel), Toast.LENGTH_SHORT).show()
        updateUi()
    }

    private fun updateUi() {
        val snapshot = LeanCalibrationStore.loadSnapshot(this, profileId)
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        if (snapshot.portraitCalibrated) {
            tvPortraitStatus.text = getString(R.string.lean_calibration_calibrated)
            tvPortraitStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            tvPortraitDate.text = dateFormat.format(snapshot.portraitTimestamp)
            btnClearPortrait.isEnabled = true
            btnCalibratePortrait.text = getString(R.string.lean_calibration_recalibrate)
        } else {
            tvPortraitStatus.text = getString(R.string.lean_calibration_not_calibrated)
            tvPortraitStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
            tvPortraitDate.text = getString(R.string.lean_calibration_missing_orientation)
            btnClearPortrait.isEnabled = false
            btnCalibratePortrait.text = getString(R.string.lean_calibration_calibrate)
        }

        if (snapshot.landscapeCalibrated) {
            tvLandscapeStatus.text = getString(R.string.lean_calibration_calibrated)
            tvLandscapeStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            tvLandscapeDate.text = dateFormat.format(snapshot.landscapeTimestamp)
            btnClearLandscape.isEnabled = true
            btnCalibrateLandscape.text = getString(R.string.lean_calibration_recalibrate)
        } else {
            tvLandscapeStatus.text = getString(R.string.lean_calibration_not_calibrated)
            tvLandscapeStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
            tvLandscapeDate.text = getString(R.string.lean_calibration_missing_orientation)
            btnClearLandscape.isEnabled = false
            btnCalibrateLandscape.text = getString(R.string.lean_calibration_calibrate)
        }

        btnClearAll.isEnabled = snapshot.hasAnyCalibration()

        if (!isCalibrating) {
            tvLiveLean.text = getString(R.string.lean_calibration_idle_hint)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val ev = event ?: return
        if (!isCalibrating || ev.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = ev.values[0]
        val y = ev.values[1]
        val z = ev.values[2]
        val total = sqrt(x * x + y * y + z * z)
        if (total <= 0.001f) return

        val normalizedAxis = if (calibratingLandscape) (y / total) else (x / total)
        val rawTilt = (-Math.toDegrees(asin(normalizedAxis.coerceIn(-1f, 1f).toDouble()))).toFloat()

        if (!hasFilteredTilt) {
            filteredTiltDeg = rawTilt
            hasFilteredTilt = true
        } else {
            filteredTiltDeg += 0.2f * (rawTilt - filteredTiltDeg)
        }

        val gravityDelta = abs(total - SensorManager.GRAVITY_EARTH)
        val movementDelta = abs(rawTilt - filteredTiltDeg)
        val isStable = gravityDelta < 1.1f && movementDelta < 0.45f

        if (isStable) {
            stableCounter += 1
            stableSamples.add(filteredTiltDeg)
            if (stableSamples.size > 160) {
                stableSamples.removeAt(0)
            }
        } else {
            stableCounter = (stableCounter - 5).coerceAtLeast(0)
            if (stableSamples.size > 20) {
                stableSamples.subList(0, stableSamples.size - 20).clear()
            }
        }

        tvLiveLean.text = getString(
            R.string.lean_calibration_live_value,
            filteredTiltDeg,
            stableCounter.coerceAtMost(80)
        )

        if (stableCounter >= 80 && stableSamples.size >= 60) {
            val avgOffset = stableSamples.average().toFloat()
            finishCalibration(avgOffset)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
