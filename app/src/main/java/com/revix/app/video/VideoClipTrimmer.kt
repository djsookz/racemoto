package com.revix.app.video

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max

object VideoClipTrimmer {
    data class Result(
        val file: File,
        val actualStartMs: Long
    )

    fun trim(
        source: File,
        output: File,
        startMs: Long,
        endMs: Long
    ): Result? {
        if (!source.exists() || source.length() <= 0L) return null
        val durationMs = durationMs(source) ?: return null
        val start = startMs.coerceIn(0L, durationMs)
        val end = when {
            endMs <= 0L -> durationMs
            else -> endMs.coerceIn((start + 200L).coerceAtMost(durationMs), durationMs)
        }
        if (start <= 80L && end >= durationMs - 80L) {
            return Result(source, 0L)
        }

        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        return try {
            extractor = MediaExtractor().apply { setDataSource(source.absolutePath) }
            val selectedTrackIndexes = mutableListOf<Int>()
            val selectedTrackFormats = mutableMapOf<Int, android.media.MediaFormat>()
            var sourceVideoTrackIndex = -1
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    selectedTrackIndexes += index
                    selectedTrackFormats[index] = format
                    if (mime.startsWith("video/") && sourceVideoTrackIndex < 0) {
                        sourceVideoTrackIndex = index
                    }
                }
            }
            if (sourceVideoTrackIndex < 0 || selectedTrackIndexes.isEmpty()) return null

            extractor.selectTrack(sourceVideoTrackIndex)
            extractor.seekTo(start * 1_000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val actualStartUs = extractor.sampleTime.coerceAtLeast(0L)
            extractor.unselectTrack(sourceVideoTrackIndex)
            val endUs = end * 1_000L

            if (output.exists()) output.delete()
            output.parentFile?.mkdirs()
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackIndexMap = mutableMapOf<Int, Int>()
            var maxInputSize = 262_144
            selectedTrackIndexes.forEach { sourceTrackIndex ->
                val format = selectedTrackFormats[sourceTrackIndex] ?: return@forEach
                extractor.selectTrack(sourceTrackIndex)
                trackIndexMap[sourceTrackIndex] = muxer.addTrack(format)
                if (format.containsKey(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    maxInputSize = max(
                        maxInputSize,
                        format.getInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)
                    )
                }
            }
            val orientation = orientationHintDegrees(source)
            if (orientation != 0) {
                muxer.setOrientationHint(orientation)
            }
            extractor.seekTo(actualStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            muxer.start()

            val buffer = ByteBuffer.allocate(maxInputSize.coerceAtLeast(262_144))
            val bufferInfo = MediaCodec.BufferInfo()
            var wroteSample = false
            while (true) {
                val sourceTrackIndex = extractor.sampleTrackIndex
                if (sourceTrackIndex < 0) break
                val targetTrackIndex = trackIndexMap[sourceTrackIndex]
                if (targetTrackIndex == null) {
                    extractor.advance()
                    continue
                }
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) break
                if (sampleTimeUs < actualStartUs) {
                    extractor.advance()
                    continue
                }
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break
                bufferInfo.presentationTimeUs = sampleTimeUs - actualStartUs
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(targetTrackIndex, buffer, bufferInfo)
                wroteSample = true
                extractor.advance()
            }
            if (!wroteSample || !output.exists() || output.length() <= 0L) {
                runCatching { if (output.exists()) output.delete() }
                return null
            }
            Result(output, actualStartUs / 1_000L)
        } catch (_: Exception) {
            runCatching { if (output.exists()) output.delete() }
            null
        } finally {
            try {
                muxer?.stop()
            } catch (_: Exception) {
            }
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            try {
                extractor?.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun durationMs(source: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(source.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun orientationHintDegrees(source: File): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(source.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?.let { rotation ->
                    when (((rotation % 360) + 360) % 360) {
                        90, 180, 270 -> ((rotation % 360) + 360) % 360
                        else -> 0
                    }
                }
                ?: 0
        } catch (_: Exception) {
            0
        } finally {
            runCatching { retriever.release() }
        }
    }
}
