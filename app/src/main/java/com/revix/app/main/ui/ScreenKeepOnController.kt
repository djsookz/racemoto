package com.revix.app.main.ui

import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import android.os.Bundle
import android.view.WindowManager
import androidx.preference.PreferenceManager
import java.util.Collections
import java.util.WeakHashMap

/**
 * Keeps the screen on across every Activity when "always_on_display" is enabled,
 * or when an activity requests it (e.g. track camera recording).
 */
object ScreenKeepOnController {

    private val resumedActivities =
        Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())
    private val keepOnOverrides =
        Collections.synchronizedMap(WeakHashMap<Activity, Boolean>())

    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var lifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null

    fun init(application: Application) {
        if (lifecycleCallbacks != null) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(application)
        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
            if (key == "always_on_display") {
                val keepOn = shared.getBoolean(key, false)
                synchronized(resumedActivities) {
                    resumedActivities.forEach { apply(it, keepOn) }
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                applyFromPrefs(activity)
            }

            override fun onActivityStarted(activity: Activity) = Unit

            override fun onActivityResumed(activity: Activity) {
                synchronized(resumedActivities) { resumedActivities.add(activity) }
                applyFromPrefs(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                synchronized(resumedActivities) { resumedActivities.remove(activity) }
            }

            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                synchronized(resumedActivities) { resumedActivities.remove(activity) }
            }
        }
        application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
    }

    fun applyFromPrefs(activity: Activity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        apply(activity, prefs.getBoolean("always_on_display", true))
    }

    fun setKeepOnOverride(activity: Activity, keepOn: Boolean) {
        if (keepOn) {
            keepOnOverrides[activity] = true
        } else {
            keepOnOverrides.remove(activity)
        }
        applyFromPrefs(activity)
    }

    private fun apply(activity: Activity, keepOnFromPrefs: Boolean) {
        if (activity.isFinishing) return
        val keepOn = keepOnFromPrefs || keepOnOverrides[activity] == true
        if (keepOn) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
