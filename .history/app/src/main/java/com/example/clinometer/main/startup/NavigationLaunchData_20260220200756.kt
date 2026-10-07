package com.example.clinometer.main.startup

import com.example.clinometer.navigation.DirectionsStep
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point

data class NavigationLaunchData(
    val startFromPreview: Boolean,
    val directionsResponseJson: String?,
    val parsedRouteGeometry: LineString?,
    val routePoints: List<Point>,
    val navigationSteps: List<DirectionsStep>,
    val destinationPoint: Point?,
    val originLat: Double,
    val originLon: Double,
    val originBearing: Float,
    val allowMotorways: Boolean,
    val destinationName: String,
    val preferredRoutePolyline: String?
)
