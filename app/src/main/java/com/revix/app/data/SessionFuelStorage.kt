package com.revix.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object SessionFuelStorage {
    private const val PREFS_NAME = "session_fuel_stops"

    private fun pendingKey(sessionKey: Long): String = "pending_$sessionKey"
    private fun raceKey(raceId: Long): String = "race_$raceId"

    private fun loadList(context: Context, key: String): MutableList<SessionFuelStop> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key, null)
            ?: return mutableListOf()
        val type = object : TypeToken<MutableList<SessionFuelStop>>() {}.type
        val loaded = Gson().fromJson<MutableList<SessionFuelStop>>(json, type) ?: mutableListOf()
        val sanitized = loaded.map { stop ->
            stop.copy(station = (stop.station as String?) ?: "")
        }.toMutableList()
        if (sanitized != loaded) {
            saveList(context, key, sanitized)
        }
        return sanitized
    }

    private fun saveList(context: Context, key: String, stops: List<SessionFuelStop>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key, Gson().toJson(stops))
            .apply()
    }

    fun addPendingStop(context: Context, sessionKey: Long, stop: SessionFuelStop) {
        if (sessionKey <= 0L) return
        val stops = loadList(context, pendingKey(sessionKey))
        stops.add(stop)
        saveList(context, pendingKey(sessionKey), stops)
    }

    fun getPendingStops(context: Context, sessionKey: Long): List<SessionFuelStop> {
        if (sessionKey <= 0L) return emptyList()
        return loadList(context, pendingKey(sessionKey)).sortedBy { it.timestamp }
    }

    fun linkSessionToRace(context: Context, sessionKey: Long, raceId: Long) {
        if (sessionKey <= 0L || raceId <= 0L) return
        val pending = loadList(context, pendingKey(sessionKey))
        if (pending.isEmpty()) return

        val existing = loadList(context, raceKey(raceId))
        existing.addAll(pending)
        saveList(context, raceKey(raceId), existing.sortedBy { it.timestamp })

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(pendingKey(sessionKey))
            .apply()
    }

    fun getStopsForRace(context: Context, raceId: Long): List<SessionFuelStop> {
        if (raceId <= 0L) return emptyList()
        return loadList(context, raceKey(raceId)).sortedBy { it.timestamp }
    }

    fun clearPending(context: Context, sessionKey: Long) {
        if (sessionKey <= 0L) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(pendingKey(sessionKey))
            .apply()
    }
}
