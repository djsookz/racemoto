package com.example.clinometer

import android.content.Context
import com.example.clinometer.track.catalog.OfficialTrackCatalog
import com.example.clinometer.track.catalog.TrackDefinition
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

class TrackManager(private val context: Context) {
    
    private val gpxParser = GPXParser(context)
    
    fun getAllTracks(): List<TrackDefinition> = OfficialTrackCatalog.getAll()
    
    fun getTrackById(id: String): TrackDefinition? = OfficialTrackCatalog.getById(id)

    fun getTrackDefinition(id: String): TrackDefinition? = getTrackById(id)
    
    fun loadTrackData(trackId: String): TrackData? {
        val track = getTrackById(trackId) ?: return null
        val gpxResourceId = track.gpxResourceId ?: return null
        return gpxParser.parseTrack(gpxResourceId)
    }
    
    fun setupTrackOnMap(map: MapView, trackId: String) {
        val trackData = loadTrackData(trackId) ?: return
        
        // Clear existing overlays
        map.overlays.clear()
        
        // Add track layout
        if (trackData.trackPoints.isNotEmpty()) {
            val trackPolyline = Polyline().apply {
                val osmdroidPoints = trackData.trackPoints.map { 
                    org.osmdroid.util.GeoPoint(it.geoPoint.latitude, it.geoPoint.longitude)
                }
                setPoints(osmdroidPoints)
                color = android.graphics.Color.parseColor("#1976D2")
                outlinePaint.strokeWidth = 8f
            }
            map.overlays.add(trackPolyline)
        }
        
        // Add sector markers
        trackData.sectors.forEachIndexed { index, sector ->
            val sectorPoint = sector.startPoint.geoPoint
            val sectorMarker = Marker(map).apply {
                position = org.osmdroid.util.GeoPoint(sectorPoint.latitude, sectorPoint.longitude)
                title = "Sector ${sector.sectorNumber}"
                snippet = "Sector ${sector.sectorNumber} Start"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(sectorMarker)
        }
        
        // Add corner markers
        trackData.corners.forEach { corner ->
            val cornerPoint = corner.geoPoint
            val cornerMarker = Marker(map).apply {
                position = org.osmdroid.util.GeoPoint(cornerPoint.latitude, cornerPoint.longitude)
                title = corner.name ?: "Corner"
                snippet = corner.description ?: ""
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(cornerMarker)
        }
        
        // Add pit lane
        if (trackData.pitLane.isNotEmpty()) {
            val pitLanePolyline = Polyline().apply {
                val osmdroidPoints = trackData.pitLane.map { 
                    org.osmdroid.util.GeoPoint(it.geoPoint.latitude, it.geoPoint.longitude)
                }
                setPoints(osmdroidPoints)
                color = android.graphics.Color.parseColor("#FF9800")
                outlinePaint.strokeWidth = 4f
            }
            map.overlays.add(pitLanePolyline)
        }
        
        // Add start/finish line marker
        if (trackData.trackPoints.isNotEmpty()) {
            val firstPoint = trackData.trackPoints.first().geoPoint
            val startFinishMarker = Marker(map).apply {
                position = org.osmdroid.util.GeoPoint(firstPoint.latitude, firstPoint.longitude)
                title = "Start/Finish Line"
                snippet = "Start and finish of the lap"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(startFinishMarker)
        }
        
        // Zoom to track
        zoomToTrack(map, trackData)
        
        map.invalidate()
    }
    
    private fun zoomToTrack(map: MapView, trackData: TrackData) {
        if (trackData.trackPoints.isEmpty()) return
        
        val allPoints = trackData.trackPoints.map { it.geoPoint }
        val osmdroidPoints = allPoints.map { 
            org.osmdroid.util.GeoPoint(it.latitude, it.longitude)
        }
        
        if (osmdroidPoints.size >= 2) {
            val boundingBox = BoundingBox.fromGeoPointsSafe(osmdroidPoints)
            
            val latDiff = boundingBox.latNorth - boundingBox.latSouth
            val lonDiff = boundingBox.lonEast - boundingBox.lonWest
            val padding = kotlin.math.max(latDiff, lonDiff) * 0.15
            
            val adjustedBox = BoundingBox(
                boundingBox.latNorth + padding,
                boundingBox.lonEast + padding,
                boundingBox.latSouth - padding,
                boundingBox.lonWest - padding
            )
            
            map.post {
                map.zoomToBoundingBox(adjustedBox, false)
                map.invalidate()
            }
        } else {
            val point = allPoints[0]
            val osmdroidPoint = org.osmdroid.util.GeoPoint(point.latitude, point.longitude)
            map.controller.setCenter(osmdroidPoint)
            map.controller.setZoom(15.0)
        }
    }
    
    fun getTrackInfo(trackId: String): String? {
        val track = getTrackById(trackId) ?: return null
        return """
            ${track.name}
            ${track.description}
            Length: ${track.lengthKm}km
            Turns: ${track.turns}
            Country: ${track.country}
        """.trimIndent()
    }
}
