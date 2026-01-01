package com.example.clinometer

import android.app.Application
import android.util.Log

/**
 * Custom Application class за инициализация на глобални компоненти
 */
class MyApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Зареждаме drag калибрацията от SharedPreferences
        DragCalibration.init(this)
        
        // Mapbox initialization removed - will be initialized directly in MapboxTestActivity
        // val mapProvider = MapProviderManager.getMapProvider(this)
        // if (mapProvider == MapProviderManager.MapProvider.MAPBOX) {
        //     MapboxHelper.initialize(this)
        // }
        
        Log.d("MyApplication", "Application started - DragCalibration initialized")
    }
}
