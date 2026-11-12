package dev.rtrcompanion.ride

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** One second of a ride. Every field is optional: the bike or GPS may not have sent it. */
data class RideSample(
    val t: Long,
    val speed: Int? = null,
    val rpm: Int? = null,
    /** 0 = neutral, 1..6. */
    val gear: Int? = null,
    val engineTemp: Int? = null,
    val battery: Float? = null,
    val throttle: Int? = null,
    /** Odometer in km. */
    val odo: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
)

data class RideSummary(
    val id: String,
    val start: Long,
    val end: Long,
    val bike: String = "",
    val distanceKm: Double = 0.0,
    val durationS: Long = 0,
    val movingS: Long = 0,
    val idleS: Long = 0,
    /** Average while moving, km/h. */
    val avgSpeed: Double = 0.0,
    val maxSpeed: Int = 0,
    val avgRpm: Int = 0,
    val maxRpm: Int = 0,
    val maxEngineTemp: Int? = null,
    val minBattery: Float? = null,
    val maxBattery: Float? = null,
    /** Estimated from distance and the cluster's average mileage. */
    val fuelL: Double? = null,
    val mileageKmpl: Int? = null,
    /** Seconds spent moving in each gear (0 = neutral). */
    val gearSeconds: Map<Int, Long> = emptyMap(),
    val hasRoute: Boolean = false,
)

object RideStats {
    private const val EARTH_M = 6_371_000.0

    fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_M * asin(sqrt(a))
    }

    /** Distance along the GPS track, ignoring jumps faster than 70 m/s (GPS glitches). */
    fun gpsDistanceKm(samples: List<RideSample>): Double {
        var meters = 0.0
        var prev: RideSample? = null
        for (s in samples) {
            if (s.lat == null || s.lon == null) continue
            val p = prev
            if (p?.lat != null && p.lon != null) {
                val d = haversineM(p.lat, p.lon, s.lat, s.lon)
                val dt = ((s.t - p.t) / 1000.0).coerceAtLeast(1.0)
                if (d / dt < 70.0) meters += d
            }
            prev = s
        }
        return meters / 1000.0
    }

    /** Distance from the bike's odometer, falling back to GPS. */
    fun distanceKm(samples: List<RideSample>): Double {
        val odos = samples.mapNotNull { it.odo }
        val odoDist = if (odos.size >= 2) odos.last() - odos.first() else 0.0
        return if (odoDist > 0.0) odoDist else gpsDistanceKm(samples)
    }

    fun compute(id: String, samples: List<RideSample>, mileageKmpl: Int?, bike: String = ""): RideSummary {
        if (samples.isEmpty()) return RideSummary(id, 0, 0, bike)
        val start = samples.first().t
        val end = samples.last().t
        var moving = 0L
        var idle = 0L
        val gears = HashMap<Int, Long>()
        for (i in 1 until samples.size) {
            val dt = ((samples[i].t - samples[i - 1].t) / 1000).coerceIn(0, 5)
            val sp = samples[i].speed ?: 0
            if (sp > 0) {
                moving += dt
                samples[i].gear?.let { g -> gears[g] = (gears[g] ?: 0L) + dt }
            } else {
                idle += dt
            }
        }
        val dist = distanceKm(samples)
        val rpms = samples.mapNotNull { it.rpm }.filter { it > 0 }
        val batteries = samples.mapNotNull { it.battery }.filter { it > 0f }
        val mileage = mileageKmpl?.takeIf { it > 0 }
        return RideSummary(
            id = id,
            start = start,
            end = end,
            bike = bike,
            distanceKm = dist,
            durationS = (end - start) / 1000,
            movingS = moving,
            idleS = idle,
            avgSpeed = if (moving > 0) dist / (moving / 3600.0) else 0.0,
            maxSpeed = samples.maxOf { it.speed ?: 0 },
            avgRpm = if (rpms.isEmpty()) 0 else rpms.average().roundToInt(),
            maxRpm = rpms.maxOrNull() ?: 0,
            maxEngineTemp = samples.mapNotNull { it.engineTemp }.maxOrNull(),
            minBattery = batteries.minOrNull(),
            maxBattery = batteries.maxOrNull(),
            fuelL = mileage?.let { dist / it },
            mileageKmpl = mileage,
            gearSeconds = gears,
            hasRoute = samples.count { it.lat != null && it.lon != null } >= 2,
        )
    }
}

/**
 * Ride file format: `#key=value` header lines holding the summary, then CSV
 * with one row per second. Plain text so a ride can be opened in a spreadsheet.
 */
object RideCsv {
    const val HEADER = "t,speed,rpm,gear,engineTemp,battery,throttle,odo,lat,lon"

    fun write(summary: RideSummary, samples: List<RideSample>): String = buildString {
        for ((k, v) in metaOf(summary)) append('#').append(k).append('=').append(v).append('\n')
        append(HEADER).append('\n')
        for (s in samples) {
            val cells = listOf(s.t, s.speed, s.rpm, s.gear, s.engineTemp, s.battery, s.throttle, s.odo, s.lat, s.lon)
            append(cells.joinToString(",") { it?.toString() ?: "" }).append('\n')
        }
    }

