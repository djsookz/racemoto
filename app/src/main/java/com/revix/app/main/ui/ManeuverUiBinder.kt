package com.revix.app.main.ui

import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Guideline
import com.mapbox.navigation.ui.components.R
import com.revix.app.R as AppR
import java.util.Locale

object ManeuverUiBinder {
    private val distanceLabelRegex = Regex(
        "^\\d+[\\d\\s.,]*\\s*(m|km|ft|mi|м|км|χλμ)$",
        RegexOption.IGNORE_CASE
    )

    fun applyAfterRender(root: View, isLandscape: Boolean) {
        clearSurfaces(root)
        applyLayoutTuning(root, isLandscape)
        if (root.getTag(AppR.id.tag_maneuver_spacing_adjusted) != true) {
            reduceManeuverSpacing(root, isLandscape)
            root.setTag(AppR.id.tag_maneuver_spacing_adjusted, true)
        }
    }

    fun applyLayoutTuning(root: View, isLandscape: Boolean) {
        val primary = root.findViewById<TextView>(R.id.primaryManeuverText) ?: return
        ensurePrimaryWrapFlags(primary)

        val orientationFlag = if (isLandscape) 2 else 1
        if (root.getTag(AppR.id.tag_maneuver_layout_tuned) == orientationFlag) {
            return
        }

        adjustGuideline(root, isLandscape)
        matchContainerWidth(root)
        applyDistanceTuning(root)
        applyIconTuning(root, isLandscape)
        root.setTag(AppR.id.tag_maneuver_layout_tuned, orientationFlag)
    }

    private fun ensurePrimaryWrapFlags(primary: TextView) {
        if (primary.maxLines != 2) primary.maxLines = 2
        if (primary.minLines != 1) primary.minLines = 1
        if (primary.isSingleLine) primary.isSingleLine = false
        primary.setHorizontallyScrolling(false)
        if (primary.ellipsize != TextUtils.TruncateAt.END) {
            primary.ellipsize = TextUtils.TruncateAt.END
        }
    }

    private fun matchContainerWidth(root: View) {
        fun matchWidth(view: View?) {
            val params = view?.layoutParams ?: return
            if (params.width > 0) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                view.layoutParams = params
            }
        }
        matchWidth(root)
        matchWidth(root.findViewById(R.id.maneuver))
        matchWidth(root.findViewById(R.id.mainManeuverLayout))
    }

    private fun clearSurfaces(root: View) {
        root.findViewById<CardView>(R.id.maneuver)?.apply {
            setCardBackgroundColor(Color.TRANSPARENT)
            cardElevation = 0f
            maxCardElevation = 0f
        }
        root.findViewById<View>(R.id.mainManeuverLayout)?.setBackgroundColor(Color.TRANSPARENT)
        root.findViewById<View>(R.id.subManeuverLayout)?.setBackgroundColor(Color.TRANSPARENT)
        root.findViewById<View>(R.id.upcomingManeuverRecycler)?.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun adjustGuideline(root: View, isLandscape: Boolean) {
        root.findViewById<Guideline>(R.id.mainManeuverGuideline)?.let { guideline ->
            val params = guideline.layoutParams as? ConstraintLayout.LayoutParams ?: return
            val percent = if (isLandscape) 0.24f else 0.22f
            if (params.guidePercent == percent) return
            params.guidePercent = percent
            guideline.layoutParams = params
        }
    }

    private fun applyDistanceTuning(root: View) {
        val stepDistance = root.findViewById<TextView>(R.id.stepDistance) ?: return
        stepDistance.gravity = Gravity.CENTER_HORIZONTAL
        stepDistance.textAlignment = View.TEXT_ALIGNMENT_CENTER
        val isLandscape = root.resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val sizeSp = if (isLandscape) 18f else 22f
        if (stepDistance.textSize != sizeSp * root.resources.displayMetrics.scaledDensity) {
            stepDistance.textSize = sizeSp
        }
    }

    private fun applyIconTuning(root: View, isLandscape: Boolean) {
        val icon = root.findViewById<ImageView>(R.id.maneuverIcon) ?: return
        val density = icon.resources.displayMetrics.density
        val sizePx = ((if (isLandscape) 32 else 40) * density).toInt()
        val params = icon.layoutParams ?: return
        if (params.width == sizePx && params.height == sizePx) return
        params.width = sizePx
        params.height = sizePx
        icon.layoutParams = params
    }

    fun reduceManeuverSpacing(view: View, isLandscape: Boolean = false) {
        if (view is ViewGroup) {
            if (view is LinearLayout && view.orientation == LinearLayout.HORIZONTAL) {
                val maxPadding = if (isLandscape) 2 else 8
                val reductionFactor = if (isLandscape) 0.1f else 0.4f
                if (view.paddingStart > 4 || view.paddingEnd > 4) {
                    view.setPaddingRelative(
                        (view.paddingStart * reductionFactor).toInt().coerceAtMost(maxPadding),
                        view.paddingTop,
                        (view.paddingEnd * reductionFactor).toInt().coerceAtMost(maxPadding),
                        view.paddingBottom
                    )
                }
            }

            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child.id == R.id.stepDistance ||
                    child.id == R.id.primaryManeuverText ||
                    child.id == R.id.secondaryManeuverText ||
                    child.id == R.id.maneuverIcon
                ) {
                    continue
                }
                if (child is TextView && isManeuverDistanceLabel(child.text)) {
                    continue
                }

                val childParams = child.layoutParams as? ViewGroup.MarginLayoutParams
                if (childParams != null && (child is ImageView || child is TextView)) {
                    val density = child.resources.displayMetrics.density
                    if (isLandscape) {
                        if (childParams.marginStart > 0) childParams.marginStart = 0
                        if (childParams.marginEnd > 0) childParams.marginEnd = 0
                    } else {
                        val marginPx = (4 * density).toInt()
                        if (childParams.marginStart > (6 * density).toInt()) {
                            childParams.marginStart = marginPx
                        }
                        if (childParams.marginEnd > (6 * density).toInt()) {
                            childParams.marginEnd = marginPx
                        }
                    }
                    child.layoutParams = childParams
                }
                reduceManeuverSpacing(child, isLandscape)
            }
        }
    }

    fun isManeuverDistanceLabel(text: CharSequence?): Boolean {
        val value = text?.toString()?.trim()?.lowercase(Locale.getDefault()) ?: return false
        return distanceLabelRegex.matches(value)
    }
}
