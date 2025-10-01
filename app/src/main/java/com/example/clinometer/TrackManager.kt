package com.example.clinometer

import android.content.Context
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

data class Track(
    val id: String,
    val name: String,
    val description: String,
    val length: Double, // in km
    val turns: Int,
    val country: String,
    val gpxResourceId: Int
)

class TrackManager(private val context: Context) {
    
    private val gpxParser = GPXParser(context)
    private val tracks = mutableListOf<Track>()
    
    init {
        initializeTracks()
    }
    
    private fun initializeTracks() {
        tracks.add(
            Track(
                id = "serres_circuit",
                name = "Serres Circuit",
                description = "3.2km racing circuit in Greece",
                length = 3.2,
                turns = 12,
                country = "Greece",
                gpxResourceId = R.raw.serres_circuit
            )
        )
        
        // Add more tracks here in the future
    }
    
    fun getAllTracks(): List<Track> = tracks.toList()
    
    fun getTrackById(id: String): Track? = tracks.find { it.id == id }
    
    fun loadTrackData(trackId: String): TrackData? {
        val track = getTrackById(trackId) ?: return null
        return gpxParser.parseTrack(track.gpxResourceId)
    }
    
    fun setupTrackOnMap(map: MapView, trackId: String) {
        val trackData = loadTrackData(trackId) ?: return
        
        // Clear existing overlays
        map.overlays.clear()
        
        // Add track layout
        if (trackData.trackPoints.isNotEmpty()) {
            val trackPolyline = Polyline().apply {
                setPoints(trackData.trackPoints.map { it.geoPoint })
                color = android.graphics.Color.parseColor("#1976D2")
                outlinePaint.strokeWidth = 8f
            }
            map.overlays.add(trackPolyline)
        }
        
        // Add sector markers
        trackData.sectors.forEachIndexed { index, sector ->
            val sectorMarker = Marker(map).apply {
                position = sector.startPoint.geoPoint
                title = "Sector ${sector.sectorNumber}"
                snippet = "Sector ${sector.sectorNumber} Start"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(sectorMarker)
        }
        
        // Add corner markers
        trackData.corners.forEach { corner ->
            val cornerMarker = Marker(map).apply {
                position = corner.geoPoint
                title = corner.name ?: "Corner"
                snippet = corner.description ?: ""
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(cornerMarker)
        }
        
        // Add pit lane
        if (trackData.pitLane.isNotEmpty()) {
            val pitLanePolyline = Polyline().apply {
                setPoints(trackData.pitLane.map { it.geoPoint })
                color = android.graphics.Color.parseColor("#FF9800")
                outlinePaint.strokeWidth = 4f
            }
            map.overlays.add(pitLanePolyline)
        }
        
        // Add start/finish line marker
        if (trackData.trackPoints.isNotEmpty()) {
            val startFinishMarker = Marker(map).apply {
                position = trackData.trackPoints.first().geoPoint
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
        
        if (allPoints.size >= 2) {
            val boundingBox = BoundingBox.fromGeoPointsSafe(allPoints)
            
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
            map.controller.setCenter(point)
            map.controller.setZoom(15.0)
        }
    }
    
    fun getTrackInfo(trackId: String): String? {
        val track = getTrackById(trackId) ?: return null
        return """
            ${track.name}
            ${track.description}
            Length: ${track.length}km
            Turns: ${track.turns}
            Country: ${track.country}
        """.trimIndent()
    }
}
