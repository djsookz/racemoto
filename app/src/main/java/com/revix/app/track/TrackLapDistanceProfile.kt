package com.revix.app.track

import com.revix.app.GeoPoint
import com.revix.app.RoutePoint

/**
 * Maps a recorded lap onto a distance axis (meters from start/finish) for pro-style compare sync.
 */
class TrackLapDistanceProfile private constructor(
    private val route: List<RoutePoint>,
    private val distanceMeters: FloatArray,
    val maxDistanceMeters: Float,
    val usesReferencePath: Boolean
) {
    fun routePointAtDistance(targetMeters: Float): RoutePoint {
        if (route.isEmpty()) {
            return RoutePoint(GeoPoint(0.0, 0.0), 0f, 0f, 0L, 0L)
        }
        if (route.size == 1) return route.first()

        val clamped = targetMeters.coerceIn(0f, maxDistanceMeters)
        if (clamped <= distanceMeters.first()) return route.first()
        if (clamped >= distanceMeters.last()) return route.last()

        var leftIndex = 0
        var rightIndex = route.lastIndex
        for (index in 0 until route.lastIndex) {
            if (clamped in distanceMeters[index]..distanceMeters[index + 1]) {
                leftIndex = index
                rightIndex = index + 1
                break
            }
        }

        val leftDistance = distanceMeters[leftIndex]
        val rightDistance = distanceMeters[rightIndex]
        val deltaDistance = (rightDistance - leftDistance).coerceAtLeast(1e-3f)
        val factor = ((clamped - leftDistance) / deltaDistance).coerceIn(0f, 1f)

        val left = route[leftIndex]
        val right = route[rightIndex]
        val latitude = left.geoPoint.latitude + (right.geoPoint.latitude - left.geoPoint.latitude) * factor
        val longitude = left.geoPoint.longitude + (right.geoPoint.longitude - left.geoPoint.longitude) * factor
        val speed = left.speed + (right.speed - left.speed) * factor
        val angle = left.angle + (right.angle - left.angle) * factor
        val timestamp = left.timestamp + ((right.timestamp - left.timestamp) * factor).toLong()
        val absoluteTime = left.absoluteTime + ((right.absoluteTime - left.absoluteTime) * factor).toLong()

        return RoutePoint(
            geoPoint = GeoPoint(latitude, longitude),
            speed = speed,
            angle = angle,
            timestamp = timestamp,
            absoluteTime = absoluteTime
        )
    }

    fun elapsedMsAtDistance(targetMeters: Float): Long {
        return routePointAtDistance(targetMeters).timestamp.coerceAtLeast(0L)
    }

    fun distanceAtElapsedMs(targetMs: Long): Float {
        if (route.isEmpty()) return 0f
        if (route.size == 1) return 0f

        val clampedMs = targetMs.coerceIn(route.first().timestamp, route.last().timestamp)
        if (clampedMs <= route.first().timestamp) return distanceMeters.first()
        if (clampedMs >= route.last().timestamp) return distanceMeters.last()

        var leftIndex = 0
        var rightIndex = route.lastIndex
        for (index in 0 until route.lastIndex) {
            val current = route[index]
            val next = route[index + 1]
            if (clampedMs in current.timestamp..next.timestamp) {
                leftIndex = index
                rightIndex = index + 1
                break
            }
        }

        val left = route[leftIndex]
        val right = route[rightIndex]
        val deltaMs = (right.timestamp - left.timestamp).coerceAtLeast(1L).toFloat()
        val factor = ((clampedMs - left.timestamp) / deltaMs).coerceIn(0f, 1f)
        val leftDistance = distanceMeters[leftIndex]
        val rightDistance = distanceMeters[rightIndex]
        return leftDistance + (rightDistance - leftDistance) * factor
    }

    fun distanceAtTimeSeconds(timeSeconds: Float): Float {
        return distanceAtElapsedMs((timeSeconds * 1000f).toLong())
    }

    fun chartEntries(selector: (RoutePoint) -> Float): List<Pair<Float, Float>> {
        return route.indices.map { index ->
            distanceMeters[index] to selector(route[index])
        }
    }

    fun chartEntriesFromTimes(
        timesSeconds: List<Float>,
        values: List<Float>
    ): List<Pair<Float, Float>> {
        if (timesSeconds.isEmpty() || values.isEmpty()) return emptyList()
        val size = minOf(timesSeconds.size, values.size)
        return (0 until size).map { index ->
            distanceAtTimeSeconds(timesSeconds[index]) to values[index]
        }
    }

    companion object {
        private const val MIN_USABLE_DISTANCE_METERS = 100f

        fun deltaSecondsAtDistance(
            currentProfile: TrackLapDistanceProfile,
            compareProfile: TrackLapDistanceProfile,
            distanceMeters: Float
        ): Float {
            val currentMs = currentProfile.elapsedMsAtDistance(distanceMeters)
            val compareMs = compareProfile.elapsedMsAtDistance(distanceMeters)
            return (compareMs - currentMs) / 1000f
        }

        fun buildDeltaTimeSamples(
            currentProfile: TrackLapDistanceProfile,
            compareProfile: TrackLapDistanceProfile,
            maxDistanceMeters: Float,
            stepMeters: Float = 8f
        ): List<Pair<Float, Float>> {
            if (maxDistanceMeters <= 0f) return emptyList()
            val step = stepMeters.coerceIn(4f, 25f)
            val samples = mutableListOf<Pair<Float, Float>>()
            var distance = 0f
            while (distance <= maxDistanceMeters) {
                val deltaSec = deltaSecondsAtDistance(currentProfile, compareProfile, distance)
                samples += distance to deltaSec
                distance += step
            }
            val tailDelta = deltaSecondsAtDistance(currentProfile, compareProfile, maxDistanceMeters)
            if (samples.lastOrNull()?.first != maxDistanceMeters) {
                samples += maxDistanceMeters to tailDelta
            }
            return samples
        }

        fun build(
            route: List<RoutePoint>,
            referenceRoute: TrackReferenceRoute?
        ): TrackLapDistanceProfile? {
            if (route.size < 2) return null

            val distances = FloatArray(route.size)
            val usesReferencePath = referenceRoute != null

            if (referenceRoute != null) {
                var previousDistance: Float? = null
                for (index in route.indices) {
                    val point = route[index]
                    val maxForward = if (index == 0 || previousDistance == null) {
                        Float.MAX_VALUE
                    } else {
                        val stepMeters = route[index - 1].geoPoint
                            .distanceToAsDouble(point.geoPoint)
                            .toFloat()
                        // Allow GPS noise / brief dropouts, but block jumps to far track segments.
                        (stepMeters * 4f + 30f).coerceIn(45f, 220f)
                    }
                    val mapped = if (previousDistance == null) {
                        referenceRoute.distanceFromStartFinish(
                            latitude = point.geoPoint.latitude,
                            longitude = point.geoPoint.longitude
                        )
                    } else {
                        referenceRoute.distanceFromStartFinishContinuing(
                            latitude = point.geoPoint.latitude,
                            longitude = point.geoPoint.longitude,
                            previousFromStartFinish = previousDistance,
                            maxForwardMeters = maxForward
                        )
                    }
                    distances[index] = mapped
                    previousDistance = mapped
                }
            } else {
                var cumulative = 0f
                for (index in route.indices) {
                    if (index > 0) {
                        cumulative += route[index - 1].geoPoint
                            .distanceToAsDouble(route[index].geoPoint)
                            .toFloat()
                    }
                    distances[index] = cumulative
                }
            }

            // Progressive mapping is already unwrapped; keep light cleanup only.
            monotonize(distances)
            normalizeToZero(distances)
            monotonize(distances)

            val maxDistance = distances.lastOrNull() ?: 0f
            if (maxDistance < MIN_USABLE_DISTANCE_METERS) return null

            return TrackLapDistanceProfile(
                route = route,
                distanceMeters = distances,
                maxDistanceMeters = maxDistance,
                usesReferencePath = usesReferencePath
            )
        }

        private fun monotonize(distances: FloatArray) {
            for (index in 1 until distances.size) {
                if (distances[index] < distances[index - 1]) {
                    distances[index] = distances[index - 1]
                }
            }
        }

        private fun normalizeToZero(distances: FloatArray) {
            if (distances.isEmpty()) return
            val offset = distances[0]
            for (index in distances.indices) {
                distances[index] = (distances[index] - offset).coerceAtLeast(0f)
            }
        }
    }
}
