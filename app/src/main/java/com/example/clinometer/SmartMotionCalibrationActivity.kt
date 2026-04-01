package com.example.clinometer

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.settings.LanguageManager
import java.text.SimpleDateFormat
import java.util.Locale

class SmartMotionCalibrationActivity : AppCompatActivity(), SensorEventListener {

    private var calibrationEngine: SmartCalibrationEngine? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private enum class Phase {
        IDLE,
        STILL,
        LEAN_LEFT,
        RETURN_UPRIGHT,
        FORWARD,
        COMPLETE
    }

    private lateinit var tvPortraitStatus: TextView
    private lateinit var tvPortraitDate: TextView
    private lateinit var pbPortraitProgress: ProgressBar
    private lateinit var btnCalibratePortrait: Button
    private lateinit var btnClearPortrait: Button

    private lateinit var tvLandscapeStatus: TextView
    private lateinit var tvLandscapeDate: TextView
    private lateinit var pbLandscapeProgress: ProgressBar
    private lateinit var btnCalibrateLandscape: Button
    private lateinit var btnClearLandscape: Button

    private lateinit var btnClearAll: Button
    private lateinit var btnCancel: Button
    private lateinit var btnContinue: Button

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var linearAccelSensor: Sensor? = null
    private var gravitySensor: Sensor? = null

    private var profileId: Long = -1L
    private var isMotorcycleProfile: Boolean = true
    private var phase: Phase = Phase.IDLE
    private var phaseStartMs: Long = 0L
    private var calibrationActive: Boolean = false
    private var targetLandscape: Boolean = false

    private val gravityLp = FloatArray(3)
    private var gravityLpInitialized = false
    private val gravitySensorValues = FloatArray(3)
    private var gravitySensorTimestampNs: Long = 0L
    private val linearSensorValues = FloatArray(3)
    private var linearSensorTimestampNs: Long = 0L
    private val motionFastLp = FloatArray(3)
    private var motionFastLpInitialized = false
    private val lastRawAccel = FloatArray(3)
    private var lastRawAccelTimestampNs: Long = 0L

    private val stillGravitySum = FloatArray(3)
    private val stillMaxAxis = FloatArray(3)
    private var stillLinearMagSum = 0f
    private var stillLinearGoodCount = 0
    private var stillLinearCount = 0
    private var stillSamplingStarted = false
    private var lastGuidanceUiUpdateMs = 0L
    private var lastProgressUiUpdateMs = 0L

    private val stillGyroBiasSum = FloatArray(3)
    private var stillGyroMagSum = 0f
    private var stillGyroGoodCount = 0
    private var stillGyroCount = 0

    private val leanReferenceGravity = FloatArray(3)
    private val leanGravitySum = FloatArray(3)
    private var leanSampleCount = 0
    private var leanTargetStableSamples = 0
    private var uprightStableSamples = 0

    private val forwardSettleBaselineSum = FloatArray(3)
    private var forwardSettleCount = 0
    private var forwardSettleBaselineLocked = false
    private val forwardLateralAxis = FloatArray(3)
    private var forwardLateralAxisReady = false

