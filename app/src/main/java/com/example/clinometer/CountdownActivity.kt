package com.example.clinometer

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class CountdownActivity : AppCompatActivity() {

    private var isCountingDown = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_countdown)

        val countdownText = findViewById<TextView>(R.id.countdownText)

        // Коригиране: Премахване на override от вътрешния клас
        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                countdownText.text = "$secondsLeft"
            }

            // Коригиране: Правилно заместване на метода
            override fun onFinish() {
                isCountingDown = false
                val serviceIntent = Intent(this@CountdownActivity, ForegroundService::class.java)
                ContextCompat.startForegroundService(this@CountdownActivity, serviceIntent)

                startActivity(Intent(this@CountdownActivity, MainActivity::class.java))
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