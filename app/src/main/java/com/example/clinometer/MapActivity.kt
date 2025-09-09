package com.example.clinometer
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.os.Bundle
import android.util.Log
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
import androidx.compose.ui.graphics.Paint
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
        val maxLeftLayout = findViewById<androidx.cardview.widget.CardView>(R.id.maxLeftLayout)
        val maxRightLayout = findViewById<LinearLayout>(R.id.maxRightLayout)

        if (isMotorcycle) {
            // Показваме данни за ъгли
            val maxLeftAngle = routePoints.filter { it.angle < 0 }.minByOrNull { it.angle }?.angle?.let { kotlin.math.abs(it) } ?: 0f
            val maxRightAngle = routePoints.filter { it.angle > 0 }.maxByOrNull { it.angle }?.angle ?: 0f

            // Показваме данни за ъгли
            findViewById<TextView>(R.id.tvMaxLeftInfo).text = getString(R.string.max_left_angle) + " " + "%.1f°".format(maxLeftAngle)
            findViewById<TextView>(R.id.tvMaxRightInfo).text = getString(R.string.max_right_angle) + " " + "%.1f°".format(maxRightAngle)
            findViewById<TextView>(R.id.tvDistanceMoto).apply {
                visibility = View.VISIBLE
                text = getString(R.string.distance_format) + " " + "%.2f".format(race.distance) + " " + getString(R.string.km_unit)
            }
            findViewById<TextView>(R.id.tvDistanceCar).visibility = View.GONE
            // 👇 ДОБАВЕТЕ този ред за да покажете картата с ъгли
            findViewById<androidx.cardview.widget.CardView>(R.id.maxLeftLayout).visibility = View.VISIBLE
        } else {
            // Скриваме целите редове за ъгли
            maxLeftLayout.visibility = View.GONE
            findViewById<TextView>(R.id.tvDistanceCar).apply {
                visibility = View.VISIBLE
                text = getString(R.string.distance_format) + " " + "%.2f".format(race.distance) + " " + getString(R.string.km_unit)
            }
            findViewById<TextView>(R.id.tvDistanceMoto).visibility = View.GONE
        }

        // Винаги показваме скоростта
        findViewById<TextView>(R.id.tvMaxSpeedInfo).text = getString(R.string.max_speed) + " " + "%.0f".format(race.maxSpeed) + " " + getString(R.string.speed_unit)

        val btnNewRoute = findViewById<Button>(R.id.btnStart)
        btnNewRoute.setText(R.string.new_session_button)
        btnNewRoute.setOnClickListener {
            startActivity(Intent(this, MainMapActivity::class.java))
            finish()
        }

        val avgSpeed = if (race.duration > 0) {
            (race.distance * 3600000) / race.duration // km/h = (km * ms_in_hour) / ms
        } else {
            0.0
        }
        findViewById<TextView>(R.id.tvAvgSpeedInfo).text = getString(R.string.avg_speed) + " " + "%.0f".format(avgSpeed) + " " + getString(R.string.speed_unit)


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


        val tvSessionTitle = findViewById<TextView>(R.id.tvSessionTitle)
