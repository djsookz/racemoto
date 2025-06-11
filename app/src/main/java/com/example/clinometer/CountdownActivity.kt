package com.example.clinometer

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CountdownActivity : AppCompatActivity() {
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
                startActivity(Intent(this@CountdownActivity, MainActivity::class.java))
                finish()
            }
        }.start()
    }
}
