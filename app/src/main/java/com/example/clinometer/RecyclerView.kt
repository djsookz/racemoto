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
import java.util.Calendar
import java.util.Date

class RaceAdapter(
    private val races: MutableList<Race>,
    private val onItemClick: (Race) -> Unit,
    private val onDeleteClick: (Race) -> Unit,
    private val onRename: (Race, String) -> Unit
) : RecyclerView.Adapter<RaceAdapter.RaceViewHolder>() {

    companion object {
        private const val MENU_RENAME = 1
        private const val MENU_DELETE = 2
    }

    inner class RaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvDate)
        val dateTextView: TextView = itemView.findViewById(R.id.dateTextView)
        val tvDuration: TextView = itemView.findViewById(R.id.tvNumber)
        val btnOptions: ImageButton = itemView.findViewById(R.id.btnOptions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_race, parent, false)
        return RaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: RaceViewHolder, position: Int) {
        val race = races[position]

        fun formatRelativeDate(timestamp: Long): String {
            val ctx = holder.itemView.context

            // Calculate midnight boundaries
            val nowMidnight = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val thenMidnight = Calendar.getInstance().apply {
                timeInMillis = timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val diffMillis = nowMidnight.timeInMillis - thenMidnight.timeInMillis
            val days = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

            return when {
                days < 0 -> DateFormat.format("dd.MM.yyyy", Date(timestamp)).toString()
                days == 0 -> ctx.getString(R.string.session_today)
                days == 1 -> ctx.getString(R.string.session_yesterday)
                else -> ctx.resources.getQuantityString(R.plurals.session_days, days, days)
            }
        }

        holder.tvTitle.text = race.name
            ?: holder.itemView.context.getString(R.string.session_title, position + 1)
        holder.dateTextView.text = formatRelativeDate(race.absoluteTimestamp)
        holder.tvDuration.text = formatTime(race.duration)

        holder.itemView.setOnClickListener { onItemClick(race) }
        holder.btnOptions.setOnClickListener { view ->
            PopupMenu(view.context, view).apply {
                menu.add(0, MENU_RENAME, 0, view.context.getString(R.string.session_options_rename))
                menu.add(0, MENU_DELETE, 1, view.context.getString(R.string.profile_delete_button))
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
        val hours = millis / (1000 * 60 * 60)
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun showRenameDialog(holder: RaceViewHolder, race: Race) {
        val input = EditText(holder.itemView.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(race.name ?: "")
            setSelection(text.length)
        }
        AlertDialog.Builder(holder.itemView.context)
            .setTitle(holder.itemView.context.getString(R.string.session_options_rename_popup_header))
            .setView(input)
            .setPositiveButton(holder.itemView.context.getString(R.string.session_options_rename_popup_ok)) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    race.name = newName
                    onRename(race, newName)
                    notifyItemChanged(holder.bindingAdapterPosition)
                }
            }
            .setNegativeButton(holder.itemView.context.getString(R.string.dialog_cancel_button)) { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
