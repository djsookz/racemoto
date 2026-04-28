package com.example.clinometer.main.session

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.SystemClock
import android.widget.Chronometer
import com.example.clinometer.ForegroundService
import com.example.clinometer.R
import com.example.clinometer.main.MainContainerActivity

object MainServiceCoordinator {
    fun createNormalModeServiceIntent(context: Context): Intent {
        return Intent(context, ForegroundService::class.java).apply {
            putExtra("ACTIVATE_NORMAL_MODE", true)
        }
    }

    fun startForegroundAndBind(context: Context, serviceConnection: ServiceConnection) {
        val serviceIntent = createNormalModeServiceIntent(context)
        context.startService(serviceIntent)
        context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun applyChronometerStart(
        startTime: Long,
        mainChronometer: Chronometer?,
        carChronometer: Chronometer?,
        carLandscapeChronometer: Chronometer?
    ) {
        mainChronometer?.base = startTime
        mainChronometer?.start()

        carChronometer?.base = startTime
        carChronometer?.start()

        carLandscapeChronometer?.base = startTime
        carLandscapeChronometer?.start()
    }

    fun safeCleanup(context: Context, serviceBound: Boolean, serviceConnection: ServiceConnection): Boolean {
        try {
            if (serviceBound) {
                try {
                    context.unbindService(serviceConnection)
                } catch (_: IllegalArgumentException) {
                }
            }
            try {
                context.stopService(Intent(context, ForegroundService::class.java))
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
        return false
    }

    fun buildNavigateToMapIntent(context: Context): Intent {
        return Intent(context, MainContainerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainContainerActivity.EXTRA_NAV_ITEM_ID, R.id.navMap)
        }
    }

    fun buildNavigateToMapPageIntent(context: Context): Intent {
        return Intent(context, MainContainerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainContainerActivity.EXTRA_INITIAL_PAGE, MainContainerActivity.PAGE_MAP)
        }
    }

    fun resolveStartTimeOrNow(serviceStartTime: Long?): Long {
        return serviceStartTime ?: SystemClock.elapsedRealtime()
    }
}
