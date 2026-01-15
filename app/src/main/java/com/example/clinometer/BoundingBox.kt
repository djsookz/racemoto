package com.example.clinometer

/**
 * Simple BoundingBox class to replace OSMDroid BoundingBox
 */
data class BoundingBox(
    val latNorth: Double,
    val latSouth: Double,
    val lonEast: Double,
    val lonWest: Double
) {
    companion object {
        /**
         * Create bounding box from list of GeoPoints
         */
        fun fromGeoPointsSafe(geoPoints: List<GeoPoint>): BoundingBox {
            if (geoPoints.isEmpty()) {
                // Default bounding box if no points
                return BoundingBox(0.0, 0.0, 0.0, 0.0)
            }
            
            var minLat = geoPoints[0].latitude
            var maxLat = geoPoints[0].latitude
            var minLon = geoPoints[0].longitude
            var maxLon = geoPoints[0].longitude
            
            for (point in geoPoints) {
                minLat = minOf(minLat, point.latitude)
                maxLat = maxOf(maxLat, point.latitude)
                minLon = minOf(minLon, point.longitude)
                maxLon = maxOf(maxLon, point.longitude)
            }
            
            return BoundingBox(
                latNorth = maxLat,
                latSouth = minLat,
                lonEast = maxLon,
                lonWest = minLon
            )
        }
    }
}