    fun parse(text: String): Pair<RideSummary, List<RideSample>> {
        val meta = LinkedHashMap<String, String>()
        val samples = ArrayList<RideSample>()
        for (line in text.lineSequence()) {
            when {
                line.isBlank() -> Unit
                line.startsWith("#") -> {
                    val eq = line.indexOf('=')
                    if (eq > 1) meta[line.substring(1, eq)] = line.substring(eq + 1)
                }
                line.startsWith("t,") -> Unit
                else -> parseRow(line)?.let { samples += it }
            }
        }
        return summaryOf(meta) to samples
    }

    fun parseRow(line: String): RideSample? {
        val c = line.split(',')
        if (c.size < 10) return null
        val t = c[0].toLongOrNull() ?: return null
        return RideSample(
            t = t,
            speed = c[1].toIntOrNull(),
            rpm = c[2].toIntOrNull(),
            gear = c[3].toIntOrNull(),
            engineTemp = c[4].toIntOrNull(),
            battery = c[5].toFloatOrNull(),
            throttle = c[6].toIntOrNull(),
            odo = c[7].toDoubleOrNull(),
            lat = c[8].toDoubleOrNull(),
            lon = c[9].toDoubleOrNull(),
        )
    }

    fun metaOf(s: RideSummary): Map<String, String> = linkedMapOf(
        "v" to "1",
        "id" to s.id,
        "start" to s.start.toString(),
        "end" to s.end.toString(),
        "bike" to s.bike.replace('\n', ' '),
        "distanceKm" to s.distanceKm.toString(),
        "durationS" to s.durationS.toString(),
        "movingS" to s.movingS.toString(),
        "idleS" to s.idleS.toString(),
        "avgSpeed" to s.avgSpeed.toString(),
        "maxSpeed" to s.maxSpeed.toString(),
        "avgRpm" to s.avgRpm.toString(),
        "maxRpm" to s.maxRpm.toString(),
        "maxEngineTemp" to (s.maxEngineTemp?.toString() ?: ""),
        "minBattery" to (s.minBattery?.toString() ?: ""),
        "maxBattery" to (s.maxBattery?.toString() ?: ""),
        "fuelL" to (s.fuelL?.toString() ?: ""),
        "mileageKmpl" to (s.mileageKmpl?.toString() ?: ""),
        "gears" to s.gearSeconds.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value}" },
        "hasRoute" to s.hasRoute.toString(),
    )

    fun summaryOf(m: Map<String, String>): RideSummary = RideSummary(
        id = m["id"] ?: "",
        start = m["start"]?.toLongOrNull() ?: 0,
        end = m["end"]?.toLongOrNull() ?: 0,
        bike = m["bike"] ?: "",
        distanceKm = m["distanceKm"]?.toDoubleOrNull() ?: 0.0,
        durationS = m["durationS"]?.toLongOrNull() ?: 0,
        movingS = m["movingS"]?.toLongOrNull() ?: 0,
        idleS = m["idleS"]?.toLongOrNull() ?: 0,
        avgSpeed = m["avgSpeed"]?.toDoubleOrNull() ?: 0.0,
        maxSpeed = m["maxSpeed"]?.toIntOrNull() ?: 0,
        avgRpm = m["avgRpm"]?.toIntOrNull() ?: 0,
        maxRpm = m["maxRpm"]?.toIntOrNull() ?: 0,
        maxEngineTemp = m["maxEngineTemp"]?.toIntOrNull(),
        minBattery = m["minBattery"]?.toFloatOrNull(),
        maxBattery = m["maxBattery"]?.toFloatOrNull(),
        fuelL = m["fuelL"]?.toDoubleOrNull(),
        mileageKmpl = m["mileageKmpl"]?.toIntOrNull(),
        gearSeconds = (m["gears"] ?: "").split(';').mapNotNull { part ->
            val kv = part.split(':')
            val k = kv.getOrNull(0)?.toIntOrNull()
            val v = kv.getOrNull(1)?.toLongOrNull()
            if (k != null && v != null) k to v else null
        }.toMap(),
        hasRoute = m["hasRoute"] == "true",
    )
}

/** GPX 1.1 track, readable by Google Earth, Strava, OsmAnd and most map tools. */
object RideGpx {
    fun build(name: String, samples: List<RideSample>): String {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<gpx version=\"1.1\" creator=\"RTR Companion\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
            append("  <trk><name>").append(name.replace("&", "and").replace("<", "").replace(">", "")).append("</name><trkseg>\n")
            for (s in samples) {
                if (s.lat == null || s.lon == null) continue
                append("    <trkpt lat=\"").append(s.lat).append("\" lon=\"").append(s.lon).append("\"><time>")
                append(iso.format(Date(s.t))).append("</time></trkpt>\n")
            }
            append("  </trkseg></trk>\n</gpx>\n")
        }
    }
}
