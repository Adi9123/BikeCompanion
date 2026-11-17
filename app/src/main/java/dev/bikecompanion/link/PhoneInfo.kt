package dev.bikecompanion.link

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager
import dev.bikecompanion.data.AppState
import dev.bikecompanion.data.Prefs
import dev.bikecompanion.protocol.PhoneStatus
import java.util.Calendar

/** Reads the phone's clock, battery and signal for the keepalive frame. */
object PhoneInfo {
    fun status(ctx: Context): PhoneStatus {
        val cal = Calendar.getInstance()
        val pct = try {
            ctx.getSystemService(BatteryManager::class.java)
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        } catch (ignored: Exception) {
            0
        }
        val signal = try {
            if (Build.VERSION.SDK_INT >= 28) {
                ctx.getSystemService(TelephonyManager::class.java)?.signalStrength?.level ?: 0
            } else {
                0
            }
        } catch (ignored: Exception) {
            0
        }
        val hour = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        return PhoneStatus(
            signalBars = signal,
            batteryBars = ((pct + 19) / 20).coerceIn(0, 5),
            hour12 = hour,
            minute = cal.get(Calendar.MINUTE),
            second = cal.get(Calendar.SECOND),
            pm = cal.get(Calendar.AM_PM) == Calendar.PM,
            day = cal.get(Calendar.DAY_OF_MONTH),
            month = cal.get(Calendar.MONTH) + 1,
            year2 = cal.get(Calendar.YEAR) % 100,
            missedCalls = AppState.missedCalls.value.coerceIn(0, 99),
            overspeedKmh = if (Prefs.overspeedOn) Prefs.overspeedKmh.coerceIn(20, 200) else 120,
        )
    }
}
