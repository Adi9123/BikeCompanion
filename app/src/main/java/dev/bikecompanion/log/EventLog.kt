package dev.bikecompanion.log

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Session log: every frame in and out, every connection event.
 *
 * Kept in memory for the Log screen and mirrored to files/logs/ so a ride can
 * be shared and analysed afterwards. The file contains the bike's Bluetooth
 * address, so it stays on the phone unless the rider shares it.
 */
object EventLog {
    private const val MAX_LINES = 1500
    private const val KEEP_FILES = 10
    private const val FLUSH_EVERY = 20

    private val ring = ArrayDeque<String>(MAX_LINES)
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    private var writer: BufferedWriter? = null
    private var file: File? = null
    private var unflushed = 0
    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun init(ctx: Context) {
        if (writer != null) return
        val dir = File(ctx.filesDir, "logs").apply { mkdirs() }
        dir.listFiles()
            ?.filter { it.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP_FILES - 1)
            ?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val f = File(dir, "session-$stamp.log")
        file = f
        writer = try {
            BufferedWriter(FileWriter(f, true))
        } catch (e: Exception) {
            Log.w("BikeCompanion", "log file unavailable", e)
            null
        }
    }

    fun i(tag: String, msg: String) = add('I', tag, msg)
    fun w(tag: String, msg: String) = add('W', tag, msg)

    @Synchronized
    private fun add(level: Char, tag: String, msg: String) {
        val line = "${clock.format(Date())} $level/$tag: $msg"
        if (ring.size >= MAX_LINES) ring.removeFirst()
        ring.addLast(line)
        _lines.value = ring.toList()
        writer?.let {
            try {
                it.write(line)
                it.newLine()
                if (++unflushed >= FLUSH_EVERY) {
                    it.flush()
                    unflushed = 0
                }
            } catch (ignored: Exception) {
            }
        }
        if (level == 'W') Log.w("BikeCompanion/$tag", msg) else Log.i("BikeCompanion/$tag", msg)
    }

    @Synchronized
    fun flush() {
        try {
            writer?.flush()
        } catch (ignored: Exception) {
        }
        unflushed = 0
    }

    /** Current session file, flushed so its size is accurate. */
    fun currentFile(): File? {
        flush()
        return file
    }

    @Synchronized
    fun clearView() {
        ring.clear()
        _lines.value = emptyList()
    }
}
