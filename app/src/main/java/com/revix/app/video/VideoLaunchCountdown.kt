package com.revix.app.video

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.min

object VideoLaunchCountdown {
    const val REQUIRED_MS = 3_000L
    const val PRE_ROLL_MS = 5_000L
    const val POST_ROLL_MS = 3_000L
    private const val KEYFRAME_SLACK_MS = 400L

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 0, 0, 0)
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
    }

    fun digit(prerollAvailableMs: Long, timeUntilStartMs: Long): Int? {
        // Track trims to exactly 3s; keyframe snap can land a few hundred ms short.
        if (prerollAvailableMs + KEYFRAME_SLACK_MS < REQUIRED_MS) return null
        if (timeUntilStartMs <= 0L || timeUntilStartMs > REQUIRED_MS) return null
        return when {
            timeUntilStartMs > 2_000L -> 3
            timeUntilStartMs > 1_000L -> 2
            else -> 1
        }
    }

    fun draw(canvas: Canvas, width: Float, height: Float, digit: Int?) {
        val number = digit ?: return
        if (width < 8f || height < 8f) return
        val size = min(width, height) * 0.22f
        fillPaint.textSize = size
        strokePaint.textSize = size
        strokePaint.strokeWidth = (size * 0.08f).coerceAtLeast(4f)
        val x = width / 2f
        val y = height * 0.46f - (fillPaint.ascent() + fillPaint.descent()) / 2f
        val text = number.toString()
        canvas.drawText(text, x, y, strokePaint)
        canvas.drawText(text, x, y, fillPaint)
    }
}
