package com.revix.app

import android.widget.LinearLayout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.revix.app.R
import com.revix.app.drag.DragAttemptMetrics
import com.revix.app.settings.LanguageManager
import com.revix.app.settings.UnitsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DragSessionAdapter(
    private val sessions: MutableList<DragSession>,
    private val onItemClick: (DragSession) -> Unit,
    private val onDeleteClick: (DragSession) -> Unit
) : RecyclerView.Adapter<DragSessionAdapter.DragSessionViewHolder>() {

    private enum class SessionMode {
        ALL,
        ZERO_TO_100,
        ZERO_TO_200,
        HUNDRED_TO_200,
        QUARTER_MILE,
        UNKNOWN
    }

    private enum class MetricKey {
        ZERO_TO_100,
        HUNDRED_TO_200,
        ZERO_TO_200,
        QUARTER_MILE
    }

    inner class DragSessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val llAllModeContent: LinearLayout = itemView.findViewById(R.id.llAllModeContent)

        val tvAllDay: TextView = itemView.findViewById(R.id.tvAllDay)
        val tvAllMonth: TextView = itemView.findViewById(R.id.tvAllMonth)
        val tvAllSessionTitle: TextView = itemView.findViewById(R.id.tvAllSessionTitle)
        val tvAllPbBadge: TextView = itemView.findViewById(R.id.tvAllPbBadge)
        val tvAllModeBadge: TextView = itemView.findViewById(R.id.tvAllModeBadge)
        val tvAllRaceBoxBadge: TextView = itemView.findViewById(R.id.tvAllRaceBoxBadge)
        val tvAllPrimaryLabel: TextView = itemView.findViewById(R.id.tvAllPrimaryLabel)
        val tvAllPrimaryTime: TextView = itemView.findViewById(R.id.tvAllPrimaryTime)
        val tvAllMetric0to100: TextView = itemView.findViewById(R.id.tvAllMetric0to100)
        val tvAllMetric100to200: TextView = itemView.findViewById(R.id.tvAllMetric100to200)
        val tvAllMetric0to200: TextView = itemView.findViewById(R.id.tvAllMetric0to200)
        val tvAllMetric0to402: TextView = itemView.findViewById(R.id.tvAllMetric0to402)
        val tvAllRuns: TextView = itemView.findViewById(R.id.tvAllRuns)
        val llAllTrapCol: View = itemView.findViewById(R.id.llAllTrapCol)
        val tvAllTrap: TextView = itemView.findViewById(R.id.tvAllTrap)
        val tvAllPeakG: TextView = itemView.findViewById(R.id.tvAllPeakG)

        val btnOptions: ImageButton = itemView.findViewById(R.id.btnSessionOptions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DragSessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_drag_session, parent, false)
        return DragSessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: DragSessionViewHolder, position: Int) {
        val session = sessions[position]
        val mode = resolveSessionMode(session)

        holder.tvAllModeBadge.text = getModeLabel(holder.itemView.context, mode)
        holder.tvAllModeBadge.visibility = View.VISIBLE
        com.revix.app.racebox.RaceBoxSessionUi.bindLabel(holder.tvAllRaceBoxBadge, session.recordedWithRaceBox)

        if (mode == SessionMode.ALL) {
            bindAllMode(holder, session, position)
        } else {
            bindSingleMode(holder, session, mode, position)
        }

        holder.itemView.setOnClickListener { onItemClick(session) }

        holder.btnOptions.setOnClickListener {
            onDeleteClick(session)
        }
    }

    override fun getItemCount(): Int = sessions.size

    private fun bindSingleMode(holder: DragSessionViewHolder, session: DragSession, mode: SessionMode, position: Int) {
        val context = holder.itemView.context
        val color0to100 = ContextCompat.getColor(context, R.color.accent_green)
        val color0to200 = ContextCompat.getColor(context, R.color.accent_blue)
        val color100to200 = ContextCompat.getColor(context, R.color.accent_purple)
        val color0to402 = ContextCompat.getColor(context, R.color.accent_red)
        val inactiveColor = ContextCompat.getColor(context, R.color.text_tertiary)
        val modeLabel = getModeLabel(context, mode)
        val primaryTime = getModeBestTime(session, mode)
        val metric0to100 = getBestMetricTime(session, SessionMode.ZERO_TO_100)
        val metric0to200 = getBestMetricTime(session, SessionMode.ZERO_TO_200)
        val metric100to200 = getBestMetricTime(session, SessionMode.HUNDRED_TO_200)
        val metric0to402 = getBestMetricTime(session, SessionMode.QUARTER_MILE)

        holder.llAllModeContent.visibility = View.VISIBLE

        holder.tvAllDay.text = formatSessionDay(session.timestamp, context)
        holder.tvAllMonth.text = formatSessionMonth(session.timestamp, context)
        holder.tvAllSessionTitle.text = buildAllModeTitle(context, session, position)
        holder.tvAllModeBadge.text = modeLabel
        holder.tvAllPbBadge.visibility = if (isGlobalPbForMode(session, mode)) View.VISIBLE else View.GONE

        holder.tvAllPrimaryLabel.text = holder.itemView.context.getString(R.string.drag_session_adapter_best, modeLabel)
        holder.tvAllPrimaryTime.text = formatTimeWithoutUnit(primaryTime)
        holder.tvAllPrimaryTime.setTextColor(
            when (mode) {
                SessionMode.ZERO_TO_100 -> color0to100
                SessionMode.ZERO_TO_200 -> color0to200
                SessionMode.HUNDRED_TO_200 -> color100to200
                SessionMode.QUARTER_MILE -> color0to402
                else -> inactiveColor
            }
        )

        holder.tvAllMetric0to100.text = formatMetricChipNullable(context, MetricKey.ZERO_TO_100, metric0to100, false)
        holder.tvAllMetric100to200.text = formatMetricChipNullable(context, MetricKey.HUNDRED_TO_200, metric100to200, false)
        holder.tvAllMetric0to200.text = formatMetricChipNullable(context, MetricKey.ZERO_TO_200, metric0to200, false)
        holder.tvAllMetric0to402.text = formatMetricChipNullable(context, MetricKey.QUARTER_MILE, metric0to402, false)

        val active0to100 = when (mode) {
            SessionMode.ZERO_TO_200 -> metric0to100 != null
            else -> mode == SessionMode.ZERO_TO_100
        }
        val active100to200 = when (mode) {
            SessionMode.ZERO_TO_200 -> metric100to200 != null
            else -> mode == SessionMode.HUNDRED_TO_200
        }
        val active0to200 = when (mode) {
            SessionMode.ZERO_TO_200 -> metric0to200 != null
            else -> mode == SessionMode.ZERO_TO_200
        }
        val active0to402 = mode == SessionMode.QUARTER_MILE

        holder.tvAllMetric0to100.setTextColor(if (active0to100) color0to100 else inactiveColor)
        holder.tvAllMetric100to200.setTextColor(if (active100to200) color100to200 else inactiveColor)
        holder.tvAllMetric0to200.setTextColor(if (active0to200) color0to200 else inactiveColor)
        holder.tvAllMetric0to402.setTextColor(if (active0to402) color0to402 else inactiveColor)

        holder.tvAllMetric0to100.alpha = if (active0to100) 1f else 0.58f
        holder.tvAllMetric100to200.alpha = if (active100to200) 1f else 0.58f
        holder.tvAllMetric0to200.alpha = if (active0to200) 1f else 0.58f
        holder.tvAllMetric0to402.alpha = if (active0to402) 1f else 0.58f

        holder.tvAllRuns.text = session.attempts.size.toString()
        bindSessionTrap(holder, session)
        val peakG = resolveSessionPeakGValue(session)
        holder.tvAllPeakG.text = peakG?.let { String.format(Locale.US, "%.2fg", it) } ?: "--"
    }

    private fun bindAllMode(holder: DragSessionViewHolder, session: DragSession, position: Int) {
        val context = holder.itemView.context
        val color0to100 = ContextCompat.getColor(context, R.color.accent_green)
        val color0to200 = ContextCompat.getColor(context, R.color.accent_blue)
        val color100to200 = ContextCompat.getColor(context, R.color.accent_purple)
        val color0to402 = ContextCompat.getColor(context, R.color.accent_red)

        holder.llAllModeContent.visibility = View.VISIBLE

        holder.tvAllDay.text = formatSessionDay(session.timestamp, context)
        holder.tvAllMonth.text = formatSessionMonth(session.timestamp, context)

        holder.tvAllSessionTitle.text = buildAllModeTitle(context, session, position)
        holder.tvAllPbBadge.visibility = if (hasAnyGlobalPb(session)) View.VISIBLE else View.GONE

        val metrics = listOf(
            MetricKey.ZERO_TO_100 to session.best0to100.takeIf { it > 0L },
            MetricKey.HUNDRED_TO_200 to session.best100to200.takeIf { it > 0L },
            MetricKey.ZERO_TO_200 to session.best0to200.takeIf { it > 0L },
            MetricKey.QUARTER_MILE to session.best0to402.takeIf { it > 0L }
        )

        val fastestMetric = metrics
            .filter { it.second != null }
            .minByOrNull { it.second ?: Long.MAX_VALUE }
            ?.first

        val primaryMetric = if (session.best0to402 > 0L) MetricKey.QUARTER_MILE else fastestMetric
        val primaryTime = when (primaryMetric) {
            MetricKey.ZERO_TO_100 -> session.best0to100.takeIf { it > 0L }
            MetricKey.HUNDRED_TO_200 -> session.best100to200.takeIf { it > 0L }
            MetricKey.ZERO_TO_200 -> session.best0to200.takeIf { it > 0L }
            MetricKey.QUARTER_MILE -> session.best0to402.takeIf { it > 0L }
            null -> null
        }

        holder.tvAllPrimaryLabel.text = if (primaryMetric != null) {
            context.getString(
                R.string.drag_session_adapter_best,
                metricDisplayLabel(context, primaryMetric)
            )
        } else {
            context.getString(R.string.drag_best_label)
        }
        holder.tvAllPrimaryTime.text = formatTimeWithoutUnit(primaryTime)
        holder.tvAllPrimaryTime.setTextColor(
            when (primaryMetric) {
                MetricKey.ZERO_TO_100 -> color0to100
                MetricKey.HUNDRED_TO_200 -> color100to200
                MetricKey.ZERO_TO_200 -> color0to200
                MetricKey.QUARTER_MILE -> color0to402
                null -> ContextCompat.getColor(context, R.color.drag_run_purple)
            }
        )

        holder.tvAllMetric0to100.text = formatMetricChip(context, MetricKey.ZERO_TO_100, session.best0to100, fastestMetric == MetricKey.ZERO_TO_100)
        holder.tvAllMetric100to200.text = formatMetricChip(context, MetricKey.HUNDRED_TO_200, session.best100to200, fastestMetric == MetricKey.HUNDRED_TO_200)
        holder.tvAllMetric0to200.text = formatMetricChip(context, MetricKey.ZERO_TO_200, session.best0to200, fastestMetric == MetricKey.ZERO_TO_200)
        holder.tvAllMetric0to402.text = formatMetricChip(context, MetricKey.QUARTER_MILE, session.best0to402, fastestMetric == MetricKey.QUARTER_MILE)
        holder.tvAllMetric0to100.setTextColor(color0to100)
        holder.tvAllMetric100to200.setTextColor(color100to200)
        holder.tvAllMetric0to200.setTextColor(color0to200)
        holder.tvAllMetric0to402.setTextColor(color0to402)

        holder.tvAllRuns.text = session.attempts.size.toString()
        bindSessionTrap(holder, session)

        val peakG = resolveSessionPeakGValue(session)
        holder.tvAllPeakG.text = peakG?.let { String.format(Locale.US, "%.2fg", it) } ?: "--"
    }

    private fun buildAllModeTitle(context: android.content.Context, session: DragSession, position: Int): String {
        val name = session.name?.trim().orEmpty()
        if (name.isNotBlank()) return name

        return context.getString(R.string.drag_compare_session_title_fallback, position + 1)
    }

    private fun metricDisplayLabel(context: android.content.Context, key: MetricKey): String {
        return when (key) {
            MetricKey.ZERO_TO_100 -> UnitsManager.formatDragSpeedIntervalLabel(0, 100, context)
            MetricKey.HUNDRED_TO_200 -> UnitsManager.formatDragSpeedIntervalLabel(100, 200, context)
            MetricKey.ZERO_TO_200 -> UnitsManager.formatDragSpeedIntervalLabel(0, 200, context)
            MetricKey.QUARTER_MILE -> UnitsManager.formatDragZeroTo402IntervalLabel(context)
        }
    }

    private fun formatMetricChip(
        context: android.content.Context,
        key: MetricKey,
        timeNs: Long,
        isFastest: Boolean
    ): String {
        val suffix = if (isFastest && timeNs > 0L) " ★" else ""
        return "${metricDisplayLabel(context, key)} · ${formatTime(timeNs)}$suffix"
    }

    private fun formatMetricChipNullable(
        context: android.content.Context,
        key: MetricKey,
        timeNs: Long?,
        isFastest: Boolean
    ): String {
        val suffix = if (isFastest && (timeNs ?: -1L) > 0L) " ★" else ""
        return "${metricDisplayLabel(context, key)} · ${formatTime(timeNs ?: -1L)}$suffix"
    }

    private fun hasAnyGlobalPb(session: DragSession): Boolean {
        return isGlobalBestForMetric(session.best0to100, DragSession::best0to100) ||
            isGlobalBestForMetric(session.best100to200, DragSession::best100to200) ||
            isGlobalBestForMetric(session.best0to200, DragSession::best0to200) ||
            isGlobalBestForMetric(session.best0to402, DragSession::best0to402)
    }

    private fun isGlobalPbForMode(session: DragSession, mode: SessionMode): Boolean {
        return when (mode) {
            SessionMode.ZERO_TO_100 -> isGlobalBestForMetric(session.best0to100, DragSession::best0to100)
            SessionMode.ZERO_TO_200 -> isGlobalBestForMetric(session.best0to200, DragSession::best0to200)
            SessionMode.HUNDRED_TO_200 -> isGlobalBestForMetric(session.best100to200, DragSession::best100to200)
            SessionMode.QUARTER_MILE -> isGlobalBestForMetric(session.best0to402, DragSession::best0to402)
            SessionMode.ALL, SessionMode.UNKNOWN -> false
        }
    }

    private fun isGlobalBestForMetric(value: Long, selector: (DragSession) -> Long): Boolean {
        if (value <= 0L) return false
        val globalBest = sessions
            .map(selector)
            .filter { it > 0L }
            .minOrNull()
            ?: return false
        return value == globalBest
    }

    private fun resolveSessionMode(session: DragSession): SessionMode {
        return when (session.measurementMode?.uppercase(Locale.US)) {
            "ALL" -> SessionMode.ALL
            "ZERO_TO_100" -> SessionMode.ZERO_TO_100
            "ZERO_TO_200" -> SessionMode.ZERO_TO_200
            "HUNDRED_TO_200" -> SessionMode.HUNDRED_TO_200
            "QUARTER_MILE" -> SessionMode.QUARTER_MILE
            else -> inferSessionMode(session)
        }
    }

    private fun inferSessionMode(session: DragSession): SessionMode {
        val activeMetrics = listOf(session.best0to100, session.best100to200, session.best0to200, session.best0to402)
            .count { it > 0L }
        if (activeMetrics >= 2) return SessionMode.ALL

        return when {
            session.best0to100 > 0L -> SessionMode.ZERO_TO_100
            session.best100to200 > 0L -> SessionMode.HUNDRED_TO_200
            session.best0to200 > 0L -> SessionMode.ZERO_TO_200
            session.best0to402 > 0L -> SessionMode.QUARTER_MILE
            else -> SessionMode.UNKNOWN
        }
    }

    private fun getModeBestTime(session: DragSession, mode: SessionMode): Long? {
        return when (mode) {
            SessionMode.ALL -> getBestMetricTime(session, SessionMode.QUARTER_MILE)
            SessionMode.ZERO_TO_100 -> getBestMetricTime(session, SessionMode.ZERO_TO_100)
            SessionMode.ZERO_TO_200 -> getBestMetricTime(session, SessionMode.ZERO_TO_200)
            SessionMode.HUNDRED_TO_200 -> getBestMetricTime(session, SessionMode.HUNDRED_TO_200)
            SessionMode.QUARTER_MILE -> getBestMetricTime(session, SessionMode.QUARTER_MILE)
            SessionMode.UNKNOWN -> {
                listOf(
                    getBestMetricTime(session, SessionMode.ZERO_TO_100) ?: -1L,
                    getBestMetricTime(session, SessionMode.HUNDRED_TO_200) ?: -1L,
                    getBestMetricTime(session, SessionMode.ZERO_TO_200) ?: -1L,
                    getBestMetricTime(session, SessionMode.QUARTER_MILE) ?: -1L
                )
                    .filter { it > 0L }
                    .minOrNull()
            }
        }
    }

    private fun getBestMetricTime(session: DragSession, mode: SessionMode): Long? {
        val fromSessionBest = when (mode) {
            SessionMode.ZERO_TO_100 -> session.best0to100
            SessionMode.ZERO_TO_200 -> session.best0to200
            SessionMode.HUNDRED_TO_200 -> session.best100to200
            SessionMode.QUARTER_MILE, SessionMode.ALL -> session.best0to402
            SessionMode.UNKNOWN -> -1L
        }.takeIf { it > 0L }

        if (fromSessionBest != null) return fromSessionBest

        return when (mode) {
            SessionMode.ZERO_TO_100 -> session.attempts.map { it.time0to100 }.filter { it > 0L }.minOrNull()
            SessionMode.ZERO_TO_200 -> session.attempts.map { it.time0to200 }.filter { it > 0L }.minOrNull()
            SessionMode.HUNDRED_TO_200 -> {
                session.attempts.mapNotNull { DragAttemptMetrics.resolve100To200SplitTimeNs(it) }.minOrNull()
            }
            SessionMode.QUARTER_MILE, SessionMode.ALL -> session.attempts.map { it.time0to402 }.filter { it > 0L }.minOrNull()
            SessionMode.UNKNOWN -> null
        }
    }

    private fun getModeLabel(context: android.content.Context, mode: SessionMode): String {
        return when (mode) {
            SessionMode.ALL -> context.getString(R.string.drag_mode_all)
            SessionMode.ZERO_TO_100 -> context.getString(R.string.drag_mode_0to100)
            SessionMode.ZERO_TO_200 -> context.getString(R.string.drag_mode_0to200)
            SessionMode.HUNDRED_TO_200 -> context.getString(R.string.drag_mode_100to200)
            SessionMode.QUARTER_MILE -> context.getString(R.string.drag_mode_quarter)
            SessionMode.UNKNOWN -> context.getString(R.string.drag_mode_unknown)
        }
    }

    private fun bindSessionTrap(holder: DragSessionViewHolder, session: DragSession) {
        val trapSpeed = resolveSessionTrapSpeedKmh(session)
        holder.llAllTrapCol.visibility = if (trapSpeed > 0f) View.VISIBLE else View.GONE
        holder.tvAllTrap.text = if (trapSpeed > 0f) UnitsManager.formatSpeed(trapSpeed, holder.itemView.context, 0) else "--"
    }

    private fun resolveSessionTrapSpeedKmh(session: DragSession): Float {
        return session.attempts.mapNotNull { DragAttemptMetrics.resolveTrapSpeedKmh(it) }.maxOrNull() ?: 0f
    }

    private fun resolveSessionPeakGValue(session: DragSession): Float? {
        val peaks = session.attempts.mapNotNull { resolveAttemptPreferredPeakGValue(it) }
        return peaks.maxOrNull()?.takeIf { it > 0f }
    }

    private fun resolveAttemptPreferredPeakGValue(attempt: DragAttempt): Float? {
        if (attempt.peakLongitudinalG > 0f) return attempt.peakLongitudinalG

        val liveLimit = minOf(attempt.liveAccelDisplaySamples.size, attempt.liveAccelDisplayTimeStamps.size)
        if (liveLimit > 1) {
            val livePeak = attempt.liveAccelDisplaySamples
                .take(liveLimit)
                .map { if (it.isFinite()) it.coerceAtLeast(0f) else 0f }
                .maxOrNull()
            if (livePeak != null && livePeak > 0f) return livePeak
        }

        val imuVals = attempt.longitudinalAccelSamples
        if (imuVals.size > 1) {
            val mps2ToG = 1f / 9.81f
            val clampG = 3.5f
            val emaAlpha = 0.22f
            val accelG = imuVals.map { (it * mps2ToG).coerceIn(-clampG, clampG) }

            var previous = accelG.first()
            val smoothed = mutableListOf(previous)
            for (i in 1 until accelG.size) {
                previous += emaAlpha * (accelG[i] - previous)
                smoothed.add(previous)
            }

            val imuPeak = smoothed.filter { it > 0f }.maxOrNull()
            if (imuPeak != null && imuPeak > 0f) return imuPeak
        }

        return attempt.gSamples.maxOrNull()?.takeIf { it > 0f }
    }

    private fun formatSessionDay(timestamp: Long, context: android.content.Context): String {
        return SimpleDateFormat("dd", LanguageManager.getCurrentLocale(context))
            .format(Date(timestamp))
    }

    private fun formatSessionMonth(timestamp: Long, context: android.content.Context): String {
        val locale = LanguageManager.getCurrentLocale(context)
        return SimpleDateFormat("MMM", locale).format(Date(timestamp)).uppercase(locale)
    }

    private fun formatTime(nanos: Long): String {
        return if (nanos > 0L) {
            val seconds = nanos / 1_000_000_000.0
            String.format(Locale.US, "%.2fs", seconds)
        } else {
            "--"
        }
    }

    private fun formatTimeWithoutUnit(nanos: Long?): String {
        return if (nanos != null && nanos > 0L) {
            val seconds = nanos / 1_000_000_000.0
            String.format(Locale.US, "%.2f", seconds)
        } else {
            "--"
        }
    }
}