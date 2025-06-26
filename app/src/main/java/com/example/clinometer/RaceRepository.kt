package com.example.clinometer

object RaceRepository {
    private val races = mutableListOf<Race>()

    fun addRace(race: Race) {
        races.add(race)
    }

}
