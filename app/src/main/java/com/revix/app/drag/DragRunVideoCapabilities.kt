package com.revix.app.drag

import android.hardware.camera2.CameraCharacteristics
import android.media.CamcorderProfile
import android.media.MediaCodec
import android.media.MediaRecorder
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import kotlin.math.abs

data class DragRunVideoCaps(
    val qualities: Set<DragRunVideoSettings.QualityOption>,
    val fpsByQuality: Map<DragRunVideoSettings.QualityOption, Set<Int>>,
    val lenses: Set<DragRunVideoSettings.LensOption>
) {
    fun fpsFor(quality: DragRunVideoSettings.QualityOption): Set<Int> {
        return fpsByQuality[quality] ?: setOf(30)
    }

    fun sanitize(
        quality: DragRunVideoSettings.QualityOption,
        fps: Int
    ): Pair<DragRunVideoSettings.QualityOption, Int> {
        val resolvedQuality = quality.takeIf { it in qualities }
            ?: preferredQuality()
        val allowedFps = fpsFor(resolvedQuality)
        val resolvedFps = fps.takeIf { it in allowedFps } ?: allowedFps.maxOrNull() ?: 30
        return resolvedQuality to resolvedFps
    }

    private fun preferredQuality(): DragRunVideoSettings.QualityOption {
        return listOf(
            DragRunVideoSettings.QualityOption.FHD,
            DragRunVideoSettings.QualityOption.HD,
            DragRunVideoSettings.QualityOption.UHD
        ).firstOrNull { it in qualities }
            ?: qualities.firstOrNull()
            ?: DragRunVideoSettings.QualityOption.HD
    }
}

@OptIn(ExperimentalCamera2Interop::class)
object DragRunVideoCapabilities {

    fun query(
        provider: ProcessCameraProvider,
        lens: DragRunVideoSettings.LensOption
    ): DragRunVideoCaps {
        val lenses = availableLenses(provider)
        val cameraInfo = cameraInfoFor(provider, lens)
            ?: cameraInfoFor(provider, lenses.firstOrNull() ?: DragRunVideoSettings.LensOption.REAR)
        if (cameraInfo == null) {
            return DragRunVideoCaps(
                qualities = setOf(DragRunVideoSettings.QualityOption.HD),
                fpsByQuality = mapOf(DragRunVideoSettings.QualityOption.HD to setOf(30)),
                lenses = lenses
            )
        }

        val supportedQualities = supportedQualities(cameraInfo)
        val cameraSupports60 = cameraInfo.supportedFrameRateRanges.any { it.upper >= 60 }
        val fpsByQuality = supportedQualities.associateWith { quality ->
            supportedFps(cameraInfo, quality.cameraQuality, cameraSupports60)
        }
        return DragRunVideoCaps(
            qualities = supportedQualities,
            fpsByQuality = fpsByQuality,
            lenses = lenses
        )
    }

    fun availableLenses(provider: ProcessCameraProvider): Set<DragRunVideoSettings.LensOption> {
        return DragRunVideoSettings.LensOption.entries.filter { lens ->
            cameraInfoFor(provider, lens) != null
        }.toSet().ifEmpty { setOf(DragRunVideoSettings.LensOption.REAR) }
    }

    private fun cameraInfoFor(
        provider: ProcessCameraProvider,
        lens: DragRunVideoSettings.LensOption
    ): CameraInfo? {
        val selector = CameraSelector.Builder().requireLensFacing(lens.lensFacing).build()
        return runCatching { selector.filter(provider.availableCameraInfos).firstOrNull() }.getOrNull()
    }

    private fun supportedQualities(cameraInfo: CameraInfo): Set<DragRunVideoSettings.QualityOption> {
        val capabilities = runCatching { Recorder.getVideoCapabilities(cameraInfo) }.getOrNull()
        val fromRecorder = capabilities
            ?.getSupportedQualities(DynamicRange.SDR)
            .orEmpty()
            .toSet()
        val matched = DragRunVideoSettings.QualityOption.entries.filter { option ->
            option.cameraQuality in fromRecorder ||
                capabilities?.isQualitySupported(option.cameraQuality, DynamicRange.SDR) == true
        }.toSet()
        return matched.ifEmpty { setOf(DragRunVideoSettings.QualityOption.HD) }
    }

    private fun supportedFps(
        cameraInfo: CameraInfo,
        quality: Quality,
        cameraSupports60: Boolean
    ): Set<Int> {
        val fps = linkedSetOf(30)
        if (!cameraSupports60) return fps

        val size = runCatching { QualitySelector.getResolution(cameraInfo, quality) }.getOrNull()
        val maxFps = size?.let { maxFpsForSize(cameraInfo, it) } ?: 0.0
        val profileFps = camcorderProfileFps(cameraInfo, quality)
        if (maxFps >= 59.0 || profileFps >= 60) {
            fps += 60
        }
        return fps
    }

    private fun maxFpsForSize(cameraInfo: CameraInfo, size: Size): Double {
        val map = runCatching {
            Camera2CameraInfo.from(cameraInfo)
                .getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        }.getOrNull() ?: return 0.0

        var best = 0.0
        listOf(MediaRecorder::class.java, MediaCodec::class.java).forEach { klass ->
            val outputs = runCatching { map.getOutputSizes(klass) }.getOrNull().orEmpty()
            val match = outputs.minByOrNull { candidate ->
                abs(candidate.width * candidate.height - size.width * size.height)
            } ?: return@forEach
            val duration = runCatching { map.getOutputMinFrameDuration(klass, match) }.getOrNull() ?: 0L
            if (duration > 0L) {
                best = maxOf(best, 1_000_000_000.0 / duration)
            }
        }
        return best
    }

    private fun camcorderProfileFps(cameraInfo: CameraInfo, quality: Quality): Int {
        val profileQuality = when (quality) {
            Quality.UHD -> CamcorderProfile.QUALITY_2160P
            Quality.FHD -> CamcorderProfile.QUALITY_1080P
            Quality.HD -> CamcorderProfile.QUALITY_720P
            else -> return 0
        }
        val cameraId = runCatching { Camera2CameraInfo.from(cameraInfo).cameraId }.getOrNull() ?: return 0
        val numericId = cameraId.toIntOrNull() ?: return 0
        return runCatching {
            if (!CamcorderProfile.hasProfile(numericId, profileQuality)) return 0
            CamcorderProfile.get(numericId, profileQuality)?.videoFrameRate ?: 0
        }.getOrDefault(0)
    }
}
