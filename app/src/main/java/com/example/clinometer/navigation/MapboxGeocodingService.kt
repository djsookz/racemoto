package com.example.clinometer.navigation

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

data class GeocodingFeature(
    @SerializedName("id") val id: String,
    @SerializedName("place_name") val placeName: String,
    @SerializedName("center") val center: List<Double>?,
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

// Category Search API data classes (different format!)
data class CategoryFeature(
    @SerializedName("type") val type: String,
    @SerializedName("geometry") val geometry: FeatureGeometry,
    @SerializedName("properties") val properties: CategoryProperties
)

data class FeatureGeometry(
    @SerializedName("coordinates") val coordinates: List<Double>,  // [longitude, latitude]
    @SerializedName("type") val type: String
)

data class CategoryCoordinates(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double
)

data class CategoryProperties(
    @SerializedName("name") val name: String,
    @SerializedName("mapbox_id") val mapboxId: String,
    @SerializedName("feature_type") val featureType: String,
    @SerializedName("full_address") val fullAddress: String?,
    @SerializedName("place_formatted") val placeFormatted: String?,
    @SerializedName("coordinates") val coordinates: CategoryCoordinates,
    @SerializedName("poi_category") val poiCategory: List<String>?
)

data class CategoryResponse(
    @SerializedName("type") val type: String,
    @SerializedName("features") val features: List<CategoryFeature>,
    @SerializedName("attribution") val attribution: String?
)

interface MapboxGeocodingService {
    @GET("geocoding/v5/mapbox.places/{query}.json")
    suspend fun searchPlaces(
        @retrofit2.http.Path("query") query: String,
        @Query("access_token") accessToken: String,
        @Query("proximity") proximity: String? = null,
        @Query("limit") limit: Int = 10,
        @Query("language") language: String = "bg",
        @Query("country") country: String? = null,
        @Query("autocomplete") autocomplete: Boolean = true,
        @Query("fuzzyMatch") fuzzyMatch: Boolean = true,
        @Query("types") types: String? = null
    ): Response<GeocodingResponse>
    
    @GET("geocoding/v5/mapbox.places/{lng},{lat}.json")
    suspend fun reverseGeocode(
        @retrofit2.http.Path("lng") longitude: Double,
        @retrofit2.http.Path("lat") latitude: Double,
        @Query("access_token") accessToken: String,
        @Query("limit") limit: Int = 1
    ): Response<GeocodingResponse>
    
    @GET("search/searchbox/v1/category/{category}")
    suspend fun searchCategory(
        @retrofit2.http.Path("category") category: String,
        @Query("proximity") proximity: String,
        @Query("access_token") accessToken: String,
        @Query("limit") limit: Int = 25,
        @Query("language") language: String = "en"
    ): Response<CategoryResponse>  // Changed from GeocodingResponse
}

