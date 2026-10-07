package com.revix.app

import android.app.Application
import android.util.Log
import com.revix.app.billing.PlayBillingManager
import com.revix.app.billing.FreeProfilePickerCoordinator
import com.revix.app.main.ui.ScreenKeepOnController
import com.revix.app.settings.LanguageManager
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp

/**
 * Custom Application class for initializing global components
 */
class MyApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()

        ScreenKeepOnController.init(this)
        installStickyImmersiveMode()
        
        // Initialize Drag calibration
        DragCalibration.init(this)

        LanguageManager.ensureInitialLanguage(this)

        PlayBillingManager.start(this)
        FreeProfilePickerCoordinator.install(this)
        
        // Initialize Mapbox Navigation SDK
        if (!MapboxNavigationApp.isSetup()) {
            MapboxNavigationApp.setup(
                NavigationOptions.Builder(this)
                    // The accessToken is automatically read from the 'mapbox_access_token' string resource
                    // or from the Mapbox configuration. In SDK v3, we don't need to set it explicitly
                    // via NavigationOptions.Builder if it is correctly defined in resources.
                    .build()
            )
        }
        
        Log.d("MyApplication", "Application started - Mapbox Navigation initialized")
    }
}
