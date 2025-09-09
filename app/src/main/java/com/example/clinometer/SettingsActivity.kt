package com.example.clinometer

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.commit
import androidx.preference.PreferenceManager

class SettingsActivity : BaseActivity() {
    override fun getLayoutResourceId(): Int = R.layout.activity_settings
    override fun getNavigationItemId(): Int = R.id.navOptions

    private lateinit var prefs: SharedPreferences
    private lateinit var switchAlwaysOn: SwitchCompat
    private var backPressedTime: Long = 0
    private lateinit var backToast: Toast

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        switchAlwaysOn = findViewById(R.id.switchAlwaysOn)
        switchAlwaysOn.isChecked = prefs.getBoolean("always_on_display", false)
        switchAlwaysOn.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("always_on_display", isChecked).apply()
        }
    }

    fun toggleAlwaysOnDisplay(view: View) {
        switchAlwaysOn.isChecked = !switchAlwaysOn.isChecked
    }

    override fun onBackPressed() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            backToast.cancel()
            super.onBackPressed()
            return
        } else {
            backToast = Toast.makeText(baseContext, "Натиснете отново за изход", Toast.LENGTH_SHORT)
            backToast.show()
        }
        backPressedTime = System.currentTimeMillis()
    }
}