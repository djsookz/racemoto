package com.example.clinometer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

enum class CalibrationQualityLevel {
    NOT_CALIBRATED,
    BAD,
    WARNING,
    GOOD
}

data class CalibrationQualityReport(
    val level: CalibrationQualityLevel,
    val score: Int,
    val reasons: List<String>,
    val baselineSamples: Int,
    val forwardSamples: Int,
    val baselineNoiseRms: Float,
    val baselineMaxVibration: Float,
    val forwardMeanMagnitude: Float,
    val forwardConsistency: Float,
    val forwardToNoiseRatio: Float,
    val isStale: Boolean
)

/**
 * Singleton за запазване на калибрацията на forward/lateral оси.
 * Калибрацията се запазва ПО ПРОФИЛ в SharedPreferences.
 * Всеки профил (Kawasaki, Audi, и т.н.) има собствена калибрация.
 */
object DragCalibration {
    
    private const val PREFS_NAME = "DragCalibration"
    private const val DEFAULT_STALE_CALIBRATION_MS = 45L * 24L * 60L * 60L * 1000L
    
    private var prefs: SharedPreferences? = null
    private var currentProfileId: Long = -1L
    
    // UNIVERSAL GRAVITY-BASED CALIBRATION (работи с всяка ориентация!)
    @Volatile var gravityVector = floatArrayOf(0f, 9.8f, 0f) // DOWN вектор (от gravity sensor)
    @Volatile var forwardVector = floatArrayOf(1f, 0f, 0f) // FORWARD посока (от реално бутане)
    @Volatile var rightVector = floatArrayOf(0f, 0f, 1f) // RIGHT посока (изчислена)
    @Volatile var maxVibrationBaseline = 0.8f // MAX linear accel magnitude по време на IDLE (5 sec)
    @Volatile var maxVibrXUniversal = 0f // Максимална вибрация по X ос (за weighted threshold)
    @Volatile var maxVibrYUniversal = 0f // Максимална вибрация по Y ос (за weighted threshold)
    @Volatile var maxVibrZUniversal = 0f // Максимална вибрация по Z ос (за weighted threshold)
    @Volatile var isUniversalCalibrated = false
    @Volatile var universalCalibrationTime = 0L
    @Volatile var universalBaselineSamples = 0
    @Volatile var universalForwardSamples = 0
    @Volatile var universalBaselineNoiseRms = 0f
    @Volatile var universalForwardMeanMagnitude = 0f
    @Volatile var universalForwardConsistency = 0f
    @Volatile var universalForwardToNoiseRatio = 0f
    @Volatile var universalQualityScore = 0
    @Volatile var universalQualityLevelRaw = CalibrationQualityLevel.NOT_CALIBRATED.name
    @Volatile var universalQualityUpdatedAt = 0L
    
    // Portrait calibration - DEPRECATED (backward compatibility)
    @Volatile var forwardAxisPortrait = floatArrayOf(1f, 0f, 0f)
    @Volatile var lateralAxisPortrait = floatArrayOf(0f, 1f, 0f)
    @Volatile var baselinePortrait = floatArrayOf(0f, 0f, 0f) // Baseline шум от 5 секунди неподвижност
    @Volatile var noiseStdDevPortrait = 0f // Deprecated - запазено за backward compatibility
    @Volatile var maxVibrXPortrait = 0f // Максимална вибрация по X ос
    @Volatile var maxVibrYPortrait = 0f // Максимална вибрация по Y ос
    @Volatile var maxVibrZPortrait = 0f // Максимална вибрация по Z ос
    @Volatile var isPortraitCalibrated = false
    @Volatile var portraitCalibrationTime = 0L
    
    // Landscape calibration - ПЪЛЕН 3D вектор на ускорението
    @Volatile var forwardAxisLandscape = floatArrayOf(1f, 0f, 0f)
    @Volatile var lateralAxisLandscape = floatArrayOf(0f, 1f, 0f)
    @Volatile var baselineLandscape = floatArrayOf(0f, 0f, 0f) // Baseline шум от 5 секунди неподвижност
    @Volatile var noiseStdDevLandscape = 0f // Deprecated - запазено за backward compatibility
    @Volatile var maxVibrXLandscape = 0f // Максимална вибрация по X ос
    @Volatile var maxVibrYLandscape = 0f // Максимална вибрация по Y ос
    @Volatile var maxVibrZLandscape = 0f // Максимална вибрация по Z ос
    @Volatile var isLandscapeCalibrated = false
    @Volatile var landscapeCalibrationTime = 0L
    
    // Дали е в процес на учебно ускорение (само за DragCalibrationActivity)
    @Volatile var isLearningForward = false
    
    // Deprecated - за backward compatibility
    @Volatile var forwardAxis = floatArrayOf(1f, 0f, 0f)
    @Volatile var lateralAxis = floatArrayOf(0f, 1f, 0f)
    @Volatile var isCalibrated = false
    @Volatile var calibrationTime = 0L
    
    /**
     * Инициализира от SharedPreferences
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Бърза проверка дали профилът има калибрация (universal, portrait или landscape).
     */
    fun isProfileCalibrated(context: Context, profileId: Long): Boolean {
        if (prefs == null) {
            init(context)
        }
        val p = prefs ?: return false
        val keyPrefix = "profile_${profileId}_"
        return p.getBoolean(keyPrefix + "universal_isCalibrated", false) ||
            p.getBoolean(keyPrefix + "portrait_isCalibrated", false) ||
            p.getBoolean(keyPrefix + "landscape_isCalibrated", false)
    }
    
    /**
     * Сменя профила и зарежда неговата калибрация
     */
    fun setProfile(profileId: Long) {
        currentProfileId = profileId
        loadFromPrefs()
    }
    
