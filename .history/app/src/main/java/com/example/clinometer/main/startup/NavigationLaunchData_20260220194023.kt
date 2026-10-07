package com.example.clinometer.main.startup

data class NavigationLaunchData(
    val startFromPreview: Boolean,
    val routeGeometryJson: String?,
    val directionsResponseJson: String?,
    val destinationLat: Double,
    val destinationLon: Double,
    val originLat: Double,
    val originLon: Double,
    val originBearing: Float,
    val allowMotorways: Boolean,
    val destinationName: String,
    val preferredRoutePolyline: String?
)
