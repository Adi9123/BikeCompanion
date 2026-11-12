package dev.rtrcompanion.nav

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.rtrcompanion.data.AppState
import dev.rtrcompanion.data.Prefs
import dev.rtrcompanion.log.EventLog
import dev.rtrcompanion.protocol.NavParser

/**
 * Reads Google Maps' turn-by-turn notification and publishes each step to
 * [AppState.nav]; the Bluetooth link forwards it to the cluster.
 *
 * Needs "Notification access", which the rider grants in Android settings.
 * Only notifications from the maps apps below are looked at, and only while
 * they are ongoing (that is, while a route is being navigated).
 */
class NavListener : NotificationListenerService() {

    private var activeKey: String? = null
    private var lastLogged: String? = null

    override fun onListenerConnected() {
        Prefs.init(this)
        EventLog.init(this)
        AppState.navAccess.value = true
        EventLog.i(TAG, "Notification access active")
        try {
            activeNotifications?.forEach { handle(it) }
        } catch (ignored: Exception) {
        }
    }

    override fun onListenerDisconnected() {
        AppState.navAccess.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName in MAPS && sbn.key == activeKey) {
            activeKey = null
            lastLogged = null
            if (AppState.nav.value?.source == SOURCE) AppState.nav.value = null
            EventLog.i(TAG, "Google Maps navigation ended")
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName !in MAPS) return
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) == 0) return
        val ex = n.extras ?: return
        val keys = listOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_INFO_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
        )
        val texts = keys.mapNotNull { ex.getCharSequence(it)?.toString() }.toMutableList()
        ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { texts += it.toString() }
        val parsed = NavParser.parse(texts) ?: return
        activeKey = sbn.key
        val step = parsed.copy(source = SOURCE)
        val signature = "${step.maneuver} ${step.text}"
        if (signature != lastLogged) {
            lastLogged = signature
            EventLog.i(TAG, "Maps: ${texts.filter { it.isNotBlank() }.joinToString(" | ")} -> ${step.maneuver.name} ${step.distanceToTurnM ?: "?"} m")
        }
        AppState.nav.value = step
    }

    companion object {
        private const val TAG = "Nav"
        const val SOURCE = "Google Maps"
        val MAPS = setOf("com.google.android.apps.maps")
    }
}
