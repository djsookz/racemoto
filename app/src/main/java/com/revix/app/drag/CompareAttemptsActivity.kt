package com.revix.app.drag

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
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
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.revix.app.R
import com.revix.app.settings.LanguageManager
import com.revix.app.settings.UnitsManager
import com.revix.app.DragSession
import com.revix.app.DragSessionDetailsActivity
import com.revix.app.DragAttempt
import com.revix.app.DragStorage
import com.revix.app.track.TrackGForceChartSmoothing
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
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
import com.github.mikephil.charting.renderer.LineChartRenderer
import com.github.mikephil.charting.renderer.XAxisRenderer
import com.github.mikephil.charting.utils.MPPointF
import java.util.*

class CompareAttemptsActivity : AppCompatActivity() {

    private val CHART_TOP_OFFSET_DP = 30f
    private val CHART_Y_HEADROOM_MULTIPLIER = 1.15f
    private val CURRENT_LINE_COLOR = 0xFFFF6020.toInt()
    private val COMPARE_LINE_COLOR = 0xFFA64CEB.toInt()
    private val READER_SNAP_DP = 8f
    private val READER_SNAP_MAX_SECONDS = 0.12f
    private val KMH_TO_MPS = 1f / 3.6f
    private val MPS2_TO_G = 1f / 9.81f
    private val ACCEL_DISPLAY_MAX_DELTA_G_PER_SEC = 7f
    private val ACCEL_DISPLAY_EMA_ALPHA = 0.22f
    private val ACCEL_DISPLAY_CLAMP_G = 3.5f
    
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
    private var snapDialog: CompareSnapDialog? = null

    private data class CompareSnapDialog(
        val type: PointType,
        val exactTime: Float,
        val chartX: Float,
        val chartY: Float,
        val isCurrent: Boolean
    )

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
        updateCompareMetricLabels()
        loadData()

