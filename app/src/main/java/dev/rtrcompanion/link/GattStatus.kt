package dev.rtrcompanion.link

/** Human-readable meaning of the status code Android reports when a link drops. */
object GattStatus {
    fun describe(status: Int): String = when (status) {
        0 -> "closed normally (0)"
        8 -> "link lost: out of range or bike switched off (8)"
        19 -> "the bike ended the connection (19)"
        22 -> "the phone ended the connection (22)"
        34 -> "radio timeout (34)"
        62 -> "could not establish the link (62)"
        133 -> "generic Bluetooth error (133)"
        147 -> "connection timed out (147)"
        257 -> "Bluetooth stack failure (257)"
        else -> "status $status"
    }
}
