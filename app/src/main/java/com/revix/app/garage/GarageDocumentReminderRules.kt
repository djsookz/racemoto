package com.revix.app.garage

import com.revix.app.data.GarageDocumentEntry

object GarageDocumentReminderRules {
    fun resolveTargetDate(entry: GarageDocumentEntry, issueTimestamp: Long): Long? {
        if (!entry.reminderEnabled) {
            return null
        }

        return when {
            entry.reminderExactDateMillis != null -> {
                GarageMaintenanceReminderRules.resolveDateTarget(
                    serviceTimestamp = issueTimestamp,
                    mode = GarageReminderMode.EXACT,
                    intervalMonths = null,
                    exactDateMillis = entry.reminderExactDateMillis
                )
            }

            entry.reminderDateIntervalMonths != null -> {
                GarageMaintenanceReminderRules.resolveDateTarget(
                    serviceTimestamp = issueTimestamp,
                    mode = GarageReminderMode.INTERVAL,
                    intervalMonths = entry.reminderDateIntervalMonths,
                    exactDateMillis = null
                )
            }

            else -> entry.expiryDateMillis?.takeIf { it > issueTimestamp }
        }
    }

    fun resolveReminderDate(entry: GarageDocumentEntry, issueTimestamp: Long): Long? {
        val targetMillis = resolveTargetDate(entry, issueTimestamp) ?: return null
        return GarageMaintenanceReminderRules.resolveDateReminder(
            serviceTimestamp = issueTimestamp,
            mode = GarageReminderMode.EXACT,
            intervalMonths = null,
            exactDateMillis = targetMillis,
            leadOption = null
        )
    }
}
