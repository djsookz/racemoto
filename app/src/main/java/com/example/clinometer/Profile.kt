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

    // Методи за актуализиране на рекордите
    fun updateBest0to100(time: Long) {
        if (time < best0to100 && time > 0) best0to100 = time
    }

    fun updateBest0to200(time: Long) {
        if (time < best0to200 && time > 0) best0to200 = time
    }

    fun updateBest100to200(time: Long) {
        if (time < best100to200 && time > 0) best100to200 = time
    }

    fun updateMaxSpeed(speed: Float) {
        if (speed > maxSpeed) maxSpeed = speed
    }
}