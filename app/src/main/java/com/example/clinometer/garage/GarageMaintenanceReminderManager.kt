package com.example.clinometer.garage

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.BitmapFactory
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.clinometer.Profile
import com.example.clinometer.R
import com.example.clinometer.data.GarageMaintenanceEntry
import com.example.clinometer.data.GarageMaintenanceEntryStorage
import com.example.clinometer.data.GarageOdometerSource
import com.example.clinometer.data.GarageOdometerTimeline
import com.example.clinometer.data.ProfileStorage
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object GarageMaintenanceReminderManager {
    private const val CHANNEL_ID = "garage_maintenance_reminders"
    private const val CHANNEL_NAME = "Garage reminders"
    private const val EXTRA_PROFILE_ID = "reminder_profile_id"
    private const val EXTRA_ENTRY_ID = "reminder_entry_id"

    fun syncReminder(context: Context, entry: GarageMaintenanceEntry) {
        if (!entry.reminderEnabled || entry.reminderTriggeredAt != null || entry.reminderCompletedAt != null) {
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

        GarageMaintenanceEntryStorage.loadEntries(context, profileId)
            .filter { it.reminderEnabled && it.reminderTriggeredAt == null && it.reminderCompletedAt == null }
            .forEach { entry ->
                maybeTriggerReminder(context, entry)
            }
    }

    fun handleReminderAlarm(context: Context, profileId: Long, entryId: Long) {
        val entry = GarageMaintenanceEntryStorage.findEntry(context, profileId, entryId) ?: return
        maybeTriggerReminder(context, entry)
    }

    fun cancelReminder(context: Context, entry: GarageMaintenanceEntry) {
        cancelDateReminder(context, entry)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager?.cancel(reminderNotificationId(entry.id))
    }

    fun markReminderCompleted(context: Context, entry: GarageMaintenanceEntry): GarageMaintenanceEntry {
        val updatedEntry = if (entry.reminderCompletedAt != null) {
            entry
        } else {
            entry.copy(reminderCompletedAt = System.currentTimeMillis())
        }

        GarageMaintenanceEntryStorage.upsertEntry(context, updatedEntry)
        cancelReminder(context, updatedEntry)
        return updatedEntry
    }

    fun rescheduleAll(context: Context) {
        ProfileStorage.loadProfiles(context).forEach { profile ->
            GarageMaintenanceEntryStorage.loadEntries(context, profile.id).forEach { entry ->
                if (entry.reminderEnabled && entry.reminderTriggeredAt == null && entry.reminderCompletedAt == null) {
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

    private fun maybeTriggerReminder(context: Context, entry: GarageMaintenanceEntry): Boolean {
        if (!entry.reminderEnabled || entry.reminderTriggeredAt != null || entry.reminderCompletedAt != null) {
            return false
        }

        val serviceTimestamp = resolveServiceTimestamp(entry)
        val kmReachedAt = resolveReminderOdometer(entry)?.let {
            GarageOdometerTimeline.firstReachedTargetTimestampAfter(
                context = context,
                profileId = entry.profileId,
                source = GarageOdometerSource.MAINTENANCE,
                entryId = entry.id,
                targetOdometerKm = it,
                dateText = entry.date,
                fallbackTimestamp = entry.createdAt
            )
        }
        val kmDue = kmReachedAt != null

        val dueDateMillis = resolveReminderDateMillis(entry, serviceTimestamp)
        val dateDue = dueDateMillis != null && System.currentTimeMillis() >= dueDateMillis

        if (!kmDue && !dateDue) {
            return false
        }

        if (!hasNotificationPermission(context)) {
            return false
        }

        val reason = when {
            kmReachedAt != null && dueDateMillis != null && dateDue -> {
                if (kmReachedAt <= dueDateMillis) ReminderTriggerReason.KILOMETERS else ReminderTriggerReason.DATE
            }

            kmDue -> ReminderTriggerReason.KILOMETERS
            else -> ReminderTriggerReason.DATE
        }

        showReminderNotification(context, entry)

        GarageMaintenanceEntryStorage.upsertEntry(
            context,
            entry.copy(
                reminderTriggeredAt = System.currentTimeMillis(),
                reminderTriggeredBy = reason.name
            )
        )
        cancelDateReminder(context, entry)
        return true
    }

    private fun resolveReminderOdometer(entry: GarageMaintenanceEntry): Long? {
        return GarageMaintenanceReminderRules.resolveKmReminder(entry)
    }

    private fun resolveReminderDateMillis(entry: GarageMaintenanceEntry, serviceTimestamp: Long): Long? {
        return GarageMaintenanceReminderRules.resolveDateReminder(entry, serviceTimestamp)
    }

    private fun resolveServiceTimestamp(entry: GarageMaintenanceEntry): Long {
        return GarageOdometerTimeline.resolveReferenceTimestamp(entry.date, entry.createdAt)
    }

    private fun scheduleDateReminderIfNeeded(context: Context, entry: GarageMaintenanceEntry) {
        val dueDateMillis = resolveReminderDateMillis(entry, resolveServiceTimestamp(entry))
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

    private fun cancelDateReminder(context: Context, entry: GarageMaintenanceEntry) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildReminderPendingIntent(context, entry.profileId, entry.id)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildReminderPendingIntent(context: Context, profileId: Long, entryId: Long): PendingIntent {
        val intent = Intent(context, GarageMaintenanceReminderReceiver::class.java).apply {
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

    private fun showReminderNotification(
        context: Context,
        entry: GarageMaintenanceEntry
    ) {
        ensureNotificationChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            reminderRequestCode(entry.id),
            GarageMaintenanceEntryActivity.createIntent(context, entry.profileId, entry.id).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val profile = ProfileStorage.loadProfiles(context).firstOrNull { it.id == entry.profileId }
        val title = buildNotificationTitle(context, profile, entry)
        val message = buildNotificationMessage(context, profile)

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

    private fun buildNotificationTitle(
        context: Context,
        profile: Profile?,
        entry: GarageMaintenanceEntry
    ): String {
        val profileName = profile
            ?.name
            ?.trim()
            .orEmpty()
        val serviceType = entry.serviceType.trim()

        return when {
            profileName.isNotEmpty() && serviceType.isNotEmpty() -> context.getString(
                R.string.garage_maintenance_reminder_notification_profile_service_title,
                profileName,
                serviceType
            )

            profileName.isNotEmpty() -> profileName
            serviceType.isNotEmpty() -> serviceType
            else -> context.getString(R.string.garage_maintenance_reminder_notification_title)
        }
    }

    private fun buildNotificationMessage(context: Context, profile: Profile?): String {
        return when (profile?.vehicleType) {
            Profile.VehicleType.MOTORCYCLE -> context.getString(
                R.string.garage_maintenance_reminder_notification_expired_text_motorcycle
            )

            Profile.VehicleType.CAR -> context.getString(
                R.string.garage_maintenance_reminder_notification_expired_text_car
            )

            null -> context.getString(
                R.string.garage_maintenance_reminder_notification_expired_text_generic
            )
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
        return (entryId xor (entryId ushr 32)).toInt()
    }

    private fun reminderNotificationId(entryId: Long): Int {
        return reminderRequestCode(entryId) xor 0x3A51
    }

    private enum class ReminderTriggerReason {
        KILOMETERS,
        DATE
    }
}