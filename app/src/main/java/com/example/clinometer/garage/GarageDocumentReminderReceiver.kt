package com.example.clinometer.garage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class GarageDocumentReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val profileId = intent.getLongExtra("document_reminder_profile_id", -1L)
        val entryId = intent.getLongExtra("document_reminder_entry_id", -1L)
        if (profileId == -1L || entryId == -1L) {
            return
        }

        GarageDocumentReminderManager.handleReminderAlarm(context, profileId, entryId)
    }
}