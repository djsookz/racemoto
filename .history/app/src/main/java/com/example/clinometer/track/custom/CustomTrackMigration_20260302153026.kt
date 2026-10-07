package com.example.clinometer.tracking

import com.example.clinometer.GeoPoint

object CustomTrackMigration {
    fun toV2(track: CustomTrack): CustomTrackDefinitionV2 {
        val mode = when (track.type) {
            CustomTrack.TrackType.CIRCUIT -> CustomTrackMode.CIRCUIT
            CustomTrack.TrackType.POINT_TO_POINT -> CustomTrackMode.POINT_TO_POINT
        }

        val startFinishPoints = track.points
            .filter { it.pointType == CustomTrack.TrackPoint.PointType.START_FINISH }
            .map { it.geoPoint }
        val startPoints = track.points
            .filter { it.pointType == CustomTrack.TrackPoint.PointType.START }
            .map { it.geoPoint }
        val finishPoints = track.points
            .filter { it.pointType == CustomTrack.TrackPoint.PointType.FINISH }
            .map { it.geoPoint }
        val snapPoints = track.points
            .filter { it.pointType == CustomTrack.TrackPoint.PointType.SNAP_HELPER }
            .map { it.geoPoint }

        val startGate = when (mode) {
            CustomTrackMode.CIRCUIT -> lineFromPoints(startFinishPoints)
                ?: lineFromPoints(snapPoints.take(2))
            CustomTrackMode.POINT_TO_POINT -> lineFromPoints(startPoints)
                ?: lineFromPoints(startFinishPoints)
        }

        val finishGate = when (mode) {
            CustomTrackMode.CIRCUIT -> null
            CustomTrackMode.POINT_TO_POINT -> lineFromPoints(finishPoints)
        }

        return CustomTrackDefinitionV2(
            id = track.id,
            name = track.name,
            mode = mode,
            createdAt = track.createdAt,
            startGate = startGate,
            finishGate = finishGate,
            sectorGates = emptyList(),
            referencePath = snapPoints
        )
    }

    fun toLegacy(track: CustomTrackDefinitionV2): CustomTrack {
        val legacyType = when (track.mode) {
            CustomTrackMode.CIRCUIT -> CustomTrack.TrackType.CIRCUIT
            CustomTrackMode.POINT_TO_POINT -> CustomTrack.TrackType.POINT_TO_POINT
        }

        val points = mutableListOf<CustomTrack.TrackPoint>()

        when (track.mode) {
            CustomTrackMode.CIRCUIT -> {
                track.startGate?.let { gate ->
                    points.add(
                        CustomTrack.TrackPoint(
                            geoPoint = gate.start,
                            pointType = CustomTrack.TrackPoint.PointType.START_FINISH
                        )
                    )
                    points.add(
                        CustomTrack.TrackPoint(
                            geoPoint = gate.end,
                            pointType = CustomTrack.TrackPoint.PointType.START_FINISH
                        )
                    )
                }
            }
            CustomTrackMode.POINT_TO_POINT -> {
                track.startGate?.let { gate ->
                    points.add(
                        CustomTrack.TrackPoint(
                            geoPoint = gate.start,
                            pointType = CustomTrack.TrackPoint.PointType.START
                        )
                    )
                    points.add(
                        CustomTrack.TrackPoint(
                            geoPoint = gate.end,
                            pointType = CustomTrack.TrackPoint.PointType.START
                        )
                    )
                }
                track.finishGate?.let { gate ->
                    points.add(
                        CustomTrack.TrackPoint(
                            geoPoint = gate.start,
                            pointType = CustomTrack.TrackPoint.PointType.FINISH
                        )
                    )
                    points.add(
                        CustomTrack.TrackPoint(
                            geoPoint = gate.end,
                            pointType = CustomTrack.TrackPoint.PointType.FINISH
                        )
                    )
                }
            }
        }

        track.referencePath.forEach { point ->
            points.add(
                CustomTrack.TrackPoint(
                    geoPoint = point,
                    pointType = CustomTrack.TrackPoint.PointType.SNAP_HELPER
                )
            )
        }

        return CustomTrack(
            id = track.id,
            name = track.name,
            type = legacyType,
            points = points,
            createdAt = track.createdAt
        )
    }

    private fun lineFromPoints(points: List<GeoPoint>): GateLine? {
        return when {
            points.size >= 2 -> GateLine(start = points[0], end = points[1])
            points.size == 1 -> GateLine(start = points[0], end = points[0])
            else -> null
        }
    }
}
