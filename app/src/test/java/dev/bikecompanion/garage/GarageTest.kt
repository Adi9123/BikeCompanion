package dev.bikecompanion.garage

import dev.bikecompanion.protocol.Outbound
import dev.bikecompanion.ride.RideSample
import dev.bikecompanion.ride.RideStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GarageTest {

    private fun status(b14: Int): Map<Int, ByteArray> {
        val f = ByteArray(20)
        f[0] = 0x5A
        f[1] = 0x11
        f[13] = b14.toByte()
        return mapOf(0x11 to f)
    }

    @Test
    fun learnsABlinkingBit() {
        val baseline = SwitchLearn.fractions(List(8) { status(0x10) })
        // indicator: bit 5 of byte 14 blinks on and off
        val action = SwitchLearn.fractions(List(8) { i -> status(if (i % 2 == 0) 0x30 else 0x10) })
        val best = SwitchLearn.best(baseline, action, emptySet())
        assertNotNull(best)
        assertEquals(BitRef(0x11, 14, 5), best!!.first)
        assertTrue(best.second)
    }

    @Test
    fun nothingLearnedWithoutChange() {
        val same = SwitchLearn.fractions(List(8) { status(0x10) })
        assertNull(SwitchLearn.best(same, same, emptySet()))
    }

    @Test
    fun switchMapRoundTrip() {
        val m = mapOf(Switch.LEFT to (BitRef(0x11, 14, 5) to true), Switch.SIDE_STAND to (BitRef(0x11, 4, 6) to false))
        assertEquals(m, SwitchLearn.decode(SwitchLearn.encode(m)))
    }

    @Test
    fun serviceDue() {
        val oil = MaintItem("oil", "Engine oil", 3000, 180, lastOdo = 5000.0, lastDate = 0L)
        val day = 86_400_000L
        assertEquals(Due.OK, Maintenance.due(oil, 6000.0, 10 * day).status)
        assertEquals(Due.SOON, Maintenance.due(oil, 7800.0, 10 * day).status)
        assertEquals(Due.DUE, Maintenance.due(oil, 8100.0, 10 * day).status)
        assertEquals(Due.DUE, Maintenance.due(oil, 6000.0, 200 * day).status)
        assertEquals(Due.UNKNOWN, Maintenance.due(oil.copy(lastOdo = null, lastDate = null), 6000.0, 0).status)
        assertEquals(2000.0, Maintenance.due(oil, 6000.0, 0).kmLeft!!, 0.01)
    }

    @Test
    fun maintenanceRoundTrip() {
        assertEquals(Maintenance.defaults, Maintenance.decodeItems(Maintenance.encodeItems(Maintenance.defaults)))
        val log = listOf(ServiceEntry(1, 1234.5, "Oil change", 450.0), ServiceEntry(2, null, "Chain", null))
        assertEquals(log, Maintenance.decodeLog(Maintenance.encodeLog(log)))
    }

    @Test
    fun batteryHealth() {
        val good = List(3) { BatteryRecord(it.toLong(), 12.7f, 10.5f, 14.3f) }
        assertEquals(Health.GOOD, BatteryHealth.assess(good).battery)
        assertEquals(Health.GOOD, BatteryHealth.assess(good).charging)
        val weak = List(3) { BatteryRecord(it.toLong(), 11.8f, 9.0f, 12.9f) }
        assertEquals(Health.WEAK, BatteryHealth.assess(weak).battery)
        assertEquals(Health.WEAK, BatteryHealth.assess(weak).charging)
        assertEquals(Health.UNKNOWN, BatteryHealth.assess(emptyList()).battery)
        assertEquals(good, BatteryHealth.decode(BatteryHealth.encode(good)))
    }

    @Test
    fun ridingStyle() {
        val t0 = 1_000_000L
        // steady 40 km/h for 5 minutes, with one harsh acceleration and one hard brake
        val speeds = MutableList(300) { 40 }
        speeds[100] = 25
        speeds[200] = 40
        speeds[201] = 26
        val samples = speeds.mapIndexed { i, v ->
            RideSample(t = t0 + i * 1000L, speed = v, rpm = if (i in 50..59) 8500 else 5000, throttle = 30, engineTemp = if (i >= 30) 70 else 40)
        }
        val s = RideStats.compute("r", samples, null)
        assertEquals(2, s.harshAccel)      // 25 -> 40 at i=101, 26 -> 40 at i=202
        assertEquals(2, s.hardBrake)       // 40 -> 25 at i=100, 40 -> 26 at i=201
        assertEquals(10L, s.overRevS)
        assertEquals(30L, s.warmupS)
        assertEquals(30, s.avgThrottle)
        assertTrue(s.smoothness in 1..99)
    }

    @Test
    fun clusterTextReversed() {
        val f = Outbound.clusterText("CALL Ravi", reversed = true)
        assertEquals(0x5B, f[0].toInt() and 0xFF)
        assertEquals(0x4C, f[1].toInt() and 0xFF)
        assertEquals("ivaR LLAC", String(f, 2, 9, Charsets.US_ASCII))
        assertEquals(0xFF, f[19].toInt() and 0xFF)
        assertEquals("CALL Ravi", String(Outbound.clusterText("CALL Ravi!", reversed = false), 2, 9, Charsets.US_ASCII))
    }
}
