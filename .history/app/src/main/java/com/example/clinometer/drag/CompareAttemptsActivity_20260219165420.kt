package com.example.clinometer.drag

import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.R
import com.example.clinometer.settings.LanguageManager
import com.example.clinometer.settings.UnitsManager
import com.example.clinometer.DragSession
import com.example.clinometer.DragAttempt
import com.example.clinometer.DragStorage
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.SimpleDateFormat
import java.util.*

class CompareAttemptsActivity : AppCompatActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }
    
    private lateinit var chart: LineChart
    private lateinit var tvChartTitle: TextView
    private lateinit var btnSpeed: Button
    private lateinit var btnAcceleration: Button
    private lateinit var btnGForce: Button
    private lateinit var llChartMode: LinearLayout
    
    // Comparison stats views
    private lateinit var tvCurrentMax: TextView
    private lateinit var tvCurrent0to100: TextView
    private lateinit var tvCurrent0to200: TextView
    private lateinit var tvCurrent100to200: TextView
    private lateinit var tvCurrent0to402: TextView
    private lateinit var tvCompareMax: TextView
    private lateinit var tvCompare0to100: TextView
    private lateinit var tvCompare0to200: TextView
    private lateinit var tvCompare100to200: TextView
    private lateinit var tvCompare0to402: TextView
    
    private var currentSessionId: Long = -1
    private var currentAttemptId: Long = -1
    private var compareSessionId: Long = -1
    private var compareAttemptId: Long = -1
    
    private var currentAttempt: DragAttempt? = null
    private var compareAttempt: DragAttempt? = null
    private var currentSession: DragSession? = null
    private var compareSession: DragSession? = null
    
    enum class ChartMode {
        SPEED, ACCELERATION, G_FORCE
    }
    
    enum class PointType {
        SPEED_100, SPEED_200, DISTANCE_402
    }
    
    private var currentMode = ChartMode.SPEED
    private lateinit var smartMarker: SmartMarker
    
    // Data class за специални точки (използван в snapping логиката)
    private data class SpecialPoint(
        val x: Float,
        val y: Float,
        val type: PointType,
        val isCurrent: Boolean,
        val exactTime: Float
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compare_attempts)
        
        currentSessionId = intent.getLongExtra("current_session_id", -1)
        currentAttemptId = intent.getLongExtra("current_attempt_id", -1)
        compareSessionId = intent.getLongExtra("compare_session_id", -1)
        compareAttemptId = intent.getLongExtra("compare_attempt_id", -1)
        
        setupViews()
        // loadData() се извиква в setupViews() след настройката на графиката
    }
    
    private fun setupViews() {
        chart = findViewById(R.id.chart)
        tvChartTitle = findViewById(R.id.tvChartTitle)
        btnSpeed = findViewById(R.id.btnSpeed)
        btnAcceleration = findViewById(R.id.btnAcceleration)
        btnGForce = findViewById(R.id.btnGForce)
        llChartMode = findViewById(R.id.llChartMode)
        
        // Comparison stats views
        tvCurrentMax = findViewById(R.id.tvCurrentMax)
        tvCurrent0to100 = findViewById(R.id.tvCurrent0to100)
        tvCurrent0to200 = findViewById(R.id.tvCurrent0to200)
        tvCurrent100to200 = findViewById(R.id.tvCurrent100to200)
        tvCurrent0to402 = findViewById(R.id.tvCurrent0to402)
        tvCompareMax = findViewById(R.id.tvCompareMax)
        tvCompare0to100 = findViewById(R.id.tvCompare0to100)
        tvCompare0to200 = findViewById(R.id.tvCompare0to200)
        tvCompare100to200 = findViewById(R.id.tvCompare100to200)
        tvCompare0to402 = findViewById(R.id.tvCompare0to402)
        
        setupChart()
        setupChartModeButtons()
        // КРИТИЧНО: Първо зареждаме данните, после настройваме listener-ите
        loadData()
        // СЛЕД това настройваме listener-ите (сега маркерът и данните са готови)
        setupChartZoom(chart)
        
        findViewById<View>(R.id.btnBack)?.setOnClickListener {
            finish()
        }
    }
    
    private fun setupChart() {
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.setDoubleTapToZoomEnabled(true)
        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false
        chart.setBackgroundColor(ContextCompat.getColor(this, R.color.background_primary))
        chart.setNoDataText("No data available")
        chart.setNoDataTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        
        // Настройваме X оста - ТОЧНО като в DragSessionDetailsActivity
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 0.1f
        xAxis.setDrawGridLines(true)
        xAxis.gridColor = ContextCompat.getColor(this, R.color.grid_line)
        xAxis.textColor = android.graphics.Color.WHITE
        xAxis.textSize = 12f
        xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(x: Float): String {
                // Показваме секунди с 1 десетичен знак за по-голяма точност
                val result = String.format("%.1fs", x)
                return result
            }
        }
        xAxis.axisMinimum = 0f
        xAxis.axisMaximum = 10f // Временно, ще се обнови при зареждане на данни
        
        // Настройваме Y оста - ТОЧНО като в DragSessionDetailsActivity
        val yAxis = chart.axisLeft
        yAxis.setDrawGridLines(true)
        yAxis.gridColor = ContextCompat.getColor(this, R.color.grid_line)
        yAxis.textColor = android.graphics.Color.WHITE
        yAxis.textSize = 12f
        
        // Скриваме дясната Y ос
        chart.axisRight.isEnabled = false
        
        // Допълнителни настройки като в DragSessionDetailsActivity
        chart.legend.isEnabled = false  // Махаме легендата
        chart.isDragDecelerationEnabled = false
        chart.dragDecelerationFrictionCoef = 0f
        
        // Настройваме легендата
        val legend = chart.legend
        legend.isEnabled = true
        legend.textColor = ContextCompat.getColor(this, R.color.text_primary)
        legend.textSize = 12f
        
        // Добавяме зум функционалност
        setupChartZoom(chart)
        
        // Настройваме smart marker за балончета
        setupSmartMarker()
        
        // Listener-ите се настройват в setupChartZoom СЛЕД зареждане на данните
    }
    
    private fun setupChartModeButtons() {
        btnSpeed.setOnClickListener { updateChartMode(ChartMode.SPEED) }
        btnAcceleration.setOnClickListener { updateChartMode(ChartMode.ACCELERATION) }
        btnGForce.setOnClickListener { updateChartMode(ChartMode.G_FORCE) }
        
        updateChartMode(ChartMode.SPEED)
    }
    
    private fun updateChartMode(mode: ChartMode) {
        currentMode = mode
        
        // Обновяваме бутоните - използваме същите цветове като в DragSessionDetailsActivity
        val density = resources.displayMetrics.density
        
        // Reset всички бутони
        btnSpeed.setBackgroundResource(R.drawable.button_toggle_unselected)
        btnSpeed.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        btnAcceleration.setBackgroundResource(R.drawable.button_toggle_unselected)
        btnAcceleration.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        btnGForce.setBackgroundResource(R.drawable.button_toggle_unselected)
        btnGForce.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        
        // Задай активния бутон
        when (mode) {
            ChartMode.SPEED -> {
                // Create drawable programmatically to avoid caching issues - FORCE ORANGE
                val orangeColorInt = 0xFFFF6020.toInt() // Hardcoded orange #FF6020
                val orangeDrawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setColor(orangeColorInt)
                    cornerRadius = 8f * density
                    setStroke((1 * density).toInt(), orangeColorInt)
                }
                // Clear any tint that might override the color
                btnSpeed.backgroundTintList = null
                btnSpeed.background = null // Clear first
                btnSpeed.background = orangeDrawable
                btnSpeed.setTextColor(android.graphics.Color.WHITE)
                btnSpeed.post {
                    btnSpeed.invalidate()
                    btnSpeed.requestLayout()
                }
            }
            ChartMode.ACCELERATION -> {
                // Create drawable programmatically with #3486A9 color
                val accelerationColorInt = 0xFF3486A9.toInt() // #3486A9
                val accelerationDrawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setColor(accelerationColorInt)
                    cornerRadius = 8f * density
                    setStroke((1 * density).toInt(), accelerationColorInt)
                }
                btnAcceleration.backgroundTintList = null
                btnAcceleration.background = null
                btnAcceleration.background = accelerationDrawable
                btnAcceleration.setTextColor(android.graphics.Color.WHITE)
                btnAcceleration.post {
                    btnAcceleration.invalidate()
                    btnAcceleration.requestLayout()
                }
            }
            ChartMode.G_FORCE -> {
                // Create drawable programmatically with #E68894 color
                val gForceColorInt = 0xFFE68894.toInt() // #E68894
                val gForceDrawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setColor(gForceColorInt)
                    cornerRadius = 8f * density
                    setStroke((1 * density).toInt(), gForceColorInt)
                }
                btnGForce.backgroundTintList = null
                btnGForce.background = null
                btnGForce.background = gForceDrawable
                btnGForce.setTextColor(android.graphics.Color.WHITE)
                btnGForce.post {
                    btnGForce.invalidate()
                    btnGForce.requestLayout()
                }
            }
        }
        
        // Обновяваме маркера
        updateSmartMarker()
        
        // Обновяваме графиката и статистиките
        updateChart()
        updateComparisonStats()
    }
    
    private fun loadData() {
        // Зареждаме текущата сесия и избрания current опит
        currentSession = DragStorage.getDragSession(this, currentSessionId)
        currentAttempt = currentSession?.attempts?.find { it.id == currentAttemptId }
            ?: currentSession?.attempts?.firstOrNull()
        
        // Зареждаме опита за сравняване
        compareSession = DragStorage.getDragSession(this, compareSessionId)
        compareAttempt = compareSession?.attempts?.find { it.id == compareAttemptId }
        
        // Debug логове
        println("🔍 Current session: ${currentSession?.name}, attempts: ${currentSession?.attempts?.size}")
        println("🔍 Current attempt: ${currentAttempt?.id}, speedSamples: ${currentAttempt?.speedSamples?.size}")
        println("🔍 Compare session: ${compareSession?.name}, attempts: ${compareSession?.attempts?.size}")
        println("🔍 Compare attempt: ${compareAttempt?.id}, speedSamples: ${compareAttempt?.speedSamples?.size}")
        
        // КРИТИЧНО: Първо зареждаме данните на графиката (създава маркера)
        updateChart()
        updateSessionInfo()
        updateComparisonStats()
        updateSmartMarker()
    }
    
    private fun updateSessionInfo() {
        val currentSessionName = currentSession?.name ?: "Current Session"
        val compareSessionName = compareSession?.name ?: "Compare Session"
        
        findViewById<TextView>(R.id.tvCurrentSession).text = currentSessionName
        findViewById<TextView>(R.id.tvCompareSession).text = compareSessionName
    }
    
    private fun updateComparisonStats() {
        if (currentAttempt == null || compareAttempt == null) return
        
        when (currentMode) {
            ChartMode.SPEED -> updateSpeedStats()
            ChartMode.ACCELERATION -> updateAccelerationStats()
            ChartMode.G_FORCE -> updateGForceStats()
        }
    }
    
    private fun updateSpeedStats() {
        val speedUnit = UnitsManager.getSpeedUnit(this)
        
        // Current attempt stats - използваме реалните данни
        val (currentSpeeds, _) = getAlignedSpeedData(currentAttempt!!)
        val currentMaxSpeed = currentSpeeds.maxOrNull() ?: 0f
        val currentMaxSpeedConverted = UnitsManager.convertSpeed(currentMaxSpeed, speedUnit)
        val currentTime0to100 = if (currentAttempt!!.time0to100 > 0) currentAttempt!!.time0to100 / 1_000_000_000.0 else 0.0
        val currentTime0to200 = if (currentAttempt!!.time0to200 > 0) currentAttempt!!.time0to200 / 1_000_000_000.0 else 0.0
        val currentTime100to200 = if (currentTime0to200 > 0 && currentTime0to100 > 0) currentTime0to200 - currentTime0to100 else 0.0
        val currentTime0to402 = if (currentAttempt!!.time0to402 > 0) currentAttempt!!.time0to402 / 1_000_000_000.0 else 0.0
        
        // Compare attempt stats - използваме реалните данни
        val (compareSpeeds, _) = getAlignedSpeedData(compareAttempt!!)
        val compareMaxSpeed = compareSpeeds.maxOrNull() ?: 0f
        val compareMaxSpeedConverted = UnitsManager.convertSpeed(compareMaxSpeed, speedUnit)
        val compareTime0to100 = if (compareAttempt!!.time0to100 > 0) compareAttempt!!.time0to100 / 1_000_000_000.0 else 0.0
        val compareTime0to200 = if (compareAttempt!!.time0to200 > 0) compareAttempt!!.time0to200 / 1_000_000_000.0 else 0.0
        val compareTime100to200 = if (compareTime0to200 > 0 && compareTime0to100 > 0) compareTime0to200 - compareTime0to100 else 0.0
        val compareTime0to402 = if (compareAttempt!!.time0to402 > 0) compareAttempt!!.time0to402 / 1_000_000_000.0 else 0.0
        
        // Update UI
        val currentColor = 0xFFFF6020.toInt() // #FF6020 - оранжев като бутона Speed
        val compareColor = 0xFFA64CEB.toInt() // #A64CEB - лилав винаги за Compare
        
        tvCurrentMax.text = "Max: ${currentMaxSpeedConverted.toInt()} ${speedUnit.symbol}"
        tvCurrentMax.setTextColor(currentColor)
        tvCurrent0to100.text = "0-100: ${String.format("%.3f", currentTime0to100)}s"
        tvCurrent0to100.setTextColor(android.graphics.Color.WHITE)
        tvCurrent0to200.text = "0-200: ${String.format("%.3f", currentTime0to200)}s"
        tvCurrent0to200.setTextColor(android.graphics.Color.WHITE)
        tvCurrent100to200.text = "100-200: ${String.format("%.3f", currentTime100to200)}s"
        tvCurrent100to200.setTextColor(android.graphics.Color.WHITE)
        tvCurrent0to402.text = "0-402m: ${String.format("%.3f", currentTime0to402)}s"
        tvCurrent0to402.setTextColor(android.graphics.Color.WHITE)
        
        tvCompareMax.text = "Max: ${compareMaxSpeedConverted.toInt()} ${speedUnit.symbol}"
        tvCompareMax.setTextColor(compareColor)
        tvCompare0to100.text = "0-100: ${String.format("%.3f", compareTime0to100)}s"
        tvCompare0to100.setTextColor(android.graphics.Color.WHITE)
        tvCompare0to200.text = "0-200: ${String.format("%.3f", compareTime0to200)}s"
        tvCompare0to200.setTextColor(android.graphics.Color.WHITE)
        tvCompare100to200.text = "100-200: ${String.format("%.3f", compareTime100to200)}s"
        tvCompare100to200.setTextColor(android.graphics.Color.WHITE)
        tvCompare0to402.text = "0-402m: ${String.format("%.3f", compareTime0to402)}s"
        tvCompare0to402.setTextColor(android.graphics.Color.WHITE)
    }
    
    private fun updateAccelerationStats() {
        // Използваме реалните данни
        val (currentAccels, _) = getAlignedAccelData(currentAttempt!!)
        val (compareAccels, _) = getAlignedAccelData(compareAttempt!!)
        
        val currentMaxAccel = currentAccels.maxOrNull() ?: 0f
        val compareMaxAccel = compareAccels.maxOrNull() ?: 0f
        
        // Намираме ускорението на ключовите точки
        val currentAccelAt100 = findValueAtTimeInterpolated(currentAttempt!!, currentAttempt!!.time0to100 / 1_000_000_000.0f, ChartMode.ACCELERATION)
        val currentAccelAt200 = findValueAtTimeInterpolated(currentAttempt!!, currentAttempt!!.time0to200 / 1_000_000_000.0f, ChartMode.ACCELERATION)
        val currentAccelAt402 = findValueAtTimeInterpolated(currentAttempt!!, currentAttempt!!.time0to402 / 1_000_000_000.0f, ChartMode.ACCELERATION)
        
        val compareAccelAt100 = findValueAtTimeInterpolated(compareAttempt!!, compareAttempt!!.time0to100 / 1_000_000_000.0f, ChartMode.ACCELERATION)
        val compareAccelAt200 = findValueAtTimeInterpolated(compareAttempt!!, compareAttempt!!.time0to200 / 1_000_000_000.0f, ChartMode.ACCELERATION)
        val compareAccelAt402 = findValueAtTimeInterpolated(compareAttempt!!, compareAttempt!!.time0to402 / 1_000_000_000.0f, ChartMode.ACCELERATION)
        
        // Намираме максималното ускорение в интервала 100-200 km/h
        val currentMaxAccel100to200 = findMaxValueInRange(currentAttempt!!, currentAttempt!!.time0to100 / 1_000_000_000.0f, currentAttempt!!.time0to200 / 1_000_000_000.0f, ChartMode.ACCELERATION)
        val compareMaxAccel100to200 = findMaxValueInRange(compareAttempt!!, compareAttempt!!.time0to100 / 1_000_000_000.0f, compareAttempt!!.time0to200 / 1_000_000_000.0f, ChartMode.ACCELERATION)
        
        val currentColor = 0xFF3486A9.toInt() // #3486A9 - син-зелен като бутона Acceleration
        val compareColor = 0xFFA64CEB.toInt() // #A64CEB - лилав винаги за Compare
        
        tvCurrentMax.text = "Max: ${String.format("%.1f", currentMaxAccel)} m/s²"
        tvCurrentMax.setTextColor(currentColor)
        tvCurrent0to100.text = "0-100: ${String.format("%.1f", currentAccelAt100)} m/s²"
        tvCurrent0to100.setTextColor(currentColor)
        tvCurrent0to200.text = "0-200: ${String.format("%.1f", currentAccelAt200)} m/s²"
        tvCurrent0to200.setTextColor(currentColor)
        tvCurrent100to200.text = "100-200: ${String.format("%.1f", currentMaxAccel100to200)} m/s²"
        tvCurrent100to200.setTextColor(currentColor)
        tvCurrent0to402.text = "0-402m: ${String.format("%.1f", currentAccelAt402)} m/s²"
        tvCurrent0to402.setTextColor(currentColor)
        
        tvCompareMax.text = "Max: ${String.format("%.1f", compareMaxAccel)} m/s²"
        tvCompareMax.setTextColor(compareColor)
        tvCompare0to100.text = "0-100: ${String.format("%.1f", compareAccelAt100)} m/s²"
        tvCompare0to100.setTextColor(compareColor)
        tvCompare0to200.text = "0-200: ${String.format("%.1f", compareAccelAt200)} m/s²"
        tvCompare0to200.setTextColor(compareColor)
        tvCompare100to200.text = "100-200: ${String.format("%.1f", compareMaxAccel100to200)} m/s²"
        tvCompare100to200.setTextColor(compareColor)
        tvCompare0to402.text = "0-402m: ${String.format("%.1f", compareAccelAt402)} m/s²"
        tvCompare0to402.setTextColor(compareColor)
    }
    
    private fun updateGForceStats() {
        // Използваме реалните данни
        val (currentGs, _) = getAlignedGData(currentAttempt!!)
        val (compareGs, _) = getAlignedGData(compareAttempt!!)
        
        val currentMaxG = currentGs.maxOrNull() ?: 0f
        val compareMaxG = compareGs.maxOrNull() ?: 0f
        
        // Намираме G-силата на ключовите точки
        val currentGAt100 = findValueAtTimeInterpolated(currentAttempt!!, currentAttempt!!.time0to100 / 1_000_000_000.0f, ChartMode.G_FORCE)
        val currentGAt200 = findValueAtTimeInterpolated(currentAttempt!!, currentAttempt!!.time0to200 / 1_000_000_000.0f, ChartMode.G_FORCE)
        val currentGAt402 = findValueAtTimeInterpolated(currentAttempt!!, currentAttempt!!.time0to402 / 1_000_000_000.0f, ChartMode.G_FORCE)
        
        val compareGAt100 = findValueAtTimeInterpolated(compareAttempt!!, compareAttempt!!.time0to100 / 1_000_000_000.0f, ChartMode.G_FORCE)
        val compareGAt200 = findValueAtTimeInterpolated(compareAttempt!!, compareAttempt!!.time0to200 / 1_000_000_000.0f, ChartMode.G_FORCE)
        val compareGAt402 = findValueAtTimeInterpolated(compareAttempt!!, compareAttempt!!.time0to402 / 1_000_000_000.0f, ChartMode.G_FORCE)
        
        // Намираме максималната G-сила в интервала 100-200 km/h
        val currentMaxG100to200 = findMaxValueInRange(currentAttempt!!, currentAttempt!!.time0to100 / 1_000_000_000.0f, currentAttempt!!.time0to200 / 1_000_000_000.0f, ChartMode.G_FORCE)
        val compareMaxG100to200 = findMaxValueInRange(compareAttempt!!, compareAttempt!!.time0to100 / 1_000_000_000.0f, compareAttempt!!.time0to200 / 1_000_000_000.0f, ChartMode.G_FORCE)
        
        val currentColor = 0xFFE68894.toInt() // #E68894 - розов като бутона G-Force
        val compareColor = 0xFFA64CEB.toInt() // #A64CEB - лилав винаги за Compare
        
        tvCurrentMax.text = "Max: ${String.format("%.2f", currentMaxG)} G"
        tvCurrentMax.setTextColor(currentColor)
        tvCurrent0to100.text = "0-100: ${String.format("%.2f", currentGAt100)} G"
        tvCurrent0to100.setTextColor(currentColor)
        tvCurrent0to200.text = "0-200: ${String.format("%.2f", currentGAt200)} G"
        tvCurrent0to200.setTextColor(currentColor)
        tvCurrent100to200.text = "100-200: ${String.format("%.2f", currentMaxG100to200)} G"
        tvCurrent100to200.setTextColor(currentColor)
        tvCurrent0to402.text = "0-402m: ${String.format("%.2f", currentGAt402)} G"
        tvCurrent0to402.setTextColor(currentColor)
        
        tvCompareMax.text = "Max: ${String.format("%.2f", compareMaxG)} G"
        tvCompareMax.setTextColor(compareColor)
        tvCompare0to100.text = "0-100: ${String.format("%.2f", compareGAt100)} G"
        tvCompare0to100.setTextColor(compareColor)
        tvCompare0to200.text = "0-200: ${String.format("%.2f", compareGAt200)} G"
        tvCompare0to200.setTextColor(compareColor)
        tvCompare100to200.text = "100-200: ${String.format("%.2f", compareMaxG100to200)} G"
        tvCompare100to200.setTextColor(compareColor)
        tvCompare0to402.text = "0-402m: ${String.format("%.2f", compareGAt402)} G"
        tvCompare0to402.setTextColor(compareColor)
    }
    
    private fun updateChart() {
        if (currentAttempt == null || compareAttempt == null) return
        
        when (currentMode) {
            ChartMode.SPEED -> updateSpeedChart()
            ChartMode.ACCELERATION -> updateAccelerationChart()
            ChartMode.G_FORCE -> updateGForceChart()
        }
    }

    private fun applyRightChartPadding(maxTimeFromAllMeasurements: Float) {
        val rightPaddingSeconds = maxOf(0.5f, maxTimeFromAllMeasurements * 0.08f)
        chart.xAxis.axisMinimum = 0f
        chart.xAxis.axisMaximum = maxTimeFromAllMeasurements + rightPaddingSeconds
        chart.setVisibleXRangeMaximum(maxTimeFromAllMeasurements + rightPaddingSeconds)
        chart.moveViewToX(0f)
    }
    
    private fun updateSpeedChart() {
        val speedUnitSymbol = UnitsManager.getSpeedUnit(this).symbol
        tvChartTitle.text = "Speed Comparison ($speedUnitSymbol)"
        
        // Създаваме нов LineData с двете линии
        val lineData = LineData()
        addSpeedLineToData(lineData, currentAttempt!!, "Current", 0xFFFF6020.toInt(), true) // #FF6020 като в DragSessionDetailsActivity
        addSpeedLineToData(lineData, compareAttempt!!, "Compare", 0xFFA64CEB.toInt(), false) // #A64CEB винаги за Compare
        
        // Добавяме ключовите точки директно към lineData
        addKeyPointMarkersToData(lineData)
        
        chart.data = lineData
        
        // Настройваме Y оста - използваме реалните данни, не attempt.maxSpeed
        val speedUnit = UnitsManager.getSpeedUnit(this)
        val (currentSpeeds, _) = getAlignedSpeedData(currentAttempt!!)
        val (compareSpeeds, _) = getAlignedSpeedData(compareAttempt!!)
        
        val currentMaxSpeed = currentSpeeds.maxOrNull() ?: 0f
        val compareMaxSpeed = compareSpeeds.maxOrNull() ?: 0f
        val maxSpeed = maxOf(currentMaxSpeed, compareMaxSpeed)
        val convertedMaxSpeed = UnitsManager.convertSpeed(maxSpeed, speedUnit)
        val threshold200 = UnitsManager.convertSpeed(200f, speedUnit)
        
        // Настройваме Y оста - ТОЧНО като в DragSessionDetailsActivity
        val yAxis = chart.axisLeft
        yAxis.axisMinimum = 0f
        yAxis.axisMaximum = if (convertedMaxSpeed > threshold200) convertedMaxSpeed * 1.1f else threshold200
        yAxis.setDrawZeroLine(true)
        yAxis.zeroLineColor = android.graphics.Color.GRAY
        yAxis.zeroLineWidth = 1f
        yAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return value.toInt().toString()
            }
        }
        
        // Настройваме X оста - ТОЧНО като в DragSessionDetailsActivity
        val maxTimeFromAllMeasurements = getMaxTimeFromAllMeasurements(currentAttempt!!, compareAttempt!!).toFloat()
        applyRightChartPadding(maxTimeFromAllMeasurements)
        
        chart.invalidate()
    }
    
    private fun updateAccelerationChart() {
        tvChartTitle.text = "Acceleration Comparison"
        
        // Създаваме нов LineData с двете линии
        val lineData = LineData()
        addAccelerationLineToData(lineData, currentAttempt!!, "Current", 0xFF3486A9.toInt(), true) // #3486A9 като в DragSessionDetailsActivity
        addAccelerationLineToData(lineData, compareAttempt!!, "Compare", 0xFFA64CEB.toInt(), false) // #A64CEB винаги за Compare
        
        // Добавяме ключовите точки директно към lineData
        addKeyPointMarkersToData(lineData)
        
        chart.data = lineData
        
        // Настройваме Y оста - използваме реалните данни
        val (currentAccels, _) = getAlignedAccelData(currentAttempt!!)
        val (compareAccels, _) = getAlignedAccelData(compareAttempt!!)
        
        val maxAccel1 = currentAccels.maxOrNull() ?: 0f
        val maxAccel2 = compareAccels.maxOrNull() ?: 0f
        val minAccel1 = currentAccels.minOrNull() ?: 0f
        val minAccel2 = compareAccels.minOrNull() ?: 0f
        
        val maxAccel = maxOf(maxAccel1, maxAccel2)
        val minAccel = minOf(minAccel1, minAccel2)
        val padding = (maxAccel - minAccel) * 0.1f
        
        // Настройваме Y оста - ТОЧНО като в DragSessionDetailsActivity
        val yAxis = chart.axisLeft
        yAxis.axisMinimum = minAccel - padding
        yAxis.axisMaximum = maxAccel + padding
        yAxis.setDrawZeroLine(true)
        yAxis.zeroLineColor = android.graphics.Color.GRAY
        yAxis.zeroLineWidth = 1f
        yAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return value.toInt().toString()
            }
        }
        
        // Настройваме X оста - ТОЧНО като в DragSessionDetailsActivity
        val maxTimeFromAllMeasurements = getMaxTimeFromAllMeasurements(currentAttempt!!, compareAttempt!!).toFloat()
        applyRightChartPadding(maxTimeFromAllMeasurements)
        
        chart.invalidate()
    }
    
    private fun updateGForceChart() {
        tvChartTitle.text = "G-Force Comparison"
        
        // Създаваме нов LineData с двете линии
        val lineData = LineData()
        addGForceLineToData(lineData, currentAttempt!!, "Current", 0xFFE68894.toInt(), true) // #E68894 като в DragSessionDetailsActivity
        addGForceLineToData(lineData, compareAttempt!!, "Compare", 0xFFA64CEB.toInt(), false) // #A64CEB винаги за Compare
        
        // Добавяме ключовите точки директно към lineData
        addKeyPointMarkersToData(lineData)
        
        chart.data = lineData
        
        // Настройваме Y оста - използваме реалните данни
        val (currentGs, _) = getAlignedGData(currentAttempt!!)
        val (compareGs, _) = getAlignedGData(compareAttempt!!)
        
        val maxG1 = currentGs.maxOrNull() ?: 0f
        val maxG2 = compareGs.maxOrNull() ?: 0f
        val maxG = maxOf(maxG1, maxG2)
        
        // Настройваме Y оста - ТОЧНО като в DragSessionDetailsActivity
        val yAxis = chart.axisLeft
        yAxis.axisMinimum = 0f
        val yMax = if (maxG > 0.1f) maxG * 1.1f else 2f
        yAxis.axisMaximum = yMax
        yAxis.setDrawZeroLine(true)
        yAxis.zeroLineColor = android.graphics.Color.GRAY
        yAxis.zeroLineWidth = 1f
        yAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return String.format("%.2f", value)
            }
        }
        
        // Настройваме X оста - ТОЧНО като в DragSessionDetailsActivity
        val maxTimeFromAllMeasurements = getMaxTimeFromAllMeasurements(currentAttempt!!, compareAttempt!!).toFloat()
        applyRightChartPadding(maxTimeFromAllMeasurements)
        
        chart.invalidate()
    }
    
    private fun addSpeedLineToData(lineData: LineData, attempt: DragAttempt, label: String, colorInt: Int, isCurrent: Boolean) {
        val (speedSamples, timestamps) = getAlignedSpeedData(attempt)
        if (speedSamples.isNotEmpty() && timestamps.isNotEmpty()) {
            val speedUnit = UnitsManager.getSpeedUnit(this)
            val entries = mutableListOf<Entry>()
            
            // Показваме реалните времена без нормализация
            for (i in speedSamples.indices) {
                val timeInSeconds = timestamps[i] / 1_000_000_000.0
                val convertedSpeed = UnitsManager.convertSpeed(speedSamples[i], speedUnit)
                entries.add(Entry(timeInSeconds.toFloat(), convertedSpeed))
            }
            
            val dataSet = LineDataSet(entries, label).apply {
                color = colorInt // Използваме директно Int color
                lineWidth = if (isCurrent) 3f else 2f
                setDrawValues(false)
                setDrawCircles(false)
                // КРИТИЧНО: И двете линии (Current и Compare) могат да се highlight-ват
                isHighlightEnabled = true
            }
            
            lineData.addDataSet(dataSet)
        }
    }
    
    private fun addSpeedLine(attempt: DragAttempt, label: String, colorRes: Int, isCurrent: Boolean) {
        val (speedSamples, timestamps) = getAlignedSpeedData(attempt)
        if (speedSamples.isNotEmpty() && timestamps.isNotEmpty()) {
            val speedUnit = UnitsManager.getSpeedUnit(this)
            val entries = mutableListOf<Entry>()
            
            // Показваме реалните времена без нормализация
            for (i in speedSamples.indices) {
                val timeInSeconds = timestamps[i] / 1_000_000_000.0
                val convertedSpeed = UnitsManager.convertSpeed(speedSamples[i], speedUnit)
                entries.add(Entry(timeInSeconds.toFloat(), convertedSpeed))
            }
            
            val dataSet = LineDataSet(entries, label).apply {
                color = ContextCompat.getColor(this@CompareAttemptsActivity, colorRes)
                lineWidth = if (isCurrent) 3f else 2f
                setDrawValues(false)
                setDrawCircles(false)
            }
            
            if (chart.data == null) {
                chart.data = LineData(dataSet)
            } else {
                chart.data?.addDataSet(dataSet)
            }
        }
    }
    
    private fun addAccelerationLineToData(lineData: LineData, attempt: DragAttempt, label: String, colorInt: Int, isCurrent: Boolean) {
        val (accelSamples, timestamps) = getAlignedAccelData(attempt)
        if (accelSamples.isNotEmpty() && timestamps.isNotEmpty()) {
            val entries = mutableListOf<Entry>()
            
            // Показваме реалните времена без нормализация
            for (i in accelSamples.indices) {
                val timeInSeconds = timestamps[i] / 1_000_000_000.0
                entries.add(Entry(timeInSeconds.toFloat(), accelSamples[i]))
            }
            
            val dataSet = LineDataSet(entries, label).apply {
                color = colorInt // Използваме директно Int color
                lineWidth = if (isCurrent) 3f else 2f
                setDrawValues(false)
                setDrawCircles(false)
                // КРИТИЧНО: И двете линии (Current и Compare) могат да се highlight-ват
                isHighlightEnabled = true
            }
            
            lineData.addDataSet(dataSet)
        }
    }
    
    private fun addAccelerationLine(attempt: DragAttempt, label: String, colorRes: Int, isCurrent: Boolean) {
        val (accelSamples, timestamps) = getAlignedAccelData(attempt)
        if (accelSamples.isNotEmpty() && timestamps.isNotEmpty()) {
            val entries = mutableListOf<Entry>()
            
            // Показваме реалните времена без нормализация
            for (i in accelSamples.indices) {
                val timeInSeconds = timestamps[i] / 1_000_000_000.0
                entries.add(Entry(timeInSeconds.toFloat(), accelSamples[i]))
            }
            
            val dataSet = LineDataSet(entries, label).apply {
                color = ContextCompat.getColor(this@CompareAttemptsActivity, colorRes)
                lineWidth = if (isCurrent) 3f else 2f
                setDrawValues(false)
                setDrawCircles(false)
            }
            
            if (chart.data == null) {
                chart.data = LineData(dataSet)
            } else {
                chart.data?.addDataSet(dataSet)
            }
        }
    }
    
    private fun addGForceLineToData(lineData: LineData, attempt: DragAttempt, label: String, colorInt: Int, isCurrent: Boolean) {
        val (gSamples, timestamps) = getAlignedGData(attempt)
        if (gSamples.isNotEmpty() && timestamps.isNotEmpty()) {
            val entries = mutableListOf<Entry>()
            
            for (i in gSamples.indices) {
                val timeInSeconds = timestamps[i] / 1_000_000_000.0
                entries.add(Entry(timeInSeconds.toFloat(), gSamples[i]))
            }
            
            val dataSet = LineDataSet(entries, label).apply {
                color = colorInt // Използваме директно Int color
                lineWidth = if (isCurrent) 3f else 2f
                setDrawValues(false)
                setDrawCircles(false)
                // КРИТИЧНО: И двете линии (Current и Compare) могат да се highlight-ват
                isHighlightEnabled = true
            }
            
            lineData.addDataSet(dataSet)
        }
    }
    
    private fun addGForceLine(attempt: DragAttempt, label: String, colorRes: Int, isCurrent: Boolean) {
        val (gSamples, timestamps) = getAlignedGData(attempt)
        if (gSamples.isNotEmpty() && timestamps.isNotEmpty()) {
            val entries = mutableListOf<Entry>()
            
            for (i in gSamples.indices) {
                val timeInSeconds = timestamps[i] / 1_000_000_000.0
                entries.add(Entry(timeInSeconds.toFloat(), gSamples[i]))
            }
            
            val dataSet = LineDataSet(entries, label).apply {
                color = ContextCompat.getColor(this@CompareAttemptsActivity, colorRes)
                lineWidth = if (isCurrent) 3f else 2f
                setDrawValues(false)
                setDrawCircles(false)
            }
            
            if (chart.data == null) {
                chart.data = LineData(dataSet)
            } else {
                chart.data?.addDataSet(dataSet)
            }
        }
    }
    
    // Helper functions for getting data (copy from DragSessionDetailsActivity)
    private fun getAlignedSpeedData(attempt: DragAttempt): Pair<List<Float>, List<Long>> {
        val speeds = attempt.speedSamples ?: emptyList()
        val times = attempt.speedTimeStamps ?: emptyList()
        val limit = minOf(speeds.size, times.size)
        // RAW данни - без филтри, показваме всичко както е записано
        return speeds.take(limit) to times.take(limit)
    }
    
    private fun getAlignedAccelData(attempt: DragAttempt): Pair<List<Float>, List<Long>> {
        val vals = attempt.gpsAccelSamples ?: emptyList()
        val times = attempt.gpsTimeStamps ?: emptyList()
        val speeds = attempt.speedSamples ?: emptyList()
        val limit = minOf(vals.size, times.size, speeds.size)
        
        // Показваме всички данни - графиката започва от 0 секунди
        return vals.take(limit) to times.take(limit)
    }
    
    private fun getAlignedGData(attempt: DragAttempt): Pair<List<Float>, List<Long>> {
        val gValues = attempt.gSamples ?: emptyList()
        val gTimestamps = attempt.timeStamps ?: emptyList()
        
        val limit = minOf(gValues.size, gTimestamps.size)
        if (limit == 0) {
            return emptyList<Float>() to emptyList()
        }
        
        val sanitizedValues = gValues.take(limit).map { value ->
            when {
                value.isNaN() || value.isInfinite() -> 0f
                else -> value
            }
        }
        val sanitizedTimestamps = gTimestamps.take(limit)
        
        return sanitizedValues to sanitizedTimestamps
    }
    
    // -------- Start offset helpers (begin charts at 60 km/h for tests) --------
    private fun getSpeedStartOffsetMs(attempt: DragAttempt, thresholdKmH: Float = 60f): Long {
        val speeds = attempt.speedSamples ?: emptyList()
        val times = attempt.speedTimeStamps ?: emptyList()
        val limit = minOf(speeds.size, times.size)
        
        for (i in 0 until limit) {
            if (speeds[i] >= thresholdKmH) return times[i]
        }
        return 0L
    }
    
    private fun getStartOffsetMsForMode(attempt: DragAttempt, mode: ChartMode): Long {
        // Align all modes to the speed start (first >= 4 km/h)
        return getSpeedStartOffsetMs(attempt)
    }
    
    private fun getMaxTimeFromAllMeasurements(currentAttempt: DragAttempt, compareAttempt: DragAttempt): Double {
        // Намираме максималното време САМО от успешните измервания за двата опита
        val allTimes = mutableListOf<Double>()
        
        // Добавяме САМО успешните измерени времена за текущия опит
        if (currentAttempt.time0to100 > 0) allTimes.add(currentAttempt.time0to100 / 1_000_000_000.0)
        if (currentAttempt.time0to200 > 0) allTimes.add(currentAttempt.time0to200 / 1_000_000_000.0)
        if (currentAttempt.time100to200 > 0) allTimes.add(currentAttempt.time100to200 / 1_000_000_000.0)
        if (currentAttempt.time0to402 > 0) allTimes.add(currentAttempt.time0to402 / 1_000_000_000.0)
        
        // Добавяме САМО успешните измерени времена за сравняващия опит
        if (compareAttempt.time0to100 > 0) allTimes.add(compareAttempt.time0to100 / 1_000_000_000.0)
        if (compareAttempt.time0to200 > 0) allTimes.add(compareAttempt.time0to200 / 1_000_000_000.0)
        if (compareAttempt.time100to200 > 0) allTimes.add(compareAttempt.time100to200 / 1_000_000_000.0)
        if (compareAttempt.time0to402 > 0) allTimes.add(compareAttempt.time0to402 / 1_000_000_000.0)
        
        // НЕ добавяме timestamps - използваме само успешните измервания
        // Ако няма успешни измервания, използваме минимално време
        return allTimes.maxOrNull() ?: 1.0 // По подразбиране 1 секунда ако няма успешни измервания
    }
    
    private fun setupSmartMarker() {
        smartMarker = SmartMarker(this, R.layout.marker_simple)
        chart.marker = smartMarker
    }
    
    private fun updateSmartMarker() {
        if (::smartMarker.isInitialized) {
            smartMarker.setAttempts(currentAttempt, compareAttempt)
            smartMarker.setMode(currentMode)
        }
    }
    
    private fun getMaxAcceleration(attempt: DragAttempt): Float {
        return attempt.gpsAccelSamples?.maxOrNull() ?: 0f
    }
    
    private fun getMinAcceleration(attempt: DragAttempt): Float {
        return attempt.gpsAccelSamples?.minOrNull() ?: 0f
    }
    
    private fun getMaxGForce(attempt: DragAttempt): Float {
        return attempt.gSamples?.maxOrNull() ?: 0f
    }
    
    private fun setupChartZoom(chart: LineChart) {
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

            // Спираме разпространението на touch събитията към родителския контейнер
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Спираме скрола на родителския контейнер когато докосваме графиката
                    chart.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Разрешаваме скрола на родителския контейнер когато приключваме с графиката
                    chart.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }

            true
        }
        
        // Gesture listener за double tap to fit screen - ТОЧНО като в DragSessionDetailsActivity
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
                // Нищо – не искаме бъбъл при обикновено цъкане (snapping-ът в onValueSelected ще се погрижи)
            }
            override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}
            override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {}
            override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {}
        })
        
        // Value selected listener за tooltip-и с SNAPPING логика
        chart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                if (e == null || h == null || currentAttempt == null || compareAttempt == null) {
                    chart.highlightValue(null)
                    smartMarker.shouldShow = false
                    chart.invalidate()
                    return
                }

                smartMarker.shouldShow = true  // Винаги показваме бъбъл при плъзгане

                // 2D snapping за специални точки
                val specialPoints = mutableListOf<SpecialPoint>()
                addSpecialPointsForAttempt(specialPoints, currentAttempt!!, true, currentMode)
                addSpecialPointsForAttempt(specialPoints, compareAttempt!!, false, currentMode)

                var closestSpecial: SpecialPoint? = null
                var minDist2D = Float.MAX_VALUE
                val xThresh = 0.6f
                val yThreshMultiplier = 12f
                val yThreshBase = chart.height / 12f

                for (point in specialPoints) {
                    val dx = kotlin.math.abs(e.x - point.x)
                    val dy = kotlin.math.abs(e.y - point.y)
                    val yThresh = yThreshBase * yThreshMultiplier
                    if (dx < xThresh && dy < yThresh) {
                        val dist2D = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (dist2D < minDist2D) {
                            minDist2D = dist2D
                            closestSpecial = point
                        }
                    }
                }

                var finalEntry = e
                // Определяме на коя линия сме - използваме dataSetIndex от highlight
                var finalIsCurrent = (h.dataSetIndex == 0) // 0 = Current, 1 = Compare
                var finalExactTime = e.x

                if (closestSpecial != null) {
                    // Специална точка – snap с точна Y
                    val exactY = when (currentMode) {
                        ChartMode.SPEED -> {
                            val speedUnit = UnitsManager.getSpeedUnit(this@CompareAttemptsActivity)
                            when (closestSpecial.type) {
                                PointType.SPEED_100 -> UnitsManager.convertSpeed(100f, speedUnit)
                                PointType.SPEED_200 -> UnitsManager.convertSpeed(200f, speedUnit)
                                PointType.DISTANCE_402 -> {
                                    val attempt = if (closestSpecial.isCurrent) currentAttempt!! else compareAttempt!!
                                    findValueAtTimeInterpolated(attempt, closestSpecial.x, currentMode)
                                }
                            }
                        }
                        else -> {
                            val attempt = if (closestSpecial.isCurrent) currentAttempt!! else compareAttempt!!
                            findValueAtTimeInterpolated(attempt, closestSpecial.x, currentMode)
                        }
                    }
                    finalEntry = Entry(closestSpecial.x, exactY)
                    finalIsCurrent = closestSpecial.isCurrent
                    finalExactTime = closestSpecial.exactTime
                }

                val lineIndex = if (finalIsCurrent) 0 else 1
                val specialDataSetIndex = if (closestSpecial != null) {
                    findSpecialPointDataSetIndex(chart, finalEntry.x, finalEntry.y)
                } else {
                    null
                }
                val finalHighlight = Highlight(finalEntry.x, finalEntry.y, specialDataSetIndex ?: lineIndex)

                try {
                    // Запазваме текущия pointType преди да го сетнем (за fallback)
                    val currentPointTypeField = smartMarker.javaClass.getDeclaredField("pointType")
                    currentPointTypeField.isAccessible = true
                    val currentPointType = currentPointTypeField.get(smartMarker) as? PointType
                    
                    val pointTypeField = smartMarker.javaClass.getDeclaredField("pointType")
                    pointTypeField.isAccessible = true
                    pointTypeField.set(smartMarker, closestSpecial?.type ?: currentPointType)

                    val isOnSpecialPointField = smartMarker.javaClass.getDeclaredField("isOnSpecialPoint")
                    isOnSpecialPointField.isAccessible = true
                    isOnSpecialPointField.set(smartMarker, closestSpecial != null)

                    val actualValueField = smartMarker.javaClass.getDeclaredField("actualValue")
                    actualValueField.isAccessible = true
                    actualValueField.set(smartMarker, finalEntry.y)

                    val exactTimeField = smartMarker.javaClass.getDeclaredField("exactTime")
                    exactTimeField.isAccessible = true
                    exactTimeField.set(smartMarker, finalExactTime)

                    val isOnCurrentLineField = smartMarker.javaClass.getDeclaredField("isOnCurrentLine")
                    isOnCurrentLineField.isAccessible = true
                    isOnCurrentLineField.set(smartMarker, finalIsCurrent)

                    val modeField = smartMarker.javaClass.getDeclaredField("mode")
                    modeField.isAccessible = true
                    modeField.set(smartMarker, currentMode)

                    val attemptField = smartMarker.javaClass.getDeclaredField("currentAttempt")
                    attemptField.isAccessible = true
                    attemptField.set(smartMarker, currentAttempt)

                    val compareAttemptField = smartMarker.javaClass.getDeclaredField("compareAttempt")
                    compareAttemptField.isAccessible = true
                    compareAttemptField.set(smartMarker, compareAttempt)

                    // Активираме показването
                    val shouldShowField = smartMarker.javaClass.getDeclaredField("shouldShow")
                    shouldShowField.isAccessible = true
                    shouldShowField.set(smartMarker, true)
                } catch (ex: Exception) {}

                smartMarker.refreshContent(finalEntry, finalHighlight)

                chart.highlightValue(finalHighlight, false)
                chart.invalidate()
            }

            override fun onNothingSelected() {
                chart.highlightValue(null)
                // КРИТИЧНО: Скриваме маркера когато няма избрана стойност
                smartMarker.shouldShow = false
                chart.invalidate()
            }
        })
    }
    
    // Помощна функция за добавяне на специални точки в списък (за snapping логиката)
    private fun addSpecialPointsForAttempt(
        specialPoints: MutableList<SpecialPoint>,
        attempt: DragAttempt,
        isCurrent: Boolean,
        mode: ChartMode
    ) {
        val (speeds, times) = getAlignedSpeedData(attempt)
        
        // 0-100 km/h
        if (attempt.time0to100 > 0) {
            val time100 = findSpeedCrossingPoint(speeds, times, 100f)
            if (time100 != null && time100 > 0f) {
                val speedUnit = UnitsManager.getSpeedUnit(this)
                val y100 = when (mode) {
                    ChartMode.SPEED -> UnitsManager.convertSpeed(100f, speedUnit)
                    ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, time100, mode)
                    ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, time100, mode)
                }
                val exactTime100 = attempt.time0to100 / 1_000_000_000.0f
                specialPoints.add(SpecialPoint(time100, y100, PointType.SPEED_100, isCurrent, exactTime100))
            }
        }
        
        // 0-200 km/h
        if (attempt.time0to200 > 0) {
            val time200 = findSpeedCrossingPoint(speeds, times, 200f)
            if (time200 != null && time200 > 0f) {
                val speedUnit = UnitsManager.getSpeedUnit(this)
                val y200 = when (mode) {
                    ChartMode.SPEED -> UnitsManager.convertSpeed(200f, speedUnit)
                    ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, time200, mode)
                    ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, time200, mode)
                }
                val exactTime200 = attempt.time0to200 / 1_000_000_000.0f
                specialPoints.add(SpecialPoint(time200, y200, PointType.SPEED_200, isCurrent, exactTime200))
            }
        }
        
        // 0-402m
        if (attempt.time0to402 > 0) {
            val time402 = attempt.time0to402 / 1_000_000_000.0f
            if (time402 > 0f) {
                val y402 = findValueAtTimeInterpolated(attempt, time402, mode)
                val exactTime402 = attempt.time0to402 / 1_000_000_000.0f
                specialPoints.add(SpecialPoint(time402, y402, PointType.DISTANCE_402, isCurrent, exactTime402))
            }
        }
    }

    private fun findSpecialPointDataSetIndex(chart: LineChart, x: Float, y: Float): Int? {
        val dataSets = chart.data?.dataSets ?: return null
        for (i in dataSets.indices) {
            val dataSet = dataSets[i]
            if (dataSet.label.isNotEmpty() || dataSet.entryCount != 1) continue
            val pointEntry = dataSet.getEntryForIndex(0) ?: continue
            if (kotlin.math.abs(pointEntry.x - x) <= 0.03f && kotlin.math.abs(pointEntry.y - y) <= 0.8f) {
                return i
            }
        }
        return null
    }
    
    // Помощна функция за показване на маркера на специална точка
    private fun showMarkerAtPoint(chart: LineChart, entry: Entry, pointType: PointType, exactTime: Float, isCurrentAttempt: Boolean = true) {
        val dataSetIndex = when (currentMode) {
            ChartMode.SPEED -> {
                chart.data?.dataSets?.indexOfFirst {
                    it.label.contains("Speed", ignoreCase = true) || it.label.contains("Current", ignoreCase = true) || it.label.isEmpty()
                } ?: 0
            }
            ChartMode.ACCELERATION -> {
                chart.data?.dataSets?.indexOfFirst {
                    it.label.contains("Acceleration", ignoreCase = true) || it.label.contains("Accel", ignoreCase = true) || it.label.contains("Current", ignoreCase = true)
                } ?: 0
            }
            ChartMode.G_FORCE -> {
                chart.data?.dataSets?.indexOfFirst {
                    it.label.contains("G-Force", ignoreCase = true) || it.label.contains("G Force", ignoreCase = true) || it.label.contains("GForce", ignoreCase = true) || it.label.contains("Current", ignoreCase = true)
                } ?: 0
            }
        }
        val specialDataSetIndex = findSpecialPointDataSetIndex(chart, entry.x, entry.y)
        val validDataSetIndex = (specialDataSetIndex ?: dataSetIndex)
            .coerceIn(0, (chart.data?.dataSets?.size ?: 1) - 1)
        val highlight = Highlight(entry.x, entry.y, validDataSetIndex)
        
        try {
            val pointTypeField = smartMarker.javaClass.getDeclaredField("pointType")
            pointTypeField.isAccessible = true
            pointTypeField.set(smartMarker, pointType)

            val isOnSpecialPointField = smartMarker.javaClass.getDeclaredField("isOnSpecialPoint")
            isOnSpecialPointField.isAccessible = true
            isOnSpecialPointField.set(smartMarker, true)

            val actualValueField = smartMarker.javaClass.getDeclaredField("actualValue")
            actualValueField.isAccessible = true
            actualValueField.set(smartMarker, entry.y)

            val exactTimeField = smartMarker.javaClass.getDeclaredField("exactTime")
            exactTimeField.isAccessible = true
            exactTimeField.set(smartMarker, exactTime)

            val isOnCurrentLineField = smartMarker.javaClass.getDeclaredField("isOnCurrentLine")
            isOnCurrentLineField.isAccessible = true
            isOnCurrentLineField.set(smartMarker, isCurrentAttempt)

            val modeField = smartMarker.javaClass.getDeclaredField("mode")
            modeField.isAccessible = true
            modeField.set(smartMarker, currentMode)

            val attemptField = smartMarker.javaClass.getDeclaredField("currentAttempt")
            attemptField.isAccessible = true
            attemptField.set(smartMarker, currentAttempt)

            val compareAttemptField = smartMarker.javaClass.getDeclaredField("compareAttempt")
            compareAttemptField.isAccessible = true
            compareAttemptField.set(smartMarker, compareAttempt)

            // КРИТИЧНО: Активираме маркера за показване
            val shouldShowField = smartMarker.javaClass.getDeclaredField("shouldShow")
            shouldShowField.isAccessible = true
            shouldShowField.set(smartMarker, true)
        } catch (ex: Exception) {
            // Reflection failed
        }

        smartMarker.refreshContent(entry, highlight)

        chart.highlightValue(highlight, false)
        chart.invalidate()
    }
    
    private fun addKeyPointMarkersToData(lineData: LineData) {
        if (currentAttempt == null || compareAttempt == null) return
        
        // Добавяме точки за текущия опит БЕЗ етикети
        addKeyPointMarkersForAttemptToData(lineData, currentAttempt!!, "")
        // Добавяме точки за сравняващия опит БЕЗ етикети
        addKeyPointMarkersForAttemptToData(lineData, compareAttempt!!, "")
    }
    
    private fun addKeyPointMarkers() {
        if (currentAttempt == null || compareAttempt == null) return
        
        // Добавяме точки за текущия опит БЕЗ етикети
        addKeyPointMarkersForAttempt(currentAttempt!!, "")
        // Добавяме точки за сравняващия опит БЕЗ етикети
        addKeyPointMarkersForAttempt(compareAttempt!!, "")
    }
    
    private fun addKeyPointMarkersForAttemptToData(lineData: LineData, attempt: DragAttempt, label: String) {
        val entries = mutableListOf<Entry>()

        val (speedSamples, timestamps) = getAlignedSpeedData(attempt)
        if (speedSamples.isEmpty() || timestamps.isEmpty()) return

        // Маркер за 100 km/h (зелен) - конвертиран според единицата
        if (attempt.time0to100 > 0) {
            val crossing100 = findSpeedCrossingPoint(speedSamples, timestamps, 100f)
            if (crossing100 != null) {
                val speedUnit = UnitsManager.getSpeedUnit(this)
                val valueAt100 = when (currentMode) {
                    ChartMode.SPEED -> UnitsManager.convertSpeed(100f, speedUnit)
                    ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, crossing100, currentMode)
                    ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, crossing100, currentMode)
                }
                entries.add(Entry(crossing100, valueAt100))
            }
        }

        // Маркер за 200 km/h (син) - конвертиран според единицата
        if (attempt.time0to200 > 0) {
            val crossing200 = findSpeedCrossingPoint(speedSamples, timestamps, 200f)
            if (crossing200 != null) {
                val speedUnit = UnitsManager.getSpeedUnit(this)
                val valueAt200 = when (currentMode) {
                    ChartMode.SPEED -> UnitsManager.convertSpeed(200f, speedUnit)
                    ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, crossing200, currentMode)
                    ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, crossing200, currentMode)
                }
                entries.add(Entry(crossing200, valueAt200))
            }
        }

        // Маркер за 402m (червен)
        if (attempt.time0to402 > 0) {
            // Показваме реалното време без нормализация
            val time402Seconds = attempt.time0to402 / 1_000_000_000.0f
            val valueAt402 = findValueAtTimeInterpolated(attempt, time402Seconds, currentMode)
            entries.add(Entry(time402Seconds, valueAt402))
        }

        // Създаваме отделни DataSet-ове за всеки milestone с правилния цвят
        if (attempt.time0to100 > 0) {
            val crossing100 = findSpeedCrossingPoint(speedSamples, timestamps, 100f)
            if (crossing100 != null) {
                val speedUnit = UnitsManager.getSpeedUnit(this)
                val valueAt100 = when (currentMode) {
                    ChartMode.SPEED -> UnitsManager.convertSpeed(100f, speedUnit)
                    ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, crossing100, currentMode)
                    ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, crossing100, currentMode)
                }
                val entry100 = Entry(crossing100, valueAt100)
                val dataSet100 = LineDataSet(listOf(entry100), "").apply {
                    setDrawValues(false)
                    setDrawCircles(true)
                    setDrawFilled(false)
                    lineWidth = 0f
                    isHighlightEnabled = true // ВРЪЩАМЕ TRUE, за да работи tap върху кръгчето
                    setCircleColor(ContextCompat.getColor(this@CompareAttemptsActivity, R.color.accent_green)) // 100 km/h - зелена
                    circleRadius = 8f
                    circleHoleRadius = 4f
                    // Скриваме от легендата
                    form = com.github.mikephil.charting.components.Legend.LegendForm.NONE
                }
                dataSet100.color = android.graphics.Color.parseColor("#3c4040")
                lineData.addDataSet(dataSet100)
            }
        }

        if (attempt.time0to200 > 0) {
            val crossing200 = findSpeedCrossingPoint(speedSamples, timestamps, 200f)
            if (crossing200 != null) {
                val speedUnit = UnitsManager.getSpeedUnit(this)
                val valueAt200 = when (currentMode) {
                    ChartMode.SPEED -> UnitsManager.convertSpeed(200f, speedUnit)
                    ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, crossing200, currentMode)
                    ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, crossing200, currentMode)
                }
                val entry200 = Entry(crossing200, valueAt200)
                val dataSet200 = LineDataSet(listOf(entry200), "").apply {
                    setDrawValues(false)
                    setDrawCircles(true)
                    setDrawFilled(false)
                    lineWidth = 0f
                    isHighlightEnabled = true // ВРЪЩАМЕ TRUE, за да работи tap върху кръгчето
                    setCircleColor(ContextCompat.getColor(this@CompareAttemptsActivity, R.color.accent_blue)) // 200 km/h - синя
                    circleRadius = 8f
                    circleHoleRadius = 4f
                    // Скриваме от легендата
                    form = com.github.mikephil.charting.components.Legend.LegendForm.NONE
                }
                dataSet200.color = android.graphics.Color.parseColor("#3c4040")
                lineData.addDataSet(dataSet200)
            }
        }

        if (attempt.time0to402 > 0) {
            val time402Seconds = attempt.time0to402 / 1_000_000_000.0f
            val valueAt402 = findValueAtTimeInterpolated(attempt, time402Seconds, currentMode)
            val entry402 = Entry(time402Seconds, valueAt402)
            val dataSet402 = LineDataSet(listOf(entry402), "").apply {
                setDrawValues(false)
                setDrawCircles(true)
                setDrawFilled(false)
                lineWidth = 0f
                isHighlightEnabled = true // ВРЪЩАМЕ TRUE, за да работи tap върху кръгчето
                setCircleColor(ContextCompat.getColor(this@CompareAttemptsActivity, R.color.accent_red)) // 402m - червена
                circleRadius = 8f
                circleHoleRadius = 4f
                // Скриваме от легендата
                form = com.github.mikephil.charting.components.Legend.LegendForm.NONE
            }
            dataSet402.color = android.graphics.Color.parseColor("#3c4040")
            lineData.addDataSet(dataSet402)
        }
    }
    
    private fun addKeyPointMarkersForAttempt(attempt: DragAttempt, label: String) {
        val (speedSamples, timestamps) = getAlignedSpeedData(attempt)
        if (speedSamples.isEmpty() || timestamps.isEmpty()) return

        // Създаваме отделни DataSet-ове за всеки milestone с правилния цвят
        if (attempt.time0to100 > 0) {
            val crossing100 = findSpeedCrossingPoint(speedSamples, timestamps, 100f)
            if (crossing100 != null) {
                val speedUnit = UnitsManager.getSpeedUnit(this)
                val valueAt100 = when (currentMode) {
                    ChartMode.SPEED -> UnitsManager.convertSpeed(100f, speedUnit)
                    ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, crossing100, currentMode)
                    ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, crossing100, currentMode)
                }
                val entry100 = Entry(crossing100, valueAt100)
                val dataSet100 = LineDataSet(listOf(entry100), "").apply {
                    setDrawValues(false)
                    setDrawCircles(true)
                    setDrawFilled(false)
                    lineWidth = 0f
                    setCircleColor(ContextCompat.getColor(this@CompareAttemptsActivity, R.color.accent_green)) // 100 km/h - зелена
                    circleRadius = 8f
                    circleHoleRadius = 4f
                }
                dataSet100.color = android.graphics.Color.parseColor("#3c4040")
                chart.data?.addDataSet(dataSet100)
            }
        }

        if (attempt.time0to200 > 0) {
            val crossing200 = findSpeedCrossingPoint(speedSamples, timestamps, 200f)
            if (crossing200 != null) {
                val speedUnit = UnitsManager.getSpeedUnit(this)
                val valueAt200 = when (currentMode) {
                    ChartMode.SPEED -> UnitsManager.convertSpeed(200f, speedUnit)
                    ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, crossing200, currentMode)
                    ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, crossing200, currentMode)
                }
                val entry200 = Entry(crossing200, valueAt200)
                val dataSet200 = LineDataSet(listOf(entry200), "").apply {
                    setDrawValues(false)
                    setDrawCircles(true)
                    setDrawFilled(false)
                    lineWidth = 0f
                    setCircleColor(ContextCompat.getColor(this@CompareAttemptsActivity, R.color.accent_blue)) // 200 km/h - синя
                    circleRadius = 8f
                    circleHoleRadius = 4f
                }
                dataSet200.color = android.graphics.Color.parseColor("#3c4040")
                chart.data?.addDataSet(dataSet200)
            }
        }

        if (attempt.time0to402 > 0) {
            val time402Seconds = attempt.time0to402 / 1_000_000_000.0f
            val valueAt402 = findValueAtTimeInterpolated(attempt, time402Seconds, currentMode)
            val entry402 = Entry(time402Seconds, valueAt402)
            val dataSet402 = LineDataSet(listOf(entry402), "").apply {
                setDrawValues(false)
                setDrawCircles(true)
                setDrawFilled(false)
                lineWidth = 0f
                setCircleColor(ContextCompat.getColor(this@CompareAttemptsActivity, R.color.accent_red)) // 402m - червена
                circleRadius = 8f
                circleHoleRadius = 4f
            }
            dataSet402.color = android.graphics.Color.parseColor("#3c4040")
            chart.data?.addDataSet(dataSet402)
        }
    }
    
    
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
                } else {
                    return t0
                }
            }
        }
        return null
    }
    
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

        // Ако не намерим съседни точки, връщаме последната стойност
        return values.lastOrNull() ?: 0f
    }
    
    private fun findMaxValueInRange(attempt: DragAttempt, startTimeSeconds: Float, endTimeSeconds: Float, mode: ChartMode): Float {
        val (values, timestamps) = when (mode) {
            ChartMode.SPEED -> getAlignedSpeedData(attempt)
            ChartMode.ACCELERATION -> getAlignedAccelData(attempt)
            ChartMode.G_FORCE -> getAlignedGData(attempt)
        }
        
        if (values.isEmpty() || timestamps.isEmpty()) return 0f
        
        val startTimeNanos = (startTimeSeconds * 1_000_000_000).toLong()
        val endTimeNanos = (endTimeSeconds * 1_000_000_000).toLong()
        
        var maxValue = Float.MIN_VALUE
        
        for (i in values.indices) {
            val timestamp = timestamps[i]
            if (timestamp >= startTimeNanos && timestamp <= endTimeNanos) {
                maxValue = maxOf(maxValue, values[i])
            }
        }
        
        return if (maxValue == Float.MIN_VALUE) 0f else maxValue
    }
}

