package com.example.clinometer
import java.io.Serializable

data class Profile(
    var id: Long = System.currentTimeMillis(),
    var name: String,
    var vehicleType: VehicleType,
    var best0to100: Long = 0L,
    var best0to200: Long = 0L,
    var best100to200: Long = 0L,
    var maxSpeed: Float = 0f
) : Serializable {  // Това е важно!
    enum class VehicleType { CAR, MOTORCYCLE }
}