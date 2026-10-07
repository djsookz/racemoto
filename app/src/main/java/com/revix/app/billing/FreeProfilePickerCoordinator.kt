package com.revix.app.billing

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.revix.app.OnboardingTutorialActivity
import com.revix.app.SplashActivity
import com.revix.app.WelcomeActivity
import com.revix.app.garage.FirstProfileActivity
import java.lang.ref.WeakReference

/**
 * Ensures the free-profile picker is shown whenever Pro/trial is gone and
 * the user still has 2+ profiles without a choice. Cannot be skipped by
 * staying on Garage/Map/session screens.
 */
object FreeProfilePickerCoordinator {
    private var foregroundActivity: WeakReference<Activity>? = null
    private var installed = false

    fun install(application: Application) {
        if (installed) return
        installed = true

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                if (foregroundActivity?.get() === activity) {
                    foregroundActivity = null
                }
            }

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityResumed(activity: Activity) {
                foregroundActivity = WeakReference(activity)
                promptIfNeeded(activity)
            }
        })

        PlayBillingManager.addEntitlementListener { entitled ->
            ProAccess.onEntitlementChanged(application, entitled)
            if (!entitled) {
                foregroundActivity?.get()?.let { promptIfNeeded(it) }
            }
        }
    }

    fun promptIfNeeded(activity: Activity) {
        if (activity.isFinishing) return
        if (shouldSkipHost(activity)) return
        if (!ProAccess.needsFreeProfileSelection(activity)) return

        val intent = FreeProfilePickerActivity.intent(activity).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        activity.startActivity(intent)
    }

    private fun shouldSkipHost(activity: Activity): Boolean {
        return activity is FreeProfilePickerActivity ||
            activity is SplashActivity ||
            activity is WelcomeActivity ||
            activity is OnboardingTutorialActivity ||
            activity is FirstProfileActivity
    }
}
