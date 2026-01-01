package com.example.clinometer.tracking

import android.content.Context
import org.osmdroid.util.GeoPoint
import kotlin.math.*

/**
 * HMM (Hidden Markov Model) Map Matching
 * Industry-standard approach for GPS map matching
 * 
 * Algorithm:
 * 1. Generate road candidates for each GPS point
 * 2. Calculate emission probabilities (GPS accuracy)
 * 3. Calculate transition probabilities (routing between candidates)
 * 4. Use Viterbi algorithm to find most probable path
 */
object HMMMapMatcher {
    
    private const val GPS_SIGMA = 15.0 // GPS accuracy in meters
    // BETA and other parameters are now dynamic based on speed!
    private const val MAX_CANDIDATES = 10 // Max road candidates per GPS point
    
    // ===== CACHING FOR PERFORMANCE =====
    private var cachedRoadGraph: RoadNetworkGraph? = null
    private var cachedSpatialIndex: SpatialGridIndex? = null
    private var cachedRoadIds: Set<String>? = null
    
    /**
     * Dynamic parameters based on average speed
     */
    private data class SpeedAdjustedParams(
        val beta: Double,              // Transition weight
        val searchRadius: Double,      // Candidate search radius
        val maxCandidates: Int         // Max candidates per point
    )
    
    /**
     * Calculate optimal parameters based on average speed
     */
    private fun calculateSpeedAdjustedParams(speedData: List<Float>): SpeedAdjustedParams {
        val avgSpeed = if (speedData.isNotEmpty()) speedData.average() else 0.0
        
        android.util.Log.d("HMMMapMatcher", "📊 Average speed: ${avgSpeed.toInt()} km/h")
        
        return when {
            avgSpeed > 200 -> {
                android.util.Log.d("HMMMapMatcher", "⚡ Very high speed mode: BETA=0.15, RADIUS=250m")
                SpeedAdjustedParams(beta = 0.15, searchRadius = 250.0, maxCandidates = 8)
            }
            avgSpeed > 150 -> {
                android.util.Log.d("HMMMapMatcher", "⚡ High speed mode: BETA=0.25, RADIUS=200m")
                SpeedAdjustedParams(beta = 0.25, searchRadius = 200.0, maxCandidates = 10)
            }
            avgSpeed > 100 -> {
                android.util.Log.d("HMMMapMatcher", "🏍️ Fast mode: BETA=0.35, RADIUS=200m")
                SpeedAdjustedParams(beta = 0.35, searchRadius = 200.0, maxCandidates = 10)
            }
            avgSpeed > 60 -> {
                android.util.Log.d("HMMMapMatcher", "🚗 Normal mode: BETA=0.45, RADIUS=200m")
                SpeedAdjustedParams(beta = 0.45, searchRadius = 200.0, maxCandidates = 10)
            }
            else -> {
                android.util.Log.d("HMMMapMatcher", "🐌 Slow mode: BETA=0.5, RADIUS=200m")
                SpeedAdjustedParams(beta = 0.5, searchRadius = 200.0, maxCandidates = 10)
            }
        }
    }
    
    /**
     * Candidate: possible road segment for a GPS point
     */
    data class Candidate(
        val gpsIndex: Int,
        val roadSegment: RoadGeometry.RoadSegment,
        val segmentIndex: Int,
        val projectedPoint: GeoPoint,
        val distance: Double, // meters from GPS to road
        val emissionProbability: Double
    )
    
    /**
     * Viterbi state for dynamic programming
     */
    private data class ViterbiState(
        val candidate: Candidate,
        val probability: Double,
        val previousCandidate: Candidate?
    )

    private data class PathContext(
        val roadId: String,
        val duration: Int,
        val lastCandidate: Candidate
    )

    private data class ChunkResult(
        val processedPoints: List<SmartMapMatcher.ProcessedPoint>,
        val finalContext: PathContext?
    )

    private data class ViterbiResult(
        val path: List<Candidate>,
        val finalContext: PathContext?
    )
    
