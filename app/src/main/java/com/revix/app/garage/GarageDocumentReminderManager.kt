package com.revix.app.garage

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
import com.revix.app.Profile
import com.revix.app.R
import com.revix.app.data.GarageDocumentEntry
import com.revix.app.data.GarageDocumentEntryStorage
import com.revix.app.data.GarageOdometerTimeline
import com.revix.app.data.ProfileStorage

object GarageDocumentReminderManager {
    private const val CHANNEL_ID = "garage_document_reminders"
    private const val CHANNEL_NAME = "Garage document reminders"
    private const val EXTRA_PROFILE_ID = "document_reminder_profile_id"
    private const val EXTRA_ENTRY_ID = "document_reminder_entry_id"

    fun syncReminder(context: Context, entry: GarageDocumentEntry) {
        if (!entry.reminderEnabled || entry.reminderCompletedAt != null) {
            cancelReminder(context, entry)
            return
        }

        if (resolveTargetDateMillis(entry) == null) {
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
            .filter { it.reminderEnabled && it.reminderCompletedAt == null }
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
                if (entry.reminderEnabled && entry.reminderCompletedAt == null && resolveTargetDateMillis(entry) != null) {
                    maybeTriggerReminder(context, entry)
                } else {
                    cancelDateReminder(context, entry)
                }
            }
        }
    }

    private fun maybeTriggerReminder(context: Context, entry: GarageDocumentEntry): Boolean {
        if (!entry.reminderEnabled || entry.reminderCompletedAt != null) {
            return false
        }

        val targetMillis = resolveTargetDateMillis(entry) ?: return false
        val now = System.currentTimeMillis()
        val daysUntil = GarageReminderSchedule.daysUntil(targetMillis, now)
        val fired = GarageReminderSchedule.parseFired(entry.reminderNotifiedStages).toMutableSet()
        if (entry.reminderTriggeredAt != null && entry.reminderNotifiedStages.isNullOrBlank()) {
            GarageReminderSchedule.missedDateStages(daysUntil).forEach { fired.add(it.key) }
            GarageReminderSchedule.dateStageToday(daysUntil)?.let { fired.add(it.key) }
        }

        val todayStage = GarageReminderSchedule.dateStageToday(daysUntil)
        val missed = GarageReminderSchedule.missedDateStages(daysUntil).map { it.key }.filter { it !in fired }
        fired.addAll(missed)

        val notifyKey = todayStage?.key?.takeIf { it !in fired }
        if (notifyKey == null) {
            persistDocumentFiredIfChanged(context, entry, fired)
            scheduleDateReminderIfNeeded(
                context,
                entry.copy(reminderNotifiedStages = GarageReminderSchedule.encodeFired(fired))
            )
            return false
        }

        if (!hasNotificationPermission(context)) {
            persistDocumentFiredIfChanged(context, entry, fired)
            scheduleDateReminderIfNeeded(
                context,
                entry.copy(reminderNotifiedStages = GarageReminderSchedule.encodeFired(fired))
            )
            return false
        }

        fired.add(notifyKey)
        showReminderNotification(context, entry, notifyKey)
        val updated = entry.copy(
            reminderNotifiedStages = GarageReminderSchedule.encodeFired(fired),
            reminderTriggeredAt = entry.reminderTriggeredAt ?: now
        )
        GarageDocumentEntryStorage.upsertEntry(context, updated)
        scheduleDateReminderIfNeeded(context, updated)
        return true
    }

    private fun persistDocumentFiredIfChanged(
        context: Context,
        entry: GarageDocumentEntry,
        fired: Set<String>
    ) {
        val encoded = GarageReminderSchedule.encodeFired(fired)
        if (encoded == entry.reminderNotifiedStages) {
            return
        }
        GarageDocumentEntryStorage.upsertEntry(context, entry.copy(reminderNotifiedStages = encoded))
    }

    private fun resolveTargetDateMillis(entry: GarageDocumentEntry): Long? {
        val issueTimestamp = GarageOdometerTimeline.resolveReferenceTimestamp(entry.date, entry.createdAt)
        return GarageDocumentReminderRules.resolveTargetDate(entry, issueTimestamp)
    }

    private fun scheduleDateReminderIfNeeded(context: Context, entry: GarageDocumentEntry) {
        val targetMillis = resolveTargetDateMillis(entry)
        val fired = GarageReminderSchedule.parseFired(entry.reminderNotifiedStages)
        val alarmAt = targetMillis?.let {
            GarageReminderSchedule.nextDateAlarmMillis(it, System.currentTimeMillis(), fired)
        }
        if (alarmAt == null || alarmAt <= System.currentTimeMillis()) {
            cancelDateReminder(context, entry)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildReminderPendingIntent(context, entry.profileId, entry.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmAt, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmAt, pendingIntent)
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

    private fun showReminderNotification(context: Context, entry: GarageDocumentEntry, notifyKey: String) {
        ensureNotificationChannel(context)

        val profile = ProfileStorage.loadProfiles(context).firstOrNull { it.id == entry.profileId }
        val title = buildNotificationTitle(context, profile, entry)
        val message = buildDocumentStageMessage(context, notifyKey)
        val contentIntent = PendingIntent.getActivity(
            context,
            reminderRequestCode(entry.id),
            openReminderContentIntent(context, entry, notifyKey).apply {
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

    private fun openReminderContentIntent(
        context: Context,
        entry: GarageDocumentEntry,
        notifyKey: String
    ): Intent {
        val dueNow = notifyKey == GarageReminderSchedule.DateStage.DUE_DAY.key
        return if (dueNow) {
            GarageDocumentEntryActivity.createDueReminderIntent(context, entry.profileId, entry.id)
        } else {
            GarageDocumentEntryActivity.createIntent(context, entry.profileId, entry.id)
        }
    }

    private fun buildDocumentStageMessage(context: Context, notifyKey: String): String {
        val stageText = when (notifyKey) {
            GarageReminderSchedule.DateStage.TWO_WEEKS.key ->
                context.getString(R.string.garage_reminder_stage_two_weeks)
            GarageReminderSchedule.DateStage.ONE_WEEK.key ->
                context.getString(R.string.garage_reminder_stage_one_week)
            GarageReminderSchedule.DateStage.THREE_DAYS.key ->
                context.getString(R.string.garage_reminder_stage_days_left, 3)
            GarageReminderSchedule.DateStage.TWO_DAYS.key ->
                context.getString(R.string.garage_reminder_stage_days_left, 2)
            GarageReminderSchedule.DateStage.ONE_DAY.key ->
                context.getString(R.string.garage_reminder_stage_days_left, 1)
            GarageReminderSchedule.DateStage.DUE_DAY.key ->
                context.getString(R.string.garage_reminder_stage_today)
            else -> null
        }
        val body = context.getString(R.string.garage_document_reminder_notification_text)
        return listOfNotNull(stageText, body).joinToString(" ")
    }

    private fun buildNotificationTitle(context: Context, profile: Profile?, entry: GarageDocumentEntry): String {
        val profileName = profile?.name?.trim().orEmpty()
        val documentType = DocumentTypes.localizedLabel(context, entry.documentType.trim())
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