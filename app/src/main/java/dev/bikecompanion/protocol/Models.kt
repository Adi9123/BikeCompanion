package dev.bikecompanion.protocol

/**
 * Data layout families. Bikes in one family put the same value in the same
 * byte. Layouts come from the official app's frame tables and our own
 * captures; see docs/MODELS.md for the full comparison.
 */
enum class Family { APACHE, RAIDER, SCOOTER, NONE }

enum class Support(val label: String) {
    TESTED("Tested"),
    UNTESTED("Not tested yet"),
    UNSUPPORTED("Not supported yet"),
}

/** Every vehicle the official app connects to, and how far this app supports it. */
enum class BikeModel(
    val title: String,
    val kind: String,
    val family: Family,
    val support: Support,
    val note: String,
) {
    APACHE_RTR_160_4V("Apache RTR 160 4V", "Motorcycle", Family.APACHE, Support.TESTED, "Developed and tested on this bike."),
    RAIDER_125("Raider 125", "Motorcycle", Family.RAIDER, Support.TESTED, "Connection tested. Uses its own data layout."),
    APACHE_RTR_160_2V("Apache RTR 160 2V", "Motorcycle", Family.APACHE, Support.UNTESTED, "Same layout as the 4V in the official app."),
    APACHE_RTR_180("Apache RTR 180", "Motorcycle", Family.APACHE, Support.UNTESTED, "Same layout as the 160 4V in the official app."),
    NTORQ_125("Ntorq 125", "Scooter", Family.SCOOTER, Support.UNTESTED, "Layout from the official app."),
    JUPITER("Jupiter", "Scooter", Family.SCOOTER, Support.UNTESTED, "Connected (SmartXonnect) variants. Layout from the official app."),
    APACHE_RR_310("Apache RR 310", "Motorcycle", Family.NONE, Support.UNSUPPORTED, "Uses a different Bluetooth service."),
    APACHE_RTR_310("Apache RTR 310", "Motorcycle", Family.NONE, Support.UNSUPPORTED, "TFT cluster with a different connection."),
    RONIN("Ronin", "Motorcycle", Family.NONE, Support.UNSUPPORTED, "Uses a different connection."),
    IQUBE("iQube", "Electric scooter", Family.NONE, Support.UNSUPPORTED, "Electric scooters use a different connection."),
    TVS_X("TVS X", "Electric scooter", Family.NONE, Support.UNSUPPORTED, "Electric scooters use a different connection."),
    ORBITER("Orbiter", "Electric scooter", Family.NONE, Support.UNSUPPORTED, "Electric scooters use a different connection."),
    ;

    companion object {
        val DEFAULT = APACHE_RTR_160_4V

        fun of(name: String?): BikeModel = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * The fields of the selected model. Everything that reads bike values goes
 * through here, so the rest of the app uses the same keys ("speed", "rpm",
 * "engineTemp" …) whatever the bike, and a key a model never sends simply
 * never appears (shown as —, never as 0).
 */
object Fields {
    @Volatile
    var model: BikeModel = BikeModel.DEFAULT
        private set

    val family: Family get() = model.family

    fun select(m: BikeModel) {
        model = m
    }

    fun layout(f: Family): List<FieldSpec> = when (f) {
        Family.APACHE, Family.NONE -> ApacheFields.all
        Family.RAIDER -> RaiderLayout.all
        Family.SCOOTER -> ScooterLayout.all
    }

    val all: List<FieldSpec> get() = layout(family)

    fun spec(key: String): FieldSpec? = all.firstOrNull { it.key == key }

    fun forFrame(id: Int): List<FieldSpec> = all.filter { it.frameId == id }

    fun frameName(id: Int): String? = when (family) {
        Family.APACHE, Family.NONE -> ApacheFields.frameNames[id]
        else -> SharedLayout.frameNames[id]
    }

    /** Whether the selected model sends this value at all. */
    fun has(key: String): Boolean = spec(key) != null

    fun extract(clear: ByteArray): Map<String, Long> = extract(clear, family)

    fun extract(clear: ByteArray, f: Family): Map<String, Long> {
        if (clear.size < 2) return emptyMap()
        val id = Codec.u8(clear[1])
        val out = LinkedHashMap<String, Long>()
        for (s in layout(f)) {
            if (s.frameId != id) continue
            Codec.be(clear, s.index, s.length)?.let { out[s.key] = it }
        }
        return out
    }

    fun format(spec: FieldSpec, raw: Long): String = ApacheFields.format(spec, raw)
}

/** Frames shared by the Raider and the scooters: engine (0x18) and economy (0x19). */
internal object SharedLayout {
    const val RIDE = 0x10
    const val STATUS = 0x11
    const val ENGINE = 0x18
    const val ECONOMY = 0x19

    val frameNames = mapOf(
        RIDE to "Ride",
        STATUS to "Status",
        ENGINE to "Engine",
        ECONOMY to "Economy",
        0x54 to "Acknowledge",
        0x6B to "Music controls",
    )

    val engine = listOf(
        FieldSpec(ENGINE, 3, 1, "engineLoad", "Engine load", "%"),
        FieldSpec(ENGINE, 4, 2, "injAccum", "Accumulated injection time (raw)"),
        FieldSpec(ENGINE, 6, 1, "map", "Manifold pressure", "kPa"),
        FieldSpec(ENGINE, 8, 1, "intakeTemp", "Intake air temperature", "°C"),
        FieldSpec(ENGINE, 9, 1, "engineTemp", "Engine temperature", "°C", Fmt.TEMP_OFFSET_25),
        FieldSpec(ENGINE, 10, 2, "baro", "Barometric pressure (raw)"),
        FieldSpec(ENGINE, 12, 1, "battery", "Battery", "V", Fmt.TENTHS),
        FieldSpec(ENGINE, 13, 2, "runTime", "Run time since start", "s"),
        FieldSpec(ENGINE, 15, 2, "distance", "Distance travelled (raw)"),
        FieldSpec(ENGINE, 17, 2, "injVolume", "Fuel-injection counter (resets at key-on)"),
    )

    val economy = listOf(
        FieldSpec(ECONOMY, 3, 3, "issDuration", "Idle stop-start time", "s"),
        FieldSpec(ECONOMY, 6, 1, "otaStatus", "Update status", "", Fmt.HEX),
        FieldSpec(ECONOMY, 9, 1, "avgEconomy", "Average fuel economy", "km/l"),
        FieldSpec(ECONOMY, 10, 1, "instEconomy", "Instant fuel economy", "km/l"),
        FieldSpec(ECONOMY, 12, 2, "bikeRange", "Range (from the bike)", "km"),
        FieldSpec(ECONOMY, 16, 3, "issCount", "Idle stop-start count"),
    )

    /** Status frame fields both share. */
    val status = listOf(
        FieldSpec(STATUS, 3, 1, "fuelSensor", "Fuel sensor status", "", Fmt.HEX),
        FieldSpec(STATUS, 4, 1, "state1", "Vehicle state 1", "", Fmt.HEX),
        FieldSpec(STATUS, 5, 1, "service", "Service reminder", "", Fmt.HEX),
        FieldSpec(STATUS, 7, 1, "state2", "Idle stop-start / vehicle state 2", "", Fmt.HEX),
        FieldSpec(STATUS, 8, 1, "swVersion", "Cluster software version"),
        FieldSpec(STATUS, 9, 1, "mil", "Engine fault (MIL) blink code"),
        FieldSpec(STATUS, 10, 1, "modelCode", "Vehicle model code", "", Fmt.HEX),
        FieldSpec(STATUS, 11, 1, "diag", "Diagnostic flags", "", Fmt.HEX),
        FieldSpec(STATUS, 12, 1, "issCode", "Idle stop-start fault code"),
        FieldSpec(STATUS, 13, 1, "indicators", "Turn indicators", "", Fmt.HEX),
        FieldSpec(STATUS, 14, 1, "connector", "Connector status", "", Fmt.HEX),
    )
}

/** Raider 125: the Apache's ride frame almost unchanged, the scooters' economy frame. */
internal object RaiderLayout {
    private const val RIDE = SharedLayout.RIDE
    private const val STATUS = SharedLayout.STATUS

    val all: List<FieldSpec> = listOf(
        FieldSpec(RIDE, 3, 1, "speed", "Speed", "km/h"),
        FieldSpec(RIDE, 4, 3, "odometer", "Odometer", "km", Fmt.TENTHS),
        FieldSpec(RIDE, 7, 1, "fuel", "Fuel level", "bars"),
        FieldSpec(RIDE, 8, 1, "avgSpeed", "Average speed", "km/h"),
        FieldSpec(RIDE, 9, 1, "tripStatus", "Trip status", "", Fmt.HEX),
        FieldSpec(RIDE, 10, 1, "topSpeed", "Top speed record", "km/h"),
        FieldSpec(RIDE, 11, 1, "throttle", "Throttle", "%", Fmt.HALF),
        FieldSpec(RIDE, 12, 1, "switches", "Ignition / switch status", "", Fmt.HEX),
        FieldSpec(RIDE, 13, 1, "zeroTo60", "Last 0-60 time", "s", Fmt.TENTHS),
        FieldSpec(RIDE, 14, 1, "avgMileage", "Average mileage", "km/l"),
        FieldSpec(RIDE, 15, 2, "tripF", "Trip F (ridden on reserve)", "km", Fmt.TENTHS),
        FieldSpec(RIDE, 17, 2, "rpm", "Engine speed", "rpm"),
        FieldSpec(STATUS, 6, 1, "gear", "Gear", "", Fmt.GEAR),
        FieldSpec(STATUS, 18, 1, "backlight", "Backlight / pairing", "", Fmt.HEX),
    ) + SharedLayout.status + SharedLayout.engine + SharedLayout.economy
}

/** Jupiter and Ntorq: the ride frame is shifted by one byte from byte 12 on, and there is no gear. */
internal object ScooterLayout {
    private const val RIDE = SharedLayout.RIDE

    val all: List<FieldSpec> = listOf(
        FieldSpec(RIDE, 3, 1, "speed", "Speed", "km/h"),
        FieldSpec(RIDE, 4, 3, "odometer", "Odometer", "km", Fmt.TENTHS),
        FieldSpec(RIDE, 7, 1, "fuel", "Fuel level", "bars"),
        FieldSpec(RIDE, 8, 1, "avgSpeed", "Average speed", "km/h"),
        FieldSpec(RIDE, 9, 1, "tripStatus", "Trip status", "", Fmt.HEX),
        FieldSpec(RIDE, 10, 1, "topSpeed", "Top speed record", "km/h"),
        FieldSpec(RIDE, 11, 1, "throttle", "Throttle", "%", Fmt.HALF),
        FieldSpec(RIDE, 12, 1, "backlight", "Backlight / pairing", "", Fmt.HEX),
        FieldSpec(RIDE, 13, 1, "switches", "Ignition / switch status", "", Fmt.HEX),
        FieldSpec(RIDE, 14, 1, "zeroTo60", "Last 0-60 time", "s", Fmt.TENTHS),
        FieldSpec(RIDE, 15, 3, "tripF", "Trip F (ridden on reserve)", "km", Fmt.TENTHS),
        FieldSpec(RIDE, 18, 2, "rpm", "Engine speed", "rpm"),
    ) + SharedLayout.status + SharedLayout.engine + SharedLayout.economy
}
