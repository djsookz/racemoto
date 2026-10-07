package com.revix.app.track

import android.content.Context
import com.revix.app.drag.DragRunVideoSettings

object TrackSessionVideoSettings {
    private const val PREFS = "track_session_video_settings"
    private const val KEY_QUALITY = "quality"
    private const val KEY_FPS = "fps"
    private const val KEY_LENS = "lens"
    private const val KEY_DUAL = "dual"
    private const val KEY_MIC = "mic"
    private const val KEY_MAP_3D = "map_3d"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun quality(context: Context): DragRunVideoSettings.QualityOption =
        DragRunVideoSettings.QualityOption.fromStored(
            prefs(context).getString(KEY_QUALITY, DragRunVideoSettings.QualityOption.HD.stored)
        )

    fun fps(context: Context): Int =
        prefs(context).getInt(KEY_FPS, 30).let { if (it == 60) 60 else 30 }

    fun lens(context: Context): DragRunVideoSettings.LensOption =
        DragRunVideoSettings.LensOption.fromStored(
            prefs(context).getString(KEY_LENS, DragRunVideoSettings.LensOption.REAR.stored)
        )

    fun dualEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DUAL, false)

    fun micEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MIC, true)

    fun map3dEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MAP_3D, true)

    fun save(
        context: Context,
        quality: DragRunVideoSettings.QualityOption = quality(context),
        fps: Int = fps(context),
        lens: DragRunVideoSettings.LensOption = lens(context),
        micEnabled: Boolean = micEnabled(context),
        map3dEnabled: Boolean = map3dEnabled(context),
        dualEnabled: Boolean = dualEnabled(context)
    ) {
        prefs(context).edit()
            .putString(KEY_QUALITY, quality.stored)
            .putInt(KEY_FPS, if (fps == 60) 60 else 30)
            .putString(KEY_LENS, lens.stored)
            .putBoolean(KEY_DUAL, dualEnabled)
            .putBoolean(KEY_MIC, micEnabled)
            .putBoolean(KEY_MAP_3D, map3dEnabled)
            .apply()
    }
}
