package com.example.clinometer.garage

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.clinometer.Profile
import com.example.clinometer.R
import com.example.clinometer.data.GarageDocumentEntry
import com.example.clinometer.data.GarageDocumentEntryStorage
import com.example.clinometer.data.GarageOdometerTimeline
import com.example.clinometer.data.ProfileStorage

object GarageDocumentReminderManager {
    private const val CHANNEL_ID = "garage_document_reminders"
    private const val CHANNEL_NAME = "Garage document reminders"
    private const val EXTRA_PROFILE_ID = "document_reminder_profile_id"
    private const val EXTRA_ENTRY_ID = "document_reminder_entry_id"

    fun syncReminder(context: Context, entry: GarageDocumentEntry) {
        if (!entry.reminderEnabled || entry.reminderTriggeredAt != null || entry.reminderCompletedAt != null) {
            cancelReminder(context, entry)
            return
        }

        if (resolveReminderDateMillis(entry) == null) {
            cancelReminder(context, entry)
            return
        }

        scheduleDateReminderIfNeeded(context, entry)
        evaluateDueRemindersForProfile(context, entry.profileId)
    }

    fun evaluateDueRemindersForProfile(context: Context, profileId: Long) {
        if (profileId == -1L) {
            return
        }

        GarageDocumentEntryStorage.loadEntries(context, profileId)
            .filter { it.reminderEnabled && it.reminderTriggeredAt == null && it.reminderCompletedAt == null }
            .forEach { entry ->
                maybeTriggerReminder(context, entry)
            }
    }

    fun handleReminderAlarm(context: Context, profileId: Long, entryId: Long) {
        val entry = GarageDocumentEntryStorage.findEntry(context, profileId, entryId) ?: return
        maybeTriggerReminder(context, entry)
    }

    fun cancelReminder(context: Context, entry: GarageDocumentEntry) {
        cancelDateReminder(context, entry)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager?.cancel(reminderNotificationId(entry.id))
    }

    fun markReminderCompleted(context: Context, entry: GarageDocumentEntry): GarageDocumentEntry {
        val updatedEntry = if (entry.reminderCompletedAt != null) {
            entry
        } else {
            entry.copy(reminderCompletedAt = System.currentTimeMillis())
        }

        GarageDocumentEntryStorage.upsertEntry(context, updatedEntry)
        cancelReminder(context, updatedEntry)
        return updatedEntry
    }

    fun rescheduleAll(context: Context) {
        ProfileStorage.loadProfiles(context).forEach { profile ->
            GarageDocumentEntryStorage.loadEntries(context, profile.id).forEach { entry ->
                if (entry.reminderEnabled && entry.reminderTriggeredAt == null && entry.reminderCompletedAt == null && resolveReminderDateMillis(entry) != null) {
                    val wasTriggered = maybeTriggerReminder(context, entry)
                    if (!wasTriggered) {
                        scheduleDateReminderIfNeeded(context, entry)
                    }
                } else {
                    cancelDateReminder(context, entry)
                }
            }
        }
    }

    private fun maybeTriggerReminder(context: Context, entry: GarageDocumentEntry): Boolean {
        if (!entry.reminderEnabled || entry.reminderTriggeredAt != null || entry.reminderCompletedAt != null) {
            return false
        }

        val dueAt = resolveReminderDateMillis(entry) ?: return false
        if (System.currentTimeMillis() < dueAt) {
            return false
        }

        if (!hasNotificationPermission(context)) {
            return false
        }

        showReminderNotification(context, entry)
        GarageDocumentEntryStorage.upsertEntry(
            context,
            entry.copy(reminderTriggeredAt = System.currentTimeMillis())
        )
        cancelDateReminder(context, entry)
        return true
    }

    private fun resolveReminderDateMillis(entry: GarageDocumentEntry): Long? {
        val issueTimestamp = GarageOdometerTimeline.resolveReferenceTimestamp(entry.date, entry.createdAt)
        return GarageDocumentReminderRules.resolveReminderDate(entry, issueTimestamp)
    }

    private fun scheduleDateReminderIfNeeded(context: Context, entry: GarageDocumentEntry) {
        val dueDateMillis = resolveReminderDateMillis(entry)
        if (dueDateMillis == null || dueDateMillis <= System.currentTimeMillis()) {
            cancelDateReminder(context, entry)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildReminderPendingIntent(context, entry.profileId, entry.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueDateMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueDateMillis, pendingIntent)
        }
    }

    private fun cancelDateReminder(context: Context, entry: GarageDocumentEntry) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildReminderPendingIntent(context, entry.profileId, entry.id)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildReminderPendingIntent(context: Context, profileId: Long, entryId: Long): PendingIntent {
        val intent = Intent(context, GarageDocumentReminderReceiver::class.java).apply {
            putExtra(EXTRA_PROFILE_ID, profileId)
            putExtra(EXTRA_ENTRY_ID, entryId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderRequestCode(entryId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showReminderNotification(context: Context, entry: GarageDocumentEntry) {
        ensureNotificationChannel(context)

        val profile = ProfileStorage.loadProfiles(context).firstOrNull { it.id == entry.profileId }
        val title = buildNotificationTitle(context, profile, entry)
        val message = context.getString(R.string.garage_document_reminder_notification_text)
        val contentIntent = PendingIntent.getActivity(
            context,
            reminderRequestCode(entry.id),
            GarageDocumentEntryActivity.createIntent(context, entry.profileId, entry.id).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(message)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        notificationManager.notify(reminderNotificationId(entry.id), notification)
    }

    private fun buildNotificationTitle(context: Context, profile: Profile?, entry: GarageDocumentEntry): String {
        val profileName = profile?.name?.trim().orEmpty()
        val documentType = entry.documentType.trim()
        return when {
            profileName.isNotEmpty() && documentType.isNotEmpty() -> context.getString(
                R.string.garage_document_reminder_notification_title_format,
                profileName,
                documentType
            )
            profileName.isNotEmpty() -> profileName
            documentType.isNotEmpty() -> documentType
            else -> context.getString(R.string.garage_document_entry_title)
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun reminderRequestCode(entryId: Long): Int {
        return (entryId xor (entryId ushr 32)).toInt() xor 0x27D0
    }

    private fun reminderNotificationId(entryId: Long): Int {
        return reminderRequestCode(entryId) xor 0x4B1A
    }
}