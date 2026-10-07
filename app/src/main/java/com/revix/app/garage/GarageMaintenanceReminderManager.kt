package com.revix.app.garage

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
import com.revix.app.Profile
import com.revix.app.R
import com.revix.app.data.GarageMaintenanceEntry
import com.revix.app.data.GarageMaintenanceEntryStorage
import com.revix.app.data.GarageOdometerSource
import com.revix.app.data.GarageOdometerTimeline
import com.revix.app.data.ProfileStorage

object GarageMaintenanceReminderManager {
    private const val CHANNEL_ID = "garage_maintenance_reminders"
    private const val CHANNEL_NAME = "Garage reminders"
    private const val EXTRA_PROFILE_ID = "reminder_profile_id"
    private const val EXTRA_ENTRY_ID = "reminder_entry_id"

    fun syncReminder(context: Context, entry: GarageMaintenanceEntry) {
        if (!entry.reminderEnabled || entry.reminderCompletedAt != null) {
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
            .filter { it.reminderEnabled && it.reminderCompletedAt == null }
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
                if (entry.reminderEnabled && entry.reminderCompletedAt == null) {
                    maybeTriggerReminder(context, entry)
                } else {
                    cancelDateReminder(context, entry)
                }
            }
        }
    }

    private fun maybeTriggerReminder(context: Context, entry: GarageMaintenanceEntry): Boolean {
        if (!entry.reminderEnabled || entry.reminderCompletedAt != null) {
            return false
        }

        val now = System.currentTimeMillis()
        val snapshot = reminderSnapshot(context, entry, now)
        val fired = GarageReminderSchedule.parseFired(entry.reminderNotifiedStages).toMutableSet()
        if (entry.reminderTriggeredAt != null && entry.reminderNotifiedStages.isNullOrBlank()) {
            seedLegacyFiredStages(snapshot, fired)
        }

        val dateKeyToFire = snapshot.dateStageToday?.key?.takeIf { it !in fired }
        val missedDateKeys = snapshot.missedDateStages.map { it.key }.filter { it !in fired }
        fired.addAll(missedDateKeys)

        val kmKeyToFire = snapshot.kmStage?.key?.takeIf { it !in fired }
        snapshot.skippedKmStages.forEach { stage -> fired.add(stage.key) }

        val notifyKey = dateKeyToFire ?: kmKeyToFire
        if (notifyKey == null) {
            persistFiredIfChanged(context, entry, fired)
            scheduleDateReminderIfNeeded(context, entry.copy(reminderNotifiedStages = GarageReminderSchedule.encodeFired(fired)))
            return false
        }

        if (!hasNotificationPermission(context)) {
            persistFiredIfChanged(context, entry, fired)
            scheduleDateReminderIfNeeded(context, entry.copy(reminderNotifiedStages = GarageReminderSchedule.encodeFired(fired)))
            return false
        }

        fired.add(notifyKey)

        showReminderNotification(context, entry, snapshot, notifyKey)
        val updated = entry.copy(
            reminderNotifiedStages = GarageReminderSchedule.encodeFired(fired),
            reminderTriggeredAt = entry.reminderTriggeredAt ?: now,
            reminderTriggeredBy = if (notifyKey.startsWith("k")) {
                ReminderTriggerReason.KILOMETERS.name
            } else {
                ReminderTriggerReason.DATE.name
            }
        )
        GarageMaintenanceEntryStorage.upsertEntry(context, updated)
        scheduleDateReminderIfNeeded(context, updated)
        return true
    }

    private data class ReminderSnapshot(
        val targetKm: Long?,
        val remainingKm: Long?,
        val targetDateMillis: Long?,
        val daysUntil: Int?,
        val dateStageToday: GarageReminderSchedule.DateStage?,
        val missedDateStages: List<GarageReminderSchedule.DateStage>,
        val kmStage: GarageReminderSchedule.KmStage?,
        val skippedKmStages: List<GarageReminderSchedule.KmStage>
    )

    private fun reminderSnapshot(
        context: Context,
        entry: GarageMaintenanceEntry,
        nowMillis: Long
    ): ReminderSnapshot {
        val serviceTimestamp = resolveServiceTimestamp(entry)
        val targetKm = GarageMaintenanceReminderRules.resolveKmTarget(entry)
        val latestKm = GarageOdometerTimeline.latestRecordedOdometerFrom(
            context = context,
            profileId = entry.profileId,
            source = GarageOdometerSource.MAINTENANCE,
            entryId = entry.id,
            dateText = entry.date,
            fallbackTimestamp = entry.createdAt
        )
        val remainingKm = if (targetKm != null) {
            targetKm - (latestKm ?: entry.odometerKm)
        } else {
            null
        }
        val kmStage = remainingKm?.let { GarageReminderSchedule.kmStageForRemaining(it) }
        val targetDateMillis = GarageMaintenanceReminderRules.resolveDateTarget(entry, serviceTimestamp)
        val daysUntil = targetDateMillis?.let { GarageReminderSchedule.daysUntil(it, nowMillis) }
        return ReminderSnapshot(
            targetKm = targetKm,
            remainingKm = remainingKm,
            targetDateMillis = targetDateMillis,
            daysUntil = daysUntil,
            dateStageToday = daysUntil?.let { GarageReminderSchedule.dateStageToday(it) },
            missedDateStages = daysUntil?.let { GarageReminderSchedule.missedDateStages(it) }.orEmpty(),
            kmStage = kmStage,
            skippedKmStages = kmStage?.let { GarageReminderSchedule.skippedKmStagesBefore(it) }.orEmpty()
        )
    }

    private fun seedLegacyFiredStages(snapshot: ReminderSnapshot, fired: MutableSet<String>) {
        snapshot.missedDateStages.forEach { fired.add(it.key) }
        snapshot.dateStageToday?.let { fired.add(it.key) }
        snapshot.skippedKmStages.forEach { fired.add(it.key) }
        snapshot.kmStage?.let { fired.add(it.key) }
    }

    private fun persistFiredIfChanged(
        context: Context,
        entry: GarageMaintenanceEntry,
        fired: Set<String>
    ) {
        val encoded = GarageReminderSchedule.encodeFired(fired)
        if (encoded == entry.reminderNotifiedStages) {
            return
        }
        GarageMaintenanceEntryStorage.upsertEntry(context, entry.copy(reminderNotifiedStages = encoded))
    }

    private fun scheduleDateReminderIfNeeded(context: Context, entry: GarageMaintenanceEntry) {
        val serviceTimestamp = resolveServiceTimestamp(entry)
        val targetDateMillis = GarageMaintenanceReminderRules.resolveDateTarget(entry, serviceTimestamp)
        val fired = GarageReminderSchedule.parseFired(entry.reminderNotifiedStages)
        val alarmAt = targetDateMillis?.let {
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

    private fun resolveServiceTimestamp(entry: GarageMaintenanceEntry): Long {
        return GarageOdometerTimeline.resolveReferenceTimestamp(entry.date, entry.createdAt)
    }

    private fun showReminderNotification(
        context: Context,
        entry: GarageMaintenanceEntry,
        snapshot: ReminderSnapshot,
        notifyKey: String
    ) {
        ensureNotificationChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            reminderRequestCode(entry.id),
            openReminderContentIntent(context, entry, notifyKey).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val profile = ProfileStorage.loadProfiles(context).firstOrNull { it.id == entry.profileId }
        val title = buildNotificationTitle(context, profile, entry)
        val message = buildStageNotificationMessage(context, profile, snapshot, notifyKey)

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
        entry: GarageMaintenanceEntry,
        notifyKey: String
    ): Intent {
        val dueNow = notifyKey == GarageReminderSchedule.DateStage.DUE_DAY.key ||
            notifyKey == GarageReminderSchedule.KmStage.DUE.key
        return if (dueNow) {
            GarageMaintenanceEntryActivity.createDueReminderIntent(context, entry.profileId, entry.id)
        } else {
            GarageMaintenanceEntryActivity.createIntent(context, entry.profileId, entry.id)
        }
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
        val serviceType = MaintenanceServiceTypes.localizedLabel(context, entry.serviceType)

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

    private fun buildStageNotificationMessage(
        context: Context,
        profile: Profile?,
        snapshot: ReminderSnapshot,
        notifyKey: String
    ): String {
        val vehicleText = when (profile?.vehicleType) {
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
        val remainingKmText = snapshot.remainingKm
            ?.coerceAtLeast(0L)
            ?.toInt()
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
            GarageReminderSchedule.KmStage.APPROACHING.key,
            GarageReminderSchedule.KmStage.SOON.key ->
                remainingKmText?.let { context.getString(R.string.garage_reminder_stage_km_left, it) }
            GarageReminderSchedule.KmStage.DUE.key ->
                context.getString(R.string.garage_reminder_stage_km_due)
            else -> null
        }
        return listOfNotNull(stageText, vehicleText).joinToString(" ")
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