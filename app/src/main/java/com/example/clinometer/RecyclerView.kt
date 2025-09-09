package com.example.clinometer

import android.app.AlertDialog
import android.graphics.Color
import android.text.InputType
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.util.Calendar
import java.util.Date

class RaceAdapter(
    races: MutableList<Race>,
    private val onItemClick: (Race) -> Unit,
    private val onDeleteClick: (Race) -> Unit,
    private val onRename: (Race, String) -> Unit
) : RecyclerView.Adapter<RaceAdapter.RaceViewHolder>() {

    private var races: MutableList<Race> = races.sortedByDescending { it.absoluteTimestamp }.toMutableList()

    companion object {
        private const val MENU_RENAME = 1
        private const val MENU_DELETE = 2
    }

    inner class RaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvName)
        val dateTextView: TextView = itemView.findViewById(R.id.dateTextView)
        val tvDuration: TextView = itemView.findViewById(R.id.tvNumber)
        val btnOptions: ImageButton = itemView.findViewById(R.id.btnOptions)
        val miniMapView: MapView = itemView.findViewById(R.id.miniMapView)
        val layoutMapPlaceholder: LinearLayout = itemView.findViewById(R.id.layoutMapPlaceholder)

        init {
            setupMiniMap()
        }

        private fun setupMiniMap() {
            try {
                miniMapView.setTileSource(TileSourceFactory.MAPNIK)
                miniMapView.setMultiTouchControls(false)
                miniMapView.setBuiltInZoomControls(false)
                miniMapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                miniMapView.isTilesScaledToDpi = true
                miniMapView.setDestroyMode(false)
                miniMapView.setTilesScaledToDpi(true)
                miniMapView.minZoomLevel = 3.0
                miniMapView.maxZoomLevel = 19.0
                miniMapView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                miniMapView.isClickable = false
                miniMapView.isFocusable = false
                miniMapView.isEnabled = false
                miniMapView.setHorizontalMapRepetitionEnabled(false)
                miniMapView.setVerticalMapRepetitionEnabled(false)

                val parent = miniMapView.parent
                if (parent is ViewGroup) {
                    val blockerTag = "mini_map_blocker"
                    var blocker = parent.findViewWithTag<View>(blockerTag)
                    if (blocker == null) {
                        val layoutParams = if (parent is FrameLayout) {
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        } else {
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }

                        blocker = View(itemView.context).apply {
                            tag = blockerTag
                            this.layoutParams = layoutParams
                            isClickable = true
                            isFocusable = true
                            setOnTouchListener { _, event ->
                                if (event.action == MotionEvent.ACTION_UP) {
                                    itemView.performClick()
                                }
                                true
                            }
                        }
                        parent.addView(blocker)
                        parent.bringChildToFront(blocker)
                        parent.requestLayout()
                        parent.invalidate()
                    } else {
                        parent.bringChildToFront(blocker)
                    }
                }
            } catch (e: Exception) {
                Log.w("RaceAdapter", "Грешка при setup на мини карта", e)
            }
        }

        fun loadMiniMap(race: Race) {
            try {
                val routePoints = RouteStorage.loadRoutePoints(itemView.context, race.id)

                if (routePoints.isEmpty()) {
                    miniMapView.visibility = View.GONE
                    layoutMapPlaceholder.visibility = View.VISIBLE
                    return
                }

                layoutMapPlaceholder.visibility = View.GONE
                miniMapView.visibility = View.VISIBLE

                miniMapView.overlays.clear()

                val polyline = Polyline().apply {
                    setPoints(routePoints.map { it.geoPoint })
                    color = Color.rgb(0, 25, 255)
                    outlinePaint.strokeWidth = 8f
                    outlinePaint.alpha = 255
                }
                miniMapView.overlays.add(polyline)

                if (routePoints.size >= 2) {
                    val startPoint = routePoints.first().geoPoint
                    val endPoint = routePoints.last().geoPoint

                    val startMarker = org.osmdroid.views.overlay.Marker(miniMapView).apply {
                        position = startPoint
                        setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
                        icon = createMarkerIcon(Color.parseColor("#4CAF50"), "A")
                    }
                    miniMapView.overlays.add(startMarker)

                    val endMarker = org.osmdroid.views.overlay.Marker(miniMapView).apply {
                        position = endPoint
                        setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
                        icon = createMarkerIcon(Color.parseColor("#F44336"), "B")
                    }
                    miniMapView.overlays.add(endMarker)
                }

                val allGeoPoints = routePoints.map { it.geoPoint }

                if (allGeoPoints.size >= 2) {
                    val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPointsSafe(allGeoPoints)

                    val latDiff = boundingBox.latNorth - boundingBox.latSouth
                    val lonDiff = boundingBox.lonEast - boundingBox.lonWest
                    val padding = kotlin.math.max(latDiff, lonDiff) * 0.15

                    val adjustedBox = org.osmdroid.util.BoundingBox(
                        boundingBox.latNorth + padding,
                        boundingBox.lonEast + padding,
                        boundingBox.latSouth - padding,
                        boundingBox.lonWest - padding
                    )

                    miniMapView.post {
                        miniMapView.zoomToBoundingBox(adjustedBox, false)
                        miniMapView.invalidate()
                    }
                } else {
                    val point = allGeoPoints[0]
                    miniMapView.controller.setCenter(point)
                    miniMapView.controller.setZoom(15.0)
                }

            } catch (e: Exception) {
                Log.e("RaceAdapter", "Грешка при зареждане на мини карта", e)
                miniMapView.visibility = View.GONE
                layoutMapPlaceholder.visibility = View.VISIBLE
            }
        }

        private fun createMarkerIcon(color: Int, text: String): android.graphics.drawable.Drawable {
            return object : android.graphics.drawable.Drawable() {
                override fun draw(canvas: android.graphics.Canvas) {
                    val paint = android.graphics.Paint().apply {
                        this.color = color
                        isAntiAlias = true
                        style = android.graphics.Paint.Style.FILL
                    }

                    val strokePaint = android.graphics.Paint().apply {
                        this.color = android.graphics.Color.WHITE
                        isAntiAlias = true
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 3f
                    }

                    val textPaint = android.graphics.Paint().apply {
                        this.color = android.graphics.Color.WHITE
                        textSize = 18f
                        isAntiAlias = true
                        isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }

                    val radius = 16f
                    val centerX = bounds.exactCenterX()
                    val centerY = bounds.exactCenterY()

                    canvas.drawCircle(centerX, centerY, radius, paint)
                    canvas.drawCircle(centerX, centerY, radius, strokePaint)
                    val textY = centerY + (textPaint.textSize / 3)
                    canvas.drawText(text, centerX, textY, textPaint)
                }

                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT

                override fun getIntrinsicWidth(): Int = 32
                override fun getIntrinsicHeight(): Int = 32
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RaceViewHolder {
        try {
            Configuration.getInstance().load(
                parent.context,
                PreferenceManager.getDefaultSharedPreferences(parent.context)
            )
        } catch (e: Exception) {
            Log.w("RaceAdapter", "Грешка при инициализация на OSMDroid", e)
        }

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_race, parent, false)
        return RaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: RaceViewHolder, position: Int) {
        val race = races[position]

        fun formatRelativeDate(timestamp: Long): String {
            val ctx = holder.itemView.context

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

        holder.loadMiniMap(race)

        holder.itemView.setOnClickListener { onItemClick(race) }
        holder.miniMapView.setOnClickListener { onItemClick(race) }

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
                            true
                        }
                        else -> false
                    }
                }
            }.show()
        }
    }

    override fun getItemCount(): Int = races.size

    override fun onViewRecycled(holder: RaceViewHolder) {
        super.onViewRecycled(holder)
        try {
            holder.miniMapView.overlays.clear()
        } catch (e: Exception) {
            Log.w("RaceAdapter", "Грешка при изчистване на overlay-и", e)
        }
    }

    fun updateRaces(newRaces: List<Race>) {
        races.clear()
        races.addAll(newRaces.sortedByDescending { it.absoluteTimestamp })
        notifyDataSetChanged()
    }

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
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos)
                }
            }
            .setNegativeButton(holder.itemView.context.getString(R.string.dialog_cancel_button)) { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
