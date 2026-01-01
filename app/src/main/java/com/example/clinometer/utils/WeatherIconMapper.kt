package com.example.clinometer.utils

import com.example.clinometer.R

object WeatherIconMapper {
    
    /**
     * Maps WeatherAPI.com condition codes to drawable resources
     * Based on: https://www.weatherapi.com/docs/weather_conditions.json
     */
    fun getWeatherApiIcon(conditionCode: Int, cloudCover: Int, isDay: Boolean = true): Int {
        return when {
            // Sunny/Clear (1000)
            conditionCode == 1000 -> {
                if (cloudCover <= 25) {
                    if (isDay) R.drawable.ic_weather_sunny else R.drawable.ic_weather_clear_night
                } else if (cloudCover <= 75) {
                    if (isDay) R.drawable.ic_weather_partly_cloudy else R.drawable.ic_weather_partly_cloudy_night
                } else {
                    R.drawable.ic_weather_cloudy
                }
            }
            
            // Partly cloudy (1003)
            conditionCode == 1003 -> {
                if (cloudCover <= 50) {
                    if (isDay) R.drawable.ic_weather_partly_cloudy else R.drawable.ic_weather_partly_cloudy_night
                } else {
                    R.drawable.ic_weather_cloudy
                }
            }
            
            // Cloudy/Overcast (1006, 1009)
            conditionCode in 1006..1009 -> R.drawable.ic_weather_cloudy
            
            // Mist/Fog (1030, 1135, 1147)
            conditionCode in listOf(1030, 1135, 1147) -> R.drawable.ic_weather_cloudy
            
            // Rain (1063, 1150, 1153, 1180-1201, 1240-1246)
            conditionCode in listOf(1063, 1150, 1153) -> R.drawable.ic_weather_rainy
            conditionCode in 1180..1201 -> R.drawable.ic_weather_rainy
            conditionCode in 1240..1246 -> R.drawable.ic_weather_rainy
            
            // Snow (1066, 1114, 1210-1237, 1249-1264)
            conditionCode in listOf(1066, 1114) -> R.drawable.ic_weather_snowy
            conditionCode in 1210..1237 -> R.drawable.ic_weather_snowy
            conditionCode in 1249..1264 -> R.drawable.ic_weather_snowy
            
            // Thunderstorm (1087, 1273-1282)
            conditionCode == 1087 -> R.drawable.ic_weather_rainy
            conditionCode in 1273..1282 -> R.drawable.ic_weather_rainy
            
            // Default
            else -> R.drawable.ic_thermometer
        }
    }
    

    /**
     * Maps OpenWeatherMap condition codes to drawable resources
     * Based on: https://openweathermap.org/weather-conditions
     */
    fun getWeatherIcon(conditionCode: Int): Int {
        return when (conditionCode) {
            // Thunderstorm (200-232)
            in 200..232 -> R.drawable.ic_weather_rainy
            
            // Drizzle (300-321)
            in 300..321 -> R.drawable.ic_weather_rainy
            
            // Rain (500-531)
            in 500..531 -> R.drawable.ic_weather_rainy
            
            // Snow (600-622)
            in 600..622 -> R.drawable.ic_weather_snowy
            
            // Atmosphere (701-781) - mist, fog, haze, etc.
            in 701..781 -> R.drawable.ic_weather_cloudy
            
            // Clear (800)
            800 -> R.drawable.ic_weather_sunny
            
            // Few clouds (801) - 11-25% облачност
            801 -> R.drawable.ic_weather_partly_cloudy
            
            // Scattered clouds (802) - 25-50% облачност
            802 -> R.drawable.ic_weather_partly_cloudy
            
            // Broken clouds (803) - 51-84% облачност → все още има слънце!
            803 -> R.drawable.ic_weather_partly_cloudy
            
            // Overcast (804) - 85-100% облачност → само облак
            804 -> R.drawable.ic_weather_cloudy
            
            // Default fallback
            else -> R.drawable.ic_thermometer
        }
    }
    
    /**
     * Alternative method using icon code from API (e.g., "01d", "02n", etc.)
     */
    fun getWeatherIconFromCode(iconCode: String): Int {
        return when {
            iconCode.startsWith("01") -> R.drawable.ic_weather_sunny          // Clear sky
            iconCode.startsWith("02") -> R.drawable.ic_weather_partly_cloudy  // Few clouds
            iconCode.startsWith("03") -> R.drawable.ic_weather_partly_cloudy  // Scattered clouds (25-50%)
            iconCode.startsWith("04") -> R.drawable.ic_weather_partly_cloudy  // Broken clouds (51-84%, но има слънце!)
            iconCode.startsWith("09") -> R.drawable.ic_weather_rainy          // Shower rain
            iconCode.startsWith("10") -> R.drawable.ic_weather_rainy          // Rain
            iconCode.startsWith("11") -> R.drawable.ic_weather_rainy          // Thunderstorm
            iconCode.startsWith("13") -> R.drawable.ic_weather_snowy          // Snow
            iconCode.startsWith("50") -> R.drawable.ic_weather_cloudy         // Mist
            else -> R.drawable.ic_thermometer                                  // Default
        }
    }
}

