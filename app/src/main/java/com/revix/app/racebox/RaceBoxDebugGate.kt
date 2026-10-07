package com.revix.app.racebox

import android.content.Context
import androidx.preference.PreferenceManager
import com.revix.app.billing.ProAccess

/**
 * Gate for external GPS/IMU ("RaceBox") hardware.
 * Connection UI and GPS override require Pro / active trial.
 */
object RaceBoxDebugGate {
    private const val PREF_USE_RACEBOX_GPS = "debug_use_racebox_gps"

    fun isAvailable(): Boolean = true

    fun canUse(context: Context): Boolean = ProAccess.hasFullAccess(context)

    fun isUseRaceBoxGpsEnabled(context: Context): Boolean {
        if (!canUse(context)) return false
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_USE_RACEBOX_GPS, false)
    }

    fun setUseRaceBoxGpsEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(PREF_USE_RACEBOX_GPS, enabled)
            .apply()
    }

    fun shouldOverridePhoneGps(context: Context): Boolean {
        return canUse(context) &&
            isUseRaceBoxGpsEnabled(context) &&
            RaceBoxManager.isConnected
    }
}
