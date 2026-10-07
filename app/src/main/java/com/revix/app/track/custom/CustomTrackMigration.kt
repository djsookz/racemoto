package com.revix.app.tracking

import com.revix.app.GeoPoint

object CustomTrackMigration {
    fun toV2(track: CustomTrack): CustomTrackDefinitionV2 {
        val mode = when (track.type as CustomTrack.TrackType?) {
            CustomTrack.TrackType.POINT_TO_POINT -> CustomTrackMode.POINT_TO_POINT
            else -> CustomTrackMode.CIRCUIT
        }

        val safePoints = (track.points as List<CustomTrack.TrackPoint?>?)?.filterNotNull().orEmpty()
        val startFinishPoints = safePoints
            .filter { it.pointType == CustomTrack.TrackPoint.PointType.START_FINISH }
            .mapNotNull { it.geoPoint }
        val startPoints = safePoints
            .filter { it.pointType == CustomTrack.TrackPoint.PointType.START }
            .mapNotNull { it.geoPoint }
        val finishPoints = safePoints
            .filter { it.pointType == CustomTrack.TrackPoint.PointType.FINISH }
            .mapNotNull { it.geoPoint }
        val snapPoints = safePoints
            .filter { it.pointType == CustomTrack.TrackPoint.PointType.SNAP_HELPER }
            .mapNotNull { it.geoPoint }

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
            id = (track.id as String?) ?: "custom_${track.createdAt}",
            name = (track.name as String?) ?: "",
            mode = mode,
            creationMode = CustomTrackCreationMode.PHONE,
            createdAt = track.createdAt,
            startGate = startGate,
            finishGate = finishGate,
            sectorGates = emptyList(),
            referencePath = snapPoints,
            measuredDistanceMeters = null
        )
    }

    fun toLegacy(track: CustomTrackDefinitionV2): CustomTrack {
        val mode = (track.mode as CustomTrackMode?) ?: CustomTrackMode.CIRCUIT
        val legacyType = when (mode) {
            CustomTrackMode.POINT_TO_POINT -> CustomTrack.TrackType.POINT_TO_POINT
            CustomTrackMode.CIRCUIT -> CustomTrack.TrackType.CIRCUIT
        }

        val points = mutableListOf<CustomTrack.TrackPoint>()

        when (mode) {
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

        (track.referencePath as List<GeoPoint?>?)?.filterNotNull().orEmpty().forEach { point ->
            points.add(
                CustomTrack.TrackPoint(
                    geoPoint = point,
                    pointType = CustomTrack.TrackPoint.PointType.SNAP_HELPER
                )
            )
        }

        return CustomTrack(
            id = (track.id as String?) ?: "custom_${track.createdAt}",
            name = (track.name as String?) ?: "",
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
