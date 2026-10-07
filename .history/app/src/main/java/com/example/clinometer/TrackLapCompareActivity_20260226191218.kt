package com.example.clinometer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.example.clinometer.settings.LanguageManager
import com.example.clinometer.settings.UnitsManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.google.gson.Gson
import com.mapbox.geojson.Point as MapboxPoint
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView as MapboxMapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.scalebar.scalebar
import kotlin.math.abs
import kotlin.math.roundToInt

class TrackLapCompareActivity : AppCompatActivity() {

    private data class LapStats(
        val lapDurationSec: Float,
        val maxSpeedKmh: Float,
        val avgSpeedKmh: Float,
        val distanceKm: Double
    )

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private data class Bounds(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    )

    private lateinit var chart: LineChart
    private lateinit var tvTitle: TextView
    private lateinit var tvCurrentValue: TextView
    private lateinit var tvCompareValue: TextView
    private lateinit var tvStatLapCurrent: TextView
    private lateinit var tvStatLapCompare: TextView
    private lateinit var tvStatMaxCurrent: TextView
    private lateinit var tvStatMaxCompare: TextView
    private lateinit var tvStatAvgCurrent: TextView
    private lateinit var tvStatAvgCompare: TextView
    private lateinit var tvStatDistCurrent: TextView
    private lateinit var tvStatDistCompare: TextView

    private var mapboxMapView: MapboxMapView? = null
    private var pointAnnotationManager: PointAnnotationManager? = null
    private var polylineAnnotationManager: PolylineAnnotationManager? = null
    private var currentMarker: PointAnnotation? = null
    private var compareMarker: PointAnnotation? = null

    private var currentRoute: List<RoutePoint> = emptyList()
    private var compareRoute: List<RoutePoint> = emptyList()

    private var currentSessionId: String = ""
    private var compareSessionId: String = ""
    private var currentOutingNumber: Int = 1
    private var compareOutingNumber: Int = 1
    private var currentLapNumber: Int = 1
    private var compareLapNumber: Int = 1

