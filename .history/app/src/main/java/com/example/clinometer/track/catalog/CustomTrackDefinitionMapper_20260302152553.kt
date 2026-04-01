package com.example.clinometer.track.catalog

import com.example.clinometer.GeoPoint
import com.example.clinometer.track.custom.CustomTrack

object CustomTrackDefinitionMapper {
    fun fromCustomTrack(track: CustomTrack): TrackDefinition {
        val mode = when (track.type) {
            CustomTrack.TrackType.CIRCUIT -> TrackMode.CIRCUIT
            CustomTrack.TrackType.POINT_TO_POINT -> TrackMode.POINT_TO_POINT
        }

        val lapSequence = when (track.type) {
            CustomTrack.TrackType.CIRCUIT -> buildCircuitLapSequence(track)
            CustomTrack.TrackType.POINT_TO_POINT -> buildPointToPointLapSequence(track)
        }

        val startFinishGate = track.points
            .filter { it.pointType == CustomTrack.TrackPoint.PointType.START_FINISH }
            .take(2)
            .let { points ->
                when {
                    points.size >= 2 -> TrackGate(points[0].geoPoint, points[1].geoPoint)
                    points.size == 1 -> TrackGate(points[0].geoPoint, points[0].geoPoint)
                    else -> null
                }
            }

        return TrackDefinition(
            id = track.id,
            name = track.name,
            description = "Custom ${mode.name.lowercase().replace('_', '-')}",
            country = "Custom",
            lengthKm = 0.0,
            turns = 0,
            mode = mode,
            gpxResourceId = null,
            startFinishGate = startFinishGate,
            lapSequence = lapSequence
        )
    }

    private fun buildCircuitLapSequence(track: CustomTrack): List<GeoPoint> {
        val startFinish = track.points
            .filter { it.pointType == CustomTrack.TrackPoint.PointType.START_FINISH }
            .map { it.geoPoint }

        if (startFinish.size >= 2) {
            val p1 = startFinish[0]
            val p2 = startFinish[1]
            return listOf(p1, p2, p1, p2)
        }

        val fallback = track.points.firstOrNull()?.geoPoint
        return if (fallback != null) listOf(fallback) else emptyList()
    }

    private fun buildPointToPointLapSequence(track: CustomTrack): List<GeoPoint> {
        val starts = track.points
            .filter { it.pointType == CustomTrack.TrackPoint.PointType.START }
            .map { it.geoPoint }
        val snaps = track.points
            .filter { it.pointType == CustomTrack.TrackPoint.PointType.SNAP_HELPER }
            .map { it.geoPoint }
        val finishes = track.points
            .filter { it.pointType == CustomTrack.TrackPoint.PointType.FINISH }
            .map { it.geoPoint }

        return starts + snaps + finishes
    }
}
