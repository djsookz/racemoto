package com.example.clinometer.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    @GET("forecast.json")
    suspend fun getCurrentWeather(
        @Query("key") apiKey: String,
        @Query("q") location: String, // "lat,lon" format
        @Query("days") days: Int = 1,  // 1 day forecast (enough for hourly)
        @Query("lang") lang: String = "bg"
    ): Response<WeatherApiResponse>
}

// Response models for WeatherAPI.com
data class WeatherApiResponse(
    val location: WeatherApiLocation,
    val current: WeatherApiCurrent,
    val forecast: WeatherApiForecast?
)

data class WeatherApiLocation(
    val name: String,
    val region: String,
    val country: String,
    val lat: Double,
    val lon: Double
)

data class WeatherApiCurrent(
    val temp_c: Double,
    val temp_f: Double,
    val condition: WeatherApiCondition,
    val wind_kph: Double,
    val wind_dir: String,   // Wind direction (N, SE, etc.)
    val pressure_mb: Double,
    val humidity: Int,
    val cloud: Int, // Cloud cover in %
    val feelslike_c: Double,
    val uv: Double,
    val is_day: Int  // 1 = day, 0 = night
)

data class WeatherApiCondition(
    val text: String,       // "Partly cloudy"
    val icon: String,       // "//cdn.weatherapi.com/weather/64x64/day/116.png"
    val code: Int           // 1003
)

data class WeatherApiForecast(
    val forecastday: List<WeatherApiForecastDay>
)

data class WeatherApiForecastDay(
    val date: String,
    val hour: List<WeatherApiHour>
)

data class WeatherApiHour(
    val time: String,
    val temp_c: Double,
    val condition: WeatherApiCondition,
    val chance_of_rain: Int,  // 0-100%
    val will_it_rain: Int     // 0 or 1
)

