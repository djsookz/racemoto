package com.example.clinometer  // промени ако твоят пакет е различен

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.example.clinometer.databinding.CustomTripProgressViewBinding
import com.mapbox.navigation.base.trip.model.RouteProgress
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CustomTripProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = CustomTripProgressViewBinding.inflate(
        LayoutInflater.from(context),
        this,
        true
    )

    fun update(routeProgress: RouteProgress) {
        val distanceRemaining = routeProgress.distanceRemaining ?: 0f
        val durationRemainingSeconds = (routeProgress.durationRemaining ?: 0.0).toLong()

        // Remaining distance – цяло число km (без десетична)
        val distanceKm = (distanceRemaining / 1000f).toInt()
        binding.tvTripRemainingDistance.text = distanceKm.coerceAtLeast(0).toString()

        // Remaining time – само часове и минути, без секунди
        val totalMinutes = (durationRemainingSeconds / 60).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        val timeText = if (hours > 0) {
            "${hours}ч ${minutes}м"
        } else {
            "${minutes}м"
        }
        binding.tvTripRemainingTime.text = timeText

        // ETA – HH:mm
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.SECOND, durationRemainingSeconds.toInt())
        val etaFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.tvTripEta.text = etaFormatter.format(calendar.time)
    }

    fun reset() {
        binding.tvTripEta.text = "--:--"
        binding.tvTripRemainingTime.text = "--:--"
        binding.tvTripRemainingDistance.text = "--"
    }
}