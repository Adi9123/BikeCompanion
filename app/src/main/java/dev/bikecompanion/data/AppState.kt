package dev.bikecompanion.data

import dev.bikecompanion.protocol.ApacheFields
import dev.bikecompanion.protocol.Inbound
import dev.bikecompanion.protocol.NavInstruction
import dev.bikecompanion.protocol.Scramble
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

enum class LinkPhase(val label: String) {
    IDLE("Not connected"),
    CONNECTING("Connecting"),
    DISCOVERING("Discovering services"),
    SUBSCRIBING("Subscribing"),
    LIVE("Connected"),
    RECONNECT_WAIT("Waiting to reconnect"),
    FAILED("Failed"),
}

data class LinkStatus(
    val phase: LinkPhase = LinkPhase.IDLE,
    val deviceName: String? = null,
    val address: String? = null,
    val detail: String = "",
    val rssi: Int? = null,
    val connectedAt: Long? = null,
    val attempts: Int = 0,
    val drops: Int = 0,
    val lastDrop: String? = null,
)

class FrameStat(
    val id: Int,
    val count: Long,
    val firstAt: Long,
    val lastAt: Long,
    val lastWire: ByteArray,
    val lastClear: ByteArray,
    /** Which byte positions differ from the previous frame with this id. */
    val changed: BooleanArray,
    /** Positions that have changed at least once this session. */
    val everChanged: BooleanArray,
    val checksumOk: Long,
    val scramble: Scramble?,
)

data class DecoderStatus(
    val locked: Scramble? = null,
    val passes: Map<Scramble, Long> = emptyMap(),
    val noScheme: Long = 0,
    val total: Long = 0,
    /** All-zero frames the cluster sends while it is still waking up. */
    val idleFrames: Long = 0,
    val txCount: Long = 0,
    val txRejected: Long = 0,
    val lastTx: String? = null,
    val outbound: Scramble? = null,
)

data class Reading(val raw: Long, val at: Long)

/** Process-wide state the UI observes. Written by the Bluetooth link. */
object AppState {
    val link = MutableStateFlow(LinkStatus())
    val readings = MutableStateFlow<Map<String, Reading>>(emptyMap())
    val frames = MutableStateFlow<Map<Int, FrameStat>>(emptyMap())
    val decoder = MutableStateFlow(DecoderStatus())

    /** Current navigation step from Google Maps (or a test), null when not navigating. */
    val nav = MutableStateFlow<NavInstruction?>(null)

    /** Text waiting to be shown on the cluster's message line (calls, messages, music, tests). */
    val clusterTexts = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Key of the incoming-call notification while the phone is ringing, else null. */
    val ringingCall = MutableStateFlow<String?>(null)

    /** Requests to decline the ringing call (from the bike's Info switch). */
    val declineCall = MutableSharedFlow<Unit>(extraBufferCapacity = 2)

    /** Missed calls currently in the notification shade; sent in the phone-status frame. */
    val missedCalls = MutableStateFlow(0)

    /** Whether the notification listener is connected, i.e. notification access is granted. */
    val navAccess = MutableStateFlow(false)

    fun resetSession() {
        readings.value = emptyMap()
        frames.value = emptyMap()
        decoder.value = DecoderStatus()
    }

    fun onInbound(inb: Inbound, now: Long) {
        frames.update { m ->
            val prev = m[inb.id]
            val size = inb.clear.size
            val changed = BooleanArray(size) { i ->
                prev != null && i < prev.lastClear.size && prev.lastClear[i] != inb.clear[i]
            }
            val ever = BooleanArray(size) { i ->
                changed[i] || (prev != null && i < prev.everChanged.size && prev.everChanged[i])
            }
            val stat = FrameStat(
                id = inb.id,
                count = (prev?.count ?: 0L) + 1,
                firstAt = prev?.firstAt ?: now,
                lastAt = now,
                lastWire = inb.wire,
                lastClear = inb.clear,
                changed = changed,
                everChanged = ever,
                checksumOk = (prev?.checksumOk ?: 0L) + if (inb.checksumOk) 1 else 0,
                scramble = inb.scramble,
            )
            m + (inb.id to stat)
        }
        decoder.update { d ->
            val passes = d.passes.toMutableMap()
            for (s in inb.passing) passes[s] = (passes[s] ?: 0L) + 1
            d.copy(
                locked = if (inb.checksumOk || inb.trusted) inb.scramble else d.locked,
                passes = passes,
                noScheme = d.noScheme + if (inb.passing.isEmpty()) 1 else 0,
                total = d.total + 1,
            )
        }
        if (inb.usable) {
            val values = dev.bikecompanion.protocol.Fields.extract(inb.clear)
            if (values.isNotEmpty()) {
                readings.update { r -> r + values.mapValues { (_, v) -> Reading(v, now) } }
            }
        }
    }
}