// SmartMarker клас за показване на балончета точно като в нормалната графика
class SmartMarker(context: Context, layoutResource: Int) : com.github.mikephil.charting.components.MarkerView(context, layoutResource) {
    
    private val markerContext: Context = context
    private var currentEntry: Entry? = null
    private var isOnSpecialPoint = false
    private var pointType: CompareAttemptsActivity.PointType = CompareAttemptsActivity.PointType.SPEED_100
    private var actualValue: Float = 0f
    private var mode: CompareAttemptsActivity.ChartMode = CompareAttemptsActivity.ChartMode.SPEED
    private var currentAttempt: DragAttempt? = null
    private var compareAttempt: DragAttempt? = null
    private var isOnCurrentLine = true // Дали цъкването е на текущата линия или на сравняващата
    private var exactTime: Float = 0f // КРИТИЧНО: Точното време от attempt (за да съвпада с Best Times)
    var shouldShow: Boolean = false // КРИТИЧНО: Флаг за контрол на показването на бъбъла
    
    fun setAttempts(current: DragAttempt?, compare: DragAttempt?) {
        currentAttempt = current
        compareAttempt = compare
    }
    
    fun setMode(chartMode: CompareAttemptsActivity.ChartMode) {
        mode = chartMode
    }
    
    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        currentEntry = e
        // КРИТИЧНО: Активираме маркера само ако има валиден entry
        shouldShow = e != null
        if (e != null) {
            // Проверяваме дали е на специална точка (цветна)
            val specialPointType = determinePointType(e.x)
            isOnSpecialPoint = specialPointType != null
            pointType = specialPointType ?: CompareAttemptsActivity.PointType.SPEED_100
            
            // КРИТИЧНО: За специални точки exactTime вече е зададено от snapping логиката чрез reflection
            // Ако не е зададено (не е специална точка), използваме координатата
            if (isOnSpecialPoint && exactTime == 0f) {
                // Fallback: изчисляваме от attempt-ите (само ако не е зададено от snapping)
                exactTime = when (specialPointType) {
                    CompareAttemptsActivity.PointType.SPEED_100 -> {
                        val currentTime100 = currentAttempt?.time0to100?.let { it / 1_000_000_000.0f } ?: 0f
                        val compareTime100 = compareAttempt?.time0to100?.let { it / 1_000_000_000.0f } ?: 0f
                        if (kotlin.math.abs(e.x - currentTime100) < kotlin.math.abs(e.x - compareTime100)) {
                            currentTime100
                        } else {
                            compareTime100
                        }
                    }
                    CompareAttemptsActivity.PointType.SPEED_200 -> {
                        val currentTime200 = currentAttempt?.time0to200?.let { it / 1_000_000_000.0f } ?: 0f
                        val compareTime200 = compareAttempt?.time0to200?.let { it / 1_000_000_000.0f } ?: 0f
                        if (kotlin.math.abs(e.x - currentTime200) < kotlin.math.abs(e.x - compareTime200)) {
                            currentTime200
                        } else {
                            compareTime200
                        }
                    }
                    CompareAttemptsActivity.PointType.DISTANCE_402 -> {
                        val currentTime402 = currentAttempt?.time0to402?.let { it / 1_000_000_000.0f } ?: 0f
                        val compareTime402 = compareAttempt?.time0to402?.let { it / 1_000_000_000.0f } ?: 0f
                        if (kotlin.math.abs(e.x - currentTime402) < kotlin.math.abs(e.x - compareTime402)) {
                            currentTime402
                        } else {
                            compareTime402
                        }
                    }
                    null -> e.x
                }
            } else if (!isOnSpecialPoint) {
                exactTime = e.x // За нормални точки използваме координатата
            }
            
            actualValue = e.y
            
            // Определяме на коя линия е цъкнато (current или compare)
            // isOnCurrentLine вече е зададено от snapping логиката чрез reflection, ако е специална точка
            if (!isOnSpecialPoint) {
                isOnCurrentLine = determineWhichLine(e.x, e.y)
            }
        }
        super.refreshContent(e, highlight)
    }
    
    override fun draw(canvas: Canvas, posX: Float, posY: Float) {
        if (currentEntry == null || !shouldShow) return
        
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        
        // Определяме текста и цвета
        val (text, backgroundColor) = if (isOnSpecialPoint) {
            val timeToShow = exactTime.coerceAtLeast(0f)
            val typeText = when (pointType) {
                CompareAttemptsActivity.PointType.SPEED_100 -> "0-100 km/h\n${String.format("%.3f", timeToShow)}s"
                CompareAttemptsActivity.PointType.SPEED_200 -> "0-200 km/h\n${String.format("%.3f", timeToShow)}s"
                CompareAttemptsActivity.PointType.DISTANCE_402 -> {
                    // Използваме правилния attempt според линията
                    val attempt = if (isOnCurrentLine) currentAttempt else compareAttempt
                    val speedAt402 = if (attempt != null) {
                        val (speedSamples, timestamps) = getAlignedSpeedData(attempt)
                        interpolateValueAtTime(speedSamples, timestamps, timeToShow)
                    } else 0f
                    "0-402m\n${speedAt402.toInt()} km/h\n${String.format("%.3f", timeToShow)}s"
                }
            }
            // ПРАВИЛНИ ЦВЕТОВЕ – използваме същите цветове като в DragSessionDetailsActivity за консистентност
            val bgColor = when (pointType) {
                CompareAttemptsActivity.PointType.SPEED_100 -> ContextCompat.getColor(markerContext, R.color.accent_green)
                CompareAttemptsActivity.PointType.SPEED_200 -> ContextCompat.getColor(markerContext, R.color.accent_blue) // ЦИАН – точния цвят на кръгчето за 200 km/h
                CompareAttemptsActivity.PointType.DISTANCE_402 -> ContextCompat.getColor(markerContext, R.color.accent_red)
            }
            Pair(typeText, bgColor)
        } else {
            // Нормална точка – цвят според линията
            val timeAtPoint = currentEntry?.x ?: 0f
            val valueFormatted = when (mode) {
                CompareAttemptsActivity.ChartMode.SPEED -> "${actualValue.toInt()} km/h"
                CompareAttemptsActivity.ChartMode.ACCELERATION -> String.format("%.1f m/s²", actualValue)
                CompareAttemptsActivity.ChartMode.G_FORCE -> String.format("%.2f G", actualValue)
            }
            val lineColor = if (isOnCurrentLine) {
                when (mode) {
                    CompareAttemptsActivity.ChartMode.SPEED -> 0xFFFF6020.toInt() // Оранжев
                    CompareAttemptsActivity.ChartMode.ACCELERATION -> 0xFF3486A9.toInt()
                    CompareAttemptsActivity.ChartMode.G_FORCE -> 0xFFE68894.toInt()
                }
            } else {
                0xFFA64CEB.toInt() // Лилаво за Compare
            }
            Pair("$valueFormatted\n${String.format("%.3f", timeAtPoint)}s", lineColor)
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
        
        // Позиционираме балончето над точката (като в DragSessionDetailsActivity)
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
        
        // Рисуваме малка стрелка надолу към точката (като в DragSessionDetailsActivity)
        val path = android.graphics.Path()
        path.moveTo(posX - 8f, balloonY + rectHeight)
        path.lineTo(posX + 8f, balloonY + rectHeight)
        path.lineTo(posX, balloonY + rectHeight + 12f)
        path.close()
        canvas.drawPath(path, paint)
    }
    
    override fun getOffset(): MPPointF {
        // Marker-ът се позиционира ръчно в draw() около posX/posY.
        // Не добавяме допълнителен офсет, за да няма изместване вляво/вдясно.
        return MPPointF(0f, 0f)
    }
    
    private fun determinePointType(x: Float): CompareAttemptsActivity.PointType? {
        // Проверяваме за 100 km/h точка в двата опита
        val time100Current = currentAttempt?.time0to100 ?: 0L
        val time100Compare = compareAttempt?.time0to100 ?: 0L
        
        if (time100Current > 0) {
            val time100Seconds = time100Current / 1_000_000_000.0f
            if (kotlin.math.abs(x - time100Seconds) < 0.4f) { // Увеличен радиус за по-лесно засичане
                return CompareAttemptsActivity.PointType.SPEED_100
            }
        }
        if (time100Compare > 0) {
            val time100Seconds = time100Compare / 1_000_000_000.0f
            if (kotlin.math.abs(x - time100Seconds) < 0.4f) {
                return CompareAttemptsActivity.PointType.SPEED_100
            }
        }
        
        // Проверяваме за 200 km/h точка в двата опита
        val time200Current = currentAttempt?.time0to200 ?: 0L
        val time200Compare = compareAttempt?.time0to200 ?: 0L
        
        if (time200Current > 0) {
            val time200Seconds = time200Current / 1_000_000_000.0f
            if (kotlin.math.abs(x - time200Seconds) < 0.4f) {
                return CompareAttemptsActivity.PointType.SPEED_200
            }
        }
        if (time200Compare > 0) {
            val time200Seconds = time200Compare / 1_000_000_000.0f
            if (kotlin.math.abs(x - time200Seconds) < 0.4f) {
                return CompareAttemptsActivity.PointType.SPEED_200
            }
        }
        
        // Проверяваме за 402m точка в двата опита
        val time402Current = currentAttempt?.time0to402 ?: 0L
        val time402Compare = compareAttempt?.time0to402 ?: 0L
        
        if (time402Current > 0) {
            val time402Seconds = time402Current / 1_000_000_000.0f
            if (kotlin.math.abs(x - time402Seconds) < 0.4f) {
                return CompareAttemptsActivity.PointType.DISTANCE_402
            }
        }
        if (time402Compare > 0) {
            val time402Seconds = time402Compare / 1_000_000_000.0f
            if (kotlin.math.abs(x - time402Seconds) < 0.4f) {
                return CompareAttemptsActivity.PointType.DISTANCE_402
            }
        }
        
        return null
    }
    
    private fun determineWhichLine(x: Float, y: Float): Boolean {
        // Трябва да определим на коя линия е цъкнато - current или compare
        // Това е сложно, защото и двете линии могат да имат еднакви стойности
        // За сега ще използваме просто правило - ако е близо до current attempt данните
        
        if (currentAttempt == null || compareAttempt == null) return true
        
        // Получаваме данните за двата опита
        val (currentValues, currentTimes) = getAlignedDataForMode(currentAttempt!!, mode)
        val (compareValues, compareTimes) = getAlignedDataForMode(compareAttempt!!, mode)
        
        // Намираме най-близката точка в current данните
        val currentDistance = findMinDistanceToLine(currentValues, currentTimes, x, y)
        
        // Намираме най-близката точка в compare данните
        val compareDistance = findMinDistanceToLine(compareValues, compareTimes, x, y)
        
        // Връщаме true ако current е по-близо
        return currentDistance <= compareDistance
    }
    
    private fun getAlignedDataForMode(attempt: DragAttempt, mode: CompareAttemptsActivity.ChartMode): Pair<List<Float>, List<Long>> {
        return when (mode) {
            CompareAttemptsActivity.ChartMode.SPEED -> {
                val speeds = attempt.speedSamples ?: emptyList()
                val times = attempt.speedTimeStamps ?: emptyList()
                val limit = minOf(speeds.size, times.size)
                speeds.take(limit) to times.take(limit)
            }
            CompareAttemptsActivity.ChartMode.ACCELERATION -> {
                val accels = attempt.gpsAccelSamples ?: emptyList()
                val times = attempt.gpsTimeStamps ?: emptyList()
                val limit = minOf(accels.size, times.size)
                accels.take(limit) to times.take(limit)
            }
            CompareAttemptsActivity.ChartMode.G_FORCE -> {
                val gs = attempt.gSamples ?: emptyList()
                val times = attempt.timeStamps ?: emptyList()
                val limit = minOf(gs.size, times.size)
                gs.take(limit) to times.take(limit)
            }
        }
    }
    
    private fun findMinDistanceToLine(values: List<Float>, timestamps: List<Long>, targetX: Float, targetY: Float): Float {
        if (values.isEmpty() || timestamps.isEmpty()) return Float.MAX_VALUE
        
        var minDistance = Float.MAX_VALUE
        
        for (i in values.indices) {
            val timeSeconds = timestamps[i] / 1_000_000_000.0f
            val value = values[i]
            
            val distance = kotlin.math.sqrt((targetX - timeSeconds) * (targetX - timeSeconds) + (targetY - value) * (targetY - value))
            if (distance < minDistance) {
                minDistance = distance
            }
        }
        
        return minDistance
    }
    
    private fun getSpeedAtTime(timeSeconds: Float): Float {
        val attempt = if (isOnCurrentLine) currentAttempt else compareAttempt
        if (attempt == null) return 0f
        
        val (speedSamples, timestamps) = getAlignedSpeedData(attempt)
        return interpolateValueAtTime(speedSamples, timestamps, timeSeconds)
    }
    
    private fun getAlignedSpeedData(attempt: DragAttempt): Pair<List<Float>, List<Long>> {
        val speeds = attempt.speedSamples ?: emptyList()
        val times = attempt.speedTimeStamps ?: emptyList()
        val limit = minOf(speeds.size, times.size)
        
        // Показваме всички данни от 0 секунди
        return speeds.take(limit) to times.take(limit)
    }
    
    private fun getSpeedStartOffsetMs(attempt: DragAttempt, thresholdKmH: Float = 60f): Long {
        val speeds = attempt.speedSamples ?: emptyList()
        val times = attempt.speedTimeStamps ?: emptyList()
        val limit = minOf(speeds.size, times.size)
        
        for (i in 0 until limit) {
            if (speeds[i] >= thresholdKmH) return times[i]
        }
        return 0L
    }
    
    private fun interpolateValueAtTime(values: List<Float>, timestamps: List<Long>, targetTimeSeconds: Float): Float {
        if (values.isEmpty() || timestamps.isEmpty()) return 0f
        
        val targetTimeNanos = (targetTimeSeconds * 1_000_000_000).toLong()
        
        for (i in 1 until values.size) {
            val t0 = timestamps[i - 1]
            val t1 = timestamps[i]
            val v0 = values[i - 1]
            val v1 = values[i]
            
            if (targetTimeNanos >= t0 && targetTimeNanos <= t1) {
                if (t1 == t0) return v1
                val ratio = (targetTimeNanos - t0).toFloat() / (t1 - t0).toFloat()
                return v0 + (v1 - v0) * ratio
            }
        }
        
        return values.lastOrNull() ?: 0f
    }
}
