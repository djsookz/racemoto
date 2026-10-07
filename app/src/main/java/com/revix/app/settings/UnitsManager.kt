package com.revix.app.settings

import android.content.Context
import androidx.preference.PreferenceManager
import com.revix.app.R

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

    // Symbol of the currently selected speed unit (km/h, mph, m/s).
    fun getSpeedUnitSymbol(context: Context): String = getSpeedUnit(context).symbol

    // Speed value only (no unit), converted to the selected unit and rounded to a whole number.
    // Use this for HUD TextViews that show the number and the unit label separately.
    fun formatSpeedValueWhole(kmh: Float, context: Context): String {
        return Math.round(convertSpeed(kmh, getSpeedUnit(context))).toString()
    }

    // Extracts the leading numeric part of a stored string like "231 km/h", "231", "40 km/h".
    private fun parseLeadingNumber(stored: String?): Float? {
        if (stored.isNullOrBlank()) return null
        val match = Regex("-?\\d+(?:[.,]\\d+)?").find(stored) ?: return null
        return match.value.replace(',', '.').toFloatOrNull()
    }

    // Formats a speed value that was persisted in the base unit (km/h), possibly as a string
    // such as "231 km/h" or "231". Re-converts to the currently selected unit so changing the
    // setting updates historical sessions too. Returns the placeholder unchanged if unparsable.
    fun formatStoredSpeed(stored: String?, context: Context, withUnit: Boolean = true, placeholder: String = "--"): String {
        val kmh = parseLeadingNumber(stored)
            ?: return if (withUnit) "$placeholder ${getSpeedUnitSymbol(context)}" else placeholder
        val unit = getSpeedUnit(context)
        val converted = Math.round(convertSpeed(kmh, unit))
        return if (withUnit) "$converted ${unit.symbol}" else converted.toString()
    }

    // Formats a temperature persisted in the base unit (°C), possibly as a string such as "12°C"
    // or "12". If a value already carries °F it is interpreted accordingly so old saved sessions
    // keep displaying correctly. Re-converts to the selected unit.
    fun formatStoredTemperature(stored: String?, context: Context, placeholder: String = "--"): String {
        val number = parseLeadingNumber(stored)
            ?: return "$placeholder${getTemperatureUnit(context).symbol}"
        val celsius = if (stored?.contains("°F") == true) (number - 32f) * 5f / 9f else number
        return formatTemperature(celsius, context, decimals = 0)
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

    // Trip-scale distance: kilometres or miles only. Meters (paired with the m/s speed unit) is
    // impractical for a whole trip, so it falls back to kilometres. Use for the driving HUD and
    // session distance readouts.
    fun isTripDistanceMiles(context: Context): Boolean =
        getDistanceUnit(context) == DistanceUnit.MILES

    fun getTripDistanceUnitSymbol(context: Context): String =
        if (isTripDistanceMiles(context)) "mi" else "km"

    fun convertTripDistanceFromKm(km: Double, context: Context): Double =
        if (isTripDistanceMiles(context)) km * 0.621371 else km

    fun formatTripDistanceValue(km: Double, context: Context, decimals: Int = 2): String =
        "%.${decimals}f".format(convertTripDistanceFromKm(km, context))

    fun convertTripDistanceToKm(value: Double, context: Context): Double =
        if (isTripDistanceMiles(context)) value / 0.621371 else value

    // === ODOMETER (whole-number distance, e.g. garage mileage) ===
    // Stored internally in whole kilometres. These helpers convert to/from the displayed unit
    // (km or mi) so all interval/economy math stays in km while the user sees/enters their unit.
    fun getOdometerUnitSymbol(context: Context): String = getTripDistanceUnitSymbol(context)

    fun kmToOdometerValue(km: Long, context: Context): Long =
        Math.round(convertTripDistanceFromKm(km.toDouble(), context))

    fun odometerValueToKm(displayValue: Long, context: Context): Long =
        Math.round(convertTripDistanceToKm(displayValue.toDouble(), context))

    // Formats a stored km value as a localized whole number in the displayed unit (no unit suffix).
    fun formatOdometerValue(km: Long, context: Context): String =
        java.text.NumberFormat.getIntegerInstance(java.util.Locale.US)
            .format(kmToOdometerValue(km, context))
    
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

    /** Drag UI използва mph/imperial етикети когато скоростта е в mph (същата логика като getQuarterMileDistance). */
    fun isDragImperial(context: Context): Boolean = getSpeedUnit(context) == SpeedUnit.MPH

    /** Компактен speed interval label за drag cards/grafики: "0-100" или "0-62". */
    fun formatDragSpeedIntervalLabel(fromKmh: Int, toKmh: Int, context: Context): String {
        val unit = getSpeedUnit(context)
        val from = if (fromKmh <= 0) 0 else convertSpeed(fromKmh.toFloat(), unit).toInt()
        val to = convertSpeed(toKmh.toFloat(), unit).toInt()
        return if (from <= 0) "0-$to" else "$from-$to"
    }

    /** Компактен distance split label: "50M" или "0.03MI" / "0.25MI" при mph. */
    fun formatDragSplitDistanceLabel(meters: Int, context: Context): String {
        if (!isDragImperial(context)) return "${meters}M"
        if (meters == 402) {
            return getQuarterMileDistance(context).replace(" ", "").uppercase(java.util.Locale.US)
        }
        val miles = meters / 1609.344
        return String.format(java.util.Locale.US, "%.2fMI", miles)
    }

    /** Label за 0-402/quarter mile interval: "0-402M" или "0-0.25MI". */
    fun formatDragZeroTo402IntervalLabel(context: Context): String {
        if (!isDragImperial(context)) return "0-402M"
        val quarter = getQuarterMileDistance(context).replace(" ", "").uppercase(java.util.Locale.US)
        return "0-$quarter"
    }

    /** Compare screen – full row label: "0 - 402M" (metric) or localized imperial quarter-mile label. */
    fun formatDragCompareZeroTo402Label(context: Context): String {
        return if (isDragImperial(context)) {
            context.getString(R.string.drag_compare_metric_0_402_imperial)
        } else {
            context.getString(R.string.drag_compare_metric_0_402)
        }
    }

    /** Compare screen – center short label: "402M" (metric) or localized quarter-mile label. */
    fun formatDragCompareShortZeroTo402Label(context: Context): String {
        return if (isDragImperial(context)) {
            context.getString(R.string.drag_compare_metric_short_quarter_mile)
        } else {
            context.getString(R.string.drag_compare_metric_short_402)
        }
    }
}

