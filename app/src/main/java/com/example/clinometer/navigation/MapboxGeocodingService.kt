package com.example.clinometer.navigation

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

data class GeocodingFeature(
    @SerializedName("id") val id: String,
    @SerializedName("place_name") val placeName: String,
    @SerializedName("center") val center: List<Double>,
    @SerializedName("text") val text: String,
    @SerializedName("properties") val properties: GeocodingProperties?
)

data class GeocodingProperties(
    @SerializedName("address") val address: String?,
    @SerializedName("category") val category: String?
)

data class GeocodingResponse(
    @SerializedName("type") val type: String,
    @SerializedName("features") val features: List<GeocodingFeature>
)

interface MapboxGeocodingService {
    @GET("geocoding/v5/mapbox.places/{query}.json")
    suspend fun searchPlaces(
        @retrofit2.http.Path("query") query: String,
        @Query("access_token") accessToken: String,
        @Query("proximity") proximity: String? = null,
        @Query("limit") limit: Int = 10
    ): Response<GeocodingResponse>
    
    @GET("geocoding/v5/mapbox.places/{lng},{lat}.json")
    suspend fun reverseGeocode(
        @retrofit2.http.Path("lng") longitude: Double,
        @retrofit2.http.Path("lat") latitude: Double,
        @Query("access_token") accessToken: String,
        @Query("limit") limit: Int = 1
    ): Response<GeocodingResponse>
}

