package dev.bikecompanion.protocol

/** One frame as received, plus what we worked out about it. */
class Inbound(
    /** Bytes exactly as they arrived over Bluetooth. */
    val wire: ByteArray,
    /** Bytes after removing the scramble that was chosen. */
    val clear: ByteArray,
    /** The scramble used to produce [clear]; null when none could be chosen. */
    val scramble: Scramble?,
    /** True when [clear] carries a valid checksum. */
    val checksumOk: Boolean,
    /** Every scramble under which this frame's checksum validates. */
    val passing: Set<Scramble>,
    /** True when the user forced a scramble instead of letting it be detected. */
    val forced: Boolean,
    /** True when the scheme was settled without a checksum (models whose frames have none). */
    val trusted: Boolean = false,
) {
    val id: Int get() = if (wire.size > 1) Codec.u8(wire[1]) else -1

    /** Whether the decoded values can be trusted enough to display. */
    val usable: Boolean get() = checksumOk || forced || trusted
}

/**
 * Removes the wire scramble from incoming frames.
 *
 * With a checksum (Apache): each frame is tried under every [Scramble], and
 * the one that yields a valid checksum wins. Once a scheme has produced a
 * valid frame it is locked in and preferred, so a rare frame that happens to
 * validate under two schemes cannot flip it.
 *
 * Without a checksum (Raider, Jupiter, Ntorq: byte 19 is data): the right
 * scheme is the one that turns the unused and idle fields back into zeros, so
 * zero bytes are counted under every scheme over the first frames and the
 * clear winner is locked in.
 */
class FrameDecoder {
    /** When set, every frame is decoded with this scheme and detection is skipped. */
    @Volatile
    var forced: Scramble? = null

    /** False for models whose frames carry no checksum. */
    @Volatile
    var checksummed: Boolean = true

    @Volatile
    var locked: Scramble? = null
        private set

    private val zeroScore = HashMap<Scramble, Int>()
    private var scored = 0

    fun reset() {
        locked = null
        zeroScore.clear()
        scored = 0
    }

    fun decode(wire: ByteArray): Inbound? {
        if (wire.size < 4) return null
        return if (checksummed) withChecksum(wire) else withoutChecksum(wire)
    }

    private fun withChecksum(wire: ByteArray): Inbound {
        val passing = Scramble.entries.filter { Codec.checksumOk(Codec.xor(wire, it)) }.toSet()
        val force = forced
        val chosen: Scramble? = when {
            force != null -> force
            locked != null && locked in passing -> locked
            passing.isNotEmpty() -> Scramble.entries.first { it in passing }
            else -> locked
        }
        if (force == null && chosen != null && chosen in passing) locked = chosen
        val clear = if (chosen != null) Codec.xor(wire, chosen) else wire.copyOf()
        return Inbound(
            wire = wire.copyOf(),
            clear = clear,
            scramble = chosen,
            checksumOk = chosen != null && chosen in passing,
            passing = passing,
            forced = force != null,
        )
    }

    private fun withoutChecksum(wire: ByteArray): Inbound {
        val force = forced
        if (force == null && locked == null) {
            for (s in Scramble.entries) zeroScore[s] = (zeroScore[s] ?: 0) + zeros(Codec.xor(wire, s))
            scored++
            pick()?.let { locked = it }
        }
        val chosen = force ?: locked
        val clear = if (chosen != null) Codec.xor(wire, chosen) else wire.copyOf()
        return Inbound(
            wire = wire.copyOf(),
            clear = clear,
            scramble = chosen,
            checksumOk = false,
            passing = emptySet(),
            forced = force != null,
            trusted = force == null && chosen != null,
        )
    }

    /** Zero bytes in the data area (positions 3 to 19). */
    private fun zeros(clear: ByteArray): Int {
        var n = 0
        for (i in 2 until minOf(clear.size, 19)) if (clear[i].toInt() == 0) n++
        return n
    }

    /** The winning scheme once it leads clearly over a few frames, else null. */
    internal fun pick(): Scramble? {
        if (scored < MIN_FRAMES) return null
        val ranked = zeroScore.entries.sortedByDescending { it.value }
        val best = ranked.getOrNull(0) ?: return null
        val second = ranked.getOrNull(1)?.value ?: 0
        // a clear lead: at least two more zero bytes per frame on average than the runner-up
        return if (best.value - second >= 2 * scored) best.key else null
    }

    private companion object {
        const val MIN_FRAMES = 4
    }
}
