package com.example.clinometer.main.startup

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.clinometer.main.map.NavigationDataCache

object NavigationLaunchDataParser {

    private const val TAG = "NavigationLaunchDataParser"

    fun parse(context: Context, intent: Intent): NavigationLaunchData {
        return NavigationLaunchData(
            startFromPreview = intent.getBooleanExtra("nav_start_from_preview", false),
            routeGeometryJson = resolveRouteGeometryJson(context, intent),
            directionsResponseJson = resolveDirectionsResponseJson(context, intent),
            destinationLat = intent.getDoubleExtra("destination_latitude", 0.0),
            destinationLon = intent.getDoubleExtra("destination_longitude", 0.0),
            originLat = intent.getDoubleExtra("origin_latitude", 0.0),
            originLon = intent.getDoubleExtra("origin_longitude", 0.0),
            originBearing = intent.getFloatExtra("origin_bearing", 0f),
            allowMotorways = intent.getBooleanExtra("allow_motorways", true),
            destinationName = intent.getStringExtra("destination_name") ?: "",
            preferredRoutePolyline = intent.getStringExtra("preferred_route_polyline")
        )
    }

    private fun resolveRouteGeometryJson(context: Context, intent: Intent): String? {
        val routeGeometryInCache = intent.getBooleanExtra("route_geometry_in_cache", false)
        return if (routeGeometryInCache) {
            try {
                NavigationDataCache.loadRouteGeometry(context).also {
                    NavigationDataCache.clear(context)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to load route geometry from cache", error)
                null
            }
        } else {
            intent.getStringExtra("route_geometry")
        }
    }

    private fun resolveDirectionsResponseJson(context: Context, intent: Intent): String? {
        val directionsResponseInCache = intent.getBooleanExtra("directions_response_in_cache", false)
        return if (directionsResponseInCache) {
            try {
                NavigationDataCache.loadDirectionsResponse(context)
            } catch (error: Exception) {
                Log.e(TAG, "Failed to load directions response from cache", error)
                null
            }
        } else {
            intent.getStringExtra("directions_response_json")
        }
    }
}
