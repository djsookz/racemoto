package com.example.clinometer

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ProfileDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_detail)

        val profileId = intent.getLongExtra("profile_id", -1)
        if (profileId == -1L) return finish()

        val profiles = ProfileStorage.loadProfiles(this)
        val profile = profiles.find { it.id == profileId } ?: return finish()

        // UI components
        val tvName = findViewById<TextView>(R.id.tvProfileNameDetail)
        val tvVehicleType = findViewById<TextView>(R.id.tvVehicleTypeDetail)
        val tvBest0to100 = findViewById<TextView>(R.id.tvBest0to100)
        val tvBest0to200 = findViewById<TextView>(R.id.tvBest0to200)
        val tvBest100to200 = findViewById<TextView>(R.id.tvBest100to200)
        val tvMaxSpeed = findViewById<TextView>(R.id.tvMaxSpeed)

        val tvSessionCount = findViewById<TextView>(R.id.tvSessionCount)
        val tvTotalDistance = findViewById<TextView>(R.id.tvTotalDistance)
        val tvTotalDuration = findViewById<TextView>(R.id.tvTotalDuration)

        // Populate header
        tvName.text = profile.name
        tvVehicleType.text = when (profile.vehicleType) {
            Profile.VehicleType.CAR -> getString(R.string.vehicle_type_car)
            Profile.VehicleType.MOTORCYCLE -> getString(R.string.vehicle_type_motorcycle)
        }

        // Best times & max speed
        val bestTimes = getBestTimesFromAllRaces(profileId)
        tvBest0to100.text = formatBestTime(bestTimes.best0to100)
        tvBest0to200.text = formatBestTime(bestTimes.best0to200)
        tvBest100to200.text = formatBestTime(bestTimes.best100to200)
        tvMaxSpeed.text = if (bestTimes.maxSpeed > 0f) {
            getString(R.string.max_speed_format, bestTimes.maxSpeed)
        } else "--"

        // Summary stats
        val allRaces = RouteStorage.loadRaces(this)
        val profileRaces = allRaces.filter { it.profileId == profileId }

        tvSessionCount.text = profileRaces.size.toString()

        val totalDist = profileRaces.sumOf { it.distance }
        tvTotalDistance.text = String.format("%.1f km", totalDist)

        val totalTimeMs = profileRaces.sumOf { it.duration.toLong() }
        val totalSeconds = totalTimeMs / 1000
        val days = totalSeconds / (24 * 3600)
        val hours = (totalSeconds % (24 * 3600)) / 3600
        tvTotalDuration.text = getString(R.string.profile_detail_duration_format, days, hours)
        tvTotalDistance.text = getString(
            R.string.profile_detail_distance_format,
            totalDist
        )
    }

    private fun getBestTimesFromAllRaces(profileId: Long): BestTimes {
        val allRaces = RouteStorage.loadRaces(this)
        val profileRaces = allRaces.filter { it.profileId == profileId }

        var best0to100 = Long.MAX_VALUE
        var best0to200 = Long.MAX_VALUE
        var best100to200 = Long.MAX_VALUE
        var maxSpeed = 0f

        profileRaces.forEach { race ->
            if (race.time0to100 > 0 && race.time0to100 < best0to100) best0to100 = race.time0to100
            if (race.time0to200 > 0 && race.time0to200 < best0to200) best0to200 = race.time0to200
            if (race.time100to200 > 0 && race.time100to200 < best100to200) best100to200 = race.time100to200
            if (race.maxSpeed > maxSpeed) maxSpeed = race.maxSpeed
        }

        return BestTimes(
            best0to100 = if (best0to100 == Long.MAX_VALUE) 0L else best0to100,
            best0to200 = if (best0to200 == Long.MAX_VALUE) 0L else best0to200,
            best100to200 = if (best100to200 == Long.MAX_VALUE) 0L else best100to200,
            maxSpeed = maxSpeed
        )
    }

    private fun formatBestTime(nanos: Long): String =
        if (nanos > 0) String.format("%.3f", nanos / 1_000_000_000.0) else "--"

    data class BestTimes(
        val best0to100: Long,
        val best0to200: Long,
        val best100to200: Long,
        val maxSpeed: Float
    )
}
