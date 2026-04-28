package com.example.clinometer

import android.location.Location
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

class SmartCalibrationEngine(
    private val hasGyroSensor: Boolean,
    private val useLeanStep: Boolean,
    private val requireGpsForwardAssist: Boolean = false
) {
    enum class Phase {
        IDLE,
        STILL,
        LEAN_LEFT,
        RETURN_UPRIGHT,
        FORWARD,
        COMPLETE
    }

    enum class FailureReason {
        NOT_ENOUGH_STILL,
        NOT_ENOUGH_FORWARD,
        NOT_ENOUGH_GPS_FORWARD,
        INVALID_GRAVITY,
        INVALID_FORWARD_VECTOR,
        LEAN_LEFT_TOO_SMALL,
        UPRIGHT_TIMEOUT
    }

    sealed class Guidance {
        data class StillWarmup(val remainingSec: Float) : Guidance()
        data class Still(val remainingSec: Float) : Guidance()
        data class LeanLeft(
            val targetDeg: Int,
            val currentDeg: Int,
            val remainingDeg: Int,
            val hold: Int,
            val holdRequired: Int
        ) : Guidance()

        data class ReturnUpright(val hold: Int, val holdRequired: Int) : Guidance()
        data class ForwardWait(val remainingSec: Float) : Guidance()
        data class ForwardDrive(
            val accepted: Int,
            val target: Int,
            val gpsRequired: Boolean = false,
            val gpsDistanceMeters: Float = 0f,
            val gpsTargetDistanceMeters: Float = 0f,
            val gpsPoints: Int = 0,
            val gpsTargetPoints: Int = 0
        ) : Guidance()
    }

    data class CalibrationResult(
        val gravityAvg: FloatArray,
        val gravityNorm: FloatArray,
        val forwardNorm: FloatArray,
        val rightNorm: FloatArray,
        val stillMaxAxis: FloatArray,
        val gyroBias: FloatArray,
        val hasGyroBias: Boolean,
        val quality: Float,
        val stillLinearAvg: Float,
        val stillVibrationMag: Float,
        val forwardNoiseFloor: Float,
        val forwardExcessTrigger: Float,
        val stillLinearCount: Int,
        val forwardAcceptedSamples: Int,
        val leanOffsetPortraitComponent: Float,
        val leanOffsetLandscapeComponent: Float
    )

    data class Frame(
        val phase: Phase,
        val progressPercent: Int,
        val guidance: Guidance?,
        val failure: FailureReason? = null,
        val result: CalibrationResult? = null
    )

    private var phase: Phase = Phase.IDLE
    private var phaseStartMs: Long = 0L
    private var started = false

    private val gravityLp = FloatArray(3)
    private var gravityLpInitialized = false
    private val gravitySensorValues = FloatArray(3)
    private var gravitySensorTimestampNs: Long = 0L
    private val linearSensorValues = FloatArray(3)
    private var linearSensorTimestampNs: Long = 0L
    private val motionFastLp = FloatArray(3)
    private var motionFastLpInitialized = false
    private val lastRawAccel = FloatArray(3)
    private var lastRawAccelTimestampNs: Long = 0L

    private val stillGravitySum = FloatArray(3)
    private val stillMaxAxis = FloatArray(3)
    private var stillLinearMagSum = 0f
    private var stillLinearGoodCount = 0
    private var stillLinearCount = 0
    private var stillSamplingStarted = false

    private val stillGyroBiasSum = FloatArray(3)
    private var stillGyroMagSum = 0f
    private var stillGyroGoodCount = 0
    private var stillGyroCount = 0

    private val leanReferenceGravity = FloatArray(3)
    private val leanGravitySum = FloatArray(3)
    private var leanSampleCount = 0
    private var leanTargetStableSamples = 0
    private var uprightStableSamples = 0

    private val forwardSettleBaselineSum = FloatArray(3)
    private var forwardSettleCount = 0
    private var forwardSettleBaselineLocked = false
    private val forwardLateralAxis = FloatArray(3)
    private var forwardLateralAxisReady = false

    private data class WeightedVector(val magnitude: Float, val vector: FloatArray)
    private val forwardTopVectors = mutableListOf<WeightedVector>()
    private var forwardAcceptedSamples = 0
    private var forwardSampleCount = 0
    private var forwardTrigger = 0.6f
    private val forwardBaseline = FloatArray(3)
    private var forwardNoiseFloor = 0f
    private var forwardExcessTrigger = 0f
    private var forwardDirectionUnit: FloatArray? = null
    private var forwardDirectionStreak = 0

    private var gpsAnchorLocation: Location? = null
    private var gpsLastAcceptedLocation: Location? = null
    private var gpsAcceptedPoints = 0
    private var gpsAcceptedSegments = 0
    private var gpsTotalDistanceMeters = 0f
    private var gpsDirectionSumEast = 0.0
    private var gpsDirectionSumNorth = 0.0
    private var gpsForwardReady = false

    fun start(nowMs: Long): Frame {
        resetRuntimeState()
        started = true
        phase = Phase.STILL
        phaseStartMs = nowMs
        val guidance = Guidance.StillWarmup(STILL_WARMUP_MS / 1000f)
        return Frame(
            phase = phase,
            progressPercent = 0,
            guidance = guidance
        )
    }

    fun onGravity(values: FloatArray, timestampNs: Long) {
        gravitySensorValues[0] = values[0]
        gravitySensorValues[1] = values[1]
        gravitySensorValues[2] = values[2]
        gravitySensorTimestampNs = timestampNs
    }

    fun onLinear(values: FloatArray, timestampNs: Long) {
        linearSensorValues[0] = values[0]
        linearSensorValues[1] = values[1]
        linearSensorValues[2] = values[2]
        linearSensorTimestampNs = timestampNs
    }

    fun onGyroscope(values: FloatArray) {
        if (!started || phase != Phase.STILL || !stillSamplingStarted || !hasGyroSensor) return

        val gx = values[0]
        val gy = values[1]
        val gz = values[2]

        stillGyroBiasSum[0] += gx
        stillGyroBiasSum[1] += gy
        stillGyroBiasSum[2] += gz

        val gMag = norm3(gx, gy, gz)
        stillGyroMagSum += gMag
        stillGyroCount++

        if (gMag <= STILL_GYRO_GOOD_THRESHOLD) {
            stillGyroGoodCount++
        }
    }

    fun onLocation(location: Location) {
        if (!started || !requireGpsForwardAssist || phase != Phase.FORWARD || !forwardSettleBaselineLocked) return
        if (location.hasAccuracy() && location.accuracy > GPS_MAX_HORIZONTAL_ACCURACY_M) return

        val currentLocation = Location(location)
        val anchor = gpsAnchorLocation
        if (anchor == null) {
            resetGpsForwardSampling(currentLocation)
            return
        }

        val lastAccepted = gpsLastAcceptedLocation ?: run {
            gpsLastAcceptedLocation = currentLocation
            return
        }

        val deltaEastNorth = computeEastNorthDeltaMeters(lastAccepted, currentLocation)
        val deltaEast = deltaEastNorth[0]
        val deltaNorth = deltaEastNorth[1]
        val segmentDistance = sqrt(deltaEast * deltaEast + deltaNorth * deltaNorth).toFloat()
        if (segmentDistance < GPS_MIN_SEGMENT_DISTANCE_M) {
            return
        }

        val dtSec = ((currentLocation.elapsedRealtimeNanos - lastAccepted.elapsedRealtimeNanos) / 1_000_000_000.0)
            .coerceAtLeast(0.05)
        val derivedSpeed = (segmentDistance / dtSec).toFloat()
        val speedMps = when {
            currentLocation.hasSpeed() && currentLocation.speed > 0f -> currentLocation.speed
            else -> derivedSpeed
        }
        if (speedMps < GPS_MIN_SPEED_MPS) {
            resetGpsForwardSampling(currentLocation)
            return
        }

        val unitEast = deltaEast / segmentDistance
        val unitNorth = deltaNorth / segmentDistance
        if (gpsAcceptedSegments > 0) {
            val directionMag = sqrt(gpsDirectionSumEast * gpsDirectionSumEast + gpsDirectionSumNorth * gpsDirectionSumNorth)
            if (directionMag > 0.0001) {
                val avgEast = gpsDirectionSumEast / directionMag
                val avgNorth = gpsDirectionSumNorth / directionMag
                val dot = avgEast * unitEast + avgNorth * unitNorth
                if (dot < GPS_DIRECTION_DOT_MIN) {
                    resetGpsForwardSampling(currentLocation)
                    return
                }
            }
        }

        gpsAcceptedSegments++
        gpsAcceptedPoints = gpsAcceptedSegments + 1
        gpsTotalDistanceMeters += segmentDistance
        gpsDirectionSumEast += unitEast * segmentDistance
        gpsDirectionSumNorth += unitNorth * segmentDistance
        gpsLastAcceptedLocation = currentLocation

        val anchorDelta = computeEastNorthDeltaMeters(anchor, currentLocation)
        val anchorDistance = sqrt(anchorDelta[0] * anchorDelta[0] + anchorDelta[1] * anchorDelta[1]).toFloat()
        if (gpsAcceptedPoints >= GPS_MIN_POINTS && anchorDistance >= GPS_MIN_TOTAL_DISTANCE_M) {
            gpsForwardReady = true
            gpsTotalDistanceMeters = anchorDistance
        }
    }

    fun onAccelerometer(values: FloatArray, timestampNs: Long, nowMs: Long): Frame {
        if (!started) {
            return Frame(phase = Phase.IDLE, progressPercent = 0, guidance = null)
        }

        val ax = values[0]
        val ay = values[1]
        val az = values[2]
        val noGyroDevice = !hasGyroSensor

        val gravityFresh = gravitySensorTimestampNs > 0L &&
            (timestampNs - gravitySensorTimestampNs) <= NO_GYRO_GRAVITY_FRESH_NS
        val gravityTargetX = if (noGyroDevice && gravityFresh) {
            NO_GYRO_GRAVITY_SENSOR_BLEND * gravitySensorValues[0] + (1f - NO_GYRO_GRAVITY_SENSOR_BLEND) * ax
        } else {
            ax
        }
        val gravityTargetY = if (noGyroDevice && gravityFresh) {
            NO_GYRO_GRAVITY_SENSOR_BLEND * gravitySensorValues[1] + (1f - NO_GYRO_GRAVITY_SENSOR_BLEND) * ay
        } else {
            ay
        }
        val gravityTargetZ = if (noGyroDevice && gravityFresh) {
            NO_GYRO_GRAVITY_SENSOR_BLEND * gravitySensorValues[2] + (1f - NO_GYRO_GRAVITY_SENSOR_BLEND) * az
        } else {
            az
        }

        if (!gravityLpInitialized) {
            gravityLp[0] = gravityTargetX
            gravityLp[1] = gravityTargetY
            gravityLp[2] = gravityTargetZ
            gravityLpInitialized = true
        }
        if (!motionFastLpInitialized) {
            motionFastLp[0] = gravityTargetX
            motionFastLp[1] = gravityTargetY
            motionFastLp[2] = gravityTargetZ
            motionFastLpInitialized = true
        }

        val alpha = if (phase == Phase.FORWARD) 0.985f else 0.92f
        gravityLp[0] = alpha * gravityLp[0] + (1f - alpha) * gravityTargetX
        gravityLp[1] = alpha * gravityLp[1] + (1f - alpha) * gravityTargetY
        gravityLp[2] = alpha * gravityLp[2] + (1f - alpha) * gravityTargetZ

        val fastAlpha = if (phase == Phase.FORWARD) 0.78f else 0.60f
        motionFastLp[0] = fastAlpha * motionFastLp[0] + (1f - fastAlpha) * ax
        motionFastLp[1] = fastAlpha * motionFastLp[1] + (1f - fastAlpha) * ay
        motionFastLp[2] = fastAlpha * motionFastLp[2] + (1f - fastAlpha) * az

        val lx = ax - gravityLp[0]
        val ly = ay - gravityLp[1]
        val lz = az - gravityLp[2]
        val lMag = norm3(lx, ly, lz)

        val hpX = motionFastLp[0] - gravityLp[0]
        val hpY = motionFastLp[1] - gravityLp[1]
        val hpZ = motionFastLp[2] - gravityLp[2]
        val hpMag = norm3(hpX, hpY, hpZ)

        val hardwareLinearFresh = linearSensorTimestampNs > 0L &&
            (timestampNs - linearSensorTimestampNs) <= NO_GYRO_LINEAR_FRESH_NS
        val hwX = if (hardwareLinearFresh) linearSensorValues[0] else 0f
        val hwY = if (hardwareLinearFresh) linearSensorValues[1] else 0f
        val hwZ = if (hardwareLinearFresh) linearSensorValues[2] else 0f
        val hwMag = if (hardwareLinearFresh) norm3(hwX, hwY, hwZ) else 0f

        val jerkEquivalent = if (lastRawAccelTimestampNs > 0L) {
            val dtSec = ((timestampNs - lastRawAccelTimestampNs) / 1_000_000_000f).coerceAtLeast(0.001f)
            val deltaMag = norm3(ax - lastRawAccel[0], ay - lastRawAccel[1], az - lastRawAccel[2])
            (deltaMag / dtSec * NO_GYRO_JERK_WINDOW_SEC).coerceIn(0f, NO_GYRO_JERK_EQ_MAX)
        } else {
            0f
        }

        lastRawAccel[0] = ax
        lastRawAccel[1] = ay
        lastRawAccel[2] = az
        lastRawAccelTimestampNs = timestampNs

        val noGyroCompositeMag = maxOf(
            lMag,
            hpMag * NO_GYRO_HIGHPASS_GAIN,
            if (hardwareLinearFresh) hwMag * NO_GYRO_LINEAR_GAIN else 0f,
            jerkEquivalent
        )

        return when (phase) {
            Phase.STILL -> handleStillPhase(noGyroDevice, lMag, lx, ly, lz, hpX, hpY, hpZ, hardwareLinearFresh, hwX, hwY, hwZ, noGyroCompositeMag, nowMs)
            Phase.LEAN_LEFT -> handleLeanLeftPhase(nowMs)
            Phase.RETURN_UPRIGHT -> handleReturnUprightPhase(nowMs)
            Phase.FORWARD -> handleForwardPhase(ax, ay, az, lx, ly, lz, hpX, hpY, hpZ, hwX, hwY, hwZ, hardwareLinearFresh, noGyroDevice, nowMs)
            Phase.COMPLETE -> Frame(phase = phase, progressPercent = 100, guidance = null)
            else -> Frame(phase = phase, progressPercent = 0, guidance = null)
        }
    }

    private fun handleStillPhase(
        noGyroDevice: Boolean,
        lMag: Float,
        lx: Float,
        ly: Float,
        lz: Float,
        hpX: Float,
        hpY: Float,
        hpZ: Float,
        hardwareLinearFresh: Boolean,
        hwX: Float,
        hwY: Float,
        hwZ: Float,
        noGyroCompositeMag: Float,
        nowMs: Long
    ): Frame {
        val elapsed = nowMs - phaseStartMs
        if (!stillSamplingStarted && elapsed >= STILL_WARMUP_MS) {
            resetStillSamplingStats()
            stillSamplingStarted = true
        }

        val stillMotionMag = if (noGyroDevice) noGyroCompositeMag else lMag

        if (stillSamplingStarted) {
            stillGravitySum[0] += gravityLp[0]
            stillGravitySum[1] += gravityLp[1]
            stillGravitySum[2] += gravityLp[2]
            stillLinearMagSum += stillMotionMag
            stillLinearCount++

            stillMaxAxis[0] = maxAbs(stillMaxAxis[0], lx)
            stillMaxAxis[1] = maxAbs(stillMaxAxis[1], ly)
            stillMaxAxis[2] = maxAbs(stillMaxAxis[2], lz)

            if (noGyroDevice) {
                stillMaxAxis[0] = maxAbs(stillMaxAxis[0], hpX)
                stillMaxAxis[1] = maxAbs(stillMaxAxis[1], hpY)
                stillMaxAxis[2] = maxAbs(stillMaxAxis[2], hpZ)
                if (hardwareLinearFresh) {
                    stillMaxAxis[0] = maxAbs(stillMaxAxis[0], hwX)
                    stillMaxAxis[1] = maxAbs(stillMaxAxis[1], hwY)
                    stillMaxAxis[2] = maxAbs(stillMaxAxis[2], hwZ)
                }
            }

            val stillGoodThreshold = if (noGyroDevice) STILL_LINEAR_GOOD_THRESHOLD_NO_GYRO else STILL_LINEAR_GOOD_THRESHOLD
            if (stillMotionMag <= stillGoodThreshold) {
                stillLinearGoodCount++
            }
        }

        if (!stillSamplingStarted) {
            return Frame(
                phase = phase,
                progressPercent = ((elapsed * 100f) / STILL_WARMUP_MS.coerceAtLeast(1L)).toInt().coerceIn(0, 20),
                guidance = Guidance.StillWarmup((STILL_WARMUP_MS - elapsed).coerceAtLeast(0L) / 1000f)
            )
        }

        val measuredElapsed = (elapsed - STILL_WARMUP_MS).coerceAtLeast(0L)
        val measuredDuration = (STILL_DURATION_MS - STILL_WARMUP_MS).coerceAtLeast(1L)
        val measuredPercent = ((measuredElapsed * 100f) / measuredDuration).toInt().coerceIn(0, 100)
        val phasePercent = 20 + (measuredPercent * 50 / 100)

        if (elapsed >= STILL_DURATION_MS) {
            return if (useLeanStep) {
                beginLeanLeftPhase(nowMs)
            } else {
                beginForwardPhase(nowMs)
            }
        }

        return Frame(
            phase = phase,
            progressPercent = phasePercent,
            guidance = Guidance.Still((STILL_DURATION_MS - elapsed).coerceAtLeast(0L) / 1000f)
        )
    }

    private fun beginLeanLeftPhase(nowMs: Long): Frame {
        phase = Phase.LEAN_LEFT
        phaseStartMs = nowMs

        val safeCount = stillLinearCount.coerceAtLeast(1)
        leanReferenceGravity[0] = stillGravitySum[0] / safeCount
        leanReferenceGravity[1] = stillGravitySum[1] / safeCount
        leanReferenceGravity[2] = stillGravitySum[2] / safeCount
        leanGravitySum.fill(0f)
        leanSampleCount = 0
        leanTargetStableSamples = 0
        uprightStableSamples = 0

        return Frame(
            phase = phase,
            progressPercent = 70,
            guidance = Guidance.LeanLeft(
                targetDeg = LEAN_LEFT_TARGET_DEG.roundToInt(),
                currentDeg = 0,
                remainingDeg = LEAN_LEFT_TARGET_DEG.roundToInt(),
                hold = 0,
                holdRequired = LEAN_LEFT_STABLE_REQUIRED_SAMPLES
            )
        )
    }

    private fun handleLeanLeftPhase(nowMs: Long): Frame {
        val refMag = norm3(leanReferenceGravity[0], leanReferenceGravity[1], leanReferenceGravity[2]).coerceAtLeast(0.0001f)
        val refNorm = floatArrayOf(
            leanReferenceGravity[0] / refMag,
            leanReferenceGravity[1] / refMag,
            leanReferenceGravity[2] / refMag
        )
        val gravMag = norm3(gravityLp[0], gravityLp[1], gravityLp[2]).coerceAtLeast(0.0001f)
        val gravNorm = floatArrayOf(
            gravityLp[0] / gravMag,
            gravityLp[1] / gravMag,
            gravityLp[2] / gravMag
        )
        val tiltDot = (refNorm[0] * gravNorm[0] + refNorm[1] * gravNorm[1] + refNorm[2] * gravNorm[2]).coerceIn(-1f, 1f)
        val tiltDeg = Math.toDegrees(kotlin.math.acos(tiltDot.toDouble())).toFloat()

        if (tiltDeg >= LEAN_LEFT_CAPTURE_MIN_DEG) {
            leanGravitySum[0] += gravityLp[0]
            leanGravitySum[1] += gravityLp[1]
            leanGravitySum[2] += gravityLp[2]
            leanSampleCount++
        }

        if (tiltDeg >= LEAN_LEFT_TARGET_ENTER_DEG) {
            leanTargetStableSamples++
        } else {
            leanTargetStableSamples = 0
        }

        val targetLeanDegInt = LEAN_LEFT_TARGET_DEG.roundToInt()
        val currentLeanDegInt = tiltDeg.roundToInt().coerceAtLeast(0)
        val remainingLeanDegInt = (LEAN_LEFT_TARGET_DEG - tiltDeg).coerceAtLeast(0f).roundToInt()

        val elapsed = nowMs - phaseStartMs
        if (leanTargetStableSamples >= LEAN_LEFT_STABLE_REQUIRED_SAMPLES) {
            return beginReturnUprightPhase(nowMs)
        }
        if (elapsed >= LEAN_LEFT_MAX_DURATION_MS) {
            return Frame(phase = phase, progressPercent = 0, guidance = null, failure = FailureReason.LEAN_LEFT_TOO_SMALL)
        }

        val leanProgress = 70 + (
            leanTargetStableSamples.coerceIn(0, LEAN_LEFT_STABLE_REQUIRED_SAMPLES) * 15 /
                LEAN_LEFT_STABLE_REQUIRED_SAMPLES
            )

        return Frame(
            phase = phase,
            progressPercent = leanProgress,
            guidance = Guidance.LeanLeft(
                targetDeg = targetLeanDegInt,
                currentDeg = currentLeanDegInt,
                remainingDeg = remainingLeanDegInt,
                hold = leanTargetStableSamples.coerceAtMost(LEAN_LEFT_STABLE_REQUIRED_SAMPLES),
                holdRequired = LEAN_LEFT_STABLE_REQUIRED_SAMPLES
            )
        )
    }

    private fun beginReturnUprightPhase(nowMs: Long): Frame {
        phase = Phase.RETURN_UPRIGHT
        phaseStartMs = nowMs
        uprightStableSamples = 0

        return Frame(
            phase = phase,
            progressPercent = 85,
            guidance = Guidance.ReturnUpright(0, RETURN_UPRIGHT_STABLE_REQUIRED_SAMPLES)
        )
    }

    private fun handleReturnUprightPhase(nowMs: Long): Frame {
        val refMag = norm3(leanReferenceGravity[0], leanReferenceGravity[1], leanReferenceGravity[2]).coerceAtLeast(0.0001f)
        val refNorm = floatArrayOf(
            leanReferenceGravity[0] / refMag,
            leanReferenceGravity[1] / refMag,
            leanReferenceGravity[2] / refMag
        )
        val gravMag = norm3(gravityLp[0], gravityLp[1], gravityLp[2]).coerceAtLeast(0.0001f)
        val gravNorm = floatArrayOf(
            gravityLp[0] / gravMag,
            gravityLp[1] / gravMag,
            gravityLp[2] / gravMag
        )
        val tiltDot = (refNorm[0] * gravNorm[0] + refNorm[1] * gravNorm[1] + refNorm[2] * gravNorm[2]).coerceIn(-1f, 1f)
        val tiltDeg = Math.toDegrees(kotlin.math.acos(tiltDot.toDouble())).toFloat()

        if (tiltDeg <= RETURN_UPRIGHT_MAX_DEG) {
            uprightStableSamples++
        } else {
            uprightStableSamples = 0
        }

        val elapsed = nowMs - phaseStartMs
        if (uprightStableSamples >= RETURN_UPRIGHT_STABLE_REQUIRED_SAMPLES) {
            return beginForwardPhase(nowMs)
        }
        if (elapsed >= RETURN_UPRIGHT_MAX_DURATION_MS) {
            return Frame(phase = phase, progressPercent = 0, guidance = null, failure = FailureReason.UPRIGHT_TIMEOUT)
        }

        val uprightProgress = 85 + (
            uprightStableSamples.coerceIn(0, RETURN_UPRIGHT_STABLE_REQUIRED_SAMPLES) * 10 /
                RETURN_UPRIGHT_STABLE_REQUIRED_SAMPLES
            )

        return Frame(
            phase = phase,
            progressPercent = uprightProgress,
            guidance = Guidance.ReturnUpright(
                hold = uprightStableSamples.coerceAtMost(RETURN_UPRIGHT_STABLE_REQUIRED_SAMPLES),
                holdRequired = RETURN_UPRIGHT_STABLE_REQUIRED_SAMPLES
            )
        )
    }

    private fun beginForwardPhase(nowMs: Long): Frame {
        phase = Phase.FORWARD
        phaseStartMs = nowMs

        forwardSettleBaselineSum.fill(0f)
        forwardSettleCount = 0
        forwardSettleBaselineLocked = false
        resetGpsForwardSampling()

        val safeCount = stillLinearCount.coerceAtLeast(1)
        forwardBaseline[0] = stillGravitySum[0] / safeCount
        forwardBaseline[1] = stillGravitySum[1] / safeCount
        forwardBaseline[2] = stillGravitySum[2] / safeCount

        if (useLeanStep) {
            forwardLateralAxisReady = buildForwardLateralAxisFromLean()
            if (!forwardLateralAxisReady) {
                return Frame(phase = phase, progressPercent = 0, guidance = null, failure = FailureReason.LEAN_LEFT_TOO_SMALL)
            }
        } else {
            forwardLateralAxisReady = false
        }

        val noGyroDevice = !hasGyroSensor
        val vibrationMagnitude = norm3(stillMaxAxis[0], stillMaxAxis[1], stillMaxAxis[2])
        val avgStillLinear = if (stillLinearCount > 0) stillLinearMagSum / stillLinearCount else vibrationMagnitude

        forwardNoiseFloor = maxOf(vibrationMagnitude, avgStillLinear * FORWARD_NOISE_AVG_MULTIPLIER)
        if (noGyroDevice) {
            forwardNoiseFloor = maxOf(forwardNoiseFloor, NO_GYRO_FORWARD_NOISE_MIN)
        }

        val excessScale = if (noGyroDevice) FORWARD_EXCESS_SCALE_NO_GYRO else FORWARD_EXCESS_SCALE
        val dynamicExcess = avgStillLinear * excessScale
        val excessMin = if (noGyroDevice) FORWARD_EXCESS_MIN_NO_GYRO else FORWARD_EXCESS_MIN
        forwardExcessTrigger = dynamicExcess.coerceIn(excessMin, FORWARD_EXCESS_MAX)
        forwardTrigger = forwardNoiseFloor + forwardExcessTrigger

        return Frame(
            phase = phase,
            progressPercent = 95,
            guidance = Guidance.ForwardWait(FORWARD_SETTLE_MS / 1000f)
        )
    }

    private fun handleForwardPhase(
        ax: Float,
        ay: Float,
        az: Float,
        lx: Float,
        ly: Float,
        lz: Float,
        hpX: Float,
        hpY: Float,
        hpZ: Float,
        hwX: Float,
        hwY: Float,
        hwZ: Float,
        hardwareLinearFresh: Boolean,
        noGyroDevice: Boolean,
        nowMs: Long
    ): Frame {
        val elapsed = nowMs - phaseStartMs

        forwardSettleBaselineSum[0] += gravityLp[0]
        forwardSettleBaselineSum[1] += gravityLp[1]
        forwardSettleBaselineSum[2] += gravityLp[2]
        forwardSettleCount++

        if (!forwardSettleBaselineLocked) {
            if (elapsed < FORWARD_SETTLE_MS) {
                val settleProgress = 95 + (((elapsed * 100f) / FORWARD_SETTLE_MS.coerceAtLeast(1L)).toInt().coerceIn(0, 100) * 3 / 100)
                return Frame(
                    phase = phase,
                    progressPercent = settleProgress,
                    guidance = Guidance.ForwardWait((FORWARD_SETTLE_MS - elapsed).coerceAtLeast(0L) / 1000f)
                )
            }

            val settleCount = forwardSettleCount.coerceAtLeast(1)
            forwardBaseline[0] = forwardSettleBaselineSum[0] / settleCount
            forwardBaseline[1] = forwardSettleBaselineSum[1] / settleCount
            forwardBaseline[2] = forwardSettleBaselineSum[2] / settleCount
            forwardSettleBaselineLocked = true
            forwardDirectionUnit = null
            forwardDirectionStreak = 0
        }

        val rx = ax - forwardBaseline[0]
        val ry = ay - forwardBaseline[1]
        val rz = az - forwardBaseline[2]
        val rawVector = suppressLateralComponent(floatArrayOf(rx, ry, rz))
        val rawMag = norm3(rawVector[0], rawVector[1], rawVector[2])

        forwardSampleCount++

        if (forwardAcceptedSamples == 0 && elapsed >= FORWARD_RELAX_START_MS) {
            val relaxFloor = if (noGyroDevice) FORWARD_EXCESS_MIN_NO_GYRO else FORWARD_EXCESS_MIN
            forwardExcessTrigger = maxOf(relaxFloor, forwardExcessTrigger * FORWARD_RELAX_FACTOR)
            forwardTrigger = forwardNoiseFloor + forwardExcessTrigger
        }

        val lpVector = suppressLateralComponent(floatArrayOf(lx, ly, lz))
        val lpMag = norm3(lpVector[0], lpVector[1], lpVector[2])
        val hpVector = suppressLateralComponent(floatArrayOf(hpX, hpY, hpZ))
        val hpMagProjected = norm3(hpVector[0], hpVector[1], hpVector[2])
        val hwVector = suppressLateralComponent(floatArrayOf(hwX, hwY, hwZ))
        val hwMagProjected = norm3(hwVector[0], hwVector[1], hwVector[2])

        val rawExcess = (rawMag - forwardNoiseFloor).coerceAtLeast(0f)
        val lpExcess = (lpMag - forwardNoiseFloor).coerceAtLeast(0f)
        val hpExcess = if (noGyroDevice) (hpMagProjected - forwardNoiseFloor).coerceAtLeast(0f) else 0f
        val hwExcess = if (noGyroDevice && hardwareLinearFresh) {
            (hwMagProjected - forwardNoiseFloor).coerceAtLeast(0f)
        } else {
            0f
        }
        val acceptedMagnitude = maxOf(rawExcess, lpExcess, hpExcess, hwExcess)

        if (acceptedMagnitude >= forwardExcessTrigger) {
            val acceptedVector = when {
                hwExcess >= rawExcess && hwExcess >= lpExcess && hwExcess >= hpExcess && hardwareLinearFresh -> hwVector
                hpExcess >= rawExcess && hpExcess >= lpExcess && hpExcess >= hwExcess -> hpVector
                rawExcess >= lpExcess -> rawVector
                else -> lpVector
            }
            val acceptedUnit = normalize(acceptedVector)
            if (acceptedUnit != null && passesForwardDirectionGate(acceptedUnit)) {
                addForwardCandidate(acceptedMagnitude, acceptedVector)
                forwardAcceptedSamples++
            }
        }

        val forwardProgress = 98 + (
            (((computeForwardCompletionRatio().coerceIn(0f, 1f)) * 2f).roundToInt())
            )

        if (requireGpsForwardAssist && elapsed >= FORWARD_GPS_MAX_DURATION_MS && !gpsForwardReady) {
            return Frame(
                phase = phase,
                progressPercent = 0,
                guidance = null,
                failure = FailureReason.NOT_ENOUGH_GPS_FORWARD
            )
        }

        if (forwardAcceptedSamples >= FORWARD_EARLY_FINISH_SAMPLES &&
            elapsed >= FORWARD_EARLY_FINISH_MIN_MS &&
            (!requireGpsForwardAssist || gpsForwardReady)
        ) {
            return finalizeCalibration()
        }

        return Frame(
            phase = phase,
            progressPercent = forwardProgress,
            guidance = Guidance.ForwardDrive(
                accepted = forwardAcceptedSamples.coerceAtMost(FORWARD_EARLY_FINISH_SAMPLES),
                target = FORWARD_EARLY_FINISH_SAMPLES,
                gpsRequired = requireGpsForwardAssist,
                gpsDistanceMeters = gpsTotalDistanceMeters,
                gpsTargetDistanceMeters = GPS_MIN_TOTAL_DISTANCE_M,
                gpsPoints = gpsAcceptedPoints,
                gpsTargetPoints = GPS_MIN_POINTS
            )
        )
    }

    private fun finalizeCalibration(): Frame {
        phase = Phase.COMPLETE

        if (stillLinearCount < 80) {
            return Frame(phase = phase, progressPercent = 0, guidance = null, failure = FailureReason.NOT_ENOUGH_STILL)
        }

        if (forwardTopVectors.size < 4) {
            return Frame(phase = phase, progressPercent = 0, guidance = null, failure = FailureReason.NOT_ENOUGH_FORWARD)
        }

        if (requireGpsForwardAssist && !gpsForwardReady) {
            return Frame(phase = phase, progressPercent = 0, guidance = null, failure = FailureReason.NOT_ENOUGH_GPS_FORWARD)
        }

        val gravityAvg = floatArrayOf(
            stillGravitySum[0] / stillLinearCount,
            stillGravitySum[1] / stillLinearCount,
            stillGravitySum[2] / stillLinearCount
        )

        val gravityNorm = normalize(gravityAvg) ?: return Frame(
            phase = phase,
            progressPercent = 0,
            guidance = null,
            failure = FailureReason.INVALID_GRAVITY
        )

        val forwardWeighted = floatArrayOf(0f, 0f, 0f)
        var weightSum = 0f
        for (item in forwardTopVectors) {
            forwardWeighted[0] += item.vector[0] * item.magnitude
            forwardWeighted[1] += item.vector[1] * item.magnitude
            forwardWeighted[2] += item.vector[2] * item.magnitude
            weightSum += item.magnitude
        }

        if (weightSum <= 0.001f) {
            return Frame(phase = phase, progressPercent = 0, guidance = null, failure = FailureReason.INVALID_FORWARD_VECTOR)
        }

        forwardWeighted[0] /= weightSum
        forwardWeighted[1] /= weightSum
        forwardWeighted[2] /= weightSum

        val gravProjection =
            forwardWeighted[0] * gravityNorm[0] +
                forwardWeighted[1] * gravityNorm[1] +
                forwardWeighted[2] * gravityNorm[2]
        forwardWeighted[0] -= gravityNorm[0] * gravProjection
        forwardWeighted[1] -= gravityNorm[1] * gravProjection
        forwardWeighted[2] -= gravityNorm[2] * gravProjection

        var forwardNorm = normalize(forwardWeighted) ?: return Frame(
            phase = phase,
            progressPercent = 0,
            guidance = null,
            failure = FailureReason.INVALID_FORWARD_VECTOR
        )

        var rightVector = floatArrayOf(
            gravityNorm[1] * forwardNorm[2] - gravityNorm[2] * forwardNorm[1],
            gravityNorm[2] * forwardNorm[0] - gravityNorm[0] * forwardNorm[2],
            gravityNorm[0] * forwardNorm[1] - gravityNorm[1] * forwardNorm[0]
        )
        var rightNorm = normalize(rightVector) ?: return Frame(
            phase = phase,
            progressPercent = 0,
            guidance = null,
            failure = FailureReason.INVALID_FORWARD_VECTOR
        )

        if (useLeanStep) {
            if (leanSampleCount < LEAN_LEFT_MIN_SAMPLES) {
                return Frame(phase = phase, progressPercent = 0, guidance = null, failure = FailureReason.LEAN_LEFT_TOO_SMALL)
            }

            val leanAvg = floatArrayOf(
                leanGravitySum[0] / leanSampleCount,
                leanGravitySum[1] / leanSampleCount,
                leanGravitySum[2] / leanSampleCount
            )
            val leanDelta = floatArrayOf(
                leanAvg[0] - gravityAvg[0],
                leanAvg[1] - gravityAvg[1],
                leanAvg[2] - gravityAvg[2]
            )
            val leanProjection =
                leanDelta[0] * rightNorm[0] +
                    leanDelta[1] * rightNorm[1] +
                    leanDelta[2] * rightNorm[2]

            if (abs(leanProjection) < LEAN_LEFT_MIN_PROJECTION) {
                return Frame(phase = phase, progressPercent = 0, guidance = null, failure = FailureReason.LEAN_LEFT_TOO_SMALL)
            }

            if (leanProjection > 0f) {
                forwardNorm = floatArrayOf(-forwardNorm[0], -forwardNorm[1], -forwardNorm[2])
                rightVector = floatArrayOf(-rightVector[0], -rightVector[1], -rightVector[2])
                rightNorm = normalize(rightVector) ?: return Frame(
                    phase = phase,
                    progressPercent = 0,
                    guidance = null,
                    failure = FailureReason.INVALID_FORWARD_VECTOR
                )
                
                // Инвертираме gravity векторите за консистентност с новата координатна система
                gravityAvg[0] = -gravityAvg[0]
                gravityAvg[1] = -gravityAvg[1]
                gravityAvg[2] = -gravityAvg[2]
                gravityNorm[0] = -gravityNorm[0]
                gravityNorm[1] = -gravityNorm[1]
                gravityNorm[2] = -gravityNorm[2]
            }
        }

        val hasGyroBias = hasGyroSensor && stillGyroCount >= 40
        val gyroBias = if (hasGyroBias) {
            floatArrayOf(
                stillGyroBiasSum[0] / stillGyroCount,
                stillGyroBiasSum[1] / stillGyroCount,
                stillGyroBiasSum[2] / stillGyroCount
            )
        } else {
            floatArrayOf(0f, 0f, 0f)
        }

        val quality = computeQualityScore()
        val stillLinearAvg = if (stillLinearCount > 0) stillLinearMagSum / stillLinearCount else 0f
        val stillVibrationMag = norm3(stillMaxAxis[0], stillMaxAxis[1], stillMaxAxis[2])

        val result = CalibrationResult(
            gravityAvg = gravityAvg,
            gravityNorm = gravityNorm,
            forwardNorm = forwardNorm,
            rightNorm = rightNorm,
            stillMaxAxis = stillMaxAxis.clone(),
            gyroBias = gyroBias,
            hasGyroBias = hasGyroBias,
            quality = quality,
            stillLinearAvg = stillLinearAvg,
            stillVibrationMag = stillVibrationMag,
            forwardNoiseFloor = forwardNoiseFloor,
            forwardExcessTrigger = forwardExcessTrigger,
            stillLinearCount = stillLinearCount,
            forwardAcceptedSamples = forwardAcceptedSamples,
            leanOffsetPortraitComponent = gravityNorm[0],
            leanOffsetLandscapeComponent = gravityNorm[1]
        )

        return Frame(
            phase = phase,
            progressPercent = 100,
            guidance = null,
            result = result
        )
    }

    private fun resetStillSamplingStats() {
        stillGravitySum.fill(0f)
        stillMaxAxis.fill(0f)
        stillLinearMagSum = 0f
        stillLinearGoodCount = 0
        stillLinearCount = 0
        stillGyroBiasSum.fill(0f)
        stillGyroMagSum = 0f
        stillGyroGoodCount = 0
        stillGyroCount = 0
    }

    private fun resetRuntimeState() {
        phase = Phase.IDLE
        phaseStartMs = 0L
        started = false

        gravityLpInitialized = false
        gravitySensorTimestampNs = 0L
        linearSensorTimestampNs = 0L
        motionFastLpInitialized = false
        lastRawAccelTimestampNs = 0L
        gravitySensorValues.fill(0f)
        linearSensorValues.fill(0f)
        motionFastLp.fill(0f)
        lastRawAccel.fill(0f)

        stillGravitySum.fill(0f)
        stillMaxAxis.fill(0f)
        stillLinearMagSum = 0f
        stillLinearGoodCount = 0
        stillLinearCount = 0
        stillSamplingStarted = false

        stillGyroBiasSum.fill(0f)
        stillGyroMagSum = 0f
        stillGyroGoodCount = 0
        stillGyroCount = 0

        forwardTopVectors.clear()
        forwardAcceptedSamples = 0
        forwardSampleCount = 0
        forwardTrigger = 0.6f
        forwardBaseline.fill(0f)
        forwardNoiseFloor = 0f
        forwardExcessTrigger = 0f
        forwardDirectionUnit = null
        forwardDirectionStreak = 0

        resetGpsForwardSampling()

        leanReferenceGravity.fill(0f)
        leanGravitySum.fill(0f)
        leanSampleCount = 0
        leanTargetStableSamples = 0
        uprightStableSamples = 0

        forwardSettleBaselineSum.fill(0f)
        forwardSettleCount = 0
        forwardSettleBaselineLocked = false
        forwardLateralAxis.fill(0f)
        forwardLateralAxisReady = false
    }

    private fun resetGpsForwardSampling(anchorLocation: Location? = null) {
        gpsAnchorLocation = anchorLocation?.let { Location(it) }
        gpsLastAcceptedLocation = anchorLocation?.let { Location(it) }
        gpsAcceptedPoints = if (anchorLocation != null) 1 else 0
        gpsAcceptedSegments = 0
        gpsTotalDistanceMeters = 0f
        gpsDirectionSumEast = 0.0
        gpsDirectionSumNorth = 0.0
        gpsForwardReady = false
    }

    private fun computeEastNorthDeltaMeters(from: Location, to: Location): DoubleArray {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val lon1 = Math.toRadians(from.longitude)
        val lon2 = Math.toRadians(to.longitude)
        val avgLat = (lat1 + lat2) * 0.5
        val east = (lon2 - lon1) * EARTH_RADIUS_M * cos(avgLat)
        val north = (lat2 - lat1) * EARTH_RADIUS_M
        return doubleArrayOf(east, north)
    }

    private fun computeForwardCompletionRatio(): Float {
        val pulseRatio = forwardAcceptedSamples.toFloat() / FORWARD_EARLY_FINISH_SAMPLES.toFloat()
        if (!requireGpsForwardAssist) {
            return pulseRatio
        }
        val distanceRatio = gpsTotalDistanceMeters / GPS_MIN_TOTAL_DISTANCE_M
        val pointRatio = gpsAcceptedPoints.toFloat() / GPS_MIN_POINTS.toFloat()
        return minOf(pulseRatio, distanceRatio, pointRatio)
    }

    private fun addForwardCandidate(magnitude: Float, vector: FloatArray) {
        if (forwardTopVectors.size < MAX_FORWARD_VECTORS) {
            forwardTopVectors.add(WeightedVector(magnitude, vector))
            return
        }

        var minIndex = 0
        var minMag = forwardTopVectors[0].magnitude
        for (i in 1 until forwardTopVectors.size) {
            if (forwardTopVectors[i].magnitude < minMag) {
                minMag = forwardTopVectors[i].magnitude
                minIndex = i
            }
        }

        if (magnitude > minMag) {
            forwardTopVectors[minIndex] = WeightedVector(magnitude, vector)
        }
    }

    private fun passesForwardDirectionGate(unitVector: FloatArray): Boolean {
        val current = forwardDirectionUnit
        if (current == null) {
            forwardDirectionUnit = unitVector.copyOf()
            forwardDirectionStreak = 1
            return false
        }

        val dot =
            current[0] * unitVector[0] +
                current[1] * unitVector[1] +
                current[2] * unitVector[2]

        if (dot < FORWARD_DIRECTION_DOT_MIN) {
            forwardDirectionUnit = unitVector.copyOf()
            forwardDirectionStreak = 1
            return false
        }

        val blended = floatArrayOf(
            current[0] * FORWARD_DIRECTION_BLEND_OLD + unitVector[0] * FORWARD_DIRECTION_BLEND_NEW,
            current[1] * FORWARD_DIRECTION_BLEND_OLD + unitVector[1] * FORWARD_DIRECTION_BLEND_NEW,
            current[2] * FORWARD_DIRECTION_BLEND_OLD + unitVector[2] * FORWARD_DIRECTION_BLEND_NEW
        )
        forwardDirectionUnit = normalize(blended) ?: current
        forwardDirectionStreak++

        return forwardDirectionStreak >= FORWARD_DIRECTION_MIN_STREAK
    }

    private fun buildForwardLateralAxisFromLean(): Boolean {
        if (leanSampleCount < LEAN_LEFT_MIN_SAMPLES) return false

        val leanAvg = floatArrayOf(
            leanGravitySum[0] / leanSampleCount,
            leanGravitySum[1] / leanSampleCount,
            leanGravitySum[2] / leanSampleCount
        )
        val referenceNorm = normalize(leanReferenceGravity) ?: return false

        val delta = floatArrayOf(
            leanAvg[0] - leanReferenceGravity[0],
            leanAvg[1] - leanReferenceGravity[1],
            leanAvg[2] - leanReferenceGravity[2]
        )
        val gravityProjection =
            delta[0] * referenceNorm[0] +
                delta[1] * referenceNorm[1] +
                delta[2] * referenceNorm[2]
        delta[0] -= referenceNorm[0] * gravityProjection
        delta[1] -= referenceNorm[1] * gravityProjection
        delta[2] -= referenceNorm[2] * gravityProjection

        val lateralMagnitude = norm3(delta[0], delta[1], delta[2])
        if (lateralMagnitude < FORWARD_LATERAL_AXIS_MIN_MAG) return false

        forwardLateralAxis[0] = delta[0] / lateralMagnitude
        forwardLateralAxis[1] = delta[1] / lateralMagnitude
        forwardLateralAxis[2] = delta[2] / lateralMagnitude
        return true
    }

    private fun suppressLateralComponent(vector: FloatArray): FloatArray {
        if (!forwardLateralAxisReady) return vector

        val lateralProjection =
            vector[0] * forwardLateralAxis[0] +
                vector[1] * forwardLateralAxis[1] +
                vector[2] * forwardLateralAxis[2]

        return floatArrayOf(
            vector[0] - forwardLateralAxis[0] * lateralProjection,
            vector[1] - forwardLateralAxis[1] * lateralProjection,
            vector[2] - forwardLateralAxis[2] * lateralProjection
        )
    }

    private fun computeQualityScore(): Float {
        val avgLin = if (stillLinearCount > 0) stillLinearMagSum / stillLinearCount else 1f
        val linReference = if (!hasGyroSensor) 0.38f else 0.25f
        val linStillScore = (1f - (avgLin / linReference)).coerceIn(0f, 1f)

        val gyroStillScore = if (stillGyroCount > 0) {
            val avgGyro = stillGyroMagSum / stillGyroCount
            (1f - (avgGyro / 0.20f)).coerceIn(0f, 1f)
        } else {
            0.70f
        }

        val stillRatioScore = if (stillLinearCount > 0) {
            stillLinearGoodCount.toFloat() / stillLinearCount.toFloat()
        } else {
            0f
        }

        val forwardStrength = if (forwardTopVectors.isNotEmpty()) {
            val avgTop = forwardTopVectors.map { it.magnitude }.average().toFloat()
            (avgTop / 2.5f).coerceIn(0f, 1f)
        } else {
            0f
        }

        val forwardSampleScore = (forwardAcceptedSamples / 20f).coerceIn(0f, 1f)

        return (
            0.35f * linStillScore +
                0.20f * gyroStillScore +
                0.20f * stillRatioScore +
                0.15f * forwardStrength +
                0.10f * forwardSampleScore
            ).coerceIn(0f, 1f)
    }

    companion object {
        private const val STILL_DURATION_MS = 5000L
        private const val STILL_WARMUP_MS = 1200L
        private const val LEAN_LEFT_MAX_DURATION_MS = 12_000L
        private const val RETURN_UPRIGHT_MAX_DURATION_MS = 10_000L
        private const val FORWARD_SETTLE_MS = 3000L
        private const val FORWARD_GPS_MAX_DURATION_MS = 20_000L
        private const val LEAN_LEFT_TARGET_DEG = 20f
        private const val LEAN_LEFT_TARGET_ENTER_DEG = 18f
        private const val LEAN_LEFT_CAPTURE_MIN_DEG = 14f
        private const val LEAN_LEFT_STABLE_REQUIRED_SAMPLES = 18
        private const val RETURN_UPRIGHT_MAX_DEG = 6f
        private const val RETURN_UPRIGHT_STABLE_REQUIRED_SAMPLES = 18
        private const val LEAN_LEFT_MIN_SAMPLES = 12
        private const val LEAN_LEFT_MIN_PROJECTION = 0.15f
        private const val FORWARD_LATERAL_AXIS_MIN_MAG = 0.12f
        private const val STILL_LINEAR_GOOD_THRESHOLD = 0.20f
        private const val STILL_LINEAR_GOOD_THRESHOLD_NO_GYRO = 0.32f
        private const val STILL_GYRO_GOOD_THRESHOLD = 0.08f
        private const val MAX_FORWARD_VECTORS = 40
        private const val FORWARD_NOISE_AVG_MULTIPLIER = 1.10f
        private const val FORWARD_EXCESS_SCALE = 0.60f
        private const val FORWARD_EXCESS_SCALE_NO_GYRO = 0.48f
        private const val FORWARD_EXCESS_MIN = 0.08f
        private const val FORWARD_EXCESS_MIN_NO_GYRO = 0.06f
        private const val FORWARD_EXCESS_MAX = 0.24f
        private const val NO_GYRO_FORWARD_NOISE_MIN = 0.02f
        private const val FORWARD_RELAX_START_MS = 900L
        private const val FORWARD_RELAX_FACTOR = 0.975f
        private const val FORWARD_EARLY_FINISH_SAMPLES = 15
        private const val FORWARD_EARLY_FINISH_MIN_MS = 350L
        private const val FORWARD_DIRECTION_DOT_MIN = 0.60f
        private const val FORWARD_DIRECTION_MIN_STREAK = 2
        private const val FORWARD_DIRECTION_BLEND_OLD = 0.80f
        private const val FORWARD_DIRECTION_BLEND_NEW = 0.20f
        private const val GPS_MIN_TOTAL_DISTANCE_M = 20f
        private const val GPS_MIN_POINTS = 6
        private const val GPS_MIN_SPEED_MPS = 4.2f
        private const val GPS_MIN_SEGMENT_DISTANCE_M = 1.5f
        private const val GPS_MAX_HORIZONTAL_ACCURACY_M = 12f
        private const val GPS_DIRECTION_DOT_MIN = 0.97
        private const val NO_GYRO_GRAVITY_SENSOR_BLEND = 0.70f
        private const val NO_GYRO_GRAVITY_FRESH_NS = 220_000_000L
        private const val NO_GYRO_LINEAR_FRESH_NS = 140_000_000L
        private const val NO_GYRO_HIGHPASS_GAIN = 1.20f
        private const val NO_GYRO_LINEAR_GAIN = 1.10f
        private const val NO_GYRO_JERK_WINDOW_SEC = 0.055f
        private const val NO_GYRO_JERK_EQ_MAX = 1.20f
        private const val EARTH_RADIUS_M = 6_371_000.0

        @JvmStatic
        fun leanOffsetDegFromGravityComponent(component: Float): Float {
            val normalized = component.coerceIn(-1f, 1f)
            return (-Math.toDegrees(asin(normalized.toDouble()))).toFloat().coerceIn(-89f, 89f)
        }
    }

    private fun normalize(v: FloatArray): FloatArray? {
        val mag = norm3(v[0], v[1], v[2])
        if (mag < 0.0001f) return null
        return floatArrayOf(v[0] / mag, v[1] / mag, v[2] / mag)
    }

    private fun norm3(x: Float, y: Float, z: Float): Float {
        return sqrt(x * x + y * y + z * z)
    }

    private fun maxAbs(current: Float, candidate: Float): Float {
        return maxOf(current, abs(candidate))
    }
}
