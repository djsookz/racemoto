package com.example.clinometer

object RaceRepository {
    private val races = mutableListOf<Race>()
    private var nextId = 1L


    fun addRace(
        routePoints: List<RoutePoint>,
        duration: Long,
        maxLeftAngle: Float,
        maxRightAngle: Float,
        maxSpeed: Float
    ) {
        val now = System.currentTimeMillis()
        races.add(
            Race(
                id = nextId++,
                routePoints = routePoints.toList(),
                timestamp = System.currentTimeMillis(),
                duration = duration,
                absoluteTimestamp = now,
                maxLeftAngle = maxLeftAngle,
                maxRightAngle = maxRightAngle,
                maxSpeed = maxSpeed

            )
        )
    }
    fun deleteRace(race: Race) {
        races.removeAll { it.id == race.id } // Ако Race има уникален ID
    }

    fun getAllRaces(): List<Race> = races.toList()
}
