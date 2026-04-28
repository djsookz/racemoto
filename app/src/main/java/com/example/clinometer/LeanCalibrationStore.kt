package com.example.clinometer

import android.content.Context

data class LeanCalibrationSnapshot(
    val portraitOffsetDeg: Float = 0f,
    val portraitCalibrated: Boolean = false,
    val portraitTimestamp: Long = 0L,
    val landscapeOffsetDeg: Float = 0f,
    val landscapeCalibrated: Boolean = false,
    val landscapeTimestamp: Long = 0L
) {
    fun hasAnyCalibration(): Boolean = portraitCalibrated || landscapeCalibrated
}

object LeanCalibrationStore {
    private const val PREFS_NAME = "LeanCalibrationPrefs"

    private fun key(profileId: Long, suffix: String): String = "profile_${profileId}_$suffix"

    fun loadSnapshot(context: Context, profileId: Long): LeanCalibrationSnapshot {
        if (profileId == -1L) return LeanCalibrationSnapshot()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return LeanCalibrationSnapshot(
            portraitOffsetDeg = prefs.getFloat(key(profileId, "portrait_offset_deg"), 0f),
            portraitCalibrated = prefs.getBoolean(key(profileId, "portrait_calibrated"), false),
            portraitTimestamp = prefs.getLong(key(profileId, "portrait_time"), 0L),
            landscapeOffsetDeg = prefs.getFloat(key(profileId, "landscape_offset_deg"), 0f),
            landscapeCalibrated = prefs.getBoolean(key(profileId, "landscape_calibrated"), false),
            landscapeTimestamp = prefs.getLong(key(profileId, "landscape_time"), 0L)
        )
    }

    fun saveOrientation(context: Context, profileId: Long, isLandscape: Boolean, offsetDeg: Float) {
        if (profileId == -1L) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val prefix = if (isLandscape) "landscape" else "portrait"
        editor.putFloat(key(profileId, "${prefix}_offset_deg"), offsetDeg)
        editor.putBoolean(key(profileId, "${prefix}_calibrated"), true)
        editor.putLong(key(profileId, "${prefix}_time"), System.currentTimeMillis())
        editor.apply()
    }

    fun clearOrientation(context: Context, profileId: Long, isLandscape: Boolean) {
        if (profileId == -1L) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val prefix = if (isLandscape) "landscape" else "portrait"
        editor.putFloat(key(profileId, "${prefix}_offset_deg"), 0f)
        editor.putBoolean(key(profileId, "${prefix}_calibrated"), false)
        editor.putLong(key(profileId, "${prefix}_time"), 0L)
        editor.apply()
    }

    fun clearAll(context: Context, profileId: Long) {
        if (profileId == -1L) return
        clearOrientation(context, profileId, isLandscape = false)
        clearOrientation(context, profileId, isLandscape = true)
    }
}