    /**
     * Зарежда калибрацията за текущия профил от SharedPreferences
     */
    private fun loadFromPrefs() {
        if (currentProfileId == -1L) {
            Log.d("DragCalibration", "⚠️ No profile selected")
            return
        }
        
        prefs?.let { p ->
            val keyPrefix = "profile_${currentProfileId}_"
            
            // Load UNIVERSAL calibration (NEW!)
            isUniversalCalibrated = p.getBoolean(keyPrefix + "universal_isCalibrated", false)
            universalBaselineSamples = p.getInt(keyPrefix + "universal_baselineSamples", 0)
            universalForwardSamples = p.getInt(keyPrefix + "universal_forwardSamples", 0)
            universalBaselineNoiseRms = p.getFloat(keyPrefix + "universal_baselineNoiseRms", 0f)
            universalForwardMeanMagnitude = p.getFloat(keyPrefix + "universal_forwardMeanMagnitude", 0f)
            universalForwardConsistency = p.getFloat(keyPrefix + "universal_forwardConsistency", 0f)
            universalForwardToNoiseRatio = p.getFloat(keyPrefix + "universal_forwardToNoiseRatio", 0f)
            universalQualityScore = p.getInt(keyPrefix + "universal_qualityScore", 0)
            universalQualityLevelRaw = p.getString(
                keyPrefix + "universal_qualityLevel",
                CalibrationQualityLevel.NOT_CALIBRATED.name
            ) ?: CalibrationQualityLevel.NOT_CALIBRATED.name
            universalQualityUpdatedAt = p.getLong(keyPrefix + "universal_qualityUpdatedAt", 0L)
            if (isUniversalCalibrated) {
                gravityVector = floatArrayOf(
                    p.getFloat(keyPrefix + "universal_gravityX", 0f),
                    p.getFloat(keyPrefix + "universal_gravityY", 9.8f),
                    p.getFloat(keyPrefix + "universal_gravityZ", 0f)
                )
                forwardVector = floatArrayOf(
                    p.getFloat(keyPrefix + "universal_forwardX", 1f),
                    p.getFloat(keyPrefix + "universal_forwardY", 0f),
                    p.getFloat(keyPrefix + "universal_forwardZ", 0f)
                )
                rightVector = floatArrayOf(
                    p.getFloat(keyPrefix + "universal_rightX", 0f),
                    p.getFloat(keyPrefix + "universal_rightY", 0f),
                    p.getFloat(keyPrefix + "universal_rightZ", 1f)
                )
                maxVibrationBaseline = p.getFloat(keyPrefix + "universal_maxVibration", 0.8f)
                maxVibrXUniversal = p.getFloat(keyPrefix + "universal_maxVibrX", 0f)
                maxVibrYUniversal = p.getFloat(keyPrefix + "universal_maxVibrY", 0f)
                maxVibrZUniversal = p.getFloat(keyPrefix + "universal_maxVibrZ", 0f)
                universalCalibrationTime = p.getLong(keyPrefix + "universal_calibrationTime", 0L)
                
                Log.d("DragCalibration", "✅ UNIVERSAL calibration loaded at ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(universalCalibrationTime)}")
                Log.d("DragCalibration", "   Max vibrations per axis: X=${String.format("%.3f", maxVibrXUniversal)}, Y=${String.format("%.3f", maxVibrYUniversal)}, Z=${String.format("%.3f", maxVibrZUniversal)} m/s²")
                Log.d("DragCalibration", "   Gravity (DOWN): [${String.format("%.3f", gravityVector[0])}, ${String.format("%.3f", gravityVector[1])}, ${String.format("%.3f", gravityVector[2])}]")
                Log.d("DragCalibration", "   Forward: [${String.format("%.3f", forwardVector[0])}, ${String.format("%.3f", forwardVector[1])}, ${String.format("%.3f", forwardVector[2])}]")
                Log.d("DragCalibration", "   Right: [${String.format("%.3f", rightVector[0])}, ${String.format("%.3f", rightVector[1])}, ${String.format("%.3f", rightVector[2])}]")
                Log.d("DragCalibration", "   🔥 MAX вибрация (idle): ${String.format("%.2f", maxVibrationBaseline)} m/s²")
                Log.d("DragCalibration", "   🎯 ДИНАМИЧЕН праг: ${String.format("%.2f", maxVibrationBaseline * 1.5f)} m/s² (1.5× MAX вибрация)")
                Log.d("DragCalibration", "   📈 Quality: ${normalizeQualityLevel(universalQualityLevelRaw)} score=$universalQualityScore")
            } else {
                universalBaselineSamples = 0
                universalForwardSamples = 0
                universalBaselineNoiseRms = 0f
                universalForwardMeanMagnitude = 0f
                universalForwardConsistency = 0f
                universalForwardToNoiseRatio = 0f
                universalQualityScore = 0
                universalQualityLevelRaw = CalibrationQualityLevel.NOT_CALIBRATED.name
                universalQualityUpdatedAt = 0L
            }
            
            // Load Portrait calibration (DEPRECATED)
            isPortraitCalibrated = p.getBoolean(keyPrefix + "portrait_isCalibrated", false)
            if (isPortraitCalibrated) {
                forwardAxisPortrait = floatArrayOf(
                    p.getFloat(keyPrefix + "portrait_forwardX", 1f),
                    p.getFloat(keyPrefix + "portrait_forwardY", 0f),
                    p.getFloat(keyPrefix + "portrait_forwardZ", 0f)
                )
                lateralAxisPortrait = floatArrayOf(
                    p.getFloat(keyPrefix + "portrait_lateralX", 0f),
                    p.getFloat(keyPrefix + "portrait_lateralY", 1f),
                    p.getFloat(keyPrefix + "portrait_lateralZ", 0f)
                )
                baselinePortrait = floatArrayOf(
                    p.getFloat(keyPrefix + "portrait_baselineX", 0f),
                    p.getFloat(keyPrefix + "portrait_baselineY", 0f),
                    p.getFloat(keyPrefix + "portrait_baselineZ", 0f)
                )
                noiseStdDevPortrait = p.getFloat(keyPrefix + "portrait_noiseStdDev", 0f) // Backward compat
                maxVibrXPortrait = p.getFloat(keyPrefix + "portrait_maxVibrX", 0f)
                maxVibrYPortrait = p.getFloat(keyPrefix + "portrait_maxVibrY", 0f)
                maxVibrZPortrait = p.getFloat(keyPrefix + "portrait_maxVibrZ", 0f)
                portraitCalibrationTime = p.getLong(keyPrefix + "portrait_calibrationTime", 0L)
                Log.d("DragCalibration", "✅ Portrait calibrated at ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(portraitCalibrationTime)}")
                Log.d("DragCalibration", "   Baseline: [${baselinePortrait[0]}, ${baselinePortrait[1]}, ${baselinePortrait[2]}]")
                Log.d("DragCalibration", "   Max vibrations per axis: X=$maxVibrXPortrait, Y=$maxVibrYPortrait, Z=$maxVibrZPortrait m/s²")
            }
            
            // Load Landscape calibration
            isLandscapeCalibrated = p.getBoolean(keyPrefix + "landscape_isCalibrated", false)
            if (isLandscapeCalibrated) {
                forwardAxisLandscape = floatArrayOf(
                    p.getFloat(keyPrefix + "landscape_forwardX", 1f),
                    p.getFloat(keyPrefix + "landscape_forwardY", 0f),
                    p.getFloat(keyPrefix + "landscape_forwardZ", 0f)
                )
                lateralAxisLandscape = floatArrayOf(
                    p.getFloat(keyPrefix + "landscape_lateralX", 0f),
                    p.getFloat(keyPrefix + "landscape_lateralY", 1f),
                    p.getFloat(keyPrefix + "landscape_lateralZ", 0f)
                )
                baselineLandscape = floatArrayOf(
                    p.getFloat(keyPrefix + "landscape_baselineX", 0f),
                    p.getFloat(keyPrefix + "landscape_baselineY", 0f),
                    p.getFloat(keyPrefix + "landscape_baselineZ", 0f)
                )
                noiseStdDevLandscape = p.getFloat(keyPrefix + "landscape_noiseStdDev", 0f) // Backward compat
                maxVibrXLandscape = p.getFloat(keyPrefix + "landscape_maxVibrX", 0f)
                maxVibrYLandscape = p.getFloat(keyPrefix + "landscape_maxVibrY", 0f)
                maxVibrZLandscape = p.getFloat(keyPrefix + "landscape_maxVibrZ", 0f)
                landscapeCalibrationTime = p.getLong(keyPrefix + "landscape_calibrationTime", 0L)
                Log.d("DragCalibration", "✅ Landscape calibrated at ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(landscapeCalibrationTime)}")
                Log.d("DragCalibration", "   Baseline: [${baselineLandscape[0]}, ${baselineLandscape[1]}, ${baselineLandscape[2]}]")
                Log.d("DragCalibration", "   Max vibrations per axis: X=$maxVibrXLandscape, Y=$maxVibrYLandscape, Z=$maxVibrZLandscape m/s²")
            }
            
            // Backward compatibility - use portrait as default if exists
            if (isPortraitCalibrated) {
                forwardAxis = forwardAxisPortrait.clone()
                lateralAxis = lateralAxisPortrait.clone()
                isCalibrated = true
                calibrationTime = portraitCalibrationTime
            } else if (isLandscapeCalibrated) {
                forwardAxis = forwardAxisLandscape.clone()
                lateralAxis = lateralAxisLandscape.clone()
                isCalibrated = true
                calibrationTime = landscapeCalibrationTime
            } else {
                isCalibrated = false
                calibrationTime = 0L
                Log.d("DragCalibration", "⚠️ Profile $currentProfileId not calibrated")
            }
        }
    }
    
