package com.example.clinometer

import android.text.format.DateFormat
import android.widget.LinearLayout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.settings.UnitsManager
import java.util.*

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

    inner class DragSessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val llAllModeContent: LinearLayout = itemView.findViewById(R.id.llAllModeContent)
        val llSingleModeContent: LinearLayout = itemView.findViewById(R.id.llSingleModeContent)

        val tvName: TextView = itemView.findViewById(R.id.tvSessionName)
        val tvDate: TextView = itemView.findViewById(R.id.tvSessionDate)
        val tvSingleModeBadge: TextView = itemView.findViewById(R.id.tvSingleModeBadge)
        val tvAttempts: TextView = itemView.findViewById(R.id.tvAttemptCount)
        val tvBestTime: TextView = itemView.findViewById(R.id.tvBestTime)
        val tvEnvironment: TextView = itemView.findViewById(R.id.tvEnvironment)

        val tvAllDay: TextView = itemView.findViewById(R.id.tvAllDay)
        val tvAllMonth: TextView = itemView.findViewById(R.id.tvAllMonth)
        val tvAllSessionTitle: TextView = itemView.findViewById(R.id.tvAllSessionTitle)
        val tvAllPbBadge: TextView = itemView.findViewById(R.id.tvAllPbBadge)
        val tvAllModeBadge: TextView = itemView.findViewById(R.id.tvAllModeBadge)
        val tvAllPrimaryLabel: TextView = itemView.findViewById(R.id.tvAllPrimaryLabel)
        val tvAllPrimaryTime: TextView = itemView.findViewById(R.id.tvAllPrimaryTime)
        val tvAllMetric0to100: TextView = itemView.findViewById(R.id.tvAllMetric0to100)
        val tvAllMetric100to200: TextView = itemView.findViewById(R.id.tvAllMetric100to200)
        val tvAllMetric0to200: TextView = itemView.findViewById(R.id.tvAllMetric0to200)
        val tvAllMetric0to402: TextView = itemView.findViewById(R.id.tvAllMetric0to402)
        val tvAllRuns: TextView = itemView.findViewById(R.id.tvAllRuns)
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
        val context = holder.itemView.context
        val mode = resolveSessionMode(session)

        val attemptCount = session.attempts.size
        holder.tvAttempts.text = context.resources.getQuantityString(
            R.plurals.drag_attempts_count,
            attemptCount,
            attemptCount
        )

        val envText = buildString {
            session.temperature?.let { 
                val tempUnit = UnitsManager.getTemperatureUnit(context)
                val convertedTemp = UnitsManager.convertTemperature(it, tempUnit)
                append("${String.format("%.1f", convertedTemp)}${tempUnit.symbol}")
            }
            if (session.temperature != null && session.altitude != null) append(" • ")
            session.altitude?.let { append("${it.toInt()}m") }
        }

        if (mode == SessionMode.ALL) {
            bindAllMode(holder, session, position)
        } else {
            bindSingleMode(holder, session, mode, position)
        }

        if (envText.isNotEmpty()) {
            holder.tvEnvironment.text = envText
            holder.tvEnvironment.visibility = View.VISIBLE
        } else {
            holder.tvEnvironment.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onItemClick(session) }

        holder.btnOptions.setOnClickListener {
            onDeleteClick(session)
        }
    }

    override fun getItemCount(): Int = sessions.size

    private fun formatDate(timestamp: Long): String {
        return DateFormat.format("dd.MM.yyyy HH:mm", Date(timestamp)).toString()
    }

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
        holder.llSingleModeContent.visibility = View.GONE

        holder.tvAllDay.text = DateFormat.format("dd", Date(session.timestamp)).toString()
        holder.tvAllMonth.text = DateFormat.format("MMM", Date(session.timestamp)).toString().uppercase(Locale.getDefault())
        holder.tvAllSessionTitle.text = buildAllModeTitle(session, position)
        holder.tvAllModeBadge.text = modeLabel
        holder.tvAllPbBadge.visibility = if (isGlobalPbForMode(session, mode)) View.VISIBLE else View.GONE

        holder.tvAllPrimaryLabel.text = "BEST $modeLabel"
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

        holder.tvAllMetric0to100.text = formatMetricChipNullable("0-100", metric0to100, false)
        holder.tvAllMetric100to200.text = formatMetricChipNullable("100-200", metric100to200, false)
        holder.tvAllMetric0to200.text = formatMetricChipNullable("0-200", metric0to200, false)
        holder.tvAllMetric0to402.text = formatMetricChipNullable("0-402", metric0to402, false)

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
        val trapSpeed = session.attempts.maxOfOrNull { it.maxSpeed } ?: 0f
        holder.tvAllTrap.text = if (trapSpeed > 0f) UnitsManager.formatSpeed(trapSpeed, context, 0) else "--"
        val peakG = session.attempts.flatMap { it.gSamples }.maxOrNull()?.takeIf { it > 0f }
        holder.tvAllPeakG.text = peakG?.let { String.format(Locale.US, "%.2fg", it) } ?: "--"
    }

    private fun bindAllMode(holder: DragSessionViewHolder, session: DragSession, position: Int) {
        val context = holder.itemView.context
        val color0to100 = ContextCompat.getColor(context, R.color.accent_green)
        val color0to200 = ContextCompat.getColor(context, R.color.accent_blue)
        val color100to200 = ContextCompat.getColor(context, R.color.accent_purple)
        val color0to402 = ContextCompat.getColor(context, R.color.accent_red)

        holder.llSingleModeContent.visibility = View.GONE
        holder.llAllModeContent.visibility = View.VISIBLE

        holder.tvAllDay.text = DateFormat.format("dd", Date(session.timestamp)).toString()
        holder.tvAllMonth.text = DateFormat.format("MMM", Date(session.timestamp)).toString().uppercase(Locale.getDefault())

        holder.tvAllSessionTitle.text = buildAllModeTitle(session, position)
        holder.tvAllPbBadge.visibility = if (hasAnyGlobalPb(session)) View.VISIBLE else View.GONE

        val metrics = listOf(
            "0-100" to session.best0to100.takeIf { it > 0L },
            "100-200" to session.best100to200.takeIf { it > 0L },
            "0-200" to session.best0to200.takeIf { it > 0L },
            "0-402" to session.best0to402.takeIf { it > 0L }
        )

        val fastestMetric = metrics
            .filter { it.second != null }
            .minByOrNull { it.second ?: Long.MAX_VALUE }
            ?.first

        val primaryMetric = if (session.best0to402 > 0L) "0-402" else fastestMetric
        val primaryTime = when (primaryMetric) {
            "0-100" -> session.best0to100.takeIf { it > 0L }
            "100-200" -> session.best100to200.takeIf { it > 0L }
            "0-200" -> session.best0to200.takeIf { it > 0L }
            "0-402" -> session.best0to402.takeIf { it > 0L }
            else -> null
        }

        holder.tvAllPrimaryLabel.text = if (primaryMetric != null) "BEST $primaryMetric" else "BEST"
        holder.tvAllPrimaryTime.text = formatTimeWithoutUnit(primaryTime)
        holder.tvAllPrimaryTime.setTextColor(
            when (primaryMetric) {
                "0-100" -> color0to100
                "100-200" -> color100to200
                "0-200" -> color0to200
                "0-402" -> color0to402
                else -> ContextCompat.getColor(context, R.color.drag_run_purple)
            }
        )

        holder.tvAllMetric0to100.text = formatMetricChip("0-100", session.best0to100, fastestMetric == "0-100")
        holder.tvAllMetric100to200.text = formatMetricChip("100-200", session.best100to200, fastestMetric == "100-200")
        holder.tvAllMetric0to200.text = formatMetricChip("0-200", session.best0to200, fastestMetric == "0-200")
        holder.tvAllMetric0to402.text = formatMetricChip("0-402", session.best0to402, fastestMetric == "0-402")
        holder.tvAllMetric0to100.setTextColor(color0to100)
        holder.tvAllMetric100to200.setTextColor(color100to200)
        holder.tvAllMetric0to200.setTextColor(color0to200)
        holder.tvAllMetric0to402.setTextColor(color0to402)

        holder.tvAllRuns.text = session.attempts.size.toString()

        val trapSpeed = session.attempts.maxOfOrNull { it.maxSpeed } ?: 0f
        holder.tvAllTrap.text = if (trapSpeed > 0f) UnitsManager.formatSpeed(trapSpeed, context, 0) else "--"

        val peakG = session.attempts
            .flatMap { it.gSamples }
            .maxOrNull()
            ?.takeIf { it > 0f }
        holder.tvAllPeakG.text = peakG?.let { String.format(Locale.US, "%.2fg", it) } ?: "--"
    }

    private fun buildAllModeTitle(session: DragSession, position: Int): String {
        val name = session.name?.trim().orEmpty()
        val numberFromName = Regex("(\\d+)").find(name)?.groupValues?.getOrNull(1)
        return if (!numberFromName.isNullOrBlank()) {
            "Session #$numberFromName"
        } else {
            "Session #${position + 1}"
        }
    }

    private fun formatMetricChip(label: String, timeNs: Long, isFastest: Boolean): String {
        val suffix = if (isFastest && timeNs > 0L) " ★" else ""
        return "$label · ${formatTime(timeNs)}$suffix"
    }

    private fun formatMetricChipNullable(label: String, timeNs: Long?, isFastest: Boolean): String {
        val suffix = if (isFastest && (timeNs ?: -1L) > 0L) " ★" else ""
        return "$label · ${formatTime(timeNs ?: -1L)}$suffix"
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
                val explicit = session.attempts.map { it.time100to200 }.filter { it > 0L }
                val derived = session.attempts.mapNotNull { attempt ->
                    if (attempt.time0to200 > 0L && attempt.time0to100 > 0L && attempt.time0to200 > attempt.time0to100) {
                        attempt.time0to200 - attempt.time0to100
                    } else {
                        null
                    }
                }
                (explicit + derived).minOrNull()
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

    private fun getModeColor(mode: SessionMode): Int {
        return when (mode) {
            SessionMode.ZERO_TO_100 -> R.color.accent_green
            SessionMode.ZERO_TO_200 -> R.color.accent_blue
            SessionMode.HUNDRED_TO_200 -> R.color.accent_purple
            SessionMode.QUARTER_MILE -> R.color.accent_red
            SessionMode.ALL -> R.color.drag_run_purple
            SessionMode.UNKNOWN -> R.color.text_secondary
        }
    }

    private fun formatTime(nanos: Long): String {
        return if (nanos > 0L) {
            val seconds = nanos / 1_000_000_000.0
            String.format(Locale.US, "%.3fs", seconds)
        } else {
            "--"
        }
    }

    private fun formatTimeWithoutUnit(nanos: Long?): String {
        return if (nanos != null && nanos > 0L) {
            val seconds = nanos / 1_000_000_000.0
            String.format(Locale.US, "%.3f", seconds)
        } else {
            "--"
        }
    }
}