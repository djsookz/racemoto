package com.example.clinometer

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.clinometer.StartActivity.CrossfadeTransition



class ProfileDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_detail)

        val profileId = intent.getLongExtra("profile_id", -1)
        if (profileId == -1L) {
            finish()
            return
        }

        val profiles = ProfileStorage.loadProfiles(this)
        val profile = profiles.find { it.id == profileId }

        if (profile == null) {
            finish()
            return
        }

        // Инициализация на UI компонентите
        val tvName: TextView = findViewById(R.id.tvProfileNameDetail)
        val tvVehicleType: TextView = findViewById(R.id.tvVehicleTypeDetail)
        val tvBest0to100: TextView = findViewById(R.id.tvBest0to100)
        val tvBest0to200: TextView = findViewById(R.id.tvBest0to200)
        val tvBest100to200: TextView = findViewById(R.id.tvBest100to200)
        val tvMaxSpeed: TextView = findViewById(R.id.tvMaxSpeed)



        // Попълване на данните
        tvName.text = profile.name
        tvVehicleType.text = when (profile.vehicleType) {
            Profile.VehicleType.CAR -> getString(R.string.vehicle_type_car)
            Profile.VehicleType.MOTORCYCLE -> getString(R.string.vehicle_type_motorcycle)
        }

        // Задаване на иконката според типа превозно средство
        val iconRes = when (profile.vehicleType) {
            Profile.VehicleType.CAR -> R.drawable.ic_car
            Profile.VehicleType.MOTORCYCLE -> R.drawable.ic_motorcycle
        }


        // Изчисляване на най-добрите времена от всички сесии
        val bestTimes = getBestTimesFromAllRaces(profileId)

        // Форматиране на времето с проверка за рекорди
        tvBest0to100.text = formatBestTime(bestTimes.best0to100)
        tvBest0to200.text = formatBestTime(bestTimes.best0to200)
        tvBest100to200.text = formatBestTime(bestTimes.best100to200)
        tvMaxSpeed.text = getString(R.string.max_speed_format, bestTimes.maxSpeed)
    }



    private fun getBestTimesFromAllRaces(profileId: Long): BestTimes {
        val allRaces = RouteStorage.loadRaces(this)
        val profileRaces = allRaces.filter { it.profileId == profileId }

        var bestTime0to100 = Long.MAX_VALUE
        var bestTime0to200 = Long.MAX_VALUE
        var bestTime100to200 = Long.MAX_VALUE
        var maxSpeed = 0f

        profileRaces.forEach { race ->
            // Проверка за най-добро време 0-100
            if (race.time0to100 > 0 && race.time0to100 < bestTime0to100) {
                bestTime0to100 = race.time0to100
            }

            // Проверка за най-добро време 0-200
            if (race.time0to200 > 0 && race.time0to200 < bestTime0to200) {
                bestTime0to200 = race.time0to200
            }

            // Проверка за най-добро време 100-200
            if (race.time100to200 > 0 && race.time100to200 < bestTime100to200) {
                bestTime100to200 = race.time100to200
            }

            // Проверка за максимална скорост
            if (race.maxSpeed > maxSpeed) {
                maxSpeed = race.maxSpeed
            }
        }

        return BestTimes(
            best0to100 = if (bestTime0to100 == Long.MAX_VALUE) 0L else bestTime0to100,
            best0to200 = if (bestTime0to200 == Long.MAX_VALUE) 0L else bestTime0to200,
            best100to200 = if (bestTime100to200 == Long.MAX_VALUE) 0L else bestTime100to200,
            maxSpeed = maxSpeed
        )
    }

    private fun formatBestTime(nanos: Long): String {
        return if (nanos > 0) {
            val seconds = nanos / 1_000_000_000.0
            String.format("%.3f s", seconds)
        } else {
            "N/A"
        }
    }

    data class BestTimes(
        val best0to100: Long,
        val best0to200: Long,
        val best100to200: Long,
        val maxSpeed: Float
    )
}