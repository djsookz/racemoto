package com.example.clinometer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.track.catalog.TrackDefinition
import com.example.clinometer.track.custom.CustomTrack

class TrackSelectionAdapter(
    private val tracks: List<TrackItem>,
    private val onTrackSelected: (TrackItem) -> Unit,
    private val onTrackDeleted: (CustomTrack) -> Unit,
    private val onTrackEdited: (CustomTrack) -> Unit
) : RecyclerView.Adapter<TrackSelectionAdapter.TrackViewHolder>() {
    
    sealed class TrackItem {
        data class Official(val track: TrackDefinition) : TrackItem()
        data class Custom(val track: CustomTrack) : TrackItem()
    }
    
    class TrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTrackTitle)
        val tvDetails: TextView = itemView.findViewById(R.id.tvTrackDetails)
        val tvDescription: TextView = itemView.findViewById(R.id.tvTrackDescription)
        val tvChevron: TextView = itemView.findViewById(R.id.tvChevron)
        val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_track_selection, parent, false)
        return TrackViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val trackItem = tracks[position]
        
        when (trackItem) {
            is TrackItem.Official -> {
                holder.tvTitle.text = trackItem.track.name
                holder.tvDetails.text = trackItem.track.detailsText()
                holder.tvDescription.text = trackItem.track.description
                holder.tvChevron.visibility = View.VISIBLE
                holder.tvChevron.text = if (trackItem.track.isReadyForSession()) "▼" else "🔒"
                holder.btnEdit.visibility = View.GONE
                holder.btnDelete.visibility = View.GONE
            }
            is TrackItem.Custom -> {
                holder.tvTitle.text = trackItem.track.name
                holder.tvDetails.text = when (trackItem.track.type) {
                    CustomTrack.TrackType.CIRCUIT -> "Обиколка • ${trackItem.track.points.size} точки"
                    CustomTrack.TrackType.POINT_TO_POINT -> "Точка-до-точка • ${trackItem.track.points.size} точки"
                }
                holder.tvDescription.text = "Създадена на ${formatDate(trackItem.track.createdAt)}"
                holder.tvChevron.visibility = View.GONE
                holder.btnEdit.visibility = View.VISIBLE
                holder.btnDelete.visibility = View.VISIBLE

                holder.btnEdit.setOnClickListener {
                    onTrackEdited(trackItem.track)
                }
                
                holder.btnDelete.setOnClickListener {
                    onTrackDeleted(trackItem.track)
                }
            }
        }
        
        holder.itemView.setOnClickListener {
            onTrackSelected(trackItem)
        }
    }
    
    override fun getItemCount(): Int = tracks.size
    
    private fun formatDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}
