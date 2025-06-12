package com.example.clinometer

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object RouteStorage {
    private const val FILE_NAME = "routes.json"

    // Записваме списък от Race (включително name)
    fun saveRaces(context: Context, races: List<Race>) {
        val gson = Gson()
        val json = gson.toJson(races)
        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use {
            it.write(json.toByteArray())
        }
    }

    // Зареждаме списък от Race
    fun loadRaces(context: Context): List<Race> {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return emptyList()

            val json = file.readText()
            val type = object : TypeToken<List<Race>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun loadRoutes(context: Context): List<List<RoutePoint>> {
        // TODO: ако имаш стар формат – тук може да се адаптира
        val races = loadRaces(context)
        return races.map { it.routePoints }
    }

    fun saveRoutes(context: Context, routes: List<List<RoutePoint>>) {
        // 1) Зареждаме всички вече записани Race (с техните имена)
        val oldRaces = loadRaces(context)

        // 2) Преобразуваме всяка листа от точки обратно в Race, но
        //    запазваме name от стария запис (ако има такъв)
        val races = routes.map { routePoints ->
            val startTs = routePoints.firstOrNull()?.timestamp ?: 0L
            val endTs   = routePoints.lastOrNull()?.timestamp  ?: 0L
            val duration = endTs - startTs

            // намери стария Race с този ID, за да вземеш името
            val oldName = oldRaces.find { it.id == startTs }?.name

            Race(
                id = startTs,
                name = oldName,           // <—— прехвърляме старото име тук
                timestamp = startTs,
                duration = duration,
                routePoints = routePoints
            )
        }

        // 3) Записваме вече с коректно name
        saveRaces(context, races)
    }


}
