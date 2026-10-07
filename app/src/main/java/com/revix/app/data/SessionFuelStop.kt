package com.revix.app.data

data class SessionFuelStop(
    val id: Long = System.currentTimeMillis(),
    val fuelEntryId: Long,
    val profileId: Long,
    val station: String,
    val litres: Double,
    val pricePerLitre: Double = 0.0,
    val totalAmount: Double,
    val odometerKm: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)
