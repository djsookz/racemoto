package com.example.clinometer.drag

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.DragAttempt
import com.example.clinometer.DragSession
import com.example.clinometer.DragStorage
import com.example.clinometer.Profile
import com.example.clinometer.R
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.settings.LanguageManager
import com.example.clinometer.settings.UnitsManager
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionSelectionActivity : AppCompatActivity() {

    private data class AttemptItem(
        val attempt: DragAttempt,
        val originalNumber: Int
    )

    private data class SessionItem(
        val session: DragSession,
        val availableAttempts: List<AttemptItem>
    )

    private enum class SessionMode {
        ALL,
        ZERO_TO_100,
        ZERO_TO_200,
        HUNDRED_TO_200,
        QUARTER_MILE,
        UNKNOWN
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private lateinit var tabProfiles: TabLayout
    private lateinit var rvSessions: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var sessionsAdapter: SessionsAdapter

    private var currentSessionId: Long = -1L
    private var currentAttemptId: Long = -1L
    private var selectedProfileId: Long = -1L

    private var profilesById: Map<Long, Profile> = emptyMap()
    private var allProfiles: List<Profile> = emptyList()
    private var allSessions: List<DragSession> = emptyList()
    private var currentSession: DragSession? = null

    private val sessionDateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
    private val attemptClockFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_selection)

        currentSessionId = intent.getLongExtra("current_session_id", -1L)
        currentAttemptId = intent.getLongExtra("current_attempt_id", -1L)

        setupViews()
        loadData()
    }

    private fun setupViews() {
        tabProfiles = findViewById(R.id.tabProfiles)
        rvSessions = findViewById(R.id.rvSessions)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        rvSessions.layoutManager = LinearLayoutManager(this)
        sessionsAdapter = SessionsAdapter { session, attempt ->
            startActivity(Intent(this, CompareAttemptsActivity::class.java).apply {
                putExtra("current_session_id", currentSessionId)
                putExtra("current_attempt_id", currentAttemptId)
                putExtra("compare_session_id", session.id)
                putExtra("compare_attempt_id", attempt.id)
            })
        }
        rvSessions.adapter = sessionsAdapter

        findViewById<View>(R.id.btnBack)?.setOnClickListener {
            finish()
        }

        tabProfiles.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val profile = allProfiles.getOrNull(tab?.position ?: -1) ?: return
                if (selectedProfileId == profile.id && sessionsAdapter.itemCount > 0) {
                    return
                }
                selectedProfileId = profile.id
                applySelectedProfile()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit

            override fun onTabReselected(tab: TabLayout.Tab?) {
                val profile = allProfiles.getOrNull(tab?.position ?: -1) ?: return
                selectedProfileId = profile.id
                applySelectedProfile()
            }
        })
    }

    private fun loadData() {
        allProfiles = ProfileStorage.loadProfiles(this)
        profilesById = allProfiles.associateBy { it.id }
        allSessions = DragStorage.getAllDragSessions(this).sortedByDescending { it.timestamp }
        currentSession = allSessions.firstOrNull { it.id == currentSessionId }

        selectedProfileId = currentSession?.profileId
            ?.takeIf { profilesById.containsKey(it) }
            ?: allProfiles.firstOrNull()?.id
            ?: -1L

        bindProfileTabs()
        applySelectedProfile()
    }

    private fun bindProfileTabs() {
        tabProfiles.removeAllTabs()
        tabProfiles.visibility = if (allProfiles.isEmpty()) View.GONE else View.VISIBLE

        allProfiles.forEachIndexed { index, profile ->
            tabProfiles.addTab(tabProfiles.newTab().setText(getProfileTabTitle(profile, index)))
        }

        if (allProfiles.isEmpty()) {
            return
        }

        val selectedIndex = allProfiles.indexOfFirst { it.id == selectedProfileId }
            .takeIf { it >= 0 }
            ?: 0

        if (tabProfiles.selectedTabPosition != selectedIndex) {
            tabProfiles.getTabAt(selectedIndex)?.select()
        }
    }

    private fun applySelectedProfile() {
        if (selectedProfileId == -1L) {
            sessionsAdapter.updateSessions(emptyList())
            showEmptyState(getString(R.string.drag_compare_no_profiles))
            return
        }

        val sessionItems = allSessions
            .asSequence()
            .filter { it.profileId == selectedProfileId }
            .mapNotNull { session ->
                val attempts = buildAvailableAttempts(session)
                if (attempts.isEmpty()) {
                    null
                } else {
                    SessionItem(session, attempts)
                }
            }
            .toList()

        sessionsAdapter.updateSessions(sessionItems)

        if (sessionItems.isEmpty()) {
            showEmptyState(
                getString(
                    R.string.drag_compare_no_sessions_for_profile,
                    getSelectedProfileName()
                )
            )
        } else {
            hideEmptyState()
        }
    }

    private fun buildAvailableAttempts(session: DragSession): List<AttemptItem> {
        return session.attempts
            .mapIndexed { index, attempt -> AttemptItem(attempt, index + 1) }
            .filterNot { session.id == currentSessionId && it.attempt.id == currentAttemptId }
            .filter { hasRecordedMetric(it.attempt) }
            .asReversed()
    }

    private fun showEmptyState(message: String) {
        tvEmptyState.text = message
        tvEmptyState.visibility = View.VISIBLE
        rvSessions.visibility = View.GONE
    }

    private fun hideEmptyState() {
        tvEmptyState.visibility = View.GONE
        rvSessions.visibility = View.VISIBLE
    }

    private fun getSelectedProfileName(): String {
        val profile = profilesById[selectedProfileId]
        val fallbackIndex = allProfiles.indexOfFirst { it.id == selectedProfileId }
            .takeIf { it >= 0 }
            ?: 0
        return profile?.name?.takeIf { it.isNotBlank() }
            ?: getString(R.string.drag_compare_profile_fallback, fallbackIndex + 1)
    }

    private fun getProfileTabTitle(profile: Profile, index: Int): String {
        return profile.name.takeIf { it.isNotBlank() }
            ?: getString(R.string.drag_compare_profile_fallback, index + 1)
    }

    private inner class SessionsAdapter(
        private val onAttemptSelected: (DragSession, DragAttempt) -> Unit
    ) : RecyclerView.Adapter<SessionsAdapter.SessionViewHolder>() {

        private var sessionItems: List<SessionItem> = emptyList()
        private val expandedSessionIds = mutableSetOf<Long>()

        fun updateSessions(newSessionItems: List<SessionItem>) {
            sessionItems = newSessionItems
            expandedSessionIds.retainAll(sessionItems.map { it.session.id }.toSet())
            if (expandedSessionIds.isEmpty()) {
                sessionItems.firstOrNull()?.let { expandedSessionIds.add(it.session.id) }
            }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_drag_compare_session, parent, false)
            return SessionViewHolder(view)
        }

        override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
            val item = sessionItems[position]
            holder.bind(
                item = item,
                position = position,
                isExpanded = expandedSessionIds.contains(item.session.id),
                onToggleSession = {
                    toggleSession(item.session.id)
                },
                onAttemptSelected = { attempt ->
                    onAttemptSelected(item.session, attempt)
                }
            )
        }

        override fun getItemCount(): Int = sessionItems.size

        private fun toggleSession(sessionId: Long) {
            if (expandedSessionIds.contains(sessionId)) {
                expandedSessionIds.remove(sessionId)
            } else {
                expandedSessionIds.add(sessionId)
            }

            val index = sessionItems.indexOfFirst { it.session.id == sessionId }
            if (index >= 0) {
                notifyItemChanged(index)
            }
        }

        inner class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val summaryContainer: LinearLayout = itemView.findViewById(R.id.llSessionSummary)
            private val tvDay: TextView = itemView.findViewById(R.id.tvSessionDay)
            private val tvMonth: TextView = itemView.findViewById(R.id.tvSessionMonth)
            private val tvSessionTitle: TextView = itemView.findViewById(R.id.tvSessionTitle)
            private val tvSessionMeta: TextView = itemView.findViewById(R.id.tvSessionMeta)
            private val tvSessionModeBadge: TextView = itemView.findViewById(R.id.tvSessionModeBadge)
            private val tvSessionExpand: TextView = itemView.findViewById(R.id.tvSessionExpand)
            private val tvSessionHeroLabel: TextView = itemView.findViewById(R.id.tvSessionHeroLabel)
            private val tvSessionHeroTime: TextView = itemView.findViewById(R.id.tvSessionHeroTime)
            private val tvMetric0to100: TextView = itemView.findViewById(R.id.tvSessionMetric0to100)
            private val tvMetric0to200: TextView = itemView.findViewById(R.id.tvSessionMetric0to200)
            private val tvMetric100to200: TextView = itemView.findViewById(R.id.tvSessionMetric100to200)
            private val tvMetric0to402: TextView = itemView.findViewById(R.id.tvSessionMetric0to402)
            private val tvRunsValue: TextView = itemView.findViewById(R.id.tvSessionRunsValue)
            private val tvTrapValue: TextView = itemView.findViewById(R.id.tvSessionTrapValue)
            private val tvPeakGValue: TextView = itemView.findViewById(R.id.tvSessionPeakGValue)
            private val attemptsContainer: View = itemView.findViewById(R.id.llAttemptsContainer)
            private val rvAttempts: RecyclerView = itemView.findViewById(R.id.rvAttempts)

            init {
                rvAttempts.layoutManager = LinearLayoutManager(itemView.context)
                rvAttempts.isNestedScrollingEnabled = false
                rvAttempts.itemAnimator = null
            }

            fun bind(
                item: SessionItem,
                position: Int,
                isExpanded: Boolean,
                onToggleSession: () -> Unit,
                onAttemptSelected: (DragAttempt) -> Unit
            ) {
                val context = itemView.context
                val session = item.session
                val attempts = item.availableAttempts.map { it.attempt }
                val sessionMode = resolveSessionMode(session)
                val primaryMode = resolveSessionPrimaryMode(sessionMode, attempts)

                tvDay.text = dayFormat.format(Date(session.timestamp))
                tvMonth.text = monthFormat.format(Date(session.timestamp)).uppercase(Locale.getDefault())
                tvSessionTitle.text = buildSessionTitle(session, position)
                tvSessionMeta.text = getString(
                    R.string.drag_compare_session_meta_short,
                    sessionDateFormat.format(Date(session.timestamp)),
                    getModeLabel(context, sessionMode)
                )
                tvSessionModeBadge.text = getModeLabel(context, sessionMode)
                tvSessionExpand.text = if (isExpanded) "^" else "v"

                val primaryTime = getBestMetricTime(attempts, primaryMode)
                tvSessionHeroLabel.text = getString(
                    R.string.drag_compare_best_metric,
                    getModeLabel(context, primaryMode)
                )
                tvSessionHeroTime.text = formatDurationValue(primaryTime)
                tvSessionHeroTime.setTextColor(
                    ContextCompat.getColor(context, getMetricColorRes(primaryMode))
                )

                bindSessionMetricChip(tvMetric0to100, SessionMode.ZERO_TO_100, attempts)
                bindSessionMetricChip(tvMetric0to200, SessionMode.ZERO_TO_200, attempts)
                bindSessionMetricChip(tvMetric100to200, SessionMode.HUNDRED_TO_200, attempts)
                bindSessionMetricChip(tvMetric0to402, SessionMode.QUARTER_MILE, attempts)

                tvRunsValue.text = item.availableAttempts.size.toString()
                tvTrapValue.text = formatTrapSpeed(attempts, context)
                tvPeakGValue.text = formatPeakG(attempts)

                rvAttempts.adapter = AttemptsAdapter(item.availableAttempts, sessionMode) { attempt ->
                    onAttemptSelected(attempt)
                }
                attemptsContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE

                summaryContainer.setOnClickListener {
                    onToggleSession()
                }
            }

            private fun bindSessionMetricChip(
                view: TextView,
                metricMode: SessionMode,
                attempts: List<DragAttempt>
            ) {
                val context = view.context
                val metricTime = getBestMetricTime(attempts, metricMode)
                view.text = formatSessionMetricText(metricMode, metricTime)
                view.setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (metricTime != null) getMetricColorRes(metricMode) else R.color.text_tertiary
                    )
                )
                view.alpha = if (metricTime != null) 1f else 0.58f
            }
        }
    }

    private inner class AttemptsAdapter(
        private val attempts: List<AttemptItem>,
        private val preferredMode: SessionMode,
        private val onAttemptSelected: (DragAttempt) -> Unit
    ) : RecyclerView.Adapter<AttemptsAdapter.AttemptViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttemptViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_drag_compare_attempt, parent, false)
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
            private val tvAttemptTitle: TextView = itemView.findViewById(R.id.tvAttemptTitle)
            private val tvAttemptMeta: TextView = itemView.findViewById(R.id.tvAttemptMeta)
            private val tvAttemptPrimaryLabel: TextView = itemView.findViewById(R.id.tvAttemptPrimaryLabel)
            private val tvAttemptPrimaryValue: TextView = itemView.findViewById(R.id.tvAttemptPrimaryValue)
            private val tvAttemptPrimaryUnit: TextView = itemView.findViewById(R.id.tvAttemptPrimaryUnit)
            private val tvMetric0to100: TextView = itemView.findViewById(R.id.tvAttemptMetric0to100)
            private val tvMetric0to200: TextView = itemView.findViewById(R.id.tvAttemptMetric0to200)
            private val tvMetric100to200: TextView = itemView.findViewById(R.id.tvAttemptMetric100to200)
            private val tvMetric0to402: TextView = itemView.findViewById(R.id.tvAttemptMetric0to402)
            private val tvTrapValue: TextView = itemView.findViewById(R.id.tvAttemptTrapValue)
            private val tvPeakGValue: TextView = itemView.findViewById(R.id.tvAttemptPeakGValue)
            private val tvDurationValue: TextView = itemView.findViewById(R.id.tvAttemptDurationValue)

            fun bind(item: AttemptItem, onAttemptSelected: () -> Unit) {
                val context = itemView.context
                val attempt = item.attempt
                val primaryMode = resolveAttemptPrimaryMode(preferredMode, attempt)
                val primaryTime = getAttemptMetricTime(attempt, primaryMode)

                tvAttemptTitle.text = getString(R.string.drag_compare_attempt_title, item.originalNumber)
                tvAttemptMeta.text = getString(
                    R.string.drag_compare_attempt_recorded_at,
                    formatAttemptClock(attempt.timestamp)
                )
                tvAttemptPrimaryLabel.text = getModeLabel(context, primaryMode)
                tvAttemptPrimaryValue.text = formatDurationValue(primaryTime)
                tvAttemptPrimaryValue.setTextColor(
                    ContextCompat.getColor(context, getMetricColorRes(primaryMode))
                )
                tvAttemptPrimaryUnit.visibility = if (primaryTime != null) View.VISIBLE else View.GONE

                bindAttemptMetricChip(tvMetric0to100, SessionMode.ZERO_TO_100, attempt)
                bindAttemptMetricChip(tvMetric0to200, SessionMode.ZERO_TO_200, attempt)
                bindAttemptMetricChip(tvMetric100to200, SessionMode.HUNDRED_TO_200, attempt)
                bindAttemptMetricChip(tvMetric0to402, SessionMode.QUARTER_MILE, attempt)

                tvTrapValue.text = formatTrapSpeed(attempt.maxSpeed, context)
                tvPeakGValue.text = formatPeakG(attempt)
                tvDurationValue.text = formatDurationToSeconds(attempt.duration)

                itemView.setOnClickListener {
                    onAttemptSelected()
                }
            }

            private fun bindAttemptMetricChip(
                view: TextView,
                metricMode: SessionMode,
                attempt: DragAttempt
            ) {
                val context = view.context
                val metricTime = getAttemptMetricTime(attempt, metricMode)
                view.text = formatAttemptMetricText(metricMode, metricTime)
                view.setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (metricTime != null) getMetricColorRes(metricMode) else R.color.text_tertiary
                    )
                )
                view.alpha = if (metricTime != null) 1f else 0.58f
            }
        }
    }

    private fun hasRecordedMetric(attempt: DragAttempt): Boolean {
        return getAttemptMetricTime(attempt, SessionMode.ZERO_TO_100) != null ||
            getAttemptMetricTime(attempt, SessionMode.ZERO_TO_200) != null ||
            getAttemptMetricTime(attempt, SessionMode.HUNDRED_TO_200) != null ||
            getAttemptMetricTime(attempt, SessionMode.QUARTER_MILE) != null
    }

    private fun resolveSessionMode(session: DragSession): SessionMode {
        return when (session.measurementMode?.uppercase(Locale.US)) {
            MeasurementMode.ALL.name -> SessionMode.ALL
            MeasurementMode.ZERO_TO_100.name -> SessionMode.ZERO_TO_100
            MeasurementMode.ZERO_TO_200.name -> SessionMode.ZERO_TO_200
            MeasurementMode.HUNDRED_TO_200.name -> SessionMode.HUNDRED_TO_200
            MeasurementMode.QUARTER_MILE.name -> SessionMode.QUARTER_MILE
            else -> inferModeFromAttempts(session.attempts)
        }
    }

    private fun inferModeFromAttempts(attempts: List<DragAttempt>): SessionMode {
        val has0to100 = attempts.any { getAttemptMetricTime(it, SessionMode.ZERO_TO_100) != null }
        val has0to200 = attempts.any { getAttemptMetricTime(it, SessionMode.ZERO_TO_200) != null }
        val has100to200 = attempts.any { getAttemptMetricTime(it, SessionMode.HUNDRED_TO_200) != null }
        val has0to402 = attempts.any { getAttemptMetricTime(it, SessionMode.QUARTER_MILE) != null }
        val measuredCount = listOf(has0to100, has0to200, has100to200, has0to402).count { it }

        return when {
            measuredCount >= 2 -> SessionMode.ALL
            has0to100 -> SessionMode.ZERO_TO_100
            has0to200 -> SessionMode.ZERO_TO_200
            has100to200 -> SessionMode.HUNDRED_TO_200
            has0to402 -> SessionMode.QUARTER_MILE
            else -> SessionMode.UNKNOWN
        }
    }

    private fun resolveSessionPrimaryMode(
        preferredMode: SessionMode,
        attempts: List<DragAttempt>
    ): SessionMode {
        if (preferredMode != SessionMode.ALL && preferredMode != SessionMode.UNKNOWN) {
            if (getBestMetricTime(attempts, preferredMode) != null) {
                return preferredMode
            }
        }

        if (preferredMode == SessionMode.ALL && getBestMetricTime(attempts, SessionMode.QUARTER_MILE) != null) {
            return SessionMode.QUARTER_MILE
        }

        return listOf(
            SessionMode.QUARTER_MILE,
            SessionMode.ZERO_TO_200,
            SessionMode.HUNDRED_TO_200,
            SessionMode.ZERO_TO_100
        ).firstOrNull { getBestMetricTime(attempts, it) != null } ?: SessionMode.UNKNOWN
    }

    private fun resolveAttemptPrimaryMode(preferredMode: SessionMode, attempt: DragAttempt): SessionMode {
        if (preferredMode != SessionMode.ALL && preferredMode != SessionMode.UNKNOWN) {
            if (getAttemptMetricTime(attempt, preferredMode) != null) {
                return preferredMode
            }
        }

        if (preferredMode == SessionMode.ALL && getAttemptMetricTime(attempt, SessionMode.QUARTER_MILE) != null) {
            return SessionMode.QUARTER_MILE
        }

        return listOf(
            SessionMode.QUARTER_MILE,
            SessionMode.ZERO_TO_200,
            SessionMode.HUNDRED_TO_200,
            SessionMode.ZERO_TO_100
        ).firstOrNull { getAttemptMetricTime(attempt, it) != null } ?: SessionMode.UNKNOWN
    }

    private fun getBestMetricTime(attempts: List<DragAttempt>, mode: SessionMode): Long? {
        return when (mode) {
            SessionMode.ZERO_TO_100,
            SessionMode.ZERO_TO_200,
            SessionMode.HUNDRED_TO_200,
            SessionMode.QUARTER_MILE -> attempts.mapNotNull { getAttemptMetricTime(it, mode) }.minOrNull()
            SessionMode.ALL,
            SessionMode.UNKNOWN -> null
        }
    }

    private fun getAttemptMetricTime(attempt: DragAttempt, mode: SessionMode): Long? {
        val rawValue = when (mode) {
            SessionMode.ZERO_TO_100 -> attempt.time0to100
            SessionMode.ZERO_TO_200 -> attempt.time0to200
            SessionMode.HUNDRED_TO_200 -> resolve100To200SplitTimeNs(attempt) ?: -1L
            SessionMode.QUARTER_MILE -> attempt.time0to402
            SessionMode.ALL,
            SessionMode.UNKNOWN -> -1L
        }
        return rawValue.takeIf { it > 0L }
    }

    private fun resolve100To200SplitTimeNs(attempt: DragAttempt): Long? {
        val directSplit = attempt.time100to200.takeIf { it > 0L }
        if (directSplit != null) {
            return directSplit
        }

        val time0to100 = attempt.time0to100.takeIf { it > 0L } ?: return null
        val time0to200 = attempt.time0to200.takeIf { it > 0L } ?: return null
        val derivedSplit = time0to200 - time0to100
        return derivedSplit.takeIf { it > 0L }
    }

    private fun getModeLabel(context: Context, mode: SessionMode): String {
        return when (mode) {
            SessionMode.ALL -> context.getString(R.string.drag_mode_all)
            SessionMode.ZERO_TO_100 -> context.getString(R.string.drag_mode_0to100)
            SessionMode.ZERO_TO_200 -> context.getString(R.string.drag_mode_0to200)
            SessionMode.HUNDRED_TO_200 -> context.getString(R.string.drag_mode_100to200)
            SessionMode.QUARTER_MILE -> context.getString(R.string.drag_mode_quarter)
            SessionMode.UNKNOWN -> context.getString(R.string.drag_mode_unknown)
        }
    }

    private fun getMetricColorRes(mode: SessionMode): Int {
        return when (mode) {
            SessionMode.ZERO_TO_100 -> R.color.accent_green
            SessionMode.ZERO_TO_200 -> R.color.accent_blue
            SessionMode.HUNDRED_TO_200 -> R.color.accent_purple
            SessionMode.QUARTER_MILE -> R.color.accent_red
            SessionMode.ALL,
            SessionMode.UNKNOWN -> R.color.text_tertiary
        }
    }

    private fun buildSessionTitle(session: DragSession, position: Int): String {
        val sessionName = session.name?.trim().orEmpty()
        return when {
            sessionName.isNotBlank() -> sessionName
            else -> getString(R.string.drag_compare_session_title_fallback, position + 1)
        }
    }

    private fun formatSessionMetricText(mode: SessionMode, timeNs: Long?): String {
        return "${metricLabel(mode)} • ${formatDurationToSeconds(timeNs ?: -1L)}"
    }

    private fun formatAttemptMetricText(mode: SessionMode, timeNs: Long?): String {
        return "${metricLabel(mode)}\n${formatDurationToSeconds(timeNs ?: -1L)}"
    }

    private fun metricLabel(mode: SessionMode): String {
        return when (mode) {
            SessionMode.ZERO_TO_100 -> "0-100"
            SessionMode.ZERO_TO_200 -> "0-200"
            SessionMode.HUNDRED_TO_200 -> "100-200"
            SessionMode.QUARTER_MILE -> "0-402"
            SessionMode.ALL,
            SessionMode.UNKNOWN -> "--"
        }
    }

    private fun formatTrapSpeed(attempts: List<DragAttempt>, context: Context): String {
        val trapSpeed = attempts.maxOfOrNull { it.maxSpeed } ?: 0f
        return formatTrapSpeed(trapSpeed, context)
    }

    private fun formatTrapSpeed(speedKph: Float, context: Context): String {
        return if (speedKph > 0f) {
            UnitsManager.formatSpeed(speedKph, context, 0)
        } else {
            getString(R.string.drag_compare_attempt_no_data)
        }
    }

    private fun formatPeakG(attempts: List<DragAttempt>): String {
        val peakG = attempts.flatMap { it.gSamples }.maxOrNull()?.takeIf { it > 0f }
        return peakG?.let { String.format(Locale.getDefault(), "%.2fg", it) }
            ?: getString(R.string.drag_compare_attempt_no_data)
    }

    private fun formatPeakG(attempt: DragAttempt): String {
        val peakG = attempt.gSamples.maxOrNull()?.takeIf { it > 0f }
        return peakG?.let { String.format(Locale.getDefault(), "%.2fg", it) }
            ?: getString(R.string.drag_compare_attempt_no_data)
    }

    private fun formatAttemptClock(timestamp: Long): String {
        return if (timestamp > 0L) {
            attemptClockFormat.format(Date(timestamp))
        } else {
            getString(R.string.drag_compare_attempt_no_data)
        }
    }

    private fun formatDurationValue(rawValue: Long?): String {
        if (rawValue == null || rawValue <= 0L) {
            return getString(R.string.drag_compare_attempt_no_data)
        }

        val seconds = if (rawValue >= 1_000_000L) {
            rawValue / 1_000_000_000.0
        } else {
            rawValue / 1000.0
        }
        return String.format(Locale.getDefault(), "%.3f", seconds)
    }

    private fun formatDurationToSeconds(rawValue: Long): String {
        if (rawValue <= 0L) {
            return getString(R.string.drag_compare_attempt_no_data)
        }

        val seconds = if (rawValue >= 1_000_000L) {
            rawValue / 1_000_000_000.0
        } else {
            rawValue / 1000.0
        }
        return String.format(Locale.getDefault(), "%.3fs", seconds)
    }
}