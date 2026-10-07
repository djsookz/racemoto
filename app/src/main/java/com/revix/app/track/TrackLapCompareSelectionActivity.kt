package com.revix.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.revix.app.data.ProfileStorage
import com.revix.app.utils.LapTimeFormatter
import com.revix.app.settings.LanguageManager
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.Locale

class TrackLapCompareSelectionActivity : AppCompatActivity() {

    private data class TrackLapCandidate(
        val sessionId: String,
        val outingNumber: Int,
        val lapNumber: Int,
        val lapTimeText: String
    )

    private data class SessionGroup(
        val key: String,
        val sessionId: String,
        val outingNumber: Int,
        val profileId: Long?,
        val profileName: String,
        val vehicleType: Profile.VehicleType?,
        val date: String,
        val time: String,
        val sortEpoch: Long,
        val recordedWithRaceBox: Boolean,
        val laps: List<TrackLapCandidate>
    )

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private lateinit var tabProfiles: TabLayout
    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvEmptyState: TextView
    private lateinit var rvSessions: RecyclerView
    private lateinit var adapter: SessionsAdapter

    private var currentSessionId: String = ""
    private var currentOutingNumber: Int = -1
    private var currentLapNumber: Int = -1
    private var trackName: String = ""
    private var trackId: String = ""
    private var isCurrentMotorcycle: Boolean = true
    private var originRaceId: Long = -1L
    private var originIsPointToPoint: Boolean = false

