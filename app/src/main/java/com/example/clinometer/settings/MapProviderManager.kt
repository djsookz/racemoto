package com.example.clinometer.settings

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Manages map provider selection (OSMDroid vs Mapbox)
 */
object MapProviderManager {
    
    enum class MapProvider {
        OSMDROID,  // Default - OSMDroid
        MAPBOX     // Mapbox
    }
    
    private const val PREF_MAP_PROVIDER = "map_provider"
    private const val DEFAULT_PROVIDER = "osmdroid"
    
    /**
     * Get current map provider
     */
    fun getMapProvider(context: Context): MapProvider {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val providerString = prefs.getString(PREF_MAP_PROVIDER, DEFAULT_PROVIDER) ?: DEFAULT_PROVIDER
        return when (providerString.lowercase()) {
            "mapbox" -> MapProvider.MAPBOX
            else -> MapProvider.OSMDROID
        }
    }
    
    /**
     * Set map provider
     */
    fun setMapProvider(context: Context, provider: MapProvider) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val providerString = when (provider) {
            MapProvider.MAPBOX -> "mapbox"
            MapProvider.OSMDROID -> "osmdroid"
        }
        prefs.edit().putString(PREF_MAP_PROVIDER, providerString).apply()
    }
    
    /**
     * Get provider display name
     */
    fun getProviderDisplayName(provider: MapProvider): String {
        return when (provider) {
            MapProvider.OSMDROID -> "OSMDroid (Default)"
            MapProvider.MAPBOX -> "Mapbox"
        }
    }
}

