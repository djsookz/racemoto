package com.example.clinometer.tracking

import android.content.Context
import android.location.Location
import org.osmdroid.util.GeoPoint
import kotlin.math.*
import kotlinx.coroutines.runBlocking

/**
 * Smart Map Matching system for track sessions
 * Uses real track geometry data for accurate snapping
 */
object SmartMapMatcher {
    
    // Kalman Filter state
    data class KalmanState(
        var lat: Double,
        var lon: Double,
        var latVariance: Double,
        var lonVariance: Double
    )
    
    // Configuration
    private const val HIGH_CONFIDENCE_THRESHOLD = 0.8f
    private const val LOW_CONFIDENCE_THRESHOLD = 0.5f
    private const val MIN_SPEED_FOR_SNAPPING = 5.0 // km/h
    private const val MAX_SNAPPING_DISTANCE = 50.0 // meters
    private const val MIN_SNAPPING_DISTANCE = 5.0 // meters
    
    // Track-specific configurations
    private val TRACK_CONFIGS = mapOf(
        "serres_circuit" to TrackConfig(
            maxSpeed = 300.0, // km/h - increased for motorcycles
            typicalSpeed = 120.0, // km/h
            trackWidth = 12.0, // meters
            snappingRadius = 25.0 // meters
        ),
        "sofia_ring" to TrackConfig(
            maxSpeed = 280.0,
            typicalSpeed = 140.0,
            trackWidth = 14.0,
            snappingRadius = 30.0
        ),
        "custom_track" to TrackConfig(
            maxSpeed = 200.0,
            typicalSpeed = 100.0,
            trackWidth = 10.0,
            snappingRadius = 20.0
        )
    )
    
    data class TrackConfig(
        val maxSpeed: Double,
        val typicalSpeed: Double,
        val trackWidth: Double,
        val snappingRadius: Double
    )
    
    data class SensorData(
        val accelerometer: FloatArray,
        val gyroscope: FloatArray,
        val magneticField: FloatArray,
        val timestamp: Long
    )
    
    data class ProcessedPoint(
        val geoPoint: GeoPoint,
        val confidence: Float,
        val speed: Float,
        val bearing: Float,
        val acceleration: Float,
        val isSnapped: Boolean,
        val originalPoint: GeoPoint
    )
    
    /**
     * Process GPS points for regular session
     * Uses HMM (Hidden Markov Model) Map Matching - Industry Standard
     */
    fun processRegularSession(
        gpsPoints: List<GeoPoint>,
        speedData: List<Float>,
        context: Context? = null
    ): List<ProcessedPoint> {
        if (gpsPoints.isEmpty()) return emptyList()
        
        // Use HMM Map Matching (most reliable, no backwards jumps!)
        return HMMMapMatcher.processWithHMM(gpsPoints, speedData, context)
    }
    
    /**
     * Process GPS points with real track geometry and smart snapping
     * For custom tracks
     */
    fun processTrackSession(
        gpsPoints: List<GeoPoint>,
        sensorData: List<SensorData>,
        trackId: String,
        speedData: List<Float>,
        snapPoints: List<GeoPoint> = emptyList(),
        context: Context? = null
    ): List<ProcessedPoint> {
        if (gpsPoints.isEmpty()) return emptyList()
        
        val trackConfig = TRACK_CONFIGS[trackId] ?: TRACK_CONFIGS["custom_track"]!!
        val processedPoints = mutableListOf<ProcessedPoint>()
        
        // Initialize Kalman filter with first point
        var kalmanState = KalmanState(
            lat = gpsPoints[0].latitude,
            lon = gpsPoints[0].longitude,
            latVariance = 0.0001,
            lonVariance = 0.0001
        )
        
        for (i in gpsPoints.indices) {
            val gpsPoint = gpsPoints[i]
            val speed = speedData.getOrNull(i) ?: 0f
            val sensor = sensorData.getOrNull(i)
            
            // Apply Kalman filter
            kalmanState = applyKalmanFilter(
                state = kalmanState,
                measurement = gpsPoint,
                speed = speed,
                processNoise = 0.00001,
                measurementNoise = if (speed > 30f) 0.00002 else 0.00005
            )
            
            val filteredPoint = GeoPoint(kalmanState.lat, kalmanState.lon)
            
            // Calculate confidence
            val confidence = calculateConfidence(
                gpsPoint = gpsPoint,
                speed = speed,
                sensor = sensor,
                previousPoint = processedPoints.lastOrNull(),
                trackConfig = trackConfig
            )
            
            // Smart snapping to track geometry
            val snappedPoint = snapToTrackGeometry(
                point = filteredPoint,
                trackId = trackId,
                speed = speed,
                confidence = confidence,
                snapPoints = snapPoints,
                context = context
            )
            
            val bearing = calculateBearing(processedPoints.lastOrNull()?.geoPoint, snappedPoint)
            
            processedPoints.add(
                ProcessedPoint(
                    geoPoint = snappedPoint,
                    confidence = confidence,
                    speed = speed,
                    bearing = bearing,
                    acceleration = 0f,
                    isSnapped = snappedPoint != filteredPoint,
                    originalPoint = gpsPoint
                )
            )
        }
        
        return processedPoints
    }
    
