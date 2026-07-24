package com.revix.app.racebox

import android.location.Location
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * RaceBox Mini / Mini S / Micro UART-over-BLE protocol helpers (UBX framed).
 * Data message class/id: 0xFF / 0x01, payload 80 bytes.
 */
object RaceBoxProtocol {
    const val UART_SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
    const val UART_RX_UUID = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
    const val UART_TX_UUID = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

    private const val SYNC_1: Byte = 0xB5.toByte()
    private const val SYNC_2: Byte = 0x62
    private const val CLASS_RACEBOX: Byte = 0xFF.toByte()
    private const val ID_DATA: Byte = 0x01
    private const val DATA_PAYLOAD_LEN = 80

    /**
     * @param gForceX RaceBox raw: front/back (g)
     * @param gForceY RaceBox raw: right/left (g)
     * @param gForceZ RaceBox raw: up/down (g)
     * @param displayLateralG App gauge X (inertial lateral)
     * @param displayLongitudinalG App gauge Y (inertial longitudinal)
     * @param leanDegApprox Roll from gravity vector (atan2), degrees
     */
    data class Sample(
        val location: Location,
        val fixOk: Boolean,
        val fixStatus: Int,
        /** RaceBox Fix Status Flags bit0 (cleared when hAcc worse than CFG min, default 3 m). */
        val gnssFixOk: Boolean,
        val satellites: Int,
        /** Horizontal accuracy in meters from the RaceBox data message. */
        val horizontalAccuracyM: Float,
        val gForceX: Float,
        val gForceY: Float,
        val gForceZ: Float,
        val rotRateXDegPerSec: Float,
        val rotRateYDegPerSec: Float,
        val rotRateZDegPerSec: Float,
        val displayLateralG: Float,
        val displayLongitudinalG: Float,
        val dynamicG: Float,
        val leanDegApprox: Float
    )

    class PacketAssembler {
        private val buffer = ArrayList<Byte>(1024)

        fun clear() {
            buffer.clear()
        }

        fun append(chunk: ByteArray): List<ByteArray> {
            for (b in chunk) buffer.add(b)
            if (buffer.size > 8192) {
                buffer.subList(0, buffer.size - 512).clear()
            }
            val packets = ArrayList<ByteArray>()
            while (true) {
                val start = findSync()
                if (start < 0) {
                    if (buffer.isNotEmpty() && buffer.last() == SYNC_1) {
                        val keep = buffer.last()
                        buffer.clear()
                        buffer.add(keep)
                    } else {
                        buffer.clear()
                    }
                    break
                }
                if (start > 0) {
                    repeat(start) { buffer.removeAt(0) }
                }
                if (buffer.size < 6) break
                val payloadLen = u16(buffer[4], buffer[5])
                if (payloadLen > 504) {
                    buffer.removeAt(0)
                    continue
                }
                val total = 6 + payloadLen + 2
                if (buffer.size < total) break
                val packet = ByteArray(total) { i -> buffer[i] }
                repeat(total) { buffer.removeAt(0) }
                if (checksumOk(packet)) {
                    packets.add(packet)
                }
            }
            return packets
        }

        private fun findSync(): Int {
            for (i in 0 until buffer.size - 1) {
                if (buffer[i] == SYNC_1 && buffer[i + 1] == SYNC_2) return i
            }
            return -1
        }
    }

