package com.revix.app.drag

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.revix.app.DragAttempt
import com.revix.app.R
import com.revix.app.video.HudOffscreenPass
import java.io.File
import java.util.Locale
import kotlin.math.min

@OptIn(UnstableApi::class)
class DragAttemptHudExporter(context: Context) {

    data class Request(
        val inputUri: Uri,
        val outputFile: File,
        val alphaOutputFile: File? = null,
        val attempt: DragAttempt,
        val mode: MeasurementMode,
        val hudOnly: Boolean = true
    )

    private data class VideoSpec(val displayWidth: Int, val displayHeight: Int)
    private data class VideoProfile(val mimeType: String, val bitrate: Int)

    private val appContext = context.applicationContext
    private val logo by lazy {
        runCatching { BitmapFactory.decodeResource(appContext.resources, R.drawable.revix_logo) }.getOrNull()
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var exportThread: HandlerThread? = null
    private var transformer: Transformer? = null
    private var isFinishing = false
    private var hudPassIndex = 0
    private var dualHudPass = false
    private var progressPollRunnable: Runnable? = null

    fun export(
        request: Request,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit,
        onProgress: (Int) -> Unit = {}
    ) {
        cancel()
        isFinishing = false
        val thread = HandlerThread("DragAttemptHudExport").apply { start() }
        exportThread = thread
        hudPassIndex = 0
        dualHudPass = request.hudOnly && request.alphaOutputFile != null
        val sourceSpec = resolveSourceVideoSpec(request.inputUri)
        Handler(thread.looper).post {
            startExport(request, sourceSpec, onSuccess, onError, onProgress)
        }
    }

    fun cancel() {
        val thread = exportThread ?: return
        Handler(thread.looper).post {
            runCatching { transformer?.cancel() }
            cleanupThread()
        }
    }

    private fun startExport(
        request: Request,
        sourceSpec: VideoSpec?,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit,
        onProgress: (Int) -> Unit
    ) {
        try {
            val window = DragAttemptMetrics.finishedVideoWindow(request.attempt, request.mode)
            val overlayAttempt = if (window != null) {
                request.attempt.copy(videoT0OffsetMs = window.t0InClipMs)
            } else {
                request.attempt
            }
            val pass = hudPassFor(request)
            val overlay = DragHudOverlay(appContext, overlayAttempt, request.mode, logo, pass)
            val outputSpec = resolveOutputSpec(sourceSpec)
            val profile = resolveVideoProfile(outputSpec)
            if (profile == null) {
                finishError(IllegalStateException("No compatible H.264 encoder"), onError)
                return
            }
            val outputFile = if (pass == HudOffscreenPass.Mode.MATTE) {
                request.alphaOutputFile ?: request.outputFile
            } else {
                request.outputFile
            }
            if (outputFile.exists()) outputFile.delete()
            val mediaItem = MediaItem.Builder()
                .setUri(request.inputUri)
                .also { builder -> applyClip(builder, window) }
                .build()
            val edited = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(request.hudOnly)
                .setEffects(Effects(emptyList(), buildEffects(overlay, sourceSpec, outputSpec)))
                .build()
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (pass == HudOffscreenPass.Mode.FILL && request.alphaOutputFile != null) {
                        hudPassIndex = 1
                        transformer = null
                        startExport(request, sourceSpec, onSuccess, onError, onProgress)
                        return
                    }
                    finishSuccess(request.outputFile, onSuccess)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    request.outputFile.delete()
                    request.alphaOutputFile?.delete()
                    transformer = null
                    finishError(exportException, onError)
                }
            }
            transformer = Transformer.Builder(appContext)
                .setLooper(Looper.myLooper() ?: exportThread?.looper ?: Looper.getMainLooper())
                .setVideoMimeType(profile.mimeType)
                .setPortraitEncodingEnabled(true)
                .addListener(listener)
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(appContext)
                        .setRequestedVideoEncoderSettings(
                            VideoEncoderSettings.Builder()
                                .setBitrate(profile.bitrate)
                                .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                                .setiFrameIntervalSeconds(2f)
                                .build()
                        )
                        .setEnableFallback(true)
                        .build()
                )
                .build()
            transformer?.start(edited, outputFile.absolutePath)
            if (hudPassIndex == 0) startProgressPolling(onProgress)
        } catch (error: Throwable) {
            request.outputFile.delete()
            request.alphaOutputFile?.delete()
            transformer = null
            finishError(error, onError)
        }
    }

    private fun buildEffects(
        overlay: DragHudOverlay,
        sourceSpec: VideoSpec?,
        outputSpec: VideoSpec
    ): List<Effect> {
        val overlayEffect = OverlayEffect(listOf(overlay))
        val needsScale = sourceSpec == null ||
            sourceSpec.displayWidth != outputSpec.displayWidth ||
            sourceSpec.displayHeight != outputSpec.displayHeight
        if (!needsScale) return listOf(overlayEffect)
        return listOf(
            Presentation.createForWidthAndHeight(
                outputSpec.displayWidth,
                outputSpec.displayHeight,
                Presentation.LAYOUT_SCALE_TO_FIT
            ),
            overlayEffect
        )
    }

    private fun resolveSourceVideoSpec(inputUri: Uri): VideoSpec? {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(appContext, inputUri)
                val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                    ?: return@runCatching null
                val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    ?: return@runCatching null
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val quarter = rotation == 90 || rotation == 270
                VideoSpec(
                    displayWidth = if (quarter) rawHeight else rawWidth,
                    displayHeight = if (quarter) rawWidth else rawHeight
                )
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }

    private fun resolveOutputSpec(sourceSpec: VideoSpec?): VideoSpec {
        val width = sourceSpec?.displayWidth ?: 1080
        val height = sourceSpec?.displayHeight ?: 1920
        if (width <= 0 || height <= 0) return VideoSpec(1080, 1920)
        val shortEdge = min(width, height)
        if (shortEdge <= EXPORT_MAX_SHORT_EDGE) {
            return VideoSpec(evenPx(width), evenPx(height))
        }
        val scale = EXPORT_MAX_SHORT_EDGE.toFloat() / shortEdge.toFloat()
        return VideoSpec(evenPx((width * scale).toInt()), evenPx((height * scale).toInt()))
    }

    private fun applyClip(
        mediaItemBuilder: MediaItem.Builder,
        window: DragAttemptMetrics.FinishedVideoWindow?
    ) {
        if (window == null) return
        val clipping = MediaItem.ClippingConfiguration.Builder()
        if (window.startMs > 0L) {
            clipping.setStartPositionMs(window.startMs)
        }
        if (window.endMs > window.startMs) {
            clipping.setEndPositionMs(window.endMs)
        }
        mediaItemBuilder.setClippingConfiguration(clipping.build())
    }

    private fun evenPx(value: Int): Int = (value / 2 * 2).coerceAtLeast(2)

    private fun resolveVideoProfile(outputSpec: VideoSpec): VideoProfile? {
        val bitrate = if (min(outputSpec.displayWidth, outputSpec.displayHeight) >= EXPORT_MAX_SHORT_EDGE) {
            10_000_000
        } else {
            6_000_000
        }
        val hardware = findEncoder(outputSpec, requireHardware = true)
        val software = if (hardware == null) findEncoder(outputSpec, requireHardware = false) else hardware
        return software?.let { VideoProfile(MimeTypes.VIDEO_H264, bitrate) }
    }

    private fun findEncoder(outputSpec: VideoSpec, requireHardware: Boolean): MediaCodecInfo? {
        return runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { codec ->
                codec.isEncoder &&
                    codec.supportedTypes.any { it.equals(MimeTypes.VIDEO_H264, ignoreCase = true) } &&
                    (!requireHardware || isHardwareCodec(codec)) &&
                    supportsSize(codec, outputSpec)
            }
        }.getOrNull()
    }

    private fun supportsSize(codec: MediaCodecInfo, spec: VideoSpec): Boolean {
        val caps = runCatching { codec.getCapabilitiesForType(MimeTypes.VIDEO_H264).videoCapabilities }.getOrNull()
            ?: return false
        return caps.isSizeSupported(spec.displayWidth, spec.displayHeight) ||
            caps.isSizeSupported(spec.displayHeight, spec.displayWidth)
    }

    private fun isHardwareCodec(codec: MediaCodecInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            codec.isHardwareAccelerated && !codec.isSoftwareOnly
        } else {
            val name = codec.name.lowercase(Locale.US)
            !name.startsWith("omx.google.") && !name.startsWith("c2.android.") && !name.startsWith("c2.google.")
        }
    }

    private fun startProgressPolling(onProgress: (Int) -> Unit) {
        val thread = exportThread ?: return
        val holder = ProgressHolder()
        val runnable = object : Runnable {
            override fun run() {
                if (isFinishing) return
                val current = transformer ?: return
                val state = runCatching { current.getProgress(holder) }.getOrNull() ?: return
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val raw = holder.progress.coerceIn(0, 99)
                    val mapped = if (dualHudPass) {
                        (hudPassIndex * 50) + (raw / 2)
                    } else {
                        raw
                    }
                    mainHandler.post { onProgress(mapped.coerceIn(0, 99)) }
                }
                Handler(thread.looper).postDelayed(this, 500L)
            }
        }
        progressPollRunnable = runnable
        Handler(thread.looper).postDelayed(runnable, 500L)
    }

    private fun finishSuccess(outputFile: File, onSuccess: (File) -> Unit) {
        if (markFinished()) return
        cleanupThread()
        mainHandler.post { onSuccess(outputFile) }
    }

    private fun finishError(error: Throwable, onError: (Throwable) -> Unit) {
        if (markFinished()) return
        cleanupThread()
        mainHandler.post { onError(error) }
    }

    @Synchronized
    private fun markFinished(): Boolean {
        if (isFinishing) return true
        isFinishing = true
        return false
    }

    private fun cleanupThread() {
        progressPollRunnable = null
        transformer = null
        exportThread?.quitSafely()
        exportThread = null
    }

    private fun hudPassFor(request: Request): HudOffscreenPass.Mode {
        if (!request.hudOnly) return HudOffscreenPass.Mode.OVERLAY
        return if (hudPassIndex == 0) HudOffscreenPass.Mode.FILL else HudOffscreenPass.Mode.MATTE
    }

    private class DragHudOverlay(
        context: Context,
        attempt: DragAttempt,
        mode: MeasurementMode,
        logo: android.graphics.Bitmap?,
        private val pass: HudOffscreenPass.Mode
    ) : CanvasOverlay(true) {
        private val renderer = DragAttemptHudRenderer(context, attempt, mode, logo)
        private val offscreen = HudOffscreenPass()
        private var overlayWidth = 0f
        private var overlayHeight = 0f

        override fun configure(videoSize: Size) {
            super.configure(videoSize)
            overlayWidth = videoSize.width.toFloat()
            overlayHeight = videoSize.height.toFloat()
        }

        override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
            offscreen.draw(canvas, pass, overlayWidth, overlayHeight) { target ->
                renderer.draw(target, overlayWidth, overlayHeight, presentationTimeUs / 1000L)
            }
        }
    }

    companion object {
        private const val EXPORT_MAX_SHORT_EDGE = 1080
    }
}
