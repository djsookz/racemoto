package com.revix.app.main.map

import android.util.Log
import com.mapbox.bindgen.Value
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.layers.properties.generated.Visibility

/**
 * Toggles Studio country-wide traffic overlay layers (`traffic` / mapbox-traffic-v1).
 *
 * Must NOT touch Navigation SDK route-line layers such as
 * `mapbox-layerGroup-*-traffic` — those carry congestion colors on the orange route.
 */
object MapCountryTrafficLayer {
    private const val TAG = "MapCountryTrafficLayer"
    /** Layer id as named in Mapbox Studio for the country traffic overlay. */
    private const val STUDIO_TRAFFIC_LAYER_ID = "traffic"

    /** Navigation SDK route-line traffic layers — must stay visible for congestion colors. */
    private val NAV_SDK_ROUTE_TRAFFIC_LAYER_IDS = setOf(
        "mapbox-layerGroup-1-traffic",
        "mapbox-layerGroup-2-traffic",
        "mapbox-layerGroup-3-traffic",
        "mapbox-masking-layer-traffic"
    )

    fun setVisible(style: Style, visible: Boolean) {
        // Prefer explicit Studio id; also hide any leftover traffic-* overlay names.
        val candidates = linkedSetOf(STUDIO_TRAFFIC_LAYER_ID)
        try {
            style.styleLayers.forEach { info ->
                val id = info.id
                if (id in NAV_SDK_ROUTE_TRAFFIC_LAYER_IDS) return@forEach
                if (id.equals("traffic", ignoreCase = true) ||
                    id.startsWith("traffic-", ignoreCase = true) ||
                    id.contains("mapbox-traffic", ignoreCase = true)
                ) {
                    candidates.add(id)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed enumerating style layers", e)
        }

        var found = false
        for (id in candidates) {
            if (style.styleLayerExists(id)) {
                setLayerVisibility(style, id, visible)
                found = true
            }
        }
        if (!found && !visible) {
            Log.d(TAG, "No Studio traffic overlay layer found to hide")
        }
        // Undo any previous mistaken hide of Nav SDK congestion layers.
        restoreNavigationRouteTrafficLayers(style)
    }

    private fun restoreNavigationRouteTrafficLayers(style: Style) {
        for (id in NAV_SDK_ROUTE_TRAFFIC_LAYER_IDS) {
            if (style.styleLayerExists(id)) {
                setLayerVisibility(style, id, visible = true)
            }
        }
    }

    private fun setLayerVisibility(style: Style, layerId: String, visible: Boolean) {
        val visibility = if (visible) Visibility.VISIBLE else Visibility.NONE
        try {
            val layer = style.getLayer(layerId)
            if (layer != null) {
                layer.visibility(visibility)
                Log.d(TAG, "Set layer '$layerId' visibility=$visible")
                return
            }
            val result = style.setStyleLayerProperty(
                layerId,
                "visibility",
                Value.valueOf(if (visible) "visible" else "none")
            )
            if (result.isError) {
                Log.w(TAG, "Failed visibility=$visible on '$layerId': ${result.error}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set '$layerId' visibility=$visible", e)
        }
    }
}
