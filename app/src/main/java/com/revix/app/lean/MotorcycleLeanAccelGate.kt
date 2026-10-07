package com.revix.app.lean

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Gyro-led motorcycle lean. Body-frame accelerometer reads ~0° both when upright
 * and in a coordinated turn, so it may only correct gyro drift on a straight
 * (or at walking speed).
 */
class MotorcycleLeanAccelGate {
    var frozen: Boolean = false
        private set

    fun correctionGain(
        speedMps: Float,
        yawRateDegPerSec: Float,
        kinematicLateralG: Float,
        gyroLeanDeg: Float,
        accelLeanDeg: Float,
        straightGain: Float
    ): Float {
        return if (allowAccelCorrection(
                speedMps,
                yawRateDegPerSec,
                kinematicLateralG,
                gyroLeanDeg,
                accelLeanDeg
            )
        ) {
            straightGain
        } else {
            0f
        }
    }

    fun allowAccelCorrection(
        speedMps: Float,
        yawRateDegPerSec: Float,
        kinematicLateralG: Float,
        gyroLeanDeg: Float,
        accelLeanDeg: Float
    ): Boolean {
        val absYaw = abs(yawRateDegPerSec)
        val absLatG = abs(kinematicLateralG)
        val coordinatedTurnLie = speedMps >= COORDINATED_MIN_SPEED_MPS &&
            abs(gyroLeanDeg) >= COORDINATED_GYRO_LEAN_DEG &&
            abs(accelLeanDeg) <= COORDINATED_ACCEL_LEAN_DEG
        val inCorner = coordinatedTurnLie ||
            (speedMps >= CORNER_MIN_SPEED_MPS &&
                (absYaw >= CORNER_ENTER_YAW_DEG_S || absLatG >= CORNER_ENTER_LAT_G))
        val onStraight = !coordinatedTurnLie &&
            (speedMps < STRAIGHT_MAX_SPEED_MPS ||
                (absYaw <= CORNER_EXIT_YAW_DEG_S && absLatG <= CORNER_EXIT_LAT_G))
        when {
            inCorner -> frozen = true
            onStraight -> frozen = false
        }
        return !frozen
    }

    fun reset() {
        frozen = false
    }

    companion object {
        const val CORNER_MIN_SPEED_MPS = 3.0f
        const val STRAIGHT_MAX_SPEED_MPS = 2.5f
        const val CORNER_ENTER_YAW_DEG_S = 8f
        const val CORNER_EXIT_YAW_DEG_S = 4f
        const val CORNER_ENTER_LAT_G = 0.18f
        const val CORNER_EXIT_LAT_G = 0.10f
        const val COORDINATED_MIN_SPEED_MPS = 4.0f
        const val COORDINATED_GYRO_LEAN_DEG = 15f
        const val COORDINATED_ACCEL_LEAN_DEG = 8f
        const val STRAIGHT_ACCEL_GAIN = 0.05f
        private const val GRAVITY_MPS2 = 9.80665f
        private const val RAD_TO_DEG = 57.29578f

        fun kinematicLateralG(speedMps: Float, yawRateDegPerSec: Float): Float {
            return (speedMps * Math.toRadians(yawRateDegPerSec.toDouble()) / GRAVITY_MPS2).toFloat()
        }

        fun yawRateDegPerSec(
            gxRad: Float,
            gyRad: Float,
            gzRad: Float,
            downX: Float,
            downY: Float,
            downZ: Float
        ): Float {
            val mag = sqrt(downX * downX + downY * downY + downZ * downZ)
            if (mag < 0.5f) return gzRad * RAD_TO_DEG
            return ((gxRad * downX + gyRad * downY + gzRad * downZ) / mag) * RAD_TO_DEG
        }
    }
}
