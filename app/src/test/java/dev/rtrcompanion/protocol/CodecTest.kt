package dev.rtrcompanion.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecTest {

    /** A clear frame with values at 1-based positions and a valid checksum. */
    private fun frame(id: Int, vararg data: Pair<Int, Int>): ByteArray {
        val f = ByteArray(Codec.FRAME_SIZE)
        f[0] = Codec.START_IN.toByte()
        f[1] = id.toByte()
        for ((pos, v) in data) f[pos - 1] = v.toByte()
        return Codec.withChecksum(f)
    }

    @Test
    fun checksumMatchesTheDefinition() {
        val f = frame(0x10, 3 to 42, 17 to 0x0B, 18 to 0xB8)
        var sum = 0
        for (i in 0..17) sum += f[i].toInt() and 0xFF
        assertEquals(255, (f[18].toInt() and 0xFF) + sum % 256)
        assertEquals(0xFF, f[19].toInt() and 0xFF)
        assertTrue(Codec.checksumOk(f))
    }

    @Test
    fun corruptedFrameFailsChecksum() {
        val f = frame(0x10, 3 to 42)
        f[5] = (f[5] + 1).toByte()
        assertFalse(Codec.checksumOk(f))
    }

    @Test
    fun xorLeavesHeaderAndEndByteAlone() {
        val f = frame(0x11, 6 to 3)
        val w = Codec.xor(f, Scramble.XOR_EA)
        assertEquals(f[0], w[0])
        assertEquals(f[1], w[1])
        assertEquals(f[19], w[19])
        assertEquals((f[5].toInt() xor 0xEA).toByte(), w[5])
        assertEquals((f[18].toInt() xor 0xEA).toByte(), w[18])
        assertArrayEquals(f, Codec.xor(w, Scramble.XOR_EA))
    }

    @Test
    fun bigEndianRead() {
        val f = frame(0x10, 4 to 0x01, 5 to 0x02, 6 to 0x03)
        assertEquals(0x010203L, Codec.be(f, 3, 3))
        assertEquals(null, Codec.be(f, 19, 2))
    }

    @Test
    fun decoderLearnsTheEaScrambleAndDecodesRideFrame() {
        // speed 42, odometer 12345.6 km (123456 = 0x01E240), rpm 3000 (0x0BB8)
        val clear = frame(0x10, 3 to 42, 4 to 0x01, 5 to 0xE2, 6 to 0x40, 17 to 0x0B, 18 to 0xB8)
        val wire = Codec.xor(clear, Scramble.XOR_EA)
        val d = FrameDecoder()
        val inb = d.decode(wire)
        assertNotNull(inb)
        inb!!
        assertEquals(Scramble.XOR_EA, inb.scramble)
        assertTrue(inb.checksumOk)
        assertArrayEquals(clear, inb.clear)
        assertEquals(Scramble.XOR_EA, d.locked)

        val v = ApacheFields.extract(inb.clear)
        assertEquals(42L, v["speed"])
        assertEquals(123456L, v["odometer"])
        assertEquals("12345.6", ApacheFields.format(ApacheFields.spec("odometer")!!, v["odometer"]!!))
        assertEquals(3000L, v["rpm"])
    }

    @Test
    fun decoderLearnsTheEbScramble() {
        val status = frame(0x11, 6 to 4)
        val inb = FrameDecoder().decode(Codec.xor(status, Scramble.XOR_EB))!!
        assertEquals(Scramble.XOR_EB, inb.scramble)
        assertTrue(inb.checksumOk)
        val v = ApacheFields.extract(inb.clear)
        assertEquals("4", ApacheFields.format(ApacheFields.spec("gear")!!, v["gear"]!!))

        // engine frame: engine temperature at position 9 (raw - 25), battery at 12 (tenths of a volt)
        val engine = frame(0x18, 9 to 110, 12 to 0x92)
        val e = ApacheFields.extract(FrameDecoder().decode(Codec.xor(engine, Scramble.XOR_EB))!!.clear)
        assertEquals("85", ApacheFields.format(ApacheFields.spec("engineTemp")!!, e["engineTemp"]!!))
        assertEquals("14.6", ApacheFields.format(ApacheFields.spec("battery")!!, e["battery"]!!))
    }

    /** A frame exactly as an RTR 160 4V sent it (the empty lap-timer frame). */
    @Test
    fun realRtr160Frame() {
        val wire = ByteArray(20) { 0xEB.toByte() }
        wire[0] = 0x5A
        wire[1] = 0x16
        wire[18] = 0x64
        wire[19] = 0xFF.toByte()
        val inb = FrameDecoder().decode(wire)!!
        assertEquals(Scramble.XOR_EB, inb.scramble)
        assertTrue(inb.checksumOk)
        assertEquals(0x8F, inb.clear[18].toInt() and 0xFF)
        for (i in 2..17) assertEquals(0, inb.clear[i].toInt())
    }

    @Test
    fun tripsFrame() {
        // Trip A 6755 km, trip B 120 km, average mileage 33 and 41 km/l
        val f = frame(0x19, 6 to 0x1A, 7 to 0x63, 10 to 120, 14 to 33, 15 to 41)
        val v = ApacheFields.extract(f)
        assertEquals(6755L, v["tripA"])
        assertEquals(120L, v["tripB"])
        assertEquals(33L, v["avgMileageA"])
        assertEquals(41L, v["avgMileageB"])
    }

    @Test
    fun tripFIsTenthsOfAKilometre() {
        val f = frame(0x10, 15 to 0x14, 16 to 0x84)
        val v = ApacheFields.extract(f)
        assertEquals("525.2", ApacheFields.format(ApacheFields.spec("tripF")!!, v["tripF"]!!))
    }

    @Test
    fun forcedSchemeIsUsedEvenWhenChecksumFails() {
        val clear = frame(0x10, 3 to 50)
        clear[18] = (clear[18] + 1).toByte()
        val d = FrameDecoder().apply { forced = Scramble.NONE }
        val inb = d.decode(clear)!!
        assertEquals(Scramble.NONE, inb.scramble)
        assertFalse(inb.checksumOk)
        assertTrue(inb.usable)
    }

    @Test
    fun undecodableFrameIsNotUsable() {
        val junk = ByteArray(20) { (it * 37).toByte() }
        junk[0] = 0x5A
        junk[1] = 0x10
        val inb = FrameDecoder().decode(junk)!!
        if (inb.passing.isEmpty()) assertFalse(inb.usable)
    }

    @Test
    fun formatting() {
        val gear = ApacheFields.spec("gear")!!
        assertEquals("N", ApacheFields.format(gear, 0))
        assertEquals("N", ApacheFields.format(gear, 8))
        assertEquals("3", ApacheFields.format(gear, 3))
        val temp = ApacheFields.spec("engineTemp")!!
        assertEquals("0", ApacheFields.format(temp, 20))
        assertEquals("65", ApacheFields.format(temp, 90))
        assertEquals("12.6", ApacheFields.format(ApacheFields.spec("battery")!!, 126))
    }

    @Test
    fun fieldKeysAreUnique() {
        val keys = ApacheFields.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }
}
