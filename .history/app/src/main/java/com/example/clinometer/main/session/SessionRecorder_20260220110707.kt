package com.example.clinometer.main.session

import android.content.Context
import com.example.clinometer.Profile
import com.example.clinometer.Race
import com.example.clinometer.RoutePoint
import com.example.clinometer.RouteStorage

object SessionRecorder {
    fun nextSessionNumber(context: Context, profileId: Long): Int {
        val allRaces = RouteStorage.loadRaces(context)
        val profileRaces = allRaces.filter { it.profileId == profileId }

        val sessionNumbers = profileRaces.mapNotNull { race ->
            race.name?.let { name ->
                if (name.startsWith("Session ")) {
                    name.substringAfter("Session ").toIntOrNull()
                } else {
                    null
                }
            }
        }

        return (sessionNumbers.maxOrNull() ?: 0) + 1
    }

    fun buildRace(
        profile: Profile,
        raceId: Long,
        routePoints: List<RoutePoint>,
        duration: Long,
        maxSpeed: Float,
        totalDistance: Double,
        maxLeftAngle: Float,
        maxRightAngle: Float,
        sessionNumber: Int,
        timestamp: Long = System.currentTimeMillis()
    ): Race {
        return Race(
            profileId = profile.id,
            id = raceId,
            routePoints = routePoints,
            timestamp = timestamp,
            duration = duration,
            absoluteTimestamp = timestamp,
            maxLeftAngle = maxLeftAngle,
            maxRightAngle = maxRightAngle,
            maxSpeed = maxSpeed,
            name = "Session $sessionNumber",
            distance = totalDistance,
            time0to100 = 0L,
            time0to200 = 0L,
            time100to200 = 0L
        )
    }
}
