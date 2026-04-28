package com.example.clinometer

import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.navigation.GeocodingFeature

enum class QuickDestinationCategory {
    HOME,
    WORK,
    FAVORITE,
    RECENT
}

data class QuickDestinationItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: QuickDestinationCategory,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val destinationName: String? = null
)

class SearchResultsAdapter(
    private val onItemClick: (GeocodingFeature) -> Unit,
    private val onQuickDestinationClick: ((QuickDestinationItem) -> Unit)? = null,
    private val onQuickDestinationRemove: ((QuickDestinationItem) -> Unit)? = null,
    private val onSearchResultLongClick: ((GeocodingFeature) -> Unit)? = null,
    private val isSearchResultFavorite: ((GeocodingFeature) -> Boolean)? = null,
    private val onSearchResultFavoriteToggle: ((GeocodingFeature, Boolean) -> Unit)? = null,
    private val distanceTextProvider: ((GeocodingFeature) -> String?)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    constructor(onItemClick: (GeocodingFeature) -> Unit) : this(
        onItemClick = onItemClick,
        onQuickDestinationClick = null,
        onQuickDestinationRemove = null,
        onSearchResultLongClick = null,
        isSearchResultFavorite = null,
        onSearchResultFavoriteToggle = null,
        distanceTextProvider = null
    )

    private sealed interface Row {
        data class SearchResult(val feature: GeocodingFeature) : Row
        data class QuickDestination(val item: QuickDestinationItem) : Row
    }

    private var rows: List<Row> = emptyList()

    fun updateResults(newResults: List<GeocodingFeature>) {
        rows = newResults.map { Row.SearchResult(it) }
        notifyDataSetChanged()
    }

    fun showQuickItems(items: List<QuickDestinationItem>) {
        rows = items.map { Row.QuickDestination(it) }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.SearchResult -> {
                val itemHolder = holder as ViewHolder
                val feature = row.feature
                itemHolder.nameText.text = feature.text
                val distanceText = distanceTextProvider?.invoke(feature)
                if (distanceText.isNullOrBlank()) {
                    itemHolder.distanceText.visibility = View.GONE
                    itemHolder.distanceText.text = ""
                } else {
                    itemHolder.distanceText.visibility = View.VISIBLE
                    itemHolder.distanceText.text = distanceText
                }
                itemHolder.descriptionText.text = feature.placeName
                itemHolder.descriptionText.visibility = View.VISIBLE
                bindSearchResultIcon(itemHolder, feature)

                val favoriteEnabled = isSearchResultFavorite != null && onSearchResultFavoriteToggle != null
                if (favoriteEnabled) {
                    itemHolder.favoriteButton.visibility = View.VISIBLE
                    val isFavorite = isSearchResultFavorite.invoke(feature)
                    itemHolder.favoriteButton.setImageResource(
                        if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
                    )
                    itemHolder.favoriteButton.setColorFilter(
                        Color.parseColor(if (isFavorite) "#FF6020" else "#9AA2AF")
                    )
                    itemHolder.favoriteButton.setOnClickListener {
                        val adapterPos = itemHolder.bindingAdapterPosition
                        if (adapterPos == RecyclerView.NO_POSITION) return@setOnClickListener
                        val currentlyFavorite = isSearchResultFavorite.invoke(feature)
                        onSearchResultFavoriteToggle.invoke(feature, !currentlyFavorite)
                        notifyItemChanged(adapterPos)
                    }
                } else {
                    itemHolder.favoriteButton.visibility = View.GONE
                    itemHolder.favoriteButton.setOnClickListener(null)
                }

                itemHolder.itemView.setOnClickListener {
                    onItemClick(feature)
                }
                itemHolder.itemView.setOnLongClickListener {
                    if (onSearchResultLongClick != null) {
                        onSearchResultLongClick.invoke(feature)
                        true
                    } else {
                        false
                    }
                }
            }
            is Row.QuickDestination -> {
                val itemHolder = holder as ViewHolder
                val quick = row.item
                itemHolder.nameText.text = quick.title
                itemHolder.distanceText.visibility = View.GONE
                itemHolder.distanceText.text = ""
                bindQuickDestinationIcon(itemHolder, quick)

                val isRecentItem = quick.category == QuickDestinationCategory.RECENT
                if (isRecentItem) {
                    itemHolder.nameText.textSize = 14f
                    itemHolder.descriptionText.textSize = 12f
                } else {
                    itemHolder.nameText.textSize = 16f
                    itemHolder.descriptionText.textSize = 14f
                }

                if (quick.subtitle.isBlank()) {
                    itemHolder.descriptionText.visibility = View.GONE
                } else {
                    itemHolder.descriptionText.visibility = View.VISIBLE
                    itemHolder.descriptionText.text = quick.subtitle
                }

                if (isRecentItem && onQuickDestinationRemove != null) {
                    itemHolder.favoriteButton.visibility = View.VISIBLE
                    itemHolder.favoriteButton.setImageResource(R.drawable.ic_close)
                    itemHolder.favoriteButton.setColorFilter(Color.parseColor("#9AA2AF"))
                    itemHolder.favoriteButton.contentDescription =
                        itemHolder.itemView.context.getString(R.string.search_remove_recent_destination)
                    itemHolder.favoriteButton.setOnClickListener {
                        onQuickDestinationRemove.invoke(quick)
                    }
                } else {
                    itemHolder.favoriteButton.visibility = View.GONE
                    itemHolder.favoriteButton.setOnClickListener(null)
                }

                itemHolder.itemView.setOnClickListener {
                    onQuickDestinationClick?.invoke(quick)
                }
                itemHolder.itemView.setOnLongClickListener(null)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return VIEW_TYPE_ITEM
    }

    override fun getItemCount() = rows.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val placeTypeIcon: ImageView = view.findViewById(R.id.ivPlaceTypeIcon)
        val nameText: TextView = view.findViewById(R.id.tvPlaceName)
        val distanceText: TextView = view.findViewById(R.id.tvPlaceDistance)
        val descriptionText: TextView = view.findViewById(R.id.tvPlaceDescription)
        val favoriteButton: ImageView = view.findViewById(R.id.ivFavoriteToggle)
    }

    private fun bindSearchResultIcon(holder: ViewHolder, feature: GeocodingFeature) {
        val iconRes = resolveSearchResultIcon(feature)
        holder.placeTypeIcon.setImageResource(iconRes)
        val primaryColor = ContextCompat.getColor(holder.itemView.context, R.color.primary_color)
        holder.placeTypeIcon.setColorFilter(primaryColor)
    }

    private fun bindQuickDestinationIcon(holder: ViewHolder, item: QuickDestinationItem) {
        val (iconRes, tint) = when (item.category) {
            QuickDestinationCategory.HOME -> R.drawable.ic_place_black_24dp to "#66C3FF"
            QuickDestinationCategory.WORK -> R.drawable.ic_car to "#66C3FF"
            QuickDestinationCategory.FAVORITE -> R.drawable.ic_favorite to "#FF7A00"
            QuickDestinationCategory.RECENT -> R.drawable.ic_timer to "#9AA2AF"
        }
        holder.placeTypeIcon.setImageResource(iconRes)
        holder.placeTypeIcon.setColorFilter(Color.parseColor(tint))
    }

    private fun resolveSearchResultIcon(feature: GeocodingFeature): Int {
        val category = feature.properties?.category?.lowercase().orEmpty()
        val label = "${feature.text} ${feature.placeName}".lowercase()

        return when {
            category.contains("gas") || category.contains("fuel") || label.contains("газ") || label.contains("бенз") -> R.drawable.gas_station
            category.contains("parking") || label.contains("паркин") -> R.drawable.parking
            category.contains("coffee") || category.contains("cafe") || label.contains("каф") -> R.drawable.coffee_cup
            category.contains("restaurant") || category.contains("food") || label.contains("ресторан") || label.contains("храна") -> R.drawable.cutlery
            else -> R.drawable.ic_place_black_24dp
        }
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 1
    }
}

