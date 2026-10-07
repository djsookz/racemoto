package com.revix.app.drag

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
import com.revix.app.DragAttempt
import com.revix.app.DragSession
import com.revix.app.DragStorage
import com.revix.app.Profile
import com.revix.app.R
import com.revix.app.data.ProfileStorage
import com.revix.app.settings.LanguageManager
import com.revix.app.settings.UnitsManager
import com.revix.app.utils.DragTimeFormatter
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
    private lateinit var tvHeaderTitle: TextView
    private lateinit var rvSessions: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var sessionsAdapter: SessionsAdapter

    private var currentSessionId: Long = -1L
    private var currentAttemptId: Long = -1L
    private var compareSessionId: Long = -1L
    private var selectedProfileId: Long = -1L

    private var profilesById: Map<Long, Profile> = emptyMap()
    private var allProfiles: List<Profile> = emptyList()
    private var displayedProfiles: List<Profile> = emptyList()
    private var allSessions: List<DragSession> = emptyList()
    private var currentSession: DragSession? = null

    private fun formatSessionDay(timestamp: Long): String {
        return SimpleDateFormat("dd", LanguageManager.getCurrentLocale(this))
            .format(Date(timestamp))
    }

    private fun formatSessionMonth(timestamp: Long): String {
        val locale = LanguageManager.getCurrentLocale(this)
        return SimpleDateFormat("MMM", locale).format(Date(timestamp)).uppercase(locale)
    }

    private fun formatAttemptClock(timestamp: Long): String {
        return if (timestamp > 0L) {
            SimpleDateFormat("HH:mm", LanguageManager.getCurrentLocale(this))
                .format(Date(timestamp))
        } else {
            "--:--"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_selection)

        currentSessionId = intent.getLongExtra("current_session_id", -1L)
        currentAttemptId = intent.getLongExtra("current_attempt_id", -1L)
        compareSessionId = intent.getLongExtra("compare_session_id", -1L)

        setupViews()
        loadData()
    }

    private fun setupViews() {
        tabProfiles = findViewById(R.id.tabProfiles)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        rvSessions = findViewById(R.id.rvSessions)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        rvSessions.layoutManager = LinearLayoutManager(this)
        sessionsAdapter = SessionsAdapter { session ->
            val attempts = buildAvailableAttempts(session)
            if (attempts.size == 1) {
                openCompare(session, attempts[0].attempt)
            } else {
                startActivity(Intent(this, SessionSelectionActivity::class.java).apply {
                    putExtra("current_session_id", currentSessionId)
                    putExtra("current_attempt_id", currentAttemptId)
                    putExtra("compare_session_id", session.id)
                })
                finish()
            }
        }
        rvSessions.adapter = sessionsAdapter

        findViewById<View>(R.id.btnBack)?.setOnClickListener {
            finish()
        }

        tabProfiles.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val profile = displayedProfiles.getOrNull(tab?.position ?: -1) ?: return
                if (selectedProfileId == profile.id && sessionsAdapter.itemCount > 0) {
                    return
                }
                selectedProfileId = profile.id
                applySelectedProfile()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit

            override fun onTabReselected(tab: TabLayout.Tab?) {
                val profile = displayedProfiles.getOrNull(tab?.position ?: -1) ?: return
                selectedProfileId = profile.id
                applySelectedProfile()
            }
        })
    }

    private fun loadData() {
        allProfiles = ProfileStorage.loadProfiles(this)
        profilesById = allProfiles.associateBy { it.id }
        allSessions = DragStorage.loadDragSessions(this).sortedByDescending { it.timestamp }
        currentSession = allSessions.firstOrNull { it.id == currentSessionId }

        val garageProfileId = ProfileStorage.getSelectedProfileId(this)
        selectedProfileId = currentSession?.profileId
            ?.takeIf { profilesById.containsKey(it) }
            ?: garageProfileId.takeIf { profilesById.containsKey(it) }
            ?: allProfiles.firstOrNull()?.id
            ?: -1L

        if (compareSessionId > 0L) {
            bindAttemptSelectionMode()
            return
        }

        tvHeaderTitle.text = getString(R.string.select_session_to_compare)
        bindProfileTabs()
        applySelectedProfile()
    }

    private fun bindAttemptSelectionMode() {
        tvHeaderTitle.text = getString(R.string.select_attempt_to_compare)
        tabProfiles.visibility = View.GONE

        val compareSession = allSessions.firstOrNull { it.id == compareSessionId }
        if (compareSession == null) {
            showEmptyState(getString(R.string.drag_compare_session_not_found))
            return
        }

        val attempts = buildAvailableAttempts(compareSession)
        if (attempts.isEmpty()) {
            showEmptyState(getString(R.string.drag_compare_no_attempts_for_session))
            return
        }

        if (attempts.size == 1) {
            openCompare(compareSession, attempts[0].attempt)
            return
        }

        val preferredMode = resolveSessionMode(compareSession)
        rvSessions.adapter = AttemptsAdapter(attempts, preferredMode) { attempt ->
            openCompare(compareSession, attempt)
        }
        hideEmptyState()
    }

    private fun openCompare(session: DragSession, attempt: DragAttempt) {
        startActivity(Intent(this, CompareAttemptsActivity::class.java).apply {
            putExtra("current_session_id", currentSessionId)
            putExtra("current_attempt_id", currentAttemptId)
            putExtra("compare_session_id", session.id)
            putExtra("compare_attempt_id", attempt.id)
        })
        finish()
    }

    private fun bindProfileTabs() {
        displayedProfiles = if (selectedProfileId > 0L) {
            allProfiles.filter { it.id == selectedProfileId } +
                allProfiles.filter { it.id != selectedProfileId }
        } else {
            allProfiles
        }

        tabProfiles.removeAllTabs()
        tabProfiles.visibility = if (displayedProfiles.isEmpty()) View.GONE else View.VISIBLE

        displayedProfiles.forEachIndexed { index, profile ->
            val originalIndex = allProfiles.indexOfFirst { it.id == profile.id }.takeIf { it >= 0 } ?: index
            tabProfiles.addTab(tabProfiles.newTab().setText(getProfileTabTitle(profile, originalIndex)))
        }

        if (displayedProfiles.isEmpty()) {
            return
        }

        if (tabProfiles.selectedTabPosition != 0) {
            tabProfiles.getTabAt(0)?.select()
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
            .sortedWith(
                compareByDescending<AttemptItem> { it.attempt.timestamp }
                    .thenByDescending { it.originalNumber }
            )
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
        private val onSessionSelected: (DragSession) -> Unit
    ) : RecyclerView.Adapter<SessionsAdapter.SessionViewHolder>() {

        private var sessionItems: List<SessionItem> = emptyList()

        fun updateSessions(newSessionItems: List<SessionItem>) {
            sessionItems = newSessionItems
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
                onSessionSelected = {
                    onSessionSelected(item.session)
                }
            )
        }

        override fun getItemCount(): Int = sessionItems.size

        inner class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val summaryContainer: LinearLayout = itemView.findViewById(R.id.llSessionSummary)
            private val tvDay: TextView = itemView.findViewById(R.id.tvSessionDay)
            private val tvMonth: TextView = itemView.findViewById(R.id.tvSessionMonth)
            private val tvSessionTitle: TextView = itemView.findViewById(R.id.tvSessionTitle)
            private val tvSessionMeta: TextView = itemView.findViewById(R.id.tvSessionMeta)
            private val tvSessionRaceBoxBadge: TextView = itemView.findViewById(R.id.tvSessionRaceBoxBadge)
            private val tvSessionHeroTime: TextView = itemView.findViewById(R.id.tvSessionHeroTime)

            fun bind(
                item: SessionItem,
                position: Int,
                onSessionSelected: () -> Unit
            ) {
                val context = itemView.context
                val session = item.session
                val attempts = item.availableAttempts.map { it.attempt }
                val sessionMode = resolveSessionMode(session)
                val primaryMode = resolveSessionPrimaryMode(sessionMode, attempts)

                tvDay.text = formatSessionDay(session.timestamp)
                tvMonth.text = formatSessionMonth(session.timestamp)
                tvSessionTitle.text = buildSessionTitle(session, position)
                tvSessionMeta.text = getString(
                    R.string.drag_compare_session_meta_short,
                    getModeLabel(context, sessionMode),
                    getString(R.string.drag_compare_runs_count, item.availableAttempts.size)
                )
                com.revix.app.racebox.RaceBoxSessionUi.bindLabel(
                    tvSessionRaceBoxBadge,
                    session.recordedWithRaceBox
                )

                tvSessionHeroTime.text = formatDurationValue(getBestMetricTime(attempts, primaryMode))
                tvSessionHeroTime.setTextColor(ContextCompat.getColor(context, R.color.text_primary))

                summaryContainer.setOnClickListener {
                    onSessionSelected()
                }
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

            fun bind(item: AttemptItem, onAttemptSelected: () -> Unit) {
                val context = itemView.context
                val attempt = item.attempt
                val primaryMode = resolveAttemptPrimaryMode(preferredMode, attempt)
                val primaryTime = getAttemptMetricTime(attempt, primaryMode)

                tvAttemptTitle.text = getString(R.string.drag_run_short_format, item.originalNumber)
                tvAttemptMeta.text = getString(
                    R.string.drag_compare_attempt_recorded_at,
                    formatAttemptClock(attempt.timestamp)
                )
                tvAttemptPrimaryLabel.text = getModeLabel(context, primaryMode)
                tvAttemptPrimaryValue.text = formatDurationValue(primaryTime)
                tvAttemptPrimaryValue.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                tvAttemptPrimaryUnit.visibility = if (primaryTime != null) View.VISIBLE else View.GONE

                itemView.setOnClickListener {
                    onAttemptSelected()
                }
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
            SessionMode.HUNDRED_TO_200 -> DragAttemptMetrics.resolve100To200SplitTimeNs(attempt) ?: -1L
            SessionMode.QUARTER_MILE -> attempt.time0to402
            SessionMode.ALL,
            SessionMode.UNKNOWN -> -1L
        }
        return rawValue.takeIf { it > 0L }
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
            SessionMode.ZERO_TO_100 -> UnitsManager.formatDragSpeedIntervalLabel(0, 100, this)
            SessionMode.ZERO_TO_200 -> UnitsManager.formatDragSpeedIntervalLabel(0, 200, this)
            SessionMode.HUNDRED_TO_200 -> UnitsManager.formatDragSpeedIntervalLabel(100, 200, this)
            SessionMode.QUARTER_MILE -> UnitsManager.formatDragZeroTo402IntervalLabel(this)
            SessionMode.ALL,
            SessionMode.UNKNOWN -> getString(R.string.drag_compare_attempt_no_data)
        }
    }

    private fun resolveAttemptTrapSpeedKmh(attempt: DragAttempt): Float {
        return DragAttemptMetrics.resolveTrapSpeedKmh(attempt) ?: 0f
    }

    private fun formatTrapSpeed(attempts: List<DragAttempt>, context: Context): String {
        val trapSpeed = attempts.maxOfOrNull { resolveAttemptTrapSpeedKmh(it) } ?: 0f
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
        val peakG = resolvePreferredPeakGValue(attempts)
        return peakG?.let { String.format(Locale.getDefault(), "%.2fg", it) }
            ?: getString(R.string.drag_compare_attempt_no_data)
    }

    private fun formatPeakG(attempt: DragAttempt): String {
        val peakG = resolvePreferredPeakGValue(attempt)
        return peakG?.let { String.format(Locale.getDefault(), "%.2fg", it) }
            ?: getString(R.string.drag_compare_attempt_no_data)
    }

    private fun resolvePreferredPeakGValue(attempts: List<DragAttempt>): Float? {
        val peaks = attempts.mapNotNull { resolvePreferredPeakGValue(it) }
        return peaks.maxOrNull()?.takeIf { it > 0f }
    }

    private fun resolvePreferredPeakGValue(attempt: DragAttempt): Float? {
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

    private fun formatDurationValue(rawValue: Long?): String {
        if (rawValue == null || rawValue <= 0L) {
            return getString(R.string.drag_compare_attempt_no_data)
        }

        val seconds = if (rawValue >= 1_000_000L) {
            rawValue / 1_000_000_000.0
        } else {
            rawValue / 1000.0
        }
        return DragTimeFormatter.formatSeconds(seconds)
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
        return String.format(Locale.US, "%.2fs", seconds)
    }
}