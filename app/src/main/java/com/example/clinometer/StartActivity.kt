package com.example.clinometer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class StartActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startActivity(Intent(this, CountdownActivity::class.java))

        }
        findViewById<Button>(R.id.btnRaces).setOnClickListener {
            startActivity(Intent(this, RacesActivity::class.java))
        }

    }
    override fun onBackPressed() {
        super.onBackPressed()
    }
}
