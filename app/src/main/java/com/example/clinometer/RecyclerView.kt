package com.example.clinometer

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RaceAdapter(
    private val races: MutableList<Race>,
    private val onItemClick: (Race) -> Unit,
    private val onDeleteClick: (Race) -> Unit
) : RecyclerView.Adapter<RaceAdapter.RaceViewHolder>() {

    inner class RaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDate: TextView      = itemView.findViewById(R.id.tvDate)
        val tvDuration: TextView  = itemView.findViewById(R.id.tvDuration)
        val tvNumber: TextView    = itemView.findViewById(R.id.tvNumber) // номер на състезанието
        val btnDelete: Button     = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_race, parent, false)
        return RaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: RaceViewHolder, position: Int) {
        val race = races[position]

        // Форматиране на дата
        holder.tvDate.text = DateFormat.format("dd.MM.yyyy HH:mm", race.timestamp)
        // Форматиране на продължителност
        holder.tvDuration.text = formatTime(race.duration)
        // Показваме пореден номер на състезанието (1-базиран)
        holder.tvNumber.text = (position + 1).toString()

        // Клик върху елемента → отваря съответното състезание
        holder.itemView.setOnClickListener {
            onItemClick(race)
        }

        // Клик върху delete бутона
        holder.btnDelete.setOnClickListener {
            onDeleteClick(race)
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                races.removeAt(pos)
                notifyItemRemoved(pos)
            }
        }
    }

    override fun getItemCount(): Int = races.size

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours   = millis / (1000 * 60 * 60)
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
