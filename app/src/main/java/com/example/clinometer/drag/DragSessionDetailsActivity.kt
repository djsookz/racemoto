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
import com.example.clinometer.settings.LanguageManager
import com.example.clinometer.settings.UnitsManager
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.listener.ChartTouchListener

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
    private lateinit var attemptsAdapter: DragAttemptsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drag_session_details)
        
        setupScreenKeepOn()

        val sessionId = intent.getLongExtra("SESSION_ID", -1L)
        if (sessionId == -1L) {
            finish()
            return
        }

        session = DragStorage.getDragSession(this, sessionId)
        if (session == null) {
            finish()
            return
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
                    tvDetailTemperature.text = UnitsManager.formatTemperature(s.temperature!!, this)
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
                    MeasurementMode.valueOf(s.measurementMode ?: "ALL")
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
        if (speeds.isEmpty() || times.isEmpty()) return null

        for (i in 1 until speeds.size) {
            val s0 = speeds[i - 1]
            val s1 = speeds[i]
            // Търсим първото преминаване нагоре през targetKmH
            if (s0 < targetKmH && s1 >= targetKmH) {
                val t0 = times[i - 1].toDouble()
                val t1 = times[i].toDouble()
                val ratio = ((targetKmH - s0) / (s1 - s0).coerceAtLeast(0.0001f)).toDouble()
                val tCross = t0 + (t1 - t0) * ratio
                return (tCross / 1_000_000_000.0).toFloat()
            }
        }
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

        // Показване на продължителността
        val displayDuration = if (attempt.duration > 0) {
            attempt.duration / 1_000_000_000.0 // от nanoseconds в секунди
        } else {
            getMaxMeasuredTime(attempt)
        }

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
        setupChartConfiguration(holder.chart)
        
        // Настройваме бутоните
        setupChartButtons(holder, attempt)
        
        // Показваме данните за скорост по подразбиране
        updateChartData(holder, attempt, ChartMode.SPEED)
    }
    
    private fun setupChartConfiguration(chart: com.github.mikephil.charting.charts.LineChart) {
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
            override fun onChartSingleTapped(me: MotionEvent?) {}
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
        // RAW данни - без филтри, показваме всичко както е записано
        return speeds.take(limit) to times.take(limit)
    }

    private fun getAlignedAccelData(attempt: DragAttempt): Pair<List<Float>, List<Long>> {
        val vals = attempt.gpsAccelSamples
        val times = attempt.gpsTimeStamps
        val limit = minOf(vals.size, times.size)
        // RAW данни - без филтри, показваме всичко както е записано
        return vals.take(limit) to times.take(limit)
    }

    // -------- Start offset helpers (begin charts at 4 km/h) --------
    private fun getSpeedStartOffsetMs(attempt: DragAttempt, thresholdKmH: Float = 4f): Long {
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
        
        // Обновяваме стила на бутоните
        updateButtonStyles(holder, mode)
        
        // Обновяваме данните на графиката
        updateChartData(holder, attempt, mode)
        
        // Обновяваме настройките на графиката при всяко превключване
        setupChartConfiguration(holder.chart)
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
    
    private fun updateSpeedChart(holder: AttemptViewHolder, attempt: DragAttempt) {
        val speedUnitSymbol = UnitsManager.getSpeedUnit(context).symbol
        holder.tvChartTitle.text = "${context.getString(R.string.track_tab_speed)} ($speedUnitSymbol)"
        
        val (speedSamples, timestamps) = getAlignedSpeedData(attempt)
        
        if (speedSamples.isNotEmpty() && timestamps.isNotEmpty()) {
            val minSize = speedSamples.size
            val entries = mutableListOf<com.github.mikephil.charting.data.Entry>()
            
            // Use measurement start time as reference point for consistency with measurement times
            val firstTimestamp = 0L // Always start from 0 to match measurement timing
            
            // Търсим 100 km/h в данните
            for (i in 0 until minSize) {
                if (speedSamples[i] >= 100f) {
                    val timeAt100 = timestamps[i] / 1_000_000_000.0
                    break
                }
            }
            
            // Convert speed samples to selected unit
            val speedUnit = UnitsManager.getSpeedUnit(context)
            for (i in 0 until minSize) {
                // Use measurement start time as reference point for consistency
                val timeInSeconds = timestamps[i] / 1_000_000_000.0
                val convertedSpeed = UnitsManager.convertSpeed(speedSamples[i], speedUnit)
                entries.add(com.github.mikephil.charting.data.Entry(timeInSeconds.toFloat(), convertedSpeed))
            }
            
            val maxSpeed = speedSamples.maxOrNull() ?: 0f
            val convertedMaxSpeed = UnitsManager.formatSpeed(maxSpeed, context, 0)
            holder.tvChartStats.text = context.getString(R.string.drag_max_speed_label) + " " + convertedMaxSpeed
            
            // Debug проверка за съответствие на времената
            val crossing100 = findSpeedCrossingPoint(speedSamples, timestamps, 100f)
            
            val dataSet = com.github.mikephil.charting.data.LineDataSet(entries, "${context.getString(R.string.drag_tab_speed)} (${speedUnit.symbol})").apply {
                color = ContextCompat.getColor(holder.itemView.context, R.color.accent_blue)
                lineWidth = 2f
                setDrawValues(false)
                setDrawCircles(false)
            }
            
            val lineData = com.github.mikephil.charting.data.LineData(dataSet)
            holder.chart.data = lineData
            
            // Настройваме Y оста - конвертирана в избраната единица
            val yAxis = holder.chart.axisLeft
            val convertedMax = UnitsManager.convertSpeed(maxSpeed, speedUnit)
            val threshold200 = UnitsManager.convertSpeed(200f, speedUnit)
            yAxis.axisMinimum = 0f
            yAxis.axisMaximum = if (convertedMax > threshold200) convertedMax * 1.1f else threshold200
            yAxis.setDrawZeroLine(true)
            yAxis.zeroLineColor = android.graphics.Color.GRAY
            yAxis.zeroLineWidth = 1f
            
            // Форматиране за Y-оста - само цели числа за скоростта
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
            
            // Използваме същата нормализация като маркерите - спрямо първия timestamp
            val firstTimestamp = timestamps.firstOrNull() ?: 0L
            
            for (i in 0 until minSize) {
                // Нормализираме спрямо първия timestamp, както правят маркерите
                val timeInSeconds = (timestamps[i] - firstTimestamp) / 1_000_000_000.0
                entries.add(com.github.mikephil.charting.data.Entry(timeInSeconds.toFloat(), accelSamples[i]))
            }
            
            val maxAccel = accelSamples.maxOrNull() ?: 0f
            holder.tvChartStats.text = context.getString(R.string.drag_chart_max_accel, maxAccel)
            
            val dataSet = com.github.mikephil.charting.data.LineDataSet(entries, context.getString(R.string.drag_tab_acceleration)).apply {
                color = ContextCompat.getColor(holder.itemView.context, R.color.accent_green)
                lineWidth = 2f
                setDrawValues(false)
                setDrawCircles(false)
            }
            
            val lineData = com.github.mikephil.charting.data.LineData(dataSet)
            holder.chart.data = lineData
            
            // Настройваме Y оста
            val yAxis = holder.chart.axisLeft
            yAxis.axisMinimum = 0f
            yAxis.axisMaximum = if (maxAccel > 0) maxAccel * 1.1f else 20f
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
            
            // Използваме същата нормализация като маркерите - спрямо първия timestamp
            val firstTimestamp = timestamps.firstOrNull() ?: 0L
            
            for (i in 0 until minSize) {
                // Нормализираме спрямо първия timestamp, както правят маркерите
                val timeInSeconds = (timestamps[i] - firstTimestamp) / 1_000_000_000.0
                entries.add(com.github.mikephil.charting.data.Entry(timeInSeconds.toFloat(), gSamples[i]))
            }
            
            val maxG = gSamples.maxOrNull() ?: 0f
            val minG = gSamples.minOrNull() ?: 0f
            
            
            holder.tvChartStats.text = context.getString(R.string.drag_chart_peak_g, maxG)
            
            val dataSet = com.github.mikephil.charting.data.LineDataSet(entries, context.getString(R.string.drag_tab_gforce)).apply {
                color = ContextCompat.getColor(holder.itemView.context, R.color.accent_red)
                lineWidth = 2f
                setDrawValues(false)
                setDrawCircles(false)
            }
            
            val lineData = com.github.mikephil.charting.data.LineData(dataSet)
            holder.chart.data = lineData
            
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
        // Намираме максималното време от всички измервания
        val allTimes = mutableListOf<Double>()
        
        // Добавяме всички измерени времена (в секунди)
        if (attempt.time0to100 > 0) allTimes.add(attempt.time0to100 / 1_000_000_000.0)
        if (attempt.time0to200 > 0) allTimes.add(attempt.time0to200 / 1_000_000_000.0)
        if (attempt.time100to200 > 0) allTimes.add(attempt.time100to200 / 1_000_000_000.0)
        if (attempt.time0to402 > 0) allTimes.add(attempt.time0to402 / 1_000_000_000.0)
        
        // Добавяме максималното време от timestamps (в секунди)
        // Използваме същата нормализация като графиката - спрямо първия timestamp
        val (_, speedTimestamps) = getAlignedSpeedData(attempt)
        val firstTimestamp = speedTimestamps.firstOrNull() ?: 0L
        
        if (firstTimestamp > 0) {
            val maxTimestamp = listOf(
                attempt.speedTimeStamps.maxOrNull() ?: 0L,
                attempt.timeStamps.maxOrNull() ?: 0L,
                attempt.gpsTimeStamps.maxOrNull() ?: 0L
            ).filter { it > 0 }.maxOrNull() ?: 0L
            
            if (maxTimestamp > 0) {
                // Нормализираме спрямо първия timestamp, както прави графиката
                allTimes.add((maxTimestamp - firstTimestamp) / 1_000_000_000.0)
            }
        }
        
        return allTimes.maxOrNull() ?: 10.0 // По подразбиране 10 секунди
    }

    private fun addKeyPointMarkers(holder: AttemptViewHolder, attempt: DragAttempt, mode: ChartMode) {
        val entries = mutableListOf<com.github.mikephil.charting.data.Entry>()
        val context = holder.itemView.context

        val (speedSamples, timestamps) = getAlignedSpeedData(attempt)
        if (speedSamples.isEmpty() || timestamps.isEmpty()) return

        // Маркер за 100 km/h (зелен) - конвертиран според единицата
        if (attempt.time0to100 > 0) {
            val crossing100 = findSpeedCrossingPoint(speedSamples, timestamps, 100f)
            if (crossing100 != null) {
                val speedUnit = UnitsManager.getSpeedUnit(holder.itemView.context)
                val valueAt100 = when (mode) {
                    ChartMode.SPEED -> UnitsManager.convertSpeed(100f, speedUnit)  // Конвертиран threshold
                    ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, crossing100, mode)
                    ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, crossing100, mode)
                }
                entries.add(com.github.mikephil.charting.data.Entry(crossing100, valueAt100))
            }
        }

        // Маркер за 200 km/h (син) - конвертиран според единицата
        if (attempt.time0to200 > 0) {
            val crossing200 = findSpeedCrossingPoint(speedSamples, timestamps, 200f)
            if (crossing200 != null) {
                val speedUnit = UnitsManager.getSpeedUnit(holder.itemView.context)
                val valueAt200 = when (mode) {
                    ChartMode.SPEED -> UnitsManager.convertSpeed(200f, speedUnit)  // Конвертиран threshold
                    ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, crossing200, mode)
                    ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, crossing200, mode)
                }
                entries.add(com.github.mikephil.charting.data.Entry(crossing200, valueAt200))
            }
        }

        // Маркер за 402m (червен)
        if (attempt.time0to402 > 0) {
            val time402Seconds = attempt.time0to402 / 1_000_000_000.0f
            val valueAt402 = findValueAtTimeInterpolated(attempt, time402Seconds, mode)
            entries.add(com.github.mikephil.charting.data.Entry(time402Seconds, valueAt402))
        }

        if (entries.isNotEmpty()) {
            val existingData = holder.chart.data
            if (existingData != null) {
                entries.forEachIndexed { index, entry ->
                    val color = when (index) {
                        0 -> R.color.accent_green  // 100 km/h
                        1 -> R.color.accent_blue   // 200 km/h
                        2 -> R.color.accent_red    // 402m
                        else -> R.color.white
                    }

                    val markerDataSet = com.github.mikephil.charting.data.LineDataSet(listOf(entry), "").apply {
                        setDrawCircles(true)
                        setDrawValues(false)
                        lineWidth = 0f
                        circleRadius = 8f
                        circleHoleRadius = 4f
                        setCircleColor(ContextCompat.getColor(context, color))
                    }
                    existingData.addDataSet(markerDataSet)
                }
                holder.chart.notifyDataSetChanged()
                holder.chart.invalidate()
            }
        }
    }

    // Нова функция - намира ТОЧНОТО време когато скоростта пресича targetSpeed
    private fun findSpeedCrossingPoint(speeds: List<Float>, timestamps: List<Long>, targetSpeed: Float): Float? {
        
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
        // За 402m използваме измереното време, но го нормализираме спрямо данните
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
        // За 402m използваме измереното време, но го конвертираме в същия формат като данните
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
        }
    }

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

    override fun getItemCount(): Int = attempts.size


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