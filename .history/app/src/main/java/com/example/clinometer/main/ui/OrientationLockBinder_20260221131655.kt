package com.example.clinometer.main.ui

import android.content.pm.ActivityInfo
import android.widget.ImageButton
import com.example.clinometer.R

object OrientationLockBinder {

    fun apply(
        setRequestedOrientation: (Int) -> Unit,
        orientationToggle: ImageButton?,
        locked: Boolean
    ) {
        if (locked) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED)
            orientationToggle?.setImageResource(R.drawable.ic_lock)
            orientationToggle?.imageAlpha = (255 * 0.95).toInt()
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
            orientationToggle?.setImageResource(R.drawable.ic_unlock)
            orientationToggle?.imageAlpha = (255 * 0.5).toInt()
        }
    }
}
