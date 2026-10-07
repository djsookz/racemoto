package com.revix.app.reports.ui

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.revix.app.audio.AppAudioFocus
import com.revix.app.navigation.NavigationVoiceGuidance
import com.revix.app.reports.ReportRouteMatching
import com.revix.app.reports.data.PoliceReport
import com.revix.app.reports.data.ReportType
import com.revix.app.settings.LanguageManager
import com.revix.app.settings.VoiceAlertsSettings
import com.google.firebase.auth.FirebaseAuth
import com.mapbox.geojson.LineString
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Waze-style report alerts.
 *
 * Core ideas (matching real Waze behaviour):
 * - "Direction" is derived from whether you are APPROACHING the report (distance shrinking),
 *   not from the noisy instantaneous GPS bearing. This is what makes it reliable.
 * - On-road check: navigation uses cross-track to the route; free drive uses lateral offset
 *   to your travelled path, so parallel/side streets are ignored.
 * - Voice + panel fire once when you first enter alert range (threshold locked from speed at entry).
 * - "Still there?" appears ~4 seconds before the report at current speed (min 30 m).
 */
class ReportAlertsManager(
    private val context: Context,
    private val onAlertShow: (PoliceReport, Float) -> Unit,
    private val onAlertUpdate: (PoliceReport, Float) -> Unit,
    private val onAlertHide: () -> Unit,
    private val onConfirmationNeeded: (PoliceReport, () -> Unit) -> Unit
) {
    private enum class ReportPhase {
        WATCHING,
        ALERTED,
        PASSED
    }

    private data class ReportSession(
        var phase: ReportPhase = ReportPhase.WATCHING,
        var minDistance: Float = Float.MAX_VALUE,
        var lastDistance: Float = Float.MAX_VALUE,
        var minLateral: Float = Float.MAX_VALUE,
        var firstAnnounced: Boolean = false,
        var confirmationShown: Boolean = false,
        var sawBeyondFirstAlertRange: Boolean = false,
        var lockedFirstAlertMeters: Float? = null,
        var irrelevantTicks: Int = 0,
        val distanceHistory: ArrayDeque<Float> = ArrayDeque(6)
    )

    /** Route-relative snapshot used only during turn-by-turn navigation. */
    private data class NavRouteContext(
        val aheadMeters: Float
    )

    private var tts: TextToSpeech? = null
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val handler = Handler(Looper.getMainLooper())

    private val sessions = mutableMapOf<String, ReportSession>()
    private val pendingConfirmations = ArrayDeque<PoliceReport>()

    private var activeAlertReportId: String? = null

    private var alertsEnabled = false
    private var backgroundMode = false
    private var hasNavigationRoute = false
    private var currentRoute: LineString? = null
    private val recentTrail = ArrayDeque<Location>(12)

    companion object {
        private const val TAG = "ReportAlertsManager"

        // Distances
        private const val MAX_ALERT_DISTANCE_METERS = 1200f
        private const val SESSION_RESET_DISTANCE_METERS = 2000f
        private const val ROUTE_TOLERANCE_METERS = 55.0

        // Perpendicular distance from your line of travel to the report. Own road (incl. the
        // opposite lane + GPS error) is typically < ~25 m; a parallel city street is usually
        // >= ~40 m, so this threshold separates them.
        private const val FREE_DRIVE_LATERAL_MAX_METERS = 32f

        // The "Still there?" prompt only appears once the report has genuinely lined up with our
        // path at least once (stricter than the alert), so a parallel street never triggers it.
        private const val CONFIRM_ALIGN_METERS = 24f

        // Approaching detection
        private const val APPROACH_EPSILON_METERS = 6f
        private const val TRAIL_MIN_DISTANCE_METERS = 4f

        // We only trust "same road / ahead" once we have actually moved a bit, so a car sitting
        // still next to a report on a parallel street never gets an alert.
        private const val HEADING_MIN_TRAIL_SPAN_METERS = 14f

        // Pass / hide detection (only to hide the panel; confirmation is shown earlier)
        private const val PASS_GOT_CLOSE_METERS = 140f
        private const val PASS_AWAY_MARGIN_METERS = 40f
        private const val PASS_ALONG_ROUTE_METERS = 35.0

        // Hysteresis
        private const val IRRELEVANT_TICKS_TO_DROP = 6

        private const val MIN_SPEED_FOR_BEARING_MPS = 1.5f

        // First alert distance tiers (locked when the report becomes relevant / on-route).
        private const val FIRST_ALERT_METERS_UNDER_30_KMH = 100f
        private const val FIRST_ALERT_METERS_30_KMH = 200f
        private const val FIRST_ALERT_METERS_50_KMH = 250f
        private const val FIRST_ALERT_METERS_90_KMH = 400f
        private const val FIRST_ALERT_METERS_140_KMH = 600f

        // "Still there?" lead time from current speed.
        private const val CONFIRMATION_LEAD_SECONDS = 4f
        private const val CONFIRMATION_MIN_DISTANCE_METERS = 30f
    }

    init {
        initializeTts()
    }

    fun setDrivingContext(
        enabled: Boolean,
        navigationRouteActive: Boolean = hasNavigationRoute,
        routeGeometry: LineString? = currentRoute,
        background: Boolean = backgroundMode
    ) {
        val wasEnabled = alertsEnabled
        alertsEnabled = enabled
        backgroundMode = background
        hasNavigationRoute = navigationRouteActive
        currentRoute = if (navigationRouteActive) routeGeometry else null
        if (!enabled && wasEnabled) {
            clearAllState()
        }
    }

    fun setNavigationState(isActive: Boolean, routeGeometry: LineString? = null) {
        setDrivingContext(
            enabled = alertsEnabled,
            navigationRouteActive = isActive,
            routeGeometry = routeGeometry,
            background = backgroundMode
        )
    }

    fun setBackgroundMode(background: Boolean) {
        backgroundMode = background
    }

    fun drainPendingConfirmations(): List<PoliceReport> {
        if (pendingConfirmations.isEmpty()) return emptyList()
        val pending = pendingConfirmations.toList()
        pendingConfirmations.clear()
        return pending
    }

    fun markAsResponded(reportId: String) {
        sessions[reportId]?.confirmationShown = true
    }

    fun checkForAlerts(
        currentLocation: Location,
        currentBearing: Float,
        reports: List<PoliceReport>
    ) {
        if (!alertsEnabled) return

        appendTrailSample(currentLocation)
        val movementBearing = resolveMovementBearing(currentLocation, currentBearing)
        val speedMps = if (currentLocation.hasSpeed()) currentLocation.speed else 0f

        var panelReport: PoliceReport? = null
        var panelDistance = Float.MAX_VALUE
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val navigating = hasNavigationRoute && currentRoute != null

        reports.forEach { report ->
            if (report.getScore() < -2) return@forEach
            if (currentUserId != null &&
                report.reporterUserId.isNotEmpty() &&
                report.reporterUserId == currentUserId
            ) {
                return@forEach
            }

            val session = sessions.getOrPut(report.id) { ReportSession() }
            val panelPick = if (navigating) {
                evaluateNavigationReport(
                    report = report,
                    session = session,
                    currentLocation = currentLocation,
                    speedMps = speedMps
                )
            } else {
                evaluateFreeDriveReport(
                    report = report,
                    session = session,
                    currentLocation = currentLocation,
                    movementBearing = movementBearing,
                    speedMps = speedMps
                )
            }

            if (panelPick != null) {
                panelReport = pickPanel(panelPick.first, panelPick.second, panelReport, panelDistance)
                panelDistance = minOf(panelDistance, panelPick.second)
            }
        }

        updatePanel(panelReport, panelDistance)
    }

    /**
     * Navigation: all distances and pass detection follow the active route polyline.
     */
    private fun evaluateNavigationReport(
        report: PoliceReport,
        session: ReportSession,
        currentLocation: Location,
        speedMps: Float
    ): Pair<PoliceReport, Float>? {
        if (session.phase == ReportPhase.PASSED) {
            return null
        }

        if (hasPassedOnRoute(report, currentLocation)) {
            if (session.phase == ReportPhase.ALERTED) {
                markPassed(report, session)
            } else {
                session.phase = ReportPhase.PASSED
            }
            return null
        }

        val routeContext = resolveNavRouteContext(currentLocation, report)
        if (routeContext == null) {
            if (session.phase == ReportPhase.ALERTED) {
                session.irrelevantTicks++
                if (session.irrelevantTicks >= IRRELEVANT_TICKS_TO_DROP) {
                    dropAlert(report, session)
                    return null
                }
                // Brief projection gap (roundabout geometry) — keep last known distance on panel.
                val fallbackDistance = session.minDistance.takeIf { it != Float.MAX_VALUE } ?: return null
                return if (!session.confirmationShown) report to fallbackDistance else null
            }
            return null
        }

        session.irrelevantTicks = 0
        val aheadMeters = routeContext.aheadMeters
        lockFirstAlertThresholdIfNeeded(session, speedMps, lock = true)
        val firstAlertMeters = firstAlertThreshold(session, speedMps)

        if (aheadMeters > SESSION_RESET_DISTANCE_METERS) {
            resetSession(session)
            return null
        }

        pushDistance(session, aheadMeters)
        if (aheadMeters > firstAlertMeters) {
            session.sawBeyondFirstAlertRange = true
        }
        session.minDistance = minOf(session.minDistance, aheadMeters)
        session.lastDistance = aheadMeters

        when (session.phase) {
            ReportPhase.WATCHING -> {
                if (aheadMeters <= firstAlertMeters &&
                    aheadMeters <= MAX_ALERT_DISTANCE_METERS &&
                    session.sawBeyondFirstAlertRange
                ) {
                    session.phase = ReportPhase.ALERTED
                    announceFirst(report, session, aheadMeters)
                    maybeShowConfirmation(
                        report, session, aheadMeters, speedMps, confirmAllowed = true
                    )
                }
            }

            ReportPhase.ALERTED -> {
                maybeShowConfirmation(
                    report, session, aheadMeters, speedMps, confirmAllowed = true
                )
            }

            ReportPhase.PASSED -> return null
        }

        return if (session.phase == ReportPhase.ALERTED && !session.confirmationShown) {
            report to aheadMeters
        } else {
            null
        }
    }

    /**
     * Free drive: straight-line distance, GPS trail heading, and lateral corridor (unchanged).
     */
    private fun evaluateFreeDriveReport(
        report: PoliceReport,
        session: ReportSession,
        currentLocation: Location,
        movementBearing: Float,
        speedMps: Float
    ): Pair<PoliceReport, Float>? {
        val straightDistance = calculateDistance(
            currentLocation.latitude, currentLocation.longitude,
            report.location.latitude, report.location.longitude
        )

        if (straightDistance > SESSION_RESET_DISTANCE_METERS) {
            resetSession(session)
            return null
        }

        pushDistance(session, straightDistance)

        if (session.phase == ReportPhase.PASSED) {
            session.lastDistance = straightDistance
            return null
        }

        session.minDistance = minOf(session.minDistance, straightDistance)

        val approaching = isApproaching(session, straightDistance)
        val lateral = freeDriveLateralOrNull(currentLocation, movementBearing, report, straightDistance)
        if (lateral != null && lateral < session.minLateral) {
            session.minLateral = lateral
        }

        val onRoad = lateral != null && lateral <= FREE_DRIVE_LATERAL_MAX_METERS
        val relevant = straightDistance <= MAX_ALERT_DISTANCE_METERS && approaching && onRoad
        val confirmAllowed = session.minLateral <= CONFIRM_ALIGN_METERS
        lockFirstAlertThresholdIfNeeded(session, speedMps, lock = relevant)
        val firstAlertMeters = firstAlertThreshold(session, speedMps)

        if (straightDistance > firstAlertMeters) {
            session.sawBeyondFirstAlertRange = true
        }

        if (relevant) {
            session.irrelevantTicks = 0
        } else if (session.phase == ReportPhase.ALERTED) {
            session.irrelevantTicks++
        }

        if (session.phase == ReportPhase.ALERTED && hasPassedFreeDrive(session, straightDistance)) {
            markPassed(report, session)
            session.lastDistance = straightDistance
            return null
        }

        when (session.phase) {
            ReportPhase.WATCHING -> {
                if (relevant && straightDistance <= firstAlertMeters && session.sawBeyondFirstAlertRange) {
                    session.phase = ReportPhase.ALERTED
                    announceFirst(report, session, straightDistance)
                    maybeShowConfirmation(
                        report, session, straightDistance, speedMps, confirmAllowed
                    )
                }
            }

            ReportPhase.ALERTED -> {
                if (session.irrelevantTicks >= IRRELEVANT_TICKS_TO_DROP) {
                    dropAlert(report, session)
                    session.lastDistance = straightDistance
                    return null
                }
                maybeShowConfirmation(
                    report, session, straightDistance, speedMps, confirmAllowed
                )
            }

            ReportPhase.PASSED -> return null
        }

        session.lastDistance = straightDistance

        if (session.phase == ReportPhase.ALERTED && !session.confirmationShown) {
            val panelDistance = when {
                relevant -> straightDistance
                session.lastDistance != Float.MAX_VALUE -> session.lastDistance
                else -> straightDistance
            }
            return report to panelDistance
        }

        return null
    }

    private fun resolveNavRouteContext(
        currentLocation: Location,
        report: PoliceReport
    ): NavRouteContext? {
        val route = currentRoute ?: return null

        val userCrossTrack = ReportRouteMatching.crossTrackDistanceMeters(
            currentLocation.latitude,
            currentLocation.longitude,
            route
        )
        if (userCrossTrack > ROUTE_TOLERANCE_METERS) {
            return null
        }

        val reportCrossTrack = ReportRouteMatching.crossTrackDistanceMeters(
            report.location.latitude,
            report.location.longitude,
            route
        )
        if (reportCrossTrack > ROUTE_TOLERANCE_METERS) {
            return null
        }

        val aheadMeters = ReportRouteMatching.distanceAlongRouteAheadMeters(
            currentLocation.latitude,
            currentLocation.longitude,
            report.location.latitude,
            report.location.longitude,
            route,
            ROUTE_TOLERANCE_METERS
        ) ?: return null

        return NavRouteContext(aheadMeters)
    }

    private fun hasPassedOnRoute(report: PoliceReport, currentLocation: Location): Boolean {
        val route = currentRoute ?: return false
        if (!reportNearRoute(report)) return false
        val pastMeters = ReportRouteMatching.metersUserPastReportOnRoute(
            currentLocation.latitude,
            currentLocation.longitude,
            report.location.latitude,
            report.location.longitude,
            route,
            ROUTE_TOLERANCE_METERS
        ) ?: return false
        return pastMeters > PASS_ALONG_ROUTE_METERS
    }

    private fun hasPassedFreeDrive(session: ReportSession, straightDistance: Float): Boolean {
        if (session.minDistance > PASS_GOT_CLOSE_METERS) return false
        return straightDistance >= session.minDistance + PASS_AWAY_MARGIN_METERS
    }

    private fun pickPanel(
        candidate: PoliceReport,
        candidateDistance: Float,
        current: PoliceReport?,
        currentDistance: Float
    ): PoliceReport {
        return if (current == null || candidateDistance < currentDistance) candidate else current
    }

    // ---- Announcements -------------------------------------------------------

    private fun announceFirst(report: PoliceReport, session: ReportSession, distance: Float) {
        if (session.firstAnnounced) return
        session.firstAnnounced = true
        announce(report, distance)
    }

    private fun announce(report: PoliceReport, distance: Float) {
        val reportType = report.getReportType()
        var playedClip = false
        if (VoiceAlertsSettings.isEnabled(context)) {
            NavigationVoiceGuidance.yieldToReportAlert()
            val language = LanguageManager.getLanguage(context)
            playedClip = ReportAlertSounds.play(context, reportType) {
                NavigationVoiceGuidance.onReportAlertFinished()
            }
            if (!playedClip) {
                speakMessage(ReportAlertSounds.fallbackMessage(reportType, language))
            }
        }
        vibrate(120)
        activeAlertReportId = report.id
        if (!backgroundMode) {
            handler.post { onAlertShow(report, distance) }
        }
        Log.d(TAG, "Alert ${reportType.name} at ${distance.toInt()}m (clip=$playedClip)")
    }

    /**
     * "Still there?" fires once when within ~4 seconds of travel time at current speed (min 30 m).
     */
    private fun maybeShowConfirmation(
        report: PoliceReport,
        session: ReportSession,
        distance: Float,
        speedMps: Float,
        confirmAllowed: Boolean
    ) {
        if (session.confirmationShown) return
        if (!confirmAllowed) return
        val confirmationDistance = resolveConfirmationDistanceMeters(speedMps)
        if (distance > confirmationDistance) return
        session.confirmationShown = true

        if (activeAlertReportId == report.id) {
            activeAlertReportId = null
            hidePanel()
        }

        if (backgroundMode) {
            pendingConfirmations.addLast(report)
            Log.d(
                TAG,
                "Confirmation queued (background) for ${report.id} at ${distance.toInt()}m (lead=${confirmationDistance.toInt()}m)"
            )
            return
        }

        handler.post {
            onConfirmationNeeded(report) {
                markAsResponded(report.id)
            }
        }
        Log.d(
            TAG,
            "Confirmation shown for ${report.id} at ${distance.toInt()}m (lead=${confirmationDistance.toInt()}m)"
        )
    }

    // ---- Panel ---------------------------------------------------------------

    private fun updatePanel(report: PoliceReport?, distance: Float) {
        if (report != null) {
            activeAlertReportId = report.id
            if (!backgroundMode) {
                handler.post { onAlertUpdate(report, distance) }
            }
            return
        }
        if (activeAlertReportId != null) {
            activeAlertReportId = null
            hidePanel()
        }
    }

    private fun hidePanel() {
        if (!backgroundMode) {
            handler.post { onAlertHide() }
        }
    }

    // ---- Phase transitions ---------------------------------------------------

    private fun markPassed(report: PoliceReport, session: ReportSession) {
        session.phase = ReportPhase.PASSED
        if (activeAlertReportId == report.id) {
            activeAlertReportId = null
            hidePanel()
        }
        Log.d(TAG, "Passed report ${report.id}")
    }

    private fun dropAlert(report: PoliceReport, session: ReportSession) {
        session.phase = ReportPhase.WATCHING
        session.irrelevantTicks = 0
        session.minDistance = Float.MAX_VALUE
        if (activeAlertReportId == report.id) {
            activeAlertReportId = null
            hidePanel()
        }
    }

    private fun resetSession(session: ReportSession) {
        session.phase = ReportPhase.WATCHING
        session.minDistance = Float.MAX_VALUE
        session.lastDistance = Float.MAX_VALUE
        session.minLateral = Float.MAX_VALUE
        session.firstAnnounced = false
        session.confirmationShown = false
        session.sawBeyondFirstAlertRange = false
        session.lockedFirstAlertMeters = null
        session.irrelevantTicks = 0
        session.distanceHistory.clear()
    }

    // ---- Geometry / relevance ------------------------------------------------

    private fun pushDistance(session: ReportSession, distance: Float) {
        if (session.distanceHistory.size >= 6) session.distanceHistory.removeFirst()
        session.distanceHistory.addLast(distance)
    }

    /**
     * Approaching = distance to the report is trending down. This is a robust stand-in for
     * "the report is ahead in my direction of travel" that does not depend on GPS bearing noise.
     */
    private fun isApproaching(session: ReportSession, currentDistance: Float): Boolean {
        val history = session.distanceHistory
        if (history.size >= 2) {
            val oldest = history.first()
            if (oldest - currentDistance >= APPROACH_EPSILON_METERS) return true
            if (currentDistance - oldest >= APPROACH_EPSILON_METERS) return false
        }
        // Not enough movement to decide from history: fall back to last-tick comparison.
        if (session.lastDistance != Float.MAX_VALUE) {
            return currentDistance <= session.lastDistance + 2f
        }
        return true
    }

    private fun reportNearRoute(report: PoliceReport): Boolean {
        val route = currentRoute ?: return false
        val reportCrossTrack = ReportRouteMatching.crossTrackDistanceMeters(
            report.location.latitude,
            report.location.longitude,
            route
        )
        return reportCrossTrack <= ROUTE_TOLERANCE_METERS
    }

    /**
     * Perpendicular distance from our line of travel to the report, in free drive.
     * Returns null when we cannot judge it reliably (no route context needed, but we require a
     * trustworthy heading), so a car standing still never counts anything as on-road.
     */
    private fun freeDriveLateralOrNull(
        currentLocation: Location,
        movementBearing: Float,
        report: PoliceReport,
        straightDistance: Float
    ): Float? {
        if (hasNavigationRoute && currentRoute != null) return null
        if (!hasReliableHeading()) return null
        return estimateLateralMeters(
            currentLocation.latitude,
            currentLocation.longitude,
            movementBearing,
            report.location.latitude,
            report.location.longitude,
            straightDistance
        )
    }

    /**
     * True only after we have travelled a short distance, giving us a trustworthy direction of
     * travel from the GPS trail (independent of the noisy instantaneous compass bearing).
     */
    private fun hasReliableHeading(): Boolean {
        val trail = recentTrail.toList()
        if (trail.size < 2) return false
        val span = trail.first().distanceTo(trail.last())
        return span >= HEADING_MIN_TRAIL_SPAN_METERS
    }

    private fun estimateLateralMeters(
        userLat: Double,
        userLon: Double,
        userBearing: Float,
        targetLat: Double,
        targetLon: Double,
        straightDistance: Float
    ): Float {
        val bearingToTarget = calculateBearing(userLat, userLon, targetLat, targetLon)
        val bearingDiff = abs(normalizeBearing(bearingToTarget - userBearing))
        return straightDistance * sin(Math.toRadians(bearingDiff.toDouble())).toFloat()
    }

    private fun resolveMovementBearing(currentLocation: Location, fallbackBearing: Float): Float {
        val trail = recentTrail.toList()
        if (trail.size >= 2) {
            val from = trail[trail.size - 2]
            val segmentMeters = calculateDistance(
                from.latitude, from.longitude,
                currentLocation.latitude, currentLocation.longitude
            )
            if (segmentMeters >= 3f) {
                return calculateBearing(
                    from.latitude, from.longitude,
                    currentLocation.latitude, currentLocation.longitude
                )
            }
        }
        if (currentLocation.hasBearing() && currentLocation.speed >= MIN_SPEED_FOR_BEARING_MPS) {
            return currentLocation.bearing
        }
        if (fallbackBearing != 0f) return fallbackBearing
        if (currentLocation.hasBearing()) return currentLocation.bearing
        return fallbackBearing
    }

    private fun lockFirstAlertThresholdIfNeeded(session: ReportSession, speedMps: Float, lock: Boolean) {
        if (!lock || session.lockedFirstAlertMeters != null) return
        session.lockedFirstAlertMeters = resolveFirstAlertMeters(speedMps)
    }

    private fun firstAlertThreshold(session: ReportSession, speedMps: Float): Float {
        return session.lockedFirstAlertMeters ?: resolveFirstAlertMeters(speedMps)
    }

    private fun resolveFirstAlertMeters(speedMps: Float): Float {
        val speedKmh = speedMps * 3.6f
        return when {
            speedKmh >= 140f -> FIRST_ALERT_METERS_140_KMH
            speedKmh >= 90f -> FIRST_ALERT_METERS_90_KMH
            speedKmh >= 50f -> FIRST_ALERT_METERS_50_KMH
            speedKmh >= 30f -> FIRST_ALERT_METERS_30_KMH
            else -> FIRST_ALERT_METERS_UNDER_30_KMH
        }
    }

    private fun resolveConfirmationDistanceMeters(speedMps: Float): Float {
        return max(CONFIRMATION_MIN_DISTANCE_METERS, speedMps * CONFIRMATION_LEAD_SECONDS)
    }

    private fun appendTrailSample(location: Location) {
        val last = recentTrail.lastOrNull()
        if (last != null && last.distanceTo(location) < TRAIL_MIN_DISTANCE_METERS) return
        if (recentTrail.size >= 12) recentTrail.removeFirst()
        recentTrail.addLast(Location(location))
    }

    fun clearAllState() {
        hidePanel()
        sessions.clear()
        pendingConfirmations.clear()
        recentTrail.clear()
        activeAlertReportId = null
    }

    fun cleanup() {
        ReportAlertSounds.stop()
        NavigationVoiceGuidance.onReportAlertFinished()
        tts?.stop()
        tts?.shutdown()
        handler.removeCallbacksAndMessages(null)
        clearAllState()
    }

    // ---- Low level helpers ---------------------------------------------------

    private fun initializeTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                applyTtsLanguageFromSettings()
            }
        }
    }

    private fun applyTtsLanguageFromSettings(): Boolean {
        val locale = LanguageManager.getLocaleForLanguage(LanguageManager.getLanguage(context))
        val result = tts?.setLanguage(locale) ?: return false
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = tts?.setLanguage(Locale.ENGLISH) ?: return false
            return fallback != TextToSpeech.LANG_MISSING_DATA && fallback != TextToSpeech.LANG_NOT_SUPPORTED
        }
        return true
    }

    private fun speakMessage(message: String) {
        if (!VoiceAlertsSettings.isEnabled(context)) {
            NavigationVoiceGuidance.onReportAlertFinished()
            return
        }
        applyTtsLanguageFromSettings()
        val engine = tts
        if (engine == null) {
            NavigationVoiceGuidance.onReportAlertFinished()
            return
        }
        val utteranceId = "report_alert_${System.currentTimeMillis()}"
        AppAudioFocus.request(context)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(spokenId: String?) {
                if (spokenId == utteranceId) {
                    AppAudioFocus.abandon()
                    NavigationVoiceGuidance.onReportAlertFinished()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(spokenId: String?) {
                if (spokenId == utteranceId) {
                    AppAudioFocus.abandon()
                    NavigationVoiceGuidance.onReportAlertFinished()
                }
            }
        })
        engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
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
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }

    private fun normalizeBearing(bearing: Float): Float {
        var value = bearing % 360f
        if (value > 180f) value -= 360f
        if (value < -180f) value += 360f
        return value
    }
}
