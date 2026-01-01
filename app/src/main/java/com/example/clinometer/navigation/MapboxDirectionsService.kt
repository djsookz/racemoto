package com.example.clinometer.navigation

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class DirectionsRoute(
    @SerializedName("geometry") val geometry: DirectionsGeometry,
    @SerializedName("duration") val duration: Double,
    @SerializedName("distance") val distance: Double,
    @SerializedName("legs") val legs: List<DirectionsLeg>
)

data class DirectionsGeometry(
    @SerializedName("coordinates") val coordinates: List<List<Double>>
)

data class DirectionsLeg(
    @SerializedName("duration") val duration: Double,
    @SerializedName("distance") val distance: Double,
    @SerializedName("steps") val steps: List<DirectionsStep>
)

data class DirectionsStep(
    @SerializedName("geometry") val geometry: DirectionsGeometry,
    @SerializedName("duration") val duration: Double,
    @SerializedName("distance") val distance: Double,
    @SerializedName("maneuver") val maneuver: StepManeuver?,
    @SerializedName("name") val name: String?,
    @SerializedName("mode") val mode: String?,
    @SerializedName("driving_side") val drivingSide: String?,
    @SerializedName("bannerInstructions") val bannerInstructions: List<BannerInstruction>?
)

data class StepManeuver(
    @SerializedName("type") val type: String?, // turn, depart, arrive, merge, etc.
    @SerializedName("modifier") val modifier: String?, // left, right, straight, slight left, etc.
    @SerializedName("instruction") val instruction: String?,
    @SerializedName("bearing_before") val bearingBefore: Double?,
    @SerializedName("bearing_after") val bearingAfter: Double?,
    @SerializedName("location") val location: List<Double>? // [lon, lat]
)

data class BannerInstruction(
    @SerializedName("distanceAlongGeometry") val distanceAlongGeometry: Double?,
    @SerializedName("primary") val primary: BannerComponent?,
    @SerializedName("secondary") val secondary: BannerComponent?
)

data class BannerComponent(
    @SerializedName("text") val text: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("modifier") val modifier: String?
)

data class DirectionsResponse(
    @SerializedName("routes") val routes: List<DirectionsRoute>,
    @SerializedName("code") val code: String
)

interface MapboxDirectionsService {
    @GET("directions/v5/mapbox/driving/{coordinates}")
    suspend fun getRoute(
        @Path("coordinates") coordinates: String, // Format: "lon1,lat1;lon2,lat2"
        @Query("access_token") accessToken: String,
        @Query("geometries") geometries: String = "geojson",
        @Query("overview") overview: String = "full",
        @Query("steps") steps: Boolean = true, // Get turn-by-turn steps
        @Query("banner_instructions") bannerInstructions: Boolean = true, // Get banner instructions for maneuvers
        @Query("voice_instructions") voiceInstructions: Boolean = false,
        @Query("alternatives") alternatives: Boolean = true, // Get alternative routes
        @Query("exclude") exclude: String? = null // Exclude road types: motorway, toll, ferry
    ): Response<DirectionsResponse>
}

