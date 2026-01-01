package com.example.clinometer.tracking

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

/**
 * Road geometry data from OpenStreetMap
 * Handles fetching, caching, and snapping to real roads
 */
object RoadGeometry {
    
    private const val TAG = "RoadGeometry"
    
    // MULTI-SERVER FALLBACK: Списък с Overpass API сървъри (за надеждност!)
    private val OVERPASS_SERVERS = listOf(
        "https://overpass-api.de/api/interpreter",      // Main server (Germany)
        "https://lz4.overpass-api.de/api/interpreter",  // Backup 1 (Germany, compressed)
        "https://z.overpass-api.de/api/interpreter"     // Backup 2 (Germany, alternative)
    )
    
    // Cache for road segments
    private val roadCache = mutableMapOf<String, List<RoadSegment>>()
    
    data class RoadSegment(
        val id: String,
        val points: List<GeoPoint>,
        val roadType: String, // highway type (motorway, primary, secondary, etc.)
        val maxSpeed: Int? = null,
        val name: String? = null
    )
    
    data class RoadPoint(
        val point: GeoPoint,
        val segment: RoadSegment,
        val distanceFromStart: Double
    )
    
    /**
     * Fetch roads from OpenStreetMap in a bounding box
     * Bounding box is calculated from GPS points with padding
     */
    suspend fun fetchRoadsForRoute(
        gpsPoints: List<GeoPoint>,
        context: Context? = null
    ): List<RoadSegment> = withContext(Dispatchers.IO) {
        if (gpsPoints.isEmpty()) return@withContext emptyList()
        
        // Calculate bounding box with padding
        val latitudes = gpsPoints.map { it.latitude }
        val longitudes = gpsPoints.map { it.longitude }
        
        val minLat = latitudes.minOrNull()!! - 0.005 // ~500m padding
        val maxLat = latitudes.maxOrNull()!! + 0.005
        val minLon = longitudes.minOrNull()!! - 0.005
        val maxLon = longitudes.maxOrNull()!! + 0.005
        
        // Create cache key from bounding box
        val cacheKey = "%.4f_%.4f_%.4f_%.4f".format(minLat, maxLat, minLon, maxLon)
        
        // Check cache first
        roadCache[cacheKey]?.let {
            Log.d(TAG, "Loaded ${it.size} road segments from cache")
            return@withContext it
        }
        
        try {
            // Build Overpass QL query
            val query = buildOverpassQuery(minLat, maxLat, minLon, maxLon)
            
            Log.d(TAG, "Fetching roads from OSM: bbox($minLat, $minLon, $maxLat, $maxLon)")
            
            // Fetch from Overpass API
            val roads = fetchFromOverpass(query)
            
            // Cache results
            roadCache[cacheKey] = roads
            
            Log.d(TAG, "Fetched and cached ${roads.size} road segments from OSM")
            
            roads
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching roads from OSM: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Build Overpass API query for roads
     */
    private fun buildOverpassQuery(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): String {
        return """
            [out:json][timeout:25];
            (
              way["highway"~"motorway|trunk|primary|secondary|tertiary|residential|service|unclassified"]($minLat,$minLon,$maxLat,$maxLon);
            );
            out geom;
        """.trimIndent()
    }
    
    /**
     * Fetch road data from Overpass API (with multi-server fallback!)
     */
    private fun fetchFromOverpass(query: String): List<RoadSegment> {
        // MULTI-SERVER FALLBACK: Опитваме всички сървъри по ред
        for ((index, serverUrl) in OVERPASS_SERVERS.withIndex()) {
            val serverName = when (index) {
                0 -> "Main"
                1 -> "Backup 1"
                2 -> "Backup 2"
                else -> "Server ${index + 1}"
            }
            
            Log.d(TAG, "📡 Trying $serverName: ${serverUrl.substringAfter("//").substringBefore("/")}")
            
            try {
                val roads = fetchFromSingleServer(serverUrl, query)
                
                if (roads.isNotEmpty()) {
                    Log.d(TAG, "✅ SUCCESS from $serverName! Got ${roads.size} roads")
                    return roads
                } else {
                    Log.w(TAG, "⚠️ $serverName returned 0 roads, trying next server...")
                }
            } catch (e: java.net.SocketTimeoutException) {
                Log.w(TAG, "⚠️ $serverName TIMEOUT (internet too slow), trying next server...")
            } catch (e: java.net.UnknownHostException) {
                Log.w(TAG, "⚠️ $serverName unreachable (no internet?), trying next server...")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ $serverName failed: ${e.javaClass.simpleName}, trying next server...")
            }
        }
        
        // Всички сървъри fail-наха!
        Log.e(TAG, "❌ ALL ${OVERPASS_SERVERS.size} SERVERS FAILED! Fallback to Kalman filter.")
        return emptyList()
    }
    
    /**
     * Fetch from a single Overpass server
     */
    private fun fetchFromSingleServer(serverUrl: String, query: String): List<RoadSegment> {
        val roads = mutableListOf<RoadSegment>()
        
        val url = URL(serverUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        
        // КРИТИЧНО: Timeout-и за да не зависва на бавен интернет!
        connection.connectTimeout = 15000  // 15 секунди за свързване
        connection.readTimeout = 30000     // 30 секунди за четене
        
        // Send query
        connection.outputStream.use { os ->
            os.write("data=$query".toByteArray())
        }
        
        // Read response
        val responseCode = connection.responseCode
        
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            
            // Parse JSON response
            roads.addAll(parseOverpassResponse(response))
        } else {
            Log.w(TAG, "HTTP $responseCode from server")
        }
        
        connection.disconnect()
        return roads
    }
    
    /**
     * Parse Overpass API JSON response
     */
    private fun parseOverpassResponse(jsonString: String): List<RoadSegment> {
        val roads = mutableListOf<RoadSegment>()
        
        try {
            val json = JSONObject(jsonString)
            val elements = json.getJSONArray("elements")
            
            for (i in 0 until elements.length()) {
                val element = elements.getJSONObject(i)
                
                if (element.getString("type") == "way") {
                    val id = element.getLong("id").toString()
                    val tags = element.optJSONObject("tags")
                    val geometry = element.optJSONArray("geometry")
                    
                    if (geometry != null && geometry.length() > 1) {
                        val points = mutableListOf<GeoPoint>()
                        
                        for (j in 0 until geometry.length()) {
                            val node = geometry.getJSONObject(j)
                            val lat = node.getDouble("lat")
                            val lon = node.getDouble("lon")
                            points.add(GeoPoint(lat, lon))
                        }
                        
                        val roadType = tags?.optString("highway") ?: "unknown"
                        val maxSpeed = tags?.optString("maxspeed")?.replace("[^0-9]".toRegex(), "")?.toIntOrNull()
                        val name = tags?.optString("name")
                        
                        roads.add(
                            RoadSegment(
                                id = id,
                                points = points,
                                roadType = roadType,
                                maxSpeed = maxSpeed,
                                name = name
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Overpass response: ${e.message}", e)
        }
        
        return roads
    }
    
    /**
     * Find closest point on any road to a GPS point
     */
    fun findClosestRoadPoint(point: GeoPoint, roads: List<RoadSegment>): RoadPoint? {
        var closestPoint: RoadPoint? = null
        var minDistance = Double.MAX_VALUE
        
        for (road in roads) {
            // Check each segment of the road
            for (i in 0 until road.points.size - 1) {
                val p1 = road.points[i]
                val p2 = road.points[i + 1]
                
                val closestOnSegment = findClosestPointOnSegment(point, p1, p2)
                val distance = calculateDistanceInMeters(point, closestOnSegment)
                
                if (distance < minDistance) {
                    minDistance = distance
                    
                    // Calculate distance from start of road
                    var distanceFromStart = 0.0
                    for (j in 0 until i) {
                        distanceFromStart += calculateDistanceInMeters(road.points[j], road.points[j + 1])
                    }
                    distanceFromStart += calculateDistanceInMeters(p1, closestOnSegment)
                    
                    closestPoint = RoadPoint(
                        point = closestOnSegment,
                        segment = road,
                        distanceFromStart = distanceFromStart
                    )
                }
            }
        }
        
        return closestPoint
    }
    
    /**
     * Find closest point on a line segment
     */
    private fun findClosestPointOnSegment(point: GeoPoint, p1: GeoPoint, p2: GeoPoint): GeoPoint {
        val dx = p2.longitude - p1.longitude
        val dy = p2.latitude - p1.latitude
        
        if (dx == 0.0 && dy == 0.0) {
            return p1
        }
        
        val t = ((point.longitude - p1.longitude) * dx + (point.latitude - p1.latitude) * dy) / (dx * dx + dy * dy)
        
        return when {
            t < 0 -> p1
            t > 1 -> p2
            else -> GeoPoint(
                p1.latitude + t * dy,
                p1.longitude + t * dx
            )
        }
    }
    
    /**
     * Calculate distance between two points in meters using Haversine formula
     */
    private fun calculateDistanceInMeters(point1: GeoPoint, point2: GeoPoint): Double {
        val earthRadius = 6371000.0 // Earth radius in meters
        
        val lat1Rad = Math.toRadians(point1.latitude)
        val lat2Rad = Math.toRadians(point2.latitude)
        val deltaLatRad = Math.toRadians(point2.latitude - point1.latitude)
        val deltaLonRad = Math.toRadians(point2.longitude - point1.longitude)
        
        val a = sin(deltaLatRad / 2) * sin(deltaLatRad / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLonRad / 2) * sin(deltaLonRad / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
    
    /**
     * Clear road cache (useful for memory management)
     */
    fun clearCache() {
        roadCache.clear()
        Log.d(TAG, "Road cache cleared")
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): Pair<Int, Int> {
        val cacheSize = roadCache.size
        val totalRoads = roadCache.values.sumOf { it.size }
        return Pair(cacheSize, totalRoads)
    }
}

