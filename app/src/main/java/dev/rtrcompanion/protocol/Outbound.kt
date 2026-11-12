package dev.rtrcompanion.protocol

/** What the phone reports to the cluster in the keepalive frame. */
data class PhoneStatus(
    val signalBars: Int,
    val batteryBars: Int,
    val hour12: Int,
    val minute: Int,
    val second: Int,
    val pm: Boolean,
    val day: Int,
    val month: Int,
    val year2: Int,
    val missedCalls: Int = 0,
    val networkType: Int = 0,
    val callState: Int = 0,
    /** 120 is what the cluster is sent when no overspeed alert is configured. */
    val overspeedKmh: Int = 120,
    val voiceAssist: Int = 0,
)

/**
 * Frames the phone sends. All are built in the clear; the link layer applies
 * the wire scramble just before writing.
 *
 * Deliberately limited to frames that only feed the display. Nothing here
 * writes calibration, ride-mode or vehicle settings.
 */
object Outbound {
    const val ID_KEEPALIVE = 0x4A
    const val ID_TEXT_LINE = 0x4C
    const val ID_RIDER_NAME = 0x52
    const val TEXT_MAX = 17

    /**
     * Keepalive / phone-status frame, sent every 2 seconds while connected.
     *
     * Byte map (0-based): 2 signal and battery as two nibbles, 3 overspeed
     * limit, 6-8 time, 9 PM flag, 10 missed calls, 11 network type,
     * 12-14 date, 16 voice assist, 18 call state, 19 end byte.
     */
    fun keepalive(s: PhoneStatus): ByteArray {
        val f = ByteArray(Codec.FRAME_SIZE)
        f[0] = Codec.START_OUT.toByte()
        f[1] = ID_KEEPALIVE.toByte()
        f[2] = ((s.signalBars.coerceIn(0, 9) shl 4) or s.batteryBars.coerceIn(0, 9)).toByte()
        f[3] = s.overspeedKmh.coerceIn(0, 255).toByte()
        f[6] = s.hour12.coerceIn(0, 23).toByte()
        f[7] = s.minute.coerceIn(0, 59).toByte()
        f[8] = s.second.coerceIn(0, 59).toByte()
        f[9] = (if (s.pm) 1 else 0).toByte()
        f[10] = s.missedCalls.coerceIn(0, 255).toByte()
        f[11] = s.networkType.coerceIn(0, 255).toByte()
        f[12] = s.day.coerceIn(1, 31).toByte()
        f[13] = s.month.coerceIn(1, 12).toByte()
        f[14] = s.year2.coerceIn(0, 99).toByte()
        f[16] = (s.voiceAssist.coerceIn(0, 9) shl 4).toByte()
        f[18] = s.callState.coerceIn(0, 255).toByte()
        f[19] = Codec.END.toByte()
        return f
    }

    /** Only letters, digits and space survive; anything else is shown as X. */
    fun sanitize(text: String): String {
        val mapped = text.map { c ->
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == ' ') c else 'X'
        }.joinToString("")
        return mapped.take(TEXT_MAX).trim()
    }

    /** 5B <id> <up to 17 characters> <zero padding> FF */
    fun text(id: Int, text: String): ByteArray {
        val f = ByteArray(Codec.FRAME_SIZE)
        f[0] = Codec.START_OUT.toByte()
        f[1] = id.toByte()
        val chars = sanitize(text).toByteArray(Charsets.US_ASCII)
        chars.copyInto(f, destinationOffset = 2)
        f[19] = Codec.END.toByte()
        return f
    }

    fun riderName(name: String): ByteArray = text(ID_RIDER_NAME, name)
}
