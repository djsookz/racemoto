// ProfileDetailActivity.kt - Обновена версия с reset функционалност

package com.example.clinometer.garage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.widget.Toast
import com.example.clinometer.*
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.settings.LanguageManager
import java.io.File

class ProfileDetailActivity : AppCompatActivity() {

    private lateinit var profile: Profile
    private var profileId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_detail)

        profileId = intent.getLongExtra("profile_id", -1)
        if (profileId == -1L) return finish()

        val profiles = ProfileStorage.loadProfiles(this)
        profile = profiles.find { it.id == profileId } ?: return finish()

        val tvName = findViewById<TextView>(R.id.tvProfileNameDetail)
        val tvVehicleType = findViewById<TextView>(R.id.tvVehicleTypeDetail)
        val tvNameOverlay = findViewById<TextView>(R.id.tvProfileNameOverlay)
        val tvVehicleTypeOverlay = findViewById<TextView>(R.id.tvVehicleTypeOverlay)
        val ivProfileImage = findViewById<ImageView>(R.id.ivProfileImage)
        val flProfileImageContainer = findViewById<android.widget.FrameLayout>(R.id.flProfileImageContainer)
        val llOriginalLayout = findViewById<android.widget.LinearLayout>(R.id.llOriginalLayout)
        val vFadeOverlay = findViewById<View>(R.id.vFadeOverlay)

        val btnReset0to100 = findViewById<ImageButton>(R.id.btnReset0to100)
        val btnReset0to200 = findViewById<ImageButton>(R.id.btnReset0to200)
        val btnReset100to200 = findViewById<ImageButton>(R.id.btnReset100to200)
        val btnResetMaxSpeed = findViewById<ImageButton>(R.id.btnResetMaxSpeed)
        val tvBest0to402 = findViewById<TextView>(R.id.tvBest0to402)
        val btnReset0to402 = findViewById<ImageButton>(R.id.btnReset0to402)

        // Задаваме текста за името и типа
        val nameText = profile.name
        val typeText = when (profile.vehicleType) {
            Profile.VehicleType.CAR ->"🚗 " +  getString(R.string.vehicle_type_car)
            Profile.VehicleType.MOTORCYCLE -> "🏍️ " + getString(R.string.vehicle_type_motorcycle)
        }
        
        tvName.text = nameText
        tvVehicleType.text = typeText
        tvNameOverlay.text = nameText
        tvVehicleTypeOverlay.text = typeText

        // Зареждаме снимката или показваме оригиналния layout
        updateProfileImage(ivProfileImage, flProfileImageContainer, llOriginalLayout, vFadeOverlay)

        updateStatistics()

        btnReset0to100.setOnClickListener {
            showResetConfirmation("0-100 km/h") {
                resetStat(StatType.ZERO_TO_100)
                updateStatistics()
            }
        }

        btnReset0to200.setOnClickListener {
            showResetConfirmation("0-200 km/h") {
                resetStat(StatType.ZERO_TO_200)
                updateStatistics()
            }
        }

        btnReset100to200.setOnClickListener {
            showResetConfirmation("100-200 km/h") {
                resetStat(StatType.HUNDRED_TO_200)
                updateStatistics()
            }
        }

        btnResetMaxSpeed.setOnClickListener {
            showResetConfirmation(getString(R.string.profile_detail_max_speed)) {
                resetStat(StatType.MAX_SPEED)
                updateStatistics()
            }
        }
        btnReset0to402.setOnClickListener {
            showResetConfirmation("0-402m") {
                resetStat(StatType.ZERO_TO_402)
                updateStatistics()
            }
        }
    }

    private fun updateStatistics() {
        val tvBest0to100 = findViewById<TextView>(R.id.tvBest0to100)
        val tvBest0to200 = findViewById<TextView>(R.id.tvBest0to200)
        val tvBest100to200 = findViewById<TextView>(R.id.tvBest100to200)
        val tvMaxSpeed = findViewById<TextView>(R.id.tvMaxSpeed)
        val tvSessionCount = findViewById<TextView>(R.id.tvSessionCount)
        val tvTotalDistance = findViewById<TextView>(R.id.tvTotalDistance)
        val tvTotalDuration = findViewById<TextView>(R.id.tvTotalDuration)
        val tvBest0to402 = findViewById<TextView>(R.id.tvBest0to402)

        val bestTimes = getBestTimesFromAllRaces(profileId)
        tvBest0to100.text = formatBestTime(bestTimes.best0to100)
        tvBest0to200.text = formatBestTime(bestTimes.best0to200)
        tvBest100to200.text = formatBestTime(bestTimes.best100to200)
        tvMaxSpeed.text = if (bestTimes.maxSpeed > 0f) {
            getString(R.string.max_speed_format, bestTimes.maxSpeed)
        } else "--"
        tvBest0to402.text = formatBestTime(bestTimes.best0to402)

        val allRaces = RouteStorage.loadRaces(this)
        val profileRaces = allRaces.filter { it.profileId == profileId }

        val allDragSessions = DragStorage.loadDragSessions(this)
        val profileDragSessions = allDragSessions.filter { it.profileId == profileId }

        val totalSessions = profileRaces.size + profileDragSessions.size
        tvSessionCount.text = totalSessions.toString()

        val totalDist = profileRaces.sumOf { it.distance }
        tvTotalDistance.text = String.format("%.1f km", totalDist)

        // Изчисляваме времето от races - ако duration е 0 или невалидно, изчисляваме от routePoints
        val racesTimeMs = profileRaces.sumOf { race ->
            android.util.Log.d("ProfileDetail", "   Checking race ${race.id}: duration=${race.duration}ms, timestamp=${race.timestamp}, absoluteTimestamp=${race.absoluteTimestamp}")
            if (race.duration > 0) {
                android.util.Log.d("ProfileDetail", "   Race ${race.id}: using stored duration=${race.duration}ms")
                race.duration.toLong()
            } else if (race.absoluteTimestamp > race.timestamp && race.absoluteTimestamp > 0) {
                // Fallback: използвай absoluteTimestamp ако е различен от timestamp
                // Но това не е duration, а само timestamp, така че не можем да го използваме директно
                // Затова продължаваме към изчисляване от routePoints
                android.util.Log.d("ProfileDetail", "   Race ${race.id}: duration is 0, will calculate from routePoints")
                0L // Продължаваме към изчисляване от routePoints
            } else {
                android.util.Log.d("ProfileDetail", "   Race ${race.id}: duration is 0, calculating from routePoints...")
                // Изчисляваме duration от routePoints (разлика между първата и последната точка)
                // Ако routePoints не са заредени в паметта, зареждаме ги от storage
                val points = if (race.routePoints.isNotEmpty()) {
                    race.routePoints
                } else {
                    // Зареждаме routePoints от storage само ако не са в паметта
                    // ОПТИМИЗАЦИЯ: Зареждаме само първата и последната точка за по-бързо изчисление
                    val allPoints = RouteStorage.loadRoutePoints(this, race.id)
                    if (allPoints.size >= 2) {
                        listOf(allPoints.first(), allPoints.last())
                    } else {
                        allPoints
                    }
                }
                
                if (points.isNotEmpty()) {
                    // Използваме absoluteTime вместо timestamp, защото timestamp е относителен
                    val firstPoint = points.first()
                    val lastPoint = points.last()
                    val firstTime = firstPoint.absoluteTime
                    val lastTime = lastPoint.absoluteTime
                    
                    // Ако absoluteTime е 0 или невалидно, опитай се с timestamp (относителен)
                    val calculatedDuration = if (firstTime > 0 && lastTime > 0 && lastTime > firstTime) {
                        lastTime - firstTime
                    } else {
                        // Fallback: използвай timestamp (относителен)
                        val firstTimestamp = firstPoint.timestamp
                        val lastTimestamp = lastPoint.timestamp
                        if (lastTimestamp > firstTimestamp) {
                            lastTimestamp - firstTimestamp
                        } else {
                            0L
                        }
                    }
                    
                    android.util.Log.d("ProfileDetail", "   Race ${race.id}: firstTime=$firstTime, lastTime=$lastTime")
                    android.util.Log.d("ProfileDetail", "   Race ${race.id}: calculated duration = ${calculatedDuration}ms (${calculatedDuration/1000}s)")
                    calculatedDuration.coerceAtLeast(0L)
                } else {
                    android.util.Log.w("ProfileDetail", "   Race ${race.id}: no points available")
                    0L
                }
            }
        }
        
        val dragTimeMs = profileDragSessions.sumOf { session ->
            session.attempts.sumOf { attempt ->
                attempt.duration / 1_000_000 // от nanoseconds в milliseconds
            }
        }

        val totalTimeMs = racesTimeMs + dragTimeMs
        
        // Debug logging
        android.util.Log.d("ProfileDetail", "⏱️ Time calculation:")
        android.util.Log.d("ProfileDetail", "   Races count: ${profileRaces.size}")
        android.util.Log.d("ProfileDetail", "   Races time (ms): $racesTimeMs")
        android.util.Log.d("ProfileDetail", "   Drag sessions count: ${profileDragSessions.size}")
        android.util.Log.d("ProfileDetail", "   Drag time (ms): $dragTimeMs")
        android.util.Log.d("ProfileDetail", "   Total time (ms): $totalTimeMs")
        
        val totalSeconds = totalTimeMs / 1000
        val totalHours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        
        android.util.Log.d("ProfileDetail", "   Total seconds: $totalSeconds")
        android.util.Log.d("ProfileDetail", "   Hours: $totalHours, Minutes: $minutes")
        
        tvTotalDuration.text = getString(R.string.profile_detail_duration_format, totalHours, minutes)
    }

    private fun showResetConfirmation(statName: String, onConfirm: () -> Unit) {
        val resetDialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(getString(R.string.profile_detail_reset_title, statName))
            .setMessage(getString(R.string.profile_detail_reset_message, statName))
            .setPositiveButton(getString(R.string.profile_detail_reset_button)) { _, _ ->
                onConfirm()
                Toast.makeText(this, "✅ $statName е нулиран", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.garage_cancel_button), null)
            .create()
        
        DialogHelper.styleDialogButtons(resetDialog)
        resetDialog.show()
    }

    private fun resetStat(statType: StatType) {
        val allRaces = RouteStorage.loadRaces(this).toMutableList()
        val profileRaces = allRaces.filter { it.profileId == profileId }

        when (statType) {
            StatType.ZERO_TO_100 -> {
                val bestRace = profileRaces
                    .filter { it.time0to100 > 0 }
                    .minByOrNull { it.time0to100 }

                bestRace?.let { race ->
                    val index = allRaces.indexOfFirst { it.id == race.id }
                    if (index != -1) {
                        allRaces[index] = race.copy(time0to100 = 0)
                        RouteStorage.saveRaces(this, allRaces)
                    }
                }

                val profiles = ProfileStorage.loadProfiles(this).toMutableList()
                val profileIndex = profiles.indexOfFirst { it.id == profileId }
                if (profileIndex != -1) {
                    profiles[profileIndex] = profiles[profileIndex].copy(best0to100 = 0)
                    ProfileStorage.saveProfiles(this, profiles)
                }
            }

            StatType.ZERO_TO_200 -> {
                val bestRace = profileRaces
                    .filter { it.time0to200 > 0 }
                    .minByOrNull { it.time0to200 }

                bestRace?.let { race ->
                    val index = allRaces.indexOfFirst { it.id == race.id }
                    if (index != -1) {
                        allRaces[index] = race.copy(time0to200 = 0)
                        RouteStorage.saveRaces(this, allRaces)
                    }
                }

                val profiles = ProfileStorage.loadProfiles(this).toMutableList()
                val profileIndex = profiles.indexOfFirst { it.id == profileId }
                if (profileIndex != -1) {
                    profiles[profileIndex] = profiles[profileIndex].copy(best0to200 = 0)
                    ProfileStorage.saveProfiles(this, profiles)
                }
            }

            StatType.HUNDRED_TO_200 -> {
                val bestRace = profileRaces
                    .filter { it.time100to200 > 0 }
                    .minByOrNull { it.time100to200 }

                bestRace?.let { race ->
                    val index = allRaces.indexOfFirst { it.id == race.id }
                    if (index != -1) {
                        allRaces[index] = race.copy(time100to200 = 0)
                        RouteStorage.saveRaces(this, allRaces)
                    }
                }
            }

            StatType.MAX_SPEED -> {

                val maxSpeed = profileRaces.maxOfOrNull { it.maxSpeed } ?: 0f

                profileRaces.forEach { race ->
                    if (race.maxSpeed == maxSpeed) {
                        val index = allRaces.indexOfFirst { it.id == race.id }
                        if (index != -1) {
                            allRaces[index] = race.copy(maxSpeed = 0f)
                        }
                    }
                }
                RouteStorage.saveRaces(this, allRaces)

                val profiles = ProfileStorage.loadProfiles(this).toMutableList()
                val profileIndex = profiles.indexOfFirst { it.id == profileId }
                if (profileIndex != -1) {
                    profiles[profileIndex] = profiles[profileIndex].copy(maxSpeed = 0f)
                    ProfileStorage.saveProfiles(this, profiles)
                }
            }
            StatType.ZERO_TO_402 -> {
                // Проверяваме драг сесиите за най-доброто 0-402 време
                val allDragSessions = DragStorage.loadDragSessions(this).toMutableList()
                val profileDragSessions = allDragSessions.filter { it.profileId == profileId }

                val bestDragSession = profileDragSessions
                    .filter { it.best0to402 > 0 }
                    .minByOrNull { it.best0to402 }

                bestDragSession?.let { session ->
                    val index = allDragSessions.indexOfFirst { it.id == session.id }
                    if (index != -1) {
                        allDragSessions[index] = session.copy(best0to402 = -1L)
                        DragStorage.saveDragSessions(this, allDragSessions)
                    }
                }
            }
        }
    }

    private fun getBestTimesFromAllRaces(profileId: Long): BestTimes {

        val allRaces = RouteStorage.loadRaces(this)
        val profileRaces = allRaces.filter { it.profileId == profileId }

        val allDragSessions = DragStorage.loadDragSessions(this)
        val profileDragSessions = allDragSessions.filter { it.profileId == profileId }

        var best0to100 = Long.MAX_VALUE
        var best0to200 = Long.MAX_VALUE
        var best100to200 = Long.MAX_VALUE
        var maxSpeed = 0f
        var best0to402 = Long.MAX_VALUE

        profileRaces.forEach { race ->
            if (race.time0to100 > 0 && race.time0to100 < best0to100) best0to100 = race.time0to100
            if (race.time0to200 > 0 && race.time0to200 < best0to200) best0to200 = race.time0to200
            if (race.time100to200 > 0 && race.time100to200 < best100to200) best100to200 = race.time100to200
            if (race.maxSpeed > maxSpeed) maxSpeed = race.maxSpeed
        }

        profileDragSessions.forEach { session ->
            if (session.best0to100 > 0 && session.best0to100 < best0to100) best0to100 = session.best0to100
            if (session.best0to200 > 0 && session.best0to200 < best0to200) best0to200 = session.best0to200
            if (session.best100to200 > 0 && session.best100to200 < best100to200) best100to200 = session.best100to200
            if (session.best0to402 > 0 && session.best0to402 < best0to402) best0to402 = session.best0to402

            session.attempts.forEach { attempt ->
                if (attempt.maxSpeed > maxSpeed) maxSpeed = attempt.maxSpeed
            }
        }

        return BestTimes(
            best0to100 = if (best0to100 == Long.MAX_VALUE) 0L else best0to100,
            best0to200 = if (best0to200 == Long.MAX_VALUE) 0L else best0to200,
            best100to200 = if (best100to200 == Long.MAX_VALUE) 0L else best100to200,
            maxSpeed = maxSpeed,
            best0to402 = if (best0to402 == Long.MAX_VALUE) 0L else best0to402
        )
    }

    private fun formatBestTime(nanos: Long): String =
        if (nanos > 0) String.format("%.3f", nanos / 1_000_000_000.0) else "--"

    data class BestTimes(
        val best0to100: Long,
        val best0to200: Long,
        val best100to200: Long,
        val best0to402: Long,
        val maxSpeed: Float,

    )

    enum class StatType {
        ZERO_TO_100,
        ZERO_TO_200,
        HUNDRED_TO_200,
        ZERO_TO_402,
        MAX_SPEED
    }

    private fun updateProfileImage(
        imageView: ImageView,
        container: android.widget.FrameLayout,
        originalLayout: android.widget.LinearLayout,
        fadeOverlay: View
    ) {
        // Зареждаме снимка ако има, иначе показваме оригиналния layout
        if (!profile.imagePath.isNullOrEmpty()) {
            val imageFile = File(getExternalFilesDir(null), profile.imagePath)
            if (imageFile.exists()) {
                // Image is already scaled on disk, just load it
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                imageView.setImageBitmap(bitmap)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.imageTintList = null
                
                // Показваме контейнера със снимката
                container.visibility = View.VISIBLE
                // Скриваме оригиналния layout
                originalLayout.visibility = View.GONE
                // Показваме fade градиента
                fadeOverlay.visibility = View.VISIBLE
            } else {
                // Файлът не съществува, показваме оригиналния layout
                showOriginalLayout(container, originalLayout, fadeOverlay)
            }
        } else {
            // Няма снимка, показваме оригиналния layout
            showOriginalLayout(container, originalLayout, fadeOverlay)
        }
    }

    private fun showOriginalLayout(
        container: android.widget.FrameLayout,
        originalLayout: android.widget.LinearLayout,
        fadeOverlay: View
    ) {
        // Скриваме контейнера за снимката
        container.visibility = View.GONE
        // Показваме оригиналния layout
        originalLayout.visibility = View.VISIBLE
        // Скриваме fade градиента
        fadeOverlay.visibility = View.GONE
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }
}