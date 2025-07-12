package com.example.clinometer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
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
import com.google.android.material.tabs.TabLayout
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.ScaleGestureDetector
import com.github.mikephil.charting.components.YAxis
import kotlin.math.abs

class MapActivity : AppCompatActivity() {
    private lateinit var routePoints: List<RoutePoint>
    private lateinit var map: MapView
    private lateinit var marker: Marker
    private lateinit var chart: LineChart
    private lateinit var tabLayout: TabLayout
    private var currentMode: Mode = Mode.SPEED
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private enum class Mode {
        SPEED, ANGLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        setContentView(R.layout.activity_map)

        // Зареждане на профила
        val currentProfileId = ProfileStorage.getSelectedProfileId(this)
        val profiles = ProfileStorage.loadProfiles(this)
        val profile = profiles.find { it.id == currentProfileId }
        val isMotorcycle = profile?.vehicleType == Profile.VehicleType.MOTORCYCLE

        // Взимаме ID на сесията
        val raceId = intent.getLongExtra("RACE_ID", -1)
        if (raceId == -1L) {
            Toast.makeText(this, R.string.error_missing_session_id, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Зареждаме метаданните
        val races = RouteStorage.loadRaces(this)
        val race = races.find { it.id == raceId }
        if (race == null) {
            Toast.makeText(this, R.string.error_session_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Зареждаме точките от хранилището
        routePoints = RouteStorage.loadRoutePoints(this, raceId)

        // Показване/скриване на елементите за ъгли според типа превозно средство
        val maxLeftLayout = findViewById<LinearLayout>(R.id.maxLeftLayout)
        val maxRightLayout = findViewById<LinearLayout>(R.id.maxRightLayout)
        val distanceKm = race.distance / 1000.0
        // Коригирано за разстояние
        val distanceText = "%.2f".format(distanceKm) + " " + getString(R.string.km_unit)

        if (isMotorcycle) {
            // Показваме данни за ъгли
            findViewById<TextView>(R.id.tvMaxLeftInfo).text = getString(R.string.max_left_angle) + " " + "%.1f°".format(race.maxLeftAngle)
            findViewById<TextView>(R.id.tvMaxRightInfo).text = getString(R.string.max_right_angle) + " " + "%.1f°".format(race.maxRightAngle)
            findViewById<TextView>(R.id.tvDistanceMoto).text = getString(R.string.distance_format) + " " + "%.2f".format(race.distance) + " " + getString(R.string.km_unit)
            findViewById<LinearLayout>(R.id.distanceCarContainer).visibility = View.GONE
        } else {
            // Скриваме целите редове за ъгли (включително точките)
            maxLeftLayout.visibility = View.GONE
            maxRightLayout.visibility = View.GONE
            // Коригиран код за автомобили
            findViewById<TextView>(R.id.tvDistanceCar).text = getString(R.string.distance_format) + " " + distanceText
            findViewById<LinearLayout>(R.id.distanceCarContainer).visibility = View.VISIBLE
            findViewById<LinearLayout>(R.id.distanceMotoContainer).visibility = View.GONE
        }

        // Винаги показваме скоростта
        findViewById<TextView>(R.id.tvMaxSpeedInfo).text = getString(R.string.max_speed) + " " + "%.0f".format(race.maxSpeed) + " " + getString(R.string.speed_unit)

        // Покажете ускоренията
        val zeroTo100Text = if (race.time0to100 > 0) "%.3fs".format(race.time0to100 / 1_000_000_000.0) else getString(R.string.accel_not_available)
        findViewById<TextView>(R.id.tvZeroTo100).text = getString(R.string.accel_0_100) + ": " + zeroTo100Text

        val zeroTo200Text = if (race.time0to200 > 0) "%.3fs".format(race.time0to200 / 1_000_000_000.0) else getString(R.string.accel_not_available)
        findViewById<TextView>(R.id.tvZeroTo200).text = getString(R.string.accel_0_200) + ": " + zeroTo200Text

        val hundredTo200Text = if (race.time100to200 > 0) "%.3fs".format(race.time100to200 / 1_000_000_000.0) else getString(R.string.accel_not_available)
        findViewById<TextView>(R.id.tvHundredTo200).text = getString(R.string.accel_100_200) + ": " + hundredTo200Text


        val btnNewRoute = findViewById<Button>(R.id.btnStart)
        btnNewRoute.setText(R.string.new_session_button)
        btnNewRoute.setOnClickListener {
            startActivity(Intent(this, StartActivity::class.java))
            finish()
        }

        // Проверка дали има данни за маршрут
        if (routePoints.isEmpty()) {
            Toast.makeText(this, R.string.error_no_route_data, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val sessionTimestamp = routePoints.firstOrNull()?.absoluteTime ?: System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(sessionTimestamp))

        // Покажете датата
        val sessionDateTime = findViewById<TextView>(R.id.sessionDateTime)
        sessionDateTime.text = getString(R.string.created_date_format, formattedDate)

        map = findViewById(R.id.mapRoute)
        chart = findViewById(R.id.chart)
        tabLayout = findViewById(R.id.tabs)

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(routePoints.first().geoPoint)

        val polyline = Polyline().apply {
            setPoints(routePoints.map { it.geoPoint })
            color = Color.rgb(0, 25, 255)
            outlinePaint.strokeWidth = 8f
        }
        map.overlays.add(polyline)

        marker = Marker(map).apply {
            position = routePoints.first().geoPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = getString(R.string.location_title)
        }
        map.overlays.add(marker)

        // Пресмятаме точните секунди
        findViewById<TextView>(R.id.tvTotalTime).text = getString(R.string.time_format, formatTime(race.duration))

        setupChart(isMotorcycle)
        setupTabs(isMotorcycle)
        updateChartData(currentMode, isMotorcycle)
    }

    private fun formatAccelerationTime(timeNanos: Long): String {
        return if (timeNanos > 0) "%.3f".format(timeNanos / 1_000_000_000.0) + "s" else "--"
    }

    private fun setupChart(isMotorcycle: Boolean) {
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(false) // Деактивираме вграденото pinch zoom
        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false
        chart.xAxis.axisMinimum = 0f

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            axisMinimum = 0f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(x: Float): String {
                    val totalSeconds = x.toLong()
                    val min = (totalSeconds / 60)
                    val sec = totalSeconds % 60
                    return String.format("%02d:%02d", min, sec)
                }
            }
        }

        // Инициализираме ScaleGestureDetector с персонализирана логика
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactorX: Float
                val scaleFactorY: Float

                // Определяме посоката на жеста
                val deltaX = abs(detector.currentSpanX - detector.previousSpanX)
                val deltaY = abs(detector.currentSpanY - detector.previousSpanY)
                val isHorizontal = deltaX > deltaY * 2
                val isVertical = deltaY > deltaX * 2

                when {
                    isHorizontal -> {
                        // Зум само по X ос
                        scaleFactorX = detector.scaleFactor
                        scaleFactorY = 1f
                    }
                    isVertical -> {
                        // Зум само по Y ос
                        scaleFactorX = 1f
                        scaleFactorY = detector.scaleFactor
                    }
                    else -> {
                        // Диагонален жест - прилагаме зум по двете оси
                        scaleFactorX = detector.scaleFactor
                        scaleFactorY = detector.scaleFactor
                    }
                }

                // Прилагаме зума
                chart.zoom(scaleFactorX, scaleFactorY, detector.focusX, detector.focusY, YAxis.AxisDependency.LEFT)
                return true
            }
        })

        // Задаваме слушател за докосвания
        chart.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            chart.onTouchEvent(event) // Позволяваме нормалното докосване
            true
        }