    /**
     * Process GPS points with HMM map matching
     */
    fun processWithHMM(
        gpsPoints: List<GeoPoint>,
        speedData: List<Float>,
        context: Context?
    ): List<SmartMapMatcher.ProcessedPoint> {
        if (gpsPoints.isEmpty()) return emptyList()
        
        android.util.Log.d("HMMMapMatcher", "")
        android.util.Log.d("HMMMapMatcher", "═══════════════════════════════════════")
        android.util.Log.d("HMMMapMatcher", "🧠 HMM MAP MATCHING (CHUNKED)")
        android.util.Log.d("HMMMapMatcher", "═══════════════════════════════════════")
        android.util.Log.d("HMMMapMatcher", "Processing ${gpsPoints.size} GPS points")
        
        val startTime = System.currentTimeMillis()
        
        // ===== CHUNKED PROCESSING FOR LARGE SESSIONS =====
        val CHUNK_SIZE = 150 // Optimal chunk size
        val needsChunking = gpsPoints.size > CHUNK_SIZE
        
        if (needsChunking) {
            android.util.Log.d("HMMMapMatcher", "📦 Large session detected - using CHUNKED processing!")
            return processInChunks(gpsPoints, speedData, context, startTime)
        }
        
        // ===== SMALL SESSION - PROCESS NORMALLY =====
        android.util.Log.d("HMMMapMatcher", "⚡ Small session - processing in one go")
        
        // ===== PHASE 0: CALCULATE SPEED-ADJUSTED PARAMETERS =====
        val params = calculateSpeedAdjustedParams(speedData)
        
        // ===== PHASE 1: FETCH OSM ROADS =====
        android.util.Log.d("HMMMapMatcher", "📡 PHASE 1: Fetching OSM roads...")
        val osmRoads = fetchOSMRoads(gpsPoints, context)
        
        if (osmRoads.isEmpty()) {
            android.util.Log.w("HMMMapMatcher", "⚠️ No roads available - fallback to Kalman only")
            return fallbackKalmanOnly(gpsPoints, speedData)
        }
        
        // Check if we can reuse cached structures
        val currentRoadIds = osmRoads.map { it.id }.toSet()
        val canReuseCache = cachedRoadIds == currentRoadIds
        
        // ===== PHASE 2: BUILD SPATIAL INDEX =====
        android.util.Log.d("HMMMapMatcher", "🗺️ PHASE 2: Building spatial index...")
        val spatialIndex = if (canReuseCache && cachedSpatialIndex != null) {
            android.util.Log.d("HMMMapMatcher", "💾 Reusing cached spatial index (0ms!)")
            cachedSpatialIndex!!
        } else {
            val index = SpatialGridIndex(cellSizeMeters = 100.0)
            index.buildIndex(osmRoads)
            cachedSpatialIndex = index
            cachedRoadIds = currentRoadIds
            android.util.Log.d("HMMMapMatcher", "✅ Built new spatial index")
            index
        }
        
        // ===== PHASE 3: SKIP ROAD GRAPH FOR SPEED! =====
        // android.util.Log.d("HMMMapMatcher", "🛣️ PHASE 3: Building road network graph...")
        // val roadGraph = if (canReuseCache && cachedRoadGraph != null) {
        //     android.util.Log.d("HMMMapMatcher", "💾 Reusing cached road graph (0ms!)")
        //     cachedRoadGraph!!
        // } else {
        //     val graph = RoadNetworkGraph()
        //     graph.buildGraph(osmRoads)
        //     cachedRoadGraph = graph
        //     android.util.Log.d("HMMMapMatcher", "✅ Built new road graph")
        //     graph
        // }
        
        // ===== PHASE 3: GENERATE CANDIDATES =====
        android.util.Log.d("HMMMapMatcher", "📍 PHASE 3: Generating candidates...")
        val allCandidates = generateCandidatesForAll(gpsPoints, spatialIndex, params)
        
        val totalCandidates = allCandidates.sumOf { it.size }
        val avgCandidates = if (gpsPoints.isNotEmpty()) totalCandidates / gpsPoints.size else 0
        android.util.Log.d("HMMMapMatcher", "✅ Generated $totalCandidates candidates (avg $avgCandidates per point)")
        
        // ===== PHASE 4: VITERBI ALGORITHM (БЕЗ GRAPH!) =====
        android.util.Log.d("HMMMapMatcher", "🎯 PHASE 4: Running Viterbi (direct distance)...")
        val viterbiResult = viterbiAlgorithmSimplified(allCandidates, gpsPoints, params, previousContext = null)
        
        // ===== PHASE 5: CONVERT TO PROCESSED POINTS =====
        android.util.Log.d("HMMMapMatcher", "🔄 PHASE 5: Converting to processed points...")
        val processedPoints = convertToProcessedPoints(viterbiResult.path, gpsPoints, speedData)
        
        val elapsed = System.currentTimeMillis() - startTime
        
        android.util.Log.d("HMMMapMatcher", "")
        android.util.Log.d("HMMMapMatcher", "═══════════════════════════════════════")
        android.util.Log.d("HMMMapMatcher", "✅ HMM MATCHING COMPLETE!")
        android.util.Log.d("HMMMapMatcher", "═══════════════════════════════════════")
        android.util.Log.d("HMMMapMatcher", "   Total points: ${processedPoints.size}")
        android.util.Log.d("HMMMapMatcher", "   Roads used: ${osmRoads.size}")
        android.util.Log.d("HMMMapMatcher", "   Avg candidates: $avgCandidates per point")
        android.util.Log.d("HMMMapMatcher", "   ⚡ Total time: ${elapsed}ms")
        android.util.Log.d("HMMMapMatcher", "═══════════════════════════════════════")
        android.util.Log.d("HMMMapMatcher", "")
        
        return processedPoints
    }
    
