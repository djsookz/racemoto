package com.example.clinometer.settings

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Manages map provider - now only Mapbox is supported
 */
object MapProviderManager {
    
    enum class MapProvider {
        MAPBOX     // Mapbox only
    }
    
    private const val PREF_MAP_PROVIDER = "map_provider"
    private const val DEFAULT_PROVIDER = "mapbox"
    
    /**
     * Get current map provider (always Mapbox)
     */
    fun getMapProvider(context: Context): MapProvider {
        return MapProvider.MAPBOX
    }
    
    /**
     * Set map provider (always Mapbox)
     */
    fun setMapProvider(context: Context, provider: MapProvider) {
        // Always Mapbox, no need to save
    }
    
    /**
     * Get provider display name
     */
    fun getProviderDisplayName(provider: MapProvider): String {
        return "Mapbox"
    }
}

