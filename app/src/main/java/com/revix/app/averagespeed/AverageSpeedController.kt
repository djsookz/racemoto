package com.revix.app.averagespeed

import android.content.Context
import android.location.Location
import android.view.View
import com.mapbox.geojson.LineString
import com.mapbox.maps.Style

class AverageSpeedController(
    context: Context,
    private val progressView: AverageSpeedProgressView,
    private val sessionSignView: AverageSpeedSignView?,
    private val idleSignView: AverageSpeedSignView?
) {
    private val appContext = context.applicationContext
    private val tracker = AverageSpeedRuntime.tracker
    private val mapLayer = AverageSpeedMapLayer()
    private val announcer = AverageSpeedAnnouncer(appContext)
    private val catalogListener: (List<AverageSpeedSection>) -> Unit = { sections ->
        tracker.setSections(sections)
        mapLayer.showCatalog(sections)
    }
    private var lastBarVisible = false
    private var voiceEnabled = false
    private var sessionHudActive = false

    init {
        AverageSpeedCatalog.ensureLoaded(appContext)
        tracker.setSections(AverageSpeedCatalog.sections())
        AverageSpeedCatalog.addListener(catalogListener)
        render(AverageSpeedRuntime.lastState, announce = false)
    }

    fun attachStyle(style: Style) {
        mapLayer.attach(style)
        mapLayer.showCatalog(AverageSpeedCatalog.sections())
    }

    fun setSessionHudActive(active: Boolean) {
        sessionHudActive = active
        if (active) {
            idleSignView?.hideSign()
        } else {
            sessionSignView?.hideSign()
            hideBar()
        }
        render(AverageSpeedRuntime.lastState, announce = false)
    }

    fun setGuidedRoute(line: LineString?, active: Boolean, voiceEnabled: Boolean = false) {
        this.voiceEnabled = active && voiceEnabled
        tracker.setGuidedRoute(line, active, this.voiceEnabled)
        if (!active) {
            hideBar()
        }
    }

    fun onLocation(location: Location) {
        val state = tracker.update(location)
        AverageSpeedRuntime.publish(state)
        render(state, announce = true)
    }

    fun release() {
        AverageSpeedCatalog.removeListener(catalogListener)
        mapLayer.detach()
        hideBar()
        hideSign()
    }

    private fun render(state: AverageSpeedUiState, announce: Boolean) {
        if (state.showProgressBar && sessionHudActive) {
            progressView.progress = state.progress01
            if (!lastBarVisible) {
                progressView.visibility = View.VISIBLE
                lastBarVisible = true
            }
        } else {
            hideBar()
        }
        val targetSign = if (sessionHudActive) sessionSignView else idleSignView
        val otherSign = if (sessionHudActive) idleSignView else sessionSignView
        otherSign?.hideSign()
        if (state.showSign && targetSign != null) {
            if (state.signApproachMeters > 0) {
                targetSign.showApproach(state.signApproachMeters, state.speedLimitKmh)
            } else {
                targetSign.showInside(state.speedLimitKmh, state.instantAverageKmh)
            }
        } else {
            targetSign?.hideSign()
        }
        if (announce && voiceEnabled) {
            state.voice?.let { event ->
                announcer.speak(event)
            }
        }
    }

    private fun hideBar() {
        if (!lastBarVisible) return
        progressView.visibility = View.GONE
        lastBarVisible = false
    }

    private fun hideSign() {
        sessionSignView?.hideSign()
        idleSignView?.hideSign()
    }
}
