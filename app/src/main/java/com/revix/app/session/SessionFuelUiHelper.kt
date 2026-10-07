package com.revix.app.session

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.revix.app.R
import com.revix.app.data.SessionFuelStorage
import com.revix.app.data.SessionFuelStop
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SessionFuelUiHelper {

    fun bindFuelStopsSection(
        context: Context,
        sectionContainer: View?,
        entriesContainer: LinearLayout?,
        raceId: Long
    ) {
        if (sectionContainer == null || entriesContainer == null) return

        val stops = SessionFuelStorage.getStopsForRace(context, raceId)
        if (stops.isEmpty()) {
            sectionContainer.visibility = View.GONE
            entriesContainer.removeAllViews()
            return
        }

        sectionContainer.visibility = View.VISIBLE
        entriesContainer.removeAllViews()
        val inflater = LayoutInflater.from(context)
        stops.forEach { stop ->
            val itemView = inflater.inflate(R.layout.item_session_fuel_stop, entriesContainer, false)
            bindStopItem(context, itemView, stop)
            entriesContainer.addView(itemView)
        }
    }

    private fun bindStopItem(context: Context, itemView: View, stop: SessionFuelStop) {
        val stationLabel = stop.station.trim().ifBlank {
            context.getString(R.string.session_fuel_stop_unknown_station)
        }
        itemView.findViewById<TextView>(R.id.tvSessionFuelStopTitle).text = stationLabel
        itemView.findViewById<TextView>(R.id.tvSessionFuelStopAmount).text = context.getString(
            R.string.session_fuel_stop_litres_format,
            formatLitres(stop.litres)
        )
        val priceView = itemView.findViewById<TextView>(R.id.tvSessionFuelStopPrice)
        if (stop.totalAmount > 0.0) {
            priceView.visibility = View.VISIBLE
            priceView.text = context.getString(
                R.string.session_fuel_stop_price_format,
                formatPrice(stop.totalAmount)
            )
        } else {
            priceView.visibility = View.GONE
        }
        val pricePerLitreView = itemView.findViewById<TextView>(R.id.tvSessionFuelStopPricePerLitre)
        resolvePricePerLitre(stop)?.let { pricePerLitre ->
            pricePerLitreView.visibility = View.VISIBLE
            pricePerLitreView.text = context.getString(
                R.string.session_fuel_stop_price_per_litre_format,
                formatPrice(pricePerLitre)
            )
        } ?: run {
            pricePerLitreView.visibility = View.GONE
        }
        itemView.findViewById<TextView>(R.id.tvSessionFuelStopMeta).text = buildMeta(context, stop)
    }

    private fun buildMeta(context: Context, stop: SessionFuelStop): String {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(stop.timestamp))
        val location = if (stop.latitude != null && stop.longitude != null) {
            context.getString(
                R.string.session_fuel_stop_location_format,
                formatCoordinate(stop.latitude),
                formatCoordinate(stop.longitude)
            )
        } else {
            context.getString(R.string.session_fuel_stop_location_unknown)
        }
        return context.getString(R.string.session_fuel_stop_meta_format, time, location)
    }

    private fun formatLitres(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    private fun formatPrice(value: Double): String =
        String.format(Locale.US, "%.2f", value.coerceAtLeast(0.0))

    private fun resolvePricePerLitre(stop: SessionFuelStop): Double? {
        if (stop.pricePerLitre > 0.0) return stop.pricePerLitre
        if (stop.totalAmount > 0.0 && stop.litres > 0.0) {
            return stop.totalAmount / stop.litres
        }
        return null
    }

    private fun formatCoordinate(value: Double): String =
        String.format(Locale.US, "%.5f", value)
}
