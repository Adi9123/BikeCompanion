package dev.rtrcompanion.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import dev.rtrcompanion.data.AppState
import dev.rtrcompanion.data.LinkPhase
import dev.rtrcompanion.data.Prefs
import dev.rtrcompanion.log.EventLog
import dev.rtrcompanion.protocol.Codec
import dev.rtrcompanion.protocol.FrameDecoder
import dev.rtrcompanion.protocol.Gatt
import dev.rtrcompanion.protocol.Outbound
import dev.rtrcompanion.protocol.Scramble
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One Bluetooth LE connection to the cluster.
 *
 * All GATT state changes are funnelled onto the main thread; the keepalive
 * loop runs in [scope]. Callers must hold BLUETOOTH_CONNECT (Android 12+)
 * before using this class — [LinkService] is only started once it is granted.
 */
@SuppressLint("MissingPermission")
class BleLink(private val ctx: Context, private val scope: CoroutineScope) {

    private val main = Handler(Looper.getMainLooper())
    private val decoder = FrameDecoder()

    // Read by the keepalive coroutine, written on the main thread.
    @Volatile
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var tx: BluetoothGattCharacteristic? = null

    @Volatile
    private var liveSince = 0L

    private var address: String? = null
    private var wanted = false
    private var attempt = 0
    private var gotFirstFrame = false

    @Volatile
    private var riderNamePending = false

    private var keepaliveJob: Job? = null
    private var reconnectJob: Job? = null
    private var watchdogJob: Job? = null
    private var rssiJob: Job? = null

    // ---------------------------------------------------------------- control

    fun connect(addr: String) {
        main.post {
            address = addr
            wanted = true
            attempt = 0
            reconnectJob?.cancel()
            decoder.reset()
            AppState.resetSession()
            EventLog.i(TAG, "Connect requested to $addr")
            open()
        }
    }

    fun disconnect(reason: String) {
        main.post {
            wanted = false
            reconnectJob?.cancel()
            stopJobs()
            gatt?.let {
                try {
                    it.disconnect()
                    it.close()
                } catch (ignored: Exception) {
                }
            }
            gatt = null
            tx = null
            AppState.link.update {
                it.copy(phase = LinkPhase.IDLE, detail = "Disconnected ($reason)", connectedAt = null, rssi = null)
            }
            EventLog.i(TAG, "Disconnected: $reason")
        }
    }

    fun queueRiderName() {
        riderNamePending = true
    }

    // ------------------------------------------------------------- lifecycle

