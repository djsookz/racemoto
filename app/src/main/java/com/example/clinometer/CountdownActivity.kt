package com.example.clinometer

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class CountdownActivity : AppCompatActivity() {

    private var isCountingDown = true
    private var hasFinished = false
    private lateinit var selectedProfile: Profile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_countdown)

        // Позволяваме свободно въртене, но обработваме промените без рестартиране

        // Вземане на избрания профил от StartActivity
        selectedProfile = intent.getSerializableExtra("SELECTED_PROFILE") as? Profile
            ?: Profile(name = "Моят профил", vehicleType = Profile.VehicleType.MOTORCYCLE)

        // Ако активността се рестартира, не започваме отново броенето
        if (savedInstanceState != null) {
            return
        }

        val countdownText = findViewById<TextView>(R.id.countdownText)

        // Стартиране на услугата ВЕДНАГА за да започне събирането на GPS данни
        // но с флаг че е в режим "pre-warming" - само GPS, без хронометър
        val serviceIntent = Intent(this, ForegroundService::class.java).apply {
            putExtra("PRE_WARMING_MODE", true)
        }
        ContextCompat.startForegroundService(this, serviceIntent)

        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                countdownText.text = "$secondsLeft"
            }

            override fun onFinish() {
                if (hasFinished) return // Предотвратяваме множествено изпълнение
                
                isCountingDown = false
                hasFinished = true

                // Сигнализираме на услугата че вече може да премине в нормален режим
                val activateIntent = Intent(this@CountdownActivity, ForegroundService::class.java).apply {
                    putExtra("ACTIVATE_NORMAL_MODE", true)
                }
                startService(activateIntent)

                // Преминаване към MainActivity с избрания профил
                val mainIntent = Intent(this@CountdownActivity, MainActivity::class.java).apply {
                    putExtra("SELECTED_PROFILE", selectedProfile)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(mainIntent)
                finish()
            }
        }.start()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Не правим нищо - просто позволяваме ориентацията да се променя без рестартиране
        // Броенето продължава нормално
    }

    override fun onBackPressed() {
        if (!isCountingDown) {
            super.onBackPressed()
        }
    }
}