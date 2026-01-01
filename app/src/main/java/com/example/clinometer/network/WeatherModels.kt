package com.example.clinometer.network

data class ElevationResponse(
    val elevation: List<Double>
)

data class WeatherResponse(
    val main: MainData,
    val weather: List<WeatherCondition>?
)

data class MainData(
    val temp: Double
)

data class WeatherCondition(
    val id: Int,           // Weather condition code
    val main: String,      // Group of weather parameters (Rain, Snow, Clear etc.)
    val description: String, // Weather condition description
    val icon: String       // Weather icon id (01d, 02d, 03d, etc.)
)