    /**
     * Process large sessions in chunks to avoid memory/performance issues
     */
    private fun processInChunks(
        gpsPoints: List<GeoPoint>,
        speedData: List<Float>,
        context: Context?,
        overallStartTime: Long
    ): List<SmartMapMatcher.ProcessedPoint> {
        val CHUNK_SIZE = 150
        val OVERLAP = 10 // Overlap between chunks for smooth transitions
        
        val totalChunks = (gpsPoints.size + CHUNK_SIZE - 1) / CHUNK_SIZE
        android.util.Log.d("HMMMapMatcher", "   Splitting into $totalChunks chunks (size=$CHUNK_SIZE, overlap=$OVERLAP)")
        
        val allProcessed = mutableListOf<SmartMapMatcher.ProcessedPoint>()
        var previousContext: PathContext? = null
        
        for (chunkIndex in 0 until totalChunks) {
            val startIdx = (chunkIndex * CHUNK_SIZE).coerceAtLeast(0)
            val endIdx = minOf(startIdx + CHUNK_SIZE + OVERLAP, gpsPoints.size)
            
            val chunkGPS = gpsPoints.subList(startIdx, endIdx)
            val chunkSpeed = speedData.subList(startIdx, minOf(endIdx, speedData.size))
            
            android.util.Log.d("HMMMapMatcher", "")
            android.util.Log.d("HMMMapMatcher", "📦 ═══ CHUNK ${chunkIndex + 1}/$totalChunks ═══")
            android.util.Log.d("HMMMapMatcher", "   Processing points $startIdx-$endIdx (${chunkGPS.size} points)")
            
            val chunkStartTime = System.currentTimeMillis()
            
            // Process this chunk
            val chunkResult = try {
                processChunk(chunkGPS, chunkSpeed, context, previousContext)
            } catch (e: Exception) {
                android.util.Log.e("HMMMapMatcher", "❌ Chunk ${chunkIndex + 1} FAILED: ${e.message}")
                // Fallback to raw GPS for this chunk
                ChunkResult(
                    processedPoints = chunkGPS.mapIndexed { idx, gp ->
                        SmartMapMatcher.ProcessedPoint(
                            geoPoint = gp,
                            confidence = 0.3f,
                            speed = chunkSpeed.getOrNull(idx) ?: 0f,
                            bearing = 0f,
                            acceleration = 0f,
                            isSnapped = false,
                            originalPoint = gp
                        )
                    },
                    finalContext = previousContext
                )
            }
            
            val chunkTime = (System.currentTimeMillis() - chunkStartTime) / 1000.0
            val processedPoints = chunkResult.processedPoints
            val snapped = processedPoints.count { it.isSnapped }
            val snapPercent = if (processedPoints.isNotEmpty()) (snapped * 100.0 / processedPoints.size).toInt() else 0
            
            android.util.Log.d("HMMMapMatcher", "   ✅ Chunk ${chunkIndex + 1} done in ${chunkTime}s - ${snapped}/${processedPoints.size} snapped ($snapPercent%)")
            
            // Add results (removing overlap from previous chunk)
            val effectiveResults = if (chunkIndex > 0) {
                // Skip first OVERLAP points (they were in previous chunk)
                processedPoints.drop(OVERLAP)
            } else {
                processedPoints
            }
            
            allProcessed.addAll(effectiveResults)
            previousContext = chunkResult.finalContext
        }
        
        val totalElapsed = (System.currentTimeMillis() - overallStartTime) / 1000.0
        val totalSnapped = allProcessed.count { it.isSnapped }
        val totalSnapPercent = (totalSnapped * 100.0 / allProcessed.size).toInt()
        
        android.util.Log.d("HMMMapMatcher", "")
        android.util.Log.d("HMMMapMatcher", "═══════════════════════════════════════")
        android.util.Log.d("HMMMapMatcher", "✅ CHUNKED HMM COMPLETE!")
        android.util.Log.d("HMMMapMatcher", "═══════════════════════════════════════")
        android.util.Log.d("HMMMapMatcher", "   Total chunks: $totalChunks")
        android.util.Log.d("HMMMapMatcher", "   Total points: ${allProcessed.size}")
        android.util.Log.d("HMMMapMatcher", "   Snapped: $totalSnapped ($totalSnapPercent%)")
        android.util.Log.d("HMMMapMatcher", "   ⚡ Total time: ${totalElapsed}s")
        android.util.Log.d("HMMMapMatcher", "═══════════════════════════════════════")
        android.util.Log.d("HMMMapMatcher", "")
        
        return allProcessed
    }
    
    /**
     * Process a single chunk of GPS points
     */
    private fun processChunk(
        chunkGPS: List<GeoPoint>,
        chunkSpeed: List<Float>,
        context: Context?,
        previousContext: PathContext?
    ): ChunkResult {
        // Calculate params for this chunk
        val params = calculateSpeedAdjustedParams(chunkSpeed)
        
        // Fetch roads for THIS chunk only (small bbox!)
        android.util.Log.d("HMMMapMatcher", "   📡 Fetching roads for chunk...")
        val osmRoads = fetchOSMRoads(chunkGPS, context)
        
        if (osmRoads.isEmpty()) {
            android.util.Log.w("HMMMapMatcher", "   ⚠️ No roads - attempting OSRM navigation fallback")
            val navigationPoints = OsrmRouteHelper.generateNavigatedProcessedPoints(chunkGPS, chunkSpeed)
            if (navigationPoints != null) {
                android.util.Log.d("HMMMapMatcher", "   ✅ OSRM navigation applied (no roads)")
                return ChunkResult(navigationPoints, previousContext)
            }

            android.util.Log.w("HMMMapMatcher", "   ⚠️ OSRM navigation failed - using raw GPS")
            val fallbackPoints = chunkGPS.mapIndexed { idx, gp ->
                SmartMapMatcher.ProcessedPoint(
                    geoPoint = gp,
                    confidence = 0.5f,
                    speed = chunkSpeed.getOrNull(idx) ?: 0f,
                    bearing = 0f,
                    acceleration = 0f,
                    isSnapped = false,
                    originalPoint = gp
                )
            }
            return ChunkResult(fallbackPoints, previousContext)
        }
        
        android.util.Log.d("HMMMapMatcher", "   ✅ Fetched ${osmRoads.size} roads")
        
        // Limit roads to prevent memory issues
        val limitedRoads = if (osmRoads.size > 1500) {
            android.util.Log.d("HMMMapMatcher", "   ⚠️ Too many roads (${osmRoads.size}) - filtering closest 1500...")
            filterClosestRoads(osmRoads, chunkGPS, 1500)
        } else {
            osmRoads
        }
        
        if (limitedRoads.size != osmRoads.size) {
            android.util.Log.d("HMMMapMatcher", "   ✅ Filtered to ${limitedRoads.size} roads")
        }
        
        // Build spatial index
        val spatialIndex = SpatialGridIndex(cellSizeMeters = 100.0)
        spatialIndex.buildIndex(limitedRoads)
        android.util.Log.d("HMMMapMatcher", "   ✅ Built spatial index")
        
        // === SKIP ROAD GRAPH BUILDING FOR SPEED! ===
        // Build road graph
        // val roadGraph = RoadNetworkGraph()
        // roadGraph.buildGraph(limitedRoads)
        // android.util.Log.d("HMMMapMatcher", "   ✅ Built graph with ${limitedRoads.size} roads")
        
        // Generate candidates
        val allCandidates = generateCandidatesForAll(chunkGPS, spatialIndex, params)
        
        // Run Viterbi WITHOUT road graph (direct distance!)
        val viterbiResult = viterbiAlgorithmSimplified(allCandidates, chunkGPS, params, previousContext)
        
        // Convert to processed points
        val processedPoints = convertToProcessedPoints(viterbiResult.path, chunkGPS, chunkSpeed)

        val shouldInvokeOsrm = shouldInvokeOsrmNavigation(processedPoints, chunkGPS, chunkSpeed, params)
        if (shouldInvokeOsrm) {
            val navigationPoints = OsrmRouteHelper.generateNavigatedProcessedPoints(chunkGPS, chunkSpeed)
            if (navigationPoints != null && navigationPoints.size == chunkGPS.size) {
                android.util.Log.d("HMMMapMatcher", "   ✅ OSRM navigation applied (post-HMM)")
                return ChunkResult(navigationPoints, viterbiResult.finalContext)
            }
        }

        if (processedPoints.size != chunkGPS.size) {
            android.util.Log.w(
                "HMMMapMatcher",
                "   ⚠️ Processed count mismatch (${processedPoints.size}/${chunkGPS.size}) - invoking OSRM fallback"
            )
            val fallbackNavigation = OsrmRouteHelper.generateNavigatedProcessedPoints(chunkGPS, chunkSpeed)
            if (fallbackNavigation != null) {
                android.util.Log.d("HMMMapMatcher", "   ✅ OSRM navigation successful after mismatch")
                return ChunkResult(fallbackNavigation, viterbiResult.finalContext)
            }

            android.util.Log.w("HMMMapMatcher", "   ⚠️ OSRM fallback failed - using Kalman fallback")
            val fallbackPoints = fallbackKalmanOnly(chunkGPS, chunkSpeed)
            return ChunkResult(fallbackPoints, viterbiResult.finalContext)
        }
        
        return ChunkResult(processedPoints, viterbiResult.finalContext)
    }
    
