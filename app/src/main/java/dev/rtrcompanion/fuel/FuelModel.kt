package dev.rtrcompanion.fuel

/**
 * One visit to the pump.
 *
 * [injTotal] and [trackedKm] are snapshots of the app's running totals at
 * the moment of the refuel: the bike's fuel-injection counter summed over
 * every ride the app saw, and the kilometres the app saw. Comparing two full
 * fills tells how much fuel one counter step is worth.
 */
data class Refuel(
    val t: Long,
    val odoKm: Double?,
    val litres: Double,
    /** Amount paid, in rupees. */
    val price: Double?,
    val full: Boolean,
    val injTotal: Long?,
    val trackedKm: Double?,
)

/** Fuel burned between two full-tank fills. */
data class Tank(val start: Refuel, val end: Refuel, val km: Double, val litres: Double, val price: Double?) {
    val kmpl: Double get() = km / litres
    val costPerKm: Double? get() = price?.let { it / km }
}

/** The bike's fuel picture right now. */
data class FuelStatus(
    val litresLeft: Double?,
    /** True when [litresLeft] comes from the calibrated counter, false when from the gauge bars. */
    val measured: Boolean,
    val rangeKm: Double?,
    val mileageKmpl: Double?,
    val onReserve: Boolean,
)

object FuelMath {
    /** Minimum share of the km between two fills the app must have seen to trust the counter. */
    const val MIN_COVERAGE = 0.9

    /**
     * Full-to-full tanks: the litres put in at a full fill (plus any partial
     * fills since the previous full one) are exactly what was burned over the
     * km since that previous full fill.
     */
    fun tanks(refuels: List<Refuel>): List<Tank> {
        val s = refuels.sortedBy { it.t }
        val out = ArrayList<Tank>()
        var lastFull = -1
        for (i in s.indices) {
            if (!s[i].full) continue
            if (lastFull >= 0) {
                val a = s[lastFull]
                val b = s[i]
                val ao = a.odoKm
                val bo = b.odoKm
                if (ao != null && bo != null && bo > ao) {
                    val between = s.subList(lastFull + 1, i + 1)
                    val litres = between.sumOf { it.litres }
                    val price = if (between.all { it.price != null }) between.sumOf { it.price ?: 0.0 } else null
                    if (litres > 0.0) out += Tank(a, b, bo - ao, litres, price)
                }
            }
            lastFull = i
        }
        return out
    }

    fun averageMileage(tanks: List<Tank>): Double? {
        val litres = tanks.sumOf { it.litres }
        return if (tanks.isEmpty() || litres <= 0.0) null else tanks.sumOf { it.km } / litres
    }

    fun averageCostPerKm(tanks: List<Tank>): Double? {
        val priced = tanks.filter { it.price != null }
        val km = priced.sumOf { it.km }
        return if (priced.isEmpty() || km <= 0.0) null else priced.sumOf { it.price ?: 0.0 } / km
    }

    /**
     * Litres per counter step, from full-to-full tanks where the app was
     * connected for at least [MIN_COVERAGE] of the distance. Null until such a
     * tank exists.
     */
    fun calibration(refuels: List<Refuel>): Double? {
        var litres = 0.0
        var counts = 0L
        for (t in tanks(refuels)) {
            val c0 = t.start.injTotal ?: continue
            val c1 = t.end.injTotal ?: continue
            val k0 = t.start.trackedKm ?: continue
            val k1 = t.end.trackedKm ?: continue
            if ((k1 - k0) / t.km < MIN_COVERAGE || c1 - c0 < 1_000) continue
            litres += t.litres
            counts += c1 - c0
        }
        return if (counts > 0) litres / counts else null
    }

    /**
     * Litres from the gauge. For the RTR 160 4V's 8-bar gauge the owner's
     * manual gives the points: 1 bar ≈ 3.5 l, 4 bars ≈ 6.5 l, 8 bars above
     * 10.4 l, and no bars below the 2.5 l reserve. That is about 1 litre per
     * bar above the reserve. Other gauges are treated as linear.
     */
    fun litresFromBars(bars: Int, totalBars: Int, tankLitres: Double): Double {
        val b = bars.coerceIn(0, totalBars)
        if (totalBars != 8) return b.toDouble() / totalBars * tankLitres
        return when (b) {
            0 -> 2.0
            8 -> (10.4 + tankLitres) / 2
            else -> 2.5 + b
        }
    }

    /**
     * Litres left from the counter: a full tank at the last full fill, minus
     * what the counter says was burned since, plus any partial fills since.
     */
    fun litresLeftMeasured(refuels: List<Refuel>, tankLitres: Double, injNow: Long, litresPerCount: Double?): Double? {
        val lpc = litresPerCount ?: return null
        val s = refuels.sortedBy { it.t }
        val lastFull = s.indexOfLast { it.full }
        if (lastFull < 0) return null
        val c0 = s[lastFull].injTotal ?: return null
        var left = tankLitres - (injNow - c0) * lpc
        for (r in s.subList(lastFull + 1, s.size)) left += r.litres
        return left.coerceIn(0.0, tankLitres)
    }

    fun pricePerLitre(refuels: List<Refuel>): Double? =
        refuels.sortedBy { it.t }.lastOrNull { it.price != null && it.litres > 0.0 }?.let { (it.price ?: 0.0) / it.litres }

    fun status(
        refuels: List<Refuel>,
        tankLitres: Double,
        reserveLitres: Double,
        totalBars: Int,
        bars: Int?,
        injNow: Long,
        clusterMileage: Int?,
    ): FuelStatus {
        val measured = litresLeftMeasured(refuels, tankLitres, injNow, calibration(refuels))
        val left = measured ?: bars?.let { litresFromBars(it, totalBars, tankLitres) }
        val mileage = averageMileage(tanks(refuels)) ?: clusterMileage?.takeIf { it > 0 }?.toDouble()
        return FuelStatus(
            litresLeft = left,
            measured = measured != null,
            rangeKm = if (left != null && mileage != null) left * mileage else null,
            mileageKmpl = mileage,
            onReserve = left != null && left <= reserveLitres,
        )
    }
}

/** Refuel history file: one CSV line per refuel. */
object RefuelCsv {
    const val HEADER = "t,odo,litres,price,full,inj,trackedKm"

    fun write(list: List<Refuel>): String = buildString {
        append(HEADER).append('\n')
        for (r in list.sortedBy { it.t }) {
            append(listOf(r.t, r.odoKm, r.litres, r.price, r.full, r.injTotal, r.trackedKm).joinToString(",") { it?.toString() ?: "" })
            append('\n')
        }
    }

    fun parse(text: String): List<Refuel> = text.lineSequence().mapNotNull { line ->
        val c = line.split(',')
        if (c.size < 7) return@mapNotNull null
        val t = c[0].toLongOrNull() ?: return@mapNotNull null
        val litres = c[2].toDoubleOrNull() ?: return@mapNotNull null
        Refuel(t, c[1].toDoubleOrNull(), litres, c[3].toDoubleOrNull(), c[4] == "true", c[5].toLongOrNull(), c[6].toDoubleOrNull())
    }.toList()
}
