package com.revix.app.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.revix.app.drag.DragRunVideoSettings
import java.io.ByteArrayOutputStream
import kotlin.math.min

class PhoneDualCameraSession(
    val preview: Preview,
    val videoCapture: VideoCapture<Recorder>,
    private val overlayEffect: OverlayEffect,
    private val analysis: ImageAnalysis,
    private val overlayThread: HandlerThread
) {
    fun release() {
        runCatching { analysis.clearAnalyzer() }
        runCatching { overlayEffect.clearOnDrawListener() }
        runCatching { overlayEffect.close() }
        overlayThread.quitSafely()
    }
}

object PhoneDualCameraBinder {
    private const val TAG = "PhoneDualCam"

    fun bind(
        context: Context,
        owner: LifecycleOwner,
        provider: ProcessCameraProvider,
        previewView: PreviewView,
        rotation: Int,
        lens: DragRunVideoSettings.LensOption,
        dual: Boolean,
        quality: Quality,
        fps: Int
    ): Pair<Preview, VideoCapture<Recorder>> {
        DualCameraHolder.replace(null)
        provider.unbindAll()
        val wantDual = dual && ConcurrentPhoneCameras.advertised(context, provider)
        if (wantDual) {
            val session = bindDual(
                owner = owner,
                provider = provider,
                previewView = previewView,
                rotation = rotation,
                primary = lens,
                quality = quality,
                fps = fps
            )
            if (session != null) {
                ConcurrentPhoneCameras.markSupported(context, true)
                DualCameraHolder.replace(session)
                return session.preview to session.videoCapture
            }
            ConcurrentPhoneCameras.markSupported(context, false)
            provider.unbindAll()
        } else if (
            ConcurrentPhoneCameras.advertised(context, provider) &&
            !ConcurrentPhoneCameras.isProbed(context)
        ) {
            probeDual(context, owner, provider, previewView, rotation)
            provider.unbindAll()
        }
        DualCameraHolder.replace(null)
        return bindSingle(owner, provider, previewView, rotation, lens, quality, fps)
    }

    private fun probeDual(
        context: Context,
        owner: LifecycleOwner,
        provider: ProcessCameraProvider,
        previewView: PreviewView,
        rotation: Int
    ) {
        val session = bindDual(
            owner = owner,
            provider = provider,
            previewView = previewView,
            rotation = rotation,
            primary = DragRunVideoSettings.LensOption.REAR,
            quality = Quality.SD,
            fps = 30
        )
        ConcurrentPhoneCameras.markSupported(context, session != null)
        session?.release()
    }

    private fun bindSingle(
        owner: LifecycleOwner,
        provider: ProcessCameraProvider,
        previewView: PreviewView,
        rotation: Int,
        lens: DragRunVideoSettings.LensOption,
        quality: Quality,
        fps: Int
    ): Pair<Preview, VideoCapture<Recorder>> {
        val preview = Preview.Builder()
            .setTargetRotation(rotation)
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        val recorder = recorderFor(quality)
        val selector = CameraSelector.Builder().requireLensFacing(lens.lensFacing).build()
        val video = try {
            val locked = VideoCapture.Builder(recorder)
                .setTargetRotation(rotation)
                .setTargetFrameRate(Range(fps, fps))
                .build()
            provider.bindToLifecycle(owner, selector, preview, locked)
            locked
        } catch (error: Exception) {
            Log.w(TAG, "Single camera fps lock failed, retrying", error)
            val fallback = VideoCapture.Builder(recorder)
                .setTargetRotation(rotation)
                .build()
            provider.unbindAll()
            provider.bindToLifecycle(owner, selector, preview, fallback)
            fallback
        }
        return preview to video
    }

