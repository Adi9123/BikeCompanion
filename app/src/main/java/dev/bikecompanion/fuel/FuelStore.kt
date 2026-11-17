package dev.bikecompanion.fuel

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Refuel history in files/fuel/refuels.csv. */
class FuelStore(ctx: Context) {
    private val file = File(File(ctx.filesDir, "fuel").apply { mkdirs() }, "refuels.csv")

    @Synchronized
    fun list(): List<Refuel> = try {
        if (file.exists()) RefuelCsv.parse(file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    fun add(r: Refuel) {
        file.writeText(RefuelCsv.write(list() + r))
        version.value++
    }

    @Synchronized
    fun delete(t: Long) {
        file.writeText(RefuelCsv.write(list().filter { it.t != t }))
        version.value++
    }

    companion object {
        /** Bumped on every change so screens can reload. */
        val version = MutableStateFlow(0)
    }
}
