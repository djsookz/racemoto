package com.revix.app

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Live HUD on the camera preview. Layout uses the same [TrackSessionHudRenderer]
 * as export, drawn in the FIT_CENTER video rectangle so on-screen positions
 * match the exported file (same aspect, same relative layout).
 */
class TrackSessionLiveHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val renderer = TrackSessionHudRenderer(
        runCatching { BitmapFactory.decodeResource(resources, R.drawable.revix_logo) }.getOrNull()
    )
    private val videoRect = RectF()
    private var contentAspect = 16f / 9f
    private var isMotorcycle = true
    private var speedUnitLabel = "KM/H"
    private var speedFactor = 1f
    private var miniMapPoints: List<GeoPoint> = emptyList()
    private var startMarker: GeoPoint? = null
    private var followMiniMap = true
    private var trackName: String = ""
    private var vehicleName: String = ""
    private var vehicleSpecs: String = ""
    private var frame: TrackSessionHudRenderer.Frame? = null

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = false
    }

    fun attachPreview(previewView: View) {
        invalidate()
    }

    fun setSessionConfig(
        isMotorcycle: Boolean,
        speedUnitLabel: String,
        speedFactor: Float,
        miniMapPoints: List<GeoPoint>,
        trackName: String = "",
        vehicleName: String = "",
        vehicleSpecs: String = "",
        startMarker: GeoPoint? = null,
        followMiniMap: Boolean = true
    ) {
        this.isMotorcycle = isMotorcycle
        this.speedUnitLabel = speedUnitLabel
        this.speedFactor = speedFactor
        this.miniMapPoints = miniMapPoints
        this.trackName = trackName
        this.vehicleName = vehicleName
        this.vehicleSpecs = vehicleSpecs
        this.startMarker = startMarker
        this.followMiniMap = followMiniMap
        invalidate()
    }

    fun setContentAspect(aspect: Float) {
        if (aspect <= 0.05f || !aspect.isFinite()) return
        if (kotlin.math.abs(contentAspect - aspect) < 0.001f) return
        contentAspect = aspect
        invalidate()
    }

    fun update(frame: TrackSessionHudRenderer.Frame) {
        this.frame = frame
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = frame ?: return
        if (width <= 1 || height <= 1) return
        fitCenterRect(width.toFloat(), height.toFloat(), contentAspect)
        if (videoRect.width() < 8f || videoRect.height() < 8f) return
        canvas.save()
        canvas.clipRect(videoRect)
        canvas.translate(videoRect.left, videoRect.top)
        renderer.configure(
            width = videoRect.width(),
            height = videoRect.height(),
            isMotorcycle = isMotorcycle,
            speedUnitLabel = speedUnitLabel,
            speedFactor = speedFactor,
            miniMapPoints = miniMapPoints,
            trackName = trackName,
            vehicleName = vehicleName,
            vehicleSpecs = vehicleSpecs,
            startMarker = startMarker,
            followMiniMap = followMiniMap
        )
        renderer.draw(canvas, current)
        canvas.restore()
        if (isAttachedToWindow) {
            postInvalidateOnAnimation()
        }
    }

    private fun fitCenterRect(viewW: Float, viewH: Float, aspect: Float) {
        val viewAspect = viewW / viewH
        if (viewAspect > aspect) {
            val h = viewH
            val w = h * aspect
            val left = (viewW - w) / 2f
            videoRect.set(left, 0f, left + w, h)
        } else {
            val w = viewW
            val h = w / aspect
            val top = (viewH - h) / 2f
            videoRect.set(0f, top, w, top + h)
        }
    }

    companion object {
        fun displayedAspect(contentWidth: Int, contentHeight: Int, rotationDegrees: Int): Float {
            val rotated = rotationDegrees % 180 != 0
            val width = if (rotated) contentHeight else contentWidth
            val height = if (rotated) contentWidth else contentHeight
            if (height <= 0) return 16f / 9f
            return width / height.toFloat()
        }
    }
}
