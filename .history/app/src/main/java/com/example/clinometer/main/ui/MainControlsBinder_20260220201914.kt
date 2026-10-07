package com.example.clinometer.main.ui

import android.view.View
import android.widget.ImageButton

object MainControlsBinder {

    fun bindSessionControls(
        resetButton: View?,
        resetButtonOverlay: View?,
        zeroButton: View?,
        zeroButtonOverlay: View?,
        stopButton: View?,
        stopButtonOverlay: View?,
        onReset: () -> Unit,
        onZero: () -> Unit,
        onStop: () -> Unit
    ) {
        val resetClickListener = View.OnClickListener { onReset() }
        resetButton?.setOnClickListener(resetClickListener)
        resetButtonOverlay?.setOnClickListener(resetClickListener)

        val zeroClickListener = View.OnClickListener { onZero() }
        zeroButton?.setOnClickListener(zeroClickListener)
        zeroButtonOverlay?.setOnClickListener(zeroClickListener)

        val stopClickListener = View.OnClickListener { onStop() }
        stopButton?.setOnClickListener(stopClickListener)
        stopButtonOverlay?.setOnClickListener(stopClickListener)
    }

    fun bindNavigationCameraButtons(
        isNavigationActive: Boolean,
        hasNavigationCamera: Boolean,
        btnOverview: ImageButton?,
        btnRecenter: ImageButton?,
        cameraNorthModeButton: ImageButton?,
        onRecenter: () -> Unit,
        onOverview: () -> Unit
    ) {
        if (isNavigationActive && hasNavigationCamera) {
            btnOverview?.visibility = View.VISIBLE
            btnRecenter?.visibility = View.VISIBLE
            cameraNorthModeButton?.visibility = View.GONE

            btnRecenter?.setOnClickListener { onRecenter() }
            btnOverview?.setOnClickListener { onOverview() }
            return
        }

        btnOverview?.visibility = View.GONE
        btnRecenter?.visibility = View.GONE
        cameraNorthModeButton?.visibility = View.VISIBLE
    }
}
