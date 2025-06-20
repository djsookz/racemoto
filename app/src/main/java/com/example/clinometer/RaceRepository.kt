package com.example.clinometer

object RaceRepository {
    private val races = mutableListOf<Race>()
    private var nextId = 1L

    fun addRace(race: Race) {
        races.add(race)
    }

    fun deleteRace(race: Race) {
        races.removeAll { it.id == race.id }
    }

    fun getAllRaces(): List<Race> = races.toList()
}
