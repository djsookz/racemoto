package com.example.clinometer.tracking

import org.osmdroid.util.GeoPoint
import kotlin.math.*

/**
 * Road Network Graph - builds connections between roads
 * Detects junctions and connected road segments
 */
class RoadNetworkGraph {
    
    data class RoadNode(
        val point: GeoPoint,
        val connectedRoads: MutableList<RoadConnection> = mutableListOf()
    )
    
    data class RoadConnection(
        val fromRoad: RoadGeometry.RoadSegment,
        val toRoad: RoadGeometry.RoadSegment,
        val junctionPoint: GeoPoint,
        val fromIndex: Int, // Index in fromRoad.points
        val toIndex: Int    // Index in toRoad.points
    )
    
    private val nodes = mutableMapOf<String, RoadNode>()
    private val roadConnections = mutableListOf<RoadConnection>()
    
    companion object {
        private const val JUNCTION_THRESHOLD = 20.0 // meters - roads closer than this are "connected"
    }
    
    /**
     * Build graph from road segments
     */
    fun buildGraph(roads: List<RoadGeometry.RoadSegment>) {
        nodes.clear()
        roadConnections.clear()
        
        android.util.Log.d("RoadNetworkGraph", "Building road network graph from ${roads.size} roads...")
        
        val startTime = System.currentTimeMillis()
        
        // Find all junctions (where roads connect)
        for (i in roads.indices) {
            val road1 = roads[i]
            
            for (j in (i + 1) until roads.size) {
                val road2 = roads[j]
                
                // Check if roads connect (any endpoint close to another)
                val connections = findConnections(road1, road2)
                roadConnections.addAll(connections)
            }
        }
        
        val elapsedTime = System.currentTimeMillis() - startTime
        
        android.util.Log.d("RoadNetworkGraph", "Built graph in ${elapsedTime}ms:")
        android.util.Log.d("RoadNetworkGraph", "  - Roads: ${roads.size}")
        android.util.Log.d("RoadNetworkGraph", "  - Connections: ${roadConnections.size}")
        android.util.Log.d("RoadNetworkGraph", "  - Junctions: ${nodes.size}")
    }
    
    /**
     * Find connections between two roads
     * OPTIMIZED: Check only endpoints (start/end), not all points!
     */
    private fun findConnections(
        road1: RoadGeometry.RoadSegment,
        road2: RoadGeometry.RoadSegment
    ): List<RoadConnection> {
        val connections = mutableListOf<RoadConnection>()
        
        if (road1.points.size < 2 || road2.points.size < 2) return connections
        
        // Only check endpoints (start and end of each road)
        val road1Endpoints = listOf(
            0 to road1.points.first(),
            road1.points.size - 1 to road1.points.last()
        )
        
        val road2Endpoints = listOf(
            0 to road2.points.first(),
            road2.points.size - 1 to road2.points.last()
        )
        
        // Check all combinations of endpoints (4 checks instead of 250,000!)
        for ((i, p1) in road1Endpoints) {
            for ((j, p2) in road2Endpoints) {
                val distance = calculateDistanceInMeters(p1, p2)
                
                if (distance < JUNCTION_THRESHOLD) {
                    // Found a connection!
                    val junctionPoint = GeoPoint(
                        (p1.latitude + p2.latitude) / 2.0,
                        (p1.longitude + p2.longitude) / 2.0
                    )
                    
                    connections.add(
                        RoadConnection(
                            fromRoad = road1,
                            toRoad = road2,
                            junctionPoint = junctionPoint,
                            fromIndex = i,
                            toIndex = j
                        )
                    )
                }
            }
        }
        
        return connections
    }
    
    /**
     * Find path between two roads (through junctions)
     * Returns list of points to follow
     */
    fun findPathBetweenRoads(
        fromRoad: RoadGeometry.RoadSegment,
        toRoad: RoadGeometry.RoadSegment,
        fromPoint: GeoPoint,
        toPoint: GeoPoint
    ): List<GeoPoint>? {
        // Check if roads are directly connected
        val directConnection = roadConnections.find { 
            (it.fromRoad == fromRoad && it.toRoad == toRoad) ||
            (it.fromRoad == toRoad && it.toRoad == fromRoad)
        }
        
        return if (directConnection != null) {
            // Directly connected - return junction point
            listOf(fromPoint, directConnection.junctionPoint, toPoint)
        } else {
            // Not directly connected - use linear interpolation
            null
        }
    }
    
    /**
     * Check if two roads are connected
     */
    fun areRoadsConnected(road1: RoadGeometry.RoadSegment, road2: RoadGeometry.RoadSegment): Boolean {
        return roadConnections.any { 
            (it.fromRoad == road1 && it.toRoad == road2) ||
            (it.fromRoad == road2 && it.toRoad == road1)
        }
    }
    
    /**
     * Get all connections for a road
     */
    fun getConnectionsForRoad(road: RoadGeometry.RoadSegment): List<RoadConnection> {
        return roadConnections.filter { it.fromRoad == road || it.toRoad == road }
    }
    
    private fun calculateDistanceInMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val earthRadius = 6371000.0 // meters
        
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val deltaLat = Math.toRadians(p2.latitude - p1.latitude)
        val deltaLon = Math.toRadians(p2.longitude - p1.longitude)
        
        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(deltaLon / 2) * sin(deltaLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
}

