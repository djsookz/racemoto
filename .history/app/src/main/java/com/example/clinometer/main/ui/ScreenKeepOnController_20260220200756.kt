package com.example.clinometer.main.ui

import android.app.Activity
import android.view.WindowManager
import androidx.preference.PreferenceManager

object ScreenKeepOnController {

    fun setup(activity: Activity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        apply(activity, prefs.getBoolean("always_on_display", false))

        prefs.registerOnSharedPreferenceChangeListener { shared, key ->
            if (key == "always_on_display") {
                apply(activity, shared.getBoolean(key, false))
            }
        }
    }

    private fun apply(activity: Activity, keepOn: Boolean) {
        if (keepOn) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
