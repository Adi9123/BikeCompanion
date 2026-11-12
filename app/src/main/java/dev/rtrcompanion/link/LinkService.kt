package dev.rtrcompanion.link

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.rtrcompanion.R
import dev.rtrcompanion.data.AppState
import dev.rtrcompanion.data.LinkStatus
import dev.rtrcompanion.data.Prefs
import dev.rtrcompanion.log.EventLog
import dev.rtrcompanion.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the Bluetooth link, so the connection survives
 * the screen turning off or the app going to the background during a ride.
 */
class LinkService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var link: BleLink
    private var notifJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        EventLog.init(this)
        ensureChannel()
        link = BleLink(applicationContext, scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                link.disconnect("user")
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CONNECT -> {
                val addr = intent.getStringExtra(EXTRA_ADDRESS)
                if (addr != null) link.connect(addr)
            }
            ACTION_SEND_NAME -> link.queueRiderName()
            null -> {
                // Restarted by the system after being killed: resume the last bike.
                val addr = Prefs.lastAddress
                if (addr == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                EventLog.i(TAG, "Service restarted by Android; reconnecting")
                link.connect(addr)
            }
        }
        if (notifJob == null) {
            notifJob = scope.launch {
                AppState.link
                    .map { Triple(it.phase, it.detail, it.deviceName) }
                    .distinctUntilChanged()
                    .collect { updateNotification(AppState.link.value) }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        link.disconnect("service stopped")
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground(): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(AppState.link.value), type)
        true
    } catch (e: Exception) {
        EventLog.w(TAG, "Could not start the foreground service: $e")
        false
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Bike connection", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(s: LinkStatus): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LinkService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = s.deviceName ?: "RTR Companion"
        val text = if (s.detail.isBlank()) s.phase.label else "${s.phase.label} · ${s.detail}"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Disconnect", stop)
            .build()
    }

    private fun updateNotification(s: LinkStatus) {
        try {
            getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification(s))
        } catch (ignored: Exception) {
        }
    }

    companion object {
        private const val TAG = "Service"
        private const val CHANNEL = "link"
        private const val NOTIF_ID = 1
        const val ACTION_CONNECT = "dev.rtrcompanion.CONNECT"
        const val ACTION_DISCONNECT = "dev.rtrcompanion.DISCONNECT"
        const val ACTION_SEND_NAME = "dev.rtrcompanion.SEND_NAME"
        const val EXTRA_ADDRESS = "address"

        fun connect(ctx: Context, address: String) {
            val i = Intent(ctx, LinkService::class.java).setAction(ACTION_CONNECT).putExtra(EXTRA_ADDRESS, address)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun disconnect(ctx: Context) {
            ctx.startService(Intent(ctx, LinkService::class.java).setAction(ACTION_DISCONNECT))
        }

        fun sendRiderName(ctx: Context) {
            ctx.startService(Intent(ctx, LinkService::class.java).setAction(ACTION_SEND_NAME))
        }
    }
}
