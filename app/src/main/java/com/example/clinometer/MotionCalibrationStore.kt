package com.example.clinometer

import android.content.Context

/**
 * Stores profile-scoped motion calibration values used by gyro-based fusion.
 */
object MotionCalibrationStore {

    private const val PREFS_NAME = "MotionCalibrationStore"

    data class Snapshot(
        val calibrated: Boolean = false,
        val hasGyroBias: Boolean = false,
        val gyroBiasX: Float = 0f,
        val gyroBiasY: Float = 0f,
        val gyroBiasZ: Float = 0f,
        val qualityScore: Float = 0f,
        val stillSamples: Int = 0,
        val forwardSamples: Int = 0,
        val stillLinearAvg: Float = 0f,
        val stillVibrationMag: Float = 0f,
        val forwardNoiseFloor: Float = 0f,
        val forwardExcessTrigger: Float = 0f,
        val timestamp: Long = 0L
    )

    private fun keyPrefix(profileId: Long, isLandscape: Boolean?): String {
        return when (isLandscape) {
            null -> "profile_${profileId}_"
            true -> "profile_${profileId}_landscape_"
            false -> "profile_${profileId}_portrait_"
        }
    }

    fun loadSnapshot(context: Context, profileId: Long): Snapshot {
        return loadSnapshot(context, profileId, null)
    }

    fun loadSnapshot(context: Context, profileId: Long, isLandscape: Boolean?): Snapshot {
        if (profileId == -1L) return Snapshot()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keyPrefix = keyPrefix(profileId, isLandscape)
        val calibrated = prefs.getBoolean(keyPrefix + "calibrated", false)

        // Backward compatibility: if orientation-specific data is missing, fall back to legacy profile-wide values.
        if (!calibrated && isLandscape != null) {
            return loadSnapshot(context, profileId)
        }

        return Snapshot(
            calibrated = calibrated,
            hasGyroBias = prefs.getBoolean(keyPrefix + "hasGyroBias", false),
            gyroBiasX = prefs.getFloat(keyPrefix + "gyroBiasX", 0f),
            gyroBiasY = prefs.getFloat(keyPrefix + "gyroBiasY", 0f),
            gyroBiasZ = prefs.getFloat(keyPrefix + "gyroBiasZ", 0f),
            qualityScore = prefs.getFloat(keyPrefix + "qualityScore", 0f),
            stillSamples = prefs.getInt(keyPrefix + "stillSamples", 0),
            forwardSamples = prefs.getInt(keyPrefix + "forwardSamples", 0),
            stillLinearAvg = prefs.getFloat(keyPrefix + "stillLinearAvg", 0f),
            stillVibrationMag = prefs.getFloat(keyPrefix + "stillVibrationMag", 0f),
            forwardNoiseFloor = prefs.getFloat(keyPrefix + "forwardNoiseFloor", 0f),
            forwardExcessTrigger = prefs.getFloat(keyPrefix + "forwardExcessTrigger", 0f),
            timestamp = prefs.getLong(keyPrefix + "timestamp", 0L)
        )
    }

    fun saveSnapshot(
        context: Context,
        profileId: Long,
        gyroBiasRad: FloatArray,
        hasGyroBias: Boolean,
        qualityScore: Float,
        stillSamples: Int,
        forwardSamples: Int,
        stillLinearAvg: Float,
        stillVibrationMag: Float,
        forwardNoiseFloor: Float,
        forwardExcessTrigger: Float,
        isLandscape: Boolean? = null
    ) {
        if (profileId == -1L) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keyPrefix = keyPrefix(profileId, isLandscape)
        prefs.edit()
            .putBoolean(keyPrefix + "calibrated", true)
            .putBoolean(keyPrefix + "hasGyroBias", hasGyroBias)
            .putFloat(keyPrefix + "gyroBiasX", gyroBiasRad.getOrElse(0) { 0f })
            .putFloat(keyPrefix + "gyroBiasY", gyroBiasRad.getOrElse(1) { 0f })
            .putFloat(keyPrefix + "gyroBiasZ", gyroBiasRad.getOrElse(2) { 0f })
            .putFloat(keyPrefix + "qualityScore", qualityScore.coerceIn(0f, 1f))
            .putInt(keyPrefix + "stillSamples", stillSamples.coerceAtLeast(0))
            .putInt(keyPrefix + "forwardSamples", forwardSamples.coerceAtLeast(0))
                .putFloat(keyPrefix + "stillLinearAvg", stillLinearAvg.coerceAtLeast(0f))
                .putFloat(keyPrefix + "stillVibrationMag", stillVibrationMag.coerceAtLeast(0f))
                .putFloat(keyPrefix + "forwardNoiseFloor", forwardNoiseFloor.coerceAtLeast(0f))
                .putFloat(keyPrefix + "forwardExcessTrigger", forwardExcessTrigger.coerceAtLeast(0f))
            .putLong(keyPrefix + "timestamp", System.currentTimeMillis())
            .apply()
    }

    fun clearSnapshot(context: Context, profileId: Long) {
        clearSnapshot(context, profileId, null)
    }

    fun clearSnapshot(context: Context, profileId: Long, isLandscape: Boolean?) {
        if (profileId == -1L) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keyPrefix = keyPrefix(profileId, isLandscape)
        prefs.edit()
            .remove(keyPrefix + "calibrated")
            .remove(keyPrefix + "hasGyroBias")
            .remove(keyPrefix + "gyroBiasX")
            .remove(keyPrefix + "gyroBiasY")
            .remove(keyPrefix + "gyroBiasZ")
            .remove(keyPrefix + "qualityScore")
            .remove(keyPrefix + "stillSamples")
            .remove(keyPrefix + "forwardSamples")
            .remove(keyPrefix + "stillLinearAvg")
            .remove(keyPrefix + "stillVibrationMag")
            .remove(keyPrefix + "forwardNoiseFloor")
            .remove(keyPrefix + "forwardExcessTrigger")
            .remove(keyPrefix + "timestamp")
            .apply()
    }
}
