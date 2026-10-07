package com.revix.app.main.ui

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.revix.app.R as AppR
import com.mapbox.navigation.ui.components.R
import com.mapbox.bindgen.Expected
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.tripdata.shield.model.RouteShieldCallback
import com.mapbox.navigation.tripdata.shield.model.RouteShieldError
import com.mapbox.navigation.tripdata.shield.model.RouteShieldResult
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverPrimaryOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverViewOptions
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverViewState
import kotlinx.coroutines.launch

object ManeuverViewConfigurator {
    private const val SHIELD_CALLBACK_TIMEOUT_MS = 500L

    fun setup(
        owner: LifecycleOwner,
        maneuverView: MapboxManeuverView?,
        isLandscape: () -> Boolean
    ) {
        val view = maneuverView ?: return
        view.upcomingManeuverRenderingEnabled = true
        applyMapboxOptions(view, isLandscape())
        installSubPreviewGuard(view)
        suppressSubPreview(view)
        applyPresentation(view, isLandscape(), expanded = false)

        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                view.maneuverViewState.collect { state ->
                    val expanded = state == MapboxManeuverViewState.EXPANDED
                    applyPresentation(view, isLandscape(), expanded)
                }
            }
        }
    }

    /** Mapbox briefly shows the next-turn sub row on every render; hide it unless expanded list is open. */
    private fun suppressSubPreview(view: MapboxManeuverView) {
        view.updateSubManeuverViewVisibility(View.GONE)
        if (view.maneuverViewState.value != MapboxManeuverViewState.EXPANDED) {
            view.updateUpcomingManeuversVisibility(View.GONE)
        }
    }

    private fun installSubPreviewGuard(view: MapboxManeuverView) {
        view.findViewById<View>(R.id.subManeuverLayout)?.addOnLayoutChangeListener { sub, _, _, _, _, _, _, _, _ ->
            if (view.maneuverViewState.value != MapboxManeuverViewState.EXPANDED &&
                sub.visibility == View.VISIBLE
            ) {
                view.updateSubManeuverViewVisibility(View.GONE)
            }
        }
    }

    fun applyMapboxOptions(view: MapboxManeuverView, isLandscape: Boolean) {
        val primaryStyle = if (isLandscape) {
            AppR.style.RevixManeuverPrimaryTextLand
        } else {
            AppR.style.RevixManeuverPrimaryText
        }
        val distanceStyle = if (isLandscape) {
            AppR.style.RevixManeuverStepDistanceLand
        } else {
            AppR.style.RevixManeuverStepDistance
        }

        view.updateManeuverViewOptions(
            ManeuverViewOptions.Builder()
                .maneuverBackgroundColor(android.R.color.transparent)
                .subManeuverBackgroundColor(android.R.color.transparent)
                .upcomingManeuverBackgroundColor(android.R.color.transparent)
                .stepDistanceTextAppearance(distanceStyle)
                .primaryManeuverOptions(
                    ManeuverPrimaryOptions.Builder()
                        .textAppearance(primaryStyle)
                        .build()
                )
                .build()
        )
    }

    fun renderManeuvers(
        maneuverApi: MapboxManeuverApi,
        maneuverView: MapboxManeuverView?,
        routeProgress: RouteProgress,
        isLandscape: Boolean
    ) {
        val view = maneuverView ?: return
        val orientationFlag = if (isLandscape) 2 else 1
        if (view.getTag(AppR.id.tag_maneuver_layout_tuned) != orientationFlag) {
            applyMapboxOptions(view, isLandscape)
        }

        val maneuversExpected = maneuverApi.getManeuvers(routeProgress)
        view.renderManeuvers(maneuversExpected)
        suppressSubPreview(view)

        var finished = false
        fun finishOnce() {
            if (finished) return
            finished = true
            finishRender(view, isLandscape)
        }

        maneuversExpected.onValue { maneuverList ->
            if (maneuverList.isEmpty()) {
                finishOnce()
                return@onValue
            }

            var shieldsHandled = false
            view.postDelayed({
                if (!shieldsHandled) {
                    finishOnce()
                }
            }, SHIELD_CALLBACK_TIMEOUT_MS)

            maneuverApi.getRoadShields(maneuverList, object : RouteShieldCallback {
                override fun onRoadShields(
                    shields: List<Expected<RouteShieldError, RouteShieldResult>>
                ) {
                    shieldsHandled = true
                    view.post {
                        view.renderManeuverWith(shields)
                        suppressSubPreview(view)
                        finishOnce()
                    }
                }
            })
        }
        if (maneuversExpected.isError) {
            finishOnce()
        }
    }

    fun finishRender(maneuverView: MapboxManeuverView?, isLandscape: Boolean) {
        val view = maneuverView ?: return
        suppressSubPreview(view)
        val expanded = view.maneuverViewState.value == MapboxManeuverViewState.EXPANDED
        applyPresentation(view, isLandscape, expanded)
    }

    private fun applyPresentation(
        view: MapboxManeuverView,
        isLandscape: Boolean,
        expanded: Boolean
    ) {
        suppressSubPreview(view)
        ManeuverUiBinder.applyAfterRender(view, isLandscape)
        if (expanded) {
            view.post { tuneUpcomingListItems(view, isLandscape) }
        }
    }

    private fun tuneUpcomingListItems(view: MapboxManeuverView, isLandscape: Boolean) {
        val recycler = view.findViewById<RecyclerView>(R.id.upcomingManeuverRecycler) ?: return
        for (index in 0 until recycler.childCount) {
            val child = recycler.getChildAt(index) ?: continue
            ManeuverUiBinder.applyAfterRender(child, isLandscape)
        }
    }
}
