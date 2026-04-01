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
import androidx.appcompat.app.AlertDialog
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
            val attempts = session?.attempts ?: emptyList()
            when {
                attempts.isEmpty() -> {
                    android.widget.Toast.makeText(this, "Няма опити за сравнение", android.widget.Toast.LENGTH_SHORT).show()
                }
                attempts.size == 1 -> {
                    openSessionSelectionForCompare(attempts.first().id)
                }
                else -> {
                    showAttemptSelectionDialog(attempts)
                }
            }
        }
    }

    private fun showAttemptSelectionDialog(attempts: List<DragAttempt>) {
        val labels = attempts.mapIndexed { index, _ ->
            "Опит #${index + 1}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Избери опит за сравнение")
            .setItems(labels) { _, which ->
                val selected = attempts.getOrNull(which) ?: return@setItems
                openSessionSelectionForCompare(selected.id)
            }
            .setNegativeButton(getString(R.string.dialog_cancel_button), null)
            .show()
    }

    private fun openSessionSelectionForCompare(attemptId: Long) {
        val intent = android.content.Intent(this, SessionSelectionActivity::class.java)
        intent.putExtra("current_session_id", sessionId)
        intent.putExtra("current_attempt_id", attemptId)
        startActivity(intent)
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

    private fun setChartContext(
        chart: com.github.mikephil.charting.charts.LineChart,
        attempt: DragAttempt,
        mode: ChartMode
    ) {
        chart.setTag(R.id.tag_drag_chart_attempt, attempt)
        chart.setTag(R.id.tag_drag_chart_mode, mode)
    }

    private fun getChartAttempt(chart: com.github.mikephil.charting.charts.LineChart): DragAttempt? {
        return chart.getTag(R.id.tag_drag_chart_attempt) as? DragAttempt ?: currentAttempt
    }

    private fun getChartMode(chart: com.github.mikephil.charting.charts.LineChart): ChartMode {
        return chart.getTag(R.id.tag_drag_chart_mode) as? ChartMode ?: currentMode
    }

    private fun getChartMarker(chart: com.github.mikephil.charting.charts.LineChart): com.github.mikephil.charting.components.MarkerView? {
        return chart.getTag(R.id.tag_drag_chart_marker) as? com.github.mikephil.charting.components.MarkerView
            ?: (chart.marker as? com.github.mikephil.charting.components.MarkerView)
            ?: currentMarkerView
    }
    
    // Константа за Y threshold множител (използва се и при drag и при tap)
    companion object {
        private const val SNAP_Y_MULTIPLIER = 10f // Увеличен за по-добро хващане в G-Force и Acceleration
        
        // Data class за специални точки (изваден за performance)
        private data class SpecialPoint(
            val x: Float,
            val y: Float,
            val type: PointTooltipMarker.PointType,
            val priority: Int
        )
        
        /**
         * Централизирана функция за нормализация на времето спрямо start timestamp.
         * Всички функции използват тази за консистентна координатна система.
         * 
         * @param timestamps Списък с timestamps в nanoseconds
         * @return Списък с нормализирани времена в секунди (relative to first timestamp)
         */
        fun normalizeTime(timestamps: List<Long>): List<Float> {
            if (timestamps.isEmpty()) return emptyList()
            val start = timestamps.first()
            return timestamps.map { (it - start) / 1_000_000_000f }
        }
    }

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

        // ⚠️ КРИТИЧНО: Reset-вайте всички бутони ПЪРВО (RecyclerView recycling)
        holder.btnSpeed.setBackgroundResource(R.drawable.button_toggle_unselected)
        holder.btnSpeed.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        
        holder.btnAcceleration.setBackgroundResource(R.drawable.button_toggle_unselected)
        holder.btnAcceleration.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        
        holder.btnGForce.setBackgroundResource(R.drawable.button_toggle_unselected)
        holder.btnGForce.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))

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
        // КРИТИЧНО: Първо задаваме currentAttempt и currentMode (за да работят listener-ите)
        currentAttempt = attempt
        currentMode = ChartMode.SPEED
        setChartContext(holder.chart, attempt, ChartMode.SPEED)
        
        // КРИТИЧНО: Първо настройваме основните настройки на графиката (БЕЗ listener-и)
        holder.chart.setTouchEnabled(true)
        holder.chart.isDragEnabled = true
        holder.chart.setScaleEnabled(true)
        holder.chart.setPinchZoom(true)
        holder.chart.setDoubleTapToZoomEnabled(true)
        holder.chart.axisRight.isEnabled = false
        holder.chart.description.isEnabled = false
        holder.chart.legend.isEnabled = false
        holder.chart.isDragDecelerationEnabled = false
        holder.chart.dragDecelerationFrictionCoef = 0f
        holder.chart.setExtraTopOffset(30f)
        holder.chart.setExtraRightOffset(24f)
        
        holder.chart.xAxis.apply {
            position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            granularity = 0.1f
            textColor = android.graphics.Color.WHITE
            textSize = 12f
            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(x: Float): String {
                    return String.format("%.1fs", x)
                }
            }
        }
        
        holder.chart.axisLeft.apply {
            textColor = android.graphics.Color.WHITE
            textSize = 12f
        }
        
        // Настройваме бутоните
        setupChartButtons(holder, attempt)
        
        // Задаваме началния стил на бутоните (SPEED е активен по подразбиране)
        updateButtonStyles(holder, ChartMode.SPEED)
        
        // КРИТИЧНО: Първо зареждаме данните и създаваме маркера
        updateChartData(holder, attempt, ChartMode.SPEED)
        
        // СЛЕД това настройваме listener-ите (сега маркерът вече е създаден)
        setupChartZoom(holder.chart)
    }
    
    private fun setupChartConfiguration(chart: com.github.mikephil.charting.charts.LineChart, attempt: DragAttempt) {
        // Тази функция не се използва вече - всички настройки са в setupChart
        // Оставяме я само за обратна съвместимост, ако някъде се извиква
        // Настройваме само listener-ите, но трябва да се извиква СЛЕД updateChartData
        setupChartZoom(chart)
    }
    
    // Помощна функция за показване на маркера на специална точка
    private fun showMarkerAtPoint(chart: com.github.mikephil.charting.charts.LineChart, entry: com.github.mikephil.charting.data.Entry, pointType: PointTooltipMarker.PointType, exactTime: Float) {
        val activeAttempt = getChartAttempt(chart) ?: return
        val activeMode = getChartMode(chart)
        getChartMarker(chart)?.let { markerView ->
            // КРИТИЧНО: Използваме индекса на dataset-а за текущия режим, не на специалните точки
            // Специалните точки са отделни dataset-и, но бъбълът трябва да се позиционира спрямо главната линия
            val dataSetIndex = getCurrentModeDataSetIndex(chart, activeMode) ?: run {
                when (activeMode) {
                    ChartMode.SPEED -> {
                        chart.data?.dataSets?.indexOfFirst {
                            it.label.contains("Speed", ignoreCase = true) || it.label.isEmpty()
                        } ?: 0
                    }
                    ChartMode.ACCELERATION -> {
                        chart.data?.dataSets?.indexOfFirst {
                            it.label.contains("Acceleration", ignoreCase = true) || it.label.contains("Accel", ignoreCase = true)
                        } ?: 0
                    }
                    ChartMode.G_FORCE -> {
                        chart.data?.dataSets?.indexOfFirst {
                            it.label.contains("G-Force", ignoreCase = true) || it.label.contains("G Force", ignoreCase = true) || it.label.contains("GForce", ignoreCase = true)
                        } ?: 0
                    }
                }
            }
            val validDataSetIndex = dataSetIndex.coerceIn(0, (chart.data?.dataSets?.size ?: 1) - 1)
            
            // КРИТИЧНО: За специални точки, използваме точно Y координатата на главната линия в този момент
            // Това гарантира, че бъбълът се позиционира точно на точката
            val exactYForHighlight = when (activeMode) {
                ChartMode.SPEED -> {
                    // За Speed режим, използваме точно скоростта в този момент
                    val speedUnit = UnitsManager.getSpeedUnit(chart.context)
                    when (pointType) {
                        PointTooltipMarker.PointType.SPEED_100 -> UnitsManager.convertSpeed(100f, speedUnit)
                        PointTooltipMarker.PointType.SPEED_200 -> UnitsManager.convertSpeed(200f, speedUnit)
                        PointTooltipMarker.PointType.DISTANCE_402 -> {
                            // За 0-402m, използваме реалната скорост в този момент
                            findValueAtTimeInterpolated(activeAttempt, entry.x, activeMode)
                        }
                    }
                }
                ChartMode.ACCELERATION, ChartMode.G_FORCE -> {
                    // За Acceleration и G-Force, използваме интерполираната стойност
                    findValueAtTimeInterpolated(activeAttempt, entry.x, activeMode)
                }
            }
            
            // КРИТИЧНО: Използваме точно Y координатата за правилно позициониране
            val highlight = com.github.mikephil.charting.highlight.Highlight(entry.x, exactYForHighlight, validDataSetIndex)
            
            // Обновяваме entry-то с точната Y координата
            val correctedEntry = com.github.mikephil.charting.data.Entry(entry.x, exactYForHighlight)
            markerView.refreshContent(correctedEntry, highlight)
            
            try {
                // КРИТИЧНО: Активираме маркера за показване
                val shouldShowField = markerView.javaClass.getDeclaredField("shouldShow")
                shouldShowField.isAccessible = true
                shouldShowField.set(markerView, true)
                
                val pointTypeField = markerView.javaClass.getDeclaredField("pointType")
                pointTypeField.isAccessible = true
                pointTypeField.set(markerView, pointType)

                val isOnSpecialPointField = markerView.javaClass.getDeclaredField("isOnSpecialPoint")
                isOnSpecialPointField.isAccessible = true
                isOnSpecialPointField.set(markerView, true)

                val actualValueField = markerView.javaClass.getDeclaredField("actualValue")
                actualValueField.isAccessible = true
                actualValueField.set(markerView, entry.y)

                val exactTimeField = markerView.javaClass.getDeclaredField("exactTime")
                exactTimeField.isAccessible = true
                exactTimeField.set(markerView, exactTime)

                val modeField = markerView.javaClass.getDeclaredField("mode")
                modeField.isAccessible = true
                modeField.set(markerView, activeMode)

                val attemptField = markerView.javaClass.getDeclaredField("attempt")
                attemptField.isAccessible = true
                attemptField.set(markerView, activeAttempt)
            } catch (ex: Exception) {
                // Reflection failed
            }

            chart.highlightValue(null, false)
            chart.highlightValue(highlight, false)
            chart.invalidate()
        }
    }
    
    // Помощна функция за намиране на най-близката специална точка (използва се и при drag и при tap)
    private fun findClosestSpecialPoint(
        touchX: Float,
        touchY: Float,
        attempt: DragAttempt,
        mode: ChartMode,
        snapThreshold: Float,
        context: android.content.Context
    ): SpecialPoint? {
        val specialPoints = mutableListOf<SpecialPoint>()
        val (speedSamples, timestamps) = getAlignedSpeedData(attempt)

        // 0-100 km/h - приоритет 1 (най-нисък, проверява се последен)
        // КРИТИЧНО: Използваме абсолютно време (в секунди), не нормализирано
        // Това гарантира, че snapping работи правилно спрямо абсолютните времена от tooltip-а
        val startTime = timestamps.first()
        if (attempt.time0to100 > 0 && measurementMode != MeasurementMode.HUNDRED_TO_200) {
            val time100Absolute = attempt.time0to100 / 1_000_000_000.0f
            val time100Normalized = (attempt.time0to100 - startTime) / 1_000_000_000.0f
            val speedUnit = UnitsManager.getSpeedUnit(context)
            val y100 = when (mode) {
                ChartMode.SPEED -> UnitsManager.convertSpeed(100f, speedUnit)
                ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, time100Normalized, mode)
                ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, time100Normalized, mode)
            }
            specialPoints.add(SpecialPoint(time100Absolute, y100, PointTooltipMarker.PointType.SPEED_100, 1))
        }

        // 0-200 km/h (или 100-200 в зависимост от режима) - приоритет 2
        // КРИТИЧНО: Използваме абсолютно време (в секунди), не нормализирано
        if (measurementMode == MeasurementMode.HUNDRED_TO_200) {
            if (attempt.time100to200 > 0) {
                // За 100-200: time100to200 е продължителността, трябва да добавим startTime
                val absoluteTime200 = startTime + attempt.time100to200
                val time200Absolute = absoluteTime200 / 1_000_000_000.0f
                val time200Normalized = attempt.time100to200 / 1_000_000_000.0f
                val speedUnit = UnitsManager.getSpeedUnit(context)
                val y200 = when (mode) {
                    ChartMode.SPEED -> UnitsManager.convertSpeed(200f, speedUnit)
                    ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, time200Normalized, mode)
                    ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, time200Normalized, mode)
                }
                specialPoints.add(SpecialPoint(time200Absolute, y200, PointTooltipMarker.PointType.SPEED_200, 2))
            }
        } else {
            if (attempt.time0to200 > 0) {
                // За други режими: използваме абсолютното време от attempt.time0to200
                val time200Absolute = attempt.time0to200 / 1_000_000_000.0f
                val time200Normalized = (attempt.time0to200 - startTime) / 1_000_000_000.0f
                val speedUnit = UnitsManager.getSpeedUnit(context)
                val y200 = when (mode) {
                    ChartMode.SPEED -> UnitsManager.convertSpeed(200f, speedUnit)
                    ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, time200Normalized, mode)
                    ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, time200Normalized, mode)
                }
                specialPoints.add(SpecialPoint(time200Absolute, y200, PointTooltipMarker.PointType.SPEED_200, 2))
            }
        }

        // 0-402m - приоритет 3 (най-висок, проверява се първи)
        // КРИТИЧНО: Използваме абсолютно време (в секунди), не нормализирано
        if (attempt.time0to402 > 0) {
            val time402Absolute = attempt.time0to402 / 1_000_000_000.0f
            val time402Normalized = (attempt.time0to402 - startTime) / 1_000_000_000.0f
            if (time402Normalized > 0f) {
                val y402 = findValueAtTimeInterpolated(attempt, time402Normalized, mode)
                specialPoints.add(SpecialPoint(time402Absolute, y402, PointTooltipMarker.PointType.DISTANCE_402, 3))
            }
        }

        // Проверяваме за SNAPPING - намираме най-близката специална точка
        // КРИТИЧНО: Използваме 2D разстояние (X И Y) за по-точно определяне
        // КРИТИЧНО: Ако има множество близки точки, избираме тази с най-висок приоритет
        val candidatePoints = mutableListOf<Pair<SpecialPoint, Float>>()
        val xThreshold = snapThreshold
        val yThreshold = snapThreshold * SNAP_Y_MULTIPLIER // Използваме константа

        for (point in specialPoints) {
            // Изчисляваме разстояние по X и Y отделно
            val dx = kotlin.math.abs(touchX - point.x)
            val dy = kotlin.math.abs(touchY - point.y)
            
            // КРИТИЧНО: Проверяваме дали сме близо и по X И по Y (без изключения)
            if (dx < xThreshold && dy < yThreshold) {
                val distance2D = kotlin.math.sqrt(dx * dx + dy * dy)
                candidatePoints.add(point to distance2D)
            }
        }

        // Избираме точката с най-висок приоритет
        // Ако има множество точки с еднакъв приоритет, избираме най-близката по 2D разстояние
        return if (candidatePoints.isNotEmpty()) {
            candidatePoints.maxByOrNull { (point, _) -> point.priority }?.let { (highestPriorityPoint, _) ->
                // Намираме всички точки с този приоритет
                val samePriority = candidatePoints.filter { (p, _) -> p.priority == highestPriorityPoint.priority }
                // Избираме най-близката от тях
                samePriority.minByOrNull { (_, distance) -> distance }?.first
            }
        } else null
    }
    
    // Помощна функция за намиране на индекса на dataset-а за текущия режим
    private fun getCurrentModeDataSetIndex(chart: com.github.mikephil.charting.charts.LineChart, mode: ChartMode): Int? {
        val dataSets = chart.data?.dataSets ?: return null
        
        // Определяме очаквания label за текущия режим
        val expectedLabelPatterns = when (mode) {
            ChartMode.SPEED -> {
                val speedUnit = UnitsManager.getSpeedUnit(chart.context)
                listOf(
                    "${chart.context.getString(R.string.drag_tab_speed)} (${speedUnit.symbol})",
                    chart.context.getString(R.string.drag_tab_speed)
                )
            }
            ChartMode.ACCELERATION -> listOf(
                chart.context.getString(R.string.drag_tab_acceleration),
                "Acceleration",
                "Accel"
            )
            ChartMode.G_FORCE -> listOf(
                chart.context.getString(R.string.drag_tab_gforce),
                "G-Force",
                "G Force",
                "GForce"
            )
        }
        
        // Намираме първия dataset който отговаря на текущия режим
        for (i in dataSets.indices) {
            val label = dataSets[i].label
            if (expectedLabelPatterns.any { pattern -> 
                label.contains(pattern, ignoreCase = true) 
            }) {
                return i
            }
        }
        
        return null
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

        // Value selected listener за tooltip-и с SNAPPING логика
        chart.setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
            override fun onValueSelected(e: com.github.mikephil.charting.data.Entry?, h: com.github.mikephil.charting.highlight.Highlight?) {
                val activeAttempt = getChartAttempt(chart)
                val activeMode = getChartMode(chart)
                val activeMarker = getChartMarker(chart)

                if (e == null || h == null || activeAttempt == null) {
                    chart.highlightValue(null)
                    // СКРИВАМЕ маркера при празен highlight
                    activeMarker?.let { markerView ->
                        try {
                            val shouldShowField = markerView.javaClass.getDeclaredField("shouldShow")
                            shouldShowField.isAccessible = true
                            shouldShowField.set(markerView, false)
                        } catch (ex: Exception) {}
                    }
                    chart.invalidate()
                    return
                }

                // КРИТИЧНО: Проверяваме дали събитието е от dataset-а на текущия режим
                // Ако не е, игнорираме го - позволяваме плъзгане само по текущата линия
                val touchedDataSetIndex = h.dataSetIndex.coerceIn(0, (chart.data?.dataSets?.size ?: 1) - 1)
                val touchedDataSet = chart.data?.dataSets?.get(touchedDataSetIndex)
                
                // НОВА ПРОВЕРКА: Скриваме само ако е НЕАКТИВНА ГЛАВНА ЛИНИЯ (има label и isHighlightEnabled = false)
                // Маркерите имат ПРАЗЕН label ("") → позволяваме им да тригърват бъбъл
                if (touchedDataSet?.isHighlightEnabled != true && touchedDataSet?.label?.isNotEmpty() == true) {
                    chart.highlightValue(null) // Изчистваме всякакви highlight-и
                    // СКРИВАМЕ маркера при неактивен dataset
                    activeMarker?.let { markerView ->
                        try {
                            val shouldShowField = markerView.javaClass.getDeclaredField("shouldShow")
                            shouldShowField.isAccessible = true
                            shouldShowField.set(markerView, false)
                        } catch (ex: Exception) {}
                    }
                    chart.invalidate()
                    return
                }
                
                // ПРЕМАХНАТА БЛОКА: позволяваме следващата логика да намери special point
                // (не правим ранно return тук) - това позволява на специалните dataset-и (маркерите) 
                // да се обработват правилно и да се показват бъбъли при клик/драг

                val attempt = activeAttempt
                val snapThreshold = 0.4f // Радиус за snapping (0.3 секунди)
                var finalEntry = e
                var specialPointType: PointTooltipMarker.PointType? = null
                var isSpecial = false

                // Използваме помощна функция за намиране на най-близката специална точка
                val closestSpecialPoint = findClosestSpecialPoint(
                    e.x,
                    e.y,
                    attempt,
                    activeMode,
                    snapThreshold,
                    chart.context
                )

                // Ако сме близо до специална точка - SNAP към нея
                val finalSpecialPoint = closestSpecialPoint
                if (finalSpecialPoint != null) {
                    finalEntry = com.github.mikephil.charting.data.Entry(finalSpecialPoint.x, finalSpecialPoint.y)
                    specialPointType = finalSpecialPoint.type
                    isSpecial = true
                } else {
                    specialPointType = null
                    isSpecial = false
                }

                // Обновяваме маркера
                // КРИТИЧНО: ПЪРВО задаваме свойствата чрез reflection, СЛЕД това refreshContent
                // Това гарантира, че pointType е правилно зададен преди refreshContent да се извика
                activeMarker?.let { markerView ->
                    // Задаваме свойствата чрез reflection ПРЕДИ refreshContent
                    try {
                        // КРИТИЧНО: Активираме маркера за показване
                        val shouldShowField = markerView.javaClass.getDeclaredField("shouldShow")
                        shouldShowField.isAccessible = true
                        shouldShowField.set(markerView, true)
                        
                        val pointTypeField = markerView.javaClass.getDeclaredField("pointType")
                        pointTypeField.isAccessible = true
                        // КРИТИЧНО: Винаги задаваме pointType - ако е null (нормална точка), задаваме SPEED_100 като fallback
                        // Това предотвратява оставане на стар тип при превключване на режим
                        pointTypeField.set(markerView, specialPointType ?: PointTooltipMarker.PointType.SPEED_100)

                        val isOnSpecialPointField = markerView.javaClass.getDeclaredField("isOnSpecialPoint")
                        isOnSpecialPointField.isAccessible = true
                        isOnSpecialPointField.set(markerView, isSpecial)

                        val actualValueField = markerView.javaClass.getDeclaredField("actualValue")
                        actualValueField.isAccessible = true
                        actualValueField.set(markerView, finalEntry.y)

                        // Задаваме точното време от attempt (за да съвпада с Best Times)
                        val exactTimeField = markerView.javaClass.getDeclaredField("exactTime")
                        exactTimeField.isAccessible = true
                        val exactTimeValue = when (specialPointType) {
                            PointTooltipMarker.PointType.SPEED_100 -> attempt.time0to100 / 1_000_000_000.0f
                            PointTooltipMarker.PointType.SPEED_200 -> {
                                if (measurementMode == MeasurementMode.HUNDRED_TO_200) {
                                    attempt.time100to200 / 1_000_000_000.0f
                                } else {
                                    attempt.time0to200 / 1_000_000_000.0f
                                }
                            }
                            PointTooltipMarker.PointType.DISTANCE_402 -> attempt.time0to402 / 1_000_000_000.0f
                            null -> finalEntry.x // За нормални точки използваме координатата
                        }
                        exactTimeField.set(markerView, exactTimeValue)

                        val modeField = markerView.javaClass.getDeclaredField("mode")
                        modeField.isAccessible = true
                        modeField.set(markerView, activeMode)

                        val attemptField = markerView.javaClass.getDeclaredField("attempt")
                        attemptField.isAccessible = true
                        attemptField.set(markerView, attempt)
                    } catch (ex: Exception) {
                        // Reflection failed
                    }
                    
                    // СЛЕД като pointType е зададен правилно, извикваме refreshContent
                    markerView.refreshContent(finalEntry, h)
                }

                // Принудително рисуваме highlight на финалната точка
                // Използваме същия dataSetIndex който вече определихме по-горе
                val finalDataSetIndex = h.dataSetIndex.coerceIn(0, (chart.data?.dataSets?.size ?: 1) - 1)
                val highlight = com.github.mikephil.charting.highlight.Highlight(finalEntry.x, finalEntry.y, finalDataSetIndex)
                chart.highlightValue(null, false)
                chart.highlightValue(highlight, false)
                chart.invalidate()
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
                // SNAPPING логика при цъкване - използваме същата функция като при drag за консистентност
                val activeAttempt = getChartAttempt(chart)
                val activeMode = getChartMode(chart)
                if (me != null && activeAttempt != null) {
                    val touchPoint = chart.getValuesByTouchPoint(me.x, me.y, com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT)
                    if (touchPoint != null && touchPoint.x >= 0f) {
                        val attempt = activeAttempt
                        val snapThreshold = 0.4f // Радиус за snapping (0.4 секунди)
                        
                        // Използваме същата помощна функция като при drag за консистентност
                        // КРИТИЧНО: Конвертираме Double към Float (touchPoint връща Double, но функцията очаква Float)
                        val closestSpecialPoint = findClosestSpecialPoint(
                            touchPoint.x.toFloat(),
                            touchPoint.y.toFloat(),
                            attempt,
                            activeMode,
                            snapThreshold,
                            chart.context
                        )
                        
                        if (closestSpecialPoint != null) {
                            val exactTime = when (closestSpecialPoint.type) {
                                PointTooltipMarker.PointType.SPEED_100 -> attempt.time0to100 / 1_000_000_000.0f
                                PointTooltipMarker.PointType.SPEED_200 -> {
                                    if (measurementMode == MeasurementMode.HUNDRED_TO_200) {
                                        attempt.time100to200 / 1_000_000_000.0f
                                    } else {
                                        attempt.time0to200 / 1_000_000_000.0f
                                    }
                                }
                                PointTooltipMarker.PointType.DISTANCE_402 -> attempt.time0to402 / 1_000_000_000.0f
                            }
                            val entry = com.github.mikephil.charting.data.Entry(closestSpecialPoint.x, closestSpecialPoint.y)
                            showMarkerAtPoint(chart, entry, closestSpecialPoint.type, exactTime)
                        }
                    }
                }
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
        setChartContext(holder.chart, attempt, mode)
        
        // Обновяваме стила на бутоните
        updateButtonStyles(holder, mode)
        
        // КРИТИЧНО: Първо обновяваме данните на графиката (създава маркера)
        updateChartData(holder, attempt, mode)
        
        // СЛЕД това настройваме listener-ите (сега маркерът вече е създаден)
        setupChartZoom(holder.chart)
    }
    
    private fun updateButtonStyles(holder: AttemptViewHolder, mode: ChartMode) {
        val context = holder.itemView.context
        
        // Reset всички бутони
        holder.btnSpeed.setBackgroundResource(R.drawable.button_toggle_unselected)
        holder.btnSpeed.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        
        holder.btnAcceleration.setBackgroundResource(R.drawable.button_toggle_unselected)
        holder.btnAcceleration.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        
        holder.btnGForce.setBackgroundResource(R.drawable.button_toggle_unselected)
        holder.btnGForce.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        
        // Задай активния бутон
        when (mode) {
            ChartMode.SPEED -> {
                // Create drawable programmatically to avoid caching issues - FORCE ORANGE
                val orangeColorInt = 0xFFFF6020.toInt() // Hardcoded orange #FF6020
                val density = context.resources.displayMetrics.density
                val orangeDrawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setColor(orangeColorInt)
                    cornerRadius = 8f * density
                    setStroke((1 * density).toInt(), orangeColorInt)
                }
                // Clear any tint that might override the color
                holder.btnSpeed.backgroundTintList = null
                holder.btnSpeed.background = null // Clear first
                holder.btnSpeed.background = orangeDrawable
                holder.btnSpeed.setTextColor(android.graphics.Color.WHITE)
                holder.btnSpeed.post {
                    holder.btnSpeed.invalidate()
                    holder.btnSpeed.requestLayout()
                }
            }
            ChartMode.ACCELERATION -> {
                // Create drawable programmatically with #3486A9 color
                val accelerationColorInt = 0xFF3486A9.toInt() // #3486A9
                val density = context.resources.displayMetrics.density
                val accelerationDrawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setColor(accelerationColorInt)
                    cornerRadius = 8f * density
                    setStroke((1 * density).toInt(), accelerationColorInt)
                }
                holder.btnAcceleration.backgroundTintList = null
                holder.btnAcceleration.background = null
                holder.btnAcceleration.background = accelerationDrawable
                holder.btnAcceleration.setTextColor(android.graphics.Color.WHITE)
                holder.btnAcceleration.post {
                    holder.btnAcceleration.invalidate()
                    holder.btnAcceleration.requestLayout()
                }
            }
            ChartMode.G_FORCE -> {
                // Create drawable programmatically with #E68894 color
                val gForceColorInt = 0xFFE68894.toInt() // #E68894
                val density = context.resources.displayMetrics.density
                val gForceDrawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setColor(gForceColorInt)
                    cornerRadius = 8f * density
                    setStroke((1 * density).toInt(), gForceColorInt)
                }
                holder.btnGForce.backgroundTintList = null
                holder.btnGForce.background = null
                holder.btnGForce.background = gForceDrawable
                holder.btnGForce.setTextColor(android.graphics.Color.WHITE)
                holder.btnGForce.post {
                    holder.btnGForce.invalidate()
                    holder.btnGForce.requestLayout()
                }
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
        applyChartXPadding(holder.chart, maxTimeFromAllMeasurements)
        holder.chart.invalidate()
    }

    private fun applyChartXPadding(chart: com.github.mikephil.charting.charts.LineChart, baseMaxX: Float) {
        val safeBaseMax = if (baseMaxX > 0f) baseMaxX else 1f
        val rightPadding = maxOf(0.5f, safeBaseMax * 0.08f)
        chart.xAxis.axisMinimum = 0f
        chart.xAxis.axisMaximum = safeBaseMax + rightPadding
        chart.setVisibleXRangeMaximum(safeBaseMax + rightPadding)
        chart.moveViewToX(0f)
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
                // КРИТИЧНО: За всички режими използваме абсолютно време (в секунди), не нормализирано
                // Това гарантира, че маркерите и графиката използват една и съща координатна система
                // и tooltip-ът съвпада с X позицията на маркера
                for (i in speedSamples.indices) {
                    val currentSpeed = speedSamples[i]
                    val absoluteTimeInSeconds = timestamps[i] / 1_000_000_000.0f
                    val convertedSpeed = UnitsManager.convertSpeed(currentSpeed, speedUnit)
                    entries.add(com.github.mikephil.charting.data.Entry(absoluteTimeInSeconds, convertedSpeed))
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
                val baseColor = ContextCompat.getColor(holder.itemView.context, R.color.primary_color)
                color = if (isActive) baseColor else android.graphics.Color.argb(77, android.graphics.Color.red(baseColor), android.graphics.Color.green(baseColor), android.graphics.Color.blue(baseColor))
                lineWidth = if (isActive) 2f else 1f
                setDrawValues(false)
                setDrawCircles(false)
                // КРИТИЧНО: Забраняваме highlighting за неактивни линии - може да се плъзга само по активната
                isHighlightEnabled = isActive
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
            
            // КРИТИЧНО: Използваме абсолютно време (в секунди), не нормализирано
            // Това гарантира, че маркерите и графиката използват една и съща координатна система
            for (i in accelSamples.indices) {
                val absoluteTimeInSeconds = timestamps[i] / 1_000_000_000.0f
                entries.add(com.github.mikephil.charting.data.Entry(absoluteTimeInSeconds, accelSamples[i]))
            }
            
            val dataSet = com.github.mikephil.charting.data.LineDataSet(entries, context.getString(R.string.drag_tab_acceleration)).apply {
                val baseColor = 0xFF3486A9.toInt() // #3486A9
                color = if (isActive) baseColor else android.graphics.Color.argb(77, android.graphics.Color.red(baseColor), android.graphics.Color.green(baseColor), android.graphics.Color.blue(baseColor))
                lineWidth = if (isActive) 2f else 1f
                setDrawValues(false)
                setDrawCircles(false)
                // КРИТИЧНО: Забраняваме highlighting за неактивни линии - може да се плъзга само по активната
                isHighlightEnabled = isActive
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
            
            // КРИТИЧНО: Използваме абсолютно време (в секунди), не нормализирано
            // Това гарантира, че маркерите и графиката използват една и съща координатна система
            for (i in gSamples.indices) {
                val absoluteTimeInSeconds = timestamps[i] / 1_000_000_000.0f
                entries.add(com.github.mikephil.charting.data.Entry(absoluteTimeInSeconds, gSamples[i]))
            }
            
            val dataSet = com.github.mikephil.charting.data.LineDataSet(entries, context.getString(R.string.drag_tab_gforce)).apply {
                val baseColor = 0xFFE68894.toInt() // #E68894
                color = if (isActive) baseColor else android.graphics.Color.argb(77, android.graphics.Color.red(baseColor), android.graphics.Color.green(baseColor), android.graphics.Color.blue(baseColor))
                lineWidth = if (isActive) 2f else 1f
                setDrawValues(false)
                setDrawCircles(false)
                // КРИТИЧНО: Забраняваме highlighting за неактивни линии - може да се плъзга само по активната
                isHighlightEnabled = isActive
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
                    applyChartXPadding(holder.chart, maxNormalizedTime * 1.1f)
                } else {
                    // За останалите режими използваме реалните времена
                    val maxTimeFromAllMeasurements = getMaxTimeFromAllMeasurements(attempt).toFloat()
                    applyChartXPadding(holder.chart, maxTimeFromAllMeasurements)
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
            val maxAccel = accelSamples.maxOrNull() ?: 0f
            holder.tvChartStats.text = context.getString(R.string.drag_chart_max_accel, maxAccel)
            
            // Данните вече са добавени от addAccelerationLine
            
            // КРИТИЧНО: Настройваме X-оста да използва абсолютни времена (в секунди)
            // Това гарантира, че X-оста съвпада с абсолютните времена от маркерите и tooltip-а
            val maxTime = if (timestamps.isNotEmpty()) {
                timestamps.maxOrNull()!! / 1_000_000_000.0f
            } else {
                getMaxTimeFromAllMeasurements(attempt).toFloat()
            }
            
            applyChartXPadding(holder.chart, maxTime)
            
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
            
            // X-оста вече е настроена по-горе с нормализирани времена
            
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
            val maxG = gSamples.maxOrNull() ?: 0f
            val minG = gSamples.minOrNull() ?: 0f
            
            holder.tvChartStats.text = context.getString(R.string.drag_chart_peak_g, maxG)
            
            // Данните вече са добавени от addGForceLine
            
            // КРИТИЧНО: Настройваме X-оста да използва абсолютни времена (в секунди)
            // Това гарантира, че X-оста съвпада с абсолютните времена от маркерите и tooltip-а
            val maxTime = if (timestamps.isNotEmpty()) {
                timestamps.maxOrNull()!! / 1_000_000_000.0f
            } else {
                getMaxTimeFromAllMeasurements(attempt).toFloat()
            }
            
            applyChartXPadding(holder.chart, maxTime)
            
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
            
            // X-оста вече е настроена по-горе с нормализирани времена
            
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
                // КРИТИЧНО: Използваме абсолютно време (в секунди), не нормализирано
                // Това гарантира, че маркерът е на правилната X позиция спрямо tooltip-а
                val startTime = rawTimes.first()
                val time100Absolute = attempt.time0to100 / 1_000_000_000.0f
                val time100Normalized = (attempt.time0to100 - startTime) / 1_000_000_000.0f
                val speedUnit = UnitsManager.getSpeedUnit(holder.itemView.context)
                val valueAt100 = when (mode) {
                    ChartMode.SPEED -> UnitsManager.convertSpeed(100f, speedUnit)
                    ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, time100Normalized, mode)
                    ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, time100Normalized, mode)
                }
                val entry100 = com.github.mikephil.charting.data.Entry(time100Absolute, valueAt100)
                val dataSet100 = com.github.mikephil.charting.data.LineDataSet(listOf(entry100), "").apply {
                    setDrawCircles(true)
                    setDrawValues(false)
                    lineWidth = 0f
                    circleRadius = 8f
                    circleHoleRadius = 4f
                    isHighlightEnabled = false
                    setCircleColor(ContextCompat.getColor(context, R.color.accent_green)) // 100 km/h - зелена
                }
                existingData.addDataSet(dataSet100)
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
                // КРИТИЧНО: Използваме абсолютно време (в секунди), не нормализирано
                // Това гарантира, че маркерът е на правилната X позиция спрямо tooltip-а
                val startTime = rawTimes.first()
                val time200Absolute = if (measurementMode == MeasurementMode.HUNDRED_TO_200) {
                    // За 100-200: time100to200 е продължителността, трябва да добавим startTime
                    val absoluteTime200 = startTime + attempt.time100to200
                    absoluteTime200 / 1_000_000_000.0f
                } else {
                    // За други режими: използваме абсолютното време от attempt.time0to200
                    attempt.time0to200 / 1_000_000_000.0f
                }
                
                val speedUnit = UnitsManager.getSpeedUnit(holder.itemView.context)
                val valueAt200 = when (mode) {
                    ChartMode.SPEED -> UnitsManager.convertSpeed(200f, speedUnit)
                    ChartMode.ACCELERATION -> {
                        // За acceleration трябва да нормализираме времето за findValueAtTimeInterpolated
                        val normalizedTime = if (measurementMode == MeasurementMode.HUNDRED_TO_200) {
                            attempt.time100to200 / 1_000_000_000.0f
                        } else {
                            (attempt.time0to200 - startTime) / 1_000_000_000.0f
                        }
                        findValueAtTimeInterpolated(attempt, normalizedTime, mode)
                    }
                    ChartMode.G_FORCE -> {
                        // За G-Force трябва да нормализираме времето за findValueAtTimeInterpolated
                        val normalizedTime = if (measurementMode == MeasurementMode.HUNDRED_TO_200) {
                            attempt.time100to200 / 1_000_000_000.0f
                        } else {
                            (attempt.time0to200 - startTime) / 1_000_000_000.0f
                        }
                        findValueAtTimeInterpolated(attempt, normalizedTime, mode)
                    }
                }
                
                val entry200 = com.github.mikephil.charting.data.Entry(time200Absolute, valueAt200)
                val dataSet200 = com.github.mikephil.charting.data.LineDataSet(listOf(entry200), "").apply {
                    setDrawCircles(true)
                    setDrawValues(false)
                    lineWidth = 0f
                    circleRadius = 8f
                    circleHoleRadius = 4f
                    isHighlightEnabled = false
                    setCircleColor(ContextCompat.getColor(context, R.color.accent_blue)) // 200 km/h - синя
                }
                existingData.addDataSet(dataSet200)
            }

            if (attempt.time0to402 > 0) {
                // КРИТИЧНО: Използваме абсолютно време (в секунди), не нормализирано
                // Това гарантира, че маркерът е на правилната X позиция спрямо tooltip-а
                val time402Absolute = attempt.time0to402 / 1_000_000_000.0f
                val startTime = rawTimes.first()
                val time402Normalized = (attempt.time0to402 - startTime) / 1_000_000_000.0f
                val valueAt402 = findValueAtTimeInterpolated(attempt, time402Normalized, mode)
                val entry402 = com.github.mikephil.charting.data.Entry(time402Absolute, valueAt402)
                val dataSet402 = com.github.mikephil.charting.data.LineDataSet(listOf(entry402), "").apply {
                    setDrawCircles(true)
                    setDrawValues(false)
                    lineWidth = 0f
                    circleRadius = 8f
                    circleHoleRadius = 4f
                    isHighlightEnabled = false
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
            private var exactTime: Float = 0f // КРИТИЧНО: Точното време от attempt (за да съвпада с Best Times)
            var shouldShow: Boolean = false // КРИТИЧНО: Флаг за контрол на показването на бъбъла
            
            override fun refreshContent(e: Entry?, highlight: Highlight?) {
                currentEntry = e
                if (e != null) {
                    // КРИТИЧНО: НЕ презаписваме pointType и isOnSpecialPoint тук!
                    // Те се задават правилно чрез reflection в onValueSelected
                    // Ако ги презапишем тук, ще загубим правилната стойност зададена от snapping логиката
                    // actualValue може да се обнови, но pointType и isOnSpecialPoint остават както са зададени
                    actualValue = e.y
                }
                super.refreshContent(e, highlight)
            }
            
            override fun draw(canvas: Canvas, posX: Float, posY: Float) {
                if (currentEntry == null || !shouldShow) return
                
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                
                // Определяме текста и цвета
                val (text, backgroundColor) = if (isOnSpecialPoint) {
                    // КРИТИЧНО: Използваме точното време от attempt, не координатата на точката (за да съвпада с Best Times)
                    val timeToShow = if (exactTime > 0f) exactTime else (currentEntry?.x ?: 0f)
                    
                    // За Speed режим показваме типа (0-100, 0-200, 0-402)
                    // За Acceleration и G-Force показваме реалната стойност в този момент
                    val typeText = when (mode) {
                        ChartMode.SPEED -> {
                            // Speed режим - показваме типа
                            when (pointType) {
                                PointTooltipMarker.PointType.SPEED_100 -> {
                                    "0-100 km/h\n${String.format("%.3f", timeToShow)}s"
                                }
                                PointTooltipMarker.PointType.SPEED_200 -> {
                                    "0-200 km/h\n${String.format("%.3f", timeToShow)}s"
                                }
                                PointTooltipMarker.PointType.DISTANCE_402 -> {
                                    val speedAt402 = getSpeedAtTime(attempt, timeToShow)
                                    val speedUnit = UnitsManager.getSpeedUnit(context)
                                    val convertedSpeed = UnitsManager.convertSpeed(speedAt402, speedUnit)
                                    "0-402m\n${convertedSpeed.toInt()} ${speedUnit.symbol}\n${String.format("%.3f", timeToShow)}s"
                                }
                            }
                        }
                        ChartMode.ACCELERATION -> {
                            // Acceleration режим - показваме реалната стойност в m/s²
                            "${String.format("%.1f", actualValue)} m/s²\n${String.format("%.3f", timeToShow)}s"
                        }
                        ChartMode.G_FORCE -> {
                            // G-Force режим - показваме реалната стойност в G
                            "${String.format("%.2f", actualValue)} G\n${String.format("%.3f", timeToShow)}s"
                        }
                    }
                    // КРИТИЧНО: Използваме pointType зададен чрез reflection от onValueSelected
                    // Той вече е правилно зададен от snapping логиката
                    val bgColor = when (pointType) {
                        PointTooltipMarker.PointType.SPEED_100 -> ContextCompat.getColor(context, R.color.accent_green)
                        PointTooltipMarker.PointType.SPEED_200 -> ContextCompat.getColor(context, R.color.accent_blue)
                        PointTooltipMarker.PointType.DISTANCE_402 -> ContextCompat.getColor(context, R.color.accent_red)
                        // Fallback ако pointType не е зададен (не би трябвало да се случи)
                        else -> ContextCompat.getColor(context, R.color.accent_red)
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
                            text to 0xFFFF6020.toInt() // Orange #FF6020
                        }
                        ChartMode.ACCELERATION -> {
                            val text = "${String.format("%.1f", actualValue)} m/s²\n${String.format("%.3f", timeAtPoint)}s"
                            text to 0xFF3486A9.toInt() // #3486A9
                        }
                        ChartMode.G_FORCE -> {
                            val text = "${String.format("%.2f", actualValue)} G\n${String.format("%.3f", timeAtPoint)}s"
                            text to 0xFFE68894.toInt() // #E68894
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
                val balloonY = posY - rectHeight - 12f // по-малко отстояние, стрелката ще докосва кръгчето почти
                
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
                return MPPointF(0f, 0f)
            }
        }
        
        holder.chart.marker = smartMarker
        holder.chart.setTag(R.id.tag_drag_chart_marker, smartMarker)
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
        if (speeds.isEmpty() || timestamps.isEmpty()) return null
        
        // КРИТИЧНО: Използваме абсолютно време (в секунди), не нормализирано
        // Това гарантира, че резултатът съвпада с абсолютните времена от графиката и маркерите
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
    // КРИТИЧНО: targetTimeSeconds е нормализирано време (relative to first timestamp)
    // Това гарантира съвместимост с графиката и всички други функции
    private fun interpolateValueAtTime(values: List<Float>, timestamps: List<Long>, targetTimeSeconds: Float): Float {
        if (values.isEmpty() || timestamps.isEmpty()) return 0f

        // КРИТИЧНО: Използваме нормализирано време спрямо start timestamp
        val normalizedTimes = DragAttemptsAdapter.normalizeTime(timestamps)
        val targetTimeNormalized = targetTimeSeconds

        // Намираме двете съседни точки в нормализираното пространство
        for (i in 1 until normalizedTimes.size) {
            val t0 = normalizedTimes[i - 1]
            val t1 = normalizedTimes[i]

            if (targetTimeNormalized >= t0 && targetTimeNormalized <= t1) {
                val v0 = values[i - 1]
                val v1 = values[i]

                // Линейна интерполация
                val ratio = (targetTimeNormalized - t0) / (t1 - t0)
                return v0 + (v1 - v0) * ratio
            }
        }

        // Ако времето е извън диапазона, връщаме последната/първата стойност
        return when {
            targetTimeNormalized < normalizedTimes.first() -> values.first()
            targetTimeNormalized > normalizedTimes.last() -> values.last()
            else -> values.lastOrNull() ?: 0f
        }
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
                allTimestamps.maxOrNull()!! / 1_000_000_000.0 // Конвертирай от nanoseconds в секунди
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