    fun parseDataMessage(packet: ByteArray): Sample? {
        if (packet.size < 8 + DATA_PAYLOAD_LEN) return null
        if (packet[0] != SYNC_1 || packet[1] != SYNC_2) return null
        if (packet[2] != CLASS_RACEBOX || packet[3] != ID_DATA) return null
        val payloadLen = u16(packet[4], packet[5])
        if (payloadLen < DATA_PAYLOAD_LEN) return null

        val bb = ByteBuffer.wrap(packet, 6, DATA_PAYLOAD_LEN).order(ByteOrder.LITTLE_ENDIAN)
        bb.int // iTOW
        bb.short // year
        bb.get(); bb.get(); bb.get(); bb.get(); bb.get() // month day hour min sec
        bb.get() // validity
        bb.int // time accuracy
        bb.int // nanos
        val fixStatus = bb.get().toInt() and 0xFF
        val fixFlags = bb.get().toInt() and 0xFF
        bb.get() // date/time flags
        val satellites = bb.get().toInt() and 0xFF
        val lonE7 = bb.int
        val latE7 = bb.int
        bb.int // wgs alt mm
        val mslAltMm = bb.int
        val hAccMm = bb.int
        bb.int // vAcc
        val speedMmps = bb.int
        val headingE5 = bb.int
        bb.int // speedAcc
        bb.int // headingAcc
        bb.short // pdop
        val latLonFlags = bb.get().toInt() and 0xFF
        bb.get() // battery
        val gX = bb.short / 1000f
        val gY = bb.short / 1000f
        val gZ = bb.short / 1000f
        val rotX = bb.short / 100f
        val rotY = bb.short / 100f
        val rotZ = bb.short / 100f

        val gnssFixOk = (fixFlags and 0x01) != 0
        val coordsInvalid = (latLonFlags and 0x01) != 0
        val hasCoords = !coordsInvalid && (latE7 != 0 || lonE7 != 0)
        // Accept 2D/3D with coordinates even when RaceBox clears gnssFixOk due to its
        // default 3 m accuracy gate (official app still shows / uses that fix).
        val fixOk = hasCoords && fixStatus >= 2
        val horizontalAccuracyM = if (hAccMm > 0) (hAccMm / 1000f).coerceAtLeast(0.1f) else Float.NaN

        val location = Location("racebox").apply {
            if (hasCoords) {
                latitude = latE7 / 1e7
                longitude = lonE7 / 1e7
                altitude = mslAltMm / 1000.0
            }
            if (speedMmps != 0 || fixOk) {
                speed = (speedMmps / 1000f).coerceAtLeast(0f)
            }
            if (headingE5 != 0 || fixOk) {
                bearing = ((headingE5 / 1e5f) % 360f + 360f) % 360f
            }
            if (!horizontalAccuracyM.isNaN()) {
                accuracy = horizontalAccuracyM
            }
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            time = System.currentTimeMillis()
        }

        // Match phone HUD sign convention (inertial):
        // forward accel → negative longitudinal G; right turn → negative lateral G.
        val displayLateralG = -gY
        val displayLongitudinalG = -gX
        val dynamicG = sqrt(gX * gX + gY * gY + (gZ - 1f) * (gZ - 1f))
        // Roll from gravity: Y lateral, Z vertical (RaceBox axes).
        val leanDegApprox = Math.toDegrees(atan2(gY.toDouble(), gZ.toDouble())).toFloat()
            .coerceIn(-89f, 89f)

        return Sample(
            location = location,
            fixOk = fixOk,
            fixStatus = fixStatus,
            gnssFixOk = gnssFixOk,
            satellites = satellites,
            horizontalAccuracyM = horizontalAccuracyM,
            gForceX = gX,
            gForceY = gY,
            gForceZ = gZ,
            rotRateXDegPerSec = rotX,
            rotRateYDegPerSec = rotY,
            rotRateZDegPerSec = rotZ,
            displayLateralG = displayLateralG,
            displayLongitudinalG = displayLongitudinalG,
            dynamicG = dynamicG,
            leanDegApprox = leanDegApprox
        )
    }

    /**
     * Build a UBX framed packet (sync + class/id + payload + checksum).
     * Payload must be ≤ 248 bytes so total stays under RaceBox's 256-byte UART buffer.
     */
    fun buildUbxPacket(msgClass: Int, msgId: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        require(payload.size <= 248) { "RaceBox UART payload too large: ${payload.size}" }
        val packet = ByteArray(8 + payload.size)
        packet[0] = SYNC_1
        packet[1] = SYNC_2
        packet[2] = msgClass.toByte()
        packet[3] = msgId.toByte()
        packet[4] = (payload.size and 0xFF).toByte()
        packet[5] = ((payload.size shr 8) and 0xFF).toByte()
        System.arraycopy(payload, 0, packet, 6, payload.size)
        var ckA = 0
        var ckB = 0
        for (i in 2 until 6 + payload.size) {
            ckA = (ckA + (packet[i].toInt() and 0xFF)) and 0xFF
            ckB = (ckB + ckA) and 0xFF
        }
        packet[6 + payload.size] = ckA.toByte()
        packet[7 + payload.size] = ckB.toByte()
        return packet
    }