    private fun bindDual(
        owner: LifecycleOwner,
        provider: ProcessCameraProvider,
        previewView: PreviewView,
        rotation: Int,
        primary: DragRunVideoSettings.LensOption,
        quality: Quality,
        fps: Int
    ): PhoneDualCameraSession? {
        val qualities = linkedSetOf(
            if (quality == Quality.UHD) Quality.FHD else quality,
            Quality.FHD,
            Quality.HD,
            Quality.SD
        )
        qualities.forEach { candidate ->
            listOf(true, false).forEach { lockFps ->
                val session = runCatching {
                    bindDualOnce(
                        owner = owner,
                        provider = provider,
                        previewView = previewView,
                        rotation = rotation,
                        primary = primary,
                        quality = candidate,
                        fps = fps,
                        lockFps = lockFps
                    )
                }.onFailure { error ->
                    Log.w(TAG, "Dual bind failed q=$candidate fpsLock=$lockFps", error)
                    runCatching { provider.unbindAll() }
                }.getOrNull()
                if (session != null) return session
            }
        }
        return null
    }

    private fun bindDualOnce(
        owner: LifecycleOwner,
        provider: ProcessCameraProvider,
        previewView: PreviewView,
        rotation: Int,
        primary: DragRunVideoSettings.LensOption,
        quality: Quality,
        fps: Int,
        lockFps: Boolean
    ): PhoneDualCameraSession {
        provider.unbindAll()
        val overlayThread = HandlerThread("revix-pip-overlay").apply { start() }
        val overlayHandler = Handler(overlayThread.looper)
        val pipFrames = PipFrameBuffer()
        val overlayEffect = OverlayEffect(
            CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
            0,
            overlayHandler
        ) { error -> Log.w(TAG, "Overlay effect error", error) }
        overlayEffect.setOnDrawListener { frame ->
            pipFrames.draw(
                canvas = frame.overlayCanvas,
                bufferSize = frame.size,
                crop = frame.cropRect,
                rotationDegrees = frame.rotationDegrees,
                mirrored = frame.isMirroring
            )
            true
        }
        val preview = Preview.Builder()
            .setTargetRotation(rotation)
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        val recorder = recorderFor(quality)
        val videoBuilder = VideoCapture.Builder(recorder).setTargetRotation(rotation)
        if (lockFps) {
            videoBuilder.setTargetFrameRate(Range(fps, fps))
        }
        val video = videoBuilder.build()
        val analysis = ImageAnalysis.Builder()
            .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
        analysis.setAnalyzer(overlayThread.threadExecutor()) { image ->
            try {
                pipFrames.update(image, true)
            } finally {
                image.close()
            }
        }
        val primaryGroup = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(video)
            .addEffect(overlayEffect)
            .build()
        val pipGroup = UseCaseGroup.Builder()
            .addUseCase(analysis)
            .build()
        val primaryConfig = ConcurrentCamera.SingleCameraConfig(
            CameraSelector.Builder().requireLensFacing(primary.lensFacing).build(),
            primaryGroup,
            owner
        )
        val pipConfig = ConcurrentCamera.SingleCameraConfig(
            CameraSelector.Builder()
                .requireLensFacing(ConcurrentPhoneCameras.otherLens(primary).lensFacing)
                .build(),
            pipGroup,
            owner
        )
        try {
            provider.bindToLifecycle(listOf(primaryConfig, pipConfig))
        } catch (error: Exception) {
            runCatching { analysis.clearAnalyzer() }
            runCatching { overlayEffect.clearOnDrawListener() }
            runCatching { overlayEffect.close() }
            overlayThread.quitSafely()
            throw error
        }
        return PhoneDualCameraSession(
            preview = preview,
            videoCapture = video,
            overlayEffect = overlayEffect,
            analysis = analysis,
            overlayThread = overlayThread
        )
    }

    private fun recorderFor(quality: Quality): Recorder {
        val qualities = linkedSetOf(quality, Quality.FHD, Quality.HD, Quality.SD).toList()
        return Recorder.Builder()
            .setQualitySelector(
                QualitySelector.fromOrderedList(
                    qualities,
                    FallbackStrategy.lowerQualityOrHigherThan(quality)
                )
            )
            .build()
    }