    /**
     * Filter roads to keep only the closest N roads to GPS points
     */
    private fun filterClosestRoads(
        roads: List<RoadGeometry.RoadSegment>,
        gpsPoints: List<GeoPoint>,
        maxRoads: Int
    ): List<RoadGeometry.RoadSegment> {
        // Calculate min distance from each road to any GPS point
        val roadDistances = roads.map { road ->
            val minDist = gpsPoints.minOf { gpsPoint ->
                road.points.minOf { roadPoint ->
                    calculateDistanceInMeters(gpsPoint, roadPoint)
                }
            }
            road to minDist
        }
        
        // Sort by distance and take closest N
        return roadDistances
            .sortedBy { it.second }
            .take(maxRoads)
            .map { it.first }
    }
    
    /**
     * Generate candidates for all GPS points
     */
    private fun generateCandidatesForAll(
        gpsPoints: List<GeoPoint>,
        spatialIndex: SpatialGridIndex,
        params: SpeedAdjustedParams
    ): List<List<Candidate>> {
        return gpsPoints.mapIndexed { index, gpsPoint ->
            // Calculate GPS bearing from nearby points (for bearing filter)
            val gpsBearing = calculateGPSBearing(gpsPoints, index)
            generateCandidatesForPoint(index, gpsPoint, spatialIndex, params, gpsBearing)
        }
    }
    
    private data class ChunkQualityMetrics(
        val averageDistance: Double,
        val maxDistance: Double,
        val strongOutlierCount: Int
    )

    private fun shouldInvokeOsrmNavigation(
        processedPoints: List<SmartMapMatcher.ProcessedPoint>,
        originalPoints: List<GeoPoint>,
        speedData: List<Float>,
        params: SpeedAdjustedParams
    ): Boolean {
        if (processedPoints.isEmpty() || processedPoints.size != originalPoints.size) {
            android.util.Log.d("HMMMapMatcher", "   ⚠️ OSRM forced: processed/raw size mismatch (${processedPoints.size}/${originalPoints.size})")
            return true
        }

        val metrics = calculateChunkQualityMetrics(processedPoints, originalPoints)
        val avgSpeed = if (speedData.isNotEmpty()) speedData.average() else 0.0
        val highSpeed = avgSpeed > 110.0

        val maxAllowed = if (highSpeed) 12.0 else 18.0
        val avgAllowed = if (highSpeed) 5.0 else 7.5
        val outlierLimit = max(3, (processedPoints.size * 0.05).roundToInt())

        val shouldInvoke = metrics.maxDistance > maxAllowed ||
            metrics.averageDistance > avgAllowed ||
            metrics.strongOutlierCount >= outlierLimit

        if (shouldInvoke) {
            android.util.Log.d(
                "HMMMapMatcher",
                "   ⚠️ OSRM triggered (avgDist=${"%.1f".format(metrics.averageDistance)}m, maxDist=${"%.1f".format(metrics.maxDistance)}m, outliers=${metrics.strongOutlierCount}, avgSpeed=${avgSpeed.toInt()} km/h)"
            )
        } else {
            android.util.Log.d(
                "HMMMapMatcher",
                "   ✅ HMM quality acceptable (avgDist=${"%.1f".format(metrics.averageDistance)}m, maxDist=${"%.1f".format(metrics.maxDistance)}m) – skipping OSRM"
            )
        }

        return shouldInvoke
    }

    private fun calculateChunkQualityMetrics(
        processedPoints: List<SmartMapMatcher.ProcessedPoint>,
        originalPoints: List<GeoPoint>
    ): ChunkQualityMetrics {
        var sumDistance = 0.0
        var maxDistance = 0.0
        var strongOutliers = 0

        for (i in processedPoints.indices) {
            val snapped = processedPoints[i].geoPoint
            val original = originalPoints[i]
            val distance = calculateDistanceInMeters(snapped, original)
            sumDistance += distance
            if (distance > maxDistance) {
                maxDistance = distance
            }
            if (distance > 15.0) {
                strongOutliers++
            }
        }

        val averageDistance = if (processedPoints.isNotEmpty()) sumDistance / processedPoints.size else 0.0
        return ChunkQualityMetrics(
            averageDistance = averageDistance,
            maxDistance = maxDistance,
            strongOutlierCount = strongOutliers
        )
    }

