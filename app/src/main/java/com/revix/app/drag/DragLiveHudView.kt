package com.revix.app.drag

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import com.revix.app.R

class DragLiveHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val renderer = DragAttemptHudRenderer(
        context = context,
        attempt = null,
        mode = MeasurementMode.ALL,
        logo = runCatching { BitmapFactory.decodeResource(resources, R.drawable.revix_logo) }.getOrNull()
    )
    private var frame = renderer.liveFrame(DragRunVideoHudState())

    init {
        setWillNotDraw(false)
    }

    fun update(state: DragRunVideoHudState) {
        frame = renderer.liveFrame(state)
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 1 || height <= 1) return
        renderer.drawFrame(canvas, width.toFloat(), height.toFloat(), frame)
    }
}
