package com.example.clinometer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

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
     * Показва диалог който обяснява защо трябва да се изключи battery optimization
     * и насочва потребителя към Settings
     */
    fun showBatteryOptimizationDialog(context: Context) {
        if (isIgnoringBatteryOptimizations(context)) {
            return // Вече е изключен
        }
        
        AlertDialog.Builder(context)
            .setTitle("Battery Optimization")
            .setMessage(
                "DragMe PRO requires Battery Optimization to be turned off " +
                "(or set to Unrestricted) to ensure accurate GPS tracking.\n\n" +
                "Instructions:\n" +
                "1. Tap 'Open Settings' below\n" +
                "2. Find and select DragMe PRO in the list\n" +
                "3. Set Battery Optimization to 'Don't optimize' or 'Unrestricted'\n\n" +
                "This will improve GPS accuracy and prevent tracking interruptions."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                openBatteryOptimizationSettings(context)
            }
            .setNegativeButton("Skip") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Отваря Battery Optimization Settings
     */
    private fun openBatteryOptimizationSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Директно към списъка с всички апликации за Battery Optimization
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            try {
                // Fallback към app details
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                // Към общи настройки
                val intent = Intent(Settings.ACTION_SETTINGS)
                context.startActivity(intent)
            }
        }
    }
    
    /**
     * Проверява дали трябва да се покаже диалога
     * (Проверява дали вече сме го показали и дали потребителят е казал "Don't ask again")
     */
    fun shouldShowBatteryOptimizationDialog(context: Context): Boolean {
        if (isIgnoringBatteryOptimizations(context)) {
            return false // Вече е изключен
        }
        
        val prefs = context.getSharedPreferences("battery_optimization", Context.MODE_PRIVATE)
        val dontAskAgain = prefs.getBoolean("dont_ask_again", false)
        
        return !dontAskAgain
    }
    
    /**
     * Запазва че потребителят не иска повече да го питаме
     */
    fun setDontAskAgain(context: Context) {
        val prefs = context.getSharedPreferences("battery_optimization", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("dont_ask_again", true).apply()
    }
}

