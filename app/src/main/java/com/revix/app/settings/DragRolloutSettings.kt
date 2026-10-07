package com.revix.app.settings

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Drag standing-start 1 ft (~30 cm) rollout — industry standard to align with strip / RaceBox timing.
 * Shared for phone and external IMU paths.
 */
object DragRolloutSettings {
    private const val PREF_1FT_ROLLOUT_ENABLED = "drag_1ft_rollout_enabled"
    private const val DEFAULT_ENABLED = true

    /** Official 1 foot in meters. */
    const val ROLLOUT_METERS = 0.3048f

    fun is1ftRolloutEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .getBoolean(PREF_1FT_ROLLOUT_ENABLED, DEFAULT_ENABLED)
    }

    fun set1ftRolloutEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit()
            .putBoolean(PREF_1FT_ROLLOUT_ENABLED, enabled)
            .apply()
    }
}
