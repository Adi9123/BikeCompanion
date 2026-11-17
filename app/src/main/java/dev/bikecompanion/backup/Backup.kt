package dev.bikecompanion.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.bikecompanion.log.EventLog
import java.io.ByteArrayInputStream
import dev.bikecompanion.data.Prefs
import dev.bikecompanion.fuel.FuelStore
import dev.bikecompanion.garage.GarageStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * One zip with everything the rider would miss after a phone change: rides,
 * fuel log, service log and reminders, battery history, and the settings
 * that carry history (fuel counter totals, learned switches, the bike).
 */
object Backup {
    private val DIRS = listOf("rides", "fuel", "garage", "nav-arrows")

    private fun prefsText(): String = buildString {
        append("injTotal=").append(Prefs.injTotal).append('\n')
        append("trackedKm=").append(Prefs.trackedKm).append('\n')
        append("lastOdoKm=").append(Prefs.lastOdoKm).append('\n')
        append("switchMap=").append(Prefs.switchMap).append('\n')
        append("tankLitres=").append(Prefs.tankLitres).append('\n')
        append("reserveLitres=").append(Prefs.reserveLitres).append('\n')
        append("fuelBars=").append(Prefs.fuelBars).append('\n')
        append("riderName=").append(Prefs.riderName).append('\n')
        append("themeMode=").append(Prefs.themeMode).append('\n')
        Prefs.lastScheme?.let { append("lastScheme=").append(it).append('\n') }
        Prefs.lastAddress?.let { append("lastAddress=").append(it).append('\n') }
        Prefs.lastName?.let { append("lastName=").append(it).append('\n') }
    }

    private fun applyPrefs(text: String) {
        for (line in text.lineSequence()) {
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val k = line.substring(0, eq)
            val v = line.substring(eq + 1)
            when (k) {
                "injTotal" -> v.toLongOrNull()?.let { Prefs.injTotal = it }
                "trackedKm" -> v.toDoubleOrNull()?.let { Prefs.trackedKm = it }
                "lastOdoKm" -> v.toDoubleOrNull()?.let { Prefs.lastOdoKm = it }
                "switchMap" -> Prefs.switchMap = v
                "tankLitres" -> v.toDoubleOrNull()?.let { Prefs.tankLitres = it }
                "reserveLitres" -> v.toDoubleOrNull()?.let { Prefs.reserveLitres = it }
                "fuelBars" -> v.toIntOrNull()?.let { Prefs.fuelBars = it }
                "riderName" -> Prefs.riderName = v
                "themeMode" -> if (v in setOf("system", "light", "dark")) Prefs.themeMode = v
                "lastScheme" -> Prefs.lastScheme = v
                "lastAddress" -> Prefs.lastAddress = v
                "lastName" -> Prefs.lastName = v
            }
        }
    }

    /** Writes the backup zip into the cache, ready to share. */
    fun export(ctx: Context): File {
        val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val out = File(dir, "bike-companion-backup-$stamp.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            for (d in DIRS) {
                val base = File(ctx.filesDir, d)
                base.walkTopDown().filter { it.isFile && !it.path.contains("${File.separator}export${File.separator}") }.forEach { f ->
                    zip.putNextEntry(ZipEntry("$d/" + f.relativeTo(base).invariantSeparatorsPath))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            zip.putNextEntry(ZipEntry("prefs.txt"))
            zip.write(prefsText().toByteArray())
            zip.closeEntry()
        }
        return out
    }

    /** What a restore did, file by file, so the rider sees exactly what happened. */
    class Report {
        val restored = ArrayList<String>()
        val skipped = ArrayList<String>()
    }

    /**
     * Restores from any mix of files: the backup zip (as made, or re-zipped
     * with an extra folder inside), or the separate files from inside it
     * (ride CSVs, refuels.csv, service-log.csv, battery.csv, maintenance.csv,
     * spots.csv, prefs.txt). Each file is recognised by its contents and
     * name, never by the type the file manager claims.
     */
    fun restore(ctx: Context, uris: List<Uri>): Report {
        val report = Report()
        for (uri in uris) {
            val name = displayName(ctx, uri)
            val bytes = try {
                ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (e: Exception) {
                null
            }
            if (bytes == null) {
                report.skipped += "$name: could not be opened"
                continue
            }
            if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
                restoreZip(ctx, name, bytes, report)
            } else {
                restoreFile(ctx, name, bytes.decodeToString(), report)
            }
        }
        FuelStore.version.value++
        GarageStore.version.value++
        EventLog.i("Backup", "Restore: ${report.restored.size} restored, skipped: ${report.skipped.joinToString("; ")}")
        return report
    }

    private fun restoreZip(ctx: Context, zipName: String, bytes: ByteArray, report: Report) {
        var found = 0
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory || e.name.contains("..")) continue
                    // Accept "rides/x.csv" and also "some-folder/rides/x.csv" from a re-zipped backup.
                    val parts = e.name.replace('\\', '/').split('/').filter { it.isNotEmpty() }
                    val start = parts.indexOfFirst { it in DIRS || it == "prefs.txt" }
                    if (start < 0) continue
                    val rel = parts.drop(start).joinToString("/")
                    val data = zip.readBytes()
                    if (rel == "prefs.txt") {
                        applyPrefs(data.decodeToString())
                    } else {
                        val target = File(ctx.filesDir, rel)
                        target.parentFile?.mkdirs()
                        target.writeBytes(data)
                    }
                    found++
                }
            }
        } catch (e: Exception) {
            report.skipped += "$zipName: damaged zip ($e)"
            return
        }
        if (found == 0) report.skipped += "$zipName: no Bike Companion data inside" else report.restored += "$zipName: $found files"
    }

    private fun restoreFile(ctx: Context, name: String, text: String, report: Report) {
        val lines = text.lines()
        val firstData = lines.firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: ""
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        val safe = base.filter { it.isLetterOrDigit() || it in "._-" }.ifBlank { "restored.csv" }
        when {
            firstData.startsWith("t,speed,rpm") -> {
                val file = if (safe.endsWith(".csv")) safe else "$safe.csv"
                File(File(ctx.filesDir, "rides").apply { mkdirs() }, file).writeText(text)
                report.restored += "$base: ride"
            }
            firstData.startsWith("t,odo,litres") -> {
                // Merge with refuels already on this phone, so nothing logged since is lost.
                val target = File(File(ctx.filesDir, "fuel").apply { mkdirs() }, "refuels.csv")
                val old = if (target.exists()) target.readLines() else emptyList()
                val header = firstData
                val rows = (old + lines).filter { it.isNotBlank() && !it.startsWith("t,odo,litres") }.distinct()
                target.writeText((listOf(header) + rows).joinToString("\n"))
                report.restored += "$base: fuel log"
            }
            base == "prefs.txt" -> {
                applyPrefs(text)
                report.restored += "$base: settings"
            }
            base in setOf("service-log.csv", "battery.csv", "spots.csv") -> {
                val target = File(File(ctx.filesDir, "garage").apply { mkdirs() }, base)
                val old = if (target.exists()) target.readLines() else emptyList()
                target.writeText((old + lines).filter { it.isNotBlank() }.distinct().joinToString("\n"))
                report.restored += "$base: merged"
            }
            base == "maintenance.csv" -> {
                File(File(ctx.filesDir, "garage").apply { mkdirs() }, base).writeText(text)
                report.restored += "$base: service reminders"
            }
            else -> report.skipped += "$base: not a Bike Companion file"
        }
    }

    private fun displayName(ctx: Context, uri: Uri): String = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    } ?: uri.lastPathSegment ?: "file"
}
