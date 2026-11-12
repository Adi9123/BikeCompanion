package dev.rtrcompanion.fuel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelTest {

    private fun r(t: Long, odo: Double, litres: Double, full: Boolean = true, price: Double? = litres * 105, inj: Long? = null, tracked: Double? = null) =
        Refuel(t, odo, litres, price, full, inj, tracked)

    @Test
    fun mileageFromFullToFull() {
        val list = listOf(
            r(1, 1000.0, 9.0),                 // first full fill: no tank yet
            r(2, 1150.0, 2.0, full = false),   // partial top-up
            r(3, 1400.0, 8.0),                 // 400 km on 10 l
        )
        val tanks = FuelMath.tanks(list)
        assertEquals(1, tanks.size)
        assertEquals(400.0, tanks[0].km, 0.001)
        assertEquals(10.0, tanks[0].litres, 0.001)
        assertEquals(40.0, tanks[0].kmpl, 0.001)
        assertEquals(40.0, FuelMath.averageMileage(tanks)!!, 0.001)
        assertEquals(1050.0 / 400.0, FuelMath.averageCostPerKm(tanks)!!, 0.001)
    }

    @Test
    fun noTankWithoutTwoFullFills() {
        assertTrue(FuelMath.tanks(listOf(r(1, 1000.0, 9.0), r(2, 1200.0, 5.0, full = false))).isEmpty())
        assertNull(FuelMath.averageMileage(emptyList()))
    }

    @Test
    fun calibrationNeedsCoverage() {
        // 300 km between fills, 7.5 l burned, counter advanced 25 million steps.
        val good = listOf(
            r(1, 1000.0, 10.0, inj = 0, tracked = 0.0),
            r(2, 1300.0, 7.5, inj = 25_000_000, tracked = 295.0),
        )
        assertEquals(7.5 / 25_000_000, FuelMath.calibration(good)!!, 1e-12)

        // The app saw only 100 of the 300 km: the counter missed rides, so it is not trusted.
        val partial = listOf(
            r(1, 1000.0, 10.0, inj = 0, tracked = 0.0),
            r(2, 1300.0, 7.5, inj = 8_000_000, tracked = 100.0),
        )
        assertNull(FuelMath.calibration(partial))
    }

    @Test
    fun litresLeftMeasured() {
        val list = listOf(
            r(1, 1000.0, 10.0, inj = 0, tracked = 0.0),
            r(2, 1300.0, 7.5, inj = 25_000_000, tracked = 300.0),
        )
        val lpc = FuelMath.calibration(list)
        // 10 million more steps = 3 l burned since the last full fill.
        assertEquals(9.0, FuelMath.litresLeftMeasured(list, 12.0, 35_000_000, lpc)!!, 0.001)
        assertNull(FuelMath.litresLeftMeasured(list, 12.0, 35_000_000, null))
    }

    @Test
    fun statusFallsBackToGauge() {
        val st = FuelMath.status(emptyList(), 12.0, 2.5, 8, bars = 4, injNow = 0, clusterMileage = 40)
        assertEquals(6.0, st.litresLeft!!, 0.001)
        assertFalse(st.measured)
        assertEquals(240.0, st.rangeKm!!, 0.001)
        assertFalse(st.onReserve)
        assertTrue(FuelMath.status(emptyList(), 12.0, 2.5, 8, bars = 1, injNow = 0, clusterMileage = 40).onReserve)
        assertNull(FuelMath.status(emptyList(), 12.0, 2.5, 8, bars = null, injNow = 0, clusterMileage = null).litresLeft)
    }

    @Test
    fun pricePerLitreUsesLatestPricedFill() {
        val list = listOf(r(1, 1000.0, 10.0, price = 1000.0), r(2, 1300.0, 5.0, price = 550.0), r(3, 1400.0, 2.0, price = null))
        assertEquals(110.0, FuelMath.pricePerLitre(list)!!, 0.001)
    }

    @Test
    fun csvRoundTrip() {
        val list = listOf(
            r(1, 1000.5, 9.25, inj = 123, tracked = 4.5),
            Refuel(2, null, 3.0, null, false, null, null),
        )
        assertEquals(list, RefuelCsv.parse(RefuelCsv.write(list)))
    }
}
