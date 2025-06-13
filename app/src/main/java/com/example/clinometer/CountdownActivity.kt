package com.example.clinometer

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity



class CountdownActivity : AppCompatActivity() {

    private var isCountingDown  = true
    private lateinit var countdownTimer: CountDownTimer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_countdown)

        val countdownText = findViewById<TextView>(R.id.countdownText)

        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                countdownText.text = "$secondsLeft"
            }

            override fun onFinish() {
                isCountingDown = false
                startActivity(Intent(this@CountdownActivity, MainActivity::class.java))
                finish()
            }
        }.start()
    }

    override fun onBackPressed() {
        if (isCountingDown) {
            // блокиране на бутона назад по време на броене
        } else {
            super.onBackPressed()
        }
    }
}
