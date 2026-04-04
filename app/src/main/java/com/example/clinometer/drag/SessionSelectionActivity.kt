package com.example.clinometer.drag

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.Profile
import com.example.clinometer.R
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.settings.LanguageManager
import com.example.clinometer.DragSession
import com.example.clinometer.DragAttempt
import com.example.clinometer.DragStorage
import java.text.SimpleDateFormat
import java.util.*

class SessionSelectionActivity : AppCompatActivity() {

    private data class AttemptItem(
        val attempt: DragAttempt,
        val originalNumber: Int
    )
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }
    
    private lateinit var rvSessions: RecyclerView
    private lateinit var sessionsAdapter: SessionsAdapter
    private var currentSessionId: Long = -1
    private var currentAttemptId: Long = -1
    private var profilesById: Map<Long, Profile> = emptyMap()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_selection)
        
        currentSessionId = intent.getLongExtra("current_session_id", -1)
        currentAttemptId = intent.getLongExtra("current_attempt_id", -1)
        
        setupViews()
        loadSessions()
    }
    
    private fun setupViews() {
        rvSessions = findViewById(R.id.rvSessions)
        rvSessions.layoutManager = LinearLayoutManager(this)
        
        sessionsAdapter = SessionsAdapter { session, attempt ->
            // Отваряме страницата за сравняване
            val intent = Intent(this, CompareAttemptsActivity::class.java)
            intent.putExtra("current_session_id", currentSessionId)
            intent.putExtra("current_attempt_id", currentAttemptId)
            intent.putExtra("compare_session_id", session.id)
            intent.putExtra("compare_attempt_id", attempt.id)
            startActivity(intent)
        }
        
        rvSessions.adapter = sessionsAdapter
        
        // Настройваме back бутона
        findViewById<View>(R.id.btnBack)?.setOnClickListener {
            finish()
        }
    }
    
    private fun loadSessions() {
        profilesById = ProfileStorage.loadProfiles(this).associateBy { it.id }
        val sessions = DragStorage.getAllDragSessions(this).sortedByDescending { it.timestamp }
        sessionsAdapter.updateSessions(sessions)
    }
    
    private inner class SessionsAdapter(
        private val onAttemptSelected: (DragSession, DragAttempt) -> Unit
    ) : RecyclerView.Adapter<SessionsAdapter.SessionViewHolder>() {
        
        private var sessions: List<DragSession> = emptyList()
        private val expandedSessions = mutableSetOf<Long>()
        
        fun updateSessions(newSessions: List<DragSession>) {
            sessions = newSessions.filter { session ->
                if (session.id != currentSessionId) {
                    true
                } else {
                    (session.attempts?.size ?: 0) > 1
                }
            }
            expandedSessions.clear()
            sessions.firstOrNull()?.let { expandedSessions.add(it.id) }
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_track_compare_session, parent, false)
            return SessionViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
            val session = sessions[position]
            holder.bind(session, expandedSessions.contains(session.id)) { attempt ->
                onAttemptSelected(session, attempt)
            }
        }
        
        override fun getItemCount(): Int = sessions.size
        
        inner class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvSessionTitle: TextView = itemView.findViewById(R.id.tvSessionTitle)
            private val tvSessionMeta: TextView = itemView.findViewById(R.id.tvSessionMeta)
            private val tvVehicleBadge: TextView = itemView.findViewById(R.id.tvVehicleBadge)
            private val tvAttemptCount: TextView = itemView.findViewById(R.id.tvLapCount)
            private val rvAttempts: RecyclerView = itemView.findViewById(R.id.rvLaps)
            private val llAttemptsContainer: View = itemView.findViewById(R.id.llLapsContainer)
            
            fun bind(session: DragSession, isExpanded: Boolean, onAttemptSelected: (DragAttempt) -> Unit) {
                val profile = profilesById[session.profileId]
                val profileName = profile?.name
                    ?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.track_compare_unknown_profile)
                tvSessionTitle.text = profileName
                
                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                val sessionDate = dateFormat.format(Date(session.timestamp))
                val sessionName = session.name?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.compare_session)
                val sessionModeLabel = getSessionMeasurementModeLabel(session)
                tvSessionMeta.text = getString(
                    R.string.drag_compare_session_meta,
                    sessionName,
                    sessionDate,
                    sessionModeLabel
                )
                
                // Зареждаме опитите за тази сесия
                val attempts = (session.attempts ?: emptyList())
                    .mapIndexed { index, attempt -> AttemptItem(attempt, index + 1) }
                    .filterNot {
                        session.id == currentSessionId && it.attempt.id == currentAttemptId
                    }

                tvAttemptCount.text = resources.getQuantityString(
                    R.plurals.drag_compare_attempts_count,
                    attempts.size,
                    attempts.size
                )

                when (profile?.vehicleType) {
                    Profile.VehicleType.MOTORCYCLE -> {
                        tvVehicleBadge.text = getString(R.string.track_compare_vehicle_moto)
                        tvVehicleBadge.background = ContextCompat.getDrawable(itemView.context, R.drawable.bg_vehicle_chip_moto)
                    }
                    Profile.VehicleType.CAR -> {
                        tvVehicleBadge.text = getString(R.string.track_compare_vehicle_car)
                        tvVehicleBadge.background = ContextCompat.getDrawable(itemView.context, R.drawable.bg_vehicle_chip_car)
                    }
                    null -> {
                        tvVehicleBadge.text = getString(R.string.track_compare_vehicle_unknown)
                        tvVehicleBadge.background = ContextCompat.getDrawable(itemView.context, R.drawable.bg_vehicle_chip_unknown)
                    }
                }
                
                val attemptsAdapter = AttemptsAdapter(attempts) { attempt ->
                    onAttemptSelected(attempt)
                }
                rvAttempts.layoutManager = LinearLayoutManager(itemView.context)
                rvAttempts.adapter = attemptsAdapter
                
                // Показваме/скриваме опитите
                llAttemptsContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
                
                // Клик за разгъване/сгъване
                itemView.setOnClickListener {
                    if (isExpanded) {
                        expandedSessions.remove(session.id)
                    } else {
                        expandedSessions.add(session.id)
                    }
                    notifyItemChanged(bindingAdapterPosition)
                }
            }
        }
    }
    
    private inner class AttemptsAdapter(
        private val attempts: List<AttemptItem>,
        private val onAttemptSelected: (DragAttempt) -> Unit
    ) : RecyclerView.Adapter<AttemptsAdapter.AttemptViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttemptViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_track_compare_lap, parent, false)
            return AttemptViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: AttemptViewHolder, position: Int) {
            val item = attempts[position]
            holder.bind(item) {
                onAttemptSelected(item.attempt)
            }
        }
        
        override fun getItemCount(): Int = attempts.size
        
        inner class AttemptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvAttemptNumber: TextView = itemView.findViewById(R.id.tvLapTitle)
            private val tvAttemptTime: TextView = itemView.findViewById(R.id.tvLapTime)
            
            fun bind(item: AttemptItem, onAttemptSelected: () -> Unit) {
                tvAttemptNumber.text = getString(R.string.drag_compare_attempt_title, item.originalNumber)
                tvAttemptTime.text = formatAttemptSummary(item.attempt)
                
                itemView.setOnClickListener {
                    onAttemptSelected()
                }
            }
        }
    }

    private fun formatAttemptSummary(attempt: DragAttempt): String {
        return when {
            attempt.time0to100 > 0 -> "0-100 ${formatDurationToSeconds(attempt.time0to100)}"
            attempt.time100to200 > 0 -> "100-200 ${formatDurationToSeconds(attempt.time100to200)}"
            attempt.time0to200 > 0 -> "0-200 ${formatDurationToSeconds(attempt.time0to200)}"
            attempt.time0to402 > 0 -> "1/4 ${formatDurationToSeconds(attempt.time0to402)}"
            else -> getString(R.string.drag_compare_attempt_no_data)
        }
    }

    private fun getSessionMeasurementModeLabel(session: DragSession): String {
        return when (session.measurementMode) {
            MeasurementMode.ALL.name -> getString(R.string.drag_mode_all)
            MeasurementMode.ZERO_TO_100.name -> getString(R.string.drag_mode_0to100)
            MeasurementMode.ZERO_TO_200.name -> getString(R.string.drag_mode_0to200)
            MeasurementMode.HUNDRED_TO_200.name -> getString(R.string.drag_mode_100to200)
            MeasurementMode.QUARTER_MILE.name -> getString(R.string.drag_mode_quarter)
            else -> inferModeFromSessionData(session)
        }
    }

    private fun inferModeFromSessionData(session: DragSession): String {
        val has0to100 = session.best0to100 > 0 || session.attempts.any { it.time0to100 > 0 }
        val has0to200 = session.best0to200 > 0 || session.attempts.any { it.time0to200 > 0 }
        val has100to200 = session.best100to200 > 0 || session.attempts.any { it.time100to200 > 0 }
        val has0to402 = session.best0to402 > 0 || session.attempts.any { it.time0to402 > 0 }

        val measuredCount = listOf(has0to100, has0to200, has100to200, has0to402).count { it }

        return when {
            measuredCount >= 2 -> getString(R.string.drag_mode_all)
            has0to100 -> getString(R.string.drag_mode_0to100)
            has0to200 -> getString(R.string.drag_mode_0to200)
            has100to200 -> getString(R.string.drag_mode_100to200)
            has0to402 -> getString(R.string.drag_mode_quarter)
            else -> getString(R.string.drag_mode_unknown)
        }
    }

    private fun formatDurationToSeconds(rawValue: Long): String {
        // Drag attempts are stored in nanoseconds in current builds.
        // Keep a legacy fallback for older sessions that were saved in milliseconds.
        val seconds = if (rawValue >= 1_000_000L) {
            rawValue / 1_000_000_000.0
        } else {
            rawValue / 1000.0
        }
        return String.format(Locale.getDefault(), "%.3fs", seconds)
    }
}
