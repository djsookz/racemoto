package com.example.clinometer

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.view.animation.LinearInterpolator
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import android.location.Location
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.PI

class PulsingLocationOverlay(
    private val mapView: MapView
) : Overlay() {

    private val density = mapView.context.resources.displayMetrics.density

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55FF7A18")
        style = Paint.Style.FILL
    }
    private val baseFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF7A18")
        style = Paint.Style.FILL
    }
    private val baseStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }

    private var latestPoint: GeoPoint? = null
    private var accuracyMeters: Float = 20f
    private var pulseProgress = 0f

    private val point = Point()
    private val minAccuracyMeters = 5f
    private val haloPulseExtraPx = 6f * density
    private val baseRadiusPx = 11f * density
    private val haloGapPx = 4f * density

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3200L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener {
            pulseProgress = it.animatedValue as Float
            mapView.postInvalidate()
        }
    }

    fun updateLocation(location: Location) {
        latestPoint = GeoPoint(location.latitude, location.longitude)
        accuracyMeters = location.accuracy.coerceAtLeast(5f)
        mapView.postInvalidate()
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val geoPoint = latestPoint ?: return
        val projection = mapView.projection ?: return
        projection.toPixels(geoPoint, point)

        val displayAccuracyMeters = accuracyMeters.coerceAtLeast(minAccuracyMeters)
        val accuracyRadiusPx = projection.metersToPixels(displayAccuracyMeters)

        val haloStartRadius = baseRadiusPx + haloGapPx
        val haloEndRadius = max(accuracyRadiusPx, haloStartRadius + haloPulseExtraPx)
        val easedRadiusProgress = (0.5f - 0.5f * cos(PI * pulseProgress).toFloat())
        val haloRadius = haloStartRadius + (haloEndRadius - haloStartRadius) * easedRadiusProgress

        val envelope = if (pulseProgress < 0.5f) pulseProgress * 2f else (1f - pulseProgress) * 2f
        haloPaint.alpha = (130 * envelope).toInt().coerceIn(0, 140)
        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), haloRadius, haloPaint)

        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), baseRadiusPx, baseFillPaint)
        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), baseRadiusPx, baseStrokePaint)
    }

    fun start() {
        if (!animator.isRunning) {
            animator.start()
        }
    }

    fun stop() {
        if (animator.isRunning) {
            animator.cancel()
        }
    }

    fun onDestroy() {
        animator.cancel()
    }
}