// Използваме името на сесията, ако има такова, иначе показваме датата
        if (!race.name.isNullOrEmpty()) {
            tvSessionTitle.text = race.name
        } else {
            tvSessionTitle.text = formattedDate
        }

        Log.d("MapActivity", "Race name: ${race.name ?: "NULL"}")

        findViewById<View>(R.id.btnBack).setOnClickListener { onBackPressed() }

        map = findViewById(R.id.mapRoute)
        chart = findViewById(R.id.chart)
        tabLayout = findViewById(R.id.tabs)

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(routePoints.first().geoPoint)

        if (routePoints.size > 1) {
            for (i in 0 until routePoints.size - 1) {
                val startPoint = routePoints[i]
                val endPoint = routePoints[i + 1]

                // Изчисляваме ускорението (промяна в скоростта)
                val acceleration = endPoint.speed - startPoint.speed

                // Определяме максимално ускорение за нормализация (примерно ±30 km/h промяна)
                val maxAcceleration = 30f

                // Нормализираме от -1 до +1
                val accelRatio = (acceleration / maxAcceleration).coerceIn(-1f, 1f)

                // Определяме цвета според ускорението
                val color = when {
                    startPoint.speed < 2f -> {
                        // Ако сме почти спрели - тъмно червено
                        Color.rgb(139, 0, 0)
                    }
                    accelRatio > 0.66f -> {
                        // Силно ускорение - тъмно зелено
                        Color.rgb(0, 128, 0)
                    }
                    accelRatio > 0.33f -> {
                        // Средно ускорение - зелено
                        Color.rgb(0, 200, 0)
                    }
                    accelRatio > 0 -> {
                        // Леко ускорение - жълто-зелено
                        Color.rgb(154, 205, 50)
                    }
                    accelRatio == 0f -> {
                        // Постоянна скорост - жълто
                        Color.rgb(255, 255, 0)
                    }
                    accelRatio > -0.5f -> {
                        // Леко забавяне - оранжево
                        Color.rgb(255, 165, 0)
                    }
                    else -> {
                        // Силно забавяне - червено-оранжево
                        val ratio = (-accelRatio - 0.5f) * 2
                        val green = (165 * (1 - ratio)).toInt()
                        Color.rgb(255, green, 0)
                    }
                }

                // Създаваме сегмент
                val segmentPolyline = Polyline().apply {
                    setPoints(listOf(startPoint.geoPoint, endPoint.geoPoint))
                    this.color = color
                    outlinePaint.strokeWidth = 18f
                }
                map.overlays.add(segmentPolyline)
            }
        }

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

    // Добавете този помощен метод за форматиране на времето:
    private fun formatTimeForReader(seconds: Float): String {
        val totalSeconds = seconds.toLong().coerceAtLeast(0)
        val min = totalSeconds / 60
        val sec = totalSeconds % 60
        return String.format("%02d:%02d", min, sec)
    }
    private fun setupChart(isMotorcycle: Boolean) {
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(false) // Изключваме вграденото scaling
        chart.setPinchZoom(false)
        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false

        // ИЗКЛЮЧВАМЕ НАПЪЛНО ИНЕРЦИЯТА/ЕЛАСТИЧНИЯ ЕФЕКТ
        chart.isDragDecelerationEnabled = false
        chart.dragDecelerationFrictionCoef = 0f

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(x: Float): String {
                    val totalSeconds = x.toLong()
                    val min = (totalSeconds / 60)
                    val sec = totalSeconds % 60
                    return String.format("%02d:%02d", min, sec)
                }
            }
        }

        // Настройваме границите с extra space
        if (routePoints.isNotEmpty()) {
            val firstTime = routePoints.first().timestamp / 1000f
            val lastTime = routePoints.last().timestamp / 1000f
            val duration = lastTime - firstTime

            chart.xAxis.axisMinimum = firstTime - duration
            chart.xAxis.axisMaximum = lastTime + duration

            chart.moveViewToX(firstTime - duration * 0.1f)

            chart.setVisibleXRangeMaximum(duration)

            val initialCenterX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
            updateReaderPosition(initialCenterX)
        }

        // Добавяме червена линия като LimitLine на X оста
        val centerLine = com.github.mikephil.charting.components.LimitLine(0f).apply {
            lineColor = android.graphics.Color.RED
            lineWidth = 2f
            enableDashedLine(10f, 10f, 0f)
        }

        // Използваме ViewPortHandler за рисуване на линията
        chart.setExtraOffsets(0f, 0f, 0f, 0f)

        // Override на renderer за рисуване на линията
        val originalRenderer = chart.renderer
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

                // Рисуваме червената линия в центъра
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.RED
                    strokeWidth = 3f
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
                }

                val centerX = mViewPortHandler.contentCenter.x
                c.drawLine(
                    centerX,
                    mViewPortHandler.contentTop(),
                    centerX,
                    mViewPortHandler.contentBottom(),
                    paint
                )

                // Добавяме текст за времето
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.RED
                    textSize = 35f
                    isAntiAlias = true
                    isFakeBoldText = true
                }

                val centerValue = (mChart.lowestVisibleX + mChart.highestVisibleX) / 2f
                val timeText = formatTimeForReader(centerValue)
                c.drawText(timeText, centerX + 10, mViewPortHandler.contentTop() + 40, textPaint)
            }
        }

        // Променливи за контрол на zoom/pan
        var isZooming = false
        var zoomCenterX = 0f

        // Запазваме границите на данните
        val dataStartTime = if (routePoints.isNotEmpty()) routePoints.first().timestamp / 1000f else 0f
        val dataEndTime = if (routePoints.isNotEmpty()) routePoints.last().timestamp / 1000f else 0f

        // ScaleGestureDetector за zoom БЕЗ движение
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isZooming = true
                // Запазваме центъра преди zoom
                zoomCenterX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val deltaX = abs(detector.currentSpanX - detector.previousSpanX)
                val deltaY = abs(detector.currentSpanY - detector.previousSpanY)

                val scaleFactorX = if (deltaX > deltaY * 1.5) detector.scaleFactor else 1f
                val scaleFactorY = if (deltaY > deltaX * 1.5) detector.scaleFactor else 1f

                if (deltaX <= deltaY * 1.5 && deltaY <= deltaX * 1.5) {
                    // Зумваме и по двете оси
                    chart.zoom(detector.scaleFactor, detector.scaleFactor,
                        chart.width / 2f, chart.height / 2f,
                        com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT)
                } else {
                    // Зумваме само по една ос
                    chart.zoom(scaleFactorX, scaleFactorY,
                        chart.width / 2f, chart.height / 2f,
                        com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT)
                }

                // Връщаме се на същата позиция след zoom с проверка на границите
                var targetX = zoomCenterX - chart.visibleXRange / 2f
                val visibleRange = chart.visibleXRange

                // Проверяваме дали червената линия (центъра) не излиза извън данните
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
                // Обновяваме позицията
                val centerX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                updateReaderPosition(centerX)
            }
        })

        // Touch listener - САМО ТУК ДОБАВЯМ ПРОВЕРКА
        chart.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)

            // Позволяваме движение само ако НЕ зумваме
            if (!isZooming) {
                // Запазваме позицията преди движението
                val beforeCenter = (chart.lowestVisibleX + chart.highestVisibleX) / 2f

                // Изпълняваме движението
                chart.onTouchEvent(event)

                // ПРОВЕРКА: След движението проверяваме дали червената линия е в границите
                val currentCenter = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                val visibleRange = chart.visibleXRange

                // Ако червената линия излиза извън данните, връщаме я на границата
                if (currentCenter < dataStartTime) {
                    chart.moveViewToX(dataStartTime - visibleRange / 2f)
                    // Спираме всякакво инерционно движение
                    chart.isDragEnabled = false
                    chart.postDelayed({ chart.isDragEnabled = true }, 1)
                } else if (currentCenter > dataEndTime) {
                    chart.moveViewToX(dataEndTime - visibleRange / 2f)
                    // Спираме всякакво инерционно движение
                    chart.isDragEnabled = false
                    chart.postDelayed({ chart.isDragEnabled = true }, 1)
                }
            }

            // Обновяваме при край на докосване
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                val centerX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                updateReaderPosition(centerX)
                chart.invalidate() // Force redraw
            }

            true
        }

        // Gesture listener
        chart.setOnChartGestureListener(object : OnChartGestureListener {
            override fun onChartGestureStart(me: MotionEvent?, lastGesture: ChartTouchListener.ChartGesture?) {}
            override fun onChartGestureEnd(me: MotionEvent?, lastGesture: ChartTouchListener.ChartGesture?) {}
            override fun onChartLongPressed(me: MotionEvent?) {}
            override fun onChartDoubleTapped(me: MotionEvent?) {
                chart.fitScreen()
                val centerX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                updateReaderPosition(centerX)
            }
            override fun onChartSingleTapped(me: MotionEvent?) {}
            override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {
                // НАПЪЛНО БЛОКИРАМЕ FLING - без инерция, без еластичен ефект
                // Не правим нищо тук
            }
            override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {}

            override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
                if (!isZooming) {
                    val currentCenterX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                    val visibleRange = chart.visibleXRange

                    // ПРОВЕРКА: Не позволяваме червената линия да излиза извън данните
                    if (currentCenterX < dataStartTime) {
                        chart.moveViewToX(dataStartTime - visibleRange / 2f)
                        // Спираме инерцията
                        chart.isDragEnabled = false
                        chart.postDelayed({ chart.isDragEnabled = true }, 1)
                    } else if (currentCenterX > dataEndTime) {
                        chart.moveViewToX(dataEndTime - visibleRange / 2f)
                        // Спираме инерцията
                        chart.isDragEnabled = false
                        chart.postDelayed({ chart.isDragEnabled = true }, 1)
                    } else {
                        updateReaderPosition(currentCenterX)
                        chart.invalidate()
                    }
                }
            }
        })

        // Цветове
        chart.xAxis.textColor = android.graphics.Color.WHITE
        chart.axisLeft.textColor = android.graphics.Color.WHITE
        chart.legend.textColor = android.graphics.Color.WHITE

        // Force initial draw
        chart.invalidate()
    }

    private fun updateReaderPosition(timeInSeconds: Float) {
        val index = findClosestIndexToTime(timeInSeconds)
        if (index in routePoints.indices) {
            val point = routePoints[index]
            marker.position = point.geoPoint
            map.controller.setCenter(point.geoPoint)

            val currentProfileId = ProfileStorage.getSelectedProfileId(this)
            val profiles = ProfileStorage.loadProfiles(this)
            val profile = profiles.find { it.id == currentProfileId }

            findViewById<TextView>(R.id.tvReaderSpeed).text =
                "${getString(R.string.speed_label)} ${"%.0f".format(point.speed)} ${getString(R.string.speed_unit)}"

            if (profile?.vehicleType == Profile.VehicleType.MOTORCYCLE) {
                findViewById<TextView>(R.id.tvReaderAngle).apply {
                    visibility = View.VISIBLE
                    text = "${getString(R.string.angle_label)} ${"%.1f".format(point.angle)}${getString(R.string.angle_unit)}"
                }
            } else {
                findViewById<TextView>(R.id.tvReaderAngle).visibility = View.GONE
            }

            map.invalidate()
        }
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

        val activeColor = if (mode == Mode.SPEED) Color.rgb(252, 120, 5) else Color.rgb(5, 252, 227)
        val fadedColor = if (mode == Mode.SPEED) Color.argb(105,5, 252, 227) else Color.argb(105,252, 120, 5)

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