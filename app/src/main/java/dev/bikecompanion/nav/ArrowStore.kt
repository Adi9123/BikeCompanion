package dev.bikecompanion.nav

import android.content.Context
import android.graphics.Bitmap
import dev.bikecompanion.protocol.Maneuver
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * What each Google Maps arrow icon means, remembered per icon.
 *
 * - [Via.YOU]: the rider picked it on the Navigation arrows page. Final.
 * - [Via.TEXT]: the notification text named the turn while showing this icon.
 * - [Via.SHAPE]: worked out from the picture by [ArrowReader].
 *
 * Kept in files/nav-arrows/, with a small copy of each icon so the rider can
 * see and correct them. Icons are generic arrows; no place names are stored.
 */
object ArrowStore {
    enum class Via(val label: String) { YOU("Set by you"), TEXT("From directions text"), SHAPE("Read from the arrow"), NONE("Not recognised") }

    data class Entry(val sig: String, val maneuver: Maneuver?, val via: Via, val seenAt: Long)

    private var dir: File? = null
    private val entries = LinkedHashMap<String, Entry>()
    val version = MutableStateFlow(0)

    @Synchronized
    fun init(ctx: Context) {
        if (dir != null) return
        val d = File(ctx.filesDir, "nav-arrows").apply { mkdirs() }
        dir = d
        try {
            File(d, INDEX).takeIf { it.exists() }?.readLines()?.forEach { line ->
                val c = line.split(',')
                if (c.size >= 4) {
                    val m = Maneuver.entries.firstOrNull { it.name == c[1] }
                    val via = Via.entries.firstOrNull { it.name == c[2] } ?: Via.NONE
                    entries[c[0]] = Entry(c[0], m, via, c[3].toLongOrNull() ?: 0L)
                }
            }
        } catch (ignored: Exception) {
        }
    }

    /** The rider's or the text's answer for this icon, if there is one. */
    @Synchronized
    fun known(sig: String): Entry? = entries[sig]?.takeIf { it.via == Via.YOU || it.via == Via.TEXT }

    @Synchronized
    fun record(sig: String, maneuver: Maneuver?, via: Via, icon: Bitmap?) {
        val old = entries[sig]
        if (old != null && old.via == Via.YOU && via != Via.YOU) {
            entries[sig] = old.copy(seenAt = System.currentTimeMillis())
            return
        }
        val changed = old?.maneuver != maneuver || old?.via != via
        entries[sig] = Entry(sig, maneuver, via, System.currentTimeMillis())
        if (old == null && icon != null) saveIcon(sig, icon)
        if (changed || old == null) save()
    }

    @Synchronized
    fun setByRider(sig: String, maneuver: Maneuver) {
        val old = entries[sig]
        entries[sig] = Entry(sig, maneuver, Via.YOU, old?.seenAt ?: System.currentTimeMillis())
        save()
    }

    @Synchronized
    fun all(): List<Entry> = entries.values.sortedByDescending { it.seenAt }

    fun iconFile(sig: String): File? = dir?.let { File(it, "$sig.png") }?.takeIf { it.exists() }

    @Synchronized
    fun clear() {
        entries.clear()
        dir?.listFiles()?.forEach { it.delete() }
        save()
    }

    private fun saveIcon(sig: String, icon: Bitmap) {
        val d = dir ?: return
        try {
            File(d, "$sig.png").outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (ignored: Exception) {
        }
    }

    private fun save() {
        val d = dir ?: return
        try {
            val keep = entries.values.sortedByDescending { it.seenAt }.take(300)
            File(d, INDEX).writeText(keep.joinToString("\n") { "${it.sig},${it.maneuver?.name ?: ""},${it.via.name},${it.seenAt}" })
        } catch (ignored: Exception) {
        }
        version.value++
    }

    private const val INDEX = "arrows.csv"
}
