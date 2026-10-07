package com.revix.app.reports

import android.animation.ValueAnimator
import android.view.View
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.revix.app.R
import com.revix.app.reports.ReportAlertsCoordinator
import com.revix.app.reports.data.FirebaseReportsRepository
import com.revix.app.reports.data.PoliceReport
import com.revix.app.reports.data.ReportType
import com.revix.app.reports.ui.ReportAlertsManager
import com.revix.app.reports.ui.ReportBottomSheet
import com.revix.app.reports.ui.ReportConfirmationSheet
import com.revix.app.reports.ui.ReportsMapManager
import com.mapbox.geojson.LineString
import com.mapbox.maps.MapView
import android.location.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main integration point за reports системата
 * Опростява използването на Firebase reports в приложението
 * 
 * Пример за използване:
 * ```
 * val reportsIntegration = ReportsIntegration(this, mapView)
 * reportsIntegration.initialize()
 * reportsIntegration.startObservingReports(latitude, longitude)
 * reportsIntegration.showCreateReportDialog(latitude, longitude)
 * ```
 */
class ReportsIntegration(
    private val activity: FragmentActivity,
    private val mapView: MapView
) {
    private val repository = FirebaseReportsRepository()
    private val mapManager = ReportsMapManager(mapView)
    
    // Navigation alerts manager
    private val alertsManager = ReportAlertsManager(
        activity,
        onAlertShow = { report, distance ->
            showAlertPanel(report, distance)
        },
        onAlertUpdate = { report, distance ->
            updateAlertPanel(report, distance)
        },
        onAlertHide = {
            hideAlertPanel()
        },
        onConfirmationNeeded = { report, onDismiss ->
            showConfirmationPrompt(report, onDismiss)
        }
    )
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null
    private var cleanupJob: Job? = null
    
    // Кеш на докладите за voting и alerts
    private var currentReports: List<PoliceReport> = emptyList()
    private var policeBeaconAnimator: ValueAnimator? = null
    private var flashingBeaconLeft: View? = null
    private var flashingBeaconRight: View? = null
    private val confirmationQueue = ArrayDeque<Pair<PoliceReport, () -> Unit>>()
    
    private var isInitialized = false
    private var routePreviewReportsActive = false
    
    companion object {
        private const val TAG = "ReportsIntegration"
        private const val CLEANUP_INTERVAL_MS = 300_000L // 5 минути
        private const val DEFAULT_RADIUS_KM = 20.0
    }

    fun isRoutePreviewReportsActive(): Boolean = routePreviewReportsActive
    
    /**
     * Инициализира reports системата
     * Трябва да се извика преди използване
     */
    fun initialize() {
        if (isInitialized) {
            Log.w(TAG, "Already initialized")
            return
        }
        
        try {
            mapManager.initialize()
            
            // Set click listener за voting
            mapManager.setOnReportClickListener { reportId ->
                val report = currentReports.find { it.id == reportId }
                if (report != null) {
                    showVoteDialog(report)
                } else {
                    Log.w(TAG, "Clicked report not found in cache: $reportId")
                }
            }
            
            startPeriodicCleanup()
            isInitialized = true
            ReportAlertsCoordinator.attach(this)
            Log.d(TAG, "ReportsIntegration initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize", e)
        }
    }
    
    /**
     * Започва наблюдение на доклади в радиус около дадена позиция
     * Автоматично обновява картата при промени
     */
    fun startObservingReports(
        centerLatitude: Double,
        centerLongitude: Double,
        radiusKm: Double = DEFAULT_RADIUS_KM
    ) {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized. Call initialize() first")
            return
        }
        if (routePreviewReportsActive) {
            Log.d(TAG, "Skipping nearby observe — along-route reports are active")
            return
        }
        
        // Спираме предишното наблюдение ако има
        observeJob?.cancel()
        
        observeJob = scope.launch {
            repository.observeNearbyReports(centerLatitude, centerLongitude, radiusKm)
                .catch { e ->
                    Log.e(TAG, "Error observing reports", e)
                }
                .collect { reports ->
                    withContext(Dispatchers.Main) {
                        currentReports = reports // Запазваме в кеша
                        ReportAlertsCoordinator.updateReports(reports)
                        mapManager.updateReports(reports)
                        Log.d(TAG, "Displaying ${reports.size} reports on map")
                    }
                }
        }
        
        Log.d(TAG, "Started observing reports at ($centerLatitude, $centerLongitude) with radius $radiusKm km")
    }

    /**
     * Show POLICE/CAMERA markers along the route corridor during **route preview only**.
     * Navigation / follow-line must call [resumeNearbyReportsObservation] so icons stay on the
     * live Firebase nearby listener.
     * @param previewCompactSizing legacy zoom-adaptive compact sizing; keep false (full size).
     */
    fun showReportsAlongRoute(route: LineString, previewCompactSizing: Boolean = false) {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized. Call initialize() first")
            return
        }

        routePreviewReportsActive = true
        observeJob?.cancel()
        observeJob = scope.launch {
            try {
                val reports = withContext(Dispatchers.IO) {
                    repository.fetchPoliceAndCameraAlongRoute(route)
                }
                if (!routePreviewReportsActive) return@launch
                currentReports = reports
                ReportAlertsCoordinator.updateReports(reports)
                mapManager.updateReports(reports, routePreviewAdaptive = previewCompactSizing)
                Log.d(
                    TAG,
                    "Along-route displaying ${reports.size} police/camera reports " +
                        "(previewCompactSizing=$previewCompactSizing)"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load along-route reports", e)
            }
        }
    }

    /** Toggle zoom-adaptive compact marker sizing (normally kept off). */
    fun setAlongRoutePreviewMarkerSizing(enabled: Boolean) {
        mapManager.setRoutePreviewAdaptiveSizing(enabled)
    }

    /**
     * Leave along-route mode and restore the normal nearby radius listener.
     */
    fun resumeNearbyReportsObservation(centerLatitude: Double, centerLongitude: Double) {
        routePreviewReportsActive = false
        observeJob?.cancel()
        mapManager.setRoutePreviewAdaptiveSizing(false)
        startObservingReports(centerLatitude, centerLongitude, DEFAULT_RADIUS_KM)
    }

    /** Exit along-route mode without starting a nearby query (no GPS yet). */
    fun exitRoutePreviewReports() {
        if (!routePreviewReportsActive) return
        routePreviewReportsActive = false
        observeJob?.cancel()
        currentReports = emptyList()
        ReportAlertsCoordinator.updateReports(emptyList())
        mapManager.updateReports(emptyList(), routePreviewAdaptive = false)
    }
    
    fun setAlertsEnabled(enabled: Boolean) {
        alertsManager.setDrivingContext(
            enabled = enabled,
            navigationRouteActive = ReportAlertsCoordinator.hasActiveNavigationRoute,
            routeGeometry = ReportAlertsCoordinator.routeGeometry,
            background = false
        )
        Log.d(TAG, "Alerts enabled: $enabled")
    }

    fun setBackgroundMode(background: Boolean) {
        alertsManager.setBackgroundMode(background)
        alertsManager.setDrivingContext(
            enabled = true,
            navigationRouteActive = ReportAlertsCoordinator.hasActiveNavigationRoute,
            routeGeometry = ReportAlertsCoordinator.routeGeometry,
            background = background
        )
    }

    fun updateRouteGeometry(routeGeometry: LineString?, navigationRouteActive: Boolean) {
        ReportAlertsCoordinator.updateNavigationRoute(navigationRouteActive, routeGeometry)
        alertsManager.setDrivingContext(
            enabled = true,
            navigationRouteActive = navigationRouteActive,
            routeGeometry = routeGeometry,
            background = false
        )
    }

    /**
     * Set navigation state за alerts
     */
    fun setNavigationState(isActive: Boolean, routeGeometry: LineString? = null) {
        updateRouteGeometry(routeGeometry, isActive)
        Log.d(TAG, "Navigation state: $isActive")
    }
    
    fun checkForDrivingAlerts(
        location: Location,
        bearing: Float,
        forceBackground: Boolean = false
    ) {
        if (forceBackground) {
            alertsManager.setBackgroundMode(true)
        }
        val reports = currentReports.ifEmpty { ReportAlertsCoordinator.cachedReports }
        if (reports.isNotEmpty()) {
            alertsManager.checkForAlerts(location, bearing, reports)
        }
    }

    fun checkForNavigationAlerts(location: Location, bearing: Float) {
        checkForDrivingAlerts(location, bearing)
    }

    fun showPendingConfirmations() {
        alertsManager.drainPendingConfirmations().forEach { report ->
            hideAlertPanel()
            showConfirmationPrompt(report) {
                alertsManager.markAsResponded(report.id)
            }
        }
    }
    
    /**
     * Показва диалог за създаване на нов доклад
     */
    fun showCreateReportDialog(
        latitude: Double,
        longitude: Double,
        mergeDistanceMeters: Double? = null
    ) {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized. Call initialize() first")
            return
        }
        
        val bottomSheet = ReportBottomSheet.newReportSheet()
        bottomSheet.setOnReportCreatedListener { reportType ->
            createReport(reportType, latitude, longitude, mergeDistanceMeters)
        }
        bottomSheet.show(activity.supportFragmentManager, "CreateReportSheet")
    }
    
    /**
     * Показва диалог за гласуване за съществуващ доклад
     */
    fun showVoteDialog(report: PoliceReport) {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized. Call initialize() first")
            return
        }
        
        val bottomSheet = ReportBottomSheet.voteSheet(report)
        bottomSheet.setOnVoteSubmittedListener { reportId, isUpvote ->
            if (!isUpvote) return@setOnVoteSubmittedListener
            val latest = currentReports.find { it.id == reportId } ?: report
            val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (currentUserId != null &&
                latest.reporterUserId.isNotEmpty() &&
                latest.reporterUserId == currentUserId
            ) {
                return@setOnVoteSubmittedListener
            }
            voteReport(reportId, isUpvote = true)
        }
        bottomSheet.show(activity.supportFragmentManager, "VoteReportSheet")
    }
    
    /**
     * Показва confirmation prompt след преминаване покрай репорт
     */
    private fun showConfirmationPrompt(report: PoliceReport, onDismiss: () -> Unit) {
        hideAlertPanel()
        confirmationQueue.addLast(report to onDismiss)
        showNextConfirmationIfNeeded()
    }

    private fun showNextConfirmationIfNeeded() {
        if (activity.supportFragmentManager.findFragmentByTag("ReportConfirmation") != null) {
            return
        }
        val next = confirmationQueue.removeFirstOrNull() ?: return
        val (report, onDismiss) = next

        val confirmSheet = ReportConfirmationSheet.newInstance(
            report = report,
            onResponse = { isStillThere ->
                voteReport(report.id, isUpvote = isStillThere)
                alertsManager.markAsResponded(report.id)
                onDismiss()
                showNextConfirmationIfNeeded()
            },
            onDismiss = {
                alertsManager.markAsResponded(report.id)
                onDismiss()
                showNextConfirmationIfNeeded()
            }
        )

        confirmSheet.show(activity.supportFragmentManager, "ReportConfirmation")
    }
    
    /**
     * Създава нов доклад
     */
    private fun createReport(
        type: ReportType,
        latitude: Double,
        longitude: Double,
        mergeDistanceMeters: Double? = null
    ) {
        scope.launch {
            try {
                val outcome = repository.createReport(
                    type = type,
                    latitude = latitude,
                    longitude = longitude,
                    mergeDistanceMeters = mergeDistanceMeters
                )
                
                withContext(Dispatchers.Main) {
                    when (outcome.status) {
                        FirebaseReportsRepository.CreateReportStatus.CREATED -> {
                            showToast(activity.getString(R.string.report_toast_created_success))
                            Log.d(TAG, "Created report: ${outcome.reportId}")
                        }

                        FirebaseReportsRepository.CreateReportStatus.MERGED_UPVOTED -> {
                            showToast(activity.getString(R.string.report_toast_merge_upvoted))
                            Log.d(TAG, "Merged report via upvote: ${outcome.reportId}")
                        }

                        FirebaseReportsRepository.CreateReportStatus.MERGED_ALREADY_VOTED -> {
                            showToast(activity.getString(R.string.report_toast_merge_already_voted))
                            Log.d(TAG, "Merge target already voted: ${outcome.reportId}")
                        }

                        FirebaseReportsRepository.CreateReportStatus.RATE_LIMIT_EXCEEDED -> {
                            showToast(activity.getString(R.string.report_toast_rate_limit))
                            Log.w(TAG, "Create report blocked by rate limit")
                        }

                        FirebaseReportsRepository.CreateReportStatus.INVALID_LOCATION -> {
                            showToast(activity.getString(R.string.report_toast_invalid_location))
                            Log.w(TAG, "Create report blocked by invalid location")
                        }

                        FirebaseReportsRepository.CreateReportStatus.ERROR -> {
                            showToast(activity.getString(R.string.report_toast_create_error))
                            Log.e(TAG, "Failed to create report")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while creating report", e)
                withContext(Dispatchers.Main) {
                    showToast(
                        activity.getString(
                            R.string.error_with_message,
                            e.message ?: activity.getString(R.string.error_unknown)
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Гласува за доклад
     */
    private fun voteReport(reportId: String, isUpvote: Boolean) {
        scope.launch {
            try {
                val success = repository.voteReport(reportId, isUpvote)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        val voteType = activity.getString(
                            if (isUpvote) R.string.report_vote_status_confirmed else R.string.report_vote_status_disputed
                        )
                        showToast(activity.getString(R.string.report_toast_voted, voteType))
                        Log.d(TAG, "Voted for report: $reportId (upvote: $isUpvote)")
                    } else {
                        showToast(activity.getString(R.string.report_toast_already_voted))
                        Log.w(TAG, "User already voted for report: $reportId")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while voting", e)
                withContext(Dispatchers.Main) {
                    showToast(activity.getString(R.string.report_toast_vote_error))
                }
            }
        }
    }
    
    /**
     * Стартира периодично изчистване на изтекли доклади
     */
    private fun startPeriodicCleanup() {
        cleanupJob = scope.launch {
            while (true) {
                delay(CLEANUP_INTERVAL_MS)
                try {
                    val cleaned = repository.cleanupExpiredReports()
                    Log.d(TAG, "Periodic cleanup: removed $cleaned expired reports")
                } catch (e: Exception) {
                    Log.e(TAG, "Cleanup failed", e)
                }
            }
        }
    }
    
    /**
     * Показва alert panel UI за репорт
     */
    private fun showAlertPanel(report: PoliceReport, distance: Float) {
        val container = activity.findViewById<android.view.ViewGroup>(
            activity.resources.getIdentifier("reportAlertContainer", "id", activity.packageName)
        ) ?: return
        
        // Set report data
        val tvIcon = container.findViewById<android.widget.ImageView>(
            activity.resources.getIdentifier("tvReportIcon", "id", activity.packageName)
        )
        val tvDistance = container.findViewById<android.widget.TextView>(
            activity.resources.getIdentifier("tvReportDistance", "id", activity.packageName)
        )
        val beaconLeft = container.findViewById<View>(
            activity.resources.getIdentifier("viewPoliceBeaconLeft", "id", activity.packageName)
        )
        val beaconRight = container.findViewById<View>(
            activity.resources.getIdentifier("viewPoliceBeaconRight", "id", activity.packageName)
        )
        
        val reportType = report.getReportType()
        tvIcon?.setImageResource(reportType.iconResId)
        tvDistance?.text = activity.getString(R.string.distance_meters_value, distance.toInt())
        updatePoliceBeaconState(reportType, beaconLeft, beaconRight)
        
        // Show with fade-in animation
        if (container.visibility != android.view.View.VISIBLE) {
            container.visibility = android.view.View.VISIBLE
            container.alpha = 0f
            container.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
        }
        
        Log.d(TAG, "Alert panel shown: ${reportType.name} at ${distance.toInt()}m")
    }
    
    /**
     * Обновява разстоянието в alert panel
     */
    private fun updateAlertPanel(report: PoliceReport, distance: Float) {
        val container = activity.findViewById<android.view.ViewGroup>(
            activity.resources.getIdentifier("reportAlertContainer", "id", activity.packageName)
        ) ?: return
        
        val tvDistance = container.findViewById<android.widget.TextView>(
            activity.resources.getIdentifier("tvReportDistance", "id", activity.packageName)
        )
        tvDistance?.text = activity.getString(R.string.distance_meters_value, distance.toInt())

        val beaconLeft = container.findViewById<View>(
            activity.resources.getIdentifier("viewPoliceBeaconLeft", "id", activity.packageName)
        )
        val beaconRight = container.findViewById<View>(
            activity.resources.getIdentifier("viewPoliceBeaconRight", "id", activity.packageName)
        )
        updatePoliceBeaconState(report.getReportType(), beaconLeft, beaconRight)
        
        // Show warning icon if very close (<100m)
        val tvWarning = container.findViewById<android.widget.TextView>(
            activity.resources.getIdentifier("tvWarningIcon", "id", activity.packageName)
        )
        tvWarning?.visibility = if (distance < 100f) android.view.View.VISIBLE else android.view.View.GONE
    }
    
    /**
     * Скрива alert panel UI
     */
    private fun hideAlertPanel() {
        val container = activity.findViewById<android.view.ViewGroup>(
            activity.resources.getIdentifier("reportAlertContainer", "id", activity.packageName)
        ) ?: return

        val beaconLeft = container.findViewById<View>(
            activity.resources.getIdentifier("viewPoliceBeaconLeft", "id", activity.packageName)
        )
        val beaconRight = container.findViewById<View>(
            activity.resources.getIdentifier("viewPoliceBeaconRight", "id", activity.packageName)
        )
        stopPoliceBeaconAnimation(beaconLeft, beaconRight)
        
        if (container.visibility == android.view.View.VISIBLE) {
            container.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    container.visibility = android.view.View.GONE
                    container.alpha = 1f // Reset за следващ път
                }
                .start()
            Log.d(TAG, "Alert panel hidden")
        }
    }
    
    /**
     * Почиства ресурси - трябва да се извика при destroy на Activity
     */
    fun cleanup() {
        observeJob?.cancel()
        cleanupJob?.cancel()
        routePreviewReportsActive = false
        stopPoliceBeaconAnimation()
        mapManager.cleanup()
        alertsManager.cleanup()
        confirmationQueue.clear()
        ReportAlertsCoordinator.detach(this)
        currentReports = emptyList()
        isInitialized = false
        Log.d(TAG, "ReportsIntegration cleaned up")
    }

    private fun updatePoliceBeaconState(reportType: ReportType, left: View?, right: View?) {
        if (reportType == ReportType.POLICE) {
            startPoliceBeaconAnimation(left, right)
        } else {
            stopPoliceBeaconAnimation(left, right)
        }
    }

    private fun startPoliceBeaconAnimation(left: View?, right: View?) {
        if (left == null || right == null) return

        flashingBeaconLeft = left
        flashingBeaconRight = right

        left.visibility = View.VISIBLE
        right.visibility = View.VISIBLE

        if (policeBeaconAnimator?.isRunning == true) return

        policeBeaconAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 360L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { animator ->
                val leftOn = animator.animatedFraction < 0.5f
                flashingBeaconLeft?.alpha = if (leftOn) 1f else 0.2f
                flashingBeaconRight?.alpha = if (leftOn) 0.2f else 1f
            }
            start()
        }
    }

    private fun stopPoliceBeaconAnimation(left: View? = flashingBeaconLeft, right: View? = flashingBeaconRight) {
        policeBeaconAnimator?.cancel()
        policeBeaconAnimator = null

        (left ?: flashingBeaconLeft)?.apply {
            alpha = 1f
            visibility = View.GONE
        }
        (right ?: flashingBeaconRight)?.apply {
            alpha = 1f
            visibility = View.GONE
        }

        flashingBeaconLeft = null
        flashingBeaconRight = null
    }
    
    /**
     * Показва Toast съобщение
     */
    private fun showToast(message: String) {
        android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
