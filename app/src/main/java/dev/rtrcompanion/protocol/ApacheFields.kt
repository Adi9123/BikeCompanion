package dev.rtrcompanion.protocol

import java.util.Locale

enum class Fmt { INT, TENTHS, HALF, TEMP_OFFSET_25, GEAR, HEX }

/**
 * Where one value lives inside a cluster frame.
 *
 * [position] is 1-based, counting the start byte as position 1, which is how
 * the layout is written in docs/PROTOCOL.md. Multi-byte values are big-endian.
 */
data class FieldSpec(
    val frameId: Int,
    val position: Int,
    val length: Int,
    val key: String,
    val label: String,
    val unit: String = "",
    val fmt: Fmt = Fmt.INT,
) {
    val index: Int get() = position - 1
}

/**
 * Frame layout for Apache clusters (frame ids 0x10, 0x11, 0x12, 0x16, 0x18).
 *
 * The same layout is used by every Apache variant, including the larger TFT
 * models, so a smaller LCD cluster may leave some of these fields at zero or
 * never send a frame at all. The Diagnostics screen shows what actually
 * arrives; values here that never change on a given bike are not real data
 * for that bike.
 */
object ApacheFields {
    const val RIDE = 0x10
    const val STATUS = 0x11
    const val TRIP = 0x12
    const val LAP = 0x16
    const val ENGINE = 0x18
    const val TRIPS = 0x19

    val frameNames: Map<Int, String> = mapOf(
        RIDE to "Ride",
        STATUS to "Status",
        TRIP to "Trip",
        LAP to "Lap timer",
        ENGINE to "Engine",
        TRIPS to "Trips",
        0x29 to "Basic data",
    )

