package com.revix.app.data

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
    val createdAt: Long = System.currentTimeMillis(),
    val linkedRaceId: Long? = null,
    val sessionLatitude: Double? = null,
    val sessionLongitude: Double? = null
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
        val loaded = Gson().fromJson<MutableList<GarageFuelEntry>>(json, type) ?: mutableListOf()
        val sanitized = loaded.map { entry ->
            entry.copy(
                date = (entry.date as String?) ?: "",
                station = (entry.station as String?) ?: "",
                fuelType = (entry.fuelType as String?) ?: "",
                notes = (entry.notes as String?) ?: ""
            )
        }.toMutableList()
        if (sanitized != loaded) {
            persistEntries(context, profileId, sanitized)
        }
        return sanitized
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