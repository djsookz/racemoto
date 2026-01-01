package com.example.clinometer.tracking

import org.osmdroid.util.GeoPoint
import kotlin.math.floor

/**
 * Spatial Grid Index for fast road lookups
 * Divides geographic space into 100m x 100m cells
 * Provides O(1) lookup instead of O(N) linear search
 */
class SpatialGridIndex(private val cellSizeMeters: Double = 100.0) {
    
    // Grid structure: Map<cellKey, List<RoadSegmentInCell>>
    private val grid = mutableMapOf<String, MutableList<RoadSegmentInCell>>()
    
    data class RoadSegmentInCell(
        val road: RoadGeometry.RoadSegment,
        val segmentIndex: Int, // Which segment of the road (between points i and i+1)
        val p1: GeoPoint,
        val p2: GeoPoint
    )
    
    companion object {
        // 1 degree latitude = ~111,320 meters
        private const val METERS_PER_DEGREE_LAT = 111320.0
        // 1 degree longitude varies by latitude, but we use approximation at 42° (Bulgaria)
        private const val METERS_PER_DEGREE_LON = 82780.0 // cos(42°) × 111,320
    }
    
    /**
     * Build index from road segments
     */
    fun buildIndex(roads: List<RoadGeometry.RoadSegment>) {
        grid.clear()
        
        for (road in roads) {
            if (road.points.size < 2) continue
            
            // For each line segment in the road
            for (i in 0 until road.points.size - 1) {
                val p1 = road.points[i]
                val p2 = road.points[i + 1]
                
                // Find all cells this segment passes through
                val cells = getCellsForSegment(p1, p2)
                
                val segment = RoadSegmentInCell(road, i, p1, p2)
                
                for (cellKey in cells) {
                    grid.getOrPut(cellKey) { mutableListOf() }.add(segment)
                }
            }
        }
        
        android.util.Log.d("SpatialGridIndex", "Built index: ${grid.size} cells, ${roads.size} roads")
    }
    
    /**
     * Get nearby road segments for a GPS point
     * Returns segments within ~200m radius (2x2 cells + current cell)
     */
    fun getNearbySegments(point: GeoPoint, radiusCells: Int = 1): List<RoadSegmentInCell> {
        val cellKey = getCellKey(point)
        val (cellLat, cellLon) = parseCellKey(cellKey)
        
        val nearbySegments = mutableListOf<RoadSegmentInCell>()
        
        // Check current cell + surrounding cells
        for (dLat in -radiusCells..radiusCells) {
            for (dLon in -radiusCells..radiusCells) {
                val neighborKey = "${cellLat + dLat}_${cellLon + dLon}"
                grid[neighborKey]?.let { segments ->
                    nearbySegments.addAll(segments)
                }
            }
        }
        
        return nearbySegments
    }
    
    /**
     * Get cell key for a point
     */
    private fun getCellKey(point: GeoPoint): String {
        val cellLat = floor(point.latitude / (cellSizeMeters / METERS_PER_DEGREE_LAT)).toInt()
        val cellLon = floor(point.longitude / (cellSizeMeters / METERS_PER_DEGREE_LON)).toInt()
        return "${cellLat}_${cellLon}"
    }
    
    private fun parseCellKey(key: String): Pair<Int, Int> {
        val parts = key.split("_")
        return Pair(parts[0].toInt(), parts[1].toInt())
    }
    
    /**
     * Get all cells a line segment passes through (Bresenham-like algorithm)
     */
    private fun getCellsForSegment(p1: GeoPoint, p2: GeoPoint): Set<String> {
        val cells = mutableSetOf<String>()
        
        val cell1 = getCellKey(p1)
        val cell2 = getCellKey(p2)
        
        cells.add(cell1)
        cells.add(cell2)
        
        // If segment spans multiple cells, add intermediate cells
        val (lat1, lon1) = parseCellKey(cell1)
        val (lat2, lon2) = parseCellKey(cell2)
        
        val dLat = kotlin.math.abs(lat2 - lat1)
        val dLon = kotlin.math.abs(lon2 - lon1)
        
        // If segment spans more than 1 cell, add all cells in between
        if (dLat > 1 || dLon > 1) {
            val steps = maxOf(dLat, dLon)
            for (i in 1 until steps) {
                val t = i.toDouble() / steps
                val interpLat = p1.latitude + (p2.latitude - p1.latitude) * t
                val interpLon = p1.longitude + (p2.longitude - p1.longitude) * t
                cells.add(getCellKey(GeoPoint(interpLat, interpLon)))
            }
        }
        
        return cells
    }
    
    /**
     * Get statistics about the index
     */
    fun getStats(): String {
        val totalSegments = grid.values.sumOf { it.size }
        val avgSegmentsPerCell = if (grid.isNotEmpty()) totalSegments.toFloat() / grid.size else 0f
        return "Cells: ${grid.size}, Total segments: $totalSegments, Avg per cell: ${"%.1f".format(avgSegmentsPerCell)}"
    }
}