    private var allProfiles: List<Profile> = emptyList()
    private var profilesById: Map<Long, Profile> = emptyMap()
    private var allGroups: List<SessionGroup> = emptyList()
    private var selectedProfileId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_lap_compare_selection)

        currentSessionId = intent.getStringExtra("current_session_id") ?: ""
        currentOutingNumber = intent.getIntExtra("current_outing_number", -1)
        currentLapNumber = intent.getIntExtra("current_lap_number", -1)
        trackName = intent.getStringExtra("track_name") ?: ""
        trackId = intent.getStringExtra("track_id") ?: ""
        isCurrentMotorcycle = intent.getBooleanExtra("is_motorcycle", true)
        originRaceId = intent.getLongExtra("origin_race_id", -1L)
        originIsPointToPoint = intent.getBooleanExtra("origin_is_point_to_point", false)

        if (currentSessionId.isBlank() || currentOutingNumber <= 0 || currentLapNumber <= 0) {
            Toast.makeText(this, getString(R.string.track_compare_invalid_current_lap), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupViews()
        loadData()
    }

    private fun setupViews() {
        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        tabProfiles = findViewById(R.id.tabProfiles)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        rvSessions = findViewById(R.id.rvSessions)
        rvSessions.layoutManager = LinearLayoutManager(this)
        adapter = SessionsAdapter(::onLapSelected)
        rvSessions.adapter = adapter

        tvHeaderTitle.text = getString(R.string.track_select_lap_to_compare)

        tabProfiles.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val profile = allProfiles.getOrNull(tab?.position ?: -1) ?: return
                if (selectedProfileId == profile.id && adapter.itemCount > 0) {
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

        selectedProfileId = extractProfileIdFromSessionId(currentSessionId)
            ?.takeIf { profilesById.containsKey(it) }
            ?: allProfiles.firstOrNull()?.id
            ?: -1L

        allGroups = loadAllSessionGroups()

        if (allGroups.isEmpty() && allProfiles.isEmpty()) {
            showEmptyState(getString(R.string.drag_compare_no_profiles))
            tabProfiles.visibility = View.GONE
            return
        }

        bindProfileTabs()
        applySelectedProfile()
    }

    private fun loadAllSessionGroups(): List<SessionGroup> {
        val sharedPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)

        val normalizedTrackId = if (trackId.isNotBlank()) {
            extractTrackIdFromSessionId(trackId)
        } else {
            extractTrackIdFromSessionId(currentSessionId)
        }

        if (normalizedTrackId.isBlank()) {
            return emptyList()
        }

        val sessionIds = sharedPrefs.all.keys
            .filter { it.endsWith("_outing_count") }
            .map { it.removeSuffix("_outing_count") }
            .distinct()

        val groups = mutableListOf<SessionGroup>()

        sessionIds.forEach { sessionId ->
            val sessionTrackId = extractTrackIdFromSessionId(sessionId)
            if (sessionTrackId != normalizedTrackId) return@forEach

            val outingCount = sharedPrefs.getInt("${sessionId}_outing_count", 0)
            for (outing in 1..outingCount) {
                val lapDataCount = sharedPrefs.getInt("${sessionId}_outing_${outing}_lap_data_count", 0)
                if (lapDataCount <= 0) continue

                val laps = mutableListOf<TrackLapCandidate>()
                for (lap in 1..lapDataCount) {
                    if (sessionId == currentSessionId && outing == currentOutingNumber && lap == currentLapNumber) continue

                    val lapTime = LapTimeFormatter.formatDurationDisplay(
                        sharedPrefs.getString("${sessionId}_outing_${outing}_lap_${lap}", LapTimeFormatter.PLACEHOLDER)
                            ?: LapTimeFormatter.PLACEHOLDER
                    )
                    laps.add(
                        TrackLapCandidate(
                            sessionId = sessionId,
                            outingNumber = outing,
                            lapNumber = lap,
                            lapTimeText = lapTime
                        )
                    )
                }

                if (laps.isEmpty()) continue

                val profileId = extractProfileIdFromSessionId(sessionId)
                val profile = profileId?.let { profilesById[it] }
                val profileName = profile?.name ?: getString(R.string.track_compare_unknown_profile)
                val date = sharedPrefs.getString("${sessionId}_outing_${outing}_date", "") ?: ""
                val time = sharedPrefs.getString("${sessionId}_outing_${outing}_time", "") ?: ""
                val sortEpoch = parseDateTimeToEpoch(date, time)
                val recordedWithRaceBox = sharedPrefs.getBoolean(
                    "${sessionId}_outing_${outing}_recorded_with_racebox",
                    false
                )

                groups.add(
                    SessionGroup(
                        key = "${sessionId}_$outing",
                        sessionId = sessionId,
                        outingNumber = outing,
                        profileId = profileId,
                        profileName = profileName,
                        vehicleType = profile?.vehicleType,
                        date = date,
                        time = time,
                        sortEpoch = sortEpoch,
                        recordedWithRaceBox = recordedWithRaceBox,
                        laps = laps.sortedBy { it.lapNumber }
                    )
                )
            }
        }

        return groups.sortedWith(compareByDescending<SessionGroup> { it.sortEpoch }.thenBy { it.profileName })
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
            adapter.update(emptyList())
            showEmptyState(getString(R.string.drag_compare_no_profiles))
            return
        }

        val filtered = allGroups.filter { it.profileId == selectedProfileId }
        adapter.update(filtered)

        if (filtered.isEmpty()) {
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

    private fun onLapSelected(target: TrackLapCandidate) {
        val finalTrackName = trackName.ifBlank { extractTrackIdFromSessionId(currentSessionId) }
        val finalTrackId = trackId.ifBlank { extractTrackIdFromSessionId(currentSessionId) }

        startActivity(Intent(this, TrackLapCompareActivity::class.java).apply {
            putExtra("current_session_id", currentSessionId)
            putExtra("current_outing_number", currentOutingNumber)
            putExtra("current_lap_number", currentLapNumber)

            putExtra("compare_session_id", target.sessionId)
            putExtra("compare_outing_number", target.outingNumber)
            putExtra("compare_lap_number", target.lapNumber)

            putExtra("track_id", finalTrackId)
            putExtra("track_name", finalTrackName)
            putExtra("is_motorcycle", isCurrentMotorcycle)
            putExtra("origin_race_id", originRaceId)
            putExtra("origin_is_point_to_point", originIsPointToPoint)
        })

        finish()
    }

    private fun extractProfileIdFromSessionId(sessionId: String): Long? {
        val match = Regex("^(\\d+)_").find(sessionId) ?: return null
        return match.groupValues.getOrNull(1)?.toLongOrNull()
    }

    private fun parseDateTimeToEpoch(date: String, time: String): Long {
        val dateTime = "${date.trim()} ${time.trim()}".trim()
        if (dateTime.isBlank()) return 0L

        val patterns = listOf("dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy HH:mm")
        for (pattern in patterns) {
            runCatching {
                val format = SimpleDateFormat(pattern, Locale.getDefault())
                format.parse(dateTime)?.time ?: 0L
            }.onSuccess { return it }
        }

        return 0L
    }

    private fun extractTrackIdFromSessionId(sessionId: String): String {
        return TrackSessionIdUtils.extractTrackIdFromSessionId(this, sessionId)
    }

    private inner class SessionsAdapter(
        private val onLapSelected: (TrackLapCandidate) -> Unit
    ) : RecyclerView.Adapter<SessionsAdapter.SessionViewHolder>() {

        private var sessions: List<SessionGroup> = emptyList()
        private val expandedKeys = mutableSetOf<String>()

        fun update(newSessions: List<SessionGroup>) {
            sessions = newSessions
            expandedKeys.clear()
            if (sessions.isNotEmpty()) {
                expandedKeys.add(sessions.first().key)
            }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_track_compare_session, parent, false)
            return SessionViewHolder(view)
        }

        override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
            val item = sessions[position]
            holder.bind(item, expandedKeys.contains(item.key))
        }

        override fun getItemCount(): Int = sessions.size

        inner class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvSessionTitle: TextView = itemView.findViewById(R.id.tvSessionTitle)
            private val tvSessionMeta: TextView = itemView.findViewById(R.id.tvSessionMeta)
            private val tvSessionRaceBoxBadge: TextView = itemView.findViewById(R.id.tvSessionRaceBoxBadge)
            private val tvVehicleBadge: TextView = itemView.findViewById(R.id.tvVehicleBadge)
            private val tvLapCount: TextView = itemView.findViewById(R.id.tvLapCount)
            private val rvLaps: RecyclerView = itemView.findViewById(R.id.rvLaps)
            private val llLapsContainer: View = itemView.findViewById(R.id.llLapsContainer)

            fun bind(group: SessionGroup, isExpanded: Boolean) {
                tvSessionTitle.text = group.profileName
                val dateTime = "${group.date} ${group.time}".trim()
                tvSessionMeta.text = getString(R.string.track_compare_session_meta, group.outingNumber, dateTime)
                tvLapCount.text = resources.getQuantityString(R.plurals.track_compare_laps_count, group.laps.size, group.laps.size)
                com.revix.app.racebox.RaceBoxSessionUi.bindLabel(
                    tvSessionRaceBoxBadge,
                    group.recordedWithRaceBox
                )

                when (group.vehicleType) {
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

                rvLaps.layoutManager = LinearLayoutManager(itemView.context)
                rvLaps.adapter = LapsAdapter(group.laps)
                llLapsContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE

                itemView.setOnClickListener {
                    if (expandedKeys.contains(group.key)) {
                        expandedKeys.remove(group.key)
                    } else {
                        expandedKeys.add(group.key)
                    }
                    notifyItemChanged(bindingAdapterPosition)
                }
            }
        }
    }

    private inner class LapsAdapter(
        private val laps: List<TrackLapCandidate>
    ) : RecyclerView.Adapter<LapsAdapter.LapViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LapViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_track_compare_lap, parent, false)
            return LapViewHolder(view)
        }

        override fun onBindViewHolder(holder: LapViewHolder, position: Int) {
            holder.bind(laps[position])
        }

        override fun getItemCount(): Int = laps.size

        inner class LapViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvLapTitle: TextView = itemView.findViewById(R.id.tvLapTitle)
            private val tvLapTime: TextView = itemView.findViewById(R.id.tvLapTime)

            fun bind(item: TrackLapCandidate) {
                tvLapTitle.text = getString(R.string.track_compare_lap_title, item.lapNumber)
                tvLapTime.text = item.lapTimeText

                itemView.setOnClickListener {
                    onLapSelected(item)
                }
            }
        }
    }
}