    /**
     * Запазва калибрацията за текущия профил в SharedPreferences
     */
    private fun saveToPrefs() {
        if (currentProfileId == -1L) {
            Log.d("DragCalibration", "⚠️ Cannot save - no profile selected")
            return
        }
        
        prefs?.edit()?.apply {
            val keyPrefix = "profile_${currentProfileId}_"
            
            // Save UNIVERSAL calibration (NEW!)
            putBoolean(keyPrefix + "universal_isCalibrated", isUniversalCalibrated)
            putFloat(keyPrefix + "universal_gravityX", gravityVector[0])
            putFloat(keyPrefix + "universal_gravityY", gravityVector[1])
            putFloat(keyPrefix + "universal_gravityZ", gravityVector[2])
            putFloat(keyPrefix + "universal_forwardX", forwardVector[0])
            putFloat(keyPrefix + "universal_forwardY", forwardVector[1])
            putFloat(keyPrefix + "universal_forwardZ", forwardVector[2])
            putFloat(keyPrefix + "universal_rightX", rightVector[0])
            putFloat(keyPrefix + "universal_rightY", rightVector[1])
            putFloat(keyPrefix + "universal_rightZ", rightVector[2])
            putFloat(keyPrefix + "universal_maxVibration", maxVibrationBaseline)
            putFloat(keyPrefix + "universal_maxVibrX", maxVibrXUniversal)
            putFloat(keyPrefix + "universal_maxVibrY", maxVibrYUniversal)
            putFloat(keyPrefix + "universal_maxVibrZ", maxVibrZUniversal)
            putLong(keyPrefix + "universal_calibrationTime", universalCalibrationTime)
            putInt(keyPrefix + "universal_baselineSamples", universalBaselineSamples)
            putInt(keyPrefix + "universal_forwardSamples", universalForwardSamples)
            putFloat(keyPrefix + "universal_baselineNoiseRms", universalBaselineNoiseRms)
            putFloat(keyPrefix + "universal_forwardMeanMagnitude", universalForwardMeanMagnitude)
            putFloat(keyPrefix + "universal_forwardConsistency", universalForwardConsistency)
            putFloat(keyPrefix + "universal_forwardToNoiseRatio", universalForwardToNoiseRatio)
            putInt(keyPrefix + "universal_qualityScore", universalQualityScore)
            putString(keyPrefix + "universal_qualityLevel", universalQualityLevelRaw)
            putLong(keyPrefix + "universal_qualityUpdatedAt", universalQualityUpdatedAt)
            
            // Save Portrait calibration (DEPRECATED)
            putBoolean(keyPrefix + "portrait_isCalibrated", isPortraitCalibrated)
            putFloat(keyPrefix + "portrait_forwardX", forwardAxisPortrait[0])
            putFloat(keyPrefix + "portrait_forwardY", forwardAxisPortrait[1])
            putFloat(keyPrefix + "portrait_forwardZ", forwardAxisPortrait[2])
            putFloat(keyPrefix + "portrait_lateralX", lateralAxisPortrait[0])
            putFloat(keyPrefix + "portrait_lateralY", lateralAxisPortrait[1])
            putFloat(keyPrefix + "portrait_lateralZ", lateralAxisPortrait[2])
            putFloat(keyPrefix + "portrait_baselineX", baselinePortrait[0])
            putFloat(keyPrefix + "portrait_baselineY", baselinePortrait[1])
            putFloat(keyPrefix + "portrait_baselineZ", baselinePortrait[2])
            putFloat(keyPrefix + "portrait_noiseStdDev", noiseStdDevPortrait) // Backward compat
            putFloat(keyPrefix + "portrait_maxVibrX", maxVibrXPortrait)
            putFloat(keyPrefix + "portrait_maxVibrY", maxVibrYPortrait)
            putFloat(keyPrefix + "portrait_maxVibrZ", maxVibrZPortrait)
            putLong(keyPrefix + "portrait_calibrationTime", portraitCalibrationTime)
            
            // Save Landscape calibration
            putBoolean(keyPrefix + "landscape_isCalibrated", isLandscapeCalibrated)
            putFloat(keyPrefix + "landscape_forwardX", forwardAxisLandscape[0])
            putFloat(keyPrefix + "landscape_forwardY", forwardAxisLandscape[1])
            putFloat(keyPrefix + "landscape_forwardZ", forwardAxisLandscape[2])
            putFloat(keyPrefix + "landscape_lateralX", lateralAxisLandscape[0])
            putFloat(keyPrefix + "landscape_lateralY", lateralAxisLandscape[1])
            putFloat(keyPrefix + "landscape_lateralZ", lateralAxisLandscape[2])
            putFloat(keyPrefix + "landscape_baselineX", baselineLandscape[0])
            putFloat(keyPrefix + "landscape_baselineY", baselineLandscape[1])
            putFloat(keyPrefix + "landscape_baselineZ", baselineLandscape[2])
            putFloat(keyPrefix + "landscape_noiseStdDev", noiseStdDevLandscape) // Backward compat
            putFloat(keyPrefix + "landscape_maxVibrX", maxVibrXLandscape)
            putFloat(keyPrefix + "landscape_maxVibrY", maxVibrYLandscape)
            putFloat(keyPrefix + "landscape_maxVibrZ", maxVibrZLandscape)
            putLong(keyPrefix + "landscape_calibrationTime", landscapeCalibrationTime)
            
            apply()
        }
        Log.d("DragCalibration", "💾 Saved calibration for profile $currentProfileId (Portrait: $isPortraitCalibrated, Landscape: $isLandscapeCalibrated)")
    }
    