    /**
     * Calculate GPS bearing (smoothed over 5-10 points for accuracy)
     */
    private fun calculateGPSBearing(gpsPoints: List<GeoPoint>, currentIndex: Int): Double? {
        // Need at least 5 points ahead for reliable bearing
        val lookAhead = 7
        if (currentIndex + lookAhead >= gpsPoints.size) return null
        
        val currentPoint = gpsPoints[currentIndex]
        val futurePoint = gpsPoints[currentIndex + lookAhead]
        
        return calculateBearing(currentPoint, futurePoint)
    }
    
    /**
     * Generate candidates for a single GPS point
     */
    private fun generateCandidatesForPoint(
        gpsIndex: Int,
        gpsPoint: GeoPoint,
        spatialIndex: SpatialGridIndex,
        params: SpeedAdjustedParams,
        gpsBearing: Double? = null  // GPS movement direction (optional)
    ): List<Candidate> {
        val nearbySegments = spatialIndex.getNearbySegments(gpsPoint, radiusCells = 1)
        
        val candidates = mutableListOf<Candidate>()
        
        for (segment in nearbySegments) {
            // Project GPS point onto road segment
            val projectedPoint = projectPointOnLineSegment(gpsPoint, segment.p1, segment.p2)
            val distance = calculateDistanceInMeters(gpsPoint, projectedPoint)
            
            // Only consider candidates within DYNAMIC search radius
            if (distance <= params.searchRadius) {
                
                // === BEARING FILTER (soft penalty for opposite direction) ===
                var bearingPenalty = 1.0
                if (gpsBearing != null) {
                    val roadBearing = calculateBearing(segment.p1, segment.p2)
                    val bearingDiff = normalizeBearingDiff(gpsBearing - roadBearing)
                    
                    bearingPenalty = when {
                        bearingDiff < 60.0 -> 1.0      // Same direction ✅
                        bearingDiff < 120.0 -> 0.6     // Perpendicular (кръстовище)
                        else -> 0.2                     // Opposite direction ⚠️ (почти блокиран!)
                    }
                }
                
                // Calculate emission probability with bearing penalty
                val baseEmissionProb = calculateEmissionProbability(distance)
                val emissionProb = baseEmissionProb * bearingPenalty
                
                candidates.add(
                    Candidate(
                        gpsIndex = gpsIndex,
                        roadSegment = segment.road,
                        segmentIndex = segment.segmentIndex,
                        projectedPoint = projectedPoint,
                        distance = distance,
                        emissionProbability = emissionProb
                    )
                )
            }
        }
        
        // Sort by emission probability and keep top N (dynamic based on speed)
        return candidates
            .sortedByDescending { it.emissionProbability }
            .take(params.maxCandidates)
    }
    
    /**
     * Calculate emission probability: P(GPS | Road)
     * Based on distance from GPS to road segment
     */
    private fun calculateEmissionProbability(distance: Double): Double {
        // Gaussian distribution: exp(-distance² / (2 * σ²))
        return exp(-(distance * distance) / (2 * GPS_SIGMA * GPS_SIGMA))
    }
    
    /**
     * Calculate transition probability: P(Road_j | Road_i)
     * Based on routing distance between road segments
     */
    private fun calculateTransitionProbability(
        fromCandidate: Candidate,
        toCandidate: Candidate,
        gpsDistance: Double,
        roadGraph: RoadNetworkGraph,
        beta: Double  // Dynamic BETA based on speed
    ): Double {
        // Great circle distance between GPS points
        val greatCircleDistance = gpsDistance
        
        // Routing distance between road segments
        val routingDistance = calculateRoutingDistance(
            fromCandidate,
            toCandidate,
            roadGraph
        )
        
        // If routing distance is much larger than GPS distance, penalize
        val deltaDistance = abs(routingDistance - greatCircleDistance)
        
        // Exponential distribution: exp(-β * |routing_dist - gps_dist| / gps_dist)
        // β is now DYNAMIC based on speed!
        val normalizedDelta = if (greatCircleDistance > 0) {
            deltaDistance / greatCircleDistance
        } else {
            0.0
        }
        
        return exp(-beta * normalizedDelta)
    }
    
    /**
     * Calculate routing distance between two candidates
     * If on same road, use road geometry
     * If on different roads, use road network routing
     */
    private fun calculateRoutingDistance(
        fromCandidate: Candidate,
        toCandidate: Candidate,
        roadGraph: RoadNetworkGraph
    ): Double {
        // Same road segment - direct calculation
        if (fromCandidate.roadSegment == toCandidate.roadSegment) {
            return calculateDistanceInMeters(
                fromCandidate.projectedPoint,
                toCandidate.projectedPoint
            )
        }
        
        // Different roads - check if connected
        if (roadGraph.areRoadsConnected(fromCandidate.roadSegment, toCandidate.roadSegment)) {
            // Use road network to find shortest path
            val path = roadGraph.findPathBetweenRoads(
                fromRoad = fromCandidate.roadSegment,
                toRoad = toCandidate.roadSegment,
                fromPoint = fromCandidate.projectedPoint,
                toPoint = toCandidate.projectedPoint
            )
            
            if (path != null && path.isNotEmpty()) {
                return calculatePathLength(path)
            }
        }
        
        // Fallback: great circle distance (if no connection found)
        return calculateDistanceInMeters(
            fromCandidate.projectedPoint,
            toCandidate.projectedPoint
        )
    }
    
