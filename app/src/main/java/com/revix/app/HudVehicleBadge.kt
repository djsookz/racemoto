package com.revix.app

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.max

internal object HudVehicleBadge {
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HudBottomBar.TEXT_PRIMARY
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.04f
    }
    private val specsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HudBottomBar.PRIMARY
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        letterSpacing = 0.08f
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xB306080C.toInt()
        style = Paint.Style.FILL
    }
    private val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.6f
    }
    private val pillRect = RectF()

    fun draw(
        canvas: Canvas,
        identity: HudVehicleIdentity,
        logoLeft: Float,
        logoRight: Float,
        rightColumnLeft: Float,
        barTop: Float,
        barBottom: Float,
        canvasWidth: Float,
        canvasHeight: Float,
        barHeight: Float
    ) {
        if (identity.isEmpty) return
        val gap = barHeight * 0.10f
        val inBarLeft = logoRight + gap
        val inBarMax = (rightColumnLeft - gap - inBarLeft).coerceAtLeast(0f)
        val landscape = canvasWidth >= canvasHeight
        val minInBar = barHeight * 1.45f
        if (landscape && inBarMax >= minInBar) {
            drawStack(
                canvas = canvas,
                identity = identity,
                x = inBarLeft,
                centerY = (barTop + barBottom) / 2f,
                maxWidth = inBarMax,
                barHeight = barHeight,
                withPill = false
            )
            return
        }
        val maxWidth = (canvasWidth * 0.58f).coerceAtMost((rightColumnLeft - gap * 2f).coerceAtLeast(barHeight * 2.2f))
        val blockHeight = stackHeight(identity, barHeight)
        drawStack(
            canvas = canvas,
            identity = identity,
            x = gap * 1.35f,
            centerY = barTop - gap * 0.55f - blockHeight / 2f,
            maxWidth = maxWidth,
            barHeight = barHeight,
            withPill = true
        )
    }

    private fun drawStack(
        canvas: Canvas,
        identity: HudVehicleIdentity,
        x: Float,
        centerY: Float,
        maxWidth: Float,
        barHeight: Float,
        withPill: Boolean
    ) {
        if (maxWidth < 8f) return
        titlePaint.textSize = barHeight * HudBottomBar.NAME_TEXT
        specsPaint.textSize = barHeight * 0.145f
        val title = ellipsize(identity.title, titlePaint, maxWidth)
        val specs = ellipsize(identity.specs, specsPaint, maxWidth)
        val titleH = if (title.isNotEmpty()) textHeight(titlePaint) else 0f
        val specsH = if (specs.isNotEmpty()) textHeight(specsPaint) else 0f
        val lineGap = if (titleH > 0f && specsH > 0f) barHeight * 0.03f else 0f
        val blockH = titleH + lineGap + specsH
        val padX = barHeight * 0.10f
        val padY = barHeight * 0.06f
        var top = centerY - blockH / 2f
        if (withPill) {
            val textW = max(
                if (title.isNotEmpty()) titlePaint.measureText(title) else 0f,
                if (specs.isNotEmpty()) specsPaint.measureText(specs) else 0f
            )
            pillRect.set(
                x - padX,
                top - padY,
                x + textW + padX,
                top + blockH + padY
            )
            val radius = pillRect.height() / 2f
            canvas.drawRoundRect(pillRect, radius, radius, pillPaint)
            canvas.drawRoundRect(pillRect, radius, radius, pillStrokePaint)
        }
        if (title.isNotEmpty()) {
            canvas.drawText(title, x, top - titlePaint.fontMetrics.ascent, titlePaint)
            top += titleH + lineGap
        }
        if (specs.isNotEmpty()) {
            canvas.drawText(specs, x, top - specsPaint.fontMetrics.ascent, specsPaint)
        }
    }

    private fun stackHeight(identity: HudVehicleIdentity, barHeight: Float): Float {
        titlePaint.textSize = barHeight * HudBottomBar.NAME_TEXT
        specsPaint.textSize = barHeight * 0.145f
        val titleH = if (identity.title.isNotBlank()) textHeight(titlePaint) else 0f
        val specsH = if (identity.specs.isNotBlank()) textHeight(specsPaint) else 0f
        val lineGap = if (titleH > 0f && specsH > 0f) barHeight * 0.03f else 0f
        return titleH + lineGap + specsH
    }

    private fun ellipsize(value: String, paint: TextPaint, maxWidth: Float): String {
        if (value.isBlank() || maxWidth <= 0f) return ""
        return TextUtils.ellipsize(value, paint, maxWidth, TextUtils.TruncateAt.END).toString()
    }

    private fun textHeight(paint: Paint): Float {
        val metrics = paint.fontMetrics
        return metrics.descent - metrics.ascent
    }
}
