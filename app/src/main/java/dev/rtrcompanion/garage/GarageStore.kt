package dev.rtrcompanion.garage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Service reminders, service history and battery records, in files/garage/. */
class GarageStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "garage").apply { mkdirs() }
    private val itemsFile = File(dir, "maintenance.csv")
    private val logFile = File(dir, "service-log.csv")
    private val batteryFile = File(dir, "battery.csv")

    private fun read(f: File): String = try {
        if (f.exists()) f.readText() else ""
    } catch (e: Exception) {
        ""
    }

    @Synchronized
    fun items(): List<MaintItem> {
        val saved = Maintenance.decodeItems(read(itemsFile))
        return if (saved.isEmpty()) Maintenance.defaults else saved
    }

    @Synchronized
    fun saveItems(items: List<MaintItem>) {
        itemsFile.writeText(Maintenance.encodeItems(items))
        version.value++
    }

    @Synchronized
    fun log(): List<ServiceEntry> = Maintenance.decodeLog(read(logFile)).sortedByDescending { it.t }

    @Synchronized
    fun addLog(e: ServiceEntry) {
        logFile.writeText(Maintenance.encodeLog(log() + e))
        version.value++
    }

    @Synchronized
    fun deleteLog(t: Long) {
        logFile.writeText(Maintenance.encodeLog(log().filter { it.t != t }))
        version.value++
    }

    @Synchronized
    fun battery(): List<BatteryRecord> = BatteryHealth.decode(read(batteryFile))

    @Synchronized
    fun addBattery(r: BatteryRecord) {
        batteryFile.writeText(BatteryHealth.encode((battery() + r).takeLast(200)))
        version.value++
    }

    companion object {
        val version = MutableStateFlow(0)
    }
}
