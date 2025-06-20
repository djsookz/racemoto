package com.example.clinometer

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
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

class MapActivity : AppCompatActivity() {
    private lateinit var routePoints: List<RoutePoint>
    private lateinit var map: MapView
    private lateinit var marker: Marker
    private lateinit var chart: LineChart
    private lateinit var tabLayout: TabLayout
    private var currentMode: Mode = Mode.SPEED

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

        // Взимаме целия Race обект от интента
        val race = intent.getParcelableExtra<Race>("RACE")
        if (race == null) {
            Toast.makeText(this, "Грешка: липсват данни за сесията", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Използваме данните от race обекта
        routePoints = race.routePoints
        val maxLeft = race.maxLeftAngle.toInt()
        val maxRight = race.maxRightAngle.toInt()
        val maxSpeed = race.maxSpeed.toInt()
        val time0to100 = race.time0to100
        val time0to200 = race.time0to200
        val time100to200 = race.time100to200
        val totalTime = race.duration

        // Сложи ги в текстовите полета
        findViewById<TextView>(R.id.tvMaxLeftInfo).text = getString(R.string.max_left_angle, maxLeft)
        findViewById<TextView>(R.id.tvMaxRightInfo).text = getString(R.string.max_right_angle, maxRight)
        findViewById<TextView>(R.id.tvMaxSpeedInfo).text = getString(R.string.max_speed, maxSpeed)

        // Покажете ускоренията
        findViewById<TextView>(R.id.tvZeroTo100).text = "0-100 км/ч: ${formatAccelerationTime(time0to100)}"
        findViewById<TextView>(R.id.tvZeroTo200).text = "0-200 км/ч: ${formatAccelerationTime(time0to200)}"
        findViewById<TextView>(R.id.tvHundredTo200).text = "100-200 км/ч: ${formatAccelerationTime(time100to200)}"

        val btnNewRoute = findViewById<Button>(R.id.btnStart)
        btnNewRoute.text = "НОВА СЕСИЯ"
        btnNewRoute.setOnClickListener {
            startActivity(Intent(this, CountdownActivity::class.java))
        }

        // Проверка дали има данни за маршрут
        if (routePoints.isEmpty()) {
            Toast.makeText(this, "Няма данни за маршрут", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val sessionTimestamp = routePoints.firstOrNull()?.absoluteTime ?: System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(sessionTimestamp))

        // Покажете датата
        val sessionDateTime = findViewById<TextView>(R.id.sessionDateTime)
        sessionDateTime.text = "Създаден: $formattedDate"

        map = findViewById(R.id.mapRoute)
        chart = findViewById(R.id.chart)
        tabLayout = findViewById(R.id.tabs)

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(routePoints.first().geoPoint)

        val polyline = Polyline().apply {
            setPoints(routePoints.map { it.geoPoint })
            outlinePaint.strokeWidth = 8f
        }
        map.overlays.add(polyline)

        marker = Marker(map).apply {
            position = routePoints.first().geoPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Точно местоположение"
        }
        map.overlays.add(marker)

        // Пресмятаме точните секунди
        findViewById<TextView>(R.id.tvTotalTime).text = "Време: ${formatTime(totalTime)}"

        setupChart()
        setupTabs()
        updateChartData(currentMode)
    }

    private fun formatAccelerationTime(timeMs: Long): String {
        return if (timeMs > 0) {
            "%.1f".format(timeMs / 1000.0) + "s" // Форматиране до 1 цифра след десетичната запетая
        } else {
            "--"
        }
    }

    private fun setupChart() {
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
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

    private fun setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("Скорост"))
        tabLayout.addTab(tabLayout.newTab().setText("Ъгъл"))
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentMode = if (tab?.position == 0) Mode.SPEED else Mode.ANGLE
                updateChartData(currentMode)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateChartData(mode: Mode) {
        val speedEntries = routePoints.map { Entry(it.timestamp / 1000f, it.speed) }
        val angleEntries = routePoints.map { Entry(it.timestamp / 1000f, it.angle) }

        val activeColor = if (mode == Mode.SPEED) Color.RED else Color.BLUE
        val fadedColor = if (mode == Mode.SPEED) Color.argb(105, 0, 0, 255) else Color.argb(105, 255, 0, 0)

        val speedDataSet = LineDataSet(speedEntries, "Скорост (km/h)").apply {
            color = if (mode == Mode.SPEED) activeColor else fadedColor
            lineWidth = if (mode == Mode.SPEED) 2f else 1f
            setDrawValues(false)
            setDrawCircles(false)
            if (mode != Mode.SPEED) enableDashedLine(10f, 5f, 0f)
        }

        val angleDataSet = LineDataSet(angleEntries, "Ъгъл (°)").apply {
            color = if (mode == Mode.ANGLE) activeColor else fadedColor
            lineWidth = if (mode == Mode.ANGLE) 2f else 1f
            setDrawValues(false)
            setDrawCircles(false)
            if (mode != Mode.ANGLE) enableDashedLine(10f, 5f, 0f)
        }

        chart.data = LineData(speedDataSet, angleDataSet)

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
                yAxis.axisMinimum = -70f
                yAxis.axisMaximum = 70f
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
        val timeInSeconds = point.timestamp / 1000
        val formattedTime = formatTime(timeInSeconds * 1000)
        val tv = findViewById<TextView>(R.id.tvInfo)

        findViewById<TextView>(R.id.tvInfo).text = """
            Скорост: ${"%.0f".format(point.speed)} км/ч
            Ъгъл: ${"%.1f".format(point.angle)}°
            Време: $formattedTime
        """.trimIndent()
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