        findViewById<View>(R.id.btnBack)?.setOnClickListener {
            navigateBackToOriginSession()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateBackToOriginSession()
            }
        })
    }

    private fun navigateBackToOriginSession() {
        if (currentSessionId <= 0L) {
            finish()
            return
        }

        startActivity(Intent(this, DragSessionDetailsActivity::class.java).apply {
            putExtra("SESSION_ID", currentSessionId)
            if (currentAttemptId > 0L) {
                putExtra("ATTEMPT_ID", currentAttemptId)
            }
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        finish()
    }

    private fun setupChart() {
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.setDoubleTapToZoomEnabled(false)
        chart.setHighlightPerTapEnabled(false)
        chart.isHighlightPerDragEnabled = false
        chart.setDrawMarkers(false)
        chart.marker = null
        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false
        chart.setBackgroundColor(ContextCompat.getColor(this, R.color.background_primary))
        chart.setNoDataText(getString(R.string.drag_chart_no_data))
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
                if (x < -0.05f) return ""
                if (kotlin.math.abs(x) < 0.05f) return "0.0s"
                return String.format("%.1fs", x)
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
        chart.legend.apply {
            isEnabled = true
            textColor = ContextCompat.getColor(this@CompareAttemptsActivity, R.color.text_primary)
            textSize = 12f
        }
        chart.isDragDecelerationEnabled = false
        chart.dragDecelerationFrictionCoef = 0f
        chart.setExtraTopOffset(CHART_TOP_OFFSET_DP)
        chart.setExtraRightOffset(24f)
        chart.setExtraBottomOffset(18f)
        chart.setTag(R.id.tag_drag_chart_reset_view, true)
        installCompareChartCenterReader(chart)
        setupChartZoom(chart)
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
                val accelerationColorInt = 0xFFFF6020.toInt()
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
                val gForceColorInt = 0xFFFF6020.toInt()
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
        
        updateChart()
        updateComparisonStats()
    }
    
    private fun updateCompareMetricLabels() {
        val longLabel = UnitsManager.formatDragCompareZeroTo402Label(this)
        val shortLabel = UnitsManager.formatDragCompareShortZeroTo402Label(this)
        findViewById<TextView>(R.id.tvCmpLeftLabel0to402).text = longLabel
        findViewById<TextView>(R.id.tvCmpRightLabel0to402).text = longLabel
        findViewById<TextView>(R.id.tvCmpMidLabel0to402).text = shortLabel
    }

    private fun loadData() {
        // Light index for PB lookups; hydrate only the two sessions used for charts.
        allDragSessions = DragStorage.loadDragSessions(this)
        profileBestAttemptCache.clear()

        currentSession = DragStorage.getDragSession(this, currentSessionId)
            ?: allDragSessions.find { it.id == currentSessionId }
        currentAttempt = currentSession?.attempts?.find { it.id == currentAttemptId }
            ?: currentSession?.attempts?.firstOrNull()

        compareSession = if (compareSessionId > 0L) {
            DragStorage.getDragSession(this, compareSessionId)
                ?: allDragSessions.find { it.id == compareSessionId }
        } else {
            null
        }
        compareAttempt = compareSession?.attempts?.find { it.id == compareAttemptId }

        updateChart()
        updateSessionInfo()
        updateComparisonStats()
    }
    
    private fun updateSessionInfo() {
        val currentSessionName = currentSession?.name ?: getString(R.string.current_session)
        val compareSessionName = compareSession?.name ?: getString(R.string.compare_session)

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
        fun fmtTime(secs: Double) = if (secs > 0) String.format("%.2f", secs) else "--"
        fun fmtDeltaSec(delta: Double): String {
            val sign = if (delta < 0) "\u2212" else "+"
            return "$sign${String.format("%.2f", Math.abs(delta))}s"
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
        val cur100to200Ns = DragAttemptMetrics.resolve100To200SplitTimeNs(curAttempt)
        val cmp100to200Ns = DragAttemptMetrics.resolve100To200SplitTimeNs(cmpAttempt)
        val cur100to200 = nanoToSec(cur100to200Ns ?: -1L)
        val cmp100to200 = nanoToSec(cmp100to200Ns ?: -1L)
        val cur0to402   = nanoToSec(curAttempt.time0to402)
        val cmp0to402   = nanoToSec(cmpAttempt.time0to402)
        val curTrap     = DragAttemptMetrics.resolveTrapSpeedKmh(curAttempt) ?: 0f
        val cmpTrap     = DragAttemptMetrics.resolveTrapSpeedKmh(cmpAttempt) ?: 0f
        fun showCmpRow(rowId: Int, left: Double, right: Double) {
            findViewById<View>(rowId).visibility =
                if (left > 0.0 || right > 0.0) View.VISIBLE else View.GONE
        }
        showCmpRow(R.id.llCmpRow0to100, cur0to100, cmp0to100)
        showCmpRow(R.id.llCmpRow100to200, cur100to200, cmp100to200)
        showCmpRow(R.id.llCmpRow0to200, cur0to200, cmp0to200)
        showCmpRow(R.id.llCmpRow0to402, cur0to402, cmp0to402)
        val showTrapRow = DragAttemptMetrics.hasSuccessful402(curAttempt) ||
            DragAttemptMetrics.hasSuccessful402(cmpAttempt)
        findViewById<View>(R.id.llCmpTrapRow).visibility = if (showTrapRow) View.VISIBLE else View.GONE

        val redDot    = ContextCompat.getColor(this, R.color.accent_red)
        val greenDot  = ContextCompat.getColor(this, R.color.accent_green)
        val purpleDot = ContextCompat.getColor(this, R.color.accent_purple)
        val blueDot   = ContextCompat.getColor(this, R.color.accent_blue)
        val trapDot   = ContextCompat.getColor(this, R.color.accent_gold)

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
            dotColor = greenDot
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
            dotColor = purpleDot
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
            dotColor = blueDot
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
            dotColor = redDot
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
            dotColor = trapDot
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
            ComparisonMetric.HUNDRED_TO_200 -> DragAttemptMetrics.resolve100To200SplitTimeNs(attempt) ?: -1L
            ComparisonMetric.ZERO_TO_200 -> attempt.time0to200
            ComparisonMetric.QUARTER_MILE -> attempt.time0to402
        }

        return timeNs.takeIf { it > 0L }
    }

    private fun resolveAttemptTrapSpeedKmh(attempt: DragAttempt): Float {
        return DragAttemptMetrics.resolveTrapSpeedKmh(attempt) ?: 0f
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

        data class SplitRowDef(val distance: Int, val rowId: Int)
        val rows = listOf(
            SplitRowDef(50,  R.id.splitRow50m),
            SplitRowDef(100, R.id.splitRow100m),
            SplitRowDef(200, R.id.splitRow200m),
            SplitRowDef(300, R.id.splitRow300m),
            SplitRowDef(402, R.id.splitRow402m)
        )

        for (row in rows) {
            val rowView = findViewById<android.view.View>(row.rowId)
            val tvLabel    = rowView.findViewById<TextView>(R.id.tvSplitLabel)
            val tvCurTime  = rowView.findViewById<TextView>(R.id.tvSplitCurTime)
            val tvCurSpeed = rowView.findViewById<TextView>(R.id.tvSplitCurSpeed)
            val tvCmpTime  = rowView.findViewById<TextView>(R.id.tvSplitCmpTime)
            val tvCmpSpeed = rowView.findViewById<TextView>(R.id.tvSplitCmpSpeed)
            val tvDelta    = rowView.findViewById<TextView>(R.id.tvSplitDelta)

            tvLabel.text = UnitsManager.formatDragSplitDistanceLabel(row.distance, this)

            val curData = resolveDistanceSplitData(curAttempt, row.distance, curSplits[row.distance])
            val cmpData = resolveDistanceSplitData(cmpAttempt, row.distance, cmpSplits[row.distance])

            fun fmtTime(nanos: Long) = String.format("%.2f", nanos / 1_000_000_000.0) + "s"
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
                tvDelta.text = "$sign${String.format("%.2f", Math.abs(deltaSec))}s"
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
            leftStatus.text = if (currentIsBetter) getString(R.string.drag_compare_faster) else getString(R.string.drag_compare_slower)
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
            rightStatus.text = if (currentIsBetter) getString(R.string.drag_compare_slower) else getString(R.string.drag_compare_faster)
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

    private fun applyChartXPadding(maxTimeFromAllMeasurements: Float) {
        val dataStart = 0f
        val dataEnd = if (maxTimeFromAllMeasurements > 0f) maxTimeFromAllMeasurements else 1f
        val duration = (dataEnd - dataStart).coerceAtLeast(1f)
        val resetViewToStart = (chart.getTag(R.id.tag_drag_chart_reset_view) as? Boolean) ?: true
        val previousCenter = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
        val previousRange = chart.visibleXRange

        chart.setTag(R.id.tag_drag_chart_data_start, dataStart)
        chart.setTag(R.id.tag_drag_chart_data_end, dataEnd)
        chart.xAxis.axisMinimum = dataStart - duration
        chart.xAxis.axisMaximum = dataEnd + duration
        chart.setVisibleXRangeMaximum(duration)

        if (resetViewToStart) {
            showFullCompareChart()
            chart.setTag(R.id.tag_drag_chart_reset_view, false)
        } else {
            val visibleRange = if (previousRange > 0f) previousRange else duration
            val targetCenter = previousCenter.coerceIn(dataStart, dataEnd)
            chart.moveViewToX(targetCenter - visibleRange / 2f)
            chart.post { applyCompareChartReader() }
        }
    }

    private fun getChartDataStart(): Float {
        return (chart.getTag(R.id.tag_drag_chart_data_start) as? Float) ?: 0f
    }

    private fun getChartDataEnd(): Float {
        return (chart.getTag(R.id.tag_drag_chart_data_end) as? Float) ?: 1f
    }

    private fun formatCompareReaderTime(timeValue: Float): String {
        val clamped = timeValue.coerceIn(getChartDataStart(), getChartDataEnd()).coerceAtLeast(0f)
        return String.format("%.2fs", clamped)
    }

    private fun formatCompareReaderValueText(value: Float): String {
        return when (currentMode) {
            ChartMode.SPEED -> {
                val speedUnit = UnitsManager.getSpeedUnit(this)
                String.format("%.1f %s", value, speedUnit.symbol)
            }
            ChartMode.ACCELERATION -> String.format("%.2f g", value)
            ChartMode.G_FORCE -> String.format("%.2f G", value)
        }
    }

    private fun installCompareChartCenterReader(chart: LineChart) {
        chart.renderer = object : LineChartRenderer(chart, chart.animator, chart.viewPortHandler) {
            override fun drawData(c: Canvas) {
                super.drawData(c)

                val paint = Paint().apply {
                    color = android.graphics.Color.RED
                    strokeWidth = 3f
                    pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
                    isAntiAlias = true
                }

                val centerX = mViewPortHandler.contentCenter.x
                c.drawLine(
                    centerX,
                    mViewPortHandler.contentTop(),
                    centerX,
                    mViewPortHandler.contentBottom(),
                    paint
                )
            }

            override fun drawExtras(c: Canvas) {
                super.drawExtras(c)
                drawCompareReaderValuePills(c)
                drawCompareSnappedDialog(c)
            }
        }

        chart.setXAxisRenderer(object : XAxisRenderer(
            chart.viewPortHandler,
            chart.xAxis,
            chart.getTransformer(YAxis.AxisDependency.LEFT)
        ) {
            override fun renderAxisLabels(c: Canvas) {
                super.renderAxisLabels(c)
                drawCompareReaderTimePill(c)
            }
        })
    }

    private fun drawCompareSnappedDialog(canvas: Canvas) {
        val snap = snapDialog ?: return
        val pts = floatArrayOf(snap.chartX, snap.chartY)
        chart.getTransformer(YAxis.AxisDependency.LEFT).pointValuesToPixel(pts)
        val drawX = pts[0]
        val drawY = pts[1]

        val speedUnit = UnitsManager.getSpeedUnit(this)
        val speed100 = UnitsManager.convertSpeed(100f, speedUnit).toInt()
        val speed200 = UnitsManager.convertSpeed(200f, speedUnit).toInt()
        val attempt = if (snap.isCurrent) currentAttempt else compareAttempt
        val isRolling200 = attempt != null &&
            attempt.time100to200 > 0L &&
            attempt.time0to200 <= 0L
        val timeToShow = snap.exactTime.coerceAtLeast(0f)

        val text = when (currentMode) {
            ChartMode.SPEED -> when (snap.type) {
                PointType.SPEED_100 ->
                    if (isRolling200) {
                        "$speed100 ${speedUnit.symbol}\n${String.format("%.2f", timeToShow)}s"
                    } else {
                        "0-$speed100 ${speedUnit.symbol}\n${String.format("%.2f", timeToShow)}s"
                    }
                PointType.SPEED_200 ->
                    if (isRolling200) {
                        "$speed100-$speed200 ${speedUnit.symbol}\n${String.format("%.2f", timeToShow)}s"
                    } else {
                        "0-$speed200 ${speedUnit.symbol}\n${String.format("%.2f", timeToShow)}s"
                    }
                PointType.DISTANCE_402 -> {
                    val trapSpeedKmh = attempt?.let { DragAttemptMetrics.resolveTrapSpeedKmh(it) } ?: 0f
                    val convertedTrapSpeed = UnitsManager.convertSpeed(trapSpeedKmh, speedUnit)
                    "0-${UnitsManager.getQuarterMileDistance(this)}\n${convertedTrapSpeed.toInt()} ${speedUnit.symbol}\n${String.format("%.2f", timeToShow)}s"
                }
            }
            ChartMode.ACCELERATION ->
                "${String.format("%.2f", snap.chartY)} g\n${String.format("%.2f", timeToShow)}s"
            ChartMode.G_FORCE ->
                "${String.format("%.2f", snap.chartY)} G\n${String.format("%.2f", timeToShow)}s"
        }
        val backgroundColor = when (snap.type) {
            PointType.SPEED_100 -> ContextCompat.getColor(this, R.color.accent_green)
            PointType.SPEED_200 -> ContextCompat.getColor(this, R.color.accent_blue)
            PointType.DISTANCE_402 -> ContextCompat.getColor(this, R.color.accent_red)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        val padding = 16f
        val lineHeight = textBounds.height() + 4f
        val lines = if (text.contains("\n")) text.split("\n") else listOf(text)
        val maxLineWidth = lines.maxOfOrNull { line ->
            val bounds = Rect()
            textPaint.getTextBounds(line, 0, line.length, bounds)
            bounds.width()
        } ?: textBounds.width()
        val rectWidth = maxLineWidth + padding * 2
        val rectHeight = (lineHeight * lines.size) + padding * 2
        val arrow = 12f
        val contentTop = chart.viewPortHandler.contentTop()
        val contentBottom = chart.viewPortHandler.contentBottom()
        val aboveY = drawY - rectHeight - arrow
        val drawBelow = aboveY < contentTop
        val balloonX = drawX - rectWidth / 2f
        val balloonY = if (drawBelow) {
            (drawY + arrow).coerceAtMost((contentBottom - rectHeight).coerceAtLeast(contentTop))
        } else {
            aboveY
        }

        canvas.drawRoundRect(
            RectF(balloonX, balloonY, balloonX + rectWidth, balloonY + rectHeight),
            12f,
            12f,
            paint
        )
        val startY = balloonY + textBounds.height() + padding / 2f
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, drawX, startY + (index * lineHeight), textPaint)
        }
        val path = android.graphics.Path()
        if (drawBelow) {
            path.moveTo(drawX - 8f, balloonY)
            path.lineTo(drawX + 8f, balloonY)
            path.lineTo(drawX, balloonY - arrow)
        } else {
            path.moveTo(drawX - 8f, balloonY + rectHeight)
            path.lineTo(drawX + 8f, balloonY + rectHeight)
            path.lineTo(drawX, balloonY + rectHeight + arrow)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawCompareReaderTimePill(canvas: Canvas) {
        val dataStart = getChartDataStart()
        val dataEnd = getChartDataEnd()
        val centerValue = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
        val timeText = formatCompareReaderTime(centerValue.coerceIn(dataStart, dataEnd))

        val density = chart.resources.displayMetrics.density
        val xAxis = chart.xAxis
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = xAxis.textSize
            typeface = xAxis.typeface
            textAlign = Paint.Align.CENTER
        }
        val fm = textPaint.fontMetrics
        val labelTop = chart.viewPortHandler.contentBottom() + xAxis.yOffset
        val labelBottom = labelTop + (fm.descent - fm.ascent)
        val baseline = labelTop - fm.ascent

        val textBounds = Rect()
        textPaint.getTextBounds(timeText, 0, timeText.length, textBounds)

        val hPad = 8f * density
        val vPad = 2.5f * density
        val pillWidth = textBounds.width() + hPad * 2f
        val pillHeight = (labelBottom - labelTop) + vPad * 2f
        val centerX = chart.viewPortHandler.contentCenter.x
        val pillRect = RectF(
            centerX - pillWidth / 2f,
            labelTop - vPad,
            centerX + pillWidth / 2f,
            labelBottom + vPad
        )

        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(chart.context, R.color.primary_color)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, pillPaint)
        canvas.drawText(timeText, centerX, baseline, textPaint)
    }

    private fun readerValueForAttempt(attempt: DragAttempt?, centerX: Float): String? {
        if (attempt == null) return null
        val hasData = when (currentMode) {
            ChartMode.SPEED -> getAlignedSpeedData(attempt).first.isNotEmpty()
            ChartMode.ACCELERATION -> getAlignedAccelData(attempt).first.isNotEmpty()
            ChartMode.G_FORCE -> getAlignedGData(attempt).first.isNotEmpty()
        }
        if (!hasData) return null
        return formatCompareReaderValueText(findValueAtTimeInterpolated(attempt, centerX, currentMode))
    }

    private fun drawCompareReaderValuePills(canvas: Canvas) {
        val dataStart = getChartDataStart()
        val dataEnd = getChartDataEnd().coerceAtLeast(dataStart)
        val centerXValue = ((chart.lowestVisibleX + chart.highestVisibleX) / 2f)
            .coerceIn(dataStart, dataEnd)
        val currentText = readerValueForAttempt(currentAttempt, centerXValue)
        val compareText = readerValueForAttempt(compareAttempt, centerXValue)
        if (currentText == null && compareText == null) return

        val density = chart.resources.displayMetrics.density
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = chart.xAxis.textSize
            typeface = chart.xAxis.typeface
            textAlign = Paint.Align.CENTER
        }
        val fm = textPaint.fontMetrics
        val hPad = 8f * density
        val vPad = 2.5f * density
        val strokeWidth = 1.5f * density
        val gap = 6f * density
        val topMargin = chart.xAxis.yOffset
        val textHeight = fm.descent - fm.ascent
        val pillHeight = textHeight + vPad * 2f
        val pillBottom = chart.viewPortHandler.contentTop() - topMargin
        val pillTop = pillBottom - pillHeight

        fun pillWidth(text: String): Float {
            val bounds = Rect()
            textPaint.getTextBounds(text, 0, text.length, bounds)
            return bounds.width() + hPad * 2f
        }

        val pills = buildList {
            currentText?.let { add(it to CURRENT_LINE_COLOR) }
            compareText?.let { add(it to COMPARE_LINE_COLOR) }
        }
        val widths = pills.map { pillWidth(it.first) }
        val totalWidth = widths.sum() + gap * (pills.size - 1).coerceAtLeast(0)
        val contentLeft = chart.viewPortHandler.contentLeft() + 2f * density
        val contentRight = chart.viewPortHandler.contentRight() - 2f * density
        var left = chart.viewPortHandler.contentCenter.x - totalWidth / 2f
        if (left < contentLeft) left = contentLeft
        if (left + totalWidth > contentRight) left = contentRight - totalWidth

        var cursor = left
        pills.forEachIndexed { index, (text, borderColor) ->
            val width = widths[index]
            drawOutlinedReaderPill(
                canvas,
                text,
                textPaint,
                fm,
                borderColor,
                cursor,
                pillTop,
                width,
                pillHeight,
                vPad,
                strokeWidth
            )
            cursor += width + gap
        }
    }

    private fun drawOutlinedReaderPill(
        canvas: Canvas,
        text: String,
        textPaint: Paint,
        fm: Paint.FontMetrics,
        borderColor: Int,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        vPad: Float,
        strokeWidth: Float
    ) {
        val pillRect = RectF(left, top, left + width, top + height)
        val insetRect = RectF(pillRect)
        insetRect.inset(strokeWidth / 2f, strokeWidth / 2f)
        val corner = height / 2f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(chart.context, R.color.background_primary)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        }
        canvas.drawRoundRect(insetRect, corner, corner, fillPaint)
        canvas.drawRoundRect(insetRect, corner, corner, borderPaint)
        val baseline = top + vPad - fm.ascent
        canvas.drawText(text, left + width / 2f, baseline, textPaint)
    }

    private fun readerSnapThresholdX(): Float {
        val contentWidth = chart.viewPortHandler.contentWidth().coerceAtLeast(1f)
        val xPerPx = chart.visibleXRange / contentWidth
        val fromPixels = READER_SNAP_DP * chart.resources.displayMetrics.density * xPerPx
        return minOf(fromPixels, READER_SNAP_MAX_SECONDS)
    }

    private fun showFullCompareChart() {
        val dataStart = getChartDataStart()
        val dataEnd = getChartDataEnd().coerceAtLeast(dataStart)
        val duration = (dataEnd - dataStart).coerceAtLeast(1f)
        chart.fitScreen()
        chart.setVisibleXRangeMaximum(duration)
        chart.moveViewToX(dataStart)
        chart.post {
            chart.setVisibleXRangeMaximum(duration)
            chart.moveViewToX(dataStart)
            applyCompareChartReader()
        }
    }

    private fun clampCompareChartCenter() {
        val dataStart = getChartDataStart()
        val dataEnd = getChartDataEnd().coerceAtLeast(dataStart)
        val visibleRange = chart.visibleXRange
        val currentCenter = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
        when {
            currentCenter < dataStart -> {
                chart.moveViewToX(dataStart - visibleRange / 2f)
                chart.isDragEnabled = false
                chart.postDelayed({ chart.isDragEnabled = true }, 1)
            }
            currentCenter > dataEnd -> {
                chart.moveViewToX(dataEnd - visibleRange / 2f)
                chart.isDragEnabled = false
                chart.postDelayed({ chart.isDragEnabled = true }, 1)
            }
        }
    }

    private fun applyCompareChartReader() {
        val current = currentAttempt
        val compare = compareAttempt
        if (current == null || compare == null || chart.data == null) {
            snapDialog = null
            chart.highlightValue(null, false)
            return
        }

        chart.marker = null
        chart.setDrawMarkers(false)
        chart.highlightValue(null, false)

        val dataStart = getChartDataStart()
        val dataEnd = getChartDataEnd().coerceAtLeast(dataStart)
        val centerX = ((chart.lowestVisibleX + chart.highestVisibleX) / 2f)
            .coerceIn(dataStart, dataEnd)

        val specialPoints = mutableListOf<SpecialPoint>()
        addSpecialPointsForAttempt(specialPoints, current, true, currentMode)
        addSpecialPointsForAttempt(specialPoints, compare, false, currentMode)

        val snap = readerSnapThresholdX()
        val closestSpecial = specialPoints
            .map { point -> point to kotlin.math.abs(centerX - point.x) }
            .filter { (_, distance) -> distance < snap }
            .minByOrNull { it.second }
            ?.first

        if (closestSpecial == null) {
            snapDialog = null
            chart.invalidate()
            return
        }

        snapDialog = CompareSnapDialog(
            type = closestSpecial.type,
            exactTime = closestSpecial.exactTime,
            chartX = closestSpecial.x,
            chartY = closestSpecial.y,
            isCurrent = closestSpecial.isCurrent
        )
        chart.invalidate()
    }
    
    private fun updateSpeedChart() {
        val speedUnitSymbol = UnitsManager.getSpeedUnit(this).symbol
        tvChartTitle.text = getString(R.string.compare_chart_speed, speedUnitSymbol)
        
        // Създаваме нов LineData с двете линии
        val lineData = LineData()
        addSpeedLineToData(lineData, currentAttempt!!, getString(R.string.drag_compare_legend_current), CURRENT_LINE_COLOR, true)
        addSpeedLineToData(lineData, compareAttempt!!, getString(R.string.drag_compare_legend_compare), COMPARE_LINE_COLOR, false)
        
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
        val has200Milestone = listOf(
            currentAttempt?.time0to200,
            currentAttempt?.time100to200,
            compareAttempt?.time0to200,
            compareAttempt?.time100to200
        ).any { it != null && it > 0L }
        val yFloorKmh = if (has200Milestone) 200f else 100f
        val speedTopRef = maxOf(convertedMaxSpeed, UnitsManager.convertSpeed(yFloorKmh, speedUnit))

        val yAxis = chart.axisLeft
        yAxis.axisMinimum = if (isHundredTo200CompareChart()) {
            UnitsManager.convertSpeed(100f, speedUnit)
        } else {
            0f
        }
        yAxis.axisMaximum = speedTopRef * CHART_Y_HEADROOM_MULTIPLIER
        chart.setExtraLeftOffset(if (isHundredTo200CompareChart()) 10f else 4f)
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
        applyChartXPadding(maxTimeFromAllMeasurements)
        
        chart.invalidate()
    }
    
    private fun updateAccelerationChart() {
        tvChartTitle.text = getString(R.string.compare_chart_acceleration)
        
        // Създаваме нов LineData с двете линии
        val lineData = LineData()
        addAccelerationLineToData(lineData, currentAttempt!!, getString(R.string.drag_compare_legend_current), CURRENT_LINE_COLOR, true)
        addAccelerationLineToData(lineData, compareAttempt!!, getString(R.string.drag_compare_legend_compare), COMPARE_LINE_COLOR, false)
        
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
                return String.format(Locale.US, "%.2f", value)
            }
        }
        
        // Настройваме X оста - ТОЧНО като в DragSessionDetailsActivity
        val maxTimeFromAllMeasurements = getMaxTimeFromAllMeasurements(currentAttempt!!, compareAttempt!!).toFloat()
        applyChartXPadding(maxTimeFromAllMeasurements)
        
        chart.invalidate()
    }
    
    private fun updateGForceChart() {
        tvChartTitle.text = getString(R.string.compare_chart_gforce)
        
        // Създаваме нов LineData с двете линии
        val lineData = LineData()
        addGForceLineToData(lineData, currentAttempt!!, getString(R.string.drag_compare_legend_current), CURRENT_LINE_COLOR, true)
        addGForceLineToData(lineData, compareAttempt!!, getString(R.string.drag_compare_legend_compare), COMPARE_LINE_COLOR, false)
        
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
        applyChartXPadding(maxTimeFromAllMeasurements)
        
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
                mode = LineDataSet.Mode.LINEAR
                setDrawValues(false)
                setDrawCircles(false)
                isHighlightEnabled = false
                setDrawHighlightIndicators(false)
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
                mode = LineDataSet.Mode.LINEAR
                setDrawValues(false)
                setDrawCircles(false)
                isHighlightEnabled = false
                setDrawHighlightIndicators(false)
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
                mode = LineDataSet.Mode.LINEAR
                setDrawValues(false)
                setDrawCircles(false)
                isHighlightEnabled = false
                setDrawHighlightIndicators(false)
            }
            
            lineData.addDataSet(dataSet)
        }
    }
    
    // Helper functions for getting data (copy from DragSessionDetailsActivity)
    private fun getRawAlignedSpeedData(attempt: DragAttempt): Pair<List<Float>, List<Long>> {
        val speeds = attempt.speedSamples ?: emptyList()
        val times = attempt.speedTimeStamps ?: emptyList()
        val limit = minOf(speeds.size, times.size)
        return speeds.take(limit) to times.take(limit)
    }

    private fun getAlignedSpeedData(attempt: DragAttempt): Pair<List<Float>, List<Long>> {
        val (speedSamples, speedTimes) = getRawAlignedSpeedData(attempt)
        if (speedSamples.isEmpty() || speedTimes.isEmpty()) return speedSamples to speedTimes

        val sanitized = speedSamples.map { sample ->
            if (sample.isFinite()) sample.coerceAtLeast(0f) else 0f
        }

        val trimmed = trimSeriesAfterCutoff(sanitized, speedTimes, attempt)
        return ensureCompareSpeedHitsMilestones(trimmed.first, trimmed.second, attempt)
    }

    private fun ensureCompareSpeedHitsMilestones(
        speeds: List<Float>,
        times: List<Long>,
        attempt: DragAttempt
    ): Pair<List<Float>, List<Long>> {
        var s = speeds
        var t = times
        if (attempt.time0to100 > 0L) {
            val hit = insertCompareMilestone(s, t, attempt.time0to100, 100f)
            s = hit.first
            t = hit.second
        } else if (isStandaloneHundredTo200(attempt)) {
            val hit = insertCompareMilestone(s, t, 0L, 100f)
            s = hit.first
            t = hit.second
        }
        val time200Ns = resolve200MarkerTimeNs(attempt)
        if (time200Ns > 0L) {
            val hit = insertCompareMilestone(s, t, time200Ns, 200f)
            s = hit.first
            t = hit.second
        }
        val time402Ns = attempt.time0to402.takeIf { it > 0L } ?: attempt.distance402mTimeNs.takeIf { it > 0L }
        val trapSpeedKmh = DragAttemptMetrics.resolveTrapSpeedKmh(attempt)
        if (time402Ns != null && trapSpeedKmh != null) {
            val hit = insertCompareMilestone(s, t, time402Ns, trapSpeedKmh)
            s = hit.first
            t = hit.second
        }
        return s to t
    }

    private fun isStandaloneHundredTo200(attempt: DragAttempt): Boolean {
        return attempt.time100to200 > 0L && attempt.time0to200 <= 0L && attempt.time0to100 <= 0L
    }

    private fun isHundredTo200CompareChart(): Boolean {
        val current = currentAttempt ?: return false
        val compare = compareAttempt ?: return false
        return isStandaloneHundredTo200(current) && isStandaloneHundredTo200(compare)
    }

    private fun resolve200MarkerTimeNs(attempt: DragAttempt): Long {
        return when {
            attempt.time0to200 > 0L -> attempt.time0to200
            attempt.time100to200 > 0L -> attempt.time100to200
            else -> -1L
        }
    }

    private fun insertCompareMilestone(
        speeds: List<Float>,
        times: List<Long>,
        targetTimeNs: Long,
        targetSpeedKmh: Float
    ): Pair<List<Float>, List<Long>> {
        if (speeds.isEmpty() || times.isEmpty() || targetTimeNs < 0L) return speeds to times
        val limit = minOf(speeds.size, times.size)
        val outS = speeds.take(limit).toMutableList()
        val outT = times.take(limit).toMutableList()
        val existing = outT.indexOfFirst { kotlin.math.abs(it - targetTimeNs) <= 20_000_000L }
        if (existing >= 0) {
            outS[existing] = targetSpeedKmh
            outT[existing] = targetTimeNs
            return outS to outT
        }
        val insertAt = outT.indexOfFirst { it > targetTimeNs }.let { if (it >= 0) it else outT.size }
        outT.add(insertAt, targetTimeNs)
        outS.add(insertAt, targetSpeedKmh)
        return outS to outT
    }

    private fun getChartCutoffTimeNs(attempt: DragAttempt): Long? {
        return listOf(
            attempt.time0to100,
            attempt.time0to200,
            attempt.time100to200,
            attempt.time0to402,
            attempt.distance402mTimeNs
        ).filter { it > 0L }.maxOrNull()
    }

    private fun trimSeriesAfterCutoff(
        values: List<Float>,
        timestampsNs: List<Long>,
        attempt: DragAttempt
    ): Pair<List<Float>, List<Long>> {
        val cutoffTimeNs = getChartCutoffTimeNs(attempt) ?: return values to timestampsNs
        return trimSeriesToCutoffTime(values, timestampsNs, cutoffTimeNs)
    }

    private fun trimSeriesToCutoffTime(
        values: List<Float>,
        timestampsNs: List<Long>,
        cutoffTimeNs: Long
    ): Pair<List<Float>, List<Long>> {
        val limit = minOf(values.size, timestampsNs.size)
        if (limit <= 0 || cutoffTimeNs <= 0L) return values.take(limit) to timestampsNs.take(limit)

        val sourceValues = values.take(limit)
        val sourceTimes = timestampsNs.take(limit)
        val trimmedValues = mutableListOf<Float>()
        val trimmedTimes = mutableListOf<Long>()

        for (i in sourceTimes.indices) {
            val timeNs = sourceTimes[i]
            val value = sourceValues[i]

            if (timeNs <= cutoffTimeNs) {
                trimmedTimes.add(timeNs)
                trimmedValues.add(value)
                continue
            }

            if (i > 0 && trimmedTimes.isNotEmpty() && trimmedTimes.last() < cutoffTimeNs) {
                val prevTimeNs = sourceTimes[i - 1]
                val prevValue = sourceValues[i - 1]
                val dtNs = timeNs - prevTimeNs

                if (dtNs > 0L) {
                    val ratio = ((cutoffTimeNs - prevTimeNs).toDouble() / dtNs.toDouble()).coerceIn(0.0, 1.0)
                    val cutoffValue = prevValue + ((value - prevValue) * ratio.toFloat())
                    trimmedTimes.add(cutoffTimeNs)
                    trimmedValues.add(cutoffValue)
                }
            }

            break
        }

        if (trimmedValues.isEmpty() || trimmedTimes.isEmpty()) {
            return sourceValues to sourceTimes
        }

        return trimmedValues to trimmedTimes
    }
    
    private fun getAlignedAccelData(attempt: DragAttempt): Pair<List<Float>, List<Long>> {
        // Primary source: persisted live acceleration display values (same as details screen).
        val liveVals = attempt.liveAccelDisplaySamples
        val liveTimes = attempt.liveAccelDisplayTimeStamps
        val liveLimit = minOf(liveVals.size, liveTimes.size)
        if (liveLimit > 1) {
            val alignedTimes = liveTimes.take(liveLimit)
            val accelG = liveVals.take(liveLimit)
                .map { if (it.isFinite()) it.coerceAtLeast(0f) else 0f }
            return trimSeriesAfterCutoff(accelG, alignedTimes, attempt)
        }

        val imuVals = attempt.longitudinalAccelSamples
        val imuTimes = attempt.longitudinalAccelTimeStamps
        val imuLimit = minOf(imuVals.size, imuTimes.size)
        if (imuLimit > 1) {
            val alignedTimes = imuTimes.take(imuLimit)
            val accelG = imuVals.take(imuLimit)
                .map { (it * MPS2_TO_G).coerceIn(-ACCEL_DISPLAY_CLAMP_G, ACCEL_DISPLAY_CLAMP_G) }

            val smoothed = smoothSeriesForChart(
                values = accelG,
                timestampsNs = alignedTimes,
                maxDeltaPerSecond = ACCEL_DISPLAY_MAX_DELTA_G_PER_SEC,
                emaAlpha = ACCEL_DISPLAY_EMA_ALPHA,
                medianPasses = 2
            ).map { value -> if (kotlin.math.abs(value) < 0.03f) 0f else value }
            return trimSeriesAfterCutoff(smoothed, alignedTimes, attempt)
        }

        val (speedSamples, speedTimes) = getAlignedSpeedData(attempt)
        val derived = deriveAccelerationFromSpeedSamples(speedSamples, speedTimes)
        if (derived.first.isNotEmpty() && derived.second.isNotEmpty()) {
            val derivedG = derived.first.map { value -> (value * MPS2_TO_G).coerceIn(-ACCEL_DISPLAY_CLAMP_G, ACCEL_DISPLAY_CLAMP_G) }
            val smoothed = smoothSeriesForChart(
                values = derivedG,
                timestampsNs = derived.second,
                maxDeltaPerSecond = ACCEL_DISPLAY_MAX_DELTA_G_PER_SEC,
                emaAlpha = ACCEL_DISPLAY_EMA_ALPHA,
                medianPasses = 2
            ).map { value -> if (kotlin.math.abs(value) < 0.03f) 0f else value }
            return trimSeriesAfterCutoff(smoothed, derived.second, attempt)
        }

        val vals = attempt.gpsAccelSamples ?: emptyList()
        val times = attempt.gpsTimeStamps ?: emptyList()
        val limit = minOf(vals.size, times.size)
        val alignedTimes = times.take(limit)
        val accelG = vals.take(limit).map { (it * MPS2_TO_G).coerceIn(-ACCEL_DISPLAY_CLAMP_G, ACCEL_DISPLAY_CLAMP_G) }
        if (accelG.isEmpty() || alignedTimes.isEmpty()) return accelG to alignedTimes

        val smoothed = smoothSeriesForChart(
            values = accelG,
            timestampsNs = alignedTimes,
            maxDeltaPerSecond = ACCEL_DISPLAY_MAX_DELTA_G_PER_SEC,
            emaAlpha = ACCEL_DISPLAY_EMA_ALPHA,
            medianPasses = 2
        ).map { value -> if (kotlin.math.abs(value) < 0.03f) 0f else value }

        return trimSeriesAfterCutoff(smoothed, alignedTimes, attempt)
    }

    private fun smoothSeriesForChart(
        values: List<Float>,
        timestampsNs: List<Long>,
        maxDeltaPerSecond: Float,
        emaAlpha: Float,
        medianPasses: Int
    ): List<Float> {
        if (values.size < 3 || values.size != timestampsNs.size) return values

        val bounded = ArrayList<Float>(values.size)
        var previous = values.first()
        bounded.add(previous)

        for (i in 1 until values.size) {
            val raw = values[i]
            val dtSec = ((timestampsNs[i] - timestampsNs[i - 1]).coerceAtLeast(20_000_000L)) / 1_000_000_000f
            val maxDelta = maxDeltaPerSecond * dtSec
            val clipped = raw.coerceIn(previous - maxDelta, previous + maxDelta)
            bounded.add(clipped)
            previous = clipped
        }

        var filtered: List<Float> = bounded
        repeat(medianPasses.coerceAtLeast(0)) {
            filtered = medianFilterPass(filtered)
        }

        return exponentialSmoothingPass(filtered, emaAlpha)
    }

    private fun medianFilterPass(values: List<Float>): List<Float> {
        if (values.size < 3) return values

        val out = values.toMutableList()
        for (i in 1 until values.lastIndex) {
            val a = values[i - 1]
            val b = values[i]
            val c = values[i + 1]
            out[i] = when {
                (a <= b && b <= c) || (c <= b && b <= a) -> b
                (b <= a && a <= c) || (c <= a && a <= b) -> a
                else -> c
            }
        }
        return out
    }

    private fun exponentialSmoothingPass(values: List<Float>, alpha: Float): List<Float> {
        if (values.isEmpty()) return values
        val safeAlpha = alpha.coerceIn(0.01f, 1f)
        val out = ArrayList<Float>(values.size)

        var previous = values.first()
        out.add(previous)

        for (i in 1 until values.size) {
            val current = previous + safeAlpha * (values[i] - previous)
            out.add(current)
            previous = current
        }

        return out
    }

    private fun deriveAccelerationFromSpeedSamples(
        speedSamplesKmh: List<Float>,
        speedTimestampsNs: List<Long>
    ): Pair<List<Float>, List<Long>> {
        val limit = minOf(speedSamplesKmh.size, speedTimestampsNs.size)
        if (limit < 2) return emptyList<Float>() to emptyList<Long>()

        val accelValues = ArrayList<Float>(limit - 1)
        val accelTimes = ArrayList<Long>(limit - 1)

        for (i in 1 until limit) {
            val t0 = speedTimestampsNs[i - 1]
            val t1 = speedTimestampsNs[i]
            val dtNs = t1 - t0
            if (dtNs <= 0L) continue

            val dtSec = dtNs / 1_000_000_000f
            if (dtSec <= 0f) continue

            val v0Mps = speedSamplesKmh[i - 1] * KMH_TO_MPS
            val v1Mps = speedSamplesKmh[i] * KMH_TO_MPS
            val accelMps2 = (v1Mps - v0Mps) / dtSec
            if (!accelMps2.isFinite()) continue

            accelValues.add(accelMps2)
            accelTimes.add(t1)
        }

        return accelValues to accelTimes
    }
    
    private fun getAlignedGData(attempt: DragAttempt): Pair<List<Float>, List<Long>> {
        val liveVals = attempt.liveAccelDisplaySamples
        val liveTimes = attempt.liveAccelDisplayTimeStamps
        val liveLimit = minOf(liveVals.size, liveTimes.size)
        val raw = if (liveLimit > 1) {
            val alignedTimes = liveTimes.take(liveLimit)
            val liveSeries = liveVals.take(liveLimit)
                .map { if (it.isFinite()) it.coerceAtLeast(0f) else 0f }
            liveSeries to alignedTimes
        } else {
            val imuVals = attempt.longitudinalAccelSamples
            val imuTimes = attempt.longitudinalAccelTimeStamps
            val imuLimit = minOf(imuVals.size, imuTimes.size)
            if (imuLimit > 1) {
                val alignedTimes = imuTimes.take(imuLimit)
                val longitudinalG = imuVals.take(imuLimit)
                    .map { (it * MPS2_TO_G).coerceIn(-ACCEL_DISPLAY_CLAMP_G, ACCEL_DISPLAY_CLAMP_G) }
                longitudinalG to alignedTimes
            } else {
                val gValues = attempt.gSamples ?: emptyList()
                val gTimestamps = attempt.timeStamps ?: emptyList()
                val limit = minOf(gValues.size, gTimestamps.size)
                if (limit == 0) {
                    emptyList<Float>() to emptyList()
                } else {
                    val sanitizedValues = gValues.take(limit).map { value ->
                        when {
                            value.isNaN() || value.isInfinite() -> 0f
                            else -> value
                        }
                    }
                    sanitizedValues to gTimestamps.take(limit)
                }
            }
        }
        return smoothGChartSeries(raw)
    }

    private fun smoothGChartSeries(series: Pair<List<Float>, List<Long>>): Pair<List<Float>, List<Long>> {
        val (values, times) = series
        val limit = minOf(values.size, times.size)
        if (limit < 3) return values.take(limit) to times.take(limit)
        val alignedValues = values.take(limit)
        val alignedTimes = times.take(limit)
        return TrackGForceChartSmoothing.smoothGSeriesForChartNs(alignedValues, alignedTimes) to alignedTimes
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
    
    private fun setupChartZoom(chart: LineChart) {
        var isZooming = false
        var zoomCenterX = 0f

        val scaleGestureDetector = ScaleGestureDetector(chart.context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isZooming = true
                zoomCenterX = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val deltaX = kotlin.math.abs(detector.currentSpanX - detector.previousSpanX)
                val deltaY = kotlin.math.abs(detector.currentSpanY - detector.previousSpanY)

                val scaleFactorX = if (deltaX > deltaY * 1.5) detector.scaleFactor else 1f
                val scaleFactorY = if (deltaY > deltaX * 1.5) detector.scaleFactor else 1f

                if (deltaX <= deltaY * 1.5 && deltaY <= deltaX * 1.5) {
                    chart.zoom(
                        detector.scaleFactor, detector.scaleFactor,
                        chart.width / 2f, chart.height / 2f,
                        YAxis.AxisDependency.LEFT
                    )
                } else {
                    chart.zoom(
                        scaleFactorX, scaleFactorY,
                        chart.width / 2f, chart.height / 2f,
                        YAxis.AxisDependency.LEFT
                    )
                }

                var targetX = zoomCenterX - chart.visibleXRange / 2f
                val visibleRange = chart.visibleXRange
                val dataStart = getChartDataStart()
                val dataEnd = getChartDataEnd()
                val centerAfterMove = targetX + visibleRange / 2f
                if (centerAfterMove < dataStart) {
                    targetX = dataStart - visibleRange / 2f
                } else if (centerAfterMove > dataEnd) {
                    targetX = dataEnd - visibleRange / 2f
                }
                chart.moveViewToX(targetX)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isZooming = false
                applyCompareChartReader()
            }
        })

        chart.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)

            if (!isZooming) {
                chart.onTouchEvent(event)
                clampCompareChartCenter()
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    chart.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    chart.parent?.requestDisallowInterceptTouchEvent(false)
                    applyCompareChartReader()
                }
            }

            true
        }

        chart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                applyCompareChartReader()
            }

            override fun onNothingSelected() {
                applyCompareChartReader()
            }
        })

        chart.setOnChartGestureListener(object : OnChartGestureListener {
            override fun onChartGestureStart(me: MotionEvent?, lastGesture: ChartTouchListener.ChartGesture?) {
                chart.parent?.requestDisallowInterceptTouchEvent(true)
            }
            override fun onChartGestureEnd(me: MotionEvent?, lastGesture: ChartTouchListener.ChartGesture?) {
                chart.parent?.requestDisallowInterceptTouchEvent(false)
                applyCompareChartReader()
            }
            override fun onChartLongPressed(me: MotionEvent?) {}
            override fun onChartDoubleTapped(me: MotionEvent?) {
                showFullCompareChart()
            }
            override fun onChartSingleTapped(me: MotionEvent?) {}
            override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}
            override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {}
            override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
                if (!isZooming) {
                    clampCompareChartCenter()
                    applyCompareChartReader()
                }
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
        if (attempt.time0to100 > 0L) {
            val time100 = attempt.time0to100 / 1_000_000_000.0f
            val y100 = compareMilestoneY(attempt, PointType.SPEED_100, mode, time100)
            specialPoints.add(SpecialPoint(time100, y100, PointType.SPEED_100, isCurrent, time100))
        }

        val time200Ns = resolve200MarkerTimeNs(attempt)
        if (time200Ns > 0L) {
            val time200 = time200Ns / 1_000_000_000.0f
            val y200 = compareMilestoneY(attempt, PointType.SPEED_200, mode, time200)
            specialPoints.add(SpecialPoint(time200, y200, PointType.SPEED_200, isCurrent, time200))
        }
        
        // 0-402m
        if (attempt.time0to402 > 0) {
            val time402 = attempt.time0to402 / 1_000_000_000.0f
            if (time402 > 0f) {
                val y402 = compareMilestoneY(attempt, PointType.DISTANCE_402, mode, time402)
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
        val (speedSamples, timestamps) = getAlignedSpeedData(attempt)
        if (speedSamples.isEmpty() || timestamps.isEmpty()) return

        if (attempt.time0to100 > 0L) {
            addCompareMilestoneCircle(
                lineData,
                attempt.time0to100 / 1_000_000_000.0f,
                compareMilestoneY(attempt, PointType.SPEED_100, currentMode, attempt.time0to100 / 1_000_000_000.0f),
                ContextCompat.getColor(this, R.color.accent_green)
            )
        }

        val time200Ns = resolve200MarkerTimeNs(attempt)
        if (time200Ns > 0L) {
            val time200 = time200Ns / 1_000_000_000.0f
            addCompareMilestoneCircle(
                lineData,
                time200,
                compareMilestoneY(attempt, PointType.SPEED_200, currentMode, time200),
                ContextCompat.getColor(this, R.color.accent_blue)
            )
        }

        if (attempt.time0to402 > 0) {
            val time402Seconds = attempt.time0to402 / 1_000_000_000.0f
            addCompareMilestoneCircle(
                lineData,
                time402Seconds,
                compareMilestoneY(attempt, PointType.DISTANCE_402, currentMode, time402Seconds),
                ContextCompat.getColor(this, R.color.accent_red)
            )
        }
    }

    private fun addCompareMilestoneCircle(
        lineData: LineData,
        timeSeconds: Float,
        value: Float,
        circleColor: Int
    ) {
        val dataSet = LineDataSet(listOf(Entry(timeSeconds, value)), "").apply {
            setDrawValues(false)
            setDrawCircles(true)
            setDrawFilled(false)
            lineWidth = 0f
            isHighlightEnabled = false
            setDrawHighlightIndicators(false)
            setCircleColor(circleColor)
            circleRadius = 8f
            circleHoleRadius = 4f
            form = com.github.mikephil.charting.components.Legend.LegendForm.NONE
        }
        dataSet.color = android.graphics.Color.parseColor("#3c4040")
        lineData.addDataSet(dataSet)
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
            val valueAt402 = compareMilestoneY(attempt, PointType.DISTANCE_402, currentMode, time402Seconds)
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
    
    private fun compareMilestoneY(
        attempt: DragAttempt,
        type: PointType,
        mode: ChartMode,
        timeSeconds: Float
    ): Float {
        if (mode != ChartMode.SPEED) {
            return findValueAtTimeInterpolated(attempt, timeSeconds, mode)
        }
        val speedKmh = when (type) {
            PointType.SPEED_100 -> 100f
            PointType.SPEED_200 -> 200f
            PointType.DISTANCE_402 -> attempt.distance402mSpeedKmh.takeIf { it > 0f }
                ?: attempt.maxSpeed.takeIf { it > 0f }
        } ?: return findValueAtTimeInterpolated(attempt, timeSeconds, mode)
        return UnitsManager.convertSpeed(speedKmh, UnitsManager.getSpeedUnit(this))
    }

    private fun findValueAtTimeInterpolated(attempt: DragAttempt, targetTimeSeconds: Float, mode: ChartMode): Float {
        return when (mode) {
            ChartMode.SPEED -> {
                val (speedSamples, timestamps) = getAlignedSpeedData(attempt)
                val rawKmh = interpolateValueAtTime(speedSamples, timestamps, targetTimeSeconds)
                UnitsManager.convertSpeed(rawKmh, UnitsManager.getSpeedUnit(this))
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

        val absoluteTimes = timestamps.map { it / 1_000_000_000f }

        // Намираме двете съседни точки в абсолютното времево пространство
        for (i in 1 until absoluteTimes.size) {
            val t0 = absoluteTimes[i - 1]
            val t1 = absoluteTimes[i]

            if (targetTimeSeconds >= t0 && targetTimeSeconds <= t1) {
                val v0 = values[i - 1]
                val v1 = values[i]

                // Линейна интерполация
                val ratio = (targetTimeSeconds - t0) / (t1 - t0)
                return v0 + (v1 - v0) * ratio
            }
        }

        // Ако времето е извън диапазона, връщаме първата/последната стойност.
        return when {
            targetTimeSeconds < absoluteTimes.first() -> values.first()
            targetTimeSeconds > absoluteTimes.last() -> values.last()
            else -> values.lastOrNull() ?: 0f
        }
    }
}

// SmartMarker клас за показване на балончета точно като в нормалната графика
class SmartMarker(
    context: Context,
    layoutResource: Int,
    private val hostChart: LineChart
) : com.github.mikephil.charting.components.MarkerView(context, layoutResource) {
    
    private val KMH_TO_MPS = 1f / 3.6f
    private val MPS2_TO_G = 1f / 9.81f
    private val ACCEL_DISPLAY_MAX_DELTA_G_PER_SEC = 7f
    private val ACCEL_DISPLAY_EMA_ALPHA = 0.22f
    private val ACCEL_DISPLAY_CLAMP_G = 3.5f

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

    fun applySnap(
        type: CompareAttemptsActivity.PointType,
        value: Float,
        time: Float,
        onCurrentLine: Boolean,
        chartMode: CompareAttemptsActivity.ChartMode
    ) {
        shouldShow = true
        isOnSpecialPoint = true
        pointType = type
        actualValue = value
        exactTime = time
        isOnCurrentLine = onCurrentLine
        mode = chartMode
    }

    fun hideSnap() {
        shouldShow = false
        isOnSpecialPoint = false
    }
    
    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        currentEntry = e
        if (e != null) {
            actualValue = e.y
        }
        super.refreshContent(e, highlight)
    }
    
    override fun draw(canvas: Canvas, posX: Float, posY: Float) {
        if (currentEntry == null || !shouldShow) return

        val lineChart = (chartView as? LineChart) ?: hostChart
        val drawX = lineChart.viewPortHandler.contentCenter.x
        val pts = floatArrayOf(0f, actualValue)
        lineChart.getTransformer(YAxis.AxisDependency.LEFT).pointValuesToPixel(pts)
        val drawY = pts[1] 
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        
        // Определяме текста и цвета
        val (text, backgroundColor) = if (isOnSpecialPoint) {
            val timeToShow = exactTime.coerceAtLeast(0f)
            val speedUnit = UnitsManager.getSpeedUnit(markerContext)
            val speed100 = UnitsManager.convertSpeed(100f, speedUnit).toInt()
            val speed200 = UnitsManager.convertSpeed(200f, speedUnit).toInt()
            val attempt = if (isOnCurrentLine) currentAttempt else compareAttempt
            val isRolling200 = attempt != null &&
                attempt.time100to200 > 0L &&
                attempt.time0to200 <= 0L
            val typeText = when (pointType) {
                CompareAttemptsActivity.PointType.SPEED_100 ->
                    if (isRolling200) {
                        "$speed100 ${speedUnit.symbol}\n${String.format("%.2f", timeToShow)}s"
                    } else {
                        "0-$speed100 ${speedUnit.symbol}\n${String.format("%.2f", timeToShow)}s"
                    }
                CompareAttemptsActivity.PointType.SPEED_200 ->
                    if (isRolling200) {
                        "$speed100-$speed200 ${speedUnit.symbol}\n${String.format("%.2f", timeToShow)}s"
                    } else {
                        "0-$speed200 ${speedUnit.symbol}\n${String.format("%.2f", timeToShow)}s"
                    }
                CompareAttemptsActivity.PointType.DISTANCE_402 -> {
                    // Използваме правилния attempt според линията
                    val attempt = if (isOnCurrentLine) currentAttempt else compareAttempt
                    val trapSpeedKmh = resolveTrapSpeedKmh(attempt)
                    val convertedTrapSpeed = UnitsManager.convertSpeed(trapSpeedKmh, speedUnit)
                    "0-${UnitsManager.getQuarterMileDistance(markerContext)}\n${convertedTrapSpeed.toInt()} ${speedUnit.symbol}\n${String.format("%.2f", timeToShow)}s"
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
                CompareAttemptsActivity.ChartMode.SPEED -> "${actualValue.toInt()} ${UnitsManager.getSpeedUnitSymbol(markerContext)}"
                CompareAttemptsActivity.ChartMode.ACCELERATION -> String.format("%.2f g", actualValue)
                CompareAttemptsActivity.ChartMode.G_FORCE -> String.format("%.2f G", actualValue)
            }
            val lineColor = if (isOnCurrentLine) {
                when (mode) {
                    CompareAttemptsActivity.ChartMode.SPEED -> 0xFFFF6020.toInt()
                    CompareAttemptsActivity.ChartMode.ACCELERATION -> 0xFFFF6020.toInt()
                    CompareAttemptsActivity.ChartMode.G_FORCE -> 0xFFFF6020.toInt()
                }
            } else {
                0xFFA64CEB.toInt() // Лилаво за Compare
            }
            Pair("$valueFormatted\n${String.format("%.2f", timeAtPoint)}s", lineColor)
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
        val arrow = 12f
        val contentTop = lineChart.viewPortHandler.contentTop()
        val contentBottom = lineChart.viewPortHandler.contentBottom()
        val aboveY = drawY - rectHeight - arrow
        val drawBelow = aboveY < contentTop
        val balloonX = drawX - rectWidth / 2
        val balloonY = if (drawBelow) {
            (drawY + arrow).coerceAtMost((contentBottom - rectHeight).coerceAtLeast(contentTop))
        } else {
            aboveY
        }
        
        val rect = android.graphics.RectF(balloonX, balloonY, balloonX + rectWidth, balloonY + rectHeight)
        canvas.drawRoundRect(rect, 12f, 12f, paint)
        
        if (text.contains("\n")) {
            val lines = text.split("\n")
            val lineHeight = textBounds.height() + 4f
            val startY = balloonY + textBounds.height() + padding / 2
            
            lines.forEachIndexed { index, line ->
                val y = startY + (index * lineHeight)
                canvas.drawText(line, drawX, y, textPaint)
            }
        } else {
            canvas.drawText(text, drawX, balloonY + textBounds.height() + padding / 2, textPaint)
        }
        
        val path = android.graphics.Path()
        if (drawBelow) {
            path.moveTo(drawX - 8f, balloonY)
            path.lineTo(drawX + 8f, balloonY)
            path.lineTo(drawX, balloonY - arrow)
        } else {
            path.moveTo(drawX - 8f, balloonY + rectHeight)
            path.lineTo(drawX + 8f, balloonY + rectHeight)
            path.lineTo(drawX, balloonY + rectHeight + arrow)
        }
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

    private fun resolveTrapSpeedKmh(attempt: DragAttempt?): Float {
        attempt ?: return 0f
        return DragAttemptMetrics.resolveTrapSpeedKmh(attempt) ?: 0f
    }
    
    private fun getAlignedDataForMode(attempt: DragAttempt, mode: CompareAttemptsActivity.ChartMode): Pair<List<Float>, List<Long>> {
        return when (mode) {
            CompareAttemptsActivity.ChartMode.SPEED -> getAlignedSpeedData(attempt)
            CompareAttemptsActivity.ChartMode.ACCELERATION -> {
                getAlignedAccelData(attempt)
            }
            CompareAttemptsActivity.ChartMode.G_FORCE -> {
                val gs = attempt.gSamples ?: emptyList()
                val times = attempt.timeStamps ?: emptyList()
                val limit = minOf(gs.size, times.size)
                val alignedGs = gs.take(limit)
                val alignedTimes = times.take(limit)
                TrackGForceChartSmoothing.smoothGSeriesForChartNs(alignedGs, alignedTimes) to alignedTimes
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
    
    private fun getRawAlignedSpeedData(attempt: DragAttempt): Pair<List<Float>, List<Long>> {
        val speeds = attempt.speedSamples ?: emptyList()
        val times = attempt.speedTimeStamps ?: emptyList()
        val limit = minOf(speeds.size, times.size)
        return speeds.take(limit) to times.take(limit)
    }

    private fun getAlignedSpeedData(attempt: DragAttempt): Pair<List<Float>, List<Long>> {
        val (speedSamples, speedTimes) = getRawAlignedSpeedData(attempt)
        if (speedSamples.isEmpty() || speedTimes.isEmpty()) return speedSamples to speedTimes

        val sanitized = speedSamples.map { sample ->
            if (sample.isFinite()) sample.coerceAtLeast(0f) else 0f
        }

        return sanitized to speedTimes
    }

    private fun getAlignedAccelData(attempt: DragAttempt): Pair<List<Float>, List<Long>> {
        val imuVals = attempt.longitudinalAccelSamples
        val imuTimes = attempt.longitudinalAccelTimeStamps
        val imuLimit = minOf(imuVals.size, imuTimes.size)
        if (imuLimit > 1) {
            val alignedTimes = imuTimes.take(imuLimit)
            val accelG = imuVals.take(imuLimit)
                .map { (it * MPS2_TO_G).coerceIn(-ACCEL_DISPLAY_CLAMP_G, ACCEL_DISPLAY_CLAMP_G) }

            val smoothed = smoothSeriesForChart(
                values = accelG,
                timestampsNs = alignedTimes,
                maxDeltaPerSecond = ACCEL_DISPLAY_MAX_DELTA_G_PER_SEC,
                emaAlpha = ACCEL_DISPLAY_EMA_ALPHA,
                medianPasses = 2
            ).map { value -> if (kotlin.math.abs(value) < 0.03f) 0f else value }
            return smoothed to alignedTimes
        }

        val (speedSamples, speedTimes) = getAlignedSpeedData(attempt)
        val derived = deriveAccelerationFromSpeedSamples(speedSamples, speedTimes)
        if (derived.first.isNotEmpty() && derived.second.isNotEmpty()) {
            val smoothed = smoothSeriesForChart(
                values = derived.first.map { value -> value.coerceIn(-ACCEL_DISPLAY_CLAMP_G, ACCEL_DISPLAY_CLAMP_G) },
                timestampsNs = derived.second,
                maxDeltaPerSecond = ACCEL_DISPLAY_MAX_DELTA_G_PER_SEC,
                emaAlpha = ACCEL_DISPLAY_EMA_ALPHA,
                medianPasses = 2
            ).map { value -> if (kotlin.math.abs(value) < 0.03f) 0f else value }
            return smoothed to derived.second
        }

        val accels = attempt.gpsAccelSamples ?: emptyList()
        val times = attempt.gpsTimeStamps ?: emptyList()
        val limit = minOf(accels.size, times.size)
        val alignedTimes = times.take(limit)
        val accelG = accels.take(limit).map { (it * MPS2_TO_G).coerceIn(-ACCEL_DISPLAY_CLAMP_G, ACCEL_DISPLAY_CLAMP_G) }
        if (accelG.isEmpty() || alignedTimes.isEmpty()) return accelG to alignedTimes

        val smoothed = smoothSeriesForChart(
            values = accelG,
            timestampsNs = alignedTimes,
            maxDeltaPerSecond = ACCEL_DISPLAY_MAX_DELTA_G_PER_SEC,
            emaAlpha = ACCEL_DISPLAY_EMA_ALPHA,
            medianPasses = 2
        ).map { value -> if (kotlin.math.abs(value) < 0.03f) 0f else value }

        return smoothed to alignedTimes
    }

    private fun smoothSeriesForChart(
        values: List<Float>,
        timestampsNs: List<Long>,
        maxDeltaPerSecond: Float,
        emaAlpha: Float,
        medianPasses: Int
    ): List<Float> {
        if (values.size < 3 || values.size != timestampsNs.size) return values

        val bounded = ArrayList<Float>(values.size)
        var previous = values.first()
        bounded.add(previous)

        for (i in 1 until values.size) {
            val raw = values[i]
            val dtSec = ((timestampsNs[i] - timestampsNs[i - 1]).coerceAtLeast(20_000_000L)) / 1_000_000_000f
            val maxDelta = maxDeltaPerSecond * dtSec
            val clipped = raw.coerceIn(previous - maxDelta, previous + maxDelta)
            bounded.add(clipped)
            previous = clipped
        }

        var filtered: List<Float> = bounded
        repeat(medianPasses.coerceAtLeast(0)) {
            filtered = medianFilterPass(filtered)
        }

        return exponentialSmoothingPass(filtered, emaAlpha)
    }

    private fun medianFilterPass(values: List<Float>): List<Float> {
        if (values.size < 3) return values

        val out = values.toMutableList()
        for (i in 1 until values.lastIndex) {
            val a = values[i - 1]
            val b = values[i]
            val c = values[i + 1]
            out[i] = when {
                (a <= b && b <= c) || (c <= b && b <= a) -> b
                (b <= a && a <= c) || (c <= a && a <= b) -> a
                else -> c
            }
        }
        return out
    }

    private fun exponentialSmoothingPass(values: List<Float>, alpha: Float): List<Float> {
        if (values.isEmpty()) return values
        val safeAlpha = alpha.coerceIn(0.01f, 1f)
        val out = ArrayList<Float>(values.size)

        var previous = values.first()
        out.add(previous)

        for (i in 1 until values.size) {
            val current = previous + safeAlpha * (values[i] - previous)
            out.add(current)
            previous = current
        }

        return out
    }

    private fun deriveAccelerationFromSpeedSamples(
        speedSamplesKmh: List<Float>,
        speedTimestampsNs: List<Long>
    ): Pair<List<Float>, List<Long>> {
        val limit = minOf(speedSamplesKmh.size, speedTimestampsNs.size)
        if (limit < 2) return emptyList<Float>() to emptyList<Long>()

        val accelValues = ArrayList<Float>(limit - 1)
        val accelTimes = ArrayList<Long>(limit - 1)

        for (i in 1 until limit) {
            val t0 = speedTimestampsNs[i - 1]
            val t1 = speedTimestampsNs[i]
            val dtNs = t1 - t0
            if (dtNs <= 0L) continue

            val dtSec = dtNs / 1_000_000_000f
            if (dtSec <= 0f) continue

            val v0Mps = speedSamplesKmh[i - 1] * KMH_TO_MPS
            val v1Mps = speedSamplesKmh[i] * KMH_TO_MPS
            val accelMps2 = (v1Mps - v0Mps) / dtSec
            if (!accelMps2.isFinite()) continue

            accelValues.add(accelMps2 * MPS2_TO_G)
            accelTimes.add(t1)
        }

        return accelValues to accelTimes
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
