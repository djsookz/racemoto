package com.revix.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.revix.app.track.OfficialTrackSvgAssets
import com.revix.app.track.catalog.TrackDefinition
import com.revix.app.tracking.CustomTrack
import com.revix.app.tracking.CustomTrackCreationMode
import com.revix.app.tracking.CustomTrackStorage

class TrackSelectionAdapter(
    private val tracks: List<TrackItem>,
    private val onTrackSelected: (TrackItem) -> Unit,
    private val onTrackDeleted: (CustomTrack) -> Unit,
    private val onTrackEdited: (CustomTrack) -> Unit,
    private val onOfficialTrackInfo: (TrackDefinition) -> Unit = {}
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
        val btnInfo: ImageButton = itemView.findViewById(R.id.btnTrackInfo)
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
                holder.tvDetails.text = trackItem.track.detailsText(holder.itemView.context)
                holder.tvDescription.visibility = View.GONE
                holder.tvChevron.visibility = View.GONE
                holder.btnEdit.visibility = View.GONE
                holder.btnDelete.visibility = View.GONE
                val hasMap = OfficialTrackSvgAssets.assetPathFor(trackItem.track.id) != null
                holder.btnInfo.visibility = if (hasMap) View.VISIBLE else View.GONE
                holder.btnInfo.setOnClickListener {
                    onOfficialTrackInfo(trackItem.track)
                }
            }
            is TrackItem.Custom -> {
                holder.tvTitle.text = trackItem.track.name
                val customTrackV2 = CustomTrackStorage.loadCustomTrackV2(
                    holder.itemView.context,
                    trackItem.track.id
                )
                val isCalibrated = when {
                    customTrackV2?.creationMode == CustomTrackCreationMode.DRIVING -> true
                    (customTrackV2?.measuredDistanceMeters ?: 0f) > 100f -> true
                    else -> false
                }
                val context = holder.itemView.context
                val typeLabel = when (trackItem.track.type as CustomTrack.TrackType?) {
                    CustomTrack.TrackType.POINT_TO_POINT -> context.getString(R.string.track_type_point_to_point_title)
                    else -> context.getString(R.string.track_type_circuit_title)
                }
                val calibrationLabel = context.getString(
                    if (isCalibrated) {
                        R.string.track_selection_calibration_calibrated
                    } else {
                        R.string.track_selection_calibration_estimated
                    }
                )
                holder.tvDetails.text = "$typeLabel • $calibrationLabel"
                holder.tvDescription.text = context.getString(
                    R.string.created_date_format,
                    formatDate(trackItem.track.createdAt)
                )
                holder.tvDescription.visibility = View.VISIBLE
                holder.tvChevron.visibility = View.GONE
                holder.btnInfo.visibility = View.GONE
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
