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
) {
    val id: Int get() = if (wire.size > 1) Codec.u8(wire[1]) else -1

    /** Whether the decoded values can be trusted enough to display. */
    val usable: Boolean get() = checksumOk || forced
}

/**
 * Removes the wire scramble from incoming frames.
 *
 * The scheme is learned from the frames themselves: each frame is tried under
 * every [Scramble], and the one that yields a valid checksum wins. Once a
 * scheme has produced a valid frame it is locked in and preferred, so a rare
 * frame that happens to validate under two schemes cannot flip it.
 */
class FrameDecoder {
    /** When set, every frame is decoded with this scheme and detection is skipped. */
    @Volatile
    var forced: Scramble? = null

    @Volatile
    var locked: Scramble? = null
        private set

    fun reset() {
        locked = null
    }

    fun decode(wire: ByteArray): Inbound? {
        if (wire.size < 4) return null
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
}
