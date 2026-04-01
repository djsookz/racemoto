package com.example.clinometer

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import android.view.View
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
    private lateinit var btnContinue: Button
    
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    
    private val rawAcceleration = FloatArray(3)  // RAW accelerometer данни (gravity + acceleration)
    
    // UNIVERSAL GRAVITY-BASED CALIBRATION
    private var isCalibrating = false
    private var calibrationStartTime = 0L
    
    // Phase 1: GRAVITY + VIBRATION събиране (5 секунди на IDLE!)
    private val gravitySamplesX = mutableListOf<Float>()
    private val gravitySamplesY = mutableListOf<Float>()
    private val gravitySamplesZ = mutableListOf<Float>()
    private val vibrationMagnitudes = mutableListOf<Float>()  // Linear accel magnitude (за MAX вибрация!)
    private val GRAVITY_DURATION_MS = 5000L  // 5 секунди за gravity + вибрации
    
    // Phase 2: FORWARD събиране (20 samples = ~200ms за бърз и стабилен vector!)
    private val forwardSamplesX = mutableListOf<Float>()
    private val forwardSamplesY = mutableListOf<Float>()
    private val forwardSamplesZ = mutableListOf<Float>()
    private val FORWARD_SAMPLES_NEEDED = 20  // 20 samples @ 100Hz = 200ms (баланс между скорост и точност!)
    private var gravityVector = FloatArray(3)  // От phase 1
    private var maxVibration = 0f  // MAX вибрация от phase 1
    
    // LOW-PASS filter за gravity
    private var lowPassX = 0f
    private var lowPassY = 0f
    private var lowPassZ = 0f
    private val LOW_PASS_ALPHA = 0.05f  // Силно заглаждане
    
    enum class CalibrationPhase {
        IDLE,
        COLLECTING_GRAVITY,  // 5 sec: gravity + vibrations
        WAITING_FOR_FORWARD,  // Чакаме реално бутане
        COLLECTING_FORWARD,  // 20 samples: forward посока
        COMPLETE
    }
    
    private var calibrationPhase = CalibrationPhase.IDLE

    // MOTO LEAN калибрация — открива forward axis от lean наляво (акселерометър)
    private var isMotoProfile = false
    private var gyroscope: android.hardware.Sensor? = null  // запазен за бъдеща употреба
    private var leanPhaseActive = false
    private val leanAccumGravity = FloatArray(3)  // smoothed accel по време на lean
    private var leanHoldCount = 0                  // брой семпли стабилно над прага
    private val LEAN_HOLD_SAMPLES = 60             // 0.6s @ ~100Hz → авто-фиксиране
    private val LEAN_TRIGGER_DEG = 25f             // градуса за авто-тригер
    private lateinit var tvPortraitLeanAngle: TextView
    private lateinit var tvLandscapeLeanAngle: TextView

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
    private val VIBRATION_BUFFER = 0.05f

    private var profileId: Long = -1L
    private var isFirstProfile: Boolean = false
    private var isNewProfile: Boolean = false
    private var isFirstLaunch: Boolean = false
    private var calibrationCompleted: Boolean = false  // Flag за успешна калибрация
    private var profileDeleted: Boolean = false  // Flag за изтрит профил (за да избегнем двойно изтриване)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drag_calibration)
        applySystemBarsPaddingToRoot()

        window.statusBarColor = ContextCompat.getColor(this, R.color.dark_background)
        
        // Вземаме profileId от intent
        profileId = intent.getLongExtra("PROFILE_ID", -1L)
        isFirstProfile = intent.getBooleanExtra("IS_FIRST_PROFILE", false)
        isNewProfile = intent.getBooleanExtra("IS_NEW_PROFILE", false)
        isFirstLaunch = intent.getBooleanExtra("IS_FIRST_LAUNCH", false)
        
        // Зареждаме калибрацията за този профил
        DragCalibration.setProfile(profileId)

        // Проверяваме дали профилът е мотоциклет
        val profiles = ProfileStorage.loadProfiles(this)
        isMotoProfile = profiles.find { it.id == profileId }?.vehicleType == Profile.VehicleType.MOTORCYCLE

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Calibration"
        supportActionBar?.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, R.color.dark_background)))
        
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

        tvPortraitLeanAngle = findViewById(R.id.tvPortraitLeanAngle)
        tvLandscapeLeanAngle = findViewById(R.id.tvLandscapeLeanAngle)
        
        btnClearAll = findViewById(R.id.btnClearAll)
        btnCancel = findViewById(R.id.btnCancel)
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
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }
    
    private fun updateUI() {
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
            btnContinue.visibility = android.view.View.VISIBLE
            btnClearAll.visibility = android.view.View.GONE
            
            // Continue бутонът е активен само ако има поне една калибрация
            btnContinue.isEnabled = DragCalibration.hasAnyCalibration()
        } else {
            // От настройки - показваме само Clear All бутона
            btnCancel.visibility = android.view.View.GONE
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
                    startNextPhaseAfterBaseline()
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
                        startNextPhaseAfterBaseline()
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
    
    private fun startNextPhaseAfterBaseline() {
        if (isMotoProfile) startLearningLean() else startLearningForward()
    }

    private fun startLearningForward() {
        isLearningForward = true
        val statusView = if (calibratingOrientation == "portrait") tvPortraitStatus else tvLandscapeStatus
        statusView.text = "🚗 ${getString(R.string.calibration_drive_forward)}"
        statusView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
    }

    private fun startLearningLean() {
        leanPhaseActive = true
        leanHoldCount = 0
        // Инициализираме smooth buffer с baseline (интелигентно начало)
        leanAccumGravity[0] = baselineVector[0]
        leanAccumGravity[1] = baselineVector[1]
        leanAccumGravity[2] = baselineVector[2]

        val statusView = if (calibratingOrientation == "portrait") tvPortraitStatus else tvLandscapeStatus
        val angleView = if (calibratingOrientation == "portrait") tvPortraitLeanAngle else tvLandscapeLeanAngle

        statusView.text = "🏍️ Lean LEFT slowly"
        statusView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
        angleView.text = "0°"
        angleView.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        angleView.visibility = View.VISIBLE
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // Записваме RAW данни (gravity + acceleration + шум)
                rawAcceleration[0] = event.values[0]
                rawAcceleration[1] = event.values[1]
                rawAcceleration[2] = event.values[2]

                // Първи 5 секунди - събираме baseline (gravity + шум)
                if (isCalibrating && !baselineCollected) {
                    collectNoiseBaseline()
                }
                // Мото: live lean ъгъл от акселерометъра
                else if (leanPhaseActive) {
                    updateLeanAngle(event.values[0], event.values[1], event.values[2])
                }
                // След baseline (само за кола) — детектираме forward ускорение
                else if (isLearningForward) {
                    detectForwardAcceleration()
                }
            }
            Sensor.TYPE_GYROSCOPE -> { /* не се използва при lean калибрация */ }
        }
    }

    private fun updateLeanAngle(ax: Float, ay: Float, az: Float) {
        // Плавно следим наклона — леановете са бавни, alpha = 0.75 дава бърза реакция
        val alpha = 0.75f
        leanAccumGravity[0] = alpha * leanAccumGravity[0] + (1f - alpha) * ax
        leanAccumGravity[1] = alpha * leanAccumGravity[1] + (1f - alpha) * ay
        leanAccumGravity[2] = alpha * leanAccumGravity[2] + (1f - alpha) * az

        // Ъгъл между текущото и baseline положение
        val bMag = sqrt(baselineVector[0]*baselineVector[0] + baselineVector[1]*baselineVector[1] + baselineVector[2]*baselineVector[2])
        val cMag = sqrt(leanAccumGravity[0]*leanAccumGravity[0] + leanAccumGravity[1]*leanAccumGravity[1] + leanAccumGravity[2]*leanAccumGravity[2])
        if (bMag < 0.5f || cMag < 0.5f) return

        val dotNorm = ((leanAccumGravity[0]*baselineVector[0] + leanAccumGravity[1]*baselineVector[1] + leanAccumGravity[2]*baselineVector[2]) / (bMag * cMag)).coerceIn(-1f, 1f)
        val angleDeg = Math.toDegrees(acos(dotNorm).toDouble()).toFloat()

        val statusView = if (calibratingOrientation == "portrait") tvPortraitStatus else tvLandscapeStatus
        val angleView = if (calibratingOrientation == "portrait") tvPortraitLeanAngle else tvLandscapeLeanAngle

        when {
            angleDeg >= LEAN_TRIGGER_DEG -> {
                angleView.text = "${angleDeg.roundToInt()}° ✓"
                angleView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                statusView.text = "✅ HOLD!"
                statusView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                leanHoldCount++
                if (leanHoldCount >= LEAN_HOLD_SAMPLES) completeLeanPhaseFromAccel()
            }
            angleDeg >= 15f -> {
                angleView.text = "${angleDeg.roundToInt()}°"
                angleView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
                statusView.text = "🏍️ Keep leaning..."
                statusView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
                leanHoldCount = 0
            }
            else -> {
                angleView.text = "${angleDeg.roundToInt()}°"
                angleView.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                statusView.text = "🏍️ Lean LEFT →"
                statusView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
                leanHoldCount = 0
            }
        }
    }

    private fun completeLeanPhaseFromAccel() {
        leanPhaseActive = false

        // Forward axis = normalize(cross(baseline, leanedGravity))
        // И двата вектора са "нагоре" при различни ъгли на наклон.
        // Кръстосаното им произведение дава оста на въртене = forward посоката на мотора.
        val bx = baselineVector[0]; val by = baselineVector[1]; val bz = baselineVector[2]
        val lx = leanAccumGravity[0]; val ly = leanAccumGravity[1]; val lz = leanAccumGravity[2]

        val fx = by * lz - bz * ly
        val fy = bz * lx - bx * lz
        val fz = bx * ly - by * lx
        val fMag = sqrt(fx*fx + fy*fy + fz*fz)

        if (fMag < 0.01f) {
            // Деградиран случай — телефонът почти не е наклонен, рестартираме
            Log.w("DragCalibration", "Lean cross product degenerate (fMag=$fMag) - retry!")
            leanPhaseActive = true
            leanHoldCount = 0
            return
        }

        val forwardAxis = floatArrayOf(fx / fMag, fy / fMag, fz / fMag)
        Log.d("DragCalibration", "Moto lean COMPLETE! forward=[${forwardAxis[0]},${forwardAxis[1]},${forwardAxis[2]}]")

        val dummyLateral = floatArrayOf(0f, 1f, 0f)
        if (calibratingOrientation == "portrait") {
            DragCalibration.lockPortraitAxes(forwardAxis, dummyLateral, baselineVector, maxVibrX, maxVibrY, maxVibrZ)
        } else {
            DragCalibration.lockLandscapeAxes(forwardAxis, dummyLateral, baselineVector, maxVibrX, maxVibrY, maxVibrZ)
        }

        isCalibrating = false
        isLearningForward = false
        sensorManager.unregisterListener(this)

        val statusView = if (calibratingOrientation == "portrait") tvPortraitStatus else tvLandscapeStatus
        val angleView = if (calibratingOrientation == "portrait") tvPortraitLeanAngle else tvLandscapeLeanAngle
        angleView.visibility = View.GONE

        statusView.text = "✅ ${getString(R.string.calibration_success)}"
        statusView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        updateUI()
        calibrationCompleted = true
        statusView.post {
            if ((isFirstProfile || isNewProfile) && DragCalibration.hasAnyCalibration()) {
                btnContinue.isEnabled = true
                btnContinue.invalidate()
            }
        }
        val msg = if (calibratingOrientation == "portrait")
            getString(R.string.calibration_portrait_success)
        else
            getString(R.string.calibration_landscape_success)
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
    
    private fun detectForwardAcceleration() {
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
        
        // Максимална вибрация (от фаза 1)
        val maxVibrationBaseline = sqrt(maxVibrX * maxVibrX + maxVibrY * maxVibrY + maxVibrZ * maxVibrZ)
        
        // 🎯 THRESHOLD: Директно maxVibration (БЕЗ margin за максимална чувствителност!)
        val forwardThreshold = maxVibrationBaseline
        
        // Игнорираме много малки стойности (под шума)
        if (cleanMagnitude < 0.05f) return
        
        // 🔥 СЪБИРАМЕ FORWARD SAMPLES (не записваме веднага!)
        // Детектираме ускорение НАД вибрациите
        if (cleanMagnitude > forwardThreshold) {
            // Добавяме sample
            forwardSamplesX.add(cleanAccel[0])
            forwardSamplesY.add(cleanAccel[1])
            forwardSamplesZ.add(cleanAccel[2])
            
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
        
        Log.d("DragCalibration", "✅ Forward phase COMPLETE!")
        Log.d("DragCalibration", "📊 Collected ${forwardSamplesX.size} samples over ~${FORWARD_SAMPLES_NEEDED * 10}ms")
        Log.d("DragCalibration", "📍 Average Forward Vector: [${forwardAxis[0]}, ${forwardAxis[1]}, ${forwardAxis[2]}]")
        Log.d("DragCalibration", "📍 Magnitude: ${"%.3f".format(magnitude)} m/s²")

        // Lateral axis не ни трябва
        val dummyLateralAxis = floatArrayOf(0f, 1f, 0f)
        
        // Lock axes and save based on orientation!
        if (calibratingOrientation == "portrait") {
            DragCalibration.lockPortraitAxes(forwardAxis, dummyLateralAxis, baselineVector, maxVibrX, maxVibrY, maxVibrZ)
        } else {
            DragCalibration.lockLandscapeAxes(forwardAxis, dummyLateralAxis, baselineVector, maxVibrX, maxVibrY, maxVibrZ)
        }
        
        isCalibrating = false
        isLearningForward = false
        sensorManager.unregisterListener(this)
        
        val statusView = if (calibratingOrientation == "portrait") tvPortraitStatus else tvLandscapeStatus
        statusView.text = "✅ ${getString(R.string.calibration_success)}"
        statusView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        
        // ВАЖНО: Обновяваме UI веднага след калибрацията
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
    
    private fun finishCalibration() {
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
        if ((isFirstProfile || isNewProfile) && !calibrationCompleted && !DragCalibration.hasAnyCalibration() && !profileDeleted) {
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

