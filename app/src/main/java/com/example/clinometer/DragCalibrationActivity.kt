package com.example.clinometer

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.clinometer.data.CalibrationReminderStore
import com.example.clinometer.data.ProfileStorage
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.clinometer.settings.LanguageManager
import com.example.clinometer.main.MainContainerActivity
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.min

class DragCalibrationActivity : AppCompatActivity(), SensorEventListener {
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }
    
    private lateinit var tvPortraitStatus: TextView
    private lateinit var tvPortraitDate: TextView
    private lateinit var btnCalibratePortrait: Button
    private lateinit var btnClearPortrait: Button
    
    private lateinit var tvLandscapeStatus: TextView
    private lateinit var tvLandscapeDate: TextView
    private lateinit var btnCalibrateLandscape: Button
    private lateinit var btnClearLandscape: Button
    
    private lateinit var btnClearAll: Button
    private lateinit var btnCancel: Button
    private lateinit var btnCalibrateLater: Button
    private lateinit var btnContinue: Button
    
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    
    private val rawAcceleration = FloatArray(3)  // RAW accelerometer данни (gravity + acceleration)
    
    // UNIVERSAL GRAVITY-BASED CALIBRATION
    private var isCalibrating = false
    private var calibrationStartTime = 0L

    // FORWARD събиране (20 samples = ~200ms за бърз и стабилен vector!)
    private val forwardSamplesX = mutableListOf<Float>()
    private val forwardSamplesY = mutableListOf<Float>()
    private val forwardSamplesZ = mutableListOf<Float>()
    private val FORWARD_SAMPLES_NEEDED = 20  // 20 samples @ 100Hz = 200ms (баланс между скорост и точност!)
    private var gravityVector = FloatArray(3)  // От phase 1
    
    // DEPRECATED старата логика (backward compatibility)
    private var calibratingOrientation: String = ""
    private var isLearningForward = false
    private var calibrationSamples = mutableListOf<Float>()
    private val noiseBaselineX = mutableListOf<Float>()
    private val noiseBaselineY = mutableListOf<Float>()
    private val noiseBaselineZ = mutableListOf<Float>()
    private var baselineCollected = false
    private val BASELINE_DURATION_MS = 5000L
    private var baselineVector = FloatArray(3)
    private var maxVibrX = 0f
    private var maxVibrY = 0f
    private var maxVibrZ = 0f
    private var baselineNoiseRms = 0f
    private var forwardLearningStartTime = 0L
    private var forwardReferenceUnit: FloatArray? = null
    private var forwardRejectedDirectionSamples = 0
    private var forwardDotAccumulator = 0f
    private var forwardDotCount = 0
    private var forwardMagnitudeAccumulator = 0f
    private var forwardMagnitudeSquaredAccumulator = 0f
    private val FORWARD_PHASE_TIMEOUT_MS = 5000L
    private val FORWARD_DIRECTION_MIN_COS = 0.75f
    
    private var profileId: Long = -1L
    private var isFirstProfile: Boolean = false
    private var isNewProfile: Boolean = false
    private var isFirstLaunch: Boolean = false
    private var calibrationCompleted: Boolean = false  // Flag за успешна калибрация
    private var calibrationDeferred: Boolean = false
    private var profileDeleted: Boolean = false  // Flag за изтрит профил (за да избегнем двойно изтриване)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drag_calibration)
        applySystemBarsPaddingToRoot()

        window.statusBarColor = ContextCompat.getColor(this, R.color.background_primary)
        
        // Вземаме profileId от intent
        profileId = intent.getLongExtra("PROFILE_ID", -1L)
        isFirstProfile = intent.getBooleanExtra("IS_FIRST_PROFILE", false)
        isNewProfile = intent.getBooleanExtra("IS_NEW_PROFILE", false)
        isFirstLaunch = intent.getBooleanExtra("IS_FIRST_LAUNCH", false)
        
        // Зареждаме калибрацията за този профил
        DragCalibration.setProfile(profileId)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Calibration"
        supportActionBar?.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, R.color.background_primary)))
        
        // Ако е първи профил или нов профил от Garage - скриваме Back бутона
        if (isFirstProfile || isNewProfile) {
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
        }
        
        initializeViews()
        initializeSensors()
        updateUI()
    }
    
    private fun initializeViews() {
        tvPortraitStatus = findViewById(R.id.tvPortraitStatus)
        tvPortraitDate = findViewById(R.id.tvPortraitDate)
        btnCalibratePortrait = findViewById(R.id.btnCalibratePortrait)
        btnClearPortrait = findViewById(R.id.btnClearPortrait)
        
        tvLandscapeStatus = findViewById(R.id.tvLandscapeStatus)
        tvLandscapeDate = findViewById(R.id.tvLandscapeDate)
        btnCalibrateLandscape = findViewById(R.id.btnCalibrateLandscape)
        btnClearLandscape = findViewById(R.id.btnClearLandscape)
        
        btnClearAll = findViewById(R.id.btnClearAll)
        btnCancel = findViewById(R.id.btnCancel)
        btnCalibrateLater = findViewById(R.id.btnCalibrateLater)
        btnContinue = findViewById(R.id.btnContinue)
        
        // Показваме името на профила в title
        val profiles = ProfileStorage.loadProfiles(this)
        val profileName = profiles.find { it.id == profileId }?.name ?: "Unknown"
        supportActionBar?.title = "${getString(R.string.calibration_title)} - $profileName"
        
        btnCalibratePortrait.setOnClickListener {
            startCalibration("portrait")
        }
        
        btnClearPortrait.setOnClickListener {
            DragCalibration.clearOrientation(false)
            updateUI()
            // Принудително обновяване на Continue бутона след изтриване
            if (isFirstProfile || isNewProfile) {
                btnContinue.isEnabled = DragCalibration.hasAnyCalibration()
            }
        }
        
        btnCalibrateLandscape.setOnClickListener {
            startCalibration("landscape")
        }
        
        btnClearLandscape.setOnClickListener {
            DragCalibration.clearOrientation(true)
            updateUI()
            // Принудително обновяване на Continue бутона след изтриване
            if (isFirstProfile || isNewProfile) {
                btnContinue.isEnabled = DragCalibration.hasAnyCalibration()
            }
        }
        
        // Cancel бутон - изтрива профила и връща назад
        btnCancel.setOnClickListener {
            if (isFirstProfile || isNewProfile) {
                // Изтриваме профила ако няма калибрация
                if (!DragCalibration.hasAnyCalibration() && !profileDeleted) {
                    deleteProfileIfNoCalibration()
                    profileDeleted = true
                }
            }
            finish()
        }

        btnCalibrateLater.setOnClickListener {
            deferCalibrationForLater()
        }
        
        // Continue бутон - продължава напред
        btnContinue.setOnClickListener {
            if (DragCalibration.hasAnyCalibration()) {
                finishCalibration()
            }
        }
        
        // Clear All бутон - само за настройки
        btnClearAll.setOnClickListener {
            DragCalibration.clearCalibration()
            updateUI()
        }
    }
    
    private fun initializeSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    
    private fun updateUI() {
        if (DragCalibration.hasAnyCalibration()) {
            CalibrationReminderStore.clearDragCalibrationDeferred(this, profileId)
        }

        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        
        // Portrait UI
        if (DragCalibration.isPortraitCalibrated) {
            tvPortraitStatus.text = "✅ ${getString(R.string.calibration_status_calibrated)}"
            tvPortraitStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            tvPortraitDate.text = getString(R.string.calibration_date_format, dateFormat.format(DragCalibration.portraitCalibrationTime))
            btnCalibratePortrait.text = getString(R.string.calibration_btn_recalibrate)
            btnClearPortrait.isEnabled = true
        } else {
            tvPortraitStatus.text = "⚠️ ${getString(R.string.calibration_status_not_calibrated)}"
            tvPortraitStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            tvPortraitDate.text = getString(R.string.calibration_please_portrait)
            btnCalibratePortrait.text = getString(R.string.calibration_btn_calibrate)
            btnClearPortrait.isEnabled = false
        }
        
        // Landscape UI
        if (DragCalibration.isLandscapeCalibrated) {
            tvLandscapeStatus.text = "✅ ${getString(R.string.calibration_status_calibrated)}"
            tvLandscapeStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            tvLandscapeDate.text = getString(R.string.calibration_date_format, dateFormat.format(DragCalibration.landscapeCalibrationTime))
            btnCalibrateLandscape.text = getString(R.string.calibration_btn_recalibrate)
            btnClearLandscape.isEnabled = true
        } else {
            tvLandscapeStatus.text = "⚠️ ${getString(R.string.calibration_status_not_calibrated)}"
            tvLandscapeStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            tvLandscapeDate.text = getString(R.string.calibration_please_landscape)
            btnCalibrateLandscape.text = getString(R.string.calibration_btn_calibrate)
            btnClearLandscape.isEnabled = false
        }
        
        // Button visibility and state logic
        if (isFirstProfile || isNewProfile) {
            // Първи профил или нов профил от Garage - показваме Continue бутона
            // Cancel бутонът се показва САМО за нов профил от Garage, НЕ за първи профил
            btnCancel.visibility = if (isFirstProfile) android.view.View.GONE else android.view.View.VISIBLE
            btnCalibrateLater.visibility = if (isFirstProfile && !DragCalibration.hasAnyCalibration()) android.view.View.VISIBLE else android.view.View.GONE
            btnContinue.visibility = android.view.View.VISIBLE
            btnClearAll.visibility = android.view.View.GONE
            
            // Continue бутонът е активен само ако има поне една калибрация
            btnContinue.isEnabled = DragCalibration.hasAnyCalibration()
        } else {
            // От настройки - показваме само Clear All бутона
            btnCancel.visibility = android.view.View.GONE
            btnCalibrateLater.visibility = android.view.View.GONE
            btnContinue.visibility = android.view.View.GONE
            btnClearAll.visibility = android.view.View.VISIBLE
            btnClearAll.text = getString(R.string.calibration_btn_clear_all)
            btnClearAll.isEnabled = DragCalibration.hasAnyCalibration()
            btnClearAll.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
        }
    }
    
    private fun startCalibration(orientation: String) {
        calibratingOrientation = orientation
        isCalibrating = true
        calibrationSamples.clear()
        noiseBaselineX.clear()
        noiseBaselineY.clear()
        noiseBaselineZ.clear()
        
        // 🔥 ВАЖНО: Изчистваме forward samples!
        forwardSamplesX.clear()
        forwardSamplesY.clear()
        forwardSamplesZ.clear()
        forwardReferenceUnit = null
        forwardRejectedDirectionSamples = 0
        forwardDotAccumulator = 0f
        forwardDotCount = 0
        forwardMagnitudeAccumulator = 0f
        forwardMagnitudeSquaredAccumulator = 0f
        
        baselineCollected = false
        isLearningForward = false
        baselineVector = FloatArray(3)
        maxVibrX = 0f
        maxVibrY = 0f
        maxVibrZ = 0f
        baselineNoiseRms = 0f
        calibrationStartTime = System.currentTimeMillis()
        
        val statusView = if (orientation == "portrait") tvPortraitStatus else tvLandscapeStatus
        statusView.text = "📱 ${getString(R.string.calibration_hold_steady)}"
        statusView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
        
        // ВАЖНО: Регистрираме sensor ПЪРВО, преди timer-а
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        
        // ИЗЧАКВАМЕ 200ms за sensor warm-up, СЛЕД ТОВА стартираме 5-секунден timer
        statusView.postDelayed({
            calibrationStartTime = System.currentTimeMillis() // Рестартираме времето СЛЕД sensor warm-up
            
            // След 5 секунди събиране на baseline
            statusView.postDelayed({
                if (isCalibrating && !baselineCollected) {
                    completeBaseline()
                    startLearningForward()
                }
            }, BASELINE_DURATION_MS)
        }, 200L) // 200ms sensor warm-up
    }
    
    private fun completeBaseline() {
        // ПРОВЕРКА: Минимум 50 samples за надеждна калибрация (при ~50Hz = 1 секунда данни)
        val MIN_BASELINE_SAMPLES = 50
        
        if (noiseBaselineX.size < MIN_BASELINE_SAMPLES) {
            // НЕДОСТАТЪЧНО ДАННИ - продължаваме да чакаме!
            Log.w("DragCalibration", "⚠️ Insufficient baseline samples: ${noiseBaselineX.size}/$MIN_BASELINE_SAMPLES - extending collection")
            
            // Удължаваме събирането с още 2 секунди
            val statusView = if (calibratingOrientation == "portrait") tvPortraitStatus else tvLandscapeStatus
            statusView.postDelayed({
                if (isCalibrating && !baselineCollected) {
                    completeBaseline() // Опитваме отново
                    if (baselineCollected) {
                        startLearningForward()
                    }
                }
            }, 2000L)
            return
        }
        
        // ДОСТАТЪЧНО ДАННИ - изчисляваме baseline
        // Изчисляваме СРЕДНИЯ ВЕКТОР от всички семпли
        baselineVector = floatArrayOf(
            noiseBaselineX.average().toFloat(),
            noiseBaselineY.average().toFloat(),
            noiseBaselineZ.average().toFloat()
        )
        
        // Намираме МАКСИМАЛНАТА вибрация ПО ВСЯКА ОС (абсолютна стойност на отклонението)
        maxVibrX = 0f
        maxVibrY = 0f
        maxVibrZ = 0f
        var baselineNoiseEnergy = 0f
        for (i in noiseBaselineX.indices) {
            val dx = abs(noiseBaselineX[i] - baselineVector[0])
            val dy = abs(noiseBaselineY[i] - baselineVector[1])
            val dz = abs(noiseBaselineZ[i] - baselineVector[2])
            baselineNoiseEnergy += dx * dx + dy * dy + dz * dz
            
            if (dx > maxVibrX) maxVibrX = dx
            if (dy > maxVibrY) maxVibrY = dy
            if (dz > maxVibrZ) maxVibrZ = dz
        }
        baselineNoiseRms = sqrt((baselineNoiseEnergy / noiseBaselineX.size.coerceAtLeast(1)).coerceAtLeast(0f))
        
        baselineCollected = true
        Log.d("DragCalibration", "✅ Baseline collected: ${noiseBaselineX.size} samples")
        Log.d("DragCalibration", "   Average baseline vector: [${baselineVector[0]}, ${baselineVector[1]}, ${baselineVector[2]}]")
        Log.d("DragCalibration", "   Max vibrations per axis: X=${"%.3f".format(maxVibrX)}, Y=${"%.3f".format(maxVibrY)}, Z=${"%.3f".format(maxVibrZ)} m/s²")
        Log.d("DragCalibration", "   Baseline RMS noise: ${"%.3f".format(baselineNoiseRms)} m/s²")
    }
    
    private fun collectNoiseBaseline() {
        // Записваме RAW векторите (gravity + шум когато сме неподвижни)
        noiseBaselineX.add(rawAcceleration[0])
        noiseBaselineY.add(rawAcceleration[1])
        noiseBaselineZ.add(rawAcceleration[2])
    }
    
    private fun startLearningForward() {
        isLearningForward = true
        forwardLearningStartTime = System.currentTimeMillis()
        forwardReferenceUnit = null
        forwardRejectedDirectionSamples = 0
        forwardDotAccumulator = 0f
        forwardDotCount = 0
        forwardMagnitudeAccumulator = 0f
        forwardMagnitudeSquaredAccumulator = 0f
        val statusView = if (calibratingOrientation == "portrait") tvPortraitStatus else tvLandscapeStatus
        statusView.text = "🚗 ${getString(R.string.calibration_drive_forward)}"
        statusView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // Записваме RAW данни (gravity + acceleration + шум)
            rawAcceleration[0] = event.values[0]
            rawAcceleration[1] = event.values[1]
            rawAcceleration[2] = event.values[2]
            
            // Първи 5 секунди - събираме baseline (gravity + шум)
            if (isCalibrating && !baselineCollected) {
                collectNoiseBaseline()
            }
            // След baseline - детектираме forward ускорение
            else if (isLearningForward) {
                detectForwardAcceleration()
            }
        }
    }
    
    private fun detectForwardAcceleration() {
        if (System.currentTimeMillis() - forwardLearningStartTime > FORWARD_PHASE_TIMEOUT_MS) {
            forwardSamplesX.clear()
            forwardSamplesY.clear()
            forwardSamplesZ.clear()
            forwardReferenceUnit = null
            forwardRejectedDirectionSamples = 0
            forwardDotAccumulator = 0f
            forwardDotCount = 0
            forwardMagnitudeAccumulator = 0f
            forwardMagnitudeSquaredAccumulator = 0f
            forwardLearningStartTime = System.currentTimeMillis()
            Log.w("DragCalibration", "⏱️ Forward phase timeout - restarting sample collection")
            return
        }

        // ИЗВАЖДАМЕ BASELINE (gravity + шум) от RAW данните
        val cleanAccel = floatArrayOf(
            rawAcceleration[0] - baselineVector[0],
            rawAcceleration[1] - baselineVector[1],
            rawAcceleration[2] - baselineVector[2]
        )
        
        // Изчисляваме magnitude на ПЪЛНИЯ 3D вектор
        val cleanMagnitude = sqrt(
            cleanAccel[0] * cleanAccel[0] + 
            cleanAccel[1] * cleanAccel[1] + 
            cleanAccel[2] * cleanAccel[2]
        )
        
        val forwardThreshold = DragCalibration.getCalibrationWeightedThreshold(
            linearAccel = cleanAccel,
            maxVibrX = maxVibrX,
            maxVibrY = maxVibrY,
            maxVibrZ = maxVibrZ,
            minFloor = 0.6f
        )
        
        // Игнорираме много малки стойности (под шума)
        if (cleanMagnitude < 0.05f) return
        
        // 🔥 СЪБИРАМЕ FORWARD SAMPLES (не записваме веднага!)
        // Детектираме ускорение НАД вибрациите
        if (cleanMagnitude > forwardThreshold) {
            val unitX = cleanAccel[0] / cleanMagnitude
            val unitY = cleanAccel[1] / cleanMagnitude
            val unitZ = cleanAccel[2] / cleanMagnitude

            val reference = forwardReferenceUnit
            if (reference == null) {
                forwardReferenceUnit = floatArrayOf(unitX, unitY, unitZ)
                forwardDotAccumulator += 1f
                forwardDotCount++
            } else {
                val cosine = unitX * reference[0] + unitY * reference[1] + unitZ * reference[2]
                if (cosine < FORWARD_DIRECTION_MIN_COS) {
                    forwardRejectedDirectionSamples++
                    if (forwardRejectedDirectionSamples % 5 == 0) {
                        Log.d(
                            "DragCalibration",
                            "↩️ Rejected off-direction sample: cos=${"%.3f".format(cosine)} (< ${"%.2f".format(FORWARD_DIRECTION_MIN_COS)})"
                        )
                    }
                    return
                }
                forwardDotAccumulator += cosine
                forwardDotCount++
            }

            // Добавяме sample
            forwardSamplesX.add(cleanAccel[0])
            forwardSamplesY.add(cleanAccel[1])
            forwardSamplesZ.add(cleanAccel[2])
            forwardMagnitudeAccumulator += cleanMagnitude
            forwardMagnitudeSquaredAccumulator += cleanMagnitude * cleanMagnitude
            
            if (forwardSamplesX.size % 5 == 0) {  // Лог на всеки 5 samples
                Log.d("DragCalibration", "📊 Forward sample #${forwardSamplesX.size}/$FORWARD_SAMPLES_NEEDED: magnitude=${"%.3f".format(cleanMagnitude)} (threshold=${"%.3f".format(forwardThreshold)})")
            }
            
            // 🎯 КОГАТО ИМАМЕ ДОСТАТЪЧНО SAMPLES → ЗАВЪРШВАМЕ!
            if (forwardSamplesX.size >= FORWARD_SAMPLES_NEEDED) {
                completeForwardPhase()
            }
            return
        }
        
        // 🔍 DEBUG: Ако не мина threshold
        if (forwardSamplesX.isEmpty() && cleanMagnitude > 0.1f) {
            Log.d("DragCalibration", "⚠️ Below threshold: magnitude=${"%.3f".format(cleanMagnitude)}, threshold=${"%.3f".format(forwardThreshold)}")
        }
    }
    
    private fun completeForwardPhase() {
        if (forwardSamplesX.isEmpty()) {
            Log.w("DragCalibration", "⚠️ Forward phase completed with zero samples - abort")
            return
        }

        // Изчисляваме средния forward vector от всички samples
        val avgForwardX = forwardSamplesX.average().toFloat()
        val avgForwardY = forwardSamplesY.average().toFloat()
        val avgForwardZ = forwardSamplesZ.average().toFloat()
        
        val magnitude = sqrt(avgForwardX * avgForwardX + avgForwardY * avgForwardY + avgForwardZ * avgForwardZ)
        
        // Нормализираме
        val forwardAxis = floatArrayOf(
            avgForwardX / magnitude,
            avgForwardY / magnitude,
            avgForwardZ / magnitude
        )

        val acceptedSamples = forwardSamplesX.size
        val attemptedSamples = acceptedSamples + forwardRejectedDirectionSamples
        val acceptRatio = if (attemptedSamples > 0) acceptedSamples.toFloat() / attemptedSamples.toFloat() else 0f
        val meanDot = if (forwardDotCount > 0) forwardDotAccumulator / forwardDotCount else 0f
        val meanMag = if (acceptedSamples > 0) forwardMagnitudeAccumulator / acceptedSamples else 0f
        val varianceMag = if (acceptedSamples > 0) {
            (forwardMagnitudeSquaredAccumulator / acceptedSamples) - (meanMag * meanMag)
        } else {
            0f
        }
        val stdMag = sqrt(max(0f, varianceMag))

        // Прост confidence score за quality на calibration (0..100)
        val coherenceScore = ((meanDot + 1f) / 2f).coerceIn(0f, 1f)
        val noiseScore = (1f - (baselineNoiseRms / 1.6f)).coerceIn(0f, 1f)
        val stabilityScore = (1f - (stdMag / max(0.3f, meanMag))).coerceIn(0f, 1f)
        val confidence = ((0.45f * coherenceScore) + (0.25f * acceptRatio) + (0.15f * noiseScore) + (0.15f * stabilityScore)) * 100f
        
        Log.d("DragCalibration", "✅ Forward phase COMPLETE!")
        Log.d("DragCalibration", "📊 Collected ${forwardSamplesX.size} samples over ~${FORWARD_SAMPLES_NEEDED * 10}ms")
        Log.d("DragCalibration", "📊 Rejected off-direction samples: $forwardRejectedDirectionSamples")
        Log.d("DragCalibration", "📍 Average Forward Vector: [${forwardAxis[0]}, ${forwardAxis[1]}, ${forwardAxis[2]}]")
        Log.d("DragCalibration", "📍 Magnitude: ${"%.3f".format(magnitude)} m/s²")
        Log.d("DragCalibration", "📍 Coherence: ${"%.3f".format(meanDot)}, acceptRatio=${"%.3f".format(acceptRatio)}")
        Log.d("DragCalibration", "📍 Confidence: ${"%.1f".format(confidence.coerceIn(0f, 100f))}%")

        // Lateral axis не ни трябва
        val dummyLateralAxis = floatArrayOf(0f, 1f, 0f)
        
        // Lock axes and save based on orientation!
        if (calibratingOrientation == "portrait") {
            DragCalibration.lockPortraitAxes(
                forward = forwardAxis,
                lateral = dummyLateralAxis,
                baseline = baselineVector,
                maxVibrX = maxVibrX,
                maxVibrY = maxVibrY,
                maxVibrZ = maxVibrZ,
                confidence = confidence.coerceIn(0f, 100f)
            )
        } else {
            DragCalibration.lockLandscapeAxes(
                forward = forwardAxis,
                lateral = dummyLateralAxis,
                baseline = baselineVector,
                maxVibrX = maxVibrX,
                maxVibrY = maxVibrY,
                maxVibrZ = maxVibrZ,
                confidence = confidence.coerceIn(0f, 100f)
            )
        }
        
        isCalibrating = false
        isLearningForward = false
        sensorManager.unregisterListener(this)
        
        val statusView = if (calibratingOrientation == "portrait") tvPortraitStatus else tvLandscapeStatus
        statusView.text = "✅ ${getString(R.string.calibration_success)}"
        statusView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        
        // ВАЖНО: Обновяваме UI веднага след калибрацията
        CalibrationReminderStore.clearDragCalibrationDeferred(this, profileId)
        updateUI()
        
        // Маркираме че калибрацията е завършена успешно
        calibrationCompleted = true
        
        // Допълнително принудително обновяване на Continue бутона за първи профил или нов профил на главния thread
        // Използваме post() за да гарантираме че UI-то се обновява правилно
        statusView.post {
            if ((isFirstProfile || isNewProfile) && DragCalibration.hasAnyCalibration()) {
                btnContinue.isEnabled = true
                // Принудително обновяване на видимостта
                btnContinue.invalidate()
                btnContinue.requestLayout()
            }
        }
        
        val successMessage = if (calibratingOrientation == "portrait") {
            getString(R.string.calibration_portrait_success)
        } else {
            getString(R.string.calibration_landscape_success)
        }
        Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show()
        
        // ВАЖНО: След калибрация редиректваме според контекста
        // Ако е първи профил или нов профил от Garage - НЕ прехвърляме автоматично, показваме бутон "ПРОДЪЛЖИ"
        // Бутонът "ПРОДЪЛЖИ" ще се появи в updateUI()
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    private fun deferCalibrationForLater() {
        CalibrationReminderStore.markDragCalibrationDeferred(this, profileId)
        calibrationDeferred = true

        startActivity(Intent(this, MainContainerActivity::class.java).apply {
            putExtra(MainContainerActivity.EXTRA_NAV_ITEM_ID, R.id.navMap)
        })
        finish()
    }
    
    private fun finishCalibration() {
        CalibrationReminderStore.clearDragCalibrationDeferred(this, profileId)
        when {
            isFirstProfile -> {
                // Първи профил - отиваме в главното app
                startActivity(Intent(this, MainContainerActivity::class.java).apply {
                    putExtra(MainContainerActivity.EXTRA_NAV_ITEM_ID, R.id.navMap)
                })
                finish()
            }
            isNewProfile -> {
                // Нов профил от Garage - връщаме се в Garage
                finish() // Просто затваряме activity-то, Garage е зад него
            }
            else -> {
                // От Settings - връщаме се
                finish()
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        handleBackPress()
        return true
    }
    
    override fun onBackPressed() {
        handleBackPress()
    }
    
    private fun handleBackPress() {
        if (isFirstProfile || isNewProfile) {
            // Ако е първи профил или нов профил от Garage и НЯМА НИКАКВА калибрация - показваме само съобщението
            // НЕ връщаме назад, оставаме на същата страница
            if (!DragCalibration.hasAnyCalibration()) {
                Toast.makeText(this, getString(R.string.calibration_need_one), Toast.LENGTH_LONG).show()
                return  // НЕ затваряме activity-то
            }
        }
        // Ако има калибрация или не е първи/нов профил - нормално затваряне
        // НО ако е първо влизане - не затваряме, само показваме съобщението
        if (isFirstLaunch) {
            if (!DragCalibration.hasAnyCalibration()) {
                Toast.makeText(this, getString(R.string.calibration_need_one), Toast.LENGTH_LONG).show()
            }
            return  // НЕ затваряме activity-то при първо влизане
        }
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        
        // Ако е първи профил или нов профил от Garage и няма калибрация - изтриваме профила
        // Проверяваме дали вече не сме изтрили профила (например в handleBackPress)
        if ((isFirstProfile || isNewProfile) && !calibrationCompleted && !calibrationDeferred && !DragCalibration.hasAnyCalibration() && !profileDeleted) {
            deleteProfileIfNoCalibration()
            profileDeleted = true
        }
    }
    
    private fun deleteProfileIfNoCalibration() {
        if (profileDeleted) {
            return  // Вече сме изтрили профила
        }
        
        try {
            val profiles = ProfileStorage.loadProfiles(this)
            val profileToDelete = profiles.find { it.id == profileId }
            
            if (profileToDelete != null) {
                // Проверяваме дали е избран профил
                val selectedId = ProfileStorage.getSelectedProfileId(this)
                if (profileToDelete.id == selectedId) {
                    // Ако е избран, премахваме избора
                    ProfileStorage.saveSelectedProfile(this, -1L)
                }
                
                // Изтриваме профила
                profiles.remove(profileToDelete)
                ProfileStorage.saveProfiles(this, profiles)
                
                profileDeleted = true
                Log.d("DragCalibration", "🗑️ Изтрит профил без калибрация: ${profileToDelete.name} (ID: $profileId)")
            }
        } catch (e: Exception) {
            Log.e("DragCalibration", "❌ Грешка при изтриване на профил без калибрация", e)
        }
    }
}

