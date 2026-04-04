package com.example.clinometer.garage

import com.example.clinometer.data.GarageDocumentEntry

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
        if (!entry.reminderEnabled) {
            return null
        }

        val leadOption = GarageReminderDateLeadOption.fromStorageKey(entry.reminderDateLeadPreset)
        return when {
            entry.reminderExactDateMillis != null -> {
                GarageMaintenanceReminderRules.resolveDateReminder(
                    serviceTimestamp = issueTimestamp,
                    mode = GarageReminderMode.EXACT,
                    intervalMonths = null,
                    exactDateMillis = entry.reminderExactDateMillis,
                    leadOption = leadOption
                )
            }

            entry.reminderDateIntervalMonths != null -> {
                GarageMaintenanceReminderRules.resolveDateReminder(
                    serviceTimestamp = issueTimestamp,
                    mode = GarageReminderMode.INTERVAL,
                    intervalMonths = entry.reminderDateIntervalMonths,
                    exactDateMillis = null,
                    leadOption = leadOption
                )
            }

            else -> {
                val expiryDateMillis = entry.expiryDateMillis ?: return null
                val reminderDateMillis = leadOption?.subtractFrom(expiryDateMillis) ?: expiryDateMillis
                reminderDateMillis.takeIf { it > issueTimestamp }
            }
        }
    }
}