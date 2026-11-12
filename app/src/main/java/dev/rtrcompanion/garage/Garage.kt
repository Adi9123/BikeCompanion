package dev.rtrcompanion.garage

import java.util.Locale

// ------------------------------------------------------------- switch bits

/** A single bit in a cluster frame. [position] is 1-based, like the protocol doc. */
data class BitRef(val frame: Int, val position: Int, val bit: Int) {
    fun encode() = "$frame:$position:$bit"
    fun read(clear: ByteArray): Boolean? =
        clear.getOrNull(position - 1)?.let { ((it.toInt() and 0xFF) shr bit) and 1 == 1 }

    companion object {
        fun decode(s: String): BitRef? {
            val p = s.split(':')
            if (p.size != 3) return null
            return BitRef(p[0].toIntOrNull() ?: return null, p[1].toIntOrNull() ?: return null, p[2].toIntOrNull() ?: return null)
        }
    }
}

/**
 * Lamps, switches and buttons the app can show. [builtin] is the bit the
 * official app's own decoding uses for this cluster family, so it works
 * without learning; [learnable] ones are taught with the wizard (a learned
 * bit overrides a built-in one).
 */
enum class Switch(
    val label: String,
    val instruction: String,
    val learnable: Boolean,
    val builtin: Pair<BitRef, Boolean>? = null,
) {
    LEFT("Left indicator", "Switch the LEFT indicator on and leave it blinking.", true),
    RIGHT("Right indicator", "Switch the RIGHT indicator on and leave it blinking.", true),
    HIGH_BEAM("High beam", "Switch the HIGH BEAM on.", true),
    CALL_BUTTON("Call button", "Press and HOLD the call button until the capture finishes.", true),
    SIDE_STAND("Side stand", "Put the SIDE STAND down.", false, BitRef(0x11, 4, 6) to true),
    CLUTCH("Clutch", "Pull the CLUTCH lever in.", false, BitRef(0x11, 4, 0) to true),
    BRAKE("Brake", "Hold the BRAKE.", false, BitRef(0x11, 4, 1) to true),
}

/**
 * Finds which bit means what by comparing a quiet baseline with the rider
 * operating one switch. Each sample is the latest clear bytes per frame id.
 */
object SwitchLearn {
    /** Bytes that carry lamp and switch flags on the RTR 160 4V. */
    val candidates: List<Pair<Int, Int>> =
        listOf(4, 5, 7, 11, 12, 13, 14, 15, 16, 17, 18).map { 0x11 to it } + listOf(0x10 to 12)

    fun fractions(samples: List<Map<Int, ByteArray>>): Map<BitRef, Double> {
        val out = HashMap<BitRef, Double>()
        for ((frame, pos) in candidates) for (bit in 0..7) {
            val ref = BitRef(frame, pos, bit)
            val vals = samples.mapNotNull { s -> s[frame]?.let { ref.read(it) } }
            if (vals.isNotEmpty()) out[ref] = vals.count { it }.toDouble() / vals.size
        }
        return out
    }

    /** The bit that changed most between baseline and action, or null if nothing changed clearly. */
    fun best(baseline: Map<BitRef, Double>, action: Map<BitRef, Double>, exclude: Set<BitRef>): Pair<BitRef, Boolean>? {
        var bestRef: BitRef? = null
        var bestDiff = 0.0
        for ((ref, a) in action) {
            if (ref in exclude) continue
            val d = a - (baseline[ref] ?: continue)
            if (kotlin.math.abs(d) > kotlin.math.abs(bestDiff)) {
                bestDiff = d
                bestRef = ref
            }
        }
        val r = bestRef ?: return null
        return if (kotlin.math.abs(bestDiff) >= 0.3) r to (bestDiff > 0) else null
    }

    /** Built-in bits, with anything the rider has learned taking priority. */
    fun effective(learned: Map<Switch, Pair<BitRef, Boolean>>): Map<Switch, Pair<BitRef, Boolean>> {
        val out = LinkedHashMap<Switch, Pair<BitRef, Boolean>>()
        for (sw in Switch.entries) sw.builtin?.let { out[sw] = it }
        out.putAll(learned)
        return out
    }

    /** "LEFT=17:13:5:1;..." where the last field is 1 when the bit is set while active. */
    fun encode(map: Map<Switch, Pair<BitRef, Boolean>>): String =
        map.entries.joinToString(";") { (k, v) -> "${k.name}=${v.first.encode()}:${if (v.second) 1 else 0}" }

