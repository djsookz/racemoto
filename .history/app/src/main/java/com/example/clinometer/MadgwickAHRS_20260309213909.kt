package com.example.clinometer

import kotlin.math.sqrt

/**
 * Madgwick AHRS filter.
 *
 * Слива акселерометър + гироскоп за стабилна оценка на ориентацията (quaternion),
 * от която се извлича точен gravity вектор дори при висок G (спиране, ускорение, завой).
 *
 * Предимство пред прост low-pass на акселерометъра: гироскопът поема бързите
 * промени на ориентацията и не позволява динамичното ускорение да замърси gravity.
 *
 * Литература: S. O. H. Madgwick et al., IEEE ICORR 2011.
 *
 * Употреба:
 *   val mw = MadgwickAHRS(beta = 0.08f, samplePeriodSec = 0.01f)
 *   // При всеки sensor event (~100 Hz):
 *   mw.update(gx, gy, gz, ax, ay, az)   // rad/s, m/s²
 *   val g = mw.getGravityVector()        // заменя low-pass gravity масива
 */
class MadgwickAHRS(
    /** Gain на филтъра. По-висок = по-бързо сближаване, повече шум. Типично: 0.033–0.1 */
    var beta: Float = 0.08f,
    /** Номинален период между updates в секунди (1/Hz). */
    var samplePeriodSec: Float = 0.01f
) {
    // Кватернион [q0, q1, q2, q3] — identity = устройство наравнено с Earth frame
    var q0 = 1f; var q1 = 0f; var q2 = 0f; var q3 = 0f

    var isInitialized = false
        private set

    fun reset() {
        q0 = 1f; q1 = 0f; q2 = 0f; q3 = 0f
        isInitialized = false
    }

    /**
     * Обновява оценката на ориентацията.
     * @param gx, gy, gz  Гироскоп в rad/s (phone frame)
     * @param ax, ay, az  Акселерометър в m/s² (phone frame) — TYPE_ACCELEROMETER (RAW, с gravity)
     */
    fun update(
        gx: Float, gy: Float, gz: Float,
        ax: Float, ay: Float, az: Float
    ) {
        // Нормализираме акселерометъра
        val aNorm = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
        if (aNorm < 0.1f) {
            // Почти нулево ускорение — само интегрираме гироскопа
            gyroIntegrate(gx, gy, gz)
            return
        }
        val r = 1f / aNorm
        val axN = ax * r; val ayN = ay * r; val azN = az * r

        // Оценка на посоката на gravity в phone frame от текущия кватернион
        // (третата колона на rotation matrix от Earth към device frame)
        val vx = 2f * (q1 * q3 - q0 * q2)
        val vy = 2f * (q0 * q1 + q2 * q3)
        val vz = q0 * q0 - q1 * q1 - q2 * q2 + q3 * q3

        // Грешка = cross(измерено, очаквано) — gradient descent корекция
        val ex = ayN * vz - azN * vy
        val ey = azN * vx - axN * vz
        val ez = axN * vy - ayN * vx

        // Коригирани ъглови скорости (gyro + обратна връзка)
        val twoB = 2f * beta
        val gxC = gx + twoB * ex
        val gyC = gy + twoB * ey
        val gzC = gz + twoB * ez

        // Интегриране на кватерниона
        val halfT = 0.5f * samplePeriodSec
        val dq0 = (-q1 * gxC - q2 * gyC - q3 * gzC) * halfT
        val dq1 = ( q0 * gxC + q2 * gzC - q3 * gyC) * halfT
        val dq2 = ( q0 * gyC - q1 * gzC + q3 * gxC) * halfT
        val dq3 = ( q0 * gzC + q1 * gyC - q2 * gxC) * halfT

        q0 += dq0; q1 += dq1; q2 += dq2; q3 += dq3

        // Нормализиране на кватерниона
        val qNorm = sqrt((q0 * q0 + q1 * q1 + q2 * q2 + q3 * q3).toDouble()).toFloat()
        val rQ = 1f / qNorm
        q0 *= rQ; q1 *= rQ; q2 *= rQ; q3 *= rQ

        isInitialized = true
    }

    private fun gyroIntegrate(gx: Float, gy: Float, gz: Float) {
        val halfT = 0.5f * samplePeriodSec
        val dq0 = (-q1 * gx - q2 * gy - q3 * gz) * halfT
        val dq1 = ( q0 * gx + q2 * gz - q3 * gy) * halfT
        val dq2 = ( q0 * gy - q1 * gz + q3 * gx) * halfT
        val dq3 = ( q0 * gz + q1 * gy - q2 * gx) * halfT
        q0 += dq0; q1 += dq1; q2 += dq2; q3 += dq3
        val n = sqrt((q0*q0 + q1*q1 + q2*q2 + q3*q3).toDouble()).toFloat()
        val ir = 1f / n; q0 *= ir; q1 *= ir; q2 *= ir; q3 *= ir
        isInitialized = true
    }

    /**
     * Връща gravity вектора в phone frame [m/s²].
     *
     * Съответства на това, което TYPE_ACCELEROMETER дава в покой:
     * сочи "нагоре" (реакционна сила), magnitude ≈ 9.81.
     *
     * Заменя low-pass filtered `gravity` масива в TrackSessionActivity.
     */
    fun getGravityVector(): FloatArray {
        // Earth "up" [0,0,1] → device frame чрез rotation от кватерниона
        val g = GRAVITY_EARTH
        return floatArrayOf(
            2f * (q1 * q3 - q0 * q2) * g,
            2f * (q0 * q1 + q2 * q3) * g,
            (q0 * q0 - q1 * q1 - q2 * q2 + q3 * q3) * g
        )
    }

    companion object {
        const val GRAVITY_EARTH = 9.81f
    }
}
