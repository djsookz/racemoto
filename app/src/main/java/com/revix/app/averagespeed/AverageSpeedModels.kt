package com.revix.app.averagespeed

import androidx.annotation.Keep
import com.mapbox.geojson.Point

@Keep
data class AverageSpeedCatalogFile(
    val version: Int = 1,
    val source: String = "",
    val sections: List<AverageSpeedSectionDto> = emptyList()
)

@Keep
data class AverageSpeedSectionDto(
    val id: String = "",
    val road: String = "",
    val startName: String = "",
    val endName: String = "",
    val startKm: String = "",
    val endKm: String = "",
    val startLat: Double = 0.0,
    val startLon: Double = 0.0,
    val endLat: Double = 0.0,
    val endLon: Double = 0.0,
    val lengthMeters: Int = 0,
    val speedLimitKmh: Int = 90,
    val polyline: List<List<Double>>? = null,
    val displayOpposite: List<List<List<Double>>>? = null
)

data class AverageSpeedSection(
    val id: String,
    val road: String,
    val startName: String,
    val endName: String,
    val startKm: String,
    val endKm: String,
    val lengthMeters: Double,
    val speedLimitKmh: Int,
    val points: List<Point>,
    val oppositeStretches: List<List<Point>> = emptyList()
) {
    val start: Point get() = points.first()
    val end: Point get() = points.last()
    val label: String get() = "$startName → $endName"
}

internal fun AverageSpeedSectionDto.toSection(): AverageSpeedSection? {
    if (id.isBlank()) return null
    if (!startLat.isFinite() || !startLon.isFinite() || !endLat.isFinite() || !endLon.isFinite()) {
        return null
    }
    val polyPoints = polyline
        ?.mapNotNull { pair ->
            if (pair.size < 2) return@mapNotNull null
            val lon = pair[0]
            val lat = pair[1]
            if (!lat.isFinite() || !lon.isFinite()) return@mapNotNull null
            Point.fromLngLat(lon, lat)
        }
        ?.takeIf { it.size >= 2 }
    val points = polyPoints ?: listOf(
        Point.fromLngLat(startLon, startLat),
        Point.fromLngLat(endLon, endLat)
    )
    val oppositeStretches = displayOpposite
        ?.mapNotNull { stretch ->
            stretch.mapNotNull { pair ->
                if (pair.size < 2) return@mapNotNull null
                val lon = pair[0]
                val lat = pair[1]
                if (!lat.isFinite() || !lon.isFinite()) return@mapNotNull null
                Point.fromLngLat(lon, lat)
            }.takeIf { it.size >= 2 }
        }
        .orEmpty()
    val length = if (lengthMeters > 0) {
        lengthMeters.toDouble()
    } else {
        AverageSpeedGeometry.lengthMeters(points)
    }
    return AverageSpeedSection(
        id = id,
        road = road,
        startName = startName,
        endName = endName,
        startKm = startKm,
        endKm = endKm,
        lengthMeters = length,
        speedLimitKmh = speedLimitKmh,
        points = points,
        oppositeStretches = oppositeStretches
    )
}