    private fun HandlerThread.threadExecutor(): java.util.concurrent.Executor {
        val handler = Handler(looper)
        return java.util.concurrent.Executor { handler.post(it) }
    }
}

internal object DualCameraHolder {
    @Volatile
    private var session: PhoneDualCameraSession? = null

    fun replace(next: PhoneDualCameraSession?) {
        session?.release()
        session = next
    }

    fun isActive(): Boolean = session != null

    fun release() {
        replace(null)
    }
}

private class PipFrameBuffer {
    private val lock = Any()
    private var bitmap: Bitmap? = null
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 6, 8, 12) }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 230, 236, 242)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val dest = RectF()
    private val src = Rect()
    private val roundPath = Path()

    fun update(image: ImageProxy, mirror: Boolean) {
        val decoded = image.toSensorBitmap(mirror) ?: return
        synchronized(lock) {
            bitmap?.recycle()
            bitmap = decoded
        }
    }

    fun draw(
        canvas: Canvas,
        bufferSize: Size,
        crop: Rect,
        rotationDegrees: Int,
        mirrored: Boolean
    ) {
        val frame = synchronized(lock) { bitmap }
        val usedCrop = if (crop.width() <= 1 || crop.height() <= 1) {
            Rect(0, 0, bufferSize.width, bufferSize.height)
        } else {
            crop
        }
        dest.set(DualCameraPipLayout.pipInBuffer(usedCrop, rotationDegrees, mirrored))
        if (dest.width() < 8f || dest.height() < 8f) return
        val radius = min(dest.width(), dest.height()) * 0.08f
        strokePaint.strokeWidth = min(dest.width(), dest.height()) * 0.018f
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawRoundRect(dest, radius, radius, fillPaint)
        if (frame != null) {
            val side = min(frame.width, frame.height)
            src.set(
                (frame.width - side) / 2,
                (frame.height - side) / 2,
                (frame.width - side) / 2 + side,
                (frame.height - side) / 2 + side
            )
            roundPath.reset()
            roundPath.addRoundRect(dest, radius, radius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(roundPath)
            canvas.drawBitmap(frame, src, dest, bitmapPaint)
            canvas.restore()
        }
        canvas.drawRoundRect(dest, radius, radius, strokePaint)
    }
}

private fun ImageProxy.toSensorBitmap(mirror: Boolean): Bitmap? {
    val nv21 = toNv21() ?: return null
    val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val jpeg = ByteArrayOutputStream()
    if (!yuv.compressToJpeg(Rect(0, 0, width, height), 70, jpeg)) return null
    val bytes = jpeg.toByteArray()
    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    if (mirror) {
        val matrix = Matrix().apply { preScale(-1f, 1f) }
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    return bitmap
}

private fun ImageProxy.toNv21(): ByteArray? {
    if (format != ImageFormat.YUV_420_888) return null
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]
    val ySize = width * height
    val nv21 = ByteArray(ySize + ySize / 2)
    val yBuf = yPlane.buffer.duplicate().apply { rewind() }
    val yRowStride = yPlane.rowStride
    val yPixelStride = yPlane.pixelStride
    var output = 0
    for (row in 0 until height) {
        var input = row * yRowStride
        for (col in 0 until width) {
            nv21[output++] = yBuf.get(input)
            input += yPixelStride
        }
    }
    val chromaHeight = height / 2
    val chromaWidth = width / 2
    val vRowStride = vPlane.rowStride
    val uRowStride = uPlane.rowStride
    val vPixelStride = vPlane.pixelStride
    val uPixelStride = uPlane.pixelStride
    val vBuf = vPlane.buffer.duplicate().apply { rewind() }
    val uBuf = uPlane.buffer.duplicate().apply { rewind() }
    for (row in 0 until chromaHeight) {
        var vInput = row * vRowStride
        var uInput = row * uRowStride
        for (col in 0 until chromaWidth) {
            nv21[output++] = vBuf.get(vInput)
            nv21[output++] = uBuf.get(uInput)
            vInput += vPixelStride
            uInput += uPixelStride
        }
    }
    return nv21
}
