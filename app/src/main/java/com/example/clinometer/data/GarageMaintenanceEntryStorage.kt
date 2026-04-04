package com.example.clinometer.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class GarageMaintenanceEntry(
    val id: Long = System.currentTimeMillis(),
    val profileId: Long,
    val date: String,
    val serviceType: String,
    val partsCost: Double,
    val laborCost: Double,
    val odometerKm: Long,
    val description: String,
    val receiptImagePath: String? = null,
    val reminderEnabled: Boolean = false,
    val reminderKmInterval: Long? = null,
    val reminderExactKm: Long? = null,
    val reminderKmLeadKm: Long? = null,
    val reminderDateIntervalMonths: Int? = null,
    val reminderExactDateMillis: Long? = null,
    val reminderDateLeadPreset: String? = null,
    val reminderTriggeredAt: Long? = null,
    val reminderTriggeredBy: String? = null,
    val reminderCompletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

object GarageMaintenanceEntryStorage {
    private const val PREFS_NAME = "garage_maintenance_entries"

    private fun key(profileId: Long): String = "profile_${profileId}_maintenance_entries"

    private fun persistEntries(context: Context, profileId: Long, entries: List<GarageMaintenanceEntry>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(profileId), Gson().toJson(entries))
            .apply()
    }

    fun loadEntries(context: Context, profileId: Long): MutableList<GarageMaintenanceEntry> {
        if (profileId == -1L) {
            return mutableListOf()
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(key(profileId), null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<GarageMaintenanceEntry>>() {}.type
        return Gson().fromJson(json, type) ?: mutableListOf()
    }

    fun upsertEntry(context: Context, entry: GarageMaintenanceEntry) {
        val entries = loadEntries(context, entry.profileId)
        val existingIndex = entries.indexOfFirst { it.id == entry.id }

        if (existingIndex >= 0) {
            entries[existingIndex] = entry
        } else {
            entries.add(0, entry)
        }

        persistEntries(context, entry.profileId, entries)
    }

    fun findEntry(context: Context, profileId: Long, entryId: Long): GarageMaintenanceEntry? {
        return loadEntries(context, profileId).firstOrNull { it.id == entryId }
    }

    fun removeEntries(context: Context, profileId: Long, entryIds: Set<Long>): List<GarageMaintenanceEntry> {
        if (profileId == -1L || entryIds.isEmpty()) {
            return emptyList()
        }

        val entries = loadEntries(context, profileId)
        val removedEntries = entries.filter { it.id in entryIds }
        if (removedEntries.isEmpty()) {
            return emptyList()
        }

        entries.removeAll { it.id in entryIds }
        persistEntries(context, profileId, entries)
        return removedEntries
    }

    fun getCount(context: Context, profileId: Long): Int {
        return loadEntries(context, profileId).size
    }
}