package dev.bikecompanion.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    /** A 20-byte frame with values at 1-based positions, the way the layouts are written. */
    private fun frame(id: Int, vararg values: Pair<Int, Int>): ByteArray {
        val f = ByteArray(20)
        f[0] = 0x5A
        f[1] = id.toByte()
        for ((pos, v) in values) f[pos - 1] = v.toByte()
        f[19] = 0xFF.toByte()
        return f
    }

    @Test
    fun scooterRideFrameIsShiftedFromByte12() {
        // rpm 5000 = 0x1388 at positions 18-19, trip F 12.3 km = 123 at 15-17
        val f = frame(0x10, 3 to 42, 7 to 5, 13 to 0x80, 14 to 95, 17 to 123, 18 to 0x13, 19 to 0x88)
        val v = Fields.extract(f, Family.SCOOTER)
        assertEquals(42L, v["speed"])
        assertEquals(5000L, v["rpm"])
        assertEquals(123L, v["tripF"])
        assertEquals(0x80L, v["switches"])
        assertEquals(95L, v["zeroTo60"])
        // the Apache table would have read the wrong bytes for the same frame
        assertEquals((123L shl 8) or 0x13L, Fields.extract(f, Family.APACHE)["rpm"])
    }

    @Test
    fun scootersHaveNoGearAndApacheHasNoEconomy() {
        assertTrue(Fields.layout(Family.SCOOTER).none { it.key == "gear" })
        assertTrue(Fields.layout(Family.RAIDER).any { it.key == "gear" })
        assertTrue(Fields.layout(Family.APACHE).none { it.key == "bikeRange" })
        assertTrue(Fields.layout(Family.RAIDER).any { it.key == "bikeRange" })
        assertTrue(Fields.layout(Family.APACHE).any { it.key == "tripA" })
        assertTrue(Fields.layout(Family.RAIDER).none { it.key == "tripA" || it.key == "mode" })
    }

    @Test
    fun raiderEconomyFrame() {
        // range 245 km at 12-13, average 52 km/l at 9, idle stops 1234 at 16-18
        val f = frame(0x19, 9 to 52, 10 to 48, 12 to 0, 13 to 245, 16 to 0, 17 to 0x04, 18 to 0xD2)
        val v = Fields.extract(f, Family.RAIDER)
        assertEquals(245L, v["bikeRange"])
        assertEquals(52L, v["avgEconomy"])
        assertEquals(48L, v["instEconomy"])
        assertEquals(1234L, v["issCount"])
    }

    @Test
    fun everyUsableFamilyHasTheCoreValues() {
        for (f in listOf(Family.APACHE, Family.RAIDER, Family.SCOOTER)) {
            val keys = Fields.layout(f).map { it.key }.toSet()
            for (k in listOf("speed", "odometer", "fuel", "rpm", "engineTemp", "battery", "injVolume", "switches")) {
                assertTrue("$f lacks $k", k in keys)
            }
            assertEquals("$f has a duplicate key", keys.size, Fields.layout(f).size)
        }
    }

    @Test
    fun modelsAreGroupedHonestly() {
        assertEquals(BikeModel.APACHE_RTR_160_4V, BikeModel.DEFAULT)
        assertEquals(Support.TESTED, BikeModel.RAIDER_125.support)
        assertTrue(BikeModel.entries.filter { it.support == Support.UNSUPPORTED }.all { it.family == Family.NONE })
        assertTrue(BikeModel.entries.filter { it.support != Support.UNSUPPORTED }.none { it.family == Family.NONE })
        assertEquals(BikeModel.DEFAULT, BikeModel.of("no such bike"))
    }

    // ---------------------------------------------------- frames without a checksum

    private fun scooterFrames(scheme: Scramble): List<ByteArray> = (0 until 6).map { i ->
        Codec.xor(frame(0x10, 3 to 30 + i, 4 to 0, 5 to 0x30, 6 to 0x39, 7 to 4, 18 to 0x0B, 19 to 0xB8), scheme)
    }

    @Test
    fun learnsTheScrambleWithoutAChecksum() {
        for (scheme in Scramble.entries) {
            val d = FrameDecoder().apply { checksummed = false }
            var last: Inbound? = null
            for (w in scooterFrames(scheme)) last = d.decode(w)
            assertEquals(scheme, d.locked)
            val inb = last!!
            assertTrue(inb.usable)
            assertFalse(inb.checksumOk)
            assertEquals(35L, Fields.extract(inb.clear, Family.SCOOTER)["speed"])
            assertEquals(3000L, Fields.extract(inb.clear, Family.SCOOTER)["rpm"])
        }
    }

    @Test
    fun doesNotGuessFromTooFewFrames() {
        val d = FrameDecoder().apply { checksummed = false }
        d.decode(scooterFrames(Scramble.XOR_EB).first())
        assertNull(d.locked)
        assertFalse(d.decode(scooterFrames(Scramble.XOR_EB)[1])!!.usable)
    }
}
