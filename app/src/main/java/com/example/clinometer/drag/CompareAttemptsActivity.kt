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
import android.widget.ImageView
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.util.*

class CompareAttemptsActivity : AppCompatActivity() {

    private val CHART_TOP_OFFSET_DP = 28f
    private val CHART_Y_HEADROOM_MULTIPLIER = 1.15f
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }
    
    private lateinit var chart: LineChart
    private lateinit var tvChartTitle: TextView
    private lateinit var btnSpeed: Button
    private lateinit var btnAcceleration: Button
    private lateinit var btnGForce: Button
    private lateinit var llChartMode: LinearLayout
    
    // Comparison row views bound inline in bindCmpRow()
    
    private var currentSessionId: Long = -1
    private var currentAttemptId: Long = -1
    private var compareSessionId: Long = -1
    private var compareAttemptId: Long = -1
    
    private var currentAttempt: DragAttempt? = null
    private var compareAttempt: DragAttempt? = null
    private var currentSession: DragSession? = null
    private var compareSession: DragSession? = null
    private var allDragSessions: List<DragSession> = emptyList()
    private val profileBestAttemptCache = mutableMapOf<Pair<Long, ComparisonMetric>, PersonalBestAttempt?>()
    
    enum class ChartMode {
        SPEED, ACCELERATION, G_FORCE
    }
    
    enum class PointType {
        SPEED_100, SPEED_200, DISTANCE_402
    }

    enum class ComparisonMetric {
        ZERO_TO_100, HUNDRED_TO_200, ZERO_TO_200, QUARTER_MILE
    }

    private data class PbFlags(
        val current: Boolean,
        val compare: Boolean
    )

    private data class PersonalBestAttempt(
        val sessionId: Long,
        val attemptId: Long,
        val metricTimeNs: Long,
        val attemptTimestamp: Long,
        val sessionTimestamp: Long
    )

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
    }

    private fun setupViews() {
        chart = findViewById(R.id.chart)
        tvChartTitle = findViewById(R.id.tvChartTitle)
        btnSpeed = findViewById(R.id.btnSpeed)
        btnAcceleration = findViewById(R.id.btnAcceleration)
        btnGForce = findViewById(R.id.btnGForce)
        btnGForce.visibility = View.GONE
        btnGForce.isEnabled = false
        llChartMode = findViewById(R.id.llChartMode)

        setupChart()
        setupChartModeButtons()
        loadData()

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
        chart.setExtraTopOffset(CHART_TOP_OFFSET_DP)
        
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
        allDragSessions = DragStorage.getAllDragSessions(this)
        profileBestAttemptCache.clear()

        // Зареждаме текущата сесия и избрания current опит
        currentSession = allDragSessions.find { it.id == currentSessionId }
        currentAttempt = currentSession?.attempts?.find { it.id == currentAttemptId }
            ?: currentSession?.attempts?.firstOrNull()
        
        // Зареждаме опита за сравняване
        compareSession = allDragSessions.find { it.id == compareSessionId }
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

        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

        currentSession?.timestamp?.let { ts ->
            val dateView = findViewById<TextView>(R.id.tvCurrentSessionDate)
            dateView.text = dateFormat.format(Date(ts))
            dateView.visibility = View.VISIBLE
        }

        compareSession?.timestamp?.let { ts ->
            val dateView = findViewById<TextView>(R.id.tvCompareSessionDate)
            dateView.text = dateFormat.format(Date(ts))
            dateView.visibility = View.VISIBLE
        }

        currentAttempt?.let { attempt ->
            if (attempt.temperature != null || attempt.humidity != null || attempt.windKph != null) {
                val weatherLayout = findViewById<LinearLayout>(R.id.llCurrentWeather)
                weatherLayout.visibility = View.VISIBLE
                val (iconRes, tintRes) = resolveWeatherIconStyle(attempt.weatherIcon ?: -1, attempt.humidity)
                val ivCondition = findViewById<ImageView>(R.id.ivCurrentWeatherCondition)
                ivCondition.setImageResource(iconRes)
                ivCondition.imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, tintRes)
                )
                attempt.temperature?.let {
                    findViewById<TextView>(R.id.tvCurrentWeatherTemp).text =
                        UnitsManager.formatTemperature(it, this, decimals = 0)
                }
                attempt.humidity?.let {
                    findViewById<TextView>(R.id.tvCurrentWeatherHumidity).text = "$it%"
                }
                attempt.windKph?.let {
                    val speedUnit = UnitsManager.getSpeedUnit(this)
                    val converted = UnitsManager.convertSpeed(it, speedUnit)
                    findViewById<TextView>(R.id.tvCurrentWeatherWind).text =
                        "${converted.toInt()} ${speedUnit.symbol}"
                }
            }
        }

        compareAttempt?.let { attempt ->
            if (attempt.temperature != null || attempt.humidity != null || attempt.windKph != null) {
                val weatherLayout = findViewById<LinearLayout>(R.id.llCompareWeather)
                weatherLayout.visibility = View.VISIBLE
                val (iconRes, tintRes) = resolveWeatherIconStyle(attempt.weatherIcon ?: -1, attempt.humidity)
                val ivCondition = findViewById<ImageView>(R.id.ivCompareWeatherCondition)
                ivCondition.setImageResource(iconRes)
                ivCondition.imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, tintRes)
                )
                attempt.temperature?.let {
                    findViewById<TextView>(R.id.tvCompareWeatherTemp).text =
                        UnitsManager.formatTemperature(it, this, decimals = 0)
                }
                attempt.humidity?.let {
                    findViewById<TextView>(R.id.tvCompareWeatherHumidity).text = "$it%"
                }
                attempt.windKph?.let {
                    val speedUnit = UnitsManager.getSpeedUnit(this)
                    val converted = UnitsManager.convertSpeed(it, speedUnit)
                    findViewById<TextView>(R.id.tvCompareWeatherWind).text =
                        "${converted.toInt()} ${speedUnit.symbol}"
                }
            }
        }
    }

    private fun resolveWeatherIconStyle(iconRes: Int, humidityPercent: Int?): Pair<Int, Int> {
        val baseIcon = when (iconRes) {
            R.drawable.ic_weather_sunny -> R.drawable.ic_weather_sunny
            R.drawable.ic_weather_clear_night -> R.drawable.ic_weather_clear_night
            R.drawable.ic_weather_partly_cloudy,
            R.drawable.ic_weather_partly_cloudy_night -> R.drawable.ic_weather_partly_cloudy
            R.drawable.ic_weather_cloudy -> R.drawable.ic_weather_cloudy
            R.drawable.ic_weather_rainy -> R.drawable.ic_weather_rainy
            R.drawable.ic_weather_snowy -> R.drawable.ic_weather_snowy
            else -> R.drawable.ic_weather_cloudy
        }

        val finalIcon = if (baseIcon == R.drawable.ic_weather_sunny && (humidityPercent ?: 0) >= 70) {
            R.drawable.ic_weather_cloudy
        } else {
            baseIcon
        }

        val tintRes = when (finalIcon) {
            R.drawable.ic_weather_sunny -> R.color.warning_color
            R.drawable.ic_weather_rainy,
            R.drawable.ic_weather_snowy -> R.color.accent_light
            R.drawable.ic_weather_clear_night,
            R.drawable.ic_weather_cloudy,
            R.drawable.ic_weather_partly_cloudy -> R.color.text_tertiary
            else -> R.color.text_tertiary
        }

        return finalIcon to tintRes
    }
    
    private fun updateComparisonStats() {
        val curAttempt = currentAttempt ?: return
        val cmpAttempt = compareAttempt ?: return
        val speedUnit = UnitsManager.getSpeedUnit(this)

        fun nanoToSec(nanos: Long) = if (nanos > 0) nanos / 1_000_000_000.0 else -1.0
        fun fmtTime(secs: Double) = if (secs > 0) String.format("%.3f", secs) else "--"
        fun fmtDeltaSec(delta: Double): String {
            val sign = if (delta < 0) "\u2212" else "+"
            return "$sign${String.format("%.3f", Math.abs(delta))}s"
        }
        fun fmtDeltaSpeed(deltaKmh: Float): String {
            val conv = UnitsManager.convertSpeed(Math.abs(deltaKmh), speedUnit)
            val sign = if (deltaKmh < 0) "\u2212" else "+"
            return "$sign${conv.toInt()} ${speedUnit.symbol}"
        }

        val cur0to100   = nanoToSec(curAttempt.time0to100)
        val cmp0to100   = nanoToSec(cmpAttempt.time0to100)
        val cur0to200   = nanoToSec(curAttempt.time0to200)
        val cmp0to200   = nanoToSec(cmpAttempt.time0to200)
        val cur100to200Ns = resolve100To200SplitTimeNs(curAttempt)
        val cmp100to200Ns = resolve100To200SplitTimeNs(cmpAttempt)
        val cur100to200 = nanoToSec(cur100to200Ns ?: -1L)
        val cmp100to200 = nanoToSec(cmp100to200Ns ?: -1L)
        val cur0to402   = nanoToSec(curAttempt.time0to402)
        val cmp0to402   = nanoToSec(cmpAttempt.time0to402)
        val curTrap     = curAttempt.maxSpeed
        val cmpTrap     = cmpAttempt.maxSpeed

        val orangeDot = ContextCompat.getColor(this, R.color.primary_color)
        val blueDot   = ContextCompat.getColor(this, R.color.accent_light)

        val pb0to100 = resolvePbFlags(ComparisonMetric.ZERO_TO_100, currentSession, curAttempt, compareSession, cmpAttempt)
        val pb100to200 = resolvePbFlags(ComparisonMetric.HUNDRED_TO_200, currentSession, curAttempt, compareSession, cmpAttempt)
        val pb0to200 = resolvePbFlags(ComparisonMetric.ZERO_TO_200, currentSession, curAttempt, compareSession, cmpAttempt)
        val pb0to402 = resolvePbFlags(ComparisonMetric.QUARTER_MILE, currentSession, curAttempt, compareSession, cmpAttempt)

        bindCmpRow(
            leftValId = R.id.tvCmpLeftVal0to100,
            leftStatusId = R.id.tvCmpLeftStatus0to100,
            leftPBId = R.id.tvCmpLeftPB0to100,
            dotId = R.id.vCmpDot0to100,
            midDeltaId = R.id.tvCmpMidDelta0to100,
            rightValId = R.id.tvCmpRightVal0to100,
            rightStatusId = R.id.tvCmpRightStatus0to100,
            rightPBId = R.id.tvCmpRightPB0to100,
            curRaw = cur0to100,
            cmpRaw = cmp0to100,
            curDisplay = if (cur0to100 > 0) fmtTime(cur0to100) + "s" else "--",
            cmpDisplay = if (cmp0to100 > 0) fmtTime(cmp0to100) else "--",
            delta = if (cur0to100 > 0 && cmp0to100 > 0) fmtDeltaSec(cur0to100 - cmp0to100) else "--",
            isLeftPb = pb0to100.current,
            isRightPb = pb0to100.compare,
            lowerIsBetter = true,
            dotColor = orangeDot
        )

        bindCmpRow(
            leftValId = R.id.tvCmpLeftVal100to200,
            leftStatusId = R.id.tvCmpLeftStatus100to200,
            leftPBId = R.id.tvCmpLeftPB100to200,
            dotId = R.id.vCmpDot100to200,
            midDeltaId = R.id.tvCmpMidDelta100to200,
            rightValId = R.id.tvCmpRightVal100to200,
            rightStatusId = R.id.tvCmpRightStatus100to200,
            rightPBId = R.id.tvCmpRightPB100to200,
            curRaw = cur100to200,
            cmpRaw = cmp100to200,
            curDisplay = if (cur100to200 > 0) fmtTime(cur100to200) + "s" else "--",
            cmpDisplay = if (cmp100to200 > 0) fmtTime(cmp100to200) else "--",
            delta = if (cur100to200 > 0 && cmp100to200 > 0) fmtDeltaSec(cur100to200 - cmp100to200) else "--",
            isLeftPb = pb100to200.current,
            isRightPb = pb100to200.compare,
            lowerIsBetter = true,
            dotColor = orangeDot
        )

        bindCmpRow(
            leftValId = R.id.tvCmpLeftVal0to200,
            leftStatusId = R.id.tvCmpLeftStatus0to200,
            leftPBId = R.id.tvCmpLeftPB0to200,
            dotId = R.id.vCmpDot0to200,
            midDeltaId = R.id.tvCmpMidDelta0to200,
            rightValId = R.id.tvCmpRightVal0to200,
            rightStatusId = R.id.tvCmpRightStatus0to200,
            rightPBId = R.id.tvCmpRightPB0to200,
            curRaw = cur0to200,
            cmpRaw = cmp0to200,
            curDisplay = if (cur0to200 > 0) fmtTime(cur0to200) + "s" else "--",
            cmpDisplay = if (cmp0to200 > 0) fmtTime(cmp0to200) else "--",
            delta = if (cur0to200 > 0 && cmp0to200 > 0) fmtDeltaSec(cur0to200 - cmp0to200) else "--",
            isLeftPb = pb0to200.current,
            isRightPb = pb0to200.compare,
            lowerIsBetter = true,
            dotColor = orangeDot
        )

        bindCmpRow(
            leftValId = R.id.tvCmpLeftVal0to402,
            leftStatusId = R.id.tvCmpLeftStatus0to402,
            leftPBId = R.id.tvCmpLeftPB0to402,
            dotId = R.id.vCmpDot0to402,
            midDeltaId = R.id.tvCmpMidDelta0to402,
            rightValId = R.id.tvCmpRightVal0to402,
            rightStatusId = R.id.tvCmpRightStatus0to402,
            rightPBId = R.id.tvCmpRightPB0to402,
            curRaw = cur0to402,
            cmpRaw = cmp0to402,
            curDisplay = if (cur0to402 > 0) fmtTime(cur0to402) + "s" else "--",
            cmpDisplay = if (cmp0to402 > 0) fmtTime(cmp0to402) else "--",
            delta = if (cur0to402 > 0 && cmp0to402 > 0) fmtDeltaSec(cur0to402 - cmp0to402) else "--",
            isLeftPb = pb0to402.current,
            isRightPb = pb0to402.compare,
            lowerIsBetter = true,
            dotColor = orangeDot
        )

        val curTrapConv = UnitsManager.convertSpeed(curTrap, speedUnit)
        val cmpTrapConv = UnitsManager.convertSpeed(cmpTrap, speedUnit)
        bindCmpRow(
            leftValId = R.id.tvCmpLeftValTrap,
            leftStatusId = R.id.tvCmpLeftStatusTrap,
            leftPBId = null,
            dotId = R.id.vCmpDotTrap,
            midDeltaId = R.id.tvCmpMidDeltaTrap,
            rightValId = R.id.tvCmpRightValTrap,
            rightStatusId = R.id.tvCmpRightStatusTrap,
            rightPBId = null,
            curRaw = curTrap.toDouble(),
            cmpRaw = cmpTrap.toDouble(),
            curDisplay = if (curTrap > 0) "${curTrapConv.toInt()} ${speedUnit.symbol}" else "--",
            cmpDisplay = if (cmpTrap > 0) "${cmpTrapConv.toInt()} ${speedUnit.symbol}" else "--",
            delta = if (curTrap > 0 && cmpTrap > 0) fmtDeltaSpeed(curTrap - cmpTrap) else "--",
            isLeftPb = false,
            isRightPb = false,
            lowerIsBetter = false,
            dotColor = blueDot
        )

        updateSplitsComparison()
    }

    private fun resolvePbFlags(
        metric: ComparisonMetric,
        currentSession: DragSession?,
        currentAttempt: DragAttempt,
        compareSession: DragSession?,
        compareAttempt: DragAttempt
    ): PbFlags {
        val currentProfileId = currentSession?.profileId ?: -1L
        val compareProfileId = compareSession?.profileId ?: -1L
        val currentWinner = currentProfileId.takeIf { it > 0L }?.let { resolveProfileBestAttempt(it, metric) }
        val compareWinner = if (compareProfileId == currentProfileId) {
            currentWinner
        } else {
            compareProfileId.takeIf { it > 0L }?.let { resolveProfileBestAttempt(it, metric) }
        }

        return PbFlags(
            current = currentWinner?.matches(currentSession?.id ?: -1L, currentAttempt.id) == true,
            compare = compareWinner?.matches(compareSession?.id ?: -1L, compareAttempt.id) == true
        )
    }

    private fun resolveProfileBestAttempt(profileId: Long, metric: ComparisonMetric): PersonalBestAttempt? {
        val cacheKey = profileId to metric
        if (profileBestAttemptCache.containsKey(cacheKey)) {
            return profileBestAttemptCache[cacheKey]
        }

        val winner = allDragSessions.asSequence()
            .filter { it.profileId == profileId }
            .flatMap { session ->
                session.attempts.asSequence().mapNotNull { attempt ->
                    val metricTimeNs = getMetricTimeNs(attempt, metric) ?: return@mapNotNull null
                    PersonalBestAttempt(
                        sessionId = session.id,
                        attemptId = attempt.id,
                        metricTimeNs = metricTimeNs,
                        attemptTimestamp = attempt.timestamp,
                        sessionTimestamp = session.timestamp
                    )
                }
            }
            .minWithOrNull(
                compareBy<PersonalBestAttempt> { it.metricTimeNs }
                    .thenByDescending { it.attemptTimestamp }
                    .thenByDescending { it.attemptId }
                    .thenByDescending { it.sessionTimestamp }
                    .thenByDescending { it.sessionId }
            )

        profileBestAttemptCache[cacheKey] = winner
        return winner
    }

    private fun PersonalBestAttempt.matches(sessionId: Long, attemptId: Long): Boolean {
        return this.sessionId == sessionId && this.attemptId == attemptId
    }

    private fun getMetricTimeNs(attempt: DragAttempt, metric: ComparisonMetric): Long? {
        val timeNs = when (metric) {
            ComparisonMetric.ZERO_TO_100 -> attempt.time0to100
            ComparisonMetric.HUNDRED_TO_200 -> resolve100To200SplitTimeNs(attempt) ?: -1L
            ComparisonMetric.ZERO_TO_200 -> attempt.time0to200
            ComparisonMetric.QUARTER_MILE -> attempt.time0to402
        }

        return timeNs.takeIf { it > 0L }
    }

    private fun resolve100To200SplitTimeNs(attempt: DragAttempt): Long? {
        val directSplit = attempt.time100to200.takeIf { it > 0L }
        if (directSplit != null) return directSplit

        val time0to100 = attempt.time0to100.takeIf { it > 0L } ?: return null
        val time0to200 = attempt.time0to200.takeIf { it > 0L } ?: return null
        val derivedSplit = time0to200 - time0to100
        return derivedSplit.takeIf { it > 0L }
    }

    // ─── Distance-based splits (50m, 100m, 200m, 300m, 402m) ──────────────────

    /** Integrates speedSamples (km/h) + speedTimeStamps (nanos) using the trapezoidal
     *  rule and returns elapsed time in nanos (relative to measurement start)
     *  at each distance checkpoint, along with interpolated speed (km/h). */
    private fun computeDistanceSplitsNs(
        attempt: DragAttempt
    ): Map<Int, Pair<Long, Float>> {
        val speeds = attempt.speedSamples
        val times  = attempt.speedTimeStamps
        if (speeds.size < 2 || times.size < 2 || speeds.size != times.size) return emptyMap()

        val markers = listOf(50, 100, 200, 300, 402)
        val result  = mutableMapOf<Int, Pair<Long, Float>>()

        var cumDistM = 0.0

        for (i in 1 until speeds.size) {
            val dtS        = (times[i] - times[i - 1]) / 1_000_000_000.0
            if (dtS <= 0) continue
            val avgSpeedMs = (speeds[i] + speeds[i - 1]) / 2.0 / 3.6
            val prevDist   = cumDistM
            cumDistM      += avgSpeedMs * dtS

            for (marker in markers) {
                if (marker in result) continue
                if (prevDist < marker && cumDistM >= marker) {
                    val fraction     = if (cumDistM - prevDist > 0) (marker - prevDist) / (cumDistM - prevDist) else 0.0
                    val interpNs     = times[i - 1] + ((times[i] - times[i - 1]) * fraction).toLong()
                    val interpSpeedKmh = (speeds[i - 1] + (speeds[i] - speeds[i - 1]) * fraction).toFloat()
                    result[marker]   = Pair(interpNs, interpSpeedKmh)
                }
            }
            if (result.size == markers.size) break
        }
        return result
    }

    private fun resolveStoredDistanceSplitData(
        attempt: DragAttempt,
        distanceMeters: Int
    ): Pair<Long, Float>? {
        val timeNs = when (distanceMeters) {
            50 -> attempt.distance50mTimeNs
            100 -> attempt.distance100mTimeNs
            200 -> attempt.distance200mTimeNs
            300 -> attempt.distance300mTimeNs
            402 -> attempt.time0to402.takeIf { it > 0L } ?: attempt.distance402mTimeNs
            else -> -1L
        }
        if (timeNs <= 0L) return null

        val storedSpeedKmh = when (distanceMeters) {
            50 -> attempt.distance50mSpeedKmh
            100 -> attempt.distance100mSpeedKmh
            200 -> attempt.distance200mSpeedKmh
            300 -> attempt.distance300mSpeedKmh
            402 -> attempt.distance402mSpeedKmh
            else -> -1f
        }

        val resolvedSpeedKmh = if (storedSpeedKmh >= 0f) {
            storedSpeedKmh
        } else {
            findValueAtTimeInterpolated(
                attempt,
                timeNs / 1_000_000_000.0f,
                ChartMode.SPEED
            )
        }

        return timeNs to resolvedSpeedKmh
    }

    private fun resolveDistanceSplitData(
        attempt: DragAttempt,
        distanceMeters: Int,
        derivedSplit: Pair<Long, Float>?
    ): Pair<Long, Float>? {
        return resolveStoredDistanceSplitData(attempt, distanceMeters) ?: derivedSplit
    }

    /** Populates the splits comparison card. Card is shown only when both
     *  attempts have full 402m data with speed samples. */
    private fun updateSplitsComparison() {
        val curAttempt = currentAttempt
        val cmpAttempt = compareAttempt
        val card       = findViewById<androidx.cardview.widget.CardView>(R.id.cvSplitsComparison)

        val bothHave402 = (curAttempt?.time0to402 ?: -1L) > 0L &&
                          (cmpAttempt?.time0to402 ?: -1L) > 0L &&
                          (curAttempt?.speedSamples?.size ?: 0) > 1 &&
                          (cmpAttempt?.speedSamples?.size ?: 0) > 1

        if (!bothHave402 || curAttempt == null || cmpAttempt == null) {
            card.visibility = View.GONE
            return
        }

        card.visibility = View.VISIBLE

        val curSplits = computeDistanceSplitsNs(curAttempt)
        val cmpSplits = computeDistanceSplitsNs(cmpAttempt)
        val speedUnit = UnitsManager.getSpeedUnit(this)

        val orangeColor  = ContextCompat.getColor(this, R.color.primary_color)
        val purpleColor  = ContextCompat.getColor(this, R.color.drag_run_purple)
        val greenColor   = ContextCompat.getColor(this, R.color.drag_run_green)
        val redColor     = ContextCompat.getColor(this, R.color.accent_red)
        val neutralColor = ContextCompat.getColor(this, R.color.text_secondary)

        data class SplitRowDef(val distance: Int, val label: String, val rowId: Int)
        val rows = listOf(
            SplitRowDef(50,  "50M",  R.id.splitRow50m),
            SplitRowDef(100, "100M", R.id.splitRow100m),
            SplitRowDef(200, "200M", R.id.splitRow200m),
            SplitRowDef(300, "300M", R.id.splitRow300m),
            SplitRowDef(402, "402M", R.id.splitRow402m)
        )

        for (row in rows) {
            val rowView = findViewById<android.view.View>(row.rowId)
            val tvLabel    = rowView.findViewById<TextView>(R.id.tvSplitLabel)
            val tvCurTime  = rowView.findViewById<TextView>(R.id.tvSplitCurTime)
            val tvCurSpeed = rowView.findViewById<TextView>(R.id.tvSplitCurSpeed)
            val tvCmpTime  = rowView.findViewById<TextView>(R.id.tvSplitCmpTime)
            val tvCmpSpeed = rowView.findViewById<TextView>(R.id.tvSplitCmpSpeed)
            val tvDelta    = rowView.findViewById<TextView>(R.id.tvSplitDelta)

            tvLabel.text = row.label

            val curData = resolveDistanceSplitData(curAttempt, row.distance, curSplits[row.distance])
            val cmpData = resolveDistanceSplitData(cmpAttempt, row.distance, cmpSplits[row.distance])

            fun fmtTime(nanos: Long) = String.format("%.3f", nanos / 1_000_000_000.0) + "s"
            fun fmtSpd(kmh: Float): String {
                val conv = UnitsManager.convertSpeed(kmh, speedUnit)
                return "${conv.toInt()} ${speedUnit.symbol}"
            }

            tvCurTime.text  = if (curData != null) fmtTime(curData.first) else "--"
            tvCurSpeed.text = if (curData != null) fmtSpd(curData.second) else ""
            tvCmpTime.text  = if (cmpData != null) fmtTime(cmpData.first) else "--"
            tvCmpSpeed.text = if (cmpData != null) fmtSpd(cmpData.second) else ""

            if (curData != null && cmpData != null) {
                val deltaSec = (curData.first - cmpData.first) / 1_000_000_000.0
                val curWins  = deltaSec < 0
                val sign     = if (deltaSec < 0) "\u2212" else "+"
                tvDelta.text = "$sign${String.format("%.3f", Math.abs(deltaSec))}s"
                tvDelta.setTextColor(if (curWins) greenColor else redColor)
                tvCurTime.setTextColor(orangeColor)
                tvCmpTime.setTextColor(purpleColor)
            } else {
                tvDelta.text = ""
                tvCurTime.setTextColor(orangeColor)
                tvCmpTime.setTextColor(purpleColor)
            }
        }
    }

    private fun bindCmpRow(
        leftValId: Int, leftStatusId: Int, leftPBId: Int?,
        dotId: Int, midDeltaId: Int,
        rightValId: Int, rightStatusId: Int, rightPBId: Int?,
        curRaw: Double, cmpRaw: Double,
        curDisplay: String, cmpDisplay: String,
        delta: String, isLeftPb: Boolean, isRightPb: Boolean,
        lowerIsBetter: Boolean, dotColor: Int
    ) {
        val fasterColor  = ContextCompat.getColor(this, R.color.drag_run_green)
        val cmpWinColor  = ContextCompat.getColor(this, R.color.drag_run_purple)
        val redColor     = ContextCompat.getColor(this, R.color.accent_red)
        val orangeColor  = ContextCompat.getColor(this, R.color.primary_color)

        val hasData        = curRaw > 0 && cmpRaw > 0
        val currentIsBetter = hasData && if (lowerIsBetter) curRaw < cmpRaw else curRaw > cmpRaw

        val leftVal     = findViewById<TextView>(leftValId)
        val leftStatus  = findViewById<TextView>(leftStatusId)
        val midDelta    = findViewById<TextView>(midDeltaId)
        val rightVal    = findViewById<TextView>(rightValId)
        val rightStatus = findViewById<TextView>(rightStatusId)
        val dot         = findViewById<View>(dotId)

        leftVal.text = curDisplay
        leftVal.setTextColor(orangeColor)
        if (hasData) {
            leftStatus.text = if (currentIsBetter) "\u25B2 FASTER" else "\u25BC SLOWER"
            leftStatus.setTextColor(if (currentIsBetter) fasterColor else redColor)
        } else {
            leftStatus.text = ""
        }

        leftPBId?.let { id ->
            findViewById<TextView>(id).visibility = if (isLeftPb) View.VISIBLE else View.GONE
        }

        dot.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(dotColor)
        }
        midDelta.text = delta

        rightVal.text = cmpDisplay
        rightVal.setTextColor(cmpWinColor)
        rightPBId?.let { id ->
            findViewById<TextView>(id).visibility = if (isRightPb) View.VISIBLE else View.GONE
        }
        if (hasData) {
            rightStatus.text = if (currentIsBetter) "\u25BC SLOWER" else "\u25B2 FASTER"
            rightStatus.setTextColor(if (currentIsBetter) redColor else fasterColor)
        } else {
            rightStatus.text = ""
        }
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
        val speedTopRef = maxOf(convertedMaxSpeed, threshold200)
        yAxis.axisMaximum = speedTopRef * CHART_Y_HEADROOM_MULTIPLIER
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
        val padding = (maxAccel - minAccel) * 0.15f
        
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
        val yMax = if (maxG > 0.1f) maxG * CHART_Y_HEADROOM_MULTIPLIER else 2f
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
    
    private fun addKeyPointMarkersToData(lineData: LineData) {
        if (currentAttempt == null || compareAttempt == null) return
        
        // Добавяме точки за текущия опит БЕЗ етикети
        addKeyPointMarkersForAttemptToData(lineData, currentAttempt!!, "")
        // Добавяме точки за сравняващия опит БЕЗ етикети
        addKeyPointMarkersForAttemptToData(lineData, compareAttempt!!, "")
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
            // Ако snapping логиката вече е сетнала isOnSpecialPoint=true и pointType чрез reflection,
            // НЕ презаписваме – determinePointType може погрешно да върне SPEED_100 когато
            // 0-100 и 0-200 точките са в рамките на 0.4s (напр. 9.0s vs 9.3s).
            if (!isOnSpecialPoint) {
                val specialPointType = determinePointType(e.x)
                isOnSpecialPoint = specialPointType != null
                pointType = specialPointType ?: CompareAttemptsActivity.PointType.SPEED_100
            }
            
            // КРИТИЧНО: За специални точки exactTime вече е зададено от snapping логиката чрез reflection
            // Ако не е зададено (не е специална точка), използваме координатата
            if (isOnSpecialPoint && exactTime == 0f) {
                // Fallback: изчисляваме от attempt-ите (само ако не е зададено от snapping)
                exactTime = when (pointType) {
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
    
    private fun getAlignedSpeedData(attempt: DragAttempt): Pair<List<Float>, List<Long>> {
        val speeds = attempt.speedSamples ?: emptyList()
        val times = attempt.speedTimeStamps ?: emptyList()
        val limit = minOf(speeds.size, times.size)
        
        // Показваме всички данни от 0 секунди
        return speeds.take(limit) to times.take(limit)
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