    /**
     * Изчиства ВСИЧКИ калибрации (и Portrait, и Landscape)
     */
    fun clearCalibration() {
        // Clear UNIVERSAL calibration
        isUniversalCalibrated = false
        universalCalibrationTime = 0L
        gravityVector = floatArrayOf(0f, 9.8f, 0f)
        forwardVector = floatArrayOf(1f, 0f, 0f)
        rightVector = floatArrayOf(0f, 0f, 1f)
        maxVibrationBaseline = 0.8f
        maxVibrXUniversal = 0f
        maxVibrYUniversal = 0f
        maxVibrZUniversal = 0f
        universalBaselineSamples = 0
        universalForwardSamples = 0
        universalBaselineNoiseRms = 0f
        universalForwardMeanMagnitude = 0f
        universalForwardConsistency = 0f
        universalForwardToNoiseRatio = 0f
        universalQualityScore = 0
        universalQualityLevelRaw = CalibrationQualityLevel.NOT_CALIBRATED.name
        universalQualityUpdatedAt = 0L
        
        // Clear Portrait calibration (DEPRECATED)
        isPortraitCalibrated = false
        portraitCalibrationTime = 0L
        forwardAxisPortrait = floatArrayOf(1f, 0f, 0f)
        lateralAxisPortrait = floatArrayOf(0f, 1f, 0f)
        baselinePortrait = floatArrayOf(0f, 0f, 0f)
        noiseStdDevPortrait = 0f
        maxVibrXPortrait = 0f
        maxVibrYPortrait = 0f
        maxVibrZPortrait = 0f
        
        isLandscapeCalibrated = false
        landscapeCalibrationTime = 0L
        forwardAxisLandscape = floatArrayOf(1f, 0f, 0f)
        lateralAxisLandscape = floatArrayOf(0f, 1f, 0f)
        baselineLandscape = floatArrayOf(0f, 0f, 0f)
        noiseStdDevLandscape = 0f
        maxVibrXLandscape = 0f
        maxVibrYLandscape = 0f
        maxVibrZLandscape = 0f
        
        isLearningForward = false
        
        // Backward compatibility
        isCalibrated = false
        calibrationTime = 0L
        forwardAxis = floatArrayOf(1f, 0f, 0f)
        lateralAxis = floatArrayOf(0f, 1f, 0f)
        
        saveToPrefs()
        Log.d("DragCalibration", "🗑️ ALL calibrations cleared (Universal + Portrait + Landscape)")
    }
    
    /**
     * Стартира процеса на учебно ускорение
     */
    fun startLearning() {
        if (!isCalibrated) {
            isLearningForward = true
            Log.d("DragCalibration", "Започва учебно ускорение...")
        }
    }
    
    /**
     * Заключва forward/lateral оси за PORTRAIT (от DragCalibrationActivity)
     */
    fun lockPortraitAxes(forward: FloatArray, lateral: FloatArray, baseline: FloatArray, 
                        maxVibrX: Float = 0f, maxVibrY: Float = 0f, maxVibrZ: Float = 0f) {
        forwardAxisPortrait = forward.clone()
        lateralAxisPortrait = lateral.clone()
        baselinePortrait = baseline.clone()
        noiseStdDevPortrait = 0f // Deprecated
        maxVibrXPortrait = maxVibrX
        maxVibrYPortrait = maxVibrY
        maxVibrZPortrait = maxVibrZ
        isPortraitCalibrated = true
        isLearningForward = false
        portraitCalibrationTime = System.currentTimeMillis()
        
        // UNIVERSAL калибрация (за ForegroundService)
        gravityVector = baseline.clone()
        forwardVector = forward.clone()
        // RIGHT = normalize(cross(GRAVITY, FORWARD))
        // ВАЖНО: baseline вече е gravity вектор (посока ДОЛУ), НЕ го обръщаме!
        rightVector = floatArrayOf(
            baseline[1] * forward[2] - baseline[2] * forward[1],
            baseline[2] * forward[0] - baseline[0] * forward[2],
            baseline[0] * forward[1] - baseline[1] * forward[0]
        )
        val rightMag = kotlin.math.sqrt(rightVector[0]*rightVector[0] + rightVector[1]*rightVector[1] + rightVector[2]*rightVector[2])
        if (rightMag > 0.01f) {
            rightVector[0] /= rightMag
            rightVector[1] /= rightMag
            rightVector[2] /= rightMag
        }
        
        // MAX вибрация е MAX от 3-те оси
        maxVibrationBaseline = maxOf(maxVibrX, maxVibrY, maxVibrZ).coerceAtLeast(0.8f)
        // Записваме по-осовите вибрации за WEIGHTED threshold
        maxVibrXUniversal = maxVibrX
        maxVibrYUniversal = maxVibrY
        maxVibrZUniversal = maxVibrZ
        universalBaselineSamples = 0
        universalForwardSamples = 0
        universalBaselineNoiseRms = 0f
        universalForwardMeanMagnitude = 0f
        universalForwardConsistency = 0f
        universalForwardToNoiseRatio = 0f
        isUniversalCalibrated = true
        universalCalibrationTime = portraitCalibrationTime
        universalQualityScore = 0
        universalQualityLevelRaw = CalibrationQualityLevel.NOT_CALIBRATED.name
        universalQualityUpdatedAt = universalCalibrationTime
        
        // Backward compatibility
        forwardAxis = forward.clone()
        lateralAxis = lateral.clone()
        isCalibrated = true
        calibrationTime = portraitCalibrationTime
        
        saveToPrefs()
        
        Log.d("DragCalibration", "✅ PORTRAIT оси заключени и запазени!")
        Log.d("DragCalibration", "Forward: (${String.format("%.3f", forward[0])}, ${String.format("%.3f", forward[1])}, ${String.format("%.3f", forward[2])})")
        Log.d("DragCalibration", "Baseline: (${String.format("%.3f", baseline[0])}, ${String.format("%.3f", baseline[1])}, ${String.format("%.3f", baseline[2])})")
        Log.d("DragCalibration", "Max vibrations per axis: X=${String.format("%.3f", maxVibrX)}, Y=${String.format("%.3f", maxVibrY)}, Z=${String.format("%.3f", maxVibrZ)} m/s²")
        Log.d("DragCalibration", "Lateral: (${String.format("%.3f", lateral[0])}, ${String.format("%.3f", lateral[1])}, ${String.format("%.3f", lateral[2])})")
        Log.d("DragCalibration", "🌐 UNIVERSAL калибрация: isUniversalCalibrated=true, maxVibrationBaseline=${String.format("%.2f", maxVibrationBaseline)} m/s²")
    }
    
