package dev.rtrcompanion.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.rtrcompanion.log.EventLog
import dev.rtrcompanion.protocol.Gatt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Found(
    val address: String,
    val name: String?,
    val rssi: Int,
    val advertisesService: Boolean,
) {
    val looksLikeCluster: Boolean
        get() = advertisesService || (name?.startsWith(Gatt.NAME_PREFIX, ignoreCase = true) == true)
}

/**
 * Unfiltered BLE scan. Clusters often advertise no service UUID, so filtering
 * on the service would hide them; instead everything is listed and clusters
 * (name starting TVSBT, or the service UUID) are sorted to the top.
 */
@SuppressLint("MissingPermission")
class Scanner(private val ctx: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val found = LinkedHashMap<String, Found>()

    private val _results = MutableStateFlow<List<Found>>(emptyList())
    val results: StateFlow<List<Found>> = _results

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val rec = result.scanRecord
            val name = rec?.deviceName ?: result.device.name
            val hasService = rec?.serviceUuids?.any { it.uuid == Gatt.SERVICE } == true
            val f = Found(result.device.address, name, result.rssi, hasService)
            handler.post {
                found[f.address] = f
                _results.value = found.values.sortedWith(
                    compareByDescending<Found> { it.looksLikeCluster }.thenByDescending { it.rssi }
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            EventLog.w("Scan", "Scan failed with code $errorCode")
            handler.post { _scanning.value = false }
        }
    }

    fun start(): Boolean {
        val scanner = ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            EventLog.w("Scan", "Bluetooth is off or has no LE scanner")
            return false
        }
        stop()
        found.clear()
        _results.value = emptyList()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        return try {
            scanner.startScan(null, settings, callback)
            _scanning.value = true
            EventLog.i("Scan", "Scanning for 15 s")
            handler.postDelayed({ stop() }, 15_000)
            true
        } catch (e: Exception) {
            EventLog.w("Scan", "Could not scan: $e")
            false
        }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        if (!_scanning.value) return
        try {
            ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (ignored: Exception) {
        }
        _scanning.value = false
        EventLog.i("Scan", "Scan stopped, ${found.size} devices")
    }
}
