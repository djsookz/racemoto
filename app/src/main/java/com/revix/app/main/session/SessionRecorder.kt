package com.revix.app.main.session

import android.content.Context
import com.revix.app.Profile
import com.revix.app.Race
import com.revix.app.RoutePoint
import com.revix.app.RouteStorage

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
        @Suppress("UNUSED_PARAMETER") routePoints: List<RoutePoint>,
        duration: Long,
        maxSpeed: Float,
        totalDistance: Double,
        maxLeftAngle: Float,
        maxRightAngle: Float,
        sessionNumber: Int,
        timestamp: Long = System.currentTimeMillis(),
        recordedWithRaceBox: Boolean = false
    ): Race {
        return Race(
            profileId = profile.id,
            id = raceId,
            // Geometry lives in points_<id>.json — keep races.json metadata-only.
            routePoints = emptyList(),
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
            time100to200 = 0L,
            recordedWithRaceBox = recordedWithRaceBox
        )
    }
}
