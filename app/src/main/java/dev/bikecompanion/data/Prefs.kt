package dev.bikecompanion.data

import android.content.Context
import android.content.SharedPreferences
import dev.bikecompanion.protocol.Scramble

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

    // -------------------------------------------------------- cluster extras

    /** Ask the cluster to warn above [overspeedKmh]. */
    var overspeedOn: Boolean
        get() = sp.getBoolean("overspeedOn", false)
        set(v) = sp.edit().putBoolean("overspeedOn", v).apply()

    var overspeedKmh: Int
        get() = sp.getInt("overspeedKmh", 80)
        set(v) = sp.edit().putInt("overspeedKmh", v).apply()

    var callAlerts: Boolean
        get() = sp.getBoolean("callAlerts", true)
        set(v) = sp.edit().putBoolean("callAlerts", v).apply()

    var messageAlerts: Boolean
        get() = sp.getBoolean("messageAlerts", true)
        set(v) = sp.edit().putBoolean("messageAlerts", v).apply()

    /** Apps whose notifications are shown on the cluster (sender only). Calls and Maps have their own switches. */
    var relayApps: Set<String>
        get() = sp.getStringSet("relayApps", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("relayApps", v).apply()

    /** Auto-connect to the remembered bike when the app opens and after the phone restarts. */
    var autoConnectOnOpen: Boolean
        get() = sp.getBoolean("autoConnectOnOpen", true)
        set(v) = sp.edit().putBoolean("autoConnectOnOpen", v).apply()

    /** "system", "light" or "dark". */
    var themeMode: String
        get() = sp.getString("themeMode", "system") ?: "system"
        set(v) = sp.edit().putString("themeMode", v).apply()

    var musicOnCluster: Boolean
        get() = sp.getBoolean("musicOnCluster", false)
        set(v) = sp.edit().putBoolean("musicOnCluster", v).apply()

    /** The cluster's custom-text line may expect characters in reverse order. */
    var clusterTextReversed: Boolean
        get() = sp.getBoolean("textReversed", true)
        set(v) = sp.edit().putBoolean("textReversed", v).apply()

    /** Also show alerts such as FUEL LOW on the cluster's message line. */
    var alertsOnCluster: Boolean
        get() = sp.getBoolean("alertsOnCluster", true)
        set(v) = sp.edit().putBoolean("alertsOnCluster", v).apply()

    /** Phone notifications for overheating, charging problems, faults, reserve. */
    var alertsOn: Boolean
        get() = sp.getBoolean("alertsOn", true)
        set(v) = sp.edit().putBoolean("alertsOn", v).apply()

    // ---------------------------------------------------------------- garage

    /** Last odometer reading seen, for service reminders while disconnected. */
    var lastOdoKm: Double
        get() = sp.getLong("lastOdoM", 0L) / 1000.0
        set(v) = sp.edit().putLong("lastOdoM", (v * 1000).toLong()).apply()

    /** "lat,lon,time" of where the bike was switched off, or empty. */
    var parked: String
        get() = sp.getString("parked", "") ?: ""
        set(v) = sp.edit().putString("parked", v).apply()

    /** Rider's names for the bike's ride-mode numbers, e.g. "1=Urban;2=Sport". */
    var modeNames: String
        get() = sp.getString("modeNames", "") ?: ""
        set(v) = sp.edit().putString("modeNames", v).apply()

    /** Info switch actions by press type; names of InfoAction. */
    var infoShort: String
        get() = sp.getString("infoShort", "NONE") ?: "NONE"
        set(v) = sp.edit().putString("infoShort", v).apply()

    var infoDouble: String
        get() = sp.getString("infoDouble", "SHOW_RANGE") ?: "SHOW_RANGE"
        set(v) = sp.edit().putString("infoDouble", v).apply()

    var infoLong: String
        get() = sp.getString("infoLong", "SAVE_SPOT") ?: "SAVE_SPOT"
        set(v) = sp.edit().putString("infoLong", v).apply()

    /** While the phone rings, a short press declines the call. */
    var infoDeclinesCalls: Boolean
        get() = sp.getBoolean("infoDeclines", true)
        set(v) = sp.edit().putBoolean("infoDeclines", v).apply()

    /** On fuel reserve, a long press finds the nearest petrol pump. */
    var infoPumpOnReserve: Boolean
        get() = sp.getBoolean("infoPump", true)
        set(v) = sp.edit().putBoolean("infoPump", v).apply()

    /** Learned switch bits, see SwitchLearn. */
    var switchMap: String
        get() = sp.getString("switchMap", "") ?: ""
        set(v) = sp.edit().putString("switchMap", v).apply()

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