    fun decode(s: String): Map<Switch, Pair<BitRef, Boolean>> = s.split(';').mapNotNull { part ->
        val eq = part.indexOf('=')
        if (eq < 0) return@mapNotNull null
        val sw = Switch.entries.firstOrNull { it.name == part.substring(0, eq) } ?: return@mapNotNull null
        val f = part.substring(eq + 1).split(':')
        if (f.size != 4) return@mapNotNull null
        val ref = BitRef.decode(f.take(3).joinToString(":")) ?: return@mapNotNull null
        sw to (ref to (f[3] == "1"))
    }.toMap()
}

// ---------------------------------------------------------------- ride modes

/**
 * Ride mode from `5A 11` byte 18: the low four bits are the mode, the high four
 * the backlight level in tens of percent. The official app folds codes 9-12
 * into mode 3. Which number is Sport, Urban or Rain is not in the official
 * app for this bike, so the rider names each one once.
 */
object RideModes {
    val NAMES = listOf("Sport", "Urban", "Rain")

    fun mode(byte18: Long): Int {
        val m = (byte18 and 0x0F).toInt()
        return if (m in 9..12) 3 else m
    }

    fun backlightPercent(byte18: Long): Int = ((byte18 shr 4) and 0x0F).toInt() * 10

    fun decodeNames(s: String): Map<Int, String> = s.split(';').mapNotNull { part ->
        val eq = part.indexOf('=')
        val k = if (eq > 0) part.substring(0, eq).toIntOrNull() else null
        if (k != null) k to part.substring(eq + 1) else null
    }.toMap()

    fun encodeNames(m: Map<Int, String>): String = m.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }

    /**
     * Assumed until the rider confirms: the manual cycles Urban, Sport, Rain,
     * and the bike falls back to Urban on a fault.
     */
    val ASSUMED = mapOf(1 to "Urban", 2 to "Sport", 3 to "Rain")

    /** Top speed per mode, from the owner's manual. */
    fun topSpeed(name: String): Int? = when (name) {
        "Sport" -> 114
        "Urban", "Rain" -> 103
        else -> null
    }

    fun name(mode: Int, confirmed: Map<Int, String>): String = confirmed[mode] ?: ASSUMED[mode] ?: "Mode $mode"
}

// ------------------------------------------------------------- maintenance

data class MaintItem(
    val id: String,
    val name: String,
    val intervalKm: Int?,
    val intervalDays: Int?,
    val lastOdo: Double?,
    val lastDate: Long?,
)

enum class Due { OK, SOON, DUE, UNKNOWN }

data class DueInfo(val status: Due, val kmLeft: Double?, val daysLeft: Long?)

data class ServiceEntry(val t: Long, val odo: Double?, val title: String, val cost: Double?)

object Maintenance {
    /** Intervals from the RTR 160 4V owner's manual maintenance schedule (after the free services). */
    val defaults = listOf(
        MaintItem("service", "Periodic service (check and top up oil)", 3000, 90, null, null),
        MaintItem("oil", "Engine oil change (1.2 l 10W30)", 6000, 180, null, null),
        MaintItem("chainLube", "Chain clean and lube", 500, null, null, null),
        MaintItem("chainAdjust", "Chain tension check", 1000, null, null, null),
        MaintItem("tyres", "Tyre pressure (F 25 / R 28 psi / 32 with pillion)", null, 14, null, null),
        MaintItem("sparkPlug", "Spark plug (UR4KE)", 12000, 365, null, null),
        MaintItem("airFilter", "Air filter", 12000, null, null, null),
        MaintItem("forkOil", "Front fork oil", 18000, null, null, null),
        MaintItem("brakeFluid", "Brake fluid (DOT 3/4)", 21000, 730, null, null),
        MaintItem("clutch", "Clutch plates check", 21000, null, null, null),
    )

    private const val DAY_MS = 86_400_000L