    /**
     * Заключва forward/lateral оси за LANDSCAPE (от DragCalibrationActivity)
     */
    fun lockLandscapeAxes(forward: FloatArray, lateral: FloatArray, baseline: FloatArray, 
                         maxVibrX: Float = 0f, maxVibrY: Float = 0f, maxVibrZ: Float = 0f) {
        forwardAxisLandscape = forward.clone()
        lateralAxisLandscape = lateral.clone()
        baselineLandscape = baseline.clone()
        noiseStdDevLandscape = 0f // Deprecated
        maxVibrXLandscape = maxVibrX
        maxVibrYLandscape = maxVibrY
        maxVibrZLandscape = maxVibrZ
        isLandscapeCalibrated = true
        isLearningForward = false
        landscapeCalibrationTime = System.currentTimeMillis()
        
        // UNIVERSAL калибрация (за ForegroundService)
        gravityVector = baseline.clone()
        forwardVector = forward.clone()
        // RIGHT = normalize(cross(GRAVITY, FORWARD))
        // ВАЖНО: baseline вече е gravity вектор (посока ДОЛУ), НЕ го обръщаме!
        rightVector = floatArrayOf(
            baseline[1] * forward[2] - baseline[2] * forward[1],
            baseline[2] * forward[0] - baseline[0] * forward[2],
            baseline[0] * forward[1] - baseline[1] * forward[0]
        )
        val rightMag = kotlin.math.sqrt(rightVector[0]*rightVector[0] + rightVector[1]*rightVector[1] + rightVector[2]*rightVector[2])
        if (rightMag > 0.01f) {
            rightVector[0] /= rightMag
            rightVector[1] /= rightMag
            rightVector[2] /= rightMag
        }
        
        // MAX вибрация е MAX от 3-те оси
        maxVibrationBaseline = maxOf(maxVibrX, maxVibrY, maxVibrZ).coerceAtLeast(0.8f)
        // Записваме по-осовите вибрации за WEIGHTED threshold
        maxVibrXUniversal = maxVibrX
        maxVibrYUniversal = maxVibrY
        maxVibrZUniversal = maxVibrZ
        universalBaselineSamples = 0
        universalForwardSamples = 0
        universalBaselineNoiseRms = 0f
        universalForwardMeanMagnitude = 0f
        universalForwardConsistency = 0f
        universalForwardToNoiseRatio = 0f
        isUniversalCalibrated = true
        universalCalibrationTime = landscapeCalibrationTime
        universalQualityScore = 0
        universalQualityLevelRaw = CalibrationQualityLevel.NOT_CALIBRATED.name
        universalQualityUpdatedAt = universalCalibrationTime
        
        // Backward compatibility - ако няма portrait, landscape става default
        if (!isPortraitCalibrated) {
            forwardAxis = forward.clone()
            lateralAxis = lateral.clone()
            isCalibrated = true
            calibrationTime = landscapeCalibrationTime
        }
        
        saveToPrefs()
        
        Log.d("DragCalibration", "✅ LANDSCAPE оси заключени и запазени!")
        Log.d("DragCalibration", "Forward: (${String.format("%.3f", forward[0])}, ${String.format("%.3f", forward[1])}, ${String.format("%.3f", forward[2])})")
        Log.d("DragCalibration", "Baseline: (${String.format("%.3f", baseline[0])}, ${String.format("%.3f", baseline[1])}, ${String.format("%.3f", baseline[2])})")
        Log.d("DragCalibration", "Max vibrations per axis: X=${String.format("%.3f", maxVibrX)}, Y=${String.format("%.3f", maxVibrY)}, Z=${String.format("%.3f", maxVibrZ)} m/s²")
        Log.d("DragCalibration", "Lateral: (${String.format("%.3f", lateral[0])}, ${String.format("%.3f", lateral[1])}, ${String.format("%.3f", lateral[2])})")
        Log.d("DragCalibration", "🌐 UNIVERSAL калибрация: isUniversalCalibrated=true, maxVibrationBaseline=${String.format("%.2f", maxVibrationBaseline)} m/s²")
    }
    
    /**
     * Връща осите за дадена ориентация (null ако не е калибрирана)
     */
    fun getAxesForOrientation(isLandscape: Boolean): Pair<FloatArray, FloatArray>? {
        return if (isLandscape) {
            if (isLandscapeCalibrated) {
                Pair(forwardAxisLandscape, lateralAxisLandscape)
            } else null
        } else {
            if (isPortraitCalibrated) {
                Pair(forwardAxisPortrait, lateralAxisPortrait)
            } else null
        }
    }
    
    /**
     * Връща baseline вектора за дадена ориентация (null ако не е калибрирана)
     */
    fun getBaselineForOrientation(isLandscape: Boolean): FloatArray? {
        return if (isLandscape) {
            if (isLandscapeCalibrated) baselineLandscape.clone() else null
        } else {
            if (isPortraitCalibrated) baselinePortrait.clone() else null
        }
    }
    
    /**
     * Връща noise std dev за дадена ориентация (0f ако не е калибрирана)
     * DEPRECATED - използвай getMaxVibrationsPerAxis
     */
    @Deprecated("Use getMaxVibrationsPerAxis instead")
    fun getNoiseStdDevForOrientation(isLandscape: Boolean): Float {
        return if (isLandscape) {
            if (isLandscapeCalibrated) noiseStdDevLandscape else 0f
        } else {
            if (isPortraitCalibrated) noiseStdDevPortrait else 0f
        }
    }
    
    /**
     * Връща максималните вибрации по всяка ос за дадена ориентация
     * @return FloatArray(3) - [maxX, maxY, maxZ] или null ако не е калибрирана
     */
    fun getMaxVibrationsPerAxis(isLandscape: Boolean): FloatArray? {
        return if (isLandscape) {
            if (isLandscapeCalibrated) {
                floatArrayOf(maxVibrXLandscape, maxVibrYLandscape, maxVibrZLandscape)
            } else null
        } else {
            if (isPortraitCalibrated) {
                floatArrayOf(maxVibrXPortrait, maxVibrYPortrait, maxVibrZPortrait)
            } else null
        }
    }
    
    /**
     * Проверява дали има калибрация за дадена ориентация
     */
    fun hasCalibrationFor(isLandscape: Boolean): Boolean {
        return if (isLandscape) isLandscapeCalibrated else isPortraitCalibrated
    }
    
    /**
     * Проверява дали има ПОНЕ 1 калибрация
     */
    fun hasAnyCalibration(): Boolean {
        return isUniversalCalibrated || isPortraitCalibrated || isLandscapeCalibrated
    }
    
