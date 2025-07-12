package com.example.clinometer

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.PreferenceManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var switchAlwaysOn: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        switchAlwaysOn = findViewById(R.id.switchAlwaysOn)

        // Зареждане на съхранената настройка
        switchAlwaysOn.isChecked = prefs.getBoolean("always_on_display", false)

        // Слушател за промени
        switchAlwaysOn.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("always_on_display", isChecked).apply()
        }
    }

    fun toggleAlwaysOnDisplay(view: View) {
        switchAlwaysOn.isChecked = !switchAlwaysOn.isChecked
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}