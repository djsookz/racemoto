package com.revix.app.track

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.revix.app.DialogHelper
import com.revix.app.R
import com.revix.app.TrackSessionActivity
import com.revix.app.TrackSessionDetailActivity
import com.revix.app.applySystemBarsPaddingToRoot
import com.revix.app.Profile
import com.revix.app.data.ProfileSessionSummaryStore
import com.revix.app.data.ProfileStorage
import com.revix.app.settings.LanguageManager
import com.revix.app.utils.LapTimeFormatter
import com.google.android.material.button.MaterialButton

class TrackSessionsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRACK_ID = "track_id"
        const val EXTRA_SESSION_ID_FULL = "session_id_full"
        const val EXTRA_TRACK_NAME = "track_name"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private lateinit var trackId: String
    private lateinit var trackName: String
    private lateinit var sessionIdFull: String
    private var resumeSessionId: String? = null

    private lateinit var rvOutings: RecyclerView
    private lateinit var tvNoOutings: TextView
    private lateinit var btnResume: MaterialButton

    private val outings = mutableListOf<TrackOutingsRepository.TrackOutingListItem>()
    private lateinit var adapter: TrackOutingListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_sessions)
        applySystemBarsPaddingToRoot()

        sessionIdFull = intent.getStringExtra(EXTRA_SESSION_ID_FULL).orEmpty()
        if (sessionIdFull.isEmpty()) {
            finish()
            return
        }
        trackId = TrackOutingsRepository.extractTrackIdFromSessionId(this, sessionIdFull)
        trackName = intent.getStringExtra(EXTRA_TRACK_NAME)
            ?: TrackOutingsRepository.getTrackName(this, trackId)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        rvOutings = findViewById(R.id.rvOutings)
        tvNoOutings = findViewById(R.id.tvNoOutings)
        btnResume = findViewById(R.id.btnResume)

        findViewById<TextView>(R.id.tvTrackName).text = trackName

        adapter = TrackOutingListAdapter(
            items = outings,
            onItemClick = { item -> openOutingDetail(item) },
            onDeleteClick = { item -> showDeleteOutingConfirmation(item) }
        )
        rvOutings.layoutManager = LinearLayoutManager(this)
        rvOutings.adapter = adapter

        btnResume.setOnClickListener {
            val sessionId = resumeSessionId ?: return@setOnClickListener
            resumeSession(sessionId)
        }

        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        val stats = TrackOutingsRepository.buildSessionBucketStats(this, sessionIdFull)

        findViewById<TextView>(R.id.tvAllTimeBest).text =
            stats.bestLapMs?.let { TrackOutingsRepository.formatTimeMs(it) } ?: LapTimeFormatter.PLACEHOLDER
        findViewById<TextView>(R.id.tvSummarySessions).text = stats.totalSessions.toString()
        findViewById<TextView>(R.id.tvSummaryLaps).text = stats.totalLaps.toString()
        findViewById<TextView>(R.id.tvSummaryTime).text =
            TrackOutingsRepository.formatSummaryDuration(this, stats.totalDurationMs)

        findViewById<TextView>(R.id.tvTrackMeta).text = resources.getQuantityString(
            R.plurals.track_sessions_meta,
            stats.totalSessions,
            stats.totalSessions
        )

        resumeSessionId = sessionIdFull
        btnResume.visibility = View.VISIBLE

        outings.clear()
        outings.addAll(TrackOutingsRepository.loadOutingsForSessionBucket(this, sessionIdFull))
        adapter.notifyDataSetChanged()

        val hasOutings = outings.isNotEmpty()
        tvNoOutings.visibility = if (hasOutings) View.GONE else View.VISIBLE
        rvOutings.visibility = if (hasOutings) View.VISIBLE else View.GONE
    }

    private fun openOutingDetail(item: TrackOutingsRepository.TrackOutingListItem) {
        val sharedPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val sessionId = item.sessionIdFull
        val outingNumber = item.outingNumber

        startActivity(Intent(this, TrackSessionDetailActivity::class.java).apply {
            putExtra("trackName", trackName)
            putExtra("trackId", sessionId)
            putExtra("outingNumber", outingNumber)
            putExtra("date", sharedPrefs.getString("${sessionId}_outing_${outingNumber}_date", ""))
            putExtra("time", sharedPrefs.getString("${sessionId}_outing_${outingNumber}_time", ""))
            putExtra("duration", sharedPrefs.getString("${sessionId}_outing_${outingNumber}_duration", ""))
            putExtra("totalLaps", sharedPrefs.getString("${sessionId}_outing_${outingNumber}_laps", "0"))
            putExtra("bestLapTime", sharedPrefs.getString("${sessionId}_outing_${outingNumber}_best_lap", LapTimeFormatter.PLACEHOLDER))
            putExtra("maxSpeed", sharedPrefs.getString("${sessionId}_outing_${outingNumber}_max_speed", "0.0 km/h"))
            putExtra("maxAcceleration", sharedPrefs.getString("${sessionId}_outing_${outingNumber}_max_acceleration", "0.00 G"))
            putExtra("maxBraking", sharedPrefs.getString("${sessionId}_outing_${outingNumber}_max_braking", "0.00 G"))
            putExtra("maxCorneringG", sharedPrefs.getString("${sessionId}_outing_${outingNumber}_max_cornering", "0.00 G"))
            putExtra("maxLeanAngle", sharedPrefs.getString("${sessionId}_outing_${outingNumber}_max_lean_angle", "0.0°"))
        })
    }

    private fun resumeSession(sessionId: String) {
        if (!com.revix.app.billing.ProGate.ensureDragOrTrack(
                this,
                com.revix.app.billing.ProAccess.Feature.TRACK
            )
        ) {
            return
        }
        val profile = ProfileStorage.loadProfiles(this)
            .find { it.id == ProfileStorage.getSelectedProfileId(this) }
        val isMotorcycle = profile?.vehicleType == Profile.VehicleType.MOTORCYCLE

        startActivity(Intent(this, TrackSessionActivity::class.java).apply {
            putExtra("track_id", trackId)
            putExtra("track_name", trackName)
            putExtra("resume_session", true)
            putExtra("session_id", sessionId)
            putExtra("is_official", !trackId.startsWith("custom_"))
            putExtra("is_motorcycle", isMotorcycle ?: true)
        })
    }

    private fun showDeleteOutingConfirmation(item: TrackOutingsRepository.TrackOutingListItem) {
        DialogHelper.showDeleteConfirmation(
            context = this,
            title = getString(R.string.track_delete_session_title),
            message = getString(
                R.string.track_delete_outing_message,
                getString(R.string.track_session_title, item.outingNumber)
            ),
            positiveButtonText = getString(R.string.track_delete_button),
            negativeButtonText = getString(R.string.cancel)
        ) {
            deleteOuting(item)
        }
    }

    private fun deleteOuting(item: TrackOutingsRepository.TrackOutingListItem) {
        TrackOutingsRepository.deleteOutingFromSessionBucket(
            this,
            item.sessionIdFull,
            item.outingNumber
        )

        val trackSessionsPrefs = getSharedPreferences("track_sessions", MODE_PRIVATE)
        if (trackSessionsPrefs.getBoolean("has_active_session", false)) {
            val activeSessionId = trackSessionsPrefs.getString("active_session_id", null)
            if (activeSessionId == sessionIdFull) {
                val remainingOutings = getSharedPreferences("track_outings", MODE_PRIVATE)
                    .getInt("${sessionIdFull}_outing_count", 0)
                if (remainingOutings <= 0) {
                    trackSessionsPrefs.edit()
                        .putBoolean("has_active_session", false)
                        .putBoolean("active_session_has_lap", false)
                        .remove("active_track_id")
                        .remove("active_track_name")
                        .remove("active_session_id")
                        .remove("pending_session_id_raw")
                        .apply()
                }
            }
        }

        ProfileSessionSummaryStore.refreshTrackSummary(
            this,
            ProfileStorage.getSelectedProfileId(this)
        )
        Toast.makeText(this, getString(R.string.track_session_deleted), Toast.LENGTH_SHORT).show()
        loadData()

        if (outings.isEmpty()) {
            finish()
        }
    }
}