    /**
     * SIMPLIFIED Viterbi algorithm (БЕЗ Road Graph - 7× по-бързо!)
     * Uses direct distance instead of Dijkstra routing
     */
    private fun viterbiAlgorithmSimplified(
        allCandidates: List<List<Candidate>>,
        gpsPoints: List<GeoPoint>,
        params: SpeedAdjustedParams,
        previousContext: PathContext?
    ): ViterbiResult {
        if (allCandidates.isEmpty() || gpsPoints.isEmpty()) {
            return ViterbiResult(emptyList(), previousContext)
        }
        
        data class ViterbiState(
            val candidate: Candidate,
            val probability: Double,
            val previousCandidate: Candidate?
        )
        
        val viterbi = mutableListOf<List<ViterbiState>>()
        
        // Initialize first column
        val firstStates = allCandidates[0].map { candidate ->
            val initialBoost = calculateInitialContinuityBoost(previousContext, candidate)
            ViterbiState(
                candidate = candidate,
                probability = candidate.emissionProbability * initialBoost,
                previousCandidate = previousContext?.lastCandidate
            )
        }
        viterbi.add(firstStates)
        
        // Fill remaining columns
        for (t in 1 until allCandidates.size) {
            val currentCandidates = allCandidates[t]
            if (currentCandidates.isEmpty()) continue
            
            val previousStates = viterbi.lastOrNull() ?: continue
            if (previousStates.isEmpty()) continue
            
            val currentStates = mutableListOf<ViterbiState>()
            val gpsDistance = calculateDistanceInMeters(gpsPoints[t - 1], gpsPoints[t])
            
            for (currentCandidate in currentCandidates) {
                var maxProbability = 0.0
                var bestPrevious: Candidate? = null
                
                for (prevState in previousStates) {
                    // DIRECT DISTANCE (БЕЗ Dijkstra!)
                    val directDist = calculateDistanceInMeters(
                        prevState.candidate.projectedPoint,
                        currentCandidate.projectedPoint
                    )
                    
                    // Road continuity penalty (STRICT to prevent lane switching!)
                    val samePath = prevState.candidate.roadSegment.id == currentCandidate.roadSegment.id
                    val penaltyFactor = if (samePath) 1.0 else 0.3  // По-строг penalty!
                    
                    // Transition probability
                    val deltaDistance = abs(directDist - gpsDistance)
                    val normalizedDelta = if (gpsDistance > 0) deltaDistance / gpsDistance else 0.0
                    val transitionProb = exp(-params.beta * normalizedDelta) * penaltyFactor
                    
                    val probability = prevState.probability * 
                                    currentCandidate.emissionProbability * 
                                    transitionProb
                    
                    if (probability > maxProbability) {
                        maxProbability = probability
                        bestPrevious = prevState.candidate
                    }
                }
                
                currentStates.add(ViterbiState(
                    candidate = currentCandidate,
                    probability = maxProbability,
                    previousCandidate = bestPrevious
                ))
            }
            
            if (currentStates.isNotEmpty()) {
                viterbi.add(currentStates)
            }
        }
        
        // Backtracking
        if (viterbi.isEmpty()) return ViterbiResult(emptyList(), previousContext)
        
        val rawPath = mutableListOf<Candidate>()
        var currentState = viterbi.last().maxByOrNull { it.probability } ?: return ViterbiResult(emptyList(), previousContext)
        
        rawPath.add(currentState.candidate)
        
        for (t in viterbi.size - 2 downTo 0) {
            val prevCandidate = currentState.previousCandidate ?: break
            currentState = viterbi[t].firstOrNull { it.candidate == prevCandidate } ?: break
            rawPath.add(0, currentState.candidate)
        }
        
        // === APPLY MINIMUM PATH DURATION FILTER ===
        val (smoothedPath, finalContext) = applyMinimumPathDuration(
            rawPath = rawPath,
            minDuration = 7,
            previousContext = previousContext
        )
        
        return ViterbiResult(smoothedPath, finalContext)
    }
    
    /**
     * Apply minimum path duration filter to prevent rapid path switching
     * This eliminates "ping-pong" between parallel roads (e.g. opposite lanes)
     */
    private fun applyMinimumPathDuration(
        rawPath: List<Candidate>,
        minDuration: Int,
        previousContext: PathContext?
    ): Pair<List<Candidate>, PathContext?> {
        if (rawPath.isEmpty()) return emptyList<Candidate>() to previousContext
        
        val smoothedPath = mutableListOf<Candidate>()
        var currentRoadId: String? = previousContext?.roadId
        var currentRoadDuration = previousContext?.duration ?: 0
        var lastValidCandidate: Candidate? = previousContext?.lastCandidate
        
        rawPath.forEach { candidate ->
            if (currentRoadId == null) {
                currentRoadId = candidate.roadSegment.id
                currentRoadDuration = 1
                smoothedPath.add(candidate)
                lastValidCandidate = candidate
                return@forEach
            }
            
            if (candidate.roadSegment.id == currentRoadId) {
                currentRoadDuration++
                smoothedPath.add(candidate)
                lastValidCandidate = candidate
            } else {
                if (currentRoadDuration >= minDuration) {
                    currentRoadId = candidate.roadSegment.id
                    currentRoadDuration = 1
                    smoothedPath.add(candidate)
                    lastValidCandidate = candidate
                } else {
                    val stayCandidate = candidate.copy(
                        roadSegment = lastValidCandidate?.roadSegment ?: candidate.roadSegment
                    )
                    smoothedPath.add(stayCandidate)
                    currentRoadDuration++
                    lastValidCandidate = stayCandidate
                }
            }
        }
        
        val switches = smoothedPath.zipWithNext().count { (a, b) -> a.roadSegment.id != b.roadSegment.id }
        android.util.Log.d("HMMMapMatcher", "   🔄 Min Duration filter: ${rawPath.size} points, $switches path switches")
        
        val finalCandidate = lastValidCandidate ?: smoothedPath.lastOrNull()
        val finalRoadId = currentRoadId ?: finalCandidate?.roadSegment?.id
        val finalContext = if (finalCandidate != null && finalRoadId != null) {
            PathContext(
                roadId = finalRoadId,
                duration = currentRoadDuration.coerceAtMost(50),
                lastCandidate = finalCandidate
            )
        } else {
            previousContext
        }
        
        return smoothedPath to finalContext
    }

