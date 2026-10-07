package com.revix.app

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.revix.app.drag.DragAttemptHudExport
import com.revix.app.drag.DragAttemptVideoActivity
import com.revix.app.drag.DragAttemptMetrics
import com.revix.app.drag.DragVideoOverlayService
import com.revix.app.track.TrackGForceChartSmoothing
import com.revix.app.utils.DragTimeFormatter
import com.revix.app.drag.MeasurementMode
import com.revix.app.drag.PointTooltipMarker
import com.revix.app.drag.SessionSelectionActivity
import com.revix.app.settings.LanguageManager
import com.revix.app.settings.UnitsManager
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.renderer.LineChartRenderer
import com.github.mikephil.charting.renderer.XAxisRenderer
import com.github.mikephil.charting.utils.MPPointF
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

class DragSessionDetailsActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private lateinit var tvSessionName: TextView
    private lateinit var tvSessionDate: TextView
    private lateinit var tvBest0to100: TextView
    private lateinit var tvBest0to200: TextView
    private lateinit var tvBest100to200: TextView
    private lateinit var tvBest0to402: TextView
    private lateinit var tvBestMeta0to100: TextView
    private lateinit var tvBestMeta0to200: TextView
    private lateinit var tvBestMeta100to200: TextView
    private lateinit var tvBestMeta0to402: TextView
    private lateinit var cvBestTimes: View
    private lateinit var cvSingleSessionBest: View
    private lateinit var tvSessionBestLabel: TextView
    private lateinit var tvSessionBestValue: TextView
    private lateinit var tvSessionBestValueUnit: TextView
    private lateinit var tvSessionBestAtSpeedLabel: TextView
    private lateinit var tvSessionBestAtSpeedValue: TextView
    private lateinit var tvSessionBestPeakGLabel: TextView
    private lateinit var tvSessionBestPeakGValue: TextView
    private lateinit var tvSessionBestRunLabel: TextView
    private lateinit var tvSessionBestRunValue: TextView
    private lateinit var tvSessionBestVsPrevPbValue: TextView
    private lateinit var tvSessionBestPrevBestValue: TextView
    private lateinit var tvSessionBestAvgAllRunsValue: TextView
    private lateinit var tvSessionBestConsistencyValue: TextView
    
    private lateinit var tvLabelBest0to100: TextView
    private lateinit var tvLabelBest0to200: TextView
    private lateinit var tvLabelBest100to200: TextView
    private lateinit var tvLabelBest0to402: TextView
    private lateinit var tvRunsCount: TextView
    private lateinit var rvAttempts: RecyclerView
    private lateinit var tvNoAttempts: TextView

    private var session: DragSession? = null
    private var sessionId: Long = -1L
    private var selectedAttemptId: Long = -1L
    private var selectedAttemptIndex: Int = -1
    private var isAttemptDetailsMode: Boolean = false
    private lateinit var attemptsAdapter: DragAttemptsAdapter
    private var measurementMode: MeasurementMode = MeasurementMode.ALL
    @Volatile
    private var closestTo200Normalized: Float? = null // За 200 km/h маркер в 100-200 режим

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drag_session_details)

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

        selectedAttemptId = intent.getLongExtra("ATTEMPT_ID", -1L)
        if (selectedAttemptId > 0L) {
            selectedAttemptIndex = session?.attempts?.indexOfFirst { it.id == selectedAttemptId } ?: -1
            isAttemptDetailsMode = selectedAttemptIndex >= 0
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

    private fun initializeViews() {
        tvSessionName = findViewById(R.id.tvDetailSessionName)
        tvSessionDate = findViewById(R.id.tvDetailSessionDate)
        tvBest0to100 = findViewById(R.id.tvDetailBest0to100)
        tvBest0to200 = findViewById(R.id.tvDetailBest0to200)
        tvBest100to200 = findViewById(R.id.tvDetailBest100to200)
        tvBest0to402 = findViewById(R.id.tvDetailBest0to402)
        tvBestMeta0to100 = findViewById(R.id.tvBestMeta0to100)
        tvBestMeta0to200 = findViewById(R.id.tvBestMeta0to200)
        tvBestMeta100to200 = findViewById(R.id.tvBestMeta100to200)
        tvBestMeta0to402 = findViewById(R.id.tvBestMeta0to402)
        cvBestTimes = findViewById(R.id.cvBestTimes)
        cvSingleSessionBest = findViewById(R.id.cvSingleSessionBest)
        tvSessionBestLabel = findViewById(R.id.tvSessionBestLabel)
        tvSessionBestValue = findViewById(R.id.tvSessionBestValue)
        tvSessionBestValueUnit = findViewById(R.id.tvSessionBestValueUnit)
        tvSessionBestAtSpeedLabel = findViewById(R.id.tvSessionBestAtSpeedLabel)
        tvSessionBestAtSpeedValue = findViewById(R.id.tvSessionBestAtSpeedValue)
        tvSessionBestPeakGLabel = findViewById(R.id.tvSessionBestPeakGLabel)
        tvSessionBestPeakGValue = findViewById(R.id.tvSessionBestPeakGValue)
        tvSessionBestRunLabel = findViewById(R.id.tvSessionBestRunLabel)
        tvSessionBestRunValue = findViewById(R.id.tvSessionBestRunValue)
        tvSessionBestVsPrevPbValue = findViewById(R.id.tvSessionBestVsPrevPbValue)
        tvSessionBestPrevBestValue = findViewById(R.id.tvSessionBestPrevBestValue)
        tvSessionBestAvgAllRunsValue = findViewById(R.id.tvSessionBestAvgAllRunsValue)
        tvSessionBestConsistencyValue = findViewById(R.id.tvSessionBestConsistencyValue)
        
        tvLabelBest0to100 = findViewById(R.id.tvLabelBest0to100)
        tvLabelBest0to200 = findViewById(R.id.tvLabelBest0to200)
        tvLabelBest100to200 = findViewById(R.id.tvLabelBest100to200)
        tvLabelBest0to402 = findViewById(R.id.tvLabelBest0to402)
        tvRunsCount = findViewById(R.id.tvRunsCount)
        
        // Update labels with current unit
        val speedUnit = UnitsManager.getSpeedUnit(this)
        val speed100 = UnitsManager.convertSpeed(100f, speedUnit).toInt()
        val speed200 = UnitsManager.convertSpeed(200f, speedUnit).toInt()
        tvLabelBest0to100.text = "${UnitsManager.formatDragSpeedIntervalLabel(0, 100, this)}:"
        tvLabelBest0to200.text = "${UnitsManager.formatDragSpeedIntervalLabel(0, 200, this)}:"
        tvLabelBest100to200.text = "${UnitsManager.formatDragSpeedIntervalLabel(100, 200, this)}:"
        tvLabelBest0to402.text = "${UnitsManager.formatDragZeroTo402IntervalLabel(this)}:"
        
        rvAttempts = findViewById(R.id.rvDetailAttempts)
        tvNoAttempts = findViewById(R.id.tvNoAttempts)

        findViewById<View>(R.id.btnBack)?.setOnClickListener {
            finish()
        }
        
        val compareButton = findViewById<View>(R.id.btnCompare)
        if (isAttemptDetailsMode && selectedAttemptId > 0L) {
            compareButton?.visibility = View.VISIBLE
            compareButton?.setOnClickListener {
                openSessionSelectionForCompare(selectedAttemptId)
            }
        } else {
            compareButton?.visibility = View.GONE
        }
    }

    private fun openSessionSelectionForCompare(attemptId: Long) {
        val intent = android.content.Intent(this, SessionSelectionActivity::class.java)
        intent.putExtra("current_session_id", sessionId)
        intent.putExtra("current_attempt_id", attemptId)
        startActivity(intent)
    }

    private fun openAttemptDetails(attemptId: Long) {
        startActivity(android.content.Intent(this, DragSessionDetailsActivity::class.java).apply {
            putExtra("SESSION_ID", sessionId)
            putExtra("ATTEMPT_ID", attemptId)
        })
    }

    private fun displaySessionData() {
        session?.let { s ->
            tvSessionName.text = s.name ?: getString(R.string.drag_session_default_name)
            tvSessionDate.text = SimpleDateFormat(
                "dd.MM.yyyy HH:mm",
                LanguageManager.getCurrentLocale(this)
            ).format(Date(s.timestamp))
            com.revix.app.racebox.RaceBoxSessionUi.bindLabel(
                findViewById(R.id.tvDetailRaceBoxBadge),
                s.recordedWithRaceBox
            )

            if (isAttemptDetailsMode) {
                cvBestTimes.visibility = View.GONE
                cvSingleSessionBest.visibility = View.GONE
                findViewById<TextView>(R.id.tvAttemptsHeader).text =
                    getString(R.string.drag_run_details_title, selectedAttemptIndex + 1)
                tvRunsCount.visibility = View.GONE
                return@let
            }

            tvRunsCount.visibility = View.VISIBLE

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
            cvBestTimes.visibility = View.VISIBLE
            cvSingleSessionBest.visibility = View.GONE

            val speedUnit = UnitsManager.getSpeedUnit(this)
            val speed100 = UnitsManager.convertSpeed(100f, speedUnit).toInt()
            val speed200 = UnitsManager.convertSpeed(200f, speedUnit).toInt()
            val speedSymbol = speedUnit.symbol.uppercase(Locale.getDefault())
            val quarterDistance = UnitsManager.getQuarterMileDistance(this).uppercase(Locale.getDefault())

            when (mode) {
                MeasurementMode.ZERO_TO_100 -> {
                    tvBest0to100.text = formatTimeWithLabel(null,s.best0to100)
                    findViewById<LinearLayout>(R.id.ll0to100).visibility = View.VISIBLE
                    tvSessionBestLabel.text = getString(R.string.drag_session_best_mode_format, "0-$speed100 $speedSymbol")
                    tvSessionBestValue.text = formatHeroTime(s.best0to100)
                    tvSessionBestValue.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                    tvSessionBestValueUnit.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                    val bestAttemptInfo = findBestAttemptForMode(s, mode)
                    val bestAttempt = bestAttemptInfo?.second
                    tvSessionBestAtSpeedLabel.text = getString(R.string.drag_session_best_at_speed_label, "$speed100 $speedSymbol")
                    tvSessionBestAtSpeedValue.text = "$speed100 ${speedUnit.symbol}"
                    tvSessionBestPeakGLabel.text = getString(R.string.drag_session_best_peak_g_label).uppercase(Locale.getDefault())
                    tvSessionBestPeakGValue.text = formatPeakGValue(getPreferredPeakGValue(bestAttempt))
                    tvSessionBestRunLabel.text = getString(R.string.drag_session_best_achieved_in_label).uppercase(Locale.getDefault())
                    tvSessionBestRunValue.text = formatRunValue(bestAttemptInfo?.first)
                    updateSessionBestStats(s, mode)
                    cvBestTimes.visibility = View.GONE
                    cvSingleSessionBest.visibility = View.VISIBLE
                }
                MeasurementMode.ZERO_TO_200 -> {
                    tvBest0to200.text = formatTimeWithLabel(null,s.best0to200)
                    findViewById<LinearLayout>(R.id.ll0to200).visibility = View.VISIBLE
                    tvSessionBestLabel.text = getString(R.string.drag_session_best_mode_format, "0-$speed200 $speedSymbol")
                    tvSessionBestValue.text = formatHeroTime(s.best0to200)
                    tvSessionBestValue.setTextColor(ContextCompat.getColor(this, R.color.accent_blue))
                    tvSessionBestValueUnit.setTextColor(ContextCompat.getColor(this, R.color.accent_blue))
                    val bestAttemptInfo = findBestAttemptForMode(s, mode)
                    val bestAttempt = bestAttemptInfo?.second
                    tvSessionBestAtSpeedLabel.text = getString(R.string.drag_session_best_at_speed_label, "$speed200 $speedSymbol")
                    tvSessionBestAtSpeedValue.text = "$speed200 ${speedUnit.symbol}"
                    tvSessionBestPeakGLabel.text = getString(R.string.drag_session_best_peak_g_label).uppercase(Locale.getDefault())
                    tvSessionBestPeakGValue.text = formatPeakGValue(getPreferredPeakGValue(bestAttempt))
                    tvSessionBestRunLabel.text = getString(R.string.drag_session_best_achieved_in_label).uppercase(Locale.getDefault())
                    tvSessionBestRunValue.text = formatRunValue(bestAttemptInfo?.first)
                    updateSessionBestStats(s, mode)
                    cvBestTimes.visibility = View.GONE
                    cvSingleSessionBest.visibility = View.VISIBLE
                }
                MeasurementMode.HUNDRED_TO_200 -> {
                    tvBest100to200.text = formatTimeWithLabel(null,s.best100to200)
                    findViewById<LinearLayout>(R.id.ll100to200).visibility = View.VISIBLE
                    tvSessionBestLabel.text = getString(R.string.drag_session_best_mode_format, "$speed100-$speed200 $speedSymbol")
                    tvSessionBestValue.text = formatHeroTime(s.best100to200)
                    tvSessionBestValue.setTextColor(ContextCompat.getColor(this, R.color.accent_purple))
                    tvSessionBestValueUnit.setTextColor(ContextCompat.getColor(this, R.color.accent_purple))
                    val bestAttemptInfo = findBestAttemptForMode(s, mode)
                    val bestAttempt = bestAttemptInfo?.second
                    tvSessionBestAtSpeedLabel.text = getString(R.string.drag_session_best_at_speed_label, "$speed200 $speedSymbol")
                    tvSessionBestAtSpeedValue.text = "$speed200 ${speedUnit.symbol}"
                    tvSessionBestPeakGLabel.text = getString(R.string.drag_session_best_peak_g_label).uppercase(Locale.getDefault())
                    tvSessionBestPeakGValue.text = formatPeakGValue(getPreferredPeakGValue(bestAttempt))
                    tvSessionBestRunLabel.text = getString(R.string.drag_session_best_achieved_in_label).uppercase(Locale.getDefault())
                    tvSessionBestRunValue.text = formatRunValue(bestAttemptInfo?.first)
                    updateSessionBestStats(s, mode)
                    cvBestTimes.visibility = View.GONE
                    cvSingleSessionBest.visibility = View.VISIBLE
                }
                MeasurementMode.QUARTER_MILE -> {
                    tvBest0to402.text = formatTimeWithLabel(null,s.best0to402)
                    findViewById<LinearLayout>(R.id.ll0to402).visibility = View.VISIBLE
                    tvSessionBestLabel.text = getString(R.string.drag_session_best_mode_format, "0-$quarterDistance")
                    tvSessionBestValue.text = formatHeroTime(s.best0to402)
                    tvSessionBestValue.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
                    tvSessionBestValueUnit.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
                    val bestAttemptInfo = findBestAttemptForMode(s, mode)
                    val bestAttempt = bestAttemptInfo?.second
                    tvSessionBestAtSpeedLabel.text = getString(R.string.drag_session_best_finish_speed_label).uppercase(Locale.getDefault())
                    tvSessionBestAtSpeedValue.text = formatAttemptSpeed(bestAttempt)
                    tvSessionBestPeakGLabel.text = getString(R.string.drag_session_best_peak_g_label).uppercase(Locale.getDefault())
                    tvSessionBestPeakGValue.text = formatPeakGValue(getPreferredPeakGValue(bestAttempt))
                    tvSessionBestRunLabel.text = getString(R.string.drag_session_best_achieved_in_label).uppercase(Locale.getDefault())
                    tvSessionBestRunValue.text = formatRunValue(bestAttemptInfo?.first)
                    updateSessionBestStats(s, mode)
                    cvBestTimes.visibility = View.GONE
                    cvSingleSessionBest.visibility = View.VISIBLE
                }
                MeasurementMode.ALL -> {
                    bindAllModeBestCard(
                        session = s,
                        metricMode = MeasurementMode.ZERO_TO_100,
                        labelView = tvLabelBest0to100,
                        valueView = tvBest0to100,
                        metaView = tvBestMeta0to100,
                        labelText = "0 - $speed100 $speedSymbol",
                        metricColorRes = R.color.accent_green
                    )
                    bindAllModeBestCard(
                        session = s,
                        metricMode = MeasurementMode.HUNDRED_TO_200,
                        labelView = tvLabelBest100to200,
                        valueView = tvBest100to200,
                        metaView = tvBestMeta100to200,
                        labelText = "$speed100 - $speed200 $speedSymbol",
                        metricColorRes = R.color.accent_purple
                    )
                    bindAllModeBestCard(
                        session = s,
                        metricMode = MeasurementMode.ZERO_TO_200,
                        labelView = tvLabelBest0to200,
                        valueView = tvBest0to200,
                        metaView = tvBestMeta0to200,
                        labelText = "0 - $speed200 $speedSymbol",
                        metricColorRes = R.color.accent_blue
                    )
                    bindAllModeBestCard(
                        session = s,
                        metricMode = MeasurementMode.QUARTER_MILE,
                        labelView = tvLabelBest0to402,
                        valueView = tvBest0to402,
                        metaView = tvBestMeta0to402,
                        labelText = "0 - $quarterDistance",
                        metricColorRes = R.color.accent_red
                    )

                    findViewById<LinearLayout>(R.id.ll0to100).visibility = View.VISIBLE
                    findViewById<LinearLayout>(R.id.ll0to200).visibility = View.VISIBLE
                    findViewById<LinearLayout>(R.id.ll100to200).visibility = View.VISIBLE
                    findViewById<LinearLayout>(R.id.ll0to402).visibility = View.VISIBLE
                    cvBestTimes.visibility = View.VISIBLE
                    cvSingleSessionBest.visibility = View.GONE
                }
            }
        }
    }




    private fun setupRecyclerView() {
        session?.let { s ->
            val attemptsToShow = if (isAttemptDetailsMode) {
                s.attempts.firstOrNull { it.id == selectedAttemptId }?.let { listOf(it) } ?: emptyList()
            } else {
                s.attempts
            }

            if (!isAttemptDetailsMode) {
                tvRunsCount.text = resources.getQuantityString(
                    R.plurals.drag_runs_count,
                    attemptsToShow.size,
                    attemptsToShow.size
                )
            }

            if (attemptsToShow.isEmpty()) {
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

                val globalAllTimeBestNs = getGlobalAllTimeBestForProfile(s, mode)
                val globalAllTimeBestAttemptId = getGlobalAllTimeBestAttemptIdForProfile(s, mode)

                attemptsAdapter = DragAttemptsAdapter(
                    this@DragSessionDetailsActivity,
                    attemptsToShow,
                    s.attempts,
                    s.id,
                    s.profileId,
                    mode,
                    globalAllTimeBestNs,
                    globalAllTimeBestAttemptId,
                    inlineExpansionEnabled = false,
                    forcedExpandedAttemptId = selectedAttemptId.takeIf { isAttemptDetailsMode },
                    onAttemptClick = if (isAttemptDetailsMode) {
                        null
                    } else {
                        { attempt -> openAttemptDetails(attempt.id) }
                    }
                )
                rvAttempts.apply {
                    isNestedScrollingEnabled = false
                    layoutManager = LinearLayoutManager(this@DragSessionDetailsActivity).apply {
                        reverseLayout = !isAttemptDetailsMode
                        stackFromEnd = !isAttemptDetailsMode
                    }
                    adapter = attemptsAdapter
                }
            }
        }
    }

    private fun formatTimeWithLabel(label: String?, time: Long?): String {
        if (time == null || time <= 0) return "-"
        val formatted = DragTimeFormatter.formatSecondsWithUnitFromNanos(time)
        return if (!label.isNullOrEmpty()) "$label $formatted" else formatted
    }

    private fun formatHeroTime(time: Long?): String =
        DragTimeFormatter.formatSecondsFromNanos(time)

    private fun getAttemptTimeForMode(attempt: DragAttempt, mode: MeasurementMode): Long {
        return when (mode) {
            MeasurementMode.ZERO_TO_100 -> attempt.time0to100
            MeasurementMode.ZERO_TO_200 -> attempt.time0to200
            MeasurementMode.HUNDRED_TO_200 -> attempt.time100to200
            MeasurementMode.QUARTER_MILE,
            MeasurementMode.ALL -> resolveQuarterTimeNs(attempt)
        }
    }

    private fun resolveQuarterTimeNs(attempt: DragAttempt): Long {
        return attempt.time0to402.takeIf { it > 0L }
            ?: attempt.distance402mTimeNs.takeIf { it > 0L }
            ?: -1L
    }

    private fun compatibleSessionModes(mode: MeasurementMode): Set<String> {
        return when (mode) {
            MeasurementMode.ZERO_TO_100 -> setOf("ZERO_TO_100", "ALL")
            MeasurementMode.ZERO_TO_200 -> setOf("ZERO_TO_200", "ALL")
            MeasurementMode.HUNDRED_TO_200 -> setOf("HUNDRED_TO_200", "ALL")
            MeasurementMode.QUARTER_MILE -> setOf("QUARTER_MILE", "ALL")
            MeasurementMode.ALL -> setOf("ALL", "QUARTER_MILE")
        }
    }

    private fun getPreviousAllTimeBestBeforeSession(
        session: DragSession,
        mode: MeasurementMode
    ): Long? {
        val compatible = compatibleSessionModes(mode)
        return DragStorage.loadDragSessions(this)
            .asSequence()
            .filter { it.profileId == session.profileId }
            .filter { it.id != session.id }
            .filter { it.timestamp <= session.timestamp }
            .filter { (it.measurementMode?.uppercase() ?: "ALL") in compatible }
            .flatMap { it.attempts.asSequence() }
            .map { getAttemptTimeForMode(it, mode) }
            .filter { it > 0L }
            .minOrNull()
    }

    private fun getGlobalAllTimeBestForProfile(
        session: DragSession,
        mode: MeasurementMode
    ): Long? {
        val compatible = compatibleSessionModes(mode)
        return DragStorage.loadDragSessions(this)
            .asSequence()
            .filter { it.profileId == session.profileId }
            .filter { (it.measurementMode?.uppercase() ?: "ALL") in compatible }
            .flatMap { it.attempts.asSequence() }
            .map { getAttemptTimeForMode(it, mode) }
            .filter { it > 0L }
            .minOrNull()
    }

    private fun getGlobalAllTimeBestAttemptIdForProfile(
        session: DragSession,
        mode: MeasurementMode
    ): Long? {
        val compatible = compatibleSessionModes(mode)
        var bestTime = Long.MAX_VALUE
        var bestSessionTimestamp = Long.MIN_VALUE
        var bestAttemptTimestamp = Long.MIN_VALUE
        var bestAttemptId: Long? = null

        DragStorage.loadDragSessions(this)
            .asSequence()
            .filter { it.profileId == session.profileId }
            .filter { (it.measurementMode?.uppercase() ?: "ALL") in compatible }
            .forEach { dragSession ->
                dragSession.attempts.forEach { attempt ->
                    val time = getAttemptTimeForMode(attempt, mode)
                    if (time <= 0L) return@forEach

                    val isBetterTime = time < bestTime
                    val isSameTimeButNewerSession = time == bestTime && dragSession.timestamp > bestSessionTimestamp
                    val isSameSessionButNewerAttempt =
                        time == bestTime && dragSession.timestamp == bestSessionTimestamp && attempt.timestamp > bestAttemptTimestamp
                    val isSameTimestampButHigherId =
                        time == bestTime && dragSession.timestamp == bestSessionTimestamp && attempt.timestamp == bestAttemptTimestamp &&
                            (bestAttemptId == null || attempt.id > bestAttemptId!!)

                    if (isBetterTime || isSameTimeButNewerSession || isSameSessionButNewerAttempt || isSameTimestampButHigherId) {
                        bestTime = time
                        bestSessionTimestamp = dragSession.timestamp
                        bestAttemptTimestamp = attempt.timestamp
                        bestAttemptId = attempt.id
                    }
                }
            }

        return bestAttemptId
    }

    private fun findBestAttemptForMode(s: DragSession, mode: MeasurementMode): Pair<Int, DragAttempt>? {
        var bestIndex = -1
        var bestTime = Long.MAX_VALUE
        var bestAttempt: DragAttempt? = null

        s.attempts.forEachIndexed { index, attempt ->
            val time = getAttemptTimeForMode(attempt, mode)
            if (time <= 0L) return@forEachIndexed

            val isBetterTime = time < bestTime
            val isSameTimeButNewerAttempt =
                time == bestTime && bestAttempt != null &&
                    (attempt.timestamp > bestAttempt!!.timestamp ||
                        (attempt.timestamp == bestAttempt!!.timestamp && attempt.id > bestAttempt!!.id))

            if (isBetterTime || isSameTimeButNewerAttempt || bestAttempt == null) {
                bestTime = time
                bestIndex = index
                bestAttempt = attempt
            }
        }

        return if (bestAttempt != null) bestIndex to bestAttempt!! else null
    }

    private fun bindAllModeBestCard(
        session: DragSession,
        metricMode: MeasurementMode,
        labelView: TextView,
        valueView: TextView,
        metaView: TextView,
        labelText: String,
        metricColorRes: Int
    ) {
        labelView.text = labelText

        val bestAttemptInfo = findBestAttemptForMode(session, metricMode)
        val bestAttempt = bestAttemptInfo?.second
        val bestRunNumber = bestAttemptInfo?.first?.plus(1)
        val bestTimeNs = bestAttempt?.let { getAttemptTimeForMode(it, metricMode) }?.takeIf { it > 0L }

        if (bestAttempt == null || bestRunNumber == null || bestTimeNs == null) {
            valueView.text = "--.--"
            valueView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            metaView.text = getString(R.string.drag_run_no_run)
            metaView.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            return
        }

        val isGlobalPbForMetric = getGlobalAllTimeBestAttemptIdForProfile(session, metricMode) == bestAttempt.id

        valueView.text = formatHeroTime(bestTimeNs)
        val metricColor = ContextCompat.getColor(this, metricColorRes)
        valueView.setTextColor(metricColor)

        if (isGlobalPbForMetric) {
            val badgeText = getString(R.string.drag_run_pb_best_line, bestRunNumber)
            val span = SpannableString(badgeText)
            val pbMarkerEnd = getString(R.string.drag_run_indicator_pb).length
            span.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this, R.color.accent_gold)),
                0,
                pbMarkerEnd.coerceAtMost(badgeText.length),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            span.setSpan(
                ForegroundColorSpan(metricColor),
                pbMarkerEnd.coerceAtMost(badgeText.length),
                badgeText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            metaView.text = span
        } else {
            metaView.text = getString(R.string.drag_run_best_line, bestRunNumber)
            metaView.setTextColor(metricColor)
        }
    }

    private fun formatPeakGValue(peakG: Float?): String {
        if (peakG == null || peakG <= 0f) return "--"
        return String.format(Locale.US, "%.2fg", peakG)
    }

    private fun getPreferredPeakGValue(attempt: DragAttempt?): Float? {
        attempt ?: return null

        // Primary: use the live peak value stored at save time — identical to what
        // the run screen displayed. This ensures details == live.
        if (attempt.peakLongitudinalG > 0f) return attempt.peakLongitudinalG

        // New-run fallback: recover peak from persisted live display samples.
        val liveLimit = minOf(attempt.liveAccelDisplaySamples.size, attempt.liveAccelDisplayTimeStamps.size)
        if (liveLimit > 1) {
            val livePeak = attempt.liveAccelDisplaySamples
                .take(liveLimit)
                .map { if (it.isFinite()) it.coerceAtLeast(0f) else 0f }
                .maxOrNull()
            if (livePeak != null && livePeak > 0f) return livePeak
        }

        // Fallback for old sessions that don't have the stored value.
        val imuVals = attempt.longitudinalAccelSamples
        val imuLimit = imuVals.size
        if (imuLimit > 1) {
            val mps2ToG = 1f / 9.81f
            val clampG = 3.5f
            val emaAlpha = 0.22f
            val accelG = imuVals.map { (it * mps2ToG).coerceIn(-clampG, clampG) }
            var prev = accelG.first()
            val smoothed = mutableListOf(prev)
            for (i in 1 until accelG.size) {
                prev += emaAlpha * (accelG[i] - prev)
                smoothed.add(prev)
            }
            val peakG = smoothed.filter { it > 0f }.maxOrNull()
            if (peakG != null) return peakG
        }

        return attempt.gSamples.maxOrNull()
    }

    private fun formatRunValue(attemptIndex: Int?): String {
        if (attemptIndex == null || attemptIndex < 0) {
            return getString(R.string.drag_session_best_run_placeholder).uppercase(Locale.getDefault())
        }
        return getString(R.string.drag_session_best_run_format, attemptIndex + 1).uppercase(Locale.getDefault())
    }

    private fun resolveAttemptTrapSpeedKmh(attempt: DragAttempt?): Float? {
        attempt ?: return null
        return resolveAttemptFinishSpeedKmh(attempt, measurementMode)
    }

    private fun formatAttemptSpeed(attempt: DragAttempt?): String {
        val trapSpeedKmh = resolveAttemptTrapSpeedKmh(attempt) ?: return "--"
        return UnitsManager.formatSpeed(trapSpeedKmh, this, 0)
    }

    private fun updateSessionBestStats(session: DragSession, mode: MeasurementMode) {
        val timesSeconds = session.attempts.mapNotNull { attempt ->
            val timeNanos = getAttemptTimeForMode(attempt, mode)
            if (timeNanos > 0L) timeNanos / 1_000_000_000.0 else null
        }

        if (timesSeconds.isEmpty()) {
            tvSessionBestVsPrevPbValue.text = "--"
            tvSessionBestPrevBestValue.text = "--"
            tvSessionBestAvgAllRunsValue.text = "--"
            tvSessionBestConsistencyValue.text = "--"
            tvSessionBestVsPrevPbValue.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            return
        }

        val bestTime = timesSeconds.minOrNull() ?: return
        val previousGlobalPbNs = getPreviousAllTimeBestBeforeSession(session, mode)
        val previousGlobalPbSeconds = previousGlobalPbNs?.takeIf { it > 0L }?.div(1_000_000_000.0)
        val avg = timesSeconds.average()
        val consistency = if (timesSeconds.size >= 2) {
            val variance = timesSeconds.map { (it - avg) * (it - avg) }.average()
            sqrt(variance)
        } else {
            Double.NaN
        }

        val vsPrev = previousGlobalPbSeconds?.let { bestTime - it }

        tvSessionBestVsPrevPbValue.text = if (vsPrev == null) {
            "--"
        } else {
            String.format(Locale.US, "%+.2fs", vsPrev)
        }

        val deltaColor = when {
            vsPrev == null -> ContextCompat.getColor(this, R.color.text_secondary)
            vsPrev < 0.0 -> ContextCompat.getColor(this, R.color.accent_purple)
            vsPrev > 0.0 -> ContextCompat.getColor(this, R.color.accent_red)
            else -> ContextCompat.getColor(this, R.color.text_secondary)
        }
        tvSessionBestVsPrevPbValue.setTextColor(deltaColor)

        tvSessionBestPrevBestValue.text = previousGlobalPbSeconds?.let {
            String.format(Locale.US, "%.2fs", it)
        } ?: "--"

        tvSessionBestAvgAllRunsValue.text = String.format(Locale.US, "%.2fs", avg)

        tvSessionBestConsistencyValue.text = if (consistency.isNaN()) {
            "--"
        } else {
            String.format(Locale.US, "±%.2fs", consistency)
        }
    }
    
    private fun formatTimeWithLabelNormalized(label: String?, time: Long?, firstAttempt: DragAttempt?): String {
        if (time == null || time <= 0) return "-"

        // НЕ нормализираме времената - показваме ги като са записани
        // Нормализацията се използва само за графиката, не за дисплея
        val displayTime = time / 1_000_000_000.0
        

        return if (!label.isNullOrEmpty()) {
            "$label\n${DragTimeFormatter.formatSecondsWithUnit(displayTime)}"
        } else {
            DragTimeFormatter.formatSecondsWithUnit(displayTime)
        }
    }

}