    private data class WeightedVector(val magnitude: Float, val vector: FloatArray)
    private val forwardTopVectors = mutableListOf<WeightedVector>()
    private var forwardAcceptedSamples = 0
    private var forwardSampleCount = 0
    private var forwardTrigger = 0.6f
    private val forwardBaseline = FloatArray(3)
    private var forwardNoiseFloor = 0f
    private var forwardExcessTrigger = 0f
    private var forwardDirectionUnit: FloatArray? = null
    private var forwardDirectionStreak = 0
    private var forwardMoveStatusShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drag_calibration)
        applySystemBarsPaddingToRoot()

        window.statusBarColor = ContextCompat.getColor(this, R.color.dark_background)

        profileId = intent.getLongExtra("PROFILE_ID", ProfileStorage.getSelectedProfileId(this))

        DragCalibration.init(this)
        DragCalibration.setProfile(profileId)

        val selectedProfile = ProfileStorage.loadProfiles(this).find { it.id == profileId }
        isMotorcycleProfile = selectedProfile?.vehicleType == Profile.VehicleType.MOTORCYCLE
        val profileName = selectedProfile?.name ?: "Unknown"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "${getString(R.string.smart_calibration_title)} - $profileName"

        initializeViews()
        initializeSensors()
        setupListeners()
        updateUi()
    }

    override fun onResume() {
        super.onResume()
        DragCalibration.setProfile(profileId)
        if (calibrationActive) {
            registerSensors()
        } else {
            updateUi()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterSensors()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterSensors()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun initializeViews() {
        tvPortraitStatus = findViewById(R.id.tvPortraitStatus)
        tvPortraitDate = findViewById(R.id.tvPortraitDate)
        pbPortraitProgress = findViewById(R.id.pbPortraitProgress)
        btnCalibratePortrait = findViewById(R.id.btnCalibratePortrait)
        btnClearPortrait = findViewById(R.id.btnClearPortrait)

        tvLandscapeStatus = findViewById(R.id.tvLandscapeStatus)
        tvLandscapeDate = findViewById(R.id.tvLandscapeDate)
        pbLandscapeProgress = findViewById(R.id.pbLandscapeProgress)
        btnCalibrateLandscape = findViewById(R.id.btnCalibrateLandscape)
        btnClearLandscape = findViewById(R.id.btnClearLandscape)

        btnClearAll = findViewById(R.id.btnClearAll)
        btnCancel = findViewById(R.id.btnCancel)
        btnContinue = findViewById(R.id.btnContinue)

        // Settings entrypoint: keep classic look but only use clear-all as global action.
        btnCancel.visibility = android.view.View.GONE
        btnContinue.visibility = android.view.View.GONE
        btnClearAll.visibility = android.view.View.VISIBLE
    }

    private fun initializeSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    }

    private fun setupListeners() {
        btnCalibratePortrait.setOnClickListener { startCalibration(isLandscape = false) }
        btnCalibrateLandscape.setOnClickListener { startCalibration(isLandscape = true) }

        btnClearPortrait.setOnClickListener { clearOrientation(isLandscape = false) }
        btnClearLandscape.setOnClickListener { clearOrientation(isLandscape = true) }

        btnClearAll.setOnClickListener {
            if (calibrationActive) return@setOnClickListener
            MotionCalibrationStore.clearSnapshot(this, profileId)
            MotionCalibrationStore.clearSnapshot(this, profileId, isLandscape = false)
            MotionCalibrationStore.clearSnapshot(this, profileId, isLandscape = true)
            LeanCalibrationStore.clearAll(this, profileId)
            DragCalibration.clearCalibration()
            updateUi()
            Toast.makeText(this, getString(R.string.smart_calibration_cleared), Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearOrientation(isLandscape: Boolean) {
        if (calibrationActive) return

        MotionCalibrationStore.clearSnapshot(this, profileId, isLandscape)
        LeanCalibrationStore.clearOrientation(this, profileId, isLandscape)
        DragCalibration.clearOrientation(isLandscape)

        updateUi()
        Toast.makeText(this, getString(R.string.smart_calibration_cleared), Toast.LENGTH_SHORT).show()
    }

    private fun startCalibration(isLandscape: Boolean) {
        if (accelerometer == null) {
            Toast.makeText(this, getString(R.string.lean_calibration_sensor_missing), Toast.LENGTH_LONG).show()
            return
        }

        calibrationEngine = SmartCalibrationEngine(
            hasGyroSensor = gyroscope != null,
            useLeanStep = shouldUseLeanStep()
        )

        targetLandscape = isLandscape
        val initialFrame = calibrationEngine?.start(SystemClock.elapsedRealtime())
        resetRuntimeState()
        phase = Phase.STILL
        phaseStartMs = SystemClock.elapsedRealtime()
        calibrationActive = true

        setButtonsEnabled(false)
        val stillStatus = if (shouldUseLeanStep()) {
            getString(R.string.smart_calibration_status_still_gyro)
        } else {
            getString(R.string.smart_calibration_status_still)
        }
        setActiveStatus(stillStatus, android.R.color.holo_orange_light)
        setActiveDate(
            String.format(
                Locale.getDefault(),
                getString(R.string.smart_calibration_guidance_still_warmup),
                STILL_WARMUP_MS / 1000f
            )
        )
        setActivePhaseProgress(phaseStartMs, percent = 0, force = true)

        registerSensors()
        initialFrame?.let { applyEngineFrame(it) }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnCalibratePortrait.isEnabled = enabled
        btnCalibrateLandscape.isEnabled = enabled
        btnClearPortrait.isEnabled = enabled
        btnClearLandscape.isEnabled = enabled
        btnClearAll.isEnabled = enabled
    }

    private fun registerSensors() {
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        linearAccelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    private fun unregisterSensors() {
        accelerometer?.let { sensorManager.unregisterListener(this, it) }
        gyroscope?.let { sensorManager.unregisterListener(this, it) }
        linearAccelSensor?.let { sensorManager.unregisterListener(this, it) }
        gravitySensor?.let { sensorManager.unregisterListener(this, it) }
    }

    private fun resetRuntimeState() {
        phase = Phase.IDLE
        phaseStartMs = 0L
        calibrationActive = false
        gravityLpInitialized = false
        gravitySensorTimestampNs = 0L
        linearSensorTimestampNs = 0L
        motionFastLpInitialized = false
        lastRawAccelTimestampNs = 0L
        gravitySensorValues.fill(0f)
        linearSensorValues.fill(0f)
        motionFastLp.fill(0f)
        lastRawAccel.fill(0f)
        stillGravitySum.fill(0f)
        stillMaxAxis.fill(0f)
        stillLinearMagSum = 0f
        stillLinearGoodCount = 0
        stillLinearCount = 0
        stillSamplingStarted = false
        lastGuidanceUiUpdateMs = 0L
        lastProgressUiUpdateMs = 0L
        stillGyroBiasSum.fill(0f)
        stillGyroMagSum = 0f
        stillGyroGoodCount = 0
        stillGyroCount = 0
        forwardTopVectors.clear()
        forwardAcceptedSamples = 0
        forwardSampleCount = 0
        forwardTrigger = 0.6f
        forwardBaseline.fill(0f)
        forwardNoiseFloor = 0f
        forwardExcessTrigger = 0f
        forwardDirectionUnit = null
        forwardDirectionStreak = 0
        forwardMoveStatusShown = false
        leanReferenceGravity.fill(0f)
        leanGravitySum.fill(0f)
        leanSampleCount = 0
        leanTargetStableSamples = 0
        uprightStableSamples = 0
        forwardSettleBaselineSum.fill(0f)
        forwardSettleCount = 0
        forwardSettleBaselineLocked = false
        forwardLateralAxis.fill(0f)
        forwardLateralAxisReady = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val ev = event ?: return
        val now = SystemClock.elapsedRealtime()

        val engine = calibrationEngine
        if (!calibrationActive || engine == null) return

        when (ev.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val frame = engine.onAccelerometer(ev.values, ev.timestamp, now)
                applyEngineFrame(frame)
            }
            Sensor.TYPE_GYROSCOPE -> engine.onGyroscope(ev.values)
            Sensor.TYPE_LINEAR_ACCELERATION -> engine.onLinear(ev.values, ev.timestamp)
            Sensor.TYPE_GRAVITY -> engine.onGravity(ev.values, ev.timestamp)
        }
    }

    private fun applyEngineFrame(frame: SmartCalibrationEngine.Frame) {
        phase = when (frame.phase) {
            SmartCalibrationEngine.Phase.IDLE -> Phase.IDLE
            SmartCalibrationEngine.Phase.STILL -> Phase.STILL
            SmartCalibrationEngine.Phase.LEAN_LEFT -> Phase.LEAN_LEFT
            SmartCalibrationEngine.Phase.RETURN_UPRIGHT -> Phase.RETURN_UPRIGHT
            SmartCalibrationEngine.Phase.FORWARD -> Phase.FORWARD
            SmartCalibrationEngine.Phase.COMPLETE -> Phase.COMPLETE
        }

        setActivePhaseProgress(SystemClock.elapsedRealtime(), frame.progressPercent, force = true)

        when (val guidance = frame.guidance) {
            is SmartCalibrationEngine.Guidance.StillWarmup -> {
                val stillStatus = if (shouldUseLeanStep()) {
                    getString(R.string.smart_calibration_status_still_gyro)
                } else {
                    getString(R.string.smart_calibration_status_still)
                }
                setActiveStatus(stillStatus, android.R.color.holo_orange_light)
                setGuidanceText(
                    SystemClock.elapsedRealtime(),
                    String.format(
                        Locale.getDefault(),
                        getString(R.string.smart_calibration_guidance_still_warmup),
                        guidance.remainingSec
                    ),
                    force = true
                )
            }
            is SmartCalibrationEngine.Guidance.Still -> {
                val stillStatus = if (shouldUseLeanStep()) {
                    getString(R.string.smart_calibration_status_still_gyro)
                } else {
                    getString(R.string.smart_calibration_status_still)
                }
                setActiveStatus(stillStatus, android.R.color.holo_orange_light)
                setGuidanceText(
                    SystemClock.elapsedRealtime(),
                    String.format(
                        Locale.getDefault(),
                        getString(R.string.smart_calibration_guidance_still),
                        guidance.remainingSec
                    ),
                    force = true
                )
            }
            is SmartCalibrationEngine.Guidance.LeanLeft -> {
                setActiveStatus(getString(R.string.smart_calibration_status_lean_left_target), android.R.color.holo_orange_light)
                setGuidanceText(
                    SystemClock.elapsedRealtime(),
                    String.format(
                        Locale.getDefault(),
                        getString(R.string.smart_calibration_guidance_lean_left),
                        guidance.targetDeg,
                        guidance.currentDeg,
                        guidance.remainingDeg,
                        guidance.hold,
                        guidance.holdRequired
                    ),
                    force = true
                )
            }
            is SmartCalibrationEngine.Guidance.ReturnUpright -> {
                setActiveStatus(getString(R.string.smart_calibration_status_return_upright), android.R.color.holo_orange_light)
                setGuidanceText(
                    SystemClock.elapsedRealtime(),
                    String.format(
                        Locale.getDefault(),
                        getString(R.string.smart_calibration_guidance_return_upright),
                        guidance.hold,
                        guidance.holdRequired
                    ),
                    force = true
                )
            }
            is SmartCalibrationEngine.Guidance.ForwardWait -> {
                val forwardStatus = if (shouldUseLeanStep()) {
                    getString(R.string.smart_calibration_status_forward_wait_gyro)
                } else {
                    getString(R.string.smart_calibration_status_forward_wait)
                }
                setActiveStatus(forwardStatus, android.R.color.holo_orange_light)
                setGuidanceText(
                    SystemClock.elapsedRealtime(),
                    String.format(
                        Locale.getDefault(),
                        getString(R.string.smart_calibration_guidance_forward_wait),
                        guidance.remainingSec
                    ),
                    force = true
                )
            }
            is SmartCalibrationEngine.Guidance.ForwardDrive -> {
                val moveStatus = if (shouldUseLeanStep()) {
                    getString(R.string.smart_calibration_status_forward_gyro)
                } else {
                    getString(R.string.smart_calibration_status_forward)
                }
                setActiveStatus(moveStatus, android.R.color.holo_orange_light)
                setGuidanceText(
                    SystemClock.elapsedRealtime(),
                    String.format(
                        Locale.getDefault(),
                        getString(R.string.smart_calibration_guidance_forward_drive),
                        guidance.accepted,
                        guidance.target
                    ),
                    force = true
                )
            }
            null -> Unit
        }

        frame.failure?.let { reason ->
            val text = when (reason) {
                SmartCalibrationEngine.FailureReason.NOT_ENOUGH_STILL -> getString(R.string.smart_calibration_error_not_enough_still)
                SmartCalibrationEngine.FailureReason.NOT_ENOUGH_FORWARD -> getString(R.string.smart_calibration_error_not_enough_forward)
                SmartCalibrationEngine.FailureReason.INVALID_GRAVITY -> getString(R.string.smart_calibration_error_gravity)
                SmartCalibrationEngine.FailureReason.INVALID_FORWARD_VECTOR -> getString(R.string.smart_calibration_error_forward_vector)
                SmartCalibrationEngine.FailureReason.LEAN_LEFT_TOO_SMALL -> getString(R.string.smart_calibration_error_not_enough_lean_left)
                SmartCalibrationEngine.FailureReason.UPRIGHT_TIMEOUT -> getString(R.string.smart_calibration_error_not_upright_return)
            }
            onCalibrationFailed(text)
            calibrationEngine = null
            return
        }

        frame.result?.let { result ->
            persistEngineResult(result)
            calibrationEngine = null
        }
    }

    private fun persistEngineResult(result: SmartCalibrationEngine.CalibrationResult) {
        unregisterSensors()
        calibrationActive = false

        if (targetLandscape) {
            DragCalibration.lockLandscapeAxes(
                forward = result.forwardNorm,
                lateral = result.rightNorm,
                baseline = result.gravityAvg,
                maxVibrX = result.stillMaxAxis[0],
                maxVibrY = result.stillMaxAxis[1],
                maxVibrZ = result.stillMaxAxis[2]
            )
        } else {
            DragCalibration.lockPortraitAxes(
                forward = result.forwardNorm,
                lateral = result.rightNorm,
                baseline = result.gravityAvg,
                maxVibrX = result.stillMaxAxis[0],
                maxVibrY = result.stillMaxAxis[1],
                maxVibrZ = result.stillMaxAxis[2]
            )
        }

        MotionCalibrationStore.saveSnapshot(
            context = this,
            profileId = profileId,
            gyroBiasRad = result.gyroBias,
            hasGyroBias = result.hasGyroBias,
            qualityScore = result.quality,
            stillSamples = result.stillLinearCount,
            forwardSamples = result.forwardAcceptedSamples,
            stillLinearAvg = result.stillLinearAvg,
            stillVibrationMag = result.stillVibrationMag,
            forwardNoiseFloor = result.forwardNoiseFloor,
            forwardExcessTrigger = result.forwardExcessTrigger,
            isLandscape = targetLandscape
        )

        val leanOffsetComponent = if (targetLandscape) {
            result.leanOffsetLandscapeComponent
        } else {
            result.leanOffsetPortraitComponent
        }
        val leanOffsetDeg = SmartCalibrationEngine.leanOffsetDegFromGravityComponent(leanOffsetComponent)
        LeanCalibrationStore.saveOrientation(this, profileId, targetLandscape, leanOffsetDeg)

        val successMsg = if (targetLandscape) {
            getString(R.string.calibration_landscape_success)
        } else {
            getString(R.string.calibration_portrait_success)
        }
        setActiveStatus(getString(R.string.calibration_success), android.R.color.holo_green_dark)
        setActiveDate(
            String.format(
                Locale.getDefault(),
                getString(R.string.smart_calibration_success_metrics),
                result.quality * 100f,
                result.gyroBias[0],
                result.gyroBias[1],
                result.gyroBias[2]
            )
        )
        Toast.makeText(this, successMsg, Toast.LENGTH_SHORT).show()
        updateUi()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun setGuidanceText(nowMs: Long, text: String, force: Boolean = false) {
        if (!force && nowMs - lastGuidanceUiUpdateMs < UI_GUIDANCE_UPDATE_INTERVAL_MS) return
        lastGuidanceUiUpdateMs = nowMs
        setActiveDate(text)
    }

    private fun shouldUseLeanStep(): Boolean {
        return isMotorcycleProfile && gyroscope != null
    }

    private fun onCalibrationFailed(reason: String) {
        calibrationActive = false
        calibrationEngine = null
        unregisterSensors()
        setOrientationProgressVisible(targetLandscape, visible = false)
        setActiveStatus(getString(R.string.smart_calibration_status_failed), android.R.color.holo_red_light)
        setActiveDate(reason)
        setButtonsEnabled(true)
        Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
    }

    private fun updateUi() {
        val portraitSnapshot = MotionCalibrationStore.loadSnapshot(this, profileId, isLandscape = false)
        val landscapeSnapshot = MotionCalibrationStore.loadSnapshot(this, profileId, isLandscape = true)

        updateOrientationUi(
            isLandscape = false,
            snapshot = portraitSnapshot,
            statusView = tvPortraitStatus,
            dateView = tvPortraitDate,
            calibrateButton = btnCalibratePortrait,
            clearButton = btnClearPortrait,
            notCalibratedText = getString(R.string.calibration_please_portrait)
        )
        updateOrientationUi(
            isLandscape = true,
            snapshot = landscapeSnapshot,
            statusView = tvLandscapeStatus,
            dateView = tvLandscapeDate,
            calibrateButton = btnCalibrateLandscape,
            clearButton = btnClearLandscape,
            notCalibratedText = getString(R.string.calibration_please_landscape)
        )

        val hasAny = portraitSnapshot.calibrated || landscapeSnapshot.calibrated
        btnClearAll.isEnabled = hasAny
    }

    private fun updateOrientationUi(
        isLandscape: Boolean,
        snapshot: MotionCalibrationStore.Snapshot,
        statusView: TextView,
        dateView: TextView,
        calibrateButton: Button,
        clearButton: Button,
        notCalibratedText: String
    ) {
        if (snapshot.calibrated) {
            val date = if (snapshot.timestamp > 0L) {
                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(snapshot.timestamp)
            } else {
                "--"
            }
            statusView.text = "${getString(R.string.calibration_status_calibrated)}"
            statusView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            dateView.text = String.format(
                Locale.getDefault(),
                "%s  Q:%d%%",
                getString(R.string.calibration_date_format, date),
                (snapshot.qualityScore * 100f).toInt().coerceIn(0, 100)
            )
            calibrateButton.text = getString(R.string.calibration_btn_recalibrate)
            clearButton.isEnabled = true
        } else {
            statusView.text = getString(R.string.calibration_status_not_calibrated)
            statusView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            dateView.text = notCalibratedText
            calibrateButton.text = getString(R.string.calibration_btn_calibrate)
            clearButton.isEnabled = false
        }

        if (calibrationActive && targetLandscape == isLandscape) {
            calibrateButton.isEnabled = false
            clearButton.isEnabled = false
            setOrientationProgressVisible(isLandscape, visible = true)
        } else {
            setOrientationProgressVisible(isLandscape, visible = false)
        }
    }

    private fun setActiveStatus(text: String, colorRes: Int) {
        val statusView = if (targetLandscape) tvLandscapeStatus else tvPortraitStatus
        statusView.text = text
        statusView.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun setActiveDate(text: String) {
        val dateView = if (targetLandscape) tvLandscapeDate else tvPortraitDate
        dateView.text = text
    }

    private fun setActivePhaseProgress(nowMs: Long, percent: Int, force: Boolean = false) {
        if (!force && nowMs - lastProgressUiUpdateMs < UI_PROGRESS_UPDATE_INTERVAL_MS) return
        lastProgressUiUpdateMs = nowMs
        val progressView = if (targetLandscape) pbLandscapeProgress else pbPortraitProgress
        progressView.visibility = View.VISIBLE
        progressView.progress = percent.coerceIn(0, 100)
    }

    private fun setOrientationProgressVisible(isLandscape: Boolean, visible: Boolean) {
        val progressView = if (isLandscape) pbLandscapeProgress else pbPortraitProgress
        progressView.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) {
            progressView.progress = 0
        }
    }


    companion object {
        private const val UI_GUIDANCE_UPDATE_INTERVAL_MS = 220L
        private const val UI_PROGRESS_UPDATE_INTERVAL_MS = 140L
        private const val STILL_WARMUP_MS = 1200L
    }
}
