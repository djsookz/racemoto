package com.example.clinometer.garage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class GarageMaintenanceReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val profileId = intent.getLongExtra("reminder_profile_id", -1L)
        val entryId = intent.getLongExtra("reminder_entry_id", -1L)
        if (profileId == -1L || entryId == -1L) {
            return
        }

        GarageMaintenanceReminderManager.handleReminderAlarm(context, profileId, entryId)
    }
}