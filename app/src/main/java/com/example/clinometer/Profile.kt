package com.example.clinometer
import java.io.Serializable

data class Profile(
    var id: Long = System.currentTimeMillis(),
    var name: String,
    var vehicleType: VehicleType,
    var best0to100: Long = Long.MAX_VALUE, // Инициализираме с много голяма стойност
    var best0to200: Long = Long.MAX_VALUE,
    var best100to200: Long = Long.MAX_VALUE,
    var maxSpeed: Float = 0f,
    var imagePath: String? = null // Път към снимката на профила
) : Serializable {
    enum class VehicleType { CAR, MOTORCYCLE }
}