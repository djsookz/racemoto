package com.revix.app.averagespeed

import android.util.Log
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.dsl.generated.eq
import com.mapbox.maps.extension.style.expressions.dsl.generated.get
import com.mapbox.maps.extension.style.expressions.dsl.generated.interpolate
import com.mapbox.maps.extension.style.expressions.dsl.generated.literal
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import java.util.Locale

internal class AverageSpeedMapLayer {
    private var attachedStyle: Style? = null
    private var lineFeatures: FeatureCollection =
        FeatureCollection.fromFeatures(emptyList())

    fun attach(style: Style) {
        attachedStyle = style
        try {
            ensureSource(style, LINE_SOURCE_ID, lineFeatures)
            if (style.styleLayerExists(POINT_LAYER_ID)) {
                style.removeStyleLayer(POINT_LAYER_ID)
            }
            if (style.styleSourceExists(POINT_SOURCE_ID)) {
                style.removeStyleSource(POINT_SOURCE_ID)
            }
            if (style.styleLayerExists(OPP_LAYER_ID)) {
                style.removeStyleLayer(OPP_LAYER_ID)
            }
            if (style.styleLayerExists(LAYER_ID)) {
                style.removeStyleLayer(LAYER_ID)
            }
            // `middle` sits with roads, under Mapbox Standard labels.
            style.addLayer(avgSpeedLineLayer(LAYER_ID, "primary"))
            style.addLayer(avgSpeedLineLayer(OPP_LAYER_ID, "opposite"))
            pushToStyle()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach average-speed layer", e)
        }
    }

    fun showCatalog(sections: List<AverageSpeedSection>) {
        val unique = uniqueCorridors(sections)
        val lines = ArrayList<Feature>(unique.size)
        val drawn = ArrayList<List<Point>>()
        val drawnByCorridor = HashMap<String, List<Point>>()
        fun addLine(points: List<Point>, role: String): Boolean {
            if (points.size < 2) return false
            if (drawn.any { polylinesOverlap(it, points) }) return false
            lines.add(lineFeature(points, role))
            drawn.add(points)
            return true
        }
        for (section in unique) {
            val display = AverageSpeedGeometry.flattenSpikes(
                AverageSpeedGeometry.collapseDetours(stripEndpointHooks(section.points))
            )
            if (display.size < 2) continue
            val undirected = undirectedKey(section)
            val partner = drawnByCorridor[undirected]
            if (partner == null) {
                if (!addLine(display, "primary")) continue
                drawnByCorridor[undirected] = display
                for (stretch in section.oppositeStretches) {
                    val cleaned = AverageSpeedGeometry.flattenSpikes(
                        AverageSpeedGeometry.collapseDetours(stretch)
                    )
                    if (AverageSpeedGeometry.lengthMeters(cleaned) < MIN_OPPOSITE_STRETCH_M) continue
                    addLine(cleaned, "opposite")
                }
                continue
            }
            if (section.oppositeStretches.isNotEmpty()) continue
            if (polylinesOverlap(partner, display) ||
                AverageSpeedGeometry.isRigidParallelOffset(partner, display)
            ) {
                continue
            }
            val stretches = AverageSpeedGeometry.clipToSeparationBand(display, partner)
            for (stretch in stretches) {
                if (AverageSpeedGeometry.lengthMeters(stretch) < MIN_OPPOSITE_STRETCH_M) continue
                addLine(stretch, "opposite")
            }
        }
        lineFeatures = FeatureCollection.fromFeatures(lines)
        pushToStyle()
    }

    fun detach() {
        lineFeatures = FeatureCollection.fromFeatures(emptyList())
        pushToStyle()
        attachedStyle = null
    }

    private fun ensureSource(style: Style, sourceId: String, collection: FeatureCollection) {
        if (style.styleSourceExists(sourceId)) return
        style.addSource(
            geoJsonSource(sourceId) {
                featureCollection(collection)
            }
        )
    }

    private fun pushToStyle() {
        val style = attachedStyle ?: return
        try {
            style.getSourceAs<GeoJsonSource>(LINE_SOURCE_ID)?.featureCollection(lineFeatures)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update average-speed catalog layer", e)
        }
    }

