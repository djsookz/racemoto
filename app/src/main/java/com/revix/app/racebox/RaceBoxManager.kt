package com.revix.app.racebox

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Debug-only RaceBox BLE client. No-ops outside [RaceBoxDebugGate.isAvailable].
 */
object RaceBoxManager {
    private const val TAG = "RaceBoxManager"
    private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val UART_SERVICE = UUID.fromString(RaceBoxProtocol.UART_SERVICE_UUID)
    private val UART_TX = UUID.fromString(RaceBoxProtocol.UART_TX_UUID)
    private val UART_RX = UUID.fromString(RaceBoxProtocol.UART_RX_UUID)

    enum class ConnectionState {
        DISCONNECTED,
        SCANNING,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    data class Status(
        val state: ConnectionState = ConnectionState.DISCONNECTED,
        val deviceName: String? = null,
        val message: String = "Disconnected",
        val lastSpeedKmh: Float = 0f,
        val satellites: Int = 0,
        val updateHz: Double = 0.0,
        val fixOk: Boolean = false
    )

    data class ScannedDevice(
        val device: BluetoothDevice,
        val name: String,
        val address: String
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val assembler = RaceBoxProtocol.PacketAssembler()
    private val locationListeners = CopyOnWriteArrayList<(Location) -> Unit>()
    private val sampleListeners = CopyOnWriteArrayList<(RaceBoxProtocol.Sample) -> Unit>()
    private val statusListeners = CopyOnWriteArrayList<(Status) -> Unit>()
    private val scanListeners = CopyOnWriteArrayList<(List<ScannedDevice>) -> Unit>()

    @Volatile
    private var status = Status()
    @Volatile
    private var latestSample: RaceBoxProtocol.Sample? = null
    private var fusedLeanDeg = 0f
    private var hasFusedLean = false
    private var lastLeanFuseNanos = 0L
    private var appContext: Context? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var gatt: BluetoothGatt? = null
    private var scanning = false
    private val foundDevices = LinkedHashMap<String, ScannedDevice>()
    private var lastSampleNanos = 0L
    private var pendingDiscoverAfterMtu = false
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private val pendingWrites = ArrayDeque<ByteArray>()
    private var writeInFlight = false
    private var phoneAidingStartedForConnection = false
    private var rinexAidingStartedForConnection = false
    private var bestPhoneAidingLocation: Location? = null
    private var phoneAidingInjectCount = 0
    private var phoneGpsListener: LocationListener? = null
    private val phoneAidingRetryRunnable = object : Runnable {
        override fun run() {
            if (status.state != ConnectionState.CONNECTED) return
            if (status.fixOk) {
                stopPhoneGpsUpdates()
                return
            }
            injectPhoneAidingOnce(reason = "retry")
            phoneAidingInjectCount += 1
            if (phoneAidingInjectCount < phoneAidingMaxInjects) {
                mainHandler.postDelayed(this, phoneAidingRetryMs)
            } else {
                stopPhoneGpsUpdates()
            }
        }
    }
    private val aidingExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "RaceBoxRinexAiding").apply { isDaemon = true }
    }
    private val bulkWritePaceMs = AtomicInteger(40)
    private val phoneAidingRetryMs = 5_000L
    private val phoneAidingMaxInjects = 18 // ~90s of retries
    private val phonePosMaxAgeMs = 60_000L
    private val phonePosMaxAccuracyM = 100f

    val isConnected: Boolean
        get() = status.state == ConnectionState.CONNECTED

    fun currentStatus(): Status = status

    fun latestSample(): RaceBoxProtocol.Sample? = latestSample

    fun addLocationListener(listener: (Location) -> Unit) {
        locationListeners.addIfAbsent(listener)
    }

    fun removeLocationListener(listener: (Location) -> Unit) {
        locationListeners.remove(listener)
    }

    fun addSampleListener(listener: (RaceBoxProtocol.Sample) -> Unit) {
        sampleListeners.addIfAbsent(listener)
        latestSample?.let { listener(it) }
    }

