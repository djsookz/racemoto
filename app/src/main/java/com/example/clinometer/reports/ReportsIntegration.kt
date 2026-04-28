package com.example.clinometer.reports

import android.animation.ValueAnimator
import android.view.View
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.example.clinometer.reports.data.FirebaseReportsRepository
import com.example.clinometer.reports.data.PoliceReport
import com.example.clinometer.reports.data.ReportType
import com.example.clinometer.reports.ui.ReportAlertsManager
import com.example.clinometer.reports.ui.ReportBottomSheet
import com.example.clinometer.reports.ui.ReportConfirmationSheet
import com.example.clinometer.reports.ui.ReportsMapManager
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
    
    private var isInitialized = false
    
    companion object {
        private const val TAG = "ReportsIntegration"
        private const val CLEANUP_INTERVAL_MS = 300_000L // 5 минути
        private const val DEFAULT_RADIUS_KM = 100.0 // Максимален радиус за POLICE/CAMERA
    }
    
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
                        mapManager.updateReports(reports)
                        Log.d(TAG, "Displaying ${reports.size} reports on map")
                    }
                }
        }
        
        Log.d(TAG, "Started observing reports at ($centerLatitude, $centerLongitude) with radius $radiusKm km")
    }
    
    /**
     * Set navigation state за alerts
     * @param isActive Дали имаме активна навигация
     * @param routeGeometry Route линията (за on-route check)
     */
    fun setNavigationState(isActive: Boolean, routeGeometry: LineString? = null) {
        alertsManager.setNavigationState(isActive, routeGeometry)
        Log.d(TAG, "Navigation state: $isActive")
    }
    
    /**
     * Проверка за alerts при GPS update (само ако има навигация)
     * @param location Текуща GPS позиция
     * @param bearing Текущ bearing (посока)
     */
    fun checkForNavigationAlerts(location: Location, bearing: Float) {
        if (currentReports.isNotEmpty()) {
            Log.d(TAG, "Checking navigation alerts: ${currentReports.size} reports cached")
            alertsManager.checkForAlerts(location, bearing, currentReports)
        } else {
            Log.d(TAG, "No reports cached for alerts check")
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
            voteReport(reportId, isUpvote)
        }
        bottomSheet.show(activity.supportFragmentManager, "VoteReportSheet")
    }
    
    /**
     * Показва confirmation prompt след преминаване покрай репорт
     */
    private fun showConfirmationPrompt(report: PoliceReport, onDismiss: () -> Unit) {
        val confirmSheet = ReportConfirmationSheet.newInstance(
            report = report,
            onResponse = { isStillThere ->
                // Гласува според отговора
                voteReport(report.id, isUpvote = isStillThere)
                alertsManager.markAsResponded(report.id)
                onDismiss()
            },
            onDismiss = {
                // Timeout или dismiss без отговор
                alertsManager.markAsResponded(report.id)
                onDismiss()
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
                            showToast("Докладът е създаден успешно")
                            Log.d(TAG, "Created report: ${outcome.reportId}")
                        }

                        FirebaseReportsRepository.CreateReportStatus.MERGED_UPVOTED -> {
                            showToast("Вече има такъв доклад наблизо. Добавено е потвърждение.")
                            Log.d(TAG, "Merged report via upvote: ${outcome.reportId}")
                        }

                        FirebaseReportsRepository.CreateReportStatus.MERGED_ALREADY_VOTED -> {
                            showToast("Вече сте потвърдили този доклад.")
                            Log.d(TAG, "Merge target already voted: ${outcome.reportId}")
                        }

                        FirebaseReportsRepository.CreateReportStatus.RATE_LIMIT_EXCEEDED -> {
                            showToast("Достигнахте лимита: до 10 доклада на час.")
                            Log.w(TAG, "Create report blocked by rate limit")
                        }

                        FirebaseReportsRepository.CreateReportStatus.INVALID_LOCATION -> {
                            showToast("Невалидна локация за доклад")
                            Log.w(TAG, "Create report blocked by invalid location")
                        }

                        FirebaseReportsRepository.CreateReportStatus.ERROR -> {
                            showToast("Грешка при създаване на доклад")
                            Log.e(TAG, "Failed to create report")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while creating report", e)
                withContext(Dispatchers.Main) {
                    showToast("Грешка: ${e.message}")
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
                        val voteType = if (isUpvote) "потвърден" else "оспорен"
                        showToast("Докладът е $voteType")
                        Log.d(TAG, "Voted for report: $reportId (upvote: $isUpvote)")
                    } else {
                        showToast("Вече сте гласували за този доклад")
                        Log.w(TAG, "User already voted for report: $reportId")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while voting", e)
                withContext(Dispatchers.Main) {
                    showToast("Грешка при гласуване")
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
        val tvIcon = container.findViewById<android.widget.TextView>(
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
        tvIcon?.text = reportType.icon
        tvDistance?.text = "${distance.toInt()}м"
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
        tvDistance?.text = "${distance.toInt()}м"

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
        stopPoliceBeaconAnimation()
        mapManager.cleanup()
        alertsManager.cleanup()
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