    /**
     * Запазва UNIVERSAL калибрация (gravity-based, работи с всяка ориентация!)
     */
    fun lockUniversalCalibration(gravity: FloatArray, forward: FloatArray, maxVibration: Float) {
        // Нормализираме gravity → DOWN
        val gravityMag = kotlin.math.sqrt(
            gravity[0] * gravity[0] +
            gravity[1] * gravity[1] +
            gravity[2] * gravity[2]
        )
        gravityVector = floatArrayOf(gravity[0], gravity[1], gravity[2])
        
        // Нормализираме forward
        val forwardMag = kotlin.math.sqrt(
            forward[0] * forward[0] +
            forward[1] * forward[1] +
            forward[2] * forward[2]
        )
        forwardVector = floatArrayOf(
            forward[0] / forwardMag,
            forward[1] / forwardMag,
            forward[2] / forwardMag
        )
        
        // Изчисляваме RIGHT = cross(DOWN, FORWARD)
        val downNorm = floatArrayOf(
            gravity[0] / gravityMag,
            gravity[1] / gravityMag,
            gravity[2] / gravityMag
        )
        rightVector = floatArrayOf(
            downNorm[1] * forwardVector[2] - downNorm[2] * forwardVector[1],
            downNorm[2] * forwardVector[0] - downNorm[0] * forwardVector[2],
            downNorm[0] * forwardVector[1] - downNorm[1] * forwardVector[0]
        )
        
        maxVibrationBaseline = maxVibration
        // NOTE: maxVibrX/Y/ZUniversal се записват в lockPortraitAxes/lockLandscapeAxes
        isUniversalCalibrated = true
        universalCalibrationTime = System.currentTimeMillis()
        universalQualityUpdatedAt = universalCalibrationTime
        universalQualityScore = 0
        universalQualityLevelRaw = CalibrationQualityLevel.NOT_CALIBRATED.name
        
        saveToPrefs()
        
        Log.d("DragCalibration", "✅ UNIVERSAL калибрация заключена!")
        Log.d("DragCalibration", "   Gravity (mag=${String.format("%.2f", gravityMag)}): [${String.format("%.3f", gravityVector[0])}, ${String.format("%.3f", gravityVector[1])}, ${String.format("%.3f", gravityVector[2])}]")
        Log.d("DragCalibration", "   Forward: [${String.format("%.3f", forwardVector[0])}, ${String.format("%.3f", forwardVector[1])}, ${String.format("%.3f", forwardVector[2])}]")
        Log.d("DragCalibration", "   Right: [${String.format("%.3f", rightVector[0])}, ${String.format("%.3f", rightVector[1])}, ${String.format("%.3f", rightVector[2])}]")
        Log.d("DragCalibration", "   🔥 MAX вибрация: ${String.format("%.2f", maxVibration)} m/s²")
        Log.d("DragCalibration", "   🎯 ДИНАМИЧЕН праг: ${String.format("%.2f", maxVibration * 1.5f)} m/s² (1.5× MAX вибрация)")
    }
    
    /**
     * Изчислява linear acceleration (премахва gravity от RAW accelerometer)
     * ИЗПОЛЗВА ПРАВИЛНАТА КАЛИБРАЦИЯ СПОРЕД ОРИЕНТАЦИЯТА!
     */
    fun getLinearAcceleration(rawAccel: FloatArray, isLandscape: Boolean): FloatArray {
        val baseline = if (isLandscape) baselineLandscape else baselinePortrait
        return floatArrayOf(
            rawAccel[0] - baseline[0],
            rawAccel[1] - baseline[1],
            rawAccel[2] - baseline[2]
        )
    }
    
    /**
     * Изчислява forward acceleration (проекция на FORWARD вектор)
     * ИЗПОЛЗВА ПРАВИЛНАТА КАЛИБРАЦИЯ СПОРЕД ОРИЕНТАЦИЯТА!
     */
    fun getForwardAcceleration(rawAccel: FloatArray, isLandscape: Boolean): Float {
        val linearAccel = getLinearAcceleration(rawAccel, isLandscape)
        val forward = if (isLandscape) forwardAxisLandscape else forwardAxisPortrait
        return linearAccel[0] * forward[0] +
               linearAccel[1] * forward[1] +
               linearAccel[2] * forward[2]
    }
    
    /**
     * Изчислява lateral (странично) ускорение - перпендикулярно на forward посоката.
     * Това е МАГНИТУДЪТ на компонента който НЕ Е в посоката напред.
     * 
     * Използва се за филтриране на вибрации:
     * - Вибрации: forward ≈ lateral (хаотични посоки)
     * - Реално ускорение напред: forward >> lateral (доминираща посока)
     */
    fun getLateralAcceleration(rawAccel: FloatArray, isLandscape: Boolean): Float {
        val linearAccel = getLinearAcceleration(rawAccel, isLandscape)
        val forward = if (isLandscape) forwardAxisLandscape else forwardAxisPortrait
        
        // Forward компонент (проекция върху forward vector)
        val forwardComponent = linearAccel[0] * forward[0] +
                               linearAccel[1] * forward[1] +
                               linearAccel[2] * forward[2]
        
        // Forward вектор scaled с forward компонента
        val forwardProjection = floatArrayOf(
            forward[0] * forwardComponent,
            forward[1] * forwardComponent,
            forward[2] * forwardComponent
        )
        
        // Lateral = linear accel МИНУС forward projection
        val lateral = floatArrayOf(
            linearAccel[0] - forwardProjection[0],
            linearAccel[1] - forwardProjection[1],
            linearAccel[2] - forwardProjection[2]
        )
        
        // Magnitude на lateral компонента
        return kotlin.math.sqrt(
            lateral[0] * lateral[0] +
            lateral[1] * lateral[1] +
            lateral[2] * lateral[2]
        )
    }
    
    /**
     * Изчислява signed lateral acceleration (right/left) използвайки universal калибрация.
     * Положителна стойност = надясно, отрицателна = наляво.
     * Работи независимо от ориентацията на телефона.
     * 
     * @param rawAccel Raw accelerometer data [x, y, z]
     * @param liveGravity Live gravity vector (filtered from accelerometer) [x, y, z]
     * @return Lateral acceleration в m/s² (positive = right, negative = left)
     */
    fun getSignedLateralAcceleration(rawAccel: FloatArray, liveGravity: FloatArray): Float {
        if (!isUniversalCalibrated) return 0f
        
        // Изчисляваме linear acceleration (премахваме live gravity, не калибрираната!)
        val linearAccel = floatArrayOf(
            rawAccel[0] - liveGravity[0],
            rawAccel[1] - liveGravity[1],
            rawAccel[2] - liveGravity[2]
        )
        
        // Проекция върху rightVector (dot product)
        // Положителна = надясно, отрицателна = наляво
        return linearAccel[0] * rightVector[0] +
               linearAccel[1] * rightVector[1] +
               linearAccel[2] * rightVector[2]
    }
    
    /**
     * Изчислява signed forward acceleration (forward/backward) използвайки universal калибрация.
     * Положителна стойност = напред (ускорение), отрицателна = назад (спиране).
     * Работи независимо от ориентацията на телефона.
     * 
     * @param rawAccel Raw accelerometer data [x, y, z]
     * @param liveGravity Live gravity vector (filtered from accelerometer) [x, y, z]
     * @return Forward acceleration в m/s² (positive = forward, negative = backward)
     */
    fun getSignedForwardAcceleration(rawAccel: FloatArray, liveGravity: FloatArray): Float {
        if (!isUniversalCalibrated) return 0f
        
        // Изчисляваме linear acceleration (премахваме live gravity, не калибрираната!)
        val linearAccel = floatArrayOf(
            rawAccel[0] - liveGravity[0],
            rawAccel[1] - liveGravity[1],
            rawAccel[2] - liveGravity[2]
        )
        
        // Проекция върху forwardVector (dot product)
        // Положителна = напред, отрицателна = назад
        return linearAccel[0] * forwardVector[0] +
               linearAccel[1] * forwardVector[1] +
               linearAccel[2] * forwardVector[2]
    }