    fun removeSampleListener(listener: (RaceBoxProtocol.Sample) -> Unit) {
        sampleListeners.remove(listener)
    }

    fun addStatusListener(listener: (Status) -> Unit) {
        statusListeners.addIfAbsent(listener)
        listener(status)
    }

    fun removeStatusListener(listener: (Status) -> Unit) {
        statusListeners.remove(listener)
    }

    fun addScanListener(listener: (List<ScannedDevice>) -> Unit) {
        scanListeners.addIfAbsent(listener)
        listener(foundDevices.values.toList())
    }

    fun removeScanListener(listener: (List<ScannedDevice>) -> Unit) {
        scanListeners.remove(listener)
    }

    fun ensureInitialized(context: Context) {
        if (!RaceBoxDebugGate.isAvailable()) return
        if (appContext != null) return
        appContext = context.applicationContext
        val manager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = manager?.adapter
    }

    fun hasBluetoothPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(context: Context) {
        if (!RaceBoxDebugGate.isAvailable()) return
        ensureInitialized(context)
        if (!hasBluetoothPermissions(context)) {
            publishStatus(status.copy(state = ConnectionState.ERROR, message = "Bluetooth permission required"))
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            publishStatus(status.copy(state = ConnectionState.ERROR, message = "Bluetooth is off"))
            return
        }
        stopScan()
        foundDevices.clear()
        notifyScan()
        scanning = true
        publishStatus(status.copy(state = ConnectionState.SCANNING, message = "Scanning…"))
        adapter.bluetoothLeScanner?.startScan(scanCallback)
        mainHandler.postDelayed({ stopScan(keepStatusIfIdle = true) }, 12_000L)
    }

