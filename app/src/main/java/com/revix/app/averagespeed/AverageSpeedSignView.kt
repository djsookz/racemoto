package com.revix.app.averagespeed

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.revix.app.R
import com.revix.app.settings.UnitsManager
import kotlin.math.roundToInt

class AverageSpeedSignView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val titleView: TextView
    private val detailView: TextView
    private val limitValueView: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.bg_avg_speed_sign)
        val padH = (12 * resources.displayMetrics.density).toInt()
        val padV = (7 * resources.displayMetrics.density).toInt()
        setPadding(padH, padV, padH, padV)
        inflate(context, R.layout.view_avg_speed_sign, this)
        titleView = findViewById(R.id.avgSpeedSignTitle)
        detailView = findViewById(R.id.avgSpeedSignDetail)
        limitValueView = findViewById(R.id.avgSpeedLimitValue)
        visibility = GONE
    }

    fun showApproach(distanceMeters: Int, speedLimitKmh: Int) {
        titleView.setText(R.string.avg_speed_sign_title)
        bindLimit(speedLimitKmh)
        detailView.text = context.getString(R.string.avg_speed_sign_in_meters, distanceMeters)
        detailView.setTextColor(DETAIL_OK_COLOR)
        detailView.visibility = VISIBLE
        visibility = VISIBLE
    }

    fun showInside(speedLimitKmh: Int, instantAverageKmh: Float?) {
        titleView.setText(R.string.avg_speed_sign_title)
        bindLimit(speedLimitKmh)
        if (instantAverageKmh != null && instantAverageKmh > 0f) {
            detailView.text = UnitsManager.formatSpeed(instantAverageKmh, context, 0)
            val overLimit = instantAverageKmh.roundToInt() > speedLimitKmh
            detailView.setTextColor(if (overLimit) DETAIL_OVER_COLOR else DETAIL_OK_COLOR)
            detailView.visibility = VISIBLE
        } else {
            detailView.text = ""
            detailView.setTextColor(DETAIL_OK_COLOR)
            detailView.visibility = GONE
        }
        visibility = VISIBLE
    }

    fun hideSign() {
        if (visibility != GONE) {
            visibility = GONE
        }
    }

    private fun bindLimit(speedLimitKmh: Int) {
        val shown = speedLimitKmh.coerceAtLeast(0)
        limitValueView.text = if (shown > 0) shown.toString() else "—"
        val sp = if (shown >= 100) 15f else 17f
        limitValueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    }

    private companion object {
        const val DETAIL_OK_COLOR = 0xFFB6E6DE.toInt()
        const val DETAIL_OVER_COLOR = 0xFFE30613.toInt()
    }
}
