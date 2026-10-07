package com.example.clinometer.main.map

import com.example.clinometer.*
import android.location.Location
import androidx.lifecycle.ViewModel

/**
 * ViewModel за запазване на състоянието на картата между lifecycle промени
 * Това предотвратява презареждане на картата при връщане в activity-то
 */
class MapStateViewModel : ViewModel() {
    
    // Флаг дали камерата е инициализирана (използва се само при първо зареждане)
    var hasInitializedCamera: Boolean = false
    
    // Последно запазено състояние на картата
    var lastMapState: MapState? = null
    
    // Последна известна локация - използва се за instant display при resume
    var lastKnownLocation: Location? = null
    
    /**
     * Запазва текущото състояние на картата
     */
    fun saveMapState(centerLat: Double, centerLon: Double, zoom: Double, pitch: Double? = null) {
        lastMapState = MapState(
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = zoom,
            pitch = pitch
        )
    }
    
    /**
     * Запазва последната известна локация
     */
    fun saveLastLocation(location: Location) {
        lastKnownLocation = location
    }
    
    /**
     * Изчиства запазеното състояние (използва се при първо зареждане или при нужда)
     */
    fun clearMapState() {
        lastMapState = null
        hasInitializedCamera = false
    }
    
    data class MapState(
        val centerLat: Double,
        val centerLon: Double,
        val zoom: Double,
        val pitch: Double? = null
    )
}

