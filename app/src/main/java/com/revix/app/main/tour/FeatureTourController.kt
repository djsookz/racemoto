package com.revix.app.main.tour

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.revix.app.R
import com.revix.app.main.MainContainerActivity
import com.revix.app.main.map.MapFragment

class FeatureTourController(
    private val activity: MainContainerActivity
) {
    private var overlay: FeatureTourOverlayView? = null
    private var stepIndex = 0
    private var running = false

    private data class Step(
        val page: Int,
        val targetProvider: () -> View?,
        val titleRes: Int,
        val bodyRes: Int
    )

    private val steps: List<Step> by lazy {
        listOf(
            Step(
                page = MainContainerActivity.PAGE_MAP,
                targetProvider = { activity.findViewById(R.id.navMap) },
                titleRes = R.string.feature_tour_map_nav_title,
                bodyRes = R.string.feature_tour_map_nav_body
            ),
            Step(
                page = MainContainerActivity.PAGE_MAP,
                targetProvider = { mapView(R.id.destinationSearchContainer) },
                titleRes = R.string.feature_tour_search_title,
                bodyRes = R.string.feature_tour_search_body
            ),
            Step(
                page = MainContainerActivity.PAGE_MAP,
                targetProvider = { mapView(R.id.btnStartNavigationNoDestination) },
                titleRes = R.string.feature_tour_start_title,
                bodyRes = R.string.feature_tour_start_body
            ),
            Step(
                page = MainContainerActivity.PAGE_MAP,
                targetProvider = { mapView(R.id.btnSessions) },
                titleRes = R.string.feature_tour_sessions_title,
                bodyRes = R.string.feature_tour_sessions_body
            ),
            Step(
                page = MainContainerActivity.PAGE_MAP,
                targetProvider = { mapView(R.id.fabReport) },
                titleRes = R.string.feature_tour_report_title,
                bodyRes = R.string.feature_tour_report_body
            ),
            Step(
                page = MainContainerActivity.PAGE_DRAG,
                targetProvider = { activity.findViewById(R.id.navDrag) },
                titleRes = R.string.feature_tour_drag_title,
                bodyRes = R.string.feature_tour_drag_body
            ),
            Step(
                page = MainContainerActivity.PAGE_TRACK,
                targetProvider = { activity.findViewById(R.id.navTrack) },
                titleRes = R.string.feature_tour_track_title,
                bodyRes = R.string.feature_tour_track_body
            ),
            Step(
                page = MainContainerActivity.PAGE_GARAGE,
                targetProvider = { activity.findViewById(R.id.navGarage) },
                titleRes = R.string.feature_tour_garage_title,
                bodyRes = R.string.feature_tour_garage_body
            ),
            Step(
                page = MainContainerActivity.PAGE_SETTINGS,
                targetProvider = { activity.findViewById(R.id.navOptions) },
                titleRes = R.string.feature_tour_settings_title,
                bodyRes = R.string.feature_tour_settings_body
            )
        )
    }

    fun startIfNeeded() {
        if (running || !FeatureTourStore.shouldShow(activity)) return
        running = true
        stepIndex = 0
        attachOverlay()
        showStep(0)
    }

    fun isRunning(): Boolean = running

    private fun attachOverlay() {
        if (overlay != null) return
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val view = FeatureTourOverlayView(activity)
        overlay = view
        root.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        view.btnSkip.setOnClickListener { finishTour(goToMap = true) }
        view.btnNext.setOnClickListener { goNext() }
    }

    private fun goNext() {
        if (stepIndex >= steps.lastIndex) {
            finishTour(goToMap = true)
        } else {
            showStep(stepIndex + 1)
        }
    }

    private fun showStep(index: Int) {
        stepIndex = index
        val step = steps[index]
        val overlayView = overlay ?: return

        activity.navigateToPage(step.page)

        overlayView.tvStepCounter.text = activity.getString(
            R.string.feature_tour_step_counter,
            index + 1,
            steps.size
        )
        overlayView.tvTitle.setText(step.titleRes)
        overlayView.tvBody.setText(step.bodyRes)
        overlayView.btnNext.setText(
            if (index == steps.lastIndex) R.string.feature_tour_done else R.string.feature_tour_next
        )
        overlayView.btnSkip.visibility = if (index == steps.lastIndex) View.GONE else View.VISIBLE

        // Wait for page / map views to lay out.
        overlayView.postDelayed({
            if (!running) return@postDelayed
            val target = step.targetProvider()
            overlayView.setHighlight(target)
        }, 280)
    }

    fun cancel() {
        if (!running) return
        finishTour(goToMap = true)
    }

    private fun finishTour(goToMap: Boolean) {
        running = false
        FeatureTourStore.markCompleted(activity)
        overlay?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        overlay = null
        if (goToMap) {
            activity.navigateToPage(MainContainerActivity.PAGE_MAP)
        }
    }

    private fun mapView(id: Int): View? {
        return findMapFragment()?.view?.findViewById(id)
    }

    private fun findMapFragment(): MapFragment? {
        return activity.supportFragmentManager.fragments
            .asSequence()
            .mapNotNull { findMapInHierarchy(it) }
            .firstOrNull()
    }

    private fun findMapInHierarchy(fragment: Fragment): MapFragment? {
        if (fragment is MapFragment) return fragment
        fragment.childFragmentManager.fragments.forEach { child ->
            findMapInHierarchy(child)?.let { return it }
        }
        return null
    }
}
