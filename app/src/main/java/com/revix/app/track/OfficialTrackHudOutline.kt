package com.revix.app.track

import android.content.Context
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.RectF
import androidx.core.graphics.PathParser
import com.revix.app.GeoPoint
import com.revix.app.TrackManager
import com.revix.app.track.catalog.OfficialTrackCatalog
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

object OfficialTrackHudOutline {
    class Outline(
        val sourcePath: Path,
        val bounds: RectF,
        val startPoint: PointF?,
        val startAxis: PointF?,
        val trackLengthMeters: Float,
        private val centerline: List<PointF>,
        private val centerlineLength: Float,
        private val startDistance: Float,
        private val reverseProgress: Boolean
    ) {
        fun mappedTo(target: RectF): Path {
            val mapped = Path()
            if (sourcePath.isEmpty || bounds.isEmpty || target.isEmpty) return mapped
            mapped.addPath(sourcePath, mapMatrix(target))
            mapped.fillType = Path.FillType.EVEN_ODD
            return mapped
        }

        fun startOnMap(target: RectF): Pair<PointF, PointF>? {
            val start = startPoint ?: return null
            val axis = startAxis ?: return null
            val matrix = mapMatrix(target)
            val origin = floatArrayOf(start.x, start.y)
            val vector = floatArrayOf(axis.x, axis.y)
            matrix.mapPoints(origin)
            matrix.mapVectors(vector)
            return PointF(origin[0], origin[1]) to PointF(vector[0], vector[1])
        }

        fun riderOnMap(progress: Float, target: RectF): PointF? {
            if (centerline.size < 2 || centerlineLength <= 1f) {
                return startOnMap(target)?.first ?: startPoint?.let { mapSvgPoint(it, target) }
            }
            val clamped = wrap01(progress)
            return mapSvgPoint(pointAtProgress(clamped), target)
        }

        fun createLocator(gpsPath: List<GeoPoint>, startA: GeoPoint?, startB: GeoPoint?): Locator {
            return Locator(gpsPath, startA, startB)
        }

        inner class Locator(
            gpsPath: List<GeoPoint>,
            startA: GeoPoint?,
            startB: GeoPoint?
        ) {
            private val gpsCircuit = gpsPath.takeIf { path -> isDenseCircuit(path) }?.let { points ->
                GpsCircuit(points, startA, startB)
            }
            private var lastGpsAlong = Float.NaN
            private var lastGpsSeg = 0

            fun progress(geo: GeoPoint, headingDeg: Float?, speedMs: Float): Float? {
                val circuit = gpsCircuit ?: return null
                val projected = circuit.project(
                    geo = geo,
                    headingDeg = headingDeg,
                    speedMs = speedMs,
                    lastSeg = lastGpsSeg,
                    lastAlong = lastGpsAlong
                ) ?: return null
                lastGpsSeg = projected.segmentIndex
                lastGpsAlong = projected.along
                return wrap01((projected.along - circuit.startAlong) / circuit.length)
            }
        }

        private fun pointAtProgress(progress: Float): PointF {
            val traveled = (if (reverseProgress) 1f - progress else progress) * centerlineLength
            return pointAtDistance((startDistance + traveled).mod(centerlineLength))
        }

        private fun pointAtDistance(distance: Float): PointF {
            if (centerline.size < 2) return startPoint ?: PointF(bounds.centerX(), bounds.centerY())
            var remaining = distance.coerceAtLeast(0f)
            for (index in 0 until centerline.lastIndex) {
                val start = centerline[index]
                val end = centerline[index + 1]
                val segment = hypot(end.x - start.x, end.y - start.y)
                if (remaining <= segment) {
                    val t = if (segment <= 0.001f) 0f else remaining / segment
                    return PointF(start.x + (end.x - start.x) * t, start.y + (end.y - start.y) * t)
                }
                remaining -= segment
            }
            return centerline.last()
        }

        private fun mapSvgPoint(point: PointF, target: RectF): PointF {
            val mapped = floatArrayOf(point.x, point.y)
            mapMatrix(target).mapPoints(mapped)
            return PointF(mapped[0], mapped[1])
        }

        private fun mapMatrix(target: RectF): Matrix {
            val matrix = Matrix()
            matrix.setRectToRect(bounds, target, Matrix.ScaleToFit.CENTER)
            return matrix
        }
    }

