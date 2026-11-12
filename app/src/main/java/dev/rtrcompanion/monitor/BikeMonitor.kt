package dev.rtrcompanion.monitor

import dev.rtrcompanion.data.AppState
import dev.rtrcompanion.data.Prefs
import dev.rtrcompanion.fuel.FuelStatus
import dev.rtrcompanion.garage.BatteryRecord
import dev.rtrcompanion.garage.BitRef
import dev.rtrcompanion.garage.Switch
import dev.rtrcompanion.garage.SwitchLearn
import dev.rtrcompanion.log.EventLog
import dev.rtrcompanion.ride.RideRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

enum class Severity { INFO, WARN, DANGER }

data class Alert(val id: String, val title: String, val detail: String, val severity: Severity)

/**
 * Watches the bike's live values once a second (driven by the link service):
 * raises alerts, tracks battery behaviour per key-on, works out lamp and
 * side-stand state from the learned switch bits, and remembers the odometer
 * and where the bike was parked.
 */
object BikeMonitor {
    private const val FRESH_MS = 3_000L
    private const val RENOTIFY_MS = 10 * 60_000L

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts: StateFlow<List<Alert>> = _alerts

    private val _switches = MutableStateFlow<Map<Switch, Boolean>>(emptyMap())
    val switches: StateFlow<Map<Switch, Boolean>> = _switches

    /** Called with an alert the first time it appears (and again after 10 minutes). */
    @Volatile
    var notifier: ((Alert) -> Unit)? = null

    @Volatile
    var fuelStatus: FuelStatus? = null

    private val notifiedAt = HashMap<String, Long>()
    private val lastOn = HashMap<Switch, Long>()
    private var mapText = ""
    private var map: Map<Switch, Pair<BitRef, Boolean>> = emptyMap()

    // battery session
    private var wasLive = false
    private var engineOn = false
    private var lastIdleBattery: Float? = null
    private var resting: Float? = null
    private var startMin: Float? = null
    private var startAt = 0L
    private var chargeSum = 0.0
    private var chargeN = 0
    private var lowChargeSince = 0L
    private var indicatorSince = HashMap<Switch, Long>()

    @Synchronized
    fun tick(now: Long, live: Boolean, saveBattery: (BatteryRecord) -> Unit) {
        if (!live) {
            if (wasLive) sessionEnd(now, saveBattery)
            wasLive = false
            if (_alerts.value.isNotEmpty()) _alerts.value = emptyList()
            return
        }
        wasLive = true
        val r = AppState.readings.value
        fun fresh(key: String): Long? = r[key]?.takeIf { now - it.at < FRESH_MS }?.raw

        val speed = fresh("speed")?.toInt()
        val rpm = fresh("rpm")?.toInt()
        val battery = fresh("battery")?.let { it / 10f }
        val engineTemp = fresh("engineTemp")?.let { if (it < 25) 0 else (it - 25).toInt() }

        fresh("odometer")?.let { raw ->
            val km = raw / 10.0
            if (kotlin.math.abs(km - Prefs.lastOdoKm) >= 0.1) Prefs.lastOdoKm = km
        }

        trackBattery(now, rpm, battery)
        updateSwitches(now)

        val list = ArrayList<Alert>()
        if (engineTemp != null && engineTemp >= 110) {
            list += Alert("hot", "Engine running hot", "$engineTemp °C. Let it cool before riding hard.", Severity.DANGER)
        }
        if (rpm != null && rpm > 3000 && battery != null && battery < 13.0f) {
            if (lowChargeSince == 0L) lowChargeSince = now
            if (now - lowChargeSince > 30_000) {
                list += Alert("charge", "Battery not charging", String.format(Locale.US, "%.1f V at %d rpm (normal 13.5–14.8 V).", battery, rpm), Severity.WARN)
            }
        } else {
            lowChargeSince = 0L
        }
        if (battery != null && battery > 15.2f) {
            list += Alert("overcharge", "Battery overcharging", String.format(Locale.US, "%.1f V. Have the regulator checked.", battery), Severity.WARN)
        }
        fresh("mil")?.takeIf { it > 0 }?.let { list += Alert("mil", "Engine fault code $it", "The engine warning light is reporting code $it.", Severity.DANGER) }
        fresh("absMil")?.takeIf { it > 0 }?.let { list += Alert("abs", "ABS fault code $it", "The ABS is reporting code $it.", Severity.DANGER) }
        fuelStatus?.takeIf { it.onReserve }?.let { f ->
            list += Alert("reserve", "Fuel on reserve", f.rangeKm?.let { "About ${it.toInt()} km left." } ?: "Refuel soon.", Severity.WARN)
        }
        if (Prefs.overspeedOn && speed != null && speed > Prefs.overspeedKmh) {
            list += Alert("speed", "Over ${Prefs.overspeedKmh} km/h", "Speed $speed km/h.", Severity.WARN)
        }
        val sw = _switches.value
        if (sw[Switch.SIDE_STAND] == true && (speed ?: 0) > 5) {
            list += Alert("stand", "Side stand is down", "Lift the side stand.", Severity.DANGER)
        }
        for (ind in listOf(Switch.LEFT, Switch.RIGHT)) {
            if (sw[ind] == true && (speed ?: 0) > 20) {
                val since = indicatorSince.getOrPut(ind) { now }
                if (now - since > 30_000) list += Alert("ind-${ind.name}", "${ind.label} still on", "It has been blinking for over 30 seconds.", Severity.INFO)
            } else {
                indicatorSince.remove(ind)
            }
        }

        _alerts.value = list
        if (Prefs.alertsOn) {
            for (a in list) {
                if (a.severity == Severity.INFO && a.id.startsWith("ind")) continue
                if (a.id == "speed") continue
                val last = notifiedAt[a.id] ?: 0L
                if (now - last > RENOTIFY_MS) {
                    notifiedAt[a.id] = now
                    EventLog.w(TAG, "Alert: ${a.title} — ${a.detail}")
                    notifier?.invoke(a)
                }
            }
        }
    }