    private fun snapToTrackGeometry(
        point: GeoPoint,
        trackId: String,
        speed: Float,
        confidence: Float,
        snapPoints: List<GeoPoint>,
        context: Context?
    ): GeoPoint {
        if (confidence < LOW_CONFIDENCE_THRESHOLD || speed < 10f) {
            return point
        }
        
        // For custom tracks, use snap points
        if (snapPoints.isNotEmpty()) {
            val closestSnapPoint = findClosestPoint(snapPoints, point)
            val distanceToSnap = calculateDistanceInMeters(point, closestSnapPoint)
            
            val maxSnapDistance = if (confidence > HIGH_CONFIDENCE_THRESHOLD) 80.0 else 40.0
            
            if (distanceToSnap <= maxSnapDistance) {
                val blendFactor = (confidence - LOW_CONFIDENCE_THRESHOLD) / (HIGH_CONFIDENCE_THRESHOLD - LOW_CONFIDENCE_THRESHOLD)
                val finalLat = point.latitude * (1 - blendFactor) + closestSnapPoint.latitude * blendFactor
                val finalLon = point.longitude * (1 - blendFactor) + closestSnapPoint.longitude * blendFactor
                
                return GeoPoint(finalLat, finalLon)
            }
        }
        
        return point
    }
    
    private fun applyKalmanFilter(
        state: KalmanState,
        measurement: GeoPoint,
        speed: Float,
        processNoise: Double,
        measurementNoise: Double
    ): KalmanState {
        val predictedLatVariance = state.latVariance + processNoise
        val predictedLonVariance = state.lonVariance + processNoise
        
        val latGain = predictedLatVariance / (predictedLatVariance + measurementNoise)
        val lonGain = predictedLonVariance / (predictedLonVariance + measurementNoise)
        
        val newLat = state.lat + latGain * (measurement.latitude - state.lat)
        val newLon = state.lon + lonGain * (measurement.longitude - state.lon)
        
        val newLatVariance = (1 - latGain) * predictedLatVariance
        val newLonVariance = (1 - lonGain) * predictedLonVariance
        
        return KalmanState(newLat, newLon, newLatVariance, newLonVariance)
    }
    
    private fun calculateConfidence(
        gpsPoint: GeoPoint,
        speed: Float,
        sensor: SensorData?,
        previousPoint: ProcessedPoint?,
        trackConfig: TrackConfig
    ): Float {
        var confidence = 0.5f
        
        val speedConfidence = when {
            speed < 5f -> 0.3f
            speed in 5f..50f -> 0.6f
            speed in 50f..trackConfig.typicalSpeed.toFloat() -> 0.8f
            speed <= trackConfig.maxSpeed.toFloat() -> 0.7f
            else -> 0.4f
        }
        confidence += speedConfidence * 0.5f
        
        return confidence.coerceIn(0f, 1f)
    }
    
    private fun findClosestPoint(points: List<GeoPoint>, target: GeoPoint): GeoPoint {
        return points.minByOrNull { point ->
            calculateDistanceInMeters(point, target)
        } ?: points.first()
    }
    
    private fun calculateBearing(from: GeoPoint?, to: GeoPoint): Float {
        if (from == null) return 0f
        
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)
        
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        
        val bearing = Math.toDegrees(atan2(y, x))
        return ((bearing + 360) % 360).toFloat()
    }
    
    private fun calculateDistanceInMeters(point1: GeoPoint, point2: GeoPoint): Double {
        val earthRadius = 6371000.0 // meters
        
        val lat1 = Math.toRadians(point1.latitude)
        val lat2 = Math.toRadians(point2.latitude)
        val deltaLat = Math.toRadians(point2.latitude - point1.latitude)
        val deltaLon = Math.toRadians(point2.longitude - point1.longitude)
        
        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(deltaLon / 2) * sin(deltaLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
}