    fun getSignedForwardAccelerationFromLinear(linearAccel: FloatArray): Float {
        if (!isUniversalCalibrated) return 0f

        return linearAccel[0] * forwardVector[0] +
               linearAccel[1] * forwardVector[1] +
               linearAccel[2] * forwardVector[2]
    }

    fun getSignedLateralAccelerationFromLinear(linearAccel: FloatArray): Float {
        if (!isUniversalCalibrated) return 0f

        return linearAccel[0] * rightVector[0] +
               linearAccel[1] * rightVector[1] +
               linearAccel[2] * rightVector[2]
    }
    
    /**
     * DEPRECATED: Използвай getLinearAcceleration(rawAccel, isLandscape) вместо това!
     */
    @Deprecated("Use getLinearAcceleration(rawAccel, isLandscape)")
    fun getLinearAcceleration(rawAccel: FloatArray): FloatArray {
        return floatArrayOf(
            rawAccel[0] - gravityVector[0],
            rawAccel[1] - gravityVector[1],
            rawAccel[2] - gravityVector[2]
        )
    }
    
    /**
     * DEPRECATED: Използвай getForwardAcceleration(rawAccel, isLandscape) вместо това!
     */
    @Deprecated("Use getForwardAcceleration(rawAccel, isLandscape)")
    fun getForwardAcceleration(rawAccel: FloatArray): Float {
        val linearAccel = getLinearAcceleration(rawAccel)
        return linearAccel[0] * forwardVector[0] +
               linearAccel[1] * forwardVector[1] +
               linearAccel[2] * forwardVector[2]
    }
    
    /**
     * Връща динамичния праг (1.5× MAX вибрация от калибрацията)
     * @deprecated Използвайте getWeightedDynamicThreshold() за по-точна детекция
     */
    fun getDynamicThreshold(): Float {
        return maxVibrationBaseline * 1.5f
    }
    
    /**
     * Връща WEIGHTED динамичния праг (като в калибрацията) за по-точна детекция.
     * ИЗПОЛЗВА ПРАВИЛНАТА КАЛИБРАЦИЯ СПОРЕД ОРИЕНТАЦИЯТА!
     * 
     * @param linearAccel Clean linear acceleration (X, Y, Z) без gravity
     * @param isLandscape Дали е в Landscape режим
     * @return Weighted threshold за текущия acceleration pattern
     */
    fun getWeightedDynamicThreshold(linearAccel: FloatArray, isLandscape: Boolean): Float {
        val mag = kotlin.math.sqrt(
            linearAccel[0] * linearAccel[0] +
            linearAccel[1] * linearAccel[1] +
            linearAccel[2] * linearAccel[2]
        )
        
        val (maxVibrX, maxVibrY, maxVibrZ) = if (isLandscape) {
            Triple(maxVibrXLandscape, maxVibrYLandscape, maxVibrZLandscape)
        } else {
            Triple(maxVibrXPortrait, maxVibrYPortrait, maxVibrZPortrait)
        }
        
        val maxVibr = maxOf(maxVibrX, maxVibrY, maxVibrZ).coerceAtLeast(0.8f)
        
        if (mag < 0.001f) return maxVibr * 1.5f // Fallback само ако mag = 0
        
        // Weighted threshold: (|X|/mag * maxVibrX + |Y|/mag * maxVibrY + |Z|/mag * maxVibrZ) + 0.05
        return (kotlin.math.abs(linearAccel[0]) / mag * maxVibrX +
                kotlin.math.abs(linearAccel[1]) / mag * maxVibrY +
                kotlin.math.abs(linearAccel[2]) / mag * maxVibrZ) + 0.05f
    }
    
    /**
     * DEPRECATED: Използвай getWeightedDynamicThreshold(linearAccel, isLandscape) вместо това!
     */
    @Deprecated("Use getWeightedDynamicThreshold(linearAccel, isLandscape)")
    fun getWeightedDynamicThreshold(linearAccel: FloatArray): Float {
        val mag = kotlin.math.sqrt(
            linearAccel[0] * linearAccel[0] +
            linearAccel[1] * linearAccel[1] +
            linearAccel[2] * linearAccel[2]
        )
        
        if (mag < 0.001f) return maxVibrationBaseline * 1.5f // Fallback само ако mag = 0
        
        // Weighted threshold: (|X|/mag * maxVibrX + |Y|/mag * maxVibrY + |Z|/mag * maxVibrZ) + 0.05
        // ВАЖНО: НЕ слагаме coerceAtLeast! Weighted threshold трябва да е ПО-НИСЪК от fixed!
        return (kotlin.math.abs(linearAccel[0]) / mag * maxVibrXUniversal +
                kotlin.math.abs(linearAccel[1]) / mag * maxVibrYUniversal +
                kotlin.math.abs(linearAccel[2]) / mag * maxVibrZUniversal) + 0.05f
    }

    /**
     * Запазва диагностични метрики за качество на universal калибрацията.
     */
    fun updateUniversalCalibrationDiagnostics(
        baselineSamples: Int,
        forwardSamples: Int,
        baselineNoiseRms: Float,
        forwardMeanMagnitude: Float,
        forwardConsistency: Float,
        forwardToNoiseRatio: Float
    ) {
        universalBaselineSamples = baselineSamples.coerceAtLeast(0)
        universalForwardSamples = forwardSamples.coerceAtLeast(0)
        universalBaselineNoiseRms = baselineNoiseRms.coerceAtLeast(0f)
        universalForwardMeanMagnitude = forwardMeanMagnitude.coerceAtLeast(0f)
        universalForwardConsistency = forwardConsistency.coerceIn(0f, 1f)
        universalForwardToNoiseRatio = forwardToNoiseRatio.coerceAtLeast(0f)

        val report = getUniversalCalibrationQualityReport()
        universalQualityScore = report.score
        universalQualityLevelRaw = report.level.name
        universalQualityUpdatedAt = System.currentTimeMillis()
        saveToPrefs()
    }

    fun getAccuracyModeByCalibrationQuality(): String {
        val report = getUniversalCalibrationQualityReport()
        return when (report.level) {
            CalibrationQualityLevel.GOOD -> "HIGH_ACCURACY"
            CalibrationQualityLevel.WARNING -> "GOOD_ACCURACY"
            CalibrationQualityLevel.BAD -> "LOW_ACCURACY"
            CalibrationQualityLevel.NOT_CALIBRATED -> "GPS_ONLY"
        }
    }

    fun isUniversalCalibrationReliable(minScore: Int = 50): Boolean {
        if (!isUniversalCalibrated) return false
        return getUniversalCalibrationQualityReport().score >= minScore
    }