    private fun trackBattery(now: Long, rpm: Int?, battery: Float?) {
        if (rpm == null || battery == null) return
        if (!engineOn && rpm >= 600) {
            engineOn = true
            startAt = now
            resting = resting ?: lastIdleBattery
            startMin = battery
        } else if (engineOn && rpm < 300) {
            engineOn = false
        }
        if (!engineOn) lastIdleBattery = battery
        if (engineOn && now - startAt < 6_000) startMin = minOf(startMin ?: battery, battery)
        if (engineOn && rpm > 3000) {
            chargeSum += battery
            chargeN++
        }
    }

    private fun updateSwitches(now: Long) {
        val text = Prefs.switchMap
        if (text != mapText) {
            mapText = text
            map = SwitchLearn.decode(text)
        }
        if (map.isEmpty()) {
            if (_switches.value.isNotEmpty()) _switches.value = emptyMap()
            return
        }
        val frames = AppState.frames.value
        val out = HashMap<Switch, Boolean>()
        for ((sw, learned) in map) {
            val (ref, activeHigh) = learned
            val clear = frames[ref.frame]?.lastClear ?: continue
            val v = ref.read(clear) ?: continue
            if (v == activeHigh) lastOn[sw] = now
            val blinking = sw == Switch.LEFT || sw == Switch.RIGHT
            out[sw] = if (blinking) now - (lastOn[sw] ?: 0L) < 2_000 else v == activeHigh
        }
        _switches.value = out
    }

    private fun sessionEnd(now: Long, saveBattery: (BatteryRecord) -> Unit) {
        val charging = if (chargeN >= 10) (chargeSum / chargeN).toFloat() else null
        val rest = resting ?: lastIdleBattery
        if (rest != null || charging != null) {
            saveBattery(BatteryRecord(now, rest, startMin, charging))
        }
        RideRecorder.lastFix()?.let { (lat, lon) -> Prefs.parked = "$lat,$lon,$now" }
        engineOn = false
        lastIdleBattery = null
        resting = null
        startMin = null
        chargeSum = 0.0
        chargeN = 0
        lowChargeSince = 0L
        indicatorSince.clear()
    }

    private const val TAG = "Monitor"
}