    val all: List<FieldSpec> = listOf(
        // 0x10 — ride
        FieldSpec(RIDE, 3, 1, "speed", "Speed", "km/h"),
        FieldSpec(RIDE, 4, 3, "odometer", "Odometer", "km", Fmt.TENTHS),
        FieldSpec(RIDE, 7, 1, "fuel", "Fuel level", "bars"),
        FieldSpec(RIDE, 8, 1, "avgSpeed", "Average speed", "km/h"),
        FieldSpec(RIDE, 9, 1, "mileage", "Mileage (raw)"),
        FieldSpec(RIDE, 10, 1, "topSpeed", "Top speed", "km/h"),
        FieldSpec(RIDE, 11, 1, "throttle", "Throttle", "%", Fmt.HALF),
        FieldSpec(RIDE, 12, 1, "switches", "Switch status", "", Fmt.HEX),
        FieldSpec(RIDE, 13, 1, "zeroTo60", "0-60 time (raw)"),
        FieldSpec(RIDE, 14, 1, "avgMileage", "Average mileage (raw)"),
        FieldSpec(RIDE, 15, 2, "tripF", "Trip F", "km", Fmt.TENTHS),
        FieldSpec(RIDE, 17, 2, "rpm", "Engine speed", "rpm"),

        // 0x11 — status and diagnostics
        FieldSpec(STATUS, 3, 1, "fuelSensor", "Fuel sensor status", "", Fmt.HEX),
        FieldSpec(STATUS, 4, 1, "state1", "Vehicle state 1", "", Fmt.HEX),
        FieldSpec(STATUS, 5, 1, "service", "Service reminder", "", Fmt.HEX),
        FieldSpec(STATUS, 6, 1, "gear", "Gear", "", Fmt.GEAR),
        FieldSpec(STATUS, 7, 1, "state2", "Battery / vehicle state 2 (raw)"),
        FieldSpec(STATUS, 8, 1, "swVersion", "Cluster software version"),
        FieldSpec(STATUS, 9, 1, "mil", "Engine fault (MIL) blink code"),
        FieldSpec(STATUS, 10, 1, "modelCode", "Vehicle model code", "", Fmt.HEX),
        FieldSpec(STATUS, 11, 1, "diag", "Diagnostic flags", "", Fmt.HEX),
        FieldSpec(STATUS, 13, 1, "indicators", "Turn indicators", "", Fmt.HEX),
        FieldSpec(STATUS, 14, 1, "telltales", "Warning lamps (raw bits)", "", Fmt.HEX),
        FieldSpec(STATUS, 15, 1, "screen", "Screen", "", Fmt.HEX),
        FieldSpec(STATUS, 16, 1, "state3", "Vehicle state 3", "", Fmt.HEX),
        FieldSpec(STATUS, 17, 1, "absMil", "ABS fault blink code"),
        FieldSpec(STATUS, 18, 1, "mode", "Ride mode / backlight", "", Fmt.HEX),

        // 0x12 — trip
        FieldSpec(TRIP, 3, 1, "lean", "Lean angle (raw)"),
        FieldSpec(TRIP, 4, 2, "range", "Range to empty", "km"),
        FieldSpec(TRIP, 6, 1, "wheelOffset", "Wheel offset (raw)"),
        FieldSpec(TRIP, 7, 1, "accel", "Acceleration (raw)"),
        FieldSpec(TRIP, 8, 1, "torque", "Torque (raw)"),
        FieldSpec(TRIP, 9, 2, "tripDistance", "Trip distance", "km", Fmt.TENTHS),
        FieldSpec(TRIP, 11, 1, "tripHours", "Trip time hours", "h"),
        FieldSpec(TRIP, 12, 1, "tripMinutes", "Trip time minutes", "min"),
        FieldSpec(TRIP, 13, 1, "tripMileage", "Trip mileage", "km/l"),
        FieldSpec(TRIP, 14, 2, "tripFuel", "Trip fuel", "l", Fmt.TENTHS),
        FieldSpec(TRIP, 16, 1, "overspeedLimit", "Overspeed threshold", "km/h"),
        FieldSpec(TRIP, 17, 1, "overspeedSetting", "Overspeed setting", "", Fmt.HEX),

        // 0x18 — engine
        FieldSpec(ENGINE, 3, 1, "engineLoad", "Engine load", "%"),
        FieldSpec(ENGINE, 4, 2, "injAccum", "Accumulated injection time (raw)"),
        FieldSpec(ENGINE, 6, 1, "map", "Manifold pressure", "kPa"),
        FieldSpec(ENGINE, 7, 1, "baro", "Barometric pressure", "kPa"),
        FieldSpec(ENGINE, 8, 1, "intakeTemp", "Intake air temperature", "°C"),
        FieldSpec(ENGINE, 9, 1, "engineTemp", "Engine temperature", "°C", Fmt.TEMP_OFFSET_25),
        FieldSpec(ENGINE, 10, 2, "injTime", "Injection time (raw)"),
        FieldSpec(ENGINE, 12, 1, "battery", "Battery", "V", Fmt.TENTHS),
        FieldSpec(ENGINE, 13, 2, "runTime", "Run time since start", "s"),
        FieldSpec(ENGINE, 15, 2, "distance", "Distance travelled (raw)"),
        FieldSpec(ENGINE, 17, 2, "injVolume", "Fuel-injection counter (resets at key-on)"),

        // 0x19 — trips. Not in the official Apache table; read from the RTR 160 4V.
        FieldSpec(TRIPS, 5, 3, "tripA", "Trip A", "km"),
        FieldSpec(TRIPS, 8, 3, "tripB", "Trip B", "km"),
        FieldSpec(TRIPS, 14, 1, "avgMileageA", "Average mileage A", "km/l"),
        FieldSpec(TRIPS, 15, 1, "avgMileageB", "Average mileage B", "km/l"),
    )

    private val byKey: Map<String, FieldSpec> = all.associateBy { it.key }

    fun spec(key: String): FieldSpec? = byKey[key]

    fun forFrame(id: Int): List<FieldSpec> = all.filter { it.frameId == id }

    /** Raw values for every known field in a decoded frame. */
    fun extract(clear: ByteArray): Map<String, Long> {
        if (clear.size < 2) return emptyMap()
        val id = Codec.u8(clear[1])
        val out = LinkedHashMap<String, Long>()
        for (f in all) {
            if (f.frameId != id) continue
            Codec.be(clear, f.index, f.length)?.let { out[f.key] = it }
        }
        return out
    }

    fun format(spec: FieldSpec, raw: Long): String = when (spec.fmt) {
        Fmt.INT -> raw.toString()
        Fmt.TENTHS -> String.format(Locale.US, "%.1f", raw / 10.0)
        Fmt.HALF -> (raw / 2).toString()
        Fmt.TEMP_OFFSET_25 -> if (raw < 25) "0" else (raw - 25).toString()
        Fmt.GEAR -> when (raw) {
            0L, 8L -> "N"
            in 1L..6L -> raw.toString()
            else -> "?"
        }
        Fmt.HEX -> String.format(Locale.US, "0x%02X", raw)
    }
}
