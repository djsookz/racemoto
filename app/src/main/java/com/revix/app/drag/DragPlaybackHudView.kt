package com.revix.app.drag

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import com.revix.app.DragAttempt
import com.revix.app.R

class DragPlaybackHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var renderer: DragAttemptHudRenderer? = null
    private var positionMs = 0L

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean = false

    fun bind(attempt: DragAttempt?, mode: MeasurementMode) {
        renderer = DragAttemptHudRenderer(
            context = context,
            attempt = attempt,
            mode = mode,
            logo = runCatching { BitmapFactory.decodeResource(resources, R.drawable.revix_logo) }.getOrNull()
        )
        invalidate()
    }

    fun setPositionMs(positionMs: Long) {
        if (this.positionMs == positionMs) return
        this.positionMs = positionMs
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 1 || height <= 1) return
        renderer?.draw(canvas, width.toFloat(), height.toFloat(), positionMs)
    }
}
