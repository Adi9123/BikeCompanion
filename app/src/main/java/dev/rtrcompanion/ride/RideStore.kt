package dev.rtrcompanion.ride

import android.content.Context
import java.io.File

/** Saved rides, one text file each in files/rides/. Nothing leaves the phone unless shared. */
class RideStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "rides").apply { mkdirs() }
    private val exportDir = File(dir, "export").apply { mkdirs() }

    private fun fileOf(id: String) = File(dir, "$id.csv")

    fun save(samples: List<RideSample>, mileageKmpl: Int?, bike: String): RideSummary {
        val id = "ride-${samples.first().t}"
        val summary = RideStats.compute(id, samples, mileageKmpl, bike)
        fileOf(id).writeText(RideCsv.write(summary, samples))
        return summary
    }

    /** Newest first. Reads only the header of each file. */
    fun list(): List<RideSummary> =
        (dir.listFiles { f -> f.isFile && f.name.endsWith(".csv") } ?: emptyArray())
            .mapNotNull { f ->
                try {
                    val meta = LinkedHashMap<String, String>()
                    f.bufferedReader().useLines { lines ->
                        for (line in lines) {
                            if (!line.startsWith("#")) break
                            val eq = line.indexOf('=')
                            if (eq > 1) meta[line.substring(1, eq)] = line.substring(eq + 1)
                        }
                    }
                    RideCsv.summaryOf(meta).takeIf { it.id.isNotEmpty() }
                } catch (e: Exception) {
                    null
                }
            }
            .sortedByDescending { it.start }

    fun load(id: String): Pair<RideSummary, List<RideSample>>? {
        val f = fileOf(id)
        if (!f.exists()) return null
        return try {
            RideCsv.parse(f.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun delete(id: String) {
        fileOf(id).delete()
        File(exportDir, "$id.gpx").delete()
    }

    /** The ride as CSV plus GPX, ready to share. */
    fun exportFiles(id: String): List<File> {
        val (summary, samples) = load(id) ?: return emptyList()
        val out = mutableListOf(fileOf(id))
        if (summary.hasRoute) {
            val gpx = File(exportDir, "$id.gpx")
            gpx.writeText(RideGpx.build("Ride $id", samples))
            out += gpx
        }
        return out
    }
}
