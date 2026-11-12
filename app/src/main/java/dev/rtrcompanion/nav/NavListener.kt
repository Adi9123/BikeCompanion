package dev.rtrcompanion.nav

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.rtrcompanion.data.AppState
import dev.rtrcompanion.data.Prefs
import dev.rtrcompanion.log.EventLog
import dev.rtrcompanion.protocol.NavParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

    private var scope: CoroutineScope? = null

    override fun onListenerConnected() {
        Prefs.init(this)
        EventLog.init(this)
        AppState.navAccess.value = true
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { sc ->
            sc.launch { AppState.declineCall.collect { declineRinging() } }
        }
        EventLog.i(TAG, "Notification access active")
        try {
            activeNotifications?.forEach { if (it.packageName in MAPS) handle(it) }
        } catch (ignored: Exception) {
        }
        countMissedCalls()
    }

    override fun onListenerDisconnected() {
        AppState.navAccess.value = false
        scope?.cancel()
        scope = null
    }

    /** Presses the "Decline" button on the ringing call's notification. */
    private fun declineRinging() {
        val key = AppState.ringingCall.value ?: return
        val sbn = try {
            activeNotifications?.firstOrNull { it.key == key }
        } catch (e: Exception) {
            null
        } ?: return
        val words = listOf("decline", "reject", "dismiss", "hang up", "end call")
        val action = sbn.notification.actions?.firstOrNull { a ->
            val t = a.title?.toString()?.lowercase() ?: ""
            words.any { t.contains(it) }
        }
        if (action == null) {
            EventLog.w(TAG, "No decline button on the call notification")
            return
        }
        try {
            action.actionIntent.send()
            EventLog.i(TAG, "Call declined from the Info switch")
        } catch (e: Exception) {
            EventLog.w(TAG, "Could not decline: $e")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName in MAPS) handle(sbn) else handlePhone(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == AppState.ringingCall.value) AppState.ringingCall.value = null
        if (isMissedCall(sbn)) countMissedCalls()
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

    // ------------------------------------------------- calls, messages, music

    private val sent = HashMap<String, String>()

    private fun title(n: Notification): String =
        n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()

    private fun text(n: Notification): String =
        n.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()

    private fun isMissedCall(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification ?: return false
        return n.category == "missed_call" ||
            (sbn.packageName in DIALERS && title(n).contains("missed", ignoreCase = true))
    }

    private fun countMissedCalls() {
        val count = try {
            activeNotifications?.filter { isMissedCall(it) }?.sumOf { maxOf(1, it.notification.number) } ?: 0
        } catch (e: Exception) {
            0
        }
        if (AppState.missedCalls.value != count) {
            AppState.missedCalls.value = count
            EventLog.i(TAG, "Missed calls: $count")
        }
    }

    /** Sends a short line to the cluster once per notification and content. */
    private fun once(key: String, line: String) {
        if (sent[key] == line) return
        sent[key] = line
        if (sent.size > 100) sent.clear()
        AppState.clusterTexts.tryEmit(line)
        EventLog.i(TAG, "To cluster: $line")
    }

    private fun handlePhone(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (isMissedCall(sbn)) {
            countMissedCalls()
            return
        }
        val pkg = sbn.packageName
        when {
            Prefs.callAlerts && n.category == Notification.CATEGORY_CALL &&
                (text(n).contains("incoming", true) || title(n).contains("incoming", true)) -> {
                val who = title(n).takeUnless { it.contains("incoming", true) } ?: text(n)
                AppState.ringingCall.value = sbn.key
                once(sbn.key, "CALL $who")
            }
            Prefs.messageAlerts && pkg in WHATSAPP -> {
                val from = title(n).ifBlank { return }
                once(sbn.key + text(n), "WA $from")
            }
            Prefs.messageAlerts && (pkg in SMS || n.category == Notification.CATEGORY_MESSAGE) && pkg !in WHATSAPP -> {
                val from = title(n).ifBlank { return }
                once(sbn.key + text(n), "SMS $from")
            }
            Prefs.musicOnCluster && n.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true -> {
                val song = title(n).ifBlank { return }
                once("music", song)
            }
        }
    }

    companion object {
        private const val TAG = "Nav"
        val DIALERS = setOf(
            "com.google.android.dialer", "com.android.dialer", "com.samsung.android.incallui",
            "com.android.incallui", "com.android.server.telecom", "com.samsung.android.dialer",
        )
        val WHATSAPP = setOf("com.whatsapp", "com.whatsapp.w4b")
        val SMS = setOf(
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
            "org.telegram.messenger",
        )
        const val SOURCE = "Google Maps"
        val MAPS = setOf("com.google.android.apps.maps")
    }
}
