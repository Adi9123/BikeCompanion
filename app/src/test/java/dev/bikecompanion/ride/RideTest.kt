package dev.bikecompanion.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideTest {

    /** 10 minutes: 2 min idle in neutral, then 8 min at 30 km/h in 3rd, odometer +4.0 km. */
    private fun ride(): List<RideSample> {
        val out = ArrayList<RideSample>()
        val t0 = 1_700_000_000_000L
        for (i in 0..600) {
            val moving = i > 120
            out += RideSample(
                t = t0 + i * 1000L,
                speed = if (moving) 30 else 0,
                rpm = if (moving) 5000 else 1500,
                gear = if (moving) 3 else 0,
                engineTemp = 60 + i / 60,
                battery = if (moving) 14.4f else 12.6f,
                throttle = if (moving) 30 else 0,
                odo = 1000.0 + if (moving) (i - 120) * (4.0 / 480) else 0.0,
                lat = 12.9 + i * 0.0001,
                lon = 77.6,
                mode = if (i < 400) 1 else 2,
            )
        }
        return out
    }

    @Test
    fun summary() {
        val s = RideStats.compute("r1", ride(), mileageKmpl = 40)
        assertEquals(4.0, s.distanceKm, 0.01)
        assertEquals(600, s.durationS)
        assertEquals(480, s.movingS)
        assertEquals(120, s.idleS)
        assertEquals(30.0, s.avgSpeed, 0.1)
        assertEquals(30, s.maxSpeed)
        assertEquals(5000, s.maxRpm)
        assertEquals(70, s.maxEngineTemp)
        assertEquals(12.6f, s.minBattery!!, 0.01f)
        assertEquals(14.4f, s.maxBattery!!, 0.01f)
        assertEquals(0.1, s.fuelL!!, 0.001)
        assertEquals(480L, s.gearSeconds[3])
        assertTrue(s.hasRoute)
    }

    @Test
    fun gpsDistanceWhenNoOdometer() {
        val noOdo = ride().map { it.copy(odo = null) }
        // 600 steps of 0.0001 degrees latitude ≈ 6.67 km
        assertEquals(6.67, RideStats.distanceKm(noOdo), 0.05)
    }

    @Test
    fun gpsJumpsAreIgnored() {
        val pts = listOf(
            RideSample(t = 0, lat = 12.9, lon = 77.6),
            RideSample(t = 1000, lat = 13.9, lon = 77.6), // 111 km in a second: a glitch
            RideSample(t = 2000, lat = 13.9001, lon = 77.6),
        )
        assertTrue(RideStats.gpsDistanceKm(pts) < 0.02)
    }

    @Test
    fun noFuelWithoutMileage() {
        assertNull(RideStats.compute("r", ride(), mileageKmpl = null).fuelL)
        assertNull(RideStats.compute("r", ride(), mileageKmpl = 0).fuelL)
    }

    @Test
    fun timeInEachRideMode() {
        val s = RideStats.compute("r", ride(), mileageKmpl = 40)
        assertEquals(setOf(1, 2), s.modeSeconds.keys)
        assertEquals(s.gearSeconds.values.sum(), s.modeSeconds.values.sum())
        assertTrue(s.modeSeconds.getValue(1) > s.modeSeconds.getValue(2) / 2)
    }

    @Test
    fun csvRoundTrip() {
        val samples = ride()
        val s = RideStats.compute("ride-1", samples, 40, "TVSBT Example")
        val (back, parsed) = RideCsv.parse(RideCsv.write(s, samples))
        assertEquals(samples, parsed)
        assertEquals(s, back)
    }

    @Test
    fun csvToleratesMissingValues() {
        val row = RideCsv.parseRow("1000,,,,,,,,,")!!
        assertEquals(1000L, row.t)
        assertNull(row.speed)
        assertNull(row.lat)
        assertNull(RideCsv.parseRow("garbage"))
    }

    @Test
    fun gpx() {
        val g = RideGpx.build("Test <ride>", ride())
        assertTrue(g.contains("<gpx version=\"1.1\""))
        assertEquals(601, Regex("<trkpt ").findAll(g).count())
        assertFalse(g.contains("<ride>"))
    }
}