    /** UBX-MGA-INI-TIME_UTC — approximate UTC time to speed cold starts. */
    fun buildMgaIniTimeUtc(epochMs: Long = System.currentTimeMillis()): ByteArray {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = epochMs
        }
        val payload = ByteArray(24)
        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(0x10) // type TIME_UTC
        bb.put(0x00) // version
        bb.put(0x00) // ref (source unknown)
        bb.put((-1).toByte()) // leapSecs unknown
        bb.putShort(cal.get(java.util.Calendar.YEAR).toShort())
        bb.put((cal.get(java.util.Calendar.MONTH) + 1).toByte())
        bb.put(cal.get(java.util.Calendar.DAY_OF_MONTH).toByte())
        bb.put(cal.get(java.util.Calendar.HOUR_OF_DAY).toByte())
        bb.put(cal.get(java.util.Calendar.MINUTE).toByte())
        bb.put(cal.get(java.util.Calendar.SECOND).toByte())
        bb.put(0) // reserved
        bb.putInt(((epochMs % 1000L) * 1_000_000L).toInt()) // ns
        bb.putShort(2) // tAccS ≈ 2 s
        bb.putShort(0) // reserved
        bb.putInt(0) // tAccNs
        return buildUbxPacket(0x13, 0x40, payload)
    }

    /** UBX-MGA-INI-POS_LLH — coarse position from phone GPS to speed TTFF. */
    fun buildMgaIniPosLlh(latitude: Double, longitude: Double, altitudeM: Double = 0.0, accuracyM: Float = 50f): ByteArray {
        val payload = ByteArray(20)
        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(0x01) // type POS_LLH
        bb.put(0x00) // version
        bb.putShort(0) // reserved
        bb.putInt((latitude * 1e7).toInt())
        bb.putInt((longitude * 1e7).toInt())
        bb.putInt((altitudeM * 100.0).toInt()) // cm
        bb.putInt((accuracyM.coerceIn(5f, 50_000f) * 100f).toInt()) // cm
        return buildUbxPacket(0x13, 0x40, payload)
    }

    /** Split a concatenated UBX-MGA aiding blob into individual frames (≤256 B for RaceBox UART). */
    fun splitUbxFrames(data: ByteArray): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        var i = 0
        while (i + 8 <= data.size) {
            if (data[i] != SYNC_1 || data[i + 1] != SYNC_2) {
                i++
                continue
            }
            val payloadLen = u16(data[i + 4], data[i + 5])
            if (payloadLen > 248) {
                i++
                continue
            }
            val total = 6 + payloadLen + 2
            if (i + total > data.size) break
            val frame = data.copyOfRange(i, i + total)
            if (checksumOk(frame)) {
                out.add(frame)
                i += total
            } else {
                i++
            }
        }
        return out
    }

    private fun checksumOk(packet: ByteArray): Boolean {
        if (packet.size < 8) return false
        var ckA = 0
        var ckB = 0
        for (i in 2 until packet.size - 2) {
            ckA = (ckA + (packet[i].toInt() and 0xFF)) and 0xFF
            ckB = (ckB + ckA) and 0xFF
        }
        return (packet[packet.size - 2].toInt() and 0xFF) == ckA &&
            (packet[packet.size - 1].toInt() and 0xFF) == ckB
    }

    private fun u16(lo: Byte, hi: Byte): Int {
        return (lo.toInt() and 0xFF) or ((hi.toInt() and 0xFF) shl 8)
    }
}