    private fun calculateInitialContinuityBoost(
        previousContext: PathContext?,
        candidate: Candidate
    ): Double {
        if (previousContext == null) return 1.0
        return when (previousContext.roadId) {
            candidate.roadSegment.id -> 5.0
            else -> 0.6
        }
    }
    
    /**
     * ORIGINAL Viterbi algorithm (WITH Road Graph - SLOW but accurate!)
     * KEPT FOR REFERENCE - not used anymore
     */
    private fun viterbiAlgorithm(
        allCandidates: List<List<Candidate>>,
        gpsPoints: List<GeoPoint>,
        roadGraph: RoadNetworkGraph,
        params: SpeedAdjustedParams  // Dynamic parameters based on speed
    ): List<Candidate> {
        if (allCandidates.isEmpty() || gpsPoints.isEmpty()) return emptyList()
        
        // Initialize Viterbi matrix
        val viterbi = mutableListOf<MutableList<ViterbiState>>()
        
        // STEP 1: Initialize first column with emission probabilities
        val firstStates = allCandidates[0].map { candidate ->
            ViterbiState(
                candidate = candidate,
                probability = candidate.emissionProbability,
                previousCandidate = null
            )
        }
        viterbi.add(firstStates.toMutableList())
        
        // STEP 2: Forward pass - calculate max probability for each state
        for (t in 1 until allCandidates.size) {
            val currentCandidates = allCandidates[t]
            val previousStates = viterbi[t - 1]
            
            val currentStates = mutableListOf<ViterbiState>()
            
            // Calculate GPS distance for transition probability
            val gpsDistance = calculateDistanceInMeters(gpsPoints[t - 1], gpsPoints[t])
            
            for (currentCandidate in currentCandidates) {
                // Find best previous state
                var maxProbability = 0.0
                var bestPrevious: Candidate? = null
                
                for (prevState in previousStates) {
                    // Calculate transition probability with DYNAMIC BETA
                    val transitionProb = calculateTransitionProbability(
                        prevState.candidate,
                        currentCandidate,
                        gpsDistance,
                        roadGraph,
                        params.beta  // Use speed-adjusted beta!
                    )
                    
                    // Total probability = previous_prob * transition_prob * emission_prob
                    val totalProb = prevState.probability * transitionProb * currentCandidate.emissionProbability
                    
                    if (totalProb > maxProbability) {
                        maxProbability = totalProb
                        bestPrevious = prevState.candidate
                    }
                }
                
                currentStates.add(
                    ViterbiState(
                        candidate = currentCandidate,
                        probability = maxProbability,
                        previousCandidate = bestPrevious
                    )
                )
            }
            
            viterbi.add(currentStates)
        }
        
        // STEP 3: Backtrack to find best path
        val bestPath = mutableListOf<Candidate>()
        
        // Find best final state
        var currentState = viterbi.last().maxByOrNull { it.probability }
        
        while (currentState != null) {
            bestPath.add(0, currentState.candidate)
            
            // Find previous state
            val prevCandidate = currentState.previousCandidate
            if (prevCandidate != null && bestPath.first().gpsIndex > 0) {
                val prevIndex = bestPath.first().gpsIndex - 1
                currentState = viterbi[prevIndex].find { it.candidate == prevCandidate }
            } else {
                currentState = null
            }
        }
        
        return bestPath
    }
    
    /**
     * Convert matched candidates to ProcessedPoints
     */
    private fun convertToProcessedPoints(
        bestPath: List<Candidate>,
        gpsPoints: List<GeoPoint>,
        speedData: List<Float>
    ): List<SmartMapMatcher.ProcessedPoint> {
        return bestPath.mapIndexed { index, candidate ->
            val speed = speedData.getOrNull(candidate.gpsIndex) ?: 0f
            val bearing = if (index < bestPath.size - 1) {
                calculateBearing(candidate.projectedPoint, bestPath[index + 1].projectedPoint).toFloat()
            } else {
                0f
            }
            
            val originalGPS = gpsPoints.getOrNull(candidate.gpsIndex) ?: candidate.projectedPoint
            
            SmartMapMatcher.ProcessedPoint(
                geoPoint = candidate.projectedPoint,
                speed = speed,
                bearing = bearing,
                confidence = (candidate.emissionProbability * 100).toFloat(),
                acceleration = 0f,
                isSnapped = true,
                originalPoint = originalGPS
            )
        }
    }
    
    // ===== HELPER FUNCTIONS =====
    
