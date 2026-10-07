package com.revix.app.main.startup

import android.hardware.Sensor

data class SensorSetup(
    val rotationSensor: Sensor?,
    val accelerometer: Sensor?,
    val magnetometer: Sensor?
)
