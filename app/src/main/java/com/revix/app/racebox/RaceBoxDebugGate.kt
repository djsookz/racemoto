package com.revix.app.racebox

import android.content.Context
import androidx.preference.PreferenceManager
import com.revix.app.BuildConfig

/**
 * Local/debug-only gate for RaceBox hardware. Release builds never expose or use it.
 */
object RaceBoxDebugGate {
    private const val PREF_USE_RACEBOX_GPS = "debug_use_racebox_gps"

    fun isAvailable(): Boolean = BuildConfig.DEBUG

    fun isUseRaceBoxGpsEnabled(context: Context): Boolean {
        if (!isAvailable()) return false
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_USE_RACEBOX_GPS, false)
    }

    fun setUseRaceBoxGpsEnabled(context: Context, enabled: Boolean) {
        if (!isAvailable()) return
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(PREF_USE_RACEBOX_GPS, enabled)
            .apply()
    }

    fun shouldOverridePhoneGps(context: Context): Boolean {
        return isUseRaceBoxGpsEnabled(context) && RaceBoxManager.isConnected
    }
}
