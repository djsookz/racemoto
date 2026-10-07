package com.revix.app.track

import android.util.Log
import com.revix.app.GeoPoint
import com.revix.app.LapData
import com.revix.app.RoutePoint
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Append-only binary writer for one live lap.
 * Keeps RaceBox/phone GPS + IMU at full rate on disk without growing ArrayLists in RAM.
 */
class TrackLapStreamWriter(
    private val dir: File,
    private val baseName: String
) {
    enum class ReadMode {
        /** Full telemetry for charts / compare / export. */
        FULL,
        /** Route + lean/timestamps only — enough for map open + lean peak enrich. */
        MAP
    }

    data class LapBinMeta(
        val lapNumber: Int,
        val startTime: Long,
        val endTime: Long,
        val maxSpeedKmh: Float = 0f
    )

    companion object {
        private const val TAG = "TrackLapStreamWriter"
        private const val MAGIC = 0x524C4150 // "RLAP"
        private const val VERSION = 1
        private const val REC_ROUTE: Byte = 1
        private const val REC_TELEM: Byte = 2
        private const val REC_GYRO: Byte = 3

        fun readFromFile(file: File, meta: LapBinMeta, mode: ReadMode = ReadMode.FULL): LapData {
            if (!file.exists() || file.length() < 8L) {
                return LapData(
                    lapNumber = meta.lapNumber,
                    startTime = meta.startTime,
                    endTime = meta.endTime
                )
            }

            val speedData = ArrayList<Float>(256)
            val routePoints = ArrayList<RoutePoint>(256)
            val accelerationData = ArrayList<Float>(if (mode == ReadMode.FULL) 512 else 0)
            val leanAngleData = ArrayList<Float>(256)
            val displayLeanAngleData = ArrayList<Float>(if (mode == ReadMode.FULL) 256 else 0)
            val longitudinalGData = ArrayList<Float>(if (mode == ReadMode.FULL) 256 else 0)
            val lateralGData = ArrayList<Float>(if (mode == ReadMode.FULL) 256 else 0)
            val maxBrakingData = ArrayList<Float>(if (mode == ReadMode.FULL) 256 else 0)
            val maxAccelData = ArrayList<Float>(if (mode == ReadMode.FULL) 256 else 0)
            val maxCorneringLeftData = ArrayList<Float>(if (mode == ReadMode.FULL) 256 else 0)
            val maxCorneringRightData = ArrayList<Float>(if (mode == ReadMode.FULL) 256 else 0)
            val maxResultGData = ArrayList<Float>(if (mode == ReadMode.FULL) 256 else 0)
            val timestamps = ArrayList<Long>(256)
            val gyroscopeData = ArrayList<Float>(if (mode == ReadMode.FULL) 256 else 0)

            try {
                DataInputStream(BufferedInputStream(FileInputStream(file), 64 * 1024)).use { input ->
                    val magic = input.readInt()
                    val version = input.readInt()
                    if (magic != MAGIC || version != VERSION) {
                        Log.e(TAG, "Bad stream header magic=$magic version=$version")
                        return LapData(
                            lapNumber = meta.lapNumber,
                            startTime = meta.startTime,
                            endTime = meta.endTime
                        )
                    }
                    while (true) {
                        val type = try {
                            input.readByte()
                        } catch (_: java.io.EOFException) {
                            break
                        }
                        when (type) {
                            REC_ROUTE -> {
                                val lat = input.readDouble()
                                val lon = input.readDouble()
                                val speed = input.readFloat()
                                val angle = input.readFloat()
                                val ts = input.readLong()
                                val abs = input.readLong()
                                speedData.add(speed)
                                routePoints.add(
                                    RoutePoint(
                                        geoPoint = GeoPoint(lat, lon),
                                        speed = speed,
                                        angle = angle,
                                        timestamp = ts,
                                        absoluteTime = abs
                                    )
                                )
                            }
                            REC_TELEM -> {
                                val ax = input.readFloat()
                                val ay = input.readFloat()
                                val az = input.readFloat()
                                val lean = input.readFloat()
                                val displayLean = input.readFloat()
                                val longG = input.readFloat()
                                val latG = input.readFloat()
                                val maxBrake = input.readFloat()
                                val maxAccel = input.readFloat()
                                val maxCornL = input.readFloat()
                                val maxCornR = input.readFloat()
                                val maxResult = input.readFloat()
                                val ts = input.readLong()
                                when (mode) {
                                    ReadMode.FULL -> {
                                        accelerationData.add(ax)
                                        accelerationData.add(ay)
                                        accelerationData.add(az)
                                        leanAngleData.add(lean)
                                        displayLeanAngleData.add(displayLean)
                                        longitudinalGData.add(longG)
                                        lateralGData.add(latG)
                                        maxBrakingData.add(maxBrake)
                                        maxAccelData.add(maxAccel)
                                        maxCorneringLeftData.add(maxCornL)
                                        maxCorneringRightData.add(maxCornR)
                                        maxResultGData.add(maxResult)
                                        timestamps.add(ts)
                                    }
                                    ReadMode.MAP -> {
                                        leanAngleData.add(lean)
                                        timestamps.add(ts)
                                    }
                                }
                            }
                            REC_GYRO -> {
                                val gx = input.readFloat()
                                val gy = input.readFloat()
                                val gz = input.readFloat()
                                if (mode == ReadMode.FULL) {
                                    gyroscopeData.add(gx)
                                    gyroscopeData.add(gy)
                                    gyroscopeData.add(gz)
                                }
                            }
                            else -> {
                                Log.e(TAG, "Unknown record type — aborting read")
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed reading lap stream ${file.name}", e)
            }

            return LapData(
                lapNumber = meta.lapNumber,
                startTime = meta.startTime,
                endTime = meta.endTime,
                speedData = speedData,
                accelerationData = accelerationData,
                leanAngleData = leanAngleData,
                gyroscopeData = gyroscopeData,
                routePoints = routePoints,
                longitudinalGData = longitudinalGData,
                lateralGData = lateralGData,
                timestamps = timestamps,
                displayLeanAngleData = displayLeanAngleData,
                maxBrakingData = maxBrakingData,
                maxAccelData = maxAccelData,
                maxCorneringLeftData = maxCorneringLeftData,
                maxCorneringRightData = maxCorneringRightData,
                maxResultGData = maxResultGData
            )
        }
    }

    private val file = File(dir, "$baseName.bin")
    private var out: DataOutputStream? = null
    private var routeCount = 0
    private var telemCount = 0
    private var gyroTripletCount = 0
    private var maxSpeedKmh = 0f
    private var closed = false

    init {
        if (!dir.exists()) dir.mkdirs()
        if (file.exists()) file.delete()
        out = DataOutputStream(BufferedOutputStream(FileOutputStream(file), 64 * 1024))
        out!!.writeInt(MAGIC)
        out!!.writeInt(VERSION)
    }

    val routePointCount: Int get() = routeCount
    val maxRouteSpeedKmh: Float get() = maxSpeedKmh
    val streamFile: File get() = file

    fun hasSamples(): Boolean = routeCount > 0 || telemCount > 0 || gyroTripletCount > 0

    fun appendRoutePoint(
        latitude: Double,
        longitude: Double,
        speedKmh: Float,
        angle: Float,
        timestampMs: Long,
        absoluteTimeMs: Long
    ) {
        val stream = out ?: return
        if (closed) return
        stream.writeByte(REC_ROUTE.toInt())
        stream.writeDouble(latitude)
        stream.writeDouble(longitude)
        stream.writeFloat(speedKmh)
        stream.writeFloat(angle)
        stream.writeLong(timestampMs)
        stream.writeLong(absoluteTimeMs)
        routeCount++
        if (speedKmh.isFinite() && speedKmh > maxSpeedKmh) {
            maxSpeedKmh = speedKmh
        }
    }

    fun appendTelemetry(
        ax: Float,
        ay: Float,
        az: Float,
        lean: Float,
        displayLean: Float,
        longG: Float,
        latG: Float,
        maxBrake: Float,
        maxAccel: Float,
        maxCornL: Float,
        maxCornR: Float,
        maxResult: Float,
        timestampMs: Long
    ) {
        val stream = out ?: return
        if (closed) return
        stream.writeByte(REC_TELEM.toInt())
        stream.writeFloat(ax)
        stream.writeFloat(ay)
        stream.writeFloat(az)
        stream.writeFloat(lean)
        stream.writeFloat(displayLean)
        stream.writeFloat(longG)
        stream.writeFloat(latG)
        stream.writeFloat(maxBrake)
        stream.writeFloat(maxAccel)
        stream.writeFloat(maxCornL)
        stream.writeFloat(maxCornR)
        stream.writeFloat(maxResult)
        stream.writeLong(timestampMs)
        telemCount++
    }

    fun appendGyro(gx: Float, gy: Float, gz: Float) {
        val stream = out ?: return
        if (closed) return
        stream.writeByte(REC_GYRO.toInt())
        stream.writeFloat(gx)
        stream.writeFloat(gy)
        stream.writeFloat(gz)
        gyroTripletCount++
    }

    /** Flush buffered bytes so a crash mid-lap loses as little as possible. */
    fun flushQuietly() {
        try {
            out?.flush()
        } catch (_: Exception) {
        }
    }

    /**
     * Close the live writer and move the `.bin` to [targetBin] without materializing LapData in RAM.
     */
    fun sealToDurableFile(targetBin: File): Boolean {
        closeWriter()
        if (!file.exists() || file.length() < 8L) {
            deleteBinQuietly()
            return false
        }
        try {
            targetBin.parentFile?.mkdirs()
            if (targetBin.exists()) targetBin.delete()
            if (!file.renameTo(targetBin)) {
                file.copyTo(targetBin, overwrite = true)
                file.delete()
            }
            return targetBin.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Failed sealing stream to ${targetBin.name}", e)
            return false
        }
    }

    /** Materialize full LapData and delete the live stream file (legacy / one-shot snapshot). */
    fun finalizeToLapData(lapNumber: Int, startTime: Long, endTime: Long): LapData {
        closeWriter()
        val meta = LapBinMeta(lapNumber, startTime, endTime, maxSpeedKmh)
        val lap = readFromFile(file, meta, ReadMode.FULL)
        deleteBinQuietly()
        return lap
    }

    fun discard() {
        closeWriter()
        deleteBinQuietly()
    }

    private fun closeWriter() {
        if (closed) return
        closed = true
        try {
            out?.flush()
            out?.close()
        } catch (_: Exception) {
        }
        out = null
    }

    private fun deleteBinQuietly() {
        try {
            if (file.exists()) file.delete()
        } catch (_: Exception) {
        }
    }
}