private fun resolveAttemptFinishSpeedKmh(attempt: DragAttempt, measurementMode: MeasurementMode): Float? {
    val modeFinishSpeed = when (measurementMode) {
        MeasurementMode.ZERO_TO_100 -> attempt.time0to100.takeIf { it > 0L }?.let { 100f }
        MeasurementMode.ZERO_TO_200 -> attempt.time0to200.takeIf { it > 0L }?.let { 200f }
        MeasurementMode.HUNDRED_TO_200 -> attempt.time100to200.takeIf { it > 0L }?.let { 200f }
        MeasurementMode.QUARTER_MILE,
        MeasurementMode.ALL -> null
    }

    if (modeFinishSpeed != null) return modeFinishSpeed
    return DragAttemptMetrics.resolveTrapSpeedKmh(attempt)
}

class DragAttemptsAdapter(
    private val context: Context,
    private val attempts: List<DragAttempt>,
    private val allSessionAttempts: List<DragAttempt>,
    private val sessionId: Long,
    private val profileId: Long,
    private val measurementMode: MeasurementMode,
    private val globalAllTimeBestNs: Long?,
    private val globalAllTimeBestAttemptId: Long?,
    private val inlineExpansionEnabled: Boolean = true,
    private val forcedExpandedAttemptId: Long? = null,
    private val onAttemptClick: ((DragAttempt) -> Unit)? = null
) : RecyclerView.Adapter<DragAttemptsAdapter.AttemptViewHolder>() {
    
    private var currentMode: ChartMode = ChartMode.SPEED
    private var currentAttempt: DragAttempt? = null
    private var currentMarkerView: com.github.mikephil.charting.components.MarkerView? = null
    private val expandedAttemptIds = mutableSetOf<Long>()
    private val allModeDistanceTargets = listOf(50, 100, 200, 300, 402)

    private data class DistanceBestCandidate(
        val timeNs: Long,
        val sessionTimestamp: Long,
        val attemptTimestamp: Long,
        val attemptId: Long
    )

    private val sessionDistanceBestAttemptIds: Map<Int, Long> by lazy {
        computeSessionDistanceBestAttemptIds()
    }

    private val profileDistanceBestAttemptIds: Map<Int, Long> by lazy {
        computeProfileDistanceBestAttemptIds()
    }

    private fun resolveAttemptTrapSpeedKmh(attempt: DragAttempt): Float? {
        return resolveAttemptFinishSpeedKmh(attempt, measurementMode)
    }

    private fun getModeSpeedTargetPoints(attempt: DragAttempt): List<Pair<Long, Float>> {
        val targets = mutableListOf<Pair<Long, Float>>()
        if (measurementMode != MeasurementMode.HUNDRED_TO_200 && attempt.time0to100 > 0L) {
            targets.add(attempt.time0to100 to 100f)
        }
        if (attempt.time0to200 > 0L) {
            targets.add(attempt.time0to200 to 200f)
        }
        if (measurementMode == MeasurementMode.HUNDRED_TO_200 && attempt.time100to200 > 0L) {
            targets.add(attempt.time100to200 to 200f)
        }
        val time402Ns = attempt.time0to402.takeIf { it > 0L } ?: attempt.distance402mTimeNs.takeIf { it > 0L }
        val trapSpeedKmh = DragAttemptMetrics.resolveTrapSpeedKmh(attempt)
        if (time402Ns != null && trapSpeedKmh != null) {
            targets.add(time402Ns to trapSpeedKmh)
        }
        return targets
    }

    private fun ensureSpeedSeriesHitsMilestones(
        speedSamplesKmh: List<Float>,
        speedTimesNs: List<Long>,
        attempt: DragAttempt
    ): Pair<List<Float>, List<Long>> {
        var samples = speedSamplesKmh
        var times = speedTimesNs
        for ((targetTimeNs, targetSpeedKmh) in getModeSpeedTargetPoints(attempt)) {
            val hit = insertSpeedMilestone(samples, times, targetTimeNs, targetSpeedKmh)
            samples = hit.first
            times = hit.second
        }
        return samples to times
    }

    private fun insertSpeedMilestone(
        speedSamplesKmh: List<Float>,
        speedTimesNs: List<Long>,
        targetTimeNs: Long,
        targetSpeedKmh: Float
    ): Pair<List<Float>, List<Long>> {
        if (speedSamplesKmh.isEmpty() || speedTimesNs.isEmpty()) return speedSamplesKmh to speedTimesNs
        if (targetTimeNs <= 0L || targetSpeedKmh <= 0f) return speedSamplesKmh to speedTimesNs

        val limit = minOf(speedSamplesKmh.size, speedTimesNs.size)
        val samples = speedSamplesKmh.take(limit).toMutableList()
        val times = speedTimesNs.take(limit).toMutableList()
        val matchWindowNs = 20_000_000L

        val existingIndex = times.indexOfFirst { kotlin.math.abs(it - targetTimeNs) <= matchWindowNs }
        if (existingIndex >= 0) {
            samples[existingIndex] = targetSpeedKmh
            times[existingIndex] = targetTimeNs
            return samples to times
        }

        val insertIndex = times.indexOfFirst { it > targetTimeNs }
            .let { idx -> if (idx >= 0) idx else times.size }
        times.add(insertIndex, targetTimeNs)
        samples.add(insertIndex, targetSpeedKmh)
        return samples to times
    }

    private fun getModeChartCutoffTimeNs(attempt: DragAttempt): Long? {
        return when (measurementMode) {
            MeasurementMode.ZERO_TO_100 -> attempt.time0to100.takeIf { it > 0L }
            MeasurementMode.ZERO_TO_200 -> attempt.time0to200.takeIf { it > 0L }
            MeasurementMode.HUNDRED_TO_200 -> attempt.time100to200.takeIf { it > 0L }
            MeasurementMode.QUARTER_MILE ->
                attempt.time0to402.takeIf { it > 0L } ?: attempt.distance402mTimeNs.takeIf { it > 0L }
            MeasurementMode.ALL -> listOf(
                attempt.time0to100,
                attempt.time100to200,
                attempt.time0to200,
                attempt.time0to402,
                attempt.distance402mTimeNs
            ).filter { it > 0L }.maxOrNull()
        }
    }

    private fun trimSeriesAfterModeCutoff(
        values: List<Float>,
        timestampsNs: List<Long>,
        attempt: DragAttempt
    ): Pair<List<Float>, List<Long>> {
        val cutoffTimeNs = getModeChartCutoffTimeNs(attempt) ?: return values to timestampsNs
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

    private fun getChartDataStart(chart: com.github.mikephil.charting.charts.LineChart): Float {
        return (chart.getTag(R.id.tag_drag_chart_data_start) as? Float) ?: 0f
    }

    private fun getChartDataEnd(chart: com.github.mikephil.charting.charts.LineChart): Float {
        return (chart.getTag(R.id.tag_drag_chart_data_end) as? Float) ?: 1f
    }

    private fun shouldResetChartView(chart: com.github.mikephil.charting.charts.LineChart): Boolean {
        return (chart.getTag(R.id.tag_drag_chart_reset_view) as? Boolean) ?: true
    }

    private fun formatDragReaderTime(timeValue: Float, dataStart: Float, dataEnd: Float): String {
        val clamped = timeValue.coerceIn(dataStart, dataEnd).coerceAtLeast(0f)
        return String.format("%.2fs", clamped)
    }

    private fun installDragChartCenterReader(chart: com.github.mikephil.charting.charts.LineChart) {
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
                drawDragReaderValuePill(c, chart)
            }
        }

        chart.setXAxisRenderer(object : XAxisRenderer(
            chart.viewPortHandler,
            chart.xAxis,
            chart.getTransformer(YAxis.AxisDependency.LEFT)
        ) {
            override fun renderAxisLabels(c: Canvas) {
                super.renderAxisLabels(c)
                drawDragReaderTimePill(c, chart)
            }
        })
    }

    private fun drawDragReaderTimePill(canvas: Canvas, chart: com.github.mikephil.charting.charts.LineChart) {
        val dataStart = getChartDataStart(chart)
        val dataEnd = getChartDataEnd(chart)
        val centerValue = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
        val timeText = formatDragReaderTime(centerValue, dataStart, dataEnd)

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

    private fun formatDragReaderValueText(
        chart: com.github.mikephil.charting.charts.LineChart,
        value: Float,
        mode: ChartMode
    ): String {
        return when (mode) {
            ChartMode.SPEED -> {
                val speedUnit = UnitsManager.getSpeedUnit(chart.context)
                String.format("%.1f %s", value, speedUnit.symbol)
            }
            ChartMode.ACCELERATION -> String.format("%.2f g", value)
            ChartMode.G_FORCE -> String.format("%.2f G", value)
        }
    }

    private fun resolveDragReaderValue(
        chart: com.github.mikephil.charting.charts.LineChart
    ): Pair<ChartMode, Float>? {
        val attempt = getChartAttempt(chart) ?: return null
        val mode = getChartMode(chart)
        val dataStart = getChartDataStart(chart)
        val dataEnd = getChartDataEnd(chart).coerceAtLeast(dataStart)
        val centerX = ((chart.lowestVisibleX + chart.highestVisibleX) / 2f)
            .coerceIn(dataStart, dataEnd)
        val value = findValueAtTimeInterpolated(attempt, centerX, mode)
        return mode to value
    }

    private fun drawDragReaderValuePill(canvas: Canvas, chart: com.github.mikephil.charting.charts.LineChart) {
        val (mode, value) = resolveDragReaderValue(chart) ?: return
        val valueText = formatDragReaderValueText(chart, value, mode)

        val density = chart.resources.displayMetrics.density
        val orange = ContextCompat.getColor(chart.context, R.color.primary_color)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = chart.xAxis.textSize
            typeface = chart.xAxis.typeface
            textAlign = Paint.Align.CENTER
        }
        val fm = textPaint.fontMetrics
        val textBounds = Rect()
        textPaint.getTextBounds(valueText, 0, valueText.length, textBounds)

        val hPad = 8f * density
        val vPad = 2.5f * density
        val strokeWidth = 1.5f * density
        val topMargin = chart.xAxis.yOffset
        val pillWidth = textBounds.width() + hPad * 2f
        val textHeight = fm.descent - fm.ascent
        val pillHeight = textHeight + vPad * 2f
        val centerX = chart.viewPortHandler.contentCenter.x
        val pillBottom = chart.viewPortHandler.contentTop() - topMargin
        val pillTop = pillBottom - pillHeight
        val pillRect = RectF(
            centerX - pillWidth / 2f,
            pillTop,
            centerX + pillWidth / 2f,
            pillBottom
        )
        val insetRect = RectF(pillRect)
        insetRect.inset(strokeWidth / 2f, strokeWidth / 2f)
        val corner = pillHeight / 2f

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(chart.context, R.color.background_primary)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = orange
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        }
        canvas.drawRoundRect(insetRect, corner, corner, fillPaint)
        canvas.drawRoundRect(insetRect, corner, corner, borderPaint)

        val baseline = pillTop + vPad - fm.ascent
        canvas.drawText(valueText, centerX, baseline, textPaint)
    }

    private fun readerSnapThresholdX(chart: com.github.mikephil.charting.charts.LineChart): Float {
        val contentWidth = chart.viewPortHandler.contentWidth().coerceAtLeast(1f)
        val xPerPx = chart.visibleXRange / contentWidth
        val fromPixels = READER_SNAP_DP * chart.resources.displayMetrics.density * xPerPx
        return minOf(fromPixels, READER_SNAP_MAX_SECONDS)
    }

    private fun showFullDragChart(chart: com.github.mikephil.charting.charts.LineChart) {
        val dataStart = getChartDataStart(chart)
        val dataEnd = getChartDataEnd(chart).coerceAtLeast(dataStart)
        val duration = (dataEnd - dataStart).coerceAtLeast(1f)
        chart.fitScreen()
        chart.setVisibleXRangeMaximum(duration)
        chart.moveViewToX(dataStart)
        chart.post {
            chart.setVisibleXRangeMaximum(duration)
            chart.moveViewToX(dataStart)
            applyDragChartReader(chart)
        }
    }

    private fun clampDragChartCenter(chart: com.github.mikephil.charting.charts.LineChart) {
        val dataStart = getChartDataStart(chart)
        val dataEnd = getChartDataEnd(chart).coerceAtLeast(dataStart)
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

    private fun applyDragChartReader(chart: com.github.mikephil.charting.charts.LineChart) {
        val activeAttempt = getChartAttempt(chart)
        val activeMode = getChartMode(chart)
        val activeMarker = getChartMarker(chart)
        if (activeAttempt == null || chart.data == null) {
            chart.highlightValue(null)
            return
        }

        val dataStart = getChartDataStart(chart)
        val dataEnd = getChartDataEnd(chart).coerceAtLeast(dataStart)
        val centerX = ((chart.lowestVisibleX + chart.highestVisibleX) / 2f)
            .coerceIn(dataStart, dataEnd)

        val closestSpecialPoint = findClosestSpecialPointByX(
            centerX,
            activeAttempt,
            activeMode,
            readerSnapThresholdX(chart)
        )

        if (closestSpecialPoint == null) {
            activeMarker?.let { markerView ->
                try {
                    val shouldShowField = markerView.javaClass.getDeclaredField("shouldShow")
                    shouldShowField.isAccessible = true
                    shouldShowField.set(markerView, false)
                } catch (_: Exception) {
                }
            }
            chart.highlightValue(null, false)
            chart.invalidate()
            return
        }

        val specialPointType = closestSpecialPoint.type
        val finalEntry = Entry(centerX, closestSpecialPoint.y)

        val modeDataSetIndex = (getCurrentModeDataSetIndex(chart, activeMode) ?: 0)
            .coerceIn(0, (chart.data?.dataSets?.size ?: 1) - 1)
        val specialDataSetIndex = findSpecialPointDataSetIndex(
            chart,
            specialPointType,
            closestSpecialPoint.x
        )
        val snappedDataSetIndex = (specialDataSetIndex ?: modeDataSetIndex)
            .coerceIn(0, (chart.data?.dataSets?.size ?: 1) - 1)
        val finalHighlight = Highlight(finalEntry.x, finalEntry.y, snappedDataSetIndex)

        activeMarker?.let { markerView ->
            try {
                val shouldShowField = markerView.javaClass.getDeclaredField("shouldShow")
                shouldShowField.isAccessible = true
                shouldShowField.set(markerView, true)

                val pointTypeField = markerView.javaClass.getDeclaredField("pointType")
                pointTypeField.isAccessible = true
                pointTypeField.set(markerView, specialPointType)

                val isOnSpecialPointField = markerView.javaClass.getDeclaredField("isOnSpecialPoint")
                isOnSpecialPointField.isAccessible = true
                isOnSpecialPointField.set(markerView, true)

                val actualValueField = markerView.javaClass.getDeclaredField("actualValue")
                actualValueField.isAccessible = true
                actualValueField.set(markerView, finalEntry.y)

                val exactTimeField = markerView.javaClass.getDeclaredField("exactTime")
                exactTimeField.isAccessible = true
                val exactTimeValue = when (specialPointType) {
                    PointTooltipMarker.PointType.SPEED_100 -> activeAttempt.time0to100 / 1_000_000_000.0f
                    PointTooltipMarker.PointType.SPEED_200 -> {
                        if (measurementMode == MeasurementMode.HUNDRED_TO_200) {
                            activeAttempt.time100to200 / 1_000_000_000.0f
                        } else {
                            activeAttempt.time0to200 / 1_000_000_000.0f
                        }
                    }
                    PointTooltipMarker.PointType.DISTANCE_402 -> activeAttempt.time0to402 / 1_000_000_000.0f
                }
                exactTimeField.set(markerView, exactTimeValue)

                val modeField = markerView.javaClass.getDeclaredField("mode")
                modeField.isAccessible = true
                modeField.set(markerView, activeMode)

                val attemptField = markerView.javaClass.getDeclaredField("attempt")
                attemptField.isAccessible = true
                attemptField.set(markerView, activeAttempt)
            } catch (_: Exception) {
            }

            markerView.refreshContent(finalEntry, finalHighlight)
        }

        chart.highlightValue(finalHighlight, false)
        chart.invalidate()
    }
    
    // Константа за Y threshold множител (използва се и при drag и при tap)
    companion object {
        private const val SNAP_Y_MULTIPLIER = 10f // Увеличен за по-добро хващане в G-Force и Acceleration
        private const val KMH_TO_MPS = 1f / 3.6f
        private const val MPS2_TO_G = 1f / 9.81f
        private const val ACCEL_DISPLAY_MAX_DELTA_G_PER_SEC = 7f
        private const val ACCEL_DISPLAY_EMA_ALPHA = 0.22f
        private const val ACCEL_DISPLAY_CLAMP_G = 3.5f
        private const val READER_SNAP_DP = 8f
        private const val READER_SNAP_MAX_SECONDS = 0.12f
        
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
        val shellContainer: View = itemView.findViewById(R.id.llAttemptShell)
        val summaryContainer: View = itemView.findViewById(R.id.llAttemptSummary)
        val detailsContainer: View = itemView.findViewById(R.id.llAttemptDetails)
        val runAccent: View = itemView.findViewById(R.id.vRunAccent)
        val tvAttemptNumber: TextView = itemView.findViewById(R.id.tvAttemptNumber)
        val btnAttemptVideo: ImageButton = itemView.findViewById(R.id.btnAttemptVideo)
        val llAttemptVideoCard: View = itemView.findViewById(R.id.llAttemptVideoCard)
        val flAttemptVideoPreview: View = itemView.findViewById(R.id.flAttemptVideoPreview)
        val btnAttemptVideoOpen: MaterialButton = itemView.findViewById(R.id.btnAttemptVideoOpen)
        val btnAttemptVideoShare: MaterialButton = itemView.findViewById(R.id.btnAttemptVideoShare)
        val btnAttemptVideoReexport: MaterialButton = itemView.findViewById(R.id.btnAttemptVideoReexport)
        val btnAttemptVideoHud: MaterialButton = itemView.findViewById(R.id.btnAttemptVideoHud)
        val tvRunPrimaryTime: TextView = itemView.findViewById(R.id.tvRunPrimaryTime)
        val tvRunPrimaryUnit: TextView = itemView.findViewById(R.id.tvRunPrimaryUnit)
        val tvRunPrimaryMetricLabel: TextView = itemView.findViewById(R.id.tvRunPrimaryMetricLabel)
        val llRunTrapSpeed: View = itemView.findViewById(R.id.llRunTrapSpeed)
        val tvRunSpeedMeta: TextView = itemView.findViewById(R.id.tvRunSpeedMeta)
        val tvRunDeltaLabel: TextView = itemView.findViewById(R.id.tvRunDeltaLabel)
        val tvRunDeltaValue: TextView = itemView.findViewById(R.id.tvRunDeltaValue)
        val tvRunBestBadge: TextView = itemView.findViewById(R.id.tvRunBestBadge)
        val tvRunPbBadge: TextView = itemView.findViewById(R.id.tvRunPbBadge)
        val tvAttemptChevron: TextView = itemView.findViewById(R.id.tvAttemptChevron)
        val tvTime0to100: TextView = itemView.findViewById(R.id.tvAttempt0to100)
        val tvTime0to200: TextView = itemView.findViewById(R.id.tvAttempt0to200)
        val tvTime100to200: TextView = itemView.findViewById(R.id.tvAttempt100to200)
        val tvTime0to402: TextView = itemView.findViewById(R.id.tvAttempt0to402)
        val llZeroTo200RunSplits: View = itemView.findViewById(R.id.llZeroTo200RunSplits)
        val tvAttemptSplit0to100: TextView = itemView.findViewById(R.id.tvAttemptSplit0to100)
        val tvAttemptSplit100to200: TextView = itemView.findViewById(R.id.tvAttemptSplit100to200)
        val tvMaxSpeed: TextView = itemView.findViewById(R.id.tvAttemptMaxSpeed)
        val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        val llRunTimesPreview: LinearLayout = itemView.findViewById(R.id.llRunTimesPreview)
        val llRunPreviewCol0to100: View = itemView.findViewById(R.id.llRunPreviewCol0to100)
        val llRunPreviewCol100to200: View = itemView.findViewById(R.id.llRunPreviewCol100to200)
        val llRunPreviewCol0to200: View = itemView.findViewById(R.id.llRunPreviewCol0to200)
        val llRunPreviewCol0to402: View = itemView.findViewById(R.id.llRunPreviewCol0to402)
        val tvRunPreviewLabel0to100: TextView = itemView.findViewById(R.id.tvRunPreviewLabel0to100)
        val tvRunPreviewLabel100to200: TextView = itemView.findViewById(R.id.tvRunPreviewLabel100to200)
        val tvRunPreviewLabel0to200: TextView = itemView.findViewById(R.id.tvRunPreviewLabel0to200)
        val tvRunPreviewLabel0to402: TextView = itemView.findViewById(R.id.tvRunPreviewLabel0to402)
        val tvRunPreviewValue0to100: TextView = itemView.findViewById(R.id.tvRunPreviewValue0to100)
        val tvRunPreviewValue100to200: TextView = itemView.findViewById(R.id.tvRunPreviewValue100to200)
        val tvRunPreviewValue0to200: TextView = itemView.findViewById(R.id.tvRunPreviewValue0to200)
        val tvRunPreviewValue0to402: TextView = itemView.findViewById(R.id.tvRunPreviewValue0to402)
        val pbAttemptGForce: ProgressBar = itemView.findViewById(R.id.pbAttemptGForce)
        val tvAttemptPeakG: TextView = itemView.findViewById(R.id.tvAttemptPeakG)
        val tvAttemptAvgG: TextView = itemView.findViewById(R.id.tvAttemptAvgG)
        val llAllModeBreakdown: View = itemView.findViewById(R.id.llAllModeBreakdown)
        val llAllModePrimaryMetrics: View = itemView.findViewById(R.id.llAllModePrimaryMetrics)
        val llAllMetricCol0to100: View = itemView.findViewById(R.id.llAllMetricCol0to100)
        val llAllMetricCol100to200: View = itemView.findViewById(R.id.llAllMetricCol100to200)
        val llAllMetricCol0to200: View = itemView.findViewById(R.id.llAllMetricCol0to200)
        val llAllMetricCol0to402: View = itemView.findViewById(R.id.llAllMetricCol0to402)
        val vAllModePrimaryDivider: View = itemView.findViewById(R.id.vAllModePrimaryDivider)
        val tvAllMetric0to100: TextView = itemView.findViewById(R.id.tvAllMetric0to100)
        val tvAllMetric100to200: TextView = itemView.findViewById(R.id.tvAllMetric100to200)
        val tvAllMetric0to200: TextView = itemView.findViewById(R.id.tvAllMetric0to200)
        val tvAllMetric0to402: TextView = itemView.findViewById(R.id.tvAllMetric0to402)
        val tvAllMetricLabel0to100: TextView = itemView.findViewById(R.id.tvAllMetricLabel0to100)
        val tvAllMetricLabel100to200: TextView = itemView.findViewById(R.id.tvAllMetricLabel100to200)
        val tvAllMetricLabel0to200: TextView = itemView.findViewById(R.id.tvAllMetricLabel0to200)
        val tvAllMetricLabel0to402: TextView = itemView.findViewById(R.id.tvAllMetricLabel0to402)
        val tvAllDist50Label: TextView = itemView.findViewById(R.id.tvAllDist50Label)
        val tvAllDist100Label: TextView = itemView.findViewById(R.id.tvAllDist100Label)
        val tvAllDist200Label: TextView = itemView.findViewById(R.id.tvAllDist200Label)
        val tvAllDist300Label: TextView = itemView.findViewById(R.id.tvAllDist300Label)
        val tvAllDist402Label: TextView = itemView.findViewById(R.id.tvAllDist402Label)
        val tvAllDist50Time: TextView = itemView.findViewById(R.id.tvAllDist50Time)
        val tvAllDist100Time: TextView = itemView.findViewById(R.id.tvAllDist100Time)
        val tvAllDist200Time: TextView = itemView.findViewById(R.id.tvAllDist200Time)
        val tvAllDist300Time: TextView = itemView.findViewById(R.id.tvAllDist300Time)
        val tvAllDist402Time: TextView = itemView.findViewById(R.id.tvAllDist402Time)
        val tvAllDist50Speed: TextView = itemView.findViewById(R.id.tvAllDist50Speed)
        val tvAllDist100Speed: TextView = itemView.findViewById(R.id.tvAllDist100Speed)
        val tvAllDist200Speed: TextView = itemView.findViewById(R.id.tvAllDist200Speed)
        val tvAllDist300Speed: TextView = itemView.findViewById(R.id.tvAllDist300Speed)
        val tvAllDist402Speed: TextView = itemView.findViewById(R.id.tvAllDist402Speed)
        val tvAllDist50Badge: TextView = itemView.findViewById(R.id.tvAllDist50Badge)
        val tvAllDist100Badge: TextView = itemView.findViewById(R.id.tvAllDist100Badge)
        val tvAllDist200Badge: TextView = itemView.findViewById(R.id.tvAllDist200Badge)
        val tvAllDist300Badge: TextView = itemView.findViewById(R.id.tvAllDist300Badge)
        val tvAllDist402Badge: TextView = itemView.findViewById(R.id.tvAllDist402Badge)
        val ivAttemptWeatherTemp: ImageView = itemView.findViewById(R.id.ivAttemptWeatherTemp)
        val tvAttemptTrackTempValue: TextView = itemView.findViewById(R.id.tvAttemptTrackTempValue)
        val tvAttemptHumidityValue: TextView = itemView.findViewById(R.id.tvAttemptHumidityValue)
        val tvAttemptWindValue: TextView = itemView.findViewById(R.id.tvAttemptWindValue)
        val tvAttemptTimeValue: TextView = itemView.findViewById(R.id.tvAttemptTimeValue)
        
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

    private fun getOriginalRunNumber(attempt: DragAttempt, adapterPosition: Int): Int {
        val indexInSession = allSessionAttempts.indexOfFirst { it.id == attempt.id }
        return if (indexInSession >= 0) indexInSession + 1 else adapterPosition + 1
    }

    override fun onBindViewHolder(holder: AttemptViewHolder, position: Int) {
        val attempt = attempts[position]
        val context = holder.itemView.context
        val runNumber = getOriginalRunNumber(attempt, position)
        // ⚠️ КРИТИЧНО: Reset-вайте всички бутони ПЪРВО (RecyclerView recycling)
        holder.btnSpeed.setBackgroundResource(R.drawable.button_toggle_unselected)
        holder.btnSpeed.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        
        holder.btnAcceleration.setBackgroundResource(R.drawable.button_toggle_unselected)
        holder.btnAcceleration.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        
        holder.btnGForce.setBackgroundResource(R.drawable.button_toggle_unselected)
        holder.btnGForce.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        holder.btnGForce.visibility = View.GONE
        holder.btnGForce.isEnabled = false

        holder.tvAttemptNumber.text = context.getString(R.string.drag_run_short_format, runNumber)
        val videoFile = attempt.videoPath?.takeIf { it.isNotBlank() }?.let(::File)
        val hasVideo = videoFile?.exists() == true
        val videoTitle = context.getString(R.string.drag_run_short_format, runNumber)
        holder.btnAttemptVideo.visibility = if (hasVideo) View.VISIBLE else View.GONE
        holder.llAttemptVideoCard.visibility = if (hasVideo) View.VISIBLE else View.GONE
        val openVideo = View.OnClickListener {
            if (!hasVideo || videoFile == null) return@OnClickListener
            DragAttemptVideoActivity.open(context, videoFile.absolutePath, videoTitle, attempt, measurementMode)
        }
        holder.btnAttemptVideo.setOnClickListener(openVideo)
        holder.flAttemptVideoPreview.setOnClickListener(openVideo)
        holder.btnAttemptVideoOpen.setOnClickListener(openVideo)
        holder.btnAttemptVideoShare.setOnClickListener {
            if (!hasVideo || videoFile == null) return@setOnClickListener
            DragAttemptVideoActivity.shareVideo(context, videoFile, videoTitle)
        }
        holder.btnAttemptVideoReexport.setOnClickListener {
            if (!hasVideo || videoFile == null || sessionId <= 0L) return@setOnClickListener
            DragVideoOverlayService.start(
                context = context,
                sessionId = sessionId,
                mode = measurementMode,
                forceAttemptId = attempt.id
            )
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.drag_attempt_video_reexport_started),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        holder.btnAttemptVideoHud.setOnClickListener {
            if (!hasVideo || videoFile == null) return@setOnClickListener
            val activity = context as? AppCompatActivity ?: return@setOnClickListener
            DragAttemptHudExport.start(
                activity = activity,
                videoFile = videoFile,
                attempt = attempt,
                mode = measurementMode,
                title = videoTitle
            )
        }

        val primaryTimeNs = getPrimaryTimeForSummary(attempt)
        holder.tvRunPrimaryTime.text = formatSummaryTime(primaryTimeNs)
        holder.tvRunPrimaryUnit.text = if (primaryTimeNs > 0L) "s" else ""

        val speedUnit = UnitsManager.getSpeedUnit(context)
        val hideAllModeQuarterHero = measurementMode == MeasurementMode.ALL
        val showQuarterMetricUnderPrimary =
            !hideAllModeQuarterHero &&
                measurementMode == MeasurementMode.ALL &&
                (attempt.time0to402 > 0L || attempt.distance402mTimeNs > 0L)

        holder.tvRunPrimaryMetricLabel.text = UnitsManager.formatDragZeroTo402IntervalLabel(context)
        holder.tvRunPrimaryMetricLabel.visibility = if (showQuarterMetricUnderPrimary) View.VISIBLE else View.GONE
        holder.tvRunPrimaryTime.visibility = if (hideAllModeQuarterHero) View.GONE else View.VISIBLE
        holder.tvRunPrimaryUnit.visibility =
            if (primaryTimeNs > 0L && !showQuarterMetricUnderPrimary && !hideAllModeQuarterHero) {
                View.VISIBLE
            } else {
                View.GONE
            }

        val trapSpeedKmh = DragAttemptMetrics.resolveTrapSpeedKmh(attempt)
        val convertedTrapSpeed = trapSpeedKmh?.let { UnitsManager.convertSpeed(it, speedUnit) }
        holder.llRunTrapSpeed.visibility = if (trapSpeedKmh != null) View.VISIBLE else View.GONE
        holder.tvMaxSpeed.text = convertedTrapSpeed?.toInt()?.toString() ?: "--"
        holder.tvRunSpeedMeta.text = context.getString(
            R.string.drag_run_speed_at_finish,
            speedUnit.symbol.uppercase(Locale.getDefault())
        )
        bindSummaryPreviewLabels(holder, speedUnit)
        bindSummaryTimesPreview(holder, attempt)

        val currentGlobalBestNs = globalAllTimeBestNs?.takeIf { it > 0L }
        val isAllTimePb =
            primaryTimeNs > 0L &&
                globalAllTimeBestAttemptId != null &&
                attempt.id == globalAllTimeBestAttemptId

        // Скриваме старите chip-бейджове - използваме само индикаторния блок вдясно.
        holder.tvRunBestBadge.visibility = View.GONE
        holder.tvRunPbBadge.visibility = View.GONE
        applyCollapsedRunCardStyle(holder, runNumber = runNumber)
        bindDeltaSummary(
            holder,
            primaryTimeNs,
            isAllTimePb,
            currentGlobalBestNs
        )

        updateVisibility(holder)

        // Използваме същата нормализация като Best Times за съответствие
        val firstAttempt = attempts.firstOrNull()
        val speed100 = UnitsManager.convertSpeed(100f, speedUnit).toInt()
        val speed200 = UnitsManager.convertSpeed(200f, speedUnit).toInt()
        val distLabel = UnitsManager.getQuarterMileDistance(context)
        
        holder.tvTime0to100.text = formatTimeWithLabelNormalized("0-$speed100", attempt.time0to100, firstAttempt)
        holder.tvTime0to200.text = formatTimeWithLabelNormalized("0-$speed200", attempt.time0to200, firstAttempt)
        holder.tvTime100to200.text = formatTimeWithLabelNormalized("$speed100-$speed200", attempt.time100to200, firstAttempt)
        holder.tvTime0to402.text = formatTimeWithLabelNormalized(distLabel, attempt.time0to402, firstAttempt)
        bindZeroTo200RunSplits(holder, attempt)

        bindGSummary(holder, attempt)
        bindAllModeBreakdown(holder, attempt)
        bindMetaRow(holder, attempt)

        val isExpanded = forcedExpandedAttemptId?.let { attempt.id == it }
            ?: expandedAttemptIds.contains(attempt.id)
        applyExpandedState(holder, isExpanded)
        holder.summaryContainer.isClickable = onAttemptClick != null || inlineExpansionEnabled
        holder.summaryContainer.setOnClickListener {
            if (onAttemptClick != null) {
                onAttemptClick.invoke(attempt)
                return@setOnClickListener
            }
            if (!inlineExpansionEnabled || forcedExpandedAttemptId != null) {
                return@setOnClickListener
            }
            val currentlyExpanded = expandedAttemptIds.contains(attempt.id)
            if (currentlyExpanded) {
                expandedAttemptIds.remove(attempt.id)
            } else {
                expandedAttemptIds.add(attempt.id)
            }
            applyExpandedState(holder, !currentlyExpanded)
            if (!currentlyExpanded) {
                expandChartToFillRemaining(holder)
            } else {
                resetChartHeight(holder)
            }
        }

        if (isExpanded || inlineExpansionEnabled) {
            // Настройваме новата графика
            setupChart(holder, attempt)
            if (isExpanded) {
                expandChartToFillRemaining(holder)
            }
        } else {
            holder.chart.clear()
            holder.chart.marker = null
        }
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
        holder.chart.setHighlightPerTapEnabled(false)
        holder.chart.isHighlightPerDragEnabled = false
        holder.chart.axisRight.isEnabled = false
        holder.chart.description.isEnabled = false
        holder.chart.legend.isEnabled = false
        holder.chart.isDragDecelerationEnabled = false
        holder.chart.dragDecelerationFrictionCoef = 0f
        holder.chart.setExtraTopOffset(30f)
        holder.chart.setExtraRightOffset(24f)
        holder.chart.setExtraBottomOffset(6f)
        holder.chart.setTag(R.id.tag_drag_chart_reset_view, true)
        installDragChartCenterReader(holder.chart)
        
        holder.chart.xAxis.apply {
            position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            granularity = 0.1f
            textColor = android.graphics.Color.WHITE
            textSize = 12f
            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(x: Float): String {
                    if (x < -0.05f) return ""
                    if (kotlin.math.abs(x) < 0.05f) return "0.0s"
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
    
    private fun collectSpecialPoints(
        attempt: DragAttempt,
        mode: ChartMode
    ): List<SpecialPoint> {
        val specialPoints = mutableListOf<SpecialPoint>()
        val (_, timestamps) = getAlignedSpeedData(attempt)
        if (timestamps.isEmpty()) return emptyList()

        if (attempt.time0to100 > 0 && measurementMode != MeasurementMode.HUNDRED_TO_200) {
            val time100Absolute = attempt.time0to100 / 1_000_000_000.0f
            val y100 = milestoneMarkerY(attempt, PointTooltipMarker.PointType.SPEED_100, mode, time100Absolute)
            specialPoints.add(SpecialPoint(time100Absolute, y100, PointTooltipMarker.PointType.SPEED_100, 1))
        }

        if (measurementMode == MeasurementMode.HUNDRED_TO_200) {
            if (attempt.time100to200 > 0) {
                val time200Absolute = attempt.time100to200 / 1_000_000_000.0f
                val y200 = milestoneMarkerY(attempt, PointTooltipMarker.PointType.SPEED_200, mode, time200Absolute)
                specialPoints.add(SpecialPoint(time200Absolute, y200, PointTooltipMarker.PointType.SPEED_200, 2))
            }
        } else {
            if (attempt.time0to200 > 0) {
                val time200Absolute = attempt.time0to200 / 1_000_000_000.0f
                val y200 = milestoneMarkerY(attempt, PointTooltipMarker.PointType.SPEED_200, mode, time200Absolute)
                specialPoints.add(SpecialPoint(time200Absolute, y200, PointTooltipMarker.PointType.SPEED_200, 2))
            }
        }

        if (attempt.time0to402 > 0) {
            val time402Absolute = attempt.time0to402 / 1_000_000_000.0f
            if (time402Absolute > 0f) {
                val y402 = milestoneMarkerY(attempt, PointTooltipMarker.PointType.DISTANCE_402, mode, time402Absolute)
                specialPoints.add(SpecialPoint(time402Absolute, y402, PointTooltipMarker.PointType.DISTANCE_402, 3))
            }
        }

        return specialPoints
    }

    private fun findClosestSpecialPointByX(
        centerX: Float,
        attempt: DragAttempt,
        mode: ChartMode,
        snapThresholdX: Float
    ): SpecialPoint? {
        return collectSpecialPoints(attempt, mode)
            .map { point -> point to kotlin.math.abs(centerX - point.x) }
            .filter { (_, distance) -> distance < snapThresholdX }
            .minWithOrNull(compareBy({ it.second }, { -it.first.priority }))
            ?.first
    }

    // Помощна функция за намиране на най-близката специална точка (използва се и при drag и при tap)
    private fun findClosestSpecialPoint(
        touchX: Float,
        touchY: Float,
        attempt: DragAttempt,
        mode: ChartMode,
        snapThreshold: Float,
        context: android.content.Context,
        yThresholdMultiplier: Float = SNAP_Y_MULTIPLIER
    ): SpecialPoint? {
        val specialPoints = collectSpecialPoints(attempt, mode)
        if (specialPoints.isEmpty()) return null

        // Проверяваме за SNAPPING - намираме най-близката специална точка
        // КРИТИЧНО: Използваме 2D разстояние (X И Y) за по-точно определяне
        // КРИТИЧНО: Ако има множество близки точки, избираме тази с най-висок приоритет
        val candidatePoints = mutableListOf<Pair<SpecialPoint, Float>>()
        val xThreshold = snapThreshold
        val yThreshold = snapThreshold * yThresholdMultiplier

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

    private fun findSpecialPointDataSetIndex(
        chart: com.github.mikephil.charting.charts.LineChart,
        pointType: PointTooltipMarker.PointType,
        pointX: Float
    ): Int? {
        val dataSets = chart.data?.dataSets ?: return null
        val expectedColor = when (pointType) {
            PointTooltipMarker.PointType.SPEED_100 -> ContextCompat.getColor(chart.context, R.color.accent_green)
            PointTooltipMarker.PointType.SPEED_200 -> ContextCompat.getColor(chart.context, R.color.accent_blue)
            PointTooltipMarker.PointType.DISTANCE_402 -> ContextCompat.getColor(chart.context, R.color.accent_red)
        }

        for (i in dataSets.indices) {
            val dataSet = dataSets[i]
            if (dataSet.label.isNotEmpty() || dataSet.entryCount != 1) continue

            val onlyEntry = dataSet.getEntryForIndex(0) ?: continue
            if (kotlin.math.abs(onlyEntry.x - pointX) > 0.03f) continue

            val lineDataSet = dataSet as? com.github.mikephil.charting.data.LineDataSet
            val circleColor = lineDataSet?.circleColors?.firstOrNull()
            if (circleColor == expectedColor) {
                return i
            }
        }

        return null
    }
    
    private fun setupChartZoom(chart: com.github.mikephil.charting.charts.LineChart) {
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
                val dataStart = getChartDataStart(chart)
                val dataEnd = getChartDataEnd(chart)
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
                applyDragChartReader(chart)
            }
        })

        chart.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)

            if (!isZooming) {
                chart.onTouchEvent(event)
                clampDragChartCenter(chart)
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    chart.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    chart.parent?.requestDisallowInterceptTouchEvent(false)
                    applyDragChartReader(chart)
                }
            }

            true
        }

        chart.setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                applyDragChartReader(chart)
            }

            override fun onNothingSelected() {
                applyDragChartReader(chart)
            }
        })

        chart.setOnChartGestureListener(object : OnChartGestureListener {
            override fun onChartGestureStart(me: MotionEvent?, lastGesture: ChartTouchListener.ChartGesture?) {
                chart.parent?.requestDisallowInterceptTouchEvent(true)
            }
            override fun onChartGestureEnd(me: MotionEvent?, lastGesture: ChartTouchListener.ChartGesture?) {
                chart.parent?.requestDisallowInterceptTouchEvent(false)
                applyDragChartReader(chart)
            }
            override fun onChartLongPressed(me: MotionEvent?) {}
            override fun onChartDoubleTapped(me: MotionEvent?) {
                showFullDragChart(chart)
            }
            override fun onChartSingleTapped(me: MotionEvent?) {}
            override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}
            override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {}
            override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
                if (!isZooming) {
                    clampDragChartCenter(chart)
                    applyDragChartReader(chart)
                }
            }
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

    private fun getAlignedSpeedDataForChart(attempt: DragAttempt): Pair<List<Float>, List<Long>> {
        val (speedSamples, speedTimes) = getAlignedSpeedData(attempt)
        if (speedSamples.isEmpty() || speedTimes.isEmpty()) return speedSamples to speedTimes

        val sanitized = speedSamples.map { sample ->
            if (sample.isFinite()) sample.coerceAtLeast(0f) else 0f
        }

        val trimmed = trimSeriesAfterModeCutoff(sanitized, speedTimes, attempt)
        return ensureSpeedSeriesHitsMilestones(trimmed.first, trimmed.second, attempt)
    }

    private fun getAlignedAccelData(attempt: DragAttempt): Pair<List<Float>, List<Long>> {
        // Primary: use persisted live ACCEL panel series so chart matches run screen.
        val liveVals = attempt.liveAccelDisplaySamples
        val liveTimes = attempt.liveAccelDisplayTimeStamps
        val liveLimit = minOf(liveVals.size, liveTimes.size)
        if (liveLimit > 1) {
            val alignedTimes = liveTimes.take(liveLimit)
            val accelG = liveVals.take(liveLimit)
                .map { if (it.isFinite()) it.coerceAtLeast(0f) else 0f }
            return trimSeriesAfterModeCutoff(accelG, alignedTimes, attempt)
        }

        // Предпочитаме IMU longitudinal acceleration (RaceBox стил), конвертиран в g.
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

            return trimSeriesAfterModeCutoff(smoothed, alignedTimes, attempt)
        }

        // Legacy fallback: ACCEL от speed derivative за стари опити без IMU longitudinal серия.
        val (speedSamples, speedTimes) = getAlignedSpeedDataForChart(attempt)
        val derived = deriveAccelerationFromSpeedSamples(speedSamples, speedTimes)
        if (derived.first.isNotEmpty() && derived.second.isNotEmpty()) {
            val derivedG = derived.first.map { (it * MPS2_TO_G).coerceIn(-ACCEL_DISPLAY_CLAMP_G, ACCEL_DISPLAY_CLAMP_G) }
            val smoothed = smoothSeriesForChart(
                values = derivedG,
                timestampsNs = derived.second,
                maxDeltaPerSecond = ACCEL_DISPLAY_MAX_DELTA_G_PER_SEC,
                emaAlpha = ACCEL_DISPLAY_EMA_ALPHA,
                medianPasses = 2
            ).map { value -> if (kotlin.math.abs(value) < 0.03f) 0f else value }
            return trimSeriesAfterModeCutoff(smoothed, derived.second, attempt)
        }

        // Legacy fallback: GPS accel буфер.
        val vals = attempt.gpsAccelSamples
        val times = attempt.gpsTimeStamps
        val limit = minOf(vals.size, times.size)
        val alignedTimes = times.take(limit)
        val accelG = vals.take(limit)
            .map { (it * MPS2_TO_G).coerceIn(-ACCEL_DISPLAY_CLAMP_G, ACCEL_DISPLAY_CLAMP_G) }
        if (accelG.isEmpty() || alignedTimes.isEmpty()) return accelG to alignedTimes

        val smoothed = smoothSeriesForChart(
            values = accelG,
            timestampsNs = alignedTimes,
            maxDeltaPerSecond = ACCEL_DISPLAY_MAX_DELTA_G_PER_SEC,
            emaAlpha = ACCEL_DISPLAY_EMA_ALPHA,
            medianPasses = 2
        ).map { value -> if (kotlin.math.abs(value) < 0.03f) 0f else value }

        return trimSeriesAfterModeCutoff(smoothed, alignedTimes, attempt)
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
            val accel = (v1Mps - v0Mps) / dtSec
            if (!accel.isFinite()) continue

            accelValues.add(accel)
            accelTimes.add(t1)
        }

        return accelValues to accelTimes
    }

    private fun getPreferredGSeries(attempt: DragAttempt): Pair<List<Float>, List<Long>> {
        val liveVals = attempt.liveAccelDisplaySamples
        val liveTimes = attempt.liveAccelDisplayTimeStamps
        val liveLimit = minOf(liveVals.size, liveTimes.size)

        val raw = if (liveLimit > 1) {
            val alignedTimes = liveTimes.take(liveLimit)
            val liveSeries = liveVals.take(liveLimit)
                .map { if (it.isFinite()) it.coerceAtLeast(0f) else 0f }
            trimSeriesAfterModeCutoff(liveSeries, alignedTimes, attempt)
        } else {
            val imuVals = attempt.longitudinalAccelSamples
            val imuTimes = attempt.longitudinalAccelTimeStamps
            val imuLimit = minOf(imuVals.size, imuTimes.size)
            if (imuLimit > 1) {
                val alignedTimes = imuTimes.take(imuLimit)
                val longitudinalG = imuVals.take(imuLimit)
                    .map { (it * MPS2_TO_G).coerceIn(-ACCEL_DISPLAY_CLAMP_G, ACCEL_DISPLAY_CLAMP_G) }
                trimSeriesAfterModeCutoff(longitudinalG, alignedTimes, attempt)
            } else {
                val vals = attempt.gSamples
                val times = attempt.timeStamps
                val limit = minOf(vals.size, times.size)
                trimSeriesAfterModeCutoff(vals.take(limit), times.take(limit), attempt)
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

    private fun getPreferredPeakGValue(attempt: DragAttempt): Float? {
        // Primary: use the live peak value stored at save time.
        if (attempt.peakLongitudinalG > 0f) return attempt.peakLongitudinalG

        // Fallback for old sessions.
        val values = getPreferredGSeries(attempt).first
        if (values.isEmpty()) return null
        return values.filter { it > 0f }.maxOrNull()
    }

    private fun getPreferredAverageAbsGValue(attempt: DragAttempt): Float? {
        val values = getPreferredGSeries(attempt).first
        if (values.isEmpty()) return null
        return values.map { kotlin.math.abs(it) }.average().toFloat()
    }

    private fun getAlignedGData(attempt: DragAttempt): Pair<List<Float>, List<Long>> {
        return getPreferredGSeries(attempt)
    }
    
    private fun updateChartMode(holder: AttemptViewHolder, attempt: DragAttempt, mode: ChartMode) {
        holder.currentChartMode = mode
        currentMode = mode
        currentAttempt = attempt
        setChartContext(holder.chart, attempt, mode)
        holder.chart.setTag(R.id.tag_drag_chart_reset_view, false)
        
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

        bindExpandedChartHeader(holder, attempt, mode)

        // Рисуваме само избрания режим, за да няма скрити overlay линии.
        when (mode) {
            ChartMode.SPEED -> addSpeedLine(holder, attempt, true)
            ChartMode.ACCELERATION -> addAccelerationLine(holder, attempt, true)
            ChartMode.G_FORCE -> addGForceLine(holder, attempt, true)
        }
        
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
        holder.chart.setTag(R.id.tag_drag_chart_reset_view, false)
        holder.chart.invalidate()
    }

    private fun bindExpandedChartHeader(holder: AttemptViewHolder, attempt: DragAttempt, mode: ChartMode) {
        val speedUnit = UnitsManager.getSpeedUnit(context)
        val speed100 = UnitsManager.convertSpeed(100f, speedUnit).toInt()
        val speed200 = UnitsManager.convertSpeed(200f, speedUnit).toInt()
        val rangeLabel = when (measurementMode) {
            MeasurementMode.ZERO_TO_100 -> "0-$speed100"
            MeasurementMode.ZERO_TO_200 -> "0-$speed200"
            MeasurementMode.HUNDRED_TO_200 -> "$speed100-$speed200"
            MeasurementMode.QUARTER_MILE -> "0-${UnitsManager.getQuarterMileDistance(context)}"
            MeasurementMode.ALL -> "ALL"
        }

        val modeLabel = when (mode) {
            ChartMode.SPEED -> context.getString(R.string.drag_chart_mode_speed)
            ChartMode.ACCELERATION -> context.getString(R.string.drag_chart_mode_accel)
            ChartMode.G_FORCE -> context.getString(R.string.drag_chart_mode_gforce)
        }

        holder.tvChartTitle.text = context.getString(R.string.drag_chart_title_format, modeLabel, rangeLabel)

        val primaryTimeNs = getPrimaryTimeForSummary(attempt)
        holder.tvChartStats.text = if (primaryTimeNs > 0L) {
            context.getString(
                R.string.drag_chart_stats_format,
                String.format(Locale.US, "%.2fs", primaryTimeNs / 1_000_000_000.0)
            )
        } else {
            context.getString(R.string.drag_chart_stats_empty)
        }
    }

    private fun applyChartXPadding(chart: com.github.mikephil.charting.charts.LineChart, baseMaxX: Float) {
        val dataStart = 0f
        val dataEnd = if (baseMaxX > 0f) baseMaxX else 1f
        val duration = (dataEnd - dataStart).coerceAtLeast(1f)
        val resetViewToStart = shouldResetChartView(chart)
        val previousCenter = (chart.lowestVisibleX + chart.highestVisibleX) / 2f
        val previousRange = chart.visibleXRange

        chart.setTag(R.id.tag_drag_chart_data_start, dataStart)
        chart.setTag(R.id.tag_drag_chart_data_end, dataEnd)
        chart.xAxis.axisMinimum = dataStart - duration
        chart.xAxis.axisMaximum = dataEnd + duration
        chart.setVisibleXRangeMaximum(duration)

        if (resetViewToStart) {
            showFullDragChart(chart)
        } else {
            val visibleRange = if (previousRange > 0f) previousRange else duration
            val targetCenter = previousCenter.coerceIn(dataStart, dataEnd)
            chart.moveViewToX(targetCenter - visibleRange / 2f)
            chart.post { applyDragChartReader(chart) }
        }
    }
    
    private fun addSpeedLine(holder: AttemptViewHolder, attempt: DragAttempt, isActive: Boolean) {
        Log.d("DragSessionDetails", "📊 addSpeedLine START: attempt.speedSamples=${attempt.speedSamples.size}, attempt.speedTimeStamps=${attempt.speedTimeStamps.size}")
        
        val (speedSamples, timestamps) = getAlignedSpeedDataForChart(attempt)
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
            
            for (i in speedSamples.indices) {
                val currentSpeed = speedSamples[i]
                val absoluteTimeInSeconds = timestamps[i] / 1_000_000_000.0f
                val convertedSpeed = UnitsManager.convertSpeed(currentSpeed, speedUnit)
                entries.add(com.github.mikephil.charting.data.Entry(absoluteTimeInSeconds, convertedSpeed))
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
                mode = com.github.mikephil.charting.data.LineDataSet.Mode.LINEAR
                setDrawValues(false)
                setDrawCircles(false)
                // КРИТИЧНО: Забраняваме highlighting за неактивни линии - може да се плъзга само по активната
                isHighlightEnabled = isActive
                setDrawHighlightIndicators(false)
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
                mode = com.github.mikephil.charting.data.LineDataSet.Mode.LINEAR
                setDrawValues(false)
                setDrawCircles(false)
                // КРИТИЧНО: Забраняваме highlighting за неактивни линии - може да се плъзга само по активната
                isHighlightEnabled = isActive
                setDrawHighlightIndicators(false)
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
                mode = com.github.mikephil.charting.data.LineDataSet.Mode.LINEAR
                setDrawValues(false)
                setDrawCircles(false)
                // КРИТИЧНО: Забраняваме highlighting за неактивни линии - може да се плъзга само по активната
                isHighlightEnabled = isActive
                setDrawHighlightIndicators(false)
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
        val (speedSamples, timestamps) = getAlignedSpeedDataForChart(attempt)
        
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
            
            // Debug проверка за съответствие на времената
            val crossing100 = findSpeedCrossingPoint(speedSamples, timestamps, 100f)
            
            // Данните вече са добавени от addSpeedLine
            
            // Настройваме Y оста - конвертирана в избраната единица
            val yAxis = holder.chart.axisLeft
            val convertedMax = UnitsManager.convertSpeed(maxSpeed, speedUnit)
            val has200Milestone = attempt.time0to200 > 0L || attempt.time100to200 > 0L
            val yFloorKmh = if (has200Milestone) 200f else 100f
            val topRef = maxOf(convertedMax, UnitsManager.convertSpeed(yFloorKmh, speedUnit))

            if (measurementMode == MeasurementMode.HUNDRED_TO_200) {
                yAxis.axisMinimum = UnitsManager.convertSpeed(100f, speedUnit)
            } else {
                yAxis.axisMinimum = 0f
            }
            yAxis.axisMaximum = topRef * 1.05f
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
            holder.chart.data = null
            holder.chart.invalidate()
        }
    }
    
    private fun updateAccelerationChart(holder: AttemptViewHolder, attempt: DragAttempt) {
        val (accelSamples, timestamps) = getAlignedAccelData(attempt)
        
        if (accelSamples.isNotEmpty() && timestamps.isNotEmpty()) {
            val maxAccel = accelSamples.maxOrNull() ?: 0f
            
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
            val rawRange = maxAccelValue - minAccel
            val minVisualRange = 0.20f
            val padding = if (rawRange > 0f) rawRange * 0.1f else 0f

            var axisMin = minAccel - padding
            var axisMax = maxAccelValue + padding

            // Prevent duplicate tick labels when the sampled range is very narrow.
            if ((axisMax - axisMin) < minVisualRange) {
                val center = (maxAccelValue + minAccel) / 2f
                axisMin = center - (minVisualRange / 2f)
                axisMax = center + (minVisualRange / 2f)
            }

            yAxis.axisMinimum = axisMin
            yAxis.axisMaximum = axisMax
            yAxis.setLabelCount(6, true)
            yAxis.granularity = 0.01f
            yAxis.setDrawZeroLine(true)
            yAxis.zeroLineColor = android.graphics.Color.GRAY
            yAxis.zeroLineWidth = 1f

            val axisRange = axisMax - axisMin
            
            // Форматиране за Y-оста - само цели числа за ускорението
            yAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val decimals = when {
                        axisRange < 0.15f -> 3
                        axisRange < 2f -> 2
                        else -> 1
                    }
                    return String.format(Locale.US, "%.${decimals}f", value)
                }
            }
            
            // X-оста вече е настроена по-горе с нормализирани времена
            
            holder.chart.invalidate()
        } else {
            holder.chart.data = null
            holder.chart.invalidate()
        }
    }
    
    private fun updateGForceChart(holder: AttemptViewHolder, attempt: DragAttempt) {
        val (gSamples, timestamps) = getAlignedGData(attempt)
        
        if (gSamples.isNotEmpty() && timestamps.isNotEmpty()) {
            val maxG = gSamples.maxOrNull() ?: 0f
            val minG = gSamples.minOrNull() ?: 0f
            
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
            holder.chart.data = null
            holder.chart.invalidate()
        }
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
                val time100Absolute = attempt.time0to100 / 1_000_000_000.0f
                val valueAt100 = milestoneMarkerY(attempt, PointTooltipMarker.PointType.SPEED_100, mode, time100Absolute)
                val entry100 = com.github.mikephil.charting.data.Entry(time100Absolute, valueAt100)
                val dataSet100 = com.github.mikephil.charting.data.LineDataSet(listOf(entry100), "").apply {
                    setDrawCircles(true)
                    setDrawValues(false)
                    lineWidth = 0f
                    circleRadius = 8f
                    circleHoleRadius = 4f
                    isHighlightEnabled = true
                    setDrawHighlightIndicators(false)
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
                val time200Absolute = if (measurementMode == MeasurementMode.HUNDRED_TO_200) {
                    attempt.time100to200 / 1_000_000_000.0f
                } else {
                    attempt.time0to200 / 1_000_000_000.0f
                }
                
                val valueAt200 = milestoneMarkerY(attempt, PointTooltipMarker.PointType.SPEED_200, mode, time200Absolute)
                
                val entry200 = com.github.mikephil.charting.data.Entry(time200Absolute, valueAt200)
                val dataSet200 = com.github.mikephil.charting.data.LineDataSet(listOf(entry200), "").apply {
                    setDrawCircles(true)
                    setDrawValues(false)
                    lineWidth = 0f
                    circleRadius = 8f
                    circleHoleRadius = 4f
                    isHighlightEnabled = true
                    setDrawHighlightIndicators(false)
                    setCircleColor(ContextCompat.getColor(context, R.color.accent_blue)) // 200 km/h - синя
                }
                existingData.addDataSet(dataSet200)
            }

            if (attempt.time0to402 > 0) {
                // КРИТИЧНО: Използваме абсолютно време (в секунди), не нормализирано
                // Това гарантира, че маркерът е на правилната X позиция спрямо tooltip-а
                val time402Absolute = attempt.time0to402 / 1_000_000_000.0f
                val valueAt402 = milestoneMarkerY(attempt, PointTooltipMarker.PointType.DISTANCE_402, mode, time402Absolute)
                val entry402 = com.github.mikephil.charting.data.Entry(time402Absolute, valueAt402)
                val dataSet402 = com.github.mikephil.charting.data.LineDataSet(listOf(entry402), "").apply {
                    setDrawCircles(true)
                    setDrawValues(false)
                    lineWidth = 0f
                    circleRadius = 8f
                    circleHoleRadius = 4f
                    isHighlightEnabled = true
                    setDrawHighlightIndicators(false)
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
            val (speedSamples, timestamps) = getAlignedSpeedDataForChart(attempt)
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

                val lineChart = chartView as? com.github.mikephil.charting.charts.LineChart
                val drawX = lineChart?.viewPortHandler?.contentCenter?.x ?: posX
                val drawY = if (lineChart != null) {
                    val pts = floatArrayOf(0f, actualValue)
                    lineChart.getTransformer(YAxis.AxisDependency.LEFT).pointValuesToPixel(pts)
                    pts[1]
                } else {
                    posY
                }
                
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
                            val speedUnit = UnitsManager.getSpeedUnit(context)
                            val speed100 = UnitsManager.convertSpeed(100f, speedUnit).toInt()
                            val speed200 = UnitsManager.convertSpeed(200f, speedUnit).toInt()
                            when (pointType) {
                                PointTooltipMarker.PointType.SPEED_100 -> {
                                    "0-$speed100 ${speedUnit.symbol}\n${String.format("%.2f", timeToShow)}s"
                                }
                                PointTooltipMarker.PointType.SPEED_200 -> {
                                    "0-$speed200 ${speedUnit.symbol}\n${String.format("%.2f", timeToShow)}s"
                                }
                                PointTooltipMarker.PointType.DISTANCE_402 -> {
                                    val speedAt402 = resolveAttemptTrapSpeedKmh(attempt) ?: getSpeedAtTime(attempt, timeToShow)
                                    val convertedSpeed = UnitsManager.convertSpeed(speedAt402, speedUnit)
                                    "0-${UnitsManager.getQuarterMileDistance(context)}\n${convertedSpeed.toInt()} ${speedUnit.symbol}\n${String.format("%.2f", timeToShow)}s"
                                }
                            }
                        }
                        ChartMode.ACCELERATION -> {
                            // Acceleration режим - показваме реалната стойност в g
                            "${String.format("%.2f", actualValue)} g\n${String.format("%.2f", timeToShow)}s"
                        }
                        ChartMode.G_FORCE -> {
                            // G-Force режим - показваме реалната стойност в G
                            "${String.format("%.2f", actualValue)} G\n${String.format("%.2f", timeToShow)}s"
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
                            val text = "${actualValue.toInt()} $unitSymbol\n${String.format("%.2f", timeAtPoint)}s"
                            text to 0xFFFF6020.toInt() // Orange #FF6020
                        }
                        ChartMode.ACCELERATION -> {
                            val text = "${String.format("%.2f", actualValue)} g\n${String.format("%.2f", timeAtPoint)}s"
                            text to 0xFF3486A9.toInt() // #3486A9
                        }
                        ChartMode.G_FORCE -> {
                            val text = "${String.format("%.2f", actualValue)} G\n${String.format("%.2f", timeAtPoint)}s"
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
                val arrow = 12f
                val contentTop = lineChart?.viewPortHandler?.contentTop() ?: 0f
                val contentBottom = lineChart?.viewPortHandler?.contentBottom() ?: Float.MAX_VALUE
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
                return MPPointF(0f, 0f)
            }
        }
        
        holder.chart.marker = smartMarker
        holder.chart.setTag(R.id.tag_drag_chart_marker, smartMarker)
        currentMarkerView = smartMarker
    }
    
    // Помощна функция - намира скоростта в даден момент
    private fun getSpeedAtTime(attempt: DragAttempt, timeSeconds: Float): Float {
        val (speedSamples, timestamps) = getAlignedSpeedDataForChart(attempt)
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

    private fun milestoneMarkerY(
        attempt: DragAttempt,
        type: PointTooltipMarker.PointType,
        mode: ChartMode,
        timeSeconds: Float
    ): Float {
        if (mode != ChartMode.SPEED) {
            return findValueAtTimeInterpolated(attempt, timeSeconds, mode)
        }
        val speedKmh = when (type) {
            PointTooltipMarker.PointType.SPEED_100 -> 100f
            PointTooltipMarker.PointType.SPEED_200 -> 200f
            PointTooltipMarker.PointType.DISTANCE_402 -> resolveAttemptTrapSpeedKmh(attempt)
        } ?: return findValueAtTimeInterpolated(attempt, timeSeconds, mode)
        return UnitsManager.convertSpeed(speedKmh, UnitsManager.getSpeedUnit(context))
    }

    // Променена функция - използва интерполация за точна стойност
    private fun findValueAtTimeInterpolated(attempt: DragAttempt, targetTimeSeconds: Float, mode: ChartMode): Float {
        return when (mode) {
            ChartMode.SPEED -> {
                val (speedSamples, timestamps) = getAlignedSpeedDataForChart(attempt)
                val rawKmh = interpolateValueAtTime(speedSamples, timestamps, targetTimeSeconds)
                // Точките/маркерите трябва да са в същата единица като линията на графиката.
                UnitsManager.convertSpeed(rawKmh, UnitsManager.getSpeedUnit(context))
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

    // Помощна функция - интерполира стойност по абсолютно време (секунди)
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

        // Ако времето е извън диапазона, връщаме последната/първата стойност
        return when {
            targetTimeSeconds < absoluteTimes.first() -> values.first()
            targetTimeSeconds > absoluteTimes.last() -> values.last()
            else -> values.lastOrNull() ?: 0f
        }
    }

    private fun getPrimaryTimeForSummary(attempt: DragAttempt): Long {
        return when (measurementMode) {
            MeasurementMode.ZERO_TO_100 -> attempt.time0to100
            MeasurementMode.ZERO_TO_200 -> attempt.time0to200
            MeasurementMode.HUNDRED_TO_200 -> attempt.time100to200
            MeasurementMode.QUARTER_MILE,
            MeasurementMode.ALL -> attempt.time0to402.takeIf { it > 0L }
                ?: attempt.distance402mTimeNs.takeIf { it > 0L }
                ?: -1L
        }
    }

    private fun formatSummaryTime(timeNs: Long): String {
        if (timeNs <= 0L) return "--"
        return String.format(Locale.US, "%.2f", timeNs / 1_000_000_000.0)
    }

    private fun bindSummaryTimesPreview(holder: AttemptViewHolder, attempt: DragAttempt) {
        val time100to200 = DragAttemptMetrics.resolve100To200SplitTimeNs(attempt)
        val time0to402 = attempt.time0to402.takeIf { it > 0L } ?: attempt.distance402mTimeNs.takeIf { it > 0L }

        holder.llRunPreviewCol0to100.visibility = View.VISIBLE
        holder.llRunPreviewCol100to200.visibility = View.VISIBLE
        holder.llRunPreviewCol0to200.visibility = View.VISIBLE
        holder.llRunPreviewCol0to402.visibility = View.VISIBLE

        val value0to100 = when (measurementMode) {
            MeasurementMode.ZERO_TO_100,
            MeasurementMode.ZERO_TO_200,
            MeasurementMode.ALL -> attempt.time0to100.takeIf { it > 0L }
            else -> null
        }

        val value100to200 = when (measurementMode) {
            MeasurementMode.HUNDRED_TO_200,
            MeasurementMode.ZERO_TO_200,
            MeasurementMode.ALL -> time100to200
            else -> null
        }

        val value0to200 = when (measurementMode) {
            MeasurementMode.ZERO_TO_200,
            MeasurementMode.ALL -> attempt.time0to200.takeIf { it > 0L }
            else -> null
        }

        val value0to402 = when (measurementMode) {
            MeasurementMode.QUARTER_MILE,
            MeasurementMode.ALL -> time0to402
            else -> null
        }

        holder.tvRunPreviewValue0to100.text = formatSummaryMetricTime(value0to100)
        holder.tvRunPreviewValue100to200.text = formatSummaryMetricTime(value100to200)
        holder.tvRunPreviewValue0to200.text = formatSummaryMetricTime(value0to200)
        holder.tvRunPreviewValue0to402.text = formatSummaryMetricTime(value0to402)
        holder.tvDuration.visibility = View.GONE
    }

    private fun bindSummaryPreviewLabels(holder: AttemptViewHolder, speedUnit: UnitsManager.SpeedUnit) {
        holder.tvRunPreviewLabel0to100.text =
            UnitsManager.formatDragSpeedIntervalLabel(0, 100, context)
        holder.tvRunPreviewLabel100to200.text =
            UnitsManager.formatDragSpeedIntervalLabel(100, 200, context)
        holder.tvRunPreviewLabel0to200.text =
            UnitsManager.formatDragSpeedIntervalLabel(0, 200, context)
        holder.tvRunPreviewLabel0to402.text =
            UnitsManager.formatDragZeroTo402IntervalLabel(context)
    }

    private fun formatSummaryMetricTime(timeNs: Long?): String {
        if (timeNs == null || timeNs <= 0L) return "--"
        return String.format(Locale.US, "%.2f", timeNs / 1_000_000_000.0)
    }

    private fun bindDeltaSummary(
        holder: AttemptViewHolder,
        primaryTimeNs: Long,
        isAllTimePb: Boolean,
        currentGlobalBestNs: Long?
    ) {
        holder.tvRunDeltaLabel.text = context.getString(R.string.drag_run_delta_vs_best)
        holder.tvRunDeltaLabel.visibility = View.VISIBLE
        holder.tvRunDeltaLabel.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))

        if (primaryTimeNs <= 0L) {
            holder.tvRunDeltaValue.text = "--"
            holder.tvRunDeltaValue.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            return
        }

        if (isAllTimePb || currentGlobalBestNs == null || currentGlobalBestNs <= 0L) {
            holder.tvRunDeltaValue.text = context.getString(R.string.drag_run_indicator_pb)
            holder.tvRunDeltaValue.setTextColor(ContextCompat.getColor(context, R.color.accent_gold))
            return
        }

        val deltaNs = primaryTimeNs - currentGlobalBestNs
        if (deltaNs == 0L) {
            holder.tvRunDeltaValue.text = context.getString(R.string.drag_run_indicator_pb)
            holder.tvRunDeltaValue.setTextColor(ContextCompat.getColor(context, R.color.accent_gold))
            return
        }

        holder.tvRunDeltaValue.text = String.format(Locale.US, "%+.2fs", deltaNs / 1_000_000_000.0)
        holder.tvRunDeltaValue.setTextColor(
            ContextCompat.getColor(
                context,
                if (deltaNs < 0L) R.color.accent_green else R.color.drag_run_delta_positive
            )
        )
    }

    private fun applyCollapsedRunCardStyle(
        holder: AttemptViewHolder,
        runNumber: Int
    ) {
        val normalizedRunNumber = runNumber.coerceAtLeast(1)
        val accentColorRes = if (normalizedRunNumber % 2 == 1) {
            R.color.drag_run_purple
        } else {
            R.color.drag_run_green
        }
        val accentColor = ContextCompat.getColor(context, accentColorRes)
        holder.runAccent.backgroundTintList = ColorStateList.valueOf(accentColor)
        holder.tvAttemptNumber.setTextColor(accentColor)
        holder.tvRunPrimaryTime.setTextColor(accentColor)
        holder.tvRunPrimaryUnit.setTextColor(withAlpha(accentColor, 0.7f))

        val strokeColor = withAlpha(accentColor, 0.45f)
        (holder.shellContainer.background as? GradientDrawable)?.setStroke(dpToPx(1), strokeColor)
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val clamped = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        return (color and 0x00FFFFFF) or (clamped shl 24)
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt().coerceAtLeast(1)
    }

    private fun bindGSummary(holder: AttemptViewHolder, attempt: DragAttempt) {
        val peak = getPreferredPeakGValue(attempt)
        val avgAbs = getPreferredAverageAbsGValue(attempt)

        val peakText = peak?.takeIf { it > 0f }?.let {
            String.format(Locale.US, "%.2fg", it)
        } ?: "--"
        val avgText = avgAbs?.takeIf { it > 0f }?.let {
            String.format(Locale.US, "%.2fg", it)
        } ?: "--"

        holder.tvAttemptPeakG.text = peakText
        holder.tvAttemptAvgG.text = avgText

        val progressRatio = when {
            peak != null && peak > 0f && avgAbs != null && avgAbs > 0f -> (avgAbs / peak).coerceIn(0f, 1f)
            peak != null && peak > 0f -> 0.75f
            else -> 0f
        }
        holder.pbAttemptGForce.max = 1000
        holder.pbAttemptGForce.progress = (progressRatio * 1000f).toInt()
        holder.pbAttemptGForce.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent_orange))
        holder.pbAttemptGForce.progressBackgroundTintList = ColorStateList.valueOf(withAlpha(ContextCompat.getColor(context, R.color.text_tertiary), 0.25f))
    }

    private fun bindAllModeBreakdown(holder: AttemptViewHolder, attempt: DragAttempt) {
        val shouldShowDistanceBreakdown =
            measurementMode == MeasurementMode.ALL || measurementMode == MeasurementMode.QUARTER_MILE
        if (!shouldShowDistanceBreakdown) {
            holder.llAllModeBreakdown.visibility = View.GONE
            return
        }

        holder.llAllModeBreakdown.visibility = View.VISIBLE
        val showPrimaryMetrics = measurementMode == MeasurementMode.ALL
        holder.llAllModePrimaryMetrics.visibility = if (showPrimaryMetrics) View.VISIBLE else View.GONE
        holder.vAllModePrimaryDivider.visibility = if (showPrimaryMetrics) View.VISIBLE else View.GONE

        bindAllModeBreakdownLabels(holder)

        if (showPrimaryMetrics) {
            holder.tvAllMetric0to100.text = formatCompactTime(attempt.time0to100.takeIf { it > 0L })
            holder.tvAllMetric100to200.text = formatCompactTime(attempt.time100to200.takeIf { it > 0L })
            holder.tvAllMetric0to200.text = formatCompactTime(attempt.time0to200.takeIf { it > 0L })
            holder.tvAllMetric0to402.text = formatCompactTime(attempt.time0to402.takeIf { it > 0L })
            holder.llAllMetricCol0to100.visibility = View.VISIBLE
            holder.llAllMetricCol100to200.visibility = View.VISIBLE
            holder.llAllMetricCol0to200.visibility = View.VISIBLE
            holder.llAllMetricCol0to402.visibility = View.VISIBLE
        }

        bindDistanceMetric(holder, attempt, 50, holder.tvAllDist50Time, holder.tvAllDist50Speed, holder.tvAllDist50Badge)
        bindDistanceMetric(holder, attempt, 100, holder.tvAllDist100Time, holder.tvAllDist100Speed, holder.tvAllDist100Badge)
        bindDistanceMetric(holder, attempt, 200, holder.tvAllDist200Time, holder.tvAllDist200Speed, holder.tvAllDist200Badge)
        bindDistanceMetric(holder, attempt, 300, holder.tvAllDist300Time, holder.tvAllDist300Speed, holder.tvAllDist300Badge)
        bindDistanceMetric(holder, attempt, 402, holder.tvAllDist402Time, holder.tvAllDist402Speed, holder.tvAllDist402Badge)
    }

    private fun bindAllModeBreakdownLabels(holder: AttemptViewHolder) {
        holder.tvAllMetricLabel0to100.text =
            UnitsManager.formatDragSpeedIntervalLabel(0, 100, context)
        holder.tvAllMetricLabel100to200.text =
            UnitsManager.formatDragSpeedIntervalLabel(100, 200, context)
        holder.tvAllMetricLabel0to200.text =
            UnitsManager.formatDragSpeedIntervalLabel(0, 200, context)
        holder.tvAllMetricLabel0to402.text =
            UnitsManager.formatDragZeroTo402IntervalLabel(context)

        holder.tvAllDist50Label.text = UnitsManager.formatDragSplitDistanceLabel(50, context)
        holder.tvAllDist100Label.text = UnitsManager.formatDragSplitDistanceLabel(100, context)
        holder.tvAllDist200Label.text = UnitsManager.formatDragSplitDistanceLabel(200, context)
        holder.tvAllDist300Label.text = UnitsManager.formatDragSplitDistanceLabel(300, context)
        holder.tvAllDist402Label.text = UnitsManager.formatDragSplitDistanceLabel(402, context)
    }

    private fun bindDistanceMetric(
        holder: AttemptViewHolder,
        attempt: DragAttempt,
        distanceMeters: Int,
        timeView: TextView,
        speedView: TextView,
        badgeView: TextView
    ) {
        val timeNs = getDistanceCrossingTimeNs(attempt, distanceMeters.toFloat())
        timeView.text = formatCompactTime(timeNs)

        if (timeNs == null) {
            speedView.text = "--"
            speedView.visibility = View.GONE
            badgeView.text = "--"
            badgeView.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
            return
        }

        val speedKmh = getDistanceCrossingSpeedKmh(attempt, distanceMeters, timeNs)
        if (speedKmh != null && speedKmh >= 0f) {
            speedView.text = formatDistanceSpeed(speedKmh)
            speedView.visibility = View.VISIBLE
        } else {
            speedView.text = "--"
            speedView.visibility = View.GONE
        }

        val profileBestAttemptId = profileDistanceBestAttemptIds[distanceMeters]
        val sessionBestAttemptId = sessionDistanceBestAttemptIds[distanceMeters]

        when {
            attempt.id == profileBestAttemptId -> {
                badgeView.text = context.getString(R.string.drag_badge_pb)
                badgeView.setTextColor(ContextCompat.getColor(context, R.color.accent_gold))
            }
            attempt.id == sessionBestAttemptId -> {
                badgeView.text = context.getString(R.string.drag_badge_best)
                badgeView.setTextColor(ContextCompat.getColor(context, R.color.accent_purple))
            }
            else -> {
                badgeView.text = "--"
                badgeView.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
            }
        }
    }

    private fun formatDistanceSpeed(speedKmh: Float): String {
        val speedUnit = UnitsManager.getSpeedUnit(context)
        val converted = UnitsManager.convertSpeed(speedKmh, speedUnit).toInt()
        return "$converted ${speedUnit.symbol}"
    }

    private fun getDistanceCrossingSpeedKmh(
        attempt: DragAttempt,
        distanceMeters: Int,
        timeNs: Long
    ): Float? {
        val storedSpeedKmh = when (distanceMeters) {
            50 -> attempt.distance50mSpeedKmh
            100 -> attempt.distance100mSpeedKmh
            200 -> attempt.distance200mSpeedKmh
            300 -> attempt.distance300mSpeedKmh
            402 -> attempt.distance402mSpeedKmh
            else -> -1f
        }
        if (storedSpeedKmh >= 0f) return storedSpeedKmh

        val interpolated = findValueAtTimeInterpolated(
            attempt,
            timeNs / 1_000_000_000.0f,
            ChartMode.SPEED
        )
        return interpolated.takeIf { it >= 0f }
    }

    private fun formatCompactTime(timeNs: Long?): String {
        if (timeNs == null || timeNs <= 0L) return "--"
        return String.format(Locale.US, "%.2f", timeNs / 1_000_000_000.0)
    }

    private fun getDistanceCrossingTimeNs(attempt: DragAttempt, targetDistanceM: Float): Long? {
        if (targetDistanceM <= 0f) return null

        val storedTimeNs = when (targetDistanceM.toInt()) {
            50 -> attempt.distance50mTimeNs
            100 -> attempt.distance100mTimeNs
            200 -> attempt.distance200mTimeNs
            300 -> attempt.distance300mTimeNs
            402 -> attempt.time0to402.takeIf { it > 0L } ?: attempt.distance402mTimeNs
            else -> -1L
        }
        if (storedTimeNs > 0L) return storedTimeNs

        if (targetDistanceM >= 402f && attempt.time0to402 > 0L) return attempt.time0to402

        val (speedSamples, timestamps) = getAlignedSpeedData(attempt)
        if (speedSamples.size < 2 || timestamps.size < 2) {
            return if (targetDistanceM >= 402f) attempt.time0to402.takeIf { it > 0L } else null
        }

        val startTime = timestamps.first()
        var accumulatedDistance = 0.0

        for (i in 1 until speedSamples.size) {
            val t0 = timestamps[i - 1]
            val t1 = timestamps[i]
            val deltaSec = (t1 - t0) / 1_000_000_000.0
            if (deltaSec <= 0.0) continue

            val v0 = (speedSamples[i - 1].coerceAtLeast(0f) * KMH_TO_MPS).toDouble()
            val v1 = (speedSamples[i].coerceAtLeast(0f) * KMH_TO_MPS).toDouble()
            val segmentDistance = ((v0 + v1) * 0.5) * deltaSec
            if (segmentDistance <= 0.0) continue

            val nextAccumulated = accumulatedDistance + segmentDistance
            if (targetDistanceM <= nextAccumulated) {
                val remain = (targetDistanceM - accumulatedDistance).coerceAtLeast(0.0)
                val ratio = (remain / segmentDistance).coerceIn(0.0, 1.0)
                val crossingTime = t0 + ((t1 - t0) * ratio).toLong()
                return (crossingTime - startTime).coerceAtLeast(0L)
            }

            accumulatedDistance = nextAccumulated
        }

        return if (targetDistanceM >= 402f) {
            attempt.time0to402.takeIf { it > 0L } ?: attempt.distance402mTimeNs.takeIf { it > 0L }
        } else {
            null
        }
    }

    private fun computeSessionDistanceBestAttemptIds(): Map<Int, Long> {
        val bestByDistance = mutableMapOf<Int, DistanceBestCandidate>()

        allSessionAttempts.forEach { attempt ->
            allModeDistanceTargets.forEach { distance ->
                val timeNs = getDistanceCrossingTimeNs(attempt, distance.toFloat()) ?: return@forEach
                val current = bestByDistance[distance]
                val candidate = DistanceBestCandidate(
                    timeNs = timeNs,
                    sessionTimestamp = 0L,
                    attemptTimestamp = attempt.timestamp,
                    attemptId = attempt.id
                )
                if (isBetterDistanceCandidate(candidate, current)) {
                    bestByDistance[distance] = candidate
                }
            }
        }

        return bestByDistance.mapValues { it.value.attemptId }
    }

    private fun computeProfileDistanceBestAttemptIds(): Map<Int, Long> {
        val bestByDistance = mutableMapOf<Int, DistanceBestCandidate>()

        DragStorage.loadDragSessions(context)
            .asSequence()
            .filter { it.profileId == profileId }
            .forEach { dragSession ->
                dragSession.attempts.forEach { attempt ->
                    allModeDistanceTargets.forEach { distance ->
                        val timeNs = getDistanceCrossingTimeNs(attempt, distance.toFloat()) ?: return@forEach
                        val current = bestByDistance[distance]
                        val candidate = DistanceBestCandidate(
                            timeNs = timeNs,
                            sessionTimestamp = dragSession.timestamp,
                            attemptTimestamp = attempt.timestamp,
                            attemptId = attempt.id
                        )
                        if (isBetterDistanceCandidate(candidate, current)) {
                            bestByDistance[distance] = candidate
                        }
                    }
                }
            }

        return bestByDistance.mapValues { it.value.attemptId }
    }

    private fun isBetterDistanceCandidate(
        candidate: DistanceBestCandidate,
        current: DistanceBestCandidate?
    ): Boolean {
        if (current == null) return true
        if (candidate.timeNs < current.timeNs) return true
        if (candidate.timeNs > current.timeNs) return false
        if (candidate.sessionTimestamp > current.sessionTimestamp) return true
        if (candidate.sessionTimestamp < current.sessionTimestamp) return false
        if (candidate.attemptTimestamp > current.attemptTimestamp) return true
        if (candidate.attemptTimestamp < current.attemptTimestamp) return false
        return candidate.attemptId > current.attemptId
    }

    private fun bindMetaRow(holder: AttemptViewHolder, attempt: DragAttempt) {
        val tempText = attempt.temperature?.let {
            UnitsManager.formatTemperature(it, context, decimals = 0)
        } ?: context.getString(R.string.drag_weather_temp_placeholder)
        val humidityPercent = attempt.humidity
        val humidityText = humidityPercent?.let { "$it%" }
            ?: context.getString(R.string.drag_weather_humidity_placeholder)
        val windText = attempt.windKph?.let {
            UnitsManager.formatStoredSpeed(it.toString(), context)
        } ?: context.getString(R.string.drag_weather_wind_placeholder)
        val clockText = if (attempt.timestamp > 0L) {
            SimpleDateFormat("HH:mm", LanguageManager.getCurrentLocale(context))
                .format(Date(attempt.timestamp))
        } else {
            "--:--"
        }

        val savedWeatherIcon = attempt.weatherIcon ?: R.drawable.ic_weather_cloudy
        val (weatherIconRes, weatherTintRes) = resolveWeatherIconStyle(savedWeatherIcon, humidityPercent)

        holder.ivAttemptWeatherTemp.setImageResource(weatherIconRes)
        holder.ivAttemptWeatherTemp.setColorFilter(ContextCompat.getColor(context, weatherTintRes))
        holder.tvAttemptTrackTempValue.text = tempText
        holder.tvAttemptHumidityValue.text = humidityText
        holder.tvAttemptWindValue.text = windText
        holder.tvAttemptTimeValue.text = clockText
    }

    private fun bindZeroTo200RunSplits(holder: AttemptViewHolder, attempt: DragAttempt) {
        val split0to100 = attempt.time0to100.takeIf { it > 0L }
        val split100to200 = DragAttemptMetrics.resolve100To200SplitTimeNs(attempt)

        holder.tvAttemptSplit0to100.text =
            "${UnitsManager.formatDragSpeedIntervalLabel(0, 100, context)}: ${formatInlineSplitTime(split0to100)}"
        holder.tvAttemptSplit100to200.text =
            "${UnitsManager.formatDragSpeedIntervalLabel(100, 200, context)}: ${formatInlineSplitTime(split100to200)}"
    }

    private fun formatInlineSplitTime(timeNs: Long?): String {
        if (timeNs == null || timeNs <= 0L) return "--"
        return String.format(Locale.US, "%.2f s", timeNs / 1_000_000_000.0)
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
            R.drawable.ic_weather_rainy -> R.color.accent_light
            R.drawable.ic_weather_snowy -> R.color.accent_light
            R.drawable.ic_weather_clear_night -> R.color.text_secondary_light
            R.drawable.ic_weather_cloudy,
            R.drawable.ic_weather_partly_cloudy -> R.color.text_tertiary
            else -> R.color.text_tertiary
        }

        return finalIcon to tintRes
    }

    private fun applyExpandedState(holder: AttemptViewHolder, expanded: Boolean) {
        holder.detailsContainer.visibility = if (expanded) View.VISIBLE else View.GONE
        holder.llRunTimesPreview.visibility =
            if (expanded && measurementMode == MeasurementMode.ALL) View.GONE else View.VISIBLE
        when {
            onAttemptClick != null -> {
                holder.tvAttemptChevron.visibility = View.GONE
            }
            inlineExpansionEnabled -> {
                holder.tvAttemptChevron.visibility = View.VISIBLE
                holder.tvAttemptChevron.text = if (expanded) "^" else "v"
            }
            else -> {
                holder.tvAttemptChevron.visibility = View.GONE
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
                holder.llZeroTo200RunSplits.visibility = View.GONE
            }
            MeasurementMode.ZERO_TO_200 -> {
                holder.tvTime0to100.visibility = View.VISIBLE
                holder.tvTime0to200.visibility = View.VISIBLE
                holder.tvTime100to200.visibility = View.GONE
                holder.tvTime0to402.visibility = View.GONE
                holder.llZeroTo200RunSplits.visibility = View.VISIBLE
            }
            MeasurementMode.HUNDRED_TO_200 -> {
                holder.tvTime0to100.visibility = View.GONE
                holder.tvTime0to200.visibility = View.GONE
                holder.tvTime100to200.visibility = View.VISIBLE
                holder.tvTime0to402.visibility = View.GONE
                holder.llZeroTo200RunSplits.visibility = View.GONE
            }
            MeasurementMode.QUARTER_MILE -> {
                holder.tvTime0to100.visibility = View.GONE
                holder.tvTime0to200.visibility = View.GONE
                holder.tvTime100to200.visibility = View.GONE
                holder.tvTime0to402.visibility = View.VISIBLE
                holder.llZeroTo200RunSplits.visibility = View.GONE
            }
            MeasurementMode.ALL -> {
                holder.tvTime0to100.visibility = View.VISIBLE
                holder.tvTime0to200.visibility = View.VISIBLE
                holder.tvTime100to200.visibility = View.VISIBLE
                holder.tvTime0to402.visibility = View.VISIBLE
                holder.llZeroTo200RunSplits.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int = attempts.size

    private fun resetChartHeight(holder: AttemptViewHolder) {
        val minHeightPx = (280 * context.resources.displayMetrics.density).toInt()
        val params = holder.chart.layoutParams
        if (params.height != minHeightPx) {
            params.height = minHeightPx
            holder.chart.layoutParams = params
        }
    }

    private fun expandChartToFillRemaining(holder: AttemptViewHolder) {
        val chart = holder.chart
        val minHeightPx = (280 * context.resources.displayMetrics.density).toInt()
        chart.post {
            if (holder.detailsContainer.visibility != View.VISIBLE) return@post
            val density = context.resources.displayMetrics.density
            val telemetry = holder.itemView.findViewById<View>(R.id.llAttemptTelemetry)
            val splits = holder.llZeroTo200RunSplits
            val telemetryPx = telemetry?.height?.takeIf { it > 0 } ?: (120 * density).toInt()
            val splitsPx = if (splits.visibility == View.VISIBLE) splits.height else 0
            val gapBelowChartPx = (6 * density).toInt()
            val screenBottomPadPx = (28 * density).toInt()
            val belowPx = telemetryPx + splitsPx + gapBelowChartPx + screenBottomPadPx
            val loc = IntArray(2)
            chart.getLocationOnScreen(loc)
            val root = holder.itemView.rootView
            val rootLoc = IntArray(2)
            root.getLocationOnScreen(rootLoc)
            val rootBottom = rootLoc[1] + root.height
            val remaining = rootBottom - loc[1] - belowPx
            val newHeight = remaining.coerceAtLeast(minHeightPx)
            val params = chart.layoutParams
            if (kotlin.math.abs(params.height - newHeight) > 2) {
                params.height = newHeight
                chart.layoutParams = params
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
    
    private fun formatTime(label: String, nanos: Long): String {
        return if (nanos > 0) {
            val seconds = nanos / 1_000_000_000.0
            "$label\n${DragTimeFormatter.formatSecondsWithUnit(seconds)}"
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
            "$label\n${DragTimeFormatter.formatSecondsWithUnit(displayTime)}"
        } else {
            DragTimeFormatter.formatSecondsWithUnit(displayTime)
        }
    }
}