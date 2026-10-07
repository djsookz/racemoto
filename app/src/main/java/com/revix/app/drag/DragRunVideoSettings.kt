package com.revix.app.drag

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.video.Quality

object DragRunVideoSettings {
    private const val PREFS = "drag_run_video_settings"
    private const val KEY_QUALITY = "quality"
    private const val KEY_FPS = "fps"
    private const val KEY_LENS = "lens"
    private const val KEY_DUAL = "dual"
    private const val KEY_MIC = "mic"

    enum class QualityOption(val stored: String, val cameraQuality: Quality) {
        HD("hd", Quality.HD),
        FHD("fhd", Quality.FHD),
        UHD("uhd", Quality.UHD);

        companion object {
            fun fromStored(value: String?): QualityOption {
                return entries.firstOrNull { it.stored == value } ?: HD
            }
        }
    }

    enum class LensOption(val stored: String, val lensFacing: Int) {
        REAR("rear", CameraSelector.LENS_FACING_BACK),
        FRONT("front", CameraSelector.LENS_FACING_FRONT);

        companion object {
            fun fromStored(value: String?): LensOption {
                return entries.firstOrNull { it.stored == value } ?: REAR
            }
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun quality(context: Context): QualityOption =
        QualityOption.fromStored(prefs(context).getString(KEY_QUALITY, QualityOption.HD.stored))

    fun fps(context: Context): Int =
        prefs(context).getInt(KEY_FPS, 30).let { if (it == 60) 60 else 30 }

    fun lens(context: Context): LensOption =
        LensOption.fromStored(prefs(context).getString(KEY_LENS, LensOption.REAR.stored))

    fun dualEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DUAL, false)

    fun micEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MIC, true)

    fun save(
        context: Context,
        quality: QualityOption = quality(context),
        fps: Int = fps(context),
        lens: LensOption = lens(context),
        micEnabled: Boolean = micEnabled(context),
        dualEnabled: Boolean = dualEnabled(context)
    ) {
        prefs(context).edit()
            .putString(KEY_QUALITY, quality.stored)
            .putInt(KEY_FPS, if (fps == 60) 60 else 30)
            .putString(KEY_LENS, lens.stored)
            .putBoolean(KEY_DUAL, dualEnabled)
            .putBoolean(KEY_MIC, micEnabled)
            .apply()
    }
}
