package com.example.clinometer

object RaceRepository {
    private val races = mutableListOf<Race>()
    private var nextId = 1L


    fun addRace(routePoints: List<RoutePoint>, duration: Long) {
        races.add(
            Race(
                id = nextId++,
                routePoints = routePoints.toList(),
                timestamp = System.currentTimeMillis(),
                duration = duration
            )
        )
    }
    fun deleteRace(race: Race) {
        races.removeAll { it.id == race.id } // Ако Race има уникален ID
    }

    fun getAllRaces(): List<Race> = races.toList()
}