    fun due(item: MaintItem, odoNow: Double?, now: Long): DueInfo {
        val kmLeft = if (item.intervalKm != null && item.lastOdo != null && odoNow != null && odoNow > 0) {
            item.lastOdo + item.intervalKm - odoNow
        } else {
            null
        }
        val daysLeft = if (item.intervalDays != null && item.lastDate != null) {
            item.intervalDays - (now - item.lastDate) / DAY_MS
        } else {
            null
        }
        if (kmLeft == null && daysLeft == null) return DueInfo(Due.UNKNOWN, null, null)
        val kmFrac = if (kmLeft != null && item.intervalKm != null) kmLeft / item.intervalKm else 1.0
        val dayFrac = if (daysLeft != null && item.intervalDays != null) daysLeft.toDouble() / item.intervalDays else 1.0
        val worst = minOf(kmFrac, dayFrac)
        val status = when {
            worst <= 0.0 -> Due.DUE
            worst <= 0.1 -> Due.SOON
            else -> Due.OK
        }
        return DueInfo(status, kmLeft, daysLeft)
    }

    fun encodeItems(items: List<MaintItem>): String = items.joinToString("\n") {
        listOf(it.id, it.name.replace(',', ' '), it.intervalKm, it.intervalDays, it.lastOdo, it.lastDate).joinToString(",") { v -> v?.toString() ?: "" }
    }

    fun decodeItems(text: String): List<MaintItem> = text.lineSequence().mapNotNull { line ->
        val c = line.split(',')
        if (c.size < 6 || c[0].isBlank()) return@mapNotNull null
        MaintItem(c[0], c[1], c[2].toIntOrNull(), c[3].toIntOrNull(), c[4].toDoubleOrNull(), c[5].toLongOrNull())
    }.toList()

    fun encodeLog(list: List<ServiceEntry>): String = list.joinToString("\n") {
        listOf(it.t, it.odo, it.title.replace(',', ' '), it.cost).joinToString(",") { v -> v?.toString() ?: "" }
    }

    fun decodeLog(text: String): List<ServiceEntry> = text.lineSequence().mapNotNull { line ->
        val c = line.split(',')
        if (c.size < 4) return@mapNotNull null
        ServiceEntry(c[0].toLongOrNull() ?: return@mapNotNull null, c[1].toDoubleOrNull(), c[2], c[3].toDoubleOrNull())
    }.toList()
}

// ----------------------------------------------------------------- battery

/** One key-on of the bike: voltage before start, lowest while starting, average while charging. */
data class BatteryRecord(val t: Long, val resting: Float?, val startMin: Float?, val charging: Float?)

enum class Health(val label: String) { GOOD("Good"), FAIR("Fair"), WEAK("Weak"), UNKNOWN("Not enough data") }

data class BatteryVerdict(val battery: Health, val charging: Health, val notes: List<String>)

object BatteryHealth {
    fun assess(records: List<BatteryRecord>): BatteryVerdict {
        val recent = records.sortedBy { it.t }.takeLast(5)
        val rest = recent.mapNotNull { it.resting }.takeIf { it.isNotEmpty() }?.average()
        val dip = recent.mapNotNull { it.startMin }.takeIf { it.isNotEmpty() }?.average()
        val chg = recent.mapNotNull { it.charging }.takeIf { it.isNotEmpty() }?.average()
        val notes = ArrayList<String>()
        val battery = when {
            rest == null -> Health.UNKNOWN
            rest < 12.0 || (dip != null && dip < 9.6) -> Health.WEAK
            rest < 12.4 -> Health.FAIR
            else -> Health.GOOD
        }
        val charging = when {
            chg == null -> Health.UNKNOWN
            chg < 13.2 || chg > 15.2 -> Health.WEAK
            chg < 13.5 || chg > 14.9 -> Health.FAIR
            else -> Health.GOOD
        }
        rest?.let { notes += String.format(Locale.US, "Resting voltage %.1f V (healthy is 12.4 V or more).", it) }
        dip?.let { notes += String.format(Locale.US, "Lowest while starting %.1f V (below 9.6 V means a weak battery).", it) }
        chg?.let { notes += String.format(Locale.US, "Charging voltage %.1f V (normal is 13.5–14.8 V).", it) }
        return BatteryVerdict(battery, charging, notes)
    }

    fun encode(list: List<BatteryRecord>): String = list.joinToString("\n") {
        listOf(it.t, it.resting, it.startMin, it.charging).joinToString(",") { v -> v?.toString() ?: "" }
    }

    fun decode(text: String): List<BatteryRecord> = text.lineSequence().mapNotNull { line ->
        val c = line.split(',')
        if (c.size < 4) return@mapNotNull null
        BatteryRecord(c[0].toLongOrNull() ?: return@mapNotNull null, c[1].toFloatOrNull(), c[2].toFloatOrNull(), c[3].toFloatOrNull())
    }.toList()
}
