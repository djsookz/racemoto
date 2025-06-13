package com.example.clinometer

import android.app.AlertDialog
import android.text.InputType
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RaceAdapter(
    private val races: MutableList<Race>,
    private val onItemClick: (Race) -> Unit,
    private val onDeleteClick: (Race) -> Unit,
    private val onRename: (Race, String) -> Unit
) : RecyclerView.Adapter<RaceAdapter.RaceViewHolder>() {

    inner class RaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView     = itemView.findViewById(R.id.tvDate)
        val dateTextView: TextView  = itemView.findViewById(R.id.dateTextView)
        val tvDuration: TextView  = itemView.findViewById(R.id.tvNumber)
        val btnOptions: ImageButton = itemView.findViewById(R.id.btnOptions)
    }

    companion object {
        private const val MENU_RENAME = 1
        private const val MENU_DELETE = 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_race, parent, false)
        return RaceViewHolder(view)
    }



    override fun onBindViewHolder(holder: RaceViewHolder, position: Int) {
        val race = races[position]


        fun formatRelativeDate(timestamp: Long): String {
            val now = java.util.Calendar.getInstance()
            val then = java.util.Calendar.getInstance().apply {
                timeInMillis = timestamp
            }

            val nowYear = now.get(java.util.Calendar.YEAR)
            val nowDayOfYear = now.get(java.util.Calendar.DAY_OF_YEAR)
            val thenYear = then.get(java.util.Calendar.YEAR)
            val thenDayOfYear = then.get(java.util.Calendar.DAY_OF_YEAR)

            // Проверка дали е днес
            if (nowYear == thenYear && nowDayOfYear == thenDayOfYear) {
                return "Днес"
            }

            // Проверка дали е вчера
            if (nowYear == thenYear && nowDayOfYear - 1 == thenDayOfYear) {
                return "Вчера"
            }

            // Разлика в дни
            val diffInMillis = now.timeInMillis - then.timeInMillis
            val days = (diffInMillis / (1000 * 60 * 60 * 24)).toInt()

            // Разлика в месеци и години
            val months = (days / 30)
            val years = (days / 365)

            return when {
                years >= 1 -> {
                    if (years == 1) "1 година" else "$years години"
                }
                months >= 1 -> {
                    if (months == 1) "1 месец" else "$months месеца"
                }
                else -> "$days дни"
            }
        }


        // Заглавие: името на маршрута, по default "Маршрут X"
        holder.tvTitle.text = race.name ?: "Сесия ${position + 1}"

        // Дата на създаване
        holder.dateTextView.text = formatRelativeDate(race.absoluteTimestamp)



        // Продължителност
        holder.tvDuration.text = formatTime(race.duration)

        // Цял елемент кликаем за преглед
        holder.itemView.setOnClickListener {
            onItemClick(race)
        }

        // Меню с 3 точки
        holder.btnOptions.setOnClickListener { view ->
            PopupMenu(view.context, view).apply {
                menu.add(0, MENU_RENAME, 0, "Преименувай")
                menu.add(0, MENU_DELETE, 1, "Изтрий")
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        MENU_RENAME -> {
                            showRenameDialog(holder, race)
                            true
                        }
                        MENU_DELETE -> {
                            onDeleteClick(race)
                            val pos = holder.bindingAdapterPosition
                            if (pos != RecyclerView.NO_POSITION) {
                                races.removeAt(pos)
                                notifyItemRemoved(pos)
                            }
                            true
                        }
                        else -> false
                    }
                }
            }.show()
        }
    }

    override fun getItemCount(): Int = races.size

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours   = millis / (1000 * 60 * 60)
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun showRenameDialog(holder: RaceViewHolder, race: Race) {
        val input = EditText(holder.itemView.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(race.name ?: "")
            setSelection(text.length)
        }
        AlertDialog.Builder(holder.itemView.context)
            .setTitle("Преименуване на сесията")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    race.name = newName
                    onRename(race, newName)
                    notifyItemChanged(holder.bindingAdapterPosition)
                }
            }
            .setNegativeButton("Отказ") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
