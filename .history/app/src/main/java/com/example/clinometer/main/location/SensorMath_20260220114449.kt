package com.example.clinometer.main.location

object SensorMath {
    fun smoothBearing(currentBearing: Float, azimuth: Float): Float {
        var bearingDiff = azimuth - currentBearing
        while (bearingDiff > 180) bearingDiff -= 360
        while (bearingDiff < -180) bearingDiff += 360

        var smoothedBearing = currentBearing + bearingDiff * 0.2f
        while (smoothedBearing < 0) smoothedBearing += 360
        while (smoothedBearing > 360) smoothedBearing -= 360

        return smoothedBearing
    }

    fun lowPass(input: FloatArray, output: FloatArray?): FloatArray {
        if (output == null) return input

        val alpha = 0.8f
        for (i in input.indices) {
            output[i] = output[i] + alpha * (input[i] - output[i])
        }
        return output
    }
}