        chart.setOnChartGestureListener(object : OnChartGestureListener {
            override fun onChartGestureStart(me: MotionEvent?, lastGesture: ChartTouchListener.ChartGesture?) {}
            override fun onChartGestureEnd(me: MotionEvent?, lastGesture: ChartTouchListener.ChartGesture?) {}
            override fun onChartLongPressed(me: MotionEvent?) {}
            override fun onChartDoubleTapped(me: MotionEvent?) {}
            override fun onChartSingleTapped(me: MotionEvent?) {}
            override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}
            override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {}

            override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
                val centerX = (chart.lowestVisibleX + chart.highestVisibleX) / 2
                val index = findClosestIndexToTime(centerX)
                if (index in routePoints.indices) {
                    val point = routePoints[index]
                    marker.position = point.geoPoint
                    map.controller.setCenter(point.geoPoint)
                    updateInfoDisplay(point)
                    map.invalidate()
                }
            }
        })

        chart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                e?.let {
                    val index = findClosestIndexToTime(it.x)
                    if (index in routePoints.indices) {
                        val point = routePoints[index]
                        marker.position = point.geoPoint
                        map.controller.setCenter(point.geoPoint)
                        updateInfoDisplay(point)
                        map.invalidate()
                    }
                }
            }

            override fun onNothingSelected() {}
        })
    }

    private fun setupTabs(isMotorcycle: Boolean) {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_speed))

        // Добавяме таб за ъгъл само за мотоциклети
        if (isMotorcycle) {
            tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_angle))
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentMode = if (tab?.position == 0) Mode.SPEED else Mode.ANGLE
                updateChartData(currentMode, isMotorcycle)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateChartData(mode: Mode, isMotorcycle: Boolean) {
        val speedEntries = routePoints.map { Entry(it.timestamp / 1000f, it.speed) }
        val angleEntries = if (isMotorcycle) {
            routePoints.map { Entry(it.timestamp / 1000f, it.angle) }
        } else {
            emptyList()
        }

        val activeColor = if (mode == Mode.SPEED) Color.RED else Color.BLUE
        val fadedColor = if (mode == Mode.SPEED) Color.argb(105, 0, 0, 255) else Color.argb(105, 255, 0, 0)

        val speedDataSet = LineDataSet(speedEntries, getString(R.string.chart_speed_legend)).apply {
            color = if (mode == Mode.SPEED) activeColor else fadedColor
            lineWidth = if (mode == Mode.SPEED) 2f else 1f
            setDrawValues(false)
            setDrawCircles(false)
            if (mode != Mode.SPEED) enableDashedLine(10f, 5f, 0f)
        }

        val lineData = if (isMotorcycle) {
            val angleDataSet = LineDataSet(angleEntries, getString(R.string.chart_angle_legend)).apply {
                color = if (mode == Mode.ANGLE) activeColor else fadedColor
                lineWidth = if (mode == Mode.ANGLE) 2f else 1f
                setDrawValues(false)
                setDrawCircles(false)
                if (mode != Mode.ANGLE) enableDashedLine(10f, 5f, 0f)
            }
            LineData(speedDataSet, angleDataSet)
        } else {
            LineData(speedDataSet)
        }

        chart.data = lineData

        val yAxis = chart.axisLeft
        when (mode) {
            Mode.SPEED -> {
                val maxSpeed = routePoints.maxOfOrNull { it.speed } ?: 200f
                yAxis.axisMinimum = 0f
                yAxis.axisMaximum = if (maxSpeed > 200) maxSpeed * 1.1f else 200f
                yAxis.setDrawZeroLine(true)
                yAxis.zeroLineColor = Color.GRAY
                yAxis.zeroLineWidth = 1f
            }
            Mode.ANGLE -> {
                yAxis.axisMinimum = -90f
                yAxis.axisMaximum = 90f
                yAxis.setDrawZeroLine(true)
                yAxis.zeroLineColor = Color.GRAY
                yAxis.zeroLineWidth = 1f
            }
        }

        chart.invalidate()
    }

    private fun findClosestIndexToTime(targetTimeSeconds: Float): Int {
        var closestIndex = 0
        var minDiff = Float.MAX_VALUE
        routePoints.forEachIndexed { index, routePoint ->
            val pointTimeSeconds = routePoint.timestamp / 1000f
            val diff = Math.abs(pointTimeSeconds - targetTimeSeconds)
            if (diff < minDiff) {
                minDiff = diff
                closestIndex = index
            }
        }
        return closestIndex
    }

    private fun updateInfoDisplay(point: RoutePoint) {
        val formattedTime = formatTime(point.timestamp)
        val tv = findViewById<TextView>(R.id.tvInfo)

        val currentProfileId = ProfileStorage.getSelectedProfileId(this)
        val profiles = ProfileStorage.loadProfiles(this)
        val profile = profiles.find { it.id == currentProfileId }

        val infoText = if (profile?.vehicleType == Profile.VehicleType.MOTORCYCLE) {
            """
            ${getString(R.string.speed_label)} ${"%.0f".format(point.speed)} ${getString(R.string.speed_unit)}
            ${getString(R.string.angle_label)} ${"%.1f".format(point.angle)}${getString(R.string.angle_unit)}
            ${getString(R.string.time_label)} $formattedTime
            """.trimIndent()
        } else {
            """
            ${getString(R.string.speed_label)} ${"%.0f".format(point.speed)} ${getString(R.string.speed_unit)}
            ${getString(R.string.time_label)} $formattedTime
            """.trimIndent()
        }

        tv.text = infoText
        tv.textSize = 14f
    }

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = millis / (1000 * 60 * 60)
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onBackPressed() {
        val intent = Intent(this, RacesActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}