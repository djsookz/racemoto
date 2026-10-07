package com.revix.app.main.session

import android.content.Context
import com.revix.app.Profile
import com.revix.app.Race
import com.revix.app.RoutePoint
import com.revix.app.racebox.RaceBoxDebugGate

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
            sessionNumber = sessionNumber,
            recordedWithRaceBox = RaceBoxDebugGate.shouldOverridePhoneGps(context)
        )
    }
}
