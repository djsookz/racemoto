package com.example.clinometer.main.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.clinometer.R

object ManeuverUiBinder {
    fun setViewColors(view: View, textColor: Int, backgroundColor: Int) {
        if (view.background != null) {
            view.setBackgroundColor(backgroundColor)
        }

        if (view is TextView) {
            view.setTextColor(textColor)
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setViewColors(view.getChildAt(i), textColor, backgroundColor)
            }
        }
    }

    fun reduceManeuverTextSize(view: View, isLandscape: Boolean = false) {
        if (view is TextView) {
            val alreadyScaled = (view.getTag(R.id.tag_maneuver_scaled_text) as? Boolean) == true
            if (alreadyScaled) return
            val currentSize = view.textSize / view.resources.displayMetrics.scaledDensity
            val scaleFactor = if (isLandscape) 1.2f else 0.8f
            val newSize = currentSize * scaleFactor
            view.textSize = newSize
            if (isLandscape) {
                view.maxLines = 2
                view.ellipsize = android.text.TextUtils.TruncateAt.END
                val density = view.resources.displayMetrics.density
                view.maxWidth = (360 * density).toInt()
            }
            view.setTag(R.id.tag_maneuver_scaled_text, true)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                reduceManeuverTextSize(view.getChildAt(i), isLandscape)
            }
        }
    }

    fun reduceManeuverIconSize(view: View, isLandscape: Boolean = false) {
        if (view is ImageView) {
            val alreadyScaled = (view.getTag(R.id.tag_maneuver_scaled_icon) as? Boolean) == true
            if (alreadyScaled) return
            val layoutParams = view.layoutParams
            if (layoutParams != null) {
                val reductionFactor = if (isLandscape) 0.8f else 0.7f
                val currentWidth = layoutParams.width
                val currentHeight = layoutParams.height

                if (currentWidth > 0 && currentHeight > 0) {
                    layoutParams.width = (currentWidth * reductionFactor).toInt()
                    layoutParams.height = (currentHeight * reductionFactor).toInt()
                    view.layoutParams = layoutParams
                } else {
                    val sizeInDp = if (isLandscape) 36 else 32
                    val sizeInPx = (sizeInDp * view.resources.displayMetrics.density).toInt()
                    layoutParams.width = sizeInPx
                    layoutParams.height = sizeInPx
                    view.layoutParams = layoutParams
                }
            }
            view.setTag(R.id.tag_maneuver_scaled_icon, true)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                reduceManeuverIconSize(view.getChildAt(i), isLandscape)
            }
        }
    }

    fun centerManeuverText(view: View) {
        if (view is TextView) {
            view.textAlignment = View.TEXT_ALIGNMENT_CENTER
            view.gravity = Gravity.CENTER_HORIZONTAL
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                centerManeuverText(view.getChildAt(i))
            }
        }
    }

    fun reduceManeuverSpacing(view: View, isLandscape: Boolean = false) {
        if (view is ViewGroup) {
            val alreadyAdjusted = (view.getTag(R.id.tag_maneuver_spacing_adjusted) as? Boolean) == true
            if (!alreadyAdjusted) {
                if (view is LinearLayout && view.orientation == LinearLayout.HORIZONTAL) {
                    val currentPaddingStart = view.paddingStart
                    val currentPaddingEnd = view.paddingEnd
                    val maxPadding = if (isLandscape) 2 else 8
                    val reductionFactor = if (isLandscape) 0.1f else 0.4f
                    if (currentPaddingStart > 4 || currentPaddingEnd > 4) {
                        view.setPaddingRelative(
                            (currentPaddingStart * reductionFactor).toInt().coerceAtMost(maxPadding),
                            view.paddingTop,
                            (currentPaddingEnd * reductionFactor).toInt().coerceAtMost(maxPadding),
                            view.paddingBottom
                        )
                    }
                }

                view.setTag(R.id.tag_maneuver_spacing_adjusted, true)
            }

            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val childAlreadyAdjusted = (child.getTag(R.id.tag_maneuver_spacing_adjusted) as? Boolean) == true
                if (!childAlreadyAdjusted && (child is ImageView || child is TextView)) {
                    val childParams = child.layoutParams as? ViewGroup.MarginLayoutParams
                    if (childParams != null) {
                        val density = child.resources.displayMetrics.density
                        val marginDp = if (isLandscape) 0 else 4
                        if (childParams.marginStart > (6 * density).toInt()) {
                            childParams.marginStart = (marginDp * density).toInt()
                        }
                        if (childParams.marginEnd > (6 * density).toInt()) {
                            childParams.marginEnd = (marginDp * density).toInt()
                        }
                        child.layoutParams = childParams
                        child.setTag(R.id.tag_maneuver_spacing_adjusted, true)
                    }
                }
            }

            for (i in 0 until view.childCount) {
                reduceManeuverSpacing(view.getChildAt(i), isLandscape)
            }
        }
    }
}
