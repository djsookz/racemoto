package com.example.clinometer.tracking

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.osmdroid.util.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.floor

/**
 * Tile-based caching system for OSM road data
 * 
 * Features:
 * - Caches OSM road data in 1km × 1km tiles
 * - Automatic cache cleanup (removes data older than 30 days)
 * - Maximum cache size: 100 MB
 * - Progressive coverage (only caches areas you drive in)
 * 
 * Performance:
 * - First visit to area: 2-5 seconds (fetch from Overpass API)
 * - Repeat visits: 50-100ms (read from cache)
 */
class OSMTileCache(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    
    companion object {
        private const val DATABASE_NAME = "osm_tile_cache.db"
        private const val DATABASE_VERSION = 1
        
        private const val TABLE_TILES = "tiles"
        private const val COLUMN_TILE_ID = "tile_id"
        private const val COLUMN_ROADS_JSON = "roads_json"
        private const val COLUMN_CACHED_TIME = "cached_time"
        
        private const val TILE_SIZE_DEGREES = 0.01 // ~1km at mid-latitudes
        private const val CACHE_EXPIRY_DAYS = 7 // 7 days - balance between freshness & performance
        private const val MAX_CACHE_SIZE_BYTES = 50 * 1024 * 1024 // 50 MB - reasonable limit
        private const val MIN_ROADS_PER_TILE = 1 // Minimum roads to consider cache valid
        
        @Volatile
        private var instance: OSMTileCache? = null
        
        fun getInstance(context: Context): OSMTileCache {
            return instance ?: synchronized(this) {
                instance ?: OSMTileCache(context.applicationContext).also { instance = it }
            }
        }
    }
    
    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_TILES (
                $COLUMN_TILE_ID TEXT PRIMARY KEY,
                $COLUMN_ROADS_JSON TEXT NOT NULL,
                $COLUMN_CACHED_TIME INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTable)
        
        // Create index for faster lookups
        db.execSQL("CREATE INDEX idx_cached_time ON $TABLE_TILES($COLUMN_CACHED_TIME)")
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TILES")
        onCreate(db)
    }
    
    /**
     * Get tile ID for a GeoPoint
     */
    private fun getTileId(point: GeoPoint): String {
        val tileX = floor(point.longitude / TILE_SIZE_DEGREES).toInt()
        val tileY = floor(point.latitude / TILE_SIZE_DEGREES).toInt()
        return "${tileX}_${tileY}"
    }
    
    /**
     * Get all unique tile IDs for a route
     */
    private fun getTileIdsForRoute(points: List<GeoPoint>): Set<String> {
        return points.map { getTileId(it) }.toSet()
    }
    
    /**
     * Check if we have cached data for all tiles in a route
     * Returns coverage percentage (0.0 - 1.0)
     */
    fun getCoverageForRoute(points: List<GeoPoint>): Double {
        val tileIds = getTileIdsForRoute(points)
        if (tileIds.isEmpty()) return 0.0
        
        val db = readableDatabase
        val currentTime = System.currentTimeMillis()
        val expiryTime = currentTime - (CACHE_EXPIRY_DAYS * 24 * 60 * 60 * 1000L)
        
        var validTiles = 0
        tileIds.forEach { tileId ->
            val cursor = db.query(
                TABLE_TILES,
                arrayOf(COLUMN_ROADS_JSON, COLUMN_CACHED_TIME),
                "$COLUMN_TILE_ID = ? AND $COLUMN_CACHED_TIME > ?",
                arrayOf(tileId, expiryTime.toString()),
                null, null, null
            )
            
            if (cursor.moveToFirst()) {
                // Validate cache integrity
                val roadsJson = cursor.getString(0)
                val roadsCount = try {
                    JSONArray(roadsJson).length()
                } catch (e: Exception) {
                    0
                }
                
                if (roadsCount >= MIN_ROADS_PER_TILE) {
                    validTiles++
                }
            }
            cursor.close()
        }
        
        return validTiles.toDouble() / tileIds.size
    }
    
    /**
     * Check if we have cached data for all tiles in a route
     * DEPRECATED - use getCoverageForRoute instead
     */
    @Deprecated("Use getCoverageForRoute() for better control")
    fun hasCachedDataForRoute(points: List<GeoPoint>): Boolean {
        return getCoverageForRoute(points) >= 0.95 // Conservative 95% coverage
    }
    
    /**
     * SMART: Get cached roads + identify missing tiles for API fetch
     * Returns: Pair(cachedRoads, missingTileIds)
     */
    fun getSmartCacheForRoute(points: List<GeoPoint>): Pair<List<RoadGeometry.RoadSegment>, Set<String>> {
        val tileIds = getTileIdsForRoute(points)
        val cachedRoads = mutableListOf<RoadGeometry.RoadSegment>()
        val missingTiles = mutableSetOf<String>()
        
        val db = readableDatabase
        val currentTime = System.currentTimeMillis()
        val expiryTime = currentTime - (CACHE_EXPIRY_DAYS * 24 * 60 * 60 * 1000L)
        
        tileIds.forEach { tileId ->
            val cursor = db.query(
                TABLE_TILES,
                arrayOf(COLUMN_ROADS_JSON, COLUMN_CACHED_TIME),
                "$COLUMN_TILE_ID = ? AND $COLUMN_CACHED_TIME > ?",
                arrayOf(tileId, expiryTime.toString()),
                null, null, null
            )
            
            if (cursor.moveToFirst()) {
                try {
                    val roadsJson = cursor.getString(0)
                    val jsonArray = JSONArray(roadsJson)
                    
                    if (jsonArray.length() >= MIN_ROADS_PER_TILE) {
                        // Valid cache - parse roads
                        for (i in 0 until jsonArray.length()) {
                            val roadObj = jsonArray.getJSONObject(i)
                            cachedRoads.add(parseRoadFromJson(roadObj))
                        }
                    } else {
                        // Invalid cache (too few roads) - mark as missing
                        missingTiles.add(tileId)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("OSMTileCache", "Cache corruption for tile $tileId: ${e.message}")
                    missingTiles.add(tileId)
                }
            } else {
                // No cache for this tile
                missingTiles.add(tileId)
            }
            cursor.close()
        }
        
        // Deduplicate roads by ID
        val uniqueRoads = cachedRoads.distinctBy { it.id }
        
        return Pair(uniqueRoads, missingTiles)
    }
    
    /**
     * Get cached roads for a route
     */
    fun getCachedRoadsForRoute(points: List<GeoPoint>): List<RoadGeometry.RoadSegment> {
        val tileIds = getTileIdsForRoute(points)
        val allRoads = mutableListOf<RoadGeometry.RoadSegment>()
        val db = readableDatabase
        
        tileIds.forEach { tileId ->
            val cursor = db.query(
                TABLE_TILES,
                arrayOf(COLUMN_ROADS_JSON),
                "$COLUMN_TILE_ID = ?",
                arrayOf(tileId),
                null, null, null
            )
            
            if (cursor.moveToFirst()) {
                val roadsJson = cursor.getString(0)
                val roads = deserializeRoads(roadsJson)
                allRoads.addAll(roads)
            }
            cursor.close()
        }
        
        android.util.Log.d("OSMTileCache", "📦 Loaded ${allRoads.size} cached roads from ${tileIds.size} tiles")
        return allRoads
    }
    
    /**
     * Cache roads for a route
     */
    fun cacheRoadsForRoute(points: List<GeoPoint>, roads: List<RoadGeometry.RoadSegment>) {
        val tileIds = getTileIdsForRoute(points)
        val db = writableDatabase
        val currentTime = System.currentTimeMillis()
        
        // Group roads by tile
        val roadsByTile = mutableMapOf<String, MutableList<RoadGeometry.RoadSegment>>()
        
        roads.forEach { road ->
            // Assign road to all tiles it intersects
            road.points.forEach { point ->
                val tileId = getTileId(point)
                if (tileId in tileIds) {
                    roadsByTile.getOrPut(tileId) { mutableListOf() }.add(road)
                }
            }
        }
        
        // Save to database
        db.beginTransaction()
        try {
            roadsByTile.forEach { (tileId, tileRoads) ->
                val roadsJson = serializeRoads(tileRoads.distinct())
                
                db.execSQL(
                    "INSERT OR REPLACE INTO $TABLE_TILES ($COLUMN_TILE_ID, $COLUMN_ROADS_JSON, $COLUMN_CACHED_TIME) VALUES (?, ?, ?)",
                    arrayOf(tileId, roadsJson, currentTime)
                )
            }
            db.setTransactionSuccessful()
            android.util.Log.d("OSMTileCache", "💾 Cached ${roads.size} roads across ${roadsByTile.size} tiles")
        } finally {
            db.endTransaction()
        }
        
        // Cleanup old data
        cleanupOldCache()
    }
    
    /**
     * Serialize roads to JSON
     */
    private fun serializeRoads(roads: List<RoadGeometry.RoadSegment>): String {
        val jsonArray = JSONArray()
        
        roads.forEach { road ->
            val roadJson = JSONObject().apply {
                put("id", road.id)
                put("name", road.name ?: "")
                put("type", road.roadType)
                put("maxSpeed", road.maxSpeed ?: 0)
                
                val pointsArray = JSONArray()
                road.points.forEach { point ->
                    pointsArray.put(JSONObject().apply {
                        put("lat", point.latitude)
                        put("lon", point.longitude)
                    })
                }
                put("points", pointsArray)
            }
            jsonArray.put(roadJson)
        }
        
        return jsonArray.toString()
    }
    
    /**
     * Parse single road from JSON object
     */
    private fun parseRoadFromJson(roadJson: JSONObject): RoadGeometry.RoadSegment {
        val points = mutableListOf<GeoPoint>()
        val pointsArray = roadJson.getJSONArray("points")
        for (j in 0 until pointsArray.length()) {
            val pointJson = pointsArray.getJSONObject(j)
            points.add(GeoPoint(
                pointJson.getDouble("lat"),
                pointJson.getDouble("lon")
            ))
        }
        
        return RoadGeometry.RoadSegment(
            id = roadJson.getString("id"),
            points = points,
            roadType = roadJson.getString("type"),
            maxSpeed = roadJson.optInt("maxSpeed", 0).takeIf { it > 0 },
            name = roadJson.optString("name", null)
        )
    }
    
    /**
     * Deserialize roads from JSON
     */
    private fun deserializeRoads(json: String): List<RoadGeometry.RoadSegment> {
        val roads = mutableListOf<RoadGeometry.RoadSegment>()
        
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val roadJson = jsonArray.getJSONObject(i)
                
                val points = mutableListOf<GeoPoint>()
                val pointsArray = roadJson.getJSONArray("points")
                for (j in 0 until pointsArray.length()) {
                    val pointJson = pointsArray.getJSONObject(j)
                    points.add(GeoPoint(
                        pointJson.getDouble("lat"),
                        pointJson.getDouble("lon")
                    ))
                }
                
                roads.add(RoadGeometry.RoadSegment(
                    id = roadJson.getString("id"),
                    name = roadJson.optString("name", null),
                    roadType = roadJson.getString("type"),
                    maxSpeed = roadJson.optInt("maxSpeed", 0).takeIf { it > 0 },
                    points = points
                ))
            }
        } catch (e: Exception) {
            android.util.Log.e("OSMTileCache", "Error deserializing roads: ${e.message}")
        }
        
        return roads
    }
    
    /**
     * Cleanup old cached data (older than 30 days)
     */
    private fun cleanupOldCache() {
        val db = writableDatabase
        val expiryTime = System.currentTimeMillis() - (CACHE_EXPIRY_DAYS * 24 * 60 * 60 * 1000L)
        
        val deleted = db.delete(TABLE_TILES, "$COLUMN_CACHED_TIME < ?", arrayOf(expiryTime.toString()))
        
        if (deleted > 0) {
            android.util.Log.d("OSMTileCache", "🧹 Cleaned up $deleted old cache entries")
        }
        
        // Check total size and cleanup if needed
        enforceMaxCacheSize()
    }
    
    /**
     * Enforce maximum cache size
     */
    private fun enforceMaxCacheSize() {
        val db = readableDatabase
        
        // Get total size (approximate)
        val cursor = db.rawQuery("SELECT SUM(LENGTH($COLUMN_ROADS_JSON)) FROM $TABLE_TILES", null)
        var totalSize = 0L
        if (cursor.moveToFirst()) {
            totalSize = cursor.getLong(0)
        }
        cursor.close()
        
        // If over limit, delete oldest entries
        if (totalSize > MAX_CACHE_SIZE_BYTES) {
            val toDelete = (totalSize - MAX_CACHE_SIZE_BYTES) * 1.2 // Delete 20% more to avoid frequent cleanup
            
            android.util.Log.d("OSMTileCache", "⚠️ Cache size ${totalSize / 1024 / 1024}MB exceeds limit, cleaning up...")
            
            val deleteDb = writableDatabase
            deleteDb.execSQL("""
                DELETE FROM $TABLE_TILES 
                WHERE $COLUMN_TILE_ID IN (
                    SELECT $COLUMN_TILE_ID FROM $TABLE_TILES 
                    ORDER BY $COLUMN_CACHED_TIME ASC 
                    LIMIT 10
                )
            """)
        }
    }
    
    /**
     * Clear entire cache (for debugging or user preference)
     */
    fun clearCache() {
        val db = writableDatabase
        db.delete(TABLE_TILES, null, null)
        android.util.Log.d("OSMTileCache", "🗑️ Entire cache cleared")
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        val db = readableDatabase
        
        val cursor = db.rawQuery("""
            SELECT 
                COUNT(*) as tile_count,
                SUM(LENGTH($COLUMN_ROADS_JSON)) as total_size
            FROM $TABLE_TILES
        """, null)
        
        var tileCount = 0
        var totalSize = 0L
        
        if (cursor.moveToFirst()) {
            tileCount = cursor.getInt(0)
            totalSize = cursor.getLong(1)
        }
        cursor.close()
        
        return CacheStats(tileCount, totalSize)
    }
    
    data class CacheStats(
        val tileCount: Int,
        val totalSizeBytes: Long
    ) {
        val totalSizeMB: Float
            get() = totalSizeBytes / 1024f / 1024f
    }
}

