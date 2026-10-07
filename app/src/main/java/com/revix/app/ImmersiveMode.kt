package com.revix.app

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

fun Application.installStickyImmersiveMode() {
    registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            applyStickyImmersiveMode(activity)
            ensureImmersiveFocusListener(activity)
        }

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) {
            applyStickyImmersiveMode(activity)
        }

        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    })
}

fun applyStickyImmersiveMode(activity: Activity) {
    applyStickyImmersiveMode(activity.window ?: return)
}

fun applyStickyImmersiveMode(window: Window) {
    WindowCompat.setDecorFitsSystemWindows(window, true)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val params = window.attributes
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.attributes = params
    }

    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.isAppearanceLightStatusBars = false
    controller.isAppearanceLightNavigationBars = false
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(
        WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
    )
}

private fun ensureImmersiveFocusListener(activity: Activity) {
    val decor = activity.window?.decorView ?: return
    if (decor.getTag(R.id.tag_immersive_focus_listener) == true) return
    decor.setTag(R.id.tag_immersive_focus_listener, true)
    decor.viewTreeObserver.addOnWindowFocusChangeListener { hasFocus ->
        if (hasFocus && !activity.isFinishing) {
            applyStickyImmersiveMode(activity)
        }
    }
}
