package com.example.clinometer

import android.content.Context
import android.os.Build
import android.os.PowerManager

object BatteryOptimizationHelper {
    
    /**
     * Проверява дали апликацията е изключена от battery optimization
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true // На стари версии няма battery optimization
    }

    /**
     * Проверява дали power saving режимът е изключен
     */
    fun isPowerSavingModeOff(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return true
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !powerManager.isPowerSaveMode
    }

    /**
     * Показваме optimization setup страницата, докато и двете стъпки не са завършени.
     * Тоест ако поне една липсва, екранът трябва да се показва.
     */
    fun shouldShowOptimizationSetup(context: Context): Boolean {
        return !isIgnoringBatteryOptimizations(context) || !isPowerSavingModeOff(context)
    }
}

