package dev.bikecompanion.link

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.bikecompanion.log.EventLog

/**
 * After the phone restarts (or the app is updated), starts waiting for the
 * remembered bike, so the rider never has to open the app to connect.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        EventLog.init(context)
        LinkService.autoConnect(context, if (action == Intent.ACTION_BOOT_COMPLETED) "phone started" else "app updated")
    }
}
