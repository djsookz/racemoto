package com.example.clinometer.data

import android.content.Context
import com.example.clinometer.DragCalibration

object CalibrationReminderStore {

    private const val PREFS_NAME = "CalibrationReminderPrefs"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(profileId: Long) = "profile_${profileId}_drag_calibration_reminder"

    fun markDragCalibrationDeferred(context: Context, profileId: Long) {
        if (profileId <= 0L) {
            return
        }
        prefs(context).edit().putBoolean(key(profileId), true).apply()
    }

    fun clearDragCalibrationDeferred(context: Context, profileId: Long) {
        if (profileId <= 0L) {
            return
        }
        prefs(context).edit().remove(key(profileId)).apply()
    }

    fun needsDragCalibrationReminder(context: Context, profileId: Long): Boolean {
        if (profileId <= 0L) {
            return false
        }
        if (DragCalibration.isProfileCalibrated(context, profileId)) {
            clearDragCalibrationDeferred(context, profileId)
            return false
        }
        return prefs(context).getBoolean(key(profileId), false)
    }

    fun needsSelectedProfileDragCalibrationReminder(context: Context): Boolean {
        val selectedProfileId = ProfileStorage.getSelectedProfileId(context)
        return needsDragCalibrationReminder(context, selectedProfileId)
    }
}