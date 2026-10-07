package com.revix.app.main.tour

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.revix.app.R
import com.google.android.material.button.MaterialButton

/**
 * Full-screen coach-mark overlay: dimmed scrim with a rounded cutout around the target.
 */
class FeatureTourOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC000000.toInt()
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        color = ContextCompat.getColor(context, R.color.primary_color)
    }

    private val highlightRect = RectF()
    private var hasHighlight = false
    private val cornerRadius = 16f * resources.displayMetrics.density
    private val padding = 8f * resources.displayMetrics.density

    val tooltipRoot: View
    val tvStepCounter: TextView
    val tvTitle: TextView
    val tvBody: TextView
    val btnSkip: TextView
    val btnNext: MaterialButton

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isClickable = true
        isFocusable = true

        tooltipRoot = inflate(context, R.layout.view_feature_tour_tooltip, null)
        addView(
            tooltipRoot,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )
        tvStepCounter = tooltipRoot.findViewById(R.id.tvTourStepCounter)
        tvTitle = tooltipRoot.findViewById(R.id.tvTourTitle)
        tvBody = tooltipRoot.findViewById(R.id.tvTourBody)
        btnSkip = tooltipRoot.findViewById(R.id.btnTourSkip)
        btnNext = tooltipRoot.findViewById(R.id.btnTourNext)
    }

    fun setHighlight(target: View?) {
        if (target == null || !target.isShown) {
            hasHighlight = false
            invalidate()
            return
        }
        val loc = IntArray(2)
        target.getLocationInWindow(loc)
        val overlayLoc = IntArray(2)
        getLocationInWindow(overlayLoc)
        val left = loc[0] - overlayLoc[0] - padding
        val top = loc[1] - overlayLoc[1] - padding
        val right = left + target.width + padding * 2
        val bottom = top + target.height + padding * 2
        highlightRect.set(left, top, right, bottom)
        hasHighlight = true
        positionTooltip()
        invalidate()
    }

    private fun positionTooltip() {
        tooltipRoot.post {
            val tipHeight = tooltipRoot.height
            val tipMargin = (16 * resources.displayMetrics.density).toInt()
            val overlayHeight = height
            val spaceBelow = overlayHeight - highlightRect.bottom
            val spaceAbove = highlightRect.top
            val y = if (hasHighlight && spaceBelow > tipHeight + tipMargin + 40) {
                (highlightRect.bottom + tipMargin).toInt()
            } else if (hasHighlight && spaceAbove > tipHeight + tipMargin + 40) {
                (highlightRect.top - tipHeight - tipMargin).toInt()
            } else {
                (overlayHeight * 0.28f).toInt()
            }.coerceIn(tipMargin, (overlayHeight - tipHeight - tipMargin).coerceAtLeast(tipMargin))

            tooltipRoot.translationY = y.toFloat()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val checkpoint = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        if (hasHighlight) {
            canvas.drawRoundRect(highlightRect, cornerRadius, cornerRadius, clearPaint)
            canvas.drawRoundRect(highlightRect, cornerRadius, cornerRadius, ringPaint)
        }
        canvas.restoreToCount(checkpoint)
        super.onDraw(canvas)
    }
}
