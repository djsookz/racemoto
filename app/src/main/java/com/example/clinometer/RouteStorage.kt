package com.example.clinometer

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream


object RouteStorage {
    private const val RACES_FILE = "races.json"
    private const val POINTS_DIR = "route_points"

    // Запазване на метаданни за сесиите
    fun saveRaces(context: Context, races: List<Race>) {
        synchronized(this) {
            try {
                val gson = GsonBuilder().create()
                val json = gson.toJson(races)
                context.openFileOutput(RACES_FILE, Context.MODE_PRIVATE).use {
                    it.write(json.toByteArray())
                }
            } catch (e: Exception) {
                Log.e("RouteStorage", "Error saving races", e)
            }
        }
    }

    // Запазване на точките за конкретна сесия
    fun saveRoutePoints(context: Context, raceId: Long, points: List<RoutePoint>) {
        synchronized(this) {
            try {
                // Създаваме директория ако не съществува
                val dir = File(context.filesDir, POINTS_DIR)
                if (!dir.exists()) dir.mkdirs()

                // Записваме точките във файл
                val file = File(dir, "points_$raceId.json")
                val gson = GsonBuilder().create()
                val json = gson.toJson(points)
                FileOutputStream(file).use {
                    it.write(json.toByteArray())
                }
            } catch (e: Exception) {
                Log.e("RouteStorage", "Error saving points", e)
            }
        }
    }

    // Зареждане на метаданни
    fun loadRaces(context: Context): List<Race> {
        synchronized(this) {
            return try {
                val file = File(context.filesDir, RACES_FILE)
                if (!file.exists()) return emptyList()

                val json = file.readText()
                val type = object : TypeToken<List<Race>>() {}.type
                Gson().fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                Log.e("RouteStorage", "Error loading races", e)
                emptyList()
            }
        }
    }

    // Зареждане на точки за конкретна сесия
    fun loadRoutePoints(context: Context, raceId: Long): List<RoutePoint> {
        synchronized(this) {
            return try {
                val file = File(File(context.filesDir, POINTS_DIR), "points_$raceId.json")
                if (!file.exists()) return emptyList()

                val json = file.readText()
                val type = object : TypeToken<List<RoutePoint>>() {}.type
                Gson().fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                Log.e("RouteStorage", "Error loading points", e)
                emptyList()
            }
        }
    }
}
