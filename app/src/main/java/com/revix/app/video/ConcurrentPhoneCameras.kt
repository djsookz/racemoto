package com.revix.app.video

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import com.revix.app.drag.DragRunVideoSettings

object ConcurrentPhoneCameras {
    private const val PREFS = "concurrent_phone_cameras"
    private const val KEY_DUAL_OK = "dual_video_ok"

    fun advertised(context: Context, provider: ProcessCameraProvider): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_CONCURRENT)) {
            return false
        }
        val combos = runCatching { provider.availableConcurrentCameraInfos }.getOrNull().orEmpty()
        return combos.any { infos ->
            hasFacing(infos, CameraSelector.LENS_FACING_BACK) &&
                hasFacing(infos, CameraSelector.LENS_FACING_FRONT)
        }
    }

    fun isSupported(context: Context, provider: ProcessCameraProvider?): Boolean {
        if (provider == null) return false
        if (!advertised(context, provider)) return false
        val prefs = prefs(context)
        if (!prefs.contains(KEY_DUAL_OK)) return false
        return prefs.getBoolean(KEY_DUAL_OK, false)
    }

    fun isProbed(context: Context): Boolean {
        return prefs(context).contains(KEY_DUAL_OK)
    }

    fun markSupported(context: Context, supported: Boolean) {
        prefs(context).edit().putBoolean(KEY_DUAL_OK, supported).apply()
    }

    fun otherLens(lens: DragRunVideoSettings.LensOption): DragRunVideoSettings.LensOption {
        return if (lens == DragRunVideoSettings.LensOption.FRONT) {
            DragRunVideoSettings.LensOption.REAR
        } else {
            DragRunVideoSettings.LensOption.FRONT
        }
    }

    private fun hasFacing(infos: List<CameraInfo>, facing: Int): Boolean {
        val selector = CameraSelector.Builder().requireLensFacing(facing).build()
        return runCatching { selector.filter(infos).isNotEmpty() }.getOrDefault(false)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
