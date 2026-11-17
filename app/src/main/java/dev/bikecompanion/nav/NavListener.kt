package dev.bikecompanion.nav

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import dev.bikecompanion.protocol.Maneuver
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.bikecompanion.data.AppState
import dev.bikecompanion.data.Prefs
import dev.bikecompanion.log.EventLog
import dev.bikecompanion.protocol.NavParser
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
        ArrowStore.init(this)
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
        val (maneuver, how) = decideArrow(n, parsed.maneuver)
        val step = parsed.copy(maneuver = maneuver, source = SOURCE)
        val signature = "${step.maneuver} ${step.text}"
        if (signature != lastLogged) {
            lastLogged = signature
            EventLog.i(TAG, "Maps: ${texts.filter { it.isNotBlank() }.joinToString(" | ")} -> ${step.maneuver.name} ($how) ${step.distanceToTurnM ?: "?"} m")
        }
        AppState.nav.value = step
    }

    /**
     * Which way the turn goes. The rider's correction wins, then a turn named
     * in the text (which also teaches the app this icon), then what this icon
     * meant before, then the shape of the arrow itself.
     */
    private fun decideArrow(n: Notification, fromText: Maneuver): Pair<Maneuver, String> {
        val icon = arrowIcon(n)
        if (icon == null) return fromText to "text, no icon"
        val (bmp, mask) = icon
        val sig = ArrowReader.signature(mask)
        val known = ArrowStore.known(sig)
        if (known?.via == ArrowStore.Via.YOU && known.maneuver != null) return known.maneuver to "set by you"
        if (fromText != Maneuver.UNKNOWN && fromText != Maneuver.DEPART) {
            ArrowStore.record(sig, fromText, ArrowStore.Via.TEXT, bmp)
            return fromText to "text"
        }
        if (known?.maneuver != null) return known.maneuver to "learned"
        val shape = ArrowReader.classify(mask)
        ArrowStore.record(sig, shape, if (shape != null) ArrowStore.Via.SHAPE else ArrowStore.Via.NONE, bmp)
        return (shape ?: fromText) to (if (shape != null) "arrow shape" else "unrecognised arrow")
    }

    /** The turn icon Maps puts in the notification, drawn small, and its ink mask. */
    private fun arrowIcon(n: Notification): Pair<Bitmap, BooleanArray>? = try {
        val d = n.getLargeIcon()?.loadDrawable(this)
        if (d == null) {
            null
        } else {
            val size = ArrowReader.N * 3
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, size, size)
            d.draw(Canvas(bmp))
            val px = IntArray(size * size)
            bmp.getPixels(px, 0, size, 0, 0, size, size)
            bmp to ArrowReader.mask(size, size, px)
        }
    } catch (e: Exception) {
        null
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
            Prefs.musicOnCluster && n.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true -> {
                val song = title(n).ifBlank { return }
                once("music", song)
            }
            pkg in Prefs.relayApps && n.category != Notification.CATEGORY_CALL -> {
                // Only apps the rider picked; the sender's name, never the message itself.
                val from = title(n).ifBlank { return }
                once(sbn.key + text(n), "${shortName(pkg)} $from")
            }
        }
    }

    /** A short tag for the cluster line: WA, SMS, or the first letters of the app's name. */
    private fun shortName(pkg: String): String = when {
        pkg in WHATSAPP -> "WA"
        pkg in SMS -> "SMS"
        else -> try {
            val label = packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            label.filter { it.isLetterOrDigit() }.take(4).uppercase(java.util.Locale.US)
        } catch (e: Exception) {
            "MSG"
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
