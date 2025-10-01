package com.example.clinometer

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.settings.UnitsManager
import java.util.*

class DragSessionAdapter(
    private val sessions: MutableList<DragSession>,
    private val onItemClick: (DragSession) -> Unit,
    private val onDeleteClick: (DragSession) -> Unit
) : RecyclerView.Adapter<DragSessionAdapter.DragSessionViewHolder>() {

    inner class DragSessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvSessionName)
        val tvDate: TextView = itemView.findViewById(R.id.tvSessionDate)
        val tvAttempts: TextView = itemView.findViewById(R.id.tvAttemptCount)
        val tvBestTime: TextView = itemView.findViewById(R.id.tvBestTime)
        val tvEnvironment: TextView = itemView.findViewById(R.id.tvEnvironment)
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

        holder.tvName.text = session.name ?: "Session ${position + 1}"
        holder.tvDate.text = formatDate(session.timestamp)

        // Показваме броя опити
        val attemptCount = session.attempts.size
        holder.tvAttempts.text = context.resources.getQuantityString(
            R.plurals.drag_attempts_count,
            attemptCount,
            attemptCount
        )

        // Показваме най-доброто време
        val bestTimeText = getBestTimeText(session)
        holder.tvBestTime.text = bestTimeText

        // Показваме температура и височина ако има
        val envText = buildString {
            session.temperature?.let { 
                val tempUnit = UnitsManager.getTemperatureUnit(context)
                val convertedTemp = UnitsManager.convertTemperature(it, tempUnit)
                append("${String.format("%.1f", convertedTemp)}${tempUnit.symbol}")
            }
            if (session.temperature != null && session.altitude != null) append(" • ")
            session.altitude?.let { append("${it.toInt()}m") }
        }

        if (envText.isNotEmpty()) {
            holder.tvEnvironment.text = envText
            holder.tvEnvironment.visibility = View.VISIBLE
        } else {
            holder.tvEnvironment.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onItemClick(session) }

        holder.btnOptions.setOnClickListener { view ->
            PopupMenu(view.context, view).apply {
                menu.add(0, 1, 0, context.getString(R.string.profile_delete_button))
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> {
                            onDeleteClick(session)
                            true
                        }
                        else -> false
                    }
                }
            }.show()
        }
    }

    override fun getItemCount(): Int = sessions.size

    private fun formatDate(timestamp: Long): String {
        val context = sessions.firstOrNull()?.let {
            return DateFormat.format("dd.MM.yyyy HH:mm", Date(timestamp)).toString()
        }
        return ""
    }

    private fun getBestTimeText(session: DragSession): String {
        val times = mutableListOf<Pair<String, Long>>()

        session.best0to100.takeIf { it > 0 }?.let {
            times.add("0-100" to it)
        }
        session.best0to200.takeIf { it > 0 }?.let {
            times.add("0-200" to it)
        }
        session.best0to402.takeIf { it > 0 }?.let {
            times.add("0-402" to it)
        }

        return if (times.isNotEmpty()) {
            val best = times.minByOrNull { it.second }!!
            "${best.first}: ${formatTime(best.second)}"
        } else {
            "No times recorded"
        }
    }

    private fun formatTime(nanos: Long): String {
        return if (nanos > 0) {
            val seconds = nanos / 1_000_000_000.0
            String.format("%.3fs", seconds)
        } else {
            "--"
        }
    }
}