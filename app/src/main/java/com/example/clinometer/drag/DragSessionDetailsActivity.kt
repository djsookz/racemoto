package com.example.clinometer

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.drag.MeasurementMode
import com.example.clinometer.drag.PointTooltipMarker
import com.example.clinometer.drag.SessionSelectionActivity
import com.example.clinometer.settings.LanguageManager
import com.example.clinometer.settings.UnitsManager
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import android.graphics.Canvas
import com.github.mikephil.charting.utils.MPPointF

class DragSessionDetailsActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private lateinit var tvSessionName: TextView
    private lateinit var tvSessionDate: TextView
    private lateinit var llEnvironment: LinearLayout
    private lateinit var tvDetailTemperature: TextView
    private lateinit var tvDetailAltitude: TextView
    private lateinit var tvBest0to100: TextView
    private lateinit var tvBest0to200: TextView
    private lateinit var tvBest100to200: TextView
    private lateinit var tvBest0to402: TextView
    
    private lateinit var tvLabelBest0to100: TextView
    private lateinit var tvLabelBest0to200: TextView
    private lateinit var tvLabelBest100to200: TextView
    private lateinit var tvLabelBest0to402: TextView
    private lateinit var rvAttempts: RecyclerView
    private lateinit var tvNoAttempts: TextView

    private var session: DragSession? = null
    private var sessionId: Long = -1L
    private lateinit var attemptsAdapter: DragAttemptsAdapter
    private var measurementMode: MeasurementMode = MeasurementMode.ALL
    @Volatile
    private var closestTo200Normalized: Float? = null // За 200 km/h маркер в 100-200 режим

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drag_session_details)
        
        setupScreenKeepOn()

        sessionId = intent.getLongExtra("SESSION_ID", -1L)
        if (sessionId == -1L) {
            finish()
            return
        }

        session = DragStorage.getDragSession(this, sessionId)
        if (session == null) {
            finish()
            return
        }
        
        // Зареждаме measurement mode от сесията
        measurementMode = try {
            val modeString = session?.measurementMode ?: "ALL"
            MeasurementMode.valueOf(modeString)
        } catch (e: Exception) {
            MeasurementMode.ALL
        }

        initializeViews()
        displaySessionData()
        setupRecyclerView()
    }
    
    private fun setupScreenKeepOn() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        updateScreenKeepOn(prefs.getBoolean("always_on_display", false))

        prefs.registerOnSharedPreferenceChangeListener { shared, key ->
            if (key == "always_on_display") {
                updateScreenKeepOn(shared.getBoolean(key, false))
            }
        }
    }

    private fun updateScreenKeepOn(keepOn: Boolean) {
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun initializeViews() {
        tvSessionName = findViewById(R.id.tvDetailSessionName)
        tvSessionDate = findViewById(R.id.tvDetailSessionDate)
        llEnvironment = findViewById(R.id.llEnvironment)
        tvDetailTemperature = findViewById(R.id.tvDetailTemperature)
        tvDetailAltitude = findViewById(R.id.tvDetailAltitude)
        tvBest0to100 = findViewById(R.id.tvDetailBest0to100)
        tvBest0to200 = findViewById(R.id.tvDetailBest0to200)
        tvBest100to200 = findViewById(R.id.tvDetailBest100to200)
        tvBest0to402 = findViewById(R.id.tvDetailBest0to402)
        
        tvLabelBest0to100 = findViewById(R.id.tvLabelBest0to100)
        tvLabelBest0to200 = findViewById(R.id.tvLabelBest0to200)
        tvLabelBest100to200 = findViewById(R.id.tvLabelBest100to200)
        tvLabelBest0to402 = findViewById(R.id.tvLabelBest0to402)
        
        // Update labels with current unit
        val speedUnit = UnitsManager.getSpeedUnit(this)
        val speed100 = UnitsManager.convertSpeed(100f, speedUnit).toInt()
        val speed200 = UnitsManager.convertSpeed(200f, speedUnit).toInt()
        tvLabelBest0to100.text = "0-$speed100 ${speedUnit.symbol}:"
        tvLabelBest0to200.text = "0-$speed200 ${speedUnit.symbol}:"
        tvLabelBest100to200.text = "$speed100-$speed200 ${speedUnit.symbol}:"
        tvLabelBest0to402.text = "0-${UnitsManager.getQuarterMileDistance(this)}:"
        
        rvAttempts = findViewById(R.id.rvDetailAttempts)
        tvNoAttempts = findViewById(R.id.tvNoAttempts)

        findViewById<View>(R.id.btnBack)?.setOnClickListener {
            finish()
        }
        
        findViewById<View>(R.id.btnCompare)?.setOnClickListener {
            // Отваряме страницата за избор на сесия и опит за сравняване
            val intent = android.content.Intent(this, SessionSelectionActivity::class.java)
            intent.putExtra("current_session_id", sessionId)
            intent.putExtra("current_attempt_id", session?.attempts?.firstOrNull()?.id ?: -1)
            startActivity(intent)
        }
    }

    private fun displaySessionData() {
        session?.let { s ->
            tvSessionName.text = s.name ?: "Drag Session"
            tvSessionDate.text = android.text.format.DateFormat.format(
                "dd.MM.yyyy HH:mm",
                java.util.Date(s.timestamp)
            ).toString()

            val hasTemperature = s.temperature != null
            val hasAltitude = s.altitude != null

            if (hasTemperature || hasAltitude) {
                llEnvironment.visibility = View.VISIBLE

                if (hasTemperature) {
                    tvDetailTemperature.text = UnitsManager.formatTemperature(s.temperature!!, this, decimals = 0)
                    (tvDetailTemperature.parent as View).visibility = View.VISIBLE
                } else {
                    (tvDetailTemperature.parent as View).visibility = View.GONE
                }

                if (hasAltitude) {
                    tvDetailAltitude.text = "${s.altitude?.toInt()}m"
                    (tvDetailAltitude.parent as View).visibility = View.VISIBLE
                } else {
                    (tvDetailAltitude.parent as View).visibility = View.GONE
                }
            } else {
                llEnvironment.visibility = View.GONE
            }

            val mode = try {
                MeasurementMode.valueOf(s.measurementMode ?: "ALL")
            } catch (e: Exception) {
                MeasurementMode.ALL
            }

            // Скриваме всички контейнери първоначално
            findViewById<LinearLayout>(R.id.ll0to100).visibility = View.GONE
            findViewById<LinearLayout>(R.id.ll0to200).visibility = View.GONE
            findViewById<LinearLayout>(R.id.ll100to200).visibility = View.GONE
            findViewById<LinearLayout>(R.id.ll0to402).visibility = View.GONE

            when (mode) {
                MeasurementMode.ZERO_TO_100 -> {
                    tvBest0to100.text = formatTimeWithLabel(null,s.best0to100)
                    findViewById<LinearLayout>(R.id.ll0to100).visibility = View.VISIBLE
                }
                MeasurementMode.ZERO_TO_200 -> {
                    tvBest0to200.text = formatTimeWithLabel(null,s.best0to200)
                    findViewById<LinearLayout>(R.id.ll0to200).visibility = View.VISIBLE
                }
                MeasurementMode.HUNDRED_TO_200 -> {
                    tvBest100to200.text = formatTimeWithLabel(null,s.best100to200)
                    findViewById<LinearLayout>(R.id.ll100to200).visibility = View.VISIBLE
                }
                MeasurementMode.QUARTER_MILE -> {
                    tvBest0to402.text = formatTimeWithLabel(null,s.best0to402)
                    findViewById<LinearLayout>(R.id.ll0to402).visibility = View.VISIBLE
                }
                MeasurementMode.ALL -> {
                    // Задаваме стойностите на всички TextView елементи и ги показваме
                    // Използваме нормализирани времена спрямо първия опит за съответствие с графиката
                    val firstAttempt = s.attempts.firstOrNull()
                    
                    
                    tvBest0to100.text = formatTimeWithLabelNormalized(null, s.best0to100, firstAttempt)
                    tvBest0to200.text = formatTimeWithLabelNormalized(null, s.best0to200, firstAttempt)
                    tvBest100to200.text = formatTimeWithLabelNormalized(null, s.best100to200, firstAttempt)
                    tvBest0to402.text = formatTimeWithLabelNormalized(null, s.best0to402, firstAttempt)

                    findViewById<LinearLayout>(R.id.ll0to100).visibility = View.VISIBLE
                    findViewById<LinearLayout>(R.id.ll0to200).visibility = View.VISIBLE
                    findViewById<LinearLayout>(R.id.ll100to200).visibility = View.VISIBLE
                    findViewById<LinearLayout>(R.id.ll0to402).visibility = View.VISIBLE
                }
            }
        }
    }




    private fun setupRecyclerView() {
        session?.let { s ->
            if (s.attempts.isEmpty()) {
                tvNoAttempts.visibility = View.VISIBLE
                rvAttempts.visibility = View.GONE
            } else {
                tvNoAttempts.visibility = View.GONE
                rvAttempts.visibility = View.VISIBLE

                val mode = try {
                    val modeString = s.measurementMode ?: "ALL"
                    val result = MeasurementMode.valueOf(modeString)
                    result
                } catch (e: Exception) {
                    MeasurementMode.ALL
                }

                attemptsAdapter = DragAttemptsAdapter(this@DragSessionDetailsActivity, s.attempts, mode)
                rvAttempts.apply {
                    layoutManager = LinearLayoutManager(this@DragSessionDetailsActivity)
                    adapter = attemptsAdapter
                }
            }
        }
    }

    private fun formatTimeWithLabel(label: String?, time: Long?): String {
        if (time == null || time <= 0) return "-"
        val seconds = time / 1_000_000_000.0
        return if (!label.isNullOrEmpty()) {
            "$label ${String.format("%.3f s", seconds)}"
        } else {
            String.format("%.3f s", seconds)
        }
    }
    
    private fun formatTimeWithLabelNormalized(label: String?, time: Long?, firstAttempt: DragAttempt?): String {
        if (time == null || time <= 0) return "-"

        // НЕ нормализираме времената - показваме ги като са записани
        // Нормализацията се използва само за графиката, не за дисплея
        val displayTime = time / 1_000_000_000.0
        

        return if (!label.isNullOrEmpty()) {
            "$label\n${String.format("%.3f s", displayTime)}"  // Добавяме \n за съответствие с formatTime
        } else {
            String.format("%.3f s", displayTime)
        }
    }

}

