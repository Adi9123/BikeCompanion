package dev.bikecompanion.fuel

import dev.bikecompanion.data.AppState
import dev.bikecompanion.data.Prefs
import dev.bikecompanion.log.EventLog
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Keeps the running fuel totals and spots refuels. Driven once a second by
 * the link service.
 *
 * - The bike's fuel-injection counter (`5A 18` bytes 17-18) restarts at zero
 *   at every key-on and only rises while the engine runs. Its increases are
 *   summed into [Prefs.injTotal], a lifetime total.
 * - Odometer increases seen while connected are summed into [Prefs.trackedKm],
 *   so calibration can check the app saw most of the riding between fills.
 * - The fuel bars are noisy while moving, so only readings taken standing
 *   still count. The steady value at key-off is remembered; if it is two or
 *   more bars higher at the next key-on, the rider is asked about a refuel.
 */
object FuelMeter {
    private const val FRESH_MS = 3_000L
    private const val WINDOW = 10

    /** Bars before and after, when a refuel looks likely. */
    val refuelPrompt = MutableStateFlow<Pair<Int, Int>?>(null)

    private var lastInj: Long? = null
    private var lastOdo: Double? = null
    private val bars = ArrayDeque<Int>()
    private var checked = false
    private var connected = false

    @Synchronized
    fun tick(now: Long, live: Boolean) {
        if (!live) {
            if (connected) sessionEnd()
            return
        }
        connected = true
        val r = AppState.readings.value
        fun fresh(key: String): Long? = r[key]?.takeIf { now - it.at < FRESH_MS }?.raw

        fresh("injVolume")?.let { v ->
            val last = lastInj
            val add = when {
                last == null -> if (v < 2_000) v else 0L   // just after key-on: count from zero
                v >= last -> v - last
                last > 60_000 -> 65_536 - last + v          // 16-bit wrap
                else -> v                                   // counter restarted at key-on
            }
            if (add in 1..20_000) Prefs.injTotal = Prefs.injTotal + add
            lastInj = v
        }

        fresh("odometer")?.let { raw ->
            val km = raw / 10.0
            val l = lastOdo
            if (l != null && km > l && km - l < 1.0) Prefs.trackedKm = Prefs.trackedKm + (km - l)
            lastOdo = km
        }

        val b = fresh("fuel")?.toInt()
        if (b != null && b in 0..Prefs.fuelBars && fresh("speed") == 0L) {
            bars.addLast(b)
            if (bars.size > WINDOW) bars.removeFirst()
            if (!checked && bars.size >= 8) {
                checked = true
                val nowBars = steady()
                val before = Prefs.lastStableBars
                if (before >= 0 && nowBars - before >= 2) {
                    EventLog.i(TAG, "Fuel went from $before to $nowBars bars since last ride: refuel?")
                    refuelPrompt.value = before to nowBars
                }
            }
        }
    }

    private fun steady(): Int = bars.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: -1

    @Synchronized
    private fun sessionEnd() {
        if (bars.size >= 5) Prefs.lastStableBars = steady()
        bars.clear()
        lastInj = null
        lastOdo = null
        checked = false
        connected = false
    }

    private const val TAG = "Fuel"
}
