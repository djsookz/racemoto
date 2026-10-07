package com.revix.app.track

import android.content.Intent
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.revix.app.GeoPoint
import com.revix.app.R
import com.revix.app.Race
import com.revix.app.RoutePoint
import com.revix.app.main.map.MapActivity
import com.github.mikephil.charting.charts.LineChart
import com.mapbox.geojson.FeatureCollection
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager

class TrackMapIntegration private constructor(
    private val context: TrackMapIntentContext
) {
    private val routeRenderer = TrackLapMapboxRouteRenderer()

    companion object {
        fun fromIntent(intent: Intent, defaultIsMotorcycle: Boolean): TrackMapIntegration {
            val context = TrackMapNavigator.parseIntent(intent, defaultIsMotorcycle)
            return TrackMapIntegration(context)
        }
    }

    fun configureStartButton(
        activity: AppCompatActivity,
        button: Button,
        onFallback: () -> Unit
    ) {
        button.setText(if (context.isTrackContext) R.string.session_new_button else R.string.new_session_button)
        button.setOnClickListener {
            TrackMapNavigator.openSessionOrNewRoute(activity, context, onFallback)
        }
    }

    fun configureTrackUi(
        activity: MapActivity,
        chart: LineChart,
        raceId: Long,
        race: Race?
    ) {
        TrackMapNavigator.setupTrackLapNavigation(
            activity = activity,
            state = TrackLapNavState(
                lapNumber = context.lapNumber,
                outingNumber = context.outingNumber,
                totalLaps = -1,
                isPointToPoint = context.isPointToPoint
            ),
            context = context,
            raceId = raceId,
            race = race
        )

        TrackMapNavigator.applyTrackLapLayoutAdjustments(activity, chart, context.isTrackContext)
    }

    fun showFullRouteIfTrack(
        style: Style?,
        polyManager: PolylineAnnotationManager?,
        routePoints: List<RoutePoint>,
        routeSourceId: String,
        routeLayerId: String = "",
        longitudinalGByRouteIndex: FloatArray? = null
    ): Boolean {
        if (!context.isTrackContext || routePoints.size <= 1) return false

        routeRenderer.showFullRoute(polyManager, routePoints, longitudinalGByRouteIndex)

        try {
            val source = style?.getSourceAs<GeoJsonSource>(routeSourceId)
            source?.featureCollection(FeatureCollection.fromFeatures(emptyList()))
        } catch (_: Exception) {
        }
        if (routeLayerId.isNotBlank()) {
            try {
                style?.setStyleLayerProperty(
                    routeLayerId,
                    "visibility",
                    com.mapbox.bindgen.Value.valueOf("none")
                )
            } catch (_: Exception) {
            }
        }

        return true
    }

    fun handleReaderUpdateIfTrack(
        hasUserInteracted: Boolean,
        index: Int,
        interpolatedPoint: GeoPoint,
        polyManager: PolylineAnnotationManager?,
        routePoints: List<RoutePoint>,
        followCamera: Boolean,
        updateMarker: (GeoPoint, Boolean) -> Unit
    ): Boolean? {
        if (!context.isTrackContext) return null

        updateMarker(interpolatedPoint, followCamera)

        if (hasUserInteracted) {
            routeRenderer.drawUpToIndex(polyManager, routePoints, index, interpolatedPoint)
            return true
        }

        return false
    }

    fun clear() {
        routeRenderer.clear()
    }
}
