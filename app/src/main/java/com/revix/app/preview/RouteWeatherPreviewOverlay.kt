package com.revix.app.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.content.ContextCompat
import com.revix.app.R
import com.revix.app.network.WeatherApiService
import com.revix.app.utils.WeatherIconMapper
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.navigation.base.route.NavigationRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class RouteWeatherPreviewOverlay(
    private val context: Context,
    private val mapView: MapView,
    private val weatherApiKey: String,
    private val coroutineScope: CoroutineScope,
    private val sampleCount: Int = 5
) {
    private val weatherApiService: WeatherApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.weatherapi.com/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }

    private var pointAnnotationManager: PointAnnotationManager? = null
    private val annotations = mutableListOf<PointAnnotation>()
    private var job: Job? = null
    private val cache = mutableMapOf<String, WeatherMarkerData>()

    private enum class PointerSide { TOP, RIGHT, BOTTOM, LEFT }

    fun showForRoute(route: NavigationRoute) {
        clear()
        val geometry = route.directionsRoute.geometry().orEmpty()
        if (geometry.isBlank()) return

        val coordinates = try {
            LineString.fromPolyline(geometry, 6).coordinates()
        } catch (_: Exception) {
            try {
                LineString.fromPolyline(geometry, 5).coordinates()
            } catch (_: Exception) {
                emptyList()
            }
        }
        if (coordinates.size < 2) return

        val durationSeconds = route.directionsRoute.duration() ?: 0.0
        val distanceMeters = route.directionsRoute.distance() ?: 0.0
        val adaptiveCount = determineSampleCount(distanceMeters)
        if (adaptiveCount == 0) return
        val nowMillis = System.currentTimeMillis()
        val samplePoints = computeSamplePoints(coordinates, adaptiveCount)
        if (samplePoints.isEmpty()) return

        job = coroutineScope.launch {
            val manager = getOrCreateManager()
            val newAnnotations = mutableListOf<PointAnnotation>()
            for (sample in samplePoints) {
                // Pill stays upright; triangle moves so it always points back at the route.
                // Tip anchored on the route; body extends left-of-travel.
                // iconOffset is in ems (× icon size), NOT pixels — keep tiny.
                val pointerSide = pointerSideForBearing(sample.bearing + 90.0)
                val anchor = iconAnchorFor(pointerSide)
                val iconOffset = tipClearanceOffsetEms(pointerSide, gapEms = 0.12)

                val etaMillis = nowMillis + (durationSeconds * sample.fraction * 1000.0).toLong()
                val cacheKey = buildCacheKey(sample.point, etaMillis)
                val data = cache[cacheKey] ?: fetchWeather(sample.point, etaMillis)?.also {
                    cache[cacheKey] = it
                }
                if (data != null) {
                    val bitmap = createMarkerBitmap(data, pointerSide)
                    val options = PointAnnotationOptions()
                        .withPoint(sample.point)
                        .withIconImage(bitmap)
                        .withIconAnchor(anchor)
                        .withIconOffset(iconOffset)
                    val annotation = manager.create(options)
                    newAnnotations.add(annotation)
                }
            }
            annotations.addAll(newAnnotations)
        }
    }

    fun clear() {
        job?.cancel()
        job = null
        pointAnnotationManager?.let { manager ->
            if (annotations.isNotEmpty()) {
                manager.delete(annotations)
            }
        }
        annotations.clear()
    }

    private fun getOrCreateManager(): PointAnnotationManager {
        val existing = pointAnnotationManager
        if (existing != null) return existing
        val manager = mapView.annotations.createPointAnnotationManager()
        pointAnnotationManager = manager
        return manager
    }

    private fun pointerSideForBearing(bearingDegrees: Double): PointerSide {
        val b = ((bearingDegrees % 360.0) + 360.0) % 360.0
        return when {
            b >= 315.0 || b < 45.0 -> PointerSide.TOP
            b < 135.0 -> PointerSide.RIGHT
            b < 225.0 -> PointerSide.BOTTOM
            else -> PointerSide.LEFT
        }
    }

    private fun iconAnchorFor(side: PointerSide): IconAnchor = when (side) {
        PointerSide.TOP -> IconAnchor.TOP
        PointerSide.RIGHT -> IconAnchor.RIGHT
        PointerSide.BOTTOM -> IconAnchor.BOTTOM
        PointerSide.LEFT -> IconAnchor.LEFT
    }

    /** Nudge tip slightly off the orange stroke. Values are Mapbox ems (× icon size). */
    private fun tipClearanceOffsetEms(side: PointerSide, gapEms: Double): List<Double> = when (side) {
        PointerSide.BOTTOM -> listOf(0.0, -gapEms)
        PointerSide.TOP -> listOf(0.0, gapEms)
        PointerSide.RIGHT -> listOf(-gapEms, 0.0)
        PointerSide.LEFT -> listOf(gapEms, 0.0)
    }

    private suspend fun fetchWeather(point: Point, etaMillis: Long): WeatherMarkerData? {
        return withContext(Dispatchers.IO) {
            try {
                val response = weatherApiService.getCurrentWeather(
                    apiKey = weatherApiKey,
                    location = "${point.latitude()},${point.longitude()}",
                    lang = "bg"
                )
                if (!response.isSuccessful) return@withContext null
                val body = response.body() ?: return@withContext null

                val hours = body.forecast?.forecastday?.firstOrNull()?.hour.orEmpty()
                val targetHour = hours
                    .mapNotNull { hour ->
                        parseHourMillis(hour.time)?.let { millis ->
                            HourEntry(hour.temp_c, hour.condition.code, millis)
                        }
                    }
                    .minByOrNull { entry -> abs(entry.timeMillis - etaMillis) }

                val tempC = targetHour?.tempC ?: body.current.temp_c
                val conditionCode = targetHour?.conditionCode ?: body.current.condition.code
                val isDay = body.current.is_day == 1
                val cloudCover = body.current.cloud
                val iconRes = WeatherIconMapper.getWeatherApiIcon(conditionCode, cloudCover, isDay)

                WeatherMarkerData(
                    tempC = tempC.toInt(),
                    iconRes = iconRes,
                    timeText = formatTime(etaMillis)
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun createMarkerBitmap(data: WeatherMarkerData, pointerSide: PointerSide): Bitmap {
        val density = context.resources.displayMetrics.density
        val scaledDensity = context.resources.displayMetrics.scaledDensity
        val padding = 4f * density
        val iconSize = 18f * density
        val textSize = 12f * scaledDensity
        val timeSize = 10f * scaledDensity
        val pointerHeight = 10f * density
        val pointerHalfWidth = 7f * density
        val tempText = "${data.tempC}°"
        val timeText = data.timeText

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            isFakeBoldText = true
        }
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E0E0")
            this.textSize = timeSize
            isFakeBoldText = true
        }
        val tempWidth = textPaint.measureText(tempText)
        val timeWidth = timePaint.measureText(timeText)
        val tempRect = android.graphics.Rect()
        val timeRect = android.graphics.Rect()
        textPaint.getTextBounds(tempText, 0, tempText.length, tempRect)
        timePaint.getTextBounds(timeText, 0, timeText.length, timeRect)
        val tempHeight = tempRect.height().toFloat()
        val timeHeight = timeRect.height().toFloat()

        val blockGap = 6f * density
        val tempTopMargin = 2f * density
        val iconBottomMargin = 2f * density
        val contentWidth = max(iconSize, max(tempWidth, timeWidth))
        val contentHeight = tempTopMargin + tempHeight + blockGap + timeHeight + blockGap + iconSize + iconBottomMargin
        val bubbleWidth = padding * 2 + contentWidth
        val bubbleHeight = padding * 2 + contentHeight

        val borderWidth = 1.25f * density
        val borderPad = borderWidth
        val horizontalPointer = pointerSide == PointerSide.LEFT || pointerSide == PointerSide.RIGHT
        val contentW = if (horizontalPointer) bubbleWidth + pointerHeight else bubbleWidth
        val contentH = if (horizontalPointer) bubbleHeight else bubbleHeight + pointerHeight
        val width = contentW + borderPad * 2f
        val height = contentH + borderPad * 2f

        val bubbleLeft = borderPad + if (pointerSide == PointerSide.LEFT) pointerHeight else 0f
        val bubbleTop = borderPad + if (pointerSide == PointerSide.TOP) pointerHeight else 0f

        val bitmap = Bitmap.createBitmap(
            max(1, width.toInt()),
            max(1, height.toInt()),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CC000000")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = borderWidth
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val radius = bubbleHeight / 2f
        val bubbleRect = RectF(
            bubbleLeft,
            bubbleTop,
            bubbleLeft + bubbleWidth,
            bubbleTop + bubbleHeight
        )
        canvas.drawRoundRect(bubbleRect, radius, radius, bgPaint)

        val pointerPath = Path()
        when (pointerSide) {
            PointerSide.BOTTOM -> {
                val tipX = bubbleLeft + bubbleWidth / 2f
                val baseY = bubbleTop + bubbleHeight - 1f * density
                pointerPath.moveTo(tipX - pointerHalfWidth, baseY)
                pointerPath.lineTo(tipX + pointerHalfWidth, baseY)
                pointerPath.lineTo(tipX, height - borderPad)
                pointerPath.close()
            }
            PointerSide.TOP -> {
                val tipX = bubbleLeft + bubbleWidth / 2f
                val baseY = bubbleTop + 1f * density
                pointerPath.moveTo(tipX - pointerHalfWidth, baseY)
                pointerPath.lineTo(tipX + pointerHalfWidth, baseY)
                pointerPath.lineTo(tipX, borderPad)
                pointerPath.close()
            }
            PointerSide.RIGHT -> {
                val tipY = bubbleTop + bubbleHeight / 2f
                val baseX = bubbleLeft + bubbleWidth - 1f * density
                pointerPath.moveTo(baseX, tipY - pointerHalfWidth)
                pointerPath.lineTo(baseX, tipY + pointerHalfWidth)
                pointerPath.lineTo(width - borderPad, tipY)
                pointerPath.close()
            }
            PointerSide.LEFT -> {
                val tipY = bubbleTop + bubbleHeight / 2f
                val baseX = bubbleLeft + 1f * density
                pointerPath.moveTo(baseX, tipY - pointerHalfWidth)
                pointerPath.lineTo(baseX, tipY + pointerHalfWidth)
                pointerPath.lineTo(borderPad, tipY)
                pointerPath.close()
            }
        }
        canvas.drawPath(pointerPath, bgPaint)
        canvas.drawRoundRect(bubbleRect, radius, radius, borderPaint)
        // Stroke only the outer edges of the pointer (skip base seam into the bubble).
        when (pointerSide) {
            PointerSide.BOTTOM -> {
                val tipX = bubbleLeft + bubbleWidth / 2f
                val baseY = bubbleTop + bubbleHeight - 1f * density
                val tipY = height - borderPad
                canvas.drawLine(tipX - pointerHalfWidth, baseY, tipX, tipY, borderPaint)
                canvas.drawLine(tipX + pointerHalfWidth, baseY, tipX, tipY, borderPaint)
            }
            PointerSide.TOP -> {
                val tipX = bubbleLeft + bubbleWidth / 2f
                val baseY = bubbleTop + 1f * density
                val tipY = borderPad
                canvas.drawLine(tipX - pointerHalfWidth, baseY, tipX, tipY, borderPaint)
                canvas.drawLine(tipX + pointerHalfWidth, baseY, tipX, tipY, borderPaint)
            }
            PointerSide.RIGHT -> {
                val tipY = bubbleTop + bubbleHeight / 2f
                val baseX = bubbleLeft + bubbleWidth - 1f * density
                val tipX = width - borderPad
                canvas.drawLine(baseX, tipY - pointerHalfWidth, tipX, tipY, borderPaint)
                canvas.drawLine(baseX, tipY + pointerHalfWidth, tipX, tipY, borderPaint)
            }
            PointerSide.LEFT -> {
                val tipY = bubbleTop + bubbleHeight / 2f
                val baseX = bubbleLeft + 1f * density
                val tipX = borderPad
                canvas.drawLine(baseX, tipY - pointerHalfWidth, tipX, tipY, borderPaint)
                canvas.drawLine(baseX, tipY + pointerHalfWidth, tipX, tipY, borderPaint)
            }
        }

        val iconRes = if (data.iconRes != 0) data.iconRes else R.drawable.ic_thermometer
        val icon = ContextCompat.getDrawable(context, iconRes)?.mutate()
        val contentLeft = bubbleLeft + (bubbleWidth - contentWidth) / 2f
        val contentTop = bubbleTop + (bubbleHeight - contentHeight) / 2f
        val tempX = bubbleLeft + (bubbleWidth - tempWidth) / 2f
        val tempTop = contentTop + tempTopMargin
        val tempBaseline = tempTop - tempRect.top
        canvas.drawText(tempText, tempX, tempBaseline, textPaint)

        val timeX = bubbleLeft + (bubbleWidth - timeWidth) / 2f
        val timeTop = tempTop + tempHeight + blockGap
        val timeBaseline = timeTop - timeRect.top
        canvas.drawText(timeText, timeX, timeBaseline, timePaint)

        val iconLeft = contentLeft + (contentWidth - iconSize) / 2f
        val iconTop = timeTop + timeHeight + blockGap
        icon?.setBounds(
            iconLeft.toInt(),
            iconTop.toInt(),
            (iconLeft + iconSize).toInt(),
            (iconTop + iconSize).toInt()
        )
        icon?.setTint(Color.WHITE)
        icon?.draw(canvas)

        return bitmap
    }

    private fun computeSamplePoints(points: List<Point>, count: Int): List<RouteSample> {
        if (points.size < 2 || count <= 0) return emptyList()

        val distances = DoubleArray(points.size) { 0.0 }
        var total = 0.0
        for (i in 1 until points.size) {
            total += distanceMeters(points[i - 1], points[i])
            distances[i] = total
        }
        if (total <= 0.0) return emptyList()

        val result = mutableListOf<RouteSample>()
        for (i in 1..count) {
            val target = total * (i.toDouble() / (count + 1))
            val fraction = target / total
            val samplePoint = interpolateAtDistance(points, distances, target)
            result.add(samplePoint.copy(fraction = fraction))
        }
        return result
    }

    private fun interpolateAtDistance(
        points: List<Point>,
        distances: DoubleArray,
        target: Double
    ): RouteSample {
        for (i in 1 until points.size) {
            if (distances[i] >= target) {
                val prevDist = distances[i - 1]
                val segDist = max(distances[i] - prevDist, 0.0001)
                val t = ((target - prevDist) / segDist).coerceIn(0.0, 1.0)
                val a = points[i - 1]
                val b = points[i]
                val lat = a.latitude() + (b.latitude() - a.latitude()) * t
                val lon = a.longitude() + (b.longitude() - a.longitude()) * t
                val bearing = bearingDegrees(a, b)
                return RouteSample(Point.fromLngLat(lon, lat), 0.0, bearing)
            }
        }
        val last = points.last()
        val prev = points.getOrNull(points.size - 2) ?: last
        return RouteSample(last, 0.0, bearingDegrees(prev, last))
    }

    private fun bearingDegrees(a: Point, b: Point): Double {
        val lat1 = Math.toRadians(a.latitude())
        val lat2 = Math.toRadians(b.latitude())
        val dLon = Math.toRadians(b.longitude() - a.longitude())
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val bearing = Math.toDegrees(kotlin.math.atan2(y, x))
        return (bearing + 360.0) % 360.0
    }

    private fun distanceMeters(a: Point, b: Point): Double {
        val lat1 = Math.toRadians(a.latitude())
        val lat2 = Math.toRadians(b.latitude())
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude() - a.longitude())
        val sinLat = sin(dLat / 2)
        val sinLon = sin(dLon / 2)
        val h = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon
        val c = 2 * kotlin.math.atan2(sqrt(h), sqrt(1 - h))
        return 6371000.0 * c
    }

    private fun parseHourMillis(timeText: String): Long? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            sdf.isLenient = false
            sdf.parse(timeText)?.time
        } catch (_: Exception) {
            null
        }
    }

    private fun formatTime(timeMillis: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(java.util.Date(timeMillis))
    }

    private fun buildCacheKey(point: Point, etaMillis: Long): String {
        val hourBucket = etaMillis / (60 * 60 * 1000)
        return "${point.latitude()},${point.longitude()}_$hourBucket"
    }

    private fun determineSampleCount(distanceMeters: Double): Int {
        return when {
            distanceMeters < 5000 -> 0
            distanceMeters < 20000 -> 1
            distanceMeters < 50000 -> 2
            distanceMeters < 100000 -> 3
            distanceMeters < 200000 -> 4
            else -> sampleCount
        }
    }

    private data class RouteSample(val point: Point, val fraction: Double, val bearing: Double)

    private data class HourEntry(val tempC: Double, val conditionCode: Int, val timeMillis: Long)

    private data class WeatherMarkerData(val tempC: Int, val iconRes: Int, val timeText: String)
}
