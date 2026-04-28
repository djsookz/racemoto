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
}