    private data class Projection(
        val segmentIndex: Int,
        val along: Float,
        val distance: Float
    )

    private class GpsCircuit(
        points: List<GeoPoint>,
        startA: GeoPoint?,
        startB: GeoPoint?
    ) {
        private val origin = points.first()
        private val latScale = 111_320.0
        private val lonScale = cos(Math.toRadians(origin.latitude)).coerceAtLeast(0.15) * 111_320.0
        private val local = points.map { point ->
            PointF(
                ((point.longitude - origin.longitude) * lonScale).toFloat(),
                ((point.latitude - origin.latitude) * latScale).toFloat()
            )
        }
        private val cumulative: FloatArray
        val length: Float
        val startAlong: Float

        init {
            val values = FloatArray(local.size)
            var total = 0f
            values[0] = 0f
            for (index in 0 until local.lastIndex) {
                total += hypot(local[index + 1].x - local[index].x, local[index + 1].y - local[index].y)
                values[index + 1] = total
            }
            cumulative = values
            length = total
            val hint = when {
                startA != null && startB != null -> GeoPoint(
                    (startA.latitude + startB.latitude) / 2.0,
                    (startA.longitude + startB.longitude) / 2.0
                )
                startA != null -> startA
                else -> points.first()
            }
            startAlong = closestAlong(toLocal(hint))
        }

        fun project(
            geo: GeoPoint,
            headingDeg: Float?,
            speedMs: Float,
            lastSeg: Int,
            lastAlong: Float
        ): Projection? {
            if (local.size < 2 || length < 20f) return null
            val point = toLocal(geo)
            val nSeg = local.lastIndex
            val searchAll = !lastAlong.isFinite()
            val lookMeters = (70f + speedMs * 1.8f).coerceIn(70f, 220f)
            var best: Projection? = null
            for (index in 0 until nSeg) {
                val start = local[index]
                val end = local[index + 1]
                val dx = end.x - start.x
                val dy = end.y - start.y
                val segLen = hypot(dx, dy)
                val lengthSq = dx * dx + dy * dy
                if (lengthSq <= 0.0001f) continue
                val along0 = cumulative[index]
                val inWindow = searchAll || circularDelta(along0, lastAlong, length) <= lookMeters
                if (!inWindow) continue
                val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSq).coerceIn(0f, 1f)
                val px = start.x + dx * t
                val py = start.y + dy * t
                var distance = hypot(point.x - px, point.y - py)
                if (headingDeg != null && speedMs > 3f && segLen > 0.5f) {
                    val segDeg = Math.toDegrees(atan2(dx, dy).toDouble()).toFloat().let { if (it < 0f) it + 360f else it }
                    var diff = abs(headingDeg - segDeg) % 360f
                    if (diff > 180f) diff = 360f - diff
                    distance += (diff / 90f) * 18f
                }
                if (best == null || distance < best.distance) {
                    best = Projection(index, along0 + segLen * t, distance)
                }
            }
            val maxSnap = if (searchAll) 90f else 55f
            return best?.takeIf { it.distance <= maxSnap }
        }