    fun getUniversalCalibrationQualityReport(
        staleAfterMs: Long = DEFAULT_STALE_CALIBRATION_MS
    ): CalibrationQualityReport {
        if (!isUniversalCalibrated) {
            return CalibrationQualityReport(
                level = CalibrationQualityLevel.NOT_CALIBRATED,
                score = 0,
                reasons = listOf("Universal calibration is missing."),
                baselineSamples = 0,
                forwardSamples = 0,
                baselineNoiseRms = 0f,
                baselineMaxVibration = 0f,
                forwardMeanMagnitude = 0f,
                forwardConsistency = 0f,
                forwardToNoiseRatio = 0f,
                isStale = false
            )
        }

        var score = 100
        val reasons = mutableListOf<String>()
        val now = System.currentTimeMillis()
        val isStale = universalCalibrationTime > 0L && (now - universalCalibrationTime) > staleAfterMs
        val hasDiagnostics = universalBaselineSamples > 0 || universalForwardSamples > 0 || universalQualityUpdatedAt > 0L

        val gravityMag = vectorMagnitude(gravityVector)
        val forwardMag = vectorMagnitude(forwardVector)
        val rightMag = vectorMagnitude(rightVector)

        if (forwardMag < 0.2f || rightMag < 0.2f || gravityMag < 0.2f) {
            score -= 45
            reasons.add("Calibration vectors are weak or invalid.")
        }

        val dotForwardRight = abs(dot(forwardVector, rightVector))
        if (dotForwardRight > 0.35f) {
            score -= 18
            reasons.add("Forward and right vectors are not orthogonal enough.")
        }

        val dotForwardGravity = abs(dot(forwardVector, gravityVector) / (forwardMag * gravityMag).coerceAtLeast(0.001f))
        if (dotForwardGravity > 0.55f) {
            score -= 16
            reasons.add("Forward vector is too aligned with gravity.")
        }

        if (!hasDiagnostics) {
            if (maxVibrationBaseline > 2.0f) {
                score -= 25
                reasons.add("Legacy calibration shows high idle vibration.")
            } else if (maxVibrationBaseline > 1.3f) {
                score -= 12
                reasons.add("Legacy calibration likely collected in non-stable conditions.")
            } else {
                score -= 8
                reasons.add("Legacy calibration has no diagnostic samples.")
            }
        } else {
            if (universalBaselineSamples < 40) {
                score -= 24
                reasons.add("Too few baseline samples were collected.")
            } else if (universalBaselineSamples < 60) {
                score -= 12
                reasons.add("Baseline sample count is lower than recommended.")
            }

            if (universalForwardSamples < 12) {
                score -= 24
                reasons.add("Too few forward samples were collected.")
            } else if (universalForwardSamples < 20) {
                score -= 10
                reasons.add("Forward sample count is lower than recommended.")
            }

            if (universalBaselineNoiseRms > 0.60f) {
                score -= 25
                reasons.add("Baseline noise is too high.")
            } else if (universalBaselineNoiseRms > 0.35f) {
                score -= 12
                reasons.add("Baseline noise is elevated.")
            }

            if (maxVibrationBaseline > 2.0f) {
                score -= 25
                reasons.add("Idle vibration peak is very high.")
            } else if (maxVibrationBaseline > 1.4f) {
                score -= 12
                reasons.add("Idle vibration peak is above ideal range.")
            }

            if (universalForwardMeanMagnitude < 0.35f) {
                score -= 35
                reasons.add("Forward movement signal is too weak.")
            } else if (universalForwardMeanMagnitude < 0.60f) {
                score -= 16
                reasons.add("Forward movement signal is weaker than ideal.")
            }

            if (universalForwardConsistency < 0.60f) {
                score -= 30
                reasons.add("Forward samples are inconsistent.")
            } else if (universalForwardConsistency < 0.75f) {
                score -= 14
                reasons.add("Forward samples have moderate directional drift.")
            }

            if (universalForwardToNoiseRatio < 1.2f) {
                score -= 35
                reasons.add("Signal-to-noise ratio is very low.")
            } else if (universalForwardToNoiseRatio < 2.0f) {
                score -= 16
                reasons.add("Signal-to-noise ratio is below target.")
            }
        }

        if (isStale) {
            score -= 10
            reasons.add("Calibration is stale and should be refreshed.")
        }

        score = score.coerceIn(0, 100)
        val level = resolveQualityLevel(score)
        if (reasons.isEmpty()) {
            reasons.add("Calibration quality is stable.")
        }

        return CalibrationQualityReport(
            level = level,
            score = score,
            reasons = reasons,
            baselineSamples = universalBaselineSamples,
            forwardSamples = universalForwardSamples,
            baselineNoiseRms = universalBaselineNoiseRms,
            baselineMaxVibration = maxVibrationBaseline,
            forwardMeanMagnitude = universalForwardMeanMagnitude,
            forwardConsistency = universalForwardConsistency,
            forwardToNoiseRatio = universalForwardToNoiseRatio,
            isStale = isStale
        )
    }

    private fun resolveQualityLevel(score: Int): CalibrationQualityLevel {
        return when {
            score >= 75 -> CalibrationQualityLevel.GOOD
            score >= 50 -> CalibrationQualityLevel.WARNING
            else -> CalibrationQualityLevel.BAD
        }
    }

    private fun normalizeQualityLevel(raw: String): CalibrationQualityLevel {
        return try {
            CalibrationQualityLevel.valueOf(raw)
        } catch (_: Exception) {
            CalibrationQualityLevel.NOT_CALIBRATED
        }
    }

    private fun vectorMagnitude(v: FloatArray): Float {
        return sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
    }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    }
    
    /**
     * Изчиства калибрацията за дадена ориентация
     */
    fun clearOrientation(isLandscape: Boolean) {
        if (isLandscape) {
            isLandscapeCalibrated = false
            landscapeCalibrationTime = 0L
            forwardAxisLandscape = floatArrayOf(1f, 0f, 0f)
            lateralAxisLandscape = floatArrayOf(0f, 1f, 0f)
            baselineLandscape = floatArrayOf(0f, 0f, 0f)
            noiseStdDevLandscape = 0f
            maxVibrXLandscape = 0f
            maxVibrYLandscape = 0f
            maxVibrZLandscape = 0f
        } else {
            isPortraitCalibrated = false
            portraitCalibrationTime = 0L
            forwardAxisPortrait = floatArrayOf(1f, 0f, 0f)
            lateralAxisPortrait = floatArrayOf(0f, 1f, 0f)
            baselinePortrait = floatArrayOf(0f, 0f, 0f)
            noiseStdDevPortrait = 0f
            maxVibrXPortrait = 0f
            maxVibrYPortrait = 0f
            maxVibrZPortrait = 0f
        }
        
        // Update backward compatibility
        if (!isPortraitCalibrated && !isLandscapeCalibrated) {
            isCalibrated = false
            calibrationTime = 0L
            // Ако и двете ориентации са изчистени, изчистваме и universal калибрацията
            isUniversalCalibrated = false
            universalCalibrationTime = 0L
            universalBaselineSamples = 0
            universalForwardSamples = 0
            universalBaselineNoiseRms = 0f
            universalForwardMeanMagnitude = 0f
            universalForwardConsistency = 0f
            universalForwardToNoiseRatio = 0f
            universalQualityScore = 0
            universalQualityLevelRaw = CalibrationQualityLevel.NOT_CALIBRATED.name
            universalQualityUpdatedAt = 0L
        }
        
        saveToPrefs()
        Log.d("DragCalibration", "🗑️ ${if (isLandscape) "LANDSCAPE" else "PORTRAIT"} калибрация изчистена")
    }
}

