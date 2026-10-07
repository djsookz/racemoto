package com.revix.app.reports

import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import com.revix.app.billing.ProAccess
import com.revix.app.main.location.LocationPermissionHelper
import com.revix.app.reports.data.PoliceReport
import com.revix.app.reports.service.ReportAlertsBackgroundService
import com.mapbox.geojson.LineString

/**
 * Shared state between MapFragment (foreground) and background alert service.
 * Alerts are armed only while the user is on the Map tab.
 * Background continuation (screen off / other apps) is Pro / trial only.
 */
object ReportAlertsCoordinator {
    private const val TAG = "ReportAlertsCoordinator"

    @Volatile
    var mapTabActive: Boolean = false
        private set

    @Volatile
    var armedForBackground: Boolean = false
        private set

    @Volatile
    var hasActiveNavigationRoute: Boolean = false
        private set

    @Volatile
    var routeGeometry: LineString? = null
        private set

    @Volatile
    var cachedReports: List<PoliceReport> = emptyList()
        private set

    private var integration: ReportsIntegration? = null

    fun attach(integration: ReportsIntegration) {
        this.integration = integration
    }

    fun detach(integration: ReportsIntegration) {
        if (this.integration === integration) {
            this.integration = null
        }
    }

    fun onMapTabSelected(context: Context) {
        mapTabActive = true
        armedForBackground = true
        stopBackgroundService(context)
        integration?.setAlertsEnabled(true)
        Log.d(TAG, "Map tab selected - alerts armed")
    }

    fun onMapTabHidden(context: Context) {
        mapTabActive = false
        armedForBackground = false
        stopBackgroundService(context)
        integration?.setAlertsEnabled(false)
        Log.d(TAG, "Map tab hidden - alerts disarmed")
    }

    fun onAppMovedToBackground(context: Context) {
        if (!armedForBackground || !mapTabActive) {
            return
        }
        if (!ProAccess.hasFullAccess(context)) {
            Log.d(TAG, "Background report alerts skipped - Pro/trial required")
            return
        }
        if (!LocationPermissionHelper.hasAll(context)) {
            Log.w(TAG, "Background report alerts skipped - missing location/FGS permissions")
            return
        }
        // Arm alert engine before the service starts delivering locations.
        integration?.setBackgroundMode(true)
        startBackgroundService(context)
        Log.d(TAG, "App backgrounded from map - background alerts started")
    }

    fun onAppReturnedToForeground(context: Context) {
        stopBackgroundService(context)
        integration?.setBackgroundMode(false)
        integration?.showPendingConfirmations()
        Log.d(TAG, "App foregrounded - background alerts stopped")
    }

    /** Full shutdown when the user leaves the app (back-to-exit, task removed, activity destroyed). */
    fun disarmAll(context: Context) {
        mapTabActive = false
        armedForBackground = false
        hasActiveNavigationRoute = false
        routeGeometry = null
        cachedReports = emptyList()
        integration?.setBackgroundMode(false)
        integration?.setAlertsEnabled(false)
        stopBackgroundService(context)
        Log.d(TAG, "All report alerts disarmed")
    }

    fun updateReports(reports: List<PoliceReport>) {
        cachedReports = reports
    }

    fun updateNavigationRoute(active: Boolean, geometry: LineString?) {
        hasActiveNavigationRoute = active
        routeGeometry = geometry
    }

    fun processBackgroundLocation(context: Context, location: Location) {
        if (!ProAccess.hasFullAccess(context)) {
            stopBackgroundService(context)
            return
        }
        val reportsIntegration = integration
        if (reportsIntegration == null) {
            Log.w(TAG, "Background location ignored - reports integration not attached")
            return
        }
        reportsIntegration.checkForDrivingAlerts(
            location = location,
            bearing = location.bearing,
            forceBackground = true
        )
    }

    private fun startBackgroundService(context: Context) {
        val intent = Intent(context, ReportAlertsBackgroundService::class.java)
        try {
            ContextCompatStartForegroundService.start(context, intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start background report alerts service", e)
        }
    }

    fun stopBackgroundService(context: Context) {
        val stopIntent = Intent(context, ReportAlertsBackgroundService::class.java).apply {
            action = ReportAlertsBackgroundService.ACTION_STOP
        }
        context.stopService(stopIntent)
    }
}

private object ContextCompatStartForegroundService {
    fun start(context: Context, intent: Intent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