class DragAttemptsAdapter(
    private val context: Context,
    private val attempts: List<DragAttempt>,
    private val measurementMode: MeasurementMode
) : RecyclerView.Adapter<DragAttemptsAdapter.AttemptViewHolder>() {
    
    private var currentMode: ChartMode = ChartMode.SPEED
    private var currentAttempt: DragAttempt? = null
    private var currentMarkerView: com.github.mikephil.charting.components.MarkerView? = null

    inner class AttemptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAttemptNumber: TextView = itemView.findViewById(R.id.tvAttemptNumber)
        val tvTime0to100: TextView = itemView.findViewById(R.id.tvAttempt0to100)
        val tvTime0to200: TextView = itemView.findViewById(R.id.tvAttempt0to200)
        val tvTime100to200: TextView = itemView.findViewById(R.id.tvAttempt100to200)
        val tvTime0to402: TextView = itemView.findViewById(R.id.tvAttempt0to402)
        val tvMaxSpeed: TextView = itemView.findViewById(R.id.tvAttemptMaxSpeed)
        val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        
        // Нова графика
        val chart: com.github.mikephil.charting.charts.LineChart = itemView.findViewById(R.id.chart)
        val tvChartTitle: TextView = itemView.findViewById(R.id.tvChartTitle)
        val tvChartStats: TextView = itemView.findViewById(R.id.tvChartStats)
        val btnSpeed: android.widget.Button = itemView.findViewById(R.id.btnSpeed)
        val btnAcceleration: android.widget.Button = itemView.findViewById(R.id.btnAcceleration)
        val btnGForce: android.widget.Button = itemView.findViewById(R.id.btnGForce)
        
        // Текущ режим на графиката
        var currentChartMode: ChartMode = ChartMode.SPEED
    }
    
    // Намира точния момент (в секунди) когато скоростта пресича targetKmH
    // Използваме линейна интерполация между два съседни семпъла (timestamps са в наносекунди)
    private fun getSpeedCrossingTimeSeconds(attempt: DragAttempt, targetKmH: Float): Float? {
        val (speeds, times) = getAlignedSpeedData(attempt)
        Log.d("DragSessionDetails", "📊 getSpeedCrossingTimeSeconds: looking for $targetKmH km/h in ${speeds.size} samples")
        
        if (speeds.isEmpty() || times.isEmpty()) {
            Log.d("DragSessionDetails", "⚠️ No speed data available")
            return null
        }

        val minSpeed = speeds.minOrNull() ?: 0f
        val maxSpeed = speeds.maxOrNull() ?: 0f
        Log.d("DragSessionDetails", "📊 Speed range: $minSpeed - $maxSpeed km/h")

        for (i in 1 until speeds.size) {
            val s0 = speeds[i - 1]
            val s1 = speeds[i]
            // Търсим първото преминаване нагоре през targetKmH
            if (s0 < targetKmH && s1 >= targetKmH) {
                val t0 = times[i - 1].toDouble()
                val t1 = times[i].toDouble()
                val ratio = ((targetKmH - s0) / (s1 - s0).coerceAtLeast(0.0001f)).toDouble()
                val tCross = t0 + (t1 - t0) * ratio
                val resultSeconds = (tCross / 1_000_000_000.0).toFloat()
                Log.d("DragSessionDetails", "📊 Found crossing: $s0 -> $s1 km/h at ${resultSeconds}s")
                return resultSeconds
            }
        }
        
        Log.d("DragSessionDetails", "⚠️ No crossing found for $targetKmH km/h")
        return null
    }

    enum class ChartMode {
        SPEED, ACCELERATION, G_FORCE
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): AttemptViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_drag_attempt, parent, false)
        return AttemptViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttemptViewHolder, position: Int) {
        val attempt = attempts[position]
        val context = holder.itemView.context

        holder.tvAttemptNumber.text = context.getString(R.string.attempt_number, position + 1)

        if (attempt.maxSpeed > 0) {
            val convertedSpeed = UnitsManager.formatSpeed(attempt.maxSpeed, context, 0)
            holder.tvMaxSpeed.text = context.getString(R.string.drag_max_speed_label) + " " + convertedSpeed
            holder.tvMaxSpeed.visibility = View.VISIBLE
        } else {
            holder.tvMaxSpeed.visibility = View.GONE
        }

        // Показване на продължителността - винаги използваме максималното време от успешните измервания
        val displayDuration = getMaxMeasuredTime(attempt)

        if (displayDuration > 0) {
            holder.tvDuration.text = context.getString(R.string.drag_attempt_duration, displayDuration)
            holder.tvDuration.visibility = View.VISIBLE
        } else {
            holder.tvDuration.visibility = View.GONE
        }

        updateVisibility(holder)


        // Използваме същата нормализация като Best Times за съответствие
        val firstAttempt = attempts.firstOrNull()
        val speedUnit = UnitsManager.getSpeedUnit(context)
        val speed100 = UnitsManager.convertSpeed(100f, speedUnit).toInt()
        val speed200 = UnitsManager.convertSpeed(200f, speedUnit).toInt()
        val distLabel = UnitsManager.getQuarterMileDistance(context)
        
        holder.tvTime0to100.text = formatTimeWithLabelNormalized("0-$speed100", attempt.time0to100, firstAttempt)
        holder.tvTime0to200.text = formatTimeWithLabelNormalized("0-$speed200", attempt.time0to200, firstAttempt)
        holder.tvTime100to200.text = formatTimeWithLabelNormalized("$speed100-$speed200", attempt.time100to200, firstAttempt)
        holder.tvTime0to402.text = formatTimeWithLabelNormalized(distLabel, attempt.time0to402, firstAttempt)

        // Настройваме новата графика
        setupChart(holder, attempt)
    }
    
    private fun setupChart(holder: AttemptViewHolder, attempt: DragAttempt) {
        // Настройваме графиката
        setupChartConfiguration(holder.chart, attempt)
        
        // Настройваме бутоните
        setupChartButtons(holder, attempt)
        
        // Показваме данните за скорост по подразбиране
        updateChartData(holder, attempt, ChartMode.SPEED)
    }
    
    private fun setupChartConfiguration(chart: com.github.mikephil.charting.charts.LineChart, attempt: DragAttempt) {
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.setDoubleTapToZoomEnabled(true)
        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false
        chart.legend.isEnabled = false  // Махаме легендата
        chart.isDragDecelerationEnabled = false
        chart.dragDecelerationFrictionCoef = 0f
        
        chart.xAxis.apply {
            position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            granularity = 0.1f
            textColor = android.graphics.Color.WHITE
            textSize = 12f
            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(x: Float): String {
                    // Показваме секунди с 1 десетичен знак за по-голяма точност
                    val result = String.format("%.1fs", x)
                    return result
                }
            }
        }
        
        // Настройваме Y оста
        chart.axisLeft.apply {
            textColor = android.graphics.Color.WHITE
            textSize = 12f
        }
        
        // Добавяме зум функционалност като в MapActivity
        setupChartZoom(chart)
    }
    
    private fun setupChartZoom(chart: com.github.mikephil.charting.charts.LineChart) {
        // Променливи за контрол на zoom/pan
        var isZooming = false
        var zoomCenterX = 0f
        var scaleGestureDetector: ScaleGestureDetector? = null
        
        // ScaleGestureDetector за zoom
        scaleGestureDetector = ScaleGestureDetector(chart.context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isZooming = true
                // Запазваме центъра преди zoom
                zoomCenterX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val deltaX = kotlin.math.abs(detector.currentSpanX - detector.previousSpanX)
                val deltaY = kotlin.math.abs(detector.currentSpanY - detector.previousSpanY)

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

                // Връщаме се на същата позиция след zoom
                val targetX = zoomCenterX - chart.visibleXRange / 2f
                chart.moveViewToX(targetX)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isZooming = false
            }
        })

        // Touch listener с правилна обработка на touch събитията
        chart.setOnTouchListener { _, event ->
            scaleGestureDetector?.onTouchEvent(event)

            // Позволяваме движение само ако НЕ зумваме
            if (!isZooming) {
                chart.onTouchEvent(event)
            }

            // Спираме разпространението на touch събитията към родителския RecyclerView
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Спираме скрола на RecyclerView когато докосваме графиката
                    chart.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Разрешаваме скрола на RecyclerView когато приключваме с графиката
                    chart.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }

            true
        }

        // Value selected listener за tooltip-и
        chart.setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
            override fun onValueSelected(e: com.github.mikephil.charting.data.Entry?, h: com.github.mikephil.charting.highlight.Highlight?) {
                if (e != null && h != null) {
                    val specialPointType = currentAttempt?.let { determinePointType(e.x, it) }
                    currentMarkerView?.let { markerView ->
                        markerView.refreshContent(e, h)
                        // Задаваме свойствата чрез reflection
                        try {
                            val pointTypeField = markerView.javaClass.getDeclaredField("pointType")
                            pointTypeField.isAccessible = true
                            pointTypeField.set(markerView, specialPointType)
                            
                            val isOnSpecialPointField = markerView.javaClass.getDeclaredField("isOnSpecialPoint")
                            isOnSpecialPointField.isAccessible = true
                            isOnSpecialPointField.set(markerView, specialPointType != null)
                            
                            val actualValueField = markerView.javaClass.getDeclaredField("actualValue")
                            actualValueField.isAccessible = true
                            actualValueField.set(markerView, e.y)
                            
                            val modeField = markerView.javaClass.getDeclaredField("mode")
                            modeField.isAccessible = true
                            modeField.set(markerView, currentMode)
                            
                            val attemptField = markerView.javaClass.getDeclaredField("attempt")
                            attemptField.isAccessible = true
                            attemptField.set(markerView, currentAttempt)
                        } catch (e: Exception) {
                            // Ако reflection не работи, просто обновяваме marker-а
                        }
                    }
                    chart.highlightValue(h)
                }
            }

            override fun onNothingSelected() {
                chart.highlightValue(null)
            }
        })
        
        // Gesture listener за double tap to fit screen
        chart.setOnChartGestureListener(object : OnChartGestureListener {
            override fun onChartGestureStart(me: MotionEvent?, lastGesture: ChartTouchListener.ChartGesture?) {
                // Спираме скрола на RecyclerView когато започваме gesture
                chart.parent?.requestDisallowInterceptTouchEvent(true)
            }
            override fun onChartGestureEnd(me: MotionEvent?, lastGesture: ChartTouchListener.ChartGesture?) {
                // Разрешаваме скрола на RecyclerView когато завършваме gesture
                chart.parent?.requestDisallowInterceptTouchEvent(false)
            }
            override fun onChartLongPressed(me: MotionEvent?) {}
            override fun onChartDoubleTapped(me: MotionEvent?) {
                chart.fitScreen()
            }
            override fun onChartSingleTapped(me: MotionEvent?) {
                // Оставяме празно - използваме само OnChartValueSelectedListener
            }
            override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}
            override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {}
            override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {}
        })
    }
    
    private fun setupChartButtons(holder: AttemptViewHolder, attempt: DragAttempt) {
        holder.btnSpeed.setOnClickListener {
            updateChartMode(holder, attempt, ChartMode.SPEED)
        }
        
        holder.btnAcceleration.setOnClickListener {
            updateChartMode(holder, attempt, ChartMode.ACCELERATION)
        }
        
        holder.btnGForce.setOnClickListener {
            updateChartMode(holder, attempt, ChartMode.G_FORCE)
        }
    }

    // -------- Aligned data helpers to keep samples and timestamps in sync --------
    private fun getAlignedSpeedData(attempt: DragAttempt): Pair<List<Float>, List<Long>> {
        val speeds = attempt.speedSamples
        val times = attempt.speedTimeStamps
        val limit = minOf(speeds.size, times.size)
        
        Log.d("DragSessionDetails", "📊 getAlignedSpeedData: speeds=${speeds.size}, times=${times.size}, limit=$limit")
        
        val result = speeds.take(limit) to times.take(limit)
        
        if (measurementMode == MeasurementMode.HUNDRED_TO_200) {
            val maxSpeed = speeds.maxOrNull() ?: 0f
            val minSpeed = speeds.minOrNull() ?: 0f
            Log.d("DragSessionDetails", "📊 Speed data: ${speeds.size} samples, range $minSpeed - $maxSpeed km/h")
            
            // Debug: показваме първите няколко sample-а
            for (i in 0 until minOf(5, speeds.size)) {
                Log.d("DragSessionDetails", "📊 Sample $i: speed=${speeds[i]} km/h, time=${times[i]/1_000_000_000.0}s")
            }
        }
        
        return result
    }

    private fun getAlignedAccelData(attempt: DragAttempt): Pair<List<Float>, List<Long>> {
        val vals = attempt.gpsAccelSamples
        val times = attempt.gpsTimeStamps
        val limit = minOf(vals.size, times.size)
        // RAW данни - без филтри, показваме всичко както е записано
        return vals.take(limit) to times.take(limit)
    }

    // -------- Start offset helpers (begin charts at 60 km/h) --------
    private fun getSpeedStartOffsetMs(attempt: DragAttempt, thresholdKmH: Float = 60f): Long {
        val (speeds, times) = getAlignedSpeedData(attempt)
        for (i in speeds.indices) {
            if (speeds[i] >= thresholdKmH) return times[i]
        }
        return 0L
    }

    private fun getStartOffsetMsForMode(attempt: DragAttempt, mode: ChartMode): Long {
        // Align all modes to the speed start (first >= 4 km/h)
        return getSpeedStartOffsetMs(attempt)
    }

    private fun getMaxTimeWithStartOffset(attempt: DragAttempt): Float {
        // Сега всички timestamps са в наносекунди
        val maxTsNanos = listOf(
            attempt.speedTimeStamps.maxOrNull() ?: 0L,
            attempt.gpsTimeStamps.maxOrNull() ?: 0L,
            attempt.timeStamps.maxOrNull() ?: 0L
        ).filter { it > 0 }.maxOrNull() ?: 0L
        return (maxTsNanos / 1_000_000_000.0f) // Конвертираме от наносекунди в секунди
    }

    private fun getAlignedGData(attempt: DragAttempt): Pair<List<Float>, List<Long>> {
        val vals = attempt.gSamples
        val times = attempt.timeStamps
        val limit = minOf(vals.size, times.size)
        // RAW данни - без филтри, показваме всичко както е записано
        return vals.take(limit) to times.take(limit)
    }
    
    private fun updateChartMode(holder: AttemptViewHolder, attempt: DragAttempt, mode: ChartMode) {
        holder.currentChartMode = mode
        currentMode = mode
        currentAttempt = attempt
        
        // Обновяваме стила на бутоните
        updateButtonStyles(holder, mode)
        
        // Обновяваме данните на графиката
        updateChartData(holder, attempt, mode)
        
        // Обновяваме настройките на графиката при всяко превключване
        setupChartConfiguration(holder.chart, attempt)
    }
    
    private fun updateButtonStyles(holder: AttemptViewHolder, mode: ChartMode) {
        val context = holder.itemView.context
        
        when (mode) {
            ChartMode.SPEED -> {
                holder.btnSpeed.background = ContextCompat.getDrawable(context, R.drawable.button_toggle_selected)
                holder.btnSpeed.setTextColor(ContextCompat.getColor(context, R.color.white))
                holder.btnAcceleration.background = ContextCompat.getDrawable(context, R.drawable.button_toggle_unselected)
                holder.btnAcceleration.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                holder.btnGForce.background = ContextCompat.getDrawable(context, R.drawable.button_toggle_unselected)
                holder.btnGForce.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
            ChartMode.ACCELERATION -> {
                holder.btnSpeed.background = ContextCompat.getDrawable(context, R.drawable.button_toggle_unselected)
                holder.btnSpeed.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                holder.btnAcceleration.background = ContextCompat.getDrawable(context, R.drawable.button_toggle_selected)
                holder.btnAcceleration.setTextColor(ContextCompat.getColor(context, R.color.white))
                holder.btnGForce.background = ContextCompat.getDrawable(context, R.drawable.button_toggle_unselected)
                holder.btnGForce.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
            ChartMode.G_FORCE -> {
                holder.btnSpeed.background = ContextCompat.getDrawable(context, R.drawable.button_toggle_unselected)
                holder.btnSpeed.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                holder.btnAcceleration.background = ContextCompat.getDrawable(context, R.drawable.button_toggle_unselected)
                holder.btnAcceleration.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                holder.btnGForce.background = ContextCompat.getDrawable(context, R.drawable.button_toggle_selected)
                holder.btnGForce.setTextColor(ContextCompat.getColor(context, R.color.white))
            }
        }
    }
    
    private fun updateChartData(holder: AttemptViewHolder, attempt: DragAttempt, mode: ChartMode) {
        // Изчистваме всички данни
        holder.chart.clear()
        
        // Добавяме всички три линии
        addSpeedLine(holder, attempt, mode == ChartMode.SPEED)
        addAccelerationLine(holder, attempt, mode == ChartMode.ACCELERATION)
        addGForceLine(holder, attempt, mode == ChartMode.G_FORCE)
        
        // Настройваме заглавието и Y-оста според активния режим
        when (mode) {
            ChartMode.SPEED -> updateSpeedChart(holder, attempt)
            ChartMode.ACCELERATION -> updateAccelerationChart(holder, attempt)
            ChartMode.G_FORCE -> updateGForceChart(holder, attempt)
        }
        
        // Добавяме маркери за ключовите точки
        addKeyPointMarkers(holder, attempt, mode)
        
        // Принудително обновяваме X-оста след всяко обновяване на данните
        val maxTimeFromAllMeasurements = getMaxTimeFromAllMeasurements(attempt).toFloat()
        holder.chart.xAxis.axisMinimum = 0f
        holder.chart.xAxis.axisMaximum = maxTimeFromAllMeasurements
        holder.chart.setVisibleXRangeMaximum(maxTimeFromAllMeasurements)
        holder.chart.moveViewToX(0f)
        holder.chart.invalidate()
    }
    
    private fun addSpeedLine(holder: AttemptViewHolder, attempt: DragAttempt, isActive: Boolean) {
        Log.d("DragSessionDetails", "📊 addSpeedLine START: attempt.speedSamples=${attempt.speedSamples.size}, attempt.speedTimeStamps=${attempt.speedTimeStamps.size}")
        
        val (speedSamples, timestamps) = getAlignedSpeedData(attempt)
        Log.d("DragSessionDetails", "📊 addSpeedLine: ${speedSamples.size} samples, ${timestamps.size} timestamps")
        
        // Debug: показваме всички speed samples
        for (i in speedSamples.indices) {
            Log.d("DragSessionDetails", "📊 Speed sample $i: ${speedSamples[i]} km/h at ${timestamps[i]/1_000_000_000.0}s")
        }
        
        if (speedSamples.isEmpty() || timestamps.isEmpty()) {
            Log.d("DragSessionDetails", "❌ No speed data available for line!")
            return
        }
        
        if (speedSamples.isNotEmpty() && timestamps.isNotEmpty()) {
            val speedUnit = UnitsManager.getSpeedUnit(context)
            val entries = mutableListOf<com.github.mikephil.charting.data.Entry>()
            
            // Debug: показваме диапазона на данните
            val minSpeed = speedSamples.minOrNull() ?: 0f
            val maxSpeed = speedSamples.maxOrNull() ?: 0f
            Log.d("DragSessionDetails", "📊 Speed range: $minSpeed - $maxSpeed km/h")
            
            if (measurementMode == MeasurementMode.HUNDRED_TO_200) {
                // За 100-200 режим: мащабираме линията да съвпада с точката
                val resultTimeSeconds = attempt.time100to200 / 1_000_000_000.0
                
                // Намираме кога реално се достига 200 km/h в данните (относително време)
                val firstTimeSeconds = if (timestamps.isNotEmpty()) {
                    timestamps.first() / 1_000_000_000.0
                } else {
                    0.0
                }
                
                val actual200kmhCrossingTime = findSpeedCrossingPoint(speedSamples, timestamps, 200f)
                val actual200kmhRelativeTime = if (actual200kmhCrossingTime != null) {
                    actual200kmhCrossingTime - firstTimeSeconds
                } else {
                    0.0
                }
                
                val scalingFactor = if (actual200kmhRelativeTime > 0.001) {
                    (resultTimeSeconds / actual200kmhRelativeTime).toFloat()
                } else {
                    1.0f // No scaling if 200km/h not crossed
                }
                
                // Мащабираме времето така че линията да достигне 200 km/h на resultTimeSeconds
                for (i in speedSamples.indices) {
                    val currentSpeed = speedSamples[i]
                    
                    val rawTimeInSeconds = timestamps[i] / 1_000_000_000.0
                    val normalizedTimeInSeconds = rawTimeInSeconds - firstTimeSeconds
                    val scaledTimeInSeconds = normalizedTimeInSeconds * scalingFactor
                    val convertedSpeed = UnitsManager.convertSpeed(currentSpeed, speedUnit)
                    entries.add(com.github.mikephil.charting.data.Entry(scaledTimeInSeconds.toFloat(), convertedSpeed))
                }
            } else {
                // За всички други режими: използваме сурови данни без нормализация (като точките)
                for (i in speedSamples.indices) {
                    val currentSpeed = speedSamples[i]
                    
                    val rawTimeInSeconds = timestamps[i] / 1_000_000_000.0
                    val convertedSpeed = UnitsManager.convertSpeed(currentSpeed, speedUnit)
                    entries.add(com.github.mikephil.charting.data.Entry(rawTimeInSeconds.toFloat(), convertedSpeed))
                }
            }
            
            Log.d("DragSessionDetails", "📊 Speed line: ${entries.size} entries added")
            if (entries.isNotEmpty()) {
                val firstEntry = entries.first()
                val lastEntry = entries.last()
                Log.d("DragSessionDetails", "📊 First entry: time=${firstEntry.x}s, speed=${firstEntry.y}")
                Log.d("DragSessionDetails", "📊 Last entry: time=${lastEntry.x}s, speed=${lastEntry.y}")
            }
            
            val dataSet = com.github.mikephil.charting.data.LineDataSet(entries, "${context.getString(R.string.drag_tab_speed)} (${speedUnit.symbol})").apply {
                val baseColor = ContextCompat.getColor(holder.itemView.context, R.color.accent_blue)
                color = if (isActive) baseColor else android.graphics.Color.argb(77, android.graphics.Color.red(baseColor), android.graphics.Color.green(baseColor), android.graphics.Color.blue(baseColor))
                lineWidth = if (isActive) 2f else 1f
                setDrawValues(false)
                setDrawCircles(false)
            }
            
            if (holder.chart.data == null) {
                val lineData = com.github.mikephil.charting.data.LineData(dataSet)
                holder.chart.data = lineData
            } else {
                holder.chart.data?.addDataSet(dataSet)
            }
            
            if (isActive) {
                addTooltipMarkers(holder, attempt, ChartMode.SPEED, null)
            }
        }
    }
    
    private fun addAccelerationLine(holder: AttemptViewHolder, attempt: DragAttempt, isActive: Boolean) {
        val (accelSamples, timestamps) = getAlignedAccelData(attempt)
        if (accelSamples.isNotEmpty() && timestamps.isNotEmpty()) {
            val entries = mutableListOf<com.github.mikephil.charting.data.Entry>()
            
            // За 100-200 режим: нормализираме времето спрямо startTime от attempt-а
            val crossing100TimeSeconds = if (measurementMode == MeasurementMode.HUNDRED_TO_200) {
                if (attempt.startTime > 0) {
                    attempt.startTime / 1_000_000_000.0
                } else if (timestamps.isNotEmpty()) {
                    timestamps.first() / 1_000_000_000.0
                } else {
                    0.0
                }
            } else {
                0.0
            }
            
            for (i in accelSamples.indices) {
                val timeInSeconds = timestamps[i] / 1_000_000_000.0
                
                val normalizedTime = timeInSeconds - crossing100TimeSeconds
                entries.add(com.github.mikephil.charting.data.Entry(normalizedTime.toFloat(), accelSamples[i]))
            }
            
            val dataSet = com.github.mikephil.charting.data.LineDataSet(entries, context.getString(R.string.drag_tab_acceleration)).apply {
                val baseColor = ContextCompat.getColor(holder.itemView.context, R.color.accent_green)
                color = if (isActive) baseColor else android.graphics.Color.argb(77, android.graphics.Color.red(baseColor), android.graphics.Color.green(baseColor), android.graphics.Color.blue(baseColor))
                lineWidth = if (isActive) 2f else 1f
                setDrawValues(false)
                setDrawCircles(false)
            }
            
            if (holder.chart.data == null) {
                val lineData = com.github.mikephil.charting.data.LineData(dataSet)
                holder.chart.data = lineData
            } else {
                holder.chart.data?.addDataSet(dataSet)
            }

            if (isActive) {
                addTooltipMarkers(holder, attempt, ChartMode.ACCELERATION, null)
            }
        }
    }
    
    private fun addGForceLine(holder: AttemptViewHolder, attempt: DragAttempt, isActive: Boolean) {
        val (gSamples, timestamps) = getAlignedGData(attempt)
        if (gSamples.isNotEmpty() && timestamps.isNotEmpty()) {
            val entries = mutableListOf<com.github.mikephil.charting.data.Entry>()
            
            // За 100-200 режим: нормализираме времето спрямо startTime от attempt-а
            val crossing100TimeSeconds = if (measurementMode == MeasurementMode.HUNDRED_TO_200) {
                if (attempt.startTime > 0) {
                    attempt.startTime / 1_000_000_000.0
                } else if (timestamps.isNotEmpty()) {
                    timestamps.first() / 1_000_000_000.0
                } else {
                    0.0
                }
            } else {
                0.0
            }
            
            for (i in gSamples.indices) {
                val timeInSeconds = timestamps[i] / 1_000_000_000.0
                
                val normalizedTime = timeInSeconds - crossing100TimeSeconds
                entries.add(com.github.mikephil.charting.data.Entry(normalizedTime.toFloat(), gSamples[i]))
            }
            
            val dataSet = com.github.mikephil.charting.data.LineDataSet(entries, context.getString(R.string.drag_tab_gforce)).apply {
                val baseColor = ContextCompat.getColor(holder.itemView.context, R.color.accent_red)
                color = if (isActive) baseColor else android.graphics.Color.argb(77, android.graphics.Color.red(baseColor), android.graphics.Color.green(baseColor), android.graphics.Color.blue(baseColor))
                lineWidth = if (isActive) 2f else 1f
                setDrawValues(false)
                setDrawCircles(false)
            }
            
            if (holder.chart.data == null) {
                val lineData = com.github.mikephil.charting.data.LineData(dataSet)
                holder.chart.data = lineData
            } else {
                holder.chart.data?.addDataSet(dataSet)
            }

            if (isActive) {
                addTooltipMarkers(holder, attempt, ChartMode.G_FORCE, null)
            }
        }
    }
    
    private fun updateSpeedChart(holder: AttemptViewHolder, attempt: DragAttempt) {
        val speedUnitSymbol = UnitsManager.getSpeedUnit(context).symbol
        holder.tvChartTitle.text = "${context.getString(R.string.track_tab_speed)} ($speedUnitSymbol)"
        
        val (speedSamples, timestamps) = getAlignedSpeedData(attempt)
        
        if (speedSamples.isNotEmpty() && timestamps.isNotEmpty()) {
            val minSize = speedSamples.size
            val entries = mutableListOf<com.github.mikephil.charting.data.Entry>()
            
            // Показваме реалните времена без нормализация
            val speedUnit = UnitsManager.getSpeedUnit(context)
            for (i in 0 until minSize) {
                val timeInSeconds = timestamps[i] / 1_000_000_000.0
                val convertedSpeed = UnitsManager.convertSpeed(speedSamples[i], speedUnit)
                entries.add(com.github.mikephil.charting.data.Entry(timeInSeconds.toFloat(), convertedSpeed))
            }
            
            val maxSpeed = speedSamples.maxOrNull() ?: 0f
            val convertedMaxSpeed = UnitsManager.formatSpeed(maxSpeed, context, 0)
            holder.tvChartStats.text = context.getString(R.string.drag_max_speed_label) + " " + convertedMaxSpeed
            
            // Debug проверка за съответствие на времената
            val crossing100 = findSpeedCrossingPoint(speedSamples, timestamps, 100f)
            
            // Данните вече са добавени от addSpeedLine
            
            // Настройваме Y оста - конвертирана в избраната единица
            val yAxis = holder.chart.axisLeft
            val convertedMax = UnitsManager.convertSpeed(maxSpeed, speedUnit)
            val threshold200 = UnitsManager.convertSpeed(200f, speedUnit)
            
            // Настройваме Y оста - за HUNDRED_TO_200 режим започваме от 100 km/h
            if (measurementMode == MeasurementMode.HUNDRED_TO_200) {
                val threshold100 = UnitsManager.convertSpeed(100f, speedUnit)
                yAxis.axisMinimum = threshold100
                yAxis.axisMaximum = if (convertedMax > threshold200) convertedMax * 1.1f else threshold200
            } else {
                yAxis.axisMinimum = 0f
                yAxis.axisMaximum = if (convertedMax > threshold200) convertedMax * 1.1f else threshold200
            }
            yAxis.setDrawZeroLine(true)
            yAxis.zeroLineColor = android.graphics.Color.GRAY
            yAxis.zeroLineWidth = 1f
            
            // Форматиране за Y-оста - само цели числа за скоростта
            yAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
            
            // Настройваме X оста - за HUNDRED_TO_200 режим използваме нормализирано време
            if (entries.isNotEmpty()) {
                if (measurementMode == MeasurementMode.HUNDRED_TO_200) {
                    // За 100-200 режим времето е нормализирано (100 km/h = 0.0s)
                    val maxNormalizedTime = entries.maxOfOrNull { it.x } ?: 0f
                    holder.chart.xAxis.axisMinimum = 0f
                    holder.chart.xAxis.axisMaximum = maxNormalizedTime * 1.1f
                    holder.chart.setVisibleXRangeMaximum(maxNormalizedTime * 1.1f)
                    holder.chart.moveViewToX(0f)
                } else {
                    // За останалите режими използваме реалните времена
                    val maxTimeFromAllMeasurements = getMaxTimeFromAllMeasurements(attempt).toFloat()
                    holder.chart.xAxis.axisMinimum = 0f
                    holder.chart.xAxis.axisMaximum = maxTimeFromAllMeasurements
                    holder.chart.setVisibleXRangeMaximum(maxTimeFromAllMeasurements)
                    holder.chart.moveViewToX(0f)
                }
            }
            
            holder.chart.invalidate()
        } else {
            holder.tvChartStats.text = context.getString(R.string.drag_chart_no_speed_data)
            holder.chart.data = null
            holder.chart.invalidate()
        }
    }
    
    private fun updateAccelerationChart(holder: AttemptViewHolder, attempt: DragAttempt) {
        holder.tvChartTitle.text = context.getString(R.string.drag_accel_chart_title)
        
        val (accelSamples, timestamps) = getAlignedAccelData(attempt)
        
        if (accelSamples.isNotEmpty() && timestamps.isNotEmpty()) {
            val minSize = accelSamples.size
            val entries = mutableListOf<com.github.mikephil.charting.data.Entry>()
            
            // Графиката започва от 0 секунди - показваме всички данни
            for (i in 0 until minSize) {
                val timeInSeconds = timestamps[i] / 1_000_000_000.0
                entries.add(com.github.mikephil.charting.data.Entry(timeInSeconds.toFloat(), accelSamples[i]))
            }
            
            val maxAccel = accelSamples.maxOrNull() ?: 0f
            holder.tvChartStats.text = context.getString(R.string.drag_chart_max_accel, maxAccel)
            
            // Данните вече са добавени от addAccelerationLine
            
            // Настройваме Y оста - показваме и отрицателни стойности за acceleration
            val yAxis = holder.chart.axisLeft
            val minAccel = accelSamples.minOrNull() ?: 0f
            val maxAccelValue = accelSamples.maxOrNull() ?: 0f
            val range = maxAccelValue - minAccel
            val padding = if (range > 0) range * 0.1f else 2f
            
            yAxis.axisMinimum = minAccel - padding
            yAxis.axisMaximum = maxAccelValue + padding
            yAxis.setDrawZeroLine(true)
            yAxis.zeroLineColor = android.graphics.Color.GRAY
            yAxis.zeroLineWidth = 1f
            
            // Форматиране за Y-оста - само цели числа за ускорението
            yAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
            
            // Настройваме X оста - използваме реалните времена
            if (entries.isNotEmpty()) {
                val maxTimeFromAllMeasurements = getMaxTimeFromAllMeasurements(attempt).toFloat()
                
                holder.chart.xAxis.axisMinimum = 0f
                holder.chart.xAxis.axisMaximum = maxTimeFromAllMeasurements
                holder.chart.setVisibleXRangeMaximum(maxTimeFromAllMeasurements)
                holder.chart.moveViewToX(0f)
            }
            
            holder.chart.invalidate()
        } else {
            holder.tvChartStats.text = context.getString(R.string.drag_chart_no_accel_data)
            holder.chart.data = null
            holder.chart.invalidate()
        }
    }
    
    private fun updateGForceChart(holder: AttemptViewHolder, attempt: DragAttempt) {
        holder.tvChartTitle.text = context.getString(R.string.drag_g_force_chart_title)
        
        val (gSamples, timestamps) = getAlignedGData(attempt)
        
        if (gSamples.isNotEmpty() && timestamps.isNotEmpty()) {
            val minSize = gSamples.size
            val entries = mutableListOf<com.github.mikephil.charting.data.Entry>()
            
            // Графиката започва от 0 секунди - показваме всички данни
            for (i in 0 until minSize) {
                val timeInSeconds = timestamps[i] / 1_000_000_000.0
                entries.add(com.github.mikephil.charting.data.Entry(timeInSeconds.toFloat(), gSamples[i]))
            }
            
            val maxG = gSamples.maxOrNull() ?: 0f
            val minG = gSamples.minOrNull() ?: 0f
            
            
            holder.tvChartStats.text = context.getString(R.string.drag_chart_peak_g, maxG)
            
            // Данните вече са добавени от addGForceLine
            
            // Настройваме Y оста - поправяме скалирането
            val yAxis = holder.chart.axisLeft
            yAxis.axisMinimum = 0f
            // Ако maxG е много малко, използваме разумен диапазон
            val yMax = if (maxG > 0.1f) maxG * 1.1f else 2f
            yAxis.axisMaximum = yMax
            yAxis.setDrawZeroLine(true)
            yAxis.zeroLineColor = android.graphics.Color.GRAY
            yAxis.zeroLineWidth = 1f
            
            // Добавяме форматиране за Y-оста за G-силите
            yAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format("%.2f", value)
                }
            }
            
            // Настройваме X оста - използваме реалните времена
            if (entries.isNotEmpty()) {
                val maxTimeFromAllMeasurements = getMaxTimeFromAllMeasurements(attempt).toFloat()
                
                holder.chart.xAxis.axisMinimum = 0f
                holder.chart.xAxis.axisMaximum = maxTimeFromAllMeasurements
                holder.chart.setVisibleXRangeMaximum(maxTimeFromAllMeasurements)
                holder.chart.moveViewToX(0f)
            }
            
            holder.chart.invalidate()
        } else {
            holder.tvChartStats.text = context.getString(R.string.drag_chart_no_g_data)
            holder.chart.data = null
            holder.chart.invalidate()
        }
    }

    private fun getStartTimeForMode(attempt: DragAttempt, mode: ChartMode): Long {
        // Use measurement start time as reference point for consistency with measurement times
        val result = 0L // Always start from 0 to match measurement timing
        
        return result
    }
    
    private fun getMaxTimeFromAllMeasurements(attempt: DragAttempt): Double {
        // Намираме максималното време САМО от успешните измервания
        val allTimes = mutableListOf<Double>()
        
        // Добавяме САМО успешните измерени времена (в секунди)
        if (attempt.time0to100 > 0) allTimes.add(attempt.time0to100 / 1_000_000_000.0)
        if (attempt.time0to200 > 0) allTimes.add(attempt.time0to200 / 1_000_000_000.0)
        if (attempt.time100to200 > 0) allTimes.add(attempt.time100to200 / 1_000_000_000.0)
        if (attempt.time0to402 > 0) allTimes.add(attempt.time0to402 / 1_000_000_000.0)
        
        // НЕ добавяме timestamps - използваме само успешните измервания
        // Ако няма успешни измервания, използваме минимално време
        return allTimes.maxOrNull() ?: 1.0 // По подразбиране 1 секунда ако няма успешни измервания
    }

    private fun addKeyPointMarkers(holder: AttemptViewHolder, attempt: DragAttempt, mode: ChartMode) {
        val context = holder.itemView.context

        val rawSpeeds = attempt.speedSamples
        val rawTimes = attempt.speedTimeStamps
        Log.d("DragSessionDetails", "📊 addKeyPointMarkers: ${rawSpeeds.size} samples, measurementMode=$measurementMode")
        
        if (rawSpeeds.isEmpty() || rawTimes.isEmpty()) {
            Log.d("DragSessionDetails", "⚠️ No data for key point markers")
            return
        }

        val existingData = holder.chart.data
        if (existingData != null) {
            // За 100-200 режим НЕ показваме маркер на 100 km/h (графиката започва от там)
            if (attempt.time0to100 > 0 && measurementMode != MeasurementMode.HUNDRED_TO_200) {
                val crossing100 = findSpeedCrossingPoint(rawSpeeds, rawTimes, 100f)
                if (crossing100 != null) {
                    val speedUnit = UnitsManager.getSpeedUnit(holder.itemView.context)
                    val valueAt100 = when (mode) {
                        ChartMode.SPEED -> UnitsManager.convertSpeed(100f, speedUnit)
                        ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, crossing100, mode)
                        ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, crossing100, mode)
                    }
                    val entry100 = com.github.mikephil.charting.data.Entry(crossing100, valueAt100)
                    val dataSet100 = com.github.mikephil.charting.data.LineDataSet(listOf(entry100), "").apply {
                        setDrawCircles(true)
                        setDrawValues(false)
                        lineWidth = 0f
                        circleRadius = 8f
                        circleHoleRadius = 4f
                        setCircleColor(ContextCompat.getColor(context, R.color.accent_green)) // 100 km/h - зелена
                    }
                    existingData.addDataSet(dataSet100)
                }
            }

            val shouldShow200kmh = when (measurementMode) {
                MeasurementMode.HUNDRED_TO_200 -> {
                    val result = attempt.time100to200 > 0
                    result
                }
                else -> {
                    val result = attempt.time0to200 > 0
                    result
                }
            }
            
            if (shouldShow200kmh) {
                val speedUnit = UnitsManager.getSpeedUnit(holder.itemView.context)
                val valueAt200 = when (mode) {
                    ChartMode.SPEED -> UnitsManager.convertSpeed(200f, speedUnit)
                    ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, 0f, mode) // За 100-200 режим използваме резултата
                    ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, 0f, mode)
                }
                
                val timeForChart = if (measurementMode == MeasurementMode.HUNDRED_TO_200) {
                    // За 100-200: използваме резултата (attempt.time100to200) - това е продължителността
                    attempt.time100to200 / 1_000_000_000.0f
                } else {
                    // За други режими: използваме findSpeedCrossingPoint
                    val crossing200 = findSpeedCrossingPoint(rawSpeeds, rawTimes, 200f)
                    crossing200 ?: 0f
                }
                
                val entry200 = com.github.mikephil.charting.data.Entry(timeForChart, valueAt200)
                val dataSet200 = com.github.mikephil.charting.data.LineDataSet(listOf(entry200), "").apply {
                    setDrawCircles(true)
                    setDrawValues(false)
                    lineWidth = 0f
                    circleRadius = 8f
                    circleHoleRadius = 4f
                    setCircleColor(ContextCompat.getColor(context, R.color.accent_blue)) // 200 km/h - синя
                }
                existingData.addDataSet(dataSet200)
            }

            if (attempt.time0to402 > 0) {
                val time402Seconds = attempt.time0to402 / 1_000_000_000.0f
                val valueAt402 = findValueAtTimeInterpolated(attempt, time402Seconds, mode)
                val entry402 = com.github.mikephil.charting.data.Entry(time402Seconds, valueAt402)
                val dataSet402 = com.github.mikephil.charting.data.LineDataSet(listOf(entry402), "").apply {
                    setDrawCircles(true)
                    setDrawValues(false)
                    lineWidth = 0f
                    circleRadius = 8f
                    circleHoleRadius = 4f
                    setCircleColor(ContextCompat.getColor(context, R.color.accent_red)) // 402m - червена
                }
                existingData.addDataSet(dataSet402)
            }

            holder.chart.notifyDataSetChanged()
            holder.chart.invalidate()
        }
        
        // Tooltip маркерите се добавят в addSpeedLine/addAccelerationLine/addGForceLine
    }
    
    private fun addTooltipMarkers(holder: AttemptViewHolder, attempt: DragAttempt, mode: ChartMode, closestTo200Normalized: Float?) {
        val context = holder.itemView.context
        
        // Изчисляваме позицията на 200 km/h маркера за 100-200 режим
        var calculatedClosestTo200 = closestTo200Normalized
        if (measurementMode == MeasurementMode.HUNDRED_TO_200 && attempt.startTime > 0 && calculatedClosestTo200 == null) {
            val (speedSamples, timestamps) = getAlignedSpeedData(attempt)
            val startTimeSeconds = attempt.startTime / 1_000_000_000.0f
            
            // Намираме най-близката точка до 200 km/h в нормализираните времена
            var closestTo200: Float? = null
            var minDistanceTo200 = Float.MAX_VALUE
            
            for (i in speedSamples.indices) {
                val speed = speedSamples[i]
                val time = timestamps[i] / 1_000_000_000.0f - startTimeSeconds
                val distanceTo200 = kotlin.math.abs(speed - 200f)
                
                if (distanceTo200 < minDistanceTo200 && speed >= 195f) { // Търсим близо до 200 km/h
                    minDistanceTo200 = distanceTo200
                    closestTo200 = time
                }
            }
            
            Log.d("DragSessionDetails", "📊 Calculated closest to 200 km/h at normalized time: $closestTo200")
            calculatedClosestTo200 = closestTo200
        }
        
        // Създаваме custom marker който показва различни tooltip-и
        val smartMarker = object : com.github.mikephil.charting.components.MarkerView(context, R.layout.marker_simple) {
            private var currentEntry: Entry? = null
            private var isOnSpecialPoint = false
            private var pointType: PointTooltipMarker.PointType = PointTooltipMarker.PointType.SPEED_100
            private var actualValue: Float = 0f
            
            override fun refreshContent(e: Entry?, highlight: Highlight?) {
                currentEntry = e
                if (e != null) {
                    // Проверяваме дали е на специална точка (цветна) – само за Speed режим
                    val specialPointType = if (mode == ChartMode.SPEED) {
                        determinePointType(e.x, attempt, calculatedClosestTo200)
                    } else {
                        null
                    }
                    isOnSpecialPoint = specialPointType != null
                    pointType = specialPointType ?: PointTooltipMarker.PointType.SPEED_100
                    actualValue = e.y
                }
                super.refreshContent(e, highlight)
            }
            
            override fun draw(canvas: Canvas, posX: Float, posY: Float) {
                if (currentEntry == null) return
                
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                
                // Определяме текста и цвета
                val (text, backgroundColor) = if (isOnSpecialPoint) {
                    // На специална точка - показваме типа и времето
                    val typeText = when (pointType) {
                        PointTooltipMarker.PointType.SPEED_100 -> {
                            val timeAt100 = currentEntry?.x ?: 0f
                            "0-100 km/h\n${String.format("%.3f", timeAt100)}s"
                        }
                        PointTooltipMarker.PointType.SPEED_200 -> {
                            val timeAt200 = currentEntry?.x ?: 0f
                            "0-200 km/h\n${String.format("%.3f", timeAt200)}s"
                        }
                        PointTooltipMarker.PointType.DISTANCE_402 -> {
                            // За 402m показваме скоростта и времето в момента на достигане
                            val timeAt402 = currentEntry?.x ?: 0f
                            val speedAt402 = getSpeedAtTime(attempt, timeAt402)
                            val speedUnit = UnitsManager.getSpeedUnit(context)
                            val convertedSpeed = UnitsManager.convertSpeed(speedAt402, speedUnit)
                            "0-402m\n${convertedSpeed.toInt()} ${speedUnit.symbol}\n${String.format("%.3f", timeAt402)}s"
                        }
                    }
                    val bgColor = when (pointType) {
                        PointTooltipMarker.PointType.SPEED_100 -> ContextCompat.getColor(context, R.color.accent_green)
                        PointTooltipMarker.PointType.SPEED_200 -> ContextCompat.getColor(context, R.color.accent_blue)
                        PointTooltipMarker.PointType.DISTANCE_402 -> ContextCompat.getColor(context, R.color.accent_red)
                    }
                    Pair(typeText, bgColor)
                } else {
                    // На линията - показваме точната стойност и времето
                    val timeAtPoint = currentEntry?.x ?: 0f
                    val (valueText, backgroundColor) = when (mode) {
                        ChartMode.SPEED -> {
                            val speedUnit = UnitsManager.getSpeedUnit(context)
                            val unitSymbol = speedUnit.symbol
                            val text = "${actualValue.toInt()} $unitSymbol\n${String.format("%.3f", timeAtPoint)}s"
                            text to ContextCompat.getColor(context, R.color.accent_blue)
                        }
                        ChartMode.ACCELERATION -> {
                            val text = "${String.format("%.1f", actualValue)} m/s²\n${String.format("%.3f", timeAtPoint)}s"
                            text to ContextCompat.getColor(context, R.color.accent_green)
                        }
                        ChartMode.G_FORCE -> {
                            val text = "${String.format("%.2f", actualValue)} G\n${String.format("%.3f", timeAtPoint)}s"
                            text to ContextCompat.getColor(context, R.color.accent_orange)
                        }
                    }
                    Pair(valueText, backgroundColor)
                }
                
                // Настройваме paint-овете
                paint.color = backgroundColor
                textPaint.color = android.graphics.Color.WHITE
                textPaint.textSize = 28f
                textPaint.textAlign = android.graphics.Paint.Align.CENTER
                
                // Измерваме текста - поддържаме многоредов текст
                val textBounds = android.graphics.Rect()
                textPaint.getTextBounds(text, 0, text.length, textBounds)
                
                val padding = 16f
                val lineHeight = textBounds.height() + 4f
                val lines = if (text.contains("\n")) text.split("\n") else listOf(text)
                val maxLineWidth = lines.maxOfOrNull { line ->
                    val bounds = android.graphics.Rect()
                    textPaint.getTextBounds(line, 0, line.length, bounds)
                    bounds.width()
                } ?: textBounds.width()
                
                val rectWidth = maxLineWidth + padding * 2
                val rectHeight = (lineHeight * lines.size) + padding * 2
                
                // Позиционираме балончето над точката
                val balloonX = posX - rectWidth / 2
                val balloonY = posY - rectHeight - 20f
                
                // Рисуваме закръглен правоъгълник (балончето)
                val rect = android.graphics.RectF(balloonX, balloonY, balloonX + rectWidth, balloonY + rectHeight)
                canvas.drawRoundRect(rect, 12f, 12f, paint)
                
                // Рисуваме текста - поддържаме многоредов текст
                if (text.contains("\n")) {
                    val lines = text.split("\n")
                    val lineHeight = textBounds.height() + 4f // Малко разстояние между редовете
                    val startY = balloonY + textBounds.height() + padding / 2
                    
                    lines.forEachIndexed { index, line ->
                        val y = startY + (index * lineHeight)
                        canvas.drawText(line, posX, y, textPaint)
                    }
                } else {
                    canvas.drawText(text, posX, balloonY + textBounds.height() + padding / 2, textPaint)
                }
                
                // Рисуваме малка стрелка надолу към точката
                val path = android.graphics.Path()
                path.moveTo(posX - 8f, balloonY + rectHeight)
                path.lineTo(posX + 8f, balloonY + rectHeight)
                path.lineTo(posX, balloonY + rectHeight + 12f)
                path.close()
                canvas.drawPath(path, paint)
            }
            
            override fun getOffset(): MPPointF {
                return MPPointF(-width / 2f, -height.toFloat())
            }
        }
        
        holder.chart.marker = smartMarker
        currentMarkerView = smartMarker
    }
    
    private fun determinePointType(x: Float, attempt: DragAttempt, closestTo200Normalized: Float? = null): PointTooltipMarker.PointType? {
        val (speedSamples, timestamps) = getAlignedSpeedData(attempt)
        
        // Проверяваме за 100 km/h точка - много по-тесен радиус
        // За 100-200 режим НЕ показваме маркер на 100 km/h (графиката започва от там)
        if (attempt.time0to100 > 0 && measurementMode != MeasurementMode.HUNDRED_TO_200) {
            val crossing100 = findSpeedCrossingPoint(speedSamples, timestamps, 100f)
            if (crossing100 != null && kotlin.math.abs(x - crossing100) < 0.05f) {
                return PointTooltipMarker.PointType.SPEED_100
            }
        }
        
        // Проверяваме за 200 km/h точка - много по-тесен радиус
        val shouldCheck200 = when (measurementMode) {
            MeasurementMode.HUNDRED_TO_200 -> attempt.time100to200 > 0
            else -> attempt.time0to200 > 0
        }
        
        Log.d("DragSessionDetails", "📊 Checking 200 km/h marker: shouldCheck=$shouldCheck200, time100to200=${attempt.time100to200}")
        Log.d("DragSessionDetails", "📊 Attempt details: time0to100=${attempt.time0to100}, time0to200=${attempt.time0to200}, time100to200=${attempt.time100to200}")
        
        if (shouldCheck200) {
            // За 100-200 режим: използваме предварително изчисления closestTo200Normalized
            if (measurementMode == MeasurementMode.HUNDRED_TO_200) {
                if (closestTo200Normalized != null && kotlin.math.abs(x - closestTo200Normalized!!) < 0.1f) {
                    Log.d("DragSessionDetails", "📊 Found 200 km/h marker at x=$x, closestTo200=$closestTo200Normalized")
                    return PointTooltipMarker.PointType.SPEED_200
                }
            } else {
                // За други режими: използваме старата логика
                val crossing200 = findSpeedCrossingPoint(speedSamples, timestamps, 200f)
                
                if (crossing200 != null && kotlin.math.abs(x - crossing200) < 0.05f) {
                    return PointTooltipMarker.PointType.SPEED_200
                }
            }
        }
        
        // Проверяваме за 402m точка - много по-тесен радиус
        if (attempt.time0to402 > 0) {
            // Показваме реалното време без нормализация
            val time402Seconds = attempt.time0to402 / 1_000_000_000.0f
            if (kotlin.math.abs(x - time402Seconds) < 0.05f) {
                return PointTooltipMarker.PointType.DISTANCE_402
            }
        }
        
        // Не е на специална точка
        return null
    }
    
    // Помощна функция - намира скоростта в даден момент
    private fun getSpeedAtTime(attempt: DragAttempt, timeSeconds: Float): Float {
        val (speedSamples, timestamps) = getAlignedSpeedData(attempt)
        return interpolateValueAtTime(speedSamples, timestamps, timeSeconds)
    }

    // Нова функция - намира ТОЧНОТО време когато скоростта пресича targetSpeed
    private fun findSpeedCrossingPoint(speeds: List<Float>, timestamps: List<Long>, targetSpeed: Float): Float? {
        // Показваме реалните времена без нормализация
        for (i in 1 until speeds.size) {
            val v0 = speeds[i - 1]
            val v1 = speeds[i]
            val t0 = timestamps[i - 1] / 1_000_000_000.0f
            val t1 = timestamps[i] / 1_000_000_000.0f

            // Проверяваме дали има пресичане между двете точки
            if (v0 < targetSpeed && v1 >= targetSpeed) {
                // Линейна интерполация за точното време на пресичането
                if (v1 != v0) {
                    val ratio = (targetSpeed - v0) / (v1 - v0)
                    val crossingTime = t0 + (t1 - t0) * ratio
                    
                    
                    return crossingTime
                }
                return t1
            }
        }
        
        return null
    }

    // Променена функция - използва интерполация за точна стойност
    private fun findValueAtTimeInterpolated(attempt: DragAttempt, targetTimeSeconds: Float, mode: ChartMode): Float {
        return when (mode) {
            ChartMode.SPEED -> {
                val (speedSamples, timestamps) = getAlignedSpeedData(attempt)
                interpolateValueAtTime(speedSamples, timestamps, targetTimeSeconds)
            }
            ChartMode.ACCELERATION -> {
                val (accelSamples, timestamps) = getAlignedAccelData(attempt)
                interpolateValueAtTime(accelSamples, timestamps, targetTimeSeconds)
            }
            ChartMode.G_FORCE -> {
                val (gSamples, timestamps) = getAlignedGData(attempt)
                interpolateValueAtTime(gSamples, timestamps, targetTimeSeconds)
            }
        }
    }

    // Помощна функция - интерполира стойност по време
    private fun interpolateValueAtTime(values: List<Float>, timestamps: List<Long>, targetTimeSeconds: Float): Float {
        if (values.isEmpty() || timestamps.isEmpty()) return 0f

        // Използваме същата нормализация като графиката - без нормализация спрямо първия timestamp
        val targetTimeNanos = (targetTimeSeconds * 1_000_000_000).toLong()

        // Намираме двете съседни точки
        for (i in 1 until timestamps.size) {
            val t0 = timestamps[i - 1]
            val t1 = timestamps[i]

            if (targetTimeNanos >= t0 && targetTimeNanos <= t1) {
                val v0 = values[i - 1]
                val v1 = values[i]

                // Линейна интерполация
                val ratio = (targetTimeNanos - t0).toFloat() / (t1 - t0).toFloat()
                return v0 + (v1 - v0) * ratio
            }
        }

        // Ако времето е извън диапазона, връщаме последната стойност
        return values.lastOrNull() ?: 0f
    }

    private fun findValueAtTime(attempt: DragAttempt, targetTimeSeconds: Float, mode: ChartMode): Float {
        return when (mode) {
            ChartMode.SPEED -> {
                val (speedSamples, timestamps) = getAlignedSpeedData(attempt)
                findClosestValue(speedSamples, timestamps, targetTimeSeconds)
            }
            ChartMode.ACCELERATION -> {
                val (accelSamples, timestamps) = getAlignedAccelData(attempt)
                findClosestValue(accelSamples, timestamps, targetTimeSeconds)
            }
            ChartMode.G_FORCE -> {
                val (gSamples, timestamps) = getAlignedGData(attempt)
                findClosestValue(gSamples, timestamps, targetTimeSeconds)
            }
        }
    }

    private fun findClosestValue(values: List<Float>, timestamps: List<Long>, targetTimeSeconds: Float): Float {
        if (values.isEmpty() || timestamps.isEmpty()) return 0f

        val targetTimeNanos = (targetTimeSeconds * 1_000_000_000).toLong()

        // Намираме най-близкия индекс
        var closestIndex = 0
        var minDiff = kotlin.math.abs(timestamps[0] - targetTimeNanos)

        for (i in 1 until timestamps.size) {
            val diff = kotlin.math.abs(timestamps[i] - targetTimeNanos)
            if (diff < minDiff) {
                minDiff = diff
                closestIndex = i
            }
        }

        return values[closestIndex]
    }

    // Намира точното време (в секунди) когато скоростта пресича targetSpeed, чрез линейна интерполация
    private fun findInterpolatedTimeForSpeedCrossing(attempt: DragAttempt, targetSpeed: Float): Float {
        val speedSamples = attempt.speedSamples.filter { it.isFinite() && !it.isNaN() }
        val timestamps = attempt.speedTimeStamps
        if (speedSamples.size < 2 || timestamps.size < 2) return 0f

        for (i in 1 until speedSamples.size) {
            val v0 = speedSamples[i - 1]
            val v1 = speedSamples[i]
            if ((v0 <= targetSpeed && v1 >= targetSpeed) || (v0 >= targetSpeed && v1 <= targetSpeed)) {
                val t0 = timestamps[i - 1] / 1000.0f
                val t1 = timestamps[i] / 1000.0f
                if (v1 == v0) return t1
                val ratio = (targetSpeed - v0) / (v1 - v0)
                return t0 + (t1 - t0) * ratio
            }
        }
        return 0f
    }
    
    private fun findTimeWhenSpeedReached(attempt: DragAttempt, targetSpeed: Float, mode: ChartMode): Float {
        // Винаги използваме speed данните за намиране на времето
        val speedSamples = attempt.speedSamples.filter { it.isFinite() && !it.isNaN() }
        val speedTimestamps = attempt.speedTimeStamps
        
        if (speedSamples.isNotEmpty() && speedTimestamps.isNotEmpty()) {
            // Намираме началното време за нормализация спрямо текущия режим
            val startTime = when (mode) {
                ChartMode.SPEED -> speedTimestamps[0]
                ChartMode.ACCELERATION -> attempt.gpsTimeStamps.minOrNull() ?: speedTimestamps[0]
                ChartMode.G_FORCE -> attempt.timeStamps.minOrNull() ?: speedTimestamps[0]
            }
            
            // Намираме кога е постигната целта
            for (i in speedSamples.indices) {
                if (speedSamples[i] >= targetSpeed) {
                    return (speedTimestamps[i] - startTime) / 1000.0f
                }
            }
        }
        return 0f
    }
    
    private fun findTimeWhenDistanceReached(attempt: DragAttempt, targetDistance: Float, mode: ChartMode): Float {
        // За 402m използваме измереното време без нормализация
        val time402Seconds = (attempt.time0to402 / 1_000_000_000.0).toFloat()
        
        // Намираме началното време за нормализация спрямо текущия режим
        val startTime = when (mode) {
            ChartMode.SPEED -> attempt.speedTimeStamps.minOrNull() ?: 0L
            ChartMode.ACCELERATION -> attempt.gpsTimeStamps.minOrNull() ?: 0L
            ChartMode.G_FORCE -> attempt.timeStamps.minOrNull() ?: 0L
        }
        
        // Изчисляваме реалното време спрямо данните
        val speedStartTime = attempt.speedTimeStamps.minOrNull() ?: 0L
        val realTimeMs = speedStartTime + (time402Seconds * 1000).toLong()
        return (realTimeMs - startTime) / 1000.0f
    }
    
    private fun findTimeWhenSpeedReachedInData(attempt: DragAttempt, targetSpeed: Float, mode: ChartMode): Float {
        // Използваме speed данните за намиране на времето
        val (speedSamples, speedTimestamps) = getAlignedSpeedData(attempt)
        
        if (speedSamples.isNotEmpty() && speedTimestamps.isNotEmpty()) {
            for (i in speedSamples.indices) {
                if (speedSamples[i] >= targetSpeed) {
                    return speedTimestamps[i] / 1000.0f // Конвертираме в секунди
                }
            }
        }
        return 0f
    }
    
    private fun findTimeWhenDistanceReachedInData(attempt: DragAttempt, targetDistance: Float, mode: ChartMode): Float {
        // За 402m използваме измереното време без нормализация
        val time402Seconds = (attempt.time0to402 / 1_000_000_000.0).toFloat()
        return time402Seconds
    }

    private fun getMaxMeasuredTimeForGraphs(attempt: DragAttempt): Double {
        // Използвай duration ако е налична (това е измереното време в nanoseconds)
        if (attempt.duration > 0) {
            return attempt.duration / 1_000_000_000.0 // Конвертирай от nanoseconds в секунди
        }

        // Иначе изчисли от измерените времена
        val times = mutableListOf<Long>()

        // За различните режими, вземи съответното време
        when (measurementMode) {
            MeasurementMode.ZERO_TO_100 -> {
                if (attempt.time0to100 > 0) times.add(attempt.time0to100)
            }
            MeasurementMode.ZERO_TO_200 -> {
                if (attempt.time0to200 > 0) times.add(attempt.time0to200)
            }
            MeasurementMode.HUNDRED_TO_200 -> {
                if (attempt.time100to200 > 0) times.add(attempt.time100to200)
            }
            MeasurementMode.QUARTER_MILE -> {
                if (attempt.time0to402 > 0) times.add(attempt.time0to402)
            }
            MeasurementMode.ALL -> {
                if (attempt.time0to100 > 0) times.add(attempt.time0to100)
                if (attempt.time0to200 > 0) times.add(attempt.time0to200)
                if (attempt.time0to402 > 0) times.add(attempt.time0to402)
                // Не добавяме 100-200 за ALL защото е част от 0-200
            }
        }

        return if (times.isNotEmpty()) {
            times.maxOrNull()?.let { it / 1_000_000_000.0 } ?: 0.0
        } else {
            // Ако няма измерени времена, опитай да изчислиш от timestamps
            val allTimestamps = listOf(
                attempt.speedTimeStamps.maxOrNull() ?: 0L,
                attempt.timeStamps.maxOrNull() ?: 0L,
                attempt.gpsTimeStamps.maxOrNull() ?: 0L
            ).filter { it > 0 }
            
            if (allTimestamps.isNotEmpty()) {
                allTimestamps.maxOrNull()!! / 1000.0 // Конвертирай от milliseconds в секунди
            } else {
                0.0
            }
        } // ЗАТВАРЯ return if
    } // ЗАТВАРЯ функция

    private fun updateVisibility(holder: AttemptViewHolder) {
        when (measurementMode) {
            MeasurementMode.ZERO_TO_100 -> {
                holder.tvTime0to100.visibility = View.VISIBLE
                holder.tvTime0to200.visibility = View.GONE
                holder.tvTime100to200.visibility = View.GONE
                holder.tvTime0to402.visibility = View.GONE
            }
            MeasurementMode.ZERO_TO_200 -> {
                holder.tvTime0to100.visibility = View.VISIBLE
                holder.tvTime0to200.visibility = View.VISIBLE
                holder.tvTime100to200.visibility = View.GONE
                holder.tvTime0to402.visibility = View.GONE
            }
            MeasurementMode.HUNDRED_TO_200 -> {
                holder.tvTime0to100.visibility = View.GONE
                holder.tvTime0to200.visibility = View.GONE
                holder.tvTime100to200.visibility = View.VISIBLE
                holder.tvTime0to402.visibility = View.GONE
            }
            MeasurementMode.QUARTER_MILE -> {
                holder.tvTime0to100.visibility = View.GONE
                holder.tvTime0to200.visibility = View.GONE
                holder.tvTime100to200.visibility = View.GONE
                holder.tvTime0to402.visibility = View.VISIBLE
            }
            MeasurementMode.ALL -> {
                holder.tvTime0to100.visibility = View.VISIBLE
                holder.tvTime0to200.visibility = View.VISIBLE
                holder.tvTime100to200.visibility = View.VISIBLE
                holder.tvTime0to402.visibility = View.VISIBLE
            }
        }
    }

    override fun getItemCount(): Int = attempts.size

    private fun getMaxMeasuredTime(attempt: DragAttempt): Double {
        val times = mutableListOf<Long>()

        when (measurementMode) {
            MeasurementMode.ZERO_TO_100 -> {
                if (attempt.time0to100 > 0) times.add(attempt.time0to100)
            }
            MeasurementMode.ZERO_TO_200 -> {
                if (attempt.time0to200 > 0) times.add(attempt.time0to200)
            }
            MeasurementMode.HUNDRED_TO_200 -> {
                if (attempt.time100to200 > 0) times.add(attempt.time100to200)
            }
            MeasurementMode.QUARTER_MILE -> {
                if (attempt.time0to402 > 0) times.add(attempt.time0to402)
            }
            MeasurementMode.ALL -> {
                if (attempt.time0to100 > 0) times.add(attempt.time0to100)
                if (attempt.time0to200 > 0) times.add(attempt.time0to200)
                if (attempt.time0to402 > 0) times.add(attempt.time0to402)
            }
        }

        return if (times.isNotEmpty()) {
            times.maxOrNull()?.let { it / 1_000_000_000.0 } ?: 0.0
        } else {
            0.0
        }
    }
    
    private fun formatTime(label: String, nanos: Long): String {
        return if (nanos > 0) {
            val seconds = nanos / 1_000_000_000.0
            "$label\n${String.format("%.3f s", seconds)}"  // Добавяме интервал за съответствие
        } else {
            "$label\n--"
        }
    }
    
    private fun formatTimeWithLabelNormalized(label: String?, time: Long?, firstAttempt: DragAttempt?): String {
        if (time == null || time <= 0) return "-"

        // НЕ нормализираме времената - показваме ги като са записани
        // Нормализацията се използва само за графиката, не за дисплея
        val displayTime = time / 1_000_000_000.0

        return if (!label.isNullOrEmpty()) {
            "$label\n${String.format("%.3f s", displayTime)}"  // Добавяме \n за съответствие с formatTime
        } else {
            String.format("%.3f s", displayTime)
        }
    }
}