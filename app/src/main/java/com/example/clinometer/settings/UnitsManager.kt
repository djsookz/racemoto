package com.example.clinometer.settings

import android.content.Context
import androidx.preference.PreferenceManager
import com.example.clinometer.R

/**
 * Manager за единици за измерване
 */
object UnitsManager {
    
    // Preference keys
    private const val PREF_SPEED_UNIT = "speed_unit"
    private const val PREF_DISTANCE_UNIT = "distance_unit"
    private const val PREF_TEMPERATURE_UNIT = "temperature_unit"
    
    // Единици за скорост
    enum class SpeedUnit(val displayNameResId: Int, val symbol: String) {
        KMH(R.string.unit_kmh, "km/h"),
        MPH(R.string.unit_mph, "mph"),
        MS(R.string.unit_ms, "m/s")
    }
    
    // Единици за разстояние
    enum class DistanceUnit(val displayNameResId: Int, val symbol: String) {
        KILOMETERS(R.string.unit_kilometers, "km"),
        MILES(R.string.unit_miles, "mi"),
        METERS(R.string.unit_meters, "m")
    }
    
    // Единици за температура
    enum class TemperatureUnit(val displayNameResId: Int, val symbol: String) {
        CELSIUS(R.string.unit_celsius, "°C"),
        FAHRENHEIT(R.string.unit_fahrenheit, "°F")
    }
    
    // Getter методи
    fun getSpeedUnit(context: Context): SpeedUnit {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val value = prefs.getString(PREF_SPEED_UNIT, SpeedUnit.KMH.name)
        return try {
            SpeedUnit.valueOf(value ?: SpeedUnit.KMH.name)
        } catch (e: IllegalArgumentException) {
            SpeedUnit.KMH
        }
    }
    
    fun getDistanceUnit(context: Context): DistanceUnit {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val value = prefs.getString(PREF_DISTANCE_UNIT, DistanceUnit.KILOMETERS.name)
        return try {
            DistanceUnit.valueOf(value ?: DistanceUnit.KILOMETERS.name)
        } catch (e: IllegalArgumentException) {
            DistanceUnit.KILOMETERS
        }
    }
    
    fun getTemperatureUnit(context: Context): TemperatureUnit {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val value = prefs.getString(PREF_TEMPERATURE_UNIT, TemperatureUnit.CELSIUS.name)
        return try {
            TemperatureUnit.valueOf(value ?: TemperatureUnit.CELSIUS.name)
        } catch (e: IllegalArgumentException) {
            TemperatureUnit.CELSIUS
        }
    }
    
    // Setter методи
    fun setSpeedUnit(context: Context, unit: SpeedUnit) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_SPEED_UNIT, unit.name)
            .apply()
    }
    
    fun setDistanceUnit(context: Context, unit: DistanceUnit) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_DISTANCE_UNIT, unit.name)
            .apply()
    }
    
    fun setTemperatureUnit(context: Context, unit: TemperatureUnit) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_TEMPERATURE_UNIT, unit.name)
            .apply()
    }
    
    // === КОНВЕРСИОННИ МЕТОДИ ===
    
    // Скорост (базова единица: km/h)
    fun convertSpeed(kmh: Float, toUnit: SpeedUnit): Float {
        return when (toUnit) {
            SpeedUnit.KMH -> kmh
            SpeedUnit.MPH -> kmh * 0.621371f
            SpeedUnit.MS -> kmh / 3.6f
        }
    }
    
    fun formatSpeed(kmh: Float, context: Context, decimals: Int = 0): String {
        val unit = getSpeedUnit(context)
        val converted = convertSpeed(kmh, unit)
        return if (decimals == 0) {
            "${converted.toInt()} ${unit.symbol}"
        } else {
            "%.${decimals}f ${unit.symbol}".format(converted)
        }
    }
    
    // Разстояние (базова единица: km)
    fun convertDistance(km: Double, toUnit: DistanceUnit): Double {
        return when (toUnit) {
            DistanceUnit.KILOMETERS -> km
            DistanceUnit.MILES -> km * 0.621371
            DistanceUnit.METERS -> km * 1000.0
        }
    }
    
    fun formatDistance(km: Double, context: Context, decimals: Int = 2): String {
        val unit = getDistanceUnit(context)
        val converted = convertDistance(km, unit)
        return "%.${decimals}f ${unit.symbol}".format(converted)
    }
    
    // Температура (базова единица: °C)
    fun convertTemperature(celsius: Float, toUnit: TemperatureUnit): Float {
        return when (toUnit) {
            TemperatureUnit.CELSIUS -> celsius
            TemperatureUnit.FAHRENHEIT -> celsius * 9f / 5f + 32f
        }
    }
    
    fun formatTemperature(celsius: Float, context: Context, decimals: Int = 1): String {
        val unit = getTemperatureUnit(context)
        val converted = convertTemperature(celsius, unit)
        return "%.${decimals}f${unit.symbol}".format(converted)
    }
    
    // Обратни конверсии (за input fields)
    fun speedToKmh(value: Float, fromUnit: SpeedUnit): Float {
        return when (fromUnit) {
            SpeedUnit.KMH -> value
            SpeedUnit.MPH -> value / 0.621371f
            SpeedUnit.MS -> value * 3.6f
        }
    }
    
    fun distanceToKm(value: Double, fromUnit: DistanceUnit): Double {
        return when (fromUnit) {
            DistanceUnit.KILOMETERS -> value
            DistanceUnit.MILES -> value / 0.621371
            DistanceUnit.METERS -> value / 1000.0
        }
    }
    
    // Helper методи за DRAG режим
    fun getSpeedThreshold100(context: Context): String {
        val unit = getSpeedUnit(context)
        val converted = convertSpeed(100f, unit)
        return "${converted.toInt()} ${unit.symbol}"
    }
    
    fun getSpeedThreshold200(context: Context): String {
        val unit = getSpeedUnit(context)
        val converted = convertSpeed(200f, unit)
        return "${converted.toInt()} ${unit.symbol}"
    }
    
    fun getQuarterMileDistance(context: Context): String {
        // Зависи от скоростта, не от distance unit
        val speedUnit = getSpeedUnit(context)
        return when (speedUnit) {
            SpeedUnit.MPH -> {
                // При mph показваме в мили
                val distInKm = 0.402
                val converted = convertDistance(distInKm, DistanceUnit.MILES)
                String.format("%.2f mi", converted)
            }
            else -> "402m"  // При km/h или m/s остава 402m
        }
    }
}

