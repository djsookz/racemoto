package com.example.clinometer

import android.content.Context
import android.util.Log
import java.io.File

object NavigationDataCache {
    private const val TAG = "NavigationDataCache"
    private const val CACHE_DIR = "navigation_cache"
    private const val ROUTE_GEOMETRY_FILE = "route_geometry.json"
    private const val DIRECTIONS_RESPONSE_FILE = "directions_response.json"
    
    fun saveRouteGeometry(context: Context, geometryJson: String) {
        try {
            val cacheDir = File(context.cacheDir, CACHE_DIR)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val file = File(cacheDir, ROUTE_GEOMETRY_FILE)
            file.writeText(geometryJson)
            Log.d(TAG, "Saved route geometry to cache (${geometryJson.length} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save route geometry to cache", e)
            throw e
        }
    }
    
    fun loadRouteGeometry(context: Context): String? {
        try {
            val file = File(File(context.cacheDir, CACHE_DIR), ROUTE_GEOMETRY_FILE)
            return if (file.exists()) {
                val content = file.readText()
                Log.d(TAG, "Loaded route geometry from cache (${content.length} bytes)")
                content
            } else {
                Log.d(TAG, "Route geometry file not found in cache")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load route geometry from cache", e)
            return null
        }
    }
    
    fun saveDirectionsResponse(context: Context, json: String) {
        try {
            val cacheDir = File(context.cacheDir, CACHE_DIR)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val file = File(cacheDir, DIRECTIONS_RESPONSE_FILE)
            file.writeText(json)
            Log.d(TAG, "Saved directions response to cache (${json.length} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save directions response to cache", e)
            throw e
        }
    }
    
    fun loadDirectionsResponse(context: Context): String? {
        try {
            val file = File(File(context.cacheDir, CACHE_DIR), DIRECTIONS_RESPONSE_FILE)
            return if (file.exists()) {
                val content = file.readText()
                Log.d(TAG, "Loaded directions response from cache (${content.length} bytes)")
                content
            } else {
                Log.d(TAG, "Directions response file not found in cache")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load directions response from cache", e)
            return null
        }
    }
    
    fun clear(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, CACHE_DIR)
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                Log.d(TAG, "Cleared navigation data cache")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear navigation data cache", e)
        }
    }
}

