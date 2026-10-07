package com.revix.app.main.tour

import android.content.Context
import androidx.preference.PreferenceManager

object FeatureTourStore {
    private const val PREF_PENDING = "feature_tour_pending"
    private const val PREF_COMPLETED = "feature_tour_completed"

    fun markPending(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(PREF_PENDING, true)
            .apply()
    }

    fun shouldShow(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(PREF_PENDING, false) && !prefs.getBoolean(PREF_COMPLETED, false)
    }

    fun markCompleted(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(PREF_PENDING, false)
            .putBoolean(PREF_COMPLETED, true)
            .apply()
    }
}
