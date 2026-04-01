package com.example.clinometer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
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
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.gson.Gson
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.roundToInt

class TrackLapCompareActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private lateinit var map: MapView
    private lateinit var chart: LineChart
    private lateinit var tvTitle: TextView
    private lateinit var tvCurrentLegend: TextView
    private lateinit var tvCompareLegend: TextView
    private lateinit var tvCurrentValue: TextView
    private lateinit var tvCompareValue: TextView

    private lateinit var currentMarker: Marker
    private lateinit var compareMarker: Marker

    private var currentRoute: List<RoutePoint> = emptyList()
    private var compareRoute: List<RoutePoint> = emptyList()

    private var currentSessionId: String = ""
    private var compareSessionId: String = ""
    private var currentOutingNumber: Int = 1
    private var compareOutingNumber: Int = 1
    private var currentLapNumber: Int = 1
    private var compareLapNumber: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        setContentView(R.layout.activity_track_lap_compare)
        applySystemBarsPaddingToRoot()

        bindViews()
        readExtras()

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        if (!loadRoutes()) {
            Toast.makeText(this, "Няма достатъчно lap данни за сравнение", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupMap()
        setupChart()
        updateByProgress(0f)
    }

    private fun bindViews() {
        map = findViewById(R.id.mapCompare)
        chart = findViewById(R.id.chartCompare)
        tvTitle = findViewById(R.id.tvTitle)
        tvCurrentLegend = findViewById(R.id.tvCurrentLegend)
        tvCompareLegend = findViewById(R.id.tvCompareLegend)
        tvCurrentValue = findViewById(R.id.tvCurrentValue)
        tvCompareValue = findViewById(R.id.tvCompareValue)
    }

    private fun readExtras() {
        currentSessionId = intent.getStringExtra("current_session_id") ?: ""
        compareSessionId = intent.getStringExtra("compare_session_id") ?: ""
        currentOutingNumber = intent.getIntExtra("current_outing_number", 1)
        compareOutingNumber = intent.getIntExtra("compare_outing_number", 1)
        currentLapNumber = intent.getIntExtra("current_lap_number", 1)
        compareLapNumber = intent.getIntExtra("compare_lap_number", 1)
        val trackName = intent.getStringExtra("track_name") ?: "Track"

        tvTitle.text = "$trackName • Lap Compare"
        tvCurrentLegend.text = "● Session #$currentOutingNumber • Lap #$currentLapNumber"
        tvCompareLegend.text = "● Session #$compareOutingNumber • Lap #$compareLapNumber"
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

        return currentRoute.size > 1 && compareRoute.size > 1
    }

    private fun normalizeRoute(points: List<RoutePoint>): List<RoutePoint> {
        if (points.isEmpty()) return emptyList()
        val baseTs = points.first().timestamp
        return points.map { it.copy(timestamp = it.timestamp - baseTs) }
    }

    private fun setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.setUseDataConnection(true)

        val currentPolyline = Polyline().apply {
            setPoints(currentRoute.map { GeoPoint(it.geoPoint.latitude, it.geoPoint.longitude) })
            color = Color.rgb(252, 120, 5)
            outlinePaint.strokeWidth = 12f
        }
        val comparePolyline = Polyline().apply {
            setPoints(compareRoute.map { GeoPoint(it.geoPoint.latitude, it.geoPoint.longitude) })
            color = Color.rgb(166, 76, 235)
            outlinePaint.strokeWidth = 10f
        }

        currentMarker = Marker(map).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setIcon(createDot(Color.rgb(252, 120, 5)))
        }
        compareMarker = Marker(map).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setIcon(createDot(Color.rgb(166, 76, 235)))
        }

        map.overlays.clear()
        map.overlays.add(comparePolyline)
        map.overlays.add(currentPolyline)
        map.overlays.add(compareMarker)
        map.overlays.add(currentMarker)

        val allPoints = currentRoute.map { GeoPoint(it.geoPoint.latitude, it.geoPoint.longitude) } +
            compareRoute.map { GeoPoint(it.geoPoint.latitude, it.geoPoint.longitude) }

        if (allPoints.size > 1) {
            val box = BoundingBox.fromGeoPointsSafe(allPoints)
            map.post { map.zoomToBoundingBox(box, true, 100) }
        }
    }

    private fun setupChart() {
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false
        chart.legend.isEnabled = true

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            axisMinimum = 0f
            axisMaximum = 100f
            granularity = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "${value.roundToInt()}%"
            }
        }

        val speedUnit = UnitsManager.getSpeedUnit(this).symbol
        val currentEntries = buildSpeedEntries(currentRoute)
        val compareEntries = buildSpeedEntries(compareRoute)

        val currentDataSet = LineDataSet(currentEntries, "Current ($speedUnit)").apply {
            color = Color.rgb(252, 120, 5)
            lineWidth = 2f
            setDrawValues(false)
            setDrawCircles(false)
        }

        val compareDataSet = LineDataSet(compareEntries, "Compare ($speedUnit)").apply {
            color = Color.rgb(166, 76, 235)
            lineWidth = 2f
            setDrawValues(false)
            setDrawCircles(false)
        }

        chart.data = LineData(currentDataSet, compareDataSet)
        chart.invalidate()

        chart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                updateByProgress(e?.x ?: 0f)
            }

            override fun onNothingSelected() {}
        })

        chart.setOnChartGestureListener(object : OnChartGestureListener {
            override fun onChartGestureStart(me: android.view.MotionEvent?, lastGesture: ChartTouchListener.ChartGesture?) {}
            override fun onChartGestureEnd(me: android.view.MotionEvent?, lastGesture: ChartTouchListener.ChartGesture?) {}
            override fun onChartLongPressed(me: android.view.MotionEvent?) {}
            override fun onChartDoubleTapped(me: android.view.MotionEvent?) {}
            override fun onChartSingleTapped(me: android.view.MotionEvent?) {}
            override fun onChartFling(me1: android.view.MotionEvent?, me2: android.view.MotionEvent?, velocityX: Float, velocityY: Float) {}
            override fun onChartScale(me: android.view.MotionEvent?, scaleX: Float, scaleY: Float) {
                val center = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                updateByProgress(center)
            }
            override fun onChartTranslate(me: android.view.MotionEvent?, dX: Float, dY: Float) {
                val center = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                updateByProgress(center)
            }
        })
    }

    private fun buildSpeedEntries(route: List<RoutePoint>): List<Entry> {
        if (route.isEmpty()) return emptyList()
        if (route.size == 1) return listOf(Entry(0f, route.first().speed))

        val speedUnit = UnitsManager.getSpeedUnit(this)
        return route.mapIndexed { index, point ->
            val progress = (index.toFloat() / (route.size - 1).toFloat()) * 100f
            Entry(progress, UnitsManager.convertSpeed(point.speed, speedUnit))
        }
    }

    private fun updateByProgress(progressPercent: Float) {
        val safeProgress = progressPercent.coerceIn(0f, 100f)

        val currentIndex = ((currentRoute.size - 1) * (safeProgress / 100f)).roundToInt().coerceIn(0, currentRoute.lastIndex)
        val compareIndex = ((compareRoute.size - 1) * (safeProgress / 100f)).roundToInt().coerceIn(0, compareRoute.lastIndex)

        val currentPoint = currentRoute[currentIndex]
        val comparePoint = compareRoute[compareIndex]

        val currentGeo = GeoPoint(currentPoint.geoPoint.latitude, currentPoint.geoPoint.longitude)
        val compareGeo = GeoPoint(comparePoint.geoPoint.latitude, comparePoint.geoPoint.longitude)

        currentMarker.position = currentGeo
        compareMarker.position = compareGeo

        val centerLat = (currentGeo.latitude + compareGeo.latitude) / 2.0
        val centerLon = (currentGeo.longitude + compareGeo.longitude) / 2.0
        map.controller.setCenter(GeoPoint(centerLat, centerLon))
        map.invalidate()

        val currentSpeedText = UnitsManager.formatSpeed(currentPoint.speed, this, 0)
        val compareSpeedText = UnitsManager.formatSpeed(comparePoint.speed, this, 0)
        tvCurrentValue.text = "Current: $currentSpeedText"
        tvCompareValue.text = "Compare: $compareSpeedText"
    }

    private fun createDot(color: Int): android.graphics.drawable.Drawable {
        val size = 42
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val outerPaint = android.graphics.Paint().apply {
            this.color = Color.WHITE
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3f, outerPaint)

        val innerPaint = android.graphics.Paint().apply {
            this.color = color
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 9f, innerPaint)

        return android.graphics.drawable.BitmapDrawable(resources, bitmap)
    }
}
