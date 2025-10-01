package com.example.clinometer

import android.content.Context
import org.osmdroid.util.GeoPoint
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double = 0.0,
    val name: String? = null,
    val description: String? = null
) {
    val geoPoint: GeoPoint
        get() = GeoPoint(latitude, longitude)
}

data class TrackSector(
    val startPoint: TrackPoint,
    val endPoint: TrackPoint,
    val sectorNumber: Int
)

data class TrackData(
    val name: String,
    val description: String,
    val trackPoints: List<TrackPoint>,
    val sectors: List<TrackSector>,
    val corners: List<TrackPoint>,
    val pitLane: List<TrackPoint>
)

class GPXParser(private val context: Context) {
    
    fun parseTrack(resourceId: Int): TrackData? {
        return try {
            val inputStream: InputStream = context.resources.openRawResource(resourceId)
            val document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(inputStream)
            
            val trackPoints = parseTrackPoints(document)
            val sectors = parseSectors(document)
            val corners = parseCorners(document)
            val pitLane = parsePitLane(document)
            
            val metadata = document.getElementsByTagName("metadata").item(0) as? Element
            val name = metadata?.getElementsByTagName("name")?.item(0)?.textContent ?: "Unknown Track"
            val description = metadata?.getElementsByTagName("desc")?.item(0)?.textContent ?: ""
            
            TrackData(name, description, trackPoints, sectors, corners, pitLane)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun parseTrackPoints(document: Document): List<TrackPoint> {
        val trackPoints = mutableListOf<TrackPoint>()
        val trkpts = document.getElementsByTagName("trkpt")
        
        for (i in 0 until trkpts.length) {
            val trkpt = trkpts.item(i) as Element
            val lat = trkpt.getAttribute("lat").toDouble()
            val lon = trkpt.getAttribute("lon").toDouble()
            val ele = trkpt.getElementsByTagName("ele").item(0)?.textContent?.toDoubleOrNull() ?: 0.0
            val name = trkpt.getElementsByTagName("name").item(0)?.textContent
            val desc = trkpt.getElementsByTagName("desc").item(0)?.textContent
            
            trackPoints.add(TrackPoint(lat, lon, ele, name, desc))
        }
        
        return trackPoints
    }
    
    private fun parseSectors(document: Document): List<TrackSector> {
        // Temporarily disabled: sectors will be provided anew (4 sectors incl. start/finish)
        return emptyList()
    }
    
    private fun parseCorners(document: Document): List<TrackPoint> {
        val corners = mutableListOf<TrackPoint>()
        val waypoints = document.getElementsByTagName("wpt")
        
        for (i in 0 until waypoints.length) {
            val wpt = waypoints.item(i) as Element
            val name = wpt.getElementsByTagName("name").item(0)?.textContent ?: ""
            
            if (name.contains("Turn")) {
                val lat = wpt.getAttribute("lat").toDouble()
                val lon = wpt.getAttribute("lon").toDouble()
                val ele = wpt.getElementsByTagName("ele").item(0)?.textContent?.toDoubleOrNull() ?: 0.0
                val desc = wpt.getElementsByTagName("desc").item(0)?.textContent
                
                corners.add(TrackPoint(lat, lon, ele, name, desc))
            }
        }
        
        return corners
    }
    
    private fun parsePitLane(document: Document): List<TrackPoint> {
        val pitLane = mutableListOf<TrackPoint>()
        val waypoints = document.getElementsByTagName("wpt")
        
        for (i in 0 until waypoints.length) {
            val wpt = waypoints.item(i) as Element
            val name = wpt.getElementsByTagName("name").item(0)?.textContent ?: ""
            
            if (name.contains("Pit Lane")) {
                val lat = wpt.getAttribute("lat").toDouble()
                val lon = wpt.getAttribute("lon").toDouble()
                val ele = wpt.getElementsByTagName("ele").item(0)?.textContent?.toDoubleOrNull() ?: 0.0
                val desc = wpt.getElementsByTagName("desc").item(0)?.textContent
                
                pitLane.add(TrackPoint(lat, lon, ele, name, desc))
            }
        }
        
        return pitLane
    }
}
