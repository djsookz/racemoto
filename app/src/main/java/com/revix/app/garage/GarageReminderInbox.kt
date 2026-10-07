package com.revix.app.garage

import android.content.Context
import android.content.Intent
import com.revix.app.Profile
import com.revix.app.R
import com.revix.app.data.GarageDocumentEntry
import com.revix.app.data.GarageDocumentEntryStorage
import com.revix.app.data.GarageMaintenanceEntry
import com.revix.app.data.GarageMaintenanceEntryStorage
import com.revix.app.data.GarageOdometerSource
import com.revix.app.data.GarageOdometerTimeline
import com.revix.app.data.ProfileStorage

object GarageReminderInbox {
    private const val PREFS_NAME = "garage_reminder_inbox"
    private const val KEY_DISMISSED_HOME = "dismissed_home_keys"

    data class Item(
        val profileId: Long,
        val entryId: Long,
        val isDocument: Boolean,
        val profileName: String,
        val title: String,
        val iconRes: Int,
        val phase: GarageReminderSchedule.HomePhase,
        val daysUntil: Int?,
        val remainingKm: Long?,
        val sortScore: Int
    ) {
        fun openIntent(context: Context): Intent {
            val showDueActions = phase == GarageReminderSchedule.HomePhase.DUE_TODAY ||
                phase == GarageReminderSchedule.HomePhase.OVERDUE
            return if (isDocument) {
                if (showDueActions) {
                    GarageDocumentEntryActivity.createDueReminderIntent(context, profileId, entryId)
                } else {
                    GarageDocumentEntryActivity.createIntent(context, profileId, entryId)
                }
            } else if (showDueActions) {
                GarageMaintenanceEntryActivity.createDueReminderIntent(context, profileId, entryId)
            } else {
                GarageMaintenanceEntryActivity.createIntent(context, profileId, entryId)
            }.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }

    fun refreshAll(context: Context) {
        ProfileStorage.loadProfiles(context).forEach { profile ->
            GarageMaintenanceReminderManager.evaluateDueRemindersForProfile(context, profile.id)
            GarageDocumentReminderManager.evaluateDueRemindersForProfile(context, profile.id)
        }
    }

    fun visibleHomeItems(context: Context, nowMillis: Long = System.currentTimeMillis()): List<Item> {
        val profiles = ProfileStorage.loadProfiles(context)
        val items = mutableListOf<Item>()
        profiles.forEach { profile ->
            GarageMaintenanceEntryStorage.loadEntries(context, profile.id)
                .mapNotNull { entry -> maintenanceItem(context, profile, entry, nowMillis) }
                .filter { it.phase != GarageReminderSchedule.HomePhase.UPCOMING }
                .filter { !isHomeItemDismissed(context, it) }
                .let(items::addAll)
            GarageDocumentEntryStorage.loadEntries(context, profile.id)
                .mapNotNull { entry -> documentItem(context, profile, entry, nowMillis) }
                .filter { it.phase != GarageReminderSchedule.HomePhase.UPCOMING }
                .filter { !isHomeItemDismissed(context, it) }
                .let(items::addAll)
        }
        return items.sortedBy { it.sortScore }
    }

    fun dismissHomeItem(context: Context, item: Item) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dismissed = prefs.getStringSet(KEY_DISMISSED_HOME, emptySet()).orEmpty().toMutableSet()
        dismissed.add(homeDismissKey(item))
        prefs.edit().putStringSet(KEY_DISMISSED_HOME, dismissed).apply()
    }

    private fun isHomeItemDismissed(context: Context, item: Item): Boolean {
        val dismissed = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_DISMISSED_HOME, emptySet())
            .orEmpty()
        return homeDismissKey(item) in dismissed
    }

    private fun homeDismissKey(item: Item): String {
        val kind = if (item.isDocument) "d" else "m"
        return "${item.profileId}:$kind:${item.entryId}:${item.phase.name}"
    }

    private fun maintenanceItem(
        context: Context,
        profile: Profile,
        entry: GarageMaintenanceEntry,
        nowMillis: Long
    ): Item? {
        if (!entry.reminderEnabled || entry.reminderCompletedAt != null) {
            return null
        }
        val serviceTimestamp = GarageOdometerTimeline.resolveReferenceTimestamp(entry.date, entry.createdAt)
        val targetKm = GarageMaintenanceReminderRules.resolveKmTarget(entry)
        val latestKm = GarageOdometerTimeline.latestRecordedOdometerFrom(
            context = context,
            profileId = entry.profileId,
            source = GarageOdometerSource.MAINTENANCE,
            entryId = entry.id,
            dateText = entry.date,
            fallbackTimestamp = entry.createdAt
        )
        val remainingKm = targetKm?.let { it - (latestKm ?: entry.odometerKm) }
        val targetDate = GarageMaintenanceReminderRules.resolveDateTarget(entry, serviceTimestamp)
        val daysUntil = targetDate?.let { GarageReminderSchedule.daysUntil(it, nowMillis) }
        val phase = GarageReminderSchedule.homePhase(daysUntil, remainingKm)
        val title = MaintenanceServiceTypes.localizedLabel(context, entry.serviceType)
            .ifBlank { context.getString(R.string.garage_profile_maintenance_reminder_badge) }
        return Item(
            profileId = profile.id,
            entryId = entry.id,
            isDocument = false,
            profileName = profile.name.trim(),
            title = title,
            iconRes = GarageMaintenanceServiceIcons.resolveIconRes(entry.serviceType) ?: R.drawable.ic_wrench,
            phase = phase,
            daysUntil = daysUntil,
            remainingKm = remainingKm,
            sortScore = GarageReminderSchedule.sortScore(phase, daysUntil, remainingKm)
        )
    }

    private fun documentItem(
        context: Context,
        profile: Profile,
        entry: GarageDocumentEntry,
        nowMillis: Long
    ): Item? {
        if (!entry.reminderEnabled || entry.reminderCompletedAt != null) {
            return null
        }
        val issueTimestamp = GarageOdometerTimeline.resolveReferenceTimestamp(entry.date, entry.createdAt)
        val targetDate = GarageDocumentReminderRules.resolveTargetDate(entry, issueTimestamp) ?: return null
        val daysUntil = GarageReminderSchedule.daysUntil(targetDate, nowMillis)
        val phase = GarageReminderSchedule.homePhase(daysUntil, remainingKm = null)
        val title = DocumentTypes.localizedLabel(context, entry.documentType)
            .ifBlank { context.getString(R.string.garage_document_entry_title) }
        return Item(
            profileId = profile.id,
            entryId = entry.id,
            isDocument = true,
            profileName = profile.name.trim(),
            title = title,
            iconRes = GarageDocumentTypeIcons.resolveIconRes(entry.documentType) ?: R.drawable.ic_tab_document,
            phase = phase,
            daysUntil = daysUntil,
            remainingKm = null,
            sortScore = GarageReminderSchedule.sortScore(phase, daysUntil, null)
        )
    }
}