    private var maxDurationSec: Float = 0f
    private var currentReaderTimeSec: Float = 0f
    private var isChartZooming = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_lap_compare)
        applySystemBarsPaddingToRoot()

        bindViews()
        readExtras()

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        if (!loadRoutes()) {
            Toast.makeText(this, "Няма lap данни за сравнение", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupMapboxMap()
        setupChart()
        populateStatsComparison()
        updateByTime(0f)
    }

    private fun bindViews() {
        chart = findViewById(R.id.chartCompare)
        tvTitle = findViewById(R.id.tvTitle)
        tvCurrentValue = findViewById(R.id.tvCurrentValue)
        tvCompareValue = findViewById(R.id.tvCompareValue)
        tvStatLapCurrent = findViewById(R.id.tvStatLapCurrent)
        tvStatLapCompare = findViewById(R.id.tvStatLapCompare)
        tvStatMaxCurrent = findViewById(R.id.tvStatMaxCurrent)
        tvStatMaxCompare = findViewById(R.id.tvStatMaxCompare)
        tvStatAvgCurrent = findViewById(R.id.tvStatAvgCurrent)
        tvStatAvgCompare = findViewById(R.id.tvStatAvgCompare)
        tvStatDistCurrent = findViewById(R.id.tvStatDistCurrent)
        tvStatDistCompare = findViewById(R.id.tvStatDistCompare)
    }

    private fun readExtras() {
        currentSessionId = intent.getStringExtra("current_session_id") ?: ""
        compareSessionId = intent.getStringExtra("compare_session_id") ?: ""
        currentOutingNumber = intent.getIntExtra("current_outing_number", 1)
        compareOutingNumber = intent.getIntExtra("compare_outing_number", 1)
        currentLapNumber = intent.getIntExtra("current_lap_number", 1)
        compareLapNumber = intent.getIntExtra("compare_lap_number", 1)
        val trackName = intent.getStringExtra("track_name") ?: "Track"

        tvTitle.text = "$trackName • Compare"
    }

    private fun loadRoutes(): Boolean {
        val prefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val gson = Gson()

        val currentJson = prefs.getString("${currentSessionId}_outing_${currentOutingNumber}_lap_data_${currentLapNumber}", null)
        val compareJson = prefs.getString("${compareSessionId}_outing_${compareOutingNumber}_lap_data_${compareLapNumber}", null)

        if (currentJson.isNullOrBlank() || compareJson.isNullOrBlank()) return false

        val currentLapData = try {
            gson.fromJson(currentJson, LapData::class.java)
        } catch (_: Exception) {
            null
        } ?: return false

        val compareLapData = try {
            gson.fromJson(compareJson, LapData::class.java)
        } catch (_: Exception) {
            null
        } ?: return false

        currentRoute = normalizeRoute(currentLapData.routePoints)
        compareRoute = normalizeRoute(compareLapData.routePoints)

        if (currentRoute.size < 2 || compareRoute.size < 2) return false

        val currentDuration = (currentRoute.last().timestamp / 1000f).coerceAtLeast(0.1f)
        val compareDuration = (compareRoute.last().timestamp / 1000f).coerceAtLeast(0.1f)
        maxDurationSec = maxOf(currentDuration, compareDuration)

        return true
    }

    private fun normalizeRoute(points: List<RoutePoint>): List<RoutePoint> {
        if (points.isEmpty()) return emptyList()
        val baseTs = points.first().timestamp
        return points.map { it.copy(timestamp = it.timestamp - baseTs) }
    }

    private fun setupMapboxMap() {
        val mapContainer = findViewById<FrameLayout>(R.id.mapContainer)
        val osmdroidMap = mapContainer.findViewById<android.view.View>(R.id.mapCompare)
        if (osmdroidMap != null) {
            mapContainer.removeView(osmdroidMap)
        }

        mapboxMapView = MapboxMapView(this)
        mapContainer.addView(mapboxMapView)

        mapboxMapView?.scalebar?.enabled = false
        mapboxMapView?.compass?.enabled = false
        mapboxMapView?.attribution?.enabled = false

        val allGeoPoints = (currentRoute + compareRoute).map { it.geoPoint }
        val bounds = calculateBounds(allGeoPoints)
        mapboxMapView?.mapboxMap?.setCamera(
            CameraOptions.Builder()
                .center(MapboxPoint.fromLngLat((bounds.minLon + bounds.maxLon) / 2.0, (bounds.minLat + bounds.maxLat) / 2.0))
                .zoom(calculateZoomLevel(bounds))
                .build()
        )

        mapboxMapView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> view.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        val styleUri = "mapbox://styles/djsookz/cmiyer8iu000101s84wqsav5l"
        mapboxMapView?.mapboxMap?.loadStyleUri(styleUri) { _ ->
            setupMapboxAnnotations()
            drawStaticRoutes()
            createMovingMarkers()
            updateByTime(currentReaderTimeSec)
        }
    }

    private fun setupMapboxAnnotations() {
        val annotationApi = mapboxMapView?.annotations ?: return
        polylineAnnotationManager = annotationApi.createPolylineAnnotationManager()
        pointAnnotationManager = annotationApi.createPointAnnotationManager()
    }

    private fun drawStaticRoutes() {
        val polyManager = polylineAnnotationManager ?: return
        polyManager.deleteAll()

        val currentPoints = currentRoute.map { MapboxPoint.fromLngLat(it.geoPoint.longitude, it.geoPoint.latitude) }
        val comparePoints = compareRoute.map { MapboxPoint.fromLngLat(it.geoPoint.longitude, it.geoPoint.latitude) }

        polyManager.create(
            PolylineAnnotationOptions()
                .withPoints(comparePoints)
                .withLineColor("#A64CEB")
                .withLineWidth(5.5)
        )
        polyManager.create(
            PolylineAnnotationOptions()
                .withPoints(currentPoints)
                .withLineColor("#FC7805")
                .withLineWidth(6.5)
        )
    }

    private fun createMovingMarkers() {
        val pointManager = pointAnnotationManager ?: return
        pointManager.deleteAll()

        val currentStart = currentRoute.first().geoPoint
        val compareStart = compareRoute.first().geoPoint

        val currentBitmap = buildMarkerBitmap(Color.parseColor("#FC7805"))
        val compareBitmap = buildMarkerBitmap(Color.parseColor("#A64CEB"))

        currentMarker = pointManager.create(
            PointAnnotationOptions()
                .withPoint(MapboxPoint.fromLngLat(currentStart.longitude, currentStart.latitude))
                .withIconImage(currentBitmap)
        )

        compareMarker = pointManager.create(
            PointAnnotationOptions()
                .withPoint(MapboxPoint.fromLngLat(compareStart.longitude, compareStart.latitude))
                .withIconImage(compareBitmap)
        )
    }

    private fun setupChart() {
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(false)
        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false
        chart.isDragDecelerationEnabled = false
        chart.dragDecelerationFrictionCoef = 0f

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            axisMinimum = -maxDurationSec
            axisMaximum = maxDurationSec * 2f
            textColor = Color.WHITE
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val sec = abs(value).toLong()
                    val min = sec / 60
                    val remSec = sec % 60
                    return String.format("%02d:%02d", min, remSec)
                }
            }
        }

        chart.axisLeft.apply {
            textColor = Color.WHITE
            axisMinimum = 0f
            val maxSpeed = maxOf(
                currentRoute.maxOfOrNull { it.speed } ?: 0f,
                compareRoute.maxOfOrNull { it.speed } ?: 0f,
                200f
            )
            axisMaximum = maxSpeed * 1.1f
            setDrawZeroLine(true)
            zeroLineColor = Color.GRAY
            zeroLineWidth = 1f
        }

        chart.legend.textColor = Color.WHITE

        val speedUnit = UnitsManager.getSpeedUnit(this)
        val currentEntries = currentRoute.map {
            Entry(it.timestamp / 1000f, UnitsManager.convertSpeed(it.speed, speedUnit))
        }
        val compareEntries = compareRoute.map {
            Entry(it.timestamp / 1000f, UnitsManager.convertSpeed(it.speed, speedUnit))
        }

        val currentDataSet = LineDataSet(currentEntries, "Current").apply {
            color = Color.parseColor("#FC7805")
            lineWidth = 2.6f
            setDrawValues(false)
            setDrawCircles(false)
            mode = LineDataSet.Mode.LINEAR
        }

        val compareDataSet = LineDataSet(compareEntries, "Compare").apply {
            color = Color.parseColor("#A64CEB")
            lineWidth = 2.6f
            setDrawValues(false)
            setDrawCircles(false)
            mode = LineDataSet.Mode.LINEAR
        }

        chart.data = LineData(currentDataSet, compareDataSet)
        chart.setVisibleXRangeMaximum(maxDurationSec)

        chart.renderer = object : com.github.mikephil.charting.renderer.LineChartRenderer(
            chart, chart.animator, chart.viewPortHandler
        ) {
            init {
                mChart = chart
                mAnimator = chart.animator
                mViewPortHandler = chart.viewPortHandler
            }

            override fun drawData(c: Canvas) {
                super.drawData(c)

                val redLinePaint = android.graphics.Paint().apply {
                    color = Color.RED
                    strokeWidth = 3f
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
                }

                val centerX = mViewPortHandler.contentCenter.x
                c.drawLine(
                    centerX,
                    mViewPortHandler.contentTop(),
                    centerX,
                    mViewPortHandler.contentBottom(),
                    redLinePaint
                )

                val timeTextPaint = android.graphics.Paint().apply {
                    color = Color.RED
                    textSize = 30f
                    isAntiAlias = true
                    isFakeBoldText = true
                }

                val centerValue = (mChart.lowestVisibleX + mChart.highestVisibleX) / 2f
                val timeText = formatTimeForReader(centerValue)
                c.drawText(timeText, centerX + 10f, mViewPortHandler.contentTop() + 38f, timeTextPaint)
            }
        }

        val dataStartTime = 0f
        val dataEndTime = maxDurationSec

        chart.setOnTouchListener { _, event ->
            if (!isChartZooming) {
                chart.onTouchEvent(event)

                val currentCenter = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                clampChartCenter(currentCenter, dataStartTime, dataEndTime)
            }

            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                val centerX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                val clampedCenter = centerX.coerceIn(dataStartTime, dataEndTime)
                updateByTime(clampedCenter)
                chart.invalidate()
            }

            true
        }

        chart.invalidate()

        chart.setOnChartGestureListener(object : OnChartGestureListener {
            override fun onChartGestureStart(me: MotionEvent?, lastGesture: ChartTouchListener.ChartGesture?) {}
            override fun onChartGestureEnd(me: MotionEvent?, lastGesture: ChartTouchListener.ChartGesture?) {}
            override fun onChartLongPressed(me: MotionEvent?) {}
            override fun onChartDoubleTapped(me: MotionEvent?) {}
            override fun onChartSingleTapped(me: MotionEvent?) {}
            override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}
            override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {
                isChartZooming = true
            }
            override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
                val centerX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                val clampedCenter = clampChartCenter(centerX, dataStartTime, dataEndTime)
                updateByTime(clampedCenter)
                isChartZooming = false
            }
        })

        chart.post {
            val visibleRange = chart.visibleXRange.coerceAtLeast(1f)
            chart.moveViewToX(dataStartTime - visibleRange / 2f)
            clampChartCenter(dataStartTime, dataStartTime, dataEndTime)
            updateByTime(dataStartTime)
            chart.invalidate()
        }
    }

    private fun clampChartCenter(centerX: Float, start: Float, end: Float): Float {
        val visibleRange = chart.visibleXRange
        return when {
            centerX < start -> {
                chart.moveViewToX(start - visibleRange / 2f)
                chart.isDragEnabled = false
                chart.postDelayed({ chart.isDragEnabled = true }, 1)
                start
            }

            centerX > end -> {
                chart.moveViewToX(end - visibleRange / 2f)
                chart.isDragEnabled = false
                chart.postDelayed({ chart.isDragEnabled = true }, 1)
                end
            }

            else -> centerX
        }
    }

    private fun formatTimeForReader(seconds: Float): String {
        val totalSeconds = seconds.toLong().coerceAtLeast(0)
        val min = totalSeconds / 60
        val sec = totalSeconds % 60
        return String.format("%02d:%02d", min, sec)
    }

    private fun populateStatsComparison() {
        val currentStats = calculateLapStats(currentRoute)
        val compareStats = calculateLapStats(compareRoute)

        tvStatLapCurrent.text = formatLapTime(currentStats.lapDurationSec)
        tvStatLapCompare.text = formatLapTime(compareStats.lapDurationSec)

        tvStatMaxCurrent.text = UnitsManager.formatSpeed(currentStats.maxSpeedKmh, this, 0)
        tvStatMaxCompare.text = UnitsManager.formatSpeed(compareStats.maxSpeedKmh, this, 0)

        tvStatAvgCurrent.text = UnitsManager.formatSpeed(currentStats.avgSpeedKmh, this, 0)
        tvStatAvgCompare.text = UnitsManager.formatSpeed(compareStats.avgSpeedKmh, this, 0)

        tvStatDistCurrent.text = UnitsManager.formatDistance(currentStats.distanceKm, this, 2)
        tvStatDistCompare.text = UnitsManager.formatDistance(compareStats.distanceKm, this, 2)
    }

    private fun calculateLapStats(route: List<RoutePoint>): LapStats {
        if (route.isEmpty()) {
            return LapStats(0f, 0f, 0f, 0.0)
        }

        val durationSec = (route.last().timestamp / 1000f).coerceAtLeast(0f)
        val maxSpeedKmh = route.maxOfOrNull { it.speed } ?: 0f
        val avgSpeedKmh = if (route.isNotEmpty()) route.map { it.speed }.average().toFloat() else 0f

        var distanceMeters = 0.0
        if (route.size > 1) {
            for (index in 1 until route.size) {
                distanceMeters += route[index - 1].geoPoint.distanceToAsDouble(route[index].geoPoint)
            }
        }

        return LapStats(
            lapDurationSec = durationSec,
            maxSpeedKmh = maxSpeedKmh,
            avgSpeedKmh = avgSpeedKmh,
            distanceKm = distanceMeters / 1000.0
        )
    }

    private fun formatLapTime(seconds: Float): String {
        val totalMillis = (seconds * 1000f).roundToInt().coerceAtLeast(0)
        val minutes = totalMillis / 60_000
        val sec = (totalMillis % 60_000) / 1000
        val millis = totalMillis % 1000
        return String.format("%02d:%02d.%03d", minutes, sec, millis)
    }

    private fun updateByTime(timeSec: Float) {
        val targetTime = timeSec.coerceIn(0f, maxDurationSec)
        currentReaderTimeSec = targetTime

        val currentPoint = interpolateRoutePoint(currentRoute, targetTime)
        val comparePoint = interpolateRoutePoint(compareRoute, targetTime)

        val currentSpeedText = UnitsManager.formatSpeed(currentPoint.speed, this, 0)
        val compareSpeedText = UnitsManager.formatSpeed(comparePoint.speed, this, 0)
        tvCurrentValue.text = currentSpeedText
        tvCompareValue.text = compareSpeedText

        val currentMarkerRef = currentMarker
        val compareMarkerRef = compareMarker
        val pointManager = pointAnnotationManager

        if (currentMarkerRef != null && compareMarkerRef != null && pointManager != null) {
            currentMarkerRef.point = MapboxPoint.fromLngLat(currentPoint.geoPoint.longitude, currentPoint.geoPoint.latitude)
            compareMarkerRef.point = MapboxPoint.fromLngLat(comparePoint.geoPoint.longitude, comparePoint.geoPoint.latitude)
            pointManager.update(currentMarkerRef)
            pointManager.update(compareMarkerRef)

            val centerPoint = MapboxPoint.fromLngLat(
                (currentPoint.geoPoint.longitude + comparePoint.geoPoint.longitude) / 2.0,
                (currentPoint.geoPoint.latitude + comparePoint.geoPoint.latitude) / 2.0
            )

            val currentCamera = mapboxMapView?.mapboxMap?.cameraState
            mapboxMapView?.mapboxMap?.setCamera(
                CameraOptions.Builder()
                    .center(centerPoint)
                    .zoom(currentCamera?.zoom ?: 15.5)
                    .build()
            )
        }
    }

    private fun interpolateRoutePoint(route: List<RoutePoint>, targetSec: Float): RoutePoint {
        if (route.isEmpty()) {
            return RoutePoint(GeoPoint(0.0, 0.0), 0f, 0f, 0L, 0L)
        }
        if (route.size == 1) return route.first()

        val targetMs = (targetSec * 1000f).toLong().coerceAtLeast(0L)

        if (targetMs <= route.first().timestamp) return route.first()
        if (targetMs >= route.last().timestamp) return route.last()

        var left = route.first()
        var right = route.last()
        for (index in 0 until route.size - 1) {
            val p1 = route[index]
            val p2 = route[index + 1]
            if (targetMs in p1.timestamp..p2.timestamp) {
                left = p1
                right = p2
                break
            }
        }

        val dt = (right.timestamp - left.timestamp).coerceAtLeast(1L).toFloat()
        val k = ((targetMs - left.timestamp).toFloat() / dt).coerceIn(0f, 1f)

        val lat = left.geoPoint.latitude + (right.geoPoint.latitude - left.geoPoint.latitude) * k
        val lon = left.geoPoint.longitude + (right.geoPoint.longitude - left.geoPoint.longitude) * k
        val speed = left.speed + (right.speed - left.speed) * k
        val angle = left.angle + (right.angle - left.angle) * k

        return RoutePoint(
            geoPoint = GeoPoint(lat, lon),
            speed = speed,
            angle = angle,
            timestamp = targetMs,
            absoluteTime = left.absoluteTime + ((right.absoluteTime - left.absoluteTime) * k).toLong()
        )
    }

    private fun calculateBounds(points: List<GeoPoint>): Bounds {
        if (points.isEmpty()) {
            return Bounds(0.0, 0.0, 0.0, 0.0)
        }

        var minLat = points[0].latitude
        var maxLat = points[0].latitude
        var minLon = points[0].longitude
        var maxLon = points[0].longitude

        for (point in points) {
            minLat = minOf(minLat, point.latitude)
            maxLat = maxOf(maxLat, point.latitude)
            minLon = minOf(minLon, point.longitude)
            maxLon = maxOf(maxLon, point.longitude)
        }

        return Bounds(minLat, maxLat, minLon, maxLon)
    }

    private fun calculateZoomLevel(bounds: Bounds): Double {
        val latDiff = bounds.maxLat - bounds.minLat
        val lonDiff = bounds.maxLon - bounds.minLon
        val maxDiff = maxOf(latDiff, lonDiff)

        return when {
            maxDiff > 0.1 -> 10.0
            maxDiff > 0.05 -> 12.0
            maxDiff > 0.01 -> 14.0
            maxDiff > 0.005 -> 16.0
            else -> 18.0
        }
    }

    private fun buildMarkerBitmap(color: Int): Bitmap {
        val size = 44
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val outerPaint = android.graphics.Paint().apply {
            this.color = Color.WHITE
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, outerPaint)

        val innerPaint = android.graphics.Paint().apply {
            this.color = color
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 9f, innerPaint)

        return bitmap
    }

    override fun onStart() {
        super.onStart()
        mapboxMapView?.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapboxMapView?.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        pointAnnotationManager?.deleteAll()
        polylineAnnotationManager?.deleteAll()
        mapboxMapView?.onDestroy()
    }
}
