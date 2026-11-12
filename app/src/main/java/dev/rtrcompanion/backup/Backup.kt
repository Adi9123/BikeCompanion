package dev.rtrcompanion.backup

import android.content.Context
import android.net.Uri
import dev.rtrcompanion.data.Prefs
import dev.rtrcompanion.fuel.FuelStore
import dev.rtrcompanion.garage.GarageStore
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
    private val DIRS = listOf("rides", "fuel", "garage")

    private fun prefsText(): String = buildString {
        append("injTotal=").append(Prefs.injTotal).append('\n')
        append("trackedKm=").append(Prefs.trackedKm).append('\n')
        append("lastOdoKm=").append(Prefs.lastOdoKm).append('\n')
        append("switchMap=").append(Prefs.switchMap).append('\n')
        append("tankLitres=").append(Prefs.tankLitres).append('\n')
        append("reserveLitres=").append(Prefs.reserveLitres).append('\n')
        append("fuelBars=").append(Prefs.fuelBars).append('\n')
        append("riderName=").append(Prefs.riderName).append('\n')
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
        val out = File(dir, "rtr-companion-backup-$stamp.zip")
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

    /** Restores a backup. Returns how many files were restored. */
    fun restore(ctx: Context, uri: Uri): Int {
        var count = 0
        val input = ctx.contentResolver.openInputStream(uri) ?: return 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name
                if (e.isDirectory || name.contains("..")) continue
                if (name == "prefs.txt") {
                    applyPrefs(zip.readBytes().decodeToString())
                    continue
                }
                if (DIRS.none { name.startsWith("$it/") }) continue
                val target = File(ctx.filesDir, name)
                target.parentFile?.mkdirs()
                target.outputStream().use { zip.copyTo(it) }
                count++
            }
        }
        FuelStore.version.value++
        GarageStore.version.value++
        return count
    }
}