    private fun stripEndpointHooks(points: List<Point>): List<Point> {
        if (points.size < 3) return points
        var start = 0
        var end = points.lastIndex
        while (end - start >= 2 && isEndpointHook(points[start], points[start + 1], points[start + 2])) {
            start += 1
        }
        while (end - start >= 2 && isEndpointHook(points[end], points[end - 1], points[end - 2])) {
            end -= 1
        }
        return if (end - start + 1 >= 2) points.subList(start, end + 1) else points
    }

    private fun isEndpointHook(a: Point, b: Point, c: Point): Boolean {
        if (AverageSpeedGeometry.distanceMeters(a, b) > 45.0) return false
        val turn = AverageSpeedGeometry.headingDeltaDegrees(
            AverageSpeedGeometry.bearingDegrees(a, b),
            AverageSpeedGeometry.bearingDegrees(b, c)
        )
        return turn > 50.0
    }

    private fun lineFeature(points: List<Point>, role: String): Feature {
        val feature = Feature.fromGeometry(LineString.fromLngLats(points))
        feature.addStringProperty("role", role)
        return feature
    }

    private fun uniqueCorridors(sections: List<AverageSpeedSection>): List<AverageSpeedSection> {
        val seen = HashSet<String>()
        val unique = ArrayList<AverageSpeedSection>(sections.size)
        for (section in sections) {
            if (section.points.size < 2) continue
            if (!seen.add(corridorKey(section))) continue
            unique.add(section)
        }
        return unique.sortedWith(
            compareBy<AverageSpeedSection> { undirectedKey(it) }
                .thenByDescending { it.oppositeStretches.isNotEmpty() }
        )
    }

    private fun undirectedKey(section: AverageSpeedSection): String {
        val start = pointKey(section.start)
        val end = pointKey(section.end)
        val ordered = if (start <= end) "$start|$end" else "$end|$start"
        return "${section.road}|$ordered"
    }

    private fun polylinesOverlap(a: List<Point>, b: List<Point>): Boolean {
        if (a.size < 2 || b.size < 2) return true
        val samples = listOf(a.size / 4, a.size / 2, a.size * 3 / 4)
        var hits = 0
        for (index in samples) {
            val point = a[index.coerceIn(0, a.lastIndex)]
            var best = Double.MAX_VALUE
            for (other in b) {
                val d = AverageSpeedGeometry.distanceMeters(point, other)
                if (d < best) best = d
            }
            if (best < 8.0) hits += 1
        }
        return hits >= 2
    }

    private fun corridorKey(section: AverageSpeedSection): String {
        val start = pointKey(section.start)
        val end = pointKey(section.end)
        return "${section.road}|$start|$end"
    }

    private fun pointKey(point: Point): String {
        return String.format(Locale.US, "%.4f,%.4f", point.longitude(), point.latitude())
    }

    private fun avgSpeedLineLayer(
        layerId: String,
        role: String,
        minZoom: Double? = null
    ) = lineLayer(layerId, LINE_SOURCE_ID) {
        filter(
            eq {
                get("role")
                literal(role)
            }
        )
        if (minZoom != null) minZoom(minZoom)
        lineColor(LINE_COLOR)
        lineWidth(
            interpolate {
                exponential(1.5)
                zoom()
                stop(8.0, 1.8)
                stop(10.0, 2.2)
                stop(12.0, 3.2)
                stop(13.0, 4.0)
                stop(14.0, 5.5)
                stop(15.0, 8.0)
                stop(16.0, 14.0)
                stop(17.0, 24.0)
                stop(18.0, 36.0)
                stop(20.0, 90.0)
            }
        )
        lineOpacity(0.88)
        lineCap(LineCap.ROUND)
        lineJoin(LineJoin.ROUND)
        slot("middle")
    }

    private companion object {
        const val TAG = "AvgSpeedMapLayer"
        const val LINE_SOURCE_ID = "revix-avg-speed-src"
        const val POINT_SOURCE_ID = "revix-avg-speed-ends"
        const val LAYER_ID = "revix-avg-speed-line"
        const val OPP_LAYER_ID = "revix-avg-speed-line-opp"
        const val POINT_LAYER_ID = "revix-avg-speed-ends-layer"
        const val LINE_COLOR = "#c73b0c"
        const val MIN_OPPOSITE_STRETCH_M = 80.0
    }
}
