package com.example.clinometer.garage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class GarageMaintenanceReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                GarageMaintenanceReminderManager.rescheduleAll(context)
                GarageDocumentReminderManager.rescheduleAll(context)
            }
        }
    }
}