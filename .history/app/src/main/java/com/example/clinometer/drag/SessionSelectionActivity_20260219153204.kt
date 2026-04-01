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
import com.example.clinometer.R
import com.example.clinometer.settings.LanguageManager
import com.example.clinometer.DragSession
import com.example.clinometer.DragAttempt
import com.example.clinometer.DragStorage
import java.text.SimpleDateFormat
import java.util.*
import android.util.TypedValue
import android.widget.LinearLayout

// Extension function за конвертиране на dp в px
private fun Int.dpToPx(context: Context): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        context.resources.displayMetrics
    ).toInt()
}

class SessionSelectionActivity : AppCompatActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }
    
    private lateinit var rvSessions: RecyclerView
    private lateinit var sessionsAdapter: SessionsAdapter
    private var currentSessionId: Long = -1
    private var currentAttemptId: Long = -1
    private var selectedAttempt: DragAttempt? = null
    
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
            selectedAttempt = attempt
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
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_session_with_attempts, parent, false)
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
            private val tvSessionName: TextView = itemView.findViewById(R.id.tvSessionName)
            private val tvSessionDate: TextView = itemView.findViewById(R.id.tvSessionDate)
            private val rvAttempts: RecyclerView = itemView.findViewById(R.id.rvAttempts)
            private val llAttemptsContainer: View = itemView.findViewById(R.id.llAttemptsContainer)
            
            // Добавяме TextView за статистики
            private val tvSessionStats: TextView = TextView(itemView.context).apply {
                textSize = 12f
                setTextColor(ContextCompat.getColor(itemView.context, R.color.accent_green))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 8.dpToPx(itemView.context)
                }
            }
            
            fun bind(session: DragSession, isExpanded: Boolean, onAttemptSelected: (DragAttempt) -> Unit) {
                tvSessionName.text = session.name ?: "Drag Session"
                
                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                tvSessionDate.text = dateFormat.format(Date(session.timestamp))
                
                // Зареждаме опитите за тази сесия
                val attempts = (session.attempts ?: emptyList()).filterNot {
                    session.id == currentSessionId && it.id == currentAttemptId
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
                    notifyItemChanged(position)
                }
            }
        }
    }
    
    private inner class AttemptsAdapter(
        private val attempts: List<DragAttempt>,
        private val onAttemptSelected: (DragAttempt) -> Unit
    ) : RecyclerView.Adapter<AttemptsAdapter.AttemptViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttemptViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_attempt_simple, parent, false)
            return AttemptViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: AttemptViewHolder, position: Int) {
            val attempt = attempts[position]
            holder.bind(attempt) {
                onAttemptSelected(attempt)
            }
        }
        
        override fun getItemCount(): Int = attempts.size
        
        inner class AttemptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvAttemptNumber: TextView = itemView.findViewById(R.id.tvAttemptNumber)
            private val tvAttemptTime: TextView = itemView.findViewById(R.id.tvAttemptTime)
            private val tvAttemptStats: TextView = itemView.findViewById(R.id.tvAttemptStats)
            
            fun bind(attempt: DragAttempt, onAttemptSelected: () -> Unit) {
                tvAttemptNumber.text = "Attempt ${adapterPosition + 1}"
                
                // Показваме датата и часа
                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                tvAttemptTime.text = dateFormat.format(Date(attempt.timestamp))
                
                // НЕ показваме статистики
                tvAttemptStats.text = ""
                
                itemView.setOnClickListener {
                    onAttemptSelected()
                }
            }
        }
    }
}