    private fun fetchOSMRoads(
        gpsPoints: List<GeoPoint>,
        context: Context?
    ): List<RoadGeometry.RoadSegment> {
        if (context == null) return emptyList()
        
        return try {
            val cache = OSMTileCache.getInstance(context)
            
            // === SMART CACHING: Get what we have + identify missing ===
            val (cachedRoads, missingTiles) = cache.getSmartCacheForRoute(gpsPoints)
            
            val coverage = cache.getCoverageForRoute(gpsPoints)
            
            if (cachedRoads.isNotEmpty()) {
                android.util.Log.d("HMMMapMatcher", "📦 Loaded ${cachedRoads.size} cached roads (${(coverage * 100).toInt()}% coverage)")
            }
            
            // If we have good coverage (90%+), use cache only!
            if (coverage >= 0.90) {
                android.util.Log.d("HMMMapMatcher", "✅ Excellent coverage - using cache only!")
                return cachedRoads
            }
            
            // Partial cache - fetch ONLY missing tiles!
            if (missingTiles.isNotEmpty() && cachedRoads.isNotEmpty()) {
                android.util.Log.d("HMMMapMatcher", "🔄 Partial cache hit - fetching ${missingTiles.size} missing tiles...")
                val fetchedRoads = kotlinx.coroutines.runBlocking {
                    try {
                        // Fetch roads for missing area only
                        RoadGeometry.fetchRoadsForRoute(gpsPoints, context)
                    } catch (e: Exception) {
                        android.util.Log.e("HMMMapMatcher", "❌ API fetch failed: ${e.message}")
                        emptyList()
                    }
                }
                
                if (fetchedRoads.isNotEmpty()) {
                    cache.cacheRoadsForRoute(gpsPoints, fetchedRoads)
                    android.util.Log.d("HMMMapMatcher", "💾 Cached ${fetchedRoads.size} new roads")
                }
                
                // Combine cached + fetched roads
                val combinedRoads = (cachedRoads + fetchedRoads).distinctBy { it.id }
                android.util.Log.d("HMMMapMatcher", "✅ Total roads: ${combinedRoads.size} (${cachedRoads.size} cached + ${fetchedRoads.size} fetched)")
                return combinedRoads
            }
            
            // No cache - full API fetch
            android.util.Log.d("HMMMapMatcher", "🌐 No cache found - fetching from Overpass API...")
            val fetchedRoads = kotlinx.coroutines.runBlocking {
                try {
                    RoadGeometry.fetchRoadsForRoute(gpsPoints, context)
                } catch (e: Exception) {
                    android.util.Log.e("HMMMapMatcher", "❌ API fetch failed: ${e.message}")
                    emptyList()
                }
            }
            
            // Cache for next time
            if (fetchedRoads.isNotEmpty()) {
                cache.cacheRoadsForRoute(gpsPoints, fetchedRoads)
                android.util.Log.d("HMMMapMatcher", "💾 Cached ${fetchedRoads.size} roads for future use")
            } else {
                android.util.Log.w("HMMMapMatcher", "⚠️ Overpass API returned 0 roads")
            }
            
            fetchedRoads
        } catch (e: Exception) {
            android.util.Log.e("HMMMapMatcher", "❌ Error fetching roads: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
    
    private fun fallbackKalmanOnly(
        gpsPoints: List<GeoPoint>,
        speedData: List<Float>
    ): List<SmartMapMatcher.ProcessedPoint> {
        android.util.Log.d("HMMMapMatcher", "Using Kalman filter fallback")
        
        // Simple Kalman filter
        data class KalmanState(val lat: Double, val lon: Double, val latVar: Double, val lonVar: Double)
        
        fun applyKalman(state: KalmanState, measurement: GeoPoint): KalmanState {
            val processNoise = 0.1
            val measurementNoise = 10.0
            
            val predictedLatVariance = state.latVar + processNoise
            val predictedLonVariance = state.lonVar + processNoise
            
            val latGain = predictedLatVariance / (predictedLatVariance + measurementNoise)
            val lonGain = predictedLonVariance / (predictedLonVariance + measurementNoise)
            
            val newLat = state.lat + latGain * (measurement.latitude - state.lat)
            val newLon = state.lon + lonGain * (measurement.longitude - state.lon)
            
            return KalmanState(
                newLat, newLon,
                (1 - latGain) * predictedLatVariance,
                (1 - lonGain) * predictedLonVariance
            )
        }
        
        var kalmanState = KalmanState(
            gpsPoints[0].latitude,
            gpsPoints[0].longitude,
            100.0,
            100.0
        )
        
        return gpsPoints.mapIndexed { index, gpsPoint ->
            kalmanState = applyKalman(kalmanState, gpsPoint)
            
            SmartMapMatcher.ProcessedPoint(
                geoPoint = GeoPoint(kalmanState.lat, kalmanState.lon),
                speed = speedData.getOrNull(index) ?: 0f,
                bearing = 0f,
                confidence = 50f,
                acceleration = 0f,
                isSnapped = false,
                originalPoint = gpsPoint
            )
        }
    }
    
    private fun projectPointOnLineSegment(
        point: GeoPoint,
        lineStart: GeoPoint,
        lineEnd: GeoPoint
    ): GeoPoint {
        val dx = lineEnd.longitude - lineStart.longitude
        val dy = lineEnd.latitude - lineStart.latitude
        
        if (dx == 0.0 && dy == 0.0) return lineStart
        
        val t = ((point.longitude - lineStart.longitude) * dx +
                (point.latitude - lineStart.latitude) * dy) /
                (dx * dx + dy * dy)
        
        val clampedT = t.coerceIn(0.0, 1.0)
        
        val projectedLat = lineStart.latitude + clampedT * dy
        val projectedLon = lineStart.longitude + clampedT * dx
        
        return GeoPoint(projectedLat, projectedLon)
    }
    
    private fun calculateDistanceInMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val earthRadius = 6371000.0
        
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
    
    /**
     * Normalize bearing difference to 0-180 range
     */
    private fun normalizeBearingDiff(diff: Double): Double {
        var normalized = diff % 360.0
        if (normalized < 0) normalized += 360.0
        if (normalized > 180.0) normalized = 360.0 - normalized
        return normalized
    }
    
    private fun calculateBearing(from: GeoPoint, to: GeoPoint): Double {
        val dLon = to.longitude - from.longitude
        val dLat = to.latitude - from.latitude
        val bearing = Math.toDegrees(atan2(dLon, dLat))
        return if (bearing < 0) bearing + 360.0 else bearing
    }
    
    // Keep Float version for compatibility
    private fun calculateBearingFloat(from: GeoPoint, to: GeoPoint): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)
        
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        
        val bearing = Math.toDegrees(atan2(y, x))
        return ((bearing + 360) % 360).toFloat()
    }
    
    private fun calculatePathLength(path: List<GeoPoint>): Double {
        var length = 0.0
        for (i in 0 until path.size - 1) {
            length += calculateDistanceInMeters(path[i], path[i + 1])
        }
        return length
    }
}

