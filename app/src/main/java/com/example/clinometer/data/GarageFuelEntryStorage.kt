package com.example.clinometer.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class GarageFuelEntry(
    val id: Long = System.currentTimeMillis(),
    val profileId: Long,
    val date: String,
    val station: String,
    val fuelType: String,
    val litres: Double,
    val pricePerLitre: Double,
    val discountAmount: Double = 0.0,
    val totalAmount: Double,
    val odometerKm: Long,
    val isFullTank: Boolean,
    val notes: String,
    val receiptImagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

object GarageFuelEntryStorage {
    private const val PREFS_NAME = "garage_fuel_entries"

    private fun key(profileId: Long): String = "profile_${profileId}_fuel_entries"

    private fun persistEntries(context: Context, profileId: Long, entries: List<GarageFuelEntry>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(profileId), Gson().toJson(entries))
            .apply()
    }

    fun loadEntries(context: Context, profileId: Long): MutableList<GarageFuelEntry> {
        if (profileId == -1L) {
            return mutableListOf()
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(key(profileId), null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<GarageFuelEntry>>() {}.type
        return Gson().fromJson(json, type) ?: mutableListOf()
    }

    fun addEntry(context: Context, entry: GarageFuelEntry) {
        val entries = loadEntries(context, entry.profileId)
        entries.add(0, entry)

        persistEntries(context, entry.profileId, entries)
    }

    fun findEntry(context: Context, profileId: Long, entryId: Long): GarageFuelEntry? {
        return loadEntries(context, profileId).firstOrNull { it.id == entryId }
    }

    fun upsertEntry(context: Context, entry: GarageFuelEntry) {
        val entries = loadEntries(context, entry.profileId)
        val existingIndex = entries.indexOfFirst { it.id == entry.id }

        if (existingIndex >= 0) {
            entries[existingIndex] = entry
        } else {
            entries.add(0, entry)
        }

        persistEntries(context, entry.profileId, entries)
    }

    fun removeEntries(context: Context, profileId: Long, entryIds: Set<Long>): List<GarageFuelEntry> {
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