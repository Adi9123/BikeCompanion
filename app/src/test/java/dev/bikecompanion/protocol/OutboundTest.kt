package dev.bikecompanion.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class OutboundTest {

    private fun u(b: Byte) = b.toInt() and 0xFF

    @Test
    fun keepaliveLayout() {
        val f = Outbound.keepalive(
            PhoneStatus(
                signalBars = 4, batteryBars = 3,
                hour12 = 9, minute = 41, second = 7, pm = true,
                day = 12, month = 11, year2 = 26,
                missedCalls = 2,
            )
        )
        assertEquals(20, f.size)
        assertEquals(0x5B, u(f[0]))
        assertEquals(0x4A, u(f[1]))
        assertEquals(0x43, u(f[2]))
        assertEquals(120, u(f[3]))
        assertEquals(9, u(f[6]))
        assertEquals(41, u(f[7]))
        assertEquals(7, u(f[8]))
        assertEquals(1, u(f[9]))
        assertEquals(2, u(f[10]))
        assertEquals(12, u(f[12]))
        assertEquals(11, u(f[13]))
        assertEquals(26, u(f[14]))
        assertEquals(0xFF, u(f[19]))
    }

    @Test
    fun scrambledKeepaliveKeepsHeader() {
        val clear = Outbound.keepalive(
            PhoneStatus(4, 3, 9, 41, 7, true, 12, 11, 26)
        )
        val wire = Codec.xor(clear, Scramble.XOR_EA)
        assertEquals(0x5B, u(wire[0]))
        assertEquals(0x4A, u(wire[1]))
        assertEquals(0x43 xor 0xEA, u(wire[2]))
        assertEquals(0xFF, u(wire[19]))
    }

    @Test
    fun textIsSanitisedAndPadded() {
        val f = Outbound.riderName("Rider 1!")
        assertEquals(0x5B, u(f[0]))
        assertEquals(0x52, u(f[1]))
        assertEquals("Rider 1X", String(f, 2, 8, Charsets.US_ASCII))
        assertEquals(0, u(f[10]))
        assertEquals(0xFF, u(f[19]))
    }

    @Test
    fun textIsCutAt17Characters() {
        assertEquals(17, Outbound.sanitize("ABCDEFGHIJKLMNOPQRSTUVWXYZ").length)
    }
}
