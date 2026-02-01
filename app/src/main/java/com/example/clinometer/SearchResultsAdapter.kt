package com.example.clinometer

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.navigation.GeocodingFeature

class SearchResultsAdapter(
    private val onItemClick: (GeocodingFeature) -> Unit
) : RecyclerView.Adapter<SearchResultsAdapter.ViewHolder>() {
    
    private var results: List<GeocodingFeature> = emptyList()
    
    fun updateResults(newResults: List<GeocodingFeature>) {
        results = newResults
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val feature = results[position]
        holder.nameText.text = feature.text
        holder.descriptionText.text = feature.placeName
        holder.itemView.setOnClickListener {
            onItemClick(feature)
        }
    }
    
    override fun getItemCount() = results.size
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.tvPlaceName)
        val descriptionText: TextView = view.findViewById(R.id.tvPlaceDescription)
    }
}