        private fun closestAlong(point: PointF): Float {
            var bestAlong = 0f
            var bestDistance = Float.MAX_VALUE
            for (index in 0 until local.lastIndex) {
                val start = local[index]
                val end = local[index + 1]
                val dx = end.x - start.x
                val dy = end.y - start.y
                val lengthSq = dx * dx + dy * dy
                val segLen = hypot(dx, dy)
                val t = if (lengthSq <= 0.0001f) {
                    0f
                } else {
                    (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSq).coerceIn(0f, 1f)
                }
                val px = start.x + dx * t
                val py = start.y + dy * t
                val distance = hypot(point.x - px, point.y - py)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestAlong = cumulative[index] + segLen * t
                }
            }
            return bestAlong
        }

        private fun toLocal(point: GeoPoint): PointF {
            return PointF(
                ((point.longitude - origin.longitude) * lonScale).toFloat(),
                ((point.latitude - origin.latitude) * latScale).toFloat()
            )
        }

        private fun circularDelta(a: Float, b: Float, total: Float): Float {
            val delta = abs(a - b)
            return min(delta, total - delta)
        }
    }

    private val cache = mutableMapOf<String, Outline?>()
    private val pathTagRegex = Regex("""<path\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val classRegex = Regex("""\bclass\s*=\s*(['"])(.*?)\1""", RegexOption.IGNORE_CASE)
    private val dRegex = Regex("""\bd\s*=\s*(['"])(.*?)\1""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val matrixRegex = Regex("""transform="matrix\(([^)]+)\)"""", RegexOption.IGNORE_CASE)

    fun createLocator(
        context: Context,
        trackId: String,
        extraPath: List<GeoPoint> = emptyList()
    ): Outline.Locator? {
        val outline = load(context, trackId) ?: return null
        val definition = OfficialTrackCatalog.tracks.firstOrNull { it.id == trackId.trim() }
        val gate = definition?.startFinishGate ?: definition?.startGate
        return outline.createLocator(resolveHudGpsPath(context, trackId, extraPath), gate?.start, gate?.end)
    }

    private fun resolveHudGpsPath(
        context: Context,
        trackId: String,
        extraPath: List<GeoPoint>
    ): List<GeoPoint> {
        val gpx = runCatching {
            TrackManager(context).loadTrackData(trackId)?.trackPoints?.map { it.geoPoint }.orEmpty()
        }.getOrDefault(emptyList())
        val stored = runCatching {
            com.revix.app.TrackMiniMapShapeResolver(context).resolveMiniMapPoints(
                trackId = trackId,
                orderedLaps = emptyList(),
                routeFallback = extraPath,
                isCircuit = true
            )
        }.getOrDefault(emptyList())
        return listOf(extraPath, gpx, stored).firstOrNull(::isDenseCircuit).orEmpty()
    }

    private fun isDenseCircuit(points: List<GeoPoint>): Boolean {
        if (points.size < 25) return false
        var length = 0.0
        for (index in 1 until points.size) {
            length += points[index - 1].distanceToAsDouble(points[index])
        }
        if (length < 200.0) return false
        return length / (points.size - 1) <= 60.0
    }

    fun load(context: Context, trackId: String): Outline? {
        val normalized = trackId.trim()
        if (normalized.isEmpty()) return null
        if (cache.containsKey(normalized)) return cache[normalized]

        val assetPath = OfficialTrackSvgAssets.assetPathFor(normalized)
        val definition = OfficialTrackCatalog.tracks.firstOrNull { it.id == normalized }
        val outline = if (assetPath == null) {
            null
        } else {
            runCatching {
                context.assets.open(assetPath).bufferedReader().use { it.readText() }
            }.getOrNull()?.let { svg ->
                parseOutline(
                    rawSvg = svg,
                    trackLengthMeters = ((definition?.lengthKm ?: 0.0) * 1000.0).toFloat()
                )
            }
        }
        cache[normalized] = outline
        return outline
    }

    private fun parseOutline(rawSvg: String, trackLengthMeters: Float): Outline? {
        val combined = Path()
        combined.fillType = Path.FillType.EVEN_ODD
        pathTagRegex.findAll(rawSvg).forEach { match ->
            val attrs = match.groupValues.getOrNull(1).orEmpty()
            val classes = classRegex.find(attrs)?.groupValues?.getOrNull(2).orEmpty()
            if (!classes.split(Regex("""\s+""")).any { it.equals("asp", ignoreCase = true) }) {
                return@forEach
            }
            val data = dRegex.find(attrs)?.groupValues?.getOrNull(2)?.trim().orEmpty()
            if (data.isEmpty()) return@forEach
            val parsed = runCatching { PathParser.createPathFromPathData(data) }.getOrNull()
            if (parsed != null && !parsed.isEmpty) {
                combined.addPath(parsed)
            }
        }
        if (combined.isEmpty) return null
        val bounds = RectF()
        combined.computeBounds(bounds, true)
        if (bounds.width() < 4f || bounds.height() < 4f) return null
        val pad = maxOf(bounds.width(), bounds.height()) * 0.04f
        bounds.inset(-pad, -pad)

        val flag = parseStartFlag(rawSvg)
        val centerline = flattenLongestContour(combined)
        val centerlineLength = polylineLength(centerline)
        val startPoint = flag?.center ?: centerline.firstOrNull()
        val startDistance = startPoint?.let { closestDistance(centerline, it) } ?: 0f

        return Outline(
            sourcePath = combined,
            bounds = bounds,
            startPoint = startPoint,
            startAxis = flag?.axis,
            trackLengthMeters = trackLengthMeters,
            centerline = centerline,
            centerlineLength = centerlineLength,
            startDistance = startDistance,
            reverseProgress = false
        )
    }

    private data class StartFlag(
        val center: PointF,
        val axis: PointF
    )

    private fun parseStartFlag(rawSvg: String): StartFlag? {
        val values = matrixRegex.findAll(rawSvg).lastOrNull()
            ?.groupValues
            ?.getOrNull(1)
            ?.split(',')
            ?.mapNotNull { it.trim().toFloatOrNull() }
            ?: return null
        if (values.size < 6) return null
        val a = values[0]
        val b = values[1]
        val c = values[2]
        val d = values[3]
        val e = values[4]
        val f = values[5]
        return StartFlag(
            center = PointF(a * 120f + c * 20f + e, b * 120f + d * 20f + f),
            axis = PointF(a * 240f, b * 240f)
        )
    }

    private fun flattenLongestContour(path: Path): List<PointF> {
        val measure = PathMeasure(path, false)
        var best = emptyList<PointF>()
        var bestLength = 0f
        do {
            val length = measure.length
            if (length > bestLength && length > 4f) {
                bestLength = length
                val steps = min(420, maxOf(64, (length / 2f).toInt()))
                val points = ArrayList<PointF>(steps + 1)
                val pos = FloatArray(2)
                for (index in 0..steps) {
                    measure.getPosTan(length * index / steps.toFloat(), pos, null)
                    points.add(PointF(pos[0], pos[1]))
                }
                best = points
            }
        } while (measure.nextContour())
        return best
    }

    private fun polylineLength(points: List<PointF>): Float {
        var length = 0f
        for (index in 0 until points.lastIndex) {
            length += hypot(points[index + 1].x - points[index].x, points[index + 1].y - points[index].y)
        }
        return length
    }

    private fun closestDistance(points: List<PointF>, target: PointF): Float {
        if (points.size < 2) return 0f
        var bestDistance = Float.MAX_VALUE
        var bestAlong = 0f
        var along = 0f
        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]
            val dx = end.x - start.x
            val dy = end.y - start.y
            val segment = hypot(dx, dy)
            val lengthSq = dx * dx + dy * dy
            val t = if (lengthSq <= 0.0001f) {
                0f
            } else {
                (((target.x - start.x) * dx + (target.y - start.y) * dy) / lengthSq).coerceIn(0f, 1f)
            }
            val px = start.x + dx * t
            val py = start.y + dy * t
            val distance = hypot(target.x - px, target.y - py)
            if (distance < bestDistance) {
                bestDistance = distance
                bestAlong = along + segment * t
            }
            along += segment
        }
        return bestAlong
    }

    private fun tangentAt(points: List<PointF>, distance: Float): PointF? {
        if (points.size < 2) return null
        var remaining = distance.coerceAtLeast(0f)
        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]
            val segment = hypot(end.x - start.x, end.y - start.y)
            if (remaining <= segment || index == points.lastIndex - 1) {
                val length = segment.coerceAtLeast(0.001f)
                return PointF((end.x - start.x) / length, (end.y - start.y) / length)
            }
            remaining -= segment
        }
        return null
    }

    private fun wrap01(progress: Float): Float {
        if (!progress.isFinite()) return 0f
        var value = progress - kotlin.math.floor(progress.toDouble()).toFloat()
        if (value < 0f) value += 1f
        return value
    }

    private fun Float.mod(modulus: Float): Float {
        if (modulus <= 0f) return 0f
        var value = this % modulus
        if (value < 0f) value += modulus
        return value
    }
}
