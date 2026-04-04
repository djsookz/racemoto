package com.example.clinometer.garage

import com.example.clinometer.R
import com.example.clinometer.data.GarageMaintenanceEntry
import java.util.Calendar

enum class GarageReminderMode {
    OFF,
    INTERVAL,
    EXACT
}

enum class GarageReminderDateLeadOption(
    val storageKey: String,
    val labelResId: Int
) {
    TWO_DAYS("2d", R.string.garage_maintenance_entry_reminder_lead_2_days),
    ONE_WEEK("1w", R.string.garage_maintenance_entry_reminder_lead_1_week),
    TWO_WEEKS("2w", R.string.garage_maintenance_entry_reminder_lead_2_weeks),
    ONE_MONTH("1m", R.string.garage_maintenance_entry_reminder_lead_1_month);

    fun subtractFrom(targetMillis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = targetMillis
            when (this@GarageReminderDateLeadOption) {
                TWO_DAYS -> add(Calendar.DAY_OF_YEAR, -2)
                ONE_WEEK -> add(Calendar.DAY_OF_YEAR, -7)
                TWO_WEEKS -> add(Calendar.DAY_OF_YEAR, -14)
                ONE_MONTH -> add(Calendar.MONTH, -1)
            }
        }.timeInMillis
    }

    companion object {
        fun fromStorageKey(storageKey: String?): GarageReminderDateLeadOption? {
            return values().firstOrNull { it.storageKey == storageKey }
        }
    }
}

object GarageMaintenanceReminderRules {
    val kmLeadOptions: List<Long> = listOf(50L, 100L, 500L, 1000L)

    fun resolveKmTarget(
        serviceOdometerKm: Long,
        mode: GarageReminderMode,
        value: Long?
    ): Long? {
        val normalizedValue = value?.takeIf { it > 0L } ?: return null
        return when (mode) {
            GarageReminderMode.OFF -> null
            GarageReminderMode.INTERVAL -> serviceOdometerKm + normalizedValue
            GarageReminderMode.EXACT -> normalizedValue.takeIf { it > serviceOdometerKm }
        }
    }

    fun resolveKmReminder(
        serviceOdometerKm: Long,
        mode: GarageReminderMode,
        value: Long?,
        leadKm: Long?
    ): Long? {
        val targetKm = resolveKmTarget(serviceOdometerKm, mode, value) ?: return null
        val normalizedLead = leadKm?.takeIf { it > 0L } ?: return targetKm
        return (targetKm - normalizedLead).takeIf { it > serviceOdometerKm }
    }

    fun resolveDateTarget(
        serviceTimestamp: Long,
        mode: GarageReminderMode,
        intervalMonths: Int?,
        exactDateMillis: Long?
    ): Long? {
        return when (mode) {
            GarageReminderMode.OFF -> null
            GarageReminderMode.INTERVAL -> {
                val months = intervalMonths?.takeIf { it > 0 } ?: return null
                Calendar.getInstance().apply {
                    timeInMillis = serviceTimestamp
                    add(Calendar.MONTH, months)
                }.timeInMillis
            }

            GarageReminderMode.EXACT -> exactDateMillis?.takeIf { it > serviceTimestamp }
        }
    }

    fun resolveDateReminder(
        serviceTimestamp: Long,
        mode: GarageReminderMode,
        intervalMonths: Int?,
        exactDateMillis: Long?,
        leadOption: GarageReminderDateLeadOption?
    ): Long? {
        val targetMillis = resolveDateTarget(serviceTimestamp, mode, intervalMonths, exactDateMillis) ?: return null
        val resolvedReminderMillis = leadOption?.subtractFrom(targetMillis) ?: targetMillis
        return resolvedReminderMillis.takeIf { it > serviceTimestamp }
    }

    fun resolveKmMode(entry: GarageMaintenanceEntry): GarageReminderMode {
        return when {
            entry.reminderExactKm != null -> GarageReminderMode.EXACT
            entry.reminderKmInterval != null -> GarageReminderMode.INTERVAL
            else -> GarageReminderMode.OFF
        }
    }

    fun resolveDateMode(entry: GarageMaintenanceEntry): GarageReminderMode {
        return when {
            entry.reminderExactDateMillis != null -> GarageReminderMode.EXACT
            entry.reminderDateIntervalMonths != null -> GarageReminderMode.INTERVAL
            else -> GarageReminderMode.OFF
        }
    }

    fun resolveKmTarget(entry: GarageMaintenanceEntry): Long? {
        return resolveKmTarget(entry.odometerKm, resolveKmMode(entry), entry.reminderExactKm ?: entry.reminderKmInterval)
    }

    fun resolveKmReminder(entry: GarageMaintenanceEntry): Long? {
        return resolveKmReminder(
            serviceOdometerKm = entry.odometerKm,
            mode = resolveKmMode(entry),
            value = entry.reminderExactKm ?: entry.reminderKmInterval,
            leadKm = entry.reminderKmLeadKm
        )
    }

    fun resolveDateTarget(entry: GarageMaintenanceEntry, serviceTimestamp: Long): Long? {
        return resolveDateTarget(
            serviceTimestamp = serviceTimestamp,
            mode = resolveDateMode(entry),
            intervalMonths = entry.reminderDateIntervalMonths,
            exactDateMillis = entry.reminderExactDateMillis
        )
    }

    fun resolveDateReminder(entry: GarageMaintenanceEntry, serviceTimestamp: Long): Long? {
        return resolveDateReminder(
            serviceTimestamp = serviceTimestamp,
            mode = resolveDateMode(entry),
            intervalMonths = entry.reminderDateIntervalMonths,
            exactDateMillis = entry.reminderExactDateMillis,
            leadOption = GarageReminderDateLeadOption.fromStorageKey(entry.reminderDateLeadPreset)
        )
    }
}