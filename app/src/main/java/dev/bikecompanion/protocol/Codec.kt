package dev.bikecompanion.protocol

import java.util.UUID

/** GATT layout of the SmartXonnect cluster. Shared by every petrol TVS model. */
object Gatt {
    val SERVICE: UUID = UUID.fromString("5456534D-5647-5341-5342-454E544F5251")

    /** Cluster -> phone, delivered as notifications. */
    val NOTIFY: UUID = UUID.fromString("00005354-0000-1000-8000-00805f9b34fb")

    /** Phone -> cluster. */
    val WRITE: UUID = UUID.fromString("00005352-0000-1000-8000-00805f9b34fb")

    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Clusters advertise a name starting with this, and usually no service UUID. */
    const val NAME_PREFIX = "TVSBT"
}

/**
 * How the payload of a frame is scrambled on the wire.
 *
 * Apache clusters XOR every byte between the two-byte header and the end byte
 * (that is, the 16 data bytes and the checksum) with a single constant key.
 * Most Apache variants use 0xEA; the 2V family uses 0xEB. The start byte, the
 * frame id and the 0xFF end byte are always sent in the clear.
 */
enum class Scramble(val key: Int, val label: String) {
    XOR_EA(0xEA, "XOR 0xEA (Apache 4V / most Apache)"),
    XOR_EB(0xEB, "XOR 0xEB (Apache 2V)"),
    NONE(0x00, "None (plain)"),
}

object Codec {
    const val FRAME_SIZE = 20
    const val START_IN = 0x5A
    const val START_OUT = 0x5B
    const val END = 0xFF

    fun u8(b: Byte): Int = b.toInt() and 0xFF

    /** Apply (or remove — XOR is its own inverse) a scramble. Never mutates [frame]. */
    fun xor(frame: ByteArray, scramble: Scramble): ByteArray {
        val out = frame.copyOf()
        if (scramble.key == 0) return out
        for (i in 2 until out.size - 1) {
            out[i] = (out[i].toInt() xor scramble.key).toByte()
        }
        return out
    }

    /**
     * Checksum for a frame: 255 minus the sum of every byte before the
     * checksum position, modulo 256. The checksum sits in the second-to-last
     * byte and the last byte is 0xFF.
     */
    fun checksum(frame: ByteArray): Int {
        var sum = 0
        for (i in 0 until frame.size - 2) sum += u8(frame[i])
        return 255 - (sum % 256)
    }

    fun checksumOk(frame: ByteArray): Boolean =
        frame.size >= 4 && u8(frame[frame.size - 2]) == checksum(frame)

    /** Copy of [frame] with the checksum and end byte filled in. */
    fun withChecksum(frame: ByteArray): ByteArray {
        val out = frame.copyOf()
        out[out.size - 2] = checksum(out).toByte()
        out[out.size - 1] = END.toByte()
        return out
    }

    /** Unsigned big-endian read of [length] bytes at 0-based [index], or null if out of range. */
    fun be(frame: ByteArray, index: Int, length: Int): Long? {
        if (index < 0 || length <= 0 || index + length > frame.size) return null
        var v = 0L
        for (i in index until index + length) v = (v shl 8) or u8(frame[i]).toLong()
        return v
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(u8(it)) }
}
