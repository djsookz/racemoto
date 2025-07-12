package com.example.clinometer

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class CountdownActivity : AppCompatActivity() {

    private var isCountingDown = true
    private lateinit var selectedProfile: Profile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_countdown)

        // Вземане на избрания профил от StartActivity
        selectedProfile = intent.getSerializableExtra("SELECTED_PROFILE") as? Profile
            ?: Profile(name = "Моят профил", vehicleType = Profile.VehicleType.MOTORCYCLE)

        val countdownText = findViewById<TextView>(R.id.countdownText)

        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                countdownText.text = "$secondsLeft"
            }

            override fun onFinish() {
                isCountingDown = false

                // Стартиране на услугата
                val serviceIntent = Intent(this@CountdownActivity, ForegroundService::class.java)
                ContextCompat.startForegroundService(this@CountdownActivity, serviceIntent)

                // Преминаване към MainActivity с избрания профил
                val mainIntent = Intent(this@CountdownActivity, MainActivity::class.java).apply {
                    putExtra("SELECTED_PROFILE", selectedProfile)
                }
                startActivity(mainIntent)
                finish()
            }
        }.start()
    }

    override fun onBackPressed() {
        if (!isCountingDown) {
            super.onBackPressed()
        }
    }
}