    private fun open() {
        val addr = address ?: return
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            EventLog.w(TAG, "Bluetooth is off")
            AppState.link.update { it.copy(phase = LinkPhase.FAILED, detail = "Bluetooth is off") }
            scheduleReconnect("Bluetooth is off")
            return
        }
        val device = try {
            adapter.getRemoteDevice(addr)
        } catch (e: IllegalArgumentException) {
            EventLog.w(TAG, "Bad address $addr")
            AppState.link.update { it.copy(phase = LinkPhase.FAILED, detail = "Bad address") }
            return
        }
        attempt++
        AppState.link.update {
            it.copy(
                phase = LinkPhase.CONNECTING,
                address = addr,
                deviceName = Prefs.lastName ?: it.deviceName,
                attempts = attempt,
                detail = "Attempt $attempt",
            )
        }
        EventLog.i(TAG, "Opening GATT (attempt $attempt)")
        gatt?.close()
        gatt = device.connectGatt(ctx, false, callback, BluetoothDevice.TRANSPORT_LE)

        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            main.post {
                if (AppState.link.value.phase != LinkPhase.LIVE && gatt != null) {
                    EventLog.w(TAG, "No connection after ${CONNECT_TIMEOUT_MS / 1000} s. Is the ignition on, and is no other phone or app connected?")
                    teardown("connect timeout")
                }
            }
        }
    }

    private fun teardown(reason: String) {
        stopJobs()
        gatt?.let {
            try {
                it.close()
            } catch (ignored: Exception) {
            }
        }
        gatt = null
        tx = null
        AppState.link.update {
            it.copy(
                phase = if (wanted) LinkPhase.RECONNECT_WAIT else LinkPhase.IDLE,
                connectedAt = null,
                rssi = null,
                detail = reason,
            )
        }
        if (wanted && Prefs.autoReconnect) scheduleReconnect(reason)
    }

    private fun scheduleReconnect(reason: String) {
        if (!wanted) return
        reconnectJob?.cancel()
        val wait = (2_000L shl (attempt - 1).coerceIn(0, 4)).coerceAtMost(30_000L)
        EventLog.i(TAG, "Reconnecting in ${wait / 1000} s ($reason)")
        AppState.link.update {
            it.copy(phase = LinkPhase.RECONNECT_WAIT, detail = "Retry in ${wait / 1000} s: $reason")
        }
        reconnectJob = scope.launch {
            delay(wait)
            main.post { if (wanted) open() }
        }
    }

    private fun stopJobs() {
        keepaliveJob?.cancel()
        watchdogJob?.cancel()
        rssiJob?.cancel()
        keepaliveJob = null
        watchdogJob = null
        rssiJob = null
    }

    private fun goLive() {
        val g = gatt ?: return
        watchdogJob?.cancel()
        attempt = 0
        liveSince = System.currentTimeMillis()
        gotFirstFrame = false
        AppState.link.update {
            it.copy(
                phase = LinkPhase.LIVE,
                connectedAt = liveSince,
                detail = "Waiting for data",
                deviceName = g.device.name ?: it.deviceName,
            )
        }
        EventLog.i(TAG, "Live. Keepalive ${if (Prefs.keepalive) "on" else "off"}")
        if (Prefs.riderName.isNotBlank()) riderNamePending = true
        startKeepalive()
        startRssi()
    }

    // ------------------------------------------------------------- outbound

    private fun startKeepalive() {
        keepaliveJob?.cancel()
        keepaliveJob = scope.launch {
            while (isActive) {
                val scheme = outboundScheme()
                if (scheme != null) {
                    if (Prefs.keepalive) {
                        send(Outbound.keepalive(PhoneInfo.status(ctx)), scheme, "keepalive")
                    }
                    if (riderNamePending && Prefs.riderName.isNotBlank()) {
                        delay(400)
                        riderNamePending = false
                        send(Outbound.riderName(Prefs.riderName), scheme, "rider name")
                    }
                }
                delay(KEEPALIVE_MS)
            }
        }
    }

    /**
     * Which scramble to apply to outgoing frames. In automatic mode this is
     * whatever the bike was found to use. If the bike has sent nothing valid
     * after a few seconds, fall back to the Apache default, in case the
     * cluster waits for the phone to speak first.
     */
    private fun outboundScheme(): Scramble? {
        Prefs.scheme(Prefs.outboundScheme)?.let { return it }
        decoder.locked?.let { return it }
        return if (System.currentTimeMillis() - liveSince > FALLBACK_MS) Scramble.XOR_EA else null
    }

    private fun send(clear: ByteArray, scheme: Scramble, what: String): Boolean {
        val g = gatt ?: return false
        val c = tx ?: return false
        val wire = Codec.xor(clear, scheme)
        val type = if ((c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        val ok: Boolean = try {
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(c, wire, type) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    c.writeType = type
                    c.value = wire
                    g.writeCharacteristic(c)
                }
            }
        } catch (e: Exception) {
            EventLog.w(TAG, "TX $what failed: $e")
            false
        }
        EventLog.i(TAG, "TX $what${if (ok) "" else " REJECTED"} [${scheme.name}] ${Codec.hex(wire)} | clear ${Codec.hex(clear)}")
        AppState.decoder.update {
            it.copy(
                txCount = it.txCount + 1,
                txRejected = it.txRejected + if (ok) 0 else 1,
                lastTx = "$what ${if (ok) "sent" else "rejected"} at ${stamp()}",
                outbound = scheme,
            )
        }
        return ok
    }

    private fun startRssi() {
        rssiJob?.cancel()
        rssiJob = scope.launch {
            while (isActive) {
                main.post { gatt?.readRemoteRssi() }
                delay(5_000)
            }
        }
    }

    // -------------------------------------------------------------- inbound

    private fun handle(bytes: ByteArray) {
        decoder.forced = Prefs.scheme(Prefs.inboundScheme)
        val inb = decoder.decode(bytes)
        if (inb == null) {
            EventLog.w(TAG, "RX too short: ${Codec.hex(bytes)}")
            return
        }
        val now = System.currentTimeMillis()
        AppState.onInbound(inb, now)
        val verdict = when {
            inb.checksumOk -> "ok"
            inb.forced -> "checksum BAD (forced)"
            else -> "checksum BAD"
        }
        EventLog.i(TAG, "RX ${Codec.hex(inb.wire)} [${inb.scramble?.name ?: "?"} $verdict] clear ${Codec.hex(inb.clear)}")
        if (!gotFirstFrame) {
            gotFirstFrame = true
            EventLog.i(TAG, "First frame ${now - liveSince} ms after subscribing")
            AppState.link.update { it.copy(detail = "Receiving data") }
        }
    }

    // ------------------------------------------------------------- callback

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (gatt != null && g != gatt) {
                    g.close()
                    return@post
                }
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    EventLog.i(TAG, "GATT connected, discovering services")
                    AppState.link.update { it.copy(phase = LinkPhase.DISCOVERING, detail = "Discovering services") }
                    g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    main.postDelayed({ gatt?.discoverServices() }, 300)
                } else {
                    val why = GattStatus.describe(status)
                    val wasLive = AppState.link.value.phase == LinkPhase.LIVE
                    val held = if (wasLive) " after ${(System.currentTimeMillis() - liveSince) / 1000} s" else ""
                    EventLog.w(TAG, "Link down$held: $why (state $newState)")
                    AppState.link.update { it.copy(drops = it.drops + 1, lastDrop = "$why at ${stamp()}$held") }
                    teardown(why)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post {
                if (g != gatt) return@post
                val svc = g.getService(Gatt.SERVICE)
                if (status != BluetoothGatt.GATT_SUCCESS || svc == null) {
                    EventLog.w(TAG, "SmartXonnect service not found (status $status). Services: " +
                        g.services.joinToString { it.uuid.toString() })
                    g.disconnect()
                    teardown("service not found")
                    return@post
                }
                val rx = svc.getCharacteristic(Gatt.NOTIFY)
                tx = svc.getCharacteristic(Gatt.WRITE)
                EventLog.i(TAG, "Service found. notify=${rx != null} write=${tx != null}")
                if (rx == null) {
                    g.disconnect()
                    teardown("notify characteristic missing")
                    return@post
                }
                AppState.link.update { it.copy(phase = LinkPhase.SUBSCRIBING, detail = "Enabling notifications") }
                g.setCharacteristicNotification(rx, true)
                val cccd = rx.getDescriptor(Gatt.CCCD)
                if (cccd == null) {
                    EventLog.w(TAG, "No CCCD descriptor; assuming notifications already flow")
                    goLive()
                    return@post
                }
                val value = if ((rx.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                }
                val ok: Boolean = if (Build.VERSION.SDK_INT >= 33) {
                    g.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    run {
                        cccd.value = value
                        g.writeDescriptor(cccd)
                    }
                }
                if (!ok) {
                    EventLog.w(TAG, "Subscribe request rejected; continuing anyway")
                    goLive()
                }
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            main.post {
                if (g != gatt) return@post
                EventLog.i(TAG, "Notifications enabled (status $status)")
                goLive()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= 33) return
            @Suppress("DEPRECATION")
            val v = c.value ?: return
            val copy = v.copyOf()
            main.post { handle(copy) }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            val copy = value.copyOf()
            main.post { handle(copy) }
        }

        override fun onReadRemoteRssi(g: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) AppState.link.update { it.copy(rssi = rssi) }
        }
    }

    private fun stamp(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    companion object {
        private const val TAG = "Link"
        private const val CONNECT_TIMEOUT_MS = 20_000L
        private const val KEEPALIVE_MS = 2_000L
        private const val FALLBACK_MS = 5_000L
    }
}
