package com.example.clinometer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.clinometer.drag.DragRunPageActivity
import android.graphics.PorterDuff
import androidx.core.widget.ImageViewCompat
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import android.view.WindowManager
import androidx.preference.PreferenceManager
import com.example.clinometer.settings.LanguageManager

abstract class BaseActivity : AppCompatActivity() {

    private fun isWhiteIcon(itemId: Int): Boolean {
        return itemId == R.id.navMap || itemId == R.id.navGarage || itemId == R.id.navDrag || itemId == R.id.navTrack
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getLayoutResourceId())
        setupScreenKeepOn()
        setupBottomNavigation()
    }

    private fun setupScreenKeepOn() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        updateScreenKeepOn(prefs.getBoolean("always_on_display", false))

        prefs.registerOnSharedPreferenceChangeListener { shared, key ->
            if (key == "always_on_display") {
                updateScreenKeepOn(shared.getBoolean(key, false))
            }
        }
    }

    private fun updateScreenKeepOn(keepOn: Boolean) {
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    abstract fun getLayoutResourceId(): Int

    abstract fun getNavigationItemId(): Int

    protected fun setupBottomNavigation() {
        val navDrag = findViewById<LinearLayout>(R.id.navDrag)
        val navGarage = findViewById<LinearLayout>(R.id.navGarage)
        val navMap = findViewById<LinearLayout>(R.id.navMap)
        val navTrack = findViewById<LinearLayout>(R.id.navTrack)
        val navOptions = findViewById<LinearLayout>(R.id.navOptions)

        highlightActiveNavItem(getNavigationItemId())

        navDrag?.setOnClickListener {
            if (getNavigationItemId() != R.id.navDrag) {
                animateAndNavigate(it) { navigateToDrag() }
            }
        }

        navGarage?.setOnClickListener {
            if (getNavigationItemId() != R.id.navGarage) {
                animateAndNavigate(it) { navigateToGarage() }
            }
        }

        navMap?.setOnClickListener {
            if (getNavigationItemId() != R.id.navMap) {
                animateAndNavigate(it) { navigateToMap() }
            }
        }

        navTrack?.setOnClickListener {
            if (getNavigationItemId() != R.id.navTrack) {
                animateAndNavigate(it) { navigateToTrack() }
            }
        }

        navOptions?.setOnClickListener {
            if (getNavigationItemId() != R.id.navOptions) {
                animateAndNavigate(it) { navigateToSettings() }
            }
        }
    }

    private fun animateAndNavigate(view: android.view.View, navigation: () -> Unit) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
                navigation()
            }
            .start()
    }

    private fun highlightActiveNavItem(activeItemId: Int) {

        resetNavItemState(R.id.navDrag)
        resetNavItemState(R.id.navGarage)
        resetNavItemState(R.id.navMap)
        resetNavItemState(R.id.navTrack)
        resetNavItemState(R.id.navOptions)
        setNavItemActive(activeItemId)
    }

    private fun resetNavItemState(itemId: Int) {
        val layout = findViewById<LinearLayout>(itemId) ?: return
        val imageView = layout.getChildAt(0) as? ImageView
        val textView = layout.getChildAt(1) as? TextView

        val iconColor = if (isWhiteIcon(itemId)) {
            ContextCompat.getColor(this, android.R.color.white)
        } else {
            ContextCompat.getColor(this, R.color.nav_icon_inactive)
        }
        val tint = ColorStateList.valueOf(iconColor)

        imageView?.let { iv ->
            ImageViewCompat.setImageTintList(iv, tint)
            val mode = if (isWhiteIcon(itemId)) PorterDuff.Mode.SRC_IN else PorterDuff.Mode.MULTIPLY
            ImageViewCompat.setImageTintMode(iv, mode)
            iv.drawable?.mutate()
        }

        textView?.apply {
            setTextColor(ContextCompat.getColor(this@BaseActivity, R.color.nav_text_inactive))
            setTypeface(null, Typeface.NORMAL)
        }
    }

    private fun setNavItemActive(itemId: Int) {
        val layout = findViewById<LinearLayout>(itemId) ?: return
        val imageView = layout.getChildAt(0) as? ImageView
        val textView = layout.getChildAt(1) as? TextView

        val iconColor = ContextCompat.getColor(this, R.color.primary_color)
        val tint = ColorStateList.valueOf(iconColor)

        imageView?.let { iv ->
            ImageViewCompat.setImageTintList(iv, tint)
            val mode = if (isWhiteIcon(itemId)) PorterDuff.Mode.SRC_IN else PorterDuff.Mode.MULTIPLY
            ImageViewCompat.setImageTintMode(iv, mode)
            iv.drawable?.mutate()
        }

        textView?.apply {
            setTextColor(ContextCompat.getColor(this@BaseActivity, R.color.primary_color))
            setTypeface(null, Typeface.BOLD)
        }
    }


    protected open fun navigateToDrag() {
        val intent = Intent(this, DragPageActivity::class.java)
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }

    protected open fun navigateToGarage() {
        val intent = Intent(this, GarageActivity::class.java)
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }

    protected open fun navigateToMap() {
        val intent = Intent(this, MainMapActivity::class.java)
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }

    protected open fun navigateToSessions() {
        val intent = Intent(this, RacesActivity::class.java)
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }

    protected open fun navigateToTrack() {
        val intent = Intent(this, TrackActivity::class.java)
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }

    protected open fun navigateToSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }
}