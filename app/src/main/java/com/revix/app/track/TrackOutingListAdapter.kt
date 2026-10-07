package com.revix.app.track

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.revix.app.R
import com.revix.app.settings.UnitsManager
import com.revix.app.utils.LapTimeFormatter

class TrackOutingListAdapter(
    private val items: List<TrackOutingsRepository.TrackOutingListItem>,
    private val onItemClick: (TrackOutingsRepository.TrackOutingListItem) -> Unit,
    private val onDeleteClick: (TrackOutingsRepository.TrackOutingListItem) -> Unit
) : RecyclerView.Adapter<TrackOutingListAdapter.OutingViewHolder>() {

    inner class OutingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvOutingTitle: TextView = itemView.findViewById(R.id.tvOutingTitle)
        val tvOutingPbBadge: TextView = itemView.findViewById(R.id.tvOutingPbBadge)
        val tvOutingRaceBoxBadge: TextView = itemView.findViewById(R.id.tvOutingRaceBoxBadge)
        val tvOutingTopTime: TextView = itemView.findViewById(R.id.tvOutingTopTime)
        val tvOutingDateTime: TextView = itemView.findViewById(R.id.tvOutingDateTime)
        val tvOutingLaps: TextView = itemView.findViewById(R.id.tvOutingLaps)
        val tvOutingBestLap: TextView = itemView.findViewById(R.id.tvOutingBestLap)
        val tvOutingMaxSpeed: TextView = itemView.findViewById(R.id.tvOutingMaxSpeed)
        val tvOutingDuration: TextView = itemView.findViewById(R.id.tvOutingDuration)
        val ivOutingWeather: ImageView = itemView.findViewById(R.id.ivOutingWeather)
        val ivOutingHumidity: ImageView = itemView.findViewById(R.id.ivOutingHumidity)
        val ivOutingWind: ImageView = itemView.findViewById(R.id.ivOutingWind)
        val tvOutingWeatherTemp: TextView = itemView.findViewById(R.id.tvOutingWeatherTemp)
        val tvOutingHumidity: TextView = itemView.findViewById(R.id.tvOutingHumidity)
        val tvOutingWind: TextView = itemView.findViewById(R.id.tvOutingWind)
        val btnDeleteOuting: ImageButton = itemView.findViewById(R.id.btnDeleteOuting)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OutingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.outing_item_template, parent, false)
        return OutingViewHolder(view)
    }

    override fun onBindViewHolder(holder: OutingViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val sharedPrefs = context.getSharedPreferences("track_outings", Context.MODE_PRIVATE)
        val sessionId = item.sessionIdFull
        val outingNumber = item.outingNumber

        holder.tvOutingTitle.text = context.getString(R.string.track_session_title, outingNumber)

        val outingDate = sharedPrefs.getString("${sessionId}_outing_${outingNumber}_date", "") ?: ""
        val outingTime = sharedPrefs.getString("${sessionId}_outing_${outingNumber}_time", "") ?: ""
        val outingDuration = sharedPrefs.getString("${sessionId}_outing_${outingNumber}_duration", "") ?: ""
        val outingMaxSpeed = sharedPrefs.getString("${sessionId}_outing_${outingNumber}_max_speed", "") ?: ""
        val outingTemperature = sharedPrefs.getString("${sessionId}_outing_${outingNumber}_temperature", "") ?: ""
        val outingHumidityText = sharedPrefs.getString("${sessionId}_outing_${outingNumber}_humidity", "") ?: ""
        val outingWind = sharedPrefs.getString("${sessionId}_outing_${outingNumber}_wind_speed", "") ?: ""
        val outingWeatherIcon = sharedPrefs.getInt("${sessionId}_outing_${outingNumber}_weather_icon", -1)

        holder.tvOutingPbBadge.text = context.getString(R.string.drag_run_indicator_pb)
        holder.tvOutingPbBadge.visibility = if (item.isTrackPb) View.VISIBLE else View.GONE
        com.revix.app.racebox.RaceBoxSessionUi.bindLabel(holder.tvOutingRaceBoxBadge, item.recordedWithRaceBox)
        holder.tvOutingDateTime.text = outingDate.ifBlank { "--" }
        holder.tvOutingTopTime.text = outingTime.ifBlank { "--:--" }
        holder.tvOutingLaps.text = sharedPrefs.getString("${sessionId}_outing_${outingNumber}_laps", "0")
        holder.tvOutingBestLap.text = TrackOutingsRepository.formatDurationDisplay(
            sharedPrefs.getString("${sessionId}_outing_${outingNumber}_best_lap", LapTimeFormatter.PLACEHOLDER)
                ?: LapTimeFormatter.PLACEHOLDER
        )
        holder.tvOutingDuration.text = TrackOutingsRepository.formatDurationDisplay(outingDuration)
        holder.tvOutingMaxSpeed.text = TrackOutingsRepository.formatSpeedDisplayWhole(context, outingMaxSpeed)

        val weatherTemp = UnitsManager.formatStoredTemperature(outingTemperature, context)
        val humidityText = outingHumidityText.ifBlank { "--%" }
        val windText = UnitsManager.formatStoredSpeed(outingWind, context)
        val humidityPercent = outingHumidityText.filter { it.isDigit() }.toIntOrNull()
        val (weatherIconRes, weatherTint) = TrackOutingsRepository.resolveWeatherIconStyle(
            context,
            outingWeatherIcon,
            humidityPercent
        )

        holder.ivOutingWeather.setImageResource(weatherIconRes)
        holder.ivOutingWeather.setColorFilter(weatherTint)
        holder.ivOutingHumidity.setImageResource(R.drawable.ic_humidity_drop)
        holder.ivOutingHumidity.setColorFilter(ContextCompat.getColor(context, R.color.text_tertiary))
        holder.ivOutingWind.setImageResource(R.drawable.ic_wind)
        holder.ivOutingWind.setColorFilter(ContextCompat.getColor(context, R.color.text_tertiary))
        holder.tvOutingWeatherTemp.text = weatherTemp
        holder.tvOutingHumidity.text = humidityText
        holder.tvOutingWind.text = windText

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.btnDeleteOuting.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
