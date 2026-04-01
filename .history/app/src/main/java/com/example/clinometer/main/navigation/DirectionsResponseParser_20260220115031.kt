package com.example.clinometer.main.navigation

import com.example.clinometer.navigation.DirectionsResponse
import com.example.clinometer.navigation.DirectionsStep
import com.google.gson.Gson

object DirectionsResponseParser {
    fun extractSteps(json: String): List<DirectionsStep> {
        return runCatching {
            val response = Gson().fromJson(json, DirectionsResponse::class.java)
            response.routes.firstOrNull()?.legs?.flatMap { it.steps } ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
