package dev.rtrcompanion.ride

import dev.rtrcompanion.data.AppState
import dev.rtrcompanion.data.Prefs
import dev.rtrcompanion.log.EventLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class RecPhase { IDLE, RECORDING, PAUSED }

data class RecState(
    val phase: RecPhase = RecPhase.IDLE,
    val startedAt: Long? = null,
    val pausedAt: Long? = null,
    val distanceKm: Double = 0.0,
    val maxSpeed: Int = 0,
    val reason: String = "",
)

/**
 * Records rides in the background while the bike is connected.
 *
 * - Starts the first time the bike reports a speed above zero.
 * - Takes one sample a second from the latest bike values and GPS fix.
 * - Pauses when the key is switched off (the bike sets bit 7 of the switch
 *   byte just before it powers down) or the connection drops.
 * - Resumes the same ride if the bike comes back within the auto-end time,
 *   otherwise saves it. The rider can also save or discard from the prompt.
 *
 * [tick] is driven once a second by the link service.
 */
object RideRecorder {
    private const val FRESH_MS = 3_000L
    private const val MIN_SAMPLES = 15
    private const val MIN_DISTANCE_KM = 0.1

    private val _state = MutableStateFlow(RecState())
    val state: StateFlow<RecState> = _state

    /** The most recently saved ride, for the "ride saved" prompt. */
    val lastSaved = MutableStateFlow<RideSummary?>(null)

    private val samples = ArrayList<RideSample>()

    @Volatile private var lat: Double? = null
    @Volatile private var lon: Double? = null
    @Volatile private var fixAt = 0L

    fun onLocation(latitude: Double, longitude: Double, now: Long) {
        lat = latitude
        lon = longitude
        fixAt = now
    }

    /** Last GPS fix from the last half hour, for the parked-bike location. */
    fun lastFix(): Pair<Double, Double>? {
        val la = lat
        val lo = lon
        return if (la != null && lo != null && System.currentTimeMillis() - fixAt < 30 * 60_000L) la to lo else null
    }

    /** Copy of the samples so far, for the live view. */
    @Synchronized
    fun snapshot(): List<RideSample> = ArrayList(samples)

    @Synchronized
    fun tick(now: Long, live: Boolean, store: RideStore) {
        val r = AppState.readings.value
        fun fresh(key: String): Long? = r[key]?.takeIf { now - it.at < FRESH_MS }?.raw
        val speed = fresh("speed")?.toInt()
        val switches = fresh("switches")?.toInt()
        val keyOff = switches != null && (switches and 0x80) != 0
        val flowing = live && speed != null && !keyOff
        val s = _state.value
        when (s.phase) {
            RecPhase.IDLE -> if (flowing && (speed ?: 0) > 0) {
                samples.clear()
                _state.value = RecState(RecPhase.RECORDING, startedAt = now)
                EventLog.i(TAG, "Ride started")
                sample(now)
            }
            RecPhase.RECORDING -> when {
                keyOff -> pause(now, "Key switched off")
                !live -> pause(now, "Bike disconnected")
                else -> sample(now)
            }
            RecPhase.PAUSED -> when {
                flowing -> {
                    _state.value = s.copy(phase = RecPhase.RECORDING, pausedAt = null, reason = "")
                    EventLog.i(TAG, "Ride resumed")
                    sample(now)
                }
                now - (s.pausedAt ?: now) > Prefs.autoEndMinutes * 60_000L -> finish(store, "auto")
            }
        }
    }

    private fun sample(now: Long) {
        val r = AppState.readings.value
        fun fresh(key: String): Long? = r[key]?.takeIf { now - it.at < FRESH_MS }?.raw
        val gearRaw = fresh("gear")?.toInt()
        val fix = now - fixAt < 5_000
        val smp = RideSample(
            t = now,
            speed = fresh("speed")?.toInt(),
            rpm = fresh("rpm")?.toInt(),
            gear = gearRaw?.let { if (it == 8) 0 else it }?.takeIf { it in 0..6 },
            engineTemp = fresh("engineTemp")?.let { if (it < 25) 0 else (it - 25).toInt() },
            battery = fresh("battery")?.let { it / 10f },
            throttle = fresh("throttle")?.let { (it / 2).toInt() },
            odo = fresh("odometer")?.let { it / 10.0 },
            lat = if (fix) lat else null,
            lon = if (fix) lon else null,
            inj = Prefs.injTotal,
        )
        samples += smp
        val s = _state.value
        _state.value = s.copy(
            distanceKm = RideStats.distanceKm(samples),
            maxSpeed = maxOf(s.maxSpeed, smp.speed ?: 0),
        )
    }

    private fun pause(now: Long, why: String) {
        _state.value = _state.value.copy(phase = RecPhase.PAUSED, pausedAt = now, reason = why)
        EventLog.i(TAG, "Ride paused: $why")
    }

    /** Saves the ride if it is worth keeping. Returns the summary, or null if discarded. */
    @Synchronized
    fun finish(store: RideStore, why: String): RideSummary? {
        if (_state.value.phase == RecPhase.IDLE) return null
        val copy = ArrayList(samples)
        val dist = RideStats.distanceKm(copy)
        samples.clear()
        _state.value = RecState()
        if (copy.size < MIN_SAMPLES || dist < MIN_DISTANCE_KM) {
            EventLog.i(TAG, "Ride too short to keep (${copy.size} s, ${"%.2f".format(dist)} km)")
            return null
        }
        val mileage = AppState.readings.value["avgMileageA"]?.raw?.toInt()
        return try {
            val summary = store.save(copy, mileage, Prefs.lastName ?: "")
            EventLog.i(TAG, "Ride saved ($why): ${"%.1f".format(summary.distanceKm)} km, ${summary.durationS / 60} min")
            lastSaved.value = summary
            summary
        } catch (e: Exception) {
            EventLog.w(TAG, "Could not save ride: $e")
            null
        }
    }

    @Synchronized
    fun discard() {
        samples.clear()
        _state.value = RecState()
        EventLog.i(TAG, "Ride discarded")
    }

    private const val TAG = "Ride"
}
