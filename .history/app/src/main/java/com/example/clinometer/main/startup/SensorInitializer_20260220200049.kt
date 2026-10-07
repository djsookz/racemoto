package com.example.clinometer.main.startup

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager

object SensorInitializer {

    fun initialize(context: Context): Pair<SensorManager, SensorSetup> {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val accelerometer = if (rotationSensor == null) {
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        } else {
            null
        }

        val magnetometer = if (rotationSensor == null) {
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        } else {
            null
        }

        return sensorManager to SensorSetup(
            rotationSensor = rotationSensor,
            accelerometer = accelerometer,
            magnetometer = magnetometer
        )
    }
}
