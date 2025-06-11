package com.example.clinometer

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object RouteStorage {
    private const val FILE_NAME = "routes.json"

    fun saveRoutes(context: Context, routes: List<List<RoutePoint>>) {
        val gson = Gson()
        val json = gson.toJson(routes)
        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use {
            it.write(json.toByteArray())
        }
    }

    fun loadRoutes(context: Context): List<List<RoutePoint>> {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return emptyList()

            val json = file.readText()
            val type = object : TypeToken<List<List<RoutePoint>>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
