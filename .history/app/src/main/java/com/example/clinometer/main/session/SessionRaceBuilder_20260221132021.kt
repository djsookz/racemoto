package com.example.clinometer.main.session

import android.content.Context
import com.example.clinometer.Profile
import com.example.clinometer.Race
import com.example.clinometer.RoutePoint

object SessionRaceBuilder {

    fun build(
        context: Context,
        profile: Profile,
        routePoints: List<RoutePoint>,
        serviceDuration: Long,
        maxSpeed: Float,
        totalDistanceKm: Double,
        maxLeftAngle: Float,
        maxRightAngle: Float
    ): Race {
        val sessionNumber = SessionRecorder.nextSessionNumber(context, profile.id)
        val raceId = System.currentTimeMillis()

        return SessionRecorder.buildRace(
            profile = profile,
            raceId = raceId,
            routePoints = routePoints,
            duration = serviceDuration,
            maxSpeed = maxSpeed,
            totalDistance = totalDistanceKm,
            maxLeftAngle = maxLeftAngle,
            maxRightAngle = maxRightAngle,
            sessionNumber = sessionNumber
        )
    }
}
