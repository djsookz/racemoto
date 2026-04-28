package com.example.clinometer

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.widget.NestedScrollView
import com.example.clinometer.main.map.MapActivity
import com.example.clinometer.settings.LanguageManager
import com.example.clinometer.settings.UnitsManager
import com.example.clinometer.track.TrackMapExtras
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class TrackLapCompareActivity : AppCompatActivity() {

    companion object {
        private const val TRACK_OVERLAY_AXIS_MAX = 100f
        private const val TRACK_GRAVITY = 9.80665f
        private const val TRACK_UI_PREFS = "track_ui_prefs"
        private const val TRACK_COMPARE_VISIBLE_METRICS_MOTO_PREF_KEY = "track_compare_visible_metrics_moto"
        private const val TRACK_COMPARE_VISIBLE_METRICS_CAR_PREF_KEY = "track_compare_visible_metrics_car"

        private val CURRENT_SPEED_COLOR = Color.rgb(252, 120, 5)
        private val COMPARE_SPEED_COLOR = Color.rgb(176, 86, 14)
        private val CURRENT_ANGLE_COLOR = Color.rgb(5, 252, 227)
        private val COMPARE_ANGLE_COLOR = Color.rgb(8, 156, 141)
        private val CURRENT_LONG_G_COLOR = Color.rgb(164, 214, 72)
        private val COMPARE_LONG_G_COLOR = Color.rgb(104, 136, 43)
        private val CURRENT_LAT_G_COLOR = Color.rgb(255, 106, 150)
        private val COMPARE_LAT_G_COLOR = Color.rgb(186, 76, 110)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private data class Bounds(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    )

    private data class LapStats(
        val lapDurationSec: Float,
        val maxSpeedKmh: Float,
        val avgSpeedKmh: Float,
        val minSpeedKmh: Float,
        val maxAccelerationG: Float,
        val maxBrakingG: Float,
        val maxCorneringLeftG: Float,
        val maxCorneringRightG: Float,
        val maxLeanLeftDeg: Float,
        val maxLeanRightDeg: Float
    )

    private data class TrackSignedTelemetrySample(
        val timeSeconds: Float,
        val longitudinalG: Float,
        val lateralG: Float
    )

    private data class TrackOverlayScale(
        val positiveLimit: Float,
        val negativeLimit: Float
    )

    private enum class TrackChartMetric(val labelResId: Int, val prefValue: String) {
        SPEED(R.string.chart_speed_legend, "speed"),
        ANGLE(R.string.chart_angle_legend, "angle"),
        LONGITUDINAL_G(R.string.chart_longitudinal_g_legend, "longitudinal_g"),
        LATERAL_G(R.string.chart_lateral_g_legend, "lateral_g");

        companion object {
            fun fromPref(prefValue: String): TrackChartMetric? {
                return entries.firstOrNull { it.prefValue == prefValue }
            }
        }
    }

    private fun TrackSignedTelemetrySample.toChartDisplaySample(): TrackSignedTelemetrySample {
        return copy(
            longitudinalG = -longitudinalG,
            lateralG = -lateralG
        )
    }

    private lateinit var chart: LineChart
    private lateinit var tvTitle: TextView

    private lateinit var currentAngleItem: LinearLayout
    private lateinit var currentLatGItem: LinearLayout
    private lateinit var compareAngleItem: LinearLayout
    private lateinit var compareLatGItem: LinearLayout

    private lateinit var tvCurrentSpeedValue: TextView
    private lateinit var tvCurrentAngleValue: TextView
    private lateinit var tvCurrentLongGValue: TextView
    private lateinit var tvCurrentLatGValue: TextView
    private lateinit var tvCompareSpeedValue: TextView
    private lateinit var tvCompareAngleValue: TextView
    private lateinit var tvCompareLongGValue: TextView
    private lateinit var tvCompareLatGValue: TextView

    private lateinit var tvStatLapCurrent: TextView
    private lateinit var tvStatLapCompare: TextView
    private lateinit var tvStatMaxCurrent: TextView
    private lateinit var tvStatMaxCompare: TextView
    private lateinit var tvStatAvgCurrent: TextView
    private lateinit var tvStatAvgCompare: TextView
    private lateinit var tvStatGapCurrent: TextView
    private lateinit var tvStatGapCompare: TextView
    private lateinit var tvStatMinCurrent: TextView
    private lateinit var tvStatMinCompare: TextView
    private lateinit var tvStatAccelerationCurrent: TextView
    private lateinit var tvStatAccelerationCompare: TextView
    private lateinit var tvStatBrakingCurrent: TextView
    private lateinit var tvStatBrakingCompare: TextView
    private lateinit var tvStatBottomMetricLeftLabel: TextView
    private lateinit var tvStatBottomMetricRightLabel: TextView
    private lateinit var tvStatBottomLeftCurrent: TextView
    private lateinit var tvStatBottomLeftCompare: TextView
    private lateinit var tvStatBottomRightCurrent: TextView
    private lateinit var tvStatBottomRightCompare: TextView

    private var mapboxMapView: MapboxMapView? = null
    private var pointAnnotationManager: PointAnnotationManager? = null
    private var polylineAnnotationManager: PolylineAnnotationManager? = null
    private var currentMarker: PointAnnotation? = null
    private var compareMarker: PointAnnotation? = null

    private var currentLapData: LapData? = null
    private var compareLapData: LapData? = null
    private var currentRoute: List<RoutePoint> = emptyList()
    private var compareRoute: List<RoutePoint> = emptyList()
    private var currentTelemetrySamples: List<TrackSignedTelemetrySample> = emptyList()
    private var compareTelemetrySamples: List<TrackSignedTelemetrySample> = emptyList()

    private var currentSessionId: String = ""
    private var compareSessionId: String = ""
    private var currentOutingNumber: Int = 1
    private var compareOutingNumber: Int = 1
    private var currentLapNumber: Int = 1
    private var compareLapNumber: Int = 1
    private var originRaceId: Long = -1L
    private var originIsPointToPoint: Boolean = false

    private var maxDurationSec: Float = 0f
    private var currentReaderTimeSec: Float = 0f
    private var isMotorcycleCompare: Boolean = false
    private val visibleTrackMetrics = linkedSetOf<TrackChartMetric>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_lap_compare)
        applySystemBarsPaddingToRoot()

        bindViews()
        readExtras()
        styleBackButton()

        findViewById<TextView>(R.id.btnBack).setOnClickListener { navigateBackToOriginLap() }
        findViewById<View>(R.id.btnCompareChartSettings).setOnClickListener {
            showTrackChartMetricsDialog()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateBackToOriginLap()
            }
        })

        if (!loadLapData()) {
            Toast.makeText(this, "Няма lap данни за сравнение", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeVisibleTrackMetrics()
        configureMetricRows()
        applyViewportLayoutAdjustments()
        setupMapboxMap()
        setupChart()
        populateStatsComparison()
        updateByTime(0f)
    }

    private fun bindViews() {
        chart = findViewById(R.id.chartCompare)
        tvTitle = findViewById(R.id.tvTitle)

        currentAngleItem = findViewById(R.id.currentAngleItem)
        currentLatGItem = findViewById(R.id.currentLatGItem)
        compareAngleItem = findViewById(R.id.compareAngleItem)
        compareLatGItem = findViewById(R.id.compareLatGItem)

        tvCurrentSpeedValue = findViewById(R.id.tvCurrentSpeedValue)
        tvCurrentAngleValue = findViewById(R.id.tvCurrentAngleValue)
        tvCurrentLongGValue = findViewById(R.id.tvCurrentLongGValue)
        tvCurrentLatGValue = findViewById(R.id.tvCurrentLatGValue)
        tvCompareSpeedValue = findViewById(R.id.tvCompareSpeedValue)
        tvCompareAngleValue = findViewById(R.id.tvCompareAngleValue)
        tvCompareLongGValue = findViewById(R.id.tvCompareLongGValue)
        tvCompareLatGValue = findViewById(R.id.tvCompareLatGValue)

        tvStatLapCurrent = findViewById(R.id.tvStatLapCurrent)
        tvStatLapCompare = findViewById(R.id.tvStatLapCompare)
        tvStatMaxCurrent = findViewById(R.id.tvStatMaxCurrent)
        tvStatMaxCompare = findViewById(R.id.tvStatMaxCompare)
        tvStatAvgCurrent = findViewById(R.id.tvStatAvgCurrent)
        tvStatAvgCompare = findViewById(R.id.tvStatAvgCompare)
        tvStatGapCurrent = findViewById(R.id.tvStatGapCurrent)
        tvStatGapCompare = findViewById(R.id.tvStatGapCompare)
        tvStatMinCurrent = findViewById(R.id.tvStatMinCurrent)
        tvStatMinCompare = findViewById(R.id.tvStatMinCompare)
        tvStatAccelerationCurrent = findViewById(R.id.tvStatAccelerationCurrent)
        tvStatAccelerationCompare = findViewById(R.id.tvStatAccelerationCompare)
        tvStatBrakingCurrent = findViewById(R.id.tvStatBrakingCurrent)
        tvStatBrakingCompare = findViewById(R.id.tvStatBrakingCompare)
        tvStatBottomMetricLeftLabel = findViewById(R.id.tvStatBottomMetricLeftLabel)
        tvStatBottomMetricRightLabel = findViewById(R.id.tvStatBottomMetricRightLabel)
        tvStatBottomLeftCurrent = findViewById(R.id.tvStatBottomLeftCurrent)
        tvStatBottomLeftCompare = findViewById(R.id.tvStatBottomLeftCompare)
        tvStatBottomRightCurrent = findViewById(R.id.tvStatBottomRightCurrent)
        tvStatBottomRightCompare = findViewById(R.id.tvStatBottomRightCompare)
    }

    private fun readExtras() {
        currentSessionId = intent.getStringExtra("current_session_id") ?: ""
        compareSessionId = intent.getStringExtra("compare_session_id") ?: ""
        currentOutingNumber = intent.getIntExtra("current_outing_number", 1)
        compareOutingNumber = intent.getIntExtra("compare_outing_number", 1)
        currentLapNumber = intent.getIntExtra("current_lap_number", 1)
        compareLapNumber = intent.getIntExtra("compare_lap_number", 1)
        originRaceId = intent.getLongExtra("origin_race_id", -1L)
        originIsPointToPoint = intent.getBooleanExtra("origin_is_point_to_point", false)

        val trackName = intent.getStringExtra("track_name") ?: "Track"
        tvTitle.text = "$trackName • Compare"
    }

    private fun styleBackButton() {
        val backButton = findViewById<TextView>(R.id.btnBack)
        backButton.setTextColor(Color.WHITE)

        val startDrawable = backButton.compoundDrawablesRelative[0] ?: backButton.compoundDrawables[0]
        if (startDrawable != null) {
            val tinted = DrawableCompat.wrap(startDrawable.mutate())
            DrawableCompat.setTint(tinted, Color.WHITE)
            backButton.setCompoundDrawablesRelativeWithIntrinsicBounds(tinted, null, null, null)
        }
    }

    private fun navigateBackToOriginLap() {
        if (originRaceId <= 0L) {
            finish()
            return
        }

        val trackId = intent.getStringExtra("track_id") ?: ""
        val trackName = intent.getStringExtra("track_name") ?: ""
        val isMotorcycle = intent.getBooleanExtra("is_motorcycle", false)

        startActivity(Intent(this, MapActivity::class.java).apply {
            putExtra("RACE_ID", originRaceId)
            putExtra(TrackMapExtras.EXTRA_TRACK_CONTEXT, true)
            putExtra(TrackMapExtras.EXTRA_TRACK_ID, trackId)
            putExtra(TrackMapExtras.EXTRA_TRACK_NAME, trackName)
            putExtra(TrackMapExtras.EXTRA_TRACK_IS_MOTORCYCLE, isMotorcycle)
            putExtra(TrackMapExtras.EXTRA_TRACK_SESSION_ID, currentSessionId)
            putExtra(TrackMapExtras.EXTRA_TRACK_LAP_NUMBER, currentLapNumber)
            putExtra(TrackMapExtras.EXTRA_TRACK_OUTING_NUMBER, currentOutingNumber)
            putExtra(TrackMapExtras.EXTRA_TRACK_IS_POINT_TO_POINT, originIsPointToPoint)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })

        finish()
    }

    private fun loadLapData(): Boolean {
        val prefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val gson = Gson()

        val currentJson = prefs.getString("${currentSessionId}_outing_${currentOutingNumber}_lap_data_${currentLapNumber}", null)
        val compareJson = prefs.getString("${compareSessionId}_outing_${compareOutingNumber}_lap_data_${compareLapNumber}", null)

        if (currentJson.isNullOrBlank() || compareJson.isNullOrBlank()) return false

        currentLapData = try {
            gson.fromJson(currentJson, LapData::class.java)
        } catch (_: Exception) {
            null
        }
        compareLapData = try {
            gson.fromJson(compareJson, LapData::class.java)
        } catch (_: Exception) {
            null
        }

        val currentLap = currentLapData ?: return false
        val compareLap = compareLapData ?: return false

        currentRoute = normalizeRoute(currentLap.routePoints)
        compareRoute = normalizeRoute(compareLap.routePoints)
        if (currentRoute.size < 2 || compareRoute.size < 2) return false

        currentTelemetrySamples = buildTrackSignedTelemetrySamples(currentLap, currentRoute)
            .map { it.toChartDisplaySample() }
        compareTelemetrySamples = buildTrackSignedTelemetrySamples(compareLap, compareRoute)
            .map { it.toChartDisplaySample() }

        val currentDuration = (currentRoute.last().timestamp / 1000f).coerceAtLeast(0.1f)
        val compareDuration = (compareRoute.last().timestamp / 1000f).coerceAtLeast(0.1f)
        maxDurationSec = maxOf(currentDuration, compareDuration)

        isMotorcycleCompare = intent.getBooleanExtra("is_motorcycle", false) || detectMotorcycleData()
        return true
    }

    private fun normalizeRoute(points: List<RoutePoint>): List<RoutePoint> {
        if (points.isEmpty()) return emptyList()
        val baseTimestamp = points.first().timestamp
        return points.map { point ->
            point.copy(timestamp = point.timestamp - baseTimestamp)
        }
    }

    private fun detectMotorcycleData(): Boolean {
        val routeHasLean = currentRoute.any { abs(it.angle) >= 3f } || compareRoute.any { abs(it.angle) >= 3f }
        val lapHasLean = currentLapData?.leanAngleData?.any { abs(it) >= 3f } == true ||
            compareLapData?.leanAngleData?.any { abs(it) >= 3f } == true
        return routeHasLean || lapHasLean
    }

    private fun configureMetricRows() {
        val liveValuesContainer = findViewById<View>(R.id.compareLiveValuesContainer)
        liveValuesContainer.visibility = if (visibleTrackMetrics.isEmpty()) View.GONE else View.VISIBLE
        currentAngleItem.visibility = if (isMotorcycleCompare && visibleTrackMetrics.contains(TrackChartMetric.ANGLE)) View.VISIBLE else View.GONE
        compareAngleItem.visibility = if (isMotorcycleCompare && visibleTrackMetrics.contains(TrackChartMetric.ANGLE)) View.VISIBLE else View.GONE
        currentLatGItem.visibility = if (!isMotorcycleCompare && visibleTrackMetrics.contains(TrackChartMetric.LATERAL_G)) View.VISIBLE else View.GONE
        compareLatGItem.visibility = if (!isMotorcycleCompare && visibleTrackMetrics.contains(TrackChartMetric.LATERAL_G)) View.VISIBLE else View.GONE
        tvCurrentSpeedValue.visibility = if (visibleTrackMetrics.contains(TrackChartMetric.SPEED)) View.VISIBLE else View.GONE
        tvCompareSpeedValue.visibility = if (visibleTrackMetrics.contains(TrackChartMetric.SPEED)) View.VISIBLE else View.GONE
        tvCurrentLongGValue.visibility = if (visibleTrackMetrics.contains(TrackChartMetric.LONGITUDINAL_G)) View.VISIBLE else View.GONE
        tvCompareLongGValue.visibility = if (visibleTrackMetrics.contains(TrackChartMetric.LONGITUDINAL_G)) View.VISIBLE else View.GONE
    }

    private fun applyViewportLayoutAdjustments() {
        val scrollView = findViewById<NestedScrollView?>(R.id.compareScrollView) ?: return
        val mapCard = findViewById<View?>(R.id.cardCompareMap) ?: return
        val chartCard = findViewById<View?>(R.id.cardCompareChart) ?: return
        val chartFrame = findViewById<View?>(R.id.compareChartFrame) ?: return
        val valuesContainer = findViewById<View?>(R.id.compareLiveValuesContainer)
        val density = resources.displayMetrics.density
        val minSectionHeightPx = (280f * density).toInt()
        val cardPaddingEstimatePx = (40f * density).toInt()
        val valuesFallbackHeightPx = (42f * density).toInt()

        scrollView.post {
            val viewportHeight = scrollView.height
            if (viewportHeight <= 0) return@post

            val contentPaddingPx = (28f * density).toInt()
            val interCardGapPx = (24f * density).toInt()
            val targetSectionHeightPx = (((viewportHeight - contentPaddingPx - interCardGapPx) * 0.5f).toInt())
                .coerceAtLeast(minSectionHeightPx)

            mapCard.layoutParams = mapCard.layoutParams.apply {
                height = targetSectionHeightPx
            }
            chartCard.layoutParams = chartCard.layoutParams.apply {
                height = targetSectionHeightPx
            }

            chartCard.requestLayout()
            mapCard.requestLayout()

            chartCard.post {
                val valuesHeightPx = valuesContainer?.height?.takeIf { it > 0 } ?: valuesFallbackHeightPx
                val targetChartHeightPx = (targetSectionHeightPx - valuesHeightPx - cardPaddingEstimatePx)
                    .coerceAtLeast((220f * density).toInt())

                chartFrame.layoutParams = chartFrame.layoutParams.apply {
                    height = targetChartHeightPx
                }
                chart.layoutParams = chart.layoutParams.apply {
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }

                chartFrame.requestLayout()
                chart.requestLayout()
            }
        }
    }

    private fun setupMapboxMap() {
        val mapContainer = findViewById<FrameLayout>(R.id.mapContainer)
        mapboxMapView = MapboxMapView(this)
        mapContainer.addView(mapboxMapView)

        mapboxMapView?.scalebar?.enabled = false
        mapboxMapView?.compass?.enabled = false
        mapboxMapView?.attribution?.enabled = false

        val allGeoPoints = (currentRoute + compareRoute).map { it.geoPoint }
        val bounds = calculateBounds(allGeoPoints)
        val initialCurrentPoint = currentRoute.first().geoPoint
        val initialComparePoint = compareRoute.first().geoPoint
        val initialCenter = MapboxPoint.fromLngLat(
            (initialCurrentPoint.longitude + initialComparePoint.longitude) / 2.0,
            (initialCurrentPoint.latitude + initialComparePoint.latitude) / 2.0
        )

        mapboxMapView?.mapboxMap?.setCamera(
            CameraOptions.Builder()
                .center(initialCenter)
                .zoom(calculateZoomLevel(bounds))
                .pitch(0.0)
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
        mapboxMapView?.mapboxMap?.loadStyleUri(styleUri) {
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
        val polylineManager = polylineAnnotationManager ?: return
        polylineManager.deleteAll()

        val currentPoints = currentRoute.map {
            MapboxPoint.fromLngLat(it.geoPoint.longitude, it.geoPoint.latitude)
        }
        val comparePoints = compareRoute.map {
            MapboxPoint.fromLngLat(it.geoPoint.longitude, it.geoPoint.latitude)
        }

        polylineManager.create(
            PolylineAnnotationOptions()
                .withPoints(comparePoints)
                .withLineColor(String.format("#%06X", 0xFFFFFF and COMPARE_SPEED_COLOR))
                .withLineWidth(5.5)
        )
        polylineManager.create(
            PolylineAnnotationOptions()
                .withPoints(currentPoints)
                .withLineColor(String.format("#%06X", 0xFFFFFF and CURRENT_SPEED_COLOR))
                .withLineWidth(6.5)
        )
    }

    private fun createMovingMarkers() {
        val pointManager = pointAnnotationManager ?: return
        pointManager.deleteAll()

        val currentStart = currentRoute.first().geoPoint
        val compareStart = compareRoute.first().geoPoint

        currentMarker = pointManager.create(
            PointAnnotationOptions()
                .withPoint(MapboxPoint.fromLngLat(currentStart.longitude, currentStart.latitude))
                .withIconImage(buildMarkerBitmap(CURRENT_SPEED_COLOR))
        )
        compareMarker = pointManager.create(
            PointAnnotationOptions()
                .withPoint(MapboxPoint.fromLngLat(compareStart.longitude, compareStart.latitude))
                .withIconImage(buildMarkerBitmap(COMPARE_SPEED_COLOR))
        )
    }

    private fun setupChart() {
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
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
                    val totalSeconds = abs(value).toLong()
                    val minutes = totalSeconds / 60
                    val seconds = totalSeconds % 60
                    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                }
            }
        }

        chart.axisLeft.textColor = Color.WHITE
        chart.axisRight.textColor = Color.WHITE

        applyChartData()

        chart.renderer = object : com.github.mikephil.charting.renderer.LineChartRenderer(
            chart,
            chart.animator,
            chart.viewPortHandler
        ) {
            init {
                mChart = chart
                mAnimator = chart.animator
                mViewPortHandler = chart.viewPortHandler
            }

            override fun drawData(c: Canvas) {
                super.drawData(c)

                val readerPaint = android.graphics.Paint().apply {
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
                    readerPaint
                )

                val timePaint = android.graphics.Paint().apply {
                    color = Color.RED
                    textSize = 30f
                    isAntiAlias = true
                    isFakeBoldText = true
                }

                val centerValue = (mChart.lowestVisibleX + mChart.highestVisibleX) / 2f
                val timeText = formatTimeForReader(centerValue)
                c.drawText(timeText, centerX + 10f, mViewPortHandler.contentTop() + 38f, timePaint)
            }
        }

        val dataStartTime = 0f
        val dataEndTime = maxDurationSec
        var isZooming = false
        var zoomCenterX = 0f

        val scaleGestureDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    isZooming = true
                    zoomCenterX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val deltaX = abs(detector.currentSpanX - detector.previousSpanX)
                    val deltaY = abs(detector.currentSpanY - detector.previousSpanY)

                    val scaleFactorX = if (deltaX > deltaY * 1.5f) detector.scaleFactor else 1f
                    val scaleFactorY = if (deltaY > detector.currentSpanX * 1.5f) detector.scaleFactor else 1f

                    if (deltaX <= deltaY * 1.5f && deltaY <= detector.currentSpanX * 1.5f) {
                        chart.zoom(
                            detector.scaleFactor,
                            detector.scaleFactor,
                            chart.width / 2f,
                            chart.height / 2f,
                            YAxis.AxisDependency.LEFT
                        )
                    } else {
                        chart.zoom(
                            scaleFactorX,
                            scaleFactorY,
                            chart.width / 2f,
                            chart.height / 2f,
                            YAxis.AxisDependency.LEFT
                        )
                    }

                    var targetX = zoomCenterX - chart.visibleXRange / 2f
                    val visibleRange = chart.visibleXRange
                    val centerAfterMove = targetX + visibleRange / 2f

                    if (centerAfterMove < dataStartTime) {
                        targetX = dataStartTime - visibleRange / 2f
                    } else if (centerAfterMove > dataEndTime) {
                        targetX = dataEndTime - visibleRange / 2f
                    }

                    chart.moveViewToX(targetX)
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    isZooming = false
                    val centerX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                    val clampedCenter = clampChartCenter(centerX, dataStartTime, dataEndTime)
                    updateByTime(clampedCenter)
                    chart.invalidate()
                }
            }
        )

        chart.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)

            if (!isZooming) {
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

        chart.setOnChartGestureListener(object : OnChartGestureListener {
            override fun onChartGestureStart(me: MotionEvent?, lastGesture: ChartTouchListener.ChartGesture?) = Unit
            override fun onChartGestureEnd(me: MotionEvent?, lastGesture: ChartTouchListener.ChartGesture?) = Unit
            override fun onChartLongPressed(me: MotionEvent?) = Unit
            override fun onChartSingleTapped(me: MotionEvent?) = Unit
            override fun onChartFling(
                me1: MotionEvent?,
                me2: MotionEvent?,
                velocityX: Float,
                velocityY: Float
            ) = Unit

            override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) = Unit

            override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
                if (!isZooming) {
                    val centerX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                    val clampedCenter = clampChartCenter(centerX, dataStartTime, dataEndTime)
                    updateByTime(clampedCenter)
                    chart.invalidate()
                }
            }

            override fun onChartDoubleTapped(me: MotionEvent?) {
                chart.fitScreen()
                val centerX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                val clampedCenter = clampChartCenter(centerX, dataStartTime, dataEndTime)
                updateByTime(clampedCenter)
                chart.invalidate()
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

    private fun applyChartData() {
        val speedUnit = UnitsManager.getSpeedUnit(this)
        val lineData = LineData()
        val showSpeed = visibleTrackMetrics.contains(TrackChartMetric.SPEED)
        val showAngle = isMotorcycleCompare && visibleTrackMetrics.contains(TrackChartMetric.ANGLE)
        val showLongitudinalG = visibleTrackMetrics.contains(TrackChartMetric.LONGITUDINAL_G)
        val showLateralG = !isMotorcycleCompare && visibleTrackMetrics.contains(TrackChartMetric.LATERAL_G)

        val currentSpeedEntries = currentRoute.map {
            Entry(it.timestamp / 1000f, UnitsManager.convertSpeed(it.speed, speedUnit))
        }
        val compareSpeedEntries = compareRoute.map {
            Entry(it.timestamp / 1000f, UnitsManager.convertSpeed(it.speed, speedUnit))
        }
        if (showSpeed) {
            lineData.addDataSet(
                createSpeedDataSet(
                    entries = currentSpeedEntries,
                    label = "Current ${getString(R.string.chart_speed_legend)}",
                    colorInt = CURRENT_SPEED_COLOR
                )
            )
            lineData.addDataSet(
                createSpeedDataSet(
                    entries = compareSpeedEntries,
                    label = "Compare ${getString(R.string.chart_speed_legend)}",
                    colorInt = COMPARE_SPEED_COLOR
                )
            )
        }

        val longitudinalScale = resolveTrackOverlayScale(
            currentTelemetrySamples.map { it.longitudinalG } + compareTelemetrySamples.map { it.longitudinalG }
        )
        if (showLongitudinalG) {
            lineData.addDataSet(
                createOverlayDataSet(
                    entries = currentTelemetrySamples.map {
                        Entry(it.timeSeconds, scaleToTrackOverlayAxis(it.longitudinalG, longitudinalScale))
                    },
                    label = "Current ${getString(R.string.chart_longitudinal_g_legend)}",
                    colorInt = CURRENT_LONG_G_COLOR
                )
            )
            lineData.addDataSet(
                createOverlayDataSet(
                    entries = compareTelemetrySamples.map {
                        Entry(it.timeSeconds, scaleToTrackOverlayAxis(it.longitudinalG, longitudinalScale))
                    },
                    label = "Compare ${getString(R.string.chart_longitudinal_g_legend)}",
                    colorInt = COMPARE_LONG_G_COLOR
                )
            )
        }

        if (showAngle) {
            val angleScale = resolveTrackOverlayScale(
                currentRoute.map { it.angle } + compareRoute.map { it.angle }
            )
            lineData.addDataSet(
                createOverlayDataSet(
                    entries = currentRoute.map {
                        Entry(it.timestamp / 1000f, scaleToTrackOverlayAxis(it.angle, angleScale))
                    },
                    label = "Current ${getString(R.string.chart_angle_legend)}",
                    colorInt = CURRENT_ANGLE_COLOR
                )
            )
            lineData.addDataSet(
                createOverlayDataSet(
                    entries = compareRoute.map {
                        Entry(it.timestamp / 1000f, scaleToTrackOverlayAxis(it.angle, angleScale))
                    },
                    label = "Compare ${getString(R.string.chart_angle_legend)}",
                    colorInt = COMPARE_ANGLE_COLOR
                )
            )
        } else if (showLateralG) {
            val lateralScale = resolveTrackOverlayScale(
                currentTelemetrySamples.map { it.lateralG } + compareTelemetrySamples.map { it.lateralG }
            )
            lineData.addDataSet(
                createOverlayDataSet(
                    entries = currentTelemetrySamples.map {
                        Entry(it.timeSeconds, scaleToTrackOverlayAxis(it.lateralG, lateralScale))
                    },
                    label = "Current ${getString(R.string.chart_lateral_g_legend)}",
                    colorInt = CURRENT_LAT_G_COLOR
                )
            )
            lineData.addDataSet(
                createOverlayDataSet(
                    entries = compareTelemetrySamples.map {
                        Entry(it.timeSeconds, scaleToTrackOverlayAxis(it.lateralG, lateralScale))
                    },
                    label = "Compare ${getString(R.string.chart_lateral_g_legend)}",
                    colorInt = COMPARE_LAT_G_COLOR
                )
            )
        }

        chart.data = lineData
        chart.setVisibleXRangeMaximum(maxDurationSec.coerceAtLeast(1f))

        val speedAxisMax = maxOf(
            currentRoute.maxOfOrNull { UnitsManager.convertSpeed(it.speed, speedUnit) } ?: 0f,
            compareRoute.maxOfOrNull { UnitsManager.convertSpeed(it.speed, speedUnit) } ?: 0f,
            1f
        )
        chart.axisLeft.apply {
            isEnabled = showSpeed
            axisMinimum = 0f
            axisMaximum = speedAxisMax
            granularity = 1f
            setLabelCount(6, true)
            setDrawZeroLine(false)
            setDrawAxisLine(showSpeed)
            setDrawGridLines(showSpeed)
            setDrawLabels(showSpeed)
            textColor = Color.WHITE
        }

        val showOverlayAxis = showAngle || showLongitudinalG || showLateralG
        chart.axisRight.apply {
            isEnabled = showOverlayAxis
            setDrawLabels(false)
            setDrawGridLines(false)
            setDrawAxisLine(false)
            axisMinimum = -TRACK_OVERLAY_AXIS_MAX
            axisMaximum = TRACK_OVERLAY_AXIS_MAX
            setDrawZeroLine(showOverlayAxis)
            zeroLineColor = Color.GRAY
            zeroLineWidth = 1f
        }

        chart.invalidate()
    }

    private fun createSpeedDataSet(entries: List<Entry>, label: String, colorInt: Int): LineDataSet {
        return LineDataSet(entries, label).apply {
            color = colorInt
            lineWidth = 2.2f
            setDrawValues(false)
            setDrawCircles(false)
            mode = LineDataSet.Mode.LINEAR
            axisDependency = YAxis.AxisDependency.LEFT
        }
    }

    private fun createOverlayDataSet(entries: List<Entry>, label: String, colorInt: Int): LineDataSet {
        return LineDataSet(entries, label).apply {
            color = colorInt
            lineWidth = 1.8f
            setDrawValues(false)
            setDrawCircles(false)
            mode = LineDataSet.Mode.LINEAR
            axisDependency = YAxis.AxisDependency.RIGHT
        }
    }

    private fun resolveTrackOverlayScale(values: List<Float>): TrackOverlayScale {
        val positiveLimit = values.maxOfOrNull { it.coerceAtLeast(0f) }?.takeIf { it > 0f } ?: 1f
        val negativeLimit = values.minOfOrNull { it.coerceAtMost(0f) }
            ?.let(::abs)
            ?.takeIf { it > 0f }
            ?: 1f
        return TrackOverlayScale(
            positiveLimit = positiveLimit,
            negativeLimit = negativeLimit
        )
    }

    private fun scaleToTrackOverlayAxis(value: Float, scale: TrackOverlayScale): Float {
        val divisor = if (value >= 0f) scale.positiveLimit else scale.negativeLimit
        if (divisor <= 0f) return 0f
        return ((value / divisor) * TRACK_OVERLAY_AXIS_MAX)
            .coerceIn(-TRACK_OVERLAY_AXIS_MAX, TRACK_OVERLAY_AXIS_MAX)
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
        val minutes = totalSeconds / 60
        val remainingSeconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds)
    }

    private fun populateStatsComparison() {
        val currentStats = calculateLapStats(currentLapData, currentRoute, currentTelemetrySamples)
        val compareStats = calculateLapStats(compareLapData, compareRoute, compareTelemetrySamples)

        tvStatLapCurrent.text = formatLapTime(currentStats.lapDurationSec)
        tvStatLapCompare.text = formatLapTime(compareStats.lapDurationSec)

        tvStatMaxCurrent.text = UnitsManager.formatSpeed(currentStats.maxSpeedKmh, this, 0)
        tvStatMaxCompare.text = UnitsManager.formatSpeed(compareStats.maxSpeedKmh, this, 0)

        tvStatMinCurrent.text = UnitsManager.formatSpeed(currentStats.minSpeedKmh, this, 0)
        tvStatMinCompare.text = UnitsManager.formatSpeed(compareStats.minSpeedKmh, this, 0)

        tvStatAvgCurrent.text = UnitsManager.formatSpeed(currentStats.avgSpeedKmh, this, 0)
        tvStatAvgCompare.text = UnitsManager.formatSpeed(compareStats.avgSpeedKmh, this, 0)

        tvStatAccelerationCurrent.text = String.format(Locale.getDefault(), "%.2f G", currentStats.maxAccelerationG)
        tvStatAccelerationCompare.text = String.format(Locale.getDefault(), "%.2f G", compareStats.maxAccelerationG)
        tvStatBrakingCurrent.text = String.format(Locale.getDefault(), "%.2f G", currentStats.maxBrakingG)
        tvStatBrakingCompare.text = String.format(Locale.getDefault(), "%.2f G", compareStats.maxBrakingG)

        val gapSec = compareStats.lapDurationSec - currentStats.lapDurationSec
        tvStatGapCurrent.text = if (gapSec >= 0f) {
            String.format(Locale.getDefault(), "-%.3f s", gapSec)
        } else {
            String.format(Locale.getDefault(), "+%.3f s", -gapSec)
        }
        tvStatGapCompare.text = if (gapSec >= 0f) {
            String.format(Locale.getDefault(), "+%.3f s", gapSec)
        } else {
            String.format(Locale.getDefault(), "-%.3f s", -gapSec)
        }

        if (isMotorcycleCompare) {
            tvStatBottomMetricLeftLabel.setText(R.string.track_max_lean_left)
            tvStatBottomMetricRightLabel.setText(R.string.track_max_lean_right)
            tvStatBottomLeftCurrent.text = String.format(Locale.getDefault(), "%.0f°", currentStats.maxLeanLeftDeg)
            tvStatBottomLeftCompare.text = String.format(Locale.getDefault(), "%.0f°", compareStats.maxLeanLeftDeg)
            tvStatBottomRightCurrent.text = String.format(Locale.getDefault(), "%.0f°", currentStats.maxLeanRightDeg)
            tvStatBottomRightCompare.text = String.format(Locale.getDefault(), "%.0f°", compareStats.maxLeanRightDeg)
        } else {
            tvStatBottomMetricLeftLabel.setText(R.string.track_max_cornering_left)
            tvStatBottomMetricRightLabel.setText(R.string.track_max_cornering_right)
            tvStatBottomLeftCurrent.text = String.format(Locale.getDefault(), "%.2f G", currentStats.maxCorneringLeftG)
            tvStatBottomLeftCompare.text = String.format(Locale.getDefault(), "%.2f G", compareStats.maxCorneringLeftG)
            tvStatBottomRightCurrent.text = String.format(Locale.getDefault(), "%.2f G", currentStats.maxCorneringRightG)
            tvStatBottomRightCompare.text = String.format(Locale.getDefault(), "%.2f G", compareStats.maxCorneringRightG)
        }
    }

    private fun calculateLapStats(
        lapData: LapData?,
        route: List<RoutePoint>,
        displayTelemetry: List<TrackSignedTelemetrySample>
    ): LapStats {
        if (route.isEmpty()) {
            return LapStats(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        }

        val durationMs = lapData
            ?.takeIf { it.endTime > it.startTime }
            ?.let { it.endTime - it.startTime }
            ?: route.last().timestamp.coerceAtLeast(0L)
        val speedSamples = lapData?.speedData?.takeIf { it.isNotEmpty() } ?: route.map { it.speed }
        val maxSpeedKmh = speedSamples.maxOrNull() ?: 0f
        val avgSpeedKmh = if (speedSamples.isNotEmpty()) speedSamples.average().toFloat() else 0f
        val minSpeedKmh = speedSamples.minOrNull() ?: 0f
        val maxAccelerationG = displayTelemetry
            .maxOfOrNull { it.longitudinalG }
            ?.coerceAtLeast(0f)
            ?: 0f
        val maxBrakingG = displayTelemetry
            .minOfOrNull { it.longitudinalG }
            ?.let { abs(it.coerceAtMost(0f)) }
            ?: 0f
        val maxCorneringLeftG = displayTelemetry
            .minOfOrNull { it.lateralG }
            ?.let { abs(it.coerceAtMost(0f)) }
            ?: 0f
        val maxCorneringRightG = displayTelemetry
            .maxOfOrNull { it.lateralG }
            ?.coerceAtLeast(0f)
            ?: 0f
        val maxLeanLeftDeg = route
            .asSequence()
            .map { it.angle }
            .filter { it < 0f }
            .minOrNull()
            ?.let { abs(it) }
            ?: 0f
        val maxLeanRightDeg = route
            .asSequence()
            .map { it.angle }
            .filter { it > 0f }
            .maxOrNull()
            ?: 0f

        return LapStats(
            lapDurationSec = durationMs / 1000f,
            maxSpeedKmh = maxSpeedKmh,
            avgSpeedKmh = avgSpeedKmh,
            minSpeedKmh = minSpeedKmh,
            maxAccelerationG = maxAccelerationG,
            maxBrakingG = maxBrakingG,
            maxCorneringLeftG = maxCorneringLeftG,
            maxCorneringRightG = maxCorneringRightG,
            maxLeanLeftDeg = maxLeanLeftDeg,
            maxLeanRightDeg = maxLeanRightDeg
        )
    }

    private fun formatLapTime(seconds: Float): String {
        val totalMillis = (seconds * 1000f).roundToInt().coerceAtLeast(0)
        val minutes = totalMillis / 60_000
        val sec = (totalMillis % 60_000) / 1000
        val millis = totalMillis % 1000
        return String.format(Locale.getDefault(), "%02d:%02d.%03d", minutes, sec, millis)
    }

    private fun updateByTime(timeSec: Float) {
        val targetTime = timeSec.coerceIn(0f, maxDurationSec)
        currentReaderTimeSec = targetTime

        val currentPoint = interpolateRoutePoint(currentRoute, targetTime)
        val comparePoint = interpolateRoutePoint(compareRoute, targetTime)
        val currentTelemetry = interpolateTrackTelemetrySample(currentTelemetrySamples, targetTime)
        val compareTelemetry = interpolateTrackTelemetrySample(compareTelemetrySamples, targetTime)

        tvCurrentSpeedValue.text = UnitsManager.formatSpeed(currentPoint.speed, this, 0)
        tvCompareSpeedValue.text = UnitsManager.formatSpeed(comparePoint.speed, this, 0)
        tvCurrentLongGValue.text = formatTrackGValue(currentTelemetry?.longitudinalG ?: 0f)
        tvCompareLongGValue.text = formatTrackGValue(compareTelemetry?.longitudinalG ?: 0f)

        if (isMotorcycleCompare) {
            tvCurrentAngleValue.text = formatTrackAngleValue(currentPoint.angle)
            tvCompareAngleValue.text = formatTrackAngleValue(comparePoint.angle)
        } else {
            tvCurrentLatGValue.text = formatTrackGValue(currentTelemetry?.lateralG ?: 0f)
            tvCompareLatGValue.text = formatTrackGValue(compareTelemetry?.lateralG ?: 0f)
        }

        val currentMarkerRef = currentMarker
        val compareMarkerRef = compareMarker
        val pointManager = pointAnnotationManager
        if (currentMarkerRef != null && compareMarkerRef != null && pointManager != null) {
            currentMarkerRef.point = MapboxPoint.fromLngLat(
                currentPoint.geoPoint.longitude,
                currentPoint.geoPoint.latitude
            )
            compareMarkerRef.point = MapboxPoint.fromLngLat(
                comparePoint.geoPoint.longitude,
                comparePoint.geoPoint.latitude
            )
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
                    .pitch(0.0)
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
            val current = route[index]
            val next = route[index + 1]
            if (targetMs in current.timestamp..next.timestamp) {
                left = current
                right = next
                break
            }
        }

        val deltaMs = (right.timestamp - left.timestamp).coerceAtLeast(1L).toFloat()
        val factor = ((targetMs - left.timestamp).toFloat() / deltaMs).coerceIn(0f, 1f)
        val latitude = left.geoPoint.latitude + (right.geoPoint.latitude - left.geoPoint.latitude) * factor
        val longitude = left.geoPoint.longitude + (right.geoPoint.longitude - left.geoPoint.longitude) * factor
        val speed = left.speed + (right.speed - left.speed) * factor
        val angle = left.angle + (right.angle - left.angle) * factor

        return RoutePoint(
            geoPoint = GeoPoint(latitude, longitude),
            speed = speed,
            angle = angle,
            timestamp = targetMs,
            absoluteTime = left.absoluteTime + ((right.absoluteTime - left.absoluteTime) * factor).toLong()
        )
    }

    private fun interpolateTrackTelemetrySample(
        samples: List<TrackSignedTelemetrySample>,
        timeInSeconds: Float
    ): TrackSignedTelemetrySample? {
        if (samples.isEmpty()) return null
        if (samples.size == 1) return samples.first()

        val first = samples.first()
        val last = samples.last()
        if (timeInSeconds <= first.timeSeconds) return first
        if (timeInSeconds >= last.timeSeconds) return last

        for (index in 0 until samples.size - 1) {
            val before = samples[index]
            val after = samples[index + 1]
            if (timeInSeconds in before.timeSeconds..after.timeSeconds) {
                val span = after.timeSeconds - before.timeSeconds
                val factor = if (span <= 0f) 0f else ((timeInSeconds - before.timeSeconds) / span).coerceIn(0f, 1f)
                return TrackSignedTelemetrySample(
                    timeSeconds = timeInSeconds,
                    longitudinalG = before.longitudinalG + (after.longitudinalG - before.longitudinalG) * factor,
                    lateralG = before.lateralG + (after.lateralG - before.lateralG) * factor
                )
            }
        }

        return last
    }

    private fun formatTrackAngleValue(angleDegrees: Float): String {
        val safeAngle = if (abs(angleDegrees) < 0.5f) 0f else angleDegrees
        return String.format(Locale.getDefault(), "%.0f°", safeAngle)
    }

    private fun formatTrackGValue(gValue: Float): String {
        val safeValue = if (abs(gValue) < 0.005f) 0f else gValue
        return String.format(Locale.getDefault(), "%.1f G", safeValue)
    }

    private fun initializeVisibleTrackMetrics() {
        visibleTrackMetrics.clear()
        val availableMetrics = getAvailableTrackMetrics()
        val savedValues = getSharedPreferences(TRACK_UI_PREFS, MODE_PRIVATE)
            .getStringSet(getTrackChartMetricsPrefKey(), null)
            ?.toSet()

        val metricsToShow = if (savedValues == null) {
            getDefaultTrackMetrics()
        } else {
            savedValues.mapNotNull(TrackChartMetric::fromPref)
                .filter { it in availableMetrics }
                .ifEmpty { getDefaultTrackMetrics() }
        }

        visibleTrackMetrics += metricsToShow
    }

    private fun showTrackChartMetricsDialog() {
        val availableMetrics = getAvailableTrackMetrics()
        val labels = availableMetrics.map {
            SpannableString(getString(it.labelResId)).apply {
                setSpan(
                    ForegroundColorSpan(Color.WHITE),
                    0,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }.toTypedArray<CharSequence>()
        val checkedItems = availableMetrics.map { visibleTrackMetrics.contains(it) }.toBooleanArray()

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(R.string.track_chart_display_options)
            .setMultiChoiceItems(labels, checkedItems) { _, which, isChecked ->
                val metric = availableMetrics[which]
                if (isChecked) {
                    visibleTrackMetrics += metric
                } else {
                    visibleTrackMetrics -= metric
                }
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                persistVisibleTrackMetrics()
                configureMetricRows()
                applyChartData()
                updateByTime(currentReaderTimeSec)
            }
            .show()
    }

    private fun persistVisibleTrackMetrics() {
        getSharedPreferences(TRACK_UI_PREFS, MODE_PRIVATE)
            .edit()
            .putStringSet(
                getTrackChartMetricsPrefKey(),
                visibleTrackMetrics.map { it.prefValue }.toSet()
            )
            .apply()
    }

    private fun getTrackChartMetricsPrefKey(): String {
        return if (isMotorcycleCompare) {
            TRACK_COMPARE_VISIBLE_METRICS_MOTO_PREF_KEY
        } else {
            TRACK_COMPARE_VISIBLE_METRICS_CAR_PREF_KEY
        }
    }

    private fun getDefaultTrackMetrics(): List<TrackChartMetric> {
        return getAvailableTrackMetrics()
    }

    private fun getAvailableTrackMetrics(): List<TrackChartMetric> {
        return if (isMotorcycleCompare) {
            listOf(
                TrackChartMetric.SPEED,
                TrackChartMetric.ANGLE,
                TrackChartMetric.LONGITUDINAL_G
            )
        } else {
            listOf(
                TrackChartMetric.SPEED,
                TrackChartMetric.LONGITUDINAL_G,
                TrackChartMetric.LATERAL_G
            )
        }
    }

    private fun buildTrackSignedTelemetrySamples(
        lapData: LapData,
        route: List<RoutePoint>
    ): List<TrackSignedTelemetrySample> {
        buildTrackSignedTelemetrySamplesFromLapData(lapData)?.let { return it }
        if (route.isEmpty()) return emptyList()
        if (route.size == 1) return listOf(TrackSignedTelemetrySample(0f, 0f, 0f))

        return route.indices.map { index ->
            val current = route[index]
            val longitudinalG = when {
                index == 0 -> computeSignedLongitudinalG(current, route[1])
                index == route.lastIndex -> computeSignedLongitudinalG(route[index - 1], current)
                else -> computeSignedLongitudinalG(route[index - 1], route[index + 1])
            }
            val lateralG = when {
                route.size < 3 -> 0f
                index == 0 -> computeSignedLateralG(route[0], route[1], route[2])
                index == route.lastIndex -> computeSignedLateralG(
                    route[index - 2],
                    route[index - 1],
                    route[index]
                )
                else -> computeSignedLateralG(route[index - 1], current, route[index + 1])
            }

            TrackSignedTelemetrySample(
                timeSeconds = current.timestamp / 1000f,
                longitudinalG = longitudinalG,
                lateralG = lateralG
            )
        }
    }

    private fun buildTrackSignedTelemetrySamplesFromLapData(lapData: LapData): List<TrackSignedTelemetrySample>? {
        if (lapData.timestamps.isEmpty()) return null
        if (lapData.longitudinalGData.isEmpty() && lapData.lateralGData.isEmpty()) return null

        val sampleCount = minOf(
            lapData.timestamps.size,
            maxOf(lapData.longitudinalGData.size, lapData.lateralGData.size)
        )
        if (sampleCount <= 0) return null

        val baseTime = lapData.startTime.takeIf { it > 0L } ?: lapData.timestamps.first()
        return (0 until sampleCount).map { index ->
            TrackSignedTelemetrySample(
                timeSeconds = ((lapData.timestamps[index] - baseTime).coerceAtLeast(0L)) / 1000f,
                longitudinalG = lapData.longitudinalGData.getOrElse(index) { 0f },
                lateralG = lapData.lateralGData.getOrElse(index) { 0f }
            )
        }
    }

    private fun computeSignedLongitudinalG(start: RoutePoint, end: RoutePoint): Float {
        val deltaTimeMs = end.timestamp - start.timestamp
        if (deltaTimeMs <= 0L) return 0f

        val deltaTimeSec = deltaTimeMs / 1000f
        val startSpeedMs = start.speed / 3.6f
        val endSpeedMs = end.speed / 3.6f
        return -((endSpeedMs - startSpeedMs) / deltaTimeSec / TRACK_GRAVITY)
    }

    private fun computeSignedLateralG(previous: RoutePoint, current: RoutePoint, next: RoutePoint): Float {
        val deltaTimeMs = next.timestamp - previous.timestamp
        if (deltaTimeMs <= 0L) return 0f

        val speedMs = current.speed / 3.6f
        if (speedMs < 2.5f) return 0f

        val incomingBearing = calculateBearingRadians(previous.geoPoint, current.geoPoint) ?: return 0f
        val outgoingBearing = calculateBearingRadians(current.geoPoint, next.geoPoint) ?: return 0f
        val deltaTimeSec = deltaTimeMs / 1000f
        val bearingDelta = atan2(
            sin((outgoingBearing - incomingBearing).toDouble()),
            cos((outgoingBearing - incomingBearing).toDouble())
        ).toFloat()
        val turnRate = bearingDelta / deltaTimeSec
        return -(speedMs * turnRate / TRACK_GRAVITY)
    }

    private fun calculateBearingRadians(start: GeoPoint, end: GeoPoint): Float? {
        if (start.latitude == end.latitude && start.longitude == end.longitude) {
            return null
        }

        val startLat = Math.toRadians(start.latitude)
        val endLat = Math.toRadians(end.latitude)
        val deltaLon = Math.toRadians(end.longitude - start.longitude)
        val y = sin(deltaLon) * cos(endLat)
        val x = cos(startLat) * sin(endLat) - sin(startLat) * cos(endLat) * cos(deltaLon)
        return atan2(y, x).toFloat()
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
