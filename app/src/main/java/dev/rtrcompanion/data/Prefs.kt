package dev.rtrcompanion.data

import android.content.Context
import android.content.SharedPreferences
import dev.rtrcompanion.protocol.Scramble

/** Small persistent settings. Call [init] before use. */
object Prefs {
    private lateinit var sp: SharedPreferences

    const val AUTO = "AUTO"

    fun init(ctx: Context) {
        if (!::sp.isInitialized) {
            sp = ctx.applicationContext.getSharedPreferences("rtr", Context.MODE_PRIVATE)
        }
    }

    var lastAddress: String?
        get() = sp.getString("address", null)
        set(v) = sp.edit().putString("address", v).apply()

    var lastName: String?
        get() = sp.getString("name", null)
        set(v) = sp.edit().putString("name", v).apply()

    /** Send the 2-second phone-status frame the official app sends. */
    var keepalive: Boolean
        get() = sp.getBoolean("keepalive", true)
        set(v) = sp.edit().putBoolean("keepalive", v).apply()

    var autoReconnect: Boolean
        get() = sp.getBoolean("autoReconnect", true)
        set(v) = sp.edit().putBoolean("autoReconnect", v).apply()

    /** [AUTO] or a [Scramble] name. */
    var inboundScheme: String
        get() = sp.getString("inScheme", AUTO) ?: AUTO
        set(v) = sp.edit().putString("inScheme", v).apply()

    /** [AUTO] (match what the bike uses) or a [Scramble] name. */
    var outboundScheme: String
        get() = sp.getString("outScheme", AUTO) ?: AUTO
        set(v) = sp.edit().putString("outScheme", v).apply()

    var riderName: String
        get() = sp.getString("riderName", "") ?: ""
        set(v) = sp.edit().putString("riderName", v).apply()

    /** Minutes after key-off before a paused ride is saved automatically. */
    var autoEndMinutes: Int
        get() = sp.getInt("autoEndMinutes", 3)
        set(v) = sp.edit().putInt("autoEndMinutes", v).apply()

    // ------------------------------------------------------------------ fuel

    /** Tank including reserve. RTR 160 4V: 12 litres. */
    var tankLitres: Double
        get() = sp.getFloat("tankLitres", 12f).toDouble()
        set(v) = sp.edit().putFloat("tankLitres", v.toFloat()).apply()

    var reserveLitres: Double
        get() = sp.getFloat("reserveLitres", 2.5f).toDouble()
        set(v) = sp.edit().putFloat("reserveLitres", v.toFloat()).apply()

    /** Bars on the cluster's fuel gauge when full. */
    var fuelBars: Int
        get() = sp.getInt("fuelBars", 8)
        set(v) = sp.edit().putInt("fuelBars", v).apply()

    /** Lifetime sum of the bike's fuel-injection counter, as seen by the app. */
    var injTotal: Long
        get() = sp.getLong("injTotal", 0L)
        set(v) = sp.edit().putLong("injTotal", v).apply()

    /** Kilometres the app has seen the odometer advance while connected. */
    var trackedKm: Double
        get() = sp.getLong("trackedM", 0L) / 1000.0
        set(v) = sp.edit().putLong("trackedM", (v * 1000).toLong()).apply()

    /** Steady fuel-gauge reading at the last key-off, or -1. */
    var lastStableBars: Int
        get() = sp.getInt("lastBars", -1)
        set(v) = sp.edit().putInt("lastBars", v).apply()

    /** Show Google Maps directions on the cluster. */
    var navEnabled: Boolean
        get() = sp.getBoolean("navEnabled", true)
        set(v) = sp.edit().putBoolean("navEnabled", v).apply()

    /** The scramble this bike was last seen using, so sending can start before the first frame. */
    var lastScheme: String?
        get() = sp.getString("lastScheme", null)
        set(v) = sp.edit().putString("lastScheme", v).apply()

    fun scheme(name: String?): Scramble? = Scramble.entries.firstOrNull { it.name == name }
}
