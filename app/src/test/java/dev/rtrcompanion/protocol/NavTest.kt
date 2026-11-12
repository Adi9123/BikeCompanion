package dev.rtrcompanion.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavTest {

    private fun u(b: Byte) = b.toInt() and 0xFF

    @Test
    fun parsesDistanceInstructionAndSummary() {
        val n = NavParser.parse(listOf("300 m", "Turn left onto Example Road", "12 min · 4.5 km · 10:42 ETA"))
        assertNotNull(n)
        n!!
        assertEquals(Maneuver.LEFT, n.maneuver)
        assertEquals(300, n.distanceToTurnM)
        assertEquals(4500, n.remainingM)
        assertEquals(12, n.remainingMin)
        assertEquals("Turn left onto Example Road", n.text)
    }

    @Test
    fun parsesKilometresAndHours() {
        val n = NavParser.parse(listOf("1.2 km", "Keep right at the fork", "1 hr 5 min · 62 km"))!!
        assertEquals(Maneuver.KEEP_RIGHT, n.maneuver)
        assertEquals(1200, n.distanceToTurnM)
        assertEquals(62000, n.remainingM)
        assertEquals(65, n.remainingMin)
    }

    @Test
    fun maneuverKeywords() {
        assertEquals(Maneuver.SLIGHT_RIGHT, NavParser.maneuverOf("Slight right toward Example Nagar"))
        assertEquals(Maneuver.UTURN_RIGHT, NavParser.maneuverOf("Make a U-turn"))
        assertEquals(Maneuver.ROUNDABOUT_STRAIGHT, NavParser.maneuverOf("At the roundabout, take the 2nd exit"))
        assertEquals(Maneuver.ARRIVE, NavParser.maneuverOf("You have arrived"))
        assertEquals(Maneuver.DEPART, NavParser.maneuverOf("Head north on Example Road"))
        assertEquals(Maneuver.STRAIGHT, NavParser.maneuverOf("Continue onto Example Highway"))
        assertNull(NavParser.maneuverOf("Navigating"))
    }

    @Test
    fun nonNavigationTextIsIgnored() {
        assertNull(NavParser.parse(listOf("Google Maps", "Tap to return")))
    }

    @Test
    fun controlFrameLayout() {
        val n = NavInstruction(Maneuver.RIGHT, 310, 4500, 12, "Turn right")
        val (control, text) = NavFrames.build(n)
        assertEquals(20, control.size)
        assertEquals(0x5A, u(control[0]))
        assertEquals(0x4E, u(control[1]))
        assertEquals(300, (u(control[2]) shl 8) or u(control[3]))   // rounded to 50 m
        assertEquals(12, (u(control[4]) shl 8) or u(control[5]))
        assertEquals(4500, (u(control[6]) shl 16) or (u(control[7]) shl 8) or u(control[8]))
        assertEquals(Maneuver.RIGHT.code, u(control[9]))
        assertEquals(NavFrames.STATUS_ACTIVE, u(control[11]))
        assertEquals(1, u(control[14]))   // turn coming up
        assertTrue(Codec.checksumOk(control))
        assertEquals(0xFF, u(control[19]))

        assertEquals(0x5B, u(text[0]))
        assertEquals(0x4F, u(text[1]))
        assertEquals("Turn right", String(text, 2, 10, Charsets.US_ASCII))
        assertTrue(Codec.checksumOk(text))
    }

    @Test
    fun promptBytes() {
        assertEquals(3, u(NavFrames.build(NavInstruction(Maneuver.LEFT, 40, 900, 2, "")).first[14]))
        assertEquals(4, u(NavFrames.build(NavInstruction(Maneuver.RIGHT, 40, 900, 2, "")).first[14]))
        assertEquals(5, u(NavFrames.build(NavInstruction(Maneuver.STRAIGHT, 3000, 9000, 9, "")).first[14]))
        val arrived = NavFrames.build(NavInstruction(Maneuver.UNKNOWN, 0, 20, 0, "")).first
        assertEquals(NavFrames.ARRIVED_CODE, u(arrived[9]))
        assertEquals(8, u(arrived[14]))
    }

    @Test
    fun navTextKeepsSixteenCharacters() {
        val f = Outbound.navText("Turn left onto Example Road")
        assertEquals("Turn left onto E", String(f, 2, 16, Charsets.US_ASCII))
        assertTrue(Codec.checksumOk(f))
    }

    @Test
    fun navEndIsEmptyControlFrame() {
        val f = Outbound.navEnd()
        assertEquals(0x5A, u(f[0]))
        assertEquals(0x4E, u(f[1]))
        for (i in 2..17) assertEquals(0, u(f[i]))
        assertTrue(Codec.checksumOk(f))
    }
}
