package com.example.clinometer.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoService {
    @GET("v1/elevation")
    suspend fun getElevation(
        @Query("latitude") lat: Double,
        @Query("longitude") lng: Double
    ): Response<ElevationResponse>
}
