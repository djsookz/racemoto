package com.example.clinometer.main.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

object NavigationMarkerImageFactory {

    fun createOrangeTopImage(density: Float): Bitmap {
        val size = (32 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val centerX = size / 2f
        val centerY = size / 2f
        val radius = 12f * density

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF6020")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, radius, fillPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
        }
        canvas.drawCircle(centerX, centerY, radius, strokePaint)

        return bitmap
    }

    fun createOrangeBearingImage(density: Float): Bitmap {
        val size = (32 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val centerX = size / 2f
        val centerY = size / 2f
        val radius = 12f * density

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF6020")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, radius, fillPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
        }
        canvas.drawCircle(centerX, centerY, radius, strokePaint)

        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val path = Path()
        val arrowWidth = 4f * density
        val arrowHeight = 8f * density
        val arrowTopY = centerY - radius + 2f * density

        path.moveTo(centerX, arrowTopY)
        path.lineTo(centerX - arrowWidth, arrowTopY + arrowHeight)
        path.lineTo(centerX + arrowWidth, arrowTopY + arrowHeight)
        path.close()
        canvas.drawPath(path, arrowPaint)

        return bitmap
    }

    fun createOrangeShadowImage(density: Float): Bitmap {
        val size = (32 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val centerX = size / 2f
        val centerY = size / 2f
        val radiusX = 14f * density
        val radiusY = 6f * density

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 0, 0, 0)
            style = Paint.Style.FILL
        }

        val rect = RectF(
            centerX - radiusX,
            centerY - radiusY,
            centerX + radiusX,
            centerY + radiusY
        )
        canvas.drawOval(rect, shadowPaint)

        return bitmap
    }
}
