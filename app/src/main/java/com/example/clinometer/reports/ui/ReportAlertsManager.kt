package com.example.clinometer.reports.ui

import android.content.Context
import android.location.Location
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import com.example.clinometer.reports.data.PoliceReport
import com.example.clinometer.reports.data.ReportType
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Управлява alerts за репорти при навигация (Waze-style)
 * - Предупреждава на 500м преди репорт
 * - Показва persistent UI alert докато наближаваш
 * - Пита за потвърждение след преминаване
 * - Zero нови Firebase заявки (локални изчисления)
 */
class ReportAlertsManager(
    private val context: Context,
    private val onAlertShow: (PoliceReport, Float) -> Unit, // Callback за pokazване на alert UI
    private val onAlertUpdate: (PoliceReport, Float) -> Unit, // Callback за update на distance
    private val onAlertHide: () -> Unit, // Callback за скриване на alert UI
    private val onConfirmationNeeded: (PoliceReport, () -> Unit) -> Unit // Callback за pokazване на prompt
) {
    private data class ReportTrackingState(
        var minDistance: Float = Float.MAX_VALUE,
        var wasWithinPassZone: Boolean = false,
        var increasingDistanceSamples: Int = 0,
        var lastDistance: Float? = null
    )

    private var tts: TextToSpeech? = null
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val handler = Handler(Looper.getMainLooper())
    
    // State tracking
    private val alertedReports = mutableSetOf<String>() // Показани alerts
    private val passedReports = mutableMapOf<String, Long>() // Минати репорти + timestamp
    private val respondedReports = mutableSetOf<String>() // Отговорени prompt-ове
    private val reportTrackingStates = mutableMapOf<String, ReportTrackingState>() // Runtime tracking per report
    
    // Active alert tracking
    private var activeAlertReport: PoliceReport? = null
    
    // Configuration
    private var isNavigationActive = false
    private var currentRoute: LineString? = null
    
    companion object {
        private const val TAG = "ReportAlertsManager"
        private const val MAX_ALERT_DISTANCE_METERS = 800f
        private const val ALERT_AHEAD_BEARING_DEGREES = 120f
        private const val PASS_BEHIND_BEARING_DEGREES = 130f
        private const val PASS_ARM_DISTANCE_METERS = 120f
        private const val PASS_MIN_APPROACH_METERS = 90f
        private const val PASS_DISTANCE_RECOVERY_METERS = 35f
        private const val PASS_DISTANCE_INCREASE_THRESHOLD_METERS = 8f
        private const val PASS_INCREASING_SAMPLES_REQUIRED = 2
        private const val PASS_BEARING_DISTANCE_METERS = 220f
        private const val CANDIDATE_DISTANCE_EPSILON_METERS = 5f
        private const val ROUTE_TOLERANCE_METERS = 50.0 // Репорт е "на маршрута" ако е < 50м от линията
        private const val PASS_DETECTION_WAIT_MS = 3000L // 3 секунди wait след pass
        private const val PASS_TIMEOUT_MS = 60000L // 60 сек fallback
    }
    
    init {
        initializeTTS()
    }
    
    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("bg", "BG")
                Log.d(TAG, "TTS initialized successfully")
            } else {
                Log.e(TAG, "TTS initialization failed")
            }
        }
    }
    
    /**
     * Set navigation state и route data
     */
    fun setNavigationState(isActive: Boolean, routeGeometry: LineString? = null) {
        val wasActive = isNavigationActive
        isNavigationActive = isActive
        currentRoute = routeGeometry
        
        Log.d(TAG, "setNavigationState: isActive=$isActive, hasRoute=${routeGeometry != null}")
        
        if (!isActive && wasActive) {
            // Navigation спряла - clear state
            clearAllState()
            Log.d(TAG, "Navigation stopped - state cleared")
        } else if (isActive && !wasActive) {
            Log.d(TAG, "Navigation started")
        }
    }
    
    /**
     * Проверява за alerts на репорти при GPS update
     * @param currentLocation Текуща позиция
     * @param currentBearing Текущ ъгъл на движение (0-360)
     * @param reports Списък с налични репорти
     */
    fun checkForAlerts(
        currentLocation: Location,
        currentBearing: Float,
        reports: List<PoliceReport>
    ) {
        if (!isNavigationActive) {
            Log.d(TAG, "checkForAlerts: Navigation not active, skipping")
            return
        }
        
        Log.d(TAG, "checkForAlerts: location=${currentLocation.latitude},${currentLocation.longitude}, bearing=$currentBearing, reports=${reports.size}")
        
        var closestAlertedReport: Pair<PoliceReport, Float>? = null
        
        reports.forEach { report ->
            val reportId = report.id
            
            // Изчисляваме разстояние до репорта
            val distance = calculateDistance(
                currentLocation.latitude, currentLocation.longitude,
                report.location.latitude, report.location.longitude
            )

            val trackingState = updateReportTrackingState(reportId, distance)
            
            // Check за pass detection
            if (reportId in alertedReports && reportId !in passedReports) {
                if (hasPassedReport(currentLocation, currentBearing, report, distance, trackingState)) {
                    onReportPassed(report)
                }
            }
            
            // Check за alert (само ако още не е показан)
            if (reportId !in alertedReports) {
                if (shouldShowAlert(currentLocation, currentBearing, report, distance)) {
                    showAlert(report, distance)
                }
            }
            
            // Track closest alerted report за UI update (СЛЕД като може би сме го добавили в alertedReports)
            if (reportId in alertedReports && reportId !in passedReports) {
                val aheadForDisplay = isReportAhead(currentLocation, currentBearing, report, ALERT_AHEAD_BEARING_DEGREES)
                val recentlyApproached = trackingState.wasWithinPassZone &&
                    distance <= trackingState.minDistance + PASS_DISTANCE_RECOVERY_METERS

                if (aheadForDisplay || recentlyApproached) {
                    val currentClosest = closestAlertedReport
                    if (currentClosest == null || isBetterAlertCandidate(report, distance, currentClosest)) {
                        closestAlertedReport = report to distance
                    }
                }
            }
        }
        
        // Update active alert UI с live distance
        if (closestAlertedReport != null) {
            val (report, distance) = closestAlertedReport
            // Update existing alert distance
            handler.post {
                onAlertUpdate(report, distance)
            }
        } else if (activeAlertReport != null) {
            // No more active alerts - hide UI
            hideActiveAlert()
        }
        
        // Cleanup old passed reports (fallback timeout)
        cleanupOldPassedReports()
    }
    
    /**
     * Проверява дали трябва да покажем alert за репорт
     */
    private fun shouldShowAlert(
        currentLocation: Location,
        currentBearing: Float,
        report: PoliceReport,
        distance: Float
    ): Boolean {
        Log.d(TAG, "Checking alert: reportId=${report.id}, distance=${distance.toInt()}m, type=${report.getReportType()}")
        
        // Check 1: Разстояние <= 800м (по-широк диапазон)
        if (distance > MAX_ALERT_DISTANCE_METERS) {
            Log.d(TAG, "→ Skip: too far (>${distance.toInt()}m)")
            return false
        }

        // Check 2: Докладът трябва да е на маршрута (ако имаме route geometry)
        if (!isReportOnRoute(report)) {
            Log.d(TAG, "→ Skip: report is off-route")
            return false
        }
        
        // Check 3: Ahead ли е (не зад нас) - по-толерантна проверка
        val bearingToReport = calculateBearing(
            currentLocation.latitude, currentLocation.longitude,
            report.location.latitude, report.location.longitude
        )
        val bearingDiff = abs(normalizeBearing(bearingToReport - currentBearing))
        Log.d(TAG, "→ Bearing check: currentBearing=$currentBearing, bearingToReport=$bearingToReport, diff=$bearingDiff")
        if (bearingDiff > ALERT_AHEAD_BEARING_DEGREES) {
            Log.d(TAG, "→ Skip: behind us (bearing diff > ${ALERT_AHEAD_BEARING_DEGREES.toInt()}°)")
            return false
        }
        
        // Check 4: Score check (не показваме явно false reports)
        if (report.getScore() < -2) {
            Log.d(TAG, "→ Skip: low score (${report.getScore()})")
            return false
        }
        
        Log.d(TAG, "✅ SHOW ALERT for report ${report.id}")
        return true
    }
    
    /**
     * Показва alert за репорт
     */
    private fun showAlert(report: PoliceReport, distance: Float) {
        val reportType = report.getReportType()
        val distanceText = "${distance.toInt()}м"
        
        // TTS announcement
        val message = when (reportType) {
            ReportType.POLICE -> "Полиция след $distanceText"
            ReportType.CAMERA -> "Камера след $distanceText"
            ReportType.ACCIDENT -> "Внимание! Инцидент след $distanceText"
            ReportType.HAZARD -> "Внимание! Опасност след $distanceText"
            ReportType.TRAFFIC -> "Трафик след $distanceText"
            ReportType.ROADWORK -> "Ремонт на пътя след $distanceText"
        }
        
        speakMessage(message)
        
        // Show persistent UI alert
        handler.post {
            onAlertShow(report, distance)
        }
        
        // Vibration
        vibrate(100)
        
        // Mark as alerted and set as active
        alertedReports.add(report.id)
        activeAlertReport = report
        
        Log.d(TAG, "Alert shown: $message (reportId=${report.id})")
    }
    
    /**
     * Скрива активния alert UI
     */
    private fun hideActiveAlert() {
        if (activeAlertReport != null) {
            handler.post {
                onAlertHide()
            }
            activeAlertReport = null
            Log.d(TAG, "Active alert hidden")
        }
    }
    
    /**
     * Проверява дали сме минали репорта
     */
    private fun hasPassedReport(
        currentLocation: Location,
        currentBearing: Float,
        report: PoliceReport,
        currentDistance: Float,
        trackingState: ReportTrackingState
    ): Boolean {
        // Guard: prompt only if user actually approached the report zone first
        if (!trackingState.wasWithinPassZone || trackingState.minDistance > PASS_MIN_APPROACH_METERS) {
            return false
        }

        // Method 1: distance recovery after nearest approach (most reliable)
        val isRecoveringDistance =
            currentDistance > trackingState.minDistance + PASS_DISTANCE_RECOVERY_METERS &&
                trackingState.increasingDistanceSamples >= PASS_INCREASING_SAMPLES_REQUIRED

        if (isRecoveringDistance) {
            Log.d(
                TAG,
                "Passed report (distance trend): ${report.id}, min=${trackingState.minDistance.toInt()}m, now=${currentDistance.toInt()}m"
            )
            return true
        }

        // Method 2: bearing turned behind after approach + distance trend
        val bearingToReport = calculateBearing(
            currentLocation.latitude, currentLocation.longitude,
            report.location.latitude, report.location.longitude
        )
        val bearingDiff = abs(normalizeBearing(bearingToReport - currentBearing))
        
        val isBehindAfterApproach =
            bearingDiff > PASS_BEHIND_BEARING_DEGREES &&
                currentDistance <= PASS_BEARING_DISTANCE_METERS &&
                trackingState.increasingDistanceSamples >= 1

        if (isBehindAfterApproach) {
            Log.d(TAG, "Passed report (bearing): ${report.id}")
            return true
        }

        return false
    }
    
    /**
     * Handler за минат репорт
     */
    private fun onReportPassed(report: PoliceReport) {
        passedReports[report.id] = System.currentTimeMillis()
        
        // Hide active alert UI
        if (activeAlertReport?.id == report.id) {
            hideActiveAlert()
        }
        
        // Wait 3 секунди преди да покажем prompt (safety buffer)
        handler.postDelayed({
            if (report.id !in respondedReports && isNavigationActive) {
                showConfirmationPrompt(report)
            }
        }, PASS_DETECTION_WAIT_MS)
    }
    
    /**
     * Показва confirmation prompt за репорт
     */
    private fun showConfirmationPrompt(report: PoliceReport) {
        // Callback към UI layer за показване на bottom sheet
        handler.post {
            onConfirmationNeeded(report) {
                // Mark as responded when dialog dismissed
                respondedReports.add(report.id)
            }
        }
        
        Log.d(TAG, "Confirmation prompt shown for: ${report.id}")
    }

    private fun updateReportTrackingState(reportId: String, currentDistance: Float): ReportTrackingState {
        val state = reportTrackingStates.getOrPut(reportId) { ReportTrackingState() }
        state.minDistance = minOf(state.minDistance, currentDistance)

        if (currentDistance <= PASS_ARM_DISTANCE_METERS) {
            state.wasWithinPassZone = true
        }

        val previousDistance = state.lastDistance
        state.increasingDistanceSamples =
            if (previousDistance != null && currentDistance > previousDistance + PASS_DISTANCE_INCREASE_THRESHOLD_METERS) {
                state.increasingDistanceSamples + 1
            } else {
                0
            }

        state.lastDistance = currentDistance
        return state
    }

    private fun isReportAhead(
        currentLocation: Location,
        currentBearing: Float,
        report: PoliceReport,
        maxBearingDiff: Float
    ): Boolean {
        val bearingToReport = calculateBearing(
            currentLocation.latitude, currentLocation.longitude,
            report.location.latitude, report.location.longitude
        )
        val bearingDiff = abs(normalizeBearing(bearingToReport - currentBearing))
        return bearingDiff <= maxBearingDiff
    }

    private fun isBetterAlertCandidate(
        candidateReport: PoliceReport,
        candidateDistance: Float,
        currentBest: Pair<PoliceReport, Float>
    ): Boolean {
        val (bestReport, bestDistance) = currentBest

        if (candidateDistance < bestDistance - CANDIDATE_DISTANCE_EPSILON_METERS) {
            return true
        }

        if (abs(candidateDistance - bestDistance) <= CANDIDATE_DISTANCE_EPSILON_METERS) {
            val candidatePriority = candidateReport.getReportType().alertPriority()
            val bestPriority = bestReport.getReportType().alertPriority()

            if (candidatePriority != bestPriority) {
                return candidatePriority < bestPriority
            }

            if (candidateReport.getScore() != bestReport.getScore()) {
                return candidateReport.getScore() > bestReport.getScore()
            }

            return candidateReport.id < bestReport.id
        }

        return false
    }

    private fun ReportType.alertPriority(): Int {
        return when (this) {
            ReportType.POLICE -> 0
            ReportType.CAMERA -> 1
            ReportType.ACCIDENT -> 2
            ReportType.HAZARD -> 3
            ReportType.ROADWORK -> 4
            ReportType.TRAFFIC -> 5
        }
    }
    
    /**
     * Проверява дали репорт е на активния маршрут
     */
    private fun isReportOnRoute(report: PoliceReport): Boolean {
        val route = currentRoute ?: return true // Ако няма route data, assume ON route
        
        try {
            val reportPoint = Point.fromLngLat(report.location.longitude, report.location.latitude)
            val routePoints = route.coordinates()
            
            // Намираме най-близката точка на route линията (manual calculation)
            var minDistance = Double.MAX_VALUE
            
            for (i in 0 until routePoints.size - 1) {
                val p1 = routePoints[i]
                val p2 = routePoints[i + 1]
                
                val distance = pointToLineSegmentDistance(
                    reportPoint.longitude(), reportPoint.latitude(),
                    p1.longitude(), p1.latitude(),
                    p2.longitude(), p2.latitude()
                )
                
                if (distance < minDistance) {
                    minDistance = distance
                }
            }
            
            // Convert to meters (approximate)
            val distanceMeters = minDistance * 111000 // 1 degree ≈ 111km
            
            return distanceMeters < ROUTE_TOLERANCE_METERS
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking route proximity", e)
            return true // Fallback: assume on route
        }
    }
    
    /**
     * Изчислява разстоянието от точка до линеен сегмент
     */
    private fun pointToLineSegmentDistance(
        px: Double, py: Double,
        x1: Double, y1: Double,
        x2: Double, y2: Double
    ): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        
        if (dx == 0.0 && dy == 0.0) {
            // Сегментът е точка
            return kotlin.math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1))
        }
        
        val t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)
        val clampedT = t.coerceIn(0.0, 1.0)
        
        val nearestX = x1 + clampedT * dx
        val nearestY = y1 + clampedT * dy
        
        return kotlin.math.sqrt((px - nearestX) * (px - nearestX) + (py - nearestY) * (py - nearestY))
    }
    
    /**
     * Cleanup на стари passed reports (fallback timeout)
     */
    private fun cleanupOldPassedReports() {
        val now = System.currentTimeMillis()
        val toRemove = passedReports.filter { (_, timestamp) ->
            now - timestamp > PASS_TIMEOUT_MS
        }.keys
        
        toRemove.forEach { reportId ->
            passedReports.remove(reportId)
            respondedReports.add(reportId) // Mark as responded (timeout)
            reportTrackingStates.remove(reportId)
        }
    }
    
    /**
     * Clear всички state (при stop navigation или destroy)
     */
    fun clearAllState() {
        hideActiveAlert() // Hide UI if visible
        alertedReports.clear()
        passedReports.clear()
        respondedReports.clear()
        reportTrackingStates.clear()
    }
    
    /**
     * Mark report като responded (след user interaction)
     */
    fun markAsResponded(reportId: String) {
        respondedReports.add(reportId)
    }
    
    // === Utility functions ===
    
    private fun speakMessage(message: String) {
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    
    private fun vibrate(durationMs: Long) {
        vibrator?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(durationMs)
            }
        }
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
    
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val lonDiff = Math.toRadians(lon2 - lon1)
        
        val y = sin(lonDiff) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(lonDiff)
        
        val bearing = Math.toDegrees(atan2(y, x))
        return ((bearing + 360) % 360).toFloat()
    }
    
    private fun normalizeBearing(bearing: Float): Float {
        var b = bearing % 360
        if (b > 180) b -= 360
        if (b < -180) b += 360
        return b
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        tts?.stop()
        tts?.shutdown()
        handler.removeCallbacksAndMessages(null)
        clearAllState()
    }
}
