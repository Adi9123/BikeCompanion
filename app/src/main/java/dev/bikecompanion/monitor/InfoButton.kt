package dev.bikecompanion.monitor

import android.os.Handler
import android.os.Looper
import dev.bikecompanion.data.Prefs
import dev.bikecompanion.garage.BitRef
import dev.bikecompanion.garage.Switch
import dev.bikecompanion.garage.SwitchLearn
import dev.bikecompanion.log.EventLog

/** Things the handlebar Info switch can do. The rider assigns them to press types. */
enum class InfoAction(val label: String, val detail: String) {
    NONE("Nothing", "Only the cluster's own function"),
    SHOW_RANGE("Show range", "Range to empty on the cluster"),
    SHOW_FUEL("Show fuel", "Litres left on the cluster"),
    SHOW_RIDE("Show ride so far", "Distance and time of this ride on the cluster"),
    SHOW_TIME("Show time", "Phone time on the cluster"),
    SAVE_SPOT("Save this spot", "Remember where you are; shown on the Map"),
    NEXT_TRACK("Next song", "In whatever music app is playing"),
    PREVIOUS_TRACK("Previous song", "In whatever music app is playing"),
    PLAY_PAUSE("Play / pause", "In whatever music app is playing"),
    PETROL_PUMP("Nearest petrol pump", "Phone notification that opens Maps"),
    TOGGLE_DIRECTIONS("Directions on/off", "Turn Google Maps directions on the cluster on or off"),
    DECLINE_CALL("Decline call", "Reject the ringing call"),
}

enum class Press { SHORT, DOUBLE, LONG }

/**
 * Turns raw button-down / button-up events into short, double and long
 * presses, then picks the action: context first (ringing call, fuel
 * reserve), then the rider's choice for that press type.
 *
 * Fed from every frame by the link, so presses shorter than a second are
 * not missed.
 */
object InfoButton {
    private const val LONG_MS = 1_500L
    private const val DOUBLE_GAP_MS = 700L

    private val main = Handler(Looper.getMainLooper())
    private var ref: Pair<BitRef, Boolean>? = null
    private var refText: String? = null
    private var down = false
    private var downAt = 0L
    private var pendingSingle: Runnable? = null

    /** Context the service provides. */
    @Volatile var isRinging: () -> Boolean = { false }
    @Volatile var onReserve: () -> Boolean = { false }

    /** Runs an action; set by the service. */
    @Volatile var perform: ((InfoAction, Press) -> Unit)? = null

    fun action(name: String): InfoAction = InfoAction.entries.firstOrNull { it.name == name } ?: InfoAction.NONE

    /** Called on the main thread for every decoded frame. */
    fun onFrame(frameId: Int, clear: ByteArray, now: Long) {
        val text = Prefs.switchMap
        if (text != refText) {
            refText = text
            ref = SwitchLearn.decode(text)[Switch.CALL_BUTTON]
        }
        val (bit, activeHigh) = ref ?: return
        if (bit.frame != frameId) return
        val pressed = (bit.read(clear) ?: return) == activeHigh
        if (pressed && !down) {
            down = true
            downAt = now
        } else if (!pressed && down) {
            down = false
            released(now - downAt)
        }
    }

    private fun released(heldMs: Long) {
        if (heldMs >= LONG_MS) {
            cancelPending()
            fire(Press.LONG)
            return
        }
        val waiting = pendingSingle
        if (waiting != null) {
            cancelPending()
            fire(Press.DOUBLE)
            return
        }
        // During a call, decline straight away rather than waiting for a possible second press.
        if (isRinging() && Prefs.infoDeclinesCalls) {
            fire(Press.SHORT)
            return
        }
        val r = Runnable {
            pendingSingle = null
            fire(Press.SHORT)
        }
        pendingSingle = r
        main.postDelayed(r, DOUBLE_GAP_MS)
    }

    private fun cancelPending() {
        pendingSingle?.let { main.removeCallbacks(it) }
        pendingSingle = null
    }

    private fun fire(press: Press) {
        val action = when {
            press == Press.SHORT && isRinging() && Prefs.infoDeclinesCalls -> InfoAction.DECLINE_CALL
            press == Press.LONG && onReserve() && Prefs.infoPumpOnReserve -> InfoAction.PETROL_PUMP
            press == Press.SHORT -> action(Prefs.infoShort)
            press == Press.DOUBLE -> action(Prefs.infoDouble)
            else -> action(Prefs.infoLong)
        }
        EventLog.i("Info", "Info switch ${press.name.lowercase()} press -> ${action.label}")
        if (action != InfoAction.NONE) perform?.invoke(action, press)
    }
}