    @SuppressLint("MissingPermission")
    fun stopScan(keepStatusIfIdle: Boolean = false) {
        if (!scanning) return
        scanning = false
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
        }
        if (keepStatusIfIdle && status.state == ConnectionState.SCANNING) {
            publishStatus(
                status.copy(
                    state = ConnectionState.DISCONNECTED,
                    message = if (foundDevices.isEmpty()) "No RaceBox found" else "Scan done"
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(context: Context, device: BluetoothDevice) {
        if (!RaceBoxDebugGate.isAvailable()) return
        ensureInitialized(context)
        if (!hasBluetoothPermissions(context)) {
            publishStatus(status.copy(state = ConnectionState.ERROR, message = "Bluetooth permission required"))
            return
        }
        stopScan()
        disconnect(clearPrefer = false)
        assembler.clear()
        lastSampleNanos = 0L
        latestSample = null
        hasFusedLean = false
        lastLeanFuseNanos = 0L
        pendingDiscoverAfterMtu = false
        notifyCharacteristic = null
        writeCharacteristic = null
        pendingWrites.clear()
        writeInFlight = false
        stopPhoneAiding()
        rinexAidingStartedForConnection = false
        bulkWritePaceMs.set(40)
        val name = safeName(device) ?: device.address
        publishStatus(
            Status(
                state = ConnectionState.CONNECTING,
                deviceName = name,
                message = "Connecting to $name…"
            )
        )
        gatt = device.connectGatt(context.applicationContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect(clearPrefer: Boolean = true) {
        stopScan()
        stopPhoneAiding()
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        notifyCharacteristic = null
        writeCharacteristic = null
        pendingWrites.clear()
        writeInFlight = false
        rinexAidingStartedForConnection = false
        bulkWritePaceMs.set(40)
        pendingDiscoverAfterMtu = false
        assembler.clear()
        lastSampleNanos = 0L
        latestSample = null
        hasFusedLean = false
        lastLeanFuseNanos = 0L
        if (clearPrefer) {
            appContext?.let { RaceBoxDebugGate.setUseRaceBoxGpsEnabled(it, false) }
        }
        publishStatus(Status(message = "Disconnected"))
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = result.scanRecord?.deviceName ?: safeName(device) ?: return
            if (!isRaceBoxName(name)) return
            val scanned = ScannedDevice(device = device, name = name, address = device.address)
            foundDevices[device.address] = scanned
            notifyScan()
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            publishStatus(status.copy(state = ConnectionState.ERROR, message = "Scan failed ($errorCode)"))
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, statusCode: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mainHandler.post {
                    publishStatus(status.copy(state = ConnectionState.CONNECTING, message = "Connected · negotiating BLE…"))
                }
                try {
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                } catch (_: Exception) {
                }
                pendingDiscoverAfterMtu = true
                val mtuOk = try {
                    gatt.requestMtu(247)
                } catch (_: Exception) {
                    false
                }
                if (!mtuOk) {
                    pendingDiscoverAfterMtu = false
                    gatt.discoverServices()
                } else {
                    // Fallback if onMtuChanged never arrives on some stacks.
                    mainHandler.postDelayed({
                        if (pendingDiscoverAfterMtu && this@RaceBoxManager.gatt === gatt) {
                            pendingDiscoverAfterMtu = false
                            gatt.discoverServices()
                        }
                    }, 1200L)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mainHandler.post {
                    stopPhoneAiding()
                    this@RaceBoxManager.gatt = null
                    notifyCharacteristic = null
                    pendingDiscoverAfterMtu = false
                    publishStatus(Status(message = "Disconnected"))
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, statusCode: Int) {
            Log.d(TAG, "MTU changed mtu=$mtu status=$statusCode")
            if (pendingDiscoverAfterMtu) {
                pendingDiscoverAfterMtu = false
                gatt.discoverServices()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, statusCode: Int) {
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                mainHandler.post {
                    publishStatus(status.copy(state = ConnectionState.ERROR, message = "Service discovery failed"))
                }
                return
            }
            val service = gatt.getService(UART_SERVICE)
            val tx = service?.getCharacteristic(UART_TX)
            val rx = service?.getCharacteristic(UART_RX)
            if (tx == null) {
                mainHandler.post {
                    publishStatus(status.copy(state = ConnectionState.ERROR, message = "UART TX missing"))
                }
                return
            }
            notifyCharacteristic = tx
            writeCharacteristic = rx
            gatt.setCharacteristicNotification(tx, true)
            val cccd = tx.getDescriptor(CCCD)
            if (cccd == null) {
                markStreamingReady("Connected · notifications on (no CCCD)")
                return
            }
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val wrote = gatt.writeDescriptor(cccd)
            if (!wrote) {
                markStreamingReady("Connected · notify write failed, retrying…")
                mainHandler.postDelayed({
                    try {
                        gatt.writeDescriptor(cccd)
                    } catch (_: Exception) {
                    }
                }, 300L)
            } else {
                mainHandler.post {
                    publishStatus(status.copy(state = ConnectionState.CONNECTING, message = "Enabling RaceBox stream…"))
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            statusCode: Int
        ) {
            if (descriptor.uuid != CCCD) return
            if (statusCode == BluetoothGatt.GATT_SUCCESS) {
                markStreamingReady("Live stream on · waiting GPS lock (need sky view)")
            } else {
                markStreamingReady("Connected · CCCD status=$statusCode · waiting data")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            val value = characteristic.value ?: return
            handleIncoming(value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncoming(value)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            statusCode: Int
        ) {
            if (characteristic.uuid != UART_RX) return
            writeInFlight = false
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "UART RX write failed status=$statusCode")
            }
            drainWriteQueue()
        }
    }

    private fun markStreamingReady(message: String) {
        mainHandler.post {
            appContext?.let { RaceBoxDebugGate.setUseRaceBoxGpsEnabled(it, true) }
            publishStatus(
                status.copy(
                    state = ConnectionState.CONNECTED,
                    message = message,
                    fixOk = false
                )
            )
            startPhoneAidingPipeline()
            startRinexAidingPipeline()
        }
    }

    /**
     * Offline-friendly aiding: UTC time always, position only from a fresh/good phone GPS fix.
     * Re-injects every few seconds until RaceBox has a usable fix (bad/stale pos is skipped —
     * wrong MGA-INI-POS can hurt TTFF).
     */
    @SuppressLint("MissingPermission")
    private fun startPhoneAidingPipeline() {
        if (phoneAidingStartedForConnection) return
        phoneAidingStartedForConnection = true
        phoneAidingInjectCount = 0
        bestPhoneAidingLocation = pickUsablePhoneLocation(allowStaleGpsLastKnown = true)
        injectPhoneAidingOnce(reason = "start")
        phoneAidingInjectCount = 1
        startPhoneGpsUpdates()
        mainHandler.postDelayed(phoneAidingRetryRunnable, phoneAidingRetryMs)
    }

    private fun stopPhoneAiding() {
        phoneAidingStartedForConnection = false
        phoneAidingInjectCount = 0
        bestPhoneAidingLocation = null
        mainHandler.removeCallbacks(phoneAidingRetryRunnable)
        stopPhoneGpsUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startPhoneGpsUpdates() {
        val ctx = appContext ?: return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return
        stopPhoneGpsUpdates()
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (isUsablePhoneGpsLocation(location)) {
                    bestPhoneAidingLocation = location
                    Log.d(
                        TAG,
                        "Phone GPS aiding update acc=${location.accuracy}m"
                    )
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        phoneGpsListener = listener
        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1_000L,
                0f,
                listener,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            Log.w(TAG, "requestLocationUpdates failed", e)
            phoneGpsListener = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopPhoneGpsUpdates() {
        val ctx = appContext ?: return
        val listener = phoneGpsListener ?: return
        phoneGpsListener = null
        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            lm?.removeUpdates(listener)
        } catch (_: Exception) {
        }
    }

    private fun injectPhoneAidingOnce(reason: String) {
        if (status.state != ConnectionState.CONNECTED) return
        enqueueWrite(RaceBoxProtocol.buildMgaIniTimeUtc())
        val loc = bestPhoneAidingLocation?.takeIf { isUsablePhoneGpsLocation(it) }
            ?: pickUsablePhoneLocation(allowStaleGpsLastKnown = false)
        if (loc != null) {
            bestPhoneAidingLocation = loc
            val acc = loc.accuracy.coerceIn(5f, phonePosMaxAccuracyM)
            enqueueWrite(
                RaceBoxProtocol.buildMgaIniPosLlh(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    altitudeM = if (loc.hasAltitude()) loc.altitude else 0.0,
                    accuracyM = acc
                )
            )
            Log.d(
                TAG,
                "MGA-INI time+pos ($reason) acc=${acc}m age=${System.currentTimeMillis() - loc.time}ms"
            )
        } else {
            Log.d(TAG, "MGA-INI time only ($reason) — no good phone GPS yet")
        }
    }

    @SuppressLint("MissingPermission")
    private fun pickUsablePhoneLocation(allowStaleGpsLastKnown: Boolean): Location? {
        val ctx = appContext ?: return null
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val gps = try {
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } catch (_: SecurityException) {
                null
            }
            when {
                gps != null && isUsablePhoneGpsLocation(gps) -> gps
                allowStaleGpsLastKnown && gps != null && isAcceptableStaleGps(gps) -> gps
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "pickUsablePhoneLocation failed", e)
            null
        }
    }

    private fun isUsablePhoneGpsLocation(loc: Location): Boolean {
        if (loc.latitude == 0.0 && loc.longitude == 0.0) return false
        val ageMs = System.currentTimeMillis() - loc.time
        if (ageMs < 0L || ageMs > phonePosMaxAgeMs) return false
        if (!loc.hasAccuracy() || loc.accuracy <= 0f || loc.accuracy > phonePosMaxAccuracyM) return false
        // Prefer real GPS; reject coarse network-looking fixes when provider is set.
        val provider = loc.provider.orEmpty()
        if (provider.isNotEmpty() &&
            !provider.equals(LocationManager.GPS_PROVIDER, ignoreCase = true) &&
            !provider.equals(LocationManager.PASSIVE_PROVIDER, ignoreCase = true)
        ) {
            return false
        }
        return true
    }

    private fun isAcceptableStaleGps(loc: Location): Boolean {
        if (loc.latitude == 0.0 && loc.longitude == 0.0) return false
        val ageMs = System.currentTimeMillis() - loc.time
        if (ageMs < 0L || ageMs > 5 * 60_000L) return false
        if (!loc.hasAccuracy() || loc.accuracy <= 0f || loc.accuracy > 50f) return false
        val provider = loc.provider.orEmpty()
        return provider.isEmpty() || provider.equals(LocationManager.GPS_PROVIDER, ignoreCase = true)
    }

    /**
     * Free aiding: download CI-built UBX-MGA (from public IGS/BKG RINEX) and inject over UART.
     */
    private fun startRinexAidingPipeline() {
        if (rinexAidingStartedForConnection) return
        rinexAidingStartedForConnection = true
        val ctx = appContext ?: return
        publishStatus(status.copy(message = "Connected · downloading free RINEX aiding…"))
        aidingExecutor.execute {
            try {
                val loaded = RaceBoxAidingDownloader.load(ctx)
                val frames = RaceBoxProtocol.splitUbxFrames(loaded.bytes)
                if (frames.isEmpty()) {
                    throw IllegalStateException("Aiding split produced 0 UBX frames")
                }
                mainHandler.post {
                    if (status.state != ConnectionState.CONNECTED) return@post
                    val src = if (loaded.fromCache) "cache" else "net"
                    publishStatus(
                        status.copy(
                            message = "Connected · injecting RINEX aiding ($src, ${frames.size} pkts)…"
                        )
                    )
                    bulkWritePaceMs.set(70)
                    frames.forEach { enqueueWrite(it) }
                    mainHandler.postDelayed({
                        bulkWritePaceMs.set(40)
                        if (status.state == ConnectionState.CONNECTED && !status.fixOk) {
                            publishStatus(
                                status.copy(
                                    message = "RINEX aiding injected · waiting GPS lock (need sky)"
                                )
                            )
                        }
                    }, (frames.size * 80L).coerceAtMost(25_000L))
                }
                Log.i(TAG, "RINEX aiding ready frames=${frames.size} bytes=${loaded.bytes.size}")
            } catch (e: Exception) {
                Log.w(TAG, "RINEX aiding failed", e)
                mainHandler.post {
                    if (status.state == ConnectionState.CONNECTED) {
                        publishStatus(
                            status.copy(
                                message = "RINEX aiding failed · cold lock (${e.message?.take(60) ?: "error"})"
                            )
                        )
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enqueueWrite(packet: ByteArray) {
        if (writeCharacteristic == null || gatt == null) return
        pendingWrites.addLast(packet)
        drainWriteQueue()
    }

    @SuppressLint("MissingPermission")
    private fun drainWriteQueue() {
        if (writeInFlight) return
        val gatt = gatt ?: return
        val rx = writeCharacteristic ?: return
        val next = pendingWrites.removeFirstOrNull() ?: return
        writeInFlight = true
        val paceMs = bulkWritePaceMs.get().toLong()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = gatt.writeCharacteristic(
                    rx,
                    next,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                )
                if (result != BluetoothGatt.GATT_SUCCESS) {
                    writeInFlight = false
                    Log.w(TAG, "writeCharacteristic failed result=$result")
                } else {
                    mainHandler.postDelayed({
                        if (writeInFlight) {
                            writeInFlight = false
                            drainWriteQueue()
                        }
                    }, paceMs)
                }
            } else {
                rx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                rx.value = next
                if (!gatt.writeCharacteristic(rx)) {
                    writeInFlight = false
                    Log.w(TAG, "writeCharacteristic returned false")
                } else {
                    mainHandler.postDelayed({
                        if (writeInFlight) {
                            writeInFlight = false
                            drainWriteQueue()
                        }
                    }, paceMs)
                }
            }
        } catch (e: Exception) {
            writeInFlight = false
            Log.w(TAG, "UART write exception", e)
        }
    }

    private fun handleIncoming(chunk: ByteArray) {
        val packets = assembler.append(chunk)
        for (packet in packets) {
            val raw = RaceBoxProtocol.parseDataMessage(packet) ?: continue
            val sample = fuseLean(raw)
            latestSample = sample
            val now = SystemClockNanos()
            val hz = if (lastSampleNanos > 0L) {
                val dtMs = (now - lastSampleNanos) / 1_000_000.0
                if (dtMs > 0) 1000.0 / dtMs else 0.0
            } else {
                0.0
            }
            lastSampleNanos = now
            val speedKmh = sample.location.speed * 3.6f
            val fixLabel = when (sample.fixStatus) {
                0 -> "no-fix"
                2 -> "2D"
                3 -> "3D"
                else -> "fix=${sample.fixStatus}"
            }
            val hAccLabel = if (sample.horizontalAccuracyM.isNaN()) {
                "hAcc=--"
            } else {
                "hAcc=${"%.0f".format(sample.horizontalAccuracyM)}m"
            }
            val gateLabel = if (sample.gnssFixOk) "gateOK" else "gateLoose"
            mainHandler.post {
                if (sample.fixOk) {
                    stopPhoneAiding()
                }
                publishStatus(
                    status.copy(
                        state = ConnectionState.CONNECTED,
                        lastSpeedKmh = speedKmh,
                        satellites = sample.satellites,
                        updateHz = hz,
                        fixOk = sample.fixOk,
                        message = if (sample.fixOk) {
                            "RaceBox OK · ${"%.0f".format(hz)} Hz · ${sample.satellites} sats · $fixLabel · $hAccLabel · lean ${sample.leanDegApprox.toInt()}°"
                        } else {
                            "Live ${"%.0f".format(hz)} Hz · $fixLabel · usedSats=${sample.satellites} · $hAccLabel · $gateLabel"
                        }
                    )
                )
                sampleListeners.forEach { it(sample) }
                if (sample.fixOk) {
                    locationListeners.forEach { it(sample.location) }
                }
            }
        }
    }

    /** Complementary filter: gyro roll integration + accel lean from G vector. */
    private fun fuseLean(raw: RaceBoxProtocol.Sample): RaceBoxProtocol.Sample {
        val now = SystemClockNanos()
        val accelLean = raw.leanDegApprox
        if (!hasFusedLean) {
            fusedLeanDeg = accelLean
            hasFusedLean = true
            lastLeanFuseNanos = now
            return raw.copy(leanDegApprox = fusedLeanDeg)
        }
        val dtSec = ((now - lastLeanFuseNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.08f)
        lastLeanFuseNanos = now
        // RaceBox rotation X = roll
        fusedLeanDeg = (fusedLeanDeg + raw.rotRateXDegPerSec * dtSec).coerceIn(-89f, 89f)
        val correction = 0.12f
        fusedLeanDeg += correction * (accelLean - fusedLeanDeg)
        return raw.copy(leanDegApprox = fusedLeanDeg.coerceIn(-89f, 89f))
    }

    private fun SystemClockNanos(): Long = android.os.SystemClock.elapsedRealtimeNanos()

    private fun publishStatus(next: Status) {
        status = next
        statusListeners.forEach { it(next) }
    }

    private fun notifyScan() {
        val list = foundDevices.values.toList()
        scanListeners.forEach { it(list) }
    }

    @SuppressLint("MissingPermission")
    private fun safeName(device: BluetoothDevice): String? {
        return try {
            device.name
        } catch (_: SecurityException) {
            null
        }
    }

    private fun isRaceBoxName(name: String): Boolean {
        val n = name.trim()
        return n.startsWith("RaceBox Mini", ignoreCase = true) ||
            n.startsWith("RaceBox Micro", ignoreCase = true)
    }
}
