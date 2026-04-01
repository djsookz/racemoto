package com.example.clinometer

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** Lightweight 6DOF Madgwick AHRS (gyro + accel). */
class MadgwickAHRS(
    var beta: Float = 0.08f,
    var samplePeriodSec: Float = 0.01f
) {
    private var q0 = 1f
    private var q1 = 0f
    private var q2 = 0f
    private var q3 = 0f

    var isInitialized: Boolean = false
        private set

    fun reset() {
        q0 = 1f
        q1 = 0f
        q2 = 0f
        q3 = 0f
        isInitialized = false
    }

    /**
     * Coarse alignment: seed quaternion from accelerometer so that pitch/roll
     * are correct from frame 1. Yaw is set to zero (not observable from accel).
     */
    fun seedFromAccelerometer(ax: Float, ay: Float, az: Float) {
        val norm = sqrt(ax * ax + ay * ay + az * az)
        if (norm < 1e-6f) return
        val nx = ax / norm
        val ny = ay / norm
        val nz = az / norm

        // Pitch = rotation around X axis, Roll = rotation around Y axis
        val pitch = atan2(-nx, nz)
        val roll = atan2(ny, nz)

        // Build quaternion from Euler angles (yaw = 0)
        val halfP = pitch * 0.5f
        val halfR = roll * 0.5f
        val cp = kotlin.math.cos(halfP)
        val sp = kotlin.math.sin(halfP)
        val cr = kotlin.math.cos(halfR)
        val sr = kotlin.math.sin(halfR)

        q0 = cp * cr
        q1 = cp * sr
        q2 = sp * cr
        q3 = -sp * sr

        // Normalize
        val qNorm = sqrt(q0 * q0 + q1 * q1 + q2 * q2 + q3 * q3)
        if (qNorm > 1e-9f) {
            val r = 1f / qNorm
            q0 *= r; q1 *= r; q2 *= r; q3 *= r
        }
        isInitialized = true
    }

    fun update(gx: Float, gy: Float, gz: Float, ax: Float, ay: Float, az: Float) {
        var qDot1 = 0.5f * (-q1 * gx - q2 * gy - q3 * gz)
        var qDot2 = 0.5f * (q0 * gx + q2 * gz - q3 * gy)
        var qDot3 = 0.5f * (q0 * gy - q1 * gz + q3 * gx)
        var qDot4 = 0.5f * (q0 * gz + q1 * gy - q2 * gx)

        val aNorm = sqrt(ax * ax + ay * ay + az * az)
        if (aNorm > 1e-6f) {
            val recipA = 1f / aNorm
            val axn = ax * recipA
            val ayn = ay * recipA
            val azn = az * recipA

            val twoQ0 = 2f * q0
            val twoQ1 = 2f * q1
            val twoQ2 = 2f * q2
            val twoQ3 = 2f * q3
            val fourQ0 = 4f * q0
            val fourQ1 = 4f * q1
            val fourQ2 = 4f * q2
            val eightQ1 = 8f * q1
            val eightQ2 = 8f * q2
            val q0q0 = q0 * q0
            val q1q1 = q1 * q1
            val q2q2 = q2 * q2
            val q3q3 = q3 * q3

            var s0 = fourQ0 * q2q2 + twoQ2 * axn + fourQ0 * q1q1 - twoQ1 * ayn
            var s1 =
                fourQ1 * q3q3 - twoQ3 * axn + 4f * q0q0 * q1 - twoQ0 * ayn - fourQ1 +
                    eightQ1 * q1q1 + eightQ1 * q2q2 + fourQ1 * azn
            var s2 =
                4f * q0q0 * q2 + twoQ0 * axn + fourQ2 * q3q3 - twoQ3 * ayn - fourQ2 +
                    eightQ2 * q1q1 + eightQ2 * q2q2 + fourQ2 * azn
            var s3 = 4f * q1q1 * q3 - twoQ1 * axn + 4f * q2q2 * q3 - twoQ2 * ayn

            val sNorm = sqrt(s0 * s0 + s1 * s1 + s2 * s2 + s3 * s3)
            if (sNorm > 1e-9f) {
                val recipS = 1f / sNorm
                s0 *= recipS
                s1 *= recipS
                s2 *= recipS
                s3 *= recipS

                qDot1 -= beta * s0
                qDot2 -= beta * s1
                qDot3 -= beta * s2
                qDot4 -= beta * s3
            }

            isInitialized = true
        }

        q0 += qDot1 * samplePeriodSec
        q1 += qDot2 * samplePeriodSec
        q2 += qDot3 * samplePeriodSec
        q3 += qDot4 * samplePeriodSec

        val qNorm = sqrt(q0 * q0 + q1 * q1 + q2 * q2 + q3 * q3)
        if (qNorm > 1e-9f) {
            val recipQ = 1f / qNorm
            q0 *= recipQ
            q1 *= recipQ
            q2 *= recipQ
            q3 *= recipQ
        }
    }

    fun getGravityVector(): FloatArray {
        val gx = 2f * (q1 * q3 - q0 * q2)
        val gy = 2f * (q0 * q1 + q2 * q3)
        val gz = q0 * q0 - q1 * q1 - q2 * q2 + q3 * q3
        return floatArrayOf(gx * 9.81f, gy * 9.81f, gz * 9.81f)
    }
}
