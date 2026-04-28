package com.example.clinometer

import android.content.Context
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
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
                    tvSessionBestPeakGValue.text = formatPeakGValue(bestAttempt?.gSamples?.maxOrNull())
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
                    tvSessionBestPeakGValue.text = formatPeakGValue(bestAttempt?.gSamples?.maxOrNull())
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
                    tvSessionBestValue.setTextColor(ContextCompat.getColor(this, R.color.accent_orange))
                    tvSessionBestValueUnit.setTextColor(ContextCompat.getColor(this, R.color.accent_orange))
                    val bestAttemptInfo = findBestAttemptForMode(s, mode)
                    val bestAttempt = bestAttemptInfo?.second
                    tvSessionBestAtSpeedLabel.text = getString(R.string.drag_session_best_at_speed_label, "$speed200 $speedSymbol")
                    tvSessionBestAtSpeedValue.text = "$speed200 ${speedUnit.symbol}"
                    tvSessionBestPeakGLabel.text = getString(R.string.drag_session_best_peak_g_label).uppercase(Locale.getDefault())
                    tvSessionBestPeakGValue.text = formatPeakGValue(bestAttempt?.gSamples?.maxOrNull())
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
                    tvSessionBestPeakGValue.text = formatPeakGValue(bestAttempt?.gSamples?.maxOrNull())
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
            val runsCount = s.attempts.size
            tvRunsCount.text = "$runsCount RUNS"

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

                val globalAllTimeBestNs = getGlobalAllTimeBestForProfile(s, mode)
                val globalAllTimeBestAttemptId = getGlobalAllTimeBestAttemptIdForProfile(s, mode)
                val previousAllTimeBestNs = getPreviousAllTimeBestBeforeSession(s, mode)

                attemptsAdapter = DragAttemptsAdapter(
                    this@DragSessionDetailsActivity,
                    s.attempts,
                    s.profileId,
                    mode,
                    globalAllTimeBestNs,
                    globalAllTimeBestAttemptId,
                    previousAllTimeBestNs
                )
                rvAttempts.apply {
                    layoutManager = LinearLayoutManager(this@DragSessionDetailsActivity).apply {
                        reverseLayout = true
                        stackFromEnd = true
                    }
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

    private fun formatHeroTime(time: Long?): String {
        if (time == null || time <= 0L) return "--.---"
        return String.format("%.3f", time / 1_000_000_000.0)
    }

    private fun getAttemptTimeForMode(attempt: DragAttempt, mode: MeasurementMode): Long {
        return when (mode) {
            MeasurementMode.ZERO_TO_100 -> attempt.time0to100
            MeasurementMode.ZERO_TO_200 -> attempt.time0to200
            MeasurementMode.HUNDRED_TO_200 -> attempt.time100to200
            MeasurementMode.QUARTER_MILE -> attempt.time0to402
            MeasurementMode.ALL -> listOf(
                attempt.time0to402,
                attempt.time0to200,
                attempt.time100to200,
                attempt.time0to100,
                attempt.duration
            ).firstOrNull { it > 0L } ?: -1L
        }
    }

    private fun getPreviousAllTimeBestBeforeSession(
        session: DragSession,
        mode: MeasurementMode
    ): Long? {
        return DragStorage.getAllDragSessions(this)
            .asSequence()
            .filter { it.profileId == session.profileId }
            .filter { it.id != session.id }
            .filter { it.timestamp <= session.timestamp }
            .flatMap { it.attempts.asSequence() }
            .map { getAttemptTimeForMode(it, mode) }
            .filter { it > 0L }
            .minOrNull()
    }

    private fun getGlobalAllTimeBestForProfile(
        session: DragSession,
        mode: MeasurementMode
    ): Long? {
        return DragStorage.getAllDragSessions(this)
            .asSequence()
            .filter { it.profileId == session.profileId }
            .flatMap { it.attempts.asSequence() }
            .map { getAttemptTimeForMode(it, mode) }
            .filter { it > 0L }
            .minOrNull()
    }

    private fun getGlobalAllTimeBestAttemptIdForProfile(
        session: DragSession,
        mode: MeasurementMode
    ): Long? {
        var bestTime = Long.MAX_VALUE
        var bestSessionTimestamp = Long.MIN_VALUE
        var bestAttemptTimestamp = Long.MIN_VALUE
        var bestAttemptId: Long? = null

        DragStorage.getAllDragSessions(this)
            .asSequence()
            .filter { it.profileId == session.profileId }
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
            valueView.text = "--.---"
            valueView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            metaView.text = "NO RUN"
            metaView.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            return
        }

        val isGlobalPbForMetric = getGlobalAllTimeBestAttemptIdForProfile(session, metricMode) == bestAttempt.id

        valueView.text = formatHeroTime(bestTimeNs)
        val metricColor = ContextCompat.getColor(this, metricColorRes)
        valueView.setTextColor(metricColor)

        if (isGlobalPbForMetric) {
            val badgeText = "★ PB · RUN $bestRunNumber"
            val span = SpannableString(badgeText)
            span.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this, R.color.accent_gold)),
                0,
                1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            span.setSpan(
                ForegroundColorSpan(metricColor),
                2,
                badgeText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            metaView.text = span
        } else {
            metaView.text = "BEST · RUN $bestRunNumber"
            metaView.setTextColor(metricColor)
        }
    }

    private fun formatPeakGValue(peakG: Float?): String {
        if (peakG == null || peakG <= 0f) return "--"
        return String.format(Locale.US, "%.2fg", peakG)
    }

    private fun formatRunValue(attemptIndex: Int?): String {
        if (attemptIndex == null || attemptIndex < 0) {
            return getString(R.string.drag_session_best_run_placeholder).uppercase(Locale.getDefault())
        }
        return getString(R.string.drag_session_best_run_format, attemptIndex + 1).uppercase(Locale.getDefault())
    }

    private fun formatAttemptSpeed(attempt: DragAttempt?): String {
        if (attempt == null || attempt.maxSpeed <= 0f) return "--"
        return UnitsManager.formatSpeed(attempt.maxSpeed, this, 0)
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
            String.format(Locale.US, "%+.3fs", vsPrev)
        }

        val deltaColor = when {
            vsPrev == null -> ContextCompat.getColor(this, R.color.text_secondary)
            vsPrev < 0.0 -> ContextCompat.getColor(this, R.color.accent_purple)
            vsPrev > 0.0 -> ContextCompat.getColor(this, R.color.accent_red)
            else -> ContextCompat.getColor(this, R.color.text_secondary)
        }
        tvSessionBestVsPrevPbValue.setTextColor(deltaColor)

        tvSessionBestPrevBestValue.text = previousGlobalPbSeconds?.let {
            String.format(Locale.US, "%.3fs", it)
        } ?: "--"

        tvSessionBestAvgAllRunsValue.text = String.format(Locale.US, "%.3fs", avg)

        tvSessionBestConsistencyValue.text = if (consistency.isNaN()) {
            "--"
        } else {
            String.format(Locale.US, "±%.3fs", consistency)
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
    private val profileId: Long,
    private val measurementMode: MeasurementMode,
    private val globalAllTimeBestNs: Long?,
    private val globalAllTimeBestAttemptId: Long?,
    private val previousAllTimeBestNs: Long?
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

    private fun getBestPrimaryTimeNs(): Long {
        return attempts
            .map { getPrimaryTimeForSummary(it) }
            .filter { it > 0L }
            .minOrNull() ?: -1L
    }

    private fun getCurrentSessionBestAttemptId(bestPrimaryTimeNs: Long): Long? {
        if (bestPrimaryTimeNs <= 0L) return null
        val bestIndex = attempts.indexOfLast { getPrimaryTimeForSummary(it) == bestPrimaryTimeNs }
        return attempts.getOrNull(bestIndex)?.id
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
    
    // Константа за Y threshold множител (използва се и при drag и при tap)
    companion object {
        private const val SNAP_Y_MULTIPLIER = 10f // Увеличен за по-добро хващане в G-Force и Acceleration
        private const val KMH_TO_MPS = 1f / 3.6f
        
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
        val tvRunPrimaryTime: TextView = itemView.findViewById(R.id.tvRunPrimaryTime)
        val tvRunPrimaryUnit: TextView = itemView.findViewById(R.id.tvRunPrimaryUnit)
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
        val pbAttemptGForce: ProgressBar = itemView.findViewById(R.id.pbAttemptGForce)
        val tvAttemptPeakG: TextView = itemView.findViewById(R.id.tvAttemptPeakG)
        val tvAttemptAvgG: TextView = itemView.findViewById(R.id.tvAttemptAvgG)
        val llAllModeBreakdown: View = itemView.findViewById(R.id.llAllModeBreakdown)
        val llAllModePrimaryMetrics: View = itemView.findViewById(R.id.llAllModePrimaryMetrics)
        val vAllModePrimaryDivider: View = itemView.findViewById(R.id.vAllModePrimaryDivider)
        val tvAllMetric0to100: TextView = itemView.findViewById(R.id.tvAllMetric0to100)
        val tvAllMetric100to200: TextView = itemView.findViewById(R.id.tvAllMetric100to200)
        val tvAllMetric0to200: TextView = itemView.findViewById(R.id.tvAllMetric0to200)
        val tvAllMetric0to402: TextView = itemView.findViewById(R.id.tvAllMetric0to402)
        val tvAllDist50Time: TextView = itemView.findViewById(R.id.tvAllDist50Time)
        val tvAllDist100Time: TextView = itemView.findViewById(R.id.tvAllDist100Time)
        val tvAllDist200Time: TextView = itemView.findViewById(R.id.tvAllDist200Time)
        val tvAllDist300Time: TextView = itemView.findViewById(R.id.tvAllDist300Time)
        val tvAllDist402Time: TextView = itemView.findViewById(R.id.tvAllDist402Time)
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

    override fun onBindViewHolder(holder: AttemptViewHolder, position: Int) {
        val attempt = attempts[position]
        val context = holder.itemView.context
        val bestPrimaryTimeNs = getBestPrimaryTimeNs()
        val currentSessionBestAttemptId = getCurrentSessionBestAttemptId(bestPrimaryTimeNs)

        // ⚠️ КРИТИЧНО: Reset-вайте всички бутони ПЪРВО (RecyclerView recycling)
        holder.btnSpeed.setBackgroundResource(R.drawable.button_toggle_unselected)
        holder.btnSpeed.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        
        holder.btnAcceleration.setBackgroundResource(R.drawable.button_toggle_unselected)
        holder.btnAcceleration.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        
        holder.btnGForce.setBackgroundResource(R.drawable.button_toggle_unselected)
        holder.btnGForce.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        holder.btnGForce.visibility = View.GONE
        holder.btnGForce.isEnabled = false

        holder.tvAttemptNumber.text = context.getString(R.string.drag_run_short_format, position + 1)

        val primaryTimeNs = getPrimaryTimeForSummary(attempt)
        holder.tvRunPrimaryTime.text = formatSummaryTime(primaryTimeNs)
        holder.tvRunPrimaryUnit.text = if (primaryTimeNs > 0L) "s" else ""

        val speedUnit = UnitsManager.getSpeedUnit(context)
        val convertedMaxSpeed = if (attempt.maxSpeed > 0f) {
            UnitsManager.convertSpeed(attempt.maxSpeed, speedUnit)
        } else {
            null
        }
        holder.tvMaxSpeed.text = convertedMaxSpeed?.toInt()?.toString() ?: "--"
        holder.tvRunSpeedMeta.text = context.getString(
            R.string.drag_run_speed_at_finish,
            speedUnit.symbol.uppercase(Locale.getDefault())
        )

        val displayDuration = getMaxMeasuredTime(attempt)
        holder.tvDuration.text = if (displayDuration > 0.0) {
            context.getString(R.string.drag_attempt_duration, displayDuration)
        } else {
            context.getString(R.string.drag_meta_duration_prefix, "--")
        }

        val previousSessionBestNs = attempts
            .take(position)
            .map { getPrimaryTimeForSummary(it) }
            .filter { it > 0L }
            .minOrNull()

        val allTimeBestBeforeCurrent = listOfNotNull(
            previousAllTimeBestNs?.takeIf { it > 0L },
            previousSessionBestNs
        ).minOrNull()

        val currentGlobalBestNs = listOfNotNull(
            globalAllTimeBestNs?.takeIf { it > 0L },
            bestPrimaryTimeNs.takeIf { it > 0L }
        ).minOrNull()

        val isSessionBest = primaryTimeNs > 0L && attempt.id == currentSessionBestAttemptId
        val isAllTimePb =
            isSessionBest && primaryTimeNs > 0L && currentGlobalBestNs != null &&
                attempt.id == globalAllTimeBestAttemptId && primaryTimeNs == currentGlobalBestNs

        // Скриваме старите chip-бейджове - използваме само индикаторния блок вдясно.
        holder.tvRunBestBadge.visibility = View.GONE
        holder.tvRunPbBadge.visibility = View.GONE
        applyCollapsedRunCardStyle(holder, isSessionBest)
        bindDeltaSummary(
            holder,
            primaryTimeNs,
            isSessionBest,
            isAllTimePb,
            allTimeBestBeforeCurrent,
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

        val isExpanded = expandedAttemptIds.contains(attempt.id)
        applyExpandedState(holder, isExpanded)
        holder.summaryContainer.setOnClickListener {
            val currentlyExpanded = expandedAttemptIds.contains(attempt.id)
            if (currentlyExpanded) {
                expandedAttemptIds.remove(attempt.id)
            } else {
                expandedAttemptIds.add(attempt.id)
            }
            applyExpandedState(holder, !currentlyExpanded)
        }

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
                    if (kotlin.math.abs(x) < 0.0001f) return ""
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
        val specialPoints = mutableListOf<SpecialPoint>()
        val (speedSamples, timestamps) = getAlignedSpeedData(attempt)

        // 0-100 km/h - приоритет 1 (най-нисък, проверява се последен)
        // КРИТИЧНО: Използваме абсолютно време (в секунди), не нормализирано
        // Това гарантира, че snapping работи правилно спрямо абсолютните времена от tooltip-а
        val startTime = timestamps.first()
        if (attempt.time0to100 > 0 && measurementMode != MeasurementMode.HUNDRED_TO_200) {
            val time100Absolute = attempt.time0to100 / 1_000_000_000.0f
            val speedUnit = UnitsManager.getSpeedUnit(context)
            val y100 = when (mode) {
                ChartMode.SPEED -> UnitsManager.convertSpeed(100f, speedUnit)
                ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, time100Absolute, mode)
                ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, time100Absolute, mode)
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
                val speedUnit = UnitsManager.getSpeedUnit(context)
                val y200 = when (mode) {
                    ChartMode.SPEED -> UnitsManager.convertSpeed(200f, speedUnit)
                    ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, time200Absolute, mode)
                    ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, time200Absolute, mode)
                }
                specialPoints.add(SpecialPoint(time200Absolute, y200, PointTooltipMarker.PointType.SPEED_200, 2))
            }
        } else {
            if (attempt.time0to200 > 0) {
                // За други режими: използваме абсолютното време от attempt.time0to200
                val time200Absolute = attempt.time0to200 / 1_000_000_000.0f
                val speedUnit = UnitsManager.getSpeedUnit(context)
                val y200 = when (mode) {
                    ChartMode.SPEED -> UnitsManager.convertSpeed(200f, speedUnit)
                    ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, time200Absolute, mode)
                    ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, time200Absolute, mode)
                }
                specialPoints.add(SpecialPoint(time200Absolute, y200, PointTooltipMarker.PointType.SPEED_200, 2))
            }
        }

        // 0-402m - приоритет 3 (най-висок, проверява се първи)
        // КРИТИЧНО: Използваме абсолютно време (в секунди), не нормализирано
        if (attempt.time0to402 > 0) {
            val time402Absolute = attempt.time0to402 / 1_000_000_000.0f
            if (time402Absolute > 0f) {
                val y402 = findValueAtTimeInterpolated(attempt, time402Absolute, mode)
                specialPoints.add(SpecialPoint(time402Absolute, y402, PointTooltipMarker.PointType.DISTANCE_402, 3))
            }
        }

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
                val snapThreshold = 0.16f // По-тесен радиус за snapping около самата точка
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
                    chart.context,
                    SNAP_Y_MULTIPLIER
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
                val modeDataSetIndex = (getCurrentModeDataSetIndex(chart, activeMode)
                    ?: touchedDataSetIndex).coerceIn(0, (chart.data?.dataSets?.size ?: 1) - 1)
                val specialDataSetIndex = if (isSpecial && specialPointType != null) {
                    findSpecialPointDataSetIndex(chart, specialPointType, finalEntry.x)
                } else {
                    null
                }
                val snappedDataSetIndex = (specialDataSetIndex ?: modeDataSetIndex)
                    .coerceIn(0, (chart.data?.dataSets?.size ?: 1) - 1)
                val finalHighlight = com.github.mikephil.charting.highlight.Highlight(
                    finalEntry.x,
                    finalEntry.y,
                    snappedDataSetIndex
                )

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
                    // с финалния snapped highlight за центриране върху точката.
                    markerView.refreshContent(finalEntry, finalHighlight)
                }

                // Винаги използваме финалния snapped highlight (ако е special point,
                // това е центърът на точката), за да няма изместване вляво/вдясно.
                chart.highlightValue(finalHighlight, false)
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
                // Нищо – използваме същата логика като CompareAttemptsActivity.
                // onValueSelected + snapping управляват показването на балона при tap/drag.
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
        // Предпочитаме acceleration, пресметнато от каноничните speed/time семпли,
        // защото те са основата за всички split времена и са най-надеждни за визуализация.
        val (speedSamples, speedTimes) = getAlignedSpeedData(attempt)
        val derived = deriveAccelerationFromSpeedSamples(speedSamples, speedTimes)
        if (derived.first.isNotEmpty() && derived.second.isNotEmpty()) {
            return derived
        }

        // Fallback за legacy опити без speed времеви ред.
        val vals = attempt.gpsAccelSamples
        val times = attempt.gpsTimeStamps
        val limit = minOf(vals.size, times.size)
        return vals.take(limit) to times.take(limit)
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

        bindExpandedChartHeader(holder, attempt, mode)
        
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
            ChartMode.SPEED -> "SPEED"
            ChartMode.ACCELERATION -> "ACCEL"
            ChartMode.G_FORCE -> "G-FORCE"
        }

        holder.tvChartTitle.text = "$modeLabel x TIME - $rangeLabel"

        val primaryTimeNs = getPrimaryTimeForSummary(attempt)
        holder.tvChartStats.text = if (primaryTimeNs > 0L) {
            "0 -> ${String.format(Locale.US, "%.3fs", primaryTimeNs / 1_000_000_000.0)}"
        } else {
            "0 -> --"
        }
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
                val topRef = maxOf(convertedMax, threshold200)
                yAxis.axisMaximum = topRef * 1.05f
            } else {
                yAxis.axisMinimum = 0f
                val topRef = maxOf(convertedMax, threshold200)
                yAxis.axisMaximum = topRef * 1.05f
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
                val speedUnit = UnitsManager.getSpeedUnit(holder.itemView.context)
                val valueAt100 = when (mode) {
                    ChartMode.SPEED -> UnitsManager.convertSpeed(100f, speedUnit)
                    ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, time100Absolute, mode)
                    ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, time100Absolute, mode)
                }
                val entry100 = com.github.mikephil.charting.data.Entry(time100Absolute, valueAt100)
                val dataSet100 = com.github.mikephil.charting.data.LineDataSet(listOf(entry100), "").apply {
                    setDrawCircles(true)
                    setDrawValues(false)
                    lineWidth = 0f
                    circleRadius = 8f
                    circleHoleRadius = 4f
                    isHighlightEnabled = true
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
                    ChartMode.ACCELERATION -> findValueAtTimeInterpolated(attempt, time200Absolute, mode)
                    ChartMode.G_FORCE -> findValueAtTimeInterpolated(attempt, time200Absolute, mode)
                }
                
                val entry200 = com.github.mikephil.charting.data.Entry(time200Absolute, valueAt200)
                val dataSet200 = com.github.mikephil.charting.data.LineDataSet(listOf(entry200), "").apply {
                    setDrawCircles(true)
                    setDrawValues(false)
                    lineWidth = 0f
                    circleRadius = 8f
                    circleHoleRadius = 4f
                    isHighlightEnabled = true
                    setCircleColor(ContextCompat.getColor(context, R.color.accent_blue)) // 200 km/h - синя
                }
                existingData.addDataSet(dataSet200)
            }

            if (attempt.time0to402 > 0) {
                // КРИТИЧНО: Използваме абсолютно време (в секунди), не нормализирано
                // Това гарантира, че маркерът е на правилната X позиция спрямо tooltip-а
                val time402Absolute = attempt.time0to402 / 1_000_000_000.0f
                val valueAt402 = findValueAtTimeInterpolated(attempt, time402Absolute, mode)
                val entry402 = com.github.mikephil.charting.data.Entry(time402Absolute, valueAt402)
                val dataSet402 = com.github.mikephil.charting.data.LineDataSet(listOf(entry402), "").apply {
                    setDrawCircles(true)
                    setDrawValues(false)
                    lineWidth = 0f
                    circleRadius = 8f
                    circleHoleRadius = 4f
                    isHighlightEnabled = true
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
            MeasurementMode.QUARTER_MILE -> attempt.time0to402
            MeasurementMode.ALL -> getBestAvailableTime(attempt)
        }
    }

    private fun getBestAvailableTime(attempt: DragAttempt): Long {
        return listOf(
            attempt.time0to402,
            attempt.time0to200,
            attempt.time100to200,
            attempt.time0to100,
            attempt.duration
        ).firstOrNull { it > 0L } ?: -1L
    }

    private fun formatSummaryTime(timeNs: Long): String {
        if (timeNs <= 0L) return "--"
        return String.format(Locale.US, "%.3f", timeNs / 1_000_000_000.0)
    }

    private fun bindDeltaSummary(
        holder: AttemptViewHolder,
        primaryTimeNs: Long,
        isSessionBest: Boolean,
        isAllTimePb: Boolean,
        allTimeBestBeforeCurrent: Long?,
        currentGlobalBestNs: Long?
    ) {
        if (primaryTimeNs <= 0L) {
            holder.tvRunDeltaLabel.text = context.getString(R.string.drag_run_delta_vs_best)
            holder.tvRunDeltaValue.text = "--"
            holder.tvRunDeltaValue.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            holder.tvRunDeltaLabel.visibility = View.VISIBLE
            return
        }

        if (isAllTimePb) {
            holder.tvRunDeltaValue.text = context.getString(R.string.drag_run_indicator_pb)
            holder.tvRunDeltaValue.setTextColor(ContextCompat.getColor(context, R.color.accent_gold))

            if (allTimeBestBeforeCurrent != null && allTimeBestBeforeCurrent > 0L) {
                val deltaNs = primaryTimeNs - allTimeBestBeforeCurrent
                val deltaText = String.format(Locale.US, "%+.3fs", deltaNs / 1_000_000_000.0)
                holder.tvRunDeltaLabel.text = context.getString(R.string.drag_run_delta_all_time_value, deltaText)
                holder.tvRunDeltaLabel.setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (deltaNs < 0L) R.color.accent_green else R.color.drag_run_delta_positive
                    )
                )
            } else {
                holder.tvRunDeltaLabel.text = context.getString(R.string.drag_run_delta_all_time)
                holder.tvRunDeltaLabel.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
            }
            holder.tvRunDeltaLabel.visibility = View.VISIBLE
            return
        }

        if (isSessionBest) {
            if (currentGlobalBestNs != null && currentGlobalBestNs > 0L && primaryTimeNs > currentGlobalBestNs) {
                val deltaNs = primaryTimeNs - currentGlobalBestNs
                holder.tvRunDeltaLabel.text = context.getString(R.string.drag_run_delta_vs_best)
                holder.tvRunDeltaValue.text = String.format(Locale.US, "%+.3fs", deltaNs / 1_000_000_000.0)
                holder.tvRunDeltaLabel.visibility = View.VISIBLE
                holder.tvRunDeltaLabel.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                holder.tvRunDeltaValue.setTextColor(ContextCompat.getColor(context, R.color.drag_run_delta_positive))
                return
            }

            holder.tvRunDeltaValue.text = context.getString(R.string.drag_run_indicator_crown)
            holder.tvRunDeltaValue.setTextColor(ContextCompat.getColor(context, R.color.accent_gold))
            holder.tvRunDeltaLabel.text = ""
            holder.tvRunDeltaLabel.visibility = View.INVISIBLE
            return
        }

        val deltaNs = if (currentGlobalBestNs != null && currentGlobalBestNs > 0L) {
            primaryTimeNs - currentGlobalBestNs
        } else {
            0L
        }
        holder.tvRunDeltaLabel.text = context.getString(R.string.drag_run_delta_vs_best)
        holder.tvRunDeltaValue.text = String.format(Locale.US, "%+.3fs", deltaNs / 1_000_000_000.0)
        holder.tvRunDeltaLabel.visibility = View.VISIBLE
        holder.tvRunDeltaLabel.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
        holder.tvRunDeltaValue.setTextColor(
            ContextCompat.getColor(
                context,
                if (deltaNs > 0L) R.color.drag_run_delta_positive else R.color.text_secondary
            )
        )
    }

    private fun applyCollapsedRunCardStyle(
        holder: AttemptViewHolder,
        isSessionBest: Boolean
    ) {
        val accentColor = ContextCompat.getColor(
            context,
            if (isSessionBest) R.color.drag_run_purple else R.color.drag_run_green
        )
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
        val peak = attempt.gSamples.maxOrNull()
        val avgAbs = if (attempt.gSamples.isEmpty()) null else attempt.gSamples.map { kotlin.math.abs(it) }.average().toFloat()

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

        if (showPrimaryMetrics) {
            holder.tvAllMetric0to100.text = formatCompactTime(attempt.time0to100.takeIf { it > 0L })
            holder.tvAllMetric100to200.text = formatCompactTime(attempt.time100to200.takeIf { it > 0L })
            holder.tvAllMetric0to200.text = formatCompactTime(attempt.time0to200.takeIf { it > 0L })
            holder.tvAllMetric0to402.text = formatCompactTime(attempt.time0to402.takeIf { it > 0L })
        }

        bindDistanceMetric(holder, attempt, 50, holder.tvAllDist50Time, holder.tvAllDist50Badge)
        bindDistanceMetric(holder, attempt, 100, holder.tvAllDist100Time, holder.tvAllDist100Badge)
        bindDistanceMetric(holder, attempt, 200, holder.tvAllDist200Time, holder.tvAllDist200Badge)
        bindDistanceMetric(holder, attempt, 300, holder.tvAllDist300Time, holder.tvAllDist300Badge)
        bindDistanceMetric(holder, attempt, 402, holder.tvAllDist402Time, holder.tvAllDist402Badge)
    }

    private fun bindDistanceMetric(
        holder: AttemptViewHolder,
        attempt: DragAttempt,
        distanceMeters: Int,
        timeView: TextView,
        badgeView: TextView
    ) {
        val timeNs = getDistanceCrossingTimeNs(attempt, distanceMeters.toFloat())
        timeView.text = formatCompactTime(timeNs)

        if (timeNs == null) {
            badgeView.text = "--"
            badgeView.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
            return
        }

        val profileBestAttemptId = profileDistanceBestAttemptIds[distanceMeters]
        val sessionBestAttemptId = sessionDistanceBestAttemptIds[distanceMeters]

        when {
            attempt.id == profileBestAttemptId -> {
                badgeView.text = "★ PB"
                badgeView.setTextColor(ContextCompat.getColor(context, R.color.accent_gold))
            }
            attempt.id == sessionBestAttemptId -> {
                badgeView.text = "★ BEST"
                badgeView.setTextColor(ContextCompat.getColor(context, R.color.accent_purple))
            }
            else -> {
                badgeView.text = "--"
                badgeView.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
            }
        }
    }

    private fun formatCompactTime(timeNs: Long?): String {
        if (timeNs == null || timeNs <= 0L) return "--"
        return String.format(Locale.US, "%.3f", timeNs / 1_000_000_000.0)
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

        attempts.forEach { attempt ->
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

        DragStorage.getAllDragSessions(context)
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
            String.format(Locale.US, "%.0f km/h", it)
        } ?: context.getString(R.string.drag_weather_wind_placeholder)
        val clockText = if (attempt.timestamp > 0L) {
            android.text.format.DateFormat.format("HH:mm", java.util.Date(attempt.timestamp)).toString()
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
        val split100to200 = resolve100To200SplitTimeNs(attempt)

        holder.tvAttemptSplit0to100.text = "0-100: ${formatInlineSplitTime(split0to100)}"
        holder.tvAttemptSplit100to200.text = "100-200: ${formatInlineSplitTime(split100to200)}"
    }

    private fun resolve100To200SplitTimeNs(attempt: DragAttempt): Long? {
        val directSplit = attempt.time100to200.takeIf { it > 0L }
        if (directSplit != null) return directSplit

        val time0to100 = attempt.time0to100.takeIf { it > 0L } ?: return null
        val time0to200 = attempt.time0to200.takeIf { it > 0L } ?: return null
        val derivedSplit = time0to200 - time0to100
        return derivedSplit.takeIf { it > 0L }
    }

    private fun formatInlineSplitTime(timeNs: Long?): String {
        if (timeNs == null || timeNs <= 0L) return "--"
        return String.format(Locale.US, "%.3f s", timeNs / 1_000_000_000.0)
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
        holder.tvAttemptChevron.text = if (expanded) "^" else "v"
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